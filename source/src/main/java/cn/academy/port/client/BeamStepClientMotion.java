package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.BeamStep;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** NEW232 integrated-only permission: one addition to live Y, exact live XZ, no teleport or fall reset. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class BeamStepClientMotion {
    private static final BeamStepReceiptState RECEIPTS = new BeamStepReceiptState();
    private static ClientLevel world;
    private static LocalPlayer player;
    private static Object connection;
    private static volatile long generation;
    private static long tick, capturedTick, capturedSession, capturedPresetRevision;
    private static int capturedPreset, capturedHurtTime;
    // Default-off observation only: no permissions, packets, input or movement originate here.
    private static final boolean DIAGNOSTIC = Boolean.getBoolean("academy.highpower.beamStepDiagnostic");
    private static final org.slf4j.Logger DIAGNOSTIC_LOG = org.slf4j.LoggerFactory.getLogger("academy.beam_step_diagnostics");
    private static int diagnosticTicks, diagnosticBudget;
    private static long diagnosticInput, diagnosticJump, diagnosticLastBlockerTick = -1;
    private static boolean diagnosticGroundKnown, diagnosticWasGrounded;
    private static double diagnosticCapturedY;
    private BeamStepClientMotion() {}

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { BeamStep.motionReceiver = BeamStepClientMotion::receive; }
    }
    /** Called before the fresh key-down request is sent, never from repeated held-key callbacks. */
    public static boolean captureInput(long input, long epoch) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        diagnosticWindow(input);
        if (!canStartLocal()) { if (DIAGNOSTIC) diagnostic("capture_reject", "preflight " + diagnosticSnapshot()); return false; }
        if (!AcademyClient.singleOwnerEpochMatches(epoch)) { if (DIAGNOSTIC) diagnostic("capture_reject", "owner_epoch=" + epoch); return false; }
        if (!RECEIPTS.begin(input, epoch, tick, player.getDeltaMovement().y)) {
            if (DIAGNOSTIC) diagnostic("capture_reject", "input_fence input=" + input + " epoch=" + epoch); return false;
        }
        capturedSession = AcademyClient.inputSessionEpoch();
        capturedPreset = AcademyClient.state.presets.current();
        capturedPresetRevision = AcademyClient.state.presets.revision();
        capturedHurtTime = player.hurtTime;
        capturedTick = tick;
        if (DIAGNOSTIC) diagnosticCapturedY = player.getDeltaMovement().y;
        if (DIAGNOSTIC) diagnostic("capture", "input=" + input + " epoch=" + epoch + " session=" + capturedSession + " " + diagnosticSnapshot());
        return true;
    }
    public static boolean canStartLocal() { return startBlocker().isEmpty(); }
    public static String startBlocker() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (mc.getSingleplayerServer() == null || !mc.hasSingleplayerServer() || mc.getSingleplayerServer().isPublished()) {
            diagnosticBlocker("unsupported_server"); return "academy.highpower.beam_step.unsupported";
        }
        if (!eligible(mc)) { diagnosticBlocker("eligibility"); return "academy.highpower.beam_step.unavailable"; }
        if (player.onGround()) { diagnosticBlocker("grounded"); return "academy.highpower.beam_step.airborne"; }
        if (!normalAir(player)) { diagnosticBlocker("normal_air"); return "academy.highpower.beam_step.conflict"; }
        return "";
    }
    public static void receive(BeamStep.Grant packet) {
        if (packet == null || packet.owner() == null || packet.dimension() == null || packet.entity() < 0
                || packet.input() <= 0 || packet.epoch() <= 0 || packet.token() <= 0) { if (DIAGNOSTIC) diagnostic("receipt_reject", "malformed"); return; }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level; LocalPlayer actor = mc.player; Object link = mc.getConnection();
        long queuedGeneration = generation;
        Vec3 receivedVelocity = actor == null ? null : actor.getDeltaMovement();
        if (DIAGNOSTIC) diagnostic("receipt", "input=" + packet.input() + " token=" + packet.token() + " epoch=" + packet.epoch()
                + " issued=" + packet.issuedAt() + " expires=" + packet.expiresAt() + " terminal=" + packet.terminal()
                + " receivedVelocity=" + receivedVelocity);
        mc.execute(() -> {
            if (level == null || actor == null || link == null || mc.level != level || mc.player != actor
                    || mc.getConnection() != link || generation != queuedGeneration) { if (DIAGNOSTIC) diagnostic("receipt_reject", "queued_world_player_connection_generation"); return; }
            synchronize(mc);
            if (packet.entity() != actor.getId() || !packet.owner().equals(actor.getUUID())
                    || level.getEntity(packet.entity()) != actor || !packet.dimension().equals(level.dimension().location())
                    || !AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.sameInputSessionEpoch(capturedSession)
                    || !RECEIPTS.recordedMatches(packet.input(), packet.epoch(), tick)) {
                if (DIAGNOSTIC) diagnostic("receipt_reject", "identity owner=" + (packet.entity() == actor.getId() && packet.owner().equals(actor.getUUID()))
                        + " world=" + packet.dimension().equals(level.dimension().location())
                        + " ownerEpoch=" + AcademyClient.singleOwnerEpochMatches(packet.epoch())
                        + " session=" + AcademyClient.sameInputSessionEpoch(capturedSession)
                        + " pendingInput=" + RECEIPTS.matches(packet.input(), packet.epoch())); return;
            }
            Vec3 current = actor.getDeltaMovement();
            boolean eligible = !packet.terminal() && eligible(mc) && normalAir(actor)
                    && current.equals(receivedVelocity) && actor.hurtTime == Math.max(0, capturedHurtTime - (int)Math.min(2, tick - capturedTick))
                    && AcademyClient.state.presets.current() == capturedPreset
                    && AcademyClient.state.presets.revision() == capturedPresetRevision;
            if (DIAGNOSTIC) diagnostic("receipt_gates", "eligible=" + eligible + " sameQueuedVelocity=" + current.equals(receivedVelocity)
                    + " expectedHurt=" + Math.max(0, capturedHurtTime - (int)Math.min(2, tick - capturedTick))
                    + " capturedPreset=" + capturedPreset + " capturedRevision=" + capturedPresetRevision
                    + " elapsed=" + (tick-capturedTick) + " capturedY=" + diagnosticCapturedY
                    + " nativeY=" + BeamStepReceiptState.nativeY(diagnosticCapturedY,current.y,(int)(tick-capturedTick))
                    + " clientClock=" + level.getGameTime() + " " + diagnosticSnapshot());
            // Server clock/token proof is authoritative; client game time is only the conservative outer TTL fence.
            if (!RECEIPTS.consume(packet.input(), packet.epoch(), packet.token(), tick, current.y,
                    packet.issuedAt(), packet.expiresAt(), level.getGameTime(), eligible)) {
                if (DIAGNOSTIC) diagnostic("receipt_reject", "single_use_fence_or_local_TTL_or_clock_or_nativeY_or_eligibility");
                paidNoop(packet); return;
            }
            double y = BeamStepReceiptState.assistedY(current.y);
            if (!(y > current.y) || !Double.isFinite(y)) { if (DIAGNOSTIC) diagnostic("receipt_reject", "addition_not_representable_or_at_cap current=" + current + " y=" + y); paidNoop(packet); return; }
            if (!BeamStep.consumeIntegrated(packet, mc.getSingleplayerServer())) { if (DIAGNOSTIC) diagnostic("receipt_reject", "integrated_server_proof_expired_or_revoked_or_consumed"); paidNoop(packet); return; }
            if (DIAGNOSTIC) diagnostic("proof_consumed", "input=" + packet.input() + " token=" + packet.token());
            actor.setDeltaMovement(current.x, y, current.z);
            BeamStepEffects.motionOutcome(packet,BeamStepFeedbackState.Outcome.APPLIED);
            if (DIAGNOSTIC) diagnostic("applied", "input=" + packet.input() + " token=" + packet.token() + " oldXYZ=" + current + " newXYZ=" + actor.getDeltaMovement()
                    + " position=" + actor.position() + " fallDistance=" + actor.fallDistance);
        });
    }
    private static void paidNoop(BeamStep.Grant packet) {
        if(!feedbackContext())return;
        boolean expired=tick-capturedTick>BeamStepReceiptState.TTL || world.getGameTime()>=packet.expiresAt();
        BeamStepEffects.motionOutcome(packet,expired?BeamStepFeedbackState.Outcome.PAID_EXPIRED:BeamStepFeedbackState.Outcome.PAID_NOOP);
    }
    /** Kept separately from airborne/TTL eligibility: an expired purchase may explain itself, never move again. */
    private static boolean feedbackContext() {
        var s=AcademyClient.state;
        return world!=null && player!=null && player.isAlive() && !player.isRemoved() && s!=null
                && "meltdowner".equals(s.category) && s.presets.current()==capturedPreset
                && s.presets.revision()==capturedPresetRevision && AcademyClient.sameInputSessionEpoch(capturedSession);
    }
    static boolean eligible(Minecraft mc) {
        var state = AcademyClient.state;
        return mc.getSingleplayerServer() != null && mc.hasSingleplayerServer() && !mc.getSingleplayerServer().isPublished()
                && world != null && player != null && connection != null && mc.screen == null && mc.isWindowActive()
                && !mc.isPaused() && mc.getCameraEntity() == player && player.isAlive() && !player.isRemoved()
                && !player.isSpectator() && state != null && state.activated && state.overloadFine && !state.interfering
                && !state.consumptionInProgress() && "meltdowner".equals(state.category) && state.level >= 3 && state.level <= 5
                && state.learned(BeamStep.ID) && state.learned("thermal_shell") && state.presets.currentContains(BeamStep.ID)
                && SkillCatalog.enabled("meltdowner", BeamStep.ID) && !ClassicMeltdownerLateEffects.delegateActive("jet_engine")
                && !ClassicTeleporterFinalEffects.anyActive();
    }
    private static boolean normalAir(LocalPlayer p) {
        return !p.onGround() && p.isEffectiveAi() && p.getPose() == Pose.STANDING && !p.noPhysics
                && !p.horizontalCollision && !p.verticalCollision && !p.isPassenger() && !p.isVehicle() && !p.isSleeping()
                && !p.isFallFlying() && !p.isAutoSpinAttack() && !p.getAbilities().flying && !p.getAbilities().mayfly
                && !p.isInWaterOrBubble() && !p.isInLava() && p.getMaxHeightFluidType().isAir() && !p.onClimbable()
                && !p.isInPowderSnow && !p.isSwimming() && p.getActiveEffects().isEmpty() && !p.isNoGravity()
                && p.getGravity() == .08 && finite(p.position()) && finite(p.getDeltaMovement());
    }
    private static boolean finite(Vec3 p) { return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
    public static void abortLocal(long input, long epoch) {
        if (DIAGNOSTIC && RECEIPTS.matches(input,epoch)) diagnostic("pending_abort", "input=" + input + " epoch=" + epoch);
        if(RECEIPTS.recordedMatches(input,epoch,tick))RECEIPTS.forgetFeedback();
        RECEIPTS.cancel(input, epoch);
    }
    public static void clear() { if (DIAGNOSTIC && RECEIPTS.pending()) diagnostic("pending_clear", "generation=" + generation); RECEIPTS.cancel(); RECEIPTS.forgetFeedback(); generation++; }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && player == mc.player && connection == mc.getConnection()) return;
        clear();
        if (connection != mc.getConnection()) RECEIPTS.resetConnection();
        world = mc.level; player = mc.player; connection = mc.getConnection(); tick = 0;
        if (DIAGNOSTIC) { diagnosticTicks = diagnosticBudget = 0; diagnosticGroundKnown = false; diagnosticInput = 0; diagnosticLastBlockerTick = -1; }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); tick++;
        if(!feedbackContext())RECEIPTS.forgetFeedback();
        if (RECEIPTS.pending() && (tick - capturedTick > BeamStepReceiptState.TTL || !eligible(mc) || !normalAir(player)
                || !AcademyClient.sameInputSessionEpoch(capturedSession)
                || AcademyClient.state.presets.current() != capturedPreset
                || AcademyClient.state.presets.revision() != capturedPresetRevision)) {
            if (DIAGNOSTIC) diagnostic("tick_cancel", "elapsed=" + (tick-capturedTick) + " capturedPreset=" + capturedPreset
                    + " capturedRevision=" + capturedPresetRevision + " " + diagnosticSnapshot()); RECEIPTS.cancel();
        }
        diagnosticNativeTick(mc);
    }
    private static void diagnosticWindow(long input) {
        if (!DIAGNOSTIC) return;
        diagnosticInput = input; diagnosticTicks = 40; diagnosticBudget = 120;
    }
    private static void diagnosticBlocker(String reason) {
        if (!DIAGNOSTIC || diagnosticLastBlockerTick == tick) return;
        diagnosticLastBlockerTick = tick;
        if (diagnosticTicks == 0) diagnosticWindow(0);
        diagnostic("preflight_reject", "reason=" + reason + " " + diagnosticSnapshot());
    }
    private static void diagnosticNativeTick(Minecraft mc) {
        if (!DIAGNOSTIC || player == null || world == null || mc.isPaused()) return;
        boolean grounded = player.onGround();
        if (diagnosticGroundKnown && diagnosticWasGrounded && !grounded && player.getDeltaMovement().y > 0) {
            diagnosticJump++; diagnosticWindow(0);
            diagnostic("native_jump_start", "jump=" + diagnosticJump + " " + diagnosticSnapshot());
        }
        diagnosticGroundKnown = true; diagnosticWasGrounded = grounded;
        if (diagnosticTicks > 0) {
            diagnostic("native_tick", "remaining=" + diagnosticTicks + " jump=" + diagnosticJump + " " + diagnosticSnapshot());
            diagnosticTicks--;
        }
    }
    private static String diagnosticSnapshot() {
        if (!DIAGNOSTIC || player == null) return "player=absent";
        var s = AcademyClient.state; Minecraft mc = Minecraft.getInstance();
        return "pos=" + player.position() + " velocity=" + player.getDeltaMovement() + " onGround=" + player.onGround()
                + " fallDistance=" + player.fallDistance + " hurt=" + player.hurtTime + " gravity=" + player.getGravity()
                + " horizontalCollision=" + player.horizontalCollision + " verticalCollision=" + player.verticalCollision
                + " pose=" + player.getPose() + " ai=" + player.isEffectiveAi() + " effects=" + player.getActiveEffects().size()
                + " passenger=" + player.isPassenger() + " vehicle=" + player.isVehicle() + " flying=" + player.getAbilities().flying
                + " mayfly=" + player.getAbilities().mayfly + " fallFlying=" + player.isFallFlying() + " noGravity=" + player.isNoGravity()
                + " climb=" + player.onClimbable() + " water=" + player.isInWaterOrBubble() + " lava=" + player.isInLava()
                + " fluidAir=" + player.getMaxHeightFluidType().isAir() + " noPhysics=" + player.noPhysics
                + " screen=" + (mc.screen != null) + " focus=" + mc.isWindowActive() + " paused=" + mc.isPaused()
                + " category=" + (s == null ? "null" : s.category) + " activated=" + (s != null && s.activated)
                + " overloadFine=" + (s != null && s.overloadFine) + " interfering=" + (s != null && s.interfering)
                + " busy=" + (s != null && s.consumptionInProgress())
                + " preset=" + (s == null ? -1 : s.presets.current()) + " revision=" + (s == null ? -1 : s.presets.revision());
    }
    private static void diagnostic(String stage, String detail) {
        if (!DIAGNOSTIC || diagnosticBudget <= 0) return;
        diagnosticBudget--;
        DIAGNOSTIC_LOG.info("NEW232 stage={} input={} localTick={} {}", stage, diagnosticInput, tick, detail);
    }
}

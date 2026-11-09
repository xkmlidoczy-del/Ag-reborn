package cn.academy.port.client;

import cn.academy.port.highpower.CastPoseNet;
import cn.academy.port.highpower.ConstructRuntime;
import cn.academy.port.highpower.core.CastPose;
import cn.academy.port.highpower.core.CastVisualReceipts;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Casting gestures on the client: the local key press starts one (and is relayed so others see it), remote cues start theirs,
 * the first-person hand and every player model read the same table. Purely cosmetic; gameplay never reads this class.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class CastPoseClient {
    /** Skills whose key-up is never delivered (instant or toggled): their gesture plays once for its minimum duration. */
    private static final Set<String> TAP_ONLY = Set.of("magnetic_ground_step", "conductive_circuit", "grounding_return", "polarity_tether", "forked_arc", "arc_gen",
            "thunder_bolt", "mine_detect", "electron_bomb", "ray_barrage");
    private static final int MAX_HOLD_TICKS = 1200, REFRESH_TICKS = 20;
    private static final class Cast {
        final String skill; long input, epoch; String category = ""; int level = 3; final CastPose.Archetype archetype; final double start; double release = -1, refreshed; final boolean local; Cast prev; double fixedAge = -1, fixedSince = -1; boolean qa;
        Cast(String skill, CastPose.Archetype archetype, double start, boolean local) { this.skill = skill; this.archetype = archetype; this.start = start; this.local = local; refreshed = start; }
    }
    private static final Map<Integer, Cast> CASTS = new HashMap<>();
    private static Object world, owner, connection;
    private static final Map<Integer, long[]> CONSTRUCT_PHASES = new HashMap<>();
    private static double lastRailgun = -1;
    private static final CastVisualReceipts RELEASES = new CastVisualReceipts();
    private record PendingTap(long serial, double began, long epoch) {}
    private static final Map<String, PendingTap> PENDING_TAPS = new HashMap<>();
    private static long tapSerial;
    private static final Set<String> ZERO_INPUT_TAPS = Set.of("arc_gen","forked_arc","thunder_bolt","mine_detect","electron_bomb");
    private static final Set<String> GUI_CASTS = Set.of("location_teleport","tactical_coordinate_formation","magnetic_item_pull");
    private static com.mojang.blaze3d.vertex.PoseStack lastHandStack;

    private CastPoseClient() {}

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { CastPoseNet.clientReceiver = CastPoseClient::cue; }
    }

    private static double now(Minecraft mc, float partial) { return mc.level == null ? 0 : mc.level.getGameTime() + partial; }
    private static double now(Minecraft mc) { return now(mc, mc.isPaused() ? 0 : mc.getTimer().getGameTimeDeltaPartialTick(false)); }

    private static void sync(Minecraft mc) {
        if (world != mc.level || owner != mc.player || connection != mc.getConnection()) {
            CASTS.clear(); TAILS.clear(); CONSTRUCT_PHASES.clear(); PENDING_TAPS.clear(); RELEASES.clear(); lastRailgun = -1; lastHandStack = null; CameraMotion.clear();
            world = mc.level; owner = mc.player; connection = mc.getConnection();
        }
    }

    // ---- local input -------------------------------------------------------------------------------------------------------

    /** Called when the player presses a skill key; {@code input} is the accepted input id (0 or less when nothing started). */
    public static void down(ClassicClientInputBinding.Node node, long input) {
        Minecraft mc = Minecraft.getInstance();
        if (node == null || mc.player == null || mc.level == null) return;
        // Railgun's historical iron-item branch returns 0, while a coin attempt returns -1.
        boolean ironCharge = node.kind == ClassicClientInputBinding.Kind.RAILGUN && input == 0
                && (mc.player.getMainHandItem().is(net.minecraft.world.item.Items.IRON_INGOT)
                || mc.player.getMainHandItem().is(net.minecraft.world.item.Items.IRON_BLOCK));
        sync(mc);
        if (input == 0 && ZERO_INPUT_TAPS.contains(node.skill)) {
            // These legacy slot presses allocate no nonce. A matching real server visual, not the key itself, confirms success.
            if (node.skill.equals("arc_gen") || node.skill.equals("forked_arc")) { PENDING_TAPS.remove("arc_gen"); PENDING_TAPS.remove("forked_arc"); }
            PENDING_TAPS.put(node.skill, new PendingTap(++tapSerial, now(mc), AcademyClient.inputSessionEpoch()));
            return;
        }
        if (input <= 0 && !ironCharge || GUI_CASTS.contains(node.skill)) return;
        // Construct gestures are confirmed by the same authoritative cues as the moving body and VFX.
        if ("vector_construct".equals(node.skill)) return;
        CastPose.Archetype a = CastPose.of(node.skill);
        if (a == null) return;
        sync(mc);
        boolean holds = node.kind == ClassicClientInputBinding.Kind.SINGLE_KEY && !TAP_ONLY.contains(node.skill) || node.kind == ClassicClientInputBinding.Kind.RAILGUN;
        Cast c = new Cast(node.skill, a, now(mc), true); c.input = input; c.epoch = AcademyClient.serverSingleEpoch();
        if (!holds) c.release = c.start + CastPose.minTicks(a);
        chain(mc.player.getId(), c);
        relay(a, holds ? CastPoseNet.HOLD : CastPoseNet.TAP);
        CameraMotion.cast(node.skill, holds);
    }

    public static void up(ClassicClientInputBinding.Node node) { end(node == null ? null : node.skill, false); }
    public static void abort(ClassicClientInputBinding.Node node) { end(node == null ? null : node.skill, true); }

    private static void end(String skill, boolean abort) {
        Minecraft mc = Minecraft.getInstance();
        if (skill == null || mc.player == null) return;
        if (abort) CameraMotion.cancel(skill); else if (!"railgun".equals(skill)) CameraMotion.release(skill);
        Cast c = CASTS.get(mc.player.getId());
        if (c == null || !c.skill.equals(skill) || c.release >= 0) return;
        double now = now(mc);
        c.release = abort ? now : Math.max(now, c.start + CastPose.minTicks(c.archetype));
        relay(c.archetype, CastPoseNet.END);
    }

    /** Installs a new cast; the one it replaces is released now and kept for a few ticks so the pose crossfades instead of popping. */
    private static void chain(int entity, Cast next) {
        Cast old = CASTS.get(entity);
        if (old != null) { if (old.release < 0) old.release = next.start; old.prev = null; next.prev = old; }
        CASTS.put(entity, next);
    }

    private static final double BLEND_TICKS = 4;

    private static void relay(CastPose.Archetype a, int phase) {
        try { if (Minecraft.getInstance().getConnection() != null) PacketDistributor.sendToServer(new CastPoseNet.Intent(a.ordinal(), phase, localSkill(a))); }
        catch (RuntimeException unavailable) { /* cosmetic only */ }
    }

    private static String localSkill(CastPose.Archetype a) {
        var mc = Minecraft.getInstance(); Cast c = mc.player == null ? null : CASTS.get(mc.player.getId());
        return c != null && c.archetype == a ? c.skill : "";
    }

    // ---- remote cues -------------------------------------------------------------------------------------------------------

    private static void cue(CastPoseNet.Cue cue) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.level == null || mc.player == null || cue.entityId() == mc.player.getId()) return;
            sync(mc);
            CastPose.Archetype a = CastPose.Archetype.values()[cue.archetype()];
            double now = now(mc, 0);
            Cast c = CASTS.get(cue.entityId());
            if (cue.phase() == CastPoseNet.END) { if (c != null && c.archetype == a && (cue.skill().isEmpty() || c.skill.equals(cue.skill())) && c.release < 0) c.release = Math.max(now, c.start + CastPose.minTicks(a)); return; }
            if (c != null && c.archetype == a && c.skill.equals(cue.skill()) && c.release < 0 && cue.phase() == CastPoseNet.HOLD) { c.refreshed = now; return; }
            if (CASTS.size() > 256) CASTS.clear();
            Cast next = new Cast(cue.skill(), a, now, false); next.category = cue.category(); next.level = cue.level();
            if (cue.phase() == CastPoseNet.TAP) next.release = next.start + CastPose.minTicks(a);
            chain(cue.entityId(), next);
        });
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        sync(mc);
        if (mc.level == null || mc.player == null) { CameraMotion.clear(); return; }
        double now = now(mc, 0);
        constructSignatures(mc, now);
        railgunRelease(mc, now);
        var it = CASTS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next(); Cast c = e.getValue();
            var owner=mc.level.getEntity(e.getKey());
            if(!(owner instanceof Player p) || !p.isAlive() || p.isSpectator()) { if (c.local) CameraMotion.cancel(c.skill); it.remove(); continue; }
            if (c.local && !AcademyClient.state.isActivated() && (c.release < 0 || c.release > now)) { c.release = now; CameraMotion.cancel(c.skill); relay(c.archetype, CastPoseNet.END); }
            if (c.release >= 0) { if (now - c.release > Math.max(CastPose.RELAX_TICKS, 14) + 2) it.remove(); continue; }
            if (c.local ? now - c.start > MAX_HOLD_TICKS : now - c.refreshed > REFRESH_TICKS * 3) { c.release = now; if (c.local) CameraMotion.cancel(c.skill); continue; }
            if ("vector_construct".equals(c.skill)) {
                int life = c.archetype == CastPose.Archetype.CONSTRUCT_LOCK ? ConstructDecor.PREVIEW_LIFE : ConstructDecor.HOLD_STALE;
                if (now - c.refreshed > life) { c.release = now; if (c.local) CameraMotion.cancel(c.skill); }
                continue;
            }
            if (!c.local || c.skill.isEmpty() || c.qa) continue;
            // A local hold ends when its key is no longer down (covers a lost key-up, an opened screen or a dropped connection).
            var key = AcademyClient.skillKey(c.skill);
            if (now - c.start > 4 && (mc.screen != null || key == null || !key.isDown()) && !externalHold(c.skill)) {
                c.release = Math.max(now, c.start + CastPose.minTicks(c.archetype)); relay(c.archetype, CastPoseNet.END);
                // GLFW updates the mapping before the binding delegate necessarily publishes onKeyUp.
                // A normal physical release must preserve the owned release animation, including TP's release-only FOV.
                // GUI/mapping loss is cancellation; railgun recoil still waits for its authoritative shot cue.
                if (mc.screen != null || key == null || "railgun".equals(c.skill)) CameraMotion.cancel(c.skill);
                else CameraMotion.release(c.skill);
            }
            else if (now - c.refreshed > REFRESH_TICKS) { c.refreshed = now; relay(c.archetype, CastPoseNet.HOLD); }
        }
    }

    /** The caller has already authenticated a real successful server result. No gameplay or UI action is performed here. */
    public static boolean acceptedRelease(String skill, int entity, java.util.UUID owner, long input, long epoch, long token, boolean relayLocal) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(mc.level.getEntity(entity) instanceof Player player)
                || !player.getUUID().equals(owner) || !player.isAlive() || player.isSpectator() || CastPose.of(skill) == null) return false;
        sync(mc);
        if (!RELEASES.accept(owner,skill,epoch,input,token)) return false;
        double time=now(mc); boolean local=player==mc.player;
        Cast old=CASTS.get(entity);
        // An earlier accepted throw must not steal the hands or camera of a newer press of this same skill.
        if (old!=null && old.skill.equals(skill) && old.epoch==epoch && old.input>input) return false;
        Cast c;
        if (old!=null && old.skill.equals(skill) && old.input==input && time-old.start<MAX_HOLD_TICKS) {
            c=old; c.release=Math.max(time,c.start+CastPose.minTicks(c.archetype));
        } else {
            c=new Cast(skill,CastPose.of(skill),time,local); c.input=input; c.epoch=epoch;
            c.release=time+CastPose.minTicks(c.archetype); chain(entity,c);
        }
        var catalog=cn.academy.port.SkillCatalog.REGISTERED.stream().filter(s->s.id().equals(skill)).findFirst().orElse(null);
        if (catalog!=null) { c.category=catalog.category(); c.level=catalog.level(); }
        if (local) { if (relayLocal) relay(c.archetype,CastPoseNet.TAP); CameraMotion.cast(skill,false); }
        return true;
    }

    /** Authenticated existing ClientData channel; only a pending tokenless instant can consume this success. */
    public static void acceptedLegacyVisual(net.minecraft.nbt.CompoundTag tag) {
        var mc=Minecraft.getInstance();
        if (tag==null || mc.level==null || mc.player==null || !tag.contains("entity",net.minecraft.nbt.Tag.TAG_INT)
                || tag.getInt("entity")!=mc.player.getId()) return;
        sync(mc); String kind=tag.getString("kind"),skill;
        if (kind.equals("mag_manip_perform")) {
            if (tag.hasUUID("entity_uuid") && mc.player.getUUID().equals(tag.getUUID("entity_uuid"))
                    && tag.contains("input",net.minecraft.nbt.Tag.TAG_LONG) && tag.contains("token",net.minecraft.nbt.Tag.TAG_LONG)
                    && tag.contains("owner_epoch",net.minecraft.nbt.Tag.TAG_LONG) && tag.getLong("input")>0
                    && AcademyClient.singleOwnerEpochMatches(tag.getLong("owner_epoch"))
                    && !AcademyClient.singleTokenConflicts("mag_manip",tag.getLong("input"),tag.getLong("token")))
                acceptedRelease("mag_manip",mc.player.getId(),mc.player.getUUID(),tag.getLong("input"),tag.getLong("owner_epoch"),tag.getLong("token"),true);
            return;
        }
        if (kind.equals("thunder_bolt") || kind.equals("mine_detect")) skill=kind;
        else if (kind.equals("electron_bomb_charge")) skill="electron_bomb";
        else if (kind.equals("arc")) {
            var a=PENDING_TAPS.get("arc_gen"); var b=PENDING_TAPS.get("forked_arc");
            skill=b!=null && (a==null || b.serial()>a.serial())?"forked_arc":"arc_gen";
        } else return;
        PendingTap pending=PENDING_TAPS.remove(skill);
        if (pending==null || pending.epoch()!=AcademyClient.inputSessionEpoch() || now(mc)-pending.began()>60) return;
        acceptedRelease(skill,mc.player.getId(),mc.player.getUUID(),pending.serial(),pending.epoch(),pending.serial(),true);
    }

    /** Independent construct pose ownership is retained; only the matching authoritative phase drives its signature/camera. */
    private static void constructSignatures(Minecraft mc, double now) {
        for (var cue : ConstructClient.cues()) {
            var v = cue.visual();
            long[] old = CONSTRUCT_PHASES.get(v.entityId());
            if (old != null && old[0] == v.token() && old[1] == v.phase()) {
                Cast c = CASTS.get(v.entityId());
                if (c != null && "vector_construct".equals(c.skill)) c.refreshed = cue.received();
                continue;
            }
            CONSTRUCT_PHASES.put(v.entityId(), new long[] {v.token(), v.phase()});
            boolean local = v.entityId() == mc.player.getId();
            if (v.phase() == ConstructRuntime.REFUSED || v.phase() == ConstructRuntime.END) {
                Cast c = CASTS.get(v.entityId());
                if (c != null && "vector_construct".equals(c.skill)) c.release = now;
                if (local) CameraMotion.cancel("vector_construct");
                continue;
            }
            CastPose.Archetype a = switch (v.phase()) {
                case ConstructRuntime.PREVIEW -> CastPose.Archetype.CONSTRUCT_LOCK;
                case ConstructRuntime.DASH -> CastPose.Archetype.CONSTRUCT_PULL;
                case ConstructRuntime.HOLD -> CastPose.Archetype.CONSTRUCT_STEER;
                case ConstructRuntime.THROWN -> CastPose.Archetype.CONSTRUCT_THROW;
                case ConstructRuntime.LANDED -> CastPose.Archetype.CONSTRUCT_LAND;
                default -> null;
            };
            if (a == null) continue;
            Cast c = new Cast("vector_construct", a, now, local); c.refreshed = cue.received();
            boolean holds = v.phase() == ConstructRuntime.PREVIEW || v.phase() == ConstructRuntime.DASH || v.phase() == ConstructRuntime.HOLD;
            if (!holds) c.release = now + CastPose.minTicks(a);
            chain(v.entityId(), c);
            if (local) CameraMotion.cast("vector_construct", holds);
        }
        if (CONSTRUCT_PHASES.size() > 64) CONSTRUCT_PHASES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    /** Both coin QTE and iron charging converge on an accepted local beam; rejected QTEs never kick the camera. */
    private static void railgunRelease(Minecraft mc, double now) {
        for (double[] beam : ClassicDecorSnapshots.beams()) {
            if (beam.length < 10 || beam[8] < .5 || beam[9] <= lastRailgun) continue;
            lastRailgun = beam[9];
            if (beam[7] > 150) continue; // Do not replay historical beams on an ownership/session reset.
            Cast c = new Cast("railgun", CastPose.Archetype.AIM, now, true);
            c.release = now + CastPose.minTicks(c.archetype);
            chain(mc.player.getId(), c);
            relay(c.archetype, CastPoseNet.TAP);
            CameraMotion.cast("railgun", false);
        }
    }

    /** A refusal that arrives right after the press cancels the gesture: the caster must not keep casting something that never started. */
    @SubscribeEvent public static void feedback(net.neoforged.neoforge.client.event.ClientChatReceivedEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !(event.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t)) return;
        Cast c = CASTS.get(mc.player.getId());
        if (c == null || !c.local || c.skill.isEmpty() || c.release >= 0 && c.release - c.start > CastPose.minTicks(c.archetype) || now(mc) - c.start > REFUSAL_WINDOW_TICKS) return;
        if (!CastPose.refusal(t.getKey(), c.skill)) return;
        c.release = now(mc); c.fixedAge = -1; CameraMotion.cancel(c.skill);
        relay(c.archetype, CastPoseNet.END);
    }
    private static final int REFUSAL_WINDOW_TICKS = 12;

    /** Skills whose server state keeps the gesture alive after the key is released (a structure still held in the air). */
    private static boolean externalHold(String skill) {
        Minecraft mc = Minecraft.getInstance();
        return "vector_construct".equals(skill) && mc.player != null && ConstructClient.holding(mc.player.getUUID());
    }

    // ---- first person ------------------------------------------------------------------------------------------------------

    @SubscribeEvent public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || lastHandStack == event.getPoseStack() || ownPose(mc.player)) return;
        sync(mc);
        double now = now(mc, mc.isPaused() ? 0 : event.getPartialTick());
        var motion=motion(mc.player,now);
        var h=motion.hand();
        if (h == CastPose.Hand.NONE || h.equals(CastPose.Hand.NONE)) return;
        lastHandStack = event.getPoseStack();
        var poses = event.getPoseStack();
        float partial = event.getPartialTick();
        // ItemInHandRenderer applies this Rx * Ry immediately before RenderHandEvent. Stabilize only
        // a live casting gesture, fading with its envelope; outer walking/hurt transforms remain intact.
        float pitchLag = (mc.player.getViewXRot(partial) - Mth.lerp(partial, mc.player.xBobO, mc.player.xBob)) * .1F;
        float yawLag = (mc.player.getViewYRot(partial) - Mth.lerp(partial, mc.player.yBobO, mc.player.yBob)) * .1F;
        var look = CastPose.firstPersonLookLag(motion.strength(), pitchLag, yawLag);
        poses.mulPose(Axis.YP.rotationDegrees((float) look.yaw()));
        poses.mulPose(Axis.XP.rotationDegrees((float) look.pitch()));
        poses.translate(h.x(), h.y(), h.z());
        poses.mulPose(Axis.XP.rotationDegrees((float) h.rotX()));
        poses.mulPose(Axis.YP.rotationDegrees((float) h.rotY()));
        poses.mulPose(Axis.ZP.rotationDegrees((float) h.rotZ()));
    }

    // ---- third person and other players ------------------------------------------------------------------------------------

    /**
     * Called from the humanoid model mixin after the vanilla pose is set. {@code ageInTicks} is zero only for the first-person arm
     * render, which has its own transform above; skipping it keeps the hand from being posed twice.
     */
    /**
     * Player states whose own pose must win: the model is already doing something specific (lying down, gliding, swimming, drawing a
     * bow, spinning with a trident) and a casting gesture on top would look broken. Pure client state; never read by gameplay.
     */
    static boolean ownPose(Player p) {
        return p.isSleeping() || p.isFallFlying() || p.isSwimming() || p.isVisuallyCrawling() || p.isAutoSpinAttack() || p.isDeadOrDying() || p.isUsingItem();
    }

    public static void apply(HumanoidModel<?> model, net.minecraft.world.entity.LivingEntity entity, float ageInTicks, float limbSwingAmount) {
        if (ageInTicks == 0 || !(entity instanceof Player player) || ownPose(player)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || world != mc.level) return;
        var motion=motion(player,now(mc));
        double env=motion.strength();
        if (env<=0) return;
        var arms=motion.arms();
        double keep = 1 - env;
        // A vanilla swing (attack, mining, placing) owns the swinging hand and the torso turn it causes; the gesture keeps the other arm.
        boolean swinging = model.attackTime > 0, mainRight = player.getMainArm() == HumanoidArm.RIGHT;
        boolean driveRight = !(swinging && mainRight), driveLeft = !(swinging && !mainRight);
        if (driveRight) {
            model.rightArm.xRot = (float) (model.rightArm.xRot * keep + arms.rx());
            model.rightArm.yRot = (float) (model.rightArm.yRot * keep + arms.ry());
            model.rightArm.zRot = (float) (model.rightArm.zRot * keep + arms.rz());
        }
        if (driveLeft) {
            model.leftArm.xRot = (float) (model.leftArm.xRot * keep + arms.lx());
            model.leftArm.yRot = (float) (model.leftArm.yRot * keep + arms.ly());
            model.leftArm.zRot = (float) (model.leftArm.zRot * keep + arms.lz());
        }
        if (arms.lean() != 0) { model.body.xRot += (float) (arms.lean() * .6); model.head.xRot -= (float) (arms.lean() * .3); }
        // Construct dash: a crouch like vanilla's (shoulders and head down, legs forward) scaled by how far the coil has gone, and a forward lean.
        var stance = ConstructClient.stance(player, now(mc));
        if (stance.crouch() > 0 || stance.lean() > 0) {
            float c = (float) stance.crouch(), l = (float) stance.lean();
            model.body.xRot += .5f * c + .6f * l; model.head.xRot -= .3f * c + .4f * l;
            model.body.y += 3.2f * c; model.head.y += 4.2f * c; model.rightArm.y += 3.2f * c; model.leftArm.y += 3.2f * c;
            model.rightLeg.z += 3.9f * c; model.leftLeg.z += 3.9f * c; model.rightLeg.y += .2f * c; model.leftLeg.y += .2f * c;
        }
        var body = motion.body();
        // Torso twist moves the shoulders exactly as vanilla's attack swing does, so arms stay attached to the turning body.
        if (body.twist() != 0 && !swinging) {
            float twist = (float) body.twist(); model.body.yRot += twist;
            float yaw = model.body.yRot;
            model.rightArm.z = Mth.sin(yaw) * 5; model.rightArm.x = -Mth.cos(yaw) * 5;
            model.leftArm.z = -Mth.sin(yaw) * 5; model.leftArm.x = Mth.cos(yaw) * 5;
            model.rightArm.yRot += twist; model.leftArm.yRot += twist;
        }
        // Stance fades out while walking so it never fights the vanilla stride.
        double still = 1 - Math.min(1, limbSwingAmount * 1.5);
        float spread = (float) (body.spread() * still), lunge = (float) (body.lunge() * still);
        // A seated rider keeps vanilla legs; only the upper body gestures.
        if ((spread != 0 || lunge != 0) && !player.isPassenger()) {
            model.rightLeg.zRot -= spread; model.leftLeg.zRot += spread;
            boolean right = player.getMainArm() == HumanoidArm.RIGHT;
            var front = right ? model.leftLeg : model.rightLeg; var back = right ? model.rightLeg : model.leftLeg;
            front.xRot -= lunge; back.xRot += lunge * .5f;
        }
    }

    /** Read-only sample shared by first and third person, including server-confirmed construct phases. */
    public static CastPose.Archetype profile(Player player) {
        Cast c=CASTS.get(player.getId()); return c==null?null:c.archetype;
    }
    public static CastPose.Motion motion(Player player,double time) {
        var construct=ConstructClient.motion(player,time);
        if(construct.strength()>0) return construct;
        Cast c=CASTS.get(player.getId());
        if((c==null || c.skill.equals("meltdowner")) && ClassicMeltdownerBeamEffects.mainBeamActive(player))
            return CastPose.sample(CastPose.Archetype.AIM,20,-1,player.getMainArm()==HumanoidArm.RIGHT);
        if(c==null || player==Minecraft.getInstance().player && CastPose.firstPersonOwnedElsewhere(c.skill)
                && Minecraft.getInstance().options.getCameraType().isFirstPerson()) return CastPose.Motion.NONE;
        boolean right=player.getMainArm()==HumanoidArm.RIGHT;
        if(c.fixedAge>=0) return CastPose.sample(c.archetype,c.fixedAge,c.fixedSince,right);
        var now=CastPose.sample(c.archetype,time-c.start,c.release<0?-1:time-c.release,right);
        double blend=(time-c.start)/BLEND_TICKS;
        if(c.prev!=null && blend<1) {
            var old=CastPose.sample(c.prev.archetype,time-c.prev.start,c.prev.release<0?-1:time-c.prev.release,right);
            return CastPose.mix(old,now,blend);
        }
        c.prev=null;
        return now;
    }

    /** Main wrist in world space, using the same arm angles as HumanoidModel (XYZ rotations, model Y points down). */
    public static net.minecraft.world.phys.Vec3 handAnchor(Player p,double time,float partial) {
        var mc=Minecraft.getInstance(); var motion=motion(p,time); boolean right=p.getMainArm()==HumanoidArm.RIGHT;
        double sign=right?1:-1;
        if(p==mc.player && mc.options.getCameraType().isFirstPerson()) {
            var wrist=CastPose.wristCamera(motion.hand(),right); var f=p.getLookAngle(); double yaw=Math.toRadians(p.getYRot());
            var side=new net.minecraft.world.phys.Vec3(-Math.cos(yaw),0,-Math.sin(yaw));
            var up=side.cross(f);
            return p.getEyePosition(partial).add(side.scale(wrist.x())).add(up.scale(wrist.y())).add(f.scale(-wrist.z()));
        }
        return worldHandAnchor(p,time,partial);
    }
    /** The visible world-model wrist, independent of the local camera mode. */
    public static net.minecraft.world.phys.Vec3 worldHandAnchor(Player p,double time,float partial) {
        var motion=motion(p,time); boolean right=p.getMainArm()==HumanoidArm.RIGHT;
        var a=motion.arms(); double twist=motion.body().twist();
        // The shoulder pivot and the arm's yaw move with the torso twist exactly as apply() moves them on the model.
        double pivotX=(right?-Math.cos(twist):Math.cos(twist))*.3125, pivotZ=(right?Math.sin(twist):-Math.sin(twist))*.3125;
        double x=right?a.rx():a.lx(), y=(right?a.ry():a.ly())+twist, z=right?a.rz():a.lz();
        double vy=.625*Math.cos(x), vz=.625*Math.sin(x), vx=vz*Math.sin(y); vz=vz*Math.cos(y)+pivotZ;
        double rx=vx*Math.cos(z)-vy*Math.sin(z), ry=vx*Math.sin(z)+vy*Math.cos(z);
        double yaw=Math.toRadians(p.yBodyRot), side=-pivotX-rx;
        return p.getPosition(partial).add(-Math.cos(yaw)*side+Math.sin(yaw)*vz,1.375-ry-(p.isCrouching()?.125:0),-Math.sin(yaw)*side-Math.cos(yaw)*vz);
    }

    /** QA hook: forces a gesture on an entity as if its key (local) or cue (remote) had arrived; {@code holding} keeps it until cleared. */
    public static void force(int entityId, CastPose.Archetype archetype, boolean holding, boolean local) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        sync(mc);
        Cast c = new Cast("", archetype, now(mc, 0), local);
        if (!holding) c.release = c.start + CastPose.minTicks(archetype);
        CASTS.put(entityId, c);
    }

    /** QA hook: freezes a gesture at one instant of its timeline ({@code since} < 0 while held) so a single frame can be inspected. */
    public static void forceAt(int entityId, CastPose.Archetype archetype, double age, double since, boolean local) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        sync(mc);
        Cast c = new Cast("", archetype, now(mc, 0), local);
        c.fixedAge = age; c.fixedSince = since;
        CASTS.put(entityId, c);
    }

    /** QA hook: starts a held local cast that carries its skill id, as a real key press would, so refusal feedback can be exercised. */
    public static void forceCast(int entityId, String skill) {
        Minecraft mc = Minecraft.getInstance();
        var a = CastPose.of(skill);
        if (mc.level == null || a == null) return;
        sync(mc);
        Cast c = new Cast(skill, a, now(mc, 0), true); c.qa = true;
        chain(entityId, c);
    }

    /** QA hook: {@code held}, {@code released} or {@code none} for the local player's cast. */
    /** Read-only view of a live cast for the category signature decoration (no state is changed). */
    public record SignatureCast(int entity, String skill, CastPose.Archetype archetype, double age, double sinceRelease, boolean local,
                                String category, int level) {
        public SignatureCast(int entity, String skill, CastPose.Archetype archetype, double age, double sinceRelease, boolean local) {
            this(entity, skill, archetype, age, sinceRelease, local, "", 3);
        }
    }
    /** How long after release a cast stays visible to the signature/motif decoration (the pose itself relaxes much sooner). */
    private static final double TAIL_TICKS = 52;
    private record Tail(int entity, Cast cast) {}
    private static final Map<Long, Tail> TAILS = new HashMap<>();
    public static java.util.List<SignatureCast> signatures(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (CASTS.isEmpty() && TAILS.isEmpty())) return java.util.List.of();
        double now = now(mc, partial);
        var out = new java.util.ArrayList<SignatureCast>(CASTS.size() + TAILS.size());
        for (var e : CASTS.entrySet()) {
            Cast c = e.getValue();
            double since = c.release < 0 || now < c.release ? -1 : now - c.release;
            if (since > TAIL_TICKS) continue;
            if (since >= 0 && !c.skill.isEmpty()) TAILS.put(e.getKey() * 4_000_037L + Math.round(c.start), new Tail(e.getKey(), c));
            out.add(new SignatureCast(e.getKey(), c.skill, c.archetype, Math.max(0, now - c.start), since, c.local, c.category, c.level));
        }
        // Released casts whose pose is long gone but whose visual tail (marks, shock rings, afterglow) is still playing.
        var tails = TAILS.entrySet().iterator();
        while (tails.hasNext()) {
            var e = tails.next(); Tail t = e.getValue(); Cast c = t.cast();
            double since = now - c.release;
            if (c.release < 0 || since > TAIL_TICKS) { tails.remove(); continue; }
            if (CASTS.get(t.entity()) == c || since < 0) continue;
            out.add(new SignatureCast(t.entity(), c.skill, c.archetype, Math.max(0, now - c.start), since, c.local, c.category, c.level));
        }
        return out;
    }

    public static String stateForTest(int entityId) {
        Cast c = CASTS.get(entityId);
        return c == null ? "none" : c.release < 0 ? "held" : "released";
    }

    public static void clearForTest() { CASTS.clear(); TAILS.clear(); CONSTRUCT_PHASES.clear(); PENDING_TAPS.clear(); RELEASES.clear(); CameraMotion.clear(); }
}

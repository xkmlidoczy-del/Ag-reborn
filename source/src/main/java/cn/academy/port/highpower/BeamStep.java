package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.BeamStepRules;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW232: one paid, bounded additive local impulse. Native movement and fall accounting own the result. */
@EventBusSubscriber(modid="academy")
public final class BeamStep {
    public static final String ID = "beam_step";
    private static final String QUOTA = "academy:beam_step_grants";
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    // This lock publishes immutable client proof only. Never inspect mutable server state on the client thread.
    private static final Object PROOF_LOCK = new Object();
    private static final Map<Long, Proof> PROOFS = new HashMap<>();
    private static final Map<MinecraftServer, Long> CLOCKS = new IdentityHashMap<>();
    private static long nextToken;
    private static final boolean DIAGNOSTICS = Boolean.getBoolean("academy.highpower.beamStepDiagnostic");
    private static final org.slf4j.Logger DIAGNOSTIC_LOG = org.slf4j.LoggerFactory.getLogger("academy.beam_step_diagnostics");
    // Diagnostic-only monotonic log fence: one consume observation per new token, including rejection.
    private static long diagnosticConsumedToken;
    public static Consumer<Grant> motionReceiver = ignored -> {};
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(long epoch, long input) {}
    private record Proof(MinecraftServer server, Grant grant) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset;
        final long revision, started;
        final Vec3 at;
        final boolean fixture;
        Grant grant;
        Context(ServerPlayer player, HighPowerInput.Input input, boolean fixture) {
            identity = new LegacySingleKeyServerIdentity(player, input.input(), ++nextToken, AbilityStorage.get(player));
            this.input = input; this.fixture = fixture;
            preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            started = now(player); at = player.position();
        }
    }
    private BeamStep() {}
    public static boolean tap(ServerPlayer player, String wire) { return tap(player, wire, false); }
    /** Private native-test seam models integrated admission; public requests always use the real server gate. */
    private static boolean tap(ServerPlayer p, String wire, boolean integratedContractFixture) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input) || AbilityConsumption.busy(p)) return false;
        Watermark old = INPUTS.get(p.getUUID());
        if (old != null && old.epoch == input.epoch() && input.input() <= old.input) return false;
        INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        if ((!integratedContractFixture && (!p.server.isSingleplayer() || p.server.isPublished())) || !canAct(p)
                || !AbilityStorage.get(p).canUse(ID) || !ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))
                || !quotaReady(p) || CONTEXTS.containsKey(p.getUUID()) || nextToken == Long.MAX_VALUE) return false;
        Context c = new Context(p, input, integratedContractFixture);
        CONTEXTS.put(p.getUUID(), c);
        var q = HighPowerGrowth.values(p).position();
        double cost = BeamStepRules.cpCost(q); int cooldown = BeamStepRules.cooldownTicks(q);
        // Resource calculations may dispatch native extension callbacks. The captured context and quota
        // are rechecked after them; cancellation/reentrant starts cannot resurrect or replace this grant.
        if (!valid(c) || !quotaReady(p)) { finish(c); return false; }
        if (!HighPowerResources.trySpend(p, ID, cost, cooldown)) { finish(c); return false; }
        // Payment is final. A callback-induced invalidation loses this purchase, without refund or XP.
        if (!valid(c) || !quotaReady(p)) { finish(c); return false; }
        p.getPersistentData().putLongArray(QUOTA, BeamStepRules.append(quota(p), now(p)));
        c.grant = new Grant(p.getId(), p.getUUID(), input.input(), c.identity.token(), input.epoch(),
                p.serverLevel().dimension().location(), now(p), now(p) + BeamStepRules.GRANT_TTL, false);
        synchronized (PROOF_LOCK) {
            CLOCKS.put(p.server, now(p)); PROOFS.put(c.identity.token(), new Proof(p.server, c.grant));
        }
        if (DIAGNOSTICS) diagnostic(c, "server_grant", "published " + serverSnapshot(c));
        PacketDistributor.sendToPlayer(p, c.grant);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new Visual(p.getId(), p.getUUID(), c.identity.token(), input.epoch(),
                c.grant.dimension(), c.at, now(p), now(p) + BeamStepRules.PLATE_TTL, false));
        return true;
    }
    /** Atomic integrated-client proof. All server facts were captured on the server thread. No acknowledgement or XP. */
    public static boolean consumeIntegrated(Grant grant, MinecraftServer server) {
        synchronized (PROOF_LOCK) {
            boolean accepted = freshProof(grant, server);
            if (DIAGNOSTICS && grant != null && grant.token() > diagnosticConsumedToken) {
                diagnosticConsumedToken = grant.token();
                Proof proof = PROOFS.get(grant.token()); Long clock = CLOCKS.get(server);
                // Render-thread diagnostics inspect only immutable proof data under the same lock.
                DIAGNOSTIC_LOG.info("NEW232 stage=server_consume token={} accepted={} proofExists={} sameServer={} sameGrant={} clock={} issued={} expires={} terminal={}",
                        grant.token(), accepted, proof != null, proof != null && proof.server == server,
                        proof != null && proof.grant.equals(grant), clock, grant.issuedAt(), grant.expiresAt(), grant.terminal());
            }
            if (!accepted) return false;
            PROOFS.remove(grant.token()); return true;
        }
    }
    public static boolean integratedFresh(Grant grant, MinecraftServer server) {
        synchronized (PROOF_LOCK) { return freshProof(grant, server); }
    }
    private static boolean freshProof(Grant grant, MinecraftServer server) {
        if (grant == null || server == null || grant.terminal()) return false;
        Proof proof = PROOFS.get(grant.token()); Long tick = CLOCKS.get(server);
        return proof != null && proof.server == server && proof.grant.equals(grant) && tick != null
                && BeamStepRules.fresh(grant.issuedAt(), grant.expiresAt(), tick);
    }
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Watermark old = INPUTS.get(p.getUUID());
        if (old == null || old.epoch != input.epoch() || input.input() > old.input)
            INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        Context c = owned(p);
        if (c == null || c.input.epoch() != input.epoch() || c.input.slot() != input.slot() || input.input() < c.input.input()) return false;
        finish(c, "wire_abort"); return true;
    }
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c, "lifecycle_abort"); }
    public static void remove(ServerPlayer p) { if (!thread(p)) return; abort(p); INPUTS.remove(p.getUUID()); }
    public static boolean active(ServerPlayer p) { return owned(p) != null; }
    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null) return;
        if (!valid(c)) { finish(c, "invalid_context_or_eligibility"); return; }
        if (c.grant == null) { finish(c, "missing_grant"); return; }
        if (!BeamStepRules.fresh(c.grant.issuedAt(), c.grant.expiresAt(), now(p))) finish(c, "grant_ttl");
    }
    private static boolean valid(Context c) {
        ServerPlayer p = c.identity.owner();
        return CONTEXTS.get(p.getUUID()) == c && c.identity.owns(p) && HighPowerBracerSession.authenticated(p, c.input)
                && (c.fixture || p.server.isSingleplayer() && !p.server.isPublished()) && canAct(p)
                && c.preset == c.identity.state().presets.current() && c.revision == c.identity.state().presets.revision()
                && ID.equals(c.identity.state().presets.currentSkill(c.input.slot()));
    }
    private static boolean canAct(ServerPlayer p) {
        if (WindPressureLift.active(p)) return false;
        if (!thread(p) || !finite(p.position()) || !finite(p.getDeltaMovement())) return false;
        var box = p.getBoundingBox(); var level = p.serverLevel();
        if (box.minY < level.getMinBuildHeight() || box.maxY >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(box)) return false;
        for (int x = ((int)Math.floor(box.minX)) >> 4; x <= ((int)Math.floor(box.maxX)) >> 4; x++)
            for (int z = ((int)Math.floor(box.minZ)) >> 4; z <= ((int)Math.floor(box.maxZ)) >> 4; z++)
                if (!level.hasChunk(x,z)) return false;
        var state = AbilityStorage.get(p);
        if (!HighPowerResources.ready(p) || !state.category.equals("meltdowner") || state.level < 3 || state.level > 5
                || HighPowerResources.effectiveLevel(p) < 3 || !state.activated || !state.overloadFine || state.interfering
                || !state.learned(ID) || !state.learned("thermal_shell") || !SkillCatalog.enabled("meltdowner", ID) || HighPowerCombat.unsupportedReason(p).isPresent()
                || AbilityConsumption.busy(p) || p.containerMenu != p.inventoryMenu || p.noPhysics || p.isNoGravity()
                || p.onGround() || p.isPassenger() || p.isVehicle() || p.isSleeping() || p.isFallFlying() || p.getAbilities().flying
                || p.getAbilities().mayfly || p.getPose() != net.minecraft.world.entity.Pose.STANDING || p.horizontalCollision || p.verticalCollision
                || p.isAutoSpinAttack() || p.isInPowderSnow || p.isSwimming() || !p.getActiveEffects().isEmpty()
                || p.getGravity() != .08 || !p.getMaxHeightFluidType().isAir()
                || p.getAbilities().instabuild || p.getAbilities().invulnerable || p.isInWaterOrBubble() || p.isInLava() || p.onClimbable()
                || p.hasEffect(MobEffects.LEVITATION) || p.hasEffect(MobEffects.SLOW_FALLING) || competingMovement(p)
                || !Double.isFinite(BeamStepRules.assistedY(p.getDeltaMovement().y))) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ), BlockPos.containing(box.maxX,box.maxY,box.maxZ)))
            if (!level.getFluidState(pos).isEmpty() || !level.mayInteract(p,pos)) return false;
        return true;
    }
    private static boolean competingMovement(ServerPlayer p) {
        return MagneticGroundStep.active(p) || PolarityTether.active(p) || HighPowerBracerSession.active(p)
                || cn.academy.port.skill.MagMovement.active(p) || cn.academy.port.skill.VecAccel.active(p)
                || cn.academy.port.skill.JetEngine.active(p) || cn.academy.port.skill.StormWing.active(p)
                || cn.academy.port.skill.MarkTeleport.active(p) || cn.academy.port.skill.PenetrateTeleport.active(p)
                || cn.academy.port.skill.Flashing.active(p);
    }
    private static long[] quota(ServerPlayer p) {
        var data = p.getPersistentData();
        return data.contains(QUOTA) && !data.contains(QUOTA, Tag.TAG_LONG_ARRAY) ? null : data.getLongArray(QUOTA);
    }
    private static boolean quotaReady(ServerPlayer p) { return BeamStepRules.quotaReady(quota(p), now(p)); }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static boolean thread(ServerPlayer p) { return p != null && p.server.isSameThread(); }
    private static long now(ServerPlayer p) { return p.server.overworld().getGameTime(); }
    private static Context owned(ServerPlayer p) {
        if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID());
        return c != null && c.identity.owner() == p ? c : null;
    }
    private static void finish(Context c) { finish(c, "payment_or_prepublication_refusal"); }
    private static void finish(Context c, String reason) {
        synchronized (PROOF_LOCK) { PROOFS.remove(c.identity.token()); }
        if (!CONTEXTS.remove(c.identity.owner().getUUID(), c)) return;
        if (DIAGNOSTICS) diagnostic(c, "server_finish", "reason=" + reason + " " + serverSnapshot(c));
        ServerPlayer p = c.identity.owner();
        if (c.grant != null && !p.hasDisconnected()) {
            Grant g = c.grant;
            PacketDistributor.sendToPlayer(p, new Grant(g.entity(),g.owner(),g.input(),g.token(),g.epoch(),g.dimension(),g.issuedAt(),g.expiresAt(),true));
        }
    }
    /** Diagnostic-only server-thread snapshot, never evaluated on the integrated render thread. */
    private static String serverSnapshot(Context c) {
        ServerPlayer p = c.identity.owner();
        if (!thread(p)) return "not_server_thread";
        var state = AbilityStorage.get(p);
        return "owns=" + c.identity.owns(p) + " authenticated=" + HighPowerBracerSession.authenticated(p,c.input)
                + " preset=" + state.presets.current() + "/" + c.preset + " revision=" + state.presets.revision() + "/" + c.revision
                + " slot=" + state.presets.currentSkill(c.input.slot()) + " category=" + state.category + " level=" + state.level
                + " activated=" + state.activated + " overloadFine=" + state.overloadFine + " interfering=" + state.interfering
                + " busy=" + AbilityConsumption.busy(p) + " position=" + p.position() + " velocity=" + p.getDeltaMovement()
                + " onGround=" + p.onGround() + " horizontalCollision=" + p.horizontalCollision + " verticalCollision=" + p.verticalCollision
                + " pose=" + p.getPose() + " gravity=" + p.getGravity() + " noGravity=" + p.isNoGravity() + " noPhysics=" + p.noPhysics
                + " effects=" + p.getActiveEffects().size() + " vehicle=" + p.isVehicle() + " passenger=" + p.isPassenger()
                + " sleeping=" + p.isSleeping() + " fallFlying=" + p.isFallFlying() + " flying=" + p.getAbilities().flying
                + " mayfly=" + p.getAbilities().mayfly + " instabuild=" + p.getAbilities().instabuild + " invulnerable=" + p.getAbilities().invulnerable
                + " water=" + p.isInWaterOrBubble() + " lava=" + p.isInLava() + " fluidAir=" + p.getMaxHeightFluidType().isAir()
                + " climbing=" + (p.serverLevel().hasChunkAt(p.blockPosition()) && p.onClimbable()) + " menu=" + (p.containerMenu != p.inventoryMenu)
                + " powderSnow=" + p.isInPowderSnow + " swimming=" + p.isSwimming() + " autoSpin=" + p.isAutoSpinAttack();
    }
    private static void diagnostic(Context c, String stage, String detail) {
        Grant grant = c.grant;
        DIAGNOSTIC_LOG.info("NEW232 stage={} token={} input={} now={} issued={} expires={} {}",stage,c.identity.token(),c.input.input(),
                now(c.identity.owner()),grant == null ? -1 : grant.issuedAt(),grant == null ? -1 : grant.expiresAt(),detail);
    }
    public static void clear() {
        CONTEXTS.clear(); INPUTS.clear(); synchronized (PROOF_LOCK) { PROOFS.clear(); CLOCKS.clear(); }
    }
    public record Grant(int entity, UUID owner, long input, long token, long epoch, ResourceLocation dimension,
                        long issuedAt, long expiresAt, boolean terminal) implements CustomPacketPayload {
        public static final Type<Grant> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy","hp_beam_step_grant"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Grant> CODEC = StreamCodec.of((b,g) -> {
            b.writeInt(g.entity); b.writeUUID(g.owner); b.writeLong(g.input); b.writeLong(g.token); b.writeLong(g.epoch);
            b.writeResourceLocation(g.dimension); b.writeLong(g.issuedAt); b.writeLong(g.expiresAt); b.writeBoolean(g.terminal);
        }, b -> new Grant(b.readInt(),b.readUUID(),b.readLong(),b.readLong(),b.readLong(),b.readResourceLocation(),b.readLong(),b.readLong(),b.readBoolean()));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Visual(int entity, UUID owner, long token, long epoch, ResourceLocation dimension, Vec3 at,
                         long issuedAt, long expiresAt, boolean terminal) implements CustomPacketPayload {
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy","hp_beam_step_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b,g) -> {
            b.writeInt(g.entity); b.writeUUID(g.owner); b.writeLong(g.token); b.writeLong(g.epoch); b.writeResourceLocation(g.dimension);
            b.writeDouble(g.at.x); b.writeDouble(g.at.y); b.writeDouble(g.at.z); b.writeLong(g.issuedAt); b.writeLong(g.expiresAt); b.writeBoolean(g.terminal);
        }, b -> new Visual(b.readInt(),b.readUUID(),b.readLong(),b.readLong(),b.readResourceLocation(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readLong(),b.readLong(),b.readBoolean()));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(Grant.TYPE,Grant.CODEC,(packet,context) -> motionReceiver.accept(packet));
            event.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(packet,context) -> clientReceiver.accept(packet));
        }
    }
    private static void clockAndValidate(MinecraftServer server) {
        synchronized (PROOF_LOCK) {
            CLOCKS.put(server, server.overworld().getGameTime());
            for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == server && c.grant != null) tick(c.identity.owner());
        }
    }
    @SubscribeEvent public static void pre(ServerTickEvent.Pre event) { clockAndValidate(event.getServer()); }
    @SubscribeEvent public static void post(ServerTickEvent.Post event) { clockAndValidate(event.getServer()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer p) {
            var data = p.getPersistentData();
            if (data.contains(QUOTA)) event.getEntity().getPersistentData().put(QUOTA, data.get(QUOTA).copy());
            remove(p);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { clear(); }
}

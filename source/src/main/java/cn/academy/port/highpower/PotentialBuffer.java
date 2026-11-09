package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.PotentialBufferRules;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW142. One authenticated directional shared-shield controller; paid bounded native mob knockback only. */
@EventBusSubscriber(modid = "academy")
public final class PotentialBuffer {
    public static final String ID = "potential_buffer";
    public static final int ACTIVE = 0, HIT = 1, PULSE = 2, ENDED = 3;
    private static final String DATA = "academy:potential_buffer", PULSE_UNTIL = "pulseUntil", MASTERY_UNTIL = "masteryUntil", ACTIVATE_UNTIL = "activateUntil";
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<LivingEntity, Attempt> ATTEMPTS = new IdentityHashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(long epoch, long input) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset;
        final long revision, started;
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        long lastTick = -1, sequence;
        int pulses;
        boolean processing;
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision(); started = now(p);
        }
    }
    /** Snapshot is opaque and one-use, bound to the exact activation and incoming origin/facing. */
    static final class Capture {
        private final Context context;
        private final Vec3 ownerPosition, sourcePosition, facing, impact;
        private final long tick;
        private boolean committed;
        private Capture(Context c, Vec3 source, Vec3 facing) {
            context = c; ownerPosition = c.identity.owner().position(); sourcePosition = source; this.facing = facing; tick = now(c.identity.owner());
            Vec3 direction = new Vec3(source.x - ownerPosition.x, 0, source.z - ownerPosition.z).normalize();
            impact = ownerPosition.add(direction.scale(.75)).add(0, 1, 0);
        }
    }
    private static final class Attempt {
        final Context context;
        final LivingEntity target;
        final Vec3 ownerPosition, targetPosition, point;
        final long tick;
        final double ratioX, ratioZ;
        LivingKnockBackEvent event;
        Attempt(Context c, LivingEntity e) {
            context = c; target = e; ownerPosition = c.identity.owner().position(); targetPosition = e.position(); point = e.getBoundingBox().getCenter(); tick = now(c.identity.owner());
            // Native knockback subtracts this normalized vector, so owner-minus-target pushes away.
            ratioX = ownerPosition.x - targetPosition.x; ratioZ = ownerPosition.z - targetPosition.z;
        }
    }
    private PotentialBuffer() {}

    /** Each fresh authenticated tap toggles only this controller. Key release is deliberately irrelevant. */
    public static boolean toggle(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input) || AbilityConsumption.busy(p)) return false;
        var old = INPUTS.get(p.getUUID());
        if (old != null && old.epoch == input.epoch() && input.input() <= old.input) return false;
        INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        if (!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))) return false;
        Context previous = CONTEXTS.get(p.getUUID());
        if (previous != null) {
            boolean same = previous.identity.owner() == p && valid(previous, false);
            finish(previous);
            if (same) return true;
        }
        if (!canAct(p, false) || !AbilityStorage.get(p).canUse(ID) || nextToken == Long.MAX_VALUE
                || !fenceReady(p, ACTIVATE_UNTIL, now(p))) return false;
        var before = HighPowerCombat.exactState(p);
        if (before.isEmpty() || before.get().isDead()) return false;
        // Activation is free, but new input/epoch spam cannot create more than one controller per server tick.
        stamp(p, ACTIVATE_UNTIL, now(p) + 1);
        Context c = new Context(p, input); CONTEXTS.put(p.getUUID(), c); send(c, ACTIVE, p.position(), p.getLookAngle()); return true;
    }
    public static boolean start(ServerPlayer p, String wire) { return toggle(p, wire); }
    public static boolean begin(ServerPlayer p, String wire) { return toggle(p, wire); }
    /** A focus cancel fences delayed taps but cannot terminate an activation with a newer input. */
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire); if (!HighPowerBracerSession.authenticated(p, input)) return false;
        var old = INPUTS.get(p.getUUID());
        if (old == null || old.epoch != input.epoch() || input.input() > old.input) INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        Context c = owned(p);
        if (c == null || c.input.epoch() != input.epoch() || c.input.slot() != input.slot() || c.input.input() > input.input()) return false;
        finish(c); return true;
    }
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c); }
    public static void remove(ServerPlayer p) { if (!thread(p)) return; abort(p); INPUTS.remove(p.getUUID()); }
    public static void clear() { for (Context c : new ArrayList<>(CONTEXTS.values())) finish(c); CONTEXTS.clear(); INPUTS.clear(); ATTEMPTS.clear(); }
    public static boolean active(ServerPlayer p) {
        Context c = owned(p); if (c == null) return false;
        if (!valid(c, false)) { finish(c); return false; } return true;
    }
    public static int pulseCount(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.pulses; }

    /** HighPowerCombat calls this only for its explicit reviewed source whitelist at Incoming, before callbacks. */
    static Capture capture(ServerPlayer p, Vec3 sourcePosition, Vec3 facing) {
        Context c = owned(p); if (c == null) return null;
        if (!valid(c, false)) { finish(c); return null; }
        if (!PotentialBufferRules.front(point(p.position()), point(facing), point(sourcePosition))) return null;
        return new Capture(c, sourcePosition, facing.normalize());
    }
    /** Pure plan: never writes resources, observes callbacks, awards XP or emits a pulse. */
    static PotentialBufferRules.Result plan(ServerPlayer p, Capture capture, ExactCombatState before, BigDecimal damage) {
        return before == null || before.isDead() || !matches(p, capture) ? null : PotentialBufferRules.absorb(before, damage);
    }
    static boolean matches(ServerPlayer p, Capture capture) {
        return capture != null && !capture.committed && owned(p) == capture.context && valid(capture.context, true)
                && now(p) == capture.tick && p.position().equals(capture.ownerPosition)
                && PotentialBufferRules.front(point(capture.ownerPosition), point(capture.facing), point(capture.sourcePosition));
    }
    /** Only called after the bridge's one ledger/history/lethal/normalized-HP commit. */
    static void committed(ServerPlayer p, Capture capture, PotentialBufferRules.Result result) {
        if (result == null || result.absorbed().signum() <= 0 || !matches(p, capture)) return;
        Context c = capture.context; capture.committed = true;
        send(c, HIT, capture.impact, capture.facing);
        // A nested committed hit can absorb once through the bridge, but never recurse into pulse/mastery callbacks.
        if (c.processing) return;
        c.processing = true;
        try {
            long tick = now(p);
            boolean mastery = fenceReady(p, MASTERY_UNTIL, tick);
            if (mastery) stamp(p, MASTERY_UNTIL, tick + PotentialBufferRules.MASTERY_INTERVAL);
            if (valid(c, false) && fenceReady(p, PULSE_UNTIL, tick)) {
                List<LivingEntity> targets = select(p, tick);
                if (!targets.isEmpty()) {
                    // Reserve owner before payment; reserve targets after this closed debit, before events or XP. Never roll back attempts.
                    stamp(p, PULSE_UNTIL, tick + PotentialBufferRules.PULSE_INTERVAL);
                    double cost = PotentialBufferRules.pulseCost(HighPowerGrowth.values(p).position());
                    if (ElectromasterPrototype.trySpend(p, ID, cost, 1)) {
                        for (LivingEntity target : targets) stamp(target, PULSE_UNTIL, tick + PotentialBufferRules.PULSE_INTERVAL);
                        c.pulses++;
                        send(c, PULSE, p.position(), capture.facing);
                        for (LivingEntity target : targets) {
                            if (owned(p) != c || !valid(c, false) || !p.position().equals(capture.ownerPosition)) break;
                            if (!eligible(p, target) || !clearLine(p, p.getEyePosition(), target.getBoundingBox().getCenter())) continue;
                            Attempt attempt = new Attempt(c, target);
                            if (attempt.ratioX * attempt.ratioX + attempt.ratioZ * attempt.ratioZ < 1e-5 || ATTEMPTS.containsKey(target)) continue;
                            ATTEMPTS.put(target, attempt);
                            try { target.knockback(PotentialBufferRules.KNOCKBACK, attempt.ratioX, attempt.ratioZ); }
                            finally { ATTEMPTS.remove(target, attempt); }
                        }
                    }
                }
            }
            if (mastery && owned(p) == c && valid(c, true)) {
                c.identity.state().addExperience(ID, .005); AbilityStorage.save(p);
            }
            if (owned(p) == c && !valid(c, true)) finish(c);
        } finally { c.processing = false; }
    }
    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null) return;
        if (!valid(c, false)) { finish(c); return; }
        long tick = now(p); if (tick <= c.lastTick) return; c.lastTick = tick;
        if ((tick - c.started) % 4 == 0) send(c, ACTIVE, p.position(), p.getLookAngle());
    }
    private static List<LivingEntity> select(ServerPlayer p, long tick) {
        List<LivingEntity> scanned = new ArrayList<>(PotentialBufferRules.SCAN_LIMIT);
        p.serverLevel().getEntities(EntityTypeTest.forClass(LivingEntity.class), p.getBoundingBox().inflate(PotentialBufferRules.RANGE),
                e -> true, scanned, PotentialBufferRules.SCAN_LIMIT);
        scanned.removeIf(e -> !eligible(p, e) || !fenceReady(e, PULSE_UNTIL, tick)
                || !clearLine(p, p.getEyePosition(), e.getBoundingBox().getCenter()));
        scanned.sort(Comparator.<LivingEntity>comparingDouble(e -> e.position().distanceToSqr(p.position())).thenComparingInt(LivingEntity::getId));
        return List.copyOf(scanned.subList(0, Math.min(scanned.size(), PotentialBufferRules.MAX_TARGETS)));
    }
    private static boolean eligible(ServerPlayer p, LivingEntity e) {
        if (!(HighPowerCombat.hostileTarget(e)) || !HighPowerCombat.supported(e) || e.level() != p.serverLevel() || !e.isAlive() || e.isRemoved()
                || e.isSpectator() || e.isPassenger() || e.isVehicle() || e.isInvulnerable() || p.isAlliedTo(e) || e.isAlliedTo(p)
                || p.serverLevel().getEntity(e.getId()) != e || p.serverLevel().getEntity(e.getUUID()) != e
                || HighPowerCombat.unsupportedReason(e).isPresent() || !finite(e.position()) || !finite(e.getDeltaMovement())
                || !permittedVolume(p, e.getBoundingBox()) || !PotentialBufferRules.inside(point(e.getBoundingBox().getCenter()), point(p.position()))) return false;
        double x = p.getX() - e.getX(), z = p.getZ() - e.getZ();
        double resistance = e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        return x * x + z * z >= 1e-5 && Double.isFinite(resistance) && resistance >= 0 && resistance <= 1;
    }
    private static boolean canAct(ServerPlayer p, boolean insideDamage) {
        if (!thread(p) || p.connection == null || p.hasDisconnected() || p.isRemoved() || p.isSpectator()
                || !insideDamage && !p.isAlive() || p.serverLevel().getEntity(p.getId()) != p || p.getAbilities().instabuild) return false;
        var s = AbilityStorage.get(p);
        return HighPowerCombat.enabled(p) && s.category.equals("electromaster") && s.activated && s.overloadFine && !s.interfering
                && s.learned(ID) && s.learned(GroundingReturn.ID) && s.level >= 4 && s.level <= 5 && ElectromasterPrototype.effectiveLevel(p) >= 4
                && SkillCatalog.enabled("electromaster", ID) && HighPowerCombat.unsupportedReason(p).isEmpty() && !AbilityConsumption.busy(p)
                && finite(p.position()) && finite(p.getLookAngle()) && Float.isFinite(p.getYRot()) && Float.isFinite(p.getXRot()) && PotentialBufferRules.clock(now(p));
    }
    private static boolean valid(Context c, boolean insideDamage) {
        ServerPlayer p = c.identity.owner();
        return c.identity.owns(p) && canAct(p, insideDamage) && now(p) >= c.started
                && c.preset == c.identity.state().presets.current() && c.revision == c.identity.state().presets.revision()
                && ID.equals(c.identity.state().presets.currentSkill(c.input.slot()));
    }
    private static boolean permitted(ServerPlayer p, BlockPos pos) {
        ServerLevel w = p.serverLevel();
        return !w.isOutsideBuildHeight(pos) && w.hasChunkAt(pos) && w.getWorldBorder().isWithinBounds(pos) && w.mayInteract(p, pos);
    }
    private static boolean permittedVolume(ServerPlayer p, AABB box) {
        if (!finite(new Vec3(box.minX, box.minY, box.minZ)) || !finite(new Vec3(box.maxX, box.maxY, box.maxZ))
                || box.getXsize() > 4 || box.getYsize() > 4 || box.getZsize() > 4) return false;
        for (int x = (int)Math.floor(box.minX); x <= Math.floor(box.maxX); x++)
            for (int y = (int)Math.floor(box.minY); y <= Math.floor(box.maxY); y++)
                for (int z = (int)Math.floor(box.minZ); z <= Math.floor(box.maxZ); z++) if (!permitted(p, new BlockPos(x, y, z))) return false;
        return true;
    }
    private static boolean clearLine(ServerPlayer p, Vec3 from, Vec3 to) {
        if (!finite(from) || !finite(to) || from.distanceToSqr(to) > 8 * 8) return false;
        for (int x = ((int)Math.floor(Math.min(from.x, to.x))) >> 4; x <= ((int)Math.floor(Math.max(from.x, to.x))) >> 4; x++)
            for (int z = ((int)Math.floor(Math.min(from.z, to.z))) >> 4; z <= ((int)Math.floor(Math.max(from.z, to.z))) >> 4; z++) if (!p.serverLevel().hasChunk(x, z)) return false;
        int samples = Math.max(1, (int)Math.ceil(from.distanceTo(to) * 2));
        for (int i = 0; i <= samples; i++) if (!permitted(p, BlockPos.containing(from.lerp(to, (double)i / samples)))) return false;
        return p.serverLevel().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }
    private static boolean fenceReady(LivingEntity e, String field, long tick) {
        CompoundTag root = e.getPersistentData(); if (root.contains(DATA) && !root.contains(DATA, Tag.TAG_COMPOUND)) return false;
        CompoundTag tag = root.getCompound(DATA);
        return (!tag.contains(field) || tag.contains(field, Tag.TAG_LONG)) && PotentialBufferRules.ready(tag.getLong(field), tick);
    }
    private static void stamp(LivingEntity e, String field, long until) {
        CompoundTag tag = e.getPersistentData().getCompound(DATA); tag.putLong(field, Math.max(tag.getLong(field), until)); e.getPersistentData().put(DATA, tag);
    }
    private static Context owned(ServerPlayer p) { if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    private static boolean thread(ServerPlayer p) { return p != null && p.server != null && p.server.isSameThread(); }
    private static long now(ServerPlayer p) { return p.server.overworld().getGameTime(); }
    private static boolean finite(Vec3 p) { return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
    private static PotentialBufferRules.Point point(Vec3 p) { return p == null ? null : new PotentialBufferRules.Point(p.x, p.y, p.z); }
    private static void finish(Context c) {
        if (!CONTEXTS.remove(c.identity.owner().getUUID(), c)) return;
        send(c, ENDED, c.identity.owner().position(), c.identity.owner().getLookAngle()); c.audience.clear();
    }
    private static void send(Context c, int phase, Vec3 point, Vec3 facing) {
        ServerPlayer p = c.identity.owner(); if (!finite(point) || !finite(facing)) return;
        // This toggle has no fixed lifetime: never retain historical disconnected or distant viewer objects.
        c.audience.removeIf(viewer -> viewer.connection == null || viewer.hasDisconnected() || viewer.isRemoved()
                || viewer.serverLevel() != c.identity.level() || viewer.position().distanceToSqr(p.position()) > 48 * 48);
        for (ServerPlayer viewer : c.identity.level().players()) if (viewer.position().distanceToSqr(p.position()) <= 32 * 32) c.audience.add(viewer);
        c.audience.add(p);
        var packet = new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), ++c.sequence, phase, p.position(), facing.normalize(), point);
        for (ServerPlayer viewer : c.audience) if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == c.identity.level()) PacketDistributor.sendToPlayer(viewer, packet);
    }
    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase, Vec3 point, Vec3 facing, Vec3 impact) implements CustomPacketPayload {
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_potential_buffer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.epoch); b.writeLong(v.sequence); b.writeByte(v.phase);
            b.writeDouble(v.point.x); b.writeDouble(v.point.y); b.writeDouble(v.point.z); b.writeDouble(v.facing.x); b.writeDouble(v.facing.y); b.writeDouble(v.facing.z); b.writeDouble(v.impact.x); b.writeDouble(v.impact.y); b.writeDouble(v.impact.z);
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readUnsignedByte(),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), new Vec3(b.readDouble(), b.readDouble(), b.readDouble())));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    /** Reserve the exact native event so callback reentry cannot borrow this pulse's authorization. */
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void captureKnockback(LivingKnockBackEvent event) {
        Attempt a = ATTEMPTS.get(event.getEntity()); if (a == null) return;
        if (a.event != null) event.setCanceled(true); else a.event = event;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void validateKnockback(LivingKnockBackEvent event) {
        Attempt a = ATTEMPTS.get(event.getEntity()); if (a == null) return;
        ServerPlayer p = a.context.identity.owner();
        if (a.event != event || owned(p) != a.context || !valid(a.context, false) || now(p) != a.tick
                || !p.position().equals(a.ownerPosition) || !a.target.position().equals(a.targetPosition)
                || !a.target.getBoundingBox().getCenter().equals(a.point) || !eligible(p, a.target)
                || !clearLine(p, p.getEyePosition(), a.point) || event.getStrength() != (float)PotentialBufferRules.KNOCKBACK
                || event.getRatioX() != a.ratioX || event.getRatioZ() != a.ratioZ) event.setCanceled(true);
    }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) { e.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet)); }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) { for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) tick(c.identity.owner()); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void clone(PlayerEvent.Clone e) {
        if (e.getOriginal() instanceof ServerPlayer old && e.getEntity() instanceof ServerPlayer replacement) {
            remove(old); CompoundTag source = old.getPersistentData().getCompound(DATA);
            for (String field : List.of(PULSE_UNTIL, MASTERY_UNTIL, ACTIVATE_UNTIL)) if (source.contains(field, Tag.TAG_LONG) && source.getLong(field) > now(replacement)) stamp(replacement, field, source.getLong(field));
        }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) { for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) finish(c); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { clear(); }
}

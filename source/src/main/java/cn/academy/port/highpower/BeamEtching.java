package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.BeamEtchingRules;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW213: harmless visible-surface preparation, followed by a frozen native-harvest queue. */
@EventBusSubscriber(modid = "academy")
public final class BeamEtching {
    public static final String ID = "beam_etching", TIMING_KEY = "academy:beam_etching_timing", MASTERY_KEY = "academy:beam_etching_mastery";
    public static final int PREVIEW = 0, CONFIRMED = 1, ENDED = 2, READY = 0, UNAVAILABLE = 1, NO_SUCCESS = 2;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(long epoch, long input) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset, selected, cooldown;
        final long revision, started;
        final double cost;
        final BeamEtchingMining.Capacity capacity = BeamEtchingMining.newCapacity();
        final LinkedHashMap<BlockPos, BeamEtchingMining.Target> marks = new LinkedHashMap<>();
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        List<BeamEtchingMining.Target> frozen;
        List<ItemStack> inventory;
        long lastHoldTick = -1, sequence;
        int next, successes;
        boolean processing, cooldownApplied, unavailable;
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            selected = p.getInventory().selected; started = now(p); inventory = inventory(p);
            var q = HighPowerGrowth.values(p).position(); cost = BeamEtchingRules.cost(q); cooldown = BeamEtchingRules.cooldown(q);
        }
    }
    private BeamEtching() {}

    public static boolean start(ServerPlayer p, String wire) {
        Context existing = owned(p); if (existing != null && existing.processing) return false;
        HighPowerInput.Input input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Watermark old = INPUTS.get(p.getUUID());
        if (old != null && old.epoch == input.epoch() && old.input >= input.input()) return false;
        if (existing != null) { if (valid(existing)) return false; finish(existing); }
        Context foreign = CONTEXTS.get(p.getUUID()); if (foreign != null) finish(foreign);
        INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        var timing = readTiming(p);
        if (!canAct(p) || nextToken == Long.MAX_VALUE || timing == null || now(p) < timing.stopUntil()
                || !timing.previewReady(now(p)) || !AbilityStorage.get(p).canUse(ID)
                || !ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))) return false;
        Context c = new Context(p, input); CONTEXTS.put(p.getUUID(), c); tick(p); return owned(p) == c;
    }
    /** Release owns exactly the same authenticated press; it never samples a new target. */
    public static boolean release(ServerPlayer p, String wire) {
        Context c = owned(p); var input = HighPowerInput.parse(wire);
        if (c == null || c.processing || c.frozen != null || !HighPowerBracerSession.authenticated(p, input) || !c.input.equals(input)) return false;
        if (!valid(c) || !inventoryUnchanged(c) || BeamEtchingRules.expired(c.started, now(p))) { finish(c); return false; }
        c.frozen = List.copyOf(c.marks.values());
        if (c.frozen.isEmpty()) { finish(c); return true; }
        work(c); return true;
    }
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire); Context c = owned(p);
        if (c == null || !HighPowerBracerSession.authenticated(p, input) || !c.input.equals(input)) return false;
        finish(c); return true;
    }
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c); }
    public static void remove(ServerPlayer p) { if (thread(p)) { abort(p); INPUTS.remove(p.getUUID()); } }
    public static boolean active(ServerPlayer p) { Context c = owned(p); return c != null && valid(c); }
    public static boolean holding(ServerPlayer p) { Context c = owned(p); return c != null && c.frozen == null && valid(c); }
    /** Only the synchronous, authenticated native work scope may pass its own one-tick guard. */
    public static boolean executing(ServerPlayer p) { Context c = owned(p); return c != null && c.processing && c.frozen != null && valid(c); }
    public static List<Mark> marks(ServerPlayer p) { Context c = owned(p); return c == null ? List.of() : c.marks.values().stream().map(BeamEtching::mark).toList(); }
    public static int completed(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.successes; }

    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null || c.processing) return;
        if (!valid(c) || !inventoryUnchanged(c)) { finish(c); return; }
        if (c.frozen != null) { work(c); return; }
        long tick = now(p);
        if (BeamEtchingRules.expired(c.started, tick) || tick < c.lastHoldTick) { finish(c); return; }
        if (tick == c.lastHoldTick) return;
        c.lastHoldTick = tick;
        var timing = readTiming(p); if (timing == null) { finish(c); return; }
        BeamEtchingMining.Target candidate = aim(p);
        boolean unavailable = candidate == null || !BeamEtchingMining.inspect(p, candidate, c.capacity).eligible();
        if (!unavailable && c.marks.size() < BeamEtchingRules.MAX_MARKS && !c.marks.containsKey(candidate.pos()) && timing.acquireReady(tick)) {
            // The persistent tick is consumed before mutating this cast, including on later abort.
            writeTiming(p, timing.acquire(tick)); c.marks.put(candidate.pos(), candidate);
        }
        timing = readTiming(p);
        if (timing != null && timing.previewReady(tick)) {
            for (var target : c.marks.values()) if (!visible(p, target)) { finish(c); return; }
            writeTiming(p, timing.preview(tick));
            send(c, PREVIEW, c.marks.values().stream().map(BeamEtching::mark).toList(), unavailable ? UNAVAILABLE : READY,
                    (int)(BeamEtchingRules.HOLD_TICKS - (tick - c.started)));
        }
    }
    private static void work(Context c) {
        if (c.processing || c.frozen == null) return;
        c.processing = true;
        try {
            while (c.next < c.frozen.size()) {
                ServerPlayer p = c.identity.owner(); var timing = readTiming(p);
                if (!valid(c) || !inventoryUnchanged(c) || timing == null) { c.unavailable = true; finish(c); return; }
                if (!timing.attemptReady(now(p))) return;
                var target = c.frozen.get(c.next);
                // Refuse the first changed/blocked slot. Never skip it or reacquire through its hole.
                if (!visible(p, target)) { c.unavailable = true; finish(c); return; }
                if (!claimSharedAttempt(p)) return; c.next++;
                Vec3 origin = p.getEyePosition();
                var result = BeamEtchingMining.attempt(p, target, c.cost, () -> valid(c) && origin.equals(p.getEyePosition()), c.capacity);
                if (!result.removed()) { c.unavailable = true; finish(c); return; }
                c.successes++;
                if (!valid(c)) { c.unavailable = true; finish(c); applyCooldown(c); return; }
                // The shared removal reservation intentionally has no cooldown gate: two native
                // removals may settle this frame. Only this live controller owns the client guard.
                c.identity.state().setCooldown(ID, 1);
                boolean inventoryValid = nativeInventoryTransition(c);
                // A post-removal callback cannot rewrite the origin of this already-observed ray.
                send(c, CONFIRMED, List.of(mark(target)), READY, 0, origin);
                reward(c);
                if (!inventoryValid || !valid(c) || !origin.equals(p.getEyePosition()) || !result.reason().isEmpty()) { c.unavailable = true; finish(c); return; }
            }
            finish(c);
        } finally { c.processing = false; }
    }
    /** Only native durability is admitted between successes; unrelated slot changes retire the cast. */
    private static boolean nativeInventoryTransition(Context c) {
        ServerPlayer p = c.identity.owner(); var actual = p.getInventory();
        if (actual.selected != c.selected || actual.getContainerSize() != c.inventory.size()) return false;
        for (int i=0; i<c.inventory.size(); i++) {
            ItemStack before = c.inventory.get(i), after = actual.getItem(i);
            if (ItemStack.matches(before, after)) continue;
            if (i != c.selected || before.isEmpty() || after.isEmpty() || !before.isDamageableItem()
                    || !after.isDamageableItem() || before.getDamageValue() > after.getDamageValue()) return false;
            ItemStack permitted = before.copy(); permitted.setDamageValue(after.getDamageValue());
            if (!ItemStack.matches(permitted, after)) return false;
        }
        c.inventory = inventory(p); return true;
    }

    private static void reward(Context c) {
        if (!valid(c)) return;
        ServerPlayer p = c.identity.owner(); long tick = now(p); var data = p.getPersistentData();
        if (data.contains(MASTERY_KEY)) {
            if (!data.contains(MASTERY_KEY, Tag.TAG_LONG) || !BeamEtchingRules.clock(data.getLong(MASTERY_KEY))
                    || !BeamEtchingRules.ready(data.getLong(MASTERY_KEY), tick, BeamEtchingRules.EXPERIENCE_INTERVAL)) return;
        }
        // Persist before addExperience's callbacks; failure/idle work never reaches this seam.
        data.putLong(MASTERY_KEY, tick); c.identity.state().addExperience(ID, .001); AbilityStorage.save(p);
    }
    private static boolean canAct(ServerPlayer p) {
        if (!thread(p) || p.connection == null || p.hasDisconnected() || !p.isAlive() || p.isRemoved() || p.isSpectator()
                || p.getAbilities().instabuild || p.getAbilities().invulnerable || p.noPhysics || p.isPassenger() || p.isSleeping()
                || p.containerMenu != p.inventoryMenu || AbilityConsumption.busy(p) || !HighPowerResources.ready(p)
                || HighPowerCombat.unsupportedReason(p).isPresent()) return false;
        var s = AbilityStorage.get(p);
        return s.category.equals("meltdowner") && s.activated && s.overloadFine && !s.interfering && s.learned(ID) && s.learned(DualPoint.ID)
                && s.level >= 1 && s.level <= 5 && SkillCatalog.enabled("meltdowner", ID) && BeamEtchingRules.clock(now(p))
                && finite(p.position()) && finite(p.getEyePosition()) && finite(p.getLookAngle()) && finite(p.getDeltaMovement())
                && p.getLookAngle().lengthSqr() > .5 && Float.isFinite(p.getXRot()) && Float.isFinite(p.getYRot());
    }
    private static boolean valid(Context c) {
        ServerPlayer p = c.identity.owner();
        return owned(p) == c && c.identity.owns(p) && HighPowerBracerSession.authenticated(p, c.input) && canAct(p)
                && now(p) >= c.started && c.preset == c.identity.state().presets.current() && c.revision == c.identity.state().presets.revision()
                && ID.equals(c.identity.state().presets.currentSkill(c.input.slot()));
    }
    private static List<ItemStack> inventory(ServerPlayer p) {
        List<ItemStack> items = new ArrayList<>(p.getInventory().getContainerSize());
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) items.add(p.getInventory().getItem(i).copy());
        return items;
    }
    private static boolean inventoryUnchanged(Context c) {
        var inventory = c.identity.owner().getInventory();
        if (inventory.selected != c.selected || inventory.getContainerSize() != c.inventory.size()) return false;
        for (int i = 0; i < c.inventory.size(); i++) if (!ItemStack.matches(c.inventory.get(i), inventory.getItem(i))) return false;
        return true;
    }
    private static BeamEtchingMining.Target aim(ServerPlayer p) {
        Vec3 eye = p.getEyePosition(); var bounded = BeamEtchingRules.endpoint(point(eye), point(p.getLookAngle().normalize()));
        if (bounded == null) return null;
        var hit = firstVisible(p, eye, new Vec3(bounded.x(), bounded.y(), bounded.z()));
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || hit.isInside() || !within(eye, hit.getLocation())) return null;
        var state = p.serverLevel().getBlockState(hit.getBlockPos());
        return new BeamEtchingMining.Target(hit.getBlockPos().immutable(), state, hit.getLocation(), hit.getDirection());
    }
    public static boolean visible(ServerPlayer p, BeamEtchingMining.Target target) {
        if (target == null || !within(p.getEyePosition(), target.surface()) || !p.serverLevel().hasChunkAt(target.pos())
                || !p.serverLevel().getBlockState(target.pos()).equals(target.state())) return false;
        // Nudge into the block along this ray, not along the face normal: a normal offset leaves the line of sight and,
        // at the shallow angles of ordinary ground aiming, lands the re-cast hit well outside the surface tolerance.
        Vec3 inward = target.surface().add(target.surface().subtract(p.getEyePosition()).normalize().scale(.0001));
        var hit = firstVisible(p, p.getEyePosition(), inward);
        return hit != null && hit.getType() == HitResult.Type.BLOCK && !hit.isInside() && hit.getBlockPos().equals(target.pos())
                && hit.getDirection() == target.face() && hit.getLocation().distanceToSqr(target.surface()) < .000001;
    }
    /** Each short slice is checked before clip can read it; unseen chunks are never requested. */
    private static BlockHitResult firstVisible(ServerPlayer p, Vec3 start, Vec3 end) {
        if (!finite(start) || !finite(end) || start.distanceToSqr(end) > 12.001 * 12.001) return null;
        int pieces = Math.max(1, (int)Math.ceil(start.distanceTo(end) / .25)); Vec3 previous = start;
        for (int i = 1; i <= pieces; i++) {
            Vec3 next = start.lerp(end, (double)i/pieces);
            if (!permittedSlice(p, new AABB(previous, next).inflate(.00001))) return null;
            BlockHitResult hit = p.serverLevel().clip(new ClipContext(previous, next, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, p));
            if (hit.getType() == HitResult.Type.BLOCK) return hit;
            previous = next;
        }
        return null;
    }
    private static boolean permittedSlice(ServerPlayer p, AABB box) {
        var world = p.serverLevel();
        if (box.minY < world.getMinBuildHeight() || box.maxY >= world.getMaxBuildHeight() || !world.getWorldBorder().isWithinBounds(box)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
            if (!world.hasChunkAt(pos) || !world.mayInteract(p, pos)) return false;
        return true;
    }
    /** Existing NEW213 persistent two-attempt quota also bounds the closed NEW231 tool. */
    static boolean sharedAttemptReady(ServerPlayer p) {
        if (!thread(p)) return false;
        var timing = readTiming(p);
        return timing != null && timing.attemptReady(now(p));
    }
    static boolean claimSharedAttempt(ServerPlayer p) {
        if (!thread(p)) return false;
        var timing = readTiming(p);
        if (timing == null || !timing.attemptReady(now(p))) return false;
        writeTiming(p, timing.attempt(now(p)));
        return true;
    }
    private static BeamEtchingRules.Timing readTiming(ServerPlayer p) {
        long now = now(p); if (!BeamEtchingRules.clock(now)) return null;
        var data = p.getPersistentData(); if (!data.contains(TIMING_KEY)) return BeamEtchingRules.Timing.initial(now);
        if (!data.contains(TIMING_KEY, Tag.TAG_COMPOUND)) return null; var tag = data.getCompound(TIMING_KEY);
        if (!tag.getAllKeys().equals(Set.of("version", "observed", "previewAt", "acquireAt", "attemptAt", "attempts", "stopUntil"))
                || !tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != 1 || !tag.contains("attempts", Tag.TAG_INT)) return null;
        for (String key : List.of("observed", "previewAt", "acquireAt", "attemptAt", "stopUntil")) if (!tag.contains(key, Tag.TAG_LONG)) return null;
        try {
            var value = new BeamEtchingRules.Timing(tag.getLong("observed"), tag.getLong("previewAt"), tag.getLong("acquireAt"), tag.getLong("attemptAt"), tag.getInt("attempts"), tag.getLong("stopUntil"));
            return value.current(now) ? value : null;
        } catch (IllegalArgumentException malformed) { return null; }
    }
    private static void writeTiming(ServerPlayer p, BeamEtchingRules.Timing value) {
        var tag = new CompoundTag(); tag.putInt("version", 1); tag.putLong("observed", value.observed()); tag.putLong("previewAt", value.previewAt());
        tag.putLong("acquireAt", value.acquireAt()); tag.putLong("attemptAt", value.attemptAt()); tag.putInt("attempts", value.attempts()); tag.putLong("stopUntil", value.stopUntil());
        p.getPersistentData().put(TIMING_KEY, tag);
    }
    private static void applyCooldown(Context c) {
        if (c.cooldownApplied || c.successes == 0) return;
        c.cooldownApplied = true; ServerPlayer p = c.identity.owner(); var timing = readTiming(p);
        if (timing != null) writeTiming(p, timing.finish(now(p), c.cooldown));
        c.identity.state().setCooldown(ID, c.cooldown);
    }
    private static void finish(Context c) {
        applyCooldown(c);
        if (!CONTEXTS.remove(c.identity.owner().getUUID(), c)) return;
        if (!c.audience.isEmpty()) send(c, ENDED, List.of(), c.successes == 0 ? NO_SUCCESS : c.unavailable ? UNAVAILABLE : READY, 0);
        c.audience.clear();
    }
    private static void send(Context c, int phase, List<Mark> marks, int status, int remaining) {
        send(c, phase, marks, status, remaining, c.identity.owner().getEyePosition());
    }
    private static void send(Context c, int phase, List<Mark> marks, int status, int remaining, Vec3 origin) {
        ServerPlayer p = c.identity.owner(); if (!finite(origin)) return;
        if (phase != ENDED) {
            c.audience.removeIf(v -> v.connection == null || v.hasDisconnected() || v.serverLevel() != c.identity.level() || v.position().distanceToSqr(p.position()) > 64*64);
            for (ServerPlayer viewer : c.identity.level().players()) if (viewer.distanceToSqr(p) <= 48*48) c.audience.add(viewer);
            c.audience.add(p);
        }
        Visual packet = new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), ++c.sequence, phase, origin, marks, c.successes, status, remaining);
        for (ServerPlayer viewer : c.audience) if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == c.identity.level()) PacketDistributor.sendToPlayer(viewer, packet);
    }
    private static Mark mark(BeamEtchingMining.Target target) { return new Mark(target.pos(), target.surface(), target.face()); }
    private static Context owned(ServerPlayer p) { if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    private static boolean thread(ServerPlayer p) { return p != null && p.server != null && p.server.isSameThread(); }
    private static long now(ServerPlayer p) { return MagneticDomain.serverTime(p.server); }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static BeamEtchingRules.Point point(Vec3 v) { return new BeamEtchingRules.Point(v.x, v.y, v.z); }
    private static boolean within(Vec3 origin, Vec3 end) { return finite(origin) && finite(end) && BeamEtchingRules.within(point(origin), point(end)); }

    public record Mark(BlockPos pos, Vec3 point, Direction face) {
        public Mark {
            if (pos == null || !finite(point) || face == null || point.x < pos.getX() - 1e-5 || point.x > pos.getX() + 1.00001
                    || point.y < pos.getY() - 1e-5 || point.y > pos.getY() + 1.00001 || point.z < pos.getZ() - 1e-5 || point.z > pos.getZ() + 1.00001)
                throw new IllegalArgumentException("Invalid NEW213 surface mark");
            pos = pos.immutable();
        }
    }
    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase, Vec3 origin,
                         List<Mark> marks, int confirmedCount, int status, int remainingTicks) implements CustomPacketPayload {
        public Visual {
            if (entity < 0 || owner == null || input <= 0 || token <= 0 || epoch <= 0 || sequence <= 0 || phase < PREVIEW || phase > ENDED
                    || !finite(origin) || marks == null || marks.size() > BeamEtchingRules.MAX_MARKS || confirmedCount < 0 || confirmedCount > BeamEtchingRules.MAX_MARKS
                    || status < READY || status > NO_SUCCESS || remainingTicks < 0 || remainingTicks > BeamEtchingRules.HOLD_TICKS
                    || phase == CONFIRMED && (marks.size() != 1 || confirmedCount == 0 || status != READY)
                    || phase == ENDED && !marks.isEmpty() || phase != PREVIEW && remainingTicks != 0)
                throw new IllegalArgumentException("Invalid bounded NEW213 visual");
            marks = List.copyOf(marks);
            for (Mark mark : marks) if (!within(origin, mark.point())) throw new IllegalArgumentException("NEW213 visual outside actual source range");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_beam_etching"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.epoch); b.writeLong(v.sequence); b.writeByte(v.phase); writeVec(b,v.origin);
            b.writeByte(v.marks.size()); for (Mark mark : v.marks) { b.writeBlockPos(mark.pos); writeVec(b,mark.point); b.writeByte(mark.face.ordinal()); }
            b.writeByte(v.confirmedCount); b.writeByte(v.status); b.writeByte(v.remainingTicks);
        }, b -> {
            int entity = b.readInt(); UUID owner = b.readUUID(); long input = b.readLong(), token = b.readLong(), epoch = b.readLong(), sequence = b.readLong();
            int phase = b.readUnsignedByte(); Vec3 origin = readVec(b); int count = b.readUnsignedByte();
            if (count > BeamEtchingRules.MAX_MARKS) throw new IllegalArgumentException("Oversized NEW213 marks");
            List<Mark> marks = new ArrayList<>(count);
            for (int i=0; i<count; i++) { BlockPos pos = b.readBlockPos(); Vec3 point = readVec(b); int face = b.readUnsignedByte(); if (face >= Direction.values().length) throw new IllegalArgumentException("Invalid NEW213 face"); marks.add(new Mark(pos, point, Direction.values()[face])); }
            return new Visual(entity, owner, input, token, epoch, sequence, phase, origin, marks, b.readUnsignedByte(), b.readUnsignedByte(), b.readUnsignedByte());
        });
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void writeVec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 readVec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) { e.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet)); }
    }
    public static void clear() { for (Context c : new ArrayList<>(CONTEXTS.values())) finish(c); CONTEXTS.clear(); INPUTS.clear(); }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e) { for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) tick(c.identity.owner()); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void leave(EntityLeaveLevelEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void clone(PlayerEvent.Clone e) {
        if (e.getOriginal() instanceof ServerPlayer p) {
            remove(p);
            if (e.getEntity() instanceof ServerPlayer replacement && p.getUUID().equals(replacement.getUUID()))
                for (String key : List.of(TIMING_KEY, MASTERY_KEY)) if (p.getPersistentData().contains(key)) replacement.getPersistentData().put(key, p.getPersistentData().get(key).copy());
        }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) { for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) finish(c); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { clear(); }
}

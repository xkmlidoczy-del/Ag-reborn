package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.MagneticItemPullRules;
import io.netty.handler.codec.DecoderException;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW133 owned-item first mode only. Candidate UUIDs are hints, never authority or a server search request. */
@EventBusSubscriber(modid = "academy")
public final class MagneticItemPull {
    public static final String ID = "magnetic_item_pull";
    public static final TagKey<Item> MAGNETIC_ITEMS = TagKey.create(Registries.ITEM, id("magnetic_items"));
    public static final TagKey<Item> IMMOVABLE_ITEMS = TagKey.create(Registries.ITEM, id("magnetic_item_pull_immovable"));
    private static final TagKey<EntityType<?>> PROTECTED = TagKey.create(Registries.ENTITY_TYPE, id("magnetic_pull_protected"));
    private static final TagKey<EntityType<?>> IMMOVABLE = TagKey.create(Registries.ENTITY_TYPE, id("magnetic_pull_immovable"));
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    public static final int SELECTION_TICKS = 200;
    private static long nextToken;
    private static final Map<UUID, Long> FEEDBACK = new HashMap<>();
    public static Consumer<Visual> clientReceiver = ignored -> {};
    public static Consumer<ArrowSelectionPreview> arrowPreviewReceiver = ignored -> {};
    private record Watermark(long epoch, long input) {}
    private record Selection(LegacySingleKeyServerIdentity identity, HighPowerInput.Input input, int preset, long revision, long started, boolean arrows, MagneticArrowSeparation.Pose pose) {}
    private record Target(UUID uuid, int entityId, ItemEntity entity, ServerLevel world) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset;
        final long revision, started;
        final List<Target> targets;
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        long lastTick, sequence;
        Context(ServerPlayer p, HighPowerInput.Input input, List<Target> targets) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; this.targets = new ArrayList<>(targets);
            preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            started = lastTick = now(p);
        }
    }
    private MagneticItemPull() {}
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("academy", path); }

    /** A server-timed, one-use selection lease. The client supplies no timestamp or authority. */
    public static boolean begin(ServerPlayer p, String wire) { return begin(p, wire, false); }
    public static boolean beginArrows(ServerPlayer p, String wire) { return begin(p, wire, true); }
    private static boolean begin(ServerPlayer p, String wire, boolean arrows) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        long selectionTick = now(p);
        if (selectionTick < 0 || selectionTick > Long.MAX_VALUE - SELECTION_TICKS) return false;
        var previous = INPUTS.get(p.getUUID());
        if (previous != null && previous.epoch == input.epoch() && input.input() <= previous.input) return false;
        INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        retireSelection(p);
        if (active(p) || !canAct(p) || !ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))
                || !AbilityStorage.get(p).canUse(ID) || nextToken == Long.MAX_VALUE) { feedback(p, "cannot_start"); return false; }
        MagneticArrowSeparation.Pose pose = arrows ? MagneticArrowSeparation.capturePose(p) : null;
        if (arrows && pose == null) { feedback(p, "cannot_start"); return false; }
        var identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
        var selection = new Selection(identity, input, identity.state().presets.current(), identity.state().presets.revision(), now(p), arrows, pose);
        SELECTIONS.put(p.getUUID(), selection);
        sendPreview(selection, true);
        return true;
    }
    private static boolean selectionValid(ServerPlayer p, Selection selection) {
        return selection != null && selection.identity.owns(p) && HighPowerBracerSession.authenticated(p, selection.input)
                && now(p) >= selection.started && now(p) - selection.started < SELECTION_TICKS
                && selection.preset == selection.identity.state().presets.current() && selection.revision == selection.identity.state().presets.revision()
                && ID.equals(selection.identity.state().presets.currentSkill(selection.input.slot()))
                && (!selection.arrows || MagneticArrowSeparation.poseValid(p, selection.pose));
    }
    public static boolean tap(ServerPlayer p, String wire, List<UUID> candidates) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Selection selection = SELECTIONS.get(p.getUUID());
        if (selection == null || !selection.input.equals(input)) return false;
        SELECTIONS.remove(p.getUUID(), selection);
        if (selection.arrows) { sendPreview(selection, false); return false; }
        if (!selectionValid(p, selection)) return false;
        List<UUID> unique = MagneticItemPullRules.candidates(candidates);
        if (unique == null || unique.isEmpty()) { feedback(p, "empty_selection"); return false; }
        if (active(p) || !canAct(p)
                || !ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))
                || !AbilityStorage.get(p).canUse(ID) || nextToken == Long.MAX_VALUE) return false;
        Vec3 hand = hand(p);
        List<Target> targets = new ArrayList<>(unique.size());
        boolean magnetic = false, owned = false, supported = false;
        for (UUID candidate : unique) {
            // ServerLevel#getEntity(UUID) consults only the live entity index; it cannot load chunks.
            Entity entity = p.serverLevel().getEntity(candidate);
            if (!(entity instanceof ItemEntity item)) continue;
            if (item instanceof NativeItemMergeAccess seam && seam.academy$nativeItemMergeContractVersion() == 1) supported = true;
            if (item.getItem().is(MAGNETIC_ITEMS)) {
                magnetic = true; if (MagneticItemProvenance.ownedBy(item, p.getUUID())) owned = true;
            }
            Target target = new Target(candidate, item.getId(), item, p.serverLevel());
            if (eligible(p, target, hand)) targets.add(target);
        }
        if (targets.isEmpty()) {
            String reason = !supported ? "unavailable" : !magnetic ? "nonmagnetic" : !owned ? "no_owned_drops" : "blocked";
            feedback(p, reason);
            return false;
        }
        var q = HighPowerGrowth.values(p).position();
        if (!ElectromasterPrototype.trySpend(p, ID, MagneticItemPullRules.cpCost(q), MagneticItemPullRules.cooldownTicks(q))) return false;
        Context context = new Context(p, input, targets); CONTEXTS.put(p.getUUID(), context); send(context, false); return true;
    }
    /** Release consumes the same server-captured corridor; no client target, origin or angle is accepted. */
    public static boolean releaseArrows(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Selection selection = SELECTIONS.get(p.getUUID());
        if (selection == null || !selection.input.equals(input)) return false;
        SELECTIONS.remove(p.getUUID(), selection);
        if (!selection.arrows) return false;
        if (!selectionValid(p, selection) || now(p) <= selection.started || active(p) || !canAct(p)
                || !AbilityStorage.get(p).canUse(ID)) { sendPreview(selection, false); return false; }
        return MagneticArrowSeparation.startAuthorized(p, input, selection.pose);
    }
    private static void sendPreview(Selection selection, boolean valid) {
        if (selection == null || !selection.arrows || selection.pose == null) return;
        ServerPlayer p = selection.identity.owner();
        if (p.connection == null || p.hasDisconnected() || p.serverLevel() != selection.identity.level()) return;
        var pose = selection.pose;
        PacketDistributor.sendToPlayer(p, new ArrowSelectionPreview(p.getId(), p.getUUID(), selection.input.input(), selection.input.epoch(),
                valid, pose.origin(), pose.axis(), pose.range(), selection.started + SELECTION_TICKS));
    }
    private static void retireSelection(ServerPlayer p) { Selection old = SELECTIONS.remove(p.getUUID()); if (old != null) sendPreview(old, false); }
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        var old = INPUTS.get(p.getUUID());
        if (old == null || old.epoch != input.epoch() || old.input < input.input())
            INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        Selection selection = SELECTIONS.get(p.getUUID());
        boolean retired = selection != null && selection.input.epoch() == input.epoch() && selection.input.slot() == input.slot()
                && selection.input.input() <= input.input() && SELECTIONS.remove(p.getUUID(), selection);
        if (retired) sendPreview(selection, false);
        retired |= MagneticArrowSeparation.abort(p, wire);
        Context c = owned(p);
        if (c == null || c.input.epoch() != input.epoch() || c.input.slot() != input.slot() || c.input.input() > input.input()) return retired;
        finish(c); return true;
    }
    public static void abort(ServerPlayer p) { if (!thread(p)) return; retireSelection(p); MagneticArrowSeparation.abort(p); Context c = owned(p); if (c != null) finish(c); }
    public static void remove(ServerPlayer p) { if (!thread(p)) return; abort(p); MagneticArrowSeparation.remove(p); INPUTS.remove(p.getUUID()); FEEDBACK.remove(p.getUUID()); }
    public static boolean itemsActive(ServerPlayer p) { return owned(p) != null; }
    public static boolean active(ServerPlayer p) { return itemsActive(p) || MagneticArrowSeparation.active(p); }
    public static int targetCount(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.targets.size(); }
    public static void clear() { CONTEXTS.clear(); INPUTS.clear(); SELECTIONS.clear(); FEEDBACK.clear(); MagneticArrowSeparation.clear(); }

    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null) return;
        long tick = now(p);
        if (!valid(c) || MagneticItemPullRules.expired(c.started, tick)) { finish(c); return; }
        if (tick <= c.lastTick) return;
        c.lastTick = tick; Vec3 hand = hand(p);
        for (Iterator<Target> it = c.targets.iterator(); it.hasNext();) {
            Target target = it.next(); ItemEntity item = target.entity;
            // Recheck the exact original object and provenance immediately before each impulse.
            // A merged-away target never follows the donor's owner/UUID to a different survivor.
            if (!eligible(p, target, hand)) { it.remove(); continue; }
            Vec3 toward = hand.subtract(item.getBoundingBox().getCenter());
            var result = MagneticItemPullRules.pull(vector(item.getDeltaMovement()), vector(toward));
            if (result == null) { it.remove(); continue; }
            Vec3 next = new Vec3(result.x(), result.y(), result.z());
            if (!movementPermitted(p, item, next)) { it.remove(); continue; }
            item.setDeltaMovement(next); item.hasImpulse = true; item.hurtMarked = true;
        }
        if (c.targets.isEmpty()) finish(c); else if ((tick - c.started) % 2 == 0) send(c, false);
    }
    private static boolean canAct(ServerPlayer p) {
        if (!thread(p)) return false;
        var state = AbilityStorage.get(p);
        return ElectromasterPrototype.ready(p) && state.activated && state.overloadFine && !state.interfering && state.learned(ID)
                && state.category.equals("electromaster") && state.level >= 3 && state.level <= 5
                && ElectromasterPrototype.effectiveLevel(p) >= 3 && SkillCatalog.enabled("electromaster", ID)
                && HighPowerCombat.unsupportedReason(p).isEmpty() && !AbilityConsumption.busy(p)
                && !p.noPhysics && !p.isPassenger() && !p.isSleeping()
                && finite(p.position()) && finite(p.getDeltaMovement()) && finite(p.getLookAngle())
                && Float.isFinite(p.getXRot()) && Float.isFinite(p.getYRot())
                && loadedArea(p.serverLevel(), p.getBoundingBox()) && permitted(p, p.blockPosition());
    }
    private static boolean valid(Context c) {
        ServerPlayer p = c.identity.owner();
        return c.identity.owns(p) && HighPowerBracerSession.authenticated(p, c.input) && canAct(p)
                && c.preset == c.identity.state().presets.current() && c.revision == c.identity.state().presets.revision()
                && ID.equals(c.identity.state().presets.currentSkill(c.input.slot()));
    }
    private static boolean eligible(ServerPlayer p, Target target, Vec3 hand) {
        ItemEntity item = target.entity; ServerLevel world = p.serverLevel();
        if (item.getClass() != ItemEntity.class || item.getType() != EntityType.ITEM
                || !(item instanceof NativeItemMergeAccess seam) || seam.academy$nativeItemMergeContractVersion() != 1
                || target.world != world || item.level() != world || item.isRemoved() || !item.isAlive()
                || item.getId() != target.entityId || !item.getUUID().equals(target.uuid)
                || world.getEntity(target.uuid) != item || world.getEntity(target.entityId) != item
                || item.noPhysics || item.isPassenger() || item.isVehicle() || item.isInvulnerable()
                || item.getType().is(PROTECTED) || item.getType().is(IMMOVABLE)
                || item.getPersistentData().getBoolean("academy:protected") || item.getPersistentData().getBoolean("academy:immovable")
                || item.getItem().isEmpty() || !item.getItem().is(MAGNETIC_ITEMS) || item.getItem().is(IMMOVABLE_ITEMS)
                || container(item) || !MagneticItemProvenance.ownedBy(item, p.getUUID())
                || item.getTarget() != null && !p.getUUID().equals(item.getTarget())
                || !finite(item.position()) || !finite(item.getDeltaMovement()) || !finite(hand)
                || item.getBoundingBox().getCenter().distanceToSqr(p.getEyePosition()) > MagneticItemPullRules.RANGE * MagneticItemPullRules.RANGE
                || !loadedArea(world, item.getBoundingBox()) || !permitted(p, item.blockPosition())) return false;
        Vec3 center = item.getBoundingBox().getCenter();
        return center.distanceToSqr(hand) <= MagneticItemPullRules.RANGE * MagneticItemPullRules.RANGE
                && clearLine(p, p.getEyePosition(), center) && clearLine(p, center, hand);
    }
    private static boolean container(ItemEntity item) {
        var stack = item.getItem();
        return stack.getItem() instanceof BundleItem || stack.getItem() instanceof BlockItem block && block.getBlock() instanceof EntityBlock
                || stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS)
                || stack.has(DataComponents.CONTAINER_LOOT) || stack.has(DataComponents.BLOCK_ENTITY_DATA);
    }
    /** Body-relative hand/pickup area, not a client-specified point or a remote orbit location. */
    private static Vec3 hand(ServerPlayer p) { return p.getEyePosition().add(p.getLookAngle().scale(.25)).add(0, -.6, 0); }
    private static boolean movementPermitted(ServerPlayer p, ItemEntity item, Vec3 velocity) {
        ServerLevel world = p.serverLevel(); AABB body = item.getBoundingBox();
        double gravity = item.getGravity();
        if (!finite(velocity) || velocity.lengthSqr() > MagneticItemPullRules.MAX_SPEED * MagneticItemPullRules.MAX_SPEED + 1e-10
                || !Double.isFinite(gravity) || gravity < 0 || gravity > .04 || item.maxUpStep() != 0) return false;
        // Native stationary items apply gravity on skipped movement ticks. Their stored downward
        // motion can therefore point into a supporting floor even while they are settled safely.
        // Query the native collision solver; never write its clipped result back as a velocity.
        Vec3 nextNativeStep = velocity.add(0, -gravity, 0);
        AABB queried = body.expandTowards(velocity).minmax(body.expandTowards(nextNativeStep));
        if (!loadedArea(world, queried) || !world.getWorldBorder().isWithinBounds(queried)) return false;
        var entities = world.getEntityCollisions(item, queried);
        return sweepPermitted(p, item, velocity, Entity.collideBoundingBox(item, velocity, body, world, entities))
                && sweepPermitted(p, item, nextNativeStep, Entity.collideBoundingBox(item, nextNativeStep, body, world, entities));
    }
    /** Only native downward floor contact may clip a requested step; walls and ceilings retire. */
    private static boolean sweepPermitted(ServerPlayer p, ItemEntity item, Vec3 requested, Vec3 clipped) {
        if (!finite(clipped) || clipped.x != requested.x || clipped.z != requested.z
                || clipped.y != requested.y && !(requested.y < 0 && clipped.y >= requested.y && clipped.y <= 0)) return false;
        AABB swept = item.getBoundingBox().expandTowards(clipped).deflate(1e-7);
        ServerLevel world = p.serverLevel();
        if (!loadedArea(world, swept) || !world.getWorldBorder().isWithinBounds(swept) || !world.noCollision(item, swept)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(swept.minX, swept.minY, swept.minZ),
                BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))) if (!permitted(p, pos)) return false;
        return true;
    }
    private static boolean clearLine(ServerPlayer p, Vec3 start, Vec3 end) {
        ServerLevel world = p.serverLevel(); AABB line = new AABB(start, end);
        if (!loadedArea(world, line) || !world.getWorldBorder().isWithinBounds(line)) return false;
        return world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }
    private static boolean permitted(ServerPlayer p, BlockPos pos) {
        ServerLevel world = p.serverLevel();
        return !world.isOutsideBuildHeight(pos) && world.hasChunkAt(pos) && world.getWorldBorder().isWithinBounds(pos) && world.mayInteract(p, pos);
    }
    private static boolean loadedArea(ServerLevel world, AABB box) {
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.maxX) || !Double.isFinite(box.minY) || !Double.isFinite(box.maxY)
                || !Double.isFinite(box.minZ) || !Double.isFinite(box.maxZ) || box.getXsize() > 26 || box.getYsize() > 26 || box.getZsize() > 26
                || box.minY < world.getMinBuildHeight() || box.maxY >= world.getMaxBuildHeight()) return false;
        for (int x = ((int)Math.floor(box.minX)) >> 4; x <= ((int)Math.floor(box.maxX)) >> 4; x++)
            for (int z = ((int)Math.floor(box.minZ)) >> 4; z <= ((int)Math.floor(box.maxZ)) >> 4; z++) if (!world.hasChunk(x, z)) return false;
        return true;
    }
    private static Context owned(ServerPlayer p) { if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    private static boolean thread(ServerPlayer p) { return p != null && p.serverLevel().getServer().isSameThread(); }
    private static long now(ServerPlayer p) { return p.serverLevel().getServer().overworld().getGameTime(); }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static MagneticItemPullRules.Vector vector(Vec3 v) { return new MagneticItemPullRules.Vector(v.x, v.y, v.z); }
    private static void feedback(ServerPlayer p, String reason) {
        long tick = now(p); Long prior = FEEDBACK.get(p.getUUID());
        if (prior != null && tick >= prior && tick - prior < 20) return;
        FEEDBACK.put(p.getUUID(), tick);
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("academy.highpower.item_pull." + reason), false);
    }
    private static void finish(Context c) { CONTEXTS.remove(c.identity.owner().getUUID(), c); send(c, true); c.targets.clear(); c.audience.clear(); }
    private static void send(Context c, boolean ended) {
        ServerPlayer p = c.identity.owner();
        for (ServerPlayer viewer : c.identity.level().players()) if (viewer.position().distanceToSqr(p.position()) <= 32 * 32) c.audience.add(viewer);
        c.audience.add(p);
        var packet = new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), ++c.sequence,
                ended ? 1 : 0, hand(p), ended ? List.of() : c.targets.stream().map(Target::uuid).toList());
        for (ServerPlayer viewer : c.audience) if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == c.identity.level()) PacketDistributor.sendToPlayer(viewer, packet);
    }
    private static List<UUID> readTargets(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MagneticItemPullRules.MAX_TARGETS) throw new DecoderException("NEW133 target budget exceeded");
        List<UUID> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) targets.add(buffer.readUUID());
        return targets;
    }
    private static void writeTargets(RegistryFriendlyByteBuf buffer, List<UUID> targets) { buffer.writeVarInt(targets.size()); targets.forEach(buffer::writeUUID); }
    public record CandidateRequest(String wire, List<UUID> candidates) implements CustomPacketPayload {
        public CandidateRequest {
            List<UUID> checked = MagneticItemPullRules.candidates(candidates);
            if (wire == null || wire.length() > 64 || checked == null) throw new IllegalArgumentException("Invalid bounded NEW133 request");
            candidates = checked;
        }
        public static final Type<CandidateRequest> TYPE = new Type<>(id("hp_magnetic_item_pull_candidates"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CandidateRequest> CODEC = StreamCodec.of(
                (b, v) -> { b.writeUtf(v.wire, 64); writeTargets(b, v.candidates); }, b -> new CandidateRequest(b.readUtf(64), readTargets(b)));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase, Vec3 hand, List<UUID> targets) implements CustomPacketPayload {
        public Visual { targets = List.copyOf(targets); if (targets.size() > MagneticItemPullRules.MAX_TARGETS) throw new IllegalArgumentException("Visual target budget exceeded"); }
        public static final Type<Visual> TYPE = new Type<>(id("hp_magnetic_item_pull"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.epoch); b.writeLong(v.sequence); b.writeByte(v.phase);
            b.writeDouble(v.hand.x); b.writeDouble(v.hand.y); b.writeDouble(v.hand.z); writeTargets(b, v.targets);
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readUnsignedByte(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), readTargets(b)));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record ArrowSelectionPreview(int entity, UUID owner, long input, long epoch, boolean valid, Vec3 origin, Vec3 axis, double range, long expiresAt) implements CustomPacketPayload {
        public static final Type<ArrowSelectionPreview> TYPE = new Type<>(id("hp_item_separation_preview"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ArrowSelectionPreview> CODEC = StreamCodec.of((b,v)->{
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.epoch); b.writeBoolean(v.valid);
            b.writeDouble(v.origin.x); b.writeDouble(v.origin.y); b.writeDouble(v.origin.z);
            b.writeDouble(v.axis.x); b.writeDouble(v.axis.y); b.writeDouble(v.axis.z); b.writeDouble(v.range); b.writeLong(v.expiresAt);
        },b->new ArrowSelectionPreview(b.readInt(),b.readUUID(),b.readLong(),b.readLong(),b.readBoolean(),
                new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readDouble(),b.readLong()));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) {
            e.registrar("1").playToServer(CandidateRequest.TYPE, CandidateRequest.CODEC, (packet, context) -> {
                if (context.player() instanceof ServerPlayer p) context.enqueueWork(() -> tap(p, packet.wire, packet.candidates));
            });
            e.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
            e.registrar("1").playToClient(ArrowSelectionPreview.TYPE, ArrowSelectionPreview.CODEC, (packet, context) -> arrowPreviewReceiver.accept(packet));
        }
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e) {
        for (Selection selection : new ArrayList<>(SELECTIONS.values()))
            if (selection.identity.level().getServer() == e.getServer() && !selectionValid(selection.identity.owner(), selection)
                    && SELECTIONS.remove(selection.identity.owner().getUUID(), selection)) sendPreview(selection, false);
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.level().getServer() == e.getServer()) tick(c.identity.owner()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if (e.getOriginal() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { clear(); }
}

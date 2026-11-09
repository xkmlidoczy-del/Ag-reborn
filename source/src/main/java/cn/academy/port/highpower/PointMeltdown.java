package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.PointMeltdownRules;
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
import net.minecraft.server.MinecraftServer;
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

/** NEW233: one frozen visible surface, ten genuine focus ticks, one native harvest. */
@EventBusSubscriber(modid = "academy")
public final class PointMeltdown {
    public static final String ID = "point_meltdown", COOLDOWN_KEY = "academy:point_meltdown_cooldown";
    public static final int MIN_LEVEL = 3, MAX_LEVEL = 5;
    public static final int PREVIEW = 0, CONFIRMED = 1, ENDED = 2, FOCUS = 3;
    public static final int READY = 0, UNAVAILABLE = 1, CANCELED = 2;
    private static final int MAX_INPUTS = 4096;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<MinecraftServer, Integer> POSTS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Context> PROCESSING = new IdentityHashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(MinecraftServer server, long epoch, long input) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset, selected, cooldown;
        final long revision, started;
        final double cost;
        final List<ItemStack> inventory;
        final BeamEtchingMining.Capacity capacity = BeamEtchingMining.newCapacity();
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        final Set<ServerPlayer> cooldownOwners = Collections.newSetFromMap(new IdentityHashMap<>());
        BeamEtchingMining.Target preview, frozen;
        PointMeltdownRules.Focus focus;
        MiningPermit digging;
        long lastHoldTick = -1, lastVisual = -1, sequence;
        boolean inspecting, processing, attempted, confirmed, ended;
        Context(ServerPlayer player, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(player, input.input(), ++nextToken, AbilityStorage.get(player));
            this.input = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            selected = player.getInventory().selected; started = now(player); inventory = inventory(player);
            var position = HighPowerGrowth.values(player).position();
            cost = PointMeltdownRules.cost(position); cooldown = PointMeltdownRules.cooldown(position);
            cooldownOwners.add(player);
        }
    }
    /** Only this controller can mint the fixed 36 m, single-cell capability. */
    public static final class MiningPermit {
        private final Context context;
        private final BeamEtchingMining.Target target;
        private final Vec3 origin, position, look;
        private final long tick;
        private boolean consumed;
        private MiningPermit(Context context, BeamEtchingMining.Target target) {
            this.context = context; this.target = target;
            ServerPlayer player = player(); origin = player.getEyePosition(); position = player.position();
            look = player.getLookAngle(); tick = now(player);
        }
        ServerPlayer player() { return context.identity.owner(); }
        BeamEtchingMining.Target target() { return target; }
        Vec3 origin() { return origin; }
        double cost() { return context.cost; }
        boolean consume() {
            if (consumed || !context.processing || context.inspecting || !context.attempted
                    || context.focus == null || !context.focus.complete() || !valid()) return false;
            consumed = true; return true;
        }
        boolean valid() {
            ServerPlayer player = player();
            // Inventory equality is owned by the native adapter in this synchronous scope.
            // In particular, a final-use pickaxe may become empty before native removal.
            return context.digging == this && (context.inspecting || context.processing)
                    && PointMeltdown.valid(context) && target == (context.frozen == null ? context.preview : context.frozen)
                    && now(player) == tick && player.position().equals(position) && player.getEyePosition().equals(origin)
                    && player.getLookAngle().equals(look) && visible(player, target);
        }
    }
    private PointMeltdown() {}

    public static boolean start(ServerPlayer player, String wire) {
        if (!thread(player)) return false;
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(player, input)) return false;
        Watermark prior = INPUTS.get(player.getUUID());
        if (prior != null && prior.server == player.server && prior.epoch == input.epoch() && input.input() <= prior.input) return false;
        if (prior == null && INPUTS.size() >= MAX_INPUTS) return false;
        // A rejected but authenticated physical press is terminal too; it cannot be
        // replayed later after this synchronous attempt or an existing focus has ended.
        INPUTS.put(player.getUUID(), new Watermark(player.server, input.epoch(), input.input()));
        if (PROCESSING.containsKey(player)) return false;
        Context existing = owned(player);
        if (existing != null) { if (valid(existing)) return false; finish(existing, CANCELED); }
        Context foreign = CONTEXTS.get(player.getUUID()); if (foreign != null) finish(foreign, CANCELED);
        if (!canAct(player) || nextToken == Long.MAX_VALUE || cooldownRemaining(player) != 0
                || !AbilityStorage.get(player).canUse(ID)
                || !ID.equals(AbilityStorage.get(player).presets.currentSkill(input.slot()))) return false;
        Context context = new Context(player, input); CONTEXTS.put(player.getUUID(), context);
        tick(player); return owned(player) == context;
    }
    /** Freeze the observed held target; release never acquires a different block. */
    public static boolean release(ServerPlayer player, String wire) {
        Context context = owned(player); var input = HighPowerInput.parse(wire);
        if (context == null || context.processing || context.focus != null
                || !HighPowerBracerSession.authenticated(player, input) || !context.input.equals(input)) return false;
        if (!valid(context) || !inventoryUnchanged(context) || PointMeltdownRules.expired(context.started, now(player))) {
            finish(context, CANCELED); return false;
        }
        if (context.preview == null || !visible(player, context.preview) || !inspect(context, context.preview)) {
            finish(context, UNAVAILABLE); return true;
        }
        context.frozen = context.preview; context.focus = PointMeltdownRules.Focus.initial(now(player));
        send(context, FOCUS, List.of(mark(context.frozen)), READY, PointMeltdownRules.FOCUS_TICKS, player.getEyePosition());
        return true;
    }
    public static boolean abort(ServerPlayer player, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(player, input)) return false;
        Context context = owned(player);
        boolean owns = context != null && context.input.epoch() == input.epoch() && context.input.slot() == input.slot()
                && input.input() >= context.input.input();
        if (!owns && !ID.equals(AbilityStorage.get(player).presets.currentSkill(input.slot()))) return false;
        Watermark prior = INPUTS.get(player.getUUID());
        boolean retired = prior == null || prior.server != player.server || prior.epoch != input.epoch() || input.input() > prior.input;
        if (retired) {
            if (prior == null && INPUTS.size() >= MAX_INPUTS) return false;
            INPUTS.put(player.getUUID(), new Watermark(player.server, input.epoch(), input.input()));
        }
        if (owns) finish(context, CANCELED);
        return owns || retired;
    }
    public static void abort(ServerPlayer player) { Context context = owned(player); if (context != null) finish(context, CANCELED); }
    public static void remove(ServerPlayer player) {
        if (!thread(player)) return;
        abort(player);
        // The next authenticated epoch replaces this fence. A delayed removal of an old
        // same-UUID owner must never erase a newer live owner's physical-input watermark.
    }
    public static boolean active(ServerPlayer player) {
        Context context = owned(player);
        if (context != null && !valid(context)) { finish(context, CANCELED); return false; }
        return context != null;
    }
    public static boolean holding(ServerPlayer player) { Context context = owned(player); return context != null && context.focus == null && valid(context); }
    public static List<BeamEtching.Mark> marks(ServerPlayer player) {
        Context context = owned(player); if (context == null) return List.of();
        var target = context.frozen == null ? context.preview : context.frozen;
        return target == null ? List.of() : List.of(mark(target));
    }
    public static int focusTicks(ServerPlayer player) {
        Context context = owned(player); return context == null || context.focus == null ? 0 : context.focus.ticks();
    }
    /** Public refreshes never manufacture focus ticks or advance persistent cooldown. */
    public static void tick(ServerPlayer player) { update(player, false); }
    private static void update(ServerPlayer player, boolean nativePost) {
        Context context = owned(player); if (context == null || context.processing) return;
        if (!valid(context) || !inventoryUnchanged(context)) { finish(context, CANCELED); return; }
        long tick = now(player);
        if (context.focus != null) {
            if (!visible(player, context.frozen) || !inspect(context, context.frozen)) { finish(context, UNAVAILABLE); return; }
            if (!nativePost) return;
            if (!context.focus.current(tick)) { finish(context, CANCELED); return; }
            var next = context.focus.advance(tick); if (next == context.focus) return;
            context.focus = next;
            if (next.complete()) { work(context); return; }
            send(context, FOCUS, List.of(mark(context.frozen)), READY, PointMeltdownRules.FOCUS_TICKS - next.ticks(), player.getEyePosition());
            return;
        }
        if (PointMeltdownRules.expired(context.started, tick) || tick < context.lastHoldTick) { finish(context, CANCELED); return; }
        if (tick == context.lastHoldTick) return;
        context.lastHoldTick = tick; context.preview = aim(player);
        boolean available = context.preview != null && inspect(context, context.preview);
        if (!available) context.preview = null;
        if (PointMeltdownRules.ready(context.lastVisual, tick, PointMeltdownRules.PREVIEW_INTERVAL)) {
            context.lastVisual = tick;
            send(context, PREVIEW, available ? List.of(mark(context.preview)) : List.of(), available ? READY : UNAVAILABLE,
                    PointMeltdownRules.PREVIEW_TICKS - (int)(tick - context.started), player.getEyePosition());
        }
    }
    private static boolean inspect(Context context, BeamEtchingMining.Target target) {
        if (context.digging != null || context.processing || context.inspecting) return false;
        MiningPermit permit = new MiningPermit(context, target); context.digging = permit; context.inspecting = true;
        try { return BeamEtchingMining.inspectPoint(permit, context.capacity).eligible(); }
        finally { context.inspecting = false; context.digging = null; }
    }
    private static void work(Context context) {
        if (context.processing || context.attempted || context.focus == null || !context.focus.complete()) return;
        ServerPlayer player = context.identity.owner();
        // An exhausted shared quota ends this focus; it never waits past its ten-tick terminal.
        if (!valid(context) || !inventoryUnchanged(context) || !visible(player, context.frozen)
                || !BeamEtching.sharedAttemptReady(player) || !BeamEtching.claimSharedAttempt(player)) {
            finish(context, UNAVAILABLE); return;
        }
        context.attempted = true; context.processing = true; PROCESSING.put(player, context);
        MiningPermit permit = new MiningPermit(context, context.frozen); context.digging = permit;
        try {
            var result = BeamEtchingMining.attemptPoint(permit, context.capacity);
            if (!result.removed()) { finish(context, UNAVAILABLE); return; }
            context.confirmed = true;
            applyCooldown(context); // Actual removal is irreversible even if a later callback retires the owner.
            if (!context.ended && owned(player) == context)
                send(context, CONFIRMED, List.of(mark(context.frozen)), READY, 0, permit.origin());
            finish(context, READY);
        } finally {
            context.digging = null; context.processing = false;
            PROCESSING.entrySet().removeIf(entry -> entry.getValue() == context);
        }
    }
    private static boolean canAct(ServerPlayer player) {
        if (!thread(player) || player.connection == null || player.hasDisconnected() || !player.isAlive() || player.isRemoved()
                || player.isSpectator() || player.getAbilities().instabuild || player.getAbilities().invulnerable || player.noPhysics
                || player.isPassenger() || player.isSleeping() || player.containerMenu != player.inventoryMenu
                || AbilityConsumption.busy(player) || !HighPowerResources.ready(player)
                || HighPowerResources.effectiveLevel(player) < MIN_LEVEL || HighPowerCombat.unsupportedReason(player).isPresent()) return false;
        var state = AbilityStorage.get(player);
        return state.category.equals("meltdowner") && state.activated && state.overloadFine && !state.interfering
                && state.learned(ID) && state.learned(BeamEtching.ID) && state.level >= MIN_LEVEL && state.level <= MAX_LEVEL
                && SkillCatalog.enabled("meltdowner", ID) && PointMeltdownRules.clock(now(player))
                && finite(player.position()) && finite(player.getEyePosition()) && finite(player.getLookAngle()) && finite(player.getDeltaMovement())
                && player.getLookAngle().lengthSqr() > .5 && Float.isFinite(player.getXRot()) && Float.isFinite(player.getYRot());
    }
    private static boolean valid(Context context) {
        ServerPlayer player = context.identity.owner();
        return !context.ended && owned(player) == context && context.identity.owns(player)
                && HighPowerBracerSession.authenticated(player, context.input) && canAct(player)
                && now(player) >= context.started && cooldownRemaining(player) == 0
                && context.identity.state().cooldowns.getOrDefault(ID, 0) == 0
                && context.preset == context.identity.state().presets.current() && context.revision == context.identity.state().presets.revision()
                && ID.equals(context.identity.state().presets.currentSkill(context.input.slot()));
    }
    private static List<ItemStack> inventory(ServerPlayer player) {
        List<ItemStack> items = new ArrayList<>(player.getInventory().getContainerSize());
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) items.add(player.getInventory().getItem(i).copy());
        return items;
    }
    private static boolean inventoryUnchanged(Context context) {
        var inventory = context.identity.owner().getInventory();
        if (inventory.selected != context.selected || inventory.getContainerSize() != context.inventory.size()) return false;
        for (int i = 0; i < context.inventory.size(); i++) if (!ItemStack.matches(context.inventory.get(i), inventory.getItem(i))) return false;
        return true;
    }
    private static BeamEtchingMining.Target aim(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(); var end = PointMeltdownRules.endpoint(point(eye), point(player.getLookAngle().normalize()));
        if (end == null) return null;
        BlockHitResult hit = firstVisible(player, eye, new Vec3(end.x(), end.y(), end.z()));
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || hit.isInside() || !within(eye, hit.getLocation())) return null;
        return new BeamEtchingMining.Target(hit.getBlockPos(), player.serverLevel().getBlockState(hit.getBlockPos()), hit.getLocation(), hit.getDirection());
    }
    /** Walking is allowed: preserve the frozen point, plus the currently aimed block and face. */
    private static boolean visible(ServerPlayer player, BeamEtchingMining.Target target) {
        if (target == null || !within(player.getEyePosition(), target.surface()) || !player.serverLevel().hasChunkAt(target.pos())
                || player.serverLevel().getBlockState(target.pos()) != target.state()) return false;
        var aimed = aim(player);
        if (aimed == null || !aimed.pos().equals(target.pos()) || aimed.state() != target.state() || aimed.face() != target.face()) return false;
        // Nudge into the block along this ray, not along the face normal: a normal offset leaves the line of sight and,
        // at the shallow angles of ordinary ground aiming, lands the re-cast hit well outside the surface tolerance.
        Vec3 eye = player.getEyePosition(), inward = target.surface().add(target.surface().subtract(eye).normalize().scale(.0001));
        BlockHitResult hit = firstVisible(player, eye, within(eye, inward) ? inward : target.surface());
        return hit != null && hit.getType() == HitResult.Type.BLOCK && !hit.isInside() && hit.getBlockPos().equals(target.pos())
                && hit.getDirection() == target.face() && hit.getLocation().distanceToSqr(target.surface()) < 1e-8;
    }
    private static BlockHitResult firstVisible(ServerPlayer player, Vec3 start, Vec3 end) {
        if (!within(start, end)) return null;
        int pieces = Math.max(1, (int)Math.ceil(start.distanceTo(end) / .25)); Vec3 previous = start;
        for (int i = 1; i <= pieces; i++) {
            Vec3 next = start.lerp(end, (double)i / pieces);
            if (!permittedSlice(player, new AABB(previous, next).inflate(.00001))) return null;
            // Keep the original eye origin: a face exactly at a previous slice end is
            // excluded by native clip, and starting the next clip on it reports "inside".
            // Every newly extended quarter-metre prefix is checked before this read;
            // the fixed 36 m segment bounds this to at most 144 prefix clips.
            BlockHitResult hit = player.serverLevel().clip(new ClipContext(start, next, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
            if (hit.getType() == HitResult.Type.BLOCK) return hit;
            previous = next;
        }
        // Clip excludes a terminal face. Use one ULP of segment length: an endpoint
        // ULP at zero is subnormal and disappears when native clip subtracts a -36 m
        // origin. At translated coordinates, retain the coordinate-ULP fallback only
        // if reconstruction rounds back to the identical endpoint. Neither probe can
        // admit a hit outside the original closed segment or its strict 36 m budget.
        Vec3 delta = end.subtract(start); double length = delta.length();
        if (!(length > 0)) return null;
        Vec3 beyond = start.add(delta.scale(1 / length).scale(Math.nextUp(length)));
        if (beyond.equals(end)) beyond = new Vec3(Math.nextAfter(end.x, end.x + delta.x),
                Math.nextAfter(end.y, end.y + delta.y), Math.nextAfter(end.z, end.z + delta.z));
        if (!beyond.equals(end) && permittedSlice(player, new AABB(end, beyond).inflate(.00001))) {
            BlockHitResult hit = player.serverLevel().clip(new ClipContext(start, beyond, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
            if (hit.getType() == HitResult.Type.BLOCK && within(start, hit.getLocation())
                    && start.distanceToSqr(hit.getLocation()) <= start.distanceToSqr(end)) return hit;
        }
        return null;
    }
    private static boolean permittedSlice(ServerPlayer player, AABB box) {
        var level = player.serverLevel();
        if (box.minY < level.getMinBuildHeight() || box.maxY >= level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(box)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
            if (!level.hasChunkAt(pos) || !level.mayInteract(player, pos)) return false;
        return true;
    }

    /** Remaining real ticks survive clock reset, disconnect and clone; malformed data never refills. */
    public static int cooldownRemaining(ServerPlayer player) {
        if (!thread(player)) return -1;
        var data = player.getPersistentData(); if (!data.contains(COOLDOWN_KEY)) return 0;
        if (!data.contains(COOLDOWN_KEY, Tag.TAG_COMPOUND)) return -1;
        var tag = data.getCompound(COOLDOWN_KEY);
        if (!tag.getAllKeys().equals(Set.of("version", "remaining")) || !tag.contains("version", Tag.TAG_INT)
                || tag.getInt("version") != 1 || !tag.contains("remaining", Tag.TAG_INT)) return -1;
        int remaining = tag.getInt("remaining"); return remaining >= 0 && remaining <= 10 ? remaining : -1;
    }
    private static void writeCooldown(ServerPlayer player, int remaining) {
        var tag = new CompoundTag(); tag.putInt("version", 1); tag.putInt("remaining", remaining);
        player.getPersistentData().put(COOLDOWN_KEY, tag);
    }
    private static void applyCooldown(Context context) {
        // A native loot/neighbor callback can clone the player after irreversible removal,
        // before destroyBlock returns. Carry this debt to that observed replacement too.
        // A foreign synchronous save/logout inside removal can serialize before this
        // return boundary and is outside the reviewed callback contract, as for NEW213.
        // Ordinary logout after the attempt returns persists the remaining-tick tag.
        for (ServerPlayer player : context.cooldownOwners) {
            int remaining = cooldownRemaining(player);
            if (remaining >= 0) writeCooldown(player, Math.max(remaining, context.cooldown));
        }
        context.identity.state().setCooldown(ID, context.cooldown);
        // Peek does not rebind the UUID-shared ability callbacks to a retired original.
        var current = AbilityStorage.peek(context.identity.owner());
        if (current != null && current != context.identity.state()) current.setCooldown(ID, context.cooldown);
    }
    private static void advanceCooldown(ServerPlayer player) {
        int remaining = cooldownRemaining(player);
        if (remaining > 0) {
            writeCooldown(player, remaining - 1);
            if (remaining > 1) AbilityStorage.get(player).setCooldown(ID, remaining - 1);
        }
    }
    private static void finish(Context context, int status) {
        if (context.ended) return;
        context.ended = true;
        CONTEXTS.remove(context.identity.owner().getUUID(), context);
        if (!context.audience.isEmpty()) send(context, ENDED, List.of(), status, 0, context.identity.owner().getEyePosition());
        context.audience.clear();
    }
    private static void send(Context context, int phase, List<BeamEtching.Mark> marks, int status, int remaining, Vec3 origin) {
        ServerPlayer player = context.identity.owner(); if (!finite(origin)) return;
        if (phase != ENDED) {
            context.audience.removeIf(viewer -> viewer.connection == null || viewer.hasDisconnected() || viewer.serverLevel() != context.identity.level()
                    || viewer.position().distanceToSqr(player.position()) > 64 * 64);
            for (ServerPlayer viewer : context.identity.level().players()) if (viewer.distanceToSqr(player) <= 48 * 48) context.audience.add(viewer);
            context.audience.add(player);
        }
        Visual packet = new Visual(player.getId(), player.getUUID(), context.identity.epoch(), context.input.input(), context.identity.token(),
                ++context.sequence, phase, origin, marks, context.focus == null ? 0 : context.focus.ticks(), status, remaining);
        for (ServerPlayer viewer : context.audience) if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == context.identity.level())
            PacketDistributor.sendToPlayer(viewer, packet);
    }
    private static BeamEtching.Mark mark(BeamEtchingMining.Target target) { return new BeamEtching.Mark(target.pos(), target.surface(), target.face()); }
    private static Context owned(ServerPlayer player) {
        if (!thread(player)) return null; Context context = CONTEXTS.get(player.getUUID());
        return context != null && context.identity.owner() == player ? context : null;
    }
    private static boolean thread(ServerPlayer player) { return player != null && player.server != null && player.server.isSameThread(); }
    private static long now(ServerPlayer player) { return Integer.toUnsignedLong(player.server.getTickCount()); }
    private static boolean finite(Vec3 value) { return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z); }
    private static PointMeltdownRules.Point point(Vec3 value) { return new PointMeltdownRules.Point(value.x, value.y, value.z); }
    private static boolean within(Vec3 origin, Vec3 endpoint) { return finite(origin) && finite(endpoint) && PointMeltdownRules.within(point(origin), point(endpoint)); }

    public record Visual(int entity, UUID owner, long epoch, long input, long token, long sequence, int phase, Vec3 origin,
                         List<BeamEtching.Mark> marks, int focusTicks, int status, int remainingTicks) implements CustomPacketPayload {
        public Visual {
            if (entity < 0 || owner == null || owner.equals(new UUID(0, 0)) || epoch <= 0 || input <= 0 || token <= 0 || sequence <= 0 || phase < PREVIEW || phase > FOCUS
                    || !finite(origin) || marks == null || marks.size() > 1 || focusTicks < 0 || focusTicks > PointMeltdownRules.FOCUS_TICKS
                    || status < READY || status > CANCELED || remainingTicks < 0 || remainingTicks > PointMeltdownRules.PREVIEW_TICKS
                    || phase == PREVIEW && (focusTicks != 0 || status == CANCELED || remainingTicks == 0)
                    || (phase == FOCUS || phase == CONFIRMED) && marks.size() != 1
                    || phase == CONFIRMED && (focusTicks != PointMeltdownRules.FOCUS_TICKS || status != READY || remainingTicks != 0)
                    || phase == FOCUS && (status != READY || remainingTicks != PointMeltdownRules.FOCUS_TICKS - focusTicks)
                    || phase == ENDED && (!marks.isEmpty() || remainingTicks != 0))
                throw new IllegalArgumentException("Invalid bounded NEW233 visual");
            marks = List.copyOf(marks);
            for (BeamEtching.Mark mark : marks) if (!within(origin, mark.point())) throw new IllegalArgumentException("NEW233 visual exceeds 36 m");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_point_meltdown"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeInt(value.entity); buffer.writeUUID(value.owner); buffer.writeLong(value.epoch); buffer.writeLong(value.input);
            buffer.writeLong(value.token); buffer.writeLong(value.sequence); buffer.writeByte(value.phase); writeVec(buffer, value.origin);
            buffer.writeByte(value.marks.size());
            for (BeamEtching.Mark mark : value.marks) { buffer.writeBlockPos(mark.pos()); writeVec(buffer, mark.point()); buffer.writeByte(mark.face().ordinal()); }
            buffer.writeByte(value.focusTicks); buffer.writeByte(value.status); buffer.writeByte(value.remainingTicks);
        }, buffer -> {
            int entity = buffer.readInt(); UUID owner = buffer.readUUID(); long epoch = buffer.readLong(), input = buffer.readLong(), token = buffer.readLong(), sequence = buffer.readLong();
            int phase = buffer.readUnsignedByte(); Vec3 origin = readVec(buffer); int count = buffer.readUnsignedByte();
            if (count > 1) throw new IllegalArgumentException("Oversized NEW233 target list");
            List<BeamEtching.Mark> marks = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                BlockPos pos = buffer.readBlockPos(); Vec3 point = readVec(buffer); int face = buffer.readUnsignedByte();
                if (face >= Direction.values().length) throw new IllegalArgumentException("Invalid NEW233 face");
                marks.add(new BeamEtching.Mark(pos, point, Direction.values()[face]));
            }
            return new Visual(entity, owner, epoch, input, token, sequence, phase, origin, marks,
                    buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readUnsignedByte());
        });
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void writeVec(RegistryFriendlyByteBuf buffer, Vec3 value) { buffer.writeDouble(value.x); buffer.writeDouble(value.y); buffer.writeDouble(value.z); }
    private static Vec3 readVec(RegistryFriendlyByteBuf buffer) { return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()); }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
        }
    }
    public static void clear() {
        for (Context context : new ArrayList<>(CONTEXTS.values())) finish(context, CANCELED);
        CONTEXTS.clear(); // Keep live input fences, persisted cooldown, native-post deduplication and token identity.
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer(); if (!server.isSameThread()) return;
        int nativeTick = server.getTickCount(); Integer previous = POSTS.put(server, nativeTick);
        if (previous != null && previous == nativeTick) return;
        if (previous != null && nativeTick - previous != 1)
            for (Context context : new ArrayList<>(CONTEXTS.values())) if (context.identity.owner().server == server) finish(context, CANCELED);
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers()))
            if (player.connection != null && !player.hasDisconnected() && !player.isRemoved()) advanceCooldown(player);
        for (Context context : new ArrayList<>(CONTEXTS.values())) if (context.identity.owner().server == server) update(context.identity.owner(), true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer player) remove(player); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer player) remove(player); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent event) { if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) remove(player); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void leave(EntityLeaveLevelEvent event) { if (event.getEntity() instanceof ServerPlayer player) remove(player); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original) {
            Context pending = PROCESSING.get(original);
            if (pending != null && event.getEntity() instanceof ServerPlayer replacement
                    && original.getUUID().equals(replacement.getUUID()) && original.server == replacement.server) {
                pending.cooldownOwners.add(replacement); PROCESSING.put(replacement, pending);
            }
            remove(original);
            if (event.getEntity() instanceof ServerPlayer replacement && original.getUUID().equals(replacement.getUUID())
                    && original.getPersistentData().contains(COOLDOWN_KEY))
                replacement.getPersistentData().put(COOLDOWN_KEY, original.getPersistentData().get(COOLDOWN_KEY).copy());
        }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (Context context : new ArrayList<>(CONTEXTS.values())) if (context.identity.owner().server == event.getServer()) finish(context, CANCELED);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        for (Context context : new ArrayList<>(CONTEXTS.values())) if (context.identity.owner().server == event.getServer()) finish(context, CANCELED);
        POSTS.remove(event.getServer());
        INPUTS.entrySet().removeIf(entry -> entry.getValue().server == event.getServer());
    }
}

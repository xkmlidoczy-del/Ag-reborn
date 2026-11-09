package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.CurrentChargingSession;
import cn.academy.port.skill.ClassicRaytrace;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Enrolled-only native charging. Existing classic session/energy semantics remain separate. */
@EventBusSubscriber(modid = "academy")
public final class HighPowerCharging {
    public static final String DATA_KEY = "academy:highpower_charging_quota_v1";
    private static final Set<UUID> BUSY = new HashSet<>();
    private HighPowerCharging() {}
    /** An invalid existing enrollment fails closed; it must not fall back to creative classic charging. */
    public static boolean applies(ServerPlayer player) { return player.getPersistentData().contains(HighPowerCombat.DATA_KEY); }
    public static boolean busy(ServerPlayer player) { return player != null && BUSY.contains(player.getUUID()); }
    private static boolean eligible(ServerPlayer player, AbilityProgress state) {
        return !player.getAbilities().instabuild && HighPowerCombat.enabled(player)
                && HighPowerCombat.unsupportedReason(player).isEmpty()
                && "electromaster".equals(state.category) && state.level >= 1 && state.level <= 5
                && state.learned(CurrentChargingSession.ID) && state.activated && state.overloadFine
                && !state.interfering && state.cooldowns.getOrDefault(CurrentChargingSession.ID, 0) == 0
                && player.isAlive() && !player.isRemoved() && !player.isSpectator();
    }
    public static Session begin(ServerPlayer player) {
        var state = AbilityStorage.get(player);
        if (busy(player) || !eligible(player, state)) return null;
        boolean itemMode = HighPowerChargingEnergy.knownItem(player.getMainHandItem());
        var target = target(player, itemMode);
        if (target == null || !target.valid() || target.full()) return null;
        return new Session(state, itemMode, state.exp(CurrentChargingSession.ID));
    }
    private static HighPowerChargingEnergy.Target target(ServerPlayer player, boolean itemMode) {
        if (itemMode) return HighPowerChargingEnergy.item(player.getMainHandItem());
        if (!loadedRay(player)) return null;
        var hit = ClassicRaytrace.living(player, CurrentChargingSession.RANGE, ClipContext.Fluid.NONE);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK
                || !player.getAbilities().mayBuild || !player.serverLevel().mayInteract(player, block.getBlockPos())) return null;
        var target = HighPowerChargingEnergy.block(player.serverLevel(), block.getBlockPos());
        return target != null && player.serverLevel().mayInteract(player, target.storagePosition()) ? target : null;
    }
    /** The classic ray performs block/entity lookups; preflight its entire inflated footprint. */
    private static boolean loadedRay(ServerPlayer player) {
        var start = player.getEyePosition(); var direction = ClassicRaytrace.direction(player);
        var end = start.add(direction.scale(CurrentChargingSession.RANGE));
        for (var point : new net.minecraft.world.phys.Vec3[]{start, end})
            if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                    || Math.abs(point.x) > 30_000_000 || Math.abs(point.z) > 30_000_000) return false;
        int minX = net.minecraft.util.Mth.floor(Math.min(start.x, end.x) - 1) >> 4;
        int maxX = net.minecraft.util.Mth.floor(Math.max(start.x, end.x) + 1) >> 4;
        int minZ = net.minecraft.util.Mth.floor(Math.min(start.z, end.z) - 1) >> 4;
        int maxZ = net.minecraft.util.Mth.floor(Math.max(start.z, end.z) + 1) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (player.serverLevel().getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    public static boolean tick(ServerPlayer player, Session session, BooleanSupplier owned) {
        if (!BUSY.add(player.getUUID())) return true;
        try {
            if (!session.active || !eligible(player, session.state) || AbilityStorage.get(player) != session.state || !owned.getAsBoolean()) return false;
            var target = target(player, session.itemMode);
            if (target == null || !target.valid() || target.full()) return false;
            long now = player.getServer().overworld().getGameTime();
            CompoundTag before = player.getPersistentData().getCompound(DATA_KEY).copy();
            Budget budget = budget(before, now, session.speed);
            if (!budget.valid) return false;
            if (budget.sameTick) return true;
            double accepted = target.accepted(Math.min(session.speed, budget.remaining));
            if (!(accepted > 0)) return false;
            double cost = HighPowerChargingRules.price(accepted, session.speed, session.cost);
            double strain = session.started ? 0 : session.startOverload;
            boolean fills = target.fills(accepted);
            boolean committed = session.state.consumeKnownEnergy(CurrentChargingSession.ID, cost, strain,
                    () -> session.active && owned.getAsBoolean() && eligible(player, session.state)
                            && AbilityStorage.get(player) == session.state
                            && before.equals(player.getPersistentData().getCompound(DATA_KEY))
                            && target.same(target(player, session.itemMode)),
                    () -> {
                        // Persistent quota is claimed before native dirty notifications can run.
                        CompoundTag next = before.copy(); ListTag entries = budget.entries.copy();
                        var entry = new CompoundTag(); entry.putLong("tick", now); entry.putDouble("amount", accepted); entries.add(entry);
                        next.putLong("lastTick", now); next.put("entries", entries);
                        player.getPersistentData().put(DATA_KEY, next);
                        target.commit(accepted);
                        session.started = true;
                    }, () -> session.state.addExperience(CurrentChargingSession.ID, CurrentChargingSession.SUPPORTED_EXPERIENCE));
            return committed && !fills;
        } catch (RuntimeException failure) {
            // Closed native setters write energy before marking dirty. Never blindly replay or
            // refund after an unexpected exception: that could duplicate an already-made write.
            LogUtils.getLogger().error("High-power native charging ended after an unexpected transaction error", failure);
            return false;
        } finally { BUSY.remove(player.getUUID()); }
    }
    private record Budget(boolean valid, boolean sameTick, double remaining, ListTag entries) {}
    private static Budget budget(CompoundTag tag, long now, double speed) {
        if (now < 0 || tag.contains("lastTick") && (!tag.contains("lastTick", Tag.TAG_LONG) || tag.getLong("lastTick") > now))
            return new Budget(false, false, 0, new ListTag());
        boolean same = tag.contains("lastTick", Tag.TAG_LONG) && tag.getLong("lastTick") == now;
        if (tag.contains("entries") && !tag.contains("entries", Tag.TAG_LIST)) return new Budget(false, same, 0, new ListTag());
        ListTag existing = tag.getList("entries", Tag.TAG_COMPOUND), active = new ListTag();
        if (tag.get("entries") instanceof ListTag raw && !raw.isEmpty() && raw.getElementType() != Tag.TAG_COMPOUND)
            return new Budget(false, same, 0, active);
        if (!existing.isEmpty() && (!tag.contains("lastTick", Tag.TAG_LONG)
                || tag.getLong("lastTick") != existing.getCompound(existing.size() - 1).getLong("tick")))
            return new Budget(false, same, 0, active);
        if (existing.size() > HighPowerChargingRules.WINDOW_TICKS) return new Budget(false, same, 0, active);
        double sum = 0; long previous = Long.MIN_VALUE;
        for (int i = 0; i < existing.size(); i++) {
            CompoundTag item = existing.getCompound(i); long tick = item.getLong("tick"); double amount = item.getDouble("amount");
            if (!item.contains("tick", Tag.TAG_LONG) || !item.contains("amount", Tag.TAG_DOUBLE) || tick <= previous
                    || tick > now || tick < 0 || !Double.isFinite(amount) || amount <= 0 || amount > 35)
                return new Budget(false, same, 0, active);
            previous = tick;
            if (now - tick < HighPowerChargingRules.WINDOW_TICKS) { active.add(item.copy()); sum += amount; }
        }
        return new Budget(true, same, Math.max(0, speed * HighPowerChargingRules.WINDOW_TICKS - sum), active);
    }
    public static void clear() { BUSY.clear(); }
    /** Death/replacement must not refill the rolling output quota. */
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original && event.getEntity() instanceof ServerPlayer replacement
                && original.getPersistentData().contains(DATA_KEY, Tag.TAG_COMPOUND))
            replacement.getPersistentData().put(DATA_KEY, original.getPersistentData().getCompound(DATA_KEY).copy());
    }
    public static final class Session {
        private final AbilityProgress state;
        private final boolean itemMode;
        private final double speed, cost, startOverload;
        private boolean active = true, started;
        private Session(AbilityProgress state, boolean itemMode, double mastery) {
            this.state = state; this.itemMode = itemMode;
            speed = CurrentChargingSession.speed(mastery); cost = CurrentChargingSession.consumption(mastery);
            startOverload = CurrentChargingSession.startOverload(mastery);
        }
        public AbilityProgress state() { return state; }
        public boolean itemMode() { return itemMode; }
        public boolean active() { return active; }
        public void end() { active = false; }
    }
}

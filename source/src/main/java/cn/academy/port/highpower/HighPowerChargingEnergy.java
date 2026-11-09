package cn.academy.port.highpower;

import cn.academy.port.AcademyCraft;
import cn.academy.port.energy.ClassicEnergy;
import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.energy.ClassicEnergyItems;
import cn.academy.port.former.ClassicMetalFormerBlockEntity;
import cn.academy.port.fusion.ClassicFusorBlockEntity;
import cn.academy.port.machine.MachineDeveloperBlock;
import cn.academy.port.machine.MachineDeveloperBlockEntity;
import cn.academy.port.machine.MachineDeveloperStructure;
import cn.academy.port.machine.ImagFluxReceiver;
import cn.academy.port.wireless.ClassicWirelessNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** Closed native IF whitelist. Never queries, simulates or calls foreign capabilities. */
public final class HighPowerChargingEnergy {
    private HighPowerChargingEnergy() {}
    public static boolean knownItem(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.is(AcademyCraft.DEVELOPER.get()) || stack.is(ClassicEnergyItems.ENERGY_UNIT.get()));
    }
    public static Target item(ItemStack stack) {
        if (!knownItem(stack) || stack.getCount() != 1) return null;
        ClassicEnergy.Access access = ClassicEnergyItemHelper.nativeStorage(stack);
        if (access == null || !access.available()) return null;
        ItemStack snapshot = stack.copy();
        return new Target(stack, null, access::energy, access::capacity, access::bandwidth,
                () -> stack.getCount() == 1 && ItemStack.matches(stack, snapshot) && access.available(), access::energy);
    }
    public static Target block(ServerLevel level, BlockPos pos) {
        BlockEntity entity = loaded(level, pos);
        if (entity == null) return null;
        BlockState state = entity.getBlockState();
        BooleanSupplier valid = () -> loaded(level, pos) == entity && entity.getBlockState() == state;
        if (entity instanceof MachineDeveloperBlockEntity machine) {
            BlockPos origin = machine.originPos();
            BlockEntity found = loaded(level, origin);
            if (!(found instanceof MachineDeveloperBlockEntity root) || !root.isOrigin()
                    || root.getBlockState().getBlock() != state.getBlock() || root.facing() != machine.facing()
                    || !completeLoaded(level, root)) return null;
            BlockState rootState = root.getBlockState();
            BooleanSupplier complete = () -> valid.getAsBoolean() && loaded(level, origin) == root
                    && root.getBlockState() == rootState && completeLoaded(level, root);
            return receiver(root, pos.immutable(), root.battery(), complete);
        }
        if (entity instanceof ClassicWirelessNodeBlockEntity node) {
            return new Target(node, pos.immutable(), node::getEnergy, node::getMaxEnergy,
                    () -> Double.MAX_VALUE, () -> valid.getAsBoolean() && node.available(), node::setEnergy);
        }
        if (entity instanceof ClassicFusorBlockEntity fusor)
            return receiver(fusor, pos.immutable(), fusor, () -> valid.getAsBoolean() && fusor.available());
        if (entity instanceof ClassicMetalFormerBlockEntity former)
            return receiver(former, pos.immutable(), former, () -> valid.getAsBoolean() && former.available());
        return null;
    }
    private static Target receiver(Object identity, BlockPos selected, ImagFluxReceiver receiver, BooleanSupplier valid) {
        return new Target(identity, selected, receiver::getEnergy, receiver::getMaxEnergy,
                () -> Double.MAX_VALUE, valid, next -> receiver.injectEnergy(next - receiver.getEnergy()));
    }
    private static BlockEntity loaded(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || level.isOutsideBuildHeight(pos)) return null;
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;
        var entity = chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
        return entity == null || entity.isRemoved() ? null : entity;
    }
    private static boolean completeLoaded(ServerLevel level, MachineDeveloperBlockEntity root) {
        for (int part = 0; part < 8; part++) {
            BlockPos cell = root.getBlockPos().offset(MachineDeveloperBlock.offset(part, root.facing()));
            if (level.getChunkSource().getChunkNow(cell.getX() >> 4, cell.getZ() >> 4) == null) return false;
        }
        return MachineDeveloperStructure.complete(level, root.getBlockPos(), root.getBlockState());
    }
    public static final class Target {
        private final Object identity;
        private final BlockPos selected;
        private final DoubleSupplier energy, capacity, bandwidth;
        private final BooleanSupplier live;
        private final DoubleConsumer write;
        private final double before, maximum, limit;
        private Target(Object identity, BlockPos selected, DoubleSupplier energy, DoubleSupplier capacity,
                       DoubleSupplier bandwidth, BooleanSupplier live, DoubleConsumer write) {
            this.identity = identity; this.selected = selected; this.energy = energy; this.capacity = capacity;
            this.bandwidth = bandwidth; this.live = live; this.write = write;
            before = energy.getAsDouble(); maximum = capacity.getAsDouble(); limit = bandwidth.getAsDouble();
        }
        public boolean valid() {
            return live.getAsBoolean() && Double.isFinite(before) && before >= 0
                    && Double.isFinite(maximum) && maximum > 0 && before <= maximum
                    && Double.isFinite(limit) && limit > 0 && energy.getAsDouble() == before
                    && capacity.getAsDouble() == maximum && bandwidth.getAsDouble() == limit;
        }
        public boolean same(Target other) {
            return other != null && identity == other.identity && java.util.Objects.equals(selected, other.selected)
                    && before == other.before && maximum == other.maximum && limit == other.limit && valid() && other.valid();
        }
        /** Canonical origin is the only mutable store for developer aliases. */
        public BlockPos storagePosition() { return identity instanceof BlockEntity entity ? entity.getBlockPos() : selected; }
        public boolean full() { return valid() && before == maximum; }
        public double accepted(double request) { return HighPowerChargingRules.accepted(before, maximum, limit, request); }
        public boolean fills(double accepted) { return before + accepted == maximum; }
        /** Planned energy is set before known native dirty notifications. No foreign receive callbacks. */
        public void commit(double accepted) { write.accept(before + accepted); }
    }
}

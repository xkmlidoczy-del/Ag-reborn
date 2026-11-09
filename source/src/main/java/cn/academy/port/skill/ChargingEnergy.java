/* AcademyCraft 1.0.7 EnergyItem/BlockHelper and RFSupport adaptation. GPLv3. See NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.CurrentChargingSession;
import cn.academy.port.energy.ClassicEnergy;
import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.energy.ClassicEnergyBlockHelper;
import cn.academy.port.machine.MachineDeveloperBlockEntity;
import cn.academy.port.machine.MachineDeveloperBlock;
import cn.academy.port.machine.MachineDeveloperStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Finite native IF / NeoForge FE adapters. Existence is support even when no energy can be received. */
public final class ChargingEnergy {
    public static final int FE_PER_IF = 4;
    private ChargingEnergy() {}

    public static CurrentChargingSession.Target item(ItemStack stack) {
        boolean present = stack != null && !stack.isEmpty();
        if (!present) return new Target(false, null, null);
        // Native storage retains fractional IF and original item-bandwidth behavior.
        var nativeStorage = ClassicEnergyItemHelper.nativeStorage(stack);
        if (nativeStorage != null) return new Target(true, nativeStorage, null);
        return new Target(true, null, stack.getCapability(Capabilities.EnergyStorage.ITEM));
    }

    public static CurrentChargingSession.Target block(Level level, BlockPos pos) {
        return new BlockTarget(level, pos == null ? null : pos.immutable());
    }

    public static boolean blockSupported(Level level, BlockPos pos) {
        return resolveBlock(level, pos).supported();
    }

    private record BlockAccess(Object nativeStorage, IEnergyStorage forge) {
        boolean supported() { return nativeStorage != null || forge != null; }
    }
    private static final BlockAccess UNSUPPORTED = new BlockAccess(null, null);

    private static BlockAccess resolveBlock(Level level, BlockPos pos) {
        if (level == null || pos == null || level.isOutsideBuildHeight(pos)) return UNSUPPORTED;
        // getChunkNow never creates or loads a chunk. Supplying both state and entity
        // to getCapability also prevents a second implicit entity lookup in the FE path.
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return UNSUPPORTED;
        var entity = chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
        if (entity != null && entity.isRemoved()) return UNSUPPORTED;
        if (entity instanceof MachineDeveloperBlockEntity machine) {
            // Preserve the existing playable body-part alias, but canonicalize it
            // directly to one finite native origin rather than transferring through
            // both a slave-local store and the root's modern FE representation.
            var origin = machine.originPos();
            var rootChunk = level.getChunkSource().getChunkNow(origin.getX() >> 4, origin.getZ() >> 4);
            if (rootChunk == null) return UNSUPPORTED;
            var found = rootChunk.getBlockEntity(origin, LevelChunk.EntityCreationType.IMMEDIATE);
            if (!(found instanceof MachineDeveloperBlockEntity root) || root.isRemoved() || !root.isOrigin()
                    || root.getBlockState().getBlock() != entity.getBlockState().getBlock()
                    || root.facing() != machine.facing()) return UNSUPPORTED;
            for (int part = 0; part < 8; part++) {
                var cell = origin.offset(MachineDeveloperBlock.offset(part, root.facing()));
                if (level.getChunkSource().getChunkNow(cell.getX() >> 4, cell.getZ() >> 4) == null)
                    return UNSUPPORTED;
            }
            if (!MachineDeveloperStructure.complete(level, origin, root.getBlockState())) return UNSUPPORTED;
            Object receiver = level.isClientSide ? root.battery() : root.imagFluxReceiver();
            return receiver == null ? UNSUPPORTED : new BlockAccess(receiver, null);
        }
        if (ClassicEnergyBlockHelper.isSupported(entity)) return new BlockAccess(entity, null);
        // Source RF block managers use UP, independent of the face hit by the ray.
        // FE compatibility remains a fallback, never a second transfer to a native store.
        return new BlockAccess(null, level.getCapability(Capabilities.EnergyStorage.BLOCK,
                pos, chunk.getBlockState(pos), entity, Direction.UP));
    }

    private record BlockTarget(Level level, BlockPos pos) implements CurrentChargingSession.Target {
        @Override public boolean present() { return false; }
        @Override public boolean supported() { return resolveBlock(level, pos).supported(); }
        @Override public double charge(double amount, boolean ignoreBandwidth) {
            // Client effects may classify support but never mutate native or external energy.
            if (level == null || level.isClientSide) return amount;
            var access = resolveBlock(level, pos);
            return access.nativeStorage != null
                    ? ClassicEnergyBlockHelper.charge(access.nativeStorage, amount, ignoreBandwidth)
                    : chargeForge(access.forge, amount);
        }
    }

    /** Returns untransferred IF. One real receive call; no repeated calls to bypass FE bandwidth. */
    public static double chargeForge(IEnergyStorage storage, double amount) {
        if (storage == null || !Double.isFinite(amount) || amount <= 0) return amount;
        if (!storage.canReceive()) return amount;
        int request = (int) Math.min(Integer.MAX_VALUE, Math.floor(amount * FE_PER_IF));
        if (request <= 0) return amount;
        int accepted = Math.max(0, Math.min(request, storage.receiveEnergy(request, false)));
        return amount - (double) accepted / FE_PER_IF;
    }

    private record Target(boolean present, ClassicEnergy.Access nativeStorage, IEnergyStorage forge)
            implements CurrentChargingSession.Target {
        @Override public boolean supported() { return nativeStorage != null || forge != null; }
        @Override public double charge(double amount, boolean ignoreBandwidth) {
            return nativeStorage != null ? ClassicEnergy.charge(nativeStorage, amount, ignoreBandwidth) : chargeForge(forge, amount);
        }
    }
}

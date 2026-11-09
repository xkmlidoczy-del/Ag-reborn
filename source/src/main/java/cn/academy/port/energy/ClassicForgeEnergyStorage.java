package cn.academy.port.energy;

import java.util.Objects;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Finite optional FE view. RFSupport declares 1 IF=4 RF; modern FE preserves that explicit conversion. */
public final class ClassicForgeEnergyStorage implements IEnergyStorage {
    public static final int FE_PER_IF = 4;
    private final ClassicEnergy.Access storage;
    public ClassicForgeEnergyStorage(ClassicEnergy.Access storage) { this.storage = Objects.requireNonNull(storage); }
    private double energy() { return ClassicEnergy.sanitize(storage.energy(), storage.capacity()); }
    private static int fe(double energy) { return (int) Math.min(Integer.MAX_VALUE, Math.floor(Math.max(0, energy) * FE_PER_IF)); }
    @Override public int receiveEnergy(int amount, boolean simulate) {
        if (!storage.available() || amount <= 0) return 0;
        double current = energy();
        int accepted = Math.min(amount, fe(Math.min(ClassicEnergy.finiteBandwidth(storage.bandwidth()), storage.capacity() - current)));
        if (!simulate && accepted > 0) storage.energy(current + (double) accepted / FE_PER_IF);
        return accepted;
    }
    @Override public int extractEnergy(int amount, boolean simulate) {
        if (!storage.available() || amount <= 0) return 0;
        double current = energy();
        int extracted = Math.min(amount, fe(Math.min(ClassicEnergy.finiteBandwidth(storage.bandwidth()), current)));
        if (!simulate && extracted > 0) storage.energy(current - (double) extracted / FE_PER_IF);
        return extracted;
    }
    @Override public int getEnergyStored() { return storage.available() ? fe(energy()) : 0; }
    @Override public int getMaxEnergyStored() { return storage.available() ? fe(storage.capacity()) : 0; }
    @Override public boolean canReceive() { return storage.available(); }
    @Override public boolean canExtract() { return storage.available(); }
}

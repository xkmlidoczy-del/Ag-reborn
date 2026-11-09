package cn.academy.port.develop;

import java.util.Objects;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Optional NeoForge item-capability adapter. Classic RFSupport conversion is 1 IF = 4 RF/FE. */
public final class DeveloperForgeEnergyStorage implements IEnergyStorage {
    public static final int FE_PER_IF = 4;
    private final DeveloperEnergy.Access storage;

    public DeveloperForgeEnergyStorage(DeveloperEnergy.Access storage) { this.storage = Objects.requireNonNull(storage); }

    @Override public int receiveEnergy(int amount, boolean simulate) {
        if (!storage.available() || amount <= 0) return 0;
        double current = DeveloperEnergy.sanitize(storage.energy(), storage.type());
        int accepted = (int) Math.floor(Math.min(amount,
                Math.min(storage.type().bandwidth, storage.type().energy - current) * FE_PER_IF));
        if (!simulate && accepted > 0) storage.energy(current + (double) accepted / FE_PER_IF);
        return accepted;
    }

    @Override public int extractEnergy(int amount, boolean simulate) {
        if (!storage.available() || amount <= 0) return 0;
        double current = DeveloperEnergy.sanitize(storage.energy(), storage.type());
        int extracted = (int) Math.floor(Math.min(amount, Math.min(storage.type().bandwidth, current) * FE_PER_IF));
        if (!simulate && extracted > 0) storage.energy(current - (double) extracted / FE_PER_IF);
        return extracted;
    }

    @Override public int getEnergyStored() { return storage.available() ? (int) Math.floor(DeveloperEnergy.sanitize(storage.energy(), storage.type()) * FE_PER_IF) : 0; }
    @Override public int getMaxEnergyStored() { return storage.available() ? (int) (storage.type().energy * FE_PER_IF) : 0; }
    @Override public boolean canExtract() { return storage.available(); }
    @Override public boolean canReceive() { return storage.available(); }
}

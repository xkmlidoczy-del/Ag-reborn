package cn.academy.port.machine;

import java.util.function.LongSupplier;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Receive-only FE adapter for a source receiver. This is not a wireless network or generator. */
public final class MachineDeveloperForgeEnergy implements IEnergyStorage {
    private final MachineDeveloperEnergy energy;
    private final LongSupplier tick;
    public MachineDeveloperForgeEnergy(MachineDeveloperEnergy energy,LongSupplier tick){this.energy=energy;this.tick=tick;}
    @Override public int receiveEnergy(int amount,boolean simulate){return energy.receiveFe(amount,simulate,tick.getAsLong());}
    @Override public int extractEnergy(int amount,boolean simulate){return 0;}
    @Override public int getEnergyStored(){return (int)Math.floor(energy.getEnergy()*MachineDeveloperEnergy.FE_PER_IF);}
    @Override public int getMaxEnergyStored(){return (int)(energy.getMaxEnergy()*MachineDeveloperEnergy.FE_PER_IF);}
    @Override public boolean canReceive(){return energy.available();}
    @Override public boolean canExtract(){return false;}
}

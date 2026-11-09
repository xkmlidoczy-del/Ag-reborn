/* Source TileReceiverBase + TileDeveloper arithmetic, adapted under GPLv3. See NOTICE. */
package cn.academy.port.machine;

import cn.academy.port.develop.DeveloperEnergy;
import cn.academy.port.develop.DeveloperType;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Finite receiver battery. Unlike PortableDevData, an insufficient atomic pull leaves its energy intact. */
public final class MachineDeveloperEnergy implements ImagFluxReceiver, DeveloperEnergy.Access {
    public static final int FE_PER_IF=4;
    private final DeveloperType type;
    private final Runnable changed;
    private final BooleanSupplier available;
    private double energy;
    private long receiveTick=Long.MIN_VALUE;
    private int receivedFe;
    public MachineDeveloperEnergy(DeveloperType type,Runnable changed,BooleanSupplier available) {
        if(type==DeveloperType.PORTABLE)throw new IllegalArgumentException("machine tier required");
        this.type=Objects.requireNonNull(type);this.changed=Objects.requireNonNull(changed);this.available=Objects.requireNonNull(available);
    }
    @Override public DeveloperType type(){return type;}
    @Override public boolean available(){return available.getAsBoolean();}
    @Override public double energy(){return available()?energy:0;}
    @Override public void energy(double value){double sanitized=DeveloperEnergy.sanitize(value,type);if(energy!=sanitized){energy=sanitized;changed.run();}}
    public void load(double value){energy=DeveloperEnergy.sanitize(value,type);receiveTick=Long.MIN_VALUE;receivedFe=0;}
    public double persistedEnergy(){return energy;}
    @Override public double getEnergy(){return energy();}
    @Override public double getMaxEnergy(){return available()?type.energy:0;}
    @Override public double getRequiredEnergy(){return available()?type.energy-energy:0;}
    @Override public double getBandwidth(){return type.bandwidth;}
    @Override public double injectEnergy(double amount){
        if(!available()||!Double.isFinite(amount)||amount<0)return amount;
        double accepted=Math.min(amount,type.energy-energy);energy(energy+accepted);return amount-accepted;
    }
    @Override public double pullEnergy(double amount){
        if(!available()||!Double.isFinite(amount)||amount<0)return 0;
        double pulled=Math.min(amount,energy);energy(energy-pulled);return pulled;
    }
    public boolean tryPull(double amount){
        if(!available()||!Double.isFinite(amount)||amount<0||energy<amount)return false;
        energy(energy-amount);return true;
    }
    /** Modern finite FE input compatibility. One shared per-game-tick bandwidth budget across all faces. */
    public int receiveFe(int amount,boolean simulate,long gameTick){
        if(!available()||amount<=0)return 0;
        int used=receiveTick==gameTick?receivedFe:0;
        int accepted=(int)Math.floor(Math.min(amount,Math.min(type.bandwidth*FE_PER_IF-used,(type.energy-energy)*FE_PER_IF)));
        if(!simulate&&accepted>0){if(receiveTick!=gameTick){receiveTick=gameTick;receivedFe=0;}receivedFe+=accepted;energy(energy+(double)accepted/FE_PER_IF);}
        return accepted;
    }
}

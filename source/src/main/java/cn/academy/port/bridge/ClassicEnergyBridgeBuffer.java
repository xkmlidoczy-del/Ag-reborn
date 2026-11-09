/* AcademyCraft1.0.7 RFSupport/TileRFInput/TileRFOutput adaptation. GPLv3; see NOTICE. */
package cn.academy.port.bridge;

import net.neoforged.neoforge.energy.IEnergyStorage;

/** One finite IF store; FE is a directional view, never another battery. */
public final class ClassicEnergyBridgeBuffer {
    public static final double CAPACITY=2000, BANDWIDTH=100;
    public static final int FE_PER_IF=4;
    private double energy;
    private boolean transferring;
    public double energy(){return energy;}
    public void load(double value){energy=Double.isFinite(value)?Math.max(0,Math.min(CAPACITY,value)):0;}
    public static int fe(double value){return Double.isFinite(value)&&value>0?(int)Math.min(Integer.MAX_VALUE,Math.floor(value*FE_PER_IF)):0;}
    /** Source input truncates its RF request to whole IF before insertion. Actual acceptance fixes its remainder-report defect. */
    public int receive(int amount,boolean simulate){if(transferring||amount<=0)return 0;double accepted=Math.min(CAPACITY-energy,amount/FE_PER_IF);int moved=fe(accepted);if(!simulate)energy+=(double)moved/FE_PER_IF;return moved;}
    /** Source pull has no transport bandwidth limit; debit exactly the returned FE, including fractional IF. */
    public int extract(int amount,boolean simulate){if(transferring||amount<=0)return 0;int moved=Math.min(amount,fe(energy));if(!simulate)energy-=(double)moved/FE_PER_IF;return moved;}
    public double add(double amount){if(transferring||!Double.isFinite(amount)||amount<=0)return amount;double moved=Math.min(amount,CAPACITY-energy);energy+=moved;return amount-moved;}
    public double pull(double amount){if(transferring||!Double.isFinite(amount)||amount<=0)return 0;double moved=Math.min(amount,energy);energy-=moved;return moved;}
    /** One source-order neighbor call. Reservation and a reentrancy fence keep callbacks from spending the same IF twice. */
    public int push(IEnergyStorage target){
        if(transferring||target==null||!target.canReceive())return 0;
        long headroom=(long)target.getMaxEnergyStored()-target.getEnergyStored();int request=(int)Math.min(fe(energy),Math.max(0,headroom));if(request<=0)return 0;
        double reserve=(double)request/FE_PER_IF;energy-=reserve;transferring=true;int accepted=0;
        try{accepted=Math.max(0,Math.min(request,target.receiveEnergy(request,false)));return accepted;}
        finally{energy+=(double)(request-accepted)/FE_PER_IF;transferring=false;}
    }
}

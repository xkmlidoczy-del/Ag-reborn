/* AcademyCraft1.0.7 TilePhaseGen and TileGeneratorBase finite arithmetic. GPLv3. See NOTICE. */
package cn.academy.port.phasegen;

/** A finite authoritative buffer and tank. A charge receiver returns actual accepted IF. */
public final class ClassicPhaseGeneratorBuffer {
    @FunctionalInterface public interface ChargeReceiver { double charge(double request); }
    private double energy;
    private int liquid;
    public double energy(){return energy;}
    public int liquid(){return liquid;}
    public void load(double energy,int liquid){this.energy=ClassicPhaseGeneratorRules.sanitizeEnergy(energy);this.liquid=ClassicPhaseGeneratorRules.sanitizeLiquid(liquid);}
    public int fill(int amount,boolean simulate){if(amount<=0)return 0;int accepted=Math.min(amount,ClassicPhaseGeneratorRules.TANK_SIZE-liquid);if(!simulate)liquid+=accepted;return accepted;}
    public int drain(int amount,boolean simulate){if(amount<=0)return 0;int taken=Math.min(amount,liquid);if(!simulate)liquid-=taken;return taken;}
    /** Source integer truncation stops when less than0.5IF of buffer space remains. */
    public double generate(){int taken=drain(ClassicPhaseGeneratorRules.drainForGeneration(ClassicPhaseGeneratorRules.CAPACITY-energy),false);double made=taken*ClassicPhaseGeneratorRules.GEN_PER_MB;energy+=made;return made;}
    public double charge(ChargeReceiver receiver){if(receiver==null||energy<=0)return 0;double request=Math.min(energy,ClassicPhaseGeneratorRules.BANDWIDTH);double accepted=receiver.charge(request);if(!Double.isFinite(accepted))return 0;accepted=Math.max(0,Math.min(request,accepted));energy-=accepted;return accepted;}
    public double tick(ChargeReceiver receiver){return tick(null,receiver);}
    /** Acquisition happens after generation, so a newly inserted unit begins generating next tick. */
    public double tick(Runnable acquireUnit,ChargeReceiver receiver){double before=energy;generate();if(acquireUnit!=null)acquireUnit.run();charge(receiver);return energy-before;}
    public double addEnergy(double amount,boolean simulate){if(!Double.isFinite(amount)||amount<=0)return amount;double accepted=Math.min(ClassicPhaseGeneratorRules.CAPACITY-energy,amount);if(!simulate)energy+=accepted;return amount-accepted;}
    /** As in source, the wireless graph caller enforces its own bandwidth. */
    public double getProvidedEnergy(double request){if(!Double.isFinite(request)||request<=0)return 0;double provided=Math.min(request,energy);energy-=provided;return provided;}
}

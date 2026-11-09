/* AcademyCraft1.0.7 TileGeneratorBase arithmetic, GPLv3; see NOTICE. */
package cn.academy.port.wind;

/** One finite root-owned IF buffer. The graph owns its own bandwidth constraint. */
public final class ClassicWindBuffer {
    @FunctionalInterface public interface Receiver{double charge(double offered);}
    private double energy;
    public double energy(){return energy;}
    public void load(double amount){energy=ClassicWindRules.finiteEnergy(amount);}
    public double generate(int mainY,boolean working){double amount=Math.min(ClassicWindRules.CAPACITY-energy,ClassicWindRules.generation(mainY,working));energy+=amount;return amount;}
    public double charge(Receiver receiver){if(receiver==null||energy<=0)return 0;double offered=Math.min(energy,ClassicWindRules.BANDWIDTH),accepted=receiver.charge(offered);if(!Double.isFinite(accepted))return 0;accepted=Math.max(0,Math.min(offered,accepted));energy-=accepted;return accepted;}
    public double addEnergy(double amount,boolean simulate){if(!Double.isFinite(amount)||amount<=0)return amount;double accepted=Math.min(ClassicWindRules.CAPACITY-energy,amount);if(!simulate)energy+=accepted;return amount-accepted;}
    public double getProvidedEnergy(double request){if(!Double.isFinite(request)||request<=0)return 0;double actual=Math.min(request,energy);energy-=actual;return actual;}
}

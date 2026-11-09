/* AcademyCraft1.0.7 TileCatEngine/TileGeneratorBase arithmetic. GPLv3; see NOTICE. */
package cn.academy.port.cat;
/** Creative generator; no inventory, fuel, charge slot, GUI, or survival recipe exists in source. */
public final class ClassicCatBuffer {
    public static final double CAPACITY=2000, BANDWIDTH=200, GENERATION=500;
    private double energy, generated;
    public double energy(){return energy;}
    public double generated(){return generated;}
    public double tick(){generated=Math.min(CAPACITY-energy,GENERATION);energy+=generated;return generated;}
    public double provide(double request){double supplied=Math.min(energy,positive(request));energy-=supplied;return supplied;}
    /** Source returns the unconsumed remainder and supports nonmutating simulation. */
    public double addEnergy(double amount,boolean simulate){if(!Double.isFinite(amount)||amount<=0)return amount;double accepted=Math.min(CAPACITY-energy,amount);if(!simulate)energy+=accepted;return amount-accepted;}
    /** Modern finite malformed-save/request admission and persistence repair; source omitted energy NBT. */
    public void load(double amount){energy=Math.min(CAPACITY,positive(amount));generated=0;}
    private static double positive(double value){return Double.isFinite(value)?Math.max(0,value):0;}
}

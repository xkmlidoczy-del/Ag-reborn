/* AcademyCraft1.0.7 TilePhaseGen/TileGeneratorBase arithmetic. GPLv3. See NOTICE. */
package cn.academy.port.phasegen;

/** Source quantities and integer mB conversion, independent of Minecraft. */
public final class ClassicPhaseGeneratorRules {
    public static final int TANK_SIZE=8000, PER_UNIT=1000, CONSUME_PER_TICK=100;
    public static final double GEN_PER_MB=.5, CAPACITY=6000, BANDWIDTH=50;
    private ClassicPhaseGeneratorRules(){}
    public static double sanitizeEnergy(double amount){return Double.isFinite(amount)?Math.max(0,Math.min(CAPACITY,amount)):0;}
    public static int sanitizeLiquid(int amount){return Math.max(0,Math.min(TANK_SIZE,amount));}
    public static int drainForGeneration(double required){return Double.isFinite(required)&&required>0?(int)Math.min(CONSUME_PER_TICK,required/GEN_PER_MB):0;}
    /** Original inventory acquisition intentionally requires more than one unit of free tank. */
    public static boolean canAcquireUnit(int liquid){return TANK_SIZE-sanitizeLiquid(liquid)>PER_UNIT;}
    public static int textureIndex(int liquid){return Math.max(0,Math.min(4,(int)Math.round(4.0*sanitizeLiquid(liquid)/TANK_SIZE)));}
    public static int word(double energy,int index){if(index<0||index>=4)throw new IndexOutOfBoundsException(index);return (int)((Double.doubleToLongBits(sanitizeEnergy(energy))>>>(index*16))&65535L);}
    public static double fromWords(int a,int b,int c,int d){long bits=(a&65535L)|((b&65535L)<<16)|((c&65535L)<<32)|((d&65535L)<<48);return sanitizeEnergy(Double.longBitsToDouble(bits));}
}

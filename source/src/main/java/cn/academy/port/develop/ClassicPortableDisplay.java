/* AcademyCraft1.0.7 ItemEnergyBase/IFItemManager display arithmetic. GPLv3; see NOTICE. */
package cn.academy.port.develop;
import java.util.Locale;

/** Read-only display of existing portable IF, without changing its payload, capabilities or durability. */
public final class ClassicPortableDisplay {
    public static final int GAUGE_DAMAGE=13;
    public static final double CAPACITY=DeveloperType.PORTABLE.energy;
    public static double bounded(double energy){return DeveloperEnergy.sanitize(energy,DeveloperType.PORTABLE);}
    public static int damage(double energy){return (int)Math.round((1-bounded(energy)/CAPACITY)*GAUGE_DAMAGE);}
    public static int width(double energy){return GAUGE_DAMAGE-damage(energy);}
    public static boolean visible(double energy){return damage(energy)>0;}
    public static float hue(double energy){return (1f-damage(energy)/(float)GAUGE_DAMAGE)/3f;}
    public static int icon(double energy){int damage=damage(energy);return damage<3?2:damage>10?0:1;}
    public static String tooltip(double energy){return String.format(Locale.ROOT,"%.0f/%.0f IF",bounded(energy),CAPACITY);}
    private ClassicPortableDisplay(){}
}

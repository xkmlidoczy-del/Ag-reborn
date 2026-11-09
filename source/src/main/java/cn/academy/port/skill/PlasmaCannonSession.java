/* AcademyCraft1.0.7 PlasmaCannonContext adaptation. GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.core.AbilityProgress;
/** Captured float charge threshold, free indefinite ready hold, six-tick sync, destination-centered detonation. */
public final class PlasmaCannonSession {
 public static final String ID="plasma_cannon";public static final int LEVEL=5,MAX_TRAVEL_TICKS=240;public static final double SPEED=1;
 private final AbilityProgress state;private final float charge;private final double floor;private boolean active=true,ending,go;private int ticks,sync;
 private PlasmaCannonSession(AbilityProgress s,boolean creative){state=s;charge=chargeTime(s.exp(ID));s.consumeSkill(ID,0,initialOverload(s.exp(ID)),creative);floor=s.overload;}
 private static float lerp(float a,float b,double e){return a+(float)e*(b-a);}public static float chargeTime(double e){return lerp(60,30,e);}public static float initialOverload(double e){return lerp(500,400,e);}public static float tickCp(double e){return lerp(18,25,e);}public static int cooldown(double e){return (int)lerp(1000,600,e);}public static float damage(double e){return lerp(80,150,e);}public static float explosionPower(double e){return lerp(12,15,e);}
 public static boolean mayStart(AbilityProgress s){return s!=null&&s.category.equals("vecmanip")&&s.level>=LEVEL&&s.canUse(ID)&&Double.isFinite(s.cp)&&Double.isFinite(s.overload)&&Double.isFinite(s.exp(ID));}
 public static PlasmaCannonSession begin(AbilityProgress s,boolean creative){return mayStart(s)?new PlasmaCannonSession(s,creative):null;}
 public boolean tick(boolean creative){if(!active||ending)return false;if(state.overload<floor)state.overload=floor;ticks++;if(!go&&ticks<charge&&!state.consumeSkill(ID,tickCp(state.exp(ID)),0,creative,()->active))ending=true;return !ending;}
 /** Modern sender may request performance only after the server has independently reached the float threshold. */
 public boolean perform(){if(!active||ending||go||ticks<charge)return false;state.addExperience(ID,.008F);go=true;ticks=0;state.setCooldown(ID,cooldown(state.exp(ID)));return true;}
 public static double[] move(double[] from,double[] dest){double x=dest[0]-from[0],y=dest[1]-from[1],z=dest[2]-from[2],d=Math.sqrt(x*x+y*y+z*z);return d<1?from.clone():new double[]{from[0]+x/d,from[1]+y/d,from[2]+z/d};}
 /** Source lacks return after collision explode(): simultaneous arrival/timeout intentionally requests twice. */
 public int explosions(boolean collision,double distance){return (collision?1:0)+(ticks>=MAX_TRAVEL_TICKS||distance<1.5?1:0);}
 public boolean syncPosition(){if(!go)return false;if(sync==0){sync=5;return true;}sync--;return false;}
 public boolean ready(){return active&&!ending&&!go&&ticks>=charge;}public boolean chargedSoundTick(){return !go&&ticks==(int)charge;}public boolean go(){return go;}public int ticks(){return ticks;}public float capturedChargeTime(){return charge;}public AbilityProgress state(){return state;}public double overloadFloor(){return floor;}public boolean active(){return active;}public boolean ending(){return ending;}public void discard(){active=false;ending=true;}
}

/* AcademyCraft1.0.7 MRContext/BasicMRContext adaptation, Copyright Lambda Innovation, GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
/** Pure original target/hardness state machine. Capture is separate from the wire sentinel for modern negative Y. Failed consumption still runs this tick's mining. */
public final class MineRayBasicSession {
    public static final String ID="mine_ray_basic"; public static final int LEVEL=3,HARVEST_LEVEL=2; public static final double RANGE = SkillTuning.number("mine_ray_basic", "reach");
    public record Cell(int x,int y,int z) {public static final Cell NONE=new Cell(-1,-1,-1);}
    public interface World {
        Cell trace(); boolean denied(Cell target); int harvestLevel(Cell target,Cell previousMetadataPosition);float hardness(Cell target);void breakBlock(Cell target);void particles(Cell target);
    }
    private final AbilityProgress state;private final float mastery,speed,consumption;private final double floor;private Cell target=Cell.NONE;private float hardnessLeft=Float.MAX_VALUE;private boolean active=true,ending,captured;private int ticks;
    private MineRayBasicSession(AbilityProgress state,boolean creative){this.state=state;mastery=(float)ClassicRules.clamp(state.exp(ID),0,1);speed=speed(mastery);consumption=consumption(mastery);state.consumeSkill(ID,0,overload(mastery),creative);floor=state.overload;ending=!state.overloadFine;}
    private static float lerp(float a,float b,double exp){return a+(b-a)*(float)ClassicRules.clamp(exp,0,1);}
    public static float speed(double exp){return lerp(.2F,.4F,exp);}public static float consumption(double exp){return lerp(12,7,exp);}public static float overload(double exp){return lerp(200,150,exp);}public static int cooldown(double exp){return (int)lerp(40,20,exp);}
    public static boolean mayStart(AbilityProgress state){return state!=null&&state.category.equals("meltdowner")&&state.level>=LEVEL&&state.canUse(ID);}
    public static MineRayBasicSession begin(AbilityProgress state,boolean creative){return mayStart(state)?new MineRayBasicSession(state,creative):null;}
    public boolean tick(boolean creative,World world){if(!active||ending)return ending;++ticks;if(state.overload<floor)state.overload=floor;if(!state.consumeSkill(ID,consumption,0,creative,()->active)||!state.overloadFine)ending=true;
        if(!active)return true;Cell hit=world.trace();if(hit==null){target=Cell.NONE;captured=false;return ending;}
        if(!captured||!hit.equals(target)){if(!world.denied(hit)&&world.harvestLevel(hit,target)<=HARVEST_LEVEL){target=hit;captured=true;hardnessLeft=world.hardness(hit);if(hardnessLeft<0)hardnessLeft=Float.MAX_VALUE;}else {target=Cell.NONE;captured=false;}}
        else {hardnessLeft-=speed;if(hardnessLeft<=0){world.breakBlock(target);state.addExperience(ID,.0005F);target=Cell.NONE;captured=false;}world.particles(target);}return ending;
    }
    public boolean finish(){if(!active)return false;active=false;ending=true;state.setCooldown(ID,cooldown(mastery));return true;}
    public void discard(){active=false;ending=true;}
    public AbilityProgress state(){return state;}public boolean active(){return active;}public boolean ending(){return ending;}public int ticks(){return ticks;}public Cell target(){return target;}public float hardnessLeft(){return hardnessLeft;}public float mastery(){return mastery;}public double overloadFloor(){return floor;}
}

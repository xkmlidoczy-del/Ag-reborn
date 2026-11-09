/* AcademyCraft1.0.7 MRContext/ExpertMRContext/LuckMRContext adaptation,
 * Copyright (c) Lambda Innovation 2013-2016, GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;

/** The canonical shared MRContext algorithm, with separate Expert and Luck source configuration.
 * No delegation to Basic: each held context captures its own Float mastery and coefficients. */
public final class AdvancedMineRaySession {
    public enum Tier {
        EXPERT("mine_ray_expert",4,25,15,300,200,0),
        LUCK("mine_ray_luck",5,50,35,350,300,3);
        public final String id;
        public final int level,fortune;
        private final float cpLeft,cpRight,overloadLeft,overloadRight;
        Tier(String id,int level,float cpLeft,float cpRight,float overloadLeft,float overloadRight,int fortune) {
            this.id=id;this.level=level;this.cpLeft=cpLeft;this.cpRight=cpRight;
            this.overloadLeft=overloadLeft;this.overloadRight=overloadRight;this.fortune=fortune;
        }
        public float consumption(double exp){return lerp(cpLeft,cpRight,exp);}
        public float overload(double exp){return lerp(overloadLeft,overloadRight,exp);}
    }
    public static final int HARVEST_LEVEL=5;
    public static double range(Tier tier) { return SkillTuning.number(tier.id, "reach"); }
    public static final float EXPERIENCE=.0003F;
    public record Cell(int x,int y,int z) { public static final Cell NONE=new Cell(-1,-1,-1); }
    public interface World {
        Cell trace();boolean denied(Cell target);int harvestLevel(Cell target,Cell previousMetadataPosition);
        float hardness(Cell target);void breakBlock(Cell target,int fortune);void particles(Cell target);
    }
    private final AbilityProgress state;
    private final Tier tier;
    private final float mastery,speed,consumption;
    private final double floor;
    private Cell target=Cell.NONE;
    private float hardnessLeft=Float.MAX_VALUE;
    private boolean active=true,ending,captured;
    private int ticks;
    private AdvancedMineRaySession(AbilityProgress state,Tier tier,boolean creative) {
        this.state=state;this.tier=tier;mastery=(float)ClassicRules.clamp(state.exp(tier.id),0,1);
        speed=speed(mastery);consumption=tier.consumption(mastery);
        // Source consumes startup overload once and captures resulting overload, even on failure.
        state.consumeSkill(tier.id,0,tier.overload(mastery),creative);floor=state.overload;ending=!state.overloadFine;
    }
    private static float lerp(float left,float right,double exp) {return left+(right-left)*(float)ClassicRules.clamp(exp,0,1);}
    public static float speed(double exp){return lerp(.5F,1F,exp);}
    public static int cooldown(double exp){return (int)lerp(60,30,exp);}
    private static boolean finiteResources(AbilityProgress state,Tier tier) {return state.level>=0&&state.level<=5&&Double.isFinite(state.cp)&&state.cp>=0&&Double.isFinite(state.overload)&&state.overload>=0&&Double.isFinite(state.maxCp())&&state.maxCp()>=0&&Double.isFinite(state.maxOverload())&&state.maxOverload()>=0&&Double.isFinite(state.exp(tier.id));}
    public static boolean mayStart(AbilityProgress state,Tier tier) {return state!=null&&tier!=null&&state.category.equals("meltdowner")&&state.level>=tier.level&&state.canUse(tier.id)&&finiteResources(state,tier);}
    public static AdvancedMineRaySession begin(AbilityProgress state,Tier tier,boolean creative) {return mayStart(state,tier)?new AdvancedMineRaySession(state,tier,creative):null;}
    public boolean tick(boolean creative,World world) {
        if(!active||ending)return ending;
        if(!finiteResources(state,tier)){ending=true;return true;} // Fail closed on malformed modern saved/resources state.
        ++ticks;if(state.overload<floor)state.overload=floor;
        // Original terminate() schedules context end; mining still executes on this failed-CP tick.
        if(!state.consumeSkill(tier.id,consumption,0,creative,()->active)||!state.overloadFine)ending=true;
        if(!active)return true;Cell hit=world.trace();if(hit==null){target=Cell.NONE;captured=false;return ending;}
        // Classic sentinel Y=-1 was outside its world. Modern negative build heights require a
        // separate capture flag so the real(-1,-1,-1) block still receives acquisition protection.
        if(!captured||!hit.equals(target)) {
            if(!world.denied(hit)&&world.harvestLevel(hit,target)<=HARVEST_LEVEL) {
                target=hit;captured=true;hardnessLeft=world.hardness(hit);if(hardnessLeft<0)hardnessLeft=Float.MAX_VALUE;
            } else {target=Cell.NONE;captured=false;}
        } else {
            hardnessLeft-=speed;
            if(hardnessLeft<=0){world.breakBlock(target,tier.fortune);state.addExperience(tier.id,EXPERIENCE);target=Cell.NONE;captured=false;}
            world.particles(target); // Literal post-break sentinel(-1,-1,-1), deliberately retained.
        }
        return ending;
    }
    public boolean finish(){if(!active)return false;active=false;ending=true;state.setCooldown(tier.id,cooldown(mastery));return true;}
    public void discard(){active=false;ending=true;}
    public AbilityProgress state(){return state;}public Tier tier(){return tier;}public boolean active(){return active;}
    public boolean ending(){return ending;}public int ticks(){return ticks;}public Cell target(){return target;}public boolean hasTarget(){return captured;}
    public float hardnessLeft(){return hardnessLeft;}public float mastery(){return mastery;}public double overloadFloor(){return floor;}
}

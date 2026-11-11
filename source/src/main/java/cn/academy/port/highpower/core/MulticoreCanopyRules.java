package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** NEW251: one immutable deployment, one exact decimal whole-fire budget, no replacement slots. */
public final class MulticoreCanopyRules {
    public static final int LIFETIME = SkillTuning.integer("multicore_canopy", "duration_ticks"),DEPLOY_INTERVAL=5,WARNING_TICKS=12,SHOT_INTERVAL=5,FIRE_INTERVAL=20,
            PREVIEW_INTERVAL=5,LOS_TIMEOUT=10,MAX_CORES = SkillTuning.integer("multicore_canopy", "max_cores"),MAX_TARGETS=Integer.MAX_VALUE,SCAN_LIMIT=Integer.MAX_VALUE;
    public static final int MAX_PREFIXES=131072,MAX_ENTITY_VISITS=262144,MAX_SHAPES=262144,MAX_PERMISSION_CELLS=524288;
    public static final double RANGE = SkillTuning.number("multicore_canopy", "reach"),CORE_RADIUS=.18,BEAM_RADIUS=.035,MAX_CORE_DISTANCE=8,CONE_DEGREES=10;
    private static final ContinuousHighPowerGrowth.Curve DEPLOY=ContinuousHighPowerGrowth.curve("4","2","1",".2",".01");
    private static final ContinuousHighPowerGrowth.Curve FIRE=ContinuousHighPowerGrowth.curve("80","40","20","4",".2");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN=ContinuousHighPowerGrowth.curve("80","50","30","15","1");
    private MulticoreCanopyRules() {}
    public static BigDecimal theoreticalDeploymentCost(BigDecimal q){return DEPLOY.value(q);}
    public static double deploymentCost(BigDecimal q){return theoreticalDeploymentCost(q).doubleValue();}
    public static BigDecimal theoreticalFireCost(BigDecimal q){return FIRE.value(q);}
    public static double fireCost(BigDecimal q){return theoreticalFireCost(q).doubleValue();}
    public static BigDecimal theoreticalCooldown(BigDecimal q){return COOLDOWN.value(q);}
    public static int cooldownTicks(BigDecimal q){return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(q));}
    public static BigDecimal damage(BigDecimal d){return CombatAmounts.requirePositiveAmount(d,"NEW251 D").multiply(BigDecimal.valueOf(4)).min(CombatAmounts.CAP);}
    /** Last stable slot owns the decimal remainder; dropped slots never change any other amount. */
    public static List<BigDecimal> shares(BigDecimal total,int count){
        total=CombatAmounts.requireAmount(total,"NEW251 whole-fire budget");
        if(count<0||count>MAX_TARGETS)throw new IllegalArgumentException("NEW251 slots");
        if(count==0)return List.of();
        BigDecimal each=total.divide(BigDecimal.valueOf(count),CombatAmounts.MAX_ABSOLUTE_SCALE,RoundingMode.DOWN);
        List<BigDecimal> result=new ArrayList<>(count);
        for(int i=0;i<count-1;i++)result.add(CombatAmounts.requireAmount(each,"NEW251 assigned share"));
        result.add(CombatAmounts.requireAmount(total.subtract(each.multiply(BigDecimal.valueOf(count-1))),"NEW251 remainder"));
        return List.copyOf(result);
    }
    public static boolean clock(long tick){return tick>=0&&tick<=Long.MAX_VALUE-LIFETIME-1;}
    public static boolean expired(long start,long now,int duration){return duration<=0||!clock(start)||!clock(now)||now<start||now-start>=duration;}
    public static boolean ready(long previous,long now,int interval){return interval>0&&clock(now)&&(previous==-1||clock(previous)&&now>=previous&&now-previous>=interval);}
    public static int remaining(long anchor,long now){return expired(anchor,now,LIFETIME)?0:(int)(LIFETIME-(now-anchor));}
    public static final class Work {
        private int prefixes,entities,shapes,cells;private boolean exhausted;
        public boolean prefix(){if(exhausted||prefixes>=MAX_PREFIXES){exhausted=true;return false;}prefixes++;return true;}
        public boolean entities(int count){if(exhausted||count<0||count>MAX_ENTITY_VISITS-entities){exhausted=true;return false;}entities+=count;return true;}
        public boolean shape(){if(exhausted||shapes>=MAX_SHAPES){exhausted=true;return false;}shapes++;return true;}
        public boolean cell(){if(exhausted||cells>=MAX_PERMISSION_CELLS){exhausted=true;return false;}cells++;return true;}
        public boolean valid(){return !exhausted;}public void refuse(){exhausted=true;}
        public int prefixes(){return prefixes;}public int entityVisits(){return entities;}
    }
}

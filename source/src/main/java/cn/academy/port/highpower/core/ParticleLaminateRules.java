package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** NEW241's decimal whole-pulse allocation and fixed, non-refreshable real-tick limits. */
public final class ParticleLaminateRules {
    public static final int FIELD_TICKS = SkillTuning.integer("particle_laminate", "duration_ticks"),ANCHOR_TICKS=200,HOLD_TICKS=40,PREVIEW_INTERVAL=5,PULSE_INTERVAL=20,WARMUP_TICKS=6,
            MAX_PULSES=5,MAX_TARGETS = SkillTuning.integer("particle_laminate", "max_targets"),MAX_UNIQUE=10,SCAN_LIMIT=64;
    // One shared budget covers acquisition and every nested combat/native guard revalidation.
    public static final int MAX_PREFIXES=16384,MAX_ENTITY_VISITS=32768,MAX_SHAPES=65536,MAX_PERMISSION_CELLS=131072;
    public static final double RANGE = SkillTuning.number("particle_laminate", "reach"),RADIUS=.035;
    private static final ContinuousHighPowerGrowth.Curve PLACE=ContinuousHighPowerGrowth.curve("8","4","2",".5",".05");
    private static final ContinuousHighPowerGrowth.Curve PULSE=ContinuousHighPowerGrowth.curve("24","12","6","2",".1");
    private ParticleLaminateRules() {}
    public static BigDecimal theoreticalPlacementCost(BigDecimal q){return PLACE.value(q);}
    public static double placementCost(BigDecimal q){return theoreticalPlacementCost(q).doubleValue();}
    public static BigDecimal theoreticalPulseCost(BigDecimal q,boolean low){return PULSE.value(q).multiply(low?new BigDecimal(".32"):BigDecimal.ONE);}
    public static double pulseCost(BigDecimal q,boolean low){return theoreticalPulseCost(q,low).doubleValue();}
    public static BigDecimal damage(BigDecimal d,boolean low){return CombatAmounts.requirePositiveAmount(d,"NEW241 D").multiply(new BigDecimal(low?".8":"2.5")).min(CombatAmounts.CAP);}
    /** Fixed stable segment-order identities receive equal shares; last receives exact remainder. */
    public static List<BigDecimal> shares(BigDecimal total,int count){
        total=CombatAmounts.requireAmount(total,"NEW241 whole-pulse budget");
        if(count<0||count>MAX_TARGETS)throw new IllegalArgumentException("NEW241 target count");
        if(count==0)return List.of();
        BigDecimal each=total.divide(BigDecimal.valueOf(count),CombatAmounts.MAX_ABSOLUTE_SCALE,RoundingMode.DOWN);
        List<BigDecimal> result=new ArrayList<>(count);
        for(int i=0;i<count-1;i++)result.add(CombatAmounts.requireAmount(each,"NEW241 assigned share"));
        result.add(CombatAmounts.requireAmount(total.subtract(each.multiply(BigDecimal.valueOf(count-1))),"NEW241 remainder"));
        return List.copyOf(result);
    }
    public static boolean clock(long tick){return tick>=0&&tick<=Long.MAX_VALUE-ANCHOR_TICKS-1;}
    public static boolean expired(long start,long now,int duration){return duration<=0||!clock(start)||!clock(now)||now<start||now-start>=duration;}
    public static boolean ready(long previous,long now,int interval){return interval>0&&clock(now)&&(previous==-1||clock(previous)&&now>=previous&&now-previous>=interval);}
    public static int opportunity(long armed,long now){return expired(armed,now,FIELD_TICKS)||now-armed<WARMUP_TICKS?-1:(int)((now-armed-WARMUP_TICKS)/PULSE_INTERVAL);}
    public static int remaining(long anchor,long armed,long now){
        if(expired(anchor,now,ANCHOR_TICKS)||armed>=0&&expired(armed,now,FIELD_TICKS))return 0;
        long remaining=ANCHOR_TICKS-(now-anchor);
        return (int)(armed<0?remaining:Math.min(remaining,FIELD_TICKS-(now-armed)));
    }
    /** Failed work consumption permanently refuses the whole committed opportunity. */
    public static final class Work {
        private int prefixes,entities,shapes,cells;private boolean exhausted;
        public boolean prefix(){if(exhausted||prefixes>=MAX_PREFIXES){exhausted=true;return false;}prefixes++;return true;}
        public boolean entities(int count){if(exhausted||count<0||count>MAX_ENTITY_VISITS-entities){exhausted=true;return false;}entities+=count;return true;}
        public boolean shape(){if(exhausted||shapes>=MAX_SHAPES){exhausted=true;return false;}shapes++;return true;}
        public boolean cell(){if(exhausted||cells>=MAX_PERMISSION_CELLS){exhausted=true;return false;}cells++;return true;}
        public boolean valid(){return !exhausted;}
        public void refuse(){exhausted=true;}
        public int prefixes(){return prefixes;}
        public int entityVisits(){return entities;}
    }
}

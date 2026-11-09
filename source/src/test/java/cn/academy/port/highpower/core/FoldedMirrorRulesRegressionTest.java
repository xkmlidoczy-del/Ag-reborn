package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** Independent NEW221 balance anchors, continuous progression and global-budget boundaries. */
public final class FoldedMirrorRulesRegressionTest {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void near(double value,double expected,String why){check(Math.abs(value-expected)<1e-10,why+": "+value+" != "+expected);}
    public static void main(String[] args){
        double[] placement={2,1,.2,.02},shot={8,3,.5,.05},cooldown={30,15,5,1};
        for(int i=0;i<4;i++){
            BigDecimal q=BigDecimal.valueOf(i+2);
            near(FoldedMirrorRules.placementCost(q),placement[i],"approved placement anchor");
            near(FoldedMirrorRules.shotCost(q),shot[i],"approved whole-shot anchor");
            near(FoldedMirrorRules.theoreticalCooldown(q).doubleValue(),cooldown[i],"approved cooldown anchor");
        }
        double previousPlace=Double.POSITIVE_INFINITY,previousShot=Double.POSITIVE_INFINITY,previousCd=Double.POSITIVE_INFINITY;
        for(int i=0;i<=3000;i++){
            BigDecimal q=new BigDecimal("2").add(BigDecimal.valueOf(i,3));
            double place=FoldedMirrorRules.placementCost(q),cost=FoldedMirrorRules.shotCost(q),cd=FoldedMirrorRules.theoreticalCooldown(q).doubleValue();
            check(Double.isFinite(place)&&place>0&&place<=previousPlace,"finite monotone placement cost");
            check(Double.isFinite(cost)&&cost>0&&cost<=previousShot,"finite monotone shot cost");
            check(Double.isFinite(cd)&&cd>=1&&cd<=previousCd,"finite monotone continuous cooldown");
            int ticks=FoldedMirrorRules.cooldown(q);
            check(ticks>=cd-1e-10&&ticks<cd+1+1e-10,"only native tick quantization");
            previousPlace=place;previousShot=cost;previousCd=cd;
        }
        for(int level=3;level<=4;level++){
            BigDecimal q=BigDecimal.valueOf(level),epsilon=new BigDecimal("0.000001");
            check(Math.abs(FoldedMirrorRules.placementCost(q.subtract(epsilon))-FoldedMirrorRules.placementCost(q.add(epsilon)))<.0001,"no placement jump at native level");
            check(Math.abs(FoldedMirrorRules.shotCost(q.subtract(epsilon))-FoldedMirrorRules.shotCost(q.add(epsilon)))<.0001,"no shot jump at native level");
        }
        check(FoldedMirrorRules.damage(new BigDecimal("10000000")).compareTo(new BigDecimal("15000000"))==0,"one whole1.5D hit");
        check(FoldedMirrorRules.damage(new BigDecimal("5000000000000000000")).compareTo(new BigDecimal("7500000000000000000"))==0,"LV5 exact nominal budget");
        check(FoldedMirrorRules.damage(CombatAmounts.CAP).compareTo(CombatAmounts.CAP)==0,"global cap after coefficient");
        check(FoldedMirrorRules.LIFETIME==cn.academy.port.core.SkillTuning.number("folded_mirror_array", "duration_ticks")&&FoldedMirrorRules.HOLD_LIMIT==100&&FoldedMirrorRules.RECALL_TICKS==12,"fixed bounded physical windows");
        check(FoldedMirrorRules.RANGE==40&&FoldedMirrorRules.PLACE_RANGE==cn.academy.port.core.SkillTuning.number("folded_mirror_array", "placement_reach")&&FoldedMirrorRules.EXIT_RANGE==24,"separate physical range contracts");
        check(!FoldedMirrorRules.expired(100,499,400)&&FoldedMirrorRules.expired(100,500,400),"immutable exact expiry boundary");
        check(FoldedMirrorRules.expired(100,99,400)&&!FoldedMirrorRules.clock(Long.MAX_VALUE),"rollback and overflow fail closed");
        System.out.println("PASS "+checks+" NEW221 smooth costs, native tick quantization, conserved damage and lifetime checks");
    }
}

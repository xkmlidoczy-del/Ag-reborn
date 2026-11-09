/* Modern invalid-state contract preserved around source-exact finite capacity arithmetic. */
package cn.academy.port.core;
import cn.academy.port.skill.AdvancedMineRaySession;
import java.lang.reflect.Field;
import java.util.*;

public final class NumericCapacityInvalidStateRegressionTest {
    static long checks;
    static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
    static void bits(double actual,float expected,String reason){check(Double.doubleToRawLongBits(actual)==Double.doubleToRawLongBits((double)expected),reason+" actual="+actual+" expected="+expected);}
    static AbilityProgress ready(AdvancedMineRaySession.Tier tier){var s=new AbilityProgress();s.selectCategory("meltdowner");s.setLevel(tier.level);s.learn(tier.id);s.activated=true;return s;}
    static void assign(AbilityProgress s,String name,double value)throws Exception {Field field=AbilityProgress.class.getDeclaredField(name);field.setAccessible(true);field.setDouble(s,value);}
    static final class UntouchedWorld implements AdvancedMineRaySession.World {
        int calls;
        public AdvancedMineRaySession.Cell trace(){calls++;return new AdvancedMineRaySession.Cell(1,2,3);}
        public boolean denied(AdvancedMineRaySession.Cell c){calls++;return false;}
        public int harvestLevel(AdvancedMineRaySession.Cell c,AdvancedMineRaySession.Cell old){calls++;return 0;}
        public float hardness(AdvancedMineRaySession.Cell c){calls++;return 0;}
        public void breakBlock(AdvancedMineRaySession.Cell c,int fortune){calls++;}
        public void particles(AdvancedMineRaySession.Cell c){calls++;}
    }
    public static void main(String[] args)throws Exception {
        double[] bad={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-1,-Double.MIN_VALUE};
        for(var tier:AdvancedMineRaySession.Tier.values()) {
            for(String name:List.of("extraCp","extraOverload"))for(double value:bad){
                var s=ready(tier);double cp=s.cp,overload=s.overload;var events=new int[1];s.bindConsumption(()->SkillConsumption.Config.DEFAULT,e->events[0]++,()->{},()->true);assign(s,name,value);
                check(!Double.isFinite(name.equals("extraCp")?s.maxCp():s.maxOverload()),"invalid raw growth cannot become finite usable capacity: "+name+" "+value);
                check(AdvancedMineRaySession.begin(s,tier,false)==null,"normal mining rejects invalid growth: "+name);
                check(AdvancedMineRaySession.begin(s,tier,true)==null,"creative mining rejects invalid growth: "+name);
                check(s.cp==cp&&s.overload==overload&&events[0]==0,"invalid admission never posts cost or changes current resources");
                s.sanitize();check(Double.isFinite(s.maxCp())&&Double.isFinite(s.maxOverload()),"explicit sanitizer remains the repair path");
            }
            for(String name:List.of("extraCp","extraOverload","cp","overload"))for(double value:bad){
                var s=ready(tier);var session=AdvancedMineRaySession.begin(s,tier,false);check(session!=null,"valid original startup");assign(s,name,value);double cp=s.cp,overload=s.overload;var world=new UntouchedWorld();
                check(session.tick(false,world)&&session.ticks()==0&&world.calls==0,"poisoned active session ends before debit or world operation: "+name);
                check(Double.doubleToRawLongBits(s.cp)==Double.doubleToRawLongBits(cp)&&Double.doubleToRawLongBits(s.overload)==Double.doubleToRawLongBits(overload),"poisoned active session never heals or mutates current resources");
            }
            for(String name:List.of("calculatedCp","calculatedOverload"))for(double value:bad){
                var s=ready(tier);s.baseCp();s.baseOverload();assign(s,name,value);
                check(!Double.isFinite(name.equals("calculatedCp")?s.maxCp():s.maxOverload()),"invalid cached raw maximum is not masked");
                check(AdvancedMineRaySession.begin(s,tier,false)==null,"existing session guard rejects invalid cached maximum");
            }
        }
        var random=new Random(0x107CA9AC17L);var s=ready(AdvancedMineRaySession.Tier.LUCK);
        for(int i=0;i<20000;i++){
            float cp=random.nextFloat()*12000,overload=random.nextFloat()*500;s.extraCp=cp;s.extraOverload=overload;
            bits(s.maxCp(),8000f+cp,"ordinary capacity source float addition");bits(s.maxOverload(),500f+overload,"ordinary overload source float addition");
        }
        s.restoreCalculatedMaxima(Float.MAX_VALUE,Float.MAX_VALUE);s.extraCp=Float.MAX_VALUE;s.extraOverload=Float.MAX_VALUE;
        check(Float.isInfinite(Float.MAX_VALUE+Float.MAX_VALUE),"finite float addition overflow fixture");bits(s.maxCp(),Float.MAX_VALUE,"finite input overflow retains protected CP saturation");bits(s.maxOverload(),Float.MAX_VALUE,"finite input overflow retains protected overload saturation");
        s.extraCp=Double.MIN_VALUE;s.extraOverload=-0d;bits(s.maxCp(),Float.MAX_VALUE,"finite underflow input retains source rounding");bits(s.maxOverload(),Float.MAX_VALUE,"negative zero is a valid nonnegative operand");
        System.out.println("PASS "+checks+" invalid raw-capacity start/live-session guards, explicit repair and source finite-bit checks");
    }
}

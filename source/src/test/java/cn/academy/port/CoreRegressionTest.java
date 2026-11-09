package cn.academy.port;
import cn.academy.port.core.*;
public final class CoreRegressionTest {
    private static int assertions;
    private static void eq(double expected,double actual) { assertions++;if(Math.abs(expected-actual)>0.000001)throw new AssertionError(expected+" != "+actual); }
    private static void truth(boolean value) { assertions++;if(!value)throw new AssertionError("expected true"); }
    public static void main(String[] args) {
        for(int level=0;level<=5;level++) {var s=new AbilityProgress();s.selectCategory("electromaster");s.setLevel(level);eq(ClassicRules.BASE_CP[level],s.cp);eq(ClassicRules.BASE_OVERLOAD[level],s.maxOverload());}
        eq(30,ClassicRules.arc(0).cp());eq(70,ClassicRules.arc(1).cp());eq(5,ClassicRules.arc(0).damage());eq(9,ClassicRules.arc(1).damage());eq(15,ClassicRules.arc(0).cooldown());eq(5,ClassicRules.arc(1).cooldown());
        eq(200,ClassicRules.railgun(0).cp());eq(450,ClassicRules.railgun(1).cp());eq(180,ClassicRules.railgun(0).overload());eq(120,ClassicRules.railgun(1).overload());eq(300,ClassicRules.railgun(0).cooldown());eq(160,ClassicRules.railgun(1).cooldown());
        eq(0,ClassicRules.handFrame(0));eq(1,ClassicRules.handFrame(40));eq(39,ClassicRules.handFrame(1599));eq(20,ClassicRules.railgunChargeTicks());
        var s=new AbilityProgress();s.selectCategory("electromaster");s.setLevel(1);s.experience.put("arc_gen",0.0);s.activated=true;
        truth(s.canUse("arc_gen"));truth(s.consume(30,18,false));eq(1770,s.cp);eq(.075,s.extraCp);eq(.1044,s.extraOverload);eq(15,s.cpDelay);eq(32,s.overloadDelay);
        for(int i=0;i<15;i++)s.tick();eq(1770,s.cp);s.tick();truth(s.cp>1770);for(int i=16;i<32;i++)s.tick();eq(18,s.overload);s.tick();truth(s.overload<18);
        s.cp=0;double before=s.overload;truth(!s.consume(1,5,false));eq(before,s.overload);truth(s.consume(30,18,true));eq(0,s.cp);eq(before,s.overload);
        s.cp=1800;s.overload=s.maxOverload()-1;truth(s.consume(30,18,false));truth(!s.overloadFine);truth(!s.canUse("arc_gen"));for(int i=0;i<500;i++)s.tick();truth(s.overloadFine);
        s.addExperience("arc_gen",2);eq(1,s.exp("arc_gen"));eq(2,s.levelExperience);truth(s.canLevelUp(1));s.setLevel(2);eq(0,s.levelExperience);eq(0,s.extraCp);eq(2800,s.cp);
        s.cp=Double.NaN;s.overload=Double.POSITIVE_INFINITY;s.level=99;s.sanitize();eq(5,s.level);eq(0,s.cp);eq(0,s.overload);truth(!s.consume(Double.NaN,1,false));
        eq(1.62,ClassicRules.cpRecovery(3600,1800));
        s.setLevel(1);s.extraCp=10;s.cp=1;s.overload=55;s.learn("charging");eq(1810,s.cp);eq(0,s.overload);eq(10,s.extraCp);
        s.setLevel(5);s.learn("brain_course");eq(9000,s.maxCp());s.learn("brain_course_advanced");eq(10500,s.maxCp());eq(600,s.maxOverload());s.learn("mind_course");s.cp=0;s.cpDelay=0;s.tick();eq(3.78,s.cp);
        var radiation=new AbilityProgress();radiation.selectCategory("meltdowner");radiation.setLevel(1);eq(.225,radiation.exp("rad_intensify"));radiation.extraCp=900;eq(.3375,radiation.exp("rad_intensify"));radiation.setLevel(5);radiation.extraCp=12000;eq(1,radiation.exp("rad_intensify"));
        System.out.println("PASS "+assertions+" deterministic classic-rule assertions");
    }
}

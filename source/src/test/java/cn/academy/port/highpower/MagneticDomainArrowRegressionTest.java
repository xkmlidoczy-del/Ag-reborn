package cn.academy.port.highpower;

import cn.academy.port.highpower.core.MagneticDomainArrowRules;
import cn.academy.port.highpower.core.MagneticDomainClock;
import cn.academy.port.highpower.core.MagneticDomainArrowRules.Vector;
import cn.academy.port.highpower.core.MagneticDomainArrowRules.Admission;
import java.util.*;

/** Pure directional and bounded-native-speed proof for NEW152, without a Minecraft classloader. */
public final class MagneticDomainArrowRegressionTest {
    private static int checks;
    private static void check(boolean pass,String why){checks++;if(!pass)throw new AssertionError(why);}
    private static void near(double a,double b,String why){check(Math.abs(a-b)<1e-10,why+": "+a+" vs "+b);}
    public static void main(String[] args){
        MagneticDomainClock firstBoot=new MagneticDomainClock(450,0);
        check(firstBoot.at(20)==470,"twenty independent server ticks, regardless of world phase");
        long persistedPaidUntil=firstBoot.at(10)+20;
        MagneticDomainClock coldRestart=new MagneticDomainClock(473,0);
        check(coldRestart.at(0)<persistedPaidUntil&&coldRestart.at(6)<persistedPaidUntil,"cold process zero tickcount retains remaining saved paid fence");
        check(coldRestart.at(7)==persistedPaidUntil,"cold process fence expires only after remaining seven real ticks");
        MagneticDomainClock restoredAtOtherCount=new MagneticDomainClock(473,9999);
        check(restoredAtOtherCount.at(10006)==persistedPaidUntil,"raw process counter identity never enters persistent fence");
        check(firstBoot.at(-1)==-1,"backwards process clock fails closed");
        Vector zero=new Vector(0,0,0),forward=new Vector(0,0,1),shooter=new Vector(0,0,15);
        check(MagneticDomainArrowRules.inCorridor(new Vector(0,0,12),zero,forward),"sphere boundary admitted");
        check(!MagneticDomainArrowRules.inCorridor(new Vector(0,0,12.00001),zero,forward),"outside sphere refused");
        check(!MagneticDomainArrowRules.inCorridor(new Vector(0,0,-2),zero,forward),"rear refused");
        check(!MagneticDomainArrowRules.inCorridor(new Vector(10,0,1),zero,forward),"outside120cone refused");
        check(MagneticDomainArrowRules.inCorridor(new Vector(Math.sqrt(3)*3-1e-9,0,3),zero,forward),"inside60degreehalfangle admitted");
        check(!MagneticDomainArrowRules.inCorridor(new Vector(Math.sqrt(3)*3+1e-9,0,3),zero,forward),"outside60degreehalfangle refused");
        for(int i=0;i<1000;i++){
            double speed=.031+(i%97)*.025,y=(i%19-9)*.013;
            Vector original=new Vector(0,y,-speed),velocity=original,at=new Vector(0,0,8);double accumulated=0;int side=0;
            for(int tick=0;tick<20;tick++){
                var turn=MagneticDomainArrowRules.turn(velocity,at,zero,forward,shooter,side,accumulated,new UUID(0,i));
                if(turn==null)break;
                check(turn.angle()>0&&turn.angle()<=.1+1e-12,"bounded positive tick rotation");
                check(Double.doubleToRawLongBits(turn.velocity().y())==Double.doubleToRawLongBits(original.y()),"Y exact");
                check(turn.velocity().lengthSquared()<=velocity.lengthSquared(),"native speed never increases even at ceil boundary");
                near(Math.hypot(turn.velocity().x(),turn.velocity().z()),Math.hypot(original.x(),original.z()),"speed preserved except rounding ULP");
                check(turn.velocity().dot(shooter.subtract(at))<=1e-10,"never return toward shooter");
                accumulated+=turn.angle();side=turn.side();velocity=turn.velocity();
            }
            near(accumulated,Math.PI/4,"exact45degree cumulative budget");
            check(MagneticDomainArrowRules.turn(velocity,at,zero,forward,shooter,side,accumulated,new UUID(0,i))==null,"exhausted budget cannot curve again");
        }
        var a=new Admission(new UUID(0,1),100L);var b=new Admission(new UUID(0,2),105L);
        check(MagneticDomainArrowRules.window(List.of(a,b),105,119).size()==2,"rolling20tick inclusive start");
        check(MagneticDomainArrowRules.window(List.of(a,b),105,120).size()==1,"old entry expires at20ticks");
        check(MagneticDomainArrowRules.window(List.of(a,a),100,100)==null,"duplicate UUID ledger refused");
        check(MagneticDomainArrowRules.window(List.of(b,a),105,105)==null,"unordered ledger refused");
        check(MagneticDomainArrowRules.window(List.of(a),101,100)==null,"backwards clock refused");
        check(MagneticDomainArrowRules.turn(new Vector(0,0,.2),new Vector(0,0,8),zero,forward,shooter,0,0,new UUID(0,1))==null,"receding arrow refused");
        System.out.println("MagneticDomainArrowRegressionTest: "+checks+" checks passed");
    }
}

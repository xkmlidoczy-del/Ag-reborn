package cn.academy.port.highpower;

import cn.academy.port.highpower.core.DualPointRules;
import cn.academy.port.highpower.core.DualPointRules.Point;
import java.math.BigDecimal;

/** Decimal allocation, bounded geometry and clocks independent of Minecraft runtime. */
public final class DualPointRegressionTest {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void fails(Runnable test){try{test.run();throw new AssertionError("Expected invalid input rejection");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        String[] d={"1e7","1e10","1e13","1e16","5e18","99999999999999999999.999",".0000000000001"};
        for(String value:d){
            BigDecimal input=new BigDecimal(value),one=DualPointRules.damage(input);
            check(one.compareTo(input.multiply(new BigDecimal(".65")))==0,"each independently frozen allocation is exactly .65D");
            check(one.add(one).compareTo(input.multiply(new BigDecimal("1.3")))==0,"two nominal opportunities never exceed 1.3D");
        }
        double[] cp={10,5,2,.5,.1};
        for(int i=0;i<cp.length;i++){
            check(DualPointRules.cost(BigDecimal.valueOf(i+1),1)==cp[i],"approved per-beam CP anchor");
            check(DualPointRules.cost(BigDecimal.valueOf(i+1),2)==2*cp[i],"one commit pays exactly its two admitted slots");
        }
        double previous=10;
        for(int i=1;i<=400;i++){
            double next=DualPointRules.cost(BigDecimal.ONE.add(BigDecimal.valueOf(i,2)),1);
            check(next<previous&&next>=.1,"continuous decreasing CP between anchors");previous=next;
        }
        check(DualPointRules.cooldown(1)==4&&DualPointRules.cooldown(4)==4&&DualPointRules.cooldown(27)==27,"minimum four engine ticks and current arc cooldown");
        fails(()->DualPointRules.cost(BigDecimal.ONE,0));fails(()->DualPointRules.cost(BigDecimal.ONE,3));
        fails(()->DualPointRules.damage(BigDecimal.ZERO));fails(()->DualPointRules.damage(new BigDecimal("1e21")));
        check(!DualPointRules.expired(10,9+DualPointRules.MARK_TICKS)&&DualPointRules.expired(10,10+DualPointRules.MARK_TICKS),"one immutable mark deadline");
        check(DualPointRules.expired(10,9)&&DualPointRules.expired(-1,1)&&DualPointRules.expired(1,Long.MAX_VALUE),"rollback and overflow fail closed");
        check(!DualPointRules.markReady(10,13)&&DualPointRules.markReady(10,14)&&!DualPointRules.markReady(10,9),"minimum four-tick cadence");
        check(DualPointRules.SCAN_LIMIT==64&&DualPointRules.EMISSIONS==2&&DualPointRules.RANGE==cn.academy.port.core.SkillTuning.number("dual_point", "reach"),"finite admission and geometry caps");
        Point eye=new Point(0,1.62,0),look=new Point(0,0,1);
        Point left=DualPointRules.origin(eye,look,0),right=DualPointRules.origin(eye,look,1);
        check(left.x()==-.32&&right.x()==.32&&left.y()==1.34&&left.z()==.35,"real distinct hand origins");
        check(Math.abs(Math.sqrt(left.distanceSquared(right))-.64)<1e-12,"focusing points are spatially distinct");
        check(DualPointRules.within(eye,new Point(0,1.62,DualPointRules.RANGE))&&!DualPointRules.within(eye,new Point(0,1.62,Math.nextUp(DualPointRules.RANGE))),"hard configured boundary without tolerance extension");
        for(int yaw=-180;yaw<=180;yaw++)for(int pitch=-90;pitch<=90;pitch+=15){
            double y=Math.toRadians(yaw),x=Math.toRadians(pitch);Point axis=new Point(-Math.sin(y)*Math.cos(x),-Math.sin(x),Math.cos(y)*Math.cos(x));
            Point a=DualPointRules.origin(eye,axis,0),b=DualPointRules.origin(eye,axis,1);
            check(a!=null&&b!=null&&a.finite()&&b.finite()&&a.distanceSquared(eye)<1&&b.distanceSquared(eye)<1,"vertical and oblique origins stay beside caster");
            check(Math.abs(a.distanceSquared(b)-.4096)<1e-12,"two distinct origins at every angle");
        }
        check(DualPointRules.origin(eye,new Point(0,0,0),0)==null&&DualPointRules.origin(eye,new Point(Double.NaN,0,0),1)==null,"nonfinite and degenerate geometry refused");
        check(DualPointRules.origin(eye,look,2)==null&&!DualPointRules.within(eye,new Point(Double.POSITIVE_INFINITY,0,0)),"unbounded slot and endpoint rejected");
        int rawOvershoots=0;
        for(double coordinate:new double[]{0,-13497555.5,8902083.5,-10460878.5,29999900,-29999900})for(int yaw=-180;yaw<=180;yaw++)for(int pitch:new int[]{-89,-45,0,17,75,89}){
            double y=Math.toRadians(yaw),x=Math.toRadians(pitch);Point axis=new Point(-Math.sin(y)*Math.cos(x),-Math.sin(x),Math.cos(y)*Math.cos(x));
            double length=Math.sqrt(axis.distanceSquared(new Point(0,0,0)));axis=new Point(axis.x()/length,axis.y()/length,axis.z()/length);
            Point start=new Point(coordinate,-42.38,-coordinate),end=DualPointRules.endpoint(start,axis);
            if(!DualPointRules.within(start,new Point(start.x()+axis.x()*DualPointRules.RANGE,start.y()+axis.y()*DualPointRules.RANGE,start.z()+axis.z()*DualPointRules.RANGE)))rawOvershoots++;
            check(end!=null&&DualPointRules.within(start,end),"large-coordinate diagonal endpoints stay strictly within 28 m without tolerance widening");
        }
        check(rawOvershoots>0,"negative control reproduces outward rounding in the former unbounded endpoint calculation");
        check(DualPointRules.endpoint(eye,new Point(Double.NaN,0,1))==null&&DualPointRules.endpoint(eye,new Point(0,0,2))==null,"invalid endpoint direction rejected");
        System.out.println("DualPointRegressionTest: "+checks+" checks passed");
    }
}

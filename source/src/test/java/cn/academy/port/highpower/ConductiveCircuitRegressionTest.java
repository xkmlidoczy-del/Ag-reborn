package cn.academy.port.highpower;

import cn.academy.port.highpower.core.ConductiveCircuitRules;
import cn.academy.port.highpower.core.ConductiveCircuitRules.Point;

/** Pure actual-trajectory/finite-ground/persistent-interval boundary coverage. */
public final class ConductiveCircuitRegressionTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Point p(double x,double y,double z){return new Point(x,y,z);}
    public static void main(String[] args){
        Point a=p(0,1,0),b=p(0,1,10);
        Point bottomRay=ConductiveCircuitRules.rayEndWithinHeight(p(0,-55.38,0),p(0,-70.49,18.65),-64,320);
        check(bottomRay!=null&&bottomRay.y()==-64&&bottomRay.z()>0&&bottomRay.z()<18.65,"ray clips at minbuild rather than rejecting valid earlier ground");
        Point upperRay=ConductiveCircuitRules.rayEndWithinHeight(p(0,315,0),p(0,330,10),-64,320);
        check(upperRay!=null&&upperRay.y()<320&&upperRay.y()>319.99,"maxbuild boundary remains exclusive");
        Point normalRay=ConductiveCircuitRules.rayEndWithinHeight(p(0,64,0),p(0,48,16),-64,320);
        check(normalRay.equals(p(0,48,16)),"ordinary ray unchanged");
        check(ConductiveCircuitRules.rayEndWithinHeight(p(0,-65,0),p(0,0,1),-64,320)==null,"outside-world origin refused");
        check(ConductiveCircuitRules.segmentValid(a,b),"normal segment");
        check(ConductiveCircuitRules.segmentValid(a,p(ConductiveCircuitRules.MAX_LENGTH,1,0)),"exact configured length admitted");
        check(!ConductiveCircuitRules.segmentValid(a,p(Math.nextUp(ConductiveCircuitRules.MAX_LENGTH),1,0)),"outside the configured length refused");
        check(!ConductiveCircuitRules.segmentValid(a,p(.49,1,0)),"degenerate pair refused");
        check(!ConductiveCircuitRules.segmentValid(a,p(0,1.5,10)),"step is not invented flat terrain");
        check(!ConductiveCircuitRules.segmentValid(a,p(Double.NaN,1,10)),"nan refused");
        check(cross(a,b,p(-.5,1,5),p(.5,1,5),1,2),"left right crossing");
        check(cross(a,b,p(.5,1,5),p(-.5,1,5),1,2),"right left crossing");
        check(!cross(a,b,p(.5,1,5),p(.5,1,5),1,2),"stationary beside line");
        check(!cross(a,b,p(0,1,5),p(0,1,5),1,2),"stationary on line");
        check(!cross(a,b,p(0,1,4),p(0,1,6),1,2),"sliding along line");
        check(!cross(a,b,p(-.5,1,5),p(-.1,1,5),1,2),"nearby same side");
        check(cross(a,b,p(-.5,1,5),p(0,1,5),1,2),"arrival at line counts once");
        check(!cross(a,b,p(0,1,5),p(.5,1,5),2,3),"leaving after arrival cannot doublehit");
        check(!cross(a,b,p(-.5,1,11),p(.5,1,11),1,2),"infinite extension is no circuit");
        check(!cross(a,b,p(-.5,1.2,5),p(.5,1.2,5),1,2),"jump over line");
        check(!cross(a,b,p(-1.001,1,5),p(1.001,1,5),1,2),"unreviewed highspeed step refused");
        check(cross(a,b,p(-1,1,5),p(1,1,5),1,2),"bounded2m crossing retained");
        check(!cross(a,b,p(-.5,1,5),p(.5,1,5),1,1),"same tick replay");
        check(!cross(a,b,p(-.5,1,5),p(.5,1,5),1,3),"missing sample cannot bridge motion");
        check(!cross(a,b,p(-.5,1,5),p(.5,1,5),2,1),"clock rollback");
        for(int angle=0;angle<360;angle++){
            double theta=Math.toRadians(angle),dx=Math.cos(theta),dz=Math.sin(theta);
            Point start=p(100,64,-100),end=p(100+dx*20,64,-100+dz*20);
            for(int index=1;index<20;index++){
                double x=100+dx*index,z=-100+dz*index;
                check(cross(start,end,p(x-dz*.4,64,z+dx*.4),p(x+dz*.4,64,z-dx*.4),40,41),"rotation-invariant crossing");
                check(!cross(start,end,p(x-dz*.4,64,z+dx*.4),p(x-dz*.3,64,z+dx*.3),40,41),"rotation-invariant same side");
            }
        }
        Point diagonalStart=p(.5,2,.5),diagonalEnd=p(2.5,2,2.5);
        check(ConductiveCircuitRules.intersectsCell(diagonalStart,diagonalEnd,0,0,.05),"first support");
        check(ConductiveCircuitRules.intersectsCell(diagonalStart,diagonalEnd,1,0,.05),"corner-touch neighboring support must exist");
        check(ConductiveCircuitRules.intersectsCell(diagonalStart,diagonalEnd,0,1,.05),"other corner-touch support must exist");
        check(ConductiveCircuitRules.intersectsCell(diagonalStart,diagonalEnd,1,1,.05),"middle support");
        check(!ConductiveCircuitRules.intersectsCell(diagonalStart,diagonalEnd,0,2,.05),"outside diagonal corridor");
        check(ConductiveCircuitRules.intervalReady(false,0,0),"initial tick zero is not previously hit");
        check(!ConductiveCircuitRules.intervalReady(true,100,109),"nine ticks fenced");
        check(ConductiveCircuitRules.intervalReady(true,100,110),"ten ticks allowed");
        check(!ConductiveCircuitRules.intervalReady(true,100,99),"persisted future clock never resets fence");
        check(!ConductiveCircuitRules.intervalReady(true,-1,100),"negative persisted timestamp is not absence");
        check(!ConductiveCircuitRules.intervalReady(false,0,-1),"negative current clock is invalid");
        check(!ConductiveCircuitRules.expired(100,299),"lifetime199");
        check(ConductiveCircuitRules.expired(100,300),"lifetime200");
        check(ConductiveCircuitRules.expired(100,99),"backward lifetime clock");
        check(Double.isNaN(ConductiveCircuitRules.cpCost(1)),"notLV1");
        check(ConductiveCircuitRules.cpCost(2)==5&&ConductiveCircuitRules.cpCost(5)==.1,"bounded pulse cost");
        System.out.println("ConductiveCircuitRegressionTest: "+checks+" checks passed");
    }
    private static boolean cross(Point a,Point b,Point before,Point after,long old,long now){return ConductiveCircuitRules.crosses(a,b,before,after,old,now);}
}

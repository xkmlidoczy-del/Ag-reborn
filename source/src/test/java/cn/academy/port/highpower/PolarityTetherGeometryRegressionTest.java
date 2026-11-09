package cn.academy.port.highpower;

import cn.academy.port.client.PolarityTetherGeometry;
import cn.academy.port.client.PolarityTetherGeometry.Point;
import cn.academy.port.client.PolarityTetherGeometry.Segment;
import java.util.Random;

/** Reproduces the old eye-plane flare and bounds the actual pure helper used by the compiled renderer. */
public final class PolarityTetherGeometryRegressionTest {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static Point unit(Point p){return p.scale(1/p.length());}
    private static void checkSegment(Segment segment,Point forward,double requestedWidth){
        check(segment!=null,"visible segment retained");
        check(segment.start().finite()&&segment.end().finite()&&Double.isFinite(segment.halfWidth()),"finite clipped endpoints and width");
        check(segment.halfWidth()>0&&segment.halfWidth()<=requestedWidth,"near clip never expands ribbon width");
        double nearest=Math.min(segment.start().dot(forward),segment.end().dot(forward));
        check(nearest-segment.halfWidth()>=PolarityTetherGeometry.NEAR_DEPTH-1e-12,"all billboard vertices conservatively in front of near plane");
        check(segment.halfWidth()/nearest<=PolarityTetherGeometry.MAX_ANGULAR_HALF_WIDTH+1e-12,"bounded projected ribbon half-width even when looking away");
    }
    public static void main(String[] args){
        Point forward=new Point(0,0,1),up=new Point(0,1,0),end=new Point(0,3,12);
        Point oldOrigin=new Point(0,-.18,0);
        check(oldOrigin.dot(forward)==0,"old first-person fixture begins on camera plane");
        Point right=PolarityTetherGeometry.wrist(forward,up,false),left=PolarityTetherGeometry.wrist(forward,up,true);
        check(Math.abs(right.dot(forward)-.45)<1e-12&&Math.abs(left.dot(forward)-.45)<1e-12,"both hands move origin safely forward");
        check(Math.abs(right.y()+.20)<1e-12&&Math.abs(right.x()+left.x())<1e-12,"camera-down and handed offset");
        check(right.length()<.52&&left.length()<.52,"wrist remains within bounded local envelope");
        checkSegment(PolarityTetherGeometry.clip(right,end,forward,.013),forward,.013);
        checkSegment(PolarityTetherGeometry.clip(oldOrigin,end,forward,.013),forward,.013);
        checkSegment(PolarityTetherGeometry.clip(end,oldOrigin,forward,.03),forward,.03);
        check(PolarityTetherGeometry.clip(new Point(0,0,-2),new Point(0,0,.01),forward,.013)==null,"fully near/behind-camera ribbon suppressed");
        check(PolarityTetherGeometry.wrist(forward,forward,false)==null,"degenerate camera basis never emits invalid geometry");
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){
            check(PolarityTetherGeometry.wrist(new Point(bad,0,0),up,false)==null,"nonfinite camera rejected");
            check(PolarityTetherGeometry.clip(new Point(0,bad,0),end,forward,.013)==null,"nonfinite endpoint rejected");
            check(PolarityTetherGeometry.clip(right,end,forward,bad)==null,"nonfinite width rejected");
        }
        Random random=new Random(11306);
        for(int i=0;i<6000;i++){
            // Explicit exact and near-vertical camera frames, plus arbitrary camera rotations.
            double pitch=i%3==0?Math.PI/2:(i%3==1?-Math.PI/2+1e-10:(random.nextDouble()-.5)*Math.PI);
            double yaw=random.nextDouble()*Math.PI*2;
            Point f=new Point(Math.cos(pitch)*Math.sin(yaw),Math.sin(pitch),Math.cos(pitch)*Math.cos(yaw));
            Point u=new Point(-Math.sin(pitch)*Math.sin(yaw),Math.cos(pitch),-Math.sin(pitch)*Math.cos(yaw));
            Point wrist=PolarityTetherGeometry.wrist(f,u,i%2==0);
            check(wrist!=null&&wrist.finite(),"finite wrist at arbitrary/vertical camera angle");
            check(Math.abs(wrist.dot(f)-.45)<1e-9&&wrist.length()<.52,"wrist depth and envelope unaffected by camera rotation");
            Point target=f.scale(.25+random.nextDouble()*15).plus(u.scale((random.nextDouble()-.5)*2));
            double width=.005+random.nextDouble()*.025;
            checkSegment(PolarityTetherGeometry.clip(wrist,target,f,width),f,width);
            Point behind=f.scale(-random.nextDouble()).plus(u.scale(.05));
            checkSegment(PolarityTetherGeometry.clip(behind,target,f,width),f,width);
        }
        System.out.println("PASS "+checks+" NEW-113 first-person wrist, near-plane and bounded-width geometry checks");
    }
}

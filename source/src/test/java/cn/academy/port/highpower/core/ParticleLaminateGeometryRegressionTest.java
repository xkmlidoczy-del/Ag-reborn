package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.FoldedMirrorGeometry.Bounds;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import static cn.academy.port.highpower.core.ParticleLaminateGeometry.*;

/** Standalone checks of exact three-ray geometry, gaps, full slabs and closed native contacts. */
public final class ParticleLaminateGeometryRegressionTest {
    private static int checks;
    public static void main(String[] args){
        Panel first=new Panel(0,v(0,10,0),v(0,0,1)),second=new Panel(1,v(0,10,12),v(0,0,1));
        var rays=segments(first,second).orElseThrow();check(rays.size()==3,"exactly three segments");
        check(rays.stream().map(Segment::start).distinct().count()==3,"three distinct true emitters");
        check(rays.stream().map(Segment::end).distinct().count()==3,"three distinct terminal panel points");
        for(var ray:rays){
            Vector middle=ray.start().add(ray.end()).scale(.5);near(middle,v(0,10,6),"crossed actual center");
            check(clip(b(middle,.01),ray.start(),ray.end(),RADIUS).isPresent(),"real crossed contact");
            check(clip(b(v(.4,10.7,6),.05),ray.start(),ray.end(),RADIUS).isEmpty(),"corridor AABB gap is never damage");
            check(clip(new Bounds(v(-2,8,5),v(2,12,5)),ray.start(),ray.end(),RADIUS).isPresent(),"wall across true ray clips");
            check(clip(b(ray.start(),.01),ray.start(),ray.end(),RADIUS).orElseThrow().equals(ray.start()),"body containing real emitter contacts at zero");
        }
        check(segments(first,new Panel(1,v(0,10,1.999),v(0,0,1))).isEmpty(),"under two meters refused");
        check(segments(first,new Panel(1,v(0,10,2),v(0,0,1))).isPresent(),"closed minimum two meters");
        check(segments(first,new Panel(1,v(0,10,64.001),v(0,0,1))).isEmpty(),"over 64m centers refused");
        check(segments(first,new Panel(1,v(0,10,64),v(0,0,1))).isEmpty(),"64m center cannot silently widen diagonal beam permit");
        check(segments(first,new Panel(1,v(0,10,63.9),v(0,0,1))).isPresent(),"long legal corridor admitted");
        for(double pitch:new double[]{-Math.PI/2,-.8,0,.7,Math.PI/2})for(double yaw:new double[]{0,.4,2.7}){
            Vector normal=v(Math.sin(yaw)*Math.cos(pitch),Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch));Panel panel=new Panel(0,v(100,90,-70),normal);var frame=frame(panel);var volume=volumeBounds(panel);
            check(Math.abs(frame.right().dot(frame.up()))<1e-10,"stable orthogonal visual frame");
            for(double x:new double[]{-.6,.6})for(double y:new double[]{-1,1})for(double z:new double[]{-.02,.02}){
                Vector point=point(panel,x,y).add(normal.scale(z));check(inside(point,volume),"full oriented thin panel corners enclosed");
            }
        }
        Vector start=v(0,0,0),end=v(0,0,10);Bounds terminal=new Bounds(v(-1,-1,10),v(1,1,11));
        check(clip(terminal,start,end,0).orElseThrow().equals(end),"terminal solid/body tie remains a closed contact");
        check(clip(new Bounds(v(.035,0,5),v(.035,0,5)),start,end,RADIUS).isPresent(),"closed radius tangent");
        check(clip(new Bounds(v(Math.nextUp(.035),0,5),v(Math.nextUp(.035),0,5)),start,end,RADIUS).isEmpty(),"no epsilon widening");
        reject(()->new Panel(0,start,v(0,0,0)),"zero normal");reject(()->new Panel(2,start,v(0,0,1)),"third panel");reject(()->new Segment(start,v(0,0,Math.nextUp(64))),"closed segment hard range");
        System.out.println("Particle laminate geometry: "+checks+" checks passed");
    }
    private static boolean inside(Vector p,Bounds b){return p.x()>=b.min().x()-1e-12&&p.x()<=b.max().x()+1e-12&&p.y()>=b.min().y()-1e-12&&p.y()<=b.max().y()+1e-12&&p.z()>=b.min().z()-1e-12&&p.z()<=b.max().z()+1e-12;}
    private static Vector v(double x,double y,double z){return new Vector(x,y,z);}
    private static Bounds b(Vector center,double radius){Vector e=v(radius,radius,radius);return new Bounds(center.subtract(e),center.add(e));}
    private static void near(Vector a,Vector b,String why){check(a.subtract(b).length()<1e-10,why);}
    private static void reject(Runnable call,String why){try{call.run();throw new AssertionError(why);}catch(IllegalArgumentException expected){checks++;}}
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
}

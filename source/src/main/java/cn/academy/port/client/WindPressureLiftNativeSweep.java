package cn.academy.port.client;

import cn.academy.port.highpower.core.WindPressureLiftRules.Velocity;
import cn.academy.port.highpower.core.WindPressureLiftGeometry.Box;
import cn.academy.port.highpower.core.WindPressureLiftGeometry.Point;
import java.util.Objects;

/** Validates a read-only native collision result. The result is geometry only, never written as velocity. */
public final class WindPressureLiftNativeSweep {
    private WindPressureLiftNativeSweep() {}
    public record Boundary(Object world,Object player,Object connection,long generation,Object input,Object pose,
                           Point position,Velocity velocity,Box actualBody,Box standingBody,float yaw,float speed,
                           boolean grounded,float forward,float left,boolean jumping,boolean shift) {}
    /** Native Entity.moveRelative projection from the actual travel argument, already scaled by aiStep. */
    public static Velocity project(Velocity current,Velocity travel,float speed,float sin,float cos) {
        if(current==null || travel==null || !current.finite() || !travel.finite() || !Float.isFinite(speed)
                || speed<0 || speed>.5F || !Float.isFinite(sin) || !Float.isFinite(cos) || Math.abs(sin)>1 || Math.abs(cos)>1)return null;
        double length=travel.x()*travel.x()+travel.y()*travel.y()+travel.z()*travel.z();
        if(!Double.isFinite(length))return null;
        double x=0,y=0,z=0;
        if(length>=1e-7) {
            double scale=length>1?Math.sqrt(length):1;
            x=(travel.x()/scale)*speed;y=(travel.y()/scale)*speed;z=(travel.z()/scale)*speed;
        }
        var result=new Velocity(current.x()+(x*cos-z*sin),current.y()+y,current.z()+(z*cos+x*sin));
        return result.finite()?result:null;
    }
    /** Collision callbacks may alter native state. Never overwrite any such change with a stale proposal. */
    public static boolean unchanged(Boundary a,Boundary b) {
        return a!=null && b!=null && a.world==b.world && a.player==b.player && a.connection==b.connection
                && a.generation==b.generation && a.input==b.input && a.pose==b.pose
                && Objects.equals(a.position,b.position) && Objects.equals(a.velocity,b.velocity)
                && Objects.equals(a.actualBody,b.actualBody) && Objects.equals(a.standingBody,b.standingBody)
                && Float.floatToIntBits(a.yaw)==Float.floatToIntBits(b.yaw) && Float.floatToIntBits(a.speed)==Float.floatToIntBits(b.speed)
                && a.grounded==b.grounded && Float.floatToIntBits(a.forward)==Float.floatToIntBits(b.forward)
                && Float.floatToIntBits(a.left)==Float.floatToIntBits(b.left) && a.jumping==b.jumping && a.shift==b.shift;
    }
    public static Velocity accepted(Velocity requested, Velocity collided, boolean wasAirborne) {
        if (requested == null || collided == null || !requested.finite() || !collided.finite()
                || requested.x() != collided.x() || requested.z() != collided.z()) return null;
        if (requested.y() == collided.y()) return collided;
        // Native ground clipping may remove some downward displacement. No ceiling clip, upward correction,
        // step-up, sideways wall slide, floor penetration, or second landing is an assist permission.
        boolean floor = requested.y() < 0 && collided.y() > requested.y() && collided.y() <= 0;
        return floor && !wasAirborne ? collided : null;
    }
}

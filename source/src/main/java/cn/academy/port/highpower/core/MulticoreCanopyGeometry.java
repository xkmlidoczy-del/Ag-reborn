package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.FoldedMirrorGeometry.Bounds;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Six frozen eye-relative core centers, closed body contacts and a finite forward cone. */
public final class MulticoreCanopyGeometry {
    public record Core(int id,Vector center){public Core{if(id<0||id>=6||center==null||!center.finite())throw new IllegalArgumentException("NEW251 core");}}
    private MulticoreCanopyGeometry() {}
    public static List<Core> cores(Vector eye,Vector forward){
        if(eye==null||!eye.finite()||forward==null||!forward.finite()||Math.abs(forward.length()-1)>1e-10)throw new IllegalArgumentException("NEW251 initial pose");
        double horizontal=Math.hypot(forward.x(),forward.z());
        Vector right=horizontal>0?new Vector(forward.z()/horizontal,0,-forward.x()/horizontal):new Vector(1,0,0),up=forward.cross(right);
        double[][] offsets={{-.9,0},{-.45,.65},{.45,.65},{.9,0},{.45,-.65},{-.45,-.65}};
        List<Core> result=new ArrayList<>(6);
        for(int i=0;i<6;i++)result.add(new Core(i,eye.add(forward.scale(2)).add(right.scale(offsets[i][0])).add(up.scale(offsets[i][1]))));
        return List.copyOf(result);
    }
    public static boolean inCone(Vector eye,Vector forward,Vector point){
        if(eye==null||forward==null||point==null||!eye.finite()||!forward.finite()||!point.finite()||Math.abs(forward.length()-1)>1e-10)return false;
        Vector delta=point.subtract(eye);double distance=delta.length();
        return distance>0&&distance<=MulticoreCanopyRules.RANGE&&delta.dot(forward)>=distance*Math.cos(Math.toRadians(MulticoreCanopyRules.CONE_DEGREES));
    }
    public static boolean pathWithinRange(Vector eye,Vector core,Vector hit){
        return eye!=null&&core!=null&&hit!=null&&eye.finite()&&core.finite()&&hit.finite()
                &&eye.subtract(core).length()+core.subtract(hit).length()<=MulticoreCanopyRules.RANGE;
    }
    public static Optional<Vector> clip(Bounds bounds,Vector start,Vector end,double radius){
        if(bounds==null||!bounds.min().finite()||!bounds.max().finite()||start==null||end==null||!start.finite()||!end.finite()
                ||!Double.isFinite(radius)||radius<0||radius>MulticoreCanopyRules.CORE_RADIUS)return Optional.empty();
        double near=0,far=1;double[] origin={start.x(),start.y(),start.z()},delta={end.x()-start.x(),end.y()-start.y(),end.z()-start.z()};
        double[] min={bounds.min().x()-radius,bounds.min().y()-radius,bounds.min().z()-radius},max={bounds.max().x()+radius,bounds.max().y()+radius,bounds.max().z()+radius};
        for(int i=0;i<3;i++){
            if(min[i]>max[i])return Optional.empty();
            if(delta[i]==0){if(origin[i]<min[i]||origin[i]>max[i])return Optional.empty();continue;}
            double a=(min[i]-origin[i])/delta[i],b=(max[i]-origin[i])/delta[i];near=Math.max(near,Math.min(a,b));far=Math.min(far,Math.max(a,b));if(near>far)return Optional.empty();
        }
        return near>=0&&near<=1?Optional.of(start.add(end.subtract(start).scale(near))):Optional.empty();
    }
}

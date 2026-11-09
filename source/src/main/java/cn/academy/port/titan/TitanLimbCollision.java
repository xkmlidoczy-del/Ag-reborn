package cn.academy.port.titan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Server-only collision: transformed limb cuboids against entity AABBs. */
public final class TitanLimbCollision {
    private TitanLimbCollision() {}
    private static final int MAX_HITS=64;
    private static final double MAX_VERTEX_STEP=.30;
    public record SweepResult(int hits,int samples,double maxVertexStep) {}

    public static SweepResult sweep(ServerLevel level,TitanEntity caster,boolean variantB,
        double previousTime,double currentTime,Vec3 previousPosition,float previousYaw,Set<UUID> struck) {
        if(!TitanBoneRig.supported(caster.species(),variantB))return new SweepResult(0,0,0);
        Vec3 currentPosition=caster.position();float yaw=caster.yBodyRot;float scale=caster.getScale();
        List<TitanBoneRig.Shape> before=TitanBoneRig.shapes(caster.species(),variantB,previousTime,previousPosition,previousYaw,scale);
        List<TitanBoneRig.Shape> after=TitanBoneRig.shapes(caster.species(),variantB,currentTime,currentPosition,yaw,scale);
        if(before.size()!=after.size())throw new IllegalStateException("Collider count changed during attack");
        double max=0;AABB broad=null;
        for(int i=0;i<before.size();i++) {
            var a=before.get(i);var b=after.get(i);
            broad=broad==null?a.bounds().minmax(b.bounds()):broad.minmax(a.bounds()).minmax(b.bounds());
            for(int j=0;j<8;j++)max=Math.max(max,a.vertices()[j].distance(b.vertices()[j]));
        }
        if(broad==null)return new SweepResult(0,0,0);
        // Inflation includes curved trajectories between endpoints, not the hit
        // volume itself; narrow phase below uses the actual transformed cubes.
        List<LivingEntity> candidates=level.getEntitiesOfClass(LivingEntity.class,broad.inflate(max+1),
            target->TitanCombat.eligible(caster,target) && !struck.contains(target.getUUID()));
        if(Boolean.getBoolean("titancollision.debug") && Math.abs(currentTime-.65)<.001 && caster.getTarget()!=null) {
            Vec3 point=caster.getTarget().getBoundingBox().getCenter();
            var nearest=after.stream().min(java.util.Comparator.comparingDouble(s->distanceSqr(s.bounds(),point))).orElse(null);
            System.out.println("TITAN_LIMB_DIAG "+caster.species()+" variant="+variantB+" yaw="+yaw+
                " origin="+currentPosition+" target="+point+" nearest="+(nearest==null?"none":nearest.bone()+" "+nearest.bounds())+
                " candidates="+candidates.size()+" maxstep="+max);
        }
        if(candidates.isEmpty())return new SweepResult(0,0,0);
        int steps=Math.max(1,Math.min(96,(int)Math.ceil(max/MAX_VERTEX_STEP)));
        int hits=0;double stepDistance=max/steps;
        boolean diagnosticLogged=false;
        for(int i=0;i<=steps && hits<MAX_HITS;i++) {
            double f=(double)i/steps,t=Mth.lerp(f,previousTime,currentTime);
            Vec3 pos=previousPosition.lerp(currentPosition,f);float rotation=Mth.rotLerp((float)f,previousYaw,yaw);
            List<TitanBoneRig.Shape> shapes=(i==0)?before:(i==steps?after:TitanBoneRig.shapes(caster.species(),variantB,t,pos,rotation,scale));
            for(LivingEntity victim:candidates) {
                if(struck.contains(victim.getUUID()) || !TitanCombat.eligible(caster,victim))continue;
                AABB box=victim.getBoundingBox();TitanBoneRig.Shape contact=null;
                for(var shape:shapes) {
                    if(!shape.bounds().intersects(box) || !intersects(shape.vertices(),box))continue;
                    // A conservative solid-block visibility check avoids damage
                    // through walls even if the decorative limb clips a block.
                    Vec3 destination=box.getCenter();
                    var block=level.clip(new ClipContext(shape.root(),destination,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,caster));
                    if(Boolean.getBoolean("titancollision.debug") && !diagnosticLogged && victim==caster.getTarget()) {
                        diagnosticLogged=true;System.out.println("TITAN_LIMB_CONTACT "+caster.species()+" bone="+shape.bone()+
                            " time="+t+" block="+block.getType()+" at="+block.getBlockPos()+" invulnerability="+victim.invulnerableTime);
                    }
                    if(block.getType()!=HitResult.Type.MISS)continue;
                    contact=shape;break;
                }
                if(contact!=null && TitanCombat.limbHit(level,caster,victim)) {
                    struck.add(victim.getUUID());hits++;
                }
            }
        }
        return new SweepResult(hits,steps+1,stepDistance);
    }
    private static double distanceSqr(AABB b,Vec3 p) {
        double x=Math.max(b.minX,Math.min(b.maxX,p.x))-p.x;
        double y=Math.max(b.minY,Math.min(b.maxY,p.y))-p.y;
        double z=Math.max(b.minZ,Math.min(b.maxZ,p.z))-p.z;
        return x*x+y*y+z*z;
    }

    /** Exact separating-axis test between an affine cuboid and an entity AABB. */
    public static boolean intersects(Vector3d[] vertices,AABB box) {
        Vector3d e0=new Vector3d(vertices[4]).sub(vertices[0]);
        Vector3d e1=new Vector3d(vertices[2]).sub(vertices[0]);
        Vector3d e2=new Vector3d(vertices[1]).sub(vertices[0]);
        Vector3d[] axes={new Vector3d(1,0,0),new Vector3d(0,1,0),new Vector3d(0,0,1),
            new Vector3d(e0).cross(e1),new Vector3d(e1).cross(e2),new Vector3d(e2).cross(e0),
            new Vector3d(e0).cross(1,0,0),new Vector3d(e0).cross(0,1,0),new Vector3d(e0).cross(0,0,1),
            new Vector3d(e1).cross(1,0,0),new Vector3d(e1).cross(0,1,0),new Vector3d(e1).cross(0,0,1),
            new Vector3d(e2).cross(1,0,0),new Vector3d(e2).cross(0,1,0),new Vector3d(e2).cross(0,0,1)};
        double cx=(box.minX+box.maxX)/2,cy=(box.minY+box.maxY)/2,cz=(box.minZ+box.maxZ)/2;
        double hx=(box.maxX-box.minX)/2,hy=(box.maxY-box.minY)/2,hz=(box.maxZ-box.minZ)/2;
        for(Vector3d axis:axes) {
            if(axis.lengthSquared()<1e-14)continue;
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(Vector3d vertex:vertices){double value=axis.dot(vertex);min=Math.min(min,value);max=Math.max(max,value);}
            double center=axis.x*cx+axis.y*cy+axis.z*cz;
            double radius=Math.abs(axis.x)*hx+Math.abs(axis.y)*hy+Math.abs(axis.z)*hz;
            if(max<center-radius-1e-8 || min>center+radius+1e-8)return false;
        }
        return true;
    }
}

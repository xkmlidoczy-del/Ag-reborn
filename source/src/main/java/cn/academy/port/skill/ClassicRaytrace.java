/* Port of LambdaLib 1.2.3 entity-ray semantics (MIT); see docs/LAMBDALIB-LICENSE. */
package cn.academy.port.skill;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
public final class ClassicRaytrace {
    private ClassicRaytrace() {}
    public static Vec3 direction(Entity source) {return Vec3.directionFromRotation(source.getXRot(),source instanceof LivingEntity living?living.getYHeadRot():source.getYRot());}
    /** "traceLiving" means trace from the source's eye, not restrict targets to living entities. */
    public static HitResult living(Entity source,double range,ClipContext.Fluid fluids) {
        var start=source.getEyePosition();return perform(source,start,start.add(direction(source).scale(range)),fluids,e->true);
    }
    /** LambdaLib chooses nearest inflated hitbox but reports entity feet and compares feet to blocks. */
    public static HitResult perform(Entity source,Vec3 start,Vec3 end,ClipContext.Fluid fluids,Predicate<Entity> selector) {
        var world=source.level();Entity nearest=null;double best=0;
        for(var candidate:world.getEntities(source,new AABB(start,end).inflate(1),e->e.isAlive()&&e.isPickable()&&(selector==null||selector.test(e)))) {
            double distance=ThreateningTeleport.classicEntityDistanceSquared(candidate.getBoundingBox(),start,end);
            if(Double.isFinite(distance)&&(distance<best||best==0)){nearest=candidate;best=distance;}
        }
        var block=world.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,fluids,source));
        if(nearest!=null&&(block.getType()==HitResult.Type.MISS||start.distanceToSqr(nearest.position())<=start.distanceToSqr(block.getLocation())))return new EntityHitResult(nearest,nearest.position());
        return block;
    }
}

package cn.academy.port.highpower;

import cn.academy.port.highpower.core.DisplacedProjectionRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** NEW311 closed, loaded-only acquisition. A visible fixed side point is not a damage hit. */
final class DisplacedProjectionWorld {
    record Plan(ServerLevel world,ServerPlayer owner,LivingEntity target,Vec3 caster,
                Vec3 endpoint,Vec3 side,Vec3 velocity,float yaw,float pitch){}
    record Result(Plan plan,String reason){boolean valid(){return plan!=null;}}
    private record Contact(Entity entity,Vec3 point,double distance){}
    private DisplacedProjectionWorld(){}
    static Result acquire(ServerPlayer p){
        if(p==null||!p.server.isSameThread()||!finite(p.getEyePosition())||!finite(p.getLookAngle()))return fail("not_ready");
        Vec3 start=p.getEyePosition(),end=start.add(p.getLookAngle().scale(DisplacedProjectionRules.RANGE));
        String volume=volume(p,new AABB(start,end).inflate(.01));if(!volume.isEmpty())return fail(volume);
        var solid=p.serverLevel().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,p));
        double solidDistance=solid.getType()==HitResult.Type.MISS?Double.POSITIVE_INFINITY:start.distanceToSqr(solid.getLocation());
        List<Entity> entities=p.serverLevel().getEntities(p,new AABB(start,end).inflate(1),e->e.isPickable()&&!e.isSpectator());
        if(entities.size()>64)return fail("no_target");
        Contact first=entities.stream().map(e->e.getBoundingBox().clip(start,end).map(point->new Contact(e,point,start.distanceToSqr(point))))
                .flatMap(Optional::stream).min(Comparator.comparingDouble(Contact::distance)).orElse(null);
        if(first==null||first.distance>=solidDistance||!(first.entity instanceof LivingEntity target)||!hostile(p,target))return fail("no_target");
        Vec3 center=target.getBoundingBox().getCenter();
        var s=DisplacedProjectionRules.side(point(center),p.getYRot());if(s==null)return fail("out_of_range");
        Vec3 side=new Vec3(s.x(),s.y(),s.z());
        if(!DisplacedProjectionRules.path(point(start),s,point(center)))return fail("out_of_range");
        Vec3 direction=center.subtract(side).normalize();
        Plan plan=new Plan(p.serverLevel(),p,target,start,center,side,direction.scale(DisplacedProjectionRules.SPEED),p.getYRot(),p.getXRot());
        String reason=validate(plan,null,true);return reason.isEmpty()?new Result(plan,""):fail(reason);
    }
    static String validate(Plan plan,Entity medium,boolean requireSourcePose){
        ServerPlayer p=plan.owner;LivingEntity target=plan.target;
        if(!p.isAlive()||p.isRemoved()||p.level()!=plan.world||p.serverLevel().getEntity(p.getUUID())!=p)return "session_changed";
        if(requireSourcePose&&(!p.getEyePosition().equals(plan.caster)||p.getYRot()!=plan.yaw||p.getXRot()!=plan.pitch))return "transaction_changed";
        if(!hostile(p,target))return "target_changed";
        if(!DisplacedProjectionRules.path(point(p.getEyePosition()),point(plan.side),point(plan.endpoint)))return "out_of_range";
        String v=volume(p,new AABB(plan.side.x-.125,plan.side.y,plan.side.z-.125,
                plan.side.x+.125,plan.side.y+.25,plan.side.z+.125));if(!v.isEmpty())return v;
        AABB body=new AABB(plan.side.x-.125,plan.side.y,plan.side.z-.125,plan.side.x+.125,plan.side.y+.25,plan.side.z+.125);
        if(!plan.world.noCollision(body)||!plan.world.getEntities(p,body,e->e!=medium&&!e.isSpectator()).isEmpty())return "blocked";
        if(!clear(p,p.getEyePosition(),plan.side,null,medium)||!clear(p,plan.side,plan.endpoint,target,medium))return "blocked";
        // A currently registered source must itself remain loaded and physically visible;
        // this prototype does not pull an embedded/unloaded medium through terrain.
        if(medium!=null&&(!finite(medium.position())||medium.level()!=plan.world
                ||medium.position().distanceToSqr(p.getEyePosition())>DisplacedProjectionRules.RANGE*DisplacedProjectionRules.RANGE
                ||!clear(p,medium.position(),plan.side,null,medium)))return "blocked";
        return "";
    }
    static boolean hostile(ServerPlayer p,LivingEntity target){
        return HighPowerCombat.hostileTarget(target)&&target.level()==p.level()&&target.isAlive()&&!target.isRemoved()
                &&!target.isSpectator()&&!target.isInvulnerable()&&HighPowerCombat.supported(target)
                &&HighPowerCombat.unsupportedReason(target).isEmpty()
                &&p.serverLevel().getEntity(target.getUUID())==target&&p.serverLevel().getEntity(target.getId())==target
                &&!p.isAlliedTo(target)&&!target.isAlliedTo(p)&&volume(p,target.getBoundingBox()).isEmpty();
    }
    /** Pinned native AbstractArrow uses0.3F body inflation; keep its chosen first body. */
    static boolean contactClear(ServerPlayer p,Vec3 start,Vec3 end,LivingEntity target,DisplacedNeedleEntity medium){
        if(!finite(start)||!finite(end)||!volume(p,new AABB(start,end).inflate(.301)).isEmpty())return false;
        var clip=p.serverLevel().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,p));
        if(clip.getType()!=HitResult.Type.MISS)return false;
        var padded=target.getBoundingBox().inflate((double)0.3F);
        if(end.x<padded.minX||end.x>padded.maxX||end.y<padded.minY||end.y>padded.maxY||end.z<padded.minZ||end.z>padded.maxZ)return false;
        // Native padding is never permission to hit a body hidden behind newly inserted terrain.
        var bodySight=p.serverLevel().clip(new ClipContext(start,target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,p));
        if(bodySight.getType()!=HitResult.Type.MISS)return false;
        var bodies=p.serverLevel().getEntities(p,new AABB(start,end).inflate(1),e->e!=medium&&e.isPickable()&&!e.isSpectator());
        if(bodies.size()>64)return false;
        double distance=start.distanceToSqr(end);
        for(Entity body:bodies)if(body!=target){
            var obstruction=body.getBoundingBox().inflate((double)0.3F);
            // A body that moved over the segment origin cannot be skipped merely
            // because native AABB.clip reports its exit rather than its entry.
            if(obstruction.contains(start))return false;
            var hit=obstruction.clip(start,end);
            if(hit.isPresent()&&start.distanceToSqr(hit.get())<=distance)return false;
        }
        return true;
    }

    static boolean clear(ServerPlayer p,Vec3 start,Vec3 end,Entity endpointTarget,Entity ignoredMedium){
        if(!finite(start)||!finite(end)||!volume(p,new AABB(start,end).inflate(.001)).isEmpty())return false;
        var solid=p.serverLevel().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,p));
        double block=solid.getType()==HitResult.Type.MISS?Double.POSITIVE_INFINITY:start.distanceToSqr(solid.getLocation());
        List<Entity> bodies=p.serverLevel().getEntities(p,new AABB(start,end).inflate(.3),e->e!=ignoredMedium&&e.isPickable()&&!e.isSpectator());
        if(bodies.size()>64)return false;
        Contact first=bodies.stream().map(e->e.getBoundingBox().clip(start,end).map(hit->new Contact(e,hit,start.distanceToSqr(hit))))
                .flatMap(Optional::stream).min(Comparator.comparingDouble(Contact::distance)).orElse(null);
        if(endpointTarget!=null)return first!=null&&first.entity==endpointTarget&&first.distance<block;
        return first==null&&solid.getType()==HitResult.Type.MISS;
    }
    static String volume(ServerPlayer p,AABB box){
        if(p==null||box==null||!finite(new Vec3(box.minX,box.minY,box.minZ))||!finite(new Vec3(box.maxX,box.maxY,box.maxZ)))return "out_of_range";
        ServerLevel w=p.serverLevel();if(!w.getWorldBorder().isWithinBounds(box))return "border";
        if(box.minY<w.getMinBuildHeight()||box.maxY>w.getMaxBuildHeight())return "out_of_range";
        int x0=(int)Math.floor(box.minX),x1=(int)Math.floor(box.maxX),y0=(int)Math.floor(box.minY),y1=(int)Math.floor(box.maxY),z0=(int)Math.floor(box.minZ),z1=(int)Math.floor(box.maxZ);
        if((long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1)>12000)return "out_of_range";
        for(int x=x0>>4;x<=x1>>4;x++)for(int z=z0>>4;z<=z1>>4;z++)if(w.getChunkSource().getChunkNow(x,z)==null)return "unloaded";
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)if(!w.mayInteract(p,new BlockPos(x,y,z)))return "permission";
        return "";
    }
    static boolean finite(Vec3 v){return v!=null&&point(v).finite();}
    private static DisplacedProjectionRules.Point point(Vec3 v){return new DisplacedProjectionRules.Point(v.x,v.y,v.z);}
    private static Result fail(String reason){return new Result(null,reason);}
}

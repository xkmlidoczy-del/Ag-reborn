package cn.academy.port.worldboss;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Bounded vanilla-scale attacks. A cast owns its budget, aim and outstanding meteors. */
public final class WorldBossCombat {
    public record Meteor(long due,Vec3 position,float damage,long cast) {}
    private static final float[] MAXIMUM={0,0,0,4,10,12,12,10,10,10,12,10,12,10,10,12,12,14,16};
    private static float[][][][] anchors;
    public static float maximumDamage(int clip){return clip>=0&&clip<MAXIMUM.length?MAXIMUM[clip]:0;}
    public static boolean eligibleTarget(WorldBossContent.Boss boss,LivingEntity victim) {
        if(victim==null||victim==boss||victim instanceof WorldBossContent.Boss||victim.level()!=boss.level()
            ||!victim.isAlive()||victim.isRemoved()||boss.isAlliedTo(victim))return false;
        return !(victim instanceof Player player&&(player.isCreative()||player.isSpectator()));
    }
    private static synchronized float[][][][] anchors() {
        if(anchors==null) {
            var stream=WorldBossCombat.class.getResourceAsStream("/assets/academy/worldboss/anchors.json");
            if(stream==null)throw new IllegalStateException("Missing World Boss anchors");
            try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                anchors=new Gson().fromJson(reader,float[][][][].class);
                if(anchors.length!=19)throw new IllegalStateException("Invalid boss anchor clips");
            } catch(java.io.IOException ex){throw new IllegalStateException(ex);}
        }
        return anchors;
    }
    /** Same baked V9 mouth locator used by the client, including the original pit-rise displacement. */
    public static Vec3 mouth(WorldBossContent.Boss boss) {
        var frames=anchors()[boss.clip()];int frame=Mth.clamp((int)boss.frame(0),0,frames.length-1);var p=frames[frame][0];
        return boss.local(p[0],p[1]+boss.rise(boss.frame(0)),p[2]);
    }
    /** Authored mouth orientation, shared with the client locator rather than the body's yaw. */
    public static Vec3 mouthDirection(WorldBossContent.Boss boss) {
        var frames=anchors()[boss.clip()];int frame=Mth.clamp((int)boss.frame(0),0,frames.length-1);var p=frames[frame][6];
        return boss.local(p[0],p[1]+boss.rise(boss.frame(0)),p[2]).subtract(mouth(boss)).normalize();
    }
    private static void fx(WorldBossContent.Boss boss,String kind,Vec3 point,float a,float b,float c,int i,int j) {
        PacketDistributor.sendToPlayersNear((ServerLevel)boss.level(),null,point.x,point.y,point.z,420,
            new FxPacket(kind,(float)point.x,(float)point.y,(float)point.z,a,b,c,i,j));
    }
    private static void sound(WorldBossContent.Boss boss,String name,Vec3 at,float volume,float pitch) {
        var holder=WorldBossContent.SND.get(name);
        if(holder!=null)boss.level().playSound(null,at.x,at.y,at.z,holder.get(),SoundSource.HOSTILE,volume,pitch);
    }
    private static void shake(WorldBossContent.Boss boss,Vec3 at,float strength,int ticks) {fx(boss,"shake",at,strength,96,0,ticks,0);}
    private static void ring(WorldBossContent.Boss boss,Vec3 at,float radius) {fx(boss,"ring",at.add(0,.15,0),radius,.8F,0,28,0);}
    private static void crack(WorldBossContent.Boss boss,Vec3 at,float radius){fx(boss,"crack",at.add(0,.05,0),radius,0,0,60,0);}
    private static boolean visible(WorldBossContent.Boss boss,Vec3 from,Vec3 to) {
        return boss.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss)).getType()==HitResult.Type.MISS;
    }
    private static void damage(WorldBossContent.Boss boss,LivingEntity victim,float amount,Vec3 origin,double knockback) {
        if(!eligibleTarget(boss,victim))return;
        // Charge even a cancelled native hit against this cast; shields/iframe cannot cause unbounded retries.
        float spend=boss.reserveDamage(victim,amount);if(spend<=0)return;
        boolean hurt=victim.hurt(boss.damageSources().mobAttack(boss),spend);
        if(hurt&&boss.clip()==Clips.ROAR)
            victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,60,2),boss);
        if(hurt&&knockback>0) {
            Vec3 direction=victim.position().subtract(origin);double dx=direction.x,dz=direction.z;
            if(dx*dx+dz*dz<1e-8){var f=boss.forward();dx=f.x;dz=f.z;}
            // Native knockback emits ACA's shield/superarmor event. No direct push/velocity override.
            victim.knockback(knockback,-dx,-dz);
        }
    }
    private static void pulse(WorldBossContent.Boss boss,Vec3 center,double radius,float damage,double knockback) {
        var area=new AABB(center,center).inflate(radius);
        for(var victim:boss.level().getEntitiesOfClass(LivingEntity.class,area)) {
            if(!eligibleTarget(boss,victim))continue;
            var box=victim.getBoundingBox();var point=new Vec3(Mth.clamp(center.x,box.minX,box.maxX),Mth.clamp(center.y,box.minY,box.maxY),Mth.clamp(center.z,box.minZ,box.maxZ));
            double distance=center.distanceTo(point);if(distance>radius||!visible(boss,center,point))continue;
            damage(boss,victim,(float)(damage*(1-.6*distance/radius)),center,knockback);
        }
    }
    public static void event(WorldBossContent.Boss boss,String type,int age) {
        if(!boss.isAlive()||boss.level().isClientSide)return;
        int clip=boss.clip();Vec3 origin=boss.position();
        WorldBossPresentation.event(boss,type,clip,age);
        // Combat uses native damage and the agreed vanilla-scale whole-action budgets.
        switch(type) {
            case "bite" -> pulse(boss,boss.local(0,4,-20),12,clip==Clips.COMBO?6:10,.45);
            case "ram" -> pulse(boss,boss.local(0,3,-18),14,clip==Clips.CHARGE?10:12,.7);
            case "left_slash","right_slash" -> pulse(boss,boss.local(type.equals("left_slash")?4.5:-4.5,4,-15),10,10,.5);
            case "cross_slash" -> pulse(boss,boss.local(0,4,-18),13,6,.6);
            case "tail_sweep" -> pulse(boss,boss.local(0,2,24),18,10,.75);
            case "tail_quake" -> pulse(boss,boss.local(0,0,26),18,12,.7);
            case "stomp" -> pulse(boss,boss.local(5.1,0,0),18,10,.6);
            case "quake" -> {if(clip!=Clips.SPAWN)pulse(boss,origin,24,6,.65);}
            case "eruption" -> pulse(boss,origin,28,6,.65);
            case "nova" -> pulse(boss,origin,36,14,.8);
            case "cataclysm" -> pulse(boss,origin,44,10,.85);
            case "roar" -> {if(clip==Clips.ROAR)pulse(boss,origin,30,2,.45);}
            case "meteor" -> {
                var target=boss.getTarget();Vec3 targetPoint=eligibleTarget(boss,target)?target.position():boss.local(0,0,-32);
                for(int i=0;i<4&&boss.meteors.size()<16;i++) {
                    Vec3 point=i==0?targetPoint:targetPoint.add((boss.getRandom().nextDouble()-.5)*24,0,(boss.getRandom().nextDouble()-.5)*24);
                    int ground=boss.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,(int)Math.floor(point.x),(int)Math.floor(point.z));
                    point=new Vec3(point.x,ground,point.z);
                    float damage=clip==Clips.WORLDBREAKER?.75F:1F;int delay=18+i*9;
                    boss.meteors.add(new Meteor(boss.level().getGameTime()+delay,point,damage,boss.castSequence()));
                    fx(boss,"meteor",mouth(boss),(float)point.x,(float)point.y,(float)point.z,delay,0);
                }
                sound(boss,"meteor",mouth(boss),12,1);
            }
            default -> {}
        }
        WorldBossEnvironment.event(boss,type,age);
    }
    public static void continuous(WorldBossContent.Boss boss,int age) {
        if(!boss.isAlive())return;int clip=boss.clip();
        WorldBossEnvironment.continuous(boss,age);
        if(clip==Clips.CHARGE&&age>=42&&age<86&&age%4==0)pulse(boss,boss.local(0,2,-12),8,10,.6);
        boolean breath=clip==Clips.BREATH&&age>=46&&age<102,beam=clip==Clips.LANCE&&age>=57&&age<107;
        if((!breath&&!beam)||age%10!=0)return;
        Vec3 mouth=mouth(boss),direction=boss.attackAim().subtract(mouth).normalize();double length=breath?46:90;
        Vec3 end=mouth.add(direction.scale(length));var area=new AABB(mouth,end).inflate(breath?14:3.5);
        for(var victim:boss.level().getEntitiesOfClass(LivingEntity.class,area)) {
            if(!eligibleTarget(boss,victim))continue;
            Vec3 point=victim.getBoundingBox().getCenter(),delta=point.subtract(mouth);double along=delta.dot(direction);
            if(along<0||along>length)continue;
            double width=breath?2+along*.22:2.0;
            if(delta.subtract(direction.scale(along)).length()>width+victim.getBbWidth()*.5||!visible(boss,mouth,point))continue;
            damage(boss,victim,breath?2:3,mouth,.12);
        }
    }
    public static void meteors(WorldBossContent.Boss boss) {
        if(!boss.isAlive()){boss.meteors.clear();return;}
        long now=boss.level().getGameTime();
        for(var it=boss.meteors.iterator();it.hasNext();) {
            var meteor=it.next();if(meteor.cast()!=boss.castSequence()){it.remove();continue;}
            if(now>=meteor.due()) {
                it.remove();pulse(boss,meteor.position(),10,meteor.damage(),.45);
                Vec3 at=meteor.position();ServerLevel level=(ServerLevel)boss.level();
                WorldBossPresentation.sound(level,"impact",at.x,at.y,at.z,12,.9F);
                WorldBossPresentation.sound(level,"meteor",at.x,at.y,at.z,10,.8F);
                WorldBossPresentation.fx(level,"ring",at.x,at.y+.2,at.z,12,.8F,0,24,0);
                WorldBossPresentation.fx(level,"burst",at.x,at.y+1,at.z,1.6F,0,0,42,2);
                WorldBossPresentation.shake(level,at.x,at.y,at.z,.68F,14,130);
                WorldBossPresentation.crack(level,at.x,at.y,at.z,7);
                WorldBossEnvironment.meteorImpact(boss,at);
            }
        }
    }
    private WorldBossCombat() {}
}

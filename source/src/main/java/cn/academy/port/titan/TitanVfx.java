package cn.academy.port.titan;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** One bounded visual event per stage, instead of one network packet per particle. */
public final class TitanVfx {
    private TitanVfx() {}
    private static void send(ServerLevel level,String id,int slot,int phase,int age,Vec3 origin,Vec3 target,double radius) {
        Vec3 middle=origin.add(target).scale(.5);
        double reach=Math.min(192,Math.max(112,origin.distanceTo(target)*.5+radius+48));
        TitanFxPayload payload=new TitanFxPayload(id,slot,phase,age,origin,target,TitanConfig.PARTICLE_DENSITY.get().floatValue());
        PacketDistributor.sendToPlayersNear(level,null,middle.x,middle.y,middle.z,reach,payload);
    }
    private static int slot(String id,TitanProfiles.Ability ability) {
        return TitanProfiles.get(id).second().equals(ability)?2:1;
    }
    public static void telegraph(ServerLevel level,String id,TitanProfiles.Ability ability,Vec3 origin,Vec3 target,int age) {
        send(level,id,slot(id,ability),0,age,origin,target,ability.radius());
    }
    public static void impact(ServerLevel level,String id,TitanProfiles.Ability ability,Vec3 origin,Vec3 target) {
        send(level,id,slot(id,ability),1,0,origin,target,ability.radius());
    }
    public static void melee(ServerLevel level,String id,Vec3 at) {
        send(level,id,0,2,0,at,at,8);
    }
}

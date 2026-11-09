package cn.academy.port.titan;

import cn.academy.port.city.*;
import cn.academy.port.city.portal.CityPortalTravel;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Rural mountain district and the quiet outer 300-block band, never ordinary city parks. */
public final class CityTitanZones {
    public static final long PLAN_SEED=20261103L;
    private CityTitanZones(){}
    public static boolean dimension(ResourceKey<Level> key){return key.equals(CityPortalTravel.CITY);}
    public static boolean rural(int x,int z){
        if(Math.abs((long)x)>CityPlan.HALF+256L||Math.abs((long)z)>CityPlan.HALF+256L)return false;
        return CityPlan.district(x,z)==21||Math.max(Math.abs(x),Math.abs(z))>=1700;
    }
    public static boolean quietPlan(int x,int z,int radius){
        if(!rural(x,z))return false;
        for(int dx=-radius;dx<=radius;dx+=Math.max(1,radius/2))for(int dz=-radius;dz<=radius;dz+=Math.max(1,radius/2)){
            int px=x+dx,pz=z+dz;if(!rural(px,pz))return false;
            var kind=CityPlan.sample(PLAN_SEED,px,pz).kind();
            if(kind!=CityPlan.Kind.TERRAIN&&kind!=CityPlan.Kind.GROUND&&kind!=CityPlan.Kind.PARK&&kind!=CityPlan.Kind.WATER&&kind!=CityPlan.Kind.SEA)return false;
        }return true;
    }
    public static boolean allowed(ServerLevel level,BlockPos pos,float bodyWidth){
        int radius=Math.max(24,(int)Math.ceil(bodyWidth*.5+14));
        if(!dimension(level.dimension())||!quietPlan(pos.getX(),pos.getZ(),radius))return false;
        var area=new AABB(pos.getX()-96,level.getMinBuildHeight(),pos.getZ()-96,pos.getX()+97,level.getMaxBuildHeight(),pos.getZ()+97);
        return level.getEntitiesOfClass(CityNpcEntity.class,area).isEmpty()&&level.getEntitiesOfClass(Villager.class,area).isEmpty();
    }
    /** V9 retains bounded rural terrain abilities, but never edits city buildings or block entities. */
    public static boolean terrainAllowed(ServerLevel level,BlockPos pos){
        if(!dimension(level.dimension())||!rural(pos.getX(),pos.getZ())||!level.hasChunkAt(pos))return false;
        var sample=CityPlan.sample(PLAN_SEED,pos.getX(),pos.getZ());
        if(sample.kind()!=CityPlan.Kind.TERRAIN&&sample.kind()!=CityPlan.Kind.GROUND&&sample.kind()!=CityPlan.Kind.PARK&&sample.kind()!=CityPlan.Kind.WATER&&sample.kind()!=CityPlan.Kind.SEA)return false;
        var state=level.getBlockState(pos);
        return !state.hasBlockEntity()&&(state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.DIRT)||state.is(Blocks.COARSE_DIRT)||state.is(Blocks.STONE)||state.is(Blocks.DEEPSLATE)||state.is(Blocks.SAND)||state.is(Blocks.RED_SAND)||state.is(Blocks.GRAVEL)||state.is(Blocks.SNOW_BLOCK)||state.is(Blocks.SNOW)||state.is(Blocks.ICE)||state.is(Blocks.FROSTED_ICE)||state.is(Blocks.WATER)||state.is(Blocks.MYCELIUM));
    }
}

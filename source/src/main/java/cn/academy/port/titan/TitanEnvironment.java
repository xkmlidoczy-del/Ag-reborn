package cn.academy.port.titan;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/** Bounded, grief-rule-aware block effects; no block entities or unbreakable blocks are altered. */
public final class TitanEnvironment {
    private TitanEnvironment() {}
    private static boolean nearby(ServerLevel level,BlockPos center,Predicate<BlockState> match) {
        for(int dx=-3;dx<=3;dx++)for(int dy=-2;dy<=2;dy++)for(int dz=-3;dz<=3;dz++) {
            BlockPos p=center.offset(dx,dy,dz);
            if(level.hasChunkAt(p)&&match.test(level.getBlockState(p)))return true;
        }
        return false;
    }
    public static boolean empowered(ServerLevel level,TitanEntity titan) {
        BlockPos p=titan.blockPosition();BlockState below=level.getBlockState(p.below());
        return switch(titan.profile().environment()) {
            case LAVA -> titan.isInLava() || level.dimensionType().ultraWarm() || nearby(level,p,s->s.is(Blocks.MAGMA_BLOCK));
            case FROST -> below.is(BlockTags.ICE)||below.is(Blocks.SNOW_BLOCK)||below.is(Blocks.SNOW)||nearby(level,p,s->s.is(Blocks.SNOW_BLOCK)||s.is(BlockTags.ICE));
            case STORM -> level.isThundering() || titan.isInWaterRainOrBubble();
            case GROVE -> below.is(Blocks.GRASS_BLOCK)||nearby(level,p,s->s.is(BlockTags.LOGS));
            case TIDAL -> titan.isInWaterOrBubble();
            case VOID -> !titan.onGround() && titan.getY()>level.getSeaLevel()+16;
            case CRYSTAL -> nearby(level,p,s->s.is(Blocks.AMETHYST_BLOCK)||s.is(Blocks.AMETHYST_CLUSTER)||s.is(Blocks.BUDDING_AMETHYST));
            case SAND -> below.is(BlockTags.SAND);
            case FUNGUS -> below.is(Blocks.MYCELIUM)||nearby(level,p,s->s.is(Blocks.RED_MUSHROOM)||s.is(Blocks.BROWN_MUSHROOM)||s.is(Blocks.MYCELIUM));
            case SOUL -> below.is(Blocks.SOUL_SAND)||below.is(Blocks.SOUL_SOIL)||nearby(level,p,s->s.is(Blocks.SOUL_FIRE));
            case METAL -> nearby(level,p,s->s.is(BlockTags.BEACON_BASE_BLOCKS)||s.is(Blocks.COPPER_BLOCK));
            case NIGHT -> level.isNight();
        };
    }
    /** Returns number changed; samples only loaded chunks and respects mob griefing hooks. */
    public static int interact(ServerLevel level,TitanEntity titan,Vec3 center,double radius) {
        if(!TitanConfig.TERRAIN_CHANGES.get() || !EventHooks.canEntityGrief(level,titan))return 0;
        int limit=TitanConfig.TERRAIN_BLOCK_LIMIT.get();if(limit<=0)return 0;
        var env=titan.profile().environment();
        int changed=0;int span=(int)Math.min(32,Math.max(3,radius));
        for(int i=0;i<Math.min(512,limit*24) && changed<limit;i++) {
            int dx=level.random.nextInt(span*2+1)-span,dz=level.random.nextInt(span*2+1)-span;
            if(dx*dx+dz*dz>span*span)continue;
            BlockPos xz=BlockPos.containing(center.x+dx,center.y,center.z+dz);
            if(!level.hasChunkAt(xz))continue;
            BlockPos top=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,xz);
            if(top.getY()>center.y+6 || top.getY()<center.y-40)continue;
            BlockPos pos=top.below();if(!CityTitanZones.terrainAllowed(level,pos))continue;BlockState state=level.getBlockState(pos);
            if(state.isAir() || state.hasBlockEntity() || !state.canEntityDestroy(level,pos,titan))continue;
            float hardness=state.getDestroySpeed(level,pos);
            boolean isSourceWater=state.is(Blocks.WATER) && level.getFluidState(pos).is(FluidTags.WATER)
                && level.getFluidState(pos).isSource() && level.isEmptyBlock(pos.above());
            if(!isSourceWater && (hardness<0 || hardness>8))continue;
            if(!EventHooks.onEntityDestroyBlock(titan,pos,state))continue;
            if(TitanConfig.BLOCK_DESTRUCTION.get() && i%3==0 && !isSourceWater && level.destroyBlock(pos,true,titan)) {
                changed++;continue;
            }
            BlockState next=themedReplacement(env,state,isSourceWater);
            if(next==null || next.equals(state))continue;
            if(level.setBlockAndUpdate(pos,next)) {
                level.levelEvent(2001,pos,Block.getId(state));
                if(next.is(Blocks.FROSTED_ICE))level.scheduleTick(pos,Blocks.FROSTED_ICE,60+level.random.nextInt(61));
                changed++;
            }
        }
        return changed;
    }

    private static BlockState themedReplacement(TitanProfiles.Environment env,BlockState state,boolean sourceWater) {
        if(env==TitanProfiles.Environment.FROST && sourceWater)return Blocks.FROSTED_ICE.defaultBlockState();
        if(env==TitanProfiles.Environment.LAVA && state.is(Blocks.FROSTED_ICE))return Blocks.WATER.defaultBlockState();
        if(env==TitanProfiles.Environment.GROVE && state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state))
            return crop.getStateForAge(crop.getAge(state)+1);
        // Replacements are intentionally restricted to common natural terrain, not builds or inventories.
        boolean natural=state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.DIRT)||state.is(Blocks.COARSE_DIRT)
            ||state.is(Blocks.STONE)||state.is(Blocks.COBBLESTONE)||state.is(Blocks.GRAVEL)
            ||state.is(Blocks.SAND)||state.is(Blocks.RED_SAND)||state.is(Blocks.MYCELIUM)
            ||state.is(Blocks.MOSS_BLOCK)||state.is(Blocks.SNOW_BLOCK)||state.is(Blocks.MUD)
            ||state.is(Blocks.SOUL_SOIL);
        if(!natural)return null;
        return switch(env) {
            case LAVA -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case FROST -> Blocks.SNOW_BLOCK.defaultBlockState();
            case STORM -> state.is(Blocks.SAND)||state.is(Blocks.RED_SAND)?Blocks.GLASS.defaultBlockState():Blocks.COARSE_DIRT.defaultBlockState();
            case GROVE -> Blocks.MOSS_BLOCK.defaultBlockState();
            case TIDAL -> Blocks.MUD.defaultBlockState();
            case VOID -> Blocks.DEEPSLATE.defaultBlockState();
            case CRYSTAL -> Blocks.AMETHYST_BLOCK.defaultBlockState();
            case SAND -> Blocks.SAND.defaultBlockState();
            case FUNGUS -> Blocks.MYCELIUM.defaultBlockState();
            case SOUL -> Blocks.SOUL_SOIL.defaultBlockState();
            case METAL -> Blocks.COPPER_BLOCK.defaultBlockState();
            case NIGHT -> Blocks.SCULK.defaultBlockState();
        };
    }
}

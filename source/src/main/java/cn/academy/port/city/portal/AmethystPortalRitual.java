package cn.academy.port.city.portal;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.joml.Vector3f;

/** Four source-water cells, two living fibers per side, and exactly one offered dirt item. */
@EventBusSubscriber(modid="academy")
public final class AmethystPortalRitual {
    public static final DustParticleOptions SPARK=new DustParticleOptions(new Vector3f(.63f,.20f,.94f),.7f);
    public static final List<BlockPos> WATER=List.of(new BlockPos(0,0,0),new BlockPos(1,0,0),new BlockPos(0,0,1),new BlockPos(1,0,1));
    public static final List<BlockPos> FIBERS=List.of(new BlockPos(0,1,-1),new BlockPos(1,1,-1),new BlockPos(0,1,2),new BlockPos(1,1,2),new BlockPos(-1,1,0),new BlockPos(-1,1,1),new BlockPos(2,1,0),new BlockPos(2,1,1));
    private AmethystPortalRitual(){}
    public static boolean loaded(ServerLevel world,BlockPos anchor){for(var off:FIBERS)if(!world.hasChunkAt(anchor.offset(off)))return false;for(var off:WATER)if(!world.hasChunkAt(anchor.offset(off)))return false;return true;}
    public static boolean frame(ServerLevel world,BlockPos anchor){
        if(!world.getWorldBorder().isWithinBounds(anchor.offset(-1,0,-1))||!world.getWorldBorder().isWithinBounds(anchor.offset(2,0,2)))return false;
        for(var off:FIBERS){var p=anchor.offset(off);if(!world.hasChunkAt(p))return false;var s=world.getBlockState(p);if(!s.is(AmethystPortalContent.FIBER.get())||!s.canSurvive(world,p))return false;}
        for(var off:WATER){var p=anchor.offset(off);if(!world.hasChunkAt(p)||!world.getBlockState(p.above()).getCollisionShape(world,p.above()).isEmpty())return false;}
        return true;
    }
    public static boolean sourcePool(ServerLevel world,BlockPos anchor){
        if(!frame(world,anchor))return false;
        for(var off:WATER){var s=world.getBlockState(anchor.offset(off));if(!s.is(Blocks.WATER)||!s.getFluidState().isSource())return false;}
        return true;
    }
    public static boolean active(ServerLevel world,BlockPos anchor){
        if(!frame(world,anchor))return false;
        for(int i=0;i<4;i++){var s=world.getBlockState(anchor.offset(WATER.get(i)));if(!s.is(AmethystPortalContent.PORTAL.get())||s.getValue(AmethystPortalBlock.QUADRANT)!=i)return false;}
        return true;
    }
    public static void open(ServerLevel world,BlockPos anchor){
        for(int i=0;i<4;i++)world.setBlock(anchor.offset(WATER.get(i)),AmethystPortalContent.PORTAL.get().defaultBlockState().setValue(AmethystPortalBlock.QUADRANT,i),2);
        for(var off:WATER){var p=anchor.offset(off);world.blockUpdated(p,AmethystPortalContent.PORTAL.get());world.scheduleTick(p,AmethystPortalContent.PORTAL.get(),20);}
    }
    public static void close(ServerLevel world,BlockPos anchor){
        for(var off:WATER){var p=anchor.offset(off);if(world.getBlockState(p).is(AmethystPortalContent.PORTAL.get()))world.setBlock(p,Blocks.WATER.defaultBlockState(),3);}
    }
    public static boolean tryActivate(ServerLevel world,ItemEntity dirt){
        if(!dirt.isAlive()||!dirt.getItem().is(Items.DIRT)||dirt.getItem().isEmpty())return false;
        // A floating dropped item can bob just above the fluid cell; allow that height only.
        var at=dirt.blockPosition();
        for(int dy:new int[]{0,-1})for(int dx=-1;dx<=0;dx++)for(int dz=-1;dz<=0;dz++){
            var anchor=at.offset(dx,dy,dz);
            if(dirt.getX()<anchor.getX()||dirt.getX()>=anchor.getX()+2||dirt.getZ()<anchor.getZ()||dirt.getZ()>=anchor.getZ()+2
                ||dirt.getY()>anchor.getY()+1.3||!sourcePool(world,anchor))continue;
            open(world,anchor);if(!active(world,anchor)){close(world,anchor);return false;}
            dirt.getItem().shrink(1);if(dirt.getItem().isEmpty())dirt.discard();else dirt.setItem(dirt.getItem().copy());
            world.playSound(null,anchor,SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.BLOCKS,1.2f,.7f);
            world.playSound(null,anchor,SoundEvents.PORTAL_TRIGGER,SoundSource.BLOCKS,.45f,1.4f);
            world.sendParticles(SPARK,anchor.getX()+1,anchor.getY()+1,anchor.getZ()+1,60,.8,.35,.8,.03);
            return true;
        }return false;
    }
    @SubscribeEvent public static void offered(EntityTickEvent.Post event){
        if(event.getEntity() instanceof ItemEntity item&&item.tickCount%5==0&&item.level() instanceof ServerLevel world&&item.getItem().is(Items.DIRT))tryActivate(world,item);
    }
    @SubscribeEvent public static void smelted(net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemSmeltedEvent event){
        if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer p&&event.getSmelting().is(AmethystPortalContent.FIBER_ITEM.get())&&!p.getPersistentData().getBoolean("academy_amethyst_portal_guide")){
            p.getPersistentData().putBoolean("academy_amethyst_portal_guide",true);p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("academy.city.portal.guide"));
        }
    }
}

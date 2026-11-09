package cn.academy.port.fleet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.*;

/** One craftable deployment crate produces one persistent vehicle. */
public final class VehicleDeployItem extends Item {
    public final int variant;
    public VehicleDeployItem(int variant){super(new Properties().stacksTo(1));this.variant=variant;}
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();if(player==null)return InteractionResult.PASS;
        var world=context.getLevel();if(world.isClientSide)return InteractionResult.SUCCESS;
        var car=InteractionLab.CAR.get().create(world);if(car==null)return InteractionResult.FAIL;car.variant(variant);
        Vec3 p=context.getClickLocation().add(0,.02,0);float yaw=Math.round(player.getYRot()/90)*90;
        car.moveTo(p.x,p.y,p.z,yaw,0);
        if(!world.getWorldBorder().isWithinBounds(BlockPos.containing(p))||!car.canDriveAt(p,yaw)||!world.getEntities(car,car.getBoundingBox().inflate(.15)).isEmpty()){
            player.displayClientMessage(Component.translatable("academy.fleet.deploy_blocked"),true);car.discard();return InteractionResult.FAIL;
        }
        if(!world.addFreshEntity(car))return InteractionResult.FAIL;
        if(!player.getAbilities().instabuild)context.getItemInHand().shrink(1);
        player.displayClientMessage(Component.translatable("academy.fleet.vehicle_controls"),true);return InteractionResult.CONSUME;
    }
}

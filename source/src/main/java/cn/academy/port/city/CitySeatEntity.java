package cn.academy.port.city;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** An occupied chair only; no idle entity per piece of furniture. */
public final class CitySeatEntity extends Entity {
    public CitySeatEntity(EntityType<?> type,Level w){super(type,w);setNoGravity(true);noPhysics=true;}
    public static boolean sit(Player p,BlockPos pos){
        if(p.level().isClientSide||p.isPassenger()||!p.isAlive()||p.distanceToSqr(Vec3.atCenterOf(pos))>16)return false;
        if(!p.level().getEntitiesOfClass(CitySeatEntity.class,new AABB(pos).inflate(.1)).isEmpty())return false;
        CitySeatEntity seat=CityInteriorContent.SEAT.get().create(p.level());if(seat==null)return false;
        float facing=p.level().getBlockState(pos).getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot();seat.setYRot(facing);p.setYRot(facing);p.setXRot(0);
        seat.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);if(!p.level().addFreshEntity(seat))return false;
        if(!p.startRiding(seat)){seat.discard();return false;}return true;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){}
    @Override protected void readAdditionalSaveData(CompoundTag t){}
    @Override protected void addAdditionalSaveData(CompoundTag t){}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public void tick(){super.tick();if(!level().isClientSide){var b=level().getBlockState(blockPosition()).getBlock();if(getPassengers().isEmpty()||!(b==CityInteriorContent.block("chair")||b==CityInteriorContent.block("bench")||b==CityInteriorContent.block("sofa"))){ejectPassengers();discard();}}}
    // A vanilla player's rendered sitting hip is about .70 above its feet; the cushion is .5625 high.
    @Override protected Vec3 getPassengerAttachmentPoint(Entity e,EntityDimensions d,float partial){return new Vec3(0,e.getVehicleAttachmentPoint(this).y-.14,0);}
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity p){return CityElevators.safeExit(level(),blockPosition(),p).orElse(position().add(0,1,0));}
}

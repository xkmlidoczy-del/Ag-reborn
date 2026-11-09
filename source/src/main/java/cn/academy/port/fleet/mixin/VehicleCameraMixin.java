package cn.academy.port.fleet.mixin;
import cn.academy.port.fleet.*;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class VehicleCameraMixin {
 @Shadow protected abstract void setPosition(double x,double y,double z);
 @Inject(method="setup",at=@At("TAIL"))
 private void aclab$driverEye(BlockGetter level,Entity entity,boolean detached,boolean mirrored,float partial,CallbackInfo ci){
  if(!detached&&entity.getVehicle() instanceof LabVehicle vehicle){
   var cfg=vehicle.config();int seat=vehicle.seatIndex(entity);var eye=cfg.eye.add(cfg.seats.get(Math.min(seat,cfg.seats.size()-1)).subtract(cfg.seats.get(0)));
   var pos=LabVehicle.localToWorld(eye,vehicle.getPosition(partial),net.minecraft.util.Mth.rotLerp(partial,vehicle.yRotO,vehicle.getYRot()),vehicle.renderPitch(partial),vehicle.renderRoll(partial));setPosition(pos.x,pos.y,pos.z);
  }
 }
}

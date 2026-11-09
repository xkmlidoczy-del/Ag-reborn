package cn.academy.port.fleet.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import cn.academy.port.fleet.LabVehicle;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A local rendering adjustment so a seated block avatar fits inside the sedan cabin. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerScaleMixin {
    @Inject(method="scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V",at=@At("TAIL"))
    private void aclab$seatScale(AbstractClientPlayer player,PoseStack stack,float partial,CallbackInfo ci){
        if(player.getVehicle() instanceof LabVehicle v && v.variant()!=2)stack.scale(.69f,.69f,.69f);
    }
}

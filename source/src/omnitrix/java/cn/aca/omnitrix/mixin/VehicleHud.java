package cn.aca.omnitrix.mixin;

import cn.aca.omnitrix.PursuitBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Gui.class)
abstract class VehicleHud {
    @Inject(method = "getPlayerVehicleWithHealth", at = @At("RETURN"), cancellable = true)
    private void omnitrix$hideProxyHearts(CallbackInfoReturnable<LivingEntity> callback) {
        if (callback.getReturnValue() instanceof PursuitBody) callback.setReturnValue(null);
    }
    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void omnitrix$skipProxyHint(Component message, boolean animate, CallbackInfo callback) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getVehicle() instanceof PursuitBody
                && message.getContents() instanceof TranslatableContents text && text.getKey().equals("mount.onboard")) {
            callback.cancel();
        }
    }
}

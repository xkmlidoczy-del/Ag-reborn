package cn.aca.omnitrix.mixin;

import cn.aca.omnitrix.client.AscalonRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
abstract class PlayerFlightPose {
    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("TAIL"))
    private void omnitrix$viltrumSetupRotations(AbstractClientPlayer player, PoseStack pose, float bob,
                                                float yBodyRot, float partialTick, float scale, CallbackInfo ci) {
        AscalonRenderer.applyViltrumBodyTransform(player, pose, bob, yBodyRot, partialTick);
    }
}

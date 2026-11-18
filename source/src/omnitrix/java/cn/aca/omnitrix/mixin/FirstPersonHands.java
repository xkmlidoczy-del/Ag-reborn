package cn.aca.omnitrix.mixin;

import cn.aca.omnitrix.client.WatchRender;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class FirstPersonHands {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void omnitrix$hands(float partial, PoseStack pose, MultiBufferSource.BufferSource buffers,
                               LocalPlayer p, int light, CallbackInfo ci) {
        if (WatchRender.firstPerson((ItemInHandRenderer)(Object)this, p, pose, buffers, light, partial)) ci.cancel();
    }

    @Inject(method = "renderHandsWithItems", at = @At("TAIL"))
    private void omnitrix$idle(float partial, PoseStack pose, MultiBufferSource.BufferSource buffers,
                              LocalPlayer p, int light, CallbackInfo ci) {
        WatchRender.idleWrist(p, pose, buffers, light, partial);
    }
}

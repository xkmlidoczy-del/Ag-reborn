package cn.aca.omnitrix.mixin;

import cn.aca.omnitrix.WristService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "cn.academy.port.AcademyGameplay", remap = false)
abstract class AcademyRequest {
    @Inject(method = "requestFromClient", at = @At("HEAD"), cancellable = true, require = 0)
    private static void omnitrix$whileTakenOver(ServerPlayer player, @Coerce Object request, CallbackInfoReturnable<Boolean> cir) {
        if (player != null && WristService.controlled(player)) cir.setReturnValue(false);
    }
}

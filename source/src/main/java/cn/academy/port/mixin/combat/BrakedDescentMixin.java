package cn.academy.port.mixin.combat;

import cn.academy.port.canon.CanonFloatExpansion;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class BrakedDescentMixin {
    @Shadow public ServerPlayer player;

    @ModifyExpressionValue(method = "tick", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;clientIsFloating:Z"),
            require = 1, allow = 1)
    private boolean academy$checkBrakedDescent(boolean floating) {
        return floating && !CanonFloatExpansion.isDescendingUnderAirBrake(player);
    }
}

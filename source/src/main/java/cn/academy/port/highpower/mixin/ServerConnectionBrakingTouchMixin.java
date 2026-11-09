package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.BrakingTouchLandingProof;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerConnectionBrakingTouchMixin {
    @WrapMethod(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V")
    private void academy$brakingPacket(ServerboundMovePlayerPacket packet, Operation<Void> original) {
        BrakingTouchLandingProof.nativePacket((ServerGamePacketListenerImpl)(Object)this, packet, original);
    }
    @WrapOperation(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"),
            require = 1, expect = 1, allow = 1)
    private void academy$brakingMove(ServerPlayer player, MoverType type, Vec3 movement, Operation<Void> original) {
        BrakingTouchLandingProof.nativeMove((ServerGamePacketListenerImpl)(Object)this, player, type, movement, original);
    }
    @WrapOperation(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;doCheckFallDamage(DDDZ)V", ordinal = 0),
            require = 1, expect = 1, allow = 1)
    private void academy$brakingAcceptedFall(ServerPlayer player, double x, double y, double z, boolean grounded, Operation<Void> original) {
        BrakingTouchLandingProof.nativeAcceptedFall((ServerGamePacketListenerImpl)(Object)this, player, x, y, z, grounded, original);
    }
}

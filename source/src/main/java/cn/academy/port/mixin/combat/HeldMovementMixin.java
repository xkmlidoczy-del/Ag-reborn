package cn.academy.port.mixin.combat;

import cn.academy.port.api.CombatControl;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class HeldMovementMixin {
    @Shadow public ServerPlayer player;
    @Shadow public abstract void teleport(double x, double y, double z, float yaw, float pitch);

    @Unique private long academy$nextControlCorrection;

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void academy$holdPosition(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (!packet.hasPosition() || !CombatControl.held(player)) return;
        double x = packet.getX(player.getX()), y = packet.getY(player.getY()), z = packet.getZ(player.getZ());
        float yaw = packet.getYRot(player.getYRot()), pitch = packet.getXRot(player.getXRot());
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
        if (player.distanceToSqr(x, y, z) < 1.0e-8) return;
        long now = player.level().getGameTime();
        if (now >= academy$nextControlCorrection) {
            academy$nextControlCorrection = now + 4;
            teleport(player.getX(), player.getY(), player.getZ(), Mth.wrapDegrees(yaw), Mth.wrapDegrees(pitch));
        }
        ci.cancel();
    }
}

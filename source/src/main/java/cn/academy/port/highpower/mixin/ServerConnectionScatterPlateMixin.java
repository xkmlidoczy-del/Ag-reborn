package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.ScatterPlates;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ordinary walking/looking keeps historical crossings; a native teleport retires the activation. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerConnectionScatterPlateMixin implements cn.academy.port.highpower.NativeWindPressureLiftTeleportAccess {
    @Shadow public ServerPlayer player;
    @Inject(method = "teleport(DDDFFLjava/util/Set;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void academy$clearBeforeTeleport(double x, double y, double z, float yaw, float pitch, Set<RelativeMovement> relative, CallbackInfo ci) { ScatterPlates.abort(player); cn.academy.port.highpower.DissipationCurtain.abort(player); cn.academy.port.highpower.ThermalShell.abort(player); cn.academy.port.highpower.WindPressureLift.abort(player); }
}

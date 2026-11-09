package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.EmergencyReturnConnectionCycle;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Three observations of the pinned native cycle; each original operation executes once. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerConnectionEmergencyReturnMixin {
    @WrapMethod(method = "tick()V")
    private void academy$observeConnectionTick(Operation<Void> original) {
        boolean completed = false;
        try { original.call(); completed = true; }
        finally { EmergencyReturnConnectionCycle.finished((ServerGamePacketListenerImpl)(Object)this, completed); }
    }

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;doTick()V"), require = 1, expect = 1, allow = 1)
    private void academy$observeNativeDoTick(ServerPlayer player, Operation<Void> original) {
        EmergencyReturnConnectionCycle.beforeDoTick((ServerGamePacketListenerImpl)(Object)this, player);
        boolean completed = false;
        try { original.call(player); completed = true; }
        finally { EmergencyReturnConnectionCycle.afterDoTick((ServerGamePacketListenerImpl)(Object)this, player, completed); }
    }

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;absMoveTo(DDDFF)V"), require = 1, expect = 1, allow = 1)
    private void academy$observeNativeRestore(ServerPlayer player, double x, double y, double z,
                                              float yaw, float pitch, Operation<Void> original) {
        EmergencyReturnConnectionCycle.beforeRestore((ServerGamePacketListenerImpl)(Object)this, player, x, y, z);
        boolean completed = false;
        try { original.call(player, x, y, z, yaw, pitch); completed = true; }
        finally { EmergencyReturnConnectionCycle.afterRestore((ServerGamePacketListenerImpl)(Object)this, player, completed); }
    }
}

package cn.academy.port.client.highpower.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only native auto-jump observation. No setter, injection, visibility change, or movement hook. */
@Mixin(targets = "net.minecraft.client.player.LocalPlayer")
public interface WindPressureLiftAutoJumpAccessor {
    @Accessor("autoJumpTime")
    int academy$windPressureLiftAutoJumpTime();
}

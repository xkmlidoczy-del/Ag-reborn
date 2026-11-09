package cn.academy.port.client.highpower.mixin;

import cn.academy.port.client.WindPressureLiftNativeTravel;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Three exact client call boundaries; the bridge delegates every original operation once. */
@Mixin(Minecraft.class)
abstract class WindPressureLiftMinecraftMixin {
    @WrapMethod(method = "runTick(Z)V")
    private void academy$windLiftRun(boolean ticking, Operation<Void> original) {
        WindPressureLiftNativeTravel.run((Minecraft)(Object)this, ticking, original);
    }
    @Redirect(method = "runTick(Z)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/DeltaTracker$Timer;advanceTime(JZ)I"), require = 1, expect = 1, allow = 1)
    private static int academy$windLiftPlan(DeltaTracker.Timer timer, long now, boolean ticking) {
        return WindPressureLiftNativeTravel.plan(timer, now, ticking);
    }
    @Redirect(method = "runTick(Z)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;tick()V"), require = 1, expect = 1, allow = 1)
    private static void academy$windLiftStep(Minecraft minecraft) {
        WindPressureLiftNativeTravel.step(minecraft);
    }
}

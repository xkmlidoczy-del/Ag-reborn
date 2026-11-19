package cn.academy.port.client.highpower.mixin;

import cn.academy.port.client.HighPowerRecoveryHud;
import cn.academy.port.client.highpower.RecoveryHudHeartsAccess;
import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Replace only the normalized HP witness, preserving vanilla absorption and all separate native layers. */
@Mixin(Gui.class)
abstract class RecoveryHudHeartMixin implements RecoveryHudHeartsAccess {
    // Resolve all three native reads consistently at draw time; never replace the stored full fallback.
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "renderOverlayMessage(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Gui;overlayMessageString:Lnet/minecraft/network/chat/Component;", opcode = 180),
            require = 3, expect = 3, allow = 3)
    private net.minecraft.network.chat.Component academy$compactOwnSummary(net.minecraft.network.chat.Component input) {
        return HighPowerRecoveryHud.compactActionBar(input);
    }

    // Bound vanilla's layout before it computes heart rows or moves the armor layer.
    @ModifyExpressionValue(method = "renderHealthLevel",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"), require = 1)
    private float academy$boundHeartLayout(float measured) {
        return measured > 80 ? 20 : measured;
    }

    @ModifyExpressionValue(method = "renderHealthLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getHealth()F"), require = 1)
    private float academy$boundHeartBlink(float health) {
        return Math.min(health, 80);
    }

    @ModifyArgs(method = "renderHealthLevel(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;renderHearts(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V"),
            require = 1, expect = 1, allow = 1)
    private void academy$replaceOnlyNativeHp(Args args) {
        Player player = args.get(1);
        boolean academyHud = HighPowerRecoveryHud.replacesNativeHearts(player);
        if (!academyHud && player.getMaxHealth() <= 80) return;
        if (!academyHud) {
            GuiGraphics graphics = args.get(0);
            int x = args.get(2), y = args.get(3);
            float ratio = Mth.clamp(player.getHealth() / player.getMaxHealth(), 0, 1);
            int width = 81;
            graphics.fill(x, y + 1, x + width, y + 9, 0xdc101b22);
            graphics.fill(x, y + 8, x + Math.round(width * ratio), y + 9, 0xffb0e5ff);
            var font = Minecraft.getInstance().font;
            String hp = HighPowerRecoveryHud.compactNativeHealth(player.getHealth());
            graphics.drawString(font, hp, x + 2, y, 0xffe4edf2, false);
        }
        args.set(5, -1); // No native regeneration-bounce index.
        args.set(6, 0F); // No native maximum HP containers.
        args.set(7, 0);  // No current native proxy HP fill.
        args.set(8, 0);  // No previous native proxy HP blink fill.
        // args[9] absorption, position, spacing and hardcore style remain native.
    }

    @ModifyConstant(method = "renderHearts(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V",
            constant = @Constant(intValue = 4), require = 1, expect = 1, allow = 1)
    private int academy$preserveAbsorptionJitter(int threshold, net.minecraft.client.gui.GuiGraphics graphics, Player player,
            int x, int y, int rowSpacing, int regenerationIndex, float maximum, int current, int previous, int absorption, boolean blinking) {
        if (maximum != 0F || current != 0 || previous != 0 || !HighPowerRecoveryHud.replacesNativeHearts(player)) return threshold;
        return HighPowerRecoveryHudModel.absorptionJitterThreshold(net.minecraft.util.Mth.ceil(player.getHealth()));
    }
}

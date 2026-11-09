package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.BorrowedNeedleEscrow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No death-event interception: these callbacks run only within the committed native loot path. */
@Mixin(Player.class)
abstract class PlayerBorrowedNeedleDeathMixin {
    // require=0 lets missing optional seams leave ordinary death intact. The plugin proves
    // exactly one installation of EACH callback before it admits any borrowed item.
    @Inject(method = "dropEquipment()V", at = @At("HEAD"), require = 0, expect = 1, allow = 1)
    private void academy$borrowedNeedleBeginDeath(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer owner && NativeBorrowedNeedleMixinPlugin.isReviewedDeathDropSupported()) {
            try {
                BorrowedNeedleEscrow.beginNativeDeath(owner);
            } catch (RuntimeException | LinkageError failure) {
                NativeBorrowedNeedleMixinPlugin.disableAfterCallbackFailure(failure);
            }
        }
    }

    @Inject(method = "dropEquipment()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;dropAll()V", shift = At.Shift.BEFORE),
            require = 0, expect = 1, allow = 1)
    private void academy$borrowedNeedleDeathDrop(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer owner && NativeBorrowedNeedleMixinPlugin.isReviewedDeathDropSupported()) {
            try {
                BorrowedNeedleEscrow.deathDrop(owner);
            } catch (RuntimeException | LinkageError failure) {
                NativeBorrowedNeedleMixinPlugin.disableAfterCallbackFailure(failure);
            }
        }
    }
}

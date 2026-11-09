package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.MagneticItemProvenance;
import cn.academy.port.highpower.NativeItemMergeAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records provenance before native stack mutation; never cancels or replaces a merge. */
@Mixin(ItemEntity.class)
abstract class ItemEntityProvenanceMixin implements NativeItemMergeAccess {
    @Override public int academy$nativeItemMergeContractVersion() {
        // The plugin changes this constant only after inspecting the applied hook.
        return 0;
    }

    @Inject(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = false, require = 1, expect = 1, allow = 1)
    private static void academy$beforeNativeMerge(ItemEntity destination, ItemStack destinationStack,
                                                  ItemEntity source, ItemStack sourceStack, CallbackInfo callback) {
        MagneticItemProvenance.beforeMerge(destination, destinationStack, source, sourceStack);
    }
}

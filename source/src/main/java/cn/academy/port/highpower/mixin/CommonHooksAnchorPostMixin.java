package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.AnchorDamagePostProof;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observe the actual object constructed by the reviewed native Post hook; never alter dispatch. */
@Mixin(CommonHooks.class)
abstract class CommonHooksAnchorPostMixin {
    @WrapOperation(method = "onLivingDamagePost(Lnet/minecraft/world/entity/LivingEntity;Lnet/neoforged/neoforge/common/damagesource/DamageContainer;)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"),
            require = 1, expect = 1, allow = 1)
    private static Event anchor$nativeBusPost(IEventBus bus, Event event, Operation<Event> original,
                                            LivingEntity entity, DamageContainer container) {
        return AnchorDamagePostProof.nativeBusPost(bus, event, original, entity, container);
    }
}

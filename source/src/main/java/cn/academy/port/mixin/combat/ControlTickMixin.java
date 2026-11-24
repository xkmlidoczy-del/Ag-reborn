package cn.academy.port.mixin.combat;

import cn.academy.port.api.ControlRelations;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerLevel.class)
abstract class ControlTickMixin {
    @WrapMethod(method = "tickNonPassenger")
    private void academy$activeActor(Entity entity, Operation<Void> original) {
        ControlRelations.tick(entity, () -> original.call(entity));
    }

    @WrapMethod(method = "tickPassenger")
    private void academy$passengerActor(Entity vehicle, Entity rider, Operation<Void> original) {
        ControlRelations.tick(rider, () -> original.call(vehicle, rider));
    }
}

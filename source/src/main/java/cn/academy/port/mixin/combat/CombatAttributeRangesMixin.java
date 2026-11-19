package cn.academy.port.mixin.combat;

import cn.academy.port.highpower.CombatAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

@Mixin(Attributes.class)
abstract class CombatAttributeRangesMixin {
    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/attributes/Attributes;register(Ljava/lang/String;Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lnet/minecraft/core/Holder;"))
    private static Holder<Attribute> academy$extendCombatRanges(String name, Attribute original,
                                                                 Operation<Holder<Attribute>> register) {
        double maximum = switch (name) {
            case "generic.max_health", "generic.attack_damage" -> CombatAttributes.LIMIT;
            case "generic.armor", "generic.armor_toughness" -> 100;
            default -> 0;
        };
        if (maximum == 0 || !(original instanceof RangedAttribute range)) return register.call(name, original);
        return register.call(name, new RangedAttribute(range.getDescriptionId(), range.getDefaultValue(),
                range.getMinValue(), maximum).setSyncable(true));
    }
}

package cn.academy.port.mixin.combat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.titanecology.EcoMobEntity;
import dev.titanecology.TransformerMobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TransformerMobEntity.class, remap = false)
abstract class EcologyMorphArmorMixin {
    // V4.8's own base + morph armor used vanilla's 30-point range. Other sources keep ACA's full range.
    @ModifyExpressionValue(method = "applyAltModifiers", at = @At(value = "INVOKE",
            target = "Ldev/titanecology/EcoSpeciesProfile$MorphData;altArmorBonus()D", ordinal = 1))
    private double academy$preserveNativeMorphArmor(double bonus) {
        double base = ((EcoMobEntity) (Object) this).profile().armor();
        return Math.min(bonus, Math.max(0.0, 30.0 - base));
    }
}

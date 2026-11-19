package cn.academy.port.highpower;

import java.math.BigDecimal;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Physical attributes use the vanilla registry; spell strength is an additional entity attribute. */
public final class CombatAttributes {
    public static final double LIMIT = 1e20;
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, "academy");
    public static final DeferredHolder<Attribute, Attribute> ABILITY_POWER = ATTRIBUTES.register("ability_power",
            () -> new RangedAttribute("attribute.name.academy.ability_power", 0, 0, LIMIT).setSyncable(true));

    private CombatAttributes() {}

    public static void register(IEventBus bus) {
        ATTRIBUTES.register(bus);
        bus.addListener(CombatAttributes::attach);
    }

    private static void attach(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, ABILITY_POWER);
        event.add(cn.academy.port.city.CityNpcContent.NPC.get(), ABILITY_POWER);
        for (var titan : cn.academy.port.titan.TitanBlocks.TYPES.values()) {
            event.add(titan.get(), ABILITY_POWER, cn.academy.port.titan.TitanProfiles.get(titan.getId().getPath()).damageUnit().doubleValue());
        }
    }

    public static BigDecimal installSkillPower(net.minecraft.world.entity.LivingEntity entity, BigDecimal base) {
        var instance = entity.getAttribute(ABILITY_POWER);
        if (instance == null) return base;
        double desired = base.doubleValue();
        if (instance.getBaseValue() != desired) instance.setBaseValue(desired);
        return BigDecimal.valueOf(instance.getValue());
    }
    public static BigDecimal skillPower(net.minecraft.world.entity.LivingEntity entity) {
        return BigDecimal.valueOf(entity.getAttributeValue(ABILITY_POWER));
    }

}

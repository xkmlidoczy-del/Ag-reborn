package cn.academy.port.city;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CityNpcContent {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, "academy");
    public static final DeferredHolder<EntityType<?>, EntityType<CityNpcEntity>> NPC = TYPES.register("city_npc",
            () -> EntityType.Builder.<CityNpcEntity>of(CityNpcEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).clientTrackingRange(8).build("academy:city_npc"));

    private CityNpcContent() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(CityNpcContent::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(NPC.get(), Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED,0.27).add(Attributes.FOLLOW_RANGE,28).add(Attributes.ATTACK_DAMAGE,2).build());
    }
}

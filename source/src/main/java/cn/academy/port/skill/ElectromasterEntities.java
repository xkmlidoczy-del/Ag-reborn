package cn.academy.port.skill;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The moving block is a real server entity, not a disguised generic projectile. */
public final class ElectromasterEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,"academy");
    public static final DeferredHolder<EntityType<?>,EntityType<MagneticBlockEntity>> MAGNETIC_BLOCK=ENTITIES.register("magnetic_block",()->EntityType.Builder.<MagneticBlockEntity>of(MagneticBlockEntity::new,MobCategory.MISC).sized(1,1).clientTrackingRange(64).updateInterval(1).build("academy:magnetic_block"));
    private ElectromasterEntities() {}
    public static void register(IEventBus bus){ENTITIES.register(bus);}
}

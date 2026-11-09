package cn.academy.port.train;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TrainContent {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, "academy");
    public static final DeferredHolder<EntityType<?>, EntityType<TrainCarEntity>> CAR = TYPES.register("train_car",
            () -> EntityType.Builder.<TrainCarEntity>of(TrainCarEntity::new, MobCategory.MISC).sized(3.4f, 3.7f)
                    .noSave().fireImmune().clientTrackingRange(24).updateInterval(1).build("academy:train_car"));

    private TrainContent() {}

    public static void register(IEventBus bus) { TYPES.register(bus); }
}

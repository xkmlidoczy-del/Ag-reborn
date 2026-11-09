package cn.academy.port.highpower;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Closed native projectile types; each skill has its own physical custody domain. */
public final class TeleporterNeedleEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, "academy");
    public static final DeferredHolder<EntityType<?>, EntityType<DisplacedNeedleEntity>> DISPLACED_NEEDLE =
        ENTITIES.register("displaced_needle", () -> EntityType.Builder
            .<DisplacedNeedleEntity>of(DisplacedNeedleEntity::new, MobCategory.MISC)
            .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(1)
            .build("academy:displaced_needle"));
    public static final DeferredHolder<EntityType<?>, EntityType<DisplacedNeedleEntity>> CROSS_NEEDLE =
        ENTITIES.register("cross_needle", () -> EntityType.Builder
            .<DisplacedNeedleEntity>of(DisplacedNeedleEntity::new, MobCategory.MISC)
            .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(1)
            .build("academy:cross_needle"));

    public static final DeferredHolder<EntityType<?>, EntityType<DisplacedNeedleEntity>> INTERCEPTION_NEEDLE =
        ENTITIES.register("interception_needle", () -> EntityType.Builder
            .<DisplacedNeedleEntity>of(DisplacedNeedleEntity::new, MobCategory.MISC)
            .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(1)
            .build("academy:interception_needle"));

    public static final DeferredHolder<EntityType<?>, EntityType<DisplacedNeedleEntity>> CORRIDOR_NEEDLE =
        ENTITIES.register("corridor_needle", () -> EntityType.Builder
            .<DisplacedNeedleEntity>of(DisplacedNeedleEntity::new, MobCategory.MISC)
            .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(1)
            .build("academy:corridor_needle"));

    /** NEW351 foundation only: no skill or public command can create this lease yet. */
    public static final DeferredHolder<EntityType<?>, EntityType<BorrowedNeedleEntity>> BORROWED_NEEDLE =
        ENTITIES.register("borrowed_needle", () -> EntityType.Builder
            .<BorrowedNeedleEntity>of(BorrowedNeedleEntity::new, MobCategory.MISC)
            .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(1).noSave()
            .build("academy:borrowed_needle"));

    private TeleporterNeedleEntities() { }
    public static void register(IEventBus bus) { ENTITIES.register(bus); }
}

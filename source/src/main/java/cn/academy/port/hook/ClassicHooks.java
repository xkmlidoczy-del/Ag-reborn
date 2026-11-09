/* AcademyCraft1.0.7 magnetic hook registration adaptation. GPLv3; see NOTICE. */
package cn.academy.port.hook;

import cn.academy.port.AcademyCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ClassicHooks {
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,"academy");
    public static final DeferredItem<MagHookItem> MAGHOOK=AcademyCraft.ITEMS.register("maghook",()->new MagHookItem(new net.minecraft.world.item.Item.Properties()));
    public static final DeferredHolder<EntityType<?>,EntityType<MagHookEntity>> ENTITY=ENTITIES.register("maghook",()->EntityType.Builder.<MagHookEntity>of(MagHookEntity::new,MobCategory.MISC).sized(.5F,.5F).clientTrackingRange(64).updateInterval(1).build("academy:maghook"));
    public static void register(IEventBus bus){ENTITIES.register(bus);}
    private ClassicHooks(){}
}

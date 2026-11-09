/* AcademyCraft1.0.7 Silbarn registration adaptation, GPLv3; see NOTICE. */
package cn.academy.port.skill;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
public final class MeltdownerLateEntities {
 private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,"academy");
 private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("academy");
 public static final DeferredHolder<EntityType<?>,EntityType<SilbarnEntity>> SILBARN_ENTITY=ENTITIES.register("silbarn",()->EntityType.Builder.<SilbarnEntity>of(SilbarnEntity::new,MobCategory.MISC).sized(.4F,.4F).clientTrackingRange(64).updateInterval(1).build("academy:silbarn"));
 public static final DeferredItem<Item> SILBARN=ITEMS.register("silbarn",()->new SilbarnItem(new Item.Properties()));
 private MeltdownerLateEntities(){}public static void register(IEventBus bus){ENTITIES.register(bus);ITEMS.register(bus);}
}

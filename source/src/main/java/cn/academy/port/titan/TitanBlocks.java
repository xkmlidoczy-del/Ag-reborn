package cn.academy.port.titan;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

public final class TitanBlocks {
    public static final String MODID = "academy";
    public static final String[] IDS = {"clockwork_rex", "myco_frog", "lava_rex", "ironback", "frost_bastion", "grove_titan", "crystal_mantis", "dune_scorpion", "storm_drake", "ossuary_dragon", "tidal_sail", "astral_moth"};
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<TitanEntity>>> TYPES = new LinkedHashMap<>();
    static {
        for (String id : IDS) {
            var type = ENTITIES.register(id, () -> {
                // A single collision volume cannot cover all long tails and legs.
                var builder=EntityType.Builder.<TitanEntity>of(TitanEntity::new, MobCategory.MONSTER)
                    .sized(id.equals("myco_frog") ? 18.0f : id.equals("lava_rex") || id.equals("frost_bastion") || id.equals("storm_drake") || id.equals("ossuary_dragon") ? 12.0f : id.equals("grove_titan") ? 8.0f : id.equals("crystal_mantis") || id.equals("dune_scorpion") ? 14.0f : 22.0f,
                        id.equals("myco_frog") ? 24.0f : 30.0f)
                    .clientTrackingRange(16).updateInterval(1);
                if(id.equals("lava_rex"))builder.fireImmune();
                return builder.build(MODID + ":" + id);
            });
            TYPES.put(id, type);
            ITEMS.register(id + "_spawn_egg", () -> new DeferredSpawnEggItem(type, 0x202934, TitanProfiles.get(id).color(), new Item.Properties()));
        }
    }
    public TitanBlocks(IEventBus bus,ModContainer container) {
        ENTITIES.register(bus); ITEMS.register(bus);TitanParticleTypes.TYPES.register(bus);TitanSounds.SOUNDS.register(bus);
        container.registerConfig(ModConfig.Type.SERVER,TitanConfig.SPEC,"academy-city-titans-server.toml");
        container.registerConfig(ModConfig.Type.CLIENT,TitanVisualConfig.SPEC,"academy-city-titans-client.toml");
        bus.addListener(this::attributes); bus.addListener(this::creative);bus.addListener(this::spawnPlacements);
    }
    private void attributes(EntityAttributeCreationEvent event) {
        TYPES.forEach((id,t) -> event.put(t.get(), TitanEntity.attributes(TitanProfiles.get(id)).build()));
    }
    private void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) ITEMS.getEntries().forEach(h -> event.accept(h.get()));
    }
    private void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        TYPES.forEach((id,t)->event.register(t.get(),id.equals("tidal_sail")?SpawnPlacementTypes.IN_WATER:SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,(type,level,reason,pos,random)-> {
                if(reason!=MobSpawnType.NATURAL && reason!=MobSpawnType.CHUNK_GENERATION)return true;
                return TitanConfig.NATURAL_SPAWNS.get() && cn.academy.port.titan.CityTitanZones.allowed(level.getLevel(),pos,type.getWidth());
            },RegisterSpawnPlacementsEvent.Operation.REPLACE));
    }
}

package cn.academy.port.city;

import cn.academy.port.city.portal.CityPortalTravel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/** The urban dimensions share vanilla plains; their population has separate admission rules. */
@EventBusSubscriber(modid = "academy")
public final class CityFaunaSpawns {
    private static final ResourceKey<Level> LEGACY_CITY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("academy", "story_city"));

    private CityFaunaSpawns() {}

    public static boolean suppresses(ResourceKey<Level> dimension, ResourceLocation entityType, MobSpawnType reason) {
        return (reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION)
                && (CityPortalTravel.CITY.equals(dimension) || LEGACY_CITY.equals(dimension))
                && entityType.getNamespace().equals("aca_fauna");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void restrictNaturalSpawns(MobSpawnEvent.SpawnPlacementCheck event) {
        if (suppresses(event.getLevel().getLevel().dimension(), BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntityType()), event.getSpawnType()))
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }
}

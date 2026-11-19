package cn.academy.port.city;

import cn.academy.port.city.portal.CityPortalTravel;
import dev.acafauna.core.FaunaConfig;
import dev.acafauna.core.FaunaCore;
import dev.acafauna.core.Species;
import dev.acafauna.entity.FaunaAnimal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Bounded groups near explorers, with separate admission for the city's fixed-biome map. */
@EventBusSubscriber(modid = "academy")
public final class CityFaunaSpawns {
    private static final ResourceKey<Level> LEGACY_CITY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("academy", "story_city"));
    private static final int INTERVAL = 100;
    private static final int SITE_ATTEMPTS = 16;
    private static final int MIN_DISTANCE = 24;
    private static final int LOCAL_RADIUS = 48;
    private static final List<Group> LAND = List.of(new Group("hedgehog", 1, 1), new Group("red_squirrel", 1, 2),
            new Group("woodpecker", 2, 2), new Group("raccoon", 2, 3));
    private static final List<Group> PARK = LAND.subList(0, 3);
    private static final List<Group> WETLAND = List.of(new Group("mallard_duck", 2, 3), new Group("beaver", 1, 2),
            new Group("pukeko", 2, 3));
    private static final List<Group> CITY_SHORE = WETLAND.subList(0, 2);

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

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel world) || world.getGameTime() % INTERVAL != 0
                || !ModList.get().isLoaded("aca_fauna") || !world.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                || !FaunaConfig.NATURAL.get()) return;
        CityGreyboxGenerator city = world.dimension().equals(CityPortalTravel.CITY)
                && world.getChunkSource().getGenerator() instanceof CityGreyboxGenerator generator ? generator : null;
        if (city == null && !world.dimension().equals(Level.OVERWORLD)) return;
        List<ServerPlayer> players = world.players().stream().filter(p -> p.isAlive() && !p.isSpectator()).toList();
        if (players.isEmpty()) return;
        int index = Math.floorMod(world.getGameTime() / INTERVAL, players.size());
        replenish(world, players.get(index).blockPosition(), players, city);
    }

    private static void replenish(ServerLevel world, BlockPos anchor, List<ServerPlayer> players, CityGreyboxGenerator city) {
        int cap = Math.min(city == null ? 8 : 6, FaunaConfig.LOCAL_CAP.get());
        int speciesCap = city == null ? 4 : 3;
        var nearby = new ArrayList<>(world.getEntitiesOfClass(FaunaAnimal.class,
                new AABB(anchor).inflate(112, 64, 112), FaunaAnimal::isAlive));
        if (count(nearby, anchor, null) >= cap) return;
        RandomSource random = world.getRandom();
        for (int attempt = 0; attempt < SITE_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = 27 + random.nextInt(23);
            BlockPos column = anchor.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
            BlockPos feet = findSurface(world, column, anchor, players);
            if (feet == null) continue;
            Site site = habitat(world, feet, city);
            if (site == null) continue;
            Group group = chooseGroup(world, site, feet, nearby, speciesCap, city == null);
            if (group == null) continue;
            int available = Math.min(cap - count(nearby, feet, null), speciesCap - count(nearby, feet, group.id()));
            if (available <= 0) continue;
            int desired = Math.min(available, group.minimum() + random.nextInt(group.maximum() - group.minimum() + 1));
            if (spawnGroup(world, group, feet, anchor, players, city, site.water() != null, nearby, cap, speciesCap, desired)) return;
        }
    }

    private static BlockPos findSurface(ServerLevel world, BlockPos column, BlockPos anchor, List<ServerPlayer> players) {
        if (world.getChunkSource().getChunkNow(column.getX() >> 4, column.getZ() >> 4) == null
                || !world.isPositionEntityTicking(column)) return null;
        BlockPos feet = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        if (Math.abs(feet.getY() - anchor.getY()) > 24) return null;
        for (ServerPlayer player : players) {
            double dx = feet.getX() + .5 - player.getX(), dz = feet.getZ() + .5 - player.getZ();
            if (dx * dx + dz * dz < MIN_DISTANCE * MIN_DISTANCE) return null;
        }
        return feet;
    }

    private static Site habitat(ServerLevel world, BlockPos feet, CityGreyboxGenerator city) {
        var ground = world.getBlockState(feet.below());
        if (!world.getFluidState(feet).isEmpty() || !world.canSeeSky(feet)) return null;
        if (city == null) {
            if (!ground.is(BlockTags.DIRT) && !ground.is(BlockTags.SAND) && !ground.is(Blocks.GRAVEL)) return null;
            BlockPos water = shoreWater(world, feet, null);
            return new Site(water == null ? LAND : WETLAND, water);
        }
        int x = feet.getX(), z = feet.getZ();
        var planned = CityPlan.sample(city.planSeed(), x, z);
        if (planned.district() == 21 && planned.kind() == CityPlan.Kind.TERRAIN
                && (ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(Blocks.GRAVEL))) {
            BlockPos water = shoreWater(world, feet, city);
            return water == null ? null : new Site(CITY_SHORE, water);
        }
        if (feet.getY() != city.getSeaLevel() + 1 || !ground.is(Blocks.GRASS_BLOCK)) return null;
        boolean park = planned.kind() == CityPlan.Kind.PARK
                || CityD7CentralGardens.at(city.planSeed(), x, z, planned) != null;
        return park ? new Site(PARK, null) : null;
    }

    private static BlockPos shoreWater(ServerLevel world, BlockPos feet, CityGreyboxGenerator city) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int distance = 1; distance <= 5; distance++) {
                BlockPos column = feet.relative(direction, distance);
                if (!world.hasChunkAt(column)) break;
                BlockPos surface = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
                if (Math.abs(surface.getY() - feet.getY()) > 3 || !world.getFluidState(surface).is(FluidTags.WATER)) continue;
                if (city == null || CityPlan.sample(city.planSeed(), column.getX(), column.getZ()).kind() == CityPlan.Kind.WATER)
                    return surface;
            }
        }
        return null;
    }

    private static Group chooseGroup(ServerLevel world, Site site, BlockPos feet, List<FaunaAnimal> nearby, int speciesCap,
                                     boolean checkBiome) {
        List<Group> groups = site.groups();
        int start = world.getRandom().nextInt(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get((start + i) % groups.size());
            if (count(nearby, feet, group.id()) < speciesCap && (!checkBiome || matchesBiome(world, group, feet, site)))
                return group;
        }
        return null;
    }

    private static boolean matchesBiome(ServerLevel world, Group group, BlockPos feet, Site site) {
        var key = TagKey.create(Registries.BIOME, Species.get(group.id()).resource(group.id()));
        return world.getBiome(feet).is(key) || site.water() != null && world.getBiome(site.water()).is(key);
    }

    private static int count(List<FaunaAnimal> nearby, BlockPos centre, String species) {
        int result = 0;
        for (FaunaAnimal animal : nearby) {
            double dx = animal.getX() - centre.getX() - .5, dz = animal.getZ() - centre.getZ() - .5;
            if (dx * dx + dz * dz <= LOCAL_RADIUS * LOCAL_RADIUS && Math.abs(animal.getY() - centre.getY()) <= 24
                    && (species == null || species.equals(animal.profile().id()))) result++;
        }
        return result;
    }

    private static boolean spawnGroup(ServerLevel world, Group group, BlockPos first, BlockPos anchor, List<ServerPlayer> players,
                                      CityGreyboxGenerator city, boolean wet, List<FaunaAnimal> nearby,
                                      int cap, int speciesCap, int desired) {
        var type = FaunaCore.TYPES.get(group.id()).get();
        var species = Species.get(group.id());
        MobSpawnType reason = city == null ? MobSpawnType.NATURAL : MobSpawnType.EVENT;
        SpawnGroupData data = null;
        RandomSource random = world.getRandom();
        int spawned = 0;
        for (int attempt = 0; attempt < 6 && spawned < desired; attempt++) {
            BlockPos feet = attempt == 0 ? first
                    : findSurface(world, first.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3), anchor, players);
            if (feet == null || count(nearby, feet, null) >= cap || count(nearby, feet, group.id()) >= speciesCap) continue;
            Site site = habitat(world, feet, city);
            if (site == null || wet != (site.water() != null) || city == null && !matchesBiome(world, group, feet, site)) continue;
            if (!FaunaCore.fitsSpawnSite(species, world, feet) || !SpawnPlacements.isSpawnPositionOk(type, world, feet)
                    || !EventHooks.checkSpawnPlacements(type, world, reason, feet, random, true)) continue;
            var animal = type.create(world);
            if (animal == null) continue;
            animal.moveTo(feet.getX() + .5, feet.getY(), feet.getZ() + .5, random.nextFloat() * 360, 0);
            if (!EventHooks.checkSpawnPosition(animal, world, reason)) continue;
            data = EventHooks.finalizeMobSpawn(animal, world, world.getCurrentDifficultyAt(feet), reason, data);
            if (!animal.isSpawnCancelled() && world.addFreshEntity(animal)) {
                nearby.add(animal);
                spawned++;
            }
        }
        return spawned > 0;
    }

    private record Group(String id, int minimum, int maximum) {}
    private record Site(List<Group> groups, BlockPos water) {}
}

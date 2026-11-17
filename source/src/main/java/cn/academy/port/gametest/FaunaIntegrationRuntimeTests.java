package cn.academy.port.gametest;

import cn.academy.port.city.CityFaunaSpawns;
import cn.academy.port.city.portal.CityPortalTravel;
import cn.academy.port.highpower.VanillaCombatContract;
import cn.academy.port.highpower.VanillaMiningContract;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FaunaIntegrationRuntimeTests.SUITE)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class FaunaIntegrationRuntimeTests {
    static final String SUITE = "academy_fauna_integration";
    private static final String TEMPLATE = "empty";
    private static final String FAUNA = "aca_fauna";

    private FaunaIntegrationRuntimeTests() {}

    @SubscribeEvent public static void createTemplate(ServerStartedEvent event) {
        if (!GameTestHooks.isGametestEnabled()) return;
        var level = event.getServer().overworld();
        // An empty template also works when the final JAR is tested with an external QA classpath.
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath(SUITE, TEMPLATE))
                .fillFromWorld(level, new BlockPos(4, level.getMaxBuildHeight() - 30, 4), new Vec3i(7, 6, 7), false, null);
    }

    @GameTest(template = TEMPLATE, batch = SUITE)
    public static void embedded_registries_and_reviewed_contracts(GameTestHelper h) {
        Set<String> animals = BuiltInRegistries.ENTITY_TYPE.keySet().stream().filter(id -> id.getNamespace().equals(FAUNA))
                .map(ResourceLocation::getPath).collect(Collectors.toSet());
        Set<String> eggs = BuiltInRegistries.ITEM.keySet().stream().filter(id -> id.getNamespace().equals(FAUNA))
                .filter(id -> BuiltInRegistries.ITEM.get(id) instanceof SpawnEggItem).map(ResourceLocation::getPath)
                .collect(Collectors.toSet());
        h.assertTrue(animals.size() == 53 && eggs.equals(animals.stream().map(id -> id + "_spawn_egg").collect(Collectors.toSet())),
                "all 53 animals have their original registry ID and spawn egg");
        var mods = ModList.get().getMods().stream().collect(Collectors.toMap(m -> m.getModId(), m -> m.getVersion().toString()));
        var packs = h.getLevel().getServer().getResourceManager().listPacks().map(p -> p.packId()).collect(Collectors.toSet());
        h.assertTrue("0.1.17".equals(mods.get(FAUNA)) && "4.7".equals(mods.get("geckolib")) && "0.4.5".equals(mods.get("acamech")),
                "embedded fauna, GeckoLib and mech versions match the reviewed artifact");
        h.assertTrue(VanillaCombatContract.runtimeReason(mods, packs).isEmpty()
                && VanillaMiningContract.runtimeReason(mods, packs).isEmpty(), "the exact combined package admits the existing strict rules");
        var unknown = new HashMap<>(mods);
        unknown.put(FAUNA, "0.1.18");
        h.assertTrue(VanillaCombatContract.runtimeReason(unknown, packs).isPresent()
                && VanillaMiningContract.runtimeReason(unknown, packs).isPresent(), "unreviewed versions remain refused");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = SUITE, timeoutTicks = 70)
    public static void urban_plains_do_not_admit_ambient_fauna(GameTestHelper h) {
        var overworld = h.getLevel();
        var server = overworld.getServer();
        var legacy = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("academy", "story_city"));
        var id = ResourceLocation.fromNamespaceAndPath(FAUNA, "sika_deer");
        var animal = BuiltInRegistries.ENTITY_TYPE.get(id);
        var feet = new BlockPos(8, 180, 8);
        overworld.getChunkAt(feet);
        overworld.setBlockAndUpdate(feet.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        h.runAfterDelay(10, () -> {
            var rule = overworld.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING);
            boolean saved = rule.get();
            rule.set(true, server);
            try {
                h.assertTrue(SpawnPlacements.checkSpawnRules(animal, overworld, MobSpawnType.NATURAL, feet, overworld.random),
                        "the original habitat placement still admits a clear overworld site");
                for (var dimension : List.of(CityPortalTravel.CITY, legacy)) {
                    for (var reason : List.of(MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION))
                        h.assertTrue(CityFaunaSpawns.suppresses(dimension, id, reason), "urban plains reject ambient fauna");
                    h.assertFalse(CityFaunaSpawns.suppresses(dimension, id, MobSpawnType.SPAWN_EGG),
                            "deliberate spawn-egg placement remains available in the city");
                    h.assertFalse(CityFaunaSpawns.suppresses(dimension, ResourceLocation.withDefaultNamespace("cow"), MobSpawnType.NATURAL),
                            "the gate only affects fauna types");
                }
                h.succeed();
            } finally {
                rule.set(saved, server);
            }
        });
    }
}

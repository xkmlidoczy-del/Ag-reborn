package cn.academy.port.gametest;

import cn.academy.port.city.CityBlocks;
import cn.academy.port.city.CityCrowdPlan;
import cn.academy.port.city.CityGreyboxGenerator;
import cn.academy.port.city.CityNpcContent;
import cn.academy.port.city.CityNpcEntity;
import cn.academy.port.city.CityPlan;
import cn.academy.port.city.CityTransit;
import cn.academy.port.city.CityWindContent;
import cn.academy.port.train.TrainLine;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Uses the real city codec and registered block states in the disposable native test server. */
@GameTestHolder("academy_urban_story")
@PrefixGameTestTemplate(false)
public final class StoryCityRuntimeTests {
    private static final ResourceLocation CITY_RESOURCE =
            ResourceLocation.fromNamespaceAndPath("academy", "dimension/story_city_map.json");
    private static final long PLAN_SEED = 20261103L;

    private StoryCityRuntimeTests() {}

    @GameTest(template = "empty", batch = "story_city_facade", timeoutTicks = 30)
    public static void city_codec_and_four_facade_families_generate_registered_states(GameTestHelper helper) {
        var level = helper.getLevel();
        LevelStem city = decodeCity(helper);
        var type = city.type().value();
        helper.assertTrue(type.minY() == -64 && type.height() == 1024, "story city dimension uses full height");
        var bounds = LevelHeightAccessor.create(type.minY(), type.height());
        var generator = city.generator();
        helper.assertTrue(generator instanceof CityGreyboxGenerator, "registered graybox generator decoded");
        var random = level.getChunkSource().randomState();
        for (int district : new int[]{7, 15, 17, 8}) {
            var lot = findLot(district);
            var sample = CityPlan.sample(PLAN_SEED, lot.x(), lot.z());
            var corner = generator.getBaseColumn(lot.x(), lot.z(), bounds, random);
            var center = generator.getBaseColumn(lot.x() + sample.bw() / 2, lot.z() + sample.bd() / 2, bounds, random);
            var regenerated = generator.getBaseColumn(lot.x(), lot.z(), bounds, random);
            String pier = switch (district) {
                case 15 -> "pillar_dark";
                case 17 -> "brushed_steel";
                default -> "pillar_white";
            };
            helper.assertTrue(corner.getBlock(5).is(CityBlocks.block(pier)), "D" + district + " facade family pier");
            helper.assertTrue(center.getBlock(1).isAir() && !center.getBlock(4).isAir(),
                    "D" + district + " hollow shell with a floor");
            helper.assertTrue(corner.getBlock(sample.height()).is(CityBlocks.block("dark_panel_wall"))
                            && center.getBlock(sample.height()).isAir()
                            && corner.getBlock(sample.height() + 1).isAir(),
                    "D" + district + " roof parapet bounds");
            for (int y = bounds.getMinBuildHeight(); y <= sample.height() + 1; y++) {
                helper.assertTrue(corner.getBlock(y).equals(regenerated.getBlock(y)),
                        "D" + district + " facade changed after sampling another column at y=" + y);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "story_city_facade", timeoutTicks = 30)
    public static void sample_streets_and_wind_turbines_leave_the_walk_clear(GameTestHelper helper) {
        var city = decodeCity(helper);
        var generator = city.generator();
        var bounds = LevelHeightAccessor.create(city.type().value().minY(), city.type().value().height());
        var random = helper.getLevel().getChunkSource().randomState();
        var head = generator.getBaseColumn(-80, 20, bounds, random);
        helper.assertTrue(head.getBlock(23).is(CityBlocks.block("pillar_white"))
                        && head.getBlock(24).is(CityWindContent.HEAD.get()) && head.getBlock(24).hasBlockEntity(),
                "D7 wind tower ends in a decorative rotor block entity");
        var district22 = CityPlan.sample(PLAN_SEED, -804, -1596);
        helper.assertTrue(district22.kind() == CityPlan.Kind.TURBINE, "D22 fixture is a turbine");
        var farm = generator.getBaseColumn(-804, -1596, bounds, random);
        helper.assertTrue(farm.getBlock(district22.height()).is(CityWindContent.HEAD.get()), "D22 has a rotor head");
        var post = generator.getBaseColumn(-72, 22, bounds, random);
        var fixture = generator.getBaseColumn(-70, 22, bounds, random);
        helper.assertTrue(post.getBlock(0).is(CityBlocks.block("pillar_dark"))
                        && fixture.getBlock(4).is(CityBlocks.block("ceiling_light")), "walk has an illuminated streetlight");
        helper.assertTrue(generator.getBaseColumn(-76, 46, bounds, random).getBlock(0).is(Blocks.OAK_LOG),
                "D7 route has a planted tree");
        helper.assertTrue(generator.getBaseColumn(-95, 40, bounds, random).getBlock(0).is(Blocks.OAK_LEAVES)
                        && generator.getBaseColumn(-90, 40, bounds, random).getBlock(0).is(Blocks.OAK_LOG),
                "D7 plaza has a garden border and tree");
        helper.assertTrue(generator.getBaseColumn(-72, 46, bounds, random).getBlock(0).is(Blocks.OAK_STAIRS),
                "D7 route has a bench outside the walking lane");
        for (int z = -48; z <= 350; z += 8) {
            var walk = generator.getBaseColumn(-65, z, bounds, random);
            helper.assertTrue(!walk.getBlock(-1).isAir() && walk.getBlock(0).isAir() && walk.getBlock(1).isAir(),
                    "street furniture must not block the sample walking route at z=" + z);
        }
        var stairSupport = generator.getBaseColumn(-63, 392, bounds, random);
        helper.assertTrue(stairSupport.getBlock(0).is(CityBlocks.block("pillar_white"))
                        && stairSupport.getBlock(11).is(CityBlocks.block("brushed_steel"))
                        && stairSupport.getBlock(12).is(Blocks.QUARTZ_STAIRS),
                "central station stairs retain their step and open structural support");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "story_city_facade", timeoutTicks = 30)
    public static void city_filters_fluid_sources_from_plains_features(GameTestHelper helper) {
        var generator = decodeCity(helper).generator();
        var plains = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        var original = plains.value().getGenerationSettings().features().stream().flatMap(net.minecraft.core.HolderSet::stream)
                .flatMap(h -> h.unwrapKey().stream()).map(k -> k.location().toString()).collect(java.util.stream.Collectors.toSet());
        var filtered = generator.getBiomeGenerationSettings(plains).features().stream().flatMap(net.minecraft.core.HolderSet::stream)
                .flatMap(h -> h.unwrapKey().stream()).map(k -> k.location().toString()).collect(java.util.stream.Collectors.toSet());
        var excluded = java.util.Set.of("minecraft:lake_lava_surface", "minecraft:lake_lava_underground",
                "minecraft:spring_lava", "minecraft:spring_lava_frozen", "minecraft:spring_water");
        helper.assertTrue(original.contains("minecraft:lake_lava_surface") && original.contains("minecraft:lake_lava_underground")
                        && original.contains("minecraft:spring_lava") && original.contains("minecraft:spring_water")
                        && java.util.Collections.disjoint(filtered, excluded), "city fluid sources must be filtered");
        var expected = new java.util.HashSet<>(original); expected.removeAll(excluded);
        helper.assertTrue(expected.equals(filtered) && filtered.contains("minecraft:ore_iron_middle")
                        && filtered.contains("minecraft:trees_plains"), "ore, vegetation and every other placed feature must remain");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "story_city_facade", timeoutTicks = 30)
    public static void district_landmarks_are_in_the_world_generator(GameTestHelper helper) {
        var city = decodeCity(helper);
        var generator = city.generator();
        var bounds = LevelHeightAccessor.create(city.type().value().minY(), city.type().value().height());
        var random = helper.getLevel().getChunkSource().randomState();
        int[][] positions = {
                {-1042,1,-241}, {-236,3,-1812}, {-207,1,-1025}, {566,7,-1027},
                {594,21,-224}, {1387,30,-1038}, {-1825,-1,-224}, {-1324,-5,-760},
                {-800,16,-1596}, {1593,1,-1868}, {-65,0,630}, {-1752,92,-819}, {1792,20,-1868}
        };
        var expected = new net.minecraft.world.level.block.Block[] {
                CityBlocks.block("pillar_white"), Blocks.RED_CONCRETE, CityBlocks.block("brushed_steel"), CityBlocks.block("led_screen"),
                CityBlocks.block("brushed_steel"), Blocks.GRAY_CONCRETE, Blocks.GRASS_BLOCK, CityBlocks.block("reinforced_concrete"),
                CityBlocks.block("brushed_steel"), CityBlocks.block("curtain_glass_clear"), Blocks.AIR,
                CityBlocks.block("reinforced_concrete"), CityBlocks.block("white_panel_wall")
        };
        for (int i = 0; i < positions.length; i++) {
            int[] p = positions[i];
            var actual = generator.getBaseColumn(p[0], p[2], bounds, random).getBlock(p[1]);
            helper.assertTrue(actual.is(expected[i]), "district structure at " + Arrays.toString(p) + " was " + actual);
        }
        helper.assertTrue(CityCrowdPlan.ground(PLAN_SEED, 8, -1825, 575, false) != null,
                "D8 park paths may host the district's existing ambient population");
        helper.assertTrue(CityCrowdPlan.ground(PLAN_SEED, 10, 1374, 575, false) == null,
                "fountain water must never be a crowd spawn location");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "story_city_facade", timeoutTicks = 30)
    public static void authored_city_structures_are_present_in_generated_columns(GameTestHelper helper) {
        var city = decodeCity(helper);
        var generator = city.generator();
        var bounds = LevelHeightAccessor.create(city.type().value().minY(), city.type().value().height());
        var random = helper.getLevel().getChunkSource().randomState();
        int[][] positions = {
                {-43, 1, -365}, {-33, 44, -360},          // D7 academic_09
                {-363, 1, 503},                            // D15 northwest residential_01 after district zoning
                {213, 1, 695}, {213, 9, 695},               // D15 commercial_01 in the commercial core
                {-19, -1, 700}, {-15, 1, 706}, {-51, 44, 734}, {0, 299, 788} // D15 complex
        };
        String[] blockNames = {"pillar_white", "facade_fin", "pillar_white", "pillar_dark", "dark_panel_wall",
                "white_panel_wall", "pillar_white", "white_panel_slab", "pillar_white"};
        for (int i = 0; i < positions.length; i++) {
            int[] p = positions[i];
            var actual = generator.getBaseColumn(p[0], p[2], bounds, random).getBlock(p[1]);
            helper.assertTrue(actual.is(CityBlocks.block(blockNames[i])),
                    "authored structure block at " + Arrays.toString(p) + " expected " + blockNames[i] + " got " + actual);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "story_city_facade", timeoutTicks = 30)
    public static void ordinary_facades_include_lit_and_unlit_windows(GameTestHelper helper) {
        var city = decodeCity(helper);
        var generator = city.generator();
        var bounds = LevelHeightAccessor.create(city.type().value().minY(), city.type().value().height());
        var random = helper.getLevel().getChunkSource().randomState();
        for (int district : new int[]{7, 15, 8}) {
            var lot = findLot(district);
            var sample = CityPlan.sample(PLAN_SEED, lot.x(), lot.z());
            int lit = 0, unlit = 0;
            for (int a = 1; a < sample.bw() - 1; a++) {
                var column = generator.getBaseColumn(lot.x() + a, lot.z() + 1, bounds, random);
                for (int y = 1; y < Math.min(sample.height() - 1, 20); y++) {
                    var block = column.getBlock(y);
                    if (block.is(CityBlocks.block("lit_window"))) lit++;
                    if (block.is(CityBlocks.block("curtain_glass_blue"))
                            || block.is(CityBlocks.block("curtain_glass_teal"))
                            || block.is(CityBlocks.block("curtain_glass_smoke"))
                            || block.is(CityBlocks.block("curtain_glass_clear"))) unlit++;
                }
            }
            helper.assertTrue(lit > 0 && unlit > 0,
                    "D" + district + " generated facade needs lit and unlit windows, got " + lit + "/" + unlit);
            LogUtils.getLogger().info("CITY_FACADE_WINDOWS D{} lit={} unlit={}", district, lit, unlit);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "story_city_facade", timeoutTicks = 30)
    public static void reservoir_depth_and_walkable_crossing_use_the_generated_columns(GameTestHelper helper) {
        var city = decodeCity(helper);
        var generator = city.generator();
        var bounds = LevelHeightAccessor.create(city.type().value().minY(), city.type().value().height());
        var random = helper.getLevel().getChunkSource().randomState();
        int lakeX = -1467, lakeZ = -762;
        var lake = CityPlan.sample(PLAN_SEED, lakeX, lakeZ);
        helper.assertTrue(lake.kind() == CityPlan.Kind.WATER && lake.height() <= -12, "deep center of the reservoir");
        var water = generator.getBaseColumn(lakeX, lakeZ, bounds, random);
        int bed = -2 + lake.height();
        helper.assertTrue(water.getBlock(bed).is(Blocks.STONE) && water.getBlock(bed + 1).is(Blocks.WATER)
                && water.getBlock(-2).is(Blocks.WATER) && water.getBlock(-1).isAir(), "generated water and bed match the planned depth");
        helper.assertTrue(generator.getBaseHeight(lakeX, lakeZ, Heightmap.Types.WORLD_SURFACE_WG, bounds, random) == -1
                && generator.getBaseHeight(lakeX, lakeZ, Heightmap.Types.OCEAN_FLOOR_WG, bounds, random) == bed + 1,
                "surface and ocean floor heightmaps distinguish water from the bed");

        var entrance = generator.getBaseColumn(-65, -51, bounds, random);
        helper.assertTrue(!entrance.getBlock(-1).isAir() && entrance.getBlock(0).isAir() && entrance.getBlock(1).isAir(),
                "campus gate opens to a walkable floor");
        var lamp = CityBlocks.block("ceiling_light");
        var hallLamp = generator.getBaseColumn(-65, -70, bounds, random).getBlock(5);
        var shape = hallLamp.getShape(helper.getLevel(), BlockPos.ZERO, CollisionContext.empty());
        helper.assertTrue(hallLamp.is(lamp) && hallLamp.getLightEmission() == 14
                        && shape.min(Direction.Axis.Y) > 0.75 && shape.max(Direction.Axis.Y) == 1,
                "hall lamp lights the room and stays against the ceiling");
        for (int z : new int[]{393, 407}) {
            var landing = generator.getBaseColumn(-76, z, bounds, random);
            var stair = generator.getBaseColumn(-75, z, bounds, random);
            helper.assertTrue(landing.getBlock(-1).is(CityBlocks.block("plaza_tile"))
                            && landing.getBlock(0).isAir() && stair.getBlock(0).is(Blocks.QUARTZ_STAIRS),
                    "west landing meets the low face of the first stair at z=" + z);
        }
        for (int z = 393; z <= 407; z++) {
            var crossing = generator.getBaseColumn(-42, z, bounds, random);
            helper.assertTrue(crossing.getBlock(30).is(CityBlocks.block("composite_floor"))
                    && crossing.getBlock(31).isAir() && crossing.getBlock(32).isAir(), "unobstructed footbridge at z=" + z);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "story_city_facade", timeoutTicks = 30)
    public static void npc_look_and_activity_round_trip_in_entity_data(GameTestHelper helper) {
        var npc = CityNpcContent.NPC.get().create(helper.getLevel());
        helper.assertTrue(npc != null && npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) != null,
                "city NPC registered with navigation attributes");
        var input = new CompoundTag();
        input.putString("Look", "visitor");
        input.putString("Activity", "sit");
        input.putString("Expression", "smile");
        npc.readAdditionalSaveData(input);
        var saved = npc.saveWithoutId(new CompoundTag());
        var restored = CityNpcContent.NPC.get().create(helper.getLevel());
        restored.load(saved);
        helper.assertTrue(restored.look() == CityNpcEntity.Look.VISITOR && restored.activity() == CityNpcEntity.Activity.SIT
                        && restored.expression() == CityNpcEntity.Expression.SMILE
                        && !restored.removeWhenFarAway(160),
                "named look and sitting activity survive serialization");
        input.putString("Look", "unknown_look");
        input.putString("Activity", "unknown_activity");
        input.putString("Expression", "unknown_expression");
        restored.readAdditionalSaveData(input);
        helper.assertTrue(restored.look() == CityNpcEntity.Look.STUDENT_BAG && restored.activity() == CityNpcEntity.Activity.STAND
                        && restored.expression() == CityNpcEntity.Expression.NEUTRAL,
                "unknown appearance and activity use the default preview");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "story_city_facade", timeoutTicks = 60)
    public static void every_district_has_a_role_mix_and_usable_crowd_ground(GameTestHelper helper) {
        var city = decodeCity(helper);
        var type = city.type().value();
        var bounds = LevelHeightAccessor.create(type.minY(), type.height());
        var random = helper.getLevel().getChunkSource().randomState();
        var generator = city.generator();
        for (int district = 1; district <= 23; district++) {
            var profile = CityCrowdPlan.profile(district);
            int peak = 0;
            for (var period : CityCrowdPlan.Period.values()) {
                int quota = profile.target(period);
                helper.assertTrue(quota >= 1 && quota <= 15 && !profile.roles().isEmpty(), "district " + district + " quota");
                peak = Math.max(peak, quota);
            }
            int best = 0;
            for (int z = -1968; z < CityPlan.HALF && best < peak; z += 96) {
                for (int x = -1968; x < CityPlan.HALF && best < peak; x += 96) {
                    if (CityPlan.district(x, z) != district) continue;
                    int available = 0;
                    for (int dx = -20; dx <= 20 && available < peak; dx += 5) {
                        for (int dz = -20; dz <= 20 && available < peak; dz += 5) {
                            var pos = CityCrowdPlan.ground(PLAN_SEED, district, x + dx, z + dz, false);
                            if (pos == null) continue;
                            var column = generator.getBaseColumn(pos.getX(), pos.getZ(), bounds, random);
                            if (!column.getBlock(pos.getY() - 1).isAir() && column.getBlock(pos.getY()).isAir()
                                    && column.getBlock(pos.getY() + 1).isAir()) available++;
                        }
                    }
                    best = Math.max(best, available);
                }
            }
            helper.assertTrue(best >= peak, "district " + district + " has " + best + " usable positions for " + peak);
            LogUtils.getLogger().info("CITY_CROWD_DISTRICT D{} peak={} valid_positions={}", district, peak, best);
        }
        helper.assertTrue(CityCrowdPlan.profile(6).night() > CityCrowdPlan.profile(6).morning()
                && CityCrowdPlan.profile(22).day() < CityCrowdPlan.profile(7).day(), "nightlife and sparse service areas differ");
        helper.assertTrue(CityCrowdPlan.ground(PLAN_SEED, 23, 1600, -1660, false) == null
                && CityCrowdPlan.ground(PLAN_SEED, 21, -1467, -762, false) == null,
                "crowd routes stay out of the runway and reservoir");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "story_city_facade", timeoutTicks = 30)
    public static void every_express_station_generates_boarding_screens_and_safe_exits(GameTestHelper helper) {
        var city = decodeCity(helper);
        var type = city.type().value();
        var bounds = LevelHeightAccessor.create(type.minY(), type.height());
        var generator = city.generator();
        var random = helper.getLevel().getChunkSource().randomState();
        int y = TrainLine.RAIL_Y;
        for (int station = 0; station < TrainLine.STATIONS.size(); station++) {
            int stationX = TrainLine.STATIONS.get(station).x();
            var canopy = generator.getBaseColumn(stationX, CityTransit.Z - 6, bounds, random);
            helper.assertTrue(canopy.getBlock(33).is(CityBlocks.block("ceiling_light")),
                    "station " + station + " uses the ceiling light");
            for (int role = 0; role < 3; role++) {
                int x = CityTransit.bayX(station, role);
                for (int side : new int[]{-1, 1}) {
                    var screen = generator.getBaseColumn(x, CityTransit.Z + side * 7, bounds, random);
                    var exit = generator.getBaseColumn(x, CityTransit.Z + side * 5, bounds, random);
                    helper.assertTrue(screen.getBlock(y).is(CityBlocks.block("led_screen")),
                            "missing boarding screen at station " + station + ", car " + role);
                    helper.assertTrue(exit.getBlock(y - 1).is(CityBlocks.block("plaza_tile"))
                                    && exit.getBlock(y).isAir() && exit.getBlock(y + 1).isAir(),
                            "blocked exit at station " + station + ", car " + role);
                }
            }
        }
        helper.succeed();
    }

    private static LevelStem decodeCity(GameTestHelper helper) {
        var level = helper.getLevel();
        var resources = level.getServer().getResourceManager();
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        LevelStem city;
        // GameTestServer creates a flat overworld only. Decode the shipped dimension against its live registry.
        try (var reader = resources.getResourceOrThrow(CITY_RESOURCE).openAsReader()) {
            city = LevelStem.CODEC.parse(ops, JsonParser.parseReader(reader)).getOrThrow();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read story city dimension resource", e);
        }
        return city;
    }

    private record Lot(int x, int z) {}

    private static Lot findLot(int district) {
        for (int z = -CityPlan.HALF; z < CityPlan.HALF; z += CityPlan.CELL) {
            for (int x = -CityPlan.HALF; x < CityPlan.HALF; x += CityPlan.CELL) {
                if (CityPlan.district(x, z) != district) continue;
                for (int dz = 0; dz < CityPlan.CELL; dz += 4) {
                    for (int dx = 0; dx < CityPlan.CELL; dx += 4) {
                        var sample = CityPlan.sample(PLAN_SEED, x + dx, z + dz);
                        if (sample.kind() == CityPlan.Kind.BUILDING && sample.bw() > 0
                                && !cn.academy.port.city.CityTemplateSites.usesTemplate(PLAN_SEED, x + dx, z + dz)) {
                            return new Lot(x + dx - sample.a(), z + dz - sample.b());
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("no ordinary lot in D" + district);
    }
}

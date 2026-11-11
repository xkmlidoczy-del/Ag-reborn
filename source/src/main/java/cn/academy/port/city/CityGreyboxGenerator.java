package cn.academy.port.city;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.core.Holder;

/** Grey-box story city: the whole 4096x4096 plan (see {@link CityPlan}) as district-coloured massing. Ground surface is y=-1. */
public final class CityGreyboxGenerator extends ChunkGenerator {
    public static final MapCodec<CityGreyboxGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            com.mojang.serialization.Codec.LONG.optionalFieldOf("plan_seed", 20261103L).forGetter(g -> g.seed)
    ).apply(i, CityGreyboxGenerator::new));

    private static final int SURFACE = -1;
    private static final int SEA_FLOOR = -13;
    private static final java.util.Set<net.minecraft.resources.ResourceLocation> CITY_FLUID_FEATURES = java.util.Set.of(
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("lake_lava_surface"),
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("lake_lava_underground"),
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("spring_lava"),
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("spring_lava_frozen"),
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("spring_water"));
    private static final Map<BiomeGenerationSettings, BiomeGenerationSettings> FILTERED_FEATURES = new WeakHashMap<>();
    private final long seed;
    private final CityMountainTrail mountainTrail;
    private final CityMountainNature mountainNature;
    private final CityInteriors.Planner interiors;
    private volatile java.util.function.Consumer<CityInteriors.Group> interiorRecorder=g->{};

    public CityGreyboxGenerator(BiomeSource biomeSource, long seed) {
        super(biomeSource, CityGreyboxGenerator::cityGenerationSettings);
        this.seed = seed;
        this.interiors = new CityInteriors.Planner(seed);
        this.mountainTrail = new CityMountainTrail(seed);
        this.mountainNature = new CityMountainNature(seed, this.mountainTrail);
    }

    /** Preserve city vegetation and ore features while excluding vanilla fluid sources that can flood transit. */
    private static BiomeGenerationSettings cityGenerationSettings(Holder<Biome> biome) {
        var original = biome.value().getGenerationSettings();
        synchronized (FILTERED_FEATURES) {
            return FILTERED_FEATURES.computeIfAbsent(original, CityGreyboxGenerator::withoutFluidFeatures);
        }
    }

    private static BiomeGenerationSettings withoutFluidFeatures(BiomeGenerationSettings original) {
        var carvers = new java.util.EnumMap<GenerationStep.Carving,
                HolderSet<net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver<?>>>(GenerationStep.Carving.class);
        for (var stage : original.getCarvingStages()) {
            var entries = new java.util.ArrayList<Holder<net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver<?>>>();
            original.getCarvers(stage).forEach(entries::add);
            carvers.put(stage, HolderSet.direct(entries));
        }
        var features = original.features().stream().<HolderSet<net.minecraft.world.level.levelgen.placement.PlacedFeature>>map(group -> HolderSet.direct(group.stream().filter(feature ->
                feature.unwrapKey().map(key -> !CITY_FLUID_FEATURES.contains(key.location())).orElse(true)).toList())).toList();
        return new BiomeGenerationSettings(carvers, features);
    }

    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> sets, RandomState random, long worldSeed) {
        return ChunkGeneratorStructureState.createForFlat(random, worldSeed, this.biomeSource, Stream.<Holder<StructureSet>>empty());
    }

    // ------------------------------------------------------------------ block choice
    private static BlockState academy(String id) { return CityBlocks.block(id).defaultBlockState(); }

    private static BlockState wall(int district) {
        return (switch (district) {
            case 1 -> Blocks.WHITE_CONCRETE;
            case 2 -> Blocks.GRAY_CONCRETE;
            case 3 -> Blocks.CYAN_CONCRETE;
            case 4 -> Blocks.ORANGE_CONCRETE;
            case 5 -> Blocks.LIGHT_BLUE_CONCRETE;
            case 6 -> Blocks.MAGENTA_CONCRETE;
            case 7 -> Blocks.YELLOW_CONCRETE;
            case 8 -> Blocks.LIGHT_BLUE_CONCRETE;
            case 9 -> Blocks.CYAN_CONCRETE;
            case 10 -> Blocks.ORANGE_CONCRETE;
            case 11 -> Blocks.BROWN_CONCRETE;
            case 12 -> Blocks.LIGHT_GRAY_CONCRETE;
            case 13 -> Blocks.LIME_CONCRETE;
            case 14 -> Blocks.PINK_CONCRETE;
            case 15 -> Blocks.PURPLE_CONCRETE;
            case 16 -> Blocks.BLUE_CONCRETE;
            case 17 -> Blocks.BROWN_CONCRETE;
            case 18 -> Blocks.GREEN_CONCRETE;
            case 19 -> Blocks.PURPLE_CONCRETE;
            case 20 -> Blocks.LIME_CONCRETE;
            case 22 -> Blocks.LIGHT_GRAY_CONCRETE;
            case 23 -> Blocks.BLACK_CONCRETE;
            default -> Blocks.LIGHT_GRAY_CONCRETE;
        }).defaultBlockState();
    }

    /** Block at absolute y for one column of the plan. */
    private static BlockState blockAt(CityPlan.Sample s, CityDistrictZoning.Use zone, int y, int minY) {
        if (y == minY) return Blocks.BEDROCK.defaultBlockState();
        boolean sea = s.kind() == CityPlan.Kind.SEA || s.kind() == CityPlan.Kind.WATER;
        if (sea) {
            int bed = s.kind() == CityPlan.Kind.WATER ? SURFACE - 1 + s.height() : SEA_FLOOR;
            if (y <= bed) return Blocks.STONE.defaultBlockState();
            return y <= SURFACE - 1 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        if (y < SURFACE) return Blocks.STONE.defaultBlockState();
        if (y == SURFACE) {
            return switch (s.kind()) {
                case ROAD, RUNWAY -> academy("asphalt");
                case MARKING -> academy("road_marking");
                case SIDEWALK -> academy("plaza_tile");
                case PLAZA -> academy("composite_floor");
                case PARK, TERRAIN -> s.height() > 0 && s.kind() == CityPlan.Kind.TERRAIN ? Blocks.STONE.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                default -> academy("reinforced_concrete");
            };
        }
        int h = y - SURFACE - 1;                                  // 0 = first block above the surface
        return switch (s.kind()) {
            case BUILDING -> s.bw() > 0 ? CityFacade.blockAt(s, h, wall(s.district()), zone) : h < s.height() ? wall(s.district()) : Blocks.AIR.defaultBlockState();
            case LANDMARK -> h < s.height() ? academy("white_panel_wall") : Blocks.AIR.defaultBlockState();
            case TERRAIN -> h < s.height() - 1 ? Blocks.STONE.defaultBlockState()
                    : h == s.height() - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
            case TURBINE -> h < s.height() ? academy("pillar_white") : h == s.height() ? CityWindContent.HEAD.get().defaultBlockState() : Blocks.AIR.defaultBlockState();
            case RUNWAY -> Blocks.AIR.defaultBlockState();
            default -> Blocks.AIR.defaultBlockState();
        };
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, BlockState> PARSED = new java.util.concurrent.ConcurrentHashMap<>();

    static BlockState parse(String spec) {
        return PARSED.computeIfAbsent(spec, k -> {
            try {
                return net.minecraft.commands.arguments.blocks.BlockStateParser
                        .parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), k, false).blockState();
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                throw new IllegalStateException("bad block spec " + k, e);
            }
        });
    }

    private record Column(CityPlan.Sample sample, CityDistrictZoning.Use zone, CityTransit.Over[] transit, CityTransit.Over[] airport, CityTransit.Over[] northSouth, CityTransit.Over[] ring, CityTransit.Over[] metroAirport, CityTransit.Over[] metroNorth, CityTransit.Over[] metro, CitySampleRoute.Column route,
                          CityStreetProps.Column street, CityNeighborhoodProps.Column neighborhood, CityDistrictLandmarks.Column landmark,
                          CityMountainTrail.Column trail, CityDistrictBlocks.Column districtBlock, CityTemplateLibrary.Column template, CityTemplateFrontage.Column frontage, CityAirportCorridor.Column corridor,
                          CityCampusPark.Column campusPark, CityD7CentralGardens.Column centralGardens, CityD7PlazaLandscape.Column plazaLandscape, CityCommercialStreets.Column commercialStreets,
                          CityMountainNature.Column mountainNature, CityAirportApron.Column airportApron,
                          CityNeighborhoodSpaces.Column neighborhoodSpaces, CityServicePlan.Column service,CityLifeRooms.Column life, int x,int z,CityInteriors.Group interior) {
        BlockState block(int y, int minY) {
            if (interior != null && landmark != null && landmark.type() == 32) {
                BlockState lab = CityLabPlan.blockAt(landmark.x(), y, landmark.z());if(lab!=null)return lab;
            }
            if(interior!=null){BlockState revised=interior.block(x,y,z);if(revised!=null)return revised;}
            if(life!=null){var furnished=life.block(y);if(furnished!=null)return furnished;}
            if (service != null) {
                BlockState furnishing = service.block(y);
                if (furnishing != null) return furnishing;
            }
            if (landmark != null && landmark.type() == 32) {
                BlockState lab = CityLabPlan.blockAt(landmark.x(), y, landmark.z());
                if (lab != null) return lab;
            }
            if (street != null) {
                String spec = street.block(y);
                if (spec != null) return parse(spec);
            }
            if (neighborhood != null) {
                String spec = neighborhood.block(y);
                if (spec != null) return parse(spec);
            }
            if (metroAirport != null) {
                for (int i = metroAirport.length - 1; i >= 0; i--) {
                    if (metroAirport[i].y() == y) return parse(metroAirport[i].block());
                }
            }
            if (metroNorth != null) {
                for (int i = metroNorth.length - 1; i >= 0; i--) {
                    if (metroNorth[i].y() == y) return parse(metroNorth[i].block());
                }
            }
            if (metro != null) {
                for (int i = metro.length - 1; i >= 0; i--) {
                    if (metro[i].y() == y) return parse(metro[i].block());
                }
            }
            if (route != null) {
                String spec = route.block(y);
                if (spec != null) return parse(spec);
            }
            if (transit != null) {
                for (int i = transit.length - 1; i >= 0; i--) {
                    if (transit[i].y() == y) return parse(transit[i].block());
                }
            }
            if (airport != null) {
                for (int i = airport.length - 1; i >= 0; i--) {
                    if (airport[i].y() == y) return parse(airport[i].block());
                }
            }
            if (northSouth != null) {
                for (int i = northSouth.length - 1; i >= 0; i--) {
                    if (northSouth[i].y() == y) return parse(northSouth[i].block());
                }
            }
            if (ring != null) {
                for (int i = ring.length - 1; i >= 0; i--) {
                    if (ring[i].y() == y) return parse(ring[i].block());
                }
            }
            if (airportApron != null) {
                String spec = airportApron.block(y);
                if (spec != null) return parse(spec);
            }
            if (corridor != null) {
                String spec = corridor.block(y);
                if (spec != null) return parse(spec);
            }
            if (campusPark != null) {
                String spec = campusPark.block(y);
                if (spec != null) return parse(spec);
            }
            if (centralGardens != null) {
                String spec = centralGardens.block(y);
                if (spec != null) return parse(spec);
            }
            if (plazaLandscape != null) {
                String spec = plazaLandscape.block(y);
                if (spec != null) return parse(spec);
            }
            if (commercialStreets != null) {
                String spec = commercialStreets.block(y);
                if (spec != null) return parse(spec);
            }
            if (neighborhoodSpaces != null) {
                String spec = neighborhoodSpaces.block(y);
                if (spec != null) return parse(spec);
            }
            if (landmark != null) {
                String spec = landmark.block(y);
                if (spec != null) return parse(spec);
            }
            if (trail != null) {
                String spec = trail.block(y);
                if (spec != null) return parse(spec);
            }
            if (mountainNature != null) {
                String spec = mountainNature.block(y);
                if (spec != null) return parse(spec);
            }
            if (districtBlock != null) {
                String spec = districtBlock.block(y);
                if (spec != null) return parse(spec);
            }
            if (frontage != null) {
                String spec = frontage.block(y);
                if (spec != null) return parse(spec);
            }
            if (template != null) {
                BlockState state = template.block(y);
                if (state != null) return state;
            }
            return blockAt(sample, zone, y, minY);
        }

        int top() {
            int top = switch (sample.kind()) {
                case SEA, WATER -> SURFACE - 1;
                case BUILDING -> SURFACE + sample.height() + (sample.bw() > 0 ? 6 : 0);
                case LANDMARK, TERRAIN -> SURFACE + sample.height();
                case TURBINE -> SURFACE + sample.height() + 1;
                default -> SURFACE;
            };
            if (transit != null) for (var over : transit) top = Math.max(top, over.y());
            if (airport != null) for (var over : airport) top = Math.max(top, over.y());
            if (northSouth != null) for (var over : northSouth) top = Math.max(top, over.y());
            if (ring != null) for (var over : ring) top = Math.max(top, over.y());
            if (metroAirport != null) for (var over : metroAirport) top = Math.max(top, over.y());
            if (metroNorth != null) for (var over : metroNorth) top = Math.max(top, over.y());
            if (metro != null) for (var over : metro) top = Math.max(top, over.y());
            if (route != null) top = Math.max(top, route.top());
            if (neighborhood != null) top = Math.max(top, neighborhood.top());
            if (landmark != null) top = Math.max(top, landmark.top());
            if (trail != null) top = Math.max(top, trail.top());
            if (template != null) top = Math.max(top, template.top());
            if (interior != null) top = Math.max(top, interior.top());
            if (districtBlock != null) top = Math.max(top, districtBlock.top());
            if (frontage != null) top = Math.max(top, frontage.top());
            if (corridor != null) top = Math.max(top, corridor.top());
            if (campusPark != null) top = Math.max(top, campusPark.top());
            if (centralGardens != null) top = Math.max(top, centralGardens.top());
            if (plazaLandscape != null) top = Math.max(top, plazaLandscape.top());
            if (commercialStreets != null) top = Math.max(top, commercialStreets.top());
            if (mountainNature != null) top = Math.max(top, mountainNature.top());
            if (airportApron != null) top = Math.max(top, airportApron.top());
            if (neighborhoodSpaces != null) top = Math.max(top, neighborhoodSpaces.top());
            if (service != null) top = Math.max(top, service.top());
            if (life != null) top = Math.max(top, life.top());
            return street == null ? top : Math.max(top, street.top());
        }

        int firstFree(Heightmap.Types type, int minY, int top) {
            Predicate<BlockState> opaque = type.isOpaque();
            for (int y = top; y >= minY; y--) if (opaque.test(block(y, minY))) return y + 1;
            return minY;
        }
    }

    long planSeed() { return seed; }
    public void interiorPolicy(CityInteriorVersions.Policy policy){interiors.policy(policy);}
    public CityInteriors.Group interiorAt(int x,int z){return interiors.at(x,z);}
    public java.util.Collection<CityInteriors.Group> cachedInteriors(){return interiors.cached();}
    public void onInteriorGenerated(java.util.function.Consumer<CityInteriors.Group> recorder){interiorRecorder=java.util.Objects.requireNonNull(recorder);}

    private Column column(int x, int z) {
        var sample = CityPlan.sample(seed, x, z);
        var zone = sample.kind() == CityPlan.Kind.BUILDING ? CityDistrictZoning.at(sample.district(), x, z) : null;
        var site = CityTemplateSites.at(seed, x, z);
        var districtBlock = CityDistrictBlocks.at(seed, x, z, site);
        var interior = interiors.at(x,z);
        // R5's last procedural storey had a two-block roof clearance. New buildings gain
        // one roof block of height so every four-block storey can remain usable.
        if(interior!=null&&interior.id().startsWith("procedural:"))sample=new CityPlan.Sample(sample.kind(),sample.district(),sample.height()+1,sample.a(),sample.b(),sample.bw(),sample.bd(),sample.hash());
        var template = districtBlock == null ? CityTemplateLibrary.at(x, z, site,interior!=null) : null;
        return new Column(sample, zone, CityTransit.overlay(x, z), CityAirportTransit.overlay(seed, x, z), CityNorthSouthTransit.overlay(seed, x, z), CityRingTransit.overlay(seed, x, z), CityMetroAirportTransit.overlay(x, z), CityMetroNorthTransit.overlay(x, z), CityMetroTransit.overlay(x, z),
                CitySampleRoute.at(x, z), CityStreetProps.at(seed, x, z),
                CityNeighborhoodProps.at(seed, x, z), CityDistrictLandmarks.at(seed, x, z), mountainTrail.at(x, z),
                districtBlock, template, CityTemplateFrontage.at(seed, x, z, sample, template), CityAirportCorridor.at(seed, x, z, sample),
                CityCampusPark.at(seed, x, z, sample), CityD7CentralGardens.at(seed, x, z, sample), CityD7PlazaLandscape.at(seed, x, z, sample), CityCommercialStreets.at(seed, x, z, sample),
                mountainNature.at(x, z), CityAirportApron.at(x, z),
                CityNeighborhoodSpaces.at(seed, x, z, sample), CityServicePlan.at(x, z),CityLifeRooms.at(x,z),x,z,interior);
    }

    // ------------------------------------------------------------------ generation
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        int minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight();
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int cx = chunk.getPos().getMinBlockX(), cz = chunk.getPos().getMinBlockZ();
        var generatedInteriors=new java.util.LinkedHashMap<String,CityInteriors.Group>();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                Column column = column(cx + lx, cz + lz);
                if(column.interior()!=null)generatedInteriors.putIfAbsent(column.interior().id(),column.interior());
                int top = Math.min(maxY - 1, column.top());
                int highest = minY - 1;
                for (int y = minY; y <= top; y++) {
                    BlockState state = column.block(y, minY);
                    if (state.isAir()) continue;
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                    section.setBlockState(lx, y & 15, lz, state, false);
                    if (state.hasBlockEntity() && state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock entityBlock) {
                        var entity = entityBlock.newBlockEntity(new BlockPos(cx + lx, y, cz + lz), state);
                        if (entity != null) {
                            if (column.landmark() != null && column.landmark().type() == 32)
                                CityLabPlan.initialize(entity.getBlockPos(), entity);
                            if (column.service() != null) CityServicePlan.initialize(entity.getBlockPos(), entity);
                            if (column.life() != null) CityLifeRooms.initialize(entity.getBlockPos(), entity);
                            if(column.interior()!=null)CityInteriors.initialize(column.interior(),entity.getBlockPos(),entity);
                            chunk.setBlockEntity(entity);
                        }
                    }
                    highest = y;
                }
                if (highest >= minY) {
                    surface.update(lx, highest, lz, column.block(highest, minY));
                    int floor = column.firstFree(Heightmap.Types.OCEAN_FLOOR_WG, minY, highest) - 1;
                    if (floor >= minY) ocean.update(lx, floor, lz, column.block(floor, minY));
                }
            }
        }
        generatedInteriors.values().forEach(interiorRecorder);
        return CompletableFuture.completedFuture(chunk);
    }

    @Override public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {}
    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes,
                                       StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) {}
    @Override public int getMinY() { return -64; }
    @Override public int getGenDepth() { return 1024; }
    @Override public int getSeaLevel() { return SURFACE; }
    @Override public int getSpawnHeight(LevelHeightAccessor level) { return 0; }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        Column column = column(x, z);
        return column.firstFree(type, level.getMinBuildHeight(), Math.min(column.top(), level.getMaxBuildHeight() - 1));
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        Column column = column(x, z);
        int minY = level.getMinBuildHeight();
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) states[i] = column.block(minY + i, minY);
        return new NoiseColumn(minY, states);
    }

    @Override public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        CityPlan.Sample s = CityPlan.sample(seed, pos.getX(), pos.getZ());
        info.add("Academy City D" + s.district() + " " + s.kind() + " h=" + s.height());
    }
}

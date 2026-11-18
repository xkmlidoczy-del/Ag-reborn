package cn.aca.omnitrix.world;

import cn.aca.omnitrix.Omnitrix;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ExpeditionStructure extends Structure {
    public enum Site {
        CRASH_POD, STAR_RUIN, QUARANTINE_RUIN;
        String id() { return name().toLowerCase(Locale.ROOT); }
        ResourceKey<LootTable> loot() { return ResourceKey.create(Registries.LOOT_TABLE, Omnitrix.id("chests/" + id())); }
    }

    private static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Omnitrix.ID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Omnitrix.ID);
    private static final Codec<Site> SITE = Codec.STRING.xmap(s -> Site.valueOf(s.toUpperCase(Locale.ROOT)), Site::id);
    public static final MapCodec<ExpeditionStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(settingsCodec(instance), SITE.fieldOf("site").forGetter(s -> s.site))
                    .apply(instance, ExpeditionStructure::new));
    private static final java.util.function.Supplier<StructureType<ExpeditionStructure>> TYPE =
            TYPES.register("expedition", () -> () -> CODEC);
    private static final java.util.function.Supplier<StructurePieceType> PIECE =
            PIECES.register("expedition", () -> (StructurePieceType.ContextlessType) Piece::new);

    private final Site site;

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        PIECES.register(bus);
    }

    public ExpeditionStructure(StructureSettings settings, Site site) {
        super(settings);
        this.site = site;
    }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();
        int y = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        BlockPos surface = new BlockPos(x, y, z);
        if (y < 60 || y > context.heightAccessor().getMaxBuildHeight() - 15) return Optional.empty();
        return Optional.of(new GenerationStub(surface, b -> b.addPiece(new Piece(surface, site))));
    }

    @Override public StructureType<?> type() { return TYPE.get(); }

    private static final class Piece extends StructurePiece {
        private final Site site;
        private final int surface;

        Piece(BlockPos anchor, Site site) {
            super(PIECE.get(), 0, bounds(anchor, site));
            this.site = site;
            this.surface = anchor.getY();
        }

        Piece(CompoundTag tag) {
            super(PIECE.get(), tag);
            site = Site.valueOf(tag.getString("Site"));
            surface = tag.getInt("Surface");
        }

        private static BoundingBox bounds(BlockPos at, Site s) {
            return new BoundingBox(at.getX()-7, at.getY() - (s == Site.QUARANTINE_RUIN ? 13 : 2), at.getZ()-7,
                    at.getX()+7, at.getY()+10, at.getZ()+7);
        }

        @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putString("Site", site.name());
            tag.putInt("Surface", surface);
        }

        @Override public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                                          RandomSource random, BoundingBox limit, ChunkPos chunk, BlockPos pivot) {
            switch (site) {
                case CRASH_POD -> capsule(level, limit, random);
                case STAR_RUIN -> orbit(level, limit, random);
                case QUARANTINE_RUIN -> bunker(level, limit, random);
            }
        }

        private void capsule(WorldGenLevel level, BoundingBox limit, RandomSource random) {
            for (int x = -6; x <= 6; x++) {
                for (int z = -6; z <= 6; z++) {
                    int d = x*x + z*z;
                    if (d > 40) continue;
                    int drop = d < 22 ? -2 : -1;
                    put(level, limit, x, drop, z, d % 4 == 0 ? Blocks.MAGMA_BLOCK : d % 3 == 0 ? Blocks.TUFF : Blocks.DEEPSLATE);
                    for (int y = drop + 1; y <= 1; y++) put(level, limit, x, y, z, Blocks.AIR);
                }
            }
            for (int z = -4; z <= 4; z++) {
                int breadth = 2 + (Math.abs(z) <= 1 ? 1 : 0);
                for (int x = -breadth; x <= breadth; x++) {
                    put(level, limit, x, -1, z, Math.abs(x) == breadth ? Blocks.WAXED_EXPOSED_COPPER : Blocks.POLISHED_DEEPSLATE);
                    if (Math.abs(x) == breadth || z == -4) {
                        put(level, limit, x, 0, z, Blocks.WAXED_CUT_COPPER);
                        put(level, limit, x, 1, z, z % 2 == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.GRAY_STAINED_GLASS);
                    }
                    if (z <= 1 && Math.abs(x) <= 1) put(level, limit, x, 2, z, Blocks.GRAY_STAINED_GLASS);
                }
            }
            for (int side : new int[]{-1, 1}) {
                put(level, limit, side * 3, 0, -2, Blocks.OCHRE_FROGLIGHT);
                put(level, limit, side * 3, 0, 2, Blocks.OCHRE_FROGLIGHT);
                put(level, limit, side * 2, 0, 3, Blocks.IRON_BLOCK);
            }
            chest(level, limit, random, 0, 0, -2);
        }

        private void orbit(WorldGenLevel level, BoundingBox limit, RandomSource random) {
            for (int x = -6; x <= 6; x++) {
                for (int z = -6; z <= 6; z++) {
                    int r = x*x + z*z;
                    if (r > 40) continue;
                    put(level, limit, x, -1, z, Blocks.POLISHED_DEEPSLATE);
                    put(level, limit, x, 0, z, r > 27 ? Blocks.CALCITE : (r % 4 == 0 ? Blocks.POLISHED_DIORITE : Blocks.SMOOTH_BASALT));
                    for (int y = 1; y <= 4; y++) put(level, limit, x, y, z, Blocks.AIR);
                }
            }
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                int x = dir.getStepX()*5, z = dir.getStepZ()*5;
                for (int y = 1; y <= 4; y++) put(level, limit, x, y, z, Blocks.QUARTZ_PILLAR);
                put(level, limit, x, 5, z, Blocks.PEARLESCENT_FROGLIGHT);
                put(level, limit, x, 6, z, Blocks.AMETHYST_BLOCK);
                put(level, limit, x + dir.getOpposite().getStepX(), 5, z + dir.getOpposite().getStepZ(), Blocks.PURPUR_BLOCK);
            }
            put(level, limit, 0, 1, 0, Omnitrix.STAR_STATION.get());
            put(level, limit, -2, 1, -1, Blocks.AMETHYST_BLOCK);
            put(level, limit, 2, 1, 1, Blocks.AMETHYST_BLOCK);
            chest(level, limit, random, 0, 1, -3);
        }

        private void bunker(WorldGenLevel level, BoundingBox limit, RandomSource random) {
            for (int x = -5; x <= 5; x++) {
                for (int z = -5; z <= 5; z++) {
                    boolean rim = Math.abs(x) == 5 || Math.abs(z) == 5;
                    for (int y = -12; y <= -7; y++) {
                        Block block = y == -12 ? Blocks.POLISHED_DEEPSLATE : y == -7 || rim ? Blocks.DEEPSLATE_BRICKS : Blocks.AIR;
                        put(level, limit, x, y, z, block);
                    }
                }
            }
            for (int y = -11; y <= 2; y++) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = 2; z <= 4; z++) {
                        boolean edge = Math.abs(x) == 1 || z == 2 || z == 4;
                        if (y >= -7 || x == 0 && z >= 3) {
                            var state = (edge ? Blocks.WAXED_CUT_COPPER : Blocks.AIR).defaultBlockState();
                            if (x == 0 && z == 3) state = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
                            if (x == 0 && z == 2 && y >= 0) state = Blocks.AIR.defaultBlockState();
                            if (x == 0 && z == 4) state = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                            put(level, limit, x, y, z, state);
                        }
                    }
                }
            }
            for (int x : new int[]{-4, 4}) {
                for (int z : new int[]{-4, 4}) {
                    put(level, limit, x, -10, z, Blocks.REDSTONE_BLOCK);
                    put(level, limit, x, -9, z, Blocks.REDSTONE_LAMP);
                }
            }
            put(level, limit, 0, -11, -1, Omnitrix.QUARANTINE.get());
            put(level, limit, -2, -11, -2, Blocks.TINTED_GLASS);
            put(level, limit, 2, -11, -2, Blocks.TINTED_GLASS);
            chest(level, limit, random, 0, -11, -4);
        }

        private BlockPos at(int x, int y, int z) {
            return new BlockPos(boundingBox.minX() + 7 + x, surface + y, boundingBox.minZ() + 7 + z);
        }

        private void put(WorldGenLevel level, BoundingBox limit, int x, int y, int z, Block block) {
            put(level, limit, x, y, z, block.defaultBlockState());
        }

        private void put(WorldGenLevel level, BoundingBox limit, int x, int y, int z, BlockState block) {
            BlockPos where = at(x,y,z);
            if (limit.isInside(where)) {
                level.setBlock(where, block, Block.UPDATE_CLIENTS);
                if (block.is(Blocks.LADDER)) level.getChunk(where).markPosForPostprocessing(where);
            }
        }

        private void chest(WorldGenLevel level, BoundingBox limit, RandomSource random, int x, int y, int z) {
            createChest(level, limit, random, at(x,y,z), site.loot(), Blocks.CHEST.defaultBlockState());
        }
    }
}

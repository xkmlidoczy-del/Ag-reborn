package cn.aca.infinity.world;

import cn.aca.infinity.AcaInfinity;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.function.Supplier;
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
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CosmicVaultStructure extends Structure {
    private static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, AcaInfinity.ID);
    private static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, AcaInfinity.ID);

    public static final MapCodec<CosmicVaultStructure> CODEC = simpleCodec(CosmicVaultStructure::new);

    public static final Supplier<StructureType<CosmicVaultStructure>> TYPE =
            TYPES.register("cosmic_vault", () -> () -> CODEC);
    public static final Supplier<StructurePieceType> PIECE =
            PIECES.register("cosmic_vault_piece", () -> (StructurePieceType.ContextlessType) Piece::new);

    private static final ResourceKey<LootTable> VAULT_LOOT =
            ResourceKey.create(Registries.LOOT_TABLE, AcaInfinity.id("chests/cosmic_vault"));

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        PIECES.register(modBus);
    }

    public CosmicVaultStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!cn.aca.infinity.InfinityConfig.enableCosmicVaultGeneration()) {
            return Optional.empty();
        }
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();
        int y = context.chunkGenerator().getFirstOccupiedHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        if (y < 58 || y > context.heightAccessor().getMaxBuildHeight() - 16) return Optional.empty();
        BlockPos anchor = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(anchor, b -> b.addPiece(new Piece(anchor))));
    }

    @Override
    public StructureType<?> type() {
        return TYPE.get();
    }

    public static final class Piece extends StructurePiece {
        private final int surfaceY;

        public Piece(BlockPos anchor) {
            super(PIECE.get(), 0, new BoundingBox(
                    anchor.getX() - 6, anchor.getY() - 3, anchor.getZ() - 6,
                    anchor.getX() + 6, anchor.getY() + 7, anchor.getZ() + 6));
            this.surfaceY = anchor.getY();
        }

        public Piece(CompoundTag tag) {
            super(PIECE.get(), tag);
            this.surfaceY = tag.getInt("SurfaceY");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
            tag.putInt("SurfaceY", surfaceY);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                                RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
            int cx = boundingBox.minX() + 6;
            int cz = boundingBox.minZ() + 6;
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    int r2 = dx * dx + dz * dz;
                    if (r2 > 38) continue;
                    int floorDy = r2 < 14 ? -2 : -1;
                    Block floorBlk = r2 < 8 ? Blocks.CRYING_OBSIDIAN
                            : r2 % 3 == 0 ? Blocks.GILDED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE_BRICKS;
                    setAt(level, box, cx + dx, surfaceY + floorDy, cz + dz, floorBlk);
                    for (int dy = floorDy + 1; dy <= 4; dy++) {
                        setAt(level, box, cx + dx, surfaceY + dy, cz + dz, Blocks.AIR);
                    }
                }
            }
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                int px = cx + dir.getStepX() * 4;
                int pz = cz + dir.getStepZ() * 4;
                for (int dy = 0; dy <= 3; dy++) {
                    setAt(level, box, px, surfaceY + dy, pz, Blocks.POLISHED_BLACKSTONE_BRICKS);
                }
                setAt(level, box, px, surfaceY + 4, pz, Blocks.AMETHYST_BLOCK);
                setAt(level, box, px, surfaceY + 5, pz, Blocks.SEA_LANTERN);
            }
            setAt(level, box, cx, surfaceY - 1, cz, Blocks.GOLD_BLOCK);
            BlockPos chestPos = new BlockPos(cx, surfaceY, cz);
            if (box.isInside(chestPos)) {
                createChest(level, box, random, chestPos, VAULT_LOOT, Blocks.CHEST.defaultBlockState());
            }
        }

        private static void setAt(WorldGenLevel level, BoundingBox box, int x, int y, int z, Block blk) {
            BlockPos p = new BlockPos(x, y, z);
            if (box.isInside(p)) {
                level.setBlock(p, blk.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }
}

package cn.academy.port.city;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Building materials for the Academy City: facade panels, curtain glass, neon, pillars, fins and road surfaces. */
public final class CityBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("academy");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("academy");
    private static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.chunk.ChunkGenerator>> GENERATORS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CHUNK_GENERATOR, "academy");
    static {
        GENERATORS.register("city_greybox", () -> CityGreyboxGenerator.CODEC);
    }
    private static final Map<String, DeferredBlock<Block>> ALL = new LinkedHashMap<>();
    private static final List<DeferredItem<BlockItem>> ITEM_LIST = new ArrayList<>();

    static {
        for (CityBlockTable.Spec spec : CityBlockTable.SPECS) {
            DeferredBlock<Block> block = BLOCKS.register(spec.id(), () -> create(spec));
            ALL.put(spec.id(), block);
            ITEM_LIST.add(ITEMS.registerSimpleBlockItem(block));
        }
    }

    private CityBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        GENERATORS.register(bus);
        CityWindContent.register(bus);
        CityInteriorContent.register(bus);
        CityPlatformGate.register(bus);
        bus.addListener(CityBlocks::creative);
    }

    public static Block block(String id) { return ALL.get(id).get(); }

    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) ITEM_LIST.forEach(event::accept);
    }

    private static Block create(CityBlockTable.Spec spec) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().strength(spec.hardness(), 6f)
                .sound(SoundType.STONE).lightLevel(state -> spec.light());
        switch (spec.kind()) {
            case GLASS -> {
                return new TransparentBlock(p.sound(SoundType.GLASS).noOcclusion().isValidSpawn((s, g, pos, t) -> false)
                        .isRedstoneConductor((s, g, pos) -> false).isSuffocating((s, g, pos) -> false)
                        .isViewBlocking((s, g, pos) -> false));
            }
            case PILLAR -> { return new RotatedPillarBlock(p); }
            case SLAB -> { return new SlabBlock(p); }
            case THIN -> {
                return new Facing(p.sound(SoundType.GLASS).noOcclusion(), 0, 0, 0, 16, 16, 2);
            }
            case FIN -> { return new Facing(p.noOcclusion(), 6, 0, 0, 10, 16, 5); }
            case CEILING_LIGHT -> { return new CeilingLight(p.noOcclusion()); }
            default -> { return new Block(p); }
        }
    }

    private static final class CeilingLight extends Block {
        private static final MapCodec<CeilingLight> CODEC = simpleCodec(CeilingLight::new);
        private static final VoxelShape SHAPE = Shapes.or(
                box(1, 15.25, 2, 15, 16, 14),
                box(0.5, 13, 1.5, 15.5, 15.25, 14.5),
                box(1.4, 12.35, 2.4, 14.6, 13, 13.6));

        private CeilingLight(Properties properties) { super(properties); }

        @Override protected MapCodec<CeilingLight> codec() { return CODEC; }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }
    }

    /** A box attached to the north edge of its cell, rotated by FACING (the model is authored facing north). */
    private static final class Facing extends HorizontalDirectionalBlock {
        private static final MapCodec<Facing> CODEC = simpleCodec(properties -> new Facing(properties, 0, 0, 0, 16, 16, 2));
        private final VoxelShape[] shapes = new VoxelShape[4];

        Facing(Properties properties, double x0, double y0, double z0, double x1, double y1, double z1) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
            double[] box = {x0, z0, x1, z1};
            for (int i = 0; i < 4; i++) {                       // north, east, south, west = 0, 90, 180, 270 degrees
                shapes[i] = Block.box(box[0], y0, box[1], box[2], y1, box[3]);
                box = new double[] {16 - box[3], box[0], 16 - box[1], box[2]};
            }
        }

        @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return switch (state.getValue(FACING)) {
                case EAST -> shapes[1];
                case SOUTH -> shapes[2];
                case WEST -> shapes[3];
                default -> shapes[0];
            };
        }
    }
}

package cn.academy.port.city;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Decorative, non-ticking turbine heads for the city generator. */
public final class CityWindContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("academy");
    private static final DeferredRegister<BlockEntityType<?>> TILES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "academy");

    public static final DeferredBlock<HeadBlock> HEAD = BLOCKS.register("city_wind_head",
            () -> new HeadBlock(BlockBehaviour.Properties.of().strength(2f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeadEntity>> HEAD_TILE =
            TILES.register("city_wind_head", () -> BlockEntityType.Builder.of(HeadEntity::new, HEAD.get()).build(null));

    private CityWindContent() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        TILES.register(bus);
    }

    public static final class HeadBlock extends BaseEntityBlock {
        private static final MapCodec<HeadBlock> CODEC = simpleCodec(HeadBlock::new);

        private HeadBlock(Properties properties) { super(properties); }

        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HeadEntity(pos, state); }
    }

    public static final class HeadEntity extends BlockEntity {
        public HeadEntity(BlockPos pos, BlockState state) { super(HEAD_TILE.get(), pos, state); }
    }
}

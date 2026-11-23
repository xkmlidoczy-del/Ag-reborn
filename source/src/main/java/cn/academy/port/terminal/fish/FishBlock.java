package cn.academy.port.terminal.fish;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class FishBlock extends Block implements EntityBlock {
    public static final int VARIANT_COUNT = 3;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final IntegerProperty VARIANT = IntegerProperty.create("portrait", 0, VARIANT_COUNT - 1);
    public static final BooleanProperty SHOW = BooleanProperty.create("projection");
    public static final MapCodec<FishBlock> CODEC = simpleCodec(FishBlock::new);
    private static final VoxelShape BASE = box(0, 0, 0, 16, 4, 16);

    FishBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(VARIANT, 0).setValue(SHOW, true));
    }

    @Override
    protected MapCodec<FishBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, VARIANT, SHOW);
    }

    public static boolean upper(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos above = context.getClickedPos().above();
        if (level.isOutsideBuildHeight(above) || !level.getBlockState(above).canBeReplaced(context)) return null;
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) return;
        level.setBlock(pos.above(), defaultBlockState().setValue(HALF, DoubleBlockHalf.UPPER), UPDATE_ALL);
        level.setBlock(pos, state.setValue(VARIANT, level.random.nextInt(VARIANT_COUNT)), UPDATE_ALL);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return upper(state) ? below.is(this) && !upper(below)
                : below.isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        boolean upper = upper(state);
        if (side == (upper ? Direction.DOWN : Direction.UP) && (!neighbor.is(this) || upper(neighbor) == upper)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (!upper && side == Direction.DOWN && !canSurvive(state, level, pos)) {
            level.scheduleTick(pos, this, 1);
        }
        return super.updateShape(state, side, neighbor, level, pos, neighborPos);
    }

    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
            net.minecraft.util.RandomSource random) {
        if (!canSurvive(state, level, pos)) level.destroyBlock(pos, true);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!level.isClientSide && !replacement.is(this)) {
            BlockPos otherPos = upper(state) ? pos.below() : pos.above();
            BlockState other = level.getBlockState(otherPos);
            if (other.is(this) && upper(other) != upper(state)) {
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), UPDATE_ALL | UPDATE_SUPPRESS_DROPS);
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos base = upper(state) ? pos.below() : pos;
        if (!FishMenu.isTerminal(level.getBlockState(base))) return InteractionResult.PASS;
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider(
                (id, inventory, owner) -> new FishMenu(id, inventory, ContainerLevelAccess.create(level, base)),
                Component.translatable(getDescriptionId())));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return upper(state) ? Shapes.block() : BASE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return upper(state) ? Shapes.empty() : BASE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return upper(state) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public FishTile newBlockEntity(BlockPos pos, BlockState state) {
        return upper(state) ? null : new FishTile(pos, state);
    }
}

/* AcademyCraft1.0.7 BlockMatrix + LambdaLib occupied-cell adapter, GPLv3. See NOTICE. */
package cn.academy.port.wireless;

import cn.academy.port.machine.MachineDeveloperRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class ClassicWirelessMatrixBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART=IntegerProperty.create("part",0,7);
    public ClassicWirelessMatrixBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PART,0));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(ClassicWirelessMatrixBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,PART);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,Direction.valueOf(MachineDeveloperRules.fromYaw(context.getRotation()).name()));}
    public static BlockPos offset(int part,Direction facing){var cell=ClassicWirelessRules.offset(part,MachineDeveloperRules.Facing.valueOf(facing.name()));return new BlockPos(cell.x(),cell.y(),cell.z());}
    public static BlockPos origin(BlockPos pos,BlockState state){return pos.subtract(offset(state.getValue(PART),state.getValue(FACING)));}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ClassicWirelessMatrixBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,ClassicWirelessDevices.MATRIX_TILE.get(),ClassicWirelessMatrixBlockEntity::serverTick);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide&&placer instanceof Player player&&level.getBlockEntity(pos) instanceof ClassicWirelessMatrixBlockEntity matrix)matrix.setPlacer(player);}
    private InteractionResult use(Level level,BlockPos pos,Player player){if(player.isShiftKeyDown())return InteractionResult.PASS;if(!level.isClientSide&&player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof ClassicWirelessMatrixBlockEntity tile){var root=tile.origin();if(root!=null)ClassicWirelessProtocol.open(server,root.getBlockPos(),ClassicWirelessMenu.Kind.MATRIX);}return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return use(level,pos,player);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return use(level,pos,player).consumesAction()?ItemInteractionResult.sidedSuccess(level.isClientSide):ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()&&!level.isClientSide)ClassicWirelessMatrixStructure.remove(level,pos,state);super.onRemove(state,level,pos,next,moving);}
}

/* AcademyCraft1.0.7 wind blocks and LambdaLib BlockMulti adapter. GPLv3/MIT; see NOTICE. */
package cn.academy.port.wind;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

/** All occupied cells retain the original full-cell selection/collision and invisible block render. */
public final class ClassicWindBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART=IntegerProperty.create("part",0,2);
    public final ClassicWindRules.Kind kind;
    public ClassicWindBlock(ClassicWindRules.Kind kind,Properties properties){super(properties);this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PART,0));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(properties->new ClassicWindBlock(kind,properties));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,PART);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,kind==ClassicWindRules.Kind.PILLAR?Direction.NORTH:Direction.valueOf(ClassicWindRules.fromYaw(context.getRotation()).name()));}
    public static BlockPos offset(BlockState state,int part){var block=(ClassicWindBlock)state.getBlock();var cell=ClassicWindRules.offset(block.kind,part,ClassicWindRules.Facing.valueOf(state.getValue(FACING).name()));return new BlockPos(cell.x(),cell.y(),cell.z());}
    public static BlockPos origin(BlockPos pos,BlockState state){return pos.subtract(offset(state,state.getValue(PART)));}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return switch(kind){case BASE->new ClassicWindBaseBlockEntity(pos,state);case MAIN->new ClassicWindMainBlockEntity(pos,state);case PILLAR->new ClassicWindPillarBlockEntity(pos,state);};}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){if(level.isClientSide)return null;return switch(kind){case BASE->createTickerHelper(type,ClassicWindGenerators.BASE_TILE.get(),ClassicWindBaseBlockEntity::serverTick);case MAIN->createTickerHelper(type,ClassicWindGenerators.MAIN_TILE.get(),ClassicWindMainBlockEntity::serverTick);case PILLAR->null;};}
    private InteractionResult use(Level level,BlockPos pos,Player player){if(player.isShiftKeyDown()||kind==ClassicWindRules.Kind.PILLAR)return InteractionResult.PASS;if(!level.isClientSide&&player instanceof ServerPlayer server){var root=ClassicWindStructure.inventory(level,pos,level.getBlockState(pos));if(root!=null&&root.available())server.openMenu(new net.minecraft.world.SimpleMenuProvider(root::createMenu,root.getDisplayName()),wire->{wire.writeBoolean(root instanceof ClassicWindBaseBlockEntity);wire.writeBlockPos(root.getBlockPos());});}return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return use(level,pos,player);}
    @Override protected ItemInteractionResult useItemOn(ItemStack item,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(kind==ClassicWindRules.Kind.BASE&&item.is(ClassicWindGenerators.PILLAR_ITEM.get()))return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;return use(level,pos,player).consumesAction()?ItemInteractionResult.sidedSuccess(level.isClientSide):ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()&&!level.isClientSide)ClassicWindStructure.remove(level,pos,state);super.onRemove(state,level,pos,next,moving);}
}

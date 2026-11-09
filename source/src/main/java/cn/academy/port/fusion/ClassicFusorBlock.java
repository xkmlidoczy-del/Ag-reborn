/* AcademyCraft1.0.7 BlockImagFusor, native model/menu adapter. GPLv3. */
package cn.academy.port.fusion;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
public final class ClassicFusorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WORKING=BooleanProperty.create("working");
    public ClassicFusorBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(WORKING,false));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(ClassicFusorBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,WORKING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ClassicFusorBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,ClassicFusion.FUSOR_TILE.get(),ClassicFusorBlockEntity::serverTick);}
    private InteractionResult use(Level level,BlockPos pos,Player player){if(player.isShiftKeyDown())return InteractionResult.PASS;if(!level.isClientSide&&player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof ClassicFusorBlockEntity tile)server.openMenu(new SimpleMenuProvider(tile::createMenu,tile.getDisplayName()),pos);return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return use(level,pos,player);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return use(level,pos,player).consumesAction()?ItemInteractionResult.sidedSuccess(level.isClientSide):ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof ClassicFusorBlockEntity tile){Containers.dropContents(level,pos,tile);level.updateNeighbourForOutputSignal(pos,this);}super.onRemove(state,level,pos,next,moving);}}
}

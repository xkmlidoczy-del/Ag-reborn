/* AcademyCraft 1.0.7 BlockMetalFormer / ACBlockContainer source cube. GPLv3. */
package cn.academy.port.former;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
public final class ClassicMetalFormerBlock extends BaseEntityBlock {
    public static final IntegerProperty ROTATION=IntegerProperty.create("rotation",0,3);
    public ClassicMetalFormerBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(ROTATION,0));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(ClassicMetalFormerBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(ROTATION);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){float yaw=context.getPlayer()==null?context.getHorizontalDirection().toYRot():context.getPlayer().getYRot();return defaultBlockState().setValue(ROTATION,Mth.floor(yaw*4/360+.5)&3);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ClassicMetalFormerBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,ClassicMetalFormer.TILE.get(),ClassicMetalFormerBlockEntity::serverTick);}
    private InteractionResult use(Level level,BlockPos pos,Player player){if(player.isShiftKeyDown())return InteractionResult.PASS;if(!level.isClientSide&&player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof ClassicMetalFormerBlockEntity tile&&tile.stillValid(player))server.openMenu(new SimpleMenuProvider(tile::createMenu,tile.getDisplayName()),pos);return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return use(level,pos,player);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return use(level,pos,player).consumesAction()?ItemInteractionResult.sidedSuccess(level.isClientSide):ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof ClassicMetalFormerBlockEntity tile){Containers.dropContents(level,pos,tile);level.updateNeighbourForOutputSignal(pos,this);}super.onRemove(state,level,pos,next,moving);}}
}

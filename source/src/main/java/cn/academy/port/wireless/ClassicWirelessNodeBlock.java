/* AcademyCraft1.0.7 BlockNode/ACBlockContainer native block, GPLv3. See NOTICE. */
package cn.academy.port.wireless;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class ClassicWirelessNodeBlock extends BaseEntityBlock {
    public static final BooleanProperty CONNECTED=BooleanProperty.create("connected");
    public static final IntegerProperty ENERGY_LEVEL=IntegerProperty.create("energy_level",0,4);
    public final ClassicWirelessRules.NodeType nodeType;
    public ClassicWirelessNodeBlock(ClassicWirelessRules.NodeType type,Properties properties){super(properties);nodeType=type;registerDefaultState(stateDefinition.any().setValue(CONNECTED,false).setValue(ENERGY_LEVEL,0));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(properties->new ClassicWirelessNodeBlock(nodeType,properties));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(CONNECTED,ENERGY_LEVEL);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ClassicWirelessNodeBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,ClassicWirelessDevices.NODE_TILE.get(),ClassicWirelessNodeBlockEntity::serverTick);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide&&placer instanceof Player player&&level.getBlockEntity(pos) instanceof ClassicWirelessNodeBlockEntity tile)tile.setPlacer(player);}
    private InteractionResult use(Level level,BlockPos pos,Player player){if(player.isShiftKeyDown())return InteractionResult.PASS;if(!level.isClientSide&&player instanceof ServerPlayer server)ClassicWirelessProtocol.open(server,pos,ClassicWirelessMenu.Kind.NODE);return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return use(level,pos,player);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return use(level,pos,player).consumesAction()?ItemInteractionResult.sidedSuccess(level.isClientSide):ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof ClassicWirelessNodeBlockEntity tile)Containers.dropContents(level,pos,tile);super.onRemove(state,level,pos,next,moving);}}
}

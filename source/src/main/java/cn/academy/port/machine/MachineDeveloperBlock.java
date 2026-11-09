/* AcademyCraft 1.0.7 developer multiblock, modern engine adapter. See NOTICE. */
package cn.academy.port.machine;

import cn.academy.port.develop.DeveloperType;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;

/** Eight occupied, full-collision cells, empty native block render plus original OBJ renderer. */
public final class MachineDeveloperBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART=IntegerProperty.create("part",0,7);
    public final DeveloperType type;
    public MachineDeveloperBlock(DeveloperType type,Properties properties){super(properties);if(type==DeveloperType.PORTABLE)throw new IllegalArgumentException("machine tier");this.type=type;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PART,0));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(properties->new MachineDeveloperBlock(type,properties));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> builder){builder.add(FACING,PART);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,direction(MachineDeveloperRules.fromYaw(context.getRotation())));}
    public static Direction direction(MachineDeveloperRules.Facing facing){return Direction.valueOf(facing.name());}
    public static MachineDeveloperRules.Facing orientation(Direction facing){return MachineDeveloperRules.Facing.valueOf(facing.name());}
    public static BlockPos offset(int part,Direction facing){var cell=MachineDeveloperRules.offset(part,orientation(facing));return new BlockPos(cell.x(),cell.y(),cell.z());}
    public static BlockPos origin(BlockPos pos,BlockState state){return pos.subtract(offset(state.getValue(PART),state.getValue(FACING)));}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new MachineDeveloperBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,MachineDevelopers.BLOCK_ENTITY.get(),MachineDeveloperBlockEntity::serverTick);}
    private InteractionResult interact(Level level,BlockPos pos,Player player){
        if(player.isShiftKeyDown())return InteractionResult.PASS;
        if(!level.isClientSide&&player instanceof net.minecraft.server.level.ServerPlayer server){var blockEntity=level.getBlockEntity(pos);if(blockEntity instanceof MachineDeveloperBlockEntity developer)developer.use(server);}
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return interact(level,pos,player);}
    @Override protected ItemInteractionResult useItemOn(ItemStack item,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return interact(level,pos,player).consumesAction()?ItemInteractionResult.sidedSuccess(level.isClientSide):ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()&&!level.isClientSide)MachineDeveloperStructure.remove(level,pos,state);super.onRemove(state,level,pos,next,moving);}
}

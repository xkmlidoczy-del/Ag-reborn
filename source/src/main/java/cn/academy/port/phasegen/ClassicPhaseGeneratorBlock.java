/* AcademyCraft1.0.7 BlockPhaseGen/ACBlockContainer single-cell adapter. GPLv3. See NOTICE. */
package cn.academy.port.phasegen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.phys.BlockHitResult;

/** Source block has a full-cell collision box and no facing or active block state. */
public final class ClassicPhaseGeneratorBlock extends BaseEntityBlock {
    public ClassicPhaseGeneratorBlock(Properties properties){super(properties);}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(ClassicPhaseGeneratorBlock::new);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ClassicPhaseGeneratorBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,ClassicPhaseGenerators.TILE.get(),ClassicPhaseGeneratorBlockEntity::serverTick);}
    private InteractionResult use(Level level,BlockPos pos,Player player){if(player.isShiftKeyDown())return InteractionResult.PASS;if(!level.isClientSide&&player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof ClassicPhaseGeneratorBlockEntity phase)server.openMenu(new net.minecraft.world.SimpleMenuProvider(phase::createMenu,phase.getDisplayName()),pos);return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return use(level,pos,player);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return use(level,pos,player).consumesAction()?ItemInteractionResult.sidedSuccess(level.isClientSide):ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof ClassicPhaseGeneratorBlockEntity phase){Containers.dropContents(level,pos,phase);phase.clearContent();level.updateNeighbourForOutputSignal(pos,this);}super.onRemove(state,level,pos,next,moving);}}
}

/* AcademyCraft1.0.7 BlockConverterBase/BlockRFInput/BlockRFOutput. GPLv3; see NOTICE. */
package cn.academy.port.bridge;
import cn.academy.port.wireless.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
public final class ClassicEnergyBridgeBlock extends BaseEntityBlock {
    public final boolean input;
    public ClassicEnergyBridgeBlock(boolean input,Properties properties){super(properties);this.input=input;}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(properties->new ClassicEnergyBridgeBlock(input,properties));}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return input?new ClassicRFInputBlockEntity(pos,state):new ClassicRFOutputBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:input?createTickerHelper(type,ClassicEnergyBridges.INPUT_TILE.get(),ClassicEnergyBridgeBlockEntity::serverTick):createTickerHelper(type,ClassicEnergyBridges.OUTPUT_TILE.get(),ClassicEnergyBridgeBlockEntity::serverTick);}
    private InteractionResult use(Level level,BlockPos pos,Player player){if(!level.hasChunkAt(pos)||player.isShiftKeyDown()||!(level.getBlockEntity(pos) instanceof ClassicEnergyBridgeBlockEntity))return InteractionResult.PASS;if(!level.isClientSide&&player instanceof ServerPlayer server)ClassicWirelessProtocol.open(server,pos,ClassicWirelessMenu.Kind.USER);return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return use(level,pos,player);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return use(level,pos,player).consumesAction()?ItemInteractionResult.sidedSuccess(level.isClientSide):ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
}

/* AcademyCraft1.0.7 BlockCatEngine native use routes; no source GUI/sneaking exception. GPLv3. */
package cn.academy.port.cat;
import cn.academy.port.wireless.ClassicWirelessSavedData;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
public final class ClassicCatEngineBlock extends BaseEntityBlock {
    public ClassicCatEngineBlock(Properties properties){super(properties);}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(ClassicCatEngineBlock::new);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ClassicCatEngineBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,ClassicCatEngines.TILE.get(),ClassicCatEngineBlockEntity::serverTick);}
    private InteractionResult use(Level level,BlockPos pos,Player player){
        if(!(level.getBlockEntity(pos) instanceof ClassicCatEngineBlockEntity cat))return InteractionResult.PASS;
        if(!level.isClientSide&&level instanceof ServerLevel server&&cat.available()){
            var graph=ClassicWirelessSavedData.get(server).graph();var own=ClassicWirelessSavedData.pos(pos);
            if(graph.nodeForGenerator(own)!=null){graph.unlinkGenerator(own);player.sendSystemMessage(Component.translatable("ac.cat_engine.unlink"));}
            else{
                var nodes=graph.nearbyNodes(own,20,100);
                if(nodes.isEmpty())player.sendSystemMessage(Component.translatable("ac.cat_engine.notfound"));
                else{var selected=nodes.get(level.random.nextInt(nodes.size())).node();
                    if(graph.linkGenerator(selected,own,"invalid",false)&&level.getBlockEntity(new BlockPos(selected.x(),selected.y(),selected.z())) instanceof cn.academy.port.wireless.ClassicWirelessNodeBlockEntity node)
                        player.sendSystemMessage(Component.translatable("ac.cat_engine.linked",node.getNodeName()));
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return use(level,pos,player);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return use(level,pos,player).consumesAction()?ItemInteractionResult.sidedSuccess(level.isClientSide):ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()){if(level instanceof ServerLevel server){var data=ClassicWirelessSavedData.getNonCreate(server);if(data!=null)data.graph().unlinkGenerator(ClassicWirelessSavedData.pos(pos));}super.onRemove(state,level,pos,next,moving);}}
}

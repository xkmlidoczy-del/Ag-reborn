/* AcademyCraft1.0.7 AbilityInterferer full dynamic textured cube. GPLv3; see NOTICE. */
package cn.academy.port.interferer;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
public final class ClassicAbilityInterfererBlock extends BaseEntityBlock {
    public static final BooleanProperty ENABLED=BooleanProperty.create("enabled");
    public ClassicAbilityInterfererBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(ENABLED,false));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(ClassicAbilityInterfererBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(ENABLED);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ClassicAbilityInterfererBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,ClassicAbilityInterferers.TILE.get(),ClassicAbilityInterfererBlockEntity::serverTick);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){if(!level.isClientSide&&placer instanceof Player player&&level.getBlockEntity(pos) instanceof ClassicAbilityInterfererBlockEntity tile)tile.setPlacer(player);}
    private InteractionResult use(Level level,BlockPos pos,Player player){if(!level.isClientSide&&player instanceof net.minecraft.server.level.ServerPlayer server)ClassicInterfererNetwork.open(server,pos);return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return use(level,pos,player);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){use(level,pos,player);return ItemInteractionResult.sidedSuccess(level.isClientSide);}
}

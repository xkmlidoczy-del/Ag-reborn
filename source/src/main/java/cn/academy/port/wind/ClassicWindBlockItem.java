/* LambdaLib1.2.3 ItemBlockMulti placement adapter. MIT; see NOTICE. */
package cn.academy.port.wind;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
public final class ClassicWindBlockItem extends BlockItem {
    public ClassicWindBlockItem(ClassicWindBlock block,Properties properties){super(block,properties);}
    @Override protected boolean canPlace(BlockPlaceContext context,BlockState state){return super.canPlace(context,state)&&ClassicWindStructure.canPlace(context,state);}
    @Override protected boolean placeBlock(BlockPlaceContext context,BlockState state){return ClassicWindStructure.place(context,state);}
    @Override protected boolean updateCustomBlockEntityTag(net.minecraft.core.BlockPos pos,net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.item.ItemStack stack,BlockState state){return false;}
}

package cn.academy.port.machine;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Standard vanilla consume/sound/advancement behavior after a successful eight-cell transaction. */
public final class MachineDeveloperItem extends BlockItem {
    public MachineDeveloperItem(MachineDeveloperBlock block,Properties properties){super(block,properties);}
    @Override protected boolean canPlace(BlockPlaceContext context,BlockState state){return super.canPlace(context,state)&&MachineDeveloperStructure.canPlace(context,state);}
    @Override protected boolean placeBlock(BlockPlaceContext context,BlockState state){return MachineDeveloperStructure.place(context,state);}
    /** Source ItemBlockMulti does not carry charged tile NBT into freshly placed devices. */
    @Override protected boolean updateCustomBlockEntityTag(net.minecraft.core.BlockPos pos,net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.item.ItemStack stack,BlockState state){return false;}
}

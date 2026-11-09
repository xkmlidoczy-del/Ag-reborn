/* AcademyCraft1.0.7 BlockConverterBase.Item. GPLv3; see NOTICE. */
package cn.academy.port.bridge;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
public final class ClassicEnergyBridgeItem extends BlockItem {
    private final boolean input;
    public ClassicEnergyBridgeItem(ClassicEnergyBridgeBlock block,Properties properties){super(block,properties);input=block.input;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){super.appendHoverText(stack,context,lines,flag);lines.add(Component.translatable("ac.converter.desc_template",input?"RF":"IF",input?"IF":"RF"));}
}

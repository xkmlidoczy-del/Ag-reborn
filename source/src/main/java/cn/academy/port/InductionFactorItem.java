package cn.academy.port;
import cn.academy.port.develop.InductionFactors;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;
public final class InductionFactorItem extends Item {
    public InductionFactorItem(Properties properties){super(properties.stacksTo(1));}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){super.appendHoverText(stack,context,lines,flag);InductionFactors.category(stack).ifPresent(category->lines.add(Component.translatable("ac.ability."+category+".name")));}
}

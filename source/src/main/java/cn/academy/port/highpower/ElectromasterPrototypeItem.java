package cn.academy.port.highpower;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import java.util.List;

/** Explicit prototype control item: no hidden migration of ordinary saved abilities. */
public final class ElectromasterPrototypeItem extends Item {
    public ElectromasterPrototypeItem(Properties properties) { super(properties); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResultHolder.fail(stack);
        if(player instanceof ServerPlayer server) {
            if(!ElectromasterPrototype.ready(server))return InteractionResultHolder.fail(stack);
            if(player.isShiftKeyDown())player.startUsingItem(hand);
            else if(!ElectromasterPrototype.cast(server))return InteractionResultHolder.fail(stack);
        } else if(player.isShiftKeyDown())player.startUsingItem(hand);
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("item.academy.electromaster_prototype.attack"));
        lines.add(Component.translatable("item.academy.electromaster_prototype.guard"));
        lines.add(Component.translatable("item.academy.electromaster_prototype.warning"));
    }
}

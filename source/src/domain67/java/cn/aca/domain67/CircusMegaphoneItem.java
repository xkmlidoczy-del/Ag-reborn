package cn.aca.domain67;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class CircusMegaphoneItem extends Item {
    public CircusMegaphoneItem(Properties properties) {
        super(properties.rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (player.isShiftKeyDown()) {
                Domain67Manager.handlePlayerAction(sp, Domain67Network.ACT_CIRCUS_BROADCAST, 0);
            } else {
                Domain67Manager.handlePlayerAction(sp, Domain67Network.ACT_EXPAND_DOMAIN, 0);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.aca_domain67.megaphone.1"));
        tooltip.add(Component.translatable("tooltip.aca_domain67.megaphone.2",
                Component.keybind("key.aca_domain67.domain_expand"), Component.keybind("key.aca_domain67.circus_broadcast")));
        tooltip.add(Component.translatable("tooltip.aca_domain67.megaphone.3",
                Component.keybind("key.aca_domain67.log_finisher"), Component.keybind("key.aca_domain67.bento_cameo")));
    }

    public static boolean hasMegaphone(Player player) {
        if (player == null) return false;
        if (player.getMainHandItem().is(AcaDomain67.CIRCUS_MEGAPHONE_67.get())
                || player.getOffhandItem().is(AcaDomain67.CIRCUS_MEGAPHONE_67.get())) {
            return true;
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(AcaDomain67.CIRCUS_MEGAPHONE_67.get())) return true;
        }
        return false;
    }
}

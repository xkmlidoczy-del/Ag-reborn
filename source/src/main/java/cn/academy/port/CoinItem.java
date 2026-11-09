package cn.academy.port;

import cn.academy.port.skill.CoinTosses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Tossing is server authoritative; visual clients only consume the toss payload. */
public final class CoinItem extends Item {
    public CoinItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server && !CoinTosses.toss(server, hand))
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}

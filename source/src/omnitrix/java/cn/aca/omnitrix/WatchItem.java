package cn.aca.omnitrix;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class WatchItem extends Item {
    private final Kind kind;

    public WatchItem(Kind kind) {
        super(new Properties().stacksTo(1));
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var held = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server && !WristService.equip(server, held)) {
            return InteractionResultHolder.fail(held);
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("watch.aca_omnitrix.equip_hint").withStyle(ChatFormatting.GRAY));
        if (kind == Kind.CARNITRIX) {
            lines.add(Component.translatable("watch.aca_omnitrix.carni_warning").withStyle(ChatFormatting.GOLD));
        }
    }
}

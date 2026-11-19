package cn.aca.infinity;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class InfinityGauntletItem extends Item {
    public InfinityGauntletItem() {
        super(new Properties()
                .stacksTo(1)
                .fireResistant()
                .rarity(Rarity.EPIC)
                .component(AcaInfinity.GAUNTLET_DATA.get(), GauntletData.EMPTY));
    }

    public static ItemStack createFullStack() {
        ItemStack stack = new ItemStack(AcaInfinity.INFINITY_GAUNTLET.get());
        stack.set(AcaInfinity.GAUNTLET_DATA.get(), GauntletData.FULL);
        return stack;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        GauntletData data = stack.getOrDefault(AcaInfinity.GAUNTLET_DATA.get(), GauntletData.EMPTY);
        return data.hasAllStones();
    }

    @Override
    public Component getName(ItemStack stack) {
        GauntletData data = stack.getOrDefault(AcaInfinity.GAUNTLET_DATA.get(), GauntletData.EMPTY);
        if (data.hasAllStones()) {
            return Component.translatable("item.aca_infinity.infinity_gauntlet.full").withStyle(ChatFormatting.GOLD);
        }
        return super.getName(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            WearerState wearer = AcaInfinity.wearer(sp);
            ItemStack previous = wearer.gauntlet.copy();
            wearer.gauntlet = held.copyWithCount(1);
            wearer.revision++;
            sp.setItemInHand(hand, previous);
            level.playSound(null, sp.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0f, 0.92f);
            level.playSound(null, sp.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.55f, 1.35f);
            sp.displayClientMessage(Component.translatable("msg.aca_infinity.gauntlet_equipped"), true);
            InfinityNetwork.syncWearer(sp);
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        GauntletData data = stack.getOrDefault(AcaInfinity.GAUNTLET_DATA.get(), GauntletData.EMPTY);
        tooltip.add(Component.translatable("tooltip.aca_infinity.gauntlet_count", data.count(), Stone.values().length)
                .withStyle(data.hasAllStones() ? ChatFormatting.GOLD : ChatFormatting.AQUA));

        MutableComponent row = Component.empty();
        for (Stone s : Stone.values()) {
            boolean has = data.has(s);
            row.append(Component.literal(has ? "◆ " : "◇ ").withStyle(has ? s.chatColor : ChatFormatting.DARK_GRAY));
        }
        tooltip.add(row);

        Stone sel = data.selected();
        if (data.has(sel)) {
            tooltip.add(Component.translatable("tooltip.aca_infinity.active_stone",
                    Component.translatable(sel.translationKey()).withStyle(sel.chatColor)).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("  [J] ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable(sel.primarySkillKey()).withStyle(ChatFormatting.WHITE)));
            tooltip.add(Component.literal("  [K] ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable(sel.secondarySkillKey()).withStyle(ChatFormatting.GRAY)));
        }
        if (data.hasAllStones()) {
            tooltip.add(Component.translatable("tooltip.aca_infinity.snap_ready").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        }
        tooltip.add(Component.translatable("tooltip.aca_infinity.gauntlet_controls").withStyle(ChatFormatting.DARK_GRAY));
    }
}

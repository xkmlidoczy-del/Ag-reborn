package cn.aca.infinity;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

public final class StoneItem extends Item {
    public final Stone stone;

    public StoneItem(Stone stone) {
        super(new Properties().stacksTo(16).fireResistant().rarity(Rarity.EPIC));
        this.stone = stone;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Check worn gauntlet first, then offhand/mainhand gauntlet
        WearerState wearer = AcaInfinity.wearer(player);
        if (wearer.equipped() && !wearer.data().has(stone)) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                wearer.updateData(wearer.data().withStone(stone).withSelected(stone));
                if (!sp.getAbilities().instabuild) stack.shrink(1);
                level.playSound(null, sp.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.9f, 1.15f);
                level.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.25f);
                sp.displayClientMessage(Component.translatable("msg.aca_infinity.stone_socketed",
                        Component.translatable(stone.translationKey()).withStyle(stone.chatColor)), true);
                InfinityNetwork.syncWearer(sp);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack other = player.getItemInHand(otherHand);
        if (other.is(AcaInfinity.INFINITY_GAUNTLET.get())) {
            GauntletData gData = other.getOrDefault(AcaInfinity.GAUNTLET_DATA.get(), GauntletData.EMPTY);
            if (!gData.has(stone)) {
                if (!level.isClientSide) {
                    other.set(AcaInfinity.GAUNTLET_DATA.get(), gData.withStone(stone).withSelected(stone));
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.9f, 1.15f);
                    player.displayClientMessage(Component.translatable("msg.aca_infinity.stone_socketed",
                            Component.translatable(stone.translationKey()).withStyle(stone.chatColor)), true);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.aca_infinity.cosmic_origin").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable(stone.primarySkillKey()).withStyle(stone.chatColor));
        tooltip.add(Component.translatable(stone.secondarySkillKey()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.aca_infinity.stone_socket_hint").withStyle(ChatFormatting.DARK_AQUA));
    }
}

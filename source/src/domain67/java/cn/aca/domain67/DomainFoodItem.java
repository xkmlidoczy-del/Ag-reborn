package cn.aca.domain67;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class DomainFoodItem extends Item {
    private final boolean bento;

    public DomainFoodItem(boolean bento) {
        super(new Properties()
                .rarity(bento ? Rarity.RARE : Rarity.UNCOMMON)
                .food(bento
                        ? new FoodProperties.Builder()
                        .nutrition(10).saturationModifier(1.0f).alwaysEdible()
                        .effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 160, 1), 1.0f)
                        .effect(() -> new MobEffectInstance(MobEffects.ABSORPTION, 400, 1), 1.0f)
                        .build()
                        : new FoodProperties.Builder()
                        .nutrition(8).saturationModifier(0.4f).alwaysEdible()
                        .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 80, 0), 1.0f)
                        .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0), 1.0f)
                        .build()));
        this.bento = bento;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack res = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && entity instanceof Player p) {
            if (bento) {
                p.displayClientMessage(Component.literal("§6[78元盒饭] §a贵是贵了点，但是吃了不烧心！"), true);
            } else {
                p.displayClientMessage(Component.literal("§7[67餐厅] 吃完了——没奖励，你可以走了。"), true);
            }
        }
        return res;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(bento ? "tooltip.aca_domain67.bento" : "tooltip.aca_domain67.noodles"));
    }
}

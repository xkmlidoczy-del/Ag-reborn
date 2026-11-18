package cn.aca.omnitrix;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class AscalonItem extends Item {
    public AscalonItem() {
        super(new Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .fireResistant()
                .component(DataComponents.UNBREAKABLE, new Unbreakable(false))
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE,
                                new AttributeModifier(Omnitrix.id("ascalon_damage"), 999_999_999.0, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED,
                                new AttributeModifier(Omnitrix.id("ascalon_speed"), 2.4, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ENTITY_INTERACTION_RANGE,
                                new AttributeModifier(Omnitrix.id("ascalon_reach"), 5.5, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .build()));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack))
                .withStyle(Style.EMPTY.withColor(0xdce8f8).withBold(true));
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof ServerPlayer sp) {
            AscalonService.swingCleave(sp, target);
        }
        return true;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72_000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer sp) {
                AscalonService.toggleArmor(sp);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        entity.setInvulnerable(true);
        entity.setUnlimitedLifetime();
        entity.setNoGravity(true);
        entity.clearFire();
        Vec3 v = entity.getDeltaMovement();
        if (entity.getY() < entity.level().getMinBuildHeight() + 4) {
            entity.setPos(entity.getX(), entity.level().getMinBuildHeight() + 12, entity.getZ());
            entity.setDeltaMovement(0, .18, 0);
        } else {
            entity.setDeltaMovement(v.x * .86, Math.sin(entity.tickCount * .12) * .018, v.z * .86);
        }
        if (entity.level() instanceof ServerLevel level) {
            if (entity.tickCount % 4 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY() + .35, entity.getZ(),
                        2, .16, .22, .16, .008);
            }
            // Canon Recalling: levitates back to its armored owner or thrower automatically
            if (entity.tickCount >= 18 && entity.getOwner() instanceof ServerPlayer owner
                    && owner.isAlive() && owner.level() == level && owner.distanceToSqr(entity) <= 96 * 96) {
                if (AscalonService.recallItemEntity(owner, entity)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("ascalon.aca_omnitrix.subtitle")
                .withStyle(Style.EMPTY.withColor(0x9be7ff)));
        tooltip.add(Component.translatable("ascalon.aca_omnitrix.invincible")
                .withStyle(Style.EMPTY.withColor(0xffe49c)));
        if (WristNetwork.client != null) {
            WristNetwork.client.appendAscalonTooltip(tooltip);
        } else {
            tooltip.add(Component.translatable("ascalon.aca_omnitrix.controls_1").withStyle(ChatFormatting.GRAY));
        }
    }
}

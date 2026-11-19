package cn.aca.infinity;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SanctuaryBeaconItem extends Item {
    public final boolean hostile;

    public SanctuaryBeaconItem(boolean hostile) {
        super(new Properties().stacksTo(16).fireResistant().rarity(Rarity.EPIC));
        this.hostile = hostile;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hostile;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(player.getLookAngle().scale(18.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, reach, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 spawnPos = hit.getType() == HitResult.Type.MISS
                ? player.position().add(player.getLookAngle().scale(8.0)).add(0, 3.0, 0)
                : Vec3.atBottomCenterOf(hit.getBlockPos().above(hostile ? 8 : 2));

        if (!level.isClientSide && level instanceof ServerLevel sl) {
            SanctuaryShipEntity ship = AcaInfinity.SANCTUARY_SHIP.get().create(sl);
            if (ship != null) {
                ship.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, player.getYRot(), 0f);
                ship.setHostileShip(hostile);
                if (!hostile) {
                    ship.setOwnerId(player.getUUID());
                }
                CompoundTag saved = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                if (saved.contains("Hull")) ship.setHull(saved.getInt("Hull"));
                if (saved.contains("Energy")) ship.setEnergy(saved.getInt("Energy"));

                sl.addFreshEntity(ship);
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, spawnPos.x, spawnPos.y + 1.4, spawnPos.z,
                        60, 2.2, 0.8, 2.2, 0.18);
                sl.playSound(null, BlockPos.containing(spawnPos), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.3f, 0.72f);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(hostile
                ? "tooltip.aca_infinity.beacon_hostile"
                : "tooltip.aca_infinity.beacon_friendly")
                .withStyle(hostile ? ChatFormatting.RED : ChatFormatting.AQUA));
    }
}

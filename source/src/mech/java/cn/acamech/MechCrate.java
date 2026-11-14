package cn.acamech;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

public final class MechCrate extends Item {
    private final Chassis chassis;

    public MechCrate(Chassis chassis) {
        super(new Item.Properties().stacksTo(1));
        this.chassis = chassis;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player pilot = context.getPlayer();
        if (pilot == null || context.getClickedFace() != Direction.UP || pilot.isPassenger()) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;

        Vec3 point = Vec3.atBottomCenterOf(context.getClickedPos().above());
        MechEntity mech = MechMod.entity(chassis).create(level);
        if (mech == null) return InteractionResult.FAIL;
        mech.moveTo(point.x, point.y, point.z, Mth.wrapDegrees(pilot.getYRot()), 0f);
        var box = mech.getBoundingBox();
        if (!level.getBlockState(context.getClickedPos()).isFaceSturdy(level, context.getClickedPos(), Direction.UP)
                || !level.hasChunkAt(mech.blockPosition())
                || !level.getWorldBorder().isWithinBounds(box) || !level.noCollision(mech, box)
                || level.containsAnyLiquid(box)
                || !level.getEntitiesOfClass(Player.class, box, p -> !p.isSpectator()).isEmpty()) {
            pilot.displayClientMessage(Component.translatable("message.acamech.deploy_clear"), true);
            return InteractionResult.FAIL;
        }

        mech.setOwner(pilot.getUUID());
        mech.readCrate(context.getItemInHand());
        if (!level.addFreshEntity(mech)) return InteractionResult.FAIL;
        if (!pilot.getAbilities().instabuild) context.getItemInHand().shrink(1);
        level.playSound(null, mech.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.BLOCKS, 1f, .72f);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.acamech." + chassis.id));
        var saved = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tooltip.add(Component.translatable("tooltip.acamech.state",
                saved.contains("Hull") ? saved.getInt("Hull") : chassis.hull,
                saved.contains("Charge") ? saved.getInt("Charge") : chassis.energy));
    }
}

/* AcademyCraft1.0.7 ItemMagHook adaptation. GPLv3; see NOTICE. */
package cn.academy.port.hook;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** One real thrown entity for each successful server use; original stack size64, no cooldown. */
public final class MagHookItem extends Item {
    public MagHookItem(Properties properties){super(properties);}
    @Override public InteractionResultHolder<ItemStack> use(Level world,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(!world.isClientSide){
            if(stack.isEmpty()||player.isSpectator()||!Double.isFinite(player.getX())||!Double.isFinite(player.getY())||!Double.isFinite(player.getZ())||!Float.isFinite(player.getYHeadRot())||!Float.isFinite(player.getXRot()))return InteractionResultHolder.fail(stack);
            MagHookEntity hook=new MagHookEntity(player);
            // Modern spawn-event cancellation must conserve the held item.
            if(!world.addFreshEntity(hook))return InteractionResultHolder.fail(stack);
            world.playSound(null,player.getX(),player.getY(),player.getZ(),SoundEvents.ARROW_SHOOT,SoundSource.PLAYERS,.5F,ClassicHookRules.throwPitch(world.random.nextFloat()));
            if(!player.getAbilities().instabuild)stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack,world.isClientSide);
    }
}

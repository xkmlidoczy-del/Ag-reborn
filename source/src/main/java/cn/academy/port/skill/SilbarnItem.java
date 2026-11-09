/* AcademyCraft1.0.7 ItemSilbarn adaptation, GPLv3; see NOTICE. */
package cn.academy.port.skill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
/** Native right-click use; consumed only after a successful authoritative spawn. */
public final class SilbarnItem extends Item {
 public SilbarnItem(Properties p){super(p);}
 @Override public String getDescriptionId(){return "item.ac_silbarn.name";}
 @Override public InteractionResultHolder<ItemStack> use(Level world,Player player,InteractionHand hand){var stack=player.getItemInHand(hand);if(world.isClientSide)return InteractionResultHolder.success(stack);if(!(player instanceof ServerPlayer p)||!MeltdownerLateSupport.ready(p))return InteractionResultHolder.fail(stack);var entity=new SilbarnEntity(p);if(!world.addFreshEntity(entity))return InteractionResultHolder.fail(stack);world.playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.ARROW_SHOOT,SoundSource.PLAYERS,.5F,.4F/(world.random.nextFloat()*.4F+.8F));if(!p.getAbilities().instabuild)stack.shrink(1);return InteractionResultHolder.consume(stack);}
}

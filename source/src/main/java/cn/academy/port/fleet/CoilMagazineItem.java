package cn.academy.port.fleet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** One physical, non-stackable energy module; bulk cells replenish it outside the rifle. */
public final class CoilMagazineItem extends Item {
 public static final int CAPACITY=24;
 public final int variant;
 public CoilMagazineItem(){this(2);}
 public CoilMagazineItem(int variant){super(new Item.Properties().stacksTo(1));this.variant=variant;}
 public static int capacity(ItemStack s){return s.getItem() instanceof CoilMagazineItem m?InteractionLab.CAPS[m.variant]:CAPACITY;}
 public static int energy(ItemStack s){return Math.max(0,Math.min(capacity(s),GunItem.data(s).getInt("c03_energy")));}
 public static void energy(ItemStack s,int n){CustomData.update(DataComponents.CUSTOM_DATA,s,t->t.putInt("c03_energy",Math.max(0,Math.min(capacity(s),n))));}
 public static ItemStack create(int n){ItemStack s=new ItemStack(InteractionLab.COIL_MAGAZINE.get());energy(s,n);return s;}
 public static ItemStack create(int variant,int n){ItemStack s=new ItemStack(InteractionLab.magazine(variant));energy(s,n);return s;}
 @Override public Component getName(ItemStack s){return Component.literal("G0"+(variant+1)+" 能源匣 ["+energy(s)+"/"+capacity(s)+"]");}
 @Override public InteractionResultHolder<ItemStack> use(Level level,Player p,InteractionHand hand){
  ItemStack s=p.getItemInHand(hand);if(!level.isClientSide){int need=capacity(s)-energy(s),loaded=0;for(ItemStack a:p.getInventory().items)if(a.is(variant>=4?InteractionLab.HEAVY_CELL.get():InteractionLab.LIGHT_CELL.get())){int n=Math.min(need-loaded,a.getCount());a.shrink(n);loaded+=n;if(loaded>=need)break;}energy(s,energy(s)+loaded);p.getInventory().setChanged();}
  return InteractionResultHolder.sidedSuccess(s,level.isClientSide);
 }
}

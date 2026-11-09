package cn.academy.port.fleet;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

/** Each transfer is committed once. Cancellation never rolls back a committed inventory transfer. */
public final class CoilReloads {
 private static final class Action {final ItemStack gun;final long start;final boolean empty;final int variant;boolean removed,inserted,locked,latched,guided,charged,settled;Action(ItemStack g,long t,boolean e){gun=g;start=t;empty=e;variant=((GunItem)g.getItem()).variant;}}
 private static final Map<UUID,Action> ACTIONS=new HashMap<>();
 public static boolean active(ServerPlayer p){return ACTIONS.containsKey(p.getUUID());}
 private static int held(net.minecraft.world.entity.player.Player p){return p.getMainHandItem().getItem() instanceof GunItem g?g.variant:2;}
 public static int reserve(net.minecraft.world.entity.player.Player p){return reserve(p,held(p));}
 public static int reserve(net.minecraft.world.entity.player.Player p,int variant){int n=0;for(ItemStack s:p.getInventory().items)if(s.is(InteractionLab.magazine(variant)))n+=CoilMagazineItem.energy(s);return n;}
 public static int magazines(net.minecraft.world.entity.player.Player p){return magazines(p,held(p));}
 public static int magazines(net.minecraft.world.entity.player.Player p,int variant){int n=0;for(ItemStack s:p.getInventory().items)if(s.is(InteractionLab.magazine(variant))&&CoilMagazineItem.energy(s)>0)n++;return n;}
 private static ItemStack best(ServerPlayer p,int variant){ItemStack best=ItemStack.EMPTY;int n=0;for(ItemStack s:p.getInventory().items)if(s.is(InteractionLab.magazine(variant))&&CoilMagazineItem.energy(s)>n){best=s;n=CoilMagazineItem.energy(s);}return best;}
 private static void sound(ServerPlayer p,SoundEvent s,float pitch){p.level().playSound(null,p.getX(),p.getY(),p.getZ(),s,SoundSource.PLAYERS,.55f,pitch);}
 public static void begin(ServerPlayer p,ItemStack gun){
  int variant=((GunItem)gun.getItem()).variant;var timing=GunSpec.timing(variant);
  if(active(p)||GunItem.ammo(gun)>=InteractionLab.CAPS[variant]&&GunItem.hasMagazine(gun)||best(p,variant).isEmpty())return;
  long now=p.level().getGameTime();boolean empty=GunItem.ammo(gun)<=0||!GunItem.hasMagazine(gun);Action a=new Action(gun,now,empty);ACTIONS.put(p.getUUID(),a);
  CustomData.update(DataComponents.CUSTOM_DATA,gun,t->{t.putBoolean("c03_reload_empty",empty);t.putInt("c03_reload_stage",1);});
  GunItem.reload(gun,now,now+(empty?timing.emptyEnd():timing.tacticalEnd()));
  InteractionLab.LOGGER.info("C03_RELOAD_BEGIN empty={} loaded={} reserve={} tick={}",empty,GunItem.ammo(gun),reserve(p),now);
 }
 public static void cancel(ServerPlayer p){Action a=ACTIONS.remove(p.getUUID());if(a==null)return;GunItem.reload(a.gun,0,0);stage(a.gun,0);AudioDefs.emit(p,a.variant,"cancel",a.start);InteractionLab.LOGGER.info("C03_RELOAD_CANCEL removed={} inserted={} loaded={} hasMag={}",a.removed,a.inserted,GunItem.ammo(a.gun),GunItem.hasMagazine(a.gun));}
 private static void stage(ItemStack s,int n){CustomData.update(DataComponents.CUSTOM_DATA,s,t->t.putInt("c03_reload_stage",n));}
 public static void tick(ServerPlayer p){
  Action a=ACTIONS.get(p.getUUID());if(a==null)return;if(!p.isAlive()||p.getMainHandItem()!=a.gun){cancel(p);return;}
  var timing=GunSpec.timing(a.variant);int t=(int)(p.level().getGameTime()-a.start);
  if(!a.latched&&t>=timing.release()-1){a.latched=true;AudioDefs.emit(p,a.variant,"latch",a.start);}
  int insertion=a.empty?timing.emptyInsert():timing.tacticalInsert(),end=a.empty?timing.emptyEnd():timing.tacticalEnd();
  if(!a.guided&&t>=insertion-3){a.guided=true;AudioDefs.emit(p,a.variant,"guide",a.start);}
  if(a.empty&&!a.charged&&t>=insertion+Math.round((timing.lock()-insertion)*.52f)){a.charged=true;AudioDefs.emit(p,a.variant,"charge",a.start);}
  if(!a.settled&&t>=end-2){a.settled=true;AudioDefs.emit(p,a.variant,"settle",a.start);}
  if(!a.removed&&t>=timing.release()){
   if(GunItem.hasMagazine(a.gun)){ItemStack old=CoilMagazineItem.create(a.variant,GunItem.ammo(a.gun));GunItem.ammo(a.gun,0);GunItem.hasMagazine(a.gun,false);int free=-1;for(int i=0;i<36;i++)if(p.getInventory().getItem(i).isEmpty()){free=i;break;}if(free>=0)p.getInventory().setItem(free,old);else{int oldEnergy=CoilMagazineItem.energy(old);p.drop(old,false);InteractionLab.LOGGER.info("C03_MAG_OLD_DROPPED energy={}",oldEnergy);}}
   a.removed=true;stage(a.gun,2);p.getInventory().setChanged();AudioDefs.emit(p,a.variant,"mag_out",a.start);InteractionLab.LOGGER.info("C03_MAG_REMOVED reserve={} hasMag={}",reserve(p),GunItem.hasMagazine(a.gun));
  }
  if(!a.inserted&&t>=(a.empty?timing.emptyInsert():timing.tacticalInsert())){
   ItemStack fresh=best(p,a.variant);if(fresh.isEmpty()){cancel(p);return;}
   int n=CoilMagazineItem.energy(fresh);fresh.shrink(1);GunItem.ammo(a.gun,n);GunItem.hasMagazine(a.gun,true);a.inserted=true;stage(a.gun,3);p.getInventory().setChanged();AudioDefs.emit(p,a.variant,"mag_in",a.start);InteractionLab.LOGGER.info("C03_MAG_INSERTED loaded={} reserve={} tick={}",n,reserve(p),p.level().getGameTime());
  }
  if(a.empty&&!a.locked&&t>=timing.lock()){a.locked=true;stage(a.gun,4);AudioDefs.emit(p,a.variant,"activate",a.start);}
  if(t>=(a.empty?timing.emptyEnd():timing.tacticalEnd())){GunItem.reload(a.gun,0,0);stage(a.gun,0);ACTIONS.remove(p.getUUID());InteractionLab.LOGGER.info("C03_RELOAD_DONE loaded={} reserve={}",GunItem.ammo(a.gun),reserve(p));}
 }
}

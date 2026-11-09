package cn.academy.port.fleet;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
/** Active only in the explicitly enabled, disposable local QA world. */
public final class CoilQa {
 private static final java.util.Map<java.util.UUID,Integer> EXPECTED=new java.util.HashMap<>();
 private static int total(ServerPlayer p){int n=0;for(ItemStack s:p.getInventory().items){if(s.getItem() instanceof GunItem g&&g.variant==2)n+=GunItem.ammo(s);if(s.is(InteractionLab.COIL_MAGAZINE.get()))n+=CoilMagazineItem.energy(s);if(s.is(InteractionLab.LIGHT_CELL.get()))n+=s.getCount();}for(ItemEntity e:p.serverLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(8)))if(e.getItem().is(InteractionLab.COIL_MAGAZINE.get()))n+=CoilMagazineItem.energy(e.getItem());return n;}
 private static ItemStack gun(ServerPlayer p){for(ItemStack s:p.getInventory().items)if(s.getItem() instanceof GunItem g&&g.variant==2)return s;return ItemStack.EMPTY;}
 private static int setup(ServerPlayer p,String name){
  CoilReloads.cancel(p);p.getInventory().clearContent();p.getInventory().selected=0;
  ItemStack g=new ItemStack(InteractionLab.GUNS[2].get());GunItem.ammo(g,name.equals("empty")?0:9);if(!name.equals("legacy"))GunItem.hasMagazine(g,true);p.getInventory().setItem(0,g);
  if(!name.equals("no_spare"))p.getInventory().setItem(1,CoilMagazineItem.create(24));
  if(name.equals("full"))for(int i=2;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
  if(name.equals("refill")){p.getInventory().setItem(0,CoilMagazineItem.create(7));p.getInventory().setItem(1,new ItemStack(InteractionLab.LIGHT_CELL.get(),17));}
  p.getInventory().setChanged();p.getPersistentData().putInt("c03qa_expected",total(p));EXPECTED.put(p.getUUID(),total(p));
  InteractionLab.LOGGER.info("C03_QA_SETUP case={} total={}",name,total(p));return 1;
 }
 private static int check(ServerPlayer p,String name){ItemStack g=gun(p);int expected=EXPECTED.getOrDefault(p.getUUID(),p.getPersistentData().getInt("c03qa_expected")),actual=total(p);boolean ok=expected==actual&&!CoilReloads.active(p);
  if(name.equals("refill"))ok&=p.getMainHandItem().is(InteractionLab.COIL_MAGAZINE.get())&&CoilMagazineItem.energy(p.getMainHandItem())==24;
  else if(name.equals("no_spare")||name.equals("cancel_before"))ok&=GunItem.ammo(g)==9&&GunItem.hasMagazine(g);
  else if(name.equals("cancel_removed")||name.equals("switch")||name.equals("reconnect")||name.equals("death"))ok&=GunItem.ammo(g)==0&&!GunItem.hasMagazine(g);
  else ok&=GunItem.ammo(g)==24&&GunItem.hasMagazine(g);
  InteractionLab.LOGGER.info("C03_QA_ASSERT case={} pass={} expected={} actual={} loaded={} hasMag={}",name,ok,expected,actual,GunItem.ammo(g),GunItem.hasMagazine(g));
  if(!ok)throw new IllegalStateException("C03 magazine conservation/state failed: "+name);return 1;
 }
 @SubscribeEvent public static void commands(RegisterCommandsEvent e){if(!Boolean.getBoolean("academy.qa.showcase")||!Boolean.getBoolean("academy.qa.confirmOwnWorld"))return;
  e.getDispatcher().register(Commands.literal("aclab_qa_mag").requires(s->s.hasPermission(2))
   .then(Commands.literal("setup").then(Commands.argument("case",StringArgumentType.word()).executes(c->setup(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"case")))))
   .then(Commands.literal("assert").then(Commands.argument("case",StringArgumentType.word()).executes(c->check(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"case")))))
   .then(Commands.literal("begin").executes(c->{var p=c.getSource().getPlayerOrException();LabGameplay.request(p,new LabNetwork.Request("reload",0,0));return 1;}))
   .then(Commands.literal("cancel").executes(c->{CoilReloads.cancel(c.getSource().getPlayerOrException());return 1;})));
 }
}

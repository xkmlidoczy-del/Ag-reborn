package cn.academy.port.qa;

import cn.academy.port.city.portal.*;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;

/** Fixtures build the pool; Q, walking, inventory clicks, portal tracking and restart use native paths. */
final class AmethystPortalQa {
    static final BlockPos SOURCE=new BlockPos(40,-61,24),FURNACE=new BlockPos(37,-60,24);
    private static final Map<String,Object> RECEIPT=new LinkedHashMap<>();
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("QA_AMETHYST "+why);}
    private static <T>T server(Minecraft mc,java.util.function.Supplier<T> work){return mc.getSingleplayerServer().submit(work).join();}
    private static ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private static void receipt(Minecraft mc){try{var root=Path.of(System.getProperty("academy.qa.portal.root",mc.gameDirectory.getAbsolutePath()));Files.createDirectories(root);Files.writeString(root.resolve("portal-receipt.json"),new GsonBuilder().setPrettyPrinting().create().toJson(RECEIPT));}catch(Exception e){throw new IllegalStateException(e);}}
    static void step(Minecraft mc,String op){
        switch(op){
            case "take","store","hover"->{
                require(mc.screen instanceof AbstractContainerScreen<?>,"real furnace screen open");var screen=(AbstractContainerScreen<?>)mc.screen;
                int index=op.equals("take")?2:31;var slot=mc.player.containerMenu.getSlot(index);int x=screen.getGuiLeft()+slot.x+8,y=screen.getGuiTop()+slot.y+8;
                if(op.equals("hover")){
                    try{var method=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove",long.class,double.class,double.class);method.setAccessible(true);method.invoke(mc.mouseHandler,mc.getWindow().getWindow(),x*(double)mc.getWindow().getScreenWidth()/mc.getWindow().getGuiScaledWidth(),y*(double)mc.getWindow().getScreenHeight()/mc.getWindow().getGuiScaledHeight());}catch(Exception e){throw new IllegalStateException(e);}
                }else require(screen.mouseClicked(x,y,0),"native menu click accepted");
            }
            default->server(mc,()->{
                var p=player(mc);var world=p.serverLevel();
                switch(op){
                    case "setup"->{
                        require(Boolean.getBoolean("academy.qa.confirmOwnWorld"),"isolated save required");require(world.dimension().equals(Level.OVERWORLD),"initial world is overworld");p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.setHealth(20);p.getFoodData().setFoodLevel(20);
                        world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);world.setDayTime(6000);world.setWeatherParameters(6000,0,false,false);
                        for(int x=34;x<=47;x++)for(int z=18;z<=31;z++){world.setBlock(new BlockPos(x,-62,z),Blocks.DIRT.defaultBlockState(),3);world.setBlock(new BlockPos(x,-61,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);for(int y=-60;y<=-57;y++)world.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
                        for(var o:AmethystPortalRitual.WATER)world.setBlock(SOURCE.offset(o),Blocks.WATER.defaultBlockState(),3);
                        for(var o:AmethystPortalRitual.FIBERS)world.setBlock(SOURCE.offset(o),AmethystPortalContent.FIBER.get().defaultBlockState(),3);
                        world.setBlock(FURNACE,Blocks.FURNACE.defaultBlockState(),3);var f=(FurnaceBlockEntity)world.getBlockEntity(FURNACE);f.setItem(0,new ItemStack(Items.AMETHYST_SHARD));f.setItem(1,new ItemStack(Items.COAL));
                        p.teleportTo(40.5,-60,22.25);p.setYRot(0);p.setXRot(20);p.getInventory().selected=0;p.getInventory().setItem(0,new ItemStack(Items.DIRT,3));
                        require(!p.hasPermissions(2),"ordinary player has no command permission");require(AmethystPortalRitual.sourcePool(world,SOURCE),"water and eight fibers ready");
                        RECEIPT.put("source",SOURCE.toShortString());RECEIPT.put("ordinary_survival_player",true);RECEIPT.put("fixture","water/soil/eight plants and furnace ingredients supplied; ritual offering, pickup, travel and return are native inputs");
                        LogUtils.getLogger().info("QA_AMETHYST_SETUP normal_player=true op=false");
                    }
                    case "showfiber"->{p.openMenu((FurnaceBlockEntity)world.getBlockEntity(FURNACE));}
                    case "furnace"->{var f=(FurnaceBlockEntity)world.getBlockEntity(FURNACE);require(f.getItem(2).is(AmethystPortalContent.FIBER_ITEM.get()),"real furnace produced fiber");p.openMenu(f);RECEIPT.put("real_furnace",true);}
                    case "smeltcheck"->{require(p.getInventory().getItem(1).is(AmethystPortalContent.FIBER_ITEM.get()),"real furnace pickup placed fiber in inventory");require(p.getPersistentData().getBoolean("academy_amethyst_portal_guide"),"native smelting pickup fired guide event");RECEIPT.put("native_furnace_pickup",true);p.getInventory().selected=0;}
                    case "offered"->{
                        LogUtils.getLogger().info("QA_AMETHYST_OFFER_STATE player={} held={} items={}",p.position(),p.getMainHandItem(),world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(8)).stream().map(i->i.getItem()+"@"+i.position()).toList());
                        require(AmethystPortalRitual.active(world,SOURCE),"native Q offering activated portal");require(p.getInventory().getItem(0).is(Items.DIRT)&&p.getInventory().getItem(0).getCount()==2,"one dirt dropped and consumed");RECEIPT.put("native_q_activated",true);RECEIPT.put("dirt_remaining",2);LogUtils.getLogger().info("QA_AMETHYST_OFFER_PASS one_dirt=true plants=8");
                    }
                    case "city"->{
                        require(world.dimension().equals(CityPortalTravel.CITY),"native standing entry reached city");require(CityPortalTravel.returnRecord(p).getString("Dimension").equals("minecraft:overworld"),"personal return stored");
                        var station=CityPortalTravel.station(world);require(station!=null&&AmethystPortalRitual.active(world,station),"public return pool active");require(!p.hasPermissions(2),"arrival did not grant command permission");
                        RECEIPT.put("arrived_city",true);RECEIPT.put("city_position",p.position().toString());RECEIPT.put("station",station.toShortString());RECEIPT.put("origin_record",CityPortalTravel.returnRecord(p).toString());receipt(mc);LogUtils.getLogger().info("QA_AMETHYST_CITY_PASS {}",RECEIPT);
                    }
                    case "resume"->{
                        require(world.dimension().equals(CityPortalTravel.CITY),"restart restored real city dimension");require(CityPortalTravel.returnRecord(p).getLong("Anchor")==SOURCE.asLong(),"saved personal source survived process restart");
                        var station=CityPortalTravel.station(world);require(station!=null&&AmethystPortalRitual.active(world,station),"city pool and saved station survived");
                        RECEIPT.put("restart_in_city",true);RECEIPT.put("ordinary_survival_player",!p.hasPermissions(2));RECEIPT.put("station",station.toShortString());LogUtils.getLogger().info("QA_AMETHYST_RESTART_PASS source={}",SOURCE);
                    }
                    case "returned"->{
                        require(world.dimension().equals(Level.OVERWORLD),"native return reached overworld");require(p.position().distanceToSqr(SOURCE.getCenter())<25,"return beside original pool, not world spawn");require(CityPortalTravel.standable(world,p.position()),"dry clear return landing");require(p.getPortalCooldown()>0,"native anti-bounce cooldown");
                        require(AmethystPortalRitual.active(world,SOURCE),"original portal persisted across save/restart");require(p.getInventory().getItem(0).getCount()==2&&p.getInventory().getItem(1).is(AmethystPortalContent.FIBER_ITEM.get()),"inventory retained across both dimensions and restart");
                        RECEIPT.put("returned_overworld",true);RECEIPT.put("return_position",p.position().toString());RECEIPT.put("inventory_preserved",true);RECEIPT.put("original_pool_persisted",true);receipt(mc);LogUtils.getLogger().info("QA_AMETHYST_RETURN_PASS {}",RECEIPT);
                    }
                    default->throw new IllegalArgumentException(op);
                }return null;
            });
        }
    }
}

package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.city.CityCatalog;
import cn.academy.port.city.CityNpcEntity;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.develop.InductionFactors;
import cn.academy.port.machine.MachineDeveloperBlockEntity;
import cn.academy.port.machine.MachineDeveloperSessions;
import cn.academy.port.wind.ClassicWindBaseBlockEntity;
import cn.academy.port.wind.ClassicWindMainBlockEntity;
import cn.academy.port.wireless.ClassicWirelessSavedData;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** One integrated-client journey; no QA code is shipped in the production JAR. */
final class CityCommerceQa {
    private static final Logger LOG = LogUtils.getLogger();
    private static final BlockPos[] MACHINES = {p(-238,0,-109),p(-233,0,-109),p(-217,0,-109),p(-212,0,-109)};
    private static final BlockPos[] WIND = {p(-236,21,-110),p(-214,21,-110),p(-236,21,-99),p(-214,21,-99)};
    private static final BlockPos NODE = p(-225,8,-105);
    private static final Set<String> FOODS = new HashSet<>();
    private static int retry;
    private static double before, lowest, completed;
    private static String expectedOutput, consumeId;
    private CityCommerceQa() {}
    private static BlockPos p(int x,int y,int z) { return new BlockPos(x,y,z); }
    private static void require(boolean condition,String message) { if (!condition) throw new IllegalStateException("QA_CITY_COMMERCE " + message); }
    private static ServerPlayer player(Minecraft mc) { return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); }
    private static <T> T server(Minecraft mc, java.util.function.Supplier<T> task) { return mc.getSingleplayerServer().submit(task).join(); }
    private static String path(ItemStack item) { return BuiltInRegistries.ITEM.getKey(item.getItem()).getPath(); }

    static boolean step(Minecraft mc,String argument) {
        var args = argument.split(",");
        switch (args[0]) {
            case "fixture" -> {
                server(mc,() -> { var player=player(mc); player.getInventory().add(InductionFactors.stack("electromaster")); return null; });
                LOG.info("QA_CITY_FIXTURE survival=true induction_factor=electromaster");
            }
            case "wire_check" -> {
                String problem = server(mc,() -> {
                    var level=player(mc).serverLevel(); var data=ClassicWirelessSavedData.getNonCreate(level);
                    if (data==null) return "wireless data missing";
                    var graph=data.graph();var node=ClassicWirelessSavedData.pos(NODE);
                    for (var pos : MACHINES) {
                        if (!level.hasChunkAt(pos)) return "machine chunk unloaded " + pos;
                        if (!(level.getBlockEntity(pos) instanceof MachineDeveloperBlockEntity machine)) return "machine entity missing " + pos;
                        if (!machine.available()) return "machine incomplete " + pos;
                        if (machine.battery().getEnergy()<100000) return "machine not precharged " + pos + " " + machine.battery().getEnergy();
                        if (!node.equals(graph.nodeForReceiver(ClassicWirelessSavedData.pos(pos)))) return "machine not linked " + pos;
                    }
                    for (var pos : WIND) {
                        if (!level.hasChunkAt(pos)) return "wind chunk unloaded " + pos;
                        if (!(level.getBlockEntity(pos) instanceof ClassicWindBaseBlockEntity base)) return "wind base missing " + pos;
                        if (!(level.getBlockEntity(pos.above(10)) instanceof ClassicWindMainBlockEntity main)) return "wind main missing " + pos;
                        if (!base.isComplete()) return "wind incomplete " + pos;
                        if (!main.fanInstalled()) return "wind fan missing " + pos;
                        if (base.simulatedGeneration()<=0) return "wind output zero " + pos;
                        if (!node.equals(graph.nodeForGenerator(ClassicWirelessSavedData.pos(pos)))) return "wind not linked " + pos;
                    }
                    return "";
                });
                if (!problem.isEmpty()) {
                    if (++retry % 100 == 1) LOG.warn("QA_CITY_GRID_WAIT {}",problem);
                    require(retry < 1200,"grid did not initialize: " + problem);
                    return false;
                }
                retry=0;
                LOG.info("QA_CITY_GRID machines=4 charged=true linked_receivers=4 turbines_generating=4 linked_generators=4");
            }
            case "open_machine" -> {
                var pos=MACHINES[Integer.parseInt(args[1])];
                mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos),Direction.SOUTH,pos,false));
            }
            case "check_machine" -> {
                int index=Integer.parseInt(args[1]);
                require(mc.screen instanceof cn.academy.port.client.ClassicDeveloperScreen,"developer screen missing for " + index);
                boolean authentic=server(mc,() -> MachineDeveloperSessions.activeToken(player(mc)).isPresent()
                        && player(mc).serverLevel().getBlockEntity(MACHINES[index]) instanceof MachineDeveloperBlockEntity machine
                        && player(mc).getUUID().equals(machine.user()));
                require(authentic,"real machine session missing " + index);
                LOG.info("QA_CITY_MACHINE_OPEN index={} real_screen=true",index);
            }
            case "learn" -> {
                require(mc.screen instanceof cn.academy.port.client.ClassicDeveloperScreen,"developer screen required");
                try {
                    var field=cn.academy.port.client.ClassicDeveloperScreen.class.getDeclaredField("console");field.setAccessible(true);
                    var console=(cn.academy.port.client.ClassicDeveloperConsole)field.get(mc.screen);
                    if (console==null || !console.ready()) return false;
                    before=server(mc,() -> ((MachineDeveloperBlockEntity)player(mc).serverLevel().getBlockEntity(MACHINES[0])).battery().getEnergy());
                    lowest=before;
                    for (char c : "learn".toCharArray()) mc.screen.charTyped(c,0);
                    mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,0,0);
                    LOG.info("QA_CITY_DEVELOP_REQUEST energyBefore={} real_console=true",before);
                } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            }
            case "learn_done" -> {
                var result=server(mc,() -> {
                    var player=player(mc);var process=DevelopmentController.process(player);
                    double energy=((MachineDeveloperBlockEntity)player.serverLevel().getBlockEntity(MACHINES[0])).battery().getEnergy();
                    return new Object[]{process.state(),energy,AbilityStorage.get(player).category,AbilityStorage.get(player).level};
                });
                lowest=Math.min(lowest,(double)result[1]);
                var state=(DevelopmentProcess.State)result[0];
                require(state != DevelopmentProcess.State.FAILED,"real development failed");
                if (state != DevelopmentProcess.State.DONE) return false;
                completed=(double)result[1];
                require("electromaster".equals(result[2]) && (int)result[3]>=1,"actual ability not acquired");
                require(lowest<before-500,"machine energy did not decrease: " + before + " -> " + lowest);
                LOG.info("QA_CITY_DEVELOP_DONE category={} level={} energyBefore={} min={} end={}",result[2],result[3],before,lowest,completed);
            }
            case "recharged" -> {
                double now=server(mc,() -> ((MachineDeveloperBlockEntity)player(mc).serverLevel().getBlockEntity(MACHINES[0])).battery().getEnergy());
                require(now>completed+5,"wind recharge did not increase machine IF " + completed + " -> " + now);
                LOG.info("QA_CITY_RECHARGE from={} to={}",completed,now);
            }
            case "skill_before" -> {
                before=server(mc,() -> {
                    var p=player(mc); var state=AbilityStorage.get(p);
                    require("electromaster".equals(state.category) && state.level>=1,"expected the previously awakened player");
                    require(!state.learned("arc_gen"),"test skill must start unlearned");
                    require(MachineDeveloperSessions.activeToken(p).isPresent(),"real laboratory machine session required");
                    return ((MachineDeveloperBlockEntity)p.serverLevel().getBlockEntity(MACHINES[0])).battery().getEnergy();
                });
                LOG.info("QA_CITY_SKILL_BEFORE skill=arc_gen learned=false energy={}",before);
            }
            case "skill_done" -> {
                var values=server(mc,() -> {
                    var p=player(mc);var state=AbilityStorage.get(p);
                    return new Object[]{state.learned("arc_gen"),state.category,state.level,
                            ((MachineDeveloperBlockEntity)p.serverLevel().getBlockEntity(MACHINES[0])).battery().getEnergy()};
                });
                if (!(boolean)values[0]) { require(++retry<600,"skill did not complete through the machine"); return false; }
                retry=0;
                LOG.info("QA_CITY_SKILL_DONE skill=arc_gen learned={} category={} level={} energyBefore={} energyAfter={} actualLaboratoryMachine=true",
                        values[0],values[1],values[2],before,values[3]);
            }
            case "npc" -> {
                String key="academy.city.npc."+args[1];
                var npcs=mc.level.getEntitiesOfClass(CityNpcEntity.class,mc.player.getBoundingBox().inflate(32),npc -> npc.getCustomName()!=null
                        && npc.getCustomName().getContents() instanceof TranslatableContents content && content.getKey().equals(key));
                if (npcs.isEmpty()) { require(++retry<1200,"service actor did not spawn: "+args[1]); return false; }
                var npc=npcs.getFirst();
                if (npc.distanceToSqr(mc.player)>12) {
                    if (++retry%20==1) mc.player.connection.sendCommand("tp @s "+(npc.getX()+1.3)+" "+npc.getY()+" "+npc.getZ());
                    require(retry<1200,"could not reach actor " + args[1]);return false;
                }
                retry=0;mc.gameMode.interact(mc.player,npc,InteractionHand.MAIN_HAND);
                LOG.info("QA_CITY_NPC_INTERACT role={} viaClientPacket=true",args[1]);
            }
            case "menu" -> {
                require(mc.screen instanceof MerchantScreen && mc.player.containerMenu instanceof MerchantMenu,"native merchant screen missing " + args[1]);
                var menu=(MerchantMenu)mc.player.containerMenu;
                int expected=Integer.parseInt(args[2]), expectedFoods=Integer.parseInt(args[3]), food=0;
                require(menu.getOffers().size()==expected,"offer count "+args[1]+" got="+menu.getOffers().size());
                for(var offer:menu.getOffers()) {
                    var item=offer.getResult();
                    if(item.getFoodProperties(mc.player)!=null) { food++;FOODS.add(path(item)); }
                }
                require(food==expectedFoods,"food count "+args[1]+" got="+food);
                LOG.info("QA_CITY_MENU shop={} offers={} foods={}",args[1],expected,food);
            }
            case "choose" -> {
                require(mc.player.containerMenu instanceof MerchantMenu,"merchant required");
                var menu=(MerchantMenu)mc.player.containerMenu; int wanted=-1;
                for(int i=0;i<menu.getOffers().size();i++) if(path(menu.getOffers().get(i).getResult()).equals(args[1])) { wanted=i; break; }
                require(wanted>=0,"offer missing "+args[1]); expectedOutput=args[1];
                menu.setSelectionHint(wanted);menu.tryMoveItems(wanted);
                mc.player.connection.send(new ServerboundSelectTradePacket(wanted));
            }
            case "take" -> {
                require(mc.player.containerMenu instanceof MerchantMenu,"merchant required");
                var menu=(MerchantMenu)mc.player.containerMenu;
                if (!path(menu.getSlot(2).getItem()).equals(expectedOutput)) { require(++retry<500,"offer did not populate "+expectedOutput); return false; }
                int empty=-1;
                for (int slot=3;slot<39;slot++) if (!menu.getSlot(slot).hasItem()) { empty=slot; break; }
                require(empty>=0,"no empty inventory slot for trade");
                retry=0;
                // A shift-click repeats a vanilla trade until payment runs out. Pick up
                // exactly one result and put it into the player's inventory instead.
                mc.gameMode.handleInventoryMouseClick(menu.containerId,2,0,ClickType.PICKUP,mc.player);
                mc.gameMode.handleInventoryMouseClick(menu.containerId,empty,0,ClickType.PICKUP,mc.player);
                LOG.info("QA_CITY_TRADE product={} native_result_slot=true quantity=one",expectedOutput);
            }
            case "progress" -> {
                int[] progress=server(mc,() -> {var player=player(mc);var tag=player.getPersistentData().getCompound("academy:city_errands");
                    int coins=0;for(var stack:player.getInventory().items) if(path(stack).equals("city_coin")) coins+=stack.getCount();
                    return new int[]{tag.getInt("course"),tag.getInt("stock"),coins};});
                require(progress[0]==2 && progress[1]==2,"two quests not completed");
                require(progress[2]==Integer.parseInt(args[1]),"coin balance " + progress[2]);
                LOG.info("QA_CITY_QUESTS course={} stock={} coinBalance={}",progress[0],progress[1],progress[2]);
            }
            case "all_foods" -> { require(FOODS.size()==50,"five menus did not contain 50 distinct food items, got " + FOODS.size());
                require(CityCatalog.GOODS.stream().filter(g -> g.tier()!=null).count()==50,"catalogue mismatch"); LOG.info("QA_CITY_ALL_FOODS distinct={}",FOODS.size()); }
            case "prepare_item" -> {
                consumeId=args[1];
                server(mc,() -> { var player=player(mc);var inv=player.getInventory();int slot=-1;
                    for(int i=0;i<inv.items.size();i++) if(path(inv.getItem(i)).equals(consumeId)){slot=i;break;}
                    require(slot>=0,"purchased item not present: "+consumeId);
                    var target=inv.getItem(slot);var first=inv.getItem(0);inv.setItem(slot,first);inv.setItem(0,target);inv.selected=0;
                    player.getFoodData().setFoodLevel(5);player.getFoodData().setSaturation(0);inv.setChanged();player.containerMenu.broadcastChanges();return null;});
            }
            case "consume" -> {
                ItemStack stack=mc.player.getMainHandItem(); require(path(stack).equals(consumeId),"selected food not synced");
                UseAnim expected=args[1].equals("drink")?UseAnim.DRINK:UseAnim.EAT;
                require(stack.getItem().getUseAnimation(stack)==expected,"wrong consume animation");
                mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
            }
            case "consume_done" -> {
                mc.options.keyUse.setDown(false);
                int hunger=server(mc,() -> player(mc).getFoodData().getFoodLevel());
                require(hunger==Integer.parseInt(args[1]),"actual hunger recovery " + consumeId + " got="+hunger);
                LOG.info("QA_CITY_CONSUMED item={} hunger={}",consumeId,hunger);
            }
            case "read" -> { require(path(mc.player.getMainHandItem()).equals(consumeId),"guide not selected");
                mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND); }
            case "read_done" -> { require(mc.screen instanceof BookViewScreen,"guide book did not open");LOG.info("QA_CITY_GUIDE_OPEN real_book_screen=true"); }
            default -> throw new IllegalArgumentException("Unknown city commerce QA: "+argument);
        }
        return true;
    }
}

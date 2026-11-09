/* AcademyCraft classic1.0.7 terminal/app finite native acceptance fixtures. GPLv3. See NOTICE. */
package cn.academy.port.gametest;

import cn.academy.port.AcademyNetwork;
import cn.academy.port.former.ClassicMetalFormer;
import cn.academy.port.solar.ClassicSolarGenerators;
import cn.academy.port.survival.ClassicMaterials;
import cn.academy.port.terminal.*;
import cn.academy.port.tutorial.*;
import cn.academy.port.wireless.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Compiled staging fixtures; execution and all live client/audio acceptance belong to main's serial lane. */
@GameTestHolder("academy") @PrefixGameTestTemplate(false)
public final class AcademyTerminalRuntimeTests {
    private static final String TEMPLATE="runtime_empty",BATCH="academy_terminal";
    private static ItemStack stack(Item item){return new ItemStack(item,2);}
    private static ItemStack empty(){return ItemStack.EMPTY;}
    private static void use(ServerPlayer player,ItemStack item){player.setItemInHand(InteractionHand.MAIN_HAND,item);item.getItem().use(player.serverLevel(),player,InteractionHand.MAIN_HAND);}
    private static void install(ServerPlayer player){use(player,new ItemStack(TerminalModule.INSTALLER.get()));}
    private static void frequency(ServerPlayer player){install(player);use(player,new ItemStack(TerminalModule.FREQUENCY_TRANSMITTER.get()));}
    private static TerminalNetwork.FrequencyRequest req(String action,BlockPos target,BlockPos selected,String password){return new TerminalNetwork.FrequencyRequest(target,selected,password,action);}
    public static final class Installs {
        final UUID owner;int terminal;final List<String> apps=new ArrayList<>();
        Installs(ServerPlayer player){owner=player.getUUID();}
        @SubscribeEvent public void terminal(TerminalInstalledEvent event){if(owner.equals(event.getEntity().getUUID()))terminal++;}
        @SubscribeEvent public void app(AppInstalledEvent event){if(owner.equals(event.getEntity().getUUID()))apps.add(event.app);}
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void native_terminal_first_use_once_survival_and_defaults_event_order(GameTestHelper h){
        try(var actors=new Actors(h)){
            var p=actors.player();var hook=new Installs(p);NeoForge.EVENT_BUS.register(hook);
            try{
                h.assertTrue(TerminalStorage.get(p).installedApps().equals(List.of("settings","tutorial")),"two source defaults without saved bits");
                h.assertTrue(TerminalStorage.get(p).savedAppIds().isEmpty(),"preinstalled defaults do not set saved bit ledger");
                var item=new ItemStack(TerminalModule.INSTALLER.get());h.assertValueEqual(item.getMaxStackSize(),1,"source installer maxStack1");use(p,item);
                h.assertTrue(item.isEmpty()&&TerminalStorage.get(p).terminalInstalled(),"native one survival item consumed for first server installation");
                h.assertValueEqual(hook.terminal,1,"one actual server installed event");
                var again=new ItemStack(TerminalModule.INSTALLER.get());use(p,again);h.assertValueEqual(again.getCount(),1,"already installed retains second real item");h.assertValueEqual(hook.terminal,1,"no repeat event");
                TerminalStorage.remove(p);h.assertTrue(TerminalStorage.get(p).terminalInstalled(),"persistent player reload retains installed state");h.succeed();
            }finally{NeoForge.EVENT_BUS.unregister(hook);}
        }
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void native_all_three_app_installers_gate_terminal_consume_once_and_creative_retains(GameTestHelper h){
        try(var actors=new Actors(h)){
            var p=actors.player();var hook=new Installs(p);NeoForge.EVENT_BUS.register(hook);
            try{
                var absent=new ItemStack(TerminalModule.SKILL_TREE.get(),3);use(p,absent);h.assertValueEqual(absent.getCount(),3,"absent terminal never consumes installer");h.assertFalse(TerminalStorage.get(p).isInstalled("skill_tree"),"missing terminal prevents app installation");
                install(p);for(var app:List.of(TerminalModule.SKILL_TREE.get(),TerminalModule.MEDIA_PLAYER.get(),TerminalModule.FREQUENCY_TRANSMITTER.get())){
                    var item=new ItemStack(app,3);h.assertValueEqual(item.getMaxStackSize(),64,"source ItemApp default maxStack64");use(p,item);h.assertValueEqual(item.getCount(),2,"first successful server app use consumes exactly one");use(p,item);h.assertValueEqual(item.getCount(),2,"repeat installed use consumes nothing");h.assertTrue(TerminalStorage.get(p).isInstalled(app.app),"actual installed ledger matches source app");
                }
                h.assertTrue(hook.apps.equals(List.of("skill_tree","media_player","freq_transmitter")),"exactly one event per real app in use order");
                var creative=actors.player();creative.setGameMode(GameType.CREATIVE);var installer=new ItemStack(TerminalModule.INSTALLER.get());use(creative,installer);h.assertValueEqual(installer.getCount(),1,"creative terminal retained");var app=new ItemStack(TerminalModule.SKILL_TREE.get(),4);use(creative,app);h.assertValueEqual(app.getCount(),4,"creative app retained");h.succeed();
            }finally{NeoForge.EVENT_BUS.unregister(hook);}
        }
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void native_recipe22_and38_39_40_result_slots_physically_consume_source_grids(GameTestHelper h){
        try(var actors=new Actors(h)){
            var p=actors.player();var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlockAndUpdate(pos,Blocks.CRAFTING_TABLE.defaultBlockState());
            craft(h,p,pos,TerminalModule.INSTALLER.get(),stack(ClassicMaterials.DATA_CHIP.get()),stack(Items.GLASS_PANE),stack(ClassicMaterials.DATA_CHIP.get()),stack(ClassicMaterials.REINFORCED_IRON_PLATE.get()),stack(ClassicMaterials.BRAIN_COMPONENT.get()),stack(ClassicMaterials.REINFORCED_IRON_PLATE.get()),stack(ClassicMaterials.INFO_COMPONENT.get()),stack(Items.REDSTONE_BLOCK),stack(ClassicMaterials.INFO_COMPONENT.get()));
            craft(h,p,pos,TerminalModule.SKILL_TREE.get(),stack(Items.COMPASS),empty(),empty(),stack(ClassicMaterials.DATA_CHIP.get()),empty(),empty(),stack(ClassicMaterials.INFO_COMPONENT.get()),empty(),empty());
            craft(h,p,pos,TerminalModule.MEDIA_PLAYER.get(),stack(Items.NOTE_BLOCK),stack(Items.NOTE_BLOCK),stack(Items.NOTE_BLOCK),empty(),stack(ClassicMaterials.DATA_CHIP.get()),empty(),empty(),stack(ClassicMaterials.INFO_COMPONENT.get()),empty());
            craft(h,p,pos,TerminalModule.FREQUENCY_TRANSMITTER.get(),stack(ClassicMaterials.RESONANCE_COMPONENT.get()),empty(),empty(),stack(ClassicMaterials.DATA_CHIP.get()),empty(),empty(),stack(ClassicMaterials.INFO_COMPONENT.get()),empty(),empty());h.succeed();
        }
    }
    private static void craft(GameTestHelper h,ServerPlayer player,BlockPos table,Item expected,ItemStack... cells){
        var menu=new CraftingMenu(93,player.getInventory(),ContainerLevelAccess.create(h.getLevel(),table));player.containerMenu=menu;
        for(int i=0;i<9;i++)menu.getSlot(i+1).set(cells[i]);h.assertTrue(menu.getSlot(0).getItem().is(expected),"actual source-shaped recipe yields "+BuiltInRegistries.ITEM.getKey(expected));
        var crafted=menu.getSlot(0).remove(1);menu.getSlot(0).onTake(player,crafted);h.assertValueEqual(crafted.getCount(),1,"one source output");
        for(int i=0;i<9;i++)h.assertValueEqual(menu.getSlot(i+1).getItem().getCount(),cells[i].isEmpty()?0:1,"native result slot physically consumes occupied source cell "+i);
        h.assertTrue(TutorialStorage.get(player).visible("terminal"),"actual successful native crafting activates existing source terminal page OR");player.closeContainer();
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void native_clone_reload_preserves_semantic_apps_unknown_bits_and_direct_guide_access(GameTestHelper h){
        try(var actors=new Actors(h)){
            var p=actors.player();install(p);use(p,new ItemStack(TerminalModule.SKILL_TREE.get()));
            var state=TerminalStorage.get(p);state.restore(true,List.of("skill_tree","future_extra"));TerminalStorage.save(p);var saved=TerminalStorage.encode(state);TerminalStorage.remove(p);h.assertTrue(TerminalStorage.encode(TerminalStorage.get(p)).equals(saved),"real persistent-data reload keeps unknown app IDs");
            var replacement=actors.player();NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement,p,false));h.assertTrue(TerminalStorage.encode(TerminalStorage.get(replacement)).equals(saved),"native clone event keeps exact terminal ledger");
            h.assertTrue(TerminalNetwork.request(replacement,new AcademyNetwork.Request("terminal_app","tutorial")),"preinstalled terminal tutorial invokes real guide");h.assertTrue(TutorialStorage.get(replacement).firstOpened(),"terminal guide uses existing persistent first-open flow");
            var fresh=actors.player();var guide=new ItemStack(TutorialModule.ITEM.get());use(fresh,guide);h.assertFalse(TerminalStorage.get(fresh).terminalInstalled(),"direct guide use grants no terminal");h.assertTrue(TutorialStorage.get(fresh).firstOpened()&&guide.getCount()==1,"existing direct guide remains usable independently");h.succeed();
        }
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void native_terminal_ingress_cannot_install_or_activate_absent_apps_dead_or_spectator(GameTestHelper h){
        try(var actors=new Actors(h)){
            var p=actors.player();h.assertFalse(TerminalNetwork.request(p,new AcademyNetwork.Request("terminal_open","")),"uninstalled terminal entry denied");
            h.assertFalse(TerminalNetwork.request(p,new AcademyNetwork.Request("terminal_install","")),"no client install ingress");h.assertFalse(TerminalStorage.get(p).terminalInstalled(),"forgery cannot grant installed ledger");install(p);
            h.assertFalse(TerminalNetwork.request(p,new AcademyNetwork.Request("terminal_open","spoof")),"malformed entry denied");h.assertFalse(TerminalNetwork.request(p,new AcademyNetwork.Request("terminal_app","media_player")),"absent application denied");
            h.assertTrue(TerminalNetwork.request(p,new AcademyNetwork.Request("terminal_open",""))&&TerminalNetwork.request(p,new AcademyNetwork.Request("terminal_app","settings")),"installed terminal/default settings admitted");
            p.setGameMode(GameType.SPECTATOR);h.assertFalse(TerminalNetwork.request(p,new AcademyNetwork.Request("terminal_open","")),"spectator entry denied");p.setGameMode(GameType.SURVIVAL);p.setHealth(0);h.assertFalse(TerminalNetwork.request(p,new AcademyNetwork.Request("terminal_app","tutorial")),"dead application entry denied");h.succeed();
        }
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void native_frequency_real_password_matrix_node_generator_links_and_replay_rejections(GameTestHelper h){
        try(var actors=new Actors(h)){
            var p=actors.player();var level=h.getLevel();var matrixPos=h.absolutePos(new BlockPos(5,1,5));p.setYRot(0);var matrixItem=new ItemStack(ClassicWirelessDevices.MATRIX_ITEM.get());p.setItemInHand(InteractionHand.MAIN_HAND,matrixItem);
            h.assertTrue(((BlockItem)matrixItem.getItem()).place(new BlockPlaceContext(p,InteractionHand.MAIN_HAND,matrixItem,new BlockHitResult(Vec3.atCenterOf(matrixPos),Direction.UP,matrixPos,false))).consumesAction(),"real complete eight-cell matrix fixture placed");
            var matrix=(ClassicWirelessMatrixBlockEntity)level.getBlockEntity(matrixPos);for(int i=0;i<3;i++)matrix.setItem(i,new ItemStack(ClassicMaterials.CONSTRAINT_PLATE.get()));matrix.setItem(3,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("academy:matrix_core_0"))));
            var graph=ClassicWirelessSavedData.get(level).graph();h.assertTrue(graph.createNetwork(ClassicWirelessSavedData.pos(matrixPos),"Local fixture","matrix-key"),"genuine source matrix network initialized");
            var nodePos=h.absolutePos(new BlockPos(2,1,3));h.setBlock(new BlockPos(2,1,3),ClassicWirelessDevices.BASIC.get());var node=(ClassicWirelessNodeBlockEntity)level.getBlockEntity(nodePos);node.setPassword("node-key");
            aim(p,matrixPos);h.assertFalse(TerminalFrequencySessions.request(p,req("query_matrix",matrixPos,matrixPos,"")),"no terminal/app denies frequency ingress");frequency(p);
            h.assertTrue(TerminalFrequencySessions.request(p,req("query_matrix",matrixPos,matrixPos,"")),"genuine four-block matrix SSID query");h.assertFalse(TerminalFrequencySessions.request(p,req("authorize_matrix",matrixPos,matrixPos,"wrong")),"wrong fictional password rejected");h.assertTrue(TerminalFrequencySessions.request(p,req("authorize_matrix",matrixPos,matrixPos,"matrix-key")),"real matrix authorization succeeds");
            aim(p,nodePos);h.assertTrue(TerminalFrequencySessions.request(p,req("link_matrix",nodePos,matrixPos,"")),"world node click actually links to selected matrix");h.assertTrue(TerminalFrequencySessions.request(p,req("link_matrix",nodePos,matrixPos,"")),"repeated source links retained idempotently");h.assertValueEqual(graph.networkAt(ClassicWirelessSavedData.pos(nodePos)).load(),1,"no duplicated graph load");
            h.assertTrue(TerminalFrequencySessions.request(p,req("authorize_node",nodePos,nodePos,"node-key")),"real node password authorization");var solarPos=h.absolutePos(new BlockPos(2,1,6));h.setBlock(new BlockPos(2,1,6),ClassicSolarGenerators.BLOCK.get());aim(p,solarPos);
            h.assertTrue(TerminalFrequencySessions.request(p,req("link_node",solarPos,nodePos,"")),"real clicked solar generator links to selected node");h.assertTrue(graph.nodeForGenerator(ClassicWirelessSavedData.pos(solarPos)).equals(ClassicWirelessSavedData.pos(nodePos)),"genuine generator link visible in persistent graph");
            var receiverPos=h.absolutePos(new BlockPos(2,1,8));h.setBlock(new BlockPos(2,1,8),ClassicMetalFormer.BLOCK.get());aim(p,receiverPos);h.assertTrue(TerminalFrequencySessions.request(p,req("link_node",receiverPos,nodePos,"")),"real clicked Metal Former receiver links to selected node");h.assertTrue(graph.nodeForReceiver(ClassicWirelessSavedData.pos(receiverPos)).equals(ClassicWirelessSavedData.pos(nodePos)),"genuine receiver link visible in persistent graph");aim(p,solarPos);
            node.setPassword("changed");h.assertFalse(TerminalFrequencySessions.request(p,req("link_node",solarPos,nodePos,"")),"changed node password invalidates cached authorization");node.setPassword("node-key");h.assertTrue(TerminalNetwork.request(p,new AcademyNetwork.Request("terminal_close","")),"actual app close ingress revokes selected authorization");h.assertFalse(TerminalFrequencySessions.request(p,req("link_node",solarPos,nodePos,"")),"closed capability cannot replay link");
            p.setYRot(180);p.setXRot(0);h.assertFalse(TerminalFrequencySessions.request(p,req("authorize_node",nodePos,nodePos,"node-key")),"remote/occluded endpoint forgery denied by actual world ray");h.succeed();
        }
    }
    private static void aim(ServerPlayer p,BlockPos target){
        p.moveTo(target.getX()+.5,target.getY(),target.getZ()-2.5,0,0);var delta=Vec3.atCenterOf(target).subtract(p.getEyePosition());p.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
    }
    private static final class Actors implements AutoCloseable {
        final GameTestHelper helper;final List<ServerPlayer> players=new ArrayList<>();final List<EmbeddedChannel> channels=new ArrayList<>();
        Actors(GameTestHelper helper){this.helper=helper;}
        ServerPlayer player(){var level=helper.getLevel();var server=level.getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"ACTerminalProbe"),false);var p=new ServerPlayer(server,level,cookie.gameProfile(),cookie.clientInformation());players.add(p);var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);channels.add(channel);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);var at=helper.absoluteVec(new Vec3(2.5,1,1.5));p.moveTo(at.x,at.y,at.z,0,0);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.setInvulnerable(true);return p;}
        @Override public void close(){for(var p:players){p.closeContainer();TerminalStorage.remove(p);TutorialStorage.remove(p);var list=helper.getLevel().getServer().getPlayerList();if(list.getPlayer(p.getUUID())==p)list.remove(p);else p.discard();}for(var channel:channels)channel.finishAndReleaseAll();}
    }
    private AcademyTerminalRuntimeTests(){}
}

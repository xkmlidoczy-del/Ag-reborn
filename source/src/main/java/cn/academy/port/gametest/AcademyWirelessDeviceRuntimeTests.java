package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.energy.*;
import cn.academy.port.fusion.*;
import cn.academy.port.machine.*;
import cn.academy.port.solar.*;
import cn.academy.port.survival.ClassicMaterials;
import cn.academy.port.wireless.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

/** Native source-device and full finite wireless/fusion/development fixtures. Parent owns execution. */
@GameTestHolder("academy") @PrefixGameTestTemplate(false)
public final class AcademyWirelessDeviceRuntimeTests {
    private static final String TEMPLATE="runtime_empty",BATCH="academy_wireless_devices";
    private static final BlockPos NODE=new BlockPos(2,1,3),SOLAR=new BlockPos(2,1,5),FUSOR=new BlockPos(4,1,3),NORMAL=new BlockPos(6,1,4),MATRIX=new BlockPos(3,1,6);
    private AcademyWirelessDeviceRuntimeTests(){}
    private static ResourceLocation id(String path){return ResourceLocation.parse(path.contains(":")?path:"academy:"+path);}
    private static ItemStack stack(Item item){return new ItemStack(item);}private static ItemStack empty(){return ItemStack.EMPTY;}
    private static FakePlayer player(GameTestHelper h){var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[AC-Wireless-Test]"));var at=h.absoluteVec(new Vec3(2.5,1,1.5));p.moveTo(at.x,at.y,at.z,0,0);p.getAbilities().instabuild=false;return p;}
    private static void close(FakePlayer p){p.closeContainer();DevelopmentController.remove(p);AbilityStorage.remove(p);}
    private static void equal(GameTestHelper h,double expected,double actual,String label){h.assertTrue(Double.isFinite(actual)&&Math.abs(expected-actual)<1e-7,label+": "+actual);}
    private static ItemStack craft(GameTestHelper h,String recipe,int width,int height,ItemStack... cells){var nativeRecipe=(CraftingRecipe)h.getLevel().getRecipeManager().byKey(id("classic/"+recipe)).orElseThrow().value();var input=CraftingInput.of(width,height,List.of(cells));h.assertTrue(nativeRecipe.matches(input,h.getLevel()),"actual exact source recipe "+recipe);var output=nativeRecipe.assemble(input,h.getLevel().registryAccess());h.assertFalse(output.isEmpty(),"actual native output "+recipe);for(var cell:cells)if(!cell.isEmpty())cell.shrink(1);return output;}
    private static void place(GameTestHelper h,ServerPlayer p,ItemStack item,BlockPos relative){var absolute=h.absolutePos(relative);p.setItemInHand(InteractionHand.MAIN_HAND,item);h.assertTrue(((BlockItem)item.getItem()).place(new BlockPlaceContext(p,InteractionHand.MAIN_HAND,item,new BlockHitResult(Vec3.atCenterOf(absolute),Direction.UP,absolute,false))).consumesAction(),"actual source crafted device placement");h.assertTrue(item.isEmpty(),"placed crafted device consumed exactlyonce");}
    private static void near(ServerPlayer p,BlockPos pos){p.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()-1.5,0,0);}
    private static ClassicWirelessProtocol.Request request(ClassicWirelessMenu menu,String action,BlockPos target,String value,String password){return new ClassicWirelessProtocol.Request(menu.containerId,menu.token(),action,target,value,password);}

    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void node_finite_source_battery_loops_native_inventory_and_persistence(GameTestHelper h){var actors=new NativeMenuActors(h);var p=actors.player();try{var level=h.getLevel();for(var block:new ClassicWirelessNodeBlock[]{ClassicWirelessDevices.BASIC.get(),ClassicWirelessDevices.STANDARD.get(),ClassicWirelessDevices.ADVANCED.get()}){
        h.setBlock(NODE,block);var node=(ClassicWirelessNodeBlockEntity)h.getBlockEntity(NODE);node.setPlacer(p);equal(h,0,node.getEnergy(),"fresh node empty");equal(h,node.nodeType().energy,node.getMaxEnergy(),"source capacity");equal(h,node.nodeType().bandwidth,node.getBandwidth(),"source bandwidth");equal(h,node.nodeType().range,node.getRange(),"source range");h.assertValueEqual(node.getCapacity(),node.nodeType().capacity,"source shared user capacity");
        var in=stack(ClassicEnergyItems.ENERGY_UNIT.get());ClassicEnergyItemHelper.setEnergy(in,123.625);var out=stack(AcademyCraft.DEVELOPER.get());node.setItem(0,in);node.setItem(1,out);ClassicWirelessNodeBlockEntity.serverTick(level,node.getBlockPos(),node.getBlockState(),node);equal(h,103.625,ClassicEnergyItemHelper.getEnergy(in),"native source unit20IF input");equal(h,20,ClassicEnergyItemHelper.getEnergy(out),"input happens before native output");equal(h,0,node.getEnergy(),"native node input/output conservation");h.assertTrue(node.chargingIn()&&node.chargingOut(),"source charging indicatorflags");
        node.setNodeName("Finite relay");node.setPassword("fictional-node-pass");node.setEnergy(321.125);var saved=node.saveWithFullMetadata(level.registryAccess());node.clearContent();node.setEnergy(0);node.loadWithComponents(saved,level.registryAccess());equal(h,321.125,node.getEnergy(),"fractional NBT native node buffer");equal(h,103.625,ClassicEnergyItemHelper.getEnergy(node.getItem(0)),"real native battery payload survives");h.assertTrue(node.getNodeName().equals("Finite relay")&&node.getPassword().equals("fictional-node-pass")&&node.ownedBy(p),"node local owner/name/passwordpersist");
        h.assertTrue(ClassicWirelessProtocol.open(p,node.getBlockPos(),ClassicWirelessMenu.Kind.NODE),"actual native node menu opens");var menu=(ClassicWirelessMenu)p.containerMenu;h.assertValueEqual(menu.slots.size(),38,"source two IF slots plus36player");h.assertValueEqual(menu.slots.get(0).x,42,"source inputX");h.assertValueEqual(menu.slots.get(0).y,10,"source inputY");h.assertValueEqual(menu.slots.get(1).y,80,"source outputY");h.assertFalse(menu.snapshot().contains("password"),"node password never publicly synced");h.assertFalse(menu.slots.get(0).mayPlace(stack(Items.DIAMOND)),"native slot denies unsupported");p.closeContainer();level.removeBlock(node.getBlockPos(),false);
    }h.succeed();}finally{actors.close();}}

    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void matrix_source_cube_transaction_real_core_and_dynamic_capacity(GameTestHelper h){var actors=new NativeMenuActors(h);var p=actors.player();try{var level=h.getLevel();for(int quadrant=0;quadrant<4;quadrant++){
        p.setYRot(quadrant*90);var item=stack(ClassicWirelessDevices.MATRIX_ITEM.get());place(h,p,item,MATRIX);var matrix=(ClassicWirelessMatrixBlockEntity)h.getBlockEntity(MATRIX);h.assertTrue(matrix.ownedBy(p),"source placer name plus modern realUUID");h.assertValueEqual(matrix.getCapacity(),0,"empty matrixinactive");
        for(int part=0;part<8;part++){var pos=matrix.getBlockPos().offset(ClassicWirelessMatrixBlock.offset(part,matrix.facing()));var cell=level.getBlockState(pos);h.assertTrue(Block.isShapeFullBlock(cell.getCollisionShape(level,pos)),"all8source cube occupied cells fullcollision");var slave=(ClassicWirelessMatrixBlockEntity)level.getBlockEntity(pos);h.assertTrue(slave.origin()==matrix,"allparts point to sourceorigin");h.assertValueEqual(cell.getLightEmission(level,pos),15,"source full light");}
        for(int index=0;index<3;index++)matrix.setItem(index,stack(ClassicMaterials.CONSTRAINT_PLATE.get()));for(int core=0;core<3;core++){matrix.setItem(3,stack(BuiltInRegistries.ITEM.get(id("matrix_core_"+core))));h.assertValueEqual(matrix.getCapacity(),8*(core+1),"source dynamic core capacity");equal(h,60*(core+1)*(core+1),matrix.getBandwidth(),"source quadratic corebandwidth");equal(h,24*Math.sqrt(core+1),matrix.getRange(),"source sqrt corerange");}
        matrix.removeItem(1,1);h.assertValueEqual(matrix.getCapacity(),0,"actual removedplate disables matrix");matrix.setItem(1,stack(ClassicMaterials.CONSTRAINT_PLATE.get()));h.assertTrue(ClassicWirelessProtocol.open(p,matrix.getBlockPos(),ClassicWirelessMenu.Kind.MATRIX),"native matrixmenu");var menu=(ClassicWirelessMenu)p.containerMenu;h.assertValueEqual(menu.slots.size(),40,"source fourmatrix slots plus36player");for(int i=0;i<4;i++)h.assertValueEqual(menu.slots.get(i).getMaxStackSize(),1,"source oneplate/coreper slot");p.closeContainer();level.destroyBlock(matrix.getBlockPos().offset(ClassicWirelessMatrixBlock.offset(7,matrix.facing())),false);for(int part=0;part<8;part++)h.assertTrue(level.isEmptyBlock(matrix.getBlockPos().offset(ClassicWirelessMatrixBlock.offset(part,matrix.facing()))),"breakingpart removes8cube transaction");
    }h.succeed();}finally{actors.close();}}

    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void wireless_menu_sender_owner_nonce_reach_and_no_secret_disclosure(GameTestHelper h){var actors=new NativeMenuActors(h);var owner=actors.player();var outsider=actors.player();try{h.setBlock(NODE,ClassicWirelessDevices.BASIC.get());var node=(ClassicWirelessNodeBlockEntity)h.getBlockEntity(NODE);node.setPlacer(owner);near(owner,node.getBlockPos());near(outsider,node.getBlockPos());h.assertTrue(ClassicWirelessProtocol.open(owner,node.getBlockPos(),ClassicWirelessMenu.Kind.NODE),"owner opens ownmenu");var ownerMenu=(ClassicWirelessMenu)owner.containerMenu;
        h.assertTrue(ClassicWirelessProtocol.handle(owner,request(ownerMenu,"node_password",node.getBlockPos(),"","fictional-pass")),"actual sender/owner can changelocal modpass");h.assertTrue(ClassicWirelessProtocol.handle(owner,request(ownerMenu,"node_name",node.getBlockPos(),"Owned node","")),"actual owner rename");h.assertFalse(ownerMenu.snapshot().contains("password"),"even owner snapshotdoes not leakpass");
        h.assertTrue(ClassicWirelessProtocol.open(outsider,node.getBlockPos(),ClassicWirelessMenu.Kind.NODE),"public read/menu inventorysource accessibility");var otherMenu=(ClassicWirelessMenu)outsider.containerMenu;h.assertFalse(otherMenu.snapshot().getBoolean("configure"),"server ownerpermission display");h.assertFalse(ClassicWirelessProtocol.handle(outsider,request(otherMenu,"node_password",node.getBlockPos(),"","stolen")),"unowned local passchange rejected");h.assertFalse(ClassicWirelessProtocol.handle(outsider,request(ownerMenu,"node_name",node.getBlockPos(),"Spoofed","")),"stolen ownernonce cannot impersonate");
        owner.closeContainer();h.assertFalse(ClassicWirelessProtocol.handle(owner,request(ownerMenu,"node_name",node.getBlockPos(),"Old token","")),"closed menu oldnonce rejected");h.assertTrue(ClassicWirelessProtocol.open(owner,node.getBlockPos(),ClassicWirelessMenu.Kind.NODE),"new generationmenu opens");var fresh=(ClassicWirelessMenu)owner.containerMenu;h.assertTrue(!fresh.token().equals(ownerMenu.token()),"reopened menu nonce replaced");h.assertFalse(ClassicWirelessProtocol.handle(owner,request(ownerMenu,"node_name",node.getBlockPos(),"Old token","")),"stale generation cannot mutate newmenu");owner.moveTo(node.getBlockPos().getX()+20,node.getBlockPos().getY(),node.getBlockPos().getZ(),0,0);h.assertFalse(ClassicWirelessProtocol.handle(owner,request(fresh,"node_name",node.getBlockPos(),"Remote","")),"out of source8-block menu reach rejected");h.assertTrue(node.getNodeName().equals("Owned node")&&node.getPassword().equals("fictional-pass"),"rejected actions preserve genuine local state");h.succeed();
    }finally{actors.close();}}

    /** Actual ServerPlayer/native menu path with an embedded transport fixture, not a client/socket proof.
     * NeoForge FakePlayer.openMenu deliberately returns OptionalInt.empty and cannot test this API. */
    static final class NativeMenuActors implements AutoCloseable {
        private final GameTestHelper helper;private final List<ServerPlayer> players=new ArrayList<>();private final List<EmbeddedChannel> channels=new ArrayList<>();private boolean closed;
        NativeMenuActors(GameTestHelper helper){this.helper=helper;}
        ServerPlayer player(){return player(new GameProfile(UUID.randomUUID(),"ACWiMenuProbe"));}
        ServerPlayer player(GameProfile profile){
            var level=helper.getLevel();var server=level.getServer();var cookie=CommonListenerCookie.createInitial(profile,false);
            var player=new ServerPlayer(server,level,cookie.gameProfile(),cookie.clientInformation());players.add(player);
            var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);channels.add(channel);
            // Official NeoForge GameTest hook declares supported payloads; no socket/handshake occurs.
            NetworkRegistry.configureMockConnection(connection);
            try{server.getPlayerList().placeNewPlayer(connection,player,cookie);helper.assertTrue(server.getPlayerList().getPlayer(player.getUUID())==player,"Native PlayerList admitted exact owned menu fixture");
                var at=helper.absoluteVec(new Vec3(2.5,1,1.5));player.moveTo(at.x,at.y,at.z,0,0);player.setGameMode(GameType.SURVIVAL);player.setNoGravity(true);player.setInvulnerable(true);return player;
            }catch(RuntimeException failure){try{close();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}throw failure;}
        }
        @Override public void close(){if(closed)return;closed=true;RuntimeException failure=null;
            for(var player:players){
                try{player.closeContainer();}catch(RuntimeException next){failure=add(failure,next);}
                try{DevelopmentController.remove(player);AbilityStorage.remove(player);}catch(RuntimeException next){failure=add(failure,next);}
                try{var list=helper.getLevel().getServer().getPlayerList();if(list.getPlayer(player.getUUID())==player)list.remove(player);else player.discard();}catch(RuntimeException next){failure=add(failure,next);}
            }
            for(var channel:channels)try{channel.finishAndReleaseAll();}catch(RuntimeException next){failure=add(failure,next);}
            if(failure!=null)throw failure;
        }
        private static RuntimeException add(RuntimeException previous,RuntimeException next){if(previous==null)return next;previous.addSuppressed(next);return previous;}
    }

    /** Supplied BASE materials and3source phase blocks are declared, never suppliedNormal/pure/charged items. */
    @GameTest(template=TEMPLATE,batch="academy_wireless_progression",timeoutTicks=20)
    public static void native_crafted_solar_basic_relay_fusion_normal_and_earned_arc(GameTestHelper h){var p=player(h);var level=h.getLevel();long oldTime=level.getDayTime();boolean oldRain=level.isRaining(),oldThunder=level.isThundering();try{
        // Seed provenance: authentic base crafting materials already obtainable via M08's mine/smelt
        // chain, ordinary vanilla crafted bits,3declared source phase cells. No charged/mastered device.
        var solarItem=craft(h,"solar_gen_09",3,3,stack(Items.GLASS_PANE),stack(Items.GLASS_PANE),stack(Items.GLASS_PANE),empty(),stack(ClassicMaterials.WAFER.get()),empty(),stack(ClassicMaterials.ENERGY_CONVERT_COMPONENT.get()),stack(ClassicMaterials.MACHINE_FRAME.get().asItem()),stack(ClassicMaterials.ENERGY_CONVERT_COMPONENT.get()));
        var nodeItem=craft(h,"wireless_node_basic_14",3,3,empty(),stack(ClassicMaterials.CALC_CHIP.get()),empty(),stack(Items.IRON_INGOT),stack(ClassicMaterials.MACHINE_FRAME.get().asItem()),stack(Items.IRON_INGOT),stack(ClassicMaterials.CRYSTAL_LOW.get()),stack(ClassicMaterials.RESO_CRYSTAL.get()),stack(ClassicMaterials.CRYSTAL_LOW.get()));
        var fusorItem=craft(h,"imag_fusor_23",3,3,stack(ClassicMaterials.CONSTRAINT_PLATE.get()),stack(ClassicMaterials.CRYSTAL_LOW.get()),stack(ClassicMaterials.CONSTRAINT_PLATE.get()),stack(ClassicMaterials.CALC_CHIP.get()),stack(ClassicMaterials.MACHINE_FRAME.get().asItem()),stack(ClassicMaterials.CALC_CHIP.get()),stack(ClassicMaterials.CONSTRAINT_PLATE.get()),stack(ClassicFusion.MATTER_UNIT.get()),stack(ClassicMaterials.CONSTRAINT_PLATE.get()));
        place(h,p,solarItem,SOLAR);place(h,p,nodeItem,NODE);place(h,p,fusorItem,FUSOR);var solar=(ClassicSolarBlockEntity)h.getBlockEntity(SOLAR);var node=(ClassicWirelessNodeBlockEntity)h.getBlockEntity(NODE);var fusor=(ClassicFusorBlockEntity)h.getBlockEntity(FUSOR);equal(h,0,solar.getEnergy()+node.getEnergy()+fusor.getEnergy(),"allactual crafted devices startEMPTY");
        var absolute=solar.getBlockPos();for(int y=absolute.getY()+1;y<level.getMaxBuildHeight();y++)level.setBlock(new BlockPos(absolute.getX(),y,absolute.getZ()),Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);level.setDayTime(1000);level.setWeatherParameters(24000,0,false,false);var graph=ClassicWirelessSavedData.get(level).graph();var nodePos=ClassicWirelessSavedData.pos(node.getBlockPos());var solarPos=ClassicWirelessSavedData.pos(solar.getBlockPos());var fusorPos=ClassicWirelessSavedData.pos(fusor.getBlockPos());
        h.assertTrue(graph.linkGenerator(nodePos,solarPos,"",true)&&graph.linkReceiver(nodePos,fusorPos,"",true),"authentic local node connectsgen/receiver without matrix");for(int tick=0;tick<667;tick++){ClassicSolarBlockEntity.serverTick(level,solar.getBlockPos(),solar.getBlockState(),solar);ClassicWirelessNodeBlockEntity.serverTick(level,node.getBlockPos(),node.getBlockState(),node);graph.tick();}equal(h,2000,fusor.getEnergy(),"only actualgeneratedIF charges Fusor");equal(h,1,node.getEnergy(),"finite3IF generationexcess in node");
        var matter=craft(h,"matter_unit_17",3,3,empty(),stack(ClassicMaterials.CONSTRAINT_PLATE.get()),empty(),stack(ClassicMaterials.CONSTRAINT_PLATE.get()),stack(Items.GLASS),stack(ClassicMaterials.CONSTRAINT_PLATE.get()),empty(),stack(ClassicMaterials.CONSTRAINT_PLATE.get()),empty());p.setItemInHand(InteractionHand.MAIN_HAND,matter);
        for(int index=0;index<3;index++){var phase=h.absolutePos(new BlockPos(2+index,1,7));level.setBlock(phase,ClassicFusion.PHASE_BLOCK.get().defaultBlockState(),Block.UPDATE_CLIENTS);for(int y=1;y<=4;y++)level.setBlock(phase.above(y),Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);p.moveTo(phase.getX()+.5,phase.getY()+2,phase.getZ()+.5,0,90);var used=p.getMainHandItem().getItem().use(level,p,InteractionHand.MAIN_HAND);h.assertTrue(used.getResult().consumesAction(),"realmatter use collects declaredsource phaseblock");p.setItemInHand(InteractionHand.MAIN_HAND,used.getObject());h.assertTrue(level.getBlockState(phase).isAir(),"collectedsourceblock consumed");}
        ItemStack filled=ItemStack.EMPTY;for(int slot=0;slot<p.getInventory().getContainerSize();slot++)if(p.getInventory().getItem(slot).is(ClassicFusion.PHASE_MATTER_UNIT.get())){var earned=p.getInventory().removeItem(slot,3);if(filled.isEmpty())filled=earned;else filled.grow(earned.getCount());}h.assertValueEqual(filled.getCount(),3,"3actualearned phasecontainers");fusor.setItem(2,filled);fusor.setItem(0,stack(ClassicMaterials.CRYSTAL_LOW.get()));
        for(int tick=0;tick<150;tick++){ClassicSolarBlockEntity.serverTick(level,solar.getBlockPos(),solar.getBlockState(),solar);ClassicWirelessNodeBlockEntity.serverTick(level,node.getBlockPos(),node.getBlockState(),node);ClassicFusorBlockEntity.serverTick(level,fusor.getBlockPos(),fusor.getBlockState(),fusor);graph.tick();}h.assertTrue(fusor.getItem(1).is(ClassicMaterials.CRYSTAL_NORMAL.get()),"real wirelessly-powered crystalnormal upgrade");h.assertValueEqual(fusor.liquid(),0,"source upgrade consumedgenuine3000mB");equal(h,667*3+150*3-1452,solar.getEnergy()+node.getEnergy()+fusor.getEnergy(),"allgeneratedIF minus source1452fusionwork conserved");var earnedNormalCrystal=fusor.removeItem(1,1);
        var core=craft(h,"matrix_core_0_28",3,3,empty(),stack(ClassicMaterials.CRYSTAL_LOW.get()),empty(),stack(ClassicMaterials.CALC_CHIP.get()),stack(ClassicMaterials.RESO_CRYSTAL.get()),stack(ClassicMaterials.DATA_CHIP.get()),empty(),stack(ClassicMaterials.ENERGY_CONVERT_COMPONENT.get()),empty());
        var normalItem=craft(h,"developer_normal_45",3,3,stack(ClassicMaterials.BRAIN_COMPONENT.get()),stack(ClassicMaterials.INFO_COMPONENT.get()),stack(ClassicMaterials.ENERGY_CONVERT_COMPONENT.get()),core,stack(Items.RED_BED),stack(Items.PISTON),earnedNormalCrystal,stack(ClassicMaterials.MACHINE_FRAME.get().asItem()),stack(Items.REDSTONE));place(h,p,normalItem,NORMAL);var normal=(MachineDeveloperBlockEntity)h.getBlockEntity(NORMAL);equal(h,0,normal.battery().getEnergy(),"genuinelycraftedNormal startsEMPTY");
        graph.unlinkReceiver(fusorPos);h.assertTrue(graph.linkReceiver(nodePos,ClassicWirelessSavedData.pos(normal.getBlockPos()),"",true),"actualnode delivers to genuineNormal receiver");double before=normal.battery().getEnergy();for(int tick=0;tick<1960;tick++){ClassicSolarBlockEntity.serverTick(level,solar.getBlockPos(),solar.getBlockState(),solar);ClassicWirelessNodeBlockEntity.serverTick(level,node.getBlockPos(),node.getBlockState(),node);graph.tick();}equal(h,before+5880,normal.battery().getEnergy(),"actualsolar to node toNormal produces5880IF");
        near(p,normal.getBlockPos());p.getRandom().setSeed(4096);h.assertTrue(normal.use(p),"real sender-bound Normal GUI session");String token=MachineDeveloperSessions.activeToken(p).orElseThrow().toString();h.assertTrue(MachineDeveloperSessions.request(p,new AcademyNetwork.Request("machine_level",token)),"actualNormal sourceacquisition ingress");for(int tick=0;tick<105;tick++)DevelopmentController.tick(p,snapshot->{});h.assertTrue(AbilityStorage.get(p).category.equals("electromaster")&&AbilityStorage.get(p).level==1,"completedsource RNGacquisition");equal(h,2205,normal.battery().getEnergy(),"fiveNormal stimulations3675IF consumed");h.assertTrue(MachineDeveloperSessions.request(p,new AcademyNetwork.Request("machine_learn",token+":arc_gen")),"actualNormal rootskill ingress");for(int tick=0;tick<63;tick++)DevelopmentController.tick(p,snapshot->{});h.assertTrue(DevelopmentController.process(p).state()==DevelopmentProcess.State.DONE&&AbilityStorage.get(p).learned("arc_gen"),"Arc Generation genuinely earned on crafted Normal");equal(h,0,normal.battery().getEnergy(),"threeNormal stimulations2205IF consume all finite5880");h.succeed();
    }finally{close(p);level.setDayTime(oldTime);level.setWeatherParameters(0,0,oldRain,oldThunder);}}
}

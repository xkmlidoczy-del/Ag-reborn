package cn.academy.port.gametest;

import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.solar.ClassicSolarGenerators;
import cn.academy.port.survival.ClassicMaterials;
import cn.academy.port.tutorial.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Finite native fixtures. Compiled only by the staging worker; main owns the actual runtime lane. */
@GameTestHolder("academy") @PrefixGameTestTemplate(false)
public final class AcademyTutorialRuntimeTests {
    private static final String TEMPLATE="runtime_empty",BATCH="academy_tutorial";
    private static void tick(ServerPlayer player,int count){for(int i=0;i<count;i++)TutorialEvents.tick(new PlayerTickEvent.Post(player));}
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void guide_real_registry_recipe48_native_crafting_consumption_and_use(GameTestHelper h) {
        try(var f=new Actors(h)) {
            var p=f.player();h.assertTrue(BuiltInRegistries.ITEM.getKey(TutorialModule.ITEM.get()).equals(ResourceLocation.parse("academy:tutorial")),"actual guide registered semantic identity");
            h.assertValueEqual(new ItemStack(TutorialModule.ITEM.get()).getMaxStackSize(),64,"classic ACItem default stack64");
            var table=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlockAndUpdate(table,Blocks.CRAFTING_TABLE.defaultBlockState());
            var menu=new CraftingMenu(91,p.getInventory(),ContainerLevelAccess.create(h.getLevel(),table));p.containerMenu=menu;
            menu.getSlot(1).set(new ItemStack(Items.BOOK,2));menu.getSlot(2).set(new ItemStack(ClassicMaterials.CRYSTAL_LOW.get(),2));
            h.assertTrue(menu.getSlot(0).getItem().is(TutorialModule.ITEM.get()),"real native result slot creates source guide");
            var guide=menu.getSlot(0).remove(1);menu.getSlot(0).onTake(p,guide);
            h.assertValueEqual(menu.getSlot(1).getItem().getCount(),1,"one actual book consumed");
            h.assertValueEqual(menu.getSlot(2).getItem().getCount(),1,"one actual low crystal consumed");
            p.setItemInHand(InteractionHand.MAIN_HAND,guide);h.assertTrue(TutorialModule.ITEM.get().use(h.getLevel(),p,InteractionHand.MAIN_HAND).getResult().consumesAction(),"real guide rightclick accepted");
            h.assertValueEqual(guide.getCount(),1,"guide use never consumes source item");
            h.assertTrue(TutorialStorage.get(p).firstOpened(),"real rightclick persists source first-open adapter");
            h.assertTrue(TutorialStorage.get(p).visible("welcome")&&TutorialStorage.get(p).visible("ability_basis")&&!TutorialStorage.get(p).visible("terminal"),"real guide usable immediately without installed terminal");
            h.succeed();
        }
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void first_login_ten_native_tick_events_drop_actual_guide_at_source_height_once(GameTestHelper h) {
        try(var f=new Actors(h)) {
            var p=f.player();h.assertTrue(AcademyTutorialConfig.GIVE_CLOUD_TERMINAL.get(),"this fixture requires classic default giveCloudTerminal=true");
            h.assertFalse(TutorialStorage.get(p).tutorialAcquired(),"new PlayerList login has not received guide");
            tick(p,9);h.assertTrue(f.guides(p).isEmpty(),"first nine local tutorial ticks do not drop");
            tick(p,1);var drops=f.guides(p);h.assertValueEqual(drops.size(),1,"tenth local tutorial tick drops one actual world item");
            var drop=drops.get(0);h.assertTrue(drop.getItem().is(TutorialModule.ITEM.get())&&drop.getItem().getCount()==1,"source grant is tutorial not installer");
            h.assertTrue(drop.getX()==p.getX()&&drop.getZ()==p.getZ()&&drop.getY()==p.getY()+1,"source exact grant coordinates Y+1");
            h.assertTrue(TutorialStorage.get(p).tutorialAcquired(),"source acquired flag saved");tick(p,40);h.assertValueEqual(f.guides(p).size(),1,"later10tick schedules never duplicate guide");
            TutorialStorage.remove(p);tick(p,10);h.assertValueEqual(f.guides(p).size(),1,"real persistent-data reload does not regrant");
            h.succeed();
        }
    }
    public static final class Activations {
        final UUID owner;final List<String> ids=new ArrayList<>();Activations(ServerPlayer p){owner=p.getUUID();}
        @SubscribeEvent public void activated(TutorialActivatedEvent event){if(event.getEntity()!=null&&owner.equals(event.getEntity().getUUID()))ids.add(event.tutorial.id());}
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void successful_real_solar_pickup_unlocks_OR_on_three_tick_boundary_and_remembers_after_removal(GameTestHelper h) {
        try(var f=new Actors(h)) {
            var p=f.player();var hook=new Activations(p);NeoForge.EVENT_BUS.register(hook);
            try {
                p.getInventory().add(new ItemStack(AcademyCraft.DEVELOPER.get()));tick(p,3);
                h.assertFalse(TutorialStorage.get(p).visible("ability_developer"),"inventory insertion alone is not source obtained event");
                var solar=new ItemEntity(h.getLevel(),p.getX(),p.getY(),p.getZ(),new ItemStack(ClassicSolarGenerators.ITEM.get()));h.getLevel().addFreshEntity(solar);f.items.add(solar);solar.setNoPickUpDelay();solar.playerTouch(p);
                var state=TutorialStorage.get(p);h.assertTrue(state.visible("solar_generator"),"actual successful pickup immediately sets remembered OR condition");
                h.assertFalse(state.activated("solar_generator"),"three-tick activation event has not happened early");
                p.getInventory().clearContent();h.assertTrue(state.visible("solar_generator"),"removing acquired item preserves source research");
                tick(p,2);h.assertTrue(hook.ids.isEmpty(),"no activation before dirty scheduler boundary");tick(p,1);
                h.assertTrue(hook.ids.equals(List.of("solar_generator"))&&state.activated("solar_generator"),"actual NeoForge activation event at third tick");
                tick(p,3);h.assertValueEqual(hook.ids.size(),1,"activation never repeats without a new page");
                h.assertFalse(state.visible("wind_generator")||state.visible("terminal")||state.visible("energy_bridge"),"unregistered devices/apps/bridges remain conditional locked references");
                h.succeed();
            } finally {NeoForge.EVENT_BUS.unregister(hook);}
        }
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void native_crafted_smelted_ingress_and_clone_persist_conditions_activated_Misaka_and_grant(GameTestHelper h) {
        try(var f=new Actors(h)) {
            var p=f.player();int misaka=TutorialStorage.get(p).misakaID();
            NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(p,new ItemStack(AcademyCraft.DEVELOPER.get()),p.getInventory()));
            NeoForge.EVENT_BUS.post(new PlayerEvent.ItemSmeltedEvent(p,new ItemStack(ClassicMaterials.ores().get("imag_silicon_ore").get())));
            h.assertTrue(TutorialStorage.get(p).visible("ability_developer")&&TutorialStorage.get(p).visible("ores"),"native crafted/smelted event ingress independently unlocks one OR target");
            tick(p,10);var original=TutorialStorage.encode(TutorialStorage.get(p));var replacement=f.player();TutorialStorage.clone(p,replacement);
            h.assertTrue(TutorialStorage.encode(TutorialStorage.get(replacement)).equals(original),"real player clone keeps exact persisted tutorial fields");
            h.assertValueEqual(TutorialStorage.get(replacement).misakaID(),misaka,"source Misaka identity survives clone");
            h.assertTrue(TutorialStorage.get(replacement).activated("ores")&&TutorialStorage.get(replacement).activated("ability_developer"),"remembered activation IDs survive clone");
            int before=f.guides(replacement).size();tick(replacement,10);h.assertValueEqual(f.guides(replacement).size(),before,"acquired clone cannot regrant tutorial");
            h.succeed();
        }
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void actual_server_guide_request_rejects_unheld_forgery_and_accepts_repeat_item_without_consumption(GameTestHelper h) {
        try(var f=new Actors(h)) {
            var p=f.player();var request=new AcademyNetwork.Request("tutorial_open","");
            h.assertFalse(TutorialNetwork.request(p,request),"unheld client guide request rejected");
            var guide=new ItemStack(TutorialModule.ITEM.get(),3);p.setItemInHand(InteractionHand.OFF_HAND,guide);
            h.assertFalse(TutorialNetwork.request(p,new AcademyNetwork.Request("tutorial_open","invented")),"malformed open value rejected");
            h.assertTrue(TutorialNetwork.request(p,request)&&TutorialNetwork.request(p,request),"real held guide can reopen repeatedly");
            h.assertValueEqual(guide.getCount(),3,"reopening does not consume guide");h.assertTrue(TutorialStorage.get(p).firstOpened(),"reopening retains saved first-open bit");
            p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);h.assertFalse(TutorialNetwork.request(p,request),"no arbitrary request bypass after removing guide");
            h.succeed();
        }
    }
    private static final class Actors implements AutoCloseable {
        final GameTestHelper helper;final List<ServerPlayer> players=new ArrayList<>();final List<EmbeddedChannel> channels=new ArrayList<>();final List<ItemEntity> items=new ArrayList<>();
        Actors(GameTestHelper helper){this.helper=helper;}
        ServerPlayer player() {
            var level=helper.getLevel();var server=level.getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"ACGuideProbe"),false);
            var p=new ServerPlayer(server,level,cookie.gameProfile(),cookie.clientInformation());players.add(p);var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);channels.add(channel);NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection,p,cookie);var at=helper.absoluteVec(new Vec3(2.5,1,2.5));p.moveTo(at.x,at.y,at.z,0,0);p.setNoGravity(true);p.setInvulnerable(true);p.setGameMode(GameType.SURVIVAL);return p;
        }
        List<ItemEntity> guides(ServerPlayer p){return helper.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p.blockPosition()).inflate(4),e->e.getItem().is(TutorialModule.ITEM.get()));}
        @Override public void close() {
            for(var p:players){items.addAll(guides(p));p.closeContainer();TutorialStorage.remove(p);var list=helper.getLevel().getServer().getPlayerList();if(list.getPlayer(p.getUUID())==p)list.remove(p);else p.discard();}
            for(var item:items)item.discard();for(var channel:channels)channel.finishAndReleaseAll();
        }
    }
    private AcademyTutorialRuntimeTests() {}
}

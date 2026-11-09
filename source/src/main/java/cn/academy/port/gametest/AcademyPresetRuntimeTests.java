package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.LegacySingleKeyProtocol;
import cn.academy.port.develop.DeveloperItemEnergy;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.preset.PresetSkills;
import cn.academy.port.skill.CurrentCharging;
import cn.academy.port.skill.GroundShock;
import com.mojang.authlib.GameProfile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongConsumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native world tests call the exact network-ingress seam; FakePlayer discards outbound packets. */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
public final class AcademyPresetRuntimeTests {
    private static final String TEMPLATE="runtime_empty", BATCH="academy_presets";
    private AcademyPresetRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void presets_persist_all_pages_selected_index_cache_reload_and_clone(GameTestHelper helper)throws IOException {
        var fixture=new Fixture(helper);var player=fixture.player();var state=ready(player);
        helper.assertTrue(client(player,"preset_edit","0:0:arc_gen"),"server accepts learned current mapping");
        helper.assertTrue(client(player,"preset_edit","0:1:charging"),"server accepts second current mapping");
        helper.assertTrue(client(player,"preset_edit","3:2:railgun"),"server accepts another page without selecting it");
        helper.assertValueEqual(state.presets.current(),0,"editing another page does not select it");
        helper.assertTrue(client(player,"preset_switch","3"),"activated player can select preset three");
        AbilityStorage.save(player);var saved=player.getPersistentData().getCompound("academy:classic_progress").copy();
        var out=new ByteArrayOutputStream();NbtIo.writeCompressed(saved,out);
        var cold=AbilityStorage.decode(NbtIo.readCompressed(new ByteArrayInputStream(out.toByteArray()),NbtAccounter.unlimitedHeap()));
        assertMappings(helper,cold,"compressed cold decode");
        AbilityStorage.remove(player);var reloaded=AbilityStorage.get(player);
        helper.assertTrue(reloaded!=state,"cache eviction really materializes a new state");assertMappings(helper,reloaded,"cache reload");
        var replacement=fixture.player();NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement,player,false));
        var cloned=AbilityStorage.get(replacement);assertMappings(helper,cloned,"clone event");
        cloned.selectCategory("teleporter");helper.assertValueEqual(cloned.presets.current(),3,"classic category clear preserves selected index");
        assertEmpty(helper,cloned,"category clear");helper.succeed();
    }
    private static void assertMappings(GameTestHelper helper,AbilityProgress state,String label){
        helper.assertValueEqual(state.presets.current(),3,label+" selected index");
        helper.assertValueEqual(state.presets.skill(0,0),"arc_gen",label+" first mapping");
        helper.assertValueEqual(state.presets.skill(0,1),"charging",label+" second mapping");
        helper.assertValueEqual(state.presets.currentSkill(2),"railgun",label+" selected mapping");
        helper.assertTrue(state.presets.currentSkill(0).isEmpty(),label+" sparse empty slot");
    }

    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void presets_reject_malformed_duplicate_unlearned_passive_unported_and_raw_cast_packets(GameTestHelper helper){
        var fixture=new Fixture(helper);var player=fixture.player();var state=ready(player);
        state.learn("brain_course");var beforeWrongCategory=AbilityStorage.encode(state);boolean wrongCategoryRejected=false;
        try{state.learn("electron_bomb");}catch(IllegalStateException expected){wrongCategoryRejected=true;}
        helper.assertTrue(wrongCategoryRejected&&AbilityStorage.encode(state).equals(beforeWrongCategory),"source wrong-category learn rejects before any state/event mutation");
        // Deliberately malformed cross-category learned-bit fixture for the existing ingress denials.
        state.experience.put("electron_bomb",0D);
        helper.assertTrue(client(player,"preset_edit","0:0:arc_gen"),"control mapping accepted");
        var unportedPlayer=fixture.player();var unportedState=AbilityStorage.get(unportedPlayer);
        unportedState.selectCategory("vecmanip");unportedState.setLevel(5);unportedState.activated=true;
        var missing=cn.academy.port.SkillCatalog.ALL.stream().filter(skill->skill.category().equals("vecmanip")&&skill.controllable()&&!PresetSkills.IMPLEMENTED.contains(skill.id())).findFirst();
        if(missing.isPresent()){
            String id=missing.orElseThrow().id();unportedState.learn(id);
            long unportedRevision=unportedState.presets.revision();
            helper.assertFalse(client(unportedPlayer,"preset_edit","0:0:"+id),"learned same-category level-valid unported preset rejected");
            helper.assertValueEqual(unportedState.presets.revision(),unportedRevision,"unported rejection is atomic");
        }
        long revision=state.presets.revision();double cp=state.cp;
        for(String value:List.of("", "4:0:arc_gen", "-1:0:arc_gen", "00:0:arc_gen", "0:4:arc_gen", "0:-1:arc_gen", "0:0:arc_gen:extra", "0:1:arc_gen", "0:1:unknown", "0:1:brain_course", "0:1:vec_accel", "0:1:electron_bomb", "0:1:plotter", "0:1:"+"x".repeat(49)))
            helper.assertFalse(client(player,"preset_edit",value),"reject edit "+value);
        for(String value:List.of("", "4", "-1", "00", "2147483647", "0:1"))helper.assertFalse(client(player,"preset_switch",value),"reject switch "+value);
        helper.assertValueEqual(state.presets.current(),0,"malformed input leaves selected index");
        helper.assertValueEqual(state.presets.revision(),revision,"rejected edits never mutate revision");
        for(String action:List.of("cast","skill_start","charge","skill_release","skill_abort","abort"))helper.assertFalse(client(player,action,"arc_gen"),"raw low-level action unavailable over network "+action);
        close(helper,state.cp,cp,"malformed/raw actions never consume CP");
        helper.assertFalse(client(player,"slot_press","3"),"unbound slot cannot cast");
        helper.assertFalse(client(player,"slot_press","00"),"noncanonical slot index cannot cast");
        helper.assertTrue(client(player,"slot_press","0"),"server resolves current slot to ArcGen");
        helper.assertTrue(state.cp<cp,"resolved cast pays server-owned cost");cp=state.cp;
        helper.assertFalse(client(player,"slot_press","0"),"server cooldown blocks replay");close(helper,state.cp,cp,"cooldown replay consumes nothing");
        state.cooldowns.clear();state.interfering=true;helper.assertFalse(client(player,"slot_press","0"),"interference blocks mapped cast");
        state.interfering=false;state.activated=false;helper.assertFalse(client(player,"slot_press","0"),"inactive ability blocks mapped cast");
        helper.assertFalse(client(player,"preset_switch","1"),"inactive ability cannot switch");
        state.activated=true;state.experience.remove("arc_gen");helper.assertFalse(client(player,"slot_press","0"),"mapping cannot manufacture learning");
        close(helper,state.cp,cp,"all unauthorized replays consume nothing");helper.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void presets_switch_and_any_page_edit_abort_holds_without_refund_or_release_revival(GameTestHelper helper){
        try(var fixture=new LegacySingleKeyNativeFixture(helper)){
        var player=fixture.player;var state=ready(player);
        player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(AcademyCraft.DEVELOPER.get()));
        client(player,"preset_edit","0:0:charging");fixture.assertPlainDenied(0,LegacySingleKeyProtocol.Skill.CHARGING);
        helper.assertTrue(fixture.press(0,LegacySingleKeyProtocol.Skill.CHARGING,1),"positive current-owner Charging input1 accepted");
        var charging1=fixture.accepted(LegacySingleKeyProtocol.Skill.CHARGING,1);
        fixture.assertPlainDenied(0,LegacySingleKeyProtocol.Skill.CHARGING);
        helper.assertTrue(CurrentCharging.active(player),"mapped charging hold starts");double strain=state.overload;
        // M34 orders each source key-abort callback before the preset mutation request.
        helper.assertTrue(client(player,"preset_edit","2:2:arc_gen"),"server-only edit accepted without a client callback");
        helper.assertTrue(CurrentCharging.active(player),"server-only edit preserves hold until its key-abort callback");
        helper.assertValueEqual(state.presets.skill(2,2),"arc_gen","server-only edit still commits its mapping");
        helper.assertTrue(fixture.terminal(LegacySingleKeyProtocol.ABORT,charging1),"Charging key-abort authenticated against old mapping before switch");
        helper.assertFalse(CurrentCharging.active(player),"Charging callback has already ended hold before switch");
        helper.assertTrue(client(player,"preset_switch","1"),"valid activated switch accepted");
        helper.assertFalse(CurrentCharging.active(player),"switched mapping cannot revive the callback-ended hold");close(helper,state.overload,strain,"switch never refunds paid strain");
        helper.assertFalse(fixture.terminal(LegacySingleKeyProtocol.RELEASE,charging1),"unbound destination release cannot affect old mapping");
        client(player,"preset_edit","1:3:charging");helper.assertTrue(fixture.press(3,LegacySingleKeyProtocol.Skill.CHARGING,2),"positive current-owner Charging input2 accepted");var charging2=fixture.accepted(LegacySingleKeyProtocol.Skill.CHARGING,2);helper.assertTrue(CurrentCharging.active(player),"new current mapping starts");strain=state.overload;
        helper.assertTrue(fixture.terminal(LegacySingleKeyProtocol.ABORT,charging2),"Charging key-abort delivered before other-page edit");
        helper.assertFalse(CurrentCharging.active(player),"other-page edit follows completed Charging callback");
        helper.assertTrue(client(player,"preset_edit","0:1:arc_gen"),"editing nonselected page accepted");
        helper.assertFalse(CurrentCharging.active(player),"other-page mapping edit preserves completed callback termination");close(helper,state.overload,strain,"edit never refunds strain");
        helper.assertFalse(fixture.terminal(LegacySingleKeyProtocol.RELEASE,charging2),"late accepted Charging release is inert");helper.assertFalse(CurrentCharging.active(player),"late release cannot revive canceled context");
        helper.assertTrue(fixture.press(3,LegacySingleKeyProtocol.Skill.CHARGING,3),"positive current-owner Charging input3 accepted");var charging3=fixture.accepted(LegacySingleKeyProtocol.Skill.CHARGING,3);helper.assertTrue(CurrentCharging.active(player),"fresh press restarts hold");strain=state.overload;
        helper.assertTrue(fixture.terminal(LegacySingleKeyProtocol.ABORT,charging3),"Charging key-abort delivered before removing current mapping");
        helper.assertFalse(CurrentCharging.active(player),"mapping removal follows completed Charging callback");
        client(player,"preset_edit","1:3:");helper.assertFalse(CurrentCharging.active(player),"removed mapping cannot revive the callback-ended hold");
        helper.assertTrue(state.presets.currentSkill(3).isEmpty(),"remove commits empty slot");close(helper,state.overload,strain,"remove never refunds strain");helper.succeed();
        }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void presets_learning_is_server_finite_and_never_auto_binds(GameTestHelper helper){
        var fixture=new Fixture(helper);var player=fixture.player();var state=AbilityStorage.get(player);
        state.selectCategory("electromaster");state.setLevel(1);state.activated=true;
        var developer=new ItemStack(AcademyCraft.DEVELOPER.get());new DeveloperItemEnergy(developer,DeveloperType.PORTABLE).energy(10000);
        player.setItemSlot(EquipmentSlot.MAINHAND,developer);
        helper.assertFalse(client(player,"preset_edit","0:0:arc_gen"),"unlearned selection rejected before development");
        helper.assertTrue(client(player,"learn","arc_gen"),"recognized learning request reaches authoritative developer");
        helper.assertTrue(DevelopmentController.process(player).isDeveloping(),"real finite portable process begins");
        helper.assertFalse(state.learned("arc_gen"),"client request does not grant learning");
        fixture.worldTicks(elapsed->{
            if(elapsed<78){helper.assertFalse(state.learned("arc_gen"),"three 26-tick stimulations cannot finish early");helper.assertFalse(PresetSkills.selectable(state,"arc_gen"),"in-flight learning cannot be selected");}
            else if(elapsed==78){
                helper.assertTrue(state.learned("arc_gen"),"server completion grants skill at78 ticks");assertEmpty(helper,state,"successful learning");
                close(helper,new DeveloperItemEnergy(developer,DeveloperType.PORTABLE).energy(),7660,"real finite energy was consumed");
                helper.assertTrue(client(player,"preset_edit","0:0:arc_gen"),"learned skill can now be explicitly bound");
                helper.assertValueEqual(state.presets.currentSkill(0),"arc_gen","explicit binding is separate from learning");helper.succeed();
            }
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void presets_groundshock_slot_dispatch_and_switch_edit_cancellation(GameTestHelper helper){
        try(var fixture=new LegacySingleKeyNativeFixture(helper)){
        var player=fixture.player;var state=AbilityStorage.get(player);
        state.selectCategory("vecmanip");state.setLevel(1);state.learn("dir_shock");state.learn("ground_shock");state.activated=true;
        double cp=state.cp;
        helper.assertTrue(client(player,"preset_edit","0:0:ground_shock"),"merged GroundShock is a selectable learned controllable");
        fixture.assertPlainDenied(0,LegacySingleKeyProtocol.Skill.GROUND_SHOCK);
        helper.assertTrue(fixture.press(0,LegacySingleKeyProtocol.Skill.GROUND_SHOCK,1),"authoritative current slot dispatch accepted");
        var ground1=fixture.accepted(LegacySingleKeyProtocol.Skill.GROUND_SHOCK,1);
        fixture.assertPlainDenied(0,LegacySingleKeyProtocol.Skill.GROUND_SHOCK);
        helper.assertTrue(GroundShock.active(player),"slot press reaches actual GroundShock handler");
        helper.assertFalse(client(player,"skill_start","ground_shock"),"raw names remain forbidden over wire");
        helper.assertTrue(fixture.terminal(LegacySingleKeyProtocol.ABORT,ground1),"GroundShock key-abort authenticated before switch");
        helper.assertFalse(GroundShock.active(player),"GroundShock callback ends hold before switch");
        helper.assertTrue(client(player,"preset_switch","1"),"switch accepted");helper.assertFalse(GroundShock.active(player),"switch preserves callback-ended GroundShock hold");
        helper.assertFalse(fixture.terminal(LegacySingleKeyProtocol.RELEASE,ground1),"late accepted GroundShock release is inert");helper.assertFalse(GroundShock.active(player),"stale destination release cannot revive");
        client(player,"preset_edit","1:1:ground_shock");helper.assertTrue(fixture.press(1,LegacySingleKeyProtocol.Skill.GROUND_SHOCK,2),"positive current-owner GroundShock input2 accepted");var ground2=fixture.accepted(LegacySingleKeyProtocol.Skill.GROUND_SHOCK,2);helper.assertTrue(GroundShock.active(player),"fresh press starts mapped GroundShock");
        helper.assertTrue(fixture.terminal(LegacySingleKeyProtocol.ABORT,ground2),"GroundShock key-abort authenticated before other-page edit");
        helper.assertFalse(GroundShock.active(player),"GroundShock callback ends hold before other-page edit");
        client(player,"preset_edit","0:0:");helper.assertFalse(GroundShock.active(player),"other-page edit preserves callback-ended GroundShock hold");
        close(helper,state.cp,cp,"free source start and cancellation do not consume CP");close(helper,state.overload,0,"free source start and cancellation do not consume overload");
        helper.succeed();
        }
    }
    private static void assertEmpty(GameTestHelper helper,AbilityProgress state,String label){for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++)helper.assertTrue(state.presets.skill(preset,slot).isEmpty(),label+" empty "+preset+":"+slot);}
    private static AbilityProgress ready(ServerPlayer player){var state=AbilityStorage.get(player);state.selectCategory("electromaster");state.setLevel(5);state.activated=true;state.learn("arc_gen");state.learn("charging");state.learn("railgun");return state;}
    private static boolean client(ServerPlayer player,String action,String value){return AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request(action,value));}
    private static void close(GameTestHelper helper,double actual,double expected,String label){helper.assertTrue(Math.abs(actual-expected)<.00001,label+": expected "+expected+", got "+actual);}
    private static final class Fixture {
        final GameTestHelper helper;final List<ServerPlayer> players=new ArrayList<>();final long started;long previous;boolean closed;
        Fixture(GameTestHelper helper){this.helper=helper;started=previous=helper.getLevel().getGameTime();helper.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo info){}public void testPassed(GameTestInfo info,GameTestRunner runner){cleanup();}public void testFailed(GameTestInfo info,GameTestRunner runner){cleanup();}public void testAddedForRerun(GameTestInfo original,GameTestInfo rerun,GameTestRunner runner){cleanup();}});}
        ServerPlayer player(){var p=new FakePlayer(helper.getLevel(),new GameProfile(UUID.randomUUID(),"[AC-PresetTest]"));var pos=helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5,1,2.5));p.moveTo(pos.x,pos.y,pos.z,0,0);p.setNoGravity(true);p.getAbilities().instabuild=false;players.add(p);return p;}
        void worldTicks(LongConsumer assertion){helper.onEachTick(()->{long now=helper.getLevel().getGameTime();if(closed||helper.testInfo.isDone()||now<=previous)return;helper.assertValueEqual(now-previous,1L,"consecutive actual world ticks");previous=now;for(var player:players){player.tickCount++;NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));}assertion.accept(now-started);});}
        void cleanup(){if(closed)return;closed=true;for(var player:players)NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));}
    }
}

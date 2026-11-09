/* AcademyCraft1.0.7 VecManip Lv2 native fixtures, GPLv3; see NOTICE. */
package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.core.*;
import cn.academy.port.develop.*;
import cn.academy.port.machine.*;
import cn.academy.port.skill.*;
import cn.academy.port.preset.PresetSkills;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.IntConsumer;

/** Compiled ONLY in the worker lane. Native execution/GPU/audio acceptance belongs to main.
 * Combat fixtures explicitly seed learned state. Learning fixtures never grant either target. */
@GameTestHolder("academy") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="academy")
public final class AcademyVectorStarterRuntimeTests {
    private static final String TEMPLATE="vector_starter_empty";
    @SubscribeEvent public static void install(LevelEvent.Load event){
        if(!GameTestHooks.isGametestEnabled()||!(event.getLevel() instanceof ServerLevel world))return;
        var tag=new CompoundTag();var size=new ListTag();size.add(IntTag.valueOf(32));size.add(IntTag.valueOf(16));size.add(IntTag.valueOf(32));tag.put("size",size);
        var blocks=new ListTag();var first=new CompoundTag();var pos=new ListTag();pos.add(IntTag.valueOf(0));pos.add(IntTag.valueOf(0));pos.add(IntTag.valueOf(0));first.put("pos",pos);first.putInt("state",0);blocks.add(first);tag.put("blocks",blocks);tag.put("entities",new ListTag());
        var palette=new ListTag();var air=new CompoundTag();air.putString("Name","minecraft:air");palette.add(air);tag.put("palette",palette);
        world.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy",TEMPLATE)).load(world.registryAccess().lookupOrThrow(Registries.BLOCK),tag);
    }
    @GameTest(template=TEMPLATE,batch="academy_vector_01",timeoutTicks=30)
    public static void real_powered_Normal_GUI_learns_both_zero_mastery_targets_with_source_gates(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var stack=new ItemStack(MachineDevelopers.NORMAL_ITEM.get());p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        BlockPos base=h.absolutePos(new BlockPos(10,1,14));
        h.assertTrue(MachineDevelopers.NORMAL_ITEM.get().place(new BlockPlaceContext(p,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(base),Direction.UP,base,false))).consumesAction(),"Actual eight-cell Normal Developer placement");
        var machine=(MachineDeveloperBlockEntity)h.getLevel().getBlockEntity(base);p.moveTo(base.getX()+.5,base.getY(),base.getZ()-1.5,0,0);
        var s=AbilityStorage.get(p);s.selectCategory("vecmanip");s.setLevel(2);s.learn("dir_shock");
        for(String id:List.of(VecAccel.ID,VecDeviation.ID)){
            h.assertFalse(s.learned(id),"Target has never been fixture-granted");machine.battery().load(50000);
            h.assertTrue(machine.use(p),"Native powered Developer GUI session");String token=MachineDeveloperSessions.activeToken(p).orElseThrow().toString();
            h.assertFalse(MachineDeveloperSessions.request(p,new AcademyNetwork.Request("machine_learn",UUID.randomUUID()+":"+id)),"Forged machine session denied");
            if(id.equals(VecDeviation.ID)){s.experience.remove(VecAccel.ID);h.assertFalse(MachineDeveloperSessions.request(p,new AcademyNetwork.Request("machine_learn",token+":"+id)),"Source learned-parent gate denied");s.learn(VecAccel.ID);}
            int level=s.level;s.level=1;h.assertFalse(MachineDeveloperSessions.request(p,new AcademyNetwork.Request("machine_learn",token+":"+id)),"Source level-two gate denied");s.level=level;
            h.assertTrue(MachineDeveloperSessions.request(p,new AcademyNetwork.Request("machine_learn",token+":"+id)),"Actual common learning ingress");
            for(int t=1;t<=105;t++){DevelopmentController.tick(p,snapshot->{});if(t<105)h.assertFalse(s.learned(id),"Source five stimulations of twenty-one ticks");}
            h.assertTrue(s.learned(id)&&s.exp(id)==0&&DevelopmentController.process(p).state()==DevelopmentProcess.State.DONE,"Genuinely learned zero-mastery target");near(h,machine.battery().getEnergy(),46325,"Finite Normal IF cost 3675");
            s.activated=true;h.assertTrue(bind(s,id.equals(VecAccel.ID)?0:1,id),"Earned target binds actual preset");MachineDeveloperSessions.close(p);
        }
        h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="academy_vector_02",timeoutTicks=20)
    public static void real_portable_item_learning_drains_3900_IF_and_empty_machine_cannot_grant(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.selectCategory("vecmanip");s.setLevel(2);s.learn("dir_shock");
        var developer=new ItemStack(AcademyCraft.DEVELOPER.get());p.setItemInHand(InteractionHand.MAIN_HAND,developer);var energy=new DeveloperItemEnergy(developer,DeveloperType.PORTABLE);energy.energy(10000);
        h.assertTrue(AcademyCraft.DEVELOPER.get().use(h.getLevel(),p,InteractionHand.MAIN_HAND).getResult().consumesAction(),"Actual portable item opens Developer screen route");
        AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("learn",VecAccel.ID));
        for(int tick=1;tick<=130;tick++){DevelopmentController.tick(p,snapshot->{});if(tick<130)h.assertFalse(s.learned(VecAccel.ID),"No command or early mastery grant");}
        h.assertTrue(s.learned(VecAccel.ID)&&s.exp(VecAccel.ID)==0,"Actual portable learning completes at zero mastery");near(h,energy.energy(),6100,"Finite portable cost 3900 IF");
        energy.energy(0);AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("learn",VecDeviation.ID));DevelopmentController.tick(p,snapshot->{});
        h.assertTrue(DevelopmentController.process(p).state()==DevelopmentProcess.State.FAILED&&!s.learned(VecDeviation.ID),"Empty real portable energy cannot grant target");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="academy_vector_03",timeoutTicks=30)
    public static void authoritative_charge20_uses_previous_body_rotation_and_dismounts_native_vehicle(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=ready(p,VecAccel.ID,0);f.floor(p);p.yRotO=70;p.xRotO=-15;p.setYRot(-40);p.setXRot(30);p.setYHeadRot(180);
        var boat=new net.minecraft.world.entity.vehicle.Boat(EntityType.BOAT,h.getLevel());boat.setPos(p.position());f.entity(boat);p.startRiding(boat,true);
        h.assertTrue(VecAccel.start(p,1),"Server-owned charge starts");double cp=s.cp;
        f.ticks(20,t->{p.yRotO=70;p.xRotO=-15;VecAccel.tick(p);int ticks=VecAccel.heldTicks(p);VecAccel.tick(p);h.assertValueEqual(VecAccel.heldTicks(p),ticks,"Native tick deduplication");if(t==20){
            Vec3 expected=VecAccel.initialVelocity(70,-15,20);h.assertTrue(VecAccel.release(p,1),"Source full sine charge performs");near(h,p.getDeltaMovement().x,expected.x,"Previous body yaw controls X");near(h,p.getDeltaMovement().y,expected.y,"Previous body pitch minus ten controls Y");near(h,p.getDeltaMovement().z,expected.z,"Previous body controls Z");
            h.assertFalse(p.isPassenger(),"Source native dismount");near(h,p.fallDistance,0,"Source initial fall reset");near(h,s.cp,cp-120,"Source novice CP");near(h,s.overload,30,"Source novice overload");near(h,s.exp(VecAccel.ID),.002F,"One original EXP award");h.assertValueEqual(s.cooldowns.get(VecAccel.ID),80,"Source eighty-tick cooldown");h.assertFalse(VecAccel.release(p,1),"Duplicate release cannot spend or launch");h.succeed();}});
    }
    @GameTest(template=TEMPLATE,batch="academy_vector_04",timeoutTicks=10)
    public static void tick0_airborne_quirk_strict_half_mastery_ground_trace_and_failed_release(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=ready(p,VecAccel.ID,0);VecAccel.start(p,1);h.assertTrue(VecAccel.release(p,1),"Initially true permits zero-tick airborne release");reset(s,VecAccel.ID,.5);h.assertTrue(VecAccel.start(p,2),"Exactly half mastery charge starts");
        h.runAtTickTime(1,()->{VecAccel.tick(p);h.assertFalse(VecAccel.release(p,2),"Exactly half mastery requires ground within two blocks");reset(s,VecAccel.ID,Math.nextUp(.5F));h.assertTrue(VecAccel.start(p,3),"Above half charge starts");h.runAtTickTime(2,()->{VecAccel.tick(p);h.assertTrue(VecAccel.release(p,3),"Strictly above half ignores ground");reset(s,VecAccel.ID,0);f.floor(p);s.cp=119;VecAccel.start(p,4);Vec3 motion=p.getDeltaMovement();h.assertFalse(VecAccel.release(p,4),"Modern authoritative insufficient CP fails closed");h.assertTrue(p.getDeltaMovement().equals(motion)&&s.cooldowns.isEmpty()&&s.exp(VecAccel.ID)==0,"Failure no impulse, cooldown or EXP");h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch="academy_vector_05",timeoutTicks=10)
    public static void actual_wire_slots_reject_replay_forged_terminal_legacy_and_unbound_casts(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=ready(p,VecAccel.ID,0);bind(s,0,VecAccel.ID);f.floor(p);
        h.assertFalse(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("skill_start",VecAccel.ID)),"Arbitrary skill-name wire ingress denied");
        h.assertFalse(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_press_token","0:01")),"Noncanonical nonce denied");
        h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_press_token","0:1")),"Actual sender-bound slot starts");
        h.assertFalse(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_release_token","0:2")),"Forged terminal nonce denied");
        h.assertFalse(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_release","0")),"Legacy terminal cannot bypass nonce");
        h.assertTrue(VecAccel.active(p),"Forged request does not replace context");
        h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_abort_token","0:1")),"Matching abort accepted");
        h.assertFalse(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_press_token","0:1")),"Aborted physical nonce replay denied");
        near(h,s.exp(VecAccel.ID),0,"Abort does not train");h.assertTrue(s.cooldowns.isEmpty(),"Abort does not invent cooldown");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="academy_vector_06",timeoutTicks=10)
    public static void native_projectiles_self_stop_fixed_cost_difficulty_EXP_marks_and_visited_once(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=ready(p,VecDeviation.ID,0);double cp=s.cp;
        var arrow=new Arrow(EntityType.ARROW,h.getLevel());arrow.setBaseDamage(9);f.entity(arrow,p.position().add(0,.1,2));
        var snow=new Snowball(EntityType.SNOWBALL,h.getLevel());f.entity(snow,p.position().add(1,.1,2));
        var potion=new ThrownPotion(EntityType.POTION,h.getLevel());f.entity(potion,p.position().add(-1,.1,2));
        var mob=f.mob(p.position().add(2,0,2));var item=new ItemEntity(h.getLevel(),p.getX()-2,p.getY(),p.getZ()+2,new ItemStack(Items.STONE));f.entity(item);var xp=new ExperienceOrb(h.getLevel(),p.getX()+2,p.getY(),p.getZ(),1);f.entity(xp);
        h.assertTrue(VecDeviation.start(p,1),"Toggle context starts");
        h.runAtTickTime(1,()->{p.setDeltaMovement(new Vec3(.3,.2,.4));VecDeviation.tick(p);
            h.assertTrue(VecDeviation.marked(p)&&p.getDeltaMovement().equals(Vec3.ZERO),"Source query includes and stops caster");
            h.assertTrue(VecDeviation.marked(arrow)&&VecDeviation.marked(snow)&&VecDeviation.marked(potion),"Native persistent deviation marks");near(h,arrow.getBaseDamage(),0,"Native arrow base damage cleared");
            h.assertFalse(VecDeviation.marked(mob)||VecDeviation.marked(item)||VecDeviation.marked(xp),"Original Mob Item XP exclusions");
            near(h,s.exp(VecDeviation.ID),.001F+.001F+.001F*.1F+.001F*1.4F,"Caster arrow Snowball Potion EXP only");
            near(h,s.cp,(float)cp-13-4*15-VecDeviationSession.normalCp(s.exp(VecDeviation.ID)),"Fixed four costs and dynamic extra tick drain");
            int ticks=VecDeviation.heldTicks(p);double paid=s.cp;VecDeviation.tick(p);near(h,s.cp,paid,"Same native time cannot replay debit");h.assertValueEqual(VecDeviation.heldTicks(p),ticks,"Tick context deduplicated");
            arrow.setDeltaMovement(new Vec3(.4,0,0));h.runAtTickTime(2,()->{double before=s.cp;VecDeviation.tick(p);near(h,s.cp,(float)before-13-VecDeviationSession.normalCp(s.exp(VecDeviation.ID)),"Visited entities never force-debited again");h.assertTrue(arrow.getDeltaMovement().lengthSqr()>0,"Source marks do not immobilize future native physics");
                VecDeviation.abort(p,1);h.assertTrue(s.cooldowns.isEmpty(),"Source no end cooldown");h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch="academy_vector_07",timeoutTicks=10)
    public static void failed_tick_debit_still_stops_entities_trains_forced_full_cost_and_then_ends(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=ready(p,VecDeviation.ID,0);var snow=new Snowball(EntityType.SNOWBALL,h.getLevel());f.entity(snow,p.position().add(0,0,2));VecDeviation.start(p,1);s.cp=0;double extra=s.extraCp;
        h.runAtTickTime(1,()->{VecDeviation.tick(p);h.assertFalse(VecDeviation.active(p),"Failed first debit requests end after source body");h.assertTrue(VecDeviation.marked(p)&&VecDeviation.marked(snow),"Deferred termination still stops caster and projectile");near(h,s.cp,0,"Forced CP clamps zero");near(h,s.extraCp,extra+30*.0025,"Full two requested costs train capacity");near(h,s.exp(VecDeviation.ID),.001F+.0001F,"Difficulty-weighted EXP still awarded");h.assertTrue(s.cooldowns.isEmpty(),"Failure no invented cooldown");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch="academy_vector_08",timeoutTicks=90)
    public static void native_global_damage_reduction_uses_owner_CP_postaward_EXP_and_honors_cancellation(GameTestHelper h){
        var f=new Fixture(h);var owner=f.player();var victim=f.connectedPlayer();victim.setPos(owner.position().add(1,0,0));var state=ready(owner,VecDeviation.ID,0);double cp=state.cp;
        // FakePlayer is unconditionally invulnerable. The ordinary admitted player must also
        // finish the native sixty-tick spawn protection before testing actual damage events.
        h.runAtTickTime(61,()->{h.assertTrue(VecDeviation.start(owner,1),"Native damage context starts after spawn protection");
        h.assertTrue(victim.hurt(victim.damageSources().generic(),10),"Ordinary native victim accepts generic damage");near(h,state.cp,cp-15,"Global context owner pays reduction");near(h,state.exp(VecDeviation.ID),10*.0006F,"Owner earns damaged-other-player EXP");near(h,victim.getHealth(),20-10*(1-VecDeviationSession.reduction(state.exp(VecDeviation.ID))),"Post-award fraction applied to actual native health");
        state.cp=0;victim.invulnerableTime=0;victim.setHealth(20);victim.hurt(victim.damageSources().generic(),4);near(h,state.cp,0,"Zero CP still reduces source damage");
        var protection=new CancelDamage(victim);f.hook(protection);double exp=state.exp(VecDeviation.ID);float health=victim.getHealth();victim.invulnerableTime=0;victim.hurt(victim.damageSources().generic(),2);near(h,state.exp(VecDeviation.ID),exp,"Canceled native incoming damage does not leak EXP");near(h,victim.getHealth(),health,"Protection cancellation retained");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch="academy_vector_09",timeoutTicks=10)
    public static void large_and_small_fireballs_are_removed_explosion_protection_and_source_EXP_preserved(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=ready(p,VecDeviation.ID,0);var large=new LargeFireball(h.getLevel(),p,Vec3.ZERO,3);f.entity(large,p.position().add(2,0,2));var small=new SmallFireball(EntityType.SMALL_FIREBALL,h.getLevel());f.entity(small,p.position().add(-2,0,2));
        var protection=new CancelExplosion();f.hook(protection);VecDeviation.start(p,1);
        h.runAtTickTime(1,()->{VecDeviation.tick(p);h.assertTrue(large.isRemoved()&&small.isRemoved(),"Native fireballs discarded");h.assertTrue(protection.seen,"Original large-fireball explosion uses native event pipeline");h.assertFalse(VecDeviation.marked(large)||VecDeviation.marked(small),"Source fireball branches never mark entities");near(h,s.exp(VecDeviation.ID),3*.001F,"Caster and both fireballs EXP remains despite protected explosion");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch="academy_vector_10",timeoutTicks=10)
    public static void toggle_keyup_persists_second_press_ends_and_lifecycle_abort_has_no_cooldown(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=ready(p,VecDeviation.ID,0);bind(s,0,VecDeviation.ID);
        h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_press_token","0:1")),"Physical toggle down starts");
        h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_release_token","0:1"))&&VecDeviation.active(p),"Source toggle persists through key-up");
        h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_press_token","0:2"))&&!VecDeviation.active(p),"Second physical press terminates");
        h.assertFalse(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_press_token","0:2")),"Second press replay cannot create replacement");
        reset(s,VecDeviation.ID,0);h.assertTrue(VecDeviation.start(p,3),"Independent interference context starts with restored resources");s.interfering=true;VecDeviation.tick(p);h.assertTrue(VecDeviation.active(p),"Source toggle survives delegate interference abort");s.interfering=false;h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("preset_switch","1"))&&VecDeviation.active(p),"Source toggle survives actual preset flush");AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("abort_active",""));h.assertFalse(VecDeviation.active(p),"Actual V handler abort route");h.assertTrue(s.cooldowns.isEmpty(),"No source cooldown introduced");
        s.learn(VecAccel.ID);s.cp=Double.NaN;h.assertFalse(VecAccel.start(p,1),"Nonfinite resource denied");s.cp=s.maxCp();p.yRotO=Float.NaN;h.assertFalse(VecAccel.start(p,1),"Nonfinite previous native aim denied");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="academy_vector_11",timeoutTicks=10)
    public static void registered_native_request_codec_sender_listener_and_real_context_ingress(GameTestHelper h){
        var f=new Fixture(h);var p=f.connectedPlayer();var s=ready(p,VecAccel.ID,0);bind(s,0,VecAccel.ID);f.floor(p);
        var wire=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        AcademyNetwork.Request.CODEC.encode(wire,new AcademyNetwork.Request("slot_press_token","0:1"));var decoded=AcademyNetwork.Request.CODEC.decode(wire);wire.release();
        h.assertTrue(decoded.action().equals("slot_press_token")&&decoded.value().equals("0:1"),"Actual bounded Request codec round-trip");
        NetworkRegistry.handleModdedPayload(p.connection,new ServerboundCustomPayloadPacket(decoded));
        h.runAtTickTime(1,()->{h.assertTrue(VecAccel.active(p),"Actual registered native sender listener starts server context");NetworkRegistry.handleModdedPayload(p.connection,new ServerboundCustomPayloadPacket(new AcademyNetwork.Request("slot_abort_token","0:9")));
            h.runAtTickTime(2,()->{h.assertTrue(VecAccel.active(p),"Forged native terminal cannot cancel context");NetworkRegistry.handleModdedPayload(p.connection,new ServerboundCustomPayloadPacket(new AcademyNetwork.Request("slot_abort_token","0:1")));
                h.runAtTickTime(3,()->{h.assertFalse(VecAccel.active(p),"Matching registered native terminal accepted");near(h,s.exp(VecAccel.ID),0,"Abort transport never grants mastery");h.succeed();});});});
    }
    @GameTest(template=TEMPLATE,batch="academy_vector_12",timeoutTicks=10)
    public static void exact_five_block_feet_sphere_marked_reentry_and_native_save_persistence(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=ready(p,VecDeviation.ID,0);var edge=new Snowball(EntityType.SNOWBALL,h.getLevel());f.entity(edge,p.position().add(5,0,0));var outside=new Snowball(EntityType.SNOWBALL,h.getLevel());f.entity(outside,p.position().add(5.001,0,0));var corner=new Snowball(EntityType.SNOWBALL,h.getLevel());f.entity(corner,p.position().add(4,0,4));
        VecDeviation.start(p,1);h.runAtTickTime(1,()->{VecDeviation.tick(p);h.assertTrue(VecDeviation.marked(edge),"Inclusive source feet distance five");h.assertFalse(VecDeviation.marked(outside)||VecDeviation.marked(corner),"Outside sphere and cube corner excluded");
            var saved=edge.saveWithoutId(new CompoundTag());var reloaded=new Snowball(EntityType.SNOWBALL,h.getLevel());reloaded.load(saved);h.assertTrue(VecDeviation.marked(reloaded),"Native entity NBT reload retains persistent source mark");
            VecDeviation.abort(p,1);double exp=s.exp(VecDeviation.ID);VecDeviation.start(p,2);h.runAtTickTime(2,()->{VecDeviation.tick(p);near(h,s.exp(VecDeviation.ID),exp,"Marked caster and projectile excluded from future contexts");h.succeed();});});
    }
    private static AbilityProgress ready(ServerPlayer p,String id,double exp){var s=AbilityStorage.get(p);s.selectCategory("vecmanip");s.setLevel(2);s.learn(id);s.experience.put(id,exp);s.activated=true;return s;}
    private static void reset(AbilityProgress s,String id,double exp){s.cooldowns.clear();s.cooldownMaxTicks.clear();s.experience.put(id,exp);s.cp=s.maxCp();s.overload=0;s.overloadFine=true;s.activated=true;s.interfering=false;}
    private static boolean bind(AbilityProgress s,int slot,String id){return s.presets.edit(0,slot,id,value->PresetSkills.selectable(s,value));}
    private static void near(GameTestHelper h,double a,double b,String why){h.assertTrue(Math.abs(a-b)<1E-5,why+" actual="+a+" expected="+b);}
    private static final class Fixture {
        final GameTestHelper h;final List<ServerPlayer> players=new ArrayList<>();final List<EmbeddedChannel> channels=new ArrayList<>();final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){this.h=h;h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){cleanup();}public void testFailed(GameTestInfo i,GameTestRunner r){cleanup();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){cleanup();}});}
        FakePlayer player(){var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[AC-Vector]"));players.add(p);Vec3 at=h.absoluteVec(new Vec3(10.5,4,10.5));p.moveTo(at.x,at.y,at.z,0,0);p.setYHeadRot(0);p.yRotO=p.xRotO=0;p.setNoGravity(true);p.setDeltaMovement(Vec3.ZERO);p.getAbilities().instabuild=false;return p;}
        /** Real native PlayerList admission and official mock connection; no socket or fabricated teleport override. */
        ServerPlayer connectedPlayer(){var level=h.getLevel();var server=level.getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"ACVectorProbe"),false);var p=new ServerPlayer(server,level,cookie.gameProfile(),cookie.clientInformation());players.add(p);var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);channels.add(channel);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);h.assertTrue(server.getPlayerList().getPlayer(p.getUUID())==p,"Owned native PlayerList fixture admitted");Vec3 at=h.absoluteVec(new Vec3(10.5,4,10.5));p.moveTo(at.x,at.y,at.z,0,0);p.setYHeadRot(0);p.yRotO=p.xRotO=0;p.setNoGravity(true);p.setDeltaMovement(Vec3.ZERO);p.setGameMode(GameType.SURVIVAL);return p;}
        void floor(ServerPlayer p){h.getLevel().setBlock(BlockPos.containing(p.position().add(0,-1,0)),Blocks.STONE.defaultBlockState(),3);}
        <T extends Entity> T entity(T e){entities.add(e);h.getLevel().addFreshEntity(e);return e;}
        <T extends Entity> T entity(T e,Vec3 at){e.setPos(at);e.setNoGravity(true);e.setDeltaMovement(Vec3.ZERO);return entity(e);}
        Villager mob(Vec3 at){var v=new Villager(EntityType.VILLAGER,h.getLevel());v.setNoAi(true);return entity(v,at);}
        void hook(Object o){hooks.add(o);NeoForge.EVENT_BUS.register(o);}
        void ticks(int n,IntConsumer callback){for(int i=1;i<=n;i++){int t=i;h.runAtTickTime(t,()->{if(!closed)callback.accept(t);});}}
        void cleanup(){if(closed)return;closed=true;for(var o:hooks)NeoForge.EVENT_BUS.unregister(o);for(var p:players){VecAccel.remove(p);VecDeviation.remove(p);DevelopmentController.remove(p);MachineDeveloperSessions.close(p);AbilityStorage.remove(p);var list=h.getLevel().getServer().getPlayerList();if(list.getPlayer(p.getUUID())==p)list.remove(p);else p.discard();}for(var e:entities)e.discard();for(var channel:channels)channel.finishAndReleaseAll();}
    }
    public static final class CancelDamage {final ServerPlayer victim;CancelDamage(ServerPlayer p){victim=p;}@SubscribeEvent(priority=EventPriority.HIGHEST)public void protect(LivingIncomingDamageEvent e){if(e.getEntity()==victim)e.setCanceled(true);}}
    public static final class CancelExplosion {boolean seen;@SubscribeEvent public void protect(ExplosionEvent.Start e){seen=true;e.setCanceled(true);}}
}

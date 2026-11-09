package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.core.*;
import cn.academy.port.skill.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Native common-world fixtures. Test setup grants only named skills; genuine zero-mastery
 * development/progression is covered separately. FakePlayer discards network payloads, so these
 * do not prove sockets, movement on an actual client, sounds, visual parity or full survival. */
@GameTestHolder("academy") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="academy")
public final class AcademyMagneticRuntimeTests {
    private static final String TEMPLATE="magnetic_runtime_empty",BATCH="academy_magnetic";
    @SubscribeEvent public static void installTemplate(net.neoforged.neoforge.event.level.LevelEvent.Load event){
        if(!GameTestHooks.isGametestEnabled()||!(event.getLevel() instanceof ServerLevel level))return;
        var tag=new CompoundTag();var size=new ListTag();for(int n:new int[]{40,12,40})size.add(IntTag.valueOf(n));tag.put("size",size);var blocks=new ListTag();var air=new CompoundTag();var pos=new ListTag();for(int i=0;i<3;i++)pos.add(IntTag.valueOf(0));air.put("pos",pos);air.putInt("state",0);blocks.add(air);tag.put("blocks",blocks);tag.put("entities",new ListTag());var palette=new ListTag();var state=new CompoundTag();state.putString("Name","minecraft:air");palette.add(state);tag.put("palette",palette);level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy",TEMPLATE)).load(level.registryAccess().lookupOrThrow(Registries.BLOCK),tag);
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12) public static void magnetic_movement_valid_target_pays_start_strain_and_exact_one_tick(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=f.ready(p,"mag_movement",0);f.target(Blocks.IRON_BLOCK,8);
        h.assertTrue(MagMovement.start(p),"metal target starts");h.assertTrue(MagMovement.active(p),"held context exists");close(h,s.cp,2800,"start zeroCP");close(h,s.overload,60,"start60strain");h.assertFalse(MagMovement.start(p),"duplicate start refused");MagMovement.tick(p);close(h,s.cp,2785,"one tick15CP");double z=p.getDeltaMovement().z;h.assertTrue(z>0&&z<=.08,"source acceleration capped.08 per-axis");MagMovement.tick(p);close(h,s.cp,2785,"same-world-tick replay refused");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12) public static void magnetic_movement_initial_invalid_target_retains_source_floor_and_strain(GameTestHelper h){var f=new Fixture(h);var p=f.player();var s=f.ready(p,"mag_movement",0);f.target(Blocks.STONE,8);h.assertTrue(MagMovement.start(p),"source initial termination still accepted");h.assertFalse(MagMovement.active(p),"nonmetal target ends");close(h,s.overload,60,"source initial strain precedes target validation");close(h,s.exp("mag_movement"),.005f,"source termination floorEXP");close(h,s.cp,2800,"no tickCP");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12) public static void magnetic_movement_weak_target_is_source_redundant_mastery_gate(GameTestHelper h){var f=new Fixture(h);var p=f.player();f.ready(p,"mag_movement",0);f.target(Blocks.IRON_ORE,8);h.assertTrue(MagMovement.start(p)&&MagMovement.active(p),"weak metal remains accepted atnovice per source isMetalBlock semantics");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12) public static void magnetic_movement_release_resets_fall_and_awards_distance_once(GameTestHelper h){var f=new Fixture(h);var p=f.player();var s=f.ready(p,"mag_movement",0);f.target(Blocks.IRON_BLOCK,8);MagMovement.start(p);p.setPos(p.position().add(0,0,10));p.fallDistance=7;h.assertTrue(MagMovement.release(p),"release accepted");close(h,s.exp("mag_movement"),.0011f*10,"distance-earnedEXP");close(h,p.fallDistance,0,"fall reset");h.assertFalse(MagMovement.release(p),"duplicate release no secondEXP");close(h,s.exp("mag_movement"),.0011f*10,"EXP remains");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12) public static void magnetic_movement_real_world_ticks_keep_strain_and_resource_stop(GameTestHelper h){var f=new Fixture(h);var p=f.player();var s=f.ready(p,"mag_movement",0);f.target(Blocks.IRON_BLOCK,8);MagMovement.start(p);s.cp=30;f.worldTicks(elapsed->{s.overload=0;MagMovement.tick(p);if(elapsed<=2){close(h,s.overload,60,"held strain floor");h.assertTrue(MagMovement.active(p),"paid tick remains active");}if(elapsed==3){h.assertFalse(MagMovement.active(p),"thirdtickCP failure terminates");close(h,s.cp,0,"paid two tick finiteCP");h.succeed();}});}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12) public static void magnetic_manipulation_item_prioritizes_held_metal_and_debits_once(GameTestHelper h){var f=new Fixture(h);var p=f.player();var s=f.ready(p,"mag_manip",0);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_BLOCK,3));f.target(Blocks.GOLD_BLOCK,7);h.assertTrue(MagManip.start(p),"heldblock starts");var block=f.track(MagManip.block(p));h.assertTrue(block.carriedState().is(Blocks.IRON_BLOCK),"held accepted block has priority");h.assertValueEqual(p.getMainHandItem().getCount(),2,"onehelditem removed");h.assertFalse(MagManip.start(p),"duplicate doesnotremove seconditem");close(h,s.cp,2800,"startfreeCP");close(h,s.overload,0,"startfreeoverload");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12) public static void magnetic_manipulation_terrain_selector_skips_nonmetal_and_preserves_block(GameTestHelper h){var f=new Fixture(h);var p=f.player();f.ready(p,"mag_manip",0);f.target(Blocks.STONE,5);f.target(Blocks.IRON_BLOCK,8);h.assertTrue(MagManip.start(p),"source filtered metalray passesnonmetal");var block=f.track(MagManip.block(p));h.assertTrue(block.carriedState().is(Blocks.IRON_BLOCK),"actualironblock transported");h.assertBlockPresent(Blocks.STONE,new BlockPos(3,2,5));h.assertBlockNotPresent(Blocks.IRON_BLOCK,new BlockPos(3,2,8));MagManip.abort(p);h.assertFalse(block.hovering(),"abort transitions physical blockto gravity");h.assertFalse(MagManip.active(p),"holdremoved");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12) public static void magnetic_manipulation_container_data_cannot_duplicate_or_disappear(GameTestHelper h){var f=new Fixture(h);var p=f.player();f.ready(p,"mag_manip",0);var pos=f.target(Blocks.HOPPER,6);var hopper=(HopperBlockEntity)h.getLevel().getBlockEntity(pos);hopper.setItem(0,new ItemStack(Items.DIAMOND,4));h.assertTrue(MagManip.start(p),"sourceweakmetalhopper accepted");var block=f.track(MagManip.block(p));h.assertTrue(h.getLevel().getBlockEntity(pos)==null,"sourcecontainer extracted");var saved=new CompoundTag();block.saveWithoutId(saved);h.assertTrue(saved.getCompound("block_entity").contains("Items"),"fullcontainer contents preserved inentity");var copy=f.track(new MagneticBlockEntity(ElectromasterEntities.MAGNETIC_BLOCK.get(),h.getLevel()));copy.load(saved);h.assertTrue(copy.carriedState().is(Blocks.HOPPER)&&!copy.hovering(),"cold reloadsettles realstate without resurrectinghold");h.assertTrue(saved.getCompound("block_state").getString("Name").equals("minecraft:hopper"),"diskstate usesregistryname instead ofunstable numericID");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12) public static void magnetic_manipulation_release_captures_cost_velocity_and_cooldown(GameTestHelper h){var f=new Fixture(h);var p=f.player();var s=f.ready(p,"mag_manip",0);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_BLOCK));MagManip.start(p);var block=f.track(MagManip.block(p));s.experience.put("mag_manip",1d);h.assertTrue(MagManip.release(p),"nearbyrelease succeeds");close(h,s.cp,2660,"capturednovice140CP");close(h,s.overload,35,"capturednovice35strain");close(h,block.getDeltaMovement().length(),.5,"capturednovice .5 throw speed");h.assertValueEqual(s.cooldowns.get("mag_manip"),60,"capturedcooldown60");h.assertFalse(MagManip.release(p),"release replay discarded");h.assertFalse(block.hovering(),"physicalentity released");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12) public static void magnetic_manipulation_exact_five_block_release_spends_nothing(GameTestHelper h){var f=new Fixture(h);var p=f.player();var s=f.ready(p,"mag_manip",0);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_BLOCK));MagManip.start(p);var block=f.track(MagManip.block(p));block.setPos(p.position().add(0,0,5));h.assertFalse(MagManip.release(p),"source strictsquared25 reject");close(h,s.cp,2800,"badreleasefreeCP");close(h,s.exp("mag_manip"),0,"badreleasefreeEXP");h.assertFalse(block.hovering(),"evenfailedrelease enablesplacement/gravity");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12) public static void magnetic_manipulation_source_rigidbody_advances_old_plus_new_motion(GameTestHelper h){var f=new Fixture(h);var p=f.player();f.ready(p,"mag_manip",0);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_BLOCK));MagManip.start(p);var block=f.track(MagManip.block(p));MagManip.abort(p);block.setPos(f.position(20,7,20));block.setDeltaMovement(new Vec3(.5,.5,.5));Vec3 old=block.position();block.tick();close(h,block.getX()-old.x,1,"doublex displacement");close(h,block.getY()-old.y,.96,"old.5 plusnew .46Y");close(h,block.getZ()-old.z,1,"doublez displacement");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12) public static void magnetic_manipulation_break_hook_rejects_before_extracting(GameTestHelper h){var f=new Fixture(h);var p=f.player();f.ready(p,"mag_manip",0);var pos=f.target(Blocks.IRON_BLOCK,6);f.hook(new BreakHook(p,pos));h.assertFalse(MagManip.start(p),"protectedbreak rejected");h.assertBlockPresent(Blocks.IRON_BLOCK,new BlockPos(3,2,6));h.assertFalse(MagManip.active(p),"noentityorhold created");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12) public static void magnetic_new_active_presets_dispatch_and_logout_abort(GameTestHelper h){
        var f=new Fixture(h);
        try(var wire=new LegacySingleKeyNativeFixture(h)){
            var p=wire.player;Vec3 at=f.position(3.5,1,2.5);p.moveTo(at.x,at.y,at.z,0,0);
            var s=f.ready(p,"mag_movement",0);s.learn("mag_manip");
            s.presets.edit(0,0,"mag_movement",id->cn.academy.port.preset.PresetSkills.selectable(s,id));
            s.presets.edit(0,1,"mag_manip",id->cn.academy.port.preset.PresetSkills.selectable(s,id));
            f.target(Blocks.IRON_BLOCK,8);wire.assertPlainDenied(0,LegacySingleKeyProtocol.Skill.MAG_MOVEMENT);
            h.assertTrue(wire.press(0,LegacySingleKeyProtocol.Skill.MAG_MOVEMENT,1)&&MagMovement.active(p),"integratedpresetdispatch starts movement");
            var movement=wire.accepted(LegacySingleKeyProtocol.Skill.MAG_MOVEMENT,1);
            wire.assertPlainDenied(0,LegacySingleKeyProtocol.Skill.MAG_MOVEMENT);
            h.assertTrue(MagMovement.active(p),"plain magnetic movement terminal cannot dispose the accepted hold");
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_BLOCK));
            wire.assertPlainDenied(1,LegacySingleKeyProtocol.Skill.MAG_MANIP);
            h.assertTrue(wire.press(1,LegacySingleKeyProtocol.Skill.MAG_MANIP,1)&&MagManip.active(p),"integrated secondslot startsmanipulation");
            var manipulation=wire.accepted(LegacySingleKeyProtocol.Skill.MAG_MANIP,1);
            wire.assertPlainDenied(1,LegacySingleKeyProtocol.Skill.MAG_MANIP);
            h.assertTrue(MagManip.active(p),"plain magnetic manipulation terminal cannot dispose the accepted hold");
            f.track(MagManip.block(p));NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p));
            h.assertFalse(MagManip.active(p),"integratedlogout abortsmanipulation");h.assertFalse(MagMovement.active(p),"movement actuallycleared");
            h.assertFalse(wire.terminal(LegacySingleKeyProtocol.RELEASE,movement),"late accepted magnetic movement release after logout is inert");
            h.assertFalse(wire.terminal(LegacySingleKeyProtocol.ABORT,manipulation),"late accepted magnetic manipulation abort after logout is inert");
            h.succeed();
        }
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12) public static void magnetic_manipulation_container_collision_replaces_actual_hopper_with_contents(GameTestHelper h){var f=new Fixture(h);var p=f.player();f.ready(p,"mag_manip",0);var source=f.target(Blocks.HOPPER,6);((HopperBlockEntity)h.getLevel().getBlockEntity(source)).setItem(0,new ItemStack(Items.DIAMOND,4));MagManip.start(p);var block=f.track(MagManip.block(p));MagManip.abort(p);h.setBlock(new BlockPos(20,3,20),Blocks.STONE);block.setPos(f.position(20.5,4.2,20.5));block.setDeltaMovement(new Vec3(0,-.5,0));block.tick();h.assertTrue(block.isRemoved(),"collision consumes onephysicalentity");h.assertBlockPresent(Blocks.HOPPER,new BlockPos(20,4,20));var moved=(HopperBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(20,4,20)));h.assertValueEqual(moved.getItem(0).getCount(),4,"allfourdiamonds preserved atactualdestination");h.assertBlockNotPresent(Blocks.HOPPER,new BlockPos(3,2,6));h.succeed();}
    private static void close(GameTestHelper h,double actual,double expected,String label){h.assertTrue(Math.abs(actual-expected)<1e-5,label+": expected "+expected+", got "+actual);}
    private static final class Fixture {
        final GameTestHelper helper;final List<ServerPlayer> players=new ArrayList<>();final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final long started;long last;boolean closed;
        Fixture(GameTestHelper h){helper=h;started=last=h.getLevel().getGameTime();h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){cleanup();}public void testFailed(GameTestInfo i,GameTestRunner r){cleanup();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){cleanup();}});}
        FakePlayer player(){var p=new FakePlayer(helper.getLevel(),new GameProfile(UUID.randomUUID(),"[AC-Magnetic]"));Vec3 at=position(3.5,1,2.5);p.moveTo(at.x,at.y,at.z,0,0);p.setYHeadRot(0);p.setNoGravity(true);p.getAbilities().instabuild=false;players.add(p);return p;}
        AbilityProgress ready(ServerPlayer p,String skill,double mastery){var s=AbilityStorage.get(p);s.selectCategory("electromaster");s.setLevel(2);s.activated=true;s.experience.put(skill,mastery);return s;}
        Vec3 position(double x,double y,double z){var origin=helper.absolutePos(BlockPos.ZERO);return new Vec3(origin.getX()+x,origin.getY()+y,origin.getZ()+z);}
        BlockPos target(Block b,int z){var relative=new BlockPos(3,2,z);helper.setBlock(relative,b);return helper.absolutePos(relative);}
        <T extends Entity>T track(T e){entities.add(e);return e;}void hook(Object h){NeoForge.EVENT_BUS.register(h);hooks.add(h);}
        void worldTicks(java.util.function.LongConsumer callback){helper.onEachTick(()->{long now=helper.getLevel().getGameTime();if(closed||helper.testInfo.isDone()||now<=last)return;last=now;callback.accept(now-started);});}
        void cleanup(){if(closed)return;closed=true;for(var p:players){MagMovement.abort(p);MagManip.abort(p);AbilityStorage.remove(p);}for(var e:entities)e.discard();for(var o:hooks)NeoForge.EVENT_BUS.unregister(o);}
    }
    public static final class BreakHook {final ServerPlayer p;final BlockPos pos;BreakHook(ServerPlayer p,BlockPos pos){this.p=p;this.pos=pos;}@SubscribeEvent public void breaking(BlockEvent.BreakEvent e){if(e.getPlayer()==p&&e.getPos().equals(pos))e.setCanceled(true);}}
}

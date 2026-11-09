package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.preset.PresetSkills;
import cn.academy.port.skill.ClassicMineOreAdapter;
import cn.academy.port.skill.ClassicMineScan;
import cn.academy.port.skill.MineDetect;
import cn.academy.port.skill.MineDetectRules;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Native world/debuff/adapter/authentication fixtures. Compiled only by the isolated worker. */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class AcademyMineDetectRuntimeTests {
    private static final String TEMPLATE="mine_detect_runtime_empty",BATCH="academy_mine_detect",ID="mine_detect";
    private AcademyMineDetectRuntimeTests() {}
    @SubscribeEvent public static void template(LevelEvent.Load event) {
        if(!GameTestHooks.isGametestEnabled()||!(event.getLevel() instanceof ServerLevel level))return;
        var tag=new CompoundTag();var size=new ListTag();size.add(IntTag.valueOf(48));size.add(IntTag.valueOf(12));size.add(IntTag.valueOf(48));tag.put("size",size);
        var blocks=new ListTag();var block=new CompoundTag();var pos=new ListTag();for(int i=0;i<3;i++)pos.add(IntTag.valueOf(0));block.put("pos",pos);block.putInt("state",0);blocks.add(block);tag.put("blocks",blocks);
        tag.put("entities",new ListTag());var palette=new ListTag();var air=new CompoundTag();air.putString("Name","minecraft:air");palette.add(air);tag.put("palette",palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy",TEMPLATE)).load(level.registryAccess().lookupOrThrow(Registries.BLOCK),tag);
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void mine_detect_native_blindness_postaward_cooldown_and_replay(GameTestHelper h) {
        var f=new Fixture(h);try {
            var p=f.player();var s=ready(p,0,3);double cp=s.cp;
            h.assertTrue(MineDetect.perform(p),"native novice cast accepted");
            var blindness=p.getEffect(MobEffects.BLINDNESS);h.assertTrue(blindness!=null,"real blindness installed");
            h.assertValueEqual(blindness.getDuration(),100,"source100t blindness");h.assertValueEqual(blindness.getAmplifier(),0,"source BlindnessI");
            exact(h,s.cp,cp-1500,"source float CP1500");exact(h,s.overload,200,"source overload200");exact(h,s.exp(ID),(double).008F,"source Float EXP");
            h.assertValueEqual(s.cooldowns.get(ID),896,"cooldown reads post-award EXP, not900");
            h.assertFalse(MineDetect.perform(p),"cooldown replay rejects");exact(h,s.cp,cp-1500,"replay cannot repay");
            h.succeed();
        }finally{f.cleanup();}
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void mine_detect_preaward_advanced_snapshot_and_local_packet_shape(GameTestHelper h) {
        var f=new Fixture(h);try {
            for(int i=0;i<3;i++){
                float old=i==0?.5F:Math.nextUp(.5F);int level=i==2?3:4;var p=f.player();var s=ready(p,old,level);
                var plan=MineDetectRules.prepare(s,false);h.assertTrue(plan!=null,"threshold cast prepared");
                var packet=MineDetect.effectData(p,plan);h.assertValueEqual(packet.getInt("entity"),p.getId(),"effect target is caster");
                h.assertTrue(packet.getBoolean("advanced")==(i==1),"strict Float>.5 and level>=4 capture");
                h.assertTrue(packet.getFloat("range")==15F+old*15F,"source float captured range");
                h.assertValueEqual(packet.getAllKeys().size(),4,"only kind/caster/range/advanced, no server ore positions");
                MineDetectRules.complete(s,plan);h.assertTrue(s.exp(ID)>.5,"award crosses boundary while captured packet remains unchanged");
            }h.succeed();
        }finally{f.cleanup();}
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void mine_detect_authenticated_slot_and_source_learning_dependencies(GameTestHelper h) {
        var f=new Fixture(h);try {
            var p=f.player();var s=ready(p,0,3);s.experience.remove(ID);
            var skill=SkillCatalog.find("electromaster",ID).orElseThrow();h.assertFalse(SkillCatalog.canLearn(s,skill),"source mine_detect requires magnetic manipulation mastery1");
            s.learn("mag_manip");s.experience.put("mag_manip",(double)Math.nextDown(1F));h.assertFalse(SkillCatalog.canLearn(s,skill),"dependency below1 rejects");
            s.experience.put("mag_manip",1.0);h.assertTrue(SkillCatalog.canLearn(s,skill),"source level3 parent mastery1 permits learning");s.learn(ID);
            h.assertTrue(PresetSkills.selectable(s,ID),"learned native skill selectable");h.assertTrue(s.presets.edit(0,0,ID,id->PresetSkills.selectable(s,id)),"explicit preset binding accepted");
            double cp=s.cp;h.assertFalse(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("cast",ID)),"raw skill-name wire spoof rejected");exact(h,s.cp,cp,"spoof consumes nothing");
            h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_press","0")),"authenticated owned current slot dispatches skill");
            exact(h,s.cp,cp-1500,"slot cast pays once");h.assertTrue(p.hasEffect(MobEffects.BLINDNESS),"slot dispatcher reaches actual blindness adapter");
            h.assertFalse(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_press","0")),"slot replay rejected by authoritative cooldown");h.succeed();
        }finally{f.cleanup();}
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void mine_detect_native_classes_tags_harvest_and_through_wall_scan(GameTestHelper h) {
        var f=new Fixture(h);try {
            for(Block b:new Block[]{Blocks.COAL_ORE,Blocks.IRON_ORE,Blocks.GOLD_ORE,Blocks.COPPER_ORE,Blocks.REDSTONE_ORE,Blocks.DIAMOND_ORE,Blocks.NETHER_QUARTZ_ORE,Blocks.DEEPSLATE_IRON_ORE})
                h.assertTrue(ClassicMineOreAdapter.isOre(b.defaultBlockState()),"native old ore-class/counterpart/tag accepted "+b);
            for(Block b:new Block[]{Blocks.STONE,Blocks.DEEPSLATE,Blocks.DIRT,Blocks.IRON_BLOCK,Blocks.SCULK})h.assertFalse(ClassicMineOreAdapter.isOre(b.defaultBlockState()),"worldgen/material tags never turn non-ores into OreDictionary ores "+b);
            h.assertValueEqual(ClassicMineOreAdapter.harvestLevel(Blocks.COAL_ORE.defaultBlockState()),0,"coal source wood-tier0");
            h.assertValueEqual(ClassicMineOreAdapter.harvestLevel(Blocks.IRON_ORE.defaultBlockState()),1,"iron source stone-tier1");
            h.assertValueEqual(ClassicMineOreAdapter.harvestLevel(Blocks.DIAMOND_ORE.defaultBlockState()),2,"diamond source iron-tier2");
            var ore=new BlockPos(26,4,24);h.setBlock(ore,Blocks.DIAMOND_ORE);h.setBlock(new BlockPos(25,4,24),Blocks.STONE);
            var center=h.absoluteVec(new Vec3(24,4,24));var scan=ClassicMineScan.scan(center.x,center.y,center.z,15,true,ClassicMineOreAdapter.access(h.getLevel()));
            var absolute=h.absolutePos(ore);h.assertTrue(scan.stream().anyMatch(e->e.x()==absolute.getX()&&e.y()==absolute.getY()&&e.z()==absolute.getZ()&&e.level()==3),"wall cannot obstruct ore discovery, correct green advanced color");
            h.setBlock(ore,Blocks.AIR);h.assertTrue(scan.stream().anyMatch(e->e.x()==absolute.getX()&&e.y()==absolute.getY()&&e.z()==absolute.getZ()),"captured mine record persists after block mined");h.succeed();
        }finally{f.cleanup();}
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void mine_detect_reentry_and_insufficient_resources_are_single_commit(GameTestHelper h) {
        var f=new Fixture(h);try {
            var p=f.player();var s=ready(p,0,3);var hook=new Reentry(p);f.hook(hook);double cp=s.cp;
            h.assertTrue(MineDetect.perform(p),"outer cast accepted");h.assertFalse(hook.accepted,"blindness-event reentry blocked while cooldown not yet installed");
            h.assertValueEqual(hook.calls,1,"one real blindness application attempt");exact(h,s.cp,cp-1500,"only one CP payment");exact(h,s.exp(ID),(double).008F,"only one EXP award");
            var poor=f.player();var poorState=ready(poor,0,3);poorState.cp=1499;
            h.assertFalse(MineDetect.perform(poor),"insufficient CP failure");h.assertFalse(poor.hasEffect(MobEffects.BLINDNESS),"failed cast no blindness");exact(h,poorState.exp(ID),0,"failed cast no EXP");h.assertTrue(poorState.cooldowns.isEmpty(),"failed cast no cooldown");h.succeed();
        }finally{f.cleanup();}
    }
    private static AbilityProgress ready(ServerPlayer p,double e,int level){var s=AbilityStorage.get(p);s.selectCategory("electromaster");s.setLevel(level);s.learn(ID);s.experience.put(ID,e);s.activated=true;return s;}
    private static void exact(GameTestHelper h,double a,double e,String label){h.assertTrue(Double.doubleToLongBits(a)==Double.doubleToLongBits(e),label+": "+a+" != "+e);}
    private static final class Fixture {
        final GameTestHelper h;final List<ServerPlayer> players=new ArrayList<>();final List<Object> hooks=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){this.h=h;h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){cleanup();}public void testFailed(GameTestInfo i,GameTestRunner r){cleanup();}public void testAddedForRerun(GameTestInfo i,GameTestInfo r,GameTestRunner runner){cleanup();}});}
        ServerPlayer player(){var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[AC-MineTest]"));var v=h.absoluteVec(new Vec3(24,4,24));p.moveTo(v.x,v.y,v.z,0,0);p.setNoGravity(true);p.getAbilities().instabuild=false;players.add(p);return p;}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void cleanup(){if(closed)return;closed=true;for(var hook:hooks)NeoForge.EVENT_BUS.unregister(hook);for(var p:players){NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p));AbilityStorage.remove(p);p.discard();}}
    }
    public static final class Reentry {
        final ServerPlayer player;int calls;boolean accepted;Reentry(ServerPlayer p){player=p;}
        @SubscribeEvent public void onApplicable(MobEffectEvent.Applicable e){if(e.getEntity()==player&&e.getEffectInstance().getEffect().equals(MobEffects.BLINDNESS)){calls++;accepted|=MineDetect.perform(player);}}
    }
}

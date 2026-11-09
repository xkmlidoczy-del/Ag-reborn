package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.api.SkillBlockDestroyEvent;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.develop.*;
import cn.academy.port.machine.*;
import cn.academy.port.skill.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.IntConsumer;
import static cn.academy.port.skill.AdvancedMineRaySession.Tier;

/** Native fixtures compiled by the isolated worker ONLY; parent owns actual execution.
 * Gameplay fixtures declare target mastery; learning fixtures never grant either target skill. */
@GameTestHolder("academy") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="academy")
public final class AcademyAdvancedMineRayRuntimeTests {
    private static final String TEMPLATE="advanced_mining_empty";
    private AcademyAdvancedMineRayRuntimeTests(){}
    @SubscribeEvent public static void install(LevelEvent.Load event) {
        if(!GameTestHooks.isGametestEnabled()||!(event.getLevel() instanceof ServerLevel world))return;
        var tag=new CompoundTag();var size=new ListTag();for(int n:new int[]{64,8,64})size.add(IntTag.valueOf(n));tag.put("size",size);
        var blocks=new ListTag();var first=new CompoundTag();var pos=new ListTag();for(int n=0;n<3;n++)pos.add(IntTag.valueOf(0));first.put("pos",pos);first.putInt("state",0);blocks.add(first);tag.put("blocks",blocks);tag.put("entities",new ListTag());
        var palette=new ListTag();var air=new CompoundTag();air.putString("Name","minecraft:air");palette.add(air);tag.put("palette",palette);
        world.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy",TEMPLATE)).load(world.registryAccess().lookupOrThrow(Registries.BLOCK),tag);
    }
    @GameTest(template=TEMPLATE,batch="academy_advanced_mining",timeoutTicks=20)
    public static void expert_twenty_range_stone_drop_paid_ticks_and_captured_cooldown(GameTestHelper h) {
        var f=new Fixture(h);var p=f.player();var s=ready(p,Tier.EXPERT,0);var at=BlockPos.containing(p.getEyePosition().add(0,0,19));
        h.getLevel().setBlock(at,Blocks.STONE.defaultBlockState(),3);double cp=s.cp;
        h.assertTrue(MineRayExpert.start(p,1),"Expert starts");h.assertFalse(MineRayExpert.start(p,1),"duplicate nonce blocks");
        f.ticks(4,tick->{MineRayExpert.tick(p);int held=MineRayExpert.heldTicks(p);MineRayExpert.tick(p);h.assertValueEqual(MineRayExpert.heldTicks(p),held,"same native tick cannot double spend");
            if(tick==1)h.assertTrue(h.getLevel().getBlockState(at).is(Blocks.STONE),"acquisition at nineteen has no hardness subtraction");
            if(tick==3)h.assertTrue(h.getLevel().getBlockState(at).is(Blocks.STONE),"two novice .5 subtractions leave .5");
            if(tick==4){h.assertTrue(h.getLevel().isEmptyBlock(at),"third subtraction breaks stone at range19");near(h,s.cp,cp-100,"four paid CP25 ticks");near(h,s.exp(Tier.EXPERT.id),.0003F,"real block trains Expert");
                h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(at).inflate(2)).stream().anyMatch(e->e.getItem().is(Items.COBBLESTONE)),"real stone loot");s.experience.put(Tier.EXPERT.id,1D);MineRayExpert.release(p);h.assertValueEqual(s.cooldowns.get(Tier.EXPERT.id),60,"captured novice cooldown despite new mastery");h.assertFalse(MineRayExpert.release(p),"release replay denied");h.succeed();}
        });
    }
    @GameTest(template=TEMPLATE,batch="academy_advanced_mining",timeoutTicks=20)
    public static void luck_master_failed_CP_break_tick_still_drops_and_trains(GameTestHelper h) {
        var f=new Fixture(h);var p=f.player();var s=ready(p,Tier.LUCK,1);var at=BlockPos.containing(p.getEyePosition().add(0,0,3));h.getLevel().setBlock(at,Blocks.DIRT.defaultBlockState(),3);
        h.assertTrue(MineRayLuck.start(p),"Luck starts");f.ticks(2,tick->{if(tick==2)s.cp=0;MineRayLuck.tick(p);if(tick==2){h.assertTrue(h.getLevel().isEmptyBlock(at),"failed CP tick still breaks captured dirt");h.assertFalse(MineRayLuck.active(p),"failed CP retires");near(h,s.levelExperience,.0003F,"source failed-tick EXP despite capped mastery");h.assertValueEqual(s.cooldowns.get(Tier.LUCK.id),30,"master cooldown");h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(at).inflate(2)).stream().anyMatch(e->e.getItem().is(Items.DIRT)),"actual native dirt drop");h.succeed();}});
    }
    @GameTest(template=TEMPLATE,batch="academy_advanced_mining",timeoutTicks=20)
    public static void expert_full_tier_accepts_obsidian_then_source_hardness_replacement(GameTestHelper h) {
        var f=new Fixture(h);var p=f.player();ready(p,Tier.EXPERT,1);var at=BlockPos.containing(p.getEyePosition().add(0,0,3));h.getLevel().setBlock(at,Blocks.OBSIDIAN.defaultBlockState(),3);MineRayExpert.start(p);
        f.ticks(4,tick->{if(tick==2)h.getLevel().setBlock(at,Blocks.DIRT.defaultBlockState(),3);if(tick==3){MineRayExpert.remove(p);var s=ready(p,Tier.EXPERT,1);h.getLevel().setBlock(at,Blocks.DIRT.defaultBlockState(),3);h.assertTrue(MineRayExpert.start(p),"new dirt context");}if(tick==4)h.getLevel().setBlock(at,Blocks.BEDROCK.defaultBlockState(),3);MineRayExpert.tick(p);
            if(tick==1)h.assertTrue(MineRayExpert.target(p).equals(new AdvancedMineRaySession.Cell(at.getX(),at.getY(),at.getZ())),"advanced harvest5 accepts vanilla tier3 obsidian");
            if(tick==2)h.assertTrue(h.getLevel().getBlockState(at).is(Blocks.DIRT),"original obsidian50 hardness remains captured after replacement");
            if(tick==4){h.assertTrue(h.getLevel().isEmptyBlock(at),"source captured dirt hardness breaks later bedrock without rechecking hardness");h.succeed();}
        });
    }
    @GameTest(template=TEMPLATE,batch="academy_advanced_mining",timeoutTicks=20)
    public static void source_and_native_protection_reject_each_variant_and_keep_bedrock(GameTestHelper h) {
        var f=new Fixture(h);var p=f.player();ready(p,Tier.EXPERT,0);var at=BlockPos.containing(p.getEyePosition().add(0,0,3));var hook=new Protection(p,at);f.hook(hook);h.getLevel().setBlock(at,Blocks.STONE.defaultBlockState(),3);hook.source=true;MineRayExpert.start(p);
        f.ticks(12,tick->{int phase=(tick-1)%6+1;boolean luck=tick>6;
            if(tick==7){MineRayExpert.remove(p);ready(p,Tier.LUCK,0);hook.source=true;h.getLevel().setBlock(at,Blocks.STONE.defaultBlockState(),3);MineRayLuck.start(p);}
            if(phase==3){hook.source=false;hook.nativeBlock=true;}if(phase==5){hook.nativeBlock=false;h.getLevel().setBlock(at,Blocks.BEDROCK.defaultBlockState(),3);}
            if(luck)MineRayLuck.tick(p);else MineRayExpert.tick(p);
            if(phase<=4)h.assertTrue((luck?MineRayLuck.target(p):MineRayExpert.target(p)).equals(AdvancedMineRaySession.Cell.NONE),"both source/native canceled acquisition repeatedly reject");
            if(tick==12){h.assertTrue(h.getLevel().getBlockState(at).is(Blocks.BEDROCK),"both negative hardness mapped FloatMAX");h.assertTrue(hook.sourceProbes>=10&&hook.nativeProbes>=4,"actual source and native protection hooks visited for both variants");h.succeed();}
        });
    }
    @GameTest(template=TEMPLATE,batch="academy_advanced_mining",timeoutTicks=20)
    public static void distinct_fortune_tools_and_seeded_native_diamond_loot(GameTestHelper h)throws Exception {
        var f=new Fixture(h);var p=f.player();var type=Class.forName("cn.academy.port.skill.MeltdownerBeamSupport");var method=type.getDeclaredMethod("advancedMineTool",ServerPlayer.class,int.class);method.setAccessible(true);
        var expert=(ItemStack)method.invoke(null,p,0);var luck=(ItemStack)method.invoke(null,p,3);var registry=p.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);var fortune=registry.getOrThrow(Enchantments.FORTUNE);
        h.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(fortune,expert),0,"Expert actual synthetic loot tool Fortune0");h.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(fortune,luck),3,"Luck actual synthetic loot tool Fortune3");
        var state=Blocks.DIAMOND_ORE.defaultBlockState();var table=h.getLevel().getServer().reloadableRegistries().getLootTable(state.getBlock().getLootTable());int novice=0,fortunate=0;
        for(long seed=1;seed<=64;seed++)for(var tool:List.of(expert,luck)) {
            var params=new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.BLOCK_STATE,state).withParameter(LootContextParams.ORIGIN,p.getEyePosition()).withParameter(LootContextParams.TOOL,tool).create(LootContextParamSets.BLOCK);
            int count=table.getRandomItems(params,seed).stream().filter(drop->drop.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum();if(tool==expert)novice+=count;else fortunate+=count;
        }
        h.assertValueEqual(novice,64,"native Fortune0 diamond yield");h.assertTrue(fortunate>novice,"deterministically seeded Fortune3 native loot produces added diamonds");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="academy_advanced_mining",timeoutTicks=20)
    public static void final_native_break_guard_keeps_blocks_with_original_unconditional_EXP(GameTestHelper h) {
        var f=new Fixture(h);var p=f.player();var s=ready(p,Tier.LUCK,1);var at=BlockPos.containing(p.getEyePosition().add(0,0,3));var hook=new Protection(p,at);f.hook(hook);h.getLevel().setBlock(at,Blocks.DIRT.defaultBlockState(),3);MineRayLuck.start(p);
        f.ticks(2,tick->{if(tick==2)hook.nativeBlock=true;MineRayLuck.tick(p);if(tick==2){h.assertTrue(h.getLevel().getBlockState(at).is(Blocks.DIRT),"final modern native guard prevents removal after allowed acquisition");h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(at).inflate(2)).isEmpty(),"canceled final break emits no duplicate loot");near(h,s.levelExperience,.0003F,"source MRContext grants XP unconditionally after its break callback");h.succeed();}});
    }
    @GameTest(template=TEMPLATE,batch="academy_advanced_mining",timeoutTicks=20)
    public static void authenticated_nonce_ingress_cross_preset_abort_and_lifecycle_disposal(GameTestHelper h) {
        var f=new Fixture(h);var p=f.player();var s=ready(p,Tier.LUCK,0);s.extraOverload=400; /* declared finite, within classic Lv5 training cap500 */ s.learn(Tier.EXPERT.id);h.assertTrue(bind(s,0,Tier.EXPERT.id)&&bind(s,1,Tier.LUCK.id),"authentic selectable bindings");
        h.assertFalse(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_press_token","0:01")),"noncanonical nonce rejected");
        h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_press_token","0:1"))&&MineRayExpert.active(p),"genuine ingress Expert");
        h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_press_token","1:1"))&&MineRayLuck.active(p),"Luck independent nonce/context");
        // MRContext key-abort is per delegate; two active keys require two scoped callbacks.
        h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_abort","0")),"Expert key-abort authenticated before edit");
        h.assertFalse(MineRayExpert.active(p),"Expert callback ends only its own context");
        h.assertTrue(MineRayLuck.active(p),"Luck context survives independent Expert key-abort");
        h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_abort","1")),"Luck key-abort authenticated before edit");
        h.assertFalse(MineRayLuck.active(p),"Luck callback ends its context before edit");
        h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("preset_edit","1:0:mine_ray_luck")),"unrelated preset edit accepted");h.assertFalse(MineRayExpert.active(p)||MineRayLuck.active(p),"mapping edit preserves both per-key callback terminations");
        h.assertFalse(MineRayExpert.start(p,1)||MineRayLuck.start(p,1),"retired physical press tokens do not restart");
        s.cooldowns.clear();s.overload=0;s.overloadFine=true;h.assertTrue(MineRayExpert.start(p,2),"new press after cooldown");MineRayExpert.remove(p);h.assertTrue(s.cooldowns.isEmpty(),"lifecycle discard does not invent cooldown");
        float previous=p.getYHeadRot();p.setYHeadRot(Float.NaN);try{h.assertFalse(MineRayExpert.start(p)||MineRayLuck.start(p),"actual NaN native head pose fails closed");}finally{p.setYHeadRot(previous);}h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="academy_advanced_mining_learning",timeoutTicks=30)
    public static void physical_Advanced_session_genuinely_learns_both_targets_with_finite_IF(GameTestHelper h) {
        var f=new Fixture(h);var p=f.player();var item=MachineDevelopers.ADVANCED_ITEM.get();var stack=new ItemStack(item);p.setItemInHand(InteractionHand.MAIN_HAND,stack);var base=h.absolutePos(new BlockPos(5,1,8));
        h.assertTrue(item.place(new BlockPlaceContext(p,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(base),Direction.UP,base,false))).consumesAction(),"physical Advanced multiblock placement");
        var machine=(MachineDeveloperBlockEntity)h.getLevel().getBlockEntity(base);p.moveTo(base.getX()+.5,base.getY(),base.getZ()-1.5,0,0);p.setYHeadRot(0);
        for(Tier tier:Tier.values()) {
            DevelopmentController.remove(p);var s=AbilityStorage.get(p);s.selectCategory("meltdowner");s.setLevel(tier.level);for(var req:SkillCatalog.find("meltdowner",tier.id).orElseThrow().requirements()){s.learn(req.id());s.experience.put(req.id(),req.exp());}
            h.assertFalse(s.learned(tier.id),"target is never fixture-granted");machine.battery().load(200000);h.assertTrue(machine.use(p),"actual senderbound Advanced GUI session");var token=MachineDeveloperSessions.activeToken(p).orElseThrow().toString();
            h.assertFalse(MachineDeveloperSessions.request(p,new AcademyNetwork.Request("machine_learn",UUID.randomUUID()+":"+tier.id)),"forged machine token fails");h.assertTrue(MachineDeveloperSessions.request(p,new AcademyNetwork.Request("machine_learn",token+":"+tier.id)),"common registry permits actual learning");int ticks=tier==Tier.EXPERT?176:240;
            for(int tick=1;tick<=ticks;tick++){DevelopmentController.tick(p,snapshot->{});if(tick<ticks)h.assertFalse(s.learned(tier.id),"source strict stimulation boundary no early learning");}
            h.assertTrue(s.learned(tier.id)&&s.exp(tier.id)==0&&DevelopmentController.process(p).state()==DevelopmentProcess.State.DONE,"genuine source developer process completes target");near(h,machine.battery().getEnergy(),200000-ticks*40,"finite source7040/9600 IF debit");h.assertTrue(s.presets.currentSkill(0).isEmpty(),"no automatic binding");s.activated=true;h.assertTrue(bind(s,0,tier.id),"naturally learned zero mastery binds");
        }
        h.succeed();
    }
    private static boolean bind(AbilityProgress s,int slot,String id){return s.presets.edit(0,slot,id,value->cn.academy.port.preset.PresetSkills.selectable(s,value));}
    private static AbilityProgress ready(ServerPlayer p,Tier tier,double exp){var s=AbilityStorage.get(p);s.selectCategory("meltdowner");s.setLevel(tier.level);s.learn(tier.id);s.experience.put(tier.id,exp);s.activated=true;return s;}
    private static void near(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<1E-6,why+" actual="+actual+" expected="+expected);}
    private static final class Fixture {
        final GameTestHelper h;final AABB bounds;final Set<UUID> initialDrops=new HashSet<>();final List<ServerPlayer> players=new ArrayList<>();final List<Object> hooks=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){this.h=h;bounds=h.getBounds();for(var drop:h.getLevel().getEntitiesOfClass(ItemEntity.class,bounds))initialDrops.add(drop.getUUID());h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){cleanup();}public void testFailed(GameTestInfo i,GameTestRunner r){cleanup();}public void testAddedForRerun(GameTestInfo old,GameTestInfo next,GameTestRunner r){cleanup();}});}
        FakePlayer player(){var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[AC-Adv-Mine]"));players.add(p);var at=h.absoluteVec(new Vec3(5.5,1,5.5));p.moveTo(at.x,at.y,at.z,0,0);p.setYHeadRot(0);p.setNoGravity(true);p.getAbilities().instabuild=false;return p;}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void ticks(int count,IntConsumer body){for(int tick=1;tick<=count;tick++){int now=tick;h.runAtTickTime(tick,()->{if(!closed)body.accept(now);});}}
        void cleanup(){if(closed)return;closed=true;for(var hook:hooks)NeoForge.EVENT_BUS.unregister(hook);for(var p:players){MineRayExpert.remove(p);MineRayLuck.remove(p);DevelopmentController.remove(p);AbilityStorage.remove(p);p.discard();}for(var drop:h.getLevel().getEntitiesOfClass(ItemEntity.class,bounds))if(!initialDrops.contains(drop.getUUID()))drop.discard();}
    }
    public static final class Protection {
        final ServerPlayer owner;final BlockPos at;boolean source,nativeBlock;int sourceProbes,nativeProbes;
        Protection(ServerPlayer p,BlockPos at){owner=p;this.at=at;}
        @SubscribeEvent public void source(SkillBlockDestroyEvent e){if(e.player==owner){sourceProbes++;if(source)e.setCanceled(true);}}
        @SubscribeEvent public void nativeBreak(BlockEvent.BreakEvent e){if(e.getPlayer()==owner&&e.getPos().equals(at)){nativeProbes++;if(nativeBlock)e.setCanceled(true);}}
    }
}

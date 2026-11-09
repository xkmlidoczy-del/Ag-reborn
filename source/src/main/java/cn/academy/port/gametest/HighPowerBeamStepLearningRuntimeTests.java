package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.develop.*;
import cn.academy.port.machine.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CombatAmounts;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW232 actual Normal-machine learning, finite payment, canonical saves and no resource refill.
 * Explicit fixtures use real paid prerequisite development, injury, shield debit and accelerated developer ticks. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerBeamStepLearningRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="highpower_beam_step_learning";
    private HighPowerBeamStepLearningRuntimeTests(){}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void genuine_lv3_thermal_parent_and_147_normal_ticks_preserve_shared_ledgers(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");s.setLevel(2);
        var skill=SkillCatalog.find("meltdowner",HighPowerSkills.BEAM_STEP).orElseThrow();
        h.assertTrue(skill.level()==3&&skill.requirements().equals(List.of(new SkillCatalog.Requirement(HighPowerSkills.THERMAL_SHELL,0)))&&HighPowerSkills.BEAM_STEP_PROPOSAL==232,"exact NEW232 LV3 and learned thermal-shell prerequisite");
        h.assertTrue(cn.academy.port.core.ClassicRules.learningStimulations(3)==7&&DeveloperType.NORMAL.ticksPerStimulation()==21&&DeveloperType.NORMAL.energyPerTick()==35&&DeveloperType.NORMAL.actualConsumption(7)==5145,"native LV3 stimulation formula remains 147 ticks and 5145 IF");
        f.parents(p);var machine=f.normal(p,5145);
        h.assertFalse(f.start(p,skill.id(),1),"paid parents cannot bypass integerLV3");h.assertTrue(machine.battery().getEnergy()==5145,"integer rejection pays nothing");
        s.setLevel(3);var portable=f.portable(p,10000);h.assertFalse(DevelopmentController.startSkill(p,skill.id()),"Portable cannot develop LV3");h.assertTrue(portable.energy()==10000,"wrong tier never drains IF");
        h.assertFalse(wire(p,"preset_edit","0:3:"+skill.id()),"unlearnedNEW232 cannot bind");
        HighPowerGrowth.values(p);f.spendShield(p,new BigDecimal("12.325"));f.injure(p);f.cp(p,37);
        var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy();double xp=s.levelExperience;
        machine=f.normal(p,5145);h.assertTrue(f.start(p,skill.id(),Integer.MAX_VALUE),"canonical correlated request starts actual Normal machine");
        f.develop(p,146);h.assertTrue(!s.learned(skill.id())&&machine.battery().getEnergy()==35,"NEW232 waits for147th paid tick");f.develop(p,1);
        h.assertTrue(s.learned(skill.id())&&s.exp(skill.id())==0&&s.levelExperience==xp&&machine.battery().getEnergy()==0&&DevelopmentController.process(p).state()==DevelopmentProcess.State.DONE,"147ticks debit5145IF and grant no mastery or XP");
        h.assertTrue(combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY))&&resources.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"learning never refills HP shield plate shares CP or q/Q");
        for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++){
            h.assertTrue(wire(p,"preset_edit",preset+":"+slot+":"+skill.id()),"NEW232 binds every native slot");
            h.assertFalse(wire(p,"preset_edit",preset+":"+((slot+1)%4)+":"+skill.id()),"ordinary duplicate mapping refused");h.assertTrue(wire(p,"preset_edit",preset+":"+slot+":"),"clear only tested mapping");
        }
        h.assertTrue(wire(p,"preset_edit","3:3:"+skill.id())&&wire(p,"activate_state","true")&&HighPowerSkills.eligible(s,skill.id()),"paidNEW232 uses ordinary activation");
        h.assertFalse(f.start(p,skill.id(),2),"learned skill cannot purchase twice");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void scatter_and_folded_branches_cannot_replace_the_learned_thermal_parent(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");s.setLevel(3);
        f.learn(p,HighPowerSkills.DUAL_POINT);f.learn(p,HighPowerSkills.SCATTER_PLATES);
        var machine=f.normal(p,5145);
        h.assertFalse(f.start(p,HighPowerSkills.BEAM_STEP,1),"genuine paid NEW212 alone cannot bypass NEW222");
        h.assertTrue(machine.battery().getEnergy()==5145&&!s.learned(HighPowerSkills.BEAM_STEP),"missing thermal parent consumes no IF");
        f.learn(p,HighPowerSkills.FOLDED_MIRROR_ARRAY);machine=f.normal(p,5145);
        h.assertFalse(f.start(p,HighPowerSkills.BEAM_STEP,2),"NEW221's separate branch does not satisfy Beam Step's NEW222 parent");
        h.assertTrue(machine.battery().getEnergy()==5145,"wrong parent never drains the physical machine");
        f.learn(p,HighPowerSkills.THERMAL_SHELL);machine=f.normal(p,5145);
        h.assertTrue(s.exp(HighPowerSkills.THERMAL_SHELL)==0&&f.start(p,HighPowerSkills.BEAM_STEP,3),"genuinely learned NEW222 suffices without invented mastery threshold");
        f.develop(p,147);h.assertTrue(s.learned(HighPowerSkills.BEAM_STEP)&&machine.battery().getEnergy()==0,"exact paid 212 to 222 to 232 prerequisite chain completes");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void final_shortage_cancel_or_late_gate_change_never_grants(GameTestHelper h){
        var f=new Fixture(h);
        for(String invalidation:List.of("battery","level","category","parent","config","cancel")){
            var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");s.setLevel(3);f.parents(p);HighPowerGrowth.values(p);f.spendShield(p,new BigDecimal("12.325"));f.injure(p);f.cp(p,37);
            var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy();
            var machine=f.normal(p,invalidation.equals("battery")?5144:5145);h.assertTrue(f.start(p,HighPowerSkills.BEAM_STEP,1),"valid machine ingress before late "+invalidation);f.develop(p,146);
            h.assertTrue(machine.battery().getEnergy()==(invalidation.equals("battery")?34:35)&&!s.learned(HighPowerSkills.BEAM_STEP)&&DevelopmentController.process(p).state()==DevelopmentProcess.State.DEVELOPING,"146 paid machine ticks leave the exact final-tick balance before "+invalidation);
            try{switch(invalidation){case "cancel"->DevelopmentController.abort(p);case "level"->s.setLevel(2);case "category"->s.changeCategoryClassic("electromaster");case "parent"->s.unlearn(HighPowerSkills.THERMAL_SHELL);case "config"->SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",HighPowerSkills.BEAM_STEP),new ClassicSkillConfiguration.Flags(false,false)));}
                f.develop(p,1);h.assertTrue(machine.battery().getEnergy()==(invalidation.equals("cancel")?35:invalidation.equals("battery")?34:0)&&!s.learned(HighPowerSkills.BEAM_STEP)&&DevelopmentController.process(p).state()==DevelopmentProcess.State.FAILED,"final tick refuses "+invalidation+" without grant or refund; insufficient Normal-machine pulls atomically retain the34IF remainder");
                h.assertTrue(combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY))&&resources.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"failed paid completion preserves exact ledgers");
            }finally{SkillCatalog.resetConfiguration();}
        }h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void native_reload_and_paid_relearning_preserve_old_bindings_and_depleted_pools(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");s.setLevel(3);f.parents(p);f.learn(p,HighPowerSkills.FOLDED_MIRROR_ARRAY);f.learn(p,HighPowerSkills.BEAM_ETCHING);f.learn(p,HighPowerSkills.DISINTEGRATION_SWEEP);
        String[] prior={"dual_point","scatter_plates","thermal_shell","folded_mirror_array","beam_etching","disintegration_sweep"};for(int i=0;i<prior.length;i++)h.assertTrue(wire(p,"preset_edit",(i/4)+":"+(i%4)+":"+prior[i]),"prior canonical mappings exist");
        var before=AbilityStorage.encodeSaved(s);var old=AbilityStorage.decodeSaved(before,s.cpDataConfig());h.assertTrue(before.getInt("schema")==3&&!old.learned(HighPowerSkills.BEAM_STEP)&&AbilityStorage.encodeSaved(old).equals(before),"preNEW232 save retains native schema with no migration or silent learning");
        f.learn(p,HighPowerSkills.BEAM_STEP);HighPowerGrowth.values(p);f.spendShield(p,new BigDecimal("12.325"));f.injure(p);f.cp(p,37);
        var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);resources.putLong("next_beam_step",p.serverLevel().getGameTime()+500);
        var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();var budget=resources.copy();
        h.assertTrue(wire(p,"preset_edit","3:3:beam_step"),"canonical NEW232 binding saved");var loaded=f.reload(p);var restored=AbilityStorage.get(loaded);
        h.assertTrue(restored.learned(HighPowerSkills.BEAM_STEP)&&restored.presets.skill(3,3).equals("beam_step"),"real native save/load retains full canonicalNEW232 ID");
        for(int i=0;i<prior.length;i++)h.assertTrue(restored.presets.skill(i/4,i%4).equals(prior[i]),"native reload retains every old MD mapping");
        h.assertTrue(stableLedger(combat).equals(stableLedger(loaded.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)))&&budget.equals(loaded.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"native reload refills neither balances nor cooldown");
        s=AbilityStorage.get(p);s.changeCategoryClassic("electromaster");f.learn(p,HighPowerSkills.FORKED_ARC);s.changeCategoryClassic("meltdowner");
        for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++)h.assertTrue(s.presets.skill(preset,slot).isEmpty(),"normal category change clears old mappings");
        var machine=f.normal(p,5145);h.assertFalse(f.start(p,HighPowerSkills.BEAM_STEP,1),"category return requires renewed prerequisite learning");h.assertTrue(machine.battery().getEnergy()==5145,"missing parent consumes noIF");f.parents(p);f.learn(p,HighPowerSkills.BEAM_STEP);
        h.assertTrue(stableLedger(combat).equals(stableLedger(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)))&&budget.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"category switches and full paid relearning never refill resources or clear cooldown");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void bounded_machine_codec_restores_canonical_id_and_pays_physical_normal_machine(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");s.setLevel(3);f.parents(p);var machine=f.normal(p,5145);
        var request=new HighPowerLearningProtocol.Request(HighPowerSkills.BEAM_STEP,Integer.MAX_VALUE);String token=MachineDeveloperSessions.activeToken(p).orElseThrow().toString(),full=token+":"+request.wire();
        h.assertTrue(request.wire().equals("beam_step:~zik0zj")&&full.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=64&&HighPowerLearningProtocol.parse(request.wire()).skill().equals("beam_step"),"unaliased canonical ID fits original 64-byte bound at maximum sequence");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());AcademyNetwork.Request decoded;
        try{AcademyNetwork.Request.CODEC.encode(buffer,new AcademyNetwork.Request("machine_hp_learn",full));decoded=AcademyNetwork.Request.CODEC.decode(buffer);h.assertTrue(buffer.readableBytes()==0&&decoded.value().equals(full),"real native bounded codec consumes exact complete request");}finally{buffer.release();}
        h.assertFalse(MachineDeveloperSessions.request(p,new AcademyNetwork.Request("machine_hp_learn",UUID.randomUUID()+":"+request.wire())),"forged machine session refused");
        h.assertTrue(MachineDeveloperSessions.request(p,decoded),"actual sender-owned machine request accepted");f.develop(p,146);h.assertTrue(machine.battery().getEnergy()==35&&!s.learned(HighPowerSkills.BEAM_STEP),"physical Normal machine still needs final tick");f.develop(p,1);
        h.assertTrue(machine.battery().getEnergy()==0&&s.learned(HighPowerSkills.BEAM_STEP)&&s.exp(HighPowerSkills.BEAM_STEP)==0,"actual machine payment grants exact canonical skill only");
        h.assertTrue(HighPowerLearningProtocol.result(request,true,"").getString("skill").equals("beam_step")&&SkillCatalog.ALL.size()==50&&cn.academy.port.preset.PresetSkills.IMPLEMENTED.size()==35&&SkillCatalog.levelSkillCount(s)==SkillCatalog.ALL.stream().filter(skill->skill.category().equals("meltdowner")&&skill.level()==3&&skill.controllable()).count(),"feedback retains full ID and all classic counts and LV3 denominator");h.succeed();
    }

    /** Native category/level witness metadata may rebase; exact balances and actual q/Q cannot. */
    private static net.minecraft.nbt.CompoundTag stableLedger(net.minecraft.nbt.CompoundTag value){
        var copy=value.copy();var growth=copy.getCompound("activeGrowth");String q=growth.getString("position");
        copy.remove("activeGrowth");copy.putString("asserted_active_position",q);return copy;
    }
    private static boolean wire(ServerPlayer player,String action,String value){return AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request(action,value));}
    private static final class Fixture {
        MachineDeveloperBlockEntity machine;
        final GameTestHelper h;final List<ServerPlayer> players=new ArrayList<>();final List<EmbeddedChannel> channels=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){this.h=h;h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});}
        ServerPlayer player(){
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"StepLearning"),false);
            var p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());players.add(p);
            var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);channels.add(channel);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            var pos=h.absoluteVec(new Vec3(5.5,2,4.5));p.moveTo(pos.x,pos.y,pos.z,0,0);p.setNoGravity(true);p.setGameMode(GameType.SURVIVAL);return p;
        }
        DeveloperItemEnergy portable(ServerPlayer player,double amount){var stack=new ItemStack(AcademyCraft.DEVELOPER.get());player.setItemInHand(InteractionHand.MAIN_HAND,stack);var energy=new DeveloperItemEnergy(stack,DeveloperType.PORTABLE);energy.energy(amount);return energy;}
        void develop(ServerPlayer player,int ticks){for(int tick=0;tick<ticks;tick++)DevelopmentController.tick(player,ignored->{});}
        MachineDeveloperBlockEntity normal(ServerPlayer player,double amount){
            var base=h.absolutePos(new BlockPos(12,1,14));
            if(machine==null){
                var item=MachineDevelopers.NORMAL_ITEM.get();var stack=new ItemStack(item);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                h.assertTrue(item.place(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(base),Direction.UP,base,false))).consumesAction(),"physical complete Normal machine placed");
                machine=(MachineDeveloperBlockEntity)h.getLevel().getBlockEntity(base);
            }
            machine.endUsers();machine.battery().load(amount);player.moveTo(base.getX()+.5,base.getY(),base.getZ()-1.5,0,0);
            h.assertTrue(machine.use(player),"actual sender-bound Normal developer session opens");return machine;
        }
        boolean start(ServerPlayer player,String skill,int sequence){
            String full=MachineDeveloperSessions.activeToken(player).orElseThrow()+":"+new HighPowerLearningProtocol.Request(skill,sequence).wire();
            return MachineDeveloperSessions.request(player,new AcademyNetwork.Request("machine_hp_learn",full));
        }
        void learn(ServerPlayer player,String skill){
            int level=SkillCatalog.find(AbilityStorage.get(player).category,skill).orElseThrow().level();int count=cn.academy.port.core.ClassicRules.learningStimulations(level);
            DeveloperType type=DeveloperType.minimumForSkill(level);double cost=type.actualConsumption(count);
            if(type==DeveloperType.PORTABLE){var battery=portable(player,cost);h.assertTrue(DevelopmentController.startSkill(player,skill),"ordinary finite prerequisite learning starts");develop(player,count*type.ticksPerStimulation());h.assertTrue(battery.energy()==0,"prerequisite pays every native tick");}
            else {var machine=normal(player,cost);h.assertTrue(start(player,skill,232),"actual Normal machine starts paid learning for "+skill);develop(player,count*type.ticksPerStimulation());h.assertTrue(machine.battery().getEnergy()==0,"Normal machine pays every native tick");}
            h.assertTrue(AbilityStorage.get(player).learned(skill)&&DevelopmentController.process(player).state()==DevelopmentProcess.State.DONE,"ordinary paid learning completes for "+skill);
        }
        void parents(ServerPlayer player){learn(player,HighPowerSkills.DUAL_POINT);learn(player,HighPowerSkills.SCATTER_PLATES);learn(player,HighPowerSkills.THERMAL_SHELL);}
        /** Same declared debit fixture as the foundation tests; production MD ingress stays closed. */
        void spendShield(ServerPlayer player,BigDecimal amount){
            var ability=AbilityStorage.get(player);String category=ability.category;var before=HighPowerCombat.shield(player);
            h.assertTrue(category.equals(HighPowerCategories.MELTDOWNER),"setup begins in the genuine MD learning category");
            h.assertFalse(HighPowerCombat.spendShield(player,amount),"MD cannot use the EM-only public shield debit");
            h.assertTrue(HighPowerCombat.shield(player).compareTo(before)==0,"rejected MD debit preserves the exact base balance");
            ability.category=HighPowerCategories.ELECTROMASTER;
            try{h.assertTrue(HighPowerCombat.spendShield(player,amount),"declared EM fixture uses the reviewed ordinary base debit");}
            finally{ability.category=category;}
            h.assertTrue(HighPowerCombat.shield(player).compareTo(before.subtract(amount))==0,"setup spends exactly the requested base shield through the central paired write");
        }
        void cp(ServerPlayer player,double amount){player.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp",amount);}
        void injure(ServerPlayer player){var maximum=new BigDecimal(HighPowerCombat.maxHealthText(player));player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health",CombatAmounts.decimalString(maximum.divide(BigDecimal.TWO)));player.setHealth(10);}
        ServerPlayer reload(ServerPlayer original){AbilityStorage.save(original);var saved=original.saveWithoutId(new net.minecraft.nbt.CompoundTag());AbilityStorage.remove(original);var restored=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[StepReload]"));players.add(restored);restored.load(saved);AbilityStorage.get(restored);return restored;}
        void close(){if(closed)return;closed=true;SkillCatalog.resetConfiguration();for(var player:players){DevelopmentController.remove(player);MachineDeveloperSessions.close(player);AbilityStorage.remove(player);var list=h.getLevel().getServer().getPlayerList();if(list.getPlayer(player.getUUID())==player)list.remove(player);else player.discard();}channels.forEach(EmbeddedChannel::finishAndReleaseAll);}
    }
}

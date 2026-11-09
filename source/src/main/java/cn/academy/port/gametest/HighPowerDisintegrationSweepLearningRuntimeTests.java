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

/** NEW231 actual Normal-machine learning, finite payment, canonical saves and no resource refill.
 * Explicit fixtures use real paid prerequisite development, injury, shield debit and accelerated developer ticks. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerDisintegrationSweepLearningRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="highpower_disintegration_sweep_learning";
    private HighPowerDisintegrationSweepLearningRuntimeTests(){}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void genuine_lv3_folded_parent_and_147_normal_ticks_preserve_shared_ledgers(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");s.setLevel(2);
        var skill=SkillCatalog.find("meltdowner",HighPowerSkills.DISINTEGRATION_SWEEP).orElseThrow();
        h.assertTrue(skill.level()==3&&skill.requirements().equals(List.of(new SkillCatalog.Requirement(HighPowerSkills.FOLDED_MIRROR_ARRAY,0)))&&HighPowerSkills.DISINTEGRATION_SWEEP_PROPOSAL==231,"exact NEW231 LV3 and learned folded-mirror prerequisite");
        f.parents(p);var machine=f.normal(p,5145);
        h.assertFalse(f.start(p,skill.id(),1),"paid parents cannot bypass integerLV3");h.assertTrue(machine.battery().getEnergy()==5145,"integer rejection pays nothing");
        s.setLevel(3);var portable=f.portable(p,10000);h.assertFalse(DevelopmentController.startSkill(p,skill.id()),"Portable cannot develop LV3");h.assertTrue(portable.energy()==10000,"wrong tier never drains IF");
        h.assertFalse(wire(p,"preset_edit","0:3:"+skill.id()),"unlearnedNEW231 cannot bind");
        HighPowerGrowth.values(p);f.spendShield(p,new BigDecimal("12.325"));f.injure(p);f.cp(p,37);
        var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy();double xp=s.levelExperience;
        machine=f.normal(p,5145);h.assertTrue(f.start(p,skill.id(),Integer.MAX_VALUE),"canonical correlated request starts actual Normal machine");
        f.develop(p,146);h.assertTrue(!s.learned(skill.id())&&machine.battery().getEnergy()==35,"NEW231 waits for147th paid tick");f.develop(p,1);
        h.assertTrue(s.learned(skill.id())&&s.exp(skill.id())==0&&s.levelExperience==xp&&machine.battery().getEnergy()==0&&DevelopmentController.process(p).state()==DevelopmentProcess.State.DONE,"147ticks debit5145IF and grant no mastery or XP");
        h.assertTrue(combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY))&&resources.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"learning never refills HP shield plate shares CP or q/Q");
        for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++){
            h.assertTrue(wire(p,"preset_edit",preset+":"+slot+":"+skill.id()),"NEW231 binds every native slot");
            h.assertFalse(wire(p,"preset_edit",preset+":"+((slot+1)%4)+":"+skill.id()),"ordinary duplicate mapping refused");h.assertTrue(wire(p,"preset_edit",preset+":"+slot+":"),"clear only tested mapping");
        }
        h.assertTrue(wire(p,"preset_edit","3:3:"+skill.id())&&wire(p,"activate_state","true")&&HighPowerSkills.eligible(s,skill.id()),"paidNEW231 uses ordinary activation");
        h.assertFalse(f.start(p,skill.id(),2),"learned skill cannot purchase twice");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void final_shortage_cancel_or_late_gate_change_never_grants(GameTestHelper h){
        var f=new Fixture(h);
        for(String invalidation:List.of("battery","level","category","parent","config","cancel")){
            var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");s.setLevel(3);f.parents(p);HighPowerGrowth.values(p);f.injure(p);f.cp(p,37);
            var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy();
            var machine=f.normal(p,invalidation.equals("battery")?5144:5145);h.assertTrue(f.start(p,HighPowerSkills.DISINTEGRATION_SWEEP,1),"valid machine ingress before late "+invalidation);f.develop(p,146);
            h.assertTrue(machine.battery().getEnergy()==(invalidation.equals("battery")?34:35)&&!s.learned(HighPowerSkills.DISINTEGRATION_SWEEP)&&DevelopmentController.process(p).state()==DevelopmentProcess.State.DEVELOPING,"146 paid machine ticks leave the exact final-tick balance before "+invalidation);
            try{switch(invalidation){case "cancel"->DevelopmentController.abort(p);case "level"->s.setLevel(2);case "category"->s.changeCategoryClassic("electromaster");case "parent"->s.unlearn(HighPowerSkills.FOLDED_MIRROR_ARRAY);case "config"->SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",HighPowerSkills.DISINTEGRATION_SWEEP),new ClassicSkillConfiguration.Flags(false,false)));}
                f.develop(p,1);h.assertTrue(machine.battery().getEnergy()==(invalidation.equals("cancel")?35:invalidation.equals("battery")?34:0)&&!s.learned(HighPowerSkills.DISINTEGRATION_SWEEP)&&DevelopmentController.process(p).state()==DevelopmentProcess.State.FAILED,"final tick refuses "+invalidation+" without grant or refund; insufficient Normal-machine pulls atomically retain the34IF remainder");
                h.assertTrue(combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY))&&resources.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"failed paid completion preserves exact ledgers");
            }finally{SkillCatalog.resetConfiguration();}
        }h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void native_reload_and_paid_relearning_preserve_old_bindings_and_depleted_pools(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");s.setLevel(3);f.parents(p);f.learn(p,HighPowerSkills.SCATTER_PLATES);f.learn(p,HighPowerSkills.THERMAL_SHELL);
        String[] prior={"dual_point","scatter_plates","thermal_shell","folded_mirror_array"};for(int slot=0;slot<4;slot++)h.assertTrue(wire(p,"preset_edit","2:"+slot+":"+prior[slot]),"prior canonical mappings exist");
        var before=AbilityStorage.encodeSaved(s);var old=AbilityStorage.decodeSaved(before,s.cpDataConfig());h.assertTrue(!old.learned(HighPowerSkills.DISINTEGRATION_SWEEP)&&AbilityStorage.encodeSaved(old).equals(before),"preNEW231 save has no migration or silent learning");
        f.learn(p,HighPowerSkills.DISINTEGRATION_SWEEP);HighPowerGrowth.values(p);f.spendShield(p,new BigDecimal("12.325"));f.injure(p);f.cp(p,37);
        var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);resources.putLong("next_disintegration_sweep",p.serverLevel().getGameTime()+500);
        var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();var budget=resources.copy();
        h.assertTrue(wire(p,"preset_edit","3:3:disintegration_sweep"),"canonical NEW231 binding saved");var loaded=f.reload(p);var restored=AbilityStorage.get(loaded);
        h.assertTrue(restored.learned(HighPowerSkills.DISINTEGRATION_SWEEP)&&restored.presets.skill(3,3).equals("disintegration_sweep"),"real native save/load retains full canonicalNEW231 ID");
        for(int slot=0;slot<4;slot++)h.assertTrue(restored.presets.skill(2,slot).equals(prior[slot]),"native reload retains every old MD mapping");
        h.assertTrue(stableLedger(combat).equals(stableLedger(loaded.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)))&&budget.equals(loaded.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"native reload refills neither balances nor cooldown");
        s=AbilityStorage.get(p);s.changeCategoryClassic("electromaster");f.learn(p,HighPowerSkills.FORKED_ARC);s.changeCategoryClassic("meltdowner");
        for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++)h.assertTrue(s.presets.skill(preset,slot).isEmpty(),"normal category change clears old mappings");
        var machine=f.normal(p,5145);h.assertFalse(f.start(p,HighPowerSkills.DISINTEGRATION_SWEEP,1),"category return requires renewed prerequisite learning");h.assertTrue(machine.battery().getEnergy()==5145,"missing parent consumes noIF");f.parents(p);f.learn(p,HighPowerSkills.DISINTEGRATION_SWEEP);
        h.assertTrue(stableLedger(combat).equals(stableLedger(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)))&&budget.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"category switches and full paid relearning never refill resources or clear cooldown");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void bounded_machine_codec_restores_canonical_id_and_pays_physical_normal_machine(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");s.setLevel(3);f.parents(p);var machine=f.normal(p,5145);
        var request=new HighPowerLearningProtocol.Request(HighPowerSkills.DISINTEGRATION_SWEEP,Integer.MAX_VALUE);String token=MachineDeveloperSessions.activeToken(p).orElseThrow().toString(),full=token+":"+request.wire();
        h.assertTrue(request.wire().equals("ds:~zik0zj")&&full.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=64&&HighPowerLearningProtocol.parse(request.wire()).skill().equals("disintegration_sweep"),"narrow wire-only alias fits original64-byte bound and restores canonicalID");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());AcademyNetwork.Request decoded;
        try{AcademyNetwork.Request.CODEC.encode(buffer,new AcademyNetwork.Request("machine_hp_learn",full));decoded=AcademyNetwork.Request.CODEC.decode(buffer);h.assertTrue(buffer.readableBytes()==0&&decoded.value().equals(full),"real native bounded codec consumes exact complete request");}finally{buffer.release();}
        h.assertFalse(MachineDeveloperSessions.request(p,new AcademyNetwork.Request("machine_hp_learn",UUID.randomUUID()+":"+request.wire())),"forged machine session refused");
        h.assertTrue(MachineDeveloperSessions.request(p,decoded),"actual sender-owned machine request accepted");f.develop(p,146);h.assertTrue(machine.battery().getEnergy()==35&&!s.learned(HighPowerSkills.DISINTEGRATION_SWEEP),"physical Normal machine still needs final tick");f.develop(p,1);
        h.assertTrue(machine.battery().getEnergy()==0&&s.learned(HighPowerSkills.DISINTEGRATION_SWEEP)&&s.exp(HighPowerSkills.DISINTEGRATION_SWEEP)==0,"actual machine payment grants exact canonical skill only");
        h.assertTrue(HighPowerLearningProtocol.result(request,true,"").getString("skill").equals("disintegration_sweep")&&SkillCatalog.ALL.size()==50&&cn.academy.port.preset.PresetSkills.IMPLEMENTED.size()==35&&SkillCatalog.levelSkillCount(s)==SkillCatalog.ALL.stream().filter(skill->skill.category().equals("meltdowner")&&skill.level()==3&&skill.controllable()).count(),"feedback retains full ID and all classic counts and LV3 denominator");h.succeed();
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
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"SweepLearning"),false);
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
            else {var machine=normal(player,cost);h.assertTrue(start(player,skill,231),"actual Normal machine starts NEW231 learning");develop(player,count*type.ticksPerStimulation());h.assertTrue(machine.battery().getEnergy()==0,"Normal machine pays every native tick");}
            h.assertTrue(AbilityStorage.get(player).learned(skill)&&DevelopmentController.process(player).state()==DevelopmentProcess.State.DONE,"ordinary paid learning completes for "+skill);
        }
        void parents(ServerPlayer player){learn(player,HighPowerSkills.DUAL_POINT);learn(player,HighPowerSkills.FOLDED_MIRROR_ARRAY);}
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
        ServerPlayer reload(ServerPlayer original){AbilityStorage.save(original);var saved=original.saveWithoutId(new net.minecraft.nbt.CompoundTag());AbilityStorage.remove(original);var restored=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[SweepReload]"));players.add(restored);restored.load(saved);AbilityStorage.get(restored);return restored;}
        void close(){if(closed)return;closed=true;SkillCatalog.resetConfiguration();for(var player:players){DevelopmentController.remove(player);MachineDeveloperSessions.close(player);AbilityStorage.remove(player);var list=h.getLevel().getServer().getPlayerList();if(list.getPlayer(player.getUUID())==player)list.remove(player);else player.discard();}channels.forEach(EmbeddedChannel::finishAndReleaseAll);}
    }
}

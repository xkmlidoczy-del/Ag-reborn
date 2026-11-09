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

/** NEW221 native learning, paid final validation, ordinary bindings and exact save/ledger retention.
 * Finite batteries, injury, shield debit and accelerated developer ticks are declared fixtures. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerFoldedMirrorLearningRuntimeTests {
    private static final String TEMPLATE="highpower_empty";
    private HighPowerFoldedMirrorLearningRuntimeTests(){}

    @GameTest(template=TEMPLATE,batch="highpower_folded_mirror_array_learning",timeoutTicks=15)
    public static void genuine_lv2_dual_point_prerequisite_and_130_paid_ticks_preserve_shared_ledgers(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");
        var skill=SkillCatalog.find("meltdowner",HighPowerSkills.FOLDED_MIRROR_ARRAY).orElseThrow();
        h.assertTrue(skill.level()==2&&skill.requirements().equals(List.of(new SkillCatalog.Requirement(HighPowerSkills.DUAL_POINT,0)))&&HighPowerSkills.FOLDED_MIRROR_ARRAY_PROPOSAL==221,"NEW221 canonical MD LV2 and exact learned NEW211 prerequisite");
        var blocked=f.portable(p,3900);
        h.assertFalse(wire(p,"hp_learn",new HighPowerLearningProtocol.Request(skill.id(),1).wire()),"fresh MD cannot bypass level or parent");
        h.assertFalse(wire(p,"preset_edit","0:3:"+skill.id()),"unlearned NEW221 cannot bind");
        h.assertTrue(blocked.energy()==3900&&!HighPowerCombat.enabled(p),"early rejection spends no IF or creates a ledger");
        f.learn(p,HighPowerSkills.DUAL_POINT);
        blocked=f.portable(p,3900);h.assertFalse(DevelopmentController.startSkill(p,skill.id()),"genuine NEW211 cannot bypass integer LV2");
        h.assertTrue(blocked.energy()==3900,"integer level rejection consumes no IF");
        s.setLevel(2);HighPowerGrowth.values(p);f.spendShield(p,new BigDecimal("12.325"));f.injure(p);f.cp(p,37);
        var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy();double xp=s.levelExperience;
        var battery=f.portable(p,3900);
        h.assertTrue(wire(p,"hp_learn",new HighPowerLearningProtocol.Request(skill.id(),Integer.MAX_VALUE).wire()),"canonical bounded learning ingress starts actual NEW221 development");
        f.develop(p,129);
        h.assertTrue(!s.learned(skill.id())&&battery.energy()==30&&combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)),"NEW221 waits for all 130 ticks without early grant or shield mutation");
        f.develop(p,1);
        h.assertTrue(s.learned(skill.id())&&s.exp(skill.id())==0&&s.levelExperience==xp&&battery.energy()==0&&DevelopmentController.process(p).state()==DevelopmentProcess.State.DONE,"NEW221 exact3900IF completion gives no mastery or levelXP");
        h.assertTrue(combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY))&&resources.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"learning never refills HP, shared shield, independent plate shares, CP or q/Q");
        for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++){
            h.assertTrue(wire(p,"preset_edit",preset+":"+slot+":"+skill.id()),"NEW221 binds each native preset slot");
            h.assertFalse(wire(p,"preset_edit",preset+":"+((slot+1)%4)+":"+skill.id()),"NEW221 retains ordinary duplicate-per-preset rejection");
            h.assertTrue(wire(p,"preset_edit",preset+":"+slot+":"),"clear only tested binding");
        }
        h.assertTrue(wire(p,"preset_edit","3:3:"+skill.id())&&wire(p,"activate_state","true")&&HighPowerSkills.eligible(s,skill.id()),"paid NEW221 uses ordinary binding and activation");
        var saved=AbilityStorage.encodeSaved(s);var restored=AbilityStorage.decodeSaved(saved,s.cpDataConfig());
        h.assertTrue(saved.getInt("schema")==3&&AbilityStorage.encodeSaved(restored).equals(saved),"NEW221 preserves native save schema and canonical mapping");
        h.assertFalse(DevelopmentController.startSkill(p,skill.id()),"already learned skill cannot purchase twice");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_folded_mirror_array_learning",timeoutTicks=15)
    public static void final_shortage_cancel_or_late_gate_change_never_grants(GameTestHelper h){
        var f=new Fixture(h);
        for(String invalidation:List.of("battery","level","category","parent","config","cancel")){
            var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");s.setLevel(2);f.learn(p,HighPowerSkills.DUAL_POINT);HighPowerGrowth.values(p);f.injure(p);f.cp(p,37);
            var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy();
            var battery=f.portable(p,invalidation.equals("battery")?3899:3900);
            h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.FOLDED_MIRROR_ARRAY),"valid native NEW221 development starts before late "+invalidation);f.develop(p,129);
            try{
                switch(invalidation){
                    case "cancel"->DevelopmentController.abort(p);
                    case "level"->s.setLevel(1);
                    case "category"->s.changeCategoryClassic("electromaster");
                    case "parent"->s.unlearn(HighPowerSkills.DUAL_POINT);
                    case "config"->SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",HighPowerSkills.FOLDED_MIRROR_ARRAY),new ClassicSkillConfiguration.Flags(false,false)));
                }
                f.develop(p,1);
                h.assertTrue(battery.energy()==(invalidation.equals("cancel")?30:0)&&!s.learned(HighPowerSkills.FOLDED_MIRROR_ARRAY)&&DevelopmentController.process(p).state()==DevelopmentProcess.State.FAILED,"final paid tick rejects "+invalidation+" without a grant or refund");
                h.assertTrue(combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY))&&resources.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"failed completion cannot refill or alter existing exact ledgers");
            }finally{SkillCatalog.resetConfiguration();}
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_folded_mirror_array_learning",timeoutTicks=15)
    public static void native_reload_category_changes_and_paid_relearning_keep_depleted_resources(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");s.setLevel(2);
        f.learn(p,HighPowerSkills.DUAL_POINT);f.learn(p,HighPowerSkills.SCATTER_PLATES);f.learn(p,HighPowerSkills.BEAM_ETCHING);f.learn(p,HighPowerSkills.THERMAL_SHELL);
        h.assertTrue(wire(p,"preset_edit","2:0:dual_point")&&wire(p,"preset_edit","2:1:scatter_plates")&&wire(p,"preset_edit","2:2:beam_etching")&&wire(p,"preset_edit","2:3:thermal_shell"),"pre-NEW221 save uses all four existing MD additions and bindings");
        var previous=AbilityStorage.encodeSaved(s);var old=AbilityStorage.decodeSaved(previous,s.cpDataConfig());
        h.assertTrue(!old.learned(HighPowerSkills.FOLDED_MIRROR_ARRAY)&&AbilityStorage.encodeSaved(old).equals(previous),"existing save roundtrips without NEW221 migration or silent learning");
        f.learn(p,HighPowerSkills.FOLDED_MIRROR_ARRAY);
        for(int slot=0;slot<4;slot++)h.assertTrue(s.presets.skill(2,slot).equals(old.presets.skill(2,slot)),"NEW221 learning preserves every old MD binding");
        HighPowerGrowth.values(p);f.spendShield(p,new BigDecimal("12.325"));f.injure(p);f.cp(p,37);
        var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);resources.putLong("next_folded_mirror_array",p.serverLevel().getGameTime()+500);
        var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();var budget=resources.copy();
        h.assertTrue(wire(p,"preset_edit","3:0:dual_point")&&wire(p,"preset_edit","3:1:scatter_plates")&&wire(p,"preset_edit","3:2:thermal_shell")&&wire(p,"preset_edit","3:3:folded_mirror_array"),"NEW221 coexists with preserved NEW211/212/222 in ordinary slots");
        var loaded=f.reload(p);var restored=AbilityStorage.get(loaded);
        h.assertTrue(restored.learned(HighPowerSkills.FOLDED_MIRROR_ARRAY)&&restored.presets.skill(3,3).equals("folded_mirror_array")&&restored.learned(HighPowerSkills.THERMAL_SHELL)&&restored.presets.skill(3,2).equals("thermal_shell"),"real native player save/load retains NEW221 learning and mapping");
        for(int slot=0;slot<4;slot++)h.assertTrue(restored.presets.skill(2,slot).equals(old.presets.skill(2,slot)),"native reload retains every previous MD skill binding");
        h.assertTrue(stableLedger(combat).equals(stableLedger(loaded.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)))&&budget.equals(loaded.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"native reload cannot refill balances or cooldown");
        s=AbilityStorage.get(p);
        s.changeCategoryClassic("electromaster");f.learn(p,HighPowerSkills.FORKED_ARC);s.changeCategoryClassic("meltdowner");
        for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++)h.assertTrue(s.presets.skill(preset,slot).isEmpty(),"ordinary category change clears old mappings");
        var blocked=f.portable(p,3900);h.assertFalse(DevelopmentController.startSkill(p,HighPowerSkills.FOLDED_MIRROR_ARRAY),"category return cannot bypass renewed NEW211 learning");h.assertTrue(blocked.energy()==3900,"missing prerequisite consumes no energy");
        f.learn(p,HighPowerSkills.DUAL_POINT);f.learn(p,HighPowerSkills.FOLDED_MIRROR_ARRAY);
        h.assertTrue(stableLedger(combat).equals(stableLedger(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)))&&budget.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"category switches and paid relearning never refill existing shared resources or reset cooldown");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_folded_mirror_array_learning",timeoutTicks=15)
    public static void longest_64_byte_machine_request_roundtrips_and_pays_real_normal_developer(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");s.setLevel(2);f.learn(p,HighPowerSkills.DUAL_POINT);
        var item=MachineDevelopers.NORMAL_ITEM.get();var stack=new ItemStack(item);p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var base=h.absolutePos(new BlockPos(12,1,14));
        h.assertTrue(item.place(new BlockPlaceContext(p,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(base),Direction.UP,base,false))).consumesAction(),"physical eight-cell Normal developer placed");
        var machine=(MachineDeveloperBlockEntity)h.getLevel().getBlockEntity(base);machine.battery().load(3675);
        p.moveTo(base.getX()+.5,base.getY(),base.getZ()-1.5,0,0);h.assertTrue(machine.use(p),"real sender-bound Normal session opens");
        String token=MachineDeveloperSessions.activeToken(p).orElseThrow().toString();
        var request=new HighPowerLearningProtocol.Request(HighPowerSkills.FOLDED_MIRROR_ARRAY,Integer.MAX_VALUE);String full=token+":"+request.wire();
        h.assertTrue(full.length()==64&&full.getBytes(java.nio.charset.StandardCharsets.UTF_8).length==64&&request.wire().equals("folded_mirror_array:~zik0zj"),"full canonical NEW221 plus UUID and max correlation exactly fills unchanged 64-byte value bound");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());AcademyNetwork.Request decoded;
        try{AcademyNetwork.Request.CODEC.encode(buffer,new AcademyNetwork.Request("machine_hp_learn",full));decoded=AcademyNetwork.Request.CODEC.decode(buffer);h.assertTrue(buffer.readableBytes()==0,"native codec consumes the complete longest request");}finally{buffer.release();}
        h.assertTrue(decoded.value().equals(full),"actual native request codec preserves every canonical byte");
        h.assertFalse(MachineDeveloperSessions.request(p,new AcademyNetwork.Request("machine_hp_learn",UUID.randomUUID()+":"+request.wire())),"forged machine token cannot use the longest request");
        h.assertTrue(MachineDeveloperSessions.request(p,decoded),"real sender-bound machine ingress accepts canonical NEW221 request at the exact wire limit");
        f.develop(p,104);h.assertTrue(!s.learned(HighPowerSkills.FOLDED_MIRROR_ARRAY)&&machine.battery().getEnergy()==35,"Normal machine still requires its final paid native tick");f.develop(p,1);
        h.assertTrue(s.learned(HighPowerSkills.FOLDED_MIRROR_ARRAY)&&s.exp(HighPowerSkills.FOLDED_MIRROR_ARRAY)==0&&machine.battery().getEnergy()==0&&DevelopmentController.process(p).state()==DevelopmentProcess.State.DONE,"five real Normal stimulations cost 3675 IF over 105 ticks without free grant");
        h.assertTrue(SkillCatalog.ALL.size()==50&&cn.academy.port.preset.PresetSkills.IMPLEMENTED.size()==35&&SkillCatalog.levelSkillCount(s)==SkillCatalog.ALL.stream().filter(skill->skill.category().equals(s.category)&&skill.level()==2&&skill.controllable()).count(),"NEW221 retains fifty classic skills, thirty-five classic routes and MD denominator");h.succeed();
    }

    /** Native category/level witness metadata may rebase; exact balances and actual q/Q cannot. */
    private static net.minecraft.nbt.CompoundTag stableLedger(net.minecraft.nbt.CompoundTag value){
        var copy=value.copy();var growth=copy.getCompound("activeGrowth");String q=growth.getString("position");
        copy.remove("activeGrowth");copy.putString("asserted_active_position",q);return copy;
    }
    private static boolean wire(ServerPlayer player,String action,String value){return AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request(action,value));}
    private static final class Fixture {
        final GameTestHelper h;final List<ServerPlayer> players=new ArrayList<>();final List<EmbeddedChannel> channels=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){this.h=h;h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});}
        ServerPlayer player(){
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"MirrorLearning"),false);
            var p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());players.add(p);
            var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);channels.add(channel);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            var pos=h.absoluteVec(new Vec3(5.5,2,4.5));p.moveTo(pos.x,pos.y,pos.z,0,0);p.setNoGravity(true);p.setGameMode(GameType.SURVIVAL);return p;
        }
        DeveloperItemEnergy portable(ServerPlayer player,double amount){var stack=new ItemStack(AcademyCraft.DEVELOPER.get());player.setItemInHand(InteractionHand.MAIN_HAND,stack);var energy=new DeveloperItemEnergy(stack,DeveloperType.PORTABLE);energy.energy(amount);return energy;}
        void develop(ServerPlayer player,int ticks){for(int tick=0;tick<ticks;tick++)DevelopmentController.tick(player,ignored->{});}
        void learn(ServerPlayer player,String skill){int level=SkillCatalog.find(AbilityStorage.get(player).category,skill).orElseThrow().level();int stimulations=cn.academy.port.core.ClassicRules.learningStimulations(level);double cost=DeveloperType.PORTABLE.actualConsumption(stimulations);var battery=portable(player,cost);h.assertTrue(DevelopmentController.startSkill(player,skill),"ordinary finite learning starts for "+skill);develop(player,stimulations*DeveloperType.PORTABLE.ticksPerStimulation());h.assertTrue(AbilityStorage.get(player).learned(skill)&&battery.energy()==0&&DevelopmentController.process(player).state()==DevelopmentProcess.State.DONE,"ordinary learning pays all native IF for "+skill);}
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
        ServerPlayer reload(ServerPlayer original){AbilityStorage.save(original);var saved=original.saveWithoutId(new net.minecraft.nbt.CompoundTag());AbilityStorage.remove(original);var restored=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[MirrorReload]"));players.add(restored);restored.load(saved);AbilityStorage.get(restored);return restored;}
        void close(){if(closed)return;closed=true;SkillCatalog.resetConfiguration();for(var player:players){DevelopmentController.remove(player);MachineDeveloperSessions.close(player);AbilityStorage.remove(player);var list=h.getLevel().getServer().getPlayerList();if(list.getPlayer(player.getUUID())==player)list.remove(player);else player.discard();}channels.forEach(EmbeddedChannel::finishAndReleaseAll);}
    }
}

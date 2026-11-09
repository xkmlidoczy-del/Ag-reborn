/* Isolated M34 fixtures: cached API compile only; native execution belongs to owner. GPLv3. */
package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.core.*;
import cn.academy.port.skill.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.util.*;

@GameTestHolder("academy") @PrefixGameTestTemplate(false)
public final class AcademyTargetedContextTerminationRuntimeTests {
    private static final String TEMPLATE="runtime_empty", BATCH="academy_targeted_context_termination";
    private static AbilityProgress ready(ServerPlayer player, String category, String... ids) {
        var state=AbilityStorage.get(player);state.selectCategory(category);state.setLevel(5);
        for(String id:ids)state.learn(id);
        state.restoreCalculatedMaxima(20000,20000);state.cp=20000;state.overload=0;state.overloadFine=true;
        state.setActivateState(true);player.setYRot(0);player.setXRot(0);player.setYHeadRot(0);player.yRotO=player.xRotO=0;
        return state;
    }
    private static boolean request(ServerPlayer player,String wire){return AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("context_abort",wire));}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void unmapped_accepted_vector_context_targets_one_live_session(GameTestHelper helper){
        try(var fixture=new Fixture(helper)){
            var player=fixture.player;var state=ready(player,"vecmanip",VecDeviation.ID,VecReflection.ID);
            state.presets.edit(0,0,VecDeviation.ID,id->true);state.presets.edit(0,1,VecReflection.ID,id->true);
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("slot_press_token","0:101")),"authenticated deviation starts");
            long deviation=fixture.token("vec_deviation_start",101);
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("slot_press_token","1:102")),"authenticated reflection starts independently");
            long reflection=fixture.token("vec_reflection_start",102);
            helper.assertTrue(deviation>0&&reflection>0,"tokens captured from actual production start payloads");
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("preset_edit","0:1:")),"actual preset edit unmaps default reflection delegate");
            helper.assertTrue(VecDeviation.active(player)&&VecReflection.active(player),"preset flush preserves independent contexts");
            helper.assertFalse(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("slot_abort_token","1:102")),"old slot route loses mapping");
            double cp=state.cp,overload=state.overload;
            helper.assertFalse(request(player,"vec_reflection:102:"+(reflection+1)),"wrong accepted server token rejected");
            helper.assertFalse(request(player,"vec_reflection:101:"+reflection),"other context input rejected");
            helper.assertTrue(request(player,"vec_reflection:102:"+reflection),"retained context identity terminates after unmapping");
            helper.assertTrue(VecDeviation.active(player)&&!VecReflection.active(player)&&state.activated,"selected newest context only; activation remains on");
            helper.assertTrue(state.cp==cp&&state.overload==overload&&state.cooldowns.isEmpty(),"reflection terminal has no spend or cooldown");
            helper.assertFalse(request(player,"vec_reflection:102:"+reflection),"duplicate terminal inert");
            helper.assertTrue(request(player,"vec_deviation:101:"+deviation),"older independent context remains separately terminable");
            helper.succeed();
        }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void authenticated_context_rejects_wildcards_replacement_busy_and_liveness(GameTestHelper helper){
        try(var fixture=new Fixture(helper)){
            var player=fixture.player;var state=ready(player,"vecmanip",VecDeviation.ID);
            state.presets.edit(0,0,VecDeviation.ID,id->true);
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("slot_press_token","0:201")),"real input admits context");
            long token=fixture.token("vec_deviation_start",201);
            String wire="vec_deviation:201:"+token;
            for(String bad:List.of("vec_deviation:0:"+token,"vec_deviation:201:0","vec_deviation:0201:"+token,"vec_deviation:+201:"+token,"railgun:201:"+token,"vec_deviation:201:"+token+":1"))
                helper.assertFalse(request(player,bad),"strict canonical whitelist wire rejected: "+bad);
            player.setGameMode(GameType.SPECTATOR);helper.assertFalse(request(player,wire),"spectator cannot terminate through wire");player.setGameMode(GameType.SURVIVAL);
            state.cp=Double.NaN;helper.assertFalse(request(player,wire),"nonfinite live state denied");state.cp=20000;
            boolean[] attempted={false};state.bindConsumption(()->SkillConsumption.Config.DEFAULT,mutation->{attempted[0]=true;helper.assertFalse(request(player,wire),"consumption reentry cannot terminate its live session");},SkillConsumption.NO_OVERLOAD_EVENT,()->true);
            state.consumeSkill(VecDeviation.ID,0,0,false);helper.assertTrue(attempted[0]&&VecDeviation.active(player),"busy rejection leaves original context live");
            AbilityStorage.remove(player);var replacement=AbilityStorage.get(player);
            helper.assertTrue(replacement!=state,"actual cache replacement creates distinct ability state");
            helper.assertFalse(request(player,wire),"stale state identity rejected without terminal side effects");
            helper.assertTrue(VecDeviation.active(player)&&replacement.cooldowns.isEmpty(),"rejected stale endpoint leaves lifecycle disposal to its owner");
            helper.succeed();
        }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void accepted_flashing_and_storm_contexts_keep_source_terminal_side_effects(GameTestHelper helper){
        try(var fixture=new Fixture(helper)){
            var player=fixture.player;var state=ready(player,"vecmanip",StormWing.ID);
            state.presets.edit(0,0,StormWing.ID,id->true);player.getAbilities().mayfly=false;
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("slot_press_token","0:301")),"real storm charge starts");
            long storm=fixture.token("storm_wing_start",301);
            helper.assertTrue(player.getAbilities().mayfly&&request(player,"storm_wing:301:"+storm),"strict storm terminal accepts actual token and input");
            helper.assertTrue(!StormWing.active(player)&&!player.getAbilities().mayfly&&state.cooldowns.getOrDefault(StormWing.ID,0)>0,"storm terminal restores flight and applies source cooldown");
            state=ready(player,"teleporter",Flashing.ID);state.presets.edit(0,0,Flashing.ID,id->true);
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("slot_press_token","0:302")),"real flashing mode starts");
            long flashing=fixture.token("flashing_start",302);double cp=state.cp;
            helper.assertFalse(request(player,"flashing:302:"+(flashing+1)),"flashing token mismatch cannot apply cooldown");
            helper.assertTrue(Flashing.active(player)&&state.cooldowns.isEmpty(),"rejected terminal preserves live mode");
            helper.assertTrue(request(player,"flashing:302:"+flashing),"strict flashing terminal accepts matching context");
            helper.assertTrue(!Flashing.active(player)&&state.cooldowns.getOrDefault(Flashing.ID,0)==900&&state.cp==cp,"source captured-mastery cooldown with no extra spend");
            helper.succeed();
        }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void authenticated_preset_switch_and_edit_preserve_all_source_no_abort_contexts(GameTestHelper helper){
        // Flashing requires a different category; it is tested with a separate authenticated owner.
        try(var vectors=new Fixture(helper);var teleporter=new Fixture(helper)){
            var player=vectors.player;var state=ready(player,"vecmanip",VecDeviation.ID,VecReflection.ID,StormWing.ID,BloodRetrograde.ID);
            String[] skills={VecDeviation.ID,VecReflection.ID,StormWing.ID,BloodRetrograde.ID};
            for(int slot=0;slot<skills.length;slot++){
                state.presets.edit(0,slot,skills[slot],id->true);
                helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("slot_press_token",slot+":"+(401+slot))),"actual vector slot starts "+skills[slot]);
            }
            long reflection=vectors.token("vec_reflection_start",402),storm=vectors.token("storm_wing_start",403);
            var other=teleporter.player;var flashState=ready(other,"teleporter",Flashing.ID);flashState.presets.edit(0,0,Flashing.ID,id->true);
            helper.assertTrue(AcademyGameplay.requestFromClient(other,new AcademyNetwork.Request("slot_press_token","0:405")),"actual separate-category Flashing starts");
            long flashing=teleporter.token("flashing_start",405);
            double cp=state.cp,overload=state.overload,flashCp=flashState.cp;
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("preset_switch","1")),"actual authenticated vector preset switch");
            helper.assertTrue(AcademyGameplay.requestFromClient(other,new AcademyNetwork.Request("preset_switch","1")),"actual authenticated teleporter preset switch");
            helper.assertTrue(state.presets.current()==1&&flashState.presets.current()==1,"selected indices changed");
            helper.assertTrue(VecDeviation.active(player)&&VecReflection.active(player)&&StormWing.active(player)&&BloodRetrograde.active(player)&&Flashing.active(other),"switch does not undo source empty onAbort callbacks");
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("preset_edit","0:1:")),"actual edit to nonselected old preset");
            helper.assertTrue(AcademyGameplay.requestFromClient(other,new AcademyNetwork.Request("preset_edit","0:0:")),"actual edit unmaps Flashing in nonselected preset");
            helper.assertTrue(VecDeviation.active(player)&&VecReflection.active(player)&&StormWing.active(player)&&BloodRetrograde.active(player)&&Flashing.active(other),"edit preserves all five independent source contexts");
            helper.assertTrue(state.cp==cp&&state.overload==overload&&flashState.cp==flashCp&&state.cooldowns.isEmpty()&&flashState.cooldowns.isEmpty()&&player.getAbilities().mayfly,"preset mutation has no spend/flight/cooldown terminal side effects");
            helper.assertTrue(request(player,"vec_reflection:402:"+reflection),"retained unmapped reflection identity still targets V terminal");
            helper.assertTrue(VecDeviation.active(player)&&!VecReflection.active(player)&&StormWing.active(player)&&BloodRetrograde.active(player)&&Flashing.active(other),"one retained V terminal leaves other owners/contexts alive");
            helper.assertTrue(request(player,"storm_wing:403:"+storm)&&request(other,"flashing:405:"+flashing),"other unmapped accepted context identities remain independently terminable");
            helper.assertTrue(VecDeviation.active(player)&&BloodRetrograde.active(player),"strict context terminals do not blanket dispose autonomous Blood or deviation");
            helper.succeed();
        }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void rejected_preset_mutations_keep_admission_and_live_contexts_unchanged(GameTestHelper helper){
        try(var fixture=new Fixture(helper)){
            var player=fixture.player;var state=ready(player,"vecmanip",VecDeviation.ID);
            state.presets.edit(0,0,VecDeviation.ID,id->true);
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("slot_press_token","0:501")),"actual accepted deviation");
            long revision=state.presets.revision();int current=state.presets.current();
            for(String value:List.of("-1","4","00","+1"," 1","1 ","", "١"))
                helper.assertFalse(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("preset_switch",value)),"existing strict preset index rejection "+value);
            for(String value:List.of("-1:0:","4:0:","0:4:","0:00:","0:0:unknown","0:0:vec_reflection","0:0::"))
                helper.assertFalse(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("preset_edit",value)),"existing preset edit rejection "+value);
            helper.assertTrue(state.presets.current()==current&&state.presets.revision()==revision&&state.presets.currentSkill(0).equals(VecDeviation.ID)&&VecDeviation.active(player),"rejected indices/skills do not mutate presets or terminate context");
            state.activated=false;
            helper.assertFalse(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("preset_switch","1")),"existing inactive switch admission remains denied");
            helper.assertTrue(state.presets.current()==current&&state.presets.revision()==revision&&VecDeviation.active(player),"inactive rejection retains pending context until lifecycle policy runs");
            helper.succeed();
        }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void scoped_and_preset_railgun_abort_only_cancel_owned_pending_transport(GameTestHelper helper){
        try(var fixture=new Fixture(helper);var outsider=new Fixture(helper)){
            var player=fixture.player;var state=ready(player,"electromaster","railgun");
            state.presets.edit(0,0,"railgun",id->true);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT,4));
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("slot_press","0")),"actual slot starts server-owned Railgun countdown");
            helper.assertFalse(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("railgun_abort","0")),"scoped action requires exactly empty value");
            helper.assertFalse(AcademyGameplay.requestFromClient(outsider.player,new AcademyNetwork.Request("railgun_abort","")),"different authenticated owner has no matching charge");
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("railgun_abort","")),"matching current owner cancels only pending countdown");
            helper.assertFalse(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("railgun_abort","")),"duplicate pending charge cancellation inert");
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("slot_press","0")),"fresh owned countdown after scoped cancellation");
            helper.assertFalse(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("preset_switch","4")),"invalid switch denied");
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("railgun_abort","")),"invalid preset did not silently cancel admitted timer");
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("slot_press","0")),"another source countdown");
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("preset_switch","1")),"valid switch mutates preset");
            helper.assertFalse(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("railgun_abort","")),"valid switch already removed only its pending timer");
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("preset_switch","0")),"return to mapped preset");
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("slot_press","0")),"countdown before nonselected edit");
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("preset_edit","1:0:")),"source flush from nonselected preset edit");
            helper.assertFalse(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("railgun_abort","")),"successful edit cancels pending Railgun transport");
            helper.assertTrue(player.getMainHandItem().getCount()==4&&state.cooldowns.isEmpty(),"cancellation spends no ammo and applies no cast cooldown");
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("slot_press","0")),"owned countdown before state replacement");
            AbilityStorage.remove(player);var replacement=AbilityStorage.get(player);
            helper.assertTrue(replacement!=state&&!AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("railgun_abort","")),"old state timer cannot be cancelled as current replacement state");
            helper.assertTrue(player.getMainHandItem().getCount()==4,"stale cancellation never performs");
            AcademyGameplay.cancelRailgunCharge(player);
            helper.succeed();
        }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void retained_unmapped_flashing_direction_requires_exact_accepted_owner_context(GameTestHelper helper){
        try(var fixture=new Fixture(helper)){
            var player=fixture.player;var state=ready(player,"teleporter",Flashing.ID);state.presets.edit(0,0,Flashing.ID,id->true);
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("slot_press_token","0:601")),"actual authenticated Flashing start");
            long token=fixture.token("flashing_start",601);
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("preset_edit","0:0:")),"actual current preset unmaps Flashing default delegate");
            helper.assertTrue(Flashing.active(player)&&!state.presets.currentContains(Flashing.ID),"accepted mode survives unmapping");
            helper.assertFalse(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("flashing_direction","601:1:3")),"legacy slot-mapped route remains unchanged and rejects unmapping");
            String action="flashing_direction_owned";Vec3 before=player.position();double cp=state.cp;
            for(String value:List.of("601:0:1:3","0:"+token+":1:3","0601:"+token+":1:3","601:"+token+":0:3","601:"+token+":4097:3","601:"+token+":1:5","601:"+(token+1)+":1:3","602:"+token+":1:3"))
                helper.assertFalse(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request(action,value)),"owned context/sequence/key wire rejects "+value);
            helper.assertTrue(player.position().equals(before)&&state.cp==cp&&player.getPersistentData().getInt("ac_tpcount")==0,"rejected owned directions cannot move/spend/increment sequence");
            Vec3 expected=Flashing.destination(player,0,3);
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request(action,"601:"+token+":1:3")),"retained exact accepted mode can hop after preset unmapping");
            helper.assertTrue(player.position().distanceToSqr(expected)<1E-10&&Math.abs(state.cp-(cp-13))<1E-5&&player.getPersistentData().getInt("ac_tpcount")==1,"unchanged authoritative perform computes destination and consumes once");
            Vec3 after=player.position();double afterCp=state.cp;
            helper.assertFalse(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request(action,"601:"+token+":1:3")),"same owned direction sequence cannot replay");
            helper.assertTrue(player.position().equals(after)&&state.cp==afterCp,"replay cannot re-hop or debit");
            AbilityStorage.remove(player);var replacement=AbilityStorage.get(player);
            helper.assertTrue(replacement!=state&&!AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request(action,"601:"+token+":2:3")),"old accepted mode cannot hop through replacement ability state");
            helper.assertTrue(player.position().equals(after),"stale state hop has no relocation");
            helper.succeed();
        }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void pending_context_abort_uses_old_skill_when_original_slot_remaps_to_same_input(GameTestHelper helper){
        try(var fixture=new Fixture(helper)){
            var player=fixture.player;var state=ready(player,"vecmanip",VecDeviation.ID,VecReflection.ID);
            state.presets.edit(0,0,VecDeviation.ID,id->true);state.presets.edit(0,1,VecReflection.ID,id->true);
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("slot_press_token","0:1")),"original slot admits pending deviation input1");
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("slot_press_token","1:1")),"another same-category skill independently admits coincident input1");
            long reflection=fixture.token("vec_reflection_start",1);
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("preset_edit","0:1:")),"server unmaps old reflection delegate while context survives");
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request("preset_edit","0:0:vec_reflection")),"server remaps original deviation slot to reflection");
            helper.assertTrue(state.presets.currentSkill(0).equals(VecReflection.ID)&&VecDeviation.active(player)&&VecReflection.active(player),"two actual contexts survive server remap before pending cancellation arrives");
            String action="pending_context_abort";double cp=state.cp,overload=state.overload;
            for(String value:List.of("vec_deviation:0","vec_deviation:01","vec_deviation:2","plasma_cannon:1","vec_deviation:1:0"))
                helper.assertFalse(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request(action,value)),"strict bounded pending wire rejection "+value);
            helper.assertTrue(VecDeviation.active(player)&&VecReflection.active(player),"malformed/stale pending requests have no mapped-skill fallback");
            helper.assertTrue(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request(action,"vec_deviation:1")),"fixed original skill/input safely derives its actual server token");
            helper.assertTrue(!VecDeviation.active(player)&&VecReflection.active(player)&&state.activated&&state.cp==cp&&state.overload==overload&&state.cooldowns.isEmpty(),"only original pending deviation terminates; newly mapped same-input reflection remains live");
            helper.assertFalse(AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request(action,"vec_deviation:1")),"duplicate old pending cancel cannot redirect to reflection");
            helper.assertTrue(VecReflection.active(player)&&request(player,"vec_reflection:1:"+reflection),"retained reflection still has its independent strict accepted V identity");
            helper.succeed();
        }
    }

    private static final class CaptureConnection extends Connection {
        final List<CompoundTag> data=new ArrayList<>();
        CaptureConnection(){super(PacketFlow.SERVERBOUND);}
        @Override public void send(Packet<?> packet,PacketSendListener listener,boolean flush){
            if(packet instanceof ClientboundCustomPayloadPacket payload&&payload.payload() instanceof AcademyNetwork.ClientData message)data.add(message.data().copy());
            super.send(packet,listener,flush);
        }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;final CaptureConnection connection;final EmbeddedChannel channel;final ServerPlayer player;
        Fixture(GameTestHelper helper){
            this.helper=helper;var level=helper.getLevel();var server=level.getServer();
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"ACTargetContext"),false);
            player=new ServerPlayer(server,level,cookie.gameProfile(),cookie.clientInformation());connection=new CaptureConnection();channel=new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,player,cookie);
            helper.assertTrue(server.getPlayerList().getPlayer(player.getUUID())==player&&level.getEntity(player.getId())==player,"exact native PlayerList and Level owner admitted");
            var at=helper.absoluteVec(new Vec3(2.5,1,1.5));player.moveTo(at.x,at.y,at.z,0,0);player.setGameMode(GameType.SURVIVAL);player.setNoGravity(true);player.setInvulnerable(true);
        }
        long token(String kind,long input){return connection.data.stream().filter(tag->tag.getString("kind").equals(kind)&&tag.getLong("input")==input).mapToLong(tag->tag.getLong("token")).max().orElseThrow(()->new AssertionError("Missing actual start payload "+kind));}
        @Override public void close(){
            VecDeviation.remove(player);VecReflection.remove(player);StormWing.remove(player);Flashing.remove(player);BloodRetrograde.remove(player);
            AcademyGameplay.cancelRailgunCharge(player);AbilityStorage.remove(player);var players=helper.getLevel().getServer().getPlayerList();if(players.getPlayer(player.getUUID())==player)players.remove(player);else player.discard();channel.finishAndReleaseAll();
        }
    }
}

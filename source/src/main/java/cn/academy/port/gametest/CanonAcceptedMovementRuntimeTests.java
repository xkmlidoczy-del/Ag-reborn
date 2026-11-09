package cn.academy.port.gametest;

import cn.academy.port.core.SkillTuning;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.canon.CanonExpansionCombatA;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Native accepted-packet regression, not a separate client or keyboard claim.
 * The existing canon_media empty template is registered by CanonMediumRuntimeTests.
 * Positive momentum comes only from the original movement packet handler.
 */
@GameTestHolder("academy_canon_media")
@PrefixGameTestTemplate(false)
public final class CanonAcceptedMovementRuntimeTests {
    private static final String SKILL="aero_brake";
    private CanonAcceptedMovementRuntimeTests() {}

    @GameTest(template="empty",batch="canon_accepted_movement",timeoutTicks=100)
    public static void accepted_strafe_with_zero_raw_horizontal_velocity_brakes_and_pays(GameTestHelper h) {
        var f=new Fixture(h);
        try {
            Vec3 start=f.player.position(),wanted=start.add(.25,0,0);
            f.player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(wanted.x,wanted.y,wanted.z,true));
            near(h,f.player.position(),wanted,"ordinary original connection handler accepts the collision-free strafe");
            Vec3 accepted=f.player.getKnownMovement(),raw=f.player.getDeltaMovement();
            near(h,accepted,new Vec3(.25,0,0),"accepted client displacement is recorded as known movement");
            h.assertTrue(horizontalSquared(raw)<1e-20,"native raw horizontal velocity remains zero after accepted player movement");
            double cp=HighPowerResources.cp(f.player),nativeCp=AbilityStorage.get(f.player).cp;
            double cost=HighPowerGrowth.values(f.player).arcCost(),experience=AbilityStorage.get(f.player).exp(SKILL);
            h.assertTrue(CanonExpansionCombatA.cast(f.player,SKILL),"normal accepted strafe satisfies the unchanged brake speed gate");
            double factor = SkillTuning.number(SKILL, "horizontal_factor");
            Vec3 expected=new Vec3(accepted.x*factor,accepted.y,accepted.z*factor);
            near(h,f.player.getDeltaMovement(),expected,"brake writes exactly one tuned horizontal output from the accepted native motion");
            h.assertTrue(HighPowerResources.cp(f.player)<cp&&Math.abs(cp-HighPowerResources.cp(f.player)-cost)<1e-8,
                    "successful native action pays exactly its actual growth arc cost");
            h.assertTrue(AbilityStorage.get(f.player).cp<=nativeCp,"native CP ledger is not replenished by the cast");
            h.assertTrue(f.clock()>h.getLevel().getGameTime(),"successful action commits its real skill cooldown clock");
            h.assertTrue(AbilityStorage.get(f.player).exp(SKILL)>experience,"accepted motion awards actual skill training");
            h.assertFalse(f.busy(),"single action releases its transaction guard");
            h.succeed();
        } finally {f.close();}
    }

    @GameTest(template="empty",batch="canon_accepted_movement",timeoutTicks=100)
    public static void unacknowledged_teleport_move_proposal_is_not_brake_momentum(GameTestHelper h) {
        var f=new Fixture(h);
        try {
            Vec3 start=f.player.position(),proposal=start.add(.25,0,0);
            // A genuine server teleport deliberately remains unacknowledged. Vanilla
            // must ignore the following proposed move instead of accepting momentum.
            f.player.connection.teleport(start.x,start.y,start.z,0,0);
            f.player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(proposal.x,proposal.y,proposal.z,true));
            near(h,f.player.position(),start,"unacknowledged native teleport blocks the proposed displacement");
            h.assertTrue(horizontalSquared(f.player.getKnownMovement())<1e-20&&horizontalSquared(f.player.getDeltaMovement())<1e-20,
                    "an ignored proposal creates neither accepted nor raw horizontal momentum");
            double cp=HighPowerResources.cp(f.player),nativeCp=AbilityStorage.get(f.player).cp;
            double experience=AbilityStorage.get(f.player).exp(SKILL);long clock=f.clock();
            var cooldowns=Map.copyOf(AbilityStorage.get(f.player).cooldowns);var contexts=f.skillContexts();
            h.assertFalse(CanonExpansionCombatA.cast(f.player,SKILL),"stationary accepted state cannot brake an unaccepted client proposal");
            h.assertTrue(HighPowerResources.cp(f.player)==cp&&AbilityStorage.get(f.player).cp==nativeCp,
                    "refusal leaves both real resource ledgers unchanged");
            h.assertTrue(f.clock()==clock&&AbilityStorage.get(f.player).cooldowns.equals(cooldowns),
                    "refusal creates no resource clock or native cooldown");
            h.assertTrue(f.skillContexts().equals(contexts)&&!f.busy(),"refusal creates no skill lease/context and releases the transaction guard");
            h.assertTrue(AbilityStorage.get(f.player).exp(SKILL)==experience,"refusal awards no skill training");
            near(h,f.player.position(),start,"rejected cast never moves the authoritative player position");
            h.succeed();
        } finally {f.close();}
    }

    private static double horizontalSquared(Vec3 v){return v.x*v.x+v.z*v.z;}
    private static void near(GameTestHelper h,Vec3 actual,Vec3 expected,String why){
        h.assertTrue(actual.distanceToSqr(expected)<1e-18,why+" actual="+actual+" expected="+expected);
    }
    private static Field field(String name){
        try{var f=CanonExpansionCombatA.class.getDeclaredField(name);f.setAccessible(true);return f;}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    private static final class Fixture {
        final ServerPlayer player;final EmbeddedChannel channel;boolean closed;
        Fixture(GameTestHelper h){
            var world=h.getLevel();var id=UUID.randomUUID();
            var cookie=CommonListenerCookie.createInitial(new GameProfile(id,"CAM"+id.toString().substring(0,8)),false);
            player=new ServerPlayer(world.getServer(),world,cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);world.getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
            h.testInfo.addListener(new GameTestListener(){
                public void testStructureLoaded(GameTestInfo i){}
                public void testPassed(GameTestInfo i,GameTestRunner r){close();}
                public void testFailed(GameTestInfo i,GameTestRunner r){close();}
                public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}
            });
            player.setGameMode(GameType.SURVIVAL);
            Vec3 at=h.absoluteVec(new Vec3(4.5,3,4.5));
            for(int x=(BlockPos.containing(at).getX()-32)>>4;x<=(BlockPos.containing(at).getX()+32)>>4;x++)
                for(int z=(BlockPos.containing(at).getZ()-32)>>4;z<=(BlockPos.containing(at).getZ()+32)>>4;z++)world.getChunk(x,z);
            for(int x=2;x<=9;x++)for(int z=2;z<=9;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            player.connection.teleport(at.x,at.y,at.z,0,0);
            h.assertTrue(acknowledgeTeleports()>0,"normal client ACK consumes actual outbound teleport IDs");
            player.connection.resetPosition();
            player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(at.x,at.y,at.z,true));
            near(h,player.position(),at,"baseline zero-displacement native packet is accepted on the floor");
            h.assertTrue(player.onGround()&&horizontalSquared(player.getKnownMovement())<1e-20&&horizontalSquared(player.getDeltaMovement())<1e-20,
                    "both cases start at the same real grounded stationary state");
            var state=AbilityStorage.get(player);state.selectCategory("aero_hand");state.setLevel(5);
            for(var skill:SkillCatalog.REGISTERED)if(skill.category().equals("aero_hand"))state.learn(skill.id());
            state.activated=true;state.overloadFine=true;state.presets.edit(0,0,SKILL,state::learned);
            h.assertTrue(HighPowerCombat.enroll(player,5),"real connected LV5 resource ledger admitted");
            HighPowerResources.initializeBudget(player);AbilityStorage.save(player);
            h.assertTrue(HighPowerResources.ready(player)&&state.canUse(SKILL),"actual learned mapped action and finite real budget ready");
        }
        int acknowledgeTeleports(){
            channel.runPendingTasks();channel.flushOutbound();channel.runPendingTasks();int count=0;Object packet;
            while((packet=channel.readOutbound())!=null){
                try{if(packet instanceof ClientboundPlayerPositionPacket teleport){player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(teleport.getId()));count++;}}
                finally{ReferenceCountUtil.release(packet);}
            }
            return count;
        }
        long clock(){return player.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+SKILL);}
        boolean busy(){try{return ((Set<?>)field("BUSY").get(null)).contains(player);}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
        Map<String,Integer> skillContexts(){
            try{var result=new LinkedHashMap<String,Integer>();
                for(String name:new String[]{"BARRIERS","FALL_GUARDS","ALLY_GUARDS","MIND_LEASES"})result.put(name,((Map<?,?>)field(name).get(null)).size());
                return result;
            }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        }
        void close(){if(closed)return;closed=true;try{player.server.getPlayerList().remove(player);}finally{channel.finishAndReleaseAll();}}
    }
}

package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.canon.CanonSkillsCombatB;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerResources;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Connected-server tests for the water-source selector, using the canon media empty template. */
@GameTestHolder("academy_canon_media")
@PrefixGameTestTemplate(false)
public final class CanonWaterSelectorRuntimeTests {
    private CanonWaterSelectorRuntimeTests() {}

    @GameTest(template="empty", batch="canon_water_selector", timeoutTicks=100)
    public static void selected_real_water_is_paid_and_invalidated_without_harming_blocks(GameTestHelper h) {
        var world=h.getLevel(); UUID id=UUID.randomUUID();
        var cookie=CommonListenerCookie.createInitial(new GameProfile(id,"CW"+id.toString().substring(0,8)),false);
        var player=new ServerPlayer(world.getServer(),world,cookie.gameProfile(),cookie.clientInformation());
        var connection=new Connection(PacketFlow.SERVERBOUND); var channel=new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection); world.getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        h.testInfo.addListener(new GameTestListener(){
            boolean closed;
            private void close(){if(closed)return;closed=true;try{player.server.getPlayerList().remove(player);}finally{channel.finishAndReleaseAll();}}
            public void testStructureLoaded(GameTestInfo i){} public void testPassed(GameTestInfo i,GameTestRunner r){close();}
            public void testFailed(GameTestInfo i,GameTestRunner r){close();} public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}
        });
        player.setGameMode(GameType.SURVIVAL); player.getAbilities().instabuild=false;
        player.moveTo(h.absoluteVec(new Vec3(4.5,3,2.5)),0,30); player.setYHeadRot(0);
        for(int x=(player.blockPosition().getX()-32)>>4;x<=(player.blockPosition().getX()+32)>>4;x++)
            for(int z=(player.blockPosition().getZ()-32)>>4;z<=(player.blockPosition().getZ()+32)>>4;z++)world.getChunk(x,z);
        h.setBlock(new BlockPos(4,2,2),Blocks.STONE);
        var state=AbilityStorage.get(player); state.changeCategoryClassic("hydro_hand"); state.setLevel(5);
        state.learn("hydro_whip"); state.setActivateState(true);
        h.assertTrue(state.presets.edit(0,0,"hydro_whip",state::learned),"water whip is mapped");
        h.assertTrue(HighPowerCombat.enroll(player,5),"player exact ledger enrolled"); HighPowerResources.initializeBudget(player);
        player.setShiftKeyDown(true); double before=HighPowerResources.cp(player);
        h.assertFalse(CanonSkillsCombatB.cast(player,"hydro_whip"),"no targeted source cannot select");
        h.assertTrue(HighPowerResources.cp(player)==before,"failed selection does not charge CP");
        h.setBlock(new BlockPos(4,3,5),Blocks.WATER); h.setBlock(new BlockPos(3,3,2),Blocks.WATER);
        player.getAbilities().mayBuild=false;
        h.assertFalse(CanonSkillsCombatB.cast(player,"hydro_whip"),"no build permission denies selection");
        h.assertTrue(HighPowerResources.cp(player)==before,"denied selection does not charge CP");
        player.getAbilities().mayBuild=true;
        h.assertTrue(CanonSkillsCombatB.cast(player,"hydro_whip"),"aimed actual source is selected");
        h.assertTrue(HighPowerResources.cp(player)<before,"selection costs actual CP");
        h.assertBlockPresent(Blocks.WATER,new BlockPos(4,3,5)); h.assertBlockPresent(Blocks.WATER,new BlockPos(3,3,2));
        h.assertFalse(CanonSkillsCombatB.cast(player,"hydro_whip"),"selection respects its cooldown");
        player.setShiftKeyDown(false); player.setXRot(0);
        var target=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,7.5));
        target.setNoGravity(true);
        h.assertTrue(HighPowerCombat.enroll(target,5),"target ledger enrolled");
        BigDecimal health=new BigDecimal(HighPowerCombat.healthText(target));
        long start=world.getGameTime();
        // EmbeddedChannel is not a live client: its connection is not advanced by the normal
        // network loop, which normally calls ServerPlayer.doTick and emits PlayerTickEvent.Post.
        // Run one real player tick on each advancing server tick, as other native fixtures do.
        for (int tick=1;tick<=12;tick++) h.runAfterDelay(tick,player::doTick);
        h.runAfterDelay(12,()->{
            // Hold the fixture's aimed target still during the real cooldown; otherwise an unsupported
            // zombie falls outside the horizontal ray before the fallback attack is attempted.
            target.setPos(h.absoluteVec(new Vec3(4.5,3,7.5))); target.setDeltaMovement(Vec3.ZERO);
            player.setXRot(0); player.setShiftKeyDown(false);
            h.setBlock(new BlockPos(4,3,5),Blocks.AIR);
            h.assertTrue(world.getGameTime()-start>=10,"real server time passed the selection cooldown");
            h.assertTrue(state.canUse("hydro_whip"),"selection cooldown has actually expired");
            h.assertTrue(world.getFluidState(h.absolutePos(new BlockPos(3,3,2))).isSource(),"fallback remains a real water source");
            var aim=cn.academy.port.skill.ClassicRaytrace.living(player,cn.academy.port.core.RangedSkillRanges.fixed("hydro_whip"),ClipContext.Fluid.NONE);
            h.assertTrue(aim instanceof EntityHitResult hit && hit.getEntity()==target,"fallback target remains in the skill ray");
            double paid=HighPowerResources.cp(player);
            h.assertFalse(CanonSkillsCombatB.cast(player,"hydro_whip"),"lost selected source refuses instead of silently switching");
            h.assertTrue(HighPowerResources.cp(player)==paid,"invalid selection does not charge CP");
            h.assertTrue(CanonSkillsCombatB.cast(player,"hydro_whip"),"next cast restores automatic nearby source selection");
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(target)).compareTo(health)<0,"fallback source powers exact water attack");
            h.assertBlockPresent(Blocks.WATER,new BlockPos(3,3,2)); target.discard(); h.succeed();
        });
    }
}

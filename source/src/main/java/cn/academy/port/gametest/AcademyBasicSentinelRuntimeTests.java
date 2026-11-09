/* BasicMRContext adaptation/regression, AcademyCraft 1.0.7, Copyright Lambda Innovation,
 * 2013-2016, GPLv3 and additional upstream notices; see NOTICE. */
package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillBlockDestroyEvent;
import cn.academy.port.skill.MineRayBasic;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

/** Dedicated disposable negative-world footprint; compiled only in the isolated staging lane.
 * The main lane must run this in a fresh disposable GameTest world and its own batch. */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class AcademyBasicSentinelRuntimeTests {
    private static final String TEMPLATE = "basic_sentinel_empty";
    private static final BlockPos NORTH_WEST = new BlockPos(-8, -5, -8);
    private static final BlockPos TARGET = new BlockPos(-1, -1, -1);
    private static final AABB EXPECTED_BOUNDS = new AABB(-8, -5, -8, 8, 3, 8);
    private AcademyBasicSentinelRuntimeTests() {}

    @SubscribeEvent
    public static void install(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var tag = new CompoundTag();
        var size = new ListTag();
        size.add(IntTag.valueOf(16)); size.add(IntTag.valueOf(8)); size.add(IntTag.valueOf(16));
        tag.put("size", size);
        var blocks = new ListTag(); var first = new CompoundTag(); var pos = new ListTag();
        pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0));
        first.put("pos", pos); first.putInt("state", 0); blocks.add(first); tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        var palette = new ListTag(); var air = new CompoundTag(); air.putString("Name", "minecraft:air");
        palette.add(air); tag.put("palette", palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template=TEMPLATE, batch="academy_basic_sentinel", timeoutTicks=20)
    public static void real_negative_sentinel_denial_capture_reset_and_final_protection(GameTestHelper helper) {
        helper.assertValueEqual(helper.getTestRotation(), Rotation.NONE, "sentinel fixture uses unrotated declared footprint");
        helper.assertTrue(EXPECTED_BOUNDS.contains(Vec3.atCenterOf(TARGET)), "planned relocated footprint contains sentinel cell");
        helper.assertFalse(helper.getLevel().isOutsideBuildHeight(TARGET), "modern native world permits negative-Y target");
        // Native GameTest preparation clears/encases its relocated fixture and forces its chunks.
        // This is fixture preparation, not an unscoped target write from the original positive bounds.
        helper.testInfo.setNorthWestCorner(NORTH_WEST);
        helper.testInfo.prepareTestStructure();
        helper.assertValueEqual(helper.testInfo.getStructureBounds(), EXPECTED_BOUNDS,
                "actual relocated native declared bounds match the planned footprint");
        helper.assertTrue(helper.testInfo.getStructureBounds().contains(Vec3.atCenterOf(TARGET)),
                "real (-1,-1,-1) is inside actual declared test footprint before any target mutation");

        var fixture = new Fixture(helper);
        var player = fixture.player;
        double eyeY = -.5;
        player.moveTo(-.5, eyeY - player.getEyeHeight(), -4.5, 0, 0);
        player.setYHeadRot(0); player.setNoGravity(true); player.getAbilities().instabuild = false;
        helper.assertTrue(EXPECTED_BOUNDS.contains(player.position()) && EXPECTED_BOUNDS.contains(player.getEyePosition()),
                "listener-owned player and eye remain inside declared footprint");
        var eye = player.getEyePosition(); var end = eye.add(0, 0, 10);
        helper.assertTrue(EXPECTED_BOUNDS.contains(end), "entire native trace is inside declared footprint");
        var state = AbilityStorage.get(player);
        state.selectCategory("meltdowner"); state.setLevel(3); state.learn(MineRayBasic.ID);
        state.experience.put(MineRayBasic.ID, 1D); state.activated = true;
        fixture.hook.sourceDenied = true;
        helper.getLevel().setBlock(TARGET, Blocks.DIRT.defaultBlockState(), 3);
        var hit = helper.getLevel().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        helper.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(TARGET),
                "actual native ray, not a synthetic session World, reaches the sentinel-coordinate dirt");
        double cp = state.cp;
        helper.assertTrue(MineRayBasic.start(player), "native Basic hold starts");
        for (int tick = 1; tick <= 8; ++tick) {
            int now = tick;
            helper.runAtTickTime(tick, () -> {
                if (fixture.closed) return;
                if (now == 3) fixture.hook.sourceDenied = false;
                if (now == 5) fixture.hook.nativeDenied = true;
                if (now == 6) fixture.hook.nativeDenied = false;
                if (now == 8) helper.getLevel().setBlock(TARGET, Blocks.BEDROCK.defaultBlockState(), 3);
                MineRayBasic.tick(player);
                if (now <= 2) {
                    helper.assertValueEqual(fixture.hook.sourceProbes, now,
                            "every uncaptured sentinel-coordinate hit requests native source permission");
                    helper.assertValueEqual(fixture.hook.nativeProbes, 0, "source denial short-circuits later native permission");
                    helper.assertTrue(helper.getLevel().getBlockState(TARGET).is(Blocks.DIRT), "denied sentinel dirt stays intact");
                }
                if (now == 3) {
                    helper.assertValueEqual(fixture.hook.sourceProbes, 3, "allowing sentinel hit acquires on third source probe");
                    helper.assertValueEqual(fixture.hook.nativeProbes, 1, "first permitted acquisition performs native permission");
                    helper.assertTrue(helper.getLevel().getBlockState(TARGET).is(Blocks.DIRT), "first acquisition has no .4 subtraction");
                }
                if (now == 4) {
                    helper.assertTrue(helper.getLevel().getBlockState(TARGET).is(Blocks.DIRT),
                            "one .4 progress subtraction leaves original .5 dirt intact, proving prior tick did not subtract");
                    helper.assertValueEqual(fixture.hook.sourceProbes, 3, "progress does not reacquire ambiguous sentinel target");
                }
                if (now == 5) {
                    helper.assertTrue(helper.getLevel().getBlockState(TARGET).is(Blocks.DIRT), "final native BreakEvent denial is preserved");
                    helper.assertValueEqual(fixture.hook.nativeProbes, 2, "final break rechecks modern native protection");
                    near(helper, state.levelExperience, .0005F, "original session still trains after denied final adapter attempt");
                }
                if (now == 6) {
                    helper.assertValueEqual(fixture.hook.sourceProbes, 4, "post-break reset reacquires real sentinel coordinate");
                    helper.assertTrue(helper.getLevel().getBlockState(TARGET).is(Blocks.DIRT), "reacquisition again has no subtraction");
                }
                if (now == 7) helper.assertTrue(helper.getLevel().getBlockState(TARGET).is(Blocks.DIRT),
                        "one second-cycle subtraction still leaves dirt intact");
                if (now == 8) {
                    helper.assertTrue(helper.getLevel().isEmptyBlock(TARGET),
                            "source captured .5 hardness still breaks a replacement bedrock block without rechecking hardness/harvest");
                    helper.assertValueEqual(fixture.hook.sourceProbes, 4, "replacement progress uses captured acquisition, not a new source probe");
                    helper.assertValueEqual(fixture.hook.nativeProbes, 4, "both cycles preserve acquisition and final native protection probes");
                    near(helper, state.levelExperience, 2 * (double).0005F, "both source break attempts train");
                    near(helper, state.cp, cp - 8 * 7, "all eight native ticks pay captured master CP7");
                    helper.assertTrue(MineRayBasic.release(player), "native hold ends normally");
                    helper.assertValueEqual(state.cooldowns.get(MineRayBasic.ID), 20, "master captured cooldown remains20");
                    helper.succeed();
                }
            });
        }
    }

    private static void near(GameTestHelper helper, double value, double expected, String why) {
        helper.assertTrue(Math.abs(value - expected) < 1E-7, why + " actual=" + value + " expected=" + expected);
    }
    private static final class Fixture {
        final FakePlayer player;
        final Hook hook;
        boolean closed;
        Fixture(GameTestHelper helper) {
            player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[AC-Basic-Sentinel]"));
            hook = new Hook(player);
            helper.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo nextInfo, GameTestRunner runner) { close(); }
            });
            NeoForge.EVENT_BUS.register(hook);
        }
        void close() {
            if (closed) return;
            closed = true;
            NeoForge.EVENT_BUS.unregister(hook);
            MineRayBasic.remove(player); AbilityStorage.remove(player); player.discard();
        }
    }
    public static final class Hook {
        final FakePlayer owner;
        boolean sourceDenied, nativeDenied;
        int sourceProbes, nativeProbes;
        Hook(FakePlayer owner) { this.owner = owner; }
        @SubscribeEvent public void source(SkillBlockDestroyEvent event) {
            if (event.player == owner && event.skill.equals("meltdowner.mine_ray_basic") && event.position.equals(TARGET)) {
                ++sourceProbes;
                if (sourceDenied) event.setCanceled(true);
            }
        }
        @SubscribeEvent public void nativeBreak(BlockEvent.BreakEvent event) {
            if (event.getPlayer() == owner && event.getPos().equals(TARGET)) {
                ++nativeProbes;
                if (nativeDenied) event.setCanceled(true);
            }
        }
    }
}

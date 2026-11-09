package cn.academy.port.qa;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;

/** Runs the ten named physical GameTests near the origin, outside the historical far-coordinate skip branches. */
final class SableNativeQa {
    private static final Set<String> EXPECTED = Set.of(
        "preview_grab_hover_and_land_conserves_every_block",
        "a_distant_lock_dashes_to_the_structure_and_grabs_it_at_the_hand",
        "a_blocked_dash_gives_up_for_free",
        "sneak_tap_cancels_a_dash_for_free",
        "landing_into_occupied_space_overwrites_nothing",
        "terrain_and_protected_structures_are_refused_for_free",
        "a_thrown_structure_hits_each_target_once",
        "the_lock_rings_a_target_with_palette_blocks_and_releases_it",
        "a_struck_target_is_locked_in_pieces_of_the_structure_that_later_break_away",
        "two_players_hold_independent_bodies_at_once");
    private static volatile MultipleTestTracker tracker;
    private static volatile Throwable failure;
    private static volatile List<GameTestInfo> tests;
    private static boolean started;
    private static long startedAt;
    private SableNativeQa() {}

    static boolean step(Minecraft mc, String action) {
        if (failure != null) throw new IllegalStateException("Sable native QA failed", failure);
        if (action.equals("start")) {
            if (started) throw new IllegalStateException("native suite already started");
            started = true;
            startedAt = mc.level.getGameTime();
            mc.getSingleplayerServer().execute(() -> {
                try {
                    net.neoforged.neoforge.gametest.GameTestHooks.registerGametests();
                    var level = mc.getSingleplayerServer().overworld();
                    var functions = GameTestRegistry.getAllTestFunctions();
                    if (functions.size() != EXPECTED.size()) throw new IllegalStateException("expected exactly the ten Sable tests: " + functions.size());
                    var runner = GameTestRunner.Builder.fromBatches(GameTestBatchFactory.fromTestFunction(functions, level), level)
                            .newStructureSpawner(new StructureGridSpawner(new BlockPos(64, -59, 64), 8, false)).build();
                    tests = runner.getTestInfos();
                    if (tests.size()!=EXPECTED.size() || !tests.stream().map(GameTestInfo::getTestName).collect(Collectors.toSet()).equals(EXPECTED))
                        throw new IllegalStateException("Sable native discovery names differ: "+tests.stream().map(GameTestInfo::getTestName).toList());
                    tracker = new MultipleTestTracker(tests);
                    runner.start();
                } catch (Throwable error) { failure = error; }
            });
            return true;
        }
        if (!action.equals("done")) throw new IllegalArgumentException(action);
        if (mc.level.getGameTime() - startedAt > 900) throw new IllegalStateException("native Sable suite timed out");
        var current = tracker;
        if (current == null || !current.isDone()) return false;
        if (current.getTotalCount() != EXPECTED.size() || current.hasFailedRequired() || current.hasFailedOptional())
            throw new IllegalStateException("native Sable suite failed: " + current + " " + current.getFailedRequired().stream()
                    .map(t -> t.getTestName() + ": " + t.getError()).toList());
        for (var test : tests) {
            var pos = test.getStructureBlockPos();
            if (Math.abs(pos.getX()) > 1000 || Math.abs(pos.getZ()) > 1000 || !test.hasSucceeded())
                throw new IllegalStateException("near-origin native test did not succeed: " + test.getTestName() + " " + pos);
            LogUtils.getLogger().info("QA_SABLE_NATIVE_PASS test={} origin={}", test.getTestName(), pos);
        }
        LogUtils.getLogger().info("QA_SABLE_NATIVE_ALL_PASS count=10 exactNames=true farCoordinateSkip=false");
        return true;
    }
}

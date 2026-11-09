package cn.academy.port.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Optional diagnostic namespace delegates the unchanged strict native far regression.
 * Default highPowerGameTest still runs its full academy_highpower suite. */
@GameTestHolder("academy_highpower_far241")
@PrefixGameTestTemplate(false)
public final class HighPowerParticleLaminateFarFocusedTests {
    private HighPowerParticleLaminateFarFocusedTests() {}
    @GameTest(template="highpower_empty",batch="particle_laminate_far_focused",timeoutTicks=30)
    public static void exact_r3_far_coordinates(GameTestHelper helper) {
        HighPowerParticleLaminateRuntimeTests.particle_laminate_three_far_targets_complete_all_native_guards_within_shared_work_limit(helper);
    }
}

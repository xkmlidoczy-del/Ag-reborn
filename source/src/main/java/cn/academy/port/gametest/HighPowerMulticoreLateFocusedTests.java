package cn.academy.port.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Optional focused namespace; delegates the same strict test used by the complete native suite. */
@GameTestHolder("academy_highpower_late251")
@PrefixGameTestTemplate(false)
public final class HighPowerMulticoreLateFocusedTests {
    private HighPowerMulticoreLateFocusedTests() {}
    @GameTest(template="highpower_empty",batch="multicore_late_absorption_focused",timeoutTicks=250)
    public static void late_absorption_cannot_commit_stale_emission(GameTestHelper helper) {
        HighPowerMulticoreCanopyDamageRuntimeTests.absorption_reduction_callbacks_cannot_spend_stale_health_or_absorption_after_native_pre(helper);
    }
    @GameTest(template="highpower_empty",batch="multicore_skill_callback_focused",timeoutTicks=260)
    public static void skill_callback_retains_full_target_hp(GameTestHelper helper) {
        HighPowerMulticoreCanopyDamageRuntimeTests.skill_callbacks_cannot_move_retarget_reteam_or_change_a_paid_segment(helper);
    }
    @GameTest(template="highpower_empty",batch="multicore_incoming_callback_focused",timeoutTicks=260)
    public static void incoming_callback_retains_full_target_hp(GameTestHelper helper) {
        HighPowerMulticoreCanopyDamageRuntimeTests.incoming_callbacks_cannot_move_retarget_reteam_or_change_a_paid_segment(helper);
    }
    @GameTest(template="highpower_empty",batch="multicore_frozen_slots_focused",timeoutTicks=60)
    public static void canceled_and_moved_slots_retain_their_allocations(GameTestHelper helper) {
        HighPowerMulticoreCanopyDamageRuntimeTests.late_canceled_and_moved_slots_never_redistribute_or_adopt_replacement_bodies(helper);
    }
}

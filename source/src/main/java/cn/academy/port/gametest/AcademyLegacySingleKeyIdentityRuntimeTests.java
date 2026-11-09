/* Isolated M37 native identity fixtures. GPLv3; see NOTICE. */
package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.core.LegacySingleKeyProtocol;
import cn.academy.port.core.LegacySingleKeyProtocol.AcceptedRequest;
import cn.academy.port.core.LegacySingleKeyProtocol.PendingRequest;
import cn.academy.port.core.LegacySingleKeyProtocol.Skill;
import cn.academy.port.core.LegacySingleKeyProtocol.StartRequest;
import cn.academy.port.skill.CurrentCharging;
import cn.academy.port.skill.Flashing;
import cn.academy.port.skill.MagManip;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Registered native candidates using actual admitted players and production outbound identities.
 * Explicit skill/maxima/item setup is a fixture grant. Tests call requestFromClient directly;
 * they do not prove sockets, the request codec, a real client/source scheduler, or rendering.
 * All checks run synchronously without synthetic ticks or invented six-skill physical targets.
 */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
public final class AcademyLegacySingleKeyIdentityRuntimeTests {
    private static final String TEMPLATE = "runtime_empty", BATCH = "academy_legacy_singlekey";
    private AcademyLegacySingleKeyIdentityRuntimeTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void actual_sync_issues_positive_epoch_and_preserves_exact_tuple(GameTestHelper helper) {
        try (var fixture = new LegacySingleKeyNativeFixture(helper)) {
            var player = fixture.player;
            var state = ready(player, "electromaster", CurrentCharging.ID);
            var before = AbilityStorage.encode(state);
            var connection = player.connection;
            var world = player.serverLevel();
            long first = fixture.syncEpoch(player), second = fixture.syncEpoch(player);
            helper.assertTrue(first > 0 && second == first, "repeat actual sync retains positive issued epoch");
            helper.assertTrue(player.connection == connection && player.serverLevel() == world
                            && AbilityStorage.get(player) == state && world.getEntity(player.getId()) == player,
                    "repeat sync retains actual actor connection world and state references");
            helper.assertValueEqual(LegacySingleKeyOwnerEpoch.peek(player, connection, world, state), first,
                    "captured actual sync epoch equals existing exact owner tuple");
            helper.assertTrue(AbilityStorage.encode(state).equals(before), "sync spends nothing and does not mutate ability state");
            helper.assertFalse(CurrentCharging.active(player), "epoch sync does not create a skill hold");
            helper.succeed();
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void mapped_positive_charging_captures_token_and_pays_start_once(GameTestHelper helper) {
        try (var fixture = new LegacySingleKeyNativeFixture(helper)) {
            var player = fixture.player;
            var state = ready(player, "electromaster", CurrentCharging.ID);
            bind(helper, player, CurrentCharging.ID);
            long epoch = fixture.syncEpoch(player);
            var start = charging(player, 1, epoch);
            double cp = state.cp, overload = state.overload;
            helper.assertTrue(request(player, LegacySingleKeyProtocol.PRESS, start.wire()), "real mapped positive Charging start accepted");
            var accepted = fixture.accepted(Skill.CHARGING, 1);
            helper.assertTrue(CurrentCharging.active(player) && accepted.actorId() == player.getId()
                            && accepted.ownerEpoch() == epoch && accepted.input() == 1 && accepted.token() > 0,
                    "actual production start supplies exact accepted owner input epoch and positive server token");
            near(helper, state.cp, cp, "source start costs zero CP");
            near(helper, state.overload, overload + 65, "source novice start costs 65 overload");
            helper.assertTrue(state.exp(CurrentCharging.ID) == 0 && state.cooldowns.isEmpty()
                            && player.getMainHandItem().is(Items.STICK) && player.getMainHandItem().getCount() == 1,
                    "unsupported native item is retained and no unticked EXP cooldown or energy transfer is invented");
            helper.assertFalse(request(player, LegacySingleKeyProtocol.PRESS, start.wire()), "duplicate physical input cannot pay or allocate another hold");
            helper.assertValueEqual(fixture.payloads("charging_start"), 1, "duplicate press emits no second accepted start");
            var wrongToken = new AcceptedRequest(Skill.CHARGING, 1, player.getId(), epoch, accepted.token() + 1);
            helper.assertFalse(request(player, LegacySingleKeyProtocol.RELEASE, wrongToken.wire()), "wrong accepted token cannot release live Charging");
            helper.assertTrue(CurrentCharging.active(player), "rejected token preserves actual live hold");
            near(helper, state.cp, cp, "duplicate and mismatched terminal spend no CP");
            near(helper, state.overload, overload + 65, "duplicate and mismatched terminal spend no extra overload");
            helper.assertTrue(fixture.terminal(LegacySingleKeyProtocol.RELEASE, accepted), "captured accepted release terminates exact hold");
            helper.assertFalse(CurrentCharging.active(player), "accepted release removes real hold");
            helper.assertFalse(fixture.terminal(LegacySingleKeyProtocol.RELEASE, accepted), "accepted release replay is inert");
            helper.assertFalse(request(player, LegacySingleKeyProtocol.PRESS, start.wire()), "retired input cannot restart after accepted release");
            helper.assertTrue(request(player, LegacySingleKeyProtocol.PRESS, charging(player, 2, epoch).wire()), "fresh physical input remains usable");
            var next = fixture.accepted(Skill.CHARGING, 2);
            helper.assertTrue(next.token() != accepted.token(), "fresh hold has a distinct actual server token");
            helper.assertTrue(fixture.terminal(LegacySingleKeyProtocol.ABORT, next), "fresh accepted hold independently aborts");
            near(helper, state.cp, cp, "two source starts and terminals remain zero CP");
            near(helper, state.overload, overload + 130, "exactly two accepted starts pay 65 each without refund");
            helper.succeed();
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void wrong_and_zero_epochs_preserve_state_nonce_and_flashing_across_feedback_sync(GameTestHelper helper) {
        try (var fixture = new LegacySingleKeyNativeFixture(helper); var flashing = new LegacySingleKeyNativeFixture(helper)) {
            var player = fixture.player;
            var state = ready(player, "electromaster", CurrentCharging.ID);
            bind(helper, player, CurrentCharging.ID);
            LegacySingleKeyOwnerEpoch.forget(player);
            var before = AbilityStorage.encode(state);
            helper.assertValueEqual(peek(player), 0L, "fixture removes prior login issuance before unissued request check");
            helper.assertFalse(request(player, LegacySingleKeyProtocol.PRESS, charging(player, 1, 1).wire()), "unissued positive epoch denied");
            long feedbackEpoch = peek(player);
            helper.assertTrue(feedbackEpoch > 0, "retained normal feedback sync issues a positive epoch after rejected unissued preflight");
            helper.assertFalse(request(player, LegacySingleKeyProtocol.PRESS, "0:c:1:" + player.getId() + ":0"), "zero epoch start denied by actual parser");
            helper.assertTrue(peek(player) == feedbackEpoch && CurrentCharging.canAcceptInput(player, 1),
                    "rejected inputs preserve the feedback-issued epoch and never retire input1");
            helper.assertTrue(!CurrentCharging.active(player) && fixture.payloads("charging_start") == 0
                            && AbilityStorage.encode(state).equals(before),
                    "invalid owner epochs create no accepted context and consume no state");
            long epoch = fixture.syncEpoch(player);
            helper.assertValueEqual(epoch, feedbackEpoch, "explicit actual sync captures the same epoch issued by ordinary rejection feedback");
            helper.assertTrue(request(player, LegacySingleKeyProtocol.PRESS, charging(player, 1, epoch).wire()), "same previously rejected input1 accepts the actual synced epoch");
            var accepted = fixture.accepted(Skill.CHARGING, 1);
            var paid = AbilityStorage.encode(state);
            var wrong = new AcceptedRequest(Skill.CHARGING, 1, player.getId(), epoch + 1, accepted.token());
            helper.assertFalse(request(player, LegacySingleKeyProtocol.ABORT, wrong.wire()), "wrong epoch accepted terminal denied");
            helper.assertFalse(request(player, LegacySingleKeyProtocol.ABORT, "c:1:" + player.getId() + ":0:" + Long.toString(accepted.token(), 36)), "zero epoch accepted terminal denied");
            helper.assertFalse(request(player, LegacySingleKeyProtocol.PENDING_ABORT, new PendingRequest(Skill.CHARGING, 1, player.getId(), epoch + 1).wire()), "wrong epoch pending terminal denied");
            helper.assertFalse(request(player, LegacySingleKeyProtocol.PENDING_ABORT, "c:1:" + player.getId() + ":0"), "zero epoch pending terminal denied");
            helper.assertTrue(CurrentCharging.active(player) && peek(player) == epoch && AbilityStorage.encode(state).equals(paid),
                    "all rejected terminal epochs retain issued tuple paid state and live hold");
            helper.assertTrue(fixture.terminal(LegacySingleKeyProtocol.ABORT, accepted), "valid accepted terminal remains usable after epoch denials");

            var other = flashing.player;
            var flashState = ready(other, "teleporter", Flashing.ID);
            bind(helper, other, Flashing.ID);
            helper.assertTrue(request(other, "slot_press_token", "0:1") && Flashing.active(other), "actual existing Flashing ingress supplies a live independent mode");
            LegacySingleKeyOwnerEpoch.forget(other);
            var flashBefore = AbilityStorage.encode(flashState);
            helper.assertValueEqual(peek(other), 0L, "Flashing control has no issued single-key epoch before invalid preflight");
            helper.assertFalse(request(other, LegacySingleKeyProtocol.PRESS, charging(other, 1, 1).wire()), "unissued Charging owner request denied while Flashing is live");
            long flashFeedbackEpoch = peek(other);
            helper.assertTrue(flashFeedbackEpoch > 0, "normal rejection feedback issues a single-key epoch without touching live Flashing");
            helper.assertFalse(request(other, LegacySingleKeyProtocol.PRESS, "0:c:1:" + other.getId() + ":0"), "zero owner request denied while Flashing is live");
            helper.assertTrue(Flashing.active(other) && peek(other) == flashFeedbackEpoch && flashing.payloads("flashing_end") == 0
                            && AbilityStorage.encode(flashState).equals(flashBefore),
                    "rejected single-key preflight preserves live Flashing and resources while normal feedback sync retains its issued epoch");
            helper.assertValueEqual(flashing.syncEpoch(other), flashFeedbackEpoch, "explicit Flashing owner sync retains ordinary feedback issuance");
            helper.assertTrue(request(other, "slot_abort_token", "0:1") && !Flashing.active(other), "existing correctly owned Flashing terminal remains functional");
            helper.succeed();
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void pending_terminals_end_original_input_and_late_acceptance_cannot_revive(GameTestHelper helper) {
        try (var fixture = new LegacySingleKeyNativeFixture(helper); var beforeStart = new LegacySingleKeyNativeFixture(helper)) {
            var player = fixture.player;
            var state = ready(player, "electromaster", CurrentCharging.ID);
            bind(helper, player, CurrentCharging.ID);
            long epoch = fixture.syncEpoch(player);
            var start = charging(player, 1, epoch);
            helper.assertTrue(request(player, LegacySingleKeyProtocol.PRESS, start.wire()), "actual server admits pending physical input1");
            var pending = new PendingRequest(Skill.CHARGING, start.input(), start.actorId(), start.ownerEpoch());
            var paid = AbilityStorage.encode(state);
            helper.assertTrue(CurrentCharging.active(player) && fixture.payloads("charging_start") == 1, "production acceptance exists before fixture consumes its accepted token");
            helper.assertFalse(request(player, LegacySingleKeyProtocol.PENDING_ABORT, "c:0:" + player.getId() + ":" + Long.toString(epoch, 36)), "pending input0 is no wildcard");
            helper.assertFalse(request(player, LegacySingleKeyProtocol.PENDING_ABORT, pending.wire() + ":1"), "pending role rejects an extra accepted token field");
            helper.assertTrue(CurrentCharging.active(player) && AbilityStorage.encode(state).equals(paid), "malformed pending roles preserve paid original hold");
            helper.assertTrue(request(player, LegacySingleKeyProtocol.PENDING_ABORT, pending.wire()), "token-free exact pending abort terminates original input");
            helper.assertFalse(CurrentCharging.active(player), "pending abort removes actual hold before acceptance is consumed");
            var lateAccepted = fixture.accepted(Skill.CHARGING, 1);
            helper.assertFalse(fixture.terminal(LegacySingleKeyProtocol.RELEASE, lateAccepted), "late captured accepted release after pending abort cannot revive");
            helper.assertFalse(fixture.terminal(LegacySingleKeyProtocol.ABORT, lateAccepted), "late captured accepted abort after pending abort is inert");
            helper.assertFalse(request(player, LegacySingleKeyProtocol.PENDING_RELEASE, pending.wire()), "duplicate pending terminal is inert");
            helper.assertFalse(request(player, LegacySingleKeyProtocol.PRESS, start.wire()), "pending-cancelled physical input cannot restart");
            helper.assertTrue(!CurrentCharging.active(player) && AbilityStorage.encode(state).equals(paid), "late callbacks and retired start preserve cancellation without refund or extra spend");
            var next = charging(player, 2, epoch);
            helper.assertTrue(request(player, LegacySingleKeyProtocol.PRESS, next.wire()), "new physical input starts independently after pending cancellation");
            helper.assertFalse(request(player, LegacySingleKeyProtocol.PENDING_ABORT, pending.wire()), "old pending abort cannot terminate newer input2");
            helper.assertTrue(CurrentCharging.active(player), "newer hold survives old pending replay");
            helper.assertTrue(request(player, LegacySingleKeyProtocol.PENDING_RELEASE, new PendingRequest(Skill.CHARGING, next.input(), next.actorId(), next.ownerEpoch()).wire()), "exact pending key-up independently terminates input2");
            helper.assertFalse(CurrentCharging.active(player), "pending release removes only its matching hold");
            near(helper, state.overload, 130, "two accepted pending starts retain exact paid overload without refund");
            near(helper, state.cp, 20000, "pending callbacks spend no CP without world ticks");

            // Separate actual owner preserves the original post-start input1/input2 assertions above.
            var awaiting = beforeStart.player;
            var awaitingState = ready(awaiting, "electromaster", CurrentCharging.ID);
            bind(helper, awaiting, CurrentCharging.ID);
            long awaitingEpoch = beforeStart.syncEpoch(awaiting);
            var delayedStart = charging(awaiting, 1, awaitingEpoch);
            var earlyPending = new PendingRequest(Skill.CHARGING, delayedStart.input(), delayedStart.actorId(), delayedStart.ownerEpoch());
            var untouched = AbilityStorage.encode(awaitingState);
            helper.assertFalse(CurrentCharging.active(awaiting), "second admitted owner has no hold before early pending input1");
            helper.assertTrue(request(awaiting, LegacySingleKeyProtocol.PENDING_ABORT, earlyPending.wire()), "exact actual issued-owner pending input1 retires before server start");
            System.out.println("M38_PENDING_OWNER_DIAGNOSTIC actor=" + awaiting.getId()
                    + " receivedStarts=" + beforeStart.payloads("charging_start")
                    + " ownStarts=" + beforeStart.payloads("charging_start", awaiting)
                    + " active=" + CurrentCharging.active(awaiting)
                    + " sameEpoch=" + (peek(awaiting) == awaitingEpoch)
                    + " resourcesExact=" + AbilityStorage.encode(awaitingState).equals(untouched));
            helper.assertTrue(!CurrentCharging.active(awaiting) && peek(awaiting) == awaitingEpoch
                            && beforeStart.payloads("charging_start", awaiting) == 0 && AbilityStorage.encode(awaitingState).equals(untouched),
                    "early pending acknowledgement neither allocates its own hold nor changes resources or issued epoch");
            helper.assertFalse(request(awaiting, LegacySingleKeyProtocol.PRESS, delayedStart.wire()), "late server start1 cannot revive the already retired pending input");
            helper.assertTrue(!CurrentCharging.active(awaiting) && AbilityStorage.encode(awaitingState).equals(untouched), "late rejected start1 consumes nothing");
            helper.assertTrue(request(awaiting, LegacySingleKeyProtocol.PRESS, charging(awaiting, 2, awaitingEpoch).wire()), "actual current-owner input2 starts after before-start input1 retirement");
            var accepted2 = beforeStart.accepted(Skill.CHARGING, 2);
            var paid2 = AbilityStorage.encode(awaitingState);
            helper.assertFalse(request(awaiting, LegacySingleKeyProtocol.PENDING_ABORT, earlyPending.wire()), "replayed early input1 pending cannot terminate accepted input2");
            helper.assertTrue(CurrentCharging.active(awaiting) && accepted2.input() == 2 && accepted2.ownerEpoch() == awaitingEpoch
                            && AbilityStorage.encode(awaitingState).equals(paid2),
                    "correct input2 hold and captured identity survive early pending replay");
            helper.assertTrue(beforeStart.terminal(LegacySingleKeyProtocol.ABORT, accepted2), "actual input2 accepted identity remains independently terminable");
            near(helper, awaitingState.overload, 65, "only positive input2 pays source start65 after earlier pending retirement");
            near(helper, awaitingState.cp, 20000, "before-start retirement and correct input2 callbacks spend zero CP");
            helper.succeed();
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void official_same_id_shared_connection_respawn_rejects_old_owner_after_new_input1(GameTestHelper helper) {
        try (var fixture = new LegacySingleKeyNativeFixture(helper)) {
            var original = fixture.player;
            ready(original, "electromaster", CurrentCharging.ID);
            bind(helper, original, CurrentCharging.ID);
            long oldEpoch = fixture.syncEpoch(original);
            var oldStart = charging(original, 1, oldEpoch);
            var oldFreshStart = charging(original, 2, oldEpoch);
            helper.assertTrue(request(original, LegacySingleKeyProtocol.PRESS, oldStart.wire()), "old actual owner accepts input1 before official respawn");
            var oldAccepted = fixture.accepted(Skill.CHARGING, 1);
            var oldPending = new PendingRequest(Skill.CHARGING, 1, original.getId(), oldEpoch);
            var replacement = fixture.respawn(original);
            helper.assertFalse(CurrentCharging.active(replacement), "official clone lifecycle disposes old held context before fresh input");
            var state = ready(replacement, "electromaster", CurrentCharging.ID);
            bind(helper, replacement, CurrentCharging.ID);
            long newEpoch = fixture.syncEpoch(replacement);
            helper.assertTrue(replacement != original && replacement.getId() == original.getId()
                            && replacement.getUUID().equals(original.getUUID()) && replacement.connection == original.connection
                            && newEpoch > oldEpoch,
                    "actual new incarnation has a fresh epoch despite equal actor ID UUID and listener");
            var beforeNewInput = AbilityStorage.encode(state);
            helper.assertFalse(request(replacement, LegacySingleKeyProtocol.PENDING_RELEASE, oldPending.wire()), "old epoch pending release cannot retire replacement input1 before its start");
            helper.assertFalse(request(replacement, LegacySingleKeyProtocol.PENDING_ABORT, oldPending.wire()), "old epoch pending abort cannot poison the fresh replacement retirement scope");
            helper.assertTrue(!CurrentCharging.active(replacement) && peek(replacement) == newEpoch
                            && AbilityStorage.encode(state).equals(beforeNewInput),
                    "old pending requests before new start preserve unstarted replacement state and current epoch");
            helper.assertTrue(request(replacement, LegacySingleKeyProtocol.PRESS, charging(replacement, 1, newEpoch).wire()), "fresh replacement owner can reuse physical input1");
            var current = fixture.accepted(replacement, Skill.CHARGING, 1);
            var paid = AbilityStorage.encode(state);
            helper.assertFalse(request(replacement, LegacySingleKeyProtocol.PRESS, oldStart.wire()), "old incarnation input1 start replay denied after new input1");
            helper.assertFalse(request(replacement, LegacySingleKeyProtocol.PRESS, oldFreshStart.wire()), "old epoch denies even unused higher input2 before nonce claim");
            helper.assertFalse(request(replacement, LegacySingleKeyProtocol.RELEASE, oldAccepted.wire()), "old accepted release cannot dispose new same-ID input1 hold");
            helper.assertFalse(request(replacement, LegacySingleKeyProtocol.ABORT, oldAccepted.wire()), "old accepted abort cannot dispose new same-ID input1 hold");
            helper.assertFalse(request(replacement, LegacySingleKeyProtocol.PENDING_RELEASE, oldPending.wire()), "old pending release cannot derive new input1 token");
            helper.assertFalse(request(replacement, LegacySingleKeyProtocol.PENDING_ABORT, oldPending.wire()), "old pending abort cannot derive new input1 token");
            helper.assertTrue(CurrentCharging.active(replacement) && AbilityStorage.encode(state).equals(paid)
                            && peek(replacement) == newEpoch && CurrentCharging.canAcceptInput(replacement, 2),
                    "all old-owner requests preserve actual new hold state epoch and unclaimed higher input");
            helper.assertTrue(request(replacement, LegacySingleKeyProtocol.ABORT, current.wire()), "new incarnation's actual captured identity can terminate its own hold");
            helper.assertFalse(CurrentCharging.active(replacement), "current-owner positive control terminates exact replacement hold");
            helper.succeed();
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void all_six_legacy_bypasses_deny_and_remapped_terminal_keeps_other_skill_live(GameTestHelper helper) {
        try (var fixture = new LegacySingleKeyNativeFixture(helper)) {
            var player = fixture.player;
            for (Skill skill : Skill.values()) {
                String category = switch (skill) {
                    case CHARGING, MAG_MOVEMENT, MAG_MANIP -> "electromaster";
                    case GROUND_SHOCK, DIRECTED_SHOCK -> "vecmanip";
                    case THREATENING_TELEPORT -> "teleporter";
                };
                var state = ready(player, category, skill.id());
                bind(helper, player, skill.id());
                var before = AbilityStorage.encode(state);
                fixture.assertPlainDenied(0, skill);
                for (String action : List.of("slot_press_token", "slot_release_token", "slot_abort_token"))
                    helper.assertFalse(request(player, action, "0:1"), "old slot-token route cannot bypass migrated identity " + skill.id() + ":" + action);
                for (String action : List.of("cast", "charge", "skill_start", "skill_release", "skill_abort", "abort"))
                    helper.assertFalse(request(player, action, skill.id()), "raw skill-name route cannot bypass migrated identity " + skill.id() + ":" + action);
                helper.assertTrue(AbilityStorage.encode(state).equals(before), "all legacy bypass denials are atomic for " + skill.id());
            }
            var state = ready(player, "electromaster", CurrentCharging.ID, MagManip.ID);
            bind(helper, player, CurrentCharging.ID);
            long epoch = fixture.syncEpoch(player);
            var start = charging(player, 1, epoch);
            for (String bad : List.of("", "0:c:0:" + player.getId() + ":" + Long.toString(epoch, 36),
                    "00:c:1:" + player.getId() + ":" + Long.toString(epoch, 36), start.wire() + ":1"))
                helper.assertFalse(request(player, LegacySingleKeyProtocol.PRESS, bad), "malformed closed start identity denied " + bad);
            helper.assertTrue(request(player, LegacySingleKeyProtocol.PRESS, start.wire()), "actual captured Charging context starts before remap");
            var charging = fixture.accepted(Skill.CHARGING, 1);
            helper.assertTrue(request(player, "preset_edit", "0:0:mag_manip"), "actual current slot remaps from Charging to MagManip");
            helper.assertTrue(CurrentCharging.active(player) && state.presets.currentSkill(0).equals(MagManip.ID), "server remap preserves captured Charging until its own callback");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_BLOCK));
            helper.assertTrue(fixture.press(0, Skill.MAG_MANIP, 1) && MagManip.active(player), "actual remapped skill independently accepts coincident input1 from a real held metal block");
            var manipulation = fixture.accepted(Skill.MAG_MANIP, 1);
            var block = MagManip.block(player);
            fixture.track(block);
            fixture.assertPlainDenied(0, Skill.MAG_MANIP);
            var paid = AbilityStorage.encode(state);
            helper.assertTrue(fixture.terminal(LegacySingleKeyProtocol.ABORT, charging), "fixed accepted Charging terminal ends captured old skill after remap");
            helper.assertTrue(!CurrentCharging.active(player) && MagManip.active(player) && block.hovering()
                            && AbilityStorage.encode(state).equals(paid),
                    "old fixed terminal never redirects into live newly mapped same-input MagManip or spends/refunds");
            helper.assertFalse(fixture.terminal(LegacySingleKeyProtocol.RELEASE, charging), "late old accepted release cannot redirect through remapped slot");
            var retiredCharging = new PendingRequest(Skill.CHARGING, 1, player.getId(), epoch);
            helper.assertTrue(request(player, LegacySingleKeyProtocol.PENDING_ABORT, retiredCharging.wire()), "first authenticated pending Charging retirement acknowledges the old input without redirecting to MagManip");
            helper.assertFalse(request(player, LegacySingleKeyProtocol.PENDING_ABORT, retiredCharging.wire()), "duplicate old Charging pending retirement is inert");
            helper.assertTrue(MagManip.active(player) && block.hovering(), "remapped native hold survives both old terminal roles");
            helper.assertTrue(fixture.terminal(LegacySingleKeyProtocol.ABORT, manipulation), "independent captured MagManip identity still aborts its own physical block");
            helper.assertTrue(!MagManip.active(player) && !block.hovering(), "only correct MagManip identity ends its hold and enables gravity");
            helper.succeed();
        }
    }

    private static AbilityProgress ready(ServerPlayer player, String category, String... skills) {
        var state = AbilityStorage.get(player);
        state.selectCategory(category);
        state.setLevel(5);
        for (String skill : skills) state.learn(skill);
        state.restoreCalculatedMaxima(20000, 20000);
        state.cp = 20000;
        state.overload = 0;
        state.overloadFine = true;
        state.activated = true;
        player.setYRot(0);
        player.setXRot(0);
        player.setYHeadRot(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        return state;
    }

    private static void bind(GameTestHelper helper, ServerPlayer player, String skill) {
        helper.assertTrue(request(player, "preset_edit", "0:0:" + skill), "actual native preset ingress binds fixture-learned skill " + skill);
    }

    private static StartRequest charging(ServerPlayer player, long input, long epoch) {
        return new StartRequest(0, Skill.CHARGING, input, player.getId(), epoch);
    }

    private static long peek(ServerPlayer player) {
        return LegacySingleKeyOwnerEpoch.peek(player, player.connection, player.serverLevel(), AbilityStorage.get(player));
    }

    private static boolean request(ServerPlayer player, String action, String wire) {
        return AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request(action, wire));
    }

    private static void near(GameTestHelper helper, double actual, double expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) < 1E-5, label + ": expected " + expected + ", got " + actual);
    }
}

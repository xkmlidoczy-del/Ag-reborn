package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.api.SkillExpAddedEvent;
import cn.academy.port.api.SkillPerformEvent;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.CurrentChargingSession;
import cn.academy.port.core.LegacySingleKeyProtocol;
import cn.academy.port.core.LegacySingleKeyProtocol.AcceptedRequest;
import cn.academy.port.core.LegacySingleKeyProtocol.Skill;
import cn.academy.port.core.LegacySingleKeyProtocol.StartRequest;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.highpower.HighPowerCharging;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.skill.CurrentCharging;
import cn.academy.port.wireless.ClassicWirelessDevices;
import cn.academy.port.wireless.ClassicWirelessNodeBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Separate, nonshipped Charging companion. Real admitted native players, requestFromClient,
 * production accepted identities, native stores and event subscribers; no socket/client claim.
 * The M41 comparison isolates the original entry class while sharing today's native ledgers.
 * Original ledger arithmetic has its own independent core differential, not a whole-M41 claim.
 */
@GameTestHolder("academy_charging_companion")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class ChargingCompanionRuntimeTests {
    private static final String TEMPLATE = "runtime_empty";
    private static final String BATCH = "charging_companion";
    private static final BlockPos TARGET = new BlockPos(3, 2, 6);
    private ChargingCompanionRuntimeTests() {}

    /** In-memory test namespace; this class is excluded from the distributed mod jar. */
    @SubscribeEvent public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var tag = new CompoundTag(); var size = new ListTag();
        for (int value : new int[]{8, 6, 24}) size.add(IntTag.valueOf(value));
        tag.put("size", size);
        var origin = new ListTag(); for (int i = 0; i < 3; i++) origin.add(IntTag.valueOf(0));
        var cell = new CompoundTag(); cell.put("pos", origin); cell.putInt("state", 0);
        var blocks = new ListTag(); blocks.add(cell); tag.put("blocks", blocks); tag.put("entities", new ListTag());
        var air = new CompoundTag(); air.putString("Name", "minecraft:air");
        var palette = new ListTag(); palette.add(air); tag.put("palette", palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_charging_companion", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void enrolled_positive_wire_identity_pays_once_and_durable_repress_waits_for_real_tick(GameTestHelper h) {
        var f = fixture(h);
        var p = f.player;
        var s = ready(h, f, true, 1);
        var stack = portable(p);
        var hook = observe(h, p, event -> {});
        long epoch = f.syncEpoch(p);
        var start = start(p, 1, epoch);
        var before = Ledger.of(s);
        h.assertTrue(request(p, LegacySingleKeyProtocol.PRESS, start.wire()), "actual enrolled positive wire press starts");
        var accepted = f.accepted(Skill.CHARGING, 1);
        before.same(h, s, "accepted enhanced start is a free preflight");
        h.assertValueEqual(hook.costs.size(), 0, "start posts no cost callback");
        CurrentCharging.tick(p);
        var first = before.paid(7, 48);
        first.same(h, s, "one master transfer pays exact native ledgers");
        exact(h, energy(stack), 35, "one actual native IF transfer");
        h.assertValueEqual(hook.costs, List.of("7.0/48.0"), "one combined actual CP/strain event");
        h.assertValueEqual(hook.awards, 1, "one real XP event even at capped mastery");
        var quota = quota(p);
        CurrentCharging.tick(p);
        h.assertFalse(request(p, LegacySingleKeyProtocol.PRESS, start.wire()), "same physical press cannot replay");
        rejectTerminals(h, f, accepted);
        rejectLegacy(h, f);
        first.same(h, s, "spoofed terminals, legacy bypasses and tick replay preserve all ledgers");
        exact(h, energy(stack), 35, "denials cannot charge again");
        h.assertTrue(quota.equals(quota(p)), "denials preserve durable quota exactly");
        h.assertTrue(CurrentCharging.active(p), "wrong terminals cannot remove real enhanced hold");
        h.assertTrue(f.terminal(LegacySingleKeyProtocol.RELEASE, accepted), "matching positive accepted release ends hold");
        h.assertFalse(CurrentCharging.active(p), "matching release removes actual hold");
        h.assertFalse(f.terminal(LegacySingleKeyProtocol.RELEASE, accepted), "released terminal replay is inert");
        h.assertFalse(request(p, LegacySingleKeyProtocol.PRESS, start.wire()), "released physical input stays retired");
        var secondStart = start(p, 2, epoch);
        h.assertTrue(request(p, LegacySingleKeyProtocol.PRESS, secondStart.wire()), "new physical input may begin same tick");
        var secondAccepted = f.accepted(Skill.CHARGING, 2);
        h.assertTrue(secondAccepted.token() != accepted.token(), "new hold has its own actual server token");
        CurrentCharging.tick(p);
        first.same(h, s, "same-tick new hold cannot pay or award again");
        exact(h, energy(stack), 35, "same-tick durable quota blocks second IF write");
        h.assertTrue(quota.equals(quota(p)), "same-tick repress leaves quota unchanged");
        h.assertValueEqual(hook.awards, 1, "same-tick repress adds no XP event");
        long tick = p.serverLevel().getGameTime();
        h.runAfterDelay(1, () -> {
            h.assertTrue(p.serverLevel().getGameTime() > tick, "real native world clock advances");
            // The actual post-tick subscriber may already have run; this call must be idempotent.
            CurrentCharging.tick(p);
            first.paid(7, 48).sameResources(h, s, "exactly two genuine transfers across two holds");
            exact(h, energy(stack), 70, "one later world tick restores one native transfer");
            h.assertValueEqual(hook.costs, List.of("7.0/48.0", "7.0/48.0"), "new paid hold pays its own first strain once");
            h.assertValueEqual(hook.awards, 2, "exactly one XP event per positive transfer");
            h.assertTrue(f.terminal(LegacySingleKeyProtocol.RELEASE, secondAccepted), "second exact accepted identity releases");
            h.assertValueEqual(f.payloads("charging_start", p), 2, "exactly two actual start payloads");
            h.assertValueEqual(f.payloads("charging_end", p), 2, "exactly two actual terminal payloads");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void enrolled_positive_partial_fill_bills_accepted_if_and_emits_one_xp(GameTestHelper h) {
        try (var f = new LegacySingleKeyNativeFixture(h)) {
            var p = f.player; var s = ready(h, f, true, 0); var stack = portable(p);
            ClassicEnergyItemHelper.setEnergy(stack, DeveloperType.PORTABLE.energy - 1);
            var hook = observe(h, p, event -> {}); var before = Ledger.of(s);
            var press = start(p, 1, f.syncEpoch(p));
            h.assertTrue(request(p, LegacySingleKeyProtocol.PRESS, press.wire()), "actual positive partial-fill press accepted");
            var accepted = f.accepted(Skill.CHARGING, 1);
            before.same(h, s, "partial fill start costs nothing");
            CurrentCharging.tick(p);
            before.paid(.2f, 65).same(h, s, "one accepted IF costs one fifth CP with exact float growth and XP");
            exact(h, energy(stack), DeveloperType.PORTABLE.energy, "native finite store fills exactly");
            h.assertValueEqual(hook.costs, List.of("0.2/65.0"), "actual event carries proportional accepted-energy cost");
            h.assertValueEqual(hook.awards, 1, "partial positive fill earns one actual skill XP event");
            h.assertFalse(CurrentCharging.active(p), "filled native target ends its actual positive-input hold");
            h.assertFalse(f.terminal(LegacySingleKeyProtocol.RELEASE, accepted), "automatic end makes later release inert");
            var paid = Ledger.of(s); var quota = quota(p);
            CurrentCharging.tick(p);
            h.assertFalse(request(p, LegacySingleKeyProtocol.PRESS, start(p, 2, press.ownerEpoch()).wire()), "full native item cannot start another hold");
            paid.same(h, s, "full target and release replay never train or debit");
            h.assertTrue(quota.equals(quota(p)), "full-target rejection does not refill output budget");
            h.succeed();
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void enrolled_positive_callback_release_and_reentry_cancel_before_payment(GameTestHelper h) {
        for (boolean reenter : List.of(false, true)) {
            try (var f = new LegacySingleKeyNativeFixture(h)) {
                var p = f.player; var s = ready(h, f, true, 0); var stack = portable(p);
                var press = start(p, 1, f.syncEpoch(p));
                h.assertTrue(request(p, LegacySingleKeyProtocol.PRESS, press.wire()), "positive callback fixture starts");
                var accepted = f.accepted(Skill.CHARGING, 1);
                var before = Ledger.of(s); var quota = quota(p);
                var hook = observe(h, p, event -> {
                    if (reenter) { CurrentCharging.tick(p); h.assertFalse(CurrentCharging.start(p), "cost callback cannot recursively start"); }
                    h.assertFalse(f.terminal(LegacySingleKeyProtocol.RELEASE, accepted), "wire ingress remains fenced while the native cost callback is busy");
                    h.assertFalse(CurrentCharging.release(p, accepted.input(), accepted.token(), accepted.ownerEpoch()),
                            "exact wire identity also remains fenced inside a busy cost callback");
                    h.assertTrue(CurrentCharging.release(p), "trusted server lifecycle callback cancels the owning hold");
                    if (reenter) {
                        h.assertFalse(request(p, LegacySingleKeyProtocol.PRESS, start(p, 2, press.ownerEpoch()).wire()), "wire reentry cannot replace canceled hold during cost callback");
                        CurrentCharging.tick(p);
                    }
                });
                CurrentCharging.tick(p);
                before.same(h, s, "callback cancellation precedes CP, strain, capacity growth and XP");
                exact(h, energy(stack), 0, "callback cancellation precedes native IF write");
                h.assertTrue(quota.equals(quota(p)), "canceled transaction claims no quota");
                h.assertFalse(CurrentCharging.active(p), "callback cancellation cannot revive");
                h.assertValueEqual(hook.costs.size(), 1, "bounded actual cost callback with no recursion");
                h.assertValueEqual(hook.awards, 0, "canceled callback emits no XP event");
                h.assertFalse(f.terminal(LegacySingleKeyProtocol.RELEASE, accepted), "late callback release is inert");
            }
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void enrolled_positive_lifecycle_cancellation_before_and_after_payment_preserves_ledgers(GameTestHelper h) {
        for (boolean paid : List.of(false, true)) for (String action : List.of("abort", "deactivate", "dimension", "logout", "state_replaced")) {
            try (var f = new LegacySingleKeyNativeFixture(h)) {
                var p = f.player; var s = ready(h, f, true, 0); var stack = portable(p);
                h.assertTrue(f.press(0, Skill.CHARGING, 1), "positive lifecycle fixture starts: " + action);
                var accepted = f.accepted(Skill.CHARGING, 1);
                var initial = Ledger.of(s);
                if (paid) { CurrentCharging.tick(p); initial.paid(3, 65).same(h, s, "one exact payment before " + action); }
                var before = Ledger.of(s); var quota = quota(p);
                switch (action) {
                    case "abort" -> h.assertTrue(f.terminal(LegacySingleKeyProtocol.ABORT, accepted), "owned positive abort accepted");
                    case "deactivate" -> h.assertTrue(request(p, "activate_state", "false"), "actual deactivation request accepted");
                    case "dimension" -> NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(p, Level.OVERWORLD, Level.NETHER));
                    case "logout" -> NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p));
                    case "state_replaced" -> { AbilityStorage.remove(p); CurrentCharging.tick(p); }
                    default -> throw new AssertionError(action);
                }
                h.assertFalse(CurrentCharging.active(p), "lifecycle removes enhanced hold: " + action);
                var current = AbilityStorage.get(p);
                before.same(h, current, "ordinary lifecycle never refunds or adds charging ledgers: " + action);
                CurrentCharging.tick(p);
                h.assertFalse(f.terminal(LegacySingleKeyProtocol.RELEASE, accepted), "stale accepted terminal cannot revive: " + action);
                before.same(h, current, "post-lifecycle tick and terminal remain inert: " + action);
                exact(h, energy(stack), paid ? 15 : 0, "no later native transfer: " + action);
                h.assertTrue(quota.equals(quota(p)), "durable quota survives lifecycle: " + action);
            }
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void enrolled_positive_death_and_official_clone_keep_quota_and_never_copy_live_hold(GameTestHelper h) {
        for (boolean paid : List.of(false, true)) {
            try (var f = new LegacySingleKeyNativeFixture(h)) {
                var p = f.player; var s = ready(h, f, true, 0); var stack = portable(p);
                h.assertTrue(f.press(0, Skill.CHARGING, 1), "positive death fixture begins");
                if (paid) CurrentCharging.tick(p);
                var before = Ledger.of(s); var quota = quota(p); double maximum = s.maxCp();
                NeoForge.EVENT_BUS.post(new LivingDeathEvent(p, p.damageSources().generic()));
                h.assertFalse(CurrentCharging.active(p), "actual death subscriber removes enhanced hold");
                exact(h, s.cp, maximum, "existing death policy recovers CP to trained native maximum");
                exact(h, s.overload, 0, "existing death policy clears overload");
                before.sameTraining(h, s, "death preserves earned training and XP without another award");
                h.assertFalse(s.activated, "death deactivates native ability state");
                h.assertFalse(s.overloadFine, "existing death recovery keeps original lock state");
                CurrentCharging.tick(p);
                exact(h, energy(stack), paid ? 15 : 0, "death cannot transfer another IF tick");
                h.assertTrue(quota.equals(quota(p)), "death preserves durable output quota");
            }
            try (var f = new LegacySingleKeyNativeFixture(h)) {
                var p = f.player; var s = ready(h, f, true, 0); portable(p);
                long epoch = f.syncEpoch(p);
                var press = start(p, 1, epoch);
                h.assertTrue(request(p, LegacySingleKeyProtocol.PRESS, press.wire()), "positive official clone fixture begins");
                var accepted = f.accepted(Skill.CHARGING, 1);
                if (paid) CurrentCharging.tick(p);
                var before = Ledger.of(s); var quota = quota(p);
                var replacement = f.respawn(p);
                h.assertFalse(CurrentCharging.active(replacement), "official clone never retains enhanced hold");
                h.assertTrue(HighPowerCombat.enabled(replacement), "official clone retains actual enrollment");
                before.same(h, AbilityStorage.get(replacement), "nondeath official clone preserves exact native charging ledgers");
                h.assertTrue(quota.equals(quota(replacement)), "official clone copies durable quota exactly");
                h.assertFalse(request(replacement, LegacySingleKeyProtocol.RELEASE, accepted.wire()), "old owner token cannot affect replacement");
                h.assertFalse(request(replacement, LegacySingleKeyProtocol.PRESS, press.wire()), "old epoch cannot restart replacement hold");
                CurrentCharging.tick(replacement);
                before.same(h, AbilityStorage.get(replacement), "stale owner traffic and untaken input never train clone");
            }
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "charging_companion_original_entry", timeoutTicks = 30)
    public static void unchanged_m41_entry_matches_current_non_enrolled_native_traces_with_shared_ledgers(GameTestHelper h) throws Exception {
        try (var original = ChargingM41EntryFixture.open()) {
            for (String scenario : List.of("item_novice", "item_master", "item_full", "item_unsupported", "item_exhausted",
                    "item_replacement", "creative", "block", "block_exhausted", "block_full", "block_miss",
                    "callback_release", "callback_tick", "callback_start")) {
                List<String> expected = classicTrace(h, original, scenario);
                List<String> actual = classicTrace(h, ChargingM41EntryFixture.current(), scenario);
                h.assertValueEqual(actual, expected, "actual current entry equals unchanged M41 entry for " + scenario);
            }
            original.verify();
            System.out.println("PASS Charging native entry differential: 14 scenarios; exact M41 CurrentCharging source; actual current class resources; shared current native ledgers; no whole-M41 equivalence claim");
            h.succeed();
        }
    }

    private static List<String> classicTrace(GameTestHelper h, ChargingM41EntryFixture.Entry entry, String scenario) {
        try (var f = new LegacySingleKeyNativeFixture(h)) {
            var p = f.player; var s = ready(h, f, false, scenario.equals("item_master") ? 1 : 0);
            h.assertFalse(p.getPersistentData().contains(HighPowerCombat.DATA_KEY), "classic lane is genuinely non-enrolled");
            var stack = portable(p);
            if (scenario.equals("item_full")) ClassicEnergyItemHelper.setEnergy(stack, DeveloperType.PORTABLE.energy);
            if (scenario.equals("item_unsupported")) p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            boolean block = scenario.startsWith("block");
            ClassicWirelessNodeBlockEntity node;
            if (block) {
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                if (!scenario.equals("block_miss")) {
                    h.setBlock(TARGET, ClassicWirelessDevices.BASIC.get());
                    node = (ClassicWirelessNodeBlockEntity) h.getBlockEntity(TARGET);
                    if (scenario.equals("block_full")) node.setEnergy(node.getMaxEnergy());
                } else { h.setBlock(TARGET, Blocks.AIR); node = null; }
            } else node = null;
            if (scenario.endsWith("exhausted")) s.cp = scenario.startsWith("item") ? 2 : 0;
            if (scenario.equals("creative")) p.getAbilities().instabuild = true;
            var trace = new ArrayList<String>();
            var hook = observe(h, p, event -> {
                trace.add("cost:" + event.cp + "/" + event.overload);
                if (event.cp > 0) {
                    if (scenario.equals("callback_release")) trace.add("callback.release:" + entry.release(p));
                    if (scenario.equals("callback_tick")) { entry.tick(p); trace.add("callback.active:" + entry.active(p)); }
                    if (scenario.equals("callback_start")) trace.add("callback.start:" + entry.start(p, 2));
                }
            });
            try {
                long epoch = f.syncEpoch(p);
                trace.add("start:" + entry.start(p, 1));
                trace.add("started:" + Ledger.of(s) + "/active=" + entry.active(p));
                var accepted = f.accepted(Skill.CHARGING, 1);
                h.assertTrue(accepted.ownerEpoch() == epoch, "original/current start emits actual issued owner epoch");
                trace.add("duplicate:" + entry.start(p, 1));
                if (scenario.equals("item_replacement")) { stack = portable(p); s.experience.put("charging", 1.0); }
                entry.tick(p);
                trace.add("tick:" + Ledger.of(s) + "/active=" + entry.active(p) + "/IF=" + (block ? node == null ? 0 : node.getEnergy() : energy(stack)));
                entry.tick(p);
                trace.add("replay:" + Ledger.of(s) + "/active=" + entry.active(p));
                trace.add("wrong-token:" + entry.release(p, accepted.input(), accepted.token() + 1, accepted.ownerEpoch()));
                trace.add("release:" + entry.release(p, accepted.input(), accepted.token(), accepted.ownerEpoch()));
                trace.add("released:" + Ledger.of(s) + "/active=" + entry.active(p));
                trace.add("reused-input:" + entry.start(p, 1));
                trace.add("events:" + hook.awards + "/starts=" + f.payloads("charging_start", p) + "/ends=" + f.payloads("charging_end", p));
                return trace;
            } finally {
                NeoForge.EVENT_BUS.unregister(hook);
                entry.remove(p);
                if (block) h.setBlock(TARGET, Blocks.AIR);
            }
        }
    }

    private static AbilityProgress ready(GameTestHelper h, LegacySingleKeyNativeFixture f, boolean enrolled, double mastery) {
        var p = f.player; var s = AbilityStorage.get(p);
        s.selectCategory("electromaster"); s.setLevel(1); s.activated = true; s.experience.put("charging", mastery);
        if (enrolled) h.assertTrue(HighPowerCombat.enroll(p, 1), "explicit genuine native fixture enrollment");
        h.assertTrue(request(p, "preset_edit", "0:0:charging"), "actual preset ingress binds learned Charging");
        f.syncEpoch(p);
        exact(h, s.cp, 1800, "native level-one fixture CP");
        exact(h, s.overload, 0, "native initial overload");
        return s;
    }

    private static void rejectTerminals(GameTestHelper h, LegacySingleKeyNativeFixture f, AcceptedRequest accepted) {
        for (var invalid : List.of(
                new AcceptedRequest(Skill.CHARGING, accepted.input(), accepted.actorId(), accepted.ownerEpoch(), accepted.token() + 1),
                new AcceptedRequest(Skill.CHARGING, accepted.input(), accepted.actorId(), accepted.ownerEpoch() + 1, accepted.token()),
                new AcceptedRequest(Skill.CHARGING, accepted.input() + 1, accepted.actorId(), accepted.ownerEpoch(), accepted.token()))) {
            for (String action : List.of(LegacySingleKeyProtocol.RELEASE, LegacySingleKeyProtocol.ABORT))
                h.assertFalse(f.terminal(action, invalid), "wrong input/token/epoch cannot terminate enrolled hold");
        }
    }

    private static void rejectLegacy(GameTestHelper h, LegacySingleKeyNativeFixture f) {
        f.assertPlainDenied(0, Skill.CHARGING);
        for (String action : List.of("slot_press_token", "slot_release_token", "slot_abort_token"))
            h.assertFalse(request(f.player, action, "0:1"), "legacy slot-token bypass denied for enrolled Charging");
        for (String action : List.of("cast", "charge", "skill_start", "skill_release", "skill_abort", "abort"))
            h.assertFalse(request(f.player, action, "charging"), "raw skill-name bypass denied for enrolled Charging");
    }

    private static LegacySingleKeyNativeFixture fixture(GameTestHelper h) {
        var fixture = new LegacySingleKeyNativeFixture(h);
        cleanup(h, fixture::close);
        return fixture;
    }
    private static Events observe(GameTestHelper h, ServerPlayer p, Consumer<SkillPerformEvent> callback) {
        var events = new Events(p, callback); NeoForge.EVENT_BUS.register(events);
        cleanup(h, () -> NeoForge.EVENT_BUS.unregister(events));
        return events;
    }
    private static void cleanup(GameTestHelper h, Runnable action) {
        h.testInfo.addListener(new GameTestListener() {
            boolean closed;
            private void close() { if (!closed) { closed = true; action.run(); } }
            public void testStructureLoaded(GameTestInfo info) {}
            public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
            public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) { close(); }
        });
    }
    public static final class Events {
        final ServerPlayer owner; final Consumer<SkillPerformEvent> callback;
        final List<String> costs = new ArrayList<>(); int awards; boolean inside;
        Events(ServerPlayer owner, Consumer<SkillPerformEvent> callback) { this.owner = owner; this.callback = callback; }
        @SubscribeEvent public void cost(SkillPerformEvent event) {
            if (event.player != owner || !event.skill.equals("charging")) return;
            costs.add(event.cp + "/" + event.overload);
            if (inside) throw new AssertionError("recursive Charging cost callback");
            inside = true; try { callback.accept(event); } finally { inside = false; }
        }
        @SubscribeEvent public void xp(SkillExpAddedEvent event) {
            if (event.getEntity() == owner && event.skill.equals("charging")) awards++;
        }
    }

    private record Ledger(double cp, double overload, double extraCp, double extraOverload,
                          double xp, double progress, int cpDelay, int overloadDelay) {
        static Ledger of(AbilityProgress s) { return new Ledger(s.cp, s.overload, s.extraCp, s.extraOverload,
                s.exp("charging"), s.levelExperience, s.cpDelay, s.overloadDelay); }
        Ledger paid(float cost, float strain) {
            float amount = .0001f;
            return new Ledger((float) cp - cost, (float) overload + strain,
                    (float) extraCp + cost * .0025f, (float) extraOverload + Math.max(0f, Math.min(10f, strain * .0058f)),
                    (float) xp + Math.min(1f - (float) xp, amount), (float) progress + amount, 15, 32);
        }
        void same(GameTestHelper h, AbilityProgress s, String label) { h.assertValueEqual(of(s), this, label); }
        void sameResources(GameTestHelper h, AbilityProgress s, String label) {
            exact(h, s.cp, cp, label + " CP"); exact(h, s.overload, overload, label + " overload"); sameTraining(h, s, label);
        }
        void sameTraining(GameTestHelper h, AbilityProgress s, String label) {
            exact(h, s.extraCp, extraCp, label + " CP growth"); exact(h, s.extraOverload, extraOverload, label + " overload growth");
            exact(h, s.exp("charging"), xp, label + " XP"); exact(h, s.levelExperience, progress, label + " level XP");
        }
    }
    private static StartRequest start(ServerPlayer p, long input, long epoch) { return new StartRequest(0, Skill.CHARGING, input, p.getId(), epoch); }
    private static boolean request(ServerPlayer p, String action, String value) { return AcademyGameplay.requestFromClient(p, new AcademyNetwork.Request(action, value)); }
    private static ItemStack portable(ServerPlayer p) { var stack = new ItemStack(AcademyCraft.DEVELOPER.get()); p.setItemInHand(InteractionHand.MAIN_HAND, stack); return stack; }
    private static double energy(ItemStack stack) { return ClassicEnergyItemHelper.getEnergy(stack); }
    private static CompoundTag quota(ServerPlayer p) { return p.getPersistentData().getCompound(HighPowerCharging.DATA_KEY).copy(); }
    private static void exact(GameTestHelper h, double actual, double expected, String label) {
        h.assertTrue(Double.doubleToRawLongBits(actual) == Double.doubleToRawLongBits(expected), label + ": expected " + expected + ", got " + actual);
    }
}

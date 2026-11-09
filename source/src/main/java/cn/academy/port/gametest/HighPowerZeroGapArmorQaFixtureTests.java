package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.*;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Command denials use the real native server. Math proposals use private reflection;
 * no test impersonates the permitted integrated world or grants a production bypass. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerZeroGapArmorQaFixtureTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_zero_gap_qa_fixture";
    private static final String PROPERTY = "academy.highpower.zeroGapArmorFixture", COMMAND = "academyhp_qa_drain252";
    private HighPowerZeroGapArmorQaFixtureTests() {}

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void command_opt_in_permission_and_real_server_denials_never_mutate(GameTestHelper h) {
        Fixture f = new Fixture(h); CompoundTag before = f.player.getPersistentData().copy();
        CompoundTag ability = AbilityStorage.encode(AbilityStorage.get(f.player)); String previous = System.getProperty(PROPERTY);
        try {
            System.clearProperty(PROPERTY);
            CommandDispatcher<CommandSourceStack> absent = new CommandDispatcher<>(); register(absent);
            h.assertTrue(absent.getRoot().getChild(COMMAND) == null, "default-off command is absent");
            System.setProperty(PROPERTY, "true");
            CommandDispatcher<CommandSourceStack> present = new CommandDispatcher<>(); register(present);
            h.assertTrue(present.getRoot().getChild(COMMAND) != null, "explicit opt-in registers command");
            var source = f.player.createCommandSourceStack().withPermission(2).withSource(CommandSource.NULL);
            try { present.execute(COMMAND, source.withPermission(0)); h.fail("unauthorized command admitted"); }
            catch (CommandSyntaxException expected) { /* Brigadier rejects before execution. */ }
            h.assertTrue(drain(source.withPermission(0)) == 0, "execution independently enforces OP2");
            System.clearProperty(PROPERTY);
            h.assertTrue(present.execute(COMMAND, source) == 0, "registered command rechecks disabled property");
            System.setProperty(PROPERTY, "true");
            h.assertFalse(f.player.server.isSingleplayer(), "native GameTest server is not a permitted integrated world");
            h.assertTrue(present.execute(COMMAND, source) == 0, "actual non-integrated native server refuses");
            h.assertTrue(f.player.getPersistentData().equals(before), "all command denials leave complete live NBT untouched");
            h.assertTrue(AbilityStorage.encode(AbilityStorage.get(f.player)).equals(ability), "denials preserve live CP, XP and skill state");
            h.succeed();
        } catch (CommandSyntaxException unexpected) { throw new AssertionError(unexpected); }
        finally { if (previous == null) System.clearProperty(PROPERTY); else System.setProperty(PROPERTY, previous); }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void isolation_policy_rejects_dedicated_wrong_world_and_published_sessions(GameTestHelper h) {
        Path named = Path.of("/isolated/saves/aca-hp-md252-test");
        h.assertTrue(isolated(true, true, false, true, false, named), "only explicit private integrated named-world combination passes");
        h.assertFalse(isolated(false, true, false, true, false, named), "property off refuses");
        h.assertFalse(isolated(true, false, false, true, false, named), "missing OP2 refuses");
        h.assertFalse(isolated(true, true, true, true, false, named), "dedicated flag always refuses");
        h.assertFalse(isolated(true, true, false, false, false, named), "non-integrated refuses");
        h.assertFalse(isolated(true, true, false, true, true, named), "LAN published session refuses");
        for (Path wrong : List.of(Path.of("/saves/real-survival"), Path.of("/saves/aca-hp-md252-test-copy"),
                Path.of("/saves/aca-hp-md252-test/DIM-1"), Path.of("/")))
            h.assertFalse(isolated(true, true, false, true, false, wrong), "exact root name required: " + wrong);
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void clamp_preserves_small_balances_parent_conservation_and_all_unowned_metadata(GameTestHelper h) {
        Fixture f = new Fixture(h); var captured = f.capture(); var old = captured.armor().orElseThrow();
        CompoundTag before = f.player.getPersistentData().copy(), ability = AbilityStorage.encode(AbilityStorage.get(f.player));
        CompoundTag live = f.tag(), growth = live.getCompound("activeGrowth");
        var prepared = prepare(captured);
        h.assertTrue(f.player.getPersistentData().equals(before), "prepare is read-only");
        h.assertTrue(prepared.brokenPlateIds().isEmpty() && prepared.timing().equals(captured.timing().orElseThrow()),
                "fixture creates no break and changes neither phase nor existing break debt");
        h.assertTrue(ZeroGapArmorStorage.apply(prepared), "validated shared transaction applies atomically");
        var after = f.capture().armor().orElseThrow();
        BigDecimal baseTotal = BigDecimal.ZERO, lower = BigDecimal.ZERO, upper = BigDecimal.ZERO;
        for (int i = 0; i < 6; i++) {
            var oldPlate = old.plate(i); var plate = after.plate(i);
            eq(h, plate.baseCurrent(), oldPlate.baseCurrent().min(BigDecimal.ONE), "base clamp " + i);
            eq(h, plate.reserveCurrent(), oldPlate.reserveCurrent().min(BigDecimal.ONE), "reserve clamp " + i);
            eq(h, plate.baseCapacity(), oldPlate.baseCapacity(), "base capacity " + i);
            eq(h, plate.reserveCapacity(), oldPlate.reserveCapacity(), "reserve capacity " + i);
            h.assertTrue(oldPlate.current().signum() == 0 || plate.current().signum() > 0, "no fabricated break " + i);
            baseTotal = baseTotal.add(plate.baseCurrent());
            if ((i & 1) == 0) lower = lower.add(plate.reserveCurrent()); else upper = upper.add(plate.reserveCurrent());
        }
        for (int i = 0; i < 3; i++) eq(h, after.scatter().plate(i).current(),
                after.plate(2 * i).baseCurrent().add(after.plate(2 * i + 1).baseCurrent()), "exact B" + i);
        eq(h, after.base().shield(), baseTotal, "exact base S");
        eq(h, after.reserves().lower(), lower, "exact lower reserve"); eq(h, after.reserves().upper(), upper, "exact upper reserve");
        eq(h, after.total(), baseTotal.add(lower).add(upper), "all twelve children conserved");
        eq(h, after.total(), new BigDecimal("6.800001"), "independently calculated clamped total");
        h.assertTrue(after.base().health().equals(old.base().health()) && after.base().maxHealth().equals(old.base().maxHealth()), "HP and maximum HP untouched");
        h.assertTrue(f.tag() == live && f.tag().getCompound("activeGrowth") == growth, "live ledger and q metadata identities preserved");
        CompoundTag expected = before.copy(), expectedCombat = expected.getCompound(HighPowerCombat.DATA_KEY);
        for (String key : List.of("shield", ScatterPlateStorage.KEY, MagneticDomainReserve.KEY, ZeroGapArmorStorage.KEY))
            expectedCombat.put(key, f.tag().get(key).copy());
        h.assertTrue(f.player.getPersistentData().equals(expected), "only current resource fields differ; CP, Q/q, XP, timestamps and timing preserved");
        h.assertTrue(AbilityStorage.encode(AbilityStorage.get(f.player)).equals(ability), "live skill CP and XP untouched");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void repeated_preparation_is_no_op_and_native_reload_retains_low_balances(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(ZeroGapArmorStorage.apply(prepare(f.capture())), "first explicit clamp applies");
        CompoundTag before = f.player.getPersistentData().copy(); var armor = f.capture().armor().orElseThrow();
        var again = prepare(f.capture());
        h.assertTrue(again.after().toDecimalMap().equals(armor.toDecimalMap()), "repeated clamp cannot increase or move low currents");
        h.assertTrue(f.player.getPersistentData().equals(before), "no-op proposal changes no timestamp or metadata");
        CompoundTag saved = new CompoundTag(); f.player.saveWithoutId(saved); f.player.load(saved);
        h.assertTrue(f.capture().armor().orElseThrow().toDecimalMap().equals(armor.toDecimalMap()), "native reload preserves drained children and parents");
        h.assertTrue(f.capture().timing().orElseThrow().equals(new ZeroGapArmorTiming(73, 2)), "reload preserves existing phase and break debt");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void missing_corrupt_and_unavailable_ledgers_refuse_without_repair(GameTestHelper h) {
        Fixture f = new Fixture(h); CompoundTag healthy = f.tag().copy();
        for (String fault : List.of("armor", "child", "parent", "reserve", "unavailable", "timing")) {
            f.player.getPersistentData().put(HighPowerCombat.DATA_KEY, healthy.copy());
            switch (fault) {
                case "armor" -> f.tag().remove(ZeroGapArmorStorage.KEY);
                case "child" -> f.tag().getCompound(ZeroGapArmorStorage.KEY).putString("reserve5", "NaN");
                case "parent" -> f.tag().getCompound(ScatterPlateStorage.KEY).putString("plate2", "999");
                case "reserve" -> f.tag().remove(MagneticDomainReserve.KEY);
                case "unavailable" -> f.tag().putBoolean(ZeroGapArmorStorage.INVALIDATED_KEY, true);
                case "timing" -> f.tag().getCompound(ZeroGapArmorStorage.TIMING_KEY).putString("breakTicks", "5");
            }
            CompoundTag bad = f.player.getPersistentData().copy();
            h.assertTrue(ZeroGapArmorStorage.capture(f.player).isEmpty(), fault + " has no valid fixture input");
            h.assertTrue(f.player.getPersistentData().equals(bad), fault + " remains byte-for-byte unchanged");
        }
        f.player.getPersistentData().put(HighPowerCombat.DATA_KEY, healthy.copy());
        for (String key : List.of(ZeroGapArmorStorage.KEY, ZeroGapArmorStorage.TIMING_KEY, ZeroGapArmorStorage.INITIALIZED_KEY)) f.tag().remove(key);
        CompoundTag absent = f.player.getPersistentData().copy(); boolean rejected = false;
        try { prepare(f.capture()); } catch (IllegalStateException expected) { rejected = true; }
        h.assertTrue(rejected && f.player.getPersistentData().equals(absent), "never-enrolled ledger refuses rather than initializes");
        h.succeed();
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) { invoke("register", new Class<?>[]{CommandDispatcher.class}, dispatcher); }
    private static int drain(CommandSourceStack source) { return invoke("drain", new Class<?>[]{CommandSourceStack.class}, source); }
    private static boolean isolated(boolean enabled, boolean operator, boolean dedicated, boolean singleplayer, boolean published, Path root) {
        return invoke("isolated", new Class<?>[]{boolean.class, boolean.class, boolean.class, boolean.class, boolean.class, Path.class},
                enabled, operator, dedicated, singleplayer, published, root);
    }
    private static ZeroGapArmorStorage.Prepared prepare(ZeroGapArmorStorage.Capture capture) {
        return invoke("prepare", new Class<?>[]{ZeroGapArmorStorage.Capture.class}, capture);
    }
    @SuppressWarnings("unchecked") private static <T> T invoke(String name, Class<?>[] types, Object... args) {
        try { var method = ZeroGapArmorQaFixture.class.getDeclaredMethod(name, types); method.setAccessible(true); return (T) method.invoke(null, args); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new AssertionError(error.getCause());
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) {
        h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected);
    }
    private static CompoundTag decimalTag(Map<String, String> values) { CompoundTag tag = new CompoundTag(); values.forEach(tag::putString); return tag; }
    private static List<BigDecimal> amounts(String... values) { return java.util.Arrays.stream(values).map(BigDecimal::new).toList(); }

    /** Explicit setup for storage arithmetic, not a production command bypass. */
    private static final class Fixture {
        final ServerPlayer player;
        Fixture(GameTestHelper h) {
            player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[252-QA-Draft]"));
            var state = AbilityStorage.get(player); state.category = "meltdowner"; state.level = 5;
            state.cp = 17.25; state.levelExperience = 0.125; state.experience.put(ZeroGapArmor.ID, 0.375);
            ExactCombatState base = new ExactCombatState(new BigDecimal("1234.5"), HighPowerLevel.LV5.maxHealth(),
                    new BigDecimal("24.200001"), HighPowerLevel.LV5.maxShield(), false);
            var scatter = ScatterPlateShares.restore(base.shield(), base.maxShield(), amounts("0.2", "9", "15.000001"));
            var reserve = new SharedShieldReserveShares(new BigDecimal("5.6"), new BigDecimal("3"));
            var armor = ZeroGapArmorShares.restore(base, scatter, reserve, amounts("0", "0.2", "1", "8", "15", "0.000001"),
                    amounts("0.1", "0", "5", "1", "0.5", "2"));
            CompoundTag combat = decimalTag(base.toDecimalMap()); combat.putInt("level", 5); combat.putInt("earnedLevel", 5);
            combat.putInt("growthCurve", ContinuousHighPowerGrowth.CURVE_VERSION); combat.putString("growthPosition", "5");
            combat.put(ScatterPlateStorage.KEY, decimalTag(scatter.toDecimalMap())); combat.putBoolean(ScatterPlateStorage.INITIALIZED_KEY, true);
            CompoundTag reserveTag = decimalTag(reserve.toDecimalMap(base)); reserveTag.putInt("version", MagneticDomainReserve.VERSION);
            combat.put(MagneticDomainReserve.KEY, reserveTag); combat.put(ZeroGapArmorStorage.KEY, decimalTag(armor.toDecimalMap()));
            combat.putBoolean(ZeroGapArmorStorage.INITIALIZED_KEY, true); combat.put(ZeroGapArmorStorage.TIMING_KEY, decimalTag(new ZeroGapArmorTiming(73, 2).toDecimalMap()));
            CompoundTag growth = new CompoundTag(); growth.putInt("version", 1); growth.putString("position", "5");
            growth.putString("category", "meltdowner"); growth.putInt("nativeLevel", 5); growth.putDouble("nativeXp", 0.125);
            growth.putLong("revision", 7); growth.putBoolean("initial", false); growth.putString("sentinel", "retain q metadata");
            combat.put("activeGrowth", growth); combat.putLong("lastShieldSpend", -77); combat.putInt("unrelated", 41);
            player.getPersistentData().put(HighPowerCombat.DATA_KEY, combat);
            CompoundTag cp = new CompoundTag(); cp.putDouble("cp", 123.75); cp.putLong("cooldown", 101);
            player.getPersistentData().put(HighPowerResources.DATA_KEY, cp);
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo info, GameTestInfo rerun, GameTestRunner runner) { close(); }
                private void close() { AbilityStorage.remove(player); player.discard(); }
            });
        }
        CompoundTag tag() { return player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY); }
        ZeroGapArmorStorage.Capture capture() { return ZeroGapArmorStorage.capture(player).orElseThrow(); }
    }
}

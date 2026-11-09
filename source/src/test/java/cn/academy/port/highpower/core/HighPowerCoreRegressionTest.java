package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Dependency-free Java 21 regression entry point; assertions do not depend on -ea. */
public final class HighPowerCoreRegressionTest {
    private static int assertions;

    public static void main(String[] args) {
        presetsAndCaps();
        exactLowLevelHits();
        mitigationExamples();
        mitigationMonotonicity();
        shieldDamageAndCosts();
        noToggleRefillOrLifeRegeneration();
        persistenceRoundTrips();
        invalidPersistenceFailsClosed();
        randomizedDamageConservation();
        System.out.println("PASS " + assertions + " high-power exact combat core assertions");
    }

    private static void presetsAndCaps() {
        String[] expected = {"10", "178", "3162", "56234", "1000000"};
        for (int i = 1; i <= 5; i++) {
            HighPowerLevel level = HighPowerLevel.forLevel(i);
            eq(expected[i - 1], level.damage());
            eq(level.damage().multiply(bd("20")), level.maxHealth());
            eq(level.damage().multiply(bd("10")), level.maxShield());
            truth(level.level() == i, "level identity");
            ExactCombatState full = ExactCombatState.full(level);
            eq(level.maxHealth(), full.health());
            eq(level.maxShield(), full.shield());
        }
        eq("20000000", HighPowerLevel.LV5.maxHealth());
        rejects(() -> HighPowerLevel.forLevel(0));
        rejects(() -> HighPowerLevel.forLevel(6));
        BigDecimal tooHigh = CombatAmounts.CAP.add(BigDecimal.ONE);
        rejects(() -> new ExactCombatState(tooHigh, tooHigh, BigDecimal.ZERO, BigDecimal.ZERO, false));
        rejects(() -> new ExactCombatState(BigDecimal.ONE, CombatAmounts.CAP, tooHigh, tooHigh, true));
        rejects(() -> new ExactCombatState(bd("2"), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, false));
        rejects(() -> new ExactCombatState(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false));
        rejects(() -> new ExactCombatState(BigDecimal.ONE, BigDecimal.ONE, bd("2"), BigDecimal.ONE, true));
        rejects(() -> CombatAmounts.requireAmount(bd("-0.1"), "test"));
        rejects(() -> CombatAmounts.parse("NaN", "test"));
        rejects(() -> CombatAmounts.parse("Infinity", "test"));
        rejects(() -> CombatAmounts.parse(" 1 ", "test"));
        rejects(() -> CombatAmounts.parse("1e21", "test"));
        rejects(() -> CombatAmounts.parse("0e-2147483647", "test"));
        rejects(() -> CombatAmounts.parse("1e-2147483647", "test"));
        rejects(() -> CombatAmounts.parse("1e2147483647", "test"));
        rejects(() -> CombatAmounts.parse("0." + "0".repeat(600) + "1", "test"));
        rejects(() -> CombatAmounts.parse("0." + "1".repeat(257), "test"));
        rejects(() -> CombatAmounts.requireAmount(new BigDecimal("0e-2147483647"), "test"));
        rejects(() -> CombatAmounts.decimalString(new BigDecimal("0e-2147483647")));
        eq(CombatAmounts.CAP, CombatAmounts.parse("1e20", "test"));
        ExactCombatState state = ExactCombatState.full(HighPowerLevel.LV5);
        rejects(() -> state.attack(tooHigh, DefenseProfile.NONE));
        rejects(() -> state.applyMitigatedDamage(tooHigh));
        rejects(() -> state.spendShield(tooHigh));
        rejects(() -> state.regenerateShield(tooHigh));
        rejects(() -> state.withMaximums(tooHigh, BigDecimal.ZERO));
        rejects(() -> state.withMaximums(BigDecimal.ONE, tooHigh));
        rejects(() -> new DefenseProfile(Double.NaN, 0, 0, 0));
        rejects(() -> new DefenseProfile(0, Double.POSITIVE_INFINITY, 0, 0));
        rejects(() -> new DefenseProfile(-1, 0, 0, 0));
        rejects(() -> new DefenseProfile(0, 0, -1, 0));
        rejects(() -> new DefenseProfile(0, 0, 0, -1));
    }

    private static void exactLowLevelHits() {
        ExactCombatState state = new ExactCombatState(CombatAmounts.CAP, CombatAmounts.CAP, BigDecimal.ZERO, BigDecimal.ZERO, false);
        BigDecimal lowLevelHit = HighPowerLevel.LV1.damage();
        state = state.attack(lowLevelHit, DefenseProfile.NONE).state();
        eq(CombatAmounts.CAP.subtract(lowLevelHit), state.health());
        for (int i = 1; i < 10000; i++) state = state.applyMitigatedDamage(lowLevelHit).state();
        eq(CombatAmounts.CAP.subtract(lowLevelHit.multiply(bd("10000"))), state.health());
        BigDecimal before = state.health();
        state = state.applyMitigatedDamage(bd("0.0000000000000000000000000000001")).state();
        eq(bd("0.0000000000000000000000000000001"), before.subtract(state.health()));
        BigDecimal smallestScale = BigDecimal.ONE.scaleByPowerOfTen(-CombatAmounts.MAX_ABSOLUTE_SCALE);
        ExactCombatState scaleBoundary = new ExactCombatState(CombatAmounts.CAP, CombatAmounts.CAP, BigDecimal.ZERO, BigDecimal.ZERO, false);
        scaleBoundary = scaleBoundary.applyMitigatedDamage(smallestScale).state();
        eq(smallestScale, CombatAmounts.CAP.subtract(scaleBoundary.health()));
        truth(scaleBoundary.equals(ExactCombatState.fromDecimalMap(scaleBoundary.toDecimalMap())), "full-cap amount plus smallest supported fraction round trips");
        ExactCombatState shield = new ExactCombatState(CombatAmounts.CAP, CombatAmounts.CAP, CombatAmounts.CAP, CombatAmounts.CAP, true);
        DamageResult shieldHit = shield.attack(lowLevelHit, DefenseProfile.NONE);
        eq(CombatAmounts.CAP.subtract(lowLevelHit), shieldHit.state().shield());
        eq(CombatAmounts.CAP, shieldHit.state().health());
        eq(lowLevelHit, shieldHit.shieldAbsorbed());
        eq("0", shieldHit.healthDamage());
        truth(shieldHit.didDamage() && !shieldHit.killed(), "shield loss counts as damage, not a kill");
        // This sum intentionally exceeds the individual cap and must be legal.
        truth(shield.health().add(shield.shield()).compareTo(CombatAmounts.CAP) > 0, "independent pool caps");
    }

    private static void mitigationExamples() {
        eq("12", DamageMitigation.mitigate(bd("20"), new DefenseProfile(20, 0, 0, 0)));
        eq("8", DamageMitigation.mitigate(bd("20"), new DefenseProfile(20, 8, 0, 0)));
        eq("4", DamageMitigation.mitigate(bd("10"), new DefenseProfile(20, 0, 0, 0)));
        eq("84", DamageMitigation.mitigate(bd("100"), new DefenseProfile(20, 8, 0, 0)));
        eq("67.2", DamageMitigation.mitigate(bd("100"), new DefenseProfile(20, 8, 1, 0)));
        eq("40.32", DamageMitigation.mitigate(bd("100"), new DefenseProfile(20, 8, 1, 10)));
        // Retain the large-hit armor floor independently of the level-one balance anchor.
        eq("8400000", DamageMitigation.mitigate(bd("10000000"), new DefenseProfile(20, 20, 0, 0)));
        eq("0", DamageMitigation.mitigate(CombatAmounts.CAP, new DefenseProfile(20, 8, 5, 20)));
        eq("0", DamageMitigation.mitigate(CombatAmounts.CAP, new DefenseProfile(20, 8, Integer.MAX_VALUE, 20)));
        eq("20", DamageMitigation.mitigate(bd("100"), new DefenseProfile(0, 0, 0, Integer.MAX_VALUE)));
        DefenseProfile bypass = new DefenseProfile(bd("20"), bd("8"), 5, 20, true, true, true);
        eq("100", DamageMitigation.mitigate(bd("100"), bypass));
        DefenseProfile bypassArmorOnly = new DefenseProfile(bd("20"), bd("8"), 1, 10, true, false, false);
        eq("48", DamageMitigation.mitigate(bd("100"), bypassArmorOnly));
        BigDecimal repeating = DamageMitigation.mitigate(BigDecimal.ONE, new DefenseProfile(20, 4, 0, 0));
        truth(repeating.compareTo(bd("0.2133333333333333333333333333333333333333")) > 0, "repeating rational lower bound");
        truth(repeating.compareTo(bd("0.2133333333333333333333333333333333333334")) < 0, "repeating rational upper bound");
        ExactCombatState big = new ExactCombatState(CombatAmounts.CAP, CombatAmounts.CAP, BigDecimal.ZERO, BigDecimal.ZERO, false);
        eq(repeating, big.health().subtract(big.applyMitigatedDamage(repeating).state().health()));
        BigDecimal tiny = DamageMitigation.mitigate(bd("1e-100"), new DefenseProfile(20, 4, 0, 0));
        truth(tiny.signum() > 0, "mitigation must not round tiny nonzero damage to zero");
    }

    private static void mitigationMonotonicity() {
        BigDecimal[] hits = {bd("0.1"), BigDecimal.ONE, bd("5"), bd("20"), bd("100"), bd("1e7"), CombatAmounts.CAP};
        for (BigDecimal hit : hits) {
            for (int toughness = 0; toughness <= 20; toughness++) {
                BigDecimal previous = hit;
                for (int armor = 0; armor <= 100; armor++) {
                    BigDecimal reduced = DamageMitigation.mitigate(hit, new DefenseProfile(armor, toughness, 0, 0));
                    truth(reduced.signum() >= 0 && reduced.compareTo(previous) <= 0, "more armor must not increase damage");
                    previous = reduced;
                }
            }
            for (int armor = 0; armor <= 30; armor++) {
                BigDecimal previous = hit;
                for (int toughness = 0; toughness <= 20; toughness++) {
                    BigDecimal reduced = DamageMitigation.mitigate(hit, new DefenseProfile(armor, toughness, 0, 0));
                    truth(reduced.signum() >= 0 && reduced.compareTo(previous) <= 0, "more toughness must not increase damage");
                    previous = reduced;
                }
            }
            BigDecimal previous = hit;
            for (int resistance = 0; resistance <= 10; resistance++) {
                BigDecimal reduced = DamageMitigation.mitigate(hit, new DefenseProfile(20, 8, resistance, 0));
                truth(reduced.compareTo(previous) <= 0, "more resistance must not increase damage");
                previous = reduced;
            }
            previous = hit;
            for (int protection = 0; protection <= 30; protection++) {
                BigDecimal reduced = DamageMitigation.mitigate(hit, new DefenseProfile(20, 8, 0, protection));
                truth(reduced.compareTo(previous) <= 0, "more protection must not increase damage");
                previous = reduced;
            }
        }
    }

    private static void shieldDamageAndCosts() {
        ExactCombatState start = new ExactCombatState(bd("100"), bd("100"), bd("10"), bd("50"), true);
        DamageResult overflow = start.applyMitigatedDamage(bd("25"));
        eq("10", overflow.shieldAbsorbed());
        eq("15", overflow.healthDamage());
        eq("85", overflow.state().health());
        eq("0", overflow.state().shield());
        eq("0", overflow.overkill());
        DamageResult mitigated = start.attack(bd("100"), new DefenseProfile(20, 8, 1, 10));
        eq("40.32", mitigated.mitigatedDamage());
        eq("10", mitigated.shieldAbsorbed());
        eq("30.32", mitigated.healthDamage());
        DamageResult lethal = start.applyMitigatedDamage(bd("200"));
        truth(lethal.killed(), "lethal result");
        eq("90", lethal.overkill());
        eq("0", lethal.state().health());
        DamageResult deadHit = lethal.state().applyMitigatedDamage(BigDecimal.ONE);
        truth(!deadHit.didDamage() && !deadHit.killed(), "dead entity not killed again");
        eq("1", deadHit.overkill());
        eq("0", lethal.state().regenerateShield(bd("50")).shield());
        rejects(() -> lethal.state().spendShield(BigDecimal.ONE));
        ExactCombatState spent = start.spendShield(bd("9.999"));
        eq("0.001", spent.shield());
        eq("100", spent.health());
        rejects(() -> spent.spendShield(bd("0.002")));
        eq("0.001", spent.shield());
        eq("0", spent.spendShield(bd("0.001")).shield());
        eq("50", spent.regenerateShield(CombatAmounts.CAP).shield());
        eq("100", spent.regenerateShield(CombatAmounts.CAP).health());
        rejects(() -> spent.regenerateShield(bd("-1")));
    }

    private static void noToggleRefillOrLifeRegeneration() {
        ExactCombatState state = new ExactCombatState(bd("25"), bd("100"), bd("3"), bd("50"), true);
        for (int i = 0; i < 1000; i++) state = state.withShieldEnabled(false).withShieldEnabled(true);
        eq("3", state.shield());
        eq("25", state.health());
        DamageResult disabled = state.withShieldEnabled(false).applyMitigatedDamage(BigDecimal.ONE);
        eq("3", disabled.state().shield());
        eq("24", disabled.state().health());
        eq("0", disabled.shieldAbsorbed());
        state = disabled.state().withShieldEnabled(true).regenerateShield(BigDecimal.ONE);
        eq("24", state.health());
        eq("4", state.shield());
        ExactCombatState larger = state.withMaximums(CombatAmounts.CAP, CombatAmounts.CAP);
        eq("24", larger.health());
        eq("4", larger.shield());
        ExactCombatState smaller = larger.withMaximums(bd("20"), bd("2"));
        eq("20", smaller.health());
        eq("2", smaller.shield());
        eq("20", smaller.withMaximums(CombatAmounts.CAP, CombatAmounts.CAP).health());
        eq("2", smaller.withMaximums(CombatAmounts.CAP, CombatAmounts.CAP).shield());
    }

    private static void persistenceRoundTrips() {
        ExactCombatState original = ExactCombatState.full(HighPowerLevel.LV5)
                .spendShield(bd("0.000000000000000000000000001"))
                .withShieldEnabled(false)
                .applyMitigatedDamage(HighPowerLevel.LV1.damage()).state();
        Map<String, String> saved = original.toDecimalMap();
        truth(saved.get("health").equals(HighPowerLevel.LV5.maxHealth().subtract(HighPowerLevel.LV1.damage()).toPlainString()), "save uses decimal string");
        truth(saved.get("shield").equals(HighPowerLevel.LV5.maxShield().subtract(bd("0.000000000000000000000000001")).toPlainString()), "save preserves sub-unit delta");
        for (String value : saved.values()) truth(!value.contains("E"), "no exponent in emitted snapshot");
        ExactCombatState restored = ExactCombatState.fromDecimalMap(saved);
        truth(original.equals(restored), "full state exact round trip");
        for (int i = 0; i < 100; i++) restored = ExactCombatState.fromDecimalMap(restored.toDecimalMap());
        truth(original.equals(restored), "repeated persistence never drifts");
        Map<String, String> scientific = new HashMap<>(saved);
        scientific.put("maxHealth", "2e7");
        truth(original.equals(ExactCombatState.fromDecimalMap(scientific)), "valid scientific input parsed without loss");
        rejects(() -> saved.put("health", "1"));
    }

    private static void invalidPersistenceFailsClosed() {
        Map<String, String> baseline = ExactCombatState.full(HighPowerLevel.LV1).toDecimalMap();
        for (String key : baseline.keySet()) {
            Map<String, String> missing = new HashMap<>(baseline);
            missing.remove(key);
            rejects(() -> ExactCombatState.fromDecimalMap(missing));
        }
        for (String bad : new String[] {"", "NaN", "Infinity", "-1", "100000000000000000001", "1e2147483647", "0e-2147483647", "1e-2147483647", "0." + "0".repeat(600) + "1"}) {
            for (String field : new String[] {"health", "maxHealth", "shield", "maxShield"}) {
                Map<String, String> corrupt = new HashMap<>(baseline);
                corrupt.put(field, bad);
                rejects(() -> ExactCombatState.fromDecimalMap(corrupt));
            }
        }
        Map<String, String> future = new HashMap<>(baseline);
        future.put("version", "2");
        rejects(() -> ExactCombatState.fromDecimalMap(future));
        Map<String, String> invalidFlag = new HashMap<>(baseline);
        invalidFlag.put("shieldEnabled", "yes");
        rejects(() -> ExactCombatState.fromDecimalMap(invalidFlag));
        Map<String, String> invalidMax = new HashMap<>(baseline);
        invalidMax.put("maxHealth", "1");
        rejects(() -> ExactCombatState.fromDecimalMap(invalidMax));
    }

    private static void randomizedDamageConservation() {
        Random random = new Random(0xACA21L);
        for (int i = 0; i < 5000; i++) {
            BigDecimal maxHealth = BigDecimal.valueOf(random.nextInt(100000) + 1L, 2);
            BigDecimal health = maxHealth.multiply(BigDecimal.valueOf(random.nextInt(101))).divide(bd("100"));
            BigDecimal shield = BigDecimal.valueOf(random.nextInt(100000), 2);
            boolean enabled = random.nextBoolean();
            ExactCombatState state = new ExactCombatState(health, maxHealth, shield, shield, enabled);
            BigDecimal raw = BigDecimal.valueOf(random.nextInt(1000000), 3);
            DefenseProfile defense = new DefenseProfile(random.nextInt(31), random.nextInt(21), random.nextInt(6), random.nextInt(21));
            DamageResult hit = state.attack(raw, defense);
            eq(hit.mitigatedDamage(), hit.shieldAbsorbed().add(hit.healthDamage()).add(hit.overkill()));
            eq(hit.shieldAbsorbed(), state.shield().subtract(hit.state().shield()));
            eq(hit.healthDamage(), state.health().subtract(hit.state().health()));
            truth(hit.mitigatedDamage().compareTo(raw) <= 0, "mitigation cannot amplify");
            truth(hit.state().health().signum() >= 0 && hit.state().shield().signum() >= 0, "no underflow");
            if (!enabled) eq(shield, hit.state().shield());
            truth(hit.state().equals(ExactCombatState.fromDecimalMap(hit.state().toDecimalMap())), "random round trip");
        }
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
    private static void eq(String expected, BigDecimal actual) { eq(bd(expected), actual); }
    private static void eq(BigDecimal expected, BigDecimal actual) {
        assertions++;
        if (expected.compareTo(actual) != 0) throw new AssertionError(expected.toPlainString() + " != " + actual.toPlainString());
    }
    private static void truth(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static void rejects(Runnable action) {
        assertions++;
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Expected invalid operation to be rejected");
    }
}

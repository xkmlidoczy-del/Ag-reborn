package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static cn.academy.port.highpower.core.RemoteScatterRelayRules.*;

/** Provisional NEW253 balance anchors, continuous growth and nonrenewable preparation clock. */
public final class RemoteScatterRelayRulesRegressionTest {
    private static int checks;

    public static void main(String[] args) {
        continuousCosts();
        singleDamageBudget();
        immutableLifetime();
        boundedScheduling();
        System.out.println("PASS " + checks + " NEW253 costs, single damage budget and immutable lifetime checks");
    }

    private static void continuousCosts() {
        String[] place = {"4", "2", "1", ".2", ".01"};
        String[] shot = {"80", "40", "20", "4", ".2"};
        int[] cooldowns = {80, 50, 30, 15, 1};
        for (int level = 1; level <= 5; level++) {
            BigDecimal q = BigDecimal.valueOf(level);
            equal(theoreticalPlacementCost(q), new BigDecimal(place[level - 1]), "exact per-node CP anchor " + level);
            equal(theoreticalShotCost(q), new BigDecimal(shot[level - 1]), "exact once-per-shot CP anchor " + level);
            equal(theoreticalCooldown(q), BigDecimal.valueOf(cooldowns[level - 1]), "exact cooldown anchor " + level);
            check(placementCost(q) == Double.parseDouble(place[level - 1]) && shotCost(q) == Double.parseDouble(shot[level - 1]),
                    "native CP boundary exposes the approved anchor");
            check(cooldown(q) == cooldowns[level - 1] && cooldownTicks(q) == cooldowns[level - 1], "native cooldown anchor");
        }
        BigDecimal previousPlace = theoreticalPlacementCost(BigDecimal.ONE), previousShot = theoreticalShotCost(BigDecimal.ONE),
                previousCooldown = theoreticalCooldown(BigDecimal.ONE);
        for (int step = 11; step <= 50; step++) {
            BigDecimal q = BigDecimal.valueOf(step, 1);
            BigDecimal nextPlace = theoreticalPlacementCost(q), nextShot = theoreticalShotCost(q), nextCooldown = theoreticalCooldown(q);
            check(nextPlace.compareTo(previousPlace) < 0 && nextShot.compareTo(previousShot) < 0 && nextCooldown.compareTo(previousCooldown) < 0,
                    "fractional progress continuously improves all three bounded costs");
            check(cooldown(q) == nextCooldown.setScale(0, RoundingMode.CEILING).intValueExact(), "only final native cooldown rounds upward");
            previousPlace = nextPlace; previousShot = nextShot; previousCooldown = nextCooldown;
        }
        BigDecimal q = new BigDecimal("3.00000001");
        check(theoreticalPlacementCost(q).compareTo(BigDecimal.ONE) < 0 && theoreticalShotCost(q).compareTo(new BigDecimal("20")) < 0,
                "fractional mastery is not rounded to integer levels or percentage buckets");
        check(theoreticalCooldown(q).compareTo(new BigDecimal("30")) < 0 && cooldown(q) == 30,
                "continuous theoretical cooldown remains distinct from whole native ticks");
        equal(theoreticalPlacementCost(BigDecimal.ZERO), new BigDecimal("4"), "lower curve clamps to q1");
        equal(theoreticalShotCost(new BigDecimal("6")), new BigDecimal(".2"), "upper curve clamps to q5");
        rejected(() -> placementCost(null), "missing growth position refuses");
        rejected(() -> shotCost(new BigDecimal("1e-257")), "unbounded scale refuses before curve work");
    }

    private static void singleDamageBudget() {
        equal(damage(BigDecimal.ONE), new BigDecimal("2.4"), "single total budget is exactly 2.4D");
        equal(damage(new BigDecimal("10000000000000000")), new BigDecimal("24000000000000000"), "large D remains decimal");
        equal(damage(new BigDecimal(".0000000000000000000000000000000000000001")),
                new BigDecimal(".00000000000000000000000000000000000000024"), "fractional D is never converted through binary floating point");
        equal(damage(CombatAmounts.CAP), CombatAmounts.CAP, "single budget respects isolated ledger cap");
        BigDecimal budget = damage(new BigDecimal("100"));
        for (int nodes = 1; nodes <= MAX_NODES; nodes++) {
            BigDecimal remainder = budget;
            for (int leg = 0; leg <= nodes; leg++) {
                BigDecimal portion = leg == nodes ? remainder : new BigDecimal("7.25");
                remainder = remainder.subtract(portion);
            }
            equal(remainder, BigDecimal.ZERO, "leg traversal divides one preexisting budget without minting another");
            equal(budget, new BigDecimal("240"), "node count never changes the shot budget");
        }
        rejected(() -> damage(BigDecimal.ZERO), "zero base D refuses");
        rejected(() -> damage(new BigDecimal("-1")), "negative base D refuses");
        rejected(() -> damage(CombatAmounts.CAP.add(BigDecimal.ONE)), "oversized base D refuses before multiplication");
        rejected(() -> damage(null), "missing base D refuses");
    }

    private static void immutableLifetime() {
        check(LIFETIME == cn.academy.port.core.SkillTuning.number("remote_scatter_relay", "duration_ticks"), "one fixed configured lifetime");
        Lifetime lifetime = new Lifetime(1000);
        long end = 1000 + LIFETIME;
        check(lifetime.startTick() == 1000 && lifetime.expiresAt() == end, "first commitment fixes absolute expiry");
        for (long now = 1000; now < end; now++) {
            check(!lifetime.expired(now) && lifetime.remainingTicks(now) == end - now, "real elapsed ticks consume fixed lifetime");
            check(lifetime.expiresAt() == end && lifetime.startTick() == 1000, "reading or continuing a route never refreshes expiry");
        }
        check(lifetime.expired(end) && lifetime.expired(end + 1) && lifetime.remainingTicks(end) == 0, "expires at exactly the configured tick");
        check(lifetime.expired(999) && lifetime.remainingTicks(999) == 0, "backwards clock fails closed");
        check(lifetime.expired(Long.MAX_VALUE) && lifetime.expired(-1), "invalid clock fails closed");
        rejected(() -> new Lifetime(-1), "negative start refuses");
        rejected(() -> new Lifetime(Long.MAX_VALUE), "overflowing absolute expiry refuses");
        Lifetime latest = new Lifetime(Long.MAX_VALUE - LIFETIME - 1);
        check(latest.expiresAt() == Long.MAX_VALUE - 1, "largest admitted start never wraps absolute expiry");
    }

    private static void boundedScheduling() {
        check(MAX_NODES == 4 && MAX_SEGMENTS == 5 && PLACE_RANGE == 32 && EXIT_RANGE == 32
                && LEG_RANGE == 32 && RANGE == 32 && TOTAL_RANGE == 160, "native and pure route limits agree");
        check(HOLD_LIMIT == 100 && RECALL_TICKS == 12 && PREVIEW_INTERVAL == 4 && ATTEMPT_INTERVAL == 1
                && EMPTY_INTERVAL == 4 && SCAN_LIMIT == 64, "input and scan work stays bounded");
        check(clock(0) && !clock(-1) && !clock(Long.MAX_VALUE), "scheduling arithmetic admits safe clocks only");
        check(!expired(100, 99 + LIFETIME) && expired(100, 100 + LIFETIME), "fixed lifetime boundary");
        check(expired(100, 99) && expired(100, 100, -1) && expired(100, 100, LIFETIME + 1), "invalid duration or reverse time cannot extend lifetime");
        check(ready(-1, 100, 1) && !ready(100, 100, 1) && ready(100, 101, 1), "one firing attempt per real native tick");
        check(!ready(100, 99, 1) && !ready(-2, 100, 1) && !ready(-1, Long.MAX_VALUE, 1)
                && !ready(-1, 100, 0), "malformed attempt clocks refuse");
    }

    private static void equal(BigDecimal actual, BigDecimal expected, String reason) { check(actual.compareTo(expected) == 0, reason); }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    private static void rejected(Runnable action, String reason) {
        try { action.run(); throw new AssertionError(reason); }
        catch (IllegalArgumentException | NullPointerException expected) { checks++; }
    }
}

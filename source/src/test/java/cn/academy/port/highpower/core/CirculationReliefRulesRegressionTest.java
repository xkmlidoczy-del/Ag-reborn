package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Random;

import static cn.academy.port.highpower.core.CirculationReliefRules.*;

/** Standalone exact-resource, side-sector and persisted native-tick quota checks for NEW432. */
public final class CirculationReliefRulesRegressionTest {
    private static int checks;

    public static void main(String[] args) {
        exactResources();
        horizontalSectors();
        rollingQuota();
        quotaPersistence();
        quotaReferenceModel();
        economics();
        System.out.println("CirculationReliefRulesRegressionTest passed " + checks + " checks");
    }

    private static void exactResources() {
        ExactCombatState before = state("100", "7.125", true);
        var partial = absorb(before, d("10.375"));
        equal(partial.absorbed(), d("7.125"), "partial base S pays exactly 1:1");
        equal(partial.state().shield(), BigDecimal.ZERO, "partial hit exhausts existing base S");
        equal(partial.state().health(), d("96.75"), "HP receives the remainder exactly once");
        equal(before.shield(), d("7.125"), "input base S remains immutable");
        equal(before.health(), d("100"), "input HP remains immutable");
        check(!partial.state().shieldEnabled(), "committed result disables another generic shield pass");
        check(partial.state().maxHealth().equals(before.maxHealth())
                && partial.state().maxShield().equals(before.maxShield()), "capacities are not changed");
        check(absorb(before.withShieldEnabled(false), d("10.375")).equals(partial),
                "relief accesses existing base S independently of generic shield flag");

        var full = absorb(before, d(".125"));
        equal(full.absorbed(), d(".125"), "fully absorbed hit reports exact payment");
        equal(full.state().shield(), d("7"), "fully absorbed hit debits only existing base S");
        equal(full.state().health(), before.health(), "fully absorbed hit does not spend HP");
        var zero = absorb(before, BigDecimal.ZERO);
        equal(zero.absorbed(), BigDecimal.ZERO, "zero hit cannot claim an absorption");
        equal(zero.state().shield(), before.shield(), "zero hit does not spend S");
        equal(zero.state().health(), before.health(), "zero hit does not spend HP");
        var empty = absorb(state(".125", "0", true), d(".1"));
        equal(empty.absorbed(), BigDecimal.ZERO, "empty base S cannot create absorption");
        equal(empty.state().health(), d(".025"), "empty base S passes exact damage to HP");
        var dead = absorb(state("0", "50", true), d("100"));
        check(dead.state().isDead() && !dead.state().shieldEnabled(), "death remains authoritative");
        equal(dead.absorbed(), BigDecimal.ZERO, "dead combatants cannot absorb");
        equal(dead.state().shield(), d("50"), "death cannot spend S");
        var lethal = absorb(state(".25", ".125", true), d("100"));
        equal(lethal.absorbed(), d(".125"), "overkill cannot overstate absorption");
        check(lethal.state().isDead() && lethal.state().shield().signum() == 0,
                "lethal residual clamps HP and shield at zero");

        String[] values = {"0", "1e-256", "3e-256", "1e-30", ".1", "7.125",
                "123456789012345.678901234567890123456789", "99999999999999999999.99999999999999999999", "1e20"};
        for (String balance : values) {
            for (String damage : values) {
                ExactCombatState original = new ExactCombatState(CombatAmounts.CAP, CombatAmounts.CAP,
                        d(balance), CombatAmounts.CAP, false);
                BigDecimal incoming = d(damage);
                var result = absorb(original, incoming);
                BigDecimal spent = original.shield().subtract(result.state().shield());
                BigDecimal lost = original.health().subtract(result.state().health());
                equal(result.absorbed(), spent, "reported absorption equals exact shield debit");
                equal(spent, incoming.min(original.shield()), "existing base S always pays first");
                equal(spent.add(lost), incoming, "damage remains conserved up to the ledger cap");
                check(result.equals(VectorParryRules.absorb(original, incoming)),
                        "relief shares the established 1:1 base-S pipeline");
            }
        }
        rejects(() -> absorb(before, d("-.01")), "negative mitigated damage refuses");
        rejects(() -> absorb(before, d("100000000000000000001")), "over-cap damage refuses");
        rejects(() -> absorb(before, d("1e-257")), "unsupported fractional scale refuses");
        rejects(() -> absorb(before, d("1e257")), "unsupported exponent refuses");
        rejects(() -> absorb(before, null), "absent damage refuses");
        rejects(() -> absorb(null, BigDecimal.ONE), "absent state refuses");
    }

    private static void horizontalSectors() {
        VectorParryRules.Point origin = p(0, 0, 0), forward = p(0, 0, 1);
        check(side(origin, forward, p(1, 0, 0)), "right sector center accepts");
        check(side(origin, forward, p(-1, 0, 0)), "left sector center accepts");
        check(!side(origin, forward, forward), "front center refuses");
        check(!side(origin, forward, p(0, 0, -1)), "rear center refuses");
        for (double x : new double[] {-1, 1}) {
            for (double z : new double[] {-1, 1}) {
                check(!side(origin, forward, p(x, 0, z)), "45-degree diagonal is outside side sectors");
            }
        }
        check(side(origin, forward, p(1, 1e100, 0)), "vertical source offset does not change sector");
        check(side(origin, p(0, 1e100, 1), p(1, 0, 0)), "look pitch does not change horizontal sector");
        check(side(p(-1, 0, 0), forward, origin), "absolute world origin is a valid source");
        check(side(origin, forward, p(1e100, 0, 0)), "4m sink range does not limit attacker distance");
        check(!side(origin, forward, origin), "coincident source has no horizontal direction");
        check(!side(origin, forward, p(0, 1, 0)), "vertical-only source refuses");
        check(!side(origin, p(0, 1, 0), p(1, 0, 0)), "vertical-only look refuses");
        check(!side(origin, origin, p(1, 0, 0)), "zero look refuses");

        double boundary = Math.sqrt(3);
        for (double xSign : new double[] {-1, 1}) {
            for (double zSign : new double[] {-1, 1}) {
                // sqrt(3) rounds below the mathematical value: (sqrt(3), 1) lies
                // just outside the side sector, while its next-up X lies inside.
                check(!side(origin, forward, p(xSign * boundary, 0, zSign)),
                        "represented outward boundary refuses without angular epsilon");
                check(!side(origin, forward, p(xSign * Math.nextDown(boundary), 0, zSign)),
                        "next outward representable point refuses");
                check(side(origin, forward, p(xSign * Math.nextUp(boundary), 0, zSign)),
                        "next inward representable point accepts");
                for (double scale : new double[] {Double.MIN_NORMAL, 1e-200, 1, 1e200, 1e307}) {
                    for (double component : new double[] {Math.nextDown(boundary), boundary, Math.nextUp(boundary)}) {
                        VectorParryRules.Point source = p(xSign * component * scale, 0, zSign * scale);
                        check(side(origin, forward, source) == exactSide(origin, forward, source),
                                "all four scaled boundaries match exact binary-vector comparison");
                    }
                }
            }
        }

        Random random = new Random(432);
        for (int i = 0; i < 1000; i++) {
            double yaw = random.nextDouble() * 2 * Math.PI;
            VectorParryRules.Point look = p(Math.sin(yaw), random.nextDouble() * 2 - 1, Math.cos(yaw));
            VectorParryRules.Point translated = p(random.nextDouble() * 59_998_000 - 29_999_000,
                    random.nextDouble() * 512 - 256, random.nextDouble() * 59_998_000 - 29_999_000);
            for (int sectorSign : new int[] {-1, 1}) {
                for (int edge : new int[] {60, 120}) {
                    double inward = yaw + sectorSign * Math.toRadians(edge + (edge == 60 ? 1e-5 : -1e-5));
                    double outward = yaw + sectorSign * Math.toRadians(edge + (edge == 60 ? -1e-5 : 1e-5));
                    check(side(origin, look, p(Math.sin(inward) * 64, 0, Math.cos(inward) * 64)),
                            "rotated inward point accepts at each side boundary");
                    check(!side(origin, look, p(Math.sin(outward) * 64, 0, Math.cos(outward) * 64)),
                            "rotated outward point refuses at each side boundary");
                    VectorParryRules.Point source = p(translated.x() + Math.sin(inward) * 64,
                            translated.y(), translated.z() + Math.cos(inward) * 64);
                    check(side(translated, look, source), "large world coordinates preserve the inner sector");
                    check(side(translated, look, source) == exactSide(translated, look, source),
                            "rotated translated vectors agree with exact predicate");
                }
            }
        }

        VectorParryRules.Point worldEdge = p(30_000_000, 320, -30_000_000);
        check(side(worldEdge, forward, p(Math.nextUp(worldEdge.x()), 320, worldEdge.z())),
                "one world-coordinate ULP is still a real horizontal direction");
        check(side(origin, p(0, 0, Double.MIN_VALUE), p(Double.MIN_VALUE, 0, 0)),
                "subnormal side/look vectors remain valid");
        check(side(origin, p(Double.MIN_VALUE, 0, Double.MIN_VALUE), p(-Double.MIN_VALUE, 0, Double.MIN_VALUE)),
                "opposed subnormal products do not collapse the side direction");
        check(!side(origin, p(Double.MIN_VALUE, 0, Double.MIN_VALUE), p(Double.MIN_VALUE, 0, Double.MIN_VALUE)),
                "subnormal forward vectors still refuse");
        check(side(origin, p(Double.MAX_VALUE, 0, Double.MAX_VALUE), p(-Double.MAX_VALUE, 0, Double.MAX_VALUE)),
                "finite huge vectors avoid product overflow");
        check(!side(p(-Double.MAX_VALUE, 0, 0), forward, p(Double.MAX_VALUE, 0, 0)),
                "overflowing displacement refuses instead of inventing direction");
        for (VectorParryRules.Point invalid : new VectorParryRules.Point[] {null, p(Double.NaN, 0, 1),
                p(0, Double.NaN, 1), p(0, 0, Double.NaN), p(Double.NEGATIVE_INFINITY, 0, 0),
                p(0, Double.POSITIVE_INFINITY, 0), p(0, 0, Double.POSITIVE_INFINITY)}) {
            check(!side(invalid, forward, p(1, 0, 0)), "invalid owner refuses");
            check(!side(origin, invalid, p(1, 0, 0)), "invalid look refuses");
            check(!side(origin, forward, invalid), "invalid source refuses");
        }
    }

    private static void rollingQuota() {
        check(WINDOW_TICKS == 20 && MAX_ABSORPTIONS == 4 && GROUND_SINK_RANGE == cn.academy.port.core.SkillTuning.number("circulation_relief", "reach"),
                "native window, positive hit cap and sink range stay explicit");
        Quota quota = Quota.EMPTY;
        check(quota.available() && quota.nativeTick().equals(quota), "empty quota remains empty on a native tick");
        for (int i = 0; i < MAX_ABSORPTIONS; i++) {
            check(quota.available(), "first four absorptions are available in a single native tick");
            quota = quota.afterAbsorption(d("1e-256"));
        }
        Quota full = quota;
        check(!full.available(), "four positive absorptions fill the rolling quota");
        rejects(() -> full.afterAbsorption(BigDecimal.ONE), "fifth absorption refuses");
        check(full.afterAbsorption(BigDecimal.ZERO) == full, "zero absorption does not consume even an exhausted quota");
        for (int i = 0; i < 100; i++) {
            check(Quota.restore(full.remainingTicks()).equals(full), "repeated persistence cannot age or reset quota");
        }
        for (int tick = 1; tick < WINDOW_TICKS; tick++) {
            quota = quota.nativeTick();
            check(!quota.available(), "quota stays exhausted before the twentieth native tick");
            check(quota.firstRemaining() == WINDOW_TICKS - tick, "one native tick decrements one unit");
        }
        check(quota.nativeTick().equals(Quota.EMPTY), "the twentieth actual native tick expires old absorptions");
        check(full.firstRemaining() == WINDOW_TICKS, "aging does not mutate saved or captured snapshots");

        Quota staggered = Quota.EMPTY.afterAbsorption(BigDecimal.ONE);
        staggered = tick(staggered, 4).afterAbsorption(BigDecimal.ONE);
        staggered = tick(staggered, 5).afterAbsorption(BigDecimal.ONE);
        staggered = tick(staggered, 9).afterAbsorption(BigDecimal.ONE);
        check(Arrays.equals(staggered.remainingTicks(), new int[] {20, 11, 6, 2}),
                "staggered hits retain independent ages without fixed-window buckets");
        check(!staggered.nativeTick().available(), "oldest hit still occupies quota at age nineteen");
        Quota firstExpiry = tick(staggered, 2);
        check(Arrays.equals(firstExpiry.remainingTicks(), new int[] {18, 9, 4, 0}),
                "only the oldest hit expires after twenty actual ticks");
        Quota replacement = firstExpiry.afterAbsorption(BigDecimal.ONE);
        check(Arrays.equals(replacement.remainingTicks(), new int[] {20, 18, 9, 4}),
                "new hit fills the expired slot without refreshing remaining old hits");
        check(!replacement.available(), "replacement restores the four-hit cap");
        check(tick(replacement, WINDOW_TICKS).equals(Quota.EMPTY), "all clocks eventually drain through actual ticks");

        var noShield = absorb(state("100", "0", false), BigDecimal.ONE);
        var noDamage = absorb(state("100", "100", false), BigDecimal.ZERO);
        var partial = absorb(state("100", ".125", false), BigDecimal.ONE);
        check(Quota.EMPTY.afterAbsorption(noShield.absorbed()).equals(Quota.EMPTY), "zero-S hit does not consume quota");
        check(Quota.EMPTY.afterAbsorption(noDamage.absorbed()).equals(Quota.EMPTY), "zero-damage hit does not consume quota");
        check(Quota.EMPTY.afterAbsorption(partial.absorbed()).equals(new Quota(20, 0, 0, 0)),
                "positive partial absorption consumes exactly one quota slot");
        rejects(() -> full.afterAbsorption(d("-.1")), "negative absorption refuses before exhausted-state handling");
        rejects(() -> Quota.EMPTY.afterAbsorption(null), "absent absorption refuses");
        rejects(() -> Quota.EMPTY.afterAbsorption(d("1e-257")), "unsupported absorption scale refuses");
        rejects(() -> Quota.EMPTY.afterAbsorption(CombatAmounts.CAP.add(BigDecimal.ONE)), "over-cap absorption refuses");
    }

    private static void quotaPersistence() {
        int[] saved = {20, 17, 4, 1};
        Quota restored = Quota.restore(saved);
        saved[0] = 0;
        check(restored.firstRemaining() == 20, "restoration does not retain mutable input array");
        int[] output = restored.remainingTicks();
        output[3] = 0;
        check(!restored.available() && restored.fourthRemaining() == 1, "serialization snapshot cannot mutate quota");
        check(restored.equals(new Quota(20, 17, 4, 1))
                && restored.hashCode() == new Quota(20, 17, 4, 1).hashCode(), "quota has structural snapshot equality");
        check(!restored.equals(restored.nativeTick()), "actual aging changes snapshot equality");
        rejects(() -> Quota.restore(null), "absent persisted clocks refuse");
        for (int[] invalid : new int[][] {{}, {0}, {0, 0, 0}, {0, 0, 0, 0, 0},
                {-1, 0, 0, 0}, {20, -1, 0, 0}, {20, 10, -1, 0}, {20, 10, 1, -1},
                {21, 0, 0, 0}, {20, 21, 0, 0}, {20, 10, 21, 0}, {20, 10, 1, 21},
                {0, 20, 0, 0}, {20, 0, 1, 0}, {20, 10, 0, 1}, {1, 2, 3, 4},
                {Integer.MAX_VALUE, 0, 0, 0}, {Integer.MIN_VALUE, 0, 0, 0},
                {20, 10, 1, Integer.MIN_VALUE}}) {
            rejects(() -> Quota.restore(invalid), "malformed saved clocks refuse instead of resetting");
        }
        // Cover every representable canonical snapshot, including equal-age simultaneous hits.
        for (int first = 0; first <= WINDOW_TICKS; first++) {
            for (int second = 0; second <= first; second++) {
                for (int third = 0; third <= second; third++) {
                    for (int fourth = 0; fourth <= third; fourth++) {
                        Quota state = new Quota(first, second, third, fourth);
                        check(Quota.restore(state.remainingTicks()).equals(state), "every canonical save round-trips exactly");
                        check(state.available() == (fourth == 0), "each save retains occupied-slot availability");
                        check(state.nativeTick().equals(new Quota(Math.max(0, first - 1), Math.max(0, second - 1),
                                Math.max(0, third - 1), Math.max(0, fourth - 1))), "each saved age advances independently");
                    }
                }
            }
        }
    }

    private static void quotaReferenceModel() {
        Random random = new Random(20_432);
        ArrayDeque<Integer> positiveTicks = new ArrayDeque<>();
        Quota quota = Quota.EMPTY;
        int nativeTime = 0;
        for (int operation = 0; operation < 20_000; operation++) {
            if (random.nextInt(3) == 0) {
                nativeTime++;
                quota = quota.nativeTick();
                while (!positiveTicks.isEmpty() && nativeTime - positiveTicks.peekFirst() >= WINDOW_TICKS) {
                    positiveTicks.removeFirst();
                }
            } else if (random.nextInt(4) == 0) {
                check(quota.afterAbsorption(BigDecimal.ZERO).equals(quota), "reference zero hit preserves all ages");
            } else if (positiveTicks.size() < MAX_ABSORPTIONS) {
                quota = quota.afterAbsorption(d(".125"));
                positiveTicks.addLast(nativeTime);
            } else {
                Quota captured = quota;
                rejects(() -> captured.afterAbsorption(BigDecimal.ONE), "reference fifth positive hit refuses");
            }
            check(quota.available() == (positiveTicks.size() < MAX_ABSORPTIONS),
                    "native-tick quota matches independent rolling event history");
            int[] expected = new int[MAX_ABSORPTIONS];
            int index = positiveTicks.size() - 1;
            for (int time : positiveTicks) expected[index--] = WINDOW_TICKS - (nativeTime - time);
            check(Arrays.equals(quota.remainingTicks(), expected), "all remaining clocks match event timestamps");
            if (operation % 7 == 0) quota = Quota.restore(quota.remainingTicks());
        }
    }

    private static void economics() {
        check(PAYMENT_PERIOD == VectorParryRules.PAYMENT_PERIOD && RESTART_FENCE == VectorParryRules.RESTART_FENCE,
                "economics retain established native payment and restart fences");
        BigDecimal lastActivation = null, lastCooldown = null;
        for (int i = 0; i <= 80; i++) {
            BigDecimal q = BigDecimal.ONE.add(BigDecimal.valueOf(i, 1).divide(BigDecimal.valueOf(2)));
            BigDecimal activation = theoreticalActivationCost(q), cooldown = theoreticalCooldown(q);
            equal(activation, VectorParryRules.theoreticalActivationCost(q), "activation curve exactly reuses vector parry");
            equal(theoreticalSustainCost(q), VectorParryRules.theoreticalSustainCost(q), "sustain curve exactly reuses vector parry");
            equal(cooldown, VectorParryRules.theoreticalCooldown(q), "cooldown curve exactly reuses vector parry");
            check(activationCost(q) == VectorParryRules.activationCost(q)
                    && sustainCost(q) == VectorParryRules.sustainCost(q), "native cost conversions stay identical");
            check(cooldownTicks(q) == VectorParryRules.cooldownTicks(q)
                    && cooldownTicks(q) >= RESTART_FENCE, "native cooldown preserves the physical restart fence");
            if (lastActivation != null) {
                check(activation.compareTo(lastActivation) < 0 && cooldown.compareTo(lastCooldown) < 0,
                        "intermediate growth continuously improves cost and cooldown");
            }
            lastActivation = activation;
            lastCooldown = cooldown;
        }
        check(theoreticalActivationCost(d("3.000000001")).compareTo(theoreticalActivationCost(d("3"))) < 0,
                "sub-percentage activation growth is not quantized");
        check(theoreticalCooldown(d("3.000000001")).compareTo(theoreticalCooldown(d("3"))) < 0,
                "sub-percentage theoretical cooldown growth is not quantized");
        rejects(() -> theoreticalActivationCost(null), "absent activation coordinate refuses");
        rejects(() -> sustainCost(d("1e-257")), "unsupported sustain coordinate refuses");
        rejects(() -> cooldownTicks(d("1e257")), "unsupported cooldown coordinate refuses");
    }

    private static boolean exactSide(VectorParryRules.Point owner, VectorParryRules.Point facing,
                                     VectorParryRules.Point source) {
        BigDecimal x = new BigDecimal(source.x() - owner.x()), z = new BigDecimal(source.z() - owner.z());
        BigDecimal fx = new BigDecimal(facing.x()), fz = new BigDecimal(facing.z());
        BigDecimal dot = x.multiply(fx).add(z.multiply(fz));
        BigDecimal normProduct = x.multiply(x).add(z.multiply(z)).multiply(fx.multiply(fx).add(fz.multiply(fz)));
        return normProduct.signum() > 0 && dot.multiply(dot).multiply(BigDecimal.valueOf(4)).compareTo(normProduct) <= 0;
    }

    private static Quota tick(Quota quota, int actualTicks) {
        for (int i = 0; i < actualTicks; i++) quota = quota.nativeTick();
        return quota;
    }
    private static ExactCombatState state(String health, String shield, boolean enabled) {
        return new ExactCombatState(d(health), d("100"), d(shield), d("100"), enabled);
    }
    private static VectorParryRules.Point p(double x, double y, double z) {
        return new VectorParryRules.Point(x, y, z);
    }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
    private static void equal(BigDecimal actual, BigDecimal expected, String reason) {
        check(actual.compareTo(expected) == 0, reason + ": " + actual + " != " + expected);
    }
    private static void rejects(Runnable action, String reason) {
        try { action.run(); throw new AssertionError(reason); }
        catch (IllegalArgumentException | IllegalStateException | NullPointerException expected) { checks++; }
    }
    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
}

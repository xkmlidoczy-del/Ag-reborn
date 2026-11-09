package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Random;

import static cn.academy.port.highpower.core.AutonomousVectorCircleRules.*;

/** Standalone NEW452 exact-resource, three-dimensional geometry and genuine-tick quota tests. */
public final class AutonomousVectorCircleRulesRegressionTest {
    private static int checks;
    // Independent high-precision trigonometric reference, well beyond binary-vector resolution in these cases.
    private static final BigDecimal COS_80 = d("0.173648177666930348851716626769314796000375677184069387236241378132065822139014735421516613157399574");

    public static void main(String[] args) {
        resources();
        invalidResources();
        geometry();
        quota();
        quotaPersistence();
        quotaReferenceModel();
        economics();
        System.out.println("AutonomousVectorCircleRulesRegressionTest: " + checks + " checks passed");
    }

    private static void resources() {
        ExactCombatState before = state("100", "1000", "7.125", "100", true);
        SharedShieldReserveShares shares = shares("11.25", "13.375");
        Result baseOnly = absorb(before, shares, d(".125"));
        amount(baseOnly.absorbed(), ".125", "base pays exact small damage");
        amount(baseOnly.state().shield(), "7", "base debit is exact");
        check(baseOnly.shares().equals(shares), "small hit preserves both reserve identities");
        amount(baseOnly.state().health(), "100", "full absorption does not touch HP");

        Result lower = absorb(before, shares, d("8.125"));
        amount(lower.state().shield(), "0", "base is exhausted before reserves");
        amount(lower.shares().lower(), "10.25", "lower pays before upper");
        amount(lower.shares().upper(), "13.375", "lower-only payment preserves upper");
        Result upper = absorb(before, shares, d("20.5"));
        amount(upper.shares().lower(), "0", "lower is exhausted before upper");
        amount(upper.shares().upper(), "11.25", "upper pays remaining exact debit");
        Result remainder = absorb(before, shares, d("33.875"));
        amount(remainder.absorbed(), "31.75", "only existing base and reserves absorb");
        amount(remainder.state().health(), "97.875", "one exact residual-HP settlement");
        check(remainder.shares().equals(SharedShieldReserveShares.ZERO), "all existing reserve is exhausted");
        amount(remainder.state().shield(), "0", "base stays exhausted");
        check(!remainder.state().shieldEnabled(), "generic absorption remains disabled");
        check(remainder.state().maxHealth().equals(before.maxHealth())
                && remainder.state().maxShield().equals(before.maxShield()), "capacities remain unchanged");
        check(absorb(before.withShieldEnabled(false), shares, d("33.875")).equals(remainder),
                "generic shield flag cannot change circle payment");
        amount(before.health(), "100", "input HP remains immutable");
        amount(before.shield(), "7.125", "input base S remains immutable");
        amount(shares.lower(), "11.25", "input reserve remains immutable");

        Result dead = absorb(state("0", "100", "50", "100", true), shares, d("100"));
        amount(dead.absorbed(), "0", "dead combatant cannot absorb");
        amount(dead.state().shield(), "50", "death does not spend or refill base");
        check(dead.shares().equals(shares) && dead.state().isDead(), "death preserves reserves and zero HP");
        Result zero = absorb(before, shares, BigDecimal.ZERO);
        amount(zero.absorbed(), "0", "native full block produces no absorption");
        check(zero.state().equals(before.withShieldEnabled(false)) && zero.shares().equals(shares),
                "zero damage preserves all balances");
        Result empty = absorb(state(".125", "100", "0", "100", true), SharedShieldReserveShares.ZERO, d(".1"));
        amount(empty.absorbed(), "0", "zero S cannot create absorption");
        amount(empty.state().health(), ".025", "zero S leaves exact HP damage");
        Result lethal = absorb(state(".25", "100", ".125", "100", true), shares(".25", ".5"), d("100"));
        amount(lethal.absorbed(), ".875", "overkill cannot overstate existing 2S absorption");
        check(lethal.state().isDead(), "lethal remainder clamps HP at zero");

        BigDecimal maxBase = d("5e19"), maxShare = d("2.5e19");
        String[] baseValues = {"0", "1e-256", ".1", "123456789012345.678901234567890123456789", "5e19"};
        String[] shareValues = {"0", "3e-256", ".125", "24999999999999999999.99999999999999999999", "2.5e19"};
        String[] damages = {"0", "1e-256", "3e-256", "1e-30", ".1", "11.75", "99999999999999999999.99999999999999999999", "1e20"};
        for (String baseText : baseValues) for (String lowerText : shareValues) for (String upperText : shareValues) {
            ExactCombatState original = new ExactCombatState(CombatAmounts.CAP, CombatAmounts.CAP,
                    d(baseText), maxBase, false);
            SharedShieldReserveShares reserve = shares(lowerText, upperText);
            for (String damageText : damages) {
                BigDecimal damage = d(damageText);
                Result result = absorb(original, reserve, damage);
                BigDecimal basePaid = original.shield().subtract(result.state().shield());
                BigDecimal lowerPaid = reserve.lower().subtract(result.shares().lower());
                BigDecimal upperPaid = reserve.upper().subtract(result.shares().upper());
                BigDecimal hpPaid = original.health().subtract(result.state().health());
                equal(basePaid, damage.min(original.shield()), "base is first exact tranche");
                equal(lowerPaid, damage.subtract(basePaid).min(reserve.lower()), "lower is second exact tranche");
                equal(upperPaid, damage.subtract(basePaid).subtract(lowerPaid).min(reserve.upper()), "upper is last exact tranche");
                equal(result.absorbed(), basePaid.add(lowerPaid).add(upperPaid), "all reported absorption is backed by debit");
                equal(result.absorbed().add(hpPaid), damage, "damage conservation survives 256 fractional places and 1e20 cap");
                check(result.shares().lower().compareTo(maxShare) <= 0 && result.shares().upper().compareTo(maxShare) <= 0,
                        "reserve identities keep their half-S ceilings");
            }
        }
        ExactCombatState cap = state("1e20", "1e20", "5e19", "5e19", true);
        Result capHit = absorb(cap, shares("2.5e19", "2.5e19"), d("1e20"));
        amount(capHit.absorbed(), "1e20", "full existing 2S absorbs the certified cap");
        amount(capHit.state().health(), "1e20", "cap absorption leaves HP intact");
        Result next = absorb(capHit.state(), capHit.shares(), d("1e20"));
        check(next.state().isDead() && next.absorbed().signum() == 0, "repeated hit cannot refill spent 2S");
    }

    private static void invalidResources() {
        ExactCombatState before = state("100", "100", "100", "100", true);
        for (BigDecimal invalid : new BigDecimal[] {null, d("-.1"), d("100000000000000000001"), d("1e-257"), d("1e257")})
            rejects(() -> absorb(before, SharedShieldReserveShares.ZERO, invalid), "invalid damage fails closed");
        rejects(() -> absorb(null, SharedShieldReserveShares.ZERO, BigDecimal.ONE), "absent state refuses");
        rejects(() -> absorb(before, null, BigDecimal.ZERO), "absent shares refuse even for zero damage");
        rejects(() -> absorb(before, shares("50.0000000000000000001", "0"), BigDecimal.ZERO), "corrupt lower share refuses before zero-hit shortcut");
        rejects(() -> absorb(before, shares("0", "50.0000000000000000001"), BigDecimal.ONE), "corrupt upper share refuses");
        rejects(() -> absorb(state("100", "100", "0", "50000000000000000001", false),
                SharedShieldReserveShares.ZERO, BigDecimal.ZERO), "capacity above 1e20 for 2S refuses without awarding credit");
        rejects(() -> shares("-.1", "0"), "negative reserve refuses");
        rejects(() -> shares("0", "1e-257"), "unsupported reserve scale refuses");
        rejects(() -> shares("1e20", "1"), "total reserve beyond cap refuses");
    }

    private static void geometry() {
        var origin = p(0, 0, 0); var forward = p(0, 0, 1);
        check(RANGE == 8 && front(origin, forward, forward), "world origin and front direction remain valid");
        check(front(p(0, 0, -1), forward, origin), "source at world origin is not absence");
        check(frontDirection(p(0, 1, 0), p(0, 1, 0)), "vertical facing and arrival are accepted in 3D");
        check(!frontDirection(p(0, 1, 0), p(0, -1, 0)), "vertical rear arrival refuses");
        check(frontDirection(p(1, 2, 3), p(2, 4, 6)), "all three look components participate");
        check(!front(origin, forward, p(0, 0, -1)), "rear source refuses");
        check(!front(origin, forward, p(1, 0, 0)), "side tangent refuses");
        check(!front(origin, forward, p(0, 1, 0)), "vertical tangent refuses for horizontal look");
        check(!front(origin, forward, origin) && !frontDirection(origin, forward), "zero arrival and look cannot invent direction");
        check(!frontDirection(forward, p(0, 0, -1)), "outward-moving projectile cannot use its front position to qualify");
        check(frontDirection(forward, p(0, 0, 1)), "reverse actual inward segment qualifies");

        // Find the adjoining represented vectors around the true tan(80-degree) boundary.
        double inward = Double.parseDouble("5.671281819617709530994418439863964421625378260689750303215909988875243416680944994179859826981728172");
        while (!referenceFront(forward, p(inward, 0, 1))) inward = Math.nextDown(inward);
        while (referenceFront(forward, p(Math.nextUp(inward), 0, 1))) inward = Math.nextUp(inward);
        double outward = Math.nextUp(inward);
        for (double sign : new double[] {-1, 1}) {
            check(frontDirection(forward, p(sign * inward, 0, 1)), "last represented inward yaw boundary accepts");
            check(!frontDirection(forward, p(sign * outward, 0, 1)), "next represented outward yaw boundary refuses");
            check(frontDirection(forward, p(0, sign * inward, 1)), "last represented inward pitch boundary accepts");
            check(!frontDirection(forward, p(0, sign * outward, 1)), "next represented outward pitch boundary refuses");
        }
        Random random = new Random(452);
        for (int i = 0; i < 1600; i++) {
            double yaw = random.nextDouble() * 2 * Math.PI;
            double pitch = (random.nextDouble() - .5) * Math.PI;
            var look = p(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            var perpendicular = p(Math.cos(yaw), 0, -Math.sin(yaw));
            for (double angle : new double[] {Math.toRadians(80 - 1e-6), Math.toRadians(80 + 1e-6), Math.toRadians(80)}) {
                var direction = p(look.x() * Math.cos(angle) + perpendicular.x() * Math.sin(angle),
                        look.y() * Math.cos(angle), look.z() * Math.cos(angle) + perpendicular.z() * Math.sin(angle));
                check(frontDirection(look, direction) == referenceFront(look, direction), "rotated pitched boundary agrees with independent precise reference");
            }
        }
        check(frontDirection(p(0, 0, Double.MIN_VALUE), p(0, 0, Double.MIN_VALUE)), "finite subnormal vectors remain valid");
        check(frontDirection(p(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE), p(1, 1, 1)), "huge finite look avoids product overflow");
        check(!front(p(-Double.MAX_VALUE, 0, 0), p(1, 0, 0), p(Double.MAX_VALUE, 0, 0)), "overflowing world subtraction refuses");
        check(inRange(origin, origin), "zero range is geometrically valid, with separate direction gate");
        for (var boundary : new VectorParryRules.Point[] {p(8, 0, 0), p(-8, 0, 0), p(0, 8, 0), p(0, -8, 0), p(0, 0, 8), p(0, 0, -8)})
            check(inRange(origin, boundary), "every exact axis sphere boundary accepts");
        check(inRange(origin, p(Math.nextDown(8.0), 0, 0)), "one ULP inward range accepts");
        check(!inRange(origin, p(Math.nextUp(8.0), 0, 0)), "one ULP outward range refuses");
        check(!inRange(origin, p(8, Double.MIN_VALUE, 0)), "tiny nonzero tangent displacement cannot round outside sphere inward");
        check(!inRange(p(.4, 0, 0), p(8.4, 0, 0)), "rounded subtraction cannot admit exact represented coordinates beyond eight metres");
        check(inRange(p(.1, 0, 0), p(8.1, 0, 0)), "represented inside translated boundary remains valid");
        check(!inRange(origin, p(6, 6, 0)), "range is spherical in all three dimensions");
        check(!inRange(origin, p(0, 0, Double.MAX_VALUE)), "huge finite range refuses safely");
        for (var invalid : new VectorParryRules.Point[] {null, p(Double.NaN, 0, 1), p(0, Double.NaN, 1),
                p(0, 0, Double.POSITIVE_INFINITY), p(Double.NEGATIVE_INFINITY, 0, 0)}) {
            check(!front(invalid, forward, forward) && !front(origin, invalid, forward) && !front(origin, forward, invalid), "invalid sector inputs refuse");
            check(!frontDirection(invalid, forward) && !frontDirection(forward, invalid), "invalid actual-arrival vectors refuse");
            check(!inRange(invalid, origin) && !inRange(origin, invalid), "invalid range inputs refuse");
        }
    }

    private static boolean referenceFront(VectorParryRules.Point facing, VectorParryRules.Point direction) {
        BigDecimal fx = new BigDecimal(facing.x()), fy = new BigDecimal(facing.y()), fz = new BigDecimal(facing.z());
        BigDecimal dx = new BigDecimal(direction.x()), dy = new BigDecimal(direction.y()), dz = new BigDecimal(direction.z());
        BigDecimal dot = fx.multiply(dx).add(fy.multiply(dy)).add(fz.multiply(dz));
        BigDecimal norm = fx.pow(2).add(fy.pow(2)).add(fz.pow(2)).multiply(dx.pow(2).add(dy.pow(2)).add(dz.pow(2)));
        return dot.signum() > 0 && dot.pow(2).compareTo(norm.multiply(COS_80.pow(2))) >= 0;
    }

    private static void quota() {
        check(WINDOW_TICKS == 20 && MAX_ABSORPTIONS == 8 && MAX_HITS_PER_TICK == 4,
                "rolling window and distinct per-physical-tick fence stay explicit");
        check(Quota.EMPTY.nativeTick() == Quota.EMPTY && Quota.EMPTY.available(0), "empty clock is stable");
        Quota firstTick = Quota.EMPTY;
        for (int admissions = 0; admissions < MAX_HITS_PER_TICK; admissions++) {
            check(firstTick.available(admissions), "first four positive hits in one physical tick qualify");
            firstTick = firstTick.afterAbsorption(d("1e-256"), admissions);
        }
        Quota four = firstTick;
        check(four.available() && !four.available(4), "rolling availability cannot override the four-hit physical fence");
        rejects(() -> four.afterAbsorption(BigDecimal.ONE, 4), "fifth positive hit within a physical tick refuses");
        check(four.afterAbsorption(BigDecimal.ZERO, 4) == four, "zero absorption does not consume exhausted physical quota");
        check(Quota.restore(four.remainingTicks()).equals(four), "restoration cannot age a hit");

        // Admissions made before Post are held at20 there; next physical tick's independent
        // count still permits four more hits, even though the old entries also remain20.
        int[] preserved = four.nativeTick().remainingTicks();
        for (int i = 0; i < 4; i++) preserved[i] = WINDOW_TICKS;
        Quota held = Quota.restore(preserved);
        check(held.equals(four) && held.available(0), "Post preserves admission ages without imposing a second tick's fence");
        Quota full = held;
        for (int admissions = 0; admissions < MAX_HITS_PER_TICK; admissions++)
            full = full.afterAbsorption(BigDecimal.ONE, admissions);
        Quota eight = full;
        check(!eight.available() && !eight.available(0), "eight rolling absorptions block even a new physical tick");
        rejects(() -> eight.afterAbsorption(BigDecimal.ONE, 0), "ninth rolling hit refuses");
        check(eight.afterAbsorption(BigDecimal.ZERO, 0) == eight, "zero damage cannot consume rolling quota");
        for (int elapsed = 0; elapsed < WINDOW_TICKS; elapsed++) {
            Quota snapshot = tick(eight, elapsed);
            check(!snapshot.available(), "all native window ages0..19 stay occupied");
            check(snapshot.eighthRemaining() == WINDOW_TICKS - elapsed, "every actual tick ages each occupied slot once");
        }
        check(tick(eight, WINDOW_TICKS).equals(Quota.EMPTY), "twentieth actual tick releases all same-age hits");

        Quota staggered = Quota.EMPTY;
        for (int i = 0; i < MAX_ABSORPTIONS; i++) {
            if (i > 0) staggered = staggered.nativeTick();
            staggered = staggered.afterAbsorption(BigDecimal.ONE, 0);
        }
        check(Arrays.equals(staggered.remainingTicks(), new int[] {20, 19, 18, 17, 16, 15, 14, 13}),
                "each staggered hit owns an independent rolling age");
        check(!tick(staggered, 12).available(), "oldest staggered hit remains occupied through age19");
        Quota expiry = tick(staggered, 13);
        check(Arrays.equals(expiry.remainingTicks(), new int[] {7, 6, 5, 4, 3, 2, 1, 0}),
                "only oldest staggered absorption expires at its twentieth tick");
        check(Arrays.equals(expiry.afterAbsorption(BigDecimal.ONE, 0).remainingTicks(), new int[] {20, 7, 6, 5, 4, 3, 2, 1}),
                "replacement cannot refresh old clocks");

        var zeroDamage = absorb(state("100", "100", "100", "100", false), shares("50", "50"), BigDecimal.ZERO);
        var zeroS = absorb(state("100", "100", "0", "100", false), SharedShieldReserveShares.ZERO, BigDecimal.ONE);
        var partial = absorb(state("100", "100", "0", "100", false), shares("0", "1e-256"), BigDecimal.ONE);
        check(Quota.EMPTY.afterAbsorption(zeroDamage.absorbed(), 0) == Quota.EMPTY, "native full block and zero damage consume no quota");
        check(Quota.EMPTY.afterAbsorption(zeroS.absorbed(), 0) == Quota.EMPTY, "zero S consumes no quota");
        check(Quota.EMPTY.afterAbsorption(partial.absorbed(), 0).firstRemaining() == WINDOW_TICKS,
                "smallest supported positive upper-only absorption consumes one slot");
        for (BigDecimal invalid : new BigDecimal[] {null, d("-.1"), d("1e-257"), d("100000000000000000001")})
            rejects(() -> Quota.EMPTY.afterAbsorption(invalid, 0), "invalid absorption cannot mint or consume quota");
        for (int invalid : new int[] {-1, 5, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            rejects(() -> Quota.EMPTY.available(invalid), "corrupt physical admission count refuses");
            rejects(() -> Quota.EMPTY.afterAbsorption(BigDecimal.ZERO, invalid), "zero damage cannot conceal corrupt physical fence");
        }
    }

    private static void quotaPersistence() {
        int[] saved = {20, 19, 16, 13, 10, 6, 2, 1};
        Quota restored = Quota.restore(saved);
        saved[0] = 0;
        check(restored.firstRemaining() == 20, "mutable restoration input cannot change quota");
        int[] output = restored.remainingTicks();
        output[7] = 0;
        check(restored.eighthRemaining() == 1 && !restored.available(), "serialization snapshot cannot change quota");
        for (int i = 0; i < 100; i++)
            check(Quota.restore(restored.remainingTicks()).equals(restored), "hold toggles and repeated restore cannot refresh quota");
        rejects(() -> Quota.restore(null), "missing saved quota refuses");
        for (int length : new int[] {0, 1, 4, 7, 9, 20})
            rejects(() -> Quota.restore(new int[length]), "only exactly eight saved clocks are valid");
        for (int position = 0; position < MAX_ABSORPTIONS; position++) {
            for (int invalid : new int[] {-1, 21, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                int[] corrupt = restored.remainingTicks();
                corrupt[position] = invalid;
                rejects(() -> Quota.restore(corrupt), "invalid clock refuses regardless of slot");
            }
            if (position > 0) {
                int[] corrupt = new int[MAX_ABSORPTIONS];
                corrupt[position] = 1;
                rejects(() -> Quota.restore(corrupt), "out-of-order positive slot refuses");
            }
        }
        // Every native age, every active-slot count and every physical admission count.
        for (int age = 0; age <= WINDOW_TICKS; age++) for (int count = 0; count <= MAX_ABSORPTIONS; count++) {
            int[] values = new int[MAX_ABSORPTIONS];
            Arrays.fill(values, 0, count, age);
            Quota state = Quota.restore(values);
            check(Quota.restore(state.remainingTicks()).equals(state), "all uniform canonical ages round-trip exactly");
            for (int admitted = 0; admitted <= MAX_HITS_PER_TICK; admitted++)
                check(state.available(admitted) == ((age == 0 || count < MAX_ABSORPTIONS) && admitted < MAX_HITS_PER_TICK),
                        "all native edges preserve both independent admission constraints");
            int[] aged = values.clone();
            for (int i = 0; i < aged.length; i++) aged[i] = Math.max(0, aged[i] - 1);
            check(Arrays.equals(state.nativeTick().remainingTicks(), aged), "every persisted clock ages by exactly one");
        }
    }

    private static void quotaReferenceModel() {
        Random random = new Random(20_452);
        ArrayDeque<Integer> positiveTicks = new ArrayDeque<>();
        Quota quota = Quota.EMPTY;
        int nativeTime = 0, admissions = 0;
        for (int operation = 0; operation < 30_000; operation++) {
            if (random.nextInt(5) == 0) {
                nativeTime++;
                admissions = 0;
                quota = quota.nativeTick();
                while (!positiveTicks.isEmpty() && nativeTime - positiveTicks.peekFirst() >= WINDOW_TICKS)
                    positiveTicks.removeFirst();
            } else if (random.nextInt(4) == 0) {
                check(quota.afterAbsorption(BigDecimal.ZERO, admissions) == quota, "random zero hit preserves all clocks");
            } else if (positiveTicks.size() < MAX_ABSORPTIONS && admissions < MAX_HITS_PER_TICK) {
                quota = quota.afterAbsorption(d(".125"), admissions++);
                positiveTicks.addLast(nativeTime);
            } else {
                Quota captured = quota; int currentAdmissions = admissions;
                rejects(() -> captured.afterAbsorption(BigDecimal.ONE, currentAdmissions), "random over-limit admission refuses");
            }
            check(quota.available() == (positiveTicks.size() < MAX_ABSORPTIONS), "rolling availability matches independent event history");
            check(quota.available(admissions) == (positiveTicks.size() < MAX_ABSORPTIONS && admissions < MAX_HITS_PER_TICK),
                    "joint admission matches independent per-tick count and event history");
            int[] expected = new int[MAX_ABSORPTIONS];
            int index = positiveTicks.size() - 1;
            for (int eventTick : positiveTicks) expected[index--] = WINDOW_TICKS - (nativeTime - eventTick);
            check(Arrays.equals(quota.remainingTicks(), expected), "all eight ages match independent rolling timestamps");
            if (operation % 7 == 0) quota = Quota.restore(quota.remainingTicks());
        }
    }

    private static Quota tick(Quota quota, int count) {
        for (int i = 0; i < count; i++) quota = quota.nativeTick();
        return quota;
    }

    private static void economics() {
        check(PAYMENT_PERIOD == 20 && RESTART_FENCE == 4, "native payment and restart limits are explicit");
        String[] activation = {"2", "1", ".4", ".1", ".02"};
        String[] sustain = {".25", ".125", ".05", ".0125", ".0025"};
        int[] cooldown = {10, 5, 4, 4, 4};
        for (int i = 0; i < 5; i++) {
            BigDecimal q = BigDecimal.valueOf(i + 1);
            amount(theoreticalActivationCost(q), activation[i], "activation anchor reuses VectorParry");
            amount(theoreticalSustainCost(q), sustain[i], "sustain anchor reuses VectorParry");
            check(cooldownTicks(q) == cooldown[i], "cooldown honors four-tick restart fence");
        }
        for (int i = 100; i <= 500; i++) {
            BigDecimal q = BigDecimal.valueOf(i, 2);
            equal(theoreticalActivationCost(q), VectorParryRules.theoreticalActivationCost(q), "fractional activation curve is shared");
            equal(theoreticalSustainCost(q), VectorParryRules.theoreticalSustainCost(q), "fractional sustain curve is shared");
            equal(theoreticalCooldown(q), VectorParryRules.theoreticalCooldown(q), "fractional cooldown curve is shared");
            check(activationCost(q) == VectorParryRules.activationCost(q) && sustainCost(q) == VectorParryRules.sustainCost(q), "native CP conversion matches existing skill");
            check(cooldownTicks(q) >= RESTART_FENCE, "fractional growth cannot bypass restart fence");
        }
        rejects(() -> activationCost(d("1e257")), "invalid activation coordinate refuses");
        rejects(() -> sustainCost(d("1e-257")), "invalid sustain coordinate refuses");
        rejects(() -> cooldownTicks(null), "absent cooldown coordinate refuses");
    }

    private static ExactCombatState state(String hp, String maxHp, String base, String maxBase, boolean enabled) {
        return new ExactCombatState(d(hp), d(maxHp), d(base), d(maxBase), enabled);
    }
    private static SharedShieldReserveShares shares(String lower, String upper) { return new SharedShieldReserveShares(d(lower), d(upper)); }
    private static VectorParryRules.Point p(double x, double y, double z) { return new VectorParryRules.Point(x, y, z); }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
    private static void amount(BigDecimal actual, String expected, String reason) { equal(actual, d(expected), reason); }
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

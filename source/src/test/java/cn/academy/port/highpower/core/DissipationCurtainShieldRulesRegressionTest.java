package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.Random;

/** Exact absorption, asymmetric resource access and smallest supported decimal-quantum tests. */
public final class DissipationCurtainShieldRulesRegressionTest {
    private static int checks;
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static ExactCombatState base(String shield) {
        return new ExactCombatState(n("80"), n("100"), n(shield), n("100"), true);
    }
    private static void check(boolean result, String why) { checks++; if (!result) throw new AssertionError(why); }
    private static void eq(BigDecimal actual, BigDecimal expected, String why) {
        check(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected);
    }
    private static void reject(Runnable action, String why) {
        try { action.run(); throw new AssertionError(why); } catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) {
        var fullShares = new SharedShieldReserveShares(n("50"), n("50"));
        eq(DissipationCurtainShieldRules.capacity(base("100")), n("150"), "242 nominal capacity is exactly1.5S");
        eq(DissipationCurtainShieldRules.available(base("100"), fullShares), n("150"), "242 never exposes upper");
        var arrow = DissipationCurtainShieldRules.absorb(base("30"), fullShares, n("90"), true);
        eq(arrow.absorbed(), n("90"), "arrow absorbs incoming within125percent reserve");
        eq(arrow.spent(), n("72"), "arrow spends80percent absorbed damage");
        eq(arrow.state().shield(), BigDecimal.ZERO, "arrow spends base first");
        eq(arrow.shares().lower(), n("8"), "arrow then spends lower");
        eq(arrow.shares().upper(), n("50"), "arrow never spends upper");
        eq(arrow.state().health(), n("80"), "interception never applies HP damage");
        var partial = DissipationCurtainShieldRules.absorb(base("30"), fullShares, n("120"), true);
        eq(partial.absorbed(), n("100"), "partial arrow is limited by125percent eligible balance");
        eq(partial.spent(), n("80"), "partial arrow exhausts eligible balance exactly");
        eq(partial.remaining(), n("20"), "partial residual retains exact damage");
        eq(partial.state().health(), n("80"), "caller owns residual health processing");
        eq(partial.shares().upper(), n("50"), "partial leaves upper untouched");
        var beam = DissipationCurtainShieldRules.absorb(base("30"), fullShares, n("120"), false);
        eq(beam.absorbed(), n("80"), "beam absorbs exactly1:1");
        eq(beam.spent(), n("80"), "beam cost equals absorption");
        eq(beam.remaining(), n("40"), "beam residual conserved");
        var capturedArrow = DissipationCurtainShieldRules.absorb(base("30"), fullShares, n("120"), true, n("10"));
        eq(capturedArrow.absorbed(), n("12.5"), "immutable ceiling limits arrow absorption after recovery");
        eq(capturedArrow.spent(), n("10"), "immutable ceiling limits exact spend");
        eq(capturedArrow.state().shield(), n("20"), "ceiling does not fabricate a smaller ledger");
        eq(capturedArrow.shares().lower(), n("50"), "ceiling preserves unspent lower");
        var capturedBeam = DissipationCurtainShieldRules.absorb(base("30"), fullShares, n("120"), false, n("10"));
        eq(capturedBeam.absorbed(), n("10"), "immutable beam ceiling stays1:1");
        var exhaustedSinceCapture = DissipationCurtainShieldRules.absorb(base("1"), SharedShieldReserveShares.ZERO, n("120"), true, n("100"));
        eq(exhaustedSinceCapture.spent(), n("1"), "older larger ceiling cannot recreate spent funds");

        var upperOnly = new SharedShieldReserveShares(BigDecimal.ZERO, n("50"));
        for (boolean isArrow : new boolean[]{false, true}) {
            var noMint = DissipationCurtainShieldRules.absorb(base("0"), upperOnly, n("20"), isArrow);
            eq(noMint.absorbed(), BigDecimal.ZERO, "upper-only cannot mint curtain absorption");
            check(noMint.shares().equals(upperOnly), "upper-only identities unchanged");
            eq(noMint.remaining(), n("20"), "upper-only passes all damage");
            var zero = DissipationCurtainShieldRules.absorb(base("30"), fullShares, BigDecimal.ZERO, isArrow);
            eq(zero.spent(), BigDecimal.ZERO, "zero damage never spends");
            eq(zero.state().shield(), n("30"), "zero damage preserves base");
            var dead = new ExactCombatState(BigDecimal.ZERO, n("100"), n("30"), n("100"), true);
            var deadResult = DissipationCurtainShieldRules.absorb(dead, fullShares, n("20"), isArrow);
            eq(deadResult.absorbed(), BigDecimal.ZERO, "death cannot absorb");
            eq(deadResult.spent(), BigDecimal.ZERO, "death cannot spend");
            check(deadResult.shares().equals(fullShares), "death cannot alter reserves");
        }

        BigDecimal quantum = BigDecimal.ONE.scaleByPowerOfTen(-CombatAmounts.MAX_ABSOLUTE_SCALE);
        var quantumBase = base(quantum.toPlainString());
        var tiny = DissipationCurtainShieldRules.absorb(quantumBase, upperOnly, quantum, true);
        eq(tiny.absorbed(), quantum, "smallest incoming quantum remains absorbable");
        eq(tiny.spent(), quantum, "ceil spend prevents a free fractional quantum");
        eq(tiny.state().shield(), BigDecimal.ZERO, "smallest balance fully charged");
        var tinyPartial = DissipationCurtainShieldRules.absorb(quantumBase, upperOnly, quantum.multiply(n("2")), true);
        eq(tinyPartial.absorbed(), quantum, "floor absorption cannot create a quarter quantum");
        eq(tinyPartial.spent(), quantum, "smallest partial absorption paid once");
        eq(tinyPartial.remaining(), quantum, "smallest residual is not lost");
        var fourQuanta = DissipationCurtainShieldRules.absorb(base(quantum.multiply(n("4")).toPlainString()), upperOnly,
                quantum.multiply(n("5")), true);
        eq(fourQuanta.absorbed(), quantum.multiply(n("5")), "exact five-for-four boundary retained");
        eq(fourQuanta.spent(), quantum.multiply(n("4")), "exact representable80percent cost retained");
        reject(() -> DissipationCurtainShieldRules.absorb(base("1"), fullShares, quantum.movePointLeft(1), true), "unsupported incoming scale fails closed");

        var capBase = new ExactCombatState(n("80"), n("100"), n("50000000000000000000"), n("50000000000000000000"), false);
        var capShares = new SharedShieldReserveShares(n("25000000000000000000"), n("25000000000000000000"));
        var capped = DissipationCurtainShieldRules.absorb(capBase, capShares, CombatAmounts.CAP, true);
        eq(capped.absorbed(), n("93750000000000000000"), "arrow absorption stays within global damage cap");
        eq(capped.spent(), n("75000000000000000000"), "huge exact spend uses only base plus lower");
        eq(capped.shares().upper(), capShares.upper(), "huge residual cannot borrow upper");

        Random random = new Random(242);
        for (int i = 0; i < 12000; i++) {
            var before = base(BigDecimal.valueOf(random.nextInt(100001), 3).toPlainString());
            var shares = new SharedShieldReserveShares(BigDecimal.valueOf(random.nextInt(50001), 3), BigDecimal.valueOf(random.nextInt(50001), 3));
            BigDecimal incoming = BigDecimal.valueOf(random.nextInt(300001), 3);
            boolean isArrow = random.nextBoolean();
            var result = DissipationCurtainShieldRules.absorb(before, shares, incoming, isArrow);
            BigDecimal eligible = before.shield().add(shares.lower());
            BigDecimal expectedAbsorbed = incoming.min(eligible.multiply(isArrow ? n("1.25") : BigDecimal.ONE));
            eq(result.absorbed(), expectedAbsorbed, "random exact absorption");
            eq(result.spent(), expectedAbsorbed.multiply(isArrow ? n("0.8") : BigDecimal.ONE), "random exact cost");
            eq(result.state().shield().add(result.shares().lower()), eligible.subtract(result.spent()), "random eligible conservation");
            eq(result.shares().upper(), shares.upper(), "random upper immutable");
            eq(result.absorbed().add(result.remaining()), incoming, "random residual conservation");
            eq(result.state().health(), before.health(), "random health untouched");
        }
        System.out.println("PASS " + checks + " dissipation curtain exact resource/rounding checks");
    }
}

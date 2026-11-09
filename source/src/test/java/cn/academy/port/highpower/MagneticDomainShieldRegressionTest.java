package cn.academy.port.highpower;

import cn.academy.port.highpower.core.*;
import java.math.BigDecimal;
import java.util.Random;

/** Exact independent conservation witnesses for two literal NEW152 shield tranches. */
public final class MagneticDomainShieldRegressionTest {
    private static int checks;
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static void eq(BigDecimal a, BigDecimal b, String why) { check(a.compareTo(b) == 0, why + ": " + a + " != " + b); }
    public static void main(String[] args) {
        ExactCombatState base = new ExactCombatState(n("100"), n("200"), n("30"), n("100"), false);
        var hit = MagneticDomainShieldRules.absorb(base, n("40"), n("55"));
        eq(hit.state().shield(), BigDecimal.ZERO, "base pays first"); eq(hit.reserve(), n("15"), "reserve pays only remaining damage");
        eq(hit.state().health(), n("100"), "shield cannot heal existing injury"); eq(hit.absorbed(), n("55"), "literal one-for-one shield");
        var partial = MagneticDomainShieldRules.absorb(base, n("40"), n("85"));
        eq(partial.state().health(), n("85"), "partial exhaustion leaves true HP damage"); eq(partial.reserve(), BigDecimal.ZERO, "no negative reserve");
        var lethal = MagneticDomainShieldRules.absorb(base, n("40"), n("200"));
        check(lethal.state().isDead(), "lethal remainder remains lethal");
        var dead = MagneticDomainShieldRules.absorb(lethal.state(), n("40"), n("1"));
        eq(dead.reserve(), n("40"), "dead ledger cannot spend reserve");
        var recovered = MagneticDomainShieldRules.recover(base, n("40"), n("80"));
        eq(recovered.state().shield(), n("100"), "recovery fills base first"); eq(recovered.reserve(), n("50"), "one budget remainder reaches reserve");
        eq(recovered.state().health(), n("100"), "shield recovery preserves injury");
        var cost = MagneticDomainShieldRules.spend(base, n("40"), n("30.125"));
        eq(cost.state().shield(), BigDecimal.ZERO, "domain fee pays base first");
        eq(cost.reserve(), n("39.875"), "fractional fee draws reserve remainder exactly");
        eq(cost.state().health(), base.health(), "domain fee cannot spend HP");
        check(MagneticDomainShieldRules.spend(base,n("40"),n("70.0001")) == null, "unaffordable fee cannot partially debit");
        // Keep exercising the arithmetic ceiling even though default LV5 pools are much smaller.
        var huge = new ExactCombatState(CombatAmounts.CAP, CombatAmounts.CAP,
                CombatAmounts.CAP.divide(n("2")), CombatAmounts.CAP.divide(n("2")), false);
        eq(huge.maxShield().multiply(n("2")), CombatAmounts.CAP, "literal two S reaches hard total cap");
        BigDecimal tiny = n("0.000000000000000001");
        var precise = MagneticDomainShieldRules.absorb(new ExactCombatState(huge.health(),huge.maxHealth(),BigDecimal.ZERO,huge.maxShield(),false),huge.maxShield(),tiny);
        eq(precise.reserve(), huge.maxShield().subtract(tiny), "sub-unit debit survives 5e19 reserve");
        try { MagneticDomainShieldRules.validate(huge, huge.maxShield().add(BigDecimal.ONE)); throw new AssertionError("overflow accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        Random random = new Random(152);
        for (int i=0;i<12000;i++) {
            BigDecimal s=BigDecimal.valueOf(random.nextInt(1000000),3), r=BigDecimal.valueOf(random.nextInt(1000000),3);
            BigDecimal hp=BigDecimal.valueOf(1+random.nextInt(1000000),3), damage=BigDecimal.valueOf(random.nextInt(3000000),3);
            ExactCombatState before=new ExactCombatState(hp,n("1000"),s,n("1000"),random.nextBoolean());
            var result=MagneticDomainShieldRules.absorb(before,r,damage);
            BigDecimal total=s.add(r), absorbed=damage.min(total);
            eq(result.absorbed(),absorbed,"exact absorption");
            eq(result.state().shield().add(result.reserve()),total.subtract(absorbed),"two-tranche conservation");
            eq(result.state().health(),hp.subtract(damage.subtract(absorbed).min(hp)),"HP conservation");
            eq(result.state().maxShield(),before.maxShield(),"base S never changes");
            BigDecimal budget=BigDecimal.valueOf(random.nextInt(3000000),3);
            var recovery=MagneticDomainShieldRules.recover(before,r,budget);
            eq(recovery.state().shield().add(recovery.reserve()),total.add(budget).min(n("2000")),"one recovery budget");
            eq(recovery.state().health(),hp,"recovery cannot heal HP");
            var ordinary=before.spendShield(s);
            eq(ordinary.shield(),BigDecimal.ZERO,"ordinary spending sees base only");
            check(MagneticDomainShieldRules.spend(before,r,total.add(n("0.001"))) == null,"insufficient aggregate fee refused");
        }
        System.out.println("PASS " + checks + " NEW152 literal two-tranche shield and one-budget recovery checks");
    }
}

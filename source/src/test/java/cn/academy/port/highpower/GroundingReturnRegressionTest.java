package cn.academy.port.highpower;

import cn.academy.port.highpower.core.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Independent transaction arithmetic and production guard witnesses for NEW-122. */
public final class GroundingReturnRegressionTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static void equal(BigDecimal a, BigDecimal b, String message) { check(a.compareTo(b) == 0, message + ": " + a + " != " + b); }
    public static void main(String[] args) throws Exception {
        check(GroundingReturnRules.active(10,10) && GroundingReturnRules.active(10,69) && !GroundingReturnRules.active(10,70)
                && !GroundingReturnRules.active(10,9), "exact sixty-tick expiry and clock regression");
        check(GroundingReturnRules.precise(10,10) && GroundingReturnRules.precise(10,15) && !GroundingReturnRules.precise(10,16)
                && !GroundingReturnRules.precise(10,9), "precision has exactly six admitted server ticks");
        var before = new ExactCombatState(n("200"), n("200"), n("40"), n("100"), false);
        var small = GroundingReturnRules.absorb(before, n("3"), n("100"), BigDecimal.ZERO, false, true);
        equal(small.absorbed(), n("3"), "real positive absorption"); equal(small.refund(), n("3"), "tiny hit refunds only actual spend");
        equal(small.state().shield(), n("40"), "tiny hit cannot refill prior missing shield");
        equal(small.state().health(), n("200"), "full absorption preserves HP"); check(small.precisionUsed(), "precision consumed exactly once");
        var second = GroundingReturnRules.absorb(small.state(), n("5"), n("100"), small.spent(), small.precisionUsed(), true);
        equal(second.refund(), BigDecimal.ZERO, "second hit in same precision window never refunds again");
        equal(second.state().shield(), n("35"), "second positive hit spends shared pool");
        var large = GroundingReturnRules.absorb(before, n("75"), n("100"), BigDecimal.ZERO, false, true);
        equal(large.absorbed(), n("40"), "absorption saturates at actual pool"); equal(large.refund(), n("20"), "refund capped by captured maximum");
        equal(large.state().health(), n("165"), "refund cannot absorb same-hit overflow"); equal(large.state().shield(), n("20"), "refund remains shield only");
        check(Math.abs(large.knockbackFactor() - (1 - .5*40/75.0)) < 1e-12, "bounded proportional knockback");
        var late = GroundingReturnRules.absorb(before,n("75"),n("100"),BigDecimal.ZERO,false,false);
        equal(late.refund(),BigDecimal.ZERO,"outside precision has no refund");equal(late.state().shield(),BigDecimal.ZERO,"late depletion");
        var zero = GroundingReturnRules.absorb(before, BigDecimal.ZERO, n("100"), BigDecimal.ZERO, false, true);
        check(!zero.precisionUsed() && zero.knockbackFactor()==1,"zero-damage veto cannot create successful grounding");
        var empty = new ExactCombatState(n("2"),n("200"),BigDecimal.ZERO,n("100"),false);
        var emptyHit = GroundingReturnRules.absorb(empty,n("3"),n("100"),BigDecimal.ZERO,false,true);
        check(emptyHit.state().isDead()&&!emptyHit.precisionUsed()&&emptyHit.knockbackFactor()==1,"empty defense does not heal or protect");
        var lethal = GroundingReturnRules.absorb(new ExactCombatState(n("2"),n("200"),n("4"),n("100"),false),n("7"),n("100"),BigDecimal.ZERO,false,true);
        check(lethal.state().isDead(),"lethal remainder stays lethal"); equal(lethal.state().shield(),n("4"),"precision refund is independent shield accounting, never HP rescue");
        Random random = new Random(122);
        for (int i=0;i<12000;i++) {
            BigDecimal cap=BigDecimal.valueOf(1+random.nextInt(1000000)), shield=cap.multiply(BigDecimal.valueOf(random.nextDouble()));
            BigDecimal hp=BigDecimal.valueOf(1+random.nextInt(1000000)), damage=BigDecimal.valueOf(random.nextInt(2000000));
            var initial=new ExactCombatState(hp,hp,shield,cap,false);
            var result=GroundingReturnRules.absorb(initial,damage,cap,BigDecimal.ZERO,false,random.nextBoolean());
            check(result.state().shield().signum()>=0&&result.state().shield().compareTo(shield)<=0,"grounding never refills above starting pool");
            check(result.refund().compareTo(result.absorbed())<=0&&result.refund().compareTo(cap.multiply(n(".2")))<=0,"both refund caps");
            equal(result.state().health(),hp.subtract(damage.subtract(result.absorbed()).min(hp)),"conserved HP remainder");
            check(result.knockbackFactor()>=.5&&result.knockbackFactor()<=1&&!result.state().shieldEnabled(),"finite bounds and no global forcefield");
        }
        var huge=ExactCombatState.full(HighPowerLevel.LV5).withShieldEnabled(false);
        var tiny=GroundingReturnRules.absorb(huge,n("0.000000000000000001"),huge.maxShield(),BigDecimal.ZERO,false,false);
        equal(tiny.state().shield(),huge.shield().subtract(n("0.000000000000000001")),"tiny exact spend survives huge capacity");
        String backend=Files.readString(Path.of("src/main/java/cn/academy/port/highpower/GroundingReturn.java"));
        for(String bad:new String[]{".hurt(","setDeltaMovement(","fallDistance", "setHealth(","setBlock(","getChunk(","teleportTo("}) check(!backend.contains(bad),"no controller bypass "+bad);
        String bridge=Files.readString(Path.of("src/main/java/cn/academy/port/highpower/HighPowerCombat.java"));
        check(bridge.contains("DamageTypes.LIGHTNING_BOLT")&&bridge.contains("DamageTypeTags.IS_LIGHTNING"),"electrical environmental type is explicit");
        check(bridge.contains("legacy.skill.equals(\"electromaster.arc_gen\")")&&!bridge.contains("source.getMsgId()"),"ACA identity uses exact literal final source classes");
        check(bridge.contains("groundingKnockbackUsed")&&bridge.contains("frame.groundingKnockbackFactor"),"same-frame one-use knockback receipt");
        System.out.println("PASS "+checks+" NEW-122 exact shield, precision, proportional knockback and guard checks");
    }
}

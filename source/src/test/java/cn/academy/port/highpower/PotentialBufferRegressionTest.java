package cn.academy.port.highpower;

import cn.academy.port.highpower.core.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Independent NEW142 transaction, geometry, growth-cost and fail-closed production-bound witnesses. */
public final class PotentialBufferRegressionTest {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static void eq(BigDecimal a, BigDecimal b, String why) { check(a.compareTo(b) == 0, why + ": " + a + " != " + b); }
    private static PotentialBufferRules.Point p(double x, double y, double z) { return new PotentialBufferRules.Point(x, y, z); }
    public static void main(String[] args) throws Exception {
        var before = new ExactCombatState(n("100"), n("200"), n("40"), n("100"), false);
        var full = PotentialBufferRules.absorb(before, n("12"));
        eq(full.absorbed(), n("12"), "fully shielded hit spends only true mitigated damage");
        eq(full.state().shield(), n("28"), "shared pool decremented exactly once"); eq(full.state().health(), n("100"), "no HP healing");
        var partial = PotentialBufferRules.absorb(before, n("55"));
        eq(partial.absorbed(), n("40"), "partial absorption saturates at available shield");
        eq(partial.state().health(), n("85"), "remaining damage goes to HP"); eq(partial.state().shield(), BigDecimal.ZERO, "no refill");
        var exhausted = PotentialBufferRules.absorb(partial.state(), n("5"));
        eq(exhausted.absorbed(), BigDecimal.ZERO, "exhaustion cannot generate positive receipt"); eq(exhausted.state().health(), n("80"), "empty shield leaves normal HP damage");
        var zero = PotentialBufferRules.absorb(before, BigDecimal.ZERO);
        check(zero.state().equals(before) && zero.absorbed().signum() == 0, "zero veto never creates absorption");
        var lethal = PotentialBufferRules.absorb(before, n("200"));
        check(lethal.state().isDead() && lethal.state().shield().signum() == 0, "lethal remainder is not rescued by shield controller");
        check(PotentialBufferRules.absorb(lethal.state(), n("5")).absorbed().signum() == 0, "dead ledger cannot create receipt");
        Random random = new Random(142);
        for (int i = 0; i < 12000; i++) {
            BigDecimal hp = BigDecimal.valueOf(1 + random.nextInt(1000000)), shield = BigDecimal.valueOf(random.nextInt(1000000));
            BigDecimal damage = BigDecimal.valueOf(random.nextInt(2000000));
            var state = new ExactCombatState(hp, hp, shield, n("1000000"), random.nextBoolean());
            var result = PotentialBufferRules.absorb(state, damage);
            eq(result.absorbed(), damage.min(shield), "one capped positive spend");
            eq(result.state().shield(), shield.subtract(result.absorbed()), "exact shared-pool conservation");
            eq(result.state().health(), hp.subtract(damage.subtract(result.absorbed()).min(hp)), "HP conservation");
            check(result.state().maxHealth().equals(state.maxHealth()) && result.state().maxShield().equals(state.maxShield())
                    && !result.state().shieldEnabled(), "no capacity change or generic shield toggle");
        }
        var huge = ExactCombatState.full(HighPowerLevel.LV5);
        var tiny = PotentialBufferRules.absorb(huge, n("0.000000000000000001"));
        eq(tiny.state().shield(), huge.shield().subtract(n("0.000000000000000001")), "tiny spend survives enormous shield scale");
        check(PotentialBufferRules.front(p(0,0,0),p(0,0,1),p(0,999,1)), "vertical origin offset does not alter horizontal sector");
        check(PotentialBufferRules.front(p(0,0,0),p(0,0,1),p(Math.sin(Math.PI/3),0,Math.cos(Math.PI/3))), "inclusive sixty-degree half-angle");
        check(!PotentialBufferRules.front(p(0,0,0),p(0,0,1),p(Math.sin(Math.PI/3+.0001),0,Math.cos(Math.PI/3+.0001))), "just outside half-angle refused");
        check(!PotentialBufferRules.front(p(0,0,0),p(0,0,1),p(1,0,0)) && !PotentialBufferRules.front(p(0,0,0),p(0,0,1),p(0,0,-1)), "side and rear refused");
        check(!PotentialBufferRules.front(p(0,0,0),p(0,1,0),p(0,0,1)), "vertical look has no forged horizontal front");
        check(!PotentialBufferRules.front(p(0,0,0),p(0,0,1),null) && !PotentialBufferRules.front(p(0,0,0),p(0,0,1),p(0,3,0)), "originless and zero horizontal offsets refused");
        check(!PotentialBufferRules.front(p(0,0,0),p(Double.NaN,0,1),p(0,0,1)) && !PotentialBufferRules.front(p(0,0,0),p(0,0,1),p(0,Double.POSITIVE_INFINITY,1)), "nonfinite geometry refused");
        check(PotentialBufferRules.front(p(0,0,0),p(0,0,1),p(0,0,Double.MAX_VALUE)), "finite distant source normalizes without squaring overflow");
        check(PotentialBufferRules.inside(p(PotentialBufferRules.RANGE,0,0),p(0,0,0)) && !PotentialBufferRules.inside(p(Math.nextUp(PotentialBufferRules.RANGE),0,0),p(0,0,0)), "exact configured pulse radius");
        check(PotentialBufferRules.ready(120,120) && !PotentialBufferRules.ready(120,119) && !PotentialBufferRules.ready(-1,120)
                && !PotentialBufferRules.clock(Long.MAX_VALUE-19), "persistent exclusive until and overflow clock guards");
        check(PotentialBufferRules.MAX_TARGETS == 6 && PotentialBufferRules.SCAN_LIMIT == 64 && PotentialBufferRules.PULSE_INTERVAL == 20, "bounded target, scan and owner rates");
        check(PotentialBufferRules.pulseCost(n("4")) == 3 && PotentialBufferRules.pulseCost(n("5")) == .1, "LV4 toLV5 exact pulse anchors");
        double previous = 3;
        for (int i=1;i<=200;i++) { double cost = PotentialBufferRules.pulseCost(n("4").add(BigDecimal.valueOf(i).divide(n("200"))));
            check(cost < previous && cost >= .1, "actual progress produces continuous decreasing pulse cost"); previous=cost; }
        String backend = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/PotentialBuffer.java"));
        for (String forbidden : new String[]{".hurt(","setDeltaMovement(","setHealth(","setBlock(","teleportTo(","fallDistance", "spendShield(", "initializeBudget(", "regenerateShield("})
            check(!backend.contains(forbidden), "no controller bypass/refill: " + forbidden);
        check(backend.contains("target.knockback(PotentialBufferRules.KNOCKBACK") && backend.contains("LivingKnockBackEvent") && backend.contains("Attributes.KNOCKBACK_RESISTANCE"), "native cancellable resistance-aware knockback path");
        check(backend.contains("HighPowerCombat.hostileTarget(e)") && backend.contains("HighPowerCombat.supported(e)") && backend.contains("p.isAlliedTo(e)") && backend.contains("e.isAlliedTo(p)"), "hostile mobs from any mod (and training dummies) only; allies and players excluded");
        check(backend.contains("PULSE_UNTIL") && backend.contains("MASTERY_UNTIL") && backend.contains("PlayerEvent.Clone") && backend.contains("stamp(target, PULSE_UNTIL"), "owner, target and mastery durable rate fences");
        check(backend.contains("getEntities(EntityTypeTest") && backend.contains("PotentialBufferRules.SCAN_LIMIT") && !backend.contains("getEntitiesOfClass"), "native limited scan rather than post-hoc unbounded truncation");
        check(backend.contains("matches(ServerPlayer p, Capture capture)") && backend.contains("capture.committed = true") && backend.contains("capture.ownerPosition"), "opaque consumed context receipt and positional replacement fencing");
        check(backend.contains("hasChunk(x, z)") && backend.contains("mayInteract(p, pos)") && backend.contains("ClipContext.Block.COLLIDER"), "loaded native permissions and no through-wall pulse");
        System.out.println("PASS " + checks + " NEW142 exact shield, source cone, continuous pulse cost and controller guard checks");
    }
}

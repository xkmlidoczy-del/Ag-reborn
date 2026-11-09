package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** Fixed invitation boundary, bounded inert schema and continuous frozen-quote economics. */
public final class TacticalCoordinateRulesRegressionTest {
    private static int checks;
    private static void check(boolean result, String reason) { checks++; if (!result) throw new AssertionError(reason); }
    public static void main(String[] args) {
        double[] costs = {8, 4, 2, .5, .05};
        int[] cooldowns = {60, 30, 16, 10, 1};
        for (int i = 0; i < 5; i++) {
            BigDecimal q = BigDecimal.valueOf(i + 1);
            check(TacticalCoordinateRules.cpCost(q) == costs[i], "exact CP anchor");
            check(TacticalCoordinateRules.cooldownTicks(q) == cooldowns[i], "exact cooldown anchor");
        }
        double previousCost = 8;
        BigDecimal previousCooldown = BigDecimal.valueOf(60);
        for (int hundredth = 101; hundredth <= 500; hundredth++) {
            BigDecimal q = BigDecimal.valueOf(hundredth, 2);
            double cost = TacticalCoordinateRules.cpCost(q);
            BigDecimal cooldown = TacticalCoordinateRules.theoreticalCooldown(q);
            int ticks = TacticalCoordinateRules.cooldownTicks(q);
            check(cost < previousCost && cooldown.compareTo(previousCooldown) < 0, "continuous decreasing economics");
            check(BigDecimal.valueOf(ticks).compareTo(cooldown) >= 0
                    && BigDecimal.valueOf(ticks - 1).compareTo(cooldown) < 0, "native cooldown ceiling exactly once");
            previousCost = cost; previousCooldown = cooldown;
        }
        check(TacticalCoordinateRules.MAX_TEMPLATES == 6 && TacticalCoordinateRules.RANGE == 64, "fixed catalog and range");
        check(TacticalCoordinateRules.fresh(100, 100) && TacticalCoordinateRules.fresh(100, 99 + TacticalCoordinateRules.INVITATION_TICKS), "invitation live until the configured expiry");
        check(!TacticalCoordinateRules.fresh(100, 100 + TacticalCoordinateRules.INVITATION_TICKS) && !TacticalCoordinateRules.fresh(100, 99), "expired/future invitation refuses");
        check(!TacticalCoordinateRules.fresh(-1, 1) && !TacticalCoordinateRules.fresh(Long.MAX_VALUE - TacticalCoordinateRules.INVITATION_TICKS, Long.MAX_VALUE), "invalid clock refuses");
        check(TacticalCoordinateRules.ready(-1, 1, 8) && TacticalCoordinateRules.ready(1, 9, 8)
                && !TacticalCoordinateRules.ready(1, 8, 8), "eight physical ticks required");
        check(!TacticalCoordinateRules.validCount(-1) && TacticalCoordinateRules.validCount(6)
                && !TacticalCoordinateRules.validCount(7), "bounded catalog size");
        check(!TacticalCoordinateRules.validRevision(0) && TacticalCoordinateRules.validRevision(1)
                && !TacticalCoordinateRules.validRevision(Long.MAX_VALUE), "revision cannot wrap");
        check(TacticalCoordinateRules.validName("阵位1") && !TacticalCoordinateRules.validName("x\ny")
                && !TacticalCoordinateRules.validName("x".repeat(33)), "bounded display-only names");
        check(TacticalCoordinateRules.validCoordinates(30_000_016, 4096, -30_000_016)
                && !TacticalCoordinateRules.validCoordinates(30_000_017, 64, 0)
                && !TacticalCoordinateRules.validCoordinates(0, Double.NaN, 0), "finite bounded persisted coordinates");
        check(TacticalCoordinateRules.normalizeYaw(180f) == -180f && TacticalCoordinateRules.normalizeYaw(-181f) == 179f
                && TacticalCoordinateRules.normalizeYaw(720f) == 0, "heading normalized once to[-180,180)");
        check(!TacticalCoordinateRules.validYaw(180) && TacticalCoordinateRules.validYaw(-180)
                && !TacticalCoordinateRules.validPitch(91), "stored orientation bounds");
        boolean refused = false;
        try { TacticalCoordinateRules.normalizeYaw(Float.NaN); } catch (IllegalArgumentException expected) { refused = true; }
        check(refused, "nonfinite heading refuses");
        System.out.println("TacticalCoordinateRulesRegressionTest passed " + checks + " checks");
    }
}

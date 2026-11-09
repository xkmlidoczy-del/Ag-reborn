package cn.academy.port.highpower;

import java.util.List;

/** Pure NEW252 presentation tests: no game bootstrap, networking or mutable combat ledger. */
public final class ZeroGapRecoveryHudRegressionTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void denied(Runnable action, String message) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, message);
    }
    private static HighPowerRecoveryHudModel.Values armor(String base, String capacity, String lower, String half,
                                                          String reserve, String reserveCapacity, boolean active) {
        return new HighPowerRecoveryHudModel.Values("90", "100", base, capacity, "full", "0", 0, 0, 0, active,
                "0", "0", false, "meltdowner", lower, half, false, reserve, reserveCapacity, active);
    }
    public static int runChecks() {
        checks = 0;
        var learned = armor("7", "20", "5", "10", "13", "20", false);
        check(learned.zeroGapVisible() && learned.curtainVisible() && !learned.domainVisible(),
                "learned valid inactive NEW252 exposes both shared reserve halves without an EM domain");
        check(learned.zeroGapTotalShield().equals("20") && learned.zeroGapFullCapacity().equals("40"),
                "total is base 7 plus lower 5 plus upper 8, and full capacity is exactly 2S");
        check(!learned.zeroGapActive(), "stored capacity does not imply active protection");
        var active = armor("7", "20", "5", "10", "13", "20", true);
        check(active.zeroGapActive() && active.shieldActive(), "active NEW252 owns the shared base shield");
        var curtainActive = new HighPowerRecoveryHudModel.Values("90", "100", "7", "20", "full", "0", 0, 0, 0, true,
                "0", "0", false, "meltdowner", "5", "10", true, "13", "20", false);
        check(curtainActive.curtainActive() && curtainActive.zeroGapVisible() && !curtainActive.zeroGapActive(),
                "running NEW242 retains its activity while NEW252's upper reserve is stored");

        var md212 = new HighPowerRecoveryHudModel.Values("90", "100", "7", "20", "full", "0", 0, 0, 0, true,
                "0", "0", false, "meltdowner");
        var md242 = new HighPowerRecoveryHudModel.Values("90", "100", "7", "20", "full", "0", 0, 0, 0, true,
                "0", "0", false, "meltdowner", "5", "10", true);
        var em152 = new HighPowerRecoveryHudModel.Values("90", "100", "7", "20", "full", "0", 0, 0, 0, true,
                "13", "20", true);
        check(!md212.zeroGapVisible() && !md212.curtainVisible() && !md212.domainVisible(), "legacy NEW212 base-only constructor stays base-only");
        check(!md242.zeroGapVisible() && md242.curtainVisible() && !md242.domainVisible(), "legacy NEW242 remains a lower-half view");
        check(!em152.zeroGapVisible() && !em152.curtainVisible() && em152.domainVisible(), "legacy NEW152 remains an EM domain view");
        check(armor("0", "20", "0", "10", "0", "20", false).zeroGapVisible(), "spent reserves retain learned capacity without falsely becoming unlearned");

        String halfCap = "25000000000000000000", baseCap = "50000000000000000000", cap = "100000000000000000000";
        var full = armor(baseCap, baseCap, halfCap, halfCap, baseCap, baseCap, true);
        check(full.zeroGapTotalShield().equals(cap) && full.zeroGapFullCapacity().equals(cap), "2S accepts the exact global cap 1e20");
        var oneShort = armor("49999999999999999999", baseCap, halfCap, halfCap, baseCap, baseCap, true);
        var zh = HighPowerRecoveryHudModel.numberLocale("zh_cn");
        check(HighPowerRecoveryHudModel.pool(oneShort.zeroGapTotalShield(), oneShort.zeroGapFullCapacity(), zh).text().equals("1垓−1 / 1垓"),
                "aggregate one-point deficit remains exact in authoritative Chinese units");
        check(HighPowerRecoveryHudModel.barPixels(oneShort.zeroGapTotalShield(), cap, 192) == 191,
                "aggregate subpixel deficit leaves a visible gap");
        var fraction = armor("7.00000000000000000001", "20", "5.00000000000000000002", "10", "13.00000000000000000003", "20", false);
        check(fraction.zeroGapTotalShield().equals("20.00000000000000000004"), "fractional base and both reserves add without floating-point conversion");
        var requestedScale = armor("100000000000", "100000000000", "50000000000", "50000000000", "100000000000", "100000000000", false);
        check(HighPowerRecoveryHudModel.pool(requestedScale.zeroGapTotalShield(), requestedScale.zeroGapFullCapacity(), zh).text().equals("2000亿 / 2000亿"),
                "2S keeps the requested 2000亿 scale rather than introducing another Chinese unit");

        denied(() -> armor("0", "50000000000000000001", "0", "25000000000000000000.5", "0", "50000000000000000001", false), "2S above 1e20 is rejected");
        denied(() -> armor("0", "20", "0", "10", "0", "19", false), "partial aggregate reserve capacity cannot impersonate all of S");
        denied(() -> armor("0", "20", "0", "9", "0", "20", false), "NEW252 requires a full consistent lower-half capacity");
        denied(() -> armor("0", "20", "5", "10", "4", "20", false), "aggregate reserve cannot be smaller than its lower half");
        denied(() -> armor("0", "20", "5", "10", "16", "20", false), "derived upper reserve cannot exceed its own half");
        denied(() -> armor("0", "20", "10", "10", "21", "20", false), "reserve total cannot exceed S");
        denied(() -> armor("0", "20", "0", "10", "1", "0", false), "reserve without learned capacity is rejected");
        denied(() -> armor("0", "20", "0", "10", "0", "0", true), "active NEW252 without valid capacity is rejected");
        denied(() -> new HighPowerRecoveryHudModel.Values("90", "100", "7", "20", "full", "0", 0, 0, 0, true,
                "0", "0", false, "electromaster", "0", "0", false, "13", "20", false), "EM cannot advertise MD aggregate reserve");
        denied(() -> new HighPowerRecoveryHudModel.Values("90", "100", "7", "20", "full", "0", 0, 0, 0, false,
                "0", "0", false, "meltdowner", "5", "10", false, "13", "20", true), "active NEW252 cannot contradict inactive shared shield");
        denied(() -> new HighPowerRecoveryHudModel.Values("90", "100", "7", "20", "full", "0", 0, 0, 0, true,
                "0", "0", false, "meltdowner", "5", "10", true, "13", "20", true), "mutually exclusive defenses cannot both be active");
        for (String malformed : List.of("-1", "NaN", "1e1", "01", "1.0", "1".repeat(513)))
            denied(() -> armor("0", "20", "0", "10", malformed, "20", false), "aggregate uses canonical bounded wire decimals");
        return checks;
    }
    public static void main(String[] args) { System.out.println("PASS " + runChecks() + " NEW252 exact aggregate HUD boundary and format checks"); }
}

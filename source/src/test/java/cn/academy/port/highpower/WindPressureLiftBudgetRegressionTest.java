package cn.academy.port.highpower;

/** Deterministic pure governor checks; this does not claim native travel or persistence coverage. */
public final class WindPressureLiftBudgetRegressionTest {
    private static int checks;
    private static final long MS = 1_000_000L;
    public static void main(String[] args) {
        sameTickAndRolling(); realTimeAndHalfOpenBoundary(); pauseAndReload(); receiptAndMalformed();
        System.out.println("NEW443 dual-clock budget: " + checks + " checks passed");
    }
    private static WindPressureLiftBudget empty() {
        WindPressureLiftBudget b = new WindPressureLiftBudget(new int[0], new long[0], 0, 0);
        b.initialize(0, 0); return b;
    }
    private static void sameTickAndRolling() {
        WindPressureLiftBudget b = empty();
        yes(b.take(0, 1, 0)); yes(b.take(0, 1, 0)); no(b.take(0, 1, 0));
        b.nativePre(0, 40 * MS); no(b.take(0, 1, 40 * MS));
        for (int tick = 1; tick < 19; tick++) { b.nativePre(tick, tick * 50 * MS); yes(b.take(tick, 1, tick * 50 * MS)); }
        b.nativePre(19, 950 * MS); no(b.take(19, 1, 950 * MS));
        eq(b.saved(950 * MS).ticks().length, 20);
        b.nativePre(20, 1000 * MS); yes(b.take(20, 2, 1000 * MS)); yes(b.take(20, 2, 1000 * MS));
        no(b.take(20, 2, 1000 * MS));
    }
    private static void realTimeAndHalfOpenBoundary() {
        WindPressureLiftBudget fast = empty();
        for (int tick = 0; tick < 20; tick++) { fast.nativePre(tick, tick * MS); yes(fast.take(tick, 1, tick * MS)); }
        fast.nativePre(20, 20 * MS); no(fast.take(20, 2, 20 * MS)); // native history expires before real-time debt
        yes(fast.saved(20 * MS).nanos()[0] > 900 * MS);
        WindPressureLiftBudget edge = empty(); yes(edge.take(0, 1, 0));
        for (int tick = 1; tick < 20; tick++) edge.nativePre(tick, tick * 50 * MS);
        eq(edge.saved(1000 * MS - 1).nanos()[0], 1);
        eq(edge.saved(1000 * MS).nanos()[0], 0); // exactly 1000ms leaves (now-1s,now]
        edge.nativePre(20, 1000 * MS); eq(edge.saved(1000 * MS).ticks().length, 0);
    }
    private static void pauseAndReload() {
        WindPressureLiftBudget b = empty(); yes(b.take(0, 1, 0)); b.pause(10 * MS);
        no(b.take(0, 1, 20 * MS));
        b.resume(90 * MS); eq(b.saved(90 * MS).nanos()[0], 990 * MS); // short pause gives zero credit
        b.pause(90 * MS); b.resume(90_000 * MS); eq(b.saved(90_000 * MS).nanos()[0], 990 * MS);
        var saved = b.saved(90_000 * MS);
        WindPressureLiftBudget loaded = new WindPressureLiftBudget(saved.ticks(), saved.nanos(), saved.receipt(), saved.receiptUses());
        loaded.initialize(500, 500_000 * MS);
        eq(loaded.saved(500_000 * MS).ticks()[0], 20); eq(loaded.saved(500_000 * MS).nanos()[0], 990 * MS);
        yes(loaded.take(500, 2, 500_000 * MS)); eq(loaded.saved(500_000 * MS).ticks().length, 2);
        saved.ticks()[0] = 0; saved.nanos()[0] = 0;
        eq(loaded.saved(500_000 * MS).ticks()[0], 20); // no caller-owned persistence array aliases the governor
        loaded.nativePre(501, 501_000 * MS); eq(loaded.saved(501_000 * MS).nanos()[0], 990 * MS); // unsupported long gap is not credit
    }
    private static void receiptAndMalformed() {
        WindPressureLiftBudget b = empty();
        for (int tick = 0; tick < 20; tick++) { b.nativePre(tick, tick * 50 * MS); yes(b.take(tick, 7, tick * 50 * MS)); }
        for (int tick = 20; tick < 40; tick++) b.nativePre(tick, tick * 50 * MS);
        no(b.take(39, 7, 1950 * MS)); yes(b.take(39, 8, 1950 * MS)); no(b.take(39, 6, 1950 * MS));
        bad(new int[]{21}, new long[]{1}, 1, 1); bad(new int[]{1}, new long[]{1_000_000_001L}, 1, 1);
        bad(new int[]{0}, new long[]{0}, 1, 1); bad(new int[]{1}, new long[]{1}, 0, 1);
        bad(new int[]{1}, new long[0], 1, 1); bad(new int[21], new long[21], 1, 1);
        b.invalidate(); no(b.take(39, 9, 1950 * MS)); no(b.saved(1950 * MS).valid());
    }
    private static void bad(int[] ticks, long[] nanos, long receipt, int uses) {
        try { new WindPressureLiftBudget(ticks, nanos, receipt, uses); throw new AssertionError("malformed budget admitted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void yes(boolean value) { checks++; if (!value) throw new AssertionError("expected true at check " + checks); }
    private static void no(boolean value) { yes(!value); }
    private static void eq(long a, long b) { checks++; if (a != b) throw new AssertionError(a + " != " + b + " at check " + checks); }
}

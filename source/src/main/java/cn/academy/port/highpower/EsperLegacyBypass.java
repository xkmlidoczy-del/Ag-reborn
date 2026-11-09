package cn.academy.port.highpower;

/**
 * Test-harness switch only. The legacy GameTest suites exercise native damage, death and XP accounting with the kill command, forged deaths
 * and exact single-award XP expectations; when the GameTest JVM sets {@code academy.esperLegacyBypass} the esper guard and
 * growth styles stand aside for them, and the {@code academy_esper_guard} suite forces them back on. A production JVM never
 * sets the property, so the protections are always active there.
 */
public final class EsperLegacyBypass {
    private static final boolean PROPERTY = Boolean.getBoolean("academy.esperLegacyBypass");
    private static int forcing;
    private EsperLegacyBypass() {}
    /** True only inside a legacy GameTest JVM and outside an esper-protection test. */
    public static synchronized boolean standAside() { return PROPERTY && forcing == 0; }
    /** Reference-counted: tests of one batch run concurrently, each brackets itself with force(true)/force(false). */
    public static synchronized void force(boolean on) { forcing = Math.max(0, forcing + (on ? 1 : -1)); }
}

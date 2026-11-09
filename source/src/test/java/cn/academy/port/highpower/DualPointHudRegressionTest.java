package cn.academy.port.highpower;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Engine-free shared category HUD checks; exact amounts remain decimal through every presentation path. */
public final class DualPointHudRegressionTest {
    private static int checks;
    private static void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
    private static void denied(Runnable action, String label) { boolean denied = false; try { action.run(); } catch (IllegalArgumentException expected) { denied = true; } check(denied, label); }
    private static HighPowerRecoveryHudModel.Values values(String category, boolean shield, String reserve, String capacity, boolean active) {
        return new HighPowerRecoveryHudModel.Values("199999999999.99999999999999999999", "200000000000", "100000000000", "100000000000",
                "slow", "2500000000", 80, 70, 0, shield, reserve, capacity, active, category);
    }
    public static void main(String[] args) throws Exception {
        var md = values("meltdowner", false, "0", "0", false);
        var em = values("electromaster", true, "1", "100", true);
        check(md.meltdowner() && md.categoryLabel().equals("MD") && !md.shieldActive(), "MD reports its own category and idle stored shield");
        check(!md.domainVisible(), "EM-only reserve is absent from MD HUD");
        check(!em.meltdowner() && em.categoryLabel().equals("EM") && em.domainVisible(), "EM retains active shield and separate reserve presentation");
        var legacy = new HighPowerRecoveryHudModel.Values("1", "20", "1", "20", "full", "0", 0, 0, 0, false);
        check(legacy.category().equals("electromaster"), "existing EM model callers preserve their category");
        denied(() -> values("vector", false, "0", "0", false), "unsupported category is not silently admitted");
        check(values("meltdowner", true, "0", "0", false).shieldActive(), "NEW212 may activate MD base shield without an EM reserve");
        denied(() -> values("meltdowner", false, "1", "100", false), "MD cannot inherit EM reserve");
        denied(() -> values("meltdowner", false, "0", "0", true), "MD cannot inherit EM magnetic domain status");
        for (String language : List.of("en_us", "zh_cn", "zh_tw", "ja_jp", "de_de")) {
            var locale = HighPowerRecoveryHudModel.numberLocale(language);
            var pool = HighPowerRecoveryHudModel.pool(md.health(), md.maxHealth(), locale);
            check(!pool.full() && (pool.text().contains("<") || pool.text().contains("−") || !pool.shortfall().isEmpty()), "tiny exact deficit remains visible in " + language);
            check(!pool.text().matches(".*[0-9][eE][+-]?[0-9].*"), "HUD has no scientific notation in " + language);
            check(HighPowerRecoveryHudModel.barPixels(md.health(), md.maxHealth(), 180) == 179, "tiny deficit does not become a full bar");
            check(HighPowerRecoveryHudModel.barPixels("0.00000000000000000001", md.maxHealth(), 180) == 1, "tiny live amount never vanishes");
            for (int width : new int[]{150, 160, 180, 202}) {
                var row = HighPowerRecoveryHudModel.numberRow(width, 110, pool.text().length() * 8);
                check(row.valueX() + row.valueWidth() <= width - 5 && row.valueScale() > 0, "complete numeric row fits narrow category HUD");
                check(row.valueScale() * pool.text().length() * 8 <= row.valueWidth() + .001, "long exact compact pair fits without clipping");
            }
        }
        var session = new HighPowerRecoveryHudModel.Session(); Object connection = new Object(), world = new Object(), player = new Object(); UUID owner = new UUID(3, 7);
        session.synchronize(connection, world, player);
        check(session.accept(new HighPowerRecoveryHudModel.Identity(owner, 4, "minecraft:overworld", 1, 1), em, owner, 4, "minecraft:overworld"), "EM snapshot accepted");
        check(session.accept(new HighPowerRecoveryHudModel.Identity(owner, 4, "minecraft:overworld", 1, 2), md, owner, 4, "minecraft:overworld"), "category switch replaces shared HUD without ledger reset");
        check(!session.accept(new HighPowerRecoveryHudModel.Identity(owner, 4, "minecraft:overworld", 1, 1), em, owner, 4, "minecraft:overworld"), "late EM snapshot cannot restore old category");
        for (int tick = 0; tick < HighPowerRecoveryHudModel.STALE_TICKS; tick++) session.tick(false);
        check(session.current() == null, "stale category snapshot stops hiding native hearts");
        String payload = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/HighPowerRecoveryHudPayload.java"));
        check(payload.contains("hp_recovery_hud_v6") && payload.contains("writeUtf(value.category(), 24)") && payload.contains("buffer.readUtf(24)"), "category wire is explicitly versioned and bounded");
        check(payload.contains("electromaster && MagneticDomain.active(player)") && !payload.contains("playToServer("), "domain is category gated and HUD remains read-only");
        String renderer = Files.readString(Path.of("src/main/java/cn/academy/port/client/HighPowerRecoveryHud.java"));
        check(renderer.contains("values.categoryLabel()") && !renderer.contains("\"ACA / EM"), "display does not hardcode EM title");
        check(renderer.contains("values.shieldHolding()") && renderer.contains("shield_broken") && renderer.contains("if (values.domainVisible())"), "MD detail does not expose EM-only reserve");
        check(renderer.contains("SESSION.current().category().equals(AcademyClient.state.category)"), "old-category visible snapshot is hidden during transition");
        System.out.println("PASS " + checks + " NEW211 category HUD, exact tiny deficits, widths and snapshot lifecycle checks");
    }
}

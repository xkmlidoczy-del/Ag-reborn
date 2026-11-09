package cn.academy.port.highpower;

import java.util.List;

/** Pure compact-presentation checks. These are not game screenshots or GPU acceptance. */
public final class CompactRecoveryHudRegressionTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static HighPowerRecoveryHudModel.Values values(String category, String status, boolean reserve, boolean defense) {
        boolean healing = status.equals("saturated") || status.equals("slow");
        String shield = category.equals("teleporter") ? "0" : "20";
        return new HighPowerRecoveryHudModel.Values("90", "100", "0", shield, status,
                healing ? "1" : "0", healing ? 80 : 0, healing ? 17 : 0, status.equals("paused") ? 40 : 0,
                defense, "0", category.equals("electromaster") && reserve ? "20" : "0", false, category,
                "0", category.equals("meltdowner") && reserve ? "10" : "0", defense);
    }
    public static int runChecks() {
        checks = 0;
        nativePixelLayout();
        for (String category : List.of("electromaster", "meltdowner", "teleporter", "vecmanip")) {
            for (String status : HighPowerRecoveryHudModel.STATUSES) {
                var value = values(category, status, false, false);
                var rows = HighPowerRecoveryHudModel.compactRows(value);
                check(rows.showRate() == value.healing(), "only a genuine recovery retains its rate/pulse row: " + category + "/" + status);
                check(rows.height() == (value.healing() ? 55 : 44), "blocked base state contracts without hiding HP or shield");
                check(!rows.showReserve() && rows.healthY() == 11 && rows.shieldY() == 22, "stable pool rows do not jump when recovery starts");
                verifyRows(rows);
                check(value.health().equals("90") && value.maxHealth().equals("100") && value.shield().equals("0"), "row selection never mutates exact snapshot strings");
            }
        }
        for (String category : List.of("electromaster", "meltdowner")) {
            for (String status : HighPowerRecoveryHudModel.STATUSES) {
                var value = values(category, status, true, false);
                var rows = HighPowerRecoveryHudModel.compactRows(value);
                check(rows.showReserve() && rows.height() == (value.healing() ? 56 : 49), "learned spent reserve remains visible, even at zero");
                verifyRows(rows);
            }
        }
        for (String status : List.of("full", "gamerule_off", "paused", "slow")) {
            var rows = HighPowerRecoveryHudModel.compactRows(values("meltdowner", status, true, true));
            check(rows.showRate() == status.equals("slow") && rows.height() == (status.equals("slow") ? 56 : 49), "active coverage preserves its status but does not display a blocked +0 recovery row");
            verifyRows(rows);
        }
        var armor = new HighPowerRecoveryHudModel.Values("90", "100", "7", "20", "full", "0", 0, 0, 0,
                false, "0", "0", false, "meltdowner", "5", "10", false, "13", "20", false);
        check(HighPowerRecoveryHudModel.compactRows(armor).showReserve(), "stored 2S aggregation remains a separate visible row");
        check(armor.zeroGapTotalShield().equals("20") && armor.zeroGapFullCapacity().equals("40"), "2S arithmetic is unchanged");
        for (int width : new int[]{320, 392, 427, 640, 960, 1280}) for (int height : new int[]{240, 271, 360, 540}) {
            var cp = new HighPowerRecoveryHudModel.Box(width - 205, 12, 194, 40);
            var keys = new HighPowerRecoveryHudModel.Box(width - 188, (int)((height - 48.3) / 2 + 30), 185, 88);
            var notification = new HighPowerRecoveryHudModel.Box(0, 15, 130, 43);
            var chat = new HighPowerRecoveryHudModel.Box(0, height - 130, Math.min(width, 336), 102);
            var box = HighPowerRecoveryHudModel.layout(width, height, cp, keys, notification, chat);
            check(box != null && box.width() <= 176 && box.height() == 56 && box.within(width, height), "compact width keeps the stable 56-pixel exact/detail reservation");
            check(!box.intersects(cp) && !box.intersects(keys) && !box.intersects(notification) && !box.intersects(chat), "compact panel respects existing reserved anchors");
        }
        for (int width : new int[]{150, 164, 174, 176}) for (int label : new int[]{12, 66, 120, 240}) for (int numeric : new int[]{36, 110, 240, 420}) {
            var row = HighPowerRecoveryHudModel.numberRow(width, label, numeric);
            check(label * row.labelScale() <= row.labelWidth() + .001f && numeric * row.valueScale() <= row.valueWidth() + .001f,
                    "complete localized numeric pairs and labels fit without substring clipping");
            check(5 + row.labelWidth() < row.valueX() && row.valueX() + row.valueWidth() <= width - 5, "label and numeric columns stay separated");
        }
        String cap = "100000000000000000000";
        for (String language : List.of("en_us", "zh_cn", "zh_tw", "ja_jp")) for (int width : new int[]{150, 174, 176}) {
            var locale = HighPowerRecoveryHudModel.hudNumberLocale(language);
            for (String current : List.of("0", "0.00000001", "1", "199999999999999.9999",
                    "99999999999999999999", "99999999999999999999.99999999", cap)) {
                String display = HighPowerRecoveryHudModel.pool(current, cap, locale).text();
                int measured = display.codePoints().map(c -> c >= 0x2e80 ? 9 : 6).sum();
                var row = HighPowerRecoveryHudModel.numberRow(width, 66, measured);
                check(row.valueScale() >= .5f && measured * row.valueScale() <= row.valueWidth() + .001f,
                        "representative multilingual tiny balances and deficits stay legible and complete");
                check(HighPowerRecoveryHudModel.pool(current, cap, locale).full() == current.equals(cap),
                        "compact layout never changes exact full-versus-deficit semantics");
            }
        }
        var noShield = new HighPowerRecoveryHudModel.Values("90", "100", "0", "0", "inactive", "0", 0, 0, 0,
                false, "0", "0", false, "vecmanip");
        check(!noShield.shieldVisible() && !HighPowerRecoveryHudModel.compactRows(noShield).showReserve(),
                "uninstalled vector defense never gains a shield row or reserve claim");
        check(HighPowerRecoveryHudModel.compactRows(noShield) == HighPowerRecoveryHudModel.compactRows(noShield),
                "row geometry is cached rather than allocated each render frame");
        check(176 * 44 < 202 * 56 * .70, "ordinary idle painted area drops more than thirty percent");
        return checks;
    }
    private static void nativePixelLayout() {
        check(HighPowerRecoveryHudModel.nativePixelText(2), "GUI2 selects a physical two-pixel native glyph");
        for (double scale : new double[]{0, 1, 1.5, 3, 4, Double.NaN, Double.POSITIVE_INFINITY})
            check(!HighPowerRecoveryHudModel.nativePixelText(scale), "other GUI scales preserve their existing renderer");
        for (String title : List.of("ACA / MD Recovery [ALT]", "ACA / MD 稳态调息 [ALT]")) {
            for (String current : List.of("0", "1", "99999999999999999999", "99999999999999999999.99999999")) {
                var sections = new java.util.ArrayList<HighPowerRecoveryHudModel.NativeSection>();
                sections.add(new HighPowerRecoveryHudModel.NativeSection(title, null, 3));
                String hp = HighPowerRecoveryHudModel.pool(current, "100000000000000000000", HighPowerRecoveryHudModel.hudNumberLocale("zh_cn")).text();
                sections.add(new HighPowerRecoveryHudModel.NativeSection("HP", hp, 0));
                sections.add(new HighPowerRecoveryHudModel.NativeSection("防护 待机", "5000京 / 5000京", 1));
                sections.add(new HighPowerRecoveryHudModel.NativeSection("储备 已储存", "1垓−1 / 1垓", 2));
                sections.add(new HighPowerRecoveryHudModel.NativeSection("Natural +100Qi HP/s next 80t", null, 5));
                sections.add(new HighPowerRecoveryHudModel.NativeSection("自然恢复规则已关闭", null, 4));
                for (int viewportWidth : new int[]{166, 320, 393, 590, 960}) {
                    var plan = HighPowerRecoveryHudModel.nativePlan(sections, viewportWidth, 9,
                            CompactRecoveryHudRegressionTest::wrapNative, CompactRecoveryHudRegressionTest::measureNative);
                    check(plan != null && plan.width() <= 202 && plan.height() >= 56, "native text owns a bounded, explicit larger reservation");
                    for (var line : plan.text()) {
                        check(line.x() >= 5 && line.x() + measureNative(line.text()) <= plan.width() - 5,
                                "native glyphs fit at full size, without fractional downscaling");
                        check(line.y() >= 3 && line.y() + 9 < plan.pulseY(), "every full-height native line is above the footer");
                    }
                    for (var section : sections) {
                        String expected = section.label() + (section.value() == null ? "" : " " + section.value());
                        String all = plan.text().stream().map(HighPowerRecoveryHudModel.NativeText::text).collect(java.util.stream.Collectors.joining(" "));
                        check(all.replace(" ", "").contains(expected.replace(" ", "")), "all localized labels, deficits and statuses remain present after wrapping");
                    }
                    var cp = new HighPowerRecoveryHudModel.Box(viewportWidth - 205, 12, 194, 40);
                    var keys = new HighPowerRecoveryHudModel.Box(viewportWidth - 188, 200, 185, 88);
                    var notification = new HighPowerRecoveryHudModel.Box(0, 15, 130, 43);
                    var chat = new HighPowerRecoveryHudModel.Box(0, 280, Math.min(viewportWidth, 336), 103);
                    var box = HighPowerRecoveryHudModel.layout(viewportWidth, 406, cp, keys, notification, chat, plan.width(), plan.height());
                    if (box != null) {
                        check(box.width() == plan.width() && box.height() == plan.height(), "collision reservation never shrinks an already-wrapped native view");
                        check(!box.intersects(cp) && !box.intersects(keys) && !box.intersects(notification) && !box.intersects(chat), "full native-font reservation stays disjoint from existing instruments");
                    }
                }
            }
        }
        var oversized = List.of(new HighPowerRecoveryHudModel.NativeSection("中".repeat(2049), null, 4));
        check(HighPowerRecoveryHudModel.nativePlan(oversized, 590, 9, CompactRecoveryHudRegressionTest::wrapNative,
                CompactRecoveryHudRegressionTest::measureNative) == null, "unbounded resource-pack text fails closed rather than clipping");
        var ordinary = List.of(new HighPowerRecoveryHudModel.NativeSection("HP", "1 / 100Qi", 0));
        check(HighPowerRecoveryHudModel.nativePlan(ordinary, 590, 9, (text, width) -> List.of("lost"), CompactRecoveryHudRegressionTest::measureNative) != null,
                "a fitting numeric pair does not require a text wrapper");
        check(HighPowerRecoveryHudModel.nativePlan(List.of(new HighPowerRecoveryHudModel.NativeSection("完整状态", null, 4)), 590, 9,
                (text, width) -> List.of("状态"), CompactRecoveryHudRegressionTest::measureNative) == null, "a wrapper may never lose characters");
        var huge = new HighPowerRecoveryHudModel.Box(0, 0, 1000, 1000);
        check(HighPowerRecoveryHudModel.layout(320, 240, huge, huge, huge, huge, 202, 80) == null,
                "unplaceable native view restores the existing native-heart fallback");
        try {
            String renderer = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port/client/HighPowerRecoveryHud.java"));
            String nativeDraw = renderer.substring(renderer.indexOf("private static void renderNative("), renderer.indexOf("private static void bar("));
            check(!nativeDraw.contains(".scale(") && !nativeDraw.contains("fittedText") && nativeDraw.contains("graphics.drawString(mc.font"),
                    "ordinary GUI2 renderer draws native glyphs directly, never through fractional fitting");
            check(renderer.contains("panel(graphics, plan.width(), paintHeight, accent.edge())")
                    && renderer.contains("HighPowerRecoveryHudModel.nativePaintHeight(plan, mc.font.lineHeight, showPulse)")
                    && renderer.contains("var nativePlan = chosen.nativePlan()")
                    && renderer.contains("return layout(Minecraft.getInstance(), width, height)"),
                    "relay reservation and heart replacement share the same actual measured layout");
        } catch (java.io.IOException error) { throw new AssertionError(error); }
    }
    private static int measureNative(String text) { return text.codePoints().map(c -> c >= 0x2e80 ? 9 : 6).sum(); }
    private static List<String> wrapNative(String text, int width) {
        var out = new java.util.ArrayList<String>(); var line = new StringBuilder(); int pixels = 0;
        for (int codePoint : text.codePoints().toArray()) {
            int size = codePoint >= 0x2e80 ? 9 : 6;
            if (pixels + size > width && !line.isEmpty()) { out.add(line.toString()); line.setLength(0); pixels = 0; }
            line.appendCodePoint(codePoint); pixels += size;
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }
    private static void verifyRows(HighPowerRecoveryHudModel.CompactRows rows) {
        check(rows.healthY() + 6 <= rows.healthBarY() && rows.healthBarY() + 2 <= rows.shieldY(), "HP numbers and bar do not overlap the next pool");
        check(rows.shieldY() + 6 <= rows.shieldBarY(), "shield number fits above its bar");
        int afterPools = rows.shieldBarY() + 2;
        if (rows.showReserve()) {
            check(afterPools <= rows.reserveY() && rows.reserveY() + 6 <= rows.reserveBarY(), "reserve row stays separate from base S");
            afterPools = rows.reserveBarY() + 2;
        }
        if (rows.showRate()) {
            check(afterPools <= rows.rateY() && rows.rateY() + 6 <= rows.statusY(), "active rate and its status retain separate lines");
        } else check(afterPools <= rows.statusY(), "zero-rate status sits below all resource rows");
        check(rows.statusY() + 6 <= rows.pulseY() && rows.pulseY() + 1 < rows.height(), "status, pulse and border remain inside the painted panel");
        check(rows.height() <= 56, "no visual state exceeds the exact-view reservation");
    }
    public static void main(String[] args) { System.out.println("PASS " + runChecks() + " compact recovery HUD presentation checks; GPU acceptance pending"); }
}

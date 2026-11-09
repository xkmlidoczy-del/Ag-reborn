package cn.academy.port.client;

import cn.academy.port.client.RemoteScatterRelayHudLayout.Content;
import cn.academy.port.client.RemoteScatterRelayHudLayout.Kind;
import cn.academy.port.client.RemoteScatterRelayHudLayout.Layout;
import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import cn.academy.port.highpower.HighPowerRecoveryHudModel.Box;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Engine-free native-font measurement seam and actual 393x272 overlap regression. */
public final class RemoteScatterRelayHudLayoutRegressionTest {
    private static int checks;
    private static final Content ENGLISH = new Content("Relay 4/4 · 20s · Endpoint fixed",
            "Tap: fire along the fixed route · Crouch hold: clear the whole route",
            "Tap: fire · Crouch hold: clear", "Last result: A route leg exceeds 32 m");
    private static final Content CHINESE = new Content("中继 4/4 · 20秒 · 终点已固定",
            "短按沿固定路线开火 · 潜行长按清除整条路线", "短按开火 · 潜行长按清除", "上次结果：路线中有一段超过32米");

    private RemoteScatterRelayHudLayoutRegressionTest() {}
    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        actualSmallViewport();
        commonGuiScales();
        optionalHelpFallsBack();
        customPositions();
        invalidAndTinyScreens();
        measuredWrappingContract();
        existingGlyphBudget();
        System.out.println("PASS RemoteScatterRelayHudLayoutRegressionTest: " + checks + " checks");
    }

    private static void actualSmallViewport() {
        List<Box> reserved = defaultInstruments(393, 272);
        Box resource = reserved.getLast();
        check(resource.equals(new Box(205, 54, 176, 56)), "fixture derives compact resource width and stable exact-view reservation from its model");
        check(reserved.contains(new Box(0, 141, 336, 103)), "fixture reserves the same default unfocused chat rectangle");
        for (Content content : List.of(ENGLISH, CHINESE)) {
            Layout<String> layout = fit(393, 272, content, reserved);
            verify(layout, 393, 272, 9, reserved);
            complete(layout, Kind.STATUS, content.status());
            complete(layout, Kind.RESULT, content.lastResult());
            if (content == CHINESE)
                check(layout.lines().stream().anyMatch(line -> line.kind() == Kind.HELP), "short Chinese text retains readable instruction help");
            else complete(layout, Kind.HELP, "");
            check(layout.bounds().x() < resource.x() && layout.bounds().y() >= 62
                            && layout.bounds().y() + layout.bounds().height() <= 137,
                    "relay occupies measured space below notification and left of resource HUD");
        }
    }

    private static void commonGuiScales() {
        // These are already-scaled GUI dimensions; no pixel-resolution or GUI-scale assumption belongs here.
        for (int[] viewport : new int[][]{{320, 240}, {393, 272}, {426, 240}, {640, 360},
                {854, 480}, {960, 540}, {1280, 720}, {1920, 1080}}) {
            int width = viewport[0], height = viewport[1];
            List<Box> reserved = defaultInstruments(width, height);
            for (Content original : List.of(ENGLISH, CHINESE)) for (boolean result : List.of(false, true)) {
                Content content = new Content(original.status(), original.instructions(),
                        original.compactInstructions(), result ? original.lastResult() : "");
                Layout<String> layout = fit(width, height, content, reserved);
                verify(layout, width, height, 9, reserved);
                complete(layout, Kind.STATUS, content.status());
                complete(layout, Kind.RESULT, result ? content.lastResult() : "");
                check(layout.lineStep() == 11, "native nine-pixel font keeps full scale and two pixels of leading");
            }
        }
        Content exact = new Content("Relay 4/4 · 19.95s · Ready", "", "", "Last result: 1234567890 / 9876543210");
        Layout<String> layout = fit(393, 272, exact, defaultInstruments(393, 272));
        verify(layout, 393, 272, 9, defaultInstruments(393, 272));
        complete(layout, Kind.STATUS, exact.status());
        complete(layout, Kind.RESULT, exact.lastResult());
    }

    private static void optionalHelpFallsBack() {
        // There is exactly one 160x39 safe panel: three native rows, with status and result taking two.
        List<Box> crowded = List.of(new Box(176, 0, 224, 300), new Box(0, 51, 400, 249));
        Content full = new Content("Relay 4/4", "Full instructions need several lines and cannot occupy this narrow opening",
                "Tap: fire", "Last result: hit");
        Layout<String> compact = fit(400, 300, full, crowded);
        verify(compact, 400, 300, 9, crowded);
        complete(compact, Kind.STATUS, full.status());
        complete(compact, Kind.RESULT, full.lastResult());
        complete(compact, Kind.HELP, full.compactInstructions());

        List<Box> lessRoom = List.of(new Box(176, 0, 224, 300), new Box(0, 40, 400, 260));
        Layout<String> noHelp = fit(400, 300, full, lessRoom);
        verify(noHelp, 400, 300, 9, lessRoom);
        complete(noHelp, Kind.STATUS, full.status());
        complete(noHelp, Kind.RESULT, full.lastResult());
        complete(noHelp, Kind.HELP, "");

        List<Box> oneRow = List.of(new Box(176, 0, 224, 300), new Box(0, 29, 400, 271));
        Layout<String> statusOnly = fit(400, 300, full, oneRow);
        verify(statusOnly, 400, 300, 9, oneRow);
        complete(statusOnly, Kind.STATUS, full.status());
        complete(statusOnly, Kind.RESULT, "");
        complete(statusOnly, Kind.HELP, "");
    }

    private static void customPositions() {
        List<Box> moved = List.of(new Box(8, 12, 194, 40), new Box(8, 56, 201, 56),
                new Box(8, 145, 185, 88), new Box(230, 15, 130, 43));
        Layout<String> layout = fit(640, 360, ENGLISH, moved);
        verify(layout, 640, 360, 9, moved);
        complete(layout, Kind.STATUS, ENGLISH.status());
        complete(layout, Kind.RESULT, ENGLISH.lastResult());
        check(layout.bounds().x() >= 213 || layout.bounds().y() >= 116,
                "custom left resource panel moves relay to a free side or below its real bounds");

        var clipped = new ArrayList<>(defaultInstruments(640, 360));
        clipped.add(new Box(-400, -100, 420, 180));
        clipped.add(new Box(Integer.MAX_VALUE - 2, 0, 100, 100));
        Layout<String> partial = fit(640, 360, CHINESE, clipped);
        verify(partial, 640, 360, 9, clipped.subList(0, clipped.size() - 1));
        complete(partial, Kind.STATUS, CHINESE.status());
        complete(partial, Kind.RESULT, CHINESE.lastResult());

        // Large font resource packs still use their own line height and widths, never a shrink-to-fit pose.
        Layout<String> taller = RemoteScatterRelayHudLayout.fit(640, 360, 16, CHINESE,
                defaultInstruments(640, 360), RemoteScatterRelayHudLayoutRegressionTest::wrap,
                RemoteScatterRelayHudLayoutRegressionTest::measure);
        verify(taller, 640, 360, 16, defaultInstruments(640, 360));
        complete(taller, Kind.RESULT, CHINESE.lastResult());
    }

    private static void invalidAndTinyScreens() {
        for (int[] viewport : new int[][]{{1, 1}, {80, 60}, {95, 200}, {320, 30}, {Integer.MAX_VALUE, 240}, {-1, 272}})
            check(fit(viewport[0], viewport[1], ENGLISH, List.of()) == null, "tiny or invalid viewport fails closed");
        check(fit(393, 272, ENGLISH, List.of(new Box(0, 0, 393, 272))) == null, "fully occupied GUI draws nothing");
        check(fit(393, 272, ENGLISH, List.of(new Box(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE))) == null,
                "oversized excluded rectangle cannot overflow into a false opening");
        check(fit(393, 272, new Content("", "help", "help", "result"), List.of()) == null, "help never renders without status");
        check(fit(393, 272, new Content("9".repeat(2049), "", "", ""), List.of()) == null, "excessive text is refused rather than truncated");
        check(fit(393, 272, ENGLISH, List.of(new Box(0, 0, -1, 20))) == null, "invalid instrument dimensions fail closed");
        check(RemoteScatterRelayHudLayout.fit(393, 272, 0, ENGLISH, List.of(),
                RemoteScatterRelayHudLayoutRegressionTest::wrap, RemoteScatterRelayHudLayoutRegressionTest::measure) == null,
                "invalid font height cannot create overlapping rows");
    }

    private static void measuredWrappingContract() {
        Content status = new Content("123456789012345678901234567890123456789012345678901234567890", "", "", "");
        Layout<String> wrapped = fit(393, 272, status, defaultInstruments(393, 272));
        verify(wrapped, 393, 272, 9, defaultInstruments(393, 272));
        complete(wrapped, Kind.STATUS, status.status());
        check(wrapped.lines().size() > 1, "an exact long numeric token wraps without losing digits");
        check(RemoteScatterRelayHudLayout.fit(393, 272, 9, status, List.of(),
                (text, width) -> List.of(text), RemoteScatterRelayHudLayoutRegressionTest::measure) == null,
                "wrapper returning an over-wide line is refused, never clipped");
        check(RemoteScatterRelayHudLayout.fit(393, 272, 9, status, List.of(),
                (text, width) -> List.of(), RemoteScatterRelayHudLayoutRegressionTest::measure) == null,
                "wrapper returning no status is refused");
        AtomicInteger calls = new AtomicInteger();
        check(RemoteScatterRelayHudLayout.fit(393, 272, 9, ENGLISH, List.of(new Box(0, 0, 393, 272)),
                (text, width) -> { calls.incrementAndGet(); return wrap(text, width); },
                RemoteScatterRelayHudLayoutRegressionTest::measure) == null, "blocked layout remains absent across all help tiers");
        check(calls.get() <= 64, "candidate widths are bounded and each text is wrapped only once per width");
    }

    private static void existingGlyphBudget() {
        Content exact = new Content("9".repeat(79), "", "", "");
        Layout<String> limit = fit(640, 360, exact, List.of());
        verify(limit, 640, 360, 9, List.of());
        complete(limit, Kind.STATUS, exact.status());
        check(limit.lines().stream().mapToInt(line -> line.text().codePointCount(0, line.text().length())).sum() * 2 + 2 == 160,
                "79 shadowed glyphs plus two background fills exactly retain the established 160 HUD quads");
        check(fit(640, 360, new Content("9".repeat(80), "", "", ""), List.of()) == null,
                "an 80-glyph status is refused rather than truncated or exceeding the old HUD allowance");

        Content compactSource = new Content("S".repeat(30), "H".repeat(30), "C".repeat(15), "R".repeat(30));
        Layout<String> compact = fit(960, 540, compactSource, List.of());
        verify(compact, 960, 540, 9, List.of());
        complete(compact, Kind.STATUS, compactSource.status());
        complete(compact, Kind.HELP, compactSource.compactInstructions());
        complete(compact, Kind.RESULT, compactSource.lastResult());

        Content noHelpSource = new Content("S".repeat(40), "H".repeat(35), "C".repeat(20), "R".repeat(30));
        Layout<String> noHelp = fit(960, 540, noHelpSource, List.of());
        verify(noHelp, 960, 540, 9, List.of());
        complete(noHelp, Kind.STATUS, noHelpSource.status());
        complete(noHelp, Kind.HELP, "");
        complete(noHelp, Kind.RESULT, noHelpSource.lastResult());

        Content statusSource = new Content("S".repeat(50), "H".repeat(30), "C".repeat(15), "R".repeat(40));
        Layout<String> statusOnly = fit(960, 540, statusSource, List.of());
        verify(statusOnly, 960, 540, 9, List.of());
        complete(statusOnly, Kind.STATUS, statusSource.status());
        complete(statusOnly, Kind.HELP, "");
        complete(statusOnly, Kind.RESULT, "");

        String supplementary = "中".repeat(70) + "\uD83D\uDE80".repeat(9);
        Layout<String> unicode = fit(960, 540, new Content(supplementary, "", "", ""), List.of());
        verify(unicode, 960, 540, 9, List.of());
        complete(unicode, Kind.STATUS, supplementary);
        check(supplementary.length() > RemoteScatterRelayHudLayout.MAX_GLYPHS,
                "budget counts Unicode code points rather than rejecting surrogate pairs as two glyphs");
        check(fit(960, 540, new Content(supplementary + " ", "", "", ""), List.of()) == null,
                "source spaces count conservatively even though native space normally emits no glyph quad");
    }

    private static Layout<String> fit(int width, int height, Content content, List<Box> reserved) {
        return RemoteScatterRelayHudLayout.fit(width, height, 9, content, reserved,
                RemoteScatterRelayHudLayoutRegressionTest::wrap, RemoteScatterRelayHudLayoutRegressionTest::measure);
    }

    private static List<Box> defaultInstruments(int width, int height) {
        int cpX = (int)Math.floor(width - 964 * .2 - 12);
        int keyX = (int)(width - 140 * .23) - 155;
        int keyY = (int)((height - 210 * .23) * .5 + 30);
        var cp = new Box(cpX, 12, 194, 40);
        var keys = new Box(keyX, keyY, 185, 88);
        var notification = new Box(0, 15, 130, 43);
        var chat = new Box(0, height - 40 - 91, Math.min(width, 336), 91 + 12);
        var reserved = new ArrayList<>(List.of(cp, keys, notification, chat));
        Box resource = HighPowerRecoveryHudModel.layout(width, height, cp, keys, notification, chat);
        if (resource != null) reserved.add(resource);
        return List.copyOf(reserved);
    }

    private static void verify(Layout<String> layout, int width, int height, int fontHeight, List<Box> reserved) {
        check(layout != null, "readable layout exists at " + width + "x" + height);
        Box bounds = layout.bounds();
        check(bounds.within(width, height) && bounds.x() >= 8 && bounds.y() >= 8,
                "panel bounds never use negative offsets or clip the GUI edge");
        check(bounds.width() <= RemoteScatterRelayHudLayout.MAX_PANEL_WIDTH, "bounded panel avoids a full-screen instruction wall");
        for (Box box : reserved) check(!bounds.intersects(box), "panel excludes actual existing HUD rectangle " + box);
        check(!bounds.intersects(new Box(width / 2 - 20, height / 2 - 20, 40, 40)), "panel preserves reticle exclusion");
        check(!bounds.intersects(new Box(width / 2 - 110, height - 64, 220, 64)), "panel preserves native status and hotbar");
        check(layout.lines().stream().mapToInt(line -> line.text().codePointCount(0, line.text().length())).sum()
                        <= RemoteScatterRelayHudLayout.MAX_GLYPHS,
                "all displayed tiers remain within the existing shadowed-glyph GUI budget");
        int previousY = -1;
        for (var line : layout.lines()) {
            check(line.x() >= bounds.x() + 4 && line.x() + line.width() <= bounds.x() + bounds.width() - 4,
                    "every complete measured line fits panel horizontally");
            check(line.y() >= bounds.y() + 4 && line.y() + fontHeight <= bounds.y() + bounds.height() - 4,
                    "every line fits vertically at full native size");
            check(line.width() == measure(line.text()), "line geometry uses the supplied native-width seam");
            check(previousY < 0 || line.y() - previousY == fontHeight + 2, "wrapped rows retain readable native leading");
            previousY = line.y();
        }
    }

    private static void complete(Layout<String> layout, Kind kind, String expected) {
        String actual = layout.lines().stream().filter(line -> line.kind() == kind)
                .map(RemoteScatterRelayHudLayout.Line::text).reduce("", String::concat);
        check(actual.equals(expected), "complete " + kind + " text survives wrapping, including every numeral: " + actual);
    }

    /** Representative Font.width metrics: Latin proportional widths and full-width CJK glyphs. */
    private static int measure(String text) { return text.codePoints().map(RemoteScatterRelayHudLayoutRegressionTest::glyph).sum(); }
    private static int glyph(int point) {
        if (point == ' ') return 4;
        if (point == 'i' || point == 'l' || point == ':' || point == '.') return 2;
        return point >= 0x2e80 ? 9 : 6;
    }
    /** Mirrors the native split adapter's essential contract: all glyphs survive, with bounded widths. */
    private static List<String> wrap(String text, int width) {
        var result = new ArrayList<String>();
        var line = new StringBuilder();
        int measured = 0;
        for (int point : text.codePoints().toArray()) {
            int next = glyph(point);
            if (line.length() > 0 && measured + next > width) {
                result.add(line.toString()); line.setLength(0); measured = 0;
            }
            line.appendCodePoint(point); measured += next;
        }
        if (line.length() > 0) result.add(line.toString());
        return result;
    }
}

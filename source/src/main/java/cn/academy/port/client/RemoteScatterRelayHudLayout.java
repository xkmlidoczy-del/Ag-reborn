package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerRecoveryHudModel.Box;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

/** NEW253-only native-font layout. Existing instruments always win over relay help text. */
public final class RemoteScatterRelayHudLayout {
    public static final int MARGIN = 8, PADDING = 4, GAP = 4, MIN_PANEL_WIDTH = 80, MAX_PANEL_WIDTH = 300;
    /** Existing 160-quad GUI allowance: one shadow plus one face per glyph, and two panel fills. */
    public static final int MAX_GLYPHS = 79;
    private static final int MAX_INSTRUMENTS = 16, MAX_WRAPPED_LINES = 32, MAX_TEXT_LENGTH = 2048;

    public enum Kind { STATUS, HELP, RESULT }
    /** A null or empty optional string means that row is absent; status is always required. */
    public record Content(String status, String instructions, String compactInstructions, String lastResult) {}
    /** Absolute GUI coordinates, using the supplied font's normal size, with no text scaling. */
    public record Line<T>(T text, Kind kind, int x, int y, int width) {}
    public record Layout<T>(Box bounds, int lineStep, List<Line<T>> lines) {
        public Layout { lines = List.copyOf(lines); }
    }
    private record Wrapped<T>(T text, Kind kind, int width) {}
    private record Rows<T>(List<Wrapped<T>> status, List<Wrapped<T>> help,
                           List<Wrapped<T>> compactHelp, List<Wrapped<T>> result) {}

    private RemoteScatterRelayHudLayout() {}

    /**
     * The caller supplies actual visible CP/key/notification/chat/resource boxes and native Font.split
     * and Font.width adapters. A wrapper must preserve the complete source, without clipping or ellipses.
     * Status and a supplied result take precedence over full help, compact help, and finally no help.
     * Only if those two cannot coexist within the free space and existing glyph allowance is the
     * complete status retained alone. Null means even that cannot fit safely. Code points in selected
     * source strings, including spaces, conservatively bound native shadowed text to 158 quads; the
     * caller's two panel fills retain the existing 160-quad HUD allowance. No text is shortened,
     * scaled down, or drawn over another instrument.
     */
    public static <T> Layout<T> fit(int width, int height, int fontHeight, Content content,
                                    List<Box> instruments,
                                    BiFunction<String, Integer, List<T>> wrap,
                                    ToIntFunction<T> measure) {
        if (width < MIN_PANEL_WIDTH + MARGIN * 2 || width > 32768 || height < MARGIN * 2
                || height > 32768 || fontHeight <= 0 || fontHeight > 32 || content == null
                || !validText(content.status(), true) || !validText(content.instructions(), false)
                || !validText(content.compactInstructions(), false) || !validText(content.lastResult(), false)
                || instruments == null || instruments.size() > MAX_INSTRUMENTS || wrap == null || measure == null)
            return null;
        var reserved = new ArrayList<Box>();
        for (Box instrument : instruments) {
            if (instrument == null || instrument.width() < 0 || instrument.height() < 0) return null;
            reserve(reserved, instrument, width, height);
        }
        reserve(reserved, new Box(width / 2 - 20, height / 2 - 20, 40, 40), width, height);
        reserve(reserved, new Box(width / 2 - 110, height - 64, 220, 64), width, height);

        // A few familiar panel widths plus the real left/right gaps keep this bounded and independent
        // of screen resolution. Actual instrument edges are needed at GUI 393x272, not guessed offsets.
        var widths = new LinkedHashSet<Integer>();
        int maximum = Math.min(MAX_PANEL_WIDTH, width - MARGIN * 2);
        widths.add(maximum);
        for (int preferred : new int[]{240, 200, 160, 120, 96, MIN_PANEL_WIDTH})
            if (preferred <= maximum) widths.add(preferred);
        for (Box box : reserved) {
            addWidth(widths, box.x() - MARGIN, maximum);
            addWidth(widths, width - MARGIN - box.x() - box.width(), maximum);
        }
        var orderedWidths = widths.stream().sorted(java.util.Comparator.reverseOrder()).toList();
        Map<Integer, Rows<T>> cache = new HashMap<>();
        int step = fontHeight + 2;
        // The fourth tier is reserved for genuinely tiny/crowded screens, never to make room for help.
        for (int tier = 0; tier < 4; tier++) {
            if (tier == 0 && empty(content.instructions())
                    || tier == 1 && (empty(content.compactInstructions())
                        || content.compactInstructions().equals(content.instructions()))
                    || tier == 3 && empty(content.lastResult())) continue;
            String helpText = tier == 0 ? content.instructions() : tier == 1 ? content.compactInstructions() : "";
            int glyphs = glyphs(content.status()) + glyphs(helpText) + (tier < 3 ? glyphs(content.lastResult()) : 0);
            if (glyphs > MAX_GLYPHS) continue;
            for (int panelWidth : orderedWidths) {
                Rows<T> rows = cache.computeIfAbsent(panelWidth, key -> new Rows<>(
                        wrapped(content.status(), Kind.STATUS, key - PADDING * 2, wrap, measure),
                        wrapped(content.instructions(), Kind.HELP, key - PADDING * 2, wrap, measure),
                        wrapped(content.compactInstructions(), Kind.HELP, key - PADDING * 2, wrap, measure),
                        wrapped(content.lastResult(), Kind.RESULT, key - PADDING * 2, wrap, measure)));
                if (rows.status() == null || tier < 3 && rows.result() == null) continue;
                List<Wrapped<T>> help = tier == 0 ? rows.help() : tier == 1 ? rows.compactHelp() : List.of();
                if (help == null) continue;
                var lines = new ArrayList<>(rows.status());
                lines.addAll(help);
                if (tier < 3) lines.addAll(rows.result());
                if (lines.size() > MAX_WRAPPED_LINES) continue;
                int panelHeight = PADDING * 2 + lines.size() * step - 2;
                if (panelHeight > height - MARGIN * 2) continue;
                Box bounds = locate(width, height, panelWidth, panelHeight, reserved);
                if (bounds == null) continue;
                var placed = new ArrayList<Line<T>>(lines.size());
                for (int row = 0; row < lines.size(); row++) {
                    Wrapped<T> line = lines.get(row);
                    placed.add(new Line<>(line.text(), line.kind(), bounds.x() + PADDING,
                            bounds.y() + PADDING + row * step, line.width()));
                }
                return new Layout<>(bounds, step, placed);
            }
        }
        return null;
    }

    private static Box locate(int width, int height, int panelWidth, int panelHeight, List<Box> reserved) {
        var xs = new LinkedHashSet<Integer>();
        var ys = new LinkedHashSet<Integer>();
        xs.add(MARGIN);
        ys.add(54); // The familiar upper-left band is preferred when its measured bounds are free.
        for (Box box : reserved) {
            ys.add(box.y() + box.height());
            xs.add(box.x() + box.width());
            xs.add(box.x() - panelWidth);
            ys.add(box.y() - panelHeight);
        }
        xs.add(width - MARGIN - panelWidth);
        ys.add(MARGIN);
        ys.add(height - MARGIN - panelHeight);
        for (int x : xs) for (int y : ys) {
            if (x < MARGIN || y < MARGIN || x + panelWidth > width - MARGIN
                    || y + panelHeight > height - MARGIN) continue;
            Box candidate = new Box(x, y, panelWidth, panelHeight);
            if (reserved.stream().noneMatch(candidate::intersects)) return candidate;
        }
        return null;
    }

    private static <T> List<Wrapped<T>> wrapped(String text, Kind kind, int width,
                                                BiFunction<String, Integer, List<T>> wrap,
                                                ToIntFunction<T> measure) {
        if (empty(text)) return List.of();
        List<T> source = wrap.apply(text, width);
        if (source == null || source.isEmpty() || source.size() > MAX_WRAPPED_LINES) return null;
        var result = new ArrayList<Wrapped<T>>(source.size());
        for (T line : source) {
            if (line == null) return null;
            int measured = measure.applyAsInt(line);
            if (measured < 0 || measured > width) return null;
            result.add(new Wrapped<>(line, kind, measured));
        }
        return result;
    }

    /** Clip before expanding, so off-screen custom HUD origins cannot overflow or create false gaps. */
    private static void reserve(List<Box> reserved, Box box, int width, int height) {
        long left = Math.max(0L, box.x()), top = Math.max(0L, box.y());
        long right = Math.min((long)width, (long)box.x() + box.width());
        long bottom = Math.min((long)height, (long)box.y() + box.height());
        if (left >= right || top >= bottom) return;
        int x = (int)Math.max(0, left - GAP), y = (int)Math.max(0, top - GAP);
        reserved.add(new Box(x, y, (int)Math.min(width, right + GAP) - x,
                (int)Math.min(height, bottom + GAP) - y));
    }

    private static void addWidth(LinkedHashSet<Integer> widths, int width, int maximum) {
        if (width >= MIN_PANEL_WIDTH && width <= maximum) widths.add(width);
    }
    private static boolean validText(String text, boolean required) {
        return (!required || !empty(text)) && (text == null || text.length() <= MAX_TEXT_LENGTH);
    }
    private static int glyphs(String text) { return text == null ? 0 : text.codePointCount(0, text.length()); }
    private static boolean empty(String text) { return text == null || text.isEmpty(); }
}

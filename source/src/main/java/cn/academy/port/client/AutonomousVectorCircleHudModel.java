package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import cn.academy.port.highpower.HighPowerRecoveryHudModel.Box;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

/** NEW452-only read-only owner instrument; the exact receipt is never modified by formatting or layout. */
public final class AutonomousVectorCircleHudModel {
    private AutonomousVectorCircleHudModel() {}
    public record Content(String shield, String status) {
        public String text() { return shield + "\n" + status; }
    }
    public static boolean visible(AutonomousVectorCircleState.Controller c) {
        return c != null && c.local() && c.quiet() >= 0 && c.quiet() < AutonomousVectorCircleState.QUIET_TICKS
                && c.frame().phase() != AutonomousVectorCircleState.ENDED && AutonomousVectorCircleState.valid(c.frame());
    }
    /** Uses the existing locale units and deficit-preserving pool formatter, never scientific notation. */
    public static Content content(AutonomousVectorCircleState.Controller c, String language,
                                  BiFunction<String, String, String> text, BiFunction<String, String, String> numbers) {
        if (!visible(c) || text == null || numbers == null) return null;
        var frame = c.frame();
        String pair = frame.availableShield().isEmpty() ? text.apply("shield_unknown", "unavailable")
                : HighPowerRecoveryHudModel.pool(frame.availableShield(), frame.shieldCapacity(),
                        HighPowerRecoveryHudModel.hudNumberLocale(language, numbers)).text();
        String shield = text.apply("shield_label", "Circle · Available shield") + " " + pair;
        String condition = frame.ready() && frame.remainingHits() > 0
                ? text.apply("state_ready", "Ready") : text.apply("state_paused", "Paused");
        String status = text.apply("quota_label", "Quota") + " " + frame.remainingHits() + "/8 · " + condition;
        return new Content(shield, status);
    }
    public record Line<T>(T text, int x, int y, int width) {}
    public record Layout<T>(Box bounds, List<Line<T>> lines) {
        public Layout { lines = List.copyOf(lines); }
    }
    /** Both required lines move and wrap together at native font size; existing instruments win. */
    public static <T> Layout<T> layout(int width, int height, int fontHeight, Content content,
            List<Box> instruments, BiFunction<String, Integer, List<T>> wrap, ToIntFunction<T> measure) {
        if (width < 96 || width > 32768 || height < 48 || height > 32768 || fontHeight <= 0 || fontHeight > 32
                || content == null || content.shield() == null || content.status() == null
                || content.shield().isBlank() || content.status().isBlank() || content.text().length() > 256
                || instruments == null || instruments.size() > 16 || wrap == null || measure == null) return null;
        var reserved = new ArrayList<Box>();
        for (Box box : instruments) {
            if (box == null || box.width() < 0 || box.height() < 0) return null;
            reserve(reserved, box, width, height);
        }
        reserve(reserved, new Box(width / 2 - 20, height / 2 - 20, 40, 40), width, height);
        reserve(reserved, new Box(width / 2 - 110, height - 64, 220, 64), width, height);
        int maximum = Math.min(300, width - 16), step = fontHeight + 2;
        var widths = new LinkedHashSet<Integer>(); widths.add(maximum);
        for (int preferred : new int[]{240, 200, 160, 120, 96, 80}) if (preferred <= maximum) widths.add(preferred);
        for (Box box : reserved) {
            for (int gap : new int[]{box.x() - 8, width - 8 - box.x() - box.width()})
                if (gap >= 80 && gap <= maximum) widths.add(gap);
        }
        for (int panelWidth : widths.stream().sorted(java.util.Comparator.reverseOrder()).toList()) {
            List<T> rows = wrap.apply(content.text(), panelWidth - 8);
            if (rows == null || rows.size() < 2 || rows.size() > 8) continue;
            var measures = new ArrayList<Integer>(); boolean valid = true;
            for (T row : rows) {
                int pixels = row == null ? -1 : measure.applyAsInt(row);
                if (pixels < 0 || pixels > panelWidth - 8) { valid = false; break; }
                measures.add(pixels);
            }
            if (!valid) continue;
            int panelHeight = 6 + rows.size() * step;
            var xs = new LinkedHashSet<Integer>(); var ys = new LinkedHashSet<Integer>();
            xs.add(8); ys.add(54);
            for (Box box : reserved) {
                xs.add(box.x() + box.width()); xs.add(box.x() - panelWidth);
                ys.add(box.y() + box.height()); ys.add(box.y() - panelHeight);
            }
            xs.add(width - 8 - panelWidth); ys.add(8); ys.add(height - 8 - panelHeight);
            for (int x : xs) for (int y : ys) {
                if (x < 8 || y < 8 || x + panelWidth > width - 8 || y + panelHeight > height - 8) continue;
                Box bounds = new Box(x, y, panelWidth, panelHeight);
                if (reserved.stream().anyMatch(bounds::intersects)) continue;
                var lines = new ArrayList<Line<T>>();
                for (int i = 0; i < rows.size(); i++) lines.add(new Line<>(rows.get(i), x + 4, y + 4 + i * step, measures.get(i)));
                return new Layout<>(bounds, lines);
            }
        }
        return null;
    }
    private static void reserve(List<Box> result, Box box, int width, int height) {
        long left = Math.max(0L, box.x()), top = Math.max(0L, box.y());
        long right = Math.min((long)width, (long)box.x() + box.width());
        long bottom = Math.min((long)height, (long)box.y() + box.height());
        if (left >= right || top >= bottom) return;
        int x = (int)Math.max(0, left - 4), y = (int)Math.max(0, top - 4);
        result.add(new Box(x, y, (int)Math.min(width, right + 4) - x, (int)Math.min(height, bottom + 4) - y));
    }
}

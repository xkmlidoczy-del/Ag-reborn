package cn.academy.port.client;

import cn.academy.port.highpower.core.TacticalCoordinateRules;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;

/** Game-independent geometry and input fences for the explicit NEW353 menu. */
public final class TacticalCoordinateUiState {
    public static final int SLOTS = 6, MAX_RECIPIENTS = 32, MAX_NOTICES = 8;
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) { return px >= x && py >= y && px < x + width && py < y + height; }
    }
    public record Layout(Rect panel, int inset, int columnWidth) {
        public Rect button(int column, int row) { return new Rect(panel.x + inset + column * (columnWidth + 6), panel.y + 108 + row * 24, columnWidth, 20); }
        public Rect tab(int index) { int w = (panel.width - 2 * inset - 5 * 4) / SLOTS; return new Rect(panel.x + inset + index * (w + 4), panel.y + 26, w, 20); }
    }
    public static Layout layout(int width, int height) {
        int panelWidth = Math.min(296, Math.max(120, width - 12));
        int panelHeight = 174;
        return new Layout(new Rect((width - panelWidth) / 2, Math.max(3, (height - panelHeight) / 2), panelWidth, panelHeight), 8, (panelWidth - 22) / 2);
    }
    public static int selected(int index, int delta) { return Math.floorMod(index + delta, SLOTS); }
    public static float normalizedYaw(float yaw) { return Float.isFinite(yaw) ? (float) (((yaw % 360) + 360) % 360) : 0; }
    public static long invitationDeadline(long now, int remaining, long previousDeadline) {
        return Math.min(previousDeadline, now + Math.max(0, Math.min(TacticalCoordinateRules.INVITE_TICKS, remaining)));
    }
    /** Observed motion on an uncertain or failed transport is not a confirmed arrival. */
    public static boolean confirmedArrival(String phase, String reason, boolean moved) {
        return moved && "success".equals(phase) && "".equals(reason);
    }

    /** A held arrow/Enter never repeats. Opening with a held input requires release before action. */
    public static final class Keys {
        private final Set<Integer> down = new HashSet<>();
        public void blockHeld(int key) { down.add(key); }
        public boolean press(int key, boolean panelFocused) { boolean edge = down.add(key); return edge && panelFocused; }
        public void release(int key) { down.remove(key); }
        public void clear() { down.clear(); }
    }
    public record Identity(long token, UUID nonce) {}
    /** Only an explicit review request may replace its exact initiating chat or the empty world screen. */
    public static final class ReviewGate {
        private UUID nonce;
        private Object origin;
        private long session;
        public void begin(UUID nextNonce, Object initiatingScreen, long generation) {
            nonce = nextNonce; origin = initiatingScreen; session = generation;
        }
        public boolean active() { return nonce != null; }
        public boolean expects(UUID candidate, long generation) { return nonce != null && nonce.equals(candidate) && session == generation; }
        public boolean allows(Object currentScreen, long generation) {
            return active() && session == generation && (currentScreen == null || currentScreen == origin);
        }
        public void cancel(UUID candidate) { if (nonce != null && nonce.equals(candidate)) clear(); }
        public void clear() { nonce = null; origin = null; session = 0; }
    }
    /** Bounded receipt replay protection; retired identities cannot be revived by late packets. */
    public static final class Receipts {
        private final LinkedHashMap<Identity, Long> latest = new LinkedHashMap<>();
        private final Set<Identity> retired = new HashSet<>();
        public boolean accept(Identity id, long sequence) {
            if (id == null || id.nonce == null || id.token <= 0 || sequence < 0 || retired.contains(id)) return false;
            Long old = latest.get(id);
            if (old != null && sequence <= old) return false;
            if (old == null && latest.size() >= 64) {
                Identity oldest = latest.keySet().iterator().next(); latest.remove(oldest); retired.remove(oldest);
            }
            latest.put(id, sequence);
            return true;
        }
        public void retire(Identity id) { if (id != null && latest.containsKey(id)) retired.add(id); }
        public void clear() { latest.clear(); retired.clear(); }
    }
    private TacticalCoordinateUiState() {}
}

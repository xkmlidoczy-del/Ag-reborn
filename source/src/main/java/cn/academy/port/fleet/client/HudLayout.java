package cn.academy.port.fleet.client;

/** GUI-pixel layout, independent of Minecraft. No window-pixel or fixed 1080p assumptions. */
public final class HudLayout {
    private HudLayout() {}

    public record Placement(int x, int y, int width, int height, boolean compact, boolean visible) {}

    /**
     * Keep a clear 82-pixel band over the centred hotbar, health and mount indicators.
     * At small GUI sizes move the panel above this band instead of shrinking the font.
     */
    public static Placement place(int screenWidth, int screenHeight) {
        if (screenWidth < 300 || screenHeight < 160) {
            return new Placement(0, 0, 0, 0, true, false);
        }
        boolean compact = screenWidth < 480 || screenHeight < 240;
        int width = compact ? 142 : 164;
        int height = compact ? 68 : 82;
        int margin = Math.max(4, Math.min(10, screenWidth / 40));
        int x = screenWidth - margin - width;
        int y = screenHeight - margin - height;
        if (x < screenWidth / 2 + 100) {
            y = screenHeight - 82 - height;
        }
        y = Math.max(margin, Math.min(y, screenHeight - margin - height));
        return new Placement(x, y, width, height, compact, true);
    }

    /** Upper-right placement leaves the first-person weapon and hotbar unobstructed. */
    public static Placement placeTop(int screenWidth, int screenHeight) {
        Placement p = place(screenWidth, screenHeight);
        if (!p.visible()) return p;
        int margin = Math.max(4, Math.min(10, screenWidth / 40));
        return new Placement(p.x(), margin, p.width(), p.height(), p.compact(), true);
    }

    public static int ammoColor(int ammo, int capacity, boolean reloading) {
        if (reloading) return 0xFFFFBC75;
        if (ammo <= 0) return 0xFFFF806D;
        if (capacity > 0 && ammo <= Math.max(1, (int) Math.ceil(capacity * 0.2))) return 0xFFFFBC75;
        return 0xFFF2F8FA;
    }

    /** Fade only the crosshair; the optical sight remains part of the model. */
    public static int crosshairAlpha(float aim, boolean reloadActive) {
        if (reloadActive) return 0;
        if (!Float.isFinite(aim)) return 0;
        float t = Math.max(0, Math.min(1, (aim - 0.2f) / 0.6f));
        float smooth = t * t * (3 - 2 * t);
        return Math.round(210 * (1 - smooth));
    }
}

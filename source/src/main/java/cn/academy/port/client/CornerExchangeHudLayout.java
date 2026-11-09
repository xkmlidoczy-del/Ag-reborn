package cn.academy.port.client;

/** NEW333 guidance stays below the crosshair; crowded layouts never jump into the upper recovery panel. */
public final class CornerExchangeHudLayout {
    public static final int MAX_TEXT_WIDTH = 340, MIN_TEXT_WIDTH = 120, SIDE_MARGIN = 12,
            BOTTOM_RESERVE = 48, PREFERRED_GAP = 28, MINIMUM_GAP = 16;
    public record Layout(int textWidth, int top, int height, int lineStep, float scale) {}
    private CornerExchangeHudLayout() {}
    public static int textWidth(int viewportWidth) {
        return viewportWidth < MIN_TEXT_WIDTH + SIDE_MARGIN * 2 ? 0
                : Math.min(MAX_TEXT_WIDTH, viewportWidth - SIDE_MARGIN * 2);
    }
    /** Returns null when even readable compact text would touch the crosshair or hotbar reserve. */
    public static Layout fit(int viewportWidth, int viewportHeight, int rows, int fontHeight) {
        int width = textWidth(viewportWidth);
        if (width == 0 || viewportHeight <= BOTTOM_RESERVE || rows <= 0 || rows > 64 || fontHeight <= 0 || fontHeight > 64) return null;
        int step = fontHeight + 2, rawHeight = rows * step;
        int minimumTop = viewportHeight / 2 + MINIMUM_GAP, bottom = viewportHeight - BOTTOM_RESERVE;
        for (float scale : new float[]{1f, .9f, .8f}) {
            int height = (int)Math.ceil(rawHeight * (double)scale);
            int top = Math.min(viewportHeight / 2 + PREFERRED_GAP, bottom - height);
            if (top >= minimumTop) return new Layout(width, top, height, step, scale);
        }
        return null;
    }
}

package cn.aca.domain67.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Delegates HUD layer rendering to the v1.1.0 3D Dynamic Camera-Tracking Spatial UI (`Domain67SpatialUi`).
 */
public final class Domain67Hud {
    private Domain67Hud() {}

    public static void render(GuiGraphics gg, DeltaTracker delta) {
        Domain67SpatialUi.renderOverlay(gg, delta);
    }
}

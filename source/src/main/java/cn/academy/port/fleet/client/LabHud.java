package cn.academy.port.fleet.client;

import cn.academy.port.fleet.InteractionLab;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Original ACA HUD. Asset proportions and ADS behaviour reference TACZ; no TACZ textures are used. */
public final class LabHud {
    private static final float PANEL_SCALE = .68f;
    private static final int PANEL = 0xDC101C25;
    private static final int INK = 0xFFF2F8FA;
    private static final int MUTED = 0xFF92ADBA;
    private static final int ACCENT = 0xFF58D5E1;
    private static final int TRACK = 0xFF2B424E;
    private static final Map<ResourceLocation, Boolean> ICONS = new HashMap<>();
    private static final java.util.Set<Integer> SEEN_GUNS=new java.util.HashSet<>(),SEEN_CARS=new java.util.HashSet<>();
    private static long gunHintUntil,driveHintUntil;

    public record GunState(int variant, int ammo, int reserve, int capacity, boolean heavy,
                           boolean reloading, float progress, String reloadKey, String aimKey) {}

    public record DriveState(float signedSpeed, float steering, String forwardKey, String backKey,
                             String leftKey, String rightKey, String exitKey) {}

    private LabHud() {}

    public static void clearResourceCache() {
        ICONS.clear();
    }

    public static void weapon(GuiGraphics g, Minecraft mc, GunState state, float aim, float recoil) {
        if(SEEN_GUNS.add(state.variant()))gunHintUntil=mc.level.getGameTime()+140;
        HudLayout.Placement p = HudLayout.placeTop((int)(g.guiWidth() / PANEL_SCALE), (int)(g.guiHeight() / PANEL_SCALE));
        if (p.visible()) {
            g.pose().pushPose();
            g.pose().scale(PANEL_SCALE, PANEL_SCALE, 1);
            // Leave the original ability CP, overload and exact HP/shield panels clear.
            g.pose().translate(p.x(), Math.max(6,g.guiHeight()/PANEL_SCALE-p.height()-36), 0);
            plate(g, p.width(), p.height());

            String id = InteractionLab.GUN_IDS[state.variant()];
            String title = "G" + number(state.variant() + 1, 2) + " / "
                    + text("item.academy." + id);
            label(g, mc, title, 10, 6, p.width() - 20, MUTED);

            int iconWidth = p.compact() ? 63 : 78;
            int iconY = 23;
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("academy", "textures/gui/gun/" + id + ".png");
            boolean exists = ICONS.computeIfAbsent(texture, key -> mc.getResourceManager().getResource(key).isPresent());
            if (exists) {
                // This samples the entire 3:1 texture; any of 180x60, 192x64, or 384x128 works.
                g.pose().pushPose();
                g.pose().translate(8, iconY, 0);
                g.pose().scale(iconWidth / 384f, (iconWidth / 3f) / 128f, 1);
                g.blit(texture, 0, 0, 0f, 0f, 384, 128, 384, 128);
                g.pose().popPose();
            } else {
                // Missing optional side-view art must never render the missing-texture checkerboard.
                label(g, mc, text(state.heavy() ? "hud.aclab.heavy_cell" : "hud.aclab.energy_cell"),
                        10, 29, iconWidth - 4, MUTED);
            }

            int numberX = p.compact() ? 82 : 101;
            int ammoColor = HudLayout.ammoColor(state.ammo(), state.capacity(), state.reloading());
            g.pose().pushPose();
            g.pose().translate(numberX, 19, 0);
            g.pose().scale(1.8f, 1.8f, 1);
            g.drawString(mc.font, number(state.ammo(), 2), 0, 0, ammoColor, false);
            g.pose().popPose();
            label(g, mc, state.variant()>=0 ? "备用匣 "+cn.academy.port.fleet.CoilReloads.magazines(mc.player) : "/ " + number(state.reserve(), 3), numberX, 39, p.width() - numberX - 8, MUTED);

            int barX = 10, barY = 51, barWidth = p.width() - 20;
            g.fill(barX, barY, barX + barWidth, barY + 2, TRACK);
            float fraction = state.reloading() ? unit(state.progress())
                    : (state.capacity() > 0 ? unit(state.ammo() / (float) state.capacity()) : 0);
            int barColor = state.reloading() ? 0xFFFFBC75 : state.ammo() <= 0 ? 0xFFFF806D : ACCENT;
            g.fill(barX, barY, barX + Math.round(barWidth * fraction), barY + 2, barColor);

            String status;
            if (state.reloading() && state.variant()>=0) {
                int stage=cn.academy.port.fleet.GunItem.data(mc.player.getMainHandItem()).getInt("c03_reload_stage");
                status=stage<=1?"抓匣 / 解锁":stage==2?"交换能源匣":stage==3?"锁定 / 激活":"复位";
            } else if (state.reloading()) status = text("hud.aclab.reloading");
            else if (state.ammo() <= 0) status = text("hud.aclab.empty");
            else if (state.ammo() <= Math.max(1, (int) Math.ceil(state.capacity() * 0.2))) status = text("hud.aclab.low_cell");
            else status = text("hud.aclab.ready");
            label(g, mc, status, 10, 57,
                    p.compact() || state.reloading() ? p.width() - 56 : p.width() - 20, ammoColor);
            if (p.compact()) {
                // Keep a live reload binding even when the two full help labels will not fit.
                String key = mc.font.plainSubstrByWidth(state.reloadKey(), 30);
                int keyX = p.width() - 10 - mc.font.width(key);
                g.drawString(mc.font, key, keyX, 57, MUTED, false);
            } else {
                if (state.reloading()) {
                    String progressText = Math.min(99, Math.round(unit(state.progress()) * 100)) + "%";
                    g.drawString(mc.font, progressText, p.width() - 10 - mc.font.width(progressText), 57,
                            0xFFFFBC75, false);
                }
                String hint = text("hud.aclab.reload_hint", state.reloadKey()) + "  ·  "
                        + text("hud.aclab.aim_hint", state.aimKey());
                if(mc.level.getGameTime()<gunHintUntil)label(g, mc, hint, 10, 70, p.width() - 20, MUTED);
            }
            g.pose().popPose();
        }
        crosshair(g, mc, aim, recoil, state.reloading());
    }

    public static void vehicle(GuiGraphics g, Minecraft mc, DriveState state) {
        if(mc.player.getVehicle() instanceof cn.academy.port.fleet.LabVehicle car&&SEEN_CARS.add(car.variant()))driveHintUntil=mc.level.getGameTime()+140;
        HudLayout.Placement p = HudLayout.placeTop((int)(g.guiWidth() / PANEL_SCALE), (int)(g.guiHeight() / PANEL_SCALE));
        if (!p.visible()) return;
        g.pose().pushPose();
        g.pose().scale(PANEL_SCALE, PANEL_SCALE, 1);
        g.pose().translate(p.x(), Math.max(6,g.guiHeight()/PANEL_SCALE-p.height()-36), 0);
        plate(g, p.width(), p.height());
        label(g, mc, text("hud.aclab.drive"), 10, 6, p.width() - 20, MUTED);

        float speed = Float.isFinite(state.signedSpeed()) ? state.signedSpeed() : 0;
        int kmh = Math.max(0, Math.round(Math.abs(speed) * 72));
        g.pose().pushPose();
        g.pose().translate(10, 21, 0);
        g.pose().scale(1.8f, 1.8f, 1);
        g.drawString(mc.font, number(kmh, 3), 0, 0, INK, false);
        g.pose().popPose();
        label(g, mc, "km/h", 55, 30, 35, MUTED);

        String direction = speed < -0.005f ? "R" : speed > 0.005f ? "D" : "N";
        g.pose().pushPose();
        g.pose().translate(p.width() - 26, 21, 0);
        g.pose().scale(1.8f, 1.8f, 1);
        g.drawString(mc.font, direction, 0, 0, ACCENT, false);
        g.pose().popPose();

        float steer = Float.isFinite(state.steering()) ? Math.max(-1, Math.min(1, state.steering())) : 0;
        int trackX = 10, trackY = 51, trackWidth = p.width() - 20;
        int mid = trackX + trackWidth / 2;
        // Server input +1 is left (see LabClient), so positive steering moves the mark left.
        int dot = mid - Math.round(steer * (trackWidth / 2f - 2));
        g.fill(trackX, trackY, trackX + trackWidth, trackY + 2, TRACK);
        g.fill(Math.min(mid, dot), trackY, Math.max(mid, dot) + 1, trackY + 2, ACCENT);
        g.fill(mid, trackY - 2, mid + 1, trackY + 4, MUTED);
        g.fill(dot - 1, trackY - 1, dot + 2, trackY + 3, INK);
        label(g, mc, text("hud.aclab.steering"), 10, 57, p.width() - 20, MUTED);

        if (!p.compact()) {
            String hint = state.forwardKey() + "/" + state.backKey() + "  ·  "
                    + state.leftKey() + "/" + state.rightKey() + "  ·  "
                    + text("hud.aclab.exit_hint", state.exitKey());
            if(mc.level.getGameTime()<driveHintUntil)label(g, mc, hint, 10, 70, p.width() - 20, MUTED);
        }
        g.pose().popPose();
    }

    private static void crosshair(GuiGraphics g, Minecraft mc, float aim, float recoil, boolean reloadActive) {
        if (mc.options.hideGui || mc.screen != null) return;
        int alpha = HudLayout.crosshairAlpha(aim, reloadActive);
        if (alpha <= 0) return;
        int color = (alpha << 24) | 0xC0EEF2;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        float safeRecoil = Float.isFinite(recoil) ? Math.max(0, recoil) : 0;
        int spread = 5 + Math.round(safeRecoil * 5);
        g.fill(cx - spread - 3, cy, cx - spread, cy + 1, color);
        g.fill(cx + spread + 1, cy, cx + spread + 4, cy + 1, color);
        g.fill(cx, cy - spread - 3, cx + 1, cy - spread, color);
        g.fill(cx, cy + spread + 1, cx + 1, cy + spread + 4, color);
    }

    private static void plate(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, PANEL);
        g.fill(0, 0, 2, height, ACCENT);
        g.fill(2, 0, width, 1, 0x70415D6A);
        g.fill(width - 1, 1, width, height, 0x40334C5B);
    }

    private static void label(GuiGraphics g, Minecraft mc, String s, int x, int y, int width, int color) {
        if (width <= 0) return;
        g.drawString(mc.font, mc.font.plainSubstrByWidth(s, width), x, y, color, false);
    }

    private static String text(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    private static float unit(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }

    private static String number(int value, int minimumDigits) {
        String s = Integer.toString(Math.max(0, value));
        return "0".repeat(Math.max(0, minimumDigits - s.length())) + s;
    }
}

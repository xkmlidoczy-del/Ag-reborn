package cn.aca.infinity.client;

import cn.aca.infinity.GauntletData;
import cn.aca.infinity.SanctuaryShipEntity;
import cn.aca.infinity.Stone;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * U1 Locked Style: Destiny 2 / Warframe Reticle Hairline Micro-Arcs + 12% Idle Breathing Stealth:
 * - Zero corner disc, zero boxes, zero raw "J"/"K" pixel letters
 * - Two ultra-thin (1.2px) translucent starlight micro-arcs 20-23px bottom-right of crosshair for [J] / [K]
 * - Crisp 3px faceted active-stone star-pip + 5 micro socket dots
 * - Delicate champagne-gold 6-point micro-crest 17px below crosshair when 6-Stone Snap [N] is ready
 * - Auto-fades to 12% breathing opacity after 2.5s (50t) out of combat for 100% unobstructed exploration
 */
public final class InfinityHud {
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    private static long lastActiveTick = -200L;
    private static int lastStoneOrdinal = -1;
    private static float currentAlpha = 0.85f;

    private InfinityHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        if (mc.player.getVehicle() instanceof SanctuaryShipEntity ship) {
            ShipClientRender.renderBridgeHud(g, delta, mc, ship);
            return;
        }

        var self = ClientCache.self();
        if (self == null || !self.snapshot().equipped() || InfinityWheelScreen.isOpen()) return;

        GauntletData data = self.data();
        Stone active = data.selected();
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        long est = self.estimatedServerNow(now);
        float pt = delta.getGameTimeDeltaPartialTick(false);
        float time = now + pt;

        long pRem = Math.max(0L, self.snapshot().primaryReady()[active.ordinal()] - est);
        long sRem = Math.max(0L, self.snapshot().secondaryReady()[active.ordinal()] - est);
        long snapRem = Math.max(0L, self.snapshot().snapReadyAt() - est);

        boolean activeCombat = pRem > 0L || sRem > 0L
                || (est - self.snapshot().castAnimAt()) < 45L
                || (est - self.snapshot().snapAnimAt()) < 45L
                || mc.player.hurtTime > 0
                || mc.player.swinging
                || InfinityClient.bufferedAction() >= 0;
        if (active.ordinal() != lastStoneOrdinal) {
            lastStoneOrdinal = active.ordinal();
            lastActiveTick = now;
        }
        if (activeCombat) {
            lastActiveTick = now;
        }

        float idleBreath = 0.11f + 0.03f * Mth.sin(time * 0.08f);
        float targetAlpha = (now - lastActiveTick > 50L) ? idleBreath : 0.90f;
        currentAlpha = Mth.lerp(0.12f, currentAlpha, targetAlpha);

        float cx = g.guiWidth() * 0.5f;
        float cy = g.guiHeight() * 0.5f;
        float a = currentAlpha;

        boolean hasActive = data.has(active);
        int stoneCol = hasActive ? active.color : 0xff5a687e;
        int stoneBright = hasActive ? active.coreBright : 0xff94a4bc;

        // Bottom-Right Quadrant Hairline Micro-Arcs around Crosshair (16° .. 74°, 20px-23.5px radius)
        float arcStart = 16.0f * DEG_TO_RAD;
        float arcSweep = 58.0f * DEG_TO_RAD;

        float pRatio = 1f - Mth.clamp(pRem / (float) active.primaryCooldown, 0f, 1f);
        float sRatio = 1f - Mth.clamp(sRem / (float) active.secondaryCooldown, 0f, 1f);

        // Inner 1.25px micro-arc: [J] Primary Skill
        StarDialDraw.ring(g, cx, cy, 19.4f, 20.65f, arcStart, arcStart + arcSweep,
                StarDialDraw.withAlpha(0xff0b1222, 0.34f * a));
        if (pRatio > 0.01f) {
            StarDialDraw.ring(g, cx, cy, 19.4f, 20.65f, arcStart, arcStart + arcSweep * pRatio,
                    StarDialDraw.withAlpha(pRatio >= 0.99f ? stoneBright : stoneCol, 0.92f * a));
        }

        // Outer 1.15px micro-arc: [K] Secondary Skill
        StarDialDraw.ring(g, cx, cy, 22.1f, 23.25f, arcStart, arcStart + arcSweep,
                StarDialDraw.withAlpha(0xff0b1222, 0.30f * a));
        if (sRatio > 0.01f) {
            StarDialDraw.ring(g, cx, cy, 22.1f, 23.25f, arcStart, arcStart + arcSweep * sRatio,
                    StarDialDraw.withAlpha(sRatio >= 0.99f ? stoneBright : stoneCol, 0.86f * a));
        }

        // Active Stone 3.0px Star-Pip at 45° bottom-right of crosshair (27px from center)
        float pipAng = 45.0f * DEG_TO_RAD;
        float pipX = cx + Mth.cos(pipAng) * 26.8f;
        float pipY = cy + Mth.sin(pipAng) * 26.8f;
        StarDialDraw.diamond(g, pipX, pipY, 3.1f, 3.1f,
                StarDialDraw.withAlpha(stoneCol, 0.88f * a),
                StarDialDraw.withAlpha(stoneBright, 0.95f * a));
        StarDialDraw.diamond(g, pipX, pipY, 1.35f, 1.35f,
                StarDialDraw.withAlpha(0xffffffff, 0.92f * a), 0);

        // 6 Micro Socket Constellation Pips along r=26.8f (14°..76°)
        Stone[] stones = Stone.values();
        for (int i = 0; i < stones.length; i++) {
            if (i == active.ordinal()) continue;
            Stone st = stones[i];
            boolean has = data.has(st);
            float dotAng = (14.0f + i * 12.4f) * DEG_TO_RAD;
            float dx = cx + Mth.cos(dotAng) * 26.8f;
            float dy = cy + Mth.sin(dotAng) * 26.8f;
            StarDialDraw.diamond(g, dx, dy, has ? 1.25f : 0.85f, has ? 1.25f : 0.85f,
                    StarDialDraw.withAlpha(has ? st.coreBright : 0xff465266, (has ? 0.78f : 0.28f) * a), 0);
        }

        // Subtle Champagne-Gold Micro-Crest 17.5px directly below crosshair when 6-Stone Snap [N] is ready
        if (data.hasAllStones()) {
            boolean ready = snapRem <= 0L;
            float pulse = ready ? (0.68f + 0.26f * Mth.sin(time * 0.15f)) : 0.22f;
            float sy = cy + 17.5f;
            int snapCol = StarDialDraw.withAlpha(ready ? 0xffffe288 : 0xff8c7644, pulse * a);
            StarDialDraw.diamond(g, cx, sy, 2.1f, 2.1f, snapCol, 0);
            StarDialDraw.line(g, cx - 6.5f, sy, cx - 2.8f, sy, 0.9f, snapCol);
            StarDialDraw.line(g, cx + 2.8f, sy, cx + 6.5f, sy, 0.9f, snapCol);
        }

        g.flush();
    }
}

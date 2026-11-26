package cn.aca.domain67.client;

import cn.aca.domain67.CircusMegaphoneItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * ACA-Domain67 v1.2.0 Haute-Couture Smoked-Mocha & Satin Champagne-Gold Minimalist Spatial UI:
 * - ZERO center-screen banners or opaque slabs — 100% unobstructed combat & recording sightlines!
 * - Frosted Smoked-Espresso Translucent Glass (`~52%` alpha) + `1px` Satin Champagne-Gold Hairline (`#E6C275`)
 *   for effortless contrast against the bright Warm Ivory-Gold Restaurant interior!
 * - 100% localized via `Component.translatable(...)` (pure Simplified Chinese in `zh_cn`, English in `en_us`).
 * - Subtle `+-2.6 deg` 3D camera-tracking parallax + miniature 3D rotating `"67"` gold crest!
 */
public final class Domain67SpatialUi {
    // Smoked Espresso-Mocha Translucent Glass & Satin Champagne-Gold Palette
    private static final int GLASS_BG_MAIN = 0x8615110D;
    private static final int GLASS_BG_SUBTLE = 0x6E18130F;
    private static final int GLASS_KEY_BG = 0xAA241C14;
    private static final int BORDER_GOLD_HAIRLINE = 0xB8E6C275;
    private static final int BORDER_GOLD_MUTED = 0x559E885C;
    private static final int CORNER_GOLD_SPARK = 0xEEFFF0C2;

    private static final int COL_IVORY_TEXT = 0xFFFFF8EC;
    private static final int COL_SAND_SUBTEXT = 0xFFC8B696;
    private static final int COL_GOLD_ACCENT = 0xFFF2C96B;
    private static final int COL_JADE_READY = 0xFF82D6A0;
    private static final int COL_CORAL_ALERT = 0xFFE8644E;
    private static final int COL_MUTED_LOCKED = 0xFF7D7468;

    private Domain67SpatialUi() {}

    /**
     * Renders a tiny, sleek translucent progress micro-pill ONLY above the single diner closest to the player's crosshair!
     * Eliminates multi-mob billboard clutter completely while preserving instant tactical feedback.
     */
    public static void renderTargetedDinerMicroPill(Minecraft mc, PoseStack pose, MultiBufferSource.BufferSource buf,
                                                    Vec3 cam, Domain67ClientCache.ClientDomainView dom, float partial) {
        if (mc.player == null || dom.diners.isEmpty()) return;
        Vec3 eye = mc.player.getEyePosition(partial);
        Vec3 look = mc.player.getViewVector(partial);

        cn.aca.domain67.Domain67Network.DinerSnapshot best = null;
        double bestCos = 0.945; // Within ~19 deg cone around crosshair
        for (var d : dom.diners) {
            Vec3 toDiner = new Vec3(d.x(), d.y() + 1.1, d.z()).subtract(eye);
            double dist = toDiner.length();
            if (dist < 0.5 || dist > 36.0) continue;
            double cos = toDiner.scale(1.0 / dist).dot(look);
            if (cos > bestCos) {
                bestCos = cos;
                best = d;
            }
        }
        if (best == null) return;

        pose.pushPose();
        pose.translate(best.x() - cam.x, best.y() - cam.y + 2.32, best.z() - cam.z);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.016f, -0.016f, 0.016f);

        String txt = best.finished()
                ? Component.translatable("ui.aca_domain67.diner.done").getString()
                : Component.translatable("ui.aca_domain67.diner.eating", (int) (best.progress() * 100f)).getString();
        float halfW = mc.font.width(txt) / 2.0f;
        int col = best.finished() ? COL_JADE_READY : COL_IVORY_TEXT;
        mc.font.drawInBatch(txt, -halfW, 0f, col, false, pose.last().pose(), buf,
                Font.DisplayMode.NORMAL, 0x7A15110D, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /**
     * Main Minimalist 3D Camera-Following HUD Entrypoint.
     */
    public static void renderOverlay(GuiGraphics gg, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        float partial = delta.getGameTimeDeltaPartialTick(false);
        long now = Domain67ClientCache.ticks();
        float time = now + partial;
        int sw = gg.guiWidth();
        int sh = gg.guiHeight();

        if (Domain67PostShader.isIrisShaderActive()) {
            float imp = Domain67PostShader.currentImpactFlashForGui(partial);
            if (imp > 0.05f) {
                int a = (int) (Mth.clamp(imp, 0f, 1f) * 140);
                gg.fill(0, 0, sw, sh, (a << 24) | 0xFFF6DE);
            }
        }

        float lagYaw = Domain67ClientCache.uiLagYaw(partial);
        float lagPitch = Domain67ClientCache.uiLagPitch(partial);

        var dom = Domain67ClientCache.primaryDomain(mc.player.position());
        boolean inDomain = dom != null && !dom.isCollapsing();

        // 1. Ultra-Slim Top-Edge Translucent Status Capsule (height = 15px at y = 5 — zero upper FOV obstruction!)
        if (dom != null) {
            renderSlimTopCapsule(gg, mc, sw, dom, now, partial, time, lagYaw, lagPitch);
            if (!dom.isCollapsing()) {
                renderReticleMicroHairline(gg, sw, sh, dom);
            }
        }

        // 2. Single-Slot Top-Left Peripheral Micro-Toast (never blocks center screen, never stacks, never spams!)
        var toast = Domain67ClientCache.activeToast();
        if (toast != null) {
            renderPeripheralMicroToast(gg, mc, toast, now, partial, time, lagYaw, lagPitch);
        }

        // 3. Compact Bottom-Right Smoked-Mocha Translucent 4-Slot Micro-Deck
        if (inDomain || CircusMegaphoneItem.hasMegaphone(mc.player)) {
            renderCompactSkillDeck(gg, mc, sw, sh, inDomain, lagYaw, lagPitch);
        }
    }

    private static void renderSlimTopCapsule(GuiGraphics gg, Minecraft mc, int sw,
                                             Domain67ClientCache.ClientDomainView dom,
                                             long now, float partial, float time,
                                             float lagYaw, float lagPitch) {
        boolean collapsing = dom.isCollapsing();
        float remSec = dom.serverTicksRemaining / 20.0f;
        float frac = collapsing
                ? (1.0f - dom.collapseProgress(now, partial))
                : Mth.clamp(dom.serverTicksRemaining / (float) Math.max(1, dom.serverTotalDuration), 0f, 1f);
        boolean urgent = !collapsing && remSec <= 3.5f;

        int activeDiners = 0;
        for (var d : dom.diners) {
            if (!d.finished()) activeDiners++;
        }

        String label;
        if (collapsing) {
            label = Component.translatable("ui.aca_domain67.top_collapsing",
                    String.format("%.0f", (1.0f - frac) * 100f)).getString();
        } else if (urgent) {
            label = Component.translatable("ui.aca_domain67.top_urgent",
                    String.format("%.1f", remSec), activeDiners).getString();
        } else {
            label = Component.translatable("ui.aca_domain67.top_active",
                    String.format("%.1f", remSec), activeDiners, dom.diners.size()).getString();
        }

        int textW = mc.font.width(label);
        int w = Math.max(148, textW + 34);
        int h = 16;

        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(sw * 0.5f - lagYaw * 0.55f, 6.0f + h * 0.5f - lagPitch * 0.35f, 60.0f);
        pose.mulPose(Axis.YP.rotationDegrees(lagYaw * 0.32f));
        pose.mulPose(Axis.XP.rotationDegrees(-lagPitch * 0.25f));

        int x0 = -w / 2;
        int y0 = -h / 2;
        int border = urgent ? 0xCCE8644E : BORDER_GOLD_HAIRLINE;
        drawFrostedGlassPanel(gg, x0, y0, w, h, GLASS_BG_MAIN, border);

        // Ultra-fine 2px progress filament along bottom inside edge
        int barW = w - 8;
        int fillW = Math.max(1, (int) (barW * frac));
        gg.fill(x0 + 4, y0 + h - 3, x0 + 4 + barW, y0 + h - 1, 0x44000000);
        gg.fill(x0 + 4, y0 + h - 3, x0 + 4 + fillW, y0 + h - 1, urgent ? COL_CORAL_ALERT : COL_GOLD_ACCENT);

        // Miniature rotating 3D Sculpted "67" Gold Crest on left of capsule
        VertexConsumer solid = gg.bufferSource().getBuffer(Domain67CoreShader.solidArch());
        pose.pushPose();
        pose.translate(x0 + 12.0f, -0.5f, 8.0f);
        pose.scale(9.5f, -9.5f, 9.5f);
        pose.mulPose(Axis.YP.rotationDegrees(time * 2.4f));
        Domain67ItemRenderer.drawSculpted67Pair(pose, solid, 0.72f, 0f, 0f);
        pose.popPose();
        gg.bufferSource().endBatch(Domain67CoreShader.solidArch());

        pose.pushPose();
        pose.translate(x0 + 24.0f, y0 + 3.0f, 4.0f);
        pose.scale(0.86f, 0.86f, 1.0f);
        gg.drawString(mc.font, label, 0, 0, urgent ? COL_CORAL_ALERT : COL_IVORY_TEXT, false);
        pose.popPose();

        pose.popPose();
    }

    private static void renderPeripheralMicroToast(GuiGraphics gg, Minecraft mc,
                                                   Domain67ClientCache.PeripheralToast toast,
                                                   long now, float partial, float time,
                                                   float lagYaw, float lagPitch) {
        float age = toast.age(now, partial);
        float dur = Math.max(12f, toast.durationTicks());
        if (age < 0f || age >= dur) return;

        float slideIn = 1.0f - (float) Math.pow(1.0f - Mth.clamp(age / 5.5f, 0f, 1f), 3.0);
        float fadeOut = age > (dur - 7.0f) ? Mth.clamp((dur - age) / 7.0f, 0f, 1f) : 1.0f;
        float alpha = slideIn * fadeOut;
        if (alpha <= 0.05f) return;

        String title = Component.translatable(toast.titleKey()).getString();
        String sub = Component.translatable(toast.subKey(), toast.countArg()).getString();

        int contentW = Math.max((int) (mc.font.width(title) * 0.86f), (int) (mc.font.width(sub) * 0.74f));
        int w = Mth.clamp(contentW + 34, 136, 198);
        int h = 24;

        // Docked cleanly in TOP-LEFT peripheral margin (x = 10, y = 10) — completely out of center sightline!
        float tx = 10.0f - (1.0f - slideIn) * 24.0f - lagYaw * 0.45f;
        float ty = 10.0f - lagPitch * 0.35f;

        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(tx + w * 0.5f, ty + h * 0.5f, 80.0f);
        pose.mulPose(Axis.YP.rotationDegrees(4.0f + lagYaw * 0.35f));
        pose.mulPose(Axis.XP.rotationDegrees(-lagPitch * 0.25f));

        int x0 = -w / 2;
        int y0 = -h / 2;
        int bgA = (int) (0x88 * alpha);
        int bdA = (int) (0xB8 * alpha);
        int txtA = (int) (0xFF * alpha) << 24;
        int accentRgb = toast.accentRgb() & 0xFFFFFF;

        drawFrostedGlassPanel(gg, x0, y0, w, h, (bgA << 24) | 0x15110D, (bdA << 24) | accentRgb);

        // Left vertical 2px champagne-gold accent bar + mini badge
        gg.fill(x0 + 3, y0 + 4, x0 + 5, y0 + h - 4, txtA | accentRgb);

        if ("67".equals(toast.badge()) && alpha > 0.45f) {
            VertexConsumer solid = gg.bufferSource().getBuffer(Domain67CoreShader.solidArch());
            pose.pushPose();
            pose.translate(x0 + 14.5f, 0.0f, 8.0f);
            pose.scale(10.0f, -10.0f, 10.0f);
            pose.mulPose(Axis.YP.rotationDegrees(time * 2.8f));
            Domain67ItemRenderer.drawSculpted67Pair(pose, solid, 0.70f, 0f, 0f);
            pose.popPose();
            gg.bufferSource().endBatch(Domain67CoreShader.solidArch());
        } else {
            pose.pushPose();
            pose.translate(x0 + 10.0f, y0 + 7.5f, 4.0f);
            pose.scale(0.82f, 0.82f, 1.0f);
            gg.drawString(mc.font, toast.badge(), 0, 0, txtA | accentRgb, false);
            pose.popPose();
        }

        // Crisp compact Simplified Chinese title & subline
        pose.pushPose();
        pose.translate(x0 + 25.0f, y0 + 4.0f, 4.0f);
        pose.scale(0.84f, 0.84f, 1.0f);
        gg.drawString(mc.font, title, 0, 0, txtA | (COL_IVORY_TEXT & 0xFFFFFF), false);
        pose.popPose();

        pose.pushPose();
        pose.translate(x0 + 25.0f, y0 + 13.5f, 4.0f);
        pose.scale(0.72f, 0.72f, 1.0f);
        gg.drawString(mc.font, sub, 0, 0, txtA | (COL_SAND_SUBTEXT & 0xFFFFFF), false);
        pose.popPose();

        pose.popPose();
    }

    private static void renderCompactSkillDeck(GuiGraphics gg, Minecraft mc, int sw, int sh,
                                               boolean inDomain, float lagYaw, float lagPitch) {
        String[][] slots = {
                {Domain67Client.keyLabel(Domain67Client.KEY_DOMAIN_EXPAND), Component.translatable(inDomain ? "ui.aca_domain67.slot.close" : "ui.aca_domain67.slot.expand").getString(), inDomain ? "1" : "0"},
                {Domain67Client.keyLabel(Domain67Client.KEY_CIRCUS_BROADCAST), Component.translatable("ui.aca_domain67.slot.broadcast").getString(), "0"},
                {Domain67Client.keyLabel(Domain67Client.KEY_LOG_FINISHER), Component.translatable("ui.aca_domain67.slot.log").getString(), inDomain ? "2" : "3"},
                {Domain67Client.keyLabel(Domain67Client.KEY_BENTO_CAMEO), Component.translatable("ui.aca_domain67.slot.bento").getString(), inDomain ? "2" : "3"}
        };

        int cardW = 48;
        int cardH = 19;
        int gap = 3;
        int totalW = slots.length * cardW + (slots.length - 1) * gap;

        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(sw - 8.0f - totalW * 0.5f - lagYaw * 0.45f,
                sh - 8.0f - cardH * 0.5f - lagPitch * 0.30f, 60.0f);
        pose.mulPose(Axis.YP.rotationDegrees(-4.5f + lagYaw * 0.30f));
        pose.mulPose(Axis.XP.rotationDegrees(-2.5f - lagPitch * 0.22f));

        int startX = -totalW / 2;
        int y0 = -cardH / 2;

        for (int i = 0; i < slots.length; i++) {
            int cx = startX + i * (cardW + gap);
            String state = slots[i][2];
            boolean locked = "3".equals(state);
            boolean active = "1".equals(state) || "2".equals(state);

            int bg = locked ? GLASS_BG_SUBTLE : GLASS_BG_MAIN;
            int border = locked ? BORDER_GOLD_MUTED : (active ? 0xD8F2C96B : BORDER_GOLD_HAIRLINE);
            drawFrostedGlassPanel(gg, cx, y0, cardW, cardH, bg, border);

            // Keycap pill
            gg.fill(cx + 3, y0 + 3, cx + 13, y0 + 11, locked ? 0x55221D18 : GLASS_KEY_BG);
            pose.pushPose();
            pose.translate(cx + 8.0f, y0 + 4.0f, 2.0f);
            float keyScale = Math.min(0.75f, 9.0f / Math.max(1, mc.font.width(slots[i][0])));
            pose.scale(keyScale, keyScale, 1.0f);
            float kw = -mc.font.width(slots[i][0]) / 2.0f;
            gg.drawString(mc.font, slots[i][0], (int) kw, 0, locked ? COL_MUTED_LOCKED : COL_GOLD_ACCENT, false);
            pose.popPose();

            // Skill title
            pose.pushPose();
            pose.translate(cx + 15.0f, y0 + 4.0f, 2.0f);
            pose.scale(0.72f, 0.72f, 1.0f);
            gg.drawString(mc.font, slots[i][1], 0, 0, locked ? COL_MUTED_LOCKED : COL_IVORY_TEXT, false);
            pose.popPose();

            // Minimalist status dot + short tag
            String stateKey = switch (state) {
                case "1" -> "ui.aca_domain67.state.active";
                case "2" -> "ui.aca_domain67.state.surehit";
                case "3" -> "ui.aca_domain67.state.locked";
                default -> "ui.aca_domain67.state.ready";
            };
            int dotCol = switch (state) {
                case "1" -> COL_GOLD_ACCENT;
                case "2" -> COL_CORAL_ALERT;
                case "3" -> COL_MUTED_LOCKED;
                default -> COL_JADE_READY;
            };
            gg.fill(cx + 4, y0 + 13, cx + 6, y0 + 15, dotCol);
            pose.pushPose();
            pose.translate(cx + 8.5f, y0 + 12.0f, 2.0f);
            pose.scale(0.64f, 0.64f, 1.0f);
            gg.drawString(mc.font, Component.translatable(stateKey).getString(), 0, 0,
                    locked ? COL_MUTED_LOCKED : COL_SAND_SUBTEXT, false);
            pose.popPose();
        }

        pose.popPose();
    }

    private static void renderReticleMicroHairline(GuiGraphics gg, int sw, int sh,
                                                   Domain67ClientCache.ClientDomainView dom) {
        float frac = Mth.clamp(dom.serverTicksRemaining / (float) Math.max(1, dom.serverTotalDuration), 0f, 1f);
        boolean urgent = dom.serverTicksRemaining <= 60;
        int rx = sw / 2 + 11;
        int ry = sh / 2 - 8;
        int barH = 16;
        int fillH = Math.max(1, (int) (barH * frac));

        gg.fill(rx, ry, rx + 1, ry + barH, 0x5518130E);
        gg.fill(rx, ry + (barH - fillH), rx + 1, ry + barH, urgent ? COL_CORAL_ALERT : COL_GOLD_ACCENT);
    }

    /**
     * Draws a refined Smoked-Mocha Frosted Translucent Glass Panel with 1px Satin Champagne-Gold Hairline
     * and 1px corner jewel highlights — high contrast, lightweight, and 45%+ transparent!
     */
    private static void drawFrostedGlassPanel(GuiGraphics gg, int x, int y, int w, int h, int bg, int border) {
        gg.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        // 1px satin hairline border (chamfered 1px corners)
        gg.fill(x + 1, y, x + w - 1, y + 1, border);
        gg.fill(x + 1, y + h - 1, x + w - 1, y + h, border);
        gg.fill(x, y + 1, x + 1, y + h - 1, border);
        gg.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        // Subtle top inner specular sheen
        if ((border >>> 24) > 0x60) {
            int sparkA = Math.min(255, (border >>> 24)) << 24;
            int spark = sparkA | (CORNER_GOLD_SPARK & 0xFFFFFF);
            gg.fill(x + 1, y + 1, x + 2, y + 2, spark);
            gg.fill(x + w - 2, y + 1, x + w - 1, y + 2, spark);
        }
    }
}

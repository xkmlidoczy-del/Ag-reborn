package cn.acamech.client;

import cn.acamech.MechEntity;
import cn.acamech.MechMod;
import cn.acamech.RangedWeapon;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

final class CockpitHud {
    private static final int GLASS_WIDTH = 376, GLASS_HEIGHT = 176;
    private static final int INK = 0xffecfaff, MUTED = 0xffa5c9d2, RULE = 0x62537782;
    private static final int AMBER = 0xffffca7a, ALERT = 0xffff9c86;
    private static final String[] CARDINALS = {"N", "E", "S", "W"};
    private static final ResourceLocation GLASS = MechMod.id("textures/gui/tek_glass.png");

    private CockpitHud() {}

    static void render(GuiGraphics g, DeltaTracker tick, Minecraft game, MechEntity mech,
                       KeyMapping aux, KeyMapping strike, KeyMapping fire, boolean hints) {
        int w = g.guiWidth(), h = g.guiHeight();
        boolean firstPerson = game.options.getCameraType().isFirstPerson();
        int accent = switch (mech.chassis()) {
            case ATLAS -> 0xff86cfff;
            case AETHER -> 0xff81f1ed;
            case SERAPH -> 0xff99ffd8;
        };
        boolean drained = mech.charge() <= 0;
        boolean lowPower = mech.charge() * 5 <= mech.chassis().energy;
        int power = lowPower ? AMBER : accent;
        int hull = mech.hull() * 4 <= mech.chassis().hull ? ALERT : 0xffffd894;
        int strikeAge = mech.strikeAge(), result = mech.strikeResult();
        int rangeAge = mech.rangedAge();
        boolean charging = rangeAge >= 0 && rangeAge < mech.chassis().ranged.windup;
        boolean rangedHit = mech.rangedHitMarker();
        boolean swinging = strikeAge >= 0;
        boolean hit = swinging && result > 0;
        int modeColor = swinging ? (hit ? 0xffb7ffe2 : result == 0 ? MUTED : AMBER) : drained ? ALERT : accent;
        String mode = drained ? "drained" : mech.auxiliary() ? mech.chassis().id : mech.running() ? "run" : mech.driveSpeed() < -.018f ? "reverse" : "drive";
        Component status = swinging ? Component.translatable("hud.acamech.attack_"
                + (hit ? "hit" : result == 0 ? "miss" : "windup"), mech.chassis().attack.displayName(), result)
                : Component.translatable("hud.acamech." + mode);

        if (firstPerson) {
            int reticleColor = rangedHit ? 0xffb7ffe2 : charging ? AMBER : modeColor;
            visor(g, w, h, reticleColor, !drained, swinging || charging);
            if (rangedHit || hit && strikeAge - mech.chassis().attack.contactTick < 8) {
                hitMarker(g, w / 2, h / 2, reticleColor);
            }
        }

        if (w < 160 || h < 101) return;
        int screenX = 8, screenY = 10, panel = 188, height = 116;
        float scale = Math.min(w < 360 || h < 265 ? .875f : 1f, Math.min((w - 16f) / panel, (h - 20f) / height));
        int x = 0, y = 0;
        g.flush();
        g.pose().pushPose();
        g.pose().translate(screenX, screenY, 0);
        g.pose().scale(scale, scale, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(GLASS, x, y, panel, height, 0f, 0f, GLASS_WIDTH, GLASS_HEIGHT, GLASS_WIDTH, GLASS_HEIGHT);
        RenderSystem.disableBlend();

        var font = game.font;
        int l = x + 9, r = x + panel - 9, mid = x + panel / 2;
        g.fill(l, y + 8, l + 2, y + 27, accent);
        g.drawString(font, Component.translatable("hud.acamech.instrument"), l + 6, y + 9, MUTED, false);
        g.drawString(font, mech.chassis().displayName(), l + 6, y + 21, INK, false);
        g.fill(l, y + 33, r, y + 34, RULE);

        String speed = digits(Math.round(Math.abs(mech.driveSpeed()) * 72), 2);
        int unit = font.width("km/h");
        g.drawString(font, "km/h", r - unit, y + 20, MUTED, false);
        g.pose().pushPose();
        g.pose().translate(r - unit - 4 - font.width(speed) * 1.8f, y + 12, 0);
        g.pose().scale(1.8f, 1.8f, 1);
        g.drawString(font, speed, 0, 0, accent, false);
        g.pose().popPose();

        gauge(g, font, l, mid - 5, y + 40, "hull_label", mech.hull(), mech.chassis().hull, hull);
        gauge(g, font, mid + 5, r, y + 40, "power_label", mech.charge(), mech.chassis().energy, power);
        g.fill(mid, y + 40, mid + 1, y + 60, RULE);

        g.fill(l, y + 67, r, y + 79, alpha(modeColor, 24));
        g.fill(l, y + 67, l + 2, y + 79, modeColor);
        g.drawString(font, status, l + 6, y + 69, modeColor, false);
        if (swinging) {
            int length = Mth.clamp(strikeAge * (r - l) / mech.chassis().attack.duration, 1, r - l);
            g.fill(l, y + 80, r, y + 81, alpha(modeColor, 34));
            g.fill(l, y + 80, l + length, y + 81, modeColor);
        }
        if (hints && !drained && !swinging) {
            var keys = mech.running() || Math.abs(mech.driveSpeed()) > .055f
                    ? Component.translatable("hud.acamech.run_key", game.options.keySprint.getTranslatedKeyMessage())
                    : Component.translatable("hud.acamech.keys", aux.getTranslatedKeyMessage(), strike.getTranslatedKeyMessage());
            if (font.width(keys) + font.width(status) < r - l - 15) textRight(g, font, keys, r - 3, y + 69, MUTED);
        }
        rangedPanel(g, font, game, mech, fire, l, r, y + 84, accent, charging);
        g.pose().popPose();

        if (firstPerson && w >= 320 && h >= 180) {
            float yaw = Mth.rotLerp(tick.getGameTimeDeltaPartialTick(false), mech.yRotO, mech.getYRot());
            int center = Math.max(w / 2, screenX + Math.round(panel * scale) + 68);
            heading(g, font, center, screenY + 1, Mth.positiveModulo(yaw + 180f, 360f), accent);
        }
    }

    private static void rangedPanel(GuiGraphics g, Font font, Minecraft game, MechEntity mech, KeyMapping fire,
                                    int l, int r, int y, int accent, boolean charging) {
        var weapon = mech.chassis().ranged;
        int age = mech.rangedAge(), cd = mech.rangedCooldown(), result = mech.rangedResult();
        int color = accent;
        float progress = 1;
        Component label = Component.translatable("hud.acamech.ranged_key", fire.getTranslatedKeyMessage(), weapon.displayName());
        Component state;
        if (charging) {
            progress = age / (float) weapon.windup;
            state = Component.translatable("hud.acamech.ranged_charge", Math.round(progress * 100));
            color = AMBER;
        } else if (cd > 0) {
            progress = 1 - cd / (float) weapon.cooldown;
            int tenths = (cd + 1) / 2;
            state = Component.translatable("hud.acamech.ranged_cooldown", (tenths / 10) + "." + (tenths % 10));
            if (result > 0) label = Component.translatable("hud.acamech.attack_hit", weapon.displayName(), result);
            else if (result == RangedWeapon.BLOCKED) label = Component.translatable("hud.acamech.ranged_blocked", weapon.displayName());
            else if (result == RangedWeapon.MISS) label = Component.translatable("hud.acamech.ranged_miss", weapon.displayName());
            else if (result == RangedWeapon.ABORTED) label = Component.translatable("hud.acamech.ranged_arc");
            else label = Component.translatable("hud.acamech.ranged_firing", weapon.displayName());
            color = result > 0 ? 0xffb7ffe2 : result == RangedWeapon.BLOCKED ? AMBER : MUTED;
        } else if (mech.charge() < weapon.charge) {
            state = Component.translatable("hud.acamech.ranged_low");
            progress = 0;
            color = ALERT;
        } else if (!mech.canAim(game.player)) {
            state = Component.translatable("hud.acamech.ranged_arc");
            color = MUTED;
        } else {
            state = Component.translatable("hud.acamech.ranged_ready");
        }
        g.fill(l, y, r, y + 1, RULE);
        int labelWidth = r - l - font.width(state) - 7;
        if (font.width(label) <= labelWidth) g.drawString(font, label, l, y + 6, color, false);
        else g.drawString(font, font.plainSubstrByWidth(label.getString(), Math.max(0, labelWidth - 6)) + "…",
                l, y + 6, color, false);
        textRight(g, font, state, r, y + 6, INK);
        for (int i = 0; i < 17; i++) {
            int start = l + i * (r - l) / 17, end = l + (i + 1) * (r - l) / 17 - 1;
            g.fill(start, y + 18, end, y + 21, 0xff243b45);
            int lit = Math.round(Mth.clamp(progress * 17 - i, 0, 1) * (end - start));
            if (lit > 0) g.fill(start, y + 18, start + lit, y + 21, alpha(color, 218));
        }
    }

    private static void gauge(GuiGraphics g, Font font, int l, int r, int y, String key, int value, int max, int color) {
        g.drawString(font, Component.translatable("hud.acamech." + key), l, y, MUTED, false);
        textRight(g, font, Component.literal(value + "/" + max), r, y, INK);
        float filled = Mth.clamp((float) value / max, 0, 1);
        int span = r - l;
        for (int i = 0; i < 10; i++) {
            int start = l + i * span / 10, end = l + (i + 1) * span / 10 - 1;
            g.fill(start, y + 12, end, y + 17, 0xff253942);
            int lit = Math.round(Mth.clamp(filled * 10 - i, 0, 1) * (end - start));
            if (lit == 0) continue;
            g.fill(start, y + 12, start + lit, y + 17, alpha(color, 172));
            g.fill(start, y + 12, start + lit, y + 13, color);
        }
    }

    private static void heading(GuiGraphics g, Font font, int center, int y, float degrees, int color) {
        g.fill(center - 59, y, center + 60, y + 29, 0xb312222b);
        g.fill(center - 59, y, center + 60, y + 1, alpha(color, 126));
        g.enableScissor(center - 57, y + 1, center + 58, y + 26);
        int first = Mth.floor((degrees - 44) / 5) * 5;
        for (int angle = first; angle <= degrees + 44; angle += 5) {
            int x = center + Math.round((angle - degrees) * 1.45f);
            boolean major = angle % 15 == 0;
            g.fill(x, y + (major ? 17 : 19), x + 1, y + 24, alpha(color, major ? 215 : 95));
            if (major) {
                int dial = Math.floorMod(angle, 360);
                String label = dial % 90 == 0 ? CARDINALS[dial / 90] : Integer.toString(dial);
                if (Math.abs(x - center) + (font.width(label) + 1) / 2 < 56)
                    g.drawCenteredString(font, label, x, y + 5, MUTED);
            }
        }
        g.disableScissor();
        g.fill(center - 2, y + 24, center + 3, y + 25, color);
        g.fill(center - 1, y + 25, center + 2, y + 26, color);
        g.fill(center, y + 26, center + 1, y + 28, color);
        g.fill(center - 15, y + 29, center + 16, y + 41, 0xdc14262e);
        g.drawCenteredString(font, digits(Math.round(degrees) % 360, 3) + "°", center, y + 31, INK);
    }

    private static void visor(GuiGraphics g, int w, int h, int color, boolean powered, boolean swinging) {
        int x = w / 2, y = h / 2;
        int rail = alpha(color, 125), shadow = 0xa913252e;
        int edgeHeight = Math.min(35, h / 8), edgeWidth = Math.min(45, w / 12);
        g.fillGradient(0, 0, w, 9, shadow, 0x0013252e);
        g.fillGradient(0, h - 9, w, h, 0x0013252e, shadow);
        for (int sign = -1; sign <= 1; sign += 2) {
            int a = sign < 0 ? 4 : w - 5;
            g.fill(a, edgeHeight, a + 1, h - edgeHeight, alpha(color, 72));
            int cap = a + sign * -edgeWidth;
            trace(g, a, edgeHeight, cap, 4, rail);
            trace(g, a, h - edgeHeight, cap, h - 5, rail);
        }
        int guide = alpha(color, powered ? 195 : 95), flare = alpha(color, 65);
        int span = swinging ? 30 : 24;
        g.fill(x - span, y, x - 10, y + 1, guide);
        g.fill(x + 11, y, x + span + 1, y + 1, guide);
        g.fill(x, y - 11, x + 1, y - 6, guide);
        g.fill(x, y + 7, x + 1, y + 12, guide);
        trace(g, x - span - 5, y + 7, x - span, y + 2, guide);
        trace(g, x + span, y + 2, x + span + 5, y + 7, guide);
        g.fill(x - 3, y - 3, x + 4, y + 4, flare);
        g.fill(x, y - 1, x + 1, y + 2, guide);
        g.fill(x - 1, y, x + 2, y + 1, guide);
    }

    private static void hitMarker(GuiGraphics g, int x, int y, int color) {
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dy = -1; dy <= 1; dy += 2) {
                trace(g, x + dx * 7, y + dy * 7, x + dx * 14, y + dy * 14, color);
            }
        }
    }

    private static void trace(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(1, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps, y = y1 + (y2 - y1) * i / steps;
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static int alpha(int color, int alpha) { return color & 0xffffff | alpha << 24; }

    private static String digits(int value, int width) {
        String raw = Integer.toString(value);
        return (raw.length() < width ? "0".repeat(width - raw.length()) : "") + raw;
    }

    private static void textRight(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.drawString(font, text, x - font.width(text), y, color, false);
    }
}

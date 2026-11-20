package cn.aca.omnitrix.client;

import cn.aca.omnitrix.Abilities;
import cn.aca.omnitrix.Form;
import cn.aca.omnitrix.Kind;
import cn.aca.omnitrix.TemporalEffects;
import cn.aca.omnitrix.Wrist.Stage;
import cn.aca.omnitrix.WristNetwork;
import cn.aca.omnitrix.WristService;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class WristVisuals {
    private static final float TAU = (float) Math.PI * 2;
    private static final int SILVER = 0xffb8ddfe;
    private static final int VIOLET = 0xffc9b8ff;
    private static final int GOLD = 0xfff9c68a;

    private record Afterimage(Vec3 at, float yaw, long tick, boolean starfield) {}
    private static final Map<UUID, ArrayDeque<Afterimage>> TRAILS = new HashMap<>();
    private static final Map<UUID, ArrayDeque<Vec3>> FEATHER_WAKES = new HashMap<>();
    private static MultiBufferSource.BufferSource worldBuffers;

    private WristVisuals() {}

    static void clear() {
        TRAILS.clear();
        FEATHER_WAKES.clear();
    }

    static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || ClientState.reduced()) {
            clear();
            return;
        }
        long now = mc.level.getGameTime();
        for (var trail : TRAILS.values()) trail.removeIf(frame -> now - frame.tick > 10);
        TRAILS.values().removeIf(ArrayDeque::isEmpty);

        for (var player : mc.level.players()) {
            var view = ClientState.get(player);
            if (view == null || !view.s().morphed() || player.isInvisible()
                    || player.distanceToSqr(mc.player) > 36 * 36) {
                FEATHER_WAKES.remove(player.getUUID());
                continue;
            }
            Kind kind = view.s().watch();
            Form form = view.s().chosen();

            if (form == Form.XLR8 && (kind == Kind.CLASSIC || kind == Kind.CELESTIAL)
                    && (player.isSprinting() || player.getDeltaMovement().horizontalDistanceSqr() > .09) && now % 2 == 0) {
                var trail = TRAILS.computeIfAbsent(player.getUUID(), key -> new ArrayDeque<>());
                if (trail.isEmpty() || trail.getLast().at.distanceToSqr(player.position()) > .018) {
                    trail.addLast(new Afterimage(player.position(), player.yBodyRot, now, kind == Kind.CELESTIAL));
                    while (trail.size() > 9) trail.removeFirst();
                }
            }

            if (form == Form.GREY_MATTER && kind == Kind.CELESTIAL && player.hasEffect(MobEffects.SLOW_FALLING)) {
                var wake = FEATHER_WAKES.computeIfAbsent(player.getUUID(), key -> new ArrayDeque<>());
                Vec3 pos = player.position().add(0, .24, 0);
                if (wake.isEmpty() || wake.getLast().distanceToSqr(pos) > .01) {
                    wake.addLast(pos);
                    while (wake.size() > 14) wake.removeFirst();
                }
            } else {
                var wake = FEATHER_WAKES.get(player.getUUID());
                if (wake != null && !wake.isEmpty()) wake.removeFirst();
            }
        }
        FEATHER_WAKES.values().removeIf(ArrayDeque::isEmpty);
    }

    /**
     * U1 Zero-Obstruction Starlight Hairline Micro-Arc HUD:
     * Symmetrically hugs the bottom-left of the crosshair (16..20px radius, 0.56*PI..0.94*PI),
     * pairing seamlessly with Infinity Gauntlet's bottom-right micro-arcs with ZERO black boxes or overlap!
     */
    static void hud(GuiGraphics g, DeltaTracker tracker) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator() || WristScreen.isOpen()) return;
        AscalonRenderer.renderHud(g, tracker);
        var view = ClientState.self();
        if (view == null) return;
        var snapshot = view.s();
        var phase = snapshot.phase();
        float partial = tracker.getGameTimeDeltaPartialTick(false);
        if (phase == Stage.LOCKING) resistance(g, view, tracker);
        feedback(g);
        if (phase == Stage.ARMING) {
            switch (snapshot.watch()) {
                case CELESTIAL -> celestials(g, view, tracker);
                case CARNITRIX -> carniLock(g, view, tracker);
                case CLASSIC -> classicShift(g, view, tracker);
            }
        }

        double now = view.now(partial);
        boolean warning = WatchRender.timeout(view);
        boolean alienX = snapshot.chosen() == Form.ALIEN_X && snapshot.watch() == Kind.CELESTIAL;
        boolean cdActive = phase == Stage.ACTIVE && (snapshot.primaryAt() > now || snapshot.secondaryAt() > now
                || snapshot.linkUntil() > now || snapshot.skillHintUntil() > now
                || (alienX && (mc.player.isShiftKeyDown() || snapshot.flickAt() > now)));
        boolean combatOrBusy = phase != Stage.IDLE || warning || snapshot.controlled() || cdActive;

        long idleTicks = ClientState.ticksSinceHudActivity();
        float breathe = 0.115f + 0.025f * Mth.sin((ClientState.clientTicks() + partial) * 0.08f);
        float hudAlpha;
        if (combatOrBusy || idleTicks <= 50L) {
            hudAlpha = 0.92f;
        } else {
            float fade = Mth.clamp((idleTicks - 50f) / 30f, 0f, 1f);
            hudAlpha = Mth.lerp(fade, 0.88f, breathe);
        }

        int tint = warning || phase == Stage.LOCKING ? 0xffff6764
                : phase == Stage.COOLING ? 0xffffb78a
                : DialDraw.glow(snapshot.watch());
        float cx = g.guiWidth() * 0.5f;
        float cy = g.guiHeight() * 0.5f;
        float ax = cx - 5.5f;
        float ay = cy + 5.5f;

        float arcStart = (float) (Math.PI * 0.56);
        float arcEnd = (float) (Math.PI * 0.94);
        float fill = phase == Stage.ACTIVE ? (1f - view.progress(partial))
                : phase == Stage.COOLING ? view.progress(partial) : 1f;
        fill = Mth.clamp(fill, 0f, 1f);

        // 1. Outer 1.2px Hairline Morph / Recharge Micro-Arc (bottom-left of reticle)
        DialDraw.arc(g, ax, ay, 18.8f, 20.0f, arcStart, arcEnd, DialDraw.alpha(0xffdce8f4, hudAlpha * 0.22f));
        if (fill > 0.01f) {
            float litStart = arcEnd - (arcEnd - arcStart) * fill;
            DialDraw.arc(g, ax, ay, 18.8f, 20.0f, litStart, arcEnd, DialDraw.alpha(tint, hudAlpha));
        }

        // 2. Inner 0.9px Hairline Barrier Micro-Arc (when morphed)
        if (phase == Stage.ACTIVE && snapshot.chosen() != null && snapshot.maxBarrier() > 0) {
            float barFrac = (float) Math.clamp(snapshot.barrier() / snapshot.maxBarrier(), 0.0, 1.0);
            int barCol = alienX ? 0xffd6c2ff : 0xffe8f6ff;
            DialDraw.arc(g, ax, ay, 16.2f, 17.1f, arcStart + 0.04f, arcEnd - 0.04f,
                    DialDraw.alpha(0xffa8bfd6, hudAlpha * 0.18f));
            if (barFrac > 0.01f) {
                float bStart = (arcEnd - 0.04f) - (arcEnd - arcStart - 0.08f) * barFrac;
                DialDraw.arc(g, ax, ay, 16.2f, 17.1f, bStart, arcEnd - 0.04f, DialDraw.alpha(barCol, hudAlpha * 0.92f));
            }
        }

        // 3. Faceted Omnitrix Hourglass Micro-Crest Pip at lower-left tip
        float pipAngle = (float) (Math.PI * 0.75);
        float px = ax + Mth.cos(pipAngle) * 23.2f;
        float py = ay + Mth.sin(pipAngle) * 23.2f;
        DialDraw.diamond(g, px, py - 1.5f, 1.7f, 1.4f, DialDraw.alpha(tint, hudAlpha));
        DialDraw.diamond(g, px, py + 1.5f, 1.7f, 1.4f, DialDraw.alpha(tint, hudAlpha));

        // 4. Airy Frameless Micro-Telemetry (only visible when morphed, recharging, or warned — zero black boxes!)
        if (hudAlpha > 0.28f && (phase != Stage.IDLE || warning || snapshot.controlled())) {
            var pose = g.pose();
            pose.pushPose();
            pose.translate(px - 4.0f, py + 2.5f, 0f);
            pose.scale(0.58f, 0.58f, 1f);

            String statusStr;
            if (snapshot.controlled()) {
                statusStr = Component.translatable("watch.aca_omnitrix.possessed").getString();
            } else if (warning) {
                statusStr = Component.translatable("watch.aca_omnitrix.timeout").getString() + " " + seconds(view.ticksLeft());
            } else if (phase == Stage.ACTIVE && snapshot.chosen() != null) {
                boolean alternate = alienX && mc.player.isShiftKeyDown();
                long leftAt = alternate ? snapshot.shiftPrimaryAt() : snapshot.primaryAt();
                long rightAt = alternate ? snapshot.shiftSecondaryAt() : snapshot.secondaryAt();
                String uKey = OmnitrixClient.PRIMARY.getTranslatedKeyMessage().getString();
                String iKey = OmnitrixClient.SECONDARY.getTranslatedKeyMessage().getString();
                StringBuilder sb = new StringBuilder();
                if (snapshot.watch() != Kind.CELESTIAL) {
                    sb.append(seconds(view.ticksLeft())).append(" · ");
                }
                sb.append(alternate ? "⇧" : "").append(uKey).append(' ').append(skillTime(leftAt - now))
                  .append("  ").append(alternate ? "⇧" : "").append(iKey).append(' ').append(skillTime(rightAt - now));
                if (alienX) {
                    sb.append("  ").append(OmnitrixClient.FLICK.getTranslatedKeyMessage().getString())
                      .append(' ').append(skillTime(snapshot.flickAt() - now));
                }
                if (snapshot.linkUntil() > now) sb.append(" ✦");
                statusStr = sb.toString();
            } else if (phase == Stage.COOLING) {
                statusStr = seconds(view.ticksLeft());
            } else {
                statusStr = Component.translatable("watch.aca_omnitrix." + phase.name().toLowerCase(Locale.ROOT)).getString();
            }

            int sw = mc.font.width(statusStr);
            int textCol = DialDraw.alpha(warning ? 0xffff8680 : snapshot.controlled() ? GOLD : 0xffeef6fa, hudAlpha);
            g.drawString(mc.font, statusStr, -sw, 0, textCol, true);
            pose.popPose();
        }
    }

    private static String skillTime(double ticks) {
        if (ticks <= 0) return "✓";
        return String.format(Locale.ROOT, "%.1f", Math.ceil(ticks / 2) / 10);
    }

    private static String seconds(int ticks) {
        int sec = (ticks + 19) / 20;
        return "%d:%02d".formatted(sec / 60, sec % 60);
    }

    private static void classicShift(GuiGraphics g, ClientState.View view, DeltaTracker tracker) {
        float t = view.age(tracker.getGameTimeDeltaPartialTick(false));
        int pulseColor = view.s().misfire() && t < 14 ? 0xffff4f46 : DialDraw.glow(Kind.CLASSIC);
        int coreGreen = view.s().misfire() && t < 14 ? 0xffff382e : 0xff46df62;
        int x = g.guiWidth() / 2, y = g.guiHeight() - 112;

        if (!ClientState.reduced() && t < 5.6f) {
            float collapse = 1f - Motion.part(t, 0f, 5.5f);
            float implosionR = 8f + collapse * 46f;
            DialDraw.gradientArc(g, x, y + 12, implosionR, implosionR + 4.5f, 0, TAU,
                    DialDraw.alpha(pulseColor, collapse * .82f), DialDraw.alpha(coreGreen, 0f));
        }

        float open = Motion.part(t, 4f, 8.8f) * (1 - Motion.part(t, 13.5f, 18.5f));
        if (open < .02f) return;
        if (!ClientState.reduced()) {
            for (int i = 0; i < 4; i++) {
                float rIn = 12 + i * 9 + t * 1.75f;
                DialDraw.arc(g, x, y, rIn, rIn + 1.5f, -2.8f + i * .12f, -.34f - i * .12f,
                        DialDraw.alpha(pulseColor, open * (.88f - i * .16f)));
            }
            float flash = Motion.part(t, 5.0f, 6.6f) * (1 - Motion.part(t, 6.8f, 12.8f));
            if (flash > .02f) {
                g.fill(0, 0, g.guiWidth(), g.guiHeight(), DialDraw.alpha(coreGreen, flash * .18f));
                float hg = 18f + (1f - flash) * 28f;
                DialDraw.diamond(g, x, y - hg * .45f, hg * .55f, hg * .45f, DialDraw.alpha(pulseColor, flash * .62f));
                DialDraw.diamond(g, x, y + hg * .45f, hg * .55f, hg * .45f, DialDraw.alpha(pulseColor, flash * .62f));
            }
        }
        caption(g, view.s().misfire() && t < 14 ? "watch.aca_omnitrix.misfire" : view.s().chosen().translationKey(Kind.CLASSIC),
                g.guiWidth() / 2, g.guiHeight() - 96, pulseColor);
    }

    private static void resistance(GuiGraphics g, ClientState.View view, DeltaTracker tracker) {
        var mc = Minecraft.getInstance();
        float time = view.age(tracker.getGameTimeDeltaPartialTick(false));
        int x = g.guiWidth() / 2, y = (int) (g.guiHeight() * .60f);
        int red = 0xfffc8983;
        float jitter = ClientState.reduced() ? 0 : Mth.sin(time * 1.8f) * .23f;
        float angle = (float) Math.toRadians(ClientState.bearing(mc.player, view, 0)) - TAU / 4 + jitter;
        DialDraw.tick(g, x, y - 25, 10, 23, angle, red);
        DialDraw.arc(g, x, y - 25, 25, 26.2f, -TAU / 4, -TAU / 4 + TAU * (1 - view.progress(0)), red);
        Component message = Component.translatable("watch.aca_omnitrix.resist", mc.options.keyShift.getTranslatedKeyMessage());
        int width = Math.max(112, mc.font.width(message) + 18);
        int left = x - width / 2;
        g.drawCenteredString(mc.font, message, x, y + 6, 0xffffe3d7);
        for (int i = 0; i < WristService.RESIST_PRESSES; i++) {
            int from = left + 8 + i * (width - 16) / WristService.RESIST_PRESSES;
            int to = left + 6 + (i + 1) * (width - 16) / WristService.RESIST_PRESSES;
            g.fill(from, y + 18, to, y + 20, i < view.s().resistance() ? red : 0x5569404a);
        }
    }

    private static void feedback(GuiGraphics g) {
        var mc = Minecraft.getInstance();
        var effect = ClientState.effect(mc.player);
        if (effect == null) return;
        long age = mc.level.getGameTime() - effect.tick();
        int action = effect.cue().action();
        if (age < 25 && action == WristNetwork.FAILSAFE_CUE) {
            caption(g, "watch.aca_omnitrix.failsafe", g.guiWidth() / 2, g.guiHeight() - 96, 0xffc3fa89);
        } else if (age < 14 && (action == Form.ALIEN_X.ordinal()
                || action == Abilities.SECONDARY + Form.ALIEN_X.ordinal()
                || action == WristNetwork.SINGULARITY_CUE
                || action == WristNetwork.EXTINCTION_CUE
                || action == WristNetwork.REWRITE_CUE)) {
            float x = g.guiWidth() * .5f, y = g.guiHeight() * .43f;
            float opacity = ClientState.reduced() ? .4f : Math.min(1, 1.25f - age / 15f);
            float size = Math.min(50f, g.guiHeight() * .17f);
            float spread = Math.min(g.guiWidth() * .25f, 108f);
            boolean stasis = action == Form.ALIEN_X.ordinal() || action == WristNetwork.SINGULARITY_CUE;
            int color = action == WristNetwork.REWRITE_CUE ? SILVER : stasis ? VIOLET : GOLD;
            persona(g, stasis ? "serena" : "bellicus", x + (stasis ? -spread : spread), y,
                    size, stasis ? 19 : -19, DialDraw.alpha(color, opacity), age + (stasis ? 10 : 17));
            String capKey = action == WristNetwork.SINGULARITY_CUE ? "motion_singularity"
                    : action == WristNetwork.EXTINCTION_CUE ? "motion_extinction"
                    : action == WristNetwork.REWRITE_CUE ? "motion_rewrite"
                    : stasis ? "motion_stasis" : "motion_starburst";
            caption(g, "watch.aca_omnitrix." + capKey,
                    (int) x, (int) (y + size * .72f), SILVER);
        }
    }

    private static void carniLock(GuiGraphics g, ClientState.View view, DeltaTracker tracker) {
        float t = view.age(tracker.getGameTimeDeltaPartialTick(false));
        float x = g.guiWidth() * .5f, y = g.guiHeight() * .54f;
        float scan = Motion.part(t, 0, 9);
        float radius = 48 - scan * 24;
        int color = DialDraw.alpha(GOLD, .80f);
        for (int i = 0; i < 4; i++) {
            float angle = TAU * i / 4f + (ClientState.reduced() ? 0 : t * .059f);
            DialDraw.arc(g, x, y, radius, radius + 1.5f, angle + .12f, angle + .65f, color);
            DialDraw.tick(g, x, y, radius - 12, radius, angle, color);
        }
        float bearing = ClientState.bearing(Minecraft.getInstance().player, view, tracker.getGameTimeDeltaPartialTick(false));
        float aim = (float) Math.toRadians(bearing) - TAU / 4;
        DialDraw.arc(g, x, y, radius + 5, radius + 7.2f, aim - .12f, aim + .12f, GOLD);
        DialDraw.tick(g, x, y, radius - 15, radius - 4, aim, GOLD);
        caption(g, "watch.aca_omnitrix.forced_shift", g.guiWidth() / 2, (int) y + 39, GOLD);
        if (t < 14) {
            String key = !ClientState.reduced() && (int) (t / 3) % 2 == 0 ? "override_noise" : "override";
            caption(g, "watch.aca_omnitrix." + key, g.guiWidth() / 2, (int) y + 21, 0xfffc8983);
        }
    }

    private static void celestials(GuiGraphics g, ClientState.View view, DeltaTracker tracker) {
        float t = view.age(tracker.getGameTimeDeltaPartialTick(false));
        float x = g.guiWidth() * .50f, y = g.guiHeight() * .465f;
        float meter = Math.min(g.guiWidth() / 430f, g.guiHeight() / 250f);
        float s = Math.clamp(meter, .64f, 1.45f);
        float spread = 112 * s;
        float press = Motion.part(t, 1, 9);
        float split = Motion.part(t, 9, 15);
        float second = Motion.part(t, 15, 21);
        float cut = Motion.part(t, 30, 39);
        float fade = 1 - Motion.part(t, 38, 47);
        float left = (ClientState.reduced() ? 0 : split * 12 * (1 - cut));
        float right = (ClientState.reduced() ? 0 : second * 12 * (1 - cut));
        int background = DialDraw.alpha(0x880b1220, press * fade * .48f);
        g.fillGradient(0, (int) (y - 96 * s), g.guiWidth(), (int) (y + 84 * s), DialDraw.alpha(background, .08f), background);
        for (int rail = 0; rail < 3; rail++) {
            float turn = -1.57f + rail * TAU / 3 + (ClientState.reduced() ? 0 : t * (.024f + rail * .008f));
            float radius = (25 + rail * 8 + cut * 36) * s;
            DialDraw.arc(g, x, y, radius, radius + (rail == 2 ? 2.0f : 1.1f) * s, turn, turn + 1.31f + cut * .25f,
                    DialDraw.alpha(rail == 0 ? SILVER : rail == 1 ? VIOLET : GOLD, fade * press * (rail == 0 ? 1 : 1 - cut)));
        }
        if (t >= 31 && t < 44 && !ClientState.reduced()) {
            float burstR = (16 + cut * 72) * s;
            DialDraw.arc(g, x, y, burstR, burstR + 2.4f * s, 0, TAU, DialDraw.alpha(SILVER, fade * (1 - cut * .6f) * .65f));
        }
        if (split > .01f && cut < 1) {
            float own = x - spread + left * s;
            DialDraw.edge(g, own + 17 * s, y + 10 * s, x - 34 * s, y + 2 * s, 1.5f * s, DialDraw.alpha(VIOLET, split * (1 - cut)));
            persona(g, "serena", own, y - 8 * s, 72 * s * split, 11 - right * .69f,
                    DialDraw.alpha(VIOLET, split * (1 - cut)), t);
        }
        if (second > .01f && cut < 1) {
            float own = x + spread - right * s;
            DialDraw.edge(g, own - 17 * s, y + 10 * s, x + 34 * s, y + 2 * s, 1.5f * s, DialDraw.alpha(GOLD, second * (1 - cut)));
            persona(g, "bellicus", own, y - 8 * s, 76 * s * second, -13 + left * .72f,
                    DialDraw.alpha(GOLD, second * (1 - cut)), t);
        }
        if (t >= 28) {
            for (int i = -1; i <= 1; i += 2) {
                float gap = 10 + cut * 15;
                DialDraw.edge(g, x - gap * s, y - i * gap * s, x + gap * s, y + i * gap * s, 1.4f * s, DialDraw.alpha(SILVER, fade * cut));
            }
        }
        if (t < 36) {
            String line = t < 13 ? "x_align" : t < 20 ? "x_serena" : t < 30 ? "x_bellicus" : "x_block";
            caption(g, "watch.aca_omnitrix." + line, (int) x, (int) (y + 94 * s), t < 30 ? 0xffe9e4f0 : SILVER);
        } else {
            var name = Component.translatable(view.s().chosen().translationKey(Kind.CELESTIAL));
            caption(g, "watch.aca_omnitrix.x_control", (int) x, (int) (y + 94 * s), SILVER, name);
        }
    }

    private static void persona(GuiGraphics g, String name, float x, float y, float size, float yaw, int color, float tick) {
        if (size < .1f) return;
        g.flush();
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y + size * .44f, 111);
        pose.scale(size, -size, size);
        pose.mulPose(Axis.YP.rotationDegrees(180 + yaw));
        Rig rig = Rig.get(name);
        rig.draw(pose, g.bufferSource(), personaPose(rig, name, tick), 16 / rig.height, color,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        g.flush();
        pose.popPose();
    }

    private static Rig.Motion personaPose(Rig rig, String name, float tick) {
        float start = name.equals("serena") ? 10 : 17;
        if (tick >= 30) return rig.animate(Rig.STILL, "verdict", tick - 30, 1);
        if (tick >= start) return rig.animate(Rig.STILL, "speak", tick - start, 1);
        return rig.animate(Rig.STILL, "idle", tick, ClientState.reduced() ? .25f : 1);
    }

    /** U1 Frameless Luminous Hairline Caption (replaces opaque dark rectangle box!). */
    private static void caption(GuiGraphics g, String key, int x, int y, int color, Object... arguments) {
        var font = Minecraft.getInstance().font;
        var text = Component.translatable(key, arguments);
        int half = Math.max(24, font.width(text) / 2 + 8);
        g.fill(x - half, y + 10, x + half, y + 11, DialDraw.alpha(color, 0.34f));
        g.fill(x - half / 2, y + 10, x + half / 2, y + 11, DialDraw.alpha(color, 0.78f));
        DialDraw.diamond(g, x, y + 10.5f, 2.2f, 1.4f, color);
        g.drawCenteredString(font, text, x, y, color);
    }

    static void world(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        StarfieldShader.clock(partial);
        CosmicVfxShader.clock(partial);

        Vec3 camera = e.getCamera().getPosition();
        PoseStack pose = e.getPoseStack();
        if (worldBuffers == null) worldBuffers = CosmicVfxShader.createWorldBuffers();
        var buffers = worldBuffers;
        long now = mc.level.getGameTime();
        float animTicks = ClientState.clientTicks() + partial;

        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);

        // 1. XLR8 & X-XLR8 afterimages + dark-sheathed twin wheel ground speed streams
        if (!TRAILS.isEmpty()) {
            Rig rig = Rig.get("xlr8");
            for (var entry : TRAILS.entrySet()) {
                boolean isSelfFp = mc.player != null && entry.getKey().equals(mc.player.getUUID())
                        && mc.options.getCameraType().isFirstPerson();
                var wheelLeft = new ArrayList<Vec3>();
                var wheelRight = new ArrayList<Vec3>();
                boolean celestialTrail = false;
                for (var frame : entry.getValue()) {
                    float age = now - frame.tick + partial;
                    if (age > 10 || frame.at.distanceToSqr(camera) > 40 * 40) continue;
                    celestialTrail |= frame.starfield;
                    double rad = Math.toRadians(frame.yaw);
                    Vec3 sideOffset = new Vec3(Math.cos(rad) * .22, .07, Math.sin(rad) * .22);
                    wheelLeft.add(frame.at.add(sideOffset));
                    wheelRight.add(frame.at.subtract(sideOffset));
                    if (!isSelfFp) {
                        pose.pushPose();
                        pose.translate(frame.at.x, frame.at.y, frame.at.z);
                        pose.mulPose(Axis.YP.rotationDegrees(180 - frame.yaw));
                        int ghostCol = frame.starfield
                                ? DialDraw.alpha(0xffa8c4ff, (float) (.42 * Math.pow(.55, age / 3)))
                                : DialDraw.alpha(0xff62ccff, (float) (.38 * Math.pow(.52, age / 3)));
                        rig.draw(pose, buffers, Motion.actor(Form.XLR8, false, age, age, 1, 0, 0, false, false, 0, 1),
                                1.74f * 16 / rig.height, ghostCol, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                                frame.starfield);
                        pose.popPose();
                    }
                }
                if (wheelLeft.size() >= 2) {
                    int streamCol = celestialTrail ? 0xe0a6ccff : 0xde56d8ff;
                    VfxPrimitives.lusterPolyline(pose, buffers, wheelLeft, camera, .105, streamCol);
                    VfxPrimitives.lusterPolyline(pose, buffers, wheelRight, camera, .105, streamCol);
                }
            }
        }

        // 2. X-Grey Matter slow-falling cosmic feather wake
        for (var wake : FEATHER_WAKES.values()) {
            if (wake.size() >= 2) {
                VfxPrimitives.lusterPolyline(pose, buffers, new ArrayList<>(wake), camera, .11, 0xd4bcf0ff);
            }
        }

        // 3. Alien X Stasis Field + Voronoi Mirror Shatter Rim
        for (var active : ClientState.scenes()) {
            var scene = active.scene();
            if (scene.effect() != WristNetwork.STASIS_SCENE || active.until() <= now
                    || scene.center().distanceToSqr(camera) > 84 * 84) continue;
            drawStasisField(pose, buffers, camera, scene.center(), animTicks,
                    Mth.clamp((active.until() - now) / 14f, 0, 1));
        }

        // 3b. Active 4-Layer Puppet & Soul-Terror Floating Crowns
        long cNow = ClientState.clientTicks();
        for (var marker : ClientState.controlMarkers()) {
            if (marker.pos().distanceToSqr(camera) > 64 * 64) continue;
            float rem = (marker.until() - cNow - partial);
            if (rem <= 0) continue;
            float mAlpha = Mth.clamp(rem / 16f, 0f, 1f) * Mth.clamp((cNow + partial - marker.startTick()) / 4f, 0.25f, 1f);
            VfxPrimitives.controlCrown(pose, buffers, marker.pos(), camera, animTicks,
                    marker.galvanic(), marker.terror(), marker.celestial(), mAlpha);
        }

        // 4. Windup anticipation gathering, 3D hexagonal crystal shell & transformation transitions
        for (Player p : mc.level.players()) {
            var view = ClientState.get(p);
            if (view == null || p.distanceToSqr(camera) > 68 * 68) continue;
            if (view.s().crystalShellUntil() > view.now(partial)) drawCrystalShell(pose, buffers, p, partial, camera);
            drawSkillWarning(pose, buffers, p, view, partial, camera);
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
                if (view.s().phase() == Stage.ARMING || view.s().phase() == Stage.REVERTING) {
                    drawTransition(pose, buffers, p, view, partial, camera, true);
                }
                continue;
            }
            if (view.s().phase() == Stage.ARMING || view.s().phase() == Stage.REVERTING) {
                drawTransition(pose, buffers, p, view, partial, camera, false);
            }
        }

        // 4b. Ascalon Blade Aurora Trails, Ground Astrolabe, Storm Domes & 6-Planet Sky Syzygy!
        AscalonRenderer.renderWorld(pose, buffers, camera, now, partial);

        // 5. Multi-event skill & projectile cues
        for (var effect : ClientState.effects()) {
            var cue = effect.cue();
            if (cue.to().distanceToSqr(camera) > 128 * 128 && cue.from().distanceToSqr(camera) > 128 * 128) continue;
            float age = now - effect.tick() + partial;
            if (age < 0) continue;
            renderCueEffect(pose, buffers, cue, age, partial, camera);
        }

        buffers.endBatch();
        pose.popPose();
    }

    private static void drawStasisField(PoseStack pose, MultiBufferSource buffers, Vec3 camera,
                                        Vec3 center, float elapsed, float opacity) {
        float turn = ClientState.reduced() ? 0 : elapsed * .013f;
        int tint = DialDraw.alpha(0xff7c8eff, opacity * .82f);
        VfxPrimitives.astrolabeRingEroded(pose, buffers, center.add(0, .07, 0), TemporalEffects.RANGE,
                .52, turn, turn + TAU, 64, tint, .08f);
        VfxPrimitives.voronoiShatterCrown(pose, buffers, center.add(0, .10, 0), new Vec3(0, 1, 0), camera,
                Math.min(12.5, TemporalEffects.RANGE * .32), 1.45, -turn * 1.4f, tint, .22f);
        if (ClientState.reduced()) return;
        for (int i = 0; i < 8; i++) {
            float azimuth = i * TAU / 8 + turn;
            Vec3 base = center.add(Math.cos(azimuth) * 39.8, .08, Math.sin(azimuth) * 39.8);
            if (base.distanceToSqr(camera) > 64 * 64) continue;
            VfxPrimitives.hexCrystalSpire(pose, buffers, base, new Vec3(0, 1, 0), .42, 4.4,
                    DialDraw.alpha(0xff778eff, opacity * .74f), .12f);
        }
    }

    private static void drawCrystalShell(PoseStack pose, MultiBufferSource buffers, Player player, float partial, Vec3 camera) {
        Vec3 center = player.getPosition(partial).add(0, player.getBbHeight() * .20, 0);
        float spin = (ClientState.clientTicks() + partial) * .045f;
        for (int i = 0; i < 6; i++) {
            double angle = spin + i * TAU / 6;
            Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 base = center.add(radial.scale(.72));
            Vec3 tiltUp = new Vec3(radial.x * .24, 1.0, radial.z * .24);
            VfxPrimitives.hexCrystalSpire(pose, buffers, base, tiltUp, .18, 1.32, 0xc868f5e2, .08f);
        }
    }

    private static void renderCueEffect(PoseStack pose, MultiBufferSource buffers, WristNetwork.Cue cue,
                                        float age, float partial, Vec3 camera) {
        if (AscalonRenderer.renderCue(pose, buffers, cue, age, camera)) return;
        if (cue.action() == WristNetwork.PUPPET_CUE || cue.action() == WristNetwork.TERROR_CUE) {
            if (age > 18f) return;
            float p = age / 18f;
            boolean terror = cue.action() == WristNetwork.TERROR_CUE;
            boolean galvanic = cue.linked();
            int c1 = DialDraw.alpha(terror ? 0xffd288ff : galvanic ? 0xff62f89c : 0xffb894ff, 1f - p);
            int c2 = DialDraw.alpha(galvanic ? 0xffe0fff2 : 0xfff4e8ff, (1f - p) * .88f);
            VfxPrimitives.curvedEnergyWhip(pose, buffers, cue.from(), cue.to(), camera,
                    .14 * (1f - p * .4f), .045, .48, age * .35f, c1, c2, p * .62f);
            VfxPrimitives.voronoiShatterCrown(pose, buffers, cue.to().add(0, -.35, 0), new Vec3(0, 1, 0), camera,
                    .38 + p * 1.15, .45 * (1f - p), age * .24f, c1, p * .55f);
            return;
        }
        if (cue.action() == WristNetwork.CRYSTAL_SHELL_BREAK_CUE) {
            if (age < 15) {
                float p = age / 15f;
                int col = DialDraw.alpha(0xff72f5dc, 1 - p);
                VfxPrimitives.voronoiShatterCrown(pose, buffers, cue.from().add(0, .85, 0), new Vec3(0, 1, 0), camera,
                        .55 + p * 1.85, .75 * (1f - p), age * .22f, col, p * .58f);
                VfxPrimitives.lusterImpactBurst(pose, buffers, cue.from().add(0, 1.10, 0), camera, 1.9, p, col);
            }
            return;
        }
        if (cue.action() == WristNetwork.THAW_CUE || cue.action() == WristNetwork.FAILSAFE_CUE
                || cue.action() == WristNetwork.RESISTED_CUE) {
            if (age > 24) return;
            float p = age / 24f;
            boolean thaw = cue.action() == WristNetwork.THAW_CUE;
            int col = DialDraw.alpha(thaw ? SILVER : 0xffc3fa89, 1 - p);
            VfxPrimitives.lusterRingEroded(pose, buffers, cue.from().add(0, .18, 0), .6 + age * (thaw ? 1.25 : .30),
                    .30, .24 * (1 - p), 0, TAU, 44, col, p * .4f);
            if (thaw) {
                VfxPrimitives.voronoiShatterCrown(pose, buffers, cue.from().add(0, .25, 0), new Vec3(0, 1, 0), camera,
                        .8 + p * 6.5, .95 * (1f - p), age * .15f, col, p * .65f);
            }
            return;
        }
        if (cue.action() == WristNetwork.AEGIS_CUE) {
            if (age > 14) return;
            float p = age / 14f;
            int col = DialDraw.alpha(SILVER, (1f - p) * .92f);
            VfxPrimitives.voronoiShatterCrown(pose, buffers, cue.to(), cue.to().subtract(cue.from()), camera,
                    .45 + p * 1.55, .42 * (1f - p), age * .25f, col, p * .45f);
            VfxPrimitives.lusterImpactBurst(pose, buffers, cue.to(), camera, 1.45, p, col);
            return;
        }
        if (cue.action() == WristNetwork.FLICK_CUE) {
            if (age > 16) return;
            float p = age / 16f;
            int col = DialDraw.alpha(0xffc978ff, 1f - p);
            Vec3 start = attached(cue, "grip_r", partial);
            VfxPrimitives.curvedEnergyWhip(pose, buffers, start, cue.to(), camera,
                    .14 * (1 - p * .45f), .035, .36, age * .42f, col, DialDraw.alpha(GOLD, 1f - p), p * .65f);
            VfxPrimitives.voronoiShatterCrown(pose, buffers, cue.to(), cue.to().subtract(start), camera,
                    .35 + p * 2.1, .65 * (1f - p), age * .32f, DialDraw.alpha(GOLD, 1f - p), p * .48f);
            if (cue.hitEntity()) VfxPrimitives.lusterImpactBurst(pose, buffers, cue.to(), camera, 2.2, p, col);
            return;
        }
        if (cue.action() == WristNetwork.SINGULARITY_CUE) {
            if (age > 34) return;
            float p = age / 34f;
            Vec3 core = cue.to().add(0, .84, 0);
            float fade = (1f - p) * .88f;
            VfxPrimitives.celestialPlanet(pose, buffers, core, .20 + 1.95 * (1 - p), age * .038f,
                    DialDraw.alpha(0xff382057, (1f - p) * .99f), DialDraw.alpha(VIOLET, fade), true);
            VfxPrimitives.voronoiShatterCrown(pose, buffers, core, new Vec3(.18, 1, -.14), camera,
                    1.2 + 5.6 * (1 - p * .72), 1.35 * (1 - p), -age * .16f, DialDraw.alpha(VIOLET, fade), p * .45f);
            inwardArcs(pose, buffers, camera, core, age, 9.2 * (1 - p * .86), fade, false);
            return;
        }
        if (cue.action() == WristNetwork.EXTINCTION_CUE) {
            if (age > 28) return;
            float p = age / 28f;
            float fade = 1f - p;
            Vec3 start = attached(cue, "grip_r", partial);
            Vec3 end = cue.to();
            VfxPrimitives.curvedEnergyWhip(pose, buffers, start, end, camera, .72 * fade, .46 * fade,
                    .58, age * .36f, DialDraw.alpha(0xffa676ff, fade * .96f),
                    DialDraw.alpha(0xffffd89a, fade * .90f), p * .52f);
            VfxPrimitives.voronoiShatterCrown(pose, buffers, end, end.subtract(start), camera,
                    .95 + p * 3.2, 1.1 * fade, age * .18f, DialDraw.alpha(0xffb48cff, fade), p * .52f);
            return;
        }
        if (cue.action() == WristNetwork.REWRITE_CUE) {
            if (age > 32) return;
            float p = age / 32f;
            float fade = 1f - p;
            VfxPrimitives.voronoiShatterCrown(pose, buffers, cue.from().add(0, .20, 0), new Vec3(0, 1, 0), camera,
                    1.6 + p * 15.8, 2.2 * fade, age * .14f, DialDraw.alpha(GOLD, fade), p * .48f);
            VfxPrimitives.astrolabeRingEroded(pose, buffers, cue.from().add(0, 1.1, 0), 1.0 + p * 12.0, .75,
                    -age * .18, -age * .18 + TAU, 44, DialDraw.alpha(SILVER, fade), p * .35f);
            VfxPrimitives.lusterImpactBurst(pose, buffers, cue.to(), camera, 4.8, p, DialDraw.alpha(VIOLET, fade));
            return;
        }
        if (cue.action() >= WristNetwork.FAILSAFE_CUE) return;

        int rawAction = cue.action();
        boolean secondary = rawAction >= Abilities.SECONDARY && rawAction < Abilities.AUTOMATED;
        boolean automated = rawAction >= Abilities.AUTOMATED;
        Form form = Form.at(rawAction % 32);
        if (form == null) return;

        Kind watch = cue.watch();
        if (watch == null) {
            var caster = Minecraft.getInstance().level.getPlayerByUUID(cue.actor());
            var cv = ClientState.get(caster);
            watch = cv == null ? Kind.CLASSIC : cv.s().watch();
        }
        boolean star = watch == Kind.CELESTIAL;
        boolean carni = watch == Kind.CARNITRIX || automated;

        if (cue.projectile()) {
            if (age > 16) return;
            float p = age / 16f;
            int col = DialDraw.alpha(star ? switch (form) {
                case HEATBLAST -> 0xfff2beff;
                case DIAMONDHEAD -> 0xff9ef0ff;
                default -> 0xff98ffd8;
            } : switch (form) {
                case HEATBLAST -> 0xffff9e44;
                case DIAMONDHEAD -> 0xff82f5ed;
                default -> 0xff98f66c;
            }, 1f - p);
            VfxPrimitives.lusterImpactBurst(pose, buffers, cue.to(), camera, star ? 1.65 : 1.25, p, col);
            if (form == Form.DIAMONDHEAD) {
                VfxPrimitives.voronoiShatterCrown(pose, buffers, cue.to(), new Vec3(0, 1, 0), camera,
                        .32 + p * (star ? 1.45 : 1.05), .48 * (1f - p), age * .25f, col, p * .55f);
            } else {
                VfxPrimitives.lusterRingEroded(pose, buffers, cue.to().add(0, .05, 0),
                        .28 + p * (form == Form.STINKFLY ? (star ? 1.75 : 1.25) : (star ? 1.25 : .92)),
                        form == Form.STINKFLY ? .34 : .22, .12 * (1 - p), 0, TAU, 28, col, p * .55f);
            }
            return;
        }

        float maxAge = switch (form) {
            case ALIEN_X -> secondary ? 28f : 26f;
            case FOUR_ARMS -> secondary ? 24f : 16f;
            case WILDMUTT, DIAMONDHEAD -> !secondary ? 28f : 20f;
            default -> 22f;
        };
        if (age > maxAge) return;
        float progress = age / maxAge;
        float alpha = (1f - progress) * (ClientState.reduced() ? .68f : .96f);

        int baseTint = carni ? switch (form) {
            case HEATBLAST -> 0xffdc5e38;
            case WILDMUTT, FOUR_ARMS -> 0xffcc7c5c;
            case DIAMONDHEAD -> 0xff5ec0af;
            case XLR8 -> 0xff7c98bc;
            case GREY_MATTER, GHOSTFREAK -> 0xffb482c8;
            case STINKFLY -> 0xffa2be50;
            case RIPJAWS -> 0xff4aa4b0;
            case UPGRADE -> 0xff98c44e;
            case ALIEN_X -> 0xffc64e82;
        } : star ? switch (form) {
            case HEATBLAST -> 0xfff09462;
            case WILDMUTT -> 0xff72c6d2;
            case DIAMONDHEAD -> 0xff9ef4ff;
            case XLR8 -> 0xffa4ccff;
            case GREY_MATTER -> 0xffbeffe8;
            case FOUR_ARMS -> 0xffc2afff;
            case STINKFLY -> 0xff9effd4;
            case RIPJAWS -> 0xff94e8ff;
            case UPGRADE -> 0xff92ffdc;
            case GHOSTFREAK -> 0xffceb2ff;
            case ALIEN_X -> secondary ? 0xfff0ad54 : 0xff8298f2;
        } : switch (form) {
            case HEATBLAST -> 0xffffa048;
            case DIAMONDHEAD -> 0xff82f2eb;
            case WILDMUTT -> 0xffdc9e54;
            case XLR8 -> 0xff58c8f6;
            case GREY_MATTER -> 0xffb6dc92;
            case FOUR_ARMS -> 0xfff07a48;
            case STINKFLY, UPGRADE -> 0xff7ef8ae;
            case ALIEN_X, GHOSTFREAK -> 0xffb8aeff;
            case RIPJAWS -> 0xff7ee4f2;
        };
        int color = DialDraw.alpha(baseTint, alpha);
        Vec3 from = cue.from();
        Vec3 to = cue.to();
        Vec3 dir = to.subtract(from);
        Vec3 fwd = dir.lengthSqr() < 1e-5 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 right = fwd.cross(Math.abs(fwd.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();

        switch (form) {
            case FOUR_ARMS -> {
                if (age <= 10 && (!secondary || cue.hitGround())) {
                    fistTrails(pose, buffers, cue, partial, camera, to, alpha, baseTint, secondary, carni, star, progress);
                }
                if (carni) {
                    float tilt = cue.beat() == 0 ? -24f : 24f;
                    VfxPrimitives.lusterSlashArcEroded(pose, buffers, from.add(0, -.35, 0), dir,
                            3.5, .82, tilt, -1.05f, 2.10f, color, progress * .55f);
                } else if (!secondary) {
                    double reach = star ? 5.3 : 4.7;
                    double radius = Math.min(reach, Math.max(2.1, dir.horizontalDistance() * (.80 + .20 * progress)));
                    int beat = cue.beat();
                    float er = progress * .52f;
                    if (beat == 0) {
                        VfxPrimitives.lusterSlashArcEroded(pose, buffers, from.add(0, .12, 0), dir,
                                radius, star ? .88 : .68, 16f, -.95f, 1.65f, color, er);
                        VfxPrimitives.lusterSlashArcEroded(pose, buffers, from.add(0, -.34, 0), dir,
                                radius * .86, star ? .66 : .50, -12f, -.80f, 1.45f, color, er);
                    } else if (beat == 1) {
                        VfxPrimitives.lusterSlashArcEroded(pose, buffers, from.add(0, .06, 0), dir,
                                radius * 1.04, star ? .98 : .76, -18f, -.74f, 1.78f, color, er);
                        VfxPrimitives.curvedEnergyWhip(pose, buffers, from, to, camera,
                                star ? .26 : .19, .045, .28, age * .35f, color, DialDraw.alpha(GOLD, alpha * .85f), er);
                    } else {
                        VfxPrimitives.lusterSlashArcEroded(pose, buffers, from, dir,
                                radius * 1.05, star ? 1.08 : .82, 22f, -1.18f, 2.36f, color, er);
                        VfxPrimitives.lusterSlashArcEroded(pose, buffers, from, dir,
                                radius * .97, star ? .92 : .70, -22f, -1.18f, 2.36f, color, er);
                        VfxPrimitives.bowShockWave(pose, buffers, to, fwd, right, camera,
                                star ? 1.45 : 1.15, .42, color);
                        VfxPrimitives.voronoiShatterCrown(pose, buffers, to, fwd, camera,
                                .55 + progress * 1.95, .62 * (1f - progress), age * .22f, color, er);
                    }
                } else {
                    double maxR = (star ? 5.4 : 4.5) * (cue.hitGround() ? 1.0 : .78);
                    double r = .45 + (maxR - .45) * Math.pow(progress, .64);
                    VfxPrimitives.voronoiShatterCrown(pose, buffers, to, new Vec3(0, 1, 0), camera,
                            r, (star ? .95 : .68) * (1f - progress), age * .14f, color, progress * .52f);
                    VfxPrimitives.astrolabeRingEroded(pose, buffers, to.add(0, .03, 0), r * .72, .32,
                            age * .12, age * .12 + TAU, 36, DialDraw.alpha(star ? SILVER : 0xffffd6a8, alpha * .82f), progress * .4f);
                    if (age < 10) {
                        VfxPrimitives.lusterImpactBurst(pose, buffers, to.add(0, .25, 0), camera,
                                star ? 2.4 : 1.8, age / 10f, color);
                    }
                }
            }
            case HEATBLAST -> {
                if (carni) {
                    VfxPrimitives.nebulaCone(pose, buffers, from, to, .22, 1.42, age * .16f,
                            DialDraw.alpha(0xff4e2529, alpha * .78f), false);
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, from, to, camera, .28, .65, .36, age * .35f,
                            color, DialDraw.alpha(0xffffca7a, alpha), progress * .5f);
                } else if (!secondary) {
                    Vec3 palm = attached(cue, "grip_r", partial);
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, palm, to, camera,
                            star ? .22 : .16, .04, .26, age * .4f, color, DialDraw.alpha(0xffffe29e, alpha), progress * .55f);
                    VfxPrimitives.orientedAstrolabeRingEroded(pose, buffers, palm.add(fwd.scale(.25)), fwd, camera,
                            .24 + progress * .38, .12, age * .22, 20, color, progress * .5f);
                } else if (cue.beat() > 0 && cue.linked()) {
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, from, to, camera, .18, .06, .48, age * .38f,
                            color, DialDraw.alpha(0xffffdc92, alpha), progress * .45f);
                } else {
                    Vec3 rPalm = attached(cue, "grip_r", partial);
                    Vec3 lPalm = attached(cue, "grip_l", partial);
                    Vec3 originMid = rPalm.lerp(lPalm, .5);
                    Vec3 spread = right.scale(star ? .68 : .46);
                    VfxPrimitives.nebulaCone(pose, buffers, originMid, to, .20, star ? 1.45 : 1.08, age * .22f,
                            DialDraw.alpha(star ? 0xffa658d8 : 0xffcc4822, alpha * .62f), star);
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, rPalm, to.add(spread), camera,
                            .24, .58, .38, age * .38f, color, DialDraw.alpha(0xffffda8c, alpha * .92f), progress * .48f);
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, lPalm, to.subtract(spread), camera,
                            .24, .54, -.38, -age * .36f, DialDraw.alpha(0xffffc878, alpha * .88f), color, progress * .48f);
                    VfxPrimitives.orientedAstrolabeRingEroded(pose, buffers, originMid.add(fwd.scale(.35)), fwd, camera,
                            .35 + progress * .55, .18, -age * .24, 24, color, progress * .45f);
                }
            }
            case WILDMUTT -> {
                if (!secondary && !carni) {
                    if (cue.beat() == 0) {
                        double maxR = star ? 23.0 : 19.0;
                        VfxPrimitives.lusterRingEroded(pose, buffers, from.add(0, .06, 0),
                                .8 + progress * maxR, star ? .64 : .42, .16 * (1 - progress), 0, TAU, 56, color, progress * .45f);
                    } else {
                        VfxPrimitives.curvedEnergyWhip(pose, buffers, to, from, camera, .10, .04, .32, age * .32f,
                                color, DialDraw.alpha(0xffffe6b2, alpha), progress * .4f);
                    }
                } else {
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, from.add(0, -.35, 0), to, camera,
                            star ? .26 : .19, .06, .24, age * .35f, color, DialDraw.alpha(GOLD, alpha * .86f), progress * .5f);
                    for (int claw = -1; claw <= 1; claw++) {
                        VfxPrimitives.lusterSlashArcEroded(pose, buffers, to.add(0, claw * .16, 0), dir,
                                cue.hitEntity() ? .96 : .72, .22, 34f + claw * 9f, -.78f, 1.56f, color, progress * .52f);
                    }
                }
            }
            case DIAMONDHEAD -> {
                if (!secondary && !carni && cue.fallback()) {
                    VfxPrimitives.voronoiShatterCrown(pose, buffers, to, new Vec3(0, 1, 0), camera,
                            .65 + progress * .45, .52 * (1f - progress * .4f), age * .14f, color, progress * .35f);
                } else if (!secondary && !carni) {
                    // 3D Faceted Hexagonal Crystal Spire Cluster!
                    float grow = Math.min(1f, progress * 2.1f);
                    for (int pillar = -2; pillar <= 2; pillar++) {
                        Vec3 base = to.add(right.scale(pillar * .48)).add(0, -.48, 0);
                        Vec3 tilt = new Vec3(right.x * pillar * .14, 1.0, right.z * pillar * .14);
                        double h = (pillar == 0 ? 2.45 : Math.abs(pillar) == 1 ? 1.85 : 1.25) * grow;
                        VfxPrimitives.hexCrystalSpire(pose, buffers, base, tilt, pillar == 0 ? .34 : .25, h,
                                color, progress * .42f);
                    }
                    VfxPrimitives.voronoiShatterCrown(pose, buffers, to.add(0, -.42, 0), new Vec3(0, 1, 0), camera,
                            .62 + progress * 1.65, .42 * (1f - progress), age * .10f, color, progress * .42f);
                } else {
                    Vec3 rPalm = attached(cue, "grip_r", partial);
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, rPalm, to, camera, .18, .04, .22, age * .4f,
                            color, DialDraw.alpha(0xffe8ffff, alpha), progress * .5f);
                    if (age < 8) {
                        VfxPrimitives.lusterImpactBurst(pose, buffers, rPalm.add(fwd.scale(.3)), camera, .65, age / 8f, color);
                    }
                }
            }
            case XLR8 -> {
                if (!secondary) {
                    Vec3 side = right.scale(.25);
                    VfxPrimitives.lusterRibbonEroded(pose, buffers, from.add(side).add(0, -1.05, 0), to.add(side).add(0, -1.05, 0),
                            camera, .18, .06, color, 1.8f, progress * .45f, true);
                    VfxPrimitives.lusterRibbonEroded(pose, buffers, from.subtract(side).add(0, -1.05, 0), to.subtract(side).add(0, -1.05, 0),
                            camera, .18, .06, color, 1.8f, progress * .45f, true);
                    VfxPrimitives.bowShockWave(pose, buffers, to.add(0, -.45, 0), fwd, right, camera,
                            star ? 1.25 : .98, .45, color);
                } else if (cue.fallback()) {
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, from.add(0, -1, 0), to.add(0, .15, 0), camera,
                            .18, .04, .24, age * .35f, color, DialDraw.alpha(SILVER, alpha), progress * .5f);
                } else {
                    double slowRadius = cue.linked() ? (star ? 3.1 : 2.1) : (star ? 2.1 : 1.4);
                    VfxPrimitives.voronoiShatterCrown(pose, buffers, to.add(0, .06, 0), new Vec3(0, 1, 0), camera,
                            .4 + progress * slowRadius, .36 * (1 - progress), age * .28f, color, progress * .45f);
                }
            }
            case GREY_MATTER -> {
                if (!secondary && cue.fallback()) {
                    VfxPrimitives.lusterRingEroded(pose, buffers, to.add(0, .055, 0), .24 + .44 * progress,
                            .14, .05 * (1 - progress), 0, TAU, 22, color, progress * .4f);
                } else if (!secondary && !carni) {
                    for (int b = -1; b <= 1; b++) {
                        Vec3 start = to.add(right.scale(b * .22)).add(0, -.35, 0);
                        Vec3 top = to.add(right.scale(b * .14)).add(0, .48 * Math.min(1f, progress * 1.6f), 0);
                        VfxPrimitives.lusterRibbonEroded(pose, buffers, start, top, camera, .08, .025, color, 1.2f, progress * .4f, true);
                    }
                } else {
                    // Galvanic Acupressure Nerve-Pin Whip + Neural Fracture Ring!
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, from, to, camera, .15, .04, .32, age * .44f,
                            color, DialDraw.alpha(0xffe8fff6, alpha), progress * .45f);
                    if (cue.hitEntity()) {
                        VfxPrimitives.orientedAstrolabeRingEroded(pose, buffers, to, fwd, camera,
                                .28 + progress * .72, .16, -age * .28, 24, color, progress * .45f);
                    }
                }
            }
            case STINKFLY -> {
                if (secondary || carni) {
                    Vec3 wingSpan = right.scale(.60);
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, from.add(wingSpan), to.add(wingSpan.scale(.32)),
                            camera, .20, .045, .25, age * .35f, color, DialDraw.alpha(0xffe8ffbe, alpha), progress * .48f);
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, from.subtract(wingSpan), to.subtract(wingSpan.scale(.32)),
                            camera, .20, .045, -.25, -age * .35f, color, DialDraw.alpha(0xffe8ffbe, alpha), progress * .48f);
                    VfxPrimitives.bowShockWave(pose, buffers, to, fwd, right, camera, .95, .36, color);
                }
            }
            case RIPJAWS -> {
                if (!secondary) {
                    VfxPrimitives.lusterSlashArcEroded(pose, buffers, to, dir.scale(-1), 1.20, .38, 72f, -.88f, 1.76f, color, progress * .45f);
                    VfxPrimitives.lusterSlashArcEroded(pose, buffers, to, dir.scale(-1), 1.20, .38, -72f, -.88f, 1.76f, color, progress * .45f);
                    if (age < 9) {
                        VfxPrimitives.lusterImpactBurst(pose, buffers, to, camera, 1.15, age / 9f, color);
                    }
                } else if (cue.beat() == 1 && cue.linked()) {
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, from, to, camera, .18, .06, .36, age * .38f,
                            color, DialDraw.alpha(0xffe0fbff, alpha), progress * .45f);
                } else {
                    Vec3 ortho = right.scale(.34);
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, from.add(ortho), to.subtract(ortho.scale(.4)),
                            camera, .22, .065, .32, age * .38f, color, DialDraw.alpha(0xffe0fbff, alpha), progress * .45f);
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, from.subtract(ortho), to.add(ortho.scale(.4)),
                            camera, .22, .065, -.32, -age * .38f, color, DialDraw.alpha(0xffe0fbff, alpha), progress * .45f);
                    VfxPrimitives.orientedAstrolabeRingEroded(pose, buffers, from.lerp(to, .65), fwd, camera,
                            .55 + progress * .45, .20, age * .28, 22, color, progress * .45f);
                }
            }
            case UPGRADE -> {
                if (!secondary && !carni && cue.fallback()) {
                    Vec3 lens = attached(cue, "optic", partial);
                    VfxPrimitives.orientedAstrolabeRingEroded(pose, buffers, lens, from.subtract(to), camera,
                            .32 + progress * .36, .14, age * .08f, 24, color, progress * .35f);
                } else if (!secondary && !carni) {
                    // Galvanic Construct Neural Hijack / Lever Link Multi-Strand Whip!
                    Vec3 lens = attached(cue, "optic", partial);
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, lens, to, camera,
                            star ? .18 : .13, .045, .44, age * .42f, color, DialDraw.alpha(0xffe6fff4, alpha), progress * .45f);
                    VfxPrimitives.orientedAstrolabeRingEroded(pose, buffers, to, fwd, camera,
                            .28 + progress * .68, .16, age * .26, 24, color, progress * .42f);
                } else {
                    Vec3 lens = attached(cue, "optic", partial);
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, lens, to, camera,
                            star ? .28 : .19, .055, .36, age * .48f, color,
                            DialDraw.alpha(0xffeafff6, alpha * .96f), progress * .48f);
                    VfxPrimitives.orientedAstrolabeRingEroded(pose, buffers, lens.add(fwd.scale(.18)), fwd, camera,
                            .22 + progress * .26, .10, age * .25, 20, color, progress * .4f);
                    if (cue.hitEntity()) {
                        VfxPrimitives.voronoiShatterCrown(pose, buffers, to, fwd, camera,
                                .35 + progress * (star ? 1.35 : .95), .45 * (1f - progress), age * .24f, color, progress * .5f);
                    }
                }
            }
            case GHOSTFREAK -> {
                if (!secondary && !carni) {
                    if (cue.hitEntity()) {
                        // Ectonurite Possession Curved Tendrils!
                        VfxPrimitives.curvedEnergyWhip(pose, buffers, from.add(0, -.22, 0), to, camera,
                                .20, .05, .52, age * .36f, color, DialDraw.alpha(0xfff2dcff, alpha), progress * .45f);
                    }
                    for (int ringIdx = 0; ringIdx < 3; ringIdx++) {
                        double h = -.9 + ringIdx * .55 + progress * .45;
                        VfxPrimitives.lusterRingEroded(pose, buffers, from.add(0, h, 0), .35 + progress * .78,
                                .26, .18 * (1 - progress), age * .16 + ringIdx, age * .16 + ringIdx + 4.4, 24, color, progress * .45f);
                    }
                } else if (cue.beat() == 1) {
                    VfxPrimitives.voronoiShatterCrown(pose, buffers, from, fwd, camera,
                            Math.max(.18, .98 * (1 - progress)), .36 * (1 - progress), -age * .32f, color, progress * .45f);
                    VfxPrimitives.voronoiShatterCrown(pose, buffers, to, fwd, camera,
                            .26 + progress * 1.35, .48 * (1 - progress), age * .32f, color, progress * .45f);
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, from, to, camera, .20, .24, .46, age * .34f,
                            color, DialDraw.alpha(0xfff4e0ff, alpha), progress * .52f);
                } else if (cue.fallback() || carni && cue.hitEntity()) {
                    VfxPrimitives.lusterSlashArcEroded(pose, buffers, to, dir, .78 + progress * .62,
                            .28, -34, -.72f, 1.56f, color, progress * .48f);
                }
            }
            case ALIEN_X -> {
                if (!secondary && !carni) {
                    double maxR = TemporalEffects.RANGE;
                    double r = 1.2 + (maxR - 1.2) * Math.pow(progress, .62);
                    VfxPrimitives.voronoiShatterCrown(pose, buffers, to.add(0, .12, 0), new Vec3(0, 1, 0), camera,
                            Math.min(18.0, r), 1.45 * (1 - progress * .55), age * .09f, color, progress * .38f);
                    VfxPrimitives.astrolabeRingEroded(pose, buffers, to.add(0, .55, 0), r * .68, .55,
                            -age * .11, -age * .11 + TAU, 48, DialDraw.alpha(SILVER, alpha * .85f), progress * .3f);
                } else if (carni && cue.beat() == 0) {
                    inwardArcs(pose, buffers, camera, to, age, 5.6 * (1f - progress * .68f), alpha, true);
                } else {
                    double maxR = carni ? 8.0 : 16.0;
                    double r = .65 + (maxR - .65) * Math.pow(progress, .58);
                    VfxPrimitives.voronoiShatterCrown(pose, buffers, to.add(0, .08, 0), new Vec3(0, 1, 0), camera,
                            r, (carni ? .65 : 1.35) * (1 - progress), age * .14f, color, progress * .44f);
                    if (!carni) {
                        VfxPrimitives.astrolabeRingEroded(pose, buffers, to.add(0, .35, 0), r * .72, .62,
                                age * .16, age * .16 + TAU, 42, DialDraw.alpha(VIOLET, alpha * .9f), progress * .35f);
                        VfxPrimitives.curvedEnergyWhip(pose, buffers, to, to.add(0, 4.6 * (1 - progress * .4f), 0), camera,
                                .92 * (1 - progress), .14, .42, age * .35f,
                                DialDraw.alpha(0xfff59e56, alpha * .94f), DialDraw.alpha(VIOLET, alpha), progress * .45f);
                        VfxPrimitives.lusterImpactBurst(pose, buffers, to.add(0, .4, 0), camera, 4.4, progress,
                                DialDraw.alpha(0xffffca78, alpha));
                    } else {
                        VfxPrimitives.orientedAstrolabeRingEroded(pose, buffers, to, dir, camera,
                                r * .72, .42, -.17 * age, 32, DialDraw.alpha(0xffc64e82, alpha), progress * .4f);
                    }
                }
            }
        }

        if (cue.hitEntity() && age <= 10 && to.distanceToSqr(camera) <= 48 * 48) {
            VfxPrimitives.lusterImpactBurst(pose, buffers, to, camera, star ? 1.35 : 1.02, age / 10f,
                    DialDraw.alpha(baseTint, .94f * (1f - age / 10f)));
        }
    }

    private static Vec3 attached(WristNetwork.Cue cue, String bone, float partial) {
        var level = Minecraft.getInstance().level;
        var player = level == null ? null : level.getPlayerByUUID(cue.actor());
        Vec3 at = WatchRender.skillAnchor(player, ClientState.get(player), bone, partial, cue.from());
        return at.distanceToSqr(cue.from()) < 25 ? at : cue.from();
    }

    private static void fistTrails(PoseStack pose, MultiBufferSource buffers, WristNetwork.Cue cue, float partial,
                                   Vec3 camera, Vec3 contact, float alpha, int tint, boolean slam, boolean carni,
                                   boolean star, float fade) {
        if (cue.from().distanceToSqr(camera) > 48 * 48) return;
        String[] sides = slam || !carni && cue.beat() >= 2 ? new String[]{"r", "l"}
                : new String[]{carni ? (cue.beat() == 0 ? "r" : "l") : (cue.beat() == 0 ? "l" : "r")};
        for (String side : sides) {
            Vec3 top = attached(cue, "grip_" + side, partial);
            Vec3 lower = attached(cue, "fist_extra_" + side, partial);
            Vec3 tip = slam ? contact.add(0, .08, 0) : contact;
            int color = DialDraw.alpha(tint, alpha * .78f);
            VfxPrimitives.lusterRibbonEroded(pose, buffers, top, tip, camera, .18, .038, color, 1.3f, fade * .55f, true);
            VfxPrimitives.lusterRibbonEroded(pose, buffers, lower, tip.add(0, slam ? 0 : -.31, 0), camera,
                    .14, .028, color, 1.3f, fade * .55f, true);
        }
    }

    private static void inwardArcs(PoseStack pose, MultiBufferSource buffers, Vec3 camera, Vec3 core, float age,
                                   double radius, float alpha, boolean corrupted) {
        int arms = ClientState.reduced() || core.distanceToSqr(camera) > 52 * 52 ? 2 : 5;
        for (int i = 0; i < arms; i++) {
            var strand = new ArrayList<Vec3>(8);
            double turn = i * TAU / arms + age * .13;
            for (int segment = 0; segment < 8; segment++) {
                float p = segment / 7f;
                double distance = radius * (1 - .89 * p);
                double angle = turn + p * 2.65;
                strand.add(core.add(Math.cos(angle) * distance, (i % 2 == 0 ? .38 : -.30) * radius * (1 - p),
                        Math.sin(angle) * distance));
            }
            int hue = corrupted ? 0xffa34265 : i % 2 == 0 ? 0xff7959bc : 0xff527aa5;
            VfxPrimitives.lusterPolyline(pose, buffers, strand, camera, .32, DialDraw.alpha(hue, alpha * .72f));
        }
    }

    private static void drawSkillWarning(PoseStack pose, MultiBufferSource buffers, Player caster,
                                         ClientState.View view, float partial, Vec3 camera) {
        var motion = view.performance(partial);
        if (motion == null || view.now(partial) >= motion.impact()) return;
        double age = view.now(partial) - motion.start();
        Kind kind = view.s().watch();
        boolean star = kind == Kind.CELESTIAL;
        Form form = view.s().chosen();
        boolean secondary = motion.action() >= Abilities.SECONDARY && motion.action() < Abilities.AUTOMATED;

        if (motion.action() == Abilities.AUTOMATED + Form.UPGRADE.ordinal()) {
            float gather = Motion.part((float) age, 0, 6);
            Vec3 at = WatchRender.skillAnchor(caster, view, "optic", partial, motion.origin());
            for (int i = 0; i < 4; i++) {
                float lit = Motion.part((float) age, i * 1.15f, i * 1.15f + 1.4f);
                if (lit <= .01f) continue;
                Vec3 from = WatchRender.skillAnchor(caster, view, "satellite_optic_" + i, partial, at);
                VfxPrimitives.lusterRibbonEroded(pose, buffers, from, at, camera, .075, .04,
                        DialDraw.alpha(0xff98c44e, lit * (.48f + .47f * gather)), 1.0f, .05f, true);
            }
            return;
        }

        if (form == Form.HEATBLAST) {
            float gather = Motion.part((float) age, 0, 4);
            int fireCol = DialDraw.alpha(star ? 0xffe894ff : 0xffff9e44, .86f);
            String[] palms = secondary ? new String[]{"grip_r", "grip_l"} : new String[]{"grip_r"};
            for (String bone : palms) {
                Vec3 palm = WatchRender.skillAnchor(caster, view, bone, partial, motion.origin());
                for (int i = 0; i < 3; i++) {
                    double a = age * .52 + TAU * i / 3;
                    Vec3 outer = palm.add(Math.cos(a) * (.58 * (1 - gather * .75)), .20 * Math.sin(a * 2),
                            Math.sin(a) * (.58 * (1 - gather * .75)));
                    VfxPrimitives.lusterRibbonEroded(pose, buffers, outer, palm, camera, .085, .028, fireCol, 1.0f, .1f, true);
                }
            }
            return;
        }

        if (form == Form.FOUR_ARMS && secondary) {
            float gather = Motion.part((float) age, 0, 4);
            int slamCol = DialDraw.alpha(star ? 0xffbaa8ff : 0xfff07a48, .85f);
            Vec3 bodyPos = caster.getPosition(partial);
            Vec3 overhead = bodyPos.add(0, 2.35 - .55 * gather, 0).add(caster.getLookAngle().multiply(1, 0, 1).scale(.65));
            for (int i = 0; i < 4; i++) {
                double a = age * .35 + TAU * i / 4;
                Vec3 outer = bodyPos.add(Math.cos(a) * 1.38, .35 + (i % 2) * .72, Math.sin(a) * 1.38);
                VfxPrimitives.lusterRibbonEroded(pose, buffers, outer, overhead, camera, .13, .045, slamCol, 1.2f, .12f, true);
            }
            VfxPrimitives.astrolabeRingEroded(pose, buffers, motion.focus().add(0, .05, 0),
                    (star ? 5.0 : 4.2) * (.35 + .65 * gather), .24, age * .18, age * .18 + TAU, 36,
                    DialDraw.alpha(star ? VIOLET : 0xfff08a52, .56f), .1f);
            return;
        }

        if (form == Form.DIAMONDHEAD) {
            float gather = Motion.part((float) age, 0, 4);
            int crysCol = DialDraw.alpha(star ? 0xff9cf0ff : 0xff82f2eb, .82f);
            Vec3 focus = secondary ? WatchRender.skillAnchor(caster, view, "grip_r", partial, motion.origin())
                    : motion.focus().add(0, .08, 0);
            Vec3 axis = secondary ? caster.getLookAngle() : new Vec3(0, 1, 0);
            VfxPrimitives.orientedAstrolabeRingEroded(pose, buffers, focus, axis, camera,
                    (secondary ? .38 : .85) * (.45 + .55 * gather), .16, age * .26, 24, crysCol, .08f);
            return;
        }

        if (form == Form.UPGRADE && secondary) {
            float gather = Motion.part((float) age, 0, 4);
            Vec3 lens = WatchRender.skillAnchor(caster, view, "optic", partial, motion.origin());
            VfxPrimitives.orientedAstrolabeRingEroded(pose, buffers, lens, caster.getLookAngle(), camera,
                    .42 * (1.15 - .55 * gather), .12, -age * .32, 22,
                    DialDraw.alpha(star ? 0xff92ffdc : 0xff7ef8ae, .88f), .06f);
            return;
        }

        if (form != Form.ALIEN_X) return;
        boolean stasis = motion.action() < Abilities.SECONDARY;
        boolean corrupted = motion.action() >= Abilities.AUTOMATED;
        boolean singularity = !corrupted && stasis && motion.style() == 1;
        boolean extinction = !corrupted && !stasis && motion.style() == 1;
        double radius = singularity ? 32 : extinction ? 3.8 : stasis ? TemporalEffects.RANGE : (corrupted ? 8 : 16);
        float fade = ClientState.reduced() ? .65f : .55f + .30f * Motion.part((float) age, 0, 4);
        int color = DialDraw.alpha(corrupted ? 0xffa14265 : stasis ? 0xff7778dd : 0xffd89651, fade);
        Vec3 at = motion.focus().add(0, .12, 0);
        if (extinction) {
            Vec3 palm = WatchRender.skillAnchor(caster, view, "grip_r", partial, motion.origin());
            VfxPrimitives.curvedEnergyWhip(pose, buffers, palm, motion.focus(), camera,
                    .085, .05, .24, (float) age * .35f, DialDraw.alpha(0xffb472e8, fade * .75f),
                    DialDraw.alpha(SILVER, fade * .6f), .08f);
            return;
        }
        double advance = ClientState.reduced() ? 0 : age * .11;
        for (int i = 0; i < 3; i++) {
            double start = TAU * i / 3 + advance;
            VfxPrimitives.astrolabeRingEroded(pose, buffers, at, radius, .42,
                    start, start + (corrupted ? 1.56 : 2.03), 28, color, .1f);
        }
        if (!stasis) {
            float gather = Motion.part((float) age, 0, 12);
            VfxPrimitives.voronoiShatterCrown(pose, buffers, at, new Vec3(0, 1, 0), camera,
                    Math.max(.35, 2.4 * (1 - gather * .65)), .52 * (1 - gather * .3), (float) (-advance * 1.5), color, .12f);
            if (!corrupted) {
                for (int i = 0; i < 4; i++) {
                    double a = TAU * i / 4 - advance * 1.4;
                    Vec3 outer = at.add(Math.cos(a) * (4.8 * (1 - gather * .72)), 1.6 * (1 - gather * .6),
                            Math.sin(a) * (4.8 * (1 - gather * .72)));
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, outer, at.add(0, .25, 0), camera, .16, .045, .32,
                            (float) (age * .35 + i), DialDraw.alpha(GOLD, .86f), DialDraw.alpha(VIOLET, .75f), .1f);
                }
            }
        }
    }

    private static void drawTransition(PoseStack pose, MultiBufferSource buffers, Player p, ClientState.View view,
                                       float partial, Vec3 camera, boolean firstPerson) {
        Vec3 at = p.getPosition(partial);
        float time = view.age(partial);
        Kind kind = view.s().watch();
        float progress = view.progress(partial);
        float pulse = Mth.sin(progress * (float) Math.PI);
        int baseColor = view.s().misfire() && time < 13 ? 0xffff4f46 : DialDraw.glow(kind);
        int color = DialDraw.alpha(baseColor, .32f + .64f * pulse);
        boolean star = kind == Kind.CELESTIAL;

        int rings = ClientState.reduced() ? 1 : 3;
        for (int i = 0; i < rings; i++) {
            double radius = (star ? .62 : .46) + .24 * i + .56 * pulse;
            float h = (.22f + .52f * i) + progress * .72f;
            double a = time * (star ? .14 : .20) + i * 2.09;
            VfxPrimitives.lusterRingEroded(pose, buffers, at.add(0, h, 0), radius, star ? .28 : .18, .10 + i * .14,
                    a, a + (kind == Kind.CARNITRIX ? 2.9 : star ? 5.2 : 4.6), 26, color, .14f);
        }

        if (!ClientState.reduced() && view.s().phase() == Stage.ARMING) {
            if (kind == Kind.CLASSIC && time >= 5) {
                float beamLife = Mth.sin(Motion.part(time, 5, 18) * (float) Math.PI);
                int beamCol = DialDraw.alpha(baseColor, beamLife * .90f);
                VfxPrimitives.curvedEnergyWhip(pose, buffers, at.add(0, .05, 0), at.add(0, 2.85, 0), camera,
                        .32 * beamLife, .14 * beamLife, .22, time * .45f,
                        DialDraw.alpha(0xff52ef6c, beamLife * .82f), beamCol, .12f);
                for (int strand = 0; strand < 2; strand++) {
                    var helix = new ArrayList<Vec3>(10);
                    double phase = strand * Math.PI + time * .48;
                    for (int k = 0; k < 10; k++) {
                        float f = k / 9f;
                        double r = .54 * (1 - f * .30);
                        double ang = phase + f * 5.4;
                        helix.add(at.add(Math.cos(ang) * r, .15 + f * 2.40, Math.sin(ang) * r));
                    }
                    VfxPrimitives.lusterPolyline(pose, buffers, helix, camera, .085, beamCol);
                }
            } else if (kind == Kind.CELESTIAL) {
                float cosmicAlpha = pulse * .90f;
                int starCol = DialDraw.alpha(0xffbaa8ff, cosmicAlpha);
                VfxPrimitives.voronoiShatterCrown(pose, buffers, at.add(0, .06, 0), new Vec3(0, 1, 0), camera,
                        .85 + 1.65 * pulse, .68 * pulse, -time * .09f, starCol, .12f);
                if (time >= 31) {
                    float surge = Motion.part(time, 31, 40) * (1 - Motion.part(time, 40, 48));
                    VfxPrimitives.curvedEnergyWhip(pose, buffers, at.add(0, .05, 0), at.add(0, 3.6, 0), camera,
                            .52 * surge, .22 * surge, .26, time * .42f,
                            DialDraw.alpha(SILVER, surge * .94f), DialDraw.alpha(GOLD, surge * .86f), .10f);
                }
            } else if (kind == Kind.CARNITRIX && time >= 6) {
                float inward = 1f - Motion.part(time, 6, 24);
                int redCol = DialDraw.alpha(0xffff4848, pulse * .88f);
                VfxPrimitives.voronoiShatterCrown(pose, buffers, at.add(0, .12, 0), new Vec3(0, 1, 0), camera,
                        .35 + 1.45 * inward, .42 * pulse, time * .32f, redCol, .18f);
            }
        }

        if (firstPerson) return;
        if (star && view.s().phase() == Stage.ARMING) {
            worldPersona(pose, buffers, at, p.getYRot(), time, "serena", -1, 10, 0xff8f78ce);
            worldPersona(pose, buffers, at, p.getYRot(), time, "bellicus", 1, 16, 0xffd78a4b);
        }
        float start = kind == Kind.CELESTIAL ? 27 : kind == Kind.CARNITRIX ? 13 : 8;
        Form chosen = view.s().chosen();
        if (chosen != null && view.s().phase() == Stage.ARMING && time > start) {
            float visible = Motion.part(time, start, kind.windup);
            var rig = Rig.get(chosen.asset(kind));
            if (rig.starfield) StarfieldShader.clock(partial);
            pose.pushPose();
            pose.translate(at.x, at.y + .1 * (1 - visible), at.z);
            pose.mulPose(Axis.YP.rotationDegrees(180 - p.getYRot()));
            float scale = chosen.dimensions(kind).height() * 16 / rig.height * (.76f + .24f * visible);
            rig.draw(pose, buffers, rig.materialize(visible), scale, DialDraw.alpha(-1, visible * .82f),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    private static void worldPersona(PoseStack pose, MultiBufferSource buffers, Vec3 at, float yaw, float tick,
                                     String name, int side, float start, int color) {
        float alpha = Motion.part(tick, start, start + 4) * (1 - Motion.part(tick, 30, 39));
        if (alpha <= .02f) return;
        double heading = Math.toRadians(yaw);
        double offset = side * (1.54 - .32 * Motion.part(tick, 27, 37));
        var rig = Rig.get(name);
        pose.pushPose();
        pose.translate(at.x + Math.cos(heading) * offset, at.y + 1.42,
                at.z + Math.sin(heading) * offset);
        pose.mulPose(Axis.YP.rotationDegrees(180 - yaw - side * 19));
        rig.draw(pose, buffers, personaPose(rig, name, tick), 16 / rig.height * .89f,
                DialDraw.alpha(color, alpha * .92f), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}

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
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
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

            // XLR8 & X-XLR8 sprinting / dashing slipstream & afterimages
            if (form == Form.XLR8 && (kind == Kind.CLASSIC || kind == Kind.CELESTIAL)
                    && (player.isSprinting() || player.getDeltaMovement().horizontalDistanceSqr() > .09) && now % 2 == 0) {
                var trail = TRAILS.computeIfAbsent(player.getUUID(), key -> new ArrayDeque<>());
                if (trail.isEmpty() || trail.getLast().at.distanceToSqr(player.position()) > .018) {
                    trail.addLast(new Afterimage(player.position(), player.yBodyRot, now, kind == Kind.CELESTIAL));
                    while (trail.size() > 8) trail.removeFirst();
                }
            }

            // X-Grey Matter slow-falling cosmic wake
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

    static void hud(GuiGraphics g, DeltaTracker tracker) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator() || WristScreen.isOpen()) return;
        AscalonRenderer.renderHud(g, tracker);
        var view = ClientState.self();
        if (view == null) return;
        var snapshot = view.s();
        var phase = snapshot.phase();
        if (phase == Stage.LOCKING) resistance(g, view, tracker);
        feedback(g);
        if (phase == Stage.ARMING) {
            switch (snapshot.watch()) {
                case CELESTIAL -> celestials(g, view, tracker);
                case CARNITRIX -> carniLock(g, view, tracker);
                case CLASSIC -> classicShift(g, view, tracker);
            }
        }
        boolean left = mc.player.getMainArm() == HumanoidArm.RIGHT;
        boolean warning = WatchRender.timeout(view);
        int tint = warning || phase == Stage.LOCKING ? 0xffff6764 : phase == Stage.COOLING ? 0xffffb78a : DialDraw.glow(snapshot.watch());
        Component name = phase == Stage.ACTIVE || phase == Stage.REVERTING
                ? Component.translatable(snapshot.chosen().translationKey(snapshot.watch()))
                : Component.translatable("watch.aca_omnitrix." + phase.name().toLowerCase(Locale.ROOT));
        String time = phase == Stage.ACTIVE || phase == Stage.COOLING ? seconds(view.ticksLeft()) : "";
        int width = 35 + mc.font.width(name) + mc.font.width(time) + (time.isEmpty() ? 0 : 8);
        width = Math.min(g.guiWidth() - 20, width);
        int x = left ? Math.max(8, g.guiWidth() / 2 - 93 - width - 11) : Math.min(g.guiWidth() - width - 8, g.guiWidth() / 2 + 105);
        int y = g.guiHeight() - 63;
        if (g.guiWidth() < 470) {
            x = left ? 8 : g.guiWidth() - width - 8;
            y = g.guiHeight() - 79;
        }
        if (snapshot.controlled() || warning) y -= 17;
        g.fill(x, y, x + width, y + 22, 0xdc1d2530);
        g.fill(x, y, x + width, y + 1, DialDraw.alpha(tint, .40f));
        float fill = phase == Stage.ACTIVE ? 1 - view.progress(0) : phase == Stage.COOLING ? view.progress(0) : 1;
        for (int notch = 0; notch < 12; notch++) {
            float opacity = notch / 12f < fill ? .88f : .17f;
            int a = x + 2 + notch * (width - 4) / 12, b = x + 2 + (notch + 1) * (width - 4) / 12 - 1;
            g.fill(a, y + 19, b, y + 21, DialDraw.alpha(tint, opacity));
        }
        icon(g, x + 4, y + 5, tint, phase);
        g.drawString(mc.font, name, x + 23, y + 5, 0xffe4eaed, false);
        if (!time.isEmpty()) g.drawString(mc.font, time, x + width - mc.font.width(time) - 4, y + 5, tint, false);
        if (phase == Stage.ACTIVE && snapshot.chosen() != null && snapshot.maxBarrier() > 0) {
            float barFrac = (float) Math.clamp(snapshot.barrier() / snapshot.maxBarrier(), 0.0, 1.0);
            int barCol = snapshot.chosen() == Form.ALIEN_X && snapshot.watch() == Kind.CELESTIAL
                    ? 0xffcbb6ff : tint;
            g.fill(x + 2, y - 4, x + width - 2, y - 1, 0xa010161e);
            int fillW = Math.max(2, Math.round((width - 4) * barFrac));
            g.fill(x + 2, y - 4, x + 2 + fillW, y - 1, DialDraw.alpha(barCol, .92f));
        }
        if (snapshot.controlled()) {
            Component takeover = Component.translatable("watch.aca_omnitrix.possessed");
            g.drawString(mc.font, takeover, x + 4, y + 24, GOLD, true);
        } else if (warning) {
            g.drawString(mc.font, Component.translatable("watch.aca_omnitrix.timeout"), x + 4, y + 24, tint, true);
        }
        double now = view.now(0);
        boolean alien = snapshot.chosen() == Form.ALIEN_X && snapshot.watch() == Kind.CELESTIAL;
        if (phase == Stage.ACTIVE && !snapshot.controlled()
                && (snapshot.skillHintUntil() > now || snapshot.linkUntil() > now
                    || alien && (mc.player.isShiftKeyDown() || snapshot.flickAt() > now))) {
            var leftKey = OmnitrixClient.PRIMARY.getTranslatedKeyMessage().getString();
            var rightKey = OmnitrixClient.SECONDARY.getTranslatedKeyMessage().getString();
            boolean alternate = alien && mc.player.isShiftKeyDown();
            long leftAt = alternate ? snapshot.shiftPrimaryAt() : snapshot.primaryAt();
            long rightAt = alternate ? snapshot.shiftSecondaryAt() : snapshot.secondaryAt();
            var hint = Component.translatable("watch.aca_omnitrix.skills",
                    (alternate ? "⇧" : "") + leftKey, skillTime(leftAt - now),
                    (alternate ? "⇧" : "") + rightKey, skillTime(rightAt - now));
            if (alien) hint.append("  ").append(OmnitrixClient.FLICK.getTranslatedKeyMessage()).append(" ")
                    .append(skillTime(snapshot.flickAt() - now));
            if (snapshot.linkUntil() > now) hint.append(" · ").append(Component.translatable("watch.aca_omnitrix.link_ready"));
            int hintWidth = mc.font.width(hint);
            int skillX = Math.max(8, Math.min(left ? x + width - hintWidth : x, g.guiWidth() - hintWidth - 10));
            int skillY = g.guiWidth() < 470 ? y - 26 : y - 15;
            g.fill(skillX - 2, skillY - 2, skillX + hintWidth + 3, skillY + 10, 0xde1d2530);
            g.drawString(mc.font, hint, skillX, skillY, 0xffe4eaed, false);
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

    private static void icon(GuiGraphics g, int x, int y, int color, Stage stage) {
        if (stage == Stage.COOLING) {
            g.fill(x + 4, y + 2, x + 14, y + 5, color);
            g.fill(x + 5, y + 5, x + 13, y + 9, DialDraw.alpha(color, .5f));
            g.fill(x + 4, y + 9, x + 14, y + 11, color);
        } else if (stage == Stage.ACTIVE || stage == Stage.REVERTING) {
            g.fill(x + 2, y + 5, x + 14, y + 7, color);
            g.fill(x + 7, y + 1, x + 10, y + 11, color);
        } else {
            g.fill(x + 2, y + 2, x + 14, y + 4, color);
            g.fill(x + 5, y + 5, x + 11, y + 7, color);
            g.fill(x + 2, y + 8, x + 14, y + 10, color);
        }
    }

    private static void classicShift(GuiGraphics g, ClientState.View view, DeltaTracker tracker) {
        float t = view.age(tracker.getGameTimeDeltaPartialTick(false));
        float open = Motion.part(t, 4, 9) * (1 - Motion.part(t, 13, 18));
        if (open < .02) return;
        int x = g.guiWidth() / 2, y = g.guiHeight() - 112;
        int pulseColor = view.s().misfire() && t < 14 ? 0xffff4f46 : DialDraw.glow(Kind.CLASSIC);
        if (!ClientState.reduced()) {
            for (int i = 0; i < 4; i++) {
                float rIn = 12 + i * 9 + t * 1.6f;
                DialDraw.arc(g, x, y, rIn, rIn + 1.6f, -2.8f + i * .12f, -.34f - i * .12f,
                        DialDraw.alpha(pulseColor, open * (.85f - i * .16f)));
            }
            // Omnitrix hourglass flash pulse on screen center at impact (t = 6..12)
            float flash = Motion.part(t, 5, 7) * (1 - Motion.part(t, 7, 13));
            if (flash > .02f) {
                g.fill(0, 0, g.guiWidth(), g.guiHeight(), DialDraw.alpha(pulseColor, flash * .18f));
            }
        }
        caption(g, view.s().misfire() && t < 14 ? "watch.aca_omnitrix.misfire" : view.s().chosen().translationKey(Kind.CLASSIC),
                g.guiWidth() / 2, g.guiHeight() - 105, pulseColor);
    }

    private static void resistance(GuiGraphics g, ClientState.View view, DeltaTracker tracker) {
        var mc = Minecraft.getInstance();
        float time = view.age(tracker.getGameTimeDeltaPartialTick(false));
        int x = g.guiWidth() / 2, y = (int) (g.guiHeight() * .60f);
        int red = 0xfffc8983;
        float jitter = ClientState.reduced() ? 0 : Mth.sin(time * 1.8f) * .23f;
        float angle = (float) Math.toRadians(ClientState.bearing(mc.player, view, 0)) - TAU / 4 + jitter;
        DialDraw.tick(g, x, y - 25, 10, 23, angle, red);
        DialDraw.arc(g, x, y - 25, 25, 26.5f, -TAU / 4, -TAU / 4 + TAU * (1 - view.progress(0)), red);
        Component message = Component.translatable("watch.aca_omnitrix.resist", mc.options.keyShift.getTranslatedKeyMessage());
        int width = Math.max(112, mc.font.width(message) + 18);
        int left = x - width / 2;
        g.fill(left, y + 4, left + width, y + 28, 0xe52d1821);
        g.drawCenteredString(mc.font, message, x, y + 8, 0xffffe3d7);
        for (int i = 0; i < WristService.RESIST_PRESSES; i++) {
            int from = left + 8 + i * (width - 16) / WristService.RESIST_PRESSES;
            int to = left + 6 + (i + 1) * (width - 16) / WristService.RESIST_PRESSES;
            g.fill(from, y + 21, to, y + 24, i < view.s().resistance() ? red : 0xff69404a);
        }
    }

    private static void feedback(GuiGraphics g) {
        var mc = Minecraft.getInstance();
        var effect = ClientState.effect(mc.player);
        if (effect == null) return;
        long age = mc.level.getGameTime() - effect.tick();
        int action = effect.cue().action();
        if (age < 25 && action == WristNetwork.FAILSAFE_CUE) {
            caption(g, "watch.aca_omnitrix.failsafe", g.guiWidth() / 2, g.guiHeight() - 106, 0xffc3fa89);
        } else if (age < 14 && (action == Form.ALIEN_X.ordinal()
                || action == Abilities.SECONDARY + Form.ALIEN_X.ordinal()
                || action == WristNetwork.SINGULARITY_CUE
                || action == WristNetwork.EXTINCTION_CUE
                || action == WristNetwork.REWRITE_CUE)) {
            float x = g.guiWidth() * .5f, y = g.guiHeight() * .43f;
            float opacity = ClientState.reduced() ? .4f : Math.min(1, 1.25f - age / 15f);
            float size = Math.min(55f, g.guiHeight() * .19f);
            float spread = Math.min(g.guiWidth() * .23f, 96f);
            boolean stasis = action == Form.ALIEN_X.ordinal() || action == WristNetwork.SINGULARITY_CUE;
            int color = action == WristNetwork.REWRITE_CUE ? SILVER : stasis ? VIOLET : GOLD;
            persona(g, stasis ? "serena" : "bellicus", x + (stasis ? -spread : spread), y,
                    size, stasis ? 19 : -19, DialDraw.alpha(color, opacity), age + (stasis ? 10 : 17));
            String capKey = action == WristNetwork.SINGULARITY_CUE ? "motion_singularity"
                    : action == WristNetwork.EXTINCTION_CUE ? "motion_extinction"
                    : action == WristNetwork.REWRITE_CUE ? "motion_rewrite"
                    : stasis ? "motion_stasis" : "motion_starburst";
            caption(g, "watch.aca_omnitrix." + capKey,
                    (int) x, (int) (y + size * .65f), SILVER);
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
            DialDraw.arc(g, x, y, radius, radius + 1.8f, angle + .12f, angle + .65f, color);
            DialDraw.tick(g, x, y, radius - 12, radius, angle, color);
        }
        float bearing = ClientState.bearing(Minecraft.getInstance().player, view, tracker.getGameTimeDeltaPartialTick(false));
        float aim = (float) Math.toRadians(bearing) - TAU / 4;
        DialDraw.arc(g, x, y, radius + 5, radius + 8, aim - .12f, aim + .12f, GOLD);
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
        float spread = 105 * s;
        float press = Motion.part(t, 1, 9);
        float split = Motion.part(t, 9, 15);
        float second = Motion.part(t, 15, 21);
        float cut = Motion.part(t, 30, 39);
        float fade = 1 - Motion.part(t, 38, 47);
        float left = (ClientState.reduced() ? 0 : split * 12 * (1 - cut));
        float right = (ClientState.reduced() ? 0 : second * 12 * (1 - cut));
        int background = DialDraw.alpha(0xd5111825, press * fade * .72f);
        g.fillGradient(0, (int) (y - 104 * s), g.guiWidth(), (int) (y + 83 * s), DialDraw.alpha(background, .22f), background);
        g.fillGradient(0, (int) (y + 83 * s), g.guiWidth(), g.guiHeight(), background, DialDraw.alpha(background, .83f));
        for (int rail = 0; rail < 3; rail++) {
            float turn = -1.57f + rail * TAU / 3 + (ClientState.reduced() ? 0 : t * (.024f + rail * .008f));
            float radius = (25 + rail * 8 + cut * 36) * s;
            DialDraw.arc(g, x, y, radius, radius + (rail == 2 ? 2.4f : 1.3f) * s, turn, turn + 1.31f + cut * .25f,
                    DialDraw.alpha(rail == 0 ? SILVER : rail == 1 ? VIOLET : GOLD, fade * press * (rail == 0 ? 1 : 1 - cut)));
        }
        if (t >= 31 && t < 44 && !ClientState.reduced()) {
            float burstR = (16 + cut * 72) * s;
            DialDraw.arc(g, x, y, burstR, burstR + 2.8f * s, 0, TAU, DialDraw.alpha(SILVER, fade * (1 - cut * .6f) * .65f));
        }
        if (split > .01f && cut < 1) {
            float own = x - spread + left * s;
            DialDraw.edge(g, own + 17 * s, y + 10 * s, x - 34 * s, y + 2 * s, 2 * s, DialDraw.alpha(VIOLET, split * (1 - cut)));
            persona(g, "serena", own, y - 8 * s, 74 * s * split, 11 - right * .69f,
                    DialDraw.alpha(VIOLET, split * (1 - cut)), t);
        }
        if (second > .01f && cut < 1) {
            float own = x + spread - right * s;
            DialDraw.edge(g, own - 17 * s, y + 10 * s, x + 34 * s, y + 2 * s, 2 * s, DialDraw.alpha(GOLD, second * (1 - cut)));
            persona(g, "bellicus", own, y - 8 * s, 78 * s * second, -13 + left * .72f,
                    DialDraw.alpha(GOLD, second * (1 - cut)), t);
        }
        if (t >= 28) {
            for (int i = -1; i <= 1; i += 2) {
                float gap = 10 + cut * 15;
                DialDraw.edge(g, x - gap * s, y - i * gap * s, x + gap * s, y + i * gap * s, 1.7f * s, DialDraw.alpha(SILVER, fade * cut));
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

    private static void caption(GuiGraphics g, String key, int x, int y, int color, Object... arguments) {
        var font = Minecraft.getInstance().font;
        var text = Component.translatable(key, arguments);
        int half = font.width(text) / 2;
        g.fill(x - half - 7, y - 4, x + half + 8, y + 13, 0xd91c2433);
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
        var buffers = mc.renderBuffers().bufferSource();
        long now = mc.level.getGameTime();

        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);

        // 1. XLR8 & X-XLR8 afterimages + twin wheel ground star-streams
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
                    Vec3 sideOffset = new Vec3(Math.cos(rad) * .22, .06, Math.sin(rad) * .22);
                    wheelLeft.add(frame.at.add(sideOffset));
                    wheelRight.add(frame.at.subtract(sideOffset));
                    if (!isSelfFp) {
                        pose.pushPose();
                        pose.translate(frame.at.x, frame.at.y, frame.at.z);
                        pose.mulPose(Axis.YP.rotationDegrees(180 - frame.yaw));
                        int ghostCol = frame.starfield
                                ? DialDraw.alpha(0xffa8c4ff, (float) (.42 * Math.pow(.55, age / 3)))
                                : DialDraw.alpha(0xff7acbff, (float) (.36 * Math.pow(.5, age / 3)));
                        rig.draw(pose, buffers, Motion.actor(Form.XLR8, false, age, age, 1, 0, 0, false, false, 0, 1),
                                1.74f * 16 / rig.height, ghostCol, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                                frame.starfield);
                        pose.popPose();
                    }
                }
                if (celestialTrail && wheelLeft.size() >= 2) {
                    VfxPrimitives.polylineRibbon(pose, buffers, wheelLeft, camera, .11, 0xdca4c8ff, true, false);
                    VfxPrimitives.polylineRibbon(pose, buffers, wheelRight, camera, .11, 0xdca4c8ff, true, false);
                }
            }
        }

        // 2. X-Grey Matter slow-falling cosmic feather wake
        for (var wake : FEATHER_WAKES.values()) {
            if (wake.size() >= 2) {
                VfxPrimitives.polylineRibbon(pose, buffers, new ArrayList<>(wake), camera, .12, 0xc8b4d8ff, true, false);
            }
        }

        for (var active : ClientState.scenes()) {
            var scene = active.scene();
            if (scene.effect() != WristNetwork.STASIS_SCENE || active.until() <= now
                    || scene.center().distanceToSqr(camera) > 84 * 84) continue;
            drawStasisField(pose, buffers, camera, scene.center(), now - active.began() + partial,
                    Mth.clamp((active.until() - now) / 14f, 0, 1));
        }

        // 4. Windup warnings, hand/fist cosmic gathering & transformation transitions
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

        buffers.endBatch(CosmicVfxShader.nebula());
        buffers.endBatch(CosmicVfxShader.material());
        buffers.endBatch(CosmicVfxShader.glow());
        buffers.endBatch(RenderType.lightning());
        pose.popPose();
    }

    private static void drawStasisField(PoseStack pose, MultiBufferSource buffers, Vec3 camera,
                                        Vec3 center, float elapsed, float opacity) {
        float turn = ClientState.reduced() ? 0 : elapsed * .013f;
        int tint = DialDraw.alpha(0xff6c7bde, opacity * .77f);
        VfxPrimitives.shockRing(pose, buffers, center.add(0, .07, 0), TemporalEffects.RANGE,
                .42, .22, turn, turn + TAU, 64, tint, true, false);
        if (ClientState.reduced()) return;
        for (int i = 0; i < 8; i++) {
            float azimuth = i * TAU / 8 + turn;
            Vec3 base = center.add(Math.cos(azimuth) * 39.8, .08, Math.sin(azimuth) * 39.8);
            if (base.distanceToSqr(camera) > 64 * 64) continue;
            VfxPrimitives.materialRibbon(pose, buffers, base, base.add(0, 4.9, 0), camera, .56, .12,
                    DialDraw.alpha(0xff677cdb, opacity * .70f), VfxPrimitives.CRYSTAL, .08f);
        }
    }

    private static void drawCrystalShell(PoseStack pose, MultiBufferSource buffers, Player player, float partial, Vec3 camera) {
        Vec3 center = player.getPosition(partial).add(0, player.getBbHeight() * .52, 0);
        for (int i = 0; i < 6; i++) {
            double angle = i * TAU / 6;
            Vec3 base = center.add(Math.cos(angle) * .77, -.63, Math.sin(angle) * .77);
            VfxPrimitives.materialRibbon(pose, buffers, base, base.add(0, 1.36, 0), camera, .17, .31,
                    0xb54cbdaa, VfxPrimitives.CRYSTAL, .03f);
        }
    }

    private static void renderCueEffect(PoseStack pose, MultiBufferSource buffers, WristNetwork.Cue cue,
                                        float age, float partial, Vec3 camera) {
        if (AscalonRenderer.renderCue(pose, buffers, cue, age, camera)) return;
        if (cue.action() == WristNetwork.CRYSTAL_SHELL_BREAK_CUE) {
            if (age < 12) {
                VfxPrimitives.impactBurst(pose, buffers, cue.from().add(0, 1.10, 0), camera, 1.7, age / 12f,
                        DialDraw.alpha(0xff5fd3b9, 1 - age / 12f), false);
            }
            return;
        }
        if (cue.action() == WristNetwork.THAW_CUE || cue.action() == WristNetwork.FAILSAFE_CUE
                || cue.action() == WristNetwork.RESISTED_CUE) {
            if (age > 24) return;
            boolean thaw = cue.action() == WristNetwork.THAW_CUE;
            int col = DialDraw.alpha(thaw ? SILVER : 0xffc3fa89, 1 - age / 24f);
            VfxPrimitives.shockRing(pose, buffers, cue.from().add(0, .18, 0), .6 + age * (thaw ? 1.25 : .28),
                    .26, .22, 0, TAU, 44, col, thaw, !thaw);
            return;
        }
        if (cue.action() == WristNetwork.AEGIS_CUE) {
            if (age > 14) return;
            float p = age / 14f;
            int col = DialDraw.alpha(SILVER, (1f - p) * .9f);
            VfxPrimitives.shockRing(pose, buffers, cue.to(), .45 + p * 1.6, .24, .18 * (1 - p),
                    age * .25, age * .25 + TAU, 24, col, true, false);
            VfxPrimitives.impactBurst(pose, buffers, cue.to(), camera, 1.4, p, col, true);
            return;
        }
        if (cue.action() == WristNetwork.FLICK_CUE) {
            if (age > 16) return;
            float p = age / 16f;
            int col = DialDraw.alpha(0xffb55bdd, 1f - p);
            VfxPrimitives.materialRibbon(pose, buffers, attached(cue, "grip_r", partial), cue.to(), camera,
                    .10 * (1 - p * .5f), .025, col, VfxPrimitives.STAR, p * .7f);
            VfxPrimitives.shockRing(pose, buffers, cue.to(), Math.max(.2, 2.8 * (1 - p * .75)), .42, .25 * (1 - p),
                    age * .35, age * .35 + TAU, 28, DialDraw.alpha(GOLD, 1f - p), true, false);
            if (cue.hitEntity()) VfxPrimitives.impactBurst(pose, buffers, cue.to(), camera, 1.8, p, col, true);
            return;
        }
        if (cue.action() == WristNetwork.SINGULARITY_CUE) {
            if (age > 34) return;
            float p = age / 34f;
            Vec3 core = cue.to().add(0, .84, 0);
            float fade = (1f - p) * .84f;
            VfxPrimitives.celestialPlanet(pose, buffers, core, .18 + 1.9 * (1 - p), age * .035f,
                    DialDraw.alpha(0xff382057, (1f - p) * .99f), 0, false);
            inwardArcs(pose, buffers, camera, core, age, 9.0 * (1 - p * .86), fade, false);
            return;
        }
        if (cue.action() == WristNetwork.EXTINCTION_CUE) {
            if (age > 28) return;
            float p = age / 28f;
            float fade = 1f - p;
            Vec3 start = attached(cue, "grip_r", partial);
            Vec3 end = cue.to();
            VfxPrimitives.materialRibbon(pose, buffers, start, end, camera, 1.04 * fade, .76 * fade,
                    DialDraw.alpha(0xff302451, fade * .91f), VfxPrimitives.VOID, p * .32f);
            VfxPrimitives.materialRibbon(pose, buffers, start, end, camera, .64 * fade, .48 * fade,
                    DialDraw.alpha(0xff805cd9, fade * .95f), VfxPrimitives.STAR, p * .62f);
            VfxPrimitives.flowRibbon(pose, buffers, start, end, camera, .13 * fade, .065 * fade,
                    DialDraw.alpha(0xffdf82b6, fade * .76f), false, true, 1f);
            VfxPrimitives.orientedAstrolabeRingEroded(pose, buffers, end, end.subtract(start), camera,
                    .88 + p * 2.4, .34, age * .045f, 30, DialDraw.alpha(0xff9c76e8, fade), p * .55f);
            return;
        }
        if (cue.action() == WristNetwork.REWRITE_CUE) {
            if (age > 32) return;
            float p = age / 32f;
            float fade = 1f - p;
            VfxPrimitives.shockRing(pose, buffers, cue.from().add(0, .15, 0), 1.5 + p * 16.5, 1.1, .9 * fade,
                    age * .14, age * .14 + TAU, 56, DialDraw.alpha(GOLD, fade), true, false);
            VfxPrimitives.shockRing(pose, buffers, cue.from().add(0, 1.1, 0), 1.0 + p * 12.0, .75, 1.4 * fade,
                    -age * .18, -age * .18 + TAU, 44, DialDraw.alpha(SILVER, fade), true, false);
            VfxPrimitives.impactBurst(pose, buffers, cue.to(), camera, 4.5, p, DialDraw.alpha(VIOLET, fade), true);
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

        // Projectile collision bursts (Fireball star-sparks, Crystal shatter, Slime starry puddle)
        if (cue.projectile()) {
            if (age > 18) return;
            float p = age / 18f;
            int col = DialDraw.alpha(star ? switch (form) {
                case HEATBLAST -> 0xffeeb8ff;
                case DIAMONDHEAD -> 0xff9ce8ff;
                default -> 0xff98ffd6;
            } : switch (form) {
                case HEATBLAST -> 0xffff9d48;
                case DIAMONDHEAD -> 0xff97e8ef;
                default -> 0xff96f46b;
            }, 1f - p);
            VfxPrimitives.impactBurst(pose, buffers, cue.to(), camera, star ? 1.55 : 1.05, p, col, star);
            if (form == Form.STINKFLY) {
                // Star-speckled slime spreading ring on contact
                VfxPrimitives.shockRing(pose, buffers, cue.to().add(0, .05, 0), .35 + p * (star ? 1.65 : 1.1),
                        .32, .06, 0, TAU, 28, col, star, false);
            } else if (star) {
                VfxPrimitives.shockRing(pose, buffers, cue.to(), .25 + p * 1.15, .20, .14, 0, TAU, 24, col, true, false);
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
        float alpha = (1f - progress) * (ClientState.reduced() ? .68f : .94f);

        int baseTint = carni ? switch (form) {
            case HEATBLAST -> 0xffc75a37;
            case WILDMUTT, FOUR_ARMS -> 0xffb58066;
            case DIAMONDHEAD -> 0xff5ba99b;
            case XLR8 -> 0xff7186a2;
            case GREY_MATTER, GHOSTFREAK -> 0xffa078b0;
            case STINKFLY -> 0xff94ac4b;
            case RIPJAWS -> 0xff448d96;
            case UPGRADE -> 0xff8bae4a;
            case ALIEN_X -> 0xffad4774;
        } : star ? switch (form) {
            case HEATBLAST -> 0xffe68b59;
            case WILDMUTT -> 0xff64afb8;
            case DIAMONDHEAD -> 0xff96f0ff;
            case XLR8 -> 0xff9cc4ff;
            case GREY_MATTER -> 0xffb6ffe4;
            case FOUR_ARMS -> 0xffb8a4ff;
            case STINKFLY -> 0xff98ffd0;
            case RIPJAWS -> 0xff8ce2ff;
            case UPGRADE -> 0xff8affd8;
            case GHOSTFREAK -> 0xffc6a8ff;
            case ALIEN_X -> secondary ? 0xffe2a24c : 0xff7589e5;
        } : switch (form) {
            case HEATBLAST -> 0xfff89e4f;
            case DIAMONDHEAD -> 0xff97e6ef;
            case WILDMUTT -> 0xffc59454;
            case XLR8 -> 0xff529ec9;
            case GREY_MATTER -> 0xffa4a783;
            case FOUR_ARMS -> 0xffcf8a54;
            case STINKFLY, UPGRADE -> 0xff89f4b4;
            case ALIEN_X, GHOSTFREAK -> 0xffaaa2fa;
            case RIPJAWS -> 0xff8cdde8;
        };
        int color = DialDraw.alpha(baseTint, alpha);
        Vec3 from = cue.from();
        Vec3 to = cue.to();
        Vec3 dir = to.subtract(from);

        switch (form) {
            case FOUR_ARMS -> {
                if (age <= 10 && (!secondary || cue.hitGround())) {
                    fistTrails(pose, buffers, cue, partial, camera, to, alpha, baseTint, secondary, carni, star, progress);
                }
                if (carni) {
                    float tilt = cue.beat() == 0 ? -24f : 24f;
                    VfxPrimitives.slashArc(pose, buffers, from.add(0, -.35, 0), dir,
                            3.5, .78, tilt, -1.05f, 2.10f, color, false);
                } else if (!secondary) {
                    // 3-beat combo: distinct left twin-jab, right heavy cross, and converging quad-clap!
                    double reach = star ? 5.3 : 4.7;
                    double radius = Math.min(reach, Math.max(2.1, dir.horizontalDistance() * (.78 + .22 * progress)));
                    int beat = cue.beat();
                    if (beat == 0) {
                        VfxPrimitives.slashArc(pose, buffers, from.add(0, .12, 0), dir,
                                radius, star ? .85 : .62, 16f, -.95f, 1.65f, color, star);
                        VfxPrimitives.slashArc(pose, buffers, from.add(0, -.34, 0), dir,
                                radius * .86, star ? .64 : .46, -12f, -.80f, 1.45f, color, star);
                    } else if (beat == 1) {
                        VfxPrimitives.slashArc(pose, buffers, from.add(0, .06, 0), dir,
                                radius * 1.03, star ? .96 : .70, -18f, -.72f, 1.75f, color, star);
                        VfxPrimitives.flowRibbon(pose, buffers, from, to, camera,
                                star ? .26 : .18, .04, color, star, !star, 1.5f);
                    } else {
                        // Beat 3: Converging double-X Tetramand Sonic Smash!
                        VfxPrimitives.slashArc(pose, buffers, from, dir,
                                radius * 1.04, star ? 1.05 : .76, 22f, -1.18f, 2.36f, color, star);
                        VfxPrimitives.slashArc(pose, buffers, from, dir,
                                radius * .96, star ? .88 : .64, -22f, -1.18f, 2.36f, color, star);
                        VfxPrimitives.shockRing(pose, buffers, to.add(0, -.25, 0), .45 + progress * 1.65,
                                .28, .18, 0, TAU, 26, color, star, !star);
                    }
                } else {
                    // Ground Slam: expanding shockwave ring up to true 4.3m / 5.0m radius
                    double maxR = (star ? 5.0 : 4.3) * (cue.hitGround() ? 1.0 : .78);
                    double r = .45 + (maxR - .45) * Math.pow(progress, .68);
                    VfxPrimitives.shockRing(pose, buffers, to, r, star ? .68 : .44,
                            (star ? .52 : .28) * (1 - progress), 0, TAU, 44, color, star, !star);
                    if (star) {
                        VfxPrimitives.shockRing(pose, buffers, to, r * .66, .36,
                                .22 * (1 - progress), age * .14, age * .14 + TAU, 34,
                                DialDraw.alpha(SILVER, alpha * .85f), true, false);
                    }
                }
            }
            case HEATBLAST -> {
                if (carni) {
                    VfxPrimitives.nebulaCone(pose, buffers, from, to, .21, 1.38, age * .15f,
                            DialDraw.alpha(0xff4e2529, alpha * .75f), false);
                    VfxPrimitives.materialRibbon(pose, buffers, from, to, camera, .34, 1.05, color, VfxPrimitives.FIRE, progress);
                } else if (!secondary) {
                    VfxPrimitives.materialRibbon(pose, buffers, attached(cue, "grip_r", partial), to,
                            camera, star ? .24 : .16, .035, color, VfxPrimitives.FIRE, progress);
                } else if (cue.beat() > 0 && cue.linked()) {
                    Vec3 mid = from.lerp(to, .5).add(0, .55, 0);
                    VfxPrimitives.materialRibbon(pose, buffers, from, mid, camera, .16, .14, color, VfxPrimitives.STAR, progress);
                    VfxPrimitives.materialRibbon(pose, buffers, mid, to, camera, .14, .06, color, VfxPrimitives.FIRE, progress);
                } else {
                    Vec3 spread = dir.cross(new Vec3(0, 1, 0)).normalize().scale(star ? .67 : .45);
                    VfxPrimitives.materialRibbon(pose, buffers, attached(cue, "grip_r", partial), to.add(spread), camera,
                            .24, .80, color, VfxPrimitives.FIRE, progress);
                    VfxPrimitives.materialRibbon(pose, buffers, attached(cue, "grip_l", partial), to.subtract(spread), camera,
                            .24, .75, DialDraw.alpha(0xffcc552e, alpha * .78f), VfxPrimitives.FIRE, progress);
                }
            }
            case WILDMUTT -> {
                if (!secondary && !carni) {
                    if (cue.beat() == 0) {
                        double maxR = star ? 23.0 : 19.0;
                        VfxPrimitives.shockRing(pose, buffers, from.add(0, .06, 0),
                                .8 + progress * maxR, star ? .62 : .35, .14, 0, TAU, 56, color, star, !star);
                    } else {
                        // Return scent/constellation thread from marked target
                        VfxPrimitives.flowRibbon(pose, buffers, to, from, camera, .09, .04, color, star, !star, 2.0f);
                    }
                } else {
                    VfxPrimitives.materialRibbon(pose, buffers, from.add(0, -.37, 0), to, camera,
                            star ? .27 : .19, .06, color, star ? VfxPrimitives.STAR : VfxPrimitives.DUST, progress);
                    if (cue.hitEntity()) {
                        for (int claw = -1; claw <= 1; claw++) {
                            VfxPrimitives.slashArc(pose, buffers, to.add(0, claw * .16, 0), dir,
                                    .85, .18, 35f + claw * 8f, -.75f, 1.5f, color, star);
                        }
                    }
                }
            }
            case DIAMONDHEAD -> {
                if (!secondary && !carni && cue.fallback()) {
                    VfxPrimitives.orientedAstrolabeRing(pose, buffers, to, new Vec3(0, 1, 0),
                            .65 + progress * .35, .19, 0, 24, color);
                } else if (!secondary && !carni) {
                    // Rising star-points climbing the crystal wall prisms
                    for (int pillar = -1; pillar <= 1; pillar++) {
                        Vec3 base = to.add(pillar * .65, -.45, 0);
                        Vec3 peak = base.add(0, (pillar == 0 ? 2.15 : 1.35) * Math.min(1f, progress * 1.8f), 0);
                        VfxPrimitives.materialRibbon(pose, buffers, base, peak, camera, .22, .03,
                                color, VfxPrimitives.CRYSTAL, progress * .4f);
                    }
                    VfxPrimitives.shockRing(pose, buffers, to.add(0, -.4, 0), .55 + progress * 1.4,
                            .25, .12, 0, TAU, 26, color, star, !star);
                } else {
                    VfxPrimitives.materialRibbon(pose, buffers, from, to, camera, .18, .035,
                            color, VfxPrimitives.CRYSTAL, progress);
                }
            }
            case XLR8 -> {
                if (!secondary) {
                    Vec3 side = dir.cross(new Vec3(0, 1, 0)).normalize().scale(.24);
                    VfxPrimitives.flowRibbon(pose, buffers, from.add(side).add(0, -1.1, 0), to.add(side).add(0, -1.1, 0),
                            camera, .16, .06, color, star, !star, 1.8f);
                    VfxPrimitives.flowRibbon(pose, buffers, from.subtract(side).add(0, -1.1, 0), to.subtract(side).add(0, -1.1, 0),
                            camera, .16, .06, color, star, !star, 1.8f);
                } else if (cue.fallback()) {
                    VfxPrimitives.materialRibbon(pose, buffers, from.add(0, -1, 0), to.add(0, .15, 0), camera,
                            .18, .03, color, VfxPrimitives.DUST, progress);
                } else {
                    double slowRadius = cue.linked() ? (star ? 3.0 : 2.0) : (star ? 2.0 : 1.3);
                    VfxPrimitives.shockRing(pose, buffers, to.add(0, .06, 0), .4 + progress * slowRadius,
                            star ? .45 : .25, .18 * (1 - progress), age * .25, age * .25 + TAU * .82, 32, color, star, !star);
                }
            }
            case GREY_MATTER -> {
                if (!secondary && cue.fallback()) {
                    VfxPrimitives.shockRing(pose, buffers, to.add(0, .055, 0), .22 + .38 * progress,
                            .10, .03, 0, TAU, 20, color, star, false);
                } else if (!secondary && !carni) {
                    // Climbing micro-constellation veins on gripped wall
                    for (int b = -1; b <= 1; b++) {
                        Vec3 start = to.add(b * .22, -.35, 0);
                        Vec3 top = to.add(b * .14, .45 * Math.min(1f, progress * 1.6f), 0);
                        VfxPrimitives.flowRibbon(pose, buffers, start, top, camera, .07, .02, color, star, !star, 1.2f);
                    }
                } else {
                    VfxPrimitives.materialRibbon(pose, buffers, from, to, camera, .16, .04,
                            color, star ? VfxPrimitives.STAR : VfxPrimitives.DUST, progress);
                }
            }
            case STINKFLY -> {
                if (secondary || carni) {
                    Vec3 wingSpan = dir.cross(new Vec3(0, 1, 0));
                    wingSpan = wingSpan.lengthSqr() < 1e-4 ? new Vec3(.6, 0, 0) : wingSpan.normalize().scale(.58);
                    VfxPrimitives.materialRibbon(pose, buffers, from.add(wingSpan), to.add(wingSpan.scale(.35)),
                            camera, .21, .04, color, VfxPrimitives.WING, progress);
                    VfxPrimitives.materialRibbon(pose, buffers, from.subtract(wingSpan), to.subtract(wingSpan.scale(.35)),
                            camera, .21, .04, color, VfxPrimitives.WING, progress);
                    if (cue.linked() && star) {
                        // Upward reverse rebound vortex
                        VfxPrimitives.shockRing(pose, buffers, to, .45 + progress * 1.1, .28, .42,
                                -age * .3, -age * .3 + TAU, 24, color, true, false);
                    }
                }
            }
            case RIPJAWS -> {
                if (!secondary) {
                    // Upper & lower jaw arcs closing onto bite point
                    VfxPrimitives.slashArc(pose, buffers, to, dir.scale(-1), 1.15, .34, 72f, -.85f, 1.7f, color, star);
                    VfxPrimitives.slashArc(pose, buffers, to, dir.scale(-1), 1.15, .34, -72f, -.85f, 1.7f, color, star);
                } else if (cue.beat() == 1 && cue.linked()) {
                    // Secondary underwater target whirlpool tether!
                    VfxPrimitives.materialRibbon(pose, buffers, from, to, camera, .18, .06, color, VfxPrimitives.WATER, progress);
                    VfxPrimitives.shockRing(pose, buffers, from, .55 * (1 - progress * .5), .22, .16,
                            age * .35, age * .35 + TAU, 20, color, true, false);
                } else {
                    // Twin spiraling star-tides along surge
                    Vec3 ortho = dir.cross(new Vec3(0, 1, 0));
                    ortho = ortho.lengthSqr() < 1e-4 ? new Vec3(.3, 0, 0) : ortho.normalize().scale(.32);
                    VfxPrimitives.materialRibbon(pose, buffers, from.add(ortho), to.subtract(ortho.scale(.4)),
                            camera, .22, .06, color, VfxPrimitives.WATER, progress);
                    VfxPrimitives.materialRibbon(pose, buffers, from.subtract(ortho), to.add(ortho.scale(.4)),
                            camera, .22, .06, color, VfxPrimitives.WATER, progress);
                }
            }
            case UPGRADE -> {
                if (!secondary && !carni && cue.fallback()) {
                    Vec3 lens = attached(cue, "optic", partial);
                    VfxPrimitives.orientedAstrolabeRing(pose, buffers, lens, from.subtract(to),
                            .30 + progress * .33, .12, age * .06f, 22, color);
                } else if (!secondary && !carni) {
                    VfxPrimitives.circuitTrace(pose, buffers, from.add(0, -.32, 0), to, camera, color, star);
                } else {
                    Vec3 lens = attached(cue, "optic", partial);
                    VfxPrimitives.materialRibbon(pose, buffers, lens, to, camera,
                            star ? .26 : .17, .09, DialDraw.alpha(0xff12352d, alpha * .9f),
                            VfxPrimitives.VOID, progress * .58f);
                    VfxPrimitives.materialRibbon(pose, buffers, lens, to, camera,
                            star ? .18 : .12, .042, color, VfxPrimitives.CIRCUIT, progress * .76f);
                    if (cue.hitEntity()) VfxPrimitives.impactBurst(pose, buffers, to, camera,
                            .7, progress, color, star);
                }
            }
            case GHOSTFREAK -> {
                if (!secondary && !carni) {
                    // Dissolving silhouette star-mist peeling off body edge
                    for (int ringIdx = 0; ringIdx < 3; ringIdx++) {
                        double h = -.9 + ringIdx * .55 + progress * .45;
                        VfxPrimitives.shockRing(pose, buffers, from.add(0, h, 0), .35 + progress * .75,
                                .24, .18, age * .15 + ringIdx, age * .15 + ringIdx + 4.2, 22, color, star, !star);
                    }
                } else if (cue.beat() == 1) {
                    // Entry inward collapse + exit outward bloom
                    VfxPrimitives.shockRing(pose, buffers, from, Math.max(.12, .95 * (1 - progress)),
                            .26, .35 * (1 - progress), -age * .3, -age * .3 + TAU, 24, color, star, !star);
                    VfxPrimitives.shockRing(pose, buffers, to, .20 + progress * 1.25,
                            .30, .38 * (1 - progress), age * .3, age * .3 + TAU, 26, color, star, !star);
                    VfxPrimitives.materialRibbon(pose, buffers, from, to, camera, .16, .22,
                            color, VfxPrimitives.VOID, progress);
                } else if (cue.fallback() || carni && cue.hitEntity()) {
                    VfxPrimitives.slashArc(pose, buffers, to, dir, .74 + progress * .6,
                            .24, -34, -.70f, 1.52f, color, star);
                }
            }
            case ALIEN_X -> {
                if (!secondary && !carni) {
                    // Layered celestial astrolabe expanding to true 40-block stasis boundary!
                    double maxR = TemporalEffects.RANGE;
                    double r = 1.2 + (maxR - 1.2) * Math.pow(progress, .62);
                    VfxPrimitives.shockRing(pose, buffers, to.add(0, .12, 0), r, .85, .52 * (1 - progress * .5),
                            age * .08, age * .08 + TAU, 64, color, true, false);
                    VfxPrimitives.shockRing(pose, buffers, to.add(0, .55, 0), r * .68, .55, .65 * (1 - progress),
                            -age * .11, -age * .11 + TAU, 48, DialDraw.alpha(SILVER, alpha * .8f), true, false);
                } else if (carni && cue.beat() == 0) {
                    inwardArcs(pose, buffers, camera, to, age, 5.6 * (1f - progress * .68f), alpha, true);
                } else {
                    double maxR = carni ? 8.0 : 16.0;
                    double r = .65 + (maxR - .65) * Math.pow(progress, .58);
                    VfxPrimitives.shockRing(pose, buffers, to.add(0, .08, 0), r, carni ? .55 : .92,
                            (carni ? .45 : .85) * (1 - progress), 0, TAU, 52, color, !carni, carni);
                    if (!carni) {
                        VfxPrimitives.shockRing(pose, buffers, to.add(0, .35, 0), r * .72, .62,
                                1.25 * (1 - progress), age * .16, age * .16 + TAU, 40,
                                DialDraw.alpha(VIOLET, alpha * .9f), true, false);
                        VfxPrimitives.materialRibbon(pose, buffers, to, to.add(0, 3.8 * (1 - progress), 0), camera,
                                .95 * (1 - progress), .12, DialDraw.alpha(0xffcb653b, alpha * .9f), VfxPrimitives.STAR, progress);
                        VfxPrimitives.impactBurst(pose, buffers, to.add(0, .4, 0), camera, 4.2, progress,
                                DialDraw.alpha(0xfff1ac58, alpha), true);
                    } else {
                        VfxPrimitives.orientedAstrolabeRing(pose, buffers, to, dir,
                                r * .72, .42, -.17 * age, 32, DialDraw.alpha(0xffad4774, alpha));
                    }
                }
            }
        }

        // Universal server-confirmed entity hit impact feedback (never shown on empty swings!)
        if (cue.hitEntity() && age <= 10 && to.distanceToSqr(camera) <= 48 * 48) {
            VfxPrimitives.impactBurst(pose, buffers, to, camera, star ? 1.15 : .82, age / 10f,
                    DialDraw.alpha(baseTint, .9f * (1f - age / 10f)), star);
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
            int material = star ? VfxPrimitives.STAR : VfxPrimitives.DUST;
            int color = DialDraw.alpha(tint, alpha * .66f);
            VfxPrimitives.materialRibbon(pose, buffers, top, tip, camera, .17, .035, color, material, fade);
            VfxPrimitives.materialRibbon(pose, buffers, lower, tip.add(0, slam ? 0 : -.31, 0), camera,
                    .13, .026, color, material, fade);
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
            VfxPrimitives.lusterPolyline(pose, buffers, strand, camera, .32, DialDraw.alpha(hue, alpha * .68f));
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

        if (motion.action() == Abilities.AUTOMATED + Form.UPGRADE.ordinal()) {
            float gather = Motion.part((float) age, 0, 6);
            Vec3 at = WatchRender.skillAnchor(caster, view, "optic", partial, motion.origin());
            for (int i = 0; i < 4; i++) {
                float lit = Motion.part((float) age, i * 1.15f, i * 1.15f + 1.4f);
                if (lit <= .01f) continue;
                Vec3 from = WatchRender.skillAnchor(caster, view, "satellite_optic_" + i, partial, at);
                VfxPrimitives.materialRibbon(pose, buffers, from, at, camera, .07, .04,
                        DialDraw.alpha(0xff91a952, lit * (.44f + .47f * gather)), VfxPrimitives.CIRCUIT, .05f);
            }
            return;
        }

        // X-Heatblast U: Nebula gathers into palm during fireball windup
        if (star && form == Form.HEATBLAST && motion.action() < Abilities.SECONDARY) {
            float gather = Motion.part((float) age, 0, 4);
            Vec3 palm = WatchRender.skillAnchor(caster, view, "grip_r", partial, motion.origin());
            for (int i = 0; i < 3; i++) {
                double a = age * .45 + TAU * i / 3;
                Vec3 outer = palm.add(Math.cos(a) * (.65 * (1 - gather)), .22 * Math.sin(a * 2), Math.sin(a) * (.65 * (1 - gather)));
                VfxPrimitives.flowRibbon(pose, buffers, outer, palm, camera, .09, .03,
                        DialDraw.alpha(0xffdd8956, .85f), true, false, 1.0f);
            }
            return;
        }

        // X-Four Arms I: Cosmic nebula streams converge into all 4 raised fists before Ground Slam!
        if (star && form == Form.FOUR_ARMS && motion.action() >= Abilities.SECONDARY && motion.action() < Abilities.AUTOMATED) {
            float gather = Motion.part((float) age, 0, 4);
            Vec3 bodyPos = caster.getPosition(partial);
            Vec3 overhead = bodyPos.add(0, 2.35 - .55 * gather, 0).add(caster.getLookAngle().multiply(1, 0, 1).scale(.65));
            for (int i = 0; i < 4; i++) {
                double a = age * .35 + TAU * i / 4;
                Vec3 outer = bodyPos.add(Math.cos(a) * 1.45, .35 + (i % 2) * .75, Math.sin(a) * 1.45);
                VfxPrimitives.flowRibbon(pose, buffers, outer, overhead, camera, .14, .05,
                        DialDraw.alpha(0xffbaa8ff, .88f), true, false, 1.2f);
            }
            VfxPrimitives.shockRing(pose, buffers, motion.focus().add(0, .05, 0), 5.0 * (.35 + .65 * gather),
                    .22, .08, age * .18, age * .18 + TAU, 36, DialDraw.alpha(VIOLET, .45f), true, false);
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
            VfxPrimitives.materialRibbon(pose, buffers, palm, motion.focus(), camera,
                    .075, .047, DialDraw.alpha(0xffa05cc5, fade * .65f), VfxPrimitives.STAR, .08f);
            return;
        }
        double advance = ClientState.reduced() ? 0 : age * .11;
        for (int i = 0; i < 3; i++) {
            double start = TAU * i / 3 + advance;
            VfxPrimitives.shockRing(pose, buffers, at, radius, .38, .18,
                    start, start + (corrupted ? 1.56 : 2.03), 28, color, !corrupted, corrupted);
        }
        if (!stasis) {
            float gather = Motion.part((float) age, 0, 12);
            VfxPrimitives.shockRing(pose, buffers, at, Math.max(.35, 2.2 * (1 - gather * .65)),
                    .34, .42, -advance * 1.5, -advance * 1.5 + TAU, 28, color, !corrupted, corrupted);
            if (!corrupted) {
                // Star streams collapsing inward before Bellicus starburst detonation!
                for (int i = 0; i < 4; i++) {
                    double a = TAU * i / 4 - advance * 1.4;
                    Vec3 outer = at.add(Math.cos(a) * (4.8 * (1 - gather * .72)), 1.6 * (1 - gather * .6),
                            Math.sin(a) * (4.8 * (1 - gather * .72)));
                    VfxPrimitives.flowRibbon(pose, buffers, outer, at.add(0, .25, 0), camera, .18, .05,
                            DialDraw.alpha(GOLD, .82f), true, false, 1.4f);
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
        int color = DialDraw.alpha(baseColor, .28f + .64f * pulse);
        boolean star = kind == Kind.CELESTIAL;

        // 1. Concentric orbital/shock rings
        int rings = ClientState.reduced() ? 1 : 3;
        for (int i = 0; i < rings; i++) {
            double radius = (star ? .62 : .44) + .24 * i + .55 * pulse;
            float h = (.22f + .52f * i) + progress * .72f;
            double a = time * (star ? .14 : .20) + i * 2.09;
            VfxPrimitives.shockRing(pose, buffers, at.add(0, h, 0), radius, star ? .26 : .15, .10 + i * .14,
                    a, a + (kind == Kind.CARNITRIX ? 2.9 : star ? 5.2 : 4.6), 26, color, star, !star);
        }

        // 2. Upgraded Transformation VFX: DNA Double-Helix / Celestial Pillar / Carnitrix Collapse
        if (!ClientState.reduced() && view.s().phase() == Stage.ARMING) {
            if (kind == Kind.CLASSIC && time >= 5) {
                float beamLife = Mth.sin(Motion.part(time, 5, 18) * (float) Math.PI);
                int beamCol = DialDraw.alpha(baseColor, beamLife * .82f);
                // Vertical Omnitrix energy pillar + twin ascending DNA helix ribbons
                VfxPrimitives.flowRibbon(pose, buffers, at.add(0, .05, 0), at.add(0, 2.75, 0), camera,
                        .32 * beamLife, .14 * beamLife, DialDraw.alpha(baseColor, beamLife * .45f), false, true, 1.5f);
                for (int strand = 0; strand < 2; strand++) {
                    var helix = new ArrayList<Vec3>(10);
                    double phase = strand * Math.PI + time * .45;
                    for (int k = 0; k < 10; k++) {
                        float f = k / 9f;
                        double r = .52 * (1 - f * .32);
                        double ang = phase + f * 5.2;
                        helix.add(at.add(Math.cos(ang) * r, .15 + f * 2.35, Math.sin(ang) * r));
                    }
                    VfxPrimitives.polylineRibbon(pose, buffers, helix, camera, .075, beamCol, false, true);
                }
            } else if (kind == Kind.CELESTIAL) {
                float cosmicAlpha = pulse * .88f;
                int starCol = DialDraw.alpha(0xffbaa8ff, cosmicAlpha);
                // Celestial astrolabe & vertical starlight pillar
                VfxPrimitives.shockRing(pose, buffers, at.add(0, .06, 0), .85 + 1.65 * pulse,
                        .48, .28 * pulse, -time * .09, -time * .09 + TAU, 36, starCol, true, false);
                if (time >= 31) {
                    float surge = Motion.part(time, 31, 40) * (1 - Motion.part(time, 40, 48));
                    VfxPrimitives.flowRibbon(pose, buffers, at.add(0, .05, 0), at.add(0, 3.3, 0), camera,
                            .55 * surge, .22 * surge, DialDraw.alpha(SILVER, surge * .9f), true, false, 1.8f);
                }
            } else if (kind == Kind.CARNITRIX && time >= 6) {
                float inward = 1f - Motion.part(time, 6, 24);
                int redCol = DialDraw.alpha(0xffff4848, pulse * .85f);
                VfxPrimitives.shockRing(pose, buffers, at.add(0, .12, 0), .35 + 1.45 * inward,
                        .28, .42 * pulse, time * .32, time * .32 + TAU, 24, redCol, false, true);
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

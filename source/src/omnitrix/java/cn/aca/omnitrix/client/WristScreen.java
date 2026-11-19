package cn.aca.omnitrix.client;

import cn.aca.omnitrix.Form;
import cn.aca.omnitrix.Kind;
import cn.aca.omnitrix.Omnitrix;
import cn.aca.omnitrix.Wrist.Stage;
import cn.aca.omnitrix.WristNetwork;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.lwjgl.glfw.GLFW;

final class WristScreen extends Screen {
    private final Kind kind;
    private final boolean equipment;
    private int selection;
    private float rotation, previous, angularVel;
    private int age, previewAt = -10;
    private int lastStepSign = 1;
    private boolean sent;
    private static int lastClassic;
    private static int lastCelestial = 10;
    private static final float TAU = (float) Math.PI * 2;
    private static final float TOP = -TAU / 4;
    private static final int BACK = 0xe0111924;
    private static final int BACK_INNER = 0xd61a2736;
    private static final int INK = 0xffedf5ee;
    private static final int MUTED = 0xffb2c7cc;

    WristScreen(Kind kind, boolean equipment) {
        super(Component.translatable("item.aca_omnitrix." + kind.id));
        this.kind = kind;
        this.equipment = equipment;
        selection = kind == Kind.CLASSIC ? lastClassic : kind == Kind.CELESTIAL ? lastCelestial : 0;
        rotation = selection;
        previous = selection;
    }

    private static void scan(GuiGraphics graphics, float age, float glitch, int color) {
        if (ClientState.reduced()) return;
        int line = (int) (age * 1.35f % 26 - 13);
        int shift = glitch > .15f ? Math.round(Mth.sin(age * 3.9f) * 3f * glitch) : 0;
        graphics.pose().pushPose();
        graphics.pose().translate(shift, 0, 68);
        graphics.fill(-16, line, 17, line + 1, DialDraw.alpha(color, .46f + glitch * .28f));
        graphics.fill(-13, line - 2, 14, line - 1, DialDraw.alpha(color, .15f + glitch * .14f));
        if (glitch > .22f) {
            int gLine = -line / 2;
            graphics.fill(-18, gLine, 18, gLine + 1, DialDraw.alpha(0xffffffff, glitch * .35f));
        }
        graphics.pose().popPose();
    }

    static boolean isOpen() { return Minecraft.getInstance().screen instanceof WristScreen; }

    static boolean showsBezelHand() {
        return Minecraft.getInstance().screen instanceof WristScreen s
                && !s.equipment && s.kind != Kind.CARNITRIX;
    }

    static float lift(float partial) {
        if (!(Minecraft.getInstance().screen instanceof WristScreen s)) return 0f;
        float raw = (s.age + partial) / 6.2f;
        return ClientState.reduced() ? Motion.ease(raw) : Motion.easeOutBack(raw, 1.35f);
    }

    static float dialYaw(float partial) {
        if (!(Minecraft.getInstance().screen instanceof WristScreen s)) return 0f;
        float turn = ClientState.reduced() ? s.selection : Mth.lerp(partial, s.previous, s.rotation);
        int count = s.kind == Kind.CLASSIC ? 10 : 11;
        return turn * (360f / count);
    }

    static float switchImpulse(float partial) {
        if (!(Minecraft.getInstance().screen instanceof WristScreen s) || ClientState.reduced()) return 0f;
        float dt = (s.age + partial) - s.previewAt;
        if (dt < 0 || dt > 5.5f) return 0f;
        float p = dt / 5.5f;
        return (float) (Math.sin(p * Math.PI) * (1.0 - p * .35));
    }

    static int switchSign() {
        return Minecraft.getInstance().screen instanceof WristScreen s ? s.lastStepSign : 1;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        age++;
        previous = rotation;
        // Tactile spring-detent solver: crisp snap with subtle mechanical settle into notch
        float delta = selection - rotation;
        angularVel = angularVel * .42f + delta * .44f;
        rotation += angularVel;
        if (Math.abs(selection - rotation) < .005f && Math.abs(angularVel) < .005f) {
            rotation = selection;
            angularVel = 0f;
        }
        var view = ClientState.self();
        if (view == null || view.s().watch() != kind || view.s().phase() == Stage.ARMING
                || view.s().phase() == Stage.LOCKING || view.s().morphed()) {
            minecraft.setScreen(null);
        }
    }

    private void choose(int delta) {
        if (kind == Kind.CARNITRIX || equipment || sent || delta == 0) return;
        lastStepSign = delta > 0 ? 1 : -1;
        selection += delta;
        int count = kind == Kind.CLASSIC ? 10 : 11;
        if (Math.abs(selection) > 1000) {
            selection = Math.floorMod(selection, count);
            rotation = selection;
            previous = selection;
            angularVel = 0f;
        }
        if (kind == Kind.CLASSIC) lastClassic = Math.floorMod(selection, 10);
        else lastCelestial = Math.floorMod(selection, 11);
        previewAt = age;
        float pitch = 0.96f + (Math.floorMod(selection, count) % 4) * 0.035f;
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                Omnitrix.SELECT.get(), pitch, .46f));
    }

    private Form chosen() {
        if (kind == Kind.CLASSIC) return Form.CLASSIC.get(Math.floorMod(selection, 10));
        if (kind == Kind.CELESTIAL) {
            int slot = Math.floorMod(selection, 11);
            return slot < 10 ? Form.CLASSIC.get(slot) : Form.ALIEN_X;
        }
        return Form.ALIEN_X;
    }

    private void confirm() {
        var view = ClientState.self();
        if (view == null || sent) return;
        if (equipment) {
            sent = true;
            WristNetwork.ask(WristNetwork.UNEQUIP, 0);
            minecraft.setScreen(null);
        } else if (kind != Kind.CARNITRIX && view.s().phase() == Stage.IDLE) {
            sent = true;
            WatchRender.notifyDialSlam();
            WristNetwork.ask(WristNetwork.TRANSFORM, chosen().ordinal());
            minecraft.setScreen(null);
        }
    }

    @Override
    public void removed() {
        if (!sent && minecraft.getConnection() != null && minecraft.player != null) {
            WristNetwork.ask(WristNetwork.CLOSE, 0);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0) choose(vertical > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_A) {
            choose(-1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_D) {
            choose(1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            confirm();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        float scale = scale();
        float dx = (float) x - centerX(scale), dy = (float) y - centerY(scale);
        if (button == 0 && Math.hypot(dx, dy) < 31 * scale) {
            confirm();
            return true;
        }
        if (button == 0 && kind != Kind.CARNITRIX && !equipment && Math.hypot(dx, dy) < 62 * scale) {
            int count = kind == Kind.CLASSIC ? 10 : 11;
            float relative = (float) Math.atan2(dy, dx) - TOP;
            int slot = Math.floorMod(Math.round(relative / TAU * count), count);
            if (slot > count / 2) slot -= count;
            choose(slot);
            return true;
        }
        return true;
    }

    private float scale() { return Math.max(.64f, Math.min(1.08f, Math.min(width / 390f, height / 240f))); }
    private boolean left() { return minecraft.player == null || minecraft.player.getMainArm() == HumanoidArm.RIGHT; }
    private float centerX(float scale) { return left() ? width * .41f : width * .59f; }
    private float centerY(float scale) { return height - Math.min(106 * scale, height * .38f); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        var view = ClientState.self();
        if (view == null) return;
        float scale = scale();
        float revealRaw = (age + partial) / 6f;
        float reveal = Motion.ease(revealRaw);
        float popBounce = ClientState.reduced() ? reveal : Motion.easeOutBack(revealRaw, 1.25f);
        float popRing = revealRaw < 1f ? (float) Math.sin(Mth.clamp(revealRaw, 0f, 1f) * Math.PI) : 0f;
        float switchWave = ClientState.reduced() ? 0f : Math.max(0f, 1f - (age + partial - previewAt) / 6.5f);
        float cx = centerX(scale), cy = centerY(scale);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy + (1f - popBounce) * 22f, 30);
        float openScale = scale * (.84f + .16f * popBounce);
        pose.scale(openScale, openScale, 1);
        float time = age + partial;
        int color = DialDraw.glow(kind);
        int coreTint = DialDraw.coreTint(kind);

        // Diegetic holographic projection cone anchored directly to the 3D wrist watch lens!
        if (minecraft.options.getCameraType().isFirstPerson()) {
            float dir = left() ? -1f : 1f;
            float wristX = 56f * dir;
            float wristY = 52f;
            DialDraw.taperedBeam(g, wristX, wristY, 6f, 0f, 10f, 52f,
                    DialDraw.alpha(coreTint, .34f * reveal), DialDraw.alpha(color, .04f * reveal));
            DialDraw.edge(g, wristX - 4f * dir, wristY, -26f * dir, 38f, 1.1f, DialDraw.alpha(color, .46f * reveal));
            DialDraw.edge(g, wristX + 4f * dir, wristY - 2f, 36f * dir, 26f, 1.1f, DialDraw.alpha(color, .38f * reveal));
            DialDraw.arc(g, wristX, wristY, 0f, 3.2f, 0f, TAU, color);
            DialDraw.gradientArc(g, wristX, wristY, 3.2f, 9.5f, 0f, TAU,
                    DialDraw.alpha(coreTint, .45f), DialDraw.alpha(coreTint, 0f));
        }

        // Soft outer radial aura + high-contrast abyssal dial disc
        DialDraw.gradientArc(g, 0, 0, 53f, 64f, 0, TAU,
                DialDraw.alpha(coreTint, .24f + .14f * switchWave), DialDraw.alpha(coreTint, 0f));
        DialDraw.gradientArc(g, 0, 0, 0, 53.5f, 0, TAU, BACK_INNER, BACK);
        DialDraw.arc(g, 0, 0, 52.0f, 53.6f, 0, TAU, DialDraw.alpha(color, .72f));
        DialDraw.arc(g, 0, 0, 45.2f, 46.2f, 0, TAU, DialDraw.alpha(color, .28f));

        // Canonical Omnitrix hourglass / chevron bezel framing arcs
        float bezelSpin = ClientState.reduced() ? 0f : dialYaw(partial) * ((float) Math.PI / 180f) * .25f;
        for (int q = 0; q < 4; q++) {
            float qa = q * (TAU / 4f) + bezelSpin;
            DialDraw.arc(g, 0, 0, 53.8f, 55.8f, qa - .22f, qa + .22f, DialDraw.alpha(color, .86f));
        }
        // Top active selection reticle chevron
        DialDraw.diamond(g, 0, -49.2f, 3.2f, 4.4f, color);

        if (popRing > .02f) {
            float pr = 50 + (1 - reveal) * 16;
            DialDraw.arc(g, 0, 0, pr, pr + 2.6f, 0, TAU, DialDraw.alpha(color, popRing * .78f));
        }
        if (switchWave > .02f) {
            float sr = 16 + (1 - switchWave) * 35;
            DialDraw.arc(g, 0, 0, sr, sr + 1.9f, 0, TAU, DialDraw.alpha(color, switchWave * .82f));
        }

        // Holographic projection emitter pedestal beneath the 3D alien model
        if (!equipment && kind != Kind.CARNITRIX) {
            DialDraw.gradientArc(g, 0, 22, 0, 18.5f, 0, TAU,
                    DialDraw.alpha(coreTint, .28f + .15f * switchWave), DialDraw.alpha(color, .05f));
            DialDraw.arc(g, 0, 22, 16.0f, 17.5f, 0, TAU, DialDraw.alpha(color, .58f));
            DialDraw.taperedBeam(g, 0, 22, 30f, 0, -14, 38f,
                    DialDraw.alpha(coreTint, .16f + .10f * switchWave), DialDraw.alpha(color, 0f));
        }

        for (int notch = 0; notch < 40; notch++) {
            float angle = notch * TAU / 40 + (ClientState.reduced() ? 0 : time * .005f - bezelSpin * .4f);
            DialDraw.tick(g, 0, 0, notch % 4 == 0 ? 47.8f : 49.8f, 52f, angle,
                    DialDraw.alpha(color, notch % 4 == 0 ? .82f : .30f));
        }

        if (equipment || kind == Kind.CARNITRIX) {
            float end = kind == Kind.CARNITRIX ? (ClientState.reduced() ? 1 : time * .038f) : TOP;
            DialDraw.arc(g, 0, 0, 40, 43, end, end + .77f, color);
            g.flush();
            drawModel(g, Rig.get(kind.id), Rig.STILL, 0, -2, 82, 6, -(ClientState.reduced() ? 0 : time * .45f), -35);
        } else if (kind == Kind.CLASSIC) {
            renderRingAndHologram(g, partial, time, color, coreTint, switchWave, 10, false);
        } else {
            for (int i = 0; i < 3; i++) {
                float turnRing = TOP + i * TAU / 3f + (ClientState.reduced() ? 0 : time * (.015f + i * .005f));
                DialDraw.arc(g, 0, 0, 31 + i * 2.4f, 32 + i * 2.4f, turnRing, turnRing + 1.45f,
                        DialDraw.alpha(color, .82f - .18f * i));
            }
            StarfieldShader.clock(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
            renderRingAndHologram(g, partial, time, color, coreTint, switchWave, 11, true);
        }

        g.flush();
        int labelX = left() ? -60 : 61;
        Component title = equipment ? Component.translatable("watch.aca_omnitrix.slot")
                : kind == Kind.CARNITRIX ? getTitle() : Component.translatable(chosen().translationKey(kind));
        Component description = equipment ? getTitle()
                : kind == Kind.CARNITRIX ? Component.translatable("watch.aca_omnitrix.waiting")
                : Component.translatable("ability.aca_omnitrix." + chosen().id());
        String hintKey = equipment ? (view.s().bonded() ? "quarantine" : "remove_key")
                : kind == Kind.CARNITRIX ? "monitor_hint" : view.s().phase() == Stage.COOLING ? "charging" : "confirm";
        Component hint = Component.translatable("watch.aca_omnitrix." + hintKey);
        int length = Math.max(font.width(title), Math.max(font.width(description), font.width(hint)));
        int textX = left() ? labelX - length : labelX;
        if (textX < -cx / openScale + 8 || textX + length > (width - cx) / openScale - 8) {
            textX = -length / 2;
            labelX = 0;
        }
        int top = labelX == 0 ? 60 : -18;
        g.fill(textX - 6, top - 6, textX + length + 6, top + 33, 0xe4131c27);
        g.fill(textX - 6, top - 6, textX + length + 6, top - 5, DialDraw.alpha(color, .38f));
        g.fill(textX - 6, top - 6, textX + Math.min(28, length), top - 4, color);
        g.drawString(font, title, textX, top, INK, false);
        g.drawString(font, description, textX, top + 12, MUTED, false);
        g.drawString(font, hint, textX, top + 23, color, false);
        pose.popPose();
    }

    private void renderRingAndHologram(GuiGraphics g, float partial, float time, int color, int coreTint,
                                       float switchWave, int count, boolean celestial) {
        var pose = g.pose();
        float turn = ClientState.reduced() ? selection : Mth.lerp(partial, previous, rotation);
        int selectedIndex = Math.floorMod(selection, count);
        for (int i = 0; i < count; i++) {
            float angle = TOP + (i - turn) * TAU / count;
            boolean selected = i == selectedIndex;
            float focusProx = Math.max(0f, Mth.cos(angle - TOP));
            float radius = 38.2f + (selected ? 1.2f : 0f);
            float x = Mth.cos(angle) * radius;
            float y = Mth.sin(angle) * radius;
            if (selected) {
                DialDraw.arc(g, 0, 0, 43.0f, 46.8f, angle - .26f, angle + .26f, color);
                DialDraw.gradientArc(g, x, y, 7.6f, 11.8f, 0, TAU,
                        DialDraw.alpha(coreTint, .46f), DialDraw.alpha(coreTint, 0f));
            }
            float nodeR = selected ? 8.3f : 6.4f + focusProx * 1.1f;
            int nodeBg = selected
                    ? (celestial ? 0xff22344c : 0xff243d34)
                    : (celestial ? 0xff151e2c : 0xff1d2732);
            DialDraw.arc(g, x, y, 0, nodeR, 0, TAU, nodeBg);
            DialDraw.arc(g, x, y, nodeR - .9f, nodeR, 0, TAU,
                    DialDraw.alpha(color, selected ? .92f : .22f + focusProx * .28f));
            g.flush();
            pose.pushPose();
            pose.translate(0, 0, 6);
            float iconU = (i < 10 ? i : 10) * 16f;
            g.blit(Omnitrix.id("textures/gui/icons.png"), Math.round(x) - 5, Math.round(y) - 5,
                    10, 10, iconU, 0f, 16, 16, 176, 16);
            pose.popPose();
        }
        g.flush();
        Form form = chosen();
        Rig previewRig = Rig.get(celestial ? form.asset(kind) : form.id());
        Rig.Motion previewMotion = Rig.STILL;
        if (!ClientState.reduced()) {
            previewMotion = previewRig.animate(Rig.STILL, "idle", time, 1f);
            // Subtle heroic pose accent right on selection & slow breathing showcase
            float poseAccent = switchWave * .52f + (.12f + .10f * Mth.sin(time * .075f));
            previewMotion = previewRig.animate(previewMotion, "u", 1.8f + Mth.sin(time * .06f) * .6f, poseAccent);
        }
        float dt = age + partial - previewAt;
        float popScale = ClientState.reduced()
                ? 1f
                : (.64f + .36f * Motion.easeOutBack(dt / 5.2f, 1.75f));
        float hover = ClientState.reduced() ? 0 : Mth.sin(time * .095f) * 1.65f;
        float yaw = celestial
                ? (ClientState.reduced() ? 12f : time * 1.55f + switchWave * lastStepSign * 34f)
                : (ClientState.reduced() ? 19f : 19f + Mth.sin(time * .055f) * 16f + switchWave * lastStepSign * 36f);
        drawModel(g, previewRig, previewMotion, 0, 4 + hover, (celestial ? 35.5f : 34.5f) * popScale,
                5, yaw, -4f);
        scan(g, time, switchWave, color);
    }

    private static void drawModel(GuiGraphics g, Rig rig, Rig.Motion motion, float x, float y,
                                  float size, float z, float yaw, float pitch) {
        g.flush();
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y + size * .50f, z + 60);
        pose.scale(size, -size, size);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(180 + yaw));
        rig.draw(pose, g.bufferSource(), motion, 16 / rig.height, -1, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        g.flush();
        pose.popPose();
    }
}

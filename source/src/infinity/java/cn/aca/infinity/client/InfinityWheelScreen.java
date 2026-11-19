package cn.aca.infinity.client;

import cn.aca.infinity.GauntletData;
import cn.aca.infinity.InfinityNetwork;
import cn.aca.infinity.Stone;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.lwjgl.glfw.GLFW;

/**
 * U1 Locked Style: Wrist-Projected Diegetic Holographic Stone Fan (腕上手套全息扇面选石).
 * - ZERO full-screen dark backdrop, ZERO centered wheel blocking crosshair, ZERO 304x74 bottom black box!
 * - First-person Uru Gauntlet 4.0 rests angled at the lower-left/right wrist while 6 live-rotating 3D faceted raw stones
 *   levitate cleanly ABOVE the fingertips in an angled golden astrolabe holographic arc.
 */
public final class InfinityWheelScreen extends Screen {
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    private static final float FAN_RADIUS = 90f;
    private static final int QUICK_HOLD_THRESHOLD_TICKS = 5;

    private boolean socketMode;
    private int selection;
    private float smoothSel;
    private int age;
    private boolean closedWithAction;

    private int holdTicks;
    private boolean keySeenDown;
    private boolean holdQuickArmed;
    private int hoveredOrdinal = -1;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;

    public InfinityWheelScreen(boolean socketMode) {
        super(Component.translatable(socketMode ? "wheel.aca_infinity.title_socket" : "wheel.aca_infinity.title_select"));
        this.socketMode = socketMode;
        var self = ClientCache.self();
        this.selection = self != null ? Math.floorMod(self.data().selectedOrdinal(), 6) : 0;
        this.smoothSel = selection;
    }

    public static boolean isOpen() {
        return Minecraft.getInstance().screen instanceof InfinityWheelScreen;
    }

    public static float openProgress(float partial) {
        if (!(Minecraft.getInstance().screen instanceof InfinityWheelScreen s)) return 0f;
        return Mth.clamp((s.age + partial) / 5.0f, 0f, 1f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean isLeftWrist() {
        var mc = Minecraft.getInstance();
        return mc.player == null || mc.player.getMainArm() == HumanoidArm.RIGHT;
    }

    private float wristAnchorX() {
        return isLeftWrist() ? width * 0.22f : width * 0.78f;
    }

    private float wristAnchorY() {
        return height * 0.70f;
    }

    /** Angle along the wrist holographic fan for stone slot [0..5], floating cleanly above gauntlet fingertips! */
    private float slotAngleRad(int index) {
        if (isLeftWrist()) {
            return (-138f + index * 21.5f) * DEG_TO_RAD;
        } else {
            return (-42f - index * 21.5f) * DEG_TO_RAD;
        }
    }

    private boolean isWheelKeyPhysicallyDown() {
        if (minecraft == null || minecraft.getWindow() == null) return false;
        var key = InfinityClient.KEY_WHEEL.getKey();
        if (key.getType() != InputConstants.Type.KEYSYM || key.getValue() <= 0) return false;
        long handle = minecraft.getWindow().getWindow();
        if (handle == 0L) return false;
        return InputConstants.isKeyDown(handle, key.getValue());
    }

    @Override
    public void tick() {
        age++;
        smoothSel = Mth.lerp(0.38f, smoothSel, selectedStone().ordinal());

        var self = ClientCache.self();
        if (self == null || !self.snapshot().equipped()) {
            minecraft.setScreen(null);
            return;
        }

        boolean down = isWheelKeyPhysicallyDown();
        if (down) {
            keySeenDown = true;
            holdTicks++;
            if (!socketMode && holdTicks >= QUICK_HOLD_THRESHOLD_TICKS) {
                holdQuickArmed = true;
            }
        } else if (keySeenDown && holdQuickArmed && !socketMode) {
            confirmQuickRelease();
        }
    }

    private void confirmQuickRelease() {
        confirmSlot(selectedStone());
    }

    private void stepSelection(int delta) {
        hoveredOrdinal = -1;
        selection = Math.floorMod(selection + delta, Stone.values().length);
        float pitch = 1.32f + (selection % 3) * 0.05f;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_STEP, pitch, 0.45f));
    }

    private Stone selectedStone() {
        return hoveredOrdinal >= 0 ? Stone.values()[hoveredOrdinal] : Stone.byOrdinal(selection);
    }

    private void updateFanHover(double mouseX, double mouseY) {
        if (Double.isNaN(lastMouseX)) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return;
        }
        if (Math.hypot(mouseX - lastMouseX, mouseY - lastMouseY) < 2.0 && hoveredOrdinal < 0) {
            return;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        float ax = wristAnchorX();
        float ay = wristAnchorY();
        int count = Stone.values().length;
        int bestIdx = -1;
        double bestDist = 34.0;
        for (int i = 0; i < count; i++) {
            float ang = slotAngleRad(i);
            float sx = ax + Mth.cos(ang) * FAN_RADIUS;
            float sy = ay + Mth.sin(ang) * FAN_RADIUS;
            double d = Math.hypot(mouseX - sx, mouseY - sy);
            if (d < bestDist) {
                bestDist = d;
                bestIdx = i;
            }
        }
        if (bestIdx < 0 && !socketMode) {
            double cdx = mouseX - width * 0.5;
            double cdy = mouseY - height * 0.5;
            if (Math.hypot(cdx, cdy) > 26.0) {
                double flickNorm = Mth.clamp((mouseX - width * 0.30) / Math.max(1.0, width * 0.40), 0.0, 0.999);
                bestIdx = (int) (flickNorm * count);
            }
        }
        if (bestIdx >= 0 && bestIdx != hoveredOrdinal) {
            hoveredOrdinal = bestIdx;
            selection = bestIdx;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_STEP, 1.48f, 0.34f));
        }
    }

    private void confirmSlot(Stone stone) {
        if (socketMode) {
            InfinityNetwork.sendToServer(InfinityNetwork.ACT_TOGGLE_SOCKET, stone.ordinal(), 0);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.END_PORTAL_FRAME_FILL, 1.15f, 0.55f));
        } else {
            closedWithAction = true;
            InfinityNetwork.sendToServer(InfinityNetwork.ACT_SELECT_STONE, stone.ordinal(), 0);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.25f, 0.65f));
            minecraft.setScreen(null);
        }
    }

    private void confirmWristCenter() {
        var self = ClientCache.self();
        if (self == null) return;
        if (socketMode) {
            closedWithAction = true;
            InfinityNetwork.sendToServer(InfinityNetwork.ACT_UNEQUIP, 0, 0);
            minecraft.setScreen(null);
        } else {
            confirmSlot(selectedStone());
        }
    }

    @Override
    public void removed() {
        if (!closedWithAction && minecraft != null && minecraft.getConnection() != null) {
            InfinityNetwork.sendToServer(InfinityNetwork.ACT_CLOSE_WHEEL, 0, 0);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            stepSelection(scrollY > 0 ? -1 : 1);
        }
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (!socketMode && holdQuickArmed && InfinityClient.KEY_WHEEL.matches(keyCode, scanCode)) {
            confirmQuickRelease();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_UP) {
            stepSelection(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_DOWN) {
            stepSelection(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            socketMode = !socketMode;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirmSlot(selectedStone());
            return true;
        }
        if (age >= 4 && !holdQuickArmed && InfinityClient.KEY_WHEEL.matches(keyCode, scanCode)) {
            confirmSlot(selectedStone());
            return true;
        }
        if (InfinityClient.KEY_SNAP.matches(keyCode, scanCode)) {
            closedWithAction = true;
            InfinityNetwork.sendToServer(InfinityNetwork.ACT_CAST_SNAP, 0, 0);
            minecraft.setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            socketMode = !socketMode;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f));
            return true;
        }
        float ax = wristAnchorX();
        float ay = wristAnchorY();
        if (button == 0) {
            if (Math.hypot(mouseX - ax, mouseY - ay) < 30.0) {
                confirmWristCenter();
                return true;
            }
            int count = Stone.values().length;
            for (int i = 0; i < count; i++) {
                float ang = slotAngleRad(i);
                float sx = ax + Mth.cos(ang) * FAN_RADIUS;
                float sy = ay + Mth.sin(ang) * FAN_RADIUS;
                if (Math.hypot(mouseX - sx, mouseY - sy) < 26.0) {
                    selection = i;
                    hoveredOrdinal = i;
                    confirmSlot(Stone.values()[i]);
                    return true;
                }
            }
            if (!socketMode) {
                confirmSlot(selectedStone());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Zero screen-darkening tint so forward vision stays 100% clear!
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        var self = ClientCache.self();
        if (self == null) return;
        updateFanHover(mouseX, mouseY);

        GauntletData data = self.data();
        Stone chosen = selectedStone();
        int activeIdx = chosen.ordinal();
        boolean leftWrist = isLeftWrist();

        float reveal = openProgress(partial);
        float ease = 1f - (1f - reveal) * (1f - reveal) * (1f - reveal);
        float time = age + partial;

        float ax = wristAnchorX();
        float ay = wristAnchorY() + (1f - ease) * 20f;
        float fanR = FAN_RADIUS * (0.68f + 0.32f * ease);

        int accent = socketMode ? 0xffffd878 : chosen.color;
        int accentBright = socketMode ? 0xfffff2b8 : chosen.coreBright;
        StarDialDraw.ring(g, ax, ay, 10.5f, 12.0f, 0f, TAU, StarDialDraw.withAlpha(0xffdfb868, 0.55f * ease));
        StarDialDraw.diamond(g, ax, ay, 3.6f, 3.6f, StarDialDraw.withAlpha(accentBright, 0.85f * ease), 0);

        float aMin = Math.min(slotAngleRad(0), slotAngleRad(5)) - 0.14f;
        float aMax = Math.max(slotAngleRad(0), slotAngleRad(5)) + 0.14f;
        StarDialDraw.ring(g, ax, ay, fanR - 0.6f, fanR + 0.6f, aMin, aMax,
                StarDialDraw.withAlpha(0xffdfb868, 0.45f * ease));
        StarDialDraw.ring(g, ax, ay, fanR * 0.54f, fanR * 0.54f + 0.8f, aMin + 0.08f, aMax - 0.08f,
                StarDialDraw.withAlpha(accent, 0.28f * ease));

        for (int t = 0; t <= 20; t++) {
            float ta = Mth.lerp(t / 20f, aMin, aMax);
            float r0 = fanR + 3.8f;
            float r1 = fanR + ((t % 4 == 0) ? 7.6f : 5.2f);
            StarDialDraw.line(g, ax + Mth.cos(ta) * r0, ay + Mth.sin(ta) * r0,
                    ax + Mth.cos(ta) * r1, ay + Mth.sin(ta) * r1,
                    0.85f, StarDialDraw.withAlpha(0xffe8cb84, (t % 4 == 0 ? 0.55f : 0.26f) * ease));
        }

        Stone[] stones = Stone.values();
        float selScreenX = ax;
        float selScreenY = ay - fanR;
        for (int i = 0; i < stones.length; i++) {
            Stone st = stones[i];
            boolean has = data.has(st);
            boolean isSel = (i == activeIdx);
            float ang = slotAngleRad(i);
            float rPush = isSel ? fanR + 9.0f : fanR;
            float sx = ax + Mth.cos(ang) * rPush;
            float sy = ay + Mth.sin(ang) * rPush + Mth.sin(time * 0.12f + i) * 1.6f;

            if (isSel) {
                selScreenX = sx;
                selScreenY = sy;
                StarDialDraw.line(g, ax, ay, sx, sy, 1.5f, StarDialDraw.withAlpha(st.coreBright, 0.65f * ease));
                StarDialDraw.line(g, ax, ay, sx, sy, 4.0f, StarDialDraw.withAlpha(st.color, 0.18f * ease));
                StarDialDraw.ring(g, sx, sy, 14.5f, 15.8f, 0f, TAU, StarDialDraw.withAlpha(st.coreBright, 0.88f * ease));
                StarDialDraw.diamond(g, sx, sy - 18.2f, 2.5f, 2.5f, st.coreBright, 0);
            } else {
                StarDialDraw.line(g, ax + Mth.cos(ang) * 15f, ay + Mth.sin(ang) * 15f,
                        sx - Mth.cos(ang) * 12f, sy - Mth.sin(ang) * 12f,
                        0.8f, StarDialDraw.withAlpha(has ? st.color : 0xff4a566c, 0.28f * ease));
                StarDialDraw.ring(g, sx, sy, 10.8f, 11.8f, 0f, TAU,
                        StarDialDraw.withAlpha(has ? st.coreBright : 0xff465268, (has ? 0.52f : 0.25f) * ease));
            }

            g.flush();
            var pose = g.pose();
            pose.pushPose();
            pose.translate(sx, sy, isSel ? 120f : 90f);
            float stonePx = (isSel ? 23.0f : 16.8f) * ease;
            pose.scale(stonePx, -stonePx, stonePx);
            float spin = isSel ? time * 3.8f : time * 1.6f + i * 45f;
            int tint = has ? -1 : 0x80586274;
            InfinityRig.get("stone_crystal").render(pose, g.bufferSource(),
                    GauntletRender.stoneCrystalPose(st, spin, 1.0f),
                    1.0f, tint, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            g.flush();
            pose.popPose();
        }

        // Frameless Floating Holographic Readout cleanly beside the Gauntlet (zero overlap!)
        float textX = leftWrist ? (ax + 82f) : (ax - 242f);
        float textY = ay - 10f;
        StarDialDraw.line(g, selScreenX, selScreenY, textX + (leftWrist ? -6f : 168f), textY + 5f,
                0.85f, StarDialDraw.withAlpha(accentBright, 0.45f * ease));
        StarDialDraw.line(g, textX, textY + 11f, textX + 168f, textY + 11f,
                0.95f, StarDialDraw.withAlpha(accent, 0.65f * ease));

        Component header = Component.literal("✦ ")
                .append(Component.translatable(chosen.translationKey()))
                .append(Component.literal(" · "))
                .append(Component.translatable(data.has(chosen) ? "wheel.aca_infinity.socketed" : "wheel.aca_infinity.unsocketed"));
        if (socketMode) {
            header = Component.literal("[镶嵌管理] ").append(header);
        }
        g.drawString(font, header, Math.round(textX), Math.round(textY),
                data.has(chosen) ? accentBright : 0xffa8b4c8, true);

        Component skillCompact = Component.literal("[J] ")
                .append(Component.translatable(chosen.primarySkillKey()))
                .append(Component.literal("   [K] "))
                .append(Component.translatable(chosen.secondarySkillKey()));
        g.drawString(font, skillCompact, Math.round(textX), Math.round(textY + 15), 0xfff0f5ff, true);

        boolean expandDetail = socketMode || Screen.hasAltDown();
        if (expandDetail) {
            g.drawString(font, Component.translatable(chosen.comboKey()),
                    Math.round(textX), Math.round(textY + 27), 0xffe8cb84, true);
            g.drawString(font, Component.translatable(socketMode ? "wheel.aca_infinity.hint_socket" : "wheel.aca_infinity.hint_select"),
                    Math.round(textX), Math.round(textY + 39), 0xff9cb2d4, true);
        } else {
            Component microHint = data.hasAllStones()
                    ? Component.translatable("tooltip.aca_infinity.snap_ready")
                    : Component.translatable("wheel.aca_infinity.hint_select");
            g.drawString(font, microHint, Math.round(textX), Math.round(textY + 27),
                    data.hasAllStones() ? 0xffffdf78 : 0xbb9ab0ce, true);
        }

        g.flush();
    }
}

package cn.aca.domain67.client;

import cn.aca.domain67.AcaDomain67;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * ACA-Domain67 v1.2.0 Sculpted High-Detail 3D Item & Gourmet Renderer:
 * - Studio 3-Point Directional Normal Shading (`putLit`) so metallic champagne-gold, dark bronze,
 *   bone-china porcelain, roasted crimson sausage, and tempered dark chocolate exhibit crisp 3D facets & speculars!
 * - Smooth Parametric Multi-Segment Curved "6" & "7" Twin Figures (`drawSculpted67Pair`) with 3-layer jewel bevels
 *   (Brushed Champagne-Gold bezel + Dark Espresso micro-inlay + Pearl-Ivory relief ridge) & delicate hairline astrolabe rings!
 * - Refined 20-sided turned porcelain bowl, wavy 3D sinusoidal noodle strands, plump curved scored red sausages,
 *   and thin artisanal gold-leaf chocolate tablet with delicate pouring ganache drizzle!
 */
public final class Domain67ItemRenderer {
    private static BlockEntityWithoutLevelRenderer instance;

    private static final float[][] DIGIT_6_PATH = buildSmoothDigit6Path();

    private Domain67ItemRenderer() {}

    private static float[][] buildSmoothDigit6Path() {
        // 21-point smooth continuous typographic '6': sweeping top-right serif -> smooth left spine -> round closed lower belly!
        float[][] pts = new float[21][2];
        // Part A (0..6): Upper hook from right serif (0.14, 0.25) curving over crown (0.0, 0.34) down to left waist (-0.155, -0.06)
        float[][] hook = {
                {0.145f, 0.235f},
                {0.110f, 0.305f},
                {0.035f, 0.345f},
                {-0.055f, 0.335f},
                {-0.125f, 0.275f},
                {-0.160f, 0.165f},
                {-0.165f, 0.025f}
        };
        System.arraycopy(hook, 0, pts, 0, hook.length);
        // Part B (7..20): Smooth circular lower belly centered at (0.0, -0.145), rx = 0.165, ry = 0.175
        // Angle sweeps from 180 deg (left waist) counter-clockwise through bottom (270), right (360), top-bridge (450), closing at (515 deg)!
        for (int i = 0; i < 14; i++) {
            float deg = 180.0f + (332.0f * i / 13.0f);
            float rad = deg * ((float) Math.PI / 180.0f);
            pts[7 + i][0] = Mth.cos(rad) * 0.162f;
            pts[7 + i][1] = -0.148f + Mth.sin(rad) * 0.172f;
        }
        return pts;
    }

    public static BlockEntityWithoutLevelRenderer get() {
        if (instance == null) {
            var mc = Minecraft.getInstance();
            instance = new BlockEntityWithoutLevelRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels()) {
                @Override
                public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                                         MultiBufferSource buffers, int light, int overlay) {
                    float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
                    float time = (mc.level != null ? mc.level.getGameTime() : Domain67ClientCache.ticks()) + partial;
                    boolean isGui = ctx == ItemDisplayContext.GUI;
                    boolean isGround = ctx == ItemDisplayContext.GROUND;
                    boolean isFirstPerson = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                            || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;

                    VertexConsumer solid = buffers.getBuffer(Domain67CoreShader.solidArch());

                    if (stack.is(AcaDomain67.CIRCUS_MEGAPHONE_67.get())) {
                        renderRefined3d67Relic(pose, solid, buffers, time, partial, isGui, isGround, isFirstPerson);
                    } else if (stack.is(AcaDomain67.CHOCO_SAUSAGE_NOODLES.get())) {
                        render3dNoodleBowlItem(pose, solid, time, isGui, isGround);
                    } else if (stack.is(AcaDomain67.NO_HEARTBURN_BENTO.get())) {
                        render3dDeluxeBentoItem(pose, solid, time, isGui, isGround);
                    }
                }
            };
        }
        return instance;
    }

    private static void renderRefined3d67Relic(PoseStack pose, VertexConsumer solid, MultiBufferSource buffers,
                                               float time, float partial, boolean isGui, boolean isGround, boolean isFirstPerson) {
        float castProg = isFirstPerson ? Domain67ClientCache.handSignProgress(partial) : -1f;
        float castBoost = castProg >= 0f ? Mth.sin(castProg * (float) Math.PI) : 0f;

        pose.pushPose();
        float baseBob = isGround ? Mth.sin(time * 0.10f) * 0.05f : (isGui ? Mth.sin(time * 0.07f) * 0.015f : 0f);
        pose.translate(0.5f, 0.45f + baseBob, 0.5f);

        if (isGround) {
            pose.mulPose(Axis.YP.rotationDegrees(time * 2.0f));
        } else if (isGui) {
            pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(time * 0.05f) * 12f));
            pose.scale(1.06f, 1.06f, 1.06f);
        } else if (isFirstPerson) {
            pose.translate(0.0f, 0.04f + castBoost * 0.05f, 0.0f);
            pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(time * 0.06f) * 4f));
        }

        // 1. Slender Fluted Ivory-Porcelain & Champagne-Gold Scepter Grip + Cupped Lotus Pedestal
        drawOctagonalFrustum(pose, solid, 0.032f, 0.040f, -0.34f, -0.09f, 12, 0.96f, 0.92f, 0.84f);
        // Delicate gold grip rings & bottom faceted finial
        drawOctagonal3dRing(pose, solid, 0.042f, 0.008f, 0.010f, 12, -0.22f, 0.92f, 0.73f, 0.31f);
        drawOctagonalFrustum(pose, solid, 0.014f, 0.044f, -0.39f, -0.34f, 10, 0.92f, 0.73f, 0.31f);
        // Sculpted stepped resonator cradle
        drawOctagonalFrustum(pose, solid, 0.045f, 0.165f, -0.09f, -0.035f, 16, 0.90f, 0.71f, 0.29f);
        drawOctagonalFrustum(pose, solid, 0.165f, 0.195f, -0.035f, 0.002f, 16, 0.98f, 0.95f, 0.89f);
        drawOctagonal3dRing(pose, solid, 0.192f, 0.010f, 0.009f, 18, 0.004f, 0.95f, 0.78f, 0.35f);

        // 2. Exquisite Curved Multi-Segment 3D "6" and "7" Figures (6-7 Balance-Scale Motion!)
        float freq = castBoost > 0.01f ? 0.60f : 0.16f;
        float amp = 0.034f + castBoost * 0.048f;
        float bob67 = Mth.sin(time * freq) * amp;
        float tilt67 = Mth.cos(time * freq) * (4.5f + castBoost * 7.5f);

        pose.pushPose();
        pose.translate(0.0f, 0.23f, 0.0f);
        drawSculpted67Pair(pose, solid, 0.50f, bob67, tilt67);
        pose.popPose();

        // 3. Delicate Wire-Thin Counter-Rotating Astrolabe Gold Rings + 4 Faceted Octahedron Gems
        float spinFast = time * (2.0f + castBoost * 6.5f);
        pose.pushPose();
        pose.translate(0.0f, 0.23f, 0.0f);

        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(24f));
        pose.mulPose(Axis.XP.rotationDegrees(70f));
        pose.mulPose(Axis.ZP.rotationDegrees(spinFast));
        drawOctagonal3dRing(pose, solid, 0.31f, 0.0075f, 0.0075f, 24, 0f, 0.95f, 0.78f, 0.35f);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(-26f));
        pose.mulPose(Axis.XP.rotationDegrees(74f));
        pose.mulPose(Axis.ZP.rotationDegrees(-spinFast * 1.3f));
        drawOctagonal3dRing(pose, solid, 0.255f, 0.006f, 0.006f, 20, 0f, 0.99f, 0.90f, 0.58f);
        pose.popPose();

        for (int i = 0; i < 4; i++) {
            float ang = (time * 0.06f) + i * ((float) Math.PI * 0.5f);
            float ox = Mth.cos(ang) * 0.33f;
            float oz = Mth.sin(ang) * 0.19f;
            float oy = Mth.sin(time * 0.12f + i * 1.6f) * 0.045f;
            pose.pushPose();
            pose.translate(ox, oy, oz);
            pose.mulPose(Axis.YP.rotationDegrees(time * 3.2f + i * 45f));
            drawOctahedronGem(pose, solid, 0.018f, 0.028f, 0.98f, 0.84f, 0.40f);
            pose.popPose();
        }
        pose.popPose();

        pose.popPose();
    }

    private static void render3dNoodleBowlItem(PoseStack pose, VertexConsumer solid, float time,
                                               boolean isGui, boolean isGround) {
        pose.pushPose();
        float bob = isGround ? Mth.sin(time * 0.10f) * 0.05f : 0f;
        pose.translate(0.5f, 0.30f + bob, 0.5f);
        if (isGround) pose.mulPose(Axis.YP.rotationDegrees(time * 2.0f));
        float sc = isGui ? 0.92f : 0.82f;
        pose.scale(sc, sc, sc);
        drawGourmetNoodleBowlAssembly(pose, solid, 1.0f, time);
        pose.popPose();
    }

    private static void render3dDeluxeBentoItem(PoseStack pose, VertexConsumer solid, float time,
                                                boolean isGui, boolean isGround) {
        pose.pushPose();
        float bob = isGround ? Mth.sin(time * 0.10f) * 0.05f : 0f;
        pose.translate(0.5f, 0.34f + bob, 0.5f);
        if (isGround) pose.mulPose(Axis.YP.rotationDegrees(time * 2.0f));
        float sc = isGui ? 0.95f : 0.85f;
        pose.scale(sc, sc, sc);

        // Sleek Lacquered Espresso-Walnut Outer Box + Warm Ivory Inner Liner + Thin Gold Rim
        drawBox(pose, solid, -0.34f, 0.0f, -0.25f, 0.34f, 0.155f, 0.25f, 0.22f, 0.15f, 0.10f);
        drawBox(pose, solid, -0.32f, 0.03f, -0.23f, 0.32f, 0.162f, 0.23f, 0.97f, 0.94f, 0.88f);
        drawBox(pose, solid, -0.345f, 0.148f, -0.255f, 0.345f, 0.164f, -0.235f, 0.93f, 0.75f, 0.33f);
        drawBox(pose, solid, -0.345f, 0.148f, 0.235f, 0.345f, 0.164f, 0.255f, 0.93f, 0.75f, 0.33f);
        drawBox(pose, solid, -0.345f, 0.148f, -0.235f, -0.325f, 0.164f, 0.235f, 0.93f, 0.75f, 0.33f);
        drawBox(pose, solid, 0.325f, 0.148f, -0.235f, 0.345f, 0.164f, 0.235f, 0.93f, 0.75f, 0.33f);
        drawBox(pose, solid, -0.012f, 0.04f, -0.23f, 0.012f, 0.164f, 0.23f, 0.88f, 0.70f, 0.30f);

        // Left: Fluffy Pearl Rice Mound + Crimson Umeboshi Plum
        drawOctagonalFrustum(pose, solid, 0.15f, 0.11f, 0.06f, 0.185f, 12, -0.17f, 0.0f, 0.99f, 0.98f, 0.95f);
        drawOctagonalFrustum(pose, solid, 0.030f, 0.018f, 0.182f, 0.208f, 8, -0.17f, 0.0f, 0.82f, 0.16f, 0.20f);

        // Right-Front: 2 Plump Curved Scored Red Sausages
        pose.pushPose();
        pose.translate(0.16f, 0.178f, 0.09f);
        pose.mulPose(Axis.YP.rotationDegrees(16f));
        drawScoredRedSausage(pose, solid, 0.23f, 0.038f);
        pose.translate(0.0f, 0.008f, -0.085f);
        pose.mulPose(Axis.YP.rotationDegrees(-30f));
        drawScoredRedSausage(pose, solid, 0.23f, 0.038f);
        pose.popPose();

        // Right-Back: Rolled Golden Tamagoyaki + Emerald Broccoli Florets
        drawBox(pose, solid, 0.035f, 0.06f, -0.21f, 0.175f, 0.185f, -0.07f, 0.98f, 0.81f, 0.28f);
        drawOctagonalFrustum(pose, solid, 0.062f, 0.045f, 0.06f, 0.185f, 8, 0.25f, -0.14f, 0.30f, 0.68f, 0.32f);

        pose.popPose();
    }

    /**
     * Renders a Sculpted Smooth-Curved 3D Pearl-Ivory & Brushed Champagne-Gold "67" Pair centered at (0, 0, 0).
     */
    public static void drawSculpted67Pair(PoseStack pose, VertexConsumer vc, float scale, float bobOffset, float tiltDeg) {
        pose.pushPose();
        pose.scale(scale, scale, scale);

        pose.pushPose();
        pose.translate(-0.245f, bobOffset, 0.0f);
        pose.mulPose(Axis.ZP.rotationDegrees(tiltDeg * 0.55f));
        drawSculptedDigit6(pose, vc);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.245f, -bobOffset, 0.0f);
        pose.mulPose(Axis.ZP.rotationDegrees(-tiltDeg * 0.55f));
        drawSculptedDigit7(pose, vc);
        pose.popPose();

        pose.popPose();
    }

    /**
     * Smooth 21-Point Parametric Curved 3D Digit "6" with 3-Layer Jewel Bevels!
     */
    public static void drawSculptedDigit6(PoseStack pose, VertexConsumer vc) {
        drawExtrudedJewelRibbon(pose, vc, DIGIT_6_PATH, 0.052f, 0.068f);
        // Faceted gold terminal gem at upper-right serif tip
        pose.pushPose();
        pose.translate(DIGIT_6_PATH[0][0], DIGIT_6_PATH[0][1], 0f);
        drawOctahedronGem(pose, vc, 0.048f, 0.078f, 0.96f, 0.80f, 0.36f);
        pose.popPose();
    }

    /**
     * Smooth Tapered Calligraphic 3D Digit "7" with Arched Serif Crown & Filigree Clasp!
     */
    public static void drawSculptedDigit7(PoseStack pose, VertexConsumer vc) {
        float[][] topBar = {
                {-0.175f, 0.175f},
                {-0.170f, 0.295f},
                {-0.050f, 0.315f},
                {0.080f, 0.310f},
                {0.185f, 0.315f}
        };
        drawExtrudedJewelRibbon(pose, vc, topBar, 0.050f, 0.066f);

        // Smooth curved diagonal calligraphic stem sweeping from (0.175, 0.305) down to (-0.045, -0.320)
        float[][] stem = new float[9][2];
        for (int i = 0; i < 9; i++) {
            float t = i / 8.0f;
            // Subtle graceful inward calligraphy curve
            stem[i][0] = Mth.lerp(t, 0.170f, -0.048f) - Mth.sin(t * (float) Math.PI) * 0.028f;
            stem[i][1] = Mth.lerp(t, 0.300f, -0.320f);
        }
        drawExtrudedJewelRibbon(pose, vc, stem, 0.054f, 0.070f);

        // Delicate Brushed-Gold Waist Filigree Clasp on the stem of '7'
        pose.pushPose();
        pose.translate(0.036f, -0.015f, 0.0f);
        pose.mulPose(Axis.ZP.rotationDegrees(-18f));
        drawBox(pose, vc, -0.095f, -0.024f, -0.082f, 0.095f, 0.024f, 0.082f, 0.94f, 0.76f, 0.32f);
        pose.popPose();
    }

    /**
     * Extrudes a smooth continuous 2D path into a 3-layer beveled 3D jewel bar:
     * - Outer Brushed Champagne-Gold Casing (`halfWidth`, `halfDepth`)
     * - Dark Bronze Pinstripe Inset (`halfWidth * 0.72`, `halfDepth + 0.008`)
     * - Raised Warm Pearl-Ivory Sculpted Ridge (`halfWidth * 0.54`, `halfDepth + 0.018`)
     */
    private static void drawExtrudedJewelRibbon(PoseStack pose, VertexConsumer vc,
                                                float[][] path, float halfW, float halfD) {
        drawRibbonPass(pose, vc, path, halfW, halfD, 0.92f, 0.73f, 0.30f);
        drawRibbonPass(pose, vc, path, halfW * 0.70f, halfD + 0.008f, 0.38f, 0.25f, 0.11f);
        drawRibbonPass(pose, vc, path, halfW * 0.52f, halfD + 0.018f, 0.99f, 0.96f, 0.90f);
    }

    private static void drawRibbonPass(PoseStack pose, VertexConsumer vc,
                                       float[][] pts, float hw, float hd,
                                       float r, float g, float b) {
        Matrix4f mat = pose.last().pose();
        int n = pts.length;
        float[] nxArr = new float[n];
        float[] nyArr = new float[n];

        for (int i = 0; i < n; i++) {
            float dx, dy;
            if (i == 0) {
                dx = pts[1][0] - pts[0][0];
                dy = pts[1][1] - pts[0][1];
            } else if (i == n - 1) {
                dx = pts[n - 1][0] - pts[n - 2][0];
                dy = pts[n - 1][1] - pts[n - 2][1];
            } else {
                dx = pts[i + 1][0] - pts[i - 1][0];
                dy = pts[i + 1][1] - pts[i - 1][1];
            }
            float len = Math.max(1e-4f, Mth.sqrt(dx * dx + dy * dy));
            nxArr[i] = -dy / len;
            nyArr[i] = dx / len;
        }

        for (int i = 0; i < n - 1; i++) {
            float x0L = pts[i][0] + nxArr[i] * hw, y0L = pts[i][1] + nyArr[i] * hw;
            float x0R = pts[i][0] - nxArr[i] * hw, y0R = pts[i][1] - nyArr[i] * hw;
            float x1L = pts[i + 1][0] + nxArr[i + 1] * hw, y1L = pts[i + 1][1] + nyArr[i + 1] * hw;
            float x1R = pts[i + 1][0] - nxArr[i + 1] * hw, y1R = pts[i + 1][1] - nyArr[i + 1] * hw;

            // Front face (+Z)
            putLit(mat, vc, x0L, y0L, hd, r, g, b, 0f, 0.15f, 1f);
            putLit(mat, vc, x0R, y0R, hd, r, g, b, 0f, -0.15f, 1f);
            putLit(mat, vc, x1R, y1R, hd, r, g, b, 0f, -0.15f, 1f);
            putLit(mat, vc, x1L, y1L, hd, r, g, b, 0f, 0.15f, 1f);

            // Back face (-Z)
            putLit(mat, vc, x0R, y0R, -hd, r, g, b, 0f, -0.15f, -1f);
            putLit(mat, vc, x0L, y0L, -hd, r, g, b, 0f, 0.15f, -1f);
            putLit(mat, vc, x1L, y1L, -hd, r, g, b, 0f, 0.15f, -1f);
            putLit(mat, vc, x1R, y1R, -hd, r, g, b, 0f, -0.15f, -1f);

            // Outer side wall (+N)
            putLit(mat, vc, x0L, y0L, -hd, r, g, b, nxArr[i], nyArr[i], 0f);
            putLit(mat, vc, x0L, y0L, hd, r, g, b, nxArr[i], nyArr[i], 0f);
            putLit(mat, vc, x1L, y1L, hd, r, g, b, nxArr[i + 1], nyArr[i + 1], 0f);
            putLit(mat, vc, x1L, y1L, -hd, r, g, b, nxArr[i + 1], nyArr[i + 1], 0f);

            // Inner side wall (-N)
            putLit(mat, vc, x0R, y0R, hd, r, g, b, -nxArr[i], -nyArr[i], 0f);
            putLit(mat, vc, x0R, y0R, -hd, r, g, b, -nxArr[i], -nyArr[i], 0f);
            putLit(mat, vc, x1R, y1R, -hd, r, g, b, -nxArr[i + 1], -nyArr[i + 1], 0f);
            putLit(mat, vc, x1R, y1R, hd, r, g, b, -nxArr[i + 1], -nyArr[i + 1], 0f);
        }

        // Start & End caps
        float sLx = pts[0][0] + nxArr[0] * hw, sLy = pts[0][1] + nyArr[0] * hw;
        float sRx = pts[0][0] - nxArr[0] * hw, sRy = pts[0][1] - nyArr[0] * hw;
        putLit(mat, vc, sLx, sLy, -hd, r, g, b, 0f, 1f, 0f);
        putLit(mat, vc, sRx, sRy, -hd, r, g, b, 0f, 1f, 0f);
        putLit(mat, vc, sRx, sRy, hd, r, g, b, 0f, 1f, 0f);
        putLit(mat, vc, sLx, sLy, hd, r, g, b, 0f, 1f, 0f);

        int e = n - 1;
        float eLx = pts[e][0] + nxArr[e] * hw, eLy = pts[e][1] + nyArr[e] * hw;
        float eRx = pts[e][0] - nxArr[e] * hw, eRy = pts[e][1] - nyArr[e] * hw;
        putLit(mat, vc, eLx, eLy, hd, r, g, b, 0f, -1f, 0f);
        putLit(mat, vc, eRx, eRy, hd, r, g, b, 0f, -1f, 0f);
        putLit(mat, vc, eRx, eRy, -hd, r, g, b, 0f, -1f, 0f);
        putLit(mat, vc, eLx, eLy, -hd, r, g, b, 0f, -1f, 0f);
    }

    /**
     * Refined 20-Sided Turned Bone-China Bowl + Wavy 3D Noodle Strands + Plump Curved Scored Red Sausages
     * + Artisanal Gold-Leaf Tempered Chocolate Tablet Pouring Ganache!
     */
    public static void drawGourmetNoodleBowlAssembly(PoseStack pose, VertexConsumer vc, float remainFrac, float time) {
        // 1. Turned 20-Sided Bone-China Bowl with Delicate Gold Foot & Lip
        drawOctagonal3dRing(pose, vc, 0.22f, 0.025f, 0.028f, 20, 0.025f, 0.93f, 0.75f, 0.33f);
        drawSmoothLatheBowl(pose, vc, 20);
        drawOctagonal3dRing(pose, vc, 0.402f, 0.014f, 0.014f, 20, 0.242f, 0.95f, 0.78f, 0.35f);

        // 2. Sculpted Golden Noodle Mound + Sinusoidal Wavy 3D Noodle Strands!
        float nH = 0.09f + 0.20f * remainFrac;
        float nR = 0.34f * (0.58f + 0.42f * remainFrac);
        drawOctagonalFrustum(pose, vc, nR, nR * 0.74f, 0.13f, 0.13f + nH, 16, 0f, 0f, 0.94f, 0.79f, 0.38f);

        float topY = 0.132f + nH;
        for (int row = -2; row <= 2; row++) {
            float zBase = row * 0.056f * remainFrac;
            float span = nR * (0.72f - Math.abs(row) * 0.09f);
            for (int seg = 0; seg < 5; seg++) {
                float x0 = Mth.lerp(seg / 5.0f, -span, span);
                float x1 = Mth.lerp((seg + 1) / 5.0f, -span, span);
                float w0 = Mth.sin(seg * 1.3f + row) * 0.016f;
                float w1 = Mth.sin((seg + 1) * 1.3f + row) * 0.016f;
                drawBox(pose, vc, x0, topY, zBase + w0 - 0.013f, x1, topY + 0.022f, zBase + w1 + 0.013f,
                        0.98f, 0.85f, 0.44f);
            }
        }

        // 3. Glossy Tempered Dark-Chocolate Ganache Pool
        float poolY = topY + 0.018f;
        drawOctagonalFrustum(pose, vc, nR * 0.64f, nR * 0.40f, poolY - 0.008f, poolY + 0.026f, 14, 0f, 0f,
                0.24f, 0.10f, 0.05f);
        drawOctagonalFrustum(pose, vc, nR * 0.36f, nR * 0.16f, poolY + 0.026f, poolY + 0.038f, 12, 0f, 0f,
                0.34f, 0.15f, 0.08f);

        if (remainFrac > 0.20f) {
            // 4. Two Plump Curved 3D Scored Grilled Red Sausages
            pose.pushPose();
            pose.translate(-0.075f, poolY + 0.052f, 0.055f);
            pose.mulPose(Axis.YP.rotationDegrees(24f));
            pose.mulPose(Axis.ZP.rotationDegrees(7f));
            drawScoredRedSausage(pose, vc, 0.34f, 0.048f);
            pose.popPose();

            pose.pushPose();
            pose.translate(0.085f, poolY + 0.062f, -0.048f);
            pose.mulPose(Axis.YP.rotationDegrees(-36f));
            pose.mulPose(Axis.ZP.rotationDegrees(-6f));
            drawScoredRedSausage(pose, vc, 0.34f, 0.048f);
            pose.popPose();

            // 5. Artisanal Beveled Dark-Chocolate Tablet + Silky Pouring Ganache Ribbon
            drawChocolateBarWithCascade(pose, vc, poolY, time, 0.82f);
        }

        // 6. Slender Tapered Ebony & Gold Chopsticks
        pose.pushPose();
        pose.translate(0.22f, 0.258f, 0.0f);
        pose.mulPose(Axis.YP.rotationDegrees(14f));
        for (float cz : new float[]{-0.024f, 0.024f}) {
            drawBox(pose, vc, -0.25f, 0.0f, cz - 0.007f, 0.13f, 0.014f, cz + 0.007f, 0.20f, 0.13f, 0.09f);
            drawBox(pose, vc, 0.13f, -0.001f, cz - 0.008f, 0.25f, 0.015f, cz + 0.008f, 0.94f, 0.77f, 0.34f);
        }
        pose.popPose();
    }

    private static void drawSmoothLatheBowl(PoseStack pose, VertexConsumer vc, int segs) {
        // 3-band curved porcelain profile (lower curve -> belly -> flared rim)
        float[][] prof = {
                {0.22f, 0.04f},
                {0.31f, 0.09f},
                {0.37f, 0.16f},
                {0.40f, 0.24f}
        };
        Matrix4f mat = pose.last().pose();
        for (int b = 0; b < prof.length - 1; b++) {
            float r0 = prof[b][0], y0 = prof[b][1];
            float r1 = prof[b + 1][0], y1 = prof[b + 1][1];
            for (int i = 0; i < segs; i++) {
                float th0 = (float) (Math.PI * 2.0 * i / segs);
                float th1 = (float) (Math.PI * 2.0 * (i + 1) / segs);
                float c0 = Mth.cos(th0), s0 = Mth.sin(th0);
                float c1 = Mth.cos(th1), s1 = Mth.sin(th1);
                putLit(mat, vc, r0 * c0, y0, r0 * s0, 0.98f, 0.96f, 0.92f, c0, -0.25f, s0);
                putLit(mat, vc, r0 * c1, y0, r0 * s1, 0.98f, 0.96f, 0.92f, c1, -0.25f, s1);
                putLit(mat, vc, r1 * c1, y1, r1 * s1, 0.99f, 0.97f, 0.94f, c1, 0.10f, s1);
                putLit(mat, vc, r1 * c0, y1, r1 * s0, 0.99f, 0.97f, 0.94f, c0, 0.10f, s0);
            }
        }
    }

    /**
     * Smooth 5-Segment Curved Plump Grilled Red Sausage with Crisp Diagonal Grill Score Cuts!
     */
    public static void drawScoredRedSausage(PoseStack pose, VertexConsumer vc, float length, float radius) {
        int nSeg = 5;
        float step = length / nSeg;
        for (int i = 0; i < nSeg; i++) {
            float u = (i - 2.0f) / 2.0f; // -1..+1
            float taper = 1.0f - 0.16f * u * u;
            float segR = radius * taper;
            pose.pushPose();
            pose.translate(u * (length * 0.39f), -u * u * radius * 0.24f, u * u * radius * 0.15f);
            pose.mulPose(Axis.ZP.rotationDegrees(-u * 10.0f));
            pose.mulPose(Axis.YP.rotationDegrees(u * 8.0f));

            drawHorizontalCylinder12(pose, vc, step * 0.56f, segR, 0f, 0.76f, 0.14f, 0.16f);

            // Diagonal Grill Score Cuts on middle 3 segments exposing tender coral-pink meat
            if (i >= 1 && i <= 3) {
                pose.pushPose();
                pose.translate(0f, segR * 0.84f, 0f);
                pose.mulPose(Axis.YP.rotationDegrees(28f));
                drawBox(pose, vc, -0.015f, -0.010f, -segR * 0.72f, 0.015f, 0.014f, segR * 0.72f, 0.48f, 0.07f, 0.08f);
                drawBox(pose, vc, -0.009f, -0.004f, -segR * 0.64f, 0.009f, 0.020f, segR * 0.64f, 1.00f, 0.52f, 0.48f);
                pose.popPose();
            }

            // Domed sausage end-caps & tied casing knots
            if (i == 0 || i == nSeg - 1) {
                float dir = (i == 0) ? -1f : 1f;
                drawHorizontalCylinder12(pose, vc, segR * 0.34f, segR * 0.68f, dir * step * 0.56f, 0.68f, 0.12f, 0.14f);
            }
            pose.popPose();
        }
    }

    public static void drawChocolateBarWithCascade(PoseStack pose, VertexConsumer vc,
                                                   float targetPoolY, float time, float scale) {
        float hoverY = targetPoolY + 0.27f * scale + Mth.sin(time * 0.12f) * 0.014f * scale;
        float streamTop = hoverY - 0.03f * scale;
        int steps = 6;
        for (int i = 0; i < steps; i++) {
            float f0 = (float) i / steps;
            float f1 = (float) (i + 1) / steps;
            float y0 = Mth.lerp(f1, streamTop, targetPoolY + 0.008f);
            float y1 = Mth.lerp(f0, streamTop, targetPoolY + 0.008f);
            float wave = Mth.sin(time * 0.24f - i * 0.8f) * 0.008f * scale;
            float r = (0.024f - f0 * 0.007f) * scale;
            drawOctagonalFrustum(pose, vc, r * 1.1f, r * 0.9f, y0, y1, 8,
                    -0.02f * scale + wave, -0.02f * scale, 0.26f, 0.11f, 0.05f);
        }

        pose.pushPose();
        pose.translate(-0.095f * scale, hoverY, -0.075f * scale);
        pose.mulPose(Axis.YP.rotationDegrees(22f));
        pose.mulPose(Axis.ZP.rotationDegrees(-32f));
        pose.mulPose(Axis.XP.rotationDegrees(16f));
        pose.scale(scale, scale, scale);

        drawBox(pose, vc, -0.115f, -0.016f, -0.078f, 0.115f, 0.016f, 0.078f, 0.20f, 0.085f, 0.04f);
        for (int gx = -1; gx <= 1; gx++) {
            for (int gz = 0; gz <= 1; gz++) {
                float cx = gx * 0.072f;
                float cz = (gz - 0.5f) * 0.072f;
                drawOctagonalFrustum(pose, vc, 0.032f, 0.024f, 0.016f, 0.030f, 4, cx, cz, 0.28f, 0.125f, 0.06f);
            }
        }
        // Thin 24K Gold-Leaf Foil Accent
        drawBox(pose, vc, -0.118f, -0.018f, -0.081f, -0.025f, 0.022f, 0.081f, 0.96f, 0.80f, 0.34f);
        pose.popPose();
    }

    public static void drawOctahedronGem(PoseStack pose, VertexConsumer vc, float radius, float halfH,
                                         float r, float g, float b) {
        Matrix4f mat = pose.last().pose();
        int segs = 6;
        for (int i = 0; i < segs; i++) {
            float th0 = (float) (Math.PI * 2.0 * i / segs);
            float th1 = (float) (Math.PI * 2.0 * (i + 1) / segs);
            float x0 = Mth.cos(th0) * radius, z0 = Mth.sin(th0) * radius;
            float x1 = Mth.cos(th1) * radius, z1 = Mth.sin(th1) * radius;
            putLit(mat, vc, x0, 0f, z0, r, g, b, Mth.cos(th0), 0.6f, Mth.sin(th0));
            putLit(mat, vc, x1, 0f, z1, r, g, b, Mth.cos(th1), 0.6f, Mth.sin(th1));
            putLit(mat, vc, 0f, halfH, 0f, r * 1.1f, g * 1.1f, b * 1.1f, 0f, 1f, 0f);
            putLit(mat, vc, 0f, halfH, 0f, r * 1.1f, g * 1.1f, b * 1.1f, 0f, 1f, 0f);

            putLit(mat, vc, x1, 0f, z1, r, g, b, Mth.cos(th1), -0.6f, Mth.sin(th1));
            putLit(mat, vc, x0, 0f, z0, r, g, b, Mth.cos(th0), -0.6f, Mth.sin(th0));
            putLit(mat, vc, 0f, -halfH, 0f, r * 0.85f, g * 0.85f, b * 0.85f, 0f, -1f, 0f);
            putLit(mat, vc, 0f, -halfH, 0f, r * 0.85f, g * 0.85f, b * 0.85f, 0f, -1f, 0f);
        }
    }

    public static void drawOctagonal3dRing(PoseStack pose, VertexConsumer vc, float radius,
                                           float halfThickR, float halfThickY, int segs,
                                           float r, float g, float b) {
        drawOctagonal3dRing(pose, vc, radius, halfThickR, halfThickY, segs, 0f, r, g, b);
    }

    public static void drawOctagonal3dRing(PoseStack pose, VertexConsumer vc, float radius,
                                           float halfThickR, float halfThickY, int segs, float centerY,
                                           float r, float g, float b) {
        Matrix4f mat = pose.last().pose();
        float rIn = radius - halfThickR;
        float rOut = radius + halfThickR;
        float yTop = centerY + halfThickY;
        float yBot = centerY - halfThickY;
        for (int i = 0; i < segs; i++) {
            float th0 = (float) (Math.PI * 2.0 * i / segs);
            float th1 = (float) (Math.PI * 2.0 * (i + 1) / segs);
            float c0 = Mth.cos(th0), s0 = Mth.sin(th0);
            float c1 = Mth.cos(th1), s1 = Mth.sin(th1);

            putLit(mat, vc, rIn * c0, yTop, rIn * s0, r, g, b, 0f, 1f, 0f);
            putLit(mat, vc, rOut * c0, yTop, rOut * s0, r, g, b, 0f, 1f, 0f);
            putLit(mat, vc, rOut * c1, yTop, rOut * s1, r, g, b, 0f, 1f, 0f);
            putLit(mat, vc, rIn * c1, yTop, rIn * s1, r, g, b, 0f, 1f, 0f);

            putLit(mat, vc, rIn * c0, yBot, rIn * s0, r, g, b, 0f, -1f, 0f);
            putLit(mat, vc, rIn * c1, yBot, rIn * s1, r, g, b, 0f, -1f, 0f);
            putLit(mat, vc, rOut * c1, yBot, rOut * s1, r, g, b, 0f, -1f, 0f);
            putLit(mat, vc, rOut * c0, yBot, rOut * s0, r, g, b, 0f, -1f, 0f);

            putLit(mat, vc, rOut * c0, yBot, rOut * s0, r, g, b, c0, 0f, s0);
            putLit(mat, vc, rOut * c1, yBot, rOut * s1, r, g, b, c1, 0f, s1);
            putLit(mat, vc, rOut * c1, yTop, rOut * s1, r, g, b, c1, 0f, s1);
            putLit(mat, vc, rOut * c0, yTop, rOut * s0, r, g, b, c0, 0f, s0);
        }
    }

    public static void drawOctagonalFrustum(PoseStack pose, VertexConsumer vc,
                                            float rBot, float rTop, float y0, float y1, int segs,
                                            float r, float g, float b) {
        drawOctagonalFrustum(pose, vc, rBot, rTop, y0, y1, segs, 0f, 0f, r, g, b);
    }

    public static void drawOctagonalFrustum(PoseStack pose, VertexConsumer vc,
                                            float rBot, float rTop, float y0, float y1, int segs,
                                            float cx, float cz, float r, float g, float b) {
        Matrix4f mat = pose.last().pose();
        for (int i = 0; i < segs; i++) {
            float th0 = (float) (Math.PI * 2.0 * i / segs);
            float th1 = (float) (Math.PI * 2.0 * (i + 1) / segs);
            float c0 = Mth.cos(th0), s0 = Mth.sin(th0);
            float c1 = Mth.cos(th1), s1 = Mth.sin(th1);
            putLit(mat, vc, cx + rBot * c0, y0, cz + rBot * s0, r, g, b, c0, 0.25f, s0);
            putLit(mat, vc, cx + rBot * c1, y0, cz + rBot * s1, r, g, b, c1, 0.25f, s1);
            putLit(mat, vc, cx + rTop * c1, y1, cz + rTop * s1, r, g, b, c1, 0.25f, s1);
            putLit(mat, vc, cx + rTop * c0, y1, cz + rTop * s0, r, g, b, c0, 0.25f, s0);

            putLit(mat, vc, cx, y1, cz, r, g, b, 0f, 1f, 0f);
            putLit(mat, vc, cx + rTop * c0, y1, cz + rTop * s0, r, g, b, 0f, 1f, 0f);
            putLit(mat, vc, cx + rTop * c1, y1, cz + rTop * s1, r, g, b, 0f, 1f, 0f);
            putLit(mat, vc, cx, y1, cz, r, g, b, 0f, 1f, 0f);
        }
    }

    private static void drawHorizontalCylinder12(PoseStack pose, VertexConsumer vc, float halfLen, float radius,
                                                 float centerX, float r, float g, float b) {
        Matrix4f mat = pose.last().pose();
        int segs = 12;
        float x0 = centerX - halfLen;
        float x1 = centerX + halfLen;
        for (int i = 0; i < segs; i++) {
            float th0 = (float) (Math.PI * 2.0 * i / segs);
            float th1 = (float) (Math.PI * 2.0 * (i + 1) / segs);
            float s0 = Mth.sin(th0), c0 = Mth.cos(th0);
            float s1 = Mth.sin(th1), c1 = Mth.cos(th1);
            putLit(mat, vc, x0, s0 * radius, c0 * radius, r, g, b, 0f, s0, c0);
            putLit(mat, vc, x1, s0 * radius, c0 * radius, r, g, b, 0f, s0, c0);
            putLit(mat, vc, x1, s1 * radius, c1 * radius, r, g, b, 0f, s1, c1);
            putLit(mat, vc, x0, s1 * radius, c1 * radius, r, g, b, 0f, s1, c1);

            putLit(mat, vc, x0, 0f, 0f, r, g, b, -1f, 0f, 0f);
            putLit(mat, vc, x0, s1 * radius, c1 * radius, r, g, b, -1f, 0f, 0f);
            putLit(mat, vc, x0, s0 * radius, c0 * radius, r, g, b, -1f, 0f, 0f);
            putLit(mat, vc, x0, 0f, 0f, r, g, b, -1f, 0f, 0f);

            putLit(mat, vc, x1, 0f, 0f, r, g, b, 1f, 0f, 0f);
            putLit(mat, vc, x1, s0 * radius, c0 * radius, r, g, b, 1f, 0f, 0f);
            putLit(mat, vc, x1, s1 * radius, c1 * radius, r, g, b, 1f, 0f, 0f);
            putLit(mat, vc, x1, 0f, 0f, r, g, b, 1f, 0f, 0f);
        }
    }

    public static void drawBox(PoseStack pose, VertexConsumer vc,
                               float x0, float y0, float z0, float x1, float y1, float z1,
                               float r, float g, float b) {
        Matrix4f mat = pose.last().pose();
        putLit(mat, vc, x0, y1, z0, r, g, b, 0f, 1f, 0f);
        putLit(mat, vc, x0, y1, z1, r, g, b, 0f, 1f, 0f);
        putLit(mat, vc, x1, y1, z1, r, g, b, 0f, 1f, 0f);
        putLit(mat, vc, x1, y1, z0, r, g, b, 0f, 1f, 0f);

        putLit(mat, vc, x0, y0, z0, r, g, b, 0f, -1f, 0f);
        putLit(mat, vc, x1, y0, z0, r, g, b, 0f, -1f, 0f);
        putLit(mat, vc, x1, y0, z1, r, g, b, 0f, -1f, 0f);
        putLit(mat, vc, x0, y0, z1, r, g, b, 0f, -1f, 0f);

        putLit(mat, vc, x0, y0, z0, r, g, b, 0f, 0f, -1f);
        putLit(mat, vc, x1, y0, z0, r, g, b, 0f, 0f, -1f);
        putLit(mat, vc, x1, y1, z0, r, g, b, 0f, 0f, -1f);
        putLit(mat, vc, x0, y1, z0, r, g, b, 0f, 0f, -1f);

        putLit(mat, vc, x0, y0, z1, r, g, b, 0f, 0f, 1f);
        putLit(mat, vc, x0, y1, z1, r, g, b, 0f, 0f, 1f);
        putLit(mat, vc, x1, y1, z1, r, g, b, 0f, 0f, 1f);
        putLit(mat, vc, x1, y0, z1, r, g, b, 0f, 0f, 1f);

        putLit(mat, vc, x0, y0, z0, r, g, b, -1f, 0f, 0f);
        putLit(mat, vc, x0, y1, z0, r, g, b, -1f, 0f, 0f);
        putLit(mat, vc, x0, y1, z1, r, g, b, -1f, 0f, 0f);
        putLit(mat, vc, x0, y0, z1, r, g, b, -1f, 0f, 0f);

        putLit(mat, vc, x1, y0, z0, r, g, b, 1f, 0f, 0f);
        putLit(mat, vc, x1, y0, z1, r, g, b, 1f, 0f, 0f);
        putLit(mat, vc, x1, y1, z1, r, g, b, 1f, 0f, 0f);
        putLit(mat, vc, x1, y1, z0, r, g, b, 1f, 0f, 0f);
    }

    /**
     * Studio 3-Point Directional Normal Shading: transforms local normal by upper-left rotation of `mat`
     * so rotating 3D items, digits, sausages, and logs exhibit genuine specular highlights & deep sculptural shadows!
     */
    public static void putLit(Matrix4f mat, VertexConsumer vc, float x, float y, float z,
                              float r, float g, float b, float nx, float ny, float nz) {
        Vector3f wn = new Vector3f(nx, ny, nz);
        mat.transformDirection(wn);
        float len = wn.length();
        if (len > 1e-5f) wn.div(len);

        // Key light from upper-front-right + warm bounce from lower-left
        float key = Math.max(0f, wn.x * 0.42f + wn.y * 0.74f + wn.z * 0.52f);
        float fill = Math.max(0f, -wn.x * 0.50f + wn.y * 0.25f - wn.z * 0.40f);
        float spec = (float) Math.pow(key, 5.0) * 0.20f;
        float shade = 0.60f + 0.42f * key + 0.14f * fill;

        float fr = Mth.clamp(r * shade + spec * 1.00f, 0f, 1f);
        float fg = Mth.clamp(g * shade + spec * 0.94f, 0f, 1f);
        float fb = Mth.clamp(b * shade + spec * 0.78f, 0f, 1f);

        vc.addVertex(mat, x, y, z)
                .setColor(fr, fg, fb, 1f)
                .setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(nx, ny, nz);
    }
}

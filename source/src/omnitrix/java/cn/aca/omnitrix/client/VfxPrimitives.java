package cn.aca.omnitrix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class VfxPrimitives {
    private static final float TAU = (float) (Math.PI * 2.0);
    static final int MODE_STARFIELD = 0;
    static final int MODE_ELEMENTAL = 1;
    static final int MODE_ASCALON_LUSTER = 2;
    static final int MODE_ASTROLABE_GEOM = 3;
    static final int MODE_VOID_SHEATH = 4;
    static final int MODE_BLADE_ASYM = 5;
    static final int MODE_SPEED_STREAK = 6;
    static final int FIRE = 0, CRYSTAL = 1, WING = 2, WATER = 3, CIRCUIT = 4, VOID = 5, STAR = 6, DUST = 7;
    private static final int MODE_MATERIAL = 7;
    private static final int MODE_PLANET = 15;
    private static final int MODE_ECLIPSE = 16;
    static final int MODE_VORONOI_SHATTER = 17;

    private VfxPrimitives() {}

    private static void emit(VertexConsumer buf, PoseStack.Pose pose, Vec3 p, float u, float v, int color, boolean starfield) {
        emitMode(buf, pose, p, u, v, color, starfield ? MODE_STARFIELD : MODE_ELEMENTAL, 0f);
    }

    private static void emitMode(VertexConsumer buf, PoseStack.Pose pose, Vec3 p, float u, float v, int color, int mode) {
        emitMode(buf, pose, p, u, v, color, mode, 0f);
    }

    private static void emitMode(VertexConsumer buf, PoseStack.Pose pose, Vec3 p, float u, float v,
                                 int color, int mode, float erosion) {
        if (!CosmicVfxShader.available()) {
            buf.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                    .setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
            return;
        }
        int erodeFixed = Math.round(Mth.clamp(erosion, 0f, 1f) * 1000f);
        buf.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(color)
                .setUv(u, v)
                .setUv1(mode, erodeFixed)
                .setUv2(LightTexture.FULL_BRIGHT & 0xFFFF, (LightTexture.FULL_BRIGHT >> 16) & 0xFFFF)
                .setNormal(pose, 0, 1, 0);
    }

    private static VertexConsumer buffer(MultiBufferSource buffers, boolean starfield, boolean additive) {
        return buffers.getBuffer(additive ? CosmicVfxShader.glow()
                : (starfield ? CosmicVfxShader.nebula() : CosmicVfxShader.glow()));
    }

    /** Smooth camera-lens clearance factor so 3rd-person trails never clip into blinding fullscreen polygons! */
    static float cameraClearance(Vec3 pt, Vec3 camera, double innerClear, double outerFull) {
        double d = pt.distanceTo(camera);
        float f = (float) Mth.clamp((d - innerClear) / Math.max(0.25, outerFull - innerClear), 0.0, 1.0);
        return f * f * (3f - 2f * f);
    }

    static void flowRibbon(PoseStack pose, MultiBufferSource buffers, Vec3 from, Vec3 to, Vec3 camera,
                           double widthStart, double widthEnd, int color, boolean starfield, boolean additive, float uScale) {
        ribbonMode(pose, buffers, from, to, camera, widthStart, widthEnd, color,
                starfield ? MODE_STARFIELD : MODE_ELEMENTAL, additive, uScale, 0f);
    }

    static void materialRibbon(PoseStack pose, MultiBufferSource buffers, Vec3 from, Vec3 to, Vec3 camera,
                               double widthStart, double widthEnd, int color, int material, float erosion) {
        float repetitions = (float) Math.clamp(from.distanceTo(to) * .40, .65, 12);
        ribbonMode(pose, buffers, from, to, camera, widthStart, widthEnd, color,
                MODE_MATERIAL + material, false, repetitions, erosion);
    }

    static void lusterRibbon(PoseStack pose, MultiBufferSource buffers, Vec3 from, Vec3 to, Vec3 camera,
                             double widthStart, double widthEnd, int color, float uScale) {
        lusterRibbonEroded(pose, buffers, from, to, camera, widthStart, widthEnd, color, uScale, 0.12f, true);
    }

    /** Dual-pass High-Contrast Ribbon: Abyssal Star-Void Dark Sheath + Eroded Incandescent Core Filament! */
    static void lusterRibbonEroded(PoseStack pose, MultiBufferSource buffers, Vec3 from, Vec3 to, Vec3 camera,
                                   double widthStart, double widthEnd, int color, float uScale,
                                   float erosion, boolean darkUnderlay) {
        float baseA = (color >>> 24) / 255f;
        if (darkUnderlay && baseA > 0.12f && (widthStart > 0.06 || widthEnd > 0.06)) {
            int voidCol = DialDraw.alpha(0xff0b152c, Math.min(0.76f, baseA * 0.72f));
            ribbonMode(pose, buffers, from, to, camera, widthStart * 1.48, widthEnd * 1.48,
                    voidCol, MODE_VOID_SHEATH, false, uScale * 0.85f, erosion * 0.85f);
        }
        ribbonMode(pose, buffers, from, to, camera, widthStart, widthEnd,
                color, MODE_ASCALON_LUSTER, true, uScale, erosion);
    }

    /**
     * V1 Stylized Next-Gen Curved Multi-Strand Energy Whip (HSR / Destiny-2 style):
     * Replaces stiff single straight beams with a 3D organic curved spine + 2 counter-spiraling twisted filaments!
     */
    static void curvedEnergyWhip(PoseStack pose, MultiBufferSource buffers, Vec3 from, Vec3 to, Vec3 camera,
                                 double widthStart, double widthEnd, double arcAmp, float phase,
                                 int primaryColor, int secondaryColor, float erosion) {
        Vec3 delta = to.subtract(from);
        double len = delta.length();
        if (len < 0.08) return;
        Vec3 dir = delta.scale(1.0 / len);
        Vec3 ref = Math.abs(dir.y) > 0.92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 sideAxis = dir.cross(ref).normalize();
        Vec3 upAxis = sideAxis.cross(dir).normalize();

        int steps = from.lerp(to, 0.5).distanceToSqr(camera) > 52 * 52 ? 10 : 18;
        var mainSpine = new ArrayList<Vec3>(steps + 1);
        var strandA = new ArrayList<Vec3>(steps + 1);
        var strandB = new ArrayList<Vec3>(steps + 1);

        double effAmp = Math.min(len * 0.16, arcAmp);
        double helixR = Math.max(widthStart, widthEnd) * 1.35 + effAmp * 0.25;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            double env = Math.sin(t * Math.PI);
            double bendA = Math.sin(t * Math.PI * 1.35 + phase) * effAmp * env;
            double bendB = Math.cos(t * Math.PI * 1.15 - phase * 0.7f) * effAmp * 0.55 * env;
            Vec3 base = from.lerp(to, t).add(sideAxis.scale(bendA)).add(upAxis.scale(bendB));
            mainSpine.add(base);

            double twist = t * TAU * Math.clamp(len * 0.28, 1.4, 4.2) - phase * 2.2;
            Vec3 radialA = sideAxis.scale(Math.cos(twist) * helixR * env).add(upAxis.scale(Math.sin(twist) * helixR * env));
            Vec3 radialB = sideAxis.scale(Math.cos(twist + Math.PI * 0.85) * helixR * 0.85 * env)
                    .add(upAxis.scale(Math.sin(twist + Math.PI * 0.85) * helixR * 0.85 * env));
            strandA.add(base.add(radialA));
            strandB.add(base.add(radialB));
        }

        float baseA = (primaryColor >>> 24) / 255f;
        if (baseA > 0.14f) {
            taperedPolylineMode(pose, buffers, mainSpine, camera, widthStart * 1.52, widthEnd * 1.45,
                    DialDraw.alpha(0xff081126, Math.min(0.76f, baseA * 0.68f)), MODE_VOID_SHEATH, false, erosion * 0.82f);
        }
        taperedPolylineMode(pose, buffers, mainSpine, camera, widthStart, widthEnd,
                primaryColor, MODE_ASCALON_LUSTER, true, erosion);
        taperedPolylineMode(pose, buffers, strandA, camera, widthStart * 0.44, widthEnd * 0.34,
                secondaryColor, MODE_ASCALON_LUSTER, true, Math.min(1f, erosion + 0.12f));
        taperedPolylineMode(pose, buffers, strandB, camera, widthStart * 0.36, widthEnd * 0.28,
                DialDraw.alpha(0xfffafcff, baseA * 0.82f), MODE_ASCALON_LUSTER, true, Math.min(1f, erosion + 0.18f));
    }

    private static void taperedPolylineMode(PoseStack pose, MultiBufferSource buffers, List<Vec3> pts, Vec3 camera,
                                            double wStart, double wEnd, int color, int mode, boolean additive, float erosion) {
        int n = pts.size();
        if (n < 2) return;
        PoseStack.Pose last = pose.last();
        VertexConsumer sink = buffers.getBuffer(additive ? CosmicVfxShader.glow() : CosmicVfxShader.voidSheath());
        float uScale = (float) Math.clamp(pts.getFirst().distanceTo(pts.getLast()) * 0.45, 1.0, 8.0);
        for (int i = 0; i < n - 1; i++) {
            float t0 = (float) i / (n - 1);
            float t1 = (float) (i + 1) / (n - 1);
            Vec3 a = pts.get(i);
            Vec3 b = pts.get(i + 1);
            Vec3 seg = b.subtract(a);
            if (seg.lengthSqr() < 1e-6) continue;
            double w0 = Mth.lerp(t0, wStart, wEnd) * Math.sin(Math.PI * Math.clamp(t0 * 0.90 + 0.05, 0.05, 0.95));
            double w1 = Mth.lerp(t1, wStart, wEnd) * Math.sin(Math.PI * Math.clamp(t1 * 0.90 + 0.05, 0.05, 0.95));
            Vec3 s0 = seg.cross(camera.subtract(a));
            s0 = s0.lengthSqr() < 1e-6 ? new Vec3(0, w0, 0) : s0.normalize().scale(w0);
            Vec3 s1 = seg.cross(camera.subtract(b));
            s1 = s1.lengthSqr() < 1e-6 ? new Vec3(0, w1, 0) : s1.normalize().scale(w1);
            emitMode(sink, last, a.subtract(s0), t0 * uScale, 0f, color, mode, erosion);
            emitMode(sink, last, a.add(s0), t0 * uScale, 1f, color, mode, erosion);
            emitMode(sink, last, b.add(s1), t1 * uScale, 1f, color, mode, erosion);
            emitMode(sink, last, b.subtract(s1), t1 * uScale, 0f, color, mode, erosion);
        }
    }

    /**
     * V1 Mode-17 Voronoi Mirror Space-Shatter Crown:
     * Renders tilted dimensional fracture rings & faceted crystalline space shards!
     */
    static void voronoiShatterCrown(PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 axisDir,
                                    Vec3 camera, double radius, double crownHeight, float spin,
                                    int color, float erosion) {
        if (radius <= 0.08) return;
        float clear = camera == null ? 1f : cameraClearance(center, camera, 1.25, 3.0);
        if (clear <= 0.02f) return;
        Vec3 n = axisDir.lengthSqr() < 1e-5 ? new Vec3(0, 1, 0) : axisDir.normalize();
        Vec3 ref = Math.abs(n.y) > 0.92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = n.cross(ref).normalize();
        Vec3 v = n.cross(u).normalize();

        PoseStack.Pose last = pose.last();
        int effCol = DialDraw.alpha(color, ((color >>> 24) / 255f) * clear);
        int darkCol = DialDraw.alpha(0xff070f24, ((color >>> 24) / 255f) * clear * 0.58f);
        int steps = 28;

        VertexConsumer voidSink = buffers.getBuffer(CosmicVfxShader.voidSheath());
        VertexConsumer glowSink = buffers.getBuffer(CosmicVfxShader.glow());
        for (int i = 0; i < steps; i++) {
            float f0 = (float) i / steps;
            float f1 = (float) (i + 1) / steps;
            double a0 = spin + TAU * f0;
            double a1 = spin + TAU * f1;
            double jag0 = 0.55 + 0.45 * Math.sin(a0 * 5.0 - spin * 1.7);
            double jag1 = 0.55 + 0.45 * Math.sin(a1 * 5.0 - spin * 1.7);

            Vec3 radial0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
            Vec3 radial1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
            Vec3 bot0 = center.add(radial0.scale(radius * 0.88));
            Vec3 bot1 = center.add(radial1.scale(radius * 0.88));
            Vec3 top0 = center.add(radial0.scale(radius * (1.04 + 0.12 * jag0))).add(n.scale(crownHeight * jag0));
            Vec3 top1 = center.add(radial1.scale(radius * (1.04 + 0.12 * jag1))).add(n.scale(crownHeight * jag1));

            emitMode(voidSink, last, bot0, f0 * 3f, 0f, darkCol, MODE_VOID_SHEATH, erosion * 0.85f);
            emitMode(voidSink, last, top0, f0 * 3f, 1f, darkCol, MODE_VOID_SHEATH, erosion * 0.85f);
            emitMode(voidSink, last, top1, f1 * 3f, 1f, darkCol, MODE_VOID_SHEATH, erosion * 0.85f);
            emitMode(voidSink, last, bot1, f1 * 3f, 0f, darkCol, MODE_VOID_SHEATH, erosion * 0.85f);

            emitMode(glowSink, last, bot0, f0 * 3f, 0f, effCol, MODE_VORONOI_SHATTER, erosion);
            emitMode(glowSink, last, top0, f0 * 3f, 1f, effCol, MODE_VORONOI_SHATTER, erosion);
            emitMode(glowSink, last, top1, f1 * 3f, 1f, effCol, MODE_VORONOI_SHATTER, erosion);
            emitMode(glowSink, last, bot1, f1 * 3f, 0f, effCol, MODE_VORONOI_SHATTER, erosion);
        }
    }

    /**
     * V1 3D Faceted Hexagonal Crystal Spire (for Diamondhead surge & crystalline constructs):
     * True 6-sided prism + pyramidal crystal tip + internal dark refraction core!
     */
    static void hexCrystalSpire(PoseStack pose, MultiBufferSource buffers, Vec3 base, Vec3 upDir,
                                double radius, double height, int color, float erosion) {
        if (height <= 0.05 || radius <= 0.02) return;
        Vec3 up = upDir.lengthSqr() < 1e-5 ? new Vec3(0, 1, 0) : upDir.normalize();
        Vec3 ref = Math.abs(up.y) > 0.92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = up.cross(ref).normalize();
        Vec3 v = up.cross(u).normalize();

        PoseStack.Pose last = pose.last();
        float baseA = (color >>> 24) / 255f;
        int darkCore = DialDraw.alpha(0xff091a28, Math.min(0.78f, baseA * 0.72f));
        int apexCol = DialDraw.alpha(0xfff2ffff, Math.min(0.98f, baseA * 1.05f));

        Vec3 shoulderCenter = base.add(up.scale(height * 0.72));
        Vec3 apex = base.add(up.scale(height));

        VertexConsumer voidSink = buffers.getBuffer(CosmicVfxShader.voidSheath());
        VertexConsumer glowSink = buffers.getBuffer(CosmicVfxShader.glow());
        for (int i = 0; i < 6; i++) {
            double a0 = TAU * i / 6.0;
            double a1 = TAU * (i + 1) / 6.0;
            Vec3 r0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
            Vec3 r1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));

            Vec3 b0 = base.add(r0.scale(radius * 0.82));
            Vec3 b1 = base.add(r1.scale(radius * 0.82));
            Vec3 m0 = shoulderCenter.add(r0.scale(radius * 0.58));
            Vec3 m1 = shoulderCenter.add(r1.scale(radius * 0.58));

            emitMode(voidSink, last, base.add(r0.scale(radius * 0.55)), i / 6f, 0f, darkCore, MODE_VOID_SHEATH, erosion * 0.7f);
            emitMode(voidSink, last, shoulderCenter.add(r0.scale(radius * 0.35)), i / 6f, 0.8f, darkCore, MODE_VOID_SHEATH, erosion * 0.7f);
            emitMode(voidSink, last, shoulderCenter.add(r1.scale(radius * 0.35)), (i + 1) / 6f, 0.8f, darkCore, MODE_VOID_SHEATH, erosion * 0.7f);
            emitMode(voidSink, last, base.add(r1.scale(radius * 0.55)), (i + 1) / 6f, 0f, darkCore, MODE_VOID_SHEATH, erosion * 0.7f);

            int facetCol = (i & 1) == 0 ? color : DialDraw.alpha(color, baseA * 0.80f);
            emitMode(glowSink, last, b0, i / 6f, 0.1f, facetCol, MODE_ASCALON_LUSTER, erosion * 0.65f);
            emitMode(glowSink, last, m0, i / 6f, 0.75f, facetCol, MODE_ASCALON_LUSTER, erosion * 0.65f);
            emitMode(glowSink, last, m1, (i + 1) / 6f, 0.75f, facetCol, MODE_ASCALON_LUSTER, erosion * 0.65f);
            emitMode(glowSink, last, b1, (i + 1) / 6f, 0.1f, facetCol, MODE_ASCALON_LUSTER, erosion * 0.65f);

            emitMode(glowSink, last, m0, i / 6f, 0.75f, facetCol, MODE_BLADE_ASYM, erosion * 0.5f);
            emitMode(glowSink, last, apex, (i + 0.5f) / 6f, 0.98f, apexCol, MODE_BLADE_ASYM, erosion * 0.5f);
            emitMode(glowSink, last, apex, (i + 0.5f) / 6f, 0.98f, apexCol, MODE_BLADE_ASYM, erosion * 0.5f);
            emitMode(glowSink, last, m1, (i + 1) / 6f, 0.75f, facetCol, MODE_BLADE_ASYM, erosion * 0.5f);
        }
    }

    /**
     * V1 Floating 3D Control Crown (Galvanic Construct Hijack / Ectonurite Possession / Soul-Terror):
     */
    static void controlCrown(PoseStack pose, MultiBufferSource buffers, Vec3 headPos, Vec3 camera,
                             float time, boolean galvanic, boolean terror, boolean celestial, float alpha) {
        if (alpha <= 0.02f) return;
        int tint = terror ? (celestial ? 0xffe49eff : 0xffcc7eff)
                : galvanic ? (celestial ? 0xff92ffdc : 0xff62f89c)
                : (celestial ? 0xffd6b2ff : 0xffb292ff);
        int col = DialDraw.alpha(tint, alpha * 0.92f);
        if (terror) {
            voronoiShatterCrown(pose, buffers, headPos, new Vec3(0, 1, 0), camera,
                    0.46, 0.32, -time * 0.16f, col, 0.18f);
            return;
        }
        astrolabeRingEroded(pose, buffers, headPos, 0.52, 0.16, time * 0.12, time * 0.12 + TAU, 28, col, 0.08f);
        for (int i = 0; i < 4; i++) {
            double ang = time * 0.12 + i * TAU / 4.0;
            Vec3 top = headPos.add(Math.cos(ang) * 0.46, 0.04, Math.sin(ang) * 0.46);
            Vec3 bot = headPos.add(Math.cos(ang) * 0.18, -0.58, Math.sin(ang) * 0.18);
            speedStreak(pose, buffers, bot, top, camera, 0.024, col, 0.12f);
        }
    }

    private static void ribbonMode(PoseStack pose, MultiBufferSource buffers, Vec3 from, Vec3 to, Vec3 camera,
                                   double widthStart, double widthEnd, int color, int mode,
                                   boolean additive, float uScale, float erosion) {
        Vec3 delta = to.subtract(from);
        double len = delta.length();
        if (len < 1e-3) return;
        int limit = from.lerp(to, .5).distanceToSqr(camera) > 48 * 48 ? 8 : 20;
        int steps = Math.max(4, Math.min(limit, (int) Math.ceil(len * 2.5)));
        PoseStack.Pose last = pose.last();
        VertexConsumer sink = buffers.getBuffer(mode >= MODE_MATERIAL && mode < MODE_VORONOI_SHATTER
                ? CosmicVfxShader.material()
                : additive ? CosmicVfxShader.glow() : CosmicVfxShader.voidSheath());

        for (int i = 0; i < steps; i++) {
            float t0 = (float) i / steps;
            float t1 = (float) (i + 1) / steps;
            Vec3 a = from.lerp(to, t0);
            Vec3 b = from.lerp(to, t1);
            double w0 = Mth.lerp(t0, widthStart, widthEnd) * Math.sin(Math.PI * Math.clamp(t0 * .88 + .06, 0.04, 0.96));
            double w1 = Mth.lerp(t1, widthStart, widthEnd) * Math.sin(Math.PI * Math.clamp(t1 * .88 + .06, 0.04, 0.96));
            Vec3 side0 = delta.cross(camera.subtract(a));
            side0 = side0.lengthSqr() < 1e-6 ? new Vec3(0, w0, 0) : side0.normalize().scale(w0);
            Vec3 side1 = delta.cross(camera.subtract(b));
            side1 = side1.lengthSqr() < 1e-6 ? new Vec3(0, w1, 0) : side1.normalize().scale(w1);

            emitMode(sink, last, a.subtract(side0), t0 * uScale, 0f, color, mode, erosion);
            emitMode(sink, last, a.add(side0), t0 * uScale, 1f, color, mode, erosion);
            emitMode(sink, last, b.add(side1), t1 * uScale, 1f, color, mode, erosion);
            emitMode(sink, last, b.subtract(side1), t1 * uScale, 0f, color, mode, erosion);
        }
    }

    /** Razor-Thin Anamorphic Speed Streak / Starlight Spark (Mode 6). */
    static void speedStreak(PoseStack pose, MultiBufferSource buffers, Vec3 tail, Vec3 head, Vec3 camera,
                            double halfWidth, int color, float erosion) {
        Vec3 delta = head.subtract(tail);
        if (delta.lengthSqr() < 1e-5) return;
        Vec3 side = delta.cross(camera.subtract(head));
        side = side.lengthSqr() < 1e-6 ? new Vec3(0, halfWidth, 0) : side.normalize().scale(halfWidth);
        PoseStack.Pose last = pose.last();
        VertexConsumer sink = buffers.getBuffer(CosmicVfxShader.glow());
        emitMode(sink, last, tail.subtract(side), 0f, 0f, color, MODE_SPEED_STREAK, erosion);
        emitMode(sink, last, tail.add(side), 0f, 1f, color, MODE_SPEED_STREAK, erosion);
        emitMode(sink, last, head.add(side), 1f, 1f, color, MODE_SPEED_STREAK, erosion);
        emitMode(sink, last, head.subtract(side), 1f, 0f, color, MODE_SPEED_STREAK, erosion);
    }

    static void polylineRibbon(PoseStack pose, MultiBufferSource buffers, List<Vec3> points, Vec3 camera,
                               double maxHalfWidth, int color, boolean starfield, boolean additive) {
        polylineMode(pose, buffers, points, camera, maxHalfWidth, color,
                starfield ? MODE_STARFIELD : MODE_ELEMENTAL, additive, false);
    }

    static void lusterPolyline(PoseStack pose, MultiBufferSource buffers, List<Vec3> points, Vec3 camera,
                               double maxHalfWidth, int color) {
        if (points.size() >= 2 && maxHalfWidth >= 0.07) {
            float baseA = (color >>> 24) / 255f;
            polylineMode(pose, buffers, points, camera, maxHalfWidth * 1.45,
                    DialDraw.alpha(0xff0b152c, baseA * 0.66f), MODE_VOID_SHEATH, false, true);
        }
        polylineMode(pose, buffers, points, camera, maxHalfWidth, color, MODE_ASCALON_LUSTER, true, true);
    }

    private static void polylineMode(PoseStack pose, MultiBufferSource buffers, List<Vec3> points, Vec3 camera,
                                     double maxHalfWidth, int color, int mode, boolean additive, boolean erodeTail) {
        int n = points.size();
        if (n < 2) return;
        PoseStack.Pose last = pose.last();
        VertexConsumer sink = buffers.getBuffer(additive ? CosmicVfxShader.glow() : CosmicVfxShader.voidSheath());
        for (int i = 0; i < n - 1; i++) {
            float t0 = (float) i / (n - 1);
            float t1 = (float) (i + 1) / (n - 1);
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            Vec3 seg = b.subtract(a);
            if (seg.lengthSqr() < 1e-6) continue;
            float clear0 = erodeTail ? cameraClearance(a, camera, 1.05, 2.8) : 1f;
            float clear1 = erodeTail ? cameraClearance(b, camera, 1.05, 2.8) : 1f;
            double w0 = maxHalfWidth * Math.pow(Math.sin(t0 * Math.PI * .94 + .04), 1.12) * (.35 + .65 * clear0);
            double w1 = maxHalfWidth * Math.pow(Math.sin(t1 * Math.PI * .94 + .04), 1.12) * (.35 + .65 * clear1);
            Vec3 s0 = seg.cross(camera.subtract(a));
            s0 = s0.lengthSqr() < 1e-6 ? new Vec3(0, w0, 0) : s0.normalize().scale(w0);
            Vec3 s1 = seg.cross(camera.subtract(b));
            s1 = s1.lengthSqr() < 1e-6 ? new Vec3(0, w1, 0) : s1.normalize().scale(w1);

            int c0 = DialDraw.alpha(color, ((color >>> 24) / 255f) * (erodeTail ? (.35f + .65f * t0) * clear0 : t0));
            int c1 = DialDraw.alpha(color, ((color >>> 24) / 255f) * (erodeTail ? (.35f + .65f * t1) * clear1 : t1));
            float er0 = erodeTail ? (1f - t0) * .88f : 0f;
            float er1 = erodeTail ? (1f - t1) * .88f : 0f;
            emitMode(sink, last, a.subtract(s0), t0 * 1.8f, 0f, c0, mode, er0);
            emitMode(sink, last, a.add(s0), t0 * 1.8f, 1f, c0, mode, er0);
            emitMode(sink, last, b.add(s1), t1 * 1.8f, 1f, c1, mode, er1);
            emitMode(sink, last, b.subtract(s1), t1 * 1.8f, 0f, c1, mode, er1);
        }
    }

    static void overflowingSilkRibbon(PoseStack pose, MultiBufferSource buffers, List<Vec3> points, List<Vec3> wingAxes,
                                      Vec3 camera, double maxHalfWidth, float uScroll, int veilColor, int spineColor) {
        int n = points.size();
        if (n < 2) return;
        PoseStack.Pose last = pose.last();
        float veilBaseA = (veilColor >>> 24) / 255f;
        float spineBaseA = (spineColor >>> 24) / 255f;

        Vec3[] widths = new Vec3[n];
        float[] alphas = new float[n];
        float[] erosions = new float[n];
        float[] uCoords = new float[n];
        float cumDist = 0f;

        for (int i = 0; i < n; i++) {
            float t = (float) i / (n - 1);
            Vec3 p = points.get(i);
            if (i > 0) {
                cumDist += (float) Math.min(2.6, p.distanceTo(points.get(i - 1)));
            }
            uCoords[i] = cumDist * .34f - uScroll;

            Vec3 prev = points.get(Math.max(0, i - 1));
            Vec3 next = points.get(Math.min(n - 1, i + 1));
            Vec3 tangent = next.subtract(prev);
            tangent = tangent.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : tangent.normalize();

            Vec3 camSide = tangent.cross(camera.subtract(p));
            camSide = camSide.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : camSide.normalize();

            Vec3 wingSide = (wingAxes != null && i < wingAxes.size()) ? wingAxes.get(i) : camSide;
            if (wingSide.dot(camSide) < 0) wingSide = wingSide.scale(-1.0);
            Vec3 hybridSide = camSide.scale(.58).add(wingSide.scale(.42));
            hybridSide = hybridSide.lengthSqr() < 1e-6 ? camSide : hybridSide.normalize();

            float camClear = cameraClearance(p, camera, 1.55, 4.10);
            double envelope = (0.28 + 0.72 * Math.sin(Math.min(1.0, t * 1.08 + .06) * Math.PI * .84)) * (0.22 + 0.78 * camClear);
            widths[i] = hybridSide.scale(maxHalfWidth * envelope);
            alphas[i] = camClear * (0.38f + 0.62f * (float) Math.pow(t, .55));
            erosions[i] = Mth.clamp((float) Math.pow(1.0 - t, 0.82) * 0.90f + 0.04f, 0.04f, 0.94f);
        }

        VertexConsumer voidSink = buffers.getBuffer(CosmicVfxShader.voidSheath());
        for (int i = 0; i < n - 1; i++) {
            if (alphas[i] < 0.02f && alphas[i + 1] < 0.02f) continue;
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            Vec3 vw0 = widths[i].scale(1.32);
            Vec3 vw1 = widths[i + 1].scale(1.32);
            int d0 = DialDraw.alpha(0xff081228, Math.min(0.74f, veilBaseA * 0.88f * alphas[i]));
            int d1 = DialDraw.alpha(0xff081228, Math.min(0.74f, veilBaseA * 0.88f * alphas[i + 1]));
            emitMode(voidSink, last, a.subtract(vw0), uCoords[i], 0f, d0, MODE_VOID_SHEATH, erosions[i] * .86f);
            emitMode(voidSink, last, a.add(vw0), uCoords[i], 1f, d0, MODE_VOID_SHEATH, erosions[i] * .86f);
            emitMode(voidSink, last, b.add(vw1), uCoords[i + 1], 1f, d1, MODE_VOID_SHEATH, erosions[i + 1] * .86f);
            emitMode(voidSink, last, b.subtract(vw1), uCoords[i + 1], 0f, d1, MODE_VOID_SHEATH, erosions[i + 1] * .86f);
        }

        VertexConsumer glowSink = buffers.getBuffer(CosmicVfxShader.glow());
        for (int i = 0; i < n - 1; i++) {
            if (alphas[i] < 0.02f && alphas[i + 1] < 0.02f) continue;
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            Vec3 w0 = widths[i];
            Vec3 w1 = widths[i + 1];
            int vc0 = DialDraw.alpha(veilColor, veilBaseA * alphas[i] * .76f);
            int vc1 = DialDraw.alpha(veilColor, veilBaseA * alphas[i + 1] * .76f);
            float u0 = uCoords[i];
            float u1 = uCoords[i + 1];

            emitMode(glowSink, last, a.subtract(w0), u0, 0f, vc0, MODE_ASCALON_LUSTER, erosions[i]);
            emitMode(glowSink, last, a.add(w0), u0, 1f, vc0, MODE_ASCALON_LUSTER, erosions[i]);
            emitMode(glowSink, last, b.add(w1), u1, 1f, vc1, MODE_ASCALON_LUSTER, erosions[i + 1]);
            emitMode(glowSink, last, b.subtract(w1), u1, 0f, vc1, MODE_ASCALON_LUSTER, erosions[i + 1]);

            Vec3 c0 = w0.scale(.19);
            Vec3 c1 = w1.scale(.19);
            int sc0 = DialDraw.alpha(spineColor, spineBaseA * alphas[i]);
            int sc1 = DialDraw.alpha(spineColor, spineBaseA * alphas[i + 1]);
            emitMode(glowSink, last, a.subtract(c0), u0 * 1.25f, .22f, sc0, MODE_ASCALON_LUSTER, erosions[i] * .72f);
            emitMode(glowSink, last, a.add(c0), u0 * 1.25f, .78f, sc0, MODE_ASCALON_LUSTER, erosions[i] * .72f);
            emitMode(glowSink, last, b.add(c1), u1 * 1.25f, .78f, sc1, MODE_ASCALON_LUSTER, erosions[i + 1] * .72f);
            emitMode(glowSink, last, b.subtract(c1), u1 * 1.25f, .22f, sc1, MODE_ASCALON_LUSTER, erosions[i + 1] * .72f);
        }
    }

    static void orientedAstrolabeRing(PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 axisDir,
                                      double radius, double bandWidth, double spin, int steps, int color) {
        orientedAstrolabeRingEroded(pose, buffers, center, axisDir, null, radius, bandWidth, spin, steps, color, 0f);
    }

    static void orientedAstrolabeRingEroded(PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 axisDir,
                                            Vec3 camera, double radius, double bandWidth, double spin,
                                            int steps, int color, float erosion) {
        if (radius <= .03) return;
        float clear = camera == null ? 1f : cameraClearance(center, camera, 1.35, 3.2);
        if (clear <= 0.02f) return;
        int effCol = DialDraw.alpha(color, ((color >>> 24) / 255f) * clear);

        Vec3 n = axisDir.lengthSqr() < 1e-5 ? new Vec3(0, 1, 0) : axisDir.normalize();
        Vec3 ref = Math.abs(n.y) > .92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = n.cross(ref).normalize();
        Vec3 v = n.cross(u).normalize();

        PoseStack.Pose last = pose.last();
        double rInner = Math.max(.02, radius - bandWidth * .55);
        double rOuter = radius + bandWidth * .45;

        float baseA = (effCol >>> 24) / 255f;
        if (baseA > 0.16f) {
            VertexConsumer voidSink = buffers.getBuffer(CosmicVfxShader.voidSheath());
            int darkCol = DialDraw.alpha(0xff081226, baseA * 0.54f);
            for (int i = 0; i < steps; i++) {
                float f0 = (float) i / steps, f1 = (float) (i + 1) / steps;
                double a0 = spin + TAU * f0, a1 = spin + TAU * f1;
                Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
                Vec3 d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
                emitMode(voidSink, last, center.add(d0.scale(rInner * .96)), f0, 0f, darkCol, MODE_VOID_SHEATH, erosion);
                emitMode(voidSink, last, center.add(d0.scale(rOuter * 1.04)), f0, 1f, darkCol, MODE_VOID_SHEATH, erosion);
                emitMode(voidSink, last, center.add(d1.scale(rOuter * 1.04)), f1, 1f, darkCol, MODE_VOID_SHEATH, erosion);
                emitMode(voidSink, last, center.add(d1.scale(rInner * .96)), f1, 0f, darkCol, MODE_VOID_SHEATH, erosion);
            }
        }

        VertexConsumer sink = buffers.getBuffer(CosmicVfxShader.glow());
        for (int i = 0; i < steps; i++) {
            float f0 = (float) i / steps;
            float f1 = (float) (i + 1) / steps;
            double a0 = spin + TAU * f0;
            double a1 = spin + TAU * f1;
            Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
            Vec3 d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
            emitMode(sink, last, center.add(d0.scale(rInner)), f0, 0f, effCol, MODE_ASTROLABE_GEOM, erosion);
            emitMode(sink, last, center.add(d0.scale(rOuter)), f0, 1f, effCol, MODE_ASTROLABE_GEOM, erosion);
            emitMode(sink, last, center.add(d1.scale(rOuter)), f1, 1f, effCol, MODE_ASTROLABE_GEOM, erosion);
            emitMode(sink, last, center.add(d1.scale(rInner)), f1, 0f, effCol, MODE_ASTROLABE_GEOM, erosion);
        }
    }

    static void bowShockWave(PoseStack pose, MultiBufferSource buffers, Vec3 apex, Vec3 flightDir, Vec3 wingRight,
                             Vec3 camera, double spanRadius, double sweepBack, int color) {
        float clear = cameraClearance(apex, camera, 1.15, 2.8);
        if (clear <= 0.02f) return;
        Vec3 fwd = flightDir.lengthSqr() < 1e-5 ? new Vec3(0, 0, 1) : flightDir.normalize();
        Vec3 right = wingRight.lengthSqr() < 1e-5 ? new Vec3(1, 0, 0) : wingRight.normalize();
        Vec3 up = right.cross(fwd).normalize();

        PoseStack.Pose last = pose.last();
        float baseA = ((color >>> 24) / 255f) * clear;
        int steps = 20;

        VertexConsumer voidSink = buffers.getBuffer(CosmicVfxShader.voidSheath());
        for (int plane = 0; plane < 2; plane++) {
            Vec3 lat = plane == 0 ? right : up.scale(.68);
            double planeAlpha = plane == 0 ? 1.0 : 0.62;
            int dc = DialDraw.alpha(0xff081228, (float) (baseA * 0.58 * planeAlpha));
            for (int i = 0; i < steps; i++) {
                float f0 = (float) i / steps;
                float f1 = (float) (i + 1) / steps;
                double s0 = (f0 - 0.5) * 2.0;
                double s1 = (f1 - 0.5) * 2.0;
                double taper0 = Math.pow(Math.max(0.0, 1.0 - s0 * s0), 0.85);
                double taper1 = Math.pow(Math.max(0.0, 1.0 - s1 * s1), 0.85);
                Vec3 lead0 = apex.add(lat.scale(s0 * spanRadius)).subtract(fwd.scale(s0 * s0 * sweepBack));
                Vec3 lead1 = apex.add(lat.scale(s1 * spanRadius)).subtract(fwd.scale(s1 * s1 * sweepBack));
                Vec3 wake0 = lead0.subtract(fwd.scale(0.14 + 0.34 * taper0));
                Vec3 wake1 = lead1.subtract(fwd.scale(0.14 + 0.34 * taper1));
                emitMode(voidSink, last, wake0, f0, 0f, dc, MODE_VOID_SHEATH, 0.32f);
                emitMode(voidSink, last, lead0, f0, 1f, dc, MODE_VOID_SHEATH, 0.12f);
                emitMode(voidSink, last, lead1, f1, 1f, dc, MODE_VOID_SHEATH, 0.12f);
                emitMode(voidSink, last, wake1, f1, 0f, dc, MODE_VOID_SHEATH, 0.32f);
            }
        }

        VertexConsumer glowSink = buffers.getBuffer(CosmicVfxShader.glow());
        for (int plane = 0; plane < 2; plane++) {
            Vec3 lat = plane == 0 ? right : up.scale(.68);
            double planeAlpha = plane == 0 ? 1.0 : 0.62;
            int gc = DialDraw.alpha(color, (float) (baseA * planeAlpha));
            for (int i = 0; i < steps; i++) {
                float f0 = (float) i / steps;
                float f1 = (float) (i + 1) / steps;
                double s0 = (f0 - 0.5) * 2.0;
                double s1 = (f1 - 0.5) * 2.0;
                double taper0 = Math.pow(Math.max(0.0, 1.0 - s0 * s0), 0.85);
                double taper1 = Math.pow(Math.max(0.0, 1.0 - s1 * s1), 0.85);
                Vec3 lead0 = apex.add(lat.scale(s0 * spanRadius)).subtract(fwd.scale(s0 * s0 * sweepBack));
                Vec3 lead1 = apex.add(lat.scale(s1 * spanRadius)).subtract(fwd.scale(s1 * s1 * sweepBack));
                Vec3 wake0 = lead0.subtract(fwd.scale(0.14 + 0.34 * taper0));
                Vec3 wake1 = lead1.subtract(fwd.scale(0.14 + 0.34 * taper1));
                emitMode(glowSink, last, wake0, f0 * 1.4f, 0f, gc, MODE_BLADE_ASYM, 0.22f);
                emitMode(glowSink, last, lead0, f0 * 1.4f, 1f, gc, MODE_BLADE_ASYM, 0.04f);
                emitMode(glowSink, last, lead1, f1 * 1.4f, 1f, gc, MODE_BLADE_ASYM, 0.04f);
                emitMode(glowSink, last, wake1, f1 * 1.4f, 0f, gc, MODE_BLADE_ASYM, 0.22f);
            }
        }
    }

    static void slashArc(PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 forward,
                         double radius, double bladeWidth, float tiltDeg, float startAngle, float sweepAngle,
                         int color, boolean starfield) {
        slashArcMode(pose, buffers, center, forward, radius, bladeWidth, tiltDeg, startAngle, sweepAngle, color,
                starfield ? MODE_STARFIELD : MODE_ELEMENTAL, !starfield, 0f);
    }

    static void lusterSlashArc(PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 forward,
                               double radius, double bladeWidth, float tiltDeg, float startAngle, float sweepAngle,
                               int color) {
        lusterSlashArcEroded(pose, buffers, center, forward, radius, bladeWidth, tiltDeg, startAngle, sweepAngle, color, 0.18f);
    }

    static void lusterSlashArcEroded(PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 forward,
                                     double radius, double bladeWidth, float tiltDeg, float startAngle, float sweepAngle,
                                     int color, float erosion) {
        float baseA = (color >>> 24) / 255f;
        int darkCol = DialDraw.alpha(0xff071024, Math.min(0.80f, baseA * 0.78f));
        slashArcMode(pose, buffers, center, forward, radius * 1.02, bladeWidth * 1.26, tiltDeg,
                startAngle * 1.03f, sweepAngle * 1.03f, darkCol, MODE_VOID_SHEATH, false, erosion * 0.85f);
        slashArcMode(pose, buffers, center, forward, radius, bladeWidth, tiltDeg,
                startAngle, sweepAngle, color, MODE_BLADE_ASYM, true, erosion);
    }

    static void lusterCrescent(PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 forward,
                               double radius, double bladeWidth, float rollDeg, float start, float sweep, int color) {
        lusterCrescentEroded(pose, buffers, center, forward, radius, bladeWidth, rollDeg, start, sweep, color, 0.14f);
    }

    static void lusterCrescentEroded(PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 forward,
                                     double radius, double bladeWidth, float rollDeg, float start, float sweep,
                                     int color, float erosion) {
        Vec3 fwd = forward.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : forward.normalize();
        Vec3 ref = Math.abs(fwd.y) > .92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = fwd.cross(ref).normalize();
        Vec3 up = right.cross(fwd).normalize();
        double roll = Math.toRadians(rollDeg);
        Vec3 horizontal = right.scale(Math.cos(roll)).add(up.scale(Math.sin(roll)));
        Vec3 vertical = up.scale(Math.cos(roll)).subtract(right.scale(Math.sin(roll)));
        PoseStack.Pose last = pose.last();
        float baseA = (color >>> 24) / 255f;
        int darkCol = DialDraw.alpha(0xff071024, Math.min(0.82f, baseA * 0.78f));
        int steps = 32;

        VertexConsumer voidSink = buffers.getBuffer(CosmicVfxShader.voidSheath());
        for (int i = 0; i < steps; i++) {
            float f0 = (float) i / steps, f1 = (float) (i + 1) / steps;
            double a0 = start + sweep * f0, a1 = start + sweep * f1;
            double taper0 = Math.pow(Math.sin(f0 * Math.PI), 1.22);
            double taper1 = Math.pow(Math.sin(f1 * Math.PI), 1.22);
            Vec3 d0 = horizontal.scale(Math.sin(a0)).add(vertical.scale(Math.cos(a0)));
            Vec3 d1 = horizontal.scale(Math.sin(a1)).add(vertical.scale(Math.cos(a1)));
            Vec3 bow0 = fwd.scale(Math.sin(f0 * Math.PI) * radius * .18);
            Vec3 bow1 = fwd.scale(Math.sin(f1 * Math.PI) * radius * .18);
            Vec3 inner0 = center.add(d0.scale(Math.max(.12, radius - bladeWidth * 1.28 * taper0))).add(bow0.scale(.65));
            Vec3 outer0 = center.add(d0.scale(radius + bladeWidth * .32 * taper0)).add(bow0.scale(1.06));
            Vec3 inner1 = center.add(d1.scale(Math.max(.12, radius - bladeWidth * 1.28 * taper1))).add(bow1.scale(.65));
            Vec3 outer1 = center.add(d1.scale(radius + bladeWidth * .32 * taper1)).add(bow1.scale(1.06));
            float tipErode0 = erosion + (float) Math.pow(Math.abs(f0 - .5f) * 2f, 2.2) * .36f;
            float tipErode1 = erosion + (float) Math.pow(Math.abs(f1 - .5f) * 2f, 2.2) * .36f;
            emitMode(voidSink, last, inner0, f0 * 1.6f, 0f, darkCol, MODE_VOID_SHEATH, tipErode0);
            emitMode(voidSink, last, outer0, f0 * 1.6f, 1f, darkCol, MODE_VOID_SHEATH, tipErode0);
            emitMode(voidSink, last, outer1, f1 * 1.6f, 1f, darkCol, MODE_VOID_SHEATH, tipErode1);
            emitMode(voidSink, last, inner1, f1 * 1.6f, 0f, darkCol, MODE_VOID_SHEATH, tipErode1);
        }

        VertexConsumer glowSink = buffers.getBuffer(CosmicVfxShader.glow());
        for (int i = 0; i < steps; i++) {
            float f0 = (float) i / steps, f1 = (float) (i + 1) / steps;
            double a0 = start + sweep * f0, a1 = start + sweep * f1;
            double taper0 = Math.pow(Math.sin(f0 * Math.PI), 1.34);
            double taper1 = Math.pow(Math.sin(f1 * Math.PI), 1.34);
            Vec3 d0 = horizontal.scale(Math.sin(a0)).add(vertical.scale(Math.cos(a0)));
            Vec3 d1 = horizontal.scale(Math.sin(a1)).add(vertical.scale(Math.cos(a1)));
            Vec3 bow0 = fwd.scale(Math.sin(f0 * Math.PI) * radius * .18);
            Vec3 bow1 = fwd.scale(Math.sin(f1 * Math.PI) * radius * .18);
            Vec3 inner0 = center.add(d0.scale(Math.max(.15, radius - bladeWidth * taper0))).add(bow0.scale(.72));
            Vec3 outer0 = center.add(d0.scale(radius + bladeWidth * .26 * taper0)).add(bow0);
            Vec3 inner1 = center.add(d1.scale(Math.max(.15, radius - bladeWidth * taper1))).add(bow1.scale(.72));
            Vec3 outer1 = center.add(d1.scale(radius + bladeWidth * .26 * taper1)).add(bow1);
            float tipErode0 = erosion + (float) Math.pow(Math.abs(f0 - .5f) * 2f, 2.4) * .42f;
            float tipErode1 = erosion + (float) Math.pow(Math.abs(f1 - .5f) * 2f, 2.4) * .42f;
            emitMode(glowSink, last, inner0, f0 * 1.6f, 0f, color, MODE_BLADE_ASYM, tipErode0);
            emitMode(glowSink, last, outer0, f0 * 1.6f, 1f, color, MODE_BLADE_ASYM, tipErode0);
            emitMode(glowSink, last, outer1, f1 * 1.6f, 1f, color, MODE_BLADE_ASYM, tipErode1);
            emitMode(glowSink, last, inner1, f1 * 1.6f, 0f, color, MODE_BLADE_ASYM, tipErode1);
        }
    }

    private static void slashArcMode(PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 forward,
                                     double radius, double bladeWidth, float tiltDeg, float startAngle, float sweepAngle,
                                     int color, int mode, boolean additive, float erosion) {
        Vec3 fwd = forward.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : forward.normalize();
        Vec3 worldUp = Math.abs(fwd.y) > .92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = fwd.cross(worldUp).normalize();
        Vec3 up = right.cross(fwd).normalize();
        double tilt = Math.toRadians(tiltDeg);
        Vec3 planeSide = right.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));
        Vec3 planeNormal = right.scale(-Math.sin(tilt)).add(up.scale(Math.cos(tilt)));

        int steps = (mode == MODE_BLADE_ASYM || mode == MODE_VOID_SHEATH || mode == MODE_ASCALON_LUSTER) ? 30 : 16;
        PoseStack.Pose last = pose.last();
        VertexConsumer sink = buffers.getBuffer(additive ? CosmicVfxShader.glow() : CosmicVfxShader.voidSheath());
        for (int i = 0; i < steps; i++) {
            float f0 = (float) i / steps;
            float f1 = (float) (i + 1) / steps;
            double a0 = startAngle + sweepAngle * f0;
            double a1 = startAngle + sweepAngle * f1;
            double taper0 = Math.pow(Math.sin(f0 * Math.PI), 1.32);
            double taper1 = Math.pow(Math.sin(f1 * Math.PI), 1.32);

            Vec3 dir0 = fwd.scale(Math.cos(a0)).add(planeSide.scale(Math.sin(a0)));
            Vec3 dir1 = fwd.scale(Math.cos(a1)).add(planeSide.scale(Math.sin(a1)));
            Vec3 inner0 = center.add(dir0.scale(Math.max(.15, radius - bladeWidth * taper0)))
                    .add(planeNormal.scale(.04 * (f0 - .5)));
            Vec3 outer0 = center.add(dir0.scale(radius + bladeWidth * .24 * taper0));
            Vec3 inner1 = center.add(dir1.scale(Math.max(.15, radius - bladeWidth * taper1)))
                    .add(planeNormal.scale(.04 * (f1 - .5)));
            Vec3 outer1 = center.add(dir1.scale(radius + bladeWidth * .24 * taper1));

            float endErode0 = Mth.clamp(erosion + (float) Math.pow(Math.abs(f0 - .5f) * 2f, 2.4) * .35f, 0f, 1f);
            float endErode1 = Mth.clamp(erosion + (float) Math.pow(Math.abs(f1 - .5f) * 2f, 2.4) * .35f, 0f, 1f);
            emitMode(sink, last, inner0, f0 * 1.6f, 0f, color, mode, endErode0);
            emitMode(sink, last, outer0, f0 * 1.6f, 1f, color, mode, endErode0);
            emitMode(sink, last, outer1, f1 * 1.6f, 1f, color, mode, endErode1);
            emitMode(sink, last, inner1, f1 * 1.6f, 0f, color, mode, endErode1);
        }
    }

    static void shockRing(PoseStack pose, MultiBufferSource buffers, Vec3 center, double radius,
                          double bandWidth, double crestHeight, double angleStart, double angleEnd,
                          int steps, int color, boolean starfield, boolean additive) {
        ringMode(pose, buffers, center, radius, bandWidth, crestHeight, angleStart, angleEnd, steps, color,
                starfield ? MODE_STARFIELD : MODE_ELEMENTAL, additive, 0f);
    }

    static void lusterRing(PoseStack pose, MultiBufferSource buffers, Vec3 center, double radius,
                           double bandWidth, double crestHeight, double angleStart, double angleEnd,
                           int steps, int color) {
        lusterRingEroded(pose, buffers, center, radius, bandWidth, crestHeight, angleStart, angleEnd, steps, color, 0.16f);
    }

    static void lusterRingEroded(PoseStack pose, MultiBufferSource buffers, Vec3 center, double radius,
                                 double bandWidth, double crestHeight, double angleStart, double angleEnd,
                                 int steps, int color, float erosion) {
        float baseA = (color >>> 24) / 255f;
        if (baseA > 0.15f) {
            ringMode(pose, buffers, center, radius, bandWidth * 1.3, crestHeight * .6, angleStart, angleEnd, steps,
                    DialDraw.alpha(0xff081228, baseA * 0.62f), MODE_VOID_SHEATH, false, erosion * .85f);
        }
        ringMode(pose, buffers, center, radius, bandWidth, crestHeight, angleStart, angleEnd, steps, color,
                MODE_ASCALON_LUSTER, true, erosion);
    }

    static void astrolabeRing(PoseStack pose, MultiBufferSource buffers, Vec3 center, double radius,
                              double bandWidth, double angleStart, double angleEnd, int steps, int color) {
        astrolabeRingEroded(pose, buffers, center, radius, bandWidth, angleStart, angleEnd, steps, color, 0f);
    }

    static void astrolabeRingEroded(PoseStack pose, MultiBufferSource buffers, Vec3 center, double radius,
                                    double bandWidth, double angleStart, double angleEnd,
                                    int steps, int color, float erosion) {
        if (radius <= .03) return;
        PoseStack.Pose last = pose.last();
        double rInner = Math.max(.02, radius - bandWidth * .55);
        double rOuter = radius + bandWidth * .45;

        float baseA = (color >>> 24) / 255f;
        if (baseA > 0.18f) {
            VertexConsumer voidSink = buffers.getBuffer(CosmicVfxShader.voidSheath());
            int darkCol = DialDraw.alpha(0xff081226, baseA * 0.48f);
            for (int i = 0; i < steps; i++) {
                float f0 = (float) i / steps, f1 = (float) (i + 1) / steps;
                double a0 = angleStart + (angleEnd - angleStart) * f0;
                double a1 = angleStart + (angleEnd - angleStart) * f1;
                emitMode(voidSink, last, center.add(Math.cos(a0) * rInner * .96, .035, Math.sin(a0) * rInner * .96), f0, 0f, darkCol, MODE_VOID_SHEATH, erosion);
                emitMode(voidSink, last, center.add(Math.cos(a0) * rOuter * 1.04, .035, Math.sin(a0) * rOuter * 1.04), f0, 1f, darkCol, MODE_VOID_SHEATH, erosion);
                emitMode(voidSink, last, center.add(Math.cos(a1) * rOuter * 1.04, .035, Math.sin(a1) * rOuter * 1.04), f1, 1f, darkCol, MODE_VOID_SHEATH, erosion);
                emitMode(voidSink, last, center.add(Math.cos(a1) * rInner * .96, .035, Math.sin(a1) * rInner * .96), f1, 0f, darkCol, MODE_VOID_SHEATH, erosion);
            }
        }

        VertexConsumer sink = buffers.getBuffer(CosmicVfxShader.glow());
        for (int i = 0; i < steps; i++) {
            float f0 = (float) i / steps;
            float f1 = (float) (i + 1) / steps;
            double a0 = angleStart + (angleEnd - angleStart) * f0;
            double a1 = angleStart + (angleEnd - angleStart) * f1;
            double c0 = Math.cos(a0), s0 = Math.sin(a0);
            double c1 = Math.cos(a1), s1 = Math.sin(a1);
            emitMode(sink, last, center.add(c0 * rInner, .04, s0 * rInner), f0, 0f, color, MODE_ASTROLABE_GEOM, erosion);
            emitMode(sink, last, center.add(c0 * rOuter, .04, s0 * rOuter), f0, 1f, color, MODE_ASTROLABE_GEOM, erosion);
            emitMode(sink, last, center.add(c1 * rOuter, .04, s1 * rOuter), f1, 1f, color, MODE_ASTROLABE_GEOM, erosion);
            emitMode(sink, last, center.add(c1 * rInner, .04, s1 * rInner), f1, 0f, color, MODE_ASTROLABE_GEOM, erosion);
        }
    }

    static void heraldicHexShield(PoseStack pose, MultiBufferSource buffers, double radius, float spin,
                                  int plateColor, int trimColor) {
        PoseStack.Pose last = pose.last();
        double innerR = radius * .22;
        int darkBacking = DialDraw.alpha(0xff09142b, 0.52f);

        VertexConsumer voidSink = buffers.getBuffer(CosmicVfxShader.voidSheath());
        for (int i = 0; i < 6; i++) {
            double a0 = i * TAU / 6.0 + Math.PI / 6.0;
            double a1 = (i + 1) * TAU / 6.0 + Math.PI / 6.0;
            Vec3 pIn0 = new Vec3(Math.cos(a0) * innerR, Math.sin(a0) * innerR, 0);
            Vec3 pIn1 = new Vec3(Math.cos(a1) * innerR, Math.sin(a1) * innerR, 0);
            Vec3 pOut0 = new Vec3(Math.cos(a0) * radius, Math.sin(a0) * radius, 0);
            Vec3 pOut1 = new Vec3(Math.cos(a1) * radius, Math.sin(a1) * radius, 0);
            emitMode(voidSink, last, pIn0, i / 6f, .1f, darkBacking, MODE_VOID_SHEATH, 0.12f);
            emitMode(voidSink, last, pOut0, i / 6f, .9f, darkBacking, MODE_VOID_SHEATH, 0.12f);
            emitMode(voidSink, last, pOut1, (i + 1) / 6f, .9f, darkBacking, MODE_VOID_SHEATH, 0.12f);
            emitMode(voidSink, last, pIn1, (i + 1) / 6f, .1f, darkBacking, MODE_VOID_SHEATH, 0.12f);
        }

        VertexConsumer glow = buffers.getBuffer(CosmicVfxShader.glow());
        for (int i = 0; i < 6; i++) {
            double a0 = i * TAU / 6.0 + Math.PI / 6.0;
            double a1 = (i + 1) * TAU / 6.0 + Math.PI / 6.0;
            Vec3 pIn0 = new Vec3(Math.cos(a0) * innerR, Math.sin(a0) * innerR, 0);
            Vec3 pIn1 = new Vec3(Math.cos(a1) * innerR, Math.sin(a1) * innerR, 0);
            Vec3 pOut0 = new Vec3(Math.cos(a0) * radius, Math.sin(a0) * radius, 0);
            Vec3 pOut1 = new Vec3(Math.cos(a1) * radius, Math.sin(a1) * radius, 0);
            emitMode(glow, last, pIn0, i / 6f, .15f, plateColor, MODE_ASCALON_LUSTER, 0.24f);
            emitMode(glow, last, pOut0, i / 6f, .85f, plateColor, MODE_ASCALON_LUSTER, 0.24f);
            emitMode(glow, last, pOut1, (i + 1) / 6f, .85f, plateColor, MODE_ASCALON_LUSTER, 0.24f);
            emitMode(glow, last, pIn1, (i + 1) / 6f, .15f, plateColor, MODE_ASCALON_LUSTER, 0.24f);

            Vec3 pRim0 = new Vec3(Math.cos(a0) * (radius * .91), Math.sin(a0) * (radius * .91), .005);
            Vec3 pRim1 = new Vec3(Math.cos(a1) * (radius * .91), Math.sin(a1) * (radius * .91), .005);
            Vec3 pEdge0 = new Vec3(Math.cos(a0) * (radius * 1.03), Math.sin(a0) * (radius * 1.03), .005);
            Vec3 pEdge1 = new Vec3(Math.cos(a1) * (radius * 1.03), Math.sin(a1) * (radius * 1.03), .005);
            emitMode(glow, last, pRim0, i / 6f, 0f, trimColor, MODE_ASTROLABE_GEOM, 0f);
            emitMode(glow, last, pEdge0, i / 6f, 1f, trimColor, MODE_ASTROLABE_GEOM, 0f);
            emitMode(glow, last, pEdge1, (i + 1) / 6f, 1f, trimColor, MODE_ASTROLABE_GEOM, 0f);
            emitMode(glow, last, pRim1, (i + 1) / 6f, 0f, trimColor, MODE_ASTROLABE_GEOM, 0f);
        }
        int segs = 36;
        double r0 = radius * .50, r1 = radius * .72;
        for (int i = 0; i < segs; i++) {
            float f0 = (float) i / segs, f1 = (float) (i + 1) / segs;
            double a0 = spin + f0 * TAU, a1 = spin + f1 * TAU;
            emitMode(glow, last, new Vec3(Math.cos(a0) * r0, Math.sin(a0) * r0, .008), f0, 0f, trimColor, MODE_ASTROLABE_GEOM, 0f);
            emitMode(glow, last, new Vec3(Math.cos(a0) * r1, Math.sin(a0) * r1, .008), f0, 1f, trimColor, MODE_ASTROLABE_GEOM, 0f);
            emitMode(glow, last, new Vec3(Math.cos(a1) * r1, Math.sin(a1) * r1, .008), f1, 1f, trimColor, MODE_ASTROLABE_GEOM, 0f);
            emitMode(glow, last, new Vec3(Math.cos(a1) * r0, Math.sin(a1) * r0, .008), f1, 0f, trimColor, MODE_ASTROLABE_GEOM, 0f);
        }
    }

    static void eclipseCanopy(PoseStack pose, MultiBufferSource buffers, Vec3 center, double radius, double domeHeight,
                              float spin, int color) {
        PoseStack.Pose last = pose.last();
        VertexConsumer nebula = buffers.getBuffer(CosmicVfxShader.nebula());
        int rings = 5, segs = 28;
        for (int r = 0; r < rings; r++) {
            double f0 = (double) r / rings;
            double f1 = (double) (r + 1) / rings;
            double rad0 = f0 * radius, rad1 = f1 * radius;
            double y0 = domeHeight * (1.0 - f0 * f0 * .55);
            double y1 = domeHeight * (1.0 - f1 * f1 * .55);
            int c0 = DialDraw.alpha(color, ((color >>> 24) / 255f) * (float) (1.0 - f0 * .72));
            int c1 = DialDraw.alpha(color, ((color >>> 24) / 255f) * (float) Math.max(0.0, 1.0 - f1 * .96));
            for (int s = 0; s < segs; s++) {
                double a0 = spin + TAU * s / segs;
                double a1 = spin + TAU * (s + 1) / segs;
                Vec3 p00 = center.add(Math.cos(a0) * rad0, y0, Math.sin(a0) * rad0);
                Vec3 p01 = center.add(Math.cos(a1) * rad0, y0, Math.sin(a1) * rad0);
                Vec3 p11 = center.add(Math.cos(a1) * rad1, y1, Math.sin(a1) * rad1);
                Vec3 p10 = center.add(Math.cos(a0) * rad1, y1, Math.sin(a0) * rad1);
                emitMode(nebula, last, p00, (float) s / segs, (float) f0, c0, MODE_ECLIPSE, 0f);
                emitMode(nebula, last, p01, (float) (s + 1) / segs, (float) f0, c0, MODE_ECLIPSE, 0f);
                emitMode(nebula, last, p11, (float) (s + 1) / segs, (float) f1, c1, MODE_ECLIPSE, 0f);
                emitMode(nebula, last, p10, (float) s / segs, (float) f1, c1, MODE_ECLIPSE, 0f);
            }
        }
    }

    private static void ringMode(PoseStack pose, MultiBufferSource buffers, Vec3 center, double radius,
                                 double bandWidth, double crestHeight, double angleStart, double angleEnd,
                                 int steps, int color, int mode, boolean additive, float erosion) {
        if (radius <= .03) return;
        PoseStack.Pose last = pose.last();
        VertexConsumer sink = buffers.getBuffer(additive ? CosmicVfxShader.glow() : CosmicVfxShader.voidSheath());
        double rInner = Math.max(.02, radius - bandWidth * .65);
        double rOuter = radius + bandWidth * .35;
        float uLoops = (float) Math.max(1.0, Math.round(radius * .45));

        for (int i = 0; i < steps; i++) {
            float f0 = (float) i / steps;
            float f1 = (float) (i + 1) / steps;
            double a0 = angleStart + (angleEnd - angleStart) * f0;
            double a1 = angleStart + (angleEnd - angleStart) * f1;
            double c0 = Math.cos(a0), s0 = Math.sin(a0);
            double c1 = Math.cos(a1), s1 = Math.sin(a1);
            double wave0 = Math.sin(a0 * 3.0) * crestHeight * .18;
            double wave1 = Math.sin(a1 * 3.0) * crestHeight * .18;

            Vec3 pIn0 = center.add(c0 * rInner, .045 + wave0, s0 * rInner);
            Vec3 pOut0 = center.add(c0 * rOuter, .055 + crestHeight * .32 + wave0, s0 * rOuter);
            Vec3 pOut1 = center.add(c1 * rOuter, .055 + crestHeight * .32 + wave1, s1 * rOuter);
            Vec3 pIn1 = center.add(c1 * rInner, .045 + wave1, s1 * rInner);
            emitMode(sink, last, pIn0, f0 * uLoops, 0f, color, mode, erosion);
            emitMode(sink, last, pOut0, f0 * uLoops, 1f, color, mode, erosion);
            emitMode(sink, last, pOut1, f1 * uLoops, 1f, color, mode, erosion);
            emitMode(sink, last, pIn1, f1 * uLoops, 0f, color, mode, erosion);

            if (crestHeight > .03) {
                Vec3 vBot0 = center.add(c0 * radius, .03, s0 * radius);
                Vec3 vTop0 = center.add(c0 * radius, crestHeight + wave0, s0 * radius);
                Vec3 vTop1 = center.add(c1 * radius, crestHeight + wave1, s1 * radius);
                Vec3 vBot1 = center.add(c1 * radius, .03, s1 * radius);
                emitMode(sink, last, vBot0, f0 * uLoops, 0f, color, mode, erosion);
                emitMode(sink, last, vTop0, f0 * uLoops, 1f, color, mode, erosion);
                emitMode(sink, last, vTop1, f1 * uLoops, 1f, color, mode, erosion);
                emitMode(sink, last, vBot1, f1 * uLoops, 0f, color, mode, erosion);
            }
        }
    }

    static void celestialPlanet(PoseStack pose, MultiBufferSource buffers, Vec3 center, double radius,
                                float spin, int planetColor, int ringColor, boolean orbitalRing) {
        int latBands = 12, lonBands = 20;
        var last = pose.last();
        var sink = buffers.getBuffer(CosmicVfxShader.nebula());
        float longitudeOffset = spin / TAU;
        for (int i = 0; i < latBands; i++) {
            double v0 = (double) i / latBands;
            double v1 = (double) (i + 1) / latBands;
            double phi0 = (v0 - .5) * Math.PI;
            double phi1 = (v1 - .5) * Math.PI;
            double cp0 = Math.cos(phi0), sp0 = Math.sin(phi0);
            double cp1 = Math.cos(phi1), sp1 = Math.sin(phi1);
            for (int j = 0; j < lonBands; j++) {
                double u0 = (double) j / lonBands;
                double u1 = (double) (j + 1) / lonBands;
                double th0 = u0 * TAU + spin;
                double th1 = u1 * TAU + spin;
                Vec3 p00 = center.add(Math.cos(th0) * cp0 * radius, sp0 * radius, Math.sin(th0) * cp0 * radius);
                Vec3 p10 = center.add(Math.cos(th1) * cp0 * radius, sp0 * radius, Math.sin(th1) * cp0 * radius);
                Vec3 p11 = center.add(Math.cos(th1) * cp1 * radius, sp1 * radius, Math.sin(th1) * cp1 * radius);
                Vec3 p01 = center.add(Math.cos(th0) * cp1 * radius, sp1 * radius, Math.sin(th0) * cp1 * radius);
                emitMode(sink, last, p00, (float) u0 + longitudeOffset, (float) v0, planetColor, MODE_PLANET, 0f);
                emitMode(sink, last, p01, (float) u0 + longitudeOffset, (float) v1, planetColor, MODE_PLANET, 0f);
                emitMode(sink, last, p11, (float) u1 + longitudeOffset, (float) v1, planetColor, MODE_PLANET, 0f);
                emitMode(sink, last, p10, (float) u1 + longitudeOffset, (float) v0, planetColor, MODE_PLANET, 0f);
            }
        }
        if (orbitalRing) {
            astrolabeRingEroded(pose, buffers, center, radius * 2.05, radius * .48, -spin * .7, -spin * .7 + TAU, 44, ringColor, 0.08f);
        }
    }

    static void nebulaCone(PoseStack pose, MultiBufferSource buffers, Vec3 origin, Vec3 end,
                           double startRadius, double endRadius, float twistPhase, int color, boolean starfield) {
        Vec3 axis = end.subtract(origin);
        double len = axis.length();
        if (len < .15) return;
        Vec3 dir = axis.scale(1.0 / len);
        Vec3 ref = Math.abs(dir.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 uAxis = dir.cross(ref).normalize();
        Vec3 vAxis = dir.cross(uAxis).normalize();

        int rings = 6, sides = 12;
        PoseStack.Pose last = pose.last();
        VertexConsumer sink = buffer(buffers, starfield, false);
        for (int r = 0; r < rings; r++) {
            float t0 = (float) r / rings;
            float t1 = (float) (r + 1) / rings;
            Vec3 c0 = origin.add(axis.scale(t0));
            Vec3 c1 = origin.add(axis.scale(t1));
            double rad0 = Mth.lerp(t0, startRadius, endRadius);
            double rad1 = Mth.lerp(t1, startRadius, endRadius);
            int col0 = DialDraw.alpha(color, ((color >>> 24) / 255f) * (1f - t0 * .45f));
            int col1 = DialDraw.alpha(color, ((color >>> 24) / 255f) * (1f - t1 * .65f));

            for (int s = 0; s < sides; s++) {
                double a0 = TAU * s / sides + twistPhase + t0 * 1.1;
                double a1 = TAU * (s + 1) / sides + twistPhase + t0 * 1.1;
                double b0 = TAU * s / sides + twistPhase + t1 * 1.1;
                double b1 = TAU * (s + 1) / sides + twistPhase + t1 * 1.1;

                Vec3 p00 = c0.add(uAxis.scale(Math.cos(a0) * rad0)).add(vAxis.scale(Math.sin(a0) * rad0));
                Vec3 p01 = c0.add(uAxis.scale(Math.cos(a1) * rad0)).add(vAxis.scale(Math.sin(a1) * rad0));
                Vec3 p11 = c1.add(uAxis.scale(Math.cos(b1) * rad1)).add(vAxis.scale(Math.sin(b1) * rad1));
                Vec3 p10 = c1.add(uAxis.scale(Math.cos(b0) * rad1)).add(vAxis.scale(Math.sin(b0) * rad1));

                float vCoord0 = (float) s / sides;
                float vCoord1 = (float) (s + 1) / sides;
                emit(sink, last, p00, t0 * 2.2f, vCoord0, col0, starfield);
                emit(sink, last, p01, t0 * 2.2f, vCoord1, col0, starfield);
                emit(sink, last, p11, t1 * 2.2f, vCoord1, col1, starfield);
                emit(sink, last, p10, t1 * 2.2f, vCoord0, col1, starfield);
            }
        }
    }

    static void circuitTrace(PoseStack pose, MultiBufferSource buffers, Vec3 from, Vec3 to, Vec3 camera,
                             int color, boolean starfield) {
        curvedEnergyWhip(pose, buffers, from, to, camera, .085, .042, .42, .8f, color, 0xe8e4fff5, .12f);
    }

    static void impactBurst(PoseStack pose, MultiBufferSource buffers, Vec3 at, Vec3 camera,
                            double radius, float progress, int color, boolean starfield) {
        lusterImpactBurst(pose, buffers, at, camera, radius, progress, color);
    }

    /** Asymmetric Sakuga Star-Cross Impact Burst (fast exponential snap + 2 primary anamorphic spikes + 6 fine sparks). */
    static void lusterImpactBurst(PoseStack pose, MultiBufferSource buffers, Vec3 at, Vec3 camera,
                                  double radius, float progress, int color) {
        double snap = 1.0 - Math.pow(1.0 - Mth.clamp(progress, 0f, 1f), 4.2);
        float fade = 1f - progress;
        for (int k = 0; k < 2; k++) {
            double yaw = k * Math.PI * .52 + .28;
            double pitch = k == 0 ? .18 : -.22;
            Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
            double span = radius * (0.45 + 1.15 * snap);
            speedStreak(pose, buffers, at.subtract(dir.scale(span)), at.add(dir.scale(span)), camera,
                    Math.max(.025, .13 * fade), color, progress * .75f);
        }
        for (int i = 0; i < 6; i++) {
            double yaw = TAU * i / 6 + .48;
            double pitch = i % 2 == 0 ? .42 : -.36;
            Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
            double r0 = radius * (.28 * snap);
            double r1 = radius * (.38 + .78 * snap) * (i % 2 == 0 ? 1.05 : 0.72);
            speedStreak(pose, buffers, at.add(dir.scale(r0)), at.add(dir.scale(r1)), camera,
                    Math.max(.015, .065 * fade), color, progress * .85f);
        }
    }
}

package cn.aca.infinity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * V1 Next-Gen Stylized VFX 4.0 Volumetric 3D Mesh Primitives (HSR / Destiny 2 Grade):
 * - Catmull-Rom 3D Curved Multi-Strand Entangled Energy Whips (replaces straight single beams!)
 * - Voronoi Mirror Space-Shatter Shards & Fracture Planes (MODE_VORONOI_SHATTER)
 * - 3D Hexagonal Tapered Aether Crystal Spires with True 3D Outward Normals (MODE_FRESNEL_SHELL)
 * - Multi-Stage 3D Mach Sonic Vapor Cones (MODE_FRESNEL_SHELL)
 * - 3D Flapping Crimson Healing Butterflies & Psionic Puppet Control Crowns
 */
public final class InfinityVfxPrimitives {
    private static final float TAU = (float) (Math.PI * 2.0);

    private InfinityVfxPrimitives() {}

    public static float cameraClearance(Vec3 camRel) {
        double d2 = camRel.lengthSqr();
        if (d2 >= 1.35 * 1.35) return 1.0f;
        float d = (float) Math.sqrt(d2);
        return Mth.clamp((d - 0.30f) / 1.05f, 0.12f, 1.0f);
    }

    private static int encodeErosion(float erosion) {
        return Math.round(Mth.clamp(erosion, 0f, 1f) * 1000f);
    }

    private static void putVertexN(VertexConsumer vc, PoseStack.Pose pose,
                                   float x, float y, float z,
                                   float nx, float ny, float nz,
                                   int argb, float u, float v, int mode, int erodeFixed, boolean customShader) {
        if (customShader) {
            vc.addVertex(pose.pose(), x, y, z)
                    .setColor(argb)
                    .setUv(u, v)
                    .setUv1(mode, erodeFixed)
                    .setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(pose, nx, ny, nz);
        } else {
            vc.addVertex(pose.pose(), x, y, z).setColor(argb);
        }
    }

    private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        double x = 0.5 * ((2.0 * p1.x) + (-p0.x + p2.x) * t
                + (2.0 * p0.x - 5.0 * p1.x + 4.0 * p2.x - p3.x) * t2
                + (-p0.x + 3.0 * p1.x - 3.0 * p2.x + p3.x) * t3);
        double y = 0.5 * ((2.0 * p1.y) + (-p0.y + p2.y) * t
                + (2.0 * p0.y - 5.0 * p1.y + 4.0 * p2.y - p3.y) * t2
                + (-p0.y + 3.0 * p1.y - 3.0 * p2.y + p3.y) * t3);
        double z = 0.5 * ((2.0 * p1.z) + (-p0.z + p2.z) * t
                + (2.0 * p0.z - 5.0 * p1.z + 4.0 * p2.z - p3.z) * t2
                + (-p0.z + 3.0 * p1.z - 3.0 * p2.z + p3.z) * t3);
        return new Vec3(x, y, z);
    }

    /**
     * V1 Signature Primitive: Curved Multi-Strand Catmull-Rom Entangled Energy Whip!
     * Replaces monotonous straight beams with sweeping 3D curved plasma whips + dark abyssal contrast sheath.
     */
    public static void catmullSplineWhip(PoseStack pose, MultiBufferSource buffers,
                                         Vec3 a, Vec3 b, int strands, float bowAmplitude, float baseHalfW,
                                         int darkSheathArgb, int glowCoreArgb, float time, float erosion) {
        Vec3 diff = b.subtract(a);
        double len = diff.length();
        if (len < 1e-3) return;
        Vec3 axis = diff.scale(1.0 / len);
        Vec3 uVec = axis.cross(new Vec3(0, 1, 0));
        if (uVec.lengthSqr() < 1e-4) uVec = axis.cross(new Vec3(1, 0, 0));
        uVec = uVec.normalize();
        Vec3 vVec = axis.cross(uVec).normalize();

        float clear = cameraClearance(a.lerp(b, 0.5));
        int darkCol = scaleAlpha(darkSheathArgb, clear);
        int glowCol = scaleAlpha(glowCoreArgb, clear);
        boolean custom = InfinityCosmicShader.available();

        int segs = 18;
        float bow = (float) Math.min(bowAmplitude, len * 0.28);

        for (int s = 0; s < strands; s++) {
            float phase = s * TAU / Math.max(1, strands) + time * 0.22f;
            float sign = (s % 2 == 0) ? 1.0f : -0.82f;
            Vec3 radial1 = uVec.scale(Mth.cos(phase) * bow).add(vVec.scale(Mth.sin(phase) * bow));
            Vec3 radial2 = uVec.scale(Mth.cos(phase + 2.1f * sign) * bow * 0.85f)
                    .add(vVec.scale(Mth.sin(phase + 2.1f * sign) * bow * 0.85f));

            Vec3 p1 = a.lerp(b, 0.32).add(radial1);
            Vec3 p2 = a.lerp(b, 0.68).add(radial2);
            Vec3 p0 = a.subtract(axis.scale(len * 0.15));
            Vec3 p3 = b.add(axis.scale(len * 0.15));

            float strandW = (s == 0) ? baseHalfW * 1.18f : baseHalfW * (0.62f + 0.16f * (s % 2));
            if (s == 0) {
                emitSplineTubeOrCrossRibbon(pose, buffers.getBuffer(InfinityCosmicShader.voidSheath()),
                        p0, a, p1, p2, b, p3, segs, strandW * 1.95f, darkCol,
                        InfinityCosmicShader.MODE_DARK_SHEATH, erosion * 0.78f, custom);
            }
            emitSplineTubeOrCrossRibbon(pose, buffers.getBuffer(InfinityCosmicShader.additiveGlow()),
                    p0, a, p1, p2, b, p3, segs, strandW, glowCol,
                    InfinityCosmicShader.MODE_SILK_FILAMENT, erosion, custom);
        }
    }

    private static void emitSplineTubeOrCrossRibbon(PoseStack pose, VertexConsumer vc,
                                                    Vec3 pre, Vec3 a, Vec3 c1, Vec3 c2, Vec3 b, Vec3 post,
                                                    int segs, float halfW, int argb,
                                                    int mode, float erosion, boolean custom) {
        Vec3[] pts = new Vec3[segs + 1];
        int third = segs / 3;
        int twoThird = (segs * 2) / 3;
        for (int i = 0; i <= segs; i++) {
            if (i <= third) {
                pts[i] = catmullRom(pre, a, c1, c2, i / (float) Math.max(1, third));
            } else if (i <= twoThird) {
                pts[i] = catmullRom(a, c1, c2, b, (i - third) / (float) Math.max(1, twoThird - third));
            } else {
                pts[i] = catmullRom(c1, c2, b, post, (i - twoThird) / (float) Math.max(1, segs - twoThird));
            }
        }

        PoseStack.Pose last = pose.last();
        int er = encodeErosion(erosion);
        for (int i = 0; i < segs; i++) {
            Vec3 pA = pts[i];
            Vec3 pB = pts[i + 1];
            Vec3 seg = pB.subtract(pA);
            double sl = seg.length();
            if (sl < 1e-5) continue;
            Vec3 dir = seg.scale(1.0 / sl);
            Vec3 s1 = dir.cross(new Vec3(0, 1, 0));
            if (s1.lengthSqr() < 1e-4) s1 = dir.cross(new Vec3(1, 0, 0));
            Vec3 n1 = s1.normalize();
            Vec3 n2 = dir.cross(n1).normalize();

            float t0 = i / (float) segs;
            float t1 = (i + 1) / (float) segs;
            float w0 = halfW * (0.35f + 0.85f * Mth.sin(Math.max(0.08f, t0) * (float) Math.PI));
            float w1 = halfW * (0.35f + 0.85f * Mth.sin(Math.min(0.92f, t1) * (float) Math.PI));

            emitRibbonSegment(vc, last, pA, pB, n1, n2, w0, w1, argb, t0 * 3.0f, t1 * 3.0f, mode, er, custom);
            emitRibbonSegment(vc, last, pA, pB, n2, n1, w0, w1, argb, t0 * 3.0f, t1 * 3.0f, mode, er, custom);
        }
    }

    private static void emitRibbonSegment(VertexConsumer vc, PoseStack.Pose last,
                                          Vec3 pA, Vec3 pB, Vec3 sideDir, Vec3 normDir,
                                          float w0, float w1, int argb, float u0, float u1,
                                          int mode, int er, boolean custom) {
        Vec3 o0 = sideDir.scale(w0);
        Vec3 o1 = sideDir.scale(w1);
        float nx = (float) normDir.x, ny = (float) normDir.y, nz = (float) normDir.z;
        putVertexN(vc, last, (float) (pA.x - o0.x), (float) (pA.y - o0.y), (float) (pA.z - o0.z), nx, ny, nz, argb, u0, 0f, mode, er, custom);
        putVertexN(vc, last, (float) (pA.x + o0.x), (float) (pA.y + o0.y), (float) (pA.z + o0.z), nx, ny, nz, argb, u0, 1f, mode, er, custom);
        putVertexN(vc, last, (float) (pB.x + o1.x), (float) (pB.y + o1.y), (float) (pB.z + o1.z), nx, ny, nz, argb, u1, 1f, mode, er, custom);
        putVertexN(vc, last, (float) (pB.x - o1.x), (float) (pB.y - o1.y), (float) (pB.z - o1.z), nx, ny, nz, argb, u1, 0f, mode, er, custom);
    }

    /** Dual-Pass Cosmic Beam upgraded to Curved Entangled Whips by default. */
    public static void dualPassBeam(PoseStack pose, MultiBufferSource buffers,
                                    Vec3 a, Vec3 b, float coreHalfWidth,
                                    int darkSheathArgb, int glowCoreArgb, float erosion) {
        catmullSplineWhip(pose, buffers, a, b, 3, coreHalfWidth * 2.4f, coreHalfWidth,
                darkSheathArgb, glowCoreArgb, erosion * 14f, erosion);
    }

    /**
     * V1 Signature Primitive: Voronoi Mirror Space-Shatter Crown!
     * Spawns tilted 3D shattered spacetime glass planes + radial Voronoi fracture ring.
     */
    public static void voronoiShatterCrown(PoseStack pose, MultiBufferSource buffers,
                                           int shardCount, float radius, float shardSize, float heightSpread,
                                           int argb, float spinDeg, float erosion) {
        boolean custom = InfinityCosmicShader.available();
        VertexConsumer vc = buffers.getBuffer(InfinityCosmicShader.additiveGlow());
        int er = encodeErosion(erosion);

        // Fractured Voronoi ground ring
        emitRingOnConsumer(pose, vc, Math.max(0.12f, radius * 0.48f), radius * 1.08f, 28,
                scaleAlpha(argb, 0.72f), spinDeg * 0.4f, InfinityCosmicShader.MODE_VORONOI_SHATTER, erosion, custom);

        // Tilted 3D polygonal mirror glass shards erupting in 3D space
        for (int i = 0; i < shardCount; i++) {
            float ang = i * TAU / shardCount + spinDeg * 0.01745f;
            float r = radius * (0.58f + 0.42f * ((i * 37 % 11) / 10.0f));
            float y = 0.25f + heightSpread * ((i * 19 % 7) / 6.0f);
            float s = shardSize * (0.72f + 0.45f * ((i % 3) * 0.5f));

            pose.pushPose();
            pose.translate(Mth.cos(ang) * r, y, Mth.sin(ang) * r);
            pose.mulPose(Axis.YP.rotationDegrees(-ang * Mth.RAD_TO_DEG + 25f * (i % 3 - 1)));
            pose.mulPose(Axis.XP.rotationDegrees(32f + (i % 4) * 16f));
            pose.mulPose(Axis.ZP.rotationDegrees(18f * (i % 5 - 2)));
            PoseStack.Pose last = pose.last();

            // Asymmetric shattered glass polygon quad with true face normal (0, 0, 1)
            float uOff = (i * 0.17f) % 0.5f;
            float vOff = (i * 0.23f) % 0.5f;
            putVertexN(vc, last, -s * 0.9f, -s * 0.7f, 0f, 0f, 0f, 1f, argb, uOff, vOff, InfinityCosmicShader.MODE_VORONOI_SHATTER, er, custom);
            putVertexN(vc, last, s * 1.1f, -s * 0.4f, 0f, 0f, 0f, 1f, argb, uOff + 0.48f, vOff, InfinityCosmicShader.MODE_VORONOI_SHATTER, er, custom);
            putVertexN(vc, last, s * 0.65f, s * 1.15f, 0f, 0f, 0f, 1f, argb, uOff + 0.48f, vOff + 0.48f, InfinityCosmicShader.MODE_VORONOI_SHATTER, er, custom);
            putVertexN(vc, last, -s * 0.55f, s * 0.85f, 0f, 0f, 0f, 1f, argb, uOff, vOff + 0.48f, InfinityCosmicShader.MODE_VORONOI_SHATTER, er, custom);
            pose.popPose();
        }
    }

    /**
     * V1 Signature Primitive: Erupting 3D Hexagonal Tapered Crystal Cluster with True 3D Outward Normals!
     */
    public static void eruptingCrystalCluster(PoseStack pose, MultiBufferSource buffers,
                                              int spireCount, float ringRadius, float spireHeight, float spireRadius,
                                              float tiltOutDeg, int argb, float spinDeg, float erosion) {
        boolean custom = InfinityCosmicShader.available();
        VertexConsumer vc = buffers.getBuffer(InfinityCosmicShader.additiveGlow());
        int er = encodeErosion(erosion);

        for (int i = 0; i < spireCount; i++) {
            float ang = i * TAU / spireCount + spinDeg * Mth.DEG_TO_RAD;
            float rDist = ringRadius * (0.65f + 0.35f * ((i * 23 % 7) / 6.0f));
            float h = spireHeight * (0.72f + 0.46f * ((i * 13 % 5) / 4.0f));
            float w = spireRadius * (0.80f + 0.30f * (i % 3 * 0.5f));

            pose.pushPose();
            pose.translate(Mth.cos(ang) * rDist, 0.02f, Mth.sin(ang) * rDist);
            pose.mulPose(Axis.YP.rotationDegrees(-ang * Mth.RAD_TO_DEG + 90f));
            pose.mulPose(Axis.XP.rotationDegrees(tiltOutDeg + (i % 3) * 7f));
            PoseStack.Pose last = pose.last();

            float shoulderH = h * 0.68f;
            int sides = 6;
            for (int s = 0; s < sides; s++) {
                float a0 = s * TAU / sides;
                float a1 = (s + 1) * TAU / sides;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
                float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                float nx = Mth.cos((a0 + a1) * 0.5f);
                float nz = Mth.sin((a0 + a1) * 0.5f);

                // Hexagonal prism shaft
                putVertexN(vc, last, c0 * w, 0f, s0 * w, nx, 0.15f, nz, scaleAlpha(argb, 0.75f), 0f, 0f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, c1 * w, 0f, s1 * w, nx, 0.15f, nz, scaleAlpha(argb, 0.75f), 1f, 0f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, c1 * w * 0.82f, shoulderH, s1 * w * 0.82f, nx, 0.15f, nz, argb, 1f, 0.72f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, c0 * w * 0.82f, shoulderH, s0 * w * 0.82f, nx, 0.15f, nz, argb, 0f, 0.72f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);

                // Pointed pyramidal crystal apex
                putVertexN(vc, last, c0 * w * 0.82f, shoulderH, s0 * w * 0.82f, nx, 0.55f, nz, argb, 0f, 0.72f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, c1 * w * 0.82f, shoulderH, s1 * w * 0.82f, nx, 0.55f, nz, argb, 1f, 0.72f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, 0f, h, 0f, nx, 0.75f, nz, argb, 0.5f, 1f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, 0f, h, 0f, nx, 0.75f, nz, argb, 0.5f, 1f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
            }
            pose.popPose();
        }
    }

    /**
     * V1 Signature Primitive: Multi-Stage 3D Mach Sonic Vapor Cones along a strike vector!
     */
    public static void machVaporCones(PoseStack pose, MultiBufferSource buffers,
                                      Vec3 a, Vec3 b, int coneCount, float maxConeR,
                                      int argb, float age, float erosion) {
        Vec3 diff = b.subtract(a);
        double len = diff.length();
        if (len < 0.2) return;
        Vec3 axis = diff.scale(1.0 / len);
        Vec3 uVec = axis.cross(new Vec3(0, 1, 0));
        if (uVec.lengthSqr() < 1e-4) uVec = axis.cross(new Vec3(1, 0, 0));
        uVec = uVec.normalize();
        Vec3 vVec = axis.cross(uVec).normalize();

        boolean custom = InfinityCosmicShader.available();
        VertexConsumer vc = buffers.getBuffer(InfinityCosmicShader.additiveGlow());
        int er = encodeErosion(erosion);
        PoseStack.Pose last = pose.last();
        int segs = 20;

        for (int c = 0; c < coneCount; c++) {
            float frac = (c + 0.55f) / coneCount;
            Vec3 apex = a.lerp(b, frac);
            float skirtLen = 0.75f + c * 0.35f;
            Vec3 baseCenter = apex.subtract(axis.scale(skirtLen));
            float r = maxConeR * (0.55f + 0.45f * frac);

            for (int i = 0; i < segs; i++) {
                float u0 = i / (float) segs;
                float u1 = (i + 1) / (float) segs;
                float a0 = TAU * u0 + age * 0.15f;
                float a1 = TAU * u1 + age * 0.15f;
                Vec3 rad0 = uVec.scale(Mth.cos(a0)).add(vVec.scale(Mth.sin(a0)));
                Vec3 rad1 = uVec.scale(Mth.cos(a1)).add(vVec.scale(Mth.sin(a1)));
                Vec3 p0 = baseCenter.add(rad0.scale(r));
                Vec3 p1 = baseCenter.add(rad1.scale(r));
                Vec3 aPt0 = apex.add(rad0.scale(0.08));
                Vec3 aPt1 = apex.add(rad1.scale(0.08));
                Vec3 nrm = rad0.add(rad1).scale(0.5).add(axis.scale(0.35)).normalize();

                putVertexN(vc, last, (float) aPt0.x, (float) aPt0.y, (float) aPt0.z, (float) nrm.x, (float) nrm.y, (float) nrm.z, argb, u0, 1f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, (float) aPt1.x, (float) aPt1.y, (float) aPt1.z, (float) nrm.x, (float) nrm.y, (float) nrm.z, argb, u1, 1f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, (float) p1.x, (float) p1.y, (float) p1.z, (float) nrm.x, (float) nrm.y, (float) nrm.z, scaleAlpha(argb, 0.32f), u1, 0f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, (float) p0.x, (float) p0.y, (float) p0.z, (float) nrm.x, (float) nrm.y, (float) nrm.z, scaleAlpha(argb, 0.32f), u0, 0f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
            }
        }
    }

    /** 3D Flapping Crimson Healing Butterflies spiraling from transmuted projectile to player! */
    public static void crimsonButterflySwarm(PoseStack pose, MultiBufferSource buffers,
                                             Vec3 from, Vec3 to, int count, float p, float age, int argb) {
        boolean custom = InfinityCosmicShader.available();
        VertexConsumer vc = buffers.getBuffer(InfinityCosmicShader.additiveGlow());
        int er = encodeErosion(p * 0.65f);

        for (int i = 0; i < count; i++) {
            float localP = Mth.clamp(p * 1.15f - i * 0.06f, 0f, 1f);
            Vec3 base = from.lerp(to, localP);
            float swirl = age * 0.35f + i * 2.1f;
            float orbit = (1f - localP) * 0.85f + 0.15f;
            double bx = base.x + Mth.cos(swirl) * orbit;
            double by = base.y + Mth.sin(localP * (float) Math.PI) * 0.95 + Mth.sin(swirl * 1.3f) * 0.25;
            double bz = base.z + Mth.sin(swirl) * orbit;

            float flap = Mth.sin(age * 1.45f + i * 1.9f) * 48f;
            float wingS = 0.22f * (1f - localP * 0.3f);

            pose.pushPose();
            pose.translate(bx, by, bz);
            pose.mulPose(Axis.YP.rotationDegrees(swirl * Mth.RAD_TO_DEG));
            for (int side : new int[]{-1, 1}) {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(side * flap));
                PoseStack.Pose last = pose.last();
                putVertexN(vc, last, 0f, 0f, -wingS * 0.6f, 0f, 1f, 0f, argb, 0.5f, 0.5f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, side * wingS * 1.3f, 0f, -wingS * 0.9f, 0f, 1f, 0f, argb, 1f, 1f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, side * wingS * 1.1f, 0f, wingS * 0.8f, 0f, 1f, 0f, argb, 1f, 0f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, 0f, 0f, wingS * 0.5f, 0f, 1f, 0f, argb, 0.5f, 0.5f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                pose.popPose();
            }
            pose.popPose();
        }
    }

    /** Floating Psionic Puppet Crown + Neural Strings above 4-Layer Mind-Controlled Mobs! */
    public static void puppetCrown(PoseStack pose, MultiBufferSource buffers, float radius, float time) {
        armillaryRing(pose, buffers, radius * 0.72f, radius, 24, 0xeeffd84d, time * 5.0f, 0.05f);
        eruptingCrystalCluster(pose, buffers, 4, radius * 0.78f, 0.28f, 0.06f, 14f, 0xeefff2a6, time * 5.0f, 0.05f);
        for (int i = 0; i < 4; i++) {
            float a = i * TAU / 4f + time * 0.09f;
            Vec3 top = new Vec3(Mth.cos(a) * radius * 0.65f, 0f, Mth.sin(a) * radius * 0.65f);
            Vec3 bot = new Vec3(Mth.cos(a) * radius * 0.25f, -0.55f, Mth.sin(a) * radius * 0.25f);
            silkSlipstream(pose, buffers, top, bot, 0.035f, 0.01f, 0xd8ffe680, 0.15f);
        }
    }

    /** Tapered FBM-eroded silk slipstream ribbon. */
    public static void silkSlipstream(PoseStack pose, MultiBufferSource buffers,
                                      Vec3 start, Vec3 end, float startHalfW, float endHalfW,
                                      int argb, float erosion) {
        Vec3 diff = end.subtract(start);
        double len = diff.length();
        if (len < 1e-4) return;
        Vec3 dir = diff.scale(1.0 / len);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-4) side = dir.cross(new Vec3(1, 0, 0));
        side = side.normalize();
        Vec3 nrm = dir.cross(side).normalize();

        boolean custom = InfinityCosmicShader.available();
        VertexConsumer vc = buffers.getBuffer(InfinityCosmicShader.additiveGlow());
        int er = encodeErosion(erosion);
        PoseStack.Pose last = pose.last();
        int col = scaleAlpha(argb, cameraClearance(start.lerp(end, 0.5)));

        Vec3 sOff = side.scale(startHalfW);
        Vec3 eOff = side.scale(endHalfW);
        float uSpan = Math.max(1.0f, (float) (len * 0.35));
        float nx = (float) nrm.x, ny = (float) nrm.y, nz = (float) nrm.z;

        putVertexN(vc, last, (float) (start.x - sOff.x), (float) (start.y - sOff.y), (float) (start.z - sOff.z), nx, ny, nz, col, 0f, 0f, InfinityCosmicShader.MODE_SILK_FILAMENT, er, custom);
        putVertexN(vc, last, (float) (start.x + sOff.x), (float) (start.y + sOff.y), (float) (start.z + sOff.z), nx, ny, nz, col, 0f, 1f, InfinityCosmicShader.MODE_SILK_FILAMENT, er, custom);
        putVertexN(vc, last, (float) (end.x + eOff.x), (float) (end.y + eOff.y), (float) (end.z + eOff.z), nx, ny, nz, scaleAlpha(col, 0.15f), uSpan, 1f, InfinityCosmicShader.MODE_SILK_FILAMENT, encodeErosion(Math.min(1f, erosion + 0.45f)), custom);
        putVertexN(vc, last, (float) (end.x - eOff.x), (float) (end.y - eOff.y), (float) (end.z - eOff.z), nx, ny, nz, scaleAlpha(col, 0.15f), uSpan, 0f, InfinityCosmicShader.MODE_SILK_FILAMENT, encodeErosion(Math.min(1f, erosion + 0.45f)), custom);
    }

    /** 3D Minkowski Double Light Cone with true outward conical normals. */
    public static void minkowskiLightCone(PoseStack pose, MultiBufferSource buffers,
                                          float radius, float halfHeight, int segs,
                                          int upperArgb, int lowerArgb, float spinDeg, float erosion) {
        boolean custom = InfinityCosmicShader.available();
        VertexConsumer vc = buffers.getBuffer(InfinityCosmicShader.additiveGlow());
        int er = encodeErosion(erosion);
        pose.pushPose();
        if (spinDeg != 0f) pose.mulPose(Axis.YP.rotationDegrees(spinDeg));
        PoseStack.Pose last = pose.last();

        float apexR = Math.max(0.06f, radius * 0.06f);
        for (int i = 0; i < segs; i++) {
            float u0 = i / (float) segs;
            float u1 = (i + 1) / (float) segs;
            float a0 = TAU * u0;
            float a1 = TAU * u1;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float nx = (c0 + c1) * 0.5f;
            float nz = (s0 + s1) * 0.5f;

            putVertexN(vc, last, c0 * apexR, 0f, s0 * apexR, nx, -0.45f, nz, upperArgb, u0 * 2f, 0f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
            putVertexN(vc, last, c1 * apexR, 0f, s1 * apexR, nx, -0.45f, nz, upperArgb, u1 * 2f, 0f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
            putVertexN(vc, last, c1 * radius, halfHeight, s1 * radius, nx, -0.45f, nz, scaleAlpha(upperArgb, 0.24f), u1 * 2f, 1f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
            putVertexN(vc, last, c0 * radius, halfHeight, s0 * radius, nx, -0.45f, nz, scaleAlpha(upperArgb, 0.24f), u0 * 2f, 1f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);

            putVertexN(vc, last, c0 * apexR, 0f, s0 * apexR, nx, 0.45f, nz, lowerArgb, u0 * 2f, 0f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
            putVertexN(vc, last, c1 * apexR, 0f, s1 * apexR, nx, 0.45f, nz, lowerArgb, u1 * 2f, 0f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
            putVertexN(vc, last, c1 * radius, -halfHeight, s1 * radius, nx, 0.45f, nz, scaleAlpha(lowerArgb, 0.24f), u1 * 2f, 1f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
            putVertexN(vc, last, c0 * radius, -halfHeight, s0 * radius, nx, 0.45f, nz, scaleAlpha(lowerArgb, 0.24f), u0 * 2f, 1f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
        }
        pose.popPose();
    }

    /** 3D Helical Vortex Strands twisting from start to end. */
    public static void helicalVortex(PoseStack pose, MultiBufferSource buffers,
                                     Vec3 start, Vec3 end, float startR, float endR,
                                     int strands, float turns, float ribbonHalfW,
                                     int argb, float phaseRad, float erosion) {
        Vec3 diff = end.subtract(start);
        double len = diff.length();
        if (len < 1e-4) return;
        Vec3 axis = diff.scale(1.0 / len);
        Vec3 uVec = axis.cross(new Vec3(0, 1, 0));
        if (uVec.lengthSqr() < 1e-4) uVec = axis.cross(new Vec3(1, 0, 0));
        uVec = uVec.normalize();
        Vec3 vVec = axis.cross(uVec).normalize();

        boolean custom = InfinityCosmicShader.available();
        VertexConsumer vc = buffers.getBuffer(InfinityCosmicShader.additiveGlow());
        int er = encodeErosion(erosion);
        PoseStack.Pose last = pose.last();
        int steps = 18;

        for (int s = 0; s < strands; s++) {
            float strandOffset = s * TAU / strands + phaseRad;
            Vec3 prev = null;
            for (int i = 0; i <= steps; i++) {
                float t = i / (float) steps;
                float r = Mth.lerp(t, startR, endR);
                float ang = strandOffset + t * turns * TAU;
                Vec3 center = start.lerp(end, t);
                Vec3 radial = uVec.scale(Mth.cos(ang)).add(vVec.scale(Mth.sin(ang)));
                Vec3 pt = center.add(radial.scale(r));
                if (prev != null) {
                    Vec3 segDir = pt.subtract(prev).normalize();
                    Vec3 side = segDir.cross(radial).normalize().scale(ribbonHalfW);
                    float env = Mth.sin(t * (float) Math.PI);
                    int c = scaleAlpha(argb, 0.25f + 0.75f * env);
                    float nx = (float) radial.x, ny = (float) radial.y, nz = (float) radial.z;
                    putVertexN(vc, last, (float) (prev.x - side.x), (float) (prev.y - side.y), (float) (prev.z - side.z), nx, ny, nz, c, t * 2f, 0f, InfinityCosmicShader.MODE_SILK_FILAMENT, er, custom);
                    putVertexN(vc, last, (float) (prev.x + side.x), (float) (prev.y + side.y), (float) (prev.z + side.z), nx, ny, nz, c, t * 2f, 1f, InfinityCosmicShader.MODE_SILK_FILAMENT, er, custom);
                    putVertexN(vc, last, (float) (pt.x + side.x), (float) (pt.y + side.y), (float) (pt.z + side.z), nx, ny, nz, c, (t + 0.1f) * 2f, 1f, InfinityCosmicShader.MODE_SILK_FILAMENT, er, custom);
                    putVertexN(vc, last, (float) (pt.x - side.x), (float) (pt.y - side.y), (float) (pt.z - side.z), nx, ny, nz, c, (t + 0.1f) * 2f, 0f, InfinityCosmicShader.MODE_SILK_FILAMENT, er, custom);
                }
                prev = pt;
            }
        }
    }

    /** 3D Hollow Fresnel Lensing Shock Dome with true spherical outward normals! */
    public static void volumetricShockDome(PoseStack pose, MultiBufferSource buffers,
                                           float radius, int latSteps, int lonSteps,
                                           int argb, float spinDeg, float erosion) {
        boolean custom = InfinityCosmicShader.available();
        VertexConsumer vc = buffers.getBuffer(InfinityCosmicShader.additiveGlow());
        int er = encodeErosion(erosion);
        pose.pushPose();
        if (spinDeg != 0f) pose.mulPose(Axis.YP.rotationDegrees(spinDeg));
        PoseStack.Pose last = pose.last();

        for (int lat = 0; lat < latSteps; lat++) {
            float v0 = lat / (float) latSteps;
            float v1 = (lat + 1) / (float) latSteps;
            float phi0 = (float) (Math.PI * 0.5) * v0;
            float phi1 = (float) (Math.PI * 0.5) * v1;
            float cp0 = Mth.cos(phi0), sp0 = Mth.sin(phi0);
            float cp1 = Mth.cos(phi1), sp1 = Mth.sin(phi1);

            for (int lon = 0; lon < lonSteps; lon++) {
                float u0 = lon / (float) lonSteps;
                float u1 = (lon + 1) / (float) lonSteps;
                float th0 = TAU * u0;
                float th1 = TAU * u1;
                float c0 = Mth.cos(th0), s0 = Mth.sin(th0);
                float c1 = Mth.cos(th1), s1 = Mth.sin(th1);

                putVertexN(vc, last, c0 * cp0 * radius, sp0 * radius, s0 * cp0 * radius, c0 * cp0, sp0, s0 * cp0, argb, u0 * 2f, v0, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, c1 * cp0 * radius, sp0 * radius, s1 * cp0 * radius, c1 * cp0, sp0, s1 * cp0, argb, u1 * 2f, v0, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, c1 * cp1 * radius, sp1 * radius, s1 * cp1 * radius, c1 * cp1, sp1, s1 * cp1, argb, u1 * 2f, v1, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, c0 * cp1 * radius, sp1 * radius, s0 * cp1 * radius, c0 * cp1, sp1, s0 * cp1, argb, u0 * 2f, v1, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
            }
        }
        pose.popPose();
    }

    /** 3D Orbiting Faceted Polyhedral Shards with true octahedral facet normals! */
    public static void floatingPolyShards(PoseStack pose, MultiBufferSource buffers,
                                          int count, float orbitR, float shardSize, float heightSpread,
                                          int argb, float time, float erosion) {
        boolean custom = InfinityCosmicShader.available();
        VertexConsumer vc = buffers.getBuffer(InfinityCosmicShader.additiveGlow());
        int er = encodeErosion(erosion);
        for (int i = 0; i < count; i++) {
            float ang = i * TAU / count + time * (0.09f + (i % 3) * 0.03f);
            float r = orbitR * (0.72f + 0.32f * Mth.sin(i * 2.1f + time * 0.08f));
            float y = Mth.sin(i * 1.7f + time * 0.14f) * heightSpread;
            pose.pushPose();
            pose.translate(Mth.cos(ang) * r, y, Mth.sin(ang) * r);
            pose.mulPose(Axis.YP.rotationDegrees(time * 5.5f + i * 42f));
            pose.mulPose(Axis.XP.rotationDegrees(28f + i * 19f));
            PoseStack.Pose last = pose.last();
            float s = shardSize * (0.75f + 0.35f * ((i % 3) * 0.5f));
            float h = s * 1.9f;
            for (int f = 0; f < 4; f++) {
                float a0 = f * TAU / 4f;
                float a1 = (f + 1) * TAU / 4f;
                float x0 = Mth.cos(a0) * s, z0 = Mth.sin(a0) * s;
                float x1 = Mth.cos(a1) * s, z1 = Mth.sin(a1) * s;
                float nx = Mth.cos((a0 + a1) * 0.5f), nz = Mth.sin((a0 + a1) * 0.5f);
                putVertexN(vc, last, 0f, h, 0f, nx, 0.45f, nz, argb, 0.5f, 1f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, x0, 0f, z0, nx, 0.25f, nz, argb, 0f, 0.5f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, x1, 0f, z1, nx, 0.25f, nz, argb, 1f, 0.5f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
                putVertexN(vc, last, 0f, -h, 0f, nx, -0.45f, nz, scaleAlpha(argb, 0.68f), 0.5f, 0f, InfinityCosmicShader.MODE_FRESNEL_SHELL, er, custom);
            }
            pose.popPose();
        }
    }

    /** Multi-segment jagged 3D branching neural / psionic arc. */
    public static void branchingNeuralArc(PoseStack pose, MultiBufferSource buffers,
                                          Vec3 a, Vec3 b, float jitter, float halfW,
                                          int argb, int seed, float erosion) {
        int segs = 8;
        Vec3 prev = a;
        for (int i = 1; i <= segs; i++) {
            float t = i / (float) segs;
            Vec3 p = a.lerp(b, t);
            if (i < segs) {
                float env = Mth.sin(t * (float) Math.PI) * jitter;
                float jx = Mth.sin(seed * 13.7f + i * 4.3f) * env;
                float jy = Mth.cos(seed * 9.1f + i * 5.7f) * env;
                float jz = Mth.sin(seed * 5.3f - i * 3.9f) * env;
                p = p.add(jx, jy, jz);
            }
            silkSlipstream(pose, buffers, prev, p, halfW, halfW * 0.85f, argb, erosion);
            prev = p;
        }
    }

    public static void armillaryRing(PoseStack pose, MultiBufferSource buffers,
                                     float rIn, float rOut, int segs, int argb, float degRot, float erosion) {
        emitRing(pose, buffers, rIn, rOut, segs, argb, degRot, InfinityCosmicShader.MODE_ARMILLARY_RING, erosion);
    }

    public static void crescentShockRing(PoseStack pose, MultiBufferSource buffers,
                                         float rIn, float rOut, int segs, int argb, float degRot, float erosion) {
        if (InfinityCosmicShader.available()) {
            float mid = (rIn + rOut) * 0.5f;
            float span = (rOut - rIn) * 0.85f;
            emitRingOnConsumer(pose, buffers.getBuffer(InfinityCosmicShader.voidSheath()),
                    Math.max(0.05f, mid - span), mid + span * 0.7f, segs,
                    scaleAlpha(0xb8080d1c, extractAlpha(argb)), degRot,
                    InfinityCosmicShader.MODE_DARK_SHEATH, erosion * 0.75f, true);
        }
        emitRing(pose, buffers, rIn, rOut, segs, argb, degRot, InfinityCosmicShader.MODE_CRESCENT_ARC, erosion);
    }

    private static void emitRing(PoseStack pose, MultiBufferSource buffers,
                                 float rIn, float rOut, int segs, int argb, float degRot, int mode, float erosion) {
        boolean custom = InfinityCosmicShader.available();
        VertexConsumer vc = buffers.getBuffer(InfinityCosmicShader.additiveGlow());
        emitRingOnConsumer(pose, vc, rIn, rOut, segs, argb, degRot, mode, erosion, custom);
    }

    private static void emitRingOnConsumer(PoseStack pose, VertexConsumer vc,
                                           float rIn, float rOut, int segs, int argb, float degRot,
                                           int mode, float erosion, boolean custom) {
        pose.pushPose();
        if (degRot != 0f) pose.mulPose(Axis.YP.rotationDegrees(degRot));
        PoseStack.Pose last = pose.last();
        int er = encodeErosion(erosion);
        for (int i = 0; i < segs; i++) {
            float u0 = i / (float) segs;
            float u1 = (i + 1) / (float) segs;
            float a0 = TAU * u0;
            float a1 = TAU * u1;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);

            putVertexN(vc, last, c0 * rIn, 0f, s0 * rIn, 0f, 1f, 0f, argb, u0, 0f, mode, er, custom);
            putVertexN(vc, last, c1 * rIn, 0f, s1 * rIn, 0f, 1f, 0f, argb, u1, 0f, mode, er, custom);
            putVertexN(vc, last, c1 * rOut, 0f, s1 * rOut, 0f, 1f, 0f, argb, u1, 1f, mode, er, custom);
            putVertexN(vc, last, c0 * rOut, 0f, s0 * rOut, 0f, 1f, 0f, argb, u0, 1f, mode, er, custom);
        }
        pose.popPose();
    }

    private static float extractAlpha(int argb) {
        return ((argb >>> 24) & 0xff) / 255.0f;
    }

    private static int scaleAlpha(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 0xff) * Mth.clamp(factor, 0f, 1f));
        return (a << 24) | (argb & 0x00ffffff);
    }
}

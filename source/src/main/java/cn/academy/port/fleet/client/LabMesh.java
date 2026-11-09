package cn.academy.port.fleet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import cn.academy.port.fleet.InteractionLab;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** ACM1 drawing adapter. Mesh, contact points and reload share the asset profile. */
public final class LabMesh {
    private static final AcmMeshReader.Mesh EMPTY = new AcmMeshReader.Mesh(new AcmMeshReader.Part[0], 0);
    private static final Map<String, AcmMeshReader.Mesh> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> LOD_FILES = new ConcurrentHashMap<>();
    private LabMesh() {}

    /** Register this with the client resource reload listener alongside GunAnchors. */
    public static void clearCache() { CACHE.clear(); LOD_FILES.clear(); }

    private static ResourceLocation meshLocation(String id) {
        if (id == null || !id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("invalid mesh ID");
        return ResourceLocation.fromNamespaceAndPath("academy", "mesh/" + id + ".bin");
    }

    private static AcmMeshReader.Mesh load(String id) {
        return CACHE.computeIfAbsent(id, key -> {
            try (var in = Minecraft.getInstance().getResourceManager().getResource(meshLocation(key)).orElseThrow().open()) {
                return AcmMeshReader.read(in);
            } catch (Exception ex) {
                // Cache the failure, report it once and do not crash every frame.
                // An absent asset is still a failed asset check, not a valid mesh.
                InteractionLab.LOGGER.error("Cannot render ACM1 mesh {}: {}", key, ex.getMessage());
                return EMPTY;
            }
        });
    }

    private static String lodFile(String id, int lod) {
        int level = Math.max(0, Math.min(2, lod));
        if (level == 0) return id;
        String candidate = id + "_lod" + level;
        return LOD_FILES.computeIfAbsent(candidate, key ->
                Minecraft.getInstance().getResourceManager().getResource(meshLocation(key)).isPresent() ? key : id);
    }

    public static void draw(String id, PoseStack ps, MultiBufferSource buffers, int light,
                            float wheel, float steering, float reload, float recoil) {
        draw(id, ps, buffers, light, wheel, steering, reload, recoil, false, false);
    }

    public static void draw(String id, PoseStack ps, MultiBufferSource buffers, int light,
                            float wheel, float steering, float reload, float recoil, boolean hideGlass) {
        draw(id, ps, buffers, light, wheel, steering, reload, recoil, hideGlass, false);
    }

    public static void draw(String id, PoseStack ps, MultiBufferSource buffers, int light,
                            float wheel, float steering, float reload, float recoil,
                            boolean hideGlass, boolean emptyReload) {
        drawLod(id, 0, ps, buffers, light, wheel, steering, reload, recoil, hideGlass, emptyReload);
    }

    /** LOD uses the same texture, anchor profile, part names and pivot locations. */
    public static void drawLod(String id, int lod, PoseStack ps, MultiBufferSource buffers, int light,
                               float wheel, float steering, float reload, float recoil,
                               boolean hideGlass, boolean emptyReload) {
        AcmMeshReader.Mesh mesh = load(lodFile(id, lod));
        if (mesh.parts().length == 0) return;
        boolean gun = id.matches("g0[1-6]_[a-z0-9_]+");
        GunAnchors.Profile profile = gun ? GunAnchors.forId(id) : null;
        ReloadTimeline.Pose motion = ReloadTimeline.sample(reload, emptyReload);
        float steer = finiteClamp(steering, -1, 1);
        float spin = Float.isFinite(wheel) ? wheel % 360 : 0;
        float kick = finiteClamp(recoil, 0, 1.25f);
        ResourceLocation tex = ResourceLocation.fromNamespaceAndPath("academy", "textures/models/" + id + ".png");
        for (AcmMeshReader.Part part : mesh.parts()) {
            if (hideGlass && part.name().equals("glass")) continue;
            if (gun && part.name().equals("pivot_energy_cell") && !motion.magazineVisible()) continue;
            VertexConsumer out = buffers.getBuffer(part.name().equals("glass")
                    ? RenderType.entityTranslucent(tex) : RenderType.entityCutoutNoCull(tex));
            ps.pushPose();
            if (id.equals("v03_campus_scooter") && part.name().equals("pivot_wheel_front")) {
                // Existing scooter pivot calibration; its source model is not in this handover.
                float sy = .70f * 1.17f, sz = .50f * 1.17f;
                ps.translate(0, sy, sz);
                ps.mulPose(Axis.YP.rotationDegrees(-steer * 24));
                ps.translate(part.x(), part.y() - sy, part.z() - sz);
                ps.mulPose(Axis.XP.rotationDegrees(spin));
            } else {
                ps.translate(part.x(), part.y(), part.z());
                if (part.name().startsWith("pivot_wheel")) {
                    if (part.name().contains("FL") || part.name().contains("FR")) ps.mulPose(Axis.YP.rotationDegrees(-steer * 24));
                    ps.mulPose(Axis.XP.rotationDegrees(spin));
                } else if (part.name().equals("pivot_steer_front")) {
                    ps.mulPose(Axis.YP.rotationDegrees(-steer * 24));
                } else if (gun && part.name().equals("pivot_energy_cell")) {
                    ps.translate(0, -profile.magazineDropDistance() * motion.magazineDrop(), 0);
                    ps.mulPose(Axis.XP.rotationDegrees(profile.magazineTiltDegrees() * motion.magazineTilt()));
                } else if (gun && (part.name().equals("pivot_slide") || part.name().equals("pivot_charge"))) {
                    ps.translate(0, 0, -profile.slideTravel() * Math.min(1.25f, motion.charge() + kick));
                }
            }
            var pose = ps.last();
            float[] values = part.values();
            for (int triangle = 0; triangle < part.triangles(); triangle++) {
                // Minecraft's entity render type is QUADS. Duplicating vertex 2
                // emits one real triangle plus one degenerate triangle, as in the
                // handover renderer; no incorrect fourth corner is introduced.
                for (int corner = 0; corner < 4; corner++) {
                    int i = (triangle * 3 + Math.min(corner, 2)) * 8;
                    out.addVertex(pose, values[i], values[i + 1], values[i + 2])
                            .setColor(255, 255, 255, 255).setUv(values[i + 6], values[i + 7])
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                            .setNormal(pose, values[i + 3], values[i + 4], values[i + 5]);
                }
            }
            ps.popPose();
        }
    }

    private static float finiteClamp(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : 0;
    }
}

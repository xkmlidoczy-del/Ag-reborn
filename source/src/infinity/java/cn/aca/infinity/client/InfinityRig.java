package cn.aca.infinity.client;

import cn.aca.infinity.AcaInfinity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class InfinityRig {
    public record BonePose(float x, float y, float z, float pitch, float yaw, float roll, float scale, int tint) {
        public static final BonePose IDENTITY = new BonePose(0, 0, 0, 0, 0, 0, 1f, -1);
        public static final BonePose HIDDEN = new BonePose(0, 0, 0, 0, 0, 0, 0f, -1);

        public static BonePose rot(float pitch, float yaw, float roll) {
            return new BonePose(0, 0, 0, pitch, yaw, roll, 1f, -1);
        }
    }

    @FunctionalInterface
    public interface PoseProvider {
        BonePose poseFor(String boneId);
    }

    public static final PoseProvider STILL = id -> BonePose.IDENTITY;

    private static final Map<String, InfinityRig> CACHE = new LinkedHashMap<>();
    private static final float UNIT = 1f / 16f;
    private static final int[][] FACES = {
            {1, 0, 4, 5}, {3, 2, 6, 7}, {4, 0, 3, 7},
            {0, 1, 2, 3}, {1, 5, 6, 2}, {5, 4, 7, 6}
    };
    private static final int[][] NORMALS = {
            {0, -1, 0}, {0, 1, 0}, {-1, 0, 0},
            {0, 0, -1}, {1, 0, 0}, {0, 0, 1}
    };
    // Directional hemisphere shading per face (-Y, +Y, -X, -Z, +X, +Z) so 3D armor facets & wings read with crisp depth!
    private static final float[] FACE_SHADE = {
            0.54f, 1.00f, 0.73f, 0.85f, 0.76f, 0.88f
    };

    private static int shadeColor(int argb, float factor) {
        int a = (argb >>> 24) & 0xff;
        int r = Math.min(255, Math.round(((argb >>> 16) & 0xff) * factor));
        int g = Math.min(255, Math.round(((argb >>> 8) & 0xff) * factor));
        int b = Math.min(255, Math.round((argb & 0xff) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private record Box(float[][] verts, float[][] uv, boolean glow) {
        static Box parse(JsonObject obj, float px, float py, float pz, float texel) {
            var f = obj.getAsJsonArray("from");
            var t = obj.getAsJsonArray("to");
            float x1 = f.get(0).getAsFloat() - px;
            float y1 = f.get(1).getAsFloat() - py;
            float z1 = f.get(2).getAsFloat() - pz;
            float x2 = t.get(0).getAsFloat() - px;
            float y2 = t.get(1).getAsFloat() - py;
            float z2 = t.get(2).getAsFloat() - pz;
            var uvArr = obj.getAsJsonArray("uv");
            float[][] faces = new float[6][4];
            for (int s = 0; s < 6; s++) {
                var r = uvArr.get(s).getAsJsonArray();
                for (int i = 0; i < 4; i++) faces[s][i] = r.get(i).getAsFloat() * texel;
            }
            boolean glow = obj.has("glow") && obj.get("glow").getAsBoolean();
            return new Box(new float[][]{
                    {x1, y1, z1}, {x2, y1, z1}, {x2, y2, z1}, {x1, y2, z1},
                    {x1, y1, z2}, {x2, y1, z2}, {x2, y2, z2}, {x1, y2, z2}
            }, faces, glow);
        }

        void emit(PoseStack.Pose pose, VertexConsumer vc, int color, int light, int overlay) {
            int packedLight = glow ? LightTexture.FULL_BRIGHT : light;
            for (int s = 0; s < 6; s++) {
                int[] n = NORMALS[s];
                float[] r = uv[s];
                int faceColor = glow ? color : shadeColor(color, FACE_SHADE[s]);
                for (int i = 0; i < 4; i++) {
                    float[] p = verts[FACES[s][i]];
                    float u = r[i == 0 || i == 3 ? 0 : 2];
                    float v = r[i < 2 ? 3 : 1];
                    vc.addVertex(pose, p[0], p[1], p[2])
                            .setColor(faceColor)
                            .setUv(u, v)
                            .setOverlay(overlay)
                            .setLight(packedLight)
                            .setNormal(pose, n[0], n[1], n[2]);
                }
            }
        }
    }

    private static final class Bone {
        final String id;
        final float x, y, z, pitch, yaw, roll;
        final List<Box> boxes = new ArrayList<>();
        final List<Bone> children = new ArrayList<>();

        Bone(JsonObject obj, float texel) {
            id = obj.get("id").getAsString();
            var p = obj.getAsJsonArray("p");
            var r = obj.getAsJsonArray("r");
            x = p.get(0).getAsFloat();
            y = p.get(1).getAsFloat();
            z = p.get(2).getAsFloat();
            pitch = r.get(0).getAsFloat();
            yaw = r.get(1).getAsFloat();
            roll = r.get(2).getAsFloat();
            for (var el : obj.getAsJsonArray("boxes")) {
                boxes.add(Box.parse(el.getAsJsonObject(), x, y, z, texel));
            }
        }

        void render(PoseStack pose, VertexConsumer vc, PoseProvider anim,
                    float px, float py, float pz, int baseColor, int light, int overlay) {
            BonePose bp = anim.poseFor(id);
            if (bp.scale() <= 0.001f) return;
            pose.pushPose();
            pose.translate(x - px + bp.x(), y - py + bp.y(), z - pz + bp.z());
            rotate(pose, pitch + bp.pitch(), yaw + bp.yaw(), roll + bp.roll());
            if (bp.scale() != 1f) pose.scale(bp.scale(), bp.scale(), bp.scale());
            int drawCol = bp.tint() != -1 ? bp.tint() : baseColor;
            for (Box b : boxes) {
                b.emit(pose.last(), vc, drawCol, light, overlay);
            }
            for (Bone c : children) {
                c.render(pose, vc, anim, x, y, z, drawCol, light, overlay);
            }
            pose.popPose();
        }
    }

    public final float height;
    private final ResourceLocation texture;
    private final Bone root;

    private InfinityRig(String name, JsonObject json) {
        this.height = json.get("height").getAsFloat();
        this.texture = AcaInfinity.id("textures/rig/" + name + ".png");
        float texel = 1f / json.get("texture_size").getAsInt();
        Map<String, Bone> map = new LinkedHashMap<>();
        for (var el : json.getAsJsonArray("bones")) {
            var obj = el.getAsJsonObject();
            Bone b = new Bone(obj, texel);
            map.put(b.id, b);
            if (!obj.get("parent").isJsonNull()) {
                Bone parent = map.get(obj.get("parent").getAsString());
                if (parent != null) parent.children.add(b);
            }
        }
        this.root = map.get("root");
    }

    public static InfinityRig get(String name) {
        return CACHE.computeIfAbsent(name, key -> {
            ResourceLocation loc = AcaInfinity.id("rigs/" + key + ".json");
            try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(loc)) {
                return new InfinityRig(key, JsonParser.parseReader(reader).getAsJsonObject());
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to load rig: " + loc, ex);
            }
        });
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public ResourceLocation texture() {
        return texture;
    }

    public void render(PoseStack pose, MultiBufferSource buffers, PoseProvider anim,
                       float scale, int color, int light, int overlay) {
        if (root == null) return;
        pose.pushPose();
        pose.scale(UNIT * scale, UNIT * scale, UNIT * scale);
        RenderType rt = (color >>> 24) < 255
                ? RenderType.entityTranslucent(texture)
                : RenderType.entityCutoutNoCull(texture);
        VertexConsumer vc = buffers.getBuffer(rt);
        root.render(pose, vc, anim, 0, 0, 0, color, light, overlay);
        pose.popPose();
    }

    public static void rotate(PoseStack pose, float pitch, float yaw, float roll) {
        if (roll != 0f) pose.mulPose(Axis.ZP.rotationDegrees(roll));
        if (yaw != 0f) pose.mulPose(Axis.YP.rotationDegrees(yaw));
        if (pitch != 0f) pose.mulPose(Axis.XP.rotationDegrees(pitch));
    }
}

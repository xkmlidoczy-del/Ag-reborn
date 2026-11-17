package cn.academy.biohum.client;

import cn.academy.biohum.Profiles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A six-face detail with the same texture layout as a vanilla model cube. */
final class AccessoryMesh {
    private static final float MODEL_UNITS = 16f;
    private static final float ATLAS_UNITS = 128f;

    private record Face(Vector3f[] corners, Vector3f normal, float u0, float v0, float u1, float v1) {
        static Face from(Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                         float u, float v, float width, float height) {
            var normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize();
            return new Face(new Vector3f[]{a, b, c, d}, normal,
                    u / ATLAS_UNITS, v / ATLAS_UNITS,
                    (u + width) / ATLAS_UNITS, (v + height) / ATLAS_UNITS);
        }

        void draw(PoseStack.Pose pose, VertexConsumer out, int light, int overlay) {
            emit(pose, out, corners[0], u1, v0, light, overlay);
            emit(pose, out, corners[1], u0, v0, light, overlay);
            emit(pose, out, corners[2], u0, v1, light, overlay);
            emit(pose, out, corners[3], u1, v1, light, overlay);
        }

        private void emit(PoseStack.Pose pose, VertexConsumer out, Vector3f p,
                          float u, float v, int light, int overlay) {
            out.addVertex(pose, p).setColor(-1).setUv(u, v).setOverlay(overlay).setLight(light)
                    .setNormal(pose, normal.x, normal.y, normal.z);
        }
    }

    private final Vector3f pivot;
    private final Quaternionf rotation;
    private final Face[] faces;

    AccessoryMesh(Profiles.Accessory detail) {
        float[] p = detail.pivot();
        float[] r = detail.rotation();
        float[] b = detail.box();
        float[] t = detail.taper();
        pivot = new Vector3f(p[0], p[1], p[2]).div(MODEL_UNITS);
        rotation = new Quaternionf().rotationZYX(r[2], r[1], r[0]);
        float w = b[3], h = b[4], d = b[5];
        float centerX = b[0] + w / 2 - p[0];
        float centerZ = b[2] + d / 2 - p[2];
        Vector3f[] top = ring(centerX, b[1] - p[1], centerZ, w * t[0], d * t[1]);
        Vector3f[] bottom = ring(centerX, b[1] + h - p[1], centerZ, w * t[2], d * t[3]);
        float u = detail.uv()[0], v = detail.uv()[1];
        float sideY = v + d;
        faces = new Face[]{
            Face.from(top[1], top[0], bottom[0], bottom[1], u + d, sideY, w, h),
            Face.from(top[3], top[2], bottom[2], bottom[3], u + 2 * d + w, sideY, w, h),
            Face.from(top[0], top[3], bottom[3], bottom[0], u, sideY, d, h),
            Face.from(top[2], top[1], bottom[1], bottom[2], u + d + w, sideY, d, h),
            Face.from(top[2], top[3], top[0], top[1], u + d, v, w, d),
            Face.from(bottom[1], bottom[0], bottom[3], bottom[2], u + d + w, v, w, d),
        };
    }

    private static Vector3f[] ring(float x, float y, float z, float w, float d) {
        float x0 = (x - w / 2) / MODEL_UNITS, x1 = (x + w / 2) / MODEL_UNITS;
        float z0 = (z - d / 2) / MODEL_UNITS, z1 = (z + d / 2) / MODEL_UNITS;
        y /= MODEL_UNITS;
        return new Vector3f[]{
            new Vector3f(x0, y, z0), new Vector3f(x1, y, z0),
            new Vector3f(x1, y, z1), new Vector3f(x0, y, z1),
        };
    }

    void draw(PoseStack pose, VertexConsumer out, int light, int overlay, float length) {
        pose.translate(pivot.x, pivot.y, pivot.z);
        pose.mulPose(rotation);
        if (length != 1) pose.scale(1, length, 1);
        var transform = pose.last();
        for (Face face : faces) face.draw(transform, out, light, overlay);
    }
}

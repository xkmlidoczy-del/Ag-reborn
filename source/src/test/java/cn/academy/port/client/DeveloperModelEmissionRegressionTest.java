package cn.academy.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Exercises the actual modern VertexConsumer emission without Minecraft startup or an OpenGL context. */
public final class DeveloperModelEmissionRegressionTest {
    private static int assertions;
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void eq(double expected, double actual) { check(Double.isFinite(actual) && Math.abs(expected - actual) < 1E-5, expected + " != " + actual); }
    private record Emitted(float x, float y, float z, float u, float v, float nx, float ny, float nz, int color, int overlayU, int overlayV, int lightU, int lightV) {}
    private static final class Recorder implements VertexConsumer {
        final ArrayList<Emitted> vertices = new ArrayList<>();
        float x, y, z, u, v;
        int color, overlayU, overlayV, lightU, lightV;
        int attributes;
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            check(attributes == 0, "Previous vertex completed");
            this.x = x; this.y = y; this.z = z; attributes = 1; return this;
        }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) { color = (alpha << 24) | (red << 16) | (green << 8) | blue; attributes |= 2; return this; }
        @Override public VertexConsumer setUv(float u, float v) { this.u = u; this.v = v; attributes |= 4; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { overlayU = u; overlayV = v; attributes |= 8; return this; }
        @Override public VertexConsumer setUv2(int u, int v) { lightU = u; lightV = v; attributes |= 16; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) {
            check(attributes == 31, "All NEW_ENTITY attributes emitted before normal");
            vertices.add(new Emitted(this.x, this.y, this.z, u, v, x, y, z, color, overlayU, overlayV, lightU, lightV));
            attributes = 0; return this;
        }
    }
    public static void main(String[] args) throws Exception {
        int light = 0x00F00080, overlay = 0x000A0003;
        for (String name : new String[]{"normal", "advanced"}) {
            ClassicDeveloperObj.Mesh mesh;
            try (var stream = DeveloperModelEmissionRegressionTest.class.getResourceAsStream("/assets/academy/models/developer_" + name + ".obj");
                 var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) { mesh = ClassicDeveloperObj.parse(reader); }
            for (var facing : ClassicDeveloperTransform.Facing.values()) {
                var poses = new PoseStack();
                poses.translate(.5, 0, .5);
                poses.mulPose(Axis.YP.rotationDegrees(facing.blockRotation()));
                poses.mulPose(Axis.YP.rotationDegrees(ClassicDeveloperTransform.MODEL_Y_ROTATION));
                poses.scale(.5f, .5f, .5f);
                var recorded = new Recorder();
                ClassicDeveloperRenderer.emitMesh(mesh, poses.last(), recorded, light, overlay);
                check(recorded.vertices.size() == mesh.triangles().size() * 4, "One modern quad for each source triangle");
                int i = 0;
                for (var triangle : mesh.triangles()) {
                    for (var vertex : new ClassicDeveloperObj.Vertex[]{triangle.a(), triangle.b(), triangle.c(), triangle.c()}) {
                        var actual = recorded.vertices.get(i++);
                        var expected = ClassicDeveloperTransform.worldPoint(vertex.x(), vertex.y(), vertex.z(), facing);
                        eq(expected.x(), actual.x); eq(expected.y(), actual.y); eq(expected.z(), actual.z);
                        eq(vertex.u(), actual.u); eq(vertex.v(), actual.v);
                        check(actual.color == 0xFFFFFFFF, "Source white un-tinted color");
                        check(actual.overlayU == 3 && actual.overlayV == 10, "Packed overlay preserved");
                        check(actual.lightU == 128 && actual.lightV == 240, "Packed world light preserved");
                        eq(1, actual.nx * actual.nx + actual.ny * actual.ny + actual.nz * actual.nz);
                    }
                    check(recorded.vertices.get(i - 1).equals(recorded.vertices.get(i - 2)), "Fourth vertex duplicates third exactly");
                }
            }
        }
        System.out.println("DeveloperModelEmissionRegressionTest: " + assertions + " assertions passed; actual modern buffers preserve source triangles, four facing transforms, UVs and packed light");
    }
}

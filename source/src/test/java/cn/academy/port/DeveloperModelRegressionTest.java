package cn.academy.port;

import cn.academy.port.client.ClassicDeveloperObj;
import cn.academy.port.client.ClassicDeveloperTransform;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Original asset identity, live parser topology, legacy UV/normal semantics and cardinal geometry. */
public final class DeveloperModelRegressionTest {
    private static int assertions;
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static void eq(double expected, double actual) { check(Double.isFinite(actual) && Math.abs(expected - actual) < 1E-6, expected + " != " + actual); }
    private static byte[] resource(String name) throws IOException {
        try (var stream = DeveloperModelRegressionTest.class.getResourceAsStream("/assets/academy/" + name)) {
            if (stream == null) throw new IOException("Missing resource " + name);
            return stream.readAllBytes();
        }
    }
    private static byte[] original(String name, String expectedHash) throws Exception {
        byte[] bytes = resource(name);
        check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(expectedHash), "Original source bytes changed: " + name);
        return bytes;
    }
    private static ClassicDeveloperObj.Mesh model(String name, String hash, int positions, int uvs, int normals, int triangles) throws Exception {
        byte[] bytes = original("models/developer_" + name + ".obj", hash);
        var mesh = ClassicDeveloperObj.parse(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
        check(mesh.positionCount() == positions, name + " original vertex count");
        check(mesh.uvCount() == uvs, name + " original UV count");
        check(mesh.normalCount() == normals, name + " original normal count");
        check(mesh.triangles().size() == triangles, name + " original triangle topology");
        for (var triangle : mesh.triangles()) {
            for (var vertex : new ClassicDeveloperObj.Vertex[]{triangle.a(), triangle.b(), triangle.c()}) {
                check(Float.isFinite(vertex.u()) && Float.isFinite(vertex.v()), name + " finite UV");
                eq(triangle.a().nx(), vertex.nx()); eq(triangle.a().ny(), vertex.ny()); eq(triangle.a().nz(), vertex.nz());
                eq(1, vertex.nx() * vertex.nx() + vertex.ny() * vertex.ny() + vertex.nz() * vertex.nz());
            }
        }
        try { mesh.triangles().clear(); throw new AssertionError("Mutable triangles"); } catch (UnsupportedOperationException expected) { assertions++; }
        for (var facing : ClassicDeveloperTransform.Facing.values()) {
            var bounds = ClassicDeveloperTransform.worldBounds(mesh.bounds(), facing);
            eq((mesh.bounds().maxY() - mesh.bounds().minY()) * .5, bounds.maxY() - bounds.minY());
            for (var triangle : mesh.triangles())
                for (var vertex : new ClassicDeveloperObj.Vertex[]{triangle.a(), triangle.b(), triangle.c()}) {
                    var p = ClassicDeveloperTransform.worldPoint(vertex.x(), vertex.y(), vertex.z(), facing);
                    check(p.x() >= bounds.minX() && p.x() <= bounds.maxX() && p.y() >= bounds.minY() && p.y() <= bounds.maxY() && p.z() >= bounds.minZ() && p.z() <= bounds.maxZ(), name + " full model frustum bounds " + facing);
                }
        }
        return mesh;
    }
    private static void texture(String name, String hash, int size, int minAlpha, int maxAlpha) throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(original(name, hash)));
        check(image != null && image.getWidth() == size && image.getHeight() == size, "Original texture dimensions " + name);
        int minimum = 255, maximum = 0;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int alpha = image.getRGB(x, y) >>> 24;
            minimum = Math.min(minimum, alpha); maximum = Math.max(maximum, alpha);
        }
        check(minimum == minAlpha && maximum == maxAlpha, "Original alpha range " + name);
    }
    private static void malformed(String obj) throws Exception {
        try { ClassicDeveloperObj.parse(new StringReader(obj)); throw new AssertionError("Malformed OBJ accepted: " + obj); }
        catch (IOException expected) { check(!expected.getMessage().isEmpty(), "Descriptive parser failure"); }
    }
    public static void main(String[] args) throws Exception {
        var normal = model("normal", "2e8c5dd427944493465ca1265b9abfe331a762ac0e965457a0af4f550af16e79", 110, 106, 105, 180);
        var advanced = model("advanced", "202700c6338aea9e23676e942d0a22af5665c88cd1b344973f7096f3563f104b", 132, 120, 127, 198);
        eq(-1.1493, normal.bounds().minX()); eq(1.4541, normal.bounds().maxX());
        eq(.000478, normal.bounds().minY()); eq(5.718179, normal.bounds().maxY());
        eq(-1.157901, normal.bounds().minZ()); eq(4.096198, normal.bounds().maxZ());
        eq(-1.1493, advanced.bounds().minX()); eq(1.4123, advanced.bounds().maxX());
        eq(-.007701, advanced.bounds().minY()); eq(5.709999, advanced.bounds().maxY());
        eq(-1.166542, advanced.bounds().minZ()); eq(4.087558, advanced.bounds().maxZ());
        eq(.4059, normal.triangles().getFirst().a().x());
        eq(.9131, normal.triangles().getFirst().a().u());
        eq(.9271, normal.triangles().getFirst().a().v());
        texture("textures/models/developer_normal.png", "dfd0ad090c1caac5524e8e498e61d212afaefb955e16a3e88a7b668e02684493", 512, 233, 255);
        texture("textures/models/developer_advanced.png", "fc636210eb7809e334369817eb60a72aceda53d4f58d8efd533e339c6b4546ea", 512, 204, 255);
        original("textures/blocks/developer_normal.png", "3ae44b1cf4b7b972333fc7140d24afaca61a92bbeabc4f4fbec641c90b9bda72");
        original("textures/blocks/developer_advanced.png", "57becb5864bab28ec5a7083653b04ceaf55fdcfeaff1d49801ca4af5b4d71b0c");
        for (String name : new String[]{"normal", "advanced"}) {
            String item = new String(resource("models/item/developer_" + name + ".json"), StandardCharsets.UTF_8);
            check(item.contains("minecraft:item/generated") && item.contains("academy:blocks/developer_" + name), "Source 2D inventory icon");
            String block = new String(resource("models/block/developer_" + name + ".json"), StandardCharsets.UTF_8);
            check(block.contains("\"elements\": []") && block.contains("\"particle\""), "Empty chunk model with original particles");
        }
        // Both source transforms must be applied: BlockMulti's cardinal angle, then model Y180.
        eq(.5, ClassicDeveloperTransform.SCALE); eq(180, ClassicDeveloperTransform.MODEL_Y_ROTATION);
        double[][] expected = {{1.5, 2, 3.5}, {-.5, 2, -2.5}, {3.5, 2, -.5}, {-2.5, 2, 1.5}};
        int i = 0;
        for (var facing : ClassicDeveloperTransform.Facing.values()) {
            var point = ClassicDeveloperTransform.worldPoint(2, 4, 6, facing);
            eq(expected[i][0], point.x()); eq(expected[i][1], point.y()); eq(expected[i++][2], point.z());
            double radians = Math.toRadians(facing.blockRotation() + ClassicDeveloperTransform.MODEL_Y_ROTATION);
            eq(point.x(), .5 + Math.cos(radians) * 1 + Math.sin(radians) * 3);
            eq(point.z(), .5 - Math.sin(radians) * 1 + Math.cos(radians) * 3);
        }
        String polygon = "  v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 1 1\nvt 0 1\nvn 1 0 0\nf -4/1/1 -3/2/1 -2/3/1 -1/4/1 # comment\n";
        var quad = ClassicDeveloperObj.parse(new StringReader(polygon));
        check(quad.triangles().size() == 2, "Polygon fan triangulation");
        eq(0, quad.triangles().getFirst().a().nx()); eq(1, quad.triangles().getFirst().a().nz());
        eq(.0005, quad.triangles().getFirst().a().u()); eq(.9995, quad.triangles().getFirst().a().v());
        String vertices = "v 0 0 0\nv 1 0 0\nv 0 1 0\n";
        check(ClassicDeveloperObj.parse(new StringReader(vertices + "f 1 2 3\n")).triangles().size() == 1, "Untextured face support");
        malformed(""); malformed("v NaN 0 0\n"); malformed(vertices + "f 0 2 3\n");
        malformed(vertices + "f 1 2 4\n"); malformed(vertices + "f 1 2\n");
        malformed(vertices + "vt 0 0\nf 1/1 2 3\n");
        malformed(vertices + "f 1//1 2//1 3//1\n"); malformed("unexpected 1 2 3\n");
        System.out.println("DeveloperModelRegressionTest: " + assertions + " assertions passed; original 180/198 triangles and all four cardinal bounds");
    }
}

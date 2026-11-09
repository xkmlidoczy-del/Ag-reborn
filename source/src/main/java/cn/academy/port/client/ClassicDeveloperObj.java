/* AcademyCraft 1.0.7 model adaptation, GPLv3. See NOTICE. */
package cn.academy.port.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Small runtime OBJ reader. Deliberately independent of Minecraft for deterministic asset tests. */
public final class ClassicDeveloperObj {
    public static final float LEGACY_UV_INSET = .0005f;

    public record Point(float x, float y, float z) {}
    public record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {}
    public record Triangle(Vertex a, Vertex b, Vertex c) {}
    public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
    public record Mesh(List<Triangle> triangles, Bounds bounds, int positionCount, int uvCount, int normalCount) {
        public Mesh { triangles = List.copyOf(triangles); }
    }
    private record Uv(float u, float v) {}
    private record Ref(int position, int uv) {}

    private ClassicDeveloperObj() {}

    /**
     * Original Forge WavefrontObject flips V; Face uses flat geometric normals, even when vn exists,
     * and insets each face's UVs toward its average by .0005. Preserve those details rather than
     * substituting a cube, smoothing the old meshes, or silently ignoring malformed resource packs.
     * This parser accepts polygon fan triangulation and relative indices in addition to the source's
     * positive-index triangles. Resources do not use MTL: each machine has one explicit texture.
     */
    public static Mesh parse(Reader input) throws IOException {
        var positions = new ArrayList<Point>();
        var uvs = new ArrayList<Uv>();
        var normals = new ArrayList<Point>();
        var triangles = new ArrayList<Triangle>();
        var reader = input instanceof BufferedReader buffered ? buffered : new BufferedReader(input);
        int lineNumber = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            int comment = line.indexOf('#');
            if (comment >= 0) line = line.substring(0, comment);
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+");
            try {
                switch (parts[0]) {
                    case "v" -> { require(parts.length == 4, "v needs xyz"); positions.add(point(parts)); }
                    case "vt" -> {
                        require(parts.length == 3 || parts.length == 4, "vt needs uv");
                        uvs.add(new Uv(number(parts[1]), 1 - number(parts[2])));
                        if (parts.length == 4) number(parts[3]);
                    }
                    case "vn" -> { require(parts.length == 4, "vn needs xyz"); normals.add(point(parts)); }
                    case "f" -> {
                        require(parts.length >= 4, "face needs at least three vertices");
                        var face = new ArrayList<Ref>();
                        for (int i = 1; i < parts.length; i++) {
                            String[] indices = parts[i].split("/", -1);
                            require(indices.length <= 3, "invalid face reference");
                            int position = index(indices[0], positions.size());
                            int uv = indices.length > 1 && !indices[1].isEmpty() ? index(indices[1], uvs.size()) : -1;
                            if (indices.length > 2 && !indices[2].isEmpty()) index(indices[2], normals.size());
                            face.add(new Ref(position, uv));
                        }
                        boolean textured = face.getFirst().uv >= 0;
                        for (Ref ref : face) require((ref.uv >= 0) == textured, "mixed textured/untextured face");
                        float averageU = 0, averageV = 0;
                        if (textured) {
                            for (Ref ref : face) { Uv uv = uvs.get(ref.uv); averageU += uv.u; averageV += uv.v; }
                            averageU /= face.size(); averageV /= face.size();
                        }
                        Point normal = faceNormal(positions.get(face.get(0).position), positions.get(face.get(1).position), positions.get(face.get(2).position));
                        var vertices = new ArrayList<Vertex>();
                        for (Ref ref : face) {
                            Point p = positions.get(ref.position);
                            float u = 0, v = 0;
                            if (textured) {
                                Uv uv = uvs.get(ref.uv);
                                u = uv.u + (uv.u > averageU ? -LEGACY_UV_INSET : LEGACY_UV_INSET);
                                v = uv.v + (uv.v > averageV ? -LEGACY_UV_INSET : LEGACY_UV_INSET);
                            }
                            vertices.add(new Vertex(p.x, p.y, p.z, u, v, normal.x, normal.y, normal.z));
                        }
                        for (int i = 1; i + 1 < vertices.size(); i++) triangles.add(new Triangle(vertices.get(0), vertices.get(i), vertices.get(i + 1)));
                    }
                    case "g", "o", "s", "mtllib", "usemtl" -> { /* Single externally supplied texture; render all groups. */ }
                    default -> throw new IllegalArgumentException("unsupported OBJ directive " + parts[0]);
                }
            } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
                throw new IOException("Invalid developer OBJ line " + lineNumber + ": " + exception.getMessage(), exception);
            }
        }
        if (triangles.isEmpty()) throw new IOException("Developer OBJ contains no faces");
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (Point point : positions) {
            minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
        }
        return new Mesh(triangles, new Bounds(minX, minY, minZ, maxX, maxY, maxZ), positions.size(), uvs.size(), normals.size());
    }

    private static Point point(String[] parts) { return new Point(number(parts[1]), number(parts[2]), number(parts[3])); }
    private static float number(String token) { float value = Float.parseFloat(token); require(Float.isFinite(value), "non-finite number"); return value; }
    private static int index(String token, int size) {
        int number = Integer.parseInt(token);
        int index = number > 0 ? number - 1 : size + number;
        require(number != 0 && index >= 0 && index < size, "index outside declared data");
        return index;
    }
    private static Point faceNormal(Point a, Point b, Point c) {
        double abX = (double)b.x - a.x, abY = (double)b.y - a.y, abZ = (double)b.z - a.z;
        double acX = (double)c.x - a.x, acY = (double)c.y - a.y, acZ = (double)c.z - a.z;
        double x = abY * acZ - abZ * acY, y = abZ * acX - abX * acZ, z = abX * acY - abY * acX;
        double length = Math.sqrt(x * x + y * y + z * z);
        // Source Vec3.normalize gives zero for a degenerate triangle; preserve that benign case.
        return length < 1E-4 ? new Point(0, 0, 0) : new Point((float)(x / length), (float)(y / length), (float)(z / length));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}

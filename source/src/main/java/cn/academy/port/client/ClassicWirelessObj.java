/* Original AcademyCraft1.0.7 OBJ groups / Forge Wavefront semantics, GPLv3. See NOTICE. */
package cn.academy.port.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Group-aware adapter; keeps the existing tested flat-normal/V-flip/UV-inset parser unchanged. */
public final class ClassicWirelessObj {
    public record Model(Map<String, ClassicDeveloperObj.Mesh> groups) {
        public Model { groups = Collections.unmodifiableMap(new LinkedHashMap<>(groups)); }
        public ClassicDeveloperObj.Mesh require(String group) throws IOException {
            var result = groups.get(group);
            if (result == null) throw new IOException("Missing wireless OBJ group " + group);
            return result;
        }
    }
    private record Line(String source, List<String> groups, boolean face) {}
    private ClassicWirelessObj() {}

    public static Model parse(Reader input) throws IOException {
        var lines = new ArrayList<Line>();
        var names = new LinkedHashSet<String>();
        List<String> active = List.of("default");
        var reader = input instanceof BufferedReader buffered ? buffered : new BufferedReader(input);
        String source;
        while ((source = reader.readLine()) != null) {
            String clean = source.split("#", 2)[0].trim();
            if (clean.isEmpty()) { lines.add(new Line(source, List.of(), false)); continue; }
            String[] words = clean.split("\\s+");
            if (words[0].equals("g") || words[0].equals("o")) {
                active = words.length == 1 ? List.of("default") : List.of(java.util.Arrays.copyOfRange(words, 1, words.length));
                continue;
            }
            boolean face = words[0].equals("f");
            lines.add(new Line(source, active, face));
            if (face) names.addAll(active);
        }
        if (names.isEmpty()) throw new IOException("Wireless OBJ contains no faces");
        var meshes = new LinkedHashMap<String, ClassicDeveloperObj.Mesh>();
        for (String name : names) {
            var filtered = new StringBuilder();
            for (Line line : lines) {
                // Preserve all declarations in order, so positive AND relative indices remain correct.
                filtered.append(!line.face || line.groups.contains(name) ? line.source : "").append('\n');
            }
            var parsed = ClassicDeveloperObj.parse(new StringReader(filtered.toString()));
            var bounds = bounds(parsed.triangles());
            meshes.put(name, new ClassicDeveloperObj.Mesh(parsed.triangles(), bounds,
                    parsed.positionCount(), parsed.uvCount(), parsed.normalCount()));
        }
        return new Model(meshes);
    }
    private static ClassicDeveloperObj.Bounds bounds(List<ClassicDeveloperObj.Triangle> triangles) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (var triangle : triangles) for (var point : List.of(triangle.a(), triangle.b(), triangle.c())) {
            minX = Math.min(minX, point.x()); minY = Math.min(minY, point.y()); minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x()); maxY = Math.max(maxY, point.y()); maxZ = Math.max(maxZ, point.z());
        }
        return new ClassicDeveloperObj.Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}

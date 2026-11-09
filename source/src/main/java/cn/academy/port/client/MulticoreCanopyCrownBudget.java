package cn.academy.port.client;

import cn.academy.port.client.FoldedMirrorMesh.Face;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Original sources/rays and font reservations are admitted before all optional ornament groups. */
public final class MulticoreCanopyCrownBudget {
    public static final int MAX_GROUP_QUADS = 576;
    static final int MAX_RAW_QUADS_PER_CONTEXT = 192;
    private MulticoreCanopyCrownBudget() {}
    public static List<Face> append(List<Face> original, List<List<Face>> groups, Vector camera, Vector look,
                                     MulticoreCanopyMesh.Budget budget, int labelQuads) {
        if (groups == null || groups.size() > 4 || labelQuads < 0 || labelQuads > 96
                || camera == null || look == null || budget == null || !camera.finite() || !MulticoreCanopyState.direction(look)) return original;
        List<Face> result = new ArrayList<>(original);
        for (List<Face> group : groups) {
            if (group == null || group.isEmpty() || group.size() > MAX_RAW_QUADS_PER_CONTEXT) continue;
            List<Face> clipped = new ArrayList<>(); boolean valid = true;
            for (Face face : group) {
                if (face == null || face.alpha() < 0 || face.alpha() > 255
                        || !face.vertices().stream().allMatch(Vector::finite)
                        || !face.uvs().stream().allMatch(uv -> Double.isFinite(uv.u()) && Double.isFinite(uv.v()))
                        || !Float.isFinite(face.material().phase())) { valid = false; break; }
                if (face.alpha() > 0) clipped.addAll(FoldedMirrorMesh.nearClip(face, camera, look));
            }
            if (!valid || clipped.size() > MAX_GROUP_QUADS
                    || budget.used() + labelQuads + clipped.size() > MulticoreCanopyMesh.MAX_MESH_QUADS) continue;
            // One atomic reservation: never leave half a crown at the limit or steal a classic label's slot.
            if (budget.reserve(clipped.size())) result.addAll(clipped);
        }
        if (result.size() == original.size()) return original;
        result.sort(Comparator.comparingInt(MulticoreCanopyMesh::layer)
                .thenComparing(Comparator.comparingDouble((Face f) -> f.a().add(f.b()).add(f.c()).add(f.d()).scale(.25).subtract(camera).length()).reversed()));
        return List.copyOf(result);
    }
}

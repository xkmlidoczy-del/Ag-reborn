package cn.academy.port.client;

import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Plate;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.ArrayList;
import java.util.List;

/** Original finite mdshield surface and a small direction arrow; all path legs remain dashed. */
public final class ScatterLocatorMesh {
    public static final int MAX_VISIBLE_CONTEXTS = 4, MAX_SCENE_VERTICES = 4096, SPAWNED_PARTICLES = 0;
    private ScatterLocatorMesh() {}
    public static List<FoldedMirrorMesh.Face> plate(ScatterLocatorState.Frame f, float phase) {
        if (!ScatterLocatorState.valid(f) || f.phase() == ScatterLocatorState.ENDED) return List.of();
        // An editing ghost replaces the same plane's display instead of drawing two overlapping planes.
        if (ScatterLocatorState.hasGhost(f)) return FoldedMirrorMesh.plate(new Plate(0, f.ghostCenter(), f.ghostNormal()),
                f.plates().isEmpty(), phase, null, 0);
        return f.plates().isEmpty() ? List.of() : FoldedMirrorMesh.plate(f.plates().getFirst(), false, phase, null, 0);
    }
    public static List<FoldedMirrorMesh.Face> path(ScatterLocatorState.Frame f, Vector eye, Vector look) {
        return ScatterLocatorState.valid(f) ? FoldedMirrorMesh.path(ScatterLocatorState.mirror(f), eye, look, 0) : List.of();
    }
    public static List<FoldedMirrorMesh.Face> arrow(ScatterLocatorState.Frame f, Vector eye, Vector look) {
        if (!ScatterLocatorState.valid(f) || f.phase() == ScatterLocatorState.ENDED || eye == null || !eye.finite()
                || !FoldedMirrorState.direction(look) || f.path().size() != 2
                || f.plates().isEmpty() && !ScatterLocatorState.hasGhost(f)) return List.of();
        Plate p = ScatterLocatorState.hasGhost(f) ? new Plate(0, f.ghostCenter(), f.ghostNormal()) : f.plates().getFirst();
        // The normal is not the outgoing direction. Only the real clipped second leg may orient this arrow.
        if (FoldedMirrorState.reflectionPlate(List.of(p), f.path().getFirst(), f.path().getLast()) == null) return List.of();
        var leg = f.path().getLast(); Vector delta = leg.end().subtract(leg.start());
        double length = Math.min(.85, delta.length()); if (length < .12) return List.of();
        Vector direction = FoldedMirrorGeometry.unit(delta).orElseThrow();
        Vector base = leg.start().add(direction.scale(.06)), tip = leg.start().add(direction.scale(length));
        Vector right = FoldedMirrorGeometry.unit(direction.cross(Math.abs(direction.y()) > .9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0))).orElseThrow();
        Vector back = tip.subtract(direction.scale(Math.min(.22, length / 3)));
        var out = new ArrayList<FoldedMirrorMesh.Face>();
        arrowLeg(out, base, tip, eye, look);
        arrowLeg(out, back.add(right.scale(.13)), tip, eye, look);
        arrowLeg(out, back.subtract(right.scale(.13)), tip, eye, look);
        return List.copyOf(out);
    }
    private static void arrowLeg(List<FoldedMirrorMesh.Face> out, Vector a, Vector b, Vector eye, Vector look) {
        Vector delta = b.subtract(a), side = FoldedMirrorGeometry.unit(delta.cross(eye.subtract(a)))
                .orElseGet(() -> FoldedMirrorGeometry.unit(delta.cross(look)).orElse(new Vector(0, 1, 0)));
        side = side.scale(.008);
        out.add(new FoldedMirrorMesh.Face(a.add(side), a.subtract(side), b.subtract(side), b.add(side),
                FoldedMirrorMesh.PREVIEW, 170, FoldedMirrorMesh.PLANNED, FoldedMirrorMesh.WHITE_TEXTURE));
    }
    public static List<FoldedMirrorMesh.Face> clipped(List<FoldedMirrorMesh.Face> faces, Vector eye, Vector look) {
        return RemoteScatterRelayMesh.clipped(faces, eye, look);
    }
    public static List<FoldedMirrorMesh.Face> ordered(List<FoldedMirrorMesh.Face> faces, Vector eye) {
        return RemoteScatterRelayMesh.ordered(faces, eye);
    }
    /** Every complete owner group fits or is skipped; reserve the existing 160-quad status panel first. */
    public static final class Budget {
        private int quads = RemoteScatterRelayMesh.MAX_HUD_QUADS;
        public boolean take(int count) {
            if (count < 0 || count > MAX_SCENE_VERTICES / 4 - quads) return false;
            quads += count; return true;
        }
        public int vertices() { return quads * 4; }
    }
}

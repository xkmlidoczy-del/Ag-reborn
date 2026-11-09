package cn.academy.port.client;

import cn.academy.port.client.FoldedMirrorMesh.Face;
import cn.academy.port.client.ZeroGapArmorState.Frame;
import cn.academy.port.client.ZeroGapArmorState.Identity;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.core.ZeroGapArmorGeometry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static cn.academy.port.client.FoldedMirrorMesh.*;
import static cn.academy.port.client.ZeroGapArmorState.*;

/** Engine-free invariants for optional finite bands; no shader, GPU or beauty acceptance. */
public final class ZeroGapArmorEnhancedMeshRegressionTest {
    private static int checks;
    private static final Identity ID = new Identity(8, new UUID(252, 58), 2, 3, 10);
    private static final Vector FEET = new Vector(0, 64, 0);

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-8; }

    private static Frame frame(Vector feet, double yaw, int physical, int phase, int live, int gap) {
        int selected = ZeroGapArmorGeometry.capture(feet, yaw, physical).activeMask();
        return new Frame(ID, 4, phase, feet, yaw, physical, selected, live, gap, phase == HIT ? 0 : -1, feet);
    }

    private static Frame liveFrame(int physical, double yaw) {
        return frame(FEET, yaw, physical, ACTIVE, ZeroGapArmorGeometry.capture(FEET, yaw, physical).activeMask(), 0);
    }

    public static void main(String[] args) {
        masksAndPhases(); originalStates(); contactsAndClock(); continuityAndClipping(); malformed(); budgets();
        System.out.println("PASS ZeroGapArmorEnhancedMeshRegressionTest: " + checks + " checks");
    }

    private static void masksAndPhases() {
        for (int physical = 0; physical < 120; physical++) for (double yaw : new double[]{0, 31, 179.9, 270}) {
            int selected = ZeroGapArmorGeometry.capture(FEET, yaw, physical).activeMask();
            for (int mask = 0; mask < 64; mask++) {
                Frame current = frame(FEET, yaw, physical, ACTIVE, mask, 0);
                boolean valid = (mask & ~selected) == 0;
                var pose = valid ? ZeroGapArmorMesh.snapshot(current) : null;
                int fills = 0;
                for (int index = 0; index < 6; index++) {
                    List<Face> actual = ZeroGapArmorEnhancedMesh.plate(current, index, -1, 19.25f);
                    if (!valid) {
                        check(actual.isEmpty(), "invalid funding mask cannot display optional geometry");
                        continue;
                    }
                    boolean live = (mask & 1 << index) != 0;
                    List<Face> original = ZeroGapArmorMesh.plate(current, index, -1, 19.25f);
                    if (live) {
                        check(actual.size() == 37 && actual.size() == original.size(), "live geometry retains exact original face count");
                        check(actual.stream().filter(f -> f.kind() != RIM).toList()
                                .equals(original.stream().filter(f -> f.kind() != RIM).toList()), "original fill, sides and contact material stay exact");
                        check(actual.stream().filter(ZeroGapArmorEnhancedMesh::isOuterRim).count() == 16, "sixteen finite outer segments");
                        check(actual.stream().filter(ZeroGapArmorEnhancedMesh::isEnhancedRim).count() == 32, "exactly two rim bands");
                        fills++;
                    } else {
                        boolean fundedSelection = (selected & 1 << index) != 0;
                        int color = fundedSelection ? ZeroGapArmorEnhancedMesh.SPENT_COLOR : ZeroGapArmorEnhancedMesh.DORMANT_COLOR;
                        check(actual.size() == 8, "nonlive active plate has exactly eight open corner segments");
                        check(actual.stream().allMatch(f -> f.kind() == GHOST && f.texture() == WHITE_TEXTURE
                                        && f.color() == color && f.alpha() <= 48 && !additive(f)),
                                "dormant and spent cannot claim a bright rim, body or additive protection");
                        var basis = ZeroGapArmorGeometry.frame(pose.plates().get(index));
                        for (Face f : actual) {
                            Vector center = f.a().add(f.b()).add(f.c()).add(f.d()).scale(.25)
                                    .subtract(pose.plates().get(index).center());
                            check(Math.abs(center.dot(basis.right())) > ZeroGapArmorGeometry.HALF_WIDTH - .12
                                    && Math.abs(center.dot(basis.up())) > ZeroGapArmorGeometry.HALF_HEIGHT - .12,
                                    "nonlive geometry is confined to corner brackets, with open edge middles");
                        }
                    }
                    for (Face f : actual) {
                        check(f.alpha() > 0 && f.alpha() <= 210, "no zero-alpha holes or unbounded vertex opacity");
                        for (Vector p : f.vertices()) check(ZeroGapArmorMesh.contains(pose.plates().get(index), p), "all optional vertices remain in authentic slab");
                        for (var uv : f.uvs()) check(Double.isFinite(uv.u()) && Double.isFinite(uv.v())
                                && uv.u() >= 0 && uv.u() <= 1 && uv.v() >= 0 && uv.v() <= 1, "finite normalized material UV");
                    }
                }
                if (valid) check(fills == Integer.bitCount(mask), "only genuinely funded shares have a textured body");
            }
        }
    }

    private static void originalStates() {
        for (int physical : new int[]{0, 1, 29, 59, 119}) for (double yaw : new double[]{0, 91, 270}) {
            for (int phase : new int[]{STARTUP, ENDED}) for (int gap = 0; gap <= 4; gap++) {
                Frame current = frame(FEET, yaw, physical, phase, 0, gap);
                for (int index = 0; index < 6; index++) for (int fade : new int[]{-1, 0, 1, 3, 4})
                    equalOriginal(current, index, fade, "startup and retirement retain exact original faces");
            }
            for (int phase : new int[]{ACTIVE, HIT}) for (int gap = 1; gap <= 4; gap++) {
                Frame current = frame(FEET, yaw, physical, phase, 0, gap);
                for (int index = 0; index < 6; index++) equalOriginal(current, index, -1, "break preserves unknown-funding original brackets");
            }
            Frame current = liveFrame(physical, yaw);
            for (int index = 0; index < 6; index++) for (int fade = 0; fade <= FADE_TICKS; fade++)
                equalOriginal(current, index, fade, "live controller fade remains exact original output");
        }
    }

    private static void equalOriginal(Frame frame, int index, int fade, String reason) {
        check(ZeroGapArmorEnhancedMesh.plate(frame, index, fade, 31.5f)
                .equals(ZeroGapArmorMesh.plate(frame, index, fade, 31.5f)), reason);
    }

    private static void contactsAndClock() {
        Frame current = liveFrame(0, 0);
        int index = Integer.numberOfTrailingZeros(current.liveMask());
        var plate = ZeroGapArmorMesh.snapshot(current).plates().get(index);
        Frame contact = new Frame(ID, 3, HIT, FEET, 0, 0, current.selectedMask(), current.liveMask(), 0, index,
                ZeroGapArmorGeometry.point(plate, .20, .25));
        for (float age : new float[]{0, .5f, 3, 5.5f, 6, Float.NaN}) {
            var original = ZeroGapArmorMesh.plate(current, index, -1, 13, contact, age);
            var enhanced = ZeroGapArmorEnhancedMesh.plate(current, index, -1, 13, contact, age);
            check(enhanced.stream().filter(f -> f.kind() != RIM).toList()
                    .equals(original.stream().filter(f -> f.kind() != RIM).toList()), "confirmed and expired contact material remain original");
            check(enhanced.stream().noneMatch(f -> f.kind() == REFRACTION), "optional plate does not invent an extra contact accent");
        }
        for (int plateIndex = 0; plateIndex < 6; plateIndex++) {
            var before = ZeroGapArmorEnhancedMesh.plate(current, plateIndex, -1, 19.5f);
            var after = ZeroGapArmorEnhancedMesh.plate(current, plateIndex, -1, 219.5f);
            check(before.equals(ZeroGapArmorEnhancedMesh.plate(current, plateIndex, -1, 19.5f)), "fixed accepted clock is deterministic");
            check(before.size() == after.size(), "material clock does not change geometry count");
            for (int j = 0; j < before.size(); j++) {
                check(before.get(j).vertices().equals(after.get(j).vertices()) && before.get(j).uvs().equals(after.get(j).uvs()),
                        "material time cannot rotate or move panes or their UV domain");
                check(before.get(j).color() == after.get(j).color() && before.get(j).alpha() == after.get(j).alpha(),
                        "material flow does not create CPU opacity holes");
            }
        }
    }

    private static void continuityAndClipping() {
        for (Vector feet : List.of(FEET, new Vector(29_999_997, 4093, -29_999_997))) {
            int selected = ZeroGapArmorGeometry.capture(feet, 31, 73).activeMask();
            Frame current = frame(feet, 31, 73, ACTIVE, selected, 0);
            int index = Integer.numberOfTrailingZeros(selected);
            var plate = ZeroGapArmorMesh.snapshot(current).plates().get(index);
            var basis = ZeroGapArmorGeometry.frame(plate);
            var actual = ZeroGapArmorEnhancedMesh.plate(current, index, -1, 8);
            for (boolean outer : new boolean[]{true, false}) {
                var rim = actual.stream().filter(ZeroGapArmorEnhancedMesh::isEnhancedRim)
                        .filter(f -> ZeroGapArmorEnhancedMesh.isOuterRim(f) == outer).toList();
                check(rim.size() == 16 && close(rim.getFirst().uvA().u(), 0) && close(rim.getLast().uvB().u(), 1), "complete perimeter UV wraps only once");
                double width = outer ? ZeroGapArmorEnhancedMesh.OUTER_RIM_WIDTH : ZeroGapArmorEnhancedMesh.INNER_RIM_WIDTH;
                check(width > 0 && width <= (outer ? .045 : .014), "physical rim support respects approved daylight crest width");
                for (int part = 0; part < rim.size(); part++) {
                    Face f = rim.get(part), next = rim.get((part + 1) % rim.size());
                    check(f.b().subtract(next.a()).length() < 1e-7 && f.c().subtract(next.d()).length() < 1e-7,
                            "mitered bands have no seam or enlarged corners");
                    if (part < rim.size() - 1) check(close(f.uvB().u(), next.uvA().u()), "perimeter U is continuous between edges and segments");
                    check(f.uvA().v() == 0 && f.uvB().v() == 0 && f.uvC().v() == 1 && f.uvD().v() == 1,
                            "transverse UV consistently runs boundary to inward edge");
                    for (Vector p : List.of(f.c(), f.d())) {
                        Vector local = p.subtract(plate.center());
                        check(Math.abs(local.dot(basis.right())) <= ZeroGapArmorGeometry.HALF_WIDTH - width + 1e-7
                                        && Math.abs(local.dot(basis.up())) <= ZeroGapArmorGeometry.HALF_HEIGHT - width + 1e-7,
                                "inner band edge stays inset on both corner axes");
                    }
                }
            }
            Vector eye = plate.center().subtract(basis.normal().scale(.015));
            Vector look = basis.normal().scale(.8).add(basis.right().scale(.6));
            int clippedCount = 0;
            for (Face f : actual) for (Face clipped : FoldedMirrorMesh.nearClip(f, eye, look)) {
                clippedCount++;
                for (Vector p : clipped.vertices()) {
                    check(ZeroGapArmorMesh.contains(plate, p), "near clipping cannot grow the shield rectangle");
                    check(p.subtract(eye).dot(look) >= NEAR_DEPTH - 1e-7, "near-eye clipping keeps every resulting vertex in front");
                }
                check(clipped.material().equals(f.material()) && clipped.color() == f.color() && clipped.kind() == f.kind(), "clipping preserves material identity");
                double lo = f.uvs().stream().mapToDouble(Uv::u).min().orElseThrow();
                double hi = f.uvs().stream().mapToDouble(Uv::u).max().orElseThrow();
                for (var uv : clipped.uvs()) check(uv.u() >= lo - 1e-8 && uv.u() <= hi + 1e-8 && uv.v() >= 0 && uv.v() <= 1,
                        "clipping interpolates rather than restarting material coordinates");
            }
            check(clippedCount > 0, "grazing live material is actually exercised");
        }
    }

    private static void malformed() {
        Frame current = liveFrame(0, 0);
        for (int index : new int[]{-1, 6, Integer.MAX_VALUE}) equalOriginal(current, index, -1, "invalid index follows baseline refusal");
        for (int fade : new int[]{Integer.MIN_VALUE, -2, 4, Integer.MAX_VALUE}) equalOriginal(current, 0, fade, "invalid or expired fade follows baseline refusal");
        for (float phase : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
            check(ZeroGapArmorEnhancedMesh.plate(current, 0, -1, phase).isEmpty(), "nonfinite material phase refused");
        check(ZeroGapArmorEnhancedMesh.plate(null, 0, -1, 0).isEmpty(), "null receipt refused without exception");
        Frame malformed = new Frame(ID, 4, ACTIVE, new Vector(Double.NaN, 64, 0), 0, 0, current.selectedMask(), current.liveMask(), 0, -1, FEET);
        check(ZeroGapArmorEnhancedMesh.plate(malformed, 0, -1, 0).isEmpty(), "nonfinite receipt refused");
        check(!ZeroGapArmorEnhancedMesh.isEnhancedRim(null) && !ZeroGapArmorEnhancedMesh.isOuterRim(null), "null material identification is safe");
        for (Face original : ZeroGapArmorMesh.plate(current, 0, -1, 0)) check(!ZeroGapArmorEnhancedMesh.isEnhancedRim(original), "original faces cannot select optional material");
        var enhanced = ZeroGapArmorEnhancedMesh.plate(current, Integer.numberOfTrailingZeros(current.liveMask()), -1, 0);
        boolean immutable = false;
        try { enhanced.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "optional mesh snapshot is immutable");
    }

    private static void budgets() {
        Frame current = liveFrame(0, 0);
        List<Face> scene = new ArrayList<>();
        for (int owner = 0; owner < 4; owner++) for (int index = 0; index < 6; index++)
            scene.addAll(ZeroGapArmorEnhancedMesh.plate(current, index, -1, 0));
        check(scene.size() == 540 && scene.size() * 4 < 3072, "four complete controllers fit the existing preclip vertex ceiling");
        Vector eye = FEET.add(new Vector(0, 1.1, 0)), look = new Vector(0, 0, 1);
        var budget = new ZeroGapArmorMesh.Budget();
        var ordered = ZeroGapArmorMesh.ordered(scene, eye, look, budget);
        check(ordered.size() * 4 <= 3072 && budget.used() == ordered.size(), "complete scene shares existing postclip cap");
        List<Face> pressure = new ArrayList<>();
        for (int copy = 0; copy < 16; copy++) pressure.addAll(scene);
        budget = new ZeroGapArmorMesh.Budget();
        ordered = ZeroGapArmorMesh.ordered(pressure, eye, look, budget);
        check(ordered.size() == 768 && budget.used() == 768, "overflow cannot exceed original hard cap");
        int layer = -1;
        for (Face face : ordered) {
            check(layer(face) >= layer, "shared draw layers remain ordered");
            layer = layer(face);
        }
        check(ZeroGapArmorMesh.MAX_VISIBLE_CONTEXTS == 4 && ZeroGapArmorMesh.SPAWNED_PARTICLES == 0,
                "existing controller and zero-particle limits remain authoritative");
    }
}

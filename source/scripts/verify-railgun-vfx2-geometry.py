#!/usr/bin/env python3
"""Compile and exercise the actual stateless Railgun overlay without game/graphics shims."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import tempfile


HARNESS = r'''
import cn.academy.port.client.ClassicEffectTimeline;
import cn.academy.port.client.vfx2.RailgunOverlayGeometry;
import cn.academy.port.client.vfx2.RailgunOverlayGeometry.*;
import java.util.*;

public final class RailgunOverlayGeometryOracle {
    static int assertions;
    static final Vec ZERO = new Vec(0, 0, 0);
    static final Vec START = new Vec(2, 3, -4);
    static final Vec END = new Vec(130, 3, -4);
    static final Vec CAMERA = new Vec(-1, 2, 1);

    static void yes(boolean value, String why) {
        assertions++;
        if (!value) throw new AssertionError(why);
    }
    static void close(double expected, double actual, double epsilon, String why) {
        yes(Double.isFinite(actual) && Math.abs(expected - actual) <= epsilon,
                why + ": " + expected + " vs " + actual);
    }
    static Vertex[] vertices(Quad quad) { return new Vertex[] {quad.a(), quad.b(), quad.c(), quad.d()}; }
    static List<Quad> normal(Quality quality, double age, double width, long seed) {
        return RailgunOverlayGeometry.build(START, END, CAMERA, age, width, seed, quality);
    }
    static void empty(List<Quad> result, String why) { yes(result.isEmpty(), why); }
    static void verify(List<Quad> result, Quality quality, Vec relativeStart, Vec direction, double length) {
        yes(result.size() <= quality.maxQuads(), "fixed per-instance quad ceiling");
        yes(result.size() * 4 <= quality.maxVertices(), "fixed per-instance vertex ceiling");
        Vec axis = direction.scale(1 / direction.length());
        int halo = 0, filament = 0, ribbons = 0;
        double ribbonStart = 0;
        for (Quad quad : result) {
            if (quad.layer() == Layer.HALO) halo++; else filament++;
            if (quad.layer() == Layer.FILAMENT && quad.a().u() == 0) {
                ribbons++;
                ribbonStart = quad.a().position().subtract(relativeStart).dot(axis);
            }
            if (quad.layer() == Layer.FILAMENT && quad.b().u() == 1) {
                double ribbonEnd = quad.b().position().subtract(relativeStart).dot(axis);
                yes(ribbonEnd - ribbonStart <= RailgunOverlayGeometry.MAX_FILAMENT_LENGTH
                        + Math.max(1e-8, length * 1e-14), "each sparse filament remains short along the axis");
            }
            double alphaLimit = quad.layer() == Layer.HALO
                    ? RailgunOverlayGeometry.HALO_ALPHA : RailgunOverlayGeometry.FILAMENT_ALPHA;
            for (Vertex vertex : vertices(quad)) {
                yes(vertex.position().finite(), "all coordinates finite");
                yes(Double.isFinite(vertex.u()) && vertex.u() >= 0 && vertex.u() <= 1, "u in full texture interval");
                yes(Double.isFinite(vertex.v()) && vertex.v() >= 0 && vertex.v() <= 1, "v in full texture interval");
                yes(Double.isFinite(vertex.alpha()) && vertex.alpha() >= 0 && vertex.alpha() <= alphaLimit,
                        "restrained layer alpha");
                Vec fromStart = vertex.position().subtract(relativeStart);
                double along = fromStart.dot(axis);
                double tolerance = Math.max(1e-8, length * 1e-14);
                yes(along >= -tolerance && along <= length + tolerance, "no range extension or cap vertices");
                Vec radial = fromStart.subtract(axis.scale(along));
                double radialLimit = quad.layer() == Layer.HALO
                        ? RailgunOverlayGeometry.MAX_HALO_HALF_WIDTH : .42 + RailgunOverlayGeometry.MAX_FILAMENT_HALF_WIDTH;
                yes(radial.length() <= radialLimit + tolerance, "no oversized halo or filament");
            }
            close(0, quad.a().v(), 0, "upper edge texture v");
            close(0, quad.b().v(), 0, "upper edge texture v at end");
            close(1, quad.c().v(), 0, "lower edge texture v at end");
            close(1, quad.d().v(), 0, "lower edge texture v");
            close(quad.a().alpha(), quad.d().alpha(), 0, "texture alone supplies transverse softness");
            close(quad.b().alpha(), quad.c().alpha(), 0, "no second cross-width alpha attenuation");
        }
        yes(halo <= quality.haloSegments(), "bounded halo segments");
        yes(filament <= quality.filaments() * RailgunOverlayGeometry.MAX_FILAMENT_SEGMENTS, "bounded sparse filament segments");
        yes(ribbons <= quality.filaments(), "no more than two distinct short ribbons");
    }

    static void invalidInputs() {
        empty(RailgunOverlayGeometry.build(null, END, CAMERA, 100, 1, 0, Quality.MEDIUM), "null start");
        empty(RailgunOverlayGeometry.build(START, null, CAMERA, 100, 1, 0, Quality.MEDIUM), "null end");
        empty(RailgunOverlayGeometry.build(START, END, null, 100, 1, 0, Quality.MEDIUM), "null camera");
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            for (Vec point : new Vec[] {new Vec(bad, 0, 0), new Vec(0, bad, 0), new Vec(0, 0, bad)}) {
                empty(RailgunOverlayGeometry.build(point, END, CAMERA, 100, 1, 0, Quality.MEDIUM), "nonfinite start");
                empty(RailgunOverlayGeometry.build(START, point, CAMERA, 100, 1, 0, Quality.MEDIUM), "nonfinite end");
                empty(RailgunOverlayGeometry.build(START, END, point, 100, 1, 0, Quality.MEDIUM), "nonfinite camera");
                empty(RailgunOverlayGeometry.buildAlong(START, point, 10, CAMERA, 100, 1, 0, Quality.MEDIUM), "nonfinite direction");
            }
        }
        for (double age : new double[] {-1, -Double.MIN_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            empty(normal(Quality.MEDIUM, age, 1, 0), "invalid age");
        }
        for (double width : new double[] {-1, 0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            empty(normal(Quality.MEDIUM, 100, width, 0), "invalid width");
        }
        for (double length : new double[] {-1, 0, 1e-7, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            empty(RailgunOverlayGeometry.buildAlong(START, new Vec(1, 0, 0), length, CAMERA,
                    100, 1, 0, Quality.MEDIUM), "invalid/negative length");
        }
        empty(RailgunOverlayGeometry.buildAlong(START, null, 10, CAMERA, 100, 1, 0, Quality.MEDIUM), "null direction");
        empty(RailgunOverlayGeometry.buildAlong(START, ZERO, 10, CAMERA, 100, 1, 0, Quality.MEDIUM), "zero direction");
        empty(RailgunOverlayGeometry.build(START, START, CAMERA, 100, 1, 0, Quality.MEDIUM), "zero segment");
        empty(normal(null, 100, 1, 0), "null quality");
        Vec huge = new Vec(Double.MAX_VALUE, 0, 0), opposite = new Vec(-Double.MAX_VALUE, 0, 0);
        empty(RailgunOverlayGeometry.build(huge, opposite, ZERO, 100, 1, 0, Quality.MEDIUM), "overflowing delta fails closed");
        empty(RailgunOverlayGeometry.build(huge, huge, opposite, 100, 1, 0, Quality.MEDIUM), "overflowing camera subtraction fails closed");
    }

    static void lifetimeAndOff() {
        close(2500, RailgunOverlayGeometry.LIFETIME_MILLIS, 0, "classic lifetime unchanged");
        close(ClassicEffectTimeline.RAILGUN_LIFETIME_MS, RailgunOverlayGeometry.LIFETIME_MILLIS, 0,
                "overlay lifetime matches production classic timeline");
        empty(normal(Quality.OFF, 500, 1, 12), "OFF emits no geometry");
        empty(normal(Quality.LOW, 2500, 1, 12), "no independent lifetime at classic expiry");
        empty(normal(Quality.MEDIUM, 2501, 1, 12), "no lingering filament");
        yes(!normal(Quality.LOW, 0, 1, 12).isEmpty(), "caller can sample age zero with supplied visible endpoints");
        List<Quad> early = normal(Quality.MEDIUM, 500, 1, 12);
        List<Quad> late = normal(Quality.MEDIUM, 2000, 1, 12);
        for (Quad q : early) if (q.layer() == Layer.HALO) close(.16, q.a().alpha(), 1e-15, "early restrained halo alpha");
        for (Quad q : late) if (q.layer() == Layer.HALO) close(.08, q.a().alpha(), 1e-15, "classic final-second fade exactly once");
        for (long age : new long[] {0, 149, 150, 1499, 1500, 1501, 1700, 2000, 2499}) {
            for (Quad q : normal(Quality.LOW, age, 1, 12)) {
                close(RailgunOverlayGeometry.HALO_ALPHA * ClassicEffectTimeline.beamAlpha(age),
                        q.a().alpha(), 1e-15, "overlay inherits exact classic alpha envelope");
            }
        }
        List<Quad> last = normal(Quality.MEDIUM, 2499.999, 1, 12);
        for (Quad q : last) for (Vertex v : vertices(q)) yes(v.alpha() <= .000000161, "all layers fade before expiry");
    }

    static void budgetsAndUv() {
        close(12, Quality.LOW.maxQuads(), 0, "LOW quad limit");
        close(36, Quality.MEDIUM.maxQuads(), 0, "MEDIUM quad limit");
        close(48, Quality.LOW.maxVertices(), 0, "LOW vertex limit");
        close(144, Quality.MEDIUM.maxVertices(), 0, "MEDIUM vertex limit");
        for (Quality quality : Quality.values()) {
            for (double length : new double[] {1e-6, .01, 1, 16, 128, 256, 1e6, 1e12, Double.MAX_VALUE}) {
                List<Quad> result = RailgunOverlayGeometry.buildAlong(new Vec(0, 3, 0), new Vec(1, 0, 0),
                        length, ZERO, 450, 1, 8675309L, quality);
                verify(result, quality, new Vec(0, 3, 0), new Vec(1, 0, 0), length);
                if (quality == Quality.OFF) empty(result, "OFF even for huge length");
                else yes(!result.isEmpty(), "finite positive supplied length supported");
                List<Quad> halo = result.stream().filter(q -> q.layer() == Layer.HALO).toList();
                if (!halo.isEmpty()) {
                    close(0, halo.getFirst().a().u(), 0, "texture begins at start");
                    close(1, halo.getLast().b().u(), 0, "texture ends at actual supplied endpoint");
                    for (int i = 1; i < halo.size(); i++) {
                        Quad before = halo.get(i - 1), after = halo.get(i);
                        yes(before.b().position().equals(after.a().position()), "continuous upper strip positions");
                        yes(before.c().position().equals(after.d().position()), "continuous lower strip positions");
                        close(before.b().u(), after.a().u(), 0, "continuous u across segments");
                    }
                }
                if (quality == Quality.LOW) yes(result.stream().noneMatch(q -> q.layer() == Layer.FILAMENT), "LOW omits filaments");
            }
        }
        List<Quad> medium = normal(Quality.MEDIUM, 350, 1, 123);
        yes(medium.stream().anyMatch(q -> q.layer() == Layer.FILAMENT), "MEDIUM includes subtle short ribbons");
        try { medium.add(medium.getFirst()); throw new AssertionError("result must be immutable"); }
        catch (UnsupportedOperationException expected) { assertions++; }
    }

    static void basisAndFacing() {
        Vec[] directions = {new Vec(1, 0, 0), new Vec(0, 1, 0), new Vec(0, 0, 1),
                new Vec(1, 1, 1), new Vec(1, 1e-11, 1e-11), new Vec(-1, 1e-9, 0)};
        for (Vec direction : directions) {
            Vec axis = direction.scale(1 / direction.length());
            for (Vec start : new Vec[] {ZERO, axis.scale(-64), axis.scale(3), new Vec(2, 4, 8), new Vec(0, 1e-10, 0)}) {
                List<Quad> result = RailgunOverlayGeometry.buildAlong(start, direction, 128, ZERO,
                        375, 1, Long.MIN_VALUE, Quality.MEDIUM);
                yes(!result.isEmpty(), "finite on-axis and near-axis camera basis");
                verify(result, Quality.MEDIUM, start, direction, 128);
                boolean nonzeroWidth = false;
                for (Quad quad : result) if (quad.layer() == Layer.HALO) {
                    Vec cross = quad.b().position().subtract(quad.c().position());
                    close(0, cross.dot(axis), 1e-12, "strip width perpendicular to beam");
                    if (cross.length() > 1e-6) nonzeroWidth = true;
                }
                yes(nonzeroWidth, "fallback basis does not collapse all widths");
            }
        }
        Vec start = new Vec(2, 4, 8), direction = new Vec(1, 2, 3);
        Vec axis = direction.scale(1 / direction.length());
        Vec midpoint = start.add(axis.scale(64));
        List<Quad> result = RailgunOverlayGeometry.buildAlong(start, direction, 128, ZERO,
                375, 1, 7, Quality.LOW);
        for (Quad q : result) {
            Vec width = q.b().position().subtract(q.c().position());
            close(0, width.dot(midpoint), 1e-12, "billboard width perpendicular to midpoint view");
        }
    }

    static void translationPrecision() {
        Vec shift = new Vec(29999984, 4096, -29999984);
        Vec start = new Vec(2.125, 3.5, -4.25), end = new Vec(130.375, 8.5, -2.25), camera = new Vec(-1.125, 2.25, 1.5);
        for (Quality quality : new Quality[] {Quality.LOW, Quality.MEDIUM}) {
            List<Quad> near = RailgunOverlayGeometry.build(start, end, camera, 730.5, .37, 8923, quality);
            List<Quad> border = RailgunOverlayGeometry.build(start.add(shift), end.add(shift), camera.add(shift),
                    730.5, .37, 8923, quality);
            yes(near.equals(border), "exact translation invariance for representable world-border coordinates");
            Vec direction = new Vec(1, .125, .25);
            near = RailgunOverlayGeometry.buildAlong(start, direction, 213.37, camera, 730.5, .37, 8923, quality);
            border = RailgunOverlayGeometry.buildAlong(start.add(shift), direction, 213.37, camera.add(shift),
                    730.5, .37, 8923, quality);
            yes(near.equals(border), "local along-axis geometry does not inherit world-coordinate float loss");
            double widest = near.stream().filter(q -> q.layer() == Layer.HALO)
                    .mapToDouble(q -> q.b().position().subtract(q.c().position()).length()).max().orElse(0);
            yes(widest > .8 && widest < 1, "thin halo retained after far-world translation");
        }
    }

    static void nearCameraAndWidthCaps() {
        Vec start = new Vec(.02, .01, .005), direction = new Vec(1, 0, 0);
        List<Quad> result = RailgunOverlayGeometry.buildAlong(start, direction, 128, ZERO, 300, 1000, 31, Quality.MEDIUM);
        verify(result, Quality.MEDIUM, start, direction, 128);
        Quad first = result.getFirst();
        close(0, first.a().position().subtract(first.d().position()).dot(direction), 1e-12, "near-camera bound preserves start");
        double halfWidth = first.a().position().subtract(first.d().position()).length() * .5;
        yes(halfWidth <= start.length() * RailgunOverlayGeometry.MAX_ANGULAR_HALF_WIDTH + 1e-12,
                "no oversized first-person wedge at camera");
        double maxWidth = result.stream().filter(q -> q.layer() == Layer.HALO)
                .mapToDouble(q -> q.b().position().subtract(q.c().position()).length() * .5).max().orElse(0);
        yes(maxWidth <= 1.6 + 1e-12, "halo has an absolute cap at arbitrary widthScale");
    }

    static void pausedDeterminism() {
        List<Quad> paused = normal(Quality.MEDIUM, 711.25, .87, 411);
        for (int frame = 0; frame < 240; frame++) {
            normal(Quality.MEDIUM, (frame * 13) % 2500, 1, frame);
            yes(paused.equals(normal(Quality.MEDIUM, 711.25, .87, 411)), "same paused age unaffected by intervening frames");
        }
        yes(!paused.equals(normal(Quality.MEDIUM, 712.25, .87, 411)), "flow follows supplied age");
        yes(!paused.equals(normal(Quality.MEDIUM, 711.25, .87, 412)), "stable beam seed differentiates flow");
        yes(normal(Quality.MEDIUM, 711.25, .87, Long.MIN_VALUE)
                .equals(normal(Quality.MEDIUM, 711.25, .87, Long.MIN_VALUE)), "negative seed deterministic");
    }

    static void randomizedBounds() {
        Random random = new Random(0xACA2026L);
        for (int run = 0; run < 1000; run++) {
            Vec start = new Vec(random.nextDouble() * 128 - 64, random.nextDouble() * 64 - 32, random.nextDouble() * 128 - 64);
            Vec direction = new Vec(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1);
            double length = .001 + random.nextDouble() * 255.999;
            double age = random.nextDouble() * 2500;
            double width = .001 + random.nextDouble() * 2;
            Quality quality = run % 2 == 0 ? Quality.LOW : Quality.MEDIUM;
            List<Quad> result = RailgunOverlayGeometry.buildAlong(start, direction, length, ZERO, age, width,
                    random.nextLong(), quality);
            yes(!result.isEmpty(), "valid randomized beam sampled");
            verify(result, quality, start, direction, length);
        }
    }

    public static void main(String[] args) {
        invalidInputs(); lifetimeAndOff(); budgetsAndUv(); basisAndFacing();
        translationPrecision(); nearCameraAndWidthCaps(); pausedDeterminism(); randomizedBounds();
        System.out.println("PASS|8|" + assertions);
    }
}
'''


def toolchain(root):
    candidates = []
    if os.environ.get("JAVA_HOME"):
        candidates.append(Path(os.environ["JAVA_HOME"]))
    if shutil.which("javac"):
        candidates.append(Path(shutil.which("javac")).resolve().parent.parent)
    for parent in [root, *root.parents]:
        candidates.extend(sorted((parent / "aca-tools").glob("jdk*")))
        candidates.extend(sorted((parent / ".tools").glob("jdk*")))
    for home in candidates:
        if (home / "bin/javac").is_file() and (home / "bin/java").is_file():
            return home
    raise RuntimeError("Set JAVA_HOME to a JDK 21 installation")


def run(command):
    result = subprocess.run([str(value) for value in command], capture_output=True, text=True, timeout=60)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout.strip()


def main():
    root = Path(__file__).resolve().parents[1]
    source = root / "src/main/java/cn/academy/port/client/vfx2/RailgunOverlayGeometry.java"
    timeline = root / "src/main/java/cn/academy/port/client/ClassicEffectTimeline.java"
    java = toolchain(root)
    with tempfile.TemporaryDirectory(prefix="railgun-vfx2-geometry-") as temporary:
        work = Path(temporary)
        harness = work / "RailgunOverlayGeometryOracle.java"
        harness.write_text(HARNESS, encoding="utf-8")
        run([java / "bin/javac", "--release", "21", "-encoding", "UTF-8", "-d", work, source, timeline, harness])
        result = run([java / "bin/java", "-cp", work, "RailgunOverlayGeometryOracle"]).split("|")
    if result[0] != "PASS":
        raise RuntimeError(str(result))
    print(json.dumps({
        "result": "PASS", "groups": int(result[1]), "assertions": int(result[2]),
        "productionSource": str(source.relative_to(root)),
        "sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "maxQuadsPerBeam": {"OFF": 0, "LOW": 12, "MEDIUM": 36},
        "maxVerticesPerBeam": {"OFF": 0, "LOW": 48, "MEDIUM": 144},
        "nativeMinecraftRuntime": False,
        "visualAcceptanceClaimed": False,
    }, indent=2))


if __name__ == "__main__":
    main()

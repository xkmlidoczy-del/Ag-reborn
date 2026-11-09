#!/usr/bin/env python3
"""Exercise actual renderer emitters and pure meshes without a Minecraft/GPU runtime.

The emitter methods and particle loop are extracted unchanged from production source.
Only Minecraft/JOML output and Vec3 plumbing are replaced with recording adapters.
An early-float mutation must fail, proving the far-coordinate oracle detects the bug.
"""
from pathlib import Path
import hashlib
import json
import os
import os
import re
import shutil
import subprocess
import tempfile


HARNESS = r'''
import cn.academy.port.client.DisplacedProjectionMesh;
import cn.academy.port.client.SafeLandingProbeMesh;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.*;

public class DisplacedProjectionPrecisionOracle {
    static int assertions;
    static final double TOLERANCE = 4e-6;
    static final Matrix4f MATRIX = new Matrix4f();
    static final BufferSource buffers = new BufferSource();
    static final Cosmetic success = new Cosmetic();
    static class Matrix4f {}
    static class Cosmetic { int age; }
    static class Material { static final Object CORNERS = new Object(); }
    record Frame(Point source, Point arrival) {}
    static final class Vec3 {
        final double x, y, z;
        Vec3(double x, double y, double z) { this.x=x; this.y=y; this.z=z; }
        Vec3 add(Vec3 b) { return new Vec3(x+b.x, y+b.y, z+b.z); }
        Vec3 subtract(Vec3 b) { return new Vec3(x-b.x, y-b.y, z-b.z); }
        Vec3 scale(double s) { return new Vec3(x*s, y*s, z*s); }
        Vec3 cross(Vec3 b) { return new Vec3(y*b.z-z*b.y, z*b.x-x*b.z, x*b.y-y*b.x); }
        double lengthSqr() { return x*x+y*y+z*z; }
        Vec3 normalize() { double n=Math.sqrt(lengthSqr()); return n<1e-4 ? new Vec3(0,0,0) : scale(1/n); }
    }
    static final class Vertex {
        final float x, y, z;
        float u, v, nx, ny, nz;
        int r, g, b, alpha;
        Vertex(float x, float y, float z) { this.x=x; this.y=y; this.z=z; }
    }
    static final class VertexConsumer {
        final List<Vertex> values = new ArrayList<>();
        VertexConsumer addVertex(Matrix4f m, float x, float y, float z) {
            yes(m == MATRIX, "the unchanged pose matrix reaches every vertex");
            values.add(new Vertex(x,y,z)); return this;
        }
        Vertex last() { return values.getLast(); }
        VertexConsumer setColor(int r,int g,int b,int a) { var p=last();p.r=r;p.g=g;p.b=b;p.alpha=a;return this; }
        VertexConsumer setNormal(float x,float y,float z) { var p=last();p.nx=x;p.ny=y;p.nz=z;return this; }
        VertexConsumer setUv(float u,float v) { var p=last();p.u=u;p.v=v;return this; }
    }
    static final class BufferSource {
        VertexConsumer current;
        VertexConsumer getBuffer(Object ignored) { return current; }
    }
    /*EMITTERS*/
    static void particles(Frame f, Vec3 camera, Vec3 horizontal, Vec3 vertical, int alpha, VertexConsumer out) {
        Matrix4f matrix = MATRIX;
        /*PARTICLES*/
    }
    static void yes(boolean value, String why) { assertions++; if (!value) throw new AssertionError(why); }
    static void close(double a,double b,String why) { yes(Double.isFinite(a) && Double.isFinite(b) && Math.abs(a-b)<=TOLERANCE,why+": "+a+" versus "+b); }
    static Point moved(Point p,Vec3 t) { return point(vec(p).add(t)); }
    static VertexConsumer render(Point source,Point arrival,Vec3 camera,int age,int kind) {
        var out=new VertexConsumer(); buffers.current=out; success.age=age;
        int alpha=DisplacedProjectionMesh.alpha(age);
        if(kind==0) {
            corners(MATRIX,DisplacedProjectionMesh.bounds(source),camera,alpha);
            corners(MATRIX,DisplacedProjectionMesh.bounds(arrival),camera,alpha);
        } else if(kind==1) {
            for(Point endpoint:List.of(source,arrival)) for(var line:DisplacedProjectionMesh.corners(endpoint))
                stroke(out,MATRIX,line.from(),line.to(),camera,alpha);
        } else {
            particles(new Frame(source,arrival),camera,new Vec3(1,0,0),new Vec3(0,1,0),alpha,out);
        }
        return out;
    }
    static void compare(VertexConsumer expected,VertexConsumer actual,String why) {
        yes(expected.values.size()==actual.values.size(),why+" vertex count");
        for(int i=0;i<expected.values.size();i++) {
            Vertex a=expected.values.get(i),b=actual.values.get(i);
            close(a.x,b.x,why+" x");close(a.y,b.y,why+" y");close(a.z,b.z,why+" z");
            close(a.nx,b.nx,why+" normal x");close(a.ny,b.ny,why+" normal y");close(a.nz,b.nz,why+" normal z");
            yes(a.u==b.u && a.v==b.v,why+" UV preserved");
            yes(a.r==b.r && a.g==b.g && a.b==b.b && a.alpha==b.alpha,why+" color/alpha preserved");
        }
    }
    static void translationInvariance() {
        Point source=new Point(.25,65.125,-.5),arrival=new Point(4.5,65.375,-1.25);
        Vec3 camera=new Vec3(-2.25,66.625,3.25);
        Vec3[] shifts={new Vec3(0,0,0),new Vec3(8192,2048,-4096),new Vec3(-1_000_000,-8192,1_000_000),
            new Vec3(29_999_900,0,-29_999_900),new Vec3(-29_999_900,0,29_999_900)};
        for(int age=0;age<DisplacedProjectionMesh.SUCCESS_TICKS;age++) for(int kind=0;kind<3;kind++) {
            var reference=render(source,arrival,camera,age,kind);
            yes(reference.values.size()==new int[]{96,192,128}[kind],"complete endpoint corner/stroke/particle emission");
            for(var shift:shifts) compare(reference,render(moved(source,shift),moved(arrival,shift),camera.add(shift),age,kind),
                "translation invariance age="+age+" emitter="+kind+" shift="+shift.x);
        }
    }
    static void smallSegmentsAndExactlyOnce() {
        for(double base:new double[]{0,8192,-1_000_000,29_999_900,-29_999_900}) {
            Vec3 camera=new Vec3(base,65,-base);
            var out=new VertexConsumer();
            Point p=new Point(base+1.0/4096,65+1.0/2048,-base-1.0/8192);
            vertex(out,MATRIX,p,camera,.25f,.75f,73);
            Vertex v=out.last();
            close(1.0/4096,v.x,"tiny x survives and camera is subtracted once");
            close(1.0/2048,v.y,"tiny y survives and camera is subtracted once");
            close(-1.0/8192,v.z,"tiny z survives and camera is subtracted once");
            yes(v.u==.25f && v.v==.75f && v.alpha==73,"direct vertex attributes preserved");
            out=new VertexConsumer();
            Point from=new Point(base+.25,65.5,-base+.125),to=new Point(base+.28125,65.5,-base+.125);
            stroke(out,MATRIX,from,to,camera,73);
            yes(out.values.size()==4,"small stroke remains a quad at all coordinates");
            close(.03125,out.values.get(2).x-out.values.get(1).x,"small stroke length preserved");
            close(.018,Math.sqrt(Math.pow(out.values.get(1).y-out.values.get(0).y,2)
                +Math.pow(out.values.get(1).z-out.values.get(0).z,2)),"stroke width preserved");
        }
    }
    static void degenerateCameraAxis() {
        for(double base:new double[]{0,8192,-1_000_000,29_999_900,-29_999_900}) {
            Point from=new Point(base+.25,65.5,-base+.125),to=new Point(base+.28125,65.5,-base+.125);
            for(Vec3 camera:List.of(new Vec3(base-2,65.5,-base+.125),vec(from),new Vec3(base-2,65.5+1e-8,-base+.125))) {
                var out=new VertexConsumer();stroke(out,MATRIX,from,to,camera,73);
                yes(out.values.isEmpty(),"camera-axis degeneracy retains no-quad guard");
            }
            var out=new VertexConsumer();stroke(out,MATRIX,from,from,new Vec3(base-2,66,-base+1),73);
            yes(out.values.isEmpty(),"zero-length stroke emits no invalid vertices");
        }
    }
    public static void main(String[] args) {
        translationInvariance();smallSegmentsAndExactlyOnce();degenerateCameraAxis();
        System.out.println("PASS|"+assertions);
    }
}
'''


def block(source, marker):
    start = source.index(marker)
    opening = source.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def toolchain(root):
    candidates = []
    if os.environ.get('JAVA_HOME'):
        candidates.append(Path(os.environ['JAVA_HOME']))
    if shutil.which('javac'):
        candidates.append(Path(shutil.which('javac')).resolve().parent.parent)
    for parent in [root, *root.parents]:
        candidates.extend(sorted((parent / 'aca-tools').glob('jdk*')))
        candidates.extend(sorted((parent / '.tools').glob('jdk*')))
    for home in candidates:
        if (home / 'bin/javac').is_file() and (home / 'bin/java').is_file():
            return home
    raise RuntimeError('Set JAVA_HOME to a JDK 21 installation')


def run(command):
    result = subprocess.run([str(value) for value in command], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout.strip()


def main():
    root = Path(__file__).resolve().parents[1]
    java_root = root / 'src/main/java/cn/academy/port'
    effects = java_root / 'client/DisplacedProjectionEffects.java'
    source = effects.read_text()
    render = block(source, '@SubscribeEvent public static void render(')
    # Emitted vertices own the single subtraction. No second matrix translation is allowed.
    if re.search(r'\.(?:translate|translation|setTranslation)\s*\(', render):
        raise AssertionError('render must not also translate the pose/model-view matrix')
    for call in ['corners(matrix, departure, camera, alpha)', 'corners(matrix, arrival, camera, alpha)',
                 'stroke(out, matrix, segment.from(), segment.to(), camera, alpha)']:
        if call not in render:
            raise AssertionError('missing production camera propagation: ' + call)
    methods = '\n'.join(block(source, 'private static ' + marker) for marker in [
        'void corners(', 'void stroke(', 'void vertex(', 'Point point(', 'Vec3 vec('])
    particles = block(render, 'for (var p : DisplacedProjectionMesh.particles(')
    harness = HARNESS.replace('/*EMITTERS*/', methods).replace('/*PARTICLES*/', particles)
    pure = [java_root / name for name in [
        'core/SkillTuning.java', 'highpower/core/MobilityScale.java', 'highpower/core/CombatAmounts.java', 'highpower/core/ContinuousHighPowerGrowth.java', 'highpower/core/SafeLandingProbeRules.java',
        'highpower/core/SafeLandingProbeGeometry.java', 'client/ClassicTeleporterTimeline.java',
        'client/SafeLandingProbeMesh.java', 'client/DisplacedProjectionMesh.java']]
    java = toolchain(root)
    with tempfile.TemporaryDirectory(prefix='new311-precision-') as temporary:
        work = Path(temporary)
        path = work / 'DisplacedProjectionPrecisionOracle.java'
        path.write_text(harness)
        run([java / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-d', work, *pure, path])
        runtime_cp = os.pathsep.join(map(str, (work, root / 'src/main/resources')))
        result = run([java / 'bin/java', '-cp', runtime_cp, path.stem]).split('|')
        if result[0] != 'PASS':
            raise RuntimeError(str(result))
        assertions = int(result[1])
        # Reproduce the old float-world-coordinate subtraction, without altering production.
        mutated, mutations = re.subn(r'\(float\)\(([ab]\.\w|p\.\w\(\)) - camera\.(\w)\)',
                                    r'((float)\1 - (float)camera.\2)', harness)
        if mutations != 9:
            raise AssertionError('expected nine tested position-coordinate narrowings')
        path.write_text(mutated)
        run([java / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-cp', work, '-d', work, path])
        old = subprocess.run([str(java / 'bin/java'), '-cp', runtime_cp, path.stem], capture_output=True, text=True)
        if old.returncode == 0 or 'translation invariance' not in old.stderr:
            raise AssertionError('oracle must reject early-float coordinates for translation precision')
    print(json.dumps({'result': 'PASS', 'assertions': assertions,
                      'coordinates': ['origin', 'mid', 'far', 'positive-world-border', 'negative-world-border'],
                      'emitters': ['corner-lines', 'atlas-strokes', 'particle-quads'],
                      'earlyFloatMutationRejected': True, 'doubleCameraSubtractionGuard': True,
                      'cameraAxisDegeneracy': 'PASS', 'smallSegments': 'PASS',
                      'productionSource': str(effects.relative_to(root)),
                      'sha256': hashlib.sha256(effects.read_bytes()).hexdigest(),
                      'nativeMinecraftRuntime': False}, indent=2))


if __name__ == '__main__':
    main()

package cn.academy.port.client;

import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.core.ParticleLaminateGeometry;
import cn.academy.port.highpower.core.ParticleLaminateGeometry.Panel;
import cn.academy.port.client.FoldedMirrorMesh.Face;
import cn.academy.port.client.FoldedMirrorMesh.Material;
import cn.academy.port.client.FoldedMirrorMesh.Uv;
import static cn.academy.port.client.FoldedMirrorMesh.*;
import java.util.ArrayList;
import java.util.List;

/** NEW241 uses two real .04m panel slabs and three exact narrow segments. The gaps contain no mesh. */
public final class ParticleLaminateMesh {
    public static final int MAX_VISIBLE_CONTEXTS = 4, MAX_MESH_QUADS = 1536, SPAWNED_PARTICLES = 0;
    public static final double CULL_DISTANCE = 64, PREVIEW_DASH_SPACING = 1, PREVIEW_DASH_LENGTH = .23;
    private static final double EPS = 1e-8;
    private ParticleLaminateMesh() {}
    private static Vector lerp(Vector a, Vector b, double t) { return a.add(b.subtract(a).scale(t)); }
    private static Vector corner(Panel p, double x, double y, double depth) {
        var basis = ParticleLaminateGeometry.frame(p);
        return p.center().add(basis.right().scale(x)).add(basis.up().scale(y)).add(p.normal().scale(depth));
    }
    /** Original mdshield pixels remain unchanged; their alpha is confined to the actual rectangular plane. */
    public static List<Face> panel(Panel p, boolean ghost, boolean warning, float phase) {
        if (p == null) return List.of();
        List<Face> out = new ArrayList<>(69);
        double[] xs = {-.6, .6, .6, -.6}, ys = {-1, -1, 1, 1};
        if (!ghost) {
            out.add(new Face(corner(p,-.6,-1,0),corner(p,.6,-1,0),corner(p,.6,1,0),corner(p,-.6,1,0),
                    0xFFFFFF, 210, FILL, SHIELD_TEXTURE, new Uv(0,1),new Uv(1,1),new Uv(1,0),new Uv(0,0),Material.phase(phase)));
        }
        for (int edge = 0; edge < 4; edge++) {
            int next = (edge + 1) % 4;
            Vector a = corner(p, xs[edge], ys[edge], 0), b = corner(p, xs[next], ys[next], 0);
            Vector inward = p.center().subtract(a.add(b).scale(.5));
            inward = FoldedMirrorGeometry.unit(inward).orElseThrow();
            if (!ghost) out.add(new Face(a.add(p.normal().scale(-.02)), b.add(p.normal().scale(-.02)),
                    b.add(p.normal().scale(.02)), a.add(p.normal().scale(.02)), EDGE, 72, SIDE, WHITE_TEXTURE));
            for (int part = 0; part < 8; part++) {
                if (ghost && part % 2 != 0) continue;
                Vector from = lerp(a,b,part/8d), to = lerp(a,b,(part+1)/8d);
                if (!ghost) out.add(new Face(from, to, to.add(inward.scale(.026)), from.add(inward.scale(.026)),
                        GREEN, warning ? 82 : 32, RIM, WHITE_TEXTURE));
                out.add(new Face(from, to, to.add(inward.scale(.008)), from.add(inward.scale(.008)),
                        ghost ? PREVIEW : warning ? CORE : GREEN, ghost ? 136 : warning ? 225 : 105,
                        ghost ? GHOST : RIM, WHITE_TEXTURE));
            }
        }
        Material material = Material.phase(phase);
        return out.stream().map(f -> f.withMaterial(material)).toList();
    }
    /** The short incoming normal witness exposes orientation even when a thin ghost is nearly edge-on. */
    public static List<Face> orientation(Panel p, Vector camera, Vector look) {
        if (p == null || !validCamera(camera,look)) return List.of();
        List<Face> out = new ArrayList<>(3);
        Vector a = p.center().subtract(p.normal().scale(.72));
        Vector side = side(a,p.center(),camera,look);
        if (side == null) return List.of();
        for (int i=0;i<3;i++) ray(out,lerp(a,p.center(),i/3d),lerp(a,p.center(),(i+.42)/3d),side,camera,look,
                .006,.006,PREVIEW,125,PLANNED,WHITE_TEXTURE);
        return List.copyOf(out);
    }
    /** Solid cores exist for two ticks after an authenticated pulse; the later halo is cosmetic fade only. */
    public static List<Face> path(ParticleLaminateState.Frame frame, Vector camera, Vector look, float age) {
        if (!ParticleLaminateState.valid(frame) || !validCamera(camera,look) || frame.phase() == ParticleLaminateState.ENDED) return List.of();
        boolean pulse = frame.phase() == ParticleLaminateState.FIRE;
        if (pulse && (age < 0 || age >= ParticleLaminateState.FLASH_TICKS)) return List.of();
        List<Face> out = new ArrayList<>();
        for (var segment : frame.path()) {
            Vector from = segment.start(), to = segment.end(), delta = to.subtract(from);
            double length = delta.length(); if (length < EPS) continue; Vector side = side(from,to,camera,look); if (side == null) continue;
            if (!pulse) {
                int count = (int)Math.ceil(length / PREVIEW_DASH_SPACING);
                for (int i = 0; i < count; i++) ray(out,lerp(from,to,i*PREVIEW_DASH_SPACING/length),
                        lerp(from,to,Math.min(1,(i*PREVIEW_DASH_SPACING+PREVIEW_DASH_LENGTH)/length)),side,camera,look,
                        .006,.006,PREVIEW,110,PLANNED,WHITE_TEXTURE);
                continue;
            }
            int alpha = (int)(245 * (1 - age / ParticleLaminateState.FLASH_TICKS));
            double blend = Math.min(.24 / length,.25); double[] cut = {0,blend,1-blend,1};
            for (int part=0;part<3;part++) {
                Vector a=lerp(from,to,cut[part]), b=lerp(from,to,cut[part+1]);
                ray(out,a,b,side,camera,look,HALO_RADIUS,HALO_RADIUS,0xFFFFFF,
                        alpha*(age < ParticleLaminateState.LIVE_FLASH_TICKS ? 108 : 48)/245,BEAM_HALO,part);
                if (age < ParticleLaminateState.LIVE_FLASH_TICKS) {
                    tube(out,a,b,camera,look,CORE_RADIUS,CORE_RADIUS,CORE,alpha*230/245,BEAM_CORE,
                            part==0,part==2,cut[part]*length,cut[part+1]*length);
                    tube(out,a,b,camera,look,ParticleLaminateGeometry.RADIUS,ParticleLaminateGeometry.RADIUS,
                            GREEN,alpha*45/245,BEAM_EDGE,part==0,part==2,cut[part]*length,cut[part+1]*length);
                }
            }
            if (age < ParticleLaminateState.LIVE_FLASH_TICKS
                    && (segment.status()==ParticleLaminateState.DAMAGED || segment.status()==ParticleLaminateState.ABSORBED))
                hitGlint(out,segment,camera,look,alpha);
        }
        Material material = Material.phase(age);
        return out.stream().map(f -> f.withMaterial(material)).toList();
    }
    /** Two tiny strokes at this segment's native first-body endpoint, never a speculative target explosion. */
    private static void hitGlint(List<Face> out, ParticleLaminateState.Segment s, Vector camera, Vector look, int alpha) {
        Vector axis = FoldedMirrorGeometry.unit(s.end().subtract(s.start())).orElseThrow();
        Vector right = FoldedMirrorGeometry.unit(axis.cross(Math.abs(axis.y())>.9?new Vector(1,0,0):new Vector(0,1,0))).orElseThrow();
        Vector up = axis.cross(right);
        for (Vector direction : List.of(right,up)) {
            Vector a=s.end().subtract(direction.scale(.06)),b=s.end().add(direction.scale(.06));
            Vector side=side(a,b,camera,look);
            if(side!=null)ray(out,a,b,side,camera,look,.004,.004,
                    s.status()==ParticleLaminateState.DAMAGED?CORE:GREEN,alpha,REFRACTION,WHITE_TEXTURE);
        }
    }
    /** Includes the full fixed corridor and both endpoints, regardless of where its owner walked. */
    public static double distanceSquared(ParticleLaminateState.Frame frame, Vector camera) {
        if (!ParticleLaminateState.valid(frame) || camera == null || !camera.finite()) return Double.POSITIVE_INFINITY;
        double distance = square(frame.ownerFeet().subtract(camera).length());
        for (Panel p : frame.panels()) distance = Math.min(distance,square(p.center().subtract(camera).length()));
        Panel ghost=ParticleLaminateState.ghost(frame);
        if (ghost!=null) distance=Math.min(distance,square(ghost.center().subtract(camera).length()));
        if (frame.panels().size()==2) for(var s:ParticleLaminateGeometry.segments(frame.panels().getFirst(),frame.panels().getLast()).orElse(List.of()))
            distance=Math.min(distance,segmentDistanceSquared(s.start(),s.end(),camera));
        for(var s:frame.path())distance=Math.min(distance,segmentDistanceSquared(s.start(),s.end(),camera));
        return distance;
    }
    public static boolean visible(ParticleLaminateState.Frame frame, Vector camera) { return distanceSquared(frame,camera)<=CULL_DISTANCE*CULL_DISTANCE; }
    private static double square(double n){return n*n;}
    private static double segmentDistanceSquared(Vector a,Vector b,Vector p){
        Vector d=b.subtract(a);double length2=d.dot(d);double t=length2==0?0:Math.clamp(p.subtract(a).dot(d)/length2,0,1);
        Vector delta=p.subtract(a.add(d.scale(t)));return delta.dot(delta);
    }
    /** A real cross-section in the ray's world-space frame, with finite caps and no billboard-only white center. */
    private static void tube(List<Face> out, Vector a, Vector b, Vector camera, Vector look,
                             double radiusA, double radiusB, int rgb, int alpha, int kind, boolean capA, boolean capB, double uA, double uB) {
        Vector axis = FoldedMirrorGeometry.unit(b.subtract(a)).orElse(null); if (axis == null) return;
        Vector right = FoldedMirrorGeometry.unit(axis.cross(Math.abs(axis.y()) > .9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0))).orElseThrow();
        Vector up = axis.cross(right);
        double za = a.subtract(camera).dot(look), zb = b.subtract(camera).dot(look);
        if (za < RAY_NEAR_DEPTH && zb < RAY_NEAR_DEPTH) return;
        if (za < RAY_NEAR_DEPTH) {
            double t = (RAY_NEAR_DEPTH - za) / (zb - za); a = lerp(a, b, t); radiusA += (radiusB - radiusA) * t; uA += (uB - uA) * t; capA = false;
        } else if (zb < RAY_NEAR_DEPTH) {
            double t = (RAY_NEAR_DEPTH - za) / (zb - za); b = lerp(a, b, t); radiusB = radiusA + (radiusB - radiusA) * t; uB = uA + (uB - uA) * t; capB = false;
        }
        if (a.subtract(b).length() < EPS) return;
        Vector[] first = ring(a, right, up, camera, look, radiusA, kind), last = ring(b, right, up, camera, look, radiusB, kind);
        for (int i = 0; i < TUBE_SIDES; i++) {
            int next = (i + 1) % TUBE_SIDES;
            out.add(new Face(first[i], first[next], last[next], last[i], rgb, alpha, kind, WHITE_TEXTURE,
                    tubeUv(first[i], a, camera, axis, uA), tubeUv(first[next], a, camera, axis, uA),
                    tubeUv(last[next], b, camera, axis, uB), tubeUv(last[i], b, camera, axis, uB)));
        }
        // Four quads cover the eight triangular cap sectors; all vertices lie on the authentic endpoint plane.
        for (int i = 0; i < TUBE_SIDES; i += 2) {
            Uv startCap = new Uv(uA, 1), endCap = new Uv(uB, 1);
            if (capA) out.add(new Face(a, first[(i + 2) % TUBE_SIDES], first[i + 1], first[i], rgb, alpha, kind, WHITE_TEXTURE, startCap, startCap, startCap, startCap));
            if (capB) out.add(new Face(b, last[i], last[i + 1], last[(i + 2) % TUBE_SIDES], rgb, alpha, kind, WHITE_TEXTURE, endCap, endCap, endCap, endCap));
        }
    }
    private static Uv tubeUv(Vector point, Vector center, Vector camera, Vector axis, double u) {
        Vector normal = FoldedMirrorGeometry.unit(point.subtract(center)).orElse(new Vector(0, 1, 0));
        Vector toward = camera.subtract(center);
        Vector view = FoldedMirrorGeometry.unit(toward.subtract(axis.scale(toward.dot(axis)))).orElse(normal);
        return new Uv(u, .5 + .5 * Math.clamp(normal.dot(view), -1, 1));
    }
    private static Vector[] ring(Vector center, Vector right, Vector up, Vector camera, Vector look, double radius, int kind) {
        Vector[] directions = new Vector[TUBE_SIDES], points = new Vector[TUBE_SIDES];
        double bounded = radius;
        for (int i = 0; i < TUBE_SIDES; i++) {
            double angle = Math.PI * 2 * i / TUBE_SIDES;
            directions[i] = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            bounded = Math.min(bounded, projectiveWidth(center.subtract(camera), directions[i], look, radius, kind));
        }
        // One shared radius keeps a circular world-space section; camera clipping never turns it into paired ribbons.
        for (int i = 0; i < TUBE_SIDES; i++) points[i] = center.add(directions[i].scale(bounded));
        return points;
    }
    private static boolean validCamera(Vector camera, Vector look) { return camera != null && camera.finite() && ParticleLaminateState.direction(look); }
    private static Vector side(Vector from, Vector to, Vector camera, Vector look) {
        Vector axis = to.subtract(from), side = axis.cross(camera.subtract(from));
        if (side.length() < EPS) side = axis.cross(Math.abs(look.y()) > .9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0));
        return FoldedMirrorGeometry.unit(side).orElse(null);
    }
    /** Clip the authentic centerline before expanding width, including historic source positions after strafing. */
    private static void ray(List<Face> out, Vector a, Vector b, Vector side, Vector camera, Vector look,
                            double widthA, double widthB, int rgb, int alpha, int kind, int texture) {
        double za = a.subtract(camera).dot(look), zb = b.subtract(camera).dot(look);
        double uA = 0, uB = 1;
        if (za < RAY_NEAR_DEPTH && zb < RAY_NEAR_DEPTH) return;
        if (za < RAY_NEAR_DEPTH) {
            double t = (RAY_NEAR_DEPTH - za) / (zb - za); a = lerp(a, b, t); widthA += (widthB - widthA) * t; uA = t;
        } else if (zb < RAY_NEAR_DEPTH) {
            double t = (RAY_NEAR_DEPTH - za) / (zb - za); b = lerp(a, b, t); widthB = widthA + (widthB - widthA) * t; uB = t;
        }
        if (a.subtract(b).length() < EPS) return;
        Vector wa = side.scale(projectiveWidth(a.subtract(camera), side, look, widthA, kind));
        Vector wb = side.scale(projectiveWidth(b.subtract(camera), side, look, widthB, kind));
        out.add(new Face(a.add(wa), a.subtract(wa), b.subtract(wb), b.add(wb), rgb, alpha, kind, texture,
                new Uv(uA, 1), new Uv(uA, 0), new Uv(uB, 0), new Uv(uB, 1)));
    }
    public static double angularLimit(int kind) {
        return kind == BEAM_HALO ? HALO_ANGULAR_HALF_WIDTH : kind == BEAM_CORE ? CORE_ANGULAR_HALF_WIDTH : MAX_ANGULAR_HALF_WIDTH;
    }
    private static double projectiveWidth(Vector relative, Vector side, Vector look, double width, int kind) {
        double z = relative.dot(look), depth = side.dot(look);
        double radius = kind == BEAM_HALO ? HALO_RADIUS : kind == BEAM_CORE ? CORE_RADIUS : FoldedMirrorGeometry.BEAM_RADIUS;
        double angular = angularLimit(kind) * width / radius;
        double numerator = side.scale(z).subtract(relative.scale(depth)).length();
        double denominator = numerator + angular * z * Math.abs(depth);
        return denominator <= EPS ? width : Math.min(width, angular * z * z / denominator);
    }
}

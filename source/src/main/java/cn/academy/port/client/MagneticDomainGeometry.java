package cn.academy.port.client;

import cn.academy.port.client.RailThunderArrayGeometry.Basis;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import java.util.ArrayList;
import java.util.List;

/** Sparse twelve-meter sphere and sixty-degree half-angle front boundary; never a solid dome. */
public final class MagneticDomainGeometry {
    public static final double RADIUS = 12, FRONT_COSINE = .5;
    public static final int MAX_QUADS = 128, BLUE = 0x82CFFF, WHITE = 0xE7F8FF;
    public static final int BOUNDARY_BLUE = 0x3C93D7, FLOW_BLUE = 0x217CCA, BOUNDARY_ALPHA = 205, FLOW_ALPHA = 225, CORE_ALPHA = 215;
    public static final double WIDTH = .025, RECEIPT_WIDTH = .008, FLOW_WIDTH = .019, FLOW_CORE_WIDTH = .005;
    private MagneticDomainGeometry() {}

    /** A strict budget applies after near-plane/aim clipping, including the possible two-piece split. */
    public static final class Budget {
        private int used;
        public boolean take() { if (used >= MAX_QUADS) return false; used++; return true; }
        public int used() { return used; }
    }
    private static Point radial(Basis b, double latitude, double longitude) {
        double ring = Math.cos(latitude);
        return b.forward().scale(Math.sin(latitude)).plus(b.side().scale(ring * Math.cos(longitude))).plus(b.up().scale(ring * Math.sin(longitude)));
    }
    /** Four interrupted latitude bands, flowing slowly; every endpoint sits on the actual sphere. */
    public static List<Segment> boundary(Point center, Point facing, int frame) {
        Basis basis = RailThunderArrayGeometry.basis(facing);
        if (!RailThunderArrayGeometry.validPoint(center) || basis == null) return List.of();
        List<Segment> lines = new ArrayList<>(40);
        double phase = Math.floorMod(frame, 1200) * Math.PI / 300;
        for (int band = 0; band < 4; band++) {
            double latitude = Math.toRadians(-60 + band * 40);
            for (int arc = 0; arc < 5; arc++) {
                double start = phase * (band % 2 == 0 ? 1 : -1) + arc * Math.PI * 2 / 5 + band * .29;
                for (int step = 0; step < 2; step++) {
                    double a = start + step * .055, b = a + .055;
                    lines.add(new Segment(center.plus(radial(basis, latitude, a).scale(RADIUS)), center.plus(radial(basis, latitude, b).scale(RADIUS))));
                }
            }
        }
        return List.copyOf(lines);
    }
    /** Broken circle is exactly the boundary of the 120-degree front sector, not a preview of a hit. */
    public static List<Segment> frontBoundary(Point center, Point facing, int frame) {
        Basis basis = RailThunderArrayGeometry.basis(facing);
        if (!RailThunderArrayGeometry.validPoint(center) || basis == null) return List.of();
        List<Segment> lines = new ArrayList<>(12);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6 + Math.floorMod(frame, 1200) * Math.PI / 600;
            lines.add(new Segment(center.plus(radial(basis, Math.PI / 6, a).scale(RADIUS)),
                    center.plus(radial(basis, Math.PI / 6, a + .045).scale(RADIUS))));
        }
        return List.copyOf(lines);
    }
    /**
     * Two interrupted, gently undulating circulation streams inside the field. These are decorative
     * moving arcs, deliberately unlike the spherical boundary or the directional refraction receipt.
     * Their world-horizontal planes keep a readable flow above/below the aim line at normal FOV.
     */
    public static List<Segment> circulation(Point center, int frame) {
        if (!RailThunderArrayGeometry.validPoint(center)) return List.of();
        List<Segment> lines = new ArrayList<>(24);
        double phase = Math.floorMod(frame, 1200) * Math.PI / 120;
        for (int band = 0; band < 2; band++) {
            double radius = band == 0 ? 3.2 : 3.8, height = band == 0 ? -.1 : 1.3;
            for (int arc = 0; arc < 3; arc++) {
                double start = arc * Math.PI * 2 / 3 + phase * (band == 0 ? 1 : -1) + band * .47;
                for (int step = 0; step < 4; step++) {
                    double a = start + step * Math.PI / 15, b = a + Math.PI / 15;
                    Point from = center.plus(new Point(Math.sin(a) * radius, height + .08 * Math.sin(a * 3 + phase), Math.cos(a) * radius));
                    Point to = center.plus(new Point(Math.sin(b) * radius, height + .08 * Math.sin(b * 3 + phase), Math.cos(b) * radius));
                    lines.add(new Segment(from, to));
                }
            }
        }
        return List.copyOf(lines);
    }

    /** Shield impact is a tiny outward four-spoke flash at the accepted absorption location. */
    public static List<Segment> impact(Point point, Point outward, int age) {
        Basis basis = RailThunderArrayGeometry.basis(outward);
        if (!RailThunderArrayGeometry.validPoint(point) || basis == null || age < 0 || age >= MagneticDomainState.HIT_TICKS) return List.of();
        double radius = .11 + age * .022;
        List<Segment> lines = new ArrayList<>(4);
        for (Point axis : List.of(basis.side(), basis.up())) for (int sign : new int[]{-1, 1})
            lines.add(new Segment(point.plus(axis.scale(sign * radius * .32)), point.plus(axis.scale(sign * radius))));
        return List.copyOf(lines);
    }
    /** Two curved brackets mark an actual refraction receipt, without inventing an outgoing projectile path. */
    public static List<Segment> refraction(Point point, Point axis, int age) {
        Basis basis = RailThunderArrayGeometry.basis(axis);
        if (!RailThunderArrayGeometry.validPoint(point) || basis == null || age < 0 || age >= MagneticDomainState.REFRACT_TICKS) return List.of();
        List<Segment> lines = new ArrayList<>(6);
        double size = .14 + age * .01;
        for (int sign : new int[]{-1, 1}) for (int i = 0; i < 3; i++) {
            double a = -.65 + i * .43, b = a + .43;
            Point start = point.plus(basis.side().scale(sign * size * Math.cos(a))).plus(basis.up().scale(size * Math.sin(a)));
            Point end = point.plus(basis.side().scale(sign * size * Math.cos(b))).plus(basis.up().scale(size * Math.sin(b)));
            lines.add(new Segment(start, end));
        }
        return List.copyOf(lines);
    }
    /** Self-only slow fall is anchored under the caster; it is never drawn under neighboring entities. */
    public static List<Segment> slowFall(Point center, int frame) {
        if (!RailThunderArrayGeometry.validPoint(center)) return List.of();
        List<Segment> lines = new ArrayList<>(3);
        double rise = Math.floorMod(frame, 12) / 12.0 * .15;
        for (int i = 0; i < 3; i++) {
            double angle = i * Math.PI * 2 / 3;
            Point at = center.plus(new Point(Math.cos(angle) * .36, -1.02 + rise, Math.sin(angle) * .36));
            lines.add(new Segment(at, at.plus(new Point(0, .09, 0))));
        }
        return List.copyOf(lines);
    }
}

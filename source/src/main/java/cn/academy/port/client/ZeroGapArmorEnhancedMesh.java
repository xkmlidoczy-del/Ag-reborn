package cn.academy.port.client;

import cn.academy.port.client.FoldedMirrorMesh.Face;
import cn.academy.port.client.FoldedMirrorMesh.Material;
import cn.academy.port.client.FoldedMirrorMesh.Uv;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.core.ZeroGapArmorGeometry;
import cn.academy.port.highpower.core.ZeroGapArmorGeometry.Plate;
import java.util.ArrayList;
import java.util.List;
import static cn.academy.port.client.FoldedMirrorMesh.*;

/** Optional NEW252 material geometry, confined to the original numbered finite rectangles. */
public final class ZeroGapArmorEnhancedMesh {
    public static final int SEGMENTS_PER_EDGE = 4;
    /** Unique color tags select the two optional rim bands; both retain the original RIM kind. */
    public static final int OUTER_RIM_COLOR = 0x63D98C, INNER_RIM_COLOR = 0xEAFFF3;
    public static final int DORMANT_COLOR = 0x4D6657, SPENT_COLOR = 0x7E8E84;
    public static final double OUTER_RIM_WIDTH = .045, INNER_RIM_WIDTH = .014;
    public static final double DORMANT_BRACKET_LENGTH = .11, SPENT_BRACKET_LENGTH = .065;
    private static final double PERIMETER = 2 * (ZeroGapArmorGeometry.WIDTH + ZeroGapArmorGeometry.HEIGHT);

    private ZeroGapArmorEnhancedMesh() {}

    public static boolean isEnhancedRim(Face face) {
        return face != null && face.kind() == RIM && face.texture() == WHITE_TEXTURE
                && (face.color() == OUTER_RIM_COLOR || face.color() == INNER_RIM_COLOR);
    }

    public static boolean isOuterRim(Face face) {
        return isEnhancedRim(face) && face.color() == OUTER_RIM_COLOR;
    }

    public static List<Face> plate(ZeroGapArmorState.Frame current, int index, int fadeAge, float materialPhase) {
        return plate(current, index, fadeAge, materialPhase, null, ZeroGapArmorState.HIT_TICKS);
    }

    /**
     * The original mesh owns validation, fill, slab sides and confirmed-contact material.
     * Startup, break and retirement are exact original output. Only active live rims and
     * active unfunded/dormant outlines change; no additional contact geometry is produced.
     */
    public static List<Face> plate(ZeroGapArmorState.Frame current, int index, int fadeAge, float materialPhase,
                                   ZeroGapArmorState.Frame contact, float contactAge) {
        List<Face> original = ZeroGapArmorMesh.plate(current, index, fadeAge, materialPhase, contact, contactAge);
        if (original.isEmpty() || fadeAge >= 0 || current.phase() == ZeroGapArmorState.STARTUP
                || current.phase() == ZeroGapArmorState.ENDED || current.breakTicks() > 0) return original;
        Plate plate = ZeroGapArmorMesh.snapshot(current).plates().get(index);
        boolean live = (current.liveMask() & 1 << index) != 0;
        Material material = original.getFirst().material();
        List<Face> result = new ArrayList<>(live ? 37 : 8);
        if (live) {
            for (Face face : original) if (face.kind() != RIM) result.add(face);
            for (int edge = 0; edge < 4; edge++) for (int part = 0; part < SEGMENTS_PER_EDGE; part++) {
                double from = part / (double)SEGMENTS_PER_EDGE, to = (part + 1) / (double)SEGMENTS_PER_EDGE;
                result.add(band(plate, edge, from, to, OUTER_RIM_WIDTH, OUTER_RIM_COLOR, 84, RIM, material));
                result.add(band(plate, edge, from, to, INNER_RIM_WIDTH, INNER_RIM_COLOR, 210, RIM, material));
            }
        } else {
            // Only the native live mask establishes funding. Break retains its original unknown-funding brackets.
            boolean selected = (current.selectedMask() & 1 << index) != 0;
            double length = selected ? SPENT_BRACKET_LENGTH : DORMANT_BRACKET_LENGTH;
            for (int edge = 0; edge < 4; edge++) {
                double fraction = length / edgeLength(edge);
                int color = selected ? SPENT_COLOR : DORMANT_COLOR;
                int alpha = selected ? 48 : 34;
                double width = selected ? .009 : .007;
                result.add(band(plate, edge, 0, fraction, width, color, alpha, GHOST, material));
                result.add(band(plate, edge, 1 - fraction, 1, width, color, alpha, GHOST, material));
            }
        }
        return List.copyOf(result);
    }

    private static double edgeLength(int edge) {
        return edge % 2 == 0 ? ZeroGapArmorGeometry.WIDTH : ZeroGapArmorGeometry.HEIGHT;
    }

    private static double perimeterStart(int edge) {
        return switch (edge) {
            case 0 -> 0;
            case 1 -> ZeroGapArmorGeometry.WIDTH;
            case 2 -> ZeroGapArmorGeometry.WIDTH + ZeroGapArmorGeometry.HEIGHT;
            case 3 -> 2 * ZeroGapArmorGeometry.WIDTH + ZeroGapArmorGeometry.HEIGHT;
            default -> throw new IllegalArgumentException("Invalid plate edge");
        };
    }

    /** Corresponding outer/inner corners make closed miter joins without extending beyond the slab. */
    private static Vector corner(Plate plate, int corner, double inset) {
        double w = ZeroGapArmorGeometry.HALF_WIDTH - inset, h = ZeroGapArmorGeometry.HALF_HEIGHT - inset;
        double x = corner == 0 || corner == 3 ? -w : w;
        double y = corner < 2 ? -h : h;
        var basis = ZeroGapArmorGeometry.frame(plate);
        return plate.center().add(basis.right().scale(x)).add(basis.up().scale(y));
    }

    private static Vector along(Vector a, Vector b, double fraction) {
        return a.add(b.subtract(a).scale(fraction));
    }

    private static Face band(Plate plate, int edge, double from, double to, double width,
                             int color, int alpha, int kind, Material material) {
        int next = (edge + 1) % 4;
        Vector start = corner(plate, edge, 0), end = corner(plate, next, 0);
        Vector innerStart = corner(plate, edge, width), innerEnd = corner(plate, next, width);
        double u0 = (perimeterStart(edge) + edgeLength(edge) * from) / PERIMETER;
        double u1 = (perimeterStart(edge) + edgeLength(edge) * to) / PERIMETER;
        return new Face(along(start, end, from), along(start, end, to),
                along(innerStart, innerEnd, to), along(innerStart, innerEnd, from),
                color, alpha, kind, WHITE_TEXTURE, new Uv(u0, 0), new Uv(u1, 0),
                new Uv(u1, 1), new Uv(u0, 1), material);
    }
}

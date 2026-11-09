package cn.academy.port.fleet.client;

/** Shared outward box topology for the skin hand mesh and its geometry check. */
public final class RigGeometry {
    private static final int[][] BOX_FACES = {
            {3, 2, 1, 0}, // -Z
            {6, 7, 4, 5}, // +Z
            {7, 3, 0, 4}, // -X
            {2, 6, 5, 1}, // +X
            {7, 6, 2, 3}, // +Y
            {0, 1, 5, 4}  // -Y
    };
    public static final int BOX_FACE_COUNT = BOX_FACES.length;
    private RigGeometry() {}
    public static int boxCorner(int face, int corner) { return BOX_FACES[face][corner]; }
}

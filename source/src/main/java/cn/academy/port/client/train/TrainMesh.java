package cn.academy.port.client.train;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/** Baked car meshes from assets/academy/train/shinkansen.bin (see scripts/city/export_train_mesh.py). */
final class TrainMesh {
    /** One part: 3 vertices per triangle, each 6 floats (position, normal) and a packed ARGB int. */
    record Part(float[] v, int[] argb, int triangles) {}

    static final int HEAD_SHELL = 0, MID_SHELL = 1, HEAD_INTERIOR = 2, MID_INTERIOR = 3;
    private static Part[] parts;

    private TrainMesh() {}

    static synchronized Part part(int index) {
        if (parts == null) parts = load();
        return parts[index];
    }

    private static Part[] load() {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                TrainMesh.class.getResourceAsStream("/assets/academy/train/shinkansen.bin"), 1 << 16))) {
            Part[] out = new Part[in.readInt()];
            for (int p = 0; p < out.length; p++) {
                int tris = in.readInt();
                float[] v = new float[tris * 3 * 6];
                int[] argb = new int[tris * 3];
                for (int i = 0; i < tris * 3; i++) {
                    for (int k = 0; k < 6; k++) v[i * 6 + k] = in.readFloat();
                    int r = in.readUnsignedByte(), g = in.readUnsignedByte(), b = in.readUnsignedByte(), a = in.readUnsignedByte();
                    argb[i] = a << 24 | r << 16 | g << 8 | b;
                }
                out[p] = new Part(v, argb, tris);
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("train mesh", e);
        }
    }
}

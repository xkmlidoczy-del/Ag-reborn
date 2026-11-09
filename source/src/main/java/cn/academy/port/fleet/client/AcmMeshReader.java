package cn.academy.port.fleet.client;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Bounded ACM1 reader; independent of the Minecraft renderer for validation. */
public final class AcmMeshReader {
    public static final int MAX_PARTS = 512;
    public static final int MAX_TOTAL_TRIANGLES = 200_000;
    public static final int MAX_NAME_BYTES = 96;
    public record Part(String name, float x, float y, float z, int triangles, float[] values) {}
    public record Mesh(Part[] parts, int triangles) {}
    private AcmMeshReader() {}

    public static Mesh read(InputStream stream) throws IOException {
        DataInputStream in = new DataInputStream(stream);
        if (in.readInt() != 0x41434d31) throw new IOException("not an ACM1 mesh");
        int count = in.readInt();
        if (count <= 0 || count > MAX_PARTS) throw new IOException("invalid ACM1 part count: " + count);
        Part[] parts = new Part[count];
        Set<String> names = new HashSet<>();
        int total = 0;
        for (int partIndex = 0; partIndex < count; partIndex++) {
            int length = in.readUnsignedShort();
            if (length == 0 || length > MAX_NAME_BYTES) throw new IOException("invalid ACM1 name length");
            byte[] bytes = in.readNBytes(length);
            if (bytes.length != length) throw new EOFException("truncated ACM1 name");
            for (byte b : bytes) if (b < 32 || b > 126) throw new IOException("non-ASCII ACM1 name");
            String name = new String(bytes, StandardCharsets.US_ASCII);
            if (!names.add(name)) throw new IOException("duplicate ACM1 part: " + name);
            float x = finite(in.readFloat(), "pivot"), y = finite(in.readFloat(), "pivot"), z = finite(in.readFloat(), "pivot");
            int triangles = in.readInt();
            if (triangles <= 0 || triangles > MAX_TOTAL_TRIANGLES - total) {
                throw new IOException("ACM1 triangle budget exceeded or empty part: " + triangles);
            }
            total += triangles;
            float[] values = new float[triangles * 24];
            for (int i = 0; i < values.length; i++) values[i] = finite(in.readFloat(), "vertex");
            for (int vertex = 0; vertex < values.length; vertex += 8) {
                double nx = values[vertex + 3], ny = values[vertex + 4], nz = values[vertex + 5];
                double norm2 = nx * nx + ny * ny + nz * nz;
                if (norm2 < .000001 || norm2 > 4) throw new IOException("invalid ACM1 normal in " + name);
                // The exporter writes unit normals; normalise bounded older files too.
                float inv = (float) (1 / Math.sqrt(norm2));
                values[vertex + 3] *= inv;
                values[vertex + 4] *= inv;
                values[vertex + 5] *= inv;
            }
            parts[partIndex] = new Part(name, x, y, z, triangles, values);
        }
        if (in.read() != -1) throw new IOException("unexpected trailing ACM1 bytes");
        return new Mesh(parts, total);
    }

    private static float finite(float value, String what) throws IOException {
        if (!Float.isFinite(value) || Math.abs(value) > 10_000) throw new IOException("invalid ACM1 " + what);
        return value;
    }
}

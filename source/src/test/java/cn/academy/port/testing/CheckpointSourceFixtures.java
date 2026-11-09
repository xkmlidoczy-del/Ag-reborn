package cn.academy.port.testing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Small audited original texts only. Missing, empty, unlisted or altered fixtures fail closed. */
public final class CheckpointSourceFixtures {
    private static final String ROOT = "/cn/academy/port/source-checkpoint/";
    private static final Map<String, String> PINNED = Map.of(
            "academycraft-1.0.7/ScatterBomb.scala.txt", "2516cd187fab43de8336a2e64b39a8f94f70367f51bb230c0b0690f547618b87",
            "academycraft-1.0.7/LightShield.scala.txt", "0891d76f017957b1a49010463fb39304b2fcb58de650df8853a78d44c67e71be",
            "academycraft-1.0.7/RenderMdShield.java.txt", "851065d5d3645dad2b53cf771009841a12f8656e07d30bda178ec1dd36583bbe",
            "lambdalib-1.2.3/Motion3D.java.txt", "85d514c11b5130b994c2ceb7efa3bd93a7c0a832e65a9abb0f196cc6d2897a56",
            "academycraft-1.0.7/mine-detect-original-assets.json", "5cdbc956a6400f02e7e4e9fcced6d42aaa25adf249fe8a6da147416b087e6700");
    private CheckpointSourceFixtures() {}

    public static String pinnedText(String name) throws Exception {
        byte[] bytes = resourceBytes(ROOT + name);
        verifyPinned(name, bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    static void verifyPinned(String name, byte[] bytes) throws Exception {
        String wanted = PINNED.get(name);
        if (wanted == null) throw new AssertionError("Unlisted source fixture: " + name);
        if (bytes == null || bytes.length == 0) throw new AssertionError("Empty source fixture: " + name);
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (!actual.equals(wanted)) throw new AssertionError("Altered canonical source fixture " + name + ": expected " + wanted + ", got " + actual);
    }
    public static byte[] resourceBytes(String path) throws Exception {
        if (path == null || !path.startsWith("/") || path.contains("..")) throw new AssertionError("Absolute classpath resource required: " + path);
        try (var stream = CheckpointSourceFixtures.class.getResourceAsStream(path)) {
            if (stream == null) throw new AssertionError("Missing required classpath resource: " + path);
            byte[] result = stream.readAllBytes();
            if (result.length == 0) throw new AssertionError("Empty required classpath resource: " + path);
            return result;
        }
    }
    public static String resourceText(String path) throws Exception {
        return new String(resourceBytes(path), StandardCharsets.UTF_8);
    }
}

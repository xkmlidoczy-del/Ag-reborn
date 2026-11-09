package cn.academy.port.skill;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exact original classpath fixtures. A restored build never reads the excluded reference checkout. */
public final class TeleporterFinalSourceFixtures {
    public static final String ROOT = "/classic-teleporter-final-source/";
    public static final String COMMIT = "00d19ec0cf538f61c1095c9292f5ee6863db4521";
    private static final String MANIFEST_HASH = "a4a535d918157c311625a4e7925096f4667ac47f4ffe9111f8b9541092596796";
    private static final Map<String, String> SOURCES = new LinkedHashMap<>();
    private static final Map<String, String> MEDIA = new LinkedHashMap<>();

    static {
        var manifest = JsonParser.parseString(new String(read(ROOT + "manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        require(hash(read(ROOT + "manifest.json")).equals(MANIFEST_HASH), "Altered original fixture manifest");
        require(manifest.get("academycraft_commit").getAsString().equals(COMMIT), "Wrong canonical commit");
        require(manifest.get("lambdalib_dependency").getAsString().equals("1.2.3"), "Wrong LambdaLib dependency");
        for (var entry : manifest.getAsJsonArray("sources")) {
            var item = entry.getAsJsonObject();
            String key = item.get("project").getAsString() + "/" + item.get("path").getAsString();
            require(SOURCES.put(key, item.get("sha256").getAsString()) == null, "Duplicate original source " + key);
        }
        for (var entry : manifest.getAsJsonArray("media")) {
            var item = entry.getAsJsonObject();
            String key = item.get("path").getAsString();
            require(MEDIA.put(key, item.get("sha256").getAsString()) == null, "Duplicate original media " + key);
        }
        require(SOURCES.size() == 44 && MEDIA.size() == 28, "Incomplete pinned fixture manifest");
    }

    private TeleporterFinalSourceFixtures() {}

    private static void require(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }

    private static byte[] read(String path) {
        try (var input = TeleporterFinalSourceFixtures.class.getResourceAsStream(path)) {
            require(input != null, "Missing classpath original resource " + path);
            return input.readAllBytes();
        } catch (java.io.IOException e) {
            throw new AssertionError("Cannot read original resource " + path, e);
        }
    }

    static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    /** Also used by fail-closed mutation checks; callers cannot supply an expected replacement hash. */
    static void verifySourceBytes(String key, byte[] bytes) {
        require(SOURCES.containsKey(key), "Unlisted original fixture " + key);
        require(bytes.length != 0 && hash(bytes).equals(SOURCES.get(key)), "Altered original fixture " + key);
    }

    static void verifyMediaBytes(String key, byte[] bytes) {
        require(MEDIA.containsKey(key), "Unlisted original media " + key);
        require(bytes.length != 0 && hash(bytes).equals(MEDIA.get(key)), "Altered original media " + key);
    }

    public static String source(String project, String path) {
        String key = project + "/" + path;
        byte[] bytes = read(ROOT + key);
        verifySourceBytes(key, bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String academy(String path) { return source("AcademyCraft-1.0.7", path); }
    public static String lambda(String path) { return source("LambdaLib-1.2.3", path); }
    public static String location() { return academy("src/main/scala/cn/academy/vanilla/teleporter/skill/LocationTeleport.scala"); }
    public static String shift() { return academy("src/main/scala/cn/academy/vanilla/teleporter/skill/ShiftTeleport.scala"); }
    public static String flashing() { return academy("src/main/java/cn/academy/vanilla/teleporter/skill/Flashing.java"); }

    public static int verifyAll() {
        for (String key : SOURCES.keySet()) verifySourceBytes(key, read(ROOT + key));
        return SOURCES.size();
    }

    public static int verifyMedia() {
        for (String key : MEDIA.keySet()) verifyMediaBytes(key, read("/" + key));
        return MEDIA.size();
    }

    public static void contains(String source, String text) {
        require(source.contains(text), "Pinned original source contract missing: " + text);
    }
}

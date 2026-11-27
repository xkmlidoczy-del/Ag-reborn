package cn.academy.biohum.client;

import cn.academy.biohum.BioHum;
import cn.academy.biohum.Profiles;
import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.zip.ZipFile;

/** Locates the local Story YSM release and prepares a body only when first seen. */
final class StoryYsmAssets extends SimplePreparableReloadListener<Map<String, List<StoryYsmAssets.Source>>> {
    static final StoryYsmAssets INSTANCE = new StoryYsmAssets();
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_MEMBER_BYTES = 4 * 1024 * 1024;
    private static final String[][] RELEASE_LAYOUTS = {
            {"models/main.json", "textures/default.png"},
            {"main.json", "default.png"}
    };
    private static final java.util.concurrent.Executor WORKER = Executors.newSingleThreadExecutor(job -> {
        var thread = new Thread(job, "ACA story appearance loader");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile Generation generation = new Generation(Map.of());

    record Appearance(ResourceLocation texture, StoryYsmMesh mesh) {}
    private record Digests(String model, String texture) {}
    private record Release(Map<String, Digests> models) {}
    record Source(Path path, String model, String texture, boolean archive, Digests digests) {}
    private static final class Generation {
        final Map<String, List<Source>> sources;
        final Map<String, Appearance> ready = new HashMap<>();
        final Set<String> requested = new HashSet<>();

        Generation(Map<String, List<Source>> sources) {
            this.sources = sources;
        }
    }

    /** Reads and uploads no data on the render call. The default model remains visible until ready. */
    static Appearance find(String id) {
        var current = generation;
        var loaded = current.ready.get(id);
        if (loaded != null) return loaded;
        var candidates = current.sources.get(id);
        if (candidates != null && current.requested.add(id)) {
            WORKER.execute(() -> prepareAppearance(current, id, candidates));
        }
        return null;
    }

    @Override
    protected Map<String, List<Source>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        if (!ModList.get().isLoaded("geckolib")) return Map.of();
        Path custom = Minecraft.getInstance().gameDirectory.toPath().resolve("config/yes_steve_model/custom");
        if (!Files.isDirectory(custom)) return Map.of();
        try (var in = StoryYsmAssets.class.getResourceAsStream("/assets/acabiohum/story_ysm_v1851.json");
             var paths = Files.list(custom)) {
            if (in == null) return Map.of();
            var release = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Release.class);
            var indexed = new HashMap<String, List<Source>>();
            indexDirectory(custom, release, indexed);
            for (Path candidate : paths.sorted().toList()) {
                if (Files.isDirectory(candidate)) {
                    if (!Profiles.ALL.containsKey(candidate.getFileName().toString())) {
                        indexDirectory(candidate, release, indexed);
                    }
                } else if (candidate.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                    indexArchive(candidate, release, indexed);
                }
            }
            return indexed;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not index the ACA story appearance pack", exception);
            return Map.of();
        }
    }

    @Override
    protected void apply(Map<String, List<Source>> sources, ResourceManager manager, ProfilerFiller profiler) {
        var previous = generation;
        generation = new Generation(sources);
        var textures = Minecraft.getInstance().getTextureManager();
        previous.ready.values().forEach(appearance -> textures.release(appearance.texture()));
    }

    private static void indexDirectory(Path path, Release release, Map<String, List<Source>> sources) {
        release.models().forEach((id, digests) -> {
            if (!Profiles.ALL.containsKey(id)) return;
            for (String[] layout : RELEASE_LAYOUTS) {
                String model = id + "/" + layout[0];
                String texture = id + "/" + layout[1];
                if (Files.isRegularFile(path.resolve(model)) && Files.isRegularFile(path.resolve(texture))) {
                    add(sources, id, new Source(path, model, texture, false, digests));
                }
            }
        });
    }

    private static void indexArchive(Path path, Release release, Map<String, List<Source>> sources) {
        try (var archive = new ZipFile(path.toFile())) {
            for (var entry : release.models().entrySet()) {
                var id = entry.getKey();
                if (!Profiles.ALL.containsKey(id)) continue;
                for (String[] layout : RELEASE_LAYOUTS) {
                    String model = id + "/" + layout[0];
                    String texture = id + "/" + layout[1];
                    if (archive.getEntry(model) != null && archive.getEntry(texture) != null) {
                        add(sources, id, new Source(path, model, texture, true, entry.getValue()));
                    }
                }
            }
        } catch (IOException exception) {
            LOGGER.debug("Could not list YSM model archive {}", path, exception);
        }
    }

    private static void add(Map<String, List<Source>> sources, String id, Source source) {
        sources.computeIfAbsent(id, ignored -> new ArrayList<>()).add(source);
    }

    private static void prepareAppearance(Generation current, String id, List<Source> sources) {
        for (var source : sources) {
            if (current != generation) return;
            try {
                byte[][] files = read(source);
                var actual = new Digests(sha256(files[0]), sha256(files[1]));
                if (!source.digests().equals(actual)) continue;
                var mesh = StoryYsmMesh.bake(id, new String(files[0], StandardCharsets.UTF_8));
                var pixels = NativeImage.read(new java.io.ByteArrayInputStream(files[1]));
                Minecraft.getInstance().execute(() -> publish(current, id, mesh, pixels));
                return;
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Could not load ACA story appearance {} from {}", id, source.path(), exception);
            }
        }
    }

    private static void publish(Generation current, String id, StoryYsmMesh mesh, NativeImage pixels) {
        if (current != generation) {
            pixels.close();
            return;
        }
        var texture = ResourceLocation.fromNamespaceAndPath(BioHum.ID, "story_ysm/v1851/" + id);
        DynamicTexture dynamic = null;
        try {
            dynamic = new DynamicTexture(pixels);
            Minecraft.getInstance().getTextureManager().register(texture, dynamic);
            current.ready.put(id, new Appearance(texture, mesh));
        } catch (RuntimeException exception) {
            if (dynamic == null) pixels.close();
            else dynamic.close();
            LOGGER.warn("Could not register ACA story appearance {}", id, exception);
        }
    }

    private static byte[][] read(Source source) throws IOException {
        if (source.archive()) {
            try (var archive = new ZipFile(source.path().toFile())) {
                var model = archive.getEntry(source.model());
                var texture = archive.getEntry(source.texture());
                if (model == null || texture == null) throw new IOException("Model archive changed after indexing");
                try (var geometry = archive.getInputStream(model); var image = archive.getInputStream(texture)) {
                    return new byte[][]{readMember(geometry), readMember(image)};
                }
            }
        }
        try (var geometry = Files.newInputStream(source.path().resolve(source.model()));
             var image = Files.newInputStream(source.path().resolve(source.texture()))) {
            return new byte[][]{readMember(geometry), readMember(image)};
        }
    }

    private static byte[] readMember(InputStream in) throws IOException {
        byte[] data = in.readNBytes(MAX_MEMBER_BYTES + 1);
        if (data.length > MAX_MEMBER_BYTES) throw new IOException("Story appearance file is too large");
        return data;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

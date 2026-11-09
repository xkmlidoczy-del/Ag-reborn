package cn.academy.port.client;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.tools.ToolProvider;

/** Current compileJava ClassResources, closed independent review fixtures, real cached CPU geometry APIs.
 * No production renderer source or binary snapshot is compiled or embedded as the subject.
 */
public final class ClassicMeltdownerGeometryClassResourcesTest {
    private static final String PREFIX = "classic-oracles/meltdowner-camera/";
    private static final String INDEX_SHA = "49cec99d629e9eb46f6cab953f4ddd1e5271b36dc4da37182ed7bbab8e00c98a";
    private static final String PACKAGE = "cn.academy.port.client.";
    private static final String HARNESS = PACKAGE + "MeltdownerCurrentMainGeometryHarness";
    private static final List<String> CLASSES = List.of(
        "cn.academy.port.client.ClassicAdvancedMineRayTimeline",
        "cn.academy.port.client.ClassicAdvancedMineRayTimeline$Color",
        "cn.academy.port.client.ClassicMeltdownerBeamEffects",
        "cn.academy.port.client.ClassicMeltdownerBeamEffects$Context",
        "cn.academy.port.client.ClassicMeltdownerBeamEffects$FollowingSound",
        "cn.academy.port.client.ClassicMeltdownerBeamEffects$Material",
        "cn.academy.port.client.ClassicMeltdownerBeamEffects$Material$Key",
        "cn.academy.port.client.ClassicMeltdownerBeamEffects$MdParticle",
        "cn.academy.port.client.ClassicMeltdownerBeamEffects$Ray",
        "cn.academy.port.client.ClassicMeltdownerBeamTimeline",
        "cn.academy.port.client.ClassicMeltdownerBeamTimeline$Caster",
        "cn.academy.port.client.ClassicMeltdownerBeamTimeline$CylinderFrame",
        "cn.academy.port.client.ClassicMeltdownerBeamTimeline$Endpoints",
        "cn.academy.port.client.ClassicMeltdownerBeamTimeline$Glow",
        "cn.academy.port.client.ClassicMeltdownerBeamTimeline$ParticleSequence",
        "cn.academy.port.client.ClassicMeltdownerBeamTimeline$Point",
        "cn.academy.port.client.ClassicMeltdownerBeamTimeline$Spec",
        "cn.academy.port.client.ClassicMeltdownerBeamTimeline$Tokens",
        "cn.academy.port.client.ClassicMeltdownerBeamTimeline$Tokens$State",
        "cn.academy.port.client.ClassicMeltdownerLateEffects",
        "cn.academy.port.client.ClassicMeltdownerLateEffects$Ball",
        "cn.academy.port.client.ClassicMeltdownerLateEffects$Context",
        "cn.academy.port.client.ClassicMeltdownerLateEffects$Material",
        "cn.academy.port.client.ClassicMeltdownerLateEffects$Material$Key",
        "cn.academy.port.client.ClassicMeltdownerLateEffects$Particle",
        "cn.academy.port.client.ClassicMeltdownerLateEffects$Ray",
        "cn.academy.port.client.ClassicMeltdownerLateEffects$RaySound",
        "cn.academy.port.client.ClassicMeltdownerLateTimeline",
        "cn.academy.port.client.ClassicMeltdownerLateTimeline$SubRay",
        "cn.academy.port.client.ClassicMeltdownerStarterEffects",
        "cn.academy.port.client.ClassicMeltdownerStarterEffects$Ball",
        "cn.academy.port.client.ClassicMeltdownerStarterEffects$Context",
        "cn.academy.port.client.ClassicMeltdownerStarterEffects$CutoffShader",
        "cn.academy.port.client.ClassicMeltdownerStarterEffects$FollowingSound",
        "cn.academy.port.client.ClassicMeltdownerStarterEffects$Material",
        "cn.academy.port.client.ClassicMeltdownerStarterEffects$Material$Key",
        "cn.academy.port.client.ClassicMeltdownerStarterEffects$MdParticle",
        "cn.academy.port.client.ClassicMeltdownerStarterEffects$SmallRay",
        "cn.academy.port.client.ClassicMeltdownerStarterTimeline",
        "cn.academy.port.client.ClassicMeltdownerStarterTimeline$BallWiggle",
        "cn.academy.port.client.ClassicMeltdownerStarterTimeline$Input",
        "cn.academy.port.client.ClassicMeltdownerStarterTimeline$PauseClock",
        "cn.academy.port.client.ClassicMeltdownerStarterTimeline$Point",
        "cn.academy.port.client.ClassicMeltdownerStarterTimeline$Quad",
        "cn.academy.port.client.ClassicMeltdownerStarterTimeline$RayWiggle",
        "cn.academy.port.client.ClassicMeltdownerStarterTimeline$ShieldFrame",
        "cn.academy.port.client.ClassicMeltdownerStarterTimeline$Tokens",
        "cn.academy.port.client.ClassicMeltdownerStarterTimeline$Tokens$State",
        "cn.academy.port.client.ClassicMeltdownerStarterTimeline$Vertex",
        "cn.academy.port.clientless.ClassicLateTrig");
    private static final List<String> HOSTS = List.of(
        "net.minecraft.client.Minecraft",
        "net.minecraft.client.Options",
        "net.minecraft.client.player.LocalPlayer",
        "net.minecraft.client.renderer.entity.ItemRenderer",
        "net.minecraft.world.entity.Entity",
        "net.minecraft.world.entity.LivingEntity",
        "net.minecraft.world.entity.player.Player");
    private static final List<String> API = List.of(
        "net.minecraft.world.phys.Vec3",
        "com.mojang.blaze3d.vertex.PoseStack",
        "com.mojang.blaze3d.vertex.PoseStack$Pose",
        "com.mojang.blaze3d.vertex.VertexConsumer",
        "net.minecraft.client.renderer.RenderType",
        "net.minecraft.client.renderer.RenderStateShard",
        "net.minecraft.client.Camera",
        "net.neoforged.neoforge.client.event.RenderLevelStageEvent",
        "com.mojang.math.Axis",
        "org.joml.Matrix4f",
        "org.joml.Quaternionf");

    private static void require(boolean result, String message) { if (!result) throw new AssertionError(message); }
    private static String required(String key) {
        String value = System.getProperty("academy.meltdowner." + key);
        require(value != null && !value.isBlank(), "Missing actual-main admission: " + key); return value;
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static byte[] resource(String path) throws Exception {
        String absolute = PREFIX + path;
        var urls = Collections.list(ClassicMeltdownerGeometryClassResourcesTest.class.getClassLoader().getResources(absolute));
        require(urls.size() == 1, "Missing/ambiguous closed geometry resource: " + path);
        try (var in = urls.getFirst().openStream()) { return in.readAllBytes(); }
    }
    private static String region(String source, String signature) {
        int start = source.indexOf(signature), opening = source.indexOf('{', start), depth = 0;
        require(start >= 0 && opening >= 0, "Missing protected method: " + signature);
        for (int i = opening; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            else if (source.charAt(i) == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("Incomplete protected method: " + signature);
    }
    private static Set<String> classInventory(Path root) throws Exception {
        var result = new TreeSet<String>();
        try (var files = Files.walk(root)) {
            files.filter(Files::isRegularFile).forEach(p -> {
                String n = root.relativize(p).toString().replace(java.io.File.separatorChar, '/');
                if (n.matches("cn/academy/port/client/(?:ClassicMeltdowner(?:Starter|Beam|Late)(?:Effects|Timeline)|ClassicAdvancedMineRayTimeline)(?:\\$[^/]+)?\\.class")
                    || n.equals("cn/academy/port/clientless/ClassicLateTrig.class")) result.add(n);
            });
        }
        return result;
    }
    private static void snapshot(Map<Path, String> hashes, Path path) throws Exception {
        String digest = sha(Files.readAllBytes(path)); String prior = hashes.putIfAbsent(path, digest);
        require(prior == null || prior.equals(digest), "Input changed during admission: " + path);
    }
    private static void checkSnapshots(Map<Path, String> hashes) throws Exception {
        for (var row : hashes.entrySet()) require(row.getValue().equals(sha(Files.readAllBytes(row.getKey()))),
            "Input changed during geometry proof: " + row.getKey());
    }
    private static void verifyMainResources(ClassLoader application, Path actualMain, Map<Path, String> hashes,
            Map<String, String> evidence) throws Exception {
        var inventory = new TreeSet<String>();
        for (String name : CLASSES) {
            String relative = name.replace('.', '/') + ".class";
            Path file = actualMain.resolve(relative).toRealPath();
            Class<?> type = Class.forName(name, false, application);
            Path origin = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            require(origin.equals(actualMain), "Actual main CodeSource mismatch: " + name);
            URL url = type.getResource("/" + relative);
            require(url != null && url.getProtocol().equals("file") && Path.of(url.toURI()).toRealPath().equals(file),
                "Actual main ClassResource origin mismatch: " + name);
            var locations = Collections.list(application.getResources(relative));
            require(locations.size() == 1 && locations.getFirst().equals(url), "Ambiguous actual main ClassResources: " + name);
            try (var in = url.openStream()) { require(Arrays.equals(in.readAllBytes(), Files.readAllBytes(file)),
                "Actual main ClassResource differs from compileJava output: " + name); }
            require(sha(Files.readAllBytes(file)).equals(required("main.sha256." + name)), "Actual main class changed since task admission: " + name);
            snapshot(hashes, file); inventory.add(relative);
            evidence.put("main.class.sha256." + name, hashes.get(file));
            evidence.put("main.class.resource." + name, url.toExternalForm());
        }
        require(classInventory(actualMain).equals(inventory), "Changed actual-main fifty-class geometry closure");
    }
    private static final class GeometryLoader extends URLClassLoader {
        private final Map<String, Path> bound;
        private final Map<Path, String> admitted;
        GeometryLoader(URL[] librariesAndFixtures, Map<String, Path> bound, Map<Path, String> admitted, Path fixtureClasses) {
            super(librariesAndFixtures, ClassLoader.getPlatformClassLoader()); this.bound = new LinkedHashMap<>(bound); this.admitted = admitted;
            for (String host : HOSTS) this.bound.put(host, fixtureClasses.resolve(host.replace('.', '/') + ".class"));
        }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null && bound.containsKey(name)) {
                    Path file = bound.get(name);
                    try {
                        byte[] bytes = Files.readAllBytes(file);
                        require(sha(bytes).equals(admitted.get(file)), "Subject class changed before definition: " + name);
                        Path root = file;
                        for (String ignored : (name.replace('.', '/') + ".class").split("/")) root = root.getParent();
                        type = defineClass(name, bytes, 0, bytes.length,
                            new CodeSource(root.toUri().toURL(), (java.security.cert.Certificate[]) null));
                    } catch (Exception failure) { throw new ClassNotFoundException(name, failure); }
                }
                if (type == null) {
                    if (name.startsWith("cn.academy.port.") && !(name.equals(HARNESS) || name.startsWith(HARNESS + "$")))
                        throw new ClassNotFoundException("Undeclared production geometry dependency: " + name);
                    type = super.loadClass(name, false);
                }
                if (resolve) resolveClass(type); return type;
            }
        }
        @Override public URL getResource(String name) {
            String type = name.endsWith(".class") ? name.substring(0, name.length() - 6).replace('/', '.') : "";
            if (bound.containsKey(type)) try { return bound.get(type).toUri().toURL(); }
            catch (MalformedURLException failure) { throw new IllegalStateException(failure); }
            return super.getResource(name);
        }
        @Override public Enumeration<URL> getResources(String name) throws IOException {
            String type = name.endsWith(".class") ? name.substring(0, name.length() - 6).replace('/', '.') : "";
            if (bound.containsKey(type)) return Collections.enumeration(List.of(bound.get(type).toUri().toURL()));
            return super.getResources(name);
        }
    }
    private static void verifySubject(GeometryLoader loader, Map<String, Path> bound, Map<Path, String> hashes,
            Properties guards, Map<String, String> evidence) throws Exception {
        for (var row : bound.entrySet()) {
            Class<?> type = Class.forName(row.getKey(), false, loader);
            Path expectedRoot = row.getValue();
            for (String ignored : (row.getKey().replace('.', '/') + ".class").split("/")) expectedRoot = expectedRoot.getParent();
            Path codeSource = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            require(codeSource.equals(expectedRoot), "Subject CodeSource mismatch: " + row.getKey());
            URL resource = type.getResource("/" + row.getKey().replace('.', '/') + ".class");
            require(resource != null && resource.getProtocol().equals("file")
                && Path.of(resource.toURI()).toRealPath().equals(row.getValue()), "Subject ClassResource origin mismatch: " + row.getKey());
            var locations = Collections.list(loader.getResources(row.getKey().replace('.', '/') + ".class"));
            require(locations.size() == 1 && locations.getFirst().equals(resource), "Ambiguous bound subject ClassResource: " + row.getKey());
            snapshot(hashes, row.getValue());
            evidence.put("subject.class.resource." + row.getKey(), resource.toExternalForm());
            evidence.put("subject.class.code.source." + row.getKey(), codeSource.toString());
            evidence.put("subject.class.sha256." + row.getKey(), hashes.get(row.getValue()));
        }
        for (String name : API) {
            Class<?> type = Class.forName(name, false, loader);
            Path jar = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            require(Files.isRegularFile(jar), "Official geometry API did not come from its archive: " + name);
            if (!name.startsWith("org.joml.")) require(jar.equals(Path.of(required("api.minecraft.jar")).toRealPath()),
                "Official Minecraft API must come from explicitly selected standard merged archive: " + name);
            var apiResources = Collections.list(loader.getResources(name.replace('.', '/') + ".class"));
            require(apiResources.size() == 1, "Ambiguous official geometry API ClassResource: " + name);
            require(sha(Files.readAllBytes(jar)).equals(guards.getProperty(name.startsWith("org.joml.") ? "joml.jar.sha256" : "minecraft.jar.sha256")),
                "Official geometry API archive changed: " + name);
            try (var in = type.getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
                require(in != null && sha(in.readAllBytes()).equals(guards.getProperty("api." + name)), "Official geometry API class changed: " + name);
            }
            snapshot(hashes, jar); evidence.put("api.origin." + name, jar.toString());
            evidence.put("api.jar.sha256." + name, hashes.get(jar));
            evidence.put("api.class.sha256." + name, guards.getProperty("api." + name));
        }
    }
    private static void verifyHosts(GeometryLoader loader, Path fixtureClasses, Map<Path, String> hashes,
            Map<String, String> evidence) throws Exception {
        for (String name : HOSTS) {
            Class<?> type = Class.forName(name, false, loader);
            Path root = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            require(root.equals(fixtureClasses.toRealPath()), "Declared host CodeSource mismatch: " + name);
            String relative = name.replace('.', '/') + ".class"; URL resource = type.getResource("/" + relative);
            Path file = fixtureClasses.resolve(relative).toRealPath();
            require(resource != null && Path.of(resource.toURI()).toRealPath().equals(file), "Declared host resource origin mismatch: " + name);
            var locations = Collections.list(loader.getResources(relative));
            require(locations.size() == 1 && locations.getFirst().equals(resource), "Ambiguous declared host resource: " + name);
            snapshot(hashes, file); evidence.put("host.class.sha256." + name, hashes.get(file));
        }
    }
    private static void writeProperties(Path file, Map<String, String> map) throws Exception {
        var properties = new Properties(); properties.putAll(map);
        try (var out = Files.newOutputStream(file)) { properties.store(out, "Current-main Meltdowner CPU geometry proof"); }
    }
    public static void main(String[] args) throws Exception {
        Path report = Path.of(required("report")).toAbsolutePath().normalize(); Files.createDirectories(report);
        for (String name : List.of("PASS", "STAGED_PASS", "evidence.properties", "failure.properties", "files.sha256", "renderer.log", "near-vertices.bin", "near.sha256"))
            Files.deleteIfExists(report.resolve(name));
        Map<Path, String> hashes = new TreeMap<>(); Map<String, String> evidence = new TreeMap<>();
        Map<String, String> fixtureHashes = new TreeMap<>();
        Path temp = Files.createTempDirectory("academy-meltdowner-current-main-");
        Throwable failure = null; String log = "";
        try {
            Path project = Path.of(required("project.dir")).toRealPath();
            Path actualMain = Path.of(required("main.classes")).toRealPath();
            Path mainResources = Path.of(required("main.resources")).toRealPath();
            require(actualMain.equals(project.resolve("build/classes/java/main").toRealPath())
                && mainResources.equals(project.resolve("build/resources/main").toRealPath()),
                "Actual main binding must be the standard admitted compileJava/processResources output");
            Path selectedApi = Path.of(required("api.minecraft.jar")).toRealPath();
            require(selectedApi.equals(project.resolve("build/moddev/artifacts/neoforge-21.1.252-merged.jar").toRealPath()),
                "Official API selection must name the standard ModDev merged artifact");
            snapshot(hashes, selectedApi);
            Path staged = null;
            if (System.getProperty("academy.meltdowner.staged.renderer.classes") != null) {
                require("true".equals(required("staged.verification")), "Staged subject needs explicit staged verification");
                staged = Path.of(required("staged.renderer.classes")).toRealPath();
                require(staged.toString().contains("/.staging/") && !staged.equals(actualMain), "Staged renderer must remain distinct from actual main");
            }
            String mode = staged == null ? "ACTUAL_MAIN" : "STAGED_RENDERER_WITH_ACTUAL_MAIN_TIMELINES";
            evidence.put("mode", mode); evidence.put("game.class.hosts", "seven declared view/entity/glint hosts");
            evidence.put("selected.api.minecraft.jar", selectedApi.toString());
            evidence.put("scope", "Existing 2640-case private ray/ball/shield/diamond CPU matrix; fifty actual compileJava ClassResources; seven declared view/entity/glint hosts; actual cached Vec3/PoseStack/VertexConsumer/RenderType/Camera/Axis/JOML. Event ingress/particles/ripple/world/GPU/shader/audio/original-engine/full-source visual parity remain outside evidence.");
            byte[] index = resource("files.sha256"); require(sha(index).equals(INDEX_SHA), "Changed closed geometry fixture index");
            fixtureHashes.put("files.sha256", INDEX_SHA);
            var fixtureSources = new ArrayList<Path>(); var seen = new HashSet<String>(); Properties guards = new Properties();
            int witnesses = 0, hostSources = 0;
            for (String line : new String(index, StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank()) continue; String[] row = line.split("  ", 2);
                require(row.length == 2 && row[0].matches("[a-f0-9]{64}") && seen.add(row[1])
                    && !row[1].contains("..") && !row[1].startsWith("/"), "Unsafe/duplicate closed geometry fixture index");
                byte[] bytes = resource(row[1]); require(sha(bytes).equals(row[0]), "Changed closed geometry fixture: " + row[1]);
                fixtureHashes.put(row[1], row[0]);
                if (row[1].equals("guards.properties")) guards.load(new ByteArrayInputStream(bytes));
                else if ((row[1].startsWith("harness/") || row[1].startsWith("hosts/")) && row[1].endsWith(".java.txt")) {
                    require(row[1].endsWith(".java.txt"), "Only the declared fixture Java is compiled");
                    Path source = temp.resolve("src").resolve(row[1].substring(0, row[1].length() - 4)).normalize();
                    require(source.startsWith(temp.resolve("src")), "Unsafe extracted geometry source");
                    Files.createDirectories(source.getParent()); Files.write(source, bytes); fixtureSources.add(source); if (row[1].startsWith("hosts/")) hostSources++;
                } else if (!row[1].equals("hosts/BOUNDARIES.md")) {
                    require(row[1].startsWith("witnesses/"), "Undeclared fixture kind"); witnesses++;
                }
            }
            require(seen.size() == 15 && fixtureSources.size() == 8 && hostSources == 7 && witnesses == 5,
                "Incomplete portable geometry closure");
            require(hashes.get(selectedApi).equals(guards.getProperty("minecraft.jar.sha256"))
                && guards.getProperty("original.review.minecraft.jar.sha256").equals("59a14efc6e5c163fe7e1374b3cf66ace00dd20f1b847ae18dd5a08aee186d0c7"),
                "Official merged/review API archive guard changed");
            boolean cameraRelative = true;
            for (int i = 0; i < Integer.parseInt(guards.getProperty("source.count")); i++) {
                Path source = project.resolve(guards.getProperty("source." + i + ".path")).toRealPath();
                String digest = sha(Files.readAllBytes(source));
                require(digest.equals(required("source.sha256." + i)), "Source changed since task admission: " + source);
                require(digest.equals(guards.getProperty("source." + i + ".baseline.sha256"))
                    || digest.equals(guards.getProperty("source." + i + ".candidate.sha256")),
                    "Protected whole source/math/identity changed: " + source);
                if (i < 3 && !digest.equals(guards.getProperty("source." + i + ".candidate.sha256"))) cameraRelative = false;
                snapshot(hashes, source);
            }
            for (int i = 0; i < Integer.parseInt(guards.getProperty("protected.logic.count")); i++) {
                Path source = project.resolve(guards.getProperty("protected.logic." + i + ".path")).toRealPath();
                require(sha(Files.readAllBytes(source)).equals(guards.getProperty("protected.logic." + i + ".sha256")),
                    "Protected costs/admission/identity source changed: " + source);
                snapshot(hashes, source);
            }
            String sourceMode = cameraRelative ? "CAMERA_RELATIVE" : "BASELINE";
            evidence.put("main.source.mode", sourceMode);
            ClassLoader application = ClassicMeltdownerGeometryClassResourcesTest.class.getClassLoader();
            verifyMainResources(application, actualMain, hashes, evidence);
            for (int i = 0; i < Integer.parseInt(guards.getProperty("asset.count")); i++) {
                String asset = guards.getProperty("asset." + i + ".path"), digest = guards.getProperty("asset." + i + ".sha256");
                Path source = project.resolve("src/main/resources").resolve(asset).toRealPath();
                Path processed = mainResources.resolve(asset).toRealPath(); URL url = application.getResource(asset);
                require(url != null && url.getProtocol().equals("file") && Path.of(url.toURI()).toRealPath().equals(processed), "Runtime asset origin mismatch: " + asset);
                require(Collections.list(application.getResources(asset)).size() == 1, "Ambiguous runtime asset resource: " + asset);
                require(sha(Files.readAllBytes(source)).equals(digest) && sha(Files.readAllBytes(processed)).equals(digest), "Original runtime asset bytes changed: " + asset);
                snapshot(hashes, source); snapshot(hashes, processed); evidence.put("asset.sha256." + asset, digest);
            }
            Map<String, Path> bound = new LinkedHashMap<>();
            for (String name : CLASSES) {
                Path root = staged != null && (name.startsWith(PACKAGE + "ClassicMeltdownerStarterEffects") || name.startsWith(PACKAGE + "ClassicMeltdownerBeamEffects") || name.startsWith(PACKAGE + "ClassicMeltdownerLateEffects")) ? staged : actualMain;
                Path file = root.resolve(name.replace('.', '/') + ".class").toRealPath(); snapshot(hashes, file);
                if (root.equals(staged)) require(hashes.get(file).equals(required("staged.sha256." + name)), "Staged renderer bytes changed since explicit admission: " + name);
                bound.put(name, file);
            }
            Path fixtureClasses = temp.resolve("fixture-classes"); Files.createDirectories(fixtureClasses);
            var compile = new ArrayList<String>(List.of("-proc:none", "-implicit:none", "-encoding", "UTF-8", "-cp", System.getProperty("java.class.path"), "-d", fixtureClasses.toString()));
            fixtureSources.forEach(p -> compile.add(p.toString()));
            var compiler = ToolProvider.getSystemJavaCompiler(); require(compiler != null && compiler.run(null, System.out, System.err, compile.toArray(String[]::new)) == 0,
                "Declared geometry fixture-only compile failed");
            try (var compiled = Files.walk(fixtureClasses)) {
                Set<String> outputs = new TreeSet<>();
                compiled.filter(Files::isRegularFile).forEach(p -> outputs.add(fixtureClasses.relativize(p).toString()));
                var expected = new TreeSet<String>();
                for (String host : HOSTS) expected.add(host.replace('.', '/') + ".class");
                for (String suffix : List.of("", "$Sink", "$Draw")) expected.add(HARNESS.replace('.', '/') + suffix + ".class");
                require(outputs.equals(expected), "Fixture compiler emitted an undeclared class or production subject");
            }
            evidence.put("compiled.fixture.class.count", "10");
            evidence.put("compiled.production.subject.class.count", "0");
            var urls = new ArrayList<URL>(); urls.add(fixtureClasses.toUri().toURL()); urls.add(selectedApi.toUri().toURL());
            for (String path : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
                Path jar = Path.of(path).toRealPath();
                if (Files.isRegularFile(jar) && path.endsWith(".jar") && !jar.equals(selectedApi)) {
                    try (var zip = new java.util.zip.ZipFile(jar.toFile())) {
                        // ModDev slim/merged containers can share identical CPU classes but differ as archives.
                        if (zip.getEntry("net/minecraft/world/phys/Vec3.class") == null) urls.add(jar.toUri().toURL());
                    }
                }
            }
            for (String host : HOSTS) snapshot(hashes, fixtureClasses.resolve(host.replace('.', '/') + ".class"));
            try (var loader = new GeometryLoader(urls.toArray(URL[]::new), bound, hashes, fixtureClasses)) {
                verifySubject(loader, bound, hashes, guards, evidence);
                verifyHosts(loader, fixtureClasses, hashes, evidence);
                var captured = new ByteArrayOutputStream(); PrintStream prior = System.out;
                try (var output = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
                    System.setOut(output);
                    try { Class.forName(HARNESS, true, loader).getMethod("main", String[].class).invoke(null,
                        (Object) (System.getProperty("academy.meltdowner.negative.family") == null
                            ? new String[]{"digest-only"}
                            : new String[]{"digest-only", required("negative.family")})); }
                    catch (InvocationTargetException invocation) { failure = invocation.getCause(); }
                    finally { System.setOut(prior); log = captured.toString(StandardCharsets.UTF_8); prior.print(log); }
                }
                verifySubject(loader, bound, hashes, guards, evidence);
                verifyHosts(loader, fixtureClasses, hashes, evidence);
                verifyMainResources(application, actualMain, hashes, evidence);
                if (System.getProperty("academy.meltdowner.negative.family") == null) {
                    require(log.contains("cases=2640 nearVertices=3280176 batches=18792 coordinateComparisons=39362112"),
                        "Incomplete actual renderer case/vertex/coordinate coverage");
                    require(log.contains("near.sha256=" + guards.getProperty("near.sha256")),
                        "Zero-camera position/UV/RGBA/RNG/material/batch reviewed digest changed");
                    evidence.put("near.sha256", guards.getProperty("near.sha256"));
                    evidence.put("near.bytes", guards.getProperty("near.bytes"));
                    if (failure == null && staged == null) require(sourceMode.equals("CAMERA_RELATIVE"),
                        "Source event/main-bytecode binding is still baseline");
                } else {
                    require(staged != null && failure != null, "A staged negative control cannot publish success");
                }

            }
        } catch (Throwable problem) { if (failure == null) failure = problem; else failure.addSuppressed(problem); }
        finally {
            try { checkSnapshots(hashes); } catch (Throwable changed) { if (failure == null) failure = changed; else failure.addSuppressed(changed); }
            try {
                for (var row : fixtureHashes.entrySet()) require(sha(resource(row.getKey())).equals(row.getValue()),
                    "Closed geometry fixture changed during proof: " + row.getKey());
            } catch (Throwable changed) { if (failure == null) failure = changed; else failure.addSuppressed(changed); }
            try (var paths = Files.walk(temp)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            } catch (Throwable cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
        }
        if (!log.isEmpty()) Files.writeString(report.resolve("renderer.log"), log);
        if (failure != null) {
            Files.deleteIfExists(report.resolve("PASS")); Files.deleteIfExists(report.resolve("STAGED_PASS"));
            evidence.put("result", "FAIL"); evidence.put("failure", failure.toString()); writeProperties(report.resolve("failure.properties"), evidence);
            throw new AssertionError("Current-main Meltdowner geometry proof failed", failure);
        }
        boolean stagedResult = evidence.get("mode").startsWith("STAGED");
        evidence.put("result", stagedResult ? "STAGED_PASS" : "PASS"); evidence.put("inputs.before.after.verified", "true");
        evidence.put("closed.fixture.index.sha256", INDEX_SHA);
        boolean published = false;
        try {
            writeProperties(report.resolve("evidence.properties"), evidence);
            var ledger = new StringBuilder(); hashes.forEach((path, digest) -> ledger.append(digest).append("  ").append(path).append('\n'));
            Files.writeString(report.resolve("files.sha256"), ledger); Files.writeString(report.resolve(stagedResult ? "STAGED_PASS" : "PASS"), "2640 cases; 39362112 coordinates; current-main fifty-class whole/origin/SHA binding; seven declared hosts\n");
            System.out.println((stagedResult ? "STAGED_PASS" : "PASS") + " current-main-resource Meltdowner geometry proof; report=" + report);
            published = true;
        } finally {
            if (!published) for (String name : List.of("PASS", "STAGED_PASS", "evidence.properties", "files.sha256"))
                Files.deleteIfExists(report.resolve(name));
        }
    }
}

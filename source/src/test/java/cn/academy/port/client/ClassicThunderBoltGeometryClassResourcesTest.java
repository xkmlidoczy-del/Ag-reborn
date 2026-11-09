package cn.academy.port.client;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.tools.ToolProvider;

/** Current compileJava ClassResources, closed declared view host, real cached CPU geometry APIs.
 * No production renderer source or binary snapshot is compiled or embedded as the subject.
 */
public final class ClassicThunderBoltGeometryClassResourcesTest {
    private static final String PREFIX = "classic-oracles/thunderbolt-camera/";
    private static final String INDEX_SHA = "8999ed4e000f93ab8d846d96028638cc5287f979ad148c65ad4e1ad9d4f64fc5";
    private static final String PACKAGE = "cn.academy.port.client.";
    private static final String HARNESS = PACKAGE + "ThunderBoltCurrentMainGeometryHarness";
    private static final List<String> CLASSES = List.of(
        PACKAGE + "ClassicThunderBoltEffects", PACKAGE + "ClassicThunderBoltEffects$Arc", PACKAGE + "ClassicThunderBoltEffects$FollowingSound",
        PACKAGE + "ClassicArcGeometry", PACKAGE + "ClassicArcGeometry$Point", PACKAGE + "ClassicArcGeometry$Segment",
        PACKAGE + "ClassicArcGeometry$Quad", PACKAGE + "ClassicArcGeometry$PatternPaths",
        PACKAGE + "ClassicThunderBoltTimeline", PACKAGE + "ClassicThunderBoltTimeline$ViewOffset");
    private static final List<String> API = List.of("net.minecraft.world.phys.Vec3", "com.mojang.blaze3d.vertex.PoseStack",
        "com.mojang.blaze3d.vertex.VertexConsumer", "org.joml.Matrix4f");

    private static void require(boolean result, String message) { if (!result) throw new AssertionError(message); }
    private static String required(String key) {
        String value = System.getProperty("academy.thunderbolt." + key);
        require(value != null && !value.isBlank(), "Missing actual-main admission: " + key); return value;
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static byte[] resource(String path) throws Exception {
        try (var in = ClassicThunderBoltGeometryClassResourcesTest.class.getResourceAsStream("/" + PREFIX + path)) {
            require(in != null, "Missing closed geometry resource: " + path); return in.readAllBytes();
        }
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
        try (var files = Files.list(root.resolve("cn/academy/port/client"))) {
            files.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".class"))
                .filter(n -> n.matches("(?:ClassicThunderBoltEffects|ClassicThunderBoltTimeline|ClassicArcGeometry)(?:\\$[^/]+)?\\.class"))
                .forEach(result::add);
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
            snapshot(hashes, file); inventory.add(file.getFileName().toString());
            evidence.put("main.class.resource." + name, url.toExternalForm());
        }
        require(classInventory(actualMain).equals(inventory), "Changed actual-main ten-class geometry closure");
    }
    private static final class GeometryLoader extends URLClassLoader {
        private final Map<String, Path> bound;
        private final Map<Path, String> admitted;
        GeometryLoader(URL[] librariesAndFixtures, Map<String, Path> bound, Map<Path, String> admitted) {
            super(librariesAndFixtures, ClassLoader.getPlatformClassLoader()); this.bound = bound; this.admitted = admitted;
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
                    if (name.startsWith("cn.academy.port.") && !name.equals(HARNESS))
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
    }
    private static void verifyHost(GeometryLoader loader, Path fixtureClasses) throws Exception {
        Class<?> host = Class.forName("net.minecraft.client.Minecraft", true, loader);
        require(Path.of(host.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(fixtureClasses.toRealPath()),
            "Minecraft view host origin mismatch");
        Object instance = host.getMethod("getInstance").invoke(null);
        require(instance != null && host.getField("player").get(instance) == null && host.getField("options").get(instance) == null,
            "Declared Minecraft view host must contain null player/options");
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
            snapshot(hashes, row.getValue());
            evidence.put("subject.class.resource." + row.getKey(), resource.toExternalForm());
            evidence.put("subject.class.code.source." + row.getKey(), codeSource.toString());
            evidence.put("subject.class.sha256." + row.getKey(), hashes.get(row.getValue()));
        }
        for (String name : API) {
            Class<?> type = Class.forName(name, false, loader);
            Path jar = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            require(Files.isRegularFile(jar), "Official geometry API did not come from its archive: " + name);
            require(sha(Files.readAllBytes(jar)).equals(guards.getProperty(name.startsWith("org.joml.") ? "joml.jar.sha256" : "minecraft.jar.sha256")),
                "Official geometry API archive changed: " + name);
            try (var in = type.getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
                require(in != null && sha(in.readAllBytes()).equals(guards.getProperty("api." + name)), "Official geometry API class changed: " + name);
            }
            snapshot(hashes, jar); evidence.put("api.origin." + name, jar.toString());
        }
    }
    private static void writeProperties(Path file, Map<String, String> map) throws Exception {
        var properties = new Properties(); properties.putAll(map);
        try (var out = Files.newOutputStream(file)) { properties.store(out, "Current-main ThunderBolt CPU geometry proof"); }
    }
    public static void main(String[] args) throws Exception {
        Path report = Path.of(required("report")).toAbsolutePath().normalize(); Files.createDirectories(report);
        for (String name : List.of("PASS", "STAGED_PASS", "evidence.properties", "failure.properties", "files.sha256", "renderer.log", "near-vertices.bin"))
            Files.deleteIfExists(report.resolve(name));
        Map<Path, String> hashes = new TreeMap<>(); Map<String, String> evidence = new TreeMap<>();
        Map<String, String> fixtureHashes = new TreeMap<>();
        Path temp = Files.createTempDirectory("academy-thunderbolt-current-main-");
        Throwable failure = null; String log = "";
        try {
            Path project = Path.of(required("project.dir")).toRealPath();
            Path actualMain = Path.of(required("main.classes")).toRealPath();
            Path mainResources = Path.of(required("main.resources")).toRealPath();
            require(actualMain.startsWith(project) && !actualMain.toString().contains("/.staging/"),
                "Actual main binding must be the admitted compileJava output");
            Path staged = null;
            if (System.getProperty("academy.thunderbolt.staged.renderer.classes") != null) {
                require("true".equals(required("staged.verification")), "Staged subject needs explicit staged verification");
                staged = Path.of(required("staged.renderer.classes")).toRealPath();
                require(staged.toString().contains("/.staging/") && !staged.equals(actualMain), "Staged renderer must remain distinct from actual main");
            }
            String mode = staged == null ? "ACTUAL_MAIN" : "STAGED_RENDERER_WITH_ACTUAL_MAIN_GEOMETRY";
            evidence.put("mode", mode);
            evidence.put("scope", "48 third-person CPU renderer cases; actual compileJava ClassResources/current timeline and geometry; declared null-player Minecraft view host; actual cached Vec3/PoseStack/VertexConsumer/JOML. No first-person runtime, event execution, game/bootstrap, GPU, sound, original procedural/full-source visual parity.");
            byte[] index = resource("files.sha256"); require(sha(index).equals(INDEX_SHA), "Changed closed geometry fixture index");
            fixtureHashes.put("files.sha256", INDEX_SHA);
            var fixtureSources = new ArrayList<Path>(); var seen = new HashSet<String>(); Properties guards = new Properties();
            int witnesses = 0;
            for (String line : new String(index, StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank()) continue; String[] row = line.split("  ", 2);
                require(row.length == 2 && row[0].matches("[a-f0-9]{64}") && seen.add(row[1])
                    && !row[1].contains("..") && !row[1].startsWith("/"), "Unsafe/duplicate closed geometry fixture index");
                byte[] bytes = resource(row[1]); require(sha(bytes).equals(row[0]), "Changed closed geometry fixture: " + row[1]);
                fixtureHashes.put(row[1], row[0]);
                if (row[1].equals("guards.properties")) guards.load(new ByteArrayInputStream(bytes));
                else if (row[1].startsWith("hosts/") || row[1].startsWith("harness/")) {
                    require(row[1].endsWith(".java.txt"), "Only declared host/harness Java is compiled");
                    Path source = temp.resolve("src").resolve(row[1].substring(0, row[1].length() - 4)).normalize();
                    require(source.startsWith(temp.resolve("src")), "Unsafe extracted geometry source");
                    Files.createDirectories(source.getParent()); Files.write(source, bytes); fixtureSources.add(source);
                } else { require(row[1].startsWith("witnesses/"), "Undeclared fixture kind"); witnesses++; }
            }
            require(seen.size() == 8 && fixtureSources.size() == 2 && witnesses == 5, "Incomplete portable geometry closure");
            Path effectsSource = project.resolve("src/main/java/cn/academy/port/client/ClassicThunderBoltEffects.java").toRealPath();
            String effects = Files.readString(effectsSource);
            require(sha(Files.readAllBytes(effectsSource)).equals(required("source.effects.sha256")), "Effects source changed since task admission");
            snapshot(hashes, effectsSource);
            for (int i = 0; i < Integer.parseInt(guards.getProperty("method.count")); i++)
                require(sha(region(effects, guards.getProperty("method." + i + ".signature")).getBytes(StandardCharsets.UTF_8))
                    .equals(guards.getProperty("method." + i + ".sha256")), "Protected source method changed: " + i);
            for (int i = 0; i < 2; i++) {
                Path source = project.resolve(guards.getProperty("dependency." + i + ".path")).toRealPath();
                String digest = sha(Files.readAllBytes(source));
                require(digest.equals(required(i == 0 ? "source.geometry.sha256" : "source.timeline.sha256"))
                    && digest.equals(guards.getProperty("dependency." + i + ".sha256")), "Protected animation/math/RNG source changed");
                snapshot(hashes, source);
            }
            String sourceMode = sha(region(effects, "public static void onRenderLevel(").getBytes(StandardCharsets.UTF_8)).equals(guards.getProperty("candidate.event.sha256"))
                && sha(region(effects, "private static void render(").getBytes(StandardCharsets.UTF_8)).equals(guards.getProperty("candidate.render.sha256")) ? "CAMERA_RELATIVE" : "BASELINE";
            if (sourceMode.equals("BASELINE")) require(sha(region(effects, "public static void onRenderLevel(").getBytes(StandardCharsets.UTF_8)).equals(guards.getProperty("baseline.event.sha256"))
                && sha(region(effects, "private static void render(").getBytes(StandardCharsets.UTF_8)).equals(guards.getProperty("baseline.render.sha256")), "Unrecognized renderer/event source changes");
            evidence.put("main.source.mode", sourceMode);
            ClassLoader application = ClassicThunderBoltGeometryClassResourcesTest.class.getClassLoader();
            verifyMainResources(application, actualMain, hashes, evidence);
            for (int i = 0; i < 2; i++) {
                String asset = guards.getProperty("asset." + i + ".path"), digest = guards.getProperty("asset." + i + ".sha256");
                Path source = project.resolve("src/main/resources").resolve(asset).toRealPath();
                Path processed = mainResources.resolve(asset).toRealPath(); URL url = application.getResource(asset);
                require(url != null && url.getProtocol().equals("file") && Path.of(url.toURI()).toRealPath().equals(processed), "Runtime asset origin mismatch: " + asset);
                require(sha(Files.readAllBytes(source)).equals(digest) && sha(Files.readAllBytes(processed)).equals(digest), "Original runtime asset bytes changed: " + asset);
                snapshot(hashes, source); snapshot(hashes, processed); evidence.put("asset.sha256." + asset, digest);
            }
            Map<String, Path> bound = new LinkedHashMap<>();
            for (String name : CLASSES) {
                Path root = staged != null && name.startsWith(PACKAGE + "ClassicThunderBoltEffects") ? staged : actualMain;
                Path file = root.resolve(name.replace('.', '/') + ".class").toRealPath(); snapshot(hashes, file);
                if (root.equals(staged)) require(hashes.get(file).equals(required("staged.sha256." + name)), "Staged renderer bytes changed since explicit admission: " + name);
                bound.put(name, file);
            }
            Path fixtureClasses = temp.resolve("fixture-classes"); Files.createDirectories(fixtureClasses);
            var compile = new ArrayList<String>(List.of("-proc:none", "-encoding", "UTF-8", "-cp", System.getProperty("java.class.path"), "-d", fixtureClasses.toString()));
            fixtureSources.forEach(p -> compile.add(p.toString()));
            var compiler = ToolProvider.getSystemJavaCompiler(); require(compiler != null && compiler.run(null, System.out, System.err, compile.toArray(String[]::new)) == 0,
                "Declared geometry host/harness compile failed");
            var urls = new ArrayList<URL>(); urls.add(fixtureClasses.toUri().toURL());
            for (String path : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
                Path jar = Path.of(path).toRealPath(); if (Files.isRegularFile(jar) && path.endsWith(".jar")) urls.add(jar.toUri().toURL());
            }
            try (var loader = new GeometryLoader(urls.toArray(URL[]::new), bound, hashes)) {
                verifyHost(loader, fixtureClasses); verifySubject(loader, bound, hashes, guards, evidence);
                var captured = new ByteArrayOutputStream(); PrintStream prior = System.out;
                try (var output = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
                    System.setOut(output);
                    try { Class.forName(HARNESS, true, loader).getMethod("main", String[].class).invoke(null,
                        (Object) new String[]{report.resolve("near-vertices.bin").toString()}); }
                    catch (InvocationTargetException invocation) { failure = invocation.getCause(); }
                    finally { System.setOut(prior); log = captured.toString(StandardCharsets.UTF_8); prior.print(log); }
                }
                verifyHost(loader, fixtureClasses); verifySubject(loader, bound, hashes, guards, evidence);
                verifyMainResources(application, actualMain, hashes, evidence);
                require(log.contains("cases=48 nearVertices=4848 coordinateComparisons=58176"), "Incomplete actual renderer case/vertex/coordinate coverage");
                require(sha(Files.readAllBytes(report.resolve("near-vertices.bin"))).equals(guards.getProperty("near.sha256")), "Near-origin mesh/UV/color/alpha golden hash changed");
                if (failure == null && staged == null) require(sourceMode.equals("CAMERA_RELATIVE"), "Source event/main-bytecode binding is still baseline");
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
            throw new AssertionError("Current-main ThunderBolt geometry proof failed", failure);
        }
        boolean stagedResult = evidence.get("mode").startsWith("STAGED");
        evidence.put("result", stagedResult ? "STAGED_PASS" : "PASS"); evidence.put("inputs.before.after.verified", "true");
        evidence.put("closed.fixture.index.sha256", INDEX_SHA); evidence.put("near.sha256", sha(Files.readAllBytes(report.resolve("near-vertices.bin"))));
        boolean published = false;
        try {
            writeProperties(report.resolve("evidence.properties"), evidence);
            var ledger = new StringBuilder(); hashes.forEach((path, digest) -> ledger.append(digest).append("  ").append(path).append('\n'));
            Files.writeString(report.resolve("files.sha256"), ledger); Files.writeString(report.resolve(stagedResult ? "STAGED_PASS" : "PASS"), "48 cases; 58176 coordinates; current-main ten-class origin/SHA binding; declared null-player view host\n");
            System.out.println((stagedResult ? "STAGED_PASS" : "PASS") + " current-main-resource ThunderBolt geometry proof; report=" + report);
            published = true;
        } finally {
            if (!published) for (String name : List.of("PASS", "STAGED_PASS", "evidence.properties", "files.sha256"))
                Files.deleteIfExists(report.resolve(name));
        }
    }
}

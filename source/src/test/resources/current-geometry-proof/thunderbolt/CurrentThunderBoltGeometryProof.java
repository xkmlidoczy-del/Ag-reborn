import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Version 1 current-contract execution. Historical admission remains a separate, failing task. */
public final class CurrentThunderBoltGeometryProof {
    static final String SUBJECT = "cn.academy.port.client.ClassicThunderBoltEffects";
    static final String HARNESS = "cn.academy.port.client.ThunderBoltCurrentMainGeometryHarness";
    static final String GOLDEN = "56d71d97bfb8b4a0203821f24edb2068620944a1d27906274e3aa5fb4f7394e9";
    static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static String sha(byte[] b) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
    record Entry(String name, Path origin, String digest, String member) {
        URL url(boolean clazz) throws Exception {
            String member = this.member;
            return Files.isDirectory(origin) ? origin.resolve(member).toUri().toURL()
                : new URI("jar:" + origin.toUri() + "!/" + member).toURL();
        }
        byte[] bytes(boolean clazz) throws Exception { try(var stream = url(clazz).openStream()) { return stream.readAllBytes(); } }
    }
    static final class ClosedLoader extends URLClassLoader {
        final Map<String, Entry> entries = new TreeMap<>();
        final Map<String, List<Entry>> resources = new TreeMap<>();
        final Set<String> absent = new TreeSet<>(), violations = new TreeSet<>();
        final Map<String,String> loaded = new TreeMap<>();
        ClosedLoader(Path config, Path pins, String mode) throws Exception {
            super(new URL[0], ClassLoader.getPlatformClassLoader());
            for (String line : Files.readAllLines(config)) {
                String[] row = line.split("\t", -1);
                if (row[0].equals("absent")) { require(row.length == 2 && absent.add(row[1]), "DUPLICATE_ABSENT"); continue; }
                require(row.length == 5, "BAD_ADMISSION_ROW");
                Entry e = new Entry(row[1], Path.of(row[2]).toRealPath(), row[3], row[4]);
                require(e.digest.matches("[a-f0-9]{64}"), "BAD_DIGEST");
                if (row[0].equals("class")) require(entries.putIfAbsent(e.name, e) == null, "DUPLICATE_CLASS_ADMISSION " + e.name);
                else if (row[0].equals("resource")) resources.computeIfAbsent(e.name, ignored -> new ArrayList<>()).add(e);
                else throw new AssertionError("UNKNOWN_ADMISSION_KIND");
            }
            var expected = new TreeMap<String, String[]>();
            for (String line : Files.readAllLines(pins)) {
                String[] row = line.split("\\t", -1);
                if (row[0].equals("class")) require(expected.putIfAbsent(row[1], row) == null, "DUPLICATE_PIN");
            }
            require(entries.keySet().equals(expected.keySet()), "UNKNOWN_OR_MISSING_DEPENDENCY");
            require(Files.readAllLines(config).stream().filter(line -> !line.startsWith("class\t")).sorted().toList()
                .equals(Files.readAllLines(pins).stream().filter(line -> !line.startsWith("class\t")).sorted().toList()),
                "WRONG_RESOURCE_OR_OPTIONAL_DEPENDENCY_ADMISSION");
            for (Entry e : entries.values()) {
                String[] pin = expected.get(e.name);
                boolean mutant = mode.matches("NEGATIVE_CONTROL:(coordinate|uv|rng|camera)") && e.name.equals(SUBJECT);
                if (!mutant) {
                    require(e.origin.equals(Path.of(pin[2]).toRealPath()), "WRONG_DEPENDENCY_ORIGIN " + e.name);
                    require(e.digest.equals(pin[3]) && e.member.equals(pin[4]), "WRONG_DEPENDENCY_PIN " + e.name);
                }
            }
            for (Entry e : entries.values()) require(sha(e.bytes(true)).equals(e.digest), "STALE_OR_WRONG_CLASS " + e.name);
            for (var list : resources.values()) for (Entry e : list)
                require(sha(e.bytes(false)).equals(e.digest), "STALE_OR_WRONG_RESOURCE " + e.name);
        }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    try { type = getParent().loadClass(name); }
                    catch (ClassNotFoundException expectedNonJdk) {
                        Entry entry = entries.get(name);
                        if (entry == null) {
                            if (!absent.contains(name)) violations.add("UNKNOWN_DEPENDENCY " + name);
                            throw new ClassNotFoundException("UNKNOWN_DEPENDENCY " + name);
                        }
                        try {
                            byte[] bytes = entry.bytes(true);
                            require(sha(bytes).equals(entry.digest), "STALE_OR_WRONG_CLASS " + name);
                            type = defineClass(name, bytes, 0, bytes.length,
                                new CodeSource(entry.origin.toUri().toURL(), (java.security.cert.Certificate[]) null));
                            Path realOrigin = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
                            require(realOrigin.equals(entry.origin), "WRONG_CODESOURCE " + name);
                            URL classResource = type.getResource("/" + name.replace('.', '/') + ".class");
                            require(classResource != null && classResource.equals(entry.url(true)), "WRONG_CLASSRESOURCE " + name);
                            require(Collections.list(getResources(name.replace('.', '/') + ".class")).equals(List.of(classResource)),
                                "DUPLICATE_CLASSRESOURCE " + name);
                            loaded.put(name, entry.origin + "\t" + entry.digest + "\t" + classResource);
                        } catch (Exception problem) { throw new ClassNotFoundException(name, problem); }
                    }
                }
                if (resolve) resolveClass(type);
                return type;
            }
        }
        @Override public URL getResource(String name) {
            try { var all = getResources(name); return all.hasMoreElements() ? all.nextElement() : null; }
            catch (IOException failure) { throw new IllegalStateException(failure); }
        }
        @Override public Enumeration<URL> getResources(String name) throws IOException {
            try {
                if (name.endsWith(".class")) {
                    Entry e = entries.get(name.substring(0, name.length()-6).replace('/', '.'));
                    if (e != null) return Collections.enumeration(List.of(e.url(true)));
                    return getParent().getResources(name);
                }
                var urls = new ArrayList<URL>();
                for (Entry e : resources.getOrDefault(name, List.of())) {
                    require(sha(e.bytes(false)).equals(e.digest), "RESOURCE_CHANGED " + name); urls.add(e.url(false));
                }
                // Nonexistent service/config resources stay absent, independent of ambient classpaths.
                return Collections.enumeration(urls);
            } catch (Exception failure) { throw new IOException(failure); }
        }
        @Override protected String findLibrary(String libname) { throw new AssertionError("NATIVE_LIBRARY_FORBIDDEN " + libname); }
        void assertClosed() { require(violations.isEmpty(), "UNDECLARED_LOAD_ATTEMPTS " + violations); }
    }
    static void exactSignatures(ClosedLoader loader) throws Exception {
        Class<?> effect = Class.forName(SUBJECT, false, loader), arc = Class.forName(SUBJECT + "$Arc", false, loader);
        Class<?> vec = Class.forName("net.minecraft.world.phys.Vec3", false, loader);
        Class<?> matrix = Class.forName("org.joml.Matrix4f", false, loader);
        Class<?> vertex = Class.forName("com.mojang.blaze3d.vertex.VertexConsumer", false, loader);
        Method render = effect.getDeclaredMethod("render", arc, vec, matrix, vertex);
        require(render.getReturnType() == void.class && Modifier.isPrivate(render.getModifiers()) && Modifier.isStatic(render.getModifiers()), "WRONG_RENDER_SIGNATURE");
        require(Arrays.stream(effect.getDeclaredMethods()).filter(m -> m.getName().equals("render")).count() == 1, "AMBIGUOUS_RENDER_SIGNATURE");
        Method put = effect.getDeclaredMethod("vertex", vertex, matrix, vec, float.class, float.class, int.class);
        require(put.getReturnType() == void.class && Modifier.isPrivate(put.getModifiers()) && Modifier.isStatic(put.getModifiers()), "WRONG_VERTEX_SIGNATURE");
        require(Arrays.stream(effect.getDeclaredMethods()).filter(m -> m.getName().equals("vertex")).count() == 1, "AMBIGUOUS_VERTEX_SIGNATURE");
        arc.getDeclaredConstructor(vec, vec, double.class, int.class, boolean.class, int.class);
        require(arc.getDeclaredConstructors().length == 1, "AMBIGUOUS_ARC_CONSTRUCTOR");
    }
    static void host(ClosedLoader loader, Path fixtures) throws Exception {
        Class<?> mc = Class.forName("net.minecraft.client.Minecraft", true, loader);
        require(Path.of(mc.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(fixtures), "WRONG_HOST_ORIGIN");
        Object instance = mc.getMethod("getInstance").invoke(null);
        require(instance != null && mc.getField("player").get(instance) == null && mc.getField("options").get(instance) == null, "WRONG_VIEW_HOST");
    }
    public static void main(String[] args) throws Exception {
        require(args.length == 7, "Need config, actual-main, fixtures, report, mode, probe, pins");
        Path actualMain = Path.of(args[1]).toRealPath(), fixtures = Path.of(args[2]).toRealPath(), report = Path.of(args[3]);
        require(args[4].equals("ACTUAL_MAIN") || args[4].startsWith("NEGATIVE_CONTROL:"), "UNKNOWN_MODE");
        Files.createDirectories(report); String log = "";
        try (var loader = new ClosedLoader(Path.of(args[0]), Path.of(args[6]), args[4])) {
            try {
                int subjects = 0;
                for (Entry e : loader.entries.values()) if (e.name.startsWith("cn.academy.port.") && !e.name.equals(HARNESS)) {
                    require(e.name.matches("cn.academy.port.client.(ClassicThunderBoltEffects|ClassicThunderBoltTimeline|ClassicArcGeometry)(\\$.*)?"), "UNKNOWN_PRODUCTION_DEPENDENCY " + e.name);
                    if (args[4].equals("ACTUAL_MAIN")) require(e.origin.equals(actualMain), "WRONG_SUBJECT_ORIGIN " + e.name);
                    Class.forName(e.name, false, loader); subjects++;
                }
                require(subjects == 10, "WRONG_PRODUCTION_CLOSURE_SIZE " + subjects);
                if (!args[5].equals("-")) Class.forName(args[5], false, loader);
                for (String api : List.of("net.minecraft.world.phys.Vec3", "com.mojang.blaze3d.vertex.PoseStack",
                        "com.mojang.blaze3d.vertex.PoseStack$Pose", "com.mojang.blaze3d.vertex.VertexConsumer",
                        "net.minecraft.client.renderer.RenderType", "net.minecraft.client.renderer.RenderStateShard",
                        "net.minecraft.client.Camera", "net.neoforged.neoforge.client.event.RenderLevelStageEvent",
                        "com.mojang.math.Axis", "org.joml.Matrix4f", "org.joml.Quaternionf"))
                    Class.forName(api, false, loader);
                exactSignatures(loader); host(loader, fixtures);
                var capture = new ByteArrayOutputStream(); PrintStream previous = System.out;
                try (var output = new PrintStream(capture, true, StandardCharsets.UTF_8)) {
                    System.setOut(output);
                    try { Class.forName(HARNESS, true, loader).getMethod("main", String[].class).invoke(null,
                        (Object) new String[]{report.resolve("near-vertices.bin").toString()}); }
                    catch (InvocationTargetException error) { throw new AssertionError("RENDERER_ASSERTION", error.getCause()); }
                    finally { System.setOut(previous); log = capture.toString(StandardCharsets.UTF_8); previous.print(log); }
                }
                host(loader, fixtures); loader.assertClosed();
                require(loader.loaded.keySet().equals(loader.entries.keySet()), "DECLARED_LOADED_CLOSURE_MISMATCH");
                require(log.contains("cases=48 nearVertices=4848 coordinateComparisons=58176 mismatches=0")
                    && log.contains("stateAndAttributeChecks=175728 baseline=false"), "INCOMPLETE_FROZEN_MATRIX");
                require(sha(Files.readAllBytes(report.resolve("near-vertices.bin"))).equals(GOLDEN), "NEAR_GOLDEN_MISMATCH");
                require(args[4].equals("ACTUAL_MAIN"), "MUTANT_SURVIVED " + args[4]);
                System.out.println("CURRENT_CONTRACT_RENDERER_PASS " + args[4] + " loaded=" + loader.loaded.size());
            } finally {
                Files.writeString(report.resolve("renderer.log"), log);
                Files.write(report.resolve("loaded-classes.tsv"), loader.loaded.entrySet().stream().map(e -> e.getKey()+"\t"+e.getValue()).toList());
                Files.write(report.resolve("undeclared-loads.txt"), loader.violations);
            }
        }
    }
}

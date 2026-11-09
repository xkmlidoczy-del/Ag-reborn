package cn.academy.port.gametest;

import cn.academy.port.skill.CurrentCharging;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.SecureClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;

/**
 * Test-only exact original entry loader. Only original CurrentCharging and its own Hold
 * are isolated; every native API, storage, ledger, identity and packet implementation
 * delegates to the actual server loader. Never claims an original whole-game runtime.
 */
final class ChargingM41EntryFixture {
    private static final String SUBJECT = "cn.academy.port.skill.CurrentCharging";
    private static final String ZIP_SHA = "8dd1dc1345cb687347d6a89abae5f30587a3dbd97a3dd31c7377da4e361c602b";
    private static final String SOURCE_SHA = "ea6c88aaa8a6abe6388c93053aa4315d0e839d94ad6e73c5f4278c5a1aaa108d";
    private static final Set<String> ORIGINAL_CLASSES = Set.of(SUBJECT, SUBJECT + "$Hold");
    private ChargingM41EntryFixture() {}

    interface Entry {
        boolean start(ServerPlayer p, long input);
        boolean release(ServerPlayer p);
        boolean release(ServerPlayer p, long input, long token, long epoch);
        boolean active(ServerPlayer p);
        void tick(ServerPlayer p);
        void remove(ServerPlayer p);
    }

    static Entry current() {
        return new Entry() {
            public boolean start(ServerPlayer p, long input) { return CurrentCharging.start(p, input); }
            public boolean release(ServerPlayer p) { return CurrentCharging.release(p); }
            public boolean release(ServerPlayer p, long input, long token, long epoch) { return CurrentCharging.release(p, input, token, epoch); }
            public boolean active(ServerPlayer p) { return CurrentCharging.active(p); }
            public void tick(ServerPlayer p) { CurrentCharging.tick(p); }
            public void remove(ServerPlayer p) { CurrentCharging.remove(p); }
        };
    }

    static Original open() throws Exception {
        String configured = System.getProperty("academy.charging.companion.baseline");
        require(configured != null && !configured.isBlank(), "Missing required academy.charging.companion.baseline; stage exact M41 entry first");
        return new Original(Path.of(configured).toRealPath());
    }

    static final class Original implements Entry, AutoCloseable {
        private final Path root;
        private final Properties provenance = new Properties();
        private final Map<Path, String> snapshots = new LinkedHashMap<>();
        private final Class<?> type;
        Original(Path root) throws Exception {
            this.root = root;
            Path evidence = root.resolve("provenance.properties");
            try (var in = Files.newInputStream(evidence)) { provenance.load(in); }
            snapshot(evidence, sha(Files.readAllBytes(evidence)));
            require(ZIP_SHA.equals(provenance.getProperty("original.zip.sha256")), "Wrong immutable M41 ZIP provenance");
            require(SOURCE_SHA.equals(provenance.getProperty("original.source.sha256")), "Wrong immutable M41 entry provenance");
            snapshot(Path.of(required("original.zip")), ZIP_SHA);
            snapshot(root.resolve("source/cn/academy/port/skill/CurrentCharging.java"), SOURCE_SHA);
            var bound = new LinkedHashMap<String, Path>();
            for (String name : ORIGINAL_CLASSES) {
                Path file = root.resolve("classes").resolve(name.replace('.', '/') + ".class").toRealPath();
                snapshot(file, required("original.class." + name)); bound.put(name, file);
            }
            try (var files = Files.walk(root.resolve("classes"))) {
                Set<String> found = files.filter(Files::isRegularFile)
                        .map(path -> root.resolve("classes").relativize(path).toString().replace('/', '.'))
                        .collect(java.util.stream.Collectors.toSet());
                require(found.equals(Set.of(SUBJECT + ".class", SUBJECT + "$Hold.class")), "Original compiler emitted an undeclared subject class");
            }
            verifyCurrent();
            var loader = new OriginalLoader(CurrentCharging.class.getClassLoader(), root.resolve("classes").toRealPath(), bound, snapshots);
            type = Class.forName(SUBJECT, true, loader);
            for (String name : ORIGINAL_CLASSES) {
                Class<?> loaded = Class.forName(name, false, loader);
                require(loaded.getClassLoader() == loader, "Original entry escaped its isolated class loader: " + name);
                require(Path.of(loaded.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(root.resolve("classes").toRealPath()),
                        "Original entry CodeSource mismatch: " + name);
                require(loaded.getResource("/" + name.replace('.', '/') + ".class").equals(bound.get(name).toUri().toURL()),
                        "Original entry ClassResource mismatch: " + name);
            }
            require(Class.forName("cn.academy.port.highpower.HighPowerCharging", false, loader)
                            == cn.academy.port.highpower.HighPowerCharging.class,
                    "Fixture must retain actual current HighPowerCharging; no fake classic applicability host");
            require(Class.forName("cn.academy.port.core.AbilityProgress", false, loader)
                            == cn.academy.port.core.AbilityProgress.class,
                    "Entry differential uses explicit shared actual current ledger classes");
            System.out.println("Charging entry admission: originalZIP=" + ZIP_SHA + " originalSource=" + SOURCE_SHA
                    + " currentCodeSource=" + CurrentCharging.class.getProtectionDomain().getCodeSource().getLocation()
                    + " baseline=" + root + " scope=entry-only/shared-current-native-dependencies");
        }
        private String required(String key) {
            String value = provenance.getProperty(key); require(value != null && !value.isBlank(), "Missing Charging entry provenance: " + key); return value;
        }
        private void snapshot(Path path, String expected) throws Exception {
            Path file = path.toRealPath(); require(sha(Files.readAllBytes(file)).equals(expected), "Changed Charging entry input: " + file);
            String old = snapshots.putIfAbsent(file, expected); require(old == null || old.equals(expected), "Conflicting Charging entry snapshot: " + file);
        }
        private void verifyCurrent() throws Exception {
            Path main = Path.of(required("current.main.classes")).toRealPath();
            int count = Integer.parseInt(required("current.class.count"));
            require(count >= 8, "Incomplete current entry/enhancement admission");
            var names = new java.util.HashSet<String>();
            for (int i = 0; i < count; i++) {
                String name = required("current.class." + i + ".name");
                require(names.add(name), "Duplicate current class admission");
                String expected = required("current.class." + i + ".sha256");
                Path file = main.resolve(name.replace('.', '/') + ".class").toRealPath(); snapshot(file, expected);
                Class<?> loaded = Class.forName(name, false, CurrentCharging.class.getClassLoader());
                String relative = name.replace('.', '/') + ".class";
                var resources = Collections.list(loaded.getClassLoader().getResources(relative));
                require(resources.size() == 1, "Ambiguous current native Charging class resource: " + name);
                try (var input = loaded.getResourceAsStream("/" + relative)) {
                    require(input != null && sha(input.readAllBytes()).equals(expected), "Runtime Charging bytes differ from admitted compileJava output: " + name);
                }
                require(loaded.getProtectionDomain().getCodeSource() != null, "Missing current native Charging CodeSource: " + name);
            }
            require(names.containsAll(Set.of(SUBJECT, SUBJECT + "$Hold", "cn.academy.port.highpower.HighPowerCharging",
                    "cn.academy.port.highpower.HighPowerCharging$Session", "cn.academy.port.highpower.HighPowerChargingEnergy",
                    "cn.academy.port.highpower.HighPowerChargingEnergy$Target", "cn.academy.port.highpower.HighPowerChargingRules",
                    "cn.academy.port.core.CurrentChargingSession", "cn.academy.port.core.AbilityProgress", "cn.academy.port.AbilityConsumption")),
                    "Current admission omits an actual production Charging dependency");
        }
        void verify() throws Exception {
            verifyCurrent();
            for (var entry : snapshots.entrySet()) require(sha(Files.readAllBytes(entry.getKey())).equals(entry.getValue()),
                    "Charging proof input changed during native execution: " + entry.getKey());
        }
        private Object call(String method, Class<?>[] parameters, Object... values) {
            try { return type.getMethod(method, parameters).invoke(null, values); }
            catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof Error error) throw error;
                if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
                throw new AssertionError("Original native entry failed: " + method, failure.getCause());
            } catch (ReflectiveOperationException failure) { throw new AssertionError("Missing original native entry: " + method, failure); }
        }
        public boolean start(ServerPlayer p, long input) { return (boolean) call("start", new Class<?>[]{ServerPlayer.class, long.class}, p, input); }
        public boolean release(ServerPlayer p) { return (boolean) call("release", new Class<?>[]{ServerPlayer.class}, p); }
        public boolean release(ServerPlayer p, long input, long token, long epoch) {
            return (boolean) call("release", new Class<?>[]{ServerPlayer.class, long.class, long.class, long.class}, p, input, token, epoch);
        }
        public boolean active(ServerPlayer p) { return (boolean) call("active", new Class<?>[]{ServerPlayer.class}, p); }
        public void tick(ServerPlayer p) { call("tick", new Class<?>[]{ServerPlayer.class}, p); }
        public void remove(ServerPlayer p) { call("remove", new Class<?>[]{ServerPlayer.class}, p); }
        @Override public void close() throws Exception { verify(); }
    }

    private static final class OriginalLoader extends SecureClassLoader {
        final Path root; final Map<String, Path> bound; final Map<Path, String> hashes;
        OriginalLoader(ClassLoader parent, Path root, Map<String, Path> bound, Map<Path, String> hashes) {
            super(parent); this.root = root; this.bound = bound; this.hashes = hashes;
        }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!bound.containsKey(name)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) try {
                    byte[] bytes = Files.readAllBytes(bound.get(name));
                    require(sha(bytes).equals(hashes.get(bound.get(name))), "Original bytes changed before class definition");
                    loaded = defineClass(name, bytes, 0, bytes.length, new CodeSource(root.toUri().toURL(), (java.security.cert.Certificate[]) null));
                } catch (Exception failure) { throw new ClassNotFoundException(name, failure); }
                if (resolve) resolveClass(loaded); return loaded;
            }
        }
        @Override public URL getResource(String name) {
            String type = name.endsWith(".class") ? name.substring(0, name.length() - 6).replace('/', '.') : "";
            if (bound.containsKey(type)) try { return bound.get(type).toUri().toURL(); }
            catch (IOException failure) { throw new IllegalStateException(failure); }
            return super.getResource(name);
        }
        @Override public Enumeration<URL> getResources(String name) throws IOException {
            String type = name.endsWith(".class") ? name.substring(0, name.length() - 6).replace('/', '.') : "";
            return bound.containsKey(type) ? Collections.enumeration(java.util.List.of(bound.get(type).toUri().toURL())) : super.getResources(name);
        }
    }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}

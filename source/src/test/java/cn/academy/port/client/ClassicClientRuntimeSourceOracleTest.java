package cn.academy.port.client;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import javax.tools.ToolProvider;

/** Executes unchanged classic Java against declared hosts and bytes of the actual main coordinator. */
public final class ClassicClientRuntimeSourceOracleTest {
    private static final String PREFIX = "classic-oracles/client-runtime/";
    private static final String NAME = "cn.academy.port.client.ClassicClientRuntimeCoordinator";
    private static final String CLASS_PATH = NAME.replace('.', '/');
    private static final String INDEX_SHA = "b9674740c2a3fcff38107d75de1da54e9310e0c0bbbff09b8e2e6608f73224b4";
    private static final String GUAVA_SHA = "bc65dea7cfd9e4dacf8419d8af0e741655857d27885bb35d943d7187fc3a8fce";
    private static final List<String> SUFFIXES = List.of("", "$1", "$2", "$3", "$ActivateHandler",
        "$Delegate", "$DelegateNode", "$Inputs", "$KeyState", "$KeyStateSnapshot");

    private static byte[] resource(String path) throws Exception {
        try (var in = ClassicClientRuntimeSourceOracleTest.class.getResourceAsStream("/" + PREFIX + path)) {
            if (in == null) throw new AssertionError("Missing closed oracle resource: " + path);
            return in.readAllBytes();
        }
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String required(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) throw new AssertionError("Missing explicit actual-main binding: " + key);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static java.util.Set<String> coordinatorClassNames(Path actualMain) throws Exception {
        try (var files = Files.list(actualMain.resolve("cn/academy/port/client"))) {
            var names = new java.util.TreeSet<String>();
            files.map(path -> path.getFileName().toString())
                .filter(name -> name.equals("ClassicClientRuntimeCoordinator.class")
                    || name.startsWith("ClassicClientRuntimeCoordinator$") && name.endsWith(".class"))
                .forEach(names::add);
            return names;
        }
    }

    private static void compile(Path output, String classpath, List<Path> sources) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        require(compiler != null, "Source oracle requires an official JDK compiler, not a JRE");
        Files.createDirectories(output);
        var args = new ArrayList<String>(List.of("-proc:none", "-encoding", "UTF-8", "-classpath", classpath,
            "-d", output.toString()));
        sources.forEach(path -> args.add(path.toString()));
        require(compiler.run(null, System.out, System.err, args.toArray(String[]::new)) == 0,
            "Unchanged original/declared-host oracle compiler failed");
    }

    private static String invokeMain(URL[] urls, String className, String[] args) throws Exception {
        var captured = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try (var loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
             var output = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            try {
                loader.loadClass(className).getMethod("main", String[].class).invoke(null, (Object) args);
            } catch (InvocationTargetException failure) {
                throw new AssertionError("Isolated source oracle failed: " + className, failure.getCause());
            } finally {
                System.setOut(originalOut);
                originalOut.print(captured.toString(StandardCharsets.UTF_8));
            }
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        // Invalidate a prior success before checking any input or binding.
        Path report = Path.of(required("academy.runtime.oracle.report")).toAbsolutePath().normalize();
        Files.createDirectories(report);
        Files.deleteIfExists(report.resolve("PASS"));
        Files.deleteIfExists(report.resolve("evidence.properties"));
        Files.deleteIfExists(report.resolve("files.sha256"));
        Files.deleteIfExists(report.resolve("source-oracle.log"));
        Files.deleteIfExists(report.resolve("cold-link.log"));
        Path project = Path.of(required("academy.runtime.project.dir")).toRealPath();
        Path actualMain = Path.of(required("academy.runtime.main.classes")).toRealPath();
        Path source = project.resolve("src/main/java/" + CLASS_PATH + ".java").toRealPath();
        String sourceSha = sha(Files.readAllBytes(source));
        require(sourceSha.equals(required("academy.runtime.main.source.sha256")),
            "Actual main source changed since task admission");
        require(actualMain.startsWith(project) && !actualMain.toString().contains("/.staging/"),
            "Actual main class binding must be the project main compiler output");
        var evidence = new TreeMap<String, String>();
        var hashes = new TreeMap<String, String>();
        evidence.put("boundary", "Unchanged 8 classic Java sources against 32 deterministic hosts; actual main coordinator bytes. Local JVM only; no old engine, modern game bootstrap, native input, transport, GPU or XP/Level observer evidence.");
        evidence.put("java.version", System.getProperty("java.version"));
        evidence.put("java.home", System.getProperty("java.home"));
        evidence.put("main.source", source.toString());
        evidence.put("main.source.sha256", sourceSha);
        evidence.put("main.classes", actualMain.toString());
        hashes.put("main-source/" + CLASS_PATH + ".java", sourceSha);
        Path temp = Files.createTempDirectory("academy-classic-client-runtime-");
        String priorCandidate = System.getProperty("academy.candidate.classes");
        boolean successPublished = false;
        try {
            // This closed resource bundle contains no modern coordinator source or bytecode snapshot.
            byte[] index = resource("files.sha256");
            require(sha(index).equals(INDEX_SHA), "Changed closed original/host/harness/probe resource index");
            hashes.put("resource/files.sha256", INDEX_SHA);
            var sources = new ArrayList<Path>();
            Path coldSource = null;
            int originals = 0, hosts = 0, witnesses = 0, resources = 0, harnesses = 0, probes = 0;
            var seen = new java.util.HashSet<String>();
            for (String line : new String(index, StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank()) continue;
                String[] row = line.split("  ", 2);
                require(row.length == 2 && row[0].matches("[a-f0-9]{64}"), "Malformed closed fixture index");
                String relative = row[1];
                require(seen.add(relative) && !relative.contains("..") && !relative.startsWith("/"),
                    "Unsafe or duplicate closed fixture: " + relative);
                byte[] bytes = resource(relative);
                require(sha(bytes).equals(row[0]), "Changed source/host/runner witness: " + relative);
                hashes.put("resource/" + relative, row[0]);
                ++resources;
                if (relative.startsWith("original/")) ++originals;
                else if (relative.startsWith("hosts/")) ++hosts;
                else if (relative.startsWith("witnesses/")) ++witnesses;
                else if (relative.startsWith("harness/")) ++harnesses;
                else if (relative.startsWith("tools/")) ++probes;
                else require(relative.startsWith("archive/"), "Undeclared fixture kind: " + relative);
                if (relative.startsWith("original/") || relative.startsWith("hosts/")
                    || relative.startsWith("harness/") || relative.startsWith("tools/")) {
                    require(relative.endsWith(".java.txt"), "Only Java is compiled in the source host oracle");
                    Path copied = temp.resolve("src").resolve(relative.substring(0, relative.length() - 4));
                    require(copied.normalize().startsWith(temp.resolve("src")), "Unsafe extracted Java path");
                    Files.createDirectories(copied.getParent());
                    Files.write(copied, bytes);
                    if (relative.startsWith("tools/")) coldSource = copied;
                    else sources.add(copied);
                }
            }
            require(resources == 49 && originals == 8 && hosts == 32 && witnesses == 3
                && harnesses == 1 && probes == 1 && sources.size() == 41 && coldSource != null,
                "Incomplete unchanged-source/host/runner closure");

            // Read each real main class through the same application loader, verify both origins and bytes.
            ClassLoader application = ClassicClientRuntimeSourceOracleTest.class.getClassLoader();
            var expectedNames = new java.util.TreeSet<String>();
            var mainBytes = new LinkedHashMap<String, byte[]>();
            Path candidateBytes = temp.resolve("actual-main-bytes");
            for (String suffix : SUFFIXES) {
                String name = NAME + suffix, path = CLASS_PATH + suffix + ".class";
                expectedNames.add("ClassicClientRuntimeCoordinator" + suffix + ".class");
                Class<?> type = Class.forName(name, false, application);
                Path codeSource = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
                require(codeSource.equals(actualMain), "Actual main class CodeSource mismatch: " + name);
                URL url = type.getResource("/" + path);
                require(url != null && url.getProtocol().equals("file")
                    && Path.of(url.toURI()).toRealPath().equals(actualMain.resolve(path).toRealPath()),
                    "Actual main class-resource origin mismatch: " + name);
                var locations = java.util.Collections.list(application.getResources(path));
                require(locations.size() == 1 && locations.getFirst().equals(url),
                    "Ambiguous actual main class resources: " + name + " " + locations);
                byte[] bytes;
                try (var in = url.openStream()) { bytes = in.readAllBytes(); }
                require(java.util.Arrays.equals(bytes, Files.readAllBytes(actualMain.resolve(path))),
                    "Actual main class bytes changed during extraction: " + name);
                mainBytes.put(path, bytes);
                hashes.put("actual-main-class/" + path, sha(bytes));
                evidence.put("class.resource." + name, url.toExternalForm());
                evidence.put("class.code.source." + name, codeSource.toString());
                Path copied = candidateBytes.resolve(path);
                Files.createDirectories(copied.getParent());
                Files.write(copied, bytes);
            }
            require(coordinatorClassNames(actualMain).equals(expectedNames), "Changed 10-class main coordinator closure");
            Class<?> guavaType = Class.forName("com.google.common.collect.ArrayListMultimap", false, application);
            Path guava = Path.of(guavaType.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            require(Files.isRegularFile(guava) && sha(Files.readAllBytes(guava)).equals(GUAVA_SHA),
                "Wrong official Guava 32.1.2-jre boundary");
            evidence.put("guava", guava.toString());
            hashes.put("toolchain/guava-32.1.2-jre.jar", GUAVA_SHA);

            Path coldClasses = temp.resolve("cold-tool-classes"), oracleClasses = temp.resolve("source-host-classes");
            compile(coldClasses, "", List.of(coldSource));
            String coldLog = invokeMain(new URL[]{coldClasses.toUri().toURL()}, "CandidateColdLinkProbe",
                new String[]{candidateBytes.toString(), guava.toString()});
            Files.writeString(report.resolve("cold-link.log"), coldLog, StandardCharsets.UTF_8);
            require(coldLog.contains("PASS candidate cold link: 10 classes, JDK+Guava-only loader")
                && coldLog.contains("zero denied engine namespace loads"), "Missing 10-class engine-denying linkage result");
            compile(oracleClasses, candidateBytes + java.io.File.pathSeparator + guava, sources);
            System.setProperty("academy.candidate.classes", candidateBytes.toString());
            String oracleLog = invokeMain(new URL[]{oracleClasses.toUri().toURL(), candidateBytes.toUri().toURL(),
                guava.toUri().toURL()}, "cn.academy.port.client.ClientRuntimeSourceOracle", new String[0]);
            Files.writeString(report.resolve("source-oracle.log"), oracleLog, StandardCharsets.UTF_8);
            require(oracleLog.contains("PASS ClientRuntime source oracle: 25 scenarios, 2236 assertions"),
                "Missing exact unchanged-source differential result");

            // Fail closed if the current source or class resources changed while the oracle was running.
            require(sourceSha.equals(sha(Files.readAllBytes(source))), "Actual main source changed during oracle");
            for (var row : mainBytes.entrySet()) require(java.util.Arrays.equals(row.getValue(),
                Files.readAllBytes(actualMain.resolve(row.getKey()))), "Actual main class changed during oracle: " + row.getKey());
            require(coordinatorClassNames(actualMain).equals(expectedNames), "Actual main class closure changed during oracle");
            evidence.put("result", "PASS");
            evidence.put("scenario.count", "25");
            evidence.put("assertion.count", "2236");
            evidence.put("cold.class.count", "10");
            evidence.put("denied.engine.namespace.attempts", "0");
            evidence.put("original.java.count", "8");
            evidence.put("declared.host.java.count", "32");
            evidence.put("hashed.copied.resource.count", "49");
            var properties = new Properties();
            properties.putAll(evidence);
            try (var out = Files.newOutputStream(report.resolve("evidence.properties"))) {
                properties.store(out, "Actual-main class-resource and closed original-source/host oracle evidence");
            }
            var ledger = new StringBuilder();
            hashes.forEach((path, hash) -> ledger.append(hash).append("  ").append(path).append('\n'));
            Files.writeString(report.resolve("files.sha256"), ledger, StandardCharsets.UTF_8);
            Files.writeString(report.resolve("PASS"), "25 scenarios; 2236 assertions; 10 actual main class resources; zero denied engine namespace attempts\n", StandardCharsets.UTF_8);
            System.out.println("PASS formal ClientRuntime source oracle: 8 original Java, 32 hosts, 25 scenarios, 2236 assertions; 10 actual main class resources from " + actualMain);
            System.out.println("EVIDENCE " + report + "; source SHA256=" + sourceSha);
            successPublished = true;
        } finally {
            boolean cleanupComplete = false;
            try {
                if (priorCandidate == null) System.clearProperty("academy.candidate.classes");
                else System.setProperty("academy.candidate.classes", priorCandidate);
                try (var paths = Files.walk(temp)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
                cleanupComplete = true;
            } finally {
                if (!successPublished || !cleanupComplete) {
                    Files.deleteIfExists(report.resolve("PASS"));
                    Files.deleteIfExists(report.resolve("evidence.properties"));
                    Files.deleteIfExists(report.resolve("files.sha256"));
                }
            }
        }
    }
}

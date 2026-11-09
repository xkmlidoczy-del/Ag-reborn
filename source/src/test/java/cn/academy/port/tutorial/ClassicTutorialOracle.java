package cn.academy.port.tutorial;

import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;

/**
 * Compiles byte-exact classic sources with explicitly finite dependency stubs in an isolated classloader.
 * State/conditions/module/scheduler bodies are never translated, regex-extracted or imported from the port.
 * This executes classic Java logic, not Forge, networking, NBTS11n's binary encoding or a graphical client.
 */
public final class ClassicTutorialOracle implements AutoCloseable {
    private static final String[] ORIGINALS = {
        "academy/TutorialData.java", "academy/Conditions.java", "academy/Condition.java",
        "academy/ACTutorial.java", "academy/TutorialRegistry.java", "academy/ModuleTutorial.java",
        "academy/ItemTutorial.java", "academy/TutorialActivatedEvent.java", "academy/App.java",
        "academy/AppRegistry.java", "lambdalib/TickScheduler.java", "lambdalib/RandUtils.java"
    };
    private static Path compiled;
    private final URLClassLoader loader;
    private final Object harness;

    public ClassicTutorialOracle(boolean give) throws Exception {
        this(give, "skill_tree,freq_transmitter,media_player,settings");
    }
    public ClassicTutorialOracle(boolean give, String appOrder) throws Exception {
        loader = new URLClassLoader(new java.net.URL[]{compile().toUri().toURL()}, ClassLoader.getPlatformClassLoader());
        Class<?> type = Class.forName("cn.academy.misc.tutorial.ClassicHarness", true, loader);
        harness = type.getConstructor(boolean.class, String.class).newInstance(give, appOrder);
    }
    private static synchronized Path compile() throws Exception {
        if (compiled != null) return compiled;
        TutorialSourceFixtures.verifyAll();
        TutorialOracleBoundaryFixtures.verifyAll();
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new AssertionError("The immutable classic oracle requires a JDK, not a JRE");
        Path work = Files.createTempDirectory("academy-classic-tutorial-oracle-");
        Path sources = work.resolve("source");
        Path classes = work.resolve("classes");
        Path emptyClasspath = work.resolve("empty-classpath");
        Files.createDirectories(classes);
        Files.createDirectories(emptyClasspath);
        var files = new ArrayList<java.io.File>();
        for (String resource : ORIGINALS) {
            byte[] bytes = TutorialSourceFixtures.bytes(resource);
            String text = new String(bytes, StandardCharsets.UTF_8);
            var matcher = Pattern.compile("(?m)^package ([\\w.]+);").matcher(text);
            if (!matcher.find()) throw new AssertionError("Canonical source package absent: " + resource);
            String name = resource.substring(resource.lastIndexOf('/') + 1);
            Path target = sources.resolve(matcher.group(1).replace('.', '/')).resolve(name);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            files.add(target.toFile());
        }
        for (String resource : boundaryResources()) {
            Path target = sources.resolve(resource.substring(resource.indexOf('/') + 1));
            Files.createDirectories(target.getParent());
            Files.write(target, boundaryBytes(resource));
            files.add(target.toFile());
        }
        var diagnostics = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            boolean success = compiler.getTask(null, manager, diagnostics,
                List.of("-proc:none", "-encoding", "UTF-8", "-classpath", emptyClasspath.toString(), "-d", classes.toString()),
                null, manager.getJavaFileObjectsFromFiles(files)).call();
            if (!success) throw new AssertionError("Unchanged classic tutorial sources did not compile: " + diagnostics.getDiagnostics());
        }
        compiled = classes;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (var stream = Files.walk(work)) {
                for (var path : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            } catch (Exception ignored) { /* Temporary test-only source/classes, never user data. */ }
        }));
        return compiled;
    }
    private static List<String> boundaryResources() throws Exception {
        var list = new ArrayList<String>();
        String entries = new String(boundaryBytes("stubs/stub-list.txt"), StandardCharsets.UTF_8);
        for (String line : entries.lines().filter(s -> !s.isBlank()).toList()) list.add("stubs/" + line);
        list.add("bridge/cn/academy/misc/tutorial/ClassicHarness.java");
        return list;
    }
    private static byte[] boundaryBytes(String resource) throws Exception {
        return TutorialOracleBoundaryFixtures.bytes(resource);
    }
    @SuppressWarnings("unchecked") public <T> T call(String name, Class<?>[] arguments, Object... values) throws Exception {
        try {
            return (T) harness.getClass().getMethod(name, arguments).invoke(harness, values);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) throw cause;
            if (exception.getCause() instanceof Error cause) throw cause;
            throw exception;
        }
    }
    public List<String> ids() throws Exception { return call("ids", new Class<?>[0]); }
    public List<String> defaults() throws Exception { return call("defaults", new Class<?>[0]); }
    public Map<String,List<String>> previews() throws Exception { return call("previews", new Class<?>[0]); }
    public List<String> conditionTargets() throws Exception { return call("conditionTargets", new Class<?>[0]); }
    public boolean visible(String id) throws Exception { return call("visible", new Class<?>[]{String.class}, id); }
    public boolean activated(String id) throws Exception { return call("activated", new Class<?>[]{String.class}, id); }
    public Set<String> activatedIds() throws Exception { return call("activatedIds", new Class<?>[0]); }
    public long[] bits() throws Exception { return call("bits", new Class<?>[0]); }
    public boolean dirty() throws Exception { return call("dirty", new Class<?>[0]); }
    public boolean acquired() throws Exception { return call("acquired", new Class<?>[0]); }
    public int misakaID() throws Exception { return call("misakaID", new Class<?>[0]); }
    public boolean record(String item, TutorialState.EventKind event) throws Exception { return record(item,event,0,false); }
    public boolean record(String item, TutorialState.EventKind event, int meta, boolean client) throws Exception {
        return call("record", new Class<?>[]{String.class,String.class,int.class,boolean.class}, item,event.name(),meta,client);
    }
    public void tick() throws Exception { call("tick", new Class<?>[0]); }
    public List<String> log() throws Exception { return call("log", new Class<?>[0]); }
    public List<String> observableLog() throws Exception { return log().stream().filter(s -> !s.startsWith("local:")).toList(); }
    public void clearLog() throws Exception { call("clearLog", new Class<?>[0]); }
    public void changeConfig(boolean give) throws Exception { call("changeConfig", new Class<?>[]{boolean.class},give); }
    public Map<String,Object> save() throws Exception { return call("save", new Class<?>[0]); }
    public void restore(long[] bits, Collection<String> ids, boolean acquired) throws Exception {
        call("restore", new Class<?>[]{long[].class,Collection.class,boolean.class},bits,ids,acquired);
    }
    public void restart() throws Exception { call("restart", new Class<?>[0]); }
    @Override public void close() throws Exception { loader.close(); }
}

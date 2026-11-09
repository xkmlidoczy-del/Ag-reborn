import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Version 1 current-contract execution. Historical admission remains a separate, failing task. */
public final class CurrentMeltdownerGeometryProof {
    static final String SUBJECT = "cn.academy.port.client.ClassicMeltdownerBeamEffects";
    static final String HARNESS = "cn.academy.port.client.MeltdownerCurrentMainGeometryHarness";
    static final String GOLDEN = "335d8736629a8bbb585927086393a787fb9019c87befd1b654c57df8b123482a";
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
                boolean mutant = (mode.matches("NEGATIVE_CONTROL:(coordinate|uv|rng|camera|scope)") && e.name.equals(SUBJECT)) || (mode.equals("NEGATIVE_CONTROL:state") && e.name.equals("cn.academy.port.client.vfx2.MdNearEyeMaterial")) || (mode.equals("NEGATIVE_CONTROL:host-signature") && e.name.equals("net.minecraft.client.Minecraft"));
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

 public static void main(String[] a)throws Exception {
  require(a.length==8,"Need config pins actual-main fixtures report lane mode probe");
  Path main=Path.of(a[2]).toRealPath(),fixtures=Path.of(a[3]).toRealPath(),report=Path.of(a[4]);Files.createDirectories(report);
  String log="";
  try(var loader=new ClosedLoader(Path.of(a[0]),Path.of(a[1]),a[6])){
   try {
    int subjects=0;
    for(Entry e:loader.entries.values())if(e.name.startsWith("cn.academy.port.")&&!e.name.startsWith("cn.academy.port.client.MeltdownerCurrent")){
     require(e.name.matches("cn\\.academy\\.port\\.(ClassicHudConfig|client\\.(ClassicMeltdowner(?:Starter|Beam|Late)(?:Effects|Timeline)|ClassicAdvancedMineRayTimeline|LightShield(?:Vfx2(?:Material|RenderTypes)?|MaterialPreview)|vfx2\\.MdNearEye(?:Client|Material|Visibility))|clientless\\.ClassicLateTrig)(\\$.*)?"),"UNKNOWN_PRODUCTION_DEPENDENCY "+e.name);
     if(a[6].equals("ACTUAL_MAIN"))require(e.origin.equals(main),"WRONG_SUBJECT_ORIGIN "+e.name);
     Class.forName(e.name,false,loader);subjects++;
    }
    require(subjects==69,"EXPLICIT_PRODUCTION_CLOSURE_69");
    if(!a[7].equals("-"))Class.forName(a[7],false,loader);
    String h=a[5].equals("historical")?HARNESS:"cn.academy.port.client.MeltdownerCurrentPathHarness";
    var bytes=new ByteArrayOutputStream();PrintStream previous=System.out;
    try(var out=new PrintStream(bytes,true,StandardCharsets.UTF_8)){
     System.setOut(out);
     try{Class.forName(h,true,loader).getMethod("main",String[].class).invoke(null,(Object)new String[]{a[5]});}
     catch(InvocationTargetException e){throw new AssertionError("RENDERER_ASSERTION",e.getCause());}
     finally{System.setOut(previous);log=bytes.toString(StandardCharsets.UTF_8);previous.print(log);}
    }
    loader.assertClosed();
    if(a[5].equals("historical")){
     require(log.contains("cases=2640 nearVertices=3280176 batches=18792 coordinateComparisons=39362112 mismatches=0"),"FULL_FROZEN_MATRIX_REQUIRED");
     require(log.contains("near.sha256="+GOLDEN),"NEAR_GOLDEN_MISMATCH");
    }else{
     require(log.contains("CURRENT_PATH_PASS scenes=2640"),"FULL_CURRENT_MATRIX_REQUIRED");
     if(!a[5].equals("on"))require(log.contains("requestedMaterialDigest="+GOLDEN),"CURRENT_FALLBACK_GOLDEN_MISMATCH");
     else require(log.contains("selectedDraws=722 eventCases=22"),"CURRENT_EVENT_SCOPE_REQUIRED");
    }
    for(Entry e:loader.entries.values())require(sha(e.bytes(true)).equals(e.digest),"INPUT_CHANGED "+e.name);
    require(a[6].equals("ACTUAL_MAIN"),"MUTANT_SURVIVED "+a[6]);
    System.out.println("CURRENT_MELTDOWNER_PASS lane="+a[5]+" loaded="+loader.loaded.size()+" declared="+loader.entries.size());
   }finally{
    if(loader.loaded.containsKey(HARNESS)){
     Class<?> h=Class.forName(HARNESS,false,loader);StringBuilder summary=new StringBuilder();
     for(String name:List.of("cases","nearVertices","batches","coordinates","mismatches","nearSnapshot")){Field f=h.getDeclaredField(name);f.setAccessible(true);summary.append(name).append('=').append(f.get(null)).append('\n');}
     Files.writeString(report.resolve("matrix.properties"),summary);
    }
    Files.writeString(report.resolve("renderer.log"),log);Files.write(report.resolve("loaded-classes.tsv"),loader.loaded.entrySet().stream().map(e->e.getKey()+"\t"+e.getValue()).toList());Files.write(report.resolve("undeclared-loads.txt"),loader.violations);
   }
  }
 }
}

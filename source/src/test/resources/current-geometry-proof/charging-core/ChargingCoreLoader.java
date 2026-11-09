import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Platform-parent, explicit-entry loader. No application classpath fallback or native calls. */
public final class ChargingCoreLoader extends URLClassLoader {
    record Entry(String name,Path origin,String digest) {
        URL url(boolean clazz)throws Exception {
            String member=clazz?name.replace('.','/')+".class":name;
            return Files.isDirectory(origin)?origin.resolve(member).toUri().toURL():new URI("jar:"+origin.toUri()+"!/"+member).toURL();
        }
        byte[] bytes(boolean clazz)throws Exception {try(var in=url(clazz).openStream()){return in.readAllBytes();}}
    }
    final Map<String,Entry> classes=new TreeMap<>(),resources=new TreeMap<>();
    final Map<String,String> loaded=new TreeMap<>(); final Set<String> violations=new TreeSet<>();
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static String sha(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    ChargingCoreLoader(Path manifest,Path production,Path harness,String mode)throws Exception {
        super(new URL[0],ClassLoader.getPlatformClassLoader());
        for(String line:Files.readAllLines(manifest)){
            String[] row=line.split("\t",-1);require(row.length==4,"BAD_MANIFEST");
            Entry e=new Entry(row[1],Path.of(row[2]).toRealPath(),row[3]);
            require(e.digest.matches("[a-f0-9]{64}"),"BAD_DIGEST");
            if(row[0].equals("class")){
                require(classes.putIfAbsent(e.name,e)==null,"DUPLICATE_CLASS");
                if(e.name.startsWith("cn.academy.port."))require(e.origin.equals(production)
                    ||(mode.startsWith("negative-")&&e.name.equals("cn.academy.port.core.CurrentChargingSession")),"WRONG_PRODUCTION_ORIGIN "+e.name);
                if(e.name.startsWith("proof.charging."))require(e.origin.equals(harness),"WRONG_HARNESS_ORIGIN");
                require(sha(e.bytes(true)).equals(e.digest),"INPUT_CLASS_CHANGED "+e.name);
            } else {
                require(row[0].equals("resource")&&e.name.equals("classic-skills.json"),"UNKNOWN_RESOURCE");
                require(resources.putIfAbsent(e.name,e)==null,"DUPLICATE_RESOURCE");
                require(sha(e.bytes(false)).equals(e.digest),"INPUT_RESOURCE_CHANGED");
            }
        }
    }
    protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
        synchronized(getClassLoadingLock(name)){
            Class<?> type=findLoadedClass(name);
            if(type==null){
                try{type=getParent().loadClass(name);}
                catch(ClassNotFoundException notPlatform){
                    Entry e=classes.get(name);
                    if(e==null){violations.add(name);throw new ClassNotFoundException("UNDECLARED_DEPENDENCY "+name);}
                    try{
                        byte[] bytes=e.bytes(true);require(sha(bytes).equals(e.digest),"CHANGED_CLASS "+name);
                        type=defineClass(name,bytes,0,bytes.length,new CodeSource(e.origin.toUri().toURL(),(java.security.cert.Certificate[])null));
                        require(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(e.origin),"WRONG_CODESOURCE "+name);
                        URL resource=type.getResource("/"+name.replace('.','/')+".class");
                        require(resource!=null&&resource.equals(e.url(true)),"WRONG_CLASS_RESOURCE "+name);
                        require(Collections.list(getResources(name.replace('.','/')+".class")).equals(List.of(resource)),"NONUNIQUE_CLASS_RESOURCE "+name);
                        loaded.put(name,e.origin+"\t"+e.digest+"\t"+resource);
                    }catch(Exception error){throw new ClassNotFoundException(name,error);}
                }
            }
            if(resolve)resolveClass(type);return type;
        }
    }
    public URL getResource(String name){try{var all=getResources(name);return all.hasMoreElements()?all.nextElement():null;}catch(IOException error){throw new UncheckedIOException(error);}}
    public Enumeration<URL> getResources(String name)throws IOException {
        try{
            boolean clazz=name.endsWith(".class");
            Entry e=clazz?classes.get(name.substring(0,name.length()-6).replace('/','.')):resources.get(name);
            if(e==null)return clazz?getParent().getResources(name):Collections.emptyEnumeration();
            require(sha(e.bytes(clazz)).equals(e.digest),"CHANGED_RESOURCE "+name);
            return Collections.enumeration(List.of(e.url(clazz)));
        }catch(Exception error){throw new IOException(error);}
    }
    protected String findLibrary(String name){throw new AssertionError("NATIVE_LIBRARY_FORBIDDEN "+name);}
    public static void main(String[] args)throws Exception {
        require(args.length==5,"manifest production harness output mode");
        require(Set.of("original-m41","actual-compileJava","negative-cp","negative-xp","negative-order",
            "guard-unknown","guard-wrong-origin","guard-duplicate-resource","guard-wrong-digest").contains(args[4]),"UNKNOWN_RUN_MODE");
        Path out=Path.of(args[3]);Files.createDirectories(out);
        try(var loader=new ChargingCoreLoader(Path.of(args[0]),Path.of(args[1]).toRealPath(),Path.of(args[2]).toRealPath(),args[4])){
            try{
                if(args[4].equals("guard-unknown"))Class.forName("cn.academy.port.UndeclaredChargingProofDependency",false,loader);
                String trace=(String)Class.forName("proof.charging.ChargingCoreTrace",true,loader).getMethod("run").invoke(null);
                require(loader.violations.isEmpty(),"UNDECLARED_LOADS "+loader.violations);
                require(Collections.list(loader.getResources("classic-skills.json")).size()==1,"NONUNIQUE_CATALOG_RESOURCE");
                Files.writeString(out.resolve("trace.tsv"),trace);
                System.out.println(args[4]+" trace="+sha(trace.getBytes(java.nio.charset.StandardCharsets.UTF_8))+" loaded="+loader.loaded.size());
            }finally{
                Files.write(out.resolve("loaded-classes.tsv"),loader.loaded.entrySet().stream().map(e->e.getKey()+"\t"+e.getValue()).toList());
                Files.write(out.resolve("undeclared-loads.txt"),loader.violations);
            }
        }
    }
}

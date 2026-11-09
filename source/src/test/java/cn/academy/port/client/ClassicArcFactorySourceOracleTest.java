package cn.academy.port.client;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/** Original ArcFactory and LambdaLib execute unchanged in an isolated legacy platform host. */
public final class ClassicArcFactorySourceOracleTest {
    static long checks, differences;
    static Field field(Class<?> type, String name) throws Exception {
        Field value = type.getDeclaredField(name); value.setAccessible(true); return value;
    }
    static Object get(Object object, String name) throws Exception { return field(object.getClass(),name).get(object); }
    static void same(double expected, double actual) {
        checks++; if (Double.doubleToRawLongBits(expected) != Double.doubleToRawLongBits(actual)) differences++;
    }
    static void integer(int expected,int actual) { checks++; if(expected != actual) differences++; }
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            Path root=Path.of("src/test/resources/classic-oracles/arc-factory-m32");
            var manifest=com.google.gson.JsonParser.parseString(Files.readString(root.resolve("source-witnesses.json"))).getAsJsonObject();
            List<Path> sources=new ArrayList<>();
            for(var item:manifest.getAsJsonArray("files")) {
                var row=item.getAsJsonObject();Path file=root.resolve(row.get("fixture").getAsString());
                String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
                if(!digest.equals(row.get("sha256").getAsString()))throw new AssertionError("Changed original/declared host witness "+file);
                sources.add(file);
            }
            Path originalClasses=Files.createTempDirectory("academy-original-arc-factory-");
            var options=new ArrayList<String>(List.of("-proc:none","-classpath",System.getProperty("java.class.path"),"-d",originalClasses.toString()));
            sources.forEach(file->options.add(file.toString()));
            var compiler=javax.tools.ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new AssertionError("JDK21 compiler required");
            if(compiler.run(null,System.out,System.err,options.toArray(String[]::new))!=0)throw new AssertionError("Unchanged original ArcFactory compilation failed");
            main(new String[]{originalClasses.toString()});return;
        }
        Path compiled = Path.of(args[0]);
        var loader = new URLClassLoader(new URL[]{compiled.toUri().toURL()}, ClassicArcFactorySourceOracleTest.class.getClassLoader()) {
            @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                Class<?> type=findLoadedClass(name);
                if(type==null && Files.isRegularFile(compiled.resolve(name.replace('.','/')+".class"))) type=findClass(name);
                if(type==null) type=super.loadClass(name,false);
                if(resolve) resolveClass(type); return type;
            }
        };
        Class<?> factoryType=loader.loadClass("cn.academy.vanilla.electromaster.client.effect.ArcFactory");
        Random factoryRandom=(Random)field(factoryType,"rand").get(null);
        Random utilityRandom=(Random)loader.loadClass("cn.lambdalib.util.generic.RandUtils").getField("RNG").get(null);
        double[][] cases={{20,6,.1,1.1,.15,.7},{20,5,.08,1.2,.2,.7},{20,5,.1,1.2,.3,.7},
                {20,5,.3,1.4,.3,.7},{20,5,.13,1.2,.28,.7},{2.5,3,.3,.8,.7,.9},{20,6,.1,1.5,.4,.7}};
        List<String> inventory=new ArrayList<>();
        for(int variant=0;variant<cases.length;variant++) for(int seed=0;seed<16;seed++) {
            double[] c=cases[variant]; long rotationSeed=987123L+seed;
            factoryRandom.setSeed(seed); utilityRandom.setSeed(rotationSeed);
            Object factory=factoryType.getConstructor().newInstance();
            for(var parameter:Map.of("width",c[2],"maxOffset",c[3],"branchFactor",c[4],"widthShrink",c[5]).entrySet())
                factoryType.getField(parameter.getKey()).setDouble(factory,parameter.getValue());
            factoryType.getField("passes").setInt(factory,(int)c[1]);
            Object arc=factoryType.getMethod("generate",double.class).invoke(factory,c[0]);
            @SuppressWarnings("unchecked") List<double[]> bakedOriginal=new ArrayList<>((List<double[]>)loader.loadClass("org.lwjgl.opengl.GL11").getField("vertices").get(null));
            @SuppressWarnings("unchecked") List<List<Object>> expected=(List<List<Object>>)get(arc,"segmentList");
            Random modernShape=new Random(seed),modernRotation=new Random(rotationSeed);
            @SuppressWarnings("unchecked")
            List<List<ClassicArcGeometry.Segment>> actual=Arrays.asList(args).contains("--baseline")
                    ? ClassicArcGeometry.generate(modernShape,c[0],(int)c[1],c[2],c[3],c[4],c[5])
                    : (List<List<ClassicArcGeometry.Segment>>)ClassicArcGeometry.class.getDeclaredMethod("generate",Random.class,Random.class,double.class,int.class,double.class,double.class,double.class,double.class)
                        .invoke(null,modernShape,modernRotation,c[0],(int)c[1],c[2],c[3],c[4],c[5]);
            integer(expected.size(),actual.size());
            int segments=0;
            for(int path=0;path<Math.min(expected.size(),actual.size());path++) {
                var ep=expected.get(path);var ap=actual.get(path);integer(ep.size(),ap.size());segments+=ep.size();
                for(int index=0;index<Math.min(ep.size(),ap.size());index++) {
                    Object segment=ep.get(index);var modern=ap.get(index);same((double)get(segment,"alpha"),modern.alpha());
                    for(String end:List.of("start","end")) {
                        Object point=get(segment,end), vector=get(point,"pt");
                        var target=end.equals("start")?modern.start():modern.end();
                        same((double)get(point,"width"),target.width());
                        same((double)get(vector,"xCoord"),target.position().x);
                        same((double)get(vector,"yCoord"),target.position().y);
                        same((double)get(vector,"zCoord"),target.position().z);
                    }
                }
            }
            if(!Arrays.asList(args).contains("--baseline")) {
                @SuppressWarnings("unchecked") List<Object> baked=(List<Object>)ClassicArcGeometry.class.getDeclaredMethod("baked",List.class).invoke(null,actual);
                integer(bakedOriginal.size(),baked.size()*4);
                for(int quad=0;quad<baked.size();quad++) {
                    Object q=baked.get(quad);String[] corners={"p1","p2","p4","p3"};double[][] uv={{0,0},{0,1},{1,1},{1,0}};
                    for(int corner=0;corner<4;corner++) {
                        var v=(net.minecraft.world.phys.Vec3)get(q,corners[corner]);double[] ov=bakedOriginal.get(quad*4+corner);
                        same(ov[0],v.x);same(ov[1],v.y);same(ov[2],v.z);same(ov[3],uv[corner][0]);same(ov[4],uv[corner][1]);same(ov[5],(double)get(q,"alpha"));
                    }
                }
            }
            if(!Arrays.asList(args).contains("--baseline")) {
                for(int r=0;r<16;r++){same(factoryRandom.nextFloat(),modernShape.nextFloat());same(utilityRandom.nextFloat(),modernRotation.nextFloat());}
                @SuppressWarnings("unchecked") List<double[]> recorded=(List<double[]>)loader.loadClass("org.lwjgl.opengl.GL11").getField("vertices").get(null);
                for(double limit:new double[]{0,c[0]/2,c[0]}) {
                    recorded.clear();arc.getClass().getMethod("draw",double.class).invoke(arc,limit);
                    @SuppressWarnings("unchecked") List<Object> dynamic=(List<Object>)ClassicArcGeometry.class.getDeclaredMethod("ribbons",List.class,double.class,net.minecraft.world.phys.Vec3.class,Random.class)
                            .invoke(null,actual,limit,new net.minecraft.world.phys.Vec3(0,0,1),modernRotation);
                    integer(recorded.size(),dynamic.size()*4);
                    for(int quad=0;quad<dynamic.size();quad++) {
                        Object q=dynamic.get(quad);String[] corners={"p1","p2","p4","p3"};double[][] uv={{0,0},{0,1},{1,1},{1,0}};
                        for(int corner=0;corner<4;corner++) {
                            var v=(net.minecraft.world.phys.Vec3)get(q,corners[corner]);double[] ov=recorded.get(quad*4+corner);
                            same(ov[0],v.x);same(ov[1],v.y);same(ov[2],v.z);same(ov[3],uv[corner][0]);same(ov[4],uv[corner][1]);same(ov[5],(double)get(q,"alpha"));
                        }
                    }
                    for(int r=0;r<4;r++)same(utilityRandom.nextFloat(),modernRotation.nextFloat());
                }
            }
            if(seed==0) inventory.add("variant"+variant+": sourcePaths="+expected.size()+", portPaths="+actual.size()+", comparedSourceSegments="+segments);
        }
        System.out.println(String.join("\n",inventory));
        System.out.println("ClassicArcFactorySourceOracleTest: "+checks+" unchanged-source graph/raw-double comparisons; "+differences+" differences");
        if(Arrays.asList(args).contains("--baseline")) {if(differences==0)throw new AssertionError("baseline should expose source differences");}
        else if(differences!=0)throw new AssertionError("source graph differences="+differences);
    }
}

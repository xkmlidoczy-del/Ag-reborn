/* AcademyCraft1.0.7 GPLv3 / LambdaLib1.2.3 MIT executable acceptance witnesses. */
package cn.academy.port.client.terminal;

import com.google.gson.JsonParser;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** No Minecraft, desktop, GL context or native bootstrap. Executes unchanged original entry/draw handlers. */
public final class TerminalAcceptanceProjectionTest {
    private static int assertions;
    private static final String BASE="/classic-terminal-acceptance/";
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private static void equal(double a,double b,double tolerance,String message){check(Math.abs(a-b)<=tolerance,message+" "+a+" != "+b);}
    private static byte[] bytes(String path)throws Exception{try(var input=TerminalAcceptanceProjectionTest.class.getResourceAsStream(BASE+path)){if(input==null)throw new AssertionError("Missing portable witness "+path);return input.readAllBytes();}}
    private static String text(String path)throws Exception{return new String(bytes(path),StandardCharsets.UTF_8);}
    private static String sha(byte[] value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    private static Path root(){return Path.of(System.getProperty("academy.terminal.root",".")).toAbsolutePath();}
    private static String capture(String source,String regex){var matcher=Pattern.compile(regex,Pattern.DOTALL).matcher(source);check(matcher.find(),"Expected unchanged source expression "+regex);return matcher.group(1);}
    private static void write(Path source,String relative,String body,List<String> inputs)throws Exception{Path file=source.resolve(relative);Files.createDirectories(file.getParent());Files.writeString(file,body);inputs.add(file.toString());}
    private static double[] transform(double[] matrix,double[] value){double[] result=new double[4];for(int row=0;row<4;row++)for(int col=0;col<4;col++)result[row]+=matrix[col*4+row]*value[col];return result;}
    private static double[] ndc(double[][] matrices,double x,double y,double z){double[] clip=transform(matrices[1],transform(matrices[0],new double[]{x,y,z,1}));return new double[]{clip[0]/clip[3],clip[1]/clip[3],clip[2]/clip[3]};}
    private static Element widget(Element parent,String name){for(var node=parent.getFirstChild();node!=null;node=node.getNextSibling())if(node instanceof Element element&&element.getTagName().equals("Widget")&&element.getAttribute("name").equals(name))return element;throw new AssertionError("Missing source widget "+name);}
    private static Element component(Element parent,String name){for(var node=parent.getFirstChild();node!=null;node=node.getNextSibling())if(node instanceof Element element&&element.getTagName().equals("Component")&&element.getAttribute("class").endsWith("."+name))return element;throw new AssertionError("Missing source component "+name);}
    private static double value(Element element,String field){return Double.parseDouble(element.getElementsByTagName(field).item(0).getTextContent());}
    public static void main(String[] args)throws Exception{
        check(sha(bytes("source-manifest.json")).equals("69d3a5fa1d5611d8540f3b3447aca4e7d4f04ec19c9e36e3fae00f1bddf23de7"),"Pinned independent acceptance source manifest");
        for(var entry:JsonParser.parseString(text("source-manifest.json")).getAsJsonArray()){var witness=entry.getAsJsonObject();check(sha(bytes(witness.get("name").getAsString())).equals(witness.get("sha256").getAsString()),"Immutable original witness "+witness.get("name"));}
        String modern=Files.readString(root().resolve("src/main/java/cn/academy/port/client/terminal/TerminalHud.java"));
        String client=Files.readString(root().resolve("src/main/java/cn/academy/port/client/terminal/TerminalClient.java"));
        String pose=capture(modern,"Matrix4f pose=(.*?);\\s*graphics\\.pose\\(\\)\\.mulPose\\(pose\\)");
        String projection=capture(modern,"RenderSystem\\.setProjectionMatrix\\((new Matrix4f\\(\\)\\.perspective\\(.*?\\)),VertexSorting\\.DISTANCE_TO_ORIGIN\\)");
        String origin=capture(text("original/TextBox.java.txt"),"(private Vector2d origin\\(\\) \\{.*?\\n    \\})");
        Path temporary=Files.createTempDirectory("academy-terminal-acceptance-");
        try{
            Path sources=temporary.resolve("source"),classes=temporary.resolve("classes");Files.createDirectories(classes);var inputs=new ArrayList<String>();
            for(String path:text("stubs-index.txt").split("\\R"))if(!path.isBlank())write(sources,path,text("stubs/"+path+".txt"),inputs);
            for(var original:Map.of("TerminalUI.java","cn/academy/terminal/client/TerminalUI.java","AppSettings.java","cn/academy/terminal/app/settings/AppSettings.java","App.java","cn/academy/terminal/App.java","AppEnvironment.java","cn/academy/terminal/AppEnvironment.java","AuxGui.java","cn/lambdalib/util/client/auxgui/AuxGui.java","AuxGuiHandler.java","cn/lambdalib/util/client/auxgui/AuxGuiHandler.java").entrySet())write(sources,original.getValue(),text("original/"+original.getKey()+".txt"),inputs);
            write(sources,"probe/ModernProjection.java","package probe;import org.joml.Matrix4f;public class ModernProjection {public static double[][] sample(int width,int height,long now,double buffX,double buffY){float aspect=(float)width/height;Matrix4f pose="+pose+";Matrix4f projection="+projection+";float[] model=pose.get(new float[16]),screen=projection.get(new float[16]);double[][] result=new double[2][16];for(int i=0;i<16;i++){result[0][i]=model[i];result[1][i]=screen[i];}return result;}}",inputs);
            // The exact original private method executes in a finite enclosing field model.
            write(sources,"probe/OriginalOrigin.java","package probe;public class OriginalOrigin {private static class Vector2d {final double x,y;Vector2d(double x,double y){this.x=x;this.y=y;}}private static class Transform{double width,height;}private static class Widget{Transform transform=new Transform();}private static class Align{double lenOffset;}private static class Option{Align align=new Align();double fontSize;}private static class HeightAlign{double factor;}private Widget widget=new Widget();private Option option=new Option();private HeightAlign heightAlign=new HeightAlign();private double xOffset,yOffset;"+origin+"public static double[] sample(double width,double height,double size,double align,double vertical){var instance=new OriginalOrigin();instance.widget.transform.width=width;instance.widget.transform.height=height;instance.option.fontSize=size;instance.option.align.lenOffset=align;instance.heightAlign.factor=vertical;var point=instance.origin();return new double[]{point.x,point.y};}}",inputs);
            var compiler=new ArrayList<>(List.of("-proc:none","--release","21","-encoding","UTF-8","-cp",System.getProperty("java.class.path"),"-d",classes.toString()));compiler.addAll(inputs);
            check(ToolProvider.getSystemJavaCompiler().run(null,null,null,compiler.toArray(String[]::new))==0,"Unchanged original TerminalUI/AppSettings/AuxGui handlers and current JOML chain compile");
            try(var loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},TerminalAcceptanceProjectionTest.class.getClassLoader()){
                @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                    synchronized(getClassLoadingLock(name)){
                        Class<?> found=findLoadedClass(name);
                        if(found==null&&!name.startsWith("java.")&&!name.startsWith("org.joml.")){try{found=findClass(name);}catch(ClassNotFoundException ignored){}}
                        if(found==null)found=super.loadClass(name,false);if(resolve)resolveClass(found);return found;
                    }
                }
            }){
                var probe=loader.loadClass("cn.academy.terminal.client.AcceptanceProbe");probe.getMethod("initialize").invoke(null);
                var originalSample=probe.getMethod("sample",int.class,int.class,long.class,double.class,double.class);
                var modernSample=loader.loadClass("probe.ModernProjection").getMethod("sample",int.class,int.class,long.class,double.class,double.class);
                var random=new Random(741812);
                for(int frame=0;frame<500;frame++){
                    int width=700+random.nextInt(2500),height=500+random.nextInt(1300);long time=random.nextInt(100000);double x=random.nextDouble()*605,y=random.nextDouble()*740;
                    double[][] classic=(double[][])originalSample.invoke(null,width,height,time,x,y),port=(double[][])modernSample.invoke(null,width,height,time,x,y);
                    for(int matrix=0;matrix<2;matrix++)for(int i=0;i<16;i++)equal(classic[matrix][i],port[matrix][i],1e-6,"Original draw matrix/current JOML order");
                    for(int point=0;point<20;point++){double px=random.nextDouble()*640,py=random.nextDouble()*785,pz=random.nextDouble()*40;double[] a=ndc(classic,px,py,pz),b=ndc(port,px,py,pz);for(int axis=0;axis<3;axis++)equal(a[axis],b[axis],2e-6,"Source/port projected widget point");}
                }
                double[][] narrow=(double[][])originalSample.invoke(null,1180,812,0L,150d,150d);
                double[] frame=ndc(narrow,640,0,0),username=ndc(narrow,600,41.66666666666667,15);
                check(frame[0]>1,"Source frame top-right clips at1180x812");check(username[0]>1,"Source username right anchor clips at1180x812");
                equal((frame[0]+1)*590,1215.02504,0.001,"Original frame right X pixels");equal((username[0]+1)*590,1180.22317,0.001,"Original username right X pixels");
                double[][] wide=(double[][])originalSample.invoke(null,1440,810,0L,150d,150d);
                check(ndc(wide,640,0,0)[0]<1&&ndc(wide,600,41.66666666666667,15)[0]<1,"Source anchors fit at16:9");
                System.out.printf(Locale.ROOT,"Source1180x812 frame right %.5fpx, username anchor %.5fpx; source16:9 frame NDC %.8f%n",(frame[0]+1)*590,(username[0]+1)*590,ndc(wide,640,0,0)[0]);
                check((boolean)probe.getMethod("settingsKeepsTerminal").invoke(null),"Unchanged source left-click/AppSettings opens Settings without terminal disposal");
                check((int)probe.getMethod("backgroundDraws",boolean.class).invoke(null,false)==1,"Unchanged AuxGuiHandler still draws TerminalUI behind Settings");
                check((int)probe.getMethod("backgroundDraws",boolean.class).invoke(null,true)==1,"Paused Settings still draws background TerminalUI");
                check((boolean)probe.getMethod("disposeStopsBackground").invoke(null),"Explicit original disposal is the draw stopping condition");
                var originSample=loader.loadClass("probe.OriginalOrigin").getMethod("sample",double.class,double.class,double.class,double.class,double.class);
                var document=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes("original/terminal.xml.txt")));
                Element back=widget(document.getDocumentElement(),"back");
                for(String name:List.of("text_static_0","text_static_1","text_username","text_appcount")){
                    Element w=widget(back,name),transform=component(w,"Transform"),box=component(w,"TextBox");
                    double height=value(transform,"height"),size=value(box,"fontSize"),vertical=box.getElementsByTagName("heightAlign").item(0).getTextContent().equals("CENTER")?.5:0;
                    double[] point=(double[])originSample.invoke(null,value(transform,"width"),height,size,0d,vertical);equal(point[1],0,0,"Source terminal text origin clamps negative center "+name);
                }
                Element appLabel=widget(widget(back,"app_template"),"text");double[] point=(double[])originSample.invoke(null,151d,21d,32d,.5d,.5d);equal(point[1],0,0,"Source app label starts at148 without negative centering");equal(value(component(appLabel,"Transform"),"y"),148,0,"Immutable source app-label Y");
                check(modern.contains("drawing.text(\"TERMINAL\",98,74,40")&&modern.contains("drawing.text(\"DATA\",98,42,40")&&modern.contains("x+75.5,y+148,32"),"Port applies three exact source TextBox origin corrections");
                String activation=capture(client,"(private static void activate\\(String app,CompoundTag data\\).*?)\\n    public static void clear");
                check(activation.contains("case \"urban_communication\" -> mc.setScreen(new UrbanStoryScreen(data))"),"Urban bulletin receives server state");
                check(activation.contains("case \"settings\" -> mc.setScreen(new TerminalSettingsScreen())"),"Modern settings activation retains source screen semantics");
                check(activation.contains("case \"freq_transmitter\" -> {if(terminal!=null){terminal.release();terminal=null;}"),"Only source passOn app disposes/replaces perspective terminal");
                check(client.contains("if(terminal!=null)terminal.render(event.getGuiGraphics())"),"Modern screen retains original AuxGui background draw");
            }
        }finally{try(var paths=Files.walk(temporary)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
        System.out.println("PASS TerminalAcceptanceProjectionTest "+assertions+" assertions; unchanged original terminal draw/settings/lifecycle executed; current JOML chain differential; source text-origin clamp; no game/native startup");
    }
}

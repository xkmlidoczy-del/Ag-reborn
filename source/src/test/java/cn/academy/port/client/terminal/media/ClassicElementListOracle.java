package cn.academy.port.client.terminal.media;

import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;

/** Executes unchanged LambdaLib ElementList.java in a JDK-only classloader with finite widget ownership stubs. */
final class ClassicElementListOracle implements AutoCloseable {
    private final Path work=Files.createTempDirectory("academy-media-element-oracle-");private final URLClassLoader loader;private final Class<?>widgetType,listType;private final Object list;
    ClassicElementListOracle(int count)throws Exception{
        Path src=work.resolve("src"),classes=work.resolve("classes");Files.createDirectories(classes);var sources=new ArrayList<java.io.File>();
        write(src,"cn/lambdalib/cgui/gui/component/ElementList.java",MediaSourceFixtures.bytes("lambdalib/ElementList.java"),sources);
        for(String name:List.of("cn/lambdalib/cgui/gui/Widget.java","cn/lambdalib/cgui/gui/component/Component.java","cn/lambdalib/cgui/gui/event/GuiEvent.java","cn/lambdalib/util/generic/MathUtils.java","com/google/common/collect/ImmutableList.java"))write(src,name,MediaSourceFixtures.bytes("stubs/"+name),sources);
        var compiler=ToolProvider.getSystemJavaCompiler();try(var manager=compiler.getStandardFileManager(null,null,null)){boolean ok=compiler.getTask(null,manager,null,List.of("-proc:none","-classpath",work.resolve("empty").toString(),"-d",classes.toString()),null,manager.getJavaFileObjectsFromFiles(sources)).call();if(!ok)throw new AssertionError("Unchanged ElementList failed to compile");}
        loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},ClassLoader.getPlatformClassLoader());widgetType=loader.loadClass("cn.lambdalib.cgui.gui.Widget");listType=loader.loadClass("cn.lambdalib.cgui.gui.component.ElementList");list=listType.getConstructor().newInstance();
        Object owner=widgetType.getConstructor().newInstance();transform(owner,"height",302d);transform(owner,"width",552d);
        for(int i=0;i<count;i++){Object row=widgetType.getConstructor().newInstance();transform(row,"height",60d);transform(row,"width",554d);listType.getMethod("addWidget",widgetType).invoke(list,row);}listType.getMethod("bind",widgetType).invoke(list,owner);
    }
    private static void write(Path src,String path,byte[]bytes,List<java.io.File>sources)throws Exception{Path p=src.resolve(path);Files.createDirectories(p.getParent());Files.write(p,bytes);sources.add(p.toFile());}
    private void transform(Object widget,String field,double value)throws Exception{Object t=widgetType.getField("transform").get(widget);t.getClass().getField(field).setDouble(t,value);}
    int max()throws Exception{return(int)listType.getMethod("getMaxProgress").invoke(list);}
    int progress(int request)throws Exception{listType.getMethod("setProgress",int.class).invoke(list,request);return(int)listType.getMethod("getProgress").invoke(list);}
    List<Integer>visible()throws Exception{List<?>rows=(List<?>)listType.getMethod("getSubWidgets").invoke(list);List<Integer>out=new ArrayList<>();for(int i=0;i<rows.size();i++){Object t=widgetType.getField("transform").get(rows.get(i));if(t.getClass().getField("doesDraw").getBoolean(t)){out.add(i);if(t.getClass().getField("x").getDouble(t)!=0)throw new AssertionError("Source row X isn't zero");if(t.getClass().getField("y").getDouble(t)!=(out.size()-1)*60d)throw new AssertionError("Source row Y differs");}}return out;}
    @Override public void close()throws Exception{loader.close();try(var all=Files.walk(work)){for(Path p:all.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
}

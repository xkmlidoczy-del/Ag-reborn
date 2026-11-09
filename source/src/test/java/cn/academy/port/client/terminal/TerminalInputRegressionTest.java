/* AcademyCraft1.0.7 GPLv3 executable relative-input witnesses; finite official platform boundaries. */
package cn.academy.port.client.terminal;

import com.google.gson.JsonParser;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;

/** Executes source MouseHelper/TerminalUI and exact official cursor methods without any native/game startup. */
public final class TerminalInputRegressionTest {
    private static int assertions;
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private static void equal(double a,double b,double tolerance,String message){check(Math.abs(a-b)<=tolerance,message+" "+a+" != "+b);}
    private static byte[] bytes(String prefix,String path)throws Exception{try(var in=TerminalInputRegressionTest.class.getResourceAsStream(prefix+path)){if(in==null)throw new AssertionError("Missing portable fixture "+prefix+path);return in.readAllBytes();}}
    private static String old(String path)throws Exception{return new String(bytes("/classic-terminal-acceptance/",path),StandardCharsets.UTF_8);}
    private static String witness(String path)throws Exception{return new String(bytes("/classic-terminal-input/",path),StandardCharsets.UTF_8);}
    private static Path root(){return Path.of(System.getProperty("academy.terminal.root",".")).toAbsolutePath();}
    private static String sha(byte[] value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    private static void write(Path dir,String name,String body,List<String> sources)throws Exception{Path file=dir.resolve(name);Files.createDirectories(file.getParent());Files.writeString(file,body);sources.add(file.toString());}
    private static String between(String source,String start,String stop){int left=source.indexOf(start),right=source.indexOf(stop,left);check(left>=0&&right>left,"Exact actual production input slice");return source.substring(left,right);}
    public static void main(String[] args)throws Exception{
        check(sha(bytes("/classic-terminal-input/","source-manifest.json")).equals("52a4d27a3ff16ed3c2ca9e54f682d941b3df820ff8647c53bf190b03aece234b"),"Pinned input-source manifest");
        var manifest=JsonParser.parseString(witness("source-manifest.json")).getAsJsonObject();
        for(var item:manifest.getAsJsonArray("files")){var file=item.getAsJsonObject();check(sha(bytes("/classic-terminal-input/",file.get("name").getAsString())).equals(file.get("sha256").getAsString()),"Immutable source input witness "+file.get("name"));}
        String hud=Files.readString(root().resolve("src/main/java/cn/academy/port/client/terminal/TerminalHud.java"));
        String client=Files.readString(root().resolve("src/main/java/cn/academy/port/client/terminal/TerminalClient.java"));
        check(client.contains("terminal.captureMouse(mc.mouseHandler.getXVelocity(),mc.mouseHandler.getYVelocity())"),"Capture actual official accumulated motion before turn drains it");
        check(!hud.contains("xpos()-lastX")&&!hud.contains("ypos()-lastY"),"No absolute cursor difference or native rebase ingress");
        check(client.contains("event.setMouseSensitivity(-((double).2F)/.6F)")&&client.contains("event.setCinematicCameraEnabled(false)"),"View-turn suppression remains source override");
        check(client.contains("event.getAction()==GLFW.GLFW_RELEASE)terminal.click()"),"Source left activation stays on release");
        String render=between(hud,"// Classic selection", "if(Boolean.getBoolean(\"academy.visual.qa\")");
        String balanced=between(hud,"buffX=TerminalTimeline.balance(dt", "int selected=TerminalTimeline.selectedIndex");
        Path temp=Files.createTempDirectory("academy-terminal-input-");
        try{
            Path dir=temp.resolve("src"),classes=temp.resolve("classes");Files.createDirectories(classes);var sources=new ArrayList<String>();
            for(String path:old("stubs-index.txt").split("\\R"))if(!path.isBlank()&&!path.equals("cn/academy/terminal/client/TerminalMouseHelper.java")&&!path.equals("cn/academy/terminal/client/AcceptanceProbe.java")){
                String body=old("stubs/"+path+".txt");if(path.equals("net/minecraft/util/MouseHelper.java"))body="package net.minecraft.util;public class MouseHelper {public void mouseXYChange(){}}";
                write(dir,path,body,sources);
            }
            for(var item:Map.of("TerminalUI.java","cn/academy/terminal/client/TerminalUI.java","AppSettings.java","cn/academy/terminal/app/settings/AppSettings.java","App.java","cn/academy/terminal/App.java","AppEnvironment.java","cn/academy/terminal/AppEnvironment.java","AuxGui.java","cn/lambdalib/util/client/auxgui/AuxGui.java","AuxGuiHandler.java","cn/lambdalib/util/client/auxgui/AuxGuiHandler.java").entrySet())write(dir,item.getValue(),old("original/"+item.getKey()+".txt"),sources);
            write(dir,"cn/academy/terminal/client/TerminalMouseHelper.java",witness("TerminalMouseHelper.java.txt"),sources);
            write(dir,"org/lwjgl/input/Mouse.java","package org.lwjgl.input;public class Mouse {public static int dx,dy;public static int getDX(){int value=dx;dx=0;return value;}public static int getDY(){int value=dy;dy=0;return value;}}",sources);
            write(dir,"cn/academy/terminal/client/InputProbe.java","package cn.academy.terminal.client;public class InputProbe {private static TerminalUI ui;public static void reset()throws Exception {var init=TerminalUI.class.getDeclaredMethod(\"__init\");init.setAccessible(true);init.invoke(null);cn.lambdalib.util.helper.GameTimer.time=0;ui=new TerminalUI();ui.onAdded();}public static double[] frame(int dx,int down,long now){org.lwjgl.input.Mouse.dx=dx;org.lwjgl.input.Mouse.dy=-down;ui.helper.mouseXYChange();cn.lambdalib.util.helper.GameTimer.time=now;ui.draw(new net.minecraft.client.gui.ScaledResolution());return new double[]{ui.mouseX,ui.mouseY,ui.buffX,ui.buffY,ui.selection,ui.helper.dx,ui.helper.dy};}}",sources);
            write(dir,"cn/academy/port/client/terminal/TerminalPointerInput.java",Files.readString(root().resolve("src/main/java/cn/academy/port/client/terminal/TerminalPointerInput.java")),sources);
            write(dir,"cn/academy/port/client/terminal/TerminalTimeline.java",Files.readString(root().resolve("src/main/java/cn/academy/port/client/terminal/TerminalTimeline.java")),sources);
            write(dir,"cn/academy/port/client/terminal/PortPointerProbe.java","package cn.academy.port.client.terminal;public class PortPointerProbe {private static final class Screen {Object screen;}private Screen mc=new Screen();private TerminalPointerInput input=new TerminalPointerInput();private double mouseX=150,mouseY=150,buffX=150,buffY=150;private int selection,scroll;private java.util.List<Integer> apps=java.util.List.of(1,2,3,4,5);private long lastFrame;private static PortPointerProbe probe=new PortPointerProbe();public static void reset(){probe=new PortPointerProbe();}public static void capture(double x,double y){probe.input.capture(x,y);}public static double[] frame(long now,boolean active){probe.mc.screen=active?null:new Object();return probe.draw(now);}private double[] draw(long now){if(lastFrame==0)lastFrame=now;long dt=Math.max(0,now-lastFrame);"+render+balanced+"return new double[]{mouseX,mouseY,buffX,buffY,selection};}public static void clear(){probe.input.clear();}}",sources);
            // Exact official methods retain original bodies; only their dependencies become finite fields.
            String official="package probe;public class OfficialCursor {public static class Window {public long getWindow(){return 1;}public int getScreenWidth(){return 1180;}public int getScreenHeight(){return 812;}}public static class Minecraft {public static boolean ON_OSX=false;public int missTime;public boolean active=true;private static final Minecraft INSTANCE=new Minecraft();public static Minecraft getInstance(){return INSTANCE;}public Window getWindow(){return new Window();}public boolean isWindowActive(){return active;}public void setScreen(Object o){}}public static class KeyMapping {public static void setAll(){}}public static class InputConstants {public static void grabOrReleaseMouse(long window,int mode,double x,double y){}}private final Minecraft minecraft=Minecraft.getInstance();private double xpos,ypos,accumulatedDX,accumulatedDY;private boolean ignoreFirstMove=true,mouseGrabbed;";
            for(String name:List.of("onMove","getXVelocity","getYVelocity","grabMouse","releaseMouse","cursorEntered"))official+=witness(name+".java.txt");
            official+="public void move(double x,double y){onMove(1,x,y);}public void active(boolean v){minecraft.active=v;}public void drain(){accumulatedDX=accumulatedDY=0;}public double[] state(){return new double[]{xpos,ypos,accumulatedDX,accumulatedDY};}}";
            write(dir,"probe/OfficialCursor.java",official,sources);
            var compiler=new ArrayList<>(List.of("-proc:none","--release","21","-encoding","UTF-8","-cp",System.getProperty("java.class.path"),"-d",classes.toString()));compiler.addAll(sources);check(ToolProvider.getSystemJavaCompiler().run(null,null,null,compiler.toArray(String[]::new))==0,"Unchanged source input and actual corrected port compile");
            try(var loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},TerminalInputRegressionTest.class.getClassLoader()){
                @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{synchronized(getClassLoadingLock(name)){Class<?> c=findLoadedClass(name);if(c==null&&!name.startsWith("java.")){try{c=findClass(name);}catch(ClassNotFoundException ignored){}}if(c==null)c=super.loadClass(name,false);if(resolve)resolveClass(c);return c;}}
            }){
                var oldProbe=loader.loadClass("cn.academy.terminal.client.InputProbe");var port=loader.loadClass("cn.academy.port.client.terminal.PortPointerProbe");
                var oldFrame=oldProbe.getMethod("frame",int.class,int.class,long.class);var newFrame=port.getMethod("frame",long.class,boolean.class);var capture=port.getMethod("capture",double.class,double.class);
                var random=new Random(605740);for(int trace=0;trace<100;trace++){
                    oldProbe.getMethod("reset").invoke(null);port.getMethod("reset").invoke(null);long now=0;
                    for(int frame=0;frame<200;frame++){int dx=random.nextInt(901)-450,dy=random.nextInt(901)-450;now+=1+random.nextInt(50);capture.invoke(null,(double)dx,(double)dy);double[] classic=(double[])oldFrame.invoke(null,dx,dy,now),modern=(double[])newFrame.invoke(null,now,true);for(int axis=0;axis<5;axis++)equal(classic[axis],modern[axis],1e-9,"Source relative direction/clamp/selection-before-delta/balance");equal(classic[5],0,0,"Original MouseHelper X drained after draw");equal(classic[6],0,0,"Original MouseHelper Y drained after draw");}
                }
                for(double[] move:List.of(new double[]{-100,220,3},new double[]{200,220,4})){
                    port.getMethod("reset").invoke(null);capture.invoke(null,move[0],move[1]);newFrame.invoke(null,1L,true);double[] selected=(double[])newFrame.invoke(null,2L,true);equal(selected[4],move[2],0,"Both genuine lower-row app slots reachable by relative movement");
                }
                port.getMethod("reset").invoke(null);capture.invoke(null,12.25,18.5);capture.invoke(null,7.75,-8.5);double[] value=(double[])newFrame.invoke(null,1L,true);equal(value[0],164,0,"Multiple captured raw samples accumulate once");equal(value[1],157,0,"Fractional native deltas retain direction");double[] repeat=(double[])newFrame.invoke(null,2L,true);equal(repeat[0],164,0,"Same delta cannot replay next frame");equal(repeat[1],157,0,"Y cannot replay next frame");
                capture.invoke(null,999d,999d);value=(double[])newFrame.invoke(null,3L,false);equal(value[0],164,0,"Foreground screen drains pending input without motion");value=(double[])newFrame.invoke(null,4L,true);equal(value[0],164,0,"Dismissal cannot replay foreground motion");capture.invoke(null,10d,10d);port.getMethod("clear").invoke(null);value=(double[])newFrame.invoke(null,5L,true);equal(value[0],164,0,"Disposed input cannot reach replacement");capture.invoke(null,Double.NaN,4d);capture.invoke(null,1d,Double.POSITIVE_INFINITY);value=(double[])newFrame.invoke(null,6L,true);equal(value[0],164,0,"Malformed nonfinite native input cannot poison virtual cursor");
                var cursorClass=loader.loadClass("probe.OfficialCursor");var move=cursorClass.getMethod("move",double.class,double.class);var state=cursorClass.getMethod("state");var cursor=cursorClass.getConstructor().newInstance();
                move.invoke(cursor,590d,406d);value=(double[])state.invoke(cursor);equal(value[2],0,0,"Official ignored first X move has no velocity");equal(value[3],0,0,"Official ignored first Y move has no velocity");check(value[0]!=0&&value[1]!=0,"Old absolute differencing would invent first-move jump");
                cursorClass.getMethod("drain").invoke(cursor);cursorClass.getMethod("active",boolean.class).invoke(cursor,false);move.invoke(cursor,900d,700d);value=(double[])state.invoke(cursor);equal(value[2],0,0,"Unfocused X callback has no native velocity");equal(value[3],0,0,"Unfocused Y callback has no native velocity");check(value[0]==900&&value[1]==700,"Official raw position still updates while unfocused");
                cursorClass.getMethod("active",boolean.class).invoke(cursor,true);cursorClass.getMethod("grabMouse").invoke(cursor);value=(double[])state.invoke(cursor);equal(value[0],590,0,"Official grab rebases raw X to center");equal(value[1],406,0,"Official grab rebases raw Y to center");equal(value[2],0,0,"Grab has no relative X motion");equal(value[3],0,0,"Grab has no relative Y motion");move.invoke(cursor,1000d,750d);value=(double[])state.invoke(cursor);equal(value[2],0,0,"First post-grab callback is ignored");equal(value[3],0,0,"Post-grab ignored callback Y is not movement");
                move.invoke(cursor,1007d,759d);value=(double[])state.invoke(cursor);equal(value[2],7,0,"Official true X relative movement retained");equal(value[3],9,0,"Official true Y relative movement retained");cursorClass.getMethod("drain").invoke(cursor);cursorClass.getMethod("releaseMouse").invoke(cursor);value=(double[])state.invoke(cursor);equal(value[0],590,0,"Release raw X rebase is not motion");equal(value[1],406,0,"Release raw Y rebase is not motion");equal(value[2],0,0,"Release delta remains zero");equal(value[3],0,0,"Release Y delta remains zero");
                cursorClass.getMethod("cursorEntered").invoke(cursor);move.invoke(cursor,100d,200d);value=(double[])state.invoke(cursor);equal(value[2],0,0,"Cursor-enter baseline reset is not terminal motion");equal(value[3],0,0,"Cursor-enter Y reset is not terminal motion");
            }
        }finally{try(var files=Files.walk(temp)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.delete(file);}}
        System.out.println("PASS TerminalInputRegressionTest "+assertions+" assertions; unchanged original helper/UI relative input, exact official cursor rebase methods and actual port draw prefix; no game/native startup");
    }
}

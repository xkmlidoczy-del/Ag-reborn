package cn.academy.port.cat;
import cn.academy.port.client.ClassicCatVisual;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import javax.tools.ToolProvider;
/** Immutable unchanged original methods, compiled independently of the modern implementation. */
public final class ClassicMiscSourceOracleTest {
    private static final Map<String,String> PINNED=Map.ofEntries(
        Map.entry("original/src/main/java/cn/academy/energy/block/TileCatEngine.java","0ed47deceb3d58eb2f6202f9909b336a23af38e78fff8357743c8f9170fb18d5"),
        Map.entry("original/src/main/java/cn/academy/energy/block/BlockCatEngine.java","53649e63da7352a15e34d93f07483de94d667a8ccb01ff8ce92eba8215f1d7c7"),
        Map.entry("original/src/main/java/cn/academy/core/block/TileGeneratorBase.java","21669f5fdb7684452fec12cb8e0384731b543ff8bda27c6d22fbacc122c6207a"),
        Map.entry("original/src/main/java/cn/academy/energy/client/render/block/RenderCatEngine.java","870c356aa33caa28174306c0656443e40083667f1ffa859f3cca3932984a446f"),
        Map.entry("original/src/main/java/cn/academy/misc/achievements/ItemAchievement.java","6ca749166d80e2e9912af91b472740443c180b1b217815ba5ffe2ab854369417"),
        Map.entry("original/src/main/java/cn/academy/misc/achievements/client/RenderItemAchievement.java","4755621b42f64500056b088844e95a111c1fd440014d1c2dd9616fbae8eddd44"));
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void equal(double a,double b,String label){check(Double.isFinite(b)&&Math.abs(a-b)<1e-8,label+": "+a+" != "+b);}
    public static void main(String[] args)throws Exception{
        Path source=Path.of(System.getProperty("academy.misc.stage",".")).resolve("src/test/resources/classic-misc-source");
        for(String line:Files.readAllLines(source.resolve("manifest.sha256"))){int split=line.indexOf("  ");String path=line.substring(split+2);check(line.substring(0,split).equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source.resolve(path))))),"original/boundary SHA256 "+path);}
        for(var pinned:PINNED.entrySet())check(pinned.getValue().equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source.resolve(pinned.getKey()))))),"independent pinned original "+pinned.getKey());
        var compiled=Files.createTempDirectory("academy-cat-original-");
        try{
            var inputs=new ArrayList<String>(List.of("-proc:none","-encoding","UTF-8","-d",compiled.toString()));
            try(var files=Files.walk(source.resolve("stubs"))){files.filter(p->p.toString().endsWith(".java")).sorted().forEach(p->inputs.add(p.toString()));}
            for(String name:List.of("cn/academy/energy/block/TileCatEngine.java","cn/academy/energy/block/BlockCatEngine.java","cn/academy/core/block/TileGeneratorBase.java","cn/academy/energy/client/render/block/RenderCatEngine.java","cn/academy/misc/achievements/ItemAchievement.java","cn/academy/misc/achievements/client/RenderItemAchievement.java"))inputs.add(source.resolve("original/src/main/java/"+name).toString());
            check(ToolProvider.getSystemJavaCompiler().run(null,null,null,inputs.toArray(String[]::new))==0,"unchanged1.0.7 compilation");
            try(var loader=new URLClassLoader(new URL[]{compiled.toUri().toURL()},ClassLoader.getPlatformClassLoader())){arithmetic(loader);render(loader);icons(loader);block(loader);}
        }finally{try(var paths=Files.walk(compiled)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        String recipes=Files.readString(source.resolve("original/src/main/resources/assets/academy/recipes/default.recipe"));check(!recipes.contains("cat_engine")&&!recipes.contains("infiniteGen")&&!recipes.contains("DUMMY_ITEM")&&!recipes.contains("logo"),"no invented default acquisition recipe");
        System.out.println("ClassicMiscSourceOracleTest: "+checks+" unchanged-source arithmetic, sync, billboard, icon and link branch checks passed");
    }
    private static void arithmetic(ClassLoader loader)throws Exception{
        var type=loader.loadClass("cn.academy.energy.block.TileCatEngine");var old=type.getConstructor().newInstance();var modern=new ClassicCatBuffer();
        equal(2000,type.getField("bufferSize").getDouble(old),"source buffer");equal(200,type.getField("bandwidth").getDouble(old),"source bandwidth");check(type.getField("size").getInt(old)==0&&type.getField("name").get(old).equals("infinite_generator"),"zero slot inventory identity");
        Random random=new Random(107);for(int i=0;i<20000;i++){
            double start=random.nextDouble()*2000;type.getMethod("setEnergy",double.class).invoke(old,start);modern.load(start);
            for(int tick=0;tick<3;tick++){type.getMethod("updateEntity").invoke(old);modern.tick();equal((double)type.getMethod("getEnergy").invoke(old),modern.energy(),"source tick energy");equal(type.getField("thisTickGen").getDouble(old),modern.generated(),"source generated");double req=random.nextDouble()*3000;equal((double)type.getMethod("getProvidedEnergy",double.class).invoke(old,req),modern.provide(req),"source demand draw has no internal bandwidth clamp");equal((double)type.getMethod("getEnergy").invoke(old),modern.energy(),"post draw energy");double offered=random.nextDouble()*3000;boolean simulate=random.nextBoolean();equal((double)type.getMethod("addEnergy",double.class,boolean.class).invoke(old,offered,simulate),modern.addEnergy(offered,simulate),"source manual add/simulated remainder");equal((double)type.getMethod("getEnergy").invoke(old),modern.energy(),"post add energy");}
        }
        var fresh=type.getConstructor().newInstance();var capture=loader.loadClass("oracle.Capture");var messages=(List<?>)capture.getField("messages").get(null);messages.clear();for(int i=0;i<19;i++)type.getMethod("updateEntity").invoke(fresh);check(messages.isEmpty(),"no early source sync");type.getMethod("updateEntity").invoke(fresh);check(messages.equals(List.of("sync_energy:2000.0","sync_genspeed:0.0")),"source20-tick energy then generation sync");
        modern.load(Double.NaN);equal(0,modern.energy(),"finite malformed energy adaptation");modern.load(5000);equal(2000,modern.energy(),"bounded save adaptation");equal(0,modern.provide(-1),"negative draw adaptation");
    }
    private static void render(ClassLoader loader)throws Exception{
        var tile=loader.loadClass("cn.academy.energy.block.TileCatEngine");var old=tile.getConstructor().newInstance();var renderer=loader.loadClass("cn.academy.energy.client.render.block.RenderCatEngine").getConstructor().newInstance();var method=renderer.getClass().getMethod("renderTileEntityAt",loader.loadClass("net.minecraft.tileentity.TileEntity"),double.class,double.class,double.class,float.class);var timer=loader.loadClass("cn.lambdalib.util.helper.GameTimer");var capture=loader.loadClass("oracle.Capture");
        var random=new Random(19);long last=0,now=100;double rotation=0;
        for(int i=0;i<500;i++){
            now+=random.nextInt(80);double generation=random.nextDouble()*500,x=random.nextDouble()*20-10,z=random.nextDouble()*20-10,y=2.25;timer.getField("time").setLong(null,now);tile.getField("thisTickGen").setDouble(old,generation);rotation=ClassicCatVisual.rotation(rotation,last,now,generation);last=now;method.invoke(renderer,old,x,y,z,0f);
            equal(rotation,tile.getField("rotation").getDouble(old),"source render accumulation");check(capture.getField("texture").get(null).equals("academy:textures/blocks/cat_engine.png"),"source bitmap path");
            var vertices=(List<?>)capture.getField("vertices").get(null);check(vertices.size()==4,"source four billboard vertices");int[][] corners={{0,0},{1,0},{1,1},{0,1}};
            for(int v=0;v<4;v++){double[] original=(double[])vertices.get(v);var modern=ClassicCatVisual.vertex(corners[v][0],corners[v][1],rotation,now,x,z);equal(original[0],modern.x(),"source transformedX");equal(original[1],modern.y()+y,"source transformedY");equal(original[2],modern.z(),"source transformedZ");equal(corners[v][0],original[3],"source U");equal(corners[v][1],original[4],"source V");}
        }
        var calls=(List<?>)capture.getField("calls").get(null);check(calls.get(0).equals("disable:1")&&calls.get(calls.size()-1).equals("enable:1"),"source no cull then restore");equal(rotation,ClassicCatVisual.rotation(rotation,last,last,500),"frozen pause clock has no rotation advance");
    }
    private static void icons(ClassLoader loader)throws Exception{
        var type=loader.loadClass("cn.academy.misc.achievements.ItemAchievement");type.getConstructor().newInstance();var stack=loader.loadClass("net.minecraft.item.ItemStack");var resource=loader.loadClass("net.minecraft.util.ResourceLocation");
        Object first=type.getMethod("getStack",String.class).invoke(null,"achievements/tp_mastery"),same=type.getMethod("getStack",String.class).invoke(null,"achievements/tp_mastery"),second=type.getMethod("getStack",String.class).invoke(null,"abilities/electromaster/skills/charging");
        check(stack.getField("damage").getInt(first)==stack.getField("damage").getInt(same)&&stack.getField("damage").getInt(second)==2,"source dynamic dedup indices");Object texture=type.getMethod("getTexture",int.class).invoke(null,1);check(resource.getMethod("path").invoke(texture).equals("textures/achievements/tp_mastery.png"),"exact dynamic texture");check(resource.getMethod("path").invoke(type.getMethod("getTexture",int.class).invoke(null,0)).equals("textures/null.png"),"source index0 null");
        var renderer=loader.loadClass("cn.academy.misc.achievements.client.RenderItemAchievement").getConstructor().newInstance();var kind=loader.loadClass("net.minecraftforge.client.IItemRenderer$ItemRenderType");for(var value:kind.getEnumConstants())check((boolean)renderer.getClass().getMethod("handleRenderType",stack,kind).invoke(renderer,first,value)==value.toString().equals("INVENTORY"),"source inventory-only icon route");
        renderer.getClass().getMethod("renderItem",kind,stack,Object[].class).invoke(renderer,kind.getEnumConstants()[0],first,new Object[0]);var capture=loader.loadClass("oracle.Capture");equal(16,capture.getField("rectWidth").getDouble(null),"source icon16 width");equal(16,capture.getField("rectHeight").getDouble(null),"source icon16 height");check(capture.getField("texture").get(null).equals("academy:textures/achievements/tp_mastery.png"),"dynamic icon render texture");
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static void block(ClassLoader loader)throws Exception{
        var type=loader.loadClass("cn.academy.energy.block.BlockCatEngine");var block=type.getConstructor().newInstance();var worldType=loader.loadClass("net.minecraft.world.World");var world=worldType.getConstructor().newInstance();var tile=loader.loadClass("cn.academy.energy.block.TileCatEngine").getConstructor().newInstance();worldType.getField("tile").set(world,tile);var playerType=loader.loadClass("net.minecraft.entity.player.EntityPlayer");var player=playerType.getConstructor().newInstance();var method=type.getMethod("onBlockActivated",worldType,int.class,int.class,int.class,playerType,int.class,float.class,float.class,float.class);var helper=loader.loadClass("cn.academy.energy.api.WirelessHelper");var nodes=(List)helper.getField("nodes").get(null);
        check(!(boolean)type.getMethod("isOpaqueCube").invoke(block),"source nonopaque full-cell");check((boolean)method.invoke(block,world,0,0,0,player,0,0f,0f,0f),"source right click consumed");var messages=(List)playerType.getField("messages").get(player);check(messages.get(0).getClass().getField("key").get(messages.get(0)).equals("ac.cat_engine.notfound"),"source no-node message");
        var nodeType=loader.loadClass("cn.academy.energy.api.block.IWirelessNode");nodes.add(java.lang.reflect.Proxy.newProxyInstance(loader,new Class[]{nodeType},(p,m,a)->"one"));nodes.add(java.lang.reflect.Proxy.newProxyInstance(loader,new Class[]{nodeType},(p,m,a)->"two"));loader.loadClass("cn.lambdalib.util.generic.RandUtils").getField("chosen").setInt(null,1);method.invoke(block,world,0,0,0,player,0,0f,0f,0f);var eventBus=loader.loadClass("net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").get(null);var events=(List)eventBus.getClass().getField("events").get(eventBus);check(events.get(0).getClass().getField("node").get(events.get(0))==nodes.get(1),"source chosen random eligible node");helper.getField("linked").setBoolean(null,true);method.invoke(block,world,0,0,0,player,0,0f,0f,0f);check(events.get(1).getClass().getName().endsWith("UnlinkUserEvent"),"source repeated click unlink");int count=events.size();worldType.getField("isRemote").setBoolean(world,true);method.invoke(block,world,0,0,0,player,0,0f,0f,0f);check(events.size()==count,"source client consumes without graph mutation");
    }
}

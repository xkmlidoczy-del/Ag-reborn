package cn.academy.port.cat;
import cn.academy.port.display.ClassicCreativeCatalog;
import cn.academy.port.wireless.*;
import cn.academy.port.solar.ImagFluxGenerator;
import cn.academy.port.machine.ImagFluxReceiver;
import java.net.*;
import java.nio.file.*;
import java.util.*;
/** Actual unchanged modern wireless graph and active common class linking; no game process. */
public final class ClassicMiscRegressionTest {
    private static int checks;
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        var buffer=new ClassicCatBuffer();var own=new ClassicWirelessGraph.Pos(0,0,0);var nodePos=new ClassicWirelessGraph.Pos(1,0,0);var energy=new double[1];var full=new boolean[1];
        var node=new ImagFluxNode(){public double getEnergy(){return energy[0];}public void setEnergy(double v){energy[0]=v;}public double getMaxEnergy(){return 2000;}public double getBandwidth(){return 300;}public double getRange(){return 15;}public int getCapacity(){return full[0]?0:1;}public String getPassword(){return "private-local-node";}public String getNodeName(){return "named";}};
        var generator=new ImagFluxGenerator(){public double getEnergy(){return buffer.energy();}public double getBandwidth(){return 200;}public double getProvidedEnergy(double request){return buffer.provide(request);}};
        var graph=new ClassicWirelessGraph(new ClassicWirelessGraph.Resolver(){public boolean isLoaded(ClassicWirelessGraph.Pos p){return true;}public ImagFluxNode node(ClassicWirelessGraph.Pos p){return p.equals(nodePos)?node:null;}public ImagFluxMatrix matrix(ClassicWirelessGraph.Pos p){return null;}public ImagFluxGenerator generator(ClassicWirelessGraph.Pos p){return p.equals(own)?generator:null;}public ImagFluxReceiver receiver(ClassicWirelessGraph.Pos p){return null;}public List<ClassicWirelessGraph.Pos> wirelessBlocksWithin(ClassicWirelessGraph.Pos p,double range,int max){return List.of(nodePos);}});
        check(graph.nearbyNodes(own,20,100).size()==1,"source20/max100 eligible node");check(!graph.linkGenerator(nodePos,own,"invalid",true),"normal auth fails");check(graph.linkGenerator(nodePos,own,"invalid",false),"source Cat bypass-auth links actual graph");buffer.tick();graph.tick();check(buffer.energy()==300&&energy[0]==200,"actual graph200 bandwidth caps500 generation");check(graph.nearbyNodes(own,20,100).isEmpty(),"full node excluded");graph.unlinkGenerator(own);check(graph.nodeForGenerator(own)==null&&graph.nearbyNodes(own,20,100).size()==1,"repeat unlink frees slot");full[0]=true;check(graph.nearbyNodes(own,20,100).isEmpty(),"capacity0 source exclusion");
        check(!ClassicCreativeCatalog.ITEMS.contains("logo")&&!ClassicCreativeCatalog.ITEMS.contains("achievement_icon"),"source hidden display-only items");check(new HashSet<>(ClassicCreativeCatalog.ITEMS).size()==ClassicCreativeCatalog.ITEMS.size(),"no duplicate tab identities");check(ClassicCreativeCatalog.ITEMS.containsAll(List.of("cat_engine","windgen_base","windgen_pillar","windgen_main","windgen_fan","rf_input","rf_output","app_media_player")),"genuine staged/registered memberships declared");check(!ClassicCreativeCatalog.ITEMS.contains("eu_input")&&!ClassicCreativeCatalog.ITEMS.contains("ac_MediaItem"),"conditional unsupportedIC2/song exclusions");
        var urls=new ArrayList<URL>();for(var part:System.getProperty("java.class.path").split(java.io.File.pathSeparator))urls.add(Path.of(part).toUri().toURL());
        try(var loader=new URLClassLoader(urls.toArray(URL[]::new),ClassLoader.getPlatformClassLoader()){@Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(name.startsWith("net.minecraft.client.")||name.startsWith("cn.academy.port.client.")||name.startsWith("com.mojang.blaze3d."))throw new ClassNotFoundException("Client denied: "+name);return super.loadClass(name,resolve);}}){
            for(var name:List.of("cn.academy.port.cat.ClassicCatBuffer","cn.academy.port.cat.ClassicCatEngineBlock","cn.academy.port.cat.ClassicCatEngineBlockEntity","cn.academy.port.cat.ClassicCatEngines","cn.academy.port.display.ClassicAchievementIconItem","cn.academy.port.display.ClassicDisplayItems","cn.academy.port.display.ClassicCreativeCatalog","cn.academy.port.display.ClassicCreativeTab")){var type=Class.forName(name,false,loader);type.getDeclaredMethods();type.getDeclaredConstructors();check(true,"common native link "+name);}
        }
        System.out.println("ClassicMiscRegressionTest: "+checks+" actual graph, creative membership and client-denied common link checks passed");
    }
}

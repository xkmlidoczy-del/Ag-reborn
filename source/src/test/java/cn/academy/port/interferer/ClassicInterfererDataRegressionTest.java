/* Pure named-source, ability gate, captured-boundary and real unchanged graph checks. GPLv3. */
package cn.academy.port.interferer;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.machine.ImagFluxReceiver;
import cn.academy.port.solar.ImagFluxGenerator;
import cn.academy.port.wireless.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
public final class ClassicInterfererDataRegressionTest {
    private static int checks;
    private static void check(boolean result,String why){checks++;if(!result)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        var s=new AbilityProgress();var aliveA=new AtomicBoolean(true);var aliveB=new AtomicBoolean(true);s.addInterference("a",aliveA::get);s.tickInterference();check(!s.interfering&&s.interferenceSourceCount()==1,"source skips no-category cache/pruning");aliveA.set(false);s.tickInterference();check(s.hasInterference("a"),"no-category pending source retained");s.selectCategory("electromaster");s.learn("arc_gen");s.activated=true;s.tickInterference();check(!s.interfering&&!s.hasInterference("a"),"new category prunes invalid pending source");aliveA.set(true);s.addInterference("a",aliveA::get);s.addInterference("b",aliveB::get);s.tick();check(s.interfering&&!s.canUse("arc_gen")&&s.interferenceSourceCount()==2,"genuine ability tick/gate consumes source cache");aliveA.set(false);s.tickInterference();check(s.interfering&&s.interferenceSourceCount()==1,"one live source retains interference");s.addInterference("b",()->false);s.tickInterference();check(!s.interfering&&s.interferenceSourceCount()==0&&s.canUse("arc_gen"),"same source ID replaced, last invalid source clears gate");s.interfering=true;s.tickInterference();check(s.interfering,"unmanaged existing client/manual flags survive without machine predicate map");s.interfering=false;
        Random random=new Random(107);for(int attempt=0;attempt<20000;attempt++){double range=10+random.nextDouble()*90;int x=random.nextInt(100)-50,y=random.nextInt(100)-50,z=random.nextInt(100)-50;var box=ClassicInterfererRules.bounds(x,y,z,range);double px=x+.5+(random.nextDouble()*4-2)*range,py=y+.5+(random.nextDouble()*4-2)*range,pz=z+.5+(random.nextDouble()*4-2)*range;boolean old=px>x+.5-range&&px<x+.5+range&&py>y+.5-range&&py<y+.5+range&&pz>z+.5-range&&pz<z+.5+range;check(old==box.inside(px,py,pz),"independent strict source cube relation");check(!box.inside(box.minX(),y+.5,z+.5)&&!box.inside(box.maxX(),y+.5,z+.5),"both exact X faces excluded");}
        var captured=ClassicInterfererRules.bounds(0,0,0,100);var small=ClassicInterfererRules.bounds(0,0,0,10);check(captured.inside(50,.5,.5)&&!small.inside(50,.5,.5),"source captured old cube after range shrink");check(ClassicInterfererRules.clampRange(-1)==10&&ClassicInterfererRules.clampRange(1000)==100&&ClassicInterfererRules.loadRange(Double.NaN)==10,"explicit finite bounds adaptation");
        var pos=new ClassicWirelessGraph.Pos(0,0,0);var node=new ClassicWirelessGraph.Pos(1,0,0);var changes=new int[1];var graph=new ClassicWirelessGraph(new ClassicWirelessGraph.Resolver(){public boolean isLoaded(ClassicWirelessGraph.Pos p){return true;}public ImagFluxNode node(ClassicWirelessGraph.Pos p){return null;}public ImagFluxMatrix matrix(ClassicWirelessGraph.Pos p){return null;}public ImagFluxGenerator generator(ClassicWirelessGraph.Pos p){return null;}public ImagFluxReceiver receiver(ClassicWirelessGraph.Pos p){return null;}public List<ClassicWirelessGraph.Pos> wirelessBlocksWithin(ClassicWirelessGraph.Pos p,double r,int max){return List.of();}},new Random(107),()->changes[0]++);
        for(int i=0;i<1000;i++){check(!graph.linkReceiver(node,pos,"",true)&&!graph.linkGenerator(node,pos,"",true),"actual current graph rejects unpowered plain tile role");graph.tick();check(changes[0]==0&&graph.nodeForReceiver(pos)==null&&graph.nodeForGenerator(pos)==null,"rejected role creates no graph state");}
        clientDeniedLink();System.out.println("ClassicInterfererDataRegressionTest: "+checks+" captured cube/source/gate/finite/actual graph/common client-denied linkage checks passed");
    }
    private static void clientDeniedLink()throws Exception{
        List<URL> urls=new ArrayList<>();for(String entry:System.getProperty("java.class.path").split(java.io.File.pathSeparator))urls.add(Path.of(entry).toUri().toURL());
        try(var loader=new URLClassLoader(urls.toArray(URL[]::new),ClassLoader.getPlatformClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(name.startsWith("net.minecraft.client.")||name.startsWith("cn.academy.port.client."))throw new ClassNotFoundException("Client-denied common link: "+name);return super.loadClass(name,resolve);}
        }){for(String name:List.of("ClassicInterfererRules","ClassicInterferenceSources","ClassicAbilityInterferers","ClassicAbilityInterfererBlock","ClassicAbilityInterfererBlockEntity","ClassicAbilityInterfererMenu","ClassicInterfererNetwork","ClassicInterfererNetwork$Request","ClassicInterfererNetwork$Snapshot","ClassicInterfererNetwork$TileSnapshot")){var type=Class.forName("cn.academy.port.interferer."+name,false,loader);type.getDeclaredMethods();type.getDeclaredConstructors();check(true,"common native class linked with client classes denied: "+name);}}
    }
}

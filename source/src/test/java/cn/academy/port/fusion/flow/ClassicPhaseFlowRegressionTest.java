package cn.academy.port.fusion.flow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import cn.academy.port.fusion.flow.ClassicPhaseFlowAlgorithm.Cell;
import cn.academy.port.fusion.flow.ClassicPhaseFlowAlgorithm.Material;

/** Pure differential/edge-case tests; does not bootstrap Minecraft or launch a server. */
public final class ClassicPhaseFlowRegressionTest {
    private static int assertions;
    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
    private record Pos(int x, int y, int z) {}
    private static final Cell SOLID = new Cell(Material.OTHER, 0, true, null, Integer.MAX_VALUE);
    private static final class World implements ClassicPhaseFlowAlgorithm.World {
        final Map<Pos, Cell> cells = new HashMap<>();
        final List<String> events = new ArrayList<>();
        Cell fallback = SOLID;
        @Override public Cell cell(int x, int y, int z) { return cells.getOrDefault(new Pos(x, y, z), fallback); }
        void put(int x, int y, int z, Cell cell) { cells.put(new Pos(x, y, z), cell); }
        World copy() { World result = new World(); result.cells.putAll(cells); result.fallback = fallback; return result; }
        @Override public void setAir(int x, int y, int z) {
            put(x, y, z, Cell.air()); events.add("air:" + new Pos(x, y, z));
        }
        @Override public void setPhase(int x, int y, int z, int metadata, int flags) {
            put(x, y, z, Cell.phase(metadata)); events.add("phase:" + new Pos(x, y, z) + ":" + metadata + ":" + flags);
        }
        @Override public void schedulePhase(int x, int y, int z, int delay) {
            events.add("schedule:" + new Pos(x, y, z) + ":" + delay);
        }
        @Override public void notifyPhaseNeighbors(int x, int y, int z) { events.add("notify:" + new Pos(x, y, z)); }
        @Override public void dropDisplaced(int x, int y, int z) { events.add("drop:" + new Pos(x, y, z)); }
    }
    // Independent oracle retains the original variable expressions and branch structure.
    private static boolean originalPhase(World w, int x, int y, int z) { return w.cell(x,y,z).material()==Material.PHASE; }
    private static boolean originalSource(World w, int x, int y, int z) { return originalPhase(w,x,y,z)&&w.cell(x,y,z).metadata()==0; }
    private static int originalQuanta(World w, int x, int y, int z) {
        Cell c=w.cell(x,y,z);if(c.material()==Material.AIR)return 0;
        if(c.material()!=Material.PHASE)return -1;return 3-c.metadata();
    }
    private static int originalLarger(World w,int x,int y,int z,int compare) {
        int q=originalQuanta(w,x,y,z);if(q<=0)return compare;return q>=compare?q:compare;
    }
    private static boolean originalCanFlow(World w,int x,int y,int z) {
        Cell c=w.cell(x,y,z);if(c.material()==Material.AIR)return true;
        if(c.material()==Material.PHASE)return true;
        if(c.displacement()!=null)return c.displacement();
        if(c.blocksMovement()||c.material()==Material.WATER||c.material()==Material.LAVA||c.material()==Material.PORTAL)return false;
        if(c.density()==Integer.MAX_VALUE)return true;return 1>c.density();
    }
    private static boolean originalCanDisplace(World w,int x,int y,int z) {
        Cell c=w.cell(x,y,z);if(c.material()==Material.AIR)return true;
        if(c.material()==Material.PHASE)return false;
        if(c.displacement()!=null)return c.displacement();
        if(c.blocksMovement()||c.material()==Material.PORTAL)return false;
        if(c.density()==Integer.MAX_VALUE)return true;return 1>c.density();
    }
    private static void originalFlow(World w,int x,int y,int z,int meta) {
        if(meta<0)return;Cell c=w.cell(x,y,z);
        if(originalCanDisplace(w,x,y,z)) {
            if(c.material()!=Material.AIR) {
                if(c.displacement()!=null) { if(c.displacement())w.dropDisplaced(x,y,z); }
                else if(c.density()==Integer.MAX_VALUE)w.dropDisplaced(x,y,z);
            }
            w.setPhase(x,y,z,meta,3);w.schedulePhase(x,y,z,30);
        }
    }
    private static int originalCost(World w,int x,int y,int z,int recurseDepth,int side) {
        int cost=1000;
        for(int adjacent=0;adjacent<4;adjacent++) {
            if((adjacent==0&&side==1)||(adjacent==1&&side==0)||(adjacent==2&&side==3)||(adjacent==3&&side==2))continue;
            int x2=x,z2=z;
            switch(adjacent){case 0:--x2;break;case 1:++x2;break;case 2:--z2;break;case 3:++z2;break;}
            if(!originalCanFlow(w,x2,y,z2)||originalSource(w,x2,y,z2))continue;
            if(originalCanFlow(w,x2,y-1,z2))return recurseDepth;
            if(recurseDepth>=4)continue;
            int min=originalCost(w,x2,y,z2,recurseDepth+1,adjacent);if(min<cost)cost=min;
        }
        return cost;
    }
    private static boolean[] originalOptimal(World w,int x,int y,int z) {
        int[] cost=new int[4];boolean[] optimal=new boolean[4];
        for(int side=0;side<4;side++) {
            cost[side]=1000;int x2=x,z2=z;
            switch(side){case 0:--x2;break;case 1:++x2;break;case 2:--z2;break;case 3:++z2;break;}
            if(!originalCanFlow(w,x2,y,z2)||originalSource(w,x2,y,z2))continue;
            if(originalCanFlow(w,x2,y-1,z2))cost[side]=0;else cost[side]=originalCost(w,x2,y,z2,1,side);
        }
        int min=cost[0];for(int side=1;side<4;side++)if(cost[side]<min)min=cost[side];
        for(int side=0;side<4;side++)optimal[side]=cost[side]==min;return optimal;
    }
    private static void originalTick(World w,int x,int y,int z) {
        int quantaRemaining=3-w.cell(x,y,z).metadata();int expQuanta=-101;
        if(quantaRemaining<3) {
            int y2=y+1;
            if(originalPhase(w,x,y2,z)||originalPhase(w,x-1,y2,z)||originalPhase(w,x+1,y2,z)
                    ||originalPhase(w,x,y2,z-1)||originalPhase(w,x,y2,z+1))expQuanta=2;
            else {
                int maxQuanta=-100;maxQuanta=originalLarger(w,x-1,y,z,maxQuanta);
                maxQuanta=originalLarger(w,x+1,y,z,maxQuanta);maxQuanta=originalLarger(w,x,y,z-1,maxQuanta);
                maxQuanta=originalLarger(w,x,y,z+1,maxQuanta);expQuanta=maxQuanta-1;
            }
            if(expQuanta!=quantaRemaining) {
                quantaRemaining=expQuanta;
                if(expQuanta<=0)w.setAir(x,y,z);
                else {w.setPhase(x,y,z,3-expQuanta,3);w.schedulePhase(x,y,z,30);w.notifyPhaseNeighbors(x,y,z);}
            }
        } else if(quantaRemaining>=3)w.setPhase(x,y,z,0,2);
        if(originalCanDisplace(w,x,y-1,z)){originalFlow(w,x,y-1,z,1);return;}
        int flowMeta=3-quantaRemaining+1;if(flowMeta>=3)return;
        boolean vertically=originalPhase(w,x,y-1,z)||(originalPhase(w,x,y,z)&&originalCanFlow(w,x,y-1,z));
        if(originalSource(w,x,y,z)||!vertically) {
            if(originalPhase(w,x,y+1,z))flowMeta=1;
            boolean[] flow=originalOptimal(w,x,y,z);
            if(flow[0])originalFlow(w,x-1,y,z,flowMeta);if(flow[1])originalFlow(w,x+1,y,z,flowMeta);
            if(flow[2])originalFlow(w,x,y,z-1,flowMeta);if(flow[3])originalFlow(w,x,y,z+1,flowMeta);
        }
    }
    private static World flat(int metadata) {
        World w=new World();for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)w.put(x,0,z,Cell.air());
        w.put(0,0,0,Cell.phase(metadata));return w;
    }
    private static void edges() {
        check(ClassicPhaseFlowAlgorithm.QUANTA==3,"original quanta");
        check(ClassicPhaseFlowAlgorithm.TICK_DELAY==30,"viscosity-derived 30 ticks");
        for(int m=0;m<3;m++)check(ClassicPhaseFlowAlgorithm.quanta(Cell.phase(m))==3-m,"metadata/quanta inverse");
        World w=flat(0);ClassicPhaseFlowAlgorithm.tick(w,0,0,0);
        check(w.cell(0,0,0).metadata()==0,"source kept");
        check(w.cell(-1,0,0).metadata()==1&&w.cell(1,0,0).metadata()==1
                &&w.cell(0,0,-1).metadata()==1&&w.cell(0,0,1).metadata()==1,"flat ties spread all four");
        check(w.events.get(1).startsWith("phase:Pos[x=-1"),"negative X first");
        check(w.events.get(3).startsWith("phase:Pos[x=1"),"positive X second");
        check(w.events.get(5).contains("z=-1]"),"negative Z third");
        check(w.events.get(7).contains("z=1]"),"positive Z fourth");
        check(w.events.stream().filter(s->s.startsWith("schedule:")).count()==4,"new-flow scheduling");
        ClassicPhaseFlowAlgorithm.tick(w,1,0,0);
        check(w.cell(2,0,0).material()==Material.PHASE&&w.cell(2,0,0).metadata()==2,"second ring one quantum");
        ClassicPhaseFlowAlgorithm.tick(w,2,0,0);
        check(w.cell(3,0,0).material()==Material.AIR,"flat reach limited to two");
        w=flat(1);ClassicPhaseFlowAlgorithm.tick(w,0,0,0);
        check(w.cell(0,0,0).material()==Material.AIR&&w.events.size()==1,"isolated non-source decays without creating isolated lateral flow");
        w=new World();w.put(0,0,0,Cell.phase(1));w.put(0,-1,0,Cell.air());
        ClassicPhaseFlowAlgorithm.tick(w,0,0,0);
        check(w.cell(0,0,0).material()==Material.AIR&&w.cell(0,-1,0).metadata()==1,"literal decay still flows downward");
        check(w.events.get(0).startsWith("air:")&&w.events.get(1).startsWith("phase:"),"decay before downflow");
        w=flat(2);w.put(1,1,0,Cell.phase(2));ClassicPhaseFlowAlgorithm.tick(w,0,0,0);
        check(w.cell(0,0,0).metadata()==1,"above-adjacent phase restores two quanta");
        check(w.cell(-1,0,0).metadata()==2,"above-adjacent alone does not reset lateral metadata");
        check(w.events.get(1).startsWith("schedule:")&&w.events.get(2).startsWith("notify:"),"decay metadata schedule then notify");
        w=flat(2);w.put(0,1,0,Cell.phase(2));ClassicPhaseFlowAlgorithm.tick(w,0,0,0);
        check(w.cell(0,0,0).metadata()==1&&w.cell(-1,0,0).metadata()==1,"direct above resets lateral metadata to one");
        w=flat(1);w.put(-1,0,0,Cell.phase(0));w.put(1,0,0,Cell.phase(0));ClassicPhaseFlowAlgorithm.tick(w,0,0,0);
        check(w.cell(0,0,0).metadata()==1,"two adjacent sources do not regenerate a source");
        w=flat(1);w.put(-1,0,0,Cell.phase(0));w.put(0,-1,0,Cell.phase(2));ClassicPhaseFlowAlgorithm.tick(w,0,0,0);
        check(w.cell(0,0,1).material()==Material.AIR,"non-source with phase below has no lateral spread");
        w=flat(0);w.put(0,-1,0,Cell.phase(2));ClassicPhaseFlowAlgorithm.tick(w,0,0,0);
        check(w.cell(0,0,1).material()==Material.PHASE,"source still spreads above phase below");
        check(w.cell(0,-1,0).metadata()==2,"existing phase is never displaced");
        w=flat(0);w.put(1,-1,0,Cell.air());
        check(Arrays.equals(ClassicPhaseFlowAlgorithm.optimalDirections(w,0,0,0),new boolean[]{false,true,false,false}),"immediate drop wins shortest-cost search");
        w=new World();w.put(0,0,0,Cell.phase(0));
        check(Arrays.equals(ClassicPhaseFlowAlgorithm.optimalDirections(w,0,0,0),new boolean[]{true,true,true,true}),"all blocked cost1000 still ties all directions");
        w=new World();w.put(0,0,0,Cell.phase(0));
        for(int x=1;x<=5;x++)w.put(x,0,0,Cell.air());w.put(5,-1,0,Cell.air());
        check(Arrays.equals(ClassicPhaseFlowAlgorithm.optimalDirections(w,0,0,0),new boolean[]{false,true,false,false}),"depth-four search sees fifth-cell drop");
        w.put(5,-1,0,SOLID);w.put(6,0,0,Cell.air());w.put(6,-1,0,Cell.air());
        check(Arrays.equals(ClassicPhaseFlowAlgorithm.optimalDirections(w,0,0,0),new boolean[]{true,true,true,true}),"depth-four cutoff cannot see sixth-cell drop");
        w=new World();w.put(0,0,0,Cell.phase(0));
        for(int x=1;x<=5;x++){w.put(x,0,0,Cell.air());w.put(-x,0,0,Cell.air());}
        w.put(5,-1,0,Cell.air());w.put(-5,-1,0,Cell.air());
        check(Arrays.equals(ClassicPhaseFlowAlgorithm.optimalDirections(w,0,0,0),new boolean[]{true,true,false,false}),"equal depth-four exits preserve both directions");
        w.put(-3,-1,0,Cell.air());
        check(Arrays.equals(ClassicPhaseFlowAlgorithm.optimalDirections(w,0,0,0),new boolean[]{true,false,false,false}),"shorter recursive exit wins");
        w=flat(0);w.put(1,0,0,Cell.phase(2));ClassicPhaseFlowAlgorithm.tick(w,0,0,0);
        check(w.cell(1,0,0).metadata()==2,"source flow cannot overwrite existing phase metadata");
        ClassicPhaseFlowAlgorithm.tick(w,1,0,0);
        check(w.cell(1,0,0).metadata()==1,"existing phase strengthens only on its own tick");
        for(Material material:List.of(Material.WATER,Material.LAVA)) {
            Cell c=new Cell(material,0,false,null,Integer.MAX_VALUE);
            check(!ClassicPhaseFlowAlgorithm.canFlowInto(c),"vanilla liquid rejects lateral flow");
            check(ClassicPhaseFlowAlgorithm.canDisplace(c),"vanilla liquid permits downward displacement");
            w=new World();w.put(0,0,0,Cell.phase(0));w.put(0,-1,0,c);ClassicPhaseFlowAlgorithm.tick(w,0,0,0);
            check(w.cell(0,-1,0).material()==Material.PHASE&&w.cell(0,-1,0).metadata()==1,"downward liquid metadata one");
            check(w.events.get(1).startsWith("drop:"),"vanilla-liquid drop hook precedes replace");
        }
        for(int density:List.of(-1,0,1,2,Integer.MAX_VALUE)) {
            Cell c=new Cell(Material.OTHER,0,false,null,density);boolean expected=density<1||density==Integer.MAX_VALUE;
            check(ClassicPhaseFlowAlgorithm.canFlowInto(c)==expected,"foreign fluid lateral density");
            check(ClassicPhaseFlowAlgorithm.canDisplace(c)==expected,"foreign fluid downflow density");
        }
        for(Material material:Material.values())for(boolean movement:List.of(false,true))for(Boolean override:Arrays.asList(null,Boolean.FALSE,Boolean.TRUE)) {
            Cell c=new Cell(material,0,movement,override,Integer.MAX_VALUE);World probe=new World();probe.put(0,0,0,c);
            check(ClassicPhaseFlowAlgorithm.canFlowInto(c)==originalCanFlow(probe,0,0,0),"material and override flow oracle");
            check(ClassicPhaseFlowAlgorithm.canDisplace(c)==originalCanDisplace(probe,0,0,0),"material and override displacement oracle");
        }
        w=new World();w.fallback=Cell.air();ClassicPhaseFlowAlgorithm.tick(w,0,0,0);
        check(w.events.isEmpty(),"stale native tick does not create fluid");
    }
    private static void differential() {
        Random random=new Random(0x107_21_1L);
        for(int trial=0;trial<4096;trial++) {
            World actual=new World();
            for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)for(int y=-1;y<=1;y++) {
                int n=random.nextInt(12);Cell c;
                if(n<4)c=Cell.air();else if(n<6)c=Cell.phase(random.nextInt(3));
                else if(n<9)c=new Cell(Material.values()[n-4],0,false,null,Integer.MAX_VALUE);
                else c=new Cell(Material.OTHER,0,random.nextBoolean(),random.nextInt(4)==0?random.nextBoolean():null,
                            switch(random.nextInt(4)){case 0->-1;case 1->1;case 2->2;default->Integer.MAX_VALUE;});
                actual.put(x,y,z,c);
            }
            actual.put(0,0,0,Cell.phase(trial%7==0?8:random.nextInt(3)));
            World expected=actual.copy();
            check(Arrays.equals(ClassicPhaseFlowAlgorithm.optimalDirections(actual,0,0,0),originalOptimal(expected,0,0,0)),"seeded shortest-flow directions "+trial);
            ClassicPhaseFlowAlgorithm.tick(actual,0,0,0);originalTick(expected,0,0,0);
            check(actual.cells.equals(expected.cells),"seeded cell state differential "+trial);
            check(actual.events.equals(expected.events),"seeded mutation/drop/schedule/notify order "+trial);
        }
    }
    public static void main(String[] args) {
        edges();differential();System.out.println("ClassicPhaseFlowRegressionTest: "+assertions+" assertions passed");
    }
}

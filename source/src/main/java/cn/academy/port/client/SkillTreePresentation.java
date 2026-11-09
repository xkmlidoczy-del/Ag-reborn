package cn.academy.port.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Client presentation only: category colours, non-stacking wires and actual prerequisite focus. */
public final class SkillTreePresentation {
    public record Accent(double r,double g,double b) {}
    public static Accent accent(String category) {
        return switch(category) {
            case "meltdowner" -> rgb(0xffd08a);
            case "teleporter" -> rgb(0xdcb8ff);
            case "vecmanip" -> rgb(0xa6f0c0);
            default -> rgb(0xb0e5ff);
        };
    }
    private static Accent rgb(int rgb){return new Accent((rgb>>16&255)/255d,(rgb>>8&255)/255d,(rgb&255)/255d);}
    public record Dependency(String child,String parent) {}

    /** Traverse explicit canonical requirements, never siblings or an invented any-of prerequisite. */
    public static Set<Dependency> prerequisiteChain(String selected,Map<String,List<String>> requirements) {
        if(selected==null||!requirements.containsKey(selected))return Set.of();
        var pending=new ArrayDeque<String>();pending.add(selected);
        var visited=new LinkedHashSet<String>();var edges=new LinkedHashSet<Dependency>();
        while(!pending.isEmpty()){
            String child=pending.removeFirst();if(!visited.add(child))continue;
            for(String parent:requirements.getOrDefault(child,List.of())){
                if(parent.equals(child)||!requirements.containsKey(parent))continue;
                edges.add(new Dependency(child,parent));pending.addLast(parent);
            }
        }
        return java.util.Collections.unmodifiableSet(edges);
    }

    public record Wire(double x0,double y0,double x1,double y1) {}
    private record Axis(boolean vertical,double coordinate) implements Comparable<Axis> {
        @Override public int compareTo(Axis other){int direction=Boolean.compare(vertical,other.vertical);return direction!=0?direction:Double.compare(coordinate,other.coordinate);}
    }
    /** Union collinear spans before drawing, so overlapping routes cannot accumulate into bright buses. */
    public static List<Wire> mergeWires(List<Wire> wires) {
        var channels=new TreeMap<Axis,List<double[]>>();
        for(var wire:wires){
            if(!Double.isFinite(wire.x0()+wire.y0()+wire.x1()+wire.y1()))throw new IllegalArgumentException("Finite tree wire required");
            boolean vertical=wire.x0()==wire.x1();
            if(!vertical&&wire.y0()!=wire.y1())throw new IllegalArgumentException("Orthogonal tree wire required");
            double a=vertical?wire.y0():wire.x0(),b=vertical?wire.y1():wire.x1();if(a==b)continue;
            channels.computeIfAbsent(new Axis(vertical,vertical?wire.x0():wire.y0()),ignored->new ArrayList<>()).add(new double[]{Math.min(a,b),Math.max(a,b)});
        }
        var result=new ArrayList<Wire>();
        for(var channel:channels.entrySet()){
            var spans=channel.getValue();spans.sort(Comparator.comparingDouble(span->span[0]));
            double start=spans.getFirst()[0],end=spans.getFirst()[1];
            for(int i=1;i<spans.size();i++){
                var span=spans.get(i);
                if(span[0]<=end){end=Math.max(end,span[1]);continue;}
                result.add(wire(channel.getKey(),start,end));start=span[0];end=span[1];
            }
            result.add(wire(channel.getKey(),start,end));
        }
        return List.copyOf(result);
    }
    private static Wire wire(Axis axis,double start,double end){return axis.vertical()?new Wire(axis.coordinate(),start,axis.coordinate(),end):new Wire(start,axis.coordinate(),end,axis.coordinate());}
    /** The card remains inside the tree and on the opposite vertical side of the pointer. */
    public static DeveloperSkillDetailsLayout.Rect hoverCard(double mx,double my,double width,double height){
        double w=Math.max(1,Math.min(249,width)),h=Math.max(1,Math.min(61,height));
        double x=Math.max(132,Math.min(381-w,mx<256?mx+10:mx-w-10));
        double y=my<87.5?153-h:22;
        return new DeveloperSkillDetailsLayout.Rect(x,y,w,h);
    }
    private SkillTreePresentation() {}
}

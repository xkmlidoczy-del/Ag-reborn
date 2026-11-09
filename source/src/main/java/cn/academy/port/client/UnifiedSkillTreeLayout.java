package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerSkills;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** One scrollable tree per category. Source identity, tier, prerequisites and learning conditions are unchanged. */
public final class UnifiedSkillTreeLayout {
    public static final double COLUMN_X = 17, COLUMN_STEP = 48, ROW = 44, TOP = 17;
    public static final double HOVER_RADIUS = ClassicDeveloperTimeline.OUTLINE_SIZE * 1.2 / 2;
    private static final double CHANNEL = ROW / 2;
    private UnifiedSkillTreeLayout() {}

    public static List<ClassicDeveloperLayout.Node> category(String category) {
        var all = ClassicDeveloperLayout.category(category);
        var placed = new ArrayList<ClassicDeveloperLayout.Node>(all.size());
        for (int level = 1; level <= 5; level++) {
            final int lv = level;
            var column = new ArrayList<ClassicDeveloperLayout.Node>();
            all.stream().filter(n -> n.level() == lv && !HighPowerSkills.contains(n.id())).forEach(column::add);
            all.stream().filter(n -> n.level() == lv && HighPowerSkills.contains(n.id()))
                    .sorted(Comparator.comparingInt(ClassicDeveloperLayout.Node::index)).forEach(column::add);
            for (int i = 0; i < column.size(); i++) {
                var n = column.get(i);
                placed.add(new ClassicDeveloperLayout.Node(n.category(), n.id(), n.index(), n.level(),
                        COLUMN_X + (lv - 1) * COLUMN_STEP, TOP + i * ROW, n.parent(), n.enabled(),
                        n.hintIcon(), n.nameKey(), n.descriptionKey(), n.conditions()));
            }
        }
        return List.copyOf(placed);
    }

    /** Original LV4/LV5 skills keep a gold frame and the first positions in their level column. */
    public static boolean signature(ClassicDeveloperLayout.Node node) {
        return node.level() >= 4 && !HighPowerSkills.contains(node.id());
    }

    /** Bottom channel and hover/parallax padding are scrollable, so the final row is fully reachable. */
    public static double contentHeight(List<ClassicDeveloperLayout.Node> nodes) {
        return Math.max(ClassicDeveloperTimeline.AREA_HEIGHT,
                nodes.stream().mapToDouble(n -> n.y() + 8 + CHANNEL + 8).max().orElse(0));
    }
    public static double maxScroll(List<ClassicDeveloperLayout.Node> nodes) {
        return contentHeight(nodes) - ClassicDeveloperTimeline.AREA_HEIGHT;
    }
    public static double clampScroll(List<ClassicDeveloperLayout.Node> nodes, double scroll) {
        return Double.isFinite(scroll) ? Math.max(0, Math.min(maxScroll(nodes), scroll)) : 0;
    }
    public static boolean inViewport(double x, double y) {
        return ClassicDeveloperTimeline.hit(x,y,ClassicDeveloperTimeline.AREA_X,ClassicDeveloperTimeline.AREA_Y,
                ClassicDeveloperTimeline.AREA_WIDTH,ClassicDeveloperTimeline.AREA_HEIGHT);
    }
    /** Shared by rendered hover and actual click handling, including the scissor boundary. */
    public static boolean hit(ClassicDeveloperLayout.Node node, double x, double y, double dx, double dy, double scroll) {
        return inViewport(x,y) && ClassicDeveloperTimeline.hit(x,y,
                ClassicDeveloperTimeline.AREA_X+node.x()-dx,
                ClassicDeveloperTimeline.AREA_Y+node.y()-dy-scroll,16,16);
    }

    /** Orthogonal row/column channels clear every unrelated full-hover circle at native line width. */
    public static List<ClassicDeveloperTimeline.Edge> dependency(List<ClassicDeveloperLayout.Node> nodes,
                                                               ClassicDeveloperLayout.Node child, double reveal) {
        return dependency(nodes,child,child.parent(),reveal);
    }
    /** A focused secondary canonical prerequisite uses the same safe channels as the primary parent. */
    public static List<ClassicDeveloperTimeline.Edge> dependency(List<ClassicDeveloperLayout.Node> nodes,
                                                               ClassicDeveloperLayout.Node child,String parentId,double reveal) {
        var parent = nodes.stream().filter(n -> n.id().equals(parentId)).findFirst();
        if (parent.isEmpty()) return List.of();
        double ax=parent.get().x()+8, ay=parent.get().y()+8, bx=child.x()+8, by=child.y()+8;
        double side=ax+COLUMN_STEP/2;
        double[][] points={{ax,ay},{ax,ay+CHANNEL},{side,ay+CHANNEL},
                {side,by+CHANNEL},{bx,by+CHANNEL},{bx,by}};
        var path=new ArrayList<double[]>();
        for(var point:points)if(path.isEmpty()||Math.hypot(point[0]-path.getLast()[0],point[1]-path.getLast()[1])>1e-9)path.add(point);
        // Trim endpoint caps within their own icon rings; zero-length segments have already been removed.
        inset(path.getFirst(),path.get(1));inset(path.getLast(),path.get(path.size()-2));
        double length=0;
        for(int i=1;i<path.size();i++)length+=Math.hypot(path.get(i)[0]-path.get(i-1)[0],path.get(i)[1]-path.get(i-1)[1]);
        double remaining=length*ClassicDeveloperTimeline.clamp(reveal);
        var result=new ArrayList<ClassicDeveloperTimeline.Edge>();
        for(int i=1;i<path.size()&&remaining>0;i++){
            var a=path.get(i-1);var b=path.get(i);double dx=b[0]-a[0],dy=b[1]-a[1],norm=Math.hypot(dx,dy);
            if(norm<1e-9)continue;
            double fraction=Math.min(1,remaining/norm),half=ClassicDeveloperTimeline.LINK_WIDTH/2;
            result.add(new ClassicDeveloperTimeline.Edge(a[0],a[1],a[0]+dx*fraction,a[1]+dy*fraction,dy/norm*half,-dx/norm*half));
            remaining-=norm;
        }
        return List.copyOf(result);
    }
    private static void inset(double[] endpoint,double[] toward) {
        double dx=toward[0]-endpoint[0],dy=toward[1]-endpoint[1],norm=Math.hypot(dx,dy);
        endpoint[0]+=dx/norm*ClassicDeveloperTimeline.LINK_INSET;
        endpoint[1]+=dy/norm*ClassicDeveloperTimeline.LINK_INSET;
    }
}

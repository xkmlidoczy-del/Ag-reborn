package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.highpower.HighPowerSkills;
import java.awt.geom.Line2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Pure geometry and actual navigation contracts; this does not claim rendered-client acceptance. */
public final class UnifiedSkillTreeLayoutRegressionTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void near(double actual,double expected,String message){check(Math.abs(actual-expected)<1e-8,message+" actual="+actual);}
    private static double length(List<ClassicDeveloperTimeline.Edge> edges){return edges.stream().mapToDouble(e->Math.hypot(e.x1()-e.x0(),e.y1()-e.y0())).sum();}
    public static void main(String[] args)throws Exception{
        SkillTreePresentationRegressionTest.main(args);
        SkillCatalog.resetConfiguration();
        try{
            int total=0;
            for(String category:DevelopmentActions.CATEGORIES){
                var source=ClassicDeveloperLayout.category(category);var merged=UnifiedSkillTreeLayout.category(category);total+=merged.size();
                check(merged.size()==source.size(),category+" includes every classic and extension node exactly once");
                var identities=new HashSet<String>();
                for(var node:merged){
                    check(identities.add(node.id()),"unique category identity "+category+"/"+node.id());
                    var original=source.stream().filter(n->n.id().equals(node.id())).findFirst().orElseThrow();
                    check(node.category().equals(original.category())&&node.index()==original.index()&&node.level()==original.level()
                            &&node.parent().equals(original.parent())&&node.enabled()==original.enabled()&&node.conditions().equals(original.conditions())
                            &&node.hintIcon().equals(original.hintIcon())&&node.nameKey().equals(original.nameKey())&&node.descriptionKey().equals(original.descriptionKey()),
                            "layout preserves complete learning identity/conditions/assets "+node.id());
                    near(node.x(),UnifiedSkillTreeLayout.COLUMN_X+(node.level()-1)*UnifiedSkillTreeLayout.COLUMN_STEP,"same-tier column "+node.id());
                    boolean originalAdvanced=node.level()>=4&&!HighPowerSkills.contains(node.id());
                    check(UnifiedSkillTreeLayout.signature(node)==originalAdvanced,"only original advanced skills receive signature gold frame "+node.id());
                    for(var other:merged)if(!other.id().equals(node.id())){
                        check(Math.hypot(node.x()-other.x(),node.y()-other.y())>2*UnifiedSkillTreeLayout.HOVER_RADIUS,
                                "full-hover circles cannot overlap "+node.id()+"/"+other.id());
                        if(originalAdvanced&&other.level()==node.level()&&HighPowerSkills.contains(other.id()))
                            check(node.y()<other.y(),"original advanced skill stays first in its tier "+node.id());
                    }
                    if(!node.parent().isEmpty())check(merged.stream().anyMatch(n->n.id().equals(node.parent())),"parent retained "+node.id());
                    double scroll=UnifiedSkillTreeLayout.clampScroll(merged,node.y()+8-139d/2);
                    for(double dx:new double[]{-5,0,5})for(double dy:new double[]{-5,0,5}){
                        double cx=128+node.x()+8-dx,cy=18+node.y()+8-dy-scroll;
                        check(cx-UnifiedSkillTreeLayout.HOVER_RADIUS>=128&&cx+UnifiedSkillTreeLayout.HOVER_RADIUS<=385
                                &&cy-UnifiedSkillTreeLayout.HOVER_RADIUS>=18&&cy+UnifiedSkillTreeLayout.HOVER_RADIUS<=157,
                                "every full-hover node reachable inside viewport including maximum parallax "+node.id());
                        check(UnifiedSkillTreeLayout.hit(node,cx,cy,dx,dy,scroll),"rendered center clicks same scrolled node "+node.id());
                        check(!UnifiedSkillTreeLayout.hit(node,cx+16,cy,dx,dy,scroll),"hit does not extend outside icon "+node.id());
                    }
                    check(!UnifiedSkillTreeLayout.hit(node,130,17,0,0,scroll),"top clipped region never clickable "+node.id());
                    check(!UnifiedSkillTreeLayout.hit(node,130,157,0,0,scroll),"bottom clipped region never clickable "+node.id());
                    var full=UnifiedSkillTreeLayout.dependency(merged,node,1);
                    check(node.parent().isEmpty()==full.isEmpty(),"exact parent-to-child link presence "+node.id());
                    for(double reveal:new double[]{0,.01,.25,.5,.9,1}){
                        var edges=UnifiedSkillTreeLayout.dependency(merged,node,reveal);
                        near(length(edges),length(full)*reveal,"continuous full-path reveal "+node.id());
                        for(int i=0;i<edges.size();i++){
                            var edge=edges.get(i);
                            near(Math.hypot(edge.nx(),edge.ny()),ClassicDeveloperTimeline.LINK_WIDTH/2,"native link width "+node.id());
                            check(Double.isFinite(edge.x0()+edge.y0()+edge.x1()+edge.y1()),"finite link coordinates "+node.id());
                            check(Math.min(edge.x0(),edge.x1())-Math.abs(edge.nx())-5>=0
                                    &&Math.max(edge.x0(),edge.x1())+Math.abs(edge.nx())+5<=257
                                    &&Math.min(edge.y0(),edge.y1())-Math.abs(edge.ny())-5>=0
                                    &&Math.max(edge.y0(),edge.y1())+Math.abs(edge.ny())+5<=UnifiedSkillTreeLayout.contentHeight(merged),"link fits scroll canvas "+node.id());
                            if(i>0){near(edge.x0(),edges.get(i-1).x1(),"contiguous x");near(edge.y0(),edges.get(i-1).y1(),"contiguous y");}
                            for(var other:merged)if(!other.id().equals(node.id())&&!other.id().equals(node.parent()))
                                check(Line2D.ptSegDist(edge.x0(),edge.y0(),edge.x1(),edge.y1(),other.x()+8,other.y()+8)
                                                >UnifiedSkillTreeLayout.HOVER_RADIUS+ClassicDeveloperTimeline.LINK_WIDTH/2,
                                        "dependency cannot cross unrelated hover circle "+node.id()+"/"+other.id());
                        }
                    }
                    var nav=new HighPowerDeveloperLayout.Navigation();nav.synchronize(category);
                    check(nav.selectIn(node.id(),merged),"classic and extension select immediately in one tree "+node.id());
                    check(!nav.selectIn(node.id(),merged)&&nav.beginLearning()&&!nav.beginLearning(),"selection/pending cannot duplicate "+node.id());
                    check(nav.back()==HighPowerDeveloperLayout.Back.BUSY&&!nav.synchronize(category)&&nav.pending(),"same snapshot retains pending attempt "+node.id());
                    nav.finishLearning();check(nav.back()==HighPowerDeveloperLayout.Back.SELECTION&&nav.back()==HighPowerDeveloperLayout.Back.CLOSE_SCREEN,"no extra page in Back path "+node.id());
                }
                near(UnifiedSkillTreeLayout.clampScroll(merged,-100),0,"negative scroll bounded");
                near(UnifiedSkillTreeLayout.clampScroll(merged,Double.NaN),0,"corrupt scroll bounded");
                near(UnifiedSkillTreeLayout.clampScroll(merged,1e6),UnifiedSkillTreeLayout.maxScroll(merged),"bottom scroll bounded");
            }
            check(total==SkillCatalog.REGISTERED.size(),"entire registered catalog covered by all enabled unified trees");
            check(UnifiedSkillTreeLayout.category("unknown").isEmpty(),"unknown category has no invented tree");
            wrongCategoryAndDisabledSelection();learningGatesRemainCanonical();productionIntegration();
        }finally{SkillCatalog.resetConfiguration();}
        System.out.println("UnifiedSkillTreeLayoutRegressionTest: "+checks+" checks passed");
    }
    private static void wrongCategoryAndDisabledSelection(){
        var nav=new HighPowerDeveloperLayout.Navigation();nav.synchronize("electromaster");
        check(!nav.selectIn(HighPowerSkills.CORNER_EXCHANGE,UnifiedSkillTreeLayout.category("teleporter")),"foreign node list cannot select another category");
        check(!nav.selectIn("nonexistent",List.of())&&!nav.selectIn(null,null),"invalid selection rejected");
        var old=UnifiedSkillTreeLayout.category("teleporter");nav.synchronize("teleporter");
        SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("teleporter",HighPowerSkills.CORNER_EXCHANGE),new ClassicSkillConfiguration.Flags(false,false)));
        check(!nav.selectIn(HighPowerSkills.CORNER_EXCHANGE,old),"stale enabled display cannot bypass current disabled skill configuration");
        SkillCatalog.resetConfiguration();
        check(nav.selectIn(HighPowerSkills.CORNER_EXCHANGE,UnifiedSkillTreeLayout.category("teleporter"))&&nav.beginLearning(),"fresh matching category selection");
        check(nav.synchronize("meltdowner")&&!nav.pending()&&nav.selected().isEmpty(),"category replacement clears pending selection");
    }
    private static void learningGatesRemainCanonical(){
        var s=new AbilityProgress();s.changeCategoryClassic("teleporter");s.setLevel(5);s.setActivateState(true);
        var skill=SkillCatalog.find("teleporter",HighPowerSkills.CORNER_EXCHANGE).orElseThrow();
        check(!DevelopmentActions.canLearn(s,DeveloperType.ADVANCED,skill),"merged display cannot bypass prerequisite");
        s.learn(HighPowerSkills.RETURN_MARK);s.setLevel(2);
        check(!DevelopmentActions.canLearn(s,DeveloperType.ADVANCED,skill),"merged display cannot bypass level");
        s.setLevel(3);check(!DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,skill),"merged display cannot bypass developer tier");
        check(DevelopmentActions.canLearn(s,DeveloperType.NORMAL,skill),"canonical paid-development eligibility remains available");
    }
    private static void productionIntegration()throws Exception{
        String s=Files.readString(Path.of("src/main/java/cn/academy/port/client/ClassicDeveloperScreen.java"));
        check(s.contains("return UnifiedSkillTreeLayout.category(AcademyClient.state.category)")&&!s.contains("switchPage(")&&!s.contains("highPowerTitle("),"screen renders one category tree with no extension tab or title");
        check(s.contains("UnifiedSkillTreeLayout.dependency(category,node,reveal.line())")&&!s.contains("ClassicDeveloperTimeline.edge(128+node"),"actual tree draws safe routed dependencies");
        check(s.contains("graphics.enableScissor(")&&s.contains("graphics.disableScissor()")&&s.contains("mouseScrolled("),"scrolling tree is clipped");
        check(s.contains("over=cover==null&&UnifiedSkillTreeLayout.hit(node,mx,my,dx,dy,treeScroll)")
                &&s.contains("if(UnifiedSkillTreeLayout.hit(node,x,y,dx,dy,treeScroll)&&navigation.selectIn(node.id(),pageNodes())"),"actual hover and click share viewport/scroll geometry");
        check(s.contains("treeScroll=0;created=now()")&&s.contains("UnifiedSkillTreeLayout.signature(node)"),"category switch resets scroll and original advanced frame is rendered");
    }
}

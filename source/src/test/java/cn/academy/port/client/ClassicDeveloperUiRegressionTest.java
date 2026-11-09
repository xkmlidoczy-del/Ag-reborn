package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.develop.DeveloperType;
import java.util.HashSet;
import java.util.List;

/** No Minecraft/OpenGL initialization. Tests exact layout/data/timeline/terminal semantics. */
public final class ClassicDeveloperUiRegressionTest {
    private static int assertions;
    private static void yes(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private static void near(double actual,double expected,String label){yes(Math.abs(actual-expected)<1e-9,label+" actual="+actual);}
    public static void main(String[] args){
        for(int percentage=0;percentage<=100;percentage++){float original=percentage/100f;yes(ClassicDeveloperTimeline.experiencePercent((double)original)==(int)(original*100f),"source Float percentage multiplication before truncation "+percentage);}
        yes(ClassicDeveloperTimeline.experiencePercent(.01F)==1,"one genuine BodyIntensify use displays1percent");
        yes(ClassicDeveloperTimeline.experiencePercent((double).01F*3)==3,"three source Float gains display3percent");
        yes(ClassicDeveloperTimeline.experiencePercent(Double.NaN)==0,"corrupt display value remains bounded");
        yes(cn.academy.port.SkillAvailability.LEARNABLE.containsAll(cn.academy.port.preset.PresetSkills.IMPLEMENTED),"all implemented active skills advertised for real learning");
        for(String id:cn.academy.port.preset.PresetSkills.IMPLEMENTED)yes(SkillCatalog.ALL.stream().anyMatch(skill->skill.id().equals(id)),"every canonical implemented skill retains its original source catalog identity "+id);
        for(String id:cn.academy.port.SkillAvailability.LEARNABLE)yes(SkillCatalog.ALL.stream().anyMatch(skill->skill.id().equals(id)),"every classic learnable entry retains its canonical source identity "+id);
        for(String id:cn.academy.port.SkillAvailability.REGISTERED_LEARNABLE)yes(SkillCatalog.REGISTERED.stream().anyMatch(skill->skill.id().equals(id)),"implemented learning entry has a registered catalog identity "+id);
        try{
            for(String source:List.of("client/ClassicDeveloperScreen.java","machine/MachineDeveloperSessions.java"))yes(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port",source)).contains("cn.academy.port.SkillAvailability.REGISTERED_LEARNABLE"),"physical GUI/session derives the common learning registry "+source);
            yes(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port/AcademyGameplay.java")).contains("SkillAvailability.learnable(request.value())"),"portable ingress derives common learning registry");
        }catch(java.io.IOException failure){throw new AssertionError("learning registry integration source check",failure);}
        var attemptedLevel=DeveloperLevelAttempt.capture(1);
        yes(attemptedLevel.targetLevel()==2&&attemptedLevel.stimulations()==10&&attemptedLevel.canStartAt(1),"LV1 confirmation captures one LV2 attempt and ten stimulations");
        var advancing=new AbilityProgress();advancing.changeCategoryClassic("electromaster");advancing.setLevel(2);
        yes(attemptedLevel.targetLevel()==2&&attemptedLevel.stimulations()==10,"completed live LV2 state cannot relabel old attempt as LV3");
        near(DeveloperType.PORTABLE.estimatedConsumption(attemptedLevel.stimulations()),7500,"completion retains the original quoted IF cost");
        yes(!attemptedLevel.canStartAt(advancing.level),"stale open cover cannot request a different level");
        yes(DeveloperLevelAttempt.capture(advancing.level).targetLevel()==3,"a newly opened cover independently targets the next level");
        try{String screen=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port/client/ClassicDeveloperScreen.java"));
            yes(screen.contains("levelName(current.levelAttempt.targetLevel())")&&screen.contains("\"abilities/condition/any\"+current.levelAttempt.targetLevel()")&&screen.contains("current.levelAttempt.stimulations()"),"actual completion label, source icon and quoted cost use the captured attempt");
        }catch(java.io.IOException failure){throw new AssertionError("level attempt integration source check",failure);}
        near(ClassicDeveloperTimeline.RIGHT_X,400-278-4,"right XML alignment");near(ClassicDeveloperTimeline.AREA_X,128,"area root x");
        var full=ClassicDeveloperTimeline.placement(960,540);near(full.scale(),1,"source scale");near(full.x(),280,"center x");near(full.y(),176.5,"center y");
        var small=ClassicDeveloperTimeline.placement(320,180);yes(small.scale()<1,"bounded narrow GUI");near(small.localX(small.x()+42*small.scale()),42,"invert mouse transform");
        near(ClassicDeveloperTimeline.parentAlpha(true,false),1,"learned opacity");near(ClassicDeveloperTimeline.parentAlpha(false,true),.7,"ready-parent opacity");near(ClassicDeveloperTimeline.parentAlpha(false,false),.25,"locked-parent opacity");
        for(int index=0;index<14;index++){
            var before=ClassicDeveloperTimeline.reveal(index*80+100,index);near(before.background(),0,"node offset");near(before.icon(),0,"node icon delayed");
            var after=ClassicDeveloperTimeline.reveal(index*80+100+1000,index);near(after.background(),1,"node final");near(after.radial(),1,"radial reveal");near(after.line(),1,"line reveal");
        }
        near(ClassicDeveloperTimeline.hoverScale(true,0),1,"hover start");near(ClassicDeveloperTimeline.hoverScale(true,50),1.1,"hover middle");near(ClassicDeveloperTimeline.hoverScale(true,100),1.2,"hover end");near(ClassicDeveloperTimeline.hoverScale(false,100),1,"unhover end");
        near(ClassicDeveloperTimeline.cover(0,false),0,"cover start");near(ClassicDeveloperTimeline.cover(100,false),.35,"cover midpoint");near(ClassicDeveloperTimeline.cover(200,false),.7,"cover final");near(ClassicDeveloperTimeline.cover(200,true),0,"cover closes");
        near(ClassicDeveloperTimeline.parallax(-1,320),-5,"left parallax clamp");near(ClassicDeveloperTimeline.parallax(320,320),5,"right parallax");
        var edge=ClassicDeveloperTimeline.edge(20,20,80,20,1);near(edge.x0(),75.8,"parent link inset");near(edge.x1(),40.2,"child link inset");near(Math.hypot(edge.nx(),edge.ny()),2.75,"half line width");
        yes(ClassicDeveloperTimeline.hit(1,1,0,0,16,16),"hit inside");yes(!ClassicDeveloperTimeline.hit(16,1,0,0,16,16),"half-open hit edge");
        yes(ClassicDeveloperLayout.NODES.size()==50,"full source catalog");var keys=new HashSet<String>();
        for(var node:ClassicDeveloperLayout.NODES){
            yes(keys.add(node.category()+":"+node.id()),"category-scoped unique identity");yes(node.x()>=0&&node.x()<257&&node.y()>=0&&node.y()<139,"source node in area");
            var skill=SkillCatalog.find(node.category(),node.id()).orElseThrow();yes(skill.level()==node.level(),"catalog level parity");yes(node.enabled(),"source default enable");
            yes(node.hintIcon().startsWith("academy:textures/abilities/"),"source icon namespace");
            yes(ClassicDeveloperUiRegressionTest.class.getResource("/assets/academy/"+node.hintIcon().substring("academy:".length()))!=null,"source icon exists");
            if(!node.parent().isEmpty())yes(ClassicDeveloperLayout.category(node.category()).stream().anyMatch(n->n.id().equals(node.parent())),"source parent exists");
            var condition=node.conditions().stream().filter(c->c.get("type").getAsString().equals("developer_type")).findFirst().orElseThrow();
            yes(condition.get("minimum").getAsString().equals(DeveloperType.minimumForSkill(node.level()).name().toLowerCase()),"minimum tier parity");
            for(var c:node.conditions())if(c.get("shouldDisplay").getAsBoolean())yes(ClassicDeveloperUiRegressionTest.class.getResource("/assets/academy/"+c.get("icon").getAsString().substring("academy:".length()))!=null,"condition icon exists");
        }
        var generic=ClassicDeveloperLayout.NODES.stream().filter(n->n.id().equals("brain_course")).toList();yes(generic.size()==4,"generic copied into4categories");for(var n:generic){near(n.x(),30,"generic x");near(n.y(),110,"generic y");}
        var state=new AbilityProgress();state.selectCategory("electromaster");state.setLevel(1);var category=ClassicDeveloperLayout.NODES.stream().filter(n->n.category().equals(state.category)).toList();
        long visible=category.stream().filter(n->n.enabled()&&(state.level>=n.level()||state.learned(n.id())||n.parent().isEmpty()||state.learned(n.parent()))).count();yes(visible==3,"source visibility roots remain visible");
        var terminal=new ClassicDeveloperConsole("AB\n","FAILED\n","Type learn\n",123);
        terminal.tick(0);yes(!terminal.ready(),"boot busy");terminal.tick(20);yes(terminal.visibleLines(20).getFirst().equals("A"),"20ms typewriter");terminal.tick(60);yes(terminal.visibleLines(60).getFirst().equals("AB"),"welcome text complete");
        for(long t=80;t<=4000;t+=20)terminal.tick(t);yes(terminal.ready(),"boot ends");terminal.type('l');terminal.type('x');terminal.backspace();terminal.type('e');terminal.type('a');terminal.type('r');terminal.type('n');yes(terminal.submit().equals("learn"),"console command");
        terminal.busy(true);terminal.type('x');yes(terminal.input().isEmpty(),"active process no duplicated input");terminal.replaceProgress("Progress: 35%");yes(terminal.visibleLines(5000).getLast().equals("Progress: 35%"),"progress rewritten once");terminal.busy(false);
        terminal.output("\n1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11");yes(terminal.visibleLines(5000).size()==10,"ten lines maximum including prompt");
        yes(ClassicDeveloperText.wrap("alpha beta",6,String::length).equals(List.of("alpha ","beta")),"source whole-word wrap");
        yes(ClassicDeveloperText.wrap("中文例子中文",3,String::length).getFirst().equals("中文例子"),"source CJK split keeps overflow character");
        yes(ClassicDeveloperText.wrap("longword",3,String::length).equals(List.of("longword")),"source unsplit long word");
        System.out.println("ClassicDeveloperUiRegressionTest: "+assertions+" assertions passed");
    }
}

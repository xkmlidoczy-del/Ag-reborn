package cn.academy.port.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Independent viewport, complete-text, scrollbar and actual screen hit-routing witnesses. */
public final class DeveloperSkillDetailsLayoutRegressionTest {
    private static int checks;
    private static void check(boolean value,String detail){checks++;if(!value)throw new AssertionError(detail);}
    private static void near(double actual,double expected,String detail){check(Math.abs(actual-expected)<1e-8,detail+" actual="+actual);}
    private static double width(String text){return text.codePoints().mapToDouble(cp->Character.isIdeographic(cp)?10:cp==' '?3:6).sum();}
    public static void main(String[] args)throws Exception{
        for(int[] viewport:new int[][]{{1920,1080},{960,540},{640,360},{427,240},{320,180}})for(int count=0;count<=6;count++){
            var p=DeveloperSkillDetailsLayout.placement(viewport[0],viewport[1],count);var panel=p.panel();
            check(p.scale()>0&&p.scale()<=1,"bounded positive detail scale");
            check(p.x()>=8-1e-8&&p.y()>=8-1e-8&&p.screenX(panel.width())<=viewport[0]-8+1e-8&&p.screenY(panel.height())<=viewport[1]-8+1e-8,"entire detail panel stays on screen");
            check(DeveloperSkillDetailsLayout.DESCRIPTION.within(panel)&&p.confirm().within(panel)&&p.back().within(panel)&&p.status().within(panel),"description, feedback and fixed buttons all fit panel");
            check(!DeveloperSkillDetailsLayout.DESCRIPTION.intersects(p.status())&&!DeveloperSkillDetailsLayout.DESCRIPTION.intersects(p.confirm())&&!p.status().intersects(p.confirm()),"long explanation cannot overlap fixed feedback/confirmation");
            check(!p.back().intersects(p.confirm()),"back and learn click regions remain separate");
            double cx=p.confirm().x()+p.confirm().width()/2,cy=p.confirm().y()+p.confirm().height()/2;
            check(p.confirm().contains(p.localX(p.screenX(cx)),p.localY(p.screenY(cy))),"rendered confirmation center is the same actual click target at every GUI scale");
            check(!p.confirm().contains(p.confirm().x()+p.confirm().width(),cy),"confirmation edge stays half-open");
            if(count>0)check(DeveloperSkillDetailsLayout.CONDITION_TOP+(count-1)*DeveloperSkillDetailsLayout.CONDITION_STEP+12<p.costY(),"every visible condition has its own row above quote");
        }
        for(String text:List.of("A short skill description.","中文技能描述。作用、条件和消耗均应完整保留。", "A verylongunbrokenidentifierwhichmustnotcrossitsdescriptionviewport plus another word.", "First paragraph.\n\n第二段。", "A 🛡️ shield and ✨ particles.")){
            var lines=DeveloperSkillDetailsLayout.wrap(text,80,DeveloperSkillDetailsLayoutRegressionTest::width);
            check(!lines.isEmpty(),"nonempty skill explanation retained");
            for(var line:lines)check(width(line)<=80,"every wrapped line fits its measured viewport: "+line);
            check(String.join("",lines).equals(text.replace("\n","")),"wrapping retains every non-newline character, including long words and supplementary code points");
        }
        check(DeveloperSkillDetailsLayout.wrap("a\n\nb",80,DeveloperSkillDetailsLayoutRegressionTest::width).equals(List.of("a","","b")),"explicit empty paragraph retained");
        check(DeveloperSkillDetailsLayout.wrap("a\\nb",80,DeveloperSkillDetailsLayoutRegressionTest::width).equals(List.of("a","b")),"legacy escaped paragraph separators displayed as real paragraphs");
        near(DeveloperSkillDetailsLayout.maxScroll(0),0,"empty description cannot scroll");
        near(DeveloperSkillDetailsLayout.maxScroll(100),1104,"long explanation has exact scroll range");
        near(DeveloperSkillDetailsLayout.clampScroll(100,Double.NaN),0,"corrupt scroll remains bounded");
        near(DeveloperSkillDetailsLayout.clampScroll(100,1e9),1104,"last line can reach bottom edge");
        near(DeveloperSkillDetailsLayout.clampScroll(100,-100),0,"first line can reach top edge");
        for(int flags=0;flags<32;flags++){
            boolean learned=(flags&1)!=0,requested=(flags&2)!=0,complete=(flags&4)!=0,disposed=(flags&8)!=0,implemented=(flags&16)!=0;
            check(!DeveloperSkillDetailsLayout.canConfirm(true,learned,requested,complete,disposed,implemented),"read-only application never renders or admits a learning confirmation");
            check(DeveloperSkillDetailsLayout.canConfirm(false,learned,requested,complete,disposed,implemented)==(!learned&&!requested&&!complete&&!disposed&&implemented),"real developer confirmation retains all original interaction fences");
        }
        check(!DeveloperSkillDetailsLayout.showLearningCost(true,false)&&!DeveloperSkillDetailsLayout.showLearningCost(false,true)&&DeveloperSkillDetailsLayout.showLearningCost(false,false),"only a real developer's unlearned skill shows a finite learning price");
        check(!DeveloperSkillDetailsLayout.showMastery(true,false,0)&&!DeveloperSkillDetailsLayout.showMastery(true,false,1),"generic training passives never display an unrelated mastery meter");
        check(!DeveloperSkillDetailsLayout.showMastery(false,true,0)&&!DeveloperSkillDetailsLayout.showMastery(false,true,Double.NaN),"unawarded or corrupt extension mastery cannot advertise a fake zero meter");
        check(DeveloperSkillDetailsLayout.showMastery(false,true,.002)&&DeveloperSkillDetailsLayout.showMastery(false,false,0),"actual earned extension mastery and classic skill progression remain visible");
        String source=Files.readString(Path.of("src/main/java/cn/academy/port/client/ClassicDeveloperScreen.java"));
        check(source.contains("if(current.kind.equals(\"skill\")){drawSkillDetails(graphics,canvas,current,mx,my);return;}")&&source.contains("var lines=detailsDescription(current)"),"actual learned and unlearned skill covers use the same complete description panel");
        check(!source.contains("screen.academy.highpower.learn_notice"),"long pack notice no longer competes with skill explanation");
        check(source.contains("detailButton(canvas,placement.confirm()")&&source.contains("placement.confirm().contains(x,y)){startCover();"),"real confirm rendering and mouse ingress share one rectangle");
        check(source.contains("cover.descriptionScroll=DeveloperSkillDetailsLayout.clampScroll")&&source.contains("DESCRIPTION.contains(placement.localX(mx),placement.localY(my))"),"detail wheel scroll is confined to explanation viewport");
        check(source.contains("if(energy()<estimated(current)){current.message=local(\"noenergy\");return;}")&&source.contains("local(\"learn_question\",Long.toString(Math.round(estimated(current))))"),"quoted IF and original finite-energy admission still share existing estimate");
        check(source.contains("!SkillCatalog.canLearn(state,skill)")&&source.contains("request(\"hp_learn\",attempt.wire())"),"canonical prerequisite and correlated server learning ingress remain intact");
        System.out.println("DeveloperSkillDetailsLayoutRegressionTest: "+checks+" checks passed");
    }
}

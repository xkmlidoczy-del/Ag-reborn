package cn.academy.port.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Focus must represent real prerequisites; cosmetic wire merging must not bridge real gaps. */
public final class SkillTreePresentationRegressionTest {
    private static int checks;
    private static void check(boolean value,String detail){checks++;if(!value)throw new AssertionError(detail);}
    public static void main(String[] args)throws Exception{
        var graph=Map.of("root",List.<String>of(),"left",List.of("root"),"right",List.of("root"),
                "advanced",List.of("left","right"),"sibling",List.of("left"),"unrelated",List.<String>of());
        var chain=SkillTreePresentation.prerequisiteChain("advanced",graph);
        check(chain.equals(Set.of(new SkillTreePresentation.Dependency("advanced","left"),new SkillTreePresentation.Dependency("advanced","right"),
                new SkillTreePresentation.Dependency("left","root"),new SkillTreePresentation.Dependency("right","root"))),"multiple actual prerequisites and all ancestors are highlighted exactly");
        check(chain.stream().noneMatch(edge->edge.child().equals("sibling")||edge.parent().equals("unrelated")),"sharing a parent does not highlight a sibling or unrelated skill");
        check(SkillTreePresentation.prerequisiteChain(null,graph).isEmpty()&&SkillTreePresentation.prerequisiteChain("root",graph).isEmpty(),"no hover and root skill produce no invented dependency");
        check(SkillTreePresentation.prerequisiteChain("a",Map.of("a",List.of("a","b","missing"),"b",List.of("a"))).size()==2,"cycles terminate and self/absent prerequisites are rejected");
        var wires=List.of(new SkillTreePresentation.Wire(8,10,0,10),new SkillTreePresentation.Wire(4,10,12,10),
                new SkillTreePresentation.Wire(12,10,14,10),new SkillTreePresentation.Wire(16,10,18,10),
                new SkillTreePresentation.Wire(8,5,8,15),new SkillTreePresentation.Wire(8,12,8,20),new SkillTreePresentation.Wire(2,2,2,2));
        var merged=SkillTreePresentation.mergeWires(wires);
        check(merged.equals(List.of(new SkillTreePresentation.Wire(0,10,14,10),new SkillTreePresentation.Wire(16,10,18,10),new SkillTreePresentation.Wire(8,5,8,20))),"overlapping/reversed/touching wires merge once, actual gap and perpendicular crossing remain");
        check(SkillTreePresentation.mergeWires(merged).equals(merged),"wire union is idempotent");
        check(SkillTreePresentation.mergeWires(java.util.stream.Stream.concat(wires.stream(),wires.stream()).toList()).equals(merged),"duplicate routes cannot brighten shared channels");
        for(var invalid:List.of(new SkillTreePresentation.Wire(0,0,1,1),new SkillTreePresentation.Wire(Double.NaN,0,1,0))){
            boolean rejected=false;try{SkillTreePresentation.mergeWires(List.of(invalid));}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"invalid visual geometry rejected before native drawing");
        }
        for(double x:new double[]{128,153,256,377,385})for(double y:new double[]{18,43,87,120,157}){
            var card=SkillTreePresentation.hoverCard(x,y,180,75);
            check(card.x()>=128&&card.y()>=18&&card.x()+card.width()<=385&&card.y()+card.height()<=157,"hover card remains within tree at every boundary");
            check(!card.contains(x,y),"card is placed away from the actual hovered pointer");
        }
        var colours=List.of("electromaster","meltdowner","teleporter","vecmanip").stream().map(SkillTreePresentation::accent).toList();
        check(Set.copyOf(colours).size()==4,"four categories have distinct accents");
        for(var colour:colours)check(colour.r()>=0&&colour.r()<=1&&colour.g()>=0&&colour.g()<=1&&colour.b()>=0&&colour.b()<=1,"normalised render colours");
        String screen=Files.readString(Path.of("src/main/java/cn/academy/port/client/ClassicDeveloperScreen.java"));
        check(screen.contains("SkillCatalog.requirements(skill)")&&screen.contains("SkillTreePresentation.prerequisiteChain(")&&screen.contains("dependency(category,child.get(),dependency.parent(),1)"),"actual focus traverses canonical configured prerequisites using safe production routing");
        check(screen.contains("SkillTreePresentation.mergeWires(wires)")&&screen.contains("if(hoveredNode!=null)drawHoverCard"),"real tree uses deduplicated wires and a bounded description/prerequisite card");
        System.out.println("SkillTreePresentationRegressionTest: "+checks+" checks passed");
    }
}

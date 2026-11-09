package cn.academy.port.client;
import cn.academy.port.client.FoldedMirrorMesh.Face;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.List;
import java.util.Collections;
public final class MulticoreCanopyCrownBudgetRegressionTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    public static void main(String[]args){
        Vector eye=new Vector(0,0,0),look=new Vector(0,0,1);
        Face face=new Face(new Vector(-1,-1,4),new Vector(-1,1,4),new Vector(1,1,4),new Vector(1,-1,4),0xFFFFFF,180,MulticoreCanopyMesh.RIM,-1);
        for(int used=0;used<=1536;used++)for(int labels:new int[]{0,24,96}){
            var budget=new MulticoreCanopyMesh.Budget();check(budget.reserve(used),"fixture");
            var original=Collections.nCopies(used,face);var group=Collections.nCopies(96,face);
            var result=MulticoreCanopyCrownBudget.append(original,List.of(group,group,group,group),eye,look,budget,labels);
            int expected=Math.min(4,Math.max(0,(1536-used-labels)/96))*96;
            check(result.size()==used+expected,"entire groups only, originals always retained");
            check(budget.used()==result.size()&&budget.used()<=1536,"scene hard cap");
            check(result.stream().filter(f->f==face).count()>=used,"all original object identities retained; nearClip may recreate decorative faces");
            if(expected==0)check(result==original,"exact unchanged fallback list");
        }
        var budget=new MulticoreCanopyMesh.Budget();var original=List.of(face);
        check(MulticoreCanopyCrownBudget.append(original,Collections.nCopies(5,List.of(face)),eye,look,budget,0)==original,"fifth context rejected");
        check(MulticoreCanopyCrownBudget.append(original,List.of(Collections.nCopies(193,face)),eye,look,budget,0)==original,"oversized raw group refused");
        check(budget.used()==0,"refusal spends no budget");
        System.out.println("PASS MulticoreCanopyCrownBudgetRegressionTest: "+checks+" checks");
    }
}

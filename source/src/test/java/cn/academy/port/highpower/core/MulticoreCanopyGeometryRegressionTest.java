package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.FoldedMirrorGeometry.Bounds;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import static cn.academy.port.highpower.core.MulticoreCanopyGeometry.*;

public final class MulticoreCanopyGeometryRegressionTest {
    private static int checks;
    public static void main(String[] args){
        Vector eye=new Vector(10,20,30),forward=new Vector(0,0,1);var frozen=cores(eye,forward);
        check(frozen.size()==6&&frozen.stream().map(Core::id).distinct().count()==6,"six stable identities");
        for(Core core:frozen){check(core.center().subtract(eye).length()<=2.5,"real centers within2.5m");check(core.center().z()==32,"frozen forward plane");}
        for(int i=0;i<6;i++)for(int j=i+1;j<6;j++)check(frozen.get(i).center().subtract(frozen.get(j).center()).length()>.6,"readable independent core gaps");
        for(Vector direction:new Vector[]{new Vector(0,1,0),new Vector(0,-1,0),new Vector(1,0,0),new Vector(-1,0,0)})check(cores(eye,direction).stream().allMatch(core->core.center().subtract(eye).length()<=2.5),"vertical and side eye poses finite");
        check(inCone(eye,forward,eye.add(new Vector(0,0,96))),"96m center accepted");
        check(!inCone(eye,forward,eye.add(new Vector(0,0,Math.nextUp(96.0)))),"beyond96 refused");
        check(inCone(eye,forward,eye.add(new Vector(Math.sin(Math.toRadians(9.9))*20,0,Math.cos(Math.toRadians(9.9))*20))),"inside10degree half cone");
        check(!inCone(eye,forward,eye.add(new Vector(Math.sin(Math.toRadians(10.1))*20,0,Math.cos(Math.toRadians(10.1))*20))),"outside10degree half cone");
        check(!inCone(eye,forward,eye.add(new Vector(0,0,-1)))&&!inCone(eye,forward,eye),"behind/zero refuse");
        Vector core=eye.add(new Vector(0,0,2));check(pathWithinRange(eye,core,eye.add(new Vector(0,0,96))),"actual closed96 path");
        check(!pathWithinRange(eye,eye.add(new Vector(1,0,2)),eye.add(new Vector(0,0,96))),"caster-core length charges side detour");
        Bounds solid=new Bounds(new Vector(0,0,0),new Vector(1,1,1));
        check(clip(solid,new Vector(-1,.5,.5),new Vector(0,.5,.5),0).orElseThrow().equals(new Vector(0,.5,.5)),"closed terminal solid tie");
        check(clip(solid,new Vector(.5,.5,.5),new Vector(.5,.5,.5),.18).isPresent(),"core body initial containment");
        check(clip(solid,new Vector(-1,1.18,.5),new Vector(2,1.18,.5),.18).isPresent(),"radius tangent included");
        check(clip(solid,new Vector(-1,1.181,.5),new Vector(2,1.181,.5),.18).isEmpty(),"outside radius excluded");
        check(clip(solid,new Vector(-1,.5,.5),new Vector(2,.5,.5),.181).isEmpty(),"unbounded radius rejected");
        System.out.println("MulticoreCanopyGeometryRegressionTest: "+checks+" checks passed");
    }
    private static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
}

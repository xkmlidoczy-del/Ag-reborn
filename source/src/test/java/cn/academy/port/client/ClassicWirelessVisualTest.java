package cn.academy.port.client;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

/** Pure JDK check: no Minecraft bootstrap, Gradle, client or server. */
public final class ClassicWirelessVisualTest {
    private static int assertions;
    private static void check(boolean value,String description){assertions++;if(!value)throw new AssertionError(description);}
    private static void close(double actual,double expected,String description){check(Math.abs(actual-expected)<1e-5,description+": "+actual+" != "+expected);}
    private static void malformed(String text)throws Exception{try{ClassicWirelessObj.parse(new StringReader(text));throw new AssertionError("Malformed OBJ accepted");}catch(IOException expected){assertions++;}}
    public static void main(String[] args)throws Exception{
        var input=ClassicWirelessVisualTest.class.getResourceAsStream("/assets/academy/models/matrix.obj");
        if(input==null)throw new IOException("Missing original matrix OBJ");
        var model=ClassicWirelessObj.parse(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8));
        check(model.groups().keySet().equals(new java.util.LinkedHashSet<>(List.of("Main","Core","Shield","Duplicate01","Duplicate02"))),"Original group names/order");
        check(model.require("Main").triangles().size()==212,"Main212triangles");check(model.require("Core").triangles().size()==4,"Core4triangles");check(model.require("Shield").triangles().size()==22,"Shield22triangles");
        check(model.require("Duplicate01").triangles().size()==22&&model.require("Duplicate02").triangles().size()==22,"OBJ duplicate22+22 preserved but renderer selects no duplicate groups");
        check(model.require("Core").bounds().maxY()>model.require("Main").bounds().minY(),"Distinct per-group geometry bounds");
        try{model.groups().clear();throw new AssertionError("Mutable group map");}catch(UnsupportedOperationException expected){assertions++;}
        var mesh=ClassicWirelessObj.parse(new StringReader("v 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 0 1\ng First\nf -3/-3 -2/-2 -1/-1\ng Second Third\nf 1/1 2/2 3/3\n"));
        check(mesh.groups().size()==3,"Multiple groups supported");close(mesh.require("First").triangles().getFirst().a().v(),.9995,"Source V flip/UV inset");
        check(mesh.require("First").triangles().getFirst().a().nz()==1,"Source geometric flat normal");
        close(mesh.require("Second").bounds().maxX(),1,"Referenced group bounds");
        malformed("g Empty\n");malformed("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 0 2 3\n");malformed("v NaN 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n");
        malformed("v 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nf 1/1 2 3\n");
        check(ClassicWirelessVisualRules.nodeEnergyLevel(0,15000)==0,"Emptynode0");check(ClassicWirelessVisualRules.nodeEnergyLevel(15000,15000)==4,"Fullnode4");
        for(int level=1;level<=4;level++){double threshold=(level-.5)/4;check(ClassicWirelessVisualRules.nodeEnergyLevel(threshold*15000,15000)==level,"Source round boundary"+level);check(ClassicWirelessVisualRules.nodeEnergyLevel((threshold-1e-8)*15000,15000)==level-1,"Below round boundary"+level);}
        check(ClassicWirelessVisualRules.nodeEnergyLevel(-1,15000)==0&&ClassicWirelessVisualRules.nodeEnergyLevel(99999,15000)==4,"Defensive range clamps");check(ClassicWirelessVisualRules.nodeEnergyLevel(1,0)==0&&ClassicWirelessVisualRules.nodeEnergyLevel(Double.NaN,15000)==0,"Invalid native energy cannot choose absent texture");
        check(ClassicWirelessVisualRules.nodeTop(false)==0&&ClassicWirelessVisualRules.nodeTop(true)==1,"Linked/unlinked top and bottom");
        for(int plates=-1;plates<=5;plates++)check(ClassicWirelessVisualRules.shieldCount(plates)==(plates==3?3:0),"Only exactlythreeplates render shields");
        for(long t=0;t<50000;t+=137){for(int i=0;i<3;i++){var shield=ClassicWirelessVisualRules.shield(t,i);close(shield.rotation(),(t/20d)%360+120*i,"Source shield phase");close(shield.height(),.1*Math.sin(t/900d+40*i),"Source hover phase radians40i");check(Math.abs(shield.height())<=.1,"Boundedhover");}double a=ClassicWirelessVisualRules.breatheAlpha(t);check(a>=.675&&a<=.85,"Breathing source alpha");}
        check(ClassicWirelessVisualRules.nodeAnimationFrame(true,0)==0&&ClassicWirelessVisualRules.nodeAnimationFrame(true,799)==0&&ClassicWirelessVisualRules.nodeAnimationFrame(true,800)==1&&ClassicWirelessVisualRules.nodeAnimationFrame(true,6400)==0,"Linked8frames800ms");
        check(ClassicWirelessVisualRules.nodeAnimationFrame(false,0)==8&&ClassicWirelessVisualRules.nodeAnimationFrame(false,2999)==8&&ClassicWirelessVisualRules.nodeAnimationFrame(false,3000)==9&&ClassicWirelessVisualRules.nodeAnimationFrame(false,6000)==8,"Unlinked2frames3000ms");
        var animation=new ClassicWirelessNodeAnimation();
        check(animation.update(false,0)==8&&animation.update(false,2999)==8&&animation.update(false,3000)==9,"Source one-step unlinked timer");
        check(animation.update(false,60000)==8,"Long render pause advances only one source frame");
        check(animation.update(true,60001)==0&&animation.update(true,60800)==0&&animation.update(true,60801)==1,"Link state resets source frame zero");
        check(animation.update(false,60802)==8,"Unlink state resets original unlinked frame eight");
        var north=ClassicWirelessVisualRules.matrixPoint(.2,.5,.4,ClassicWirelessVisualRules.OriginalFacing.NORTH);close(north.x(),.8,"NORTHpivot1 rotation180x");close(north.z(),.6,"NORTHrotation180z");
        var south=ClassicWirelessVisualRules.matrixPoint(.2,.5,.4,ClassicWirelessVisualRules.OriginalFacing.SOUTH);close(south.x(),.2,"SOUTHpivot0 rotation0x");close(south.z(),.4,"SOUTHrotation0z");
        var west=ClassicWirelessVisualRules.matrixPoint(.2,.5,.4,ClassicWirelessVisualRules.OriginalFacing.WEST);close(west.x(),.6,"WESTpivot1 rotation-90x");close(west.z(),.2,"WESTrotation-90z");
        var east=ClassicWirelessVisualRules.matrixPoint(.2,.5,.4,ClassicWirelessVisualRules.OriginalFacing.EAST);close(east.x(),.4,"EASTpivot0 rotation90x");close(east.z(),.8,"EASTpivot1 rotation90z");
        for(var facing:ClassicWirelessVisualRules.OriginalFacing.values()){var bounds=ClassicWirelessVisualRules.matrixBounds(model.require("Main").bounds(),facing);check(bounds.maxX()>bounds.minX()&&bounds.maxZ()>bounds.minZ(),"All four bounds valid");}
        close(ClassicWirelessVisualRules.histogramFraction(0,0),.03,"Zero capacity safe source lowerbarclamp");close(ClassicWirelessVisualRules.histogramFraction(0,100),.03,"Empty histogram3%minimum");close(ClassicWirelessVisualRules.histogramFraction(200,100),1,"Histogram max1");
        System.out.println("ClassicWirelessVisualTest: "+assertions+" assertions passed; original Main212/Core4/Shield22. Runtime rendering and visual parity not tested.");
    }
}

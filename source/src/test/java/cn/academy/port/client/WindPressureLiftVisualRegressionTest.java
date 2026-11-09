package cn.academy.port.client;

import cn.academy.port.highpower.core.WindPressureLiftRules.Mode;
import cn.academy.port.client.WindPressureLiftMesh.Point;
import java.util.UUID;

public final class WindPressureLiftVisualRegressionTest {
    private static int checks;
    private static void check(boolean c,String label) {checks++;if(!c) throw new AssertionError(label);}
    private static WindPressureLiftState.Frame frame(int owner,long input,long token,long sequence,boolean end,Mode mode) {
        return new WindPressureLiftState.Frame(new WindPressureLiftState.Identity(owner,new UUID(9,owner),input,token,3),sequence,
                new Point(0,64,0),mode,new Point(0,0,1),10,14,end);
    }
    public static void main(String[] args) {
        var state=new WindPressureLiftState();
        check(state.size()==0,"no receipt no controller");check(WindPressureLiftMesh.shape(null,0).vertexCount()==0,"no grant no flow");
        var start=frame(1,1,10,0,false,Mode.UP);
        check(state.accept(start,true),"authentic startup sequence zero");
        check(!state.accept(start,true),"duplicate startup denied");
        var active=frame(1,1,10,1,false,Mode.UP);check(state.accept(active,true),"fresh visual grant");
        check(!state.accept(frame(1,1,11,2,false,Mode.UP),true),"same input changed token denied");
        check(!state.accept(frame(1,1,10,2,false,Mode.SIDE),true),"frozen mode cannot change");
        var c=state.current(start.id().owner());var shape=WindPressureLiftMesh.shape(c,.5);
        check(shape.strokes().size()==44,"sparse open ring plus 32 flow strokes");
        check(shape.vertexCount()==352 && shape.vertexCount()<=WindPressureLiftMesh.MAX_CONTEXT_VERTICES,"both material passes counted");
        for(Mode mode:Mode.values()) for(int age=0;age<72;age++) {
            var f=frame(1,1,10,1,false,mode);var view=new WindPressureLiftState.Controller(f,true,age,0);
            var mesh=WindPressureLiftMesh.shape(view,.25);
            for(var stroke:mesh.strokes()) for(Point p:new Point[]{stroke.from(),stroke.to()}) {
                check(p.y()>=64 && p.y()<=70,"flow remains true six metre height");
                check(p.x()*p.x()+p.z()*p.z()<=9,"flow remains true six metre diameter");
                check(stroke.width()<=.023 && stroke.alpha()<=146,"fallback cannot become opaque wall");
            }
        }
        state.tick(true,candidate -> true);check(state.current(start.id().owner()).age()==0,"pause does not animate");
        check(state.accept(frame(1,1,10,1,true,Mode.UP),true),"same sequence END accepted");
        check(state.size()==0,"END has no residual");check(!state.accept(frame(1,1,10,99,false,Mode.UP),true),"late active cannot resurrect END");
        check(state.accept(frame(1,2,11,1,false,Mode.UP),true),"fresh owner after END");
        state.cancel(2,3);check(state.size()==0,"cancel immediate");check(!state.accept(frame(1,2,11,2,false,Mode.UP),true),"late queued cancel packet fenced");
        check(state.accept(frame(1,3,12,1,false,Mode.UP),true),"later input");state.clear();
        check(!state.accept(frame(1,3,12,2,false,Mode.UP),true),"reload cannot restore old flow");
        state.resetSession();
        for(int i=1;i<=4;i++)check(state.accept(frame(i,1,10+i,1,false,Mode.UP),i==1),"four controllers admitted");
        check(!state.accept(frame(5,1,15,1,false,Mode.UP),false),"fifth controller bounded");
        var budget=new WindPressureLiftMesh.Budget();
        for(var controller:state.controllers())check(budget.take(WindPressureLiftMesh.shape(controller,0).vertexCount()),"whole controller budget");
        check(budget.used()==1408 && budget.used()<=2048,"global vertices includes both passes");
        check(!budget.take(513),"oversize controller rejected whole");check(!budget.take(Integer.MAX_VALUE),"budget overflow rejected");
        var full=new WindPressureLiftMesh.Budget();for(int i=0;i<4;i++)check(full.take(512),"hard budget slot");
        check(!full.take(1),"hard 2048 vertex cap");
        for(int i=0;i<WindPressureLiftState.QUIET_TICKS;i++)state.tick(false,candidate -> true);
        for(var controller:state.controllers())check(WindPressureLiftMesh.shape(controller,0).vertexCount()==0,"quiet receipt produces no flow");
        state.tick(false,candidate -> false);check(state.size()==0,"entity identity or menu loss retires flow");
        Point world=new Point(29_999_999.875,72,-29_999_999.75),camera=new Point(29_999_999.5,71,-29_999_999.5);
        check(WindPressureLiftMesh.relative(world,camera).equals(new Point(.375,1,-.25)),"camera-relative subtraction occurs in doubles");
        var crossing=new WindPressureLiftMesh.Stroke(new Point(0,0,-2),new Point(0,0,2),.02,100);
        var clipped=WindPressureLiftMesh.clipNear(crossing,new Point(0,0,1),.01);
        check(clipped!=null && clipped.from().z()>=.089999999,"near eye crossing clipped");
        check(WindPressureLiftMesh.clipNear(crossing,new Point(Double.NaN,0,1),.01)==null,"forged camera refused");
        check(WindPressureLiftMesh.nearFade(new Point(0,0,0))==0,"no near eye opacity");
        check(WindPressureLiftMesh.MAX_PARTICLES==0,"no particle allocation");
        System.out.println("PASS WindPressureLiftVisualRegressionTest: "+checks+" checks");
    }
}

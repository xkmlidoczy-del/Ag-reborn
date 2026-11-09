package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.List;
import java.util.UUID;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Packet state, four-controller geometry budget, truthful cues, precision, clipping and compiled adapter seams. */
public final class YawPalmVisualRegressionTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static final Point O=new Point(0,1.6,0), E=new Point(0,1.6,6), D=new Point(-.7,0,0), Z=YawPalmState.ZERO;
    private static YawPalmState.Identity id(int actor,long input){return new YawPalmState.Identity(actor,new UUID(5,actor+1),71,input,input+100);}
    private static YawPalmState.Frame fire(YawPalmState.Identity id,int sequence,int slot,int status,Point delta){return new YawPalmState.Frame(id,sequence,YawPalmState.FIRE,slot,false,O,new Point(0,1.6,2+slot*2),status,delta);}
    private static YawPalmState.Frame end(YawPalmState.Identity id,int sequence){return new YawPalmState.Frame(id,sequence,YawPalmState.ENDED,0,false,Z,Z,0,Z);}
    private static YawPalmState.Controller cast(int status,Point delta){return new YawPalmState.Controller(id(1,1),true,false,O,List.of(new YawPalmState.Contact(E,status,delta)),0);}
    private static long roles(YawPalmMesh.Shape s,YawPalmMesh.Role role){return s.strokes().stream().filter(v->v.role()==role).count();}
    public static void main(String[] args)throws Exception{
        YawPalmState s=new YawPalmState();var a=id(1,1);
        check(s.accept(fire(a,1,0,1,D),true),"first authenticated receipt accepted");
        check(!s.accept(fire(a,1,0,1,D),true),"duplicate cannot replay");
        check(s.accept(fire(a,2,2,3,D),true),"callback-invalidated target slot may be skipped");
        check(!s.accept(fire(a,3,1,1,D),true),"target slot regression rejected");
        check(s.controllers().getFirst().contacts().size()==2,"confirmed subset only");
        check(s.accept(end(a,3),true)&&s.controllers().isEmpty(),"canonical end retires controller");
        check(!s.accept(fire(a,3,2,1,D),true),"closed receipt cannot resurrect");
        var b=id(1,2);check(s.accept(fire(b,1,2,1,D),true),"first live object may be frozen slot two");
        s.clear();check(!s.accept(fire(b,2,2,1,D),true),"reload clear preserves closed replay fence");
        var c=id(1,3);s.cancel(71,3);check(!s.accept(fire(c,1,0,1,D),true),"late acknowledgement fenced after abort");
        check(s.accept(fire(id(1,4),1,0,1,D),true),"new fresh input remains admissible");
        for(int i=0;i<80;i++)s.tick(true,x->true);check(s.controllers().getFirst().age()==0,"pause freezes age");
        for(int i=0;i<11;i++)s.tick(false,x->true);check(s.controllers().size()==1,"visible through tick eleven");
        check(s.tick(false,x->true).size()==1&&s.controllers().isEmpty(),"twelve tick lifetime exact");
        check(!s.accept(fire(id(1,4),2,1,1,D),true),"late segment cannot revive expired visual");
        check(s.accept(end(id(1,5),1),true)&&s.controllers().isEmpty(),"no-target end carries no fake cast");
        YawPalmState pool=new YawPalmState();
        for(int i=0;i<4;i++)check(pool.accept(fire(id(i+10,1),1,0,1,D),false),"bounded remote controller admitted");
        check(!pool.accept(fire(id(20,1),1,0,1,D),false)&&pool.controllers().size()==4,"fifth remote cannot overflow");
        check(pool.accept(fire(id(30,1),1,0,1,D),true)&&pool.controllers().size()==4,"local replaces one remote at capacity");
        check(pool.controllers().stream().anyMatch(YawPalmState.Controller::local),"remote flood cannot displace local");
        pool.tick(false,x->false);check(pool.controllers().isEmpty(),"death or world invalidation clears all controllers");
        check(!YawPalmState.valid(fire(a,1,0,1,new Point(3,0,0))),"applied delta obeys server vector cap");
        check(!YawPalmState.valid(fire(a,1,0,0,D)),"REFUSED cannot carry a fake applied delta");
        check(!YawPalmState.valid(new YawPalmState.Frame(a,1,0,0,false,O,new Point(0,1.6,cn.academy.port.highpower.core.YawPalmRules.RANGE + .01),1,D)),"range is strictly bounded");
        check(!YawPalmState.valid(new YawPalmState.Frame(a,1,0,0,false,O,E,1,new Point(Double.NaN,0,0))),"nonfinite delta rejected");
        check(!YawPalmState.valid(new YawPalmState.Frame(new YawPalmState.Identity(1,new UUID(0,0),71,1,1),1,0,0,false,O,E,1,D)),"invalid owner rejected");
        check(!YawPalmState.valid(new YawPalmState.Frame(a,5,0,0,false,O,E,1,D)),"sequence cap four");
        for(int status=0;status<=3;status++)for(boolean moved:new boolean[]{false,true}){
            if(status==0&&moved)continue;Point delta=moved?D:Z;
            var geometry=YawPalmMesh.shape(cast(status,delta),2);
            check(roles(geometry,YawPalmMesh.Role.PALM)>=15,"readable five-finger open palm contours");
            check(roles(geometry,YawPalmMesh.Role.PRESSURE)==8,"pressure is two bounded open arcs");
            check(roles(geometry,YawPalmMesh.Role.PATH)==1,"one thin confirmed axis");
            check((roles(geometry,YawPalmMesh.Role.MOMENTUM)>0)==moved,"side glyph iff actual native delta exists");
            check((roles(geometry,YawPalmMesh.Role.DAMAGE)>0)==(status==1),"damage burst only native DAMAGED");
            check((roles(geometry,YawPalmMesh.Role.ABSORBED)>0)==(status==2),"shield bars distinct from damage");
            var path=geometry.strokes().stream().filter(v->v.role()==YawPalmMesh.Role.PATH).findFirst().orElseThrow();
            check(path.from().x()==0&&path.to().x()==0&&path.to().z()<=6,"confirmed path never extrapolates");
        }
        var contacts=List.of(new YawPalmState.Contact(new Point(0,1.6,2),1,D),new YawPalmState.Contact(new Point(0,1.6,4),1,D),new YawPalmState.Contact(E,1,D));
        for(boolean crouch:new boolean[]{false,true})for(double age=0;age<12;age+=.125){
            var controller=new YawPalmState.Controller(a,true,crouch,O,contacts,0);var mesh=YawPalmMesh.shape(controller,age);
            check(mesh.vertexCount()<=YawPalmMesh.MAX_CONTROLLER_VERTICES&&mesh.vertexCount()*4<=2048,"whole four-controller two-layer vertex budget");
            var bounds=YawPalmMesh.bounds(controller);
            for(var stroke:mesh.strokes()){
                check(bounds.contains(stroke.from())&&bounds.contains(stroke.to()),"decorative bounds include every complete glyph");
                var core=YawPalmMesh.profile(stroke,true);var edge=YawPalmMesh.profile(stroke,false);
                check(edge.width()>core.width()&&edge.gray()<core.gray()&&core.gray()>=235,"substantial bright core with softer gray shoulder");
                if(stroke.role()==YawPalmMesh.Role.PALM)check(core.width()>=.04,"palm does not collapse to a sole hairline");
            }
        }
        check(YawPalmMesh.MAX_PARTICLES==0,"zero particle allocation");
        check(YawPalmMesh.shape(cast(1,D),12).strokes().isEmpty(),"no geometry after lifetime");
        Point camera=new Point(30_000_000,90,-30_000_000), p=new Point(30_000_000.125,90.5,-29_999_999.75);
        check(YawPalmMesh.relative(p,camera).equals(new Point(.125,.5,.25)),"double camera-relative precision at world border");
        var raw=new YawPalmMesh.Stroke(new Point(0,0,-1),new Point(0,0,2),Z,.14,255,YawPalmMesh.Role.PALM);
        var clipped=YawPalmMesh.clipNear(raw,new Point(0,0,1),.07);
        check(clipped!=null&&clipped.from().z()>=.14999999,"near clipping reserves whole shoulder half-width");
        check(YawPalmMesh.nearEyeFade(.2)==0&&YawPalmMesh.nearEyeFade(1)==1,"eye fade suppresses palm near eye without hiding distant axis");
        compiledAdapterChecks();
        System.out.println("YawPalmVisualRegressionTest: "+checks+" checks passed");
    }
    private static ClassNode read(String name)throws Exception{try(var in=YawPalmVisualRegressionTest.class.getClassLoader().getResourceAsStream("cn/academy/port/client/"+name+".class")){var n=new ClassNode();new ClassReader(in).accept(n,0);return n;}}
    private static void compiledAdapterChecks()throws Exception{
        ClassNode effects=read("YawPalmEffects"),material=read("YawPalmEffects$Material");
        boolean uuid=false,postSample=false,terrain=false,zeroParticle=true,bounds=false,relative=false;
        for(var method:effects.methods)for(var op:method.instructions){
            if(op instanceof MethodInsnNode call){
                uuid|=call.name.equals("getUUID");terrain|=call.name.equals("clip")&&call.owner.contains("ClientLevel");
                postSample|=call.name.equals("yawPalmInputMatches")&&call.desc.equals("(JJZ)Z");
                bounds|=call.owner.endsWith("YawPalmMesh")&&call.name.equals("bounds");
                relative|=call.owner.endsWith("YawPalmMesh")&&call.name.equals("relative");
                zeroParticle&=!call.name.contains("addParticle");
            }
        }
        check(uuid&&postSample,"compiled receiver checks UUID and exact Post crouch identity");
        check(terrain&&bounds&&relative&&zeroParticle,"compiled render uses terrain clip, complete decorative bounds, double relative, zero particles");
        boolean depth=false,color=false;
        for(var method:material.methods)for(var op:method.instructions)if(op instanceof FieldInsnNode f){depth|=f.name.equals("LEQUAL_DEPTH_TEST");color|=f.name.equals("COLOR_WRITE");}
        check(depth&&color,"compiled material depth-tests without depth writes");
        ClassNode client=read("AcademyClient");
        for(String hook:List.of("cancelPersistentHighPowerInputs","synchronizeSession","clearEffects","tick")){
            boolean call=false;for(var method:client.methods)if(method.name.equals(hook))for(var op:method.instructions)if(op instanceof MethodInsnNode m)
                call|=m.name.equals(hook.equals("clearEffects")?"clear":"yawPalmCancelInput")&&(m.owner.endsWith("AcademyClient")||m.owner.endsWith("YawPalmEffects"));
            check(call,"compiled lifecycle hook owns cancellation/clear: "+hook);
        }
    }
}

package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Exercises the actual input/transport/solid-geometry helpers, plus runtime authority source guards. */
public final class IronSandLanceVisualRegressionTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static UUID id(int value){return new UUID(0,value);}
    private static IronSandLanceGeometry.Point p(double x,double y,double z){return new IronSandLanceGeometry.Point(x,y,z);}
    private static IronSandLanceState.Pose pose(int index,int status){return new IronSandLanceState.Pose(index,p(0,0,0),p(0,0,1.2),p(0,0,.2),status,-1);}
    private static IronSandLanceState.Frame frame(int owner,long epoch,long input,long token,long sequence,int phase,boolean orbit,int charge,List<IronSandLanceState.Pose> poses){return new IronSandLanceState.Frame(new IronSandLanceState.Identity(id(owner),epoch,input,token),sequence,phase,orbit,charge,p(0,0,0),poses);}
    public static void main(String[] args)throws Exception{
        var inputState=new IronSandLanceInputState();var input=new HighPowerInput.Input(0,1,2,3);var next=new HighPowerInput.Input(0,2,2,3);
        inputState.begin(input,true);check(inputState.current().orbit(),"configured crouch start captures orbit");
        check(inputState.release(next)==null,"old or mismatched release cannot commit another input");
        check(inputState.release(input).orbit(),"release retains start mode without rereading crouch");
        check(inputState.release(input)==null,"duplicate release refused");
        check(inputState.cancelWhenBlocked(false)==null,"active volley/orbit survives normal key release");
        check(inputState.cancelWhenBlocked(true).equals(input),"screen/focus cancel retains the released identity");
        check(inputState.current()==null&&inputState.release(input)==null,"cancelled hold cannot release later");
        inputState.begin(next,false);check(inputState.cancel(input)==null&&!inputState.current().orbit(),"stale abort cannot replace the new attack mode");
        check(inputState.cancel(next).equals(next),"matching abort ends hold");
        inputState.begin(input,true);inputState.reset();check(inputState.current()==null,"new session clears input");
        var state=new IronSandLanceState();var start=frame(1,4,20,5,1,0,false,0,List.of(pose(0,0),pose(1,0),pose(2,0)));
        check(state.accept(start,true),"server assembly accepted as one controller");
        check(!state.accept(start,true),"duplicate sequence cannot restart charge");
        check(!state.accept(frame(1,4,20,5,2,0,true,1,start.lances()),true),"mode immutable within cast");
        check(!state.accept(frame(1,4,21,5,2,0,false,1,start.lances()),true),"token cannot change input");
        check(!state.accept(frame(1,5,20,5,2,0,false,1,start.lances()),true),"token cannot change epoch");
        check(state.accept(frame(1,4,20,5,2,0,false,12,start.lances()),true),"native12tick full charge accepted");
        check(!state.accept(frame(1,4,20,5,3,0,false,11,start.lances()),true),"charge cannot regress");
        check(!state.accept(frame(1,4,20,5,3,0,false,13,start.lances()),true),"charge clamp12 enforced");
        check(!state.accept(frame(1,4,20,5,3,0,false,12,List.of(pose(0,2))),true),"free charge cannot fabricate an impact");
        check(state.accept(frame(1,4,20,5,3,1,false,12,List.of(pose(0,1),pose(1,0),pose(2,0))),true),"three launch states share one server controller");
        check(!state.accept(frame(1,4,20,5,4,0,false,12,start.lances()),true),"late preview cannot replace active projectiles");
        check(!state.accept(frame(1,4,20,5,4,2,false,12,List.of(pose(0,1))),true),"attack mode cannot turn into orbit");
        check(state.accept(frame(1,4,20,5,4,1,false,12,List.of(pose(0,2))),true),"actual server hit accepted");
        check(!state.accept(frame(1,4,20,5,5,1,false,12,List.of(pose(0,1))),true),"hit lance cannot resume flying");
        check(state.accept(frame(1,4,20,5,5,3,false,12,List.of()),true)&&state.size()==0,"end retires complete controller");
        check(!state.accept(frame(1,4,20,5,99,1,false,12,List.of(pose(0,1))),true),"late active packet cannot resurrect ended cast");
        state.cancel(4,21);check(!state.accept(frame(1,4,21,6,1,0,true,0,start.lances()),true),"abort before first packet is fenced");
        check(state.accept(frame(1,4,22,7,1,2,true,12,List.of(pose(0,1))),true),"fresh orbit token accepted");
        check(!state.accept(frame(1,4,22,7,2,2,true,12,List.of(pose(0,2))),true),"orbit cannot render an invented damage hit");
        check(state.accept(frame(1,4,22,7,2,2,true,12,List.of(pose(0,4))),true),"actual arrow interception accepted");
        state.retire(state.current(id(1)).id());check(!state.accept(frame(1,4,22,7,3,2,true,12,List.of(pose(0,1))),true),"quiet retirement prevents replay");
        check(IronSandLanceState.valid(frame(1,4,22,8,1,2,true,12,List.of(new IronSandLanceState.Pose(0,p(0,0,80),p(0,0,81.2),p(0,0,81.2),3,-1),pose(1,1)))),"spent stationary orbit lance cannot invalidate the remaining moving owner controller");
        check(!IronSandLanceState.valid(frame(1,4,22,8,1,0,false,0,List.of(pose(0,0),pose(0,0)))),"duplicate lance index rejected");
        check(!IronSandLanceState.valid(frame(1,4,22,8,1,0,false,0,List.of(pose(3,0)))),"fourth lance rejected");
        check(!IronSandLanceState.valid(frame(1,4,22,8,1,1,false,12,List.of(new IronSandLanceState.Pose(0,p(0,0,0),p(0,0,4),p(0,0,0),1,-1)))),"oversized mesh rejected");
        check(!IronSandLanceState.valid(frame(1,4,22,8,1,1,false,12,List.of(new IronSandLanceState.Pose(0,p(0,0,0),p(Double.NaN,0,1),p(0,0,0),1,-1)))),"nonfinite transport rejected");
        for(int status:List.of(5,6,7))check(IronSandLanceState.valid(frame(1,4,22,8,1,1,false,12,List.of(new IronSandLanceState.Pose(0,p(0,0,0),p(0,0,1.2),p(0,0,0),status,21)))),"distinct damage/absorbed/refused contact transport preserved");
        check(!IronSandLanceState.valid(frame(1,4,22,8,1,1,false,12,List.of(new IronSandLanceState.Pose(0,p(0,0,0),p(0,0,1.2),p(0,0,0),3,21)))),"dispersed state must clear old entity contact");
        state.clear();check(state.accept(start,true),"new world owns fresh transport cache");
        for(int i=2;i<=64;i++)check(state.accept(frame(i,4,20,5+i,1,0,false,0,start.lances()),false),"bounded observer accepted");
        check(state.size()==64&&!state.accept(frame(65,4,20,70,1,0,false,0,start.lances()),false),"owner cap64");
        for(var axis:List.of(p(1,0,0),p(0,1,0),p(0,-1,0),p(0,0,-1),p(1,2,3))){
            var unit=axis.scale(1/axis.length());var tail=p(5,7,9);var head=tail.plus(unit.scale(1.2));
            for(double scale:List.of(.46,.78,1.0)){
                var mesh=IronSandLanceGeometry.lance(tail,head,scale);check(mesh.size()==4,"three solid triangular facets and back cap");
                for(var face:mesh)for(var vertex:List.of(face.a(),face.b(),face.c())){
                    var r=vertex.minus(tail);double along=r.dot(unit);double radial=r.minus(unit.scale(along)).length();
                    check(vertex.finite()&&along>=-1e-8&&along<=1.2+1e-8&&radial<=IronSandLanceGeometry.RADIUS*scale+1e-8,"all mesh vertices bounded to actual1.2m pose");
                }
            }
            for(int tick=0;tick<=12;tick++)for(int index=0;index<3;index++){
                var grains=IronSandLanceGeometry.gather(tail,tail,head,index,tick/12.0);check(grains.size()==16,"stable16grain allocation per lance");
                for(var grain:grains)check(grain.point().finite()&&grain.point().minus(tail).length()<1.7&&grain.size()<=.0181,"gather remains compact and finite");
            }
            for(int index=0;index<3;index++)for(int age=0;age<4;age++){
                var grains=IronSandLanceGeometry.impact(head,axis,index,age);check(grains.size()==4,"only4impact grains per lance");
                for(var grain:grains)check(grain.point().minus(head).length()<=.15,"impact confined to genuine server contact");
            }
        }
        check(IronSandLanceGeometry.CHARGE_GRAINS+3*IronSandLanceGeometry.HIT_GRAINS_PER_LANCE==60&&IronSandLanceGeometry.MAX_DECORATIVE_GRAINS<=64,"strict per-cast decorative budget60");
        check(IronSandLanceGeometry.lance(p(0,0,0),p(0,0,0),1).isEmpty(),"degenerate mesh rejected");
        check(!IronSandLanceGeometry.lance(p(0,0,-1),p(0,0,.2),1).isEmpty(),"counterexample: authoritative waiting muzzle creates a real cone crossing the eye");
        check(IronSandLanceGeometry.visibleLance(p(0,0,-1),p(0,0,.2),1,p(0,0,1)).isEmpty(),"waiting opaque shaft behind/inside the eye cannot become a screen-filling triangle wall");
        for(var forward:List.of(p(0,0,1),p(1,0,0),p(0,1,0),p(0,-1,0),p(1,2,3))){
            var unit=forward.scale(1/forward.length());var cameraBasis=IronSandLanceGeometry.basis(unit);
            for(double depth:List.of(-2.0,-.3,.0,.2,.59,.6,.61,1.0,2.0,48.0))for(double offset:List.of(-.25,0.0,.25)){
                var tail=unit.scale(depth).plus(cameraBasis.side().scale(offset));var head=tail.plus(unit.scale(1.2));
                var original=IronSandLanceGeometry.lance(tail,head,1);var visible=IronSandLanceGeometry.visibleLance(tail,head,1,forward);
                check(visible.size()<=8,"one clipped four-face body has at most eight triangles");
                if(depth>IronSandLanceGeometry.SOLID_NEAR_DEPTH+.01)check(visible.equals(original),"far solid body keeps original texture faces, dimensions and accepted positions");
                for(var face:visible)for(var vertex:List.of(face.a(),face.b(),face.c())){
                    check(vertex.finite()&&vertex.dot(unit)>=IronSandLanceGeometry.SOLID_NEAR_DEPTH-1e-12,"every emitted opaque vertex stays outside the visual near-eye safety plane");
                    check(Math.abs(vertex.minus(unit.scale(vertex.dot(unit))).length())<=Math.abs(offset)+IronSandLanceGeometry.RADIUS+1e-9,"clipping never invents expanded phantom geometry");
                }
                for(var face:visible){
                    var sourceFace=original.stream().filter(f->f.shade()==face.shade()).findFirst().orElseThrow();
                    var beforeNormal=sourceFace.b().minus(sourceFace.a()).cross(sourceFace.c().minus(sourceFace.a()));
                    var afterNormal=face.b().minus(face.a()).cross(face.c().minus(face.a()));
                    check(beforeNormal.dot(afterNormal)>0,"clipped triangle fan preserves the actual solid facet winding");
                }
            }
        }
        check(IronSandLanceGeometry.visibleLance(p(4,3,10),p(4,3,11.2),1,p(0,0,1)).equals(IronSandLanceGeometry.lance(p(4,3,10),p(4,3,11.2),1)),"third-person/observer camera sees unchanged far off-axis body");
        check(IronSandLanceGeometry.visibleLance(p(0,0,-10),p(0,0,-8.8),1,p(0,0,1)).isEmpty(),"a body completely behind the actual viewing camera emits no opaque triangles");
        check(IronSandLanceGeometry.visibleLance(p(0,0,1),p(0,0,2.2),1,p(0,0,0)).isEmpty(),"invalid camera direction fails closed");
        check(IronSandLanceGeometry.impact(p(0,0,0),p(0,0,1),0,4).isEmpty(),"flecks expire at4ticks");
        check(IronSandLanceGeometry.lerp(p(0,0,0),p(0,0,2),2).equals(p(0,0,2)),"interpolation never extrapolates past actual2m flight sample");
        check(IronSandLanceGeometry.clip(p(0,0,46),p(0,0,48),p(0,0,1),.009)!=null,"full48m magnetic axis not silently clipped at classic32m limit");
        check(IronSandLanceGeometry.clip(p(0,0,-1),p(0,0,-2),p(0,0,1),.009)==null,"behind-camera axes clipped");
        var near=IronSandLanceGeometry.clip(p(0,0,-1),p(0,0,1),p(0,0,1),.009);
        check(near!=null&&near.start().z()>=.209-1e-9&&near.halfWidth()<=.009,"near-plane clipping bounds complete ribbon width");
        for(double pitch:List.of(-89.0,-70.0,0.0,70.0,89.0)){
            double a=Math.toRadians(pitch);var basis=IronSandLanceGeometry.cameraBasis(p(0,Math.sin(a),Math.cos(a)),p(0,Math.cos(a),-Math.sin(a)));
            check(basis!=null&&Math.abs(basis.side().x()+1)<1e-9,"camera-up wrist basis keeps side at steep pitch");
        }
        var cancelFence=new IronSandLanceState();for(int i=1;i<=400;i++)cancelFence.cancel(9,i);
        check(!cancelFence.accept(frame(1,9,1,999,1,0,false,0,start.lances()),true),"old canceled begin cannot resurrect after hundreds of later cancellations");
        String client=Files.readString(Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        String effects=Files.readString(Path.of("src/main/java/cn/academy/port/client/IronSandLanceEffects.java"));
        check(client.contains("boolean orbit=down(Minecraft.getInstance().options.keyShift)")&&client.indexOf("ironLanceInputs.begin(identity,orbit)")<client.indexOf("hp_iron_lance_orbit_begin"),"configured physical crouch captured before request");
        check(client.indexOf("academy.highpower.iron_lance.mode_orbit")<client.indexOf("hp_iron_lance_orbit_begin"),"mode displayed before payment request without newHUD");
        String snapshotCleanup=client.substring(client.indexOf("private static void cleanupModern("),client.indexOf("private static void modernSnapshotFallback("));
        check(!snapshotCleanup.contains("ironLanceInputs.cancel")&&!snapshotCleanup.contains("hp_iron_lance_abort"),"ordinary CP snapshots cannot abort committed lances or send requests from silent classic cleanup");
        check(client.contains("ironLanceInputs.release(identity)!=null")&&client.contains("ironLanceInputs.cancel(identity)"),"release/abort use captured input identity");
        check(client.contains("ironLanceInputs.cancelWhenBlocked(inputBlocked)")&&client.contains("ironLanceInputs.reset();IronSandLanceEffects.clear()"),"focus/screen/session resets wired");
        check(effects.contains("mc.level!=captured")&&effects.contains("mc.getConnection()!=capturedConnection")&&effects.contains("sameInputSessionEpoch(capturedSession)")&&effects.contains("world.getEntity(m.owner.getId())!=m.owner"),"queued and live state fenced to world/session/entity");
        check(effects.contains("if(mc.isPaused())return")&&effects.contains("m.quiet>=QUIET_TICKS")&&effects.contains("STATE.retire(m.identity())"),"pause and quiet expiry cannot revive casts");
        check(effects.contains("MAX_VISIBLE_CONTEXTS=8")&&effects.contains("MAX_CONTEXTS=64")&&!effects.contains("addParticle")&&!effects.contains("setDeltaMovement")&&!effects.contains("setPos("),"bounded controller uses no cosmetic entities or gameplay writes");
        check(effects.contains("lance.previousHead()")&&effects.contains("IronSandLanceGeometry.lerp")&&effects.contains("marker.state.phase()==1"),"flight follows only accepted previous/current head samples");
        check(effects.contains("pose.status()>=2&&pose.status()!=3")&&effects.contains("impacts[pose.index()]==null"),"each lance can admit impact flecks once, only for authoritative contacts");
        check(effects.contains("modelView.set(event.getModelViewMatrix())")&&effects.contains("modelView.popMatrix()")&&effects.contains("IronSandLanceGeometry.clip"),"actual camera rotation restored and line near clipping retained");
        check(effects.contains("IronSandLanceGeometry.visibleLance(point(tail),point(head),size,point(cameraForward))"),"real released and gathering solid meshes both use clipped authoritative facets");
        check(effects.contains("charging&&marker.owner==mc.player&&mc.options.getCameraType().isFirstPerson()"),"compact hand view never changes released projectile or other caster origin");
        check(effects.contains("if(marker.fade<0)")&&effects.contains("if(marker.fade>=0||charge<.35)continue"),"cancel immediately removes solid hazard and magnetic axis");
        byte[] original=Files.readAllBytes(Path.of("src/main/resources/assets/academy/textures/abilities/electromaster/skills/iron_sand.png"));
        check(Arrays.equals(original,Files.readAllBytes(Path.of("src/main/resources/assets/academy/textures/abilities/electromaster/skills/iron_sand_lance.png"))),"new skill icon reuses unchanged licensed classic pixels");
        for(String locale:List.of("en_us","zh_cn","zh_tw","ja_jp")){
            String lang=Files.readString(Path.of("src/main/resources/assets/academy/lang/"+locale+".json"));
            for(String key:List.of("item.academy.iron_sand","ac.ability.electromaster.iron_sand_lance.name","ac.ability.electromaster.iron_sand_lance.desc","academy.highpower.iron_lance.mode_orbit","academy.highpower.iron_lance.mode_attack"))check(lang.contains("\""+key+"\""),"required translated medium/mode/skill text in"+locale);
        }
        System.out.println("PASS "+checks+" NEW131 input, transport, solid geometry, particle-budget and authority checks");
    }
}

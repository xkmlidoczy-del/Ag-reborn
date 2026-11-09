package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import static cn.academy.port.client.TrajectoryReturnState.*;

/** Real receipt chronology, complete geometry coverage, lifetime and compiled renderer wiring. */
public final class TrajectoryReturnVisualRegressionTest {
    private static int checks;
    private static final Point O=new Point(0,1.6,3),V=new Point(0,0,1),Z=ZERO;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Identity id(int owner,long input){return new Identity(owner,new UUID(7,owner),71,input,input+100);}
    private static Frame frame(Identity id,long seq,int kind,int slot,Point a,Point b,Point v,int status){return new Frame(id,seq,kind,slot,a,b,v,status);}
    private static Frame reverse(Identity id,long seq,int slot,Point p){return frame(id,seq,REVERSE,slot,p,p,V,COMMITTED);}
    private static Frame end(Identity id,long seq){return frame(id,seq,ENDED,0,Z,Z,Z,REFUSED);}
    private static long roles(TrajectoryReturnMesh.Shape mesh,TrajectoryReturnMesh.Role role){return mesh.strokes().stream().filter(s->s.role()==role).count();}
    private static Controller first(TrajectoryReturnState s){return s.controllers().getFirst();}
    private static Point z(double n){return new Point(0,1.6,n);}
    public static void main(String[] args)throws Exception {
        Identity a=id(1,1);var state=new TrajectoryReturnState();
        check(!state.accept(frame(a,1,FLIGHT,0,O,z(4),V,COMMITTED),true),"orphan FLIGHT never invents a reversal");
        check(state.accept(reverse(a,1,0,O),true),"authenticated reverse opens visual");
        check(first(state).slots().getFirst().trail().isEmpty(),"reversal creates no predicted trail");
        var mesh=TrajectoryReturnMesh.shape(first(state),0);
        check(roles(mesh,TrajectoryReturnMesh.Role.INCOMING)==3&&roles(mesh,TrajectoryReturnMesh.Role.REVERSED)==3,"separate readable before and after arrowheads");
        check(roles(mesh,TrajectoryReturnMesh.Role.PRESSURE)==6,"broad open convergence pressure");
        check(roles(mesh,TrajectoryReturnMesh.Role.DAMAGE)==0&&roles(mesh,TrajectoryReturnMesh.Role.TRAIL)==0,"reversal cannot fabricate impact or movement");
        var incoming=mesh.strokes().stream().filter(s->s.role()==TrajectoryReturnMesh.Role.INCOMING).findFirst().orElseThrow();
        var outgoing=mesh.strokes().stream().filter(s->s.role()==TrajectoryReturnMesh.Role.REVERSED).findFirst().orElseThrow();
        check(incoming.to().z()<incoming.from().z()&&outgoing.to().z()>outgoing.from().z(),"before and after vectors visibly oppose");
        check(outgoing.from().distanceSquared(outgoing.to())>.6,"default reversal shaft has visible world size");
        check(!state.accept(reverse(a,1,0,O),true),"duplicate rejected without changing controller");
        state.tick(false,c->true);
        check(state.accept(frame(a,2,FLIGHT,0,O,z(4),V,COMMITTED),true),"first actual native segment accepted");
        check(first(state).slots().getFirst().trail().size()==1,"only receipt produces trail");
        for(int i=0;i<8;i++) {
            state.tick(false,c->true);
            check(state.accept(frame(a,3+i,FLIGHT,0,z(4+i),z(5+i),V,COMMITTED),true),"continuous server flight accepted");
            check(first(state).slots().getFirst().trail().size()<=MAX_SEGMENTS,"trail is bounded at every receipt");
            for(var stroke:TrajectoryReturnMesh.shape(first(state),first(state).age()).strokes())if(stroke.role()==TrajectoryReturnMesh.Role.TRAIL)
                check(stroke.to().z()<=5+i,"trail cannot extrapolate beyond last server position");
        }
        for(int i=0;i<3;i++)state.tick(false,c->true);
        check(first(state).slots().getFirst().trail().isEmpty(),"receipt gap clears visible trail");
        check(state.accept(frame(a,11,FLIGHT,0,z(12),z(13),V,COMMITTED),true),"late contiguous wire sequence can resume");
        check(first(state).slots().getFirst().trail().isEmpty(),"late segment does not bridge paused receipt gap");
        state.tick(false,c->true);check(state.accept(frame(a,12,FLIGHT,0,z(13),z(14),V,COMMITTED),true),"next clean pair resumes true segment");
        check(first(state).slots().getFirst().trail().size()==1,"gap continuation starts fresh");
        check(state.accept(frame(a,13,FLIGHT,0,z(20),z(21),V,COMMITTED),true),"discontinuous native position is observed without a joined path");
        check(first(state).slots().getFirst().trail().isEmpty(),"teleport discontinuity clears trail");
        check(!state.accept(frame(a,15,FLIGHT,0,z(21),z(22),V,COMMITTED),true)&&state.controllers().isEmpty(),"wire sequence gap closes cast rather than joining unknown motion");
        check(!state.accept(frame(a,14,FLIGHT,0,z(21),z(22),V,COMMITTED),true),"late gap repair cannot revive retired visual");
        for(int status=REFUSED;status<=ABSORBED;status++) {
            var contact=new TrajectoryReturnState();Identity id=id(2+status,1);
            contact.accept(reverse(id,1,0,O),true);
            check(contact.accept(frame(id,2,CONTACT,0,O,z(3.7),V,status),true),"native contact status admitted");
            var contactMesh=TrajectoryReturnMesh.shape(first(contact),1);
            check(roles(contactMesh,TrajectoryReturnMesh.Role.DAMAGE)==(status==DAMAGED?4:0),"only confirmed damage yields damage marks");
            check(roles(contactMesh,TrajectoryReturnMesh.Role.ABSORBED)==(status==ABSORBED?2:0),"confirmed absorption is distinct from damage");
            check(roles(contactMesh,TrajectoryReturnMesh.Role.TRAIL)==0&&roles(contactMesh,TrajectoryReturnMesh.Role.REVERSED)==0,"contact clears flight and release decoration");
            check(!contact.accept(frame(id,3,FLIGHT,0,z(3.7),z(4),V,COMMITTED),true),"contact is terminal for its projectile slot");
        }
        for(int status=REFUSED;status<=ABSORBED;status++)for(double distance:new double[]{.08,.3,2,12,40}) {
            var hammer=new TrajectoryReturnState();Identity id=id(20+status,1);
            check(hammer.accept(frame(id,1,AIR_HAMMER,0,O,z(3+distance),Z,status),true),"paid air hammer carries actual clipped path and native outcome");
            for(double age=0;age<8;age+=.25) {
                var h=TrajectoryReturnMesh.shape(first(hammer),age);
                check((roles(h,TrajectoryReturnMesh.Role.DAMAGE)>0)==(status==DAMAGED),"hammer release alone never implies damage");
                for(var stroke:h.strokes())if(stroke.role()==TrajectoryReturnMesh.Role.HAMMER)
                    check(stroke.from().z()>=3&&stroke.to().z()<=3+distance+1e-8,"short pressure column stays inside actual clipped axial extent");
                check(roles(h,TrajectoryReturnMesh.Role.TRAIL)==0,"air hammer is never a projectile trail");
            }
        }
        var multiple=new TrajectoryReturnState();Identity second=id(1,2);
        check(multiple.accept(reverse(a,1,0,O),true)&&multiple.accept(reverse(second,1,0,z(4)),true),"same owner can own concurrent authenticated casts");
        check(multiple.accept(frame(a,2,FLIGHT,0,O,z(4),V,COMMITTED),true),"new tap does not reject older still-live flight");
        check(multiple.accept(end(a,3),true)&&multiple.controllers().size()==1&&multiple.owns(second),"ending one same-owner cast preserves the other");
        multiple.clear();check(!multiple.accept(frame(second,2,FLIGHT,0,z(4),z(5),V,COMMITTED),true),"reload fences late receipts");
        var newerEpoch=new Identity(1,a.owner(),72,1,101);
        check(multiple.accept(reverse(newerEpoch,1,0,O),true),"new server epoch accepted");
        check(!multiple.accept(reverse(id(1,3),1,0,O),true),"old owner epoch cannot reopen after replacement");
        var cancelled=new TrajectoryReturnState();cancelled.cancel(71,5);
        check(!cancelled.accept(reverse(id(1,5),1,0,O),true)&&cancelled.accept(reverse(id(1,6),1,0,O),true),"late local cancellation fence admits only fresh input");
        var pool=new TrajectoryReturnState();
        for(int i=0;i<4;i++)check(pool.accept(reverse(id(30+i,1),1,0,O),false),"bounded remote admitted");
        check(!pool.accept(reverse(id(40,1),1,0,O),false)&&pool.controllers().size()==4,"fifth remote cannot overrun controller cap");
        check(pool.accept(reverse(id(41,1),1,0,O),true)&&pool.controllers().size()==4,"local cast can replace one remote");
        for(int i=0;i<80;i++)pool.tick(true,c->true);check(first(pool).age()==0,"pause freezes receipt chronology and expiry");
        for(int i=0;i<LIFE_TICKS;i++)pool.tick(false,c->true);check(pool.controllers().isEmpty(),"hard lifetime cannot leave stale geometry");
        check(!pool.accept(frame(id(41,1),2,FLIGHT,0,O,z(4),V,COMMITTED),true),"expiry cannot be revived");
        pool.resetSession();check(pool.accept(reverse(id(41,1),1,0,O),true),"new connection resets replay fences");
        finiteAndBudget();compiledAdapterChecks();
        System.out.println("TrajectoryReturnVisualRegressionTest: "+checks+" checks passed");
    }
    private static void finiteAndBudget(){
        Identity a=id(100,1);
        check(!valid(frame(a,1,REVERSE,0,O,O,new Point(Double.NaN,0,0),COMMITTED)),"nonfinite velocity rejected");
        check(!valid(frame(a,1,REVERSE,0,O,O,new Point(0,0,8.001),COMMITTED)),"full native speed cap enforced");
        check(!valid(frame(a,1,REVERSE,0,O,O,V,DAMAGED)),"reversal receipt cannot masquerade as hit");
        check(!valid(frame(a,1,AIR_HAMMER,0,O,z(43.01),Z,DAMAGED)),"hammer range finite and bounded");
        check(!valid(frame(a,1,AIR_HAMMER,0,O,z(10),Z,COMMITTED)),"hammer must be post-native final outcome");
        check(!valid(frame(a,1,CONTACT,0,O,z(11.01),V,DAMAGED)),"arrow contact bounded by native flight distance");
        check(!valid(frame(a,129,FLIGHT,0,O,z(4),V,COMMITTED)),"wire sequence cap enforced");
        var slots=new ArrayList<Slot>();
        for(int i=0;i<4;i++) {
            Point start=new Point(i*.3,1.6,3);var trail=new ArrayList<Segment>();
            for(int j=0;j<4;j++)trail.add(new Segment(new Point(i*.3,1.6,3+j*.2),new Point(i*.3,1.6,3+(j+1)*.2),0));
            slots.add(new Slot(i,false,start,trail.getLast().to(),V,start,V,0,0,FLIGHT,COMMITTED,trail));
        }
        Controller full=new Controller(a,true,slots,0);
        for(double age=0;age<32;age+=.125) {
            var mesh=TrajectoryReturnMesh.shape(full,age);var bounds=TrajectoryReturnMesh.bounds(full);
            check(mesh.vertexCount()<=512&&mesh.vertexCount()*4<=2048,"four full controllers obey whole-field two-pass vertex budget");
            for(var stroke:mesh.strokes()) {
                check(stroke.from().finite()&&stroke.to().finite()&&bounds.contains(stroke.from())&&bounds.contains(stroke.to()),"complete decorative bounds include finite geometry");
                var core=TrajectoryReturnMesh.profile(stroke,true);var edge=TrajectoryReturnMesh.profile(stroke,false);
                check(edge.width()>core.width()*2&&edge.gray()<core.gray()&&edge.alpha()<core.alpha(),"broad soft gray shoulder surrounds white-gray core");
                if(stroke.role()==TrajectoryReturnMesh.Role.REVERSED)check(core.width()>=.058,"reversal head and shaft never become one hairline");
            }
        }
        check(TrajectoryReturnMesh.shape(full,0).vertexCount()==512,"worst-case actual shape exercises full per-controller budget");
        check(TrajectoryReturnMesh.budgeted(full,0,511).strokes().stream().allMatch(s->s.role()==TrajectoryReturnMesh.Role.FALLBACK),"whole decoration falls back instead of dropping random strips");
        check(TrajectoryReturnMesh.budgeted(full,0,96).vertexCount()==96,"complete minimal arrows fit exact remaining budget");
        check(TrajectoryReturnMesh.budgeted(full,0,95).strokes().isEmpty(),"no partial fallback admitted below its complete budget");
        int budget=2048;for(int i=0;i<4;i++)budget-=TrajectoryReturnMesh.budgeted(full,0,budget).vertexCount();check(budget==0,"budget is counted across the entire field");
        check(TrajectoryReturnMesh.shape(full,Double.NaN).strokes().isEmpty()&&TrajectoryReturnMesh.shape(full,32).strokes().isEmpty(),"invalid time and expired geometry are empty");
        Point camera=new Point(30_000_000,80,-30_000_000),point=new Point(30_000_000.125,80.5,-29_999_999.75);
        check(TrajectoryReturnMesh.relative(point,camera).equals(new Point(.125,.5,.25)),"double subtraction retains sub-block world-border precision");
        var raw=new TrajectoryReturnMesh.Stroke(new Point(0,0,-1),new Point(0,0,2),Z,.16,255,TrajectoryReturnMesh.Role.PRESSURE);
        var clipped=TrajectoryReturnMesh.clipNear(raw,new Point(0,0,1),.08);
        check(clipped!=null&&clipped.from().z()>=.1599999,"near plane reserves entire shoulder half-width");
        check(TrajectoryReturnMesh.nearEyeFade(.2)==0&&TrajectoryReturnMesh.nearEyeFade(1)==1,"near-eye fade suppresses full bright strips at eye");
        Point localCamera=new Point(.2,1.6,0),forward=new Point(0,0,1),fallback=new Point(1,0,0);
        for(var stroke:TrajectoryReturnMesh.shape(full,0).strokes())for(boolean core:new boolean[]{false,true}) {
            var profile=TrajectoryReturnMesh.profile(stroke,core);
            var ribbon=TrajectoryReturnMesh.ribbon(stroke,profile,localCamera,forward,fallback);
            check(ribbon!=null&&ribbon.corners().size()==4,"production two-pass billboard emits four finite corners");
            var c=ribbon.corners();
            check(Math.abs(Math.sqrt(c.get(0).distanceSquared(c.get(1)))-profile.width())<1e-9,"production quad keeps full declared shoulder/core width");
            for(Point q:c)check(q.finite()&&q.z()>=TrajectoryReturnMesh.NEAR_PLANE,"every emitted corner remains beyond camera near plane");
            Point offset=new Point(29_999_900,20,-29_999_900);
            var farStroke=new TrajectoryReturnMesh.Stroke(plus(stroke.from(),offset),plus(stroke.to(),offset),plus(stroke.anchor(),offset),stroke.width(),stroke.alpha(),stroke.role());
            var far=TrajectoryReturnMesh.ribbon(farStroke,profile,plus(localCamera,offset),forward,fallback);
            check(far!=null,"world-border billboard remains admitted");
            for(int i=0;i<4;i++)check(c.get(i).distanceSquared(far.corners().get(i))<1e-14,"actual emitted billboard is translation invariant before float conversion");
        }
        var nearRibbon=TrajectoryReturnMesh.ribbon(raw,TrajectoryReturnMesh.profile(raw,false),Z,forward,fallback);
        check(nearRibbon!=null&&nearRibbon.corners().stream().allMatch(q->q.z()>=TrajectoryReturnMesh.NEAR_PLANE),"entire near-eye soft shoulder clips safely");
        check(TrajectoryReturnMesh.ribbon(raw,TrajectoryReturnMesh.profile(raw,true),Z,forward,Z)==null,"degenerate camera fallback fails closed");
        check(TrajectoryReturnMesh.MAX_PARTICLES==0,"zero particle allocation");
    }
    private static Point plus(Point a,Point b){return new Point(a.x()+b.x(),a.y()+b.y(),a.z()+b.z());}
    private static ClassNode read(String name)throws Exception{try(var in=TrajectoryReturnVisualRegressionTest.class.getClassLoader().getResourceAsStream("cn/academy/port/client/"+name+".class")){var n=new ClassNode();new ClassReader(in).accept(n,0);return n;}}
    private static void compiledAdapterChecks()throws Exception{
        ClassNode effects=read("TrajectoryReturnEffects"),material=read("TrajectoryReturnEffects$Material"),geometry=read("TrajectoryReturnMesh");
        boolean uuid=false,identity=false,terrain=false,bounds=false,relative=false,budget=false,zeroParticles=true;
        for(var method:effects.methods)for(var op:method.instructions)if(op instanceof MethodInsnNode call){
            uuid|=call.name.equals("getUUID");identity|=call.name.equals("trajectoryReturnInputMatches")&&call.desc.equals("(JJ)Z");
            terrain|=call.name.equals("clip")&&call.owner.contains("ClientLevel");bounds|=call.name.equals("bounds")&&call.owner.endsWith("TrajectoryReturnMesh");
            relative|=call.name.equals("relative")&&call.owner.endsWith("TrajectoryReturnMesh");budget|=call.name.equals("budgeted")&&call.owner.endsWith("TrajectoryReturnMesh");
            zeroParticles&=!call.name.contains("addParticle");
        }
        for(var method:geometry.methods)for(var op:method.instructions)if(op instanceof MethodInsnNode call)
            relative|=call.name.equals("relative")&&call.owner.endsWith("TrajectoryReturnMesh");
        check(uuid&&identity&&terrain&&bounds&&relative&&budget&&zeroParticles,"compiled renderer authenticates exact receipt and uses finite terrain-tested budgeted geometry");
        boolean depth=false,color=false;
        for(var method:material.methods)for(var op:method.instructions)if(op instanceof FieldInsnNode f){depth|=f.name.equals("LEQUAL_DEPTH_TEST");color|=f.name.equals("COLOR_WRITE");}
        check(depth&&color,"actual material depth-tests without depth writes");
        var client=read("AcademyClient");
        for(String hook:List.of("cancelPersistentHighPowerInputs","synchronizeSession","clearEffects","tick")){
            boolean called=false;for(var method:client.methods)if(method.name.equals(hook))for(var op:method.instructions)if(op instanceof MethodInsnNode m)
                called|=m.name.equals(hook.equals("clearEffects")?"clear":"trajectoryReturnCancelInput")&&(m.owner.endsWith("AcademyClient")||m.owner.endsWith("TrajectoryReturnEffects"));
            check(called,"production lifecycle owns cancellation: "+hook);
        }
    }
}

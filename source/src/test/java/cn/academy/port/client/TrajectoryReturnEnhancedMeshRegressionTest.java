package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static cn.academy.port.client.TrajectoryReturnMesh.*;
import static cn.academy.port.client.TrajectoryReturnState.*;

/** Pure receipt/geometry contract, not a native screenshot or a claim of visual acceptance. */
public final class TrajectoryReturnEnhancedMeshRegressionTest {
    private static final Point ORIGIN=new Point(0,1.6,6),FORWARD=new Point(0,0,1);
    private static final Identity ID=new Identity(431,new UUID(67,431),71,1,1);
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static Controller controller(List<Slot> slots){return new Controller(ID,true,slots,0);}
    private static Slot arrow(int number,Point p,Point d,int kind,int status,int receivedAt) {
        return new Slot(number,false,p,p,d,p,d,0,receivedAt,kind,status,List.of());
    }
    private static Controller hammer(Point origin,Point end,int status) {
        return controller(List.of(new Slot(0,true,origin,end,ZERO,end,ZERO,0,0,AIR_HAMMER,status,List.of())));
    }
    private static Shape shape(Controller c,double age){return TrajectoryReturnEnhancedMesh.shape(c,age,Map.of());}
    private static long count(Shape s,Role role){return s.strokes().stream().filter(t->t.role()==role).count();}
    public static void main(String[] args) {
        reversalAndContact();independentHistories();completeBudgetAndBounds();hammerIntervals();cameraLocalMaterial();largeCoordinates();invalidInputs();
        System.out.println("TrajectoryReturnEnhancedMeshRegressionTest: "+checks+" checks passed");
    }

    private static void reversalAndContact() {
        var c=controller(List.of(arrow(0,ORIGIN,FORWARD,REVERSE,COMMITTED,0)));
        var initial=shape(c,0);
        check(count(initial,Role.INCOMING)==3&&count(initial,Role.REVERSED)==3,"two complete direction arrows");
        check(count(initial,Role.PRESSURE)==6&&count(initial,Role.TRAIL)==0,"local open pressure without predicted flight");
        Stroke incoming=initial.strokes().stream().filter(s->s.role()==Role.INCOMING).findFirst().orElseThrow();
        Stroke outgoing=initial.strokes().stream().filter(s->s.role()==Role.REVERSED).findFirst().orElseThrow();
        check(dot(minus(incoming.to(),incoming.from()),FORWARD)<0&&dot(minus(outgoing.to(),outgoing.from()),FORWARD)>0,
                "incoming and outgoing symbols retain opposed directions");
        check(incoming.anchor().equals(ORIGIN)&&outgoing.anchor().equals(ORIGIN),"both arrows remain anchored to the received reversal point");
        check(incoming.to().distanceSquared(outgoing.from())>.1,"before and after arrows have distinct bases");
        var degrees=new HashMap<Point,Integer>();
        for(var stroke:initial.strokes())if(stroke.role()==Role.PRESSURE) {
            degrees.merge(stroke.from(),1,Integer::sum);degrees.merge(stroke.to(),1,Integer::sum);
            check(stroke.anchor().equals(ORIGIN),"pressure remains local to the actual reversal");
        }
        check(degrees.values().stream().filter(n->n==1).count()==4&&degrees.values().stream().allMatch(n->n<=2),
                "two separate open chains, with four exposed ends and no closed ring or filled sector");
        double enhancedArea=projectedPressureArea(initial,FORWARD),oldArea=projectedPressureArea(TrajectoryReturnMesh.shape(c,0),FORWARD);
        check(enhancedArea>oldArea*2.5,"head-on pressure has materially greater geometric area, independent of brightness");
        check(shape(c,13).strokes().size()==12&&shape(c,14).strokes().isEmpty(),"reversal lasts below 14 ticks, with no frozen prediction afterward");
        for(int status=REFUSED;status<=ABSORBED;status++) {
            var terminal=controller(List.of(arrow(0,ORIGIN,FORWARD,CONTACT,status,3)));
            for(double age=3;age<15;age+=.25) {
                var s=shape(terminal,age);
                check(count(s,Role.INCOMING)==0&&count(s,Role.REVERSED)==0&&count(s,Role.PRESSURE)==0,
                        "CONTACT immediately replaces all reversal decoration");
                check((count(s,Role.DAMAGE)>0)==(status==DAMAGED),"only confirmed damage displays damage rays");
                check((count(s,Role.ABSORBED)>0)==(status==ABSORBED),"only confirmed absorption displays open absorption brackets");
                if(status==REFUSED)check(s.strokes().isEmpty(),"REFUSED never receives success decoration");
            }
            check(shape(terminal,15).strokes().isEmpty(),"contact cue ends 12 ticks after its own receipt");
        }
        var state=new TrajectoryReturnState();
        check(state.accept(new Frame(ID,1,REVERSE,0,ORIGIN,ORIGIN,FORWARD,COMMITTED),true),"actual reversal receipt accepted");
        check(!shape(state.controllers().getFirst(),0).strokes().isEmpty(),"accepted state supplies the pure presentation");
        check(state.accept(new Frame(ID,2,ENDED,0,ZERO,ZERO,ZERO,REFUSED),true)&&state.controllers().isEmpty(),
                "authoritative END removes the controller before any local decoration lifetime");
    }

    private static void independentHistories() {
        var c=controller(List.of(arrow(0,ORIGIN,FORWARD,FLIGHT,COMMITTED,4)));
        var segments=List.of(new Segment(new Point(-2,1.6,5),new Point(-1.5,1.6,5),1),
                new Segment(new Point(2,1.6,6),new Point(2,1.6,6.5),2),
                new Segment(new Point(-1,2.6,7),new Point(-.5,2.6,7),3),
                new Segment(new Point(0,1.6,6),new Point(0,1.6,7),4));
        var histories=Map.of(0,segments);
        for(double age=4;age<13;age+=.25) {
            var actual=TrajectoryReturnEnhancedMesh.shape(c,age,histories).strokes().stream().filter(s->s.role()==Role.TRAIL).toList();
            double now=age;long expected=segments.stream().filter(s->ageSafe(s.tick(),now)).count();
            check(actual.size()==expected,"each independent receipt fades on its own 8-tick lifetime, including gaps");
            for(var stroke:actual)check(segments.stream().anyMatch(s->s.from().equals(stroke.from())&&s.to().equals(stroke.to())
                    &&s.from().equals(stroke.anchor())),"history preserves exact received endpoints and never bridges disjoint segments");
        }
        check(segments.size()==4&&histories.get(0)==segments,"rendering never mutates caller histories");
        var future=new Segment(ORIGIN,plus(ORIGIN,FORWARD),10);
        var negative=new Segment(ORIGIN,plus(ORIGIN,FORWARD),-1);
        var tooLong=new Segment(ORIGIN,plus(ORIGIN,new Point(0,0,8.01)),0);
        var zero=new Segment(ORIGIN,ORIGIN,0);
        check(count(TrajectoryReturnEnhancedMesh.shape(c,4,Map.of(0,List.of(future,negative,tooLong,zero))),Role.TRAIL)==0,
                "future, invalid, oversized and zero-length histories fail closed");
        var five=new ArrayList<>(segments);five.add(new Segment(new Point(0,2,6),new Point(0,2,7),4));
        check(count(TrajectoryReturnEnhancedMesh.shape(c,4,Map.of(0,five)),Role.TRAIL)==4,"defensive history bound cannot exceed four actual segments");
        var contact=controller(List.of(arrow(0,ORIGIN,FORWARD,CONTACT,REFUSED,4)));
        check(TrajectoryReturnEnhancedMesh.shape(contact,4,histories).strokes().isEmpty(),"terminal REFUSED suppresses stale history and all success marks");
    }
    private static boolean ageSafe(int tick,double age){return age>=tick&&age-tick<8;}

    private static void completeBudgetAndBounds() {
        for(Point direction:List.of(FORWARD,new Point(1,0,0),new Point(0,1,0),unit(new Point(1,2,3)))) {
            var slots=new ArrayList<Slot>();var histories=new HashMap<Integer,List<Segment>>();
            for(int i=0;i<4;i++) {
                Point p=plus(ORIGIN,new Point(i*.3,0,0));
                var segments=new ArrayList<Segment>();
                for(int j=0;j<4;j++)segments.add(new Segment(plus(p,times(direction,j*.1)),plus(p,times(direction,(j+1)*.1)),0));
                slots.add(new Slot(i,false,p,segments.getLast().to(),direction,p,direction,0,0,FLIGHT,COMMITTED,segments));
                histories.put(i,segments);
            }
            var full=controller(slots);
            check(TrajectoryReturnEnhancedMesh.shape(full,0,histories).vertexCount()==512,"actual four-arrow four-history maximum spends exactly 512 vertices");
            for(double age=0;age<32;age+=.25) {
                var mesh=TrajectoryReturnEnhancedMesh.shape(full,age,histories);Bounds bounds=TrajectoryReturnMesh.bounds(full);
                check(mesh.strokes().size()<=64&&mesh.vertexCount()==mesh.strokes().size()*8&&mesh.vertexCount()*4<=2048,
                        "reported vertices count both real four-corner material passes across four controllers");
                for(var stroke:mesh.strokes()) {
                    check(stroke.from().finite()&&stroke.to().finite()&&stroke.anchor().finite(),"every temporal phase remains finite");
                    for(boolean core:new boolean[]{false,true}) {
                        var profile=TrajectoryReturnEnhancedMesh.profile(stroke,core);
                        check(profile.alpha()>=0&&profile.alpha()<=255&&profile.width()>0&&profile.minV()>=0&&profile.maxV()<=1,
                                "profile has finite useful UV, width and alpha");
                        double half=profile.width()/2;
                        for(Point point:List.of(stroke.from(),stroke.to()))for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}) {
                            Point offset=axis==0?new Point(sign*half,0,0):axis==1?new Point(0,sign*half,0):new Point(0,0,sign*half);
                            check(bounds.contains(plus(point,offset)),"full core/shoulder width fits the existing 1.15 m decorative bounds");
                        }
                    }
                    var core=TrajectoryReturnEnhancedMesh.profile(stroke,true);var edge=TrajectoryReturnEnhancedMesh.profile(stroke,false);
                    check(edge.width()>3*core.width()&&edge.alpha()<core.alpha()&&edge.gray()<core.gray(),"restrained gray shoulder surrounds a crisp white core");
                }
            }
        }
        var mixed=controller(List.of(arrow(0,ORIGIN,FORWARD,REVERSE,COMMITTED,0),
                arrow(1,plus(ORIGIN,new Point(.3,0,0)),FORWARD,CONTACT,DAMAGED,0),
                arrow(2,plus(ORIGIN,new Point(.6,0,0)),FORWARD,CONTACT,ABSORBED,0),
                arrow(3,plus(ORIGIN,new Point(.9,0,0)),FORWARD,CONTACT,REFUSED,0)));
        var mesh=shape(mixed,0);
        check(count(mesh,Role.REVERSED)==3&&count(mesh,Role.DAMAGE)==4&&count(mesh,Role.ABSORBED)==6&&mesh.vertexCount()==176,
                "four mixed slots retain their own terminal outcome without sharing or dropping glyphs");
    }

    private static void hammerIntervals() {
        for(Point direction:List.of(FORWARD,new Point(1,0,0),unit(new Point(1,2,3))))
            for(double distance:new double[]{0,.00001,.01,.08,.3,.8,2,12,40})for(int status=REFUSED;status<=ABSORBED;status++) {
                var c=hammer(ORIGIN,plus(ORIGIN,times(direction,distance)),status);
                for(double age=0;age<=16;age+=.25) {
                    var mesh=shape(c,age);
                    check(mesh.vertexCount()<=512&&count(mesh,Role.TRAIL)==0&&count(mesh,Role.INCOMING)==0&&count(mesh,Role.REVERSED)==0,
                            "hammer has a bounded pressure presentation and never claims an arrow trajectory");
                    check(count(mesh,Role.DAMAGE)==0||status==DAMAGED,"hammer damage remains native-confirmed");
                    check(count(mesh,Role.ABSORBED)==0||status==ABSORBED,"hammer absorption remains native-confirmed");
                    if(distance==0||age==16)check(mesh.strokes().isEmpty(),"zero-length ray and expired hammer have no invented origin or decoration");
                    for(var stroke:mesh.strokes())for(Point p:List.of(stroke.from(),stroke.to())) {
                        double axial=dot(minus(p,ORIGIN),direction),half=TrajectoryReturnEnhancedMesh.profile(stroke,false).width()/2;
                        check(p.finite()&&axial-half>=-1e-8&&axial+half<=distance+1e-8,
                                "all hammer ribbon widths remain inside the exact received axial interval, even at short range");
                        check(TrajectoryReturnMesh.bounds(c).contains(p),"hammer pressure stays in the existing decorative bounds");
                    }
                }
                if(distance>=.01) {
                    var atZero=shape(c,0).strokes().stream().filter(s->s.role()==Role.HAMMER).toList();
                    check(atZero.size()>=20,"hammer starts with substantial open transverse compression");
                    for(int i=0;i<6;i++)check(dot(minus(atZero.get(i).from(),ORIGIN),direction)<=.8,
                            "near-palm compression starts in the first 0.8 m of the actual ray");
                    double early=maxHammerAxis(shape(c,0),ORIGIN,direction),late=maxHammerAxis(shape(c,9),ORIGIN,direction);
                    check(late>early,"pressure shoulder moves forward inside the received interval");
                }
            }
    }

    private static void cameraLocalMaterial() {
        Point camera=new Point(0,1.6,0);
        Stroke reference=new Stroke(new Point(-.3,1.6,1),new Point(.3,1.6,1),camera,.087,237,Role.HAMMER);
        for(boolean core:new boolean[]{false,true}) {
            var material=TrajectoryReturnEnhancedMesh.profile(reference,core);
            check(Math.abs(material.width()-reference.width()*(core?.80:3.2))<1e-12,
                    "only the enhanced core narrows by 20 percent; the gray shoulder keeps its extent");
            int previous=-1;
            for(int step=0;step<=220;step++) {
                double distance=step*.01;
                Stroke segment=new Stroke(new Point(-.3,1.6,distance),new Point(.3,1.6,distance),camera,
                        reference.width(),reference.alpha(),Role.HAMMER);
                var local=TrajectoryReturnEnhancedMesh.profile(segment,core,camera);
                check(local.alpha()>=previous&&local.alpha()<=material.alpha(),"camera approach fades monotonically within the material alpha bound");
                if(previous>=0)check(local.alpha()-previous<=3,"one-centimeter camera movement cannot switch the whole segment on or off");
                check(local.width()==material.width()&&local.gray()==material.gray()&&local.minV()==material.minV()&&local.maxV()==material.maxV(),
                        "camera motion changes only alpha, preserving material width, gray and UVs");
                if(distance<=.35)check(local.alpha()==0,"camera-near segment is fully attenuated");
                if(distance>=1.65)check(local.equals(material),"distant shoulder and core retain exact enhanced material values");
                previous=local.alpha();
            }
            check(TrajectoryReturnEnhancedMesh.profile(reference,core,camera).alpha()==Math.round(material.alpha()*.5),
                    "transition midpoint has half opacity rather than a binary visibility decision");
        }
        Stroke crossing=new Stroke(new Point(-3,1.6,.5),new Point(3,1.6,.5),camera,.087,237,Role.HAMMER);
        check(crossing.from().distanceSquared(camera)>1.65*1.65&&crossing.to().distanceSquared(camera)>1.65*1.65,
                "crossing-segment control has distant endpoints");
        check(TrajectoryReturnEnhancedMesh.cameraFade(crossing,camera)<.05,
                "closest-segment distance catches a near-eye chord that endpoint-only fading misses");
        Stroke remote=new Stroke(new Point(-.3,1.6,6),new Point(.3,1.6,6),camera,.087,237,Role.HAMMER);
        check(TrajectoryReturnEnhancedMesh.cameraFade(remote,camera)==1,"near receipt anchor cannot hide a distant segment");
        Stroke reversed=new Stroke(reference.to(),reference.from(),new Point(4,6,8),reference.width(),reference.alpha(),Role.HAMMER);
        check(TrajectoryReturnEnhancedMesh.cameraFade(reference,camera)==TrajectoryReturnEnhancedMesh.cameraFade(reversed,camera),
                "fade is independent of segment order and receipt anchor");
        var hammer=hammer(camera,new Point(0,1.6,12),REFUSED);
        var shape=shape(hammer,6);
        int palmCount=0,farCount=0;
        for(var stroke:shape.strokes()) {
            var core=TrajectoryReturnEnhancedMesh.profile(stroke,true,camera);
            if(Math.abs(stroke.from().z()-.58)<1e-9&&Math.abs(stroke.to().z()-.58)<1e-9) {
                palmCount++;
                check(core.alpha()>0&&core.alpha()<stroke.alpha()*.25,"actual fixed palm arcs become restrained while staying smoothly represented");
            }
            if(stroke.from().z()>1.65&&stroke.to().z()>1.65) {
                farCount++;
                check(TrajectoryReturnEnhancedMesh.profile(stroke,false,camera).equals(TrajectoryReturnEnhancedMesh.profile(stroke,false)),
                        "actual forward-traveling gray shoulders retain their full distant material");
            }
        }
        check(palmCount==6&&farCount>0,"native hammer geometry exercises both fixed palm and distant head material");
        check(shape.equals(shape(hammer,6)),"camera material evaluation cannot alter accepted geometry or endpoints");
        check(TrajectoryReturnEnhancedMesh.cameraFade(reference,null)==0
                &&TrajectoryReturnEnhancedMesh.cameraFade(reference,new Point(Double.NaN,0,0))==0,"invalid camera fades closed");
        Point offset=new Point(29_999_900,30,-29_999_900);
        for(double distance:new double[]{.34,.35,.36,.58,1,1.64,1.65,1.66,6}) {
            Stroke local=new Stroke(new Point(-.3,1.6,distance),new Point(.3,1.6,distance),camera,.087,237,Role.HAMMER);
            Stroke translated=new Stroke(plus(local.from(),offset),plus(local.to(),offset),plus(camera,offset),.087,237,Role.HAMMER);
            check(Math.abs(TrajectoryReturnEnhancedMesh.cameraFade(local,camera)-TrajectoryReturnEnhancedMesh.cameraFade(translated,plus(camera,offset)))<1e-8,
                    "camera-local transition remains stable at world-border coordinates");
            for(boolean core:new boolean[]{false,true})check(TrajectoryReturnEnhancedMesh.profile(local,core,camera)
                    .equals(TrajectoryReturnEnhancedMesh.profile(translated,core,plus(camera,offset))),
                    "large-coordinate translation preserves actual rounded material values near and far");
        }
    }

    private static void largeCoordinates() {
        Point offset=new Point(29_999_900,30,-29_999_900),camera=new Point(0,1.6,0);
        var near=shape(controller(List.of(arrow(0,ORIGIN,FORWARD,REVERSE,COMMITTED,0))),2);
        var far=shape(controller(List.of(arrow(0,plus(ORIGIN,offset),FORWARD,REVERSE,COMMITTED,0))),2);
        check(near.strokes().size()==far.strokes().size(),"world-border translation preserves complete geometry");
        for(int i=0;i<near.strokes().size();i++)for(boolean core:new boolean[]{false,true}) {
            var a=near.strokes().get(i);var b=far.strokes().get(i);
            var local=TrajectoryReturnMesh.ribbon(a,TrajectoryReturnEnhancedMesh.profile(a,core),camera,FORWARD,new Point(1,0,0));
            var remote=TrajectoryReturnMesh.ribbon(b,TrajectoryReturnEnhancedMesh.profile(b,core),plus(camera,offset),FORWARD,new Point(1,0,0));
            check(local!=null&&remote!=null,"actual renderer ribbon path admits both translations");
            for(int j=0;j<4;j++)check(local.corners().get(j).distanceSquared(remote.corners().get(j))<1e-13,
                    "double camera subtraction preserves all actual ribbon corners at large coordinates");
        }
    }

    private static void invalidInputs() {
        var c=controller(List.of(arrow(0,ORIGIN,FORWARD,REVERSE,COMMITTED,0)));
        for(double age:new double[]{-1,32,Double.NaN,Double.POSITIVE_INFINITY})check(shape(c,age).strokes().isEmpty(),"invalid or expired controller time fails closed");
        check(TrajectoryReturnEnhancedMesh.shape(null,0,Map.of()).strokes().isEmpty(),"null controller is empty");
        check(shape(controller(List.of(arrow(0,ORIGIN,ZERO,REVERSE,COMMITTED,0))),0).strokes().isEmpty(),"zero reversal vector never invents direction");
        check(shape(controller(List.of(arrow(0,ORIGIN,new Point(Double.NaN,0,0),REVERSE,COMMITTED,0))),0).strokes().isEmpty(),"nonfinite direction is rejected");
        check(shape(controller(List.of(arrow(0,ORIGIN,FORWARD,REVERSE,REFUSED,0))),0).strokes().isEmpty(),"malformed refusal cannot masquerade as a committed reversal");
        check(shape(hammer(ORIGIN,plus(ORIGIN,new Point(0,0,40.01)),DAMAGED),0).strokes().isEmpty(),"oversized hammer fails closed for both release and contact");
        check(TrajectoryReturnMesh.MAX_PARTICLES==0,"enhanced mesh retains zero particles");
    }
    private static double projectedPressureArea(Shape shape,Point d) {
        double area=0;
        for(var s:shape.strokes())if(s.role()==Role.PRESSURE) {
            Point delta=minus(s.to(),s.from());Point transverse=minus(delta,times(d,dot(delta,d)));
            area+=Math.sqrt(dot(transverse,transverse))*s.width();
        }
        return area;
    }
    private static double maxHammerAxis(Shape shape,Point origin,Point d) {
        return shape.strokes().stream().filter(s->s.role()==Role.HAMMER)
                .mapToDouble(s->Math.max(dot(minus(s.from(),origin),d),dot(minus(s.to(),origin),d))).max().orElse(0);
    }
    private static Point plus(Point a,Point b){return new Point(a.x()+b.x(),a.y()+b.y(),a.z()+b.z());}
    private static Point minus(Point a,Point b){return new Point(a.x()-b.x(),a.y()-b.y(),a.z()-b.z());}
    private static Point times(Point p,double n){return new Point(p.x()*n,p.y()*n,p.z()*n);}
    private static double dot(Point a,Point b){return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
    private static Point unit(Point p){return times(p,1/Math.sqrt(dot(p,p)));}
}

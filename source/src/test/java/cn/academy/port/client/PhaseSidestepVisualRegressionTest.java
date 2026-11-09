package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.List;
import java.util.UUID;

/** Genuine endpoint geometry, terminal receipts, replay rejection and bounded original artwork. */
public final class PhaseSidestepVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER = new UUID(312, 1);
    private static final Point SOURCE = new Point(0,64,0), DEST = new Point(3,64,0);
    private static final Box BOX = new Box(2.7,64,-.3,3.3,65.8,.3);
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static PhaseSidestepState.Frame frame(long input, long token, int phase, boolean moved, Point destination) {
        return new PhaseSidestepState.Frame(new PhaseSidestepState.Identity(19,OWNER,71,input,token),1,phase,SOURCE,destination,BOX,moved,"",false);
    }
    private static boolean contains(Box b, Point p) { return p.x() >= b.minX() - 1e-7 && p.x() <= b.maxX() + 1e-7 && p.y() >= b.minY() - 1e-7 && p.y() <= b.maxY() + 1e-7 && p.z() >= b.minZ() - 1e-7 && p.z() <= b.maxZ() + 1e-7; }
    public static void main(String[] args) {
        var state = new PhaseSidestepState(); var success = frame(1,1,1,true,DEST);
        check(PhaseSidestepState.valid(success) && state.accept(success,true), "finite actual move admitted");
        check(!state.accept(success,true), "duplicate terminal cannot reanimate");
        check(!state.accept(frame(1,2,1,true,DEST),true), "same physical input cannot reopen with a new token");
        check(!state.accept(frame(2,1,1,true,DEST),true), "same token cannot reopen with a new input");
        check(state.accept(frame(2,2,3,false,null),true), "localized refused receipt is terminal with no destination");
        check(!state.accept(frame(3,3,3,false,null),false), "remote refusal cannot paint anything");
        check(!PhaseSidestepState.valid(frame(3,3,1,false,DEST)), "moved=false never animates success");
        check(!PhaseSidestepState.valid(frame(3,3,3,true,DEST)), "refusal cannot claim moved");
        check(!PhaseSidestepState.valid(frame(3,3,0,true,DEST)), "no preview phase");
        check(!PhaseSidestepState.valid(frame(3,3,1,true,new Point(2.99,64,0))), "shortened move blocked");
        check(!PhaseSidestepState.valid(frame(3,3,1,true,SOURCE)), "zero-distance is never success");
        check(!PhaseSidestepState.valid(frame(3,3,1,true,new Point(Double.NaN,64,0))), "NaN blocked");
        check(!PhaseSidestepState.valid(frame(3,3,1,true,new Point(4,64,0))), "excess range blocked");
        check(!PhaseSidestepState.valid(frame(3,3,1,true,new Point(3,64.1,0))), "invented vertical move blocked");
        state.cancel(71,3); check(!state.accept(frame(3,3,1,true,DEST),true), "local cancelled request remains fenced");
        check(state.accept(frame(4,4,1,true,DEST),true), "fresh uncancelled request admitted");
        state.resetSession(); check(state.accept(success,true), "new connection resets provenances");
        var full = new PhaseSidestepState();
        for (int i=1;i<=PhaseSidestepState.MAX_WATERMARKS;i++) {
            var f=new PhaseSidestepState.Frame(new PhaseSidestepState.Identity(i,new UUID(312,i),71,1,1),1,1,SOURCE,DEST,BOX,true,"",false);
            check(full.accept(f,false), "bounded owner accepted");
        }
        var extra=new PhaseSidestepState.Frame(new PhaseSidestepState.Identity(2000,new UUID(312,2000),71,1,1),1,1,SOURCE,DEST,BOX,true,"",false);
        check(!full.accept(extra,false) && full.watermarkCount()==1024, "full replay map never evicts old fences");
        Box departure=PhaseSidestepMesh.sourceBounds(BOX,SOURCE,DEST);
        check(Math.abs(departure.minX()+.3)<1e-8, "departure uses confirmed actual source");
        for (Box b : List.of(departure,BOX)) {
            check(PhaseSidestepMesh.corners(b).size()==24, "twenty-four bounded endpoint strokes");
            for(var line:PhaseSidestepMesh.corners(b))check(contains(b,line.from())&&contains(b,line.to()), "outline matches actual body");
            check(PhaseSidestepMesh.biped(b).size()==36,"six original atlas body parts");
            for(var q:PhaseSidestepMesh.biped(b))for(var v:List.of(q.a(),q.b(),q.c(),q.d()))
                check(contains(b,v.position())&&v.u()>=0&&v.u()<=1&&v.v()>=0&&v.v()<=1,"finite body mesh with native atlas UVs");
        }
        for(int age=0;age<7;age++)check(PhaseSidestepMesh.frame(age)==age&&PhaseSidestepMesh.alpha(age)>0
                &&PhaseSidestepMesh.particles(SOURCE,DEST,age).size()<=32,"seven original frames and thirty-two cosmetic sparks maximum");
        check(PhaseSidestepMesh.alpha(7)==0&&PhaseSidestepMesh.particles(SOURCE,DEST,7).isEmpty(),"bounded decay with no lingering portal");
        System.out.println("PhaseSidestepVisualRegressionTest: "+checks+" checks passed");
    }
}

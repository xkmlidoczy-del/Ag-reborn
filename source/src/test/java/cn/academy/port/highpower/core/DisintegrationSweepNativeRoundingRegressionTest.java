package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Bounds;
import java.util.*;
import static cn.academy.port.highpower.core.DisintegrationSweepGeometry.*;

/** Actual Minecraft float-look and short-prefix slab arithmetic witness the adapter boundary.
 * Recovery is tested against native clipping, independently of the angle proposal implementation.
 */
public final class DisintegrationSweepNativeRoundingRegressionTest {
  static final float[] SIN = new float[65536];
  static {for(int i=0;i<SIN.length;i++)SIN[i]=(float)Math.sin((double)i*Math.PI*2.0/65536.0);}
  static float sin(float f){return SIN[(int)(f*10430.378F)&65535];}
  static float cos(float f){return SIN[(int)(f*10430.378F+16384.0F)&65535];}
  static Vector look(float pitch,float yaw){float f=pitch*(float)(Math.PI/180.0),f1=-yaw*(float)(Math.PI/180.0),f2=cos(f1),f3=sin(f1),f4=cos(f),f5=sin(f);return new Vector((double)(f3*f4),(double)(-f5),(double)(f2*f4));}
  static Vector lerp(Vector s,Vector e,double t){return new Vector(s.x()+t*(e.x()-s.x()),s.y()+t*(e.y()-s.y()),s.z()+t*(e.z()-s.z()));}
  static double sq(Vector s,Vector e){Vector d=e.subtract(s);return d.x()*d.x()+d.y()*d.y()+d.z()*d.z();}
  static Bounds inflate(Bounds b,double r){return new Bounds(b.min().add(new Vector(-r,-r,-r)),b.max().add(new Vector(r,r,r)));}
  static double[] xyz(Vector v){return new double[]{v.x(),v.y(),v.z()};}
  // Exact copy of private production clipInclusive, using the pure vector type.
  static Optional<Vector> clip(Bounds box,Vector start,Vector end){double near=0,far=1;double[] origins=xyz(start),delta=xyz(end.subtract(start)),min=xyz(box.min()),max=xyz(box.max());
    for(int i=0;i<3;i++){if(delta[i]==0){if(origins[i]<min[i]||origins[i]>max[i])return Optional.empty();continue;}double a=(min[i]-origins[i])/delta[i],b=(max[i]-origins[i])/delta[i];near=Math.max(near,Math.min(a,b));far=Math.min(far,Math.max(a,b));if(near>far)return Optional.empty();}
    return near>=0&&near<=1?Optional.of(lerp(start,end,near)):Optional.empty();}
  static boolean intersects(Bounds a,Bounds b){return a.min().x()<b.max().x()&&a.max().x()>b.min().x()&&a.min().y()<b.max().y()&&a.max().y()>b.min().y()&&a.min().z()<b.max().z()&&a.max().z()>b.min().z();}
  static Bounds outwardSegment(Vector s,Vector e){return new Bounds(new Vector(Math.nextDown(Math.min(s.x(),e.x())-.035),Math.nextDown(Math.min(s.y(),e.y())-.035),Math.nextDown(Math.min(s.z(),e.z())-.035)),new Vector(Math.nextUp(Math.max(s.x(),e.x())+.035),Math.nextUp(Math.max(s.y(),e.y())+.035),Math.nextUp(Math.max(s.z(),e.z())+.035)));}
  // Models target-only nativeTrace including .25 m segmentation, AABB broad query and nearest check.
  static Optional<Vector> trace(Vector origin,Vector endpoint,Bounds body){int pieces=Math.max(1,(int)Math.ceil(Math.sqrt(sq(origin,endpoint))/.25));Vector previous=origin;for(int i=1;i<=pieces;i++){Vector next=lerp(origin,endpoint,(double)i/pieces);if(intersects(outwardSegment(previous,next),body)){var hit=clip(inflate(body,.035),origin,next);if(hit.isPresent()&&sq(origin,hit.get())<=sq(origin,next))return hit;}previous=next;}return Optional.empty();}
  record Case(String name, double x, double y, double z) {}
  record Fixture(Vector hand, Bounds body, DisintegrationSweepGeometry.FrozenPlane plane,
      DisintegrationSweepGeometry.AngleInterval interval, DisintegrationSweepGeometry.Contact tangent, Vector endpoint) {}
  static Fixture fixture(Case c){return fixture(c,new Vector(0,0,8));}
  static Fixture fixture(Case c,Vector offset){
    Vector def=new Vector(c.x+8.5,c.y+18,c.z+8.5), caster=def.add(offset);double half=(double)(.6F/2.0F);
    Bounds body=new Bounds(def.add(new Vector(-half,0,-half)),def.add(new Vector(half,(double)1.8F,half)));
    Vector eye=caster.add(new Vector(0,(double)1.62F,0)),center=lerp(body.min(),body.max(),.5),d=center.subtract(eye);
    float yaw=(float)Math.toDegrees(Math.atan2(-d.x(),d.z())),pitch=(float)-Math.toDegrees(Math.atan2(d.y(),Math.sqrt(d.x()*d.x()+d.z()*d.z())));
    Vector look=look(pitch,yaw);var hp=DualPointRules.origin(new DualPointRules.Point(eye.x(),eye.y(),eye.z()),new DualPointRules.Point(look.x(),look.y(),look.z()),0);
    Vector hand=new Vector(hp.x(),hp.y(),hp.z());var plane=DisintegrationSweepGeometry.freeze(hand,look,Math.toRadians(yaw),false).orElseThrow();double halfAngle=DisintegrationSweepGeometry.HALF_ANGLE;
    var interval=DisintegrationSweepGeometry.contactIntervals(plane,hand,body,-halfAngle,halfAngle).getFirst();
    var tangent=DisintegrationSweepGeometry.firstContact(plane,hand,body,-halfAngle,halfAngle).orElseThrow();
    var end=DisintegrationSweepGeometry.rayEndpoint(plane,hand,tangent.angle()).orElseThrow();
    return new Fixture(hand,body,plane,interval,tangent,end);
  }

    private static int assertions;
    public static void main(String[] args) {
        List<Case> positions = new ArrayList<>();
        positions.add(new Case("reported failure", 13632664, -60, 262601));
        positions.add(new Case("origin", 0, 0, 0));
        positions.add(new Case("neighbor minus", 13632663, -60, 262600));
        positions.add(new Case("neighbor plus", 13632665, -60, 262602));
        for (double x : new double[]{-29999000, 29999000}) for (double z : new double[]{-29999000, 29999000})
            positions.add(new Case("border", x, -60, z));
        var random = new Random(231252);
        for (int i = 0; i < 256; i++) positions.add(new Case("translation", random.nextInt(59998001) - 29999000, -60, random.nextInt(59998001) - 29999000));
        for (Case position : positions) checkTranslation(fixture(position));
        var failure = fixture(positions.getFirst());
        check(failure.hand.equals(new Vector(13632672.82, -40.69136611404447, 262617.15140830964)), "retains exact captured native hand");
        check(look(5.1427646F, -180F).equals(new Vector(1.219719238414516E-16, -0.08961748331785202, -0.9959762692451477)), "uses Entity.calculateViewVector with exact captured native look");
        check(trace(failure.hand, failure.endpoint, failure.body).isEmpty(), "retains the exact previously failed native tangent");
        double cursor = failure.tangent.angle();
        for (int i = 0; i < 65; i++) {
            var end = rayEndpoint(failure.plane, failure.hand, cursor).orElseThrow();
            check(end.equals(failure.endpoint), "old angle-ULP retries cannot change this world endpoint");
            check(trace(failure.hand, end, failure.body).isEmpty(), "old retry still has no native hit"); cursor = Math.nextUp(cursor);
        }
        negatives(failure);
        capturedFanEdgeEndpoint();
        System.out.println("Disintegration sweep native rounding: " + assertions + " checks passed");
    }
    private static void capturedFanEdgeEndpoint(){
        Vector hand=new Vector(.18,-58.69594192591415,.8481496483106263);
        FrozenPlane plane=new FrozenPlane(hand,new Vector(0,-.10269123052149,.9947132808875038),new Vector(-1,0,0));
        Bounds body=new Bounds(new Vector(2.199999988079071,-60,6.199999988079071),new Vector(2.800000011920929,-58.049999952316284,6.800000011920929));
        Contact boundary=firstContact(plane,hand,body,-HALF_ANGLE,HALF_ANGLE).orElseThrow();
        check(boundary.angle()==-HALF_ANGLE,"captured live body begins on the exact closed fan edge");
        check(rayEndpoint(plane,hand,boundary.angle()).isEmpty(),"retains the actual failed world-coordinate edge endpoint");
        var interval=contactIntervals(plane,hand,body,-HALF_ANGLE,HALF_ANGLE).getFirst();Optional<Vector> endpoint=Optional.empty();int tries=0;
        while(endpoint.isEmpty()&&tries<4){double candidate=nativeInteriorCandidate(plane,hand,body,boundary,HALF_ANGLE,tries++).orElseThrow();
            check(candidate>boundary.angle()&&candidate<interval.to(),"retry remains strictly inside the same original body/fan interval");
            boundary=firstContact(plane,hand,body,candidate,HALF_ANGLE).orElseThrow();endpoint=rayEndpoint(plane,hand,boundary.angle());}
        check(endpoint.isPresent()&&withinFrozenFootprint(plane,endpoint.orElseThrow()),"bounded retries recover a representable legal endpoint");
        Vector hit=trace(hand,endpoint.orElseThrow(),body).orElseThrow();
        check(withinFrozenFootprint(plane,hit)&&sq(hand,hit)<=MAX_RANGE*MAX_RANGE,"unchanged native short-prefix trace really reaches the actual body within the frozen footprint");
        Bounds wall=new Bounds(new Vector(.5,-60,2),new Vector(2,-58,3));
        check(sq(hand,trace(hand,endpoint.orElseThrow(),wall).orElseThrow())<sq(hand,hit),"the exact recovered ray still meets a nearer solid first");
    }
    private static void checkTranslation(Fixture f) {
        Contact boundary = f.tangent; Optional<Vector> hit = trace(f.hand, f.endpoint, f.body); int tries = 0;
        while (hit.isEmpty() && tries < 4) {
            var candidate = nativeInteriorCandidate(f.plane, f.hand, f.body, boundary, HALF_ANGLE, tries++);
            check(candidate.isPresent(), "finite translated body has a representable interior candidate");
            double a = candidate.getAsDouble();
            check(a > boundary.angle() && a < f.interval.to(), "candidate stays strictly in the same analytic body interval");
            check(contactAt(f.plane, f.hand, f.body, a).isPresent(), "candidate is still an exact thin-core analytic contact");
            Vector end = rayEndpoint(f.plane, f.hand, a).orElseThrow();
            check(end.subtract(f.hand).length() <= MAX_RANGE && withinFrozenFootprint(f.plane, end), "unchanged native endpoint retains both range/plane budgets");
            hit = trace(f.hand, end, f.body);
            boundary = firstContact(f.plane, f.hand, f.body, a, HALF_ANGLE).orElseThrow();
        }
        check(hit.isPresent() && withinFrozenFootprint(f.plane, hit.orElseThrow()), "native short-prefix clipping verifies the translated hit");
        check(tries <= 1, "the captured translation fixture needs at most one additional native trace");
    }
    private static void negatives(Fixture f) {
        check(nativeInteriorCandidate(f.plane, f.hand, f.body, f.tangent, Math.nextDown(f.tangent.angle()), 0).isEmpty(), "never leaves the currently swept interval");
        check(nativeInteriorCandidate(f.plane, f.hand, f.body, f.tangent, Math.nextUp(f.tangent.angle()), 0).isEmpty(), "adjacent-double interval has no fabricated interior");
        check(nativeInteriorCandidate(f.plane, f.hand, f.body, f.tangent, HALF_ANGLE, -1).isEmpty()
                && nativeInteriorCandidate(f.plane, f.hand, f.body, f.tangent, HALF_ANGLE, 4).isEmpty(), "retry budget is closed and bounded");
        Bounds behind = new Bounds(f.hand.add(new Vector(-1,-1,5)), f.hand.add(new Vector(1,1,6)));
        check(nativeInteriorCandidate(f.plane, f.hand, behind, f.tangent, HALF_ANGLE, 0).isEmpty(), "candidate cannot create a contact outside the original cone");
        Vector zero = new Vector(0,0,0); FrozenPlane plane = new FrozenPlane(zero, new Vector(0,0,1), new Vector(1,0,0));
        Bounds tangent = new Bounds(new Vector(0,0,MAX_RANGE+BEAM_RADIUS), new Vector(0,0,MAX_RANGE+BEAM_RADIUS));
        Contact rangeEdge = firstContact(plane, zero, tangent, -HALF_ANGLE, HALF_ANGLE).orElseThrow();
        check(rangeEdge.distance() == MAX_RANGE && rangeEdge.angle() == 0, "closed true range tangent still exists");
        check(nativeInteriorCandidate(plane, zero, tangent, rangeEdge, HALF_ANGLE, 0).isEmpty(), "zero-width true tangent is never widened");
        Bounds outside = new Bounds(new Vector(0,0,MAX_RANGE+BEAM_RADIUS+1e-7), new Vector(0,0,MAX_RANGE+BEAM_RADIUS+1e-7));
        check(firstContact(plane, zero, outside, -HALF_ANGLE, HALF_ANGLE).isEmpty()
                && nativeInteriorCandidate(plane, zero, outside, rangeEdge, HALF_ANGLE, 0).isEmpty(), "outside range remains outside");
        Bounds offPlane = new Bounds(new Vector(0,Math.nextUp(BEAM_RADIUS),10), new Vector(0,Math.nextUp(BEAM_RADIUS),10));
        check(firstContact(plane, zero, offPlane, -HALF_ANGLE, HALF_ANGLE).isEmpty(), "one representable step outside the thin slab stays a miss");
        check(nativeInteriorCandidate(f.plane, f.hand.add(f.plane.normal().scale(.1)), f.body, f.tangent, HALF_ANGLE, 0).isEmpty(), "moving hand never projects back onto the frozen thin core");
        double candidate = nativeInteriorCandidate(f.plane, f.hand, f.body, f.tangent, HALF_ANGLE, 0).orElseThrow();
        Vector end = rayEndpoint(f.plane, f.hand, candidate).orElseThrow();
        Vector middle = lerp(f.hand, lerp(f.body.min(), f.body.max(), .5), .5);
        Bounds wall = new Bounds(middle.add(new Vector(-1,-1,-.5)), middle.add(new Vector(1,1,.5)));
        Vector wallHit = trace(f.hand, end, wall).orElseThrow(), bodyHit = trace(f.hand, end, f.body).orElseThrow();
        check(sq(f.hand, wallHit) < sq(f.hand, bodyHit), "the exact same candidate still encounters the nearer native wall first");
        double maximumCumulative = 120 * Math.ulp(30_000_049.0);
        check(maximumCumulative < .0000005, "all four coordinate-ULP steps total less than half a micrometre at the supported world border");
    }
    private static void check(boolean condition, String why) { assertions++; if (!condition) throw new AssertionError(why); }
}

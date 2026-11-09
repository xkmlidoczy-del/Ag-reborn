package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.math.BigDecimal;
import java.util.UUID;

/** Pure exact identity, queue bounds, economics and swept-body fences for NEW343. */
public final class SpatialRelayRulesRegressionTest {
    private static int checks;
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    private static Box body(Point p) { return new Box(p.x()-.125,p.y(),p.z()-.125,p.x()+.125,p.y()+.25,p.z()+.125); }
    public static void main(String[] args) {
        Point origin = new Point(0,64,0), x32 = new Point(SpatialRelayRules.RANGE,64,0);
        check(SpatialRelayRules.withinRange(origin,x32), "exact configured distance allowed");
        check(!SpatialRelayRules.withinRange(origin,new Point(Math.nextUp(SpatialRelayRules.RANGE),64,0)), "one ULP beyond configured reach denied");
        check(!SpatialRelayRules.withinRange(origin,new Point(SpatialRelayRules.RANGE,64,Double.MIN_VALUE)), "subnormal orthogonal overflow denied");
        check(SpatialRelayRules.withinRange(origin,new Point(0,64+SpatialRelayRules.RANGE,0)), "exact vertical distance allowed");
        check(!SpatialRelayRules.withinRange(origin,new Point(SpatialRelayRules.RANGE*.75,64+SpatialRelayRules.RANGE*.75,0)), "range uses3D distance");
        check(!SpatialRelayRules.positiveLeg(origin,origin), "zero movement is never a payable leg");
        check(SpatialRelayRules.positiveLeg(origin,new Point(Double.MIN_VALUE,64,0)), "positive subnormal translation remains positive");
        check(SpatialRelayRules.withinGather(origin,new Point(2,64,0)), "exact2m gather allowed");
        check(!SpatialRelayRules.withinGather(origin,new Point(Math.nextUp(2d),64,0)), "outside2m gather denied");
        check(!SpatialRelayRules.withinGather(origin,new Point(2,64,Double.MIN_VALUE)), "exact gather boundary cannot round inward");
        check(!SpatialRelayRules.withinRange(null,x32) && !SpatialRelayRules.withinRange(origin,new Point(Double.NaN,64,0)), "invalid inputs refused");
        check(SpatialRelayRules.MAX_ITEMS==16 && SpatialRelayRules.MAX_LEGS==48 && SpatialRelayRules.HOLD_TICKS==6
                && SpatialRelayRules.PREPARATION_TICKS==cn.academy.port.core.SkillTuning.number("spatial_relay", "duration_ticks"), "approved schedule bounds fixed");
        double[] costs = {4,2,1,.25,.025};
        for (int q=1;q<=5;q++) check(SpatialRelayRules.cpCost(BigDecimal.valueOf(q))==costs[q-1], "CP anchor "+q);
        for (int q=1;q<5;q++) {
            double mid=SpatialRelayRules.cpCost(BigDecimal.valueOf(q).add(new BigDecimal(".5")));
            check(mid<costs[q-1] && mid>costs[q], "continuous CP interpolation "+q);
        }
        UUID owner=UUID.randomUUID(), entity=UUID.randomUUID(), nonce=UUID.randomUUID();
        var proof=new SpatialRelayRules.Proof(owner,entity,nonce,"minecraft:cobblestone",64);
        check(SpatialRelayRules.exactProof(proof,proof), "exact proof admits original stack");
        check(!SpatialRelayRules.exactProof(proof,new SpatialRelayRules.Proof(owner,entity,UUID.randomUUID(),"minecraft:cobblestone",64)), "repicked/rethrown same UUID requires new nonce");
        check(!SpatialRelayRules.exactProof(proof,new SpatialRelayRules.Proof(owner,UUID.randomUUID(),nonce,"minecraft:cobblestone",64)), "copied proof cannot authorize new entity UUID");
        check(!SpatialRelayRules.exactProof(proof,new SpatialRelayRules.Proof(UUID.randomUUID(),entity,nonce,"minecraft:cobblestone",64)), "owner differs");
        check(!SpatialRelayRules.exactProof(proof,new SpatialRelayRules.Proof(owner,entity,nonce,"minecraft:stone",64)), "same count different item denied");
        check(!SpatialRelayRules.exactProof(proof,new SpatialRelayRules.Proof(owner,entity,nonce,"minecraft:cobblestone",63)), "partial merge/count shrink denied");
        check(!SpatialRelayRules.exactProof(proof,new SpatialRelayRules.Proof(owner,entity,nonce,"minecraft:cobblestone",65)), "count growth denied");
        check(!new SpatialRelayRules.Proof(owner,entity,new UUID(0,0),"minecraft:stone",1).valid(), "zero nonce denied");
        check(!new SpatialRelayRules.Proof(owner,entity,nonce,"academy:needle",1).valid(), "modded physical custody medium denied");
        check(!new SpatialRelayRules.Proof(owner,entity,nonce,"minecraft:stone",0).valid(), "empty count denied");
        var sweep=SpatialRelayRules.sweep(body(origin),origin,x32);
        check(sweep.destination().equals(x32) && sweep.destinationBody().equals(body(x32)), "exact destination body retained");
        check(sweep.intersects(new Box(16,64,-.2,Math.nextUp(16d),64.25,.2)), "thin interior wall cannot be sampled away");
        check(!sweep.intersects(new Box(16,64,.125,17,64.25,.5)), "exact shoulder tangency allowed");
        check(sweep.intersects(new Box(16,64,Math.nextDown(.125),17,64.25,.5)), "one ULP shoulder penetration denied");
        check(!sweep.intersects(new Box(0,63,-1,SpatialRelayRules.RANGE,64,1)), "ground face tangency allowed");
        check(sweep.intersects(new Box(0,63,-1,SpatialRelayRules.RANGE,Math.nextUp(64d),1)), "one ULP floor penetration denied");
        check(SpatialRelayRules.insideBorder(body(origin),-.125,-.125,.125,.125), "exact border body contact allowed");
        check(!SpatialRelayRules.insideBorder(body(origin),Math.nextUp(-.125),-.125,.125,.125), "border minface penetration denied");
        check(!SpatialRelayRules.insideBorder(body(origin),-.125,-.125,Math.nextDown(.125),.125), "border maxface penetration denied");
        check(!SpatialRelayRules.itemBody(new Box(0,0,0,1,.25,.25)), "mutated item dimensions rejected");
        try { SpatialRelayRules.sweep(body(origin),origin,new Point(Math.nextUp(SpatialRelayRules.RANGE),64,0)); throw new AssertionError("out of range sweep"); }
        catch (IllegalArgumentException expected) { checks++; }
        try { SpatialRelayRules.cells(Integer.MIN_VALUE,0,0,Integer.MAX_VALUE,1,1); throw new AssertionError("overflow scan"); }
        catch (IllegalArgumentException expected) { checks++; }
        Point nonDyadicSource = new Point(30.49,64,0), nonDyadicTarget = new Point(6.51,64,0);
        var leftward = SpatialRelayRules.sweep(body(nonDyadicSource),nonDyadicSource,nonDyadicTarget);
        check(leftward.destinationBody().equals(body(nonDyadicTarget)), "non-dyadic leftward native endpoint recomputes canonical faces");
        check(body(nonDyadicSource).minX() + (nonDyadicTarget.x()-nonDyadicSource.x()) != body(nonDyadicTarget).minX(),
                "regression fixture exposes rounded subtraction/addition mismatch");
        double nativeMin = body(nonDyadicTarget).minX();
        check(leftward.intersects(new Box(Math.nextDown(nativeMin),64,-.1,Math.nextUp(nativeMin),64.2,.1)),
                "one-ULP native destination intrusion is covered by sweep");
        check(!leftward.intersects(new Box(Math.nextDown(nativeMin),64,-.1,nativeMin,64.2,.1)),
                "canonical endpoint face tangency stays clear");
        Point largeSource = new Point(29_999_998.49,64,0), largeTarget = new Point(29_999_974.51,64,0);
        var large = SpatialRelayRules.sweep(body(largeSource),largeSource,largeTarget);
        check(large.destinationBody().equals(body(largeTarget)), "world-border-scale non-dyadic destination is canonical");
        check(large.intersects(new Box(Math.nextDown(body(largeTarget).minX()),64,-.1,
                Math.nextUp(body(largeTarget).minX()),64.2,.1)), "world-border-scale endpoint shoulder remains covered");
        Point diagonalTarget = new Point(6.51,64.1,1.1);
        var diagonal = SpatialRelayRules.sweep(body(nonDyadicSource),nonDyadicSource,diagonalTarget);
        check(diagonal.destinationBody().equals(body(diagonalTarget)), "all three non-dyadic endpoint axes canonical");
        for (int i=1;i<=1000;i++) {
            double z=i/8192d;
            check(sweep.intersects(new Box(16,64,.125-z,17,64.25,.5)), "positive penetration "+i);
            check(!sweep.intersects(new Box(16,64,.125+z,17,64.25,.5)), "clear shoulder "+i);
        }
        System.out.println("SpatialRelayRulesRegressionTest passed "+checks+" checks");
    }
}

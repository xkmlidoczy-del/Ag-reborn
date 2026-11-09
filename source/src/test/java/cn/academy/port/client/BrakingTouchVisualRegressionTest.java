package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.UUID;

public final class BrakingTouchVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER = new UUID(413, 1);
    private static final Point ZERO = new Point(0, 0, 0), FEET = new Point(2, 64, 3), VELOCITY = new Point(.3, -.8, .4);
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static BrakingTouchState.Frame frame(UUID owner, long input, long token, long sequence, int phase, long expires) {
        return new BrakingTouchState.Frame(new BrakingTouchState.Identity(19, owner, 71, input, token), sequence, phase, FEET,
                phase == BrakingTouchState.BRAKED || phase == BrakingTouchState.LANDED ? VELOCITY : ZERO, ZERO, expires);
    }
    private static BrakingTouchState.Frame frame(long sequence, int phase, long expires) { return frame(OWNER, 1, 10, sequence, phase, expires); }
    public static void main(String[] args) {
        BrakingTouchState state = new BrakingTouchState();
        check(state.accept(frame(1, BrakingTouchState.ARMED, 140), true, 100), "authenticated arm receipt accepted");
        check(BrakingTouchGeometry.shape(state.current(OWNER), 0).vertexCount() == 0, "arm/freefall cannot show braking or ground-ring success");
        check(!state.accept(frame(1, BrakingTouchState.ARMED, 140), true, 100), "duplicate sequence rejected");
        check(!state.accept(frame(2, BrakingTouchState.ARMED, 141), true, 101), "arm heartbeat cannot extend the original deadline");
        state.tick(false, 101, c -> true);
        check(state.accept(frame(2, BrakingTouchState.ARMED, 140), true, 101) && state.current(OWNER).age() == 1,
                "legitimate arm update cannot restart its local clock");
        check(!state.accept(frame(3, BrakingTouchState.BRAKED, 113), true, 101), "an arm cannot turn into a tap success");
        check(state.accept(frame(3, BrakingTouchState.LANDED, 113), true, 101), "genuine landing replaces arm");
        var landing = state.current(OWNER); var full = BrakingTouchGeometry.shape(landing, 0);
        check(full.rings().size() == 1 && full.strokes().isEmpty() && full.vertexCount() == 4, "landing has one low ring and never implies velocity zeroing");
        var ring = full.rings().getFirst();
        check(Math.abs(ring.a().y() - 64.018) < 1e-10 && ring.a().y() == ring.b().y() && ring.b().y() == ring.c().y(), "landing ring lies just above confirmed contact ground");
        check(!state.accept(frame(4, BrakingTouchState.LANDED, 114), true, 102), "new sequence cannot replay landing success");
        check(!state.accept(frame(4, BrakingTouchState.ARMED, 142), true, 102), "spent landing cannot rearm");
        for (int tick = 0; tick < 50; tick++) state.tick(true, 102, c -> true);
        check(state.current(OWNER).age() == 0, "pause freezes effect age");
        for (int tick = 1; tick <= 11; tick++) state.tick(false, 101 + tick, c -> true);
        var faded = BrakingTouchGeometry.shape(state.current(OWNER), .5);
        check(faded.rings().getFirst().alpha() < full.rings().getFirst().alpha(), "landing ring fades to zero");
        check(state.tick(false, 113, c -> true).size() == 1 && state.size() == 0, "effect expires on its bounded deadline once");
        check(!state.accept(frame(5, BrakingTouchState.LANDED, 125), true, 113), "expired identity remains replay fenced");
        check(!state.accept(frame(OWNER, 2, 9, 1, BrakingTouchState.BRAKED, 125), true, 113), "new input cannot recycle a lower token");
        check(state.accept(frame(OWNER, 2, 11, 1, BrakingTouchState.BRAKED, 125), true, 113), "new identity accepted");
        check(BrakingTouchGeometry.shape(state.current(OWNER), 0).rings().isEmpty(), "object braking never draws a landing ring");
        state.clear(); check(!state.accept(frame(OWNER, 2, 11, 2, BrakingTouchState.BRAKED, 125), true, 114), "reload preserves tombstones");
        state.cancel(71, 3); check(!state.accept(frame(OWNER, 3, 12, 1, BrakingTouchState.LANDED, 125), true, 114), "release-before-ack fences delayed landing");
        check(state.accept(frame(OWNER, 3, 12, 1, BrakingTouchState.LANDED, 125), false, 114), "local release fence cannot cancel remote input identities");
        check(state.tick(true, 114, c -> false).size() == 1 && state.size() == 0, "context loss retires even when paused");
        state.resetSession(); check(state.accept(frame(1, BrakingTouchState.BRAKED, 112), true, 100), "new connection alone resets replay watermark");
        check(!state.accept(frame(new UUID(413, 2), 1, 1, 1, BrakingTouchState.LANDED, 100), false, 100), "already expired packets ignored");
        check(!state.accept(frame(new UUID(413, 2), 1, 1, 1, BrakingTouchState.BRAKED, 113), false, 100), "future overlong receipt lease ignored");
        check(!state.accept(frame(new UUID(413, 2), 1, 1, 1, BrakingTouchState.ARMED, Long.MAX_VALUE), false, 100), "unbounded arm deadline ignored");
        var id = landing.id();
        check(!BrakingTouchState.valid(new BrakingTouchState.Frame(id, 1, BrakingTouchState.BRAKED, FEET, ZERO, ZERO, 112)), "stationary object cannot synthesize stopped-velocity success");
        check(!BrakingTouchState.valid(new BrakingTouchState.Frame(id, 1, BrakingTouchState.LANDED, FEET, new Point(0, 1, 0), ZERO, 112)), "upward motion cannot claim landing");
        check(!BrakingTouchState.valid(new BrakingTouchState.Frame(id, 1, BrakingTouchState.BRAKED, FEET, VELOCITY, VELOCITY, 112)), "unstopped velocity cannot claim object brake");
        check(BrakingTouchState.valid(new BrakingTouchState.Frame(id, 1, BrakingTouchState.LANDED, FEET, VELOCITY, new Point(0, -2, 0), 112)), "landing displacement and unchanged stored velocity have distinct semantics");
        check(!BrakingTouchState.valid(new BrakingTouchState.Frame(id, 1, BrakingTouchState.ARMED, FEET, VELOCITY, ZERO, 140)), "arm cannot carry success vectors");
        check(!BrakingTouchState.valid(new BrakingTouchState.Frame(id, 1, BrakingTouchState.BRAKED, new Point(Double.NaN,0,0), VELOCITY, ZERO,112)), "nonfinite coordinates rejected");
        for (Point velocity : new Point[]{new Point(1,0,0), new Point(0,-1,0), new Point(0,0,1), VELOCITY, new Point(-100,80,-50)}) {
            var f = new BrakingTouchState.Frame(id, 1, BrakingTouchState.BRAKED, new Point(29_999_999,64,-29_999_999), velocity, ZERO,112);
            var shape = BrakingTouchGeometry.shape(new BrakingTouchState.Controller(f, false, 0), .2);
            var shrinking = BrakingTouchGeometry.shape(new BrakingTouchState.Controller(f, false, 11), .2);
            check(shrinking.strokes().getFirst().alpha() < shape.strokes().getFirst().alpha()
                    && shrinking.strokes().getFirst().from().distanceSquared(shrinking.strokes().getFirst().to()) < shape.strokes().getFirst().from().distanceSquared(shape.strokes().getFirst().to()), "actual object velocity strokes shrink and fade");
            check(shape.vertexCount() <= BrakingTouchGeometry.MAX_CONTROLLER_VERTICES, "large-coordinate geometry stays in per-owner budget");
            for (var stroke : shape.strokes()) {
                Point a = stroke.from(), b = stroke.to(); double dx = b.x()-a.x(), dy=b.y()-a.y(), dz=b.z()-a.z();
                check(dx*velocity.x()+dy*velocity.y()+dz*velocity.z() > 0, "stroke direction follows receipt's actual pre-velocity");
                check(a.finite() && b.finite() && a.distanceSquared(b) <= .65*.65, "velocity stroke stays finite and short");
                var core = BrakingTouchGeometry.profile(stroke,true); var soft = BrakingTouchGeometry.profile(stroke,false);
                check(core.gray() == 237 && soft.gray() == 181 && soft.width() > core.width(), "white core and gray shoulder preserve source texture material");
            }
        }
        Point camera = new Point(29_999_999,64,-29_999_999), world = new Point(29_999_999.03125,64.015625,-29_999_998.9375);
        Point relative = BrakingTouchGeometry.relative(world,camera);
        check(relative.equals(new Point(.03125,.015625,.0625)), "relative double subtraction retains sub-float world precision");
        var crossing = new BrakingTouchGeometry.Stroke(new Point(0,0,-1),new Point(0,0,1),.02,200,BrakingTouchGeometry.Role.VELOCITY);
        var clipped = BrakingTouchGeometry.clipNear(crossing,new Point(0,0,1),.01);
        check(clipped != null && clipped.from().z() >= .09-1e-12, "near-eye crossing clipped behind expanded near plane");
        check(BrakingTouchGeometry.clipNear(crossing,new Point(0,0,-1),.01) != null, "opposite-facing clipping remains finite");
        check(!BrakingTouchGeometry.ringInFront(ring,new Point(2,64,3),new Point(0,0,1)), "eye-crossing ground ring rejected as whole quad");
        var budget = new BrakingTouchGeometry.Budget();
        for (int i=0;i<256;i++) check(budget.take(4), "fixed quad budget admitted");
        check(!budget.take(4) && !budget.take(-1) && budget.used()==1024, "global vertex cap cannot overflow");
        check(BrakingTouchGeometry.MAX_CONTROLLERS==4 && BrakingTouchGeometry.MAX_PARTICLES==0, "four visible owners and zero particles");
        BrakingTouchState bounded = new BrakingTouchState();
        for (int i=0;i<64;i++) check(bounded.accept(frame(new UUID(413,i+1),1,1,1,BrakingTouchState.ARMED,140),false,100), "bounded live context");
        check(!bounded.accept(frame(new UUID(413,1000),1,1,1,BrakingTouchState.ARMED,140),false,100), "sixty-fifth live context rejected");
        bounded.clear();
        for (int i=64;i<1024;i++) check(bounded.accept(frame(new UUID(413,i+1),1,1,1,BrakingTouchState.ENDED,100),false,100), "bounded tombstone admitted");
        check(!bounded.accept(frame(new UUID(413,5000),1,1,1,BrakingTouchState.ENDED,100),false,100) && bounded.watermarkCount()==1024,
                "saturation never evicts replay protection");
        System.out.println("BrakingTouchVisualRegressionTest: " + checks + " checks passed");
    }
}

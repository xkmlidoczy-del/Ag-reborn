package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Receipt ordering, native-only endpoints, finite budgets, interruption and world-border precision. */
public final class ShortInterceptionVisualRegressionTest {
    private static final UUID OWNER = new UUID(331,1);
    private static final Point TARGET = new Point(12,65,0), IN = new Point(0,0,1), OUT = new Point(1,0,0);
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static ShortInterceptionState.Frame frame(UUID owner,long token,long input,long sequence,int phase,int life,List<ShortInterceptionState.Segment> segments,int admitted) {
        return new ShortInterceptionState.Frame(new ShortInterceptionState.Identity(19,owner,71,input,token),sequence,phase,life,
                TARGET,segments,"",false,admitted);
    }
    private static ShortInterceptionState.Frame mark(long input,long sequence,int life) { return frame(OWNER,1,input,sequence,0,life,List.of(),0); }
    private static ShortInterceptionState.Segment segment(double z) {
        return new ShortInterceptionState.Segment(new Point(2,65,z),new Point(10,65,z),IN,OUT);
    }
    public static void main(String[] args) {
        var state = new ShortInterceptionState(); var first = mark(1,1,400);
        check(state.accept(first,true) && state.markerVisible(),"authenticated target marker admitted");
        check(!state.accept(first,true),"duplicate sequence rejected");
        for (int i=0;i<90;i++) state.tick(true,true);
        check(state.remainingTicks()==400 && state.markerVisible(),"pause freezes lifetime and heartbeat");
        for (int i=0;i<31;i++) check(state.tick(false,true)==null,"heartbeat silence is cosmetic only");
        check(!state.markerVisible() && state.active()!=null && state.remainingTicks()==369,"stale marker hides without clearing locked target");
        check(state.accept(mark(1,2,390),true) && state.remainingTicks()==369,"heartbeat cannot extend local lifetime");
        check(!state.accept(mark(1,3,400),true),"remaining time cannot grow");
        check(!state.accept(mark(2,3,380),true),"heartbeat cannot authenticate an unrelated second press");
        var refusal=frame(OWNER,1,2,3,3,380,List.of(),0);
        check(state.accept(refusal,true)&&state.remainingTicks()==369&&state.markerVisible(),"ordinary refusal preserves original lock and lifetime");
        var list=new ArrayList<ShortInterceptionState.Segment>(); for(int i=0;i<4;i++)list.add(segment(i*.3));
        var projected=frame(OWNER,1,3,4,1,0,list,4);list.clear();
        check(projected.segments().size()==4,"receipt captures immutable groups");
        check(state.accept(projected,true)&&state.active()==null,"confirmed native movement consumes target, not a prediction or impact");
        check(!state.accept(projected,true)&&!state.accept(mark(3,5,300),true),"projected input cannot replay or revive its target");
        var contact=frame(OWNER,1,3,5,2,0,List.of(segment(2)),4);
        check(state.permitsImpact(contact)&&state.accept(contact,true),"only previously authenticated projection accepts actual native contact");
        check(!state.accept(frame(OWNER,1,3,6,2,0,List.of(segment(2)),4),true),"contact cannot replay with a new sequence");
        check(!state.accept(frame(OWNER,1,3,6,2,0,List.of(segment(3)),3),true),"contact cannot change admitted cast count");
        for(int i=0;i<60;i++)state.tick(false,true);
        check(!state.accept(frame(OWNER,1,3,6,2,0,List.of(segment(3)),4),true),"bounded contact latency window expires");
        var observer=new ShortInterceptionState();
        check(!observer.accept(first,false),"target marker stays owner-only");
        check(!observer.accept(contact,false),"contact alone cannot invent a cast");
        check(observer.accept(projected,false),"observer can see confirmed native relocation");
        check(observer.accept(frame(OWNER,1,3,6,5,0,List.of(),0),false),"server end cleans up an already-consumed input");
        check(!observer.accept(frame(OWNER,1,3,7,2,0,List.of(segment(3)),4),false),"end prevents later native-contact cosmetics");
        observer.resetSession();observer.accept(projected,false);observer.clear();
        check(!observer.accept(contact,false),"world/context clear retires all observer receipts");
        state.resetSession();check(state.accept(first,true),"verified new connection resets bounded fences");state.cancel(71,1);
        check(!state.accept(mark(1,2,399),true),"cancel rejects delayed packet");
        var expiring=new ShortInterceptionState();expiring.accept(mark(1,1,1),true);
        check(expiring.tick(false,true)!=null&&!expiring.markerVisible(),"target expires on exact final tick");
        var abandoned=new ShortInterceptionState();abandoned.accept(first,true);
        check(abandoned.tick(true,false)!=null&&!abandoned.markerVisible(),"pause never hides ownership loss");
        check(!ShortInterceptionState.valid(frame(OWNER,1,1,1,0,ShortInterceptionState.TARGET_LIFE_TICKS + 1,List.of(),0)),"excessive target lifetime rejected");
        check(!ShortInterceptionState.valid(frame(OWNER,1,1,1,1,0,List.of(segment(0)),4)),"admitted group count must equal actual segments");
        check(!ShortInterceptionState.valid(frame(OWNER,1,1,1,1,0,List.of(segment(0),segment(1),segment(2),segment(3),segment(4)),5)),"fifth group rejected");
        var fallbackRefusal=new ShortInterceptionState.Frame(first.id(),2,3,390,TARGET,List.of(),"no_medium",true,0);
        var retained=new ShortInterceptionState();retained.accept(first,true);
        check(retained.accept(fallbackRefusal,true)&&retained.markerVisible(),"empty fallback refusal preserves marker despite accounting metadata");
        var partial=new ShortInterceptionState.Frame(new ShortInterceptionState.Identity(19,OWNER,71,2,1),3,3,0,TARGET,List.of(segment(0)),"commit_unconfirmed",false,1);
        check(retained.accept(partial,true)&&retained.active()==null,"partial paid refusal closes input without inventing projected success");
        check(!retained.permitsImpact(frame(OWNER,1,2,4,2,0,List.of(segment(1)),1)),"partial refusal is never promoted to an authenticated projected cosmetic");
        var saturated=new ShortInterceptionState();
        for(int i=0;i<ShortInterceptionState.MAX_WATERMARKS;i++)check(saturated.accept(frame(new UUID(331,i+1),1,1,1,0,1,List.of(),0),true),"bounded watermark insertion");
        check(!saturated.accept(frame(new UUID(331,9999),1,1,1,0,1,List.of(),0),true)&&saturated.watermarkCount()==1024,"capacity refuses admission without evicting replay fences");
        geometry();
        System.out.println("ShortInterceptionVisualRegressionTest: "+checks+" pure checks passed; no native-rendered visual claim");
    }
    private static void geometry() {
        check(ShortInterceptionMesh.MAX_ENDPOINT_LINES==56,"fixed budget: four groups, two endpoints, seven strokes each");
        for(var velocity:List.of(IN,OUT,new Point(0,1,0),new Point(0,-1,0),new Point(.001,1,.001)))for(boolean entry:new boolean[]{true,false}) {
            var lines=ShortInterceptionMesh.endpoint(TARGET,velocity,entry);
            check(lines.size()==7,"vertical and horizontal planes have fixed topology");
            for(var line:lines)check(line.from().finite()&&line.to().finite()
                    && line.from().distanceSquared(TARGET)<.4&&line.to().distanceSquared(TARGET)<.4,
                    "all strokes remain within their endpoint, with no teleport bridge");
        }
        check(ShortInterceptionMesh.endpoint(TARGET,new Point(0,0,0),true).isEmpty(),"zero direction rejected");
        check(ShortInterceptionMesh.endpoint(new Point(Double.NaN,0,0),IN,true).isEmpty(),"nonfinite position rejected");
        for(int i=0;i<7;i++)check(ShortInterceptionMesh.frame(i)==i&&ShortInterceptionMesh.alpha(i)>0,"exact seven original atlas frames");
        check(ShortInterceptionMesh.alpha(7)==0&&ShortInterceptionMesh.alpha(-1)==0,"cosmetics stop at seven ticks");
        var point=ShortInterceptionMesh.relative(new Point(30_000_000.01,65,30_000_000.02),new Point(30_000_000,64,30_000_000));
        check(Math.abs(point.x()-.01)<1e-8&&Math.abs(point.z()-.02)<1e-8&&point.y()==1,"double camera subtraction retains border centimetres");
        for(int scale=1;scale<=4;scale++) {
            int width=(1178+scale-1)/scale,height=(814+scale-1)/scale;
            var layout=CornerExchangeHudLayout.fit(width,height,3,9);
            check(layout!=null&&layout.top()>=height/2+16&&layout.top()+layout.height()<=height-48,"guidance stays below crosshair and above hotbar at GUI scale"+scale);
        }
    }
}

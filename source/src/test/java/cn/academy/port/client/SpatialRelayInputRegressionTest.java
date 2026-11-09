package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.util.ArrayList;
import java.util.List;

/** Native press/release ordering, no press-time relay, persistent ownership, and immutable lease. */
public final class SpatialRelayInputRegressionTest {
    private static int checks;
    private static final Object WORLD=new Object(), PLAYER=new Object(), CONNECTION=new Object();
    private static SpatialRelayInputState.Gates gates(long revision, boolean ready, boolean source, boolean crouch) {
        return new SpatialRelayInputState.Gates(new SpatialRelayInputState.DispatchContext(WORLD,PLAYER,CONNECTION,1,19,71,0,revision,"relay"),
                0,-100,true,true,true,true,true,true,true,true,true,ready,source,crouch);
    }
    private static HighPowerInput.Input id(long input){return new HighPowerInput.Input(0,input,19,71);}
    private static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    public static void main(String[] args) {
        var state=new SpatialRelayInputState();var g=gates(1,true,true,false);var packets=new ArrayList<String>();
        check(state.press(id(1),g),"fresh native press captured");
        check(!state.press(id(2),g),"held key cannot auto-repeat");
        check(packets.isEmpty()&&!state.permits(1,71),"Pre has no action or accepted receipt");
        state.release(id(99));check(state.holding(),"foreign release cannot act");
        check(state.flushStartPost(g,i->packets.add("start"),i->packets.add("abort")),"Post queues server-owned gesture");
        check(!state.permitsReceipt(1,71,false)&&state.permitsReceipt(1,71,true),"unreleased input accepts terminal clear only");
        state.accepted(1,71,400,false);check(state.pendingCount()==1&&state.acceptedRemainingTicks()==0,"new-nonce heartbeat cannot consume pending release");
        for(int i=0;i<6;i++)state.tick(g,false,x->packets.add("abort"));
        check(packets.equals(List.of("start"))&&state.queuedRelease()==null,"six-tick hold never executes a relay first");
        state.ended(1,71);state.release(id(1));
        check(!state.flushReleasePost(g,i->packets.add("release"),i->packets.add("abort")),"server hold-clear acknowledgement prevents late release action");
        check(state.current()==null,"hold-clear releases ownership after key up");
        check(state.press(id(2),g),"fresh edge after clear");state.release(id(2));
        check(!state.flushReleasePost(g,i->packets.add("release"),i->packets.add("abort")),"release cannot precede its start");
        state.flushStartPost(g,i->packets.add("start2"),i->packets.add("abort"));
        state.flushReleasePost(g,i->packets.add("release2"),i->packets.add("abort"));
        check(packets.subList(1,3).equals(List.of("start2","release2")),"same tick tap preserves start-before-release ordering");
        state.accepted(2,71,400,false);check(state.permits(2,71),"marked receipt outlives genuine release");
        for(int i=0;i<75;i++)state.tick(g,true,x->packets.add("abort"));
        check(state.acceptedRemainingTicks()==400,"pause freezes accepted lifetime and ack age");
        for(int i=0;i<20;i++)state.tick(g,false,x->packets.add("abort"));
        check(state.press(id(3),gates(1,false,false,true)),"free clear available during cooldown and bad posture");
        state.release(id(3));state.flushStartPost(g,i->{},i->{});state.flushReleasePost(g,i->{},i->{});
        state.accepted(3,71,399,false);
        check(state.acceptedRemainingTicks()==380&&state.permits(3,71)&&!state.permits(2,71),"Refusal adopts latest nonce without renewing marks");
        check(state.press(id(4),g),"later tap admitted");state.release(id(4));state.flushStartPost(g,i->{},i->{});state.flushReleasePost(g,i->{},i->{});
        state.accepted(4,71,375,true);check(state.eligible(g),"retained refusal allows a fresh tap without renewing lock");
        check(state.acceptedRemainingTicks()==375,"server can shorten the immutable marks lifetime");
        check(state.press(id(5),g),"next fresh standing tap");state.release(id(5));state.flushStartPost(g,i->{},i->{});state.flushReleasePost(g,i->{},i->{});
        state.accepted(5,71,370,false);check(state.eligible(g)&&state.permits(5,71),"ordinary refusal preserves marks");
        state.tick(gates(2,true,true,false),false,x->packets.add("cancel:"+x.input()));
        check(state.current()==null&&packets.contains("cancel:5"),"preset revision change authenticates cancel before clearing");
        check(!state.press(id(5),g),"input highwater survives cancellation");
        var timeout=new SpatialRelayInputState();timeout.press(id(1),g);timeout.flushStartPost(g,i->{},i->{});
        for(int i=0;i<59;i++)timeout.tick(g,false,x->{});check(timeout.permits(1,71),"ack admission before timeout");
        timeout.tick(g,false,x->{});check(timeout.current()==null,"unacknowledged native gesture expires safely");
        var lease=new SpatialRelayInputState();lease.press(id(1),g);lease.release(id(1));lease.flushStartPost(g,i->{},i->{});lease.flushReleasePost(g,i->{},i->{});lease.accepted(1,71,1,false);
        lease.tick(g,false,x->{});check(lease.current()==null,"immutable lease expires without heartbeat renewal");
        var queue=new SpatialRelayInputState();queue.press(id(1),g);queue.release(id(1));queue.flushStartPost(g,i->{},i->{});queue.flushReleasePost(g,i->{},i->{});queue.accepted(1,71,390,true);
        check(queue.press(id(2),g),"queue permits a genuine free clear hold gesture");queue.release(id(2));queue.flushStartPost(g,i->{},i->{});queue.flushReleasePost(g,i->{},i->{});
        queue.rejectedGesture(2,71);check(queue.permits(1,71)&&!queue.permits(2,71)&&queue.pendingCount()==0,"busy short release acknowledges gesture without replacing frozen queue nonce");
        for(int i=0;i<61;i++)queue.tick(g,false,x->{throw new AssertionError("busy refusal must not timeout a valid queue");});
        check(queue.permits(1,71),"queue continues after busy gesture beyond ack deadline");
        System.out.println("SpatialRelayInputRegressionTest: "+checks+" checks passed");
    }
}

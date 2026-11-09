package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.util.ArrayList;
import java.util.List;

/** Actual release ownership, long holds, retained refusals and non-destructive client timeouts. */
public final class EmergencyReturnInputRegressionTest {
    private static int checks;
    private static final Object WORLD=new Object(),PLAYER=new Object(),CONNECTION=new Object();
    private static EmergencyReturnInputState.Gates gates(long revision,boolean ready,boolean source) {
        return new EmergencyReturnInputState.Gates(new EmergencyReturnInputState.DispatchContext(WORLD,PLAYER,CONNECTION,1,19,71,0,revision,"emergency"),
                0,-100,true,true,true,true,true,true,true,true,true,ready,source,false);
    }
    private static HighPowerInput.Input id(long input){return new HighPowerInput.Input(0,input,19,71);}
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) {
        var s=new EmergencyReturnInputState();var g=gates(1,true,true);var packets=new ArrayList<String>();
        check(s.press(id(1),g),"genuine down captures identity");
        check(!s.press(id(2),g)&&!s.permits(1,71)&&packets.isEmpty(),"held repeat and unsent receipt rejected");
        s.release(id(99));check(s.holding(),"foreign up cannot release");
        s.flushStartPost(g,i->packets.add("start"),i->packets.add("abort"));
        check(s.permitsReceipt(1,71,true)&&!s.permitsReceipt(1,71,false),"held request admits cancellation but no mark or success");
        s.accepted(1,71,1200,false);check(s.pendingCount()==1&&s.acceptedRemainingTicks()==0,"unsolicited held heartbeat cannot arm");
        for(int i=0;i<1300;i++)s.tick(g,false,x->packets.add("abort"));
        check(s.holding()&&s.pendingCount()==1&&packets.equals(List.of("start")),"long hold waits for native up without local six-tick guess or timeout abort");
        s.release(id(1));check(packets.equals(List.of("start")),"native up only queues");
        check(s.flushReleasePost(g,i->packets.add("release"),i->packets.add("abort")),"even a long hold reaches server release classification");
        check(packets.equals(List.of("start","release")),"no manual return before hold clear");
        s.ended(1,71);check(s.current()==null,"confirmed free clear consumes input");
        check(s.press(id(2),g),"fresh short tap captured");s.release(id(2));
        check(!s.flushReleasePost(g,i->packets.add("wrong"),i->{}),"release cannot outrun queued start");
        s.flushStartPost(g,i->packets.add("start2"),i->{});s.flushReleasePost(g,i->packets.add("release2"),i->{});
        check(packets.subList(2,4).equals(List.of("start2","release2")),"same-tick tap emits ordered native Post edges");
        s.accepted(2,71,1200,false);check(s.permits(2,71)&&s.pendingCount()==0,"server mark survives physical release");
        for(int i=0;i<80;i++)s.tick(g,true,x->packets.add("abort"));
        check(s.acceptedRemainingTicks()==1200,"pause freezes countdown");
        for(int i=0;i<1250;i++)s.tick(g,false,x->packets.add("abort"));
        check(s.permits(2,71)&&s.acceptedRemainingTicks()==0&&!packets.contains("abort"),"client lifetime estimate cannot destroy server-timed mark");
        s.accepted(2,71,600,false);check(s.acceptedRemainingTicks()==600,"fresh server heartbeat recovers hidden estimate");
        check(s.press(id(3),gates(1,false,false)),"free clear can start during cooldown or airborne posture");
        s.release(id(3));s.flushStartPost(g,i->{},i->{});s.flushReleasePost(g,i->{},i->{});
        s.accepted(3,71,590,false);
        check(s.permits(3,71)&&!s.permits(2,71)&&s.eligible(g),"retained refusal adopts new nonce and keeps mark usable");
        s.accepted(3,71,1,true);check(s.warning()&&!s.eligible(g),"actual queued auto owns pending transaction");
        for(int i=0;i<65;i++)s.tick(g,false,x->packets.add("abort"));
        check(s.warning()&&s.permits(3,71)&&!packets.contains("abort"),"one-tick cosmetic warning never predicts arrival or aborts slow server");
        s.ended(3,71);check(s.current()==null,"actual success or terminal failure consumes mark");
        check(s.press(id(4),g),"next request allowed");s.release(id(4));s.flushStartPost(g,i->{},i->{});s.flushReleasePost(g,i->{},i->{});
        for(int i=0;i<60;i++)s.tick(g,false,x->packets.add("abort"));
        check(s.current()==null&&!s.permits(4,71)&&!packets.contains("abort"),"unacknowledged release expires locally without destructive server abort");
        check(s.press(id(5),g),"new nonce follows timeout");s.release(id(5));s.flushStartPost(g,i->{},i->{});s.flushReleasePost(g,i->{},i->{});s.accepted(5,71,1000,false);
        check(s.press(id(6),g),"retry gesture starts while previous mark exists");s.release(id(6));s.flushStartPost(g,i->{},i->{});s.flushReleasePost(g,i->{},i->{});
        for(int i=0;i<60;i++)s.tick(g,false,x->packets.add("abort"));
        check(s.permits(5,71)&&!s.permits(6,71),"lost manual receipt cannot delete previous authenticated mark");
        s.tick(gates(2,true,true),false,x->packets.add("cancel:"+x.input()));
        check(s.current()==null&&packets.contains("cancel:5")&&packets.contains("cancel:6"),"real preset loss cancels known anchor and current gesture");
        check(!s.press(id(6),g),"nonce highwater survives context cancellation");
        s.reset();check(s.press(id(1),g),"verified connection reset resets local nonce fence");
        System.out.println("EmergencyReturnInputRegressionTest: "+checks+" checks passed");
    }
}

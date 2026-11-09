package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;

/** Reproduces the real multi-tap deadlock and verifies exact-input END acknowledgement for either network ordering. */
public final class ConstructCommandInputRegressionTest {
    private static void check(boolean b,String why) { if(!b) throw new AssertionError(why); }
    public static void main(String[] args) {
        Object world=new Object(),player=new Object(),connection=new Object();
        var context=new BrakingTouchInputState.DispatchContext(world,player,connection,1,19,71,0,1,"construct");
        var gates=new BrakingTouchInputState.Gates(context,0,19,true,true,true,true,true,true,true,true,true,true,true,true);
        for(boolean early:new boolean[]{true,false}) {
            var input=new BrakingTouchInputState();
            for(int command=1;command<=5;command++) {
                var id=new HighPowerInput.Input(0,command,19,71);
                check(input.press(id,gates),"next real command must remain available: "+command);
                check(input.flushStartPost(gates,false,i->{},i->{throw new AssertionError("unexpected abort");}),"start sent");
                if(early)input.ended(command,71);
                input.release(id);
                input.flushReleasePost(gates,i->{},i->{throw new AssertionError("unexpected abort");});
                if(!early) {
                    check(!input.press(new HighPowerInput.Input(0,command+1,19,71),gates),"negative control: missing ack reproduces blocked second tap");
                    input.ended(command,70);
                    check(input.current()!=null,"wrong epoch cannot retire command");
                    input.ended(command,71);
                }
                check(input.current()==null,"completed tap releases ownership without waiting for body lifetime");
            }
        }
        System.out.println("PASS construct command receipt ordering and missing-ack counterexample");
    }
}

package cn.academy.port;
import cn.academy.port.client.ClassicInputLatch;
public final class ClassicInputRegressionTest {
    private static int assertions;
    private static void check(boolean c){assertions++;if(!c)throw new AssertionError("classic input transition");}
    public static void main(String[] args){
        var key=new ClassicInputLatch();var t=key.update(true,true);check(t.press());check(!t.tick());check(key.active());t=key.update(true,true);check(!t.press()&&t.tick());
        t=key.update(true,false);check(t.abort());check(!key.active());t=key.update(true,true);check(!t.press()&&!t.tick());t=key.update(false,true);check(!t.release());t=key.update(true,true);check(t.press());
        key.abort();check(!key.active());t=key.update(true,true);check(!t.press());key.update(false,true);check(key.update(true,true).press());check(key.update(false,true).release());
        t=key.update(true,false);check(!t.press());t=key.update(true,true);check(!t.press());key.update(false,false);check(key.update(true,true).press());
        key.replaceSession(true);check(!key.active());check(!key.update(true,true).press());key.update(false,true);check(key.update(true,true).press());
        for(int i=0;i<100;i++)check(!key.update(true,true).press());
        System.out.println("PASS "+assertions+" source-derived input-edge assertions (no game launch)");
    }
}

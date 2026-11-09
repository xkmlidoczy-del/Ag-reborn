package cn.academy.port;
import cn.academy.port.client.ClientSessionGuard;
public final class ClientSessionRegressionTest {
    private static int assertions;
    private static void check(boolean condition){assertions++;if(!condition)throw new AssertionError("session identity boundary");}
    public static void main(String[] args){
        var guard=new ClientSessionGuard();Object level=new Object(),player=new Object();
        check(!guard.synchronize(null,null));check(guard.synchronize(level,null));check(!guard.synchronize(level,null));check(guard.synchronize(level,player));check(!guard.synchronize(level,player));
        check(guard.synchronize(level,new Object()));check(guard.synchronize(new Object(),player));check(guard.synchronize(null,null));check(!guard.synchronize(null,null));
        System.out.println("PASS "+assertions+" client session identity assertions (no game launch)");
    }
}

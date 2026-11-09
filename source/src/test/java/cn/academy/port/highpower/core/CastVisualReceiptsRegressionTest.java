package cn.academy.port.highpower.core;
import java.util.UUID;
public final class CastVisualReceiptsRegressionTest {
    private static int checks;
    private static void check(boolean pass,String why){checks++;if(!pass)throw new AssertionError(why);}
    public static void main(String[] args){
        var r=new CastVisualReceipts();var a=UUID.randomUUID();var b=UUID.randomUUID();
        check(r.accept(a,"location_teleport",0,7,1),"first accepted relocation");
        check(!r.accept(a,"location_teleport",0,7,1),"duplicate server result cannot replay camera/signature");
        check(r.accept(a,"tactical_coordinate_formation",2,7,1),"skill and epoch ownership independent");
        check(r.accept(b,"location_teleport",0,7,1),"another owner independent");
        check(r.accept(a,"location_teleport",0,8,2),"new input/token plays once");
        for(long token=3;token<1000;token++)r.accept(a,"x",2,token,token);
        check(r.size()==CastVisualReceipts.LIMIT,"bounded receipt history");
        check(!r.accept(null,"x",0,1,1)&&!r.accept(a,"",0,1,1)&&!r.accept(a,"x",-1,1,1)&&!r.accept(a,"x",0,-1,1)&&!r.accept(a,"x",0,1,0),"malformed identity rejected");
        r.clear();check(r.size()==0&&r.accept(a,"location_teleport",0,7,1),"new session clears old identities");
        System.out.println("CastVisualReceiptsRegressionTest: "+checks+" checks passed");
    }
}

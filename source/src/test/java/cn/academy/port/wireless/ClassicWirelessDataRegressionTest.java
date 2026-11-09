package cn.academy.port.wireless;

import cn.academy.port.wireless.ClassicWirelessGraph.*;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Actual modern NBT codec round-trip, including every persisted edge and fractional buffer. */
public final class ClassicWirelessDataRegressionTest {
    private static int assertions;
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static CompoundTag pos(int x,int y,int z) { var tag=new CompoundTag();tag.putInt("x",x);tag.putInt("y",y);tag.putInt("z",z);return tag; }
    public static void main(String[] args) {
        var m=new Pos(-32,64,31);var n1=new Pos(-17,64,15);var n2=new Pos(-1,65,-16);var g=new Pos(-18,63,15);var r=new Pos(-16,64,15);
        var state=new State(List.of(new NetworkData(m,"测试网络","fictional-game-password",123.456789,List.of(n1,n2))),List.of(new ConnectionData(n1,List.of(g),List.of(r))));
        var tag=ClassicWirelessSavedData.writeState(new CompoundTag(),state);check(tag.getInt("schema")==1,"explicit native schema");check(tag.getCompound("net").getList("networks",10).size()==1,"source net/networks shape");check(tag.getCompound("node").getList("list",10).size()==1,"source node/list shape");
        check(ClassicWirelessSavedData.readState(tag).equals(state),"exact native NBT graph/password/fractional buffer roundtrip");
        tag.getCompound("net").getList("networks",10).getCompound(0).putDouble("buffer",Double.NaN);check(ClassicWirelessSavedData.readState(tag).networks().getFirst().buffer()==0,"NaN storage cannot invent buffer");
        tag.getCompound("net").getList("networks",10).getCompound(0).putDouble("buffer",-3);check(ClassicWirelessSavedData.readState(tag).networks().getFirst().buffer()==0,"negative storage sanitized");
        tag.getCompound("net").getList("networks",10).getCompound(0).putDouble("buffer",3000);check(ClassicWirelessSavedData.readState(tag).networks().getFirst().buffer()==2000,"buffer hard capacity2000");
        var missing=new CompoundTag();check(ClassicWirelessSavedData.readState(missing).equals(new State(List.of(),List.of())),"missing root compounds safely empty");
        missing.putString("net","wrongtype");missing.putInt("node",7);check(ClassicWirelessSavedData.readState(missing).networks().isEmpty(),"wrong top-level types empty");
        var malformed=new CompoundTag();var nets=new ListTag();var net=new CompoundTag();net.put("matrix",pos(1,2,3));net.putString("ssid","name");net.putString("password","");var list=new ListTag();list.add(new CompoundTag());list.add(pos(4,5,6));var wrong=pos(0,0,0);wrong.putString("x","0");list.add(wrong);net.put("list",list);nets.add(net);var ntag=new CompoundTag();ntag.put("networks",nets);malformed.put("net",ntag);
        var decoded=ClassicWirelessSavedData.readState(malformed);check(decoded.networks().size()==1,"valid matrix retained");check(decoded.networks().getFirst().nodes().equals(List.of(new Pos(4,5,6))),"missing/wrongtype positions do not silently become origin");
        net.remove("password");check(ClassicWirelessSavedData.readState(malformed).networks().isEmpty(),"missing password rejects partial corrupt network");net.putString("password","");net.getCompound("matrix").remove("z");check(ClassicWirelessSavedData.readState(malformed).networks().isEmpty(),"missing matrix coordinate rejects network");
        var conns=new ListTag();var conn=new CompoundTag();conn.put("node",pos(2,3,4));conn.putString("generators","wrongtype");conn.put("receivers",new ListTag());conns.add(conn);var ct=new CompoundTag();ct.put("list",conns);malformed.put("node",ct);decoded=ClassicWirelessSavedData.readState(malformed);check(decoded.connections().getFirst().generators().isEmpty(),"wrongtype users empty");
        try{state.networks().add(state.networks().getFirst());throw new AssertionError("mutable state");}catch(UnsupportedOperationException expected){assertions++;}
        try{state.networks().getFirst().nodes().clear();throw new AssertionError("mutable nodes");}catch(UnsupportedOperationException expected){assertions++;}
        System.out.println("ClassicWirelessDataRegressionTest: "+assertions+" assertions passed");
    }
}

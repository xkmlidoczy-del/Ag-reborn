package cn.academy.port.highpower;import java.util.*;import java.util.function.Consumer;import net.minecraft.world.phys.Vec3;
public class RailThunderArray{public static final String ID="rail_thunder_array";public static Consumer<Visual> clientReceiver;
 public record Segment(int axis,int entity,UUID target,Vec3 from,Vec3 to,int status){}
 public record Visual(int entity,UUID owner,long input,long token,long epoch,long sequence,int phase,int age,Vec3 origin,List<Vec3> axes,List<Segment> segments,int reason){}}

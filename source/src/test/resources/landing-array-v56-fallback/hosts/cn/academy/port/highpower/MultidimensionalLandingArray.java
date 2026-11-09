package cn.academy.port.highpower;
import java.util.*;import java.util.function.Consumer;import net.minecraft.world.phys.Vec3;
/** Packet-shape host, with all receipt validation performed by the actual Mesh/Effects. No server behavior. */
public final class MultidimensionalLandingArray {public static Consumer<Visual> clientReceiver;public static void diagnostic(String s){}
 public record TargetVisual(UUID uuid,int id,Vec3 position,boolean available){}
 public record Visual(UUID owner,long epoch,long input,long token,long sequence,int phase,List<Vec3> points,int remainingTicks,List<TargetVisual> targets,int shotsTotal,int spent,int published,int retained,int pending,boolean freeOrigin,Vec3 source,Vec3 impact,UUID medium,int slot,int shotIndex,boolean accepted,boolean terminal,String reason){}
}

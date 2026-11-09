package cn.academy.port.highpower;
import java.util.*;import java.util.function.Consumer;import net.minecraft.world.phys.Vec3;
/** Transport DTO only; the complete production receipt validation and state are compiled. */
public final class MulticoreCanopy {
 public static final String ID="multicore_canopy"; public static Consumer<Visual> clientReceiver;
 public record CoreVisual(int id,Vec3 center,boolean available){}
 public record TargetVisual(int id,Vec3 point,int status){}
 public record SegmentVisual(int coreId,int slot,Vec3 start,Vec3 end,int status){}
 public record Visual(int owner,UUID ownerUuid,long epoch,long input,long token,long sequence,int phase,int status,int remainingTicks,Vec3 ownerFeet,List<CoreVisual> cores,List<TargetVisual> targets,List<SegmentVisual> path,boolean held,int shotIndex,int warningTicks){}
}

package cn.academy.port.highpower;
import java.util.*;import java.util.function.Consumer;import net.minecraft.world.phys.Vec3;
/** Wire-shaped host only; production renderer transforms these into the complete production state validator. */
public final class ShearWindblade {
 public static Consumer<Visual> clientReceiver=ignored->{};
 public record FrontPose(int index,List<Vec3> previous,List<Vec3> current,int status){public FrontPose{previous=List.copyOf(previous);current=List.copyOf(current);}}
 public record Impact(int blade,int slot,Vec3 point,int status,int hitEntity){}
 public record Visual(int owner,UUID ownerUuid,long epoch,long input,long token,long sequence,int phase,boolean crouch,Vec3 origin,List<FrontPose> fronts,List<Impact> impacts){public Visual{fronts=List.copyOf(fronts);impacts=List.copyOf(impacts);}}
}

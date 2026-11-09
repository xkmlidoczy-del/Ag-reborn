package cn.academy.port.highpower;
import java.util.UUID;import java.util.function.Consumer;import net.minecraft.world.phys.Vec3;
/** Network transport boundary only: the production Effects validates these native receipt fields. */
public final class TrajectoryReturn {
 public static Consumer<Visual> clientReceiver=ignored->{};
 public record Visual(int owner,UUID ownerUuid,long epoch,long input,long token,long sequence,int kind,int slot,Vec3 origin,Vec3 end,Vec3 velocity,int status) {}
}

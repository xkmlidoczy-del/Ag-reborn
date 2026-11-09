package cn.academy.port.story;
import java.util.*; import net.minecraft.nbt.CompoundTag; import net.minecraft.server.level.ServerPlayer;
public final class UrbanStoryStorage {
 public static final Map<ServerPlayer,UrbanStoryProgress> states = new IdentityHashMap<>();
 public static Optional<UrbanStoryProgress> read(ServerPlayer player) { return Optional.ofNullable(states.get(player)); }
 public static CompoundTag encode(UrbanStoryProgress state) { var tag=new CompoundTag(); tag.putString("chapter",state.chapter().name()); return tag; }
}

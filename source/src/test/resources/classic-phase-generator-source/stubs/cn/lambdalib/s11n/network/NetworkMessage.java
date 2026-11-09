package cn.lambdalib.s11n.network;
import java.util.ArrayList; import java.util.List; import cpw.mods.fml.relauncher.Side;
public final class NetworkMessage {
 public record Sent(String channel, Object[] arguments) {}
 public static final List<Sent> sent = new ArrayList<>();
 public static void sendToAllAround(Object target, Object tile, String channel, Object... arguments) { sent.add(new Sent(channel, arguments)); }
 public @interface Listener { String channel(); Side side(); }
}

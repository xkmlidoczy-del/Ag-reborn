package cn.academy.port.client;
import net.minecraft.client.Minecraft;
/** Controlled client input boundary, with explicit notifications and real Effects cancellation. */
public final class AcademyClient {
 public static long epoch=71,session=1,input=3;public static boolean control=true;
 public static final java.util.List<String> notifications=new java.util.ArrayList<>();
 public static long inputSessionEpoch(){return session;}
 public static boolean sameInputSessionEpoch(long value){return value==session;}
 public static boolean singleOwnerEpochMatches(long value){return value==epoch;}
 public static boolean trajectoryReturnInputMatches(long value,long e){return value==input&&e==epoch;}
 public static boolean trajectoryReturnControlValid(){var m=Minecraft.getInstance();return control&&m.active&&m.screen==null&&m.getCameraEntity()==m.player&&!m.player.spectator;}
 public static void trajectoryReturnEndedInput(long value,long e){notifications.add("ended:"+value+":"+e);}
 public static void trajectoryReturnCancelInput(){notifications.add("cancel:"+input+":"+epoch);TrajectoryReturnEffects.abortLocal(input,epoch);}
 public static void reset(){epoch=71;session=1;input=3;control=true;notifications.clear();}
}

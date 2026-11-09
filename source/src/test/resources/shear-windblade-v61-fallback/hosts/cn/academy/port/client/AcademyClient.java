package cn.academy.port.client;
import net.minecraft.client.Minecraft;
/** Explicit transport/control boundary. Receipt validation and controller state remain real production. */
public final class AcademyClient {
 public static long epoch=71,session=1,input=3;public static int ended,cancelled;public static boolean control=true;
 public static long inputSessionEpoch(){return session;}public static boolean sameInputSessionEpoch(long value){return value==session;}
 public static boolean singleOwnerEpochMatches(long value){return value==epoch;}
 public static boolean shearWindbladeInputMatches(long value,long e){return value==input&&e==epoch;}
 public static boolean shearWindbladeControlValid(){var mc=Minecraft.getInstance();return control&&mc.player!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()&&mc.screen==null&&mc.active&&mc.getCameraEntity()==mc.player;}
 public static void shearWindbladeCancelInput(){cancelled++;ShearWindbladeEffects.abortLocal(input,epoch);}
 public static void shearWindbladeEndedInput(long value,long e){if(!shearWindbladeInputMatches(value,e))throw new AssertionError("unexpected ended input");ended++;}
 public static void reset(){epoch=71;session=1;input=3;ended=cancelled=0;control=true;}
}

package cn.academy.port.client;
/** Controlled input/eligibility boundary, not a receipt or renderer implementation. */
public final class AcademyClient {
 public static long epoch=71,session=1,input=3;public static int ended,accepted;public static final java.util.List<String> notifications=new java.util.ArrayList<>();public static State state=new State();
 public static final class State {public boolean activated=true,overloadFine=true,interfering;public String category="meltdowner";public int level=5;public Presets presets=new Presets();public boolean learned(String id){return true;}public boolean consumptionInProgress(){return false;}}
 public static final class Presets{public boolean currentContains(String id){return true;}}
 public static long inputSessionEpoch(){return session;}public static boolean sameInputSessionEpoch(long value){return value==session;}
 public static boolean singleOwnerEpochMatches(long value){return value==epoch;}
 public static boolean multicoreCanopyInputMatches(long value,long e){return value==input&&e==epoch;}
 public static void multicoreCanopyEndedInput(long value,long e){notifications.add("ended:"+value+":"+e);ended++;}
 public static void multicoreCanopyAcceptedInput(long value,long e){notifications.add("accepted:"+value+":"+e);accepted++;}
 public static void reset(){epoch=71;session=1;input=3;ended=accepted=0;notifications.clear();state=new State();}
}

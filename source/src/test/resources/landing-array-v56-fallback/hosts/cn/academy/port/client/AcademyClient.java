package cn.academy.port.client;
/** Explicit input/session eligibility host; never a gameplay/input-state proof. */
public final class AcademyClient {
 public static long epoch=1,input=1,session=1;public static boolean control=true;public static int accepted,ended,rejected,cancelled;
 public static long inputSessionEpoch(){return session;}public static boolean sameInputSessionEpoch(long s){return s==session;}
 public static boolean singleOwnerEpochMatches(long e){return e==epoch;}
 public static boolean multidimensionalLandingArrayReceiptMatches(long i,long e,int p){return e==epoch&&i<=input;}
 public static boolean multidimensionalLandingArrayInputMatches(long i,long e){return e==epoch&&i<=input;}
 public static boolean multidimensionalLandingArrayControlValid(){return control;}
 public static void multidimensionalLandingArrayCancelInput(){cancelled++;}
 public static void multidimensionalLandingArrayRejectedInput(long i,long e){rejected++;}
 public static void multidimensionalLandingArrayEndedInput(long i,long e){ended++;}
 public static void multidimensionalLandingArrayAcceptedInput(long i,long e,int r,int p,boolean f){accepted++;}
 public static void reset(){epoch=1;input=100;session=1;control=true;accepted=ended=rejected=cancelled=0;}
}

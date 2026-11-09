package cn.academy.port.highpower.core;

import java.math.BigDecimal;

public final class BrakingTouchRulesRegressionTest {
 private static int samples;
 public static void main(String[] args){
  float previous=0;
  for(int i=0;i<=10000;i++){
   float d=i/100f,r=bounded(d);
   check(r>=previous,"ordinary-distance monotonicity");previous=r;
  }
  // Exercise every subnormal rounding parity near zero, including an unrepresentable half-minimum budget.
  for(int bits=1;bits<=256;bits++)bounded(Float.intBitsToFloat(bits));
  for(float d:new float[]{Float.MIN_VALUE,Float.MIN_NORMAL,Math.nextDown(Float.MIN_NORMAL),
      Math.nextUp(Float.MIN_NORMAL),1e-20f,.0001f,.5f,Math.nextDown(3f),3f,Math.nextUp(3f),
      Math.nextDown(6f),6f,Math.nextUp(6f),Math.nextDown(0x1p24f),0x1p24f,Math.nextUp(0x1p24f),
      16_777_220f,16_777_224f,0x1p25f,Math.nextUp(0x1p25f),0x1p26f,
      Math.nextDown(Float.MAX_VALUE),Float.MAX_VALUE})bounded(d);
  // Sampling all finite exponent bands and mantissa boundaries finds failures outside ordinary play distances.
  for(int exponent=0;exponent<255;exponent++)for(int mantissa:new int[]{0,1,2,3,0x3fffff,0x400000,0x7ffffd,0x7ffffe,0x7fffff}){
   float d=Float.intBitsToFloat((exponent<<23)|mantissa),r=bounded(d);
   if(d>0)check(BrakingTouchRules.reducedDistance(Math.nextDown(d))<=r,"float-boundary monotonicity");
  }
  check(BrakingTouchRules.reducedDistance(Float.MIN_VALUE)==Float.MIN_VALUE,"unrepresentable subnormal half reduction is a no-op");
  check(BrakingTouchRules.reducedDistance(16_777_224f)==16_777_222f,"four-metre rounding counterexample becomes conservative two-metre reduction");
  check(BrakingTouchRules.reducedDistance(Float.MAX_VALUE)==Float.MAX_VALUE,"no representable bounded reduction at maximum finite float");
  for(float d:new float[]{-Float.MIN_VALUE,-1,-Float.MAX_VALUE,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY,
      Float.NaN,Float.intBitsToFloat(0x7fc01234),0f,-0f}){
   check(Float.floatToRawIntBits(BrakingTouchRules.reducedDistance(d))==Float.floatToRawIntBits(d),"invalid and signed-zero passthrough");
  }
  check(BrakingTouchRules.reducedDistance(6)==3&&BrakingTouchRules.reducedDistance(100)==97,"ordinary exact anchors retained");
  double cp=Double.POSITIVE_INFINITY;
  for(int i=0;i<=400;i++){
   BigDecimal q=new BigDecimal("1").add(new BigDecimal(i).movePointLeft(2));double c=BrakingTouchRules.cpCost(q);
   check(c>0&&c<=cp+1e-12,"positive monotone CP growth");check(BrakingTouchRules.cooldownTicks(q)>=4,"cooldown floor");cp=c;samples++;
  }
  check(BrakingTouchRules.clock(0)&&!BrakingTouchRules.clock(-1)&&!BrakingTouchRules.clock(Long.MAX_VALUE),"clock bounds");
  System.out.println("BrakingTouch rules: "+samples+" bounded distance/growth samples plus explicit invalid/anchor controls passed");
 }
 private static float bounded(float d){
  float r=BrakingTouchRules.reducedDistance(d);double reduction=(double)d-(double)r,budget=Math.min(3.0,(double)d*.5);
  check(Float.isFinite(r)&&r>=0&&r<=d,"finite ordered output for "+d);
  check(reduction>=0&&reduction<=budget,"exact reduction budget for "+d+": "+reduction+" <= "+budget);
  if(r>0)check((double)d-(double)Math.nextDown(r)>budget,"largest representable permitted reduction for "+d);
  samples++;return r;
 }
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

package cn.academy.port.client;
import cn.academy.port.skill.*;
public final class VectorStarterVisualRegressionTest {
 private static int checks;private static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 private static void near(double a,double b,String why){check(Math.abs(a-b)<1E-6,why);}
 public static void main(String[] args)throws Exception{
  var tokens=new ClassicVectorStarterTimeline.Tokens();check(tokens.start("vec_accel",1,1),"start");check(!tokens.start("vec_accel",1,1),"dupstart");check(tokens.end("vec_accel",1,1),"terminal");check(!tokens.start("vec_accel",1,1),"lateackneverrevives");check(!tokens.end("vec_accel",1,1),"duplicateperform audio denied");check(tokens.start("vec_accel",1,2),"newcontext");check(!tokens.end("vec_accel",1,1),"oldend doesnot killnew");check(tokens.live("vec_accel",1,2),"newtoken remainslive");check(tokens.start("vec_deviation",1,1),"independentothercontext");tokens.clear();check(tokens.start("vec_accel",1,1),"worldreplacement reset");
  for(int i=0;i<20;i++){var path=ClassicVectorStarterTimeline.parabola(i*18,i%5*15-30,i);check(path.size()==100,"source100points");for(var p:path)check(Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z()),"finitecurvevertices");var speed=ClassicVectorStarterTimeline.direction(i*18,i%5*15-40).scale(VecAccelSession.speed(i)*.98*.02);near(path.get(1).x()-path.get(0).x(),speed.x(),"firststep dragbeforemoveX");near(path.get(1).y()-path.get(0).y(),speed.y(),"firststep gravityafterintegrationY");near(path.get(1).z()-path.get(0).z(),speed.z(),"firststepZ");}
  check(ClassicVectorStarterTimeline.parabolaAlpha(34)<0,"originalnegativealphatail retained");near(ClassicVectorStarterTimeline.rippleX(0,800),200,"shadercenterhalfx");near(ClassicVectorStarterTimeline.rippleY(0,600),450,"shaderliteralY");near(ClassicVectorStarterTimeline.rippleX(800,800),600,"rightcenterhalf");near(ClassicVectorStarterTimeline.rippleY(600,600),150,"invertedY");
  near(ClassicVectorStarterTimeline.rippleAlpha(.4F,2),1,"ripple20percent");near(ClassicVectorStarterTimeline.rippleAlpha(1,2),1,"ripple50percent");near(ClassicVectorStarterTimeline.rippleAlpha(2,2),0,"rippleend");near(ClassicVectorStarterTimeline.rippleSize(100,1),120,"realripplegrowth20");near(ClassicVectorStarterTimeline.waveAlpha(0,10,0),0,"wavebirth");near(ClassicVectorStarterTimeline.waveDepth(10,.3),.55,"sourcezdrift");
  check(ClassicVectorStarterTimeline.ACCEL_VOLUME==.35F&&ClassicVectorStarterTimeline.DEVIATION_VOLUME==.5F,"sourcesoundvolumes");
  String source=VectorSourceFixtures.source("academy/ParabolaEffect.scala");check(source.contains("rotateAroundY(90)")&&source.contains("speed *= 0.98")&&source.contains("speed.yCoord -= 1.9 * dt"),"canonicalparabolaphysics");
  System.out.println("PASS "+checks+" source mesh/timeline and cosmetic transport checks; GPU/audio execution pending");
 }
}

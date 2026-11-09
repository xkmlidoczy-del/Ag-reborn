package cn.academy.port.fleet.client;

import java.util.ArrayDeque;
import java.util.List;

/** Logical draw and recoil clock. Network stack replacements are not equip events. */
public final class G03Playback {
 private double time=Double.NaN,drawStart;
 private long observedShot;
 private float run,walk;
 private final ArrayDeque<Long> shots=new ArrayDeque<>();
 public void update(double requestedTime,long lastShot,float targetRun,float targetWalk,double shotDuration) {
  if(!Double.isFinite(requestedTime))throw new IllegalArgumentException("finite animation clock required");
  if(Double.isNaN(time)){time=drawStart=requestedTime;run=clamp(targetRun);walk=clamp(targetWalk);}
  double next=Math.max(time,requestedTime),dt=next-time;
  // Exponential, independent of render FPS. Repeated render passes at one time are idempotent.
  float locomotion=(float)(1-Math.exp(-dt/2.2));
  run+=(clamp(targetRun)-run)*locomotion;walk+=(clamp(targetWalk)-walk)*locomotion;time=next;
  // A server timestamp can be slightly ahead of the client. Keep it pending instead of losing it.
  if(lastShot>observedShot){observedShot=lastShot;if(time-lastShot<shotDuration*20)shots.addLast(lastShot);}
  while(!shots.isEmpty()&&time-shots.peekFirst()>=shotDuration*20)shots.removeFirst();
 }
 private static float clamp(float f){return Float.isFinite(f)?Math.max(0,Math.min(1,f)):0;}
 public double time(){return time;}
 public double drawSeconds(){return (time-drawStart)/20;}
 public float run(){return run;}
 public float walk(){return walk;}
 public List<Long> shots(){return List.copyOf(shots);}
 public static float ease(double t){float x=(float)Math.max(0,Math.min(1,t));return x*x*(3-2*x);}
}

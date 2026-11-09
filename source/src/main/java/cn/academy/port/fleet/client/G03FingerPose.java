package cn.academy.port.fleet.client;

import org.joml.Vector3f;

/** Constant phalanx lengths also during render interpolation and interrupted-action blending. */
public final class G03FingerPose {
 public static final String[] DIGITS={"index","middle","ring","little","thumb"};
 public static final float[] RADII={.0057f,.006f,.0058f,.0052f,.0065f};
 public static final float[][] LENGTHS={{.032f,.031f,.018f},{.030f,.029f,.019f},{.029f,.028f,.018f},{.027f,.026f,.017f},{.027f,.025f,.018f}};
 private G03FingerPose(){}
 public static Vector3f[] points(G03Rig rig,G03Rig.Frame f,String hand,int digit){
  Vector3f[] raw=new Vector3f[4],out=new Vector3f[4];
  for(int j=0;j<4;j++)raw[j]=rig.point(f,hand+"_"+DIGITS[digit]+"_"+j);
  out[0]=new Vector3f(raw[0]);
  for(int j=0;j<3;j++){
   Vector3f direction=new Vector3f(raw[j+1]).sub(raw[j]);
   if(direction.lengthSquared()<1e-12f)direction.set(0,0,1);else direction.normalize();
   out[j+1]=new Vector3f(direction).mul(LENGTHS[digit][j]).add(out[j]);
  }
  return out;
 }
}

/* Original LambdaLib1.2.3 toDirVector method unchanged; MIT, Copyright Lambda Innovation.
 * Test scaffolding supplies the legacy table and mutable Vec3 substrate. */
package cn.academy.port.skill.vectororacle;
import java.util.*;
public final class ParabolaOracle {
 private static final class MathHelper {
  static final float[] TABLE=new float[65536];static{for(int i=0;i<TABLE.length;i++)TABLE[i]=(float)Math.sin(i*Math.PI*2/65536);}
  static float sin(float a){return TABLE[(int)(a*10430.378F)&65535];}static float cos(float a){return TABLE[(int)(a*10430.378F+16384F)&65535];}
 }
 public static final class Vec3{public double xCoord,yCoord,zCoord;private Vec3(double x,double y,double z){xCoord=x;yCoord=y;zCoord=z;}static Vec3 createVectorHelper(double x,double y,double z){return new Vec3(x,y,z);}Vec3 copy(){return createVectorHelper(xCoord,yCoord,zCoord);}void scale(double a){xCoord*=a;yCoord*=a;zCoord*=a;}void add(Vec3 a){xCoord+=a.xCoord;yCoord+=a.yCoord;zCoord+=a.zCoord;}void rotateAroundY(float radians){float c=MathHelper.cos(radians),s=MathHelper.sin(radians);double x=xCoord*c+zCoord*s,z=zCoord*c-xCoord*s;xCoord=x;zCoord=z;}void normalize(){double n=Math.sqrt(xCoord*xCoord+yCoord*yCoord+zCoord*zCoord);if(n<1E-4){xCoord=yCoord=zCoord=0;}else scale(1/n);}}
    public static Vec3 toDirVector(float yaw, float pitch) {
        float f1 = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = MathHelper.sin(-yaw * 0.017453292F - (float)Math.PI);
        float f3 = -MathHelper.cos(-pitch * 0.017453292F);
        float f4 = MathHelper.sin(-pitch * 0.017453292F);
        return Vec3.createVectorHelper((double)(f2 * f3), (double)f4, (double)(f1 * f3));
    }
 public static List<Vec3> trajectory(float yaw,float pitch,int ticks){
  Vec3 lookFix=toDirVector(yaw,pitch);Vec3 lookRot=lookFix.copy();lookRot.yCoord=0;lookRot.rotateAroundY(90);lookRot.normalize();lookRot.scale(-0.08);lookRot.yCoord=-0.04;
  Vec3 pos=lookRot.copy(),look=lookFix.copy();look.scale(-0.12);pos.add(look);
  float yawRad=MathUtils.toRadians(yaw),pitchRad=MathUtils.toRadians(pitch-10);
  Vec3 speed=Vec3.createVectorHelper(-MathHelper.sin(yawRad)*MathHelper.cos(pitchRad),-MathHelper.sin(pitchRad),MathHelper.cos(yawRad)*MathHelper.cos(pitchRad));
  speed.scale(Math.sin(MathUtils.lerp(0.4,1,MathUtils.clampd(0,1,ticks/20.0)))*2.5);
  double dt=0.02;var vertices=new ArrayList<Vec3>();for(int i=0;i<100;i++){vertices.add(pos.copy());speed.scale(0.98);Vec3 step=speed.copy();step.scale(dt);pos.add(step);speed.yCoord-=1.9*dt;}return vertices;
 }
}

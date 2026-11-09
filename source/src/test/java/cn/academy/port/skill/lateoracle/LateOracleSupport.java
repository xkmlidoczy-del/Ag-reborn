package cn.academy.port.skill.lateoracle;
/** Minimal original-oracle host shapes, with independent vanilla1.7 table trig. No production imports. */
public final class LateOracleSupport {
 private LateOracleSupport(){}
 public enum Side{CLIENT,SERVER}public @interface SideOnly{Side value();}
 public static final class Vec3{public final double xCoord,yCoord,zCoord;private Vec3(double x,double y,double z){xCoord=x;yCoord=y;zCoord=z;}public static Vec3 createVectorHelper(double x,double y,double z){return new Vec3(x,y,z);}}
 public static class World{public boolean isRemote;}
 public static class Entity{public double posX,posY,posZ,motionX,motionY,motionZ;public float rotationYaw,rotationPitch,prevRotationYaw,prevRotationPitch;public World worldObj=new World();public float getEyeHeight(){return 1.62F;}public float getRotationYawHead(){return rotationYaw;}public void setPosition(double x,double y,double z){posX=x;posY=y;posZ=z;}}
 public static class EntityLivingBase extends Entity{}public static class EntityPlayer extends EntityLivingBase{}
 public static class Minecraft{private static final Minecraft INSTANCE=new Minecraft();public EntityPlayer thePlayer;public static Minecraft getMinecraft(){return INSTANCE;}}
 public static final class MathHelper{private static final float[] TABLE=new float[65536];static{for(int i=0;i<65536;i++)TABLE[i]=(float)Math.sin(i*2*Math.PI/65536);}public static float sin(float x){return TABLE[(int)(x*10430.378F)&65535];}public static float cos(float x){return TABLE[(int)(x*10430.378F+16384F)&65535];}}
 public static final class Objects{public static Helper toStringHelper(Object o){return new Helper();}public static final class Helper{public Helper add(String k,Object v){return this;}public String toString(){return "oracle";}}}
}

/* Minecraft1.7 lookup-table Float trig used by LambdaLib Motion3D. See NOTICE. */
package cn.academy.port.clientless;
/** Common-only; never links any client package. */
public final class ClassicLateTrig {
 private static final float[] SINES=new float[65536];static{for(int i=0;i<SINES.length;i++)SINES[i]=(float)Math.sin(i*Math.PI*2/65536);}
 private ClassicLateTrig(){}public static float sin(float f){return SINES[(int)(f*10430.378F)&65535];}public static float cos(float f){return SINES[(int)(f*10430.378F+16384F)&65535];}
 public static double[] direction(float yaw,float pitch){float a=yaw/180F*(float)Math.PI,b=pitch/180F*(float)Math.PI;return new double[]{-sin(a)*cos(b),-sin(b),cos(a)*cos(b)};}
}

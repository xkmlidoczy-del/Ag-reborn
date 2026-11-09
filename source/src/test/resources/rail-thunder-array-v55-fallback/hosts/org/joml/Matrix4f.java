package org.joml;
import java.util.*;
public class Matrix4f {
 public final float[] m=new float[16]; public Matrix4f(){identity();} public Matrix4f(Matrix4f o){set(o);}
 public Matrix4f identity(){Arrays.fill(m,0);m[0]=m[5]=m[10]=m[15]=1;return this;}
 public Matrix4f set(Matrix4f o){System.arraycopy(o.m,0,m,0,16);return this;}
 public Matrix4f translation(float x,float y,float z){identity();m[12]=x;m[13]=y;m[14]=z;return this;}
 public String toString(){StringBuilder b=new StringBuilder();for(float v:m)b.append(proof.Trace.f(v)).append(',');return b.toString();}
}

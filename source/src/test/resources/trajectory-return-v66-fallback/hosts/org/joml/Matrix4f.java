package org.joml;
import java.util.*;
/** Deterministic column-major float affine/projective host; no JOML binary claim. */
public class Matrix4f {
 public final float[] m=new float[16]; public Matrix4f(){identity();} public Matrix4f(Matrix4f o){set(o);}
 public Matrix4f identity(){Arrays.fill(m,0);m[0]=m[5]=m[10]=m[15]=1;return this;}
 public Matrix4f set(Matrix4f o){System.arraycopy(o.m,0,m,0,16);return this;}
 public Matrix4f translation(float x,float y,float z){identity();m[12]=x;m[13]=y;m[14]=z;return this;}
 public Matrix4f mul(Matrix4f b){float[] a=m.clone();for(int c=0;c<4;c++)for(int r=0;r<4;r++)m[c*4+r]=a[r]*b.m[c*4]+a[4+r]*b.m[c*4+1]+a[8+r]*b.m[c*4+2]+a[12+r]*b.m[c*4+3];return this;}
 public Matrix4f scale(float x,float y,float z){Matrix4f s=new Matrix4f();s.m[0]=x;s.m[5]=y;s.m[10]=z;return mul(s);}
 public Matrix4f translate(float x,float y,float z){return mul(new Matrix4f().translation(x,y,z));}
 public Matrix4f rotate(Quaternionfc q){float x=q.x(),y=q.y(),z=q.z(),w=q.w();Matrix4f r=new Matrix4f();r.m[0]=1-2*y*y-2*z*z;r.m[1]=2*x*y+2*z*w;r.m[2]=2*x*z-2*y*w;r.m[4]=2*x*y-2*z*w;r.m[5]=1-2*x*x-2*z*z;r.m[6]=2*y*z+2*x*w;r.m[8]=2*x*z+2*y*w;r.m[9]=2*y*z-2*x*w;r.m[10]=1-2*x*x-2*y*y;return mul(r);}
 public float m11(){return m[5];}
 public Vector4f transform(Vector4f v){return new Vector4f(m[0]*v.x()+m[4]*v.y()+m[8]*v.z()+m[12]*v.w(),m[1]*v.x()+m[5]*v.y()+m[9]*v.z()+m[13]*v.w(),m[2]*v.x()+m[6]*v.y()+m[10]*v.z()+m[14]*v.w(),m[3]*v.x()+m[7]*v.y()+m[11]*v.z()+m[15]*v.w());}
 public String toString(){StringBuilder b=new StringBuilder();for(float v:m)b.append(proof.Trace.f(v)).append(',');return b.toString();}
}

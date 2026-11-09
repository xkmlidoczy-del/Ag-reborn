package com.mojang.blaze3d.vertex;
import org.joml.Matrix4f;import proof.Trace;
/** Record raw inputs and transformed coordinates, UV, RGBA and normal in call order. */
public final class VertexConsumer {
 private String xyz,uv;
 public VertexConsumer addVertex(Matrix4f m,float x,float y,float z){uv="none";xyz=Trace.f(x)+","+Trace.f(y)+","+Trace.f(z)+"->"+Trace.f(m.m[0]*x+m.m[4]*y+m.m[8]*z+m.m[12])+","+Trace.f(m.m[1]*x+m.m[5]*y+m.m[9]*z+m.m[13])+","+Trace.f(m.m[2]*x+m.m[6]*y+m.m[10]*z+m.m[14]);return this;}
 public VertexConsumer setUv(float u,float v){uv=Trace.f(u)+","+Trace.f(v);return this;}
 public VertexConsumer setColor(int r,int g,int b,int a){Trace.add("v:"+xyz+":"+uv+":"+r+","+g+","+b+","+a);Trace.vertices++;return this;}
 public VertexConsumer setNormal(float x,float y,float z){Trace.add("normal:"+Trace.f(x)+","+Trace.f(y)+","+Trace.f(z));return this;}
}

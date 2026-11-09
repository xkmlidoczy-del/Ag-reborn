package com.mojang.blaze3d.systems;
import org.joml.Matrix4f; import java.util.*; import proof.Trace;
public final class RenderSystem {
 public static float[] color={.25f,.5f,.75f,.875f};public static float[] getShaderColor(){return color;}public static void setShaderColor(float r,float g,float b,float a){color=new float[]{r,g,b,a};proof.Trace.add("shader-color:"+proof.Trace.f(r)+","+proof.Trace.f(g)+","+proof.Trace.f(b)+","+proof.Trace.f(a));}
 public static final Stack STACK=new Stack(); public static int applies;
 public static Stack getModelViewStack(){return STACK;}
 public static void applyModelViewMatrix(){applies++;Trace.add("apply:"+STACK.value);}
 public static final class Stack {public Matrix4f value=new Matrix4f();public final Deque<Matrix4f> frames=new ArrayDeque<>();
 public void pushMatrix(){frames.push(new Matrix4f(value));Trace.add("push:"+frames.size());}
 public void popMatrix(){value=frames.pop();Trace.add("pop:"+frames.size());}
 public void set(Matrix4f m){value.set(m);Trace.add("model:"+value);}}
 public static void enableBlend(){Trace.add("blend:on");} public static void disableBlend(){Trace.add("blend:off");}
 public static void defaultBlendFunc(){Trace.add("blend:default");}
 public static void blendFuncSeparate(Object a,Object b,Object c,Object d){Trace.add("blend:"+a+":"+b+":"+c+":"+d);}
 public static void depthMask(boolean b){Trace.add("depthMask:"+b);} public static void disableCull(){Trace.add("cull:off");}public static void enableCull(){Trace.add("cull:on");}
}

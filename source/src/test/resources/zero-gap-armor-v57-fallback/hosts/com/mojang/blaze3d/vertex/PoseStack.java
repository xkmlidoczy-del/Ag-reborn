package com.mojang.blaze3d.vertex;import org.joml.Matrix4f;import java.util.*;import proof.Trace;
public class PoseStack {public Matrix4f matrix=new Matrix4f();public final Deque<Matrix4f> frames=new ArrayDeque<>();public Pose last(){return new Pose(matrix);}public record Pose(Matrix4f pose){}
 public void pushPose(){frames.push(new Matrix4f(matrix));Trace.add("pose-push:"+frames.size());}public void popPose(){matrix=frames.pop();Trace.add("pose-pop:"+frames.size());}
 public void translate(double x,double y,double z){matrix.mul(new Matrix4f().translation((float)x,(float)y,(float)z));Trace.add("pose-translate:"+matrix);}
 public void scale(float x,float y,float z){matrix.scale(x,y,z);Trace.add("pose-scale:"+matrix);}
 public void mulPose(Matrix4f rotation){matrix.mul(rotation);Trace.add("pose-rotate:"+matrix);}
}

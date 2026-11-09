package cn.academy.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** Actual native buffer emitter, separated from BEWLR's Minecraft static bootstrap for headless ownership tests. */
final class ClassicPortableMeshEmission {
    static void emit(ClassicDeveloperObj.Mesh mesh,PoseStack.Pose pose,VertexConsumer vertices,int light,int overlay){
        for(var triangle:mesh.triangles()){
            vertex(triangle.a(),pose,vertices,light,overlay);vertex(triangle.b(),pose,vertices,light,overlay);
            vertex(triangle.c(),pose,vertices,light,overlay);vertex(triangle.c(),pose,vertices,light,overlay);
        }
    }
    private static void vertex(ClassicDeveloperObj.Vertex v,PoseStack.Pose pose,VertexConsumer output,int light,int overlay){
        output.addVertex(pose,v.x(),v.y(),v.z()).setColor(255,255,255,255).setUv(v.u(),v.v()).setLight(light).setOverlay(overlay).setNormal(pose,v.nx(),v.ny(),v.nz());
    }
    private ClassicPortableMeshEmission(){}
}

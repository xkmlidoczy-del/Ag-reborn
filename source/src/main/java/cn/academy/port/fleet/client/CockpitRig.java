package cn.academy.port.fleet.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Simplified 3-D dashboard behind the steering wheel; all dimensions are in camera space. */
public final class CockpitRig {
    private CockpitRig(){}
    public static void draw(PoseStack p,MultiBufferSource buffers,int light){
        var o=buffers.getBuffer(RenderType.entityCutoutNoCull(ResourceLocation.withDefaultNamespace("textures/misc/white.png")));
        var pose=p.last();
        // Upper dash recedes under the windshield; a lower fascia gives it visible thickness.
        quad(o,pose,-1.15f,-.48f,-.78f, 1.15f,-.48f,-.78f, 1.15f,-.28f,-1.35f, -1.15f,-.28f,-1.35f, 35,43,47,light);
        quad(o,pose,-1.15f,-.82f,-.78f, 1.15f,-.82f,-.78f, 1.15f,-.48f,-.78f, -1.15f,-.48f,-.78f, 24,30,34,light);
        // Raised instrument binnacle and central screen, deliberately below the horizon.
        quad(o,pose,-.34f,-.38f,-1.04f, .34f,-.38f,-1.04f, .28f,-.25f,-1.28f, -.28f,-.25f,-1.28f, 16,23,27,light);
        quad(o,pose,-.29f,-.365f,-1.025f, .29f,-.365f,-1.025f, .24f,-.275f,-1.24f, -.24f,-.275f,-1.24f, 44,76,85,light);
        quad(o,pose,-.23f,-.35f,-1.02f, .23f,-.35f,-1.02f, .21f,-.34f,-1.04f, -.21f,-.34f,-1.04f, 64,173,187,light);
        for(float side:new float[]{-1,1}){
            quad(o,pose,side*.56f,-.40f,-.95f,side*.90f,-.40f,-.95f,side*.90f,-.36f,-1.05f,side*.56f,-.36f,-1.05f, 11,17,21,light);
        }
    }
    private static void quad(VertexConsumer o,PoseStack.Pose p,float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,int r,int g,int b,int light){
        vertex(o,p,ax,ay,az,r,g,b,light);vertex(o,p,bx,by,bz,r,g,b,light);vertex(o,p,cx,cy,cz,r,g,b,light);vertex(o,p,dx,dy,dz,r,g,b,light);
    }
    private static void vertex(VertexConsumer o,PoseStack.Pose p,float x,float y,float z,int r,int g,int b,int light){
        o.addVertex(p,x,y,z).setColor(r,g,b,255).setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p,0,1,0);
    }
}

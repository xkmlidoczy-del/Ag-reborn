/* Original RenderImagPhaseLiquid exact layer heights, scrolling, opacity and source textures. GPLv3. */
package cn.academy.port.client;
import cn.academy.port.fusion.ClassicPhaseBlockEntity;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
public final class ClassicPhaseRenderer implements BlockEntityRenderer<ClassicPhaseBlockEntity> {
    public ClassicPhaseRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(ClassicPhaseBlockEntity tile,float partialTick,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        ClassicPhaseLateEffects.queue(tile);
    }
    static void drawDeferred(ClassicPhaseBlockEntity tile,PoseStack poses,MultiBufferSource buffers){
        if(tile.getLevel()==null||Minecraft.getInstance().player==null)return;
        double distance=Minecraft.getInstance().player.distanceToSqr(tile.getBlockPos().getX()+.5,tile.getBlockPos().getY()+.5,tile.getBlockPos().getZ()+.5);float alpha=(float)(1/(1+.2*Math.sqrt(distance)));if(alpha<.1f)return;
        double surface=!tile.getLevel().getFluidState(tile.getBlockPos().above()).isEmpty()?1:tile.getBlockState().getFluidState().getOwnHeight();double height=1.2*Math.sqrt(surface);long time=ClassicWirelessClock.millis();
        layer(0,-.3*height,.3,.2,alpha,time,poses,buffers);layer(1,.35*height,.3,.05,alpha,time,poses,buffers);if(height>.5)layer(2,.7*height,.1,.25,alpha,time,poses,buffers);
    }
    private static void layer(int layer,double height,double vx,double vz,float alpha,long time,PoseStack poses,MultiBufferSource buffers){
        var texture=ResourceLocation.fromNamespaceAndPath("academy","textures/effects/imag_proj_liquid/"+layer+".png");var out=buffers.getBuffer(ClassicPhaseRenderTypes.layer(texture));float du=(float)((time*.001*vx)%1),dv=(float)((time*.001*vz)%1);var pose=poses.last();
        out.addVertex(pose,0,(float)height,0).setColor(1f,1f,1f,alpha).setUv(du,dv);out.addVertex(pose,1,(float)height,0).setColor(1f,1f,1f,alpha).setUv(du+.7f,dv);out.addVertex(pose,1,(float)height,1).setColor(1f,1f,1f,alpha).setUv(du+.7f,dv+.7f);out.addVertex(pose,0,(float)height,1).setColor(1f,1f,1f,alpha).setUv(du,dv+.7f);
    }
    @Override public AABB getRenderBoundingBox(ClassicPhaseBlockEntity tile){return new AABB(tile.getBlockPos()).inflate(0,.5,0);}
}

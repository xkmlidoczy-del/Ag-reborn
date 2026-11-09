package cn.academy.port.fleet.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import cn.academy.port.fleet.InteractionLab;
import cn.academy.port.fleet.LabVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class LabVehicleRenderer extends EntityRenderer<LabVehicle> {
    public LabVehicleRenderer(EntityRendererProvider.Context ctx){super(ctx);shadowRadius=0.9f;}
    @Override public ResourceLocation getTextureLocation(LabVehicle v){return ResourceLocation.fromNamespaceAndPath("academy","textures/models/"+InteractionLab.CAR_IDS[v.variant()]+".png");}
    @Override public void render(LabVehicle v,float yaw,float partial,PoseStack ps,MultiBufferSource buffers,int light){
        Minecraft mc=Minecraft.getInstance();
        ps.pushPose();ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(-v.renderPitch(partial)));ps.mulPose(Axis.ZP.rotationDegrees(v.renderRoll(partial)));
        VehicleModel.draw(v,partial,ps,buffers,light);
        ps.popPose();super.render(v,yaw,partial,ps,buffers,light);
    }
}

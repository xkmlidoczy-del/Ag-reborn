/* AcademyCraft1.0.7 RenderCatEngine direct textured billboard, GPLv3. */
package cn.academy.port.client;
import cn.academy.port.cat.ClassicCatEngineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import java.util.Map;
import java.util.WeakHashMap;
public final class ClassicCatRenderer implements BlockEntityRenderer<ClassicCatEngineBlockEntity> {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("academy","textures/blocks/cat_engine.png");
    private static final class Rotation {long last;double angle;}
    // Source per-tile intrusive state is kept client-only; weak keys release removed/replaced native tiles.
    private final Map<ClassicCatEngineBlockEntity,Rotation> rotations=new WeakHashMap<>();
    public ClassicCatRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(ClassicCatEngineBlockEntity tile,float partialTick,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        long time=ClassicWirelessClock.millis();var rotation=rotations.computeIfAbsent(tile,t->new Rotation());rotation.angle=ClassicCatVisual.rotation(rotation.angle,rotation.last,time,tile.generation());rotation.last=time;
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();double x=tile.getBlockPos().getX()-camera.x,z=tile.getBlockPos().getZ()-camera.z;
        pose.pushPose();try{
            pose.translate(.5,ClassicCatVisual.bob(time),.5);pose.mulPose(Axis.YP.rotationDegrees((float)ClassicCatVisual.yaw(x,z)));pose.translate(0,.5,0);pose.mulPose(Axis.XP.rotationDegrees((float)rotation.angle));pose.translate(-.5,-.5,0);
            var vertices=buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
            quad(pose.last(),vertices,light,overlay);
        }finally{pose.popPose();}
    }
    static void quad(PoseStack.Pose pose,com.mojang.blaze3d.vertex.VertexConsumer vertices,int light,int overlay){
        vertex(pose,vertices,0,0,0,0,light,overlay);vertex(pose,vertices,1,0,1,0,light,overlay);vertex(pose,vertices,1,1,1,1,light,overlay);vertex(pose,vertices,0,1,0,1,light,overlay);
    }
    private static void vertex(PoseStack.Pose p,com.mojang.blaze3d.vertex.VertexConsumer v,float x,float y,float u,float w,int light,int overlay){v.addVertex(p,x,y,0).setColor(255,255,255,255).setUv(u,w).setOverlay(overlay).setLight(light).setNormal(p,0,0,1);}
    @Override public AABB getRenderBoundingBox(ClassicCatEngineBlockEntity tile){return new AABB(tile.getBlockPos()).inflate(.25);}
}

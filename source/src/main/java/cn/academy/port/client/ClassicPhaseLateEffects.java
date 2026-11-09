/* Classic TileImagPhase.shouldRenderInPass(1) modern late-stage adapter. GPLv3. See NOTICE. */
package cn.academy.port.client;

import cn.academy.port.fusion.ClassicPhaseBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** The native BER pass precedes translucent fluid; the original pass1 must draw after its black base. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class ClassicPhaseLateEffects {
    private static final Set<ClassicPhaseBlockEntity> VISIBLE=new LinkedHashSet<>();
    private static Level world;
    private static MultiBufferSource.BufferSource buffers;
    private ClassicPhaseLateEffects(){}
    static void queue(ClassicPhaseBlockEntity tile){
        if(tile.getLevel()==null||tile.isRemoved())return;
        if(world!=tile.getLevel()){VISIBLE.clear();world=tile.getLevel();}
        // Only tiles actually admitted by native BER visibility/frustum are queued for this frame.
        VISIBLE.add(tile);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(Minecraft.getInstance().level!=world){VISIBLE.clear();world=Minecraft.getInstance().level;}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null||world!=mc.level||mc.player==null){VISIBLE.clear();return;}
        if(VISIBLE.isEmpty())return;
        if(buffers==null)buffers=MultiBufferSource.immediate(new ByteBufferBuilder(8192));
        var poses=event.getPoseStack();var camera=event.getCamera().getPosition();
        var modelView=RenderSystem.getModelViewStack();float[] previous=RenderSystem.getShaderColor().clone();
        modelView.pushMatrix();poses.pushPose();
        try{
            modelView.set(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();RenderSystem.setShaderColor(1,1,1,1);
            for(var tile:VISIBLE){
                if(tile.isRemoved()||tile.getLevel()!=mc.level||mc.level.getBlockEntity(tile.getBlockPos())!=tile)continue;
                var pos=tile.getBlockPos();poses.pushPose();
                try{poses.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z);ClassicPhaseRenderer.drawDeferred(tile,poses,buffers);}
                finally{poses.popPose();}
            }
            buffers.endBatch();
        }finally{
            VISIBLE.clear();poses.popPose();modelView.popMatrix();RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(previous[0],previous[1],previous[2],previous[3]);
        }
    }
}

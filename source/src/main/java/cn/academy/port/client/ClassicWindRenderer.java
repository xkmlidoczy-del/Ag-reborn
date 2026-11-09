/* AcademyCraft1.0.7 RenderWindGenBase/Pillar/Main and LambdaLib pivots. GPLv3/MIT; see NOTICE. */
package cn.academy.port.client;
import cn.academy.port.wind.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
/** An installed fan is always drawn; it only turns while the tower is complete and its clearance plane is free (the generation rule is unchanged). */
public final class ClassicWindRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    public ClassicWindRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(T tile,float partialTick,PoseStack poses,MultiBufferSource buffers,int light,int overlay){var state=tile.getBlockState();if(!(state.getBlock() instanceof ClassicWindBlock block)||state.getValue(ClassicWindBlock.PART)!=0)return;var facing=ClassicWindRules.Facing.valueOf(state.getValue(ClassicWindBlock.FACING).name());String name=block.kind.name().toLowerCase(java.util.Locale.ROOT);var mesh=ClassicWindModels.INSTANCE.mesh(name);if(mesh==null)return;poses.pushPose();try{poses.translate(ClassicWindVisualRules.pivotX(block.kind,facing),0,ClassicWindVisualRules.pivotZ(block.kind,facing));if(block.kind!=ClassicWindRules.Kind.PILLAR)poses.mulPose(Axis.YP.rotationDegrees((float)ClassicWindVisualRules.yaw(facing)));String texture=tile instanceof ClassicWindBaseBlockEntity base&&!base.isComplete()?"base_disabled":name;ClassicDeveloperRenderer.emitMesh(mesh,poses.last(),buffers.getBuffer(RenderType.entityCutout(ClassicWindModels.texture(texture))),light,overlay);
        if(tile instanceof ClassicWindMainBlockEntity main&&main.fanInstalled()){var fan=ClassicWindModels.INSTANCE.mesh("fan");if(fan!=null){long now=ClassicWirelessClock.millis(),elapsed=main.lastFrame==-1?0:now-main.lastFrame;main.lastFrame=now;if(ClassicWindVisualRules.showFan(main.fanInstalled(),main.noObstacle()))main.lastRotation=ClassicWindVisualRules.rotation(main.lastRotation,main.spinSpeed(),elapsed);poses.pushPose();try{poses.translate(0,.5,.82);poses.mulPose(Axis.ZP.rotationDegrees(-main.lastRotation));ClassicDeveloperRenderer.emitMesh(fan,poses.last(),buffers.getBuffer(RenderType.entityCutout(ClassicWindModels.texture("fan"))),light,overlay);}finally{poses.popPose();}}}
    }finally{poses.popPose();}}
    /** Source main used INFINITE_EXTENT_AABB. Keep offscreen rendering with finite swept-rotor bounds. */
    @Override public boolean shouldRenderOffScreen(T tile){return tile instanceof ClassicWindMainBlockEntity;}
    @Override public AABB getRenderBoundingBox(T tile){if(tile instanceof ClassicWindMainBlockEntity)return new AABB(tile.getBlockPos()).inflate(8);if(tile instanceof ClassicWindBaseBlockEntity)return new AABB(tile.getBlockPos()).expandTowards(0,1,0).inflate(.1);return new AABB(tile.getBlockPos()).inflate(.1);}
}

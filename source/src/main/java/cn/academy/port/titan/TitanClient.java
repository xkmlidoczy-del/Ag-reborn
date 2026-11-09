package cn.academy.port.titan;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.util.Color;

@EventBusSubscriber(modid=TitanBlocks.MODID, bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class TitanClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        TitanBlocks.TYPES.values().forEach(t -> event.registerEntityRenderer(t.get(), Renderer::new));
    }
    public static class Model extends GeoModel<TitanEntity> {
        private ResourceLocation asset(String path) { return ResourceLocation.fromNamespaceAndPath(TitanBlocks.MODID,path); }
        @Override public ResourceLocation getModelResource(TitanEntity entity) { return asset("geo/"+entity.species()+".geo.json"); }
        @Override public ResourceLocation getTextureResource(TitanEntity entity) { return asset("textures/entity/"+entity.species()+".png"); }
        @Override public ResourceLocation getAnimationResource(TitanEntity entity) { return asset("animations/"+entity.species()+".animation.json"); }
        @Override public void setCustomAnimations(TitanEntity entity,long instanceId,AnimationState<TitanEntity> state) {
            super.setCustomAnimations(entity,instanceId,state);
            int code=entity.limbSwingCode();
            if(code==0)return;
            boolean variantB=TitanEntity.limbCodeVariantB(code);
            if(!TitanBoneRig.supported(entity.species(),variantB))return;
            double time=TitanEntity.limbCodeTime(code,state.getPartialTick());
            for(var entry:TitanBoneRig.poses(entity.species(),variantB,time).entrySet()) {
                getBone(entry.getKey()).ifPresent(bone-> {
                    if(Boolean.getBoolean("titancollision.track") && TitanBoneRig.collisionBones(entity.species(),variantB).contains(entry.getKey()))
                        bone.setTrackingMatrices(true);
                    var p=entry.getValue();
                    bone.updatePosition((float)p.position().x,(float)p.position().y,(float)p.position().z);
                    bone.updateRotation((float)Math.toRadians(-p.rotation().x),
                        (float)Math.toRadians(-p.rotation().y),(float)Math.toRadians(p.rotation().z));
                    bone.updateScale((float)p.scale().x,(float)p.scale().y,(float)p.scale().z);
                });
            }
        }
    }
    public static class Renderer extends GeoEntityRenderer<TitanEntity> {
        private static final Function<ResourceLocation,RenderType> FADED=Util.memoize(texture->RenderType.create(
            "titan_camera_fade",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,true,true,
            RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture,false,false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setCullState(RenderStateShard.NO_CULL)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .createCompositeState(true)));
        public Renderer(EntityRendererProvider.Context context) {
            super(context,new Model());
            shadowRadius=6;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
        private float visibility(TitanEntity entity) {
            if(!TitanVisualConfig.NEAR_CAMERA_FADE.get())return 1;
            var camera=Minecraft.getInstance().gameRenderer.getMainCamera();
            if(!camera.isInitialized())return 1;
            var p=camera.getPosition();
            if(Math.abs(p.y-entity.getBoundingBox().getCenter().y)>entity.getBbHeight()*.75+10)return 1;
            double distance=Math.hypot(p.x-entity.getX(),p.z-entity.getZ());
            double near=Math.max(13,entity.getBbWidth()*.65);
            return .20f+.80f*Mth.clamp((float)((distance-near)/18),0,1);
        }
        @Override public boolean shouldRender(TitanEntity entity,Frustum frustum,double cameraX,double cameraY,double cameraZ) {
            if(super.shouldRender(entity,frustum,cameraX,cameraY,cameraZ))return true;
            // Wings, tails, and legs extend well beyond the physics collision box.
            return entity.shouldRender(cameraX,cameraY,cameraZ) && frustum.isVisible(entity.getBoundingBoxForCulling().inflate(24,8,24));
        }
        @Override public RenderType getRenderType(TitanEntity entity,ResourceLocation texture,MultiBufferSource buffers,float partialTick) {
            if(!entity.isInvisible() && visibility(entity)<.99f)return FADED.apply(texture);
            return super.getRenderType(entity,texture,buffers,partialTick);
        }
        @Override public Color getRenderColor(TitanEntity entity,float partialTick,int packedLight) {
            Color color=super.getRenderColor(entity,partialTick,packedLight);
            return Color.ofRGBA(color.getRed(),color.getGreen(),color.getBlue(),Math.round(color.getAlpha()*visibility(entity)));
        }
        @Override public void applyRenderLayers(PoseStack pose,TitanEntity entity,BakedGeoModel model,RenderType type,
            MultiBufferSource buffers,VertexConsumer consumer,float partialTick,int packedLight,int packedOverlay) {
            // The emissive layer otherwise writes opaque depth over the close-camera fade.
            if(visibility(entity)>=.99f)super.applyRenderLayers(pose,entity,model,type,buffers,consumer,partialTick,packedLight,packedOverlay);
        }
    }
}

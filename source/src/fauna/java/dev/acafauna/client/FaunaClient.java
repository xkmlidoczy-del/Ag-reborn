package dev.acafauna.client;
import dev.acafauna.core.FaunaCore;
import dev.acafauna.entity.FaunaAnimal;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
@EventBusSubscriber(modid=FaunaCore.ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class FaunaClient {
    private static final String[] NECK_CHAIN={"neck","neck_1","neck_2","neck_3","neck_4","cc0_Neck2","cc0_Neck3"};
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        FaunaCore.TYPES.values().forEach(type->event.registerEntityRenderer(type.get(),Renderer::new));
    }
    public static final class Model extends GeoModel<FaunaAnimal> {
        @Override public ResourceLocation getModelResource(FaunaAnimal a){return a.profile().resource("geo/"+a.profile().id()+(a.isBaby()&&a.profile().juvenile_model()?"_juvenile":"")+".geo.json");}
        @Override public ResourceLocation getTextureResource(FaunaAnimal a){return a.profile().resource("textures/entity/"+a.profile().id()+(a.isBaby()&&a.profile().juvenile_texture()?"_juvenile":"")+".png");}
        @Override public ResourceLocation getAnimationResource(FaunaAnimal a){return a.profile().resource("animations/"+a.profile().id()+(a.isBaby()&&a.profile().juvenile_model()?"_juvenile":"")+".animation.json");}
        @Override public void setCustomAnimations(FaunaAnimal a,long instanceId,AnimationState<FaunaAnimal> state){
            super.setCustomAnimations(a,instanceId,state);
            if(!a.isAlive()||(a.profile().id().equals("two_toed_sloth")&&a.action()==FaunaAnimal.SPECIAL_A))return;
            EntityModelData data=state.getData(DataTickets.ENTITY_MODEL_DATA);
            if(data==null)return;
            float weight=a.action()==FaunaAnimal.EATING||a.action()==FaunaAnimal.RESTING?.28F:a.isInWater()&&a.profile().aquatic()?.45F:1.0F;
            float pitch=Mth.clamp(data.headPitch(),-32F,32F)*Mth.DEG_TO_RAD*weight;
            float yaw=Mth.clamp(data.netHeadYaw(),-52F,52F)*Mth.DEG_TO_RAD*weight;
            var processor=getAnimationProcessor();
            var necks=new ArrayList<GeoBone>(4);
            for(String name:NECK_CHAIN){
                GeoBone b=processor.getBone(name);
                if(b!=null)necks.add(b);
            }
            GeoBone head=processor.getBone("head");
            if(!necks.isEmpty()&&head!=null){
                float neckShare=.42F/necks.size();
                for(GeoBone neck:necks) applyLook(neck,pitch*neckShare,yaw*neckShare);
                applyLook(head,pitch*.58F,yaw*.58F);
            }else if(head!=null){
                applyLook(head,pitch,yaw);
            }
        }
        private static void applyLook(GeoBone bone,float pitch,float yaw){
            bone.setRotX(bone.getRotX()+pitch);
            bone.setRotY(bone.getRotY()+yaw);
            // GeckoLib has finished the keyframes for this frame. Clear our temporary change marker
            // so an unkeyed bone can return to its baseline on the next frame before adding the look.
            bone.resetStateChanges();
        }
    }
    public static final class Renderer extends GeoEntityRenderer<FaunaAnimal> {
        public Renderer(EntityRendererProvider.Context context){super(context,new Model());shadowRadius=.45F;}
        @Override public void preRender(PoseStack pose,FaunaAnimal a,BakedGeoModel model,MultiBufferSource buffers,VertexConsumer vertices,boolean rerender,float partialTick,int light,int overlay,int colour){
            if(!rerender){float age=a.getAgeScale();pose.scale(age,age,age);shadowRadius=a.getBbWidth()*.45F;}
            super.preRender(pose,a,model,buffers,vertices,rerender,partialTick,light,overlay,colour);
        }
        @Override protected void applyRotations(FaunaAnimal a,PoseStack pose,float age,float yaw,float partialTick,float scale){
            super.applyRotations(a,pose,age,yaw,partialTick,scale);
            if(!a.isAlive())return;
            float center=a.getBbHeight()*.52F;
            float bank=a.renderedFlightBank(partialTick);
            float spitch=a.renderedSwimPitch(partialTick);
            float sbank=a.renderedSwimBank(partialTick);
            if(bank!=0||spitch!=0||sbank!=0){
                pose.translate(0,center,0);
                if(spitch!=0)pose.mulPose(Axis.XP.rotationDegrees(spitch));
                if(bank+sbank!=0)pose.mulPose(Axis.ZP.rotationDegrees(-(bank+sbank)));
                pose.translate(0,-center,0);
            }
        }
        @Override protected float getDeathMaxRotation(FaunaAnimal a){return 0;}
    }
    private FaunaClient(){}
}

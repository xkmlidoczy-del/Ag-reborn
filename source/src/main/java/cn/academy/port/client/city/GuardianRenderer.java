package cn.academy.port.client.city;

import cn.academy.port.city.defense.*;
import cn.academy.port.fleet.*;
import cn.academy.port.fleet.client.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.*;

/** City uniform, articulated six-gun rig, contact IK, tactical crouch and visible command/medical gestures. */
@EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class GuardianRenderer extends MobRenderer<GuardianEntity,GuardianRenderer.Model> {
    public static final ResourceLocation SKIN=ResourceLocation.fromNamespaceAndPath("academy","textures/entity/city_npc/guard.png");
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("academy","city_guardian"),"main");
    private static final ModelLayerLocation GEAR=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("academy","guardian_equipment"),"main");
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e){e.registerLayerDefinition(LAYER,()->{
        var mesh=PlayerModel.createMesh(CubeDeformation.NONE,false);var root=mesh.getRoot();
        return LayerDefinition.create(mesh,64,64);
    });e.registerLayerDefinition(GEAR,()->{
        var m=new MeshDefinition();var r=m.getRoot();
        r.addOrReplaceChild("helmet",CubeListBuilder.create().addBox(-4.4f,-8.6f,-4.4f,8.8f,2.7f,8.8f).addBox(-4.4f,-7,-4,.8f,4,8).addBox(3.6f,-7,-4,.8f,4,8).addBox(-4.4f,-7,3.6f,8.8f,4,.8f),PartPose.ZERO);
        r.addOrReplaceChild("visor",CubeListBuilder.create().addBox(-3.6f,-5.8f,-4.55f,7.2f,2,.15f),PartPose.ZERO);
        r.addOrReplaceChild("vest",CubeListBuilder.create().addBox(-4.3f,1,-2.4f,8.6f,9,4.8f),PartPose.ZERO);
        var p=CubeListBuilder.create();for(int i=0;i<3;i++)p.addBox(-3.6f+i*2.5f,5,-3.2f,2,3,.8f);
        r.addOrReplaceChild("pouches",p,PartPose.ZERO);
        r.addOrReplaceChild("trim",CubeListBuilder.create().addBox(-4.32f,2,-2.48f,8.64f,.45f,4.96f),PartPose.ZERO);
        r.addOrReplaceChild("badge",CubeListBuilder.create().addBox(1.3f,2.9f,-2.6f,2.1f,1.6f,.2f),PartPose.ZERO);
        r.addOrReplaceChild("cross",CubeListBuilder.create().addBox(2.1f,3,-2.83f,.45f,1.4f,.15f).addBox(1.6f,3.5f,-2.84f,1.45f,.45f,.15f),PartPose.ZERO);
        return LayerDefinition.create(m,1,1);
    });}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(CityDefenseContent.GUARD.get(),GuardianRenderer::new);}
    public GuardianRenderer(EntityRendererProvider.Context c){super(c,new Model(c.bakeLayer(LAYER)),.35f);
        addLayer(new HumanoidArmorLayer<>(this,new HumanoidModel<GuardianEntity>(c.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),new HumanoidModel<GuardianEntity>(c.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),c.getModelManager()));addLayer(new Weapon(this));}
    @Override public ResourceLocation getTextureLocation(GuardianEntity e){return SKIN;}
    public static final class Model extends PlayerModel<GuardianEntity> {
        Model(ModelPart root){super(root,false);}
        @Override public void setupAnim(GuardianEntity e,float stride,float amount,float age,float yaw,float pitch){
            crouching=e.isCrouching();super.setupAnim(e,stride,amount,age,yaw,pitch);rightArm.visible=leftArm.visible=rightSleeve.visible=leftSleeve.visible=true;
            if(e.getVehicle() instanceof LabVehicle car){rightArm.xRot=leftArm.xRot=-1.05f;rightArm.yRot=-.23f;leftArm.yRot=.23f;return;}
            if(!(e.getMainHandItem().getItem() instanceof GunItem gun))return;
            double time=e.level().getGameTime()+Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            var rig=G03Rig.get(gun.variant);var frame=G03Renderer.frame(e,e.getMainHandItem(),time,0,amount,0);
            var a=frame.poses[rig.id("third_main")].rot.getEulerAnglesXYZ(new Vector3f());
            rightArm.xRot=a.x+head.xRot;rightArm.yRot=head.yRot+a.y;rightArm.zRot=a.z;
            leftArm.visible=true;leftSleeve.visible=false;
            var support=new PoseStack();translateToHand(HumanoidArm.RIGHT,support);support.translate(-1/16f,10/16f,-2/16f);support.mulPose(Axis.XP.rotationDegrees(-90));
            var contact=rig.point(frame,"right_hand");support.translate(-contact.x,-contact.y,-contact.z);
            var grip=support.last().pose().transformPosition(rig.point(frame,"left_hand")).sub(leftArm.x/16,leftArm.y/16,leftArm.z/16);
            var angles=new Quaternionf().rotationTo(new Vector3f(0,1,0),grip.normalize()).getEulerAnglesXYZ(new Vector3f());leftArm.xRot=angles.x;leftArm.yRot=angles.y;leftArm.zRot=angles.z;
            if(e.action()==GuardianEntity.Action.SIGNAL){leftArm.visible=leftSleeve.visible=true;leftArm.xRot=-2.1f;leftArm.zRot=-.25f;}
            if(e.action()==GuardianEntity.Action.MEDIC){leftArm.visible=leftSleeve.visible=true;leftArm.xRot=-1.1f;leftArm.yRot=.3f;rightArm.xRot=-.2f;}
            rightSleeve.copyFrom(rightArm);leftSleeve.copyFrom(leftArm);
        }
    }
    private static final class Gear extends RenderLayer<GuardianEntity,Model>{
        private final ModelPart parts;
        private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("academy","textures/ui/white.png");
        Gear(GuardianRenderer r,ModelPart parts){super(r);this.parts=parts;}
        private void part(String name,int color,PoseStack ps,MultiBufferSource buffers,int light){parts.getChild(name).render(ps,buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(WHITE)),light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,color);}
        @Override public void render(PoseStack ps,MultiBufferSource buffers,int light,GuardianEntity e,float stride,float amount,float partial,float age,float yaw,float pitch){
            ps.pushPose();getParentModel().head.translateAndRotate(ps);part("helmet",0xff263c49,ps,buffers,light);
            parts.getChild("visor").render(ps,buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(WHITE)),light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,0x904aafc4);ps.popPose();
            ps.pushPose();getParentModel().body.translateAndRotate(ps);part("vest",0xff243a47,ps,buffers,light);part("pouches",0xff334f5e,ps,buffers,light);part("trim",0xff5ccbd2,ps,buffers,light);
            int badge=e.role()==8?0xff398e70:e.role()==0?0xffdab363:0xff6baaba;part("badge",badge,ps,buffers,light);if(e.role()==8)part("cross",0xfff0fff9,ps,buffers,light);ps.popPose();
        }
    }
    private static final class Weapon extends RenderLayer<GuardianEntity,Model> {
        Weapon(GuardianRenderer owner){super(owner);}
        @Override public void render(PoseStack ps,MultiBufferSource buffers,int light,GuardianEntity e,float stride,float amount,float partial,float age,float yaw,float pitch){
            if(e.isPassenger()||e.action()==GuardianEntity.Action.MEDIC||!(e.getMainHandItem().getItem() instanceof GunItem gun))return;
            var model=getParentModel();var base=new Matrix4f(ps.last().pose());double time=e.level().getGameTime()+partial;
            var frame=G03Renderer.frame(e,e.getMainHandItem(),time,0,amount,0);var rig=G03Rig.get(gun.variant);var anchors=GunAnchors.forVariant(gun.variant);
            ps.pushPose();model.translateToHand(HumanoidArm.RIGHT,ps);ps.translate(-1/16f,10/16f,-2/16f);ps.mulPose(Axis.XP.rotationDegrees(-90));
            var contact=rig.point(frame,"right_hand");ps.translate(-contact.x,-contact.y,-contact.z);
            double d=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(e.position());int lod=d>24*24?2:1;
            G03Renderer.draw(ps,buffers,light,lod,frame,gun.variant);
            if(e.action()!=GuardianEntity.Action.SIGNAL&&!cn.academy.port.city.defense.armor.DefenseArmor.full(e,false)){var inverse=new Matrix4f(ps.last().pose()).invert().mul(base);var arm=model.leftArm;
                var shoulder=inverse.transformPosition(arm.x/16,arm.y/16,arm.z/16,new Vector3f());var pole=inverse.transformDirection(.25f,.8f,.05f,new Vector3f());
                GunRig.drawThirdContactSkin(ps,buffers,light,SKIN,false,false,shoulder,pole,rig,frame);
            }
            G03Renderer.burst(ps,buffers,frame,(time-GunItem.lastFire(e.getMainHandItem()))/20.0,gun.variant);ps.popPose();
        }
    }
}

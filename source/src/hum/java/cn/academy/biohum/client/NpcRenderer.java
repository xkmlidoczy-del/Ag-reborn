package cn.academy.biohum.client;
import cn.academy.biohum.*;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.*;

public final class NpcRenderer extends HumanoidMobRenderer<HumNpc,PlayerModel<HumNpc>> {
 public static final Set<String> SEEN=new HashSet<>();public static long renderCalls,renderNanos;
 final Profiles.Profile profile;
 private StoryYsmAssets.Appearance storyAppearance;
 private static final RenderType FACE_ATLAS=RenderType.entityCutoutNoCull(
   ResourceLocation.fromNamespaceAndPath(BioHum.ID,"textures/entity/aleister_faces_090.png"));
 private static final RenderType PROFILE_ATLAS=RenderType.entityCutoutNoCull(
   ResourceLocation.fromNamespaceAndPath(BioHum.ID,"textures/entity/aleister_profiles.png"));
 public NpcRenderer(EntityRendererProvider.Context context, Profiles.Profile profile) {
  super(context, new NpcModel(LayerDefinition.create(
      PlayerModel.createMesh(CubeDeformation.NONE, profile.slim()), 64, 64).bakeRoot(),
      profile.slim()), .3f * profile.scale());
  this.profile = profile;
  if (profile.id().equals("kanzaki")||profile.id().equals("aleister")) {
   layers.removeIf(layer -> layer.getClass() == ItemInHandLayer.class);
   addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()) {
    @Override
    protected void renderArmWithItem(LivingEntity entity, ItemStack item, ItemDisplayContext display,
        HumanoidArm arm, PoseStack pose, MultiBufferSource buffers, int light) {
     if (arm == entity.getMainArm() && (rendersLongSword(entity,item)
             ||item.is(BioHum.BLASTING_ROD.get()))) return;
     super.renderArmWithItem(entity, item, display, arm, pose, buffers, light);
    }
   });
  }
  addLayer(new Accessories(this, profile,context.getItemInHandRenderer()));
 }
 @Override protected void scale(HumNpc a,PoseStack s,float partial){s.scale(a.phaseScale(),a.phaseScale(),a.phaseScale());}
 @Override
 protected void setupRotations(HumNpc npc,PoseStack pose,float age,float bodyYaw,float partial,float scale){
  if(npc.suspended()){
   // Invert once around the chamber pivot. The eyes are below the feet, while the room stays vertical.
   pose.translate(0,LifeSupportRenderer.height(npc,age),0);
   pose.mulPose(Axis.YP.rotationDegrees(180-(npc.chamberInstalled()?0:bodyYaw)));
   pose.mulPose(Axis.ZP.rotationDegrees(180+Mth.sin(age*.049f)*2.1f));
   return;
  }
  var skill=Skills.get(npc.technique());
  if(skill!=null&&npc.isAlive()){
   float frame=npc.signatureAge()+partial;
   boolean canceled=npc.abortAge()>=0;
   float blend=CastMotion.blend(skill,canceled?Math.max(0,npc.abortFrame()):frame);
   if(canceled)blend*=1-Mth.smoothstep(Mth.clamp((npc.abortAge()+partial)/8f,0,1));
   bodyYaw=Mth.rotLerp(blend,bodyYaw,npc.castYaw());
  }
  super.setupRotations(npc,pose,age,bodyYaw,partial,scale);
 }
 public static boolean isBlinking(HumNpc npc) {
  return (npc.tickCount + Math.floorMod(npc.getUUID().hashCode(), 127)) % 109 < 4;
 }
 @Override public ResourceLocation getTextureLocation(HumNpc a){
  if(storyAppearance!=null)return storyAppearance.texture();
  if(a.suspended())return ResourceLocation.fromNamespaceAndPath(BioHum.ID,"textures/entity/aleister_normal.png");
  String expression=isBlinking(a)?"blink":a.expression()==1?"friendly":a.expression()==2?"angry":"normal";return ResourceLocation.fromNamespaceAndPath(BioHum.ID,"textures/entity/"+profile.id()+"_"+expression+".png");}
 @Override protected RenderType getRenderType(HumNpc npc,boolean visible,boolean translucent,boolean glowing){
  if(storyAppearance!=null&&visible&&!translucent)return RenderType.entityCutoutNoCull(storyAppearance.texture());
  return super.getRenderType(npc,visible,translucent,glowing);
 }
 @Override public void render(HumNpc a,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
  long start=System.nanoTime();SEEN.add(profile.id());
  boolean capsule=a.suspended()&&!a.isInvisible();
  var previous = storyAppearance;
  var npcModel = (NpcModel) getModel();
  storyAppearance = StoryYsmAssets.find(a.characterId);
  npcModel.storyMesh = storyAppearance == null ? null : storyAppearance.mesh();
  try {
   if(capsule)LifeSupportRenderer.frame(a,partial,pose,buffers,light);
   super.render(a,yaw,partial,pose,buffers,capsule?LightTexture.pack(13,3):light);
   if(capsule)LifeSupportRenderer.liquid(a,partial,pose,buffers);
  } finally {
   storyAppearance = previous;
   npcModel.storyMesh = previous == null ? null : previous.mesh();
  }
  renderNanos+=System.nanoTime()-start;renderCalls++;
 }

 static boolean rendersLongSword(LivingEntity entity, ItemStack item) {
  return entity instanceof HumNpc npc && npc.characterId.equals("kanzaki") && item.is(Items.IRON_SWORD);
 }

 static final class Accessories extends RenderLayer<HumNpc,PlayerModel<HumNpc>> {
  private record Piece(ModelPart bone, AccessoryMesh mesh,String name) {}
  private static final Set<String> STORY_PROPS = Set.of("rod","spear","mace","mace_end","blade","staff","staff_end",
      "shield","hammer","hammer_end","needle","wand","remote","coordinate_torch","torch_lens");

  private final List<Piece> pieces = new ArrayList<>();
  private final RenderType renderType;
  private final AccessoryMesh scabbard, drawnBlade, drawnHilt, sheathedHilt, ascalon, spearPoint;
  private final java.util.Map<Integer,AccessoryMesh> flightWings;
  private final net.minecraft.client.renderer.ItemInHandRenderer heldItems;

  private static AccessoryMesh prop(String name,float[] box,float[] pivot,float[] rotation,int[] uv,float[] taper){
   return new AccessoryMesh(new Profiles.Accessory("body",name,box,pivot,rotation,uv,taper));
  }

  private static AccessoryMesh stripProp(String name,float[] box,float[] pivot,float[] rotation,
                                          int v,int width,int length,float[] taper){
   return new AccessoryMesh(new Profiles.Accessory("body",name,box,pivot,rotation,new int[]{0,v},taper),width,length);
  }

  Accessories(NpcRenderer renderer, Profiles.Profile profile,net.minecraft.client.renderer.ItemInHandRenderer heldItems) {
   super(renderer);
   this.heldItems=heldItems;
   var texture = ResourceLocation.fromNamespaceAndPath(BioHum.ID,
       "textures/entity/" + profile.id() + "_details.png");
   renderType = RenderType.entityCutoutNoCull(texture);
   var model = getParentModel();
   for (var accessory : profile.accessories()) {
    ModelPart bone = switch (accessory.parent()) {
     case "head" -> model.head;
     case "left_arm" -> model.leftArm;
     case "right_arm" -> model.rightArm;
     case "left_leg" -> model.leftLeg;
     case "right_leg" -> model.rightLeg;
     case "body" -> model.body;
     default -> throw new IllegalArgumentException("Unknown attachment bone " + accessory.parent());
    };
    pieces.add(new Piece(bone, new AccessoryMesh(accessory),accessory.name()));
   }
   scabbard=profile.id().equals("kanzaki")?stripProp("scabbard",new float[]{4.5f,10,-1.4f,1,1.15f,22.6f},
       new float[]{5,10,0},new float[]{-.38f,.13f,0},43,6,25,new float[]{1,1,.79f,.79f}):null;
   drawnBlade=profile.id().equals("kanzaki")?stripProp("blade",new float[]{-.39f,9.2f,-23.3f,.78f,.35f,23.2f},
       new float[]{0,9.4f,-.35f},new float[]{1.396f,0,0},72,4,27,new float[]{1,1,.95f,1}):null;
   drawnHilt=profile.id().equals("kanzaki")?stripProp("hilt",new float[]{-1.06f,8.95f,-.52f,2.12f,.9f,4.7f},
       new float[]{0,9.4f,-.35f},new float[]{1.396f,0,0},108,6,10,new float[]{1,1,1,1}):null;
   sheathedHilt=profile.id().equals("kanzaki")?stripProp("hip_hilt",new float[]{4.26f,10,-5.3f,1.5f,.94f,4.8f},
       new float[]{5,10,0},new float[]{-.38f,.13f,0},108,6,10,new float[]{1,1,1,1}):null;
   ascalon=profile.id().equals("acqua")?stripProp("ascalon",new float[]{-.76f,9.16f,-23.35f,1.52f,.45f,24.2f},
       new float[]{0,9.4f,-.35f},new float[]{1.396f,0,0},87,6,27,new float[]{1,1,1,1}):null;
   spearPoint=profile.id().equals("itsuwa")?stripProp("spear_tip",new float[]{-.56f,9.1f,-15.65f,1.12f,.45f,3.1f},
       new float[]{0,9.4f,-.35f},new float[]{1.396f,0,0},102,4,8,new float[]{.66f,1,1,1}):null;
   flightWings=profile.id().equals("floris")?java.util.Map.of(-1,flightWing(-1),1,flightWing(1)):java.util.Map.of();
  }

  private static AccessoryMesh flightWing(int side){
   float x=side*3.15f;
   return prop("shoulder_wing",new float[]{x-1.6f,1.58f,2.46f,3.2f,12.2f,.92f},
       new float[]{x,1.58f,2.9f},new float[]{.32f,side*.12f,0},
       new int[]{63,3},new float[]{.94f,1,.22f,.62f});
  }

  @Override
  public void render(PoseStack pose, MultiBufferSource buffers, int light, HumNpc npc,
      float walk, float amount, float partial, float age, float yaw, float pitch) {
   if (npc.isInvisible()) return;
   var model = (NpcModel) getParentModel();
   boolean story = model.storyMesh != null;
   var vertices = buffers.getBuffer(renderType);
   int overlay = LivingEntityRenderer.getOverlayCoords(npc, 0);
   boolean swordHeld = rendersLongSword(npc, npc.getMainHandItem());
   boolean ascalonPhase=ascalon!=null&&npc.phaseId().equals("ot18_ascalon");
   var signature = npc.signature();
   float wingLength = 0;
   if (npc.characterId.equals("kakine") && !npc.wings().isEmpty()) {
    wingLength = npc.wingsFolded()?.47f:1;
   } else if (signature == SignatureMove.DARK_MATTER_WINGS || npc.technique().equals("white_wing_flight")) {
    float elapsed = npc.signatureAge() + partial;
    float opening = Mth.clamp(elapsed / 13, 0, 1);
    wingLength = opening;
   }
   for (var piece : pieces) {
    String name=piece.name();
    if(story&&!name.startsWith("wing_")&&!STORY_PROPS.contains(name))continue;
    if(npc.suspended()&&Set.of("nape_hair","right_lock","left_lock","rod").contains(name))continue;
    if(npc.characterId.equals("aleister")&&name.equals("rod")&&!npc.getMainHandItem().is(BioHum.BLASTING_ROD.get()))continue;
    if(name.startsWith("sword_")||ascalonPhase&&name.startsWith("mace"))continue;
    if(name.startsWith("wing_")&&wingLength<.05f)continue;
    if(story&&name.equals("remote")&&PresentationProps.usesRemote(npc,partial))continue;
    pose.pushPose();
    translateHeldPart(model,piece.bone(),pose);
    if(Set.of("rod","spear","mace","mace_end","blade").contains(name)){
     pose.translate(0,9.4/16,-.35/16);
     pose.mulPose(Axis.XP.rotationDegrees(80));
     pose.translate(0,-9.4/16,.35/16);
    }
    piece.mesh().draw(pose, vertices, light, overlay, name.startsWith("wing_") ? wingLength : 1);
    pose.popPose();
   }
   if(swordHeld){
    var skill=Skills.get(npc.technique());
    boolean drawn=skill!=null&&(skill.id().equals("saint_intercept")||skill.id().equals("yuisen")||skill.id().startsWith("close_kanzaki"))
         &&!skill.id().endsWith("_flinch")&&npc.signatureAge()+partial>=Math.max(2,skill.windup()-3)
         &&npc.signatureAge()+partial<skill.length()-4;
    drawSheathed(scabbard,model,pose,vertices,light,overlay);
    if(drawn){
     drawHeld(drawnBlade,model,pose,vertices,light,overlay);
     drawHeld(drawnHilt,model,pose,vertices,light,overlay);
    }else drawSheathed(sheathedHilt,model,pose,vertices,light,overlay);
   }
   if(ascalonPhase)drawHeld(ascalon,model,pose,vertices,light,overlay);
   if(spearPoint!=null)drawHeld(spearPoint,model,pose,vertices,light,overlay);
   if(!flightWings.isEmpty()&&npc.wings().equals("flight_gear_step")){
    float opening=npc.technique().equals("flight_gear_step")
            ?Mth.clamp((npc.signatureAge()+partial-7)/5,0,1):1;
    for(var wing:flightWings.entrySet()){
     float x=wing.getKey()*3.15f/16;
     pose.pushPose();
     getParentModel().body.translateAndRotate(pose);
     pose.translate(x,1.58/16,2.9/16);
     pose.mulPose(Axis.ZP.rotationDegrees(-wing.getKey()*(15+54*opening)));
     pose.translate(-x,-1.58/16,-2.9/16);
     wing.getValue().draw(pose,vertices,light,overlay,.34f+.66f*opening);
     pose.popPose();
    }
   }
   if(npc.suspended()) {
    if(!story)face(npc,pose,buffers,overlay);
   } else {
    PresentationProps.draw(npc,model,heldItems,pose,buffers,light,partial);
    SkillFx.eyes(npc,getParentModel().head,pose,buffers,partial);
    SkillFx.ready(npc,(NpcModel)getParentModel(),pose,buffers,partial);
   }
   if (!npc.characterId.equals("kakine") && !npc.wings().isEmpty()) {
    SkillFx.wings(npc,getParentModel().body,pose,buffers,age);
   }
  }

  private static void translateHeldPart(NpcModel model,ModelPart bone,PoseStack pose){
   if(model.storyMesh!=null&&(bone==model.leftArm||bone==model.rightArm)){
    model.storyMesh.translateToHeldPart(model,bone==model.rightArm,pose);
   }else bone.translateAndRotate(pose);
  }

  private static void drawHeld(AccessoryMesh mesh,NpcModel model,PoseStack pose,
                               com.mojang.blaze3d.vertex.VertexConsumer out,int light,int overlay){
   pose.pushPose();
   translateHeldPart(model,model.rightArm,pose);
   mesh.draw(pose,out,light,overlay,1);
   pose.popPose();
  }

  private static void drawSheathed(AccessoryMesh mesh,NpcModel model,PoseStack pose,
                                   com.mojang.blaze3d.vertex.VertexConsumer out,int light,int overlay){
   pose.pushPose();
   if(model.storyMesh!=null)model.storyMesh.translateToSheath(model,pose);
   else model.body.translateAndRotate(pose);
   mesh.draw(pose,out,light,overlay,1);
   pose.popPose();
  }

  private void face(HumNpc npc,PoseStack stack,MultiBufferSource buffers,int overlay){
   int t=Math.floorMod(npc.level().getGameTime()+npc.getUUID().hashCode(),400);
   int profile=t/100*60+Math.max(0,t%100-40);
   int cell=profile;
   if(Math.floorMod(npc.level().getGameTime()+npc.getUUID().hashCode(),109)<4)cell+=240;
   float u=(cell%16)/16f, v=(cell/16)/32f;
   var out=buffers.getBuffer(FACE_ATLAS);
   stack.pushPose();
   getParentModel().head.translateAndRotate(stack);
   var pose=stack.last();
   var points=new float[][]{{-.25f,-.5f,u,v},{.25f,-.5f,u+.0625f,v},
           {.25f,0,u+.0625f,v+.03125f},{-.25f,0,u,v+.03125f}};
   for(var p:points)out.addVertex(pose,p[0],p[1],-.2509f).setColor(-1)
       .setUv(p[2],p[3]).setOverlay(overlay).setLight(LightTexture.pack(14,2)).setNormal(pose,0,0,-1);
   var sides=buffers.getBuffer(PROFILE_ATLAS);
   for(int side=0;side<2;side++){
    int tile=profile+side*240;
    float left=(tile%16)/16f, top=(tile/16)/30f;
    float x=side==0?-.2509f:.2509f;
    float front=side==0?.25f:-.25f;
    float[] zs={front,-front,-front,front};
    for(int i=0;i<4;i++){
     sides.addVertex(pose,x,i<2?-.5f:0,zs[i]).setColor(-1)
       .setUv(left+(i==1||i==2?.0625f:0),top+(i>=2?1/30f:0)).setOverlay(overlay)
       .setLight(LightTexture.pack(13,2)).setNormal(pose,side==0?-1:1,0,0);
    }
   }
   stack.popPose();
  }
 }
}

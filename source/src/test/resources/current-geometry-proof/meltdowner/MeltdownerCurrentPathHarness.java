package cn.academy.port.client;

import cn.academy.port.ClassicHudConfig;
import cn.academy.port.client.vfx2.*;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.mojang.blaze3d.vertex.*;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.renderer.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import static cn.academy.port.client.MeltdownerCurrentMainGeometryHarness.*;

/** Actual current compiled renderer calls, seeded ownership, and native material declarations.
 * Game/resource/GL hosts are explicit. No packet acceptance, native draw, or pixel claim.
 */
public final class MeltdownerCurrentPathHarness {
 static final Minecraft MC=Minecraft.getInstance();
 static final Class<?> BEAM=ClassicMeltdownerBeamEffects.class;
 static final Class<?> RAY=nested(BEAM,"Ray");
 static int assertions,selected,scenes,eventCases,stateCases;
 static boolean selecting=true;static java.io.DataOutputStream nearOut;static java.security.MessageDigest nearDigest;
 static final List<String> names=new ArrayList<>();
 static final CommentedConfig CONFIG=CommentedConfig.inMemory();
 static void yes(boolean p,String why){assertions++;if(!p)throw new AssertionError(why);}
 static Object get(Object o,String n)throws Exception {Class<?> c=o instanceof Class<?> t?t:o.getClass();Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o instanceof Class<?>?null:o);}
 static void put(Object o,String n,Object v)throws Exception {Class<?> c=o instanceof Class<?> t?t:o.getClass();Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o instanceof Class<?>?null:o,v);}
 static Method exact(Class<?> c,String n,Class<?>...p)throws Exception {Method m=c.getDeclaredMethod(n,p);m.setAccessible(true);return m;}
 static Object construct(Class<?> c,Class<?>[] p,Object...a)throws Exception {var k=c.getDeclaredConstructor(p);k.setAccessible(true);return k.newInstance(a);}
 static void config(boolean on)throws Exception{
  ClassicHudConfig.SPEC.correct(CONFIG);
  Class<?> loaded=Class.forName("net.neoforged.fml.config.LoadedConfig");
  ClassicHudConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig)construct(loaded,new Class<?>[]{CommentedConfig.class,java.nio.file.Path.class,net.neoforged.fml.config.ModConfig.class},CONFIG,null,null));
  ClassicHudConfig.LIGHT_SHIELD_VFX2_QUALITY.set(ClassicHudConfig.Vfx2Quality.OFF);
  ClassicHudConfig.MD_NEAR_EYE_VISIBILITY.set(on);
 }
 static RegisterShadersEvent register(boolean complete){
  List<Pair<ShaderInstance,Consumer<ShaderInstance>>> list=new ArrayList<>();
  var e=new RegisterShadersEvent(location->Optional.empty(),list);
  ClassicMeltdownerStarterEffects.CutoffShader.register(e);
  if(complete)list.forEach(p->p.getSecond().accept(p.getFirst()));
  list.clear();MdNearEyeMaterial.shaders(e);
  if(complete)list.forEach(p->p.getSecond().accept(p.getFirst()));
  return e;
 }
 static void world()throws Exception {
  MC.level=new ClientLevel();MC.player.world=MC.level;MC.level.entities.put(MC.player.getId(),MC.player);
  MC.player.alive=true;MC.player.removed=false;MC.player.spectator=false;MC.player.uuid=new UUID(1,2);
  MC.connection=new ClientPacketListener();MC.cameraEntity=null;MC.options.view=CameraType.FIRST_PERSON;MC.paused=true;
  camera(MC.gameRenderer.getMainCamera(),Vec3.ZERO,MC.player,false);
 }
 static void camera(Camera c,Vec3 position,net.minecraft.world.entity.Entity entity,boolean detached)throws Exception {
  put(c,"position",position);put(c,"entity",entity);put(c,"detached",detached);
 }
 static Object rayObject(Player p,ClassicMeltdownerBeamTimeline.Spec spec,Vec3 origin,Vec3 direction,double length,boolean direct)throws Exception {
  return construct(RAY,new Class<?>[]{Player.class,ClientLevel.class,Object.class,ClassicMeltdownerBeamTimeline.Spec.class,Vec3.class,Vec3.class,double.class,long.class,boolean.class},p,MC.level,MC.getConnection(),spec,origin,direction,length,0L,direct);
 }
 static Draw currentRay(int family,Vec3 cam,Vec3 origin,Vec3 direction,double length,long age,boolean rotated,boolean first)throws Exception {
  if(family==0||family>4)return ray(family,cam,origin,direction,length,age,rotated,first);
  begin(BEAM,age);PoseStack poses=poses(cam,rotated);MC.options.view=first?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_BACK;
  Player player=first?MC.player:new Player();player.world=MC.level;
  Object r=rayObject(player,SPECS.get(family-1),origin,direction,length,family==1);
  exact(BEAM,"renderRay",RAY,Minecraft.class,Vec3.class,Matrix4f.class,boolean.class).invoke(null,r,MC,cam,poses.last().pose(),true);
  return finish(BEAM);
 }
 static void geometry(Draw a,Draw b,String label){
  yes(a.rows().size()==b.rows().size(),"GEOMETRY_COUNT "+label);yes(a.rng()==b.rng(),"RNG_EXACT "+label);
  for(int i=0;i<a.rows().size();i++)for(int j=0;j<9;j++)yes(Float.floatToRawIntBits(a.rows().get(i)[j])==Float.floatToRawIntBits(b.rows().get(i)[j]),"GEOMETRY_ATTRIBUTE_EXACT "+label+" "+i+"/"+j);
 }
 static String typeName(RenderType t){return t.name;}
 static Object drawState(RenderType t)throws Exception {
  Object setup=t.setupState;var matches=Arrays.stream(setup.getClass().getDeclaredFields()).filter(f->f.getType().getName().equals("cn.academy.port.client.vfx2.MdNearEyeMaterial$DrawState")).toList();
  yes(matches.size()==1,"EXACT_DRAWSTATE_CAPTURE");var f=matches.getFirst();f.setAccessible(true);return f.get(setup);
 }
 static Object composite(RenderType t)throws Exception {return get(t,"state");}
 static Object shard(Object state,String name)throws Exception{return get(state,name);}
 static void sameStateExceptShader(RenderType classic,RenderType enhanced,String expectedShader)throws Exception {
  Object a=composite(classic),b=composite(enhanced);
  for(String n:List.of("transparencyState","depthTestState","cullState","lightmapState","overlayState","layeringState","outputState","texturingState","writeMaskState","lineState","colorLogicState","outlineProperty"))
   yes(shard(a,n)==shard(b,n),"MATERIAL_STATE_EXACT "+n);
  Object at=shard(a,"textureState"),bt=shard(b,"textureState");
  for(String n:List.of("texture","blur","mipmap"))yes(Objects.equals(get(at,n),get(bt,n)),"TEXTURE_STATE_EXACT "+n);
  yes(classic.format()==enhanced.format()&&classic.mode()==enhanced.mode()&&classic.bufferSize()==enhanced.bufferSize(),"MATERIAL_FORMAT_EXACT");
  yes(enhanced.format()==DefaultVertexFormat.POSITION_TEX_COLOR&&enhanced.mode()==VertexFormat.Mode.QUADS&&enhanced.bufferSize()==4096,"EXPECTED_VERTEX_FORMAT");
  Field sorting=RenderType.class.getDeclaredField("sortOnUpload");sorting.setAccessible(true);yes(!sorting.getBoolean(enhanced),"EXPECTED_UNSORTED");
  var shader=shard(b,"shaderState");@SuppressWarnings("unchecked") var supplier=(Optional<Supplier<ShaderInstance>>)get(shader,"shader");
  yes(supplier.orElseThrow().get().name.equals(expectedShader),"EXPECTED_SHADER "+expectedShader);
  yes(shard(b,"depthTestState")==RenderStateShard.LEQUAL_DEPTH_TEST,"EXPECTED_LEQUAL");
  yes(shard(b,"writeMaskState")==RenderStateShard.COLOR_DEPTH_WRITE,"EXPECTED_DEPTH_WRITE");
  yes(shard(b,"transparencyState")==RenderStateShard.TRANSLUCENT_TRANSPARENCY,"EXPECTED_TRANSLUCENCY");
  yes(shard(b,"outputState")==RenderStateShard.PARTICLES_TARGET,"EXPECTED_PARTICLES_TARGET");
  stateCases++;
 }
 static void materials(List<RenderType> actual,boolean eligible,String label)throws Exception {
  int selectedHere=0;
  for(int i=0;i<actual.size();i++){
   RenderType type=actual.get(i);boolean near=type instanceof MdNearEyeMaterial;
   yes(near==eligible,"MATERIAL_SCOPE "+label+" layer="+i);
   if(near){selectedHere++;Object d=drawState(type);RenderType original=(RenderType)get(d,"original"),enhanced=(RenderType)get(d,"enhanced");
    sameStateExceptShader(original,enhanced,i<3?"md_near_eye_glow":"md_near_eye_core");
    yes(shard(composite(enhanced),"cullState")== (i<3?RenderStateShard.NO_CULL:RenderStateShard.CULL),"EXPECTED_LAYER_CULL");
    String texture=i==0?"textures/effects/mdray/blend_in.png":i==1?"textures/effects/mdray/tile.png":i==2?"textures/effects/mdray/blend_out.png":"textures/port/white.png";
    yes(typeName(original).equals("academy_classic_beam_"+texture+"_"+(i>=3)),"EXACT_ORIGINAL_BINDING");
    Object textureState=shard(composite(enhanced),"textureState");yes(get(textureState,"texture").equals(Optional.of(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy",texture))),"EXPECTED_SOURCE_TEXTURE");
    yes(Boolean.FALSE.equals(get(textureState,"blur"))&&Boolean.FALSE.equals(get(textureState,"mipmap")),"EXPECTED_NEAREST_TEXTURE");
   }
  }
  if(eligible){yes(selectedHere==5,"FIVE_SELECTED_LAYERS");selected++;}
 }
 static void matrix()throws Exception {
  nearDigest=java.security.MessageDigest.getInstance("SHA-256");nearOut=new java.io.DataOutputStream(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(),nearDigest));
  List<Vec3> cameras=List.of(new Vec3(64.5,72.25,-32.75),new Vec3(14502577.5,-59,4277858.5),new Vec3(29999999.75,319.875,-29999999.125),new Vec3(-29999999.5,-63.75,29999999.5));
  List<Vec3> directions=List.of(new Vec3(.3,-.2,.8).normalize(),new Vec3(-.7,.21,-.13).normalize(),new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(0,0,-1));
  Vec3 origin=new Vec3(.37,-.12,.7);
  for(int family=0;family<9;family++)for(Vec3 dir:directions)for(boolean rotated:List.of(false,true))for(boolean first:List.of(false,true))for(long age:List.of(0L,200L,499L))for(double length:List.of(0.0,.1,15.0,64.0)){
   Draw old=ray(family,Vec3.ZERO,origin,dir,length,age,rotated,first),current=currentRay(family,Vec3.ZERO,origin,dir,length,age,rotated,first);
   geometry(old,current,"matrix");if(!selecting)yes(old.flushes().equals(current.flushes()),"PRESELECTION_MATERIAL_EXACT");materials(new ArrayList<>(requested),selecting&&family==1&&first,"matrix");record(nearOut,current);
   for(Vec3 cam:cameras){Draw relocated=currentRay(family,cam,cam.add(origin),dir,length,age,rotated,first);compare(current,relocated);materials(new ArrayList<>(requested),selecting&&family==1&&first,"relocated");}scenes++;
  }
  for(int family=0;family<4;family++)for(long age:List.of(0L,200L,499L))for(boolean rotated:List.of(false,true))for(boolean local:List.of(false,true)){
   Draw a=billboard(family,Vec3.ZERO,origin,age,rotated,local),b=billboard(family,Vec3.ZERO,origin,age,rotated,local);geometry(a,b,"billboard");record(nearOut,b);
   yes(a.flushes().equals(b.flushes()),"BILLBOARD_MATERIAL_EXACT");for(Vec3 cam:cameras)compare(a,billboard(family,cam,cam.add(origin),age,rotated,local));scenes++;
  }
  yes(scenes==2640&&selected==(selecting?720:0),"FULL_ON_MATRIX_SCOPE");
  nearOut.close();String digest=HexFormat.of().formatHex(nearDigest.digest());
  if(!selecting)yes(digest.equals("335d8736629a8bbb585927086393a787fb9019c87befd1b654c57df8b123482a"),"PRESELECTION_FROZEN_DIGEST_EXACT");
  System.out.println("CURRENT_PATH_MATRIX scenes="+scenes+" selecting="+selecting+" requestedMaterialDigest="+digest);
 }
 @SuppressWarnings("unchecked") static List<Object> rays()throws Exception{return (List<Object>)get(BEAM,"RAYS");}
 static RenderLevelStageEvent event(Camera camera,RenderLevelStageEvent.Stage stage){
  DeltaTracker partial=new DeltaTracker(){public float getGameTimeDeltaTicks(){return 0;}public float getGameTimeDeltaPartialTick(boolean paused){return .375F;}public float getRealtimeDeltaTicks(){return 0;}};
  return new RenderLevelStageEvent(stage,null,new PoseStack(),new Matrix4f(),new Matrix4f(),0,partial,camera,null);
 }
 static void eventSeed(boolean direct,ClassicMeltdownerBeamTimeline.Spec spec,boolean remote)throws Exception {
  rays().clear();world();ClassicMeltdownerBeamEffects.render(event(MC.gameRenderer.getMainCamera(),RenderLevelStageEvent.Stage.AFTER_PARTICLES));
  Player caster=MC.player;if(remote){caster=new Player();caster.id=9;caster.world=MC.level;MC.level.entities.put(9,caster);}
  Object r=rayObject(caster,spec,new Vec3(.37,-.12,.7),new Vec3(.3,-.2,.8).normalize(),15,direct);
  if(spec==ClassicMeltdownerBeamTimeline.MD)rays().add(r);
  else {
   String skill=spec==ClassicMeltdownerBeamTimeline.BASIC?"mine_ray_basic":spec==ClassicAdvancedMineRayTimeline.EXPERT?"mine_ray_expert":"mine_ray_luck";
   String map=spec==ClassicMeltdownerBeamTimeline.BASIC?"MINERS":spec==ClassicAdvancedMineRayTimeline.EXPERT?"EXPERT_MINERS":"LUCK_MINERS";
   Class<?> context=nested(BEAM,"Context");Object c=construct(context,new Class<?>[]{Player.class,ClientLevel.class,Object.class,String.class,long.class,long.class,boolean.class,boolean.class},caster,MC.level,MC.getConnection(),skill,1L,0L,true,!remote);
   put(c,"ray",r);@SuppressWarnings("unchecked") Map<Integer,Object> contexts=(Map<Integer,Object>)get(BEAM,map);contexts.put(caster.getId(),c);
  }
  clock(BEAM,200);((Random)get(BEAM,"RANDOM")).setSeed(4321);
 }
 static void renderEvent(String label,RenderLevelStageEvent e,boolean selectedExpected,int expectedVertices)throws Exception {
  rows.clear();flushes.clear();requested.clear();Matrix4f before=new Matrix4f(e.getPoseStack().last().pose());
  com.mojang.blaze3d.systems.RenderSystem.setShaderColor(.2F,.4F,.6F,.8F);
  ClassicMeltdownerBeamEffects.render(e);
  yes(rows.size()==expectedVertices,"EVENT_TOPOLOGY "+label+" "+rows.size());
  if(!rows.isEmpty())materials(new ArrayList<>(requested),selectedExpected,label);
  yes(e.getPoseStack().last().pose().equals(before),"EVENT_POSE_RESTORED");
  yes(Arrays.equals(com.mojang.blaze3d.systems.RenderSystem.getShaderColor(),new float[]{.2F,.4F,.6F,.8F}),"EVENT_COLOR_RESTORED");
  names.add(label);eventCases++;
 }
 static void events()throws Exception {
  for(String kind:List.of("direct","reflection","remote","third","detached","other-camera","camera-entity","minecraft-camera","dead","removed","spectator","uuid","entity-replaced","player-replaced","connection","world","epoch","wrong-stage","basic","expert","luck")){
   ClassicMeltdownerBeamTimeline.Spec spec=switch(kind){case "basic"->ClassicMeltdownerBeamTimeline.BASIC;case "expert"->ClassicAdvancedMineRayTimeline.EXPERT;case "luck"->ClassicAdvancedMineRayTimeline.LUCK;default->ClassicMeltdownerBeamTimeline.MD;};
   eventSeed(!kind.equals("reflection"),spec,kind.equals("remote"));Camera camera=MC.gameRenderer.getMainCamera();var stage=RenderLevelStageEvent.Stage.AFTER_PARTICLES;int expected=876;
   switch(kind){
    case "third"->MC.options.view=CameraType.THIRD_PERSON_BACK;case "detached"->put(camera,"detached",true);
    case "other-camera"->{camera=new Camera();camera(camera,Vec3.ZERO,MC.player,false);}case "camera-entity"->put(camera,"entity",new Player());case "minecraft-camera"->MC.cameraEntity=new Player();
    case "dead"->{MC.player.alive=false;expected=0;}case "removed"->{MC.player.removed=true;expected=0;}case "spectator"->{MC.player.spectator=true;expected=0;}
    case "uuid"->{MC.player.uuid=new UUID(8,9);expected=0;}case "entity-replaced"->{MC.level.entities.put(MC.player.getId(),new Player());expected=0;}
    case "player-replaced"->{MC.player=new net.minecraft.client.player.LocalPlayer();expected=0;}
    case "connection"->{MC.connection=new ClientPacketListener();expected=0;}case "world"->{MC.level=new ClientLevel();expected=0;}
    case "epoch"->put(BEAM,"callbackEpoch",(long)get(BEAM,"callbackEpoch")+1);
    case "wrong-stage"->{stage=RenderLevelStageEvent.Stage.AFTER_ENTITIES;expected=0;}
   }
   renderEvent(kind,event(camera,stage),kind.equals("direct"),expected);
  }
  eventSeed(true,ClassicMeltdownerBeamTimeline.MD,false);
  rays().add(rayObject(MC.player,ClassicMeltdownerBeamTimeline.MD,new Vec3(.37,-.12,.7),new Vec3(1,0,0),10,false));
  rows.clear();flushes.clear();requested.clear();ClassicMeltdownerBeamEffects.render(event(MC.gameRenderer.getMainCamera(),RenderLevelStageEvent.Stage.AFTER_PARTICLES));
  yes(rows.size()==1752&&requested.size()==10,"MIXED_DIRECT_REFLECTION_TOPOLOGY");materials(new ArrayList<>(requested.subList(0,5)),true,"mixed-direct");materials(new ArrayList<>(requested.subList(5,10)),false,"mixed-reflection");names.add("mixed-direct-reflection");eventCases++;
 }
 static void preselectionFaults()throws Exception {
  for(String failure:List.of("config-unloaded","glow-compile","core-compile","missing-uniform","inactive-uniform","reload-pending","global-forced")){
   config(true);ShaderInstance.failName="";ShaderInstance.missing="";org.lwjgl.opengl.GL20.inactive="";System.clearProperty("academy.vfx2.forceFallback");
   switch(failure){case "glow-compile"->ShaderInstance.failName="md_near_eye_glow";case "core-compile"->ShaderInstance.failName="md_near_eye_core";case "missing-uniform"->ShaderInstance.missing="FadeEnd";case "inactive-uniform"->org.lwjgl.opengl.GL20.inactive="FadeEnd";case "global-forced"->System.setProperty("academy.vfx2.forceFallback","true");}
   register(!failure.equals("reload-pending"));if(failure.equals("config-unloaded"))ClassicHudConfig.SPEC.acceptConfig(null);world();
   Draw a=ray(1,Vec3.ZERO,new Vec3(.37,-.12,.7),new Vec3(1,0,0),15,200,false,true),b=currentRay(1,Vec3.ZERO,new Vec3(.37,-.12,.7),new Vec3(1,0,0),15,200,false,true);
   geometry(a,b,failure);yes(a.flushes().equals(b.flushes()),"PRESELECTION_REQUESTED_MATERIAL_EXACT "+failure);materials(new ArrayList<>(requested),false,failure);names.add("preselection-"+failure);
  }
  config(true);ShaderInstance.failName="";ShaderInstance.missing="";org.lwjgl.opengl.GL20.inactive="";System.clearProperty("academy.vfx2.forceFallback");register(true);
 }
 static final class RecordingType extends RenderType {
  RecordingType(String name,List<String> log,boolean fail){super(name,DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,4096,false,false,()->{log.add(name+":setup");if(fail)throw new IllegalStateException("injected setup failure");},()->log.add(name+":clear"));}
 }
 static void fallback()throws Exception {
  config(true);register(true);world();
  for(String mode:List.of("success","reload","forced","closed-glow","closed-core","delete-glow","setup-failure")){
   System.clearProperty("academy.vfx2.mdNearEyeForceFallback");register(true);Object ready=get(MdNearEyeMaterial.class,"ready");
   List<String> log=new ArrayList<>();var original=new RecordingType("original",log,false);var enhanced=new RecordingType("enhanced",log,mode.equals("setup-failure"));
   Class<?> d=Class.forName("cn.academy.port.client.vfx2.MdNearEyeMaterial$DrawState");
   Object state=construct(d,new Class<?>[]{RenderType.class,RenderType.class,ready.getClass()},original,enhanced,ready);
   switch(mode){case "reload"->register(false);case "forced"->System.setProperty("academy.vfx2.mdNearEyeForceFallback","true");case "closed-glow"->((ShaderInstance)get(ready,"glow")).close();case "closed-core"->((ShaderInstance)get(ready,"core")).close();case "delete-glow"->((ShaderInstance)get(ready,"glow")).deletePending=true;}
   exact(d,"setup").invoke(state);yes(get(state,"active")== (mode.equals("success")?enhanced:original),"EFFECTIVE_FALLBACK_IDENTITY "+mode);exact(d,"clear").invoke(state);yes(get(state,"active")==null,"EFFECTIVE_CLEAR_IDENTITY");
   yes(log.getLast().equals(mode.equals("success")?"enhanced:clear":"original:clear"),"EFFECTIVE_CLEAR_ORDER");
   names.add("draw-state-"+mode);
  }
  System.clearProperty("academy.vfx2.mdNearEyeForceFallback");register(true);
 }
 public static void main(String[] args)throws Exception {
  yes(!Boolean.getBoolean("academy.vfx2.lightShieldMaterialPreview"),"PREVIEW_MUST_BE_DISABLED");install();
  String mode=args.length==0?"on":args[0];selecting=!mode.equals("off")&&!mode.equals("forced");config(!mode.equals("off"));register(true);world();
  yes(MdNearEyeMaterial.available(),"PAIR_READY");if(mode.equals("forced"))System.setProperty("academy.vfx2.mdNearEyeForceFallback","true");
  matrix();if(selecting){events();fallback();preselectionFaults();}
  System.out.println("CURRENT_PATH_PASS scenes="+scenes+" selectedDraws="+selected+" eventCases="+eventCases+" materialStateChecks="+stateCases+" assertions="+assertions);
  System.out.println("EVENT_AND_COMPONENT_CASES="+String.join(",",names));
 }
}

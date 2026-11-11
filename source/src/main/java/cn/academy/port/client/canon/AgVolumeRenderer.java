package cn.academy.port.client.canon;

import cn.academy.port.VisualTier;
import cn.academy.port.VisualTierConfig;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import java.nio.ByteBuffer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

/** Ag's original depth-bounded medium, HDR emission and bilateral glow pipeline. Presentation only. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class AgVolumeRenderer {
    private static ShaderInstance volume,composite,surface;
    private static TextureTarget scene,medium;
    private static List<MacroVfxClient.VolumeSample> frame=List.of();
    private static final Set<UUID> OWNED=new HashSet<>();
    private static int sceneDepth=-1,width,height;
    private static boolean captured,failed;
    private static long renderedFrames;
    private static Map<String,Object> diagnostics=Map.of();
    private static java.lang.reflect.Method shadowQuery;
    private static Object iris;
    private static boolean irisAbsent;
    private AgVolumeRenderer(){}
    static boolean available(){return !failed&&volume!=null&&composite!=null&&VisualTierConfig.tier()!=VisualTier.OFF;}
    static boolean owns(UUID cast){return captured&&OWNED.contains(cast);}
    static ShaderInstance surface(int family){
        if(surface==null||VisualTierConfig.tier()==VisualTier.OFF)return net.minecraft.client.renderer.GameRenderer.getPositionTexColorShader();
        var mc=Minecraft.getInstance();f(surface,"Time",mc.level==null?0:(mc.level.getGameTime()%240000)*.05f);f(surface,"Family",family);return surface;
    }
    static boolean shadow(){
        if(irisAbsent)return false;
        try{if(shadowQuery==null){var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");iris=api.getMethod("getInstance").invoke(null);shadowQuery=api.getMethod("isRenderingShadowPass");}return (Boolean)shadowQuery.invoke(iris);}
        catch(ClassNotFoundException absent){irisAbsent=true;return false;}catch(ReflectiveOperationException|LinkageError unavailable){return true;}
    }
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Programs {
        @SubscribeEvent public static void register(RegisterShadersEvent event){
            volume=composite=surface=null;failed=false;
            try{event.registerShader(new ShaderInstance(event.getResourceProvider(),id("ag_volume"),DefaultVertexFormat.POSITION_TEX_COLOR),s->volume=s);
                event.registerShader(new ShaderInstance(event.getResourceProvider(),id("ag_volume_composite"),DefaultVertexFormat.POSITION_TEX_COLOR),s->composite=s);
                event.registerShader(new ShaderInstance(event.getResourceProvider(),id("ag_surface"),DefaultVertexFormat.POSITION_TEX_COLOR),s->surface=s);
            }catch(Exception e){failed=true;LogUtils.getLogger().error("Ag medium/material shaders unavailable",e);}
        }
    }
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("academy",name);}
    private record State(int read,int draw,int[] viewport,boolean blend,boolean depth,boolean cull,boolean mask,int func,int src,int dst,int srcA,int dstA,int texture,ShaderInstance shader){
        static State save(){int[] v=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);return new State(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),v,
                GL11.glIsEnabled(GL11.GL_BLEND),GL11.glIsEnabled(GL11.GL_DEPTH_TEST),GL11.glIsEnabled(GL11.GL_CULL_FACE),GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),RenderSystem.getShader());}
        void restore(){GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            RenderSystem.depthMask(mask);RenderSystem.depthFunc(func);GlStateManager._blendFuncSeparate(src,dst,srcA,dstA);RenderSystem.activeTexture(texture);RenderSystem.setShader(()->shader);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void capture(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        captured=false;OWNED.clear();frame=List.of();var mc=Minecraft.getInstance();
        if(!available()||mc.level==null||mc.player==null||shadow())return;
        double now=mc.level.getGameTime()+event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var samples=new ArrayList<>(MacroVfxClient.volumeSamples(now));samples.addAll(AgEnergyBeam.volumes(now));if(samples.isEmpty())return;
        Vec3 camera=event.getCamera().getPosition();samples.removeIf(s->camera.distanceToSqr(s.centre())>Math.pow(192+s.radius(),2));
        samples.sort(Comparator.comparingDouble(s->s.centre().distanceToSqr(camera)-(s.event().owner().equals(mc.player.getUUID())?20000:0)));
        if(samples.size()>8)samples.subList(8,samples.size()).clear();if(samples.isEmpty())return;
        var saved=State.save();
        try{
            width=saved.viewport[2];height=saved.viewport[3];if(width<2||height<2)return;
            int tier=VisualTierConfig.tier()==VisualTier.CINEMATIC?0:VisualTierConfig.tier()==VisualTier.STANDARD?1:2;
            double pixels=tier==0?1_300_000:tier==1?700_000:300_000;
            double scale=Math.clamp(Math.sqrt(pixels/(width*(double)height*samples.size())),tier==0?.25:.17,tier==0?.75:tier==1?.55:.35);
            int w=Math.max(2,(int)(width*scale)),h=Math.max(2,(int)(height*scale));
            ensureTargets(width,height,w,h);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,saved.draw);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.frameBufferId);
            GlStateManager._glBlitFrameBuffer(saved.viewport[0],saved.viewport[1],saved.viewport[0]+width,saved.viewport[1]+height,0,0,width,height,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);GlStateManager._bindTexture(sceneDepth);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,saved.viewport[0],saved.viewport[1],width,height);
            frame=List.copyOf(samples);for(var sample:frame)OWNED.add(sample.event().cast());captured=true;
        }catch(RuntimeException e){failed=true;LogUtils.getLogger().error("Ag medium scene capture failed",e);}finally{saved.restore();}
    }
    private static void ensureTargets(int w,int h,int lowW,int lowH){
        if(scene==null||scene.width!=w||scene.height!=h){
            if(scene!=null)scene.destroyBuffers();if(sceneDepth>=0)GlStateManager._deleteTexture(sceneDepth);
            scene=new TextureTarget(w,h,false,Minecraft.ON_OSX);
            sceneDepth=GlStateManager._genTexture();GlStateManager._bindTexture(sceneDepth);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_DEPTH_COMPONENT32F,w,h,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,(ByteBuffer)null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        }
        if(medium==null||medium.width!=lowW||medium.height!=lowH){
            if(medium!=null)medium.destroyBuffers();medium=new TextureTarget(lowW,lowH,true,Minecraft.ON_OSX);medium.setFilterMode(GL11.GL_LINEAR);
            GlStateManager._bindTexture(medium.getColorTextureId());GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA16F,lowW,lowH,0,GL11.GL_RGBA,GL11.GL_FLOAT,(ByteBuffer)null);
            medium.bindWrite(false);if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Ag HDR framebuffer is incomplete");
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||!captured||!available()||shadow())return;
        var saved=State.save();
        try{
            medium.setClearColor(0,0,0,0);medium.clear(Minecraft.ON_OSX);medium.bindWrite(true);
            RenderSystem.disableCull();RenderSystem.enableBlend();GlStateManager._blendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_ALWAYS);RenderSystem.depthMask(true);
            var projection=event.getProjectionMatrix();var view=event.getModelViewMatrix();var inverseView=new Matrix4f(view).invert();var inverseProjection=new Matrix4f(projection).invert();
            matrix(volume,"InverseProjection",inverseProjection);matrix(volume,"InverseView",inverseView);matrix(volume,"ViewRotation",view);matrix(volume,"ProjectionMatrix",projection);
            volume.setSampler("SceneDepth",sceneDepth);volume.setSampler("SceneColor",scene.getColorTextureId());
            int quality=VisualTierConfig.tier()==VisualTier.CINEMATIC?48:VisualTierConfig.tier()==VisualTier.STANDARD?32:16;
            f(volume,"Steps",Math.max(12,(float)(quality/Math.sqrt(frame.size()))));
            var camera=event.getCamera().getPosition();var ordered=new ArrayList<>(frame);ordered.sort(Comparator.comparingDouble((MacroVfxClient.VolumeSample s)->s.centre().distanceToSqr(camera)).reversed());
            for(var sample:ordered){
                var v=sample.event();var profile=cn.academy.port.macro.MacroSkillRules.find(v.skill());int family=family(profile.category());
                vector(volume,"Centre",sample.centre().subtract(camera));vector(volume,"SegmentStart",v.from().subtract(camera));vector(volume,"SegmentEnd",v.to().subtract(camera));
                float radius=(float)sample.radius();float height=Math.max(2,AgEffectDesign.height(profile,radius));
                vec4(volume,"Dimensions",radius,height,profile.level(),v.shape()==cn.academy.port.macro.MacroSkillRules.Shape.LINE?1:0);
                vec4(volume,"Animation",(float)(sample.age()*.05),v.phase().ordinal(),(float)sample.opacity(),(float)(Math.floorMod(v.skill().hashCode(),977)/977.0));
                f(volume,"Family",v.skill().equals("plasma_cannon")?1:family);f(volume,"Form",AgEffectDesign.form(profile));draw(volume);
            }
            if(Boolean.getBoolean("academy.qa.signatureBudget"))diagnostics=Map.of("renderedFrames",++renderedFrames,"volumeCount",ordered.size(),"castIds",ordered.stream().map(s->s.event().cast().toString()).toList(),"samplePixels",medium.width*medium.height*ordered.size(),"resolution",medium.width+"x"+medium.height,"stepsPerRay",Math.max(12,(int)(quality/Math.sqrt(frame.size()))));
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,saved.draw);RenderSystem.viewport(saved.viewport[0],saved.viewport[1],saved.viewport[2],saved.viewport[3]);
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.enableBlend();GlStateManager._blendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            composite.setSampler("Medium",medium.getColorTextureId());composite.setSampler("MediumDepth",medium.getDepthTextureId());composite.setSampler("SceneDepth",sceneDepth);
            var texel=composite.getUniform("Texel");if(texel!=null)texel.set(1f/medium.width,1f/medium.height);f(composite,"Glow",VisualTierConfig.tier()==VisualTier.LOW?.24f:.46f);draw(composite);
        }catch(RuntimeException e){failed=true;LogUtils.getLogger().error("Ag medium rendering failed",e);}finally{saved.restore();}
    }
    static int family(String cat){return switch(cat){case "electromaster"->0;case "vecmanip"->2;case "meltdowner"->3;case "teleporter"->4;case "mental_out"->5;case "aero_hand"->6;case "float_dial"->7;case "paper_psychokinesis"->8;case "liquid_shadow"->9;case "hydro_hand"->10;case "dark_matter"->11;default->12;};}
    private static void draw(ShaderInstance shader){RenderSystem.setShader(()->shader);var b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);b.addVertex(-1,-1,0).setUv(0,0).setColor(255,255,255,255);b.addVertex(1,-1,0).setUv(1,0).setColor(255,255,255,255);b.addVertex(1,1,0).setUv(1,1).setColor(255,255,255,255);b.addVertex(-1,1,0).setUv(0,1).setColor(255,255,255,255);BufferUploader.drawWithShader(b.buildOrThrow());}
    private static void f(ShaderInstance s,String n,float x){var u=s.getUniform(n);if(u!=null)u.set(x);}
    private static void vector(ShaderInstance s,String n,Vec3 p){var u=s.getUniform(n);if(u!=null)u.set((float)p.x,(float)p.y,(float)p.z);}
    private static void vec4(ShaderInstance s,String n,float a,float b,float c,float d){var u=s.getUniform(n);if(u!=null)u.set(a,b,c,d);}
    private static void matrix(ShaderInstance s,String n,Matrix4f m){var u=s.getUniform(n);if(u!=null)u.set(m);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){frame=List.of();OWNED.clear();captured=false;if(scene!=null)scene.destroyBuffers();if(medium!=null)medium.destroyBuffers();scene=medium=null;if(sceneDepth>=0)GlStateManager._deleteTexture(sceneDepth);sceneDepth=-1;}
}

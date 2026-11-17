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
        if(surface==null||VisualTierConfig.tier()==VisualTier.OFF||cn.academy.port.client.ShaderPackState.active())return net.minecraft.client.renderer.GameRenderer.getPositionTexColorShader();
        var mc=Minecraft.getInstance();f(surface,"Time",mc.level==null?0:(mc.level.getGameTime()%240000)*.05f);f(surface,"Family",family);return surface;
    }
    public static boolean isShadowPass(){return shadow();}
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
    private record State(int read,int draw,int[] viewport,boolean blend,boolean depth,boolean cull,boolean mask,int func,int src,int dst,int srcA,int dstA,int texture,int tex0Binding,ShaderInstance shader){
        static State save(){int[] v=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);RenderSystem.activeTexture(GL13.GL_TEXTURE0);int tex0=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);RenderSystem.activeTexture(active);
            return new State(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),v,
                GL11.glIsEnabled(GL11.GL_BLEND),GL11.glIsEnabled(GL11.GL_DEPTH_TEST),GL11.glIsEnabled(GL11.GL_CULL_FACE),GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),active,tex0,RenderSystem.getShader());}
        void restore(){GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            RenderSystem.depthMask(mask);RenderSystem.depthFunc(func);GlStateManager._blendFuncSeparate(src,dst,srcA,dstA);RenderSystem.activeTexture(GL13.GL_TEXTURE0);GlStateManager._bindTexture(tex0Binding);RenderSystem.activeTexture(texture);RenderSystem.setShader(()->shader);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void capture(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        captured=false;OWNED.clear();frame=List.of();var mc=Minecraft.getInstance();
        if(!available()||mc.level==null||mc.player==null||shadow()||cn.academy.port.client.ShaderPackState.active())return;
        double now=mc.level.getGameTime()+event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var samples=new ArrayList<>(MacroVfxClient.volumeSamples(now));samples.addAll(AgEnergyBeam.volumes(now));if(samples.isEmpty())return;
        Vec3 camera=event.getCamera().getPosition();samples.removeIf(s->camera.distanceToSqr(s.centre())>Math.pow(192+s.radius(),2));
        samples.sort(Comparator.comparingDouble(s->s.centre().distanceToSqr(camera)-(s.event().owner().equals(mc.player.getUUID())?20000:0)));
        if(samples.size()>8)samples.subList(8,samples.size()).clear();if(samples.isEmpty())return;
        var saved=State.save();
        try{
            width=saved.viewport[2];height=saved.viewport[3];if(width<4||height<4)return;
            int tier=VisualTierConfig.tier()==VisualTier.CINEMATIC?0:VisualTierConfig.tier()==VisualTier.STANDARD?1:2;
            double fullScale=tier==0?.56:tier==1?.42:.28;
            // Two or more volumes share the ray-marching pixel budget. Beams, outlines and
            // contact meshes render separately at full resolution.
            double pixelBudget=1600.0*900*fullScale*fullScale;
            double scale=Math.min(fullScale,Math.sqrt(pixelBudget/(width*(double)height*samples.size())));
            int w=Math.max(4,(int)(width*scale)),h=Math.max(4,(int)(height*scale));
            ensureTargets(width,height,w,h);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,saved.draw);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.frameBufferId);
            GlStateManager._glBlitFrameBuffer(saved.viewport[0],saved.viewport[1],saved.viewport[0]+width,saved.viewport[1]+height,0,0,width,height,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);GlStateManager._bindTexture(sceneDepth);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,saved.viewport[0],saved.viewport[1],width,height);
            frame=List.copyOf(samples);for(var sample:frame)OWNED.add(sample.event().cast());captured=true;
        }catch(RuntimeException e){captured=false;LogUtils.getLogger().warn("Ag medium scene capture skipped on transient resize/state",e);}finally{saved.restore();}
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
                Vec3 relCentre=sample.centre().subtract(camera),relStart=v.from().subtract(camera),relEnd=v.to().subtract(camera);
                vector(volume,"Centre",relCentre);vector(volume,"SegmentStart",relStart);vector(volume,"SegmentEnd",relEnd);
                float radius=(float)sample.radius();float height=Math.max(2,AgEffectDesign.height(profile,radius));
                boolean isLine=v.shape()==cn.academy.port.macro.MacroSkillRules.Shape.LINE&&relEnd.subtract(relStart).length()>1.0;
                vec4(volume,"Dimensions",radius,height,profile.level(),isLine?1:0);
                vec4(volume,"Animation",(float)(sample.age()*.05),v.phase().ordinal(),(float)sample.opacity(),(float)(Math.floorMod(v.skill().hashCode(),977)/977.0));
                f(volume,"Family",v.skill().equals("plasma_cannon")?1:family);f(volume,"Form",AgEffectDesign.form(profile));
                Vec3 boundC=isLine?relStart.add(relEnd).scale(.5):relCentre;
                float boundR=isLine?(float)(relEnd.subtract(relStart).length()*.5+radius*1.5f):Math.max(radius*1.22f,height*.95f);
                drawVolumeQuad(volume,projection,view,boundC,boundR);
            }
            if(Boolean.getBoolean("academy.qa.signatureBudget"))diagnostics=Map.of("renderedFrames",++renderedFrames,"volumeCount",ordered.size(),"castIds",ordered.stream().map(s->s.event().cast().toString()).toList(),"samplePixels",medium.width*medium.height*ordered.size(),"resolution",medium.width+"x"+medium.height,"stepsPerRay",Math.max(12,(int)(quality/Math.sqrt(frame.size()))));
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,saved.draw);RenderSystem.viewport(saved.viewport[0],saved.viewport[1],saved.viewport[2],saved.viewport[3]);
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.enableBlend();GlStateManager._blendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            composite.setSampler("Medium",medium.getColorTextureId());composite.setSampler("MediumDepth",medium.getDepthTextureId());composite.setSampler("SceneDepth",sceneDepth);
            var texel=composite.getUniform("Texel");if(texel!=null)texel.set(1f/medium.width,1f/medium.height);f(composite,"Glow",VisualTierConfig.tier()==VisualTier.LOW?.24f:.46f);draw(composite);
        }catch(RuntimeException e){LogUtils.getLogger().warn("Ag medium rendering skipped on transient state",e);}finally{saved.restore();}
    }
    static int family(String cat){return switch(cat){case "electromaster"->0;case "vecmanip"->2;case "meltdowner"->3;case "teleporter"->4;case "mental_out"->5;case "aero_hand"->6;case "float_dial"->7;case "paper_psychokinesis"->8;case "liquid_shadow"->9;case "hydro_hand"->10;case "dark_matter"->11;default->12;};}
    private static void drawVolumeQuad(ShaderInstance shader,Matrix4f proj,Matrix4f view,Vec3 relCentre,float boundRadius){
        org.joml.Vector4f viewPos=new org.joml.Vector4f((float)relCentre.x,(float)relCentre.y,(float)relCentre.z,1f).mul(view);
        float z=-viewPos.z;
        if(z<=boundRadius+1.2f){draw(shader);return;}
        float r=boundRadius*1.08f;
        float zNear=Math.max(0.5f,z-r);
        float halfX=Math.abs(proj.m00())*r/zNear+.04f,halfY=Math.abs(proj.m11())*r/zNear+.04f;
        float cx=viewPos.x*proj.m00()/z,cy=viewPos.y*proj.m11()/z;
        float minX=Math.max(-1f,cx-halfX),maxX=Math.min(1f,cx+halfX),minY=Math.max(-1f,cy-halfY),maxY=Math.min(1f,cy+halfY);
        if(maxX<=minX||maxY<=minY)return;
        float u0=(minX+1f)*.5f,u1=(maxX+1f)*.5f,v0=(minY+1f)*.5f,v1=(maxY+1f)*.5f;
        RenderSystem.setShader(()->shader);
        var b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        b.addVertex(minX,minY,0).setUv(u0,v0).setColor(255,255,255,255);
        b.addVertex(maxX,minY,0).setUv(u1,v0).setColor(255,255,255,255);
        b.addVertex(maxX,maxY,0).setUv(u1,v1).setColor(255,255,255,255);
        b.addVertex(minX,maxY,0).setUv(u0,v1).setColor(255,255,255,255);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }
    private static void draw(ShaderInstance shader){RenderSystem.setShader(()->shader);var b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);b.addVertex(-1,-1,0).setUv(0,0).setColor(255,255,255,255);b.addVertex(1,-1,0).setUv(1,0).setColor(255,255,255,255);b.addVertex(1,1,0).setUv(1,1).setColor(255,255,255,255);b.addVertex(-1,1,0).setUv(0,1).setColor(255,255,255,255);BufferUploader.drawWithShader(b.buildOrThrow());}
    private static void f(ShaderInstance s,String n,float x){var u=s.getUniform(n);if(u!=null)u.set(x);}
    private static void vector(ShaderInstance s,String n,Vec3 p){var u=s.getUniform(n);if(u!=null)u.set((float)p.x,(float)p.y,(float)p.z);}
    private static void vec4(ShaderInstance s,String n,float a,float b,float c,float d){var u=s.getUniform(n);if(u!=null)u.set(a,b,c,d);}
    private static void matrix(ShaderInstance s,String n,Matrix4f m){var u=s.getUniform(n);if(u!=null)u.set(m);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){frame=List.of();OWNED.clear();captured=false;if(scene!=null)scene.destroyBuffers();if(medium!=null)medium.destroyBuffers();scene=medium=null;if(sceneDepth>=0)GlStateManager._deleteTexture(sceneDepth);sceneDepth=-1;}
}

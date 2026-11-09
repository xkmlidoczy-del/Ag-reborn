package cn.academy.port.client.canon;

import cn.academy.port.client.ShaderPackState;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * FX4 materials: the textures/fx4 pack plus five optional shaders (SH1 refraction, SH2 dissolve, SH3 fluid flow, SH5 dispersion,
 * SH6 hologram). Every shader has a fallback to the plain position/texture/colour path, used when a shader failed to load, the
 * player turned shaders off in the client config, or a shader pack (Iris/Oculus) is active. Presentation only.
 */
final class CanonFx4 {
    enum Mode { ADD, ALPHA, FLOW, DISSOLVE, HOLO, REFRACT, DISPERSE }
    private static final String[] SHADERS={"flow","dissolve","holo","refract","disperse"};
    private static final Map<String,ShaderInstance> LOADED=new HashMap<>();
    private static final Map<String,RenderType> TYPES=new HashMap<>();
    private static RenderTarget scene;
    private CanonFx4() {}

    static ResourceLocation tex(String name){return ResourceLocation.fromNamespaceAndPath("academy","textures/fx4/"+name+".png");}

    /** Whether shader materials may be used this frame. */
    static boolean shaders(){
        return CanonFxConfig.shaders()&&!ShaderPackState.active()&&LOADED.size()==SHADERS.length;
    }
    /** Mode actually drawn: shader modes fall back to plain alpha/additive (refraction to nothing) when shaders are unavailable. */
    static Mode effective(Mode m){
        if(m==Mode.ADD||m==Mode.ALPHA||shaders())return m;
        return switch(m){case HOLO -> Mode.ADD; case REFRACT,DISPERSE -> null; default -> Mode.ALPHA;};
    }
    static boolean late(Mode m){return m==Mode.REFRACT||m==Mode.DISPERSE;}

    static RenderType type(String texture,Mode mode){
        return TYPES.computeIfAbsent(texture+"|"+mode,k->Material.make(texture,mode));
    }
    static void clear(){TYPES.clear();}

    /** Copies the current colour buffer so refraction can sample what is behind it. Called once per frame before the late batch. */
    static void captureScene(){
        RenderTarget main=Minecraft.getInstance().getMainRenderTarget();
        if(scene==null||scene.width!=main.width||scene.height!=main.height){
            if(scene!=null)scene.destroyBuffers();
            scene=new TextureTarget(main.width,main.height,false,Minecraft.ON_OSX);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
        main.bindWrite(false);
    }

    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Shaders {
        @SubscribeEvent public static void register(RegisterShadersEvent event){
            LOADED.clear();TYPES.clear();
            for(String name:SHADERS){
                ShaderInstance candidate=null;
                try{
                    candidate=new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("academy","canon_fx4_"+name),
                            DefaultVertexFormat.POSITION_TEX_COLOR);
                    if(GL20.glGetProgrami(candidate.getId(),GL20.GL_LINK_STATUS)==0){candidate.close();continue;}
                    event.registerShader(candidate,loaded->LOADED.put(name,loaded));
                }catch(Exception|LinkageError unavailable){
                    if(candidate!=null)try{candidate.close();}catch(RuntimeException ignored){}
                }
            }
        }
    }

    private static final class Material extends RenderType {
        private Material(){super("unused",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,4096,false,true,()->{},()->{});}
        /** Source-alpha additive, the same blend as CanonFx's glow layer (the stock ONE/ONE ignores alpha). */
        private static final TransparencyStateShard GLOW_BLEND=new TransparencyStateShard("academy_canon_fx4_glow",
                ()->{RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);},
                ()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
        static RenderType make(String texture,Mode mode){
            var tex=tex(texture);
            EmptyTextureStateShard textures=switch(mode){
                case FLOW -> MultiTextureStateShard.builder().add(tex,true,false).add(tex("common_flow_map"),true,false).build();
                case DISSOLVE -> MultiTextureStateShard.builder().add(tex,true,false).add(tex("common_noise_tile"),true,false).build();
                case REFRACT,DISPERSE -> new EmptyTextureStateShard(()->{
                    RenderSystem.setShaderTexture(0,tex);
                    RenderSystem.setShaderTexture(1,scene==null?Minecraft.getInstance().getMainRenderTarget().getColorTextureId():scene.getColorTextureId());
                },()->{});
                default -> new TextureStateShard(tex,true,false);
            };
            ShaderStateShard shader=switch(mode){
                case ADD,ALPHA -> new ShaderStateShard(GameRenderer::getPositionTexColorShader);
                default -> {String key=mode.name().toLowerCase(java.util.Locale.ROOT);
                    yield new ShaderStateShard(()->LOADED.getOrDefault(key,GameRenderer.getPositionTexColorShader()));}
            };
            boolean additive=mode==Mode.ADD||mode==Mode.HOLO;
            var state=CompositeState.builder().setShaderState(shader).setTextureState(textures)
                    .setTransparencyState(additive?GLOW_BLEND:TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP)
                    .setOverlayState(NO_OVERLAY).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false);
            return RenderType.create("academy_canon_fx4_"+texture+"_"+mode.name().toLowerCase(java.util.Locale.ROOT),
                    DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,4096,false,true,state);
        }
    }
}

package cn.academy.port.client.canon;

import cn.academy.port.VisualTier;
import cn.academy.port.VisualTierConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Original analytic filament, tube and surface response materials. */
public final class AgEffectMaterials{
    private static ShaderInstance filament,tube,impact,glyph;
    private static final Map<Integer,RenderType> TYPES=new HashMap<>();
    private AgEffectMaterials(){}
    public static boolean beams(){return tube!=null&&VisualTierConfig.tier()!=VisualTier.OFF;}
    static ShaderInstance filament(boolean boundary){
        return filament(boundary,false);
    }
    static ShaderInstance filament(boolean boundary,boolean ink){
        if(filament==null||VisualTierConfig.tier()==VisualTier.OFF)return GameRenderer.getPositionTexColorShader();
        set(filament,"Time",time());set(filament,"Boundary",boundary?1:0);set(filament,"Ink",ink?1:0);return filament;
    }
    static float time(){var mc=Minecraft.getInstance();var l=mc.level;return l==null?0:(float)((l.getGameTime()%240000+(mc.isPaused()?0:mc.getTimer().getGameTimeDeltaPartialTick(false)))*.05);}
    static boolean surfaces(){return impact!=null;}
    static boolean glyphs(){return glyph!=null;}
    static void set(ShaderInstance s,String n,float v){var u=s.getUniform(n);if(u!=null)u.set(v);}
    static RenderType type(int mode){return TYPES.computeIfAbsent(mode,Material::make);}
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD) public static final class Programs{
        @SubscribeEvent public static void register(RegisterShadersEvent e){
            filament=tube=impact=glyph=null;TYPES.clear();
            try{e.registerShader(new ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("academy","ag_filament"),DefaultVertexFormat.POSITION_TEX_COLOR),v->filament=v);
                e.registerShader(new ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("academy","ag_energy_tube"),DefaultVertexFormat.POSITION_TEX_COLOR),v->tube=v);
                e.registerShader(new ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("academy","ag_impact_surface"),DefaultVertexFormat.POSITION_TEX_COLOR),v->impact=v);
                e.registerShader(new ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("academy","ag_neuron_glyph"),DefaultVertexFormat.POSITION_TEX_COLOR),v->glyph=v);
            }catch(Exception ex){LogUtils.getLogger().error("Ag analytic materials could not load",ex);}
        }
    }
    private static final class Material extends RenderType{
        Material(){super("unused",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,65536,false,true,()->{},()->{});}
        private static final TransparencyStateShard ADD=new TransparencyStateShard("ag_emission",()->{RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);},()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
        static RenderType make(int mode){
            var state=CompositeState.builder().setShaderState(new ShaderStateShard(()->{
                var selected=mode<3||mode>=6?tube:mode<5?impact:glyph;if(selected==null)return GameRenderer.getPositionTexColorShader();set(selected,"Time",time());set(selected,"Layer",mode>=6?mode-6:mode);set(selected,"NativeTint",mode>=6?1:0);return selected;
            })).setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("academy","textures/port/white.png"),false,false)).setTransparencyState(mode==4?TRANSLUCENT_TRANSPARENCY:ADD).setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false);
            return RenderType.create("ag_energy_"+mode,DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,65536,false,true,state);
        }
    }
}

package cn.academy.port.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL20;

/** Optional NEW151 soft shoulder only; original core/material remains untouched. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
final class RailThunderArraySoftMaterial extends RenderType {
    private static final Map<ResourceLocation,RenderType> TYPES=new HashMap<>();
    private static ShaderInstance shader;
    private static int generation;
    private RailThunderArraySoftMaterial(){super("unused",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,4096,false,true,()->{},()->{});}
    static boolean available(){return shader!=null;}
    /** The linked soft shader (no alpha discard) or null; used by the shared decoration kit. */
    static ShaderInstance shaderOrNull(){return shader;}
    static int generation(){return generation;}
    static RenderType world(ResourceLocation texture){return TYPES.computeIfAbsent(texture,t->create("academy_rail_array_soft",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,4096,false,true,CompositeState.builder()
        .setShaderState(new ShaderStateShard(()->shader==null?GameRenderer.getPositionTexColorShader():shader))
        .setTextureState(new TextureStateShard(t,false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
        .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
        .setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));}
    @SubscribeEvent public static void register(RegisterShadersEvent event){
        int revision=++generation;shader=null;
        if(Boolean.getBoolean("academy.railArrayGrand.forceUnavailable"))return;
        try{
            ShaderInstance next=new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("academy","rail_array_soft"),DefaultVertexFormat.POSITION_TEX_COLOR);
            if(GL20.glGetProgrami(next.getId(),GL20.GL_LINK_STATUS)==0){next.close();return;}
            event.registerShader(next,loaded->{if(revision==generation)shader=loaded;});
        }catch(Exception unavailable){shader=null;}
    }
}

package cn.academy.port.client;

import cn.academy.port.HighPowerVisualConfig;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL20;

/** NEW252-only optional inner edge material. Never changes the original shared shield shader. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class ZeroGapArmorMaterial {
    private static ShaderInstance shader;
    private static volatile int generation;
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("academy","textures/port/white.png");
    private static final ResourceLocation SHIELD=ResourceLocation.fromNamespaceAndPath("academy","textures/effects/mdshield.png");
    private ZeroGapArmorMaterial() {}
    public static int generation(){return generation;}
    public static boolean enabled(){
        try{return !Boolean.getBoolean("academy.zeroGapArmorMaterial.forceFallback")&&shader!=null
                &&HighPowerVisualConfig.SPEC.isLoaded()&&HighPowerVisualConfig.ZERO_GAP_ARMOR_STATE.get();}
        catch(RuntimeException unavailable){return false;}
    }
    public static ShaderInstance get(){return shader!=null?shader:GameRenderer.getPositionTexColorShader();}
    public static void set(FoldedMirrorMesh.Face face){if(shader!=null)shader.getUniform("VisualPhase").set(face.material().phase());}
    public static RenderType type(ResourceLocation texture){
        if(!WHITE.equals(texture))throw new IllegalArgumentException("NEW252 edge material requires pinned white texture");
        return Types.EDGE;
    }
    private static void requireUniform(ShaderInstance candidate,String name,int type,int count){
        var uniform=candidate.getUniform(name);
        if(uniform==null)throw new IllegalStateException("Missing NEW252 uniform "+name);
        // Linking checks GLSL only; malformed JSON declarations can still allocate the wrong native buffer.
        if(uniform.getType()!=type||uniform.getCount()!=count)
            throw new IllegalStateException("Invalid NEW252 uniform declaration "+name);
    }
    private static final class Types extends RenderType {
        private Types(){super("unused",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,4096,false,false,()->{},()->{});}
        static final RenderType EDGE=RenderType.create("academy_zero_gap_armor_edge",DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS,4096,false,false,CompositeState.builder()
                .setShaderState(new ShaderStateShard(ZeroGapArmorMaterial::get))
                .setTextureState(new TextureStateShard(WHITE,true,false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP)
                .setOverlayState(NO_OVERLAY).setOutputState(PARTICLES_TARGET).createCompositeState(false));
    }
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterShadersEvent event){
            shader=null;final int revision=++generation;
            if(Boolean.getBoolean("academy.zeroGapArmorMaterial.forceFallback"))return;
            ShaderInstance candidate=null;
            try {
                ResourceLocation[] textures={WHITE,SHIELD};String[] hashes={
                    "7ac2abfce2be8b46dd9826d597d94edeefc1570b4a992622721b90a1850e3ee9",
                    "5a0cf5f61bea467687820983efb76cfd14f68abbc38667e091b6b0f27965c5dc"};
                for(int i=0;i<textures.length;i++)try(var in=event.getResourceProvider().getResourceOrThrow(textures[i]).open()){
                    byte[] bytes=in.readNBytes(131073);
                    if(bytes.length>131072||!hashes[i].equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))return;
                }
                candidate=new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("academy","zero_gap_armor_edge"),DefaultVertexFormat.POSITION_TEX_COLOR);
                if(GL20.glGetProgrami(candidate.getId(),GL20.GL_LINK_STATUS)==0)throw new IllegalStateException("NEW252 edge shader link failed");
                requireUniform(candidate,"ModelViewMat",Uniform.UT_MAT4,16);
                requireUniform(candidate,"ProjMat",Uniform.UT_MAT4,16);
                requireUniform(candidate,"ColorModulator",Uniform.UT_FLOAT4,4);
                requireUniform(candidate,"VisualPhase",Uniform.UT_FLOAT1,1);
                event.registerShader(candidate,loaded->{if(generation==revision)shader=loaded;});
            } catch(Exception failure){
                if(candidate!=null)candidate.close();
                LogUtils.getLogger().warn("NEW252 optional material unavailable; complete original rendering retained",failure);
            }
        }
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event){
        event.getDispatcher().register(Commands.literal("aca_vfx2").then(Commands.literal("zero_gap_armor")
                .then(Commands.literal("on").executes(c->setEnabled(true)))
                .then(Commands.literal("off").executes(c->setEnabled(false)))
                .then(Commands.literal("status").executes(c->status()))));
    }
    private static int setEnabled(boolean on){if(!HighPowerVisualConfig.SPEC.isLoaded())return 0;
        HighPowerVisualConfig.ZERO_GAP_ARMOR_STATE.set(on);HighPowerVisualConfig.SPEC.save();return status();}
    private static int status(){var p=Minecraft.getInstance().player;
        if(p!=null)p.displayClientMessage(Component.translatable("academy.vfx2.zero_gap_armor.status",enabled(),shader!=null),false);return 1;}
}

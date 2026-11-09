package cn.academy.port.client.vfx2;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Independent additive-only frame pass. Owns no beam lifetime, packet, particle or gameplay state. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class RailgunGrandPass extends RenderType {
    public static final int MAX_BEAMS=8, MAX_VERTICES=384;
    private static final ArrayList<RailgunLaunchGeometry.Quad> QUEUE=new ArrayList<>(MAX_VERTICES/4);
    private static final Set<Object> PREVIOUS=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Object> SELECTED=Collections.newSetFromMap(new IdentityHashMap<>());
    private static ShaderInstance shader;
    private static long generation, frameGeneration;
    private static boolean active;
    private static Vec3 forward=Vec3.ZERO;
    private static int beams,vertices,batches;
    private static final TransparencyStateShard ADDITIVE=new TransparencyStateShard("academy_grand_railgun_additive",()->{
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO,GlStateManager.DestFactor.ONE);
    },()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
    private static final RenderType TYPE=create("academy_grand_railgun",DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,16384,false,false,CompositeState.builder()
            .setShaderState(new ShaderStateShard(RailgunGrandPass::shaderOrFallback))
            .setTransparencyState(ADDITIVE).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
            .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
            .setOutputState(PARTICLES_TARGET).createCompositeState(false));
    private RailgunGrandPass(){super("unused",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,16384,false,false,()->{},()->{});}

    @SubscribeEvent public static void register(RegisterShadersEvent event){
        long requested=++generation;shader=null;clear();
        if(Boolean.getBoolean("academy.vfx2.forceFallback"))return;
        try{
            ShaderInstance candidate=new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("academy","railgun_grand"),DefaultVertexFormat.POSITION_TEX_COLOR);
            if(GL20.glGetProgrami(candidate.getId(),GL20.GL_LINK_STATUS)==0){candidate.close();return;}
            event.registerShader(candidate,loaded->{if(requested==generation){shader=loaded;LogUtils.getLogger().info("Railgun grand additive material loaded; classic core retained");}});
        }catch(Exception failure){LogUtils.getLogger().warn("Railgun grand material unavailable; classic retained",failure);}
    }
    private static ShaderInstance shaderOrFallback(){return shader==null?GameRenderer.getPositionTexColorShader():shader;}
    public static boolean available(){return shader!=null;}
    public static long generation(){return generation;}
    public static void beginFrame(boolean enabled){
        QUEUE.clear();SELECTED.clear();beams=vertices=batches=0;forward=Vec3.ZERO;
        frameGeneration=generation;active=enabled&&available();if(!active)PREVIOUS.clear();
    }
    public static void cameraForward(Vec3 value){forward=value==null?Vec3.ZERO:value;}
    /** Budget preference only; never changes the order of ordinary alpha-blended classic draws. */
    public static double priority(Object key,double distanceSquared){
        if(!Double.isFinite(distanceSquared)||distanceSquared<0)return Double.POSITIVE_INFINITY;
        return active&&PREVIOUS.contains(key)?distanceSquared*.85:distanceSquared;
    }
    public static void selected(Object key){if(active&&key!=null&&SELECTED.size()<MAX_BEAMS)SELECTED.add(key);}
    public static int submit(Vec3 origin,Vec3 direction,double length,Vec3 camera,long age,double widthScale){
        if(!active||!available()||frameGeneration!=generation||beams>=MAX_BEAMS)return 0;
        var quads=RailgunLaunchGeometry.buildAlong(vec(origin),vec(direction),length,vec(camera),vec(forward),age,widthScale);
        int count=quads.size()*4;
        if(count==0||count>48||count>MAX_VERTICES-vertices)return 0;
        for(var quad:quads)for(var vertex:java.util.List.of(quad.a(),quad.b(),quad.c(),quad.d())){
            var point=vertex.position();
            if(!Float.isFinite((float)point.x())||!Float.isFinite((float)point.y())||!Float.isFinite((float)point.z()))return 0;
        }
        // Reserve the complete immutable submission before acquiring a GPU buffer or emitting a vertex.
        vertices+=count;beams++;QUEUE.addAll(quads);return count;
    }
    private static RailgunOverlayGeometry.Vec vec(Vec3 p){return p==null?null:new RailgunOverlayGeometry.Vec(p.x,p.y,p.z);}
    public static void flush(Matrix4f matrix,MultiBufferSource.BufferSource buffers){
        if(!active||!available()||frameGeneration!=generation||QUEUE.isEmpty()){QUEUE.clear();return;}
        var output=buffers.getBuffer(TYPE);
        for(var quad:QUEUE){
            var layer=quad.layer();
            for(var vertex:java.util.List.of(quad.a(),quad.b(),quad.c(),quad.d())){
                var p=vertex.position();
                output.addVertex(matrix,(float)p.x(),(float)p.y(),(float)p.z()).setUv((float)layer.profile(),(float)vertex.v())
                        .setColor(layer.red(),layer.green(),layer.blue(),(int)Math.round(vertex.alpha()*255));
            }
        }
        buffers.endBatch(TYPE);batches++;QUEUE.clear();
    }
    public static void finishFrame(){
        PREVIOUS.clear();if(active&&frameGeneration==generation)PREVIOUS.addAll(SELECTED);
        SELECTED.clear();QUEUE.clear();
    }
    public static void clear(){QUEUE.clear();PREVIOUS.clear();SELECTED.clear();active=false;forward=Vec3.ZERO;beams=vertices=batches=0;}
    public static int beams(){return beams;}public static int vertices(){return vertices;}public static int batches(){return batches;}
}

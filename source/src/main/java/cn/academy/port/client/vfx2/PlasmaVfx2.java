package cn.academy.port.client.vfx2;

import cn.academy.port.ClassicHudConfig;
import cn.academy.port.client.ClassicSkillAlphaShader;
import cn.academy.port.client.ClassicVectorFinalTimeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenshotEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.*;

/** Optional presentation only. Existing accepted classic context owns every instance and event. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class PlasmaVfx2 {
    private static final PlasmaVfx2Timeline.Budget BUDGET=new PlasmaVfx2Timeline.Budget();
    private static long samples,totalNanos,peakNanos;
    private static int peakVertices, submittedVertices, peakSubmittedVertices;
    private static final java.util.List<String> DIAGNOSTIC_FRAMES=new java.util.ArrayList<>(4);
    private static long reportedAuxFailure=Long.MIN_VALUE;
    private PlasmaVfx2(){}
    public static boolean enabled(){try{return ClassicHudConfig.SPEC.isLoaded()&&ClassicHudConfig.PLASMA_VFX2.get();}catch(RuntimeException e){return false;}}
    public static void beginFrame(){BUDGET.beginFrame();submittedVertices=0;DIAGNOSTIC_FRAMES.clear();}
    public static final class Instance {
        public final PlasmaVfx2Timeline.State state;
        public final PlasmaVfx2Timeline.History history=new PlasmaVfx2Timeline.History();
        private long lastClock,resourceGeneration=-1;
        private boolean previouslyEnabled;
        public Instance(float charge,long clock){state=new PlasmaVfx2Timeline.State(charge);lastClock=clock;}
        public void advance(long clock){state.advance(clock-lastClock,false);lastClock=clock;history.expire(state.snapshot().visualMillis());}
        public void ready(long clock){advance(clock);state.acceptedReady();}
        public void go(long clock,Vec3 actual){advance(clock);if(state.acceptedGo())sample(clock,actual);}
        public void sample(long clock,Vec3 actual){advance(clock);if(enabled()&&!state.snapshot().ended())history.offer(actual.x,actual.y,actual.z,state.snapshot().visualMillis());}
        public void end(long clock){advance(clock);state.acceptedEnd();history.clear();}
        public void clear(){state.clear();history.clear();}
        private void resourceFence(){long gen=PlasmaVfx2Material.generation();boolean on=enabled();if(gen!=resourceGeneration||on!=previouslyEnabled)history.clear();resourceGeneration=gen;previouslyEnabled=on;}
    }
    /** True only after complete replacement body submission. Failure before it preserves classic. */
    public static boolean render(Instance instance,List<ClassicVectorFinalTimeline.Ball> sourceBalls,
            Vec3 at,Vec3 camera,float alpha,Matrix4f cameraRelativeRoot,MultiBufferSource.BufferSource buffers,long clock){
        instance.advance(clock);instance.resourceFence();
        if(Boolean.getBoolean("academy.vfx2.plasmaDiagnostics")&&DIAGNOSTIC_FRAMES.size()<4){var observed=instance.state.snapshot();DIAGNOSTIC_FRAMES.add(observed.stage()+" ageMs="+observed.visualMillis()+" alpha="+alpha+" distance="+at.distanceTo(camera)+" anchors="+instance.history.size());}
        if(!Float.isFinite(alpha)||alpha<=0||!enabled()||!PlasmaVfx2Material.available()||!finite(at)||!finite(camera)||at.distanceToSqr(camera)>96*96)return false;
        var phase=instance.state.snapshot();
        if(phase.stage()==PlasmaVfx2Timeline.Stage.INACTIVE)return false;
        // Keep the previous four original bodies for overflow. No partial extra geometry reservation.
        if(!BUDGET.reserve(128))return false;
        long started=System.nanoTime();
        var balls=new ArrayList<Vector4f>(sourceBalls.size());
        double angle=phase.phase01()*Math.PI*2;
        double compression=.70+.30*phase.charge01();
        double breathing=1+.025*phase.readyPulse()-.06*phase.launchEnvelope();
        Matrix4f view=RenderSystem.getModelViewMatrix();
        for(var b:sourceBalls){
            // Integral cycles give a seamless bounded 120-second clock, independent of FPS.
            double h=angle*Math.max(1,Math.round(b.h().speed()*120/(Math.PI*2)))-b.h().phase();
            double v=angle*Math.max(1,Math.round(b.v().speed()*120/(Math.PI*2)))-b.v().phase();
            Vec3 relative=at.add((b.x()+b.h().amp()*Math.sin(h))*compression,
                    (b.y()+b.v().amp()*Math.sin(v))*compression,
                    (b.z()+b.h().amp()*Math.cos(h))*compression).subtract(camera);
            Vector4f p=new Vector4f((float)relative.x,(float)relative.y,(float)relative.z,1);
            cameraRelativeRoot.transform(p);view.transform(p);p.w=(float)(b.size()*breathing);balls.add(p);
        }
        if(!PlasmaVfx2Material.set(alpha*alpha,balls,(long)(phase.phase01()*120000),phase.charge01(),phase.readyPulse(),phase.launchEnvelope()))return false;
        // All world subtraction precedes float conversion. The classic OFF path is unchanged.
        PoseStack body=new PoseStack();body.mulPose(cameraRelativeRoot);
        Vec3 delta=at.subtract(camera);body.translate(delta.x,delta.y,delta.z);
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)Math.toDegrees(-Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        body.mulPose(Axis.YP.rotationDegrees(-yaw+180));body.mulPose(Axis.XP.rotationDegrees(-pitch));
        VertexConsumer out=buffers.getBuffer(PlasmaVfx2Material.renderType());
        Matrix4f m=body.last().pose();
        vertex(out,m,-11,-11,0,0,0,255,255,255,255);vertex(out,m,-11,11,0,0,1,255,255,255,255);
        vertex(out,m,11,11,0,1,1,255,255,255,255);vertex(out,m,11,-11,0,1,0,255,255,255,255);
        buffers.endBatch(PlasmaVfx2Material.renderType());
        submittedVertices+=4;
        try { auxiliary(instance,phase,at,camera,cameraRelativeRoot,buffers,alpha); }
        catch(RuntimeException optionalFailure){instance.history.clear();long generation=PlasmaVfx2Material.generation();if(reportedAuxFailure!=generation){reportedAuxFailure=generation;com.mojang.logging.LogUtils.getLogger().warn("Optional Plasma wisps skipped; actual body retained",optionalFailure);}}
        peakSubmittedVertices=Math.max(peakSubmittedVertices,submittedVertices);
        long elapsed=System.nanoTime()-started;samples++;totalNanos+=elapsed;peakNanos=Math.max(peakNanos,elapsed);peakVertices=Math.max(peakVertices,BUDGET.verticesUsed());
        return true;
    }
    private static void auxiliary(Instance instance,PlasmaVfx2Timeline.Snapshot phase,Vec3 at,Vec3 camera,Matrix4f root,MultiBufferSource.BufferSource buffers,float alpha){
        if(phase.ended()||alpha<=0)return;
        VertexConsumer out=buffers.getBuffer(Material.WISP);
        // Two open circulation strokes, never a collision ring or a target/impact marker.
        double strength=(.18+.12*phase.readyPulse()+.18*phase.launchEnvelope())*alpha;
        double turn=phase.phase01()*Math.PI*2*12,radius=2.6+.8*phase.charge01()-.4*phase.launchEnvelope();
        Vec3 normal=at.subtract(camera).normalize();Vec3 side=normal.cross(new Vec3(0,1,0));if(side.lengthSqr()<1e-8)side=normal.cross(new Vec3(1,0,0));side=side.normalize();Vec3 up=normal.cross(side).normalize();
        for(int stroke=0;stroke<2;stroke++)for(int i=0;i<4;i++){
            double a=turn+stroke*Math.PI+i*.27,b=a+.27;
            Vec3 from=at.add(side.scale(Math.cos(a)*radius)).add(up.scale(Math.sin(a)*radius));
            Vec3 to=at.add(side.scale(Math.cos(b)*radius)).add(up.scale(Math.sin(b)*radius));
            if(clearSegment(from,to))ribbon(out,root,from,to,camera,.12,strength*Math.sin(Math.PI*(i+.5)/4),179,171,246);
        }
        var points=instance.history.points();int emitted=0;
        for(int i=Math.max(1,points.size()-24);i<points.size()&&emitted<24;i++){
            var a=points.get(i-1);var b=points.get(i);long age=phase.visualMillis()-b.visualMillis();if(age<0||age>450)continue;
            Vec3 from=vec(a.position()),to=vec(b.position());if(!clearSegment(from,to))continue;
            double fade=(1-age/450.0)*.22*alpha;
            ribbon(out,root,from,to,camera,.32,fade,142,183,255);emitted++;
        }
        buffers.endBatch(Material.WISP);
    }
    private static boolean clearSegment(Vec3 a,Vec3 b){var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||!finite(a)||!finite(b))return false;double d=a.distanceTo(b);if(d>8)return false;for(int i=0;i<=(int)Math.ceil(d);i++){Vec3 p=a.lerp(b,d==0?0:Math.min(1,i/d));if(!mc.level.hasChunkAt(BlockPos.containing(p)))return false;}return mc.level.clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player)).getType()==HitResult.Type.MISS;}
    private static Vec3 vec(PlasmaVfx2Timeline.Vec p){return new Vec3(p.x(),p.y(),p.z());}
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    private static void ribbon(VertexConsumer out,Matrix4f root,Vec3 a,Vec3 b,Vec3 camera,double width,double alpha,int r,int g,int blue){
        Vec3 d=b.subtract(a);if(d.lengthSqr()<1e-10)return;Vec3 side=d.cross(a.add(b).scale(.5).subtract(camera));if(side.lengthSqr()<1e-10)side=d.cross(Math.abs(d.y)<Math.abs(d.x)?new Vec3(0,1,0):new Vec3(1,0,0));if(side.lengthSqr()<1e-10)return;side=side.normalize().scale(width);double near=Math.clamp((Math.min(a.distanceTo(camera),b.distanceTo(camera))-.4)/1.4,0,1);int opacity=(int)Math.round(Math.clamp(alpha*near,0,.5)*255);
        Vec3[] p={a.subtract(side).subtract(camera),a.add(side).subtract(camera),b.add(side).subtract(camera),b.subtract(side).subtract(camera)};
        submittedVertices+=4;
        for(int i=0;i<4;i++)vertex(out,root,p[i].x,p[i].y,p[i].z,i<2?0:1,i==0||i==3?0:1,r,g,blue,opacity);
    }
    private static void vertex(VertexConsumer out,Matrix4f m,double x,double y,double z,float u,float v,int r,int g,int b,int a){out.addVertex(m,(float)x,(float)y,(float)z).setUv(u,v).setColor(r,g,b,a);}
    private static final class Material extends RenderType{
        private Material(){super("unused",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,4096,false,false,()->{},()->{});}
        static final RenderType WISP=create("academy_plasma_vfx2_wisp",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,4096,false,true,CompositeState.builder().setShaderState(new ShaderStateShard(ClassicSkillAlphaShader::get)).setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("academy","textures/effects/glow_line.png"),false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY).setOutputState(PARTICLES_TARGET).createCompositeState(false));
    }
    /** Passive native F2 witness. Never edits/cancels/captures an image or changes effect state. */
    @SubscribeEvent public static void screenshot(ScreenshotEvent event){if(Boolean.getBoolean("academy.vfx2.plasmaDiagnostics"))com.mojang.logging.LogUtils.getLogger().info("Plasma F2 witness file={} enhanced={} frames={}",event.getScreenshotFile().getName(),enabled(),java.util.List.copyOf(DIAGNOSTIC_FRAMES));}
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent e){e.getDispatcher().register(Commands.literal("aca_vfx2").then(Commands.literal("plasma").then(Commands.literal("on").executes(c->set(true))).then(Commands.literal("off").executes(c->set(false))).then(Commands.literal("status").executes(c->status()))));}
    private static int set(boolean on){if(!ClassicHudConfig.SPEC.isLoaded())return 0;ClassicHudConfig.PLASMA_VFX2.set(on);ClassicHudConfig.SPEC.save();return status();}
    private static int status(){var p=Minecraft.getInstance().player;if(p!=null)p.displayClientMessage(Component.translatable("academy.vfx2.plasma.status",enabled(),PlasmaVfx2Material.available(),BUDGET.bodiesUsed(),submittedVertices,peakSubmittedVertices,samples==0?0:totalNanos/samples/1000,peakNanos/1000),false);return 1;}
}

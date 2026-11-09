package cn.academy.port.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import cn.academy.port.client.WindPressureLiftMesh.Point;
import cn.academy.port.client.WindPressureLiftFlowRibbon.Draw;

/** Optional presentation only: consumes the same already authenticated visible controllers. */
public final class WindPressureLiftFlowRenderer {
    private static final ResourceLocation BAND=ResourceLocation.fromNamespaceAndPath("academy","textures/effects/tornado_ring.png"),
            SPINE=ResourceLocation.fromNamespaceAndPath("academy","textures/effects/glow_line.png");
    private static ByteBufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private WindPressureLiftFlowRenderer() {}
    public static void clear() {
        try { if(buffers!=null) for(var type:List.copyOf(Material.TYPES.values())) flush(type); }
        finally { if(storage!=null) storage.close(); buffers=null;storage=null;Material.TYPES.clear(); }
    }
    public static boolean render(List<WindPressureLiftState.Controller> controllers,RenderLevelStageEvent event,float partial) {
        if(!WindPressureLiftFlowMaterial.enabled() || controllers==null || controllers.isEmpty()
                || controllers.size()>WindPressureLiftMesh.MAX_CONTEXTS) return false;
        decorate(controllers,event,partial);
        Vec3 camera=event.getCamera().getPosition();Point eye=point(camera);
        var look=event.getCamera().getLookVector();Point forward=new Point(look.x(),look.y(),look.z());
        var left=event.getCamera().getLeftVector();Point fallback=new Point(left.x(),left.y(),left.z());
        var budget=new WindPressureLiftMesh.Budget();var segments=new ArrayList<Draw>();
        for(var c:controllers) {
            var shape=WindPressureLiftFlowMesh.shape(c,partial);
            if(shape.segments().isEmpty() || !budget.take(shape.vertexCount())) return false;
            var joined=WindPressureLiftFlowRibbon.build(shape,eye,fallback);
            if(joined.size()!=shape.segments().size()) return false;
            segments.addAll(joined);
        }
        // Shared shoulders are already camera-relative; never subtract the camera twice.
        Point origin=new Point(0,0,0);
        segments.sort(Comparator.comparingDouble((Draw d)->d.segment().a().distanceSquared(origin)
                +d.segment().b().distanceSquared(origin)).reversed());
        if(buffers==null) {storage=new ByteBufferBuilder(65536);buffers=MultiBufferSource.immediate(storage);}
        var poses=event.getPoseStack();var mv=RenderSystem.getModelViewStack();float[] color=RenderSystem.getShaderColor().clone();
        poses.pushPose();mv.pushMatrix();boolean completed=false,submitted=false;
        try {
            RenderSystem.setShaderColor(1,1,1,1);mv.set(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();
            Matrix4f matrix=poses.last().pose();
            for(boolean spine:new boolean[]{false,true}) {
                var type=Material.type(spine);VertexConsumer out=buffers.getBuffer(type);
                for(var segment:segments) emit(out,matrix,segment,forward,spine);
                submitted=true;flush(type);
            }
            completed=true;return true;
        } catch(RuntimeException unavailable) {
            WindPressureLiftFlowMaterial.failed();
            // A partially submitted frame cannot also spend the classic geometry budget.
            // Retire the enhancement; the next frame resumes the complete original renderer.
            return submitted;
        }
        finally {
            try {if(!completed){buffers=null;if(storage!=null)storage.close();storage=null;}}
            finally {poses.popPose();mv.popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);}
        }
    }
    /** CINEMATIC helical streamlines, beads, pressure rings and dust for each live lift column. */
    private static void decorate(List<WindPressureLiftState.Controller> controllers,RenderLevelStageEvent event,float partial) {
        var tier=cn.academy.port.VisualTierConfig.tier();
        if(tier.layers<3)return;
        var parts=new FxLib.Builder(); var mc=net.minecraft.client.Minecraft.getInstance();
        double time=mc.level==null?0:mc.level.getGameTime()+partial;
        for(var c:controllers) {
            var a=c.frame().anchor(); var d=c.frame().direction();
            parts.add(WindPressureLiftDecor.fx(new cn.academy.port.client.RailThunderArrayGeometry.Point(a.x(),a.y(),a.z()),
                    new cn.academy.port.client.RailThunderArrayGeometry.Point(d.x(),d.y(),d.z()),c.age(),time,tier));
        }
        VisualKit.render(event,parts.build(),tier,WindPressureLiftDecor.MAX_QUADS);
    }
    // Native RenderType.draw has no exception-finally. Explicit cleanup also covers failed GPU upload/draw.
    private static void flush(RenderType type) {
        try {buffers.endBatch(type);} finally {type.clearRenderState();}
    }
    private static void emit(VertexConsumer out,Matrix4f matrix,Draw draw,Point forward,boolean spine) {
        var quad=WindPressureLiftFlowRibbon.clip(draw,forward,spine?WindPressureLiftFlowMesh.CORE_WIDTH_SCALE:1);
        if(quad==null) return;
        var s=quad.segment();
        int gray=spine?250:225;
        int limit=spine?210:125;
        int aa=(int)Math.round(Math.min(limit,s.alphaFrom())*WindPressureLiftMesh.nearFade(s.a()));
        int ab=(int)Math.round(Math.min(limit,s.alphaTo())*WindPressureLiftMesh.nearFade(s.b()));
        vertex(out,matrix,vec(quad.aMinus()),(float)s.uFrom(),1,gray,aa);
        vertex(out,matrix,vec(quad.aPlus()),(float)s.uFrom(),0,gray,aa);
        vertex(out,matrix,vec(quad.bPlus()),(float)s.uTo(),0,gray,ab);
        vertex(out,matrix,vec(quad.bMinus()),(float)s.uTo(),1,gray,ab);
    }
    private static void vertex(VertexConsumer out,Matrix4f m,Vec3 p,float u,float v,int gray,int alpha) {
        out.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(gray,gray,gray,alpha);
    }
    private static Point point(Vec3 v){return new Point(v.x,v.y,v.z);}
    private static Vec3 vec(Point p){return new Vec3(p.x(),p.y(),p.z());}
    private static final class Material extends RenderType {
        private static final Map<Boolean,RenderType> TYPES=new HashMap<>();
        private Material(String n,VertexFormat f,VertexFormat.Mode m,int s,boolean c,boolean sorted,Runnable set,Runnable reset){super(n,f,m,s,c,sorted,set,reset);}
        static RenderType type(boolean spine){return TYPES.computeIfAbsent(spine,p->create("academy_wind_lift_flow_"+p,
                DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,8192,false,true,
                CompositeState.builder().setShaderState(new ShaderStateShard(WindPressureLiftFlowMaterial::shader))
                        .setTextureState(new TextureStateShard(p?SPINE:BAND,false,false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                        .setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY).setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));}
    }
}

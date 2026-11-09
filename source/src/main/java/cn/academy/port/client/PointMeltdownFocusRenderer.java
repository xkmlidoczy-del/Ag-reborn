package cn.academy.port.client;

import cn.academy.port.client.BeamEtchingMesh.Box;
import cn.academy.port.client.BeamEtchingMesh.Mark;
import cn.academy.port.client.BeamEtchingMesh.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Optional complete-frame replacement, consuming only the existing authenticated visible marks. */
public final class PointMeltdownFocusRenderer {
    public static final int MAX_VERTICES = 4096;
    public record Request(Point origin, Mark mark, Box surface, int focusTicks, boolean focusing,
                          boolean confirmed, int age, double visualTicks) {}
    private static ByteBufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private PointMeltdownFocusRenderer() {}
    public static void clear() {
        try { if (buffers != null) for (var t : List.copyOf(Material.TYPES.values())) flush(t); }
        finally { if (storage != null) storage.close(); storage = null; buffers = null; Material.TYPES.clear(); }
    }
    public static boolean render(List<Request> requests, RenderLevelStageEvent event) {
        if (!PointMeltdownFocusMaterial.enabled() || requests == null || requests.size() > 4) return false;
        int generation = PointMeltdownFocusMaterial.generation();
        var camera = event.getCamera().getPosition(); var direction = event.getCamera().getLookVector();
        Point eye = new Point(camera.x, camera.y, camera.z), look = new Point(direction.x(), direction.y(), direction.z());
        List<PointMeltdownFocusMesh.Face> faces = new ArrayList<>();
        try {
            for (Request r : requests) {
                var group = PointMeltdownFocusMesh.build(r.origin(), r.mark(), r.surface(), eye, look,
                        r.focusTicks(), r.focusing(), r.confirmed(), r.age(), r.visualTicks());
                // One hard scene total after both clipping stages. No classic draw is added to this budget.
                if ((faces.size() + group.size()) * 4 > MAX_VERTICES) return false;
                faces.addAll(group);
            }
        } catch (RuntimeException unavailable) { PointMeltdownFocusMaterial.failed(); return false; }
        if (!PointMeltdownFocusMaterial.enabled() || generation != PointMeltdownFocusMaterial.generation()) return false;
        if (faces.isEmpty()) return true;
        if (buffers == null) { storage = new ByteBufferBuilder(131072); buffers = MultiBufferSource.immediate(storage); }
        var modelView = RenderSystem.getModelViewStack(); var color = RenderSystem.getShaderColor().clone();
        modelView.pushMatrix(); boolean submitted = false, complete = false; RenderType current = null;
        try {
            modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix(); RenderSystem.setShaderColor(1, 1, 1, 1);
            Matrix4f matrix = event.getPoseStack().last().pose();
            // Preserve the mesh's shoulder-to-core layering; do not regroup translucent faces by texture.
            for (var face : faces) {
                RenderType type = Material.type(face.texture());
                if (current != null && current != type) { submitted = true; flush(current); }
                current = type; VertexConsumer out = buffers.getBuffer(type);
                for (var vertex : face.vertices()) {
                    Point relative = vertex.point().subtract(eye);
                    out.addVertex(matrix, (float)relative.x(), (float)relative.y(), (float)relative.z())
                            .setUv((float)vertex.u(), (float)vertex.v())
                            .setColor(face.rgb() >>> 16 & 255, face.rgb() >>> 8 & 255, face.rgb() & 255, face.alpha());
                }
            }
            if (current != null) { submitted = true; flush(current); }
            complete = true; return true;
        } catch (RuntimeException unavailable) {
            PointMeltdownFocusMaterial.failed();
            // A partly submitted frame must never also submit the full classic scene. Next frame falls back.
            return submitted;
        } finally {
            try { if (!complete) { buffers = null; if (storage != null) storage.close(); storage = null; } }
            finally { modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]); }
        }
    }
    private static void flush(RenderType type) { try { buffers.endBatch(type); } finally { type.clearRenderState(); } }
    private static final class Material extends RenderType {
        static final Map<Integer, RenderType> TYPES = new HashMap<>();
        private Material(String n,VertexFormat f,VertexFormat.Mode m,int s,boolean c,boolean sorted,Runnable set,Runnable reset){super(n,f,m,s,c,sorted,set,reset);}
        static RenderType type(int texture) {
            if (texture < -1 || texture > 5) throw new IllegalArgumentException("Unknown NEW233 material");
            return TYPES.computeIfAbsent(texture, t -> create("academy_point_focus_" + t,
                    DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 8192, false, true,
                    CompositeState.builder().setShaderState(new ShaderStateShard(PointMeltdownFocusMaterial::shader))
                            .setTextureState(new TextureStateShard(PointMeltdownFocusMaterial.TEXTURES[t + 1],false,false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                            .setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY).setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
    }
}

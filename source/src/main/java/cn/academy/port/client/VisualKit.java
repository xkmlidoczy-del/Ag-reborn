package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Decal;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Quad;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Shared presentation kit for STANDARD/CINEMATIC decoration: projects pure strokes and sprites with the NEW151
 * camera rules and submits them as soft/additive quads. Purely cosmetic; callers own lifecycle, authority and caps.
 */
public final class VisualKit {
    private static MultiBufferSource.BufferSource ownBuffers;

    private VisualKit() {}

    /**
     * One-call renderer for effects without their own buffer plumbing: projects {@code parts} against the event camera and submits
     * them in a self-contained, restored GL state. Trimmed (never refused) to {@code maxQuads}; a failure draws nothing.
     */
    public static void render(RenderLevelStageEvent event, FxLib.Parts parts, VisualTier tier, int maxQuads) {
        if (tier == null || !tier.enhanced() || parts == null || !RailThunderArrayGrandMaterial.kitReady()) return;
        var camera = event.getCamera();
        var l = camera.getLookVector(); var u = camera.getUpVector(); var c = camera.getPosition();
        List<Quad> quads = projectParts(parts, new Point(c.x, c.y, c.z), new Point(l.x(), l.y(), l.z()), new Point(u.x(), u.y(), u.z()), tier, maxQuads);
        if (quads.isEmpty()) return;
        if (ownBuffers == null) ownBuffers = MultiBufferSource.immediate(new ByteBufferBuilder(262144));
        var poses = event.getPoseStack(); var modelView = RenderSystem.getModelViewStack();
        float[] color = RenderSystem.getShaderColor().clone();
        modelView.pushMatrix(); poses.pushPose();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            submit(ownBuffers, poses.last().pose(), quads);
        } finally {
            try { ownBuffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }

    /** Projects a whole {@link FxLib.Parts} (decals first, then sprites, then strokes), trimmed to {@code maxQuads}. */
    static List<Quad> projectParts(FxLib.Parts parts, Point camera, Point look, Point up, VisualTier tier, int maxQuads) {
        if (tier == null || !tier.enhanced() || maxQuads <= 0) return List.of();
        List<Quad> out = new ArrayList<>();
        for (Decal decal : parts.decals()) out.addAll(RailThunderArrayGrandGeometry.projectDecal(decal, camera, look, up, tier));
        for (Billboard sprite : parts.sprites()) out.addAll(RailThunderArrayGrandGeometry.projectBillboard(sprite, camera, look, up, tier));
        for (Stroke stroke : parts.strokes()) out.addAll(RailThunderArrayGrandGeometry.project(stroke, camera, look, up, tier));
        return out.size() <= maxQuads ? out : List.copyOf(out.subList(0, maxQuads));
    }

    /** Same projection from plain points, usable by headless regression tests. */
    static List<Quad> projectPure(List<Stroke> strokes, List<Billboard> sprites, Point camera, Point look, Point up, VisualTier tier, int maxQuads) {
        if (tier == null || !tier.enhanced() || maxQuads <= 0) return List.of();
        List<Quad> out = new ArrayList<>();
        for (Billboard sprite : sprites) out.addAll(RailThunderArrayGrandGeometry.projectBillboard(sprite, camera, look, up, tier));
        for (Stroke stroke : strokes) out.addAll(RailThunderArrayGrandGeometry.project(stroke, camera, look, up, tier));
        return out.size() <= maxQuads ? out : List.copyOf(out.subList(0, maxQuads));
    }

    /** Quads for one decoration layer set, trimmed (never refused) to {@code maxQuads}; sprites keep priority over strokes. */
    public static List<Quad> project(List<Stroke> strokes, List<Billboard> sprites, Vec3 camera, Vec3 look, Vec3 up,
                                     VisualTier tier, int maxQuads) {
        if (tier == null || !tier.enhanced() || maxQuads <= 0) return List.of();
        return projectPure(strokes, sprites, new Point(camera.x, camera.y, camera.z), new Point(look.x, look.y, look.z), new Point(up.x, up.y, up.z), tier, maxQuads);
    }

    /** Submits already-projected quads (camera-relative corners) through the soft/additive materials. */
    public static void submit(MultiBufferSource buffers, Matrix4f matrix, List<Quad> quads) {
        if (!RailThunderArrayGrandMaterial.kitReady()) return;
        for (Quad quad : quads) {
            int texture = quad.texture();
            RenderType type = RailThunderArrayGrandGeometry.additive(texture)
                    ? VisualAdditiveMaterial.additive(RailThunderArrayGrandMaterial.texture(texture))
                    : texture == RailThunderArrayGrandGeometry.GLOW ? RailThunderArraySoftMaterial.world(RailThunderArrayGrandMaterial.texture(texture))
                    : ClassicRenderTypes.world(RailThunderArrayGrandMaterial.texture(texture));
            VertexConsumer out = buffers.getBuffer(type);
            float lo = RailThunderArrayGrandGeometry.lineProfile(texture) ? 8f / 32 : 0, hi = RailThunderArrayGrandGeometry.lineProfile(texture) ? 24f / 32 : 1;
            vertex(out, matrix, quad.a(), quad.minV(), lo, quad.rgb(), quad.alpha());
            vertex(out, matrix, quad.b(), quad.minV(), hi, quad.rgb(), quad.alpha());
            vertex(out, matrix, quad.c(), quad.maxV(), hi, quad.rgb(), quad.alpha());
            vertex(out, matrix, quad.d(), quad.maxV(), lo, quad.rgb(), quad.alpha());
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Point p, float u, float v, int rgb, int alpha) {
        out.addVertex(matrix, (float) p.x(), (float) p.y(), (float) p.z()).setUv(u, v).setColor((rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255, alpha);
    }
}

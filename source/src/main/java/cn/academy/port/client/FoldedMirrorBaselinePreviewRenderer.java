package cn.academy.port.client;

import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Plate;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Opt-in staged comparison adapter for the unmodified bc46c65a (.22) material pipeline.
 * Receives the same immutable historical receipt, camera and selected age as the candidate.
 * No event subscription: production rendering never calls this unless staged baseline is selected.
 */
final class FoldedMirrorBaselinePreviewRenderer {
    private static final ResourceLocation WHITE = texture("port/white");
    private static final ResourceLocation[] SMALL_RAY = {texture("effects/mdray_small/blend_in"), texture("effects/mdray_small/tile"), texture("effects/mdray_small/blend_out")};
    private static MultiBufferSource.BufferSource buffers;
    private FoldedMirrorBaselinePreviewRenderer() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }

    static void render(RenderLevelStageEvent event, ClientLevel world, FoldedMirrorState.Frame frame, int age) {
        if (world == null || frame == null) return;
        Vec3 camera = event.getCamera().getPosition();
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(262144));
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        try {
            modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = event.getPoseStack().last().pose(); var facing = event.getCamera().getLookVector();
            Vector look = FoldedMirrorGeometry.unit(new Vector(facing.x(), facing.y(), facing.z())).orElse(null), eye = point(camera);
            var budget = new FoldedMirrorBaselineMesh.Budget();
            for (Plate plate : frame.plates()) if (world.hasChunkAt(BlockPos.containing(vec(plate.center()))))
                for (var face : FoldedMirrorBaselineMesh.plate(plate, false)) draw(matrix, face, eye, look, budget);
            if (loadedFrame(world, frame))
                for (var face : FoldedMirrorBaselineMesh.path(frame, eye, look, age)) draw(matrix, face, eye, look, budget);
        } finally {
            try { buffers.endBatch(); } finally { modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); }
        }
    }
    /** Use the proven exact chunk corridor, including corner grazes, before any world geometry is emitted. */
    private static boolean loadedPath(ClientLevel world, Vector from, Vector to) {
        if (world == null) return false;
        var chunks = BeamEtchingMesh.crossedChunks(new BeamEtchingMesh.Point(from.x(), from.y(), from.z()), new BeamEtchingMesh.Point(to.x(), to.y(), to.z()));
        if (chunks.isEmpty()) return false;
        for (var chunk : chunks) if (!world.hasChunkAt(new BlockPos(chunk.x() * 16, (int)Math.floor(from.y()), chunk.z() * 16))) return false;
        return true;
    }
    private static boolean loadedFrame(ClientLevel world, FoldedMirrorState.Frame frame) {
        for (var segment : frame.path()) if (!loadedPath(world, segment.start(), segment.end())) return false;
        return true;
    }
    private static void draw(Matrix4f matrix, FoldedMirrorBaselineMesh.Face face, Vector eye, Vector look, FoldedMirrorBaselineMesh.Budget budget) {
        if (face.alpha() <= 0) return;
        for (var clipped : FoldedMirrorBaselineMesh.nearClip(face, eye, look)) {
            if (!budget.take()) return;
            VertexConsumer out = buffers.getBuffer(ClassicRenderTypes.world(clipped.texture() < 0 ? WHITE : SMALL_RAY[clipped.texture()]));
            vertex(out, matrix, clipped.a().subtract(eye), 0, 0, clipped.color(), clipped.alpha());
            vertex(out, matrix, clipped.b().subtract(eye), 0, 1, clipped.color(), clipped.alpha());
            vertex(out, matrix, clipped.c().subtract(eye), 1, 1, clipped.color(), clipped.alpha());
            vertex(out, matrix, clipped.d().subtract(eye), 1, 0, clipped.color(), clipped.alpha());
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vector p, float u, float v, int rgb, int alpha) {
        out.addVertex(matrix, (float)p.x(), (float)p.y(), (float)p.z()).setUv(u, v).setColor(rgb >>> 16 & 255, rgb >>> 8 & 255, rgb & 255, alpha);
    }
    private static Vector point(Vec3 point) { return new Vector(point.x, point.y, point.z); }
    private static Vec3 vec(Vector point) { return new Vec3(point.x(), point.y(), point.z()); }
}

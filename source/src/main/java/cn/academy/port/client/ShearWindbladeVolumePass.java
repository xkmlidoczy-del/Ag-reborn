package cn.academy.port.client;

import cn.academy.port.highpower.core.ShearWindbladeGeometry.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** One bounded translucent pass. All geometry is planned before replacing any .61 submission. */
public final class ShearWindbladeVolumePass {
    private static ByteBufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private static final double NEAR_REFUSAL = .35, NEAR_FULL = .75;
    private ShearWindbladeVolumePass() {}
    static void clear() {
        if (buffers != null) buffers.endBatch();
        if (storage != null) storage.close();
        buffers = null;
        storage = null;
    }
    public static boolean tryRender(RenderLevelStageEvent event, List<ShearWindbladeState.Controller> controllers,
                                    Predicate<ShearWindbladeState.Controller> eligible, double partial) {
        if (!ShearWindbladeVolumeMaterial.enabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || controllers == null || controllers.size() > ShearWindbladeState.MAX_CONTROLLERS
                || !Double.isFinite(partial)) return false;
        int revision = ShearWindbladeVolumeMaterial.generation();
        var camera = event.getCamera().getPosition();
        var look = event.getCamera().getLookVector();
        var right = event.getCamera().getLeftVector();
        Point originCamera = point(camera), viewRight = new Point(right.x(), right.y(), right.z());
        var meshes = new ArrayList<ShearWindbladeVolumeGeometry.Mesh>();
        int vertices = 0;
        double nearest = Double.POSITIVE_INFINITY;
        try {
            for (var controller : controllers) if (eligible.test(controller)) {
                var shape = ShearWindbladeRenderGeometry.shape(controller, partial);
                if (shape.strokes().isEmpty()) continue;
                var mesh = ShearWindbladeVolumeGeometry.mesh(shape, controller.origin(), originCamera, viewRight);
                if (mesh.vertexCount() == 0 || mesh.vertexCount() > ShearWindbladeVolumeGeometry.MAX_SCENE_VERTICES - vertices) return false;
                vertices += mesh.vertexCount();
                double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
                double maxX = Double.NEGATIVE_INFINITY, maxZ = maxX;
                for (var quad : mesh.quads()) for (var vertex : quad.vertices()) {
                    Point p = vertex.position();
                    double depth = p.x() * look.x() + p.y() * look.y() + p.z() * look.z();
                    if (!p.finite() || !Double.isFinite(depth) || depth < NEAR_REFUSAL) return false;
                    nearest = Math.min(nearest, depth);
                    minX = Math.min(minX, camera.x + p.x()); maxX = Math.max(maxX, camera.x + p.x());
                    minY = Math.min(minY, camera.y + p.y());
                    minZ = Math.min(minZ, camera.z + p.z()); maxZ = Math.max(maxZ, camera.z + p.z());
                }
                // Whole mesh availability is reserved before any draw; one missing shoulder cannot erase another classic core.
                for (int x = Math.floorDiv((int)Math.floor(minX), 16); x <= Math.floorDiv((int)Math.floor(maxX), 16); x++)
                    for (int z = Math.floorDiv((int)Math.floor(minZ), 16); z <= Math.floorDiv((int)Math.floor(maxZ), 16); z++)
                        if (!mc.level.hasChunkAt(new BlockPos(x * 16, (int)Math.floor(minY), z * 16))) return false;
                meshes.add(mesh);
            }
        } catch (RuntimeException invalidGeometry) { return false; }
        if (vertices == 0 || !ShearWindbladeVolumeMaterial.enabled() || revision != ShearWindbladeVolumeMaterial.generation()) return false;
        double t = Math.max(0, Math.min(1, (nearest - NEAR_REFUSAL) / (NEAR_FULL - NEAR_REFUSAL)));
        double nearFade = t * t * (3 - 2 * t);
        if (buffers == null) { storage = new ByteBufferBuilder(65536); buffers = MultiBufferSource.immediate(storage); }
        var poses = event.getPoseStack();
        var modelView = RenderSystem.getModelViewStack();
        float[] color = RenderSystem.getShaderColor().clone();
        modelView.pushMatrix(); poses.pushPose();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            var matrix = poses.last().pose();
            var out = buffers.getBuffer(Types.VOLUME);
            // One texture/pass permits native translucent quad sorting across core, pressure and shoulder.
            for (var mesh : meshes) for (var quad : mesh.quads()) {
                double layerFade = quad.material() == ShearWindbladeVolumeGeometry.Material.CORE ? 1 : nearFade;
                for (var vertex : quad.vertices()) {
                    Point p = vertex.position();
                    int alpha = (int)Math.round(quad.alpha() * layerFade * ShearWindbladeRenderGeometry.nearEyeFade(Math.sqrt(p.x()*p.x()+p.y()*p.y()+p.z()*p.z())));
                    out.addVertex(matrix, (float)p.x(), (float)p.y(), (float)p.z())
                            .setUv((float)vertex.u(), (float)vertex.v()).setColor(quad.gray(), quad.gray(), quad.gray(), alpha);
                }
            }
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
        return true;
    }
    private static Point point(Vec3 p) { return new Point(p.x, p.y, p.z); }
    private static final class Types extends RenderType {
        private Types() { super("unused", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 65536, false, true, () -> {}, () -> {}); }
        static final RenderType VOLUME = create("academy_shear_windblade_volume", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 65536, false, true, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new TextureStateShard(ShearWindbladeVolumeMaterial.GLOW, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP)
                        .setOverlayState(NO_OVERLAY).setOutputState(PARTICLES_TARGET).createCompositeState(false));
    }
}

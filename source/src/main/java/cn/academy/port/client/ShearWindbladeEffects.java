package cn.academy.port.client;

import cn.academy.port.highpower.ShearWindblade;
import cn.academy.port.highpower.core.ShearWindbladeGeometry.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW421 exact moving arc fronts with bright white cores and bounded soft gray pressure edges. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ShearWindbladeEffects {
    private static final ResourceLocation GLOW_LINE = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/glow_line.png");
    private static final ShearWindbladeState STATE = new ShearWindbladeState();
    private static final Map<ShearWindbladeState.Identity, Entity> visualOwners = new HashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static long generation;
    private static ByteBufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private ShearWindbladeEffects() {}

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { ShearWindblade.clientReceiver = ShearWindbladeEffects::receive; }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> Minecraft.getInstance().execute(() -> {
                AcademyClient.shearWindbladeCancelInput(); clear();
                if (buffers != null) buffers.endBatch();
                if (storage != null) storage.close();
                buffers = null; storage = null; Material.TYPES.clear();
            }));
        }
    }
    private static ShearWindbladeState.Frame frame(ShearWindblade.Visual p) {
        return new ShearWindbladeState.Frame(new ShearWindbladeState.Identity(p.owner(), p.ownerUuid(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), p.crouch(), point(p.origin()),
                p.fronts().stream().map(f -> new ShearWindbladeState.Front(f.index(), f.previous().stream().map(ShearWindbladeEffects::point).toList(),
                        f.current().stream().map(ShearWindbladeEffects::point).toList(), f.status())).toList(),
                p.impacts().stream().map(i -> new ShearWindbladeState.Impact(i.blade(), i.slot(), point(i.point()), i.status(), i.hitEntity())).toList());
    }
    public static void receive(ShearWindblade.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!ShearWindbladeState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.owner());
            if (!(owner instanceof Player) || !owner.getUUID().equals(packet.ownerUuid()) || !owner.isAlive() || owner.isRemoved()) return;
            boolean local = owner == mc.player;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.shearWindbladeInputMatches(packet.input(), packet.epoch()))) return;
            if (local && !AcademyClient.shearWindbladeControlValid()) { AcademyClient.shearWindbladeCancelInput(); return; }
            var previous = STATE.local();
            if (!STATE.accept(receipt, local)) return;
            visualOwners.keySet().removeIf(id -> !STATE.owns(id));
            if (STATE.owns(receipt.id())) visualOwners.put(receipt.id(), owner);
            if (previous != null && !STATE.owns(previous.id()))
                AcademyClient.shearWindbladeEndedInput(previous.id().input(), previous.id().epoch());
            if (local && packet.phase() == ShearWindbladeState.ENDED)
                AcademyClient.shearWindbladeEndedInput(packet.input(), packet.epoch());
        });
    }
    public static void abortLocal(long input, long epoch) {
        STATE.cancel(epoch, input); visualOwners.keySet().removeIf(id -> !STATE.owns(id));
    }
    public static void clear() { generation++; STATE.clear(); visualOwners.clear(); }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean validOwner(ShearWindbladeState.Controller cast, Minecraft mc) {
        if (cast == null || world == null) return false;
        Entity owner = world.getEntity(cast.id().entity());
        return owner == visualOwners.get(cast.id()) && owner instanceof Player && owner.isAlive() && !owner.isRemoved()
                && owner.getUUID().equals(cast.id().owner()) && (!cast.local() || owner == mc.player
                && AcademyClient.singleOwnerEpochMatches(cast.id().epoch()) && AcademyClient.shearWindbladeControlValid());
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); var cast = STATE.local();
        return cast != null && validOwner(cast, mc) ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); var local = STATE.local();
        // Send a scoped abort before erasing the identity; a transport failure preserves it for retry.
        if (local != null && !validOwner(local, mc)) AcademyClient.shearWindbladeCancelInput();
        for (var ended : STATE.tick(mc.isPaused(), cast -> validOwner(cast, mc)))
            if (local != null && local.id().equals(ended)) AcademyClient.shearWindbladeEndedInput(ended.input(), ended.epoch());
        visualOwners.keySet().removeIf(id -> !STATE.owns(id));
    }
    private static boolean visible(ShearWindbladeState.Controller cast, RenderLevelStageEvent event, Vec3 camera) {
        return visible(cast, event, camera, ShearWindbladeRenderGeometry.SHOULDER_WIDTH);
    }
    private static boolean visible(ShearWindbladeState.Controller cast, RenderLevelStageEvent event, Vec3 camera, double decoration) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX, distance = Double.POSITIVE_INFINITY;
        for (var front : cast.fronts()) for (var points : java.util.List.of(front.previous(), front.current())) for (Point p : points) {
            minX = Math.min(minX, p.x()); minY = Math.min(minY, p.y()); minZ = Math.min(minZ, p.z());
            maxX = Math.max(maxX, p.x()); maxY = Math.max(maxY, p.y()); maxZ = Math.max(maxZ, p.z());
            distance = Math.min(distance, camera.distanceToSqr(vec(p)));
        }
        if (!Double.isFinite(distance) || distance > ShearWindbladeRenderGeometry.CULL_DISTANCE * ShearWindbladeRenderGeometry.CULL_DISTANCE) return false;
        AABB bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(decoration);
        // Enhanced chunk availability is a whole-pass refusal in VolumePass, never a skipped controller.
        if (decoration == ShearWindbladeRenderGeometry.SHOULDER_WIDTH)
        for (int x = Math.floorDiv((int)Math.floor(bounds.minX), 16); x <= Math.floorDiv((int)Math.floor(bounds.maxX), 16); x++)
            for (int z = Math.floorDiv((int)Math.floor(bounds.minZ), 16); z <= Math.floorDiv((int)Math.floor(bounds.maxZ), 16); z++)
                if (!world.hasChunkAt(new BlockPos(x * 16, (int)Math.floor(bounds.minY), z * 16))) return false;
        return event.getFrustum().isVisible(bounds);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Vec3 camera = event.getCamera().getPosition();
        double partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (ShearWindbladeVolumePass.tryRender(event, STATE.active(),
                cast -> validOwner(cast, mc) && visible(cast, event, camera, ShearWindbladeVolumeGeometry.SHOULDER_WIDTH), partial)) return;
        var shapes = new ArrayList<ShearWindbladeRenderGeometry.Shape>(ShearWindbladeState.MAX_CONTROLLERS);
        int coreVertices = 0;
        for (var cast : STATE.active()) if (validOwner(cast, mc) && visible(cast, event, camera)) {
            var shape = ShearWindbladeRenderGeometry.shape(cast, partial); shapes.add(shape); coreVertices += shape.vertexCount(false);
        }
        renderShapes(event, shapes, coreVertices, camera);
    }
    /** Owner-valid, visible live casts for the decoration renderer; reads state only. */
    static java.util.List<ShearWindbladeState.Controller> decorCasts(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Vec3 camera = event.getCamera().getPosition();
        var out = new ArrayList<ShearWindbladeState.Controller>(ShearWindbladeState.MAX_CONTROLLERS);
        for (var cast : STATE.active()) if (validOwner(cast, mc) && visible(cast, event, camera)) out.add(cast);
        return out;
    }
    static boolean materialFixtureIdle() { return STATE.active().isEmpty(); }
    /** Isolated launch-gated fixture caller only; never admits packets or changes live State. */
    static boolean renderMaterialFixture(RenderLevelStageEvent event, java.util.List<ShearWindbladeState.Controller> controllers, double partial) {
        if (!Boolean.getBoolean("academy.visual.qa") || !Boolean.getBoolean("academy.shearWindbladeVolume.preview")
                || !Minecraft.getInstance().hasSingleplayerServer() || !materialFixtureIdle()) return false;
        if (ShearWindbladeVolumePass.tryRender(event, controllers, controller -> true, partial)) return true;
        var shapes = new ArrayList<ShearWindbladeRenderGeometry.Shape>();
        int vertices = 0;
        for (var controller : controllers) {
            var shape = ShearWindbladeRenderGeometry.shape(controller, partial);
            shapes.add(shape); vertices += shape.vertexCount(false);
        }
        renderShapes(event, shapes, vertices, event.getCamera().getPosition());
        return false;
    }
    private static void renderShapes(RenderLevelStageEvent event, java.util.List<ShearWindbladeRenderGeometry.Shape> shapes, int coreVertices, Vec3 camera) {
        if (coreVertices == 0) return;
        // At most 256 core vertices across four controllers. Decoration yields first, never a partial front.
        boolean softEdges = ShearWindbladeRenderGeometry.canUseShoulder(coreVertices, ShearWindbladeRenderGeometry.SCENE_VERTEX_BUDGET);
        if (buffers == null) { storage = new ByteBufferBuilder(65536); buffers = MultiBufferSource.immediate(storage); }
        var look = event.getCamera().getLookVector(); Point forward = new Point(look.x(), look.y(), look.z());
        var left = event.getCamera().getLeftVector(); Vec3 horizontal = new Vec3(left.x(), left.y(), left.z());
        var poses = event.getPoseStack(); var modelView = RenderSystem.getModelViewStack();
        float[] color = RenderSystem.getShaderColor().clone(); modelView.pushMatrix(); poses.pushPose();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = poses.last().pose();
            // Double camera subtraction precedes every float conversion. No world-space matrix translation.
            if (softEdges) {
                var shoulder = buffers.getBuffer(Material.quads(false));
                for (var shape : shapes) for (var line : shape.strokes())
                    stroke(shoulder, matrix, line, ShearWindbladeRenderGeometry.profile(line, false), point(camera), forward, horizontal);
                buffers.endBatch(Material.quads(false));
            }
            var core = buffers.getBuffer(Material.quads(true));
            for (var shape : shapes) for (var line : shape.strokes())
                stroke(core, matrix, line, ShearWindbladeRenderGeometry.profile(line, true), point(camera), forward, horizontal);
            // No ground-contact certificate is present: create no dust, full-sector fill, impact spray or fire.
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }
    private static void stroke(VertexConsumer out, Matrix4f matrix, ShearWindbladeRenderGeometry.Stroke stroke,
                               ShearWindbladeRenderGeometry.Profile profile, Point camera, Point forward, Vec3 fallback) {
        if (profile.alpha() <= 0) return;
        var clipped = ShearWindbladeRenderGeometry.clipNear(new ShearWindbladeRenderGeometry.Stroke(
                ShearWindbladeRenderGeometry.relative(stroke.from(), camera), ShearWindbladeRenderGeometry.relative(stroke.to(), camera),
                stroke.blade(), stroke.chord(), stroke.alpha()), forward, profile.width() / 2);
        if (clipped == null) return;
        Vec3 a = vec(clipped.from()), b = vec(clipped.to());
        Vec3 side = b.subtract(a).cross(a.add(b).scale(-.5)).normalize();
        if (side.lengthSqr() < 1e-10) side = fallback;
        side = side.scale(profile.width() / 2);
        int startAlpha = (int)Math.round(profile.alpha() * ShearWindbladeRenderGeometry.nearEyeFade(a.length()));
        int endAlpha = (int)Math.round(profile.alpha() * ShearWindbladeRenderGeometry.nearEyeFade(b.length()));
        vertex(out, matrix, a.subtract(side), 0, profile.maxV(), profile.gray(), startAlpha);
        vertex(out, matrix, a.add(side), 0, profile.minV(), profile.gray(), startAlpha);
        vertex(out, matrix, b.add(side), 1, profile.minV(), profile.gray(), endAlpha);
        vertex(out, matrix, b.subtract(side), 1, profile.maxV(), profile.gray(), endAlpha);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 relative, float u, float v, int gray, int alpha) {
        out.addVertex(matrix, (float)relative.x, (float)relative.y, (float)relative.z).setUv(u, v).setColor(gray, gray, gray, alpha);
    }
    private static Point point(Vec3 p) { return p == null ? null : new Point(p.x, p.y, p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private static final class Material extends RenderType {
        private static final Map<Boolean, RenderType> TYPES = new HashMap<>();
        private Material(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling,
                         boolean sorted, Runnable set, Runnable reset) { super(name, format, mode, size, crumbling, sorted, set, reset); }
        static RenderType quads(boolean core) {
            return TYPES.computeIfAbsent(core, p -> create("academy_shear_windblade_" + p, DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS, 16384, false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(GLOW_LINE, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
    }
}

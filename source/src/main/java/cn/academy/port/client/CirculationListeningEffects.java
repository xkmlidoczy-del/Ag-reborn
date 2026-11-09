package cn.academy.port.client;

import cn.academy.port.highpower.CirculationListening;
import cn.academy.port.client.CirculationListeningMesh.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
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
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

/** NEW433 owner-only directional air cues. No client sensing, particles, sound, damage or target positions. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class CirculationListeningEffects {
    private static final ResourceLocation GLOW_LINE = texture("effects/glow_line"), WHITE = texture("port/white");
    private static final CirculationListeningState STATE = new CirculationListeningState();
    private record Binding(Entity owner, CirculationListeningState.Identity id) {}
    private static final Map<UUID, Binding> OWNERS = new HashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static ByteBufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private static Boolean textured;
    private CirculationListeningEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            CirculationListening.clientReceiver = CirculationListeningEffects::receive;
            CirculationListening.clientRejectionReceiver = CirculationListeningEffects::reject;
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> Minecraft.getInstance().execute(() -> {
                AcademyClient.cancelCirculationListeningForReload(); clear();
                if (buffers != null) buffers.endBatch();
                if (storage != null) storage.close();
                buffers = null; storage = null; textured = null; Material.TYPES.clear();
            }));
        }
    }
    private static CirculationListeningState.Frame frame(CirculationListening.Visual p) {
        return new CirculationListeningState.Frame(new CirculationListeningState.Identity(p.entity(), p.owner(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), p.cues() == null ? null : p.cues().stream().map(c -> new CirculationListeningState.Cue(
                        c.azimuth(), c.elevation(), c.strength(), c.kind())).toList());
    }
    public static void receive(CirculationListening.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!CirculationListeningState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.entity());
            if (!(owner instanceof Player) || owner != mc.player || !owner.getUUID().equals(packet.owner())) return;
            Binding binding = OWNERS.get(packet.owner());
            if (binding != null && binding.id.equals(receipt.id()) && binding.owner != owner) return;
            boolean local = owner == mc.player;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.circulationListeningInputMatches(packet.input(), packet.epoch()))) return;
            if (packet.phase() != CirculationListeningState.ENDED && (!owner.isAlive() || owner.isRemoved() || owner.isSpectator()
                    || local && !AcademyClient.circulationListeningControlValid())) return;
            if (!STATE.accept(receipt, local)) return;
            OWNERS.entrySet().removeIf(entry -> STATE.current(entry.getKey()) == null);
            if (packet.phase() == CirculationListeningState.ENDED) {
                if (local) AcademyClient.circulationListeningEndedInput(packet.input(), packet.epoch());
            } else {
                OWNERS.put(packet.owner(), new Binding(owner, receipt.id()));
                if (local) AcademyClient.circulationListeningAcceptedInput(packet.input(), packet.epoch());
            }
        });
    }
    private static void reject(CirculationListening.Rejection packet) {
        if (packet == null || packet.owner() == null || packet.entity() < 0 || packet.input() <= 0 || packet.epoch() <= 0) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || capturedPlayer == null || mc.level != capturedWorld || mc.player != capturedPlayer
                    || mc.getConnection() != capturedConnection || generation != capturedGeneration
                    || !AcademyClient.sameInputSessionEpoch(capturedSession) || packet.entity() != capturedPlayer.getId()
                    || !packet.owner().equals(capturedPlayer.getUUID()) || !AcademyClient.singleOwnerEpochMatches(packet.epoch())) return;
            AcademyClient.circulationListeningRejectedInput(packet.input(), packet.epoch());
        });
    }
    public static void cancelLocal(long input, long epoch) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); STATE.cancel(epoch, input);
        OWNERS.entrySet().removeIf(entry -> STATE.current(entry.getKey()) == null);
    }
    public static void abortLocal(long input, long epoch) { cancelLocal(input, epoch); }
    public static void clear() { generation++; STATE.clear(); OWNERS.clear(); }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean validOwner(CirculationListeningState.Controller controller, Minecraft mc) {
        if (controller == null || world == null) return false;
        Binding binding = OWNERS.get(controller.id().owner());
        if (binding == null || !binding.id.equals(controller.id())) return false;
        Entity owner = binding.owner;
        return owner instanceof Player && world.getEntity(controller.id().entity()) == owner && owner.isAlive()
                && !owner.isRemoved() && !owner.isSpectator() && owner.getUUID().equals(controller.id().owner())
                && owner == mc.player && AcademyClient.singleOwnerEpochMatches(controller.id().epoch())
                && AcademyClient.circulationListeningInputMatches(controller.id().input(), controller.id().epoch())
                && AcademyClient.circulationListeningControlValid();
    }
    public static boolean activeFor(UUID owner) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var controller = owner == null ? null : STATE.current(owner);
        return controller != null && controller.quiet() < CirculationListeningState.QUIET_TICKS && validOwner(controller, mc);
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        return mc.player != null && activeFor(mc.player.getUUID()) ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    public static boolean activeLocal() { return localVisual() == ClassicAbilityHud.DelegateVisual.ACTIVE; }
    /** Empty space keeps only this small status, with no invented disturbance marks or audio. */
    public static String localHint() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var controller = mc.player == null ? null : STATE.current(mc.player.getUUID());
        if (controller == null || !validOwner(controller, mc) || controller.quiet() >= CirculationListeningState.QUIET_TICKS) return "";
        boolean paused = controller.frame().phase() == CirculationListeningState.PAUSED;
        return net.minecraft.network.chat.Component.translatableWithFallback(paused
                ? "hud.academy.highpower.circulation_listening.paused" : "hud.academy.highpower.circulation_listening",
                paused ? "Listening paused" : "Listening").getString();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        List<CirculationListeningState.Identity> local = STATE.controllers().stream().map(CirculationListeningState.Controller::id).toList();
        var losses = STATE.tick(mc.isPaused(), controller -> validOwner(controller, mc));
        OWNERS.entrySet().removeIf(entry -> STATE.current(entry.getKey()) == null);
        // Only explicit owner invalidity is terminal. Quiet heartbeats remain recoverable and send no abort.
        for (var id : losses) if (local.contains(id)) AcademyClient.circulationListeningLocalVisualLost(id.input(), id.epoch());
    }
    /** Owner-only compass arrows reuse the same short-lived authenticated cues as the local airflow. */
    @SubscribeEvent public static void edgeGuidance(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (world == null || mc.player == null || mc.screen != null || mc.options.hideGui
                || mc.getDebugOverlay().showDebugScreen() || mc.getCameraEntity() != mc.player) return;
        var controller = STATE.current(mc.player.getUUID());
        if (controller == null || !validOwner(controller, mc)) return;
        var graphics = event.getGuiGraphics();
        double yaw = Math.toRadians(mc.gameRenderer.getMainCamera().getYRot());
        Point view = new Point(-Math.sin(yaw), 0, Math.cos(yaw));
        double partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var hud = CirculationListeningMesh.hudShape(controller, graphics.guiWidth(), graphics.guiHeight(), view, partial);
        if (hud.markers().isEmpty() || hud.vertexCount() > CirculationListeningMesh.MAX_HUD_VERTICES) return;
        // Reserve the complete world geometry ceiling, so both stages combined never exceed 512 vertices.
        var budget = new CirculationListeningMesh.Budget();
        if (!budget.take(CirculationListeningMesh.MAX_CONTEXT_VERTICES) || !budget.take(hud.vertexCount())) return;
        for (boolean core : new boolean[]{false, true}) for (var marker : hud.markers()) {
            int gray = core ? 241 : 164, alpha = core ? marker.alpha() : (int)Math.round(marker.alpha() * .32);
            if (alpha <= 0) continue;
            int color = alpha << 24 | gray << 16 | gray << 8 | gray;
            hudStroke(graphics, marker.left(), marker.tip(), core ? 2 : 4, color);
            hudStroke(graphics, marker.right(), marker.tip(), core ? 2 : 4, color);
        }
    }
    /** Two rectangles per layer make one chevron: exactly sixteen vertices, with no particles or labels. */
    private static void hudStroke(GuiGraphics graphics, CirculationListeningMesh.ScreenPoint from,
                                  CirculationListeningMesh.ScreenPoint to, int width, int color) {
        double dx = to.x() - from.x(), dy = to.y() - from.y(), length = Math.hypot(dx, dy);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(from.x(), from.y(), 0);
            graphics.pose().mulPose(Axis.ZP.rotation((float)Math.atan2(dy, dx)));
            graphics.pose().scale((float)length, 1, 1);
            graphics.fill(0, -width / 2, 1, width / 2, color);
        } finally { graphics.pose().popPose(); }
    }
    private static boolean visible(CirculationListeningState.Controller controller, RenderLevelStageEvent event, Vec3 camera) {
        Binding binding = OWNERS.get(controller.id().owner());
        if (binding == null || camera.distanceToSqr(binding.owner.position()) > CirculationListeningMesh.CULL_DISTANCE * CirculationListeningMesh.CULL_DISTANCE) return false;
        AABB bounds = binding.owner.getBoundingBox().inflate(1.5);
        for (int x = Math.floorDiv((int)Math.floor(bounds.minX), 16); x <= Math.floorDiv((int)Math.floor(bounds.maxX), 16); x++)
            for (int z = Math.floorDiv((int)Math.floor(bounds.minZ), 16); z <= Math.floorDiv((int)Math.floor(bounds.maxZ), 16); z++)
                if (!world.hasChunkAt(new BlockPos(x * 16, (int)Math.floor(bounds.minY), z * 16))) return false;
        return event.getFrustum().isVisible(bounds);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); if (world == null || mc.options.hideGui || mc.getCameraEntity() != mc.player || STATE.size() == 0) return;
        Vec3 camera = event.getCamera().getPosition(); Point eye = point(camera);
        var visible = STATE.controllers().stream().filter(c -> c.quiet() < CirculationListeningState.QUIET_TICKS && validOwner(c, mc) && visible(c, event, camera))
                .limit(CirculationListeningMesh.MAX_CONTEXTS).toList();
        if (visible.isEmpty()) return;
        float partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var budget = new CirculationListeningMesh.Budget();
        var shapes = visible.stream().map(c -> {
            Entity owner = OWNERS.get(c.id().owner()).owner;
            Point feet = new Point(Mth.lerp(partial, owner.xo, owner.getX()), Mth.lerp(partial, owner.yo, owner.getY()) + owner.getEyeHeight(),
                    Mth.lerp(partial, owner.zo, owner.getZ()));
            return CirculationListeningMesh.shape(c, feet, partial);
        }).filter(s -> s.vertexCount() > 0 && s.vertexCount() <= CirculationListeningMesh.MAX_CONTEXT_VERTICES
                && budget.take(s.vertexCount())).toList();
        if (shapes.isEmpty()) return;
        if (textured == null) textured = mc.getResourceManager().getResource(GLOW_LINE).isPresent()
                && mc.getResourceManager().getResource(WHITE).isPresent();
        if (buffers == null) { storage = new ByteBufferBuilder(65536); buffers = MultiBufferSource.immediate(storage); }
        var look = event.getCamera().getLookVector(); Point forward = new Point(look.x(), look.y(), look.z());
        var left = event.getCamera().getLeftVector(); Vec3 fallback = new Vec3(left.x(), left.y(), left.z());
        var poses = event.getPoseStack(); var modelView = RenderSystem.getModelViewStack();
        float[] color = RenderSystem.getShaderColor().clone(); poses.pushPose(); modelView.pushMatrix();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = poses.last().pose();
            for (Pass pass : new Pass[]{Pass.SOFT, Pass.CORE}) {
                VertexConsumer out = buffers.getBuffer(Material.quads(pass));
                for (var shape : shapes) for (var stroke : shape.strokes())
                    stroke(out, matrix, stroke, CirculationListeningMesh.profile(stroke, pass == Pass.CORE), eye, forward, fallback);
                buffers.endBatch(Material.quads(pass));
            }
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }
    private static void stroke(VertexConsumer out, Matrix4f matrix, CirculationListeningMesh.Stroke stroke,
                               CirculationListeningMesh.Profile profile, Point camera, Point forward, Vec3 fallback) {
        if (profile.alpha() <= 0) return;
        var clipped = CirculationListeningMesh.clipNear(new CirculationListeningMesh.Stroke(
                CirculationListeningMesh.relative(stroke.from(), camera), CirculationListeningMesh.relative(stroke.to(), camera),
                profile.width(), profile.alpha(), stroke.role()), forward, profile.width() / 2);
        if (clipped == null) return;
        Vec3 a = vec(clipped.from()), b = vec(clipped.to());
        Vec3 side = b.subtract(a).cross(a.add(b).scale(-.5)).normalize();
        if (side.lengthSqr() < 1e-10) side = fallback;
        side = side.scale(profile.width() / 2);
        vertex(out, matrix, a.subtract(side), 0, profile.maxV(), profile.gray(), profile.alpha());
        vertex(out, matrix, a.add(side), 0, profile.minV(), profile.gray(), profile.alpha());
        vertex(out, matrix, b.add(side), 1, profile.minV(), profile.gray(), profile.alpha());
        vertex(out, matrix, b.subtract(side), 1, profile.maxV(), profile.gray(), profile.alpha());
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 p, float u, float v, int gray, int alpha) {
        VertexConsumer vertex = out.addVertex(matrix, (float)p.x, (float)p.y, (float)p.z);
        if (Boolean.TRUE.equals(textured)) vertex.setUv(u, v);
        vertex.setColor(gray, gray, gray, alpha);
    }
    private static Point point(Vec3 p) { return p == null ? null : new Point(p.x, p.y, p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private enum Pass { SOFT, CORE }
    /** Original textures, ordinary translucent depth testing, color-only writes and no custom shader. */
    private static final class Material extends RenderType {
        private static final Map<Pass, RenderType> TYPES = new HashMap<>();
        private Material(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling,
                         boolean sorted, Runnable set, Runnable reset) { super(name, format, mode, size, crumbling, sorted, set, reset); }
        static RenderType quads(Pass pass) {
            return TYPES.computeIfAbsent(pass, p -> {
                boolean useTexture = Boolean.TRUE.equals(textured);
                var state = CompositeState.builder()
                        .setShaderState(new ShaderStateShard(useTexture ? GameRenderer::getPositionTexColorShader : GameRenderer::getPositionColorShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET);
                if (useTexture) state.setTextureState(new TextureStateShard(p == Pass.CORE ? WHITE : GLOW_LINE, false, false));
                return create("academy_circulation_listening_" + p,
                        useTexture ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR,
                        VertexFormat.Mode.QUADS, 8192, false, true, state.createCompositeState(false));
            });
        }
    }

    /** Read-only snapshot: points = owner position; numbers = 0, cue count, then (azimuth, elevation, strength 0..3, kind) per cue. */
    static java.util.List<DecorSnap> decorSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var out = new java.util.ArrayList<DecorSnap>();
        for (var c : STATE.controllers()) {
            if (world == null || c.quiet() >= CirculationListeningState.QUIET_TICKS || !validOwner(c, mc)) continue;
            Binding binding = OWNERS.get(c.id().owner());
            if (binding == null || c.frame().cues() == null) continue;
            var cues = c.frame().cues(); int n = Math.min(cues.size(), 12);
            double[] numbers = new double[2 + 4 * n]; numbers[1] = n;
            for (int i = 0; i < n; i++) { var cue = cues.get(i); numbers[2 + 4 * i] = cue.azimuth(); numbers[3 + 4 * i] = cue.elevation(); numbers[4 + 4 * i] = cue.strength(); numbers[5 + 4 * i] = cue.kind(); }
            var pos = binding.owner.position();
            out.add(DecorSnap.of(true, c.cueAge(), c.frame().phase(), c.id().token(), java.util.List.of(new double[] {pos.x, pos.y, pos.z}), numbers));
        }
        return out;
    }
}

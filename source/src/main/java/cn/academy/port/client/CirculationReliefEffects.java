package cn.academy.port.client;

import cn.academy.port.highpower.CirculationRelief;
import cn.academy.port.client.CirculationReliefMesh.Point;
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
import org.joml.Matrix4f;

/** NEW432 receipt-authenticated open side circulation and short descent into actual supported ground. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class CirculationReliefEffects {
    private static final ResourceLocation GLOW_LINE = texture("effects/glow_line"), WHITE = texture("port/white");
    private static final CirculationReliefState STATE = new CirculationReliefState();
    private record Binding(Entity owner, CirculationReliefState.Identity id) {}
    private static final Map<UUID, Binding> OWNERS = new HashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static ByteBufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private CirculationReliefEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            CirculationRelief.clientReceiver = CirculationReliefEffects::receive;
            CirculationRelief.clientRejectionReceiver = CirculationReliefEffects::reject;
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> Minecraft.getInstance().execute(() -> {
                AcademyClient.cancelCirculationReliefForReload(); clear();
                if (buffers != null) buffers.endBatch();
                if (storage != null) storage.close();
                buffers = null; storage = null; Material.TYPES.clear();
            }));
        }
    }
    private static CirculationReliefState.Frame frame(CirculationRelief.Visual p) {
        return new CirculationReliefState.Frame(new CirculationReliefState.Identity(p.entity(), p.owner(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), point(p.point()), point(p.facing()), point(p.groundSink()), point(p.contact()), p.remainingHits(), p.grounded());
    }
    public static void receive(CirculationRelief.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!CirculationReliefState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.entity());
            if (!(owner instanceof Player) || !owner.getUUID().equals(packet.owner())) return;
            Binding binding = OWNERS.get(packet.owner());
            if (binding != null && binding.id.equals(receipt.id()) && binding.owner != owner) return;
            boolean local = owner == mc.player;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.circulationReliefInputMatches(packet.input(), packet.epoch()))) return;
            if (packet.phase() != CirculationReliefState.ENDED && (!owner.isAlive() || owner.isRemoved() || owner.isSpectator()
                    || owner.position().distanceToSqr(vec(receipt.point())) > 16
                    || local && !AcademyClient.circulationReliefControlValid())) return;
            if (!STATE.accept(receipt, local)) return;
            OWNERS.entrySet().removeIf(entry -> STATE.current(entry.getKey()) == null);
            if (packet.phase() == CirculationReliefState.ENDED) {
                if (local) AcademyClient.circulationReliefEndedInput(packet.input(), packet.epoch());
            } else {
                OWNERS.put(packet.owner(), new Binding(owner, receipt.id()));
                if (local) AcademyClient.circulationReliefAcceptedInput(packet.input(), packet.epoch());
            }
        });
    }
    private static void reject(CirculationRelief.Rejection packet) {
        if (packet == null || packet.owner() == null || packet.entity() < 0 || packet.input() <= 0 || packet.epoch() <= 0) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || capturedPlayer == null || mc.level != capturedWorld || mc.player != capturedPlayer
                    || mc.getConnection() != capturedConnection || generation != capturedGeneration
                    || !AcademyClient.sameInputSessionEpoch(capturedSession) || packet.entity() != capturedPlayer.getId()
                    || !packet.owner().equals(capturedPlayer.getUUID()) || !AcademyClient.singleOwnerEpochMatches(packet.epoch())) return;
            AcademyClient.circulationReliefRejectedInput(packet.input(), packet.epoch());
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
    private static boolean validOwner(CirculationReliefState.Controller controller, Minecraft mc) {
        if (controller == null || world == null) return false;
        Binding binding = OWNERS.get(controller.id().owner());
        if (binding == null || !binding.id.equals(controller.id())) return false;
        Entity owner = binding.owner;
        return owner instanceof Player && world.getEntity(controller.id().entity()) == owner && owner.isAlive()
                && !owner.isRemoved() && !owner.isSpectator() && owner.getUUID().equals(controller.id().owner())
                && (!controller.local() || owner == mc.player && AcademyClient.singleOwnerEpochMatches(controller.id().epoch())
                && AcademyClient.circulationReliefInputMatches(controller.id().input(), controller.id().epoch())
                && AcademyClient.circulationReliefControlValid());
    }
    public static boolean activeFor(UUID owner) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var controller = owner == null ? null : STATE.current(owner);
        return controller != null && controller.quiet() < CirculationReliefState.QUIET_TICKS && validOwner(controller, mc);
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        return mc.player != null && activeFor(mc.player.getUUID()) ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    public static boolean activeLocal() { return localVisual() == ClassicAbilityHud.DelegateVisual.ACTIVE; }
    /** The allowance is explicitly the last receipt's tick, never a predicted replenishment. */
    public static String localHint() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var controller = mc.player == null ? null : STATE.current(mc.player.getUUID());
        if (controller == null || !validOwner(controller, mc) || controller.quiet() >= CirculationReliefState.QUIET_TICKS) return "";
        return net.minecraft.network.chat.Component.translatableWithFallback("hud.academy.highpower.circulation_relief",
                "Circulation %s/4 | %s", controller.frame().remainingHits(),
                net.minecraft.network.chat.Component.translatableWithFallback(!controller.frame().grounded()
                        ? "hud.academy.highpower.circulation_relief.no_ground" : controller.frame().remainingHits() == 0
                        ? "hud.academy.highpower.circulation_relief.exhausted" : "hud.academy.highpower.circulation_relief.grounded",
                        !controller.frame().grounded() ? "paused: no ground sink" : controller.frame().remainingHits() == 0
                                ? "paused: quota spent" : "ground sink ready")).getString();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        List<CirculationReliefState.Identity> local = STATE.controllers().stream().filter(CirculationReliefState.Controller::local)
                .map(CirculationReliefState.Controller::id).toList();
        var losses = STATE.tick(mc.isPaused(), controller -> validOwner(controller, mc));
        OWNERS.entrySet().removeIf(entry -> STATE.current(entry.getKey()) == null);
        // Only explicit owner invalidity is terminal. Quiet heartbeats remain recoverable and send no abort.
        for (var id : losses) if (local.contains(id)) AcademyClient.circulationReliefLocalVisualLost(id.input(), id.epoch());
    }
    private static boolean visible(CirculationReliefState.Controller controller, RenderLevelStageEvent event, Vec3 camera) {
        Binding binding = OWNERS.get(controller.id().owner());
        if (binding == null || camera.distanceToSqr(binding.owner.position()) > CirculationReliefMesh.CULL_DISTANCE * CirculationReliefMesh.CULL_DISTANCE) return false;
        AABB bounds = binding.owner.getBoundingBox().inflate(1);
        var absorption = controller.absorption();
        if (absorption != null && controller.absorptionAge() < CirculationReliefState.ABSORPTION_TICKS) {
            Vec3 sink = vec(absorption.groundSink()), point = vec(absorption.point());
            // Never widen culling with an old success stream after the owner has moved away.
            if (point.distanceToSqr(binding.owner.position()) > 16) return false;
            bounds = bounds.minmax(new AABB(sink, point.add(0, 1.1, 0)).inflate(1));
        }
        for (int x = Math.floorDiv((int)Math.floor(bounds.minX), 16); x <= Math.floorDiv((int)Math.floor(bounds.maxX), 16); x++)
            for (int z = Math.floorDiv((int)Math.floor(bounds.minZ), 16); z <= Math.floorDiv((int)Math.floor(bounds.maxZ), 16); z++)
                if (!world.hasChunkAt(new BlockPos(x * 16, (int)Math.floor(bounds.minY), z * 16))) return false;
        return event.getFrustum().isVisible(bounds);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); if (world == null || STATE.size() == 0) return;
        Vec3 camera = event.getCamera().getPosition(); Point eye = point(camera);
        var visible = STATE.controllers().stream().filter(c -> c.quiet() < CirculationReliefState.QUIET_TICKS && validOwner(c, mc) && visible(c, event, camera))
                .sorted(Comparator.<CirculationReliefState.Controller>comparingInt(c -> c.local() ? 0 : 1)
                        .thenComparingDouble(c -> OWNERS.get(c.id().owner()).owner.position().distanceToSqr(camera))
                        .thenComparingInt(c -> c.id().entity()))
                .limit(CirculationReliefMesh.MAX_CONTEXTS).toList();
        if (visible.isEmpty()) return;
        float partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var budget = new CirculationReliefMesh.Budget();
        var shapes = visible.stream().map(c -> {
            Entity owner = OWNERS.get(c.id().owner()).owner;
            Point feet = new Point(Mth.lerp(partial, owner.xo, owner.getX()), Mth.lerp(partial, owner.yo, owner.getY()),
                    Mth.lerp(partial, owner.zo, owner.getZ()));
            return CirculationReliefMesh.shape(c, feet, partial);
        }).filter(s -> s.vertexCount() > 0 && s.vertexCount() <= CirculationReliefMesh.MAX_CONTEXT_VERTICES
                && budget.take(s.vertexCount())).toList();
        if (shapes.isEmpty()) return;
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
                    stroke(out, matrix, stroke, CirculationReliefMesh.profile(stroke, pass == Pass.CORE), eye, forward, fallback);
                buffers.endBatch(Material.quads(pass));
            }
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }
    private static void stroke(VertexConsumer out, Matrix4f matrix, CirculationReliefMesh.Stroke stroke,
                               CirculationReliefMesh.Profile profile, Point camera, Point forward, Vec3 fallback) {
        if (profile.alpha() <= 0) return;
        var clipped = CirculationReliefMesh.clipNear(new CirculationReliefMesh.Stroke(
                CirculationReliefMesh.relative(stroke.from(), camera), CirculationReliefMesh.relative(stroke.to(), camera),
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
        out.addVertex(matrix, (float)p.x, (float)p.y, (float)p.z).setUv(u, v).setColor(gray, gray, gray, alpha);
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
            return TYPES.computeIfAbsent(pass, p -> create("academy_circulation_relief_" + p, DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS, 8192, false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(p == Pass.CORE ? WHITE : GLOW_LINE, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
    }

    /** Read-only snapshot: points = body, facing, ground sink, absorbed contact; numbers = hits, grounded, absorption age (-1 none). */
    private static double[] dp(Point p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var out = new java.util.ArrayList<DecorSnap>();
        for (var c : STATE.controllers()) {
            if (world == null || !validOwner(c, mc)) continue;
            var f = c.frame();
            out.add(DecorSnap.of(c.local(), c.quiet(), f.phase(), c.id().token(),
                    java.util.List.of(dp(f.point()), dp(f.facing()), dp(f.groundSink()), c.absorption() == null ? DecorSnap.NONE : dp(c.absorption().contact())),
                    f.remainingHits(), f.grounded() ? 1 : 0, c.absorption() == null ? -1 : c.absorptionAge()));
        }
        return out;
    }
}

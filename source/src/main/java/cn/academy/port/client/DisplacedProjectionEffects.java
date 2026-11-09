package cn.academy.port.client;

import cn.academy.port.highpower.DisplacedProjection;
import cn.academy.port.highpower.DisplacedNeedleEntity;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW311 actual needle-relocation receipts: seven-tick gray coordinates, no client projectile or predicted hit. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class DisplacedProjectionEffects {
    private static final ResourceLocation PARTICLE = texture("effects/tp_particle");
    private static final ResourceLocation[] MARKS = new ResourceLocation[DisplacedProjectionMesh.SUCCESS_TICKS];
    private static final DisplacedProjectionState STATE = new DisplacedProjectionState();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static long generation;
    private static MultiBufferSource.BufferSource buffers;
    private static Cosmetic success;
    private static DisplacedProjectionState.Frame active;
    private static final class Cosmetic {
        final Entity owner;
        final DisplacedProjectionState.Frame frame;
        int age;
        Cosmetic(Entity owner, DisplacedProjectionState.Frame frame) { this.owner = owner; this.frame = frame; }
    }
    static { for (int i = 0; i < MARKS.length; i++) MARKS[i] = texture("effects/tp_mark/" + i); }
    private DisplacedProjectionEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { DisplacedProjection.clientReceiver = DisplacedProjectionEffects::receive; }
    }
    private static DisplacedProjectionState.Frame frame(DisplacedProjection.Visual p) {
        return new DisplacedProjectionState.Frame(new DisplacedProjectionState.Identity(p.entity(), p.owner(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), point(p.source()), point(p.arrival()), point(p.impact()), p.needleEntity(), p.needleUuid(), p.accepted(), p.reason());
    }
    public static void receive(DisplacedProjection.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!DisplacedProjectionState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.entity());
            if (!(owner instanceof Player) || !owner.getUUID().equals(packet.owner()) || !owner.isAlive() || owner.isRemoved()) return;
            boolean local = owner == mc.player;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.displacedProjectionInputMatches(packet.input(), packet.epoch()))) return;
            if (local && !AcademyClient.displacedProjectionControlValid()) {
                AcademyClient.displacedProjectionCancelInput(); return;
            }
            if (packet.phase() == DisplacedProjectionState.PROJECTED || packet.phase() == DisplacedProjectionState.IMPACT) {
                Entity needle = capturedWorld.getEntity(packet.needleEntity());
                if (needle != null && (!(needle instanceof DisplacedNeedleEntity) || !needle.getUUID().equals(packet.needleUuid()))) return;
            }
            if (!STATE.accept(receipt, local)) return;
            if (local) {
                if (DisplacedProjectionState.terminal(receipt)) {
                    AcademyClient.displacedProjectionEndedInput(packet.input(), packet.epoch());
                    if (active != null && active.id().equals(receipt.id())) active = null;
                } else active = receipt;
            }
            if (packet.phase() == DisplacedProjectionState.PROJECTED) {
                // One actual endpoint pair globally; the local receipt has priority over remote activity.
                if (success == null || local || success.owner != mc.player) success = new Cosmetic(owner, receipt);
            } else if (packet.phase() == DisplacedProjectionState.CANCELLED) {
                if (success != null && success.frame.id().equals(receipt.id())) success = null;
            } else if (local && packet.phase() == DisplacedProjectionState.REFUSED) {
                String reason = "academy.highpower.displaced_projection.reason." + packet.reason();
                var detail = net.minecraft.locale.Language.getInstance().has(reason) ? Component.translatable(reason)
                        : Component.translatable("academy.highpower.displaced_projection.refused");
                mc.player.displayClientMessage(Component.translatable("academy.highpower.displaced_projection.blocked", detail), true);
            }
        });
    }
    public static void abortLocal(long input, long epoch) {
        STATE.cancel(epoch, input); active = null;
        if (success != null && success.owner == Minecraft.getInstance().player) success = null;
    }
    public static void clear() { generation++; success = null; active = null; }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean validOwner(Entity owner, DisplacedProjectionState.Frame frame) {
        return world != null && owner != null && owner.isAlive() && !owner.isRemoved()
                && world.getEntity(frame.id().entity()) == owner && owner.getUUID().equals(frame.id().owner());
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        return active != null && validOwner(mc.player, active) && AcademyClient.displacedProjectionInputMatches(active.id().input(), active.id().epoch())
                ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (active != null && (!validOwner(mc.player, active)
                || !AcademyClient.displacedProjectionInputMatches(active.id().input(), active.id().epoch()))) active = null;
        if (success != null && (!validOwner(success.owner, success.frame)
                || !mc.isPaused() && ++success.age >= DisplacedProjectionMesh.SUCCESS_TICKS)) success = null;
    }
    /** No chunk loads. World buffers use LEQUAL; refused receipts never supply renderable markers. */
    private static boolean loaded(Box bounds) {
        if (world == null || bounds == null || !bounds.body()) return false;
        for (int x = Math.floorDiv((int)Math.floor(bounds.minX()), 16); x <= Math.floorDiv((int)Math.floor(bounds.maxX()), 16); x++)
            for (int z = Math.floorDiv((int)Math.floor(bounds.minZ()), 16); z <= Math.floorDiv((int)Math.floor(bounds.maxZ()), 16); z++)
                if (!world.hasChunkAt(new BlockPos(x * 16, (int)Math.floor(bounds.minY()), z * 16))) return false;
        return true;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); if (world == null) return;
        if (success == null) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        var poses = event.getPoseStack(); Vec3 camera = event.getCamera().getPosition();
        var modelView = RenderSystem.getModelViewStack(); float[] color = RenderSystem.getShaderColor().clone();
        modelView.pushMatrix(); poses.pushPose();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            // Subtract the double camera at vertex emission before narrowing to float.
            Matrix4f matrix = poses.last().pose();
            if (success != null && validOwner(success.owner, success.frame)) {
                var f = success.frame; int alpha = DisplacedProjectionMesh.alpha(success.age);
                Box departure = DisplacedProjectionMesh.bounds(f.source()), arrival = DisplacedProjectionMesh.bounds(f.arrival());
                if (loaded(departure) && loaded(arrival)
                        && Math.min(camera.distanceToSqr(vec(f.source())), camera.distanceToSqr(vec(f.arrival()))) <= 64 * 64) {
                    corners(matrix, departure, camera, alpha); corners(matrix, arrival, camera, alpha);
                    var out = buffers.getBuffer(Material.quads(MARKS[DisplacedProjectionMesh.frame(success.age)]));
                    // Original tp_mark atlas strips follow the endpoint coordinate strokes, never the flight path.
                    for (Point endpoint : List.of(f.source(), f.arrival())) for (var segment : DisplacedProjectionMesh.corners(endpoint))
                        stroke(out, matrix, segment.from(), segment.to(), camera, alpha);
                    var right = event.getCamera().getLeftVector(); var up = event.getCamera().getUpVector();
                    Vec3 horizontal = new Vec3(right.x(), right.y(), right.z()), vertical = new Vec3(up.x(), up.y(), up.z());
                    out = buffers.getBuffer(Material.quads(PARTICLE));
                    for (var p : DisplacedProjectionMesh.particles(f.source(), f.arrival(), success.age)) {
                        Vec3 at = vec(p.position()), a = horizontal.scale(p.size()), b = vertical.scale(p.size());
                        vertex(out, matrix, point(at.subtract(a).subtract(b)), camera, 0, 1, alpha);
                        vertex(out, matrix, point(at.subtract(a).add(b)), camera, 0, 0, alpha);
                        vertex(out, matrix, point(at.add(a).add(b)), camera, 1, 0, alpha);
                        vertex(out, matrix, point(at.add(a).subtract(b)), camera, 1, 1, alpha);
                    }
                }
            }
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }
    private static void corners(Matrix4f matrix, Box box, Vec3 camera, int alpha) {
        VertexConsumer out = buffers.getBuffer(Material.CORNERS);
        for (var line : SafeLandingProbeMesh.corners(box)) {
            Vec3 a = vec(line.from()), b = vec(line.to()), normal = b.subtract(a).normalize();
            out.addVertex(matrix, (float)(a.x - camera.x), (float)(a.y - camera.y), (float)(a.z - camera.z)).setColor(220, 225, 226, alpha)
                    .setNormal((float)normal.x, (float)normal.y, (float)normal.z);
            out.addVertex(matrix, (float)(b.x - camera.x), (float)(b.y - camera.y), (float)(b.z - camera.z)).setColor(220, 225, 226, alpha)
                    .setNormal((float)normal.x, (float)normal.y, (float)normal.z);
        }
    }
    private static void stroke(VertexConsumer out, Matrix4f matrix, Point from, Point to, Vec3 camera, int alpha) {
        Vec3 a = vec(from), b = vec(to), side = b.subtract(a).cross(camera.subtract(a)).normalize().scale(.009);
        if (side.lengthSqr() < 1e-10) return;
        // This original atlas band is translucent coordinate artwork, not an opaque sprite.
        vertex(out, matrix, point(a.subtract(side)), camera, .03125f, .25f, alpha);
        vertex(out, matrix, point(a.add(side)), camera, .03125f, .46875f, alpha);
        vertex(out, matrix, point(b.add(side)), camera, .484375f, .46875f, alpha);
        vertex(out, matrix, point(b.subtract(side)), camera, .484375f, .25f, alpha);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Point p, Vec3 camera, float u, float v, int alpha) {
        out.addVertex(matrix, (float)(p.x() - camera.x), (float)(p.y() - camera.y), (float)(p.z() - camera.z))
                .setUv(u, v).setColor(230, 160, 150, alpha);
    }
    private static Point point(Vec3 p) { return p == null ? null : new Point(p.x, p.y, p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    /** Scoped material: actual depth occlusion, color writes only, bounded texture cache and restored global state. */
    private static final class Material extends RenderType {
        private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
        private Material(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean c, boolean sort, Runnable set, Runnable reset) {
            super(n, f, m, s, c, sort, set, reset);
        }
        static RenderType quads(ResourceLocation texture) {
            return TYPES.computeIfAbsent(texture, t -> create("academy_displaced_projection_" + t.getPath(), DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(t, false, false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
        static final RenderType CORNERS = create("academy_displaced_projection_corners", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 2048, false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                        .setLineState(new LineStateShard(OptionalDouble.of(2))).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setOutputState(PARTICLES_TARGET).createCompositeState(false));
    }

    /** Read-only snapshot for the decoration layer (phase 0 = aiming/active, 1 = success, 2 = refusal/warning): points = source, arrival, impact. */
    private static double[] dp(cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        if (active != null) out.add(DecorSnap.of(true, 0, 0, active.id().token(), java.util.List.of(dp(active.source()), dp(active.arrival()), dp(active.impact()))));
        if (success != null && validOwner(success.owner, success.frame)) { var f = success.frame;
            out.add(DecorSnap.of(success.owner == mc.player, success.age, 1, f.id().token(), java.util.List.of(dp(f.source()), dp(f.arrival()), dp(f.impact())))); }
        return out;
    }
}

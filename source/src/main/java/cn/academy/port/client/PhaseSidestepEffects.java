package cn.academy.port.client;

import cn.academy.port.highpower.PhaseSidestep;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW312 native-move receipts only: bounded gray-white coordinate outlines and original afterimages. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class PhaseSidestepEffects {
    private static final ResourceLocation PARTICLE = texture("effects/tp_particle");
    private static final ResourceLocation[] MARKS = new ResourceLocation[PhaseSidestepMesh.SUCCESS_TICKS];
    private static final PhaseSidestepState STATE = new PhaseSidestepState();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static long generation;
    private static MultiBufferSource.BufferSource buffers;
    private static Cosmetic success;
    private static final class Cosmetic {
        final Entity owner;
        final PhaseSidestepState.Frame frame;
        int age;
        Cosmetic(Entity owner, PhaseSidestepState.Frame frame) { this.owner = owner; this.frame = frame; }
    }
    static { for (int i = 0; i < MARKS.length; i++) MARKS[i] = texture("effects/tp_mark/" + i); }
    private PhaseSidestepEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { PhaseSidestep.clientReceiver = PhaseSidestepEffects::receive; }
    }
    private static PhaseSidestepState.Frame frame(PhaseSidestep.Visual p) {
        return new PhaseSidestepState.Frame(new PhaseSidestepState.Identity(p.entity(), p.owner(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), point(p.source()), point(p.destination()), box(p.bounds()), p.moved(), p.reason(), p.left());
    }
    public static void receive(PhaseSidestep.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!PhaseSidestepState.valid(receipt)) return;
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
                    || !AcademyClient.phaseSidestepInputMatches(packet.input(), packet.epoch()))) return;
            if (local && !AcademyClient.phaseSidestepControlValid()) {
                AcademyClient.phaseSidestepCancelInput(); return;
            }
            if (!STATE.accept(receipt, local)) return;
            if (local) AcademyClient.phaseSidestepEndedInput(packet.input(), packet.epoch());
            if (packet.phase() == PhaseSidestepState.SUCCESS) {
                // One actual endpoint pair globally; the local receipt has priority over remote activity.
                if (success == null || local || success.owner != mc.player) success = new Cosmetic(owner, receipt);
            } else if (local) {
                String reason = "academy.highpower.phase_sidestep.reason." + packet.reason();
                var detail = net.minecraft.locale.Language.getInstance().has(reason) ? Component.translatable(reason)
                        : Component.translatable("academy.highpower.phase_sidestep.refused");
                mc.player.displayClientMessage(Component.translatable("academy.highpower.phase_sidestep.blocked", detail), true);
            }
        });
    }
    public static void abortLocal(long input, long epoch) { STATE.cancel(epoch, input); }
    public static void clear() { generation++; success = null; }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean validOwner(Entity owner, PhaseSidestepState.Frame frame) {
        return world != null && owner != null && owner.isAlive() && !owner.isRemoved()
                && world.getEntity(frame.id().entity()) == owner && owner.getUUID().equals(frame.id().owner());
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        return success != null && success.owner == mc.player && validOwner(success.owner, success.frame)
                ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (success != null && (!validOwner(success.owner, success.frame)
                || !mc.isPaused() && ++success.age >= PhaseSidestepMesh.SUCCESS_TICKS)) success = null;
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
            poses.translate(-camera.x, -camera.y, -camera.z); Matrix4f matrix = poses.last().pose();
            if (success != null && validOwner(success.owner, success.frame)) {
                var f = success.frame; int alpha = PhaseSidestepMesh.alpha(success.age);
                Box departure = PhaseSidestepMesh.sourceBounds(f.bounds(), f.source(), f.destination());
                if (loaded(departure) && loaded(f.bounds()) && camera.distanceToSqr(vec(f.destination())) <= 64 * 64) {
                    corners(matrix, departure, alpha); corners(matrix, f.bounds(), alpha);
                    var out = buffers.getBuffer(Material.quads(MARKS[PhaseSidestepMesh.frame(success.age)]));
                    for (Box bounds : List.of(departure, f.bounds())) for (var quad : PhaseSidestepMesh.biped(bounds))
                        for (var v : List.of(quad.a(), quad.b(), quad.c(), quad.d())) vertex(out, matrix, v.position(), v.u(), v.v(), alpha);
                    var right = event.getCamera().getLeftVector(); var up = event.getCamera().getUpVector();
                    Vec3 horizontal = new Vec3(right.x(), right.y(), right.z()), vertical = new Vec3(up.x(), up.y(), up.z());
                    out = buffers.getBuffer(Material.quads(PARTICLE));
                    for (var p : PhaseSidestepMesh.particles(f.source(), f.destination(), success.age)) {
                        Vec3 at = vec(p.position()), a = horizontal.scale(p.size()), b = vertical.scale(p.size());
                        vertex(out, matrix, point(at.subtract(a).subtract(b)), 0, 1, alpha);
                        vertex(out, matrix, point(at.subtract(a).add(b)), 0, 0, alpha);
                        vertex(out, matrix, point(at.add(a).add(b)), 1, 0, alpha);
                        vertex(out, matrix, point(at.add(a).subtract(b)), 1, 1, alpha);
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
    private static void corners(Matrix4f matrix, Box box, int alpha) {
        VertexConsumer out = buffers.getBuffer(Material.CORNERS);
        for (var line : PhaseSidestepMesh.corners(box)) {
            Vec3 a = vec(line.from()), b = vec(line.to()), normal = b.subtract(a).normalize();
            out.addVertex(matrix, (float)a.x, (float)a.y, (float)a.z).setColor(220, 225, 226, alpha)
                    .setNormal((float)normal.x, (float)normal.y, (float)normal.z);
            out.addVertex(matrix, (float)b.x, (float)b.y, (float)b.z).setColor(220, 225, 226, alpha)
                    .setNormal((float)normal.x, (float)normal.y, (float)normal.z);
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Point p, float u, float v, int alpha) {
        out.addVertex(matrix, (float)p.x(), (float)p.y(), (float)p.z()).setUv(u, v).setColor(230, 240, 244, alpha);
    }
    private static Point point(Vec3 p) { return p == null ? null : new Point(p.x, p.y, p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private static Box box(AABB b) { return b == null ? null : new Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ); }
    /** Scoped material: actual depth occlusion, color writes only, bounded texture cache and restored global state. */
    private static final class Material extends RenderType {
        private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
        private Material(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean c, boolean sort, Runnable set, Runnable reset) {
            super(n, f, m, s, c, sort, set, reset);
        }
        static RenderType quads(ResourceLocation texture) {
            return TYPES.computeIfAbsent(texture, t -> create("academy_phase_sidestep_" + t.getPath(), DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(t, false, false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
        static final RenderType CORNERS = create("academy_phase_sidestep_corners", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 2048, false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                        .setLineState(new LineStateShard(OptionalDouble.of(2))).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setOutputState(PARTICLES_TARGET).createCompositeState(false));
    }

    /** Read-only snapshot for the decoration layer (phase 0 = aiming/active, 1 = success, 2 = refusal/warning): points = source, destination; numbers = left flag. */
    private static double[] dp(cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        if (success != null && validOwner(success.owner, success.frame)) { var f = success.frame;
            out.add(DecorSnap.of(success.owner == mc.player, success.age, 1, f.id().token(), java.util.List.of(dp(f.source()), dp(f.destination())), f.left() ? 1 : 0)); }
        return out;
    }
}

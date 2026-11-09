package cn.academy.port.client;

import cn.academy.port.highpower.DeflectionStrike;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
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

/** NEW411 white/gray hand pressure, decorative crouch fan, and native-confirmed contact dust only. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class DeflectionStrikeEffects {
    private static final ResourceLocation GLOW_LINE = texture("glow_line"), GLOW_CIRCLE = texture("glow_circle");
    private static final DeflectionStrikeState STATE = new DeflectionStrikeState();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Entity visualOwner;
    private static Object connection;
    private static long generation;
    private static ByteBufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private DeflectionStrikeEffects() {}
    private static ResourceLocation texture(String name) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/" + name + ".png"); }

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { DeflectionStrike.clientReceiver = DeflectionStrikeEffects::receive; }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> Minecraft.getInstance().execute(() -> {
                AcademyClient.deflectionStrikeCancelInput(); clear();
                if (buffers != null) buffers.endBatch();
                if (storage != null) storage.close();
                buffers = null; storage = null; Material.TYPES.clear();
            }));
        }
    }
    private static DeflectionStrikeState.Frame frame(DeflectionStrike.Visual p) {
        return new DeflectionStrikeState.Frame(new DeflectionStrikeState.Identity(p.owner(), p.ownerUuid(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), p.slot(), p.crouch(), point(p.origin()), point(p.end()), p.status());
    }
    public static void receive(DeflectionStrike.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!DeflectionStrikeState.valid(receipt)) return;
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
                    || !AcademyClient.deflectionStrikeInputMatches(packet.input(), packet.epoch()))) return;
            if (local && !AcademyClient.deflectionStrikeControlValid()) { AcademyClient.deflectionStrikeCancelInput(); return; }
            var previous = STATE.active();
            if (!STATE.accept(receipt, local)) return;
            if (STATE.owns(receipt.id())) visualOwner = owner;
            else if (STATE.active() == null) visualOwner = null;
            if (previous != null && previous.local() && !STATE.owns(previous.id()))
                AcademyClient.deflectionStrikeEndedInput(previous.id().input(), previous.id().epoch());
            if (local && packet.phase() == DeflectionStrikeState.ENDED)
                AcademyClient.deflectionStrikeEndedInput(packet.input(), packet.epoch());
        });
    }
    public static void abortLocal(long input, long epoch) { STATE.cancel(epoch, input); if (STATE.active() == null) visualOwner = null; }
    public static void clear() { generation++; STATE.clear(); visualOwner = null; }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean validOwner(DeflectionStrikeState.Controller cast, Minecraft mc) {
        if (cast == null || world == null) return false;
        Entity owner = world.getEntity(cast.id().entity());
        return owner == visualOwner && owner instanceof Player && owner.isAlive() && !owner.isRemoved() && owner.getUUID().equals(cast.id().owner())
                && (!cast.local() || owner == mc.player && AcademyClient.singleOwnerEpochMatches(cast.id().epoch())
                && AcademyClient.deflectionStrikeControlValid());
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); var cast = STATE.active();
        return cast != null && cast.local() && validOwner(cast, mc) ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); var cast = STATE.active();
        boolean valid = validOwner(cast, mc);
        // A GUI/focus change can occur after Pre. Preserve the identity until its scoped abort is sent.
        if (cast != null && cast.local() && !valid) AcademyClient.deflectionStrikeCancelInput();
        var ended = STATE.tick(mc.isPaused(), valid);
        if (STATE.active() == null) visualOwner = null;
        if (ended != null && cast.local()) AcademyClient.deflectionStrikeEndedInput(ended.input(), ended.epoch());
    }
    private static boolean visible(DeflectionStrikeState.Controller cast, RenderLevelStageEvent event, Vec3 camera) {
        Vec3 a = vec(cast.origin()), b = vec(cast.end());
        if (Math.min(camera.distanceToSqr(a), camera.distanceToSqr(b)) > DeflectionStrikeGeometry.CULL_DISTANCE * DeflectionStrikeGeometry.CULL_DISTANCE) return false;
        AABB bounds = new AABB(a, b).inflate(1);
        for (int x = Math.floorDiv((int)Math.floor(bounds.minX), 16); x <= Math.floorDiv((int)Math.floor(bounds.maxX), 16); x++)
            for (int z = Math.floorDiv((int)Math.floor(bounds.minZ), 16); z <= Math.floorDiv((int)Math.floor(bounds.maxZ), 16); z++)
                if (!world.hasChunkAt(new BlockPos(x * 16, (int)Math.floor(bounds.minY), z * 16))) return false;
        return event.getFrustum().isVisible(bounds);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); var cast = STATE.active();
        Vec3 camera = event.getCamera().getPosition();
        if (!validOwner(cast, mc) || !visible(cast, event, camera)) return;
        if (buffers == null) { storage = new ByteBufferBuilder(32768); buffers = MultiBufferSource.immediate(storage); }
        double partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double age = cast.age() + partial;
        var geometry = DeflectionStrikeGeometry.shape(cast, age);
        if (geometry.vertexCount() > DeflectionStrikeGeometry.MAX_VERTICES) return;
        var look = event.getCamera().getLookVector(); Point forward = new Point(look.x(), look.y(), look.z());
        var left = event.getCamera().getLeftVector(); Vec3 horizontal = new Vec3(left.x(), left.y(), left.z());
        var up = event.getCamera().getUpVector(); Vec3 vertical = new Vec3(up.x(), up.y(), up.z());
        var poses = event.getPoseStack(); var modelView = RenderSystem.getModelViewStack();
        float[] color = RenderSystem.getShaderColor().clone(); modelView.pushMatrix(); poses.pushPose();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = poses.last().pose();
            // No float world translation. Every vertex below is already camera-relative in double.
            // Finish the soft shoulder before the crisp core. Both use the original glow_line, with no extra contour.
            var shoulder = buffers.getBuffer(Material.quads(Pass.SHOULDER));
            for (var stroke : geometry.strokes()) stroke(shoulder, matrix, stroke,
                    DeflectionStrikeGeometry.profile(stroke, age, false), point(camera), forward, horizontal);
            buffers.endBatch(Material.quads(Pass.SHOULDER));
            var core = buffers.getBuffer(Material.quads(Pass.CORE));
            for (var stroke : geometry.strokes()) stroke(core, matrix, stroke,
                    DeflectionStrikeGeometry.profile(stroke, age, true), point(camera), forward, horizontal);
            buffers.endBatch(Material.quads(Pass.CORE));
            var dust = buffers.getBuffer(Material.quads(Pass.DUST));
            for (var particle : geometry.particles()) {
                Vec3 at = vec(DeflectionStrikeGeometry.relative(particle.position(), point(camera)));
                if (at.dot(vec(forward)) <= DeflectionStrikeGeometry.NEAR_PLANE + particle.size()) continue;
                Vec3 right = horizontal.scale(particle.size()), top = vertical.scale(particle.size());
                vertex(dust, matrix, at.subtract(right).subtract(top), 0, 1, 190, particle.alpha());
                vertex(dust, matrix, at.subtract(right).add(top), 0, 0, 190, particle.alpha());
                vertex(dust, matrix, at.add(right).add(top), 1, 0, 190, particle.alpha());
                vertex(dust, matrix, at.add(right).subtract(top), 1, 1, 190, particle.alpha());
            }
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }
    private static void stroke(VertexConsumer out, Matrix4f matrix, DeflectionStrikeGeometry.Stroke stroke,
                               DeflectionStrikeGeometry.Profile profile, Point camera, Point forward, Vec3 fallback) {
        if (profile.alpha() <= 0) return;
        var clipped = DeflectionStrikeGeometry.clipNear(new DeflectionStrikeGeometry.Stroke(
                DeflectionStrikeGeometry.relative(stroke.from(), camera), DeflectionStrikeGeometry.relative(stroke.to(), camera),
                profile.width(), profile.alpha(), stroke.role()), forward, profile.width() / 2);
        if (clipped == null) return;
        Vec3 a = vec(clipped.from()), b = vec(clipped.to());
        Vec3 side = b.subtract(a).cross(a.add(b).scale(-.5)).normalize();
        if (side.lengthSqr() < 1e-10) side = fallback;
        side = side.scale(clipped.width() / 2);
        if (stroke.role() == DeflectionStrikeGeometry.Role.HAND) {
            // Two half-quads replace this stroke's shoulder + core quads, preserving the eight-vertex budget.
            Vec3 mid = a.add(b).scale(.5);
            int peak = DeflectionStrikeGeometry.handAlpha(profile.alpha(), .5, mid.length());
            if (peak <= 0) return;
            vertex(out, matrix, a.subtract(side), 0, profile.maxV(), profile.gray(), 0);
            vertex(out, matrix, a.add(side), 0, profile.minV(), profile.gray(), 0);
            vertex(out, matrix, mid.add(side), .5f, profile.minV(), profile.gray(), peak);
            vertex(out, matrix, mid.subtract(side), .5f, profile.maxV(), profile.gray(), peak);
            vertex(out, matrix, mid.subtract(side), .5f, profile.maxV(), profile.gray(), peak);
            vertex(out, matrix, mid.add(side), .5f, profile.minV(), profile.gray(), peak);
            vertex(out, matrix, b.add(side), 1, profile.minV(), profile.gray(), 0);
            vertex(out, matrix, b.subtract(side), 1, profile.maxV(), profile.gray(), 0);
            return;
        }
        vertex(out, matrix, a.subtract(side), 0, profile.maxV(), profile.gray(), clipped.alpha());
        vertex(out, matrix, a.add(side), 0, profile.minV(), profile.gray(), clipped.alpha());
        vertex(out, matrix, b.add(side), 1, profile.minV(), profile.gray(), clipped.alpha());
        vertex(out, matrix, b.subtract(side), 1, profile.maxV(), profile.gray(), clipped.alpha());
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 relative, float u, float v, int gray, int alpha) {
        out.addVertex(matrix, (float)relative.x, (float)relative.y, (float)relative.z).setUv(u, v).setColor(gray, gray, gray, alpha);
    }
    private static Point point(Vec3 p) { return p == null ? null : new Point(p.x, p.y, p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private enum Pass { SHOULDER, CORE, DUST }
    /** Two bounded pressure materials and confirmed dust, all depth-tested with color-only writes. */
    private static final class Material extends RenderType {
        private static final Map<Pass, RenderType> TYPES = new HashMap<>();
        private Material(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling,
                         boolean sorted, Runnable set, Runnable reset) { super(name, format, mode, size, crumbling, sorted, set, reset); }
        static RenderType quads(Pass pass) {
            return TYPES.computeIfAbsent(pass, p -> create("academy_deflection_strike_" + p, DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            // These original textures are shared with classic effects; preserve their nearest filtering.
                            .setTextureState(new TextureStateShard(p == Pass.DUST ? GLOW_CIRCLE : GLOW_LINE, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
    }

    /** Read-only snapshot for the decoration layer: points = origin, end, then every contact. */
    private static double[] dp(Point p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); var cast = STATE.active();
        if (cast == null || world == null || !validOwner(cast, mc)) return java.util.List.of();
        var points = new java.util.ArrayList<double[]>(); points.add(dp(cast.origin())); points.add(dp(cast.end()));
        for (var contact : cast.contacts()) points.add(dp(contact.point()));
        return java.util.List.of(DecorSnap.of(cast.local(), cast.age(), 0, cast.id().token(), points, cast.crouch() ? 1 : 0));
    }
}

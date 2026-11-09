package cn.academy.port.client;

import cn.academy.port.highpower.DisplacedNeedleEntity;
import cn.academy.port.highpower.RecoveryMark;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW323 eight bounded physical needle/anchor pairs, one selected label, receipt-only seven-frame recovery. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class RecoveryMarkEffects {
    private static final double CULL_DISTANCE = 64, NEAR_PLANE = .08;
    private static final ResourceLocation PARTICLE = texture("effects/tp_particle");
    private static final ResourceLocation[] MARKS = new ResourceLocation[DisplacedProjectionMesh.SUCCESS_TICKS];
    private static final RecoveryMarkState STATE = new RecoveryMarkState();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static long generation;
    private static MultiBufferSource.BufferSource buffers;
    private static Cosmetic success;
    private static final class Cosmetic {
        final Entity owner;
        final RecoveryMarkState.Frame frame;
        int age;
        Cosmetic(Entity owner, RecoveryMarkState.Frame frame) { this.owner = owner; this.frame = frame; }
    }
    static { for (int i = 0; i < MARKS.length; i++) MARKS[i] = texture("effects/tp_mark/" + i); }
    private RecoveryMarkEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { RecoveryMark.clientReceiver = RecoveryMarkEffects::receive; }
    }
    private static RecoveryMarkState.Frame frame(RecoveryMark.Visual p) {
        return new RecoveryMarkState.Frame(new RecoveryMarkState.Identity(p.entity(), p.owner(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), point(p.source()), point(p.anchor()), p.needleEntity(), p.needleUuid(),
                p.remainingTicks(), p.accepted(), p.reason(), p.handles().stream().map(h -> new RecoveryMarkState.Handle(
                        h.token(), h.needleEntity(), h.needleUuid(), point(h.source()), point(h.anchor()), h.remainingTicks())).toList());
    }
    public static void receive(RecoveryMark.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!RecoveryMarkState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.entity());
            if (!(owner instanceof Player) || !owner.getUUID().equals(packet.owner()) || !owner.isAlive() || owner.isRemoved()) return;
            boolean local = owner == mc.player;
            if (!local && packet.phase() != RecoveryMarkState.RECOVERED) return;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.recoveryMarkInputMatches(packet.input(), packet.epoch()))) return;
            if (local && !AcademyClient.recoveryMarkControlValid()) { AcademyClient.recoveryMarkCancelInput(); return; }
            if (packet.phase() == RecoveryMarkState.MARKED || packet.phase() == RecoveryMarkState.RECOVERED) {
                Entity needle = capturedWorld.getEntity(packet.needleEntity());
                if (needle != null && !physicalNeedle(needle, packet.needleUuid())) return;
            }
            for (var handle : receipt.handles()) {
                Entity needle = capturedWorld.getEntity(handle.needleEntity());
                if (needle != null && !physicalNeedle(needle, handle.needleUuid())) return;
            }
            if (!STATE.accept(receipt, local)) return;
            if (local) {
                if (STATE.handleCount() > 0) AcademyClient.recoveryMarkAcceptedInput(packet.input(), packet.epoch(), STATE.remainingTicks());
                else AcademyClient.recoveryMarkEndedInput(packet.input(), packet.epoch());
            }
            if (packet.phase() != RecoveryMarkState.MARKED) {
                if (packet.phase() == RecoveryMarkState.RECOVERED) {
                    // One endpoint pair globally, with local confirmed recovery taking priority.
                    if (success == null || local || success.owner != mc.player) success = new Cosmetic(owner, receipt);
                } else if (packet.phase() == RecoveryMarkState.REFUSED && local) {
                    String reason = "academy.highpower.recovery_mark.reason." + packet.reason();
                    var detail = net.minecraft.locale.Language.getInstance().has(reason) ? Component.translatable(reason)
                            : Component.translatable("academy.highpower.recovery_mark.refused");
                    mc.player.displayClientMessage(Component.translatable("academy.highpower.recovery_mark.blocked", detail), true);
                }
            }
        });
    }
    public static void abortLocal(long input, long epoch) {
        STATE.cancel(epoch, input);
        if (success != null && success.owner == Minecraft.getInstance().player
                && success.frame.id().epoch() == epoch && success.frame.id().input() <= input) success = null;
    }
    public static void clear() { generation++; STATE.clear(); success = null; }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        if (STATE.active() != null) AcademyClient.recoveryMarkCancelInput();
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean validOwner(Entity owner, RecoveryMarkState.Frame frame) {
        return world != null && owner != null && owner.isAlive() && !owner.isRemoved()
                && world.getEntity(frame.id().entity()) == owner && owner.getUUID().equals(frame.id().owner());
    }
    private static boolean locallyOwned(RecoveryMarkState.Frame frame, Minecraft mc) {
        return frame != null && validOwner(mc.player, frame) && AcademyClient.singleOwnerEpochMatches(frame.id().epoch())
                && AcademyClient.recoveryMarkInputMatches(frame.id().input(), frame.id().epoch()) && AcademyClient.recoveryMarkControlValid();
    }
    private static boolean physicalNeedle(Entity entity, UUID uuid) {
        return entity != null && entity.isAlive() && !entity.isRemoved() && entity.getUUID().equals(uuid)
                && (entity instanceof DisplacedNeedleEntity || entity.getClass() == ItemEntity.class
                && entity.getType() == EntityType.ITEM && DisplacedNeedleEntity.isPlainNeedle(((ItemEntity)entity).getItem()));
    }
    private static Entity needle(RecoveryMarkState.Handle handle) {
        if (world == null || handle == null) return null;
        Entity entity = world.getEntity(handle.needleEntity());
        return physicalNeedle(entity, handle.needleUuid()) ? entity : null;
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        return STATE.markerVisible() && locallyOwned(STATE.active(), mc)
                || success != null && success.owner == mc.player && validOwner(success.owner, success.frame)
                ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (STATE.tick(mc.isPaused(), locallyOwned(STATE.active(), mc)) != null) AcademyClient.recoveryMarkCancelInput();
        if (success != null && (!validOwner(success.owner, success.frame)
                || !mc.isPaused() && ++success.age >= DisplacedProjectionMesh.SUCCESS_TICKS)) success = null;
    }
    /** Chunk presence checks are bounded by a small marker box and never request missing chunks. */
    private static boolean loaded(Box bounds) {
        if (world == null || bounds == null || !bounds.body()) return false;
        for (int x = Math.floorDiv((int)Math.floor(bounds.minX()), 16); x <= Math.floorDiv((int)Math.floor(bounds.maxX()), 16); x++)
            for (int z = Math.floorDiv((int)Math.floor(bounds.minZ()), 16); z <= Math.floorDiv((int)Math.floor(bounds.maxZ()), 16); z++)
                if (!world.hasChunkAt(new BlockPos(x * 16, (int)Math.floor(bounds.minY()), z * 16))) return false;
        return true;
    }
    private static boolean visible(RenderLevelStageEvent event, Point point, Vec3 camera) {
        Box b = DisplacedProjectionMesh.bounds(point);
        return loaded(b) && camera.distanceToSqr(vec(point)) <= CULL_DISTANCE * CULL_DISTANCE && event.getFrustum().isVisible(aabb(b));
    }
    private static boolean labelVisible(Entity owner, Vec3 camera, Vec3 target) {
        if (world == null || camera.distanceToSqr(target) > CULL_DISTANCE * CULL_DISTANCE) return false;
        int steps = Math.max(1, (int)Math.ceil(camera.distanceTo(target) / 8));
        for (int i = 0; i <= steps; i++) if (!world.hasChunkAt(BlockPos.containing(camera.lerp(target, (double)i / steps)))) return false;
        return world.clip(new ClipContext(camera, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getType() == HitResult.Type.MISS;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var active = STATE.active(); if (world == null || active == null && success == null) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        var poses = event.getPoseStack(); Vec3 camera = event.getCamera().getPosition();
        var direction = event.getCamera().getLookVector(); Vec3 forward = new Vec3(direction.x(), direction.y(), direction.z());
        var modelView = RenderSystem.getModelViewStack(); float[] color = RenderSystem.getShaderColor().clone();
        modelView.pushMatrix(); poses.pushPose();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = poses.last().pose();
            if (STATE.markerVisible() && locallyOwned(active, mc)) {
                var labelHandle = STATE.labelHandle(aimedNeedle(camera, forward));
                var anchors = new HashSet<Point>();
                for (var handle : STATE.handles()) {
                    // Missing, merged or replaced physical entities hide both endpoints; a stale receipt is never a ghost.
                    var needle = needle(handle); if (needle == null) continue;
                    Point source = point(needle.getPosition(event.getPartialTick().getGameTimeDeltaPartialTick(false)));
                    if (visible(event, source, camera)) corners(matrix, source, camera, forward, 205);
                    if (visible(event, handle.anchor(), camera)) {
                        if (anchors.add(handle.anchor())) corners(matrix, handle.anchor(), camera, forward, 205);
                        if (labelHandle != null && labelHandle.token() == handle.token()) label(event, handle, camera, forward);
                    }
                }
            }
            if (success != null && validOwner(success.owner, success.frame)) renderSuccess(event, matrix, camera, forward);
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }
    private static void renderSuccess(RenderLevelStageEvent event, Matrix4f matrix, Vec3 camera, Vec3 forward) {
        var f = success.frame; int alpha = DisplacedProjectionMesh.alpha(success.age);
        for (Point endpoint : List.of(f.source(), f.anchor())) if (visible(event, endpoint, camera)) {
            corners(matrix, endpoint, camera, forward, alpha);
            var out = buffers.getBuffer(Material.quads(MARKS[DisplacedProjectionMesh.frame(success.age)]));
            // The original TP atlas follows compact coordinate strokes; there is no synthetic body or impact.
            for (var segment : DisplacedProjectionMesh.corners(endpoint)) stroke(out, matrix, segment.from(), segment.to(), camera, forward, alpha);
        }
        var right = event.getCamera().getLeftVector(); var up = event.getCamera().getUpVector();
        Vec3 horizontal = new Vec3(right.x(), right.y(), right.z()), vertical = new Vec3(up.x(), up.y(), up.z());
        var out = buffers.getBuffer(Material.quads(PARTICLE));
        for (var p : DisplacedProjectionMesh.particles(f.source(), f.anchor(), success.age)) {
            Vec3 at = vec(p.position()), a = horizontal.scale(p.size()), b = vertical.scale(p.size());
            if (!visible(event, p.position(), camera) || at.subtract(camera).dot(forward) <= NEAR_PLANE + p.size()) continue;
            vertex(out, matrix, point(at.subtract(a).subtract(b)), camera, 0, 1, alpha);
            vertex(out, matrix, point(at.subtract(a).add(b)), camera, 0, 0, alpha);
            vertex(out, matrix, point(at.add(a).add(b)), camera, 1, 0, alpha);
            vertex(out, matrix, point(at.add(a).subtract(b)), camera, 1, 1, alpha);
        }
    }
    private static UUID aimedNeedle(Vec3 camera, Vec3 forward) {
        Vec3 end = camera.add(forward.scale(24)); UUID result = null; double nearest = 24 * 24;
        for (var handle : STATE.handles()) {
            var entity = needle(handle); if (entity == null) continue;
            var hit = entity.getBoundingBox().inflate(.1).clip(camera, end);
            if (hit.isEmpty() || !labelVisible(Minecraft.getInstance().player, camera, hit.get())) continue;
            double distance = camera.distanceToSqr(hit.get());
            if (distance < nearest) { nearest = distance; result = handle.needleUuid(); }
        }
        return result;
    }
    private static void label(RenderLevelStageEvent event, RecoveryMarkState.Handle frame, Vec3 camera, Vec3 forward) {
        Minecraft mc = Minecraft.getInstance(); if (mc.screen != null || mc.options.hideGui) return;
        Vec3 at = vec(frame.anchor()).add(0, .34, 0), relative = at.subtract(camera);
        if (!labelVisible(mc.player, camera, at)) return;
        String distance = String.format(Locale.ROOT, "%.1f m", mc.player.position().distanceTo(vec(frame.anchor())));
        var text = Component.translatable("academy.highpower.recovery_mark.marked", STATE.handleCount(), distance,
                (frame.remainingTicks() + 19) / 20);
        float scale = RecoveryMarkState.labelScale(relative.dot(forward), relative.length(), mc.font.width(text));
        if (scale <= 0) return;
        var poses = event.getPoseStack(); poses.pushPose();
        try {
            poses.translate(relative.x, relative.y, relative.z); poses.mulPose(event.getCamera().rotation()); poses.scale(scale, -scale, scale);
            mc.font.drawInBatch(text, -mc.font.width(text) / 2f, 0, 0xFFE2E7E8, true,
                    poses.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 15728880);
        } finally { poses.popPose(); }
    }
    private static void corners(Matrix4f matrix, Point endpoint, Vec3 camera, Vec3 forward, int alpha) {
        VertexConsumer out = buffers.getBuffer(Material.CORNERS);
        for (var line : DisplacedProjectionMesh.corners(endpoint)) {
            Vec3 a = vec(RecoveryMarkState.relative(line.from(), point(camera))), b = vec(RecoveryMarkState.relative(line.to(), point(camera)));
            double da = a.dot(forward), db = b.dot(forward);
            if (da <= NEAR_PLANE && db <= NEAR_PLANE) continue;
            if (da < NEAR_PLANE) a = a.lerp(b, (NEAR_PLANE - da) / (db - da));
            else if (db < NEAR_PLANE) b = a.lerp(b, (NEAR_PLANE - da) / (db - da));
            Vec3 normal = b.subtract(a).normalize();
            out.addVertex(matrix, (float)a.x, (float)a.y, (float)a.z).setColor(220, 225, 226, alpha)
                    .setNormal((float)normal.x, (float)normal.y, (float)normal.z);
            out.addVertex(matrix, (float)b.x, (float)b.y, (float)b.z).setColor(220, 225, 226, alpha)
                    .setNormal((float)normal.x, (float)normal.y, (float)normal.z);
        }
    }
    private static void stroke(VertexConsumer out, Matrix4f matrix, Point from, Point to, Vec3 camera, Vec3 forward, int alpha) {
        Vec3 a = vec(from), b = vec(to), side = b.subtract(a).cross(camera.subtract(a)).normalize().scale(.009);
        if (side.lengthSqr() < 1e-10 || a.subtract(camera).dot(forward) <= NEAR_PLANE + .009
                || b.subtract(camera).dot(forward) <= NEAR_PLANE + .009) return;
        vertex(out, matrix, point(a.subtract(side)), camera, .03125f, .25f, alpha);
        vertex(out, matrix, point(a.add(side)), camera, .03125f, .46875f, alpha);
        vertex(out, matrix, point(b.add(side)), camera, .484375f, .46875f, alpha);
        vertex(out, matrix, point(b.subtract(side)), camera, .484375f, .25f, alpha);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Point p, Vec3 camera, float u, float v, int alpha) {
        Point relative = RecoveryMarkState.relative(p, point(camera));
        out.addVertex(matrix, (float)relative.x(), (float)relative.y(), (float)relative.z()).setUv(u, v).setColor(230, 240, 244, alpha);
    }
    private static Point point(Vec3 p) { return p == null ? null : new Point(p.x, p.y, p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private static AABB aabb(Box b) { return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()); }
    private static final class Material extends RenderType {
        private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
        private Material(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean c, boolean sort, Runnable set, Runnable reset) {
            super(n, f, m, s, c, sort, set, reset);
        }
        static RenderType quads(ResourceLocation texture) {
            return TYPES.computeIfAbsent(texture, t -> create("academy_recovery_mark_" + t.getPath(), DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(t, false, false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
        static final RenderType CORNERS = create("academy_recovery_mark_corners", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 2048, false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                        .setLineState(new LineStateShard(OptionalDouble.of(2))).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setOutputState(PARTICLES_TARGET).createCompositeState(false));
    }

    /** Read-only snapshot for the decoration layer (phase 0 = aiming/active, 1 = success, 2 = refusal/warning): points = source, anchor, then every handle source; numbers = remaining ticks. */
    private static double[] dp(cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        var a = STATE.active();
        for (var f : new RecoveryMarkState.Frame[] {a, success != null && validOwner(success.owner, success.frame) ? success.frame : null}) {
            if (f == null) continue;
            var points = new java.util.ArrayList<double[]>(java.util.List.of(dp(f.source()), dp(f.anchor())));
            if (f.handles() != null) for (var h : f.handles()) if (points.size() < 10) points.add(dp(h.source()));
            boolean done = f != a;
            out.add(DecorSnap.of(!done || success.owner == mc.player, done ? success.age : 0, done ? 1 : 0, f.id().token(), points, f.remainingTicks()));
        }
        return out;
    }
}

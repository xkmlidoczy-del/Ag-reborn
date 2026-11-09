package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.ScatterPlates;
import cn.academy.port.highpower.core.ScatterPlateGeometry;
import cn.academy.port.highpower.core.ScatterPlateGeometry.Vector;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW212: one transparent mesh controller per caster, driven exclusively by authoritative receipts. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ScatterPlateEffects {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("academy", "textures/port/white.png");
    private static final int MAX_VISIBLE_CONTEXTS = 4;
    private static final ScatterPlateState STATE = new ScatterPlateState();
    private static final Map<UUID, Marker> MARKERS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static MultiBufferSource.BufferSource buffers;
    private static final class Marker {
        final Entity owner;
        final ScatterPlateState.Lease lease = new ScatterPlateState.Lease();
        final Vec3[] hitPoints = new Vec3[3];
        ScatterPlates.Visual state;
        Marker(Entity owner, ScatterPlates.Visual packet) { this.owner = owner; update(packet); }
        ScatterPlateState.Identity identity() { return frame(state).id(); }
        void update(ScatterPlates.Visual packet) {
            state = packet; lease.received(frame(packet));
            if (packet.phase() == ScatterPlateState.HIT) hitPoints[packet.plate()] = packet.point();
        }
    }
    private ScatterPlateEffects() {}
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { ScatterPlates.clientReceiver = ScatterPlateEffects::receive; }
    }
    private static ScatterPlateState.Frame frame(ScatterPlates.Visual p) {
        return new ScatterPlateState.Frame(new ScatterPlateState.Identity(p.entity(), p.owner(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), point(p.center()), point(p.axis()), p.liveMask(), p.plate(), point(p.point()));
    }
    public static void receive(ScatterPlates.Visual packet) {
        if (packet == null || packet.center() == null || packet.axis() == null || packet.point() == null) return;
        var frame = frame(packet); if (!ScatterPlateState.valid(frame)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.entity());
            if (owner == null || !owner.getUUID().equals(packet.owner())) return;
            Marker old = MARKERS.get(packet.owner());
            if (old != null && old.identity().equals(frame.id()) && old.owner != owner) return;
            boolean local = owner == mc.player;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch()) || !AcademyClient.scatterPlateInputMatches(packet.input(), packet.epoch()))) return;
            if (packet.phase() != ScatterPlateState.ENDED && (!owner.isAlive() || owner.isRemoved() || local && !localEligible())) return;
            if (!STATE.accept(frame, local)) return;
            if (packet.phase() == ScatterPlateState.ENDED) {
                MARKERS.remove(packet.owner());
                if (local) AcademyClient.scatterPlateEndedInput(packet.input(), packet.epoch());
                return;
            }
            if (local) AcademyClient.scatterPlateAcceptedInput(packet.input(), packet.epoch());
            if (old == null || !old.identity().equals(frame.id())) {
                if (old == null && MARKERS.size() >= ScatterPlateState.MAX_CONTEXTS) { STATE.retire(frame.id()); return; }
                MARKERS.put(packet.owner(), new Marker(owner, packet));
            } else old.update(packet);
        });
    }
    public static void abortLocal(long input, long epoch) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); STATE.cancel(epoch, input);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        if (marker != null && marker.state.epoch() == epoch && marker.state.input() <= input) {
            STATE.retire(marker.identity()); MARKERS.remove(mc.player.getUUID());
        }
    }
    public static void clear() { generation++; STATE.clear(); MARKERS.clear(); }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear();
        // Late same-connection packets remain retired across world/player replacement.
        if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean localEligible() {
        Minecraft mc = Minecraft.getInstance(); var s = AcademyClient.state;
        return mc.screen == null && mc.isWindowActive() && mc.getCameraEntity() == mc.player
                && mc.player != null && !mc.player.isSpectator() && s != null && s.activated && s.overloadFine && !s.interfering
                && "meltdowner".equals(s.category) && s.level >= 1 && s.learned(ScatterPlates.ID)
                && s.presets.currentContains(ScatterPlates.ID) && SkillCatalog.enabled("meltdowner", ScatterPlates.ID);
    }
    private static boolean validOwner(Marker marker, Minecraft mc) {
        return world != null && marker.owner.isAlive() && !marker.owner.isRemoved() && world.getEntity(marker.owner.getId()) == marker.owner
                && (marker.owner != mc.player || localEligible() && AcademyClient.singleOwnerEpochMatches(marker.state.epoch())
                && AcademyClient.scatterPlateInputMatches(marker.state.input(), marker.state.epoch()));
    }
    public static boolean activeLocal() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        return marker != null && validOwner(marker, mc) && marker.lease.quiet() < ScatterPlateState.QUIET_TICKS;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Iterator<Marker> it = MARKERS.values().iterator();
        while (it.hasNext()) {
            Marker marker = it.next();
            if (!marker.lease.tick(mc.isPaused(), validOwner(marker, mc))) {
                STATE.retire(marker.identity()); it.remove();
                if (marker.owner == mc.player) AcademyClient.scatterPlateEndedInput(marker.state.input(), marker.state.epoch());
            }
        }
    }
    /** Contact accents need current block visibility; all plate surfaces retain ordinary world depth testing. */
    private static boolean visible(Entity owner, Vec3 camera, Vec3 point) {
        if (world == null || !ScatterPlateMesh.validPoint(point(camera)) || !ScatterPlateMesh.validPoint(point(point))
                || camera.distanceToSqr(point) > 64 * 64) return false;
        int steps = Math.max(1, (int)Math.ceil(camera.distanceTo(point) / 4));
        for (int i = 0; i <= steps; i++) if (!world.hasChunkAt(BlockPos.containing(camera.lerp(point, (double)i / steps)))) return false;
        var hit = world.clip(new ClipContext(camera, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(point) < .0001;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); if (world == null || MARKERS.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        List<Marker> visibleMarkers = MARKERS.values().stream().filter(m -> validOwner(m, mc) && m.owner.distanceToSqr(camera) <= 64 * 64)
                .sorted(Comparator.comparingInt(m -> m.owner == mc.player ? 0 : 1)).limit(MAX_VISIBLE_CONTEXTS).toList();
        if (visibleMarkers.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(131072));
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        try {
            modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = event.getPoseStack().last().pose();
            var facing = event.getCamera().getLookVector(); Vector look = new Vector(facing.x(), facing.y(), facing.z());
            VertexConsumer out = buffers.getBuffer(ClassicRenderTypes.world(WHITE));
            for (Marker marker : visibleMarkers) {
                // Exact last authoritative pose, refreshed every four server ticks. Camera/FOV never changes the slab.
                var pose = ScatterPlateGeometry.pose(point(marker.state.center()), point(marker.state.axis()));
                if (pose == null) continue;
                var budget = new ScatterPlateMesh.Budget();
                for (int plate = 0; plate < 3; plate++) {
                    int age = marker.lease.hitAge(plate); boolean live = (marker.state.liveMask() & 1 << plate) != 0;
                    for (var face : ScatterPlateMesh.plate(pose, plate, live, age)) draw(out, matrix, face, camera, look, budget);
                    Vec3 hitPoint = marker.hitPoints[plate];
                    if (hitPoint != null && visible(marker.owner, camera, hitPoint))
                        for (var face : ScatterPlateMesh.contact(pose, plate, point(hitPoint), age)) draw(out, matrix, face, camera, look, budget);
                }
            }
        } finally {
            try { buffers.endBatch(); } finally { modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); }
        }
    }
    private static void draw(VertexConsumer out, Matrix4f matrix, ScatterPlateMesh.Face face, Vec3 camera, Vector look, ScatterPlateMesh.Budget budget) {
        if (face.alpha() <= 0) return;
        for (var clipped : ScatterPlateMesh.nearClip(face, point(camera), look)) {
            if (!budget.take()) return;
            vertex(out, matrix, clipped.a(), camera, 0, 0, clipped.color(), clipped.alpha());
            vertex(out, matrix, clipped.b(), camera, 0, 1, clipped.color(), clipped.alpha());
            vertex(out, matrix, clipped.c(), camera, 1, 1, clipped.color(), clipped.alpha());
            vertex(out, matrix, clipped.d(), camera, 1, 0, clipped.color(), clipped.alpha());
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vector p, Vec3 camera, float u, float v, int rgb, int alpha) {
        out.addVertex(matrix, (float)(p.x() - camera.x), (float)(p.y() - camera.y), (float)(p.z() - camera.z))
                .setUv(u, v).setColor(rgb >>> 16 & 255, rgb >>> 8 & 255, rgb & 255, alpha);
    }
    private static Vector point(Vec3 p) { return new Vector(p.x, p.y, p.z); }

    /** Read-only snapshot: points = centre, axis, point, then recorded hit points; numbers = live mask, plate. */
    private static double[] dp(net.minecraft.world.phys.Vec3 p) { return p == null ? DecorSnap.NONE : new double[] {p.x, p.y, p.z}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        for (Marker m : MARKERS.values()) { if (!validOwner(m, mc) || m.state == null) continue; var s = m.state;
            var points = new java.util.ArrayList<double[]>(java.util.List.of(dp(s.center()), dp(s.axis()), dp(s.point())));
            for (var h : m.hitPoints) points.add(dp(h));
            out.add(DecorSnap.of(m.owner == mc.player, 0, s.phase(), s.token(), points, s.liveMask(), s.plate())); }
        return out;
    }
}

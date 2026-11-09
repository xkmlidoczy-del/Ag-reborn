package cn.academy.port.client;

import cn.academy.port.highpower.ConductiveCircuit;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** NEW-121: server-owned ground anchors, dashed resting circuit, blue-white energized circuit. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ConductiveCircuitEffects {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("academy", "textures/port/white.png");
    private static final int MAX_CONTEXTS = 128, MAX_WATERMARKS = 1024, MAX_TICKS = 200;
    private static final Map<UUID, Marker> MARKERS = new HashMap<>();
    // Never evict a fence during a live session: refusing a new owner is safer than replaying an old token.
    private static final Map<UUID, Watermark> WATERMARKS = new HashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static long clearGeneration, localAbortGeneration;
    private static MultiBufferSource.BufferSource buffers;

    private static final class Marker {
        final Entity owner;
        ConductiveCircuit.Visual state;
        int age, quietTicks;
        Marker(Entity owner, ConductiveCircuit.Visual state) { this.owner = owner; this.state = state; }
    }
    private record Watermark(long token, long epoch, long sequence, boolean retired) {}
    private ConductiveCircuitEffects() {}

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { init(); }
    }
    public static void init() { ConductiveCircuit.clientReceiver = ConductiveCircuitEffects::receive; }

    public static void receive(ConductiveCircuit.Visual packet) {
        if (packet == null || packet.entity() < 0 || packet.owner() == null || packet.token() <= 0
                || packet.epoch() <= 0 || packet.sequence() <= 0 || packet.phase() < 0 || packet.phase() > 2
                || packet.remaining() < 0 || packet.remaining() > MAX_TICKS
                || !finite(packet.start()) || !finite(packet.end())
                || !ConductiveCircuitGeometry.validEndpoints(point(packet.start()), point(packet.end()))) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedClear = clearGeneration, capturedAbort = localAbortGeneration;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection) return;
            synchronize(mc);
            if (capturedClear != clearGeneration) return;
            Entity owner = capturedWorld.getEntity(packet.entity());
            if (owner == null || !owner.getUUID().equals(packet.owner())) return;
            if (owner == mc.player && (capturedAbort != localAbortGeneration || !AcademyClient.singleOwnerEpochMatches(packet.epoch()))) return;
            Watermark fence = WATERMARKS.get(packet.owner());
            if (fence != null && (packet.token() < fence.token()
                    || packet.token() == fence.token() && (fence.retired() || packet.epoch() != fence.epoch() || packet.sequence() <= fence.sequence()))) return;
            if (fence == null && WATERMARKS.size() >= MAX_WATERMARKS) return;
            if (packet.phase() == 2 || packet.remaining() == 0) {
                WATERMARKS.put(packet.owner(), new Watermark(packet.token(), packet.epoch(), packet.sequence(), true));
                MARKERS.remove(packet.owner()); return;
            }
            if (!owner.isAlive() || owner.isRemoved() || owner == mc.player && !localEligible()) return;
            Marker old = MARKERS.get(packet.owner());
            if (old == null && MARKERS.size() >= MAX_CONTEXTS) return;
            WATERMARKS.put(packet.owner(), new Watermark(packet.token(), packet.epoch(), packet.sequence(), false));
            if (old == null || old.state.token() != packet.token()) MARKERS.put(packet.owner(), new Marker(owner, packet));
            else { old.state = packet; old.quietTicks = 0; }
        });
    }

    /** Immediate local cancellation; same-token late packets stay retired until session replacement. */
    public static void abortLocal() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); localAbortGeneration++;
        if (mc.player == null) return;
        Marker marker = MARKERS.remove(mc.player.getUUID());
        if (marker != null) retire(marker);
    }

    /** Clear visible contexts while retaining anti-replay fences in the current connection. */
    public static void clear() {
        for (Marker marker : MARKERS.values()) retire(marker);
        MARKERS.clear(); clearGeneration++;
    }
    private static void retire(Marker marker) {
        var state = marker.state;
        WATERMARKS.put(marker.owner.getUUID(), new Watermark(state.token(), state.epoch(), state.sequence(), true));
    }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        MARKERS.clear(); WATERMARKS.clear();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean localEligible() {
        Minecraft mc = Minecraft.getInstance(); var state = AcademyClient.state;
        return mc.screen == null && mc.isWindowActive() && state != null && state.category.equals("electromaster")
                && state.activated && state.learned(ConductiveCircuit.ID) && state.level >= 2 && state.level <= 5
                && state.presets.currentContains(ConductiveCircuit.ID);
    }
    private static boolean invalid(Marker marker, Minecraft mc) {
        return world == null || !marker.owner.isAlive() || marker.owner.isRemoved()
                || world.getEntity(marker.owner.getId()) != marker.owner
                || marker.owner == mc.player && (!localEligible() || !AcademyClient.singleOwnerEpochMatches(marker.state.epoch()));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        boolean paused = mc.isPaused();
        Iterator<Marker> iterator = MARKERS.values().iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next();
            // Context invalidation is immediate even when game time is paused; TTL only uses active ticks.
            if (!paused) { marker.age++; marker.quietTicks++; }
            // An energized lease is shorter than the circuit's lifetime. Hide its expired pulse,
            // but let a newer authoritative resting packet restore this same surviving circuit.
            if (invalid(marker, mc) || marker.age >= MAX_TICKS
                    || marker.state.phase() == 0 && marker.quietTicks >= marker.state.remaining()) {
                retire(marker); iterator.remove();
            }
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (world == null || MARKERS.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        var type = ClassicRenderTypes.world(WHITE); VertexConsumer out = buffers.getBuffer(type);
        Matrix4f matrix = event.getPoseStack().last().pose();
        Vec3 camera = event.getCamera().getPosition(); var look = event.getCamera().getLookVector();
        var forward = new ConductiveCircuitGeometry.Point(look.x(), look.y(), look.z());
        var eye = point(camera);
        for (Marker marker : MARKERS.values()) {
            if (invalid(marker, mc) || marker.quietTicks >= marker.state.remaining()) continue;
            var state = marker.state; boolean energized = state.phase() == 1;
            for (var segment : ConductiveCircuitGeometry.segments(point(state.start()), point(state.end()), energized, marker.age / 2, state.token())) {
                var start = segment.start().minus(eye); var end = segment.end().minus(eye);
                line(out, matrix, start, end, forward, energized ? .018 : .011, 98, 187, 255, energized ? 170 : 110);
                if (energized) line(out, matrix, start, end, forward, .006, 230, 249, 255, 245);
            }
        }
        buffers.endBatch(type);
    }
    private static void line(VertexConsumer out, Matrix4f matrix, ConductiveCircuitGeometry.Point start,
                             ConductiveCircuitGeometry.Point end, ConductiveCircuitGeometry.Point forward,
                             double width, int red, int green, int blue, int alpha) {
        var ribbon = ConductiveCircuitGeometry.clip(start, end, forward, width);
        if (ribbon == null) return;
        vertex(out, matrix, ribbon.start().plus(ribbon.side()), 0, 0, red, green, blue, alpha);
        vertex(out, matrix, ribbon.start().minus(ribbon.side()), 0, 1, red, green, blue, alpha);
        vertex(out, matrix, ribbon.end().minus(ribbon.side()), 1, 1, red, green, blue, alpha);
        vertex(out, matrix, ribbon.end().plus(ribbon.side()), 1, 0, red, green, blue, alpha);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, ConductiveCircuitGeometry.Point point,
                               float u, float v, int red, int green, int blue, int alpha) {
        out.addVertex(matrix, (float) point.x(), (float) point.y(), (float) point.z()).setUv(u, v).setColor(red, green, blue, alpha);
    }
    private static ConductiveCircuitGeometry.Point point(Vec3 point) { return new ConductiveCircuitGeometry.Point(point.x, point.y, point.z); }
    private static boolean finite(Vec3 point) { return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z); }

    /** Read-only snapshot: points = start, end; numbers = phase, remaining ticks. */
    private static double[] dp(net.minecraft.world.phys.Vec3 p) { return p == null ? DecorSnap.NONE : new double[] {p.x, p.y, p.z}; }
    static java.util.List<DecorSnap> decorSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var out = new java.util.ArrayList<DecorSnap>();
        if (world == null) return out;
        for (Marker m : MARKERS.values()) {
            if (invalid(m, mc) || m.quietTicks >= m.state.remaining()) continue;
            var s = m.state;
            out.add(DecorSnap.of(m.owner == mc.player, m.age, s.phase(), s.token(), java.util.List.of(dp(s.start()), dp(s.end())), s.phase(), s.remaining()));
        }
        return out;
    }
}

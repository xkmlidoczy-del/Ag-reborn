package cn.academy.port.client;

import cn.academy.port.highpower.ElectromasterChargeDecoy;
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
import java.util.*;

/** NEW-123 reuses the classic blue-white ribbon texture. No particles or cosmetic controller entities. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ElectromasterChargeDecoyEffects {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/arc/line_segment.png");
    private static final int MAX_CONTEXTS = 128;
    private static final Map<UUID, Marker> MARKERS = new HashMap<>();
    private static final Map<UUID, Long> TERMINALS = new LinkedHashMap<>();
    private static final List<List<List<ClassicArcGeometry.Segment>>> PATTERNS = patterns();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static long retiredLocalInput;
    private static MultiBufferSource.BufferSource buffers;
    private ElectromasterChargeDecoyEffects() {}
    private static final class Marker {
        final Entity owner;
        final long token, input;
        ElectromasterChargeDecoy.Visual state;
        int quietTicks, age;
        Marker(Entity owner, ElectromasterChargeDecoy.Visual state) { this.owner = owner; this.token = state.token(); this.input = state.input(); this.state = state; }
    }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent e) { ElectromasterChargeDecoy.clientReceiver = ElectromasterChargeDecoyEffects::receive; }
    }
    public static void receive(ElectromasterChargeDecoy.Visual packet) {
        if (packet == null || packet.entity() < 0 || packet.token() <= 0 || packet.input() < 0 || packet.epoch() < 0
                || packet.input() > 0 && packet.epoch() <= 0 || packet.sequence() <= 0 || packet.phase() < 0 || packet.phase() > 3
                || packet.remaining() < 0 || packet.remaining() > 20 || !finite(packet.origin()) || !finite(packet.point())
                || packet.origin().distanceToSqr(packet.point()) > 21 * 21) return;
        Minecraft mc = Minecraft.getInstance(); ClientLevel captured = mc.level; Object capturedConnection = mc.getConnection();
        mc.execute(() -> {
            if (captured == null || mc.level != captured || mc.getConnection() != capturedConnection) return;
            synchronize(mc);
            Entity owner = captured.getEntity(packet.entity());
            if (owner == null || !owner.getUUID().equals(packet.owner())) return;
            if (owner == mc.player && packet.input() > 0 && (packet.input() <= retiredLocalInput || !AcademyClient.singleOwnerEpochMatches(packet.epoch()))) return;
            Marker old = MARKERS.get(packet.owner());
            if (packet.token() <= TERMINALS.getOrDefault(packet.owner(), 0L)
                    || old != null && (packet.token() < old.token || packet.token() == old.token && packet.sequence() <= old.state.sequence())) return;
            if (packet.phase() == 2) { terminal(packet.owner(), packet.token()); if (old != null && old.token <= packet.token()) MARKERS.remove(packet.owner()); return; }
            if (!owner.isAlive() || owner.isRemoved()) return;
            if (old == null || old.token != packet.token()) {
                if (old == null && MARKERS.size() >= MAX_CONTEXTS) return;
                MARKERS.put(packet.owner(), new Marker(owner, packet));
            } else { old.state = packet; old.quietTicks = 0; }
        });
    }
    public static void abortLocal(long input) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); if (mc.player == null) return;
        if (input > 0) retiredLocalInput = Math.max(retiredLocalInput, input);
        Marker m = MARKERS.get(mc.player.getUUID());
        if (m != null && m.owner == mc.player && m.input == input) { MARKERS.remove(mc.player.getUUID()); terminal(mc.player.getUUID(), m.token); }
    }
    public static void clear() { MARKERS.clear(); TERMINALS.clear(); retiredLocalInput = 0; }
    private static void terminal(UUID owner, long token) {
        TERMINALS.merge(owner, token, Math::max);
        if (TERMINALS.size() > 256) TERMINALS.remove(TERMINALS.keySet().iterator().next());
    }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear(); world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        // Client tick events still fire while the integrated server is paused.
        // Do not expire its live token before authoritative time resumes.
        if (mc.isPaused()) return;
        Iterator<Marker> iterator = MARKERS.values().iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next(); marker.age++; marker.quietTicks++;
            if (world == null || !marker.owner.isAlive() || marker.owner.isRemoved()
                    || world.getEntity(marker.owner.getId()) != marker.owner
                    || marker.quietTicks >= (marker.state.phase() == 1 ? marker.state.remaining() : 8)) {
                terminal(marker.owner.getUUID(), marker.token); iterator.remove();
            }
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); if (world == null || MARKERS.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(32768));
        var type = ClassicRenderTypes.world(TEXTURE); VertexConsumer out = buffers.getBuffer(type);
        Matrix4f matrix = event.getPoseStack().last().pose(); Vec3 camera = event.getCamera().getPosition();
        for (Marker marker : MARKERS.values()) {
            var state = marker.state; if (state.phase() == 3) continue;
            Vec3 point = state.point().subtract(camera);
            int alpha = state.phase() == 0 ? 100 : 220;
            // A small ground point and short approach arrow communicate placement before commitment.
            markerLine(out, matrix, point.add(-.17, 0, 0), point.add(.17, 0, 0), .017, alpha, state.phase() == 0);
            markerLine(out, matrix, point.add(0, 0, -.17), point.add(0, 0, .17), .017, alpha, state.phase() == 0);
            Vec3 flat = state.point().subtract(state.origin()).multiply(1, 0, 1).normalize();
            if (flat.lengthSqr() > .5) {
                Vec3 rear = point.subtract(flat.scale(.7)), side = flat.cross(new Vec3(0, 1, 0)).scale(.12);
                markerLine(out, matrix, rear, point.subtract(flat.scale(.23)), .013, alpha, state.phase() == 0);
                markerLine(out, matrix, point.subtract(flat.scale(.23)), point.subtract(flat.scale(.4)).add(side), .013, alpha, state.phase() == 0);
                markerLine(out, matrix, point.subtract(flat.scale(.23)), point.subtract(flat.scale(.4)).subtract(side), .013, alpha, state.phase() == 0);
            }
            if (state.phase() == 1) {
                // The actual arrows supply their own curved motion. No invented damage/tracer paths.
                for (int i = 0; i < 3; i++) {
                    double angle = i * Math.PI * 2 / 3 + (marker.age / 3 % 4) * .12;
                    Vec3 end = point.add(Math.cos(angle) * .5, 0, Math.sin(angle) * .5);
                    arc(out, matrix, point, end, PATTERNS.get((marker.age / 2 + i) % PATTERNS.size()), alpha);
                }
                // Only the first four authoritative pulse ticks light the short placement path.
                if (state.remaining() - marker.quietTicks > 16)
                    line(out, matrix, state.origin().subtract(camera), point, .012, 150);
            }
        }
        buffers.endBatch(type);
    }
    private static void arc(VertexConsumer out, Matrix4f matrix, Vec3 start, Vec3 end, List<List<ClassicArcGeometry.Segment>> pattern, int alpha) {
        Vec3 direction = end.subtract(start).normalize(), across = direction.cross(new Vec3(0, 1, 0));
        for (var path : pattern) for (var segment : path) {
            Vec3 a = segment.start().position(), b = segment.end().position();
            Vec3 from = start.add(direction.scale(a.x)).add(across.scale(a.z));
            Vec3 to = start.add(direction.scale(b.x)).add(across.scale(b.z));
            line(out, matrix, from, to, .012, (int) (alpha * segment.alpha()));
        }
    }
    private static void markerLine(VertexConsumer out, Matrix4f matrix, Vec3 start, Vec3 end, double width, int alpha, boolean preview) {
        if (!preview) { line(out, matrix, start, end, width, alpha); return; }
        // Harmless preview is always dashed, never merely a dim version of a confirmed line.
        Vec3 delta = end.subtract(start); double length = delta.length(); if (length < 1e-8) return;
        Vec3 direction = delta.scale(1 / length);
        for (double at = 0; at < length; at += .11)
            line(out, matrix, start.add(direction.scale(at)), start.add(direction.scale(Math.min(length, at + .055))), width, alpha);
    }
    private static void line(VertexConsumer out, Matrix4f matrix, Vec3 start, Vec3 end, double width, int alpha) {
        Vec3 direction = end.subtract(start); if (direction.lengthSqr() < 1e-10) return;
        Vec3 normal = direction.cross(start.add(end).scale(.5)).normalize();
        if (normal.lengthSqr() < 1e-10) normal = direction.cross(new Vec3(0, 1, 0)).normalize();
        if (normal.lengthSqr() < 1e-10) normal = new Vec3(1, 0, 0);
        Vec3 side = normal.scale(width);
        vertex(out, matrix, start.add(side), 0, 0, alpha); vertex(out, matrix, start.subtract(side), 0, 1, alpha);
        vertex(out, matrix, end.subtract(side), 1, 1, alpha); vertex(out, matrix, end.add(side), 1, 0, alpha);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 p, float u, float v, int alpha) {
        out.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(255, 255, 255, alpha);
    }
    private static List<List<List<ClassicArcGeometry.Segment>>> patterns() {
        var result = new ArrayList<List<List<ClassicArcGeometry.Segment>>>(); Random shape = new Random(123);
        for (int i = 0; i < 8; i++) result.add(ClassicArcGeometry.generate(shape, new Random(i), .5, 3, .012, .12, .12, .7));
        return List.copyOf(result);
    }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }

    /** Read-only snapshot: points = origin, decoy point; numbers = phase, remaining ticks. */
    private static double[] dp(net.minecraft.world.phys.Vec3 p) { return p == null ? DecorSnap.NONE : new double[] {p.x, p.y, p.z}; }
    static java.util.List<DecorSnap> decorSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var out = new java.util.ArrayList<DecorSnap>();
        if (world == null) return out;
        for (Marker m : MARKERS.values()) {
            var s = m.state;
            if (m.owner.isRemoved() || s.phase() == 3) continue;
            out.add(DecorSnap.of(m.owner == mc.player, m.age, s.phase(), s.token(), java.util.List.of(dp(s.origin()), dp(s.point())), s.phase(), s.remaining()));
        }
        return out;
    }
}

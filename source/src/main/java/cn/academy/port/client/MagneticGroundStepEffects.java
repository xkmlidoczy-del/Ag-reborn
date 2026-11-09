package cn.academy.port.client;

import cn.academy.port.highpower.MagneticGroundStep;
import cn.academy.port.highpower.core.MagneticGroundStepRules;
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

/** NEW-132: two captured ground rails; one short side arc only after a real server deflection. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MagneticGroundStepEffects {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("academy", "textures/port/white.png");
    private static final int MAX_CONTEXTS = 128, MAX_WATERMARKS = 1024, QUIET_TICKS = 12;
    private static final int MAX_TICKS = MagneticGroundStepRules.MAX_TICKS, ARC_TICKS = 4;
    private static final Map<UUID, Marker> MARKERS = new HashMap<>();
    // Fail closed at capacity. Never evict another owner's retirement fence in a live connection.
    private static final Map<UUID, Watermark> WATERMARKS = new HashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    // Independent of remote-owner capacity, including cancellation before the first packet.
    private static Stamp retiredLocal;
    private static volatile long generation;
    private static MultiBufferSource.BufferSource buffers;

    private record Stamp(int entity, long input, long token, long epoch) {}
    private record Watermark(Stamp stamp, long sequence, boolean retired) {}
    private static final class Marker {
        final Entity owner;
        final Stamp stamp;
        MagneticGroundStep.Visual state;
        int age, quiet, arc;
        boolean deflected;
        int received = 1;
        long railQuads;
        boolean firstRailDrawn, retirementLogged;
        Vec3 arcStart, arcEnd;
        Marker(Entity owner, MagneticGroundStep.Visual state) {
            this.owner = owner;
            this.stamp = stamp(state);
            this.state = state;
            age = MAX_TICKS - state.remaining();
            confirmDeflection(state);
        }
        void confirmDeflection(MagneticGroundStep.Visual packet) {
            if (packet.phase() != 1 || deflected) return;
            deflected = true;
            arc = ARC_TICKS;
            // Keepalives may move the ground segment, but can never move or restart this event.
            arcStart = packet.end();
            arcEnd = packet.deflection();
        }
    }
    private MagneticGroundStepEffects() {}

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            MagneticGroundStep.clientReceiver = MagneticGroundStepEffects::receive;
        }
    }

    public static void receive(MagneticGroundStep.Visual packet) {
        if (packet == null || packet.entity() < 0 || packet.owner() == null || packet.input() <= 0
                || packet.token() <= 0 || packet.epoch() <= 0 || packet.sequence() <= 0
                || packet.phase() < 0 || packet.phase() > 2 || packet.remaining() < 0 || packet.remaining() > MAX_TICKS
                || !validGeometry(packet)) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel captured = mc.level;
        Entity capturedPlayer = mc.player;
        Object capturedConnection = mc.getConnection();
        Entity capturedOwner = captured == null ? null : captured.getEntity(packet.entity());
        long capturedGeneration = generation;
        mc.execute(() -> {
            if (captured == null || capturedConnection == null || mc.level != captured || mc.player != capturedPlayer
                    || mc.getConnection() != capturedConnection || generation != capturedGeneration) return;
            synchronize(mc);
            Entity owner = captured.getEntity(packet.entity());
            if (owner == null || owner != capturedOwner || !owner.getUUID().equals(packet.owner())) return;
            Stamp incoming = stamp(packet);
            if (owner == mc.player && (!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || retiredLocal != null && compare(incoming, retiredLocal) <= 0)) return;
            Watermark fence = WATERMARKS.get(packet.owner());
            Marker old = MARKERS.get(packet.owner());
            if (fence != null) {
                int order = compare(incoming, fence.stamp());
                if (order < 0 || order > 0 && packet.token() <= fence.stamp().token()
                        || order == 0 && (fence.retired() || !incoming.equals(fence.stamp())
                        || packet.sequence() <= fence.sequence())) return;
            }
            if (fence == null && WATERMARKS.size() >= MAX_WATERMARKS) return;
            if (old != null && incoming.equals(old.stamp) && owner != old.owner) return;
            if (packet.phase() == 2 || packet.remaining() == 0) {
                terminal(packet.owner(), incoming, packet.sequence());
                if (old != null) retirementDiagnostic(old, packet.phase() == 2 ? "server_terminal" : "zero_remaining");
                MARKERS.remove(packet.owner());
                return;
            }
            if (!owner.isAlive() || owner.isRemoved() || owner == mc.player && !localEligible()) return;
            if (old == null && MARKERS.size() >= MAX_CONTEXTS) return;
            WATERMARKS.put(packet.owner(), new Watermark(incoming, packet.sequence(), false));
            if (old == null || !incoming.equals(old.stamp)) {
                if (old != null) retirementDiagnostic(old, "superseded");
                Marker marker = new Marker(owner, packet);
                MARKERS.put(packet.owner(), marker);
                visualDiagnostic(marker, "client_visual", "accepted");
            } else {
                old.received++;
                old.state = packet;
                old.quiet = 0;
                old.age = Math.max(old.age, MAX_TICKS - packet.remaining());
                old.confirmDeflection(packet);
            }
        });
    }

    /** Cancel only this client's authenticated local activation. */
    public static void abortLocal() {
        Minecraft mc = Minecraft.getInstance();
        synchronize(mc);
        if (mc.player == null) return;
        Marker marker = MARKERS.get(mc.player.getUUID());
        if (marker != null && marker.owner == mc.player) abortLocal(marker.stamp.input(), marker.stamp.epoch());
    }

    /** Retire a captured input even if its first server packet has not arrived. */
    public static void abortLocal(long input, long epoch) {
        Minecraft mc = Minecraft.getInstance();
        synchronize(mc);
        if (mc.player == null || input <= 0 || !AcademyClient.singleOwnerEpochMatches(epoch)) return;
        Watermark fence = WATERMARKS.get(mc.player.getUUID());
        long knownToken = fence == null ? 0 : fence.stamp().token();
        Stamp canceled = new Stamp(mc.player.getId(), input, knownToken, epoch);
        if (retiredLocal == null || compare(canceled, retiredLocal) > 0) retiredLocal = canceled;
        terminal(mc.player.getUUID(), canceled, Long.MAX_VALUE);
        Marker marker = MARKERS.get(mc.player.getUUID());
        if (marker != null && marker.owner == mc.player && compare(marker.stamp, canceled) <= 0) {
            retirementDiagnostic(marker, "local_abort");
            MARKERS.remove(mc.player.getUUID());
        }
    }

    /** Clear visible effects and invalidate queued packets without reopening retired inputs. */
    public static void clear() {
        for (Marker marker : MARKERS.values()) {
            retirementDiagnostic(marker, "explicit_clear");
            terminal(marker.owner.getUUID(), marker.stamp, marker.state.sequence());
        }
        MARKERS.clear();
        generation++;
    }
    private static Stamp stamp(MagneticGroundStep.Visual packet) {
        return new Stamp(packet.entity(), packet.input(), packet.token(), packet.epoch());
    }
    private static int compare(Stamp left, Stamp right) {
        int epochOrder = Long.compare(left.epoch(), right.epoch());
        return epochOrder != 0 ? epochOrder : Long.compare(left.input(), right.input());
    }
    private static void terminal(UUID owner, Stamp stamp, long sequence) {
        if (localPlayer != null && owner.equals(localPlayer.getUUID())
                && (retiredLocal == null || compare(stamp, retiredLocal) > 0)) retiredLocal = stamp;
        Watermark old = WATERMARKS.get(owner);
        if (old == null && WATERMARKS.size() >= MAX_WATERMARKS) return;
        int order = old == null ? 1 : compare(stamp, old.stamp());
        if (order > 0 || order == 0 && sequence >= old.sequence())
            WATERMARKS.put(owner, new Watermark(stamp, sequence, true));
    }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        String reason = world != mc.level ? "world_changed" : localPlayer != mc.player ? "player_changed" : "connection_changed";
        for (Marker marker : MARKERS.values()) retirementDiagnostic(marker, reason);
        MARKERS.clear();
        WATERMARKS.clear();
        retiredLocal = null;
        generation++;
        world = mc.level;
        localPlayer = mc.player;
        connection = mc.getConnection();
    }
    private static boolean localEligible() {
        Minecraft mc = Minecraft.getInstance();
        var state = AcademyClient.state;
        return mc.screen == null && mc.isWindowActive() && state != null
                && state.category.equals("electromaster") && state.activated && state.level >= 3 && state.level <= 5
                && state.learned(MagneticGroundStep.ID) && state.presets.currentContains(MagneticGroundStep.ID);
    }
    private static boolean invalid(Marker marker, Minecraft mc) {
        return world == null || !marker.owner.isAlive() || marker.owner.isRemoved()
                || world.getEntity(marker.owner.getId()) != marker.owner
                || marker.owner == mc.player && (!localEligible()
                || !AcademyClient.singleOwnerEpochMatches(marker.stamp.epoch()));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        synchronize(mc);
        Iterator<Marker> iterator = MARKERS.values().iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next();
            // Screen, death and owner identity invalidation remain immediate during integrated-server pause.
            if (invalid(marker, mc)) {
                retirementDiagnostic(marker, invalidReason(marker, mc));
                terminal(marker.owner.getUUID(), marker.stamp, marker.state.sequence());
                iterator.remove();
                continue;
            }
            if (mc.isPaused()) continue;
            marker.age++;
            marker.quiet++;
            if (marker.arc > 0) marker.arc--;
            if (marker.age >= MAX_TICKS || marker.quiet >= QUIET_TICKS || marker.quiet >= marker.state.remaining()) {
                retirementDiagnostic(marker, marker.age >= MAX_TICKS ? "max_age"
                        : marker.quiet >= QUIET_TICKS ? "quiet_timeout" : "remaining_expired");
                terminal(marker.owner.getUUID(), marker.stamp, marker.state.sequence());
                iterator.remove();
            }
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        synchronize(mc);
        if (world == null || MARKERS.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(32768));
        var type = ClassicRenderTypes.world(WHITE);
        VertexConsumer out = buffers.getBuffer(type);
        Matrix4f matrix = event.getPoseStack().last().pose();
        Vec3 camera = event.getCamera().getPosition();
        var look = event.getCamera().getLookVector();
        var forward = new PolarityTetherGeometry.Point(look.x(), look.y(), look.z());
        float partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (Marker marker : MARKERS.values()) {
            if (invalid(marker, mc)) continue;
            Vec3 start = marker.state.start().subtract(camera), end = marker.state.end().subtract(camera);
            if (!finite(start) || !finite(end) || start.lengthSqr() > 24 * 24 || end.lengthSqr() > 24 * 24) continue;
            Vec3 across = railOffset(marker.state.start(), marker.state.end());
            // Exactly two narrow blue-white rails; never predict travel or follow interpolated owner motion.
            for (int rail = -1; rail <= 1; rail += 2) {
                Vec3 offset = across.scale(rail);
                railDrawn(marker, line(out, matrix, start.add(offset), end.add(offset), forward, .012, false, 170));
                railDrawn(marker, line(out, matrix, start.add(offset), end.add(offset), forward, .004, true, 225));
            }
            if (marker.arc > 0) {
                float fade = Math.max(0, (marker.arc - partial) / ARC_TICKS);
                Vec3 previous = arcPoint(marker.arcStart, marker.arcEnd, 0).subtract(camera);
                for (int i = 1; i <= 3; i++) {
                    Vec3 next = arcPoint(marker.arcStart, marker.arcEnd, i).subtract(camera);
                    line(out, matrix, previous, next, forward, .012, false, (int)(190 * fade));
                    line(out, matrix, previous, next, forward, .004, true, (int)(245 * fade));
                    previous = next;
                }
            }
        }
        buffers.endBatch(type);
    }
    /** The backend owns the opt-in property and shared per-token event budget; details contain no identity or coordinates. */
    private static void visualDiagnostic(Marker marker, String stage, String reason) {
        MagneticGroundStep.diagnostic(marker.stamp.token(), stage, "reason=" + reason
                + " age=" + marker.age + " quiet=" + marker.quiet + " received=" + marker.received
                + " last_phase=" + marker.state.phase() + " rail_quads=" + marker.railQuads
                + " segment_length=" + Math.sqrt(marker.state.end().subtract(marker.state.start()).lengthSqr()));
    }
    private static void retirementDiagnostic(Marker marker, String reason) {
        if (marker.retirementLogged) return;
        marker.retirementLogged = true;
        visualDiagnostic(marker, "client_visual_retired", reason);
    }
    private static String invalidReason(Marker marker, Minecraft mc) {
        if (world == null) return "no_world";
        if (!marker.owner.isAlive()) return "owner_dead";
        if (marker.owner.isRemoved()) return "owner_removed";
        if (world.getEntity(marker.owner.getId()) != marker.owner) return "owner_replaced";
        if (marker.owner == mc.player && !AcademyClient.singleOwnerEpochMatches(marker.stamp.epoch())) return "local_epoch";
        return "local_ineligible";
    }
    private static void railDrawn(Marker marker, boolean emitted) {
        if (!emitted) return;
        marker.railQuads++;
        if (marker.firstRailDrawn) return;
        marker.firstRailDrawn = true;
        visualDiagnostic(marker, "client_rail_draw", "quad_emitted");
    }
    private static Vec3 railOffset(Vec3 start, Vec3 end) {
        Vec3 direction = end.subtract(start);
        double length = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        return length < 1e-8 ? Vec3.ZERO : new Vec3(-direction.z / length * .18, 0, direction.x / length * .18);
    }
    private static Vec3 arcPoint(Vec3 start, Vec3 end, int index) {
        Vec3 delta = end.subtract(start);
        Vec3 across = new Vec3(-delta.z, 0, delta.x).normalize();
        double kink = index == 1 ? .06 : index == 2 ? -.06 : 0;
        return start.add(delta.scale(index / 3.0)).add(across.scale(kink));
    }
    private static boolean validGeometry(MagneticGroundStep.Visual packet) {
        if (!finite(packet.start()) || !finite(packet.end()) || !finite(packet.deflection())) return false;
        double segment = packet.end().subtract(packet.start()).lengthSqr();
        if (!Double.isFinite(segment) || segment > .81 * .81 + 1e-8
                || Math.abs(packet.end().y - packet.start().y) > .025 + 1e-8) return false;
        double deflection = packet.deflection().subtract(packet.end()).lengthSqr();
        return packet.phase() == 1 ? Double.isFinite(deflection) && deflection <= 9 + 1e-8
                : packet.deflection().lengthSqr() == 0;
    }
    private static PolarityTetherGeometry.Point point(Vec3 value) {
        return new PolarityTetherGeometry.Point(value.x, value.y, value.z);
    }
    private static Vec3 vector(PolarityTetherGeometry.Point value) {
        return new Vec3(value.x(), value.y(), value.z());
    }
    private static boolean line(VertexConsumer out, Matrix4f matrix, Vec3 start, Vec3 end,
                             PolarityTetherGeometry.Point forward, double width, boolean white, int alpha) {
        var clipped = PolarityTetherGeometry.clip(point(start), point(end), forward, width);
        if (clipped == null) return false;
        start = vector(clipped.start());
        end = vector(clipped.end());
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1e-10) return false;
        Vec3 normal = direction.cross(start.scale(-1)).normalize();
        if (normal.lengthSqr() < 1e-10) normal = direction.cross(new Vec3(0, 1, 0)).normalize();
        if (normal.lengthSqr() < 1e-10) normal = new Vec3(1, 0, 0);
        Vec3 side = normal.scale(clipped.halfWidth());
        vertex(out, matrix, start.add(side), 0, 0, white, alpha);
        vertex(out, matrix, start.subtract(side), 0, 1, white, alpha);
        vertex(out, matrix, end.subtract(side), 1, 1, white, alpha);
        vertex(out, matrix, end.add(side), 1, 0, white, alpha);
        return true;
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 position, float u, float v,
                               boolean white, int alpha) {
        out.addVertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .setUv(u, v).setColor(white ? 225 : 105, white ? 247 : 190, 255, alpha);
    }
    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    /** Read-only snapshot: points = start, end, deflection; numbers = phase, remaining ticks, deflected flag. */
    private static double[] dp(net.minecraft.world.phys.Vec3 p) { return p == null ? DecorSnap.NONE : new double[] {p.x, p.y, p.z}; }
    static java.util.List<DecorSnap> decorSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var out = new java.util.ArrayList<DecorSnap>();
        if (world == null) return out;
        for (Marker m : MARKERS.values()) {
            if (invalid(m, mc)) continue;
            var s = m.state;
            out.add(DecorSnap.of(m.owner == mc.player, m.age, s.phase(), s.token(), java.util.List.of(dp(s.start()), dp(s.end()), dp(s.deflection())), s.phase(), s.remaining(), m.deflected ? 1 : 0));
        }
        return out;
    }
}

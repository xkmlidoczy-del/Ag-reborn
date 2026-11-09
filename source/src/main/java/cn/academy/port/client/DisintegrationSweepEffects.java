package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.DisintegrationSweep;
import cn.academy.port.highpower.FoldedMirrorArray;
import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW231 hollow planar warnings and short-lived native sweep receipts. No geometry grants damage. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class DisintegrationSweepEffects {
    private static final ResourceLocation WHITE = texture("port/white");
    private static final ResourceLocation[] SMALL_RAY = {texture("effects/mdray_small/blend_in"), texture("effects/mdray_small/tile"), texture("effects/mdray_small/blend_out")};
    private static final DisintegrationSweepState STATE = new DisintegrationSweepState();
    private static final Map<UUID, Marker> MARKERS = new LinkedHashMap<>();
    private static final Map<UUID, Tail> TAILS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static MultiBufferSource.BufferSource buffers;
    private static String terminalStatus;
    private static int statusTicks;
    private static final class Flash {
        final DisintegrationSweepState.Frame frame;
        int age;
        Flash(DisintegrationSweepState.Frame frame) { this.frame = frame; }
    }
    private static final class Marker {
        final Entity owner;
        final DisintegrationSweepState.Lease lease = new DisintegrationSweepState.Lease();
        final List<Flash> flashes = new ArrayList<>(DisintegrationSweepState.FLASH_TICKS);
        DisintegrationSweepState.Frame frame;
        Marker(Entity owner, DisintegrationSweepState.Frame frame) { this.owner = owner; update(frame); }
        void update(DisintegrationSweepState.Frame next) {
            frame = next; lease.received(next);
            if (next.phase() == DisintegrationSweepState.SWEEPING && !next.path().isEmpty()) {
                if (flashes.size() == DisintegrationSweepState.FLASH_TICKS) flashes.removeFirst();
                flashes.add(new Flash(next));
            }
        }
    }
    private record Tail(Entity owner, DisintegrationSweepState.Afterglow fade) {}
    private record RenderOwner(Entity owner, Marker marker, Tail tail) {}
    private DisintegrationSweepEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { DisintegrationSweep.clientReceiver = DisintegrationSweepEffects::receive; }
    }
    private static DisintegrationSweepState.Frame frame(DisintegrationSweep.Visual p) {
        return new DisintegrationSweepState.Frame(new DisintegrationSweepState.Identity(p.owner(), p.ownerUuid(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), p.status(), p.remainingTicks(), p.mining(), point(p.ownerFeet()), point(p.origin()), point(p.forward()), point(p.across()),
                p.fromAngle(), p.toAngle(), p.path().stream().map(s -> new DisintegrationSweepState.Segment(point(s.start()), point(s.end()), s.status())).toList(),
                p.marks().stream().map(m -> point(m.surface())).toList());
    }
    public static void receive(DisintegrationSweep.Visual packet) {
        if (packet == null || packet.ownerFeet() == null || packet.origin() == null || packet.forward() == null || packet.across() == null
                || packet.path() == null || packet.path().size() > (packet.mining() ? 2 : 8) || packet.marks() == null || packet.marks().size() > 16
                || packet.path().stream().anyMatch(s -> s == null || s.start() == null || s.end() == null)
                || packet.marks().stream().anyMatch(m -> m == null || m.surface() == null)) return;
        final DisintegrationSweepState.Frame receipt;
        try { receipt = frame(packet); } catch (IllegalArgumentException malformedGeometry) { return; }
        if (!DisintegrationSweepState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.owner());
            if (owner == null || !owner.getUUID().equals(packet.ownerUuid())) return;
            Marker old = MARKERS.get(packet.ownerUuid());
            if (old != null && old.frame.id().token() == receipt.id().token() && old.owner != owner) return;
            boolean local = owner == mc.player;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch()) || !AcademyClient.disintegrationSweepInputMatches(packet.input(), packet.epoch()))) return;
            if (packet.phase() != DisintegrationSweepState.ENDED && (!owner.isAlive() || owner.isRemoved() || local && !localEligible())) return;
            if (!STATE.accept(receipt, local)) return;
            if (local) AcademyClient.disintegrationSweepAcceptedInput(packet.input(), packet.epoch());
            if (packet.phase() == DisintegrationSweepState.ENDED) {
                MARKERS.remove(packet.ownerUuid());
                if (old != null && old.frame.phase() == DisintegrationSweepState.SWEEPING && !old.frame.path().isEmpty()
                        && old.frame.id().equals(receipt.id()) && TAILS.size() < DisintegrationSweepState.MAX_CONTEXTS)
                    TAILS.put(packet.ownerUuid(), new Tail(owner, new DisintegrationSweepState.Afterglow(old.frame, receipt)));
                if (local) {
                    terminalStatus = DisintegrationSweepState.status(receipt); statusTicks = 30;
                    AcademyClient.disintegrationSweepEndedInput(packet.input(), packet.epoch());
                }
                return;
            }
            TAILS.remove(packet.ownerUuid());
            if (local) { terminalStatus = null; statusTicks = 0; }
            if (old == null || old.frame.id().token() != receipt.id().token()) {
                if (old == null && MARKERS.size() >= DisintegrationSweepState.MAX_CONTEXTS) { STATE.retire(receipt.id()); return; }
                MARKERS.put(packet.ownerUuid(), new Marker(owner, receipt));
            } else old.update(receipt);
        });
    }
    public static void abortLocal(long input, long epoch) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); STATE.cancel(epoch, input);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        if (marker != null && marker.frame.id().epoch() == epoch && marker.frame.id().input() <= input) {
            STATE.retire(marker.frame.id()); MARKERS.remove(mc.player.getUUID());
        }
        Tail tail = mc.player == null ? null : TAILS.get(mc.player.getUUID());
        if (tail != null && tail.fade.frame().id().epoch() == epoch && tail.fade.frame().id().input() <= input) TAILS.remove(mc.player.getUUID());
        terminalStatus = null; statusTicks = 0;
    }
    public static void clear() { generation++; STATE.clear(); MARKERS.clear(); TAILS.clear(); terminalStatus = null; statusTicks = 0; }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear();
        // Same-connection player/world replacement retains replay tombstones.
        if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean localEligible() {
        Minecraft mc = Minecraft.getInstance(); var state = AcademyClient.state;
        return mc.screen == null && mc.isWindowActive() && mc.getCameraEntity() == mc.player
                && mc.player != null && !mc.player.isSpectator() && state != null && state.activated && state.overloadFine && !state.interfering
                && "meltdowner".equals(state.category) && DisintegrationSweepState.eligible(state.level, state.learned(FoldedMirrorArray.ID), state.learned(DisintegrationSweep.ID))
                && state.presets.currentContains(DisintegrationSweep.ID) && SkillCatalog.enabled("meltdowner", DisintegrationSweep.ID);
    }
    private static boolean validOwner(Marker marker, Minecraft mc) {
        return world != null && marker.owner.isAlive() && !marker.owner.isRemoved() && world.getEntity(marker.owner.getId()) == marker.owner
                && (marker.owner != mc.player || localEligible() && AcademyClient.singleOwnerEpochMatches(marker.frame.id().epoch())
                && AcademyClient.disintegrationSweepInputMatches(marker.frame.id().input(), marker.frame.id().epoch()));
    }
    private static boolean validTail(Tail tail, Minecraft mc) {
        var id = tail.fade.frame().id();
        return world != null && tail.owner.isAlive() && !tail.owner.isRemoved() && world.getEntity(id.entity()) == tail.owner
                && tail.owner.getUUID().equals(id.owner()) && (tail.owner != mc.player || localEligible() && AcademyClient.singleOwnerEpochMatches(id.epoch()));
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        if (marker == null || !validOwner(marker, mc)) return ClassicAbilityHud.DelegateVisual.IDLE;
        return marker.frame.phase() == DisintegrationSweepState.CHARGING ? ClassicAbilityHud.DelegateVisual.CHARGE : ClassicAbilityHud.DelegateVisual.ACTIVE;
    }
    public static boolean activeLocal() { return localVisual() != ClassicAbilityHud.DelegateVisual.IDLE; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (!mc.isPaused() && statusTicks > 0 && --statusTicks == 0) terminalStatus = null;
        TAILS.values().removeIf(tail -> !tail.fade.tick(mc.isPaused(), validTail(tail, mc)));
        Iterator<Marker> iterator = MARKERS.values().iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next();
            if (!mc.isPaused()) marker.flashes.removeIf(flash -> ++flash.age >= DisintegrationSweepState.FLASH_TICKS);
            if (!marker.lease.tick(mc.isPaused(), validOwner(marker, mc))) {
                STATE.retire(marker.frame.id()); iterator.remove();
                if (marker.owner == mc.player) AcademyClient.disintegrationSweepEndedInput(marker.frame.id().input(), marker.frame.id().epoch());
            }
        }
    }
    /** One unobtrusive receipt-only line below the classic CP bar; it never covers the reticle or claims preview hits. */
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (mc.player == null || mc.options.hideGui || !localEligible()) return;
        Marker marker = MARKERS.get(mc.player.getUUID());
        String status = marker != null && validOwner(marker, mc) ? DisintegrationSweepState.status(marker.frame) : terminalStatus;
        if (status == null) return;
        Component stateText = Component.translatable("academy.highpower.disintegration_sweep." + status);
        Component text = marker == null ? stateText : Component.translatable("academy.highpower.disintegration_sweep.status",
                Component.translatable("academy.highpower.disintegration_sweep." + (marker.frame.mining() ? "mining" : "attack")), stateText);
        var graphics = event.getGuiGraphics();
        graphics.fill(6, 54, 8, 64, 0xB8155D34);
        graphics.drawString(mc.font, text, 12, 55, 0xC4EAD1, true);
    }
    /** Use the proven exact chunk corridor, including corner grazes, before any world geometry is emitted. */
    private static boolean loadedPath(Vector from, Vector to) {
        if (world == null) return false;
        var chunks = BeamEtchingMesh.crossedChunks(new BeamEtchingMesh.Point(from.x(), from.y(), from.z()), new BeamEtchingMesh.Point(to.x(), to.y(), to.z()));
        if (chunks.isEmpty()) return false;
        for (var chunk : chunks) if (!world.hasChunkAt(new BlockPos(chunk.x() * 16, (int)Math.floor(from.y()), chunk.z() * 16))) return false;
        return true;
    }
    private static boolean loadedFrame(DisintegrationSweepState.Frame frame) {
        for (var segment : frame.path()) if (!loadedPath(segment.start(), segment.end())) return false;
        return true;
    }
    private static boolean loadedWarning(DisintegrationSweepState.Frame frame) {
        Vector previous = null;
        for (int i = 0; i <= 24; i++) {
            double angle = -DisintegrationSweepState.HALF_ANGLE + 2 * DisintegrationSweepState.HALF_ANGLE * i / 24;
            Vector end = frame.origin().add(DisintegrationSweepState.ray(frame, angle).scale(DisintegrationSweepState.RANGE));
            if (!loadedPath(frame.origin(), end) || previous != null && !loadedPath(previous, end)) return false;
            previous = end;
        }
        return true;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); if (world == null || MARKERS.isEmpty() && TAILS.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        List<RenderOwner> candidates = new ArrayList<>(MARKERS.size() + TAILS.size());
        for (Marker marker : MARKERS.values()) if (validOwner(marker, mc)) candidates.add(new RenderOwner(marker.owner, marker, null));
        for (Tail tail : TAILS.values()) if (validTail(tail, mc)) candidates.add(new RenderOwner(tail.owner, null, tail));
        List<RenderOwner> visible = candidates.stream().filter(v -> v.owner.distanceToSqr(camera) <= 64 * 64)
                .sorted(Comparator.comparingInt(v -> v.owner == mc.player ? 0 : 1)).limit(DisintegrationSweepMesh.MAX_VISIBLE_CONTEXTS).toList();
        if (visible.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(262144));
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        try {
            modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = event.getPoseStack().last().pose(); var facing = event.getCamera().getLookVector();
            Vector look = FoldedMirrorGeometry.unit(new Vector(facing.x(), facing.y(), facing.z())).orElse(null), eye = point(camera);
            var budget = new DisintegrationSweepMesh.Budget();
            for (RenderOwner entry : visible) {
                if (entry.tail != null) {
                    if (loadedFrame(entry.tail.fade.frame())) for (var face : DisintegrationSweepMesh.afterglow(entry.tail.fade, eye, look)) draw(matrix, face, eye, look, budget);
                    continue;
                }
                Marker marker = entry.marker;
                if (loadedWarning(marker.frame))
                    for (var face : DisintegrationSweepMesh.warning(marker.frame, eye, look)) draw(matrix, face, eye, look, budget);
                for (Flash flash : marker.flashes) if (loadedFrame(flash.frame))
                    for (var face : DisintegrationSweepMesh.path(flash.frame, eye, look, flash.age)) draw(matrix, face, eye, look, budget);
            }
        } finally {
            try { buffers.endBatch(); } finally { modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); }
        }
    }
    private static void draw(Matrix4f matrix, DisintegrationSweepMesh.Face face, Vector eye, Vector look, DisintegrationSweepMesh.Budget budget) {
        if (face.alpha() <= 0) return;
        for (var clipped : DisintegrationSweepMesh.nearClip(face, eye, look)) {
            if (!budget.take()) return;
            VertexConsumer out = buffers.getBuffer(ClassicRenderTypes.world(clipped.texture() < 0 ? WHITE : SMALL_RAY[clipped.texture()]));
            vertex(out, matrix, clipped.a().subtract(eye), 0, 0, clipped.color(), clipped.alpha());
            vertex(out, matrix, clipped.b().subtract(eye), 0, 1, clipped.color(), clipped.alpha());
            vertex(out, matrix, clipped.c().subtract(eye), 1, 1, clipped.color(), clipped.alpha());
            vertex(out, matrix, clipped.d().subtract(eye), 1, 0, clipped.color(), clipped.alpha());
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vector p, float u, float v, int rgb, int alpha) {
        out.addVertex(matrix, (float)p.x(), (float)p.y(), (float)p.z()).setUv(u, v).setColor(rgb >>> 16 & 255, rgb >>> 8 & 255, rgb & 255, alpha);
    }
    private static Vector point(Vec3 point) { return new Vector(point.x, point.y, point.z); }
    private static Vec3 vec(Vector point) { return new Vec3(point.x(), point.y(), point.z()); }

    /** Read-only snapshot: points = feet, origin, forward, across, then marks; numbers = status, remaining, from angle, to angle, mining; polyline = swept path. */
    private static double[] dv(cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        for (Marker m : MARKERS.values()) { if (!validOwner(m, mc) || m.frame == null) continue; var f = m.frame;
            var points = new java.util.ArrayList<double[]>(java.util.List.of(dv(f.ownerFeet()), dv(f.origin()), dv(f.forward()), dv(f.across())));
            if (f.marks() != null) for (var mark : f.marks()) if (points.size() < 20) points.add(dv(mark));
            var line = new java.util.ArrayList<double[]>();
            if (f.path() != null) for (var seg : f.path()) { if (line.isEmpty()) line.add(dv(seg.start())); if (line.size() < 24) line.add(dv(seg.end())); }
            out.add(new DecorSnap(m.owner == mc.player, 0, f.phase(), f.id().token(), points, new double[] {f.status(), f.remainingTicks(), f.fromAngle(), f.toAngle(), f.mining() ? 1 : 0}, line.isEmpty() ? java.util.List.of() : java.util.List.of(line)));
        }
        return out;
    }
}

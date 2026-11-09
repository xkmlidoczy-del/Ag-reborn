package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.highpower.MagneticDomain;
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

/** One bounded cosmetic controller per caster. All modes, impacts and refractions come from server receipts. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MagneticDomainEffects {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("academy", "textures/port/white.png");
    private static final int MAX_VISIBLE_CONTEXTS = 4;
    private static final MagneticDomainState STATE = new MagneticDomainState();
    private static final Map<UUID, Marker> MARKERS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long clearGeneration;
    private static MultiBufferSource.BufferSource buffers;
    private static int frameQuads;
    private static final int MAX_FRAME_QUADS = MAX_VISIBLE_CONTEXTS * MagneticDomainGeometry.MAX_QUADS;
    private static final class Marker {
        final Entity owner;
        final MagneticDomainState.Lease lease = new MagneticDomainState.Lease();
        MagneticDomain.Visual state;
        Vec3 hitPoint, hitFacing, refractionPoint, refractionAxis;
        Marker(Entity owner, MagneticDomain.Visual packet) { this.owner = owner; update(packet); }
        MagneticDomainState.Identity identity() { return identityOf(state); }
        void update(MagneticDomain.Visual packet) {
            state = packet; lease.received(packet.phase());
            if (packet.phase() == MagneticDomainState.HIT) {
                hitPoint = packet.point();
                Vec3 outward = packet.point().subtract(packet.center());
                hitFacing = outward.lengthSqr() > 1e-8 ? outward.normalize() : packet.axis();
            }
            if (packet.phase() == MagneticDomainState.REFRACT) { refractionPoint = packet.point(); refractionAxis = packet.axis(); }
        }
    }
    private MagneticDomainEffects() {}
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { MagneticDomain.clientReceiver = MagneticDomainEffects::receive; }
    }
    private static MagneticDomainState.Identity identityOf(MagneticDomain.Visual p) {
        return new MagneticDomainState.Identity(p.entity(), p.owner(), p.epoch(), p.input(), p.token());
    }
    private static MagneticDomainState.Frame frame(MagneticDomain.Visual p) {
        return new MagneticDomainState.Frame(identityOf(p), p.sequence(), p.phase(), point(p.center()), point(p.axis()), point(p.point()), p.slowFalling(), p.refracted());
    }
    public static void receive(MagneticDomain.Visual packet) {
        if (packet == null || packet.center() == null || packet.axis() == null || packet.point() == null) return;
        var frame = frame(packet); if (!MagneticDomainState.valid(frame)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel captured = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long session = AcademyClient.inputSessionEpoch(), generation = clearGeneration;
        mc.execute(() -> {
            if (captured == null || captured != mc.level || capturedPlayer != mc.player || capturedConnection != mc.getConnection()
                    || generation != clearGeneration || !AcademyClient.sameInputSessionEpoch(session)) return;
            Entity owner = captured.getEntity(packet.entity());
            if (owner == null || !owner.getUUID().equals(packet.owner())) return;
            Marker old = MARKERS.get(packet.owner());
            if (old != null && old.identity().equals(frame.id()) && old.owner != owner) return;
            boolean local = owner == mc.player;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch()) || !AcademyClient.magneticDomainInputMatches(packet.input(), packet.epoch()))) return;
            if (packet.phase() != MagneticDomainState.ENDED && (!owner.isAlive() || owner.isRemoved() || local && !localEligible())) return;
            if (!STATE.accept(frame, local)) return;
            if (packet.phase() == MagneticDomainState.ENDED) {
                MARKERS.remove(packet.owner());
                if (local) AcademyClient.magneticDomainEndedInput(packet.input(), packet.epoch());
                return;
            }
            if (local) AcademyClient.magneticDomainAcceptedInput(packet.input(), packet.epoch());
            if (old == null || !old.identity().equals(frame.id())) {
                if (old == null && MARKERS.size() >= MagneticDomainState.MAX_CONTEXTS) { STATE.retire(frame.id()); return; }
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
    public static void clear() { clearGeneration++; STATE.clear(); MARKERS.clear(); }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear();
        // World/player changes retire casts; only a different connection starts a new token namespace.
        if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean localEligible() {
        Minecraft mc = Minecraft.getInstance(); var state = AcademyClient.state;
        return mc.screen == null && mc.isWindowActive() && state != null && state.activated && state.overloadFine && !state.interfering
                && HighPowerSkills.CATEGORY.equals(state.category) && state.level == 5 && state.learned(MagneticDomain.ID)
                && state.presets.currentContains(MagneticDomain.ID) && SkillCatalog.enabled(HighPowerSkills.CATEGORY, MagneticDomain.ID);
    }
    private static boolean validOwner(Marker m, Minecraft mc) {
        return world != null && m.owner.isAlive() && !m.owner.isRemoved() && world.getEntity(m.owner.getId()) == m.owner
                && (m.owner != mc.player || localEligible() && AcademyClient.singleOwnerEpochMatches(m.state.epoch())
                && AcademyClient.magneticDomainInputMatches(m.state.input(), m.state.epoch()));
    }
    private static Marker localMarker() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        return marker != null && validOwner(marker, mc) && marker.lease.quiet() < MagneticDomainState.QUIET_TICKS ? marker : null;
    }
    public static boolean activeLocal() { return localMarker() != null; }
    public static boolean slowFallingLocal() { Marker marker = localMarker(); return marker != null && marker.state.slowFalling(); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Iterator<Marker> it = MARKERS.values().iterator();
        while (it.hasNext()) {
            Marker marker = it.next();
            if (!marker.lease.tick(mc.isPaused(), validOwner(marker, mc))) {
                STATE.retire(marker.identity()); it.remove();
                if (marker.owner == mc.player) AcademyClient.magneticDomainEndedInput(marker.state.input(), marker.state.epoch());
            }
        }
    }
    /** Cosmetic collision tests never load missing chunks or decide gameplay admission. */
    private static boolean visible(Entity owner, Vec3 from, Vec3 to) {
        if (world == null || !RailThunderArrayGeometry.validEndpoints(point(from), point(to))) return false;
        int steps = Math.max(1, (int)Math.ceil(from.distanceTo(to) / 4));
        for (int i = 0; i <= steps; i++) if (!world.hasChunkAt(BlockPos.containing(from.lerp(to, (double)i / steps)))) return false;
        var hit = world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < .0001;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); if (world == null || MARKERS.isEmpty()) return;
        frameQuads = 0;
        Vec3 camera = event.getCamera().getPosition();
        List<Marker> visible = MARKERS.values().stream().filter(m -> validOwner(m, mc) && m.owner.distanceToSqr(camera) <= 64 * 64)
                .sorted(Comparator.comparingInt(m -> m.owner == mc.player ? 0 : 1)).limit(MAX_VISIBLE_CONTEXTS).toList();
        if (visible.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        try {
            modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = event.getPoseStack().last().pose();
            var l = event.getCamera().getLookVector(); var u = event.getCamera().getUpVector();
            Vec3 look = new Vec3(l.x(), l.y(), l.z()), up = new Vec3(u.x(), u.y(), u.z());
            VertexConsumer out = buffers.getBuffer(ClassicRenderTypes.world(WHITE));
            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            for (Marker marker : visible) {
                // Switching the immediate buffer material invalidates the previous consumer.
                out = buffers.getBuffer(ClassicRenderTypes.world(WHITE));
                var budget = new MagneticDomainGeometry.Budget();
                Vec3 center = marker.owner.getPosition(partial).add(0, 1, 0);
                // Event points remain at the accepted world position when the caster moves away.
                if (marker.hitPoint != null) for (var s : MagneticDomainGeometry.impact(point(marker.hitPoint), point(marker.hitFacing), marker.lease.hitAge()))
                    line(out, matrix, marker.owner, null, s, camera, look, up, MagneticDomainGeometry.RECEIPT_WIDTH, MagneticDomainGeometry.WHITE, 225 - marker.lease.hitAge() * 35, budget);
                if (marker.refractionPoint != null) for (var s : MagneticDomainGeometry.refraction(point(marker.refractionPoint), point(marker.refractionAxis), marker.lease.refractionAge()))
                    line(out, matrix, marker.owner, null, s, camera, look, up, MagneticDomainGeometry.RECEIPT_WIDTH, MagneticDomainGeometry.BLUE, 215 - marker.lease.refractionAge() * 25, budget);
                // Interior flow is decorative, not a second defensive boundary or a predicted arrow path.
                // The saturated blue edge reads against daylight; its fine white core reads against stone.
                if (MagneticDomainVisualMaterial.enabled()) {
                    double time = MagneticDomainFlowGeometry.time(marker.lease.frame(), partial, mc.isPaused());
                    var flowOut = buffers.getBuffer(ClassicRenderTypes.world(MagneticDomainVisualMaterial.GLOW));
                    for (var path : MagneticDomainFlowGeometry.segments(point(center), time)) {
                        flow(flowOut, matrix, marker.owner, center, path, camera, look, up, time,
                                MagneticDomainFlowGeometry.Layer.SHOULDER, budget);
                        flow(flowOut, matrix, marker.owner, center, path, camera, look, up, time,
                                MagneticDomainFlowGeometry.Layer.CORE, budget);
                    }
                    out = buffers.getBuffer(ClassicRenderTypes.world(WHITE));
                } else {
                for (var s : MagneticDomainGeometry.circulation(point(center), marker.lease.frame())) {
                    line(out, matrix, marker.owner, center, s, camera, look, up, MagneticDomainGeometry.FLOW_WIDTH,
                            MagneticDomainGeometry.FLOW_BLUE, MagneticDomainGeometry.FLOW_ALPHA, budget);
                    line(out, matrix, marker.owner, center, s, camera, look, up, MagneticDomainGeometry.FLOW_CORE_WIDTH,
                            MagneticDomainGeometry.WHITE, MagneticDomainGeometry.CORE_ALPHA, budget);
                }
                }
                decorate(marker, center, camera, look, up, matrix, partial, mc);
                // The decoration submitted other materials; reacquire the classic white buffer for the remaining lines.
                out = buffers.getBuffer(ClassicRenderTypes.world(WHITE));
                for (var s : MagneticDomainGeometry.frontBoundary(point(center), point(marker.state.axis()), marker.lease.frame()))
                    line(out, matrix, marker.owner, center, s, camera, look, up, MagneticDomainGeometry.WIDTH, MagneticDomainGeometry.WHITE, 205, budget);
                for (var s : MagneticDomainGeometry.boundary(point(center), point(marker.state.axis()), marker.lease.frame()))
                    line(out, matrix, marker.owner, center, s, camera, look, up, MagneticDomainGeometry.WIDTH, MagneticDomainGeometry.BOUNDARY_BLUE, MagneticDomainGeometry.BOUNDARY_ALPHA, budget);
                if (marker.state.slowFalling()) for (var s : MagneticDomainGeometry.slowFall(point(center), marker.lease.frame()))
                    line(out, matrix, marker.owner, null, s, camera, look, up, .007, MagneticDomainGeometry.WHITE, 155, budget);
            }
        } finally {
            try { buffers.endBatch(); } finally { modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); }
        }
    }
    /** STANDARD/CINEMATIC only: dipole field loops, boundary footprint ring and riding beads; trimmed to a fixed quad budget. */
    private static void decorate(Marker marker, Vec3 center, Vec3 camera, Vec3 look, Vec3 up, Matrix4f matrix, float partial, Minecraft mc) {
        var tier = cn.academy.port.VisualTierConfig.tier();
        if (!tier.enhanced() || !(cn.academy.port.HighPowerVisualConfig.SPEC.isLoaded() && cn.academy.port.HighPowerVisualConfig.MAGNETIC_DOMAIN_FLOW.get())
                || !RailThunderArrayGrandMaterial.kitReady() || marker.owner.distanceToSqr(camera) > 40 * 40) return;
        double time = MagneticDomainFlowGeometry.time(marker.lease.frame(), partial, mc.isPaused());
        if (!Double.isFinite(time)) return;
        var fx = MagneticDomainDecor.fx(point(center), time, tier, marker.hitPoint == null ? null : point(marker.hitPoint), marker.lease.hitAge(),
                marker.refractionPoint == null ? null : point(marker.refractionPoint), marker.lease.refractionAge());
        var all = new FxLib.Builder().add(new FxLib.Parts(MagneticDomainDecor.strokes(point(center), time, tier), MagneticDomainDecor.sprites(point(center), time, tier), List.of())).add(fx).build();
        var quads = VisualKit.projectParts(all, point(camera), point(look), point(up), tier, MagneticDomainDecor.maxQuads(tier));
        VisualKit.submit(buffers, matrix, quads);
    }
    private static void line(VertexConsumer out, Matrix4f matrix, Entity owner, Vec3 domainCenter, RailThunderArrayGeometry.Segment segment,
                             Vec3 camera, Vec3 look, Vec3 up, double width, int rgb, int alpha, MagneticDomainGeometry.Budget budget) {
        Vec3 from = vec(segment.start()), to = vec(segment.end());
        if (alpha <= 0 || !visible(owner, from, to) || domainCenter != null && (!visible(owner, domainCenter, from) || !visible(owner, domainCenter, to))) return;
        // Reuse the corrected NEW151 camera projection, right-handed basis and full-width aim-center exclusion.
        var clipped = RailThunderArrayGeometry.nearClip(point(from.subtract(camera)), point(to.subtract(camera)), point(look), width);
        for (var part : RailThunderArrayGeometry.clearCenter(clipped, point(look), point(up), width)) {
            var ribbon = RailThunderArrayGeometry.ribbon(part, point(look), width); if (ribbon == null || !take(budget)) continue;
            Vec3 a = vec(ribbon.start()), b = vec(ribbon.end()), side = vec(ribbon.side());
            vertex(out, matrix, a.add(side), 0, 0, rgb, alpha); vertex(out, matrix, a.subtract(side), 0, 1, rgb, alpha);
            vertex(out, matrix, b.subtract(side), 1, 1, rgb, alpha); vertex(out, matrix, b.add(side), 1, 0, rgb, alpha);
        }
    }
    private static boolean take(MagneticDomainGeometry.Budget budget) {
        if (frameQuads >= MAX_FRAME_QUADS || !budget.take()) return false;
        frameQuads++; return true;
    }
    private static void flow(VertexConsumer out, Matrix4f matrix, Entity owner, Vec3 center,
                             MagneticDomainFlowGeometry.FlowSegment path, Vec3 camera, Vec3 look, Vec3 up,
                             double time, MagneticDomainFlowGeometry.Layer layer, MagneticDomainGeometry.Budget budget) {
        Vec3 from = vec(path.segment().start()), to = vec(path.segment().end());
        if (!visible(owner, from, to) || !visible(owner, center, from) || !visible(owner, center, to)) return;
        for (var ribbon : MagneticDomainFlowGeometry.ribbons(path, point(camera), point(look), point(up), time, layer)) {
            if (!take(budget)) continue;
            Vec3 a = vec(ribbon.start()), b = vec(ribbon.end());
            Vec3 sa = vec(ribbon.startSide()), sb = vec(ribbon.endSide());
            var pa = ribbon.startProfile(); var pb = ribbon.endProfile();
            vertex(out, matrix, a.add(sa), (float)ribbon.startT(), (float)pa.minV(), pa.rgb(), pa.alpha());
            vertex(out, matrix, a.subtract(sa), (float)ribbon.startT(), (float)pa.maxV(), pa.rgb(), pa.alpha());
            vertex(out, matrix, b.subtract(sb), (float)ribbon.endT(), (float)pb.maxV(), pb.rgb(), pb.alpha());
            vertex(out, matrix, b.add(sb), (float)ribbon.endT(), (float)pb.minV(), pb.rgb(), pb.alpha());
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 p, float u, float v, int rgb, int alpha) {
        out.addVertex(matrix, (float)p.x, (float)p.y, (float)p.z).setUv(u, v).setColor(rgb >>> 16 & 255, rgb >>> 8 & 255, rgb & 255, alpha);
    }
    private static Point point(Vec3 p) { return new Point(p.x, p.y, p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
}

package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.BeamEtching;
import cn.academy.port.highpower.PointMeltdown;
import cn.academy.port.client.BeamEtchingMesh.Block;
import cn.academy.port.client.BeamEtchingMesh.Box;
import cn.academy.port.client.BeamEtchingMesh.Mark;
import cn.academy.port.client.BeamEtchingMesh.Point;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW233 focus receipts. Native face geometry and thin original rays retain world depth and spawn zero particles. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class PointMeltdownEffects {
    private static final ResourceLocation WHITE = texture("port/white");
    private static final ResourceLocation[] SMALL_RAY = {texture("effects/mdray_small/blend_in"), texture("effects/mdray_small/tile"), texture("effects/mdray_small/blend_out")};
    private static final PointMeltdownState STATE = new PointMeltdownState();
    private static final Map<UUID, Marker> MARKERS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static long lastStatusAt = Long.MIN_VALUE;
    private static MultiBufferSource.BufferSource buffers;
    private static final class Flash {
        final PointMeltdown.Visual receipt;
        final Mark mark;
        final Box surface;
        int age;
        Flash(PointMeltdown.Visual receipt, Mark mark, Box surface) { this.receipt = receipt; this.mark = mark; this.surface = surface; }
    }
    private static final class Marker {
        final Entity owner;
        final PointMeltdownState.Lease lease = new PointMeltdownState.Lease();
        final List<Flash> flashes = new ArrayList<>();
        Mark cachedMark;
        Box cachedSurface;
        PointMeltdown.Visual state; int visualTicks; // Cosmetic only; never advances receipt focus or a lease.
        Marker(Entity owner, PointMeltdown.Visual receipt) { this.owner = owner; update(receipt); }
        PointMeltdownState.Identity identity() { return frame(state).id(); }
        void update(PointMeltdown.Visual receipt) {
            state = receipt; var frame = frame(receipt); lease.received(frame);
            if (receipt.phase() == PointMeltdownState.PREVIEW || receipt.phase() == PointMeltdownState.FOCUS) {
                cachedMark = frame.marks().isEmpty() ? null : frame.marks().getFirst();
                cachedSurface = cachedMark == null ? null : surface(owner, cachedMark);
            } else if (receipt.phase() == PointMeltdownState.CONFIRMED) {
                Mark mark = frame.marks().getFirst();
                flashes.clear();
                flashes.add(new Flash(receipt, mark, mark.equals(cachedMark) ? cachedSurface : null));
                cachedMark = null; cachedSurface = null;
            } else {
                cachedMark = null; cachedSurface = null;
                // A terminal receipt creates nothing. Only a previous confirmed removal may finish its brief flash.
                if (receipt.status() != PointMeltdownState.READY) flashes.clear();
            }
        }
    }
    private PointMeltdownEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { PointMeltdown.clientReceiver = PointMeltdownEffects::receive; }
    }
    private static PointMeltdownState.Frame frame(PointMeltdown.Visual packet) {
        return new PointMeltdownState.Frame(new PointMeltdownState.Identity(packet.entity(), packet.owner(), packet.epoch(), packet.input(), packet.token()),
                packet.sequence(), packet.phase(), point(packet.origin()), packet.marks().stream().map(PointMeltdownEffects::mark).toList(),
                packet.focusTicks(), packet.status(), packet.remainingTicks());
    }
    public static void receive(PointMeltdown.Visual packet) {
        if (packet == null || packet.origin() == null || packet.marks() == null || packet.marks().size() > 1
                || packet.marks().stream().anyMatch(m -> m == null || m.pos() == null || m.point() == null || m.face() == null)) return;
        var frame = frame(packet); if (!PointMeltdownState.valid(frame)) return;
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
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch()) || !AcademyClient.pointMeltdownInputMatches(packet.input(), packet.epoch()))) return;
            if (packet.phase() != PointMeltdownState.ENDED && (!owner.isAlive() || owner.isRemoved() || local && !localEligible())) return;
            if (!STATE.accept(frame, local)) return;
            if (local) {
                reportStatus(packet, old == null ? -1 : old.state.status());
                AcademyClient.pointMeltdownAcceptedInput(packet.input(), packet.epoch());
                if (packet.phase() == PointMeltdownState.ENDED) AcademyClient.pointMeltdownEndedInput(packet.input(), packet.epoch());
            }
            if (old == null || !old.identity().equals(frame.id())) {
                if (packet.phase() == PointMeltdownState.ENDED) return;
                if (old == null && MARKERS.size() >= PointMeltdownState.MAX_CONTEXTS) { STATE.retire(frame.id()); return; }
                MARKERS.put(packet.owner(), new Marker(owner, packet));
            } else old.update(packet);
        });
    }
    private static void reportStatus(PointMeltdown.Visual receipt, int previousStatus) {
        String key = receipt.phase() == PointMeltdownState.ENDED && receipt.status() == PointMeltdownState.CANCELED ? "canceled"
                : receipt.status() == PointMeltdownState.UNAVAILABLE && previousStatus != PointMeltdownState.UNAVAILABLE ? "unavailable" : null;
        if (key == null) return;
        long now = net.minecraft.Util.getMillis(); Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (lastStatusAt == Long.MIN_VALUE || now - lastStatusAt >= 2000)) {
            // Existing recovery HUD owns the actionbar; ordinary chat receives only state-change refusals.
            mc.player.displayClientMessage(Component.translatable("academy.highpower.point_meltdown." + key), false);
            lastStatusAt = now;
        }
    }
    public static void abortLocal(long input, long epoch) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); STATE.cancel(epoch, input);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        if (marker != null && marker.state.epoch() == epoch && marker.state.input() <= input) {
            STATE.retire(marker.identity()); MARKERS.remove(mc.player.getUUID());
        }
    }
    public static void clear() { generation++; STATE.clear(); MARKERS.clear(); lastStatusAt = Long.MIN_VALUE; }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear();
        if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean localEligible() {
        Minecraft mc = Minecraft.getInstance(); var s = AcademyClient.state;
        return mc.screen == null && mc.isWindowActive() && mc.getCameraEntity() == mc.player
                && mc.player != null && !mc.player.isSpectator() && s != null && s.activated && s.overloadFine && !s.interfering
                && "meltdowner".equals(s.category) && s.level >= 3 && s.learned(PointMeltdown.ID)
                && s.presets.currentContains(PointMeltdown.ID) && SkillCatalog.enabled("meltdowner", PointMeltdown.ID);
    }
    private static boolean validOwner(Marker marker, Minecraft mc) {
        return world != null && marker.owner.isAlive() && !marker.owner.isRemoved() && world.getEntity(marker.owner.getId()) == marker.owner
                && (marker.owner != mc.player || localEligible() && AcademyClient.singleOwnerEpochMatches(marker.state.epoch())
                && (marker.state.phase() == PointMeltdownState.ENDED || AcademyClient.pointMeltdownInputMatches(marker.state.input(), marker.state.epoch())));
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        if (marker == null || !validOwner(marker, mc)) return ClassicAbilityHud.DelegateVisual.IDLE;
        if (!marker.flashes.isEmpty()) return ClassicAbilityHud.DelegateVisual.ACTIVE;
        return !marker.lease.marks().isEmpty() ? ClassicAbilityHud.DelegateVisual.CHARGE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Iterator<Marker> it = MARKERS.values().iterator();
        while (it.hasNext()) {
            Marker marker = it.next(); boolean valid = validOwner(marker, mc);
            boolean leased = marker.lease.tick(mc.isPaused(), valid);
            if (!mc.isPaused() && valid) marker.visualTicks = Math.min(200, marker.visualTicks + 1);
            if (!mc.isPaused()) marker.flashes.removeIf(f -> ++f.age >= PointMeltdownMesh.FLASH_TICKS);
            if (!valid || !leased || marker.lease.ended() && marker.flashes.isEmpty()) {
                STATE.retire(marker.identity()); it.remove();
                if (marker.owner == mc.player) AcademyClient.pointMeltdownEndedInput(marker.state.input(), marker.state.epoch());
            }
        }
    }
    private static boolean loadedPath(Vec3 from, Vec3 to) {
        if (world == null || !BeamEtchingMesh.validPoint(point(from)) || !BeamEtchingMesh.validPoint(point(to)) || from.distanceToSqr(to) > 64 * 64) return false;
        var chunks = BeamEtchingMesh.crossedChunks(point(from), point(to));
        if (chunks.isEmpty()) return false;
        for (var chunk : chunks) if (!world.hasChunkAt(new BlockPos(chunk.x() * 16, (int)Math.floor(from.y), chunk.z() * 16))) return false;
        return true;
    }
    /** Exact point and side must still be exposed. This read-only ray can only suppress an old receipt. */
    private static boolean visibleFace(Entity owner, Vec3 from, Mark mark) {
        Vec3 normal = vec(BeamEtchingMesh.normal(mark.face())), end = vec(mark.point());
        if (!loadedPath(from, end) || from.subtract(end).dot(normal) <= 0) return false;
        var hit = world.clip(new ClipContext(from, end.subtract(normal.scale(.002)), ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, owner));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos(mark.block()))
                && hit.getDirection().ordinal() == mark.face() && hit.getLocation().distanceToSqr(end) <= .0001;
    }
    private static boolean visiblePath(Entity owner, Vec3 from, Vec3 to) {
        if (!loadedPath(from, to)) return false;
        var hit = world.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, owner));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) <= .000001;
    }
    private static Box surface(Entity owner, Mark mark) {
        if (world == null || !world.hasChunkAt(pos(mark.block()))) return null;
        BlockPos pos = pos(mark.block());
        var nativeBoxes = world.getBlockState(pos).getShape(world, pos, CollisionContext.of(owner)).toAabbs();
        if (nativeBoxes.size() > BeamEtchingMesh.MAX_SHAPE_BOXES) return null;
        List<Box> boxes = new ArrayList<>(nativeBoxes.size());
        for (var box : nativeBoxes) boxes.add(new Box(new Point(pos.getX() + box.minX, pos.getY() + box.minY, pos.getZ() + box.minZ),
                new Point(pos.getX() + box.maxX, pos.getY() + box.maxY, pos.getZ() + box.maxZ)));
        return BeamEtchingMesh.surface(mark, boxes);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); if (world == null || MARKERS.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        List<Marker> visible = MARKERS.values().stream().filter(m -> validOwner(m, mc) && m.owner.distanceToSqr(camera) <= 48 * 48)
                .sorted(Comparator.comparingInt(m -> m.owner == mc.player ? 0 : 1)).limit(PointMeltdownMesh.MAX_CONTEXTS).toList();
        if (visible.isEmpty()) return;
        decorate(visible, event, camera);
        if (PointMeltdownFocusMaterial.enabled() && renderFocus(visible, event, camera)) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(131072));
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        try {
            modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = event.getPoseStack().last().pose(); var facing = event.getCamera().getLookVector();
            Point look = new Point(facing.x(), facing.y(), facing.z()), eye = point(camera);
            var budget = new PointMeltdownMesh.Budget();
            for (Marker marker : visible) {
                for (Mark mark : marker.lease.marks()) {
                    Vec3 ownerEye = marker.owner.getEyePosition();
                    if (!PointMeltdownMesh.validSource(point(ownerEye), mark) || !visibleFace(marker.owner, ownerEye, mark)
                            || !visibleFace(marker.owner, marker.state.origin(), mark) || !visibleFace(marker.owner, camera, mark)) continue;
                    // Always use today's native outline; stale cached geometry is reserved solely for a confirmed removal.
                    Box surface = surface(marker.owner, mark); if (surface == null) continue;
                    boolean focusing = marker.lease.focusing();
                    for (var quad : PointMeltdownMesh.ray(point(marker.state.origin()), mark, eye, look, focusing, false, 0)) draw(matrix, quad, eye, look, budget);
                    for (var quad : PointMeltdownMesh.focal(mark, surface, marker.lease.focusTicks(), focusing, false, 0, eye)) draw(matrix, quad, eye, look, budget);
                }
                for (Flash flash : marker.flashes) {
                    Vec3 endpoint = vec(flash.mark.point());
                    if (!visiblePath(marker.owner, camera, endpoint) || !visiblePath(marker.owner, flash.receipt.origin(), endpoint)) continue;
                    for (var quad : PointMeltdownMesh.ray(point(flash.receipt.origin()), flash.mark, eye, look, false, true, flash.age)) draw(matrix, quad, eye, look, budget);
                    // Exact historical outline comes only from this same mark's preview. Missing history invents no surface.
                    if (flash.surface != null) for (var quad : PointMeltdownMesh.focal(flash.mark, flash.surface, 10, false, true, flash.age, eye)) draw(matrix, quad, eye, look, budget);
                }
            }
        } finally {
            try { buffers.endBatch(); } finally { modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); }
        }
    }
    /** CINEMATIC beam glow, heat halo, molten motes and completion burst for every accepted mark and flash. */
    private static void decorate(List<Marker> visible, RenderLevelStageEvent event, Vec3 camera) {
        var tier = cn.academy.port.VisualTierConfig.tier();
        if (tier.layers < 3 || !(cn.academy.port.HighPowerVisualConfig.SPEC.isLoaded() && cn.academy.port.HighPowerVisualConfig.POINT_MELTDOWN_FOCUS.get())) return;
        var parts = new FxLib.Builder(); double time = world.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (Marker marker : visible) {
            List<PointMeltdownDecor.Beam> beams = new ArrayList<>();
            for (Mark mark : marker.lease.marks()) {
                Vec3 ownerEye = marker.owner.getEyePosition();
                if (!PointMeltdownMesh.validSource(point(ownerEye), mark) || !visibleFace(marker.owner, ownerEye, mark) || !visibleFace(marker.owner, camera, mark)) continue;
                var o = marker.state.origin(); var t = mark.point();
                beams.add(new PointMeltdownDecor.Beam(new cn.academy.port.client.RailThunderArrayGeometry.Point(o.x, o.y, o.z), new cn.academy.port.client.RailThunderArrayGeometry.Point(t.x(), t.y(), t.z()),
                        marker.lease.focusTicks(), marker.lease.focusing(), false, 0));
            }
            for (Flash flash : marker.flashes) {
                var o = flash.receipt.origin(); var t = flash.mark.point();
                beams.add(new PointMeltdownDecor.Beam(new cn.academy.port.client.RailThunderArrayGeometry.Point(o.x(), o.y(), o.z()), new cn.academy.port.client.RailThunderArrayGeometry.Point(t.x(), t.y(), t.z()),
                        10, false, true, flash.age));
            }
            parts.add(PointMeltdownDecor.fx(beams, marker.owner.getUUID().getLeastSignificantBits(), time, tier));
        }
        VisualKit.render(event, parts.build(), tier, PointMeltdownDecor.MAX_QUADS);
    }
    /** Reuses the exact same visibility/receipt checks; no new target or historical surface is inferred. */
    private static boolean renderFocus(List<Marker> visible, RenderLevelStageEvent event, Vec3 camera) {
        List<PointMeltdownFocusRenderer.Request> requests = new ArrayList<>();
        for (Marker marker : visible) {
            for (Mark mark : marker.lease.marks()) {
                Vec3 ownerEye = marker.owner.getEyePosition();
                if (!PointMeltdownMesh.validSource(point(ownerEye), mark) || !visibleFace(marker.owner, ownerEye, mark)
                        || !visibleFace(marker.owner, marker.state.origin(), mark) || !visibleFace(marker.owner, camera, mark)) continue;
                Box surface = surface(marker.owner, mark); if (surface == null) continue;
                requests.add(new PointMeltdownFocusRenderer.Request(point(marker.state.origin()), mark, surface,
                        marker.lease.focusTicks(), marker.lease.focusing(), false, 0, marker.visualTicks));
            }
            for (Flash flash : marker.flashes) {
                Vec3 endpoint = vec(flash.mark.point());
                if (!visiblePath(marker.owner, camera, endpoint) || !visiblePath(marker.owner, flash.receipt.origin(), endpoint)) continue;
                // A missing historic face is not invented after removal. The original renderer remains the fallback.
                if (flash.surface == null) return false;
                requests.add(new PointMeltdownFocusRenderer.Request(point(flash.receipt.origin()), flash.mark,
                        flash.surface, 10, false, true, flash.age, marker.visualTicks));
            }
        }
        return PointMeltdownFocusRenderer.render(requests, event);
    }
    private static void draw(Matrix4f matrix, BeamEtchingMesh.Quad quad, Point camera, Point look, PointMeltdownMesh.Budget budget) {
        if (quad.alpha() <= 0) return;
        for (var clipped : BeamEtchingMesh.nearClip(quad, camera, look)) {
            if (!budget.take()) return;
            VertexConsumer out = buffers.getBuffer(ClassicRenderTypes.world(clipped.texture() < 0 ? WHITE : SMALL_RAY[clipped.texture()]));
            vertex(out, matrix, clipped.a().subtract(camera), 0, 0, clipped.color(), clipped.alpha());
            vertex(out, matrix, clipped.b().subtract(camera), 0, 1, clipped.color(), clipped.alpha());
            vertex(out, matrix, clipped.c().subtract(camera), 1, 1, clipped.color(), clipped.alpha());
            vertex(out, matrix, clipped.d().subtract(camera), 1, 0, clipped.color(), clipped.alpha());
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Point p, float u, float v, int rgb, int alpha) {
        out.addVertex(matrix, (float)p.x(), (float)p.y(), (float)p.z()).setUv(u, v).setColor(rgb >>> 16 & 255, rgb >>> 8 & 255, rgb & 255, alpha);
    }
    private static Mark mark(BeamEtching.Mark m) { return new Mark(new Block(m.pos().getX(), m.pos().getY(), m.pos().getZ()), point(m.point()), m.face().ordinal()); }
    private static BlockPos pos(Block b) { return new BlockPos(b.x(), b.y(), b.z()); }
    private static Point point(Vec3 p) { return new Point(p.x, p.y, p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
}

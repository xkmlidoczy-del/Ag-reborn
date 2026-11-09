package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.BeamEtching;
import cn.academy.port.client.BeamEtchingMesh.Block;
import cn.academy.port.client.BeamEtchingMesh.Box;
import cn.academy.port.client.BeamEtchingMesh.Mark;
import cn.academy.port.client.BeamEtchingMesh.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

/** NEW213 receipt-only face cuts. Batched world meshes retain native depth and spawn zero particles. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class BeamEtchingEffects {
    private static final ResourceLocation WHITE = texture("port/white");
    private static final ResourceLocation[] SMALL_RAY = {texture("effects/mdray_small/blend_in"), texture("effects/mdray_small/tile"), texture("effects/mdray_small/blend_out")};
    private static final BeamEtchingState STATE = new BeamEtchingState();
    private static final Map<UUID, Marker> MARKERS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static long lastStatusAt = Long.MIN_VALUE;
    private static MultiBufferSource.BufferSource buffers;
    private static final class Flash {
        final BeamEtching.Visual receipt;
        final Mark mark;
        final Box surface;
        int age;
        Flash(BeamEtching.Visual receipt, Mark mark, Box surface) { this.receipt = receipt; this.mark = mark; this.surface = surface; }
    }
    private static final class Marker {
        final Entity owner;
        final BeamEtchingState.Lease lease = new BeamEtchingState.Lease();
        final Map<Block, Box> surfaces = new HashMap<>();
        final List<Flash> flashes = new ArrayList<>();
        BeamEtching.Visual state;
        Marker(Entity owner, BeamEtching.Visual receipt) { this.owner = owner; update(receipt); }
        BeamEtchingState.Identity identity() { return frame(state).id(); }
        void update(BeamEtching.Visual receipt) {
            state = receipt; var frame = frame(receipt); lease.received(frame);
            if (receipt.phase() == BeamEtchingState.PREVIEW) {
                for (Mark mark : frame.marks()) {
                    Box surface = surface(owner, mark);
                    if (surface != null && surfaces.size() < BeamEtchingMesh.MAX_MARKS) surfaces.put(mark.block(), surface);
                }
            } else if (receipt.phase() == BeamEtchingState.CONFIRMED) {
                Mark mark = frame.marks().getFirst();
                if (flashes.size() == BeamEtchingMesh.MAX_MARKS) flashes.removeFirst();
                flashes.add(new Flash(receipt, mark, surfaces.remove(mark.block())));
            } else surfaces.clear();
        }
    }
    private BeamEtchingEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { BeamEtching.clientReceiver = BeamEtchingEffects::receive; }
    }
    private static BeamEtchingState.Frame frame(BeamEtching.Visual packet) {
        return new BeamEtchingState.Frame(new BeamEtchingState.Identity(packet.entity(), packet.owner(), packet.epoch(), packet.input(), packet.token()),
                packet.sequence(), packet.phase(), point(packet.origin()), packet.marks().stream().map(BeamEtchingEffects::mark).toList(),
                packet.confirmedCount(), packet.status(), packet.remainingTicks());
    }
    public static void receive(BeamEtching.Visual packet) {
        if (packet == null || packet.origin() == null || packet.marks() == null || packet.marks().size() > BeamEtchingMesh.MAX_MARKS
                || packet.marks().stream().anyMatch(m -> m == null || m.pos() == null || m.point() == null || m.face() == null)) return;
        var frame = frame(packet); if (!BeamEtchingState.valid(frame)) return;
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
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch()) || !AcademyClient.beamEtchingInputMatches(packet.input(), packet.epoch()))) return;
            if (packet.phase() != BeamEtchingState.ENDED && (!owner.isAlive() || owner.isRemoved() || local && !localEligible())) return;
            if (!STATE.accept(frame, local)) return;
            if (local) {
                reportStatus(packet, old == null ? -1 : old.state.status());
                AcademyClient.beamEtchingAcceptedInput(packet.input(), packet.epoch());
                if (packet.phase() == BeamEtchingState.ENDED) AcademyClient.beamEtchingEndedInput(packet.input(), packet.epoch());
            }
            if (old == null || !old.identity().equals(frame.id())) {
                if (packet.phase() == BeamEtchingState.ENDED) return;
                if (old == null && MARKERS.size() >= BeamEtchingState.MAX_CONTEXTS) { STATE.retire(frame.id()); return; }
                MARKERS.put(packet.owner(), new Marker(owner, packet));
            } else old.update(packet);
        });
    }
    private static void reportStatus(BeamEtching.Visual receipt, int previousStatus) {
        String key = receipt.phase() == BeamEtchingState.ENDED && receipt.status() == BeamEtchingState.NO_SUCCESS ? "no_success"
                : receipt.status() == BeamEtchingState.UNAVAILABLE && previousStatus != BeamEtchingState.UNAVAILABLE ? "unavailable" : null;
        if (key == null) return;
        long now = net.minecraft.Util.getMillis(); Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (lastStatusAt == Long.MIN_VALUE || now - lastStatusAt >= 2000)) {
            // Existing recovery HUD owns the actionbar; ordinary chat receives only state-change refusals.
            mc.player.displayClientMessage(Component.translatable("academy.highpower.beam_etching." + key), false);
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
                && "meltdowner".equals(s.category) && s.level >= 1 && s.learned(BeamEtching.ID)
                && s.presets.currentContains(BeamEtching.ID) && SkillCatalog.enabled("meltdowner", BeamEtching.ID);
    }
    private static boolean validOwner(Marker marker, Minecraft mc) {
        return world != null && marker.owner.isAlive() && !marker.owner.isRemoved() && world.getEntity(marker.owner.getId()) == marker.owner
                && (marker.owner != mc.player || localEligible() && AcademyClient.singleOwnerEpochMatches(marker.state.epoch())
                && (marker.state.phase() == BeamEtchingState.ENDED || AcademyClient.beamEtchingInputMatches(marker.state.input(), marker.state.epoch())));
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
            if (!mc.isPaused()) marker.flashes.removeIf(f -> ++f.age >= BeamEtchingMesh.FLASH_TICKS);
            if (!valid || !leased || marker.lease.ended() && marker.flashes.isEmpty()) {
                STATE.retire(marker.identity()); it.remove();
                if (marker.owner == mc.player) AcademyClient.beamEtchingEndedInput(marker.state.input(), marker.state.epoch());
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
                .sorted(Comparator.comparingInt(m -> m.owner == mc.player ? 0 : 1)).limit(BeamEtchingMesh.MAX_VISIBLE_CONTEXTS).toList();
        if (visible.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(131072));
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        try {
            modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = event.getPoseStack().last().pose(); var facing = event.getCamera().getLookVector();
            Point look = new Point(facing.x(), facing.y(), facing.z()), eye = point(camera);
            var budget = new BeamEtchingMesh.Budget();
            for (Marker marker : visible) {
                for (Mark mark : marker.lease.marks()) {
                    Vec3 ownerEye = marker.owner.getEyePosition();
                    if (!BeamEtchingMesh.validSource(point(ownerEye), mark) || !visibleFace(marker.owner, ownerEye, mark) || !visibleFace(marker.owner, camera, mark)) continue;
                    // Always use today's native outline; stale cached geometry is reserved solely for a confirmed removal.
                    Box surface = surface(marker.owner, mark); if (surface == null) continue;
                    for (var quad : BeamEtchingMesh.cut(mark, surface, false, 0)) draw(matrix, quad, eye, look, budget);
                }
                for (Flash flash : marker.flashes) {
                    Vec3 endpoint = vec(flash.mark.point());
                    if (!visiblePath(marker.owner, camera, endpoint) || !visiblePath(marker.owner, flash.receipt.origin(), endpoint)) continue;
                    for (var quad : BeamEtchingMesh.confirmedRay(point(flash.receipt.origin()), flash.mark, eye, look, flash.age)) draw(matrix, quad, eye, look, budget);
                    // Exact historical outline comes only from this same mark's preview. Missing history invents no surface.
                    if (flash.surface != null) for (var quad : BeamEtchingMesh.cut(flash.mark, flash.surface, true, flash.age)) draw(matrix, quad, eye, look, budget);
                }
            }
        } finally {
            try { buffers.endBatch(); } finally { modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); }
        }
    }
    private static void draw(Matrix4f matrix, BeamEtchingMesh.Quad quad, Point camera, Point look, BeamEtchingMesh.Budget budget) {
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

    /** Read-only snapshot: points = origin then every etched mark; numbers = confirmed count, status, remaining ticks. */
    private static double[] dp(net.minecraft.world.phys.Vec3 p) { return p == null ? DecorSnap.NONE : new double[] {p.x, p.y, p.z}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        for (Marker m : MARKERS.values()) { if (!validOwner(m, mc) || m.state == null) continue; var s = m.state;
            var points = new java.util.ArrayList<double[]>(); points.add(dp(s.origin()));
            if (s.marks() != null) for (var mark : s.marks()) if (points.size() < 17) points.add(dp(mark.point()));
            out.add(DecorSnap.of(m.owner == mc.player, 0, s.phase(), s.token(), points, s.confirmedCount(), s.status(), s.remainingTicks())); }
        return out;
    }
}

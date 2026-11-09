package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.DualPoint;
import cn.academy.port.highpower.core.DualPointRules;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** NEW211 uses only the classic narrow Meltdowner ray assets; all combat receipts are server-owned. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class DualPointEffects {
    private static final ResourceLocation WHITE = texture("port/white");
    private static final ResourceLocation[] SMALL_RAY = {texture("effects/mdray_small/blend_in"), texture("effects/mdray_small/tile"), texture("effects/mdray_small/blend_out")};
    private static final int MAX_VISIBLE_CONTEXTS = 8, MAX_FLASHES = 2;
    private static final DualPointState STATE = new DualPointState();
    private static final Map<UUID, Marker> MARKERS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static long generation;
    private static MultiBufferSource.BufferSource buffers;
    private record Preview(Marker marker, DualPoint.Visual receipt, DualPointGeometry.Point endpoint, DualPointGeometry.ScreenMark screen,
                           Vec3 camera, String label, int width, int height, long at, long generation) {}
    private static List<Preview> previews = List.of();
    private static final class Flash {
        final DualPoint.Visual receipt;
        int age;
        Flash(DualPoint.Visual receipt) { this.receipt = receipt; }
    }
    private static final class Marker {
        final Entity owner;
        final DualPointState.MarkLease mark = new DualPointState.MarkLease();
        final List<Flash> flashes = new ArrayList<>();
        DualPoint.Visual state;
        int quiet;
        Marker(Entity owner, DualPoint.Visual receipt) { this.owner = owner; update(receipt); }
        DualPointState.Identity identity() { return frame(state).id(); }
        void update(DualPoint.Visual receipt) {
            state = receipt; quiet = 0; mark.received(frame(receipt));
            if (receipt.phase() == DualPointState.FIRE) {
                if (flashes.size() == MAX_FLASHES) flashes.removeFirst();
                flashes.add(new Flash(receipt));
            }
        }
    }
    private DualPointEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { DualPoint.clientReceiver = DualPointEffects::receive; }
    }
    private static DualPointState.Frame frame(DualPoint.Visual p) {
        return new DualPointState.Frame(new DualPointState.Identity(p.owner(), p.ownerUuid(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), p.slot(), point(p.origin()), point(p.end()), p.status(), p.remainingTicks());
    }
    public static void receive(DualPoint.Visual receipt) {
        if (receipt == null || receipt.origin() == null || receipt.end() == null) return;
        var frame = frame(receipt); if (!DualPointState.valid(frame)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(receipt.owner());
            if (owner == null || !owner.getUUID().equals(receipt.ownerUuid()) || !owner.isAlive() || owner.isRemoved()) return;
            boolean local = owner == mc.player;
            if (local && (!localEligible() || !AcademyClient.singleOwnerEpochMatches(receipt.epoch())
                    || !AcademyClient.dualPointInputMatches(receipt.input(), receipt.epoch()))) return;
            if (!STATE.accept(frame, local)) return;
            if (local) {
                AcademyClient.dualPointAcceptedInput(receipt.input(), receipt.epoch());
                if (receipt.phase() == DualPointState.ENDED) AcademyClient.dualPointEndedInput(receipt.input(), receipt.epoch());
            }
            Marker old = MARKERS.get(receipt.ownerUuid());
            if (old == null || old.state.token() != receipt.token()) {
                if (receipt.phase() == DualPointState.ENDED) return;
                if (old == null && MARKERS.size() >= DualPointState.MAX_CONTEXTS) { STATE.retire(frame.id()); return; }
                MARKERS.put(receipt.ownerUuid(), new Marker(owner, receipt));
            } else old.update(receipt);
        });
    }
    public static void abortLocal(long input, long epoch) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); STATE.cancel(epoch, input);
        if (mc.player == null) return;
        Marker marker = MARKERS.get(mc.player.getUUID());
        if (marker != null && marker.state.epoch() == epoch && marker.state.input() <= input) {
            STATE.retire(marker.identity()); MARKERS.remove(mc.player.getUUID());
        }
    }
    public static void clear() { generation++; STATE.clear(); MARKERS.clear(); previews = List.of(); }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear(); STATE.resetSession(); world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean localEligible() {
        Minecraft mc = Minecraft.getInstance(); var s = AcademyClient.state;
        return mc.screen == null && mc.isWindowActive() && s.activated && s.overloadFine && !s.interfering
                && "meltdowner".equals(s.category) && s.level >= 1 && s.learned(DualPoint.ID)
                && s.presets.currentContains(DualPoint.ID) && SkillCatalog.enabled("meltdowner", DualPoint.ID);
    }
    private static boolean validOwner(Marker marker, Minecraft mc) {
        return world != null && marker.owner.isAlive() && !marker.owner.isRemoved() && world.getEntity(marker.owner.getId()) == marker.owner
                && (marker.owner != mc.player || localEligible() && AcademyClient.singleOwnerEpochMatches(marker.state.epoch())
                && (marker.state.phase() == DualPointState.ENDED || AcademyClient.dualPointInputMatches(marker.state.input(), marker.state.epoch())));
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        if (marker == null || !validOwner(marker, mc)) return ClassicAbilityHud.DelegateVisual.IDLE;
        if (!marker.flashes.isEmpty()) return ClassicAbilityHud.DelegateVisual.ACTIVE;
        return marker.mark.visible() ? ClassicAbilityHud.DelegateVisual.CHARGE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); boolean paused = mc.isPaused();
        Iterator<Marker> it = MARKERS.values().iterator();
        while (it.hasNext()) {
            Marker marker = it.next(); boolean valid = validOwner(marker, mc);
            marker.mark.tick(paused, valid);
            if (!paused) { marker.quiet++; marker.flashes.removeIf(f -> ++f.age >= DualPointGeometry.FLASH_TICKS); }
            if (!valid || marker.flashes.isEmpty() && (marker.state.phase() == DualPointState.ENDED || marker.quiet >= DualPointState.QUIET_TICKS)) {
                STATE.retire(marker.identity()); it.remove();
            }
        }
    }
    /** Read-only block visibility suppresses geometry. It never searches for entities or changes a hit. */
    private static boolean visible(Entity owner, Vec3 from, Vec3 to) {
        if (world == null || !DualPointGeometry.validPoint(point(from)) || !DualPointGeometry.validPoint(point(to)) || from.distanceToSqr(to) > 256 * 256) return false;
        int steps = Math.max(1, (int)Math.ceil(from.distanceTo(to) / 8));
        for (int i = 0; i <= steps; i++) if (!world.hasChunkAt(BlockPos.containing(from.lerp(to, (double)i / steps)))) return false;
        var hit = world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) <= .0025;
    }
    /** Preview overlays require an entirely unobstructed block path, with no endpoint-surface tolerance. */
    private static boolean strictMarkPath(Entity owner, Vec3 from, Vec3 to) {
        if (world == null || !DualPointGeometry.validPoint(point(from)) || !DualPointGeometry.validPoint(point(to)) || from.distanceToSqr(to) > 256 * 256) return false;
        int steps = Math.max(1, (int)Math.ceil(from.distanceTo(to) / 8));
        for (int i = 0; i <= steps; i++) if (!world.hasChunkAt(BlockPos.containing(from.lerp(to, (double)i / steps)))) return false;
        return world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getType() == HitResult.Type.MISS;
    }
    /** Recheck the same real secondary-hand corridor without acquiring or following an entity. */
    private static Vec3 visibleMarkOrigin(Marker marker, Vec3 camera, DualPointGeometry.Point endpoint) {
        Vec3 eye = marker.owner.getEyePosition(), look = marker.owner.getLookAngle();
        var source = DualPointRules.origin(new DualPointRules.Point(eye.x, eye.y, eye.z), new DualPointRules.Point(look.x, look.y, look.z), 1);
        if (source == null) return null;
        Vec3 nativeOrigin = new Vec3(source.x(), source.y(), source.z());
        return DualPointGeometry.validEndpoints(point(nativeOrigin), endpoint)
                && strictMarkPath(marker.owner, eye, nativeOrigin) && strictMarkPath(marker.owner, nativeOrigin, vec(endpoint))
                && strictMarkPath(marker.owner, camera, vec(endpoint)) ? nativeOrigin : null;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        previews = List.of();
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); if (world == null || MARKERS.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        List<Marker> visibleMarkers = MARKERS.values().stream().filter(m -> validOwner(m, mc) && m.owner.distanceToSqr(camera) <= 128 * 128)
                .sorted(Comparator.comparingInt(m -> m.owner == mc.player ? 0 : 1)).limit(MAX_VISIBLE_CONTEXTS).toList();
        if (visibleMarkers.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(16384));
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        try {
            modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix(); Matrix4f matrix = event.getPoseStack().last().pose();
            var l = event.getCamera().getLookVector(); var u = event.getCamera().getUpVector();
            Vec3 look = new Vec3(l.x(), l.y(), l.z()), up = new Vec3(u.x(), u.y(), u.z());
            var budget = new DualPointGeometry.Budget();
            var projected = new ArrayList<Preview>();
            Matrix4f projection = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).mul(matrix);
            int guiWidth = mc.getWindow().getGuiScaledWidth(), guiHeight = mc.getWindow().getGuiScaledHeight();
            String label = Component.translatableWithFallback("academy.highpower.dual_point.prepared", "PREPARE").getString();
            if (label.codePointCount(0, label.length()) > 8 || mc.font.width(label) > 46) label = "PREPARE";
            for (Marker marker : visibleMarkers) {
                for (Flash flash : marker.flashes) {
                    var shot = flash.receipt;
                    // A native damage refusal can still stop a real emitted beam. Only zero-length pre-emission refusal has no path.
                    if (shot.origin().distanceToSqr(shot.end()) < 1e-12 || !visible(marker.owner, shot.origin(), shot.end())) continue;
                    int alpha = 240 * (DualPointGeometry.FLASH_TICKS - flash.age) / DualPointGeometry.FLASH_TICKS;
                    // Native source is immutable even in first person. The two real hand origins already give nonzero projected area.
                    line(WHITE, matrix, marker.owner, shot.origin(), shot.end(), camera, look, up, DualPointGeometry.EDGE_WIDTH, DualPointGeometry.EDGE, alpha, budget);
                    for (var part : DualPointGeometry.beam(point(shot.origin()), point(shot.end())))
                        line(SMALL_RAY[part.texture()], matrix, marker.owner, vec(part.segment().start()), vec(part.segment().end()), camera, look, up,
                                DualPointGeometry.EDGE_WIDTH * .78, 0xFFFFFF, alpha, budget);
                    line(WHITE, matrix, marker.owner, shot.origin(), shot.end(), camera, look, up, DualPointGeometry.CORE_WIDTH, DualPointGeometry.CORE, alpha, budget);
                    boolean hit = shot.status() == DualPointState.DAMAGED || shot.status() == DualPointState.ABSORBED;
                    if (visible(marker.owner, camera, shot.end())) for (var spark : DualPointGeometry.impact(point(shot.end()), point(shot.end().subtract(shot.origin())), flash.age, hit))
                        line(WHITE, matrix, marker.owner, vec(spark.start()), vec(spark.end()), camera, look, up, .003, DualPointGeometry.CORE, alpha, budget);
                }
                var endpoint = marker.mark.end();
                if (endpoint != null && mc.screen == null && !mc.options.hideGui) {
                    Vec3 nativeOrigin = visibleMarkOrigin(marker, camera, endpoint);
                    if (nativeOrigin != null) {
                        Vec3 relative = vec(endpoint).subtract(camera);
                        Vector4f clip = projection.transform(new Vector4f((float)relative.x, (float)relative.y, (float)relative.z, 1));
                        var screen = DualPointGeometry.projectMark(clip.x, clip.y, clip.z, clip.w, nativeOrigin.distanceToSqr(vec(endpoint)), guiWidth, guiHeight, true);
                        // Two rectangles per corner stroke and two glyph layers (shadow + text) share the frame's existing 32-unit ceiling.
                        int cost = 16 + label.codePointCount(0, label.length()) * 2;
                        if (screen != null && budget.reserve(cost)) projected.add(new Preview(marker, marker.state, endpoint, screen, camera, label,
                                guiWidth, guiHeight, net.minecraft.Util.getMillis(), generation));
                    }
                }
            }
            previews = List.copyOf(projected);
        } finally {
            try { buffers.endBatch(); } finally { modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); }
        }
    }
    /** Read-only preparatory projection. Entity depth cannot bury it; strict block LOS still suppresses hidden endpoints. */
    @SubscribeEvent public static void preview(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        List<Preview> frame = previews; previews = List.of(); // A projection is consumed once, never reused by a later GUI frame.
        if (mc.player == null || world == null || mc.screen != null || mc.options.hideGui || !mc.isWindowActive()) return;
        GuiGraphics graphics = event.getGuiGraphics(); long now = net.minecraft.Util.getMillis();
        for (Preview preview : frame) {
            Marker marker = preview.marker;
            if (preview.generation != generation || now < preview.at || now - preview.at > 100
                    || preview.width != graphics.guiWidth() || preview.height != graphics.guiHeight()
                    || marker.state != preview.receipt || !validOwner(marker, mc) || !preview.endpoint.equals(marker.mark.end())
                    || visibleMarkOrigin(marker, preview.camera, preview.endpoint) == null) continue;
            for (var stroke : DualPointGeometry.screenMark(preview.screen)) {
                graphics.fill(stroke.x1() - 1, stroke.y1() - 1, stroke.x2() + 1, stroke.y2() + 1, 0xE0176B35);
                graphics.fill(stroke.x1(), stroke.y1(), stroke.x2(), stroke.y2(), 0xE0A3DCAC);
            }
            int x = (int)Math.round(preview.screen.x()), y = (int)Math.round(preview.screen.y());
            graphics.drawString(mc.font, preview.label, x - mc.font.width(preview.label) / 2, y - 21, 0xFFA3DCAC, true);
        }
    }
    private static void line(ResourceLocation texture, Matrix4f matrix, Entity owner, Vec3 from, Vec3 to, Vec3 camera, Vec3 look, Vec3 up,
                             double width, int rgb, int alpha, DualPointGeometry.Budget budget) {
        if (alpha <= 0 || !visible(owner, from, to)) return;
        var clipped = DualPointGeometry.nearClip(point(from.subtract(camera)), point(to.subtract(camera)), point(look), width);
        VertexConsumer out = buffers.getBuffer(ClassicRenderTypes.world(texture));
        for (var segment : DualPointGeometry.clearCenter(clipped, point(look), point(up), width)) {
            var ribbon = DualPointGeometry.ribbon(segment, point(look), width); if (ribbon == null || !budget.take()) continue;
            Vec3 a = vec(ribbon.start()), b = vec(ribbon.end()), side = vec(ribbon.side());
            vertex(out, matrix, a.add(side), 0, 0, rgb, alpha); vertex(out, matrix, a.subtract(side), 0, 1, rgb, alpha);
            vertex(out, matrix, b.subtract(side), 1, 1, rgb, alpha); vertex(out, matrix, b.add(side), 1, 0, rgb, alpha);
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 p, float u, float v, int rgb, int alpha) {
        out.addVertex(matrix, (float)p.x, (float)p.y, (float)p.z).setUv(u, v).setColor((rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255, alpha);
    }
    private static DualPointGeometry.Point point(Vec3 p) { return new DualPointGeometry.Point(p.x, p.y, p.z); }
    private static Vec3 vec(DualPointGeometry.Point p) { return new Vec3(p.x(), p.y(), p.z()); }

    /** Read-only snapshot: points = origin, end; numbers = status, remaining ticks. */
    private static double[] dp(net.minecraft.world.phys.Vec3 p) { return p == null ? DecorSnap.NONE : new double[] {p.x, p.y, p.z}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        for (Marker m : MARKERS.values()) { if (!validOwner(m, mc) || m.state == null) continue; var s = m.state;
            out.add(DecorSnap.of(m.owner == mc.player, 0, s.phase(), s.token(), java.util.List.of(dp(s.origin()), dp(s.end())), s.status(), s.remainingTicks())); }
        return out;
    }
}

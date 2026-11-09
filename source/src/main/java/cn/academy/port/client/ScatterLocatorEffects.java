package cn.academy.port.client;

import cn.academy.port.ClassicHudConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.FoldedMirrorArray;
import cn.academy.port.highpower.ScatterLocatorPlate;
import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Plate;
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
import net.minecraft.client.renderer.RenderType;
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

/** NEW223 has one fixed plate and planning paths only. It has no attack, flash, particle or hit-cue API. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ScatterLocatorEffects {
    private static final ResourceLocation WHITE = texture("port/white"), SHIELD = texture("effects/mdshield");
    private static final ScatterLocatorState STATE = new ScatterLocatorState();
    private static final Map<UUID, Marker> MARKERS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static MultiBufferSource.BufferSource buffers;
    private static ScatterLocatorState.Status terminalStatus;
    private static int statusTicks;
    private static final class Marker {
        final Entity owner;
        final ScatterLocatorState.Lease lease = new ScatterLocatorState.Lease();
        ScatterLocatorState.Frame frame;
        Marker(Entity owner, ScatterLocatorState.Frame frame) { this.owner = owner; update(frame); }
        void update(ScatterLocatorState.Frame next) { frame = next; lease.received(next); }
    }
    private ScatterLocatorEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { ScatterLocatorPlate.clientReceiver = ScatterLocatorEffects::receive; }
    }
    private static ScatterLocatorState.Frame frame(ScatterLocatorPlate.Visual p) {
        List<Plate> plates = new ArrayList<>(p.plates().size());
        for (int i = 0; i < p.plates().size(); i++) {
            var plate = p.plates().get(i); plates.add(new Plate(i, point(plate.center()), point(plate.normal())));
        }
        return new ScatterLocatorState.Frame(new FoldedMirrorState.Identity(p.owner(), p.ownerUuid(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), p.status(), p.remainingTicks(), point(p.ownerFeet()), List.copyOf(plates),
                point(p.ghostCenter()), point(p.ghostNormal()),
                p.path().stream().map(s -> new FoldedMirrorState.Segment(point(s.start()), point(s.end()))).toList(), p.heldCp());
    }
    public static void receive(ScatterLocatorPlate.Visual packet) {
        if (packet == null || packet.ownerFeet() == null || packet.ghostCenter() == null || packet.ghostNormal() == null
                || packet.plates() == null || packet.plates().size() > ScatterLocatorState.MAX_PLATES
                || packet.path() == null || packet.path().size() > ScatterLocatorState.MAX_SEGMENTS
                || packet.plates().stream().anyMatch(p -> p == null || p.center() == null || p.normal() == null)
                || packet.path().stream().anyMatch(s -> s == null || s.start() == null || s.end() == null)) return;
        final ScatterLocatorState.Frame receipt;
        try { receipt = frame(packet); } catch (IllegalArgumentException malformedGeometry) { return; }
        if (!ScatterLocatorState.valid(receipt)) return;
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
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch()) || !AcademyClient.scatterLocatorInputMatches(packet.input(), packet.epoch()))) return;
            if (packet.phase() != ScatterLocatorState.ENDED && (!owner.isAlive() || owner.isRemoved() || local && !localEligible())) return;
            if (!STATE.accept(receipt, local)) return;
            if (local) AcademyClient.scatterLocatorAcceptedInput(packet.input(), packet.epoch());
            if (packet.phase() == ScatterLocatorState.ENDED) {
                MARKERS.remove(packet.ownerUuid());
                if (local) {
                    terminalStatus = ScatterLocatorState.status(receipt, 0, ScatterLocatorState.QUIET_TICKS); statusTicks = 25;
                    AcademyClient.scatterLocatorEndedInput(packet.input(), packet.epoch());
                }
                return;
            }
            if (local) { terminalStatus = null; statusTicks = 0; }
            if (old == null || old.frame.id().token() != receipt.id().token()) MARKERS.put(packet.ownerUuid(), new Marker(owner, receipt));
            else old.update(receipt);
        });
    }
    public static void abortLocal(long input, long epoch) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); STATE.cancel(epoch, input);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        if (marker != null && marker.frame.id().epoch() == epoch && marker.frame.id().input() <= input) {
            STATE.retire(marker.frame.id()); MARKERS.remove(mc.player.getUUID());
        }
        terminalStatus = null; statusTicks = 0;
    }
    public static void clear() { generation++; STATE.clear(); MARKERS.clear(); terminalStatus = null; statusTicks = 0; }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear();
        if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    static boolean localEligible() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && mc.isWindowActive() && mc.getCameraEntity() == mc.player && stateEligible();
    }
    /** Eligibility is independent of the currently pressed bound slot, including the borrowing NEW221 slot. */
    static boolean stateEligible() {
        Minecraft mc = Minecraft.getInstance(); var state = AcademyClient.state;
        return mc.player != null && !mc.player.isSpectator() && state != null && state.activated && state.overloadFine && !state.interfering
                && "meltdowner".equals(state.category) && ScatterLocatorState.eligible(state.level, state.learned(FoldedMirrorArray.ID), state.learned(ScatterLocatorPlate.ID))
                && state.presets.currentContains(ScatterLocatorPlate.ID) && SkillCatalog.enabled("meltdowner", ScatterLocatorPlate.ID);
    }
    private static boolean validOwner(Marker marker, Minecraft mc) {
        return world != null && marker.owner.isAlive() && !marker.owner.isRemoved() && world.getEntity(marker.owner.getId()) == marker.owner
                && (marker.owner != mc.player || localEligible() && AcademyClient.singleOwnerEpochMatches(marker.frame.id().epoch())
                && AcademyClient.scatterLocatorInputMatches(marker.frame.id().input(), marker.frame.id().epoch()));
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        if (marker == null || !validOwner(marker, mc) || !marker.lease.visible()) return ClassicAbilityHud.DelegateVisual.IDLE;
        return marker.frame.phase() == ScatterLocatorState.EDIT ? ClassicAbilityHud.DelegateVisual.CHARGE : ClassicAbilityHud.DelegateVisual.ACTIVE;
    }
    public static boolean canRecallLocal() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        return marker != null && validOwner(marker, mc) && !AcademyClient.state.consumptionInProgress()
                && ScatterLocatorState.recallAvailable(marker.frame, marker.lease.remaining(), marker.lease.quiet());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (!localEligible()) { terminalStatus = null; statusTicks = 0; }
        if (!mc.isPaused() && statusTicks > 0 && --statusTicks == 0) terminalStatus = null;
        Iterator<Marker> iterator = MARKERS.values().iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next();
            if (!marker.lease.tick(mc.isPaused(), validOwner(marker, mc))) {
                STATE.retire(marker.frame.id()); iterator.remove();
                if (marker.owner == mc.player) AcademyClient.scatterLocatorEndedInput(marker.frame.id().input(), marker.frame.id().epoch());
            }
        }
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (mc.player == null || mc.isPaused() || mc.options.hideGui || !localEligible() || mc.getDebugOverlay().showDebugScreen()) return;
        Marker marker = MARKERS.get(mc.player.getUUID());
        ScatterLocatorState.Status status = marker != null && validOwner(marker, mc) && marker.lease.visible()
                ? ScatterLocatorState.status(marker.frame, marker.lease.remaining(), marker.lease.quiet()) : terminalStatus;
        if (status == null) return;
        String held = ScatterLocatorState.heldText(status.heldCp());
        String current = status.seconds() < 0 ? hudText("status_unpaid", hudText("brief." + status.key()))
                : hudText("status", status.seconds(), held, hudText("brief." + status.key()), status.recallable() ? hudText("recallable") : "");
        String help = status.key().equals("ended") ? "" : hudText(status.key().equals("adjusting") ? "help_edit" : "help");
        var graphics = event.getGuiGraphics();
        var layout = RemoteScatterRelayHudLayout.fit(graphics.guiWidth(), graphics.guiHeight(), mc.font.lineHeight,
                new RemoteScatterRelayHudLayout.Content(current, help, "", ""),
                reservedHudBoxes(graphics.guiWidth(), graphics.guiHeight()),
                (text, width) -> mc.font.split(Component.literal(text), width), mc.font::width);
        if (layout == null) return;
        var box = layout.bounds();
        graphics.fill(box.x(), box.y(), box.x() + box.width(), box.y() + box.height(), 0x82070D0A);
        graphics.fill(box.x(), box.y(), box.x() + 2, box.y() + box.height(), 0xB85D9971);
        for (var line : layout.lines()) graphics.drawString(mc.font, line.text(), line.x(), line.y(),
                line.kind() == RemoteScatterRelayHudLayout.Kind.HELP ? 0xFFB4C4BB : 0xFFE2F4E8, true);
    }
    private static String hudText(String key, Object... args) { return Component.translatable("academy.highpower.scatter_locator_plate." + key, args).getString(); }
    private static List<HighPowerRecoveryHudModel.Box> reservedHudBoxes(int width, int height) {
        boolean config = ClassicHudConfig.SPEC.isLoaded();
        var cp = ClassicHudTimeline.cpOrigin(width, config ? ClassicHudConfig.CP_X.get() : ClassicHudTimeline.CP_X,
                config ? ClassicHudConfig.CP_Y.get() : ClassicHudTimeline.CP_Y);
        var keys = ClassicHudTimeline.keyOrigin(width, height, config ? ClassicHudConfig.KEY_X.get() : 0,
                config ? ClassicHudConfig.KEY_Y.get() : 30);
        var boxes = new ArrayList<HighPowerRecoveryHudModel.Box>();
        boxes.add(new HighPowerRecoveryHudModel.Box((int)Math.floor(cp.x()), (int)Math.floor(cp.y()), 194, 40));
        boxes.add(new HighPowerRecoveryHudModel.Box((int)keys.x() - 155, (int)keys.y(), 185, 88));
        boxes.add(new HighPowerRecoveryHudModel.Box((int)Math.floor(config ? ClassicHudConfig.NOTIFICATION_X.get() : 0),
                (int)Math.floor(config ? ClassicHudConfig.NOTIFICATION_Y.get() : 15), 130, 43));
        var chat = Minecraft.getInstance().gui.getChat();
        int chatHeight = (int)Math.ceil(chat.getHeight() * chat.getScale());
        boxes.add(new HighPowerRecoveryHudModel.Box(0, height - 40 - chatHeight,
                Math.min(width, chat.getWidth() + 16), chatHeight + 12));
        var recovery = HighPowerRecoveryHud.relayReservedBox(width, height);
        // The same pure layout is usable before a resource snapshot arrives; a missing slot needs no reservation.
        if (recovery != null) boxes.add(recovery);
        return List.copyOf(boxes);
    }

    private static boolean loadedPath(Vector from, Vector to) {
        if (world == null) return false;
        var chunks = BeamEtchingMesh.crossedChunks(new BeamEtchingMesh.Point(from.x(), from.y(), from.z()), new BeamEtchingMesh.Point(to.x(), to.y(), to.z()));
        if (chunks.isEmpty()) return false;
        for (var chunk : chunks) if (!world.hasChunkAt(new BlockPos(chunk.x() * 16, (int)Math.floor(from.y()), chunk.z() * 16))) return false;
        return true;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (world == null || MARKERS.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        List<Marker> visible = MARKERS.values().stream().filter(m -> validOwner(m, mc) && m.lease.visible() && m.owner.distanceToSqr(camera) <= 96 * 96)
                .sorted(Comparator.comparingInt((Marker m) -> m.owner == mc.player ? 0 : 1)
                        .thenComparingDouble(m -> m.owner.distanceToSqr(camera)).thenComparing(m -> m.owner.getUUID()))
                .limit(ScatterLocatorMesh.MAX_VISIBLE_CONTEXTS).toList();
        if (visible.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(262144));
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            var facing = event.getCamera().getLookVector();
            Vector look = FoldedMirrorGeometry.unit(new Vector(facing.x(), facing.y(), facing.z())).orElse(null), eye = point(camera);
            if (look == null) return;
            boolean enhanced = FoldedMirrorMaterial.available() && !Boolean.getBoolean("academy.highpower.scatterLocatorForceFallback");
            var scene = new ArrayList<FoldedMirrorMesh.Face>(); var budget = new ScatterLocatorMesh.Budget();
            float phase = world.getGameTime() % 2400 + (mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false));
            for (Marker marker : visible) {
                var group = new ArrayList<FoldedMirrorMesh.Face>();
                Vector center = ScatterLocatorState.hasGhost(marker.frame) ? marker.frame.ghostCenter()
                        : marker.frame.plates().isEmpty() ? null : marker.frame.plates().getFirst().center();
                if (center != null && world.hasChunkAt(BlockPos.containing(vec(center)))) {
                    group.addAll(ScatterLocatorMesh.plate(marker.frame, phase));
                    group.addAll(ScatterLocatorMesh.arrow(marker.frame, eye, look));
                }
                if (marker.frame.path().stream().allMatch(segment -> loadedPath(segment.start(), segment.end())))
                    group.addAll(ScatterLocatorMesh.path(marker.frame, eye, look));
                var clipped = ScatterLocatorMesh.clipped(group, eye, look);
                if (budget.take(clipped.size())) scene.addAll(clipped);
            }
            drawOrdered(event.getPoseStack().last().pose(), ScatterLocatorMesh.ordered(scene, eye), eye, enhanced);
        } finally {
            try { buffers.endBatch(); } finally {
                modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            }
        }
    }
    private static void drawOrdered(Matrix4f matrix, List<FoldedMirrorMesh.Face> faces, Vector eye, boolean enhanced) {
        RenderType activeType = null; int activeLayer = -1, activeKind = -1;
        FoldedMirrorMesh.Material activeMaterial = null;
        for (var face : faces) {
            ResourceLocation texture = face.texture() == FoldedMirrorMesh.SHIELD_TEXTURE ? SHIELD : WHITE;
            RenderType type = RemoteScatterRelayRenderTypes.type(texture, FoldedMirrorMesh.additive(face), FoldedMirrorMesh.culled(face), enhanced);
            int layer = FoldedMirrorMesh.layer(face);
            boolean changed = activeKind != face.kind() || !face.material().equals(activeMaterial);
            if (activeType != null && (activeType != type || activeLayer != layer || changed)) buffers.endBatch(activeType);
            if (enhanced && (activeType != type || activeLayer != layer || changed)) FoldedMirrorMaterial.set(face);
            activeType = type; activeLayer = layer; activeKind = face.kind(); activeMaterial = face.material();
            VertexConsumer out = buffers.getBuffer(type);
            vertex(out, matrix, face.a().subtract(eye), face.uvA(), face.color(), face.alpha());
            vertex(out, matrix, face.b().subtract(eye), face.uvB(), face.color(), face.alpha());
            vertex(out, matrix, face.c().subtract(eye), face.uvC(), face.color(), face.alpha());
            vertex(out, matrix, face.d().subtract(eye), face.uvD(), face.color(), face.alpha());
        }
        if (activeType != null) buffers.endBatch(activeType);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vector p, FoldedMirrorMesh.Uv uv, int rgb, int alpha) {
        out.addVertex(matrix, (float)p.x(), (float)p.y(), (float)p.z()).setUv((float)uv.u(), (float)uv.v()).setColor(rgb >>> 16 & 255, rgb >>> 8 & 255, rgb & 255, alpha);
    }
    private static Vector point(Vec3 point) { return new Vector(point.x, point.y, point.z); }
    private static Vec3 vec(Vector point) { return new Vec3(point.x(), point.y(), point.z()); }

    /** Read-only snapshot: points = feet, ghost centre, ghost normal, then (centre, normal) per plate; numbers = status, remaining, held CP; polyline = preview path. */
    private static double[] dv(cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        for (Marker m : MARKERS.values()) { if (!validOwner(m, mc) || m.frame == null) continue; var f = m.frame;
            var points = new java.util.ArrayList<double[]>(java.util.List.of(dv(f.ownerFeet()), dv(f.ghostCenter()), dv(f.ghostNormal())));
            if (f.plates() != null) for (var plate : f.plates()) if (points.size() < 21) { points.add(dv(plate.center())); points.add(dv(plate.normal())); }
            var line = new java.util.ArrayList<double[]>();
            if (f.path() != null) for (var seg : f.path()) { if (line.isEmpty()) line.add(dv(seg.start())); if (line.size() < 24) line.add(dv(seg.end())); }
            out.add(new DecorSnap(m.owner == mc.player, 0, f.phase(), f.id().token(), points, new double[] {f.status(), f.remainingTicks(), f.heldCp()}, line.isEmpty() ? java.util.List.of() : java.util.List.of(line)));
        }
        return out;
    }
}

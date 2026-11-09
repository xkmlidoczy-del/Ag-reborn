package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.DualPoint;
import cn.academy.port.highpower.FoldedMirrorArray;
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
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW221 stationary world-space plates. Authoritative FIRE receipts alone create brief solid rays. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class FoldedMirrorEffects {
    private static final ResourceLocation WHITE = texture("port/white");
    private static final ResourceLocation SHIELD = texture("effects/mdshield");
    private static final ResourceLocation[] SMALL_RAY = {texture("effects/mdray_small/blend_in"), texture("effects/mdray_small/tile"), texture("effects/mdray_small/blend_out")};
    private static final FoldedMirrorState STATE = new FoldedMirrorState();
    private static final Map<UUID, Marker> MARKERS = new LinkedHashMap<>();
    private static final FoldedMirrorShotTail SHOT_TAILS = new FoldedMirrorShotTail();
    private static final Map<UUID, Entity> TAIL_OWNERS = new LinkedHashMap<>();
    private static volatile long tailGeneration;
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static MultiBufferSource.BufferSource buffers;
    private static FoldedMirrorState.Status terminalStatus;
    private static int statusTicks;
    private static final class Flash {
        final FoldedMirrorState.Frame frame;
        int age;
        Flash(FoldedMirrorState.Frame frame) { this.frame = frame; }
    }
    private static final class Marker {
        final Entity owner;
        final FoldedMirrorState.Lease lease = new FoldedMirrorState.Lease();
        final List<Flash> flashes = new ArrayList<>(2);
        FoldedMirrorState.Frame frame;
        Marker(Entity owner, FoldedMirrorState.Frame frame) { this.owner = owner; update(frame); }
        void update(FoldedMirrorState.Frame next) {
            frame = next; lease.received(next);
            if (next.phase() == FoldedMirrorState.FIRE && !next.path().isEmpty()) {
                if (flashes.size() == 2) flashes.removeFirst();
                flashes.add(new Flash(next));
            }
        }
    }
    private FoldedMirrorEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void reload(RegisterShadersEvent event) { tailGeneration++; SHOT_TAILS.clear(); TAIL_OWNERS.clear(); }
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { FoldedMirrorArray.clientReceiver = FoldedMirrorEffects::receive; }
    }
    private static FoldedMirrorState.Frame frame(FoldedMirrorArray.Visual p) {
        List<Plate> plates = new ArrayList<>(p.plates().size());
        for (int i = 0; i < p.plates().size(); i++) {
            var plate = p.plates().get(i); plates.add(new Plate(i, point(plate.center()), point(plate.normal())));
        }
        return new FoldedMirrorState.Frame(new FoldedMirrorState.Identity(p.owner(), p.ownerUuid(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), p.status(), p.remainingTicks(), point(p.ownerFeet()), List.copyOf(plates),
                point(p.ghostCenter()), point(p.ghostNormal()), p.path().stream().map(s -> new FoldedMirrorState.Segment(point(s.start()), point(s.end()))).toList());
    }
    public static void receive(FoldedMirrorArray.Visual packet) {
        if (packet == null || packet.ownerFeet() == null || packet.ghostCenter() == null || packet.ghostNormal() == null
                || packet.plates() == null || packet.plates().size() > 2 || packet.path() == null || packet.path().size() > 3
                || packet.plates().stream().anyMatch(p -> p == null || p.center() == null || p.normal() == null)
                || packet.path().stream().anyMatch(s -> s == null || s.start() == null || s.end() == null)) return;
        final FoldedMirrorState.Frame receipt;
        try { receipt = frame(packet); } catch (IllegalArgumentException malformedGeometry) { return; }
        if (!FoldedMirrorState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation, capturedTailGeneration = tailGeneration;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.owner());
            if (owner == null || !owner.getUUID().equals(packet.ownerUuid())) return;
            Marker old = MARKERS.get(packet.ownerUuid());
            if (old != null && old.frame.id().token() == receipt.id().token() && old.owner != owner) return;
            boolean local = owner == mc.player;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch()) || !AcademyClient.foldedMirrorInputMatches(packet.input(), packet.epoch()))) return;
            if (packet.phase() != FoldedMirrorState.ENDED && (!owner.isAlive() || owner.isRemoved() || local && !localEligible())) return;
            if (!STATE.accept(receipt, local)) return;
            FoldedMirrorMaterialPreview.accepted(receipt, local);
            if (local) AcademyClient.foldedMirrorAcceptedInput(packet.input(), packet.epoch());
            if (packet.phase() == FoldedMirrorState.ENDED) {
                MARKERS.remove(packet.ownerUuid());
                if (local) {
                    // An invalid first placement is immediately retired; retain its real refusal cue long enough to read.
                    boolean emptyRefusal = old != null && old.frame.plates().isEmpty()
                            && (old.frame.status() == FoldedMirrorState.REFUSED || old.frame.status() == FoldedMirrorState.DEGENERATE);
                    terminalStatus = FoldedMirrorState.status(emptyRefusal ? old.frame : receipt, 0); statusTicks = 30;
                    AcademyClient.foldedMirrorEndedInput(packet.input(), packet.epoch());
                }
                return;
            }
            if (local) { terminalStatus = null; statusTicks = 0; }
            if (receipt.phase() == FoldedMirrorState.FIRE && receipt.remainingTicks() == 0) {
                if (capturedTailGeneration == tailGeneration && SHOT_TAILS.accepted(receipt)) TAIL_OWNERS.put(packet.ownerUuid(), owner);
                // A reload-fenced borrowed receipt must never fall through into the ordinary live marker.
                return;
            }
            if (old == null || old.frame.id().token() != receipt.id().token()) {
                if (old == null && MARKERS.size() >= FoldedMirrorState.MAX_CONTEXTS) { STATE.retire(receipt.id()); return; }
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
        if (mc.player != null) { SHOT_TAILS.cancel(mc.player.getUUID(), epoch, input);
            if (SHOT_TAILS.current(mc.player.getUUID()) == null) TAIL_OWNERS.remove(mc.player.getUUID()); }
        terminalStatus = null; statusTicks = 0;
    }
    public static void clear() { generation++; tailGeneration++; SHOT_TAILS.clear(); TAIL_OWNERS.clear(); STATE.clear(); MARKERS.clear(); terminalStatus = null; statusTicks = 0; FoldedMirrorMaterialPreview.clear(); }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear();
        // Same-connection player/world replacement retains replay tombstones.
        if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    static boolean localEligible() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && mc.isWindowActive() && mc.getCameraEntity() == mc.player
                && materialPreviewStateEligible();
    }
    /** Shared eligibility without temporary UI/camera suppression; no ability state is changed. */
    static boolean materialPreviewStateEligible() {
        Minecraft mc = Minecraft.getInstance(); var state = AcademyClient.state;
        return mc.player != null && !mc.player.isSpectator() && state != null && state.activated && state.overloadFine && !state.interfering
                && "meltdowner".equals(state.category) && FoldedMirrorState.eligible(state.level, state.learned(DualPoint.ID), state.learned(FoldedMirrorArray.ID))
                && state.presets.currentContains(FoldedMirrorArray.ID) && SkillCatalog.enabled("meltdowner", FoldedMirrorArray.ID);
    }
    private static boolean validOwner(Marker marker, Minecraft mc) {
        return world != null && marker.owner.isAlive() && !marker.owner.isRemoved() && world.getEntity(marker.owner.getId()) == marker.owner
                && (marker.owner != mc.player || localEligible() && AcademyClient.singleOwnerEpochMatches(marker.frame.id().epoch())
                && AcademyClient.foldedMirrorInputMatches(marker.frame.id().input(), marker.frame.id().epoch()));
    }
    private static boolean validTail(FoldedMirrorState.Frame frame, Minecraft mc) {
        Entity owner = TAIL_OWNERS.get(frame.id().owner());
        return world != null && owner != null && owner.isAlive() && !owner.isRemoved()
                && world.getEntity(frame.id().entity()) == owner && owner.getUUID().equals(frame.id().owner())
                && (owner != mc.player || localEligible() && AcademyClient.singleOwnerEpochMatches(frame.id().epoch()));
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        if (marker == null || !validOwner(marker, mc)) return ClassicAbilityHud.DelegateVisual.IDLE;
        return marker.frame.phase() == FoldedMirrorState.EDIT ? ClassicAbilityHud.DelegateVisual.CHARGE : ClassicAbilityHud.DelegateVisual.ACTIVE;
    }
    public static boolean activeLocal() { return localVisual() != ClassicAbilityHud.DelegateVisual.IDLE; }
    /** Cooldown affects paid shots and edits, not the free recall of an already paid local array. */
    public static boolean canRecallLocal() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        return marker != null && validOwner(marker, mc) && !AcademyClient.state.consumptionInProgress()
                && FoldedMirrorState.recallAvailable(marker.frame, marker.lease.remaining(), marker.lease.quiet());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (!mc.isPaused() && statusTicks > 0 && --statusTicks == 0) terminalStatus = null;
        SHOT_TAILS.tick(mc.isPaused(), frame -> validTail(frame, mc));
        TAIL_OWNERS.keySet().removeIf(owner -> SHOT_TAILS.current(owner) == null);
        Iterator<Marker> iterator = MARKERS.values().iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next();
            if (!mc.isPaused()) marker.flashes.removeIf(flash -> ++flash.age >= FoldedMirrorState.FLASH_TICKS);
            if (!marker.lease.tick(mc.isPaused(), validOwner(marker, mc))) {
                STATE.retire(marker.frame.id()); iterator.remove();
                if (marker.owner == mc.player) AcademyClient.foldedMirrorEndedInput(marker.frame.id().input(), marker.frame.id().epoch());
            }
        }
    }
    /** One unobtrusive receipt-only line below the classic CP bar; it never covers the reticle or claims preview hits. */
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (mc.player == null || mc.options.hideGui || !localEligible() || FoldedMirrorMaterialPreview.activeFrame() != null) return;
        Marker marker = MARKERS.get(mc.player.getUUID());
        FoldedMirrorState.Status status = marker != null && validOwner(marker, mc)
                ? FoldedMirrorState.status(marker.frame, marker.lease.remaining()) : terminalStatus;
        if (status == null) return;
        Component stateText = Component.translatable("academy.highpower.folded_mirror_array." + status.key());
        Component text = status.seconds() < 0
                ? Component.translatable("academy.highpower.folded_mirror_array.status_unpaid", status.count(), stateText)
                : Component.translatable("academy.highpower.folded_mirror_array.status", status.count(), status.seconds(), stateText);
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
    private static boolean loadedFrame(FoldedMirrorState.Frame frame) {
        for (var segment : frame.path()) if (!loadedPath(segment.start(), segment.end())) return false;
        return true;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        FoldedMirrorState.Frame materialPreview = FoldedMirrorMaterialPreview.activeFrame();
        if (world == null || MARKERS.isEmpty() && SHOT_TAILS.empty() && materialPreview == null) return;
        if (materialPreview != null && FoldedMirrorMaterialPreview.baselineSelected()) {
            FoldedMirrorBaselinePreviewRenderer.render(event, world, materialPreview, FoldedMirrorMaterialPreview.phaseAge());
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        List<Marker> visible = materialPreview != null ? List.of() : MARKERS.values().stream().filter(m -> validOwner(m, mc) && m.owner.distanceToSqr(camera) <= 64 * 64)
                .sorted(Comparator.comparingInt(m -> m.owner == mc.player ? 0 : 1)).limit(FoldedMirrorMesh.MAX_VISIBLE_CONTEXTS).toList();
        List<FoldedMirrorShotTail.Shot> tails = materialPreview != null ? List.of() : SHOT_TAILS.shots().stream()
                .filter(shot -> validTail(shot.frame(), mc) && TAIL_OWNERS.get(shot.frame().id().owner()).distanceToSqr(camera) <= 64 * 64)
                .sorted(Comparator.comparingInt(shot -> TAIL_OWNERS.get(shot.frame().id().owner()) == mc.player ? 0 : 1))
                .limit(Math.max(0, FoldedMirrorMesh.MAX_VISIBLE_CONTEXTS - visible.size())).toList();
        if (visible.isEmpty() && tails.isEmpty() && materialPreview == null) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(262144));
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = event.getPoseStack().last().pose(); var facing = event.getCamera().getLookVector();
            Vector look = FoldedMirrorGeometry.unit(new Vector(facing.x(), facing.y(), facing.z())).orElse(null), eye = point(camera);
            var budget = new FoldedMirrorMesh.Budget();
            List<FoldedMirrorMesh.Face> faces = new ArrayList<>();
            float partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
            // The staged fixture has no wall-clock input, including the standing mirror's material.
            float mirrorPhase = materialPreview != null ? FoldedMirrorMaterialPreview.phaseAge() : (world.getGameTime() % 2400) + partial;
            // Same production mesh, ordering and materials; the opt-in fixture substitutes one frozen receipt.
            if (materialPreview != null) {
                for (Plate plate : materialPreview.plates()) if (world.hasChunkAt(BlockPos.containing(vec(plate.center()))))
                    faces.addAll(FoldedMirrorMesh.plate(plate, false, mirrorPhase, contact(materialPreview, plate),
                            1 - FoldedMirrorMaterialPreview.phaseAge() / (float)FoldedMirrorState.FLASH_TICKS));
                if (loadedFrame(materialPreview))
                    faces.addAll(FoldedMirrorMesh.path(materialPreview, eye, look, FoldedMirrorMaterialPreview.phaseAge()));
            }
            for (Marker marker : visible) {
                for (Plate plate : marker.frame.plates()) if (world.hasChunkAt(BlockPos.containing(vec(plate.center())))) {
                    Vector contact = null; float power = 0;
                    for (int i = marker.flashes.size() - 1; i >= 0; i--) {
                        Flash flash = marker.flashes.get(i); contact = contact(flash.frame, plate);
                        if (contact != null) { power = 1 - (flash.age + partial) / FoldedMirrorState.FLASH_TICKS; break; }
                    }
                    faces.addAll(FoldedMirrorMesh.plate(plate, false, mirrorPhase, contact, power));
                }
                if (FoldedMirrorState.hasGhost(marker.frame) && world.hasChunkAt(BlockPos.containing(vec(marker.frame.ghostCenter())))) {
                    Plate ghost = new Plate(0, marker.frame.ghostCenter(), marker.frame.ghostNormal());
                    faces.addAll(FoldedMirrorMesh.plate(ghost, true));
                }
                if (marker.frame.phase() != FoldedMirrorState.FIRE && loadedFrame(marker.frame))
                    faces.addAll(FoldedMirrorMesh.path(marker.frame, eye, look, 0));
                for (Flash flash : marker.flashes) if (loadedFrame(flash.frame))
                    faces.addAll(FoldedMirrorMesh.path(flash.frame, eye, look, flash.age + partial));
            }
            // The consumed locator plane is gone. Only the actual NEW221 path keeps its original short flash.
            for (var shot : tails) if (loadedFrame(shot.frame()))
                faces.addAll(FoldedMirrorMesh.path(shot.frame(), eye, look, shot.age() + partial));
            drawOrdered(matrix, FoldedMirrorMesh.ordered(faces, eye, look, budget), eye);
        } finally {
            try { buffers.endBatch(); } finally {
                modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            }
        }
    }
    /** Only a real two-leg reflection can energize a local region of the actual mirror face. */
    private static Vector contact(FoldedMirrorState.Frame frame, Plate plate) {
        if (frame == null || frame.phase() != FoldedMirrorState.FIRE) return null;
        for (int i = 0; i + 1 < frame.path().size(); i++) {
            Plate reflected = FoldedMirrorState.reflectionPlate(frame.plates(), frame.path().get(i), frame.path().get(i + 1));
            if (plate.equals(reflected)) return frame.path().get(i).end();
        }
        return null;
    }
    private static void drawOrdered(Matrix4f matrix, List<FoldedMirrorMesh.Face> faces, Vector eye) {
        RenderType activeType = null; int activeLayer = -1;
        int activeKind = -1, activeTexture = -2; FoldedMirrorMesh.Material activeMaterial = null;
        for (var face : faces) {
            ResourceLocation texture = face.texture() == FoldedMirrorMesh.SHIELD_TEXTURE ? SHIELD : face.texture() < 0 ? WHITE : SMALL_RAY[face.texture()];
            RenderType type = FoldedMirrorRenderTypes.type(texture, FoldedMirrorMesh.additive(face), FoldedMirrorMesh.culled(face));
            int layer = FoldedMirrorMesh.layer(face);
            // Immediate explicit flushes preserve alpha-surface -> halo -> core -> glint order across texture changes.
            boolean materialChanged = activeKind != face.kind() || activeTexture != face.texture() || !face.material().equals(activeMaterial);
            if (activeType != null && (activeType != type || activeLayer != layer || materialChanged)) buffers.endBatch(activeType);
            if (activeType != type || activeLayer != layer || materialChanged) FoldedMirrorMaterial.set(face);
            activeType = type; activeLayer = layer;
            activeKind = face.kind(); activeTexture = face.texture(); activeMaterial = face.material();
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

    /** Read-only snapshot: points = feet, ghost centre, ghost normal, then (centre, normal) per plate; numbers = status, remaining; polyline = fired path. */
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
            out.add(new DecorSnap(m.owner == mc.player, 0, f.phase(), f.id().token(), points, new double[] {f.status(), f.remainingTicks()}, line.isEmpty() ? java.util.List.of() : java.util.List.of(line)));
        }
        return out;
    }
}

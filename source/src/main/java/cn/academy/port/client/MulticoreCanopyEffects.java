package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.MulticoreCanopy;
import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.client.FoldedMirrorMesh.Face;
import cn.academy.port.client.FoldedMirrorMesh.Uv;
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
import net.minecraft.client.gui.Font;
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

/** NEW251 fixed numbered cores. No client entity lookup, particles, terrain mutation, or fabricated firing ray. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MulticoreCanopyEffects {
    private static final ResourceLocation WHITE = texture("port/white"), GLOW = texture("effects/mdball/glow");
    private static final ResourceLocation[] BALLS = {texture("effects/mdball/0"), texture("effects/mdball/1"),
            texture("effects/mdball/2"), texture("effects/mdball/3"), texture("effects/mdball/4")};
    private static final ResourceLocation[] SMALL_RAY = {texture("effects/mdray_small/blend_in"), texture("effects/mdray_small/tile"), texture("effects/mdray_small/blend_out")};
    private static final MulticoreCanopyState STATE = new MulticoreCanopyState();
    private static final Map<UUID, Marker> MARKERS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static MultiBufferSource.BufferSource buffers;
    private record SourceLabel(MulticoreCanopyState.Core core, int alpha) {}
    private static final class Flash {
        final MulticoreCanopyState.Frame frame;
        int age;
        Flash(MulticoreCanopyState.Frame frame) { this.frame = frame; }
    }
    private static final class Marker {
        final Entity owner;
        final MulticoreCanopyState.Lease lease = new MulticoreCanopyState.Lease();
        final MulticoreCanopyState.Visibility visibility = new MulticoreCanopyState.Visibility();
        final List<Flash> flashes = new ArrayList<>(2);
        MulticoreCanopyState.Frame frame;
        boolean terminal;
        int age;
        Marker(Entity owner, MulticoreCanopyState.Frame frame) { this.owner = owner; update(frame); }
        void update(MulticoreCanopyState.Frame next) {
            frame = next; lease.received(next); visibility.received(next);
            if (next.phase() == MulticoreCanopyState.FIRE && !next.path().isEmpty()) {
                if (flashes.size() == 2) flashes.removeFirst();
                flashes.add(new Flash(next));
            }
        }
    }
    private MulticoreCanopyEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { MulticoreCanopy.clientReceiver = MulticoreCanopyEffects::receive; }
    }
    private static MulticoreCanopyState.Frame frame(MulticoreCanopy.Visual p) {
        return new MulticoreCanopyState.Frame(new MulticoreCanopyState.Identity(p.owner(), p.ownerUuid(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), p.status(), p.remainingTicks(), point(p.ownerFeet()),
                p.cores().stream().map(c -> new MulticoreCanopyState.Core(c.id(), point(c.center()), c.available())).toList(),
                p.targets().stream().map(t -> new MulticoreCanopyState.Target(t.id(), point(t.point()), t.status())).toList(),
                p.path().stream().map(s -> new MulticoreCanopyState.Segment(s.coreId(), s.slot(), point(s.start()), point(s.end()), s.status())).toList(),
                p.held(), p.shotIndex(), p.warningTicks());
    }
    public static void receive(MulticoreCanopy.Visual packet) {
        if (packet == null || packet.ownerFeet() == null || packet.cores() == null || packet.cores().size() > 6
                || packet.targets() == null || packet.targets().size() > 16 || packet.path() == null || packet.path().size() > 6
                || packet.cores().stream().anyMatch(c -> c == null || c.center() == null)
                || packet.targets().stream().anyMatch(t -> t == null || t.point() == null)
                || packet.path().stream().anyMatch(s -> s == null || s.start() == null || s.end() == null)) return;
        final MulticoreCanopyState.Frame receipt;
        try { receipt = frame(packet); } catch (IllegalArgumentException malformed) { return; }
        if (!MulticoreCanopyState.valid(receipt)) return;
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
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.multicoreCanopyInputMatches(packet.input(), packet.epoch()))) return;
            if (packet.phase() != MulticoreCanopyState.ENDED && (!owner.isAlive() || owner.isRemoved() || local && !localEligible())) return;
            if (!STATE.accept(receipt, local)) return;
            if (local) AcademyClient.multicoreCanopyAcceptedInput(packet.input(), packet.epoch());
            if (packet.phase() == MulticoreCanopyState.ENDED) {
                // Preserve only already-emitted short flashes; terminal packets can never create fresh geometry.
                if (old != null && old.frame.id().token() == receipt.id().token() && !old.flashes.isEmpty()) old.terminal = true;
                else MARKERS.remove(packet.ownerUuid());
                if (local) AcademyClient.multicoreCanopyEndedInput(packet.input(), packet.epoch());
                return;
            }
            if (old == null || old.frame.id().token() != receipt.id().token()) {
                if (old == null && MARKERS.size() >= MulticoreCanopyState.MAX_CONTEXTS) { STATE.retire(receipt.id()); return; }
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
    }
    public static void clear() { generation++; STATE.clear(); MARKERS.clear(); }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear();
        if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    static boolean localEligible() {
        Minecraft mc = Minecraft.getInstance(); var state = AcademyClient.state;
        return mc.screen == null && mc.isWindowActive() && mc.getCameraEntity() == mc.player
                && mc.player != null && !mc.player.isSpectator() && state != null && state.activated && state.overloadFine && !state.interfering
                && "meltdowner".equals(state.category) && MulticoreCanopyState.eligible(state.level,
                state.learned(cn.academy.port.highpower.ParticleLaminate.ID), state.learned(MulticoreCanopy.ID))
                && state.presets.currentContains(MulticoreCanopy.ID) && SkillCatalog.enabled("meltdowner", MulticoreCanopy.ID);
    }
    private static boolean validOwner(Marker marker, Minecraft mc) {
        return world != null && marker.owner.isAlive() && !marker.owner.isRemoved() && world.getEntity(marker.owner.getId()) == marker.owner
                && (marker.owner != mc.player || localEligible() && AcademyClient.singleOwnerEpochMatches(marker.frame.id().epoch())
                && (marker.terminal || AcademyClient.multicoreCanopyInputMatches(marker.frame.id().input(), marker.frame.id().epoch())));
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        if (marker == null || marker.terminal || !validOwner(marker, mc)) return ClassicAbilityHud.DelegateVisual.IDLE;
        return marker.frame.phase() == MulticoreCanopyState.DEPLOY || marker.frame.phase() == MulticoreCanopyState.WARNING
                ? ClassicAbilityHud.DelegateVisual.CHARGE : ClassicAbilityHud.DelegateVisual.ACTIVE;
    }
    public static boolean activeLocal() { return localVisual() != ClassicAbilityHud.DelegateVisual.IDLE; }
    public static boolean canControlLocal() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        return marker != null && !marker.terminal && validOwner(marker, mc) && !AcademyClient.state.consumptionInProgress()
                && MulticoreCanopyState.controlAvailable(marker.frame, marker.lease.remaining(), marker.lease.quiet());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Iterator<Marker> iterator = MARKERS.values().iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next();
            if (!mc.isPaused()) { marker.age++; marker.flashes.removeIf(flash -> ++flash.age >= MulticoreCanopyState.FLASH_TICKS); }
            marker.visibility.tick(mc.isPaused());
            boolean valid = validOwner(marker, mc);
            if (!valid || (marker.terminal ? marker.flashes.isEmpty() : !marker.lease.tick(mc.isPaused(), valid))) {
                STATE.retire(marker.frame.id()); iterator.remove();
                if (!marker.terminal && marker.owner == mc.player) AcademyClient.multicoreCanopyEndedInput(marker.frame.id().input(), marker.frame.id().epoch());
            }
        }
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (mc.player == null || mc.options.hideGui || !localEligible()) return;
        Marker marker = MARKERS.get(mc.player.getUUID());
        if (marker == null || marker.terminal || !validOwner(marker, mc)) return;
        var status = MulticoreCanopyState.status(marker.frame, marker.lease.remaining());
        Component text = Component.translatable("academy.highpower.multicore_canopy.status", status.available(), status.count(), status.seconds(),
                Component.translatable("academy.highpower.multicore_canopy." + status.key()));
        var graphics = event.getGuiGraphics();
        graphics.fill(6, 68, 8, 78, 0xB8155D34);
        graphics.drawString(mc.font, text, 12, 69, 0xC4EAD1, true);
    }
    private static boolean loadedPath(Vector from, Vector to) {
        if (world == null) return false;
        var chunks = BeamEtchingMesh.crossedChunks(new BeamEtchingMesh.Point(from.x(), from.y(), from.z()), new BeamEtchingMesh.Point(to.x(), to.y(), to.z()));
        if (chunks.isEmpty()) return false;
        for (var chunk : chunks) if (!world.hasChunkAt(new BlockPos(chunk.x() * 16, (int)Math.floor(from.y()), chunk.z() * 16))) return false;
        return true;
    }
    private static boolean loaded(Vector point) { return world != null && world.hasChunkAt(BlockPos.containing(vec(point))); }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (world == null || MARKERS.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        List<Marker> visible = MARKERS.values().stream().filter(m -> validOwner(m, mc) && m.owner.distanceToSqr(camera) <= 96 * 96)
                .sorted(Comparator.comparingInt(m -> m.owner == mc.player ? 0 : 1)).limit(MulticoreCanopyMesh.MAX_VISIBLE_CONTEXTS).toList();
        if (visible.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(262144));
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = event.getPoseStack().last().pose(); var facing = event.getCamera().getLookVector();
            Vector look = FoldedMirrorGeometry.unit(new Vector(facing.x(), facing.y(), facing.z())).orElse(null), eye = point(camera);
            if (look == null) return;
            float partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
            var budget = new MulticoreCanopyMesh.Budget(); List<Face> faces = new ArrayList<>();
            List<SourceLabel> labels = new ArrayList<>();
            boolean enhanced = MulticoreCanopyCrownMaterial.enabled();
            int materialGeneration = MulticoreCanopyCrownMaterial.generation();
            List<List<Face>> ornaments = new ArrayList<>();
            for (Marker marker : visible) {
                List<Face> decoration = new ArrayList<>();
                if (!marker.terminal) {
                    for (var core : marker.frame.cores()) if (loaded(core.center())) {
                        float power = 0;
                        for (Flash flash : marker.flashes) power = Math.max(power, MulticoreCanopyMesh.emissionPower(flash.frame, core.id(), flash.age + partial));
                        faces.addAll(MulticoreCanopyMesh.core(core, eye, look, marker.age + partial, power));
                        if (core.available()) labels.add(new SourceLabel(core, 220));
                    }
                    for (var target : marker.frame.targets()) if (marker.visibility.shown(target) && loaded(target.point()))
                        faces.addAll(MulticoreCanopyMesh.target(target, eye, look));
                }
                Map<Integer, MulticoreCanopyMesh.SourceTail> tails = new LinkedHashMap<>();
                for (Flash flash : marker.flashes) if (flash.frame.path().stream().allMatch(s -> loadedPath(s.start(), s.end()))) {
                    faces.addAll(MulticoreCanopyMesh.path(flash.frame, eye, look, flash.age + partial));
                    if (enhanced) decoration.addAll(MulticoreCanopyEmissionGeometry.emission(flash.frame, eye, look, flash.age + partial));
                    if (marker.terminal) for (var tail : MulticoreCanopyMesh.terminalSources(flash.frame, flash.age + partial))
                        tails.merge(tail.core().id(), tail, (before, next) -> before.opacity() >= next.opacity() ? before : next);
                }
                // FIRE and ENDED may arrive before one render. Fade only accepted emitting sources and their numbers.
                // Deduplication prevents two retained receipts from doubling the same source's brightness.
                for (var tail : tails.values()) if (loaded(tail.core().center())) {
                    faces.addAll(MulticoreCanopyMesh.sourceTail(tail, eye, look, marker.age + partial));
                    labels.add(new SourceLabel(tail.core(), (int)(220 * tail.opacity())));
                }
                if (enhanced && !marker.terminal) {
                    var crown = MulticoreCanopyCrownGeometry.source(marker.frame, eye, look, marker.age + partial,
                            marker.lease.remaining(), marker.lease.quiet());
                    if (decoration.size() + crown.size() <= MulticoreCanopyCrownBudget.MAX_RAW_QUADS_PER_CONTEXT)
                        decoration.addAll(crown);
                }
                if (enhanced && !decoration.isEmpty() && decoration.stream().allMatch(face -> face.vertices().stream().allMatch(MulticoreCanopyEffects::loaded))) ornaments.add(List.copyOf(decoration));
            }
            List<Face> original = MulticoreCanopyMesh.ordered(faces, eye, look, budget);
            List<Face> finalFaces = enhanced && MulticoreCanopyCrownMaterial.enabled() && materialGeneration == MulticoreCanopyCrownMaterial.generation()
                    ? MulticoreCanopyCrownBudget.append(original, ornaments, eye, look, budget, labels.size() * 4) : original;
            drawOrdered(matrix, finalFaces, eye, ShaderPackState.active());
            // Native font uses ordinary depth testing, never SEE_THROUGH. Labels are the first feature omitted at the cap.
            for (SourceLabel source : labels) {
                var core = source.core();
                // Vanilla font treats alpha1–3 as unspecified/opaque; omit those final nearly invisible label samples.
                if (source.alpha() >= 4 && loaded(core.center()) && core.center().subtract(eye).dot(look) > .4 && budget.reserve(4)) {
                    // Compact labels sit below their own fixed cores, clear of the usual upper HUD band.
                    Vector at = core.center().add(new Vector(0, -.27, 0)).subtract(eye);
                    Matrix4f label = new Matrix4f(matrix).translate((float)at.x(), (float)at.y(), (float)at.z())
                            .rotate(event.getCamera().rotation()).scale(.011f, -.011f, .011f);
                    String text = Integer.toString(core.id() + 1);
                    mc.font.drawInBatch(text, -mc.font.width(text) / 2f, 0, source.alpha() << 24 | 0xEFFFDF, true, label, buffers, Font.DisplayMode.NORMAL, 0, 15728880);
                }
            }
            buffers.endBatch();
        } finally {
            try { buffers.endBatch(); } finally {
                modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            }
        }
    }
    private static ResourceLocation texture(Face face) {
        int id = face.texture();
        if (id < 0) return WHITE;
        if (id <= 2) return SMALL_RAY[id];
        if (id == MulticoreCanopyMesh.GLOW_TEXTURE) return GLOW;
        return BALLS[id - MulticoreCanopyMesh.ORB_TEXTURE];
    }
    private static void drawOrdered(Matrix4f matrix, List<Face> faces, Vector eye, boolean plainMaterial) {
        RenderType activeType = null; int activeLayer = -1, activeKind = -1, activeTexture = -2;
        FoldedMirrorMesh.Material activeMaterial = null;
        for (Face face : faces) {
            ResourceLocation texture = texture(face);
            boolean additive = MulticoreCanopyMesh.additive(face), cull = MulticoreCanopyMesh.culled(face);
            RenderType type = plainMaterial ? MulticoreCanopyIrisRenderTypes.type(texture, additive, cull)
                    : FoldedMirrorRenderTypes.type(texture, additive, cull);
            int layer = MulticoreCanopyMesh.layer(face);
            boolean changed = activeKind != face.kind() || activeTexture != face.texture() || !face.material().equals(activeMaterial);
            // This flush must happen before every kind/texture/uniform change, including every original orb animation frame.
            if (activeType != null && (activeType != type || activeLayer != layer || changed)) buffers.endBatch(activeType);
            if (!plainMaterial && (activeType != type || activeLayer != layer || changed))
                FoldedMirrorMaterial.set(MulticoreCanopyMesh.materialFace(face));
            activeType = type; activeLayer = layer; activeKind = face.kind(); activeTexture = face.texture(); activeMaterial = face.material();
            VertexConsumer out = buffers.getBuffer(type);
            vertex(out, matrix, face.a().subtract(eye), face.uvA(), face.color(), face.alpha());
            vertex(out, matrix, face.b().subtract(eye), face.uvB(), face.color(), face.alpha());
            vertex(out, matrix, face.c().subtract(eye), face.uvC(), face.color(), face.alpha());
            vertex(out, matrix, face.d().subtract(eye), face.uvD(), face.color(), face.alpha());
        }
        if (activeType != null) buffers.endBatch(activeType);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vector p, Uv uv, int rgb, int alpha) {
        out.addVertex(matrix, (float)p.x(), (float)p.y(), (float)p.z()).setUv((float)uv.u(), (float)uv.v())
                .setColor(rgb >>> 16 & 255, rgb >>> 8 & 255, rgb & 255, alpha);
    }
    private static Vector point(Vec3 p) { return new Vector(p.x, p.y, p.z); }
    private static Vec3 vec(Vector p) { return new Vec3(p.x(), p.y(), p.z()); }
}

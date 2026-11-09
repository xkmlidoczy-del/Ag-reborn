package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.highpower.FoldedMirrorArray;
import cn.academy.port.highpower.RemoteScatterRelay;
import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.client.RemoteScatterRelayState.Node;
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
import net.minecraft.client.gui.Font;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
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

/** NEW253 stationary world-space plates. Authoritative FIRE receipts alone create brief solid rays. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class RemoteScatterRelayEffects {
    private static final ResourceLocation WHITE = texture("port/white");
    private static final ResourceLocation SHIELD = texture("effects/mdshield");
    private static final ResourceLocation[] SMALL_RAY = {texture("effects/mdray_small/blend_in"), texture("effects/mdray_small/tile"), texture("effects/mdray_small/blend_out")};
    private static final RemoteScatterRelayState STATE = new RemoteScatterRelayState();
    private static final RemoteScatterRelayState.ResultNote RESULT = new RemoteScatterRelayState.ResultNote();
    private static final Map<UUID, Marker> MARKERS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static volatile long resultGeneration;
    private static MultiBufferSource.BufferSource buffers;
    private static RemoteScatterRelayState.Status terminalStatus;
    private static int statusTicks;
    private static final class Flash {
        final RemoteScatterRelayState.Frame frame;
        int age;
        Flash(RemoteScatterRelayState.Frame frame) { this.frame = frame; }
    }
    private static final class Marker {
        final Entity owner;
        final RemoteScatterRelayState.Lease lease = new RemoteScatterRelayState.Lease();
        final List<Flash> flashes = new ArrayList<>(2);
        RemoteScatterRelayState.Frame frame;
        Marker(Entity owner, RemoteScatterRelayState.Frame frame) { this.owner = owner; update(frame); }
        void update(RemoteScatterRelayState.Frame next) {
            frame = next; lease.received(next);
            if (next.phase() == RemoteScatterRelayState.FIRE && !next.path().isEmpty()) {
                if (flashes.size() == 2) flashes.removeFirst();
                flashes.add(new Flash(next));
            }
        }
    }
    private RemoteScatterRelayEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void reload(RegisterShadersEvent event) { resultGeneration++; RESULT.clear(); }
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { RemoteScatterRelay.clientReceiver = RemoteScatterRelayEffects::receive; }
    }
    private static RemoteScatterRelayState.Frame frame(RemoteScatterRelay.Visual p) {
        List<Node> plates = new ArrayList<>(p.plates().size());
        for (int i = 0; i < p.plates().size(); i++) {
            var plate = p.plates().get(i); plates.add(new Node(i, point(plate.center()), point(plate.normal())));
        }
        return new RemoteScatterRelayState.Frame(new RemoteScatterRelayState.Identity(p.owner(), p.ownerUuid(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), p.status(), p.remainingTicks(), point(p.ownerFeet()), List.copyOf(plates),
                point(p.ghostCenter()), point(p.ghostNormal()), p.path().stream().map(s -> new RemoteScatterRelayState.Segment(point(s.start()), point(s.end()))).toList());
    }
    public static void receive(RemoteScatterRelay.Visual packet) {
        if (packet == null || packet.ownerFeet() == null || packet.ghostCenter() == null || packet.ghostNormal() == null
                || packet.plates() == null || packet.plates().size() > RemoteScatterRelayState.MAX_NODES || packet.path() == null || packet.path().size() > RemoteScatterRelayState.MAX_SEGMENTS
                || packet.plates().stream().anyMatch(p -> p == null || p.center() == null || p.normal() == null)
                || packet.path().stream().anyMatch(s -> s == null || s.start() == null || s.end() == null)) return;
        final RemoteScatterRelayState.Frame receipt;
        try { receipt = frame(packet); } catch (IllegalArgumentException malformedGeometry) { return; }
        if (!RemoteScatterRelayState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation, capturedResultGeneration = resultGeneration;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.owner());
            if (owner == null || !owner.getUUID().equals(packet.ownerUuid())) return;
            Marker old = MARKERS.get(packet.ownerUuid());
            if (old != null && old.frame.id().token() == receipt.id().token() && old.owner != owner) return;
            boolean local = owner == mc.player;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch()) || !AcademyClient.remoteScatterRelayInputMatches(packet.input(), packet.epoch()))) return;
            if (packet.phase() != RemoteScatterRelayState.ENDED && (!owner.isAlive() || owner.isRemoved() || local && !localEligible())) return;
            if (!STATE.accept(receipt, local)) return;
            if (local) {
                AcademyClient.remoteScatterRelayAcceptedInput(packet.input(), packet.epoch());
                if (capturedResultGeneration == resultGeneration) RESULT.received(receipt);
            }
            if (packet.phase() == RemoteScatterRelayState.ENDED) {
                MARKERS.remove(packet.ownerUuid());
                if (local) {
                    // An invalid first placement is immediately retired; retain its real refusal cue long enough to read.
                    terminalStatus = RemoteScatterRelayState.terminalStatus(old == null ? null : old.frame, receipt); statusTicks = 30;
                    AcademyClient.remoteScatterRelayEndedInput(packet.input(), packet.epoch());
                }
                return;
            }
            if (local) { terminalStatus = null; statusTicks = 0; }
            if (old == null || old.frame.id().token() != receipt.id().token()) {
                if (old == null && MARKERS.size() >= RemoteScatterRelayState.MAX_CONTEXTS) { STATE.retire(receipt.id()); return; }
                MARKERS.put(packet.ownerUuid(), new Marker(owner, receipt));
            } else old.update(receipt);
        });
    }
    /** A fresh accepted local input discards the previous outcome before waiting for its next receipt. */
    public static void clearResultNote() { RESULT.newInput(); }
    public static void abortLocal(long input, long epoch) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); STATE.cancel(epoch, input);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        if (marker != null && marker.frame.id().epoch() == epoch && marker.frame.id().input() <= input) {
            STATE.retire(marker.frame.id()); MARKERS.remove(mc.player.getUUID());
        }
        terminalStatus = null; statusTicks = 0; RESULT.clear();
    }
    public static void clear() { generation++; STATE.clear(); MARKERS.clear(); terminalStatus = null; statusTicks = 0; RESULT.clear(); }
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
                && stateEligible();
    }
    /** Shared eligibility without temporary UI/camera suppression; no ability state is changed. */
    static boolean stateEligible() {
        Minecraft mc = Minecraft.getInstance(); var state = AcademyClient.state;
        return mc.player != null && !mc.player.isSpectator() && state != null && state.activated && state.overloadFine && !state.interfering
                && "meltdowner".equals(state.category) && RemoteScatterRelayState.eligible(state.level, state.learned(FoldedMirrorArray.ID), state.learned(RemoteScatterRelay.ID))
                && state.presets.currentContains(RemoteScatterRelay.ID) && SkillCatalog.enabled("meltdowner", RemoteScatterRelay.ID);
    }
    private static boolean validOwner(Marker marker, Minecraft mc) {
        return world != null && marker.owner.isAlive() && !marker.owner.isRemoved() && world.getEntity(marker.owner.getId()) == marker.owner
                && (marker.owner != mc.player || localEligible() && AcademyClient.singleOwnerEpochMatches(marker.frame.id().epoch())
                && AcademyClient.remoteScatterRelayInputMatches(marker.frame.id().input(), marker.frame.id().epoch()));
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        if (marker == null || !validOwner(marker, mc) || !marker.lease.visible()) return ClassicAbilityHud.DelegateVisual.IDLE;
        return marker.frame.phase() == RemoteScatterRelayState.EDIT ? ClassicAbilityHud.DelegateVisual.CHARGE : ClassicAbilityHud.DelegateVisual.ACTIVE;
    }
    public static boolean activeLocal() { return localVisual() != ClassicAbilityHud.DelegateVisual.IDLE; }
    /** Cooldown affects paid shots and edits, not the free recall of an already paid local array. */
    public static boolean canRecallLocal() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        return marker != null && validOwner(marker, mc) && !AcademyClient.state.consumptionInProgress()
                && RemoteScatterRelayState.recallAvailable(marker.frame, marker.lease.remaining(), marker.lease.quiet());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        RESULT.tick(mc.isPaused(), localEligible() && !mc.options.hideGui && !mc.getDebugOverlay().showDebugScreen());
        if (!mc.isPaused() && statusTicks > 0 && --statusTicks == 0) terminalStatus = null;
        Iterator<Marker> iterator = MARKERS.values().iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next();
            if (!mc.isPaused()) marker.flashes.removeIf(flash -> ++flash.age >= RemoteScatterRelayState.FLASH_TICKS);
            if (!marker.lease.tick(mc.isPaused(), validOwner(marker, mc))) {
                STATE.retire(marker.frame.id()); iterator.remove();
                if (marker.owner == mc.player) {
                    RESULT.clear();
                    AcademyClient.remoteScatterRelayEndedInput(marker.frame.id().input(), marker.frame.id().epoch());
                }
            }
        }
    }
    /** Native-font, wrapped client information; the separate result note never keeps rays or server leases alive. */
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (mc.player == null || mc.isPaused() || mc.options.hideGui || !localEligible() || mc.getDebugOverlay().showDebugScreen()) {
            RESULT.clear(); return;
        }
        Marker marker = MARKERS.get(mc.player.getUUID());
        RemoteScatterRelayState.Status status = marker != null && validOwner(marker, mc) && marker.lease.visible()
                ? RemoteScatterRelayState.status(marker.frame, marker.lease.remaining()) : terminalStatus;
        if (status == null && RESULT.status() == null) return;
        String stateText = status == null ? "" : hudText("brief." + status.key());
        String current = status == null ? hudText("history_title") : status.seconds() < 0 ? hudText("status_unpaid", status.count(), stateText)
                : hudText("status", status.count(), status.seconds(), stateText);
        String helpKey = switch (status == null ? "ended" : status.key()) {
            case "prepared", "placing", "endpoint", "measuring", "clearing" -> status.key();
            case "ended" -> "none";
            default -> "route";
        };
        String help = helpKey.equals("none") ? "" : hudText("help." + helpKey);
        String compact = helpKey.equals("none") ? "" : hudText("help_short." + helpKey);
        var result = RESULT.status();
        String last = result == null ? "" : hudText("last_result", hudText("result." + result.key()));
        var graphics = event.getGuiGraphics();
        var layout = RemoteScatterRelayHudLayout.fit(graphics.guiWidth(), graphics.guiHeight(), mc.font.lineHeight,
                new RemoteScatterRelayHudLayout.Content(current, help, compact, last),
                reservedHudBoxes(graphics.guiWidth(), graphics.guiHeight()),
                (text, width) -> mc.font.split(Component.literal(text), width), mc.font::width);
        if (layout == null) return;
        var box = layout.bounds();
        graphics.fill(box.x(), box.y(), box.x() + box.width(), box.y() + box.height(), 0x82070D0A);
        graphics.fill(box.x(), box.y(), box.x() + 2, box.y() + box.height(), 0xB85D9971);
        for (var line : layout.lines()) graphics.drawString(mc.font, line.text(), line.x(), line.y(),
                line.kind() == RemoteScatterRelayHudLayout.Kind.RESULT ? 0xFFF0E4AD
                        : line.kind() == RemoteScatterRelayHudLayout.Kind.HELP ? 0xFFB4C4BB : 0xFFE2F4E8, true);
    }
    private static String hudText(String key, Object... args) {
        return Component.translatable("academy.highpower.remote_scatter_relay." + key, args).getString();
    }
    /** Same configured instrument geometry as the existing HUD; no snapshots or resource values are changed. */
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
    /** Use the proven exact chunk corridor, including corner grazes, before any world geometry is emitted. */
    private static boolean loadedPath(Vector from, Vector to) {
        if (world == null) return false;
        var chunks = BeamEtchingMesh.crossedChunks(new BeamEtchingMesh.Point(from.x(), from.y(), from.z()), new BeamEtchingMesh.Point(to.x(), to.y(), to.z()));
        if (chunks.isEmpty()) return false;
        for (var chunk : chunks) if (!world.hasChunkAt(new BlockPos(chunk.x() * 16, (int)Math.floor(from.y()), chunk.z() * 16))) return false;
        return true;
    }
    private static boolean loadedFrame(RemoteScatterRelayState.Frame frame) {
        for (var segment : frame.path()) if (!loadedPath(segment.start(), segment.end())) return false;
        return true;
    }
    private record VisibleLabel(Vector position, Component text) {}
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (world == null || MARKERS.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        List<Marker> visible = MARKERS.values().stream().filter(m -> validOwner(m, mc) && m.lease.visible() && m.owner.distanceToSqr(camera) <= 192 * 192)
                .sorted(Comparator.comparingInt((Marker m) -> m.owner == mc.player ? 0 : 1)
                        .thenComparingDouble(m -> m.owner.distanceToSqr(camera)).thenComparing(m -> m.owner.getUUID())).limit(RemoteScatterRelayMesh.MAX_VISIBLE_CONTEXTS).toList();
        if (visible.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(524288));
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = event.getPoseStack().last().pose(); var facing = event.getCamera().getLookVector();
            Vector look = FoldedMirrorGeometry.unit(new Vector(facing.x(), facing.y(), facing.z())).orElse(null), eye = point(camera);
            if (look == null) return;
            boolean materialReady = RemoteScatterRelayMesh.enhanced(FoldedMirrorMaterial.available(), Boolean.getBoolean("academy.highpower.remoteScatterRelayForceFallback"));
            List<FoldedMirrorMesh.Face> scene = new ArrayList<>(), compactScene = new ArrayList<>();
            int glyphQuads = 0;
            List<VisibleLabel> labels = new ArrayList<>();
            float partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
            float phase = world.getGameTime() % 2400 + partial;
            for (Marker marker : visible) {
                List<FoldedMirrorMesh.Face> group = new ArrayList<>(), compactGroup = new ArrayList<>();
                for (Node node : marker.frame.plates()) if (world.hasChunkAt(BlockPos.containing(vec(node.center())))) {
                    float power = 0;
                    for (int i = marker.flashes.size() - 1; i >= 0; i--) {
                        Flash flash = marker.flashes.get(i);
                        if (RemoteScatterRelayMesh.reached(flash.frame, node)) { power = 1 - (flash.age + partial) / RemoteScatterRelayState.FLASH_TICKS; break; }
                    }
                    group.addAll(RemoteScatterRelayMesh.plate(node, false, phase, power));
                    compactGroup.addAll(RemoteScatterRelayMesh.compactPlate(node, false));
                }
                if (RemoteScatterRelayState.hasGhost(marker.frame) && world.hasChunkAt(BlockPos.containing(vec(marker.frame.ghostCenter())))) {
                    Node ghost = new Node(0, marker.frame.ghostCenter(), marker.frame.ghostNormal());
                    group.addAll(RemoteScatterRelayMesh.plate(ghost, true, phase, 0));
                    compactGroup.addAll(RemoteScatterRelayMesh.compactPlate(ghost, true));
                }
                if (marker.frame.phase() != RemoteScatterRelayState.FIRE && loadedFrame(marker.frame)) {
                    group.addAll(RemoteScatterRelayMesh.path(marker.frame, eye, look, 0));
                    compactGroup.addAll(RemoteScatterRelayMesh.compactPath(marker.frame, eye, look, 0));
                }
                for (Flash flash : marker.flashes) if (loadedFrame(flash.frame)) {
                    group.addAll(RemoteScatterRelayMesh.path(flash.frame, eye, look, flash.age + partial));
                    compactGroup.addAll(RemoteScatterRelayMesh.compactPath(flash.frame, eye, look, flash.age + partial));
                }
                // Only the owner needs planning labels. They use normal depth testing and a real terrain visibility check.
                if (marker.owner == mc.player && !mc.options.hideGui) for (var label : RemoteScatterRelayMesh.labels(marker.frame)) {
                    Component text = Component.translatable("academy.highpower.remote_scatter_relay." + label.key(), label.value());
                    // Reserve every glyph and its shadow inside the same hard vertex cap.
                    int needed = text.getString().length() * 2;
                    if (needed <= RemoteScatterRelayMesh.MAX_LABEL_QUADS - glyphQuads && visibleLabel(label.position(), camera, look)) {
                        labels.add(new VisibleLabel(label.position(), text)); glyphQuads += needed;
                    }
                }
                scene.addAll(RemoteScatterRelayMesh.clipped(group, eye, look));
                compactScene.addAll(RemoteScatterRelayMesh.clipped(compactGroup, eye, look));
            }
            var selected = RemoteScatterRelayMesh.scene(scene, compactScene, glyphQuads, materialReady);
            drawOrdered(matrix, RemoteScatterRelayMesh.ordered(selected.faces(), eye), eye, selected.enhanced(), selected.compact());
            for (var label : labels) label(event, label, camera, look);
        } finally {
            try { buffers.endBatch(); } finally {
                modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            }
        }
    }
    private static boolean visibleLabel(Vector position, Vec3 camera, Vector look) {
        Minecraft mc = Minecraft.getInstance();
        Vector relative = position.subtract(point(camera));
        if (relative.dot(look) <= .25 || relative.length() > 192 || !loadedPath(point(camera), position)) return false;
        return world.clip(new ClipContext(camera, vec(position), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }
    private static void label(RenderLevelStageEvent event, VisibleLabel label, Vec3 camera, Vector look) {
        Minecraft mc = Minecraft.getInstance();
        Vector relative = RemoteScatterRelayMesh.relative(label.position(), point(camera));
        float scale = (float)Math.clamp(relative.dot(look) * .0011, .012, .045);
        var poses = event.getPoseStack(); poses.pushPose();
        try {
            poses.translate(relative.x(), relative.y(), relative.z()); poses.mulPose(event.getCamera().rotation()); poses.scale(scale, -scale, scale);
            mc.font.drawInBatch(label.text(), -mc.font.width(label.text()) / 2f, 0, 0xFFD9F5E1, true,
                    poses.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 15728880);
        } finally { poses.popPose(); }
    }
    private static void drawOrdered(Matrix4f matrix, List<FoldedMirrorMesh.Face> faces, Vector eye, boolean enhanced, boolean compact) {
        RenderType activeType = null; int activeLayer = -1;
        int activeKind = -1, activeTexture = -2; FoldedMirrorMesh.Material activeMaterial = null;
        for (var face : faces) {
            ResourceLocation texture = face.texture() == FoldedMirrorMesh.SHIELD_TEXTURE ? SHIELD : face.texture() < 0 ? WHITE : SMALL_RAY[face.texture()];
            RenderType type = RemoteScatterRelayRenderTypes.type(texture, FoldedMirrorMesh.additive(face), !compact && FoldedMirrorMesh.culled(face), enhanced);
            int layer = FoldedMirrorMesh.layer(face);
            boolean changed = activeKind != face.kind() || activeTexture != face.texture() || !face.material().equals(activeMaterial);
            if (activeType != null && (activeType != type || activeLayer != layer || changed)) buffers.endBatch(activeType);
            if (enhanced && (activeType != type || activeLayer != layer || changed)) FoldedMirrorMaterial.set(face);
            activeType = type; activeLayer = layer; activeKind = face.kind(); activeTexture = face.texture(); activeMaterial = face.material();
            VertexConsumer out = buffers.getBuffer(type);
            vertex(out, matrix, RemoteScatterRelayMesh.relative(face.a(), eye), face.uvA(), face.color(), face.alpha());
            vertex(out, matrix, RemoteScatterRelayMesh.relative(face.b(), eye), face.uvB(), face.color(), face.alpha());
            vertex(out, matrix, RemoteScatterRelayMesh.relative(face.c(), eye), face.uvC(), face.color(), face.alpha());
            vertex(out, matrix, RemoteScatterRelayMesh.relative(face.d(), eye), face.uvD(), face.color(), face.alpha());
        }
        if (activeType != null) buffers.endBatch(activeType);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vector p, FoldedMirrorMesh.Uv uv, int rgb, int alpha) {
        out.addVertex(matrix, (float)p.x(), (float)p.y(), (float)p.z()).setUv((float)uv.u(), (float)uv.v()).setColor(rgb >>> 16 & 255, rgb >>> 8 & 255, rgb & 255, alpha);
    }
    private static Vector point(Vec3 point) { return new Vector(point.x, point.y, point.z); }
    private static Vec3 vec(Vector point) { return new Vec3(point.x(), point.y(), point.z()); }

    /** Read-only snapshot: points = feet, ghost centre, ghost normal, then (centre, normal) per node; numbers = status, remaining; polyline = relay path. */
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

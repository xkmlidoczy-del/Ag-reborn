package cn.academy.port.client;

import cn.academy.port.highpower.DisplacedNeedleEntity;
import cn.academy.port.highpower.SpatialPincerCorridor;
import cn.academy.port.highpower.TeleporterNeedleEntities;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Private immutable point/target plans; only actual native publication/contact and movement earn cues. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class SpatialPincerEffects {
    private static final ResourceLocation[] MARKS = new ResourceLocation[SpatialPincerMesh.SUCCESS_TICKS];
    private static final SpatialPincerUiState.VisualState STATE = new SpatialPincerUiState.VisualState();
    private static final List<Cue> CUES = new ArrayList<>(SpatialPincerMesh.MAX_CUES);
    private static Object world, localPlayer, connection;
    private static long generation;
    private static MultiBufferSource.BufferSource buffers;
    private static final class Cue {
        final Entity owner;
        final SpatialPincerUiState.Frame receipt;
        final List<Point> points;
        int age;
        Cue(Entity owner, SpatialPincerUiState.Frame receipt, List<Point> points) { this.owner = owner; this.receipt = receipt; this.points = points; }
    }
    static { for (int i = 0; i < MARKS.length; i++) MARKS[i] = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/tp_mark/" + i + ".png"); }
    private SpatialPincerEffects() {}
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { SpatialPincerCorridor.clientReceiver = SpatialPincerEffects::receive; }
    }
    private static SpatialPincerUiState.Frame frame(SpatialPincerCorridor.Visual p) {
        return new SpatialPincerUiState.Frame(new SpatialPincerUiState.Controller(p.entity(), p.owner(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), p.prepared(), p.remaining(), p.prepared() >= 1 ? point(p.pointA()) : null,
                p.prepared() >= 2 ? point(p.pointB()) : null, p.prepared() == 3 ? point(p.pointC()) : null,
                point(p.source()), point(p.arrival()), point(p.impact()), p.needleSlot(), p.needleEntity(), p.needleUuid(), p.accepted(), p.reason(),
                p.targets().stream().map(t -> new SpatialPincerUiState.Target(t.entity(), t.uuid(), point(t.endpoint()))).toList(), p.volleyNeedles());
    }
    public static void receive(SpatialPincerCorridor.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!SpatialPincerUiState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var capturedWorld = mc.level; var capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedGeneration = generation, capturedSession = AcademyClient.inputSessionEpoch();
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || capturedGeneration != generation || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.entity());
            if (!(owner instanceof Player) || !owner.getUUID().equals(packet.owner()) || !owner.isAlive() || owner.isRemoved()) return;
            boolean local = owner == mc.player;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.spatialPincerReceiptMatches(packet.input(), packet.epoch(), SpatialPincerUiState.terminal(receipt)))) return;
            if (local && !AcademyClient.spatialPincerControlValid()) { AcademyClient.spatialPincerCancelInput(); return; }
            if (packet.phase() == SpatialPincerUiState.PROJECTED || packet.phase() == SpatialPincerUiState.IMPACT) {
                Entity needle = capturedWorld.getEntity(packet.needleEntity());
                if (needle != null && (!(needle instanceof DisplacedNeedleEntity) || needle.getType() != TeleporterNeedleEntities.CORRIDOR_NEEDLE.get()
                        || !needle.getUUID().equals(packet.needleUuid()))) return;
            }
            if (!STATE.accept(receipt, local)) return;
            if (local) {
                if (SpatialPincerUiState.terminal(receipt)) { AcademyClient.spatialPincerEndedInput(packet.input(), packet.epoch()); SpatialPincerClient.cancelPending(); }
                else AcademyClient.spatialPincerAcceptedInput(packet.input(), packet.epoch(), STATE.remainingTicks(),
                        packet.phase() == SpatialPincerUiState.PROJECTED || packet.phase() == SpatialPincerUiState.IMPACT);
            }
            if (packet.phase() == SpatialPincerUiState.PROJECTED)
                addCue(new Cue(owner, receipt, List.of(receipt.source(), receipt.arrival())), local);
            else if (packet.phase() == SpatialPincerUiState.IMPACT && packet.accepted())
                addCue(new Cue(owner, receipt, List.of(receipt.impact())), local);
            if (SpatialPincerUiState.terminal(receipt) && !receipt.reason().isEmpty())
                CUES.removeIf(c -> c.receipt != null && SpatialPincerUiState.sameController(c.receipt.id(), receipt.id()));
            if (packet.phase() == SpatialPincerUiState.REFUSED && local) mc.player.displayClientMessage(SpatialPincerClient.reason(packet.reason()), true);
        });
    }
    private static void addCue(Cue cue, boolean local) {
        if (cue.receipt != null) CUES.removeIf(c -> c.receipt != null && SpatialPincerUiState.sameController(c.receipt.id(), cue.receipt.id())
                && c.receipt.needleSlot() == cue.receipt.needleSlot());
        if (CUES.size() >= SpatialPincerMesh.MAX_CUES) {
            Cue replace = CUES.stream().filter(c -> c.owner != Minecraft.getInstance().player).findFirst().orElse(null);
            if (replace != null) CUES.remove(replace); else if (local) CUES.removeFirst(); else return;
        }
        CUES.add(cue);
    }
    public static void confirm(String dimension, Vec3 from, Vec3 destination) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (mc.level == null || mc.player == null || from == null || destination == null || from.distanceToSqr(destination) <= 0
                || !mc.level.dimension().location().toString().equals(dimension)) return;
        addCue(new Cue(mc.player, null, List.of(point(from), point(destination))), true);
    }
    public static void abortLocal(long input, long epoch) {
        STATE.cancel(epoch, input);
        CUES.removeIf(c -> c.owner == Minecraft.getInstance().player && c.receipt != null
                && c.receipt.id().epoch() == epoch && c.receipt.id().input() <= input);
        SpatialPincerClient.cancelPending();
    }
    public static void clear() { generation++; STATE.clear(); CUES.clear(); }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        if (STATE.active() != null) AcademyClient.spatialPincerCancelInput();
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean locallyOwned(SpatialPincerUiState.Frame frame, Minecraft mc) {
        return frame != null && mc.level != null && mc.player != null && mc.player.isAlive() && !mc.player.isRemoved()
                && mc.player.getId() == frame.id().entity() && mc.player.getUUID().equals(frame.id().owner())
                && AcademyClient.singleOwnerEpochMatches(frame.id().epoch()) && AcademyClient.spatialPincerInputMatches(frame.id().input(), frame.id().epoch())
                && AcademyClient.spatialPincerControlValid();
    }
    public static int preparedPoints() { return SpatialPincerUiState.preparation(STATE.active()) && STATE.remainingTicks() > 0 ? STATE.active().prepared() : 0; }
    public static boolean targetsFrozen() { return STATE.active() != null && STATE.active().phase() == SpatialPincerUiState.PREVIEW && STATE.remainingTicks() > 0; }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        return (STATE.markerVisible() || STATE.firing()) && locallyOwned(STATE.active(), mc)
                ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (STATE.tick(mc.isPaused(), locallyOwned(STATE.active(), mc)) != null) AcademyClient.spatialPincerCancelInput();
        CUES.removeIf(c -> mc.level == null || c.owner.isRemoved() || !c.owner.isAlive() || mc.level.getEntity(c.owner.getId()) != c.owner
                || !mc.isPaused() && ++c.age >= SpatialPincerMesh.SUCCESS_TICKS);
    }
    @SubscribeEvent public static void guidance(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var active = STATE.active();
        if (mc.player == null || mc.screen != null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()
                || !locallyOwned(active, mc) || !STATE.markerVisible() && !STATE.firing()) return;
        var lines = new ArrayList<Component>();
        String phase = STATE.firing() ? "firing" : targetsFrozen() ? "confirm" : active.prepared() == 1 ? "place_b" : active.prepared() == 2 ? "place_c" : "ready";
        lines.add(SpatialPincerClient.text("guide." + phase, active.volleyNeedles()));
        if (targetsFrozen()) {
            int targets = active.targets().size();
            lines.add(SpatialPincerClient.text("guide.targets", targets, active.volleyNeedles(), (STATE.remainingTicks() + 19) / 20));
            lines.add(SpatialPincerClient.text(targets == 1 ? "guide.allocation_one" : targets == 2 ? "guide.allocation_two" : "guide.allocation_equal", active.volleyNeedles()));
        } else {
            if (STATE.markerVisible()) lines.add(SpatialPincerClient.text("guide.count", active.prepared(), (STATE.remainingTicks() + 19) / 20));
            lines.add(SpatialPincerClient.text("guide.clear"));
        }
        var graphics = event.getGuiGraphics(); int width = Math.min(340, graphics.guiWidth() - 24); if (width < 160) return;
        var rows = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (var line : lines) rows.addAll(mc.font.split(line, width));
        var layout = CornerExchangeHudLayout.fit(graphics.guiWidth(), graphics.guiHeight(), rows.size(), mc.font.lineHeight);
        if (layout == null) return;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(graphics.guiWidth() / 2F, layout.top(), 0);
            graphics.pose().scale(layout.scale(), layout.scale(), 1);
            int y = 0;
            for (var row : rows) { graphics.drawString(mc.font, row, -mc.font.width(row) / 2, y, 0xFFE2E7E8, true); y += layout.lineStep(); }
        } finally { graphics.pose().popPose(); }
    }
    private static boolean visible(RenderLevelStageEvent event, Point point, Vec3 camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || point == null || camera.distanceToSqr(vector(point)) > SpatialPincerMesh.CULL_DISTANCE * SpatialPincerMesh.CULL_DISTANCE) return false;
        AABB box = new AABB(point.x() - .5, point.y() - .3, point.z() - .5, point.x() + .5, point.y() + .8, point.z() + .5);
        for (int x = Math.floorDiv((int)Math.floor(box.minX), 16); x <= Math.floorDiv((int)Math.floor(box.maxX), 16); x++)
            for (int z = Math.floorDiv((int)Math.floor(box.minZ), 16); z <= Math.floorDiv((int)Math.floor(box.maxZ), 16); z++)
                if (!mc.level.hasChunkAt(new BlockPos(x * 16, (int)Math.floor(point.y()), z * 16))) return false;
        return event.getFrustum().isVisible(box);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var active = STATE.active(); if (mc.level == null || mc.player == null || active == null && CUES.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(16384));
        var poses = event.getPoseStack(); Vec3 camera = event.getCamera().getPosition();
        var look = event.getCamera().getLookVector(); Vec3 forward = new Vec3(look.x(), look.y(), look.z());
        var modelView = RenderSystem.getModelViewStack(); float[] color = RenderSystem.getShaderColor().clone();
        modelView.pushMatrix(); poses.pushPose();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = poses.last().pose();
            if (!mc.options.hideGui && STATE.markerVisible() && locallyOwned(active, mc)) {
                var points = new Point[]{active.pointA(), active.pointB(), active.pointC()};
                for (int i = 0; i < active.prepared(); i++) if (visible(event, points[i], camera)) {
                    for (var line : SpatialPincerMesh.marker(points[i])) line(buffers.getBuffer(Material.LINES), matrix, line, camera, forward, 200);
                    label(event, points[i], String.valueOf((char)('A' + i)), camera, forward);
                }
                // This is the consent route only. B contributes to the attack, never a transport waypoint.
                if (active.prepared() == 3 && (visible(event, active.pointA(), camera) || visible(event, active.pointC(), camera)))
                    for (var route : SpatialPincerMesh.route(active.pointA(), active.pointC())) line(buffers.getBuffer(Material.LINES), matrix, route, camera, forward, 72);
                // Exact server endpoints stay frozen even when the named mob moves or disappears locally.
                for (int i = 0; i < Math.min(SpatialPincerMesh.MAX_TARGET_MARKERS, active.targets().size()); i++) {
                    Point endpoint = active.targets().get(i).endpoint();
                    if (visible(event, endpoint, camera)) {
                        for (var line : SpatialPincerMesh.endpoint(endpoint)) line(buffers.getBuffer(Material.LINES), matrix, line, camera, forward, 200);
                        label(event, endpoint, "T" + (i + 1), camera, forward);
                    }
                }
            }
            for (var cue : CUES) {
                int alpha = SpatialPincerMesh.alpha(cue.age); if (alpha <= 0) continue;
                for (var point : cue.points) if (visible(event, point, camera)) {
                    for (var line : SpatialPincerMesh.endpoint(point)) line(buffers.getBuffer(Material.LINES), matrix, line, camera, forward, alpha);
                    var consumer = buffers.getBuffer(Material.quads(MARKS[SpatialPincerMesh.frame(cue.age)]));
                    for (var line : SpatialPincerMesh.endpoint(point)) stroke(consumer, matrix, line, camera, forward, alpha);
                }
            }
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }
    private static void line(VertexConsumer out, Matrix4f matrix, SpatialPincerMesh.Line line, Vec3 camera, Vec3 forward, int alpha) {
        Vec3 a = vector(SpatialPincerMesh.relative(line.from(), point(camera))), b = vector(SpatialPincerMesh.relative(line.to(), point(camera)));
        double da = a.dot(forward), db = b.dot(forward); if (da <= .08 && db <= .08) return;
        if (da < .08) a = a.lerp(b, (.08 - da) / (db - da)); else if (db < .08) b = a.lerp(b, (.08 - da) / (db - da));
        Vec3 normal = b.subtract(a).normalize();
        out.addVertex(matrix, (float)a.x, (float)a.y, (float)a.z).setColor(220, 225, 226, alpha).setNormal((float)normal.x, (float)normal.y, (float)normal.z);
        out.addVertex(matrix, (float)b.x, (float)b.y, (float)b.z).setColor(220, 225, 226, alpha).setNormal((float)normal.x, (float)normal.y, (float)normal.z);
    }
    private static void stroke(VertexConsumer out, Matrix4f matrix, SpatialPincerMesh.Line line, Vec3 camera, Vec3 forward, int alpha) {
        Vec3 a = vector(SpatialPincerMesh.relative(line.from(), point(camera))), b = vector(SpatialPincerMesh.relative(line.to(), point(camera)));
        Vec3 side = b.subtract(a).cross(a.scale(-1)).normalize().scale(.009);
        if (side.lengthSqr() < 1e-10 || a.dot(forward) <= .089 || b.dot(forward) <= .089) return;
        vertex(out, matrix, a.subtract(side), .03125F, .25F, alpha); vertex(out, matrix, a.add(side), .03125F, .46875F, alpha);
        vertex(out, matrix, b.add(side), .484375F, .46875F, alpha); vertex(out, matrix, b.subtract(side), .484375F, .25F, alpha);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 p, float u, float v, int alpha) {
        out.addVertex(matrix, (float)p.x, (float)p.y, (float)p.z).setUv(u, v).setColor(230, 240, 244, alpha);
    }
    private static void label(RenderLevelStageEvent event, Point point, String name, Vec3 camera, Vec3 forward) {
        Minecraft mc = Minecraft.getInstance(); Vec3 at = vector(point).add(0, .4, 0), relative = at.subtract(camera);
        int steps = Math.max(1, (int)Math.ceil(relative.length() / 8));
        for (int i = 0; i <= steps; i++) if (!mc.level.hasChunkAt(BlockPos.containing(camera.lerp(at, (double)i / steps)))) return;
        if (mc.level.clip(new ClipContext(camera, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() != HitResult.Type.MISS) return;
        Component text = SpatialPincerClient.text(name.startsWith("T") ? "mark_target" : name.equals("B") ? "mark_attack" : "mark_plan", name);
        float scale = CrossSubstitutionState.labelScale(relative.dot(forward), relative.length(), mc.font.width(text)); if (scale <= 0) return;
        var poses = event.getPoseStack(); poses.pushPose();
        try {
            poses.translate(relative.x, relative.y, relative.z); poses.mulPose(event.getCamera().rotation()); poses.scale(scale, -scale, scale);
            mc.font.drawInBatch(text, -mc.font.width(text) / 2F, 0, 0xFFE2E7E8, true, poses.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 15728880);
        } finally { poses.popPose(); }
    }
    private static Point point(Vec3 p) { return p == null ? null : new Point(p.x, p.y, p.z); }
    private static Vec3 vector(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private static final class Material extends RenderType {
        private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
        private Material(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable set, Runnable reset) { super(name, format, mode, size, crumbling, sort, set, reset); }
        static RenderType quads(ResourceLocation texture) {
            return TYPES.computeIfAbsent(texture, t -> create("academy_spatial_pincer_" + t.getPath(), DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(t, false, false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
        static final RenderType LINES = create("academy_spatial_pincer_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 4096, false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                        .setLineState(new LineStateShard(OptionalDouble.of(1.5))).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setOutputState(PARTICLES_TARGET).createCompositeState(false));
    }

    /** Read-only snapshot for the decoration layer (phase 0 = aiming/active, 1 = success, 2 = refusal/warning): points = A, B, C, source, arrival, impact, then target endpoints; success cues carry their own point list. */
    private static double[] dp(cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        var a = STATE.active();
        if (a != null && (STATE.markerVisible() || STATE.firing())) {
            var points = new java.util.ArrayList<double[]>(java.util.List.of(dp(a.pointA()), dp(a.pointB()), dp(a.pointC()), dp(a.source()), dp(a.arrival()), dp(a.impact())));
            if (a.targets() != null) for (var t : a.targets()) if (points.size() < 14) points.add(dp(t.endpoint()));
            out.add(DecorSnap.of(true, 0, STATE.firing() ? 1 : 0, a.id().token(), points, a.prepared(), STATE.remainingTicks()));
        }
        for (var c : CUES) {
            if (c.owner == null || c.owner.isRemoved()) continue;
            var points = new java.util.ArrayList<double[]>(); for (var p : c.points) if (points.size() < 14) points.add(dp(p));
            out.add(DecorSnap.of(c.owner == mc.player, c.age, 2, c.receipt.id().token(), points));
        }
        return out;
    }
}

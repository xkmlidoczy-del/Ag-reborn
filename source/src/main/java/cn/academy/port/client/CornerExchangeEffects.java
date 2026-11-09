package cn.academy.port.client;

import cn.academy.port.highpower.CornerExchange;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
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
import net.minecraft.client.multiplayer.ClientLevel;
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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;

/** NEW333 owner-only anchor, short coordinate warning, and confirmed native-move afterimages. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class CornerExchangeEffects {
    private static final double CULL_DISTANCE = 64, NEAR_PLANE = .08;
    private static final ResourceLocation PARTICLE = texture("effects/tp_particle");
    private static final ResourceLocation[] MARKS = new ResourceLocation[CornerExchangeMesh.SUCCESS_TICKS];
    private static final CornerExchangeState STATE = new CornerExchangeState();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static long generation;
    private static MultiBufferSource.BufferSource buffers;
    private static Cosmetic success, observerWarning;
    private static final class Cosmetic {
        final Entity owner;
        final CornerExchangeState.Frame frame;
        int age;
        Cosmetic(Entity owner, CornerExchangeState.Frame frame) { this.owner = owner; this.frame = frame; }
    }
    static { for (int i = 0; i < MARKS.length; i++) MARKS[i] = texture("effects/tp_mark/" + i); }
    private CornerExchangeEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { CornerExchange.clientReceiver = CornerExchangeEffects::receive; }
    }
    private static CornerExchangeState.Frame frame(CornerExchange.Visual p) {
        return new CornerExchangeState.Frame(new CornerExchangeState.Identity(p.entity(), p.owner(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), point(p.source()), point(p.destination()), box(p.bounds()), p.moved(), p.reason(), p.remainingTicks(), point(p.markA()), point(p.markB()), p.reversed(), p.warningTicks());
    }
    public static void receive(CornerExchange.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!CornerExchangeState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.entity());
            if (!(owner instanceof Player) || !owner.getUUID().equals(packet.owner()) || !owner.isAlive() || owner.isRemoved()) return;
            boolean local = owner == mc.player;
            if (!local && (packet.phase() == CornerExchangeState.MARKED || packet.phase() == CornerExchangeState.REFUSED)) return;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.cornerExchangeReceiptMatches(packet.input(), packet.epoch(), CornerExchangeState.terminal(receipt)))) return;
            if (local && !AcademyClient.cornerExchangeControlValid()) { AcademyClient.cornerExchangeCancelInput(); return; }
            if (!STATE.accept(receipt, local)) return;
            if (local) {
                if (CornerExchangeState.terminal(receipt)) AcademyClient.cornerExchangeEndedInput(packet.input(), packet.epoch());
                else AcademyClient.cornerExchangeAcceptedInput(packet.input(), packet.epoch(), STATE.remainingTicks(),
                        packet.phase() == CornerExchangeState.WARNING);
            }
            if (packet.phase() == CornerExchangeState.WARNING && !local) {
                if (observerWarning == null || sameController(observerWarning.frame, receipt)) {
                    if (observerWarning == null || observerWarning.frame.id().input() != receipt.id().input()) {
                        observerWarning = new Cosmetic(owner, receipt);
                        observerWarning.age = CornerExchangeState.WARNING_TICKS - packet.warningTicks();
                    }
                }
            } else {
                if (observerWarning != null && sameController(observerWarning.frame, receipt)) observerWarning = null;
                if (packet.phase() == CornerExchangeState.SUCCESS) {
                    // One bounded actual endpoint pair globally; local receipts take priority.
                    if (success == null || local || success.owner != mc.player) success = new Cosmetic(owner, receipt);
                } else if (packet.phase() == CornerExchangeState.REFUSED && local) {
                    String reason = "academy.highpower.corner_exchange.reason." + packet.reason();
                    var detail = net.minecraft.locale.Language.getInstance().has(reason) ? Component.translatable(reason)
                            : Component.translatable("academy.highpower.corner_exchange.refused");
                    mc.player.displayClientMessage(Component.translatable("academy.highpower.corner_exchange.blocked", detail), true);
                }
            }
        });
    }
    public static void abortLocal(long input, long epoch) {
        STATE.cancel(epoch, input);
        if (success != null && success.owner == Minecraft.getInstance().player && success.frame.id().epoch() == epoch
                && success.frame.id().input() <= input) success = null;
    }
    public static void clear() { generation++; STATE.clear(); success = observerWarning = null; }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        if (STATE.active() != null) AcademyClient.cornerExchangeCancelInput();
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean validOwner(Entity owner, CornerExchangeState.Frame frame) {
        return world != null && owner != null && owner.isAlive() && !owner.isRemoved()
                && world.getEntity(frame.id().entity()) == owner && owner.getUUID().equals(frame.id().owner());
    }
    private static boolean locallyOwned(CornerExchangeState.Frame frame, Minecraft mc) {
        return frame != null && validOwner(mc.player, frame) && AcademyClient.singleOwnerEpochMatches(frame.id().epoch())
                && AcademyClient.cornerExchangeInputMatches(frame.id().input(), frame.id().epoch()) && AcademyClient.cornerExchangeControlValid();
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (STATE.markerVisible() && locallyOwned(STATE.active(), mc)) return STATE.warningVisible()
                ? ClassicAbilityHud.DelegateVisual.CHARGE : ClassicAbilityHud.DelegateVisual.ACTIVE;
        return success != null && success.owner == mc.player && validOwner(success.owner, success.frame)
                ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (STATE.tick(mc.isPaused(), locallyOwned(STATE.active(), mc)) != null) AcademyClient.cornerExchangeCancelInput();
        if (success != null && (!validOwner(success.owner, success.frame)
                || !mc.isPaused() && ++success.age >= CornerExchangeMesh.SUCCESS_TICKS)) success = null;
        if (observerWarning != null) {
            if (!validOwner(observerWarning.owner, observerWarning.frame) || !loaded(observerWarning.frame.bounds())) {
                observerWarning = null;
            } else if (!mc.isPaused() && ++observerWarning.age >= CornerExchangeState.WARNING_TICKS) {
                observerWarning = null;
            }
        }
    }
    /** Bounded chunk presence checks never request or load a missing chunk. */
    private static boolean loaded(Box bounds) {
        if (world == null || bounds == null || !bounds.body()) return false;
        for (int x = Math.floorDiv((int)Math.floor(bounds.minX()), 16); x <= Math.floorDiv((int)Math.floor(bounds.maxX()), 16); x++)
            for (int z = Math.floorDiv((int)Math.floor(bounds.minZ()), 16); z <= Math.floorDiv((int)Math.floor(bounds.maxZ()), 16); z++)
                if (!world.hasChunkAt(new BlockPos(x * 16, (int)Math.floor(bounds.minY()), z * 16))) return false;
        return true;
    }
    private static boolean visible(RenderLevelStageEvent event, Box bounds, Vec3 camera) {
        return loaded(bounds) && camera.distanceToSqr((bounds.minX() + bounds.maxX()) / 2,
                (bounds.minY() + bounds.maxY()) / 2, (bounds.minZ() + bounds.maxZ()) / 2) <= CULL_DISTANCE * CULL_DISTANCE
                && event.getFrustum().isVisible(aabb(bounds));
    }
    private static boolean labelVisible(Entity owner, Vec3 camera, Vec3 target) {
        if (world == null || camera.distanceToSqr(target) > CULL_DISTANCE * CULL_DISTANCE) return false;
        int steps = Math.max(1, (int)Math.ceil(camera.distanceTo(target) / 8));
        for (int i = 0; i <= steps; i++) if (!world.hasChunkAt(BlockPos.containing(camera.lerp(target, (double)i / steps)))) return false;
        return world.clip(new ClipContext(camera, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getType() == HitResult.Type.MISS;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var active = STATE.active(); if (world == null || active == null && success == null && observerWarning == null) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        var poses = event.getPoseStack(); Vec3 camera = event.getCamera().getPosition();
        var direction = event.getCamera().getLookVector(); Vec3 forward = new Vec3(direction.x(), direction.y(), direction.z());
        var modelView = RenderSystem.getModelViewStack(); float[] color = RenderSystem.getShaderColor().clone();
        modelView.pushMatrix(); poses.pushPose();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            // Every world point is reduced by the double camera before float conversion. No absolute float translation.
            Matrix4f matrix = poses.last().pose();
            if (STATE.markerVisible() && locallyOwned(active, mc)) {
                boolean warning = STATE.warningVisible();
                int alpha = warning ? (STATE.warningRemainingTicks() % 2 == 0 ? 230 : 255) : 190;
                boolean reversed = active.phase() == CornerExchangeState.WARNING ? active.reversed()
                        : AcademyClient.cornerExchangeReversedPreview(mc.player.isShiftKeyDown());
                float yaw = mc.player.getYRot() + (reversed ? 180 : 0);
                Box a = CornerExchangeMesh.markBounds(active.markA(), active.bounds(), active.destination());
                Box b = CornerExchangeMesh.markBounds(active.markB(), active.bounds(), active.destination());
                if (visible(event, a, camera)) {
                    corners(matrix, a, camera, forward, alpha, warning);
                    segments(matrix, CornerExchangeMesh.directionArrow(a, yaw), camera, forward, alpha, warning);
                    label(event, active.markA(), a, "A", camera, forward, warning);
                }
                if (visible(event, b, camera)) {
                    corners(matrix, b, camera, forward, alpha, warning);
                    segments(matrix, CornerExchangeMesh.directionArrow(b, yaw), camera, forward, alpha, warning);
                    label(event, active.markB(), b, "B", camera, forward, warning);
                }
            }
            if (observerWarning != null && observerWarning.age < CornerExchangeState.WARNING_TICKS
                    && validOwner(observerWarning.owner, observerWarning.frame)) {
                var f = observerWarning.frame;
                int alpha = observerWarning.age % 2 == 0 ? 255 : 220;
                if (visible(event, f.bounds(), camera)) corners(matrix, f.bounds(), camera, forward, alpha, true);
                Box departure = PhaseSidestepMesh.sourceBounds(f.bounds(), f.source(), f.destination());
                if (visible(event, departure, camera)) corners(matrix, departure, camera, forward, alpha, true);
            }
            if (success != null && validOwner(success.owner, success.frame)) renderSuccess(event, matrix, camera, forward);
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }
    private static void renderSuccess(RenderLevelStageEvent event, Matrix4f matrix, Vec3 camera, Vec3 forward) {
        var f = success.frame; int alpha = CornerExchangeMesh.alpha(success.age);
        Box departure = PhaseSidestepMesh.sourceBounds(f.bounds(), f.source(), f.destination());
        if (!loaded(departure) || !loaded(f.bounds()) || camera.distanceToSqr(vec(f.destination())) > CULL_DISTANCE * CULL_DISTANCE) return;
        for (Box bounds : List.of(departure, f.bounds())) if (visible(event, bounds, camera)) {
            corners(matrix, bounds, camera, forward, alpha, false);
            // A first-person camera inside an endpoint must not be filled by the hologram's inward faces.
            if (!CornerExchangeMesh.insideCamera(bounds, point(camera))) {
                var out = buffers.getBuffer(Material.quads(MARKS[CornerExchangeMesh.frame(success.age)]));
                for (var quad : PhaseSidestepMesh.biped(bounds)) for (var v : List.of(quad.a(), quad.b(), quad.c(), quad.d()))
                    vertex(out, matrix, v.position(), camera, v.u(), v.v(), alpha);
            }
        }
        var right = event.getCamera().getLeftVector(); var up = event.getCamera().getUpVector();
        Vec3 horizontal = new Vec3(right.x(), right.y(), right.z()), vertical = new Vec3(up.x(), up.y(), up.z());
        var out = buffers.getBuffer(Material.quads(PARTICLE));
        for (var p : PhaseSidestepMesh.particles(f.source(), f.destination(), success.age)) {
            Vec3 at = vec(p.position()), a = horizontal.scale(p.size()), b = vertical.scale(p.size());
            if (at.subtract(camera).dot(forward) <= NEAR_PLANE + p.size()) continue;
            vertex(out, matrix, point(at.subtract(a).subtract(b)), camera, 0, 1, alpha);
            vertex(out, matrix, point(at.subtract(a).add(b)), camera, 0, 0, alpha);
            vertex(out, matrix, point(at.add(a).add(b)), camera, 1, 0, alpha);
            vertex(out, matrix, point(at.add(a).subtract(b)), camera, 1, 1, alpha);
        }
    }
    private static void label(RenderLevelStageEvent event, Point mark, Box b, String name, Vec3 camera, Vec3 forward, boolean warning) {
        Minecraft mc = Minecraft.getInstance(); if (mc.screen != null || mc.options.hideGui) return;
        Vec3 at = new Vec3(mark.x(), b.maxY() + .16, mark.z()), relative = at.subtract(camera);
        if (!labelVisible(mc.player, camera, at)) return;
        var text = warning ? Component.translatable("academy.highpower.corner_exchange.warning", name)
                : Component.translatable("academy.highpower.corner_exchange.marked", name, (STATE.remainingTicks()+19)/20);
        float scale = CornerExchangeState.labelScale(relative.dot(forward), relative.length(), mc.font.width(text));
        if (scale <= 0) return;
        var poses = event.getPoseStack(); poses.pushPose();
        try {
            poses.translate(relative.x, relative.y, relative.z); poses.mulPose(event.getCamera().rotation()); poses.scale(scale, -scale, scale);
            mc.font.drawInBatch(text, -mc.font.width(text)/2f, 0, warning ? 0xFFFFFFFF : 0xFFE2E7E8, true,
                    poses.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 15728880);
        } finally { poses.popPose(); }
    }
    private static void corners(Matrix4f matrix, Box box, Vec3 camera, Vec3 forward, int alpha, boolean warning) {
        segments(matrix, CornerExchangeMesh.boxLines(box), camera, forward, alpha, warning);
        segments(matrix, SafeLandingProbeMesh.corners(box), camera, forward, Math.min(255, alpha+25), warning);
    }
    private static void segments(Matrix4f matrix, List<SafeLandingProbeMesh.Segment> lines, Vec3 camera, Vec3 forward, int alpha, boolean warning) {
        VertexConsumer out = buffers.getBuffer(warning ? Material.WARNING_CORNERS : Material.CORNERS);
        for (var line : lines) {
            Vec3 a = vec(CornerExchangeState.relative(line.from(), point(camera))), b = vec(CornerExchangeState.relative(line.to(), point(camera)));
            double da = a.dot(forward), db = b.dot(forward);
            if (da <= NEAR_PLANE && db <= NEAR_PLANE) continue;
            if (da < NEAR_PLANE) a = a.lerp(b, (NEAR_PLANE-da)/(db-da));
            else if (db < NEAR_PLANE) b = a.lerp(b, (NEAR_PLANE-da)/(db-da));
            Vec3 normal = b.subtract(a).normalize(); int gray = warning ? 255 : 220;
            out.addVertex(matrix,(float)a.x,(float)a.y,(float)a.z).setColor(gray,warning?255:225,warning?255:226,alpha)
                    .setNormal((float)normal.x,(float)normal.y,(float)normal.z);
            out.addVertex(matrix,(float)b.x,(float)b.y,(float)b.z).setColor(gray,warning?255:225,warning?255:226,alpha)
                    .setNormal((float)normal.x,(float)normal.y,(float)normal.z);
        }
    }
    /** Read-only mode preview before any payment; the server samples native facing again at commit. */
    @SubscribeEvent public static void guidance(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (mc.player == null || mc.screen != null || mc.options.hideGui || !AcademyClient.state.isActivated() || AcademyClient.skillKey(CornerExchange.ID) == null) return;
        var active = STATE.active();
        boolean visible = STATE.markerVisible() && locallyOwned(active, mc);
        var lines = new ArrayList<Component>();
        if (!visible) lines.add(Component.translatable(active == null ? "academy.highpower.corner_exchange.guide.place_a"
                : "academy.highpower.corner_exchange.guide.waiting"));
        else if (active.markB() == null) lines.add(Component.translatable("academy.highpower.corner_exchange.guide.place_b"));
        else {
            boolean reversed = active.phase() == CornerExchangeState.WARNING ? active.reversed()
                    : AcademyClient.cornerExchangeReversedPreview(mc.player.isShiftKeyDown());
            lines.add(Component.translatable(reversed ? "academy.highpower.corner_exchange.guide.reverse" : "academy.highpower.corner_exchange.guide.keep"));
            int near = CornerExchangeState.nearbyMark(active, point(mc.player.position()));
            lines.add(near == 0 ? Component.translatable("academy.highpower.corner_exchange.guide.proximity")
                    : Component.translatable("academy.highpower.corner_exchange.guide.direction", near == 1 ? "A" : "B", near == 1 ? "B" : "A"));
            lines.add(Component.translatable("academy.highpower.corner_exchange.guide.cost"));
        }
        lines.add(Component.translatable("academy.highpower.corner_exchange.guide.clear"));
        var graphics = event.getGuiGraphics();
        int width = CornerExchangeHudLayout.textWidth(graphics.guiWidth()); if (width == 0) return;
        var rows = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (var line : lines) rows.addAll(mc.font.split(line, width));
        var layout = CornerExchangeHudLayout.fit(graphics.guiWidth(), graphics.guiHeight(), rows.size(), mc.font.lineHeight);
        if (layout == null && lines.size() > 2) {
            // On small viewports preserve the control mode and free clear gesture before extra guidance.
            rows.clear(); rows.addAll(mc.font.split(lines.getFirst(), width)); rows.addAll(mc.font.split(lines.getLast(), width));
            layout = CornerExchangeHudLayout.fit(graphics.guiWidth(), graphics.guiHeight(), rows.size(), mc.font.lineHeight);
        }
        if (layout == null) return;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(graphics.guiWidth()/2f, layout.top(), 0);
            graphics.pose().scale(layout.scale(), layout.scale(), 1);
            int y = 0;
            for (var row : rows) {
                graphics.drawString(mc.font,row,-mc.font.width(row)/2,y,0xFFE2E7E8,true); y+=layout.lineStep();
            }
        } finally { graphics.pose().popPose(); }
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Point p, Vec3 camera, float u, float v, int alpha) {
        Point relative = CornerExchangeState.relative(p, point(camera));
        out.addVertex(matrix, (float)relative.x(), (float)relative.y(), (float)relative.z()).setUv(u, v).setColor(230, 240, 244, alpha);
    }
    private static Point point(Vec3 p) { return p == null ? null : new Point(p.x, p.y, p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private static Box box(AABB b) { return b == null ? null : new Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ); }
    private static AABB aabb(Box b) { return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()); }
    private static boolean sameController(CornerExchangeState.Frame a, CornerExchangeState.Frame b) {
        return a.id().entity() == b.id().entity() && a.id().owner().equals(b.id().owner())
                && a.id().epoch() == b.id().epoch() && a.id().token() == b.id().token();
    }
    /** Real depth occlusion and color-only writes; eight fixed textures, no world entities or resource creation. */
    private static final class Material extends RenderType {
        private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
        private Material(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean c, boolean sort, Runnable set, Runnable reset) {
            super(n, f, m, s, c, sort, set, reset);
        }
        static RenderType quads(ResourceLocation texture) {
            return TYPES.computeIfAbsent(texture, t -> create("academy_corner_exchange_" + t.getPath(), DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(t, false, false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
        private static RenderType lines(String name, double width) {
            return create(name, DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 2048, false, false,
                    CompositeState.builder().setShaderState(new ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                            .setLineState(new LineStateShard(OptionalDouble.of(width))).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                            .setOutputState(PARTICLES_TARGET).createCompositeState(false));
        }
        static final RenderType CORNERS = lines("academy_corner_exchange_corners", 2.25);
        static final RenderType WARNING_CORNERS = lines("academy_corner_exchange_warning", 3.5);
    }

    /** Read-only snapshot for the decoration layer (phase 0 = aiming/active, 1 = success, 2 = refusal/warning): points = source, destination, mark A, mark B; numbers = remaining ticks, reversed flag. */
    private static double[] dp(cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        var a = STATE.active();
        if (a != null) out.add(DecorSnap.of(true, 0, 0, a.id().token(), java.util.List.of(dp(a.source()), dp(a.destination()), dp(a.markA()), dp(a.markB())), a.remainingTicks(), a.reversed() ? 1 : 0));
        for (var c : new Cosmetic[] {success, observerWarning}) if (c != null && validOwner(c.owner, c.frame)) { var f = c.frame;
            out.add(DecorSnap.of(c.owner == mc.player, c.age, c == success ? 1 : 2, f.id().token(), java.util.List.of(dp(f.source()), dp(f.destination()), dp(f.markA()), dp(f.markB())), f.remainingTicks(), f.reversed() ? 1 : 0)); }
        return out;
    }
}

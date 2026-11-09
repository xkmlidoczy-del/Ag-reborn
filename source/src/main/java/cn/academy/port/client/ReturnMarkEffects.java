package cn.academy.port.client;

import cn.academy.port.highpower.ReturnMark;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import org.joml.Matrix4f;

/** NEW322 owner-only anchor, short coordinate warning, and confirmed native-move afterimages. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ReturnMarkEffects {
    private static final double CULL_DISTANCE = 64, NEAR_PLANE = .08;
    private static final ResourceLocation PARTICLE = texture("effects/tp_particle");
    private static final ResourceLocation[] MARKS = new ResourceLocation[PhaseSidestepMesh.SUCCESS_TICKS];
    private static final ReturnMarkState STATE = new ReturnMarkState();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static long generation;
    private static MultiBufferSource.BufferSource buffers;
    private static Cosmetic success, observerWarning;
    private static final class Cosmetic {
        final Entity owner;
        final ReturnMarkState.Frame frame;
        int age;
        Cosmetic(Entity owner, ReturnMarkState.Frame frame) { this.owner = owner; this.frame = frame; }
    }
    static { for (int i = 0; i < MARKS.length; i++) MARKS[i] = texture("effects/tp_mark/" + i); }
    private ReturnMarkEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { ReturnMark.clientReceiver = ReturnMarkEffects::receive; }
    }
    private static ReturnMarkState.Frame frame(ReturnMark.Visual p) {
        return new ReturnMarkState.Frame(new ReturnMarkState.Identity(p.entity(), p.owner(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), point(p.source()), point(p.destination()), box(p.bounds()), p.moved(), p.reason(), p.remainingTicks());
    }
    public static void receive(ReturnMark.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!ReturnMarkState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.entity());
            if (!(owner instanceof Player) || !owner.getUUID().equals(packet.owner()) || !owner.isAlive() || owner.isRemoved()) return;
            boolean local = owner == mc.player;
            if (!local && packet.phase() == ReturnMarkState.MARKED) return;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.returnMarkInputMatches(packet.input(), packet.epoch()))) return;
            if (local && !AcademyClient.returnMarkControlValid()) { AcademyClient.returnMarkCancelInput(); return; }
            if (!STATE.accept(receipt, local)) return;
            if (packet.phase() == ReturnMarkState.MARKED || packet.phase() == ReturnMarkState.WARNING) {
                // Four warning ticks are cosmetic; its terminal server receipt retains the full acknowledgement window.
                if (local) AcademyClient.returnMarkAcceptedInput(packet.input(), packet.epoch(), packet.phase() == ReturnMarkState.MARKED
                        ? packet.remainingTicks() : ReturnMarkInputState.ACK_TIMEOUT_TICKS);
                else if (observerWarning == null) {
                    observerWarning = new Cosmetic(owner, receipt);
                    observerWarning.age = ReturnMarkState.WARNING_TICKS - packet.remainingTicks();
                }
            } else {
                if (local) AcademyClient.returnMarkEndedInput(packet.input(), packet.epoch());
                if (observerWarning != null && sameController(observerWarning.frame, receipt)) observerWarning = null;
                if (packet.phase() == ReturnMarkState.SUCCESS) {
                    // One bounded endpoint pair globally, with local receipts taking priority over observers.
                    if (success == null || local || success.owner != mc.player) success = new Cosmetic(owner, receipt);
                } else if (packet.phase() == ReturnMarkState.REFUSED && local) {
                    String reason = "academy.highpower.return_mark.reason." + packet.reason();
                    var detail = net.minecraft.locale.Language.getInstance().has(reason) ? Component.translatable(reason)
                            : Component.translatable("academy.highpower.return_mark.refused");
                    mc.player.displayClientMessage(Component.translatable("academy.highpower.return_mark.blocked", detail), true);
                }
            }
        });
    }
    public static void abortLocal(long input, long epoch) { STATE.cancel(epoch, input); }
    public static void clear() { generation++; STATE.clear(); success = observerWarning = null; }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        if (STATE.active() != null) AcademyClient.returnMarkCancelInput();
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean validOwner(Entity owner, ReturnMarkState.Frame frame) {
        return world != null && owner != null && owner.isAlive() && !owner.isRemoved()
                && world.getEntity(frame.id().entity()) == owner && owner.getUUID().equals(frame.id().owner());
    }
    private static boolean locallyOwned(ReturnMarkState.Frame frame, Minecraft mc) {
        return frame != null && validOwner(mc.player, frame) && AcademyClient.singleOwnerEpochMatches(frame.id().epoch())
                && AcademyClient.returnMarkInputMatches(frame.id().input(), frame.id().epoch()) && AcademyClient.returnMarkControlValid()
                && loaded(frame.bounds());
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (STATE.markerVisible() && locallyOwned(STATE.active(), mc)) return STATE.active().phase() == ReturnMarkState.WARNING
                ? ClassicAbilityHud.DelegateVisual.CHARGE : ClassicAbilityHud.DelegateVisual.ACTIVE;
        return success != null && success.owner == mc.player && validOwner(success.owner, success.frame)
                ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (STATE.tick(mc.isPaused(), locallyOwned(STATE.active(), mc)) != null) AcademyClient.returnMarkCancelInput();
        if (success != null && (!validOwner(success.owner, success.frame)
                || !mc.isPaused() && ++success.age >= PhaseSidestepMesh.SUCCESS_TICKS)) success = null;
        if (observerWarning != null) {
            if (!validOwner(observerWarning.owner, observerWarning.frame) || !loaded(observerWarning.frame.bounds())) {
                STATE.retire(observerWarning.frame.id()); observerWarning = null;
            } else if (!mc.isPaused() && ++observerWarning.age >= ReturnMarkState.ACK_TIMEOUT_TICKS) {
                STATE.retire(observerWarning.frame.id()); observerWarning = null;
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
                boolean warning = active.phase() == ReturnMarkState.WARNING;
                if (!warning || STATE.warningAge() < ReturnMarkState.WARNING_TICKS) {
                    int alpha = warning ? (STATE.warningAge() % 2 == 0 ? 255 : 220) : 205;
                    if (visible(event, active.bounds(), camera)) {
                        corners(matrix, active.bounds(), camera, forward, alpha, warning);
                        label(event, active, camera, forward, warning);
                    }
                    if (warning) {
                        Box departure = PhaseSidestepMesh.sourceBounds(active.bounds(), active.source(), active.destination());
                        if (visible(event, departure, camera)) corners(matrix, departure, camera, forward, alpha, true);
                    }
                }
            }
            if (active == null && observerWarning != null && observerWarning.age < ReturnMarkState.WARNING_TICKS
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
        var f = success.frame; int alpha = PhaseSidestepMesh.alpha(success.age);
        Box departure = PhaseSidestepMesh.sourceBounds(f.bounds(), f.source(), f.destination());
        if (!loaded(departure) || !loaded(f.bounds()) || camera.distanceToSqr(vec(f.destination())) > CULL_DISTANCE * CULL_DISTANCE) return;
        for (Box bounds : List.of(departure, f.bounds())) if (visible(event, bounds, camera)) {
            corners(matrix, bounds, camera, forward, alpha, false);
            // A first-person camera inside an endpoint must not be filled by the hologram's inward faces.
            if (!aabb(bounds).inflate(.12).contains(camera)) {
                var out = buffers.getBuffer(Material.quads(MARKS[PhaseSidestepMesh.frame(success.age)]));
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
    private static void label(RenderLevelStageEvent event, ReturnMarkState.Frame frame, Vec3 camera, Vec3 forward, boolean warning) {
        Minecraft mc = Minecraft.getInstance(); if (mc.screen != null || mc.options.hideGui) return;
        Box b = frame.bounds(); Vec3 at = new Vec3((b.minX() + b.maxX()) / 2, b.maxY() + .16, (b.minZ() + b.maxZ()) / 2);
        Vec3 relative = at.subtract(camera);
        if (relative.dot(forward) <= NEAR_PLANE || !labelVisible(mc.player, camera, at)) return;
        String distance = String.format(Locale.ROOT, "%.1f m", mc.player.position().distanceTo(vec(frame.destination())));
        var text = warning ? Component.translatable("academy.highpower.return_mark.warning", distance)
                : Component.translatable("academy.highpower.return_mark.marked", distance, (STATE.remainingTicks() + 19) / 20);
        var poses = event.getPoseStack(); poses.pushPose();
        try {
            // A tiny physical label grows moderately with distance, remaining legible at the 24m return limit.
            float scale = (float)Math.min(.032, .019 + camera.distanceTo(at) * .00045);
            poses.translate(relative.x, relative.y, relative.z); poses.mulPose(event.getCamera().rotation()); poses.scale(scale, -scale, scale);
            mc.font.drawInBatch(text, -mc.font.width(text) / 2f, 0, warning ? 0xFFFFFFFF : 0xFFE2E7E8, true,
                    poses.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 15728880);
        } finally { poses.popPose(); }
    }
    private static void corners(Matrix4f matrix, Box box, Vec3 camera, Vec3 forward, int alpha, boolean warning) {
        VertexConsumer out = buffers.getBuffer(warning ? Material.WARNING_CORNERS : Material.CORNERS);
        for (var line : PhaseSidestepMesh.corners(box)) {
            Vec3 a = vec(ReturnMarkState.relative(line.from(), point(camera))), b = vec(ReturnMarkState.relative(line.to(), point(camera)));
            double da = a.dot(forward), db = b.dot(forward);
            if (da <= NEAR_PLANE && db <= NEAR_PLANE) continue;
            if (da < NEAR_PLANE) a = a.lerp(b, (NEAR_PLANE - da) / (db - da));
            else if (db < NEAR_PLANE) b = a.lerp(b, (NEAR_PLANE - da) / (db - da));
            Vec3 normal = b.subtract(a).normalize(); int gray = warning ? 255 : 220;
            out.addVertex(matrix, (float)a.x, (float)a.y, (float)a.z).setColor(gray, warning ? 255 : 225, warning ? 255 : 226, alpha)
                    .setNormal((float)normal.x, (float)normal.y, (float)normal.z);
            out.addVertex(matrix, (float)b.x, (float)b.y, (float)b.z).setColor(gray, warning ? 255 : 225, warning ? 255 : 226, alpha)
                    .setNormal((float)normal.x, (float)normal.y, (float)normal.z);
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Point p, Vec3 camera, float u, float v, int alpha) {
        Point relative = ReturnMarkState.relative(p, point(camera));
        out.addVertex(matrix, (float)relative.x(), (float)relative.y(), (float)relative.z()).setUv(u, v).setColor(230, 240, 244, alpha);
    }
    private static Point point(Vec3 p) { return p == null ? null : new Point(p.x, p.y, p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private static Box box(AABB b) { return b == null ? null : new Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ); }
    private static AABB aabb(Box b) { return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()); }
    private static boolean sameController(ReturnMarkState.Frame a, ReturnMarkState.Frame b) {
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
            return TYPES.computeIfAbsent(texture, t -> create("academy_return_mark_" + t.getPath(), DefaultVertexFormat.POSITION_TEX_COLOR,
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
        static final RenderType CORNERS = lines("academy_return_mark_corners", 2);
        static final RenderType WARNING_CORNERS = lines("academy_return_mark_warning", 3);
    }

    /** Read-only snapshot for the decoration layer (phase 0 = aiming/active, 1 = success, 2 = refusal/warning): points = source, destination; numbers = remaining ticks. */
    private static double[] dp(cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        var a = STATE.active();
        if (a != null) out.add(DecorSnap.of(true, 0, 0, a.id().token(), java.util.List.of(dp(a.source()), dp(a.destination())), a.remainingTicks()));
        for (var c : new Cosmetic[] {success, observerWarning}) if (c != null && validOwner(c.owner, c.frame))
            out.add(DecorSnap.of(c.owner == mc.player, c.age, c == success ? 1 : 2, c.frame.id().token(), java.util.List.of(dp(c.frame.source()), dp(c.frame.destination())), c.frame.remainingTicks()));
        return out;
    }
}

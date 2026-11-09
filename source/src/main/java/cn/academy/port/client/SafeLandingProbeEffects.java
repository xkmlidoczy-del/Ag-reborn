package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.HighPowerCategories;
import cn.academy.port.highpower.SafeLandingProbe;
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

/** NEW313 owner-only frozen preview and real-move receipts. No movement, damage, audio or classic motion path. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class SafeLandingProbeEffects {
    private static final ResourceLocation PARTICLE = texture("effects/tp_particle");
    private static final ResourceLocation[] MARKS = new ResourceLocation[SafeLandingProbeMesh.SUCCESS_TICKS];
    private static final SafeLandingProbeState STATE = new SafeLandingProbeState();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static long generation;
    private static MultiBufferSource.BufferSource buffers;
    private static Cosmetic success, refusal;
    private static final class Cosmetic {
        final Entity owner;
        final SafeLandingProbeState.Frame frame;
        int age;
        Cosmetic(Entity owner, SafeLandingProbeState.Frame frame) { this.owner = owner; this.frame = frame; }
    }
    static { for (int i = 0; i < MARKS.length; i++) MARKS[i] = texture("effects/tp_mark/" + i); }
    private SafeLandingProbeEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { SafeLandingProbe.clientReceiver = SafeLandingProbeEffects::receive; }
    }
    private static SafeLandingProbeState.Frame frame(SafeLandingProbe.Visual p) {
        return new SafeLandingProbeState.Frame(new SafeLandingProbeState.Identity(p.entity(), p.owner(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), point(p.source()), point(p.destination()), box(p.bounds()), p.valid(), p.reason(), p.remainingTicks());
    }
    public static void receive(SafeLandingProbe.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!SafeLandingProbeState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.entity());
            if (!(owner instanceof Player) || !owner.getUUID().equals(packet.owner()) || !owner.isAlive() || owner.isRemoved()) return;
            boolean local = owner == mc.player;
            if (!local && packet.phase() != SafeLandingProbeState.SUCCESS) return;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.safeLandingProbeInputMatches(packet.input(), packet.epoch()))) return;
            if (packet.phase() == SafeLandingProbeState.PREVIEW && !localEligible()) {
                AcademyClient.safeLandingProbeLocalVisualLost(packet.input(), packet.epoch()); return;
            }
            if (!STATE.accept(receipt, local)) return;
            if (packet.phase() == SafeLandingProbeState.PREVIEW) {
                AcademyClient.safeLandingProbePreviewInput(packet.input(), packet.epoch(), packet.remainingTicks());
                refusal = null;
            } else {
                if (local) AcademyClient.safeLandingProbeEndedInput(packet.input(), packet.epoch());
                if (packet.phase() == SafeLandingProbeState.SUCCESS) {
                    // One pair globally, and thirty-two particles at most. The local receipt has priority.
                    if (success == null || local || success.owner != mc.player) success = new Cosmetic(owner, receipt);
                    refusal = null;
                } else if (packet.phase() == SafeLandingProbeState.REFUSED && local) {
                    refusal = receipt.bounds() == null ? null : new Cosmetic(owner, receipt);
                    String reason = "academy.highpower.safe_landing_probe.reason." + packet.reason();
                    var detail = net.minecraft.locale.Language.getInstance().has(reason) ? Component.translatable(reason)
                            : Component.translatable("academy.highpower.safe_landing_probe.refused");
                    mc.player.displayClientMessage(Component.translatable("academy.highpower.safe_landing_probe.blocked", detail), true);
                }
            }
        });
    }
    public static void abortLocal(long input, long epoch) {
        STATE.cancel(epoch, input);
        if (refusal != null && refusal.frame.id().epoch() == epoch && refusal.frame.id().input() <= input) refusal = null;
    }
    public static void clear() { generation++; STATE.clear(); success = refusal = null; }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        var previous = STATE.preview();
        if (previous != null) AcademyClient.safeLandingProbeLocalVisualLost(previous.id().input(), previous.id().epoch());
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean localEligible() {
        Minecraft mc = Minecraft.getInstance(); var s = AcademyClient.state;
        return mc.player != null && mc.screen == null && mc.isWindowActive() && mc.getCameraEntity() == mc.player
                && mc.player.isAlive() && !mc.player.isRemoved() && !mc.player.isSpectator()
                && !mc.player.getAbilities().instabuild && !mc.player.getAbilities().invulnerable && !mc.player.noPhysics
                && !mc.player.isPassenger() && !mc.player.isSleeping() && mc.player.onGround()
                && !mc.player.isFallFlying() && !mc.player.isSwimming() && !mc.player.isInWater() && !mc.player.isInLava()
                && s != null && s.activated && s.overloadFine && !s.interfering && !s.consumptionInProgress()
                && HighPowerCategories.TELEPORTER.equals(s.category) && s.level >= 1 && s.learned(SafeLandingProbe.ID)
                && s.presets.currentContains(SafeLandingProbe.ID) && SkillCatalog.enabled(s.category, SafeLandingProbe.ID);
    }
    private static boolean validOwner(Entity owner, SafeLandingProbeState.Frame frame) {
        return world != null && owner != null && owner.isAlive() && !owner.isRemoved()
                && world.getEntity(frame.id().entity()) == owner && owner.getUUID().equals(frame.id().owner());
    }
    private static boolean previewOwned(SafeLandingProbeState.Frame preview, Minecraft mc) {
        return preview != null && validOwner(mc.player, preview) && localEligible()
                && AcademyClient.singleOwnerEpochMatches(preview.id().epoch())
                && AcademyClient.safeLandingProbeInputMatches(preview.id().input(), preview.id().epoch())
                && AcademyClient.safeLandingProbeControlValid();
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (previewOwned(STATE.preview(), mc)) return ClassicAbilityHud.DelegateVisual.CHARGE;
        return success != null && success.owner == mc.player && validOwner(success.owner, success.frame)
                ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var lost = STATE.tick(mc.isPaused(), previewOwned(STATE.preview(), mc));
        if (lost != null) AcademyClient.safeLandingProbeLocalVisualLost(lost.input(), lost.epoch());
        if (success != null && (!validOwner(success.owner, success.frame)
                || !mc.isPaused() && ++success.age >= SafeLandingProbeMesh.SUCCESS_TICKS)) success = null;
        if (refusal != null && (!validOwner(refusal.owner, refusal.frame)
                || !mc.isPaused() && ++refusal.age >= SafeLandingProbeMesh.SUCCESS_TICKS)) refusal = null;
    }
    /** No chunk loads. World buffers use LEQUAL and labels additionally require a real unobstructed path. */
    private static boolean loaded(Box bounds) {
        if (world == null || bounds == null || !bounds.body()) return false;
        for (int x = Math.floorDiv((int)Math.floor(bounds.minX()), 16); x <= Math.floorDiv((int)Math.floor(bounds.maxX()), 16); x++)
            for (int z = Math.floorDiv((int)Math.floor(bounds.minZ()), 16); z <= Math.floorDiv((int)Math.floor(bounds.maxZ()), 16); z++)
                if (!world.hasChunkAt(new BlockPos(x * 16, (int)Math.floor(bounds.minY()), z * 16))) return false;
        return true;
    }
    private static boolean labelVisible(Entity owner, Vec3 camera, Vec3 target) {
        if (world == null || camera.distanceToSqr(target) > SafeLandingProbeMesh.CULL_DISTANCE * SafeLandingProbeMesh.CULL_DISTANCE) return false;
        int steps = Math.max(1, (int)Math.ceil(camera.distanceTo(target) / 8));
        for (int i = 0; i <= steps; i++) if (!world.hasChunkAt(BlockPos.containing(camera.lerp(target, (double)i / steps)))) return false;
        var hit = world.clip(new ClipContext(camera, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); if (world == null) return;
        var preview = STATE.preview();
        if (preview == null && success == null && refusal == null) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        var poses = event.getPoseStack(); Vec3 camera = event.getCamera().getPosition();
        var modelView = RenderSystem.getModelViewStack(); float[] color = RenderSystem.getShaderColor().clone();
        modelView.pushMatrix(); poses.pushPose();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            poses.translate(-camera.x, -camera.y, -camera.z); Matrix4f matrix = poses.last().pose();
            if (previewOwned(preview, mc) && loaded(preview.bounds()) && camera.distanceToSqr(vec(preview.destination())) <= 64 * 64) {
                corners(matrix, preview.bounds(), 210);
                label(event, preview, mc.player, camera, true);
            }
            if (refusal != null && validOwner(refusal.owner, refusal.frame) && loaded(refusal.frame.bounds())) {
                corners(matrix, refusal.frame.bounds(), SafeLandingProbeMesh.alpha(refusal.age));
                label(event, refusal.frame, refusal.owner, camera, false);
            }
            if (success != null && validOwner(success.owner, success.frame)) {
                var f = success.frame; int alpha = SafeLandingProbeMesh.alpha(success.age);
                Box departure = SafeLandingProbeMesh.sourceBounds(f.bounds(), f.source(), f.destination());
                if (loaded(departure) && loaded(f.bounds()) && camera.distanceToSqr(vec(f.destination())) <= 64 * 64) {
                    var out = buffers.getBuffer(Material.quads(MARKS[SafeLandingProbeMesh.frame(success.age)]));
                    for (Box bounds : List.of(departure, f.bounds())) for (var quad : SafeLandingProbeMesh.biped(bounds))
                        for (var v : List.of(quad.a(), quad.b(), quad.c(), quad.d())) vertex(out, matrix, v.position(), v.u(), v.v(), alpha);
                    var right = event.getCamera().getLeftVector(); var up = event.getCamera().getUpVector();
                    Vec3 horizontal = new Vec3(right.x(), right.y(), right.z()), vertical = new Vec3(up.x(), up.y(), up.z());
                    out = buffers.getBuffer(Material.quads(PARTICLE));
                    for (var p : SafeLandingProbeMesh.particles(f.source(), f.destination(), success.age)) {
                        Vec3 at = vec(p.position()), a = horizontal.scale(p.size()), b = vertical.scale(p.size());
                        vertex(out, matrix, point(at.subtract(a).subtract(b)), 0, 1, alpha);
                        vertex(out, matrix, point(at.subtract(a).add(b)), 0, 0, alpha);
                        vertex(out, matrix, point(at.add(a).add(b)), 1, 0, alpha);
                        vertex(out, matrix, point(at.add(a).subtract(b)), 1, 1, alpha);
                    }
                }
            }
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }
    private static void label(RenderLevelStageEvent event, SafeLandingProbeState.Frame frame, Entity owner, Vec3 camera, boolean safe) {
        Minecraft mc = Minecraft.getInstance(); if (mc.screen != null || mc.options.hideGui || frame.bounds() == null) return;
        Box b = frame.bounds(); Vec3 at = new Vec3((b.minX() + b.maxX()) / 2, b.maxY() + .15, (b.minZ() + b.maxZ()) / 2);
        if (!labelVisible(owner, camera, at)) return;
        String distance = frame.source() == null || frame.destination() == null ? ""
                : String.format(Locale.ROOT, "%.1f m", Math.sqrt(frame.source().distanceSquared(frame.destination())));
        var text = Component.translatable(safe ? "academy.highpower.safe_landing_probe.safe" : "academy.highpower.safe_landing_probe.marker_blocked", distance);
        var poses = event.getPoseStack(); poses.pushPose();
        try {
            // Native 1.21.1 EntityRenderer name tags use +X/-Y/+Z after camera rotation.
            // Mirroring X also reverses the text face, so ordinary depth-tested glyphs disappear.
            poses.translate(at.x, at.y, at.z); poses.mulPose(event.getCamera().rotation()); poses.scale(.018f, -.018f, .018f);
            mc.font.drawInBatch(text, -mc.font.width(text) / 2f, 0, safe ? 0xFFD7EEE5 : 0xFFE9B7B7, true,
                    poses.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 15728880);
        } finally { poses.popPose(); }
    }
    private static void corners(Matrix4f matrix, Box box, int alpha) {
        VertexConsumer out = buffers.getBuffer(Material.CORNERS);
        for (var line : SafeLandingProbeMesh.corners(box)) {
            Vec3 a = vec(line.from()), b = vec(line.to()), normal = b.subtract(a).normalize();
            out.addVertex(matrix, (float)a.x, (float)a.y, (float)a.z).setColor(220, 225, 226, alpha)
                    .setNormal((float)normal.x, (float)normal.y, (float)normal.z);
            out.addVertex(matrix, (float)b.x, (float)b.y, (float)b.z).setColor(220, 225, 226, alpha)
                    .setNormal((float)normal.x, (float)normal.y, (float)normal.z);
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Point p, float u, float v, int alpha) {
        out.addVertex(matrix, (float)p.x(), (float)p.y(), (float)p.z()).setUv(u, v).setColor(230, 240, 244, alpha);
    }
    private static Point point(Vec3 p) { return p == null ? null : new Point(p.x, p.y, p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private static Box box(AABB b) { return b == null ? null : new Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ); }
    /** Scoped material: actual depth occlusion, color writes only, bounded texture cache and restored global state. */
    private static final class Material extends RenderType {
        private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
        private Material(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean c, boolean sort, Runnable set, Runnable reset) {
            super(n, f, m, s, c, sort, set, reset);
        }
        static RenderType quads(ResourceLocation texture) {
            return TYPES.computeIfAbsent(texture, t -> create("academy_safe_landing_" + t.getPath(), DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(t, false, false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
        static final RenderType CORNERS = create("academy_safe_landing_corners", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 2048, false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                        .setLineState(new LineStateShard(OptionalDouble.of(2))).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setOutputState(PARTICLES_TARGET).createCompositeState(false));
    }

    /** Read-only snapshot for the decoration layer (phase 0 = aiming/active, 1 = success, 2 = refusal/warning): points = source, destination; numbers = valid flag. */
    private static double[] dp(cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        var preview = STATE.preview();
        if (preview != null) out.add(DecorSnap.of(true, 0, 0, preview.id().token(), java.util.List.of(dp(preview.source()), dp(preview.destination())), preview.valid() ? 1 : 0));
        for (var c : new Cosmetic[] {success, refusal}) if (c != null && validOwner(c.owner, c.frame))
            out.add(DecorSnap.of(c.owner == mc.player, c.age, c == success ? 1 : 2, c.frame.id().token(), java.util.List.of(dp(c.frame.source()), dp(c.frame.destination())), c.frame.valid() ? 1 : 0));
        return out;
    }
}

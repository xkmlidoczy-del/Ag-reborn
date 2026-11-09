package cn.academy.port.client;

import cn.academy.port.highpower.CrossSubstitution;
import cn.academy.port.highpower.DisplacedNeedleEntity;
import cn.academy.port.highpower.TeleporterNeedleEntities;
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
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW321 gray-white coordinate marks and confirmed endpoints; only native entities render the needles. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class CrossSubstitutionEffects {
    private static final double CULL_DISTANCE = 64, NEAR_PLANE = .08;
    private static final ResourceLocation[] MARKS = new ResourceLocation[DisplacedProjectionMesh.SUCCESS_TICKS];
    private static final CrossSubstitutionState STATE = new CrossSubstitutionState();
    // Three bounded endpoint receipts, no fabricated flight path and zero cosmetic particles.
    private static final List<Cosmetic> cosmetics = new ArrayList<>(CrossSubstitutionState.MAX_NEEDLES);
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static long generation;
    private static MultiBufferSource.BufferSource buffers;
    private record Endpoint(Point point, int alpha, int atlasFrame) {}
    private static final class Cosmetic {
        final Entity owner;
        final CrossSubstitutionState.Frame frame;
        int age;
        Cosmetic(Entity owner, CrossSubstitutionState.Frame frame) { this.owner = owner; this.frame = frame; }
    }
    static { for (int i = 0; i < MARKS.length; i++) MARKS[i] = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/tp_mark/" + i + ".png"); }
    private CrossSubstitutionEffects() {}
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { CrossSubstitution.clientReceiver = CrossSubstitutionEffects::receive; }
    }
    private static CrossSubstitutionState.Frame frame(CrossSubstitution.Visual p) {
        return new CrossSubstitutionState.Frame(new CrossSubstitutionState.Identity(p.entity(), p.owner(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), p.prepared(), p.remaining(), p.prepared() >= 1 ? point(p.pointA()) : null,
                p.prepared() == 2 ? point(p.pointB()) : null, point(p.source()),
                point(p.arrival()), point(p.impact()), p.needleSlot(), p.needleEntity(), p.needleUuid(), p.accepted(), p.reason());
    }
    public static void receive(CrossSubstitution.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!CrossSubstitutionState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.entity());
            if (!(owner instanceof Player) || !owner.getUUID().equals(packet.owner()) || !owner.isAlive() || owner.isRemoved()) return;
            boolean local = owner == mc.player;
            if (!local && (packet.phase() == CrossSubstitutionState.MARKED || packet.phase() == CrossSubstitutionState.REFUSED
                    || packet.phase() == CrossSubstitutionState.SWAP_REFUSED)) return;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.crossSubstitutionInputMatches(packet.input(), packet.epoch()))) return;
            if (local && !AcademyClient.crossSubstitutionControlValid()) { AcademyClient.crossSubstitutionCancelInput(); return; }
            if (packet.phase() == CrossSubstitutionState.PROJECTED || packet.phase() == CrossSubstitutionState.IMPACT) {
                Entity needle = capturedWorld.getEntity(packet.needleEntity());
                if (needle != null && (!(needle instanceof DisplacedNeedleEntity)
                        || needle.getType() != TeleporterNeedleEntities.CROSS_NEEDLE.get()
                        || !needle.getUUID().equals(packet.needleUuid()))) return;
            }
            if (!STATE.accept(receipt, local)) return;
            if (local) {
                if (CrossSubstitutionState.terminal(receipt)) AcademyClient.crossSubstitutionEndedInput(packet.input(), packet.epoch());
                else AcademyClient.crossSubstitutionAcceptedInput(packet.input(), packet.epoch(), STATE.remainingTicks(),
                        packet.phase() == CrossSubstitutionState.PROJECTED || packet.phase() == CrossSubstitutionState.IMPACT);
            }
            if (packet.phase() == CrossSubstitutionState.PROJECTED
                    || packet.phase() == CrossSubstitutionState.SWAPPED
                    || packet.phase() == CrossSubstitutionState.IMPACT && packet.accepted()) addCosmetic(owner, receipt, local);
            if ((packet.phase() == CrossSubstitutionState.REFUSED || packet.phase() == CrossSubstitutionState.SWAP_REFUSED) && local) {
                String reason = "academy.highpower.cross_substitution.reason." + packet.reason();
                var detail = net.minecraft.locale.Language.getInstance().has(reason) ? Component.translatable(reason)
                        : Component.translatable("academy.highpower.cross_substitution.refused");
                mc.player.displayClientMessage(Component.translatable("academy.highpower.cross_substitution.blocked", detail), true);
            }
        });
    }
    private static void addCosmetic(Entity owner, CrossSubstitutionState.Frame frame, boolean local) {
        cosmetics.removeIf(c -> c.frame.id().owner().equals(frame.id().owner()) && c.frame.id().epoch() == frame.id().epoch()
                && c.frame.id().token() == frame.id().token() && c.frame.needleSlot() == frame.needleSlot());
        if (cosmetics.size() >= CrossSubstitutionState.MAX_NEEDLES) {
            var replace = cosmetics.stream().filter(c -> c.owner != Minecraft.getInstance().player).findFirst().orElse(null);
            if (replace != null) cosmetics.remove(replace);
            else if (local) cosmetics.removeFirst();
            else return;
        }
        cosmetics.add(new Cosmetic(owner, frame));
    }
    public static void abortLocal(long input, long epoch) {
        STATE.cancel(epoch, input);
        cosmetics.removeIf(c -> c.owner == Minecraft.getInstance().player && c.frame.id().epoch() == epoch && c.frame.id().input() <= input);
    }
    public static void clear() { generation++; STATE.clear(); cosmetics.clear(); }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        if (STATE.active() != null) AcademyClient.crossSubstitutionCancelInput();
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean validOwner(Entity owner, CrossSubstitutionState.Frame frame) {
        return world != null && owner != null && owner.isAlive() && !owner.isRemoved()
                && world.getEntity(frame.id().entity()) == owner && owner.getUUID().equals(frame.id().owner());
    }
    private static boolean locallyOwned(CrossSubstitutionState.Frame frame, Minecraft mc) {
        return frame != null && validOwner(mc.player, frame) && AcademyClient.singleOwnerEpochMatches(frame.id().epoch())
                && AcademyClient.crossSubstitutionInputMatches(frame.id().input(), frame.id().epoch()) && AcademyClient.crossSubstitutionControlValid();
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        return (STATE.markerVisible() || STATE.firing()) && locallyOwned(STATE.active(), mc)
                ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (STATE.tick(mc.isPaused(), locallyOwned(STATE.active(), mc)) != null) AcademyClient.crossSubstitutionCancelInput();
        cosmetics.removeIf(c -> !validOwner(c.owner, c.frame) || !mc.isPaused() && ++c.age >= DisplacedProjectionMesh.SUCCESS_TICKS);
    }
    /** Compact read-only guidance, sharing the marks' gray-white palette and bounded lifetime. */
    @SubscribeEvent public static void guidance(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var active = STATE.active();
        if (mc.player == null || mc.screen != null || mc.options.hideGui || !STATE.markerVisible() || !locallyOwned(active, mc)) return;
        var graphics = event.getGuiGraphics();
        var lines = new ArrayList<Component>();
        if (active.prepared() == 1) lines.add(Component.translatable("academy.highpower.cross_substitution.guide.place_b"));
        else {
            lines.add(Component.translatable("academy.highpower.cross_substitution.guide.modes"));
            if (mc.player.isShiftKeyDown()) {
                int near = CrossSubstitutionState.nearbyMark(active, point(mc.player.position()));
                lines.add(!mc.player.onGround() ? Component.translatable("academy.highpower.cross_substitution.reason.source_airborne")
                        : near == 0 ? Component.translatable("academy.highpower.cross_substitution.guide.proximity")
                        : Component.translatable("academy.highpower.cross_substitution.guide.direction", near == 1 ? "A" : "B", near == 1 ? "B" : "A"));
                lines.add(Component.translatable("academy.highpower.cross_substitution.guide.cost"));
            }
        }
        int width = Math.min(340, graphics.guiWidth() - 24);
        if (width < 160) return;
        var rows = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (Component line : lines) rows.addAll(mc.font.split(line, width));
        int height = rows.size() * (mc.font.lineHeight + 2), y = graphics.guiHeight() / 2 + 28;
        if (y + height > graphics.guiHeight() - 72) y = graphics.guiHeight() / 2 - 28 - height;
        if (y < 8 || y + height > graphics.guiHeight() - 72) return;
        for (var wrapped : rows) {
            graphics.drawString(mc.font, wrapped, (graphics.guiWidth() - mc.font.width(wrapped)) / 2, y, 0xFFE2E7E8, true);
            y += mc.font.lineHeight + 2;
        }
    }
    private static boolean loaded(Box bounds) {
        if (world == null || bounds == null || !bounds.body()) return false;
        for (int x = Math.floorDiv((int)Math.floor(bounds.minX()), 16); x <= Math.floorDiv((int)Math.floor(bounds.maxX()), 16); x++)
            for (int z = Math.floorDiv((int)Math.floor(bounds.minZ()), 16); z <= Math.floorDiv((int)Math.floor(bounds.maxZ()), 16); z++)
                if (!world.hasChunkAt(new BlockPos(x * 16, (int)Math.floor(bounds.minY()), z * 16))) return false;
        return true;
    }
    private static boolean visible(RenderLevelStageEvent event, Point point, Vec3 camera) {
        Box b = DisplacedProjectionMesh.bounds(point);
        return loaded(b) && camera.distanceToSqr(vec(point)) <= CULL_DISTANCE * CULL_DISTANCE && event.getFrustum().isVisible(aabb(b));
    }
    private static boolean labelVisible(Vec3 camera, Vec3 target) {
        if (world == null || camera.distanceToSqr(target) > CULL_DISTANCE * CULL_DISTANCE) return false;
        int steps = Math.max(1, (int)Math.ceil(camera.distanceTo(target) / 8));
        for (int i = 0; i <= steps; i++) if (!world.hasChunkAt(BlockPos.containing(camera.lerp(target, (double)i / steps)))) return false;
        return world.clip(new ClipContext(camera, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Minecraft.getInstance().player)).getType() == HitResult.Type.MISS;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var active = STATE.active(); if (world == null || active == null && cosmetics.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        var poses = event.getPoseStack(); Vec3 camera = event.getCamera().getPosition();
        var direction = event.getCamera().getLookVector(); Vec3 forward = new Vec3(direction.x(), direction.y(), direction.z());
        var modelView = RenderSystem.getModelViewStack(); float[] color = RenderSystem.getShaderColor().clone();
        modelView.pushMatrix(); poses.pushPose();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = poses.last().pose();
            boolean marked = STATE.markerVisible() && locallyOwned(active, mc);
            var endpoints = new ArrayList<Endpoint>(8);
            if (marked) {
                if (visible(event, active.pointA(), camera)) endpoints.add(new Endpoint(active.pointA(), 205, 0));
                if (active.prepared() == 2 && visible(event, active.pointB(), camera)) endpoints.add(new Endpoint(active.pointB(), 205, 0));
            }
            for (var cosmetic : cosmetics) if (validOwner(cosmetic.owner, cosmetic.frame)) {
                var f = cosmetic.frame; int alpha = DisplacedProjectionMesh.alpha(cosmetic.age);
                // Native contact alone draws a hit; a confirmed swap draws only its actual endpoints.
                var points = f.phase() == CrossSubstitutionState.IMPACT ? List.of(f.impact()) : List.of(f.source(), f.arrival());
                for (Point endpoint : points) if (visible(event, endpoint, camera))
                    endpoints.add(new Endpoint(endpoint, alpha, DisplacedProjectionMesh.frame(cosmetic.age)));
            }
            // All corner strokes share one line batch; atlas bands are grouped by their seven bounded frames.
            for (var endpoint : endpoints) corners(matrix, endpoint.point(), camera, forward, endpoint.alpha());
            for (int atlasFrame = 0; atlasFrame < MARKS.length; atlasFrame++) {
                VertexConsumer out = null;
                for (var endpoint : endpoints) if (endpoint.atlasFrame() == atlasFrame) {
                    if (out == null) out = buffers.getBuffer(Material.quads(MARKS[atlasFrame]));
                    for (var segment : DisplacedProjectionMesh.corners(endpoint.point()))
                        stroke(out, matrix, segment.from(), segment.to(), camera, forward, Math.min(endpoint.alpha(), 150));
                }
            }
            if (marked) {
                if (visible(event, active.pointA(), camera)) label(event, active.pointA(), "A", camera, forward);
                if (active.prepared() == 2 && visible(event, active.pointB(), camera)) label(event, active.pointB(), "B", camera, forward);
            }
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }
    private static void label(RenderLevelStageEvent event, Point point, String name, Vec3 camera, Vec3 forward) {
        Minecraft mc = Minecraft.getInstance(); if (mc.screen != null || mc.options.hideGui) return;
        Vec3 at = vec(point).add(0, .34, 0), relative = at.subtract(camera);
        if (!labelVisible(camera, at)) return;
        var text = Component.translatable("academy.highpower.cross_substitution.marked", name, (STATE.remainingTicks() + 19) / 20);
        float scale = CrossSubstitutionState.labelScale(relative.dot(forward), relative.length(), mc.font.width(text));
        if (scale <= 0) return;
        var poses = event.getPoseStack(); poses.pushPose();
        try {
            poses.translate(relative.x, relative.y, relative.z); poses.mulPose(event.getCamera().rotation()); poses.scale(scale, -scale, scale);
            mc.font.drawInBatch(text, -mc.font.width(text) / 2f, 0, 0xFFE2E7E8, true,
                    poses.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 15728880);
        } finally { poses.popPose(); }
    }
    private static void corners(Matrix4f matrix, Point endpoint, Vec3 camera, Vec3 forward, int alpha) {
        VertexConsumer out = buffers.getBuffer(Material.CORNERS);
        for (var line : DisplacedProjectionMesh.corners(endpoint)) {
            Vec3 a = vec(CrossSubstitutionState.relative(line.from(), point(camera))), b = vec(CrossSubstitutionState.relative(line.to(), point(camera)));
            double da = a.dot(forward), db = b.dot(forward);
            if (da <= NEAR_PLANE && db <= NEAR_PLANE) continue;
            if (da < NEAR_PLANE) a = a.lerp(b, (NEAR_PLANE - da) / (db - da));
            else if (db < NEAR_PLANE) b = a.lerp(b, (NEAR_PLANE - da) / (db - da));
            Vec3 normal = b.subtract(a).normalize();
            out.addVertex(matrix, (float)a.x, (float)a.y, (float)a.z).setColor(220, 225, 226, alpha).setNormal((float)normal.x, (float)normal.y, (float)normal.z);
            out.addVertex(matrix, (float)b.x, (float)b.y, (float)b.z).setColor(220, 225, 226, alpha).setNormal((float)normal.x, (float)normal.y, (float)normal.z);
        }
    }
    private static void stroke(VertexConsumer out, Matrix4f matrix, Point from, Point to, Vec3 camera, Vec3 forward, int alpha) {
        Vec3 a = vec(from), b = vec(to), side = b.subtract(a).cross(camera.subtract(a)).normalize().scale(.009);
        if (side.lengthSqr() < 1e-10 || a.subtract(camera).dot(forward) <= NEAR_PLANE + .009 || b.subtract(camera).dot(forward) <= NEAR_PLANE + .009) return;
        vertex(out, matrix, point(a.subtract(side)), camera, .03125f, .25f, alpha);
        vertex(out, matrix, point(a.add(side)), camera, .03125f, .46875f, alpha);
        vertex(out, matrix, point(b.add(side)), camera, .484375f, .46875f, alpha);
        vertex(out, matrix, point(b.subtract(side)), camera, .484375f, .25f, alpha);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Point p, Vec3 camera, float u, float v, int alpha) {
        Point relative = CrossSubstitutionState.relative(p, point(camera));
        out.addVertex(matrix, (float)relative.x(), (float)relative.y(), (float)relative.z()).setUv(u, v).setColor(230, 240, 244, alpha);
    }
    private static Point point(Vec3 p) { return p == null ? null : new Point(p.x, p.y, p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private static AABB aabb(Box b) { return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()); }
    private static final class Material extends RenderType {
        private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
        private Material(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean c, boolean sort, Runnable set, Runnable reset) { super(n, f, m, s, c, sort, set, reset); }
        static RenderType quads(ResourceLocation texture) {
            return TYPES.computeIfAbsent(texture, t -> create("academy_cross_substitution_" + t.getPath(), DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(t, false, false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
        static final RenderType CORNERS = create("academy_cross_substitution_corners", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 2048, false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                        .setLineState(new LineStateShard(OptionalDouble.of(2))).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setOutputState(PARTICLES_TARGET).createCompositeState(false));
    }

    /** Read-only snapshot for the decoration layer (phase 0 = aiming/active, 1 = success, 2 = refusal/warning): points = point A, point B, source, arrival, impact; numbers = prepared, remaining. */
    private static double[] dp(cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        var a = STATE.active();
        if (a != null) out.add(DecorSnap.of(true, 0, 0, a.id().token(), java.util.List.of(dp(a.pointA()), dp(a.pointB()), dp(a.source()), dp(a.arrival()), dp(a.impact())), a.prepared(), a.remaining()));
        for (var c : cosmetics) if (validOwner(c.owner, c.frame)) { var f = c.frame;
            out.add(DecorSnap.of(c.owner == mc.player, c.age, 1, f.id().token(), java.util.List.of(dp(f.pointA()), dp(f.pointB()), dp(f.source()), dp(f.arrival()), dp(f.impact())), f.prepared(), f.remaining())); }
        return out;
    }
}

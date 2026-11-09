package cn.academy.port.client;

import cn.academy.port.highpower.VectorParry;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW412 original white/gray Vector artwork. Cosmetics never query or alter a projectile or terrain. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class VectorParryEffects {
    private static final ResourceLocation GLOW_LINE = texture("glow_line"), GLOW_CIRCLE = texture("glow_circle");
    private static final VectorParryState STATE = new VectorParryState();
    private record Binding(Entity owner, VectorParryState.Identity id) {}
    private static final Map<UUID, Binding> OWNERS = new HashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static ByteBufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private VectorParryEffects() {}
    private static ResourceLocation texture(String name) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/" + name + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { VectorParry.clientReceiver = VectorParryEffects::receive; }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> Minecraft.getInstance().execute(() -> {
                AcademyClient.cancelVectorParryForReload(); clear();
                if (buffers != null) buffers.endBatch();
                if (storage != null) storage.close();
                buffers = null; storage = null; Material.TYPES.clear();
            }));
        }
    }
    private static VectorParryState.Frame frame(VectorParry.Visual p) {
        return new VectorParryState.Frame(new VectorParryState.Identity(p.entity(), p.owner(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), point(p.point()), point(p.facing()), point(p.impact()), point(p.incoming()), point(p.turn()),
                p.preciseTicks(), p.remainingHits());
    }
    public static void receive(VectorParry.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!VectorParryState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.entity());
            if (!(owner instanceof Player) || !owner.getUUID().equals(packet.owner())) return;
            Binding binding = OWNERS.get(packet.owner());
            if (binding != null && binding.id.equals(receipt.id()) && binding.owner != owner) return;
            boolean local = owner == mc.player;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.vectorParryInputMatches(packet.input(), packet.epoch()))) return;
            if (packet.phase() != VectorParryState.ENDED && (!owner.isAlive() || owner.isRemoved() || owner.isSpectator()
                    || !bodyPositionMatches(owner, receipt) || local && !AcademyClient.vectorParryControlValid())) return;
            if (!STATE.accept(receipt, local)) return;
            if (packet.phase() == VectorParryState.ENDED) {
                OWNERS.remove(packet.owner());
                if (local) AcademyClient.vectorParryEndedInput(packet.input(), packet.epoch());
            } else {
                OWNERS.put(packet.owner(), new Binding(owner, receipt.id()));
                if (local) AcademyClient.vectorParryAcceptedInput(packet.input(), packet.epoch());
            }
        });
    }
    public static void cancelLocal(long input, long epoch) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); STATE.cancel(epoch, input);
        OWNERS.entrySet().removeIf(entry -> STATE.current(entry.getKey()) == null);
    }
    public static void abortLocal(long input, long epoch) { cancelLocal(input, epoch); }
    public static void clear() { generation++; STATE.clear(); OWNERS.clear(); }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean bodyPositionMatches(Entity owner, VectorParryState.Frame receipt) {
        return owner.position().distanceToSqr(vec(receipt.point())) <= 16;
    }
    private static boolean validOwner(VectorParryState.Controller controller, Minecraft mc) {
        if (controller == null || world == null) return false;
        Binding binding = OWNERS.get(controller.id().owner());
        if (binding == null || !binding.id.equals(controller.id())) return false;
        Entity owner = binding.owner;
        return owner instanceof Player && world.getEntity(controller.id().entity()) == owner && owner.isAlive()
                && !owner.isRemoved() && !owner.isSpectator() && owner.getUUID().equals(controller.id().owner())
                && (!controller.local() || owner == mc.player && AcademyClient.singleOwnerEpochMatches(controller.id().epoch())
                && AcademyClient.vectorParryInputMatches(controller.id().input(), controller.id().epoch())
                && AcademyClient.vectorParryControlValid());
    }
    public static boolean activeFor(UUID owner) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var controller = owner == null ? null : STATE.current(owner);
        return controller != null && controller.quiet() < VectorParryState.QUIET_TICKS && validOwner(controller, mc)
                && bodyPositionMatches(OWNERS.get(owner).owner, controller.frame());
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        return mc.player != null && activeFor(mc.player.getUUID()) ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    public static boolean activeLocal() { return localVisual() == ClassicAbilityHud.DelegateVisual.ACTIVE; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        List<VectorParryState.Identity> local = STATE.controllers().stream().filter(VectorParryState.Controller::local)
                .map(VectorParryState.Controller::id).toList();
        var ended = STATE.tick(mc.isPaused(), controller -> validOwner(controller, mc));
        for (var id : ended) {
            Binding binding = OWNERS.get(id.owner());
            if (STATE.current(id.owner()) == null && binding != null && binding.id.equals(id)) OWNERS.remove(id.owner());
            if (local.contains(id)) AcademyClient.vectorParryLocalVisualLost(id.input(), id.epoch());
        }
    }
    /** Fixed limits explain the held mode without presenting an old per-tick quota as a live counter. */
    @SubscribeEvent public static void guidance(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (mc.screen != null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || !activeLocal()) return;
        var graphics = event.getGuiGraphics();
        int width = CornerExchangeHudLayout.textWidth(graphics.guiWidth()); if (width == 0) return;
        var rows = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (String key : List.of("academy.highpower.vector_parry.guide.defense", "academy.highpower.vector_parry.guide.precision"))
            rows.addAll(mc.font.split(Component.translatable(key), width));
        var layout = CornerExchangeHudLayout.fit(graphics.guiWidth(), graphics.guiHeight(), rows.size(), mc.font.lineHeight);
        if (layout == null) return;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(graphics.guiWidth() / 2f, layout.top(), 0);
            graphics.pose().scale(layout.scale(), layout.scale(), 1);
            int y = 0;
            for (var row : rows) { graphics.drawString(mc.font, row, -mc.font.width(row) / 2, y, 0xFFDADCDD, true); y += layout.lineStep(); }
        } finally { graphics.pose().popPose(); }
    }
    private static boolean visible(VectorParryState.Controller controller, RenderLevelStageEvent event, Vec3 camera) {
        if (controller.quiet() >= VectorParryState.QUIET_TICKS || !bodyPositionMatches(OWNERS.get(controller.id().owner()).owner, controller.frame())) return false;
        Vec3 feet = vec(controller.frame().point());
        if (camera.distanceToSqr(feet) > VectorParryGeometry.CULL_DISTANCE * VectorParryGeometry.CULL_DISTANCE) return false;
        AABB bounds = new AABB(feet, feet.add(0, 2, 0)).inflate(2);
        for (int x = Math.floorDiv((int)Math.floor(bounds.minX), 16); x <= Math.floorDiv((int)Math.floor(bounds.maxX), 16); x++)
            for (int z = Math.floorDiv((int)Math.floor(bounds.minZ), 16); z <= Math.floorDiv((int)Math.floor(bounds.maxZ), 16); z++)
                if (!world.hasChunkAt(new BlockPos(x * 16, (int)Math.floor(feet.y), z * 16))) return false;
        return event.getFrustum().isVisible(bounds);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); if (world == null || STATE.size() == 0) return;
        Vec3 camera = event.getCamera().getPosition(); Point eye = point(camera);
        var visible = STATE.controllers().stream().filter(c -> validOwner(c, mc) && visible(c, event, camera))
                .sorted(Comparator.<VectorParryState.Controller>comparingInt(c -> c.local() ? 0 : 1)
                        .thenComparingDouble(c -> c.frame().point().distanceSquared(eye)).thenComparingInt(c -> c.id().entity()))
                .limit(VectorParryGeometry.MAX_CONTROLLERS).toList();
        if (visible.isEmpty()) return;
        double partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var shapes = visible.stream().map(c -> VectorParryGeometry.shape(c, partial))
                .filter(s -> s.vertexCount() <= VectorParryGeometry.MAX_CONTROLLER_VERTICES).toList();
        if (buffers == null) { storage = new ByteBufferBuilder(32768); buffers = MultiBufferSource.immediate(storage); }
        var look = event.getCamera().getLookVector(); Point forward = new Point(look.x(), look.y(), look.z());
        var left = event.getCamera().getLeftVector(); Vec3 fallback = new Vec3(left.x(), left.y(), left.z());
        var budget = new VectorParryGeometry.Budget();
        var poses = event.getPoseStack(); var modelView = RenderSystem.getModelViewStack();
        float[] color = RenderSystem.getShaderColor().clone(); poses.pushPose(); modelView.pushMatrix();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = poses.last().pose();
            for (Pass pass : new Pass[]{Pass.SHOULDER, Pass.CORE}) {
                VertexConsumer out = buffers.getBuffer(Material.quads(pass));
                for (var shape : shapes) for (var stroke : shape.strokes())
                    stroke(out, matrix, stroke, VectorParryGeometry.profile(stroke, pass == Pass.CORE), eye, forward, fallback, budget);
                buffers.endBatch(Material.quads(pass));
            }
            VertexConsumer out = buffers.getBuffer(Material.quads(Pass.ABSORPTION));
            for (var shape : shapes) for (var ring : shape.rings()) {
                if (!VectorParryGeometry.ringInFront(ring, eye, forward) || ring.alpha() <= 0 || !budget.take(4)) continue;
                vertex(out, matrix, vec(VectorParryGeometry.relative(ring.a(), eye)), 0, 1, 226, ring.alpha());
                vertex(out, matrix, vec(VectorParryGeometry.relative(ring.b(), eye)), 0, 0, 226, ring.alpha());
                vertex(out, matrix, vec(VectorParryGeometry.relative(ring.c(), eye)), 1, 0, 226, ring.alpha());
                vertex(out, matrix, vec(VectorParryGeometry.relative(ring.d(), eye)), 1, 1, 226, ring.alpha());
            }
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }
    private static void stroke(VertexConsumer out, Matrix4f matrix, VectorParryGeometry.Stroke stroke,
                               VectorParryGeometry.Profile profile, Point camera, Point forward, Vec3 fallback, VectorParryGeometry.Budget budget) {
        if (profile.alpha() <= 0) return;
        var clipped = VectorParryGeometry.clipNear(new VectorParryGeometry.Stroke(
                VectorParryGeometry.relative(stroke.from(), camera), VectorParryGeometry.relative(stroke.to(), camera),
                profile.width(), profile.alpha(), stroke.role()), forward, profile.width() / 2);
        if (clipped == null) return;
        Vec3 a = vec(clipped.from()), b = vec(clipped.to());
        Vec3 side = b.subtract(a).cross(a.add(b).scale(-.5)).normalize();
        if (side.lengthSqr() < 1e-10) side = fallback;
        side = side.scale(profile.width() / 2);
        if (!budget.take(4)) return;
        vertex(out, matrix, a.subtract(side), 0, profile.maxV(), profile.gray(), profile.alpha());
        vertex(out, matrix, a.add(side), 0, profile.minV(), profile.gray(), profile.alpha());
        vertex(out, matrix, b.add(side), 1, profile.minV(), profile.gray(), profile.alpha());
        vertex(out, matrix, b.subtract(side), 1, profile.maxV(), profile.gray(), profile.alpha());
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 p, float u, float v, int gray, int alpha) {
        out.addVertex(matrix, (float)p.x, (float)p.y, (float)p.z).setUv(u, v).setColor(gray, gray, gray, alpha);
    }
    private static Point point(Vec3 p) { return p == null ? null : new Point(p.x, p.y, p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private enum Pass { SHOULDER, CORE, ABSORPTION }
    /** Original assets, shared nearest texture flags, depth test, color-only writes and no custom shader. */
    private static final class Material extends RenderType {
        private static final Map<Pass, RenderType> TYPES = new HashMap<>();
        private Material(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling,
                         boolean sorted, Runnable set, Runnable reset) { super(name, format, mode, size, crumbling, sorted, set, reset); }
        static RenderType quads(Pass pass) {
            return TYPES.computeIfAbsent(pass, p -> create("academy_vector_parry_" + p, DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(p == Pass.ABSORPTION ? GLOW_CIRCLE : GLOW_LINE, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
    }

    /** Read-only snapshot: points = body, facing, impact, incoming, turn; numbers = precise, 0, contact age (-1 none), animation tick. */
    private static double[] dp(Point p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var out = new java.util.ArrayList<DecorSnap>();
        for (var c : STATE.controllers()) {
            if (world == null || !validOwner(c, mc)) continue;
            var contact = c.turn() != null ? c.turn() : c.absorption();
            int age = c.turn() != null ? c.turnAge() : c.absorptionAge();
            var f = c.frame();
            var points = new java.util.ArrayList<double[]>(java.util.List.of(dp(f.point()), dp(f.facing())));
            if (contact != null) { points.add(dp(contact.impact())); points.add(dp(contact.incoming())); points.add(dp(contact.turn())); }
            out.add(DecorSnap.of(c.local(), c.animationTick(), f.phase(), c.id().token(), points, c.preciseTicks() > 0 ? 1 : 0, 0, contact == null ? -1 : age, c.animationTick()));
        }
        return out;
    }
}

package cn.academy.port.client;

import cn.academy.port.highpower.MultiAxisReflection;
import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import cn.academy.port.client.MultiAxisReflectionMesh.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;

/** NEW442 finite gray-white pressure planes and native-position reflection receipts. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MultiAxisReflectionEffects {
    private static final ResourceLocation GLOW_LINE = texture("effects/glow_line"), WHITE = texture("port/white");
    private static final MultiAxisReflectionState STATE = new MultiAxisReflectionState();
    private record Binding(Entity owner, MultiAxisReflectionState.Identity id) {}
    private static final Map<UUID, Binding> OWNERS = new HashMap<>();
    private record ArrowBinding(MultiAxisReflectionState.Identity owner, MultiAxisReflectionState.Arrow arrow) {}
    private static final Map<ArrowBinding, Entity> ARROWS = new HashMap<>();
    private static Boolean enhancedMaterial;
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static ByteBufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private MultiAxisReflectionEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            MultiAxisReflection.clientReceiver = MultiAxisReflectionEffects::receive;
            MultiAxisReflection.clientRejectionReceiver = MultiAxisReflectionEffects::reject;
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> Minecraft.getInstance().execute(() -> {
                AcademyClient.cancelMultiAxisReflectionForReload(); clear();
                if (buffers != null) buffers.endBatch();
                if (storage != null) storage.close();
                buffers = null; storage = null; enhancedMaterial = null; Material.TYPES.clear();
            }));
        }
    }
    private static MultiAxisReflectionState.Frame frame(MultiAxisReflection.Visual p) {
        return new MultiAxisReflectionState.Frame(new MultiAxisReflectionState.Identity(p.entity(), p.owner(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), point(p.point()), point(p.facing()), p.remainingHits(), p.liveMask(),
                p.facet(), p.arrowEntity(), p.arrowUuid(), point(p.arrowPoint()), p.available(), p.capacity());
    }
    public static void receive(MultiAxisReflection.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!MultiAxisReflectionState.valid(receipt)) return;
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
                    || !AcademyClient.multiAxisReflectionInputMatches(packet.input(), packet.epoch()))) return;
            if (packet.phase() != MultiAxisReflectionState.ENDED && (!owner.isAlive() || owner.isRemoved() || owner.isSpectator()
                    || owner.getBoundingBox().getCenter().distanceToSqr(vec(receipt.point())) > 16
                    || local && !AcademyClient.multiAxisReflectionControlValid())) return;
            if (!STATE.accept(receipt, local)) return;
            OWNERS.entrySet().removeIf(entry -> STATE.current(entry.getKey()) == null);
            if (packet.phase() == MultiAxisReflectionState.ENDED) {
                if (local) AcademyClient.multiAxisReflectionEndedInput(packet.input(), packet.epoch());
            } else {
                OWNERS.put(packet.owner(), new Binding(owner, receipt.id()));
                if (local) AcademyClient.multiAxisReflectionAcceptedInput(packet.input(), packet.epoch());
                if (packet.phase() == MultiAxisReflectionState.REFLECT) {
                    Entity arrow = capturedWorld.getEntity(packet.arrowEntity());
                    if (arrow instanceof AbstractArrow && !arrow.isRemoved() && arrow.getUUID().equals(packet.arrowUuid()))
                        ARROWS.putIfAbsent(new ArrowBinding(receipt.id(), receipt.arrow()), arrow);
                    else STATE.forgetArrow(packet.owner(), receipt.arrow());
                }
            }
        });
    }
    private static void reject(MultiAxisReflection.Rejection packet) {
        if (packet == null || packet.owner() == null || packet.entity() < 0 || packet.input() <= 0 || packet.epoch() <= 0) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || capturedPlayer == null || mc.level != capturedWorld || mc.player != capturedPlayer
                    || mc.getConnection() != capturedConnection || generation != capturedGeneration
                    || !AcademyClient.sameInputSessionEpoch(capturedSession) || packet.entity() != capturedPlayer.getId()
                    || !packet.owner().equals(capturedPlayer.getUUID()) || !AcademyClient.singleOwnerEpochMatches(packet.epoch())) return;
            AcademyClient.multiAxisReflectionRejectedInput(packet.input(), packet.epoch());
        });
    }
    public static void cancelLocal(long input, long epoch) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); STATE.cancel(epoch, input);
        OWNERS.entrySet().removeIf(entry -> STATE.current(entry.getKey()) == null);
    }
    public static void abortLocal(long input, long epoch) { cancelLocal(input, epoch); }
    public static void clear() { generation++; STATE.clear(); OWNERS.clear(); ARROWS.clear(); }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean validOwner(MultiAxisReflectionState.Controller controller, Minecraft mc) {
        if (controller == null || world == null) return false;
        Binding binding = OWNERS.get(controller.id().owner());
        if (binding == null || !binding.id.equals(controller.id())) return false;
        Entity owner = binding.owner;
        return owner instanceof Player && world.getEntity(controller.id().entity()) == owner && owner.isAlive()
                && !owner.isRemoved() && !owner.isSpectator() && owner.getUUID().equals(controller.id().owner())
                && (!controller.local() || owner == mc.player && AcademyClient.singleOwnerEpochMatches(controller.id().epoch())
                && AcademyClient.multiAxisReflectionInputMatches(controller.id().input(), controller.id().epoch())
                && AcademyClient.multiAxisReflectionControlValid());
    }
    public static boolean activeFor(UUID owner) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var controller = owner == null ? null : STATE.current(owner);
        return controller != null && controller.quiet() < MultiAxisReflectionState.QUIET_TICKS && validOwner(controller, mc);
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        return mc.player != null && activeFor(mc.player.getUUID()) ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    public static boolean activeLocal() { return localVisual() == ClassicAbilityHud.DelegateVisual.ACTIVE; }
    /** Counts and facet availability always describe the latest server receipt, never a predicted refund. */
    public static String localHint() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var controller = mc.player == null ? null : STATE.current(mc.player.getUUID());
        if (controller == null || !validOwner(controller, mc) || controller.quiet() >= MultiAxisReflectionState.QUIET_TICKS) return "";
        String key = controller.frame().phase() == MultiAxisReflectionState.STARTUP ? "forming"
                : controller.frame().remainingHits() == 0 ? "quota_unavailable"
                : controller.frame().liveMask() == 0 ? "recovering" : "ready";
        String fallback = switch (key) {
            case "forming" -> "forming"; case "quota_unavailable" -> "quota unavailable";
            case "recovering" -> "facets recovering"; default -> "facets ready";
        };
        String condition = Component.translatableWithFallback("hud.academy.highpower.multi_axis_reflection." + key, fallback).getString();
        return net.minecraft.network.chat.Component.translatableWithFallback("hud.academy.highpower.multi_axis_reflection",
                "Reflection %s/4 | %s", controller.frame().remainingHits(), condition).getString();
    }
    /** This skill's real base+lower balance is separate from the generic base-only shield instrument. */
    @SubscribeEvent public static void resourceHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (mc.player == null || mc.level == null || mc.getConnection() == null || mc.options.hideGui || mc.screen != null
                || mc.getCameraEntity() != mc.player || mc.getDebugOverlay().showDebugScreen()) return;
        var c = STATE.current(mc.player.getUUID());
        if (c == null || !validOwner(c, mc) || c.quiet() >= MultiAxisReflectionState.QUIET_TICKS) return;
        var graphics = event.getGuiGraphics();
        int width = Math.min(370, graphics.guiWidth() - 16); if (width <= 0) return;
        String language = mc.getLanguageManager().getSelected();
        var locale = HighPowerRecoveryHudModel.hudNumberLocale(language,
                (key, fallback) -> Component.translatableWithFallback("hud.academy.highpower." + key, fallback).getString());
        String pair = Screen.hasAltDown() ? c.frame().available() + " / " + c.frame().capacity()
                : HighPowerRecoveryHudModel.pool(c.frame().available(), c.frame().capacity(), locale).text();
        String text = Component.translatableWithFallback("hud.academy.highpower.multi_axis_reflection.pool",
                "Reflection base+lower %s (1.5S) [ALT]", pair).getString();
        if (Screen.hasAltDown() && mc.font.width(text) * .5 > width)
            text = Component.translatableWithFallback("hud.academy.highpower.multi_axis_reflection.exact_too_long",
                    "Reflection: exact values are too long for this view").getString();
        float scale = HighPowerRecoveryHudModel.fittedScale(width, mc.font.width(text), .66f);
        if (scale <= 0) return;
        // Recovery layout begins at y >= 8. This fixed eight-pixel row cannot cover that panel.
        graphics.fill(7, 0, 9 + Math.min(width, (int)Math.ceil(mc.font.width(text) * scale)), 8, 0x99070b0e);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(8, 0, 0); graphics.pose().scale(scale, scale, 1);
            graphics.drawString(mc.font, text, 0, 0, 0xffdedfe1, false);
        } finally { graphics.pose().popPose(); }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        List<MultiAxisReflectionState.Identity> local = STATE.controllers().stream().filter(MultiAxisReflectionState.Controller::local)
                .map(MultiAxisReflectionState.Controller::id).toList();
        var losses = STATE.tick(mc.isPaused(), controller -> validOwner(controller, mc));
        OWNERS.entrySet().removeIf(entry -> STATE.current(entry.getKey()) == null);
        if (world != null) for (var controller : STATE.controllers()) {
            Binding owner = OWNERS.get(controller.id().owner());
            if (owner == null) continue;
            STATE.observeBody(controller.id().owner(), point(owner.owner.getBoundingBox().getCenter()));
            if (!mc.isPaused()) for (var trace : STATE.current(controller.id().owner()).traces()) {
                ArrowBinding key = new ArrowBinding(controller.id(), trace.arrow());
                Entity arrow = world.getEntity(trace.arrow().entity());
                if (!(arrow instanceof AbstractArrow) || arrow.isRemoved() || !arrow.getUUID().equals(trace.arrow().uuid())
                        || ARROWS.get(key) != arrow || !world.hasChunkAt(arrow.blockPosition())) {
                    STATE.forgetArrow(controller.id().owner(), trace.arrow()); ARROWS.remove(key);
                } else STATE.observe(controller.id().owner(), trace.arrow(), point(arrow.position()));
            }
        }
        ARROWS.keySet().removeIf(key -> {
            var owner = STATE.current(key.owner.owner());
            return owner == null || !owner.id().equals(key.owner)
                    || owner.traces().stream().noneMatch(trace -> trace.arrow().equals(key.arrow));
        });
        // Quiet receipt visibility is recoverable. Only explicit owner invalidity sends a local abort.
        for (var id : losses) if (local.contains(id)) AcademyClient.multiAxisReflectionLocalVisualLost(id.input(), id.epoch());
    }
    private static boolean visible(MultiAxisReflectionState.Controller controller, RenderLevelStageEvent event, Vec3 camera) {
        Binding binding = OWNERS.get(controller.id().owner());
        if (binding == null || camera.distanceToSqr(binding.owner.position()) > MultiAxisReflectionMesh.CULL_DISTANCE * MultiAxisReflectionMesh.CULL_DISTANCE) return false;
        Point center = point(binding.owner.getBoundingBox().getCenter());
        if (center.distanceSquared(controller.observedBody()) > MultiAxisReflectionState.OWNER_DISCONTINUITY_SQUARED) return false;
        AABB bounds = binding.owner.getBoundingBox().inflate(2);
        for (var trace : controller.traces()) {
            if (center.distanceSquared(trace.reflectionPoint()) > 64 * 64
                    || trace.points().stream().anyMatch(p -> center.distanceSquared(p) > 64 * 64)) return false;
            bounds = bounds.minmax(new AABB(vec(trace.reflectionPoint()), vec(trace.reflectionPoint())).inflate(.3));
            for (Point position : trace.points()) bounds = bounds.minmax(new AABB(vec(position), vec(position)).inflate(.1));
        }
        if (controller.contact() != null) {
            if (center.distanceSquared(controller.contact().arrowPoint()) > 64 * 64) return false;
            Vec3 contact = vec(controller.contact().arrowPoint());
            bounds = bounds.minmax(new AABB(contact, contact).inflate(.3));
        }
        for (int x = Math.floorDiv((int)Math.floor(bounds.minX), 16); x <= Math.floorDiv((int)Math.floor(bounds.maxX), 16); x++)
            for (int z = Math.floorDiv((int)Math.floor(bounds.minZ), 16); z <= Math.floorDiv((int)Math.floor(bounds.maxZ), 16); z++)
                if (!world.hasChunkAt(new BlockPos(x * 16, (int)Math.floor(bounds.minY), z * 16))) return false;
        return event.getFrustum().isVisible(bounds);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); if (world == null || STATE.size() == 0) return;
        Vec3 camera = event.getCamera().getPosition(); Point eye = point(camera);
        var visible = STATE.controllers().stream().filter(c -> c.quiet() < MultiAxisReflectionState.QUIET_TICKS && validOwner(c, mc) && visible(c, event, camera))
                .sorted(Comparator.<MultiAxisReflectionState.Controller>comparingInt(c -> c.local() ? 0 : 1)
                        .thenComparingDouble(c -> OWNERS.get(c.id().owner()).owner.position().distanceToSqr(camera))
                        .thenComparingInt(c -> c.id().entity()))
                .limit(MultiAxisReflectionMesh.MAX_CONTEXTS).toList();
        if (visible.isEmpty()) return;
        float partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var budget = new MultiAxisReflectionMesh.Budget();
        var shapes = visible.stream().map(c -> {
            Entity owner = OWNERS.get(c.id().owner()).owner;
            Vec3 offset = owner.getBoundingBox().getCenter().subtract(owner.position());
            Point center = new Point(Mth.lerp(partial, owner.xo, owner.getX()) + offset.x,
                    Mth.lerp(partial, owner.yo, owner.getY()) + offset.y, Mth.lerp(partial, owner.zo, owner.getZ()) + offset.z);
            return MultiAxisReflectionMesh.shape(c, center, partial);
        }).filter(s -> s.vertexCount() > 0 && s.vertexCount() <= MultiAxisReflectionMesh.MAX_CONTEXT_VERTICES
                && budget.take(s.vertexCount())).toList();
        if (shapes.isEmpty()) return;
        if (enhancedMaterial == null) enhancedMaterial = mc.getResourceManager().getResource(GLOW_LINE).isPresent()
                && mc.getResourceManager().getResource(WHITE).isPresent();
        if (buffers == null) { storage = new ByteBufferBuilder(65536); buffers = MultiBufferSource.immediate(storage); }
        var look = event.getCamera().getLookVector(); Point forward = new Point(look.x(), look.y(), look.z());
        var left = event.getCamera().getLeftVector(); Vec3 fallback = new Vec3(left.x(), left.y(), left.z());
        var poses = event.getPoseStack(); var modelView = RenderSystem.getModelViewStack();
        float[] color = RenderSystem.getShaderColor().clone(); poses.pushPose(); modelView.pushMatrix();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = poses.last().pose();
            // Material fallback applies to the entire accepted group, never selected edges or facets.
            for (Pass pass : enhancedMaterial ? new Pass[]{Pass.PRESSURE, Pass.SOFT, Pass.CORE} : new Pass[]{Pass.SIMPLE}) {
                VertexConsumer out = buffers.getBuffer(Material.quads(pass));
                for (var shape : shapes) {
                    if (pass == Pass.PRESSURE || pass == Pass.SIMPLE)
                        for (var panel : shape.panels()) panel(out, matrix, panel, eye, forward, pass == Pass.SIMPLE);
                    if (pass != Pass.PRESSURE) for (var stroke : shape.strokes())
                        stroke(out, matrix, stroke, MultiAxisReflectionMesh.profile(stroke, pass != Pass.SOFT), eye, forward, fallback, pass == Pass.SIMPLE);
                }
                buffers.endBatch(Material.quads(pass));
            }
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }
    private static void stroke(VertexConsumer out, Matrix4f matrix, MultiAxisReflectionMesh.Stroke stroke,
                               MultiAxisReflectionMesh.Profile profile, Point camera, Point forward, Vec3 fallback, boolean simple) {
        if (profile.alpha() <= 0) return;
        var clipped = MultiAxisReflectionMesh.clipNear(new MultiAxisReflectionMesh.Stroke(
                MultiAxisReflectionMesh.relative(stroke.from(), camera), MultiAxisReflectionMesh.relative(stroke.to(), camera),
                profile.width(), profile.alpha(), stroke.role()), forward, profile.width() / 2);
        if (clipped == null) return;
        Vec3 a = vec(clipped.from()), b = vec(clipped.to());
        Vec3 side = b.subtract(a).cross(a.add(b).scale(-.5)).normalize();
        if (side.lengthSqr() < 1e-10) side = fallback;
        side = side.scale(profile.width() / 2);
        int alphaA = (int)Math.round(profile.alpha() * MultiAxisReflectionMesh.nearFade(clipped.from()));
        int alphaB = (int)Math.round(profile.alpha() * MultiAxisReflectionMesh.nearFade(clipped.to()));
        vertex(out, matrix, a.subtract(side), 0, profile.maxV(), profile.gray(), alphaA, simple);
        vertex(out, matrix, a.add(side), 0, profile.minV(), profile.gray(), alphaA, simple);
        vertex(out, matrix, b.add(side), 1, profile.minV(), profile.gray(), alphaB, simple);
        vertex(out, matrix, b.subtract(side), 1, profile.maxV(), profile.gray(), alphaB, simple);
    }
    private static void panel(VertexConsumer out, Matrix4f matrix, MultiAxisReflectionMesh.Panel panel,
                              Point camera, Point forward, boolean simple) {
        if (!MultiAxisReflectionMesh.panelVisible(panel, camera, forward)) return;
        for (int i = 0; i < 4; i++) {
            Point relative = MultiAxisReflectionMesh.relative(panel.corners().get(i), camera);
            int alpha = (int)Math.round(panel.alpha() * MultiAxisReflectionMesh.nearFade(relative));
            vertex(out, matrix, vec(relative), i == 1 || i == 2 ? 1 : 0, i >= 2 ? 1 : 0, 194, alpha, simple);
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 p, float u, float v, int gray, int alpha, boolean simple) {
        VertexConsumer vertex = out.addVertex(matrix, (float)p.x, (float)p.y, (float)p.z);
        if (!simple) vertex.setUv(u, v);
        vertex.setColor(gray, gray, gray, alpha);
    }
    private static Point point(Vec3 p) { return p == null ? null : new Point(p.x, p.y, p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private enum Pass { PRESSURE, SOFT, CORE, SIMPLE }
    /** Original textures, ordinary translucent depth testing, color-only writes and no custom shader. */
    private static final class Material extends RenderType {
        private static final Map<Pass, RenderType> TYPES = new HashMap<>();
        private Material(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling,
                         boolean sorted, Runnable set, Runnable reset) { super(name, format, mode, size, crumbling, sorted, set, reset); }
        static RenderType quads(Pass pass) {
            return TYPES.computeIfAbsent(pass, p -> {
                var state = CompositeState.builder()
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET);
                if (p == Pass.SIMPLE) state.setShaderState(new ShaderStateShard(GameRenderer::getPositionColorShader));
                else state.setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new TextureStateShard(p == Pass.SOFT ? GLOW_LINE : WHITE, false, false));
                return create("academy_multi_axis_reflection_" + p,
                        p == Pass.SIMPLE ? DefaultVertexFormat.POSITION_COLOR : DefaultVertexFormat.POSITION_TEX_COLOR,
                        VertexFormat.Mode.QUADS, 8192, false, true, state.createCompositeState(false));
            });
        }
    }

    /** Read-only snapshot: points = body, facing, arrow, contact; numbers = live mask, contact age (-1 none); polylines = reflection traces. */
    private static double[] dp(Point p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var out = new java.util.ArrayList<DecorSnap>();
        for (var c : STATE.controllers()) {
            if (world == null || c.quiet() >= MultiAxisReflectionState.QUIET_TICKS || !validOwner(c, mc)) continue;
            var f = c.frame();
            var polylines = new java.util.ArrayList<java.util.List<double[]>>();
            for (var trace : c.traces()) {
                var line = new java.util.ArrayList<double[]>(); line.add(dp(trace.reflectionPoint()));
                for (var p : trace.points()) line.add(dp(p));
                if (line.size() > 1) polylines.add(line);
            }
            double[] contact = c.contact() == null ? DecorSnap.NONE : c.contact().arrowPoint() != null ? dp(c.contact().arrowPoint()) : dp(c.contact().point());
            out.add(new DecorSnap(c.local(), c.quiet(), f.phase(), c.id().token(), java.util.List.of(dp(f.point()), dp(f.facing()), dp(f.arrowPoint()), contact),
                    new double[] {f.liveMask(), c.contact() == null ? -1 : c.contactAge()}, polylines));
        }
        return out;
    }
}

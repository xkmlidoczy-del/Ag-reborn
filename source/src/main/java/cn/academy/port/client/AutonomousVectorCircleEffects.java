package cn.academy.port.client;

import cn.academy.port.highpower.AutonomousVectorCircle;
import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.client.AutonomousVectorCircleMesh.Point;
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
import net.minecraft.network.chat.Component;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
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
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;

/** NEW452 receipt-authenticated open forward flow, physical quota and local absorption compression. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class AutonomousVectorCircleEffects {
    private static final ResourceLocation GLOW_LINE = texture("effects/glow_line"), WHITE = texture("port/white"), GLOW_CIRCLE = texture("effects/glow_circle");
    private static final AutonomousVectorCircleState STATE = new AutonomousVectorCircleState();
    private record Binding(Entity owner, AutonomousVectorCircleState.Identity id) {}
    private static final Map<UUID, Binding> OWNERS = new HashMap<>();
    private static Boolean enhancedMaterial;
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static ByteBufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private AutonomousVectorCircleEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            AutonomousVectorCircle.clientReceiver = AutonomousVectorCircleEffects::receive;
            AutonomousVectorCircle.clientRejectionReceiver = AutonomousVectorCircleEffects::reject;
        }
        @SubscribeEvent public static void shaders(RegisterShadersEvent event) { AutonomousVectorCircleSoftMaterial.register(event); }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> Minecraft.getInstance().execute(() -> {
                AcademyClient.cancelAutonomousVectorCircleForReload(); clear();
                if (buffers != null) buffers.endBatch();
                if (storage != null) storage.close();
                buffers = null; storage = null; enhancedMaterial = null; Material.TYPES.clear();
            }));
        }
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aca_vfx2").then(Commands.literal("vector_circle_soft")
                .then(Commands.literal("on").executes(c -> requestSoft(true)))
                .then(Commands.literal("off").executes(c -> requestSoft(false)))
                .then(Commands.literal("status").executes(c -> softStatus()))));
    }
    private static int requestSoft(boolean on) { AutonomousVectorCircleSoftMaterial.request(on); return softStatus(); }
    private static int softStatus() {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatableWithFallback("academy.vfx2.vector_circle_soft.status",
                "Vector Circle soft: requested=%s, ready=%s. Session only; restarting the client restores OFF.",
                AutonomousVectorCircleSoftMaterial.requested(), AutonomousVectorCircleSoftMaterial.ready()), false);
        return 1;
    }
    private static AutonomousVectorCircleState.Frame frame(AutonomousVectorCircle.Visual p) {
        return new AutonomousVectorCircleState.Frame(new AutonomousVectorCircleState.Identity(p.entity(), p.owner(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), point(p.point()), point(p.facing()), point(p.source()), point(p.contact()), p.remainingHits(), p.ready(), p.availableShield(), p.shieldCapacity());
    }
    public static void receive(AutonomousVectorCircle.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!AutonomousVectorCircleState.valid(receipt)) return;
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
                    || !AcademyClient.autonomousVectorCircleInputMatches(packet.input(), packet.epoch()))) return;
            if (packet.phase() != AutonomousVectorCircleState.ENDED && (!owner.isAlive() || owner.isRemoved() || owner.isSpectator()
                    || owner.position().distanceToSqr(vec(receipt.point())) > 16
                    || local && !AcademyClient.autonomousVectorCircleControlValid())) return;
            if (!STATE.accept(receipt, local)) return;
            OWNERS.entrySet().removeIf(entry -> STATE.current(entry.getKey()) == null);
            if (packet.phase() == AutonomousVectorCircleState.ENDED) {
                if (local) AcademyClient.autonomousVectorCircleEndedInput(packet.input(), packet.epoch());
            } else {
                OWNERS.put(packet.owner(), new Binding(owner, receipt.id()));
                if (local) AcademyClient.autonomousVectorCircleAcceptedInput(packet.input(), packet.epoch());
            }
        });
    }
    private static void reject(AutonomousVectorCircle.Rejection packet) {
        if (packet == null || packet.owner() == null || packet.entity() < 0 || packet.input() <= 0 || packet.epoch() <= 0) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || capturedPlayer == null || mc.level != capturedWorld || mc.player != capturedPlayer
                    || mc.getConnection() != capturedConnection || generation != capturedGeneration
                    || !AcademyClient.sameInputSessionEpoch(capturedSession) || packet.entity() != capturedPlayer.getId()
                    || !packet.owner().equals(capturedPlayer.getUUID()) || !AcademyClient.singleOwnerEpochMatches(packet.epoch())) return;
            AcademyClient.autonomousVectorCircleRejectedInput(packet.input(), packet.epoch());
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
    private static boolean validOwner(AutonomousVectorCircleState.Controller controller, Minecraft mc) {
        if (controller == null || world == null) return false;
        Binding binding = OWNERS.get(controller.id().owner());
        if (binding == null || !binding.id.equals(controller.id())) return false;
        Entity owner = binding.owner;
        return owner instanceof Player && world.getEntity(controller.id().entity()) == owner && owner.isAlive()
                && !owner.isRemoved() && !owner.isSpectator() && owner.getUUID().equals(controller.id().owner())
                && (!controller.local() || owner == mc.player && AcademyClient.singleOwnerEpochMatches(controller.id().epoch())
                && AcademyClient.autonomousVectorCircleInputMatches(controller.id().input(), controller.id().epoch())
                && AcademyClient.autonomousVectorCircleControlValid());
    }
    public static boolean activeFor(UUID owner) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var controller = owner == null ? null : STATE.current(owner);
        return controller != null && controller.quiet() < AutonomousVectorCircleState.QUIET_TICKS && validOwner(controller, mc);
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        return mc.player != null && activeFor(mc.player.getUUID()) ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    public static boolean activeLocal() { return localVisual() == ClassicAbilityHud.DelegateVisual.ACTIVE; }
    /** The allowance is explicitly the last receipt's tick, never a predicted replenishment. */
    public static String localHint() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var controller = mc.player == null ? null : STATE.current(mc.player.getUUID());
        if (controller == null || !validOwner(controller, mc) || controller.quiet() >= AutonomousVectorCircleState.QUIET_TICKS) return "";
        boolean ready = controller.frame().ready() && controller.frame().remainingHits() > 0;
        return net.minecraft.network.chat.Component.translatableWithFallback("hud.academy.highpower.autonomous_vector_circle",
                "Circle %s/8 | %s", controller.frame().remainingHits(),
                net.minecraft.network.chat.Component.translatableWithFallback(ready
                        ? "hud.academy.highpower.autonomous_vector_circle.ready"
                        : "hud.academy.highpower.autonomous_vector_circle.paused",
                        ready ? "front kinetic defense ready" : "paused: physical quota or reserve")).getString();
    }
    /** The 2S total belongs to this controller's owner and never modifies the shared recovery instrument. */
    @SubscribeEvent public static void resourceHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (mc.player == null || mc.level == null || mc.getConnection() == null || mc.options.hideGui || mc.screen != null
                || mc.getCameraEntity() != mc.player || mc.getDebugOverlay().showDebugScreen()) return;
        var controller = STATE.current(mc.player.getUUID());
        if (!AutonomousVectorCircleHudModel.visible(controller) || !validOwner(controller, mc)) return;
        var content = AutonomousVectorCircleHudModel.content(controller, mc.getLanguageManager().getSelected(),
                (key, fallback) -> Component.translatableWithFallback(
                        "hud.academy.highpower.autonomous_vector_circle." + key, fallback).getString(),
                (key, fallback) -> Component.translatableWithFallback("hud.academy.highpower." + key, fallback).getString());
        var graphics = event.getGuiGraphics();
        var layout = AutonomousVectorCircleHudModel.layout(graphics.guiWidth(), graphics.guiHeight(), mc.font.lineHeight,
                content, reservedHudBoxes(mc, graphics.guiWidth(), graphics.guiHeight()),
                (text, width) -> mc.font.split(Component.literal(text), width), mc.font::width);
        if (layout == null) return;
        var box = layout.bounds();
        graphics.fill(box.x(), box.y(), box.x() + box.width(), box.y() + box.height(), 0x99070b0e);
        graphics.fill(box.x(), box.y(), box.x() + 2, box.y() + box.height(), 0xff8f969c);
        for (var line : layout.lines()) graphics.drawString(mc.font, line.text(), line.x(), line.y(), 0xffdedfe1, true);
    }
    private static List<HighPowerRecoveryHudModel.Box> reservedHudBoxes(Minecraft mc, int width, int height) {
        boolean config = ClassicHudConfig.SPEC.isLoaded();
        var cp = ClassicHudTimeline.cpOrigin(width, config ? ClassicHudConfig.CP_X.get() : ClassicHudTimeline.CP_X,
                config ? ClassicHudConfig.CP_Y.get() : ClassicHudTimeline.CP_Y);
        var keys = ClassicHudTimeline.keyOrigin(width, height, config ? ClassicHudConfig.KEY_X.get() : 0,
                config ? ClassicHudConfig.KEY_Y.get() : 30);
        var boxes = new java.util.ArrayList<HighPowerRecoveryHudModel.Box>();
        boxes.add(new HighPowerRecoveryHudModel.Box((int)Math.floor(cp.x()), (int)Math.floor(cp.y()), 194, 40));
        boxes.add(new HighPowerRecoveryHudModel.Box((int)keys.x() - 155, (int)keys.y(), 185, 88));
        boxes.add(new HighPowerRecoveryHudModel.Box((int)Math.floor(config ? ClassicHudConfig.NOTIFICATION_X.get() : 0),
                (int)Math.floor(config ? ClassicHudConfig.NOTIFICATION_Y.get() : 15), 130, 43));
        var chat = mc.gui.getChat();
        int chatHeight = (int)Math.ceil(chat.getHeight() * chat.getScale());
        boxes.add(new HighPowerRecoveryHudModel.Box(0, height - 40 - chatHeight,
                Math.min(width, chat.getWidth() + 16), chatHeight + 12));
        var recovery = HighPowerRecoveryHud.relayReservedBox(width, height);
        if (recovery != null) boxes.add(recovery);
        return List.copyOf(boxes);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        List<AutonomousVectorCircleState.Identity> local = STATE.controllers().stream().filter(AutonomousVectorCircleState.Controller::local)
                .map(AutonomousVectorCircleState.Controller::id).toList();
        var losses = STATE.tick(mc.isPaused(), controller -> validOwner(controller, mc));
        OWNERS.entrySet().removeIf(entry -> STATE.current(entry.getKey()) == null);
        // Only explicit owner invalidity is terminal. Quiet heartbeats remain recoverable and send no abort.
        for (var id : losses) if (local.contains(id)) AcademyClient.autonomousVectorCircleLocalVisualLost(id.input(), id.epoch());
    }
    private static boolean visible(AutonomousVectorCircleState.Controller controller, RenderLevelStageEvent event, Vec3 camera) {
        Binding binding = OWNERS.get(controller.id().owner());
        if (binding == null || camera.distanceToSqr(binding.owner.position()) > AutonomousVectorCircleMesh.CULL_DISTANCE * AutonomousVectorCircleMesh.CULL_DISTANCE) return false;
        AABB bounds = binding.owner.getBoundingBox().inflate(1.5);
        var absorption = controller.absorption();
        if (absorption != null && controller.absorptionAge() < AutonomousVectorCircleState.ABSORPTION_TICKS) {
            // Only contact-local compression is drawn. The real source never becomes a fake projectile trail.
            Vec3 contact = vec(absorption.contact()), position = vec(absorption.point());
            if (position.distanceToSqr(binding.owner.position()) > 16) return false;
            bounds = bounds.minmax(new AABB(contact, contact).inflate(.6));
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
        var visible = STATE.controllers().stream().filter(c -> c.quiet() < AutonomousVectorCircleState.QUIET_TICKS && validOwner(c, mc) && visible(c, event, camera))
                .sorted(Comparator.<AutonomousVectorCircleState.Controller>comparingInt(c -> c.local() ? 0 : 1)
                        .thenComparingDouble(c -> OWNERS.get(c.id().owner()).owner.position().distanceToSqr(camera))
                        .thenComparingInt(c -> c.id().entity()))
                .limit(AutonomousVectorCircleMesh.MAX_CONTEXTS).toList();
        if (visible.isEmpty()) return;
        float partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (enhancedMaterial == null) enhancedMaterial = mc.getResourceManager().getResource(GLOW_LINE).isPresent()
                && mc.getResourceManager().getResource(WHITE).isPresent() && mc.getResourceManager().getResource(GLOW_CIRCLE).isPresent();
        var detailed = new java.util.ArrayList<AutonomousVectorCircleMesh.Shape>();
        var base = new java.util.ArrayList<AutonomousVectorCircleMesh.Shape>();
        for (var c : visible) {
            Entity owner = OWNERS.get(c.id().owner()).owner;
            Point feet = new Point(Mth.lerp(partial, owner.xo, owner.getX()), Mth.lerp(partial, owner.yo, owner.getY()),
                    Mth.lerp(partial, owner.zo, owner.getZ()));
            detailed.add(AutonomousVectorCircleMesh.shape(c, feet, partial));
            base.add(AutonomousVectorCircleMesh.base(c, feet, partial));
        }
        boolean softRequested = AutonomousVectorCircleSoftMaterial.requested();
        // OFF retains the original full scene. A requested but unavailable soft material falls back atomically.
        var group = AutonomousVectorCircleMesh.group(detailed, base, enhancedMaterial && (!softRequested || AutonomousVectorCircleSoftMaterial.ready()));
        boolean useSoft = group.enhanced() && softRequested;
        var shapes = group.shapes();
        if (shapes.isEmpty()) return;
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        if (buffers == null) { storage = new ByteBufferBuilder(65536); buffers = MultiBufferSource.immediate(storage); }
        var look = event.getCamera().getLookVector(); Point forward = new Point(look.x(), look.y(), look.z());
        var left = event.getCamera().getLeftVector(); Vec3 fallback = new Vec3(left.x(), left.y(), left.z());
        var poses = event.getPoseStack(); var modelView = RenderSystem.getModelViewStack();
        float[] color = RenderSystem.getShaderColor().clone(); poses.pushPose(); modelView.pushMatrix();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = poses.last().pose();
            for (Pass pass : group.enhanced() ? new Pass[]{Pass.SOFT, Pass.CORE, Pass.WAVE} : new Pass[]{Pass.SIMPLE}) {
                var type = Material.quads(pass, useSoft);
                VertexConsumer out = buffers.getBuffer(type);
                for (var shape : shapes) {
                    if (pass == Pass.WAVE) for (var wave : shape.waves())
                        wave(out, matrix, wave, eye, forward, firstPerson);
                    else for (var stroke : shape.strokes())
                        stroke(out, matrix, stroke, AutonomousVectorCircleMesh.profile(stroke, pass != Pass.SOFT, useSoft),
                                eye, forward, fallback, firstPerson, pass == Pass.SIMPLE);
                }
                buffers.endBatch(type);
            }
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }
    private static void stroke(VertexConsumer out, Matrix4f matrix, AutonomousVectorCircleMesh.Stroke stroke,
                               AutonomousVectorCircleMesh.Profile profile, Point camera, Point forward, Vec3 fallback, boolean firstPerson, boolean simple) {
        if (profile.alpha() <= 0) return;
        var clipped = AutonomousVectorCircleMesh.clipNear(new AutonomousVectorCircleMesh.Stroke(
                AutonomousVectorCircleMesh.relative(stroke.from(), camera), AutonomousVectorCircleMesh.relative(stroke.to(), camera),
                profile.width(), profile.alpha(), stroke.role()), forward, profile.width() / 2);
        if (clipped == null || firstPerson && !AutonomousVectorCircleMesh.outsideAim(clipped, forward)) return;
        Vec3 a = vec(clipped.from()), b = vec(clipped.to());
        Vec3 side = b.subtract(a).cross(a.add(b).scale(-.5)).normalize();
        if (side.lengthSqr() < 1e-10) side = fallback;
        side = side.scale(profile.width() / 2);
        int alphaA = (int)Math.round(profile.alpha() * AutonomousVectorCircleMesh.nearFade(clipped.from()));
        int alphaB = (int)Math.round(profile.alpha() * AutonomousVectorCircleMesh.nearFade(clipped.to()));
        vertex(out, matrix, a.subtract(side), 0, profile.maxV(), profile.gray(), alphaA, simple);
        vertex(out, matrix, a.add(side), 0, profile.minV(), profile.gray(), alphaA, simple);
        vertex(out, matrix, b.add(side), 1, profile.minV(), profile.gray(), alphaB, simple);
        vertex(out, matrix, b.subtract(side), 1, profile.maxV(), profile.gray(), alphaB, simple);
    }
    private static void wave(VertexConsumer out, Matrix4f matrix, AutonomousVectorCircleMesh.Wave wave,
                             Point camera, Point forward, boolean firstPerson) {
        if (!AutonomousVectorCircleMesh.waveVisible(wave, camera, forward, firstPerson)) return;
        float[][] uv = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};
        for (int i = 0; i < 4; i++) {
            Point relative = AutonomousVectorCircleMesh.relative(wave.corners().get(i), camera);
            vertex(out, matrix, vec(relative), uv[i][0], uv[i][1], 238,
                    (int)Math.round(wave.alpha() * AutonomousVectorCircleMesh.nearFade(relative)), false);
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 p, float u, float v, int gray, int alpha, boolean simple) {
        var vertex = out.addVertex(matrix, (float)p.x, (float)p.y, (float)p.z);
        if (!simple) vertex.setUv(u, v);
        vertex.setColor(gray, gray, gray, alpha);
    }
    private static Point point(Vec3 p) { return p == null ? null : new Point(p.x, p.y, p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private enum Pass { SOFT, CORE, WAVE, SIMPLE }
    /** Original textures, ordinary translucent depth testing and color-only writes. Only stroke layers use soft alpha. */
    private static final class Material extends RenderType {
        private record Key(Pass pass, boolean soft) {}
        private static final Map<Key, RenderType> TYPES = new HashMap<>();
        private Material(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling,
                         boolean sorted, Runnable set, Runnable reset) { super(name, format, mode, size, crumbling, sorted, set, reset); }
        static RenderType quads(Pass pass, boolean soft) {
            return TYPES.computeIfAbsent(new Key(pass, soft && (pass == Pass.SOFT || pass == Pass.CORE)), key -> {
                Pass p = key.pass();
                boolean simple = p == Pass.SIMPLE;
                var state = CompositeState.builder()
                        .setShaderState(new ShaderStateShard(simple ? GameRenderer::getPositionColorShader
                                 : key.soft() ? AutonomousVectorCircleSoftMaterial::shader : GameRenderer::getPositionTexColorShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                        .setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET);
                if (!simple) state.setTextureState(new TextureStateShard(
                        p == Pass.WAVE ? GLOW_CIRCLE : p == Pass.CORE ? WHITE : GLOW_LINE, false, false));
                return create("academy_autonomous_vector_circle_" + p + (key.soft() ? "_soft_alpha" : ""),
                        simple ? DefaultVertexFormat.POSITION_COLOR : DefaultVertexFormat.POSITION_TEX_COLOR,
                        VertexFormat.Mode.QUADS, 8192, false, true, state.createCompositeState(false));
            });
        }
    }

    /** Read-only snapshot: points = body, facing, source, contact; numbers = ready, hits, 0, absorption age (-1 none). */
    private static double[] dp(Point p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var out = new java.util.ArrayList<DecorSnap>();
        for (var c : STATE.controllers()) {
            if (world == null || c.quiet() >= AutonomousVectorCircleState.QUIET_TICKS || !validOwner(c, mc)) continue;
            var f = c.frame();
            var hit = c.absorption();
            out.add(DecorSnap.of(c.local(), c.age(), f.phase(), c.id().token(),
                    java.util.List.of(dp(f.point()), dp(f.facing()), dp(f.source()), hit == null ? DecorSnap.NONE : dp(hit.contact())),
                    f.ready() ? 1 : 0, f.remainingHits(), 0, hit == null ? -1 : c.absorptionAge()));
        }
        return out;
    }
}

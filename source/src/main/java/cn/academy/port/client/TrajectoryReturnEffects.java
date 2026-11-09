package cn.academy.port.client;

import cn.academy.port.HighPowerVisualConfig;
import cn.academy.port.highpower.TrajectoryReturn;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
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
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW431 native reversal receipts and bounded white-gray pressure; no particle or predicted trail. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class TrajectoryReturnEffects {
    private static final ResourceLocation GLOW_LINE = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/glow_line.png");
    private static final String ORIGINAL_GLOW_SHA = "0785f30721bd1281cb356452c1c68ae721d3b09304df5b23de05578593dbf361";
    private static final TrajectoryReturnState STATE = new TrajectoryReturnState();
    private static final TrajectoryReturnPresentation PRESENTATION = new TrajectoryReturnPresentation();
    private static final Map<UUID, Entity> owners = new HashMap<>();
    private static final Map<UUID, Vec3> ownerPositions = new HashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static long generation;
    private static volatile long materialGeneration;
    private static volatile boolean pressureReady;
    private static ByteBufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private TrajectoryReturnEffects() {}

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { TrajectoryReturn.clientReceiver = TrajectoryReturnEffects::receive; }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> {
                pressureReady = false;
                long revision = ++materialGeneration;
                boolean valid = false;
                try (var stream = resources.getResourceOrThrow(GLOW_LINE).open()) {
                    byte[] bytes = stream.readNBytes(65537);
                    valid = bytes.length <= 65536 && ORIGINAL_GLOW_SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
                } catch (Exception unavailable) { valid = false; }
                final boolean accepted = valid;
                Minecraft.getInstance().execute(() -> {
                    AcademyClient.trajectoryReturnCancelInput(); clear();
                    if (buffers != null) buffers.endBatch();
                    if (storage != null) storage.close();
                    buffers = null; storage = null; Material.TYPES.clear();
                    if (revision == materialGeneration) pressureReady = accepted;
                });
            });
        }
    }
    private static boolean pressureRequested() {
        try { return HighPowerVisualConfig.SPEC.isLoaded() && HighPowerVisualConfig.TRAJECTORY_RETURN_PRESSURE.get(); }
        catch (RuntimeException unavailable) { return false; }
    }
    private static boolean pressureAvailable() {
        try { return pressureReady && !Boolean.getBoolean("academy.trajectoryReturnPressure.forceFallback")
                && GameRenderer.getPositionTexColorShader() != null; }
        catch (RuntimeException unavailable) { return false; }
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aca_vfx2").then(Commands.literal("trajectory_return")
                .then(Commands.literal("on").executes(c -> setPressure(true)))
                .then(Commands.literal("off").executes(c -> setPressure(false)))
                .then(Commands.literal("status").executes(c -> pressureStatus()))));
    }
    private static int setPressure(boolean enabled) {
        if (!HighPowerVisualConfig.SPEC.isLoaded()) return 0;
        HighPowerVisualConfig.TRAJECTORY_RETURN_PRESSURE.set(enabled); HighPowerVisualConfig.SPEC.save();
        return pressureStatus();
    }
    private static int pressureStatus() {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal("trajectory_return: requested=" + pressureRequested()
                + ", available=" + pressureAvailable() + ", resource=" + pressureReady + "; applies to new casts"), false);
        return 1;
    }
    private static TrajectoryReturnState.Frame frame(TrajectoryReturn.Visual p) {
        return new TrajectoryReturnState.Frame(new TrajectoryReturnState.Identity(p.owner(), p.ownerUuid(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.kind(), p.slot(), point(p.origin()), point(p.end()), point(p.velocity()), p.status());
    }
    public static void receive(TrajectoryReturn.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!TrajectoryReturnState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.owner());
            if (!(owner instanceof Player) || !owner.getUUID().equals(packet.ownerUuid()) || !owner.isAlive() || owner.isRemoved()) return;
            boolean local = owner == mc.player;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.trajectoryReturnInputMatches(packet.input(), packet.epoch()))) return;
            if (local && !AcademyClient.trajectoryReturnControlValid()) { AcademyClient.trajectoryReturnCancelInput(); return; }
            var before = STATE.controllers();
            if (!STATE.accept(receipt, local)) {
                for (var previous : before) if (previous.local() && !STATE.owns(previous.id()))
                    AcademyClient.trajectoryReturnEndedInput(previous.id().input(),previous.id().epoch());
                pruneOwners(); return;
            }
            PRESENTATION.prune(STATE.controllers());
            PRESENTATION.accepted(receipt, STATE.controllers().stream().filter(c -> c.id().equals(receipt.id())).findFirst().orElse(null), pressureRequested());
            if (STATE.owns(receipt.id())) { owners.put(packet.ownerUuid(), owner); ownerPositions.putIfAbsent(packet.ownerUuid(),owner.position()); }
            for (var previous : before) if (previous.local() && !STATE.owns(previous.id()))
                AcademyClient.trajectoryReturnEndedInput(previous.id().input(), previous.id().epoch());
            if (local && packet.kind() == TrajectoryReturnState.ENDED) AcademyClient.trajectoryReturnEndedInput(packet.input(), packet.epoch());
            pruneOwners();
        });
    }
    public static void abortLocal(long input, long epoch) { STATE.cancel(epoch, input); pruneOwners(); }
    public static void clear() { generation++; STATE.clear(); PRESENTATION.clear(); owners.clear(); ownerPositions.clear(); }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static void pruneOwners() {
        PRESENTATION.prune(STATE.controllers());
        owners.keySet().removeIf(uuid -> STATE.controllers().stream().noneMatch(c -> c.id().owner().equals(uuid)));
        ownerPositions.keySet().retainAll(owners.keySet());
    }
    private static boolean validOwner(TrajectoryReturnState.Controller cast, Minecraft mc) {
        if (cast == null || world == null) return false;
        Entity owner = world.getEntity(cast.id().entity());
        return owner == owners.get(cast.id().owner()) && owner instanceof Player && owner.isAlive() && !owner.isRemoved()
                && owner.getUUID().equals(cast.id().owner()) && (!cast.local() || owner == mc.player
                && AcademyClient.singleOwnerEpochMatches(cast.id().epoch()) && AcademyClient.trajectoryReturnInputMatches(cast.id().input(),cast.id().epoch())
                && AcademyClient.trajectoryReturnControlValid());
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        return STATE.controllers().stream().anyMatch(c -> c.local() && validOwner(c, mc))
                ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        for (var cast : STATE.controllers()) {
            Entity owner = owners.get(cast.id().owner());
            Vec3 previous = ownerPositions.get(cast.id().owner());
            boolean teleported = owner != null && previous != null && owner.position().distanceToSqr(previous) > 64;
            if (!validOwner(cast,mc) || teleported) {
                if (cast.local()) AcademyClient.trajectoryReturnCancelInput();
                else STATE.retire(cast.id());
            }
        }
        if (!mc.isPaused()) owners.forEach((uuid,owner) -> ownerPositions.put(uuid,owner.position()));
        for (var ended : STATE.tick(mc.isPaused(), c -> validOwner(c, mc))) if (ended.local())
            AcademyClient.trajectoryReturnEndedInput(ended.id().input(), ended.id().epoch());
        pruneOwners();
    }
    private static boolean visible(TrajectoryReturnState.Controller cast, RenderLevelStageEvent event, Vec3 camera) {
        TrajectoryReturnMesh.Bounds decoration = TrajectoryReturnMesh.bounds(cast);
        return visible(decoration, event, camera);
    }
    private static boolean visible(TrajectoryReturnMesh.Bounds decoration, RenderLevelStageEvent event, Vec3 camera) {
        AABB bounds = new AABB(vec(decoration.min()), vec(decoration.max()));
        Vec3 nearest = new Vec3(Math.max(bounds.minX,Math.min(bounds.maxX,camera.x)),
                Math.max(bounds.minY,Math.min(bounds.maxY,camera.y)),Math.max(bounds.minZ,Math.min(bounds.maxZ,camera.z)));
        if (camera.distanceToSqr(nearest) > TrajectoryReturnMesh.CULL_DISTANCE * TrajectoryReturnMesh.CULL_DISTANCE) return false;
        for (int x = Math.floorDiv((int)Math.floor(bounds.minX),16); x <= Math.floorDiv((int)Math.floor(bounds.maxX),16); x++)
            for (int z = Math.floorDiv((int)Math.floor(bounds.minZ),16); z <= Math.floorDiv((int)Math.floor(bounds.maxZ),16); z++)
                if (!world.hasChunkAt(new BlockPos(x*16,(int)Math.floor(bounds.minY),z*16))) return false;
        return event.getFrustum().isVisible(bounds);
    }
    private record RenderCast(TrajectoryReturnState.Controller controller, TrajectoryReturnMesh.Shape mesh, boolean enhanced) {}
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); Vec3 camera = event.getCamera().getPosition();
        double partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long materialRevision = materialGeneration;
        var render = new ArrayList<RenderCast>(TrajectoryReturnMesh.MAX_CONTROLLERS); int vertices = 0;
        for (var c : STATE.controllers()) {
            if (!validOwner(c,mc)) continue;
            boolean enhanced = PRESENTATION.enhanced(c) && pressureAvailable();
            TrajectoryReturnMesh.Shape mesh = null;
            if (enhanced) {
                try {
                    mesh = TrajectoryReturnEnhancedMesh.shape(c,c.age()+partial,PRESENTATION.histories(c));
                    // Refuse the entire enhancement, never trim individual symbols to fit.
                    if (mesh.vertexCount() > TrajectoryReturnMesh.MAX_CONTROLLER_VERTICES
                            || mesh.vertexCount() > TrajectoryReturnMesh.MAX_VERTICES-vertices) enhanced = false;
                } catch (RuntimeException unavailable) { enhanced = false; }
            }
            if (enhanced && !visible(PRESENTATION.bounds(c),event,camera)) enhanced = false;
            if (!enhanced) {
                if (!visible(c,event,camera)) continue;
                mesh = TrajectoryReturnMesh.budgeted(c,c.age()+partial,TrajectoryReturnMesh.MAX_VERTICES-vertices);
            }
            if (vertices + mesh.vertexCount() > TrajectoryReturnMesh.MAX_VERTICES || render.size() >= TrajectoryReturnMesh.MAX_CONTROLLERS) break;
            if (mesh.strokes().isEmpty()) continue;
            vertices += mesh.vertexCount(); render.add(new RenderCast(c,mesh,enhanced));
        }
        // A resource reload can start during selection. Rebuild before any enhanced vertex is emitted.
        if (render.stream().anyMatch(RenderCast::enhanced) && (materialRevision != materialGeneration || !pressureAvailable()))
            render = classicRender(event,mc,camera,partial);
        if (render.isEmpty()) return;
        if (buffers == null) { storage = new ByteBufferBuilder(65536); buffers = MultiBufferSource.immediate(storage); }
        var look = event.getCamera().getLookVector(); Point forward = new Point(look.x(),look.y(),look.z());
        var left = event.getCamera().getLeftVector(); Vec3 fallback = new Vec3(left.x(),left.y(),left.z());
        var poses = event.getPoseStack(); var modelView = RenderSystem.getModelViewStack();
        float[] color = RenderSystem.getShaderColor().clone(); modelView.pushMatrix(); poses.pushPose();
        try {
            RenderSystem.setShaderColor(1,1,1,1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = poses.last().pose();
            for (Pass pass : Pass.values()) {
                var out = buffers.getBuffer(Material.quads(pass));
                for (var c : render) for (var stroke : c.mesh.strokes())
                    stroke(out,matrix,stroke,c.enhanced ? TrajectoryReturnEnhancedMesh.profile(stroke,pass == Pass.CORE,point(camera))
                            : TrajectoryReturnMesh.profile(stroke,pass == Pass.CORE),camera,forward,fallback,owners.get(c.controller.id().owner()));
                buffers.endBatch(Material.quads(pass));
            }
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            }
        }
    }
    /** Decoration snapshot per visible cast: trail segments {x0,y0,z0,x1,y1,z1}, reversal/position/velocity triples or null; reads state only. */
    record DecorTrail(java.util.List<double[]> segments, double[] reversal, double[] position, double[] velocity, boolean reversed, double reversedAge) {}
    record DecorCast(long token, java.util.List<DecorTrail> trails) {}
    private static double[] triple(Point p) { return p == null ? null : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorCast> decorCasts(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); Vec3 camera = event.getCamera().getPosition();
        double partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var out = new ArrayList<DecorCast>();
        for (var c : STATE.controllers()) {
            if (out.size() >= TrajectoryReturnMesh.MAX_CONTROLLERS || !validOwner(c, mc) || !visible(c, event, camera)) continue;
            var trails = new ArrayList<DecorTrail>();
            for (var slot : c.slots()) {
                var segments = new ArrayList<double[]>();
                for (var seg : slot.trail()) segments.add(new double[] {seg.from().x(), seg.from().y(), seg.from().z(), seg.to().x(), seg.to().y(), seg.to().z()});
                boolean reversed = slot.reversal() != null && slot.reversedAt() >= 0;
                trails.add(new DecorTrail(segments, reversed ? triple(slot.reversal()) : null, triple(slot.position()), triple(slot.velocity()), reversed,
                        reversed ? Math.max(0, c.age() + partial - slot.reversedAt()) : 0));
            }
            out.add(new DecorCast(c.id().token(), trails));
        }
        return out;
    }
    private static ArrayList<RenderCast> classicRender(RenderLevelStageEvent event, Minecraft mc, Vec3 camera, double partial) {
        var render = new ArrayList<RenderCast>(TrajectoryReturnMesh.MAX_CONTROLLERS); int vertices = 0;
        for (var controller : STATE.controllers()) {
            if (!validOwner(controller,mc) || !visible(controller,event,camera)) continue;
            var mesh = TrajectoryReturnMesh.budgeted(controller,controller.age()+partial,TrajectoryReturnMesh.MAX_VERTICES-vertices);
            if (vertices + mesh.vertexCount() > TrajectoryReturnMesh.MAX_VERTICES || render.size() >= TrajectoryReturnMesh.MAX_CONTROLLERS) break;
            if (mesh.strokes().isEmpty()) continue;
            vertices += mesh.vertexCount(); render.add(new RenderCast(controller,mesh,false));
        }
        return render;
    }
    private static void stroke(VertexConsumer out, Matrix4f matrix, TrajectoryReturnMesh.Stroke stroke,
                               TrajectoryReturnMesh.Profile profile, Vec3 camera, Point forward, Vec3 fallback, Entity owner) {
        if (profile.alpha() <= 0 || owner == null) return;
        var ribbon = TrajectoryReturnMesh.ribbon(stroke,profile,point(camera),forward,point(fallback));
        if (ribbon == null) return;
        Vec3[] corners = ribbon.corners().stream().map(TrajectoryReturnEffects::vec).toArray(Vec3[]::new);
        // Test every emitted corner and edge, not merely the centerline. No decorative strip passes through a wall.
        Vec3 anchor = vec(stroke.anchor());
        for (int i=0;i<4;i++) {
            Vec3 current = corners[i].add(camera), next = corners[(i+1)%4].add(camera);
            if (!terrainClear(anchor,current,owner) || !terrainClear(current,next,owner)) return;
        }
        int alphaA = ribbon.startAlpha(), alphaB = ribbon.endAlpha();
        vertex(out,matrix,corners[0],0,profile.maxV(),profile.gray(),alphaA);
        vertex(out,matrix,corners[1],0,profile.minV(),profile.gray(),alphaA);
        vertex(out,matrix,corners[2],1,profile.minV(),profile.gray(),alphaB);
        vertex(out,matrix,corners[3],1,profile.maxV(),profile.gray(),alphaB);
    }
    private static boolean terrainClear(Vec3 from,Vec3 to,Entity owner) {
        if (!world.hasChunkAt(BlockPos.containing(from)) || !world.hasChunkAt(BlockPos.containing(to))) return false;
        return world.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner)).getType() == HitResult.Type.MISS;
    }
    private static void vertex(VertexConsumer out,Matrix4f matrix,Vec3 relative,float u,float v,int gray,int alpha) {
        // World coordinates are subtracted from the camera in double before this sole float boundary.
        out.addVertex(matrix,(float)relative.x,(float)relative.y,(float)relative.z).setUv(u,v).setColor(gray,gray,gray,alpha);
    }
    private static Point point(Vec3 p) { return p == null ? null : new Point(p.x,p.y,p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(),p.y(),p.z()); }
    private enum Pass { SHOULDER, CORE }
    private static final class Material extends RenderType {
        private static final Map<Pass,RenderType> TYPES = new HashMap<>();
        private Material(String name,VertexFormat format,VertexFormat.Mode mode,int size,boolean crumbling,
                         boolean sorted,Runnable set,Runnable reset) { super(name,format,mode,size,crumbling,sorted,set,reset); }
        static RenderType quads(Pass pass) {
            return TYPES.computeIfAbsent(pass,p -> create("academy_trajectory_return_"+p,DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,8192,false,true,CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(GLOW_LINE,false,false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                            .setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY).setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
    }
}

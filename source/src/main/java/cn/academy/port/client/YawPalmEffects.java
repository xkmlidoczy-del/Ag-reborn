package cn.academy.port.client;

import cn.academy.port.highpower.YawPalm;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW423 pressure palm and authoritative ray; confirmed native momentum is a short local glyph. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class YawPalmEffects {
    private static final ResourceLocation GLOW_LINE = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/glow_line.png");
    private static final YawPalmState STATE = new YawPalmState();
    private static final Map<UUID, Entity> owners = new HashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static long generation;
    private static ByteBufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private YawPalmEffects() {}

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { YawPalm.clientReceiver = YawPalmEffects::receive; }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> Minecraft.getInstance().execute(() -> {
                AcademyClient.yawPalmCancelInput(); clear();
                if (buffers != null) buffers.endBatch();
                if (storage != null) storage.close();
                buffers = null; storage = null; Material.TYPES.clear();
            }));
        }
    }
    private static YawPalmState.Frame frame(YawPalm.Visual p) {
        return new YawPalmState.Frame(new YawPalmState.Identity(p.owner(), p.ownerUuid(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), p.slot(), p.crouch(), point(p.origin()), point(p.end()), p.status(), point(p.velocityDelta()));
    }
    public static void receive(YawPalm.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!YawPalmState.valid(receipt)) return;
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
                    || !AcademyClient.yawPalmInputMatches(packet.input(), packet.epoch(), packet.crouch()))) return;
            if (local && !AcademyClient.yawPalmControlValid()) { AcademyClient.yawPalmCancelInput(); return; }
            var before = STATE.controllers();
            if (!STATE.accept(receipt, local)) return;
            if (STATE.owns(receipt.id())) owners.put(packet.ownerUuid(), owner);
            else owners.remove(packet.ownerUuid());
            for (var previous : before) if (previous.local() && !STATE.owns(previous.id()))
                AcademyClient.yawPalmEndedInput(previous.id().input(), previous.id().epoch());
            if (local && packet.phase() == YawPalmState.ENDED) AcademyClient.yawPalmEndedInput(packet.input(), packet.epoch());
            pruneOwners();
        });
    }
    public static void abortLocal(long input, long epoch) { STATE.cancel(epoch, input); pruneOwners(); }
    public static void clear() { generation++; STATE.clear(); owners.clear(); }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static void pruneOwners() { owners.keySet().removeIf(uuid -> STATE.controllers().stream().noneMatch(c -> c.id().owner().equals(uuid))); }
    private static boolean validOwner(YawPalmState.Controller cast, Minecraft mc) {
        if (cast == null || world == null) return false;
        Entity owner = world.getEntity(cast.id().entity());
        return owner == owners.get(cast.id().owner()) && owner instanceof Player && owner.isAlive() && !owner.isRemoved()
                && owner.getUUID().equals(cast.id().owner()) && (!cast.local() || owner == mc.player
                && AcademyClient.singleOwnerEpochMatches(cast.id().epoch()) && AcademyClient.yawPalmControlValid());
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        return STATE.controllers().stream().anyMatch(c -> c.local() && validOwner(c, mc))
                ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        for (var cast : STATE.controllers()) if (cast.local() && !validOwner(cast, mc)) AcademyClient.yawPalmCancelInput();
        for (var ended : STATE.tick(mc.isPaused(), c -> validOwner(c, mc))) if (ended.local())
            AcademyClient.yawPalmEndedInput(ended.id().input(), ended.id().epoch());
        pruneOwners();
    }
    private static boolean visible(YawPalmState.Controller cast, RenderLevelStageEvent event, Vec3 camera) {
        YawPalmMesh.Bounds decoration = YawPalmMesh.bounds(cast);
        AABB bounds = new AABB(vec(decoration.min()), vec(decoration.max()));
        Vec3 nearest = new Vec3(Math.max(bounds.minX,Math.min(bounds.maxX,camera.x)),
                Math.max(bounds.minY,Math.min(bounds.maxY,camera.y)),Math.max(bounds.minZ,Math.min(bounds.maxZ,camera.z)));
        if (camera.distanceToSqr(nearest) > YawPalmMesh.CULL_DISTANCE * YawPalmMesh.CULL_DISTANCE) return false;
        for (int x = Math.floorDiv((int)Math.floor(bounds.minX),16); x <= Math.floorDiv((int)Math.floor(bounds.maxX),16); x++)
            for (int z = Math.floorDiv((int)Math.floor(bounds.minZ),16); z <= Math.floorDiv((int)Math.floor(bounds.maxZ),16); z++)
                if (!world.hasChunkAt(new BlockPos(x*16,(int)Math.floor(bounds.minY),z*16))) return false;
        return event.getFrustum().isVisible(bounds);
    }
    private record RenderCast(YawPalmState.Controller controller, YawPalmMesh.Shape mesh) {}
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); Vec3 camera = event.getCamera().getPosition();
        double partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var render = new ArrayList<RenderCast>(YawPalmMesh.MAX_CONTROLLERS); int vertices = 0;
        for (var c : STATE.controllers()) {
            if (!validOwner(c,mc) || !visible(c,event,camera)) continue;
            var mesh = YawPalmMesh.shape(c,c.age()+partial);
            if (vertices + mesh.vertexCount() > YawPalmMesh.MAX_VERTICES || render.size() >= YawPalmMesh.MAX_CONTROLLERS) break;
            vertices += mesh.vertexCount(); render.add(new RenderCast(c,mesh));
        }
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
                    stroke(out,matrix,stroke,YawPalmMesh.profile(stroke,pass == Pass.CORE),camera,forward,fallback,owners.get(c.controller.id().owner()));
                buffers.endBatch(Material.quads(pass));
            }
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            }
        }
    }
    private static void stroke(VertexConsumer out, Matrix4f matrix, YawPalmMesh.Stroke stroke,
                               YawPalmMesh.Profile profile, Vec3 camera, Point forward, Vec3 fallback, Entity owner) {
        if (profile.alpha() <= 0 || owner == null) return;
        var clipped = YawPalmMesh.clipNear(new YawPalmMesh.Stroke(YawPalmMesh.relative(stroke.from(),point(camera)),
                YawPalmMesh.relative(stroke.to(),point(camera)),stroke.anchor(),profile.width(),profile.alpha(),stroke.role()),forward,profile.width()/2);
        if (clipped == null) return;
        Vec3 a = vec(clipped.from()), b = vec(clipped.to());
        Vec3 side = b.subtract(a).cross(a.add(b).scale(-.5)).normalize();
        if (side.lengthSqr() < 1e-10) side = fallback;
        side = side.scale(profile.width()/2);
        Vec3[] corners = {a.subtract(side),a.add(side),b.add(side),b.subtract(side)};
        // Test every emitted corner and edge, not merely the centerline. No decorative strip passes through a wall.
        Vec3 anchor = vec(stroke.anchor());
        for (int i=0;i<4;i++) {
            Vec3 current = corners[i].add(camera), next = corners[(i+1)%4].add(camera);
            if (!terrainClear(anchor,current,owner) || !terrainClear(current,next,owner)) return;
        }
        int alphaA = (int)Math.round(profile.alpha()*YawPalmMesh.nearEyeFade(a.length()));
        int alphaB = (int)Math.round(profile.alpha()*YawPalmMesh.nearEyeFade(b.length()));
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
            return TYPES.computeIfAbsent(pass,p -> create("academy_yaw_palm_"+p,DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,8192,false,true,CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(GLOW_LINE,false,false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                            .setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY).setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
    }

    /** Read-only snapshot: points = origin then (end, velocity delta) per contact. */
    private static double[] dp(Point p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var out = new java.util.ArrayList<DecorSnap>();
        for (var c : STATE.controllers()) {
            if (world == null || !validOwner(c, mc)) continue;
            var points = new java.util.ArrayList<double[]>(); points.add(dp(c.origin()));
            for (var contact : c.contacts()) { points.add(dp(contact.end())); points.add(dp(contact.velocityDelta())); }
            out.add(DecorSnap.of(c.local(), c.age(), 0, c.id().token(), points, c.crouch() ? 1 : 0));
        }
        return out;
    }
}

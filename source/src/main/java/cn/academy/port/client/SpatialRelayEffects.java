package cn.academy.port.client;

import cn.academy.port.highpower.SpatialRelay;
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

/** NEW343 owner-only planned routes and confirmed source/destination cues; ordinary item rendering stays native. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class SpatialRelayEffects {
    private static final double NEAR_PLANE = .08;
    private static final ResourceLocation[] MARKS = new ResourceLocation[SpatialRelayMesh.SUCCESS_TICKS];
    private static final SpatialRelayState STATE = new SpatialRelayState();
    private static Cosmetic cosmetic;
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static long generation;
    private static MultiBufferSource.BufferSource buffers;
    private static final class Cosmetic {
        final Entity owner;
        final SpatialRelayState.Frame frame;
        int age;
        Cosmetic(Entity owner, SpatialRelayState.Frame frame) { this.owner = owner; this.frame = frame; }
    }
    static { for (int i = 0; i < MARKS.length; i++) MARKS[i] = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/tp_mark/" + i + ".png"); }
    private SpatialRelayEffects() {}
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { SpatialRelay.clientReceiver = SpatialRelayEffects::receive; }
    }
    private static SpatialRelayState.Leg leg(SpatialRelay.Leg p) {
        return p == null ? null : new SpatialRelayState.Leg(p.itemEntityId(),point(p.from()),point(p.to()));
    }
    private static SpatialRelayState.Frame frame(SpatialRelay.Visual p) {
        return new SpatialRelayState.Frame(new SpatialRelayState.Identity(p.entity(),p.owner(),p.epoch(),p.input(),p.token()),
                p.sequence(),p.phase(),p.remainingTicks(),point(p.markA()),point(p.markB()),point(p.markC()),
                p.eligible(),p.queued(),p.completed(),p.planned().stream().map(SpatialRelayEffects::leg).toList(),leg(p.confirmed()),p.reason());
    }
    public static void receive(SpatialRelay.Visual packet) {
        if (packet == null || packet.planned() == null || packet.planned().size() > SpatialRelayState.MAX_LEGS
                || packet.planned().stream().anyMatch(java.util.Objects::isNull)) return;
        var receipt = frame(packet); if (!SpatialRelayState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.entity());
            if (!(owner instanceof Player) || !owner.getUUID().equals(packet.owner()) || !owner.isAlive() || owner.isRemoved()) return;
            boolean local = owner == mc.player;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.spatialRelayReceiptMatches(packet.input(),packet.epoch(),SpatialRelayState.terminal(receipt)))) return;
            if (local && !AcademyClient.spatialRelayControlValid()) { AcademyClient.spatialRelayCancelInput(); return; }
            if (local && packet.phase() == SpatialRelayState.REFUSED && "queue_running".equals(packet.reason()) && STATE.queued()) {
                if (!STATE.acceptQueueRefusal(receipt)) return;
                AcademyClient.spatialRelayRejectedGesture(packet.input(),packet.epoch());
                mc.player.displayClientMessage(Component.translatable("academy.highpower.spatial_relay.reason.queue_running"),true);
                return;
            }
            if (!STATE.accept(receipt,local)) return;
            if (local) {
                if (SpatialRelayState.terminal(receipt)) AcademyClient.spatialRelayEndedInput(packet.input(),packet.epoch());
                else AcademyClient.spatialRelayAcceptedInput(packet.input(),packet.epoch(),STATE.remainingTicks(),STATE.queued());
            }
            if (packet.phase() == SpatialRelayState.MOVED) {
                // One real confirmed endpoint pair globally: 48 planned strokes + 16 cue primitives = 64 decor.
                if (cosmetic == null || local || cosmetic.owner != mc.player) cosmetic = new Cosmetic(owner,receipt);
            } else if (SpatialRelayState.terminal(receipt) && cosmetic != null && sameController(cosmetic.frame,receipt)
                    && !SpatialRelayState.preservesConfirmedCue(receipt,cosmetic.frame)) cosmetic = null;
            if (packet.phase() == SpatialRelayState.REFUSED && local) {
                String key = "academy.highpower.spatial_relay.reason." + packet.reason();
                Component detail = net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key)
                        : Component.translatable("academy.highpower.spatial_relay.refused");
                mc.player.displayClientMessage(Component.translatable("academy.highpower.spatial_relay.blocked",detail),true);
            }
        });
    }
    public static void abortLocal(long input, long epoch) {
        STATE.cancel(epoch,input);
        if (cosmetic != null && cosmetic.owner == Minecraft.getInstance().player && cosmetic.frame.id().epoch() == epoch
                && cosmetic.frame.id().input() <= input) cosmetic = null;
    }
    public static void clear() { generation++; STATE.clear(); cosmetic = null; }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        if (STATE.active() != null) AcademyClient.spatialRelayCancelInput();
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean validOwner(Entity owner, SpatialRelayState.Frame f) {
        return world != null && owner != null && owner.isAlive() && !owner.isRemoved()
                && world.getEntity(f.id().entity()) == owner && owner.getUUID().equals(f.id().owner());
    }
    private static boolean locallyOwned(SpatialRelayState.Frame f, Minecraft mc) {
        return f != null && validOwner(mc.player,f) && AcademyClient.singleOwnerEpochMatches(f.id().epoch())
                && AcademyClient.spatialRelayInputMatches(f.id().input(),f.id().epoch()) && AcademyClient.spatialRelayControlValid();
    }
    private static boolean cosmeticContextValid(Minecraft mc) {
        var state = AcademyClient.state;
        return mc.player != null && mc.level != null && mc.player.isAlive() && !mc.player.isRemoved() && !mc.player.isSpectator()
                && mc.screen == null && mc.getCameraEntity() == mc.player && state.isActivated() && state.overloadFine && !state.interfering
                && "teleporter".equals(state.category) && state.level >= 4 && state.learned(SpatialRelay.ID)
                && cn.academy.port.SkillCatalog.enabled(state.category,SpatialRelay.ID)
                && AcademyClient.skillKey(SpatialRelay.ID) != null;
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        return STATE.markerVisible() && locallyOwned(STATE.active(),mc) ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (STATE.tick(mc.isPaused(),locallyOwned(STATE.active(),mc)) != null) AcademyClient.spatialRelayCancelInput();
        if (cosmetic != null && (!validOwner(cosmetic.owner,cosmetic.frame)
                || cosmetic.owner == mc.player && !cosmeticContextValid(mc)
                || !mc.isPaused() && ++cosmetic.age >= SpatialRelayMesh.SUCCESS_TICKS)) cosmetic = null;
    }
    @SubscribeEvent public static void guidance(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (!cosmeticContextValid(mc) || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        var active = STATE.active();
        boolean marked = STATE.markerVisible() && locallyOwned(active,mc);
        int points = marked ? active.pointCount() : 0;
        boolean previewUnavailable = marked && !STATE.queued() && active.eligible() == 0 && !active.reason().isEmpty();
        Object eligible = previewUnavailable ? "?" : marked ? active.eligible() : 0;
        var lines = new ArrayList<Component>();
        String next = !marked && active != null ? "waiting" : STATE.queued() && marked ? "queue"
                : points == 0 ? "place_a" : points == 1 ? "place_b" : points == 2 ? "place_c" : "start";
        lines.add(Component.translatable("academy.highpower.spatial_relay.guide."+next));
        if (marked) lines.add(STATE.queued()
                ? Component.translatable("academy.highpower.spatial_relay.guide.progress",points,active.eligible(),active.queued(),active.completed())
                : Component.translatable("academy.highpower.spatial_relay.guide.count",points,eligible,(STATE.remainingTicks()+19)/20));
        else if (active == null) lines.add(Component.translatable("academy.highpower.spatial_relay.guide.empty"));
        if (previewUnavailable) {
            String key = "academy.highpower.spatial_relay.reason."+active.reason();
            lines.add(Component.translatable(net.minecraft.locale.Language.getInstance().has(key) ? key : "academy.highpower.spatial_relay.reason.preview_unavailable"));
        }
        lines.add(Component.translatable("academy.highpower.spatial_relay.guide.clear"));
        var graphics = event.getGuiGraphics();
        int width = CornerExchangeHudLayout.textWidth(graphics.guiWidth()); if (width == 0) return;
        var rows = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (var line : lines) rows.addAll(mc.font.split(line,width));
        var layout = CornerExchangeHudLayout.fit(graphics.guiWidth(),graphics.guiHeight(),rows.size(),mc.font.lineHeight);
        if (layout == null) {
            rows.clear();
            rows.addAll(mc.font.split(Component.translatable("academy.highpower.spatial_relay.compact."+next),width));
            if (marked) rows.addAll(mc.font.split(Component.translatable("academy.highpower.spatial_relay.compact.count",points,eligible),width));
            rows.addAll(mc.font.split(Component.translatable("academy.highpower.spatial_relay.compact.clear"),width));
            layout = CornerExchangeHudLayout.fit(graphics.guiWidth(),graphics.guiHeight(),rows.size(),mc.font.lineHeight);
        }
        if (layout == null) return;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(graphics.guiWidth()/2f,layout.top(),0); graphics.pose().scale(layout.scale(),layout.scale(),1);
            int y = 0;
            for (var row : rows) { graphics.drawString(mc.font,row,-mc.font.width(row)/2,y,0xFFE2E7E8,true); y += layout.lineStep(); }
        } finally { graphics.pose().popPose(); }
    }
    private static boolean visible(RenderLevelStageEvent event, Point p, Vec3 camera) {
        if (p == null || world == null || camera.distanceToSqr(vec(p)) > SpatialRelayMesh.CULL_DISTANCE * SpatialRelayMesh.CULL_DISTANCE) return false;
        var b = new AABB(p.x()-.6,p.y()-.6,p.z()-.6,p.x()+.6,p.y()+.6,p.z()+.6);
        for (int x = Math.floorDiv((int)Math.floor(b.minX),16); x <= Math.floorDiv((int)Math.floor(b.maxX),16); x++)
            for (int z = Math.floorDiv((int)Math.floor(b.minZ),16); z <= Math.floorDiv((int)Math.floor(b.maxZ),16); z++)
                if (!world.hasChunkAt(new BlockPos(x*16,(int)Math.floor(b.minY),z*16))) return false;
        return event.getFrustum().isVisible(b);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var active = STATE.active(); if (world == null || active == null && cosmetic == null) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(32768));
        var poses = event.getPoseStack(); Vec3 camera = event.getCamera().getPosition();
        var look = event.getCamera().getLookVector(); Vec3 forward = new Vec3(look.x(),look.y(),look.z());
        var modelView = RenderSystem.getModelViewStack(); float[] color = RenderSystem.getShaderColor().clone();
        modelView.pushMatrix(); poses.pushPose();
        try {
            RenderSystem.setShaderColor(1,1,1,1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = poses.last().pose();
            if (STATE.markerVisible() && locallyOwned(active,mc)) {
                Point[] marks = {active.markA(),active.markB(),active.markC()};
                for (int i=0;i<SpatialRelayMesh.MAX_MARKERS;i++) if (visible(event,marks[i],camera)) {
                    lines(matrix,SpatialRelayMesh.marker(marks[i]),camera,forward,200);
                    label(event,marks[i],Component.translatable("academy.highpower.spatial_relay.marked",
                            Character.toString((char)('A'+i)),(STATE.remainingTicks()+19)/20),camera,forward);
                }
                for (var line : SpatialRelayMesh.planned(active.planned())) if (routeVisible(event,line,camera))
                    lines(matrix,List.of(line),camera,forward,145);
            }
            if (cosmetic != null && validOwner(cosmetic.owner,cosmetic.frame)) {
                int alpha = SpatialRelayMesh.alpha(cosmetic.age);
                var confirmed = cosmetic.frame.confirmed();
                endpoint(event,matrix,confirmed.from(),camera,forward,alpha,cosmetic.age);
                endpoint(event,matrix,confirmed.to(),camera,forward,alpha,cosmetic.age);
            }
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            }
        }
    }
    private static boolean routeVisible(RenderLevelStageEvent event, SpatialRelayMesh.Line line, Vec3 camera) {
        Vec3 a = vec(line.from()), b = vec(line.to());
        if (Math.min(a.distanceToSqr(camera),b.distanceToSqr(camera)) > SpatialRelayMesh.CULL_DISTANCE*SpatialRelayMesh.CULL_DISTANCE) return false;
        int steps = Math.min(32,Math.max(1,(int)Math.ceil(a.distanceTo(b)/8)));
        for (int i=0;i<=steps;i++) if (!world.hasChunkAt(BlockPos.containing(a.lerp(b,(double)i/steps)))) return false;
        return event.getFrustum().isVisible(new AABB(a,b).inflate(.03));
    }
    private static void endpoint(RenderLevelStageEvent event, Matrix4f matrix, Point p,
                                 Vec3 camera, Vec3 forward, int alpha, int age) {
        if (!visible(event,p,camera)) return;
        var segments = SpatialRelayMesh.endpoint(p);
        lines(matrix,segments,camera,forward,alpha);
        var out = buffers.getBuffer(Material.quads(MARKS[SpatialRelayMesh.frame(age)]));
        for (var segment : segments) stroke(out,matrix,segment,camera,forward,Math.min(170,alpha));
    }
    private static void lines(Matrix4f matrix, List<SpatialRelayMesh.Line> segments, Vec3 camera, Vec3 forward, int alpha) {
        var out = buffers.getBuffer(Material.LINES);
        for (var line : segments) {
            Vec3 a = vec(SpatialRelayMesh.relative(line.from(),point(camera))), b = vec(SpatialRelayMesh.relative(line.to(),point(camera)));
            double da = a.dot(forward), db = b.dot(forward);
            if (da <= NEAR_PLANE && db <= NEAR_PLANE) continue;
            if (da < NEAR_PLANE) a = a.lerp(b,(NEAR_PLANE-da)/(db-da));
            else if (db < NEAR_PLANE) b = a.lerp(b,(NEAR_PLANE-da)/(db-da));
            Vec3 normal = b.subtract(a).normalize();
            out.addVertex(matrix,(float)a.x,(float)a.y,(float)a.z).setColor(220,225,226,alpha).setNormal((float)normal.x,(float)normal.y,(float)normal.z);
            out.addVertex(matrix,(float)b.x,(float)b.y,(float)b.z).setColor(220,225,226,alpha).setNormal((float)normal.x,(float)normal.y,(float)normal.z);
        }
    }
    private static void stroke(VertexConsumer out, Matrix4f matrix, SpatialRelayMesh.Line line, Vec3 camera, Vec3 forward, int alpha) {
        Vec3 a = vec(SpatialRelayMesh.relative(line.from(),point(camera))), b = vec(SpatialRelayMesh.relative(line.to(),point(camera)));
        Vec3 side = b.subtract(a).cross(a.scale(-1)).normalize().scale(.009);
        if (side.lengthSqr() < 1e-10 || a.dot(forward) <= NEAR_PLANE+.009 || b.dot(forward) <= NEAR_PLANE+.009) return;
        vertex(out,matrix,a.subtract(side),.03125f,.25f,alpha); vertex(out,matrix,a.add(side),.03125f,.46875f,alpha);
        vertex(out,matrix,b.add(side),.484375f,.46875f,alpha); vertex(out,matrix,b.subtract(side),.484375f,.25f,alpha);
    }
    private static void label(RenderLevelStageEvent event, Point p, Component text, Vec3 camera, Vec3 forward) {
        Minecraft mc = Minecraft.getInstance(); if (mc.screen != null || mc.options.hideGui) return;
        Vec3 at = vec(p).add(0,.48,0), relative = at.subtract(camera);
        int steps = Math.max(1,(int)Math.ceil(relative.length()/8));
        for (int i = 0; i <= steps; i++) if (!world.hasChunkAt(BlockPos.containing(camera.lerp(at,(double)i/steps)))) return;
        if (world.clip(new ClipContext(camera,at,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player)).getType() != HitResult.Type.MISS) return;
        float scale = CrossSubstitutionState.labelScale(relative.dot(forward),relative.length(),mc.font.width(text));
        if (scale <= 0) return;
        var poses = event.getPoseStack(); poses.pushPose();
        try {
            poses.translate(relative.x,relative.y,relative.z); poses.mulPose(event.getCamera().rotation()); poses.scale(scale,-scale,scale);
            mc.font.drawInBatch(text,-mc.font.width(text)/2f,0,0xFFE2E7E8,true,poses.last().pose(),buffers,Font.DisplayMode.NORMAL,0,15728880);
        } finally { poses.popPose(); }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 relative, float u, float v, int alpha) {
        out.addVertex(matrix,(float)relative.x,(float)relative.y,(float)relative.z).setUv(u,v).setColor(230,240,244,alpha);
    }
    private static Point point(Vec3 p) { return p == null ? null : new Point(p.x,p.y,p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(),p.y(),p.z()); }
    private static boolean sameController(SpatialRelayState.Frame a, SpatialRelayState.Frame b) {
        return a.id().owner().equals(b.id().owner()) && a.id().epoch() == b.id().epoch() && a.id().token() == b.id().token();
    }
    private static final class Material extends RenderType {
        private static final Map<ResourceLocation,RenderType> TYPES = new HashMap<>();
        private Material(String n,VertexFormat f,VertexFormat.Mode m,int s,boolean c,boolean sort,Runnable set,Runnable reset) { super(n,f,m,s,c,sort,set,reset); }
        static RenderType quads(ResourceLocation texture) {
            return TYPES.computeIfAbsent(texture,t -> create("academy_spatial_relay_"+t.getPath(),DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,4096,false,true,CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(t,false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
        static final RenderType LINES = create("academy_spatial_relay_lines",DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES,2048,false,false,CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                        .setLineState(new LineStateShard(OptionalDouble.of(1.5))).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setOutputState(PARTICLES_TARGET).createCompositeState(false));
    }

    /** Read-only snapshot for the decoration layer (phase 0 = aiming/active, 1 = success, 2 = refusal/warning): points = mark A, B, C, then (from, to) per leg; numbers = eligible, queued, completed. */
    private static double[] dp(cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        var a = STATE.active();
        java.util.function.BiConsumer<SpatialRelayState.Frame, int[]> put = (f, meta) -> {
            var points = new java.util.ArrayList<double[]>(java.util.List.of(dp(f.markA()), dp(f.markB()), dp(f.markC())));
            var legs = new java.util.ArrayList<SpatialRelayState.Leg>(); if (f.planned() != null) legs.addAll(f.planned()); if (f.confirmed() != null) legs.add(f.confirmed());
            for (var leg : legs) if (points.size() < 15) { points.add(dp(leg.from())); points.add(dp(leg.to())); }
            out.add(DecorSnap.of(meta[0] == 1, meta[1], meta[2], f.id().token(), points, f.eligible(), f.queued(), f.completed()));
        };
        if (a != null) put.accept(a, new int[] {1, 0, 0});
        if (cosmetic != null && validOwner(cosmetic.owner, cosmetic.frame)) put.accept(cosmetic.frame, new int[] {cosmetic.owner == mc.player ? 1 : 0, cosmetic.age, 1});
        return out;
    }
}

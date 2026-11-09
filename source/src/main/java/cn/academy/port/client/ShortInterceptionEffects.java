package cn.academy.port.client;

import cn.academy.port.highpower.ShortInterception;
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

/** NEW331 private target mark, separated confirmed spatial sections, and native-contact-only ticks. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ShortInterceptionEffects {
    private static final double NEAR_PLANE = .08;
    private static final ResourceLocation[] MARKS = new ResourceLocation[ShortInterceptionMesh.SUCCESS_TICKS];
    private static final ShortInterceptionState STATE = new ShortInterceptionState();
    private static final List<Cosmetic> cosmetics = new ArrayList<>(ShortInterceptionMesh.MAX_GROUPS);
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static long generation;
    private static MultiBufferSource.BufferSource buffers;
    private static final class Cosmetic {
        final Entity owner;
        final ShortInterceptionState.Frame frame;
        int age;
        Cosmetic(Entity owner, ShortInterceptionState.Frame frame) { this.owner = owner; this.frame = frame; }
        int cost() { return frame.segments().size(); }
    }
    static { for (int i = 0; i < MARKS.length; i++) MARKS[i] = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/tp_mark/" + i + ".png"); }
    private ShortInterceptionEffects() {}
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { ShortInterception.clientReceiver = ShortInterceptionEffects::receive; }
    }
    private static ShortInterceptionState.Frame frame(ShortInterception.Visual p) {
        return new ShortInterceptionState.Frame(new ShortInterceptionState.Identity(p.entity(),p.owner(),p.epoch(),p.input(),p.token()),
                p.sequence(),p.phase(),p.remainingTicks(),point(p.target()),p.segments().stream().map(s ->
                new ShortInterceptionState.Segment(point(s.entry()),point(s.exit()),point(s.incoming()),point(s.outgoing()))).toList(),
                p.reason(),p.fallback(),p.admitted());
    }
    public static void receive(ShortInterception.Visual packet) {
        if (packet == null || packet.segments() == null || packet.segments().size() > ShortInterceptionMesh.MAX_GROUPS
                || packet.segments().stream().anyMatch(java.util.Objects::isNull)) return;
        var receipt = frame(packet); if (!ShortInterceptionState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || generation != capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.entity());
            if (!(owner instanceof Player) || !owner.getUUID().equals(packet.owner()) || !owner.isAlive() || owner.isRemoved()) return;
            boolean local = owner == mc.player, impact = packet.phase() == ShortInterceptionState.IMPACT;
            if (local) {
                if (!AcademyClient.singleOwnerEpochMatches(packet.epoch())) return;
                if (impact) {
                    if (!cosmeticContextValid(mc) || !STATE.permitsImpact(receipt)) return;
                } else if (!STATE.permitsEnd(receipt)) {
                    // Native down can receive a free clear, but cannot authenticate PROJECTED before genuine release.
                    boolean clear = packet.phase() == ShortInterceptionState.CANCELLED || packet.phase() == ShortInterceptionState.ENDED
                            || packet.phase() == ShortInterceptionState.REFUSED && packet.remainingTicks() == 0;
                    if (!AcademyClient.shortInterceptionReceiptMatches(packet.input(),packet.epoch(),clear)) return;
                    if (!AcademyClient.shortInterceptionControlValid()) { AcademyClient.shortInterceptionCancelInput(); return; }
                }
            }
            if (!STATE.accept(receipt, local)) return;
            if (local && !impact) {
                if (ShortInterceptionState.terminal(receipt)) AcademyClient.shortInterceptionEndedInput(packet.input(),packet.epoch());
                else AcademyClient.shortInterceptionAcceptedInput(packet.input(),packet.epoch(),STATE.remainingTicks(),true);
            }
            if (packet.phase() == ShortInterceptionState.PROJECTED || impact) addCosmetic(owner,receipt,local);
            else if (ShortInterceptionState.terminal(receipt)) cosmetics.removeIf(c -> sameController(c.frame,receipt));
            if (packet.phase() == ShortInterceptionState.REFUSED && local) {
                String key = "academy.highpower.short_interception.reason." + packet.reason();
                Component detail = net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key)
                        : Component.translatable("academy.highpower.short_interception.refused");
                mc.player.displayClientMessage(Component.translatable("academy.highpower.short_interception.blocked",detail),true);
            }
        });
    }
    private static void addCosmetic(Entity owner, ShortInterceptionState.Frame frame, boolean local) {
        int cost = frame.segments().size();
        while (cosmetics.stream().mapToInt(Cosmetic::cost).sum() + cost > ShortInterceptionMesh.MAX_GROUPS) {
            var older = cosmetics.stream().filter(c -> c.owner != Minecraft.getInstance().player).findFirst().orElse(null);
            if (older != null) cosmetics.remove(older);
            else if (local && !cosmetics.isEmpty()) cosmetics.removeFirst();
            else return;
        }
        cosmetics.add(new Cosmetic(owner,frame));
    }
    public static void abortLocal(long input, long epoch) {
        STATE.cancel(epoch,input);
        cosmetics.removeIf(c -> c.owner == Minecraft.getInstance().player && c.frame.id().epoch() == epoch && c.frame.id().input() <= input);
    }
    public static void clear() { generation++; STATE.clear(); cosmetics.clear(); }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        if (STATE.active() != null) AcademyClient.shortInterceptionCancelInput();
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean validOwner(Entity owner, ShortInterceptionState.Frame f) {
        return world != null && owner != null && owner.isAlive() && !owner.isRemoved()
                && world.getEntity(f.id().entity()) == owner && owner.getUUID().equals(f.id().owner());
    }
    private static boolean locallyOwned(ShortInterceptionState.Frame f, Minecraft mc) {
        return f != null && validOwner(mc.player,f) && AcademyClient.singleOwnerEpochMatches(f.id().epoch())
                && AcademyClient.shortInterceptionInputMatches(f.id().input(),f.id().epoch()) && AcademyClient.shortInterceptionControlValid();
    }
    private static boolean cosmeticContextValid(Minecraft mc) {
        var state = AcademyClient.state;
        return mc.player != null && mc.level != null && mc.player.isAlive() && !mc.player.isRemoved() && !mc.player.isSpectator()
                && mc.screen == null && mc.getCameraEntity() == mc.player && state.isActivated() && state.overloadFine && !state.interfering
                && "teleporter".equals(state.category) && state.level >= 3 && state.learned(ShortInterception.ID)
                && cn.academy.port.SkillCatalog.enabled(state.category,ShortInterception.ID)
                && AcademyClient.skillKey(ShortInterception.ID) != null;
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        return STATE.markerVisible() && locallyOwned(STATE.active(),mc) ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (STATE.tick(mc.isPaused(),locallyOwned(STATE.active(),mc)) != null) AcademyClient.shortInterceptionCancelInput();
        cosmetics.removeIf(c -> {
            if (!validOwner(c.owner,c.frame) || c.owner == mc.player && !cosmeticContextValid(mc)) { STATE.retire(c.frame.id()); return true; }
            int life = c.frame.phase() == ShortInterceptionState.IMPACT ? 3 : ShortInterceptionMesh.SUCCESS_TICKS;
            return !mc.isPaused() && ++c.age >= life;
        });
    }
    @SubscribeEvent public static void guidance(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (!cosmeticContextValid(mc) || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        var active = STATE.active();
        boolean marked = STATE.markerVisible() && locallyOwned(active,mc);
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable(marked ? "academy.highpower.short_interception.guide.intercept"
                : active == null ? "academy.highpower.short_interception.guide.target" : "academy.highpower.short_interception.guide.waiting"));
        if (marked) lines.add(Component.translatable("academy.highpower.short_interception.guide.fallback"));
        lines.add(Component.translatable("academy.highpower.short_interception.guide.clear"));
        var graphics = event.getGuiGraphics();
        int width = CornerExchangeHudLayout.textWidth(graphics.guiWidth()); if (width == 0) return;
        var rows = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (var line : lines) rows.addAll(mc.font.split(line,width));
        var layout = CornerExchangeHudLayout.fit(graphics.guiWidth(),graphics.guiHeight(),rows.size(),mc.font.lineHeight);
        if (layout == null && lines.size() > 2) {
            rows.clear(); rows.addAll(mc.font.split(lines.getFirst(),width)); rows.addAll(mc.font.split(lines.getLast(),width));
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
        if (p == null || world == null || camera.distanceToSqr(vec(p)) > ShortInterceptionMesh.CULL_DISTANCE * ShortInterceptionMesh.CULL_DISTANCE) return false;
        var b = new AABB(p.x()-.6,p.y()-.6,p.z()-.6,p.x()+.6,p.y()+.6,p.z()+.6);
        for (int x = Math.floorDiv((int)Math.floor(b.minX),16); x <= Math.floorDiv((int)Math.floor(b.maxX),16); x++)
            for (int z = Math.floorDiv((int)Math.floor(b.minZ),16); z <= Math.floorDiv((int)Math.floor(b.maxZ),16); z++)
                if (!world.hasChunkAt(new BlockPos(x*16,(int)Math.floor(b.minY),z*16))) return false;
        return event.getFrustum().isVisible(b);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var active = STATE.active(); if (world == null || active == null && cosmetics.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(32768));
        var poses = event.getPoseStack(); Vec3 camera = event.getCamera().getPosition();
        var look = event.getCamera().getLookVector(); Vec3 forward = new Vec3(look.x(),look.y(),look.z());
        var modelView = RenderSystem.getModelViewStack(); float[] color = RenderSystem.getShaderColor().clone();
        modelView.pushMatrix(); poses.pushPose();
        try {
            RenderSystem.setShaderColor(1,1,1,1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = poses.last().pose();
            if (STATE.markerVisible() && locallyOwned(active,mc) && visible(event,active.target(),camera)) {
                lines(matrix,ShortInterceptionMesh.target(active.target()),camera,forward,200);
                label(event,active.target(),Component.translatable("academy.highpower.short_interception.marked",(STATE.remainingTicks()+19)/20),camera,forward);
            }
            for (var cosmetic : cosmetics) if (validOwner(cosmetic.owner,cosmetic.frame)) {
                int alpha = ShortInterceptionMesh.alpha(cosmetic.age);
                for (var segment : cosmetic.frame.segments()) {
                    if (cosmetic.frame.phase() == ShortInterceptionState.IMPACT) {
                        if (visible(event,segment.exit(),camera)) lines(matrix,ShortInterceptionMesh.target(segment.exit()),camera,forward,alpha);
                        continue;
                    }
                    endpoint(event,matrix,segment.entry(),segment.incoming(),true,camera,forward,alpha,cosmetic.age);
                    endpoint(event,matrix,segment.exit(),segment.outgoing(),false,camera,forward,alpha,cosmetic.age);
                }
            }
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            }
        }
    }
    private static void endpoint(RenderLevelStageEvent event, Matrix4f matrix, Point p, Point direction, boolean entry,
                                 Vec3 camera, Vec3 forward, int alpha, int age) {
        if (!visible(event,p,camera)) return;
        var segments = ShortInterceptionMesh.endpoint(p,direction,entry);
        lines(matrix,segments,camera,forward,alpha);
        var out = buffers.getBuffer(Material.quads(MARKS[ShortInterceptionMesh.frame(age)]));
        for (int i = 0; i < 4; i++) stroke(out,matrix,segments.get(i),camera,forward,Math.min(150,alpha));
        label(event,p,Component.translatable(entry ? "academy.highpower.short_interception.entry" : "academy.highpower.short_interception.exit"),camera,forward);
    }
    private static void lines(Matrix4f matrix, List<ShortInterceptionMesh.Line> segments, Vec3 camera, Vec3 forward, int alpha) {
        var out = buffers.getBuffer(Material.LINES);
        for (var line : segments) {
            Vec3 a = vec(ShortInterceptionMesh.relative(line.from(),point(camera))), b = vec(ShortInterceptionMesh.relative(line.to(),point(camera)));
            double da = a.dot(forward), db = b.dot(forward);
            if (da <= NEAR_PLANE && db <= NEAR_PLANE) continue;
            if (da < NEAR_PLANE) a = a.lerp(b,(NEAR_PLANE-da)/(db-da));
            else if (db < NEAR_PLANE) b = a.lerp(b,(NEAR_PLANE-da)/(db-da));
            Vec3 normal = b.subtract(a).normalize();
            out.addVertex(matrix,(float)a.x,(float)a.y,(float)a.z).setColor(220,225,226,alpha).setNormal((float)normal.x,(float)normal.y,(float)normal.z);
            out.addVertex(matrix,(float)b.x,(float)b.y,(float)b.z).setColor(220,225,226,alpha).setNormal((float)normal.x,(float)normal.y,(float)normal.z);
        }
    }
    private static void stroke(VertexConsumer out, Matrix4f matrix, ShortInterceptionMesh.Line line, Vec3 camera, Vec3 forward, int alpha) {
        Vec3 a = vec(ShortInterceptionMesh.relative(line.from(),point(camera))), b = vec(ShortInterceptionMesh.relative(line.to(),point(camera)));
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
    private static boolean sameController(ShortInterceptionState.Frame a, ShortInterceptionState.Frame b) {
        return a.id().owner().equals(b.id().owner()) && a.id().epoch() == b.id().epoch() && a.id().token() == b.id().token();
    }
    private static final class Material extends RenderType {
        private static final Map<ResourceLocation,RenderType> TYPES = new HashMap<>();
        private Material(String n,VertexFormat f,VertexFormat.Mode m,int s,boolean c,boolean sort,Runnable set,Runnable reset) { super(n,f,m,s,c,sort,set,reset); }
        static RenderType quads(ResourceLocation texture) {
            return TYPES.computeIfAbsent(texture,t -> create("academy_short_interception_"+t.getPath(),DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,4096,false,true,CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(t,false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
        static final RenderType LINES = create("academy_short_interception_lines",DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES,2048,false,false,CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                        .setLineState(new LineStateShard(OptionalDouble.of(1.5))).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setOutputState(PARTICLES_TARGET).createCompositeState(false));
    }

    /** Read-only snapshot for the decoration layer (phase 0 = aiming/active, 1 = success, 2 = refusal/warning): points = target, then (entry, exit) per segment. */
    private static double[] dp(cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        var a = STATE.active();
        java.util.function.BiConsumer<ShortInterceptionState.Frame, int[]> put = (f, meta) -> {
            var points = new java.util.ArrayList<double[]>(); points.add(dp(f.target()));
            if (f.segments() != null) for (var seg : f.segments()) if (points.size() < 13) { points.add(dp(seg.entry())); points.add(dp(seg.exit())); }
            out.add(DecorSnap.of(meta[0] == 1, meta[1], meta[2], f.id().token(), points, f.remainingTicks()));
        };
        if (a != null) put.accept(a, new int[] {1, 0, 0});
        for (var c : cosmetics) if (validOwner(c.owner, c.frame)) put.accept(c.frame, new int[] {c.owner == mc.player ? 1 : 0, c.age, 1});
        return out;
    }
}

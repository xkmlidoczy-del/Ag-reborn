package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Private stationary plans use vanilla depth; successful movement alone earns a classic endpoint cue. */
@EventBusSubscriber(modid="academy", value=Dist.CLIENT)
public final class TacticalCoordinateEffects {
    private static final ResourceLocation[] MARKS = new ResourceLocation[TacticalCoordinateMesh.SUCCESS_TICKS];
    private static MultiBufferSource.BufferSource buffers;
    private static String dimension;
    private static Vec3 from, to;
    private static int age;
    private static Object confirmedLevel, confirmedPlayer, confirmedConnection;
    static { for (int i = 0; i < MARKS.length; i++) MARKS[i] = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/tp_mark/"+i+".png"); }
    private TacticalCoordinateEffects() {}
    public static void clear() { from = null; to = null; dimension = null; confirmedLevel = null; confirmedPlayer = null; confirmedConnection = null; age = 0; }
    public static void confirm(String world, Vec3 start, Vec3 destination) {
        Minecraft mc = Minecraft.getInstance();
        if (start == null || destination == null || start.distanceToSqr(destination) <= 0 || mc.level == null || mc.player == null
                || !mc.level.dimension().location().toString().equals(world)) return;
        dimension = world; from = start; to = destination; age = 0; confirmedLevel = mc.level; confirmedPlayer = mc.player; confirmedConnection = mc.getConnection();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (from == null) return;
        if (mc.level != confirmedLevel || mc.player != confirmedPlayer || mc.getConnection() != confirmedConnection || mc.player == null
                || !mc.player.isAlive() || mc.player.isRemoved() || !mc.isPaused() && ++age >= TacticalCoordinateMesh.SUCCESS_TICKS) clear();
    }
    private static boolean visible(RenderLevelStageEvent event, Vec3 point, Vec3 camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || point == null || point.distanceToSqr(camera) > TacticalCoordinateMesh.CULL_DISTANCE*TacticalCoordinateMesh.CULL_DISTANCE) return false;
        AABB bounds = new AABB(point.x-.5,point.y,point.z-.5,point.x+.5,point.y+2.2,point.z+.5);
        for (int x = Math.floorDiv((int)Math.floor(bounds.minX),16); x <= Math.floorDiv((int)Math.floor(bounds.maxX),16); x++)
            for (int z = Math.floorDiv((int)Math.floor(bounds.minZ),16); z <= Math.floorDiv((int)Math.floor(bounds.maxZ),16); z++)
                if (!mc.level.hasChunkAt(new BlockPos(x*16,(int)Math.floor(point.y),z*16))) return false;
        return event.getFrustum().isVisible(bounds);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); var snapshot = TacticalCoordinateClient.preview();
        if (mc.level == null || mc.player == null || mc.options.hideGui || snapshot == null && from == null) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(16384));
        var poses = event.getPoseStack(); Vec3 camera = event.getCamera().getPosition();
        var look = event.getCamera().getLookVector(); Vec3 forward = new Vec3(look.x(),look.y(),look.z());
        var modelView = RenderSystem.getModelViewStack(); float[] color = RenderSystem.getShaderColor().clone();
        modelView.pushMatrix(); poses.pushPose();
        try {
            RenderSystem.setShaderColor(1,1,1,1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = poses.last().pose();
            if (from != null && mc.level == confirmedLevel && mc.level.dimension().location().toString().equals(dimension)) {
                // Endpoint effects and previews are exclusive, keeping the global stroke budget below64.
                endpoint(event,matrix,from,camera,forward); endpoint(event,matrix,to,camera,forward);
            } else if (snapshot != null) {
                int count = 0;
                for (var template : snapshot.templates()) {
                    if (++count > TacticalCoordinateMesh.MAX_OUTLINES) break;
                    if (!template.dimension().equals(mc.level.dimension().location().toString()) || !visible(event,template.feet(),camera)) continue;
                    boolean selected = template.index() == snapshot.selected();
                    float yaw = selected ? snapshot.draftYaw() : template.yaw();
                    var consumer = buffers.getBuffer(Material.LINES);
                    for (var line : TacticalCoordinateMesh.outline(point(template.feet()),yaw)) line(consumer,matrix,line,camera,forward,selected ? 230 : 100,selected ? 232 : 166);
                    label(event,template.feet(),template.index()+1,camera,forward,selected);
                }
            }
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            }
        }
    }
    private static void line(VertexConsumer consumer, Matrix4f matrix, TacticalCoordinateMesh.Line line, Vec3 camera, Vec3 forward, int alpha, int gray) {
        Vec3 a = vector(TacticalCoordinateMesh.relative(line.from(),point(camera))), b = vector(TacticalCoordinateMesh.relative(line.to(),point(camera)));
        double da = a.dot(forward), db = b.dot(forward);
        if (da <= .08 && db <= .08) return;
        if (da < .08) a = a.lerp(b,(.08-da)/(db-da)); else if (db < .08) b = a.lerp(b,(.08-da)/(db-da));
        Vec3 normal = b.subtract(a).normalize();
        consumer.addVertex(matrix,(float)a.x,(float)a.y,(float)a.z).setColor(gray,gray+5,gray+7,alpha).setNormal((float)normal.x,(float)normal.y,(float)normal.z);
        consumer.addVertex(matrix,(float)b.x,(float)b.y,(float)b.z).setColor(gray,gray+5,gray+7,alpha).setNormal((float)normal.x,(float)normal.y,(float)normal.z);
    }
    private static void endpoint(RenderLevelStageEvent event, Matrix4f matrix, Vec3 feet, Vec3 camera, Vec3 forward) {
        if (!visible(event,feet,camera)) return;
        int alpha = TacticalCoordinateMesh.alpha(age); if (alpha <= 0) return;
        var consumer = buffers.getBuffer(Material.quads(MARKS[TacticalCoordinateMesh.frame(age)]));
        for (var line : TacticalCoordinateMesh.endpoint(point(feet))) {
            Vec3 a = vector(TacticalCoordinateMesh.relative(line.from(),point(camera))), b = vector(TacticalCoordinateMesh.relative(line.to(),point(camera)));
            Vec3 side = b.subtract(a).cross(a.scale(-1)).normalize().scale(.009);
            if (side.lengthSqr() < 1e-10 || a.dot(forward) <= .089 || b.dot(forward) <= .089) continue;
            vertex(consumer,matrix,a.subtract(side),.03125F,.25F,alpha); vertex(consumer,matrix,a.add(side),.03125F,.46875F,alpha);
            vertex(consumer,matrix,b.add(side),.484375F,.46875F,alpha); vertex(consumer,matrix,b.subtract(side),.484375F,.25F,alpha);
        }
    }
    private static void label(RenderLevelStageEvent event, Vec3 feet, int number, Vec3 camera, Vec3 forward, boolean selected) {
        Minecraft mc = Minecraft.getInstance(); Vec3 at = feet.add(0,2.02,0), relative = at.subtract(camera);
        int steps = Math.max(1,(int)Math.ceil(relative.length()/8));
        for (int i = 0; i <= steps; i++) if (!mc.level.hasChunkAt(BlockPos.containing(camera.lerp(at,(double)i/steps)))) return;
        if (mc.level.clip(new ClipContext(camera,at,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player)).getType() != HitResult.Type.MISS) return;
        Component text = Component.literal(Integer.toString(number));
        float scale = CrossSubstitutionState.labelScale(relative.dot(forward),relative.length(),mc.font.width(text)); if (scale <= 0) return;
        var poses = event.getPoseStack(); poses.pushPose();
        try {
            poses.translate(relative.x,relative.y,relative.z); poses.mulPose(event.getCamera().rotation()); poses.scale(scale,-scale,scale);
            mc.font.drawInBatch(text,-mc.font.width(text)/2F,0,selected ? 0xFFE2E7E8 : 0xFFA6ABAD,true,poses.last().pose(),buffers,Font.DisplayMode.NORMAL,0,15728880);
        } finally { poses.popPose(); }
    }
    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 relative, float u, float v, int alpha) {
        consumer.addVertex(matrix,(float)relative.x,(float)relative.y,(float)relative.z).setUv(u,v).setColor(230,240,244,alpha);
    }
    private static Point point(Vec3 p) { return new Point(p.x,p.y,p.z); }
    private static Vec3 vector(Point p) { return new Vec3(p.x(),p.y(),p.z()); }
    private static final class Material extends RenderType {
        private static final Map<ResourceLocation,RenderType> TYPES = new HashMap<>();
        private Material(String n,VertexFormat f,VertexFormat.Mode m,int s,boolean c,boolean sort,Runnable set,Runnable reset) { super(n,f,m,s,c,sort,set,reset); }
        static RenderType quads(ResourceLocation texture) {
            return TYPES.computeIfAbsent(texture,t -> create("academy_tactical_coordinate_"+t.getPath(),DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,4096,false,true,CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(t,false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
        static final RenderType LINES = create("academy_tactical_coordinate_lines",DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES,4096,false,false,CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                        .setLineState(new LineStateShard(OptionalDouble.of(1.5))).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setOutputState(PARTICLES_TARGET).createCompositeState(false));
    }

    /** Read-only snapshot for the decoration layer: phase 1, points = from, to. */
    static java.util.List<DecorSnap> decorSnaps() {
        if (from == null || to == null) return java.util.List.of();
        return java.util.List.of(DecorSnap.of(true, age, 1, Double.doubleToLongBits(from.x * 7 + to.z), java.util.List.of(new double[] {from.x, from.y, from.z}, new double[] {to.x, to.y, to.z})));
    }
}

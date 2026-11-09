package cn.academy.port.client;

import cn.academy.port.AcademyCraft;
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import cn.academy.port.highpower.MultidimensionalLandingArray;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
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
import org.joml.Vector4f;

/** Six private gray coordinates; advice, actual publication and confirmed native contact stay distinct. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MultidimensionalLandingArrayEffects {
    private static final String PREFIX = "academy.highpower.multidimensional_landing_array.";
    private static final ResourceLocation[] MARKS = new ResourceLocation[MultidimensionalLandingArrayMesh.SUCCESS_TICKS];
    private static final ResourceLocation EDGE_ENVELOPE = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/glow_line.png");
    private static final MultidimensionalLandingArrayMesh.VisualState STATE = new MultidimensionalLandingArrayMesh.VisualState();
    private static final List<Cue> CUES = new ArrayList<>(MultidimensionalLandingArrayMesh.MAX_CUES);
    private static Object world, localPlayer, connection;
    private static long generation;
    private static MultiBufferSource.BufferSource buffers;
    private record Label(Point at, Component text, boolean unavailable, int rowOffset, int priority, int order) {}
    private static final class Cue {
        final MultidimensionalLandingArrayMesh.Frame receipt;
        final Point at;
        final boolean contact;
        int age;
        Cue(MultidimensionalLandingArrayMesh.Frame receipt, Point at, boolean contact) {
            this.receipt = receipt; this.at = at; this.contact = contact;
        }
    }
    static {
        for (int i = 0; i < MARKS.length; i++) MARKS[i] = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/tp_mark/"+i+".png");
    }
    private MultidimensionalLandingArrayEffects() {}
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            MultidimensionalLandingArray.clientReceiver = MultidimensionalLandingArrayEffects::receive;
        }
    }
    private static MultidimensionalLandingArrayMesh.Frame frame(MultidimensionalLandingArray.Visual packet) {
        return new MultidimensionalLandingArrayMesh.Frame(
                new MultidimensionalLandingArrayMesh.Identity(packet.owner(), packet.epoch(), packet.input(), packet.token()),
                packet.sequence(), packet.phase(), packet.points().stream().map(MultidimensionalLandingArrayEffects::point).toList(),
                packet.remainingTicks(), packet.targets().stream().map(t -> new MultidimensionalLandingArrayMesh.Target(
                        t.uuid(), t.id(), point(t.position()), t.available())).toList(),
                packet.shotsTotal(), packet.spent(), packet.published(), packet.retained(), packet.pending(), packet.freeOrigin(),
                point(packet.source()), point(packet.impact()), packet.medium(), packet.slot(), packet.shotIndex(), packet.accepted(), packet.terminal(), packet.reason());
    }
    public static void receive(MultidimensionalLandingArray.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); boolean valid = MultidimensionalLandingArrayMesh.valid(receipt);
        MultidimensionalLandingArray.diagnostic("client received input=" + packet.input() + " phase=" + packet.phase() + " valid=" + valid);
        if (!valid) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var capturedWorld = mc.level; var capturedPlayer = mc.player; var capturedConnection = mc.getConnection();
        long capturedGeneration = generation, capturedSession = AcademyClient.inputSessionEpoch();
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer
                    || mc.getConnection() != capturedConnection || generation != capturedGeneration
                    || !AcademyClient.sameInputSessionEpoch(capturedSession) || mc.player == null
                    || !mc.player.getUUID().equals(packet.owner()) || !mc.player.isAlive() || mc.player.isRemoved()
                    || !AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.multidimensionalLandingArrayReceiptMatches(packet.input(), packet.epoch(), packet.phase())) return;
            if (!AcademyClient.multidimensionalLandingArrayControlValid()) {
                AcademyClient.multidimensionalLandingArrayCancelInput(); return;
            }
            boolean acceptedState = STATE.accept(receipt, true);
            MultidimensionalLandingArray.diagnostic("client state input=" + packet.input() + " accepted=" + acceptedState);
            if (!acceptedState) return;
            if (packet.phase() == MultidimensionalLandingArrayMesh.REFUSED && !packet.terminal()) {
                AcademyClient.multidimensionalLandingArrayRejectedInput(packet.input(), packet.epoch());
            } else if (packet.terminal()) {
                AcademyClient.multidimensionalLandingArrayEndedInput(packet.input(), packet.epoch());
                // Successful actual contacts may finish their original seven frames after a clean round end.
                if (!packet.reason().isEmpty()) CUES.removeIf(c -> MultidimensionalLandingArrayMesh.sameController(c.receipt.id(), receipt.id()));
            } else {
                AcademyClient.multidimensionalLandingArrayAcceptedInput(packet.input(), packet.epoch(), STATE.remainingTicks(), packet.phase(), packet.freeOrigin());
            }
            if (packet.phase() == MultidimensionalLandingArrayMesh.PROJECTED) addCue(new Cue(receipt, receipt.source(), false));
            else if (packet.phase() == MultidimensionalLandingArrayMesh.IMPACT && packet.accepted()) addCue(new Cue(receipt, receipt.impact(), true));
            if (packet.phase() == MultidimensionalLandingArrayMesh.REFUSED && !packet.reason().isEmpty())
                mc.player.displayClientMessage(reason(packet.reason()), true);
        });
    }
    private static void addCue(Cue cue) {
        CUES.removeIf(c -> MultidimensionalLandingArrayMesh.sameController(c.receipt.id(), cue.receipt.id())
                && c.receipt.shotIndex() == cue.receipt.shotIndex());
        if (CUES.size() >= MultidimensionalLandingArrayMesh.MAX_CUES) CUES.removeFirst();
        CUES.add(cue);
    }
    public static void abortLocal(long input, long epoch) {
        STATE.cancel(epoch, input);
        CUES.removeIf(c -> c.receipt.id().epoch() == epoch && c.receipt.id().input() <= input);
    }
    public static void clear() { generation++; STATE.clear(); CUES.clear(); }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        if (STATE.active() != null) AcademyClient.multidimensionalLandingArrayCancelInput();
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean locallyOwned(MultidimensionalLandingArrayMesh.Frame receipt, Minecraft mc) {
        return receipt != null && mc.level != null && mc.player != null && mc.player.isAlive() && !mc.player.isRemoved()
                && mc.player.getUUID().equals(receipt.id().owner()) && AcademyClient.singleOwnerEpochMatches(receipt.id().epoch())
                && AcademyClient.multidimensionalLandingArrayInputMatches(receipt.id().input(), receipt.id().epoch())
                && AcademyClient.multidimensionalLandingArrayControlValid();
    }
    public static int preparedPoints() { return STATE.markersVisible() && STATE.active() != null ? STATE.active().points().size() : 0; }
    public static boolean targetsFrozen() { return STATE.targetsFrozen(); }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (!STATE.markersVisible() || !locallyOwned(STATE.active(), mc)) return ClassicAbilityHud.DelegateVisual.IDLE;
        return STATE.targetsFrozen() ? ClassicAbilityHud.DelegateVisual.CHARGE : ClassicAbilityHud.DelegateVisual.ACTIVE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (STATE.tick(mc.isPaused(), locallyOwned(STATE.active(), mc)) != null) AcademyClient.multidimensionalLandingArrayCancelInput();
        CUES.removeIf(c -> mc.level == null || mc.player == null || !mc.player.isAlive() || mc.player.isRemoved()
                || !mc.player.getUUID().equals(c.receipt.id().owner()) || !AcademyClient.singleOwnerEpochMatches(c.receipt.id().epoch())
                || !mc.isPaused() && ++c.age >= MultidimensionalLandingArrayMesh.SUCCESS_TICKS);
    }
    @SubscribeEvent public static void guidance(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var active = STATE.active();
        if (mc.screen != null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()
                || !STATE.markersVisible() || !locallyOwned(active, mc)) return;
        var lines = new ArrayList<Component>(5);
        int seconds = (STATE.remainingTicks()+19)/20;
        Component compact;
        if (STATE.firing()) {
            Component mode = text(active.freeOrigin() ? "panel.free" : "panel.fixed");
            lines.add(text("panel.title",mode,seconds));
            lines.add(text("panel.shots",active.spent(),active.shotsTotal(),active.published()));
            lines.add(text("panel.escrow",active.retained(),active.pending()));
            if (active.freeOrigin()) lines.add(text("panel.stop"));
            compact = text("panel.status",mode,seconds,active.spent(),active.shotsTotal(),active.published(),active.retained(),active.pending());
        } else if (STATE.targetsFrozen()) {
            lines.add(text("panel.title",text("panel.preview"),seconds));
            lines.add(text("panel.targets",active.targets().size(),active.shotsTotal()));
            lines.add(text("panel.confirm"));
            lines.add(text("panel.hold"));
            compact = text("panel.preview_status",active.targets().size(),active.shotsTotal(),seconds);
        } else {
            int points = active.points().size();
            lines.add(text("panel.title",text("panel.plan"),seconds));
            lines.add(text("panel.anchors",points));
            lines.add(text(points < 6 ? "panel.place" : "panel.freeze",Math.min(6,points+1)));
            compact = text("panel.plan_status",points,seconds);
        }
        lines.add(text("panel.clear"));
        var graphics = event.getGuiGraphics();
        var layout = MultidimensionalLandingArrayHudLayout.fit(graphics.guiWidth(),graphics.guiHeight(),
                lines.stream().map(mc.font::width).toList(),mc.font.width(compact),mc.font.lineHeight,
                hudInstruments(mc,graphics.guiWidth(),graphics.guiHeight()));
        if (layout == null) return;
        var box = layout.bounds();
        graphics.fill(box.x(),box.y(),box.x()+box.width(),box.y()+box.height(),0xA00B0D0E);
        graphics.fill(box.x(),box.y(),box.x()+1,box.y()+box.height(),0xFFC4CDD0);
        graphics.pose().pushPose();
        try {
            int padding = layout.compact() ? 2 : MultidimensionalLandingArrayHudLayout.PADDING;
            graphics.pose().translate(box.x()+padding,box.y()+(layout.compact()?1:padding),0);
            graphics.pose().scale(layout.scale(), layout.scale(), 1);
            if (layout.compact()) graphics.drawString(mc.font,compact,0,0,0xFFE2E7E8,false);
            else for (int i=0;i<lines.size();i++) graphics.drawString(mc.font,lines.get(i),0,i*layout.lineStep(),
                    i==0?0xFFFFFFFF:i<3?0xFFE2E7E8:0xFFBAC3C6,false);
        } finally { graphics.pose().popPose(); }
    }
    private static List<HighPowerRecoveryHudModel.Box> hudInstruments(Minecraft mc, int width, int height) {
        boolean config = ClassicHudConfig.SPEC.isLoaded();
        var cpOrigin = ClassicHudTimeline.cpOrigin(width,config?ClassicHudConfig.CP_X.get():ClassicHudTimeline.CP_X,
                config?ClassicHudConfig.CP_Y.get():ClassicHudTimeline.CP_Y);
        var cp = new HighPowerRecoveryHudModel.Box((int)Math.floor(cpOrigin.x()),(int)Math.floor(cpOrigin.y()),194,40);
        var keyOrigin = ClassicHudTimeline.keyOrigin(width,height,config?ClassicHudConfig.KEY_X.get():0,config?ClassicHudConfig.KEY_Y.get():30);
        var keys = new HighPowerRecoveryHudModel.Box((int)keyOrigin.x()-155,(int)keyOrigin.y(),185,88);
        var notification = new HighPowerRecoveryHudModel.Box((int)Math.floor(config?ClassicHudConfig.NOTIFICATION_X.get():0),
                (int)Math.floor(config?ClassicHudConfig.NOTIFICATION_Y.get():15),130,43);
        var chat = mc.gui.getChat(); int chatHeight = (int)Math.ceil(chat.getHeight()*chat.getScale());
        var chatBox = new HighPowerRecoveryHudModel.Box(0,height-40-chatHeight,Math.min(width,chat.getWidth()+16),chatHeight+12);
        var result = new ArrayList<>(List.of(cp,keys,notification,chatBox));
        var recovery = HighPowerRecoveryHudModel.layout(width,height,cp,keys,notification,chatBox);
        if (recovery!=null) result.add(recovery);
        var emergency = EmergencyReturnHudLayout.fit(width,height,cp,keys,notification,chatBox,recovery);
        if (emergency!=null) result.add(emergency);
        return result;
    }
    private static boolean loaded(Box box) {
        Minecraft mc = Minecraft.getInstance(); if (mc.level == null || box == null || !box.body()) return false;
        for (int x = Math.floorDiv((int)Math.floor(box.minX()), 16); x <= Math.floorDiv((int)Math.floor(box.maxX()), 16); x++)
            for (int z = Math.floorDiv((int)Math.floor(box.minZ()), 16); z <= Math.floorDiv((int)Math.floor(box.maxZ()), 16); z++)
                if (!mc.level.hasChunkAt(new BlockPos(x*16, (int)Math.floor(box.minY()), z*16))) return false;
        return true;
    }
    private static boolean visible(RenderLevelStageEvent event, Point at, Vec3 camera) {
        Box box = MultidimensionalLandingArrayMesh.markBounds(at);
        return loaded(box) && camera.distanceToSqr(vector(at)) <= MultidimensionalLandingArrayMesh.CULL_DISTANCE*MultidimensionalLandingArrayMesh.CULL_DISTANCE
                && event.getFrustum().isVisible(new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
    }
    /** Loaded-range-LOS checks use the frozen coordinate, never a live target lookup. */
    private static boolean clearLine(Vec3 camera, Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        double length = camera.distanceTo(at);
        if (mc.level == null || mc.player == null || length > MultidimensionalLandingArrayMesh.CULL_DISTANCE) return false;
        int steps = Math.max(1, (int)Math.ceil(length/4));
        for (int i = 0; i <= steps; i++) if (!mc.level.hasChunkAt(BlockPos.containing(camera.lerp(at, (double)i/steps)))) return false;
        return mc.level.clip(new ClipContext(camera, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var active = STATE.active(); if (mc.level == null || mc.player == null || active == null && CUES.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        var poses = event.getPoseStack(); Vec3 camera = event.getCamera().getPosition();
        var look = event.getCamera().getLookVector(); Vec3 forward = new Vec3(look.x(), look.y(), look.z());
        var labels = new ArrayList<Label>(MultidimensionalLandingArrayMesh.MAX_LABELS);
        var modelView = RenderSystem.getModelViewStack(); float[] color = RenderSystem.getShaderColor().clone();
        modelView.pushMatrix(); poses.pushPose();
        try {
            RenderSystem.setShaderColor(1,1,1,1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = poses.last().pose();
            if (!mc.options.hideGui && mc.screen == null && STATE.markersVisible() && locallyOwned(active, mc)) {
                boolean preview = STATE.targetsFrozen();
                for (int i = 0; i < active.points().size(); i++) {
                    Point at = active.points().get(i);
                    if (!visible(event, at, camera) || !clearLine(camera, vector(at).add(0,.12,0))) continue;
                    for (var line : MultidimensionalLandingArrayMesh.marker(at)) line(matrix, line, camera, forward, preview ? 240 : 190);
                    // Gray original teleport-coordinate atlas. It is explicitly labelled as a plan, never an arrival.
                    Box box = MultidimensionalLandingArrayMesh.markBounds(at);
                    if (!MultidimensionalLandingArrayMesh.inside(point(camera), box)) {
                        var out = buffers.getBuffer(Material.quads(MARKS[0]));
                        for (var quad : SafeLandingProbeMesh.biped(box))
                            for (var v : List.of(quad.a(),quad.b(),quad.c(),quad.d()))
                                vertex(out,matrix,v.position(),camera,v.u(),v.v(),MultidimensionalLandingArrayMesh.anchorAlpha(preview));
                    }
                    labels.add(new Label(new Point(at.x(),at.y()+1.94,at.z()),text("mark.anchor",i+1),false,0,3,i));
                    var hand = mc.player.getOffhandItem();
                    if (preview && hand.is(AcademyCraft.NEEDLE.get()) && hand.getCount() >= 6 && hand.isComponentsPatchEmpty())
                        previewNeedle(event, MultidimensionalLandingArrayMesh.origin(at), camera, forward, i);
                }
                // Frozen server snapshots stay stationary when their named entities move, hide or unload.
                for (int i = 0; i < active.targets().size(); i++) {
                    var target = active.targets().get(i); Point at = target.position();
                    if (!visible(event, at, camera) || !clearLine(camera, vector(at))) continue;
                    for (var line : MultidimensionalLandingArrayMesh.contact(at)) line(matrix,line,camera,forward,target.available()?185:95);
                    int lane=0;
                    for (int j=0;j<i;j++) if (MultidimensionalLandingArrayMesh.nearbyLabel(at,active.targets().get(j).position())) lane++;
                    labels.add(new Label(MultidimensionalLandingArrayMesh.labelAnchor(at),text(target.available()?"mark.target":"mark.unavailable",
                            i+1,MultidimensionalLandingArrayMesh.targetShares(active.targets().size(),i)),!target.available(),
                            MultidimensionalLandingArrayMesh.labelOffset(false,lane,mc.font.lineHeight),2,i));
                }
            }
            for (int cueIndex=0;cueIndex<CUES.size();cueIndex++) {
                var cue=CUES.get(cueIndex);
                int alpha = MultidimensionalLandingArrayMesh.alpha(cue.age);
                if (alpha == 0 || !visible(event,cue.at,camera) || !clearLine(camera,vector(cue.at))) continue;
                for (var line : MultidimensionalLandingArrayMesh.contact(cue.at)) {
                    // Only this authenticated source/contact square gets an envelope; no endpoint-to-endpoint trail.
                    stroke(buffers.getBuffer(Material.quads(EDGE_ENVELOPE)),matrix,line,camera,forward,
                            MultidimensionalLandingArrayMesh.envelopeAlpha(cue.age),MultidimensionalLandingArrayMesh.ENVELOPE_HALF_WIDTH,true);
                    line(matrix,line,camera,forward,alpha);
                    stroke(buffers.getBuffer(Material.quads(MARKS[MultidimensionalLandingArrayMesh.frame(cue.age)])),matrix,line,camera,forward,
                            alpha,MultidimensionalLandingArrayMesh.ATLAS_HALF_WIDTH,false);
                }
                int lane=0;
                for (int j=0;j<cueIndex;j++) if (MultidimensionalLandingArrayMesh.nearbyLabel(cue.at,CUES.get(j).at)) lane++;
                labels.add(new Label(MultidimensionalLandingArrayMesh.labelAnchor(cue.at),text(cue.contact?"mark.contact":"mark.published",cue.receipt.shotIndex()+1),
                        false,MultidimensionalLandingArrayMesh.labelOffset(true,lane,mc.font.lineHeight),cue.contact?0:1,cue.receipt.shotIndex()));
            }
            // Actual contacts claim their text space first; collinear anchors never form a wall over that feedback.
            labels.sort(Comparator.comparingInt(Label::priority).thenComparingDouble(l -> vector(l.at()).distanceToSqr(camera)).thenComparingInt(Label::order));
            var occupied = new ArrayList<MultidimensionalLandingArrayMesh.LabelBounds>(MultidimensionalLandingArrayMesh.MAX_LABELS);
            for (var label : labels) label(event,label,camera,forward,occupied);
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            }
        }
    }
    private static void previewNeedle(RenderLevelStageEvent event, Point at, Vec3 camera, Vec3 forward, int slot) {
        Vec3 relative = vector(at).subtract(camera);
        if (relative.dot(forward) <= .45 || !clearLine(camera, vector(at))) return;
        var poses = event.getPoseStack(); poses.pushPose();
        try {
            poses.translate(relative.x,relative.y,relative.z); poses.mulPose(Axis.YP.rotationDegrees(90)); poses.scale(.4F,.4F,.4F);
            // Advisory rendering of exactly six owned originals; publication uses only actual native entity rendering.
            Minecraft.getInstance().getItemRenderer().renderStatic(AcademyCraft.NEEDLE.get().getDefaultInstance(),ItemDisplayContext.NONE,
                    15728880,OverlayTexture.NO_OVERLAY,poses,buffers,Minecraft.getInstance().level,slot);
        } finally { poses.popPose(); }
    }
    private static void line(Matrix4f matrix, MultidimensionalLandingArrayMesh.Line line, Vec3 camera, Vec3 forward, int alpha) {
        Vec3 a = vector(MultidimensionalLandingArrayMesh.relative(line.from(),point(camera)));
        Vec3 b = vector(MultidimensionalLandingArrayMesh.relative(line.to(),point(camera)));
        double da=a.dot(forward), db=b.dot(forward);
        if (da <= MultidimensionalLandingArrayMesh.NEAR_PLANE && db <= MultidimensionalLandingArrayMesh.NEAR_PLANE) return;
        if (da < MultidimensionalLandingArrayMesh.NEAR_PLANE) a=a.lerp(b,(MultidimensionalLandingArrayMesh.NEAR_PLANE-da)/(db-da));
        else if (db < MultidimensionalLandingArrayMesh.NEAR_PLANE) b=a.lerp(b,(MultidimensionalLandingArrayMesh.NEAR_PLANE-db)/(da-db));
        Vec3 normal=b.subtract(a).normalize(); var out=buffers.getBuffer(Material.LINES);
        out.addVertex(matrix,(float)a.x,(float)a.y,(float)a.z).setColor(226,231,232,alpha).setNormal((float)normal.x,(float)normal.y,(float)normal.z);
        out.addVertex(matrix,(float)b.x,(float)b.y,(float)b.z).setColor(226,231,232,alpha).setNormal((float)normal.x,(float)normal.y,(float)normal.z);
    }
    private static void stroke(VertexConsumer out, Matrix4f matrix, MultidimensionalLandingArrayMesh.Line line, Vec3 camera, Vec3 forward,
                               int alpha, double halfWidth, boolean envelope) {
        Vec3 a=vector(MultidimensionalLandingArrayMesh.relative(line.from(),point(camera)));
        Vec3 b=vector(MultidimensionalLandingArrayMesh.relative(line.to(),point(camera)));
        Vec3 side=b.subtract(a).cross(a.scale(-1)).normalize().scale(halfWidth);
        double depthMargin=Math.abs(side.dot(forward));
        if (side.lengthSqr()<1e-10 || a.dot(forward)-depthMargin<=.09 || b.dot(forward)-depthMargin<=.09) return;
        float u0=envelope?0:.03125F, u1=envelope?1:.484375F, v0=envelope?0:.25F, v1=envelope?1:.46875F;
        relativeVertex(out,matrix,a.subtract(side),u0,v0,alpha); relativeVertex(out,matrix,a.add(side),u0,v1,alpha);
        relativeVertex(out,matrix,b.add(side),u1,v1,alpha); relativeVertex(out,matrix,b.subtract(side),u1,v0,alpha);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Point at, Vec3 camera, float u, float v, int alpha) {
        Point relative=MultidimensionalLandingArrayMesh.relative(at,point(camera));
        relativeVertex(out,matrix,vector(relative),u,v,alpha);
    }
    private static void relativeVertex(VertexConsumer out, Matrix4f matrix, Vec3 at, float u, float v, int alpha) {
        out.addVertex(matrix,(float)at.x,(float)at.y,(float)at.z).setUv(u,v).setColor(230,235,236,alpha);
    }
    private static void label(RenderLevelStageEvent event, Label label, Vec3 camera, Vec3 forward,
                              List<MultidimensionalLandingArrayMesh.LabelBounds> occupied) {
        Minecraft mc=Minecraft.getInstance(); if (mc.screen!=null || mc.options.hideGui || !clearLine(camera,vector(label.at()))) return;
        Vec3 relative=vector(label.at()).subtract(camera); double depth=relative.dot(forward);
        int width=mc.font.width(label.text()), guiWidth=mc.getWindow().getGuiScaledWidth(), guiHeight=mc.getWindow().getGuiScaledHeight();
        float scale=MultidimensionalLandingArrayMesh.labelScale(depth,relative.length(),width,mc.font.lineHeight,
                Math.abs(event.getProjectionMatrix().m11()),guiHeight);
        int alpha=MultidimensionalLandingArrayMesh.labelAlpha(depth); if (scale<=0 || alpha==0) return;
        var poses=event.getPoseStack(); poses.pushPose();
        try {
            poses.translate(relative.x,relative.y,relative.z); poses.mulPose(event.getCamera().rotation()); poses.scale(scale,-scale,scale);
            var clip=new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).mul(poses.last().pose());
            double left=Double.POSITIVE_INFINITY,top=Double.POSITIVE_INFINITY,right=Double.NEGATIVE_INFINITY,bottom=Double.NEGATIVE_INFINITY;
            // Include the background and shadow, using the exact billboard transform used for text vertices.
            for(float x:new float[]{-width/2F-1,width/2F+1}) for(float y:new float[]{label.rowOffset()-1,label.rowOffset()+mc.font.lineHeight+1}) {
                var projected=clip.transform(new Vector4f(x,y,0,1));
                if (!Float.isFinite(projected.w()) || projected.w()<=0) return;
                double sx=(projected.x()/projected.w()+1)*guiWidth/2, sy=(1-projected.y()/projected.w())*guiHeight/2;
                left=Math.min(left,sx);right=Math.max(right,sx);top=Math.min(top,sy);bottom=Math.max(bottom,sy);
            }
            if (!MultidimensionalLandingArrayMesh.admitLabel(occupied,new MultidimensionalLandingArrayMesh.LabelBounds(left,top,right,bottom),guiWidth,guiHeight)) return;
            int color=(alpha<<24)|(label.unavailable()?0xAAAAAA:0xE2E7E8), background=((88*alpha/255)<<24)|0x0B0D0E;
            mc.font.drawInBatch(label.text(),-width/2F,label.rowOffset(),color,true,poses.last().pose(),buffers,Font.DisplayMode.NORMAL,background,15728880);
        } finally { poses.popPose(); }
    }
    private static Component text(String key,Object... args) { return Component.translatable(PREFIX+key,args); }
    private static Component reason(String key) {
        String translation=PREFIX+"reason."+key;
        return net.minecraft.locale.Language.getInstance().has(translation)?Component.translatable(translation):text("refused");
    }
    private static Point point(Vec3 at) { return at==null?null:new Point(at.x,at.y,at.z); }
    private static Vec3 vector(Point at) { return new Vec3(at.x(),at.y(),at.z()); }
    private static final class Material extends RenderType {
        private static final Map<ResourceLocation,RenderType> TYPES=new HashMap<>();
        private Material(String name,VertexFormat format,VertexFormat.Mode mode,int size,boolean crumbling,boolean sort,Runnable set,Runnable reset) {
            super(name,format,mode,size,crumbling,sort,set,reset);
        }
        static RenderType quads(ResourceLocation texture) {
            return TYPES.computeIfAbsent(texture,t->create("academy_landing_array_"+t.getPath(),DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,8192,false,true,CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(t,false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
        static final RenderType LINES=create("academy_landing_array_lines",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,
                4096,false,false,CompositeState.builder().setShaderState(new ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                        .setLineState(new LineStateShard(OptionalDouble.of(1.25))).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setOutputState(PARTICLES_TARGET).createCompositeState(false));
    }
}

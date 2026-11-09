package cn.academy.port.client;

import cn.academy.port.highpower.VectorCage;
import cn.academy.port.client.VectorCageState.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Comparator;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW441 receipt-only four-plane decoration. No client target sensing, collision, particles or predicted damage. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class VectorCageEffects {
    private static final ResourceLocation GLOW_LINE = texture("effects/glow_line"), WHITE = texture("port/white");
    private static final VectorCageState STATE = new VectorCageState();
    private record Binding(Entity owner, VectorCageState.Identity id) {}
    private static final Map<UUID, Binding> OWNERS = new HashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static ByteBufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private static Boolean textured;
    private VectorCageEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            VectorCage.clientReceiver = VectorCageEffects::receive;
            VectorCage.clientRejectionReceiver = VectorCageEffects::reject;
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> Minecraft.getInstance().execute(() -> {
                AcademyClient.vectorCageCancelInput(); clear();
                if (buffers != null) buffers.endBatch();
                if (storage != null) storage.close();
                buffers = null; storage = null; textured = null; Material.TYPES.clear();
            }));
        }
    }
    private static VectorCageState.Frame frame(VectorCage.Visual p) {
        return new VectorCageState.Frame(new VectorCageState.Identity(p.entity(),p.owner(),p.epoch(),p.input(),p.token()),
                p.sequence(),p.phase(),p.age(),p.low(),point(p.center()),p.halfWidth(),p.pulse(),p.wall(),point(p.contact()),p.status());
    }
    public static void receive(VectorCage.Visual packet) {
        if (packet == null) return;
        var receipt = frame(packet); if (!VectorCageState.valid(receipt)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || mc.level != capturedWorld || mc.player != capturedPlayer
                    || mc.getConnection() != capturedConnection || generation != capturedGeneration
                    || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = capturedWorld.getEntity(packet.entity());
            if (!(owner instanceof Player) || !owner.getUUID().equals(packet.owner())) return;
            Binding binding = OWNERS.get(packet.owner());
            if (binding != null && binding.id.equals(receipt.id()) && binding.owner != owner) return;
            boolean local = owner == mc.player;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.vectorCageInputMatches(packet.input(),packet.epoch()))) return;
            if (packet.phase() != VectorCageState.ENDED && (!owner.isAlive() || owner.isRemoved() || owner.isSpectator()
                    || local && !AcademyClient.vectorCageControlValid())) return;
            if (!STATE.accept(receipt,local)) return;
            if (STATE.owns(receipt.id())) OWNERS.put(packet.owner(),new Binding(owner,receipt.id()));
            pruneOwners();
            if (local && packet.phase() == VectorCageState.ENDED) AcademyClient.vectorCageEndedInput(packet.input(),packet.epoch());
            else if (local && (packet.phase() == VectorCageState.PREVIEW || packet.phase() == VectorCageState.STATE))
                AcademyClient.vectorCageAcceptedInput(packet.input(),packet.epoch(),packet.phase());
        });
    }
    private static void reject(VectorCage.Rejection packet) {
        if (packet == null || packet.owner() == null || packet.entity() < 0 || packet.input() <= 0 || packet.epoch() <= 0) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld=mc.level; Entity capturedPlayer=mc.player; Object capturedConnection=mc.getConnection();
        long capturedSession=AcademyClient.inputSessionEpoch(), capturedGeneration=generation;
        mc.execute(() -> {
            if (capturedWorld == null || capturedPlayer == null || mc.level != capturedWorld || mc.player != capturedPlayer
                    || mc.getConnection() != capturedConnection || generation != capturedGeneration
                    || !AcademyClient.sameInputSessionEpoch(capturedSession) || packet.entity() != capturedPlayer.getId()
                    || !packet.owner().equals(capturedPlayer.getUUID()) || !AcademyClient.singleOwnerEpochMatches(packet.epoch())) return;
            AcademyClient.vectorCageRejectedInput(packet.input(),packet.epoch());
        });
    }
    public static void abortLocal(long input,long epoch) { STATE.cancel(epoch,input); pruneOwners(); }
    public static void clear() { generation++; STATE.clear(); OWNERS.clear(); }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear(); if (connection != mc.getConnection()) STATE.resetSession();
        world=mc.level; localPlayer=mc.player; connection=mc.getConnection();
    }
    private static void pruneOwners() { OWNERS.entrySet().removeIf(e -> !STATE.owns(e.getValue().id)); }
    private static boolean validOwner(VectorCageState.Controller c,Minecraft mc) {
        if (c == null || world == null) return false;
        Binding binding=OWNERS.get(c.id().owner()); Entity owner=world.getEntity(c.id().entity());
        return binding != null && binding.id.equals(c.id()) && binding.owner == owner && owner instanceof Player
                && owner.isAlive() && !owner.isRemoved() && !owner.isSpectator() && owner.getUUID().equals(c.id().owner())
                && (!c.local() || owner == mc.player && AcademyClient.singleOwnerEpochMatches(c.id().epoch())
                && AcademyClient.vectorCageInputMatches(c.id().input(),c.id().epoch()) && AcademyClient.vectorCageControlValid());
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc=Minecraft.getInstance(); synchronize(mc);
        return STATE.controllers().stream().anyMatch(c -> c.local() && validOwner(c,mc))
                ? ClassicAbilityHud.DelegateVisual.ACTIVE : ClassicAbilityHud.DelegateVisual.IDLE;
    }
    public static String localHint() {
        Minecraft mc=Minecraft.getInstance(); synchronize(mc);
        var c=STATE.controllers().stream().filter(p -> p.local() && validOwner(p,mc)).findFirst().orElse(null);
        if(c==null)return "";
        String phase=switch(VectorCageMesh.phase(c.frame())) {
            case PREVIEW -> "preview"; case WARMUP -> "warmup"; case COMPRESS -> "compress"; case REST -> "rest"; default -> "";
        };
        if(phase.isEmpty())return "";
        String fallback=switch(phase) {
            case "preview" -> "Cage marked: tap to confirm; hold to clear";
            case "warmup" -> "Pressure cage: preparing";
            case "compress" -> "Pressure cage: pulse %s/4";
            default -> "Pressure cage: between pulses";
        };
        if(c.frame().low())fallback="Low pressure · "+fallback;
        return net.minecraft.network.chat.Component.translatableWithFallback("hud.academy.highpower.vector_cage."+phase
                +(c.frame().low()?"_low":""),fallback,c.frame().pulse()+1).getString();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance(); synchronize(mc);
        for (var c:STATE.controllers()) if (c.local() && !validOwner(c,mc)) AcademyClient.vectorCageCancelInput();
        // Cosmetic expiration never ends or aborts the server-owned input.
        STATE.tick(mc.isPaused(),c -> validOwner(c,mc)); pruneOwners();
    }
    private static boolean visible(VectorCageState.Controller c,RenderLevelStageEvent event,Vec3 camera) {
        var decoration=VectorCageMesh.bounds(c); AABB bounds=new AABB(vec(decoration.min()),vec(decoration.max()));
        Vec3 nearest=new Vec3(Math.max(bounds.minX,Math.min(bounds.maxX,camera.x)),Math.max(bounds.minY,Math.min(bounds.maxY,camera.y)),Math.max(bounds.minZ,Math.min(bounds.maxZ,camera.z)));
        if (camera.distanceToSqr(nearest)>VectorCageMesh.CULL_DISTANCE*VectorCageMesh.CULL_DISTANCE) return false;
        for (int x=Math.floorDiv((int)Math.floor(bounds.minX),16);x<=Math.floorDiv((int)Math.floor(bounds.maxX),16);x++)
            for (int z=Math.floorDiv((int)Math.floor(bounds.minZ),16);z<=Math.floorDiv((int)Math.floor(bounds.maxZ),16);z++)
                if (!world.hasChunkAt(new BlockPos(x*16,(int)Math.floor(bounds.minY),z*16))) return false;
        return event.getFrustum().isVisible(bounds);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc=Minecraft.getInstance(); synchronize(mc); Vec3 camera=event.getCamera().getPosition();
        var shapes=new ArrayList<VectorCageMesh.Shape>(VectorCageMesh.MAX_CONTROLLERS); int vertices=0;
        for (var c:STATE.controllers().stream().sorted(Comparator.comparing(VectorCageState.Controller::local).reversed()).toList()) {
            if (!validOwner(c,mc) || !visible(c,event,camera)) continue;
            if (shapes.size()>=VectorCageMesh.MAX_CONTROLLERS) break;
            var shape=VectorCageMesh.shape(c,Math.min(VectorCageMesh.MAX_CONTROLLER_VERTICES,VectorCageMesh.MAX_VERTICES-vertices));
            if (shape.vertexCount()==0) continue;
            vertices+=shape.vertexCount(); shapes.add(shape);
        }
        if (shapes.isEmpty()) return;
        if (buffers==null) { storage=new ByteBufferBuilder(65536); buffers=MultiBufferSource.immediate(storage); }
        if (textured==null) textured=mc.getResourceManager().getResource(GLOW_LINE).isPresent();
        var look=event.getCamera().getLookVector(); Point forward=new Point(look.x(),look.y(),look.z());
        var left=event.getCamera().getLeftVector(); Vec3 fallback=new Vec3(left.x(),left.y(),left.z());
        var poses=event.getPoseStack(); var modelView=RenderSystem.getModelViewStack();
        float[] color=RenderSystem.getShaderColor().clone(); modelView.pushMatrix(); poses.pushPose();
        try {
            RenderSystem.setShaderColor(1,1,1,1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix=poses.last().pose();
            var planes=buffers.getBuffer(Material.quads(Pass.PLANE));
            for (var shape:shapes) for(var panel:shape.panels()) panel(planes,matrix,panel,camera,forward);
            buffers.endBatch(Material.quads(Pass.PLANE));
            for (Pass pass:new Pass[]{Pass.SHOULDER,Pass.CORE}) {
                var out=buffers.getBuffer(Material.quads(pass));
                for(var shape:shapes)for(var stroke:shape.strokes())
                    stroke(out,matrix,stroke,VectorCageMesh.profile(stroke,pass==Pass.CORE),camera,forward,fallback);
                buffers.endBatch(Material.quads(pass));
            }
        } finally {
            try { buffers.endBatch(); } finally {
                poses.popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            }
        }
    }
    private static void panel(VertexConsumer out,Matrix4f matrix,VectorCageMesh.Panel panel,Vec3 camera,Point forward) {
        var relative=panel.corners().stream().map(p -> VectorCageMesh.relative(p,point(camera))).toList();
        // Crossing the eye plane suppresses only the optional face fill; its complete clipped outline remains.
        if(relative.stream().anyMatch(p -> VectorCageMesh.dot(p,forward)<=VectorCageMesh.NEAR_PLANE))return;
        double nearest=relative.stream().mapToDouble(p -> Math.sqrt(VectorCageMesh.dot(p,p))).min().orElse(0);
        int alpha=(int)Math.round(panel.alpha()*VectorCageMesh.nearEyeFade(nearest));
        for(var p:relative) {
            // POSITION_COLOR has no UV attribute and preserves the intended sub-0.1 plane alpha.
            out.addVertex(matrix,(float)p.x(),(float)p.y(),(float)p.z()).setColor(180,180,180,alpha);
        }
    }
    private static void stroke(VertexConsumer out,Matrix4f matrix,VectorCageMesh.Stroke s,VectorCageMesh.Profile profile,Vec3 camera,Point forward,Vec3 fallback) {
        var clipped=VectorCageMesh.clipNear(new VectorCageMesh.Stroke(VectorCageMesh.relative(s.from(),point(camera)),
                VectorCageMesh.relative(s.to(),point(camera)),s.width(),s.alpha(),s.role()),forward,profile.width()/2);
        if(clipped==null)return;
        Vec3 a=vec(clipped.from()),b=vec(clipped.to()); Vec3 side=b.subtract(a).cross(a.add(b).scale(-.5)).normalize();
        if(side.lengthSqr()<1e-10)side=fallback; side=side.scale(profile.width()/2);
        int alphaA=(int)Math.round(profile.alpha()*VectorCageMesh.nearEyeFade(a.length()));
        int alphaB=(int)Math.round(profile.alpha()*VectorCageMesh.nearEyeFade(b.length()));
        vertex(out,matrix,a.subtract(side),0,profile.maxV(),profile.gray(),alphaA);
        vertex(out,matrix,a.add(side),0,profile.minV(),profile.gray(),alphaA);
        vertex(out,matrix,b.add(side),1,profile.minV(),profile.gray(),alphaB);
        vertex(out,matrix,b.subtract(side),1,profile.maxV(),profile.gray(),alphaB);
    }
    private static void vertex(VertexConsumer out,Matrix4f matrix,Vec3 relative,float u,float v,int gray,int alpha) {
        // Like the plane writer, this float boundary follows double world-minus-camera subtraction.
        out.addVertex(matrix,(float)relative.x,(float)relative.y,(float)relative.z).setUv(u,v).setColor(gray,gray,gray,alpha);
    }
    private static Point point(Vec3 p) { return p==null?null:new Point(p.x,p.y,p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(),p.y(),p.z()); }
    private enum Pass { PLANE,SHOULDER,CORE }
    private static final class Material extends RenderType {
        private static final Map<Pass,RenderType> TYPES=new HashMap<>();
        private Material(String name,VertexFormat format,VertexFormat.Mode mode,int size,boolean crumbling,boolean sorted,Runnable set,Runnable reset) { super(name,format,mode,size,crumbling,sorted,set,reset); }
        static RenderType quads(Pass pass) {
            return TYPES.computeIfAbsent(pass,p -> create("academy_vector_cage_"+p,
                    p==Pass.PLANE?DefaultVertexFormat.POSITION_COLOR:DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,8192,false,true,CompositeState.builder()
                            .setShaderState(p==Pass.PLANE?new ShaderStateShard(GameRenderer::getPositionColorShader)
                                    :p==Pass.SHOULDER?new ShaderStateShard(ClassicSkillAlphaShader::get)
                                    :new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(p==Pass.PLANE?NO_TEXTURE
                                    :new TextureStateShard(Boolean.FALSE.equals(textured)?WHITE:GLOW_LINE,false,false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                            .setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY).setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
    }

    /** Read-only snapshot: points = center then every contact; numbers = half width, pulse, last wall (-1), 0, preview flag. */
    private static double[] dp(Point p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        for (var c : STATE.controllers()) {
            if (world == null || !validOwner(c, mc)) continue;
            var f = c.frame();
            var points = new java.util.ArrayList<double[]>(); points.add(dp(f.center()));
            int wall = -1;
            for (var contact : c.contacts()) { points.add(dp(contact.point())); wall = contact.wall(); }
            out.add(DecorSnap.of(c.local(), f.age(), f.phase(), c.id().token(), points, f.halfWidth(), f.pulse(), wall, 0, c.preview() ? 1 : 0));
        }
        return out;
    }
}

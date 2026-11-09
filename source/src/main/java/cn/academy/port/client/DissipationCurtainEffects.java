package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.DissipationCurtain;
import cn.academy.port.highpower.DualPoint;
import cn.academy.port.highpower.ScatterPlates;
import cn.academy.port.highpower.core.DissipationCurtainGeometry;
import cn.academy.port.highpower.core.DissipationCurtainGeometry.Vector;
import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW242 scoped reuse of the original shield and NEW221 material; no prediction, particles or terrain writes. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class DissipationCurtainEffects {
    private static final ResourceLocation WHITE = texture("port/white"), SHIELD = texture("effects/mdshield");
    private static final DissipationCurtainState STATE = new DissipationCurtainState();
    private static final Map<UUID, Marker> MARKERS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static MultiBufferSource.BufferSource buffers;
    private static final class Marker {
        final Entity owner;
        final DissipationCurtainState.Lease lease = new DissipationCurtainState.Lease();
        final DissipationCurtainState.Contacts contacts = new DissipationCurtainState.Contacts();
        DissipationCurtainState.Frame frame;
        Marker(Entity owner, DissipationCurtainState.Frame frame) { this.owner = owner; this.frame = frame; update(frame); }
        void update(DissipationCurtainState.Frame next) {
            // A committed absorption can refer to an older projectile span; its pose belongs only to its ripple.
            if (next.phase() != DissipationCurtainState.HIT) frame = next;
            lease.received(next); contacts.received(next);
        }
        void end() { lease.end(); contacts.clear(); }
    }
    private DissipationCurtainEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { DissipationCurtain.clientReceiver = DissipationCurtainEffects::receive; }
    }
    private static DissipationCurtainState.Frame frame(DissipationCurtain.Visual p) {
        return new DissipationCurtainState.Frame(new DissipationCurtainState.Identity(p.entity(),p.owner(),p.epoch(),p.input(),p.token()),
                p.sequence(),p.phase(),point(p.center()),point(p.axis()),p.liveMask(),p.plate(),point(p.point()));
    }
    public static void receive(DissipationCurtain.Visual packet) {
        if(packet==null || packet.center()==null || packet.axis()==null || packet.point()==null)return;
        var receipt=frame(packet); if(!DissipationCurtainState.valid(receipt))return;
        Minecraft mc=Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld=mc.level; Entity capturedPlayer=mc.player; Object capturedConnection=mc.getConnection();
        long capturedSession=AcademyClient.inputSessionEpoch(), capturedGeneration=generation;
        mc.execute(() -> {
            if(capturedWorld==null || mc.level!=capturedWorld || mc.player!=capturedPlayer || mc.getConnection()!=capturedConnection
                    || generation!=capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession))return;
            Entity owner=capturedWorld.getEntity(packet.entity());
            if(owner==null || !owner.getUUID().equals(packet.owner()))return;
            Marker old=MARKERS.get(packet.owner());
            if(old!=null && old.frame.id().token()==receipt.id().token() && old.owner!=owner)return;
            boolean local=owner==mc.player;
            if(local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch()) || !AcademyClient.dissipationCurtainInputMatches(packet.input(),packet.epoch())))return;
            if(packet.phase()!=DissipationCurtainState.ENDED && (!owner.isAlive() || owner.isRemoved() || local && !localEligible()))return;
            if(!STATE.accept(receipt,local))return;
            if(packet.phase()==DissipationCurtainState.ENDED) {
                if(old!=null && old.frame.id().equals(receipt.id()))old.end();
                if(local)AcademyClient.dissipationCurtainEndedInput(packet.input(),packet.epoch());
                return;
            }
            if(local)AcademyClient.dissipationCurtainAcceptedInput(packet.input(),packet.epoch());
            if(old==null || !old.frame.id().equals(receipt.id())) {
                if(old==null && MARKERS.size()>=DissipationCurtainState.MAX_CONTEXTS) { STATE.retire(receipt.id()); return; }
                MARKERS.put(packet.owner(),new Marker(owner,receipt));
            } else old.update(receipt);
        });
    }
    public static void abortLocal(long input,long epoch) {
        Minecraft mc=Minecraft.getInstance(); synchronize(mc); STATE.cancel(epoch,input);
        Marker marker=mc.player==null?null:MARKERS.get(mc.player.getUUID());
        if(marker!=null && marker.frame.id().epoch()==epoch && marker.frame.id().input()<=input) {
            STATE.retire(marker.frame.id()); marker.end();
        }
    }
    public static void clear() { generation++; STATE.clear(); MARKERS.clear(); }
    private static void synchronize(Minecraft mc) {
        if(world==mc.level && localPlayer==mc.player && connection==mc.getConnection())return;
        clear();
        if(connection!=mc.getConnection())STATE.resetSession();
        world=mc.level; localPlayer=mc.player; connection=mc.getConnection();
    }
    private static boolean localEligible() {
        Minecraft mc=Minecraft.getInstance(); var state=AcademyClient.state;
        return mc.screen==null && mc.isWindowActive() && mc.getCameraEntity()==mc.player
                && mc.player!=null && !mc.player.isSpectator() && state!=null && state.activated && state.overloadFine && !state.interfering
                && "meltdowner".equals(state.category) && DissipationCurtainState.eligible(state.level,state.learned(ScatterPlates.ID),state.learned(DissipationCurtain.ID)) && state.learned(DualPoint.ID)
                && state.presets.currentContains(DissipationCurtain.ID) && SkillCatalog.enabled("meltdowner",DissipationCurtain.ID);
    }
    private static boolean validOwner(Marker marker,Minecraft mc) {
        return world!=null && marker.owner.isAlive() && !marker.owner.isRemoved() && world.getEntity(marker.owner.getId())==marker.owner
                && marker.owner.getUUID().equals(marker.frame.id().owner())
                && (marker.owner!=mc.player || localEligible() && AcademyClient.singleOwnerEpochMatches(marker.frame.id().epoch())
                && (marker.lease.ending() || AcademyClient.dissipationCurtainInputMatches(marker.frame.id().input(),marker.frame.id().epoch())));
    }
    private static Marker localMarker() {
        Minecraft mc=Minecraft.getInstance(); synchronize(mc); Marker marker=mc.player==null?null:MARKERS.get(mc.player.getUUID());
        return marker!=null && validOwner(marker,mc)?marker:null;
    }
    /** A fade is intentionally never reported as an active held skill or a protecting plate. */
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Marker marker=localMarker();
        if(marker==null || !marker.lease.live())return ClassicAbilityHud.DelegateVisual.IDLE;
        return marker.frame.phase()==DissipationCurtainState.STARTUP?ClassicAbilityHud.DelegateVisual.CHARGE:ClassicAbilityHud.DelegateVisual.ACTIVE;
    }
    public static boolean activeLocal() { return localVisual()!=ClassicAbilityHud.DelegateVisual.IDLE; }
    public static String localPhase() {
        Marker marker=localMarker();
        return marker==null?"idle":marker.lease.ending()?"fading":marker.frame.phase()==DissipationCurtainState.STARTUP?"startup":"active";
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance(); synchronize(mc);
        Iterator<Marker> iterator=MARKERS.values().iterator();
        while(iterator.hasNext()) {
            Marker marker=iterator.next(); boolean wasEnding=marker.lease.ending();
            marker.contacts.tick(mc.isPaused());
            boolean keep=marker.lease.tick(mc.isPaused(),validOwner(marker,mc));
            if(!wasEnding && marker.lease.ending()) {
                STATE.retire(marker.frame.id()); marker.contacts.clear();
                if(marker.owner==mc.player)AcademyClient.dissipationCurtainEndedInput(marker.frame.id().input(),marker.frame.id().epoch());
            }
            if(!keep) {
                STATE.retire(marker.frame.id()); iterator.remove();
                if(marker.owner==mc.player)AcademyClient.dissipationCurtainEndedInput(marker.frame.id().input(),marker.frame.id().epoch());
            }
        }
    }
    /** Read-only chunk checks keep corners and historical contact meshes out of unloaded terrain. */
    private static boolean loadedFacet(DissipationCurtainGeometry.Pose pose,int facet) {
        var vertices=DissipationCurtainGeometry.vertices(pose,facet);
        double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;
        Vector back=DissipationCurtainGeometry.facetNormal(pose,facet).scale(-DissipationCurtainGeometry.THICKNESS);
        for(Vector vertex:vertices)for(Vector p:List.of(vertex,vertex.add(back))) {
            if(!DissipationCurtainMesh.validPoint(p))return false;
            minX=Math.min(minX,p.x()); maxX=Math.max(maxX,p.x()); minZ=Math.min(minZ,p.z()); maxZ=Math.max(maxZ,p.z());
        }
        for(int x=Math.floorDiv((int)Math.floor(minX),16);x<=Math.floorDiv((int)Math.floor(maxX),16);x++)
            for(int z=Math.floorDiv((int)Math.floor(minZ),16);z<=Math.floorDiv((int)Math.floor(maxZ),16);z++)
                if(!world.hasChunkAt(new BlockPos(x*16,(int)Math.floor(pose.center().y()),z*16)))return false;
        return true;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance(); synchronize(mc); if(world==null || MARKERS.isEmpty())return;
        Vector eye=point(event.getCamera().getPosition()); var facing=event.getCamera().getLookVector();
        var look=FoldedMirrorGeometry.unit(new FoldedMirrorGeometry.Vector(facing.x(),facing.y(),facing.z())).orElse(null);
        if(look==null)return;
        List<Marker> visible=MARKERS.values().stream().filter(m -> validOwner(m,mc)
                && m.frame.center().subtract(eye).length()<=DissipationCurtainMesh.CULL_DISTANCE)
                .sorted(Comparator.<Marker>comparingInt(m -> m.owner==mc.player?0:1)
                        .thenComparingDouble(m -> m.frame.center().subtract(eye).length()))
                .limit(DissipationCurtainMesh.MAX_VISIBLE_CONTEXTS).toList();
        if(visible.isEmpty())return;
        if(buffers==null)buffers=MultiBufferSource.immediate(new ByteBufferBuilder(131072));
        var modelView=RenderSystem.getModelViewStack(); modelView.pushMatrix(); float[] shaderColor=RenderSystem.getShaderColor().clone();
        try {
            RenderSystem.setShaderColor(1,1,1,1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix=event.getPoseStack().last().pose(); var budget=new DissipationCurtainMesh.Budget();
            List<FoldedMirrorMesh.Face> faces=new ArrayList<>();
            float partial=mc.isPaused()?0:event.getPartialTick().getGameTimeDeltaPartialTick(false);
            float phase=(world.getGameTime()%2400)+partial;
            for(Marker marker:visible) {
                var pose=DissipationCurtainGeometry.pose(marker.frame.center(),marker.frame.axis());
                if(pose==null)continue;
                for(int facet=0;facet<3;facet++) {
                    if(loadedFacet(pose,facet))faces.addAll(DissipationCurtainMesh.facet(pose,facet,
                            marker.frame.phase()==DissipationCurtainState.STARTUP,marker.lease.ending()?marker.lease.fadeAge():-1,phase));
                    var contact=marker.contacts.frame(facet);
                    if(!marker.lease.ending() && contact!=null && loadedFacet(pose,facet))
                        faces.addAll(DissipationCurtainMesh.ripple(marker.frame,contact,marker.contacts.age(facet)+partial,phase));
                }
            }
            var materialEye=DissipationCurtainMesh.materialPoint(eye);
            drawOrdered(matrix,DissipationCurtainMesh.ordered(faces,materialEye,look,budget),materialEye);
        } finally {
            try{buffers.endBatch();}finally{
                modelView.popMatrix();RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(shaderColor[0],shaderColor[1],shaderColor[2],shaderColor[3]);
            }
        }
    }
    private static void drawOrdered(Matrix4f matrix,List<FoldedMirrorMesh.Face> faces,FoldedMirrorGeometry.Vector eye) {
        RenderType activeType=null; int activeLayer=-1,activeKind=-1,activeTexture=-2; FoldedMirrorMesh.Material activeMaterial=null;
        for(var face:faces) {
            ResourceLocation texture=face.texture()==FoldedMirrorMesh.SHIELD_TEXTURE?SHIELD:WHITE;
            RenderType type=FoldedMirrorRenderTypes.type(texture,FoldedMirrorMesh.additive(face),false);
            int layer=FoldedMirrorMesh.layer(face);
            boolean changed=activeType!=type || activeLayer!=layer || activeKind!=face.kind() || activeTexture!=face.texture() || !face.material().equals(activeMaterial);
            if(activeType!=null && changed)buffers.endBatch(activeType);
            if(changed)FoldedMirrorMaterial.set(face);
            activeType=type; activeLayer=layer; activeKind=face.kind(); activeTexture=face.texture(); activeMaterial=face.material();
            VertexConsumer out=buffers.getBuffer(type);
            vertex(out,matrix,face.a().subtract(eye),face.uvA(),face.color(),face.alpha());
            vertex(out,matrix,face.b().subtract(eye),face.uvB(),face.color(),face.alpha());
            vertex(out,matrix,face.c().subtract(eye),face.uvC(),face.color(),face.alpha());
            vertex(out,matrix,face.d().subtract(eye),face.uvD(),face.color(),face.alpha());
        }
        if(activeType!=null)buffers.endBatch(activeType);
    }
    private static void vertex(VertexConsumer out,Matrix4f matrix,FoldedMirrorGeometry.Vector p,FoldedMirrorMesh.Uv uv,int rgb,int alpha) {
        out.addVertex(matrix,(float)p.x(),(float)p.y(),(float)p.z()).setUv((float)uv.u(),(float)uv.v()).setColor(rgb>>>16&255,rgb>>>8&255,rgb&255,alpha);
    }
    private static Vector point(Vec3 p) { return new Vector(p.x,p.y,p.z); }

    /** Read-only snapshot: points = centre, axis, contact point; numbers = live mask, plate. */
    private static double[] dv(cn.academy.port.highpower.core.DissipationCurtainGeometry.Vector p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        for (Marker m : MARKERS.values()) { if (!validOwner(m, mc) || m.frame == null) continue; var f = m.frame;
            out.add(DecorSnap.of(m.owner == mc.player, 0, f.phase(), f.id().token(), java.util.List.of(dv(f.center()), dv(f.axis()), dv(f.point())), f.liveMask(), f.plate())); }
        return out;
    }
}

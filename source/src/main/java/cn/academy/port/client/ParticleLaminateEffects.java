package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.DisintegrationSweep;
import cn.academy.port.highpower.ParticleLaminate;
import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.ParticleLaminateGeometry;
import cn.academy.port.highpower.core.ParticleLaminateGeometry.Panel;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW241 receipt-only panels and crossed pulses. Rendering never spawns particles or changes combat state. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ParticleLaminateEffects {
    private static final ResourceLocation WHITE = texture("port/white"), SHIELD = texture("effects/mdshield");
    private static final ResourceLocation[] SMALL_RAY = {texture("effects/mdray_small/blend_in"), texture("effects/mdray_small/tile"), texture("effects/mdray_small/blend_out")};
    private static final ParticleLaminateState STATE = new ParticleLaminateState();
    private static final Map<UUID, Marker> MARKERS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static MultiBufferSource.BufferSource buffers;
    private static String terminalStatus;
    private static int statusTicks;
    private static final class Flash {
        final ParticleLaminateState.Frame frame;
        int age;
        Flash(ParticleLaminateState.Frame frame) { this.frame = frame; }
    }
    private static final class Marker {
        final Entity owner;
        final ParticleLaminateState.Lease lease = new ParticleLaminateState.Lease();
        ParticleLaminateState.Frame frame;
        Flash flash;
        int lastPulse;
        Marker(Entity owner, ParticleLaminateState.Frame frame) { this.owner = owner; update(frame); }
        void update(ParticleLaminateState.Frame next) {
            frame = next; lease.received(next);
            // A retransmission or heartbeat for the same pulse cannot renew its optical lifetime.
            if (next.phase() == ParticleLaminateState.FIRE && next.pulseIndex() > lastPulse) {
                lastPulse = next.pulseIndex();
                if (!next.path().isEmpty()) flash = new Flash(next);
            }
        }
    }
    private ParticleLaminateEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { ParticleLaminate.clientReceiver = ParticleLaminateEffects::receive; }
    }
    private static ParticleLaminateState.Frame frame(ParticleLaminate.Visual p) {
        List<Panel> panels = new ArrayList<>(p.panels().size());
        for (int i = 0; i < p.panels().size(); i++) {
            var panel=p.panels().get(i); panels.add(new Panel(i,point(panel.center()),point(panel.normal())));
        }
        return new ParticleLaminateState.Frame(new ParticleLaminateState.Identity(p.owner(),p.ownerUuid(),p.epoch(),p.input(),p.token()),
                p.sequence(),p.phase(),p.status(),p.remainingTicks(),point(p.ownerFeet()),panels,point(p.ghostCenter()),point(p.ghostNormal()),
                p.path().stream().map(s -> new ParticleLaminateState.Segment(point(s.start()),point(s.end()),s.status())).toList(),
                p.lowMode(),p.pulseIndex(),p.fieldAge());
    }
    public static void receive(ParticleLaminate.Visual packet) {
        if (packet == null || packet.ownerFeet() == null || packet.ghostCenter() == null || packet.ghostNormal() == null
                || packet.panels() == null || packet.panels().size() > 2 || packet.path() == null || packet.path().size() > 3
                || packet.panels().stream().anyMatch(p -> p == null || p.center() == null || p.normal() == null)
                || packet.path().stream().anyMatch(s -> s == null || s.start() == null || s.end() == null)) return;
        final ParticleLaminateState.Frame receipt;
        try { receipt=frame(packet); } catch (IllegalArgumentException malformedGeometry) { return; }
        if (!ParticleLaminateState.valid(receipt)) return;
        Minecraft mc=Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld=mc.level; Entity capturedPlayer=mc.player; Object capturedConnection=mc.getConnection();
        long capturedSession=AcademyClient.inputSessionEpoch(), capturedGeneration=generation;
        mc.execute(() -> {
            if (capturedWorld==null || mc.level!=capturedWorld || mc.player!=capturedPlayer || mc.getConnection()!=capturedConnection
                    || generation!=capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner=capturedWorld.getEntity(packet.owner());
            if (owner==null || !owner.getUUID().equals(packet.ownerUuid())) return;
            Marker old=MARKERS.get(packet.ownerUuid());
            if (old!=null && old.frame.id().token()==receipt.id().token() && old.owner!=owner) return;
            boolean local=owner==mc.player;
            if (local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch()) || !AcademyClient.particleLaminateInputMatches(packet.input(),packet.epoch()))) return;
            if (packet.phase()!=ParticleLaminateState.ENDED && (!owner.isAlive() || owner.isRemoved() || local && !localEligible())) return;
            if (!STATE.accept(receipt,local)) return;
            if (local) AcademyClient.particleLaminateAcceptedInput(packet.input(),packet.epoch());
            if (packet.phase()==ParticleLaminateState.ENDED) {
                MARKERS.remove(packet.ownerUuid());
                if (local) {
                    boolean refusal=old!=null && (old.frame.status()==ParticleLaminateState.REFUSED || old.frame.status()==ParticleLaminateState.DEGENERATE);
                    terminalStatus=ParticleLaminateState.status(refusal?old.frame:receipt,0,receipt.fieldAge()); statusTicks=30;
                    AcademyClient.particleLaminateEndedInput(packet.input(),packet.epoch());
                }
                return;
            }
            if (local) { terminalStatus=null; statusTicks=0; }
            if (old==null || old.frame.id().token()!=receipt.id().token()) MARKERS.put(packet.ownerUuid(),new Marker(owner,receipt));
            else old.update(receipt);
        });
    }
    public static void abortLocal(long input,long epoch) {
        Minecraft mc=Minecraft.getInstance(); synchronize(mc); STATE.cancel(epoch,input);
        Marker marker=mc.player==null?null:MARKERS.get(mc.player.getUUID());
        if (marker!=null && marker.frame.id().epoch()==epoch && marker.frame.id().input()<=input) {
            STATE.retire(marker.frame.id()); MARKERS.remove(mc.player.getUUID());
        }
        terminalStatus=null; statusTicks=0;
    }
    public static void clear() { generation++; STATE.clear(); MARKERS.clear(); terminalStatus=null; statusTicks=0; }
    private static void synchronize(Minecraft mc) {
        if (world==mc.level && localPlayer==mc.player && connection==mc.getConnection()) return;
        clear();
        // World/player replacement on the same connection retains tombstones against stale queued receipts.
        if (connection!=mc.getConnection()) STATE.resetSession();
        world=mc.level; localPlayer=mc.player; connection=mc.getConnection();
    }
    private static boolean localEligible() {
        Minecraft mc=Minecraft.getInstance(); var state=AcademyClient.state;
        return mc.screen==null && mc.isWindowActive() && mc.getCameraEntity()==mc.player
                && mc.player!=null && !mc.player.isSpectator() && state!=null && state.activated && state.overloadFine && !state.interfering
                && "meltdowner".equals(state.category) && ParticleLaminateState.eligible(state.level,state.learned(DisintegrationSweep.ID),state.learned(ParticleLaminate.ID))
                && state.presets.currentContains(ParticleLaminate.ID) && SkillCatalog.enabled("meltdowner",ParticleLaminate.ID);
    }
    private static boolean validOwner(Marker marker,Minecraft mc) {
        return world!=null && marker.owner.isAlive() && !marker.owner.isRemoved() && world.getEntity(marker.owner.getId())==marker.owner
                && marker.owner.getUUID().equals(marker.frame.id().owner())
                && (marker.owner!=mc.player || localEligible() && AcademyClient.singleOwnerEpochMatches(marker.frame.id().epoch())
                && AcademyClient.particleLaminateInputMatches(marker.frame.id().input(),marker.frame.id().epoch()));
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc=Minecraft.getInstance(); synchronize(mc); Marker marker=mc.player==null?null:MARKERS.get(mc.player.getUUID());
        if (marker==null || !validOwner(marker,mc)) return ClassicAbilityHud.DelegateVisual.IDLE;
        return marker.frame.phase()==ParticleLaminateState.EDIT?ClassicAbilityHud.DelegateVisual.CHARGE:ClassicAbilityHud.DelegateVisual.ACTIVE;
    }
    public static boolean activeLocal() { return localVisual()!=ClassicAbilityHud.DelegateVisual.IDLE; }
    /** A live two-panel lease alone permits free recall during cooldown; no new field is inferred. */
    public static boolean canRecallLocal() {
        Minecraft mc=Minecraft.getInstance(); synchronize(mc); Marker marker=mc.player==null?null:MARKERS.get(mc.player.getUUID());
        return marker!=null && validOwner(marker,mc) && !AcademyClient.state.consumptionInProgress()
                && ParticleLaminateState.recallAvailable(marker.frame,marker.lease.remaining(),marker.lease.quiet());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance(); synchronize(mc);
        if (!mc.isPaused() && statusTicks>0 && --statusTicks==0) terminalStatus=null;
        Iterator<Marker> iterator=MARKERS.values().iterator();
        while(iterator.hasNext()) {
            Marker marker=iterator.next();
            if (!mc.isPaused() && marker.flash!=null && ++marker.flash.age>=ParticleLaminateState.FLASH_TICKS) marker.flash=null;
            if (!marker.lease.tick(mc.isPaused(),validOwner(marker,mc))) {
                STATE.retire(marker.frame.id()); iterator.remove();
                if (marker.owner==mc.player) AcademyClient.particleLaminateEndedInput(marker.frame.id().input(),marker.frame.id().epoch());
            }
        }
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc=Minecraft.getInstance(); synchronize(mc);
        if(mc.player==null || mc.options.hideGui || !localEligible())return;
        Marker marker=MARKERS.get(mc.player.getUUID());
        String status=marker!=null && validOwner(marker,mc)?ParticleLaminateState.status(marker.frame,marker.lease.remaining(),marker.lease.fieldAge()):terminalStatus;
        if(status==null)return;
        Component stateText=Component.translatable("academy.highpower.particle_laminate."+status);
        Component text=stateText;
        if(marker!=null) {
            int count=marker.frame.panels().size();
            text=count==0?Component.translatable("academy.highpower.particle_laminate.status_unpaid",count,stateText):
                    count==1?Component.translatable("academy.highpower.particle_laminate.status_anchor",count,(marker.lease.remaining()+19)/20,stateText):
                    Component.translatable("academy.highpower.particle_laminate.status",count,(marker.lease.remaining()+19)/20,marker.frame.pulseIndex(),
                            Component.translatable("academy.highpower.particle_laminate."+(marker.frame.lowMode()?"low_mode":"normal_mode")),stateText);
        }
        var graphics=event.getGuiGraphics(); graphics.fill(6,54,8,64,0xB8155D34); graphics.drawString(mc.font,text,12,55,0xC4EAD1,true);
    }
    private static boolean loadedPath(Vector from,Vector to) {
        if(world==null)return false;
        var chunks=BeamEtchingMesh.crossedChunks(new BeamEtchingMesh.Point(from.x(),from.y(),from.z()),new BeamEtchingMesh.Point(to.x(),to.y(),to.z()));
        if(chunks.isEmpty())return false;
        for(var chunk:chunks)if(!world.hasChunkAt(new BlockPos(chunk.x()*16,(int)Math.floor(from.y()),chunk.z()*16)))return false;
        return true;
    }
    private static boolean loadedFrame(ParticleLaminateState.Frame frame) {
        for(var segment:frame.path())if(!loadedPath(segment.start(),segment.end()))return false;
        return true;
    }
    private static boolean loadedPanel(Panel panel) {
        var bounds=ParticleLaminateGeometry.volumeBounds(panel);
        for(int x=Math.floorDiv((int)Math.floor(bounds.min().x()),16);x<=Math.floorDiv((int)Math.floor(bounds.max().x()),16);x++)
            for(int z=Math.floorDiv((int)Math.floor(bounds.min().z()),16);z<=Math.floorDiv((int)Math.floor(bounds.max().z()),16);z++)
                if(!world.hasChunkAt(new BlockPos(x*16,(int)Math.floor(panel.center().y()),z*16)))return false;
        return true;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance(); synchronize(mc); if(world==null || MARKERS.isEmpty())return;
        Vector eye=point(event.getCamera().getPosition()); var facing=event.getCamera().getLookVector();
        Vector look=FoldedMirrorGeometry.unit(new Vector(facing.x(),facing.y(),facing.z())).orElse(null);
        if(look==null)return;
        List<Marker> visible=MARKERS.values().stream().filter(m -> validOwner(m,mc) && ParticleLaminateMesh.visible(m.frame,eye))
                .sorted(Comparator.<Marker>comparingInt(m -> m.owner==mc.player?0:1).thenComparingDouble(m -> ParticleLaminateMesh.distanceSquared(m.frame,eye)))
                .limit(ParticleLaminateMesh.MAX_VISIBLE_CONTEXTS).toList();
        if(visible.isEmpty())return;
        if(buffers==null)buffers=MultiBufferSource.immediate(new ByteBufferBuilder(262144));
        var modelView=RenderSystem.getModelViewStack(); modelView.pushMatrix(); float[] shaderColor=RenderSystem.getShaderColor().clone();
        try {
            RenderSystem.setShaderColor(1,1,1,1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix=event.getPoseStack().last().pose(); var budget=new FoldedMirrorMesh.Budget(); List<FoldedMirrorMesh.Face> faces=new ArrayList<>();
            float partial=mc.isPaused()?0:event.getPartialTick().getGameTimeDeltaPartialTick(false);
            float phase=(world.getGameTime()%2400)+partial;
            for(Marker marker:visible) {
                boolean warning=ParticleLaminateState.warning(marker.frame,marker.lease.remaining(),marker.lease.fieldAge());
                for(Panel panel:marker.frame.panels())if(loadedPanel(panel))faces.addAll(ParticleLaminateMesh.panel(panel,false,warning,phase));
                Panel ghost=ParticleLaminateState.ghost(marker.frame);
                if(ghost!=null && loadedPanel(ghost)) {
                    faces.addAll(ParticleLaminateMesh.panel(ghost,true,false,phase));
                    faces.addAll(ParticleLaminateMesh.orientation(ghost,eye,look));
                }
                if(marker.frame.phase()!=ParticleLaminateState.FIRE && loadedFrame(marker.frame))faces.addAll(ParticleLaminateMesh.path(marker.frame,eye,look,0));
                if(marker.flash!=null && loadedFrame(marker.flash.frame))faces.addAll(ParticleLaminateMesh.path(marker.flash.frame,eye,look,marker.flash.age+partial));
            }
            // The one shared cap runs after near clipping across all four contexts and every material batch.
            drawOrdered(matrix,FoldedMirrorMesh.ordered(faces,eye,look,budget),eye);
        } finally {
            try{buffers.endBatch();}finally{
                modelView.popMatrix();RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(shaderColor[0],shaderColor[1],shaderColor[2],shaderColor[3]);
            }
        }
    }
    private static void drawOrdered(Matrix4f matrix, List<FoldedMirrorMesh.Face> faces, Vector eye) {
        RenderType activeType = null; int activeLayer = -1;
        int activeKind = -1, activeTexture = -2; FoldedMirrorMesh.Material activeMaterial = null;
        for (var face : faces) {
            ResourceLocation texture = face.texture() == FoldedMirrorMesh.SHIELD_TEXTURE ? SHIELD : face.texture() < 0 ? WHITE : SMALL_RAY[face.texture()];
            RenderType type = FoldedMirrorRenderTypes.type(texture, FoldedMirrorMesh.additive(face), FoldedMirrorMesh.culled(face));
            int layer = FoldedMirrorMesh.layer(face);
            // Immediate explicit flushes preserve alpha-surface -> halo -> core -> glint order across texture changes.
            boolean materialChanged = activeKind != face.kind() || activeTexture != face.texture() || !face.material().equals(activeMaterial);
            if (activeType != null && (activeType != type || activeLayer != layer || materialChanged)) buffers.endBatch(activeType);
            if (activeType != type || activeLayer != layer || materialChanged) FoldedMirrorMaterial.set(face);
            activeType = type; activeLayer = layer;
            activeKind = face.kind(); activeTexture = face.texture(); activeMaterial = face.material();
            VertexConsumer out = buffers.getBuffer(type);
            vertex(out, matrix, face.a().subtract(eye), face.uvA(), face.color(), face.alpha());
            vertex(out, matrix, face.b().subtract(eye), face.uvB(), face.color(), face.alpha());
            vertex(out, matrix, face.c().subtract(eye), face.uvC(), face.color(), face.alpha());
            vertex(out, matrix, face.d().subtract(eye), face.uvD(), face.color(), face.alpha());
        }
        if (activeType != null) buffers.endBatch(activeType);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vector p, FoldedMirrorMesh.Uv uv, int rgb, int alpha) {
        out.addVertex(matrix, (float)p.x(), (float)p.y(), (float)p.z()).setUv((float)uv.u(), (float)uv.v()).setColor(rgb >>> 16 & 255, rgb >>> 8 & 255, rgb & 255, alpha);
    }
    private static Vector point(Vec3 point) { return new Vector(point.x, point.y, point.z); }
    private static Vec3 vec(Vector point) { return new Vec3(point.x(), point.y(), point.z()); }

    /** Read-only snapshot: points = feet, ghost centre, ghost normal, then (centre, normal) per panel; numbers = status, remaining, field age, pulse; polyline = fired path. */
    private static double[] dv(cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector p) { return p == null ? DecorSnap.NONE : new double[] {p.x(), p.y(), p.z()}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        for (Marker m : MARKERS.values()) { if (!validOwner(m, mc) || m.frame == null) continue; var f = m.frame;
            var points = new java.util.ArrayList<double[]>(java.util.List.of(dv(f.ownerFeet()), dv(f.ghostCenter()), dv(f.ghostNormal())));
            if (f.panels() != null) for (var plate : f.panels()) if (points.size() < 21) { points.add(dv(plate.center())); points.add(dv(plate.normal())); }
            var line = new java.util.ArrayList<double[]>();
            if (f.path() != null) for (var seg : f.path()) { if (line.isEmpty()) line.add(dv(seg.start())); if (line.size() < 24) line.add(dv(seg.end())); }
            out.add(new DecorSnap(m.owner == mc.player, 0, f.phase(), f.id().token(), points, new double[] {f.status(), f.remainingTicks(), f.fieldAge(), f.pulseIndex()}, line.isEmpty() ? java.util.List.of() : java.util.List.of(line)));
        }
        return out;
    }
}

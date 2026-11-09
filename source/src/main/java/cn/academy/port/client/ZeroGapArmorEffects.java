package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.ZeroGapArmor;
import cn.academy.port.highpower.DualPoint;
import cn.academy.port.highpower.ScatterPlates;
import cn.academy.port.highpower.core.ZeroGapArmorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.DissipationCurtain;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW252 scoped reuse of the original shield and NEW221 material; no prediction, particles or terrain writes. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ZeroGapArmorEffects {
    private static final ResourceLocation WHITE = texture("port/white"), SHIELD = texture("effects/mdshield");
    private static final ZeroGapArmorState STATE = new ZeroGapArmorState();
    private static final Map<UUID, Marker> MARKERS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long generation;
    private static MultiBufferSource.BufferSource buffers;
    private static final class Marker {
        final Entity owner;
        final ZeroGapArmorState.Lease lease = new ZeroGapArmorState.Lease();
        final ZeroGapArmorState.Contacts contacts = new ZeroGapArmorState.Contacts();
        ZeroGapArmorState.Frame frame;
        Marker(Entity owner, ZeroGapArmorState.Frame frame) { this.owner = owner; this.frame = frame; update(frame); }
        void update(ZeroGapArmorState.Frame next) {
            // HIT carries current geometry and the post-debit mask. Its historical point is kept separately.
            frame = next;
            lease.received(next); contacts.received(next);
        }
        void end() { lease.end(); contacts.clear(); }
    }
    private ZeroGapArmorEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { ZeroGapArmor.clientReceiver = ZeroGapArmorEffects::receive; }
    }
    private static ZeroGapArmorState.Frame frame(ZeroGapArmor.Visual p) {
        return new ZeroGapArmorState.Frame(new ZeroGapArmorState.Identity(p.entity(),p.owner(),p.epoch(),p.input(),p.token()),
                p.sequence(),p.phase(),point(p.feet()),p.yaw(),p.phaseTick(),p.selectedMask(),p.liveMask(),p.breakTicks(),p.plate(),point(p.point()));
    }
    public static void receive(ZeroGapArmor.Visual packet) {
        if(packet==null || packet.feet()==null || packet.point()==null)return;
        var receipt=frame(packet); if(!ZeroGapArmorState.valid(receipt))return;
        Minecraft mc=Minecraft.getInstance(); synchronize(mc);
        ClientLevel capturedWorld=mc.level; Entity capturedPlayer=mc.player; Object capturedConnection=mc.getConnection();
        long capturedSession=AcademyClient.inputSessionEpoch(), capturedGeneration=generation;
        mc.execute(() -> {
            if(capturedWorld==null || mc.level!=capturedWorld || mc.player!=capturedPlayer || mc.getConnection()!=capturedConnection
                    || generation!=capturedGeneration || !AcademyClient.sameInputSessionEpoch(capturedSession))return;
            Entity owner=capturedWorld.getEntity(packet.entity());
            if(!(owner instanceof Player) || !owner.getUUID().equals(packet.owner()))return;
            Marker old=MARKERS.get(packet.owner());
            if(old!=null && old.frame.id().token()==receipt.id().token() && old.owner!=owner)return;
            boolean local=owner==mc.player;
            if(local && (!AcademyClient.singleOwnerEpochMatches(packet.epoch()) || !AcademyClient.zeroGapArmorInputMatches(packet.input(),packet.epoch())))return;
            if(packet.phase()!=ZeroGapArmorState.ENDED && (!owner.isAlive() || owner.isRemoved() || owner.isSpectator() || local && !localEligible()))return;
            if(!STATE.accept(receipt,local))return;
            if(packet.phase()==ZeroGapArmorState.ENDED) {
                if(old!=null)old.end();
                if(local)AcademyClient.zeroGapArmorEndedInput(packet.input(),packet.epoch());
                return;
            }
            if(local)AcademyClient.zeroGapArmorAcceptedInput(packet.input(),packet.epoch());
            if(old==null || !old.frame.id().equals(receipt.id())) {
                if(old==null && MARKERS.size()>=ZeroGapArmorState.MAX_CONTEXTS) { STATE.retire(receipt.id()); return; }
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
                && mc.player!=null && !mc.player.isSpectator() && !mc.player.getAbilities().instabuild
                && !mc.player.getAbilities().invulnerable && !mc.player.noPhysics && !mc.player.isPassenger() && !mc.player.isSleeping() && state!=null && state.activated && state.overloadFine && !state.interfering
                && "meltdowner".equals(state.category) && ZeroGapArmorState.eligible(state.level,state.learned(DissipationCurtain.ID),state.learned(ZeroGapArmor.ID))
                && state.learned(ScatterPlates.ID) && state.learned(DualPoint.ID)
                && state.presets.currentContains(ZeroGapArmor.ID) && SkillCatalog.enabled("meltdowner",ZeroGapArmor.ID);
    }
    private static boolean validOwner(Marker marker,Minecraft mc) {
        return world!=null && marker.owner instanceof Player && marker.owner.isAlive() && !marker.owner.isRemoved() && !marker.owner.isSpectator() && world.getEntity(marker.owner.getId())==marker.owner
                && marker.owner.getUUID().equals(marker.frame.id().owner())
                && (marker.owner!=mc.player || localEligible() && AcademyClient.singleOwnerEpochMatches(marker.frame.id().epoch())
                && (marker.lease.ending() || AcademyClient.zeroGapArmorInputMatches(marker.frame.id().input(),marker.frame.id().epoch())));
    }
    private static Marker localMarker() {
        Minecraft mc=Minecraft.getInstance(); synchronize(mc); Marker marker=mc.player==null?null:MARKERS.get(mc.player.getUUID());
        return marker!=null && validOwner(marker,mc)?marker:null;
    }
    /** A fade is intentionally never reported as an active controller or a protecting plate. */
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Marker marker=localMarker();
        if(marker==null || !marker.lease.live())return ClassicAbilityHud.DelegateVisual.IDLE;
        return marker.frame.phase()==ZeroGapArmorState.STARTUP?ClassicAbilityHud.DelegateVisual.CHARGE:ClassicAbilityHud.DelegateVisual.ACTIVE;
    }
    public static boolean activeLocal() { return localVisual()!=ClassicAbilityHud.DelegateVisual.IDLE; }
    public static String localPhase() {
        Marker marker=localMarker();
        return marker==null?"idle":marker.lease.ending()?"fading":marker.frame.phase()==ZeroGapArmorState.STARTUP?"startup"
                :marker.frame.breakTicks()>0?"gap":marker.frame.liveMask()==0?"empty":"active";
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance(); synchronize(mc);
        List<ZeroGapArmorState.Identity> localLosses=new ArrayList<>();
        Iterator<Marker> iterator=MARKERS.values().iterator();
        while(iterator.hasNext()) {
            Marker marker=iterator.next(); boolean wasEnding=marker.lease.ending();
            marker.contacts.tick(mc.isPaused());
            boolean keep=marker.lease.tick(mc.isPaused(),validOwner(marker,mc));
            if(!wasEnding && (!keep || marker.lease.ending()) && marker.owner==mc.player)localLosses.add(marker.frame.id());
            if(!wasEnding && marker.lease.ending()) {
                STATE.retire(marker.frame.id()); marker.contacts.clear();
            }
            if(!keep) {
                STATE.retire(marker.frame.id()); iterator.remove();
            }
        }
        // abortLocal may touch the marker collection while synchronizing; dispatch only after iteration.
        for(var identity:localLosses)AcademyClient.zeroGapArmorLocalVisualLost(identity.input(),identity.epoch());
    }
    /** Every corner and the full inward slab must lie in already-loaded chunks. */
    private static boolean loadedPlate(ZeroGapArmorGeometry.Plate plate) {
        if(world==null || plate==null)return false;
        var bounds=ZeroGapArmorGeometry.volumeBounds(plate);
        if(!ZeroGapArmorMesh.validPoint(bounds.min()) || !ZeroGapArmorMesh.validPoint(bounds.max()))return false;
        for(int x=Math.floorDiv((int)Math.floor(bounds.min().x()),16);x<=Math.floorDiv((int)Math.floor(bounds.max().x()),16);x++)
            for(int z=Math.floorDiv((int)Math.floor(bounds.min().z()),16);z<=Math.floorDiv((int)Math.floor(bounds.max().z()),16);z++)
                if(!world.hasChunkAt(new BlockPos(x*16,(int)Math.floor(plate.center().y()),z*16)))return false;
        return true;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance(); synchronize(mc); if(world==null || MARKERS.isEmpty())return;
        Vector eye=point(event.getCamera().getPosition()); var facing=event.getCamera().getLookVector();
        var look=FoldedMirrorGeometry.unit(new FoldedMirrorGeometry.Vector(facing.x(),facing.y(),facing.z())).orElse(null);
        if(look==null)return;
        List<Marker> visible=MARKERS.values().stream().filter(m -> validOwner(m,mc)
                && m.frame.feet().subtract(eye).length()<=ZeroGapArmorMesh.CULL_DISTANCE)
                .sorted(Comparator.<Marker>comparingInt(m -> m.owner==mc.player?0:1)
                        .thenComparingDouble(m -> m.frame.feet().subtract(eye).length()))
                .limit(ZeroGapArmorMesh.MAX_VISIBLE_CONTEXTS).toList();
        if(visible.isEmpty())return;
        if(buffers==null)buffers=MultiBufferSource.immediate(new ByteBufferBuilder(131072));
        var modelView=RenderSystem.getModelViewStack(); modelView.pushMatrix(); float[] shaderColor=RenderSystem.getShaderColor().clone();
        try {
            RenderSystem.setShaderColor(1,1,1,1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix=event.getPoseStack().last().pose(); var budget=new ZeroGapArmorMesh.Budget();
            List<FoldedMirrorMesh.Face> faces=new ArrayList<>(), originalFaces=new ArrayList<>();
            boolean enhanced=ZeroGapArmorMaterial.enabled(); int materialGeneration=ZeroGapArmorMaterial.generation();
            float partial=mc.isPaused()?0:event.getPartialTick().getGameTimeDeltaPartialTick(false);
            float phase=(world.getGameTime()%2400)+partial;
            for(Marker marker:visible) {
                // Neither partial ticks nor cosmetic material time may rotate or move the physical panes.
                var pose=ZeroGapArmorMesh.snapshot(marker.frame);
                if(pose==null)continue;
                for(int index=0;index<ZeroGapArmorGeometry.PLATE_COUNT;index++) {
                    if(!loadedPlate(pose.plates().get(index)))continue;
                    var contact=marker.lease.ending()?null:marker.contacts.frame(index);
                    float age=marker.contacts.age(index)+partial;
                    int fade=marker.lease.ending()?marker.lease.fadeAge():-1;
                    var original=ZeroGapArmorMesh.plate(marker.frame,index,fade,phase,contact,age);
                    originalFaces.addAll(original);
                    faces.addAll(enhanced?ZeroGapArmorEnhancedMesh.plate(marker.frame,index,fade,phase,contact,age):original);
                    if(contact!=null){var glints=ZeroGapArmorMesh.glint(marker.frame,contact,age,phase);
                        faces.addAll(glints);originalFaces.addAll(glints);}
                }
            }
            // Whole-frame classic fallback before the first optional submission. No stale resource generation.
            if(enhanced && (materialGeneration!=ZeroGapArmorMaterial.generation() || !ZeroGapArmorMaterial.enabled()))faces=originalFaces;
            drawOrdered(matrix,ZeroGapArmorMesh.ordered(faces,eye,look,budget),eye);
            decorate(event,mc,visible,partial);
        } finally {
            try{buffers.endBatch();}finally{
                modelView.popMatrix();RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(shaderColor[0],shaderColor[1],shaderColor[2],shaderColor[3]);
            }
        }
    }
    /** CINEMATIC footprint, halos, rim sparkles and contact bursts; independent of the plate meshes. */
    private static void decorate(RenderLevelStageEvent event,Minecraft mc,List<Marker> visible,float partial) {
        var tier=cn.academy.port.VisualTierConfig.tier();
        if(tier.layers<3 || !(cn.academy.port.HighPowerVisualConfig.SPEC.isLoaded() && cn.academy.port.HighPowerVisualConfig.ZERO_GAP_ARMOR_STATE.get()))return;
        var parts=new FxLib.Builder(); double time=world.getGameTime()+partial;
        for(Marker marker:visible) {
            var pose=ZeroGapArmorMesh.snapshot(marker.frame); if(pose==null)continue;
            List<RailThunderArrayGeometry.Point> centers=new ArrayList<>(); List<ZeroGapArmorDecor.Contact> contacts=new ArrayList<>();
            for(int index=0;index<ZeroGapArmorGeometry.PLATE_COUNT;index++) {
                if((marker.frame.selectedMask() & 1 << index)==0)continue;
                var c=pose.plates().get(index).center(); centers.add(new RailThunderArrayGeometry.Point(c.x(),c.y(),c.z()));
                var contact=marker.lease.ending()?null:marker.contacts.frame(index);
                if(contact==null)continue;
                float age=marker.contacts.age(index)+partial;
                var at=ZeroGapArmorMesh.currentContact(marker.frame,contact,age);
                if(at==null)continue;
                var normal=ZeroGapArmorGeometry.frame(pose.plates().get(index)).normal();
                contacts.add(new ZeroGapArmorDecor.Contact(new RailThunderArrayGeometry.Point(at.x(),at.y(),at.z()),new RailThunderArrayGeometry.Point(normal.x(),normal.y(),normal.z()),age));
            }
            var f=marker.frame.feet();
            parts.add(ZeroGapArmorDecor.fx(new RailThunderArrayGeometry.Point(f.x(),f.y(),f.z()),centers,contacts,marker.frame.id().token(),time,tier));
        }
        VisualKit.render(event,parts.build(),tier,ZeroGapArmorDecor.MAX_QUADS);
    }
    private static void drawOrdered(Matrix4f matrix,List<FoldedMirrorMesh.Face> faces,FoldedMirrorGeometry.Vector eye) {
        RenderType activeType=null; int activeLayer=-1,activeKind=-1,activeTexture=-2; FoldedMirrorMesh.Material activeMaterial=null;
        for(var face:faces) {
            ResourceLocation texture=face.texture()==FoldedMirrorMesh.SHIELD_TEXTURE?SHIELD:WHITE;
            boolean enhanced=ZeroGapArmorEnhancedMesh.isEnhancedRim(face);
            RenderType type=enhanced?ZeroGapArmorMaterial.type(texture):FoldedMirrorRenderTypes.type(texture,FoldedMirrorMesh.additive(face),false);
            int layer=FoldedMirrorMesh.layer(face);
            boolean changed=activeType!=type || activeLayer!=layer || activeKind!=face.kind() || activeTexture!=face.texture() || !face.material().equals(activeMaterial);
            if(activeType!=null && changed)buffers.endBatch(activeType);
            if(changed){if(enhanced)ZeroGapArmorMaterial.set(face);else FoldedMirrorMaterial.set(face);}
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
}

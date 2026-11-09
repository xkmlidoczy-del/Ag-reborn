package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.highpower.MagneticArrowSeparation;
import cn.academy.port.highpower.MagneticItemPull;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.*;

/** NEW133 arrow corridor and outward guides, always rooted in accepted server state and real arrows. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class MagneticArrowSeparationEffects {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("academy","textures/port/white.png");
    private static final int MAX_CONTEXTS=64,MAX_VISIBLE_CONTEXTS=8,QUIET_TICKS=12,PULSE_TICKS=20;
    private static final MagneticArrowSeparationState STATE=new MagneticArrowSeparationState();
    private static final Map<UUID,Marker> MARKERS=new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static MultiBufferSource.BufferSource buffers;
    private static MagneticItemPull.ArrowSelectionPreview preview;
    private static long previewRenderedAt;
    private static final class Marker {
        final Entity owner;
        MagneticArrowSeparation.Visual state;
        int quiet,pulseAge;
        Marker(Entity owner,MagneticArrowSeparation.Visual state){this.owner=owner;this.state=state;}
        MagneticArrowSeparationState.Identity identity(){return new MagneticArrowSeparationState.Identity(state.owner(),state.epoch(),state.input(),state.token());}
    }
    private MagneticArrowSeparationEffects() {}
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event){
            MagneticArrowSeparation.clientReceiver=MagneticArrowSeparationEffects::receive;
            MagneticItemPull.arrowPreviewReceiver=MagneticArrowSeparationEffects::receivePreview;
        }
    }
    public static void receivePreview(MagneticItemPull.ArrowSelectionPreview packet){
        if(packet==null||packet.owner()==null||packet.input()<=0||packet.epoch()<=0)return;
        Minecraft mc=Minecraft.getInstance();ClientLevel captured=mc.level;Object capturedConnection=mc.getConnection();long capturedSession=AcademyClient.inputSessionEpoch();
        mc.execute(()->{
            if(captured==null||mc.level!=captured||mc.getConnection()!=capturedConnection||!AcademyClient.sameInputSessionEpoch(capturedSession))return;
            synchronize(mc);
            if(mc.player==null||mc.player.getId()!=packet.entity()||!mc.player.getUUID().equals(packet.owner())
                    ||!AcademyClient.singleOwnerEpochMatches(packet.epoch())||!MagneticItemPullSelection.previewingArrows(packet.input(),packet.epoch()))return;
            if(!packet.valid()){closePreview();MagneticItemPullSelection.cancel();return;}
            if(!finite(packet.origin())||!finite(packet.axis())||Math.abs(packet.axis().lengthSqr()-1)>.01
                    ||!Double.isFinite(packet.range())||packet.range()<=0||packet.range()>24||packet.expiresAt()<=captured.getGameTime())return;
            // A lease's pose is immutable; repeated or delayed previews never reanchor it.
            if(preview!=null)return;
            preview=packet;previewRenderedAt=0;
        });
    }
    public static void receive(MagneticArrowSeparation.Visual packet){
        if(packet==null||packet.entity()<0||!finite(packet.origin())||!finite(packet.axis())||Math.abs(packet.axis().lengthSqr()-1)>.01||!Double.isFinite(packet.range())||packet.range()<=0||packet.range()>24)return;
        var frame=new MagneticArrowSeparationState.Frame(new MagneticArrowSeparationState.Identity(packet.owner(),packet.epoch(),packet.input(),packet.token()),packet.sequence(),packet.phase(),packet.targets());
        if(!MagneticArrowSeparationState.valid(frame))return;
        Minecraft mc=Minecraft.getInstance();ClientLevel captured=mc.level;Object capturedConnection=mc.getConnection();long capturedSession=AcademyClient.inputSessionEpoch();
        mc.execute(()->{
            if(captured==null||mc.level!=captured||mc.getConnection()!=capturedConnection||!AcademyClient.sameInputSessionEpoch(capturedSession))return;
            synchronize(mc);Entity owner=captured.getEntity(packet.entity());
            if(owner==null||!owner.getUUID().equals(packet.owner())||!owner.isAlive()||owner.isRemoved())return;
            if(owner==mc.player&&(!AcademyClient.singleOwnerEpochMatches(packet.epoch())||!localEligible()))return;
            if(!STATE.accept(frame,owner==mc.player))return;
            if(packet.phase()==2){MARKERS.remove(packet.owner());return;}
            Marker old=MARKERS.get(packet.owner());
            if(old==null||old.state.token()!=packet.token()){
                if(old==null&&MARKERS.size()>=MAX_CONTEXTS){STATE.retire(frame.id());return;}
                MARKERS.put(packet.owner(),new Marker(owner,packet));
                CastPoseClient.acceptedRelease("magnetic_item_pull",packet.entity(),packet.owner(),packet.input(),packet.epoch(),packet.token(),false);
            }else{old.state=packet;old.quiet=0;}
        });
    }
    /** True only after a fresh server corridor was visibly rendered for this exact held input. */
    public static boolean previewReady(HighPowerInput.Input input){
        Minecraft mc=Minecraft.getInstance();synchronize(mc);if(input==null||mc.player==null)return false;
        return validPreview()&&preview.input()==input.input()&&preview.epoch()==input.epoch()
                &&MagneticItemPullSelectionGeometry.fresh(net.minecraft.Util.getMillis(),previewRenderedAt);
    }
    public static void abortLocal(long input,long epoch){
        Minecraft mc=Minecraft.getInstance();synchronize(mc);STATE.cancel(epoch,input);
        if(preview!=null&&preview.input()==input&&preview.epoch()==epoch)closePreview();
        if(mc.player==null)return;Marker marker=MARKERS.get(mc.player.getUUID());
        if(marker!=null&&marker.state.input()==input&&marker.state.epoch()==epoch){STATE.retire(marker.identity());MARKERS.remove(mc.player.getUUID());}
    }
    public static String previewRange(){return validPreview()?String.format(java.util.Locale.ROOT,"%.1f",preview.range()):"≤24";}
    public static void closePreview(){preview=null;previewRenderedAt=0;}
    private static boolean validPreview(){return preview!=null&&world!=null&&world.getGameTime()<preview.expiresAt()
            &&MagneticItemPullSelection.previewingArrows(preview.input(),preview.epoch());}
    public static void clear(){MARKERS.clear();STATE.clear();closePreview();}
    private static void synchronize(Minecraft mc){
        if(world==mc.level&&localPlayer==mc.player&&connection==mc.getConnection())return;
        clear();world=mc.level;localPlayer=mc.player;connection=mc.getConnection();
    }
    private static boolean localEligible(){
        Minecraft mc=Minecraft.getInstance();var state=AcademyClient.state;
        return (mc.screen==null||MagneticItemPullSelection.ownScreen())&&mc.isWindowActive()&&state.activated&&state.overloadFine&&!state.interfering
                &&HighPowerSkills.CATEGORY.equals(state.category)&&state.level>=3&&state.level<=5&&state.learned(HighPowerSkills.MAGNETIC_ITEM_PULL)
                &&state.presets.currentContains(HighPowerSkills.MAGNETIC_ITEM_PULL)&&SkillCatalog.enabled(HighPowerSkills.CATEGORY,HighPowerSkills.MAGNETIC_ITEM_PULL);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();synchronize(mc);if(mc.isPaused())return;
        if(preview!=null&&!validPreview())closePreview();
        Iterator<Marker> it=MARKERS.values().iterator();
        while(it.hasNext()){
            Marker m=it.next();m.quiet++;
            m.pulseAge++;
            if(world==null||!m.owner.isAlive()||m.owner.isRemoved()||world.getEntity(m.owner.getId())!=m.owner
                    ||m.quiet>=QUIET_TICKS||m.pulseAge>=PULSE_TICKS+QUIET_TICKS
                    ||m.owner==mc.player&&(!localEligible()||!AcademyClient.singleOwnerEpochMatches(m.state.epoch()))){
                STATE.retire(m.identity());it.remove();
            }
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance();synchronize(mc);if(world==null||MARKERS.isEmpty()&&!validPreview())return;
        List<Marker> visible=MARKERS.values().stream().filter(m->m.owner==mc.player||m.state.origin().distanceToSqr(event.getCamera().getPosition())<=48*48)
                .sorted(Comparator.comparingInt(m->m.owner==mc.player?0:1)).limit(MAX_VISIBLE_CONTEXTS).toList();
        Set<UUID> wanted=new HashSet<>();for(Marker marker:visible)if(marker.state.phase()==1)wanted.addAll(marker.state.targets());
        Map<UUID,Arrow> arrows=new HashMap<>();int examined=0;
        for(Entity entity:world.entitiesForRendering()){
            if(++examined>2048||arrows.size()==wanted.size())break;
            if(entity instanceof Arrow arrow&&arrow.isAlive()&&!arrow.isRemoved()&&wanted.contains(arrow.getUUID()))arrows.put(arrow.getUUID(),arrow);
        }
        if(buffers==null)buffers=MultiBufferSource.immediate(new ByteBufferBuilder(32768));
        var type=ClassicRenderTypes.world(WHITE);
        var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();
        try {
        modelView.set(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();
        VertexConsumer out=buffers.getBuffer(type);
        Matrix4f matrix=event.getPoseStack().last().pose();
        Matrix4f projection=MagneticItemPullSelectionGeometry.viewProjection(event.getProjectionMatrix(),event.getModelViewMatrix(),matrix);
        Vec3 camera=event.getCamera().getPosition();var cameraLook=event.getCamera().getLookVector();Vec3 look=new Vec3(cameraLook.x(),cameraLook.y(),cameraLook.z());
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if(validPreview()){
            int shown=0;
            for(var segment:MagneticArrowSeparationGeometry.corridor(point(preview.origin()),point(preview.axis()),preview.range()))
                if(line(out,matrix,projection,vec(segment.from()).subtract(camera),vec(segment.to()).subtract(camera),look,.004,85))shown++;
            if(shown>0)previewRenderedAt=net.minecraft.Util.getMillis();
        }
        for(Marker marker:visible){
            if(marker.owner==mc.player&&!localEligible())continue;
            var origin=point(marker.state.origin());var axis=point(marker.state.axis());
            int shown=0;
            for(var segment:MagneticArrowSeparationGeometry.corridor(origin,axis,marker.state.range())){
                if(line(out,matrix,projection,vec(segment.from()).subtract(camera),vec(segment.to()).subtract(camera),look,.004,65))shown++;
            }
            if(marker.state.phase()!=1)continue;
            for(UUID id:marker.state.targets()){
                Arrow arrow=arrows.get(id);if(arrow==null)continue;
                Vec3 position=arrow.getPosition(partial);
                for(var segment:MagneticArrowSeparationGeometry.outward(point(position),origin,axis))
                    line(out,matrix,projection,vec(segment.from()).subtract(camera),vec(segment.to()).subtract(camera),look,.005,145);
            }
        }
        buffers.endBatch(type);
        } finally {
            try { buffers.endBatch(); }
            finally { modelView.popMatrix();RenderSystem.applyModelViewMatrix(); }
        }
    }
    private static boolean line(VertexConsumer out,Matrix4f matrix,Matrix4f projection,Vec3 start,Vec3 end,Vec3 look,double width,int alpha){
        // Clip against the actual camera near plane in every camera mode; world GPU rendering applies its model-view once.
        var clipped=PolarityTetherGeometry.clip(polar(start),polar(end),polar(look),width);if(clipped==null)return false;
        start=new Vec3(clipped.start().x(),clipped.start().y(),clipped.start().z());end=new Vec3(clipped.end().x(),clipped.end().y(),clipped.end().z());width=clipped.halfWidth();
        if(!visible(projection,start)&&!visible(projection,end)&&!visible(projection,start.add(end).scale(.5)))return false;
        Vec3 direction=end.subtract(start);if(!finite(direction)||direction.lengthSqr()<1e-6)return false;
        Vec3 normal=direction.cross(start.scale(-1)).normalize();if(normal.lengthSqr()<1e-10)normal=direction.cross(new Vec3(0,1,0)).normalize();
        if(normal.lengthSqr()<1e-10)normal=new Vec3(1,0,0);Vec3 side=normal.scale(width);
        vertex(out,matrix,start.add(side),0,0,alpha);vertex(out,matrix,start.subtract(side),0,1,alpha);
        vertex(out,matrix,end.subtract(side),1,1,alpha);vertex(out,matrix,end.add(side),1,0,alpha);return true;
    }
    private static boolean visible(Matrix4f projection,Vec3 p){var clip=projection.transform(new Vector4f((float)p.x,(float)p.y,(float)p.z,1));return clip.w>0&&Float.isFinite(clip.w)&&Math.abs(clip.x)<=clip.w&&Math.abs(clip.y)<=clip.w&&Math.abs(clip.z)<=clip.w;}
    private static void vertex(VertexConsumer out,Matrix4f matrix,Vec3 p,float u,float v,int alpha){out.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(157,216,255,alpha);}
    private static MagneticArrowSeparationGeometry.Point point(Vec3 p){return new MagneticArrowSeparationGeometry.Point(p.x,p.y,p.z);}
    private static Vec3 vec(MagneticArrowSeparationGeometry.Point p){return new Vec3(p.x(),p.y(),p.z());}
    private static PolarityTetherGeometry.Point polar(Vec3 p){return new PolarityTetherGeometry.Point(p.x,p.y,p.z);}
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
}

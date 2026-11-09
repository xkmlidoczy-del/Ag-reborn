package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.PotentialBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** NEW142: one renewable cosmetic controller; absorption and paid pulse are distinct server events. */
@EventBusSubscriber(modid="academy", value=Dist.CLIENT)
public final class PotentialBufferEffects {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("academy","textures/port/white.png");
    private static final int MAX_CONTEXTS=64, MAX_VISIBLE_CONTEXTS=8;
    private static final PotentialBufferState STATE=new PotentialBufferState();
    private static final Map<UUID,Marker> MARKERS=new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long clearGeneration;
    private static MultiBufferSource.BufferSource buffers;

    private static final class Marker {
        final Entity owner;
        final PotentialBufferState.Lease lease=new PotentialBufferState.Lease();
        PotentialBuffer.Visual state;
        Vec3 hitPoint,hitFacing,pulsePoint,pulseFacing;
        long hitSalt;
        Marker(Entity owner,PotentialBuffer.Visual packet){this.owner=owner;update(packet);}
        PotentialBufferState.Identity identity(){return identityOf(state);}
        void update(PotentialBuffer.Visual packet){
            state=packet;
            lease.received(packet.phase());
            if(packet.phase()==PotentialBufferState.HIT){
                hitPoint=packet.impact();
                Vec3 outward=packet.impact().subtract(packet.point().add(0,1,0));
                hitFacing=outward.lengthSqr()>1e-8?outward.normalize():packet.facing();
                hitSalt=packet.token()^packet.sequence();
            }
            if(packet.phase()==PotentialBufferState.PULSE){pulsePoint=packet.point();pulseFacing=packet.facing();}
        }
    }
    private PotentialBufferEffects(){}
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event){PotentialBuffer.clientReceiver=PotentialBufferEffects::receive;}
    }
    private static PotentialBufferState.Identity identityOf(PotentialBuffer.Visual packet){
        return new PotentialBufferState.Identity(packet.entity(),packet.owner(),packet.epoch(),packet.input(),packet.token());
    }
    private static PotentialBufferState.Frame frame(PotentialBuffer.Visual packet){
        return new PotentialBufferState.Frame(identityOf(packet),packet.sequence(),packet.phase(),point(packet.point()),point(packet.facing()),point(packet.impact()));
    }
    public static void receive(PotentialBuffer.Visual packet){
        if(packet==null||packet.point()==null||packet.facing()==null||packet.impact()==null)return;
        var frame=frame(packet);if(!PotentialBufferState.valid(frame))return;
        Minecraft mc=Minecraft.getInstance();synchronize(mc);
        ClientLevel captured=mc.level;Entity capturedPlayer=mc.player;Object capturedConnection=mc.getConnection();
        long capturedSession=AcademyClient.inputSessionEpoch(),generation=clearGeneration;
        mc.execute(()->{
            if(captured==null||mc.level!=captured||mc.player!=capturedPlayer||mc.getConnection()!=capturedConnection
                    ||clearGeneration!=generation||!AcademyClient.sameInputSessionEpoch(capturedSession))return;
            Entity owner=captured.getEntity(packet.entity());
            if(owner==null||!owner.getUUID().equals(packet.owner()))return;
            Marker old=MARKERS.get(packet.owner());
            if(old!=null&&old.state.input()==packet.input()&&old.state.epoch()==packet.epoch()&&old.owner!=owner)return;
            if(owner==mc.player&&(!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    ||!AcademyClient.potentialBufferInputMatches(packet.input(),packet.epoch())))return;
            // A terminal closes an existing owner even if its eligibility has just disappeared.
            if(packet.phase()!=PotentialBufferState.ENDED&&(!owner.isAlive()||owner.isRemoved()||owner==mc.player&&!localEligible()))return;
            if(!STATE.accept(frame,owner==mc.player))return;
            if(packet.phase()==PotentialBufferState.ENDED){
                MARKERS.remove(packet.owner());
                if(owner==mc.player)AcademyClient.potentialBufferEndedInput(packet.input(),packet.epoch());
                return;
            }
            if(owner==mc.player)AcademyClient.potentialBufferAcceptedInput(packet.input(),packet.epoch());
            if(old==null||!old.identity().equals(frame.id())){
                if(old==null&&MARKERS.size()>=MAX_CONTEXTS){STATE.retire(frame.id());return;}
                MARKERS.put(packet.owner(),new Marker(owner,packet));
            }else old.update(packet);
        });
    }
    public static void abortLocal(long input,long epoch){
        Minecraft mc=Minecraft.getInstance();synchronize(mc);
        if(!AcademyClient.singleOwnerEpochMatches(epoch))return;
        STATE.cancel(epoch,input);
        if(mc.player==null)return;
        Marker marker=MARKERS.get(mc.player.getUUID());
        if(marker!=null&&marker.owner==mc.player&&marker.state.epoch()==epoch&&marker.state.input()<=input){
            STATE.retire(marker.identity());MARKERS.remove(mc.player.getUUID());
        }
    }
    /** Clearing within the same connection closes visuals without reopening delayed packets. */
    public static void clear(){clearGeneration++;STATE.clear();MARKERS.clear();}
    private static void synchronize(Minecraft mc){
        if(world==mc.level&&localPlayer==mc.player&&connection==mc.getConnection())return;
        clear();STATE.resetSession();world=mc.level;localPlayer=mc.player;connection=mc.getConnection();
    }
    private static boolean localEligible(){
        Minecraft mc=Minecraft.getInstance();var state=AcademyClient.state;
        // Neither shared-CP updates nor an empty shield predicts the server toggle state.
        return mc.screen==null&&mc.isWindowActive()&&state!=null&&state.activated&&state.overloadFine&&!state.interfering
                &&state.category.equals("electromaster")&&state.level>=4&&state.level<=5&&state.learned(PotentialBuffer.ID)
                &&state.presets.currentContains(PotentialBuffer.ID)&&SkillCatalog.enabled("electromaster",PotentialBuffer.ID);
    }
    private static boolean validOwner(Marker marker,Minecraft mc){
        return world!=null&&marker.owner.isAlive()&&!marker.owner.isRemoved()&&world.getEntity(marker.owner.getId())==marker.owner
                &&(marker.owner!=mc.player||localEligible()&&AcademyClient.singleOwnerEpochMatches(marker.state.epoch())
                &&AcademyClient.potentialBufferInputMatches(marker.state.input(),marker.state.epoch()));
    }
    /** Read-only existing slot highlight, including after physical key release. No new HUD panel. */
    public static boolean activeLocal(){
        Minecraft mc=Minecraft.getInstance();synchronize(mc);if(mc.player==null)return false;
        Marker marker=MARKERS.get(mc.player.getUUID());
        return marker!=null&&marker.owner==mc.player&&validOwner(marker,mc)&&marker.lease.quiet()<PotentialBufferState.QUIET_TICKS;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();synchronize(mc);boolean paused=mc.isPaused();
        Iterator<Marker> iterator=MARKERS.values().iterator();
        while(iterator.hasNext()){
            Marker marker=iterator.next();
            if(!marker.lease.tick(paused,validOwner(marker,mc))){
                STATE.retire(marker.identity());iterator.remove();
                if(marker.owner==mc.player)AcademyClient.potentialBufferEndedInput(marker.state.input(),marker.state.epoch());
            }
        }
    }
    /** Read-only world clipping. No missing chunk is loaded to draw cosmetic geometry. */
    private static boolean visible(Entity owner,Vec3 start,Vec3 end){
        if(world==null||!PotentialBufferGeometry.validPoint(point(start))||!PotentialBufferGeometry.validPoint(point(end))
                ||start.distanceToSqr(end)>PotentialBufferGeometry.PULSE_RANGE*PotentialBufferGeometry.PULSE_RANGE)return false;
        if(!world.hasChunkAt(BlockPos.containing(start))||!world.hasChunkAt(BlockPos.containing(end)))return false;
        return world.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner)).getType()==HitResult.Type.MISS;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance();synchronize(mc);if(world==null||MARKERS.isEmpty())return;
        Vec3 camera=event.getCamera().getPosition();
        List<Marker> visible=MARKERS.values().stream().filter(m->validOwner(m,mc)&&(m.owner==mc.player||m.state.point().distanceToSqr(camera)<=48*48))
                .sorted(Comparator.comparingInt(m->m.owner==mc.player?0:1)).limit(MAX_VISIBLE_CONTEXTS).toList();
        if(visible.isEmpty())return;
        if(buffers==null)buffers=MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        var type=ClassicRenderTypes.world(WHITE);var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();
        try{
            modelView.set(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();
            VertexConsumer out=buffers.getBuffer(type);Matrix4f matrix=event.getPoseStack().last().pose();
            var cameraLook=event.getCamera().getLookVector();var cameraUp=event.getCamera().getUpVector();
            Vec3 look=new Vec3(cameraLook.x(),cameraLook.y(),cameraLook.z()),up=new Vec3(cameraUp.x(),cameraUp.y(),cameraUp.z());
            for(Marker marker:visible){
                // Sparse low-opacity cap, with orientation and anchor taken from the accepted server frame.
                for(var segment:PotentialBufferGeometry.layer(point(marker.state.point().add(0,1,0)),point(marker.state.facing()),marker.state.token(),marker.lease.frame()))
                    line(out,matrix,marker.owner,vec(segment.start()),vec(segment.end()),camera,look,up,.0026,126,202,240,85);
                int hitAge=marker.lease.hitAge();
                if(marker.hitPoint!=null&&hitAge<PotentialBufferState.HIT_TICKS){
                    for(var segment:PotentialBufferGeometry.hit(point(marker.hitPoint),point(marker.hitFacing),marker.hitSalt,hitAge)){
                        line(out,matrix,marker.owner,vec(segment.start()),vec(segment.end()),camera,look,up,.010,93,164,248,80-hitAge*12);
                        line(out,matrix,marker.owner,vec(segment.start()),vec(segment.end()),camera,look,up,.0028,228,249,255,230-hitAge*48);
                    }
                    // Twelve rendered flecks maximum per controller. Nothing spawns a particle entity.
                    for(var grain:PotentialBufferGeometry.grains(point(marker.hitPoint),point(marker.hitFacing),marker.hitSalt,hitAge)){
                        Vec3 center=vec(grain.point()),side=look.cross(up).normalize().scale(grain.size());
                        line(out,matrix,marker.owner,center.subtract(side),center.add(side),camera,look,up,grain.size()*.35,230,249,255,210-hitAge*45);
                    }
                }
                if(marker.pulsePoint!=null)for(var segment:PotentialBufferGeometry.pulse(point(marker.pulsePoint),point(marker.pulseFacing),marker.lease.pulseAge()))
                    line(out,matrix,marker.owner,vec(segment.start()),vec(segment.end()),camera,look,up,.004,167,222,255,175-marker.lease.pulseAge()*24);
            }
            buffers.endBatch(type);
        }finally{try{buffers.endBatch();}finally{modelView.popMatrix();RenderSystem.applyModelViewMatrix();}}
    }
    private static void line(VertexConsumer out,Matrix4f matrix,Entity owner,Vec3 a,Vec3 b,Vec3 camera,Vec3 look,Vec3 up,double width,int r,int g,int blue,int alpha){
        if(alpha<=0||!visible(owner,a,b))return;
        var clipped=PotentialBufferGeometry.nearClip(point(a.subtract(camera)),point(b.subtract(camera)),point(look),width);
        for(var segment:PotentialBufferGeometry.clearCenter(clipped,point(look),point(up),width)){
            var ribbon=PotentialBufferGeometry.ribbon(segment,point(look),width);if(ribbon==null)continue;
            Vec3 start=vec(ribbon.start()),end=vec(ribbon.end()),side=vec(ribbon.side());
            vertex(out,matrix,start.add(side),0,0,r,g,blue,alpha);vertex(out,matrix,start.subtract(side),0,1,r,g,blue,alpha);
            vertex(out,matrix,end.subtract(side),1,1,r,g,blue,alpha);vertex(out,matrix,end.add(side),1,0,r,g,blue,alpha);
        }
    }
    private static void vertex(VertexConsumer out,Matrix4f matrix,Vec3 p,float u,float v,int r,int g,int b,int alpha){
        out.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(r,g,b,alpha);
    }
    private static PotentialBufferGeometry.Point point(Vec3 value){return new PotentialBufferGeometry.Point(value.x,value.y,value.z);}
    private static Vec3 vec(PotentialBufferGeometry.Point value){return new Vec3(value.x(),value.y(),value.z());}

    /** Read-only snapshot: points = body, facing, impact; numbers = phase. */
    private static double[] dp(net.minecraft.world.phys.Vec3 p) { return p == null ? DecorSnap.NONE : new double[] {p.x, p.y, p.z}; }
    static java.util.List<DecorSnap> decorSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var out = new java.util.ArrayList<DecorSnap>();
        if (world == null) return out;
        for (Marker m : MARKERS.values()) {
            if (!validOwner(m, mc)) continue;
            var s = m.state;
            out.add(DecorSnap.of(m.owner == mc.player, 0, s.phase(), s.token(), java.util.List.of(dp(s.point()), dp(s.facing()), dp(s.impact())), s.phase()));
        }
        return out;
    }
}

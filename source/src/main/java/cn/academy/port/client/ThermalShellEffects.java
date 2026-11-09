package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.ScatterPlates;
import cn.academy.port.highpower.ThermalShell;
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

/** NEW222: four thin broken green-white rings, with server-receipted absorption and deferred pulse only. */
@EventBusSubscriber(modid="academy", value=Dist.CLIENT)
public final class ThermalShellEffects {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("academy","textures/port/white.png");
    private static final ResourceLocation SMALL_RAY=ResourceLocation.fromNamespaceAndPath("academy","textures/effects/mdray_small/tile.png");
    private static final int MAX_CONTEXTS=64, MAX_VISIBLE_CONTEXTS=8;
    private static final ThermalShellState STATE=new ThermalShellState();
    private static final Map<UUID,Marker> MARKERS=new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static volatile long clearGeneration;
    private static MultiBufferSource.BufferSource buffers,greenBuffers;

    private static final class Marker {
        final Entity owner;
        final ThermalShellState.Lease lease=new ThermalShellState.Lease();
        final ThermalShellState.Events events=new ThermalShellState.Events();
        ThermalShell.Visual state;
        Marker(Entity owner,ThermalShell.Visual packet){this.owner=owner;update(packet);}
        ThermalShellState.Identity identity(){return identityOf(state);}
        void update(ThermalShell.Visual packet){
            state=packet;
            lease.received(packet.phase());
            events.received(frame(packet));
        }
    }
    private ThermalShellEffects(){}
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event){ThermalShell.clientReceiver=ThermalShellEffects::receive;}
    }
    private static ThermalShellState.Identity identityOf(ThermalShell.Visual packet){
        return new ThermalShellState.Identity(packet.entity(),packet.owner(),packet.epoch(),packet.input(),packet.token());
    }
    private static ThermalShellState.Frame frame(ThermalShell.Visual packet){
        return new ThermalShellState.Frame(identityOf(packet),packet.sequence(),packet.phase(),point(packet.point()),point(packet.facing()),point(packet.impact()));
    }
    public static void receive(ThermalShell.Visual packet){
        if(packet==null||packet.point()==null||packet.facing()==null||packet.impact()==null)return;
        var frame=frame(packet);if(!ThermalShellState.valid(frame))return;
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
                    ||!AcademyClient.thermalShellInputMatches(packet.input(),packet.epoch())))return;
            // A terminal closes an existing owner even if its eligibility has just disappeared.
            if(packet.phase()!=ThermalShellState.ENDED&&(!owner.isAlive()||owner.isRemoved()||owner==mc.player&&!localEligible()))return;
            if(!STATE.accept(frame,owner==mc.player))return;
            if(packet.phase()==ThermalShellState.ENDED){
                MARKERS.remove(packet.owner());
                if(owner==mc.player)AcademyClient.thermalShellEndedInput(packet.input(),packet.epoch());
                return;
            }
            if(owner==mc.player)AcademyClient.thermalShellAcceptedInput(packet.input(),packet.epoch());
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
                &&state.category.equals("meltdowner")&&ThermalShellState.eligibleLevel(state.level)
                &&state.learned(ThermalShell.ID)&&state.learned(ScatterPlates.ID)
                &&state.presets.currentContains(ThermalShell.ID)&&SkillCatalog.enabled("meltdowner",ThermalShell.ID);
    }
    private static boolean validOwner(Marker marker,Minecraft mc){
        return world!=null&&marker.owner.isAlive()&&!marker.owner.isRemoved()&&world.getEntity(marker.owner.getId())==marker.owner
                &&(marker.owner!=mc.player||localEligible()&&AcademyClient.singleOwnerEpochMatches(marker.state.epoch())
                &&AcademyClient.thermalShellInputMatches(marker.state.input(),marker.state.epoch()));
    }
    /** Read-only existing slot highlight, including after physical key release. No new HUD panel. */
    public static boolean activeLocal(){
        Minecraft mc=Minecraft.getInstance();synchronize(mc);if(mc.player==null)return false;
        Marker marker=MARKERS.get(mc.player.getUUID());
        return marker!=null&&marker.owner==mc.player&&validOwner(marker,mc)&&marker.lease.quiet()<ThermalShellState.QUIET_TICKS;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();synchronize(mc);boolean paused=mc.isPaused();
        Iterator<Marker> iterator=MARKERS.values().iterator();
        while(iterator.hasNext()){
            Marker marker=iterator.next();
            if(!marker.lease.tick(paused,validOwner(marker,mc))){
                STATE.retire(marker.identity());iterator.remove();
                if(marker.owner==mc.player)AcademyClient.thermalShellEndedInput(marker.state.input(),marker.state.epoch());
            }
        }
    }
    /** Read-only loaded-world clipping. Cosmetic rendering never loads chunks or changes terrain. */
    private static boolean visible(Entity owner,Vec3 start,Vec3 end){
        if(world==null||!ThermalShellMesh.validPoint(point(start))||!ThermalShellMesh.validPoint(point(end))
                ||start.distanceToSqr(end)>ThermalShellMesh.PULSE_RANGE*ThermalShellMesh.PULSE_RANGE+.0001)return false;
        int steps=Math.max(1,(int)Math.ceil(start.distanceTo(end)*4));
        for(int i=0;i<=steps;i++)if(!world.hasChunkAt(BlockPos.containing(start.lerp(end,i/(double)steps))))return false;
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
        if(greenBuffers==null)greenBuffers=MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        var greenType=ClassicRenderTypes.world(SMALL_RAY);var coreType=ClassicRenderTypes.world(WHITE);
        var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();
        try{
            modelView.set(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();
            VertexConsumer green=greenBuffers.getBuffer(greenType),core=buffers.getBuffer(coreType);
            Matrix4f matrix=event.getPoseStack().last().pose();
            var cameraLook=event.getCamera().getLookVector();var cameraUp=event.getCamera().getUpVector();
            Vec3 look=new Vec3(cameraLook.x(),cameraLook.y(),cameraLook.z()),up=new Vec3(cameraUp.x(),cameraUp.y(),cameraUp.z());
            for(Marker marker:visible){
                ThermalShellMesh.Budget budget=new ThermalShellMesh.Budget();
                Vec3 center=marker.state.point().add(0,1,0);
                for(var segment:ThermalShellMesh.rings(point(center),point(marker.state.facing()),marker.state.token(),marker.lease.frame())){
                    line(green,matrix,marker.owner,center,vec(segment.start()),vec(segment.end()),camera,look,.005,0xFFFFFF,105,budget);
                    line(core,matrix,marker.owner,center,vec(segment.start()),vec(segment.end()),camera,look,.0015,ThermalShellMesh.CORE,100,budget);
                }
                int hitAge=marker.lease.hitAge();
                var hit=marker.events.hit();
                if(hit!=null&&hitAge<ThermalShellState.HIT_TICKS){
                    Vec3 hitPoint=vec(hit.point());
                    // Both position and direction remain at the actual committed contact as the owner moves.
                    for(var segment:ThermalShellMesh.hit(hit.point(),hit.outward(),hit.salt(),hitAge)){
                        line(green,matrix,marker.owner,hitPoint,vec(segment.start()),vec(segment.end()),camera,look,.007,0xFFFFFF,180-hitAge*35,budget);
                        line(core,matrix,marker.owner,hitPoint,vec(segment.start()),vec(segment.end()),camera,look,.0025,ThermalShellMesh.CORE,235-hitAge*48,budget);
                    }
                    // Eight local flecks at most, drawn directly without allocating any particle entity.
                    for(var grain:ThermalShellMesh.grains(hit.point(),hit.outward(),hit.salt(),hitAge)){
                        Vec3 location=vec(grain.point()),side=look.cross(up).normalize().scale(grain.size());
                        line(core,matrix,marker.owner,hitPoint,location.subtract(side),location.add(side),camera,look,.0015,ThermalShellMesh.CORE,210-hitAge*45,budget);
                    }
                }
                // PULSE depicts the released wave only; it never invents impacts on nearby entities.
                var pulse=marker.events.pulse();
                if(pulse!=null)for(var segment:ThermalShellMesh.pulse(pulse,marker.lease.pulseAge()))
                    line(core,matrix,marker.owner,vec(pulse),vec(segment.start()),vec(segment.end()),camera,look,.003,ThermalShellMesh.GREEN,165-marker.lease.pulseAge()*24,budget);
            }
            greenBuffers.endBatch(greenType);buffers.endBatch(coreType);
        }finally{try{greenBuffers.endBatch();}finally{try{buffers.endBatch();}finally{modelView.popMatrix();RenderSystem.applyModelViewMatrix();}}}
    }
    private static void line(VertexConsumer out,Matrix4f matrix,Entity owner,Vec3 origin,Vec3 a,Vec3 b,Vec3 camera,Vec3 look,
                             double width,int color,int alpha,ThermalShellMesh.Budget budget){
        if(alpha<=0||!visible(owner,origin,a)||!visible(owner,origin,b)||!visible(owner,a,b))return;
        var clipped=ThermalShellMesh.nearClip(point(a.subtract(camera)),point(b.subtract(camera)),point(look),width);
        var ribbon=ThermalShellMesh.ribbon(clipped,point(look),width);if(ribbon==null||!budget.take())return;
        Vec3 start=vec(ribbon.start()),end=vec(ribbon.end()),side=vec(ribbon.side());
        vertex(out,matrix,start.add(side),0,0,color,alpha);vertex(out,matrix,start.subtract(side),0,1,color,alpha);
        vertex(out,matrix,end.subtract(side),1,1,color,alpha);vertex(out,matrix,end.add(side),1,0,color,alpha);
    }
    private static void vertex(VertexConsumer out,Matrix4f matrix,Vec3 p,float u,float v,int color,int alpha){
        out.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor((color>>16)&255,(color>>8)&255,color&255,alpha);
    }
    private static ThermalShellMesh.Point point(Vec3 value){return new ThermalShellMesh.Point(value.x,value.y,value.z);}
    private static Vec3 vec(ThermalShellMesh.Point value){return new Vec3(value.x(),value.y(),value.z());}

    /** Read-only snapshot: points = body, facing, impact. */
    private static double[] dp(net.minecraft.world.phys.Vec3 p) { return p == null ? DecorSnap.NONE : new double[] {p.x, p.y, p.z}; }
    static java.util.List<DecorSnap> decorSnaps() {
        Minecraft mc = Minecraft.getInstance();
        var out = new java.util.ArrayList<DecorSnap>();
        if (mc.level == null) return out;
        for (Marker m : MARKERS.values()) { if (!validOwner(m, mc) || m.state == null) continue; var s = m.state;
            out.add(DecorSnap.of(m.owner == mc.player, 0, s.phase(), s.token(), java.util.List.of(dp(s.point()), dp(s.facing()), dp(s.impact())))); }
        return out;
    }
}

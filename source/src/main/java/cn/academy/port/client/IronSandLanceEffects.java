package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.highpower.IronSandLance;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
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
import java.util.*;

/** NEW131. One bounded controller owns three real server lances and at most60 decorative grains per cast. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class IronSandLanceEffects {
    private static final ResourceLocation SAND=ResourceLocation.fromNamespaceAndPath("academy","textures/effects/ironsand/0.png");
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("academy","textures/port/white.png");
    private static final int MAX_CONTEXTS=64,MAX_VISIBLE_CONTEXTS=8,QUIET_TICKS=10,FADE_TICKS=4;
    private static final IronSandLanceState STATE=new IronSandLanceState();
    private static final Map<UUID,Marker> MARKERS=new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static MultiBufferSource.BufferSource buffers;
    private static final class Marker {
        final Entity owner;
        IronSandLance.Visual state;
        final IronSandLance.LancePose[] impacts=new IronSandLance.LancePose[3];
        final int[] impactAges={4,4,4};
        int quiet,age,fade=-1;
        Marker(Entity owner,IronSandLance.Visual state){this.owner=owner;this.state=state;recordImpacts(state);}
        IronSandLanceState.Identity identity(){return new IronSandLanceState.Identity(state.owner(),state.epoch(),state.input(),state.token());}
        void recordImpacts(IronSandLance.Visual packet){for(var pose:packet.lances())if((pose.status()>=2&&pose.status()!=3)&&impacts[pose.index()]==null){impacts[pose.index()]=pose;impactAges[pose.index()]=0;}}
    }
    private IronSandLanceEffects() {}
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event){IronSandLance.clientReceiver=IronSandLanceEffects::receive;}
    }
    private static IronSandLanceState.Frame frame(IronSandLance.Visual p){
        return new IronSandLanceState.Frame(new IronSandLanceState.Identity(p.owner(),p.epoch(),p.input(),p.token()),p.sequence(),p.phase(),p.orbit(),p.chargeTicks(),point(p.origin()),
                p.lances().stream().map(l->new IronSandLanceState.Pose(l.index(),point(l.tail()),point(l.head()),point(l.previousHead()),l.status(),l.hitEntity())).toList());
    }
    public static void receive(IronSandLance.Visual packet){
        if(packet==null||packet.entity()<0||packet.origin()==null||packet.lances()==null||packet.lances().stream().anyMatch(p->p==null||p.tail()==null||p.head()==null||p.previousHead()==null))return;
        var frame=frame(packet);if(!IronSandLanceState.valid(frame))return;
        Minecraft mc=Minecraft.getInstance();ClientLevel captured=mc.level;Object capturedConnection=mc.getConnection();long capturedSession=AcademyClient.inputSessionEpoch();
        mc.execute(()->{
            if(captured==null||mc.level!=captured||mc.getConnection()!=capturedConnection||!AcademyClient.sameInputSessionEpoch(capturedSession))return;
            synchronize(mc);Entity owner=captured.getEntity(packet.entity());
            if(owner==null||!owner.getUUID().equals(packet.owner())||!owner.isAlive()||owner.isRemoved())return;
            if(owner==mc.player&&(!AcademyClient.singleOwnerEpochMatches(packet.epoch())||!localEligible()))return;
            if(!STATE.accept(frame,owner==mc.player))return;
            Marker old=MARKERS.get(packet.owner());
            if(packet.phase()==3){if(old!=null&&old.state.token()<=packet.token()){old.recordImpacts(packet);fade(old);}return;}
            if(old==null||old.state.token()!=packet.token()){
                if(old==null&&MARKERS.size()>=MAX_CONTEXTS){STATE.retire(frame.id());return;}
                MARKERS.put(packet.owner(),new Marker(owner,packet));
            }else{old.recordImpacts(packet);old.state=packet;old.quiet=0;}
        });
    }
    public static void abortLocal(long input,long epoch){
        Minecraft mc=Minecraft.getInstance();synchronize(mc);STATE.cancel(epoch,input);
        if(mc.player==null)return;Marker marker=MARKERS.get(mc.player.getUUID());
        if(marker!=null&&marker.state.input()==input&&marker.state.epoch()==epoch){STATE.retire(marker.identity());fade(marker);}
    }
    /** Cancellation removes every solid lance/axis/trail at once; only harmless loose charge grains fade. */
    private static void fade(Marker marker){if(marker.fade<0)marker.fade=0;}
    public static void clear(){MARKERS.clear();STATE.clear();}
    private static void synchronize(Minecraft mc){
        if(world==mc.level&&localPlayer==mc.player&&connection==mc.getConnection())return;
        clear();world=mc.level;localPlayer=mc.player;connection=mc.getConnection();
    }
    private static boolean localEligible(){
        Minecraft mc=Minecraft.getInstance();var state=AcademyClient.state;
        return mc.screen==null&&mc.isWindowActive()&&state.activated&&state.overloadFine&&!state.interfering
                &&HighPowerSkills.CATEGORY.equals(state.category)&&state.level>=3&&state.level<=5&&state.learned(HighPowerSkills.IRON_SAND_LANCE)
                &&state.presets.currentContains(HighPowerSkills.IRON_SAND_LANCE)&&SkillCatalog.enabled(HighPowerSkills.CATEGORY,HighPowerSkills.IRON_SAND_LANCE);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();synchronize(mc);if(mc.isPaused())return;
        Iterator<Marker> it=MARKERS.values().iterator();
        while(it.hasNext()){
            Marker m=it.next();m.quiet++;m.age++;for(int i=0;i<3;i++)m.impactAges[i]++;
            if(world==null||!m.owner.isAlive()||m.owner.isRemoved()||world.getEntity(m.owner.getId())!=m.owner){STATE.retire(m.identity());it.remove();continue;}
            if(m.quiet>=QUIET_TICKS||m.age>=320||m.owner==mc.player&&(!localEligible()||!AcademyClient.singleOwnerEpochMatches(m.state.epoch()))){STATE.retire(m.identity());fade(m);}
            if(m.fade>=0&&++m.fade>=FADE_TICKS)it.remove();
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance();synchronize(mc);if(world==null||MARKERS.isEmpty())return;
        List<Marker> visible=MARKERS.values().stream().filter(m->m.owner==mc.player||m.state.origin().distanceToSqr(event.getCamera().getPosition())<=64*64)
                .sorted(Comparator.comparingInt(m->m.owner==mc.player?0:1)).limit(MAX_VISIBLE_CONTEXTS).toList();
        if(buffers==null)buffers=MultiBufferSource.immediate(new ByteBufferBuilder(32768));
        var sandType=ClassicRenderTypes.world(SAND);var axisType=ClassicRenderTypes.world(WHITE);
        var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();
        try {
            modelView.set(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();
            Matrix4f matrix=event.getPoseStack().last().pose();Vec3 camera=event.getCamera().getPosition();
            var cameraLook=event.getCamera().getLookVector();Vec3 look=new Vec3(cameraLook.x(),cameraLook.y(),cameraLook.z());
            var cameraUp=event.getCamera().getUpVector();
            var cameraBasis=IronSandLanceGeometry.cameraBasis(point(look),new IronSandLanceGeometry.Point(cameraUp.x(),cameraUp.y(),cameraUp.z()));
            float partial=mc.isPaused()?0:event.getPartialTick().getGameTimeDeltaPartialTick(false);
            for(Marker marker:visible){
                boolean charging=marker.state.phase()==0;
                boolean hand=charging&&marker.owner==mc.player&&mc.options.getCameraType().isFirstPerson();
                int grainAlpha=marker.fade<0?210:Math.max(0,(int)(170*(1-(marker.fade+partial)/FADE_TICKS)));
                VertexConsumer sand=buffers.getBuffer(sandType);
                for(var lance:marker.state.lances()){
                    if(lance.status()==3)continue;
                    Vec3 tail=lance.tail(),head=lance.head();
                    // Only cosmetic, unpaid hand gathering uses the compact virtual wrist convention.
                    // Every released projectile, orbit, trail and impact stays at accepted server positions.
                    Vec3 source=marker.state.origin();
                    if(hand&&cameraBasis!=null){
                        double handed=mc.player.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT?-1:1;
                        Vec3 side=vec(cameraBasis.side()),up=vec(cameraBasis.up());
                        source=camera.add(look.scale(.65)).add(side.scale(.32*handed)).add(up.scale(-.29));
                        tail=source.add(side.scale((lance.index()-1)*.095)).add(up.scale(.015));
                        head=tail.add(look.scale(.44));
                    }else if(!charging&&lance.status()==1){
                        head=vec(IronSandLanceGeometry.lerp(point(lance.previousHead()),point(lance.head()),partial));
                        tail=lance.tail().add(head.subtract(lance.head()));
                    }
                    if(charging){
                        double charge=Math.min(1,(marker.state.chargeTicks()+partial)/12.0);
                        for(var grain:IronSandLanceGeometry.gather(point(source),point(tail),point(head),lance.index(),charge))
                            grain(sand,matrix,vec(grain.point()).subtract(camera),cameraBasis,grain.size()*(hand?.65:1),grainAlpha);
                        if(marker.fade>=0||charge<.35)continue;
                        mesh(sand,matrix,tail.subtract(camera),head.subtract(camera),look,hand?.46:.78,Math.min(255,(int)((charge-.3)*365)));
                    }else if(marker.fade<0&&(lance.status()==0||lance.status()==1)){
                        mesh(sand,matrix,tail.subtract(camera),head.subtract(camera),look,1,255);
                    }
                }
                buffers.endBatch(sandType);
                VertexConsumer axis=buffers.getBuffer(axisType);
                if(marker.fade<0){
                    for(var lance:marker.state.lances()){
                        if(lance.status()!=0&&lance.status()!=1)continue;
                        Vec3 tail=lance.tail(),head=lance.head();
                        if(hand&&cameraBasis!=null){
                            double handed=mc.player.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT?-1:1;
                            Vec3 side=vec(cameraBasis.side()),up=vec(cameraBasis.up());
                            tail=camera.add(look.scale(.65)).add(side.scale(.32*handed+(lance.index()-1)*.095)).add(up.scale(-.275));head=tail.add(look.scale(.44));
                        }else if(!charging&&lance.status()==1){head=vec(IronSandLanceGeometry.lerp(point(lance.previousHead()),point(lance.head()),partial));tail=lance.tail().add(head.subtract(lance.head()));}
                        if(!charging||marker.state.chargeTicks()>=5){
                            line(axis,matrix,tail.subtract(camera),head.subtract(camera),look,hand?.004:.009,120,204,255,145);
                            line(axis,matrix,tail.subtract(camera),head.subtract(camera),look,hand?.0015:.003,225,247,255,240);
                        }
                        // At most one already-travelled authoritative segment. No48m beam or speculative hit cue.
                        if(!charging&&marker.state.phase()==1&&lance.status()==1)
                            line(axis,matrix,lance.previousHead().subtract(camera),head.subtract(camera),look,.004,125,201,238,75);
                    }
                }
                buffers.endBatch(axisType);
                sand=buffers.getBuffer(sandType);
                for(int i=0;i<3;i++)if(marker.impacts[i]!=null&&marker.impactAges[i]<FADE_TICKS){
                    var pose=marker.impacts[i];
                    for(var grain:IronSandLanceGeometry.impact(point(pose.head()),point(pose.head().subtract(pose.tail())),i,marker.impactAges[i]+partial))
                        grain(sand,matrix,vec(grain.point()).subtract(camera),cameraBasis,grain.size(),Math.max(0,220-marker.impactAges[i]*55),pose.status()==4||pose.status()==6?135:pose.status()==5?255:170,pose.status()==4||pose.status()==6?205:pose.status()==5?255:170,pose.status()==4||pose.status()==6?255:pose.status()==5?255:170);
                }
                buffers.endBatch(sandType);
            }
        } finally {
            try {buffers.endBatch();} finally {modelView.popMatrix();RenderSystem.applyModelViewMatrix();}
        }
    }
    private static void mesh(VertexConsumer out,Matrix4f matrix,Vec3 tail,Vec3 head,Vec3 cameraForward,double size,int alpha){
        for(var face:IronSandLanceGeometry.visibleLance(point(tail),point(head),size,point(cameraForward))){
            vertex(out,matrix,vec(face.a()),0,0,face.shade(),face.shade(),face.shade(),alpha);
            vertex(out,matrix,vec(face.b()),0,1,face.shade(),face.shade(),face.shade(),alpha);
            vertex(out,matrix,vec(face.c()),1,.5f,face.shade(),face.shade(),face.shade(),alpha);
            vertex(out,matrix,vec(face.c()),1,.5f,face.shade(),face.shade(),face.shade(),alpha);
        }
    }
    private static void grain(VertexConsumer out,Matrix4f matrix,Vec3 center,IronSandLanceGeometry.Basis basis,double size,int alpha){
        grain(out,matrix,center,basis,size,alpha,225,225,225);
    }
    private static void grain(VertexConsumer out,Matrix4f matrix,Vec3 center,IronSandLanceGeometry.Basis basis,double size,int alpha,int r,int g,int b){
        if(basis==null||alpha<=0)return;Vec3 side=vec(basis.side()).scale(size),up=vec(basis.up()).scale(size);
        vertex(out,matrix,center.subtract(side).subtract(up),0,0,r,g,b,alpha);vertex(out,matrix,center.add(side).subtract(up),1,0,r,g,b,alpha);
        vertex(out,matrix,center.add(side).add(up),1,1,r,g,b,alpha);vertex(out,matrix,center.subtract(side).add(up),0,1,r,g,b,alpha);
    }
    private static void line(VertexConsumer out,Matrix4f matrix,Vec3 start,Vec3 end,Vec3 look,double width,int r,int g,int b,int alpha){
        var clipped=IronSandLanceGeometry.clip(point(start),point(end),point(look),width);if(clipped==null)return;
        start=new Vec3(clipped.start().x(),clipped.start().y(),clipped.start().z());end=new Vec3(clipped.end().x(),clipped.end().y(),clipped.end().z());width=clipped.halfWidth();
        Vec3 direction=end.subtract(start);if(direction.lengthSqr()<1e-10)return;
        Vec3 normal=direction.cross(start.scale(-1)).normalize();if(normal.lengthSqr()<1e-10)normal=direction.cross(new Vec3(0,1,0)).normalize();if(normal.lengthSqr()<1e-10)normal=new Vec3(1,0,0);
        Vec3 side=normal.scale(width);
        vertex(out,matrix,start.add(side),0,0,r,g,b,alpha);vertex(out,matrix,start.subtract(side),0,1,r,g,b,alpha);
        vertex(out,matrix,end.subtract(side),1,1,r,g,b,alpha);vertex(out,matrix,end.add(side),1,0,r,g,b,alpha);
    }
    private static void vertex(VertexConsumer out,Matrix4f matrix,Vec3 p,float u,float v,int r,int g,int b,int alpha){out.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(r,g,b,alpha);}
    private static IronSandLanceGeometry.Point point(Vec3 p){return new IronSandLanceGeometry.Point(p.x,p.y,p.z);}
    private static Vec3 vec(IronSandLanceGeometry.Point p){return new Vec3(p.x(),p.y(),p.z());}

    /** Read-only snapshot: points = origin then (tail, head) per lance; numbers = orbit, charge ticks, phase, then status per lance. */
    private static double[] dp(net.minecraft.world.phys.Vec3 p) { return p == null ? DecorSnap.NONE : new double[] {p.x, p.y, p.z}; }
    static java.util.List<DecorSnap> decorSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var out = new java.util.ArrayList<DecorSnap>();
        if (world == null) return out;
        for (Marker m : MARKERS.values()) {
            if (m.owner.isRemoved() || m.owner == mc.player && !localEligible()) continue;
            var s = m.state; var points = new java.util.ArrayList<double[]>(); points.add(dp(s.origin()));
            double[] numbers = new double[3 + s.lances().size()]; numbers[0] = s.orbit() ? 1 : 0; numbers[1] = s.chargeTicks(); numbers[2] = s.phase();
            for (int i = 0; i < s.lances().size(); i++) { var lance = s.lances().get(i); points.add(dp(lance.tail())); points.add(dp(lance.head())); numbers[3 + i] = lance.status(); }
            out.add(DecorSnap.of(m.owner == mc.player, m.age, s.phase(), s.token(), points, numbers));
        }
        return out;
    }
}

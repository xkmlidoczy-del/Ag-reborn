package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.highpower.ThunderNet;
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
import java.util.*;

/** NEW141. One finite batched controller per cast; every endpoint is a real accepted server position. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class ThunderNetEffects {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("academy","textures/port/white.png");
    private static final int MAX_CONTEXTS=64,MAX_VISIBLE_CONTEXTS=8;
    private static final ThunderNetState STATE=new ThunderNetState();
    private static final Map<UUID,Marker> MARKERS=new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static long clearGeneration;
    private static MultiBufferSource.BufferSource buffers;
    private static final class Impact {
        final Vec3 origin,point;final int serverAge;int age;
        Impact(Vec3 origin,Vec3 point,int serverAge){this.origin=origin;this.point=point;this.serverAge=serverAge;}
    }
    private static final class Marker {
        final Entity owner;
        final ThunderNetState.Lease lease;
        final Map<UUID,Impact> impacts=new HashMap<>();
        final Map<UUID,Integer> lastHits=new HashMap<>();
        ThunderNet.Visual state;
        Marker(Entity owner,ThunderNet.Visual packet){this.owner=owner;lease=new ThunderNetState.Lease(packet.age(),packet.phase()==ThunderNetState.ENDED);update(packet);}
        ThunderNetState.Identity identity(){return new ThunderNetState.Identity(state.owner(),state.epoch(),state.input(),state.token());}
        void update(ThunderNet.Visual packet){
            state=packet;lease.received(packet.age(),packet.phase()==ThunderNetState.ENDED);
            Set<UUID> retained=new HashSet<>();for(var node:packet.nodes()){
                retained.add(node.uuid());
                if(node.status()==ThunderNetState.HIT&&packet.age()>lastHits.getOrDefault(node.uuid(),-1)){
                    impacts.put(node.uuid(),new Impact(packet.origin(),node.point(),packet.age()));lastHits.put(node.uuid(),packet.age());
                }
            }
            impacts.keySet().retainAll(retained);
        }
    }
    private ThunderNetEffects(){}
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event){ThunderNet.clientReceiver=ThunderNetEffects::receive;}
    }
    private static ThunderNetState.Frame frame(ThunderNet.Visual p){
        return new ThunderNetState.Frame(new ThunderNetState.Identity(p.owner(),p.epoch(),p.input(),p.token()),p.sequence(),p.phase(),p.age(),point(p.origin()),point(p.anchor()),
                p.nodes().stream().map(n->new ThunderNetState.Node(n.entity(),n.uuid(),point(n.point()),n.status())).toList(),p.links(),p.reason());
    }
    public static void receive(ThunderNet.Visual packet){
        if(packet==null||packet.entity()<0||packet.origin()==null||packet.anchor()==null||packet.nodes()==null
                ||packet.nodes().stream().anyMatch(n->n==null||n.point()==null))return;
        var frame=frame(packet);if(!ThunderNetState.valid(frame))return;
        Minecraft mc=Minecraft.getInstance();synchronize(mc);ClientLevel captured=mc.level;Object capturedConnection=mc.getConnection();
        Entity capturedPlayer=mc.player;long capturedSession=AcademyClient.inputSessionEpoch(),generation=clearGeneration;
        mc.execute(()->{
            if(captured==null||mc.level!=captured||mc.player!=capturedPlayer||mc.getConnection()!=capturedConnection
                    ||clearGeneration!=generation||!AcademyClient.sameInputSessionEpoch(capturedSession))return;
            Entity owner=captured.getEntity(packet.entity());
            if(owner==null||!owner.getUUID().equals(packet.owner())||!owner.isAlive()||owner.isRemoved())return;
            if(owner==mc.player&&(!AcademyClient.singleOwnerEpochMatches(packet.epoch())||!localEligible()
                    ||!AcademyClient.thunderNetInputMatches(packet.input(),packet.epoch())))return;
            if(!STATE.accept(frame,owner==mc.player))return;
            Marker old=MARKERS.get(packet.owner());
            if(packet.phase()==ThunderNetState.ENDED&&packet.nodes().isEmpty()){MARKERS.remove(packet.owner());return;}
            if(old==null||old.state.token()!=packet.token()){
                if(old==null&&MARKERS.size()>=MAX_CONTEXTS){STATE.retire(frame.id());return;}
                MARKERS.put(packet.owner(),new Marker(owner,packet));
            }else old.update(packet);
        });
    }
    public static void abortLocal(long input,long epoch){
        Minecraft mc=Minecraft.getInstance();synchronize(mc);STATE.cancel(epoch,input);
        if(mc.player==null)return;Marker marker=MARKERS.get(mc.player.getUUID());
        if(marker!=null&&marker.state.input()==input&&marker.state.epoch()==epoch){STATE.retire(marker.identity());MARKERS.remove(mc.player.getUUID());}
    }
    /** Explicit clears preserve same-session replay fences and reject callbacks already queued. */
    public static void clear(){clearGeneration++;STATE.clear();MARKERS.clear();}
    private static void synchronize(Minecraft mc){
        if(world==mc.level&&localPlayer==mc.player&&connection==mc.getConnection())return;
        clear();STATE.resetSession();world=mc.level;localPlayer=mc.player;connection=mc.getConnection();
    }
    private static boolean localEligible(){
        Minecraft mc=Minecraft.getInstance();var state=AcademyClient.state;
        // A silent shared-CP snapshot and native cooldown cannot cancel an already accepted held cast.
        return mc.screen==null&&mc.isWindowActive()&&state.activated&&state.overloadFine&&!state.interfering
                &&HighPowerSkills.CATEGORY.equals(state.category)&&state.level>=4&&state.level<=5&&state.learned(HighPowerSkills.THUNDER_NET)
                &&state.presets.currentContains(HighPowerSkills.THUNDER_NET)&&SkillCatalog.enabled(HighPowerSkills.CATEGORY,HighPowerSkills.THUNDER_NET);
    }
    private static boolean validOwner(Marker m,Minecraft mc){
        return world!=null&&m.owner.isAlive()&&!m.owner.isRemoved()&&world.getEntity(m.owner.getId())==m.owner
                &&(m.owner!=mc.player||localEligible()&&AcademyClient.singleOwnerEpochMatches(m.state.epoch())
                &&AcademyClient.thunderNetInputMatches(m.state.input(),m.state.epoch()));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();synchronize(mc);boolean paused=mc.isPaused();
        Iterator<Marker> it=MARKERS.values().iterator();
        while(it.hasNext()){
            Marker m=it.next();
            if(!m.lease.tick(paused,validOwner(m,mc))){STATE.retire(m.identity());it.remove();continue;}
            if(!paused){m.impacts.values().forEach(hit->hit.age++);m.impacts.values().removeIf(hit->hit.age>=ThunderNetState.HIT_TICKS);}
        }
    }
    private static boolean nodeVisible(Marker m,ThunderNet.Node node){
        Entity target=world.getEntity(node.entity());
        boolean living=target!=null&&target.getUUID().equals(node.uuid())&&target.isAlive()&&!target.isRemoved();
        // A kill may remove its entity immediately; only a confirmed four-tick hit may outlive that entity.
        return (living||m.impacts.containsKey(node.uuid()))&&visible(m.owner,m.state.origin(),node.point());
    }
    /** Read-only client clipping can hide stale visuals sooner; it never makes a server hit valid. */
    private static boolean visible(Entity owner,Vec3 a,Vec3 b){
        if(world==null||a==null||b==null||!ThunderNetGeometry.validEndpoints(point(a),point(b)))return false;
        int steps=Math.max(1,(int)Math.ceil(a.distanceTo(b)/8));
        for(int i=0;i<=steps;i++)if(!world.hasChunkAt(BlockPos.containing(a.lerp(b,(double)i/steps))))return false;
        return world.clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner)).getType()==HitResult.Type.MISS;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance();synchronize(mc);if(world==null||MARKERS.isEmpty())return;
        Vec3 camera=event.getCamera().getPosition();
        List<Marker> visible=MARKERS.values().stream().filter(m->validOwner(m,mc)&&(m.owner==mc.player||m.state.origin().distanceToSqr(camera)<=96*96))
                .sorted(Comparator.comparingInt(m->m.owner==mc.player?0:1)).limit(MAX_VISIBLE_CONTEXTS).toList();
        if(visible.isEmpty())return;if(buffers==null)buffers=MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        var type=ClassicRenderTypes.world(WHITE);var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();
        try{
            modelView.set(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();VertexConsumer out=buffers.getBuffer(type);
            Matrix4f matrix=event.getPoseStack().last().pose();var cameraLook=event.getCamera().getLookVector();var cameraUp=event.getCamera().getUpVector();
            Vec3 look=new Vec3(cameraLook.x(),cameraLook.y(),cameraLook.z()),up=new Vec3(cameraUp.x(),cameraUp.y(),cameraUp.z());
            for(Marker m:visible){
                List<ThunderNet.Node> nodes=m.state.nodes();boolean[] shown=new boolean[nodes.size()];
                for(int i=0;i<nodes.size();i++){
                    var node=nodes.get(i);shown[i]=nodeVisible(m,node);if(!shown[i])continue;
                    Impact hit=m.impacts.get(node.uuid());
                    if(hit!=null&&visible(m.owner,hit.origin,hit.point)){
                        // Damage paths are caster-to-target only, at authoritative accepted source and hit points.
                        drawArc(out,matrix,m.owner,hit.origin,hit.point,camera,look,up,m.state.token()+i,m.lease.age(),false,hit.age);
                        for(var grain:ThunderNetGeometry.hit(point(hit.point),point(hit.point.subtract(hit.origin)),m.state.token()+i,hit.age))
                            drawGrain(out,matrix,m.owner,vec(grain.point()),camera,look,up,grain.size(),210-hit.age*45);
                    }else if(m.state.phase()==ThunderNetState.PENDING||m.state.phase()==ThunderNetState.HOLDING){
                        int alpha=m.state.phase()==ThunderNetState.PENDING?85:115;
                        for(var line:ThunderNetGeometry.lock(point(node.point()),point(look)))
                            worldLine(out,matrix,m.owner,vec(line.start()),vec(line.end()),camera,look,up,.0025,110,180,222,alpha);
                    }else if(node.status()==ThunderNetState.REFUSED){
                        // Muted crossed marks are refusal, never bright hit sparks or a damage beam.
                        var basis=ThunderNetGeometry.basis(point(look));if(basis!=null)for(int sign:new int[]{-1,1}){
                            Vec3 delta=vec(basis.side().plus(basis.up().scale(sign))).scale(.09);
                            worldLine(out,matrix,m.owner,node.point().subtract(delta),node.point().add(delta),camera,look,up,.002,145,155,168,80);
                        }
                    }
                }
                boolean holding=m.state.phase()==ThunderNetState.HOLDING;
                boolean tapHit=m.state.phase()==ThunderNetState.ENDED&&m.state.reason()==0&&!m.impacts.isEmpty();
                if(!(holding&&ThunderNetGeometry.restraintVisible(m.lease.age())||tapHit))continue;
                // Inter-target links are decorative only. Both server LOS mask and current local LOS must agree.
                for(int i=0;i<nodes.size();i++)for(int j=i+1;j<nodes.size();j++){
                    if(!shown[i]||!shown[j]||(m.state.links()&ThunderNetGeometry.linkBit(nodes.size(),i,j))==0)continue;
                    Vec3 a=nodes.get(i).point(),b=nodes.get(j).point();if(!visible(m.owner,a,b))continue;
                    drawArc(out,matrix,m.owner,a,b,camera,look,up,m.state.token()+i*7L+j,m.lease.age()/6,true,holding?1:Math.max(0,m.lease.terminalAge()));
                }
            }
            buffers.endBatch(type);
        }finally{try{buffers.endBatch();}finally{modelView.popMatrix();RenderSystem.applyModelViewMatrix();}}
    }
    private static void drawArc(VertexConsumer out,Matrix4f matrix,Entity owner,Vec3 a,Vec3 b,Vec3 camera,Vec3 look,Vec3 up,long token,int frame,boolean fine,int age){
        int alpha=Math.max(0,(fine?100:205)-age*(fine?15:40));
        for(var segment:ThunderNetGeometry.arc(point(a),point(b),token,frame,fine)){
            Vec3 from=vec(segment.start()),to=vec(segment.end());if(!visible(owner,from,to))continue;
            // One terrain check per actual jagged segment; halo/core share that accepted segment.
            cameraLine(out,matrix,from,to,camera,look,up,fine?.006:.013,86,159,245,alpha/2);
            cameraLine(out,matrix,from,to,camera,look,up,fine?.0018:.0035,218,243,255,alpha);
        }
    }
    private static void drawGrain(VertexConsumer out,Matrix4f matrix,Entity owner,Vec3 center,Vec3 camera,Vec3 look,Vec3 up,double size,int alpha){
        // Eight flecks per confirmed target, at most48 per six-target controller (<96); no spawned particles.
        Vec3 side=look.cross(up).normalize().scale(size);
        worldLine(out,matrix,owner,center.subtract(side),center.add(side),camera,look,up,size*.4,225,245,255,alpha);
    }
    private static void worldLine(VertexConsumer out,Matrix4f matrix,Entity owner,Vec3 a,Vec3 b,Vec3 camera,Vec3 look,Vec3 up,double width,int r,int g,int blue,int alpha){
        if(alpha<=0||!visible(owner,a,b))return;
        cameraLine(out,matrix,a,b,camera,look,up,width,r,g,blue,alpha);
    }
    private static void cameraLine(VertexConsumer out,Matrix4f matrix,Vec3 a,Vec3 b,Vec3 camera,Vec3 look,Vec3 up,double width,int r,int g,int blue,int alpha){
        if(alpha<=0)return;
        var clipped=ThunderNetGeometry.nearClip(point(a.subtract(camera)),point(b.subtract(camera)),point(look),width);
        for(var segment:ThunderNetGeometry.clearCenter(clipped,point(look),point(up))){
            var ribbon=ThunderNetGeometry.ribbon(segment,point(look),width);if(ribbon==null)continue;
            Vec3 start=vec(ribbon.start()),end=vec(ribbon.end()),side=vec(ribbon.side());
            vertex(out,matrix,start.add(side),0,0,r,g,blue,alpha);vertex(out,matrix,start.subtract(side),0,1,r,g,blue,alpha);
            vertex(out,matrix,end.subtract(side),1,1,r,g,blue,alpha);vertex(out,matrix,end.add(side),1,0,r,g,blue,alpha);
        }
    }
    private static void vertex(VertexConsumer out,Matrix4f matrix,Vec3 p,float u,float v,int r,int g,int b,int alpha){out.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(r,g,b,alpha);}
    private static ThunderNetGeometry.Point point(Vec3 p){return new ThunderNetGeometry.Point(p.x,p.y,p.z);}
    private static Vec3 vec(ThunderNetGeometry.Point p){return new Vec3(p.x(),p.y(),p.z());}

    /** Read-only snapshot: points = origin, anchor, then every node; numbers = phase, age, links, then node status. */
    private static double[] dp(net.minecraft.world.phys.Vec3 p) { return p == null ? DecorSnap.NONE : new double[] {p.x, p.y, p.z}; }
    static java.util.List<DecorSnap> decorSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var out = new java.util.ArrayList<DecorSnap>();
        if (world == null) return out;
        for (Marker m : MARKERS.values()) {
            if (!validOwner(m, mc)) continue;
            var s = m.state; var points = new java.util.ArrayList<double[]>(java.util.List.of(dp(s.origin()), dp(s.anchor())));
            double[] numbers = new double[3 + s.nodes().size()]; numbers[0] = s.phase(); numbers[1] = s.age(); numbers[2] = s.links();
            for (int i = 0; i < s.nodes().size(); i++) { points.add(dp(s.nodes().get(i).point())); numbers[3 + i] = s.nodes().get(i).status(); }
            out.add(DecorSnap.of(m.owner == mc.player, s.age(), s.phase(), s.token(), points, numbers));
        }
        return out;
    }
}

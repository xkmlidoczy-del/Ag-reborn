package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.highpower.MagneticItemPull;
import cn.academy.port.highpower.core.MagneticItemPullRules;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import java.util.*;

/** NEW133: narrow pale-blue lines follow only server-approved, currently visible native ItemEntities. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class MagneticItemPullEffects {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("academy","textures/port/white.png");
    private static final int MAX_CONTEXTS=64,QUIET_TICKS=12;
    private static final Map<UUID,Marker> MARKERS=new HashMap<>();
    private static final Map<UUID,Long> TERMINALS=new LinkedHashMap<>();
    private static final LinkedHashSet<Input> CANCELLED=new LinkedHashSet<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static MultiBufferSource.BufferSource buffers;
    private record Input(long epoch,long input) {}
    private static final class Marker {
        final Entity owner;
        MagneticItemPull.Visual state;
        int age,quiet;
        Marker(Entity owner,MagneticItemPull.Visual state){this.owner=owner;this.state=state;}
    }
    private MagneticItemPullEffects() {}
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event){MagneticItemPull.clientReceiver=MagneticItemPullEffects::receive;}
    }
    public static void receive(MagneticItemPull.Visual packet){
        if(packet==null||packet.entity()<0||packet.owner()==null||packet.input()<=0||packet.token()<=0||packet.epoch()<=0||packet.sequence()<=0
                ||packet.phase()<0||packet.phase()>1||!finite(packet.hand())||packet.targets()==null||packet.targets().size()>16
                ||packet.targets().stream().anyMatch(Objects::isNull)||new HashSet<>(packet.targets()).size()!=packet.targets().size())return;
        Minecraft mc=Minecraft.getInstance();ClientLevel captured=mc.level;Object capturedConnection=mc.getConnection();
        mc.execute(()->{
            if(captured==null||mc.level!=captured||mc.getConnection()!=capturedConnection)return;
            synchronize(mc);Entity owner=captured.getEntity(packet.entity());
            if(owner==null||!owner.getUUID().equals(packet.owner()))return;
            if(owner==mc.player&&(!AcademyClient.singleOwnerEpochMatches(packet.epoch())||CANCELLED.contains(new Input(packet.epoch(),packet.input()))))return;
            Marker old=MARKERS.get(packet.owner());
            if(packet.token()<=TERMINALS.getOrDefault(packet.owner(),0L)||old!=null&&(packet.token()<old.state.token()
                    ||packet.token()==old.state.token()&&packet.sequence()<=old.state.sequence()))return;
            if(packet.phase()==1){terminal(packet.owner(),packet.token());MARKERS.remove(packet.owner());return;}
            if(packet.targets().isEmpty()||!owner.isAlive()||owner.isRemoved()||owner==mc.player&&!localEligible())return;
            if(old==null||old.state.token()!=packet.token()){
                if(old==null&&MARKERS.size()>=MAX_CONTEXTS)return;
                MARKERS.put(packet.owner(),new Marker(owner,packet));
                CastPoseClient.acceptedRelease(MagneticItemPull.ID,packet.entity(),packet.owner(),packet.input(),packet.epoch(),packet.token(),false);
            }else{old.state=packet;old.quiet=0;}
        });
    }
    public static void abortLocal(long input,long epoch){
        Minecraft mc=Minecraft.getInstance();synchronize(mc);
        if(input<=0||epoch<=0)return;
        CANCELLED.add(new Input(epoch,input));while(CANCELLED.size()>256)CANCELLED.remove(CANCELLED.iterator().next());
        if(mc.player==null)return;Marker marker=MARKERS.get(mc.player.getUUID());
        if(marker!=null&&marker.state.input()==input&&marker.state.epoch()==epoch){terminal(mc.player.getUUID(),marker.state.token());MARKERS.remove(mc.player.getUUID());}
    }
    public static void clear(){MARKERS.clear();TERMINALS.clear();CANCELLED.clear();}
    private static void synchronize(Minecraft mc){
        if(world==mc.level&&localPlayer==mc.player&&connection==mc.getConnection())return;
        clear();world=mc.level;localPlayer=mc.player;connection=mc.getConnection();
    }
    private static void terminal(UUID owner,long token){TERMINALS.merge(owner,token,Math::max);if(TERMINALS.size()>256)TERMINALS.remove(TERMINALS.keySet().iterator().next());}
    private static boolean localEligible(){
        Minecraft mc=Minecraft.getInstance();var state=AcademyClient.state;
        return mc.screen==null&&mc.isWindowActive()&&state.activated&&state.overloadFine&&!state.interfering
                &&HighPowerSkills.CATEGORY.equals(state.category)&&state.level>=3&&state.level<=5&&state.learned(MagneticItemPull.ID)
                &&state.presets.currentContains(MagneticItemPull.ID)&&SkillCatalog.enabled(HighPowerSkills.CATEGORY,MagneticItemPull.ID);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();synchronize(mc);if(mc.isPaused())return;
        Iterator<Marker> it=MARKERS.values().iterator();
        while(it.hasNext()){
            Marker m=it.next();m.age++;m.quiet++;
            if(world==null||!m.owner.isAlive()||m.owner.isRemoved()||world.getEntity(m.owner.getId())!=m.owner||m.quiet>=QUIET_TICKS
                    ||m.age>=MagneticItemPullRules.MAX_TICKS+QUIET_TICKS
                    ||m.owner==mc.player&&(!localEligible()||!AcademyClient.singleOwnerEpochMatches(m.state.epoch()))){
                terminal(m.owner.getUUID(),m.state.token());it.remove();
            }
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance();synchronize(mc);if(world==null||MARKERS.isEmpty())return;
        Set<UUID> wanted=new HashSet<>();
        for(Marker marker:MARKERS.values())wanted.addAll(marker.state.targets());
        Map<UUID,ItemEntity> visibleItems=new HashMap<>();int examined=0;
        for(Entity entity:world.entitiesForRendering()){
            if(++examined>2048||visibleItems.size()==wanted.size())break;
            if(entity instanceof ItemEntity item&&item.isAlive()&&!item.isRemoved()&&wanted.contains(item.getUUID()))visibleItems.put(item.getUUID(),item);
        }
        if(buffers==null)buffers=MultiBufferSource.immediate(new ByteBufferBuilder(32768));
        var type=ClassicRenderTypes.world(WHITE);VertexConsumer out=buffers.getBuffer(type);
        Matrix4f matrix=event.getPoseStack().last().pose();Vec3 camera=event.getCamera().getPosition();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for(Marker marker:MARKERS.values()){
            if(marker.owner==mc.player&&!localEligible())continue;
            Vec3 realHand=marker.owner.getEyePosition(partial).add(marker.owner.getViewVector(partial).scale(.25)).add(0,-.6,0);
            Vec3 start=realHand.subtract(camera),clipLook=null;
            if(marker.owner==mc.player&&mc.options.getCameraType().isFirstPerson()&&event.getCamera().getEntity()==marker.owner){
                var look=event.getCamera().getLookVector();var up=event.getCamera().getUpVector();clipLook=new Vec3(look.x(),look.y(),look.z());
                var wrist=PolarityTetherGeometry.wrist(point(clipLook),new PolarityTetherGeometry.Point(up.x(),up.y(),up.z()),mc.player.getMainArm()==HumanoidArm.LEFT);
                if(wrist==null)continue;start=vec(wrist);
            }
            for(UUID id:marker.state.targets()){
                ItemEntity item=visibleItems.get(id);if(item==null||item.getItem().isEmpty())continue;
                Vec3 position=item.getPosition(partial).add(0,item.getBbHeight()*.5,0);
                if(realHand.distanceToSqr(position)>Math.pow(cn.academy.port.highpower.core.MagneticItemPullRules.RANGE + .5, 2))continue;
                line(out,matrix,start,position.subtract(camera),clipLook,.006,130);
            }
        }
        buffers.endBatch(type);
    }
    private static void line(VertexConsumer out,Matrix4f matrix,Vec3 start,Vec3 end,Vec3 clipLook,double width,int alpha){
        if(clipLook!=null){var clipped=PolarityTetherGeometry.clip(point(start),point(end),point(clipLook),width);if(clipped==null)return;start=vec(clipped.start());end=vec(clipped.end());width=clipped.halfWidth();}
        Vec3 direction=end.subtract(start);if(!finite(direction)||direction.lengthSqr()<1e-6)return;
        Vec3 normal=direction.cross(start.scale(-1)).normalize();if(normal.lengthSqr()<1e-10)normal=direction.cross(new Vec3(0,1,0)).normalize();
        if(normal.lengthSqr()<1e-10)normal=new Vec3(1,0,0);Vec3 side=normal.scale(width);
        vertex(out,matrix,start.add(side),0,0,alpha);vertex(out,matrix,start.subtract(side),0,1,alpha);
        vertex(out,matrix,end.subtract(side),1,1,alpha);vertex(out,matrix,end.add(side),1,0,alpha);
    }
    private static void vertex(VertexConsumer out,Matrix4f matrix,Vec3 p,float u,float v,int alpha){out.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(157,216,255,alpha);}
    private static PolarityTetherGeometry.Point point(Vec3 v){return new PolarityTetherGeometry.Point(v.x,v.y,v.z);}
    private static Vec3 vec(PolarityTetherGeometry.Point p){return new Vec3(p.x(),p.y(),p.z());}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}

    /** Read-only snapshot: points = hand then every pulled item; numbers = phase. */
    private static double[] dp(net.minecraft.world.phys.Vec3 p) { return p == null ? DecorSnap.NONE : new double[] {p.x, p.y, p.z}; }
    static java.util.List<DecorSnap> decorSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var out = new java.util.ArrayList<DecorSnap>();
        if (world == null) return out;
        for (Marker m : MARKERS.values()) {
            if (m.owner.isRemoved() || m.owner == mc.player && !localEligible()) continue;
            var s = m.state; var points = new java.util.ArrayList<double[]>(); points.add(dp(s.hand()));
            int examined = 0;
            for (Entity e : world.entitiesForRendering()) {
                if (++examined > 2048 || points.size() > s.targets().size()) break;
                if (s.targets().contains(e.getUUID())) points.add(dp(e.position()));
            }
            out.add(DecorSnap.of(m.owner == mc.player, m.age, s.phase(), s.token(), points, s.phase()));
        }
        return out;
    }
}

/* AcademyCraft1.0.7 VecAccelContextC/VecDeviationContextC/Parabola/Wave client adaptation.
 * Copyright Lambda Innovation, GPLv3; LambdaLib math MIT. See NOTICE. */
package cn.academy.port.client;

import cn.academy.port.skill.VecAccelSession;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;
import java.util.*;

/** Cosmetic prediction only. Velocity is applied solely from the authenticated authoritative perform packet. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class ClassicVectorStarterEffects {
    private static final boolean VISUAL_QA=Boolean.getBoolean("academy.visual.qa");
    private static final org.slf4j.Logger QA_LOG=com.mojang.logging.LogUtils.getLogger();
    private static int qaMotionTicks;
    private static final RandomSource RANDOM=RandomSource.create();
    private static final ClassicVectorStarterTimeline.Tokens TOKENS=new ClassicVectorStarterTimeline.Tokens();
    private static final Map<String,Long> INPUTS=new HashMap<>();
    private static final Map<String,Long> EVENTS=new LinkedHashMap<>();
    private static final List<Wave> WAVES=new ArrayList<>();
    private static final List<Ripple> RIPPLES=new ArrayList<>();
    private static final List<FollowingSound> SOUNDS=new ArrayList<>();
    private static final ResourceLocation GLOW_CIRCLE=ResourceLocation.fromNamespaceAndPath("academy","textures/effects/glow_circle.png");
    private static ClientLevel level;
    private static Entity localPlayer;
    private static Object connection;
    private static Hold accel,deviation;
    private static long nextInput,lastWall=Util.getMillis(),clock;
    private static float lastFrame;
    private static MultiBufferSource.BufferSource buffers;
    private ClassicVectorStarterEffects() {}
    public static boolean owns(String id){return id.equals("vec_accel")||id.equals("vec_deviation");}
    public static long startLocal(String id){
        var mc=Minecraft.getInstance();synchronize(mc);if(!owns(id)||mc.player==null||mc.level==null)return 0;
        long input=++nextInput;INPUTS.put(id,input);
        if(id.equals("vec_accel"))accel=new Hold(mc.player,0,input);
        else if(deviation!=null){deviation=null;RIPPLES.clear();}else {deviation=new Hold(mc.player,0,input);lastFrame=clock/1000F;}
        return input;
    }
    public static void endLocal(String id){if(id.equals("vec_accel"))accel=null;else if(id.equals("vec_deviation")){deviation=null;RIPPLES.clear();}}
    /** Accepted exact local END fences subsequent server envelopes before cosmetic cleanup. */
    public static void terminateContext(long input,long token){var mc=Minecraft.getInstance();if(mc.player==null||input<=0||token<=0)return;TOKENS.end("vec_deviation",mc.player.getId(),token);if(INPUTS.getOrDefault("vec_deviation",0L)==input)endLocal("vec_deviation");}
    /** contextActivate does not terminate on key release. */
    public static void releaseLocal(String id){if(id.equals("vec_accel"))accel=null;}
    public static long localNonce(String id){return INPUTS.getOrDefault(id,0L);}
    public static boolean active(String id){return id.equals("vec_accel")?accel!=null:id.equals("vec_deviation")&&deviation!=null;}
    public static boolean anyActive(){return accel!=null||deviation!=null;}
    public static float consumptionHint(){return accel==null?0:accel.consumption;}
    public static void receive(CompoundTag data){
        if(data==null)return;var tag=data.copy();var mc=Minecraft.getInstance();ClientLevel target=mc.level;Entity player=mc.player;Object conn=mc.getConnection();
        mc.execute(()->{if(target==null||target!=mc.level||player!=mc.player||conn!=mc.getConnection())return;synchronize(mc);advance(mc);
            String kind=tag.getString("kind"),id=kind.startsWith("vec_accel")?"vec_accel":"vec_deviation";
            int casterId=tag.getInt("entity");long token=tag.getLong("token"),input=tag.getLong("input");if(token<=0)return;
            Entity caster=target.getEntity(casterId);boolean local=caster==mc.player;
            if(VISUAL_QA&&id.equals("vec_accel"))QA_LOG.info("VecAccel QA client receive kind={} token={} input={} caster={} local={} localInput={} position={}",kind,token,input,casterId,local,INPUTS.getOrDefault(id,0L),caster==null?null:caster.position());
            if(kind.endsWith("_start")){
                if(caster==null||!TOKENS.start(id,casterId,token))return;
                if(local){if(input>0&&input!=INPUTS.getOrDefault(id,0L))return;Hold h=id.equals("vec_accel")?accel:deviation;
                    if(input>0&&h==null)return;if(id.equals("vec_deviation")&&AcademyClient.contextStartCancelled(id,input,token))return;if(h==null)h=new Hold(caster,token,input);else h.token=token;
                    if(id.equals("vec_accel")){accel=h;AcademyClient.acceptedSingleStart(id,input,token);}else {deviation=h;lastFrame=clock/1000F;AcademyClient.acceptedContextStart(id,input,token,true);}}
                return;
            }
            if(kind.equals("vec_accel_end")||kind.equals("vec_accel_perform")||kind.equals("vec_deviation_end")){
                boolean accepted=TOKENS.end(id,casterId,token);
                if(VISUAL_QA&&id.equals("vec_accel"))QA_LOG.info("VecAccel QA client end token={} accepted={}",token,accepted);
                if(!accepted)return;Hold h=id.equals("vec_accel")?accel:deviation;
                boolean deferred=local&&id.equals("vec_deviation")&&AcademyClient.acceptedContextEnd(id,input,token);
                if(local&&id.equals("vec_accel"))AcademyClient.acceptedSingleEnd(id,input,token);
                if(!deferred&&local&&h!=null&&(h.token==token||h.token==0&&h.input==input))endLocal(id);
                if(kind.equals("vec_accel_perform")&&caster!=null){
                    Vec3 v=new Vec3(tag.getDouble("vx"),tag.getDouble("vy"),tag.getDouble("vz"));
                    if(VISUAL_QA)QA_LOG.info("VecAccel QA client perform velocity={} finite={} lengthSquared={}",v,finite(v),v.lengthSqr());
                    if(!finite(v)||v.lengthSqr()>6.26)return;
                    if(local){caster.stopRiding();caster.setDeltaMovement(v);caster.fallDistance=0;qaMotionTicks=VISUAL_QA?4:0;}
                    if(SOUNDS.size()<128){var sound=new FollowingSound(caster);SOUNDS.add(sound);mc.getSoundManager().play(sound);}}
                return;
            }
            if(local&&id.equals("vec_deviation")&&AcademyClient.contextStartCancelled(id,input,token))return;
            if(!TOKENS.live(id,casterId,token)||caster==null)return;
            String eventKey=id+":"+casterId+":"+token;long seq=tag.getLong("sequence");if(seq<=0||seq<=EVENTS.getOrDefault(eventKey,0L))return;EVENTS.put(eventKey,seq);while(EVENTS.size()>512){var iter=EVENTS.keySet().iterator();iter.next();iter.remove();}
            Vec3 at=new Vec3(tag.getDouble("x"),tag.getDouble("y"),tag.getDouble("z"));if(!finite(at))return;
            if(kind.equals("vec_deviation_stop")){
                Entity entity=target.getEntity(tag.getInt("target"));if(entity!=null)entity.setDeltaMovement(Vec3.ZERO);
                // Preserve source client-NBT mark guard. Legacy dimension+ID transport never synced the server mark.
                if(entity!=null&&entity.getPersistentData().getBoolean("ac_vm_deviated")&&tag.getBoolean("wave")&&WAVES.size()<256){float yaw=tag.getFloat("yaw"),pitch=tag.getFloat("pitch");
                    if(Float.isFinite(yaw)&&Float.isFinite(pitch)){WAVES.add(new Wave(at,yaw,pitch));play(at);}}
            }else if(kind.equals("vec_deviation_sound"))play(at);
        });
    }
    private static void play(Vec3 at){var mc=Minecraft.getInstance();if(mc.level!=null)mc.level.playLocalSound(at.x,at.y,at.z,sound(ClassicVectorStarterTimeline.DEVIATION_SOUND),SoundSource.MASTER,.5F,1,false);}
    private static SoundEvent sound(String name){return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy",name));}
    public static void clear(){var mc=Minecraft.getInstance();for(var s:SOUNDS){s.close();mc.getSoundManager().stop(s);}SOUNDS.clear();accel=deviation=null;WAVES.clear();RIPPLES.clear();TOKENS.clear();INPUTS.clear();EVENTS.clear();clock=0;lastWall=Util.getMillis();lastFrame=0;}
    private static void synchronize(Minecraft mc){if(level==mc.level&&localPlayer==mc.player&&connection==mc.getConnection())return;clear();level=mc.level;localPlayer=mc.player;connection=mc.getConnection();}
    private static void advance(Minecraft mc){long wall=Util.getMillis();if(mc.level!=null&&!mc.isPaused())clock+=Math.max(0,wall-lastWall);lastWall=wall;}
    private static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();synchronize(mc);advance(mc);if(mc.level==null||mc.player==null)return;
        if(qaMotionTicks>0){QA_LOG.info("VecAccel QA client motion remaining={} position={} velocity={} onGround={}",qaMotionTicks,mc.player.position(),mc.player.getDeltaMovement(),mc.player.onGround());qaMotionTicks--;}
        if(!mc.player.isAlive()||!AcademyClient.state.category.equals("vecmanip")||!AcademyClient.state.overloadFine){accel=deviation=null;RIPPLES.clear();}
        else if(!AcademyClient.state.activated||AcademyClient.state.interfering)accel=null;
        SOUNDS.removeIf(s->!mc.getSoundManager().isActive(s));if(mc.isPaused())return;
        if(accel!=null){accel.ticks++;Vec3 feet=mc.player.position();accel.canPerform=accel.ignoreGround||mc.level.clip(new ClipContext(feet,feet.add(0,-2,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player)).getType()==HitResult.Type.BLOCK;}
        WAVES.removeIf(w->++w.ticks>=15);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;var mc=Minecraft.getInstance();synchronize(mc);if(mc.level==null||mc.player==null)return;
        if(buffers==null)buffers=MultiBufferSource.immediate(new ByteBufferBuilder(65536));var poses=event.getPoseStack();Vec3 camera=event.getCamera().getPosition();
        poses.pushPose();try{poses.translate(-camera.x,-camera.y,-camera.z);
            if(accel!=null&&mc.options.getCameraType().isFirstPerson()){
                float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false),yaw=Mth.lerp(partial,mc.player.yRotO,mc.player.getYRot()),pitch=Mth.lerp(partial,mc.player.xRotO,mc.player.getXRot());
                var points=ClassicVectorStarterTimeline.parabola(yaw,pitch,accel.ticks);var out=buffers.getBuffer(ClassicVectorStarterRenderTypes.PATH);
                poses.pushPose();poses.translate(camera.x,camera.y,camera.z);Matrix4f matrix=poses.last().pose();
                for(int i=1;i<points.size();i++){var prev=points.get(i-1);var cur=points.get(i);float a=ClassicVectorStarterTimeline.parabolaAlpha(i);float g=accel.canPerform?1:.2F;
                    vertex(out,matrix,prev.x(),prev.y()+.02,prev.z(),0,0,1,g,g,a);vertex(out,matrix,prev.x(),prev.y()-.02,prev.z(),0,1,1,g,g,a);
                    vertex(out,matrix,cur.x(),cur.y()-.02,cur.z(),1,1,1,g,g,a);vertex(out,matrix,cur.x(),cur.y()+.02,cur.z(),1,0,1,g,g,a);}
                buffers.endBatch(ClassicVectorStarterRenderTypes.PATH);poses.popPose();
            }
            if(!WAVES.isEmpty()){var out=buffers.getBuffer(ClassicVectorStarterRenderTypes.WAVE);
                for(var w:WAVES){double alpha=ClassicVectorStarterTimeline.waveAlpha(w.ticks,w.life,w.timeOffset);if(alpha<=0)continue;double size=ClassicVectorStarterTimeline.waveSize(w.ticks,w.size);
                    poses.pushPose();poses.translate(w.at.x,w.at.y,w.at.z);poses.mulPose(Axis.YP.rotationDegrees(-w.yaw));poses.mulPose(Axis.XP.rotationDegrees(w.pitch));poses.translate(0,0,ClassicVectorStarterTimeline.waveDepth(w.ticks,w.offset));
                    Matrix4f matrix=poses.last().pose();vertex(out,matrix,-size/2,-size/2,0,0,0,1,1,1,(float)alpha);vertex(out,matrix,-size/2,size/2,0,0,1,1,1,1,(float)alpha);
                    vertex(out,matrix,size/2,size/2,0,1,1,1,1,1,(float)alpha);vertex(out,matrix,size/2,-size/2,0,1,0,1,1,1,(float)alpha);poses.popPose();}
                buffers.endBatch(ClassicVectorStarterRenderTypes.WAVE);}
        }finally{poses.popPose();}}
    @SubscribeEvent public static void overlay(RenderGuiLayerEvent.Pre event){if(!event.getName().equals(ResourceLocation.withDefaultNamespace("crosshair")))return;var mc=Minecraft.getInstance();synchronize(mc);advance(mc);if(deviation==null||mc.level==null)return;
        float now=clock/1000F,dt=Math.max(0,now-lastFrame);lastFrame=now;var graphics=event.getGuiGraphics();float width=graphics.guiWidth(),height=graphics.guiHeight();
        RIPPLES.removeIf(r->{r.age+=dt;return r.age>=r.life;});
        // Original frame Bernoulli, not a rate-corrected Poisson generator. rangei(2,3) always yields2.
        if(RANDOM.nextFloat()<dt*1.4F&&RIPPLES.size()<256)RIPPLES.add(new Ripple((.8F+RANDOM.nextFloat()*(1.2F-.8F))*100,2+RANDOM.nextInt(1),RANDOM.nextFloat()*width,RANDOM.nextFloat()*height));
        var canvas=new ClassicHudCanvas(graphics);canvas.begin();try{for(var r:RIPPLES){double a=.2F*ClassicVectorStarterTimeline.rippleAlpha(r.age,r.life),size=ClassicVectorStarterTimeline.rippleSize(r.size,r.age)/2.0;
            double x=ClassicVectorStarterTimeline.rippleX(r.x,width),y=ClassicVectorStarterTimeline.rippleY(r.y,height);
            // Fragment shader multiplies RGB AND alpha, and source clip projection vertically reverses the UV.
            canvas.rect(GLOW_CIRCLE,x-size/2,y-size/2,size,size,0,1,1,0,new ClassicHudTimeline.Rgba(a,a,a,a),null,null);}}
        finally{canvas.end();}}
    private static void vertex(VertexConsumer out,Matrix4f matrix,double x,double y,double z,float u,float v,float r,float g,float b,float a){out.addVertex(matrix,(float)x,(float)y,(float)z).setUv(u,v).setColor(r,g,b,Mth.clamp(a,0,1));}
    private static final class Hold{final Entity caster;final long input;final float consumption;final boolean ignoreGround;long token;int ticks;boolean canPerform=true;Hold(Entity caster,long token,long input){this.caster=caster;this.token=token;this.input=input;consumption=VecAccelSession.cp(AcademyClient.state.exp("vec_accel"));ignoreGround=(float)AcademyClient.state.exp("vec_accel")>.5F;}}
    private static final class Wave{final Vec3 at;final float yaw,pitch;final int life,timeOffset;final double offset,size;int ticks;Wave(Vec3 at,float yaw,float pitch){this.at=at;this.yaw=yaw;this.pitch=pitch;life=8+RANDOM.nextInt(4);offset=-.3+RANDOM.nextDouble()*.6;size=.6*(.8+RANDOM.nextDouble()*(1.2-.8));timeOffset=-1+RANDOM.nextInt(2);}}
    private static final class Ripple{final float size,life,x,y;float age;Ripple(float size,float life,float x,float y){this.size=size;this.life=life;this.x=x;this.y=y;}}
    private static final class FollowingSound extends AbstractTickableSoundInstance{final Entity caster;final ClientLevel world;FollowingSound(Entity caster){super(sound(ClassicVectorStarterTimeline.ACCEL_SOUND),SoundSource.MASTER,RandomSource.create());this.caster=caster;world=Minecraft.getInstance().level;volume=.35F;tick();}@Override public void tick(){if(world!=Minecraft.getInstance().level||caster.isRemoved()){stop();return;}x=caster.getX();y=caster.getY();z=caster.getZ();}void close(){stop();}}
}

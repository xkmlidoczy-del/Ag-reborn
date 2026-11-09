/* AcademyCraft1.0.7 MagMovement/EntityArc/EntitySurroundArc + loop sounds. See NOTICE. */
package cn.academy.port.client;

import cn.academy.port.skill.MagneticBlockEntity;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import java.util.*;

/** Source-pattern arc ribbons and positional looping audio; packets never move/damage blocks here. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class ClassicMagneticEffects {
    private static final ResourceLocation ARC_TEXTURE=ResourceLocation.fromNamespaceAndPath("academy","textures/effects/arc/line_segment.png");
    private static final Map<Integer,Context> MOVEMENT=new HashMap<>(),MANIPULATION=new HashMap<>();
    private static final Map<Integer,Surround> BLOCKS=new HashMap<>();
    private static final ClassicChargingTimeline.Tokens MOVE_TOKENS=new ClassicChargingTimeline.Tokens(),MANIP_TOKENS=new ClassicChargingTimeline.Tokens();
    private static final List<Pattern> BEAMS=beamPatterns(),THIN=surroundPatterns();
    private static ClientLevel activeLevel;private static Entity activePlayer;private static Object activeConnection;private static long activeInputEpoch;
    private static final Set<Terminal> MOVE_ENDS=new LinkedHashSet<>(),MANIP_ENDS=new LinkedHashSet<>();
    private static MultiBufferSource.BufferSource buffers;
    private ClassicMagneticEffects() {}
    public static void receive(CompoundTag data){
        if(data==null)return;var tag=data.copy();
        if(!tag.contains("kind",Tag.TAG_STRING)||!tag.contains("entity",Tag.TAG_INT)||!tag.hasUUID("entity_uuid")||!tag.contains("input",Tag.TAG_LONG)||!tag.contains("token",Tag.TAG_LONG)||!tag.contains("owner_epoch",Tag.TAG_LONG)||!tag.contains("cp_hint",Tag.TAG_DOUBLE))return;
        String kind=tag.getString("kind");boolean movement=kind.equals("mag_movement_start")||kind.equals("mag_movement_update")||kind.equals("mag_movement_end");
        if(!movement&&!kind.equals("mag_manip_start")&&!kind.equals("mag_manip_end")&&!kind.equals("mag_manip_perform"))return;
        long input=tag.getLong("input"),token=tag.getLong("token"),ownerEpoch=tag.getLong("owner_epoch");double hint=tag.getDouble("cp_hint");
        if(input<0||token<=0||ownerEpoch<0||input>0&&ownerEpoch<=0||!Double.isFinite(hint)||hint<0)return;
        if(movement&&(!tag.contains("tick",Tag.TAG_LONG)||tag.getLong("tick")< -1||!tag.contains("tx",Tag.TAG_DOUBLE)||!tag.contains("ty",Tag.TAG_DOUBLE)||!tag.contains("tz",Tag.TAG_DOUBLE)||!finite(point(tag))))return;
        var mc=Minecraft.getInstance();var level=mc.level;var player=mc.player;var connection=mc.getConnection();long epoch=AcademyClient.inputSessionEpoch();
        mc.execute(()->{if(level==null||mc.level!=level||mc.player!=player||mc.getConnection()!=connection||!AcademyClient.sameInputSessionEpoch(epoch))return;synchronize(mc);
            var map=movement?MOVEMENT:MANIPULATION;var tokens=movement?MOVE_TOKENS:MANIP_TOKENS;String skill=movement?"mag_movement":"mag_manip";
            int id=tag.getInt("entity");UUID uuid=tag.getUUID("entity_uuid");Entity caster=level.getEntity(id);
            if(caster==null||!caster.getUUID().equals(uuid))return;boolean local=caster==player;
            if(local&&input>0&&(!AcademyClient.singleOwnerEpochMatches(ownerEpoch)||AcademyClient.singleTokenConflicts(skill,input,token)))return;
            var old=map.get(id);
            if(kind.endsWith("_end")||kind.equals("mag_manip_perform")){
                if(old!=null&&old.caster==caster&&old.token==token&&(old.input!=input||old.ownerEpoch!=ownerEpoch))return;
                if(!rememberTerminal(movement?MOVE_ENDS:MANIP_ENDS,new Terminal(uuid,input,token,ownerEpoch)))return;
                tokens.rememberEnd(id,token);
                if(local&&AcademyClient.singleTerminalAllowed(skill,input,token))AcademyClient.acceptedSingleEnd(skill,input,token);
                if(old!=null&&old.caster==caster&&old.input==input&&old.token==token&&old.ownerEpoch==ownerEpoch){map.remove(id);old.close(mc);}
                // A paid throw is an independent authoritative result, even after a newer hold starts.
                if(kind.equals("mag_manip_perform"))level.playLocalSound(caster.getX(),caster.getY(),caster.getZ(),sound("em.mag_manip"),SoundSource.MASTER,1,1,false);
                return;
            }
            if(kind.equals("mag_movement_update")){
                if(old!=null&&old.caster==caster&&old.input==input&&old.ownerEpoch==ownerEpoch&&ClassicMagneticTimeline.matchingUpdate(token,old.token,tag.getLong("tick"),old.lastUpdate)){Vec3 point=point(tag);if(finite(point)){old.previousPoint=old.point;old.point=point;old.lastUpdate=tag.getLong("tick");}}return;
            }
            Vec3 point=movement?point(tag):caster.position();
            // Input0 cosmetics remain trusted legacy behavior; positive ownership takes priority.
            if(!caster.isAlive()||caster.isRemoved()||!finite(point)||old!=null&&old.token>=token||old==null&&map.size()>=128||local&&(input>0?!AcademyClient.singleStartAllowed(skill,input,token):!AcademyClient.legacySingleStartAllowed(skill))||!tokens.acceptStart(id,token))return;
            if(old!=null)old.close(mc);var context=new Context(caster,level,input,token,ownerEpoch,epoch,movement,point,hint);context.lastUpdate=tag.getLong("tick");map.put(id,context);if(local)AcademyClient.acceptedSingleStart(skill,input,token);mc.getSoundManager().play(context.loop);
        });
    }
    private record Terminal(UUID caster,long input,long token,long ownerEpoch) {}
    private static boolean rememberTerminal(Set<Terminal> history,Terminal terminal){if(!history.add(terminal))return false;if(history.size()>1024)history.remove(history.iterator().next());return true;}
    public static double consumptionHint(){var mc=Minecraft.getInstance();if(mc.player==null)return 0;double sum=0;var a=MOVEMENT.get(mc.player.getId());var b=MANIPULATION.get(mc.player.getId());if(a!=null)sum+=a.hint;if(b!=null)sum+=b.hint;return sum;}
    public static void abortLocal(String skill){var mc=Minecraft.getInstance();if(mc.player==null)return;var map=skill.equals("mag_movement")?MOVEMENT:MANIPULATION;var c=map.remove(mc.player.getId());if(c!=null){(c.movement?MOVE_TOKENS:MANIP_TOKENS).rememberEnd(c.caster.getId(),c.token);c.close(mc);}}
    public static void abortLocal(String skill,long input){if(input<0||!skill.equals("mag_movement")&&!skill.equals("mag_manip"))return;var mc=Minecraft.getInstance();if(mc.player==null)return;var map=skill.equals("mag_movement")?MOVEMENT:MANIPULATION;var c=map.get(mc.player.getId());if(c!=null&&c.caster==mc.player&&c.input==input){map.remove(mc.player.getId());(c.movement?MOVE_TOKENS:MANIP_TOKENS).rememberEnd(c.caster.getId(),c.token);c.close(mc);}}
    public static void abortLocal(String skill,long input,long ownerEpoch){if(input<0||ownerEpoch<0||!skill.equals("mag_movement")&&!skill.equals("mag_manip"))return;var mc=Minecraft.getInstance();if(mc.player==null)return;var map=skill.equals("mag_movement")?MOVEMENT:MANIPULATION;var c=map.get(mc.player.getId());if(c!=null&&c.caster==mc.player&&c.input==input&&c.ownerEpoch==ownerEpoch){map.remove(mc.player.getId());(c.movement?MOVE_TOKENS:MANIP_TOKENS).rememberEnd(c.caster.getId(),c.token);c.close(mc);}}
    public static void clear(){clear(Minecraft.getInstance());}
    private static void clear(Minecraft mc){for(var c:MOVEMENT.values())c.close(mc);for(var c:MANIPULATION.values())c.close(mc);MOVEMENT.clear();MANIPULATION.clear();BLOCKS.clear();MOVE_TOKENS.clear();MANIP_TOKENS.clear();MOVE_ENDS.clear();MANIP_ENDS.clear();}
    private static void synchronize(Minecraft mc){long epoch=AcademyClient.inputSessionEpoch();if(activeLevel==mc.level&&activePlayer==mc.player&&activeConnection==mc.getConnection()&&activeInputEpoch==epoch)return;clear(mc);activeLevel=mc.level;activePlayer=mc.player;activeConnection=mc.getConnection();activeInputEpoch=epoch;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();synchronize(mc);if(mc.level==null)return;
        for(var map:List.of(MOVEMENT,MANIPULATION)){var it=map.entrySet().iterator();while(it.hasNext()){var c=it.next().getValue();if(!c.valid(mc)){(c.movement?MOVE_TOKENS:MANIP_TOKENS).rememberEnd(c.caster.getId(),c.token);c.close(mc);it.remove();}else c.tick();}}
        BLOCKS.values().removeIf(s->s.entity.isRemoved()||s.entity.level()!=mc.level||mc.level.getEntity(s.entity.getId())!=s.entity);
        for(Entity e:mc.level.entitiesForRendering())if(e instanceof MagneticBlockEntity block&&!block.isRemoved()){var s=BLOCKS.get(block.getId());if(s==null&&BLOCKS.size()<256){s=new Surround(block);BLOCKS.put(block.getId(),s);}if(s!=null)s.tick();}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;var mc=Minecraft.getInstance();synchronize(mc);if(mc.level==null||MOVEMENT.isEmpty()&&BLOCKS.isEmpty())return;
        if(buffers==null)buffers=MultiBufferSource.immediate(new ByteBufferBuilder(65536));RenderType type=ClassicRenderTypes.world(ARC_TEXTURE);PoseStack poses=event.getPoseStack();Vec3 camera=event.getCamera().getPosition();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);poses.pushPose();
        try{poses.translate(-camera.x,-camera.y,-camera.z);Matrix4f matrix=poses.last().pose();VertexConsumer out=buffers.getBuffer(type);
            for(var c:MOVEMENT.values())if(c.valid(mc)&&c.shown){Vec3 origin=position(c.caster,partial).add(0,c.caster.getEyeHeight(),0);Vec3 delta=c.previousPoint.lerp(c.point,partial).subtract(origin);double length=delta.length();if(length>1e-6&&Double.isFinite(length)){Vec3 d=delta.scale(1/length),v=d.cross(new Vec3(0,1,0));v=v.lengthSqr()<1e-12?new Vec3(-1,0,0):v.normalize();Vec3 u=v.cross(d).normalize();var offset=ClassicMagneticTimeline.viewOffset(c.caster==mc.player&&mc.options.getCameraType().isFirstPerson());origin=origin.add(d.scale(offset.x())).add(u.scale(offset.y())).add(v.scale(offset.z()));paths(out,matrix,origin,d,u,v,1,length,BEAMS.get(c.pattern).paths);}}
            for(var s:BLOCKS.values()){Vec3 center=position(s.entity,partial);for(var arc:s.arcs)if(!arc.dead&&arc.shown){var pattern=THIN.get(arc.pattern);Vec3 origin=center.add(arc.position).subtract(arc.direction.scale(pattern.length*ClassicMagneticTimeline.SURROUND_SCALE/2));paths(out,matrix,origin,arc.direction,arc.u,arc.v,ClassicMagneticTimeline.SURROUND_SCALE,Double.POSITIVE_INFINITY,pattern.paths);}}
            buffers.endBatch(type);
        }finally{poses.popPose();}
    }
    private static final class Context {
        final Entity caster;final ClientLevel level;final long input,token,ownerEpoch,epoch;final boolean movement;final Random random=new Random();final Loop loop;final double hint;
        Vec3 point,previousPoint;long lastUpdate;int pattern;boolean shown=true,closed;
        Context(Entity e,ClientLevel l,long input,long t,long ownerEpoch,long epoch,boolean m,Vec3 p,double hint){caster=e;level=l;this.input=input;token=t;this.ownerEpoch=ownerEpoch;this.epoch=epoch;movement=m;point=previousPoint=p;this.hint=Double.isFinite(hint)?Math.max(0,hint):0;loop=new Loop(this);}
        boolean valid(Minecraft mc){return !closed&&AcademyClient.sameInputSessionEpoch(epoch)&&mc.level==level&&caster.isAlive()&&!caster.isRemoved()&&level.getEntity(caster.getId())==caster;}
        void tick(){if(movement){if(random.nextDouble()<ClassicMagneticTimeline.BEAM_TEX_WIGGLE)pattern=random.nextInt(BEAMS.size());shown=ClassicMagneticTimeline.beamShown(shown,random.nextDouble());}}
        void close(Minecraft mc){closed=true;loop.finish();mc.getSoundManager().stop(loop);}
    }
    private static final class Loop extends AbstractTickableSoundInstance {
        final Context context;
        Loop(Context c){super(sound(c.movement?"em.move_loop":"em.lf_loop"),SoundSource.MASTER,RandomSource.create());context=c;looping=true;delay=0;volume=ClassicMagneticTimeline.LOOP_VOLUME;update();}
        @Override public void tick(){if(!context.valid(Minecraft.getInstance())||(context.movement?MOVEMENT:MANIPULATION).get(context.caster.getId())!=context){stop();return;}update();}
        void update(){x=context.caster.getX();y=context.caster.getY();z=context.caster.getZ();}void finish(){stop();}
    }
    private static final class Surround {
        final MagneticBlockEntity entity;final Random random=new Random();final List<SubArc> arcs=new ArrayList<>();
        Surround(MagneticBlockEntity e){entity=e;generate();}
        void generate(){for(int i=0;i<ClassicMagneticTimeline.SURROUND_COUNT;i++)arcs.add(new SubArc(cubePoint(random,entity.getBbWidth()*ClassicMagneticTimeline.SURROUND_SIZE,entity.getBbHeight()*ClassicMagneticTimeline.SURROUND_SIZE),random));}
        void tick(){if(arcs.isEmpty())generate();var it=arcs.iterator();while(it.hasNext()){var a=it.next();if(a.dead)it.remove();else a.tick(random);}}
    }
    private static final class SubArc {
        final Vec3 position,direction,u,v;int pattern,age;boolean shown,dead;
        SubArc(Vec3 p,Random random){position=p;pattern=random.nextInt(THIN.size());float x=(float)(random.nextDouble()*Math.PI*2),y=(float)(random.nextDouble()*Math.PI*2),z=(float)(random.nextDouble()*Math.PI*2);direction=new Vec3(1,0,0).xRot(-x).yRot(y).zRot(-z);u=new Vec3(0,1,0).xRot(-x).yRot(y).zRot(-z);v=new Vec3(0,0,1).xRot(-x).yRot(y).zRot(-z);}
        void tick(Random random){if(random.nextDouble()<.5*ClassicMagneticTimeline.SUBARC_FRAME_RATE)pattern=random.nextInt(THIN.size());age=ClassicMagneticTimeline.subArcAge(age,random.nextDouble());if(age==ClassicMagneticTimeline.SUBARC_LIFE)dead=true;shown=ClassicMagneticTimeline.subArcShown(shown,random.nextDouble());}
    }
    private record Pattern(double length,List<List<ClassicArcGeometry.Segment>> paths) {}
    private static List<Pattern> beamPatterns(){var random=new Random();var result=new ArrayList<Pattern>();for(int i=0;i<ClassicMagneticTimeline.BEAM_TEMPLATES;i++)result.add(new Pattern(ClassicMagneticTimeline.BEAM_LENGTH,ClassicArcGeometry.generate(random,ClassicMagneticTimeline.BEAM_LENGTH,ClassicMagneticTimeline.BEAM_PASSES,ClassicMagneticTimeline.BEAM_WIDTH,ClassicMagneticTimeline.BEAM_OFFSET,ClassicMagneticTimeline.BEAM_BRANCH,ClassicMagneticTimeline.BEAM_SHRINK)));return result;}
    private static List<Pattern> surroundPatterns(){var random=new Random();var result=new ArrayList<Pattern>();for(int i=0;i<ClassicMagneticTimeline.SURROUND_TEMPLATES;i++){double length=1.5+random.nextDouble()*.5;result.add(new Pattern(length,ClassicArcGeometry.generate(random,length,ClassicMagneticTimeline.SURROUND_PASSES,ClassicMagneticTimeline.SURROUND_WIDTH,ClassicMagneticTimeline.SURROUND_OFFSET,ClassicMagneticTimeline.SURROUND_BRANCH,ClassicMagneticTimeline.SURROUND_SHRINK)));}return result;}
    private static Vec3 cubePoint(Random r,double width,double height){int face=r.nextInt(6);if(face<2)return new Vec3((r.nextDouble()-.5)*width,face==0?0:height,(r.nextDouble()-.5)*width);if(face<4){double y=r.nextDouble()*height,x=(r.nextDouble()-.5)*width;return new Vec3(x,y,face==2?-width/2:width/2);}double y=r.nextDouble()*height,z=(r.nextDouble()-.5)*width;return new Vec3(face==4?-width/2:width/2,y,z);}
    private static void paths(VertexConsumer out,Matrix4f matrix,Vec3 origin,Vec3 d,Vec3 u,Vec3 v,double scale,double length,List<List<ClassicArcGeometry.Segment>> paths){
        for(var path:paths){Vec3 previous=null;for(var segment:path){if(segment.start().position().x>length)break;Vec3 start=arcPoint(origin,d,u,v,segment.start().position().scale(scale)),end=arcPoint(origin,d,u,v,segment.end().position().scale(scale));Vec3 up=end.subtract(start).cross(v).normalize();if(up.lengthSqr()<1e-12)up=u;if(previous==null)previous=up;int alpha=(int)Math.round(255*segment.alpha());vertex(out,matrix,start.add(previous.scale(segment.start().width()*scale)),0,0,alpha);vertex(out,matrix,start.subtract(previous.scale(segment.start().width()*scale)),0,1,alpha);vertex(out,matrix,end.subtract(up.scale(segment.end().width()*scale)),1,1,alpha);vertex(out,matrix,end.add(up.scale(segment.end().width()*scale)),1,0,alpha);previous=up;}}
    }
    private static Vec3 arcPoint(Vec3 origin,Vec3 d,Vec3 u,Vec3 v,Vec3 p){return origin.add(d.scale(p.x)).add(u.scale(p.y)).add(v.scale(p.z));}
    private static void vertex(VertexConsumer out,Matrix4f matrix,Vec3 p,float u,float v,int alpha){out.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(255,255,255,Mth.clamp(alpha,0,255));}
    private static Vec3 position(Entity e,float partial){return new Vec3(Mth.lerp(partial,e.xo,e.getX()),Mth.lerp(partial,e.yo,e.getY()),Mth.lerp(partial,e.zo,e.getZ()));}
    private static Vec3 point(CompoundTag tag){return new Vec3(tag.getDouble("tx"),tag.getDouble("ty"),tag.getDouble("tz"));}
    private static boolean finite(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    private static SoundEvent sound(String name){return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy",name));}
}

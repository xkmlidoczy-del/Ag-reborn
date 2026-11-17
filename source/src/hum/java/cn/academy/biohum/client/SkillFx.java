package cn.academy.biohum.client;

import cn.academy.biohum.BioHum;
import cn.academy.biohum.AbilityBolt;
import cn.academy.biohum.CastMotion;
import cn.academy.biohum.HumNpc;
import cn.academy.biohum.Payloads;
import cn.academy.biohum.PresentationConfig;
import cn.academy.biohum.Skills;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/** World geometry comes from the server. Surface glow, trails and particles are client side. */
public final class SkillFx {
    private record Illusion(Payloads.SkillCue event,long begin) {}
    private static Illusion illusion;
    private record Flash(Payloads.SkillCue event,long seen,CastMotion.Style style,int life) {}
    private static final ArrayList<Flash> ACTIVE=new ArrayList<>();
    private static final Set<Payloads.SkillCue> ACTIVE_EVENTS=new HashSet<>();
    private static final Map<UUID,Long> STAR_EYES=new HashMap<>();
    private record Material(RenderType body,RenderType emission) {}
    private static final RenderType FAMILIAR_FLAME=SkillRenderType.material(
            ResourceLocation.fromNamespaceAndPath(BioHum.ID,"textures/entity/familiar_fire.png"),false);
    private static final Map<String,Material> MATERIALS=new HashMap<>();
    private static final Map<String,Integer> DRAWN=new HashMap<>(), RECEIVED=new HashMap<>();
    private static final Map<String,Float> BODY_ROLL=new HashMap<>();
    private static final Map<String,Double> RELEASE_HEIGHT=new HashMap<>();
    private static final Map<String,EnumMap<Payloads.Cue,Integer>> CUE_COUNTS=new HashMap<>();
    private static final Map<String,Double> BEAM_LENGTH=new HashMap<>(), FIELD_RADIUS=new HashMap<>();
    private static final Set<String> METER_IMPACTS=Set.of("rail","melt","scan","prism","mirror");
    private static final Set<String> SEAL_FIELDS=Set.of("spell","gold","star","flame","rose","sanctuary","third","prism");
    private static final Set<String> METER_CHARGE=Set.of("scan","melt","mirror","arc","prism");
    private static final Set<String> SEAL_CHARGE=Set.of("spell","flame","gold","rose","star","prism");
    private static final Vec3 X=new Vec3(1,0,0), Y=new Vec3(0,1,0), Z=new Vec3(0,0,1);
    private static final boolean QA=Boolean.getBoolean("biohum.qa.client");
    private static long submittedQuads;
    private static ClientLevel seenWorld;

    public static Map<String,Integer> drawn() { return Map.copyOf(DRAWN); }
    public static Map<String,Integer> received() { return Map.copyOf(RECEIVED); }
    public static double highestRelease(String id){return RELEASE_HEIGHT.getOrDefault(id,0d);}
    public static float greatestBodyRoll(String id){return BODY_ROLL.getOrDefault(id,0f);}
    public static int received(String id,Payloads.Cue cue){
        var counts=CUE_COUNTS.get(id);
        return counts==null?0:counts.getOrDefault(cue,0);
    }
    public static double longestBeam(String id){return BEAM_LENGTH.getOrDefault(id,0d);}
    public static double greatestField(String id){return FIELD_RADIUS.getOrDefault(id,0d);}

    static void recordPose(HumNpc npc,float roll) {
        if(QA)BODY_ROLL.merge(npc.technique(),Math.abs(roll)*Mth.RAD_TO_DEG,Math::max);
    }

    public static ResourceLocation texture(String id) {
        return ResourceLocation.fromNamespaceAndPath(BioHum.ID,"textures/skill/"+id+".png");
    }

    static RenderType material(String id,boolean emission) {
        var material=MATERIALS.computeIfAbsent(id,key->new Material(
                SkillRenderType.material(texture(key),false),SkillRenderType.material(texture(key),true)));
        return emission?material.emission:material.body;
    }

    public static void receive(Payloads.SkillCue event) {
        var level=Minecraft.getInstance().level;
        var skill=Skills.get(event.skill());
        if(level==null||skill==null)return;
        switchWorld(level);
        if(event.cue()==Payloads.Cue.ILLUSION){illusion=new Illusion(event,level.getGameTime());return;}
        if(event.cue()==Payloads.Cue.WINDUP||event.cue()==Payloads.Cue.RECOVER||event.cue()==Payloads.Cue.WHIFF)return;
        if(!ACTIVE_EVENTS.add(event))return;
        if(event.cue()==Payloads.Cue.CANCEL) {
            ACTIVE.removeIf(f->{
                if(!f.event.cast().equals(event.cast())||f.event.cue()!=Payloads.Cue.RELEASE)return false;
                ACTIVE_EVENTS.remove(f.event);
                return true;
            });
        }
        var style=CastMotion.style(skill);
        long now=level.getGameTime();
        if(event.cue()==Payloads.Cue.IMPACT&&event.subject()!=null
                &&(event.skill().equals("mental_stop")||event.skill().equals("mental_assist")
                    ||event.skill().equals("mental_retreat")))STAR_EYES.put(event.subject(),now+20);
        ACTIVE.add(new Flash(event,now-Math.clamp(now-event.time(),0,2),style,life(event,style)));
        if(QA){
            RECEIVED.merge(event.skill(),1,Integer::sum);
            CUE_COUNTS.computeIfAbsent(event.skill(),id->new EnumMap<>(Payloads.Cue.class))
                    .merge(event.cue(),1,Integer::sum);
            if(event.cue()==Payloads.Cue.BEAM)BEAM_LENGTH.merge(event.skill(),event.from().distanceTo(event.to()),Math::max);
            if(event.cue()==Payloads.Cue.FIELD)FIELD_RADIUS.merge(event.skill(),(double)event.size(),Math::max);
            if(event.cue()==Payloads.Cue.RELEASE){
                for(var entity:level.entitiesForRendering()){
                    if(entity instanceof HumNpc npc&&npc.getUUID().equals(event.actor())){
                        RELEASE_HEIGHT.merge(event.skill(),(event.from().y-npc.getY())/npc.phaseScale(),Math::max);
                        break;
                    }
                }
            }
        }
    }

    static void illusion(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event){
        var level=Minecraft.getInstance().level;
        if(level==null||illusion==null||level!=seenWorld){illusion=null;return;}
        long age=level.getGameTime()-illusion.begin;
        if(age<0||age>=8){illusion=null;return;}
        var gui=event.getGuiGraphics();
        int w=gui.guiWidth(),h=gui.guiHeight();
        int alpha=(int)(112*(1-age/8f));
        int tint=(alpha<<24)|0xb2a4e1;
        for(int i=0;i<5;i++){
            int y=h/2-16+8*i;
            int offset=Math.floorMod(illusion.event.actor().hashCode()+i*17+(int)age*3,28);
            gui.fill(w/2-38+offset,y,w/2+28+offset,y+1,tint);
        }
        int lineAlpha=(int)(226*(1-age/8f));
        tint=(lineAlpha<<24)|0xf8eeff;
        if(illusion.event.skill().equals("aleister_spiritual_rapier")){
            for(int i=-24;i<24;i++)gui.fill(w/2+i,h/2-i/2,w/2+i+1,h/2-i/2+2,tint);
        } else {
            gui.fill(w/2-6,h/2-1,w/2+7,h/2+2,tint);
            gui.fill(w/2-1,h/2-6,w/2+2,h/2+7,tint);
        }
    }

    private static void switchWorld(ClientLevel level) {
        if(level!=seenWorld){
            seenWorld=level;
            ACTIVE.clear();
            ACTIVE_EVENTS.clear();
            STAR_EYES.clear();
        }
    }

    static void eyes(HumNpc npc,ModelPart head,PoseStack stack,MultiBufferSource buffers,float partial){
        Long until=STAR_EYES.get(npc.getUUID());
        if(until==null||npc.level()!=seenWorld)return;
        float remain=until-npc.level().getGameTime()-partial;
        if(remain<=0){STAR_EYES.remove(npc.getUUID());return;}
        stack.pushPose();
        head.translateAndRotate(stack);
        SignatureFx.stars(stack.last(),buffers,Math.min(1,remain/7));
        stack.popPose();
    }

    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();
        switchWorld(mc.level);
        if(mc.level==null||ACTIVE.isEmpty())return;
        var buffers=mc.renderBuffers().bufferSource();
        var pose=event.getPoseStack().last();
        Vec3 camera=event.getCamera().getPosition();
        var rotation=event.getCamera().rotation();
        var across=new Vector3f(1,0,0).rotate(rotation);
        var up=new Vector3f(0,1,0).rotate(rotation);
        Vec3 right=new Vec3(across.x,across.y,across.z), vertical=new Vec3(up.x,up.y,up.z);
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long tick=mc.level.getGameTime();
        if(!STAR_EYES.isEmpty())STAR_EYES.values().removeIf(until->until<=tick);
        ACTIVE.removeIf(flash->{
            if(tick-flash.seen<flash.life)return false;
            ACTIVE_EVENTS.remove(flash.event);
            return true;
        });
        if(ACTIVE.isEmpty())return;
        var seen=QA?new HashSet<String>():null;
        var quality=PresentationConfig.quality();
        for(var flash:ACTIVE){
            var e=flash.event;
            var style=flash.style;
            float t=Mth.clamp((tick-flash.seen+partial)/flash.life,0,1);
            float a=(1-t)*(1-t*.39f);
            Vec3 from=e.from().subtract(camera), to=e.to().subtract(camera);
            double size=e.size()*style.scale();
            var segment=to.subtract(from);
            double u=segment.lengthSqr()<1e-5?0:Math.clamp(-from.dot(segment)/segment.lengthSqr(),0,1);
            boolean detailed=from.add(segment.scale(u)).lengthSqr()<quality.detailDistance*quality.detailDistance;
            if(!detailed&&quality==PresentationConfig.Quality.LOW&&e.cue()==Payloads.Cue.TERRAIN)continue;
            long before=submittedQuads;
            boolean sky=OthinusSky.drawCue(e,buffers,pose,from,to,right,vertical,t,a);
            boolean authored=sky||SignatureFx.draw(e,buffers,pose,from,to,right,vertical,style,t,a,
                    detailed?quality.extraLayers:0);
            if(!authored){
              var v=buffers.getBuffer(material(e.skill(),false));
              switch(e.cue()){
                case BEAM,REFLECT -> beam(v,pose,from,to,right,vertical,style,e.skill(),size,t,a,
                        e.cue()==Payloads.Cue.REFLECT);
                case MOVE -> movement(v,pose,from,to,right,vertical,style,e.skill(),size,t,a);
                case FIELD -> field(v,pose,to,right,vertical,style,e.skill(),e.size(),t,a);
                case IMPACT -> {
                    if(e.skill().endsWith("_flinch")&&e.skill().startsWith("close_")){
                        silk(v,pose,to,right,vertical,style,t,a,3);
                    }else impact(v,pose,to,right,vertical,style,size,t,a,e.skill().hashCode());
                }
                case WALL,PARRY -> {
                    int count=e.cue()==Payloads.Cue.PARRY?7:5;
                    if(e.skill().startsWith("close_kanzaki")||e.skill().equals("yuisen"))count+=4;
                    silk(v,pose,to,right,vertical,style,t,a,count);
                }
                case WINDUP,RECOVER,WHIFF,ILLUSION -> {}
                case CANCEL -> {
                    disc(v,pose,from,right,vertical,size*(.47+t*.86),-.7+t*1.2,2,a*.67f);
                    if(style.pattern().equals("plasma")){
                        for(int i=0;i<3;i++){
                            var side=Y.yRot(i*1.8f).zRot(.55f+i*.35f);
                            arc(v,pose,from,normal(side),side,size*(.42+t*1.34),t*4+i*2,
                                    1.1-t*.6,.09,style.accent(),a*.56f,12);
                        }
                    }
                }
                case RELEASE -> release(v,pose,from,to,right,vertical,style,size,t,a);
                case TERRAIN -> terrain(v,pose,from,to,right,vertical,style,t,a,e.skill().hashCode());
              }
              if(!style.quiet()&&detailed&&quality.extraLayers>0){
                  profile(buffers.getBuffer(material(e.skill(),false)),pose,e,from,to,right,vertical,style,size,t,a);
                  emission(buffers,pose,e,from,to,right,vertical,style,size,t,a);
              }
            }
            if(seen!=null && submittedQuads>before)seen.add(e.skill());
        }
        if(seen!=null)for(String id:seen)DRAWN.merge(id,1,Integer::sum);
        buffers.endBatch();
    }

    private static void silk(VertexConsumer v, PoseStack.Pose pose,Vec3 at,Vec3 side,Vec3 up,
                             CastMotion.Style style,float t,float opacity,int count){
        for(int i=0;i<count;i++){
            double theta=i*2.399, length=(.19+.32*t)*(1+(i%3)*.34);
            Vec3 ray=side.scale(Math.cos(theta)).add(up.scale(Math.sin(theta)*.66));
            ribbon(v,pose,at.add(ray.scale(t*.14)),at.add(ray.scale(length)),.018*(1-t),1,
                    i%3==0?style.accent():style.core(),opacity*.69f);
        }
    }

    private static void profile(VertexConsumer v,PoseStack.Pose pose,Payloads.SkillCue e,Vec3 from,Vec3 to,
                                Vec3 right,Vec3 up,CastMotion.Style style,double size,float t,float a){
        String id=e.skill(),pattern=style.pattern();
        if(style.technical()){
            if(e.cue()==Payloads.Cue.BEAM||e.cue()==Payloads.Cue.REFLECT){
                Vec3 delta=to.subtract(from);
                double span=delta.length();
                if(span<1.25)return;
                Vec3 axis=delta.normalize();
                Vec3 x=normal(axis),y=axis.cross(x).normalize();
                if(pattern.equals("rail")||pattern.equals("melt")||pattern.equals("plasma")||id.equals("vector_headwind")){
                    int sections=pattern.equals("rail")?5:3;
                    for(int i=0;i<sections;i++){
                        double travel=(i+.55+t*2)*Math.min(span/sections,3.5);
                        if(travel>=span)break;
                        Vec3 anchor=from.add(axis.scale(travel));
                        double r=.27+size*.56+t*.36;
                        for(int j=0;j<4;j++)arc(v,pose,anchor,x,y,r,j*Math.PI*.5+.18+i*.15,
                                .64,.039,pattern.equals("rail")?0x79bcff:style.accent(),a*(.57f-.05f*i),4);
                    }
                }
            }else if(e.cue()==Payloads.Cue.FIELD
                    ||e.cue()==Payloads.Cue.IMPACT&&METER_IMPACTS.contains(pattern)){
                boolean floor=e.cue()==Payloads.Cue.FIELD;
                double r=floor?Math.max(.35,e.size()*(.52+.48*t)):Math.max(.30,size*(.43+.29*t));
                var center=floor?to.add(0,.109+.12*t,0):to;
                Vec3 x=floor?X:right,y=floor?Z:up;
                reticle(v,pose,center,x,y,r,t,a,style);
                if((pattern.equals("scan")||pattern.equals("mirror"))&&floor)
                    disc(v,pose,center.add(0,.08,0),X,Z,r*1.34,0,3,a*.74f);
            }
        }else if(style.ritual()){
            if(e.cue()==Payloads.Cue.FIELD&&SEAL_FIELDS.contains(pattern)){
                double radius=e.size()*(.50+.40*Math.sin(t*Math.PI*.5));
                boolean rune=id.equals("rune_summon")||id.equals("rune_relay");
                var base=to.add(0,.084,0);
                var ring=base.add(0,.086+t*.41,0);
                if(e.size()<20){
                    disc(v,pose,base,X,Z,radius*(rune?2.2:1.83),e.started()*.013,3,a*.87f);
                    for(int i=0;i<3;i++)arc(v,pose,ring,X,Z,radius*(1+i*.12),i*2.09-t*1.7,1.08,
                            .051+Math.min(.18,radius*.011),i==1?style.tint():style.accent(),a*.66f,11);
                }
                if(rune||pattern.equals("rose")||pattern.equals("sanctuary")){
                    for(int i=0;i<5;i++){
                        double angle=i*1.256+t*.68;
                        Vec3 seed=base.add(Math.cos(angle)*radius*.69,.06,Math.sin(angle)*radius*.69);
                        Vec3 tip=seed.add(Math.cos(angle)*.18,.36+t*1.11,Math.sin(angle)*.18);
                        ribbon(v,pose,seed,tip,.086+size*.012,1,style.accent(),a*.68f);
                    }
                }
            }
        }
    }

    private static void reticle(VertexConsumer v,PoseStack.Pose pose,Vec3 center,Vec3 x,Vec3 y,double r,
                                float time,float a,CastMotion.Style style){
        for(int i=0;i<4;i++){
            double theta=i*Math.PI/2+time*.53;
            arc(v,pose,center,x,y,r,theta+.16,.91,.043,style.accent(),a*.84f,9);
            Vec3 unit=x.scale(Math.cos(theta)).add(y.scale(Math.sin(theta)));
            Vec3 tick=center.add(unit.scale(r*1.17));
            ribbon(v,pose,tick,tick.add(unit.scale(Math.min(.24,r*.19))),.034,1,style.core(),a*.85f);
        }
    }

    private static int life(Payloads.SkillCue cue,CastMotion.Style style) {
        return switch(cue.cue()){
            case FIELD -> switch(cue.skill()){
                case "bethlehem_star" -> 215;
                case "othinus_sky_crossbow" -> 87;
                case "othinus_world_dark" -> 110;
                case "wind_plasma","gungnir" -> 46;
                default -> style.quiet()?12:26+style.trails();
            };
            case BEAM,REFLECT -> switch(style.pattern()){
                case "wire" -> 10;
                case "ballistic" -> 6;
                case "rail" -> 13;
                case "plasma" -> 21;
                case "melt" -> 16;
                case "dragon" -> 29;
                case "sword" -> cue.skill().equals("curtana_slice")?29:12;
                default -> 13;
            };
            case MOVE -> cue.skill().equals("othinus_black_moon")?35
                    :cue.skill().equals("gungnir")?13:cue.skill().equals("world_rejecter")?21:11;
            case IMPACT,CANCEL,WALL,PARRY -> cue.skill().startsWith("mental_")?20:
                    style.pattern().equals("physical")?8:15;
            case RELEASE -> style.quiet()?8:12;
            case TERRAIN -> 18;
            case WINDUP,RECOVER,WHIFF,ILLUSION -> 1;
        };
    }

    private static void release(VertexConsumer v,PoseStack.Pose p,Vec3 from,Vec3 to,Vec3 right,Vec3 up,
                                CastMotion.Style style,double size,float t,float alpha){
        if(style.quiet()){
            // The action itself is the readable cue. No luminous cast at the head or feet.
            return;
        }
        switch(style.pattern()){
            case "mind" -> {
                disc(v,p,from,right,up,.26+t*.11,0,0,alpha*.71f);
            }
            case "scan" -> disc(v,p,to.add(0,.32,0),right,up,.40+t*.12,0,3,alpha*.67f);
            case "wire","physical","rift","trail","sword" -> {}
            case "rail" -> {
                disc(v,p,from,right,up,.92-t*.37,t*.36,0,alpha);
                sparks(v,p,from,right,up,6,size*.62,t,alpha*.80f,73,.08);
            }
            case "plasma" -> disc(v,p,from,right,up,1.2-t*.53,t*4,0,alpha*.92f);
            default -> {
                if(style.anchor()==CastMotion.Anchor.GROUND){
                    disc(v,p,from.add(0,.035,0),X,Z,size*(.75+t*.69),t*.4,3,alpha*.7f);
                }else{
                    disc(v,p,from,right,up,size*(.40+t*.32),t*.37,0,alpha*.76f);
                }
            }
        }
    }

    private static void emission(MultiBufferSource buffers,PoseStack.Pose p,Payloads.SkillCue event,
                                 Vec3 from,Vec3 to,Vec3 right,Vec3 up,CastMotion.Style style,
                                 double size,float t,float alpha){
        if(event.cue()==Payloads.Cue.BEAM && from.distanceToSqr(to)>.001){
            double width=switch(style.pattern()){
                case "rail" -> .23;
                case "plasma" -> .37;
                case "melt" -> .16;
                case "wire" -> .065;
                case "spear","sanctuary" -> .20;
                default -> 0;
            };
            if(width>0){
                var glow=buffers.getBuffer(material(event.skill(),true));
                ribbon(glow,p,from,to,width*(1-.35*t),1,0xffffff,alpha*.34f);
            }
        }else if(event.cue()==Payloads.Cue.IMPACT && switch(style.pattern()){
            case "melt","rail","plasma","flame","third","arc" -> true;
            default -> false;
        }){
            var glow=buffers.getBuffer(material(event.skill(),true));
            disc(glow,p,to,right,up,size*(.52+t*.68),t*.79,0,alpha*.29f);
        }else if(event.cue()==Payloads.Cue.FIELD && event.skill().equals("wind_plasma") && t<.62){
            var glow=buffers.getBuffer(material(event.skill(),true));
            double radius=event.size()*(.28+.72*Math.min(1,Math.sqrt(t*2.5)));
            for(int i=0;i<4;i++){
                arc(glow,p,to.add(0,.15+i*.07,0),X,Z,radius*(1-i*.06),i*Math.PI*.5+t*.51,
                        1.15,.38,0xffffff,alpha*.37f,15);
            }
        }
    }

    static void ready(HumNpc npc,NpcModel model,PoseStack stack,MultiBufferSource buffers,float partial) {
        var skill=Skills.get(npc.technique());
        if(skill==null||skill.windup()<1||npc.casting().isEmpty()||npc.abortAge()>=0)return;
        var style=CastMotion.style(skill);
        if(style.quiet()||style.pattern().equals("physical")||style.pattern().equals("ballistic")
                ||style.pattern().equals("wire")||style.pattern().equals("sword")||style.pattern().equals("rift"))return;
        float age=npc.signatureAge()+partial;
        float charge=Mth.clamp(age/skill.windup(),0,1);
        float alpha=.23f+charge*.67f;
        double size=style.scale()*(.24+.53*charge);
        Vec3 at=model.anchor(style.anchor());
        var pose=stack.last();
        if(style.anchor()==CastMotion.Anchor.THIRD_HAND){
            thirdHand(buffers.getBuffer(material(skill.id(),false)),pose,at,age,charge,alpha);
            return;
        }
        if(style.pattern().equals("rail")) {
            // A coin has a solid edge and flips within the reach of Mikoto's thumb.
            var coins=buffers.getBuffer(RenderType.entityCutoutNoCull(
                    ResourceLocation.fromNamespaceAndPath(BioHum.ID,"textures/item/rail_coin.png")));
            double flip=Mth.clamp((charge-.18)/.82,0,1);
            Vec3 center=at.add(0,-.04-.26*Math.sin(flip*Math.PI),-.035);
            coin(coins,pose,center,new Vec3(Math.cos(flip*15),0,Math.sin(flip*15)),Y,.135);
            if(charge>.5f){
                var discharge=buffers.getBuffer(material("electric_shock",false));
                double reach=.20+.28*charge;
                electric(discharge,pose,center.add(-reach,-.12,0),center.add(reach,.03,0),
                        Z,Y,.13,charge,0xffffff,alpha);
            }
            return;
        }
        var v=buffers.getBuffer(material(skill.id(),false));
        if(style.pattern().equals("plasma")){
            // In model coordinates Y points down. Wind converges through tilted lanes on both sides.
            double core=.16+1.01*Math.pow(charge,2.4);
            int layers=PresentationConfig.quality().extraLayers;
            for(int i=0;i<3+layers*2;i++){
                double step=(age*.057+i*.217)%1;
                double ring=(1-step)*(2.75+1.5*(1-charge))+.25;
                double lift=(1-step)*(i%2==0?.85:-.74);
                double phase=age*.21+i*2.22;
                Vec3 axis=new Vec3(Math.cos(i*1.73),.48*Math.sin(i*.95),Math.sin(i*1.73)).normalize();
                arc(v,pose,at.add(0,lift,0),axis,axis.cross(Y).cross(axis).normalize(),ring,
                        phase,1.42+step*.81,.12+step*.105,0xffffff,alpha*(float)Math.sin(step*Math.PI),17);
            }
            if(charge>.19f){
                disc(v,pose,at,X,Y,core*1.36,age*.13,0,alpha);
                disc(v,pose,at,Z,Y,core*1.36,age*-.16,0,alpha*.84f);
            }
            var glow=buffers.getBuffer(material(skill.id(),true));
            if(charge>.19f)disc(glow,pose,at,X,Y,core*1.30,age*.13,0,alpha*.58f);
            for(int i=0;i<layers*2;i++){
                double step=(age*.057+i*.317)%1;
                double ring=(1-step)*(2.75+1.5*(1-charge))+.25;
                Vec3 axis=new Vec3(Math.cos(i*1.73),.48*Math.sin(i*.95),Math.sin(i*1.73)).normalize();
                arc(glow,pose,at.add(0,(1-step)*(i%2==0?.85:-.74),0),axis,axis.cross(Y).cross(axis).normalize(),ring,
                        age*.21+i*2.22,1.42+step*.81,.07+step*.10,0xffffff,alpha*.47f*(float)Math.sin(step*Math.PI),17);
            }
        }else if(style.technical()&&METER_CHARGE.contains(style.pattern())){
            reticle(v,pose,at,X,Y,size*.46,charge,alpha*.84f,style);
            disc(v,pose,at,X,Y,size*.76,0,0,alpha*.86f);
            if(style.pattern().equals("melt")){
                for(int i=0;i<3;i++){
                    Vec3 from=at.add((i-1)*size*.28,-.11-size*.15,0);
                    ribbon(v,pose,from,at,.038,1,style.accent(),alpha*.68f);
                }
            }
        }else if(style.anchor()==CastMotion.Anchor.GROUND){
            disc(v,pose,at,X,Z,size*(skill.id().equals("bethlehem_star")?3.5:2.25),age*.07,3,alpha);
        }else{
            double width=style.pattern().equals("mind")?.21:size*.65;
            if(style.ritual()&&SEAL_CHARGE.contains(style.pattern())){
                double circle=width*(1.15+.37*charge);
                disc(v,pose,at.add(0,0,-.075),X,Y,circle,age*.025,3,alpha*.66f);
                disc(v,pose,at.add(0,0,-.125),X,Y,circle*.73,-age*.019,0,alpha*.72f);
            }
            disc(v,pose,at,X,Y,width,age*.051,0,alpha);
            disc(v,pose,at,Z,Y,width,age*-.047,0,alpha*.69f);
        }
    }

    private static void thirdHand(VertexConsumer v,PoseStack.Pose p,Vec3 at,float time,float reach,float alpha){
        double scale=.32+reach*.68;
        Vec3 arm=at.add(.20,.32,.37);
        Vec3 palm=at;
        ribbon(v,p,arm,palm,.20*scale,1,0xffffff,alpha*.88f);
        for(int i=0;i<5;i++){
            double spread=(i-2)*.137*scale;
            double twitch=.032*Math.sin(time*.3+i);
            Vec3 joint=palm.add(spread,-.07*scale+twitch,-(.26-i*.019)*scale);
            Vec3 tip=joint.add(spread*.47,.07*scale,-(.22-Math.abs(i-2)*.03)*scale);
            ribbon(v,p,palm,joint,.082*scale,1,0xffffff,alpha*.95f);
            ribbon(v,p,joint,tip,.056*scale,1,0xffffff,alpha*.9f);
        }
    }

    static void wings(HumNpc npc,ModelPart body,PoseStack pose,MultiBufferSource buffers,float age) {
        var skill=Skills.get(npc.wings());
        if(skill==null)return;
        var style=CastMotion.style(skill);
        var v=buffers.getBuffer(material(npc.wings(),false));
        pose.pushPose();
        body.translateAndRotate(pose);
        var matrix=pose.last();
        for(int side:new int[]{-1,1})for(int feather=0;feather<3;feather++){
            double beat=.09*Math.sin(age*.17+feather*.48);
            var root=new Vec3(side*(.16+feather*.035),.19+feather*.07,.16);
            var bend=new Vec3(side*(.77+feather*.10),-.42+feather*.43,.46+beat);
            var tip=new Vec3(side*(1.84-feather*.16),-.91+feather*.72+beat,1.02+feather*.18);
            var edge=new Vec3(side*(.49+feather*.15),.15+feather*.22,.38+beat);
            quad(v,matrix,root,bend,tip,edge,1,0xffffff,.79f);
            ribbon(v,matrix,bend,tip,.094,1,0xffffff,.72f);
            disc(v,matrix,tip,X,Y,.33,age*.09,2,.61f);
        }
        pose.popPose();
    }

    static void familiar(PoseStack stack,MultiBufferSource buffers,float age,float facing){
        var v=buffers.getBuffer(material("rune_summon",false));
        var p=stack.last();
        var skill=Skills.get("rune_summon");
        var style=CastMotion.style(skill);
        Vec3 side=X.yRot(facing), depth=Z.yRot(facing);
        for(int i=0;i<6;i++){
            double cycle=(age*.056+i*.173)%1;
            double angle=i*2.399+cycle*.72;
            double radius=.41+.23*Math.sin(cycle*Math.PI);
            double y=-.17-1.36*cycle;
            Vec3 at=side.scale(radius*Math.cos(angle)).add(depth.scale(radius*Math.sin(angle))).add(0,y,0);
            double tip=.29+Math.sin(cycle*Math.PI)*.37;
            float alpha=(float)(Math.sin(cycle*Math.PI)*.79);
            ribbon(v,p,at,at.add(.15*Math.sin(angle),-tip,.11*Math.cos(angle)),.13,1,0xffffff,alpha);
        }
        var magma=buffers.getBuffer(FAMILIAR_FLAME);
        for(int i=0;i<5;i++){
            double f=(age*.045+i*.19)%1;
            double sweep=i*2.399+f*.51;
            Vec3 at=new Vec3(Math.cos(sweep)*(.34+.18*f),-.20-1.38*f,Math.sin(sweep)*(.34+.16*f));
            Vec3 x=new Vec3(Math.cos(sweep+.8),0,Math.sin(sweep+.8));
            fullSprite(magma,p,at,x,Y,.19+.12*Math.sin(f*Math.PI),.29+f*.16,
                    (float)(Math.sin(f*Math.PI)*.91));
        }
        var glow=buffers.getBuffer(material("rune_summon",true));
        Vec3 mid=new Vec3(0,-.92,.04);
        disc(glow,p,mid,side,Y,.58+Math.sin(age*.27)*.13,age*.08,0,.42f);
        arc(glow,p,new Vec3(0,-.05,0),X,Z,.61,age*.13,2.9,.11,style.accent(),.48f,17);
    }

    static void bolt(AbilityBolt bolt,PoseStack stack,MultiBufferSource buffers,org.joml.Quaternionf rotation) {
        var skill=Skills.get(bolt.skill());
        var style=CastMotion.style(skill);
        if(skill==null)return;
        var v=buffers.getBuffer(material(bolt.skill(),false));
        var pose=stack.last();
        var x=new Vector3f(1,0,0).rotate(rotation);
        var y=new Vector3f(0,1,0).rotate(rotation);
        Vec3 right=new Vec3(x.x,x.y,x.z), up=new Vec3(y.x,y.y,y.z);
        double scale=style.scale();
        disc(v,pose,Vec3.ZERO,right,up,scale*.64,bolt.tickCount*.18,0,.95f);
        Vec3 previous=new Vec3(bolt.xOld-bolt.getX(),bolt.yOld-bolt.getY(),bolt.zOld-bolt.getZ());
        if(previous.lengthSqr()>.0001&&previous.lengthSqr()<16){
            ribbon(v,pose,previous,Vec3.ZERO,scale*.105,1,0xffffff,.79f);
        }
    }

    private static void beam(VertexConsumer v,PoseStack.Pose pose,Vec3 a,Vec3 b,Vec3 right,Vec3 up,
                             CastMotion.Style style,String id,double size,float t,float alpha,boolean reflection) {
        Vec3 delta=b.subtract(a);
        if(delta.lengthSqr()<1e-7)return;
        Vec3 axis=delta.normalize(),side=normal(axis),other=axis.cross(side).normalize();
        String kind=style.pattern();
        double width=size*(1-.37*t);
        switch(kind){
            case "rail" -> {
                ribbon(v,pose,a,b,width*1.85,1,0xffffff,alpha*.86f);
                ribbon(v,pose,a,b,width*.47,1,0xffffff,alpha*.94f);
                double wake=Math.min(9,delta.length());
                for(int i=0;i<3;i++){
                    double advance=.6+i*1.75+t*(2.4+i*.8);
                    if(advance>=wake)break;
                    Vec3 center=a.add(axis.scale(advance));
                    arc(v,pose,center,side,other,.22+i*.16+t*.62,t*1.9+i*2.4,
                            3.8,.058,style.accent(),alpha*(.72f-i*.16f),13);
                }
            }
            case "plasma" -> {
                ribbon(v,pose,a,b,width*2.65,1,0xffffff,alpha*.78f);
                ribbon(v,pose,a,b,width*.91,1,0xffffff,alpha*.83f);
                helix(v,pose,a,b,side,other,.63+width*.79,2.1,t*8,0xffffff,alpha*.72f);
                helix(v,pose,a,b,side,other,.33+width*.85,1.6,-t*6,style.accent(),alpha*.68f);
                disc(v,pose,b,right,up,size*(2.5-t*.78),t*2,0,alpha*.76f);
            }
            case "arc","sanctuary","null" -> {
                electric(v,pose,a,b,side,other,Math.max(.16,width*1.2),t,0xffffff,alpha);
                electric(v,pose,a,b,other,side,Math.max(.10,width*.68),t+.29f,style.accent(),alpha*.69f);
                if(kind.equals("sanctuary"))ribbon(v,pose,a,b,width*1.85,1,0xffffff,alpha*.77f);
            }
            case "melt" -> {
                ribbon(v,pose,a,b,width*1.9,1,0xffffff,alpha*.95f);
                ribbon(v,pose,a,b,width*.51,1,0xffffff,alpha*.91f);
                for(int i=0;i<2;i++){
                    var near=a.add((i==0?side:other).scale(width*(.53+t*.2)));
                    var far=b.subtract((i==0?side:other).scale(width*.32));
                    ribbon(v,pose,near,far,width*.12,1,0xffffff,alpha*.73f);
                }
            }
            case "wire" -> {
                ribbon(v,pose,a,b,.045+width*.08,1,0xffffff,alpha*.96f);
                disc(v,pose,a.lerp(b,Math.min(1,.22+t*.9)),right,up,.19,0,0,alpha*.74f);
            }
            case "sword" -> {
                boolean cut=id.equals("curtana_slice");
                Vec3 curve=cut?Y:other;
                ribbon(v,pose,a,b,width*(cut?.78:.24),1,0xffffff,alpha);
                slash(v,pose,a,b,curve,cut?2.3:1.13,width*(cut?.57:.55),t,alpha);
                if(cut){
                    slash(v,pose,a,b,Y.scale(-1),1.64,width*.31,t,alpha*.71f);
                    for(int i=1;i<12;i++){
                        double q=i/12.0;
                        Vec3 point=a.lerp(b,q).add(0,Math.sin(i*6.24+t*4)*(.15+t),0);
                        disc(v,pose,point,right,up,.28+t*.35,i*1.12+t,2,alpha*.59f);
                    }
                }
            }
            case "ballistic" -> ribbon(v,pose,a,b,width*.17,1,0xffffff,alpha*(1-t));
            case "dragon" -> dragons(v,pose,a,b,axis,side,other,right,up,t,alpha);
            case "third" -> {
                double force=Math.sqrt(Math.max(.1,size/style.scale()-1)*4);
                double body=.38+force*.35;
                double tip=Math.min(delta.length(),.92+force*.35);
                Vec3 palm=b.subtract(axis.scale(tip));
                ribbon(v,pose,a,palm,body,1,0xffffff,alpha*.8f);
                ribbon(v,pose,a,b,body*.31,1,0xffffff,alpha*.82f);
                for(int i=0;i<5;i++){
                    double finger=(i-2)*body*.44;
                    Vec3 joint=palm.add(axis.scale(tip*.54)).add(side.scale(finger)).add(other.scale(body*.2));
                    Vec3 tipEnd=b.add(side.scale(finger*.55)).add(other.scale(-.10*body));
                    ribbon(v,pose,palm,joint,body*.22,1,0xffffff,alpha*.84f);
                    ribbon(v,pose,joint,tipEnd,body*.15,1,0xffffff,alpha*.91f);
                }
            }
            case "spear" -> {
                ribbon(v,pose,a,b,width*1.68,1,0xffffff,alpha);
                ribbon(v,pose,a,b,width*.46,1,0xffffff,alpha);
                double tip=Math.min(delta.length(),2.1+size*.8);
                Vec3 socket=b.subtract(axis.scale(tip));
                for(int i=0;i<4;i++){
                    double angle=i*Math.PI*.5+t*.55;
                    Vec3 edge=side.scale(Math.cos(angle)).add(other.scale(Math.sin(angle))).scale(width*.94);
                    ribbon(v,pose,socket.add(edge),b,width*.21,1,0xffffff,alpha*.83f);
                }
            }
            case "earth","iron" -> {
                ribbon(v,pose,a,b,width*1.4,1,0xffffff,alpha*.48f);
                for(int i=1;i<=7;i++){
                    double phase=(i*.137+t*.53)%1;
                    Vec3 point=a.lerp(b,phase).add(side.scale((i%3-1)*width*.9));
                    disc(v,pose,point,right,up,size*(.47+(i%4)*.18),i*.93+t*2,2,alpha*.75f);
                }
            }
            default -> {
                ribbon(v,pose,a,b,width*(reflection?.26:1.28),1,0xffffff,alpha*.87f);
                if(reflection||kind.equals("shock")){
                    for(int i=1;i<=3;i++){
                        double step=(i*.23+t*.46)%1;
                        arc(v,pose,a.lerp(b,step),side,other,size*(.33+t+step*.42),
                                i+t,4.3,.085,style.accent(),alpha*.64f,16);
                    }
                }
            }
        }
    }

    private static void dragons(VertexConsumer v,PoseStack.Pose pose,Vec3 start,Vec3 end,Vec3 axis,
                                Vec3 side,Vec3 other,Vec3 screenRight,Vec3 screenUp,float age,float alpha){
        double length=start.distanceTo(end);
        double sweep=Math.min(13.5,length*.84);
        double span=Math.max(1.2,Math.min(4.1,sweep*.34));
        for(int head=0;head<8;head++){
            double turn=head*Math.PI/4+.26;
            Vec3 spread=side.scale(Math.cos(turn)).add(other.scale(Math.sin(turn)));
            Vec3 curl=axis.cross(spread);
            double reach=.84-(head%3)*.075;
            double forward=.19+Math.min(1,(age+.17)*3.7)*(reach-.19);
            double flare=Math.min(1,(age+.15)*4);
            Vec3 previous=start;
            for(int i=1;i<=14;i++){
                double q=i/14.0;
                double wave=Math.sin(q*Math.PI*.68)*span*flare;
                Vec3 point=start.add(axis.scale(q*forward*sweep)).add(spread.scale(wave))
                        .add(curl.scale(Math.sin(q*5+head+age*3)*q*.4));
                ribbon(v,pose,previous,point,.25+.48*q,1,0xffffff,alpha*.86f);
                if(i==14){
                    Vec3 movement=point.subtract(previous);
                    double angle=Math.atan2(movement.dot(screenUp),movement.dot(screenRight));
                    double width=(1.45+span*.43)*(head%2==0?1:.9);
                    double height=width*.57;
                    Vec3 x=screenRight.scale(Math.cos(angle)*width).add(screenUp.scale(Math.sin(angle)*width));
                    Vec3 y=screenUp.scale(Math.cos(angle)*height).subtract(screenRight.scale(Math.sin(angle)*height));
                    float u0=(head%2)*.25f+.003f,u1=u0+.244f,v0=.503f+(head/2)*.125f,v1=v0+.119f;
                    quad(v,pose,point.subtract(x).subtract(y),point.add(x).subtract(y),point.add(x).add(y),
                            point.subtract(x).add(y),u0,v0,u1,v1,0xffffff,alpha*.94f);
                }
                previous=point;
            }
        }
    }

    private static void movement(VertexConsumer v,PoseStack.Pose p,Vec3 a,Vec3 b,Vec3 right,Vec3 up,
                                 CastMotion.Style style,String id,double s,float t,float alpha){
        if(style.quiet()||style.pattern().equals("physical"))return;
        if(id.equals("world_rejecter")){
            ellipse(v,p,b.add(0,.7,0),right,up,s*(.38-.15*t),s*(.58-.18*t),.06-t*.19,3,alpha*.91f);
            disc(v,p,a,right,up,.44+t*.15,0,0,alpha*.64f);
        }else if(style.pattern().equals("rift")){
            double contraction=1-.77*t;
            double narrow=Math.max(.11,s*.23);
            ellipse(v,p,a,right,up,narrow*contraction,s*.78*contraction,-.08,0,alpha*.88f);
            ellipse(v,p,b,right,up,narrow*(.74+t),s*(.77-t*.48),.09,0,alpha*.93f);
            disc(v,p,b.add(0,-s*.47,0),X,Z,s*(.48+t*.42),t,3,alpha*.55f);
        }else {
            ribbon(v,p,a,b,s*.15*(1-t*.56),1,0xffffff,alpha*.69f);
            disc(v,p,b,right,up,s*(.42+t*.56),t*.62,2,alpha*.66f);
        }
    }

    private static void field(VertexConsumer v,PoseStack.Pose p,Vec3 at,Vec3 right,Vec3 up,
                              CastMotion.Style style,String id,double radius,float t,float alpha){
        if(style.quiet())return;
        double s=radius*(.32+.68*Math.min(1,Math.sqrt(t*2.5)));
        String pattern=style.pattern();
        switch(pattern){
            case "plasma","shock","spear" -> {
                for(int i=0;i<4;i++){
                    double angle=i*Math.PI/2+t*.51;
                    Vec3 floor=at.add(0,.03+i*.054+t*.48,0);
                    arc(v,p,floor,X,Z,s*(1-i*.061),angle,1.15,.18+radius*.015,
                            i%2==0?0xffffff:style.accent(),alpha*(.85f-i*.12f),13);
                }
                if(pattern.equals("plasma") && t<.68){
                    for(int i=0;i<5;i++){
                        double a=i*2.4+t*.87;
                        var base=at.add(Math.cos(a)*s*.63,0,Math.sin(a)*s*.63);
                        var crest=base.add(Math.cos(a)*radius*.12,Math.sin(t*Math.PI)*(1.6+i*.39),Math.sin(a)*radius*.12);
                        ribbon(v,p,base,crest,.17+i*.018,1,0xffffff,alpha*.64f);
                    }
                }
                sparks(v,p,at,right,up,style.trails()+3,s*.84,t,alpha*.83f,id.hashCode(),.14);
            }
            case "star" -> {
                if(id.equals("bethlehem_star")){
                    // The outline matches the rotated-square deck that the server builds below the light.
                    double extent=radius+4;
                    double reveal=Mth.clamp(t*5,0,1);
                    Vec3 center=at.add(0,.09,0);
                    for(int i=0;i<4;i++){
                        double az=i*Math.PI*.5;
                        Vec3 start=center.add(Math.cos(az)*extent,0,Math.sin(az)*extent);
                        Vec3 end=center.add(Math.cos(az+Math.PI*.5)*extent,0,Math.sin(az+Math.PI*.5)*extent);
                        var mid=start.lerp(end,.5);
                        ribbon(v,p,mid.lerp(start,reveal),mid.lerp(end,reveal),.34,1,0xffffff,alpha*.84f);
                        var tower=center.add(Math.cos(az)*(radius-2),0,Math.sin(az)*(radius-2));
                        ribbon(v,p,tower,tower.add(0,1+3*reveal,0),.37,1,0xffffff,alpha*.63f);
                    }
                    arc(v,p,center.add(0,9,0),X,Z,2.5+3.2*t,t*2.3,5.7,.22,style.accent(),alpha*.78f,26);
                }else disc(v,p,at.add(0,.065,0),X,Z,s*2,t*.67,3,alpha*.81f);
            }
            case "world" -> {
                for(int layer=0;layer<3;layer++){
                    double ring=s*(.83+layer*.085);
                    var horizon=at.add(0,.11+layer*(.18+1.6*t),0);
                    for(int side=0;side<4;side++){
                    arc(v,p,horizon,X,Z,ring,side*Math.PI*.5+t*.73+layer*.33,
                                1.38,.32+layer*.09,0xffffff,alpha*(.67f-layer*.13f),18);
                    }
                }
            }
            case "flame" -> {
                double y=id.equals("rune_summon")?.045:.07;
                if(id.equals("rune_summon"))disc(v,p,at.add(0,y,0),X,Z,s*1.62,0,3,alpha*.9f);
                sparks(v,p,at,right,up,style.trails()+3,s*.65,t,alpha*.9f,id.hashCode(),y);
                for(int i=0;i<3;i++){
                    double angle=i*2.4+t;
                    Vec3 start=at.add(Math.cos(angle)*s*.55,y,Math.sin(angle)*s*.55);
                    ribbon(v,p,start,start.add(.15,(.45+s*.29)*(1-t),.11),.18,1,0xffffff,alpha*.74f);
                }
            }
            case "earth","iron","physical","mist","ice" -> {
                for(int i=0;i<4;i++){
                    arc(v,p,at.add(0,.10+i*.02,0),X,Z,s*(.73+i*.07),i*Math.PI/2+t*.29,
                            .79,.1+Math.min(.25,s*.023),0xffffff,alpha*.63f,12);
                }
                sparks(v,p,at,right,up,style.trails(),s*.75,t,alpha*.68f,id.hashCode(),.09);
            }
            default -> {
                disc(v,p,at.add(0,.065,0),X,Z,s*2,t*.67,3,alpha*.81f);
                if(radius>3)arc(v,p,at.add(0,.13,0),X,Z,s,t*.9,5.2,.12,0xffffff,alpha*.57f,30);
            }
        }
    }

    private static void impact(VertexConsumer v,PoseStack.Pose p,Vec3 at,Vec3 right,Vec3 up,
                               CastMotion.Style style,double size,float t,float alpha,int seed){
        if(style.quiet())return;
        if(style.pattern().equals("wire")){
            disc(v,p,at,right,up,.32+t*.23,0,0,alpha*.7f);
            return;
        }
        if(style.pattern().equals("mind")){
            disc(v,p,at.add(0,.13,0),right,up,.84+t*.13,0,2,alpha*.98f);
            return;
        }
        double extent=switch(style.pattern()){
            case "plasma","flame" -> 1.38;
            case "melt","rail","arc","spear","third" -> 1.15;
            case "physical","ballistic" -> .58;
            default -> .82;
        };
        disc(v,p,at,right,up,size*(.52+t*.83)*extent,t*.54,2,alpha*.83f);
        sparks(v,p,at,right,up,style.trails(),size*extent,t,alpha,seed,.08);
    }

    private static void terrain(VertexConsumer v,PoseStack.Pose p,Vec3 low,Vec3 high,Vec3 right,Vec3 up,
                                CastMotion.Style style,float t,float alpha,int seed){
        if(style.quiet())return;
        // The server reports both endpoint blocks after applying the edits.
        sparks(v,p,low.add(0,.34,0),right,up,style.trails(),.57,t,alpha*.72f,seed,.08);
        if(low.distanceToSqr(high)>.04){
            sparks(v,p,high.add(0,.34,0),right,up,style.trails(),.57,t,alpha*.72f,seed+47,.08);
        }
    }

    private static void sparks(VertexConsumer v,PoseStack.Pose p,Vec3 at,Vec3 right,Vec3 up,int count,
                               double size,float t,float alpha,int seed,double lift){
        double turn=(seed&31)*.19;
        for(int i=0;i<count;i++){
            double a=turn+i*2.399963;
            double r=size*(.21+t*(.8+(i%3)*.17));
            Vec3 out=at.add(Math.cos(a)*r,lift+Math.sin(t*2.7)*(size*.23+(i%3)*.12),Math.sin(a)*r);
            double radius=Math.min(.43,.045+size*(.08+.07*(1-t)));
            ellipse(v,p,out,right,up,radius,radius*.51,a,0,alpha*.86f);
        }
    }

    static void arc(VertexConsumer v,PoseStack.Pose p,Vec3 center,Vec3 x,Vec3 y,
                            double radius,double phase,double span,double width,int tint,float alpha,int segments){
        if(radius<.02||alpha<.025f)return;
        Vec3 previous=null;
        for(int i=0;i<=segments;i++){
            double step=(double)i/segments;
            double angle=phase+step*span;
            Vec3 point=center.add(x.scale(radius*Math.cos(angle))).add(y.scale(radius*Math.sin(angle)));
            if(previous!=null){
                float edge=(float)Math.sin(Math.PI*(i-.5)/segments);
                ribbon(v,p,previous,point,width*(.35+.65*edge),1,tint,alpha*(.44f+edge*.56f));
            }
            previous=point;
        }
    }

    private static void slash(VertexConsumer v,PoseStack.Pose p,Vec3 a,Vec3 b,Vec3 axis,double height,
                              double width,float t,float alpha){
        Vec3 previous=a;
        for(int i=1;i<=24;i++){
            double step=i/24.0;
            double rise=Math.sin(step*Math.PI);
            Vec3 point=a.lerp(b,step).add(axis.scale(height*rise*(.54+.77*t)));
            ribbon(v,p,previous,point,width*(.27+.73*rise),1,0xffffff,alpha);
            previous=point;
        }
    }

    static void electric(VertexConsumer v,PoseStack.Pose p,Vec3 a,Vec3 b,Vec3 side,Vec3 up,
                                 double width,float t,int color,float alpha){
        int segments=Math.max(5,Math.min(34,(int)(a.distanceTo(b)*2.2)));
        Vec3 previous=a;
        for(int i=1;i<=segments;i++){
            double s=(double)i/segments;
            double taper=Math.sin(Math.PI*s)*width;
            Vec3 at=a.lerp(b,s).add(side.scale(Math.sin(i*7.01+t*19)*taper))
                    .add(up.scale(Math.cos(i*5.47-t*17)*taper*.69));
            ribbon(v,p,previous,at,Math.max(.035,width*.27),1,color,alpha);
            previous=at;
        }
    }

    private static void helix(VertexConsumer v,PoseStack.Pose p,Vec3 a,Vec3 b,Vec3 side,Vec3 up,
                              double radius,double turns,double time,int color,float alpha){
        int steps=Math.max(8,Math.min(36,(int)(a.distanceTo(b)*2)+6));
        Vec3 previous=a;
        for(int i=1;i<=steps;i++){
            double q=(double)i/steps, angle=q*turns*2*Math.PI+time, edge=radius*Math.sin(q*Math.PI);
            Vec3 point=a.lerp(b,q).add(side.scale(edge*Math.cos(angle))).add(up.scale(edge*Math.sin(angle)));
            ribbon(v,p,previous,point,.045+radius*.095,1,color,alpha*.88f);
            previous=point;
        }
    }

    static Vec3 normal(Vec3 axis){
        var side=axis.cross(Y).normalize();
        return side.lengthSqr()<.001?X:side;
    }

    static void ribbon(VertexConsumer v,PoseStack.Pose p,Vec3 from,Vec3 to,double width,
                               int tile,int color,float alpha){
        if(from.distanceToSqr(to)<.000001)return;
        Vec3 axis=to.subtract(from).normalize(), side=normal(axis), up=axis.cross(side).normalize();
        Vec3 d=side.scale(width);
        quad(v,p,from.subtract(d),to.subtract(d),to.add(d),from.add(d),tile,color,alpha);
        d=up.scale(width);
        quad(v,p,from.subtract(d),to.subtract(d),to.add(d),from.add(d),tile,color,alpha*.87f);
    }

    private static void disc(VertexConsumer v,PoseStack.Pose p,Vec3 at,Vec3 a,Vec3 b,double size,
                             double angle,int tile,float alpha){
        ellipse(v,p,at,a,b,size/2,size/2,angle,tile,alpha);
    }

    private static void ellipse(VertexConsumer v,PoseStack.Pose p,Vec3 at,Vec3 a,Vec3 b,double wide,
                                double tall,double angle,int tile,float alpha){
        double c=Math.cos(angle),s=Math.sin(angle);
        Vec3 dx=a.scale(c).add(b.scale(s)).scale(wide),dy=b.scale(c).subtract(a.scale(s)).scale(tall);
        quad(v,p,at.subtract(dx).subtract(dy),at.add(dx).subtract(dy),at.add(dx).add(dy),at.subtract(dx).add(dy),
                tile,0xffffff,alpha);
    }

    private static void quad(VertexConsumer v,PoseStack.Pose p,Vec3 a,Vec3 b,Vec3 c,Vec3 d,
                             int tile,int rgb,float alpha){
        float u0=(tile%2)*.5f+.002f, v0=(tile/2)*.5f+.002f;
        quad(v,p,a,b,c,d,u0,v0,u0+.496f,v0+.496f,rgb,alpha);
    }

    static void fullSprite(VertexConsumer v,PoseStack.Pose p,Vec3 at,Vec3 x,Vec3 y,
                           double width,double height,float alpha){
        Vec3 dx=x.scale(width),dy=y.scale(height);
        quad(v,p,at.subtract(dx).subtract(dy),at.add(dx).subtract(dy),at.add(dx).add(dy),
                at.subtract(dx).add(dy),0,0,1,1,0xffffff,alpha);
    }

    private static void quad(VertexConsumer v,PoseStack.Pose p,Vec3 a,Vec3 b,Vec3 c,Vec3 d,
                             float u0,float v0,float u1,float v1,int rgb,float alpha){
        if(QA && alpha>=.004f)submittedQuads++;
        int color=((int)(Math.clamp(alpha,0,1)*255)<<24)|rgb;
        v.addVertex(p,(float)a.x,(float)a.y,(float)a.z).setUv(u0,v1).setColor(color);
        v.addVertex(p,(float)b.x,(float)b.y,(float)b.z).setUv(u1,v1).setColor(color);
        v.addVertex(p,(float)c.x,(float)c.y,(float)c.z).setUv(u1,v0).setColor(color);
        v.addVertex(p,(float)d.x,(float)d.y,(float)d.z).setUv(u0,v0).setColor(color);
    }

    private static void coin(VertexConsumer v,PoseStack.Pose p,Vec3 at,Vec3 a,Vec3 b,double size){
        a=a.scale(size*.5);
        b=b.scale(size*.5);
        Vec3[] points={at.subtract(a).subtract(b),at.add(a).subtract(b),at.add(a).add(b),at.subtract(a).add(b)};
        for(int i=0;i<4;i++){
            var c=points[i];
            v.addVertex(p,(float)c.x,(float)c.y,(float)c.z).setColor(0xffffffff)
                    .setUv(i==0||i==3?0:1,i<2?1:0)
                    .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).setNormal(p,0,0,1);
        }
    }

    private SkillFx() {}
}

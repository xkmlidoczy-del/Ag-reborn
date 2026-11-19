package cn.academy.port.client.canon;

import cn.academy.port.VisualTier;
import cn.academy.port.VisualTierConfig;
import cn.academy.port.macro.MacroSkillRules;
import cn.academy.port.macro.MacroVfx;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Twelve distinct spatial compositions, sharing the existing bounded, shader-compatible drawing surface. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class MacroVfxClient {
    private record Show(MacroVfx.Event event,double start,Vec3 previous){}
    record VolumeSample(MacroVfx.Event event,double age,Vec3 centre,double radius,double opacity){}
    record NativeBody(UUID owner,String skill){}
    private static final List<Show> SHOWS=new ArrayList<>();
    private static ClientLevel world;
    private static final float[] WHITE={1,1,1};
    private MacroVfxClient(){}
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->MacroVfx.receiver=MacroVfxClient::receive);}
    }
    private static void sync(){var w=Minecraft.getInstance().level;if(world!=w){SHOWS.clear();world=w;}}
    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){SHOWS.clear();world=null;}
    static boolean any(){sync();return VisualTierConfig.tier()!=VisualTier.OFF&&!SHOWS.isEmpty();}
    static int count(){sync();return VisualTierConfig.tier()==VisualTier.OFF?0:SHOWS.size();}
    /** Old renderers keep native sound/input state and take over when the classic tier is selected. */
    public static boolean owns(String skill,UUID owner){
        sync();
        if(VisualTierConfig.tier()==VisualTier.OFF||world==null)return false;
        return SHOWS.stream().anyMatch(s->s.event.owner().equals(owner)&&s.event.skill().equals(skill)
                &&!expired(s,world.getGameTime()));
    }
    private static void receive(MacroVfx.Event v){
        sync();var mc=Minecraft.getInstance();var p=MacroSkillRules.find(v.skill());
        if(world==null||mc.player==null||p==null||!world.dimension().location().equals(v.dimension())||v.life()<1||v.life()>2400
                ||!MacroVfx.finite(v.from())||!MacroVfx.finite(v.to())||v.from().distanceToSqr(v.to())>192*192
                ||!Float.isFinite(v.actualRadius())||v.actualRadius()<0||v.actualRadius()>64
                ||!Float.isFinite(v.visualRadius())||v.visualRadius()<0||v.visualRadius()>64||v.shape()!=p.shape()
                ||MacroVfx.distanceSquared(mc.player.getEyePosition(),v.from(),v.to())>Math.pow((p.level()>=4||p.ultimate()?136:80)+v.visualRadius(),2))return;
        var actor=world.getEntity(v.entity());if(actor!=null&&!actor.getUUID().equals(v.owner()))return;
        double now=world.getGameTime();if(v.tick()>now+100||now>=v.tick()+v.life()||v.phaseStart()>v.tick())return;
        if(VisualTierConfig.tier()!=VisualTier.OFF)CanonPoseClient.accept(v);
        var prior=SHOWS.stream().filter(s->s.event.cast().equals(v.cast())).findFirst().orElse(null);
        if(v.phase()==MacroVfx.Phase.END&&prior!=null&&prior.event.phase()==MacroVfx.Phase.HIT)return;
        if(prior!=null&&v.phase()==MacroVfx.Phase.HIT&&v.actualRadius()==0&&prior.event.actualRadius()>0){
            // The live field and its per-target contacts have independent lifetimes.
            return;
        }
        SHOWS.removeIf(s->s.event.cast().equals(v.cast()));
        if(SHOWS.size()>=64){
            // Discard remote decoration first; the local cast and an explicit effect boundary have priority.
            SHOWS.sort(Comparator.comparingInt(s->(s.event.owner().equals(mc.player.getUUID())?2:0)+(s.event.actualRadius()>0?1:0)));
            SHOWS.removeFirst();
        }
        double began=v.phaseStart();
        SHOWS.add(new Show(v,began,prior==null?v.from():prior.event.from()));
    }
    private static boolean expired(Show s,double now){return now>=s.event.tick()+s.event.life()||now<s.start-100;}
    private static Vec3 fieldCentre(MacroVfx.Event v,float partial){
        if(v.actualRadius()>0&&Set.of("electron_missile","magnetic_domain","dark_construct","nitrogen_dome").contains(v.skill())){
            var actor=world.getEntity(v.entity());if(actor!=null&&actor.getUUID().equals(v.owner()))return actor.getPosition(partial);
        }
        if(v.boundary()==MacroVfx.Boundary.CURTAIN){
            var actor=world.getEntity(v.entity());
            if(actor!=null&&actor.getUUID().equals(v.owner()))return actor.getEyePosition(partial);
        }
        return v.actualRadius()>0||v.phase()==MacroVfx.Phase.HIT?v.to():v.from();
    }
    static List<VolumeSample> volumeSamples(double now){
        sync();SHOWS.removeIf(s->expired(s,now));var out=new ArrayList<VolumeSample>();
        float partial=(float)Math.clamp(now-Math.floor(now),0.0,1.0);
        for(var s:SHOWS){var v=s.event;var p=MacroSkillRules.find(v.skill());double age=Math.max(0,now-s.start);double remaining=v.tick()+v.life()-now;
            // This native beam already resolves the actual hand/view origin. Its dedicated material
            // is upgraded in ClassicMeltdownerBeamEffects; an eye-origin volume would duplicate it.
            if(v.skill().equals("meltdowner")||v.boundary()==MacroVfx.Boundary.CURTAIN
                    ||v.skill().equals("electron_missile")||p.category().equals("mental_out")
                    ||p.category().equals("teleporter")||p.category().equals("paper_psychokinesis")
                    ||SkillCinematicFx.usesNativeRoute(v.skill()))continue;
            if(!p.ultimate()||VisualTierConfig.tier()==VisualTier.LOW)continue;
            boolean releasePulse=v.skill().equals("float_release_field")&&v.phase()==MacroVfx.Phase.END;
            double opacity=Math.min(1,(age+1)/2)*Math.min(1,remaining/7);if(v.phase()==MacroVfx.Phase.END&&!releasePulse)opacity*=.3;
            Vec3 centre=releasePulse?v.to():fieldCentre(v,partial);
            if(v.skill().equals("plasma_cannon")&&v.phase()==MacroVfx.Phase.ACTIVE)centre=s.previous.lerp(v.from(),Math.clamp((now-v.tick())/4,0,1));
            if(v.shape()==MacroSkillRules.Shape.SELF&&v.phase()!=MacroVfx.Phase.HIT&&v.from().distanceToSqr(v.to())<.1){var actor=world.getEntity(v.entity());if(actor!=null&&actor.getUUID().equals(v.owner()))centre=actor.getPosition(partial).add(0,1,0);}
            double radius=p.ultimate()?v.visualRadius():Math.min(6,.8+p.level()*.8);
            if(v.shape()==MacroSkillRules.Shape.LINE&&v.from().distanceToSqr(v.to())>1)radius=.5+p.level()*.3;
            if(v.skill().equals("plasma_cannon")){
                radius=v.phase()==MacroVfx.Phase.HIT?v.actualRadius()*AgModernVfx.snapEase(age/4.0):5+Math.min(3,age*.09);
                if(v.phase()==MacroVfx.Phase.END)continue;
                opacity*=v.phase()==MacroVfx.Phase.HIT?.42:.54;
            }
            if(v.skill().equals("thunder_clap")){
                if(v.phase()!=MacroVfx.Phase.HIT||age>28)continue;
                radius=Math.min(radius,24);
                opacity*=.39;
            }
            if(opacity>.001)out.add(new VolumeSample(v,age,centre,radius,opacity));
        }return out;
    }
    static void draw(CanonFx.Canvas c,double now,int allowance,Set<NativeBody> nativeBodies){
        sync();if(world==null||VisualTierConfig.tier()==VisualTier.OFF)return;
        SHOWS.removeIf(s->expired(s,now));
        float partial=(float)Math.clamp(now-Math.floor(now),0.0,1.0);
        var tier=VisualTierConfig.tier();int detail=Math.min(tier==VisualTier.CINEMATIC?48:tier==VisualTier.STANDARD?32:16,Math.max(8,(allowance-40)/8));
        for(var show:List.copyOf(SHOWS)){
            if(c.available()==0)break;
            var v=show.event;var profile=MacroSkillRules.find(v.skill());double age=Math.max(0,now-show.start);
            double fade=Math.min(1,(age+1)/4)*Math.min(1,(v.tick()+v.life()-now)/10);
            boolean releasePulse=v.skill().equals("float_release_field")&&v.phase()==MacroVfx.Phase.END;
            if(v.phase()==MacroVfx.Phase.END&&!releasePulse)fade*=.35;
            if(fade<=0)continue;int before=c.left,scope=c.limit(allowance);
            try{
                Vec3 centre=releasePulse?v.to():fieldCentre(v,partial);
                if(v.skill().equals("plasma_cannon")&&v.phase()==MacroVfx.Phase.ACTIVE)
                    centre=show.previous.lerp(v.from(),Math.clamp((now-v.tick())/4,0,1));
                if(MacroVfx.distanceSquared(c.cam,v.from(),v.to())>Math.pow(v.visualRadius()+140,2))continue;
                if(v.actualRadius()==0&&v.shape()==MacroSkillRules.Shape.SELF&&v.phase()!=MacroVfx.Phase.HIT&&v.from().distanceToSqr(v.to())<.1){var actor=world.getEntity(v.entity());if(actor!=null&&actor.getUUID().equals(v.owner()))centre=actor.getPosition(partial).add(0,1,0);}
                float[] color=colour(profile.category());String texture="macro/"+profile.category();
                CanonUltimateFinales.draw(c, v, profile, centre, color, age, fade, tier, allowance);
                if(v.skill().equals("meltdowner"))continue;
                if(profile.category().equals("mental_out")&&v.life()>200&&age>80){
                    c.sprite(texture,false,centre,.5,age*.015,WHITE,(float)(fade*.25));
                    continue;
                }
                double radius=v.actualRadius()>0?v.actualRadius():profile.ultimate()?v.visualRadius():Math.min(v.visualRadius(),.8+profile.level()*.8);
                double grow=AgModernVfx.snapEase(age/4.0);
                grow=switch(v.phase()){
                    case CHARGE -> .68-.26*Math.clamp(age/Math.max(1,v.life()),0,1);
                    case ACTIVE -> .94+.06*Math.sin(age*.085);
                    case HIT -> grow;
                    case END -> .95;
                    case RELEASE -> 1;
                };
                double boundaryFade=v.phase()==MacroVfx.Phase.HIT?fade*Math.clamp((32-age)/12,0,1):fade;
                SkillCinematicFx.boundary(c,v,centre,color,age,boundaryFade);
                if(profile.ultimate()&&!AgVolumeRenderer.owns(v.cast())&&cn.academy.port.client.ShaderPackState.active()
                        &&!profile.category().equals("mental_out")&&!profile.category().equals("teleporter")&&!profile.category().equals("paper_psychokinesis")){
                    CanonIrisFallback.volumeSurrogate(c,new VolumeSample(v,age,centre,radius*grow,fade*.76),color);
                }
                if(SkillCinematicFx.centerpiece(c,v,centre,age,fade,detail))continue;
                SkillCinematicFx.accent(c,v,centre,color,age,fade,detail);
                if(nativeBodies.contains(new NativeBody(v.owner(),v.skill())))continue;
                if(AgVolumeRenderer.owns(v.cast())){
                    switch(profile.category()){
                        case "electromaster" -> lightning(c,centre,radius*grow,age,fade,color,detail);
                        case "vecmanip" -> vectors(c,centre,radius*grow,age,fade,color,detail);
                        case "aero_hand" -> vortex(c,centre,radius*grow,age*.04,fade,color,detail,false);
                        case "float_dial" -> pressure(c,centre,radius*grow,age*.04,fade,color,detail);
                        case "dark_matter" -> shards(c,centre,radius*grow,age*.04,fade,color,detail);
                        case "liquid_shadow" -> liquid(c,centre,radius*grow,age*.04,fade,color,detail);
                        case "hydro_hand" -> vortex(c,centre,radius*grow,age*.04,fade,color,detail,true);
                        default -> {}
                    }
                    continue;
                }
                if(age>80){
                    // A long control lease keeps a readable mark, not a full-screen opening flourish for two minutes.
                    c.sprite(texture,false,centre,.5,age*.015,WHITE,(float)(fade*.25));
                    continue;
                }
                if(v.shape()==MacroSkillRules.Shape.LINE&&v.actualRadius()==0&&v.from().distanceToSqr(v.to())>1
                        &&!profile.category().equals("mental_out")){
                    var axis=v.to().subtract(v.from()).normalize();var side=axis.cross(new Vec3(0,1,0));if(side.lengthSqr()<.01)side=new Vec3(1,0,0);else side=side.normalize();
                    for(int k=0;k<3;k++){
                        var pts=CanonFx.wave(v.from(),v.to(),detail,profile.ultimate()?1.1:.12,2+k,age*.16+k,side);
                        final double opacity=fade;
                        c.ribbon("streak",true,pts,u->(.08+.2*Math.sin(u*Math.PI))*(profile.ultimate()?3:1),u->opacity*.32,color,3,-age*.03);
                    }
                }
                double t=age*.04;
                switch(profile.category()){
                    case "electromaster" -> lightning(c,centre,radius*grow,age,fade,color,detail);
                    case "vecmanip" -> vectors(c,centre,radius*grow,age,fade,color,detail);
                    case "meltdowner" -> beam(c,v,centre,age,fade,color,detail);
                    case "teleporter" -> spatial(c,centre,radius*grow,t,fade,color,detail);
                    case "aero_hand" -> vortex(c,centre,radius*grow,t,fade,color,detail,false);
                    case "float_dial" -> pressure(c,centre,radius*grow,t,fade,color,detail);
                    case "paper_psychokinesis" -> paper(c,centre,radius*grow,t,fade,detail);
                    case "liquid_shadow" -> liquid(c,centre,radius*grow,t,fade,color,detail);
                    case "hydro_hand" -> vortex(c,centre,radius*grow,t,fade,color,detail,true);
                    case "dark_matter" -> shards(c,centre,radius*grow,t,fade,color,detail);
                    case "nitrogen_armor" -> condensation(c,centre,radius*grow,t,fade,color,detail);
                    default -> {}
                }
                double size=profile.ultimate()?Math.min(7,1+age*.12):.45+profile.level()*.18;
                double near=Math.clamp((Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(centre)-1)/5,0,1);
                c.sprite(texture,false,centre,size,t*.2,WHITE,(float)(fade*.48*near));
            }finally{c.restore(scope);c.record("macro:"+v.skill()+":"+v.cast(),before);}
        }
    }
    static boolean drawsBoundary(String skill,UUID owner,Vec3 centre){
        if(VisualTierConfig.tier()==VisualTier.OFF||world==null)return false;
        double now=world.getGameTime();
        return SHOWS.stream().anyMatch(s->s.event.skill().equals(skill)&&s.event.owner().equals(owner)&&s.event.boundary()!=MacroVfx.Boundary.NONE
                &&!expired(s,now)&&fieldCentre(s.event,1f).distanceToSqr(centre)<1);
    }
    private static float[] colour(String cat){return switch(cat){
        case "electromaster"->new float[]{.42f,.82f,1};case "meltdowner"->new float[]{.3f,1,.28f};
        case "vecmanip"->new float[]{.72f,.82f,1};case "teleporter"->new float[]{.65f,.48f,1};
        case "mental_out"->new float[]{1,.65f,.8f};case "aero_hand"->new float[]{.7f,1,.92f};
        case "float_dial"->new float[]{.78f,.7f,1};case "paper_psychokinesis"->new float[]{1,.93f,.76f};
        case "liquid_shadow"->new float[]{.3f,.45f,.72f};case "hydro_hand"->new float[]{.35f,.78f,1};
        case "dark_matter"->new float[]{.92f,.83f,1};default->new float[]{.7f,.92f,1};};}
    private static Vec3 radial(double a,double r,double y){return new Vec3(Math.cos(a)*r,y,Math.sin(a)*r);}
    private static void boundary(CanonFx.Canvas c,Vec3 p,double r,float[] color,float alpha,int segments){
        AgModernVfx.planarRing(c,p.add(0,.08,0),new Vec3(1,0,0),new Vec3(0,0,1),r,0,segments,.11,color,alpha,true);
    }
    private static void lightning(CanonFx.Canvas c,Vec3 p,double r,double age,double e,float[] color,int n){
        AgModernVfx.calculationArray(c,p.add(0,.14,0),new Vec3(0,1,0),Math.min(r,18.0),"electromaster",age,e*.68,Math.min(32,n));
        long kf=(long)Math.floor(age/2.0);
        for(int k=0;k<6;k++){
            double a=k*Math.PI/3+kf*.23;
            Vec3 end=p.add(radial(a,r,Math.min(18,r*.28)));
            AgModernVfx.fractalArc(c,p.add(0,.35,0),end,3,kf*31+k*97L,1.0,Math.clamp(r*.025,.09,.28),color,e*.82,r>=4.0);
        }
        c.sprite("macro/electromaster",true,p.add(0,Math.min(18,r*.36),0),Math.min(10,r*.20),0,WHITE,(float)(e*.45));
    }
    private static void vectors(CanonFx.Canvas c,Vec3 p,double r,double age,double e,float[] color,int n){
        AgModernVfx.calculationArray(c,p.add(0,.16,0),new Vec3(0,1,0),Math.min(r,20.0),"vecmanip",age,e*.72,Math.min(32,n));
        for(int k=0;k<8;k++){double a=k*Math.PI/4+age*.012;Vec3 axis=radial(a,1,0);var pts=List.of(p.add(axis.scale(r)),p.add(axis.scale(r*.35)).add(0,2,0),p.add(0,3.2,0));
            c.ribbon("streak",true,pts,u->.14+u*.22,u->e*.48,color,3,age*.03);
            Vec3 tip=p.add(axis.scale(r*(1-(age%16)/16)));c.sprite("macro/vecmanip",false,tip,Math.max(.22,r*.036),a,WHITE,(float)(e*.52));}
    }
    private static void beam(CanonFx.Canvas c,MacroVfx.Event v,Vec3 p,double age,double e,float[] color,int n){
        if(v.to().distanceToSqr(v.from())>1&&!v.skill().equals("meltdowner")){
            double beamR=v.actualRadius()>0?v.actualRadius():.55;
            if(AgEffectMaterials.beams())c.energyStream(v.from(),v.to(),Math.min(.42,beamR*.32),color,e*.76);
            else{var pts=List.of(v.from(),v.to());c.ribbon("macro/beam_tile",true,pts,u->1.3,u->e*.7,WHITE,Math.max(1,v.from().distanceTo(v.to())/12),-age*.04);}
            AgModernVfx.grandBeamHelices(c,v.from(),v.to(),beamR,"meltdowner",color,age,e*.72,Math.min(28,n));
        }
        for(int k=0;k<n/2;k++){double a=k*2.39996+age*.03;Vec3 at=p.add(radial(a,v.visualRadius()*.5*(.3+(k%7)/10.0),k%4));c.sprite("glow",true,at,.1+(k%3)*.07,0,color,(float)(e*.5));}
    }
    private static void spatial(CanonFx.Canvas c,Vec3 p,double r,double t,double e,float[] color,int n){
        AgModernVfx.calculationArray(c,p.add(0,.9,0),new Vec3(0,1,0),r,"teleporter",t*25.0,e*.74,Math.min(32,n));
        for(int k=0;k<6;k++){double a=k*Math.PI/3-t*.4;Vec3 at=p.add(radial(a,r*.78,1+(k%3)*r*.15));double w=Math.max(.22,r*.075);
            Vec3 x=radial(a+Math.PI/2,w,0),y=new Vec3(0,w*2,0);
            c.ribbon("streak",true,List.of(at.subtract(x).subtract(y),at.add(x).subtract(y),at.add(x).add(y),at.subtract(x).add(y),at.subtract(x).subtract(y)),u->.07,u->e*.62,color,1,0);
            c.sprite("macro/teleporter",false,at,w,t*.1,WHITE,(float)(e*.45));}
    }
    private static void vortex(CanonFx.Canvas c,Vec3 p,double r,double t,double e,float[] color,int n,boolean water){
        AgModernVfx.calculationArray(c,p.add(0,.12,0),new Vec3(0,1,0),Math.min(r,22.0),water?"hydro_hand":"aero_hand",t*25.0,e*.62,Math.min(30,n));
        for(int k=0;k<(water?4:5);k++){var pts=new ArrayList<Vec3>();for(int i=0;i<=n;i++){double u=i/(double)n,a=t*2.2+k*Math.PI*2/(water?4:5)+u*6.28;pts.add(p.add(radial(a,r*(.15+.85*u),u*Math.min(28,r*.52))));}
            c.ribbon(water?"macro/hydro_hand":"streak",water,pts,u->water?.5+u*1.5:.18+u*.42,u->e*(water?.50:.44),color,4,-t*.3);}
    }
    private static void pressure(CanonFx.Canvas c,Vec3 p,double r,double t,double e,float[] color,int n){
        for(int k=0;k<3;k++)boundary(c,p.add(0,k*1.4+Math.sin(t+k)*.3,0),r*(.4+k*.25),color,(float)(e*.5),n);
        // An inner pressure stack stays readable from within a large field;
        // the outer rim alone falls onto the first-person horizon.
        double inner=Math.min(5,r*.18);
        for(int k=0;k<3;k++)boundary(c,p.add(0,.4+k*.85+Math.sin(t*1.4+k)*.16,0),inner*(.7+k*.22),color,(float)(e*.65),Math.max(12,n/2));
        for(int k=0;k<n/2;k++){double a=k*2.39996;Vec3 at=p.add(radial(a,r*Math.sqrt((k+1.0)/(n/2)),1+(k%5)*1.8+Math.sin(t+k)));
            c.sprite("macro/float_dial",false,at,.3+(k%3)*.2,t*.1,WHITE,(float)(e*.42));}
    }
    private static void paper(CanonFx.Canvas c,Vec3 p,double r,double t,double e,int n){
        for(int k=0;k<n;k++){double a=k*2.39996+t*.35;Vec3 at=p.add(radial(a,r*Math.sqrt((k+1.0)/n),1+(k%7)*1.3+Math.sin(t+k)));
            double size=Math.clamp(r*.027,.35,1.7);
            double fold=Math.sin(t*2+k)*.55;
            c.fold("macro/paper_psychokinesis",at,radial(a,1,0),new Vec3(0,1,0),size,size*.8,fold,WHITE,(float)(e*.9));
            c.fold("macro/paper_psychokinesis",at,radial(a,1,0),new Vec3(0,-1,0),size,size*.8,-fold,WHITE,(float)(e*.9));}
    }
    private static void liquid(CanonFx.Canvas c,Vec3 p,double r,double t,double e,float[] color,int n){
        for(int k=0;k<7;k++){var pts=new ArrayList<Vec3>();for(int i=0;i<12;i++){double u=i/11.0;pts.add(p.add(radial(k*Math.PI*2/7+t*.1+u*.6,r*u,.15+Math.sin(u*Math.PI)*1.2)));}
            c.ribbon("macro/liquid_shadow",false,pts,u->.4+u*1.3,u->e*.65,WHITE,1,-t*.1);}
    }
    private static void shards(CanonFx.Canvas c,Vec3 p,double r,double t,double e,float[] color,int n){
        for(int k=0;k<n/2;k++){double a=k*2.39996+t*.12;Vec3 at=p.add(radial(a,r*(.3+.7*(k+1.0)/(n/2)),1+(k%5)*2));
            double size=Math.max(.3,r*.022)*(1+(k%3)*.2);Vec3 tip=at.add(0,size*2,0),bottom=at.add(0,-size,0);
            for(int face=0;face<4;face++){
                Vec3 b=at.add(radial(a+face*Math.PI/2,size,0)),d=at.add(radial(a+(face+1)*Math.PI/2,size,0));
                c.quad("macro/dark_matter",false,b,d,tip,tip,0,0,1,1,1,1,1,(float)(e*.82));
                c.quad("macro/dark_matter",false,d,b,bottom,bottom,0,0,1,1,.65f,.6f,.8f,(float)(e*.82));
            }}
    }
    private static void condensation(CanonFx.Canvas c,Vec3 p,double r,double t,double e,float[] color,int n){
        AgModernVfx.fresnelDome(c,p,r,r,color,t*25.0,e*.48,7,16);
        AgModernVfx.calculationArray(c,p.add(0,.12,0),new Vec3(0,1,0),Math.min(r,20.0),"nitrogen_armor",t*25.0,e*.60,Math.min(28,n));
        for(int k=0;k<n/2;k++){double a=k*2.39996+t*.04;Vec3 at=p.add(radial(a,r,1+(k%4)*1.8));c.sprite("macro/nitrogen_armor",false,at,Math.max(.5,r*.08),a,WHITE,(float)(e*.28));}
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent e){
        var root=Commands.literal("agfx");
        for(var entry:Map.of("high",VisualTier.CINEMATIC,"standard",VisualTier.STANDARD,"performance",VisualTier.LOW,"classic",VisualTier.OFF).entrySet())
            root.then(Commands.literal(entry.getKey()).executes(c->{VisualTierConfig.TIER.set(entry.getValue());VisualTierConfig.SPEC.save();c.getSource().sendSuccess(()->Component.translatable("academy.macro.quality",entry.getKey()),false);return 1;}));
        e.getDispatcher().register(root);
    }
}

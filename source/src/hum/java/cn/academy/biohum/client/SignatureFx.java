package cn.academy.biohum.client;

import cn.academy.biohum.BioHum;
import cn.academy.biohum.CastMotion;
import cn.academy.biohum.Payloads;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Choreography for skills that have identifiable geometry beyond their per-skill atlas. */
final class SignatureFx {
    private enum Art { DISCHARGE, WIND, STEEL, RUNE, FRACTURE, SECTION, STAR, FROST, ROSE }
    private record Material(RenderType body, RenderType glow) {}
    private static final Map<Art,Material> ART=new EnumMap<>(Art.class);
    private static final Vec3 X=new Vec3(1,0,0),Y=new Vec3(0,1,0),Z=new Vec3(0,0,1);
    private static final int SILVER=0xcadcff, AMBER=0xffbd58, ICE=0xa0e7ff;
    static {
        for(var art:Art.values()){
            String name=art.name().toLowerCase(java.util.Locale.ROOT);
            ART.put(art,new Material(layer(name,false),layer(name+"_glow",true)));
        }
    }
    private static RenderType layer(String id,boolean glow){
        return SkillRenderType.material(ResourceLocation.fromNamespaceAndPath(BioHum.ID,
                "textures/skill/layers/"+id+".png"),glow);
    }

    static boolean draw(Payloads.SkillCue event,MultiBufferSource buffers,PoseStack.Pose pose,
                        Vec3 from,Vec3 to,Vec3 right,Vec3 up,CastMotion.Style style,
                        float age,float alpha,int layers) {
        var shot=new Shot(event,buffers,pose,from,to,right,up,style,age,alpha,layers);
        return switch(event.skill()){
            case "railgun" -> railgun(shot);
            case "wind_plasma" -> plasma(shot);
            case "vector_guard" -> reflect(shot);
            case "seven_flash" -> wire(shot);
            case "yuisen","saint_intercept","close_kanzaki_strike","close_kanzaki_counter",
                 "ascalon_cleave","ascalon_break","hunting_sword","amakusa_spear" -> weapon(shot);
            case "rune_summon","rune_relay","rune_fire_screen" -> summon(shot);
            case "sheol_fear" -> voice(shot);
            case "imagine_breaker" -> negate(shot);
            case "mental_stop","mental_assist","mental_retreat","mental_memory","mental_stinger","heart_distance" -> mind(shot);
            case "teleport_self","teleport_carry","teleport_rescue","coordinate_shift","coordinate_self",
                 "world_rejecter","world_reentry","almighty_position" -> transfer(shot);
            case "aim_track","aim_reacquire","aim_share","sister_signal","network_coordination","network_alert",
                 "worst_signal","aim_copy_discharge","rensa_scan" -> signal(shot);
            case "meltdowner","meltdowner_sweep","meltdowner_scatter","meltdowner_aim_relay","wounded_beam" -> melt(shot);
            case "nitrogen_armor","nitrogen_lance","armor_counter","amazing_punch","guts_guard",
                 "hound_feint","norse_throne","saint_mercy" -> pressure(shot);
            case "venus_reflection","precedence_cut","saint_break","thunder_blade","thunder_arc_parry",
                 "holy_right","holy_right_intercept","holy_right_close","lotus_wand" -> directed(shot);
            case "curtana_slice","curtana_interpose","curtana_territory","bethlehem_star",
                 "wonderland_corridor","wonderland_link","carbon_form","carbon_barrier","dark_forge",
                 "ellis_command","priest_earth_wave" -> structure(shot);
            case "rose_spell","rose_cross_flash","leyline_trap","shorthand_cast","shorthand_ice","shorthand_flare",
                 "ars_magna","ars_magna_shape","ars_magna_mute","apostles_cross","deep_blood_seal","cold_mistress",
                 "divine_punishment","triple_loading","aiwass_manifest","aleister_staff","aleister_karma",
                 "aleister_spiritual_trip","aleister_spiritual_rapier","adikalika","coronzon_manifest" -> ritual(shot);
            default -> false;
        };
    }

    private record Shot(Payloads.SkillCue cue,MultiBufferSource buffers,PoseStack.Pose pose,Vec3 from,Vec3 to,
                        Vec3 right,Vec3 up,CastMotion.Style style,float t,float a,int layers) {
        int detail(int standard,int showcase){return layers==0?0:layers==1?standard:showcase;}
        Vec3 axis(){
            Vec3 delta=to.subtract(from);
            return delta.lengthSqr()<1e-6?Z:delta.normalize();
        }
        VertexConsumer ink(){return buffers.getBuffer(SkillFx.material(cue.skill(),false));}
        void line(Vec3 a,Vec3 b,double w,int color,float gain){
            SkillFx.ribbon(ink(),pose,a,b,w,1,color,this.a*gain);
        }
        void arc(Vec3 at,Vec3 x,Vec3 y,double radius,double phase,double length,double width,int color,float gain,int segments){
            SkillFx.arc(ink(),pose,at,x,y,radius,phase,length,width,color,a*gain,segments);
        }
        void sprite(Art art,Vec3 at,Vec3 x,Vec3 y,double w,double h,float gain){
            var texture=ART.get(art);
            SkillFx.fullSprite(buffers.getBuffer(texture.body),pose,at,x,y,w,h,a*gain);
            if(layers>0)SkillFx.fullSprite(buffers.getBuffer(texture.glow),pose,at,x,y,w,h,a*gain*.43f);
        }
        void flare(Art art,Vec3 at,double width,double height,float gain){
            sprite(art,at,right,up,width,height,gain);
        }
        void radials(Vec3 at,int count,double length,int color,float gain){
            for(int i=0;i<count;i++){
                double phase=i*2.399963+cue.step()*.41;
                Vec3 unit=new Vec3(Math.cos(phase),.30+i%4*.17,Math.sin(phase));
                Vec3 dust=at.add(unit.scale(.12+length*t*.75));
                line(dust,dust.add(unit.scale(length*(1-t)*(.14+.09*(i%3)))),.023,color,gain);
            }
        }
    }

    private static boolean railgun(Shot s){
        switch(s.cue.cue()){
            case BEAM -> {
                double distance=s.from.distanceTo(s.to);
                if(distance<.01)return true;
                Vec3 direction=s.axis(), side=SkillFx.normal(direction), vertical=direction.cross(side);
                float expansion=Math.min(1,s.t*6);
                Vec3 front=s.from.lerp(s.to,expansion);
                s.line(s.from,front,.082*(1-s.t*.75),0xfff1c9,.97f);
                s.line(s.from,front,.255*(1-s.t*.74),0xff912b,.76f);
                s.line(s.from.lerp(front,s.t*.45),front,.035,0xffffff,.98f);
                for(int i=0;i<s.detail(4,7);i++){
                    double step=.32+i*.66+s.t*4.1;
                    if(step>distance*expansion)break;
                    Vec3 ring=s.from.add(direction.scale(step));
                    s.arc(ring,side,vertical,.19+step*.032+s.t*.47,i*2.03+s.t,5.65,.025,AMBER,.87f,16);
                }
                s.flare(Art.DISCHARGE,front,.63+s.t*.53,.18+s.t*.09,.92f);
            }
            case RELEASE -> s.flare(Art.DISCHARGE,s.from,.52,.17,.84f);
            case IMPACT,WALL,TERRAIN -> {
                double growth=.22+s.t*.6;
                s.flare(Art.DISCHARGE,s.to,growth,growth*.41,.83f);
                s.radials(s.to,5+s.detail(5,9),1.8,AMBER,.74f);
            }
            default -> {return false;}
        }
        return true;
    }

    private static boolean plasma(Shot s){
        switch(s.cue.cue()){
            case BEAM -> {
                Vec3 axis=s.axis(),side=SkillFx.normal(axis),up=axis.cross(side);
                s.line(s.from,s.to,.42*(1-s.t*.68),0xe5fbff,.93f);
                s.line(s.from,s.to,.83*(1-s.t*.79),0x62bccf,.59f);
                SkillFx.electric(s.ink(),s.pose,s.from,s.to,side,up,.34,s.t,ICE,s.a*.77f);
                for(int i=0;i<s.detail(3,5);i++){
                    double q=(.14+i*.17+s.t*.75)%1;
                    s.arc(s.from.lerp(s.to,q),side,up,.58+q*.39,i+s.t*3,3.9,.055,ICE,.53f,15);
                }
            }
            case FIELD -> {
                double radius=s.cue.size()*Math.min(1,.13+s.t*2);
                s.sprite(Art.WIND,s.to.add(0,.11,0),X,Z,radius,radius,.56f);
                for(int i=0;i<2+s.detail(1,2);i++){
                    double angle=i*2.14+s.t;
                    Vec3 centre=s.to.add(0,.08+i*.09+s.t*.25,0);
                    s.arc(centre,X,Z,radius*(1-i*.072),angle,2.55,.12,ICE,.88f-i*.13f,19);
                }
                s.radials(s.to,s.detail(7,13),radius*.56,ICE,.51f);
            }
            case CANCEL -> {
                for(int i=0;i<4;i++){
                    double phase=i*Math.PI*.5+s.t*3;
                    Vec3 outside=s.from.add(Math.cos(phase)*s.t*1.96,.21*Math.sin(i*2),Math.sin(phase)*s.t*1.96);
                    s.flare(Art.WIND,outside,.52-s.t*.29,.38-s.t*.25,.68f);
                }
            }
            default -> {return false;}
        }
        return true;
    }

    private static boolean reflect(Shot s){
        if(s.cue.cue()!=Payloads.Cue.REFLECT)return false;
        Vec3 deflection=s.axis();
        Vec3 edge=SkillFx.normal(deflection);
        Vec3 vertical=deflection.cross(edge);
        double r=.27+s.t*.95;
        for(int i=0;i<6;i++) {
            double a=i*Math.PI/3+s.t*.4,b=a+Math.PI/3;
            Vec3 start=s.from.add(edge.scale(Math.cos(a)*r)).add(vertical.scale(Math.sin(a)*r));
            Vec3 end=s.from.add(edge.scale(Math.cos(b)*r)).add(vertical.scale(Math.sin(b)*r));
            s.line(start,end,.023,SILVER,.88f);
        }
        if(s.from.distanceToSqr(s.to)>1e-5){
            s.line(s.from,s.from.lerp(s.to,.32),.063,0xc9f2ff,.85f);
            if(s.cue.step()==0)s.line(s.from,s.to,.04,0xffd9ea,.82f);
        }
        s.flare(Art.SECTION,s.from,.41+s.t*.36,.38+s.t*.37,.76f);
        return true;
    }

    private static boolean wire(Shot s){
        switch(s.cue.cue()){
            case BEAM -> {
                s.line(s.from,s.to,.021,0xf5fdff,.97f);
                var pulse=s.from.lerp(s.to,Math.min(1,.14+s.t*1.01));
                s.flare(Art.STEEL,pulse,.24,.10,.92f);
            }
            case IMPACT,WALL -> {
                s.radials(s.to,3,.42,0xddf5ff,.84f);
                s.flare(Art.STEEL,s.to,.32+s.t*.21,.19,.89f);
            }
            case RELEASE -> s.flare(Art.STEEL,s.from,.18,.095,.5f);
            default -> {return false;}
        }
        return true;
    }

    private static boolean weapon(Shot s){
        switch(s.cue.cue()){
            case BEAM -> {
                // From and to are consecutive sampled blade tips, checked against collision.
                s.line(s.from,s.to,.072*(1-s.t*.72),0xe2eaff,.95f);
                s.line(s.from,s.to,.021,0xffffff,.98f);
                if(s.layers>0)s.line(s.from.add(0,-.10*s.t,0),s.to.add(0,-.16*s.t,0),.073,SILVER,.44f);
            }
            case RELEASE -> {
                if(s.from.distanceToSqr(s.to)<.04)return false;
                s.line(s.from,s.from.lerp(s.to,.62),.031,0xf3f6ff,.60f);
            }
            case IMPACT,WALL,PARRY -> {
                boolean heavy=s.cue.skill().startsWith("ascalon");
                s.flare(Art.STEEL,s.to,(heavy?.59:.31)+s.t*.22,.24+s.t*.15,.95f);
                s.radials(s.to,4+s.detail(3,5),heavy?1.24:.63,
                        s.cue.cue()==Payloads.Cue.WALL?AMBER:SILVER,.91f);
            }
            default -> {return false;}
        }
        return true;
    }

    private static boolean summon(Shot s){
        switch(s.cue.cue()){
            case FIELD -> {
                double width=Math.min(1.8,.74+s.cue.size()*.23+s.t*.22);
                s.sprite(Art.RUNE,s.to.add(0,.06,0),X,Z,width,width,.88f);
                if(s.layers>0)s.flare(Art.DISCHARGE,s.to.add(0,.18+s.t*.72,0),.25,.42,.57f);
            }
            case BEAM -> {
                Vec3 head=s.from.lerp(s.to,Math.min(1,s.t*3.4));
                s.line(s.from,head,.09*(1-s.t*.4),0xff7d2d,.83f);
                s.flare(Art.DISCHARGE,head,.35,.34,.76f);
            }
            case CANCEL -> {
                s.sprite(Art.RUNE,s.from.add(0,.05,0),X,Z,.74,.74,.72f);
                s.radials(s.from,6,1.6,0xff852f,.83f);
            }
            default -> {return false;}
        }
        return true;
    }

    private static boolean voice(Shot s){
        if(s.cue.cue()!=Payloads.Cue.BEAM)return false;
        Vec3 axis=s.axis(),x=SkillFx.normal(axis),y=axis.cross(x);
        for(int i=0;i<4+s.detail(2,3);i++){
            double q=(i*.17+s.t*.76)%1;
            s.arc(s.from.lerp(s.to,q),x,y,.16+q*.67,s.t+i*.98,4.86,.027,0xd5c2ef,.86f,15);
        }
        return true;
    }

    private static boolean negate(Shot s){
        if(s.cue.cue()!=Payloads.Cue.CANCEL&&s.cue.cue()!=Payloads.Cue.IMPACT)return false;
        s.flare(Art.FRACTURE,s.from,.43*(1-s.t*.62),.43*(1-s.t*.62),.86f);
        s.radials(s.from,5+s.detail(3,5),.65,0xdce5f0,.77f);
        return true;
    }

    private static boolean mind(Shot s){
        if(s.cue.cue()!=Payloads.Cue.IMPACT)return false;
        if(s.cue.skill().equals("mental_stinger")||s.cue.skill().equals("mental_memory")){
            for(int i=-1;i<=1;i++){
                Vec3 at=s.to.add(0,i*.13,0).add(s.right.scale(Math.sin(i+s.t*10)*.17));
                s.line(at.add(s.right.scale(-.34)),at.add(s.right.scale(.34)),.014,0xe5b2ee,.83f);
            }
        }else if(s.cue.skill().equals("heart_distance")){
            for(int i=-2;i<=2;i++){
                Vec3 at=s.to.add(s.right.scale(i*.13*(1-s.t*.44)));
                s.line(at.add(0,.04,0),at.add(0,.17,0),.013,0xffcad1,.81f);
            }
        }else{
            if(s.cue.subject()!=null)return true;
            double side=.126*(1-s.t*.25);
            s.flare(Art.STAR,s.to.add(s.right.scale(-.085)),side,side,.86f);
            s.flare(Art.STAR,s.to.add(s.right.scale(.085)),side,side,.86f);
        }
        return true;
    }

    static void stars(PoseStack.Pose pose,MultiBufferSource buffers,float alpha){
        var layer=buffers.getBuffer(ART.get(Art.STAR).body);
        for(int side:new int[]{-1,1})SkillFx.fullSprite(layer,pose,new Vec3(.092*side,-.212,-.269),
                X,Y.scale(-1),.061,.063,alpha*.84f);
    }

    private static boolean transfer(Shot s){
        if(s.cue.cue()!=Payloads.Cue.MOVE&&s.cue.cue()!=Payloads.Cue.CANCEL)return false;
        boolean coordinates=s.cue.skill().startsWith("coordinate");
        boolean exile=s.cue.skill().startsWith("world_");
        double extent=Math.min(2.1,Math.max(.33,s.cue.size()*.47));
        Art art=exile?Art.FRACTURE:Art.SECTION;
        if(!coordinates){
            s.flare(art,s.from,extent*(1-s.t*.65),extent,.76f);
            if(s.from.distanceToSqr(s.to)>.01)s.flare(art,s.to,extent*(.42+s.t*.58),extent,.85f);
            return true;
        }
        for(var centre:new Vec3[]{s.from,s.to}){
            for(int i=0;i<4;i++){
                double angle=.785398+i*Math.PI*.5;
                Vec3 corner=centre.add(Math.cos(angle)*extent,-extent*.72,Math.sin(angle)*extent);
                s.line(corner,corner.add(0,extent*1.44,0),.018,0xfabaf4,.88f);
                s.line(corner,corner.lerp(centre,.31).add(0,-extent*.22,0),.019,0xfabaf4,.78f);
            }
        }
        return true;
    }

    private static boolean signal(Shot s){
        if(s.cue.cue()!=Payloads.Cue.MOVE&&s.cue.cue()!=Payloads.Cue.BEAM)return false;
        int color=s.cue.skill().startsWith("aim")?0xec98da:0x94dafa;
        if(s.cue.step()==1)color=0xb8fff2;
        for(int i=0;i<3+s.detail(2,5);i++){
            double at=(s.t*.8+i*.21)%1;
            var chip=s.from.lerp(s.to,at);
            s.line(chip,s.from.lerp(s.to,Math.min(1,at+.036)),.027,color,.88f);
        }
        s.arc(s.to,s.right,s.up,.24,Math.PI*.11,1.37,.021,color,.82f,8);
        s.arc(s.to,s.right,s.up,.24,Math.PI*1.11,1.37,.021,color,.82f,8);
        return true;
    }

    private static boolean melt(Shot s){
        if(s.cue.cue()==Payloads.Cue.BEAM){
            var axis=s.axis();
            var tangent=SkillFx.normal(axis);
            for(int i=-1;i<=1;i++){
                var edge=tangent.scale(i*.047);
                s.line(s.from.add(edge),s.to.add(edge),i==0?.059:.021,i==0?0xf6fff1:0xaaff82,.96f);
            }
            if(s.layers>0)s.line(s.from,s.to,.20*(1-s.t*.78),0x75ffc2,.58f);
            return true;
        }
        if(s.cue.cue()!=Payloads.Cue.WALL&&s.cue.cue()!=Payloads.Cue.IMPACT
                &&s.cue.cue()!=Payloads.Cue.TERRAIN)return false;
        s.radials(s.to,5+s.detail(3,7),1.05,0xd4ff9e,.87f);
        s.flare(Art.SECTION,s.to,.32+.3*s.t,.23+.13*s.t,.69f);
        return true;
    }

    private static boolean pressure(Shot s){
        if(s.cue.cue()!=Payloads.Cue.BEAM&&s.cue.cue()!=Payloads.Cue.FIELD&&
                s.cue.cue()!=Payloads.Cue.IMPACT&&s.cue.cue()!=Payloads.Cue.CANCEL)return false;
        boolean cone=s.cue.skill().equals("nitrogen_lance")||s.cue.skill().equals("amazing_punch");
        double radius=s.cue.cue()==Payloads.Cue.FIELD?Math.min(s.cue.size(),1.9):.75;
        Vec3 at=s.to;
        for(int i=0;i<(cone?4:2);i++){
            var center=cone?s.from.lerp(s.to,(i*.19+s.t*.38)%1):at.add(0,i*.12,0);
            s.arc(center,s.right,s.up,radius*(.28+s.t*.74+i*.10),i*Math.PI+s.t,
                    cone?4.1:2.17,.035,cone?0xe1fbff:s.style.accent(),.91f,17);
        }
        if(s.cue.cue()==Payloads.Cue.IMPACT){
            s.flare(Art.WIND,at,.29+.36*s.t,.24+.29*s.t,.63f);
            s.radials(at,4+s.detail(3,6),.79,s.style.core(),.69f);
        }
        return true;
    }

    private static boolean directed(Shot s){
        boolean holy=s.cue.skill().startsWith("holy_right");
        boolean lance=s.cue.skill().equals("saint_break");
        boolean planar=s.cue.skill().equals("precedence_cut");
        if(s.cue.cue()==Payloads.Cue.BEAM&&!holy){
            s.line(s.from,s.to,lance?.103:.048,s.style.core(),.91f);
            Vec3 side=SkillFx.normal(s.axis());
            if(lance || planar)
                s.line(s.from.add(side.scale(.093)),s.to,.038,s.style.accent(),.70f);
            if(lance)s.flare(Art.SECTION,s.from.lerp(s.to,Math.min(1,s.t*3)),.43,.29,.69f);
            if(s.cue.skill().startsWith("thunder")){
                SkillFx.electric(s.ink(),s.pose,s.from,s.to,side,s.axis().cross(side),.22,s.t,0x8cddff,s.a*.81f);
            }
            return true;
        }
        if(s.cue.cue()!=Payloads.Cue.IMPACT&&s.cue.cue()!=Payloads.Cue.WALL&&s.cue.cue()!=Payloads.Cue.CANCEL)return false;
        s.flare(holy||planar?Art.SECTION:Art.DISCHARGE,s.to,(holy?.93:.37)+s.t*.36,.30+s.t*.30,.88f);
        s.radials(s.to,5+s.detail(4,8),holy?2.0:.72,s.style.core(),.82f);
        return true;
    }

    private static boolean structure(Shot s){
        String id=s.cue.skill();
        if(s.cue.cue()==Payloads.Cue.BEAM&&id.equals("curtana_slice")){
            Vec3 from=s.from.lerp(s.to,s.t*.41);
            double height=.81*(1-s.t*.42);
            s.line(from,s.to,.06,0xfff3ca,.96f);
            s.line(from.add(0,height,0),s.to.add(0,-height,0),.14,AMBER,.62f);
            s.flare(Art.SECTION,s.to,.96,.76,.81f);
            return true;
        }
        if(s.cue.cue()==Payloads.Cue.TERRAIN){
            Art art=id.contains("carbon")?Art.FRACTURE:id.contains("wonderland")?Art.FROST:Art.SECTION;
            s.flare(art,s.from.add(0,.25,0),.29+s.t*.50,.17+s.t*.48,.74f);
            if(s.from.distanceToSqr(s.to)>.07)
                s.flare(art,s.to.add(0,.25,0),.29+s.t*.50,.17+s.t*.48,.74f);
            return true;
        }
        if(s.cue.cue()!=Payloads.Cue.FIELD||id.equals("bethlehem_star"))return false;
        var r=s.cue.size();
        s.arc(s.to.add(0,.09,0),X,Z,r,0,Math.PI*2,.063,s.style.accent(),.73f,32);
        if(id.contains("wonderland"))s.sprite(Art.FROST,s.to.add(0,.13,0),X,Z,r*.42,r*.42,.54f);
        return true;
    }

    private static boolean ritual(Shot s){
        String id=s.cue.skill();
        Art art=id.contains("rose")||id.equals("leyline_trap")?Art.ROSE:
                id.contains("ice")||id.equals("cold_mistress")?Art.FROST:
                id.startsWith("aleister")||id.equals("coronzon_manifest")||id.equals("adikalika")?Art.FRACTURE:
                id.startsWith("ars_magna")||id.equals("divine_punishment")?Art.SECTION:Art.RUNE;
        if(s.cue.cue()==Payloads.Cue.FIELD){
            double size=s.cue.size();
            s.sprite(art,s.to.add(0,.10,0),X,Z,size*(.16+.68*s.t),size*(.16+.68*s.t),.86f);
            if(size>2)s.arc(s.to.add(0,.13,0),X,Z,size,0,6.283,.044,s.style.accent(),.68f,25);
            return true;
        }
        if(s.cue.cue()==Payloads.Cue.TERRAIN){
            s.flare(art,s.from.add(0,.2,0),.31,.31,.82f);
            if(s.from.distanceToSqr(s.to)>.04)s.flare(art,s.to.add(0,.2,0),.31,.31,.82f);
            return true;
        }
        if(s.cue.cue()!=Payloads.Cue.IMPACT&&s.cue.cue()!=Payloads.Cue.CANCEL)return false;
        Vec3 at=s.cue.cue()==Payloads.Cue.CANCEL?s.from:s.to;
        s.flare(art,at,.24+s.t*.41,.22+s.t*.45,.82f);
        return true;
    }

    private SignatureFx() {}
}

package cn.academy.port.client.canon;

import static cn.academy.port.client.canon.CanonFxScripts.*;

import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.client.canon.CanonFx.View;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/**
 * Action-shape layer over each category's role art. Role art is shared by many skills (every Dark Matter volley used one shard
 * puff, every Mental skill one thin thread), so skills with different descriptions looked the same and often barely showed.
 * Each skill is tagged with what it actually does (beam, fan, ring, rain, dash, lift, mark...) and that shape is drawn in the
 * category's own colours and textures, scaled with skill level. Fan and ring sizes are the controllers' real reach.
 * Hydro Hand keeps its own art, which already reads per skill. Presentation only.
 */
final class CanonShapes {
    enum Shape { BEAM, SLASH, FAN, RING, FIELD, RAIN, BURST, UPPER, SLAM, PUSH, PULL, DASH, BRAKE, RISE, FALL, CUSHION, HOVER, ORBIT, AURA, MARK, LINK }
    private record Tag(Shape shape,double size,double angle) {}
    private record Style(float[] main,float[] accent,String glyph,String line,CanonFx4.Mode mode) {}
    private static final Map<String,Tag> TAGS=new HashMap<>();
    private static final Map<String,Style> STYLES=Map.of(
            "mental_out",new Style(new float[]{1f,.5f,.82f},new float[]{.78f,.62f,1f},"fx4:mo_star_blink_sheet","fx4:mo_thread",CanonFx4.Mode.ADD),
            "aero_hand",new Style(new float[]{.6f,1f,.88f},new float[]{.95f,1f,.98f},"fx4:ah_dust_sheet","fx4:common_speedline",CanonFx4.Mode.ADD),
            "nitrogen_armor",new Style(new float[]{.62f,.85f,1f},new float[]{.92f,.98f,1f},"fx4:na_frost_atlas","fx4:common_speedline",CanonFx4.Mode.ADD),
            "dark_matter",new Style(new float[]{.97f,.97f,1f},new float[]{.8f,.9f,1f},"fx4:dm_shard_atlas","fx4:common_speedline",CanonFx4.Mode.ADD),
            "float_dial",new Style(new float[]{.62f,1f,.72f},new float[]{.88f,1f,.95f},"fx4:fd_bubble_atlas","fx4:common_speedline",CanonFx4.Mode.ADD),
            "paper_psychokinesis",new Style(new float[]{1f,.86f,.6f},new float[]{1f,.96f,.86f},"fx4:pp_sheet_atlas","fx4:common_speedline",CanonFx4.Mode.ADD),
            "liquid_shadow",new Style(new float[]{.5f,.52f,.68f},new float[]{.15f,.15f,.2f},"fx4:ls_drip_sheet","fx4:ls_wet_ribbon",CanonFx4.Mode.ALPHA));
    private static void tag(Shape s,double size,double angle,String... ids){for(String id:ids)TAGS.put(id,new Tag(s,size,angle));}
    static {
        // Mental Out: reads, links and fields of influence.
        tag(Shape.MARK,1,0,"mental_probe","mental_intent_scan","mental_target_sense","mental_focus_break","mental_route_interrupt","mental_disrupt","mental_suppress_pair","mental_puppet");
        tag(Shape.LINK,1,0,"mental_disengage","mental_attention_shift","mental_interpose","mental_redirect","mental_conflict","mental_lure_pair");
        tag(Shape.RING,16,0,"mental_life_scan","mental_hostile_scan");
        tag(Shape.RING,12,0,"mental_pursuit_break","mental_relay_interference");
        tag(Shape.FIELD,12,0,"mental_silence_field","mental_panic_field","mental_pursuit_field","mental_crossfire","mental_crowd_detach","mental_conflict_ring");
        tag(Shape.FIELD,16,0,"mental_out_crowd");
        // Aero Hand
        tag(Shape.DASH,1,0,"aero_jet","aero_side_step","aero_backstep","aero_airborne_turn");
        tag(Shape.BRAKE,1,0,"aero_brake");
        tag(Shape.RISE,1,0,"aero_updraft","aero_vertical_jet","aero_double_jet");
        tag(Shape.CUSHION,1,0,"aero_fall_brake");
        tag(Shape.PUSH,1,0,"aero_push","aero_launch","aero_crosswind");
        tag(Shape.PULL,1,0,"aero_item_pull");
        tag(Shape.BURST,1,0,"aero_gust","aero_arrow_deflect");
        tag(Shape.FAN,8,72,"aero_cone");
        tag(Shape.FAN,8,66,"aero_ground_blast");
        tag(Shape.RING,4,0,"aero_ring_push");
        tag(Shape.RING,8,0,"aero_squall");
        tag(Shape.ORBIT,1,0,"aero_vortex");
        tag(Shape.BEAM,14,0,"aero_pressure_lance","aero_barrage");
        // Nitrogen Armor
        tag(Shape.AURA,1,0,"nitrogen_armor","nitrogen_shell_reinforce");
        tag(Shape.BURST,1,0,"nitrogen_guard","nitrogen_burst","nitrogen_punch");
        tag(Shape.DASH,1,0,"nitrogen_dash","nitrogen_ram");
        tag(Shape.BRAKE,1,0,"nitrogen_brake");
        tag(Shape.PUSH,1,0,"nitrogen_shove","nitrogen_item_push");
        tag(Shape.CUSHION,1,0,"nitrogen_buoy","nitrogen_fall_guard");
        tag(Shape.UPPER,1,0,"nitrogen_uppercut");
        tag(Shape.FAN,4,140,"nitrogen_sweep");
        tag(Shape.FAN,7,88,"nitrogen_cone");
        tag(Shape.LINK,1,0,"nitrogen_ally_guard");
        tag(Shape.RING,5,0,"nitrogen_ground_wave");
        tag(Shape.RING,6,0,"nitrogen_shock_ring");
        tag(Shape.RISE,1,0,"nitrogen_rocket");
        tag(Shape.SLAM,1,0,"nitrogen_impact");
        // Dark Matter
        tag(Shape.SLASH,1,0,"dark_blade");
        tag(Shape.BEAM,12,0,"dark_shard","dark_shard_salvo","dark_matter_barrage");
        tag(Shape.BEAM,14,0,"dark_pierce");
        tag(Shape.BURST,1,0,"dark_feather_guard","dark_synthesis");
        tag(Shape.DASH,1,0,"dark_step","dark_wing_charge");
        tag(Shape.FAN,8,88,"dark_spread");
        tag(Shape.FAN,4,140,"dark_sweep");
        tag(Shape.FAN,12,30,"dark_spear_fan");
        tag(Shape.RISE,1,0,"dark_lift");
        tag(Shape.PULL,1,0,"dark_shard_return");
        tag(Shape.AURA,1,0,"dark_wings");
        tag(Shape.ORBIT,1,0,"dark_shard_orbit");
        tag(Shape.BRAKE,1,0,"dark_wing_brake");
        tag(Shape.RAIN,1,0,"dark_rain");
        tag(Shape.CUSHION,1,0,"dark_wing_rescue");
        tag(Shape.LINK,1,0,"dark_wing_shelter");
        tag(Shape.RING,8,0,"dark_shard_ring");
        // Float Dial
        tag(Shape.RISE,1,0,"float_lift","float_item_lift","float_ascent","float_ally_ascent");
        tag(Shape.FALL,1,0,"float_item_lower","float_descent","float_ally_descent","float_hostile_descent");
        tag(Shape.PULL,1,0,"float_item_pull");
        tag(Shape.PUSH,1,0,"float_item_push");
        tag(Shape.BURST,1,0,"float_item_release");
        tag(Shape.CUSHION,1,0,"float_rescue","float_air_brake");
        tag(Shape.HOVER,1,0,"float_hover","float_ally_hover","float_suspend","float_item_shelf");
        tag(Shape.ORBIT,1,0,"float_item_orbit");
        // Paper Psychokinesis: constructs keep their real block art.
        tag(Shape.BEAM,16,0,"paper_blade");
        tag(Shape.AURA,1,0,"paper_armor");
        tag(Shape.RING,12,0,"paper_array");
        // Liquid Shadow: the body acts, so shapes start at the visual origin (the body).
        tag(Shape.BURST,1,0,"liquid_strike","liquid_guard","liquid_intercept","liquid_screen");
        tag(Shape.PUSH,1,0,"liquid_push");
        tag(Shape.RING,6,0,"liquid_pulse");
        tag(Shape.LINK,1,0,"liquid_grapple");
        tag(Shape.SLASH,1,0,"liquid_contact_cut","liquid_contact_sweep");
        tag(Shape.SLAM,1,0,"liquid_contact_crush");
        tag(Shape.RING,3,0,"liquid_listen","liquid_survey","liquid_dual_sensor");
        tag(Shape.LINK,1,0,"liquid_path","liquid_route","liquid_switch");
    }
    private record Segment(Vec3 from,Vec3 to){}
    private static final Map<Segment,Vec3> CLIPPED=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel clippedWorld;
    private static long clippedTick=Long.MIN_VALUE;
    private static Vec3 clipped(Vec3 from,Vec3 to){
        var mc=net.minecraft.client.Minecraft.getInstance();if(mc.level==null)return to;
        long tick=mc.level.getGameTime();if(mc.level!=clippedWorld||tick!=clippedTick){CLIPPED.clear();clippedWorld=mc.level;clippedTick=tick;}
        return CLIPPED.computeIfAbsent(new Segment(from,to),s->mc.level.clip(new net.minecraft.world.level.ClipContext(s.from(),s.to(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player)).getLocation());
    }
    private CanonShapes() {}
    static boolean tagged(String skill){return TAGS.containsKey(skill);}
    /** Close hits remain hits. Distance from the owner's feet cannot identify a self-cast. */
    static boolean onTarget(View v){
        return v.targetBox()!=null || (v.phase()==2||cn.academy.port.core.RangedSkillRanges.find(v.skill())!=null)&&v.origin().distanceToSqr(v.endpoint())>1e-8 || v.category().equals("mental_out")
                || v.category().equals("dark_matter")&&(CanonFxDark.role(v.skill())==CanonFxDark.Role.BLADE||CanonFxDark.role(v.skill())==CanonFxDark.Role.VOLLEY)
                || v.endpoint().distanceToSqr(v.anchor())>2.25;
    }

    static void draw(Canvas c,View v){
        if(v.phase()==3||CanonReceiptFx.replacesLegacy(v.skill()))return;Tag tag=TAGS.get(v.skill());Style st=STYLES.get(v.category());if(tag==null||st==null)return;
        double e=env(v);if(e<=.01)return;
        int tier=Math.max(1,Math.min(5,v.tier()));double k=.75+.17*tier,t=v.age(),life=Math.max(4,v.life());
        double arrive=Math.min(life*.4,4),since=t-arrive;
        Vec3 feet=v.anchor(),eye=feet.add(0,1.4,0),end=v.endpoint(),o=v.origin();
        boolean onTarget=onTarget(v);
        Vec3 f=v.aimAxis()!=null?v.aimAxis():flat(onTarget?end.subtract(feet):Vec3.directionFromRotation(0,v.yaw())),r=rightOf(f),up=new Vec3(0,1,0);
        double size=v.radius()>0?v.radius():cn.academy.port.core.RangedSkillRanges.at(v.skill(),1,tag.size());
        switch(tag.shape()){
            case BEAM -> {Vec3 a=o,b=end; // The server supplies both ends, including a nearby or obstructed hit.
                double draw=easeOut(clamp01(t/Math.max(1,arrive)));Vec3 tip=a.add(b.subtract(a).scale(draw));
                c.ribbon4(st.line(),st.mode(),line(a,tip,6),x->(.12+.1*tier)*(.4+.6*x),x->.95*e,st.main(),2,-t*.2);
                c.ribbon4(st.line(),CanonFx4.Mode.ADD,line(a,tip,6),x->(.32+.2*tier)*x,x->.35*e,st.accent(),1,-t*.1);
                if(since>=0)impact(c,st,b,k*1.2,since,e,f);}
            case SLASH -> {Vec3 at=onTarget?end:eye.add(f.scale(1.6));double sw=clamp01(t/Math.max(2,arrive));var pts=new ArrayList<Vec3>();
                for(int i=0;i<=12;i++){double a=(-1+2*i/12.0)*1.2*sw;pts.add(at.add(r.scale(Math.sin(a)*1.4*k)).add(f.scale(-Math.cos(a)*.6)).add(0,(i/12.0-.5)*.8,0));}
                c.ribbon4(st.line(),CanonFx4.Mode.ADD,pts,x->.25*k*Math.sin(Math.PI*x),x->.95*e*x,st.main(),1,-t*.2);
                if(since>=0)impact(c,st,at,k,since,e,f);}
            case FAN -> {
                double grow=easeOut(clamp01(t/Math.max(2,arrive+2)));Vec3 start=o;
                var shape=cn.academy.port.canon.CanonExpansionCombatA.shape(v.skill());
                double half=shape!=null&&shape.cone()>=0?Math.acos(shape.cone()):Math.toRadians(tag.angle()/2);
                if(v.skill().equals("dark_spear_fan")){
                    for(Vec3 d:cn.academy.port.canon.CanonExpansionCombatA.spearDirections(f)){
                        Vec3 tip=clipped(start,start.add(d.scale(size)));
                        c.cellStrip(st.line(),CanonFx4.Mode.ADD,start,start.lerp(tip,grow),.14*k,0,st.main(),(float)(.85*e));
                    }
                }else{
                    int n=8+2*tier;Vec3 u=upOf(f,r);var rim=new ArrayList<Vec3>();
                    for(int i=0;i<=n;i++){
                        double a=i*Math.PI*2/n;Vec3 d=f.scale(Math.cos(half)).add(r.scale(Math.sin(half)*Math.cos(a))).add(u.scale(Math.sin(half)*Math.sin(a)));
                        Vec3 tip=clipped(start,start.add(d.scale(size)));Vec3 draw=start.lerp(tip,grow);rim.add(draw);
                        if(i<n)c.cellStrip(st.line(),CanonFx4.Mode.ADD,start,draw,.1*k,0,st.main(),(float)(.65*e));
                    }
                    c.ribbon4(st.line(),CanonFx4.Mode.ADD,rim,x->.08*k,x->.6*e,st.accent(),2,t*.1);
                }
            }
            case RING -> {Vec3 g=v.category().equals("liquid_shadow")?o.add(0,-.5,0):feet;double ph=clamp01(t/Math.max(6,life*.6));
                var sheet=Fx4Atlas.get("common_shock_ring_sheet");
                c.cellPlane("fx4:common_shock_ring_sheet",CanonFx4.Mode.ADD,g.add(0,.12,0),new Vec3(size*ph,0,0),new Vec3(0,0,size*ph),sheet.frame(ph),st.main(),(float)(.9*(1-ph*.7)*e));
                for(int i=0;i<8+2*tier;i++){double a=i*Math.PI*2/(8+2*tier)+t*.02;Vec3 at=g.add(Math.cos(a)*size*ph,.4+.3*Math.sin(t*.2+i),Math.sin(a)*size*ph);
                    c.cellSprite(st.glyph(),st.mode(),at,.35*k,t*.1+i,i,st.main(),(float)(.8*e*(1-ph*.5)));}
                if(tier>=4)c.cellPlane("fx4:common_shock_ring_sheet",CanonFx4.Mode.ADD,g.add(0,.14,0),new Vec3(size*ph*.6,0,0),new Vec3(0,0,size*ph*.6),sheet.frame(ph),st.accent(),(float)(.6*(1-ph)*e));}
            case FIELD -> {double grow=easeOut(clamp01(t/10));
                c.ground("ring",true,feet.add(0,.07,0),size*grow,st.main(),(float)(.55*e));c.ground("ring2",true,feet.add(0,.07,0),size*.7*grow,st.accent(),(float)(.35*e));
                int n=6+tier*2;for(int i=0;i<n;i++){double a=i*Math.PI*2/n+t*.015;Vec3 at=feet.add(Math.cos(a)*size*grow,0,Math.sin(a)*size*grow);
                    c.cellStrip(st.line(),CanonFx4.Mode.ADD,at,at.add(0,1.2+.4*tier,0),.22*k,0,st.main(),(float)(.6*e));
                    c.cellSprite(st.glyph(),st.mode(),at.add(0,1.6+.4*tier+.2*Math.sin(t*.15+i),0),.3*k,t*.05,i,st.accent(),(float)(.85*e));}}
            case RAIN -> {Vec3 g=onTarget?end:feet.add(f.scale(4));int n=3+tier*2;
                for(int i=0;i<n;i++){double ph=clamp01((t-i*.8)/Math.max(3,arrive+2));if(ph<=0)continue;Vec3 off=new Vec3((rnd(i)-.5)*2.2,0,(rnd(i+7)-.5)*2.2);
                    Vec3 top=g.add(off).add(0,7,0),at=top.add(0,-7*easeIn(ph),0);
                    c.cellStrip(st.line(),CanonFx4.Mode.ADD,at.add(0,1.6,0),at,.16*k,0,st.main(),(float)(.95*e));
                    c.cellSprite(st.glyph(),st.mode(),at,.45*k,t*.2+i,i,st.accent(),(float)(.9*e));}
                if(since>=0)impact(c,st,g.add(0,.3,0),k*1.4,since,e,f);}
            case BURST -> {Vec3 at=v.category().equals("liquid_shadow")&&!onTarget?o:onTarget?end:eye.add(f.scale(1.2));double ph=clamp01(Math.max(0,since)/6);
                if(since>=0)impact(c,st,at,k*1.3,since,e,f);else c.cellSprite(st.glyph(),st.mode(),at,.6*k*(1-t/Math.max(1,arrive)),t*.3,0,st.accent(),(float)e);
                for(int i=0;i<6+tier;i++){double a=i*2.4+t*.05;Vec3 d=new Vec3(Math.cos(a),Math.sin(a*1.7)*.6,Math.sin(a)).normalize();
                    c.cellSprite(st.glyph(),st.mode(),at.add(d.scale(.3+1.6*ph*k)),.3*k,a,i,st.main(),(float)(.85*e*(1-ph)));}}
            case UPPER -> {Vec3 at=onTarget?end:feet.add(f.scale(1.5));double ph=clamp01(Math.max(0,since)/8);
                c.cellStrip(st.line(),CanonFx4.Mode.ADD,at,at.add(0,1+4*ph,0),.5*k,0,st.main(),(float)(.95*e*(1-ph*.5)));
                if(since>=0)impact(c,st,at.add(0,1,0),k*1.3,since,e,up);}
            case SLAM -> {Vec3 at=(onTarget?end:feet.add(f.scale(1)));double ph=clamp01(Math.max(0,since)/8);
                c.cellStrip(st.line(),CanonFx4.Mode.ADD,at.add(0,5*(1-ph)+.2,0),at.add(0,.2,0),.5*k,0,st.main(),(float)(.95*e));
                if(since>=0){impact(c,st,at.add(0,.3,0),k*1.5,since,e,up);var sheet=Fx4Atlas.get("common_shock_ring_sheet");
                    c.cellPlane("fx4:common_shock_ring_sheet",CanonFx4.Mode.ADD,at.add(0,.12,0),new Vec3(3*k*ph,0,0),new Vec3(0,0,3*k*ph),sheet.frame(ph),st.main(),(float)(.9*(1-ph)*e));}}
            case PUSH -> {Vec3 a=v.category().equals("liquid_shadow")?o:eye,b=onTarget?end:eye.add(f.scale(4));Vec3 d=b.subtract(a);if(d.lengthSqr()<1e-4)d=f;
                double ph=clamp01(t/Math.max(3,arrive+3));Vec3 at=a.add(d.scale(ph)),dn=d.normalize();var sheet=Fx4Atlas.get("common_shock_ring_sheet");
                c.cellPlane("fx4:common_shock_ring_sheet",CanonFx4.Mode.ADD,at,rightOf(dn).scale(.8*k+ph),upOf(dn,rightOf(dn)).scale(.8*k+ph),sheet.frame(ph),st.main(),(float)(.9*e*(1-ph*.4)));
                for(int i=0;i<4;i++){Vec3 off=rightOf(dn).scale((i-1.5)*.35);c.cellStrip(st.line(),CanonFx4.Mode.ADD,at.add(off).subtract(dn.scale(1.4)),at.add(off),.1*k,0,st.accent(),(float)(.8*e));}}
            case PULL -> {Vec3 a=onTarget?end.add(0,.5,0):eye.add(f.scale(4)),b=eye;double ph=clamp01(t/Math.max(3,life*.6));
                for(int i=0;i<5;i++){double u=frac(ph+i/5.0);Vec3 at=a.add(b.subtract(a).scale(u));
                    c.cellSprite(st.glyph(),st.mode(),at,.35*k*(1-u*.5),t*.2+i,i,st.main(),(float)(.9*e));}
                c.ribbon4(st.line(),CanonFx4.Mode.ADD,line(a,b,6),x->.1*k,x->.6*e,st.accent(),2,t*.2);}
            case DASH -> {Vec3 back=f.scale(-1);for(int i=0;i<4+tier;i++){Vec3 off=r.scale((rnd(i)-.5)*.9).add(0,.3+rnd(i+3)*1.4,0);
                    c.cellStrip(st.line(),CanonFx4.Mode.ADD,feet.add(off),feet.add(off).add(back.scale(1.5+2*k*rnd(i+5))),.12*k,0,i%2==0?st.main():st.accent(),(float)(.85*e));}
                c.cellPlane("fx4:common_shock_ring_sheet",CanonFx4.Mode.ADD,feet.add(0,1,0).add(back.scale(.6)),r.scale(.9*k),up.scale(.9*k),Fx4Atlas.get("common_shock_ring_sheet").frame(clamp01(t/8)),st.main(),(float)(.7*e*(1-clamp01(t/8))));}
            case BRAKE -> {Vec3 at=feet.add(0,1,0).add(f.scale(1.2));double ph=clamp01(t/8);
                c.cellPlane("fx4:common_shock_ring_sheet",CanonFx4.Mode.ADD,at,r.scale(1.1*k),up.scale(1.1*k),Fx4Atlas.get("common_shock_ring_sheet").frame(ph),st.main(),(float)(.9*e*(1-ph*.5)));
                for(int i=0;i<5;i++){Vec3 off=r.scale((i-2)*.35);c.cellStrip(st.line(),CanonFx4.Mode.ADD,at.add(off).add(f.scale(1.2)),at.add(off),.1*k,0,st.accent(),(float)(.7*e));}}
            case RISE, FALL, HOVER -> {Vec3 at=onTarget&&!v.skill().equals("float_item_shelf")?end:feet;boolean riseUp=tag.shape()==Shape.RISE,hold=tag.shape()==Shape.HOVER;
                double flow=frac(t*.05);for(int i=0;i<4+tier;i++){double a=i*Math.PI*2/(4+tier)+t*.03,u=frac(flow+i/(double)(4+tier));
                    double y=hold?.1+.6*Math.sin(t*.15+i)*.3:riseUp?u*3:3-u*3;Vec3 p=at.add(Math.cos(a)*.8,y,Math.sin(a)*.8);
                    c.cellStrip(st.line(),CanonFx4.Mode.ADD,p,p.add(0,hold?.2:riseUp?.8:-.8,0),.1*k,0,st.main(),(float)(.8*e));}
                c.ground("ring",true,at.add(0,hold?0:.07,0),1.1*k,st.main(),(float)(.6*e));
                if(hold)for(int i=0;i<3;i++)c.ground("ring2",true,at.add(0,.4+i*.5+.1*Math.sin(t*.1+i),0),.9*k-i*.15,st.accent(),(float)(.45*e));
                if(riseUp)c.cellStrip("fx4:common_light_pillar",CanonFx4.Mode.ADD,at,at.add(0,3.5*k,0),.9*k,0,st.accent(),(float)(.35*e));}
            case CUSHION -> {double ph=frac(t*.06);
                for(int i=0;i<3;i++){double u=frac(ph+i/3.0);c.ground("ring",true,feet.add(0,-.3+u*.6,0),.6+1.4*u*k,st.main(),(float)(.7*e*(1-u)));}
                c.cellPlane(st.glyph(),st.mode(),feet.add(0,.05,0),new Vec3(1.2*k,0,0),new Vec3(0,0,1.2*k),0,st.accent(),(float)(.5*e));}
            case ORBIT -> {Vec3 at=onTarget?end.add(0,.5,0):feet.add(0,1,0);for(int i=0;i<5+tier;i++){double a=t*.18+i*Math.PI*2/(5+tier),rad=1.1*k;
                    Vec3 p=at.add(Math.cos(a)*rad,Math.sin(a*2)*.25,Math.sin(a)*rad),q=at.add(Math.cos(a-.5)*rad,Math.sin((a-.5)*2)*.25,Math.sin(a-.5)*rad);
                    c.cellStrip(st.line(),CanonFx4.Mode.ADD,q,p,.12*k,0,st.main(),(float)(.85*e));c.cellSprite(st.glyph(),st.mode(),p,.3*k,a,i,st.accent(),(float)(.9*e));}}
            case AURA -> {Vec3 at=feet.add(0,1,0);double grow=easeOutBack(clamp01(t/8));
                for(int i=0;i<6+tier*2;i++){double a=i*2.39+t*.04,el=Math.sin(i*1.3)*.9;Vec3 d=new Vec3(Math.cos(a)*Math.cos(el),Math.sin(el),Math.sin(a)*Math.cos(el));
                    c.cellSprite(st.glyph(),st.mode(),at.add(d.scale(1.1*k*grow)),.35*k,a,i,i%2==0?st.main():st.accent(),(float)(.85*e));}
                c.ground("ring",true,feet.add(0,.07,0),1.3*k*grow,st.main(),(float)(.5*e));}
            case MARK -> {Vec3 at=onTarget?end:eye.add(f.scale(3));Vec3 top=at.add(0,1.3+.15*Math.sin(t*.2),0);var ring=Fx4Atlas.get("mo_signal_ring_sheet");
                c.cellSprite("fx4:mo_signal_ring_sheet",CanonFx4.Mode.ADD,top,1.1*k,t*.03,ring.frame(frac(t*.04)),st.main(),(float)(.95*e));
                c.cellSprite(st.glyph(),st.mode(),top,.4*k,0,Fx4Atlas.get(st.glyph().substring(4)).frame(frac(t*.06)),st.accent(),(float)e);
                c.ribbon4(st.line(),CanonFx4.Mode.ADD,line(eye,top,8),x->.06*k,x->.75*e,st.main(),3,-t*.15);}
            case LINK -> {Vec3 a=v.category().equals("liquid_shadow")?o:eye,b=onTarget?end:eye.add(f.scale(3));Vec3 mid=a.add(b).scale(.5).add(0,1+.2*tier,0);
                var arc=new ArrayList<Vec3>();for(int i=0;i<=10;i++){double u=i/10.0;arc.add(a.scale((1-u)*(1-u)).add(mid.scale(2*u*(1-u))).add(b.scale(u*u)));}
                c.ribbon4(st.line(),CanonFx4.Mode.ADD,arc,x->.09*k,x->.9*e,st.main(),3,-t*.15);
                c.cellSprite(st.glyph(),st.mode(),b,.55*k,t*.05,0,st.accent(),(float)(.9*e));c.cellSprite(st.glyph(),st.mode(),a,.4*k,-t*.05,1,st.accent(),(float)(.8*e));}
        }
        if(tier>=5&&v.local()&&t<1)CanonCamera.shake(.25,6);
    }

    /** Hit flash, a shock ring facing the hit direction and a spray of the category's glyphs. */
    private static void impact(Canvas c,Style st,Vec3 at,double k,double since,double e,Vec3 dir){
        double ph=clamp01(since/8);if(ph>=1)return;var flash=Fx4Atlas.get("common_hit_flash_sheet");var ring=Fx4Atlas.get("common_shock_ring_sheet");
        c.cellSprite("fx4:common_hit_flash_sheet",CanonFx4.Mode.ADD,at,1.4*k,0,flash.frame(ph),st.accent(),(float)(e*(1-ph)));
        Vec3 d=dir.lengthSqr()<1e-6?new Vec3(0,0,1):dir.normalize(),r=rightOf(d),u=upOf(d,r);
        c.cellPlane("fx4:common_shock_ring_sheet",CanonFx4.Mode.ADD,at,r.scale(.6+1.6*k*ph),u.scale(.6+1.6*k*ph),ring.frame(ph),st.main(),(float)(.9*e*(1-ph)));
        for(int i=0;i<6;i++){double a=i*1.05+ph;Vec3 s=r.scale(Math.cos(a)).add(u.scale(Math.sin(a)));
            c.cellSprite(st.glyph(),st.mode(),at.add(s.scale(.4+1.4*k*ph)),.28*k,a,i,st.main(),(float)(.85*e*(1-ph)));}
    }
}

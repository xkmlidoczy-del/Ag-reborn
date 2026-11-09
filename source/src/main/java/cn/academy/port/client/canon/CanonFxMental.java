package cn.academy.port.client.canon;

import static cn.academy.port.client.canon.CanonFxScripts.*;

import cn.academy.port.canon.CanonVfx;
import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.client.canon.CanonFx.View;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Mental Out on FX4: the remote control of the original. Golden hologram threads (SH6) are drawn out from the hand, the target is
 * locked with hologram corner brackets that only the caster sees (SH7 stand-in), and a star pupil pops above its head. Ladder:
 * level 3 adds a status arc showing the remaining control time (TM4), level 4 a gold thread network across several marks (PT3),
 * level 5 a gold vignette with every controlled star blinking in step (CM5). No ground sigils. Presentation only.
 */
final class CanonFxMental {
    private static final float[] GOLD={1f,.8f,.3f},PALE={1f,.95f,.75f},PINK={1f,.6f,.82f},INK={.24f,.14f,.035f};
    private static final String THREAD="fx4:mo_thread",STAR="fx4:mo_star_blink_sheet",PANEL="fx4:mo_holo_panel",SIGNAL="fx4:mo_signal_ring_sheet",ARC="fx4:mo_status_arc";
    enum Role { SCAN, CONTROL, FIELD }
    private static final Map<String,Role> ROLES=Map.ofEntries(
            Map.entry("mental_hostile_scan",Role.SCAN),Map.entry("mental_intent_scan",Role.SCAN),Map.entry("mental_life_scan",Role.SCAN),
            Map.entry("mental_target_sense",Role.SCAN),Map.entry("mental_probe",Role.SCAN),
            Map.entry("mental_panic_field",Role.FIELD),Map.entry("mental_pursuit_field",Role.FIELD),Map.entry("mental_silence_field",Role.FIELD),
            Map.entry("mental_conflict_ring",Role.FIELD),Map.entry("mental_crowd_detach",Role.FIELD),Map.entry("mental_out_crowd",Role.FIELD),
            Map.entry("mental_relay_interference",Role.FIELD),Map.entry("mental_crossfire",Role.FIELD));
    // Only these server visuals carry an actual aimed target point. Area scans
    // carry the caster position; remote-assisted target marks arrive as mental_probe.
    private static final Set<String> POINT_SCANS=Set.of("mental_probe","mental_intent_scan","mental_target_sense");
    // These FIELD visuals name a real affected mob point. CombatA.crowd emits
    // each leased mob; ExpansionCombatA.control emits its first affected mob as
    // a representative. Neither protocol grants membership to nearby entities.
    private static final Set<String> POINT_FIELDS=Set.of("mental_panic_field","mental_pursuit_field","mental_silence_field",
            "mental_conflict_ring","mental_crowd_detach","mental_out_crowd","mental_relay_interference","mental_crossfire");
    private CanonFxMental() {}
    static Role role(String skill){return ROLES.getOrDefault(skill,Role.CONTROL);}
    static boolean targetPointField(String skill){return POINT_FIELDS.contains(skill);}

    static void onReceive(CanonVfx.Visual v,int tier,boolean local,double life){
        if(v.phase()==CanonVfx.Phase.END||!local)return;
        if (targetPointField(v.skill()) && CanonReceiptFx.replacesLegacy(v.skill())) {
            // The radius is sent once per cast, even when each leased mob has its own visual.
            if (v.phase() == CanonVfx.Phase.ACTIVE && v.radius() > 0 && tier >= 5)
                CanonCamera.tint(GOLD, .24, (int) Math.min(12, life));
            return;
        }
        if(role(v.skill())==Role.CONTROL)CanonFxForms.later(4,()->{CanonCamera.shake(.12,4);CanonCamera.freeze(18);});
        if(tier>=5){CanonCamera.tint(GOLD,.35,(int)Math.min(40,life));CanonCamera.darken(.2,(int)Math.min(40,life));}
    }

    static void draw(Canvas c,View v){
        double e=env(v);if(e<=0)return;double t=time(v);int tier=Math.max(1,Math.min(5,v.tier()));
        if (targetPointField(v.skill()) && CanonReceiptFx.replacesLegacy(v.skill())) {
            if (v.phase() == CanonVfx.Phase.ACTIVE.ordinal()) receiptMark(c, v, e);
            return;
        }
        switch(role(v.skill())){
            case SCAN -> scan(c,v,e,t,tier);
            case CONTROL -> control(c,v,e,t,tier);
            case FIELD -> field(c,v,e,t,tier);
        }
    }
    private static Vec3 hand(View v){return v.anchor().add(0,1.35,0).add(flat(Vec3.directionFromRotation(0,v.yaw())).scale(.45));}
    /** The coloured alpha core and dark keyline survive bright terrain and the shader-off fallback. */
    private static void thread(Canvas c,java.util.List<Vec3> path,java.util.function.DoubleUnaryOperator width,
                               java.util.function.DoubleUnaryOperator alpha,float[] rgb,double repeat,double scroll){
        c.ribbon4(THREAD,CanonFx4.Mode.ALPHA,path,x->width.applyAsDouble(x)*1.55,x->alpha.applyAsDouble(x)*.58,INK,repeat,scroll);
        c.ribbon4(THREAD,CanonFx4.Mode.ALPHA,path,width,x->alpha.applyAsDouble(x)*.86,rgb,repeat,scroll);
        c.ribbon4(THREAD,CanonFx4.Mode.HOLO,path,x->width.applyAsDouble(x)*.62,x->alpha.applyAsDouble(x)*.8,PALE,repeat,scroll);
    }
    /** SH6 hologram corner brackets around a box: a target lock only the caster's client draws. */
    private static void brackets(Canvas c,AABB box,double e,double t){
        double k=Math.max(.18,Math.min(.34,Math.min(box.getXsize(),box.getYsize())*.35)),pulse=1+.04*Math.sin(t*.5);Vec3 mid=box.getCenter();
        double hw=box.getXsize()*.5*pulse+.1,hh=box.getYsize()*.5*pulse+.1;Vec3 r=c.right.scale(hw),u=c.up.scale(hh);
        for(int sx=-1;sx<=1;sx+=2)for(int sy=-1;sy<=1;sy+=2){Vec3 corner=mid.add(r.scale(sx)).add(u.scale(sy));
            var horizontal=line(corner,corner.subtract(c.right.scale(sx*k)),2);
            var vertical=line(corner,corner.subtract(c.up.scale(sy*k)),2);
            // mo_thread has a narrow alpha core: .035-wide geometry made the
            // actual gold ink subpixel at ordinary scan distances.
            c.ribbon4(THREAD,CanonFx4.Mode.ALPHA,horizontal,x->.18,x->.65*e,GOLD,1,0);
            c.ribbon4(THREAD,CanonFx4.Mode.ALPHA,vertical,x->.18,x->.65*e,GOLD,1,0);
            c.ribbon4(THREAD,CanonFx4.Mode.HOLO,horizontal,x->.10,x->.9*e,PALE,1,0);
            c.ribbon4(THREAD,CanonFx4.Mode.HOLO,vertical,x->.10,x->.9*e,PALE,1,0);}
    }
    /** The star pupil pops above a head, blinks, then holds its last frame while the control lasts. */
    private static void star(Canvas c,Vec3 head,double age,double life,double e,double t,boolean sync){
        var grid=Fx4Atlas.get("mo_star_blink_sheet");double p=clamp01(age/Math.min(14,Math.max(3,life*.75)));
        int frame=sync?grid.frame(frac(t*.02)):grid.frame(p);
        c.cellSprite(STAR,CanonFx4.Mode.ALPHA,head.add(0,.34+.025*Math.sin(t*.3),0),.54*easeOutBack(clamp01(age/Math.min(4,Math.max(1.5,life*.25)))),0,frame,PALE,(float)e);
    }
    private static void scan(Canvas c,View v,double e,double t,int tier){
        // A holographic radar sweep: a fan of thin gold rays turns once around the caster and leaves panels over what it found.
        Vec3 h=hand(v);double sweep=reveal(v,10)*Math.PI*2+v.yaw()*Math.PI/180;double R=4+tier;
        for(int k=0;k<6;k++){double a=sweep-k*.07,fade=1-k/6.;Vec3 end=h.add(Math.cos(a)*R,-.6,Math.sin(a)*R);
            thread(c,line(h,end,4),x->.13*(1-x*.4),x->.8*e*fade*(1-x*.4),GOLD,R/2,-t*.1);}
        // The 2.5-block association radius is around a server-confirmed endpoint,
        // not the skill reach (mental_probe can be aimed up to 24 blocks away).
        AABB box=POINT_SCANS.contains(v.skill())?v.targetBox():null;
        if(box!=null&&v.age()>.75){
            if(v.local())brackets(c,box,e*easeOut(reveal(v,2)),t);
            Vec3 top=new Vec3(box.getCenter().x,box.maxY,box.getCenter().z);
            if(v.local()){
                double panel=e*easeOut(clamp01((v.age()-3)/4));
                c.cellPlane(PANEL,CanonFx4.Mode.ALPHA,top.add(0,.75,0),c.right.scale(.42),c.up.scale(.42),0,GOLD,(float)(.65*panel));
                c.cellPlane(PANEL,CanonFx4.Mode.HOLO,top.add(0,.75,0),c.right.scale(.42),c.up.scale(.42),0,PALE,(float)(.75*panel));
            }
            Vec3 lock=top.add(0,.18,0);
            thread(c,partial(line(h,lock,8),easeOut(reveal(v,3))),x->.14,x->.8*e,GOLD,2,-t*.06);
            star(c,top,v.age()-.75,v.life(),e,t,tier>=5);}
    }
    private static void control(Canvas c,View v,double e,double t,int tier){
        AABB box=v.targetBox();Vec3 h=hand(v),mid=box==null?v.endpoint():box.getCenter();
        Vec3 head=box==null?v.endpoint():new Vec3(mid.x,box.maxY,mid.z);
        // The gold thread is drawn out segment by segment, with a bead of light running to the target.
        var path=CanonFx.wave(h,head,16,.12,1.5,t*.1,new Vec3(0,1,0));double draw=easeOut(reveal(v,6));
        thread(c,partial(path,draw),x->.12,x->.9*e,GOLD,4,-t*.08);
        if(draw>=1){Vec3 bead=path.get((int)Math.round(frac(t*.05)*15));c.cellSprite(STAR,CanonFx4.Mode.ADD,bead,.09,t*.2,5,PALE,(float)(.8*e));}
        if(v.age()<4)return;
        if(v.local()&&box!=null)brackets(c,box,e,t);
        if(box!=null)star(c,head,v.age()-4,v.life(),e,t,tier>=5);
        if(tier>=3){double left=1-life01(v);Vec3 base=box==null?v.endpoint():new Vec3(mid.x,box.minY+.05,mid.z);
            c.cellPlane(ARC,CanonFx4.Mode.HOLO,base,new Vec3(.6,0,0),new Vec3(0,0,.6),0,tint(GOLD,PINK,1-left),(float)(.8*e*left));}
    }
    /** One representative or leased target per receipt. Never infer links between members. */
    private static void receiptMark(Canvas c, View v, double e) {
        AABB box = v.targetBox();
        double age = v.age(), connect = easeOut(clamp01(age / 4));
        Vec3 head = box == null ? v.endpoint() : new Vec3(box.getCenter().x, box.maxY, box.getCenter().z);
        double signal = e * (1 - clamp01((age - 3) / 5));
        if (signal > .01 && v.origin().distanceToSqr(head) > 1e-6) {
            var path = CanonFx.wave(v.origin(), head, 9, .06, 1, age * .23, new Vec3(0, 1, 0));
            thread(c, partial(path, connect), x -> .11, x -> .9 * signal, GOLD, 3, -age * .13);
        }
        // A packet can arrive before the actor is known, or alongside an
        // ambiguous overlapping entity. Keep only a brief marker at its point.
        if (box == null) {
            c.cellSprite(STAR, CanonFx4.Mode.ALPHA, head, .22, 0,
                    Fx4Atlas.get("mo_star_blink_sheet").frame(connect), PALE, (float) signal);
            return;
        }
        star(c, head, age, v.life(), e, age, v.tier() >= 5);
        if (v.local() && signal > .01) brackets(c, box, signal, age);
    }

    private static void field(Canvas c,View v,double e,double t,int tier){
        // A received target point, not a fresh proximity scan, owns each mark.
        // Multiple actual crowd receipts naturally make a network of spokes;
        // a representative-only packet never invents controlled bystanders.
        Vec3 h=hand(v);var grid=Fx4Atlas.get("mo_signal_ring_sheet");
        for(int k=0;k<2;k++){double ph=frac(t*.04+k*.5);
            c.cellSprite(SIGNAL,CanonFx4.Mode.HOLO,h,.3+.5*ph,0,grid.frame(ph),GOLD,(float)(.5*e*(1-ph)));}
        AABB box=v.targetBox();
        if(!targetPointField(v.skill())||box==null)return;
        Vec3 head=new Vec3(box.getCenter().x,box.maxY,box.getCenter().z);
        var path=CanonFx.wave(h,head,10,.08,1.2,t*.07,new Vec3(0,1,0));
        thread(c,partial(path,easeOut(reveal(v,5))),x->.10,x->.8*e,GOLD,3,-t*.06);
        double age=v.age()-1.5;if(age<0)return;
        if(v.local())brackets(c,box,e,t);
        star(c,head,age,v.life(),e,t,tier>=5);
    }
}

package cn.academy.port.client.canon;

import static cn.academy.port.client.canon.CanonFxScripts.*;

import cn.academy.port.canon.CanonVfx;
import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.client.canon.CanonFx.View;
import java.util.ArrayList;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/**
 * Aero Hand on FX4: air is shown by what it does to the light behind it (SH1 refraction shimmer) and by thin streams that follow
 * the flow (PT6), never by solid rings. Ladder: level 3 kicks up dust and debris (PT1), level 4 makes the pressure front visible as
 * an expanding shell (GM4), level 5 punches the caster's field of view and streaks speed lines across the screen edges (CM3, CM6).
 * Presentation only.
 */
final class CanonFxAero {
    private static final float[] AIR={.8f,1f,.93f},MINT={.6f,.95f,.82f},FLOW={.38f,.74f,.66f},DUST={.78f,.7f,.58f};
    private static final String STREAM="fx4:ah_stream",VORTEX="fx4:ah_vortex_sheet",DUSTS="ah_dust_sheet",DEBRIS="vm_debris_atlas",NOISE="fx4:common_noise_tile",
            LINE="fx4:common_speedline",RIM="fx4:na_shell_rim";
    enum Role { JET, BLAST, WALL, VORTEX, DEFLECT }
    private static final Map<String,Role> ROLES=Map.ofEntries(
            Map.entry("aero_push",Role.BLAST),Map.entry("aero_gust",Role.BLAST),Map.entry("aero_cone",Role.BLAST),Map.entry("aero_crosswind",Role.BLAST),
            Map.entry("aero_ground_blast",Role.BLAST),Map.entry("aero_ring_push",Role.BLAST),Map.entry("aero_pressure_lance",Role.BLAST),
            Map.entry("aero_barrage",Role.BLAST),Map.entry("aero_squall",Role.VORTEX),Map.entry("aero_vortex",Role.VORTEX),Map.entry("aero_item_pull",Role.VORTEX),
            Map.entry("aero_arrow_deflect",Role.DEFLECT),Map.entry("aero_pressure_wall",Role.WALL),Map.entry("aero_air_curtain",Role.WALL));
    private CanonFxAero() {}
    static Role role(String skill){return ROLES.getOrDefault(skill,Role.JET);}
    static double arrive(double life){return Math.min(life*.4,4);}

    static void onReceive(CanonVfx.Visual v,int tier,boolean local,double life){
        if(v.phase()==CanonVfx.Phase.END
                || "aero_barrage".equals(v.skill()) && CanonReceiptFx.replacesLegacy(v.skill())) return;
        Role role=role(v.skill());Vec3 a=v.origin(),b=v.endpoint();
        switch(role){
            case JET -> {if(tier>=3)dust(a.add(0,-1,0),6);if(local){CanonCamera.fovKick(tier>=5?.14:.08,10);if(tier>=5)CanonCamera.edge("speed",1,16);}}
            case BLAST -> CanonFxForms.later((int)Math.round(arrive(life)),()->{if(tier>=3)dust(b,8);
                if(local){CanonCamera.shake(.25+.12*tier,6);CanonCamera.freeze(20+8*tier);if(tier>=5){CanonCamera.fovKick(.08,10);CanonCamera.edge("speed",1,14);}}});
            case WALL, DEFLECT -> {if(local)CanonCamera.shake(.2,5);}
            case VORTEX -> {for(int w=0;w<5;w++){final int wave=w;CanonFxForms.later(wave*4,()->{if(tier>=3)dust(b,4);});}if(local&&tier>=5)CanonCamera.edge("speed",.8,30);}
        }
    }
    /** PT1: dust puffs and a few pieces of debris thrown up from the ground. */
    private static void dust(Vec3 at,int n){
        for(int k=0;k<n;k++){double ang=rnd(k+(int)(at.x*3))*6.28,sp=.05+.07*rnd(k+3);
            CanonParticles.spawn(DUSTS,0,at.add(0,.1,0),new Vec3(Math.cos(ang)*sp,.04+.03*rnd(k),Math.sin(ang)*sp),-.002,.08,.35+.25*rnd(k+2),.05,24,DUST,.7f,
                    CanonFx4.Mode.ALPHA,CanonParticles.Land.NONE,false,k%3);
            if(k%2==0)CanonParticles.spawn(DEBRIS,k,at,new Vec3(Math.cos(ang)*sp*1.4,.12+.08*rnd(k+5),Math.sin(ang)*sp*1.4),.04,.02,.05,.3,40,DUST,1f,
                    CanonFx4.Mode.ALPHA,CanonParticles.Land.STOP,false,0);}
    }

    static void draw(Canvas c,View v){
        double e=env(v);if(e<=0)return;double t=time(v);int tier=Math.max(1,Math.min(5,v.tier()));
        if ("aero_barrage".equals(v.skill()) && CanonReceiptFx.replacesLegacy(v.skill())) {
            if (v.phase() == CanonVfx.Phase.HIT.ordinal()) barrage(c, v, e);
            return;
        }
        if ("aero_gust".equals(v.skill()) && v.phase() == CanonVfx.Phase.HIT.ordinal()) {
            gust(c, v, e);
        }
        switch(role(v.skill())){
            case JET -> jet(c,v,e,t,tier);
            case BLAST -> blast(c,v,e,t,tier);
            case WALL -> wall(c,v,e,t);
            case VORTEX -> vortex(c,v,e,t,tier);
            case DEFLECT -> contact(c,v,e,t);
        }
    }
    private static void stream(Canvas c,java.util.List<Vec3> path,double width,double e,double t){
        // The texture contains seven fine strands, so a wider band does not
        // become a solid beam. A coloured alpha body stays legible on daylight
        // sky while the thinner additive pass catches the moving highlights.
        c.ribbon4(STREAM,CanonFx4.Mode.ALPHA,path,x->width*1.75*Math.pow(Math.sin(Math.PI*clamp01(x)),.7),x->.68*e,FLOW,2,-t*.12);
        c.ribbon4(STREAM,CanonFx4.Mode.ADD,path,x->width*.85*Math.sin(Math.PI*clamp01(x)),x->.72*e,AIR,2,-t*.12);
    }
    private static void jet(Canvas c,View v,double e,double t,int tier){
        // Nozzles of shimmering air under the feet and behind the back, streams trailing opposite the push.
        Vec3 body=v.anchor(),push=v.endpoint().subtract(v.origin());push=push.lengthSqr()<.25?flat(Vec3.directionFromRotation(0,v.yaw())):push.normalize();
        for(int k=0;k<3;k++){Vec3 nozzle=body.add(0,.1+k*.55,0).subtract(push.scale(.25));
            var trail=CanonFx.wave(nozzle,nozzle.subtract(push.scale(1.8+.4*k)).add(0,-.2,0),10,.08,2,-t*.4+k,new Vec3(0,1,0));
            stream(c,trail,.35,e,t);}
        c.refract(NOISE,false,body.add(0,.4,0).subtract(push.scale(.6)),.8,(float)(.8*e),AIR);
    }
    private static void blast(Canvas c,View v,double e,double t,int tier){
        Vec3 a=v.anchor().add(0,1.1,0),b=v.endpoint();double arr=arrive(v.life()),p=clamp01(v.age()/Math.max(1,arr)),since=v.age()-arr;
        // The pressure front travels as a shimmer (SH1) wrapped in converging streams; level 4 shows the front as a shell.
        Vec3 front=a.add(b.subtract(a).scale(easeOut(p)));
        if(since<0){c.refract(NOISE,false,front,.5+.4*p,(float)(.9*e),AIR);
            for(int k=0;k<4;k++){Vec3 off=rightOf(b.subtract(a).normalize()).scale(Math.cos(k*1.57)*.4).add(0,Math.sin(k*1.57)*.4,0);
                stream(c,line(a.add(off.scale(1.6)),front.add(off.scale(.3)),6),.22,e,t);}
            return;}
        double q=clamp01(since/Math.max(4,v.life()-arr));double R=.6+2.4*easeOut(q);
        c.refract(NOISE,false,b,R,(float)(.9*e*(1-q)),AIR);
        if(tier>=4)c.shell(RIM,CanonFx4.Mode.ADD,b,R,R*.7,8,14,MINT,(float)(.5*e*(1-q)),0);
        Vec3 d=b.subtract(a).normalize();
        c.cellPlane("fx4:ah_cone",CanonFx4.Mode.ADD,b,rightOf(d).scale(R*.6),upOf(d,rightOf(d)).scale(R*.6),0,AIR,(float)(.5*e*(1-q)));
    }
    /** A single pressure streak travels along the accepted hit segment. */
    private static void gust(Canvas c, View v, double opacity) {
        Vec3 from = v.origin();
        Vec3 delta = v.endpoint().subtract(from);
        if (delta.lengthSqr() < 1e-6) return;

        double head = easeOut(clamp01((v.age() + .5) / 3));
        double tail = Math.max(0, head - .88 + .58 * easeOut(clamp01((v.age() - 3) / 7)));
        var path = new ArrayList<Vec3>(7);
        for (int i = 0; i <= 6; i++) {
            double along = i / 6.0;
            Vec3 point = from.add(delta.scale(tail + (head - tail) * along));
            path.add(point.add(0, .16 * Math.sin(Math.PI * along), 0));
        }
        c.ribbon4(LINE, CanonFx4.Mode.ALPHA, path,
                along -> .46 * Math.sin(Math.PI * along), along -> .95 * opacity, AIR, 1, 0);
    }
    /** Open pressure foils advance only along this accepted target's ten-tick HIT lane. */
    private static void barrage(Canvas c, View v, double e) {
        Vec3 from = v.origin(), to = v.endpoint(), delta = to.subtract(from);
        if (delta.lengthSqr() < 1e-6) return;
        Vec3 axis = delta.normalize(), side = rightOf(axis), up = upOf(axis, side);
        double age = v.age(), head = easeOut(clamp01(age / Math.min(3, v.life() * .45)));
        double tail = Math.max(0, head - .46 - .15 * (1 - head));
        if (head > tail) for (int k = -1; k <= 1; k++) {
            var points = new ArrayList<Vec3>(5);
            for (int i = 0; i <= 4; i++) {
                double u = i / 4.0, progress = tail + (head - tail) * u;
                double open = Math.sin(Math.PI * u);
                points.add(from.add(delta.scale(progress)).add(side.scale(k * .28 * open))
                        .add(up.scale(.10 * open * Math.sin(age * .9 + k))));
            }
            stream(c, points, k == 0 ? .19 : .12, e * (k == 0 ? 1 : .78), age * 2);
        }
        if (head < 1) return;
        double impact = clamp01((age - Math.min(3, v.life() * .45)) / Math.max(1, v.life() - 3));
        double lip = .26 + .46 * easeOut(impact);
        c.cellPlane("fx4:ah_cone", CanonFx4.Mode.ALPHA, to, side.scale(lip), up.scale(lip * .55),
                0, FLOW, (float) (.85 * e * (1 - impact)));
        c.refract(NOISE, false, to, lip, (float) (.55 * e * (1 - impact)), AIR);
    }
    private static void wall(Canvas c,View v,double e,double t){
        if(v.phase()==CanonVfx.Phase.HIT.ordinal()){contact(c,v,e,t);return;}
        if(v.phase()==CanonVfx.Phase.END.ordinal())return;
        // A curtain of rising air: a row of refraction panels with streams flowing upward through them.
        Vec3 f=flat(CanonBarrierGeometry.forward(v)),r=rightOf(f);double rise=easeOut(reveal(v,6));
        boolean around=v.skill().equals("aero_air_curtain");
        double w=around?v.radius():cn.academy.port.canon.CanonExpansionCombatA.barrierHalfWidth(v.skill());
        if(w<=0)return;
        Vec3 base=CanonBarrierGeometry.centre(v);int n=around?10:5;
        for(int k=0;k<n;k++){double ang=k*Math.PI*2/n;Vec3 off=around?f.scale(Math.cos(ang)*w).add(r.scale(Math.sin(ang)*w)):r.scale((k-2)*w*.42);
            Vec3 p=base.add(off);c.refract(NOISE,false,p,(around?.55:.28)*rise,(float)(.7*e),AIR);
            var up=new ArrayList<Vec3>();for(int i=0;i<8;i++)up.add(base.add(off).add(r.scale(.04*Math.sin(t*.3+i+k))).add(0,(i/7.0*2-1)*w*rise,0));stream(c,up,.12,e,t);}
    }
    private static void contact(Canvas c,View v,double e,double t){
        if(v.phase()!=CanonVfx.Phase.HIT.ordinal())return;
        Vec3 at=v.endpoint(),side=rightOf(flat(CanonBarrierGeometry.forward(v)));
        for(int k=-1;k<=1;k++){
            Vec3 from=at.add(0,k*.12,0),to=from.add(side.scale(.7)).add(0,.18,0);
            stream(c,line(from,to,5),.10,e,t);
        }
    }
    private static void vortex(Canvas c,View v,double e,double t,int tier){
        Vec3 g=v.endpoint().distanceToSqr(v.origin())<4?v.anchor():v.endpoint();double grow=easeOutBack(reveal(v,8)),H=(1.5+.5*tier)*grow;
        for(int k=0;k<4;k++){var spiral=new ArrayList<Vec3>();
            for(int i=0;i<18;i++){double s=i/17.,ang=t*.25+k*1.57+s*6;spiral.add(g.add(Math.cos(ang)*(.3+.9*s),s*H,Math.sin(ang)*(.3+.9*s)));}
            stream(c,spiral,.25,e,t);}
        var grid=Fx4Atlas.get("ah_vortex_sheet");
        c.cellPlane(VORTEX,CanonFx4.Mode.ADD,g.add(0,.05,0),new Vec3(1.4*grow,0,0),new Vec3(0,0,1.4*grow),grid.frame(frac(t*.03)),AIR,(float)(.6*e));
        c.refract(NOISE,false,g.add(0,H*.5,0),H*.45,(float)(.7*e),AIR);
    }
}

package cn.academy.port.client.canon;

import static cn.academy.port.client.canon.CanonFxScripts.*;

import cn.academy.port.canon.CanonVfx;
import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.client.canon.CanonFx.View;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/**
 * Dark Matter on FX4: white matter that "does not belong to this world". Feathers are real feather sprites (dm_feather_atlas) laid in
 * the wing plane, the matter bends what is behind it (SH5 dispersion), and feathers fly as a swarm (PT3). Ladder: level 3 refracts
 * the air around the effect (SH1), level 4 lets feathers fall, settle on the ground and dissolve (PT1), level 5 whites out the
 * caster's view and casts a huge feather of light on the ground (CM5, LT1). Presentation only.
 */
final class CanonFxDark {
    private static final float[] WHITE={1f,1f,1f},PEARL={.96f,.97f,1f},CYAN={.78f,.92f,1f},ROSE={1f,.9f,.97f};
    private static final String FEATHER="fx4:dm_feather_atlas",SHARD="fx4:dm_shard_atlas",NORMAL="fx4:dm_refraction_normal",LINE="fx4:common_speedline";
    enum Role { BLADE, VOLLEY, WINGS, GUARD, ORBIT, SYNTH }
    private static final Map<String,Role> ROLES=Map.ofEntries(
            Map.entry("dark_blade",Role.BLADE),Map.entry("dark_shard",Role.BLADE),Map.entry("dark_pierce",Role.BLADE),Map.entry("dark_shard_return",Role.BLADE),
            Map.entry("dark_spread",Role.VOLLEY),Map.entry("dark_sweep",Role.VOLLEY),Map.entry("dark_shard_salvo",Role.VOLLEY),Map.entry("dark_spear_fan",Role.VOLLEY),
            Map.entry("dark_matter_barrage",Role.VOLLEY),Map.entry("dark_rain",Role.VOLLEY),Map.entry("dark_shard_ring",Role.VOLLEY),
            Map.entry("dark_wings",Role.WINGS),Map.entry("dark_wing_brake",Role.WINGS),Map.entry("dark_wing_charge",Role.WINGS),Map.entry("dark_wing_rescue",Role.WINGS),
            Map.entry("dark_step",Role.WINGS),Map.entry("dark_lift",Role.WINGS),
            Map.entry("dark_feather_guard",Role.GUARD),Map.entry("dark_guard",Role.GUARD),Map.entry("dark_shard_screen",Role.GUARD),
            Map.entry("dark_panel_dome",Role.GUARD),Map.entry("dark_wing_shelter",Role.GUARD),
            Map.entry("dark_shard_orbit",Role.ORBIT),Map.entry("dark_construct",Role.ORBIT),Map.entry("dark_synthesis",Role.SYNTH));
    private CanonFxDark() {}
    static Role role(String skill){return ROLES.getOrDefault(skill,Role.BLADE);}
    static double arrive(double life){return Math.min(life*.4,4);}

    // ------------------------------------------------------------------ one-shot work
    static void onReceive(CanonVfx.Visual v,int tier,boolean local,double life){
        if(v.phase()==CanonVfx.Phase.END)return;
        Role role=role(v.skill());Vec3 a=v.origin(),b=v.endpoint();int hit=(int)Math.round(arrive(life));
        switch(role){
            case BLADE,VOLLEY -> CanonFxForms.later(hit,()->{
                for(int k=0;k<(role==Role.VOLLEY?6:10);k++){double ang=rnd(k+17)*6.28,sp=.05+.07*rnd(k+3);
                    CanonParticles.spawn("dm_shard_atlas",k,b,new Vec3(Math.cos(ang)*sp,.06+.08*rnd(k+5),Math.sin(ang)*sp),.02,.04,.05+.04*rnd(k),.3,
                            18,PEARL,.9f,CanonFx4.Mode.ALPHA,CanonParticles.Land.NONE,false,0);}
                if(tier>=4)feathers(b,4,tier);
                if(local){CanonCamera.shake(.25+.12*tier,5+tier);CanonCamera.freeze(20+10*tier);}
                if(tier>=5)groundFeather(b,tier,local);
            });
            case WINGS -> {
                // Loose feathers keep drifting off the wings for the whole effect.
                Vec3 body=v.origin();for(int w=0;w<Math.min(12,(int)(life/8));w++){final int wave=w;
                    CanonFxForms.later(3+wave*8,()->{var mc=net.minecraft.client.Minecraft.getInstance();if(mc.level==null)return;
                        var e=mc.level.getEntity(v.entity());Vec3 at=e==null?body:e.position().add(0,1.5,0);feathers(at,2,tier);});}
                if(local){CanonCamera.fovKick(.06,12);if(tier>=5)CanonCamera.tint(WHITE,.35,16);}
                if(tier>=5)groundFeather(body,tier,local);
            }
            case GUARD -> {if(local)CanonCamera.shake(.2,5);if(tier>=4)CanonFxForms.later((int)life-6,()->feathers(b,5,tier));}
            case ORBIT -> {if(local&&tier>=5)CanonCamera.tint(WHITE,.3,14);if(tier>=5)groundFeather(v.origin(),tier,local);}
            case SYNTH -> {
                // A short-lived material burst remains legible after the ten-tick
                // server HIT visual, without creating any world item or entity.
                for(int k=0;k<6;k++){double ang=k*Math.PI/3;
                    CanonParticles.spawn("dm_shard_atlas",k+2,b.add(0,.15,0),
                            new Vec3(Math.cos(ang)*.075,.025+.015*(k%3),Math.sin(ang)*.075),.004,.06,.13+.02*(k%2),.12,
                            16,PEARL,.9f,CanonFx4.Mode.ALPHA,CanonParticles.Land.NONE,false,0);}
                if(local){CanonCamera.tint(WHITE,.3,10);CanonCamera.freeze(60);}
            }
        }
    }
    /** PT3/PT1: feathers leave, sway as they fall and, from level 4, lie on the ground for a while before dissolving. */
    private static void feathers(Vec3 at,int n,int tier){
        for(int k=0;k<n;k++){double ang=rnd(k*5+(int)(at.x*7))*6.28;
            CanonParticles.spawn("dm_feather_atlas",k+(int)(at.z*3),at.add(Math.cos(ang)*.4,rnd(k+2)*.4,Math.sin(ang)*.4),
                    new Vec3(Math.cos(ang)*.04,.03,Math.sin(ang)*.04),.006,.06,.16+.06*rnd(k+9),.12*(rnd(k)-.5),tier>=4?140:60,PEARL,.95f,
                    CanonFx4.Mode.ALPHA,tier>=4?CanonParticles.Land.STOP:CanonParticles.Land.NONE,false,0);}
    }
    /** LT1 (level 5): a huge feather of light falls across the ground; the caster's view whites out for a moment (CM5). */
    private static void groundFeather(Vec3 at,int tier,boolean local){
        CanonDecals.light("dm_feather_atlas",at,3.4,WHITE,.5f,50);
        if(local){CanonCamera.tint(WHITE,.45,14);CanonCamera.shake(.5,8);}
    }

    // ------------------------------------------------------------------ per frame
    static void draw(Canvas c,View v){
        double e=env(v);if(e<=0)return;double t=time(v);int tier=Math.max(1,Math.min(5,v.tier()));
        switch(role(v.skill())){
            case BLADE -> blade(c,v,e,t,tier);
            case VOLLEY -> volley(c,v,e,t,tier);
            case WINGS -> wings(c,v,e,t,tier);
            case GUARD -> guard(c,v,e,t,tier);
            case ORBIT -> orbit(c,v,e,t,tier);
            case SYNTH -> synth(c,v,e,t);
        }
    }
    /** SH5: the matter bends the scene behind it; level 3+ adds a wider SH1 shimmer of the air around it. */
    private static void bend(Canvas c,Vec3 at,double size,double e,int tier){
        c.refract(NORMAL,true,at,size,(float)(.9*e),CYAN);
        if(tier>=3)c.refract(NORMAL,false,at,size*1.8,(float)(.55*e),WHITE);
    }

    private static void blade(Canvas c,View v,double e,double t,int tier){
        Vec3 a=v.origin(),b=v.endpoint();double cut=easeOut(reveal(v,4)),thin=1-.7*clamp01((life01(v)-.35)/.65);Vec3 tip=a.add(b.subtract(a).scale(cut));
        // A white edge with a rainbow fringe on each side, sweeping once and thinning out.
        c.ribbon4(LINE,CanonFx4.Mode.ADD,line(a,tip,3),x->.16*(1-x*.5)*thin,x->.95*e,WHITE,1,0);
        c.ribbon4(LINE,CanonFx4.Mode.ADD,line(a.add(0,.05,0),tip.add(0,.05,0),3),x->.3*thin,x->.3*e,CYAN,1,0);
        c.ribbon4(LINE,CanonFx4.Mode.ADD,line(a.add(0,-.05,0),tip.add(0,-.05,0),3),x->.3*thin,x->.3*e,ROSE,1,0);
        Vec3 d=b.subtract(a);double L=Math.max(.5,d.length());Vec3 dir=d.scale(1/L),side=rightOf(dir);
        c.cellPlane(FEATHER,CanonFx4.Mode.ALPHA,tip.subtract(dir.scale(.35)),dir.scale(.4),side.scale(.12),(int)(rnd(v.skill().length())*8),PEARL,(float)(.95*e));
        bend(c,tip,.45,e*(1-life01(v)),tier);
    }
    private static void volley(Canvas c,View v,double e,double t,int tier){
        Vec3 a=v.skill().equals("dark_rain")?v.endpoint().add(0,4.5,0):v.origin(),b=v.endpoint();
        double fly=easeIn(clamp01(v.age()/Math.max(1,arrive(v.life()))));if(fly>=1){bend(c,b,.5*(1-life01(v)),e,tier);return;}
        // Each projectile is a feather riding a curved path; siblings bow to different sides so a volley fans out.
        Vec3 d=b.subtract(a),side=rightOf(d.normalize());double bow=(rnd(v.skill().hashCode()+(int)(b.x*13+b.z*7))-.5)*1.6;
        java.util.function.DoubleFunction<Vec3> path=s->a.add(d.scale(s)).add(side.scale(bow*Math.sin(Math.PI*s))).add(0,.4*Math.sin(Math.PI*s),0);
        Vec3 p=path.apply(fly),q=path.apply(Math.max(0,fly-.18));
        c.ribbon4(LINE,CanonFx4.Mode.ADD,line(q,p,2),x->.08*x,x->.6*e*x,CYAN,1,0);
        Vec3 dir=p.subtract(q);dir=dir.lengthSqr()<1e-6?d.normalize():dir.normalize();
        c.cellPlane(FEATHER,CanonFx4.Mode.ALPHA,p,dir.scale(.32),rightOf(dir).scale(.09),(int)(rnd((int)(b.x*31))*8),PEARL,(float)e);
    }
    private static void wings(Canvas c,View v,double e,double t,int tier){
        Vec3 f=flat(Vec3.directionFromRotation(0,v.yaw())),back=f.scale(-1),r=rightOf(f),root=v.anchor().add(0,1.32,0).add(back.scale(.16));
        int per=Math.min(7,3+tier);
        for(int side=-1;side<=1;side+=2)for(int k=0;k<per;k++){
            // Feathers unfurl one after another with an overshoot; the two wings are deliberately not mirror images.
            double unfurl=easeOutBack(clamp01((v.age()-k*1.2)/6));if(v.phase()==3)unfurl*=e;if(unfurl<=0)continue;
            double asym=side<0?.92:1.08,spread=Math.toRadians(10+k*(70.0/per))+Math.sin(t*.08+side*.5+k*.4)*.05;
            double len=(1.0+k*.12)*asym*unfurl;
            Vec3 dir=r.scale(side*Math.sin(spread)).add(new Vec3(0,Math.cos(spread)*.8,0)).add(back.scale(.3+.04*k)).normalize();
            Vec3 perp=dir.cross(f);perp=perp.lengthSqr()<1e-6?r:perp.normalize();
            c.cellPlane(FEATHER,CanonFx4.Mode.ALPHA,root.add(dir.scale(len*.5)),dir.scale(len*.5),perp.scale(.13+.015*k),k+(side<0?3:0),PEARL,(float)(.97*e));
        }
        bend(c,root.add(back.scale(.3)),1.0,e,tier);
    }
    private static void guard(Canvas c,View v,double e,double t,int tier){
        if(v.skill().equals("dark_shard_screen")){
            Vec3 centre=CanonBarrierGeometry.centre(v),r=rightOf(flat(CanonBarrierGeometry.forward(v)));double w=cn.academy.port.canon.CanonExpansionCombatA.barrierHalfWidth(v.skill());
            for(int i=-1;i<=1;i++)for(int j=-1;j<=1;j++)c.cellPlane(SHARD,CanonFx4.Mode.ALPHA,centre.add(r.scale(i*w*.6)).add(0,j*w*.6,0),r.scale(w*.25),new Vec3(0,w*.25,0),i+3*j+4,PEARL,(float)(.8*e));
            return;
        }
        boolean dome=v.skill().equals("dark_panel_dome")||v.skill().equals("dark_wing_shelter")||v.skill().equals("dark_guard");
        boolean ally="dark_wing_shelter".equals(v.skill()), panel="dark_panel_dome".equals(v.skill());
        if (v.phase()==CanonVfx.Phase.END.ordinal() || ally && v.phase()!=CanonVfx.Phase.START.ordinal()) return;
        Vec3 f=flat(Vec3.directionFromRotation(0,v.yaw())),r=rightOf(f);
        if(panel && v.phase()==CanonVfx.Phase.HIT.ordinal()){
            c.cellPlane(SHARD,CanonFx4.Mode.ALPHA,v.endpoint(),r.scale(.32),new Vec3(0,.32,0),4,PEARL,(float)e);
            return;
        }
        Vec3 centre=panel?CanonBarrierGeometry.centre(v):(ally?v.endpoint():v.anchor()).add(0,1,0);
        double p=life01(v),out=clamp01((p-.8)/.2);
        int n=dome?18:9;
        double R=panel?v.radius():ally?1.05:dome?2.1:2;
        if(R<=0)return;
        for(int k=0;k<n;k++){
            // Crystal panels fly in from scattered positions, overshoot into place and slowly turn.
            double come=easeOut(clamp01((v.age()-k*.5)/6)),ang,el;
            if(dome){ang=k*2.399+t*.01;el=Math.asin(clamp01((k+.5)/n))*1.0;}else{ang=(k/(double)(n-1)-.5)*.7;el=(k%3-1)*.35;}
            Vec3 dir=dome?new Vec3(Math.cos(ang)*Math.cos(el),Math.sin(el),Math.sin(ang)*Math.cos(el))
                    :f.scale(Math.cos(ang)).add(r.scale(Math.sin(ang))).scale(Math.cos(el)).add(0,Math.sin(el),0);
            Vec3 home=centre.add(dir.scale(R)),from=centre.add(dir.scale(panel||ally?R*.58:R*3)).add(0,panel||ally?0:1.5*rnd(k),0),at=from.add(home.subtract(from).scale(come));
            Vec3 ax=rightOf(dir).scale(.5),ay=upOf(dir,rightOf(dir)).scale(.5);
            c.cellPlane(SHARD,out>0?CanonFx4.Mode.DISSOLVE:CanonFx4.Mode.ALPHA,at,ax,ay,k,PEARL,out>0?(float)out:(float)(.92*e));
        }
        if(tier>=3)c.refract(NORMAL,true,centre.add(dome?Vec3.ZERO:f.scale(R)),dome?R*1.6:1.0,(float)(.7*e*(1-out)),CYAN);
    }
    private static void orbit(Canvas c,View v,double e,double t,int tier){
        int n=v.skill().equals("dark_construct")?8:5;Vec3 centre=v.anchor().add(0,1.2,0);
        for(int k=0;k<n;k++){double a=t*.08+k*Math.PI*2/n,rad=1.1+.12*Math.sin(t*.1+k),y=Math.sin(t*.09+k*2)*.35;
            Vec3 p=centre.add(Math.cos(a)*rad,y,Math.sin(a)*rad),tang=new Vec3(-Math.sin(a),0,Math.cos(a));
            c.cellPlane(SHARD,CanonFx4.Mode.ALPHA,p,tang.scale(.18),new Vec3(0,.18,0),k+3,PEARL,(float)(.95*e));
            c.ribbon4(LINE,CanonFx4.Mode.ADD,line(p.subtract(tang.scale(.6)),p,2),x->.05*x,x->.4*e*x,CYAN,1,0);
            if(tier>=3&&k%2==0)c.refract(NORMAL,true,p,.3,(float)(.8*e),CYAN);}
    }
    private static void synth(Canvas c,View v,double e,double t){
        // Material crystallises at the held item, then visibly forks into two
        // faceted copies. Alpha faces carry the form even with shaders disabled.
        Vec3 f=flat(Vec3.directionFromRotation(0,v.yaw())),r=rightOf(f);
        Vec3 hand=v.anchor().add(0,1.18,0).add(f.scale(.67));
        double grow=easeOut(reveal(v,3)),split=easeOutBack(reveal(v,7));
        for(int side=-1;side<=1;side+=2){
            Vec3 at=hand.add(r.scale(side*(.06+.35*split))).add(0,.10*Math.sin(Math.PI*life01(v)),0);
            crystal(c,at,r,f,.17*grow,.37*grow,t*.055*side,(float)(.96*e),side<0?2:9);
            c.refract(NORMAL,true,at,.48,(float)(.65*e),side<0?CYAN:ROSE);
        }
        c.ribbon4(LINE,CanonFx4.Mode.ALPHA,line(hand.subtract(r.scale(.42*split)),hand.add(r.scale(.42*split)),3),
                x->.10,x->.65*e*(1-life01(v)),CYAN,1,0);
    }
    /** Eight triangular faces sampled from an opaque inner patch of the shard atlas. */
    private static void crystal(Canvas c,Vec3 at,Vec3 right,Vec3 forward,double radius,double height,double spin,float alpha,int cell){
        if(radius<=0||height<=0)return;
        float[] tile=Fx4Atlas.get("dm_shard_atlas").cell(cell);
        float u=(tile[0]+tile[2])*.5f,v=(tile[1]+tile[3])*.5f,du=(tile[2]-tile[0])*.075f,dv=(tile[3]-tile[1])*.075f;
        float[] uv={u-du,v-dv,u+du,v+dv};
        Vec3 top=at.add(0,height,0),bottom=at.add(0,-height*.72,0);
        Vec3[] rim=new Vec3[4];
        for(int k=0;k<4;k++){double a=spin+k*Math.PI*.5;rim[k]=at.add(right.scale(Math.cos(a)*radius)).add(forward.scale(Math.sin(a)*radius));}
        for(int k=0;k<4;k++){
            float[] rgb=k%2==0?PEARL:CYAN;float shade=k%2==0?1f:.76f;
            c.fx4Quad(SHARD,CanonFx4.Mode.ALPHA,top,rim[k],rim[(k+1)%4],top,uv,rgb[0]*shade,rgb[1]*shade,rgb[2]*shade,alpha);
            c.fx4Quad(SHARD,CanonFx4.Mode.ALPHA,bottom,rim[(k+1)%4],rim[k],bottom,uv,rgb[0]*.69f,rgb[1]*.72f,rgb[2]*.78f,alpha);
        }
    }
}

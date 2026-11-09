package cn.academy.port.client.canon;

import static cn.academy.port.client.canon.CanonFxScripts.*;

import cn.academy.port.canon.CanonVfx;
import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.client.canon.CanonFx.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Liquid Shadow on FX4: dark, wet liquid that rises out of the ground, can hear and touch but not see. Tendrils are real round
 * tubes of wet ink (GM1) that stretch, overshoot and snap back; bodies well up from and sink into the ground with a dissolve (SH2);
 * ink drops fall and splash (PT2). Ladder: level 3 leaves a shadow pool on the ground (DC1), level 4 stains the creatures it touches
 * (DC2), level 5 darkens the caster's surroundings and splashes ink across the screen edges (LT3, CM6). Drawn with alpha blending
 * only, so the dark ink is never washed out by additive light. Presentation only.
 */
final class CanonFxLiquid {
    private static final float[] INK={.11f,.08f,.17f},RIM={.62f,.5f,.92f},WET={.32f,.24f,.48f};
    static final String FIELD_SKILL="liquid_shadow_field";
    private static final float[] FIELD_FILM={.92f,.83f,.98f},FIELD_EDGE={.92f,.95f,1f};
    private static final String POOL="fx4:ls_pool_decal";
    private static final float[] RIBBON_UV={0,1,1,0};
    private static final String RIBBON="fx4:ls_wet_ribbon",TENDRIL="fx4:ls_tendril",INKS="fx4:ls_ink_sheet",RIPPLE="fx4:hh_ripple_sheet";
    enum Role { BODY, SENSE, STRIKE, WALL, FIELD }
    private static final Map<String,Role> ROLES=Map.ofEntries(
            Map.entry("liquid_scout",Role.BODY),Map.entry("liquid_recall",Role.BODY),Map.entry("liquid_dismiss",Role.BODY),Map.entry("liquid_follow",Role.BODY),
            Map.entry("liquid_pause",Role.BODY),Map.entry("liquid_retreat",Role.BODY),Map.entry("liquid_turn",Role.BODY),Map.entry("liquid_route",Role.BODY),
            Map.entry("liquid_path",Role.BODY),Map.entry("liquid_switch",Role.BODY),
            Map.entry("liquid_listen",Role.SENSE),Map.entry("liquid_survey",Role.SENSE),Map.entry("liquid_dual_sensor",Role.SENSE),
            Map.entry("liquid_strike",Role.STRIKE),Map.entry("liquid_contact_cut",Role.STRIKE),Map.entry("liquid_contact_sweep",Role.STRIKE),
            Map.entry("liquid_contact_crush",Role.STRIKE),Map.entry("liquid_grapple",Role.STRIKE),Map.entry("liquid_push",Role.STRIKE),
            Map.entry("liquid_pulse",Role.STRIKE),Map.entry("liquid_intercept",Role.STRIKE),
            Map.entry("liquid_guard",Role.WALL),Map.entry("liquid_screen",Role.WALL),Map.entry(FIELD_SKILL,Role.FIELD));
    /** DC2: ink stains following an entity for a few seconds (entity id, client tick it ends). */
    private record Stain(int entity,long until,int seed) {}
    private static final List<Stain> STAINS=new ArrayList<>();
    private CanonFxLiquid() {}
    static Role role(String skill){return ROLES.getOrDefault(skill,Role.BODY);}
    static double arrive(double life){return Math.min(life*.4,5);}

    // ------------------------------------------------------------------ one-shot work
    static void onReceive(CanonVfx.Visual v,int tier,boolean local,double life){
        if(v.phase()==CanonVfx.Phase.END)return;
        Role role=role(v.skill());Vec3 a=v.origin(),b=v.endpoint();
        switch(role){
            case STRIKE -> CanonFxForms.later((int)Math.round(arrive(life)),()->{
                drops(b,10);
                if(tier>=3)CanonDecals.add("ls_pool_decal",b,.7+.2*tier,INK,.85f,120);
                if(tier>=4)stain(b);
                if(local){CanonCamera.shake(.25+.1*tier,6);CanonCamera.freeze(25+10*tier);
                    if(tier>=5){CanonCamera.darken(.55,30);CanonCamera.edge("ink",.9,40);}}
            });
            case FIELD -> {
                if(v.phase()!=CanonVfx.Phase.HIT)return;
                drops(b,4);
                CanonDecals.add("ls_pool_decal",b,.5,FIELD_FILM,.70f,Math.min(36,(int)life*3));
                if(local&&v.radius()>0){CanonCamera.shake(.25,5);CanonCamera.edge("ink",.32,12);}
            }
            case BODY -> {
                if(tier>=3||v.skill().equals("liquid_scout"))CanonDecals.add("ls_pool_decal",b,1.1,INK,.85f,(int)life+80);
                CanonFxForms.later(2,()->drops(b.add(0,1.4,0),6));
            }
            case SENSE -> {if(tier>=3)CanonDecals.add("ls_pool_decal",b,.9,INK,.7f,(int)life+40);}
            case WALL -> {CanonDecals.add("ls_pool_decal",b,1.2,INK,.8f,(int)life+40);if(local)CanonCamera.shake(.2,5);}
        }
    }
    private static void drops(Vec3 at,int n){
        for(int k=0;k<n;k++){double ang=rnd(k+(int)(at.x*11))*6.28,sp=.03+.05*rnd(k+3);
            CanonParticles.spawn("ls_drip_sheet",0,at.add(0,.2,0),new Vec3(Math.cos(ang)*sp,.06+.06*rnd(k),Math.sin(ang)*sp),.03,.02,.07+.04*rnd(k+5),0,
                    30,WET,.95f,CanonFx4.Mode.ALPHA,CanonParticles.Land.SPLASH,true,0);}
    }
    /** Marks the creature nearest the hit point; the stain is drawn on it until it fades. */
    private static void stain(Vec3 at){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        var near=mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new net.minecraft.world.phys.AABB(at,at).inflate(1.2),x->x!=mc.player);
        long now=mc.level.getGameTime();
        for(var e:near){if(STAINS.size()>=24)STAINS.remove(0);STAINS.add(new Stain(e.getId(),now+80,e.getId()*7));}
    }

    // ------------------------------------------------------------------ per frame
    static void draw(Canvas c,View v){
        if(role(v.skill())==Role.FIELD)return; // The area and each contact are drawn by their dedicated layers.
        double e=env(v);if(e<=0)return;double t=time(v);
        switch(role(v.skill())){
            case BODY -> body(c,v,e,t);
            case SENSE -> sense(c,v,e,t);
            case STRIKE -> strike(c,v,e,t,v.skill().equals("liquid_contact_sweep")||v.skill().equals("liquid_pulse")?3:1);
            case WALL -> wall(c,v,e,t);
            case FIELD -> { }
        }
        drawStains(c);
    }
    private static void drawStains(Canvas c){
        var mc=Minecraft.getInstance();if(mc.level==null||STAINS.isEmpty())return;long now=mc.level.getGameTime();
        STAINS.removeIf(s->now>=s.until());var grid=Fx4Atlas.get("ls_ink_sheet");
        for(Stain s:STAINS){var e=mc.level.getEntity(s.entity());if(e==null)continue;double left=(s.until()-now)/80.;
            for(int k=0;k<3;k++){Vec3 p=e.position().add((rnd(s.seed()+k)-.5)*e.getBbWidth(),.3+rnd(s.seed()+k+5)*e.getBbHeight()*.8,(rnd(s.seed()+k+9)-.5)*e.getBbWidth());
                c.cellSprite(INKS,CanonFx4.Mode.ALPHA,p,.16+.06*rnd(s.seed()+k),rnd(k)*6,grid.frame(.35),INK,(float)Math.min(.9,left*1.5));}}
    }
    /** A wet ink tendril: tube body with a violet rim line; u runs from root (thick) to tip (thin). */
    private static void tendril(Canvas c,List<Vec3> path,double thick,double e,double t){
        if(path.size()<2)return;
        c.tube(RIBBON,CanonFx4.Mode.FLOW,path,x->thick*(1-.75*x)+.015,x->.97*e,INK,2,-t*.05,7);
        c.ribbon4(TENDRIL,CanonFx4.Mode.ALPHA,path,x->thick*.9*(1-.7*x),x->.5*e,RIM,1,0);
    }
    private static void body(Canvas c,View v,double e,double t){
        // The body wells up out of a pool and sinks back: a column of ink dissolving in at the start and out at the end.
        Vec3 p=v.endpoint();Double y=CanonDecals.surfaceAt(p);Vec3 g=y==null?p:new Vec3(p.x,y,p.z);
        double rise=easeOutBack(reveal(v,8)),p01=life01(v),sink=v.skill().equals("liquid_dismiss")||v.skill().equals("liquid_recall")?1-easeOut(p01):1;
        var grid=Fx4Atlas.get("ls_ink_sheet");
        c.cellPlane(INKS,CanonFx4.Mode.ALPHA,g.add(0,.02,0),new Vec3(.9,0,0),new Vec3(0,0,.9),grid.frame(.5),INK,(float)(.6*e));
        for(int k=0;k<3;k++){var col=new ArrayList<Vec3>();double h=1.6*rise*sink;
            for(int i=0;i<10;i++){double s=i/9.,ang=t*.12+k*2.1+s*1.5;col.add(g.add(Math.cos(ang)*.22*(1-s*.5),s*h,Math.sin(ang)*.22*(1-s*.5)));}
            tendril(c,col,.14,e*(p01>.8&&sink<1?0:1),t);}
        if(p01<.15)c.cellSprite(INKS,CanonFx4.Mode.DISSOLVE,g.add(0,.9*rise,0),.7,0,grid.frame(p01*4),INK,(float)(p01/.15));
    }
    private static void sense(Canvas c,View v,double e,double t){
        // Hearing and touch: dark rings run out along the ground, and fine tendrils feel their way outward.
        Vec3 p=v.endpoint();Double y=CanonDecals.surfaceAt(p);Vec3 g=y==null?p:new Vec3(p.x,y+.03,p.z);var grid=Fx4Atlas.get("hh_ripple_sheet");
        for(int k=0;k<3;k++){double ph=frac(t*.025+k/3.);c.cellPlane(RIPPLE,CanonFx4.Mode.ALPHA,g.add(0,k*.002,0),new Vec3(.6+ph*2.4,0,0),new Vec3(0,0,.6+ph*2.4),grid.frame(ph),RIM,(float)(.55*e*(1-ph)));}
        for(int k=0;k<5;k++){double ang=k*1.257+rnd(k)*.4,reach=easeOut(reveal(v,8))*(1.6+rnd(k+3));var path=new ArrayList<Vec3>();
            for(int i=0;i<8;i++){double s=i/7.;path.add(g.add(Math.cos(ang+Math.sin(s*3+t*.2)*.2)*reach*s,.05+.08*Math.sin(Math.PI*s),Math.sin(ang+Math.sin(s*3+t*.2)*.2)*reach*s));}
            tendril(c,path,.05,e,t);}
    }
    private static void strike(Canvas c,View v,double e,double t,int count){
        Vec3 a=v.origin(),b=v.endpoint();double arr=arrive(v.life()),since=v.age()-arr;
        // Stretch past the target with an overshoot, hold, then the surface tension pulls it back, thinning as it goes.
        double reach=easeOutBack(clamp01(v.age()/Math.max(1,arr))),back=easeIn(clamp01((life01(v)-.6)/.4)),len=Math.max(0,reach*(1-back));
        for(int k=0;k<count;k++){
            Vec3 bend=new Vec3(0,1,0).add(rightOf(b.subtract(a).normalize()).scale((k-(count-1)/2.)*.8));
            var full=CanonFx.wave(a,b.add(b.subtract(a).normalize().scale(.25)),14,.25,1.3,-t*.2+k,bend);
            tendril(c,partial(full,len),.13,e,t);
        }
        if(since>=0&&since<10){var grid=Fx4Atlas.get("ls_ink_sheet");c.cellSprite(INKS,CanonFx4.Mode.ALPHA,b,.8,rnd(3)*6,grid.frame(since/10),INK,(float)(.6*e));}
    }
    private static void wall(Canvas c,View v,double e,double t){
        Vec3 f=flat(Vec3.directionFromRotation(0,v.yaw())),r=rightOf(f),base=v.anchor().add(f.scale(1.0));
        double rise=easeOutBack(reveal(v,7)),out=clamp01((life01(v)-.75)/.25);
        for(int k=0;k<7;k++){double s=k/6.-.5;var col=new ArrayList<Vec3>();
            for(int i=0;i<9;i++){double h=i/8.;col.add(base.add(r.scale(s*2.2+.05*Math.sin(t*.2+k+h*3))).add(f.scale(.15*Math.sin(h*3+k))).add(0,h*2.0*rise*(1-out),0));}
            tendril(c,col,.12,e,t);}
    }
    /** A target receipt does not grant an area. It paints only a low splash at the accepted hit. */
    static void fieldContact(Canvas c,View v,double opacity){
        if(v.phase()!=CanonVfx.Phase.HIT.ordinal())return;
        double progress=life01(v),size=.44+.46*easeOut(progress);
        Vec3 hit=v.endpoint().add(0,.25,0);
        c.cellSprite(INKS,CanonFx4.Mode.ALPHA,hit,size,0,Fx4Atlas.get("ls_ink_sheet").frame(progress),FIELD_FILM,(float)(.92*opacity));
    }

    private record InkCrescent(Vec3 innerStart,Vec3 innerEnd,Vec3 edgeEnd,Vec3 edgeStart,double reach) {}
    record InkField(Vec3 foot,List<InkCrescent> crescents) {}

    /** Sample support once per authoritative area, never across a ledge or a raised blocker. */
    static InkField fieldAt(Vec3 origin,double radius){
        Vec3 foot=fieldSurface(origin);
        if(foot==null)return new InkField(null,List.of());
        var crescents=new ArrayList<InkCrescent>();
        for(int band=0;band<3;band++){
            int count=6+4*band;
            double reach=.42+.27*band;
            for(int i=0;i<count;i++){
                double a=(i+.3*band)*2*Math.PI/count;
                double span=(.56+.31*rnd(i+band*17))*2*Math.PI/count;
                double outer=radius*(reach+.03*rnd(i+9)),inner=outer-radius*(.105+.035*rnd(i+23));
                Vec3 innerStart=fieldSurface(radial(foot,a-.04,inner));
                Vec3 innerEnd=fieldSurface(radial(foot,a+span-.04,inner));
                Vec3 edgeEnd=fieldSurface(radial(foot,a+span,outer));
                Vec3 edgeStart=fieldSurface(radial(foot,a,outer));
                if(innerStart!=null&&innerEnd!=null&&edgeEnd!=null&&edgeStart!=null)
                    crescents.add(new InkCrescent(innerStart,innerEnd,edgeEnd,edgeStart,reach));
            }
        }
        return new InkField(foot,List.copyOf(crescents));
    }
    private static Vec3 radial(Vec3 origin,double a,double r){return origin.add(Math.cos(a)*r,0,Math.sin(a)*r);}
    private static Vec3 fieldSurface(Vec3 at){
        Double y=CanonDecals.surfaceAt(at.add(0,.15,0));
        if(y==null||Math.abs(y-at.y)>.16)return null;
        return new Vec3(at.x,y+.025,at.z);
    }

    /** Three broken bands spread from the body and retract over the packet lifetime. */
    static void drawField(Canvas c,InkField field,double radius,double progress,double opacity){
        if(field.foot()==null)return;
        double spread=easeOut(clamp01(progress/.32)),retract=easeIn(clamp01((progress-.64)/.36));
        double extent=spread*(1-retract),pool=Math.min(1.5,radius*.24)*extent;
        c.cellPlane(POOL,CanonFx4.Mode.ALPHA,field.foot(),new Vec3(pool,0,0),new Vec3(0,0,pool),0,FIELD_FILM,(float)(.94*opacity));
        for(InkCrescent arc:field.crescents()){
            double alpha=opacity*clamp01((extent-arc.reach()+.04)/.13);
            if(alpha<=.01)continue;
            c.fx4Quad(RIBBON,CanonFx4.Mode.ALPHA,arc.innerStart(),arc.innerEnd(),arc.edgeEnd(),arc.edgeStart(),RIBBON_UV,
                    FIELD_FILM[0],FIELD_FILM[1],FIELD_FILM[2],(float)(.90*alpha));
            Vec3 start=arc.innerStart().lerp(arc.edgeStart(),.74).add(0,.014,0),end=arc.innerEnd().lerp(arc.edgeEnd(),.74).add(0,.014,0);
            c.fx4Quad(RIBBON,CanonFx4.Mode.ALPHA,start,end,arc.edgeEnd().add(0,.075,0),arc.edgeStart().add(0,.075,0),RIBBON_UV,
                    FIELD_EDGE[0],FIELD_EDGE[1],FIELD_EDGE[2],(float)(.73*alpha));
        }
    }
}

package cn.academy.port.client.canon;

import static cn.academy.port.client.canon.CanonFxScripts.*;

import cn.academy.port.VisualTierConfig;
import cn.academy.port.canon.CanonVfx;
import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.client.canon.CanonFx.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;

/**
 * Hydro Hand on FX4 (sample system of the presentation upgrade). Water is a real round tube with a flowing surface (GM1 + SH3),
 * droplets are physical and splash again where they land (PT2), and each level adds a different medium instead of a bigger ring:
 * level 3 leaves wet ground and ripples (DC1), level 4 wraps the target in a water globe (SH4), level 5 bends the view through the
 * water (SH1) and wets the caster's screen edges (CM6). Presentation only; it reads the same server-confirmed events as before.
 */
final class CanonFxHydro {
    private static final float[] WATER={.25f,.62f,1f},FOAM={.9f,.97f,1f},MIST={.75f,.9f,1f};
    private static final String RIBBON="fx4:hh_water_ribbon",DROPS="hh_drop_atlas",SPLASH="fx4:hh_splash_sheet",RIPPLE="fx4:hh_ripple_sheet",
            CURTAIN="fx4:hh_curtain",LINE="fx4:common_speedline",NOISE="fx4:common_noise_tile";
    enum Role { LASH, JET, FAN, CURRENT, CARRY, WALL, FIELD }
    private static final Map<String,Role> ROLES=Map.ofEntries(
            Map.entry("hydro_whip",Role.LASH),Map.entry("hydro_pressure_jet",Role.JET),Map.entry("hydro_fan",Role.FAN),
            Map.entry("hydro_current",Role.CURRENT),Map.entry("hydro_lateral_current",Role.CURRENT),Map.entry("hydro_undertow",Role.CURRENT),
            Map.entry("hydro_repel",Role.CURRENT),Map.entry("hydro_tow_ally",Role.CURRENT),Map.entry("hydro_dive",Role.CURRENT),
            Map.entry("hydro_lift",Role.CURRENT),Map.entry("hydro_brake",Role.CURRENT),
            Map.entry("hydro_transfer",Role.CARRY),Map.entry("hydro_douse_ally",Role.CARRY),Map.entry("hydro_douse_hostile",Role.CARRY),
            Map.entry("hydro_campfire_out",Role.CARRY),Map.entry("hydro_candle_out",Role.CARRY),Map.entry("hydro_extinguish",Role.CARRY),
            Map.entry("hydro_irrigate",Role.CARRY),
            Map.entry("hydro_shield",Role.WALL),Map.entry("hydro_rescue",Role.WALL),
            Map.entry("hydro_domain",Role.FIELD),Map.entry("hydro_eddy",Role.FIELD),Map.entry("hydro_brake_field",Role.FIELD),Map.entry("hydro_douse_self",Role.FIELD));
    private CanonFxHydro() {}

    static Role role(String skill){return ROLES.getOrDefault(skill,Role.LASH);}
    /** Ticks until the water reaches its target: at most 5, or 40% of a short life. */
    static double arrive(double life){return Math.min(life*.4,5);}
    private static boolean impacting(Role r){return r!=Role.WALL&&r!=Role.FIELD;}

    // ------------------------------------------------------------------ one-shot work per event: particles, decals, caster camera
    static void onReceive(CanonVfx.Visual v,int tier,boolean local,double life){
        if(v.phase()==CanonVfx.Phase.END)return;
        if ("hydro_domain".equals(v.skill())) {
            if (local && VisualTierConfig.tier().enhanced() && v.phase() == CanonVfx.Phase.START && v.radius() > 0) {
                CanonCamera.edge("water", .65, Math.min(36, v.duration()));
            }
            return;
        }
        Role role=role(v.skill());Vec3 a=v.origin(),b=v.endpoint();int hit=(int)Math.round(arrive(life));
        if(impacting(role)){
            // TM1 wind-up: water is drawn out of the air toward the hand before it is thrown.
            for(int k=0;k<8;k++){double ang=rnd(k*3+v.skill().length())*6.28,rad=.9+.4*rnd(k+5);
                Vec3 from=a.add(Math.cos(ang)*rad,(rnd(k+9)-.3)*.8,Math.sin(ang)*rad);
                CanonParticles.spawn(DROPS,k,from,a.subtract(from).scale(.28),0,.12,.05,0,5,WATER,.85f,CanonFx4.Mode.ALPHA,CanonParticles.Land.NONE,true,0);}
            CanonFxForms.later(hit,()->impact(role,a,b,tier,local));
        }else if(role==Role.WALL){
            Vec3 f=flat(b.subtract(a).lengthSqr()<.25?Vec3.directionFromRotation(0,0):b.subtract(a)),r=rightOf(f);
            Vec3 base=(b.distanceToSqr(a)<6.25?a.add(0,-1.2,0):a).add(f.scale(.95));
            for(int wave=0;wave<Math.min(8,(int)(life/6));wave++)
                CanonFxForms.later(4+wave*6,()->{for(int k=0;k<6;k++)CanonParticles.spawn(DROPS,k,base.add(r.scale((rnd(k)-.5)*2.0)).add(0,2.0,0),
                        new Vec3(0,-.05,0),.035,.01,.05,0,30,WATER,.8f,CanonFx4.Mode.ALPHA,CanonParticles.Land.SPLASH,true,0);});
            if(local)CanonCamera.shake(.25,6);
        }else{
            Vec3 p=b.distanceToSqr(a)<4?a.add(0,-1.2,0):b;
            for(int w=0;w<Math.min(10,(int)(life/5));w++){final int wave=w;
                CanonFxForms.later(wave*5,()->{for(int k=0;k<5;k++){double ang=rnd(k+31)*6.28+wave,rad=.3+1.5*rnd(k+wave);
                    CanonParticles.spawn(DROPS,k+wave,p.add(Math.cos(ang)*rad,.1,Math.sin(ang)*rad),new Vec3(0,.05+.04*rnd(k),0),-.004,.02,.035+.03*rnd(k+2),.2,
                            22,FOAM,.7f,CanonFx4.Mode.ALPHA,CanonParticles.Land.NONE,false,0);}});}
            if(tier>=3)CanonDecals.add("hh_wet_decal",p,1.4+.3*tier,WATER,.7f,(int)life+60);
            CanonDecals.light("common_ground_mask",p,1.6+.3*tier,WATER,.35f,(int)life);
            if(local&&tier>=5){CanonCamera.edge("water",1,(int)Math.min(80,life));CanonCamera.tint(MIST,.35,30);}
        }
    }
    private static void impact(Role role,Vec3 a,Vec3 b,int tier,boolean local){
        int n=role==Role.JET?10:role==Role.FAN?18:14;Vec3 d=b.subtract(a).normalize();
        for(int k=0;k<n;k++){double ang=rnd(k+71)*6.28,sp=.06+.08*rnd(k+3);
            Vec3 vel=new Vec3(Math.cos(ang)*sp,.12+.12*rnd(k+5),Math.sin(ang)*sp).add(d.scale(role==Role.JET?.12:.04));
            CanonParticles.spawn(DROPS,k,b,vel,.035,.01,.04+.035*rnd(k+9),0,40,tint(WATER,FOAM,rnd(k)*.5),.9f,CanonFx4.Mode.ALPHA,
                    tier>=3&&k%4==0?CanonParticles.Land.SPLASH_DECAL:CanonParticles.Land.SPLASH,true,0);}
        if(tier>=3)CanonDecals.add("hh_wet_decal",b,.7+.25*tier,WATER,.75f,120);
        if(local){
            CanonCamera.shake(.35+.18*tier,6+tier);CanonCamera.freeze(30+12*tier);
            if(tier>=5){CanonCamera.edge("water",.9,60);CanonCamera.tint(MIST,.3,24);}
        }
    }

    // ------------------------------------------------------------------ per-frame drawing
    static void draw(Canvas c,View v){
        double e=env(v);if(e<=0)return;double t=time(v);Role role=role(v.skill());int tier=Math.max(1,Math.min(5,v.tier()));
        if ("hydro_domain".equals(v.skill())) {
            domain(c, v, e);
            return;
        }
        switch(role){
            case LASH -> lash(c,v,e,t);
            case JET -> jet(c,v,e,t);
            case FAN -> fan(c,v,e,t);
            case CURRENT -> current(c,v,e,t);
            case CARRY -> carry(c,v,e,t);
            case WALL -> wall(c,v,e,t,tier);
            case FIELD -> field(c,v,e,t,tier);
        }
        double since=v.age()-arrive(v.life());
        if(impacting(role)&&since>=0){
            if(tier>=3)ripple(c,v.endpoint(),since,v.life()-arrive(v.life()),.8+.2*tier,e);
            if(tier>=4)globe(c,v.endpoint(),since,v.life()-arrive(v.life()),e,t);
            if(tier>=5)c.refract(NOISE,false,v.endpoint().add(0,.3,0),1.6*easeOut(since/6),(float)(.9*e*(1-life01(v))),MIST);
        }
    }

    /** The admitted flow moves water-bound targets along the cast's horizontal axis. */
    private static void domain(Canvas c, View v, double opacity) {
        ClientLevel level = Minecraft.getInstance().level;
        Vec3 centre = v.rangeCentre();
        if (level == null || !VisualTierConfig.tier().enhanced() || centre == null || v.radius() <= .6 || v.aimAxis() == null) return;
        Vec3 axis = v.aimAxis().multiply(1, 0, 1).normalize();
        if (axis.lengthSqr() < .99) return;
        Vec3 right = new Vec3(-axis.z, 0, axis.x);
        double reach = v.radius() - .55;
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        for (int lane = -2; lane <= 2; lane++) {
            double lateral = lane * reach * .22;
            double halfLength = Math.sqrt(reach * reach - lateral * lateral);
            int slices = Math.min(32, Math.max(1, (int)Math.ceil(halfLength * 2 / .8)));
            Vec3 previous = null;
            Vec3 width = right.scale(lane == 0 ? .42 : .31);
            for (int i = 0; i <= slices; i++) {
                double along = -halfLength + halfLength * 2 * i / slices;
                Vec3 horizontal = centre.add(right.scale(lateral)).add(axis.scale(along));
                Vec3 here = wetSurface(level, cell, horizontal);
                if (here != null && here.distanceToSqr(centre) > reach * reach) here = null;
                if (previous != null && here != null && Math.abs(previous.y - here.y) <= .3) {
                    double crest = frac(v.age() * .11 - (i - .5) * .21 + lane * .27);
                    float alpha = (float)(opacity * (.49 + .33 * Math.sin(crest * Math.PI)));
                    float scroll = (float)(-v.age() * .065 + i * .16);
                    c.fx4Quad(RIBBON, CanonFx4.Mode.FLOW, previous.subtract(width), previous.add(width),
                            here.add(width), here.subtract(width), new float[]{0, scroll, 1, scroll + .16f},
                            WATER[0], WATER[1], WATER[2], alpha);
                    if (crest < .23) {
                        c.cellStrip(LINE, CanonFx4.Mode.ALPHA, previous.add(0, .012, 0),
                                here.add(0, .012, 0), .09, 0, FOAM, (float)(opacity * (.8 - crest)));
                    }
                }
                previous = here;
            }
        }
    }

    private static Vec3 wetSurface(ClientLevel level, BlockPos.MutableBlockPos cell, Vec3 sample) {
        int x = (int)Math.floor(sample.x), y = (int)Math.floor(sample.y), z = (int)Math.floor(sample.z);
        cell.set(x, y, z);
        if (!level.hasChunkAt(cell)) return null;
        for (int offset = 1; offset >= -2; offset--) {
            cell.set(x, y + offset, z);
            var fluid = level.getFluidState(cell);
            if (fluid.is(FluidTags.WATER) && level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
                return new Vec3(sample.x, cell.getY() + fluid.getHeight(level, cell) + .023, sample.z);
            }
        }
        return null;
    }

    /** A short crest at the source reported by the accepted START event. */
    static void domainSource(Canvas c, Vec3 source, double age, double opacity) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !VisualTierConfig.tier().enhanced() || age >= 12) return;
        Vec3 surface = wetSurface(level, new BlockPos.MutableBlockPos(), source);
        if (surface != null) splash(c, surface, age, 13, .7, opacity);
    }

    /** GM1 + SH3: a round water tube with a flowing surface, a bright crest line and foam at the leading edge. */
    private static void water(Canvas c,List<Vec3> path,double thick,double e,double t,double length,boolean head){
        if(path.size()<2)return;
        c.tube(RIBBON,CanonFx4.Mode.FLOW,path,x->thick*(.75+.25*Math.sin(Math.PI*Math.max(.15,x))),x->.95*e,WATER,length*1.1,-t*.06,8);
        c.ribbon4(LINE,CanonFx4.Mode.ADD,path,x->thick*.45,x->.45*e,FOAM,1,0);
        if(head)c.cellSprite("fx4:"+DROPS,CanonFx4.Mode.ALPHA,path.get(path.size()-1),thick*1.8,t*.2,3,FOAM,(float)(.9*e));
    }
    private static void splash(Canvas c,Vec3 at,double since,double rest,double size,double e){
        var grid=Fx4Atlas.get("hh_splash_sheet");double p=since/Math.max(4,rest*.9);if(p>=1)return;
        c.cellSprite(SPLASH,CanonFx4.Mode.ALPHA,at.add(0,size*.55,0),size,0,grid.frame(p),FOAM,(float)(.95*e));
    }
    /** DC1 companion: ripple rings on the ground under the impact (level 3+). */
    private static void ripple(Canvas c,Vec3 at,double since,double rest,double size,double e){
        Double y=CanonDecals.surfaceAt(at);if(y==null)return;var grid=Fx4Atlas.get("hh_ripple_sheet");
        for(int k=0;k<2;k++){double p=(since-k*3)/Math.max(6,rest);if(p<0||p>=1)continue;
            c.cellPlane(RIPPLE,CanonFx4.Mode.ADD,new Vec3(at.x,y+.03+k*.002,at.z),new Vec3(size*(1+k*.4),0,0),new Vec3(0,0,size*(1+k*.4)),grid.frame(p),FOAM,(float)(.7*e));}
    }
    /** SH4 (level 4+): the target is wrapped in a water globe that swells, wobbles and bursts back into droplets. */
    private static void globe(Canvas c,Vec3 at,double since,double rest,double e,double t){
        double p=clamp01(since/Math.max(6,rest));if(p>=.95)return;
        double grow=easeOutBack(clamp01(since/5)),wob=1+.06*Math.sin(t*.9),r=.85*grow*wob;
        c.shell(RIBBON,p>.7?CanonFx4.Mode.DISSOLVE:CanonFx4.Mode.FLOW,at.add(0,.15,0),r,r*1.15,8,12,tint(WATER,FOAM,.25),p>.7?(float)((p-.7)/.25):(float)(.95*e),t*.01);
    }

    private static void lash(Canvas c,View v,double e,double t){
        Vec3 a=v.origin(),b=v.endpoint();double L=Math.max(1,a.distanceTo(b)),arr=arrive(v.life()),since=v.age()-arr,rest=Math.max(1,v.life()-arr);
        // TM1: the water first gathers into an orb at the hand.
        double gather=clamp01(v.age()/Math.max(1,arr*.45));
        if(gather<1||since<1)c.shell(RIBBON,CanonFx4.Mode.FLOW,a,.12+.1*gather,.12+.1*gather,5,8,WATER,(float)(.9*e),t*.02);
        // Lash with overshoot, then the tail pulls back while the body falls apart into droplets.
        var full=CanonFx.wave(a,b,24,.16,2.5,-t*.25,new Vec3(0,1,0));
        double out=clamp01(easeOutBack(clamp01((v.age()-arr*.35)/Math.max(1,arr*.65))));
        double back=easeIn(clamp01((life01(v)-.55)/.45));
        var path=partial(full,out);if(back>0&&path.size()>2)path=path.subList((int)Math.min(path.size()-2,Math.floor(back*(path.size()-1))),path.size());
        water(c,path,.2*(1-.6*back),e,t,L,out<1);
        if(since>=0)splash(c,b,since,rest,1.3,e);
    }
    private static void jet(Canvas c,View v,double e,double t){
        Vec3 a=v.origin(),b=v.endpoint();double L=Math.max(1,a.distanceTo(b)),arr=arrive(v.life()),since=v.age()-arr,rest=Math.max(1,v.life()-arr);
        double reach=easeOut(clamp01(v.age()/Math.max(1,arr*.7))),fade=1-easeIn(clamp01((life01(v)-.6)/.4));
        var path=line(a,a.add(b.subtract(a).scale(reach)),12);
        c.tube(RIBBON,CanonFx4.Mode.FLOW,path,x->.07+.04*x,x->e*fade,FOAM,L*2,-t*.18,6);
        c.tube(RIBBON,CanonFx4.Mode.FLOW,path,x->.16+.1*x,x->.7*e*fade,WATER,L*1.2,-t*.12,8);
        Vec3 d=b.subtract(a).normalize();var grid=Fx4Atlas.get("hh_ripple_sheet");
        c.cellPlane(RIPPLE,CanonFx4.Mode.ADD,a.add(d.scale(.15)),rightOf(d).scale(.35),upOf(d,rightOf(d)).scale(.35),grid.frame(frac(t*.06)),FOAM,(float)(.7*e*fade));
        if(since>=0)splash(c,b,since,rest,1.1,e);
    }
    private static void fan(Canvas c,View v,double e,double t){
        // Centred on the caster's chest: the event origin is the water source, which may sit under the surface.
        Vec3 a=v.anchor().add(0,1.0,0),f=flat(forward(v)),r=rightOf(f);double sweep=easeOut(reveal(v,6)),fade=1-easeIn(clamp01((life01(v)-.65)/.35));
        var pts=new ArrayList<Vec3>();int n=18;
        for(int i=0;i<n;i++){double ang=(-1+2*i/(double)(n-1))*1.05*sweep,rad=2.4*(.6+.4*sweep);
            pts.add(a.add(0,-.35+.15*Math.sin(i*.7+t*.3),0).add(f.scale(Math.cos(ang)*rad)).add(r.scale(Math.sin(ang)*rad)));}
        c.tube(RIBBON,CanonFx4.Mode.FLOW,pts,x->.2*Math.sin(Math.PI*x)+.05,x->.95*e*fade,WATER,3,-t*.1,8);
        c.ribbon4(RIBBON,CanonFx4.Mode.FLOW,pts,x->.7*Math.sin(Math.PI*x),x->.55*e*fade,WATER,3,-t*.1);
        c.ribbon4(LINE,CanonFx4.Mode.ADD,pts,x->.06,x->.5*e*fade,FOAM,1,0);
    }
    private static void current(Canvas c,View v,double e,double t){
        Vec3 a=v.origin(),b=v.endpoint();double arr=arrive(v.life()),since=v.age()-arr;
        if(b.distanceToSqr(v.anchor())<2.5*2.5){selfCurrent(c,v,e,t);return;}
        Double ya=CanonDecals.surfaceAt(a),yb=CanonDecals.surfaceAt(b);
        Vec3 ga=ya==null?a.add(0,-1,0):new Vec3(a.x,ya+.15,a.z),gb=yb==null?b.add(0,-.6,0):new Vec3(b.x,yb+.15,b.z);
        double reach=easeOut(clamp01(v.age()/Math.max(1,arr)));
        var path=partial(CanonFx.wave(ga,gb,18,.22,3,-t*.3,new Vec3(0,1,0)),reach);
        water(c,path,.18,e,t,Math.max(1,ga.distanceTo(gb)),reach<1);
        if(since<0)return;
        // Water coils around the target as it is moved.
        for(int k=0;k<2;k++){var coil=new ArrayList<Vec3>();
            for(int i=0;i<16;i++){double s=i/15.,ang=s*Math.PI*3+t*.35+k*Math.PI;coil.add(b.add(Math.cos(ang)*.55,-.7+s*1.5,Math.sin(ang)*.55));}
            c.tube(RIBBON,CanonFx4.Mode.FLOW,partial(coil,easeOut(clamp01(since/5))),x->.09,x->.85*e,WATER,2,-t*.08,6);}
    }
    /** Currents that move the caster: water coils up the body and a wake streams out behind, opposite to the push. */
    private static void selfCurrent(Canvas c,View v,double e,double t){
        Vec3 body=v.anchor(),f=flat(Vec3.directionFromRotation(0,v.yaw()));double grow=easeOut(reveal(v,5)),fade=1-easeIn(clamp01((life01(v)-.6)/.4));
        for(int k=0;k<2;k++){var coil=new ArrayList<Vec3>();
            for(int i=0;i<18;i++){double s=i/17.,ang=s*Math.PI*3.2+t*.4+k*Math.PI;coil.add(body.add(Math.cos(ang)*.5,.05+s*1.7,Math.sin(ang)*.5));}
            c.tube(RIBBON,CanonFx4.Mode.FLOW,partial(coil,grow),x->.08+.04*Math.sin(Math.PI*x),x->.9*e*fade,WATER,2.5,-t*.1,6);}
        Double y=CanonDecals.surfaceAt(body);Vec3 g=new Vec3(body.x,(y==null?body.y:y)+.12,body.z);
        var wake=CanonFx.wave(g,g.subtract(f.scale(2.6*grow)),14,.18,2,-t*.4,new Vec3(0,1,0));
        c.tube(RIBBON,CanonFx4.Mode.FLOW,wake,x->.16*(1-x)+.03,x->.85*e*fade*(1-x*.6),WATER,2,-t*.15,6);
    }
    /** Douse self: water pours down over the caster in a spiral and splashes at the feet. */
    private static void pour(Canvas c,View v,double e,double t){
        Vec3 body=v.anchor();double fall=easeOut(reveal(v,6)),fade=1-easeIn(clamp01((life01(v)-.6)/.4));var spiral=new ArrayList<Vec3>();
        for(int i=0;i<22;i++){double s=i/21.,ang=s*Math.PI*4+t*.3;spiral.add(body.add(Math.cos(ang)*(.55-.15*s),2.6-2.5*s,Math.sin(ang)*(.55-.15*s)));}
        c.tube(RIBBON,CanonFx4.Mode.FLOW,partial(spiral,fall),x->.07+.06*x,x->.9*e*fade,WATER,3,-t*.12,6);
        if(fall>=1)splash(c,body,v.age()-6,Math.max(4,v.life()-6),.9,e);
    }
    private static void carry(Canvas c,View v,double e,double t){
        Vec3 a=v.origin(),b=v.endpoint();double arr=Math.max(arrive(v.life()),Math.min(10,v.life()*.6)),p=clamp01(v.age()/arr),since=v.age()-arr;
        double h=Math.min(1.6,.35*a.distanceTo(b));
        java.util.function.DoubleFunction<Vec3> at=s->a.add(b.subtract(a).scale(s)).add(0,4*h*s*(1-s),0);
        if(p<1){
            c.shell(RIBBON,CanonFx4.Mode.FLOW,at.apply(easeOut(p)),.22,.24,6,10,WATER,(float)(.95*e),t*.03);
            var trail=new ArrayList<Vec3>();for(int i=0;i<10;i++)trail.add(at.apply(Math.max(0,easeOut(p)-.25*(1-i/9.))));
            c.tube(RIBBON,CanonFx4.Mode.FLOW,trail,x->.03+.07*x,x->.7*e*x,WATER,1.5,-t*.1,5);
        }else splash(c,b,since,Math.max(1,v.life()-arr),.8,e);
    }
    private static void wall(Canvas c,View v,double e,double t,int tier){
        Vec3 f=flat(forward(v)),r=rightOf(f);boolean self=v.endpoint().distanceToSqr(v.anchor())<2.5*2.5;
        Vec3 base=v.anchor().add(f.scale(.95));double rise=easeOutBack(reveal(v,6)),p=life01(v),out=clamp01((p-.75)/.25);
        if(self&&v.skill().equals("hydro_rescue")){globe(c,v.anchor().add(0,.85,0),v.age(),v.life(),e,t);return;}
        double hgt=2.2*Math.max(0,rise),w=1.15;Vec3 mid=base.add(0,hgt*.5,0);
        CanonFx4.Mode mode=out>0?CanonFx4.Mode.DISSOLVE:CanonFx4.Mode.FLOW;float al=out>0?(float)out:(float)(.85*e);
        // Six columns on a shallow arc bowed away from the caster, each with its own ripple phase, so the wall has depth.
        int cols=6;for(int k=0;k<cols;k++){double s0=-1+2*k/(double)cols,s1=-1+2*(k+1)/(double)cols;
            Vec3 p0=mid.add(r.scale(s0*w)).add(f.scale(.35*(1-s0*s0)+.03*Math.sin(t*.3+k))),p1=mid.add(r.scale(s1*w)).add(f.scale(.35*(1-s1*s1)+.03*Math.sin(t*.3+k+1)));
            c.fx4Quad(CURTAIN,mode,p0.add(0,hgt*.5,0),p1.add(0,hgt*.5,0),p1.subtract(0,hgt*.5,0),p0.subtract(0,hgt*.5,0),
                    new float[]{(float)(k/(double)cols),(float)(-t*.08),(float)((k+1)/(double)cols),(float)(1-t*.08)},WATER[0],WATER[1],WATER[2],al);}
        // A thin bright crest along the top edge and foam where the curtain meets the ground.
        c.ribbon4(LINE,CanonFx4.Mode.ADD,line(base.add(0,hgt,0).subtract(r.scale(w)),base.add(0,hgt,0).add(r.scale(w)),6),x->.07,x->.6*e*(1-out),FOAM,1,0);
        var grid=Fx4Atlas.get("hh_ripple_sheet");
        c.cellPlane(RIPPLE,CanonFx4.Mode.ADD,base.add(0,.04,0),r.scale(1.3),f.scale(.6),grid.frame(frac(t*.05)),FOAM,(float)(.6*e));
        if(tier>=4&&!self)globe(c,v.endpoint(),v.age(),v.life(),e,t);
    }
    private static void field(Canvas c,View v,double e,double t,int tier){
        Vec3 p=v.endpoint().distanceToSqr(v.origin())<4?v.anchor():v.endpoint();double R=1.2+.25*tier;
        Double y=CanonDecals.surfaceAt(p);Vec3 g=y==null?p:new Vec3(p.x,y,p.z);
        var grid=Fx4Atlas.get("hh_ripple_sheet");
        for(int k=0;k<3;k++){double ph=frac(t*.02+k/3.);
            c.cellPlane(RIPPLE,CanonFx4.Mode.ADD,g.add(0,.03+k*.002,0),new Vec3(R*(.5+ph),0,0),new Vec3(0,0,R*(.5+ph)),grid.frame(ph),FOAM,(float)(.55*e*(1-ph)));}
        if(v.skill().equals("hydro_douse_self")){pour(c,v,e,t);return;}
        // A closed ring of flowing water marks the zone, swelling as it settles.
        double swell=easeOutBack(reveal(v,6));var loop=new ArrayList<Vec3>();
        for(int i=0;i<=32;i++){double ang=i/32.*Math.PI*2+t*.05;loop.add(g.add(Math.cos(ang)*R*swell,.12+.06*Math.sin(ang*3+t*.4),Math.sin(ang)*R*swell));}
        c.tube(RIBBON,CanonFx4.Mode.FLOW,loop,x->.13,x->.9*e,WATER,R*3,-t*.12,8);
        // Eddy: water arcs circling above the ground, the stronger skills with more and taller arcs.
        int arcs=2+(tier>=5?1:0);
        for(int k=0;k<arcs;k++){var arc=new ArrayList<Vec3>();double spin=t*(.08+.02*k)+k*2.1;
            for(int i=0;i<14;i++){double s=i/13.,ang=spin+s*2.2;arc.add(g.add(Math.cos(ang)*R*(.7+.12*k),.25+.35*k+.15*Math.sin(s*Math.PI),Math.sin(ang)*R*(.7+.12*k)));}
            c.tube(RIBBON,CanonFx4.Mode.FLOW,arc,x->.15*Math.sin(Math.PI*x)+.03,x->.9*e,WATER,2,-t*.1,7);}
        if(v.skill().equals("hydro_eddy")){ // a water spout rises in the middle of the eddy
            var spout=new ArrayList<Vec3>();for(int i=0;i<20;i++){double s=i/19.,ang=s*Math.PI*5+t*.5;spout.add(g.add(Math.cos(ang)*(.15+.45*s),s*2.4*swell,Math.sin(ang)*(.15+.45*s)));}
            c.tube(RIBBON,CanonFx4.Mode.FLOW,spout,x->.18*(1-x)+.05,x->.85*e,WATER,3,-t*.15,7);}
        if(tier>=5){
            for(int k=0;k<3;k++){double ph=frac(t*.015+k/3.),a=k*2.09+t*.01;
                c.refract(NOISE,false,g.add(Math.cos(a)*R*.6,.8+ph*1.2,Math.sin(a)*R*.6),.7+ph*.6,(float)(.8*e*(1-ph)),MIST);}
        }
    }
}

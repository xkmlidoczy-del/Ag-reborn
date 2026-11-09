package cn.academy.port.client.canon;

import static cn.academy.port.client.canon.CanonFxScripts.*;

import cn.academy.port.canon.CanonVfx;
import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.client.canon.CanonFx.View;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/**
 * Nitrogen Armor on FX4: a clear layered shell of compressed air pressed against the body. The shell is a real ellipsoid whose
 * silhouette is bright and centre clear (SH4); strikes visibly compress at the fist before they release (TM1); frost clings to
 * what was hit (DC2). Ladder: level 3 shows the compression, level 4 adds an expanding pressure shell and frost on the ground (GM4,
 * DC1), level 5 frosts the caster's screen edges and holds a longer impact freeze (CM6, CM1). Presentation only.
 */
final class CanonFxNitrogen {
    private static final float[] ICE={.72f,.92f,1f},PALE={.92f,.98f,1f},DEEP={.5f,.75f,1f};
    private static final String RIM="fx4:na_shell_rim",COMPRESS="fx4:na_compress_sheet",VAPOR="na_vapor_sheet",FROST="na_frost_atlas",LINE="fx4:common_speedline";
    enum Role { SHELL, STRIKE, MOVE }
    private static final Map<String,Role> ROLES=Map.ofEntries(
            Map.entry("nitrogen_burst",Role.STRIKE),Map.entry("nitrogen_punch",Role.STRIKE),Map.entry("nitrogen_uppercut",Role.STRIKE),
            Map.entry("nitrogen_sweep",Role.STRIKE),Map.entry("nitrogen_cone",Role.STRIKE),Map.entry("nitrogen_ground_wave",Role.STRIKE),
            Map.entry("nitrogen_impact",Role.STRIKE),Map.entry("nitrogen_shock_ring",Role.STRIKE),Map.entry("nitrogen_shove",Role.STRIKE),
            Map.entry("nitrogen_item_push",Role.STRIKE),Map.entry("nitrogen_ram",Role.MOVE),Map.entry("nitrogen_dash",Role.MOVE),
            Map.entry("nitrogen_brake",Role.MOVE),Map.entry("nitrogen_rocket",Role.MOVE));
    private CanonFxNitrogen() {}
    static Role role(String skill){return ROLES.getOrDefault(skill,Role.SHELL);}
    static double arrive(double life){return Math.min(life*.4,5);}

    static void onReceive(CanonVfx.Visual v,int tier,boolean local,double life){
        if(v.phase()==CanonVfx.Phase.END)return;
        Role role=role(v.skill());Vec3 b=v.endpoint();
        switch(role){
            case STRIKE -> CanonFxForms.later((int)Math.round(arrive(life)),()->{
                frost(b,8);vapor(b,4);
                if(tier>=4)CanonDecals.add("na_frost_decal",b,.9+.25*tier,ICE,.85f,140);
                if(local){CanonCamera.shake(.3+.15*tier,6+tier);CanonCamera.freeze(tier>=5?110:35+12*tier);if(tier>=5)CanonCamera.edge("frost",1,50);}
            });
            case MOVE -> {for(int w=0;w<4;w++){final int wave=w;CanonFxForms.later(wave*2,()->vapor(v.origin().add(0,-1,0),3));}
                if(local){CanonCamera.fovKick(.09,10);CanonCamera.shake(.2,5);}}
            case SHELL -> {vapor(v.origin(),4);if(local&&tier>=5)CanonCamera.edge("frost",.8,40);}
        }
    }
    private static void frost(Vec3 at,int n){
        for(int k=0;k<n;k++){double ang=rnd(k+(int)(at.z*9))*6.28,sp=.04+.06*rnd(k+2);
            CanonParticles.spawn(FROST,k,at,new Vec3(Math.cos(ang)*sp,.05+.07*rnd(k),Math.sin(ang)*sp),.025,.03,.05+.03*rnd(k+4),.2,40,PALE,.9f,
                    CanonFx4.Mode.ALPHA,CanonParticles.Land.STOP,false,0);}
    }
    /** Cold vapour that rises and thins out (PT5). */
    private static void vapor(Vec3 at,int n){
        for(int k=0;k<n;k++)CanonParticles.spawn(VAPOR,0,at.add((rnd(k)-.5)*.6,rnd(k+1)*.4,(rnd(k+2)-.5)*.6),new Vec3(0,.02+.02*rnd(k+3),0),-.001,.05,
                .3+.2*rnd(k+4),.05,30,PALE,.6f,CanonFx4.Mode.ADD,CanonParticles.Land.NONE,false,k);
    }

    static void draw(Canvas c,View v){
        double e=env(v);if(e<=0)return;double t=time(v);int tier=Math.max(1,Math.min(5,v.tier()));
        switch(role(v.skill())){
            case SHELL -> shell(c,v,e,t,tier);
            case STRIKE -> strike(c,v,e,t,tier);
            case MOVE -> move(c,v,e,t);
        }
    }
    private static void shell(Canvas c,View v,double e,double t,int tier){
        if(has(v,"plate","shield_line","bulwark")){pane(c,v,e,t,tier);return;}
        // Two shells of compressed air hug the body; the bigger defences (dome, ally guard) push the outer shell out.
        boolean ally="nitrogen_ally_guard".equals(v.skill());
        if (v.phase()==CanonVfx.Phase.END.ordinal() || ally && v.phase()!=CanonVfx.Phase.START.ordinal()) return;
        Vec3 body=(ally?v.endpoint():v.anchor()).add(0,.95,0);
        boolean wide=has(v,"dome");
        double grow=easeOut(reveal(v,6));
        double rx=(wide?2.2:.62)*grow,ry=(wide?1.6:1.05)*grow;
        c.shell(RIM,CanonFx4.Mode.ADD,body,rx,ry,9,14,ICE,(float)(.9*e),t*.01);
        c.shell(RIM,CanonFx4.Mode.ADD,body,rx*.86,ry*.9,8,12,PALE,(float)(.5*e),-t*.013);
        if(wide&&tier>=3)c.refract("fx4:common_noise_tile",false,body,rx*1.1,(float)(.5*e),ICE);
    }
    /** Frontal screens sit exactly where the server intercept box is: its distance ahead and its half width. */
    private static void pane(Canvas c,View v,double e,double t,int tier){
        double w=cn.academy.port.canon.CanonExpansionCombatA.barrierHalfWidth(v.skill()),d=cn.academy.port.canon.CanonExpansionCombatA.barrierDistance(v.skill());
        Vec3 f=flat(CanonBarrierGeometry.forward(v)),r=rightOf(f),centre=CanonBarrierGeometry.centre(v);double grow=easeOut(reveal(v,6));
        for(int k=-2;k<=2;k++){Vec3 x=centre.add(r.scale(w*k/2.0*grow));c.refract("fx4:common_noise_tile",false,x,w*.6,(float)(.55*e),ICE);}
        Vec3 a=centre.add(r.scale(-w*grow)).add(0,w*grow,0),b=centre.add(r.scale(w*grow)).add(0,w*grow,0),cc=centre.add(r.scale(w*grow)).add(0,-w*grow,0),dd=centre.add(r.scale(-w*grow)).add(0,-w*grow,0);
        c.ribbon4(LINE,CanonFx4.Mode.ADD,java.util.List.of(a,b,cc,dd,a),x->.05,x->.8*e,ICE,1,0);
    }
    private static void strike(Canvas c,View v,double e,double t,int tier){
        Vec3 a=v.origin(),b=v.endpoint();double arr=arrive(v.life()),since=v.age()-arr;Vec3 dir=b.subtract(a);dir=dir.lengthSqr()<1e-6?new Vec3(0,0,1):dir.normalize();
        Vec3 fist=v.anchor().add(0,1.15,0).add(flat(dir).scale(.45));
        if(since<0){
            // TM1: air is squeezed into the fist; level 3+ makes the squeeze visible as a shrinking shell with streaks pulled inward.
            double squeeze=clamp01(v.age()/Math.max(1,arr));
            c.shell(RIM,CanonFx4.Mode.ADD,fist,.5*(1-squeeze)+.12,.5*(1-squeeze)+.12,6,10,ICE,(float)(.9*e),t*.03);
            if(tier>=3)for(int k=0;k<6;k++){double a2=k*1.047+t*.1;Vec3 from=fist.add(Math.cos(a2)*.9*(1-squeeze),Math.sin(a2)*.9*(1-squeeze),0);
                c.ribbon4(LINE,CanonFx4.Mode.ADD,line(from,fist,2),x->.04,x->.6*e*x,PALE,1,0);}
            return;
        }
        // Release: the compressed air bursts at the target as an expanding shell (GM4 from level 4) and a pressure streak.
        double p=clamp01(since/Math.max(4,v.life()-arr));var grid=Fx4Atlas.get("na_compress_sheet");
        c.cellSprite(COMPRESS,CanonFx4.Mode.ADD,b,.9+.3*tier,0,grid.frame(p),ICE,(float)(.9*e));
        c.ribbon4(LINE,CanonFx4.Mode.ADD,line(fist,b,3),x->.12*(1-p),x->.7*e*(1-p),PALE,1,0);
        if(tier>=4){double R=.4+2.2*easeOut(p);c.shell(RIM,CanonFx4.Mode.ADD,b,R,R*.8,8,14,ICE,(float)(.7*e*(1-p)),0);
            c.refract("fx4:common_noise_tile",false,b,R,(float)(.6*e*(1-p)),ICE);}
    }
    private static void move(Canvas c,View v,double e,double t){
        // Jets of cold air from the back and feet, leaving a vapour trail along the path.
        Vec3 f=flat(Vec3.directionFromRotation(0,v.yaw())),body=v.anchor();
        for(int k=-1;k<=1;k+=2){Vec3 nozzle=body.add(0,.2+(k>0?.9:0),0).add(f.scale(-.25));
            c.ribbon4(LINE,CanonFx4.Mode.ADD,line(nozzle,nozzle.add(f.scale(-1.6)).add(0,-.2,0),4),x->.18*(1-x),x->.75*e*(1-x),ICE,1,-t*.2);}
        c.shell(RIM,CanonFx4.Mode.ADD,body.add(0,.95,0),.6,1.0,8,12,PALE,(float)(.5*e),t*.02);
    }
}

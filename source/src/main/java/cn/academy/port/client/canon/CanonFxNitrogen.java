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
        boolean ally="nitrogen_ally_guard".equals(v.skill());
        if (v.phase()==CanonVfx.Phase.END.ordinal() || ally && v.phase()!=CanonVfx.Phase.START.ordinal()) return;
        Vec3 body=(ally?v.endpoint():v.anchor()).add(0,.95,0);
        boolean wide=has(v,"dome");
        double grow=easeOut(reveal(v,6));
        double rx=(wide?2.2:.68)*grow,ry=(wide?1.65:1.08)*grow;
        c.shell(RIM,CanonFx4.Mode.ADD,body,rx,ry,9,14,ICE,(float)(.85*e),t*.01);
        Canon3dMeshKit.hexPressureDome(c,body,rx*1.02,ry*1.02,ICE,t,e*(wide?.72:.58),wide?3:2);
        if(wide&&tier>=3)c.refract("fx4:common_noise_tile",false,body,rx*1.1,(float)(.5*e),ICE);
    }
    /** Frontal screens sit exactly where the server intercept box is: its distance ahead and its half width. */
    private static void pane(Canvas c,View v,double e,double t,int tier){
        double w=cn.academy.port.canon.CanonExpansionCombatA.barrierHalfWidth(v.skill());
        Vec3 f=flat(CanonBarrierGeometry.forward(v)),r=rightOf(f),centre=CanonBarrierGeometry.centre(v);double grow=easeOut(reveal(v,6));
        AgModernVfx.obbForcefield(c,centre,CanonBarrierGeometry.forward(v),v.yaw(),v.skill(),ICE,t,e*.76);
        for(int k=-2;k<=2;k++){Vec3 x=centre.add(r.scale(w*k/2.0*grow));c.refract("fx4:common_noise_tile",false,x,w*.55,(float)(.52*e),ICE);}
        Vec3 a=centre.add(r.scale(-w*grow)).add(0,w*grow,0),b=centre.add(r.scale(w*grow)).add(0,w*grow,0),cc=centre.add(r.scale(w*grow)).add(0,-w*grow,0),dd=centre.add(r.scale(-w*grow)).add(0,-w*grow,0);
        c.darkSheathRibbon(java.util.List.of(a,b,cc,dd,a),x->.06,x->.82*e,ICE);
    }
    private static void strike(Canvas c,View v,double e,double t,int tier){
        Vec3 a=v.origin(),b=v.endpoint();double arr=arrive(v.life()),since=v.age()-arr;Vec3 dir=b.subtract(a);dir=dir.lengthSqr()<1e-6?new Vec3(0,0,1):dir.normalize();
        Vec3 fist=v.anchor().add(0,1.15,0).add(flat(dir).scale(.45));
        if(since<0){
            double squeeze=clamp01(v.age()/Math.max(1,arr));
            c.shell(RIM,CanonFx4.Mode.ADD,fist,.52*(1-squeeze)+.14,.52*(1-squeeze)+.14,6,10,ICE,(float)(.9*e),t*.03);
            if(tier>=3)for(int k=0;k<6;k++){double a2=k*1.047+t*.1;Vec3 from=fist.add(Math.cos(a2)*.9*(1-squeeze),Math.sin(a2)*.9*(1-squeeze),0);
                c.darkSheathRibbon(line(from,fist,2),x->.048,x->.68*e*x,PALE);}
            return;
        }
        double p=clamp01(since/Math.max(4,v.life()-arr));var grid=Fx4Atlas.get("na_compress_sheet");
        c.cellSprite(COMPRESS,CanonFx4.Mode.ADD,b,.9+.3*tier,0,grid.frame(p),ICE,(float)(.9*e));
        c.darkSheathRibbon(line(fist,b,4),x->.14*(1-p),x->.82*e*(1-p),ICE);
        if(has(v,"sweep","uppercut","cone")){
            Vec3 side=v.skill().equals("nitrogen_uppercut")?new Vec3(0,1,0):rightOf(dir);
            Canon3dMeshKit.slashArc3D(c,v.anchor().add(0,1.05,0),dir,side,Math.max(1.8,a.distanceTo(b)),1.85,p,.28+.04*tier,ICE,DEEP,e*(1-p*.5),false);
        }
        if(has(v,"ground_wave","shock_ring","impact")||tier>=4){
            double R=(v.radius()>0?Math.min(v.radius(),8.0):.6+2.4*easeOut(p));
            Canon3dMeshKit.shockwaveCrown3D(c,b,R*(.45+.55*easeOut(p)),ICE,since,e*(1-p*.65),28);
            for(int k=0;k<6;k++){
                double ang=k*(Math.PI/3)+.25;
                Vec3 spBase=b.add(Math.cos(ang)*R*.55*easeOut(p),0,Math.sin(ang)*R*.55*easeOut(p));
                Vec3 spTip=spBase.add(Math.cos(ang)*.35,(.55+.12*tier)*(1-p*.6),Math.sin(ang)*.35);
                Canon3dMeshKit.facetedPrism(c,spBase,spTip,.11,4,ang,PALE,ICE,e*(1-p),true);
            }
            c.refract("fx4:common_noise_tile",false,b,R*.8,(float)(.6*e*(1-p)),ICE);
        }
    }
    private static void move(Canvas c,View v,double e,double t){
        Vec3 f=flat(Vec3.directionFromRotation(0,v.yaw())),body=v.anchor();
        for(int k=-1;k<=1;k+=2){Vec3 nozzle=body.add(0,.2+(k>0?.9:0),0).add(f.scale(-.25));
            c.darkSheathRibbon(line(nozzle,nozzle.add(f.scale(-1.9)).add(0,-.2,0),5),x->.20*(1-x),x->.82*e*(1-x),ICE);}
        Canon3dMeshKit.hexPressureDome(c,body.add(0,.95,0),.65,1.05,PALE,t,e*.56,2);
    }
}

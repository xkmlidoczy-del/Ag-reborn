package cn.academy.port.client.canon;

import static cn.academy.port.client.canon.CanonFxScripts.*;

import cn.academy.port.VisualTierConfig;
import cn.academy.port.canon.CanonVfx;
import cn.academy.port.client.canon.CanonFx.Canvas;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Signature top-tier finales for each category's ultimate (its original LV5 skill). They play on top of the skill's normal
 * effect, once per cast: several server visuals of one cast (one per target) fold into one show that collects every target
 * point. Sizes follow the server's real reach. Camera work only reaches the caster's own view. Presentation only.
 */
final class CanonUltimates {
    /** Ultimate skill -> show length in ticks and its real radius when the packet carries none. */
    private record Spec(int life,double radius,boolean follow) {}
    private static final Map<String,Spec> SPECS=Map.of(
            "dark_construct",new Spec(90,8,true),
            "nitrogen_dome",new Spec(100,6,true),
            "aero_barrage",new Spec(60,12,false),
            "mental_out_crowd",new Spec(100,16,false),
            "hydro_domain",new Spec(100,9,false),
            "paper_array",new Spec(70,12,false),
            CanonFxLiquid.FIELD_SKILL,new Spec(1,0,false), // Authored from the area receipt.
            "float_field",new Spec(90,10,false));
    private static final class Show {
        final String skill;final int entity;final UUID owner;final Spec spec;final boolean local;final double start;final float yaw;
        final Vec3 origin;
        final CanonFxLiquid.InkField ink;
        Vec3 anchor;final List<Vec3> targets=new ArrayList<>();
        Show(String skill,int entity,UUID owner,Spec spec,boolean local,double start,float yaw,Vec3 anchor,Vec3 origin){
            this.skill=skill;this.entity=entity;this.owner=owner;this.spec=spec;this.local=local;this.start=start;this.yaw=yaw;this.anchor=anchor;
            this.origin=origin;
            this.ink=CanonFxLiquid.FIELD_SKILL.equals(skill)?CanonFxLiquid.fieldAt(origin,spec.radius):null;
        }
    }
    private static final List<Show> SHOWS=new ArrayList<>();
    private static final float[] PEARL={.97f,.97f,1f},HALO={.85f,.9f,1f},ICE={.72f,.92f,1f},FROST={.9f,.98f,1f},AIR={.62f,.96f,.86f},
            MIND={1f,.55f,.82f},MIND2={.75f,.6f,1f},WATER={.4f,.8f,1f},FOAM={.9f,.97f,1f},PAPER={1f,.94f,.8f},PAPER2={1f,.82f,.55f},
            FLOAT={.7f,1f,.78f},FLOAT2={.85f,1f,.95f};
    private CanonUltimates() {}

    static boolean ultimate(String skill){return SPECS.containsKey(skill);}
    static boolean any(){return !SHOWS.isEmpty();}
    static int count(){return SHOWS.size();}
    static void clear(){SHOWS.clear();}

    /** Folds a server visual of an ultimate into its cast's show, or opens a new show and starts its one-shot work. */
    static void receive(CanonVfx.Visual v,boolean local,double now){
        if(cn.academy.port.VisualTierConfig.tier()!=cn.academy.port.VisualTier.OFF&&cn.academy.port.macro.MacroSkillRules.ultimate(v.skill()))return;
        Spec spec=SPECS.get(v.skill());if(spec==null||CanonReceiptFx.replacesLegacy(v.skill()))return;
        boolean liquid=CanonFxLiquid.FIELD_SKILL.equals(v.skill()),hydro="hydro_domain".equals(v.skill());
        boolean buoyancy="float_field".equals(v.skill()), nitrogen="nitrogen_dome".equals(v.skill());
        if(v.phase()==CanonVfx.Phase.END){
            if(hydro||nitrogen)SHOWS.removeIf(s->s.owner.equals(v.owner())&&s.skill.equals(v.skill()));
            return;
        }
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        if(liquid||hydro||buoyancy||nitrogen){
            // Liquid reports its field on the first accepted HIT; the other HITs are target-only.
            // Hydro's START carries the live area. Its END cancels the remaining source flourish.
            if(v.radius()<=0||v.phase()!=(liquid?CanonVfx.Phase.HIT:CanonVfx.Phase.START))return;
            spec=new Spec(buoyancy?Math.min(v.duration(),18):v.duration(),v.radius(),nitrogen);
        }else for(Show s:SHOWS)if(s.owner.equals(v.owner())&&s.skill.equals(v.skill())&&now-s.start<25){addTarget(s,v);return;}
        var actor=mc.level.getEntity(v.entity());
        Vec3 anchor=v.skill().startsWith("liquid_")?v.origin():actor!=null?actor.position():v.origin();
        float yaw=actor instanceof net.minecraft.world.entity.LivingEntity l?l.yBodyRot:0;
        SHOWS.removeIf(s->s.owner.equals(v.owner())&&s.skill.equals(v.skill()));
        if(SHOWS.size()>=8)SHOWS.remove(0);
        Show s=new Show(v.skill(),v.entity(),v.owner(),spec,local,now,yaw,anchor,v.origin());addTarget(s,v);SHOWS.add(s);
        begin(s,radius(s,v));
    }
    private static double radius(Show s,CanonVfx.Visual v){return v.radius()>0?v.radius():s.spec.radius;}
    private static void addTarget(Show s,CanonVfx.Visual v){
        if(v.origin().distanceToSqr(v.endpoint())>1&&s.targets.size()<12)s.targets.add(v.endpoint());
    }

    /** One-shot work: camera on the caster's own view, decals and particle bursts for everyone. */
    private static void begin(Show s,double R){
        Vec3 g=s.anchor;
        switch(s.skill){
            case "dark_construct" -> {
                for(int k=0;k<70;k++){double a=rnd(k)*6.28,r=rnd(k+9)*R*1.2;
                    CanonParticles.spawn("dm_feather_atlas",k%8,g.add(Math.cos(a)*r,7+rnd(k+3)*5,Math.sin(a)*r),new Vec3(0,-.04,0),-.002,.02,.35+.25*rnd(k+5),.08,
                            70+(int)(rnd(k+7)*40),PEARL,.95f,CanonFx4.Mode.ALPHA,CanonParticles.Land.STOP,false,(int)(rnd(k+11)*30));}
                if(s.local){CanonCamera.tint(PEARL,.45,14);CanonCamera.shake(.6,12);CanonCamera.fovKick(.12,20);}
            }
            case "nitrogen_dome" -> {
                CanonDecals.add("na_frost_decal",g,R,FROST,.8f,s.spec.life+40);
                if(s.local){CanonCamera.edge("frost",1,60);CanonCamera.shake(.45,10);}
            }
            case "aero_barrage" -> {
                for(int k=0;k<40;k++){double a=k*6.28/40;CanonParticles.spawn("ah_dust_sheet",k%8,g.add(Math.cos(a)*1.5,.2,Math.sin(a)*1.5),
                        new Vec3(Math.cos(a)*.55,.05,Math.sin(a)*.55),0,.04,1.2,.02,30,AIR,.75f,CanonFx4.Mode.ALPHA,CanonParticles.Land.NONE,false,0);}
                if(s.local){CanonCamera.edge("speed",1,40);CanonCamera.shake(.7,14);CanonCamera.fovKick(.14,16);}
            }
            case "mental_out_crowd" -> {if(s.local){CanonCamera.tint(MIND,.3,30);CanonCamera.darken(.25,40);}}
            case "paper_array" -> {if(s.local){CanonCamera.tint(PAPER2,.25,16);CanonCamera.shake(.35,8);}}
            case "float_field" -> {
                if(s.local&&VisualTierConfig.tier().enhanced()) {
                    CanonCamera.fovKick(-.05,16);
                    CanonCamera.tint(FLOAT,.16,18);
                }
            }
            default -> { }
        }
    }

    static void draw(Canvas c,double now,int allowance){
        if(SHOWS.isEmpty())return;
        var mc=Minecraft.getInstance();if(mc.level==null){SHOWS.clear();return;}
        SHOWS.removeIf(s->CanonReceiptFx.replacesLegacy(s.skill)||now-s.start>s.spec.life||now<s.start-1);
        for(Show s:List.copyOf(SHOWS)){
            if(s.spec.follow){var e=mc.level.getEntity(s.entity);if(e!=null&&e.getUUID().equals(s.owner))s.anchor=e.position();}
            double age=now-s.start,p=age/s.spec.life,e=Math.min(1,age/6)*(p>.75?Math.max(0,(1-p)/.25):1);if(e<=.01)continue;
            double R=s.spec.radius;
            int before=c.left,scope=c.limit(allowance);
            try{switch(s.skill){
                case "dark_construct" -> darkConstruct(c,s,age,e,R);
                case "nitrogen_dome" -> nitrogenDome(c,s,age,e,R);
                case "aero_barrage" -> aeroBarrage(c,s,age,e,R);
                case "mental_out_crowd" -> mentalCrowd(c,s,age,e,R);
                case "hydro_domain" -> CanonFxHydro.domainSource(c,s.origin,age,e);
                case "paper_array" -> paperArray(c,s,age,e,R);
                case CanonFxLiquid.FIELD_SKILL -> CanonFxLiquid.drawField(c,s.ink,R,p,e);
                case "float_field" -> floatField(c,s,age,e,R);
                default -> { }
            }}finally{c.restore(scope);c.record("ultimate:"+s.skill,before);}
        }
    }

    private static Vec3 dir(double ang){return new Vec3(Math.cos(ang),0,Math.sin(ang));}
    private static Vec3 yawDir(float yaw){return flat(Vec3.directionFromRotation(0,yaw));}

    /** Six giant white wings, a shard halo at the attack radius, a pillar of light and a ground feather sigil. */
    private static void darkConstruct(Canvas c,Show s,double t,double e,double R){
        Vec3 g=s.anchor,f=yawDir(s.yaw),r=rightOf(f),back=f.scale(-1),root=g.add(0,1.5,0).add(back.scale(.35));
        double open=easeOutBack(clamp01(t/14)),flap=Math.sin(t*.12)*.08;
        for(int pair=0;pair<3;pair++)for(int side=-1;side<=1;side+=2){
            double lift=(pair-1)*.55+flap*(pair==1?1:-1),span=(4.6-pair*.8)*open;
            for(int k=0;k<9;k++){double u=k/8.0,ang=Math.toRadians(14+u*70),len=span*(.35+.65*Math.sin(Math.PI*.5*(1-u*.4)));
                Vec3 d=r.scale(side*Math.cos(ang*.6)).add(new Vec3(0,Math.sin(ang)*.9+lift,0)).add(back.scale(.25)).normalize();
                Vec3 perp=d.cross(f);perp=perp.lengthSqr()<1e-6?r:perp.normalize();
                c.cellPlane("fx4:dm_feather_atlas",CanonFx4.Mode.ALPHA,root.add(d.scale(len*.55)),d.scale(len*.5),perp.scale(.22+.04*k),k+pair*2,PEARL,(float)(.78*e));}
        }
        c.refract("fx4:dm_refraction_normal",true,root,3.2*open,(float)(.6*e),HALO);
        for(int k=0;k<20;k++){double a=k*Math.PI*2/20+t*.03;Vec3 at=g.add(dir(a).scale(R)).add(0,1.4+.3*Math.sin(t*.1+k),0);
            c.cellSprite("fx4:dm_shard_atlas",CanonFx4.Mode.ALPHA,at,.75,t*.05+k,k,PEARL,(float)(.9*e));}
        c.plane("glow",true,g.add(0,10,0),new Vec3(1.6,0,0).scale(1+.2*Math.sin(t*.2)),new Vec3(0,10,0),PEARL,(float)(.35*e));
        c.plane("glow",true,g.add(0,10,0),new Vec3(0,0,1.6).scale(1+.2*Math.sin(t*.2)),new Vec3(0,10,0),PEARL,(float)(.35*e));
        for(int k=0;k<12;k++){double a=k*Math.PI/6+.26;Vec3 d=dir(a);
            c.cellPlane("fx4:dm_feather_atlas",CanonFx4.Mode.ALPHA,g.add(0,.07,0).add(d.scale(R*.55*open)),d.scale(R*.45*open),rightOf(d).scale(.6),k,PEARL,(float)(.55*e));}
        c.ground("ring",true,g.add(0,.06,0),R*open,PEARL,(float)(.6*e));c.ground("ring2",true,g.add(0,.06,0),R*.5*open,HALO,(float)(.4*e));
    }

    /** A frost dome at the real radius, ice plates on its wall, vapour curtains rising at the rim and an opening shock ring. */
    private static void nitrogenDome(Canvas c,Show s,double age,double opacity,double radius){
        Vec3 centre=s.anchor;
        double settle=easeOut(clamp01(age/8));
        c.shell("fx4:na_shell_rim",CanonFx4.Mode.ALPHA,centre,radius*settle,radius*settle,
                8,16,ICE,(float)(.61*opacity),age*.006);
        var vapor=Fx4Atlas.get("na_vapor_sheet");
        for(int i=0;i<12;i++){
            double angle=i*Math.PI/6+age*.012;
            Vec3 radial=dir(angle);
            Vec3 at=centre.add(radial.scale(radius*.91*settle)).add(0,.28+.11*Math.sin(i+age*.11),0);
            c.cellPlane("fx4:na_vapor_sheet",CanonFx4.Mode.ALPHA,at,rightOf(radial).scale(.85),
                    new Vec3(0,.55,0),vapor.frame(frac(age*.019+i*.13)),FROST,(float)(.55*opacity));
        }
    }

    /** A towering cyclone behind the caster and pressure cannon rings marching forward to the barrage's reach. */
    private static void aeroBarrage(Canvas c,Show s,double t,double e,double R){
        Vec3 g=s.anchor,f=yawDir(s.yaw),base=g.add(f.scale(-2.5));double rise=easeOut(clamp01(t/10));
        for(int k=0;k<6;k++){var pts=new ArrayList<Vec3>();
            for(int i=0;i<22;i++){double u=i/21.0,a=t*.35+k*1.047+u*9,rad=.6+3.6*u;pts.add(base.add(Math.cos(a)*rad,u*14*rise,Math.sin(a)*rad));}
            c.ribbon4("fx4:ah_stream",CanonFx4.Mode.ADD,pts,x->.6+1.1*x,x->e*(1-x*.3),AIR,3,-t*.08);
            c.ribbon4("fx4:common_speedline",CanonFx4.Mode.ADD,pts,x->.15+.3*x,x->.9*e,FOAM,2,-t*.15);}
        var dustCol=Fx4Atlas.get("ah_dust_sheet");for(int k=0;k<10;k++){double u=k/9.0,a=t*.3+k*.9;Vec3 at=base.add(Math.cos(a)*(.8+2.6*u),u*12*rise,Math.sin(a)*(.8+2.6*u));
            c.cellSprite("fx4:ah_dust_sheet",CanonFx4.Mode.ALPHA,at,1.4+2*u,a,dustCol.frame(frac(t*.03+u)),AIR,(float)(.55*e));}
        c.refract("fx4:common_noise_tile",false,base.add(0,5,0),5*rise,(float)(.5*e),AIR);
        var shock=Fx4Atlas.get("common_shock_ring_sheet");Vec3 r=rightOf(f),up=new Vec3(0,1,0);
        for(int k=0;k<4;k++){double ph=clamp01((t-k*5)/14);if(ph<=0||ph>=1)continue;Vec3 at=g.add(0,1.4,0).add(f.scale(2+k*R/4));
            double size=1+2.2*ph+k*.4;c.cellPlane("fx4:common_shock_ring_sheet",CanonFx4.Mode.ADD,at,r.scale(size),up.scale(size),shock.frame(ph),AIR,(float)(.95*(1-ph)*e));
            c.cellPlane("fx4:ah_cone",CanonFx4.Mode.ADD,at.subtract(f.scale(1)),f.scale(1.6),r.scale(size*.6),0,FOAM,(float)(.5*(1-ph)*e));}
        var dust=Fx4Atlas.get("ah_dust_sheet");
        for(int k=0;k<14;k++){double a=k*Math.PI/7;Vec3 d=dir(a);c.cellPlane("fx4:ah_dust_sheet",CanonFx4.Mode.ALPHA,g.add(d.scale(R*rise)).add(0,.9,0),rightOf(d).scale(1.6),up.scale(.9),dust.frame(frac(t*.03+k*.2)),AIR,(float)(.45*e));}
        c.ground("ring",true,g.add(0,.06,0),R*rise,AIR,(float)(.5*e));
    }

    /** A giant holographic eye overhead, threads from the eye to every controlled target, and signal rings across the crowd. */
    private static void mentalCrowd(Canvas c,Show s,double t,double e,double R){
        // Hovers over the controlled crowd (or 6 blocks ahead), where a forward-looking camera actually sees it.
        Vec3 g=s.anchor,centre=g.add(yawDir(s.yaw).scale(6));
        if(!s.targets.isEmpty()){Vec3 sum=Vec3.ZERO;for(Vec3 p:s.targets)sum=sum.add(p);centre=sum.scale(1.0/s.targets.size());}
        Vec3 out=centre.subtract(g).multiply(1,0,1);if(out.length()<5)centre=g.add((out.lengthSqr()<1e-4?yawDir(s.yaw):out.normalize()).scale(6));
        Vec3 eye=centre.add(0,2.8,0);double open=easeOutBack(clamp01(t/14)),blink=Math.abs(Math.sin(t*.05))>.97?.2:1;
        var pts=new ArrayList<Vec3>();var low=new ArrayList<Vec3>();
        // The eye always faces whoever is watching, so it reads as an eye from any angle.
        for(int i=0;i<=24;i++){double u=i/24.0*Math.PI;double x=Math.cos(u)*5.5*open,y=Math.sin(u)*2.3*open*blink;
            pts.add(eye.add(c.right.scale(x)).add(c.up.scale(y)));low.add(eye.add(c.right.scale(x)).add(c.up.scale(-y)));}
        c.ribbon4("fx4:mo_thread",CanonFx4.Mode.ADD,pts,x->.35,x->e,MIND,4,t*.05);c.ribbon4("fx4:mo_thread",CanonFx4.Mode.ADD,low,x->.35,x->e,MIND,4,t*.05);
        c.ribbon4("fx4:common_speedline",CanonFx4.Mode.ADD,pts,x->.22,x->.85*e,MIND2,1,0);c.ribbon4("fx4:common_speedline",CanonFx4.Mode.ADD,low,x->.22,x->.85*e,MIND2,1,0);
        c.cellSprite("fx4:mo_signal_ring_sheet",CanonFx4.Mode.ADD,eye,4.4*open*blink,t*.02,Fx4Atlas.get("mo_signal_ring_sheet").frame(frac(t*.03)),MIND2,(float)(.9*e));
        c.sprite("glow",true,eye,2.4*open*blink,0,MIND,(float)e);c.refract("fx4:dm_refraction_normal",true,eye,3*open,(float)(.5*e),MIND2);
        for(int k=0;k<8;k++){double a=k*Math.PI/4+t*.015;c.cellPlane("fx4:mo_holo_panel",CanonFx4.Mode.HOLO,eye.add(dir(a).scale(6.2)),dir(a+Math.PI/2).scale(1.1),new Vec3(0,.8,0),k,MIND2,(float)(.6*e));}
        var targets=s.targets.isEmpty()?List.of(g):s.targets;
        for(int k=0;k<targets.size();k++){Vec3 to=targets.get(k).add(0,1.6,0);Vec3 mid=eye.add(to).scale(.5).add(0,1.5,0);
            var arc=new ArrayList<Vec3>();for(int i=0;i<=10;i++){double u=i/10.0;arc.add(eye.scale((1-u)*(1-u)).add(mid.scale(2*u*(1-u))).add(to.scale(u*u)));}
            double in=.9*e*clamp01((t-4-k)/6);c.ribbon4("fx4:mo_thread",CanonFx4.Mode.ADD,arc,x->.14,x->in,MIND,3,-t*.1);
            c.cellSprite("fx4:mo_star_blink_sheet",CanonFx4.Mode.ADD,to.add(0,.8,0),.5,0,Fx4Atlas.get("mo_star_blink_sheet").frame(frac(t*.06+k*.3)),MIND,(float)e);}
        var ring=Fx4Atlas.get("mo_signal_ring_sheet");
        for(int k=0;k<3;k++){double ph=frac(t/40.0+k/3.0);c.cellPlane("fx4:mo_signal_ring_sheet",CanonFx4.Mode.ADD,g.add(0,.1,0),new Vec3(R*ph,0,0),new Vec3(0,0,R*ph),ring.frame(ph),MIND,(float)(.7*(1-ph)*e));}
    }

    /** Three counter-rotating rings of paper over the caster, a crane spiral rising, and blades dropping onto every target. */
    private static void paperArray(Canvas c,Show s,double t,double e,double R){
        Vec3 g=s.anchor;double open=easeOutBack(clamp01(t/14));
        for(int ring=0;ring<3;ring++){int n=12+ring*6;double rad=(2.5+ring*3)*open,h=3.2+ring*.8,dirn=ring%2==0?1:-1;
            for(int k=0;k<n;k++){double a=k*Math.PI*2/n+t*.03*dirn;Vec3 d=dir(a),at=g.add(d.scale(rad)).add(0,h+.15*Math.sin(t*.1+k),0);
                c.cellPlane("fx4:pp_sheet_atlas",CanonFx4.Mode.ALPHA,at,rightOf(d).scale(.35),new Vec3(0,.45,0).add(d.scale(.15)),k,ring==1?PAPER2:PAPER,(float)(.95*e));}}
        c.ground("ring",true,g.add(0,3.4,0),2.6*open,PAPER2,(float)(.4*e));
        for(int k=0;k<16;k++){double u=frac(t/50.0+k/16.0),a=u*12+k;Vec3 at=g.add(Math.cos(a)*(1+u*2),u*10,Math.sin(a)*(1+u*2));
            c.cellSprite("fx4:pp_sheet_atlas",CanonFx4.Mode.ALPHA,at,.35,a,k+3,PAPER,(float)(.9*e*(1-u)));}
        for(int k=0;k<s.targets.size();k++){Vec3 to=s.targets.get(k);double ph=clamp01((t-6-k*2)/10);if(ph<=0)continue;
            Vec3 from=g.add(0,4.5,0),at=from.add(to.add(0,1,0).subtract(from).scale(easeIn(ph)));
            c.cellStrip("fx4:common_speedline",CanonFx4.Mode.ADD,at.subtract(to.subtract(from).normalize().scale(1.2)),at,.12,0,PAPER2,(float)(.9*e*(1-ph*.5)));
            if(ph>=1)c.ground("ring",true,to.add(0,.06,0),1.2,PAPER2,(float)(.5*e));}
        c.ground("ring2",true,g.add(0,.06,0),R*open,PAPER,(float)(.45*e));
    }

    /** Short release cues start at the real cast origin, separate from the active area. */
    private static void floatField(Canvas c,Show s,double t,double e,double radius){
        if (!VisualTierConfig.tier().enhanced()) return;
        double rise = easeOut(clamp01(t / 15));
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5 + t * .016;
            double distance = radius * (.10 + .28 * rise);
            Vec3 at = s.origin.add(dir(a).scale(distance)).add(0, .24 + rise * (.6 + rnd(i) * 1.3), 0);
            c.cellSprite("fx4:fd_bubble_atlas", CanonFx4.Mode.ALPHA, at, .13 + .08 * rnd(i + 3),
                    0, i, FLOAT2, (float)(.67 * e));
        }
    }
}

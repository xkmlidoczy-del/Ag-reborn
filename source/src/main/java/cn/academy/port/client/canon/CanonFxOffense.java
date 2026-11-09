package cn.academy.port.client.canon;

import static cn.academy.port.client.canon.CanonFxScripts.*;
import cn.academy.port.canon.CanonVfx;
import cn.academy.port.canon.CanonOffenseSkills;
import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.client.canon.CanonFx.View;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Five material languages, true server contact points and one common bounded drawing budget. */
final class CanonFxOffense {
    private static final float[] GOLD={1,.79f,.27f},IVORY={1,.96f,.76f},OCHRE={.25f,.16f,.04f},
            DENSITY={.57f,.86f,.91f},DARK_DENSITY={.12f,.27f,.34f},PAPER={1,.98f,.91f},
            INK={.55f,.65f,.78f},WET={.86f,.95f,1},AIR={.75f,.96f,.94f},DARK_AIR={.25f,.39f,.41f};
    private static final CanonFx4.Mode ALPHA=CanonFx4.Mode.ALPHA,HOLO=CanonFx4.Mode.HOLO;
    private CanonFxOffense() {}
    static void onReceive(CanonVfx.Visual v,int tier,boolean local){
        if(local&&v.phase()==CanonVfx.Phase.HIT){
            CanonCamera.shake(.018+tier*.009,3+tier/2);
            if(tier>=4)CanonCamera.fovKick(.010+tier*.002,5);
        }
    }
    static void draw(Canvas c,View v){
        double e=env(v);if(e<.003)return;
        boolean start=v.phase()==CanonVfx.Phase.START.ordinal();
        if(v.phase()==CanonVfx.Phase.END.ordinal())return;
        var spec=CanonOffenseSkills.find(v.skill());if(spec==null)return;
        switch(v.category()){
            case "mental_out"->mind(c,v,spec,e,start);
            case "float_dial"->density(c,v,spec,e,start);
            case "paper_psychokinesis"->paper(c,v,spec,e,start);
            case "liquid_shadow"->ink(c,v,spec,e,start);
            case "aero_hand"->air(c,v,e,start);
            default->{}
        }
    }
    private static void sprite(Canvas c,String key,Vec3 p,double size,double spin,float[] color,double alpha){c.cellSprite(key,ALPHA,p,size,spin,0,color,(float)alpha);}
    private static void plane(Canvas c,String key,Vec3 p,Vec3 x,Vec3 y,float[] color,double alpha){c.cellPlane(key,ALPHA,p,x,y,0,color,(float)alpha);}
    private static void impact(Canvas c,String key,Vec3 p,double size,View v,float[] color,double e){
        int f=Fx4Atlas.get(key).frame(clamp01(v.age()/Math.max(1,v.life()*.82)));
        c.cellSprite(key,ALPHA,p,size,0,f,color,(float)e);
    }
    private static void strand(Canvas c,String key,List<Vec3> points,double width,float[] color,double e){
        c.ribbon4(key,ALPHA,points,t->width,t->e*(.65+.35*Math.sin(Math.PI*t)),color,1,0);
    }
    private static List<Vec3> curved(Vec3 from,Vec3 to,Vec3 normal,double bend,int samples){
        var out=new ArrayList<Vec3>();for(int i=0;i<samples;i++){double t=i/(double)(samples-1);out.add(from.lerp(to,t).add(normal.scale(Math.sin(Math.PI*t)*bend)));}return out;
    }
    private static void mind(Canvas c,View v,CanonOffenseSkills.Spec s,double e,boolean start){
        Vec3 target=v.radius()>0?v.endpoint().add(0,.65,0):v.endpoint(),axis=forward(v),right=rightOf(axis),up=upOf(axis,right);
        double p=clamp01(v.age()/Math.max(1,v.life())),charge=start?easeOut(p):1;
        int threads=s.level()>=4?5:s.level()>=2?3:1;
        for(int k=0;k<threads;k++){
            double a=k*Math.PI*2/threads+v.age()*.035,offset=.08+s.level()*.025;
            Vec3 normal=right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            var path=partial(curved(v.origin(),target,normal,offset,20),charge);
            strand(c,"of_mind_thread",path,.16,OCHRE,e*.88);
            strand(c,"of_mind_thread",path,.065,IVORY,e*.95);
        }
        double r=(start?.48+.24*charge:.72+.012*v.age())*(1+s.level()*.07);
        sprite(c,"of_mind_neural",target,r*1.035,0,OCHRE,e*.9);
        sprite(c,"of_mind_neural",target,r,Math.sin(v.age()*.08)*.08,GOLD,e*.95);
        c.cellSprite("of_mind_neural",HOLO,target.add(axis.scale(-.008)),r*.985,0,0,IVORY,(float)(e*.42));
        if(s.id().equals("mental_feedback_spike"))for(int k=0;k<3;k++){
            double a=k*Math.PI*2/3;Vec3 p0=target.add(right.scale(Math.cos(a)*.6)).add(up.scale(Math.sin(a)*.6));
            sprite(c,"of_mind_neural",p0,.18,-a+v.age()*.04,GOLD,e*.8);
        }
        if(!start){
            impact(c,"of_mind_impact",target,1.2+s.level()*.18,v,IVORY,e*.92);
            if(s.level()>=4&&v.radius()>0){
                double radius=v.radius(),a=v.age()*.022;var nodes=new ArrayList<Vec3>();
                for(int k=0;k<6;k++){double theta=a+k*Math.PI/3;Vec3 n=target.add(Math.cos(theta)*radius*.65,.45,Math.sin(theta)*radius*.65);nodes.add(n);sprite(c,"of_mind_neural",n,.23,theta,GOLD,e*.65);}
                for(int k=0;k<6;k++)strand(c,"of_mind_thread",curved(nodes.get(k),nodes.get((k+1)%6),new Vec3(0,1,0),.25,10),.07,GOLD,e*.65);
            }
        }
    }
    private static void density(Canvas c,View v,CanonOffenseSkills.Spec s,double e,boolean start){
        Vec3 centre=v.endpoint();double t=v.age(),p=clamp01(t/Math.max(1,v.life()));
        double radius=v.radius()>0?v.radius():.85+s.level()*.12;
        double extent=start?.55+.45*easeOut(p):1+.18*Math.sin(Math.PI*p);
        int bands=s.level()>=4?5:3;
        for(int k=0;k<bands;k++){
            double h=(k-(bands-1)*.5)*.38,rr=radius*extent*(.72+.15*Math.sin(k*.9+t*.06));
            Vec3 at=centre.add(0,h,0);
            plane(c,"of_float_shell",at,new Vec3(rr,0,0),new Vec3(0,0,rr),DARK_DENSITY,e*.63);
            plane(c,"of_float_shell",at.add(0,.012,0),new Vec3(rr*.985,0,0),new Vec3(0,0,rr*.985),DENSITY,e*.9);
        }
        int spokes=s.level()>=4?8:4;
        for(int k=0;k<spokes;k++){
            double a=k*Math.PI*2/spokes+t*.012;Vec3 pos=centre.add(Math.cos(a)*radius*.66,0,Math.sin(a)*radius*.66);
            Vec3 right=new Vec3(-Math.sin(a),0,Math.cos(a));
            plane(c,"of_float_column",pos,right.scale(.22),new Vec3(0,(.7+s.level()*.1)*extent,0),DENSITY,e*.7);
        }
        if(!start){
            double vertical=s.id().contains("drop")||s.id().contains("collapse")?-1:1;
            Vec3 from=centre.add(0,-vertical*(.7+p*.4),0),to=centre.add(0,vertical*(1-p)*1.6,0);
            strand(c,"of_air_streak",line(from,to,8),.16,DENSITY,e*.9);
            impact(c,"of_float_crush",centre,1.25+s.level()*.2,v,DENSITY,e*.9);
        }
    }
    private static void folded(Canvas c,Vec3 point,Vec3 axis,double length,double width,double spin,double e){
        Vec3 x=rightOf(axis),y=upOf(axis,x);Vec3 rotated=x.scale(Math.cos(spin)).add(y.scale(Math.sin(spin)));
        Vec3 bend=axis.cross(rotated).normalize();
        // Two different physical planes catch the fold; the albedo carries fine paper fibres and shaded bevels.
        plane(c,"of_paper_blade",point,axis.scale(length*1.035),rotated.scale(width*1.10),new float[]{.30f,.27f,.20f},e*.85);
        plane(c,"of_paper_blade",point.add(c.cam.subtract(point).normalize().scale(.006)),axis.scale(length),rotated.scale(width),PAPER,e);
        plane(c,"of_paper_blade",point.add(bend.scale(.025)),axis.scale(length*.98),rotated.add(bend.scale(.24)).scale(width*.64),new float[]{.78f,.78f,.72f},e*.95);
    }
    private static void paper(Canvas c,View v,CanonOffenseSkills.Spec s,double e,boolean start){
        Vec3 axis=forward(v),right=rightOf(axis);double p=clamp01(v.age()/Math.max(1,v.life())),t=v.age();
        if(s.id().equals("paper_returning_wheel")){
            Vec3 at=start?v.origin().add(axis.scale(.35)):p<.22?v.origin().lerp(v.endpoint(),easeOut(p/.22))
                    :p<.65?v.endpoint():v.endpoint().lerp(v.origin(),easeOut((p-.65)/.35));double r=start?.42+.18*easeOut(p):.85;
            Vec3 up=upOf(axis,right);double a=t*(start?.12:.38);
            Vec3 rx=right.scale(Math.cos(a)).add(up.scale(Math.sin(a))).scale(r);
            Vec3 uy=right.scale(-Math.sin(a)).add(up.scale(Math.cos(a))).scale(r);
            plane(c,"of_paper_wheel",at,rx,uy,PAPER,e);
            plane(c,"of_paper_wheel",at.add(axis.scale(.035)),rx.scale(.96),uy.scale(.96),new float[]{.65f,.67f,.62f},e);
            if(!start)for(int k=0;k<6;k++){double q=k*Math.PI/3+a;Vec3 n=at.add(right.scale(Math.cos(q)*1.1)).add(up.scale(Math.sin(q)*1.1));folded(c,n,axis,.26,.12,q,e*(1-p));}
        }else{
            int count=s.id().equals("paper_fold_fan")?3:1;
            for(int k=0;k<count;k++){
                double spread=(k-(count-1)*.5)*.22;
                Vec3 end=v.endpoint().add(right.scale(spread)),at=start?v.origin().add(right.scale(spread*.7)).add(axis.scale(.25)):v.origin().lerp(end,easeOut(t/3));
                folded(c,at,axis,s.level()==3?1.15:.62,s.level()==3?.18:.30,spread*3+t*.02,e);
                if(!start)strand(c,"of_air_streak",curved(v.origin(),end,right,spread*.8,14),.10,PAPER,e*.6);
            }
        }
        if(!start)for(int k=0;k<6+s.level()*2;k++){
            double a=k*2.39996,spread=(.25+p*1.3)*(1+k%3*.2);Vec3 point=v.endpoint().add(Math.cos(a)*spread,Math.sin(a)*spread-p*.3,Math.sin(a*.7)*spread*.4);
            plane(c,"of_paper_fiber",point,c.right.scale(.06*(1-p)),c.up.scale(.10*(1-p)),PAPER,e*.8);
        }
    }
    private static void ink(Canvas c,View v,CanonOffenseSkills.Spec s,double e,boolean start){
        Vec3 axis=forward(v),right=rightOf(axis),up=upOf(axis,right);double t=v.age(),p=clamp01(t/Math.max(1,v.life()));
        int blades=s.id().equals("liquid_crosscut")?2:1;
        for(int k=0;k<blades;k++){
            double sign=k==0?1:-1;Vec3 side=right.scale(sign*.25).add(up.scale(sign*.2));
            var path=curved(v.origin(),v.endpoint(),side,1,18);
            var visible=start?partial(path,easeOut(p)):path;
            strand(c,"of_ink_filament",visible,.38*(start?.5+.5*p:1-p*.5),INK,e*.98);
            strand(c,"of_mind_thread",visible,.032,WET,e*.5);
            Vec3 at=start?v.origin().lerp(v.endpoint(),easeOut(p)*.75):v.endpoint();
            plane(c,"of_ink_blade",at,axis.scale(start?.55:.92),side.normalize().scale(.36),WET,e);
            plane(c,"of_ink_blade",at.add(up.scale(.035)),axis.scale(start?.38:.70),side.normalize().scale(.19),INK,e*.9);
        }
        if(!start){
            impact(c,"of_ink_ripple",v.endpoint(),1.25,v,WET,e*.7);
            for(int k=0;k<7;k++){double a=k*2.39996;Vec3 at=v.endpoint().add(right.scale(Math.cos(a)*p*.9)).add(up.scale(Math.sin(a)*p*.6-p*.3));
                sprite(c,"of_ink_blade",at,.08*(1-p),a,INK,e*.8);}
        }
    }
    private static void air(Canvas c,View v,double e,boolean start){
        Vec3 axis=forward(v),right=rightOf(axis),up=upOf(axis,right);double t=v.age(),p=clamp01(t/Math.max(1,v.life()));
        Vec3 centre=start?v.origin().add(axis.scale(.35)):v.endpoint();
        for(int k=0;k<3;k++){
            double a=t*(start?.07:.035)+k*Math.PI*2/3;Vec3 rx=right.scale(Math.cos(a)).add(up.scale(Math.sin(a))),uy=right.scale(-Math.sin(a)).add(up.scale(Math.cos(a)));
            double r=start?.25+.18*easeOut(p):.50+p*.55;Vec3 at=centre.add(axis.scale(k*.08));
            plane(c,"of_air_crescent",at,rx.scale(r*1.035),uy.scale(r*1.035),DARK_AIR,e*.7);
            plane(c,"of_air_crescent",at.add(axis.scale(-.01)),rx.scale(r),uy.scale(r),AIR,e*.95);
        }
        if(!start){strand(c,"of_air_streak",line(v.origin(),v.endpoint(),18),.18,AIR,e*.75);impact(c,"of_air_impact",centre,1.25,v,AIR,e*.9);}
    }
}

package cn.academy.port.client.canon;

import static cn.academy.port.client.canon.CanonFxScripts.env;
import static cn.academy.port.client.canon.CanonFxScripts.faceRing;
import static cn.academy.port.client.canon.CanonFxScripts.flat;
import static cn.academy.port.client.canon.CanonFxScripts.forward;
import static cn.academy.port.client.canon.CanonFxScripts.frac;
import static cn.academy.port.client.canon.CanonFxScripts.has;
import static cn.academy.port.client.canon.CanonFxScripts.line;
import static cn.academy.port.client.canon.CanonFxScripts.rightOf;
import static cn.academy.port.client.canon.CanonFxScripts.rnd;
import static cn.academy.port.client.canon.CanonFxScripts.tint;
import static cn.academy.port.client.canon.CanonFxScripts.upOf;

import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.client.canon.CanonFx.View;
import cn.academy.port.canon.CanonVfxDesign.Motif;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Two cross-cutting layers on top of each system's base look:
 * <ul>
 * <li><b>Tier</b> (skill level 1..5), graded against the original four systems: level 1-2 is fine line-work and small light points;
 * level 3 adds a ground sigil; level 4 a light pillar; level 5 the full spectacle (tall pillar, halo, repeating shock rings).</li>
 * <li><b>Verb</b>: what the skill does (rise, fall, push, pull, sweep, pierce, scan, guard, step, build, link, ring) draws its own glyph, and a
 * per-skill seed varies hue, count, spin and size so skills of one system and one level still read differently.</li>
 * </ul>
 */
final class CanonFxTier {
    private enum Verb { RISE, FALL, PUSH, PULL, SWEEP, PIERCE, RING, SCAN, GUARD, STEP, BUILD, LINK, NONE }
    private record Pal(float[] main,float[] accent,float[] core) {}
    private CanonFxTier() {}

    static void draw(Canvas c,View v){
        int tier=Math.max(1,Math.min(5,v.tier()));long seed=hash(v.skill());Pal p=palette(v.category(),seed);
        double e=env(v);if(e<=0)return;double t=CanonFxScripts.time(v);
        Vec3 base=baseOf(v);Verb verb=verbOf(v.skill());
        verbGlyph(c,v,p,verb,base,tier,seed,e,t);
        if(tier>=3)sigil(c,v,p,base,tier,seed,e,t);
        if(tier>=4)pillar(c,v,p,base,tier,seed,e,t);
        if(tier>=5)spectacle(c,v,p,base,seed,e,t);
    }

    // ------------------------------------------------------------------ identity
    private static long hash(String s){long h=1125899906842597L;for(int i=0;i<s.length();i++)h=31*h+s.charAt(i);return h;}
    private static double r(long seed,int k){double x=Math.sin((seed%100003)*.001+k*12.9898)*43758.5453;return x-Math.floor(x);}
    /** Each system keeps its colour family; the seed moves a skill along it so siblings are not identical. */
    private static Pal palette(String cat,long seed){
        float[] a,b,core;
        switch(cat){
            case "mental_out" -> {a=new float[]{1f,.78f,.24f};b=new float[]{1f,.55f,.78f};core=new float[]{1f,.97f,.82f};}
            case "dark_matter" -> {a=new float[]{.95f,.97f,1f};b=new float[]{.7f,.85f,1f};core=new float[]{1f,1f,1f};}
            case "nitrogen_armor" -> {a=new float[]{.72f,.93f,1f};b=new float[]{.5f,.78f,1f};core=new float[]{.95f,1f,1f};}
            case "aero_hand" -> {a=new float[]{.74f,1f,.92f};b=new float[]{.45f,.95f,.8f};core=new float[]{1f,1f,.98f};}
            case "hydro_hand" -> {a=new float[]{.28f,.66f,1f};b=new float[]{.2f,.9f,.95f};core=new float[]{.92f,.98f,1f};}
            case "float_dial" -> {a=new float[]{.55f,.97f,1f};b=new float[]{.85f,.8f,1f};core=new float[]{1f,1f,1f};}
            case "liquid_shadow" -> {a=new float[]{.16f,.12f,.26f};b=new float[]{.5f,.32f,.82f};core=new float[]{.78f,.7f,1f};}
            default -> {a=new float[]{1f,.95f,.82f};b=new float[]{.92f,.3f,.24f};core=new float[]{1f,1f,.95f};} // paper: cream with vermilion fold accents
        }
        return new Pal(tint(a,b,r(seed,1)*.6),b,core);
    }
    private static Vec3 baseOf(View v){
        Motif m=v.motif();
        boolean body=m==Motif.DARK_WINGS||m==Motif.DARK_CONSTRUCT||m==Motif.NITROGEN_SHELL||m==Motif.NITROGEN_DOME||m==Motif.PAPER_WING
                ||m==Motif.PAPER_ARMOR||m==Motif.WATER_SHIELD||m==Motif.FLOAT_RESCUE;
        if(body||v.endpoint().distanceToSqr(v.origin())<4)return v.anchor();
        return v.endpoint();
    }
    private static Verb verbOf(String s){
        if(has0(s,"lift","updraft","ascent","jump","buoy","vertical","hover","float_item_lift"))return Verb.RISE;
        if(has0(s,"fall","descent","drop","lower","rescue","air_brake","steady","suspen"))return Verb.FALL;
        if(has0(s,"push","shove","repel","ram","burst","blast","shock","rocket","impact","uppercut"))return Verb.PUSH;
        if(has0(s,"pull","tow","recall","retract","drain","refill"))return Verb.PULL;
        if(has0(s,"sweep","cone","fan","crosswind","spread","squall","rain","gust"))return Verb.SWEEP;
        if(has0(s,"pierce","lance","spear","strike","blade","whip","jet","salvo","shard"))return Verb.PIERCE;
        if(has0(s,"field","ring","dome","domain","orbit","eddy","vortex","crowd","conflict"))return Verb.RING;
        if(has0(s,"scan","sense","probe","listen","survey","scout","select","filter","detect","pulse"))return Verb.SCAN;
        if(has0(s,"guard","shield","screen","wall","bulwark","cover","plate","armor","curtain","barrier","shelter"))return Verb.GUARD;
        if(has0(s,"dash","step","side","back","turn","retreat","charge"))return Verb.STEP;
        if(has0(s,"walkway","ramp","stairs","ladder","platform","gate","bridge","catwalk","rail","patch","pad","construct","array"))return Verb.BUILD;
        if(has0(s,"link","route","path","follow","switch","transfer","relay","checkpoint","anchor"))return Verb.LINK;
        return Verb.NONE;
    }
    private static boolean has0(String s,String... terms){for(String t:terms)if(s.contains(t))return true;return false;}

    // ------------------------------------------------------------------ verb glyphs (every tier)
    private static void chevron(Canvas c,Vec3 at,double size,double dir,float[] rgb,float al){
        Vec3 l=at.add(c.right.scale(-size)).add(c.up.scale(-size*.6*dir)),ap=at.add(c.up.scale(size*.5*dir)),rr=at.add(c.right.scale(size)).add(c.up.scale(-size*.6*dir));
        c.ribbon("streak",true,List.of(l,ap,rr),x->size*.22,x->al,rgb,1,0);
    }
    private static void verbGlyph(Canvas c,View v,Pal p,Verb verb,Vec3 base,int tier,long seed,double e,double t){
        int n=1+tier/2+(int)(r(seed,2)*2);double size=.22+.05*tier+r(seed,3)*.08;double spin=(r(seed,4)<.5?-1:1);
        Vec3 f=forward(v),fr=rightOf(f),fu=upOf(f,fr);Vec3 O=v.origin(),E=v.endpoint();
        switch(verb){
            case RISE -> {for(int k=0;k<n+1;k++){double ph=frac(t*.025+k/(double)(n+1));chevron(c,base.add(0,.3+ph*(1.4+tier*.3),0),size,1,p.main,(float)(.85*e*Math.sin(ph*Math.PI)));}}
            case FALL -> {for(int k=0;k<n+1;k++){double ph=frac(t*.025+k/(double)(n+1));chevron(c,base.add(0,1.8+tier*.2-ph*(1.6+tier*.2),0),size,-1,p.main,(float)(.85*e*Math.sin(ph*Math.PI)));}}
            case PUSH -> {int m=5+tier*2;double ph=frac(t*.05);
                for(int k=0;k<m;k++){double a=k*Math.PI*2/m+r(seed,5);Vec3 d=fr.scale(Math.cos(a)).add(fu.scale(Math.sin(a)));Vec3 n2=f.cross(d).normalize();
                    c.plane("needle",true,E.add(d.scale(.3+ph*(.9+tier*.2))),d.scale(.35+tier*.05),n2.scale(.035),p.core,(float)(.8*e*(1-ph)));}
                faceRing(c,"ring",E,f,.3+ph*(1+tier*.25),p.main,(float)(.7*e*(1-ph)));}
            case PULL -> {int m=5+tier;double ph=frac(t*.05);
                for(int k=0;k<m;k++){double a=k*Math.PI*2/m+r(seed,5);Vec3 d=fr.scale(Math.cos(a)).add(fu.scale(Math.sin(a)));Vec3 n2=f.cross(d).normalize();
                    c.plane("needle",true,E.add(d.scale((1-ph)*(1.1+tier*.2)+.2)),d.scale(.35),n2.scale(.035),p.core,(float)(.8*e*ph));}}
            case SWEEP -> {double ph=Math.min(1,t*.06),span=Math.PI*(.4+.08*tier);var pts=new ArrayList<Vec3>();Vec3 fl=flat(f),flr=rightOf(fl);
                for(int i=0;i<18;i++){double a=-span+2*span*i/17.*Math.min(1,.3+ph);double R=1.2+.35*tier;pts.add(O.add(fl.scale(Math.cos(a)*R)).add(flr.scale(Math.sin(a)*R)).add(0,.9,0));}
                c.ribbon("streak",true,pts,x->.1+.12*x,x->.85*x*e,p.main,1,0);c.ribbon("streak",true,pts,x->.34,x->.2*x*e,p.accent,1,0);}
            case PIERCE -> {double len=Math.max(1,O.distanceTo(E));
                c.ribbon("streak",true,line(O,E,2),x->.07*(1-x*.85)+.01,x->.95*e,p.core,1,0);
                c.ribbon("streak",true,line(O,E,2),x->.18*(1-x*.9),x->.35*e,p.main,1,0);
                c.sprite("sparkle",true,E,.3+tier*.12+.1*Math.sin(t*.4),t*.2*spin,p.core,(float)(.9*e));}
            case RING -> {for(int k=0;k<2+tier/2;k++){double ph=frac(t*.025+k/(double)(2+tier/2));
                c.ground("ring",true,base.add(0,.06,0),.5+ph*(1.4+tier*.5),k%2==0?p.main:p.accent,(float)(.6*e*(1-ph)));}}
            case SCAN -> {double a=t*.12*spin,R=1.1+.3*tier;Vec3 ctr=base.add(0,.07,0);
                c.ribbon("streak",true,line(ctr,ctr.add(Math.cos(a)*R,0,Math.sin(a)*R),2),x->.08,x->.8*e,p.core,1,0);
                for(int k=1;k<=3;k++){double aa=a-k*.18;final float fa=(float)(.25/k*e);c.ribbon("streak",true,line(ctr,ctr.add(Math.cos(aa)*R,0,Math.sin(aa)*R),2),x->.1,x->fa,p.main,1,0);}
                c.ground("ring2",true,ctr,R,p.main,(float)(.4*e));}
            case GUARD -> {int m=4+tier;Vec3 ctr=base.add(0,.9,0);
                for(int k=0;k<m;k++){double a=t*.03*spin+k*Math.PI*2/m,R=.75+.08*tier;Vec3 pos=ctr.add(Math.cos(a)*R,Math.sin(t*.05+k)*.12,Math.sin(a)*R);
                    Vec3 tang=new Vec3(-Math.sin(a),0,Math.cos(a)),up=new Vec3(0,1,0);
                    c.quad("glow",true,pos.subtract(tang.scale(.2)).subtract(up.scale(.25)),pos.add(tang.scale(.2)).subtract(up.scale(.25)),pos.add(tang.scale(.2)).add(up.scale(.25)),pos.subtract(tang.scale(.2)).add(up.scale(.25)),0,0,1,1,p.main[0],p.main[1],p.main[2],(float)(.32*e));
                    c.sprite("sparkle",true,pos,.16,a,p.core,(float)(.8*e));}}
            case STEP -> {for(int k=0;k<4;k++){double ph=frac(t*.04+k*.25);c.sprite("glow",true,O.add(f.scale(-k*.5-.3)).add(0,.9,0),.9-k*.15,0,p.main,(float)(.3*e*(1-ph*.5)/(1+k*.4)));}
                c.ribbon("needle",true,line(O.add(0,.9,0),O.add(f.scale(-2.4-tier*.3)).add(0,.9,0),2),x->.35*(1-x),x->.8*(1-x)*e,p.core,1,0);}
            case BUILD -> {Vec3 fl=flat(f),fr2=rightOf(fl);double s2=.6+.15*tier,ph=Math.min(1,t/14.);Vec3 ctr=E.add(0,.07,0);
                Vec3[] q={ctr.add(fl.scale(-s2*ph)).add(fr2.scale(-s2*ph)),ctr.add(fl.scale(-s2*ph)).add(fr2.scale(s2*ph)),ctr.add(fl.scale(s2*ph)).add(fr2.scale(s2*ph)),ctr.add(fl.scale(s2*ph)).add(fr2.scale(-s2*ph))};
                for(int k=0;k<4;k++)c.ribbon("streak",true,line(q[k],q[(k+1)%4],2),x->.045,x->.8*e,p.accent,1,0);
                c.ribbon("streak",true,line(q[0],q[2],2),x->.03,x->.4*e,p.main,1,0);}
            case LINK -> {double len=Math.max(1,O.distanceTo(E));int nodes=3+tier;
                for(int k=0;k<nodes;k++){double fr3=(k+.5)/nodes,w=frac(t*.04+fr3);Vec3 pos=O.add(E.subtract(O).scale(fr3)).add(0,.2*Math.sin(t*.15+k),0);
                    c.sprite("star5",true,pos,.1+.05*tier*.4,t*.1*spin+k,p.main,(float)(.8*e));}
                c.ribbon("streak",true,line(O,E,2),x->.025,x->.45*e,p.accent,1,0);}
            default -> { }
        }
    }

    // ------------------------------------------------------------------ level >=3: ground sigil, each system its own geometry
    private static void sigil(Canvas c,View v,Pal p,Vec3 base,int tier,long seed,double e,double t){
        double R=1.3+.4*tier;double sp=(r(seed,6)<.5?-1:1)*t*.02;Vec3 g=base.add(0,.06,0);double grow=Math.min(1,t/10.);R*=grow;
        c.ground("ring",true,g,R,p.main,(float)(.55*e));c.ground("ring2",true,g,R*.72,p.accent,(float)(.45*e));
        switch(v.category()){
            case "mental_out" -> { // pentagram of star pupils
                Vec3[] s=new Vec3[5];for(int k=0;k<5;k++){double a=sp+k*Math.PI*2/5;s[k]=g.add(Math.cos(a)*R*.72,.02,Math.sin(a)*R*.72);}
                for(int k=0;k<5;k++){c.ribbon("holo",true,line(s[k],s[(k+2)%5],2),x->.08,x->.8*e,p.main,6,-t*.03);c.sprite("star5",true,s[k].add(0,.12,0),.16+.02*tier,sp*3,p.core,(float)(.9*e));}}
            case "dark_matter" -> { // radial feather petals
                int m=6+tier;for(int k=0;k<m;k++){double a=sp+k*Math.PI*2/m;Vec3 d=new Vec3(Math.cos(a),0,Math.sin(a)),n=new Vec3(-d.z,0,d.x);
                    c.quad("feather_iri",true,g.add(d.scale(R*.2)).subtract(n.scale(.1)),g.add(d.scale(R*.95)).subtract(n.scale(.2)),g.add(d.scale(R*.95)).add(n.scale(.2)),g.add(d.scale(R*.2)).add(n.scale(.1)),0,0,1,1,p.main[0],p.main[1],p.main[2],(float)(.5*e));}}
            case "nitrogen_armor" -> { // frost hexagon with bubble nodes
                Vec3[] h=new Vec3[6];for(int k=0;k<6;k++){double a=sp+k*Math.PI/3;h[k]=g.add(Math.cos(a)*R*.85,0,Math.sin(a)*R*.85);}
                for(int k=0;k<6;k++){c.ribbon("frost",true,line(h[k],h[(k+1)%6],2),x->.16,x->.85*e,p.core,3,0);c.sprite("bubble",true,h[k].add(0,.15,0),.2,0,p.core,(float)(.8*e));}}
            case "aero_hand" -> { // outward wind spirals
                for(int s=0;s<3;s++){var pts=new ArrayList<Vec3>();for(int i=0;i<20;i++){double fr=i/19.,a=sp*5+s*2.094+fr*5;pts.add(g.add(Math.cos(a)*R*fr,.02,Math.sin(a)*R*fr));}
                    c.ribbon("air",true,pts,x->.2,x->.9*(1-x*.5)*e,p.core,1.4,-t*.03);}}
            case "hydro_hand" -> { // stacked ripples and orbiting droplets
                for(int k=0;k<3;k++){double ph=frac(t*.02+k/3.);c.ground("ring2",true,g,R*(.3+ph*.7),p.main,(float)(.5*e*(1-ph)));}
                for(int k=0;k<4;k++){double a=sp*4+k*1.57;c.sprite("blob",false,g.add(Math.cos(a)*R*.8,.12+.08*Math.sin(t*.2+k),Math.sin(a)*R*.8),.12,0,p.main,(float)(.75*e));}}
            case "float_dial" -> { // rings stacked on a rising axis
                for(int k=0;k<4;k++){double ph=frac(t*.015+k/4.);c.ground("ring2",true,g.add(0,ph*1.8,0),R*.55,p.main,(float)(.5*e*Math.sin(ph*Math.PI)));}
                for(int k=0;k<6;k++){double a=sp*3+k*1.047;c.sprite("bubble_iri",true,g.add(Math.cos(a)*R*.9,.2+frac(t*.01+k*.17)*1.6,Math.sin(a)*R*.9),.14,0,p.core,(float)(.8*e));}}
            case "liquid_shadow" -> { // dark pool with radial tendrils
                c.ground("blob",false,g.add(0,-.02,0),R*.9,p.main,(float)(.9*e));
                for(int k=0;k<7;k++){double a=sp+k*.9+r(seed,7)*2;var pts=new ArrayList<Vec3>();for(int i=0;i<7;i++){double fr=i/6.;pts.add(g.add(Math.cos(a+Math.sin(fr*4+t*.1)*.15)*R*fr,.02+fr*.15,Math.sin(a+Math.sin(fr*4+t*.1)*.15)*R*fr));}
                    c.ribbon("inkflow",false,pts,x->.18*(1-x),x->.97*e,new float[]{1,1,1},1,0);}
                c.ground("ring",true,g,R,p.accent,(float)(.35*e));}
            default -> { // paper: two counter-rotating folded squares
                for(int sq=0;sq<2;sq++){double rot=(sq==0?1:-1)*sp*2+sq*.785;Vec3[] q=new Vec3[4];for(int k=0;k<4;k++){double a=rot+k*Math.PI/2+.785;q[k]=g.add(Math.cos(a)*R*.8,0,Math.sin(a)*R*.8);}
                    for(int k=0;k<4;k++)c.ribbon("streak",true,line(q[k],q[(k+1)%4],2),x->.045,x->.7*e,sq==0?p.main:p.accent,1,0);}}
        }
    }

    // ------------------------------------------------------------------ level >=4: light pillar with a system-specific body
    private static void pillar(Canvas c,View v,Pal p,Vec3 base,int tier,long seed,double e,double t){
        double H=(tier==4?5.5:13.)*Math.min(1,t/12.);Vec3 b=base.add(0,.05,0),top=b.add(0,H,0);
        var up=new Vec3(0,1,0);
        switch(v.category()){
            case "mental_out" -> {c.ribbon("streak",true,line(b,top,2),x->.14*(1-x*.5),x->.7*(1-x*.5)*e,p.main,1,0);
                for(int k=0;k<5;k++){double ph=frac(t*.02+k/5.);c.sprite("star5",true,b.add(0,ph*H,0),.28*(1-ph*.5),t*.05+k,p.core,(float)(.85*e*Math.sin(ph*Math.PI)));}}
            case "dark_matter" -> {c.ribbon("streak",true,line(b,top,2),x->.45*(1-x*.7),x->.45*(1-x*.6)*e,p.main,1,0);
                for(int k=0;k<8;k++){double ph=frac(t*.012+k/8.),a=k*2.4;c.sheet("feather_drift",true,b.add(Math.cos(a)*(.5+ph*.8),ph*H,Math.sin(a)*(.5+ph*.8)),.2,0,(int)(frac(t*.05+k*.3)*8),p.core,(float)(.8*e*Math.sin(ph*Math.PI)));}}
            case "nitrogen_armor" -> {c.ribbon("streak",true,line(b,top,2),x->.3,x->.4*e,p.main,1,0);
                for(int k=0;k<5;k++){double ph=frac(t*.018+k/5.);c.ground("ring",true,b.add(0,ph*H,0),.5+ph*1.4,p.main,(float)(.5*e*(1-ph)));}}
            case "aero_hand" -> {for(int s=0;s<2;s++){var pts=new ArrayList<Vec3>();for(int i=0;i<26;i++){double fr=i/25.,a=-t*.25+s*Math.PI+fr*14;pts.add(b.add(Math.cos(a)*(.3+fr*.5),fr*H,Math.sin(a)*(.3+fr*.5)));}
                c.ribbon("air",true,pts,x->.26,x->.95*(1-x*.4)*e,p.core,2,-t*.05);}}
            case "hydro_hand" -> {var pts=CanonFx.wave(b,top,18,.2,3,-t*.25,new Vec3(1,0,0));
                c.ribbon("flow",false,pts,x->.7*(1-x*.5),x->.8*(1-x*.3)*e,p.main,H*.6,-t*.06);c.ribbon("caustic",true,pts,x->.5,x->.5*e,p.core,H*.9,-t*.1);
                c.sheet("burst",true,top,1.1,0,(int)(frac(t*.04)*8),p.core,(float)(.6*e));}
            case "float_dial" -> {for(int k=0;k<14;k++){double ph=frac(t*.012+rnd(k)),a=k*1.9;c.sprite("bubble_iri",true,b.add(Math.cos(a+t*.02)*.5*ph,ph*H,Math.sin(a+t*.02)*.5*ph),.12+rnd(k+3)*.14,0,p.core,(float)(.8*e*Math.sin(ph*Math.PI)));}
                for(int k=0;k<3;k++){double ph=frac(t*.012+k/3.);c.ground("ring2",true,b.add(0,ph*H,0),.8,p.main,(float)(.5*e*Math.sin(ph*Math.PI)));}}
            case "liquid_shadow" -> {c.ribbon("inkflow",false,line(b,top,2),x->.5*(1-x*.75),x->.97*(1-x*.3)*e,new float[]{1,1,1},H*.5,t*.03);
                for(int k=0;k<5;k++){double ph=frac(t*.02+k/5.);c.sheet("ink",false,b.add(Math.sin(k*2.0)*.3,H*(1-ph),Math.cos(k*2.0)*.3),.5,0,(int)(ph*8),p.main,(float)(.9*e*Math.sin(ph*Math.PI)));}}
            default -> {c.ribbon("streak",true,line(b,top,2),x->.1,x->.4*e,p.accent,1,0);
                Vec3 up2=new Vec3(0,1,0);for(int k=0;k<9;k++){double ph=frac(t*.014+k/9.),a=k*.9+t*.04;Vec3 pos=b.add(Math.cos(a)*.7,ph*H,Math.sin(a)*.7);
                    Vec3 tang=new Vec3(-Math.sin(a),0,Math.cos(a));c.fold("paper_fold",pos,tang,up2,.4,.45,t*.1+k,p.main,(float)(.85*e*Math.sin(ph*Math.PI)));}}
        }
        c.sprite("glow",true,b,.7,0,p.main,(float)(.14*e));c.sprite("glow",true,top,1.2,0,p.main,(float)(.14*e));
    }

    // ------------------------------------------------------------------ level 5: halo and repeating shock rings
    private static void spectacle(Canvas c,View v,Pal p,Vec3 base,long seed,double e,double t){
        double H=13*Math.min(1,t/12.);Vec3 b=base.add(0,.06,0);
        for(int k=0;k<3;k++){double ph=frac(t*.02+k/3.);c.ground("ring2",true,b,1.5+ph*6,p.main,(float)(.32*e*(1-ph)*(1-ph)));}
        Vec3 halo=b.add(0,H,0);c.plane("ring2",true,halo,new Vec3(2.2,0,0),new Vec3(0,0,2.2),p.core,(float)(.55*e));
        c.plane("ring",true,halo.add(0,-.4,0),new Vec3(1.4,0,0),new Vec3(0,0,1.4),p.accent,(float)(.5*e));
        switch(v.category()){
            case "liquid_shadow" -> {for(int k=0;k<4;k++){double ph=frac(t*.02+k/4.);c.sheet("ink",false,b.add(Math.cos(k*1.57)*2.2*ph,.4,Math.sin(k*1.57)*2.2*ph),.9,0,(int)(ph*8),p.main,(float)(.9*e*(1-ph)));}}
            case "paper_psychokinesis" -> {for(int k=0;k<10;k++){double ph=frac(t*.014+k/10.),a=k*.63+t*.03;Vec3 tang=new Vec3(-Math.sin(a),0,Math.cos(a));
                c.fold("paper_fold",b.add(Math.cos(a)*(1.5+ph*3),.3+ph*5,Math.sin(a)*(1.5+ph*3)),tang,new Vec3(0,1,0),.5,.55,t*.12+k,new float[]{1,1,1},(float)(.9*e*Math.sin(ph*Math.PI)));}}
            default -> c.sheet("burst",true,b.add(0,.9,0),1.0+.15*Math.sin(t*.2),0,2+(int)(frac(t*.035)*6),p.main,(float)(.3*e));
        }
        for(int k=0;k<10;k++){double ph=frac(t*.015+rnd(k)),a=k*.63+t*.03;
            c.sprite("sparkle",true,b.add(Math.cos(a)*(1+ph*3),ph*H*.8,Math.sin(a)*(1+ph*3)),.22,t*.1+k,p.core,(float)(.85*e*Math.sin(ph*Math.PI)));}
    }
}

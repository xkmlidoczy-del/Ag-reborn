package cn.academy.port.client.canon;

import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.client.canon.CanonFx.View;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Per-system canon looks. Each script reads only the already-validated view (skill, anchor, endpoints, phase, age)
 * and draws light, ribbons and folds. Nothing here reads or changes game state.
 */
final class CanonFxScripts {
    private static final float[] GOLD={1f,.78f,.24f},PALE_GOLD={1f,.95f,.7f},WHITE={1f,1f,1f},CYAN={.45f,.95f,1f},MAGENTA={1f,.55f,.9f},
            WARM={1f,.9f,.55f},ICE={.78f,.95f,1f},MINT={.8f,1f,.94f},WATER={.25f,.65f,1f},FOAM={.9f,.97f,1f},BUOY={.55f,.97f,1f},
            INK={.1f,.08f,.17f},INK_RIM={.62f,.55f,.82f},PAPER={1f,.97f,.88f},CREASE={1f,1f,1f};
    private CanonFxScripts() {}

    static void draw(Canvas c,View v){
        if(cn.academy.port.canon.CanonOffenseSkills.find(v.skill())!=null){CanonFxOffense.draw(c,v);return;}
        // Systems moved to FX4 draw their own level ladder and skip the shared sigil / pillar / halo tiers.
        if(CanonFxForms.upgraded(v.category())){CanonFxForms.draw(c,v);return;}
        switch(v.category()){
            case "mental_out" -> mental(c,v);
            case "dark_matter" -> dark(c,v);
            case "nitrogen_armor" -> nitrogen(c,v);
            case "aero_hand" -> aero(c,v);
            case "hydro_hand" -> hydro(c,v);
            case "float_dial" -> buoyancy(c,v);
            case "liquid_shadow" -> liquid(c,v);
            case "paper_psychokinesis" -> paper(c,v);
            default -> { }
        }
        CanonFxTier.draw(c,v);
    }

    // ------------------------------------------------------------------ shared helpers
    /** Animation clock. Real casts mostly last 10-12 ticks; short effects play their motion faster so a whole cycle fits in the life. */
    static final double ANIM_SPAN=36;
    static double time(View v){double life=Math.max(1,v.life());return life<ANIM_SPAN?v.age()*ANIM_SPAN/life:v.age();}
    private static double ss(double x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    /**
     * Envelope for every phase: a short rise (15% of life, 1.5-6 ticks), full strength, then a fade over the last 30% of life,
     * so nothing pops off at expiry. END keeps its own linear fade and starts visible.
     */
    static double env(View v){
        double life=Math.max(1,v.life()),a=Math.max(0,v.age());
        double in=v.phase()==3?1:ss((a+.5)/Math.max(1.5,Math.min(6,life*(v.phase()==2?.1:.15))));
        double start=life*.7,out=a<=start?1:1-ss((a-start)/Math.max(1,life-start));
        if(v.phase()==3)out=Math.min(out,Math.max(0,1-a/Math.max(8,life)));
        return Math.max(0,Math.min(in,out));
    }
    static double clamp01(double x){return Math.max(0,Math.min(1,x));}
    static double easeOut(double x){x=clamp01(x);return 1-Math.pow(1-x,3);}
    static double easeIn(double x){x=clamp01(x);return x*x*x;}
    /** Overshoots to about 1.1 before settling: whips, lashes and popping glyphs. */
    static double easeOutBack(double x){x=clamp01(x);double c1=1.70158,c3=c1+1;return 1+c3*Math.pow(x-1,3)+c1*Math.pow(x-1,2);}
    /** Progress of an opening move that lasts at most {@code ticks} (or 40% of a short life). */
    static double reveal(View v,double ticks){return clamp01(v.age()/Math.max(1,Math.min(v.life()*.4,ticks)));}
    static double life01(View v){return clamp01(v.age()/Math.max(1,v.life()));}
    /** The first {@code f} (0..1) of a polyline, the last point interpolated; fewer than two points means nothing to draw. */
    static List<Vec3> partial(List<Vec3> pts,double f){
        f=clamp01(f);int n=pts.size();if(n<2||f<=0)return List.of();
        double at=f*(n-1);int i=(int)Math.floor(at);var out=new ArrayList<Vec3>(pts.subList(0,Math.min(n,i+1)));
        if(i<n-1){double k=at-i;out.add(pts.get(i).add(pts.get(i+1).subtract(pts.get(i)).scale(k)));}
        return out.size()<2?List.of():out;
    }
    static double hit(View v){return v.phase()==2?Math.max(0,1-v.age()/Math.max(4,v.life())):0;}
    static double rnd(int s){double x=Math.sin(s*12.9898+78.233)*43758.5453;return x-Math.floor(x);}
    static int frame(double age,double span){return (int)Math.min(7,Math.max(0,age/span*8));}
    static double frac(double x){return x-Math.floor(x);}
    static boolean has(View v,String... terms){for(String t:terms)if(v.skill().contains(t))return true;return false;}
    static Vec3 forward(View v){
        Vec3 d=v.endpoint().subtract(v.origin());
        if(d.lengthSqr()>.25)return d.normalize();
        return Vec3.directionFromRotation(0,v.yaw());
    }
    static Vec3 flat(Vec3 f){Vec3 h=new Vec3(f.x,0,f.z);return h.lengthSqr()<1e-6?new Vec3(0,0,1):h.normalize();}
    static Vec3 rightOf(Vec3 f){Vec3 r=f.cross(new Vec3(0,1,0));return r.lengthSqr()<1e-6?new Vec3(1,0,0):r.normalize();}
    static Vec3 upOf(Vec3 f,Vec3 r){return r.cross(f).normalize();}
    static float[] tint(float[] a,float[] b,double k){return new float[]{(float)(a[0]+(b[0]-a[0])*k),(float)(a[1]+(b[1]-a[1])*k),(float)(a[2]+(b[2]-a[2])*k)};}
    /** Ring facing along an axis. */
    static void faceRing(Canvas c,String tex,Vec3 p,Vec3 axis,double radius,float[] rgb,float al){
        Vec3 r=rightOf(axis),u=upOf(axis,r);c.plane(tex,true,p,r.scale(radius),u.scale(radius),rgb,al);
    }
    static List<Vec3> line(Vec3 a,Vec3 b,int n){var out=new ArrayList<Vec3>(n);for(int i=0;i<n;i++)out.add(a.add(b.subtract(a).scale(i/(double)(n-1))));return out;}

    // ------------------------------------------------------------------ mental out: star pupils, remote, golden threads
    private static void mental(Canvas c,View v){
        double e=env(v),t=time(v);
        Vec3 mark=v.endpoint().distanceToSqr(v.origin())<1?v.anchor().add(0,2.35,0):v.endpoint().add(0,.4,0);
        Vec3 up=new Vec3(0,1,0);
        if(v.motif()==cn.academy.port.canon.CanonVfxDesign.Motif.CROWD){
            Vec3 ctr=v.endpoint().distanceToSqr(v.origin())<1?v.anchor():v.endpoint();double r=Math.min(3.4,1.2+t*.04);
            c.ground("ring2",true,ctr.add(0,.05,0),r,GOLD,(float)(.55*e));
            c.ground("glow",true,ctr.add(0,.04,0),r*.9,PALE_GOLD,(float)(.12*e));
            Vec3[] star=new Vec3[6];
            double each=Math.max(.5,Math.min(1.2,v.life()*.06));          // stars light one after another
            for(int k=0;k<6;k++){double a=t*.05+k*Math.PI/3;star[k]=ctr.add(Math.cos(a)*r*.8,2.1+Math.sin(t*.1+k)*.12,Math.sin(a)*r*.8);
                double pop=easeOutBack((v.age()-k*each)/3);if(pop<=0)continue;
                c.sprite("star5",true,star[k],.26*pop,t*.03+k,GOLD,(float)(.9*e));c.sprite("glow",true,star[k],.7*pop,0,PALE_GOLD,(float)(.3*e));}
            double linked=clamp01((v.age()-6*each)/3);                      // then the threads join them
            for(int k=0;k<6&&linked>0;k++)c.ribbon("holo",true,partial(line(star[k],star[(k+1)%6],6),easeOut(linked)),x->.07,x->.8*e,GOLD,5,-t*.03);
            return;
        }
        if(v.motif()==cn.academy.port.canon.CanonVfxDesign.Motif.DISRUPT){
            double u=Math.min(1,v.age()/Math.max(10,v.life()));
            c.sprite("star5",true,mark,.42*(1-u*.7),t*.15,GOLD,(float)(.9*e*(1-u*.5)));
            for(int k=0;k<5;k++){double a=k*Math.PI*2/5+.3,d=.2+t*.06;
                Vec3 p=mark.add(c.right.scale(Math.cos(a)*d)).add(c.up.scale(Math.sin(a)*d));
                c.sprite("star5",true,p,.13*(1-u),a+t*.2,PALE_GOLD,(float)(.85*e*(1-u)));}
            c.sprite("ring2",true,mark,.35+t*.07,0,GOLD,(float)(.8*(1-u)*e));
            c.sheet("starburst",true,mark,.9,0,frame(v.age(),Math.max(10,v.life())),GOLD,(float)(.9*e));
            return;
        }
        // REMOTE: remote in hand -> golden thread -> star pupil mark on the target
        // Rhythm: the thread is drawn out from the hand, the star pupil pops when it arrives, then blinks.
        double drawn=easeOut(reveal(v,6));
        var full=CanonFx.wave(v.origin(),mark,16,.06,2.5,-t*.2,up);var thread=partial(full,drawn);
        c.ribbon("holo",true,thread,x->.12,x->.95*e,GOLD,full.size()*.45*drawn,-t*.03);
        c.ribbon("streak",true,thread,x->.05,x->.3*e,PALE_GOLD,1,0);
        if(!thread.isEmpty())c.sprite("sparkle",true,thread.get(thread.size()-1),.22,t*.2,PALE_GOLD,(float)(.9*e*(1-drawn*.6)));
        for(int k=0;k<3&&drawn>=1;k++){double f=frac(t*.05+k/3.);int i=(int)Math.min(15,Math.round(f*15));
            c.sprite("glow",true,full.get(i),.2,0,PALE_GOLD,(float)(.9*e));}
        double arrive=Math.min(v.life()*.4,6)*.9,pop=v.age()<arrive?0:easeOutBack((v.age()-arrive)/3);
        if(pop<=0)return;
        c.sheet("starburst",true,mark,.8,0,frame(t%26,26),GOLD,(float)(.55*e));
        double blink=1-.45*Math.exp(-Math.pow((frac(t*.04)-.5)/.04,2));
        double pulse=(1+.12*Math.sin(t*.3))*pop*blink;
        c.sprite("glow",true,mark,.8*pulse,0,GOLD,(float)(.16*e));
        c.sprite("star5",true,mark,.36*pulse,t*.025,GOLD,(float)(.92*e));
        c.sprite("star5",true,mark,.2*pulse,-t*.04,WHITE,(float)(.9*e));
        for(int k=0;k<4;k++){double a=t*.08+k*Math.PI/2;
            c.sprite("sparkle",true,mark.add(c.right.scale(Math.cos(a)*.55)).add(c.up.scale(Math.sin(a)*.55)),.14,a,PALE_GOLD,(float)(.8*e));}
        double h=hit(v);if(h>0)c.sprite("ring2",true,mark,.3+(1-h)*1.0,0,GOLD,(float)(h*.8));
    }

    // ------------------------------------------------------------------ dark matter: white feather wings, dispersion edge
    private static void dark(Canvas c,View v){
        double e=env(v),t=time(v);Vec3 f=flat(Vec3.directionFromRotation(0,v.yaw())),back=f.scale(-1),r=rightOf(f);
        switch(v.motif()){
            case DARK_WINGS -> {
                Vec3 root=v.anchor().add(0,1.3,0).add(back.scale(.14));
                for(int side=-1;side<=1;side+=2)for(int k=0;k<Math.min(6,2+v.tier());k++){
                    double asym=side<0?.9:1.07,spread=Math.toRadians(14+k*17)+Math.sin(t*.1+side*.5+k*.4)*.05;
                    double len=(1.05+k*.1)*asym*Math.min(1,.15+v.age()*(.14-.012*k)+(v.phase()==0?0:1));if(v.phase()==3)len*=e;
                    Vec3 dir=r.scale(side*Math.sin(spread)).add(new Vec3(0,Math.cos(spread)*.75,0)).add(back.scale(.34+.04*k)).normalize();
                    Vec3 perp=dir.cross(f);perp=perp.lengthSqr()<1e-6?r:perp.normalize();double w=.16+.02*k;
                    Vec3 tip=root.add(dir.scale(len));
                    for(int layer=0;layer<3;layer++){
                        // pearl body, then an iridescent thin-film layer and a faint offset ghost: the colour is in the texture, not a tint
                        String tex=layer==1?"feather_iri":"feather";float[] col=layer==2?CYAN:WHITE;double off=layer==2?(.03+.012*k):0;
                        Vec3 o=perp.scale(off);float al=(float)((layer==0?.5:layer==1?.45:.08)*e);
                        Vec3 base=layer==0?root:root.add(dir.scale(len*.35)); // only the outer feather catches the colour
                        c.quad(tex,layer!=0,base.subtract(perp.scale(w*.4)).add(o),tip.subtract(perp.scale(w)).add(o),
                                tip.add(perp.scale(w)).add(o),base.add(perp.scale(w*.4)).add(o),0,0,1,1,col[0],col[1],col[2],al);
                    }
                }
                c.sprite("glow",true,root.add(back.scale(.35)),2.6,0,tint(WHITE,CYAN,.3),(float)(.07*e));
                for(int k=0;k<6;k++){double ph=frac(t*.012+rnd(k)),side=k%2==0?-1:1;
                    Vec3 p=root.add(r.scale(side*(.8+rnd(k+9)*1.1))).add(back.scale(.4+rnd(k+3)*.5)).add(0,-ph*1.7+.2,0);
                    c.sheet("feather_drift",true,p,.17,0,(int)(frac(t*.05+k*.37)*8),WHITE,(float)(.75*e*(1-ph)));}
            }
            case DARK_BLADE -> {
                Vec3 a=v.origin(),b=v.endpoint();Vec3 up=new Vec3(0,1,0);
                double cut=easeOut(reveal(v,4)),thin=1-.65*clamp01((life01(v)-.4)/.6);Vec3 tipAt=a.add(b.subtract(a).scale(cut));
                c.ribbon("streak",true,line(a,tipAt,2),x->.2*(1-x*.4)*thin,x->.95*e,WHITE,1,0);
                c.ribbon("streak",true,partial(CanonFx.wave(a,b,12,.05,2,t*.2,up),cut),x->.34*thin,x->.28*e,CYAN,1,0);
                c.ribbon("streak",true,partial(CanonFx.wave(a,b,12,.05,2,-t*.2+2,up),cut),x->.34*thin,x->.28*e,MAGENTA,1,0);
                c.sprite("sparkle",true,tipAt,.5+hit(v)*.8,t*.1,WHITE,(float)(.9*e));c.sprite("glow",true,b,.6+hit(v)*.5,0,tint(WHITE,CYAN,.3),(float)(.2*e));
            }
            default -> { // DARK_CONSTRUCT: orbiting white shards
                for(int k=0;k<6;k++){double a=t*.07+k*Math.PI/3,rad=1.1+.1*Math.sin(t*.1+k);
                    Vec3 p=v.anchor().add(Math.cos(a)*rad,1.2+Math.sin(t*.09+k*2)*.35,Math.sin(a)*rad);
                    Vec3 tang=new Vec3(-Math.sin(a),0,Math.cos(a));Vec3 up=new Vec3(0,1,0);
                    c.quad("feather",true,p.subtract(tang.scale(.22)),p.add(tang.scale(.22)),p.add(tang.scale(.22)).add(up.scale(.07)),p.subtract(tang.scale(.22)).add(up.scale(.07)),
                            0,0,1,1,1f,1f,1f,(float)(.75*e));
                    c.sprite("glow",true,p,.4,0,tint(WHITE,CYAN,.4),(float)(.3*e));}
                c.ground("ring2",true,v.anchor().add(0,.05,0),1.4,WHITE,(float)(.3*e));
            }
        }
    }

    // ------------------------------------------------------------------ nitrogen armor: body-hugging skin, impact pressure
    private static void nitrogen(Canvas c,View v){
        double e=env(v),t=time(v);
        switch(v.motif()){
            case NITROGEN_SHELL -> {
                Vec3 p=v.anchor();
                double[] hs={.1,.48,.86,1.24,1.62,1.9};
                for(int i=0;i<hs.length;i++){double pulse=1+.05*Math.sin(t*.2+i),rad=(i==0||i==5?.3:.44)*pulse;
                    c.ground("ring",true,p.add(0,hs[i],0),rad,ICE,(float)(.36*e));}
                c.plane("glow",true,p.add(0,1,0),c.right.scale(.75),c.up.scale(1.25),ICE,(float)(.12*e));
                c.plane("frost",false,p.add(0,1,0),c.right.scale(.5),c.up.scale(1.0),WHITE,(float)(.38*e));
                for(int k=0;k<7;k++){double ph=frac(t*.012+rnd(k)),a=rnd(k+20)*6.28+t*.03;
                    c.sprite("bubble",true,p.add(Math.cos(a)*.36,.1+ph*1.8,Math.sin(a)*.36),.11+rnd(k+5)*.06,0,WHITE,(float)(.75*e*Math.sin(ph*Math.PI)));}
                for(int k=0;k<4;k++){double a=t*.05+k*1.57;c.sprite("sparkle",true,p.add(Math.cos(a)*.5,.4+k*.4,Math.sin(a)*.5),.13,a,WHITE,(float)(.7*e));}
            }
            case NITROGEN_PUNCH -> {
                Vec3 f=forward(v);Vec3 E=v.endpoint();double u=Math.min(1,v.age()/Math.max(6,v.life()));
                if(v.phase()==0){faceRing(c,"ring",v.origin().add(f.scale(.5)),f,Math.max(.1,.9-t*.05),ICE,(float)(.7*e));return;}
                // Rhythm: the nitrogen layer compresses at the fist for the first quarter, then releases at the target.
                double squeeze=Math.max(1.5,Math.min(4,v.life()*.25));
                if(v.age()<squeeze){double q=v.age()/squeeze;Vec3 fist=v.origin().add(f.scale(.55));
                    faceRing(c,"ring",fist,f,.9-.7*easeIn(q),ICE,(float)(.75*e));faceRing(c,"ring2",fist,f,.6-.45*easeIn(q),WHITE,(float)(.5*e));
                    c.sprite("glow",true,fist,.25+.2*q,0,ICE,(float)(.25*e));return;}
                u=clamp01((v.age()-squeeze)/Math.max(1,v.life()-squeeze));
                double R=.3+1.2*easeOut(u);float al=(float)(Math.max(0,1-u)*e);
                faceRing(c,"ring",E,f,R,ICE,al);Vec3 fr0=rightOf(f);c.plane("frost",true,E,fr0.scale(R),upOf(f,fr0).scale(R),WHITE,al*.7f);faceRing(c,"ring2",E,f,R*.6,WHITE,al*.7f);
                Vec3 rr=rightOf(f),uu=upOf(f,rr);
                for(int k=0;k<10;k++){double a=k*Math.PI/5+.2;Vec3 d=rr.scale(Math.cos(a)).add(uu.scale(Math.sin(a)));Vec3 n=f.cross(d).normalize();
                    c.plane("needle",true,E.add(d.scale(R*.9)),d.scale(.6),n.scale(.045),WHITE,al*.9f);}
                c.sprite("glow",true,E,.7*(1-u)+.25,0,tint(ICE,CYAN,.4),(float)(.28*Math.max(0,1-u*1.4)));
                c.sheetPlane("burst",true,E,rr.scale(R*1.1),uu.scale(R*1.1),(int)Math.min(7,u*9),ICE,(float)(.9*e));
            }
            default -> { // NITROGEN_DOME
                Vec3 p=v.endpoint().distanceToSqr(v.origin())<4?v.anchor():v.endpoint();double R=Math.min(3.2,.8+t*.12);
                c.ground("ring",true,p.add(0,.05,0),R,ICE,(float)(.6*e));
                for(double h:new double[]{.5,1.1,1.7}){double rr=Math.sqrt(Math.max(.01,R*R-h*h));c.ground("ring2",true,p.add(0,h,0),rr,ICE,(float)(.28*e));}
                for(int k=0;k<6;k++){double a=rnd(k)*6.28+t*.02,ph=frac(t*.01+rnd(k+4));
                    c.sprite("bubble",true,p.add(Math.cos(a)*R*.7,.2+ph*1.6,Math.sin(a)*R*.7),.16,0,WHITE,(float)(.7*e));}
            }
        }
    }

    // ------------------------------------------------------------------ aero hand: wind needles, spirals, shock cones
    private static void aero(Canvas c,View v){
        double e=env(v),t=time(v);Vec3 f=forward(v);Vec3 rr=rightOf(f),uu=upOf(f,rr);
        switch(v.motif()){
            case AERO_JET -> {
                Vec3 back=flat(f).scale(-1);back=new Vec3(back.x,-.25,back.z).normalize();Vec3 br=rightOf(back),bu=upOf(back,br);
                double L=Math.max(2.2,Math.min(4.5,v.endpoint().distanceTo(v.origin())+1.2));
                for(int h=0;h<3;h++){
                    var pts=new ArrayList<Vec3>();
                    for(int i=0;i<16;i++){double fr=i/15.,rad=.06+.28*fr,a=-t*.5+h*2.094+fr*8;
                        pts.add(v.origin().add(back.scale(fr*L)).add(br.scale(Math.cos(a)*rad)).add(bu.scale(Math.sin(a)*rad)));}
                    final int hh=h;c.ribbon("air",true,pts,x->.22*(1-x*.5),x->.9*(1-x)*e,MINT,1.2,-t*.04+hh*.3);
                }
                for(int k=0;k<4;k++)c.sprite("glow",true,v.origin().add(back.scale(.25+k*.38)),.42-k*.07,0,WHITE,(float)(.55*e*(1-k*.18)));
                for(int k=0;k<3;k++){double ph=frac(t*.06+k/3.);faceRing(c,"ring",v.origin().add(back.scale(.3+ph*L*.7)),back,.2+ph*.8,MINT,(float)(.5*e*(1-ph)));}
            }
            case AERO_LAUNCH -> {
                Vec3 a=v.origin(),b=v.endpoint();
                for(int h=0;h<2;h++){var pts=new ArrayList<Vec3>();
                    for(int i=0;i<18;i++){double fr=i/17.,ang=t*.4+h*Math.PI+fr*10,rad=.16+.16*Math.sin(fr*Math.PI);
                        pts.add(a.add(b.subtract(a).scale(fr)).add(rr.scale(Math.cos(ang)*rad)).add(uu.scale(Math.sin(ang)*rad)));}
                    c.ribbon("air",true,pts,x->.2,x->.85*e,MINT,1.5,-t*.05);}
                c.ribbon("streak",true,line(a,b,2),x->.08,x->.5*e,WHITE,1,0);
                for(int k=0;k<4;k++){double ph=frac(t*.05+k/4.);faceRing(c,"ring",a.add(b.subtract(a).scale(ph)),f,.25+ph*.5,MINT,(float)(.5*e*(1-ph)));}
                c.sprite("glow",true,b,.7+hit(v),0,WHITE,(float)(.4*e));
            }
            default -> { // AERO_BARRAGE
                double L=Math.max(2,Math.min(6,v.endpoint().distanceTo(v.origin())));
                for(int k=0;k<5;k++){double ph=frac(t*.045+k/5.);faceRing(c,"ring",v.origin().add(f.scale(.5+ph*L)),f,.3+ph*1.1,MINT,(float)(.6*e*(1-ph)));}
                for(int k=0;k<12;k++){double a=k*Math.PI*2/12+rnd(k)*.4;Vec3 d=rr.scale(Math.cos(a)).add(uu.scale(Math.sin(a)));double ph=frac(t*.07+rnd(k+1));
                    double speed=3*Math.pow(1-ph,2),stretch=.25+.6*speed;   // derivative of the ease: long streaks while fast
                    c.plane("air",true,v.origin().add(f.scale(.4+easeOut(ph)*L)).add(d.scale(.25+ph*.7)),f.scale(stretch),d.cross(f).normalize().scale(.05),WHITE,(float)(.8*e*(1-ph)));}
                c.sprite("glow",true,v.origin().add(f.scale(.4)),.45,0,MINT,(float)(.18*e));
                c.sheetPlane("burst",true,v.origin().add(f.scale(.5+L*.5)),rr.scale(1.1),uu.scale(1.1),frame(t%18,18),MINT,(float)(.6*e));
            }
        }
    }

    // ------------------------------------------------------------------ hydro hand: flowing water bands, spray, curtains
    private static void hydro(Canvas c,View v){
        double e=env(v),t=time(v);Vec3 up=new Vec3(0,1,0);
        switch(v.motif()){
            case WATER_WHIP,WATER_TRANSFER -> {
                boolean transfer=v.motif()==cn.academy.port.canon.CanonVfxDesign.Motif.WATER_TRANSFER;
                Vec3 a=v.origin(),b=v.endpoint();
                // Rhythm: the band lashes from the hand (5 ticks), splashes when it lands, and drips back under gravity.
                double lash=easeOut(reveal(v,5)),arrive=Math.min(v.life()*.4,5);
                var fullPath=CanonFx.wave(a,b,20,transfer?.08:.16,2.5,-t*.25,up);var path=partial(fullPath,lash);double L=Math.max(1,a.distanceTo(b));
                c.ribbon("flow",false,path,x->.55*(.7+.3*Math.sin(x*Math.PI)),x->.85*e,WATER,L*.9*lash,-t*.045);
                c.ribbon("caustic",true,path,x->.38,x->.4*e,tint(WATER,FOAM,.4),L*1.3*lash,-t*.07);
                c.ribbon("streak",true,path,x->.06,x->.55*e,FOAM,1,0);
                if(!path.isEmpty()&&lash<1)c.sprite("bubble",true,path.get(path.size()-1),.22,0,FOAM,(float)(.9*e));
                if(v.age()<arrive)return;
                double since=v.age()-arrive,rest=Math.max(1,v.life()-arrive);
                c.sheet("splash",false,b.add(0,.55,0),.9,0,(int)Math.min(7,since/rest*9),FOAM,(float)(.85*e));
                for(int k=0;k<8;k++){double tau=(since+rnd(k)*3)/20.,fr=.3+.7*rnd(k+11);Vec3 p0=fullPath.get((int)Math.round(fr*19));
                    Vec3 p=p0.add((rnd(k+3)-.5)*.6*tau,.6*tau-4.9*tau*tau,(rnd(k+5)-.5)*.6*tau);
                    c.sprite("blob",false,p,.05+rnd(k+2)*.04,0,tint(WATER,FOAM,.3),(float)(.85*e));}
                int packets=transfer?4:2;
                for(int k=0;k<packets;k++){double fr=frac(t*.05+k/(double)packets);int i=(int)Math.round(fr*19);
                    c.sprite("glow",true,path.get(i),.34,0,FOAM,(float)(.7*e));}
                for(int k=0;k<10;k++){double ph=clamp01((since-rnd(k)*2)/Math.max(4,rest*.8)),a2=rnd(k+7)*6.28,sp=.5+rnd(k+3)*.9;
                    Vec3 p=b.add(Math.cos(a2)*sp*ph,1.6*ph-2.2*ph*ph,Math.sin(a2)*sp*ph);
                    c.sprite(k%3==0?"bubble":"glow",true,p,.08+rnd(k+2)*.07,0,FOAM,(float)(.8*e*(1-ph)));}
                c.ground("ring",true,b.add(0,.04,0),.3+frac(t*.04)*.9,FOAM,(float)(.5*e));
                c.sheet("burst",true,b.add(0,.25,0),.7,0,frame(t%16,16),tint(WATER,FOAM,.55),(float)(.7*e));
                double h=hit(v);if(h>0)c.sprite("glow",true,b.add(0,.3,0),.7*(1-h)+.3,0,tint(WATER,FOAM,.5),(float)(h*.3));
            }
            case WATER_SHIELD -> {
                Vec3 f=flat(forward(v));Vec3 r=rightOf(f);Vec3 base=v.anchor().add(f.scale(.95));
                for(int k=-3;k<=3;k++){var pts=new ArrayList<Vec3>();
                    for(int i=0;i<9;i++){double fr=i/8.;pts.add(base.add(r.scale(k*.26+Math.sin(fr*6+t*.15+k)*.03)).add(f.scale(Math.abs(k)*.06)).add(0,fr*2.0,0));}
                    c.ribbon("flow",false,pts,x->.3,x->.5*e,WATER,2,t*.05);}
                c.plane("glow",true,base.add(0,1,0),r.scale(1.1),up.scale(1.1),tint(WATER,FOAM,.3),(float)(.12*e));
                c.ground("ring",true,base.add(0,.04,0),.5+frac(t*.04)*.7,FOAM,(float)(.4*e));
            }
            default -> { // WATER_DOMAIN
                Vec3 p=v.endpoint().distanceToSqr(v.origin())<4?v.anchor():v.endpoint();
                for(int k=0;k<3;k++){double ph=frac(t*.025+k/3.);c.ground("ring2",true,p.add(0,.04,0),.4+ph*2.0,WATER,(float)(.6*e*(1-ph)));}
                c.ground("glow",true,p.add(0,.03,0),1.3,WATER,(float)(.14*e));
                for(int k=0;k<8;k++){double ph=frac(t*.02+rnd(k)),a=rnd(k+5)*6.28,rad=rnd(k+9)*1.4;
                    c.sprite("glow",true,p.add(Math.cos(a)*rad,ph*1.5,Math.sin(a)*rad),.1,0,FOAM,(float)(.7*e*(1-ph)));}
            }
        }
    }

    // ------------------------------------------------------------------ buoyancy: bubbles and density slices
    private static void buoyancy(Canvas c,View v){
        double e=env(v),t=time(v);
        boolean down=has(v,"lower","descent","drop","brake","hostile","rescue","buoy","steady","suspen");
        Vec3 p=v.endpoint().distanceToSqr(v.origin())<1?v.anchor():v.endpoint();
        double opened=reveal(v,8);                    // buoyancy takes hold: one soft ring rises off the ground
        if(opened<1)c.ground("ring2",true,p.add(0,.05+easeOut(opened)*(down?.2:1.2),0),.4+1.6*easeOut(opened),BUOY,(float)(.7*(1-opened)*e));
        switch(v.motif()){
            case FLOAT_FIELD -> {
                double R=1.2+.06*Math.sin(t*.1);
                c.ground("ring2",true,p.add(0,.05,0),R,BUOY,(float)(.5*e));c.ground("glow",true,p.add(0,.04,0),R,BUOY,(float)(.1*e));
                for(int k=0;k<9;k++){double a=t*.025+k*.7,hgt=.3+frac(rnd(k)+t*.006)*1.9;
                    c.sprite("bubble_iri",true,p.add(Math.cos(a)*R*(.5+rnd(k+3)*.5),hgt,Math.sin(a)*R*(.5+rnd(k+3)*.5)),.12+rnd(k+8)*.1,0,WHITE,(float)(.75*e));}
            }
            default -> {
                for(int k=0;k<10;k++){double ph=frac(t*(down?-.012:.015)+rnd(k)),x=Math.sin(t*.09+k*2.1)*.22,z=Math.cos(t*.07+k*1.3)*.22;
                    c.sprite("bubble_iri",true,p.add(x+(rnd(k+4)-.5)*.5,ph*2.4,z+(rnd(k+6)-.5)*.5),.15+rnd(k+2)*.12,0,WHITE,(float)(.9*e*Math.sin(ph*Math.PI)));}
                for(int k=0;k<3;k++){double ph=frac(t*(down?-.02:.02)+k/3.);
                    c.ground("ring2",true,p.add(0,ph*2.3,0),.45+.1*Math.sin(ph*6),BUOY,(float)(.55*e*Math.sin(ph*Math.PI)));}
                for(int k=0;k<4;k++){double a=k*1.57+t*.02;
                    c.plane("needle",true,p.add(Math.cos(a)*.35,1.1,Math.sin(a)*.35),new Vec3(0,.9,0),c.right.scale(.03),BUOY,(float)(.35*e));}
                if(v.motif()==cn.academy.port.canon.CanonVfxDesign.Motif.FLOAT_RESCUE||down)
                    c.ground("ring",true,p.add(0,.05,0),.4+frac(t*.03)*.8,BUOY,(float)(.5*e));
                c.sprite("glow",true,p.add(0,1.1,0),1.5,0,BUOY,(float)(.1*e));
            }
        }
    }

    // ------------------------------------------------------------------ liquid shadow: viscous dark pool, tendrils, glossy drops
    private static void liquid(Canvas c,View v){
        double e=env(v),t=time(v);Vec3 up=new Vec3(0,1,0);Vec3 E=v.endpoint(),O=v.origin();
        switch(v.motif()){
            case LIQUID_STRIKE -> {
                // Rhythm: a viscous lash overshoots the target, thins as it stretches, then surface tension pulls it back.
                double reach=easeOutBack(reveal(v,5))*(1-easeIn(clamp01((life01(v)-.65)/.35)));
                if(reach<=.02)return;
                Vec3 tipAt=O.add(E.subtract(O).scale(reach));double thick=1/Math.sqrt(Math.max(.35,reach));
                var path=CanonFx.wave(O,tipAt,16,.12*reach,2,-t*.3,up);
                c.ribbon("inkflow",false,path,x->.3*thick*(1-x*.7),x->.97*e,WHITE,O.distanceTo(tipAt)*.8,-t*.03);
                c.ribbon("streak",false,path,x->.06,x->.5*e,INK_RIM,1,0);
                c.sprite("blob",false,tipAt,.32+.18*Math.min(1,reach),0,tint(INK,INK_RIM,.15),(float)(.95*e));
                double landed=clamp01((v.age()-Math.min(v.life()*.4,5)*.8)/Math.max(2,v.life()*.5));
                if(landed>0)c.sheet("ink",false,E.add(0,.2,0),.85,0,(int)Math.min(7,landed*8),INK,(float)(.95*e));
                double h=hit(v);
                for(int k=0;k<6;k++){double a=k*1.05,d=.2+(1-h)*.6;c.sprite("blob",false,E.add(Math.cos(a)*d,.15+(1-h)*.2,Math.sin(a)*d),.1,0,INK,(float)(.9*Math.max(.3,h)));}
            }
            case LIQUID_FIELD -> {
                Vec3 p=E.distanceToSqr(O)<4?v.anchor():E;double R=Math.min(1.7,.5+t*.08);
                c.ground("blob",false,p.add(0,.03,0),R,INK,(float)(.9*e));
                for(int k=0;k<2;k++){double ph=frac(t*.03+k*.5);c.ground("ring",false,p.add(0,.05,0),R*(.4+ph*.7),INK_RIM,(float)(.5*e*(1-ph)));}
                for(int k=0;k<6;k++){double a=k*1.047+rnd(k)*.5,rad=R*.55,hgt=.5+rnd(k+3)*.8+.15*Math.sin(t*.2+k);
                    Vec3 base=p.add(Math.cos(a)*rad,0,Math.sin(a)*rad);var pts=new ArrayList<Vec3>();
                    for(int i=0;i<6;i++){double fr=i/5.;pts.add(base.add(Math.sin(fr*3+t*.15+k)*.06,fr*hgt,Math.cos(fr*3+t*.1+k)*.06));}
                    c.ribbon("inkflow",false,pts,x->.2*(1-x),x->.97*e,WHITE,1,0);}
            }
            default -> { // LIQUID_SCOUT: pool, rising figure, ground tendril back to the caster
                double grow=easeOutBack(reveal(v,8));                 // the figure wells up out of the pool
                c.ground("blob",false,E.add(0,.03,0),1.0*grow,INK,(float)(.92*e));
                for(int k=0;k<2;k++){double ph=frac(t*.03+k*.5);c.ground("ring",false,E.add(0,.05,0),.4+ph*.8,INK_RIM,(float)(.5*e*(1-ph)));}
                c.sheet("ink",false,E.add(0,.35,0),.8,0,frame(t%24,24),INK,(float)(.9*e));
                double[] hs={.3,.72,1.1},sz={.55,.46,.32};
                for(int k=0;k<3;k++)c.sprite("blob",false,E.add(Math.sin(t*.12+k)*.05,hs[k]*grow,Math.cos(t*.1+k)*.05),sz[k]*(1+.05*Math.sin(t*.2+k)),0,INK,(float)(.95*e));
                var path=CanonFx.wave(O.add(0,.05,0),E.add(0,.05,0),14,.1,2.5,-t*.2,up);
                c.ribbon("inkflow",false,path,x->.36*(.6+.4*Math.sin(x*Math.PI)),x->.97*e,WHITE,O.distanceTo(E)*.8,-t*.03);
                for(int k=0;k<3;k++){double ph=frac(t*.03+rnd(k));c.sprite("blob",false,E.add((rnd(k+2)-.5)*.5,1.0-ph*1.0,(rnd(k+7)-.5)*.5),.07,0,INK,(float)(.9*e*(1-ph)));}
            }
        }
    }

    // ------------------------------------------------------------------ paper psychokinesis: folded sheets, creases, shreds
    private static double lift(Vec3 axis,Vec3 dir,double angle){return axis.normalize().cross(dir.normalize()).y<0?-angle:angle;}
    private static void paper(Canvas c,View v){
        double e=env(v),t=time(v);Vec3 up=new Vec3(0,1,0);
        switch(v.motif()){
            case PAPER_WING -> {
                Vec3 f=flat(Vec3.directionFromRotation(0,v.yaw())),r=rightOf(f);Vec3 root=v.anchor().add(0,1.25,0).add(f.scale(-.12));
                for(int side=-1;side<=1;side+=2)for(int k=0;k<3;k++){
                    double open=easeOutBack((v.age()-k*1.5)/5);if(open<=0)continue;     // panels unfold one after another
                    double flap=(.55+.25*Math.sin(t*.14+k*.5+side*.3)+k*.18)*open-.25*(1-open);Vec3 dir=r.scale(side);Vec3 hinge=root.add(0,-k*.17,0).add(f.scale(-.08*k));
                    c.fold("paper_fold",hinge,f,dir,.52,.95+k*.28,lift(f,dir,flap),PAPER,(float)(.9*e));
                }
                for(int k=0;k<5;k++){double ph=frac(t*.012+rnd(k));c.sprite("sparkle",true,root.add((rnd(k+3)-.5)*2.4,-ph*1.4,-.3-rnd(k+9)*.5),.1,t*.08+k,WARM,(float)(.5*e*(1-ph)));}
            }
            case PAPER_ARMOR -> {
                for(int k=0;k<6;k++){double a=t*.06+k*Math.PI/3;Vec3 p=v.anchor().add(Math.cos(a)*.95,.5+k*.18+Math.sin(t*.1+k)*.1,Math.sin(a)*.95);
                    Vec3 tang=new Vec3(-Math.sin(a),0,Math.cos(a));c.fold("paper_fold",p,tang,up,.45,.5,lift(tang,up,.5+.5*Math.sin(t*.12+k)),PAPER,(float)(.9*e));}
            }
            case PAPER_PLANE,PAPER_BLADE,PAPER_ARRAY -> {
                // Rhythm: the folded sheet flies from the hand to the target, leaving a trail and shed scraps behind it.
                Vec3 a=v.origin(),b=v.endpoint();double fly=easeOut(reveal(v,5));Vec3 at=a.add(b.subtract(a).scale(fly));
                c.ribbon("streak",false,line(a,at,2),x->.06+.14*x,x->.85*e*(.3+.7*x),PAPER,1,0);
                c.ribbon("streak",true,line(a,at,2),x->.12,x->.14*e,WARM,1,0);
                Vec3 f=forward(v);Vec3 r=rightOf(f);
                for(int k=0;k<11;k++){double fr=rnd(k+4);if(fr>fly)continue;double ph=frac(t*.05+rnd(k));Vec3 base=a.add(b.subtract(a).scale(fr)).add(0,-ph*.6+.1,0).add(r.scale((rnd(k+8)-.5)*.5));
                    c.fold("paper_fold",base,r,f,.16,.22,t*.3+k,PAPER,(float)(.9*e*(1-ph)));}
                c.fold("paper_fold",at.subtract(f.scale(.5)),r,f,.34,.6,Math.toRadians(25)+.2*Math.sin(t*.3)+(1-fly)*.6,PAPER,(float)(.95*e));
                if(fly>=.98)c.sprite("sparkle",true,b,.35+hit(v)*.5,t*.1,WARM,(float)(.7*e));
            }
            default -> { // PAPER_CONSTRUCT: sheets unfold from a standing fold into the flat structure; crease highlights; shreds
                Vec3 p=v.endpoint().distanceToSqr(v.origin())<.2?v.origin():v.endpoint().add(0,.05,0);Vec3 f=forward(v);Vec3 fl=flat(f);Vec3 r=rightOf(fl);
                for(int k=0;k<3;k++){Vec3 hinge=p.add(r.scale((k-1)*.34)).add(fl.scale(-.1));
                    double open=easeOutBack((v.age()-k*1.2)/6);if(open<=0)continue;   // sheets unfold flat one after another
                    c.fold("paper_fold",hinge,r,fl,.32,.85,lift(r,fl,(1-open)*Math.toRadians(80)),PAPER,(float)(.65*e));}
                c.ground("ring2",true,p,.55+.2*frac(t*.03),WARM,(float)(.22*e));
                for(int k=0;k<8;k++){double ph=frac(t*.025+rnd(k));Vec3 q=p.add((rnd(k+3)-.5)*1.4,.2+(1-ph)*1.2,(rnd(k+6)-.5)*1.4);
                    c.fold("paper_fold",q,r,fl,.09,.12,t*.25+k*2,PAPER,(float)(.85*e*Math.sin(ph*Math.PI)));
                    c.sheet("feather_drift",false,q.add(0,.1,0),.09,0,(int)(frac(t*.04+k*.31)*8),PAPER,(float)(.8*e*Math.sin(ph*Math.PI)));}
            }
        }
    }
}

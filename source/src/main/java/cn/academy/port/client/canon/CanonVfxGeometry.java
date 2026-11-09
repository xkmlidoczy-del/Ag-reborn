package cn.academy.port.client.canon;

import cn.academy.port.canon.CanonVfxDesign.Motif;
import java.util.ArrayList;
import java.util.List;

/** Pure bounded local-space geometry. x=right, y=up, z=forward; no game access. */
public final class CanonVfxGeometry {
    public record Point(double x,double y,double z) {}
    private CanonVfxGeometry() {}
    /** Skill action markers distinguish sensed locations, transfers, lift and descent. */
    public static List<Point> skillStage(String skill,Motif motif,int phase,double age,double length,int duration){
        var base=stage(motif,phase,age,length,duration);
        if(skill==null||base.isEmpty())return base;
        int kind=action(skill);if(kind==0)return base;
        var out=new ArrayList<Point>(96);out.addAll(base.subList(0,Math.min(84,base.size())));
        double z=Math.max(1,Math.min(32,length));
        if(kind==1){for(int i=0;i<12;i++){double a=i*Math.PI/6;out.add(new Point(.25*Math.cos(a),.25*Math.sin(a),z));}}
        else if(kind==2){line(out,-.2,.5,z-.4,0,.5,z,6);line(out,0,.5,z,.2,.5,z-.4,6);}
        else if(kind==3||kind==4){double sign=kind==3?1:-1;line(out,-.25,.7-sign*.3,z,0,.7,z,6);line(out,0,.7,z,.25,.7-sign*.3,z,6);}
        else if(kind==5){line(out,-.5,.5,z,-.1,.5,z,6);line(out,.5,.5,z,.1,.5,z,6);}
        else if(kind==6){line(out,-.5,.5,z,.5,.5,z,8);line(out,.5,.5,z,.25,.7,z,4);}
        else {line(out,0,.5,z,0,.5,z-.7,8);line(out,0,.5,z-.7,.2,.5,z-.5,4);}
        return List.copyOf(out);
    }
    private static int action(String skill){
        if(has(skill,"fall_brake","wing_rescue","nitrogen_buoy","rescue","lower","drop"))return 4;
        if(has(skill,"side_step","crosswind"))return 6;
        if(has(skill,"backstep","retreat"))return 7;
        if(skill.endsWith("brake"))return 5;
        if(has(skill,"scan","sense","probe","listen","survey","filter","select"))return 1;
        if(has(skill,"transfer","launch","route","strike","whip","blade","push","pull","path"))return 2;
        if(has(skill,"lift","jump","updraft","buoy"))return 3;
        if(has(skill,"retract","release"))return 4;
        return 0;
    }
    private static boolean has(String skill,String... terms){for(String term:terms)if(skill.contains(term))return true;return false;}
    /** Explicit phases: growing initiation, sustained form, bounded impact, and contraction. */
    public static List<Point> stage(Motif motif,int phase,double age,double length,int duration){
        if(phase<0||phase>3||duration<1||!Double.isFinite(age)||age<0)return List.of();
        var base=sample(motif,age,length);if(base.isEmpty())return base;
        double fraction=Math.max(0,Math.min(1,age/duration));
        double scale=phase==0?Math.min(1,.1+age*.04):phase==3?Math.max(.12,1-fraction):1;
        var out=new ArrayList<Point>(96);
        for(var p:base){if(phase==2&&out.size()>=84)break;out.add(new Point(p.x*scale,p.y*scale,p.z*scale));}
        if(phase==2){double z=Math.max(1,Math.min(32,length));for(int i=0;i<12&&out.size()<96;i++){
            double a=i*Math.PI/6,r=.15+fraction*.55;out.add(new Point(Math.cos(a)*r,Math.sin(a)*r,z));}}
        return List.copyOf(out);
    }
    public static List<Point> sample(Motif motif,double time,double length) {
        if(motif==null||!Double.isFinite(time)||!Double.isFinite(length))return List.of();
        length=Math.max(1,Math.min(32,length)); var p=new ArrayList<Point>(96); double t=time*.14;
        switch(motif) {
            case REMOTE -> {
                rect(p,-.38,.38,.15,.95,.65); for(int i=0;i<4;i++)ring(p,(i-1.5)*.16,.58,.66,.05,0,8);
                line(p,0,.55,.7,0,.55,length,12);
            }
            case DISRUPT -> { for(int i=0;i<36;i++){double a=i*.7+t;p.add(new Point(Math.cos(a)*(.2+i*.007),.65+Math.sin(a)*(.2+i*.007),length*i/36));} }
            case CROWD -> { for(int k=0;k<3;k++)ring(p,0,.7,(k+1)*length/4,.35+k*.25,t+k,16); }
            case DARK_WINGS -> { for(int side:new int[]{-1,1})for(int k=0;k<3;k++){double a=.55+k*.29;
                line(p,side*.12,.65,0,side*(1.1+k*.35),.9+k*.28,-a,9);
                line(p,side*(1.1+k*.35),.9+k*.28,-a,side*(.4+k*.35),.2+k*.23,-a*1.7,7); } }
            case DARK_BLADE -> {line(p,-.08,.55,.2,.08,.7,length,24);line(p,.08,.7,length,-.14,.4,length*.72,12);}
            case DARK_CONSTRUCT -> {for(int i=0;i<3;i++){double a=t+i*Math.PI*2/3;double x=Math.cos(a)*1.2,z=Math.sin(a)*1.2;
                line(p,x,.35,z,x+.2,.95,z+.12,8);line(p,x+.2,.95,z+.12,x-.15,.72,z-.18,8);}}
            case NITROGEN_SHELL -> {for(int k=0;k<3;k++)ring(p,0,.25+k*.43,0,.6+k*.05,t+k*.35,16);line(p,-.6,.3,0,.6,1.4,0,8);}
            case NITROGEN_PUNCH -> {for(int k=0;k<4;k++)ring(p,0,.65,length*(k+1)/5,.55-k*.09,t,12);}
            case NITROGEN_DOME -> {for(int i=0;i<4;i++)ring(p,0,.15+i*.35,0,1.4*Math.sqrt(Math.max(0,1-i*i/16.0)),t*.3,16);}
            case AERO_JET -> {for(int i=0;i<36;i++){double f=i/35.;double a=i*1.4+t;p.add(new Point(Math.cos(a)*(.15+f*.5),Math.sin(a)*(.15+f*.5),-f*2));}}
            case AERO_LAUNCH -> {for(int k=0;k<3;k++)line(p,(k-1)*.16,.55+k*.08,0,(k-1)*.45,.55+k*.14,length,20);}
            case AERO_BARRAGE -> {for(int k=0;k<5;k++)line(p,0,.65,0,(k-2)*.42,.4+Math.abs(k-2)*.16,length,12);}
            case WATER_WHIP -> {for(int i=0;i<60;i++){double f=i/59.;p.add(new Point(.18*Math.sin(f*12-t),.7+.16*Math.cos(f*10-t),f*length));}}
            case WATER_SHIELD -> {for(int i=0;i<48;i++){double a=Math.PI*(i/47.);p.add(new Point(Math.cos(a)*1.1,.15+Math.sin(a)*1.65,.8));}}
            case WATER_DOMAIN -> {for(int i=0;i<3;i++)ring(p,0,.12,0,.55+i*.45,t+i,20);}
            case WATER_TRANSFER -> {for(int k=0;k<3;k++)for(int i=0;i<20;i++){double f=i/19.;
                p.add(new Point((k-1)*.13,.3+Math.sin(f*Math.PI)*.65+k*.11,f*length));}}
            case FLOAT_LIFT -> {for(int k=0;k<3;k++)line(p,(k-1)*.35,.05,0,(k-1)*.35,2.5,0,18);}
            case FLOAT_RESCUE -> {for(int i=0;i<48;i++){double a=i*.45+t;p.add(new Point(Math.cos(a)*.65,.1+i*.045,Math.sin(a)*.65));}}
            case FLOAT_FIELD -> {for(int i=0;i<4;i++){double a=i*Math.PI/2;line(p,Math.cos(a)*1.2,.1,Math.sin(a)*1.2,Math.cos(a)*1.2,2.1,Math.sin(a)*1.2,13);}}
            case LIQUID_SCOUT -> {for(int i=0;i<50;i++){double a=i*Math.PI*2/50;p.add(new Point(Math.cos(a)*(1+.12*Math.sin(4*a+t)),.08,Math.sin(a)*.55));}}
            case LIQUID_STRIKE -> {for(int k=0;k<3;k++)for(int i=0;i<16;i++){double f=i/15.;p.add(new Point((k-1)*.2+.1*Math.sin(f*10+t),.35+f*.3,f*length));}}
            case LIQUID_FIELD -> {for(int k=0;k<3;k++)for(int i=0;i<18;i++){double a=i*Math.PI*2/18;p.add(new Point(Math.cos(a)*(k*.4+.4),.06+.07*Math.sin(a*5+t),Math.sin(a)*(k*.4+.4)));}}
            case PAPER_BLADE -> {line(p,-.25,.65,0,.25,.65,length,24);line(p,.25,.65,length,-.25,.85,length*.6,12);}
            case PAPER_ARMOR -> {for(int i=0;i<3;i++){double y=.2+i*.45;rect(p,-.55,.55,y,y+.3,-.1);}}
            case PAPER_WING -> {for(int side:new int[]{-1,1})for(int i=0;i<3;i++){double x=side*(.35+i*.45);line(p,side*.1,.7,0,x,1.15-i*.15,-.7-i*.2,10);line(p,x,1.15-i*.15,-.7-i*.2,side*.25,.35,-.8-i*.2,7);}}
            case PAPER_ARRAY -> {for(int i=0;i<5;i++){double a=i*Math.PI*2/5+t*.2;double x=Math.cos(a)*1.2,y=.7+Math.sin(a)*.8;line(p,0,.7,0,x,y,length*.7,11);}}
            case PAPER_CONSTRUCT -> {rect(p,-.5,.5,.1,1.1,.4);rect(p,-.5,.5,.1,1.1,1.4);
                for(int x:new int[]{-1,1})for(int y:new int[]{0,1})line(p,x*.5,.1+y,.4,x*.5,.1+y,1.4,7);}
            case PAPER_PLANE -> {line(p,0,.3,length,-.5,.25,length-.7,15);line(p,0,.3,length,.5,.25,length-.7,15);
                line(p,-.5,.25,length-.7,0,.12,length-.5,12);line(p,0,.12,length-.5,.5,.25,length-.7,12);}
        }
        return List.copyOf(p.subList(0,Math.min(96,p.size())));
    }
    private static void line(List<Point> p,double x,double y,double z,double a,double b,double c,int n){for(int i=0;i<n;i++){double f=i/(double)(n-1);p.add(new Point(x+(a-x)*f,y+(b-y)*f,z+(c-z)*f));}}
    private static void ring(List<Point> p,double x,double y,double z,double r,double phase,int n){for(int i=0;i<n;i++){double a=phase+i*Math.PI*2/n;p.add(new Point(x+Math.cos(a)*r,y+Math.sin(a)*r,z));}}
    private static void rect(List<Point> p,double x1,double x2,double y1,double y2,double z){line(p,x1,y1,z,x2,y1,z,6);line(p,x2,y1,z,x2,y2,z,6);line(p,x2,y2,z,x1,y2,z,6);line(p,x1,y2,z,x1,y1,z,6);}
}

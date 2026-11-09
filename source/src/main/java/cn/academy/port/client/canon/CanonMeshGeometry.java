package cn.academy.port.client.canon;

import cn.academy.port.canon.CanonVfxDesign.Motif;
import java.util.ArrayList;
import java.util.List;

/** Bounded local-space transparent planes. Coordinates are relative to the actual owner, never gameplay colliders. */
public final class CanonMeshGeometry {
    public record Point(double x,double y,double z) {}
    public record Quad(Point a,Point b,Point c,Point d,int red,int green,int blue,int alpha) {}
    private CanonMeshGeometry() {}
    /** Real paper structures use their own server-reported first/last block positions. */
    public static List<Quad> shapeFor(String skill,Motif motif,int phase,double age,int duration,double length){
        if(skill!=null&&skill.startsWith("liquid_")&&phase>=0&&phase<=3&&Double.isFinite(age)&&age>=0&&duration>0&&Double.isFinite(length)){
            var contact=liquidContact(skill,Math.max(.3,Math.min(8,length)),age);
            if(!contact.isEmpty())return faded(contact,phase,age,duration);
        }
        if(skill==null||motif!=Motif.PAPER_CONSTRUCT||phase<0||phase>3||!Double.isFinite(age)||age<0
                ||duration<1||!Double.isFinite(length))return shape(motif,phase,age,duration,length);
        double z=Math.max(1,Math.min(8,length));var out=new ArrayList<Quad>();
        switch(skill){
            case "paper_ramp","paper_lift_bridge" -> {
                for(int i=0;i<3;i++){double a=i*z/3,b=(i+1)*z/3,ya=i*.3,yb=(i+1)*.3;
                    q(out,p(-.48,ya,a),p(.48,ya,a),p(.48,yb,b),p(-.48,yb,b),255,248,230,135);}
                if(skill.equals("paper_lift_bridge"))q(out,p(-.48,.9,z),p(.48,.9,z),p(.48,.9,z+.5),p(-.48,.9,z+.5),248,240,222,160);
            }
            case "paper_cover" -> q(out,p(-.55,0,.2),p(.55,0,.2),p(.55,1.6,.2),p(-.55,1.6,.2),255,249,234,145);
            case "paper_platform" -> {for(int x=-1;x<=0;x++)for(int k=0;k<2;k++)
                q(out,p(x,.03,k),p(x+1,.03,k),p(x+1,.03,k+1),p(x,.03,k+1),250,244,226,130);}
            case "paper_catwalk" -> {q(out,p(-.42,.03,0),p(.42,.03,0),p(.42,.03,z),p(-.42,.03,z),255,250,235,140);
                for(int side:new int[]{-1,1})q(out,p(side*.42,.04,0),p(side*.48,.04,0),p(side*.48,.04,z),p(side*.42,.04,z),255,255,255,200);}
            case "paper_emergency_stairs" -> {for(int i=0;i<4;i++){double a=i*z/4,b=(i+1)*z/4,y=i*.24;
                q(out,p(-.45,y,a),p(.45,y,a),p(.45,y,b),p(-.45,y,b),255,247,230,145);}}
            case "paper_puzzle_link" -> {for(int i=0;i<3;i++){double t=i*z/2;
                q(out,p(-.24,.04,t-.24),p(.24,.04,t-.24),p(.24,.04,t+.24),p(-.24,.04,t+.24),255,248,230,175);}
                q(out,p(-.05,.05,0),p(.05,.05,0),p(.05,.05,z),p(-.05,.05,z),255,255,255,190);}
            case "paper_switch_bridge" -> {q(out,p(-.45,.03,0),p(.45,.03,0),p(.45,.03,z),p(-.45,.03,z),255,246,226,135);
                for(double t:new double[]{0,z})q(out,p(-.5,.05,t),p(.5,.05,t),p(.5,.75,t),p(-.5,.75,t),250,241,222,125);}
            default -> {return shape(motif,phase,age,duration,length);}
        }
        return faded(out,phase,age,duration);
    }
    private static List<Quad> faded(List<Quad> values,int phase,double age,int duration){
        double fade=phase==0?Math.min(1,.2+age*.12):phase==3?Math.max(.08,1-age/duration):1;
        if(fade>=1)return List.copyOf(values);
        return values.stream().map(v->new Quad(v.a,v.b,v.c,v.d,v.red,v.green,v.blue,Math.max(1,(int)(v.alpha*fade)))).toList();
    }
    private static List<Quad> liquidContact(String skill,double z,double age){
        var out=new ArrayList<Quad>();
        switch(skill){
            case "liquid_grapple","liquid_contact_crush" -> {
                int rings=skill.equals("liquid_contact_crush")?3:2;
                for(int k=0;k<rings;k++)for(int i=0;i<8;i++){
                    double a=i*Math.PI/4+age*.04,b=(i+1)*Math.PI/4+age*.04,r=.38+k*.04,y=.2+k*.38;
                    q(out,p(Math.cos(a)*r,y,z+Math.sin(a)*r),p(Math.cos(b)*r,y,z+Math.sin(b)*r),
                            p(Math.cos(b)*r,y+.12,z+Math.sin(b)*r),p(Math.cos(a)*r,y+.12,z+Math.sin(a)*r),65,53,82,175);
                }
            }
            case "liquid_contact_cut" -> q(out,p(-.12,.3,0),p(.12,.35,0),p(.25,1.2,z),p(-.15,.9,z),67,54,83,180);
            case "liquid_contact_sweep" -> {for(int i=0;i<6;i++){
                double a=-Math.PI*.45+i*Math.PI*.9/6,b=-Math.PI*.45+(i+1)*Math.PI*.9/6;
                q(out,p(0,.08,0),p(Math.sin(a)*z,.08,Math.cos(a)*z),p(Math.sin(b)*z,.08,Math.cos(b)*z),p(0,.08,0),57,47,74,145);
            }}
            default -> { }
        }
        return List.copyOf(out);
    }
    public static List<Quad> shape(Motif motif,int phase,double age,int duration,double length){
        if(motif==null||phase<0||phase>3||!Double.isFinite(age)||age<0||duration<1||!Double.isFinite(length))return List.of();
        var out=new ArrayList<Quad>();double u=Math.min(1,Math.max(0,age/duration));
        double scale=phase==0?Math.min(1,.18+age*.12):phase==3?Math.max(.08,1-u):1;
        if(motif==Motif.DARK_WINGS) wings(out,scale);
        else if(motif==Motif.NITROGEN_SHELL) shell(out,scale);
        else if(motif==Motif.NITROGEN_PUNCH) punch(out,scale,Math.max(.5,Math.min(4,length)));
        else switch(motif){
            case REMOTE,DISRUPT,CROWD -> mental(out,motif,scale,Math.max(.6,Math.min(12,length)));
            case AERO_JET,AERO_LAUNCH,AERO_BARRAGE -> aero(out,motif,scale,Math.max(1,Math.min(12,length)),age);
            case WATER_WHIP,WATER_TRANSFER,WATER_SHIELD,WATER_DOMAIN -> water(out,motif,scale,Math.max(1,Math.min(12,length)),age);
            case FLOAT_LIFT,FLOAT_RESCUE,FLOAT_FIELD -> buoyancy(out,motif,scale,age);
            case LIQUID_SCOUT,LIQUID_STRIKE,LIQUID_FIELD -> liquid(out,motif,scale,Math.max(1,Math.min(8,length)),age);
            case PAPER_BLADE,PAPER_ARMOR,PAPER_WING,PAPER_ARRAY,PAPER_CONSTRUCT,PAPER_PLANE -> paper(out,motif,scale,Math.max(1,Math.min(8,length)));
            case DARK_BLADE,DARK_CONSTRUCT,NITROGEN_DOME -> { }
            default -> { }
        }
        return List.copyOf(out);
    }
    private static void mental(List<Quad> out,Motif motif,double s,double length){
        if(motif==Motif.REMOTE){
            q(out,p(.14,-.60,.65),p(.76,-.60,.65),p(.76,-.20,.65),p(.14,-.20,.65),255,224,111,90);
            for(int i=0;i<4;i++){double x=.20+i*.14;q(out,p(x,-.48,.64),p(x+.08,-.48,.64),p(x+.08,-.40,.64),p(x,-.40,.64),255,244,185,200);}
        }else for(int i=0;i<8;i++){
            double a=i*Math.PI/4,b=(i+1)*Math.PI/4,r=(motif==Motif.CROWD?.65:.4)*s;
            q(out,p(Math.cos(a)*r,Math.sin(a)*r,length),p(Math.cos(b)*r,Math.sin(b)*r,length),
                    p(Math.cos(b)*(r+.06),Math.sin(b)*(r+.06),length),p(Math.cos(a)*(r+.06),Math.sin(a)*(r+.06),length),255,221,122,140);
        }
    }
    private static void aero(List<Quad> out,Motif motif,double s,double length,double age){
        double dir=motif==Motif.AERO_JET?-1:1;int rings=motif==Motif.AERO_BARRAGE?6:4;
        for(int k=0;k<rings;k++){double z=dir*(k+1)*length/(rings+1),r=(.15+k*.10)*s;
            for(int i=0;i<4;i++){double a=age*.12+i*Math.PI/2+k*.2,b=a+.55;
                q(out,p(Math.cos(a)*r,Math.sin(a)*r,z),p(Math.cos(b)*r,Math.sin(b)*r,z),
                        p(Math.cos(b)*(r+.09),Math.sin(b)*(r+.09),z+.12*dir),p(Math.cos(a)*(r+.09),Math.sin(a)*(r+.09),z+.12*dir),205,244,240,90);}}
    }
    private static void water(List<Quad> out,Motif motif,double s,double length,double age){
        if(motif==Motif.WATER_WHIP||motif==Motif.WATER_TRANSFER){
            for(int i=0;i<12;i++){double a=i/12.,b=(i+1)/12.,x1=.12*Math.sin(a*9-age*.15),x2=.12*Math.sin(b*9-age*.15),w=.07*s;
                q(out,p(x1-w,.08,a*length),p(x1+w,.08,a*length),p(x2+w,.08,b*length),p(x2-w,.08,b*length),78,180,255,115);}
        }else if(motif==Motif.WATER_SHIELD){
            for(int i=0;i<6;i++){double a=Math.PI*i/6,b=Math.PI*(i+1)/6;
                q(out,p(Math.cos(a)*s,0,.7),p(Math.cos(b)*s,0,.7),p(Math.cos(b)*s,1.6,.7),p(Math.cos(a)*s,1.6,.7),73,173,250,75);}
        }else groundRing(out,1.1*s,.04,77,168,245,105);
    }
    private static void buoyancy(List<Quad> out,Motif motif,double s,double age){
        int count=motif==Motif.FLOAT_FIELD?4:3;double radius=motif==Motif.FLOAT_FIELD?1.0:.5;
        for(int i=0;i<count;i++){double a=i*Math.PI*2/count+age*.04,x=Math.cos(a)*radius*s,z=Math.sin(a)*radius*s;
            q(out,p(x-.035,0,z),p(x+.035,0,z),p(x+.035,2.2*s,z),p(x-.035,2.2*s,z),160,247,247,125);}
    }
    private static void liquid(List<Quad> out,Motif motif,double s,double length,double age){
        if(motif==Motif.LIQUID_STRIKE){for(int i=0;i<8;i++){double a=i/8.,b=(i+1)/8.,x=.1*Math.sin(a*8+age*.1);
            q(out,p(x-.12,0,a*length),p(x+.12,0,a*length),p(x+.12,.05,b*length),p(x-.12,.05,b*length),49,41,66,170);}}
        else{double r=(motif==Motif.LIQUID_FIELD?1.3:.65)*s;for(int i=0;i<8;i++){
            double a=i*Math.PI/4,b=(i+1)*Math.PI/4;
            q(out,p(0,.04,0),p(Math.cos(a)*r,.04,Math.sin(a)*r*.65),p(Math.cos(b)*r,.04,Math.sin(b)*r*.65),p(0,.04,0),48,39,62,180);}
            groundRing(out,r,.06,128,112,154,125);}
    }
    private static void paper(List<Quad> out,Motif motif,double s,double length){
        if(motif==Motif.PAPER_BLADE||motif==Motif.PAPER_ARRAY){
            int count=motif==Motif.PAPER_ARRAY?3:1;for(int i=0;i<count;i++){double x=(i-(count-1)/2.)*.16;
                q(out,p(x-.09,0,0),p(x+.09,0,0),p(x+.16,.08,length),p(x-.16,.08,length),255,247,225,170);}}
        else if(motif==Motif.PAPER_WING){for(int side:new int[]{-1,1})for(int i=0;i<3;i++){
            double x=side*(.7+i*.35)*s;q(out,p(side*.1,1.2,0),p(x,1.6-i*.15,-.25-i*.2),p(x,.75-i*.05,-.8-i*.2),p(side*.2,.8,-.2),255,250,233,155);}}
        else if(motif==Motif.PAPER_CONSTRUCT){
            q(out,p(-.5,0,-.5),p(.5,0,-.5),p(.5,0,.5),p(-.5,0,.5),255,245,225,90);
            q(out,p(-.5,.03,-.5),p(.5,.03,.5),p(.44,.03,.5),p(-.5,.03,-.44),255,255,255,200);
        }else if(motif==Motif.PAPER_PLANE){
            q(out,p(0,.3,length),p(-.55,.25,length-.7),p(0,.12,length-.5),p(0,.3,length),255,251,236,180);
            q(out,p(0,.3,length),p(0,.12,length-.5),p(.55,.25,length-.7),p(0,.3,length),240,236,221,165);
        }else q(out,p(-.65,.2,.7),p(.65,.2,.7),p(.65,1.45,.7),p(-.65,1.45,.7),255,248,229,100);
    }
    private static void groundRing(List<Quad> out,double r,double y,int red,int green,int blue,int alpha){
        for(int i=0;i<12;i++){double a=i*Math.PI/6,b=(i+1)*Math.PI/6;
            q(out,p(Math.cos(a)*r,y,Math.sin(a)*r),p(Math.cos(b)*r,y,Math.sin(b)*r),p(Math.cos(b)*(r+.07),y,Math.sin(b)*(r+.07)),p(Math.cos(a)*(r+.07),y,Math.sin(a)*(r+.07)),red,green,blue,alpha);}
    }
    private static void wings(List<Quad> out,double s){
        // Six deliberately non-identical tapered panels, each with a narrow branching facet.
        for(int side:new int[]{-1,1})for(int k=0;k<3;k++){
            double asym=side<0?.92:1.08, x=side*(1.35+k*.19)*asym*s;
            double tipY=1.54+k*.22-(side<0?.12:0), z=-.3-k*.22;
            Point root=p(side*.16,1.32,-.12),joint=p(side*(.47+k*.13)*s,1.10+k*.15,-.29);
            Point tip=p(x,1.3+(tipY-1.3)*s,z*s),trailing=p(side*(.91+k*.17)*asym*s,.78+k*.11,z*s-.33*s);
            q(out,root,tip,trailing,joint,232,247,255,165);
            Point branch=p(side*(1.15+k*.18)*asym*s,.52+k*.13,z*s-.59*s);
            Point neck=p(side*(.70+k*.13)*s,.97+k*.12,z*s-.15*s);
            q(out,neck,trailing,branch,p(side*(.55+k*.11)*s,.68+k*.12,z*s-.20*s),210,234,255,115);
            q(out,root,p(side*.20,1.38,-.12),tip,p(x-side*.045,1.3+(tipY-1.3)*s-.05,z*s),255,255,255,215);
        }
    }
    private static void shell(List<Quad> out,double s){
        double r=.44*s;int n=8;
        for(int i=0;i<n;i++){
            double a=i*Math.PI*2/n,b=(i+1)*Math.PI*2/n;
            double ax=Math.cos(a)*r,az=Math.sin(a)*r,bx=Math.cos(b)*r,bz=Math.sin(b)*r;
            q(out,p(ax,.1,az),p(bx,.1,bz),p(bx,1.72,bz),p(ax,1.72,az),170,225,255,62);
            q(out,p(ax,.57,az),p(bx,.57,bz),p(bx,.64,bz),p(ax,.64,az),230,250,255,135);
            q(out,p(ax,1.35,az),p(bx,1.35,bz),p(bx,1.42,bz),p(ax,1.42,az),225,248,255,125);
            q(out,p(ax,1.72,az),p(bx,1.72,bz),p(0,1.91,0),p(0,1.91,0),205,240,255,65);
        }
    }
    private static void punch(List<Quad> out,double s,double length){
        for(int i=0;i<6;i++){double a=i*Math.PI/3,b=(i+1)*Math.PI/3;double r1=.17*s,r2=.42*s;
            q(out,p(Math.cos(a)*r1,1.18+Math.sin(a)*r1,.15),p(Math.cos(b)*r1,1.18+Math.sin(b)*r1,.15),
                    p(Math.cos(b)*r2,1.18+Math.sin(b)*r2,length),p(Math.cos(a)*r2,1.18+Math.sin(a)*r2,length),190,238,255,90);}
    }
    private static Point p(double x,double y,double z){return new Point(x,y,z);}
    private static void q(List<Quad> out,Point a,Point b,Point c,Point d,int r,int g,int bl,int alpha){out.add(new Quad(a,b,c,d,r,g,bl,alpha));}
}

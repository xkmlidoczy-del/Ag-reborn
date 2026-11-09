/* AcademyCraft 1.0.7 SkillTree.scala constants and timing. GPLv3; see NOTICE. */
package cn.academy.port.client;

/** Pure source-layout/timing arithmetic, independently testable without Minecraft or OpenGL. */
public final class ClassicDeveloperTimeline {
    public static final double MAIN_WIDTH=400,MAIN_HEIGHT=187,LEFT_X=4,LEFT_WIDTH=108.5,
            RIGHT_X=118,RIGHT_WIDTH=278,AREA_X=128,AREA_Y=18,AREA_WIDTH=257,AREA_HEIGHT=139,
            HIT_SIZE=16,BACK_SIZE=23,OUTLINE_SIZE=31,ICON_SIZE=14,LINK_WIDTH=5.5,LINK_INSET=12.2;
    public record Placement(double x,double y,double scale){
        public double localX(double x){return (x-this.x)/scale;}
        public double localY(double y){return (y-this.y)/scale;}
    }
    public record Reveal(double background,double icon,double radial,double line){}
    public record Edge(double x0,double y0,double x1,double y1,double nx,double ny){}
    public static Placement placement(int width,int height){
        // Only narrow modern GUI-scaled windows need this explicit fit adaptation.
        double scale=Math.min(1,Math.min(Math.max(1,width-12)/MAIN_WIDTH,Math.max(1,height-12)/MAIN_HEIGHT));
        return new Placement((width-MAIN_WIDTH*scale)/2,(height-MAIN_HEIGHT*scale)/2,scale);
    }
    public static double clamp(double value){return !Double.isFinite(value)?0:Math.max(0,Math.min(1,value));}
    /** SkillTree.scala multiplies AbilityData's Float getter by100 before truncating.
     * The common port stores Double; retain source display arithmetic at this UI boundary. */
    public static int experiencePercent(double value){return (int)((float)clamp(value)*100f);}
    public static double parentAlpha(boolean learned,boolean parentLearned){return learned?1:parentLearned?.7:.25;}
    public static Reveal reveal(long elapsed,int index){
        double dt=Math.max(0,(elapsed-(index*80+100))/1000d);
        return new Reveal(clamp(dt*10),clamp((dt-.08)*10),clamp((dt-.12)*2),clamp(dt*5));
    }
    public static double hoverScale(boolean hovering,long elapsed){double p=clamp(elapsed/100d);return hovering?1+.2*p:1.2-.2*p;}
    public static double cover(long elapsed,boolean ending){double p=clamp(elapsed/200d);return (ending?1-p:p)*.7;}
    public static double parallax(double mouse,double screen){return (clamp(mouse/Math.max(1,screen))-.5)*10;}
    public static boolean hit(double x,double y,double left,double top,double w,double h){return x>=left&&y>=top&&x<left+w&&y<top+h;}
    public static Edge edge(double x,double y,double parentX,double parentY,double reveal){
        double dx=parentX-x,dy=parentY-y,norm=Math.hypot(dx,dy);if(norm==0)return new Edge(x+8,y+8,x+8,y+8,0,0);
        double ux=dx/norm,uy=dy/norm;
        // Original texture begins at the parent end and grows toward the child.
        double startX=parentX+8-ux*LINK_INSET,startY=parentY+8-uy*LINK_INSET;
        double endX=x+8+ux*LINK_INSET,endY=y+8+uy*LINK_INSET,p=clamp(reveal);
        return new Edge(startX,startY,startX+(endX-startX)*p,startY+(endY-startY)*p,uy*LINK_WIDTH/2,-ux*LINK_WIDTH/2);
    }
    private ClassicDeveloperTimeline(){}
}

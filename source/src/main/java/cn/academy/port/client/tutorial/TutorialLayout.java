/* AcademyCraft 1.0.7 GuiTutorial/tutorial.xml geometry and animation adaptation. GPLv3. */
package cn.academy.port.client.tutorial;

/** All coordinates are source-local, before GuiTutorial's width / 480 scale. */
public final class TutorialLayout {
    public static final double REF_WIDTH=480, FRAME_WIDTH=427, FRAME_HEIGHT=240;
    public static final Rect LEFT=new Rect(7,9.75,85,220.5), LIST=new Rect(13.6,16.75,72,207);
    public static final Rect CONTENT=new Rect(94,14.75,160,210.5), RIGHT=new Rect(265.5,148.25,158.5,82);
    public static final Rect PREVIEW=new Rect(277.75,8.75,134,134), TAGS=new Rect(277.5,130.5,133,18);
    public static final Rect PREVIOUS=new Rect(270.5,51.5,12,52), NEXT=new Rect(405.5,51.5,12,52);
    public static final Rect TRACK=new Rect(254.5,11.75,9.5,216.5);
    public static final double SCROLL_TRAVEL=163, SCROLL_HEIGHT=53, ROW_HEIGHT=12;
    private TutorialLayout(){}
    public record Rect(double x,double y,double width,double height){
        public boolean contains(double mx,double my){return mx>=x&&my>=y&&mx<x+width&&my<y+height;}
    }
    public record Placement(double x,double y,double scale){
        public double localX(double x){return (x-this.x)/scale;}
        public double localY(double y){return (y-this.y)/scale;}
    }
    public static Placement placement(int width,int height){double s=Math.max(1,width)/REF_WIDTH;return new Placement((width-FRAME_WIDTH*s)/2,(height-FRAME_HEIGHT*s)/2,s);}
    public static double clamp(double p){return Double.isFinite(p)?Math.max(0,Math.min(1,p)):0;}
    public static double blend(double elapsed,double start,double duration){return clamp((elapsed-start)/duration);}
    public static boolean listVisible(boolean first,double elapsed){return !first||elapsed>2.4;}
    public static double scrollRange(double markdownHeight){return Math.max(0,markdownHeight-CONTENT.height()+10);}
    public static double logo3Y(double elapsed){return 164.375-99*blend(elapsed,.7,.4);}
    /** Source lineglow's two stages, in logo1's quarter-scale local space. */
    public static double[] glowSegments(boolean first,double elapsed){
        if(!first)return new double[]{200,500};
        double dt=Math.max(0,elapsed-.4);
        if(dt<.3){double end=500*dt/.3;return new double[]{50,Math.max(50,end)};}
        return new double[]{500-(400-100*clamp((dt-.3)/.2)),500};
    }
}

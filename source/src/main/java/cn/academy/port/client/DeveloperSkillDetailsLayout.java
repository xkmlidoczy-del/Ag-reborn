package cn.academy.port.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Skill-detail geometry only. Learning eligibility, prices and requests remain in their existing server-owned paths. */
public final class DeveloperSkillDetailsLayout {
    public static final double WIDTH=420, LINE_HEIGHT=12, TEXT_SIZE=10, CONDITION_TOP=184, CONDITION_STEP=14;
    public record Rect(double x,double y,double width,double height) {
        public boolean contains(double mx,double my){return ClassicDeveloperTimeline.hit(mx,my,x,y,width,height);}
        public boolean within(Rect outer){return x>=outer.x&&y>=outer.y&&x+width<=outer.x+outer.width&&y+height<=outer.y+outer.height;}
        public boolean intersects(Rect other){return x<other.x+other.width&&x+width>other.x&&y<other.y+other.height&&y+height>other.y;}
    }
    public static final Rect DESCRIPTION=new Rect(14,64,382,96);
    public record Placement(double x,double y,double scale,int conditions) {
        public double localX(double screenX){return (screenX-x)/scale;}
        public double localY(double screenY){return (screenY-y)/scale;}
        public double screenX(double localX){return x+localX*scale;}
        public double screenY(double localY){return y+localY*scale;}
        public Rect panel(){return new Rect(0,0,WIDTH,confirm().y()+32);}
        public double costY(){return CONDITION_TOP+Math.max(1,conditions)*CONDITION_STEP+4;}
        public Rect status(){return new Rect(14,costY()+16,392,32);}
        public Rect confirm(){return new Rect(346,status().y()+40,60,18);}
        public Rect back(){return new Rect(14,confirm().y(),60,18);}
    }
    public static Placement placement(int width,int height,int conditionCount){
        int count=Math.max(0,conditionCount);
        var logical=new Placement(0,0,1,count);double panelHeight=logical.panel().height();
        double scale=Math.min(1,Math.min(Math.max(1,width-16)/WIDTH,Math.max(1,height-16)/panelHeight));
        return new Placement((width-WIDTH*scale)/2,(height-panelHeight*scale)/2,scale,count);
    }
    /** One shared presentation/interaction fence: terminal skill-tree apps are always read-only. */
    public static boolean canConfirm(boolean readOnlyApp,boolean learned,boolean requested,boolean complete,boolean disposed,boolean implemented){
        return !readOnlyApp&&!learned&&!requested&&!complete&&!disposed&&implemented;
    }
    public static boolean showLearningCost(boolean readOnlyApp,boolean learned){return !readOnlyApp&&!learned;}
    /** Generic training passives have no mastery clock; new skills with no awarded mastery must not advertise a fake zero meter. */
    public static boolean showMastery(boolean generic,boolean extension,double experience){
        return !generic&&(!extension||Double.isFinite(experience)&&experience>0);
    }
    public static double maxScroll(int lines){return Math.max(0,Math.max(0,lines)*LINE_HEIGHT-DESCRIPTION.height());}
    public static double clampScroll(int lines,double value){return Double.isFinite(value)?Math.max(0,Math.min(maxScroll(lines),value)):0;}

    /** Measured whole-word wrapping with a code-point fallback for long words; explicit paragraph breaks are preserved. */
    public static List<String> wrap(String text,double limit,ToDoubleFunction<String> width){
        if(text==null||text.isEmpty())return List.of();
        if(!Double.isFinite(limit)||limit<=0)throw new IllegalArgumentException("Positive detail text width required");
        var lines=new ArrayList<String>();
        for(String paragraph:text.replace("\\n","\n").split("\n",-1)){
            if(paragraph.isEmpty()){lines.add("");continue;}
            int start=0;
            while(start<paragraph.length()){
                int end=start,space=-1;
                while(end<paragraph.length()){
                    int next=end+Character.charCount(paragraph.codePointAt(end));
                    if(width.applyAsDouble(paragraph.substring(start,next))>limit)break;
                    if(Character.isWhitespace(paragraph.codePointAt(end)))space=next;
                    end=next;
                }
                // One glyph wider than the entire panel is an invalid font/viewport pair, not permission to drop text.
                if(end==start)throw new IllegalArgumentException("Detail glyph wider than text viewport");
                if(end<paragraph.length()&&space>start)end=space;
                lines.add(paragraph.substring(start,end));start=end;
            }
        }
        return List.copyOf(lines);
    }
    private DeveloperSkillDetailsLayout() {}
}

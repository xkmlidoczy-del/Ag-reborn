/* AcademyCraft 1.0.7 NotifyUI animation adaptation. GPLv3. */
package cn.academy.port.client.tutorial;

public final class TutorialNotificationTimeline {
    public static final long KEEP_TIME=6000,BLEND_IN_TIME=500,SCAN_TIME=500,BLEND_OUT_TIME=300;
    public record Frame(double background,double icon,double iconX,double text,boolean visible){}
    public static Frame frame(double elapsed){
        if(elapsed<0||elapsed>=KEEP_TIME)return new Frame(0,0,34,0,false);
        if(elapsed<BLEND_IN_TIME)return new Frame(Math.min(elapsed/300,1),TutorialLayout.clamp((elapsed-200)/300),420,0,true);
        if(elapsed<BLEND_IN_TIME+SCAN_TIME){double scan=Math.sin((elapsed-BLEND_IN_TIME)/SCAN_TIME*Math.PI/2);return new Frame(1,1,420+(34-420)*scan,Math.max(.1,scan),true);}
        if(elapsed<KEEP_TIME-BLEND_OUT_TIME)return new Frame(1,1,34,1,true);
        double alpha=1-(elapsed-(KEEP_TIME-BLEND_OUT_TIME))/BLEND_OUT_TIME;return new Frame(alpha,alpha,34,Math.max(.1,alpha),true);
    }
    private TutorialNotificationTimeline(){}
}

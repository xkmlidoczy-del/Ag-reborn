/* Source: AcademyCraft1.0.7 TerminalInstallEffect/TerminalUI. GPLv3. */
package cn.academy.port.client.terminal;

/** Dependency-free source timing and virtual pointer geometry. */
public final class TerminalTimeline {
    public static final long ANIM_LENGTH=4000,WAIT=700,BLEND_IN=200,BLEND_OUT=200;
    public static final int MAX_MX=605,MAX_MY=740;
    private TerminalTimeline(){}
    public record Install(double progress,double alpha,boolean complete){}
    public static Install install(long elapsed){double alpha=elapsed<BLEND_IN?(double)elapsed/BLEND_IN:elapsed>ANIM_LENGTH?Math.max(0,1-(double)(elapsed-ANIM_LENGTH)/BLEND_OUT):1;return new Install(Math.min(1,(double)elapsed/ANIM_LENGTH),alpha,elapsed>=ANIM_LENGTH+WAIT);}
    public static double appAlpha(long elapsed,int id){return clamp((elapsed-(id+1)*100)/400d);}
    public static int selection(double x,double y){return (int)((y-.01)/MAX_MY*3)*3+(int)((x-.01)/MAX_MX*3);}
    public static int maxScroll(int count){return Math.max(0,(count+2)/3-3);}
    /** Source getSelectedApp uses scroll+selection, although positions advance scroll*3. Preserve it. */
    public static int selectedIndex(int scroll,int selection){return scroll+selection;}
    public static double balance(long dt,double from,double to){double d=to-from;return from+Math.min(3*dt,Math.abs(d))*Math.signum(d);}
    public static double clamp(double value){return Math.max(0,Math.min(1,value));}
    public static double cursor(long now,boolean selected){return (selected?1.3:1)*(20+Math.sin(now/300d)*2);}
    public static String worldTime(long ticks){int value=(int)Math.floorMod(ticks,24000);return String.format(java.util.Locale.ROOT,"%02d:%02d",value/1000,value%1000*60/1000);}
    /** Opens on release, and never invents a release after a session replacement. */
    public static final class ReleaseKey {
        private boolean down;
        public void replace(boolean value){down=value;}
        public boolean update(boolean value,boolean allowed){boolean release=down&&!value;down=value;return release&&allowed;}
    }
}

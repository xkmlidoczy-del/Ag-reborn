package cn.academy.port.client.terminal.media;

/** Main-thread transport state. Native source observations, not game ticks, supply elapsed time. */
public final class MediaPlayback {
    public record Info(MediaFiles.Track media,boolean paused,double seconds){public String displayTime(){return MediaPlayback.displayTime(seconds);}public double progress(){return Math.max(0,Math.min(1,seconds/media.lengthSeconds()));}}
    private long generation;private MediaFiles.Track requested,last;private Info current;private boolean desiredPause;
    public long request(MediaFiles.Track media){generation++;requested=last=media;current=null;desiredPause=false;return generation;}
    public boolean accepts(long token){return token==generation&&requested!=null;}
    public void started(long token){if(accepts(token))current=new Info(requested,desiredPause,0);}
    public void observe(long token,boolean playing,boolean stopped,double seconds){if(!accepts(token)||current==null)return;if(stopped){requested=null;current=null;}else current=new Info(requested,!playing,Math.max(0,Math.min(requested.lengthSeconds(),seconds)));}
    public void pause(){desiredPause=true;if(current!=null)current=new Info(current.media(),true,current.seconds());}
    public void resume(){desiredPause=false;if(current!=null)current=new Info(current.media(),false,current.seconds());}
    public void stop(){generation++;requested=null;current=null;desiredPause=false;}
    public void failed(long token){if(accepts(token)){requested=null;current=null;}}
    public void reset(){stop();last=null;}
    public boolean desiredPause(){return desiredPause;}public Info current(){return current;}public MediaFiles.Track last(){return last;}public boolean loading(){return requested!=null&&current==null;}
    public static String displayTime(double secs){int total=(int)secs;int minutes=total/60,seconds=total%60;return (minutes<10?"0":"")+minutes+":"+(seconds<10?"0":"")+seconds;}
}

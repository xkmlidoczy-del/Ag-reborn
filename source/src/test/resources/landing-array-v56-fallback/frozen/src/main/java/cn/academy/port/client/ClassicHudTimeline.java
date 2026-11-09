/* AcademyCraft 1.0.7 CPBar/KeyHintUI/BackgroundMask adaptation. GPLv3; see NOTICE. */
package cn.academy.port.client;

import java.util.Random;

/** Pure classic HUD timing/geometry. Clock inputs are explicit for deterministic regression tests. */
public final class ClassicHudTimeline {
    public static final double CP_WIDTH=964, CP_HEIGHT=147, CP_SCALE=.2;
    public static final double KEY_WIDTH=140, KEY_HEIGHT=210, KEY_SCALE=.23;
    public static final double CP_X=-12, CP_Y=12, KEY_X=0, KEY_Y=30;
    public static final long CP_FADE_MS=200, KEY_FADE_MS=300, PRESET_MS=2000;
    public static final double BALANCE_PER_SECOND=2;
    public record Point(double x,double y) {}
    public record CpQuad(double leftTop,double leftBottom,double right,double top,double bottom) {}
    public record Rgba(double r,double g,double b,double a) {
        public Rgba multiplyAlpha(double alpha){return new Rgba(r,g,b,a*alpha);}
    }
    public record Frame(double alpha,double cp,double overload,double numbersAlpha,double presetAlpha,
                        double keysAlpha,double sinAlpha,double warningAlpha,double scroll,
                        double offsetX,double offsetY,Rgba screenMask) {}
    private static final long NEVER=Long.MIN_VALUE/2;
    private final long[] interferenceEnds=new long[60];
    private final Point[] interferenceOffsets=new Point[60];
    private final ClassicCubicCurve interferenceAlpha;
    private final long interferencePeriod;
    private boolean lastActive,showingNumbers;
    private long lastDrawTime=NEVER,showTime=NEVER,numbersChange=NEVER;
    private long lastKeyFrame=NEVER,keyShowTime=NEVER,presetTime=NEVER,lastPresetTime=NEVER;
    private int lastPreset=-1;
    private double bufferedCp,bufferedOverload;
    private long maskLastTime=NEVER;
    private Rgba mask=new Rgba(0,0,0,0);

    public ClassicHudTimeline(long seed) {
        Random random=new Random(seed);double[] knots=new double[122];
        knots[0]=0;knots[1]=.2+.6*random.nextDouble();long sum=0;
        for(int i=0;i<60;i++){
            sum+=80+random.nextInt(321);double norm=Math.pow(random.nextDouble(),3),theta=random.nextDouble()*Math.PI*2;
            interferenceEnds[i]=sum;interferenceOffsets[i]=new Point(Math.sin(theta)*norm*9*(CP_WIDTH/CP_HEIGHT),Math.cos(theta)*norm*9);
            knots[(i+1)*2]=sum;knots[(i+1)*2+1]=.4+.3*random.nextDouble();
        }
        interferencePeriod=sum;interferenceAlpha=new ClassicCubicCurve(knots);
    }
    public ClassicHudTimeline(){this(System.nanoTime());}

    public Frame update(long gameTime,long absoluteTime,boolean active,boolean overloaded,boolean recovering,
                        boolean interfering,double cp,double overload,boolean toggleHeld,int preset,String category){
        if(toggleHeld&&!showingNumbers){showingNumbers=true;numbersChange=gameTime;}
        else if(!toggleHeld&&showingNumbers){showingNumbers=false;numbersChange=gameTime-numbersChange>400?gameTime:NEVER;}
        if(active&&!lastActive)showTime=gameTime;
        long delta=lastDrawTime==NEVER?100:Math.max(0,Math.min(100,gameTime-lastDrawTime));
        double alpha=gameTime-showTime<CP_FADE_MS?clamp((gameTime-showTime)/(double)CP_FADE_MS):
                active?1:clamp(1-(gameTime-lastDrawTime)/(double)CP_FADE_MS);
        Point offset=new Point(0,0);
        if(interfering){
            long phase=Math.floorMod(absoluteTime,interferencePeriod);int i=0;
            while(i<interferenceEnds.length-1&&interferenceEnds[i]<=phase)i++;
            offset=interferenceOffsets[i];alpha*=interferenceAlpha.valueAt(phase/10*10);
        }
        bufferedCp=balance(bufferedCp,alpha>0?clamp(cp):0,delta*.001*BALANCE_PER_SECOND);
        bufferedOverload=balance(bufferedOverload,alpha>0?clamp(overload):0,delta*.001*BALANCE_PER_SECOND);
        double numbers=0;
        if(!overloaded&&numbersChange!=NEVER){long dt=gameTime-numbersChange;
            if(showingNumbers)numbers=clamp((dt-200)/400.0);else if(dt<300)numbers=clamp(1-dt/300.0);
        }
        if(lastPreset==-1)lastPreset=preset;
        else if(lastPreset!=preset){lastPresetTime=presetTime;presetTime=gameTime;lastPreset=preset;}
        double presetAlpha=presetTime==NEVER?0:presetAlpha(gameTime-presetTime,gameTime-lastPresetTime);
        double keysAlpha=0;
        if(active){
            if(gameTime-lastKeyFrame>KEY_FADE_MS)keyShowTime=gameTime;
            keysAlpha=clamp((gameTime-keyShowTime)/(double)KEY_FADE_MS);lastKeyFrame=gameTime;
        }
        Rgba target=overloaded?new Rgba(208/255.0,20/255.0,20/255.0,170/255.0):active?categoryMask(category):mask.multiplyAlpha(0);
        if(target.a!=0||mask.a!=0){double dt=maskLastTime==NEVER?0:Math.max(0,gameTime-maskLastTime)*.001;
            mask=new Rgba(balance(mask.r,target.r,dt),balance(mask.g,target.g,dt),balance(mask.b,target.b,dt),balance(mask.a,target.a,dt));
        }else mask=target;
        maskLastTime=gameTime;
        if(active)lastDrawTime=gameTime;lastActive=active;
        return new Frame(clamp(alpha),bufferedCp,bufferedOverload,numbers,presetAlpha,keysAlpha,
                .6+(1+Math.sin((gameTime%100000)/50.0))*.2,
                .3+.35*(Math.sin(gameTime/200.0)+1),Math.floorMod(gameTime,10000)/10000.0,
                offset.x,offset.y,mask);
    }
    public static boolean defaultGroup(String group){return "def".equals(group)||"default".equals(group);}
    public static Point cpOrigin(double guiWidth,double x,double y){return new Point(guiWidth-CP_WIDTH*CP_SCALE+x,y);}
    public static Point keyOrigin(double guiWidth,double guiHeight,double x,double y){return new Point(guiWidth-KEY_WIDTH*KEY_SCALE+x,(guiHeight-KEY_HEIGHT*KEY_SCALE)*.5+y);}
    public static CpQuad cpQuad(double progress){double length=883*(.16+.8*clamp(progress));return new CpQuad(47+883-length,47+883-length+103*Math.sin(Math.toRadians(44)),930,30,114);}
    public static Rgba cpColor(double progress){return ramp(clamp(progress),new double[]{0,.35,1},new int[]{0xfff06767,0xffffae44,0xffffffff});}
    public static Rgba overloadColor(double progress){return ramp(clamp(progress),new double[]{0,.55,1},new int[]{0x0adfdfdf,0x23f0d49d,0x50f56464});}
    public static boolean overloadWarning(boolean overloadFine,int recoveryDelay){return !overloadFine&&recoveryDelay>0;}
    public static boolean unavailable(boolean overloadFine,boolean interfering){return !overloadFine||interfering;}
    public static double consumptionPulse(long gameTime){return .2+.1*(1+Math.sin(gameTime/80.0));}
    public static double presetAlpha(long elapsed,long sincePrevious){
        if(elapsed<0||elapsed>=PRESET_MS)return 0;double progress=elapsed/(double)PRESET_MS;
        return .75*(sincePrevious>3000&&progress<.2?progress/.2:progress>.8?(1-progress)/.2:1);
    }
    public static Rgba categoryMask(String category){return switch(category){
        case "electromaster"->new Rgba(20/255.0,113/255.0,208/255.0,100/255.0);
        case "meltdowner"->new Rgba(126/255.0,1,132/255.0,80/255.0);
        case "teleporter"->new Rgba(164/255.0,164/255.0,164/255.0,145/255.0);
        case "vecmanip"->new Rgba(0,0,0,1);
        default->new Rgba(0,0,0,0);
    };}
    public static double balance(double from,double to,double max){return from+Math.copySign(Math.min(Math.abs(to-from),Math.max(0,max)),to-from);}
    public static double clamp(double value){return Double.isFinite(value)?Math.max(0,Math.min(1,value)):0;}
    private static Rgba unpack(int value){return new Rgba(((value>>>16)&255)/255.0,((value>>>8)&255)/255.0,(value&255)/255.0,(value>>>24)/255.0);}
    private static Rgba ramp(double progress,double[] positions,int[] colors){
        for(int i=0;i<positions.length;i++)if(positions[i]>=progress){Rgba b=unpack(colors[i]);if(i==0)return b;
            Rgba a=unpack(colors[i-1]);double t=(progress-positions[i-1])/(positions[i]-positions[i-1]);
            return new Rgba(a.r+(b.r-a.r)*t,a.g+(b.g-a.g)*t,a.b+(b.b-a.b)*t,a.a+(b.a-a.a)*t);
        }return unpack(colors[colors.length-1]);
    }
}

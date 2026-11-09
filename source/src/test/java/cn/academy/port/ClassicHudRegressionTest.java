package cn.academy.port;

import cn.academy.port.client.ClassicHudTimeline;

/** Source CPBar/CGui/KeyHintUI/BackgroundMask equations, independent of Minecraft and a display. */
public final class ClassicHudRegressionTest {
    private static int assertions;
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private static void eq(double expected,double value){check(Double.isFinite(value)&&Math.abs(expected-value)<1e-7,expected+" != "+value);}
    private static ClassicHudTimeline.Frame frame(ClassicHudTimeline t,long time,boolean active,boolean held){return t.update(time,time,active,false,false,false,1,.6,held,0,"electromaster");}
    public static void main(String[] args){
        eq(964,ClassicHudTimeline.CP_WIDTH);eq(147,ClassicHudTimeline.CP_HEIGHT);eq(.2,ClassicHudTimeline.CP_SCALE);
        eq(140,ClassicHudTimeline.KEY_WIDTH);eq(210,ClassicHudTimeline.KEY_HEIGHT);eq(.23,ClassicHudTimeline.KEY_SCALE);
        // CGui aligns the scaled widget extent, then adds the unscaled root offset.
        var origin=ClassicHudTimeline.cpOrigin(960,-12,12);eq(755.2,origin.x());eq(12,origin.y());
        var key=ClassicHudTimeline.keyOrigin(960,540,0,30);eq(927.8,key.x());eq(275.85,key.y());
        eq(480,origin.x()-ClassicHudTimeline.cpOrigin(480,-12,12).x());
        var empty=ClassicHudTimeline.cpQuad(0);eq(788.72,empty.leftTop());eq(930,empty.right());eq(30,empty.top());eq(114,empty.bottom());
        eq(103*Math.sin(Math.toRadians(44)),empty.leftBottom()-empty.leftTop());
        var full=ClassicHudTimeline.cpQuad(1);eq(82.32,full.leftTop());eq(930,full.right());
        eq(empty.leftTop(),ClassicHudTimeline.cpQuad(-1).leftTop());eq(full.leftTop(),ClassicHudTimeline.cpQuad(8).leftTop());
        var red=ClassicHudTimeline.cpColor(0);eq(240/255.0,red.r());eq(103/255.0,red.g());eq(103/255.0,red.b());eq(1,red.a());
        var amber=ClassicHudTimeline.cpColor(.35);eq(1,amber.r());eq(174/255.0,amber.g());eq(68/255.0,amber.b());
        var white=ClassicHudTimeline.cpColor(1);eq(1,white.r());eq(1,white.g());eq(1,white.b());
        var midpoint=ClassicHudTimeline.cpColor(.175);eq((240+255)/510.0,midpoint.r());eq((103+174)/510.0,midpoint.g());
        eq(10/255.0,ClassicHudTimeline.overloadColor(0).a());eq(35/255.0,ClassicHudTimeline.overloadColor(.55).a());eq(80/255.0,ClassicHudTimeline.overloadColor(1).a());
        eq(.2,ClassicHudTimeline.balance(0,1,.2));eq(.8,ClassicHudTimeline.balance(1,0,.2));eq(.1,ClassicHudTimeline.balance(0,.1,.2));
        eq(0,ClassicHudTimeline.clamp(Double.NaN));eq(0,ClassicHudTimeline.clamp(Double.POSITIVE_INFINITY));
        var timeline=new ClassicHudTimeline(1);
        var f=frame(timeline,10000,false,false);eq(0,f.alpha());eq(0,f.cp());eq(0,f.keysAlpha());eq(0,f.presetAlpha());
        f=frame(timeline,10100,true,false);eq(0,f.alpha());eq(0,f.cp());eq(0,f.keysAlpha());
        f=frame(timeline,10200,true,false);eq(.5,f.alpha());eq(.2,f.cp());eq(.2,f.overload());eq(1/3.0,f.keysAlpha());
        f=frame(timeline,10300,true,false);eq(1,f.alpha());eq(.4,f.cp());eq(.4,f.overload());eq(2/3.0,f.keysAlpha());
        f=frame(timeline,11000,true,false);eq(.6,f.cp());eq(.6,f.overload());eq(0,f.keysAlpha()); // >300ms draw gap restarts key fade
        f=frame(timeline,11100,false,false);eq(.5,f.alpha());eq(.8,f.cp());eq(0,f.keysAlpha());
        f=frame(timeline,11200,false,false);eq(0,f.alpha());eq(.6,f.cp());
        // CP/OL hold display: 200ms delay, 400ms fade in, release fade only after >400ms hold.
        timeline=new ClassicHudTimeline(2);frame(timeline,10000,true,true);
        eq(0,frame(timeline,10200,true,true).numbersAlpha());eq(.5,frame(timeline,10400,true,true).numbersAlpha());eq(1,frame(timeline,10600,true,true).numbersAlpha());
        eq(1,frame(timeline,10650,true,false).numbersAlpha());eq(.5,frame(timeline,10800,true,false).numbersAlpha());eq(0,frame(timeline,10950,true,false).numbersAlpha());
        timeline=new ClassicHudTimeline(2);frame(timeline,10000,true,true);eq(.25,frame(timeline,10300,true,true).numbersAlpha());eq(0,frame(timeline,10400,true,false).numbersAlpha());
        timeline=new ClassicHudTimeline(3);frame(timeline,10000,true,true);
        f=timeline.update(11000,11000,true,true,true,false,.1,1,true,0,"electromaster");eq(0,f.numbersAlpha());
        check(ClassicHudTimeline.overloadWarning(false,32),"Full overload warning during source recovery delay");
        check(!ClassicHudTimeline.overloadWarning(false,0),"Recovery dim phase is no longer a warning");
        check(!ClassicHudTimeline.overloadWarning(true,32),"Normal post-cast overload delay has no warning");
        check(ClassicHudTimeline.unavailable(false,false),"Recovery prevents abilities");check(ClassicHudTimeline.unavailable(true,true),"Interference prevents abilities");check(!ClassicHudTimeline.unavailable(true,false),"Idle available state");
        eq(0,ClassicHudTimeline.presetAlpha(0,4000));eq(.375,ClassicHudTimeline.presetAlpha(200,4000));eq(.75,ClassicHudTimeline.presetAlpha(400,4000));eq(.75,ClassicHudTimeline.presetAlpha(1600,4000));eq(.375,ClassicHudTimeline.presetAlpha(1800,4000));eq(0,ClassicHudTimeline.presetAlpha(2000,4000));
        eq(.75,ClassicHudTimeline.presetAlpha(0,100)); // rapid switch has no fade-in
        timeline=new ClassicHudTimeline(5);frame(timeline,10000,true,false);
        f=timeline.update(11000,11000,true,false,false,false,1,0,false,2,"electromaster");eq(0,f.presetAlpha());
        f=timeline.update(11200,11200,true,false,false,false,1,0,false,2,"electromaster");eq(.375,f.presetAlpha());
        f=timeline.update(11300,11300,true,false,false,false,1,0,false,3,"electromaster");eq(.75,f.presetAlpha());
        var sameA=new ClassicHudTimeline(19);var sameB=new ClassicHudTimeline(19);frame(sameA,10000,true,false);frame(sameB,10000,true,false);
        for(int ms=10200;ms<25000;ms+=11){var a=sameA.update(ms,ms,true,false,false,true,1,.5,false,0,"electromaster");var b=sameB.update(ms,ms,true,false,false,true,1,.5,false,0,"electromaster");
            eq(a.alpha(),b.alpha());eq(a.offsetX(),b.offsetX());eq(a.offsetY(),b.offsetY());
            check(a.alpha()>=0&&a.alpha()<=1,"Interference alpha stays finite/normalized");check(Math.abs(a.offsetX())<=9*964/147.0,"Source horizontal interference bound");check(Math.abs(a.offsetY())<=9,"Source vertical interference bound");
        }
        timeline=new ClassicHudTimeline(8);frame(timeline,10000,true,false);f=frame(timeline,11000,true,false);
        eq(20/255.0,f.screenMask().r());eq(113/255.0,f.screenMask().g());eq(208/255.0,f.screenMask().b());eq(100/255.0,f.screenMask().a());
        eq(0,frame(timeline,12000,false,false).screenMask().a());eq(1,ClassicHudTimeline.categoryMask("vecmanip").a());
        eq(.3,ClassicHudTimeline.consumptionPulse(0));
        check(f.warningAlpha()>=.3&&f.warningAlpha()<=1,"Overload highlight pulse range");
        eq(.1,f.scroll());
        System.out.println("PASS "+assertions+" classic HUD source-equation assertions (no game launch)");
    }
}

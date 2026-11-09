package cn.academy.port;
import cn.academy.port.client.ClassicEffectTimeline;
public final class VisualTimelineRegressionTest {
    private static int assertions;
    private static void eq(double expected,double actual) {assertions++;if(Math.abs(expected-actual)>1e-8)throw new AssertionError(expected+" != "+actual);}
    public static void main(String[] args) {
        eq(0,ClassicEffectTimeline.chargeFrame(0));eq(0,ClassicEffectTimeline.chargeFrame(39));eq(1,ClassicEffectTimeline.chargeFrame(40));eq(39,ClassicEffectTimeline.chargeFrame(1560));eq(39,ClassicEffectTimeline.chargeFrame(1599));eq(-1,ClassicEffectTimeline.chargeFrame(1600));
        eq(0,ClassicEffectTimeline.beamLengthScale(0));eq(.5,ClassicEffectTimeline.beamLengthScale(75));eq(1,ClassicEffectTimeline.beamLengthScale(150));eq(1,ClassicEffectTimeline.beamLengthScale(2500));
        eq(1,ClassicEffectTimeline.beamAlpha(0));eq(1,ClassicEffectTimeline.beamAlpha(1500));eq(.5,ClassicEffectTimeline.beamAlpha(2000));eq(0,ClassicEffectTimeline.beamAlpha(2500));
        eq(1,ClassicEffectTimeline.beamWidthScale(1699));eq(1,ClassicEffectTimeline.beamWidthScale(1700));eq(.5,ClassicEffectTimeline.beamWidthScale(2100));eq(0,ClassicEffectTimeline.beamWidthScale(2500));
        System.out.println("PASS "+assertions+" visual timeline assertions (no rendering)");
    }
}

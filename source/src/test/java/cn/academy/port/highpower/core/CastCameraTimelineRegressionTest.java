package cn.academy.port.highpower.core;

/** Behavioral camera ownership, cancellation, category identity and finite-bound regression. */
public final class CastCameraTimelineRegressionTest {
    private static int checks;
    private static void check(boolean ok, String detail) { checks++; if (!ok) throw new AssertionError(detail); }
    public static void main(String[] args) {
        var clock = new CastCameraTimeline();
        for (String cat : new String[] {"electromaster", "meltdowner", "teleporter", "vecmanip"}) {
            clock.cast("a", cat, 5, 1, true);
            for (int i = 0; i < 30; i++) clock.tick();
            clock.release("other"); check(clock.holds("a"), "another skill key-up must not release camera");
            clock.cancel("other"); check(clock.holds("a"), "another skill refusal must not cancel camera");
            clock.release("a"); check(!clock.holds("a"), "owner release ends charge");
            boolean motion = false;
            for (int i = 0; i < 32; i++) {
                var v = clock.sample(.5);
                check(Double.isFinite(v.fov() + v.pitch() + v.roll()) && v.fov() >= .85 && v.fov() <= 1.15
                        && Math.abs(v.pitch()) <= 2.3 && Math.abs(v.roll()) <= 3.1, cat + " finite, bounded view");
                motion |= Math.abs(v.fov() - 1) + Math.abs(v.pitch()) + Math.abs(v.roll()) > .01;
                clock.tick();
            }
            check(motion && !clock.active() && clock.sample(.5).equals(CastCameraTimeline.View.NONE), cat + " release plays and retires");
            clock.cast("a", cat, 5, 1, true); for (int i = 0; i < 10; i++) clock.tick();
            clock.cancel("a"); check(clock.sample(0).equals(CastCameraTimeline.View.NONE), "refusal/abort is immediately neutral");
            clock.cast("a", cat, 5, 1, true); clock.clear(); check(!clock.active(), "world/owner reset clears");
            clock.cast("a", cat, 5, 1, false); var full = clock.sample(1);
            clock.cast("a", cat, 4, 1, false); var small = clock.sample(1);
            check(Math.abs(small.fov() - 1) <= Math.abs(full.fov() - 1) + 1e-9 && Math.abs(small.roll()) <= Math.abs(full.roll()) + 1e-9, "LV4 scales below LV5");
        }
        clock.cast("a", "vecmanip", 5, 1, true);
        for (int i = 0; i < CastCameraTimeline.MAX_HOLD_TICKS + 2; i++) clock.tick();
        check(!clock.active(), "lost termination has finite lifetime");
        for (double value : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            clock.cast("a", "teleporter", 5, value, false); check(!clock.active(), "bad/disabled strength stays neutral");
        }
        clock.cast("old", "vecmanip", 5, 1, true); clock.cast("basic", "vecmanip", 3, 1, true);
        check(!clock.active(), "basic cast replaces old advanced camera");
        clock.cast("a", "teleporter", 5, 1, false);
        check(clock.sample(Double.NaN).equals(CastCameraTimeline.View.NONE), "bad partial is neutral");
        physicalReleaseBeforeDelegateAndGuiCancel();
        System.out.println("CastCameraTimelineRegressionTest: " + checks + " checks passed");
    }
    private static void physicalReleaseBeforeDelegateAndGuiCancel() {
        String id="multidimensional_landing_array";
        var expected=new CastCameraTimeline();var actual=new CastCameraTimeline();
        expected.cast(id,"teleporter",5,1,true);actual.cast(id,"teleporter",5,1,true);
        for(int i=0;i<20;i++){
            expected.tick();actual.tick();check(actual.sample(.5).equals(CastCameraTimeline.View.NONE),"TP hold intentionally has no FOV/angle offset");
        }
        // Real order: mapping becomes up, pose tick runs, then the input delegate emits the same key-up.
        actual.release(id);expected.release(id);actual.release(id);
        boolean fov=false;
        for(int i=0;i<32;i++){
            var view=actual.sample(.5);check(view.equals(expected.sample(.5)),"early physical release plus delegate release is idempotent, without restarting or canceling the recoil");
            fov|=Math.abs(view.fov()-1)>1e-6;actual.tick();expected.tick();
        }
        check(fov&&!actual.active(),"normal TP key-up produces a real FOV phase and returns to neutral");
        actual.cast(id,"teleporter",5,1,true);for(int i=0;i<20;i++)actual.tick();
        actual.cancel(id);actual.release(id);
        check(!actual.active()&&actual.sample(.5).equals(CastCameraTimeline.View.NONE),"GUI/abort cancellation cannot be revived by a late delegate key-up");
        actual.cast(id,"teleporter",5,1,true);actual.release(id);actual.cancel(id);
        check(!actual.active(),"GUI cancellation after normal release still removes every residual camera offset");
        try{
            String source=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port/client/CastPoseClient.java"));
            String tick=source.substring(source.indexOf("@SubscribeEvent public static void tick("),source.indexOf("/** Independent construct pose ownership"));
            check(tick.contains("if (mc.screen != null || key == null || \"railgun\".equals(c.skill)) CameraMotion.cancel(c.skill);\n                else CameraMotion.release(c.skill);"),
                    "production fallback routes physical key-up to release, GUI/mapping loss to cancel, and keeps authoritative railgun recoil");
            String end=source.substring(source.indexOf("private static void end("),source.indexOf("/** Installs a new cast"));
            check(end.indexOf("CameraMotion.release(skill)")<end.indexOf("c.release >= 0"),"actual delegate release remains ahead of the already-released pose guard");
        }catch(java.io.IOException failure){throw new AssertionError("camera release integration source",failure);}
    }
}

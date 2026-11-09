package cn.academy.port.client;

/** Dependency-free source timing/parameters; this does not assert rendered or audible parity. */
public final class ThunderBoltVisualRegressionTest {
    private static int assertions;
    private static void check(boolean value, String description) {
        assertions++;
        if (!value) throw new AssertionError(description);
    }
    private static void equal(double expected, double actual, String description) {
        check(Math.abs(expected - actual) < 1.0E-6, description);
    }
    public static void main(String[] args) {
        equal(3, ClassicThunderBoltTimeline.MAIN_ARCS, "three independent main arcs");
        equal(20, ClassicThunderBoltTimeline.MAIN_LIFE_TICKS, "twenty tick strong Life");
        equal(20, ClassicThunderBoltTimeline.TEMPLATES, "twenty patterns per arc kind");
        equal(20, ClassicThunderBoltTimeline.PATTERN_LENGTH, "unscaled twenty-block patterns");
        equal(5, ClassicThunderBoltTimeline.PASSES, "five subdivision passes");
        equal(.3, ClassicThunderBoltTimeline.STRONG_WIDTH, "strong width");
        equal(1.4, ClassicThunderBoltTimeline.STRONG_OFFSET, "strong offset");
        equal(.3, ClassicThunderBoltTimeline.STRONG_BRANCH, "strong branch rate");
        equal(.13, ClassicThunderBoltTimeline.AOE_WIDTH, "AOE width");
        equal(1.2, ClassicThunderBoltTimeline.AOE_OFFSET, "AOE offset");
        equal(.28, ClassicThunderBoltTimeline.AOE_BRANCH, "AOE branch rate");
        equal(.7, ClassicThunderBoltTimeline.WIDTH_SHRINK, "source width shrink");
        equal(.6, ClassicThunderBoltTimeline.SOUND_VOLUME, "single strong sound volume");
        for (int age = 0; age < 20; age++) check(ClassicThunderBoltTimeline.alive(age, 20), "main alive at " + age);
        check(!ClassicThunderBoltTimeline.alive(20, 20), "main dies exactly on Life20");
        check(!ClassicThunderBoltTimeline.alive(-1, 20), "negative age rejected");
        for (int offset = 0; offset < 10; offset++) {
            int life = ClassicThunderBoltTimeline.aoeLife(offset);
            equal(15 + offset, life, "exclusive-range AOE lifespan");
            check(ClassicThunderBoltTimeline.alive(life - 1, life), "AOE alive before death tick");
            check(!ClassicThunderBoltTimeline.alive(life, life), "AOE dies on selected Life tick");
        }
        boolean rejects25 = false;
        try { ClassicThunderBoltTimeline.aoeLife(10); } catch (IllegalArgumentException expected) { rejects25 = true; }
        check(rejects25, "AOE twenty-five ticks cannot be selected");
        check(!ClassicThunderBoltTimeline.shown(true, Math.nextDown(.2)), "shown arc hides strictly below .2");
        check(ClassicThunderBoltTimeline.shown(true, .2), "show threshold equal keeps shown");
        check(ClassicThunderBoltTimeline.shown(false, Math.nextDown(.2)), "hidden arc shows strictly below .2");
        check(!ClassicThunderBoltTimeline.shown(false, .2), "hide threshold equal keeps hidden");
        check(ClassicThunderBoltTimeline.replacePattern(Math.nextDown(.5)), "pattern changes strictly below .5");
        check(!ClassicThunderBoltTimeline.replacePattern(.5), "texture threshold equal does not change");
        check(ClassicThunderBoltTimeline.drawsSegment(8, 8), "segment start exactly at clipped endpoint is drawn whole");
        check(!ClassicThunderBoltTimeline.drawsSegment(Math.nextUp(8.0), 8), "segment beyond clipped endpoint omitted");
        check(ClassicThunderBoltTimeline.drawsSegment(21, Double.POSITIVE_INFINITY), "fixed main arc does not clip extended branches");
        check(ClassicThunderBoltTimeline.drawsSegment(0, 0), "source clipping tests start instead of segment end");
        var first = ClassicThunderBoltTimeline.viewOffset(true);
        equal(-.05, first.x(), "first-person localX"); equal(-.25, first.y(), "first-person localY");
        equal(.2, first.z(), "first-person localZ");
        var third = ClassicThunderBoltTimeline.viewOffset(false);
        equal(.15, third.x(), "third-person localX"); equal(-.8, third.y(), "third-person localY");
        equal(.23, third.z(), "third-person localZ");
        System.out.println("PASS " + assertions + " ThunderBolt visual timeline assertions (no renderer launch)");
    }
}

/* AcademyCraft 1.0.7 ThunderBolt/EntityArc/ArcPatterns parameters. See NOTICE. */
package cn.academy.port.client;

/** Dependency-free effect parameters and tick decisions, used by the actual renderer. */
public final class ClassicThunderBoltTimeline {
    public static final int MAIN_ARCS = 3, MAIN_LIFE_TICKS = 20;
    public static final int AOE_LIFE_FROM = 15, AOE_LIFE_TO_EXCLUSIVE = 25;
    public static final int TEMPLATES = 20, PASSES = 5;
    public static final double PATTERN_LENGTH = 20, WIDTH_SHRINK = .7;
    public static final double STRONG_WIDTH = .3, STRONG_OFFSET = 1.4, STRONG_BRANCH = .3;
    public static final double AOE_WIDTH = .13, AOE_OFFSET = 1.2, AOE_BRANCH = .28;
    public static final double TEX_WIGGLE = .5, SHOW_WIGGLE = .2, HIDE_WIGGLE = .2;
    public static final float SOUND_VOLUME = .6F;

    private ClassicThunderBoltTimeline() {}

    public static boolean alive(int age, int life) { return age >= 0 && age < life; }
    public static boolean shown(boolean shown, double sample) {
        return sample < (shown ? SHOW_WIGGLE : HIDE_WIGGLE) ? !shown : shown;
    }
    public static boolean replacePattern(double sample) { return sample < TEX_WIGGLE; }

    /** RandUtils.rangei(15,25) excludes 25. */
    public static int aoeLife(int randomOffset) {
        if (randomOffset < 0 || randomOffset >= AOE_LIFE_TO_EXCLUSIVE - AOE_LIFE_FROM)
            throw new IllegalArgumentException("AOE random offset must be in [0,10)");
        return AOE_LIFE_FROM + randomOffset;
    }

    /** Arc.draw(length) retains complete segments whose START is <= length, without scaling. */
    public static boolean drawsSegment(double startLocalX, double clipLength) {
        return startLocalX <= clipLength;
    }

    public static ViewOffset viewOffset(boolean firstPerson) {
        return firstPerson ? new ViewOffset(-.05, -.25, .2) : new ViewOffset(.15, -.8, .23);
    }

    public record ViewOffset(double x, double y, double z) {}
}

/* AcademyCraft1.0.7 MagMovement/EntityArc/EntitySurroundArc source parameters. See NOTICE. */
package cn.academy.port.client;

/** Dependency-free decisions used by both renderer and regression tests. */
public final class ClassicMagneticTimeline {
    public static final int BEAM_TEMPLATES=20,BEAM_PASSES=5;
    public static final double BEAM_LENGTH=20,BEAM_WIDTH=.08,BEAM_OFFSET=1.2,BEAM_BRANCH=.2,BEAM_SHRINK=.7;
    public static final double BEAM_TEX_WIGGLE=1,BEAM_SHOW_WIGGLE=.1,BEAM_HIDE_WIGGLE=.6;
    public static final int SURROUND_TEMPLATES=10,SURROUND_PASSES=3,SURROUND_COUNT=4,SUBARC_LIFE=30;
    public static final double SURROUND_WIDTH=.2,SURROUND_OFFSET=.8,SURROUND_BRANCH=.7,SURROUND_SHRINK=.9,SURROUND_SCALE=.3,SURROUND_SIZE=1.3;
    public static final double SUBARC_FRAME_RATE=.6,SUBARC_SWITCH_RATE=.7;
    public static final float LOOP_VOLUME=.3f;
    public record Offset(double x,double y,double z) {}
    /** LambdaLib1.2.3 ViewOptimize, in the arc's direction/up/normal basis. */
    public static Offset viewOffset(boolean firstPerson){return firstPerson?new Offset(-.05,-.25,.2):new Offset(.15,-.8,.23);}
    private ClassicMagneticTimeline() {}
    public static boolean beamShown(boolean shown,double sample){return sample<(shown?BEAM_SHOW_WIGGLE:BEAM_HIDE_WIGGLE)?!shown:shown;}
    public static boolean subArcShown(boolean shown,double sample){return sample<(shown?.4:.3)*SUBARC_SWITCH_RATE?!shown:shown;}
    public static int subArcAge(int age,double sample){return sample<.9?age+1:age;}
    public static boolean matchingUpdate(long token,long active,long tick,long previous){return token>0&&token==active&&tick>previous;}
}

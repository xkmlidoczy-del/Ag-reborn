/* AcademyCraft 1.0.7 MagMovement/MagManip numbers and update order. See NOTICE. */
package cn.academy.port.core;

/** Pure, source-derived rules, including source quirks instead of invented projectile tuning. */
public final class MagneticRules {
    public static final String MOVEMENT = "mag_movement", MANIPULATION = "mag_manip";
    public static final double MOVEMENT_RANGE = SkillTuning.number("mag_movement", "reach"), MOVEMENT_ACCEL = .08, MOVEMENT_SPEED = 1;
    public static final double PICKUP_RANGE = SkillTuning.number("mag_manip", "reach"), THROW_RANGE = SkillTuning.number("mag_manip", "throw_reach"), RELEASE_DISTANCE_SQUARED = 25;
    public static final double BLOCK_HOVER_SPEED = .2, BLOCK_GRAVITY = .04;
    // MagManipContext.damage (8→15) was never passed: source constructor always receives 10.
    public static final double BLOCK_COLLISION_DAMAGE = 10;
    private MagneticRules() {}
    public record Movement(double cpPerTick, double overload) {}
    public record Manipulation(double cp, double overload, int cooldown, double speed) {}
    public record Vector(double x, double y, double z) {
        public double lengthSquared() { return x*x + y*y + z*z; }
        public Vector add(Vector b) { return new Vector(x+b.x,y+b.y,z+b.z); }
        public Vector subtract(Vector b) { return new Vector(x-b.x,y-b.y,z-b.z); }
        public Vector scale(double n) { return new Vector(x*n,y*n,z*n); }
        public Vector normalized() { double n=Math.sqrt(lengthSquared()); return n<1.0e-12?new Vector(0,0,0):scale(1/n); }
    }
    private static float lerpf(float a,float b,double exp){float e=(float)ClassicRules.clamp(exp,0,1);return a+e*(b-a);}
    public static Movement movement(double exp) { return new Movement(lerpf(15,8,exp),lerpf(60,30,exp)); }
    public static Manipulation manipulation(double exp) { return new Manipulation(lerpf(140,270,exp),lerpf(35,20,exp),(int)lerpf(60,40,exp),lerpf(.5f,1f,exp)); }
    public static double movementExperience(double distance) { return Math.max(.005f,.0011f*(float)Math.max(0,Double.isFinite(distance)?distance:0)); }
    public static double adjust(double from,double to) { double d=to-from; return Math.abs(d)<MOVEMENT_ACCEL?to:from+Math.copySign(MOVEMENT_ACCEL,d); }
    /** Original correction compares squared speeds, not componentwise velocity differences. */
    public static Vector movementVelocity(Vector remembered,Vector actual,Vector targetDelta) {
        if(Math.abs(remembered.lengthSquared()-actual.lengthSquared())>.5) remembered=actual;
        Vector desired=targetDelta.normalized().scale(MOVEMENT_SPEED);
        return new Vector(adjust(remembered.x,desired.x),adjust(remembered.y,desired.y),adjust(remembered.z,desired.z));
    }
    /** Called after Rigidbody has already advanced the old velocity once. */
    public static Vector hoverVelocity(Vector targetDelta) {
        double dist=targetDelta.lengthSquared();
        return targetDelta.normalized().scale(BLOCK_HOVER_SPEED*(dist<4?dist/4:1));
    }
    public static boolean acceptsRelease(double distanceSquared) { return Double.isFinite(distanceSquared)&&distanceSquared<RELEASE_DISTANCE_SQUARED; }
    public static boolean acceptsMovementBlock(double exp,boolean normal,boolean weak) {
        // Source isMetalBlock includes normal OR weak. Its exp<.6 guard is redundant.
        return normal||weak;
    }
    public static Vector thrownVelocity(Vector block,Vector target,double exp) { return target.subtract(block).normalized().scale(manipulation(exp).speed); }
    /** Exact source double displacement: old Rigidbody motion, then newly computed motion. */
    public static Vector nextBlockPosition(Vector position,Vector oldVelocity,Vector nextVelocity) { return position.add(oldVelocity).add(nextVelocity); }
}

package cn.academy.port.highpower;

/** A dispatch may be claimed exactly once, before any event subscriber can reenter it. */
final class VectorParryNativeCall {
    private final Object receiver;
    private final float strength;
    private final double ratioX;
    private final double ratioZ;
    private boolean dispatched;
    private boolean invalid;

    VectorParryNativeCall(Object receiver, double strength, double ratioX, double ratioZ) {
        this.receiver = receiver;
        this.strength = (float) strength;
        this.ratioX = ratioX;
        this.ratioZ = ratioZ;
    }

    boolean claim(Object target, float incomingStrength, double incomingX, double incomingZ) {
        if (dispatched) {
            // Reentrant native knockback remains ordinary, and also vetoes the pending outer turn.
            invalid = true;
            return false;
        }
        dispatched = true;
        return !invalid && receiver == target && same(strength, incomingStrength)
                && same(ratioX, incomingX) && same(ratioZ, incomingZ);
    }

    boolean unchanged(Object target, float eventStrength, double eventX, double eventZ) {
        return dispatched && !invalid && receiver == target && same(strength, eventStrength)
                && same(ratioX, eventX) && same(ratioZ, eventZ);
    }

    void invalidate() { invalid = true; }
    boolean valid() { return !invalid; }
    private static boolean same(float a, float b) { return Float.floatToRawIntBits(a) == Float.floatToRawIntBits(b); }
    private static boolean same(double a, double b) { return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b); }
}

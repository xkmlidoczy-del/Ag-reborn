/* LambdaLib 1.2.3 Motion3D and Minecraft1.7 float-trig bridge. MIT; see docs/LAMBDALIB-LICENSE. */
package cn.academy.port.skill;

/** Pure exact source angle perturbation for new Motion3D(player,5,true), sampled independently per ball. */
public final class ScatterBombAim {
    private static final float[] SINES = new float[65536];
    static { for (int i = 0; i < SINES.length; i++) SINES[i] = (float) Math.sin(i * Math.PI * 2 / 65536); }
    private ScatterBombAim() {}
    public record Direction(double x, double y, double z) {}
    public static Direction direction(float headYaw, float pitch, float yawSample, float pitchSample) {
        if (!Float.isFinite(headYaw) || !Float.isFinite(pitch) || !sample(yawSample) || !sample(pitchSample))
            throw new IllegalArgumentException("finite angles and server RNG samples in [0,1) required");
        // offset is Double in the original constructor; cast rotations only AFTER double offset arithmetic.
        float yaw = (float) (headYaw + 2 * (yawSample - .5F) * 5D);
        float vertical = (float) (pitch + (pitchSample - .5F) * 5D);
        float yawRadians = yaw / 180F * (float) Math.PI, pitchRadians = vertical / 180F * (float) Math.PI;
        double x = -sin(yawRadians) * cos(pitchRadians), y = -sin(pitchRadians), z = cos(yawRadians) * cos(pitchRadians);
        double norm = Math.sqrt(x * x + y * y + z * z);
        return new Direction(x / norm, y / norm, z / norm);
    }
    private static boolean sample(float value) { return Float.isFinite(value) && value >= 0 && value < 1; }
    private static float sin(float value) { return SINES[(int) (value * 10430.378F) & 65535]; }
    private static float cos(float value) { return SINES[(int) (value * 10430.378F + 16384F) & 65535]; }
}

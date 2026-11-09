/* AcademyCraft 1.0.7 TileSolarGen/TileGeneratorBase arithmetic, GPLv3. See NOTICE. */
package cn.academy.port.solar;

/** Exact meaningful source weather/time/charge arithmetic, independent of Minecraft. */
public final class ClassicSolarRules {
    public static final double CAPACITY = 1000, BANDWIDTH = 100, CLEAR_RATE = 3, RAIN_RATE = .2 * CLEAR_RATE;
    public enum Status { STOPPED, WEAK, STRONG }
    private ClassicSolarRules() {}
    public static Status status(long dayTime, boolean skyVisible, boolean raining) {
        long time = dayTime % 24000;
        return time >= 0 && time <= 12500 && skyVisible ? raining ? Status.WEAK : Status.STRONG : Status.STOPPED;
    }
    public static double rate(Status status) {
        return switch (status) { case STOPPED -> 0; case WEAK -> RAIN_RATE; case STRONG -> CLEAR_RATE; };
    }
    public static double sanitize(double energy) {
        return Double.isFinite(energy) ? Math.max(0, Math.min(CAPACITY, energy)) : 0;
    }
    public static double generation(double required, Status status) {
        return Double.isFinite(required) && required > 0 ? Math.min(required, rate(status)) : 0;
    }
    public static double chargeRequest(double energy) { return Math.min(sanitize(energy), BANDWIDTH); }
    /** Four 16-bit native menu-data words preserve the finite IF double on the short-valued wire. */
    public static int word(double energy, int index) {
        if (index < 0 || index >= 4) throw new IndexOutOfBoundsException(index);
        return (int) ((Double.doubleToLongBits(sanitize(energy)) >>> (index * 16)) & 65535L);
    }
    public static double fromWords(int a, int b, int c, int d) {
        long bits = (a & 65535L) | ((b & 65535L) << 16) | ((c & 65535L) << 32) | ((d & 65535L) << 48);
        return sanitize(Double.longBitsToDouble(bits));
    }
}

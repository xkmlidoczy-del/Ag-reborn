package cn.academy.port.highpower.core;

/** A bounded, skill-owned cosmetic camera clock. No Minecraft state or aim changes. */
public final class CastCameraTimeline {
    public static final int MIN_LEVEL = 4, RELEASE_TICKS = 30, MAX_HOLD_TICKS = 1200;
    private String skill = "", category = "";
    private double power;
    private int age = -1, charge, released = -1;
    private boolean holding;
    public record View(double fov, double pitch, double roll) {
        public static final View NONE = new View(1, 0, 0);
    }
    public void clear() { skill = category = ""; power = 0; age = -1; charge = 0; released = -1; holding = false; }
    public boolean active() { return age >= 0; }
    public boolean holds(String id) { return active() && holding && skill.equals(id); }
    public void cast(String id, String cat, int level, double strength, boolean held) {
        clear();
        if (id == null || id.isEmpty() || cat == null || level < MIN_LEVEL || !Double.isFinite(strength) || strength <= 0) return;
        skill = id; category = cat; power = (level >= 5 ? 1 : .7) * Math.min(1, strength);
        age = 0; holding = held; released = held ? -1 : 0;
    }
    public void release(String id) {
        if (!holds(id)) return;
        holding = false; released = age;
    }
    public void cancel(String id) { if (id != null && skill.equals(id)) clear(); }
    public void tick() {
        if (!active()) return;
        age++;
        if (holding) charge = Math.min(40, charge + 1);
        if ((!holding && age - released > RELEASE_TICKS) || age > MAX_HOLD_TICKS) clear();
    }
    public View sample(double partial) {
        if (!active() || !Double.isFinite(partial)) return View.NONE;
        partial = Math.max(0, Math.min(1, partial));
        double r = released < 0 ? -1 : age - released + partial;
        double c = Math.min(1, (charge + (holding ? partial : 0)) / 20.0);
        double fov = 1, pitch = 0, roll = 0;
        switch (category) {
            case "electromaster" -> {
                fov = r < 0 ? 1 - .04 * c : 1 + .07 * Math.exp(-r / 4) - .04 * c * Math.exp(-r / 2);
                if (r >= 0) { pitch = -.8 * Math.exp(-r / 3); roll = .65 * Math.sin(r * 4.1) * Math.exp(-r / 4); }
            }
            case "meltdowner" -> {
                fov = r < 0 ? 1 - .09 * c : 1 - .09 * c * Math.exp(-r / 3) + .05 * Math.exp(-r / 6) * Math.min(1, r / 2);
                if (r >= 0) pitch = -2.2 * Math.exp(-r / 5) * Math.min(1, r * 1.5);
            }
            case "teleporter" -> {
                fov = r < 0 ? 1 : 1 + .14 * Math.exp(-r / 2.2) * Math.min(1, r * 2);
                if (r >= 0) roll = 2.0 * Math.sin(r * .9) * Math.exp(-r / 4);
            }
            case "vecmanip" -> {
                fov = r < 0 ? 1 - .03 * c : 1 + .08 * Math.exp(-r / 8);
                if (r >= 0) roll = 3.0 * Math.exp(-r / 9) * Math.min(1, r / 2);
            }
            default -> { return View.NONE; }
        }
        // Reach exactly neutral before the clock is retired, avoiding a last-frame snap.
        double tail = r < 0 ? 1 : Math.min(1, Math.max(0, (RELEASE_TICKS - r) / 6));
        return new View(1 + (fov - 1) * power * tail, pitch * power * tail, roll * power * tail);
    }
}

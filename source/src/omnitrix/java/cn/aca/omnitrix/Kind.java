package cn.aca.omnitrix;

public enum Kind {
    CLASSIC("omnitrix", 18, 2400, 900),
    CELESTIAL("alien_x_watch", 48, 3600, 1200),
    CARNITRIX("carnitrix", 26, 1200, 1800);

    public final String id;
    public final int windup, duration, recharge;

    Kind(String id, int windup, int duration, int recharge) {
        this.id = id;
        this.windup = windup;
        this.duration = duration;
        this.recharge = recharge;
    }

    public static Kind at(int index) {
        return index >= 0 && index < values().length ? values()[index] : null;
    }
}

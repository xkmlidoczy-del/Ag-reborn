package cn.academy.port.client;

import java.util.ArrayList;
import java.util.List;

/** Three stationary cosmetic traces, with absolute server expiry and a local lease as a second fence. */
public final class BeamStepPlateState {
    public static final int MAX_PLATES = 3, LIFE_TICKS = 20;
    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }
    public record Plate(long token, long epoch, Point at, long issued, long expires, int age) {}
    private final List<Plate> plates = new ArrayList<>(MAX_PLATES);
    private long maxToken;
    public boolean accept(long token, long epoch, Point at, long issued, long expires, long now) {
        return token > maxToken && acceptApplied(token,epoch,at,issued,expires,now);
    }
    /** The feedback rendezvous has already consumed this outcome once; valid packet ordering may differ across tokens. */
    boolean acceptApplied(long token, long epoch, Point at, long issued, long expires, long now) {
        return acceptApplied(token,epoch,at,issued,expires,now,0);
    }
    boolean acceptApplied(long token, long epoch, Point at, long issued, long expires, long now, int minimumAge) {
        if (token <= 0 || epoch <= 0 || at == null || !at.finite() || plates.stream().anyMatch(p -> p.token()==token)
                || minimumAge<0 || minimumAge>=LIFE_TICKS
                || !BeamStepReceiptState.fresh(issued, expires, now, LIFE_TICKS) || now >= expires) return false;
        maxToken = Math.max(maxToken,token);
        if (plates.size() == MAX_PLATES) plates.removeFirst();
        plates.add(new Plate(token, epoch, at, issued, expires, (int)Math.max(minimumAge,Math.max(0, now - issued))));
        return true;
    }
    public void terminal(long token) { maxToken = Math.max(maxToken, token); plates.removeIf(p -> p.token() == token); }
    public void tick(long now, boolean paused) {
        if (!paused) plates.replaceAll(p -> new Plate(p.token(), p.epoch(), p.at(), p.issued(), p.expires(), p.age() + 1));
        plates.removeIf(p -> p.age() >= LIFE_TICKS || now >= p.expires());
    }
    public List<Plate> plates() { return List.copyOf(plates); }
    public void clear() { plates.clear(); }
    public void resetConnection() { clear(); maxToken = 0; }
    public static float alpha(float age) {
        if (!Float.isFinite(age) || age < 0 || age >= LIFE_TICKS) return 0;
        return Math.min(1, (LIFE_TICKS - age) / 8F);
    }
    /** A small chamfered footprint, never a collision shape or an opaque circular floor. */
    public static List<Point> corners(Point center) {
        if (center == null || !center.finite()) return List.of();
        return List.of(new Point(center.x()-.44,center.y(),center.z()-.16),
                new Point(center.x()-.30,center.y(),center.z()-.27),new Point(center.x()+.30,center.y(),center.z()-.27),
                new Point(center.x()+.44,center.y(),center.z()-.16),new Point(center.x()+.44,center.y(),center.z()+.16),
                new Point(center.x()+.30,center.y(),center.z()+.27),new Point(center.x()-.30,center.y(),center.z()+.27),
                new Point(center.x()-.44,center.y(),center.z()+.16));
    }
}

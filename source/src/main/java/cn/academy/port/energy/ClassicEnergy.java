/* AcademyCraft 1.0.7 IFItemManager arithmetic adaptation. GPLv3. See NOTICE. */
package cn.academy.port.energy;

/** Source IF amounts stay double-valued. Corrupt/negative storage cannot create or hide energy. */
public final class ClassicEnergy {
    public static final double ENERGY_UNIT_CAPACITY = 10000, ENERGY_UNIT_BANDWIDTH = 20;
    public static final int GAUGE_DAMAGE = 13;
    private ClassicEnergy() {}

    public interface Access {
        double energy();
        void energy(double amount);
        double capacity();
        double bandwidth();
        default boolean available() { return true; }
    }

    public static double sanitize(double energy, double capacity) {
        return Double.isFinite(energy) && Double.isFinite(capacity) && capacity > 0
                ? Math.max(0, Math.min(capacity, energy)) : 0;
    }

    /** Returns energy NOT transferred. Signed negative charging discharges, as in the source. */
    public static double charge(Access storage, double amount, boolean ignoreBandwidth) {
        if (!storage.available() || !Double.isFinite(amount)) return amount;
        double current = sanitize(storage.energy(), storage.capacity());
        double bound = amount >= 0 ? Math.min(amount, storage.capacity() - current)
                : -Math.min(-amount, current);
        double limit = ignoreBandwidth ? Double.MAX_VALUE : finiteBandwidth(storage.bandwidth());
        double moved = Math.copySign(Math.min(Math.abs(bound), limit), bound);
        storage.energy(current + moved);
        return amount - moved;
    }

    /** Returns actual IF removed; an underpowered pull drains the remainder, not an atomic check. */
    public static double pull(Access storage, double amount, boolean ignoreBandwidth) {
        if (!storage.available() || !Double.isFinite(amount) || amount < 0) return 0;
        double moved = Math.min(amount, sanitize(storage.energy(), storage.capacity()));
        if (!ignoreBandwidth) moved = Math.min(moved, finiteBandwidth(storage.bandwidth()));
        storage.energy(sanitize(storage.energy(), storage.capacity()) - moved);
        return moved;
    }

    static double finiteBandwidth(double bandwidth) {
        return Double.isFinite(bandwidth) ? Math.max(0, bandwidth) : 0;
    }

    /** IFItemManager.setEnergy uses Math.round, not a truncating item-property approximation. */
    public static int damage(double energy, double capacity) {
        double bounded = sanitize(energy, capacity);
        return capacity <= 0 || !Double.isFinite(capacity) ? GAUGE_DAMAGE
                : (int) Math.round((1 - bounded / capacity) * GAUGE_DAMAGE);
    }

    /** ItemEnergyBase selects full for damage<3, empty for damage>10, otherwise half. */
    public static int iconLevel(double energy, double capacity) {
        int damage = damage(energy, capacity);
        return damage < 3 ? 2 : damage > 10 ? 0 : 1;
    }
}

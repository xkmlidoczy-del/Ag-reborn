/* AcademyCraft IFItemManager energy arithmetic, adapted under GPLv3. See NOTICE. */
package cn.academy.port.develop;

/** Finite double-valued IF storage operations, shared by items and standalone regression tests. */
public final class DeveloperEnergy {
    private DeveloperEnergy() {}

    public interface Access {
        double energy();
        void energy(double value);
        DeveloperType type();
        /** A retained item capability becomes unavailable if its backing stack is emptied. */
        default boolean available() { return true; }
    }

    public static double sanitize(double energy, DeveloperType type) {
        return Double.isFinite(energy) ? Math.max(0, Math.min(type.energy, energy)) : 0;
    }

    /** Returns energy NOT transferred, as in IFItemManager. Negative amount discharges. */
    public static double charge(Access storage, double amount, boolean ignoreBandwidth) {
        if (!storage.available() || !Double.isFinite(amount)) return amount;
        double current = sanitize(storage.energy(), storage.type());
        double bounded = amount >= 0 ? Math.min(amount, storage.type().energy - current)
                : -Math.min(-amount, current);
        double moved = Math.copySign(Math.min(Math.abs(bounded),
                ignoreBandwidth ? Double.MAX_VALUE : storage.type().bandwidth), bounded);
        storage.energy(current + moved);
        return amount - moved;
    }

    /** Returns actual energy removed. Unlike an atomic check, an underpowered pull drains the remainder. */
    public static double pull(Access storage, double amount, boolean ignoreBandwidth) {
        if (!storage.available() || !Double.isFinite(amount) || amount < 0) return 0;
        double current = sanitize(storage.energy(), storage.type());
        double removed = Math.min(amount, current);
        if (!ignoreBandwidth) removed = Math.min(removed, storage.type().bandwidth);
        storage.energy(current - removed);
        return removed;
    }

    public static boolean tryPull(Access storage, double amount) {
        return storage.available() && Double.isFinite(amount) && amount >= 0 && pull(storage, amount, true) == amount;
    }
}

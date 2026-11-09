package cn.academy.port.skill;

import net.neoforged.neoforge.energy.IEnergyStorage;

/** Deterministic finite FE conversion only; native item/block capability lookup is tested in GameTests. */
public final class ChargingEnergyRegressionTest {
    private static int assertions;
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
    private static void close(double actual, double expected, String label) {
        check(Math.abs(actual - expected) < .000001, label + ": expected " + expected + ", got " + actual);
    }
    private static final class Storage implements IEnergyStorage {
        int energy, capacity = 1000, bandwidth = 1000, calls;
        boolean receives = true, simulated;
        @Override public int receiveEnergy(int max, boolean simulate) {
            calls++; simulated |= simulate;
            int moved = Math.min(max, Math.min(bandwidth, capacity - energy));
            if (!simulate) energy += moved;
            return moved;
        }
        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return capacity; }
        @Override public boolean canReceive() { return receives; }
        @Override public boolean canExtract() { return false; }
    }
    public static void main(String[] args) {
        var s = new Storage();
        close(ChargingEnergy.chargeForge(s, 15), 0, "15 IF fully transfers");
        check(s.energy == 60 && s.calls == 1 && !s.simulated, "one real receive of 60 FE");
        s = new Storage(); s.bandwidth = 11;
        close(ChargingEnergy.chargeForge(s, 35), 32.25, "bandwidth respected with fractional IF remainder");
        check(s.energy == 11 && s.calls == 1, "no repeated receive to bypass bandwidth");
        s = new Storage(); s.energy = 999;
        close(ChargingEnergy.chargeForge(s, 15), 14.75, "finite capacity respected");
        check(s.energy == 1000, "no capacity overflow");
        close(ChargingEnergy.chargeForge(s, 15), 15, "full supported storage rejects transfer");
        s = new Storage(); s.receives = false;
        close(ChargingEnergy.chargeForge(s, 15), 15, "extract-only supported storage gets no transfer");
        check(s.calls == 0 && s.energy == 0, "canReceive false does not invoke receiver");
        close(ChargingEnergy.chargeForge(null, 15), 15, "missing capability retains all IF");
        s = new Storage();
        close(ChargingEnergy.chargeForge(s, .24), .24, "less than one FE is retained");
        check(s.calls == 0, "sub-FE amount invokes no transfer");
        close(ChargingEnergy.chargeForge(s, .375), .125, "fraction truncation preserves remainder");
        check(s.energy == 1, "conversion truncates to integer FE");
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            int calls = s.calls;
            ChargingEnergy.chargeForge(s, invalid);
            check(s.calls == calls, "invalid amount invokes no receive " + invalid);
        }
        s = new Storage(); s.capacity = Integer.MAX_VALUE; s.bandwidth = Integer.MAX_VALUE;
        close(ChargingEnergy.chargeForge(s, Double.MAX_VALUE), Double.MAX_VALUE, "huge finite amount safely returns enormous remainder");
        check(s.energy == Integer.MAX_VALUE, "large request saturates without integer overflow");
        for (int i = 0; i <= 400; i++) {
            s = new Storage(); s.capacity = i; s.bandwidth = i / 2;
            double remaining = ChargingEnergy.chargeForge(s, 35);
            close(35 - remaining, s.energy / 4.0, "energy conservation " + i);
            check(s.energy >= 0 && s.energy <= s.capacity && s.energy <= s.bandwidth, "finite bounds " + i);
            check(s.calls == 1 && !s.simulated, "single transfer " + i);
        }
        System.out.println("PASS " + assertions + " finite Charging FE assertions");
    }
}

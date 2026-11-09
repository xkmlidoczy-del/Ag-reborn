package cn.academy.port.develop;

import net.minecraft.nbt.CompoundTag;

/** Energy arithmetic, CUSTOM_DATA payload round trips and FE contracts; no Minecraft launch. */
public final class DeveloperEnergyRegressionTest {
    private static int assertions;
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static void eq(double expected, double actual, String message) { check(Math.abs(expected - actual) < 1e-8, message); }
    private static final class Storage implements DeveloperEnergy.Access {
        private double energy;
        private final DeveloperType type;
        private boolean available = true;
        Storage(DeveloperType type) { this.type = type; }
        public DeveloperType type() { return type; }
        public double energy() { return energy; }
        public void energy(double amount) { energy = DeveloperEnergy.sanitize(amount, type); }
        public boolean available() { return available; }
    }

    public static void main(String[] args) {
        for (var tier : DeveloperType.values()) {
            var storage = new Storage(tier);
            eq(0, storage.energy(), "new storage is empty");
            eq(1000 - tier.bandwidth, DeveloperEnergy.charge(storage, 1000, false), "charging obeys bandwidth");
            eq(tier.bandwidth, storage.energy(), "charge moved bandwidth");
            eq(tier.bandwidth, DeveloperEnergy.pull(storage, 1000, false), "pull obeys bandwidth");
            eq(0, storage.energy(), "empty after bounded pull");
            eq(999999 - tier.energy, DeveloperEnergy.charge(storage, 999999, true), "overcapacity returns surplus");
            eq(tier.energy, storage.energy(), "finite capacity");
            eq(-1000 + tier.bandwidth, DeveloperEnergy.charge(storage, -1000, false), "negative charge bounded discharge");
            eq(tier.energy - tier.bandwidth, storage.energy(), "negative charge drains");
            storage.energy(2.5); check(!DeveloperEnergy.tryPull(storage, 30), "partial pull fails"); eq(0, storage.energy(), "partial pull drains remainder");
            storage.energy(-1); eq(0, storage.energy(), "negative stored data bounded");
            storage.energy(Double.NaN); eq(0, storage.energy(), "NaN stored data bounded");
            storage.energy(Double.POSITIVE_INFINITY); eq(0, storage.energy(), "infinity stored data bounded");
            storage.energy(tier.energy + 1); eq(tier.energy, storage.energy(), "capacity bound");
            check(!DeveloperEnergy.tryPull(storage, Double.NaN), "NaN consumption rejected");
            check(!DeveloperEnergy.tryPull(storage, -30), "negative consumption rejected");
            eq(tier.energy, storage.energy(), "invalid consumption does not mutate");

            var tag = new CompoundTag(); tag.putString("another_mod", "preserved");
            eq(0, DeveloperItemEnergy.read(tag, tier), "absent CUSTOM_DATA energy is empty");
            DeveloperItemEnergy.write(tag, tier, 1234.75);
            var copied = tag.copy(); eq(1234.75, DeveloperItemEnergy.read(copied, tier), "double energy payload round trip");
            check(copied.getString("another_mod").equals("preserved"), "unrelated custom data retained");
            DeveloperItemEnergy.write(copied, tier, 1); eq(1234.75, DeveloperItemEnergy.read(tag, tier), "stack-copy payload independence");
            DeveloperItemEnergy.write(copied, tier, Double.NaN); eq(0, DeveloperItemEnergy.read(copied, tier), "corrupted energy emptied");
            DeveloperItemEnergy.write(copied, tier, tier.energy + 1); eq(tier.energy, DeveloperItemEnergy.read(copied, tier), "component capacity clamped");

            storage.energy(0); var fe = new DeveloperForgeEnergyStorage(storage);
            check(fe.canReceive() && fe.canExtract(), "FE declares both directions");
            eq(tier.energy * 4, fe.getMaxEnergyStored(), "source 1 IF = 4 RF/FE conversion");
            eq(tier.bandwidth * 4, fe.receiveEnergy(999999, true), "simulate receive bandwidth");
            eq(0, storage.energy(), "simulate receive never mutates");
            eq(tier.bandwidth * 4, fe.receiveEnergy(999999, false), "real receive bandwidth");
            eq(tier.bandwidth, storage.energy(), "real FE receive stores IF");
            eq(1, fe.extractEnergy(1, true), "simulate fractional IF extract");
            eq(tier.bandwidth, storage.energy(), "simulate extraction never mutates");
            eq(1, fe.extractEnergy(1, false), "fractional IF extraction");
            eq(tier.bandwidth - .25, storage.energy(), "FE fraction preserved");
            eq(tier.bandwidth * 4 - 1, fe.getEnergyStored(), "FE view floors double units");
            storage.energy(tier.energy - .125); eq(0, fe.receiveEnergy(1, false), "cannot fit a whole FE");
            eq(tier.energy - .125, storage.energy(), "sub-FE remainder retained");
            storage.energy(.125); eq(0, fe.extractEnergy(1, false), "cannot extract half FE"); eq(.125, storage.energy(), "sub-FE remaining IF preserved");
            eq(0, fe.receiveEnergy(-1, false), "negative FE input rejected"); eq(0, fe.extractEnergy(-1, false), "negative FE extract rejected");
            storage.energy(0); eq(1, fe.receiveEnergy(1, false), "accept smallest FE"); eq(.25, storage.energy(), "smallest FE equals .25 IF");
            storage.energy(tier.energy); eq(0, fe.receiveEnergy(100, false), "full storage receives zero");
            eq(tier.bandwidth * 4, fe.extractEnergy(Integer.MAX_VALUE, false), "extraction capped by bandwidth");
            double energyBeforeUnavailable = storage.energy(); storage.available = false;
            eq(0, fe.receiveEnergy(100, false), "emptied backing item cannot receive phantom energy");
            eq(0, fe.extractEnergy(100, false), "emptied backing item cannot extract");
            eq(0, fe.getEnergyStored(), "unavailable capability has no stored energy");
            eq(0, fe.getMaxEnergyStored(), "unavailable capability has no capacity");
            check(!fe.canReceive() && !fe.canExtract(), "unavailable capability declares neither transfer direction");
            eq(energyBeforeUnavailable, storage.energy(), "unavailable capability never mutates underlying data");
            eq(100, DeveloperEnergy.charge(storage, 100, true), "unavailable IF storage returns full charge remainder");
            check(!DeveloperEnergy.tryPull(storage, 0), "unavailable IF storage cannot satisfy even a zero pull");
        }
        System.out.println("PASS " + assertions + " finite developer energy/component/FE assertions");
    }
}

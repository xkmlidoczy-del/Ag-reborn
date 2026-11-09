package cn.academy.port.energy;

import cn.academy.port.develop.DeveloperEnergy;
import cn.academy.port.develop.DeveloperForgeEnergyStorage;
import cn.academy.port.develop.DeveloperType;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;

/** Deterministic finite IF, signed remainders, corrupt payload and real IEnergyStorage contract checks. */
public final class ClassicEnergyRegressionTest {
    private static int assertions;
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static void close(double expected, double actual, String message) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) < 1e-8, message + ": " + expected + " != " + actual);
    }
    private static final class Storage implements ClassicEnergy.Access {
        double energy;
        boolean available = true;
        public double energy() { return energy; }
        public void energy(double amount) { energy = ClassicEnergy.sanitize(amount, capacity()); }
        public double capacity() { return ClassicEnergy.ENERGY_UNIT_CAPACITY; }
        public double bandwidth() { return ClassicEnergy.ENERGY_UNIT_BANDWIDTH; }
        public boolean available() { return available; }
    }
    private static final class Developer implements DeveloperEnergy.Access {
        double energy;
        public double energy() { return energy; }
        public void energy(double amount) { energy = DeveloperEnergy.sanitize(amount, type()); }
        public DeveloperType type() { return DeveloperType.PORTABLE; }
    }
    public static void main(String[] args) {
        var storage = new Storage();
        close(0, storage.energy(), "default empty");
        close(15.125, ClassicEnergy.charge(storage, 35.125, false), "charge leftover from 20IF bandwidth");
        close(20, storage.energy(), "native charge accepts source bandwidth");
        close(20, ClassicEnergy.pull(storage, 999, false), "pull capped by bandwidth");
        close(0, storage.energy(), "pulled energy conserved");
        close(990000, ClassicEnergy.charge(storage, 1000000, true), "full item returns overcapacity surplus");
        close(10000, storage.energy(), "finite capacity");
        close(-79.5, ClassicEnergy.charge(storage, -99.5, false), "signed discharge remainder");
        close(9980, storage.energy(), "signed charge discharges at source bandwidth");
        storage.energy(3.125);
        close(-96.875, ClassicEnergy.charge(storage, -100, true), "negative request cannot underflow storage");
        close(0, storage.energy(), "negative source underflow bug hardened");
        storage.energy(7.375); close(7.375, ClassicEnergy.pull(storage, 100, true), "underpowered pull drains actual remainder");
        close(0, storage.energy(), "partial pull leaves empty");
        storage.energy(9999.875);
        close(9.875, ClassicEnergy.charge(storage, 10, false), "capacity clips fractional incoming IF");
        close(10000, storage.energy(), "fractional incoming reaches exact capacity");
        for (double corrupt : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1, -Double.MAX_VALUE})
            close(0, ClassicEnergy.sanitize(corrupt, 10000), "corrupt/negative saved energy empties");
        close(10000, ClassicEnergy.sanitize(Double.MAX_VALUE, 10000), "huge finite save clamps");
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            double before = storage.energy();
            check(Double.doubleToLongBits(ClassicEnergy.charge(storage, invalid, false)) == Double.doubleToLongBits(invalid), "invalid charge returned untouched");
            close(0, ClassicEnergy.pull(storage, invalid, true), "invalid pull rejected");
            close(before, storage.energy(), "invalid operations do not mutate");
        }
        close(0, ClassicEnergy.pull(storage, -1, true), "negative pull cannot generate energy");

        var tag = new CompoundTag(); tag.putString("other_mod", "preserved");
        close(0, ClassicItemEnergy.read(tag, 10000), "missing native component is empty");
        ClassicItemEnergy.write(tag, 10000, 1234.625);
        var copy = tag.copy(); close(1234.625, ClassicItemEnergy.read(copy, 10000), "fractional custom-data round trip");
        ClassicItemEnergy.write(copy, 10000, 9.75); close(1234.625, ClassicItemEnergy.read(tag, 10000), "copy is independent");
        check(copy.getString("other_mod").equals("preserved"), "foreign component fields preserved");
        for (double corrupt : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -9}) {
            copy.putDouble(ClassicItemEnergy.KEY, corrupt); close(0, ClassicItemEnergy.read(copy, 10000), "corrupt serialized energy read safely");
            ClassicItemEnergy.write(copy, 10000, corrupt); close(0, copy.getDouble(ClassicItemEnergy.KEY), "corrupt serialized write safely");
        }
        copy.putString(ClassicItemEnergy.KEY, "not energy"); close(0, ClassicItemEnergy.read(copy, 10000), "wrong-type saved payload is empty");
        ClassicItemEnergy.write(copy, 10000, 10001); close(10000, ClassicItemEnergy.read(copy, 10000), "component clamps capacity");

        // Canonical rounding determines thresholds; source comparisons apply to rounded damage.
        close(13, ClassicEnergy.damage(0, 10000), "empty damage13");
        close(0, ClassicEnergy.damage(10000, 10000), "full damage0");
        close(7, ClassicEnergy.damage(5000, 10000), "half rounds6.5 up, does not truncate");
        close(0, ClassicEnergy.iconLevel(0, 10000), "empty icon");
        close(1, ClassicEnergy.iconLevel(5000, 10000), "half icon");
        close(2, ClassicEnergy.iconLevel(10000, 10000), "full icon");
        for (int i = 0; i <= 40000; i++) {
            double energy = i / 4.0;
            int expectedDamage = (int) Math.round((1 - energy / 10000) * 13);
            check(ClassicEnergy.damage(energy, 10000) == expectedDamage, "source rounded gauge at " + i);
            check(ClassicEnergy.iconLevel(energy, 10000) == (expectedDamage < 3 ? 2 : expectedDamage > 10 ? 0 : 1), "source icon threshold at " + i);
        }
        for (double boundary : new double[] {10000 * (1 - 10.5 / 13), 10000 * (1 - 2.5 / 13)})
            for (double energy : new double[] {Math.nextDown(boundary), boundary, Math.nextUp(boundary), boundary - .000001, boundary + .000001}) {
                int damage = (int) Math.round((1 - energy / 10000) * 13);
                check(ClassicEnergy.iconLevel(energy, 10000) == (damage < 3 ? 2 : damage > 10 ? 0 : 1), "adjacent double visual threshold");
            }

        var fe = new ClassicForgeEnergyStorage(storage); storage.energy(0);
        close(40000, fe.getMaxEnergyStored(), "native capability capacity converts4:1");
        check(fe.canReceive() && fe.canExtract(), "both capability directions");
        close(80, fe.receiveEnergy(Integer.MAX_VALUE, true), "simulate receive capped at80FE"); close(0, storage.energy(), "receive simulation is read only");
        close(80, fe.receiveEnergy(Integer.MAX_VALUE, false), "actual receive capped at80FE"); close(20, storage.energy(), "80FE becomes20IF");
        close(1, fe.extractEnergy(1, true), "simulate smallest extraction"); close(20, storage.energy(), "extract simulation is read only");
        close(1, fe.extractEnergy(1, false), "extract one FE"); close(19.75, storage.energy(), "extraction preserves fractional IF");
        storage.energy(9999.875); close(0, fe.receiveEnergy(1, false), "sub-FE capacity cannot receive1FE"); close(9999.875, storage.energy(), "sub-FE headroom preserved");
        storage.energy(.125); close(0, fe.extractEnergy(1, false), "sub-FE stored amount cannot create1FE"); close(.125, storage.energy(), "sub-FE residual preserved");
        close(0, fe.receiveEnergy(-1, false), "negative FE receive rejected"); close(0, fe.extractEnergy(-1, false), "negative FE extract rejected");
        storage.energy(10000); close(80, fe.extractEnergy(Integer.MAX_VALUE, false), "extract bandwidth capped80FE");
        double old = storage.energy(); storage.available = false;
        close(0, fe.receiveEnergy(100, false), "retained unavailable capability cannot receive");
        close(0, fe.extractEnergy(100, false), "retained unavailable capability cannot extract");
        close(0, fe.getEnergyStored(), "unavailable capability no phantom stored energy");
        close(0, fe.getMaxEnergyStored(), "unavailable capability no phantom capacity");
        check(!fe.canReceive() && !fe.canExtract(), "unavailable capability flags");
        close(12.25, ClassicEnergy.charge(storage, 12.25, true), "unavailable native returns request");
        close(0, ClassicEnergy.pull(storage, 50, true), "unavailable native cannot extract"); close(old, storage.energy(), "unavailable operations never mutate");
        storage.available = true;

        var random = new Random(107123);
        for (int i = 0; i < 20000; i++) {
            storage.energy(random.nextDouble() * 10000);
            double before = storage.energy(), amount = (random.nextDouble() * 2 - 1) * 20000;
            boolean ignore = random.nextBoolean();
            double left = ClassicEnergy.charge(storage, amount, ignore), moved = storage.energy() - before;
            close(amount, moved + left, "signed IF charge conservation");
            check(storage.energy() >= 0 && storage.energy() <= 10000 && (ignore || Math.abs(moved) <= 20 + 1e-10), "finite native bandwidth/capacity invariant");
            before = storage.energy(); int request = random.nextInt(100000); boolean simulation = random.nextBoolean();
            int accepted = fe.receiveEnergy(request, simulation);
            close(simulation ? before : before + accepted / 4.0, storage.energy(), "FE receive conservation/simulation");
            check(accepted >= 0 && accepted <= Math.min(80, request), "FE receive bandwidth/request invariant");
            before = storage.energy(); int removed = fe.extractEnergy(request, simulation);
            close(simulation ? before : before - removed / 4.0, storage.energy(), "FE extraction conservation/simulation");
            check(removed >= 0 && removed <= Math.min(80, request), "FE extraction bandwidth/request invariant");
        }

        // Actual IF extraction/FE injection across two finite adapters conserves units.
        var developer = new Developer(); var developerFe = new DeveloperForgeEnergyStorage(developer);
        storage.energy(100); developer.energy(0);
        int extracted = fe.extractEnergy(Integer.MAX_VALUE, false);
        int accepted = developerFe.receiveEnergy(extracted, false);
        close(80, extracted, "unit outgoing80FE"); close(extracted, accepted, "developer receives same FE");
        close(100, storage.energy() + developer.energy(), "unit-to-portable transfer conservation");
        close(20, developer.energy(), "developer receives20IF, not80IF");
        System.out.println("PASS " + assertions + " classic energy unit finite IF/component/FE/visual assertions");
    }
}

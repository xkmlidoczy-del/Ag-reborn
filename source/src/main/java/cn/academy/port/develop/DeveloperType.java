/* Classic AcademyCraft 1.0.7 development constants, adapted under GPLv3. See NOTICE. */
package cn.academy.port.develop;

/** Source IF units, not CP. Bandwidth limits charging, not stimulation consumption. */
public enum DeveloperType {
    PORTABLE(50, .3, 10000, 25, 750),
    NORMAL(100, .7, 50000, 20, 700),
    ADVANCED(300, 1, 200000, 15, 600);

    public final double bandwidth, syncRate, energy, cps;
    public final int tps;

    DeveloperType(double bandwidth, double syncRate, double energy, int tps, double cps) {
        this.bandwidth = bandwidth;
        this.syncRate = syncRate;
        this.energy = energy;
        this.tps = tps;
        this.cps = cps;
    }

    public double energyPerTick() { return cps / tps; }
    public int ticksPerStimulation() { return tps + 1; }
    public double actualEnergyPerStimulation() { return energyPerTick() * ticksPerStimulation(); }
    public double estimatedConsumption(int stimulations) { return cps * stimulations; }
    public double actualConsumption(int stimulations) { return actualEnergyPerStimulation() * stimulations; }

    public static DeveloperType minimumForSkill(int level) {
        return level <= 2 ? PORTABLE : level <= 3 ? NORMAL : ADVANCED;
    }

    public boolean supportsSkill(int level) { return ordinal() >= minimumForSkill(level).ordinal(); }
}

/* AcademyCraft 1.0.7 TileGeneratorBase finite buffer, GPLv3. See NOTICE. */
package cn.academy.port.solar;

/** A server-owned buffer. Charge callbacks return untransferred IF, never guessed accepted power. */
public final class ClassicSolarBuffer {
    @FunctionalInterface public interface Charger { double charge(double request); }
    private double energy;
    public double energy() { return energy; }
    public void load(double amount) { energy = ClassicSolarRules.sanitize(amount); }
    public double addEnergy(double amount, boolean simulate) {
        if (!Double.isFinite(amount) || amount <= 0) return amount;
        double accepted = Math.min(ClassicSolarRules.CAPACITY - energy, amount);
        if (!simulate) energy += accepted;
        return amount - accepted;
    }
    /** Source generator API does not itself enforce bandwidth; its wireless caller must do so. */
    public double getProvidedEnergy(double request) {
        if (!Double.isFinite(request) || request <= 0) return 0;
        double provided = Math.min(request, energy);
        energy -= provided;
        return provided;
    }
    public double tick(ClassicSolarRules.Status status, Charger charger) {
        double before = energy;
        energy += ClassicSolarRules.generation(ClassicSolarRules.CAPACITY - energy, status);
        if (charger != null && energy > 0) {
            double request = ClassicSolarRules.chargeRequest(energy);
            double remainder = charger.charge(request);
            if (Double.isFinite(remainder)) energy -= Math.max(0, Math.min(request, request - remainder));
        }
        energy = ClassicSolarRules.sanitize(energy);
        return energy - before;
    }
}

/* AcademyCraft 1.0.7 ImagEnergyItem interface adaptation. GPLv3. See NOTICE. */
package cn.academy.port.energy;

/** Native, fractional imaginary-flux item support; existence is independent of fullness. */
public interface ImagEnergyItem {
    double getMaxEnergy();
    double getBandwidth();
}

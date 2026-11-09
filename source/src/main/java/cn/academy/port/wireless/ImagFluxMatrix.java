/* AcademyCraft 1.0.7 IWirelessMatrix adaptation, GPLv3. See NOTICE. */
package cn.academy.port.wireless;

/** Source matrix limits; only the canonical multiblock origin advertises a matrix. */
public interface ImagFluxMatrix {
    int getCapacity();
    double getBandwidth();
    double getRange();
    default boolean isWirelessOrigin() { return true; }
}

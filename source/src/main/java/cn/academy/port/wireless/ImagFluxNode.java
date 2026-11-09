/* AcademyCraft 1.0.7 IWirelessNode adaptation, GPLv3. See NOTICE. */
package cn.academy.port.wireless;

/** A native, finite IF store. The graph never owns or invents node energy. */
public interface ImagFluxNode {
    double getMaxEnergy();
    double getEnergy();
    void setEnergy(double value);
    double getBandwidth();
    int getCapacity();
    double getRange();
    String getNodeName();
    String getPassword();
}

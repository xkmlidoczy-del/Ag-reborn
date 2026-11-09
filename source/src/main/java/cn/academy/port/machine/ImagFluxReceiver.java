package cn.academy.port.machine;

/** Source IWirelessReceiver arithmetic seam, in IF. Networks must respect getBandwidth themselves. */
public interface ImagFluxReceiver {
    double getEnergy();
    double getMaxEnergy();
    double getRequiredEnergy();
    double getBandwidth();
    /** Returns energy NOT accepted, as in source TileReceiverBase. */
    double injectEnergy(double amount);
    /** Returns actual energy removed; development uses a separate atomic check. */
    double pullEnergy(double amount);
}

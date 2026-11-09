package cn.academy.port.solar;

/** Source IWirelessGenerator seam only. A future real wireless network must respect bandwidth. */
public interface ImagFluxGenerator {
    double getEnergy();
    double getBandwidth();
    double getProvidedEnergy(double request);
}

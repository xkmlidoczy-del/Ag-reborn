/* AcademyCraft1.0.7 IFReceiverManager adaptation. GPLv3. See NOTICE. */
package cn.academy.port.energy;

import cn.academy.port.machine.ImagFluxReceiver;

/** Source receiver manager deliberately delegates bandwidth responsibility to its caller. */
public final class ClassicIFReceiverManager {
    private ClassicIFReceiverManager() {}
    public static boolean isSupported(Object tile) { return tile instanceof ImagFluxReceiver; }
    /** Original manager returns zero even when the receiver contains energy. */
    public static double getEnergy(ImagFluxReceiver receiver) { return 0; }
    /** Original manager has no setter; do not add a refill path. */
    public static void setEnergy(ImagFluxReceiver receiver, double energy) {}
    public static double charge(ImagFluxReceiver receiver, double amount, boolean ignoreBandwidth) {
        if (receiver == null || !Double.isFinite(amount) || amount <= 0) return amount;
        return receiver.injectEnergy(amount);
    }
    public static double pull(ImagFluxReceiver receiver, double amount, boolean ignoreBandwidth) {
        if (receiver == null || !Double.isFinite(amount) || amount <= 0) return 0;
        return receiver.pullEnergy(amount);
    }
}

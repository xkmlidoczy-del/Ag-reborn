/* AcademyCraft1.0.7 EnergyBlockHelper/IF managers adaptation. GPLv3. See NOTICE. */
package cn.academy.port.energy;

import cn.academy.port.machine.ImagFluxReceiver;
import cn.academy.port.wireless.ImagFluxNode;

/**
 * Native IF dispatch, with one selected representation per store. Original source concrete
 * nodes and receivers do not overlap these interfaces. Deterministic node-first selection
 * also prevents duplication for a foreign hybrid. The legacy aggregate charge helper
 * erroneously charges every matching manager and always returns the original amount;
 * this helper honors its documented unaccepted-IF contract instead. Current Charging
 * ignores that return, so its source transfer and support-based EXP are unchanged.
 * Generators and matrices have no native charge manager.
 */
public final class ClassicEnergyBlockHelper {
    private ClassicEnergyBlockHelper() {}
    public static boolean isSupported(Object tile) {
        return ClassicIFNodeManager.isSupported(tile) || ClassicIFReceiverManager.isSupported(tile) || cn.academy.port.bridge.ClassicForeignEnergyManager.isSupported(tile);
    }
    public static double getEnergy(Object tile) {
        if (tile instanceof ImagFluxNode node) return ClassicIFNodeManager.getEnergy(node);
        if (tile instanceof ImagFluxReceiver receiver) return ClassicIFReceiverManager.getEnergy(receiver);
        return cn.academy.port.bridge.ClassicForeignEnergyManager.getEnergy(tile);
    }
    public static void setEnergy(Object tile, double energy) {
        if (tile instanceof ImagFluxNode node) ClassicIFNodeManager.setEnergy(node, energy);
        else if (tile instanceof ImagFluxReceiver receiver) ClassicIFReceiverManager.setEnergy(receiver, energy);
    }
    public static double charge(Object tile, double amount, boolean ignoreBandwidth) {
        if (tile instanceof ImagFluxNode node) return ClassicIFNodeManager.charge(node, amount, ignoreBandwidth);
        if (tile instanceof ImagFluxReceiver receiver) return ClassicIFReceiverManager.charge(receiver, amount, ignoreBandwidth);
        return cn.academy.port.bridge.ClassicForeignEnergyManager.charge(tile, amount);
    }
    public static double pull(Object tile, double amount, boolean ignoreBandwidth) {
        if (tile instanceof ImagFluxNode node) return ClassicIFNodeManager.pull(node, amount, ignoreBandwidth);
        if (tile instanceof ImagFluxReceiver receiver) return ClassicIFReceiverManager.pull(receiver, amount, ignoreBandwidth);
        return cn.academy.port.bridge.ClassicForeignEnergyManager.pull(tile, amount);
    }
}

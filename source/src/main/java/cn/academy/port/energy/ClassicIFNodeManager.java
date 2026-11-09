/* AcademyCraft1.0.7 IFNodeManager adaptation. GPLv3. See NOTICE. */
package cn.academy.port.energy;

import cn.academy.port.wireless.ImagFluxNode;

/** Source node manager: fractional IF and per-call bandwidth, unless explicitly bypassed. */
public final class ClassicIFNodeManager {
    private ClassicIFNodeManager() {}
    public static boolean isSupported(Object tile) { return tile instanceof ImagFluxNode; }
    public static double getEnergy(ImagFluxNode node) { return node.getEnergy(); }
    public static void setEnergy(ImagFluxNode node, double energy) { node.setEnergy(energy); }
    /** Returns unaccepted IF, exactly as the original manager (not its buggy aggregate helper). */
    public static double charge(ImagFluxNode node, double amount, boolean ignoreBandwidth) {
        if (node == null || !Double.isFinite(amount) || amount <= 0) return amount;
        double accepted = Math.min(amount, Math.max(0, node.getMaxEnergy() - node.getEnergy()));
        if (!ignoreBandwidth) accepted = Math.min(Math.max(0, node.getBandwidth()), accepted);
        node.setEnergy(node.getEnergy() + accepted);
        return amount - accepted;
    }
    /** Returns removed IF. No integer FE conversion and no network ownership requirements. */
    public static double pull(ImagFluxNode node, double amount, boolean ignoreBandwidth) {
        if (node == null || !Double.isFinite(amount) || amount <= 0) return 0;
        double removed = Math.min(Math.max(0, node.getEnergy()), amount);
        if (!ignoreBandwidth) removed = Math.min(Math.max(0, node.getBandwidth()), removed);
        node.setEnergy(node.getEnergy() - removed);
        return removed;
    }
}

package cn.academy.port.energy;

import cn.academy.port.machine.ImagFluxReceiver;
import cn.academy.port.wireless.ImagFluxNode;
import java.util.Random;

/** Exact source-manager arithmetic oracle, independent of Minecraft/registry bootstrap. */
public final class ClassicNativeBlockChargingRegressionTest {
    private static int assertions;
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
    private static void close(double actual, double expected, String label) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) < 1e-8,
                label + ": expected " + expected + ", got " + actual);
    }
    private static class Node implements ImagFluxNode {
        double energy, capacity = 15000, bandwidth = 150;
        int setters;
        public double getMaxEnergy() { return capacity; }
        public double getEnergy() { return energy; }
        public void setEnergy(double value) { setters++; energy = Math.max(0, Math.min(capacity, value)); }
        public double getBandwidth() { return bandwidth; }
        public int getCapacity() { return 5; }
        public double getRange() { return 9; }
        public String getNodeName() { return "test"; }
        public String getPassword() { return ""; }
    }
    private static class Receiver implements ImagFluxReceiver {
        double energy, capacity = 2000, bandwidth = 50;
        int injections, pulls;
        double requested;
        public double getMaxEnergy() { return capacity; }
        public double getEnergy() { return energy; }
        public double getRequiredEnergy() { return capacity - energy; }
        public double getBandwidth() { return bandwidth; }
        public double injectEnergy(double amount) {
            injections++; requested = amount;
            double moved = Math.min(amount, capacity - energy); energy += moved; return amount - moved;
        }
        public double pullEnergy(double amount) { pulls++; double moved = Math.min(amount, energy); energy -= moved; return moved; }
    }
    private static final class Hybrid extends Node implements ImagFluxReceiver {
        int injections, pulls;
        public double getRequiredEnergy() { return capacity - energy; }
        public double injectEnergy(double amount) { injections++; double moved = Math.min(amount, capacity - energy); energy += moved; return amount - moved; }
        public double pullEnergy(double amount) { pulls++; double moved = Math.min(amount, energy); energy -= moved; return moved; }
    }
    public static void main(String[] args) {
        Node node = new Node();
        check(ClassicIFNodeManager.isSupported(node) && !ClassicIFReceiverManager.isSupported(node), "source concrete node role disjoint");
        Receiver receiver = new Receiver();
        check(ClassicIFReceiverManager.isSupported(receiver) && !ClassicIFNodeManager.isSupported(receiver), "source concrete receiver role disjoint");
        check(!ClassicEnergyBlockHelper.isSupported(null) && !ClassicEnergyBlockHelper.isSupported(new Object()), "generator/matrix marker alone never supported");
        close(ClassicEnergyBlockHelper.charge(null, 15.125, true), 15.125, "unsupported retains exact original IF");
        close(ClassicEnergyBlockHelper.pull(null, 15, true), 0, "unsupported pulls nothing");
        close(ClassicEnergyBlockHelper.getEnergy(null), 0, "unsupported reports zero");
        ClassicEnergyBlockHelper.setEnergy(node, 7.125);
        close(ClassicEnergyBlockHelper.getEnergy(node), 7.125, "source node getter/setter retain fractional IF");
        close(ClassicIFNodeManager.charge(node, 175.625, false), 25.625, "node source bandwidth respected");
        close(node.energy, 157.125, "node exact fractional resulting energy");
        close(ClassicIFNodeManager.charge(node, 175.625, true), 0, "node ignoreBandwidth bypasses source per-call limit");
        close(node.energy, 332.75, "bypassed charge adds exact rawIF");
        close(ClassicIFNodeManager.pull(node, 200.125, false), 150, "node pull uses source bandwidth");
        close(ClassicIFNodeManager.pull(node, 200.125, true), 182.75, "node pull bypass still bounded by actual store");
        close(node.energy, 0, "underpowered node pull drains all");
        node.energy = node.capacity - .125;
        close(ClassicEnergyBlockHelper.charge(node, .375, true), .25, "native sub-FE fractional capacity not rounded");
        close(node.energy, node.capacity, "node saturated finite capacity");
        close(ClassicEnergyBlockHelper.charge(node, 15, true), 15, "full node remains support without transfer");
        check(ClassicEnergyBlockHelper.isSupported(node), "full node support classification independent of energy");
        receiver.energy = 17.125;
        close(ClassicIFReceiverManager.getEnergy(receiver), 0, "source receiver manager getter deliberately zero");
        ClassicIFReceiverManager.setEnergy(receiver, 1500);
        close(receiver.energy, 17.125, "receiver source setter remains no-op");
        for (boolean ignore : new boolean[]{false, true}) {
            double before = receiver.energy;
            close(ClassicIFReceiverManager.charge(receiver, 350.625, ignore), 0, "receiver always bypasses bandwidth " + ignore);
            close(receiver.requested, 350.625, "receiver receives unquantized raw request " + ignore);
            close(receiver.energy - before, 350.625, "receiver exact accepted IF " + ignore);
            close(ClassicIFReceiverManager.pull(receiver, 200.375, ignore), 200.375, "receiver pull ignores bandwidth flag " + ignore);
        }
        receiver.energy = receiver.capacity - .0625;
        close(ClassicEnergyBlockHelper.charge(receiver, .125, false), .0625, "receiver finite capacity exact sub-FE remainder");
        close(ClassicEnergyBlockHelper.charge(receiver, 35, true), 35, "full receiver receives no IF");
        check(ClassicEnergyBlockHelper.isSupported(receiver), "full receiver still supported");
        ClassicEnergyBlockHelper.setEnergy(receiver, 0);
        close(receiver.energy, receiver.capacity, "aggregate receiver setter never adds a direct refill/reset");
        close(ClassicEnergyBlockHelper.getEnergy(receiver), 0, "aggregate receiver getter preserves source manager quirk");
        close(ClassicIFReceiverManager.pull(receiver, 5000, false), 2000, "receiver pull above bandwidth drains remaining capacity");
        for (double bad : new double[]{0, -1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            int sets = node.setters, injections = receiver.injections, pulls = receiver.pulls;
            ClassicIFNodeManager.charge(node, bad, true); ClassicIFNodeManager.pull(node, bad, false);
            ClassicIFReceiverManager.charge(receiver, bad, false); ClassicIFReceiverManager.pull(receiver, bad, true);
            check(node.setters == sets && receiver.injections == injections && receiver.pulls == pulls,
                    "nonpositive/nonfinite hostile input cannot mutate finite stores " + bad);
        }
        // Deterministic safety correction: source helper charges each matching representation
        // with the original amount. Here only one manager owns even a hypothetical hybrid.
        Hybrid hybrid = new Hybrid(); hybrid.bandwidth = 5.125;
        close(ClassicEnergyBlockHelper.charge(hybrid, 10.625, false), 5.5, "hybrid receives one native node-manager request");
        close(hybrid.energy, 5.125, "hybrid has no double-injection");
        check(hybrid.injections == 0 && hybrid.setters == 1, "receiver alias not invoked after native node manager");
        close(ClassicEnergyBlockHelper.pull(hybrid, 20, false), 5.125, "hybrid first-manager pull preserved");
        check(hybrid.pulls == 0, "receiver alias not invoked after node pull");
        // Concrete source node/receiver transfers match the original arithmetic for every
        // finite nonnegative input. Only aggregate return semantics are corrected.
        Random random = new Random(0x1071fL);
        for (int sample = 0; sample < 5000; sample++) {
            double capacity = (1 + random.nextInt(200000)) / 8.0;
            double start = random.nextInt(1 + (int)(capacity * 8)) / 8.0;
            double amount = random.nextInt(50000) / 8.0;
            double bandwidth = random.nextInt(10000) / 8.0;
            for (boolean ignore : new boolean[]{false, true}) {
                node = new Node(); node.capacity = capacity; node.energy = start; node.bandwidth = bandwidth;
                double sourceCharged = Math.min(amount, capacity - start);
                if (!ignore) sourceCharged = Math.min(bandwidth, sourceCharged);
                double left = ClassicEnergyBlockHelper.charge(node, amount, ignore);
                close(node.energy, start + sourceCharged, "source node charge oracle " + sample + '/' + ignore);
                close(left, amount - sourceCharged, "truthful node manager remainder " + sample + '/' + ignore);
                close(node.energy - start + left, amount, "node charge conservation " + sample + '/' + ignore);
                node.energy = start;
                double sourcePull = Math.min(start, amount);
                if (!ignore) sourcePull = Math.min(bandwidth, sourcePull);
                close(ClassicEnergyBlockHelper.pull(node, amount, ignore), sourcePull, "source node pull oracle " + sample + '/' + ignore);
                close(node.energy, start - sourcePull, "source node pull store " + sample + '/' + ignore);
                receiver = new Receiver(); receiver.capacity = capacity; receiver.energy = start; receiver.bandwidth = bandwidth;
                double sourceReceiverGain = Math.min(amount, capacity - start);
                left = ClassicEnergyBlockHelper.charge(receiver, amount, ignore);
                close(receiver.energy, start + sourceReceiverGain, "source receiver charge oracle " + sample + '/' + ignore);
                close(left, amount - sourceReceiverGain, "source receiver raw remainder " + sample + '/' + ignore);
                close(receiver.energy - start + left, amount, "receiver conservation " + sample + '/' + ignore);
                check(receiver.injections == (amount > 0 ? 1 : 0), "receiver exactly one real injection " + sample + '/' + ignore);
                receiver.energy = start;
                close(ClassicEnergyBlockHelper.pull(receiver, amount, ignore), Math.min(start, amount), "source receiver pull oracle " + sample + '/' + ignore);
                close(receiver.energy, start - Math.min(start, amount), "source receiver pull store " + sample + '/' + ignore);
            }
        }
        System.out.println("PASS " + assertions + " native block manager/source/conservation assertions");
    }
}

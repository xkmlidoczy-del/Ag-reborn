package cn.academy.port.wireless;

import cn.academy.port.machine.ImagFluxReceiver;
import cn.academy.port.solar.ClassicSolarBuffer;
import cn.academy.port.solar.ImagFluxGenerator;
import cn.academy.port.wireless.ClassicWirelessGraph.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Pure JVM assertions use real finite buffers; no native game launch or synthetic generation ticks. */
public final class ClassicWirelessGraphRegressionTest {
    private static int assertions;
    private static final Pos M = new Pos(0, 64, 0), N = new Pos(1, 64, 0), G = new Pos(2, 64, 0), R = new Pos(3, 64, 0);
    static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    static void equal(double expected, double actual, String message) { check(Double.isFinite(actual) && Math.abs(expected - actual) < 1e-7, message + ": expected=" + expected + ", actual=" + actual); }
    static final class Node implements ImagFluxNode {
        double energy, max = 1000, bandwidth = 100, range = 9;
        int capacity = 5;
        String password = "node-secret";
        public double getMaxEnergy() { return max; }
        public double getEnergy() { return energy; }
        public void setEnergy(double value) { energy = Double.isFinite(value) ? Math.max(0, Math.min(max, value)) : 0; }
        public double getBandwidth() { return bandwidth; }
        public int getCapacity() { return capacity; }
        public double getRange() { return range; }
        public String getNodeName() { return "test-node"; }
        public String getPassword() { return password; }
    }
    static final class Matrix implements ImagFluxMatrix {
        int capacity = 5;
        double bandwidth = 400, range = 100;
        boolean origin = true;
        public int getCapacity() { return capacity; }
        public double getBandwidth() { return bandwidth; }
        public double getRange() { return range; }
        public boolean isWirelessOrigin() { return origin; }
    }
    static class Generator implements ImagFluxGenerator {
        final ClassicSolarBuffer buffer = new ClassicSolarBuffer();
        double bandwidth = 100;
        int calls;
        Generator(double energy) { buffer.load(energy); }
        public double getEnergy() { return buffer.energy(); }
        public double getBandwidth() { return bandwidth; }
        public double getProvidedEnergy(double request) { calls++; return buffer.getProvidedEnergy(request); }
    }
    static class Receiver implements ImagFluxReceiver {
        double energy, max = 10000, bandwidth = 100, acceptance = 1;
        int calls;
        public double getEnergy() { return energy; }
        public double getMaxEnergy() { return max; }
        public double getRequiredEnergy() { return max - energy; }
        public double getBandwidth() { return bandwidth; }
        public double injectEnergy(double amount) { calls++; double accepted = Math.min(amount * acceptance, max - energy); energy += accepted; return amount - accepted; }
        public double pullEnergy(double amount) { double removed = Math.min(amount, energy); energy -= removed; return removed; }
    }
    static final class World implements Resolver {
        final Map<Pos, Node> nodes = new HashMap<>();
        final Map<Pos, Matrix> matrices = new HashMap<>();
        final Map<Pos, Generator> generators = new HashMap<>();
        final Map<Pos, Receiver> receivers = new HashMap<>();
        final Set<Pos> unloaded = new HashSet<>();
        int unloadedLookups, changes;
        boolean forbiddenReads;
        public boolean isLoaded(Pos pos) { if (forbiddenReads) throw new AssertionError("restore must not read world"); return pos != null && !unloaded.contains(pos); }
        private void checked(Pos pos) { if (!isLoaded(pos)) { unloadedLookups++; throw new AssertionError("unloaded lookup " + pos); } }
        public ImagFluxNode node(Pos pos) { checked(pos); return nodes.get(pos); }
        public ImagFluxMatrix matrix(Pos pos) { checked(pos); return matrices.get(pos); }
        public ImagFluxGenerator generator(Pos pos) { checked(pos); return generators.get(pos); }
        public ImagFluxReceiver receiver(Pos pos) { checked(pos); return receivers.get(pos); }
        public List<Pos> wirelessBlocksWithin(Pos origin, double range, int max) {
            var all = new HashSet<Pos>(nodes.keySet()); all.addAll(matrices.keySet());
            return all.stream().filter(this::isLoaded).filter(pos -> origin.distanceSquared(pos) <= range * range)
                    .sorted(Comparator.comparingInt(Pos::x).thenComparingInt(Pos::y).thenComparingInt(Pos::z)).limit(max).toList();
        }
        ClassicWirelessGraph graph(long seed) { return new ClassicWirelessGraph(this, new Random(seed), () -> changes++); }
        double total(ClassicWirelessGraph graph) { return nodes.values().stream().mapToDouble(n -> n.energy).sum() + generators.values().stream().mapToDouble(Generator::getEnergy).sum() + receivers.values().stream().mapToDouble(Receiver::getEnergy).sum() + graph.snapshot().networks().stream().mapToDouble(NetworkData::buffer).sum(); }
    }
    private static World basic() { var world = new World(); world.nodes.put(N, new Node()); world.matrices.put(M, new Matrix()); world.generators.put(G, new Generator(1000)); world.receivers.put(R, new Receiver()); return world; }
    public static void main(String[] args) {
        admissionAndAuthentication(); atomicOwnershipAndRemoval(); finiteTransferAndBudgets(); invalidCallbacksCannotMint(); sourceMatrixConservation(); receiverFirstDoesNotStrandBuffer(); unloadReloadAndPersistence(); discoveryQuirks(); randomizedConservation();
        System.out.println("ClassicWirelessGraphRegressionTest: " + assertions + " assertions passed");
    }
    private static void admissionAndAuthentication() {
        var world = basic(); var graph = world.graph(1);
        check(graph.createNetwork(M, "shared", "matrix-secret"), "valid source matrix creation");
        check(graph.isNetworkEncrypted(M), "public encryption flag");
        check(!graph.linkNode(M, N, "wrong"), "matrix password required"); check(graph.linkNode(M, N, "matrix-secret"), "correct matrix password");
        check(graph.linkNode(M, N, "matrix-secret") && graph.networkAt(M).load() == 1, "idempotent node relink");
        check(!graph.linkGenerator(N, G, "wrong", true), "node user password"); check(graph.linkGenerator(N, G, "node-secret", true), "node-auth generator");
        check(graph.linkReceiver(N, R, "ignored", false), "source internal no-auth user link");
        equal(2, graph.connectionAt(N).load(), "shared generator+receiver capacity");
        world.nodes.get(N).capacity = 2; var extra = new Pos(4, 64, 0); world.generators.put(extra, new Generator(10));
        check(!graph.linkGenerator(N, extra, "node-secret", true), "combined capacity enforced");
        check(graph.linkGenerator(N, G, "node-secret", true), "idempotent user relink at full capacity");
        graph.unlinkReceiver(R); world.nodes.get(N).range = 2; world.receivers.put(new Pos(3, 64, 0), new Receiver());
        check(graph.linkReceiver(N, R, "node-secret", true), "inclusive spherical range boundary");
        graph.unlinkReceiver(R); world.receivers.put(new Pos(3, 65, 0), new Receiver()); check(!graph.linkReceiver(N, new Pos(3, 65, 0), "node-secret", true), "3D range outside sphere");
        world.matrices.get(M).capacity = 1; var n2 = new Pos(4,64,0); world.nodes.put(n2,new Node()); check(!graph.linkNode(M,n2,"matrix-secret"), "matrix node capacity");
        check(graph.changeNetworkPassword(M,"new") && graph.networkPassword(M).equals("new"), "password update changes future auth");
        check(graph.renameNetwork(M,"renamed") && graph.networkAt(N).ssid().equals("renamed"), "linked node advertises renamed network");
        graph.removeNetwork(M); check(graph.networkAt(N) == null && graph.connectionAt(N).load() == 1, "destroy network does not destroy local links");
        check(graph.createNetwork(M,"same","") && !graph.isNetworkEncrypted(M), "empty source password unlocked flag");
        var m2 = new Pos(0,64,1); world.matrices.put(m2,new Matrix()); check(graph.createNetwork(m2,"same",""), "source duplicate SSIDs allowed by matrix identity");
        world.matrices.get(m2).origin = false; check(!graph.createNetwork(m2,"bad",""), "subpart cannot advertise matrix");
        equal(0, world.unloadedLookups, "never look up an unloaded endpoint");
    }
    private static void atomicOwnershipAndRemoval() {
        var world = basic(); var graph = world.graph(2); var n2 = new Pos(4,64,0); world.nodes.put(n2,new Node());
        check(graph.linkGenerator(N,G,"",false), "first generator owner"); check(graph.linkGenerator(n2,G,"",false), "generator atomic relink");
        equal(0,graph.connectionAt(N).load(),"old generator owner clear immediately"); check(n2.equals(graph.nodeForGenerator(G)),"new generator lookup survives old cleanup");
        graph.tick(); equal(100,world.nodes.get(n2).energy,"relinked generation exactly once"); equal(0,world.nodes.get(N).energy,"old owner never receives");
        graph.unlinkGenerator(G); graph.tick(); check(graph.nodeForGenerator(G)==null,"unlink lookup gone");
        graph.linkReceiver(n2,R,"",false); world.receivers.remove(R); graph.tick(); check(graph.nodeForReceiver(R)==null,"loaded removed user automatically unlinked");
        graph.linkGenerator(n2,G,"",false); world.nodes.remove(n2); graph.tick(); check(graph.nodeForGenerator(G)==null,"loaded removed node clears users");
        graph.createNetwork(M,"a",""); graph.linkNode(M,N,""); var m2=new Pos(0,64,1); world.matrices.put(m2,new Matrix()); graph.createNetwork(m2,"b",""); check(graph.linkNode(m2,N,""),"node atomic network relink");
        equal(0,graph.networkAt(M).load(),"old net clear"); check(graph.networkAt(N).matrix().equals(m2),"new network lookup stable"); graph.removeNetwork(M); check(graph.networkAt(N).matrix().equals(m2),"old cleanup cannot erase new ownership");
        world.matrices.remove(m2); graph.tick(); check(graph.networkAt(N)==null,"loaded destroyed matrix unlinks nodes");
    }
    private static void finiteTransferAndBudgets() {
        var world=basic(); var graph=world.graph(3); var node=world.nodes.get(N); var generator=world.generators.get(G); var receiver=world.receivers.get(R);
        node.bandwidth=40; generator.bandwidth=60; receiver.bandwidth=25; graph.linkGenerator(N,G,"",false); graph.linkReceiver(N,R,"",false);
        double total=world.total(graph); int changedBefore=world.changes; graph.tick(); check(world.changes>changedBefore,"actual native-store transfers dirty saveddata"); equal(960,generator.getEnergy(),"node40 input bandwidth"); equal(25,receiver.energy,"receiver25 output bandwidth"); equal(15,node.energy,"separate input+output budgets"); equal(total,world.total(graph),"input-output conservation");
        receiver.acceptance=.2; receiver.bandwidth=100; generator.buffer.load(0); node.energy=30; graph.tick(); equal(6,receiver.energy-25,"receiver actual accepted remainder"); equal(24,node.energy,"node debits only accepted IF");
        receiver.acceptance=1; receiver.energy=receiver.max-2; node.energy=50; graph.tick(); equal(receiver.max,receiver.energy,"required-energy cap"); equal(48,node.energy,"full receiver only2IF debit");
        receiver.energy=0; graph.unlinkReceiver(R); generator.buffer.load(1000); node.energy=995; graph.tick(); equal(1000,node.energy,"finite node capacity"); equal(995,generator.getEnergy(),"generator pulls only remaining5IF");
        generator.buffer.load(0); node.energy=0; graph.tick(); equal(0,node.energy,"empty solar cannot power node");
        node.bandwidth=Double.NaN; generator.buffer.load(100); graph.tick(); equal(100,generator.getEnergy(),"nonfinite bandwidth safely transfers0"); node.bandwidth=-3; graph.tick(); equal(100,generator.getEnergy(),"negative bandwidth safely transfers0");
        node.bandwidth=100; var g2=new Pos(2,64,1); world.generators.put(g2,new Generator(1000)); graph.linkGenerator(N,g2,"",false); double before=generator.getEnergy()+world.generators.get(g2).getEnergy(); graph.tick(); equal(100,before-generator.getEnergy()-world.generators.get(g2).getEnergy(),"all generators share one input budget");
    }
    private static void invalidCallbacksCannotMint() {
        var world=basic(); var graph=world.graph(4);
        world.generators.put(G,new Generator(1000){@Override public double getProvidedEnergy(double request){return request;}}); graph.linkGenerator(N,G,"",false); graph.tick(); equal(0,world.nodes.get(N).energy,"lying generator return with no actual debit creates no power");
        world.generators.put(G,new Generator(1000){@Override public double getProvidedEnergy(double request){return Double.NaN;}}); graph.tick(); equal(0,world.nodes.get(N).energy,"nonfinite generator return creates no power");
        graph.unlinkGenerator(G); world.nodes.get(N).energy=100; world.receivers.put(R,new Receiver(){@Override public double injectEnergy(double request){super.injectEnergy(request);return request;}}); graph.linkReceiver(N,R,"",false); graph.tick(); equal(0,world.nodes.get(N).energy,"misreported receiver remainder cannot create IF"); equal(100,world.receivers.get(R).energy,"actual accepted debit authoritative");
    }
    private static void sourceMatrixConservation() {
        var world=basic(); world.generators.clear(); world.receivers.clear(); var n2=new Pos(5,64,0); var other=new Node(); other.max=2000; other.bandwidth=50; world.nodes.put(n2,other); world.nodes.get(N).energy=1000; world.nodes.get(N).bandwidth=50; world.matrices.get(M).bandwidth=60;
        var graph=world.graph(5); graph.createNetwork(M,"balance",""); graph.linkNode(M,N,""); graph.linkNode(M,n2,""); double total=world.total(graph); graph.tick(); equal(total,world.total(graph),"corrected opposite-sign buffer and node conserve");
        check(graph.networkAt(M).buffer()>=0&&graph.networkAt(M).buffer()<=2000,"bounded matrix buffer"); check(world.nodes.get(N).energy>=950,"node bandwidth50 cap"); check(other.energy<=10,"matrix total absolute budget60 counts both directions");
        for(int i=0;i<100;i++){double previousN=world.nodes.get(N).energy,previousOther=other.energy;graph.tick();equal(total,world.total(graph),"matrix tick conservation"+i);check(Math.abs(world.nodes.get(N).energy-previousN)+Math.abs(other.energy-previousOther)<=60+1e-7,"total matrix budget"+i);}
        var saved=graph.snapshot(); graph.restore(new State(List.of(new NetworkData(M,"balance","",2000,List.of(N,n2))),List.of())); world.nodes.get(N).energy=1000; other.energy=0; graph.tick(); equal(1000,world.nodes.get(N).energy,"full buffer cannot withdraw donor before room available");
        graph.restore(new State(List.of(new NetworkData(M,"balance","",0,List.of(n2))),List.of())); other.energy=0; graph.tick();equal(0,other.energy,"empty buffer+empty net never fake power");
        graph.restore(new State(List.of(new NetworkData(M,"empty","",0,List.of())),List.of())); graph.tick();equal(0,graph.networkAt(M).buffer(),"zero total capacity noNaN");
    }
    private static void receiverFirstDoesNotStrandBuffer() {
        var world=new World();var donor=new Node();donor.max=100;donor.energy=100;donor.bandwidth=100;var recipient=new Node();recipient.max=100;recipient.bandwidth=100;var next=new Pos(5,64,0);world.nodes.put(N,donor);world.nodes.put(next,recipient);var mat=new Matrix();mat.bandwidth=1000;world.matrices.put(M,mat);
        // Every two-element shuffle swaps index1 with index0: recipient precedes donor.
        var random=new Random(0){@Override public int nextInt(int bound){return 0;}};
        var graph=new ClassicWirelessGraph(world,random,()->world.changes++);graph.createNetwork(M,"receiver-first","");graph.linkNode(M,N,"");graph.linkNode(M,next,"");
        graph.tick();equal(50,donor.energy,"first donor funds matrix buffer");equal(0,recipient.energy,"first recipient cannot consume an empty buffer");equal(50,graph.networkAt(M).buffer(),"first real buffer stores50IF");equal(100,world.total(graph),"first tick preserves100IF total");
        graph.tick();equal(50,donor.energy,"donor target includes real buffer");equal(50,recipient.energy,"second tick spends entire usable buffer");equal(0,graph.networkAt(M).buffer(),"no permanently stranded50IF");equal(100,world.total(graph),"corrected target pool conserves100IF total");
        for(int i=0;i<20;i++)graph.tick();equal(50,donor.energy,"balanced donor stays stable");equal(50,recipient.energy,"balanced recipient stays stable");equal(0,graph.networkAt(M).buffer(),"stable buffer empty");
        graph.restore(new State(List.of(new NetworkData(M,"overflow-pool","",2000,List.of(N,next))),List.of()));graph.tick();equal(100,donor.energy,"buffer pool target cannot exceed node capacity");equal(100,recipient.energy,"both nodes fill only to real capacity");equal(1900,graph.networkAt(M).buffer(),"excess real stored energy retained within matrix");equal(2100,world.total(graph),"capacity-saturated pool still conserves all IF");
    }
    private static void unloadReloadAndPersistence() {
        var world=basic(); var graph=world.graph(6); graph.createNetwork(M,"persistent","secret"); graph.linkNode(M,N,"secret"); graph.linkGenerator(N,G,"",false); graph.linkReceiver(N,R,"",false);
        world.unloaded.addAll(List.of(M,N,G,R)); var state=graph.snapshot(); var restored=world.graph(7); world.forbiddenReads=true; restored.restore(state); world.forbiddenReads=false;
        restored.tick(); equal(1000,world.generators.get(G).getEnergy(),"unloaded never drains"); check(restored.snapshot().equals(state),"all unloaded graph data survives save/restore"); check(restored.networkAt(N)!=null,"unloaded matrix remains linked");
        world.unloaded.remove(N); restored.tick(); equal(0,world.nodes.get(N).energy,"loaded node alone cannot consume unloaded solar"); check(restored.connectionAt(N).load()==2,"unloaded endpoints retained");
        world.unloaded.clear(); restored.tick(); equal(900,world.generators.get(G).getEnergy(),"reload resumes exact actual generation"); equal(100,world.receivers.get(R).energy,"reload resumes actual receiver");
        world.generators.remove(G); world.receivers.remove(R); var filtered=restored.snapshot(); check(filtered.connections().isEmpty(),"save omits loaded missing user refs");
        restored.tick(); check(restored.nodeForGenerator(G)==null&&restored.nodeForReceiver(R)==null,"tick also removes missing lookup");
        world.unloaded.add(M); world.matrices.remove(M); restored.tick(); check(restored.networkAt(N)!=null,"unloaded absent matrix cannot be declared destroyed"); world.unloaded.remove(M); restored.tick(); check(restored.networkAt(N)==null,"only loaded missing matrix disposed");
        equal(0,world.unloadedLookups,"no forced unloaded lookup through entire cycle");
        var duplicate=new State(List.of(new NetworkData(M,"first","",Double.NaN,List.of(N,N)),new NetworkData(M,"duplicate","",9999,List.of(N))),List.of(new ConnectionData(N,List.of(G,G),List.of(R,R)),new ConnectionData(new Pos(9,64,0),List.of(G),List.of(R))));
        world.matrices.put(M,new Matrix());world.generators.put(G,new Generator(0));world.receivers.put(R,new Receiver());restored.restore(duplicate);equal(1,restored.networkAt(M).load(),"corrupt duplicate node membership deduplicated");equal(0,restored.networkAt(M).buffer(),"NaN buffer sanitized");equal(2,restored.connectionAt(N).load(),"corrupt duplicate user membership deduplicated");check(restored.nodeForGenerator(G).equals(N),"first ownership wins duplicate restore");
        check(world.changes>0,"link/network/buffer mutations dirty saveddata callback");
    }
    private static void discoveryQuirks() {
        var world=basic(); var graph=world.graph(8); var farMatrix=new Pos(80,64,0); world.matrices.put(farMatrix,new Matrix()); graph.createNetwork(farMatrix,"remote","secret"); graph.linkNode(farMatrix,N,"secret");
        var found=graph.nearbyNetworks(G,9,100); check(found.size()==1&&found.getFirst().matrix().equals(farMatrix),"nearby linked node advertises matrix outside search sphere");
        world.matrices.get(farMatrix).range=1; check(graph.nearbyNetworks(G,9,100).isEmpty(),"advertisement still checks matrix actual link range"); world.matrices.get(farMatrix).range=100; world.matrices.get(farMatrix).capacity=1; check(graph.nearbyNetworks(G,9,100).isEmpty(),"full net excluded");
        world.nodes.get(N).range=1; check(graph.nearbyNodes(R,20,100).isEmpty(),"source user discovery checks actual node range"); world.nodes.get(N).range=9; check(graph.nearbyNodes(R,20,100).size()==1,"empty standalone local connection node discoverable");
        world.nodes.get(N).capacity=0;check(graph.nearbyNodes(R,20,100).isEmpty(),"full node discovery excluded");
        var distant=new Pos(24,64,0);world.nodes.put(distant,new Node());world.nodes.get(distant).range=100;check(graph.nearbyNodes(R,20,100).isEmpty(),"source hard search20 despite actual larger range");
    }
    private static void randomizedConservation() {
        for(int seed=0;seed<30;seed++){
            var world=new World();var matrix=new Matrix();matrix.capacity=12;matrix.bandwidth=100+seed*13;world.matrices.put(M,matrix);var graph=world.graph(seed);graph.createNetwork(M,"random","pw");var rand=new Random(seed);
            for(int i=0;i<8;i++){var p=new Pos(i+1,64,0);var node=new Node();node.max=500+rand.nextInt(3000);node.energy=rand.nextDouble()*node.max;node.bandwidth=1+rand.nextInt(100);node.range=50;world.nodes.put(p,node);graph.linkNode(M,p,"pw");var gp=new Pos(i+1,65,0);world.generators.put(gp,new Generator(rand.nextDouble()*1000));graph.linkGenerator(p,gp,"",false);var rp=new Pos(i+1,66,0);var rec=new Receiver();rec.acceptance=rand.nextDouble();world.receivers.put(rp,rec);graph.linkReceiver(p,rp,"",false);}
            double total=world.total(graph);
            for(int tick=0;tick<200;tick++){graph.tick();equal(total,world.total(graph),"random conserved seed"+seed+"tick"+tick);for(var node:world.nodes.values())check(node.energy>=0&&node.energy<=node.max,"finite bounded node");check(graph.networkAt(M).buffer()>=0&&graph.networkAt(M).buffer()<=2000,"finite bounded buffer");}
        }
    }
}

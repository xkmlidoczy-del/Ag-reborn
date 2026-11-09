/* AcademyCraft 1.0.7 NodeConn/WirelessNet/WiWorldData adaptation, GPLv3. See NOTICE. */
package cn.academy.port.wireless;

import cn.academy.port.machine.ImagFluxReceiver;
import cn.academy.port.solar.ImagFluxGenerator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Dimension-local, server-owned graph. Resolver lookups NEVER load chunks.
 * Source ordering: matrix balancing first, generator input then receiver output.
 * Source buffer += delta together with node += delta creates/destroys energy;
 * this port intentionally applies buffer -= delta, conserving actual IF.
 * The real finite buffer is included in the target pool, avoiding stranded IF
 * once loaded nodes reach equal fullness. Targets remain capped at node capacity.
 * Relinks/unlinks are atomic, avoiding the legacy deferred lookup-removal race.
 */
public final class ClassicWirelessGraph {
    public static final double BUFFER_MAX = 2000;
    public record Pos(int x, int y, int z) {
        public double distanceSquared(Pos other) {
            double dx = (double)x - other.x, dy = (double)y - other.y, dz = (double)z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
    public interface Resolver {
        boolean isLoaded(Pos pos);
        ImagFluxNode node(Pos pos);
        ImagFluxMatrix matrix(Pos pos);
        ImagFluxGenerator generator(Pos pos);
        ImagFluxReceiver receiver(Pos pos);
        /** Loaded matrix origins and nodes within a sphere, in source x/y/z order. */
        List<Pos> wirelessBlocksWithin(Pos origin, double range, int max);
    }
    public record NetworkSnapshot(Pos matrix, String ssid, int load, int capacity, double buffer, List<Pos> nodes) {
        public NetworkSnapshot { nodes = List.copyOf(nodes); }
    }
    public record NodeSnapshot(Pos node, int load, int capacity, List<Pos> generators, List<Pos> receivers) {
        public NodeSnapshot { generators = List.copyOf(generators); receivers = List.copyOf(receivers); }
    }
    /** Persistence only. Do not expose game-world passwords in public UI discovery readouts. */
    public record NetworkData(Pos matrix, String ssid, String password, double buffer, List<Pos> nodes) {
        public NetworkData { nodes = List.copyOf(nodes); }
    }
    public record ConnectionData(Pos node, List<Pos> generators, List<Pos> receivers) {
        public ConnectionData { generators = List.copyOf(generators); receivers = List.copyOf(receivers); }
    }
    public record State(List<NetworkData> networks, List<ConnectionData> connections) {
        public State { networks = List.copyOf(networks); connections = List.copyOf(connections); }
    }
    private static final class Network {
        final Pos matrix;
        String ssid, password;
        double buffer;
        final Set<Pos> nodes = new LinkedHashSet<>();
        Network(Pos matrix, String ssid, String password) { this.matrix = matrix; this.ssid = ssid; this.password = password; }
    }
    private static final class Connection {
        final Pos node;
        final Set<Pos> generators = new LinkedHashSet<>(), receivers = new LinkedHashSet<>();
        Connection(Pos node) { this.node = node; }
        int load() { return generators.size() + receivers.size(); }
    }
    private final Resolver resolver;
    private final Random random;
    private final Runnable changed;
    private final Map<Pos, Network> networks = new LinkedHashMap<>(), networkNodes = new LinkedHashMap<>();
    private final Map<Pos, Connection> connections = new LinkedHashMap<>(), generators = new LinkedHashMap<>(), receivers = new LinkedHashMap<>();

    public ClassicWirelessGraph(Resolver resolver) { this(resolver, new Random(), () -> {}); }
    public ClassicWirelessGraph(Resolver resolver, Random random, Runnable changed) {
        this.resolver = Objects.requireNonNull(resolver); this.random = Objects.requireNonNull(random); this.changed = Objects.requireNonNull(changed);
    }
    public boolean createNetwork(Pos matrix, String ssid, String password) {
        if (matrix == null || ssid == null || password == null || loadedMatrix(matrix) == null) return false;
        removeNetwork(matrix);
        networks.put(matrix, new Network(matrix, ssid, password)); changed.run(); return true;
    }
    public void removeNetwork(Pos matrix) {
        Network net = networks.remove(matrix);
        if (net == null) return;
        for (Pos node : net.nodes) networkNodes.remove(node, net);
        changed.run();
    }
    public boolean renameNetwork(Pos matrix, String ssid) {
        Network net = validNetwork(matrix);
        if (net == null || ssid == null) return false;
        if (!net.ssid.equals(ssid)) { net.ssid = ssid; changed.run(); }
        return true;
    }
    public boolean changeNetworkPassword(Pos matrix, String password) {
        Network net = validNetwork(matrix);
        if (net == null || password == null) return false;
        if (!net.password.equals(password)) { net.password = password; changed.run(); }
        return true;
    }
    public boolean isNetworkEncrypted(Pos matrixOrNode) { Network net = validNetwork(matrixOrNode); return net != null && !net.password.isEmpty(); }
    /** Trusted server-side owner readout only; never include in nearby public snapshots. */
    public String networkPassword(Pos matrix) { Network net = validNetwork(matrix); return net == null ? null : net.password; }
    public boolean linkNode(Pos matrix, Pos node, String password) {
        Network net = validNetwork(matrix); ImagFluxMatrix mat = loadedMatrix(matrix); ImagFluxNode actual = loadedNode(node);
        if (net == null || mat == null || actual == null || !net.password.equals(password)) return false;
        if (net.nodes.contains(node)) return true; // Idempotent relinks cannot duplicate throughput or consume capacity.
        if (net.nodes.size() >= Math.max(0, mat.getCapacity()) || !inRange(matrix, node, mat.getRange())) return false;
        unlinkNode(node); net.nodes.add(node); networkNodes.put(node, net); changed.run(); return true;
    }
    public void unlinkNode(Pos node) {
        Network net = networkNodes.remove(node);
        if (net != null) { net.nodes.remove(node); changed.run(); }
    }
    public boolean linkGenerator(Pos node, Pos generator, String password, boolean needAuth) {
        if (loadedGenerator(generator) == null) return false;
        return linkUser(node, generator, password, needAuth, true);
    }
    public boolean linkReceiver(Pos node, Pos receiver, String password, boolean needAuth) {
        if (loadedReceiver(receiver) == null) return false;
        return linkUser(node, receiver, password, needAuth, false);
    }
    private boolean linkUser(Pos node, Pos user, String password, boolean needAuth, boolean generator) {
        ImagFluxNode actual = loadedNode(node);
        if (actual == null || needAuth && !Objects.equals(actual.getPassword(), password) || !inRange(node, user, actual.getRange())) return false;
        Map<Pos, Connection> lookup = generator ? generators : receivers;
        Connection old = lookup.get(user);
        if (old != null && old.node.equals(node)) return true;
        Connection conn = connections.get(node);
        if ((conn == null ? 0 : conn.load()) >= Math.max(0, actual.getCapacity())) return false;
        if (generator) unlinkGenerator(user); else unlinkReceiver(user);
        conn = connections.computeIfAbsent(node, Connection::new);
        (generator ? conn.generators : conn.receivers).add(user); lookup.put(user, conn); changed.run(); return true;
    }
    public void unlinkGenerator(Pos pos) { unlinkUser(pos, true); }
    public void unlinkReceiver(Pos pos) { unlinkUser(pos, false); }
    private void unlinkUser(Pos pos, boolean generator) {
        Connection conn = (generator ? generators : receivers).remove(pos);
        if (conn != null) { (generator ? conn.generators : conn.receivers).remove(pos); changed.run(); }
    }
    public Pos nodeForGenerator(Pos pos) { return userNode(generators.get(pos)); }
    public Pos nodeForReceiver(Pos pos) { return userNode(receivers.get(pos)); }
    private Pos userNode(Connection conn) { return conn != null && validConnection(conn) ? conn.node : null; }
    public NetworkSnapshot networkAt(Pos matrixOrNode) {
        Network net = validNetwork(matrixOrNode);
        if (net == null) return null;
        ImagFluxMatrix mat = loadedMatrix(net.matrix);
        return new NetworkSnapshot(net.matrix, net.ssid, net.nodes.size(), mat == null ? 0 : Math.max(0, mat.getCapacity()), net.buffer, new ArrayList<>(net.nodes));
    }
    public NodeSnapshot connectionAt(Pos node) {
        Connection conn = connections.get(node); ImagFluxNode actual = loadedNode(node);
        if (conn != null && !validConnection(conn)) conn = null;
        return new NodeSnapshot(node, conn == null ? 0 : conn.load(), actual == null ? 0 : Math.max(0, actual.getCapacity()),
                conn == null ? List.of() : new ArrayList<>(conn.generators), conn == null ? List.of() : new ArrayList<>(conn.receivers));
    }
    /** Source search accepts nearby matrices OR linked nodes advertising their parent net. */
    public List<NetworkSnapshot> nearbyNetworks(Pos origin, double scanRange, int max) {
        if (max <= 0 || !Double.isFinite(scanRange) || scanRange < 0) return List.of();
        Set<Pos> found = new LinkedHashSet<>(); List<NetworkSnapshot> result = new ArrayList<>();
        for (Pos pos : resolver.wirelessBlocksWithin(origin, scanRange, max)) {
            NetworkSnapshot net = networkAt(pos);
            if (net == null || !found.add(net.matrix)) continue;
            ImagFluxMatrix mat = loadedMatrix(net.matrix);
            if (mat != null && net.load < net.capacity && inRange(origin, net.matrix, mat.getRange())) result.add(net);
            if (result.size() >= max) break;
        }
        return List.copyOf(result);
    }
    /** Source user search20/max100; actual admission still uses node range and capacity. */
    public List<NodeSnapshot> nearbyNodes(Pos origin, double scanRange, int max) {
        if (max <= 0 || !Double.isFinite(scanRange) || scanRange < 0) return List.of();
        List<NodeSnapshot> result = new ArrayList<>();
        for (Pos pos : resolver.wirelessBlocksWithin(origin, scanRange, Integer.MAX_VALUE)) {
            ImagFluxNode node = loadedNode(pos);
            if (node == null || !inRange(origin, pos, node.getRange())) continue;
            NodeSnapshot info = connectionAt(pos);
            if (info.load < info.capacity) result.add(info);
            if (result.size() >= max) break;
        }
        return List.copyOf(result);
    }
    public void tick() {
        for (Network net : new ArrayList<>(networks.values())) tickNetwork(net);
        for (Connection conn : new ArrayList<>(connections.values())) tickConnection(conn);
    }
    private void tickNetwork(Network net) {
        if (!resolver.isLoaded(net.matrix)) return;
        ImagFluxMatrix matrix = loadedMatrix(net.matrix);
        if (matrix == null) { removeNetwork(net.matrix); return; }
        List<Pos> order = new ArrayList<>(net.nodes); Collections.shuffle(order, random);
        double sum = 0, maximum = 0;
        for (Pos pos : order) {
            if (!resolver.isLoaded(pos)) continue;
            ImagFluxNode node = loadedNode(pos);
            if (node == null) { unlinkNode(pos); continue; }
            sum += bounded(node.getEnergy(), node.getMaxEnergy()); maximum += positive(node.getMaxEnergy());
        }
        if (maximum == 0 || !Double.isFinite(sum) || !Double.isFinite(maximum)) return;
        double percent = Math.min(1, (sum + net.buffer) / maximum), left = positive(matrix.getBandwidth());
        for (Pos pos : order) {
            if (left <= 0) break;
            ImagFluxNode node = loadedNode(pos);
            if (node == null || !net.nodes.contains(pos)) continue;
            double current = bounded(node.getEnergy(), node.getMaxEnergy());
            double delta = positive(node.getMaxEnergy()) * percent - current;
            delta = Math.copySign(Math.min(Math.abs(delta), Math.min(left, positive(node.getBandwidth()))), delta);
            // Negative delta withdraws from a donor into buffer. Positive delta spends buffer.
            delta = delta < 0 ? -Math.min(-delta, BUFFER_MAX - net.buffer) : Math.min(delta, net.buffer);
            if (delta != 0) {
                node.setEnergy(current + delta); net.buffer = bounded(net.buffer - delta, BUFFER_MAX);
                left -= Math.abs(delta); changed.run();
            }
        }
    }
    private void tickConnection(Connection conn) {
        if (!resolver.isLoaded(conn.node)) return;
        ImagFluxNode node = loadedNode(conn.node);
        if (node == null || conn.load() == 0) { removeConnection(conn); return; }
        double left = positive(node.getBandwidth());
        List<Pos> order = new ArrayList<>(conn.generators); Collections.shuffle(order, random);
        for (Pos pos : order) {
            if (!resolver.isLoaded(pos)) continue;
            ImagFluxGenerator generator = loadedGenerator(pos);
            if (generator == null) { unlinkGenerator(pos); continue; }
            if (left <= 0) continue;
            double current = bounded(node.getEnergy(), node.getMaxEnergy());
            double required = Math.min(left, Math.min(positive(generator.getBandwidth()), positive(node.getMaxEnergy()) - current));
            if (required <= 0) continue;
            double before = positive(generator.getEnergy());
            double reported = bounded(generator.getProvidedEnergy(required), required);
            // Native seam exposes the store: a callback cannot fabricate power by merely returning a number.
            double supplied = Math.min(reported, bounded(before - positive(generator.getEnergy()), required));
            if (supplied > 0) { node.setEnergy(current + supplied); left -= supplied; changed.run(); }
        }
        left = positive(node.getBandwidth()); // Source grants separate full input AND output budgets.
        order = new ArrayList<>(conn.receivers); Collections.shuffle(order, random);
        for (Pos pos : order) {
            if (!resolver.isLoaded(pos)) continue;
            ImagFluxReceiver receiver = loadedReceiver(pos);
            if (receiver == null) { unlinkReceiver(pos); continue; }
            if (left <= 0) continue;
            double current = bounded(node.getEnergy(), node.getMaxEnergy());
            double request = Math.min(current, Math.min(left, Math.min(positive(receiver.getBandwidth()), positive(receiver.getRequiredEnergy()))));
            if (request <= 0) continue;
            double before = positive(receiver.getEnergy());
            receiver.injectEnergy(request);
            // Debit actual accepted IF, also safe if a receiver misreports its remainder.
            double accepted = bounded(positive(receiver.getEnergy()) - before, request);
            if (accepted > 0) { node.setEnergy(current - accepted); left -= accepted; changed.run(); }
        }
    }
    private void removeConnection(Connection conn) {
        connections.remove(conn.node, conn);
        for (Pos pos : conn.generators) generators.remove(pos, conn);
        for (Pos pos : conn.receivers) receivers.remove(pos, conn);
        changed.run();
    }
    private Network validNetwork(Pos key) {
        Network net = networks.get(key); if (net == null) net = networkNodes.get(key);
        if (net != null && resolver.isLoaded(net.matrix) && loadedMatrix(net.matrix) == null) { removeNetwork(net.matrix); return null; }
        return net;
    }
    private boolean validConnection(Connection conn) {
        if (resolver.isLoaded(conn.node) && loadedNode(conn.node) == null) { removeConnection(conn); return false; }
        return true;
    }
    private ImagFluxNode loadedNode(Pos pos) { return pos != null && resolver.isLoaded(pos) ? resolver.node(pos) : null; }
    private ImagFluxMatrix loadedMatrix(Pos pos) {
        ImagFluxMatrix matrix = pos != null && resolver.isLoaded(pos) ? resolver.matrix(pos) : null;
        return matrix != null && matrix.isWirelessOrigin() ? matrix : null;
    }
    private ImagFluxGenerator loadedGenerator(Pos pos) { return pos != null && resolver.isLoaded(pos) ? resolver.generator(pos) : null; }
    private ImagFluxReceiver loadedReceiver(Pos pos) { return pos != null && resolver.isLoaded(pos) ? resolver.receiver(pos) : null; }
    private static boolean inRange(Pos a, Pos b, double range) { return a != null && b != null && Double.isFinite(range) && range >= 0 && a.distanceSquared(b) <= range * range; }
    private static double positive(double value) { return Double.isFinite(value) ? Math.max(0, value) : 0; }
    private static double bounded(double value, double maximum) { return Math.min(positive(value), positive(maximum)); }

    /** Saving excludes confirmed missing loaded references and retains every unloaded reference. */
    public State snapshot() {
        List<NetworkData> nets = new ArrayList<>(); List<ConnectionData> conns = new ArrayList<>();
        for (Network net : new ArrayList<>(networks.values())) {
            if (validNetwork(net.matrix) == null) continue;
            List<Pos> nodes = net.nodes.stream().filter(pos -> !resolver.isLoaded(pos) || loadedNode(pos) != null).toList();
            nets.add(new NetworkData(net.matrix, net.ssid, net.password, net.buffer, nodes));
        }
        for (Connection conn : new ArrayList<>(connections.values())) {
            if (!validConnection(conn)) continue;
            List<Pos> gens = conn.generators.stream().filter(pos -> !resolver.isLoaded(pos) || loadedGenerator(pos) != null).toList();
            List<Pos> recs = conn.receivers.stream().filter(pos -> !resolver.isLoaded(pos) || loadedReceiver(pos) != null).toList();
            if (!gens.isEmpty() || !recs.isEmpty()) conns.add(new ConnectionData(conn.node, gens, recs));
        }
        return new State(nets, conns);
    }
    /** Restore reconstructs lookups without world reads, capacity/range revalidation, or chunk loads. */
    public void restore(State state) {
        networks.clear(); networkNodes.clear(); connections.clear(); generators.clear(); receivers.clear();
        for (NetworkData data : state.networks) {
            if (data.matrix == null || data.ssid == null || data.password == null || networks.containsKey(data.matrix)) continue;
            Network net = new Network(data.matrix, data.ssid, data.password); net.buffer = bounded(data.buffer, BUFFER_MAX); networks.put(net.matrix, net);
            for (Pos pos : data.nodes) if (pos != null && !networkNodes.containsKey(pos)) { net.nodes.add(pos); networkNodes.put(pos, net); }
        }
        for (ConnectionData data : state.connections) {
            if (data.node == null || connections.containsKey(data.node)) continue;
            Connection conn = new Connection(data.node); connections.put(conn.node, conn);
            for (Pos pos : data.generators) if (pos != null && !generators.containsKey(pos)) { conn.generators.add(pos); generators.put(pos, conn); }
            for (Pos pos : data.receivers) if (pos != null && !receivers.containsKey(pos)) { conn.receivers.add(pos); receivers.put(pos, conn); }
        }
    }
}

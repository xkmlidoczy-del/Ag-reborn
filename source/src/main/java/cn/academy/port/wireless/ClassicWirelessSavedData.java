/* AcademyCraft 1.0.7 WiWorldData/VBlocks native adaptation, GPLv3. See NOTICE. */
package cn.academy.port.wireless;

import cn.academy.port.machine.ImagFluxReceiver;
import cn.academy.port.machine.MachineDeveloperBlockEntity;
import cn.academy.port.machine.MachineDeveloperStructure;
import cn.academy.port.machine.MachineDevelopers;
import cn.academy.port.solar.ClassicSolarGenerators;
import cn.academy.port.solar.ImagFluxGenerator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;

/** Each ServerLevel has independent storage. No world/client/global strong-reference cache. */
public final class ClassicWirelessSavedData extends SavedData {
    public static final String ID = "academy_wireless";
    public static final int SCHEMA_VERSION = 1;
    private final ClassicWirelessGraph graph;
    private ClassicWirelessSavedData(ServerLevel level) {
        graph = new ClassicWirelessGraph(new NativeResolver(level), new java.util.Random(), this::setDirty);
    }
    private static SavedData.Factory<ClassicWirelessSavedData> factory(ServerLevel level) {
        return new SavedData.Factory<>(() -> new ClassicWirelessSavedData(level), (tag, lookup) -> load(level, tag));
    }
    public static ClassicWirelessSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(level), ID);
    }
    public static ClassicWirelessSavedData getNonCreate(ServerLevel level) {
        return level.getDataStorage().get(factory(level), ID);
    }
    public static void register() { ClassicWirelessSystem.register(); }
    public ClassicWirelessGraph graph() { return graph; }
    public static ClassicWirelessGraph.Pos pos(BlockPos pos) { return new ClassicWirelessGraph.Pos(pos.getX(), pos.getY(), pos.getZ()); }
    public static BlockPos blockPos(ClassicWirelessGraph.Pos pos) { return new BlockPos(pos.x(), pos.y(), pos.z()); }
    public static ClassicWirelessSavedData load(ServerLevel level, CompoundTag tag) {
        var data = new ClassicWirelessSavedData(level); data.graph.restore(readState(tag)); return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) { return writeState(tag, graph.snapshot()); }

    /** Legacy logical tag names retained; native schema does not claim 1.7-world compatibility. */
    public static CompoundTag writeState(CompoundTag tag, ClassicWirelessGraph.State state) {
        tag.putInt("schema", SCHEMA_VERSION);
        var networks = new ListTag();
        for (var net : state.networks()) {
            var entry = new CompoundTag(); entry.put("matrix", writePos(net.matrix())); entry.putString("ssid", net.ssid());
            entry.putString("password", net.password()); entry.putDouble("buffer", finiteBuffer(net.buffer())); entry.put("list", writePositions(net.nodes())); networks.add(entry);
        }
        var netTag = new CompoundTag(); netTag.put("networks", networks); tag.put("net", netTag);
        var connections = new ListTag();
        for (var conn : state.connections()) {
            var entry = new CompoundTag(); entry.put("node", writePos(conn.node())); entry.put("generators", writePositions(conn.generators())); entry.put("receivers", writePositions(conn.receivers())); connections.add(entry);
        }
        var nodeTag = new CompoundTag(); nodeTag.put("list", connections); tag.put("node", nodeTag); return tag;
    }
    public static ClassicWirelessGraph.State readState(CompoundTag tag) {
        var networks = new ArrayList<ClassicWirelessGraph.NetworkData>(); var connections = new ArrayList<ClassicWirelessGraph.ConnectionData>();
        ListTag entries = tag.getCompound("net").getList("networks", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i); var matrix = readPos(entry.getCompound("matrix"));
            if (matrix == null || !entry.contains("ssid", Tag.TAG_STRING) || !entry.contains("password", Tag.TAG_STRING)) continue;
            networks.add(new ClassicWirelessGraph.NetworkData(matrix, entry.getString("ssid"), entry.getString("password"), finiteBuffer(entry.getDouble("buffer")), readPositions(entry.getList("list", Tag.TAG_COMPOUND))));
        }
        entries = tag.getCompound("node").getList("list", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i); var node = readPos(entry.getCompound("node")); if (node == null) continue;
            connections.add(new ClassicWirelessGraph.ConnectionData(node, readPositions(entry.getList("generators", Tag.TAG_COMPOUND)), readPositions(entry.getList("receivers", Tag.TAG_COMPOUND))));
        }
        return new ClassicWirelessGraph.State(networks, connections);
    }
    private static double finiteBuffer(double amount) { return Double.isFinite(amount) ? Math.max(0, Math.min(ClassicWirelessGraph.BUFFER_MAX, amount)) : 0; }
    private static CompoundTag writePos(ClassicWirelessGraph.Pos pos) { var tag = new CompoundTag(); tag.putInt("x", pos.x()); tag.putInt("y", pos.y()); tag.putInt("z", pos.z()); return tag; }
    private static ClassicWirelessGraph.Pos readPos(CompoundTag tag) {
        return tag.contains("x", Tag.TAG_INT) && tag.contains("y", Tag.TAG_INT) && tag.contains("z", Tag.TAG_INT) ? new ClassicWirelessGraph.Pos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")) : null;
    }
    private static ListTag writePositions(List<ClassicWirelessGraph.Pos> positions) { var tag = new ListTag(); for (var pos : positions) tag.add(writePos(pos)); return tag; }
    private static List<ClassicWirelessGraph.Pos> readPositions(ListTag tags) { var result = new ArrayList<ClassicWirelessGraph.Pos>(); for (int i = 0; i < tags.size(); i++) { var pos = readPos(tags.getCompound(i)); if (pos != null) result.add(pos); } return result; }

    /** Public for compiled contract tests. Every lookup begins with getChunkNow, never getChunk. */
    public static final class NativeResolver implements ClassicWirelessGraph.Resolver {
        private final ServerLevel level;
        public NativeResolver(ServerLevel level) { this.level = level; }
        @Override public boolean isLoaded(ClassicWirelessGraph.Pos pos) { return pos != null && chunk(pos) != null; }
        private LevelChunk chunk(ClassicWirelessGraph.Pos pos) { return level.getChunkSource().getChunkNow(pos.x() >> 4, pos.z() >> 4); }
        private BlockEntity entity(ClassicWirelessGraph.Pos pos) {
            if (pos == null) return null;
            var chunk = chunk(pos);
            if (chunk == null) return null;
            var entity = chunk.getBlockEntity(blockPos(pos), LevelChunk.EntityCreationType.IMMEDIATE);
            return entity == null || entity.isRemoved() ? null : entity;
        }
        @Override public ImagFluxNode node(ClassicWirelessGraph.Pos pos) { var entity = entity(pos); return entity instanceof ImagFluxNode node ? node : null; }
        @Override public ImagFluxMatrix matrix(ClassicWirelessGraph.Pos pos) { var entity = entity(pos); return entity instanceof ImagFluxMatrix matrix && matrix.isWirelessOrigin() ? matrix : null; }
        @Override public ImagFluxGenerator generator(ClassicWirelessGraph.Pos pos) {
            var entity = entity(pos); if (entity == null) return null;
            var generator=level.getCapability(ClassicSolarGenerators.IMAG_FLUX, blockPos(pos), entity.getBlockState(), entity, null);
            if(generator!=null)return generator;
            if(entity instanceof cn.academy.port.wind.ClassicWindBaseBlockEntity wind&&wind.isOrigin()&&!cn.academy.port.wind.ClassicWindStructure.allLoaded(level,wind.getBlockPos(),wind.getBlockState()))return new ImagFluxGenerator(){public double getEnergy(){return 0;}public double getBandwidth(){return 0;}public double getProvidedEnergy(double request){return 0;}};
            return null;
        }
        @Override public ImagFluxReceiver receiver(ClassicWirelessGraph.Pos pos) {
            var entity = entity(pos); if (entity == null) return null;
            if (entity instanceof MachineDeveloperBlockEntity machine && !machine.isOrigin()) return null;
            var receiver = level.getCapability(MachineDevelopers.IMAG_FLUX, blockPos(pos), entity.getBlockState(), entity, null);
            if (receiver != null) return receiver;
            // A real origin whose other seven cells cross an unloaded chunk must retain its link.
            if (entity instanceof MachineDeveloperBlockEntity machine && !MachineDeveloperStructure.allLoaded(level, machine.getBlockPos(), machine.getBlockState())) return DORMANT_RECEIVER;
            return null;
        }
        @Override public List<ClassicWirelessGraph.Pos> wirelessBlocksWithin(ClassicWirelessGraph.Pos origin, double range, int max) {
            if (origin == null || !Double.isFinite(range) || range < 0 || max <= 0) return List.of();
            // Native source tiers only search20/9/12/19. Guard malformed server calls from enormous scans.
            if (range > 512) return List.of();
            var positions = new ArrayList<ClassicWirelessGraph.Pos>();
            int minX = (int)Math.floor((origin.x() - range) / 16), maxX = (int)Math.floor((origin.x() + range) / 16);
            int minZ = (int)Math.floor((origin.z() - range) / 16), maxZ = (int)Math.floor((origin.z() + range) / 16);
            for (int cx = minX; cx <= maxX; cx++) for (int cz = minZ; cz <= maxZ; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz); if (chunk == null) continue;
                for (var candidate : chunk.getBlockEntitiesPos()) {
                    var pos = pos(candidate); if (origin.distanceSquared(pos) > range * range) continue;
                    if (node(pos) != null || matrix(pos) != null) positions.add(pos);
                }
            }
            positions.sort(Comparator.comparingInt(ClassicWirelessGraph.Pos::x).thenComparingInt(ClassicWirelessGraph.Pos::y).thenComparingInt(ClassicWirelessGraph.Pos::z));
            return List.copyOf(positions.subList(0, Math.min(max, positions.size())));
        }
    }
    private static final ImagFluxReceiver DORMANT_RECEIVER = new ImagFluxReceiver() {
        public double getEnergy() { return 0; }
        public double getMaxEnergy() { return 0; }
        public double getRequiredEnergy() { return 0; }
        public double getBandwidth() { return 0; }
        public double injectEnergy(double amount) { return amount; }
        public double pullEnergy(double amount) { return 0; }
    };
}

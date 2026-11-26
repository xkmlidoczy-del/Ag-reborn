package cn.aca.domain67;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Transforms the restaurant floor in waves and keeps original block states in per-level SavedData.
 * Restoration waits for unloaded chunks without requesting tickets.
 */
public final class DomainTerrainVault extends SavedData {
    private static final String DATA_NAME = AcaDomain67.ID + "_terrain_vault";
    private static final int SILENT_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private record SavedBlockEntry(BlockPos pos, BlockState originalState, BlockState stageState, double radialDist) {}

    private static final class DomainArenaRecord {
        public final UUID ownerId;
        public final BlockPos centerPos;
        public final int maxStageRadius;
        public double appliedWaveRadius = 0.0;
        public boolean collapsing = false;
        public double collapseWaveRadius;
        public final Map<Long, SavedBlockEntry> snapshots = new HashMap<>();

        public DomainArenaRecord(UUID ownerId, BlockPos centerPos, int maxStageRadius) {
            this.ownerId = ownerId;
            this.centerPos = centerPos;
            this.maxStageRadius = maxStageRadius;
            this.collapseWaveRadius = maxStageRadius + 1.0;
        }
    }

    private final Map<UUID, DomainArenaRecord> records = new HashMap<>();
    private final Set<Long> claimedPositions = new HashSet<>();

    public static DomainTerrainVault get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DomainTerrainVault::new, DomainTerrainVault::load),
                DATA_NAME);
    }

    public DomainTerrainVault() {}

    public static DomainTerrainVault load(CompoundTag tag, HolderLookup.Provider registries) {
        DomainTerrainVault vault = new DomainTerrainVault();
        var blockGetter = registries.lookupOrThrow(Registries.BLOCK);
        ListTag domList = tag.getList("Domains", Tag.TAG_COMPOUND);
        for (int i = 0; i < domList.size(); i++) {
            CompoundTag dTag = domList.getCompound(i);
            UUID owner = dTag.getUUID("Owner");
            BlockPos center = new BlockPos(dTag.getInt("CX"), dTag.getInt("CY"), dTag.getInt("CZ"));
            int maxR = dTag.getInt("MaxR");
            DomainArenaRecord rec = new DomainArenaRecord(owner, center, maxR);
            rec.appliedWaveRadius = dTag.getDouble("AppliedR");
            rec.collapsing = dTag.getBoolean("Collapsing");
            rec.collapseWaveRadius = dTag.getDouble("CollapseR");

            ListTag entries = dTag.getList("Blocks", Tag.TAG_COMPOUND);
            for (int j = 0; j < entries.size(); j++) {
                CompoundTag bTag = entries.getCompound(j);
                BlockPos pos = new BlockPos(bTag.getInt("X"), bTag.getInt("Y"), bTag.getInt("Z"));
                BlockState state = NbtUtils.readBlockState(blockGetter, bTag.getCompound("State"));
                double dist = bTag.getDouble("Dist");
                BlockState stage = bTag.contains("Stage", Tag.TAG_COMPOUND)
                        ? NbtUtils.readBlockState(blockGetter, bTag.getCompound("Stage"))
                        : pos.getY() == center.getY() - 1
                                ? pickModernRestaurantFloorBlock(pos.getX() - center.getX(), pos.getZ() - center.getZ(), dist, maxR)
                                : Blocks.AIR.defaultBlockState();
                if (vault.claimedPositions.add(pos.asLong())) {
                    rec.snapshots.put(pos.asLong(), new SavedBlockEntry(pos, state, stage, dist));
                }
            }
            vault.records.put(owner, rec);
        }
        return vault;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag domList = new ListTag();
        for (DomainArenaRecord rec : records.values()) {
            CompoundTag dTag = new CompoundTag();
            dTag.putUUID("Owner", rec.ownerId);
            dTag.putInt("CX", rec.centerPos.getX());
            dTag.putInt("CY", rec.centerPos.getY());
            dTag.putInt("CZ", rec.centerPos.getZ());
            dTag.putInt("MaxR", rec.maxStageRadius);
            dTag.putDouble("AppliedR", rec.appliedWaveRadius);
            dTag.putBoolean("Collapsing", rec.collapsing);
            dTag.putDouble("CollapseR", rec.collapseWaveRadius);

            ListTag entries = new ListTag();
            for (SavedBlockEntry e : rec.snapshots.values()) {
                CompoundTag bTag = new CompoundTag();
                bTag.putInt("X", e.pos.getX());
                bTag.putInt("Y", e.pos.getY());
                bTag.putInt("Z", e.pos.getZ());
                bTag.putDouble("Dist", e.radialDist);
                bTag.put("State", NbtUtils.writeBlockState(e.originalState));
                bTag.put("Stage", NbtUtils.writeBlockState(e.stageState));
                entries.add(bTag);
            }
            dTag.put("Blocks", entries);
            domList.add(dTag);
        }
        tag.put("Domains", domList);
        return tag;
    }

    public void beginArena(ServerLevel level, UUID ownerId, BlockPos centerPos, int stageRadius) {
        if (!AcademyCompat.canTransformTerrain(level)) return;
        restoreImmediate(level, ownerId);
        if (records.containsKey(ownerId)) return;
        records.put(ownerId, new DomainArenaRecord(ownerId, centerPos, stageRadius));
        setDirty();
    }

    public void beginCollapse(ServerLevel level, UUID ownerId) {
        DomainArenaRecord rec = records.get(ownerId);
        if (rec != null && !rec.collapsing) {
            rec.collapsing = true;
            rec.collapseWaveRadius = rec.appliedWaveRadius + 0.5;
            setDirty();
        }
    }

    public boolean hasRecord(UUID ownerId) {
        return records.containsKey(ownerId);
    }

    boolean manages(BlockPos pos) {
        return claimedPositions.contains(pos.asLong());
    }

    /** Advances wave-front block transformation (3.6m/t) during expansion, and wave-back restoration (4.4m/t) during collapse. */
    public void tickLevel(ServerLevel level) {
        if (records.isEmpty()) return;
        boolean mutated = false;
        Iterator<Map.Entry<UUID, DomainArenaRecord>> it = records.entrySet().iterator();
        while (it.hasNext()) {
            DomainArenaRecord rec = it.next().getValue();
            if (!rec.collapsing) {
                if (rec.appliedWaveRadius < rec.maxStageRadius) {
                    double prevR = rec.appliedWaveRadius;
                    double nextR = Math.min(rec.maxStageRadius, prevR + 3.6);
                    transformRing(level, rec, prevR, nextR);
                    rec.appliedWaveRadius = nextR;
                    mutated = true;
                }
            } else if (rec.collapseWaveRadius > 0.0 || level.getGameTime() % 20L == 0L) {
                double nextR = Math.max(0.0, rec.collapseWaveRadius - 4.4);
                mutated |= nextR != rec.collapseWaveRadius;
                rec.collapseWaveRadius = nextR;
                mutated |= restoreRingAbove(level, rec, nextR);
                if (rec.snapshots.isEmpty()) {
                    it.remove();
                    mutated = true;
                }
            }
        }
        if (mutated) setDirty();
    }

    public void restoreImmediate(ServerLevel level, UUID ownerId) {
        DomainArenaRecord rec = records.get(ownerId);
        if (rec == null) return;
        restoreImmediatelyOrDefer(level, rec);
        if (rec.snapshots.isEmpty()) records.remove(ownerId);
        setDirty();
    }

    public void restoreAll(ServerLevel level) {
        if (records.isEmpty()) return;
        records.values().removeIf(rec -> {
            restoreImmediatelyOrDefer(level, rec);
            return rec.snapshots.isEmpty();
        });
        setDirty();
    }

    private void restoreImmediatelyOrDefer(ServerLevel level, DomainArenaRecord rec) {
        rec.collapsing = true;
        rec.collapseWaveRadius = 0.0;
        restoreRingAbove(level, rec, 0.0);
    }

    private void transformRing(ServerLevel level, DomainArenaRecord rec, double rMin, double rMax) {
        int cx = rec.centerPos.getX();
        int floorY = rec.centerPos.getY() - 1;
        int cz = rec.centerPos.getZ();
        int bound = (int) Math.ceil(rMax);
        double rMinSq = rMin * rMin;
        double rMaxSq = rMax * rMax;

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int dx = -bound; dx <= bound; dx++) {
            for (int dz = -bound; dz <= bound; dz++) {
                double dSq = dx * dx + dz * dz;
                if (dSq < rMinSq || dSq > rMaxSq) continue;
                double dist = Math.sqrt(dSq);

                // 1. Transform floor surface at floorY into warm cream-white, honey-oak & gold modern tiles
                mpos.set(cx + dx, floorY, cz + dz);
                if (level.isLoaded(mpos) && canSafelyTransformFloor(level, mpos)) {
                    BlockState tile = pickModernRestaurantFloorBlock(dx, dz, dist, rec.maxStageRadius);
                    transformBlock(level, rec, mpos, tile, dist);
                }

                // 2. Clear natural trees, leaves, bushes & dirt bumps in grand hall headspace (floorY+1 .. floorY+10)
                for (int dy = 10; dy >= 1; dy--) {
                    mpos.set(cx + dx, floorY + dy, cz + dz);
                    if (!level.isLoaded(mpos)) continue;
                    BlockState cur = level.getBlockState(mpos);
                    if (cur.isAir()) continue;
                    if (canSafelyClearHeadspace(level, mpos, cur)) {
                        transformBlock(level, rec, mpos, Blocks.AIR.defaultBlockState(), dist);
                    }
                }
            }
        }
    }

    /**
     * Warm Yellow-White & Champagne-Gold Modern Luxury Restaurant Floor Palette:
     * - Outer Stage Rim: Brushed Gold + Ochre Froglight warm 3000K recessed uplights
     * - Open-Kitchen Chef Island Stage (z in 14..19, |x| <= 6): Chiseled Quartz & Gold inlay
     * - Grand Cross-Aisles (|x| <= 2 or |z| <= 2): Warm Honey-Cream Stripped Birch Parquet + Gold/Ochre-Light borders
     * - Main Dining Hall: 2x2 Smooth Quartz & Calcite / Quartz Bricks warm ivory marble checkerboard
     */
    private static BlockState pickModernRestaurantFloorBlock(int dx, int dz, double dist, int maxR) {
        if (dist > maxR - 1.15) {
            return (((dx + dz) & 3) == 0)
                    ? Blocks.OCHRE_FROGLIGHT.defaultBlockState()
                    : Blocks.GOLD_BLOCK.defaultBlockState();
        }
        // Open-Kitchen Chef Island Podium
        if (dz >= 14 && dz <= 19 && Math.abs(dx) <= 6) {
            if (Math.abs(dx) == 6 || dz == 14 || dz == 19) {
                return Blocks.GOLD_BLOCK.defaultBlockState();
            }
            return Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
        }
        // Grand Modern Cross-Aisle Runways (Warm Honey-Oak Parquet + Recessed 3000K Uplights)
        boolean inXAisle = Math.abs(dx) <= 2 && Math.abs(dz) <= maxR - 2;
        boolean inZAisle = Math.abs(dz) <= 2 && Math.abs(dx) <= maxR - 2;
        if (inXAisle || inZAisle) {
            int minAbs = Math.min(Math.abs(dx), Math.abs(dz));
            if (minAbs == 2) {
                int spanCoord = (Math.abs(dx) == 2) ? dz : dx;
                return (Math.floorMod(spanCoord, 4) == 0)
                        ? Blocks.OCHRE_FROGLIGHT.defaultBlockState()
                        : Blocks.GOLD_BLOCK.defaultBlockState();
            }
            return Blocks.STRIPPED_BIRCH_WOOD.defaultBlockState();
        }
        // Warm Ivory-Cream & Pearl Marble 2x2 Checkered Dining Floor (with subtle recessed warm floor lights under booths)
        if (Math.abs(Math.abs(dx) - 9) <= 1 && Math.floorMod(dz, 7) == 0) {
            return Blocks.OCHRE_FROGLIGHT.defaultBlockState();
        }
        int tx = Math.floorDiv(dx, 2);
        int tz = Math.floorDiv(dz, 2);
        return (((tx + tz) & 1) == 0)
                ? Blocks.SMOOTH_QUARTZ.defaultBlockState()
                : Blocks.QUARTZ_BRICKS.defaultBlockState();
    }

    private static boolean canSafelyTransformFloor(ServerLevel level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.hasBlockEntity()) return false;
        if (st.is(BlockTags.PORTALS) || st.is(Blocks.BEDROCK) || st.is(Blocks.END_PORTAL_FRAME)
                || st.is(Blocks.BARRIER) || st.is(Blocks.COMMAND_BLOCK)) {
            return false;
        }
        float speed = st.getDestroySpeed(level, pos);
        if (speed < 0f && !st.isAir()) return false;
        return true;
    }

    private static boolean canSafelyClearHeadspace(ServerLevel level, BlockPos pos, BlockState st) {
        if (st.hasBlockEntity()) return false;
        if (st.getDestroySpeed(level, pos) < 0f) return false;
        if (st.is(BlockTags.PORTALS)) return false;
        return st.is(BlockTags.LEAVES)
                || st.is(BlockTags.LOGS)
                || st.is(BlockTags.FLOWERS)
                || st.is(BlockTags.REPLACEABLE)
                || st.is(BlockTags.DIRT)
                || st.is(BlockTags.SAND)
                || st.is(BlockTags.SNOW)
                || st.is(Blocks.TALL_GRASS)
                || st.is(Blocks.SHORT_GRASS)
                || st.is(Blocks.VINE);
    }

    private void transformBlock(ServerLevel level, DomainArenaRecord rec, BlockPos pos, BlockState stage, double dist) {
        long key = pos.asLong();
        if (claimedPositions.contains(key)) return;
        BlockState original = level.getBlockState(pos);
        if (original == stage || !level.setBlock(pos, stage, SILENT_FLAGS)) return;
        rec.snapshots.put(key, new SavedBlockEntry(pos.immutable(), original, stage, dist));
        claimedPositions.add(key);
    }

    private boolean restoreRingAbove(ServerLevel level, DomainArenaRecord rec, double thresholdDist) {
        List<SavedBlockEntry> toRestore = new ArrayList<>();
        for (SavedBlockEntry e : rec.snapshots.values()) {
            if (e.radialDist >= thresholdDist && level.isLoaded(e.pos)) toRestore.add(e);
        }
        toRestore.sort(Comparator.comparingInt(e -> e.pos.getY()));
        boolean restored = false;
        for (SavedBlockEntry e : toRestore) {
            if (level.getBlockState(e.pos) == e.stageState && !level.setBlock(e.pos, e.originalState, SILENT_FLAGS)) {
                continue;
            }
            long key = e.pos.asLong();
            rec.snapshots.remove(key);
            claimedPositions.remove(key);
            restored = true;
        }
        return restored;
    }
}

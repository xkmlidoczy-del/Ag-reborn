package cn.academy.port.highpower.environment;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/** Invisible temporary vacancy, with its original state saved atomically with the owning chunk. */
final class TemporaryEnvironmentBlock extends BaseEntityBlock {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("academy");
    private static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "academy");
    private static final DeferredHolder<Block, TemporaryEnvironmentBlock> MARKER = BLOCKS.registerBlock("temporary_skill_vacancy",
            TemporaryEnvironmentBlock::new, Properties.of().noCollission().noOcclusion().replaceable()
                    .strength(-1, 3600000).pushReaction(PushReaction.BLOCK).noLootTable());
    private static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Vacancy>> TILE = TILES.register("temporary_skill_vacancy",
            () -> BlockEntityType.Builder.of(Vacancy::new, MARKER.get()).build(null));
    private static final MapCodec<TemporaryEnvironmentBlock> CODEC = simpleCodec(TemporaryEnvironmentBlock::new);
    private static final ThreadLocal<Placement> PLACING = new ThreadLocal<>();
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private record Record(UUID id, GameProfile owner, BlockState original, long expires) {}
    private record Placement(ServerLevel world, BlockPos pos, Record record) {}

    private TemporaryEnvironmentBlock(Properties properties) { super(properties); }

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        TILES.register(bus);
    }

    static BlockState marker() { return MARKER.get().defaultBlockState(); }

    static boolean place(ServerLevel world, ServerPlayer owner, BlockPos pos, BlockState original, int lifetime) {
        if (PLACING.get() != null) return false;
        var record = new Record(UUID.randomUUID(), owner.getGameProfile(), original, world.getGameTime() + lifetime);
        var index = Index.get(world);
        if (!index.remember(pos, record.id())) return false;
        boolean ownsMarker = false;
        PLACING.set(new Placement(world, pos, record));
        try {
            world.setBlock(pos, marker(), UPDATE_FLAGS);
            if (world.getBlockEntity(pos) instanceof Vacancy tile && tile.record == record) {
                ownsMarker = true;
                tile.setChanged();
            }
            return ownsMarker;
        } finally {
            PLACING.remove();
            if (!ownsMarker) {
                // An exception after placement can still leave an initialized, recoverable marker.
                if (!(world.getBlockEntity(pos) instanceof Vacancy tile && tile.record == record))
                    index.forget(pos, record.id());
            }
        }
    }

    @Override protected MapCodec<TemporaryEnvironmentBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Vacancy(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, TILE.get(), Vacancy::tick);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!next.is(this) && level instanceof ServerLevel world
                && level.getBlockEntity(pos) instanceof Vacancy tile && tile.record != null) {
            Index.get(world).forget(pos, tile.record.id());
        }
        super.onRemove(state, level, pos, next, moving);
    }

    static void reapIndex(ServerTickEvent.Post event) {
        if (Math.floorMod(event.getServer().getTickCount(), 20) != 0) return;
        for (ServerLevel world : event.getServer().getAllLevels()) {
            Index index = Index.existing(world);
            if (index != null) index.reap(world);
        }
    }

    private static final class Vacancy extends BlockEntity {
        private Record record;
        private long retry;
        private boolean initialized;
        private String loggedConflict;

        Vacancy(BlockPos pos, BlockState state) { super(TILE.get(), pos, state); }

        @Override public void setLevel(Level level) {
            super.setLevel(level);
            Placement placement = PLACING.get();
            if (record == null && placement != null && placement.world() == level
                    && placement.pos().equals(worldPosition)) record = placement.record();
        }

        @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (record == null) return;
            tag.putUUID("transaction", record.id());
            tag.putUUID("owner", record.owner().getId());
            tag.putString("ownerName", record.owner().getName());
            tag.putLong("expires", record.expires());
            tag.put("original", NbtUtils.writeBlockState(record.original()));
        }

        @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            record = null;
            if (!tag.hasUUID("transaction") || !tag.hasUUID("owner") || !tag.contains("original", Tag.TAG_COMPOUND)
                    || !tag.contains("expires", Tag.TAG_LONG) || tag.getLong("expires") < 0) return;
            String name = tag.getString("ownerName");
            if (name.isBlank() || name.length() > 16) return;
            BlockState original = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("original"));
            if (SkillEnvironment.eligibleOriginal(original)) {
                record = new Record(tag.getUUID("transaction"), new GameProfile(tag.getUUID("owner"), name),
                        original, tag.getLong("expires"));
            }
        }

        static void tick(Level level, BlockPos pos, BlockState state, Vacancy tile) {
            if (!(level instanceof ServerLevel world)) return;
            if (tile.record == null) {
                tile.conflict("invalid transaction payload");
                return;
            }
            var index = Index.get(world);
            if (!tile.initialized) {
                tile.initialized = true;
                // Cross-file saves can leave a missing index row. The marker owns the actual state.
                index.observe(pos, tile.record.id());
            }
            long now = world.getGameTime();
            if (now < tile.record.expires() || now < tile.retry) return;
            if (!SkillEnvironment.reserveRestoration(world)) return;
            tile.retry = now + 20 + Math.floorMod(pos.asLong(), 7);
            tile.restore(world, pos, index);
        }

        private void restore(ServerLevel world, BlockPos pos, Index index) {
            if (!SkillEnvironment.knownClaimPolicy()) {
                conflict("unverified claim mod");
                return;
            }
            ServerPlayer owner = index.actor(world, record.owner());
            if (owner == null || !canRestore(world, pos, owner)) {
                conflict("permission, support or occupancy");
                return;
            }
            if (!SkillEnvironment.placeAllowed(world, owner, pos, record.original())
                    || !canRestore(world, pos, owner)) {
                conflict("placement protection or callback conflict");
                return;
            }
            if (world.setBlock(pos, record.original(), UPDATE_FLAGS)
                    && world.getBlockState(pos) == record.original()) index.forget(pos, record.id());
        }

        private boolean canRestore(ServerLevel world, BlockPos pos, ServerPlayer owner) {
            LevelChunk chunk = SkillEnvironment.residentChunk(world, pos);
            if (chunk == null || !SkillEnvironment.knownClaimPolicy()
                    || !SkillEnvironment.canBuild(world, owner, pos) || chunk.getBlockState(pos) != marker()
                    || chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) != this
                    || !SkillEnvironment.eligibleNeighbors(world, pos) || !record.original().canSurvive(world, pos)) return false;
            var occupants = new ArrayList<Entity>(1);
            world.getEntities(EntityTypeTest.forClass(Entity.class), new AABB(pos),
                    entity -> entity.isAlive() && !entity.isSpectator(), occupants, 1);
            return occupants.isEmpty();
        }

        private void conflict(String reason) {
            if (reason.equals(loggedConflict)) return;
            loggedConflict = reason;
            LOGGER.warn("ACA environment restoration deferred at {}: {}", worldPosition, reason);
        }
    }

    /** Bounded persisted index only. Each vacancy chunk is the authority for its own original data. */
    private static final class Index extends SavedData {
        private static final int MAX_PENDING = 1024, REAP_BUDGET = 16, MAX_FAKE_ACTORS = 16;
        private static final String ID = "academy_skill_environment";
        private static final SavedData.Factory<Index> FACTORY = new SavedData.Factory<>(Index::new, Index::load);
        private final Map<BlockPos, UUID> rows = new HashMap<>();
        private final ArrayDeque<BlockPos> rotation = new ArrayDeque<>();
        private final LinkedHashMap<UUID, FakePlayer> fakeActors = new LinkedHashMap<>(16, .75f, true);

        static Index get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(FACTORY, ID); }
        static Index existing(ServerLevel level) { return level.getDataStorage().get(FACTORY, ID); }

        boolean remember(BlockPos pos, UUID id) {
            if (rows.containsKey(pos) || rows.size() >= MAX_PENDING) return false;
            add(pos.immutable(), id);
            setDirty();
            return true;
        }

        void observe(BlockPos pos, UUID id) {
            UUID known = rows.get(pos);
            if (known == null) {
                remember(pos, id);
            } else if (!known.equals(id)) {
                // A chunk marker can be newer than the separately saved limit index.
                rows.put(pos.immutable(), id);
                setDirty();
            }
        }

        private void add(BlockPos pos, UUID id) {
            rows.put(pos, id);
            rotation.addLast(pos);
        }

        void forget(BlockPos pos, UUID id) {
            if (rows.remove(pos, id)) {
                rotation.remove(pos);
                setDirty();
            }
        }

        void reap(ServerLevel world) {
            for (int i = Math.min(REAP_BUDGET, rotation.size()); i > 0; i--) {
                BlockPos pos = rotation.removeFirst();
                var chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk != null && !chunk.getBlockState(pos).is(MARKER.get())) {
                    rows.remove(pos);
                    setDirty();
                } else {
                    rotation.addLast(pos);
                }
            }
        }

        ServerPlayer actor(ServerLevel world, GameProfile profile) {
            ServerPlayer connected = world.getServer().getPlayerList().getPlayer(profile.getId());
            if (connected != null) {
                if (connected.isSpectator() || !connected.getAbilities().mayBuild) return null;
                if (connected.serverLevel() == world && !connected.hasDisconnected()) return connected;
            }
            // Only used when unverified claim mods are absent. Native spawn and place hooks
            // receive the actual original profile and still have full veto authority.
            FakePlayer fake = fakeActors.computeIfAbsent(profile.getId(), ignored -> new FakePlayer(world, profile));
            if (fakeActors.size() > MAX_FAKE_ACTORS) fakeActors.remove(fakeActors.keySet().iterator().next());
            return fake;
        }

        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            var list = new ListTag();
            rows.forEach((pos, id) -> {
                var row = new CompoundTag();
                row.putLong("pos", pos.asLong());
                row.putUUID("transaction", id);
                list.add(row);
            });
            tag.put("transactions", list);
            return tag;
        }

        private static Index load(CompoundTag tag, HolderLookup.Provider registries) {
            var index = new Index();
            for (Tag value : tag.getList("transactions", Tag.TAG_COMPOUND)) {
                var row = (CompoundTag) value;
                if (index.rows.size() >= MAX_PENDING) break;
                if (!row.hasUUID("transaction") || !row.contains("pos", Tag.TAG_LONG)) continue;
                BlockPos pos = BlockPos.of(row.getLong("pos"));
                if (!index.rows.containsKey(pos)) index.add(pos, row.getUUID("transaction"));
            }
            return index;
        }
    }
}

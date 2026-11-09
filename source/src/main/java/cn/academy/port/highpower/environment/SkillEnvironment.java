package cn.academy.port.highpower.environment;

import cn.academy.port.AcademyConfig;
import cn.academy.port.api.SkillBlockDestroyEvent;
import cn.academy.port.highpower.EsperShields;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Optional extra world actions. The caller retains all skill geometry and the core combat result. */
public final class SkillEnvironment {
    public enum Mode { OFF, COSMETIC, RESTORE, PERMANENT }

    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.EnumValue<Mode> ENVIRONMENT_DAMAGE;
    private static final int MAX_BLOCK_CANDIDATES = 128, MAX_BLOCK_CHECKS_PER_TICK = 256;
    private static final int MAX_NEW_WRITES_PER_TICK = 32, MAX_RESTORE_WRITES_PER_TICK = 16;
    private static final int MAX_ENTITY_CANDIDATES = 64, MAX_PUSHES = 16, MAX_PUSHES_PER_TICK = 64;
    private static final Map<MinecraftServer, Budget> BUDGETS = new WeakHashMap<>();
    // Native events remain the protection path for all other listeners.
    private static final List<String> UNVERIFIED_CLAIMS = List.of(
            "ftbchunks", "openpartiesandclaims", "simpleclaims", "cadmus", "landclaim");

    static {
        var builder = new ModConfigSpec.Builder();
        ENVIRONMENT_DAMAGE = builder.comment(
                "OFF, COSMETIC, RESTORE, or PERMANENT. Only managed ability environment effects use this mode.",
                "The two Academy story city dimensions never permit managed block writes.")
                .defineEnum("environmentDamage", Mode.RESTORE);
        SPEC = builder.build();
    }

    private SkillEnvironment() {}

    public static void register(IEventBus bus, ModContainer container) {
        TemporaryEnvironmentBlock.register(bus);
        NeoForge.EVENT_BUS.addListener(TemporaryEnvironmentBlock::reapIndex);
        container.registerConfig(ModConfig.Type.SERVER, SPEC, "academy-environment.toml");
    }

    public static boolean showsDebris() {
        return SPEC.isLoaded() && ENVIRONMENT_DAMAGE.get() != Mode.OFF;
    }

    public static Mode mode(Level level) {
        if (level == null || !SPEC.isLoaded()) return Mode.OFF;
        Mode selected = ENVIRONMENT_DAMAGE.get();
        return protectedCity(level) && selected.ordinal() > Mode.COSMETIC.ordinal() ? Mode.COSMETIC : selected;
    }

    public static boolean isVacancy(BlockState state) {
        return state == TemporaryEnvironmentBlock.marker();
    }

    public static boolean protectedCity(Level level) {
        var id = level.dimension().location();
        return "academy".equals(id.getNamespace())
                && ("story_city".equals(id.getPath()) || "story_city_map".equals(id.getPath()));
    }

    /** Use only after a successful server-side cast, never from an aim preview or a client request. */
    public static Cast begin(ServerPlayer owner, String qualifiedSkill, int skillLevel) {
        return new Cast(owner, qualifiedSkill, skillLevel);
    }

    public record Counts(int blockChecks, int blockWriteAttempts, int restorationAttempts,
                         int entityCandidates, int pushAttempts) {}

    public static Counts counts(MinecraftServer server) {
        if (!server.isSameThread()) return new Counts(0, 0, 0, 0, 0);
        var budget = budget(server);
        return new Counts(budget.blockChecks, budget.blockWrites, budget.restorations,
                budget.entityCandidates, budget.pushes);
    }

    public static final class Cast {
        private final ServerPlayer owner;
        private final ServerLevel world;
        private final String skill;
        private final int level;
        private final Set<BlockPos> blocks = new HashSet<>();
        private final Set<UUID> entities = new HashSet<>();
        private int blockWrites, pushes;
        private boolean insideHook;

        private Cast(ServerPlayer owner, String skill, int level) {
            this.owner = owner;
            this.world = owner.serverLevel();
            this.skill = skill;
            this.level = switch (skill) {
                case "electromaster.railgun", "aero_hand.aero_ground_blast" -> level == 4 ? level : 0;
                case "vecmanip.plasma_cannon" -> level == 5 ? level : 0;
                default -> 0;
            };
        }

        public boolean allowsBlockChanges() {
            Mode current = mode(world);
            return liveOwner() && !skill.equals("aero_hand.aero_ground_blast")
                    && (current == Mode.RESTORE || current == Mode.PERMANENT)
                    && world.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                    && AcademyConfig.contextTerrain(world, skill) && knownClaimPolicy();
        }

        /** True only if the candidate changed. A protected or otherwise refused cell stops a penetration ray. */
        public boolean breakBlock(BlockPos candidate, boolean drops) {
            return breakBlock(candidate, () -> drops);
        }

        public boolean breakBlock(BlockPos candidate, BooleanSupplier drops) {
            if (insideHook || !allowsBlockChanges() || blocks.size() >= MAX_BLOCK_CANDIDATES
                    || blockWrites >= (level == 4 ? 12 : 24)) return false;
            BlockPos pos = candidate.immutable();
            var budget = budget(world.getServer());
            if (!blocks.add(pos) || budget.blockChecks + budget.entityCandidates >= MAX_BLOCK_CHECKS_PER_TICK
                    || budget.blockWrites >= MAX_NEW_WRITES_PER_TICK) return false;
            budget.blockChecks++;
            var chunk = residentChunk(world, pos);
            if (chunk == null || !canBuild(world, owner, pos)) return false;
            BlockState original = chunk.getBlockState(pos);
            if (!eligibleOriginal(original) || !eligibleNeighbors(world, pos)) return false;
            Mode current = mode(world);
            var cell = new Cell(world.dimension(), pos);
            if (!budget.activeCells.add(cell)) return false;
            insideHook = true;
            try {
                if (FlanEnvironmentProtection.loaded() && !unchanged(pos, chunk, original, current)) return false;
                if (NeoForge.EVENT_BUS.post(new SkillBlockDestroyEvent(owner, skill, pos)).isCanceled()
                        || !unchanged(pos, chunk, original, current)) return false;
                if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world, pos, original, owner)).isCanceled()
                        || !unchanged(pos, chunk, original, current)) return false;
                if (current == Mode.RESTORE && (!placeAllowed(world, owner, pos, TemporaryEnvironmentBlock.marker())
                        || !unchanged(pos, chunk, original, current))) return false;
                if (budget.blockWrites >= MAX_NEW_WRITES_PER_TICK) return false;
                blockWrites++;
                budget.blockWrites++;
                if (current == Mode.RESTORE)
                    return TemporaryEnvironmentBlock.place(world, owner, pos, original, level == 4 ? 600 : 1200);
                boolean drop = drops.getAsBoolean();
                return unchanged(pos, chunk, original, current) && destroy(world, owner, pos, original, drop);
            } finally {
                insideHook = false;
                budget.activeCells.remove(cell);
            }
        }

        /** Adds one optional impulse after the caller excludes entities already moved by core combat. */
        public boolean push(LivingEntity target, Vec3 direction, double strength) {
            if (insideHook || !liveOwner() || mode(world) == Mode.OFF || !knownClaimPolicy() || FlanEnvironmentProtection.loaded()
                    || !world.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                    || pushes >= MAX_PUSHES || entities.size() >= MAX_ENTITY_CANDIDATES
                    || !Double.isFinite(direction.x) || !Double.isFinite(direction.z) || !Double.isFinite(strength)
                    || strength <= 0 || (direction.x == 0 && direction.z == 0)) return false;
            var budget = budget(world.getServer());
            if (budget.pushes >= MAX_PUSHES_PER_TICK || budget.blockChecks + budget.entityCandidates >= MAX_BLOCK_CHECKS_PER_TICK
                    || !entities.add(target.getUUID())) return false;
            budget.entityCandidates++;
            if (target == owner || !target.isAlive() || target.level() != world || target.isInvulnerable()
                    || target.isSpectator() || target.isPassenger() || target.isVehicle()
                    || owner.isAlliedTo(target) || target.isAlliedTo(owner) || EsperShields.holding(target)
                    || target.getPersistentData().getBoolean("academy:protected")
                    || residentChunk(world, target.blockPosition()) == null
                    || !world.mayInteract(owner, target.blockPosition())) return false;
            if (target instanceof ServerPlayer other) {
                if (!AcademyConfig.ATTACK_PLAYERS.get() || !owner.server.isPvpAllowed() || other.hasDisconnected()
                        || other.getAbilities().invulnerable || !owner.canHarmPlayer(other)
                        || !other.canHarmPlayer(owner)) return false;
            } else if (!(target instanceof Enemy)) return false;
            insideHook = true;
            pushes++;
            budget.pushes++;
            try {
                Vec3 before = target.getDeltaMovement();
                target.knockback(Math.min(.65, strength), -direction.x, -direction.z);
                boolean moved = !target.getDeltaMovement().equals(before);
                if (moved) target.hurtMarked = true;
                return moved;
            } finally {
                insideHook = false;
            }
        }

        private boolean liveOwner() {
            return level != 0 && world.getServer().isSameThread() && owner.serverLevel() == world
                    && owner.isAlive() && !owner.isSpectator() && !owner.hasDisconnected();
        }

        private boolean unchanged(BlockPos pos, LevelChunk chunk, BlockState state, Mode current) {
            return FlanEnvironmentProtection.permits(owner, pos, FlanEnvironmentProtection.BREAK)
                    && mode(world) == current && allowsBlockChanges() && canBuild(world, owner, pos)
                    && residentChunk(world, pos) == chunk && chunk.getBlockState(pos) == state
                    && chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) == null
                    && eligibleNeighbors(world, pos);
        }
    }

    private static boolean destroy(ServerLevel world, ServerPlayer owner, BlockPos pos, BlockState original, boolean drops) {
        // The vanilla drop helper can spawn entities that invoke foreign code. Commit the
        // vacancy first so a later callback cannot be overwritten by a second block write.
        if (!world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) return false;
        world.levelEvent(2001, pos, Block.getId(original));
        if (drops && world.getBlockState(pos).isAir())
            Block.dropResources(original, world, pos, null, owner, ItemStack.EMPTY);
        world.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(owner, original));
        return true;
    }

    static boolean placeAllowed(ServerLevel world, ServerPlayer owner, BlockPos pos, BlockState planned) {
        var snapshot = BlockSnapshot.create(world.dimension(), world, pos);
        var event = new BlockEvent.EntityPlaceEvent(snapshot, world.getBlockState(pos), owner) {
            @Override public BlockState getState() { return planned; }
            @Override public BlockState getPlacedBlock() { return planned; }
        };
        return !NeoForge.EVENT_BUS.post(event).isCanceled()
                && FlanEnvironmentProtection.permits(owner, pos, FlanEnvironmentProtection.PLACE);
    }

    static boolean canBuild(ServerLevel world, ServerPlayer owner, BlockPos pos) {
        return !world.captureBlockSnapshots && !world.restoringBlockSnapshots && !owner.isSpectator()
                && owner.getAbilities().mayBuild && world.mayInteract(owner, pos);
    }

    static boolean knownClaimPolicy() {
        return !ClaimMods.OTHER_UNVERIFIED && FlanEnvironmentProtection.available();
    }

    private static final class ClaimMods {
        private static final boolean OTHER_UNVERIFIED = UNVERIFIED_CLAIMS.stream().anyMatch(ModList.get()::isLoaded);
    }

    static LevelChunk residentChunk(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return null;
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    }

    static boolean eligibleOriginal(BlockState state) {
        if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
        if (state.is(Blocks.STONE) || state.is(Blocks.GLASS) || state.is(Blocks.TINTED_GLASS) || state.is(Blocks.SHORT_GRASS)) return true;
        return "minecraft".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())
                && state.is(BlockTags.LEAVES) && state.hasProperty(BlockStateProperties.PERSISTENT)
                && state.getValue(BlockStateProperties.PERSISTENT);
    }

    static boolean eligibleNeighbors(ServerLevel level, BlockPos center) {
        for (Direction direction : Direction.values()) {
            BlockPos pos = center.relative(direction);
            LevelChunk chunk = residentChunk(level, pos);
            if (chunk == null) return false;
            BlockState state = chunk.getBlockState(pos);
            if (!state.isAir() && !isVacancy(state) && !state.is(Blocks.STONE) && !state.is(Blocks.COBBLESTONE)
                    && !state.is(Blocks.DIRT) && !state.is(Blocks.GRASS_BLOCK)
                    && !(eligibleOriginal(state) && !state.is(Blocks.SHORT_GRASS))) return false;
        }
        return true;
    }

    static boolean reserveRestoration(ServerLevel world) {
        var budget = budget(world.getServer());
        if (budget.restorations >= MAX_RESTORE_WRITES_PER_TICK) return false;
        budget.restorations++;
        return true;
    }

    private static Budget budget(MinecraftServer server) {
        var value = BUDGETS.computeIfAbsent(server, ignored -> new Budget());
        int tick = server.getTickCount();
        if (value.tick != tick) {
            value.tick = tick;
            value.blockChecks = value.blockWrites = value.restorations = value.entityCandidates = value.pushes = 0;
            value.activeCells.clear();
        }
        return value;
    }

    private record Cell(ResourceKey<Level> dimension, BlockPos pos) {}
    private static final class Budget {
        private final Set<Cell> activeCells = new HashSet<>();
        private int tick = Integer.MIN_VALUE;
        private int blockChecks, blockWrites, restorations, entityCandidates, pushes;
    }
}

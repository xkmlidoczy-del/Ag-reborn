package cn.academy.port.highpower;

import cn.academy.port.AcademyConfig;
import cn.academy.port.api.SkillBlockDestroyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Closed NEW213/NEW231/NEW233 native harvest adapter. NEW231/NEW233 enter only with their controller's
 * opaque, one-use permit; callers cannot select an arbitrary skill, range or policy.
 * Native destroyBlock, fireBlockBreak, mineBlock, onDestroyedByPlayer and ordinary
 * playerDestroy run once; this bridge never generates loot or substitutes an AIR write.
 */
public final class BeamEtchingMining {
    private enum OwnerPolicy {
        ETCHING("beam_etching"), SWEEP("disintegration_sweep"), POINT("point_meltdown");
        final String skill;
        OwnerPolicy(String skill) { this.skill = skill; }
    }
    private static final ThreadLocal<Permit> ACTIVE = new ThreadLocal<>();
    public record Target(BlockPos pos, BlockState state, Vec3 surface, Direction face) {
        public Target { if (pos != null) pos = pos.immutable(); }
    }
    public record Eligibility(boolean eligible, String reason, ItemStack drop) {}
    public record Result(boolean removed, String reason) {}

    /**
     * A conservative per-cast admission ledger, never ownership of drops on the floor.
     * We replay every previous admitted drop over the current 36 main slots. Even if a
     * previous drop was already picked up, it remains reserved for this cast. We promise
     * neither remote pickup nor space when someone later changes the inventory.
     */
    public static final class Capacity {
        private final List<ItemStack> reserved = new ArrayList<>();
        private Capacity() {}
        public int reservations() { return reserved.size(); }
        private boolean fits(ServerPlayer player, ItemStack next) {
            if (reserved.size() >= 32 || next.isEmpty()) return false;
            List<ItemStack> slots = new ArrayList<>(36);
            for (int i = 0; i < 36; i++) slots.add(player.getInventory().getItem(i).copy());
            for (ItemStack previous : reserved) if (!insert(slots, previous)) return false;
            return insert(slots, next);
        }
        private void commit(ItemStack drop) { reserved.add(drop.copy()); }
    }
    private BeamEtchingMining() {}
    public static Capacity newCapacity() { return new Capacity(); }
    private static boolean insert(List<ItemStack> slots, ItemStack input) {
        int remaining = input.getCount();
        for (ItemStack stack : slots) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, input)) {
                int moved = Math.min(remaining, Math.max(0, Math.min(64, stack.getMaxStackSize()) - stack.getCount()));
                stack.grow(moved); remaining -= moved;
                if (remaining == 0) return true;
            }
        }
        for (int i = 0; i < slots.size(); i++) if (slots.get(i).isEmpty()) {
            int moved = Math.min(remaining, Math.min(64, input.getMaxStackSize()));
            slots.set(i, input.copyWithCount(moved)); remaining -= moved;
            if (remaining == 0) return true;
        }
        return false;
    }
    public static Eligibility inspect(ServerPlayer player, Target target, Capacity capacity) {
        return inspect(OwnerPolicy.ETCHING, player, target, null, capacity);
    }
    /** A read-only preview still needs a live, controller-owned frozen-surface capability. */
    public static Eligibility inspectSweep(DisintegrationSweep.MiningPermit permit, Capacity capacity) {
        if (permit == null || !permit.valid()) return new Eligibility(false, "Sweep owner changed", ItemStack.EMPTY);
        return inspect(OwnerPolicy.SWEEP, permit.player(), permit.target(), permit.origin(), capacity);
    }
    public static Eligibility inspectPoint(PointMeltdown.MiningPermit permit, Capacity capacity) {
        if (permit == null || !permit.valid()) return new Eligibility(false, "Point owner changed", ItemStack.EMPTY);
        return inspect(OwnerPolicy.POINT, permit.player(), permit.target(), permit.origin(), capacity);
    }
    private static Eligibility inspect(OwnerPolicy policy, ServerPlayer player, Target target, Vec3 origin, Capacity capacity) {
        String reason = basicReason(policy, player, target, origin);
        if (reason != null) return new Eligibility(false, reason, ItemStack.EMPTY);
        var unsupported = VanillaMiningContract.unsupportedReason(player);
        if (unsupported.isPresent()) return new Eligibility(false, unsupported.get(), ItemStack.EMPTY);
        if (!VanillaMiningContract.reviewedTool(player.getMainHandItem())) return new Eligibility(false, "Unreviewed held tool or components", ItemStack.EMPTY);
        if (policy == OwnerPolicy.POINT && !VanillaMiningContract.reviewedPointHarvestTool(target.state, player.getMainHandItem()))
            return new Eligibility(false, "Held tool cannot harvest the point target", ItemStack.EMPTY);
        if (!canonicalLoot(policy, player, target.state)) return new Eligibility(false, "Unreviewed loot resource/provenance", ItemStack.EMPTY);
        ItemStack drop = policy == OwnerPolicy.POINT ? VanillaMiningContract.expectedPointDrop(target.state) : VanillaMiningContract.expectedDrop(target.state);
        if (capacity == null || !capacity.fits(player, drop)) return new Eligibility(false, "Main inventory lacks conservatively reserved capacity", ItemStack.EMPTY);
        return new Eligibility(true, "", drop);
    }
    private static String basicReason(OwnerPolicy policy, ServerPlayer player, Target target, Vec3 origin) {
        if (player == null || target == null || target.pos == null || target.state == null || target.surface == null || target.face == null)
            return "Missing target";
        if (!HighPowerResources.ready(player) || player.connection == null
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player) return "Owner is not live";
        if (!HighPowerResources.reservationEligible(player, policy.skill)) return "Mining skill owner is no longer eligible";
        if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL || !player.getAbilities().mayBuild) return "Survival mining only";
        var level = player.serverLevel();
        if (level.captureBlockSnapshots || level.restoringBlockSnapshots) return "Deferred or restoring block snapshot scope is unsupported";
        BlockPos pos = target.pos;
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return "Target outside loaded world bounds";
        if (level.getBlockState(pos) != target.state || !(policy == OwnerPolicy.POINT
                ? VanillaMiningContract.reviewedPointBlock(target.state) : VanillaMiningContract.reviewedBlock(target.state))
                || level.getBlockEntity(pos) != null || !level.getFluidState(pos).isEmpty()) return "Unreviewed or changed target";
        float hardness = target.state.getDestroySpeed(level, pos);
        if (!Float.isFinite(hardness) || hardness < 0 || hardness > (policy == OwnerPolicy.POINT ? 2 : 1)) return "Unsupported block hardness";
        if (!AcademyConfig.contextTerrain(level, "meltdowner", policy.skill) || !level.mayInteract(player, pos)
                || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) return "Terrain permission denied";
        if (policy == OwnerPolicy.ETCHING) {
            // Retain NEW213's exact eye-origin visibility and twelve-metre contract.
            if (!BeamEtching.visible(player, target)) return "Target is no longer first visible surface within range";
        } else {
            // Each unforgeable controller permit rechecks its real origin, frozen target
            // and first-surface trace. Repeat its own closed bound at every native seam.
            double range = policy == OwnerPolicy.SWEEP ? 48 : 36;
            if (!finite(origin) || !finite(target.surface) || origin.distanceToSqr(target.surface) > range * range)
                return policy == OwnerPolicy.SWEEP ? "Sweep target exceeds forty-eight-metre native range"
                        : "Point target exceeds thirty-six-metre native range";
        }
        return null;
    }
    private static boolean canonicalLoot(OwnerPolicy policy, ServerPlayer player, BlockState state) {
        return policy == OwnerPolicy.POINT ? VanillaMiningContract.canonicalPointLoot(player, state)
                : VanillaMiningContract.canonicalLoot(player, state);
    }
    public static Result attempt(ServerPlayer player, Target target, double cost, BooleanSupplier ownerValid, Capacity capacity) {
        return attempt(OwnerPolicy.ETCHING, player, target, cost, ownerValid, null, capacity);
    }
    /** Closed enhanced-range entry: no public skill/range/owner-boolean overload exists. */
    public static Result attemptSweep(DisintegrationSweep.MiningPermit permit, Capacity capacity) {
        if (permit == null || !permit.consume()) return new Result(false, "Sweep permit is unavailable or already used");
        return attempt(OwnerPolicy.SWEEP, permit.player(), permit.target(), permit.cost(), permit::valid, permit.origin(), capacity);
    }
    public static Result attemptPoint(PointMeltdown.MiningPermit permit, Capacity capacity) {
        if (permit == null || !permit.consume()) return new Result(false, "Point permit is unavailable or already used");
        return attempt(OwnerPolicy.POINT, permit.player(), permit.target(), permit.cost(), permit::valid, permit.origin(), capacity);
    }
    private static Result attempt(OwnerPolicy policy, ServerPlayer player, Target target, double cost,
                                  BooleanSupplier ownerValid, Vec3 origin, Capacity capacity) {
        if (ACTIVE.get() != null) return new Result(false, "Nested mining refused");
        Eligibility eligibility = inspect(policy, player, target, origin, capacity);
        if (!eligibility.eligible) return new Result(false, eligibility.reason);
        if (ownerValid == null || !ownerValid.getAsBoolean()) return new Result(false, "Cast owner changed");
        HighPowerResources.Reservation reservation = HighPowerResources.reserve(player, policy.skill, cost);
        if (reservation == null) return new Result(false, "CP unavailable");
        Permit permit = new Permit(policy, player, target, ownerValid, origin, capacity, eligibility.drop, reservation);
        ACTIVE.set(permit);
        try (reservation) {
            // This source-style event is distinct from, and precedes, the one native BreakEvent.
            if (NeoForge.EVENT_BUS.post(new SkillBlockDestroyEvent(player, "meltdowner." + policy.skill, target.pos)).isCanceled())
                return new Result(false, "Skill terrain event canceled");
            if (!permit.valid(false)) return new Result(false, "Mining inputs changed in skill callback");
            permit.phase = Phase.BREAK;
            player.gameMode.destroyBlock(target.pos); // Its boolean is deliberately NOT success evidence.
            return new Result(permit.removed, permit.removed ? "" : permit.reason);
        } catch (RuntimeException exception) {
            return new Result(permit.removed, permit.removed ? "Native harvest threw after removal" : "Native harvest aborted before removal");
        } finally { ACTIVE.remove(); }
    }
    private enum Phase { SOURCE, BREAK, CALLBACK, TOOL, REMOVE, REMOVING, DONE, REFUSED }
    private static final class Permit {
        final OwnerPolicy policy;
        final ServerPlayer player;
        final ServerPlayerGameMode gameMode;
        final Object connection, level, menu;
        final Target target;
        final BooleanSupplier ownerValid;
        final Vec3 origin;
        final Capacity capacity;
        final ItemStack drop, tool, toolReference;
        final HighPowerResources.Reservation reservation;
        final List<ItemStack> inventory = new ArrayList<>();
        final List<ItemStack> references = new ArrayList<>();
        final int selected;
        Phase phase = Phase.SOURCE;
        boolean removed;
        String reason = "Native block was not removed";
        Permit(OwnerPolicy policy, ServerPlayer player, Target target, BooleanSupplier ownerValid, Vec3 origin,
               Capacity capacity, ItemStack drop, HighPowerResources.Reservation reservation) {
            this.policy = policy; this.origin = origin;
            this.player = player; this.gameMode = player.gameMode; this.connection = player.connection;
            this.level = player.level(); this.menu = player.containerMenu; this.target = target;
            this.ownerValid = ownerValid; this.capacity = capacity; this.drop = drop.copy(); this.reservation = reservation;
            this.selected = player.getInventory().selected; this.toolReference = player.getMainHandItem(); this.tool = toolReference.copy();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i); references.add(stack); inventory.add(stack.copy());
            }
        }
        boolean valid(boolean afterTool) {
            if (player.gameMode != gameMode || player.connection != connection || player.level() != level || player.containerMenu != menu
                    || player.getInventory().selected != selected || !ownerValid.getAsBoolean() || !reservation.valid()
                    || basicReason(policy, player, target, origin) != null || VanillaMiningContract.unsupportedReason(player).isPresent()
                    || !canonicalLoot(policy, player, target.state) || !capacity.fits(player, drop)) return false;
            // Validate the original reviewed tool, including after its last durability point.
            // Native canHarvest is captured before wear; an emptied final-use pickaxe must
            // retain that harvest. The inventory comparison below permits only native wear.
            if (policy == OwnerPolicy.POINT && !VanillaMiningContract.reviewedPointHarvestTool(target.state, tool)) return false;
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack expected = inventory.get(i);
                // Only a damageable tool wears; a hand-equivalent held item (book, paper) stays exactly as it was.
                if (afterTool && i == selected && !tool.isEmpty() && tool.isDamageableItem()) {
                    expected = tool.copy();
                    if (expected.getDamageValue() + 1 >= expected.getMaxDamage()) expected = ItemStack.EMPTY;
                    else expected.setDamageValue(expected.getDamageValue() + 1);
                }
                ItemStack actual = player.getInventory().getItem(i);
                if (!ItemStack.matches(actual, expected) || !actual.isEmpty() && actual != references.get(i)) return false;
            }
            return true;
        }
        boolean matches(Level level, Player player, BlockPos pos, BlockState state) {
            return this.player == player && this.level == level && target.pos.equals(pos) && target.state == state;
        }
        void refuse(String reason) { this.phase = Phase.REFUSED; this.reason = reason; }
        void commitRemoval() {
            if (removed) return;
            removed = true;
            reservation.settleRemoved();
            capacity.commit(drop);
            phase = Phase.DONE;
        }
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    /** Only called by the verified native redirect; ordinary vanilla/Classic paths pass through. */
    public static BlockEvent.BreakEvent nativeBreak(Level level, GameType gameType, ServerPlayer player, BlockPos pos, BlockState state) {
        Permit permit = ACTIVE.get();
        if (permit == null || permit.player != player) return CommonHooks.fireBlockBreak(level, gameType, player, pos, state);
        if (permit.phase != Phase.BREAK || !permit.matches(level, player, pos, state) || !permit.valid(false)) {
            var refused = new BlockEvent.BreakEvent(level, pos, state, player); refused.setCanceled(true); return refused;
        }
        permit.phase = Phase.CALLBACK;
        BlockEvent.BreakEvent event = CommonHooks.fireBlockBreak(level, gameType, player, pos, state);
        if (event.isCanceled() || !permit.valid(false)) {
            event.setCanceled(true); permit.refuse("Native event canceled or changed mining inputs");
        } else permit.phase = Phase.TOOL;
        return event;
    }
    /** Revalidate before tool wear; only the reviewed one-point vanilla durability transition is accepted. */
    public static void nativeMine(ItemStack stack, Level level, BlockState state, BlockPos pos, Player player) {
        Permit permit = ACTIVE.get();
        if (permit == null || permit.player != player) { stack.mineBlock(level, state, pos, player); return; }
        if (permit.phase != Phase.TOOL || stack != permit.toolReference || !permit.matches(level, player, pos, state) || !permit.valid(false)) {
            permit.refuse("Mining inputs changed before tool use"); return;
        }
        stack.mineBlock(level, state, pos, player);
        if (!permit.valid(true)) permit.refuse("Unexpected native tool/inventory transition");
        else permit.phase = Phase.REMOVE;
    }
    /**
     * Actual irreversible native removal, inside private ServerPlayerGameMode.removeBlock.
     * A native HarvestCheck veto can arrive after preflight and still incur ordinary tool
     * wear before this seam refuses removal. Never undo native wear or fabricate its loot.
     */
    public static boolean nativeRemove(BlockState state, Level level, BlockPos pos, Player player, boolean canHarvest, FluidState fluid) {
        Permit permit = ACTIVE.get();
        if (permit == null || permit.player != player) return state.onDestroyedByPlayer(level, pos, player, canHarvest, fluid);
        if (permit.phase != Phase.REMOVE || !canHarvest || !fluid.isEmpty() || !permit.matches(level, player, pos, state) || !permit.valid(true)) {
            permit.refuse("Mining inputs changed before native removal"); return false;
        }
        permit.phase = Phase.REMOVING;
        final boolean removed;
        try {
            removed = state.onDestroyedByPlayer(level, pos, player, canHarvest, fluid);
        } catch (RuntimeException failure) {
            // In the pinned dry-block native path, Level.removeBlock commits the AIR cell
            // before notifying neighbours. A notification exception can therefore escape
            // before onDestroyedByPlayer returns its boolean. Observe only this exact
            // admitted cell, never a distant change or destroyBlock's optimistic return.
            // Arbitrary foreign replacement/compensating hooks remain outside the contract.
            if (level.getBlockState(pos).isAir() && level.getFluidState(pos).isEmpty()) permit.commitRemoval();
            throw failure; // The attempt boundary reports partial native completion.
        }
        if (removed && level.getBlockState(pos) != state) permit.commitRemoval();
        else permit.refuse("Native removal returned false or retained the original block");
        return removed;
    }
}

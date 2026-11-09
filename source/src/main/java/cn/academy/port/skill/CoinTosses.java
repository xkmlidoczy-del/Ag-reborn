/* AcademyCraft 1.0.7 ItemCoin/EntityCoinThrowing/Railgun adaptation. See NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-owned coin escrow, toss physics and one-shot Railgun judgement.
 * All entry points must run on the server thread. No client supplies height,
 * vertical motion, progress, inventory, CP cost or whether the judgement passed.
 * The physical coin is represented by a transient record, never a game entity.
 */
public final class CoinTosses {
    public static final double INITIAL_VELOCITY = 0.92;
    public static final double GRAVITY = 0.06;
    public static final double LINEAR_DRAG = 1.0;
    public static final int MAX_LIFE = 120;
    public static final double QTE_THRESHOLD = 0.7;
    public static final double READY_THRESHOLD = 0.6;
    private static final String ESCROW_KEY = "academy:coin_escrow";
    private static final Map<UUID, Toss> TOSSES = new HashMap<>();
    private static long nextToken;

    private CoinTosses() {}

    /**
     * Pure common-side simulation shared by the server and visual client.
     * Keeps the classic float initHt, zero-initialized maxHt and fixed .92
     * progress denominator, including their negative-height/velocity quirks.
     */
    public static final class Trajectory {
        private final float initialHeight;
        private double height, previousHeight, maximumHeight, velocity;
        private int age;
        private boolean judged;

        public Trajectory(double initialHeight, double playerVerticalMotion) {
            if (!Double.isFinite(initialHeight) || !Double.isFinite(playerVerticalMotion))
                throw new IllegalArgumentException("Non-finite coin trajectory");
            this.initialHeight = (float) initialHeight;
            this.height = this.previousHeight = initialHeight;
            this.velocity = INITIAL_VELOCITY + playerVerticalMotion;
        }

        /** Rigidbody: subtract gravity, apply default drag, then move. */
        public void tick() {
            previousHeight = height;
            velocity = (velocity - GRAVITY) * LINEAR_DRAG;
            height += velocity;
            maximumHeight = Math.max(maximumHeight, height);
            age++;
        }

        public double height() { return height; }
        public double previousHeight() { return previousHeight; }
        public double maximumHeight() { return maximumHeight; }
        public double velocity() { return velocity; }
        public int age() { return age; }
        public boolean judged() { return judged; }

        public double progress() {
            return velocity > 0 ? (INITIAL_VELOCITY - velocity) / INITIAL_VELOCITY * 0.5
                    : Math.min(1.0, 0.5 + (maximumHeight - height)
                    / (maximumHeight - initialHeight) * 0.5);
        }

        public boolean finished(double playerHeight) {
            return (height < playerHeight && velocity < 0) || age > MAX_LIFE;
        }

        /** A failed early judgement is still spent; the coin continues falling. */
        public boolean attempt(boolean eligible) {
            if (judged) return false;
            judged = true;
            return eligible && acceptsProgress(progress());
        }

        public static boolean acceptsProgress(double progress) { return progress > QTE_THRESHOLD; }
        public static boolean readyProgress(double progress) { return progress >= READY_THRESHOLD; }
        public boolean ready() { return !judged && readyProgress(progress()); }
    }

    private static final class Toss {
        final ServerPlayer owner;
        final ServerLevel level;
        final Trajectory trajectory;
        final long token;
        final boolean qte;
        final ItemStack escrow;
        long lastTick;

        Toss(ServerPlayer owner, ItemStack escrow, boolean qte) {
            this.owner = owner;
            this.level = owner.serverLevel();
            this.trajectory = new Trajectory(owner.getY(), owner.getDeltaMovement().y);
            this.token = ++nextToken;
            this.qte = qte;
            this.escrow = escrow;
            this.lastTick = level.getGameTime();
        }
    }

    /** Right-click; at most one physical toss per UUID. Creative tosses owe no coin. */
    public static boolean toss(ServerPlayer player, InteractionHand hand) {
        if (!onServerThread(player) || hand == null || !canToss(player)) return false;
        recoverSavedEscrow(player);
        Toss previous = TOSSES.get(player.getUUID());
        if (previous != null) {
            if (valid(player, previous)) return false;
            abort(player);
        }
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(AcademyCraft.COIN.get()) || held.isEmpty()) return false;
        boolean creative = player.getAbilities().instabuild;
        ItemStack escrow = creative ? ItemStack.EMPTY : held.copyWithCount(1);
        Toss toss = new Toss(player, escrow, canUseRailgun(player));
        TOSSES.put(player.getUUID(), toss);
        if (!creative) {
            held.shrink(1);
            // Saved alongside player inventory; a restart returns rather than
            // resuming the transient toss. Never accepts a client-supplied stack.
            player.getPersistentData().put(ESCROW_KEY, escrow.save(player.registryAccess()));
            player.getInventory().setChanged();
        }
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "entity.flipcoin")),
                SoundSource.PLAYERS, 0.5F, 1.0F);
        broadcastStart(toss);
        if (toss.qte) {AcademyGameplay.cancelRailgunCharge(player);AcademyNetwork.effect(player,"charge",0);}
        return true;
    }

    /** PlayerTickEvent.Post; duplicate calls in one game tick cannot accelerate QTE. */
    public static void tick(ServerPlayer player) {
        if (!onServerThread(player)) return;
        recoverSavedEscrow(player);
        Toss toss = TOSSES.get(player.getUUID());
        if (toss == null) return;
        if (!valid(player, toss)) { abort(player); return; }
        long now = toss.level.getGameTime();
        if (now == toss.lastTick) return;
        toss.lastTick = now;
        toss.trajectory.tick();
        if (toss.trajectory.finished(player.getY())) finish(toss, true, true);
    }

    /**
     * True means the trusted late QTE removed/consumed the coin. The caller must
     * then run Railgun's ability/CP checks WITHOUT iron ammunition. CP failure
     * must not refund the coin: legacy consumes ammunition before CP consumption.
     */
    public static boolean attempt(ServerPlayer player) {
        Toss toss = onServerThread(player) ? TOSSES.get(player.getUUID()) : null;
        return toss != null && attempt(player, toss.token);
    }

    /** Network entry point: a stale coin token never judges a subsequent toss. */
    public static boolean attempt(ServerPlayer player, long token) {
        if (!onServerThread(player)) return false;
        Toss toss = TOSSES.get(player.getUUID());
        if (toss == null || toss.token != token) return false;
        if (!valid(player, toss) || toss.trajectory.finished(player.getY())) {
            finish(toss, true);
            return false;
        }
        if (!toss.trajectory.attempt(toss.qte && canUseRailgun(player))) return false;
        finish(toss, false);
        return true;
    }

    /** Physical cleanup on logout/death/dimension/clone, NOT ordinary key release. */
    public static void abort(ServerPlayer player) {
        if (!onServerThread(player)) return;
        Toss toss = TOSSES.get(player.getUUID());
        if (toss != null) finish(toss, true);
        else recoverSavedEscrow(player);
    }

    /** Call before player saving on graceful stop; returns all outstanding escrows. */
    public static void clear() {
        for (Toss toss : TOSSES.values().toArray(Toss[]::new)) finish(toss, true);
        TOSSES.clear();
    }

    public static boolean hasPendingAttempt(ServerPlayer player) {
        Toss toss = onServerThread(player) ? TOSSES.get(player.getUUID()) : null;
        return toss != null && valid(player, toss) && toss.qte && !toss.trajectory.judged();
    }

    private static boolean onServerThread(ServerPlayer player) {
        return player != null && player.serverLevel().getServer().isSameThread();
    }

    private static boolean canToss(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && Double.isFinite(player.getX()) && Double.isFinite(player.getY())
                && Double.isFinite(player.getZ()) && Double.isFinite(player.getDeltaMovement().y);
    }

    private static boolean valid(ServerPlayer player, Toss toss) {
        return toss.owner == player && toss.level == player.serverLevel() && canToss(player);
    }

    private static boolean canUseRailgun(ServerPlayer player) {
        var state = AbilityStorage.get(player);
        return "electromaster".equals(state.category) && state.canUse("railgun") && state.presets.currentContains("railgun")
                && SkillCatalog.find(state.category, "railgun")
                .filter(skill -> skill.controllable() && state.level >= skill.level()).isPresent();
    }

    private static void finish(Toss toss, boolean refund) { finish(toss, refund, false); }

    /** Natural return alone triggers the source optional local Heads/Tails display. */
    private static void finish(Toss toss, boolean refund, boolean naturalReturn) {
        if (!TOSSES.remove(toss.owner.getUUID(), toss)) return;
        toss.owner.getPersistentData().remove(ESCROW_KEY);
        if (refund && !toss.escrow.isEmpty()) returnCoin(toss.owner, toss.escrow.copy());
        var tag = new CompoundTag();
        tag.putString("kind", "coin_end");
        tag.putBoolean("natural_return", naturalReturn);
        tag.putInt("entity", toss.owner.getId());
        tag.putLong("token", toss.token);
        PacketDistributor.sendToPlayersNear(toss.level, null, toss.owner.getX(), toss.trajectory.height(),
                toss.owner.getZ(), 32, new AcademyNetwork.ClientData(tag));
    }

    private static void recoverSavedEscrow(ServerPlayer player) {
        if (TOSSES.containsKey(player.getUUID()) || !player.getPersistentData().contains(ESCROW_KEY)) return;
        ItemStack escrow = ItemStack.parseOptional(player.registryAccess(), player.getPersistentData().getCompound(ESCROW_KEY));
        player.getPersistentData().remove(ESCROW_KEY);
        // Only this mod's single coin is ever a legitimate escrow.
        if (escrow.is(AcademyCraft.COIN.get())) returnCoin(player, escrow.copyWithCount(1));
    }

    /** Classic main-hand priority, then stack merge/free slot, then y+.6 item drop. */
    private static void returnCoin(ServerPlayer player, ItemStack coin) {
        if (!player.isAlive() || player.isRemoved()) { drop(player, coin); return; }
        var inventory = player.getInventory();
        ItemStack main = player.getMainHandItem();
        if (main.isEmpty()) { player.setItemInHand(InteractionHand.MAIN_HAND, coin); inventory.setChanged(); return; }
        if (merge(main, coin)) { inventory.setChanged(); return; }
        for (ItemStack slot : inventory.items) {
            if (merge(slot, coin)) { inventory.setChanged(); return; }
        }
        int free = inventory.getFreeSlot();
        if (free >= 0) { inventory.setItem(free, coin); inventory.setChanged(); }
        else drop(player, coin);
    }

    private static boolean merge(ItemStack target, ItemStack coin) {
        if (target.isEmpty() || !ItemStack.isSameItemSameComponents(target, coin)
                || target.getCount() >= target.getMaxStackSize()) return false;
        target.grow(1);
        return true;
    }

    private static void drop(ServerPlayer player, ItemStack coin) {
        player.serverLevel().addFreshEntity(new ItemEntity(player.serverLevel(),
                player.getX(), player.getY() + 0.6, player.getZ(), coin));
    }

    private static void broadcastStart(Toss toss) {
        var player = toss.owner;
        var tag = new CompoundTag();
        tag.putString("kind", "coin_toss");
        tag.putInt("entity", player.getId());
        tag.putLong("token", toss.token);
        tag.putDouble("x", player.getX());
        tag.putDouble("y", player.getY());
        tag.putDouble("z", player.getZ());
        tag.putDouble("vy", player.getDeltaMovement().y);
        tag.putDouble("ax", 0.1 + player.getRandom().nextDouble());
        tag.putDouble("ay", player.getRandom().nextDouble());
        tag.putDouble("az", player.getRandom().nextDouble());
        tag.putBoolean("qte", toss.qte);
        PacketDistributor.sendToPlayersNear(toss.level, null, player.getX(), player.getY(), player.getZ(),
                32, new AcademyNetwork.ClientData(tag));
    }
}

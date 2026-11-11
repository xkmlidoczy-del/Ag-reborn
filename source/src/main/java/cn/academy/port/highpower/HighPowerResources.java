package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.math.BigDecimal;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * One shared CP ledger and persistent cooldown clock for the admitted EM/MD/TP categories.
 * The historical key and field names are retained: category changes never migrate,
 * reset, refill or refund resources. Electromaster shield accounting remains separate.
 */
public final class HighPowerResources {
    public static final String DATA_KEY = "academy:electromaster_prototype";

    private static final Map<ServerPlayer, Reservation> RESERVATIONS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, ControlHold> CONTROL_HOLDS = new IdentityHashMap<>();

    private HighPowerResources() {}

    /** Spendable CP in the same ledger. Neither kind of transient claim is a debit.
     * Reading this budget never calls a controller, releases a hold, or changes CP. */
    public static double availableCp(ServerPlayer player) {
        double amount = cp(player);
        Reservation reservation = RESERVATIONS.get(player);
        ControlHold hold = CONTROL_HOLDS.get(player);
        return hold == null ? Math.max(0, amount - (reservation == null ? 0 : reservation.cost))
                : heldAvailability(amount, hold, reservation).doubleValue();
    }
    public static double heldAmount(ServerPlayer player) {
        ControlHold hold = CONTROL_HOLDS.get(player);
        return hold == null ? 0 : hold.heldAmount();
    }
    /** While a control fee exists, all debits use the same canonical decimal meaning of
     * existing double values. No tolerance, floor-to-fee correction, or snapshot credit
     * may turn an insufficient balance into a sufficient one. The original no-hold
     * binary arithmetic remains unchanged, including synchronous harvest-only scopes. */
    private static BigDecimal heldAvailability(double amount, ControlHold hold, Reservation reservation) {
        return BigDecimal.valueOf(amount).subtract(protectedAmount(hold, reservation)).max(BigDecimal.ZERO);
    }
    private static BigDecimal protectedAmount(ControlHold hold, Reservation reservation) {
        BigDecimal amount = BigDecimal.valueOf(hold.cost);
        return reservation == null ? amount : amount.add(BigDecimal.valueOf(reservation.cost));
    }
    private static boolean canSpend(ServerPlayer player, double cost) {
        ControlHold hold = CONTROL_HOLDS.get(player);
        if (hold == null) return availableCp(player) >= cost;
        BigDecimal remaining = BigDecimal.valueOf(cp(player)).subtract(BigDecimal.valueOf(cost));
        BigDecimal floor = protectedAmount(hold, RESERVATIONS.get(player));
        // Two live claims can have an exact sum that no double can represent. Refuse
        // before spending if storage would undercut either claim; never add an ULP.
        return remaining.compareTo(floor) >= 0 && BigDecimal.valueOf(storedAmount(remaining)).compareTo(floor) >= 0;
    }
    /** A stored positive remainder cannot exceed its exact canonical-decimal value.
     * At most one adjacent double is discarded; zero is never moved below zero. */
    private static double storedAmount(BigDecimal amount) {
        double stored = amount.doubleValue();
        return stored > 0 && BigDecimal.valueOf(stored).compareTo(amount) > 0 ? Math.nextDown(stored) : stored;
    }
    private static double subtractCost(ServerPlayer player, double current, double cost) {
        return CONTROL_HOLDS.containsKey(player)
                ? storedAmount(BigDecimal.valueOf(current).subtract(BigDecimal.valueOf(cost)))
                : current - cost;
    }
    /** Legacy writers fail closed during this very short scope without changing ordinary behavior. */
    public static boolean hasReservation(ServerPlayer player) { return RESERVATIONS.containsKey(player); }

    /** NEW223 only. The caller has authenticated its context; no balance or clock is written. */
    static ControlHold holdControlFee(ServerPlayer player, Object owner, long generation, double cost) {
        if (!ready(player) || owner == null || generation <= 0 || !Double.isFinite(cost) || cost <= 0
                || CONTROL_HOLDS.containsKey(player) || hasReservation(player)
                || HighPowerCombat.unsupportedReason(player).isPresent()) return null;
        CompoundTag data = player.getPersistentData().getCompound(DATA_KEY);
        var growth = HighPowerGrowth.values(player);
        if (!exactBudget(data, growth.cpMaximum()) || availableCp(player) < cost) return null;
        ControlHold hold = new ControlHold(player, owner, generation, data, cost, growth);
        CONTROL_HOLDS.put(player, hold);
        return hold;
    }

    /** An unpersisted, owner/session-bound single-use control fee. Release never writes CP. */
    public static final class ControlHold implements AutoCloseable {
        public enum State { HELD, CLAIMED, TERMINAL }
        private final ServerPlayer player;
        private final Object owner;
        private final long generation;
        private final CompoundTag data;
        private final double cost, maximum;
        private final BigDecimal earnedPosition;
        private double expected;
        private State state = State.HELD;
        private ControlHold(ServerPlayer player, Object owner, long generation, CompoundTag data,
                            double cost, HighPowerGrowth.Values growth) {
            this.player = player; this.owner = owner; this.generation = generation; this.data = data;
            this.cost = cost; maximum = growth.cpMaximum(); earnedPosition = growth.earnedPosition();
            expected = data.getDouble("cp");
        }
        /** Pure inspection: no controller/context validation and no lazy retirement. */
        public double heldAmount() { return state == State.TERMINAL ? 0 : cost; }
        public State state() { return state; }
        public boolean valid(Object owner, long generation) {
            return this.owner == owner && this.generation == generation && ledgerValid();
        }
        private boolean ledgerValid() {
            if (state == State.TERMINAL || CONTROL_HOLDS.get(player) != this || !player.server.isSameThread()
                    || !player.isAlive() || player.isRemoved() || effectiveLevel(player) <= 0
                    || player.getPersistentData().getCompound(DATA_KEY) != data || !exactBudget(data, maximum)
                    || Double.doubleToLongBits(data.getDouble("cp")) != Double.doubleToLongBits(expected)
                    || expected < cost) return false;
            try {
                var growth = HighPowerGrowth.values(player);
                return Double.doubleToLongBits(growth.cpMaximum()) == Double.doubleToLongBits(maximum)
                        && growth.earnedPosition().compareTo(earnedPosition) == 0;
            } catch (IllegalArgumentException | IllegalStateException invalid) { return false; }
        }
        boolean claim(Object owner, long generation) {
            if (state != State.HELD || !valid(owner, generation)) return false;
            state = State.CLAIMED; return true;
        }
        /** A refused emission may retain its original held fee without any compensating write. */
        boolean restore(Object owner, long generation) {
            if (state != State.CLAIMED || !valid(owner, generation)) return false;
            state = State.HELD; return true;
        }
        @Override public void close() {
            CONTROL_HOLDS.remove(player, this);
            state = State.TERMINAL;
        }
    }

    /** NEW223→NEW221 closed settlement: one frozen fee plus one ordinary shot, exactly once.
     * The controller validates geometry/context first, claims immediately before this call,
     * and invokes damage/presentation callbacks only after this method returns successfully. */
    static boolean trySpendControlledMirror(ServerPlayer player, ControlHold hold, Object owner,
                                           long generation, double shotCost, int cooldownTicks) {
        if (hold == null || hold.player != player || hold.owner != owner || hold.generation != generation
                || hold.state != ControlHold.State.CLAIMED) return false;
        boolean settled = false;
        try {
            String skill = HighPowerSkills.FOLDED_MIRROR_ARRAY;
            if (!ready(player) || hasReservation(player) || !hold.valid(owner, generation)
                    || !HighPowerSkills.eligible(AbilityStorage.get(player), skill)
                    || HighPowerCombat.unsupportedReason(player).isPresent()
                    || !Double.isFinite(shotCost) || shotCost <= 0 || cooldownTicks < 1 || cooldownTicks > 12000) return false;
            var ability = AbilityStorage.get(player);
            int level = effectiveLevel(player);
            if (SkillCatalog.find(ability.category, skill).filter(entry -> level >= entry.level()).isEmpty()) return false;
            long now = player.serverLevel().getGameTime();
            String clock = "next_" + skill;
            BigDecimal current = BigDecimal.valueOf(hold.expected);
            BigDecimal total = BigDecimal.valueOf(hold.cost).add(BigDecimal.valueOf(shotCost));
            if (now < 0 || now > Long.MAX_VALUE - cooldownTicks
                    || hold.data.contains(clock) && !hold.data.contains(clock, Tag.TAG_LONG)
                    || now < hold.data.getLong(clock) || current.compareTo(total) < 0) return false;
            double remaining = storedAmount(current.subtract(total));
            // No callbacks occur between validation, retirement, and the irreversible debit.
            // Removing first also means later native callbacks cannot claim or subtract it again.
            hold.close();
            hold.data.putDouble("cp", remaining);
            hold.data.putLong(clock, now + cooldownTicks);
            player.getPersistentData().put(DATA_KEY, hold.data);
            ability.setCooldown(skill, cooldownTicks);
            settled = true;
            return true;
        } finally {
            if (!settled) hold.restore(owner, generation);
        }
    }

    private static boolean exactBudget(CompoundTag data, double maximum) {
        double amount = data.getDouble("cp");
        return data.contains("cp", Tag.TAG_DOUBLE) && Double.isFinite(maximum) && maximum > 0
                && Double.isFinite(amount) && amount >= 0 && amount <= maximum;
    }

    /** No cooldown: a controller may complete two bounded cells in the same server tick. */
    public static Reservation reserve(ServerPlayer player, String skill, double cost) {
        if (!ready(player) || !Double.isFinite(cost) || cost <= 0 || hasReservation(player)
                || !reservationEligible(player, skill) || !canSpend(player, cost)) return null;
        var data = player.getPersistentData().getCompound(DATA_KEY);
        if (!data.contains("cp") || !Double.isFinite(data.getDouble("cp")) || !ledgerWritable(player, data)) return null;
        Reservation reservation = new Reservation(player, data, cost);
        RESERVATIONS.put(player, reservation);
        return reservation;
    }

    /** Only authenticated, already processing NEW213/NEW231 queues may ignore its own one-tick UI guard. */
    static boolean reservationEligible(ServerPlayer player, String skill) {
        var ability = AbilityStorage.get(player);
        if (HighPowerSkills.eligible(ability, skill)) return true;
        return ability.cooldowns.getOrDefault(skill, 0) == 1
                && (HighPowerSkills.BEAM_ETCHING.equals(skill) && BeamEtching.executing(player)
                    || "disintegration_sweep".equals(skill) && DisintegrationSweep.executing(player));
    }

    /**
     * Not serializable or transferable. Closing only releases an in-memory claim, never refunds CP.
     * Exact live compound identity and tracked CP reject direct replacement or unreviewed writers.
     * Legitimate ledger spending/recovery updates the expectation and preserve unrelated fields.
     */
    public static final class Reservation implements AutoCloseable {
        private final ServerPlayer player;
        private final CompoundTag data;
        private final double cost, maximum;
        private double expected;
        private boolean closed, settled;
        private Reservation(ServerPlayer player, CompoundTag data, double cost) {
            this.player = player; this.data = data; this.cost = cost; this.maximum = HighPowerGrowth.values(player).cpMaximum(); this.expected = data.getDouble("cp");
        }
        public boolean valid() {
            return !closed && !settled && RESERVATIONS.get(player) == this && player.server.isSameThread()
                    && player.isAlive() && !player.isRemoved()
                    && player.getPersistentData().getCompound(DATA_KEY) == data
                    && Double.doubleToLongBits(data.getDouble("cp")) == Double.doubleToLongBits(expected)
                    && cp(player) >= cost;
        }
        /** Called only after the native onDestroyedByPlayer reported actual removal. */
        void settleRemoved() {
            if (closed || settled || RESERVATIONS.get(player) != this) throw new IllegalStateException("Inactive harvest reservation");
            // The approved native removal has no CP writer. If an unsupported callback replaces
            // the ledger after irreversible removal, debit the current ledger without restoring
            // any snapshot or minting CP; that foreign runtime is outside this closed contract.
            var current = player.getPersistentData().getCompound(DATA_KEY);
            ControlHold control = CONTROL_HOLDS.get(player);
            boolean tracked = control != null && control.data == current && control.ledgerValid();
            double remaining = budget(current.getDouble("cp"), maximum);
            current.putDouble("cp", Math.max(0, subtractCost(player, remaining, cost)));
            if (tracked) control.expected = current.getDouble("cp");
            player.getPersistentData().put(DATA_KEY, current);
            settled = true;
            // The debit is now permanent; native loot callbacks see the actual remaining
            // balance, not that balance minus this claim a second time. The mining permit
            // independently remains active until native destroyBlock unwinds.
            RESERVATIONS.remove(player, this);
        }
        @Override public void close() {
            if (RESERVATIONS.get(player) == this) RESERVATIONS.remove(player);
            closed = true;
        }
    }
    private static boolean ledgerWritable(ServerPlayer player, CompoundTag data) {
        Reservation reservation = RESERVATIONS.get(player);
        ControlHold hold = CONTROL_HOLDS.get(player);
        return (reservation == null || reservation.valid() && reservation.data == data)
                && (hold == null || hold.ledgerValid() && hold.data == data);
    }
    private static void trackLedgerWrite(ServerPlayer player, CompoundTag data) {
        Reservation reservation = RESERVATIONS.get(player);
        if (reservation != null && reservation.data == data) reservation.expected = data.getDouble("cp");
        ControlHold hold = CONTROL_HOLDS.get(player);
        if (hold != null && hold.data == data) hold.expected = data.getDouble("cp");
    }

    /** The two original EM paths retain their fees and clocks while honoring held CP. */
    static boolean trySpendPrototypeArc(ServerPlayer player, double cost, int cooldownTicks) {
        if (!ready(player) || !HighPowerCategories.isElectromaster(AbilityStorage.get(player).category)
                || hasReservation(player) || !Double.isFinite(cost) || cost <= 0
                || cooldownTicks < 1 || cooldownTicks > 12000) return false;
        CompoundTag data = player.getPersistentData().getCompound(DATA_KEY);
        long now = player.serverLevel().getGameTime();
        if (!ledgerWritable(player, data) || now < data.getLong("next_cast") || !canSpend(player, cost)) return false;
        data.putDouble("cp", subtractCost(player, cp(player), cost));
        trackLedgerWrite(player, data);
        data.putLong("next_cast", now + cooldownTicks);
        player.getPersistentData().put(DATA_KEY, data);
        return true;
    }

    static boolean trySpendPrototypeDefense(ServerPlayer player, double cost) {
        if (!ready(player) || !HighPowerCategories.isElectromaster(AbilityStorage.get(player).category)
                || hasReservation(player) || !Double.isFinite(cost) || cost <= 0) return false;
        CompoundTag data = player.getPersistentData().getCompound(DATA_KEY);
        if (!ledgerWritable(player, data) || !canSpend(player, cost)) return false;
        data.putDouble("cp", subtractCost(player, data.getDouble("cp"), cost));
        trackLedgerWrite(player, data);
        player.getPersistentData().put(DATA_KEY, data);
        return true;
    }

    public static int effectiveLevel(ServerPlayer player) {
        if (player == null || !HighPowerCategories.active(AbilityStorage.get(player))) return 0;
        int earned = HighPowerCombat.earnedLevel(player);
        return earned >= 1 && earned <= 5 ? Math.min(AbilityStorage.get(player).level, earned) : 0;
    }
    public static boolean ready(ServerPlayer player) {
        return player != null && !player.level().isClientSide && player.server.isSameThread()
                && HighPowerCombat.enabled(player) && player.isAlive() && !player.isSpectator() && !player.isRemoved()
                && effectiveLevel(player) > 0 && !AbilityStorage.get(player).interfering && HighPowerGrowth.available(player);
    }
    /** Only genuine first enrollment may create a missing initial budget. */
    public static void initializeBudget(ServerPlayer player) {
        if (player == null || !player.server.isSameThread() || effectiveLevel(player) <= 0) return;
        var data = player.getPersistentData().getCompound(DATA_KEY);
        if (!data.contains("cp")) {
            data.putDouble("cp", HighPowerGrowth.values(player).cpMaximum());
            player.getPersistentData().put(DATA_KEY, data);
        }
    }
    public static double cp(ServerPlayer player) {
        return effectiveLevel(player) > 0
                ? budget(player.getPersistentData().getCompound(DATA_KEY).getDouble("cp"), HighPowerGrowth.values(player).cpMaximum()) : 0;
    }
    public static boolean trySpend(ServerPlayer player, String skill, double cpCost, int cooldownTicks) {
        if (!ready(player) || !HighPowerSkills.eligible(AbilityStorage.get(player), skill)
                || HighPowerCombat.unsupportedReason(player).isPresent()
                || !Double.isFinite(cpCost) || cpCost < 0 || cooldownTicks < 1 || cooldownTicks > 12000) return false;
        var ability = AbilityStorage.get(player);
        int level = effectiveLevel(player);
        if (SkillCatalog.find(ability.category, skill).filter(entry -> level >= entry.level()).isEmpty()) return false;
        var data = player.getPersistentData().getCompound(DATA_KEY);
        long now = player.serverLevel().getGameTime();
        String clock = HighPowerSkills.FORKED_ARC.equals(skill) ? "next_cast" : "next_" + skill;
        double cp = budget(data.getDouble("cp"), HighPowerGrowth.values(player).cpMaximum());
        if (!ledgerWritable(player, data) || now < data.getLong(clock) || !canSpend(player, cpCost)) return false;
        data.putDouble("cp", subtractCost(player, cp, cpCost));
        trackLedgerWrite(player, data);
        data.putLong(clock, now + cooldownTicks);
        player.getPersistentData().put(DATA_KEY, data);
        ability.setCooldown(skill, cooldownTicks);
        cn.academy.port.macro.MacroVfx.paid(player,skill);
        return true;
    }
    /**
     * NEW222 only: a live deferred pulse may share a tick with its paid upkeep.
     * Its controller has already fenced the physical 15-tick interval and frozen
     * a nonempty eligible target set. No callbacks or cooldown resets occur here.
     */
    static boolean trySpendThermalShellPulse(ServerPlayer player, double cost) {
        if (!ready(player) || !ThermalShell.payingPulse(player)
                || HighPowerCombat.unsupportedReason(player).isPresent()
                || !Double.isFinite(cost) || cost <= 0) return false;
        var ability = AbilityStorage.get(player);
        int cooldown = ability.cooldowns.getOrDefault(ThermalShell.ID, 0);
        if (!HighPowerCategories.isMeltdowner(ability.category) || !ability.activated
                || !ability.overloadFine || ability.interfering || ability.consumptionInProgress()
                || !ability.learned(ThermalShell.ID) || effectiveLevel(player) < 2
                || cooldown < 0 || cooldown > 1
                || !SkillCatalog.enabled(ability.category, ThermalShell.ID)) return false;
        var data = player.getPersistentData().getCompound(DATA_KEY);
        double current = cp(player);
        if (!ledgerWritable(player, data) || !canSpend(player, cost)) return false;
        data.putDouble("cp", subtractCost(player, current, cost));
        trackLedgerWrite(player, data);
        player.getPersistentData().put(DATA_KEY, data);
        return true;
    }

    /** NEW321 only: each already published inert needle commits its own immutable CP share.
     * The first share starts the normal cooldown; later shares require the live controller's
     * closed payment scope and never reset that clock. This method invokes no callbacks.
     */
    static boolean trySpendCrossSubstitutionNeedle(ServerPlayer player, double cost, int cooldown, boolean first) {
        if (!ready(player) || !CrossSubstitution.payingNeedle(player) || hasReservation(player)
                || HighPowerCombat.unsupportedReason(player).isPresent()
                || !Double.isFinite(cost) || cost <= 0 || cooldown < 1 || cooldown > 12000) return false;
        var ability = AbilityStorage.get(player);
        if (!HighPowerCategories.isTeleporter(ability.category) || !ability.activated
                || !ability.overloadFine || ability.interfering || ability.consumptionInProgress()
                || !ability.learned(HighPowerSkills.CROSS_SUBSTITUTION)
                || !ability.learned(HighPowerSkills.DISPLACED_PROJECTION)
                || !ability.learned(HighPowerSkills.SAFE_LANDING_PROBE) || effectiveLevel(player) < 2
                || !SkillCatalog.enabled(ability.category, HighPowerSkills.CROSS_SUBSTITUTION)) return false;
        if (first) return trySpend(player, HighPowerSkills.CROSS_SUBSTITUTION, cost, cooldown);
        var data = player.getPersistentData().getCompound(DATA_KEY);
        if (!ledgerWritable(player, data) || !canSpend(player, cost)) return false;
        data.putDouble("cp", subtractCost(player, cp(player), cost));
        trackLedgerWrite(player, data);
        player.getPersistentData().put(DATA_KEY, data);
        return true;
    }

    /** NEW341 closed per-original payment; later publications cannot restart cooldown. */
    static boolean trySpendSpatialPincerCorridorNeedle(ServerPlayer player, double cost, int cooldown, boolean first) {
        if (!ready(player) || !SpatialPincerCorridor.payingNeedle(player) || hasReservation(player)
                || HighPowerCombat.unsupportedReason(player).isPresent()
                || !Double.isFinite(cost) || cost <= 0 || cooldown < 1 || cooldown > 12000) return false;
        var ability = AbilityStorage.get(player);
        if (!HighPowerCategories.isTeleporter(ability.category) || !ability.activated
                || !ability.overloadFine || ability.interfering || ability.consumptionInProgress()
                || !ability.learned(HighPowerSkills.SPATIAL_PINCER_CORRIDOR)
                || !ability.learned(HighPowerSkills.CROSS_SUBSTITUTION)
                || !ability.learned(HighPowerSkills.DISPLACED_PROJECTION)
                || !ability.learned(HighPowerSkills.SAFE_LANDING_PROBE) || effectiveLevel(player) < 4
                || !SkillCatalog.enabled(ability.category, HighPowerSkills.SPATIAL_PINCER_CORRIDOR)) return false;
        if (first) return trySpend(player, HighPowerSkills.SPATIAL_PINCER_CORRIDOR, cost, cooldown);
        var data = player.getPersistentData().getCompound(DATA_KEY);
        if (!ledgerWritable(player, data) || !canSpend(player, cost)) return false;
        data.putDouble("cp", subtractCost(player, cp(player), cost));
        trackLedgerWrite(player, data);
        player.getPersistentData().put(DATA_KEY, data);
        return true;
    }

    /** NEW351 only: real native publication commits one frozen share; only the first starts UI cooldown. */
    static boolean trySpendMultidimensionalLandingArrayNeedle(ServerPlayer player, double cost, int cooldown, boolean first) {
        if (!ready(player) || !MultidimensionalLandingArray.payingNeedle(player) || hasReservation(player)
                || HighPowerCombat.unsupportedReason(player).isPresent() || !Double.isFinite(cost) || cost <= 0
                || cooldown < 1 || cooldown > 120) return false;
        var ability = AbilityStorage.get(player);
        if (!HighPowerCategories.isTeleporter(ability.category) || !ability.activated || !ability.overloadFine
                || ability.interfering || ability.consumptionInProgress() || !ability.learned(MultidimensionalLandingArray.ID)
                || !ability.learned(HighPowerSkills.SPATIAL_PINCER_CORRIDOR) || effectiveLevel(player) < 5
                || !SkillCatalog.enabled(ability.category, MultidimensionalLandingArray.ID) || first && !ability.canUse(MultidimensionalLandingArray.ID)) return false;
        var data = player.getPersistentData().getCompound(DATA_KEY);
        if (!data.contains("cp") || !Double.isFinite(data.getDouble("cp")) || !ledgerWritable(player, data) || !canSpend(player, cost)) return false;
        data.putDouble("cp", subtractCost(player, cp(player), cost)); trackLedgerWrite(player, data); player.getPersistentData().put(DATA_KEY, data);
        if (first) ability.setCooldown(MultidimensionalLandingArray.ID, cooldown);
        return true;
    }

    /** Closed NEW331 per-projectile payment; later shares never restart cooldown. */
    static boolean trySpendShortInterception(ServerPlayer player, double cost, int cooldown, boolean first) {
        if (!ready(player) || !ShortInterception.payingProjectile(player) || hasReservation(player)
                || HighPowerCombat.unsupportedReason(player).isPresent()
                || !Double.isFinite(cost) || cost <= 0 || cooldown < 1 || cooldown > 12000) return false;
        var ability = AbilityStorage.get(player);
        if (!HighPowerCategories.isTeleporter(ability.category) || !ability.activated
                || !ability.overloadFine || ability.interfering || ability.consumptionInProgress()
                || !ability.learned(HighPowerSkills.SHORT_INTERCEPTION)
                || !ability.learned(HighPowerSkills.CROSS_SUBSTITUTION)
                || !ability.learned(HighPowerSkills.DISPLACED_PROJECTION)
                || !ability.learned(HighPowerSkills.SAFE_LANDING_PROBE) || effectiveLevel(player) < 3
                || !SkillCatalog.enabled(ability.category, HighPowerSkills.SHORT_INTERCEPTION)) return false;
        if (first) return trySpend(player, HighPowerSkills.SHORT_INTERCEPTION, cost, cooldown);
        var data = player.getPersistentData().getCompound(DATA_KEY);
        if (!ledgerWritable(player, data) || !canSpend(player, cost)) return false;
        data.putDouble("cp", subtractCost(player, cp(player), cost));
        trackLedgerWrite(player, data);
        player.getPersistentData().put(DATA_KEY, data);
        return true;
    }

    /** NEW343 only: a validated positive item leg commits CP once, without callbacks,
     * reservations, inventory writes or an additional utility cooldown. The controller
     * owns the genuine-tick admission fence and records uncertain native commits. */
    static boolean trySpendSpatialRelayLeg(ServerPlayer player, double cost) {
        if (!ready(player) || !SpatialRelay.payingLeg(player) || hasReservation(player)
                || HighPowerCombat.unsupportedReason(player).isPresent()
                || !Double.isFinite(cost) || cost <= 0) return false;
        var ability = AbilityStorage.get(player);
        if (!HighPowerCategories.isTeleporter(ability.category) || !ability.activated
                || !ability.overloadFine || ability.interfering || ability.consumptionInProgress()
                || !ability.learned(HighPowerSkills.SPATIAL_RELAY)
                || !ability.learned(HighPowerSkills.RECOVERY_MARK)
                || effectiveLevel(player) < 4
                || !SkillCatalog.enabled(ability.category, HighPowerSkills.SPATIAL_RELAY)
                || Double.compare(cost, cn.academy.port.highpower.core.SpatialRelayRules.cpCost(
                        HighPowerGrowth.values(player).position())) != 0) return false;
        var data = player.getPersistentData().getCompound(DATA_KEY);
        if (!ledgerWritable(player, data) || !canSpend(player, cost)) return false;
        data.putDouble("cp", subtractCost(player, cp(player), cost));
        trackLedgerWrite(player, data);
        player.getPersistentData().put(DATA_KEY, data);
        return true;
    }

    /** The original shared last_tick guard prevents duplicate recovery, including same-tick category swaps. */
    public static boolean tick(ServerPlayer player) {
        if (!ready(player)) return false;
        long now = player.serverLevel().getGameTime();
        var data = player.getPersistentData().getCompound(DATA_KEY);
        if (!ledgerWritable(player, data) || data.contains("last_tick") && data.getLong("last_tick") == now) return false;
        var growth = HighPowerGrowth.values(player);
        data.putLong("last_tick", now);
        data.putDouble("cp", Math.min(growth.cpMaximum(), budget(data.getDouble("cp"), growth.cpMaximum()) + growth.cpRecovery() / 20));
        trackLedgerWrite(player, data);
        player.getPersistentData().put(DATA_KEY, data);
        return true;
    }
    private static double budget(double amount, double maximum) {
        return Double.isFinite(amount) ? Math.max(0, Math.min(maximum, amount)) : 0;
    }
}

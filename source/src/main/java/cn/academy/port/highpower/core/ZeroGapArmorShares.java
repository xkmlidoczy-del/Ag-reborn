package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * NEW252's six persistent views of EXISTING NEW212/base and lower/upper reserve
 * balances. There is no new pool, efficiency multiplier, or recovery budget.
 * B_i owns children 2i/2i+1; lower owns 0/2/4 and upper owns 1/3/5.
 * This pure arithmetic cannot authenticate contacts or atomically persist a
 * proposal: the caller must compare its before snapshot and commit every parent
 * and child together, never reconcile a contact a second time as an ordinary spend.
 */
public final class ZeroGapArmorShares {
    public static final int PLATE_COUNT = 6;
    public static final String SCHEMA_VERSION = "1";
    private static final BigDecimal QUANTUM = BigDecimal.ONE.scaleByPowerOfTen(-CombatAmounts.MAX_ABSOLUTE_SCALE);
    private static final Set<String> SAVE_FIELDS = saveFields();

    public record Plate(BigDecimal baseCurrent, BigDecimal baseCapacity,
                        BigDecimal reserveCurrent, BigDecimal reserveCapacity) {
        public Plate {
            baseCurrent = CombatAmounts.requireAmount(baseCurrent, "armor base current");
            baseCapacity = CombatAmounts.requireAmount(baseCapacity, "armor base capacity");
            reserveCurrent = CombatAmounts.requireAmount(reserveCurrent, "armor reserve current");
            reserveCapacity = requireCapacity(reserveCapacity, "armor reserve capacity");
            if (baseCurrent.compareTo(baseCapacity) > 0 || reserveCurrent.compareTo(reserveCapacity) > 0)
                throw new IllegalArgumentException("Armor component exceeds its own capacity");
            requireCapacity(baseCapacity.add(reserveCapacity), "armor plate capacity");
        }
        public BigDecimal current() { return baseCurrent.add(reserveCurrent); }
        public BigDecimal capacity() { return baseCapacity.add(reserveCapacity); }
    }

    /** Immutable proposal; a broken ID means positive TOTAL plate current became zero. */
    public record Transaction(ZeroGapArmorShares before, ZeroGapArmorShares after,
                              List<Integer> brokenPlateIds) {
        public Transaction {
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            brokenPlateIds = List.copyOf(brokenPlateIds);
            if (!brokenPlateIds.equals(brokenBetween(before, after)))
                throw new IllegalArgumentException("Broken plate IDs do not match the transaction");
        }
    }

    /** Residual is still capped and has not been applied to HP or any other pool. */
    public record Contact(Transaction transaction, BigDecimal baseAbsorbed,
                          BigDecimal reserveAbsorbed, BigDecimal residual) {
        public Contact {
            Objects.requireNonNull(transaction, "transaction");
            baseAbsorbed = CombatAmounts.requireAmount(baseAbsorbed, "armor base absorption");
            reserveAbsorbed = CombatAmounts.requireAmount(reserveAbsorbed, "armor reserve absorption");
            residual = CombatAmounts.requireAmount(residual, "armor residual");
            CombatAmounts.requireAmount(baseAbsorbed.add(reserveAbsorbed).add(residual), "armor contact damage");
        }
        public BigDecimal absorbed() { return baseAbsorbed.add(reserveAbsorbed); }
        public ZeroGapArmorShares shares() { return transaction.after(); }
        public ExactCombatState base() { return shares().base(); }
        public ScatterPlateShares scatter() { return shares().scatter(); }
        public SharedShieldReserveShares reserves() { return shares().reserves(); }
        public List<Integer> brokenPlateIds() { return transaction.brokenPlateIds(); }
        /** Deterministic final settlement after this one authenticated contact. The category base layer can
         * spend only the remaining existing base S; it cannot borrow siblings' reserve or change the contact award.
         * Recompute break debt from the original pre-contact snapshot, including every additionally exhausted child. */
        public Transaction finishResidual(boolean categoryShield) {
            ExactCombatState next=base().withShieldEnabled(categoryShield).applyMitigatedDamage(residual).state().withShieldEnabled(false);
            ScatterPlateShares nextScatter=scatter().afterBaseSpend(next.shield());
            ZeroGapArmorShares after=shares().reconcileParents(next,nextScatter,reserves()).after();
            return transaction.before().proposal(after);
        }

    }

    private final ExactCombatState base;
    private final ScatterPlateShares scatter;
    private final SharedShieldReserveShares reserves;
    private final List<Plate> plates;
    private final BigDecimal reserveMaximum;
    private final BigDecimal total;
    private final BigDecimal capacity;

    private ZeroGapArmorShares(ExactCombatState base, ScatterPlateShares scatter,
                               SharedShieldReserveShares reserves,
                               List<BigDecimal> baseCurrents, List<BigDecimal> reserveCurrents) {
        validateParents(base, scatter, reserves);
        this.base = base;
        this.scatter = scatter;
        this.reserves = reserves;
        this.reserveMaximum = reserveMaximum(base);
        requireSix(baseCurrents, "base currents");
        requireSix(reserveCurrents, "reserve currents");
        List<BigDecimal> baseCapacities = basePartitions(scatter, true);
        List<BigDecimal> reserveCapacities = reservePartitions(reserveMaximum, reserveMaximum);
        List<Plate> checked = new ArrayList<>(PLATE_COUNT);
        for (int i = 0; i < PLATE_COUNT; i++)
            checked.add(new Plate(baseCurrents.get(i), baseCapacities.get(i), reserveCurrents.get(i), reserveCapacities.get(i)));
        this.plates = List.copyOf(checked);
        for (int i = 0; i < ScatterPlateShares.PLATE_COUNT; i++)
            requireEqual(plate(2 * i).baseCurrent().add(plate(2 * i + 1).baseCurrent()),
                    scatter.plate(i).current(), "NEW212 parent " + i);
        requireEqual(reserveSum(0), reserves.lower(), "lower reserve parent");
        requireEqual(reserveSum(1), reserves.upper(), "upper reserve parent");
        this.total = CombatAmounts.requireAmount(base.shield().add(reserves.total()), "total armor current");
        this.capacity = CombatAmounts.requireAmount(base.maxShield().add(base.maxShield()), "total armor capacity");
    }

    /**
     * Explicit ONE-TIME enrollment/migration of existing current balances only.
     * Caller must distinguish never-enrolled from missing/corrupt saved state;
     * toggles, relearning, category changes and reloads must never call this.
     */
    public static ZeroGapArmorShares initialize(ExactCombatState base, ScatterPlateShares scatter,
                                                SharedShieldReserveShares reserves) {
        validateParents(base, scatter, reserves);
        return new ZeroGapArmorShares(base, scatter, reserves, basePartitions(scatter, false),
                reservePartitions(reserves.lower(), reserves.upper()));
    }

    /** Retains all twelve exact saved currents, rejecting any parent mismatch. */
    public static ZeroGapArmorShares restore(ExactCombatState base, ScatterPlateShares scatter,
                                             SharedShieldReserveShares reserves,
                                             List<BigDecimal> baseCurrents, List<BigDecimal> reserveCurrents) {
        return new ZeroGapArmorShares(base, scatter, reserves, baseCurrents, reserveCurrents);
    }

    public ExactCombatState base() { return base; }
    public ScatterPlateShares scatter() { return scatter; }
    public SharedShieldReserveShares reserves() { return reserves; }
    public List<Plate> plates() { return plates; }
    public Plate plate(int index) { return plates.get(requireIndex(index)); }
    public BigDecimal total() { return total; }
    public BigDecimal capacity() { return capacity; }

    /** Checks the complete before snapshot, including health and shield-enabled state. */
    public ZeroGapArmorShares requireMatches(ExactCombatState authoritativeBase, ScatterPlateShares authoritativeScatter,
                                             SharedShieldReserveShares authoritativeReserves) {
        validateParents(authoritativeBase, authoritativeScatter, authoritativeReserves);
        if (!base.equals(authoritativeBase) || !reserves.equals(authoritativeReserves))
            throw new IllegalArgumentException("Armor does not match authoritative base/reserves");
        for (int i = 0; i < ScatterPlateShares.PLATE_COUNT; i++)
            if (!scatter.plate(i).equals(authoritativeScatter.plate(i)))
                throw new IllegalArgumentException("Armor does not match NEW212 parent " + i);
        return this;
    }

    /**
     * Reconciles actual ordinary parent changes, not recovery budgets. Each B_i
     * delta touches only its own two children; L/U only their three children, in
     * ascending plate order. No delta means no healing. Capacity growth changes
     * ceilings only. A contraction unable to hold the resulting individual
     * currents is rejected, never clamped or redistributed.
     */
    public Transaction reconcileParents(ExactCombatState nextBase, ScatterPlateShares nextScatter,
                                         SharedShieldReserveShares nextReserves) {
        validateParents(nextBase, nextScatter, nextReserves);
        if (sameParents(nextBase, nextScatter, nextReserves)) return proposal(this);
        List<BigDecimal> nextBaseCurrents = baseCurrents();
        List<BigDecimal> nextReserveCurrents = reserveCurrents();
        List<BigDecimal> baseCapacities = basePartitions(nextScatter, true);
        BigDecimal nextReserveMaximum = reserveMaximum(nextBase);
        List<BigDecimal> reserveCapacities = reservePartitions(nextReserveMaximum, nextReserveMaximum);
        for (int i = 0; i < ScatterPlateShares.PLATE_COUNT; i++)
            distribute(nextBaseCurrents, baseCapacities, nextScatter.plate(i).current().subtract(scatter.plate(i).current()),
                    2 * i, 1, 2);
        distribute(nextReserveCurrents, reserveCapacities, nextReserves.lower().subtract(reserves.lower()), 0, 2, 3);
        distribute(nextReserveCurrents, reserveCapacities, nextReserves.upper().subtract(reserves.upper()), 1, 2, 3);
        return proposal(new ZeroGapArmorShares(nextBase, nextScatter, nextReserves, nextBaseCurrents, nextReserveCurrents));
    }

    /**
     * Authenticated contact AFTER native mitigation, using independently captured
     * base/reserve ceilings. Pays contacted base then assigned reserve, exactly
     * 1:1. A zero base ceiling cannot be borrowed by the reserve, or vice versa.
     * No sibling, other parent, HP, bonus pool or remainder recipient is touched.
     */
    public Contact contact(int index, BigDecimal mitigatedIncoming,
                           BigDecimal capturedBaseCeiling, BigDecimal capturedReserveCeiling) {
        index = requireIndex(index);
        BigDecimal incoming = CombatAmounts.requireAmount(mitigatedIncoming, "mitigated armor damage");
        BigDecimal baseCeiling = CombatAmounts.requireAmount(capturedBaseCeiling, "captured base ceiling");
        BigDecimal reserveCeiling = CombatAmounts.requireAmount(capturedReserveCeiling, "captured reserve ceiling");
        if (base.isDead()) return new Contact(proposal(this), BigDecimal.ZERO, BigDecimal.ZERO, incoming);
        BigDecimal basePaid = incoming.min(baseCeiling).min(plate(index).baseCurrent());
        BigDecimal reservePaid = incoming.subtract(basePaid).min(reserveCeiling).min(plate(index).reserveCurrent());
        BigDecimal residual = CombatAmounts.requireAmount(incoming.subtract(basePaid).subtract(reservePaid), "armor final residual");
        if (basePaid.signum() == 0 && reservePaid.signum() == 0)
            return new Contact(proposal(this), basePaid, reservePaid, residual);
        List<BigDecimal> nextBaseCurrents = baseCurrents();
        List<BigDecimal> nextReserveCurrents = reserveCurrents();
        nextBaseCurrents.set(index, nextBaseCurrents.get(index).subtract(basePaid));
        nextReserveCurrents.set(index, nextReserveCurrents.get(index).subtract(reservePaid));
        ExactCombatState nextBase = new ExactCombatState(base.health(), base.maxHealth(), base.shield().subtract(basePaid),
                base.maxShield(), base.shieldEnabled());
        List<BigDecimal> parentCurrents = new ArrayList<>(ScatterPlateShares.PLATE_COUNT);
        for (int i = 0; i < ScatterPlateShares.PLATE_COUNT; i++)
            parentCurrents.add(scatter.plate(i).current().subtract(i == index / 2 ? basePaid : BigDecimal.ZERO));
        ScatterPlateShares nextScatter = ScatterPlateShares.restore(nextBase.shield(), nextBase.maxShield(), parentCurrents);
        SharedShieldReserveShares nextReserves = new SharedShieldReserveShares(
                reserves.lower().subtract(index % 2 == 0 ? reservePaid : BigDecimal.ZERO),
                reserves.upper().subtract(index % 2 == 1 ? reservePaid : BigDecimal.ZERO));
        return new Contact(proposal(new ZeroGapArmorShares(nextBase, nextScatter, nextReserves,
                nextBaseCurrents, nextReserveCurrents)), basePaid, reservePaid, residual);
    }

    /** Decimal-only schema retains all twelve currents and each parent identity. */
    public Map<String, String> toDecimalMap() {
        Map<String, String> saved = new HashMap<>();
        saved.put("version", SCHEMA_VERSION);
        put(saved, "shield", base.shield());
        put(saved, "maxShield", base.maxShield());
        put(saved, "lower", reserves.lower());
        put(saved, "upper", reserves.upper());
        for (int i = 0; i < ScatterPlateShares.PLATE_COUNT; i++) {
            put(saved, "scatter" + i, scatter.plate(i).current());
            put(saved, "scatterMax" + i, scatter.plate(i).capacity());
        }
        for (int i = 0; i < PLATE_COUNT; i++) {
            put(saved, "base" + i, plate(i).baseCurrent());
            put(saved, "reserve" + i, plate(i).reserveCurrent());
        }
        return Map.copyOf(saved);
    }

    /** No legacy fallback, reset, clamp, missing-field default or unknown-field acceptance. */
    public static ZeroGapArmorShares fromDecimalMap(ExactCombatState base, ScatterPlateShares scatter,
                                                   SharedShieldReserveShares reserves, Map<String, String> saved) {
        Objects.requireNonNull(saved, "saved armor");
        if (!saved.keySet().equals(SAVE_FIELDS)) throw new IllegalArgumentException("Invalid armor state fields");
        if (!SCHEMA_VERSION.equals(saved.get("version"))) throw new IllegalArgumentException("Unsupported armor state schema");
        validateParents(base, scatter, reserves);
        requireEqual(read(saved, "shield"), base.shield(), "saved base current");
        requireEqual(read(saved, "maxShield"), base.maxShield(), "saved base maximum");
        requireEqual(read(saved, "lower"), reserves.lower(), "saved lower reserve");
        requireEqual(read(saved, "upper"), reserves.upper(), "saved upper reserve");
        for (int i = 0; i < ScatterPlateShares.PLATE_COUNT; i++) {
            requireEqual(read(saved, "scatter" + i), scatter.plate(i).current(), "saved NEW212 current " + i);
            requireEqual(read(saved, "scatterMax" + i), scatter.plate(i).capacity(), "saved NEW212 maximum " + i);
        }
        List<BigDecimal> baseCurrents = new ArrayList<>(PLATE_COUNT);
        List<BigDecimal> reserveCurrents = new ArrayList<>(PLATE_COUNT);
        for (int i = 0; i < PLATE_COUNT; i++) {
            baseCurrents.add(read(saved, "base" + i));
            reserveCurrents.add(read(saved, "reserve" + i));
        }
        return restore(base, scatter, reserves, baseCurrents, reserveCurrents);
    }

    private Transaction proposal(ZeroGapArmorShares after) {
        return new Transaction(this, after, brokenBetween(this, after));
    }

    private static List<Integer> brokenBetween(ZeroGapArmorShares before, ZeroGapArmorShares after) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < PLATE_COUNT; i++)
            if (before.plate(i).current().signum() > 0 && after.plate(i).current().signum() == 0) result.add(i);
        return List.copyOf(result);
    }

    private boolean sameParents(ExactCombatState otherBase, ScatterPlateShares otherScatter,
                                SharedShieldReserveShares otherReserves) {
        if (!base.equals(otherBase) || !reserves.equals(otherReserves)) return false;
        for (int i = 0; i < ScatterPlateShares.PLATE_COUNT; i++)
            if (!scatter.plate(i).equals(otherScatter.plate(i))) return false;
        return true;
    }

    private static void validateParents(ExactCombatState base, ScatterPlateShares scatter,
                                        SharedShieldReserveShares reserves) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(scatter, "scatter").requireMatches(base.shield(), base.maxShield());
        Objects.requireNonNull(reserves, "reserves").validate(base);
        reserveMaximum(base);
    }

    /** Reserve parent ceilings are mathematical halves; they may include half one persisted quantum. */
    private static BigDecimal reserveMaximum(ExactCombatState base) {
        return requireCapacity(SharedShieldReserveShares.halfCapacity(base), "armor reserve parent capacity");
    }

    /** Only derived capacities admit a half quantum; all balances remain CombatAmounts amounts. */
    private static BigDecimal requireCapacity(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.scale() < -CombatAmounts.MAX_ABSOLUTE_SCALE || value.scale() > CombatAmounts.MAX_ABSOLUTE_SCALE + 1
                || value.precision() > CombatAmounts.MAX_DECIMAL_PRECISION + 1
                || value.signum() < 0 || value.compareTo(CombatAmounts.CAP) > 0)
            throw new IllegalArgumentException(name + " exceeds supported mathematical capacity");
        BigDecimal normalized = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        if (normalized.scale() == CombatAmounts.MAX_ABSOLUTE_SCALE + 1
                && normalized.unscaledValue().mod(java.math.BigInteger.TEN).intValueExact() != 5)
            throw new IllegalArgumentException(name + " is not an exact half-quantum capacity");
        return normalized;
    }

    private static void distribute(List<BigDecimal> currents, List<BigDecimal> capacities,
                                   BigDecimal delta, int first, int stride, int count) {
        if (delta.signum() == 0) return;
        boolean recovery = delta.signum() > 0;
        BigDecimal remaining = delta.abs();
        for (int child = 0; child < count && remaining.signum() != 0; child++) {
            int i = first + child * stride;
            BigDecimal available = recovery ? capacities.get(i).setScale(CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN)
                    .subtract(currents.get(i)) : currents.get(i);
            if (available.signum() < 0) throw new IllegalArgumentException("Existing armor current exceeds new component capacity");
            BigDecimal change = remaining.min(available);
            currents.set(i, recovery ? currents.get(i).add(change) : currents.get(i).subtract(change));
            remaining = remaining.subtract(change);
        }
        if (remaining.signum() != 0) throw new IllegalArgumentException("Unable to reconcile exact armor parent change");
    }

    private static List<BigDecimal> basePartitions(ScatterPlateShares scatter, boolean capacity) {
        List<BigDecimal> result = new ArrayList<>(PLATE_COUNT);
        for (int i = 0; i < ScatterPlateShares.PLATE_COUNT; i++)
            result.addAll(partition(capacity ? scatter.plate(i).capacity() : scatter.plate(i).current(), 2));
        return result;
    }

    private static List<BigDecimal> reservePartitions(BigDecimal lower, BigDecimal upper) {
        List<BigDecimal> even = partition(lower, 3), odd = partition(upper, 3);
        List<BigDecimal> result = new ArrayList<>(PLATE_COUNT);
        for (int i = 0; i < 3; i++) { result.add(even.get(i)); result.add(odd.get(i)); }
        return result;
    }

    /**
     * Monotone quantum partition: each early child receives at most one remaining
     * whole quantum. A mathematical half-quantum capacity goes on the next child,
     * so every valid strict current still fits and no fractional ceiling is lost.
     */
    private static List<BigDecimal> partition(BigDecimal total, int count) {
        BigDecimal divisor = BigDecimal.valueOf(count);
        BigDecimal whole = total.setScale(CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN);
        BigDecimal fractional = total.subtract(whole);
        BigDecimal each = whole.divide(divisor, CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN);
        int remainder = whole.subtract(each.multiply(divisor)).divide(QUANTUM).intValueExact();
        List<BigDecimal> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            result.add((i < remainder ? each.add(QUANTUM) : each).add(i == remainder ? fractional : BigDecimal.ZERO));
        return result;
    }

    private List<BigDecimal> baseCurrents() {
        List<BigDecimal> result = new ArrayList<>(PLATE_COUNT);
        for (Plate plate : plates) result.add(plate.baseCurrent());
        return result;
    }

    private List<BigDecimal> reserveCurrents() {
        List<BigDecimal> result = new ArrayList<>(PLATE_COUNT);
        for (Plate plate : plates) result.add(plate.reserveCurrent());
        return result;
    }

    private BigDecimal reserveSum(int parity) {
        return plate(parity).reserveCurrent().add(plate(parity + 2).reserveCurrent()).add(plate(parity + 4).reserveCurrent());
    }

    private static void requireSix(List<BigDecimal> values, String name) {
        if (Objects.requireNonNull(values, name).size() != PLATE_COUNT)
            throw new IllegalArgumentException("Exactly six armor " + name + " are required");
    }

    private static int requireIndex(int index) {
        if (index < 0 || index >= PLATE_COUNT) throw new IllegalArgumentException("Armor index must be between 0 and 5");
        return index;
    }

    private static void requireEqual(BigDecimal actual, BigDecimal expected, String name) {
        if (actual.compareTo(expected) != 0) throw new IllegalArgumentException("Armor mismatch: " + name);
    }

    private static void put(Map<String, String> values, String key, BigDecimal value) {
        values.put(key, CombatAmounts.decimalString(value));
    }

    private static BigDecimal read(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) throw new IllegalArgumentException("Missing armor state value: " + key);
        return CombatAmounts.parse(value, "saved armor " + key);
    }

    private static Set<String> saveFields() {
        Set<String> fields = new HashSet<>(Set.of("version", "shield", "maxShield", "lower", "upper"));
        for (int i = 0; i < ScatterPlateShares.PLATE_COUNT; i++) { fields.add("scatter" + i); fields.add("scatterMax" + i); }
        for (int i = 0; i < PLATE_COUNT; i++) { fields.add("base" + i); fields.add("reserve" + i); }
        return Set.copyOf(fields);
    }
}

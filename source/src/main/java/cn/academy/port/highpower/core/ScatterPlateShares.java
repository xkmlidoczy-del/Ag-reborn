package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * NEW212's three persistent shares of the existing base shield S. This is an
 * allocation of S, never another pool, reserve, recovery budget, or HP ledger.
 * All operations are immutable. The adapter must persist every returned state
 * and keep its total synchronized with the authoritative base shield.
 */
public final class ScatterPlateShares {
    public static final int PLATE_COUNT = 3;
    public static final String SCHEMA_VERSION = "1";
    private static final BigDecimal THREE = BigDecimal.valueOf(PLATE_COUNT);
    private static final BigDecimal QUANTUM = BigDecimal.ONE.scaleByPowerOfTen(-CombatAmounts.MAX_ABSOLUTE_SCALE);

    public record Plate(BigDecimal current, BigDecimal capacity) {
        public Plate {
            current = CombatAmounts.requireAmount(current, "plate current");
            capacity = CombatAmounts.requireAmount(capacity, "plate capacity");
            if (current.compareTo(capacity) > 0) throw new IllegalArgumentException("Plate current exceeds capacity");
        }
    }

    /** Residual has not been applied to HP or any other shield/plate. */
    public record Contact(ScatterPlateShares shares, BigDecimal absorbed, BigDecimal residual) {}

    private final List<Plate> plates;
    private final BigDecimal shield;
    private final BigDecimal maxShield;

    private ScatterPlateShares(BigDecimal maxShield, List<BigDecimal> currents) {
        this.maxShield = CombatAmounts.requireAmount(maxShield, "maximum base shield");
        Objects.requireNonNull(currents, "plate currents");
        if (currents.size() != PLATE_COUNT) throw new IllegalArgumentException("Exactly three plate currents are required");
        List<BigDecimal> capacities = partition(this.maxShield);
        List<Plate> checked = new ArrayList<>(PLATE_COUNT);
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < PLATE_COUNT; i++) {
            Plate plate = new Plate(currents.get(i), capacities.get(i));
            checked.add(plate);
            total = total.add(plate.current());
        }
        this.plates = List.copyOf(checked);
        this.shield = CombatAmounts.requireAmount(total, "total plate shield");
    }

    /**
     * One-time migration/enrollment only: partitions an EXISTING base balance.
     * Never call this for toggles, relearning, category changes, or loading an
     * existing plate save. Those paths must retain or restore the saved currents.
     */
    public static ScatterPlateShares initialize(BigDecimal currentBaseShield, BigDecimal maxBaseShield) {
        BigDecimal current = CombatAmounts.requireAmount(currentBaseShield, "base shield");
        BigDecimal maximum = CombatAmounts.requireAmount(maxBaseShield, "maximum base shield");
        if (current.compareTo(maximum) > 0) throw new IllegalArgumentException("Base shield exceeds maximum");
        return new ScatterPlateShares(maximum, partition(current));
    }

    /** Reconstructs exact saved currents; an invalid/mismatched save never falls back to initialization. */
    public static ScatterPlateShares restore(BigDecimal currentBaseShield, BigDecimal maxBaseShield,
                                            List<BigDecimal> savedCurrents) {
        return new ScatterPlateShares(maxBaseShield, savedCurrents).requireMatches(currentBaseShield, maxBaseShield);
    }

    public List<Plate> plates() { return plates; }
    public Plate plate(int index) { return plates.get(requireIndex(index)); }
    public BigDecimal shield() { return shield; }
    public BigDecimal maxShield() { return maxShield; }

    /** Use before committing paired base/plate transactions or accepting persisted state. */
    public ScatterPlateShares requireMatches(BigDecimal currentBaseShield, BigDecimal maxBaseShield) {
        BigDecimal current = CombatAmounts.requireAmount(currentBaseShield, "base shield");
        BigDecimal maximum = CombatAmounts.requireAmount(maxBaseShield, "maximum base shield");
        if (shield.compareTo(current) != 0 || maxShield.compareTo(maximum) != 0)
            throw new IllegalArgumentException("Plate shares do not match the authoritative base shield");
        return this;
    }

    /**
     * Only an authenticated contact after the native mitigation pipeline may
     * call this. Absorbs min(actual incoming, contacted current, supplied ceiling)
     * exactly once. No other plate or reserve can pay for this contact. The
     * adapter decides contact authenticity; this arithmetic cannot authenticate it.
     */
    public Contact contact(int index, BigDecimal mitigatedIncoming, BigDecimal authenticatedCeiling) {
        index = requireIndex(index);
        BigDecimal incoming = CombatAmounts.requireAmount(mitigatedIncoming, "mitigated incoming damage");
        BigDecimal ceiling = CombatAmounts.requireAmount(authenticatedCeiling, "authenticated absorption ceiling");
        BigDecimal absorbed = incoming.min(plate(index).current()).min(ceiling);
        if (absorbed.signum() == 0) return new Contact(this, BigDecimal.ZERO, incoming);
        List<BigDecimal> currents = currents();
        currents.set(index, currents.get(index).subtract(absorbed));
        return new Contact(new ScatterPlateShares(maxShield, currents), absorbed, incoming.subtract(absorbed));
    }

    /**
     * Reconciles an already committed ordinary spend/damage debit of base S.
     * Removes only the observed decrease in stable plate order 0, 1, 2. Never
     * redistributes surviving balances or restores an exhausted plate.
     */
    public ScatterPlateShares afterBaseSpend(BigDecimal newBaseShield) {
        BigDecimal next = CombatAmounts.requireAmount(newBaseShield, "base shield after spending");
        if (next.compareTo(shield) > 0) throw new IllegalArgumentException("A base spend cannot increase shield");
        return distribute(shield.subtract(next), false);
    }

    /** IDs exhausted by precisely this deterministic ordinary base debit, never by a proposed redistribution. */
    public List<Integer> brokenByBaseSpend(BigDecimal newBaseShield) {
        var after=afterBaseSpend(newBaseShield);var broken=new ArrayList<Integer>();
        for(int i=0;i<PLATE_COUNT;i++)if(plate(i).current().signum()>0&&after.plate(i).current().signum()==0)broken.add(i);
        return List.copyOf(broken);
    }

    /**
     * Reconciles an already committed actual gain of base S, not a proposed
     * recovery budget. Fills deficits in stable plate order 0, 1, 2 using only
     * that gain. A zero gain cannot heal a broken plate, even below full S.
     */
    public ScatterPlateShares afterBaseRecovery(BigDecimal newBaseShield) {
        BigDecimal next = CombatAmounts.requireAmount(newBaseShield, "base shield after recovery");
        if (next.compareTo(shield) < 0 || next.compareTo(maxShield) > 0)
            throw new IllegalArgumentException("Actual recovery must increase shield within the existing maximum");
        return distribute(next.subtract(shield), true);
    }

    /**
     * Changes only capacities. Growth never awards shield; a decrease that
     * cannot hold every existing plate is rejected rather than moving/clipping it.
     */
    public ScatterPlateShares withMaximum(BigDecimal newMaxShield) {
        BigDecimal maximum = CombatAmounts.requireAmount(newMaxShield, "maximum base shield");
        if (maximum.compareTo(maxShield) == 0) return this;
        return new ScatterPlateShares(maximum, currents());
    }

    /** Exact decimal strings, including independent saved currents and authoritative totals. */
    public Map<String, String> toDecimalMap() {
        return Map.of("version", SCHEMA_VERSION,
                "shield", CombatAmounts.decimalString(shield),
                "maxShield", CombatAmounts.decimalString(maxShield),
                "plate0", CombatAmounts.decimalString(plate(0).current()),
                "plate1", CombatAmounts.decimalString(plate(1).current()),
                "plate2", CombatAmounts.decimalString(plate(2).current()));
    }

    /** Rejects missing, malformed, excessive, inconsistent, or future-version state. */
    public static ScatterPlateShares fromDecimalMap(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        if (!SCHEMA_VERSION.equals(required(values, "version"))) throw new IllegalArgumentException("Unsupported plate schema version");
        return restore(CombatAmounts.parse(required(values, "shield"), "base shield"),
                CombatAmounts.parse(required(values, "maxShield"), "maximum base shield"),
                List.of(CombatAmounts.parse(required(values, "plate0"), "plate 0 current"),
                        CombatAmounts.parse(required(values, "plate1"), "plate 1 current"),
                        CombatAmounts.parse(required(values, "plate2"), "plate 2 current")));
    }

    private ScatterPlateShares distribute(BigDecimal amount, boolean recovery) {
        if (amount.signum() == 0) return this;
        List<BigDecimal> currents = currents();
        BigDecimal remaining = amount;
        for (int i = 0; i < PLATE_COUNT && remaining.signum() != 0; i++) {
            BigDecimal available = recovery ? plate(i).capacity().subtract(currents.get(i)) : currents.get(i);
            BigDecimal change = remaining.min(available);
            currents.set(i, recovery ? currents.get(i).add(change) : currents.get(i).subtract(change));
            remaining = remaining.subtract(change);
        }
        if (remaining.signum() != 0) throw new IllegalStateException("Unable to reconcile exact base shield change");
        return new ScatterPlateShares(maxShield, currents);
    }

    private List<BigDecimal> currents() {
        List<BigDecimal> result = new ArrayList<>(PLATE_COUNT);
        for (Plate plate : plates) result.add(plate.current());
        return result;
    }

    /**
     * Divide at the finest admitted quantum, then give at most one remaining
     * quantum to each earlier plate. This bounds decimal work, conserves the
     * total, and makes EVERY individual share monotone in its total. Giving
     * the whole remainder to the last plate would violate that last property
     * (e.g. totals 2 and 3 quanta), making valid tiny initial balances overflow.
     */
    private static List<BigDecimal> partition(BigDecimal total) {
        BigDecimal each = total.divide(THREE, CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN);
        int remainder = total.subtract(each.multiply(THREE)).divide(QUANTUM).intValueExact();
        List<BigDecimal> result = new ArrayList<>(PLATE_COUNT);
        for (int i = 0; i < PLATE_COUNT; i++) result.add(i < remainder ? each.add(QUANTUM) : each);
        return result;
    }

    private static int requireIndex(int index) {
        if (index < 0 || index >= PLATE_COUNT) throw new IllegalArgumentException("Plate index must be 0, 1, or 2");
        return index;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) throw new IllegalArgumentException("Missing plate state field: " + key);
        return value;
    }
}

package cn.academy.port.highpower;

import cn.academy.port.highpower.core.CombatAmounts;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.UUID;
import java.util.ArrayList;
import java.util.function.ToIntFunction;

/** Read-only presentation boundaries; no display number can be written into a combat ledger. */
public final class HighPowerRecoveryHudModel {
    public static final int SEND_INTERVAL = 5, KEEPALIVE_TICKS = 20, STALE_TICKS = 45;
    public static final Set<String> STATUSES = Set.of("inactive", "unlearned", "unsupported", "dead", "full", "paused", "gamerule_off", "food_low", "saturated", "slow");
    public static final BigDecimal MAX_SCHEDULED_RATE = CombatAmounts.CAP.multiply(BigDecimal.TWO);
    private static final BigDecimal MIN_VISIBLE = new BigDecimal("0.0001");
    private HighPowerRecoveryHudModel() {}

    public record Values(String health, String maxHealth, String shield, String maxShield, String status,
                         String exactHpPerSecond, int pulseTicks, int ticksUntilPulse, int pauseTicksRemaining,
                         boolean shieldActive, String domainReserve, String domainCapacity, boolean domainActive, String category,
                         String curtainReserve, String curtainCapacity, boolean curtainActive,
                         String zeroGapReserve, String zeroGapCapacity, boolean zeroGapActive) {
        /** Source-compatible NEW242 snapshots have no NEW252 aggregate reserve. */
        public Values(String health, String maxHealth, String shield, String maxShield, String status,
                      String exactHpPerSecond, int pulseTicks, int ticksUntilPulse, int pauseTicksRemaining,
                      boolean shieldActive, String domainReserve, String domainCapacity, boolean domainActive, String category,
                      String curtainReserve, String curtainCapacity, boolean curtainActive) {
            this(health,maxHealth,shield,maxShield,status,exactHpPerSecond,pulseTicks,ticksUntilPulse,pauseTicksRemaining,
                    shieldActive,domainReserve,domainCapacity,domainActive,category,curtainReserve,curtainCapacity,curtainActive,"0","0",false);
        }
        /** Source-compatible older snapshots have no NEW242 tranche. */
        public Values(String health, String maxHealth, String shield, String maxShield, String status,
                      String exactHpPerSecond, int pulseTicks, int ticksUntilPulse, int pauseTicksRemaining,
                      boolean shieldActive, String domainReserve, String domainCapacity, boolean domainActive, String category) {
            this(health,maxHealth,shield,maxShield,status,exactHpPerSecond,pulseTicks,ticksUntilPulse,pauseTicksRemaining,
                    shieldActive,domainReserve,domainCapacity,domainActive,category,"0","0",false);
        }
        public Values(String health, String maxHealth, String shield, String maxShield, String status,
                      String exactHpPerSecond, int pulseTicks, int ticksUntilPulse, int pauseTicksRemaining, boolean shieldActive) {
            this(health, maxHealth, shield, maxShield, status, exactHpPerSecond, pulseTicks, ticksUntilPulse, pauseTicksRemaining, shieldActive, "0", "0", false, "electromaster");
        }
        /** Existing EM callers retain their source-compatible constructor. */
        public Values(String health, String maxHealth, String shield, String maxShield, String status,
                      String exactHpPerSecond, int pulseTicks, int ticksUntilPulse, int pauseTicksRemaining,
                      boolean shieldActive, String domainReserve, String domainCapacity, boolean domainActive) {
            this(health, maxHealth, shield, maxShield, status, exactHpPerSecond, pulseTicks, ticksUntilPulse,
                    pauseTicksRemaining, shieldActive, domainReserve, domainCapacity, domainActive, "electromaster");
        }
        public Values {
            if (!hudCategory(category)) throw new IllegalArgumentException("Unsupported high-power HUD category");
            if (!"electromaster".equals(category) && (domainActive
                    || decimal(domainReserve).signum() != 0 || decimal(domainCapacity).signum() != 0))
                throw new IllegalArgumentException("Only electromaster can advertise an EM domain reserve");
            BigDecimal hp = decimal(health), maxHp = decimal(maxHealth), sp = decimal(shield), maxSp = decimal(maxShield);
            BigDecimal reserve = decimal(domainReserve), reserveCap = decimal(domainCapacity);
            if (reserve.compareTo(reserveCap) > 0 || reserveCap.compareTo(maxSp) > 0 || maxSp.add(reserveCap).compareTo(CombatAmounts.CAP) > 0)
                throw new IllegalArgumentException("Invalid separate domain reserve or combined shield capacity");
            BigDecimal curtain = decimal(curtainReserve), curtainCap = decimal(curtainCapacity);
            if (!"meltdowner".equals(category) && (curtainActive || curtain.signum()!=0 || curtainCap.signum()!=0)
                    || curtain.compareTo(curtainCap)>0 || curtainCap.multiply(BigDecimal.TWO).compareTo(maxSp)>0
                    || maxSp.add(curtainCap).compareTo(CombatAmounts.CAP)>0)
                throw new IllegalArgumentException("Invalid MD curtain half-reserve");
            BigDecimal armor = decimal(zeroGapReserve), armorCap = decimal(zeroGapCapacity);
            boolean armorPresent = zeroGapActive || armor.signum() != 0 || armorCap.signum() != 0;
            if (armorPresent && (!"meltdowner".equals(category) || armorCap.signum() == 0
                    || armorCap.compareTo(maxSp) != 0 || curtainCap.multiply(BigDecimal.TWO).compareTo(maxSp) != 0
                    || armor.compareTo(armorCap) > 0 || armor.compareTo(curtain) < 0
                    || armor.subtract(curtain).compareTo(curtainCap) > 0
                    || maxSp.add(armorCap).compareTo(CombatAmounts.CAP) > 0))
                throw new IllegalArgumentException("Invalid MD zero-gap aggregate reserve or 2S capacity");
            if (zeroGapActive && (!shieldActive || curtainActive))
                throw new IllegalArgumentException("Active zero-gap armor must own the shield exclusively from curtain");
            BigDecimal scheduledRate = rate(exactHpPerSecond);
            if (scheduledRate.compareTo(maxHp.multiply(BigDecimal.TWO)) > 0)
                throw new IllegalArgumentException("Recovery rate exceeds two native pulses per second");
            if ("vecmanip".equals(category) && shieldActive && maxSp.signum() == 0) throw new IllegalArgumentException("Active shield requires authenticated positive capacity");
            if (maxHp.signum() <= 0 || hp.compareTo(maxHp) > 0 || sp.compareTo(maxSp) > 0 || !STATUSES.contains(status))
                throw new IllegalArgumentException("Invalid recovery HUD ledger or status");
            if (pulseTicks != 0 && pulseTicks != 10 && pulseTicks != 80 || ticksUntilPulse < 0 || ticksUntilPulse > pulseTicks
                    || pauseTicksRemaining < 0 || pauseTicksRemaining > 12000)
                throw new IllegalArgumentException("Invalid recovery HUD timing");
            boolean active = status.equals("saturated") || status.equals("slow");
            if (!active && scheduledRate.signum() != 0)
                throw new IllegalArgumentException("Blocked recovery must not advertise a positive rate");
        }
        public double healthRatio() { return ratio(health, maxHealth); }
        public double shieldRatio() { return ratio(shield, maxShield); }
        public double pulseRatio() { return pulseTicks == 0 ? 0 : clamp((pulseTicks - ticksUntilPulse) / (double)pulseTicks); }
        public boolean healing() { return status.equals("saturated") || status.equals("slow"); }
        public String categoryLabel() { return switch (category) {
            case "electromaster" -> "EM";
            case "meltdowner" -> "MD";
            case "teleporter" -> "TP";
            case "vecmanip" -> "VM";
            case "mental_out" -> "MO";
            case "aero_hand" -> "AH";
            case "float_dial" -> "FD";
            case "nitrogen_armor" -> "NA";
            case "dark_matter" -> "DM";
            case "hydro_hand" -> "HH";
            case "paper_psychokinesis" -> "PP";
            case "liquid_shadow" -> "LS";
            default -> throw new IllegalStateException("Unsupported high-power HUD category");
        }; }
        public Accent accent() { return HighPowerRecoveryHudModel.accent(category); }
        public boolean electromaster() { return "electromaster".equals(category); }
        public boolean meltdowner() { return "meltdowner".equals(category); }
        public boolean teleporter() { return "teleporter".equals(category); }
        public boolean vector() { return "vecmanip".equals(category); }
        /** Every category carries the category shield (see EsperShields); it is shown whenever it has a capacity. */
        public boolean shieldVisible() { return decimal(maxShield).signum() > 0; }
        /** Shield left: the esper has super armor (no knockback) until it breaks. */
        public boolean shieldHolding() { return shieldActive && decimal(shield).signum() > 0; }
        /** Lang key suffix of this category's shield name. */
        public String shieldKey() { return "shield." + category; }
        public boolean curtainVisible() { return meltdowner() && decimal(curtainCapacity).signum()>0; }
        public boolean domainVisible() { return electromaster() && decimal(domainCapacity).signum() > 0; }
        /** Only an authenticated learned NEW252 snapshot carries its complete reserve capacity. */
        public boolean zeroGapVisible() { return meltdowner() && decimal(zeroGapCapacity).signum() > 0; }
        /** Lower is already part of zeroGapReserve; adding it again would invent a third half-reserve. */
        public String zeroGapTotalShield() { return CombatAmounts.decimalString(decimal(shield).add(decimal(zeroGapReserve))); }
        public String zeroGapFullCapacity() { return CombatAmounts.decimalString(decimal(maxShield).add(decimal(zeroGapCapacity))); }
    }

    public record Identity(UUID owner, int entity, String dimension, long epoch, long sequence) {
        public Identity {
            if (owner == null || entity < 0 || dimension == null || dimension.length() > 128
                    || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || epoch <= 0 || sequence <= 0)
                throw new IllegalArgumentException("Invalid recovery HUD identity");
        }
    }

    /** Preserve the retired epoch across world/player transitions, but not a new connection. */
    public static final class Session {
        private Object connection, world, player;
        private long retiredEpoch, epoch, sequence;
        private int age = STALE_TICKS;
        private Values values;
        public void synchronize(Object nextConnection, Object nextWorld, Object nextPlayer) {
            if (connection == nextConnection && world == nextWorld && player == nextPlayer) return;
            retiredEpoch = connection == nextConnection ? Math.max(retiredEpoch, epoch) : 0;
            connection = nextConnection; world = nextWorld; player = nextPlayer;
            epoch = sequence = 0; age = STALE_TICKS; values = null;
        }
        public boolean accept(Identity identity, Values next, UUID owner, int entity, String dimension) {
            if (connection == null || world == null || player == null || !identity.owner().equals(owner)
                    || identity.entity() != entity || !identity.dimension().equals(dimension)
                    || identity.epoch() <= retiredEpoch || identity.epoch() < epoch
                    || identity.epoch() == epoch && identity.sequence() <= sequence) return false;
            epoch = identity.epoch(); sequence = identity.sequence(); values = next; age = 0;
            return true;
        }
        public void tick(boolean paused) { if (!paused && age < STALE_TICKS) age++; }
        public Values current() { return age < STALE_TICKS ? values : null; }
        public int age() { return age; }
    }

    public static BigDecimal decimal(String value) {
        BigDecimal parsed = CombatAmounts.parse(value, "HUD exact decimal");
        if (!parsed.toPlainString().equals(value)) throw new IllegalArgumentException("Noncanonical HUD decimal");
        return parsed;
    }
    /** A rate is a display-only per-second quantity, not a ledger amount: native max HP=1 permits two pools/second. */
    public static BigDecimal rate(String value) {
        if (value == null || value.length() > CombatAmounts.MAX_ENCODED_LENGTH)
            throw new IllegalArgumentException("Recovery rate decimal string is too long or missing");
        final BigDecimal parsed;
        try { parsed = new BigDecimal(value); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid recovery rate decimal", invalid); }
        if (parsed.scale() < -CombatAmounts.MAX_ABSOLUTE_SCALE || parsed.scale() > CombatAmounts.MAX_ABSOLUTE_SCALE
                || parsed.precision() > CombatAmounts.MAX_DECIMAL_PRECISION || parsed.signum() < 0 || parsed.compareTo(MAX_SCHEDULED_RATE) > 0)
            throw new IllegalArgumentException("Recovery rate exceeds bounded display range");
        BigDecimal canonical = parsed.signum() == 0 ? BigDecimal.ZERO : parsed.stripTrailingZeros();
        if (!canonical.toPlainString().equals(value)) throw new IllegalArgumentException("Noncanonical recovery rate decimal");
        return canonical;
    }
    /** Labels are presentation-only; decimal arithmetic and exact wire values never depend on language. */
    public record NumberUnit(int exponent, String symbol) {}
    public record NumberLocale(List<NumberUnit> units, String separator, String approximate, String shortfall, String belowMax) {
        public NumberLocale { units = List.copyOf(units); }
    }
    public record NumberText(String text, BigDecimal represented, boolean approximate, boolean belowThreshold) {}
    public record PoolText(String current, String maximum, String shortfall, boolean full) {
        public String text() { return current + " / " + maximum + (shortfall.isEmpty() ? "" : " (" + shortfall + ")"); }
    }
    public static NumberLocale numberLocale(String language) { return numberLocale(language, (key, fallback) -> fallback); }
    public static NumberLocale numberLocale(String language, BiFunction<String, String, String> translate) {
        String code = language == null ? "en_us" : language.toLowerCase(Locale.ROOT);
        boolean traditional = code.startsWith("zh_tw") || code.startsWith("zh_hk") || code.startsWith("zh_hant");
        boolean japanese = code.startsWith("ja");
        boolean chinese = code.startsWith("zh");
        boolean myriad = chinese || japanese;
        int[] powers = myriad ? new int[]{4, 8, 12, 16, 20} : new int[]{3, 6, 9, 12, 15, 18};
        String[] symbols = japanese ? new String[]{"万", "億", "万億", "京", "垓"}
                : traditional ? new String[]{"萬", "億", "萬億", "京", "垓"}
                : chinese ? new String[]{"万", "亿", "万亿", "京", "垓"}
                : new String[]{"thousand", "million", "billion", "trillion", "quadrillion", "quintillion"};
        var units = new java.util.ArrayList<NumberUnit>(); units.add(new NumberUnit(0, ""));
        for (int i = 0; i < powers.length; i++) units.add(new NumberUnit(powers[i], label(translate, "number.unit." + powers[i], symbols[i])));
        return new NumberLocale(units, myriad ? "" : " ",
                label(translate, "number.approximate", traditional || japanese ? "約" : chinese ? "约" : "≈"),
                label(translate, "number.shortfall", japanese ? "不足" : chinese ? "差" : "short "),
                label(translate, "number.below_max", traditional ? "未滿" : chinese ? "未满" : japanese ? "未満" : "below max"));
    }
    /** Compact screen labels only; the full-word locale remains available to help/debug callers. */
    public static NumberLocale hudNumberLocale(String language) { return hudNumberLocale(language, (key, fallback) -> fallback); }
    public static NumberLocale hudNumberLocale(String language, BiFunction<String, String, String> translate) {
        String code = language == null ? "en_us" : language.toLowerCase(Locale.ROOT);
        NumberLocale full = numberLocale(language, translate);
        if (code.startsWith("zh") || code.startsWith("ja")) return full;
        String[] symbols = {"", "K", "M", "B", "T", "Qa", "Qi"};
        var units = new java.util.ArrayList<NumberUnit>();
        for (int i = 0; i < full.units().size(); i++) {
            var unit = full.units().get(i);
            units.add(new NumberUnit(unit.exponent(), i == 0 ? "" : label(translate, "number.short_unit." + unit.exponent(), symbols[i])));
        }
        return new NumberLocale(units, "", full.approximate(), label(translate, "number.short_shortfall", "−"), label(translate, "number.short_below_max", "not full"));
    }
    private static String label(BiFunction<String, String, String> translate, String key, String fallback) {
        String translated = translate.apply(key, fallback);
        return translated == null || translated.isBlank() || translated.length() > 32 ? fallback : translated;
    }
    public static String compact(String exact) { return compact(exact, numberLocale("en_us")); }
    public static String compact(String exact, NumberLocale locale) { return number(decimal(exact), locale).text(); }
    public static String compactRate(String exact) { return compactRate(exact, numberLocale("en_us")); }
    public static String compactRate(String exact, NumberLocale locale) { return number(rate(exact), locale).text(); }
    private static NumberText number(BigDecimal value, NumberLocale locale) { return number(value, unit(value, locale), locale); }
    private static NumberUnit unit(BigDecimal value, NumberLocale locale) {
        NumberUnit selected = locale.units().getFirst();
        for (NumberUnit unit : locale.units()) if (value.compareTo(BigDecimal.ONE.scaleByPowerOfTen(unit.exponent())) >= 0) selected = unit;
        return selected;
    }
    private static NumberText number(BigDecimal value, NumberUnit unit, NumberLocale locale) {
        if (value.signum() == 0) return new NumberText("0", BigDecimal.ZERO, false, false);
        BigDecimal scaled = value.movePointLeft(unit.exponent());
        String suffix = unit.symbol().isEmpty() ? "" : locale.separator() + unit.symbol();
        if (scaled.compareTo(MIN_VISIBLE) < 0)
            return new NumberText("<0.0001" + suffix, BigDecimal.ZERO, false, true);
        BigDecimal rounded = scaled.setScale(Math.min(4, Math.max(0, scaled.scale())), RoundingMode.DOWN).stripTrailingZeros();
        boolean approximate = rounded.compareTo(scaled) != 0;
        return new NumberText((approximate ? locale.approximate() : "") + rounded.toPlainString() + suffix,
                rounded.movePointRight(unit.exponent()), approximate, false);
    }
    /** Shared unit within a pool; critically low balances retain their own readable unit rather than disappearing. */
    public static PoolText pool(String current, String maximum, NumberLocale locale) {
        BigDecimal value = decimal(current), max = decimal(maximum);
        if (value.compareTo(max) > 0) throw new IllegalArgumentException("Displayed pool exceeds its maximum");
        NumberUnit common = unit(max, locale);
        NumberText maxText = number(max, common, locale);
        NumberText currentText = value.signum() > 0 && value.movePointLeft(common.exponent()).compareTo(BigDecimal.ONE) < 0
                ? number(value, locale) : number(value, common, locale);
        BigDecimal deficit = max.subtract(value);
        if (value.signum() > 0 && deficit.signum() > 0
                && (deficit.compareTo(MIN_VISIBLE.scaleByPowerOfTen(common.exponent())) < 0 || currentText.text().equals(maxText.text()))) {
            NumberText missing = number(deficit, locale);
            if (!maxText.approximate() && !maxText.belowThreshold() && !missing.approximate() && !missing.belowThreshold())
                return new PoolText(maxText.text() + "−" + missing.text(), maxText.text(), "", false);
            String below = !maxText.approximate() && !maxText.belowThreshold() ? "<" + maxText.text() : locale.belowMax();
            return new PoolText(below, maxText.text(), locale.shortfall() + missing.text(), false);
        }
        return new PoolText(currentText.text(), maxText.text(), "", deficit.signum() == 0);
    }
    /** Exact endpoint classification prevents a tiny live balance vanishing or a deficit becoming a full bar. */
    public static int barPixels(String current, String maximum, int width) {
        if (width <= 0) return 0;
        BigDecimal value = decimal(current), max = decimal(maximum);
        if (value.compareTo(max) > 0) throw new IllegalArgumentException("Displayed bar exceeds its maximum");
        if (max.signum() == 0 || value.signum() == 0) return 0;
        if (value.compareTo(max) == 0) return width;
        if (width == 1) return 0; // A one-pixel test canvas cannot represent an incomplete fill; real panels are wider.
        int filled = value.multiply(BigDecimal.valueOf(width)).divide(max, 0, RoundingMode.DOWN).intValueExact();
        return Math.max(1, Math.min(width - 1, filled));
    }
    /** Layout-only floating point: every numeric string above was formatted with BigDecimal. */
    public static float fittedScale(int availablePixels, int textPixels, float preferredScale) {
        if (availablePixels <= 0 || textPixels <= 0 || !Float.isFinite(preferredScale) || preferredScale <= 0) return 0;
        return Math.min(preferredScale, availablePixels / (float)textPixels);
    }
    public record NumberRow(int labelWidth, int valueX, int valueWidth, float labelScale, float valueScale) {}
    /** Localized labels get a bounded share; both full strings fit at every supported GUI width. */
    public static NumberRow numberRow(int panelWidth, int labelPixels, int valuePixels) {
        int labelWidth = Math.max(0, Math.min((panelWidth - 15) * 2 / 5, (int)Math.ceil(labelPixels * (2.0 / 3))));
        int valueX = 10 + labelWidth, valueWidth = Math.max(0, panelWidth - 5 - valueX);
        return new NumberRow(labelWidth, valueX, valueWidth, fittedScale(labelWidth, labelPixels, 2f / 3f), fittedScale(valueWidth, valuePixels, .75f));
    }
    public static double ratio(String current, String maximum) {
        BigDecimal max = decimal(maximum);
        if (max.signum() == 0) return 0;
        return clamp(decimal(current).divide(max, MathContext.DECIMAL64).doubleValue());
    }
    /** Retain the native low-health absorption jitter predicate after hiding its HP proxy argument. */
    public static int absorptionJitterThreshold(int nativeHealth) { return 4 - Math.max(0, nativeHealth); }
    public static double clamp(double value) { return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; }

    /** Pure row geometry. A blocked zero rate is already explained by its status, not a second +0 row. */
    public record CompactRows(int height, int healthY, int healthBarY, int shieldY, int shieldBarY,
                              int reserveY, int reserveBarY, int rateY, int statusY, int pulseY) {
        public boolean showRate() { return rateY >= 0; }
        public boolean showReserve() { return reserveY >= 0; }
    }
    private static final CompactRows BASE_IDLE_ROWS = new CompactRows(44, 11, 19, 22, 30, -1, -1, -1, 35, 41);
    private static final CompactRows BASE_HEALING_ROWS = new CompactRows(55, 11, 19, 22, 30, -1, -1, 35, 44, 52);
    private static final CompactRows RESERVE_IDLE_ROWS = new CompactRows(49, 11, 18, 20, 27, 29, 36, -1, 40, 46);
    private static final CompactRows RESERVE_RATE_ROWS = new CompactRows(56, 11, 18, 20, 27, 29, 36, 39, 46, 53);
    public static CompactRows compactRows(Values values) {
        boolean reserve = values.domainVisible() || values.curtainVisible() || values.zeroGapVisible();
        // A blocked or zero-valued recovery rate adds no useful number; the status states its reason.
        boolean showRate = values.healing() && rate(values.exactHpPerSecond()).signum() > 0;
        if (reserve) return showRate ? RESERVE_RATE_ROWS : RESERVE_IDLE_ROWS;
        return showRate ? BASE_HEALING_ROWS : BASE_IDLE_ROWS;
    }

    /** The actual spendable CP row replaces the duplicate title/rate row; the full detail reservation stays 56 px. */
    public record BudgetRows(CompactRows rows, int budgetY) {}
    private static final BudgetRows BUDGET_BASE_ROWS = new BudgetRows(
            new CompactRows(45, 2, 10, 13, 21, -1, -1, -1, 35, 42), 24);
    private static final BudgetRows BUDGET_RESERVE_ROWS = new BudgetRows(
            new CompactRows(56, 2, 10, 13, 21, 24, 32, -1, 46, 53), 35);
    public static BudgetRows budgetRows(Values values) {
        return values.domainVisible() || values.curtainVisible() || values.zeroGapVisible() ? BUDGET_RESERVE_ROWS : BUDGET_BASE_ROWS;
    }
    /** Paint only rows useful to this preset; ALT still reserves and uses the complete 56px detail slot. */
    public static BudgetRows displayRows(Values values, boolean showBudget) {
        boolean reserve = values.domainVisible() || values.curtainVisible() || values.zeroGapVisible();
        int next = 24, reserveY = -1, reserveBar = -1, budgetY = -1, rateY = -1, statusY = -1;
        if (reserve) { reserveY = next; reserveBar = next + 8; next += 11; }
        if (showBudget) { budgetY = next; next += 11; }
        else if (values.healing() && rate(values.exactHpPerSecond()).signum() > 0) { rateY = next; next += 11; }
        if (ordinaryStatusVisible(values)) { statusY = next; next += 11; }
        int height = next - 1;
        boolean pulse = values.healing() && rate(values.exactHpPerSecond()).signum() > 0;
        return new BudgetRows(new CompactRows(height, 2, 10, 13, 21, reserveY, reserveBar, rateY, statusY, pulse ? height - 2 : -1), budgetY);
    }
    /** The reservation may remain 56px, but a two-row idle panel need not paint the unused area. */
    public static int nativePaintHeight(NativePlan plan, int fontHeight, boolean showPulse) {
        int bottom = 1;
        for (var line : plan.text()) bottom = Math.max(bottom, line.y() + fontHeight);
        for (var meter : plan.meters()) bottom = Math.max(bottom, meter.y() + 1);
        if (showPulse) bottom = Math.max(bottom, plan.pulseY() + 1);
        return Math.min(plan.height(), bottom + 2);
    }
    /** Full HP is quiet; every blocked recovery reason and active defensive benefit remains visible. */
    public static boolean ordinaryStatusVisible(Values values) {
        return !values.status().equals("full") || values.domainActive() || values.curtainActive() || values.zeroGapActive();
    }
    /** GUI2 ordinary view uses the full native font, never a fractional fit-to-width transform. */
    public static boolean nativePixelText(double guiScale) { return Double.isFinite(guiScale) && Math.abs(guiScale - 2) < .001; }
    public record NativeSection(String label, String value, int role) {}
    public record NativeText(String text, int x, int y, int ink) {}
    public record NativeMeter(int y, int role) {}
    public record NativePlan(int width, int height, int pulseY, List<NativeText> text, List<NativeMeter> meters) {
        public NativePlan { text = List.copyOf(text); meters = List.copyOf(meters); }
    }
    /** Roles 0/1/2 are HP/base S/separate reserve; 3 title, 4 status and 5 rate are full text; 6 is the accent-tinted growth-style line. */
    public static NativePlan nativePlan(List<NativeSection> sections, int viewportWidth, int fontHeight,
                                        BiFunction<String, Integer, List<String>> wrap, ToIntFunction<String> measure) {
        if (sections == null || sections.size() > 8 || viewportWidth < 166 || fontHeight < 1 || fontHeight > 32
                || wrap == null || measure == null) return null;
        int width = Math.min(202, viewportWidth - 16), usable = width - 10, y = 3;
        var text = new ArrayList<NativeText>(); var meters = new ArrayList<NativeMeter>();
        for (var section : sections) {
            if (section == null || section.label() == null || section.label().length() > 2048
                    || section.value() != null && section.value().length() > 2048 || section.role() < 0 || section.role() > 6) return null;
            int ink = section.role() == 0 || section.role() == 5 ? 1 : section.role() < 3 || section.role() == 6 ? 2 : 0;
            if (section.value() != null && measure.applyAsInt(section.label()) + 6 + measure.applyAsInt(section.value()) <= usable) {
                text.add(new NativeText(section.label(), 5, y, 0));
                text.add(new NativeText(section.value(), width - 5 - measure.applyAsInt(section.value()), y, ink));
                y += fontHeight + 2;
            } else {
                String full = section.label() + (section.value() == null ? "" : " " + section.value());
                List<String> lines = wrap.apply(full, usable);
                if (lines == null || lines.isEmpty() || lines.size() > 32) return null;
                var joined = new StringBuilder();
                for (String line : lines) {
                    if (line == null || line.isEmpty() || measure.applyAsInt(line) > usable || text.size() >= 48) return null;
                    joined.append(line); text.add(new NativeText(line, 5, y, ink)); y += fontHeight + 2;
                }
                if (!joined.toString().equals(full)) return null; // No clipping, ellipses or dropped numerals/spaces.
            }
            if (section.role() < 3 && section.value() != null) { meters.add(new NativeMeter(y - 1, section.role())); y += 3; }
        }
        return new NativePlan(width, Math.max(56, y + 3), y, text, meters);
    }

    public record Box(int x, int y, int width, int height) {
        public boolean intersects(Box other) { return x < other.x + other.width && x + width > other.x && y < other.y + other.height && y + height > other.y; }
        public boolean within(int width, int height) { return x >= 0 && y >= 0 && x + this.width <= width && y + this.height <= height; }
    }
    /** Returns no panel rather than covering reserved ACA, notification, native-status or chat regions. */
    public static Box layout(int width, int height, Box cp, Box keys, Box notification, Box chat) {
        // Reserve the full detail/active height, so ALT and a healing pulse never move nearby HUDs.
        int panelWidth = Math.min(176, width - 16), panelHeight = 56;
        // At large GUI scales the default tutorial banner shares this vertical band.
        // Narrow the panel before considering overlap rather than covering its right edge.
        int rightOfNotification = width - Math.max(0, notification.x + notification.width) - 16;
        if (rightOfNotification >= 150) panelWidth = Math.min(panelWidth, rightOfNotification);
        return layout(width, height, cp, keys, notification, chat, panelWidth, panelHeight);
    }
    /** Full native-font content gets its actual measured reservation; never shrink it after wrapping. */
    public static Box layout(int width, int height, Box cp, Box keys, Box notification, Box chat, int panelWidth, int panelHeight) {
        if (panelWidth < 150 || panelWidth > width - 16 || panelHeight < 56 || panelHeight > height - 16 || height < 140) return null;
        Box[] candidates = {new Box(width - panelWidth - 12, Math.max(8, cp.y + cp.height + 2), panelWidth, panelHeight),
                new Box(8, Math.max(8, notification.y + notification.height + 4), panelWidth, panelHeight),
                new Box(8, 8, panelWidth, panelHeight)};
        Box nativeStatus = new Box(Math.max(0, width / 2 - 110), height - 64, 220, 64);
        for (Box candidate : candidates) if (candidate.within(width, height) && !candidate.intersects(cp)
                && !candidate.intersects(keys) && !candidate.intersects(notification) && !candidate.intersects(chat)
                && !candidate.intersects(nativeStatus)) return candidate;
        return null;
    }

    /** A native-font plan is optional; when it is crowded, keep HP/shield/CP visible through the compact layout. */
    public record Presentation(Box box, NativePlan nativePlan) {
        public boolean nativeText() { return nativePlan != null; }
    }
    public static Presentation presentation(int width, int height, Box cp, Box keys, Box notification, Box chat, NativePlan preferred) {
        if (preferred != null) {
            Box full = layout(width, height, cp, keys, notification, chat, preferred.width(), preferred.height());
            if (full != null) return new Presentation(full, preferred);
        }
        Box compact = layout(width, height, cp, keys, notification, chat);
        return compact == null ? null : new Presentation(compact, null);
    }

    /** Per-category instrument colours (ARGB). Only tint, never text or numbers: exact digits stay white/gray in every category. */
    public record Accent(int primary, int idle, int edge, int pulse) {}
    private static final Accent EM = new Accent(0xffb0e5ff, 0xff779bab, 0xff5f9bbd, 0xff8fd8ff),
            MD = new Accent(0xffb0efbf, 0xff779f83, 0xff5da874, 0xff8ee6a5),
            TP = new Accent(0xffdcb8ff, 0xff8f78b0, 0xff9a6fd1, 0xffc89bff),
            VM = new Accent(0xffe1e9ef, 0xff929da6, 0xff889cae, 0xffc7d9e8),
            // The eight canon categories, in the colours their skill icons and effects already use.
            MO = new Accent(0xfff8c4e2, 0xffa57d93, 0xffd2709f, 0xfff491cc),
            AH = new Accent(0xffc4f8e8, 0xff7da597, 0xff5fbf9c, 0xff7eeecd),
            FD = new Accent(0xffd4f5d9, 0xff88a68c, 0xff6fbf7c, 0xffade7b6),
            NA = new Accent(0xffc8ddff, 0xff7e8fab, 0xff5f86c9, 0xff83b6ff),
            DM = new Accent(0xfff0edff, 0xff9a97ad, 0xff9d95d6, 0xffe1d9ff),
            HH = new Accent(0xffbfeaff, 0xff7a9eb0, 0xff3f9ed4, 0xff5ed0ff),
            PP = new Accent(0xfffff0d4, 0xffb0a084, 0xffd4a95f, 0xffffe0a4),
            LS = new Accent(0xffd6d9e6, 0xff8a8d9c, 0xff7a7f99, 0xffa6adc6);
    /** Classic categories plus the eight canon categories: every category that carries the exact ledger. */
    public static boolean hudCategory(String category) {
        return "electromaster".equals(category) || "meltdowner".equals(category) || "teleporter".equals(category) || "vecmanip".equals(category)
                || cn.academy.port.canon.CanonSkills.knownCategory(category);
    }
    public static Accent accent(String category) {
        return switch (category) {
            case "electromaster" -> EM;
            case "meltdowner" -> MD;
            case "teleporter" -> TP;
            case "vecmanip" -> VM;
            case "mental_out" -> MO;
            case "aero_hand" -> AH;
            case "float_dial" -> FD;
            case "nitrogen_armor" -> NA;
            case "dark_matter" -> DM;
            case "hydro_hand" -> HH;
            case "paper_psychokinesis" -> PP;
            case "liquid_shadow" -> LS;
            default -> throw new IllegalStateException("Unsupported high-power HUD category");
        };
    }

    /**
     * The per-category growth-style status line (presentation only, from the server's {@code EsperGrowthStyle.Hint}). It never
     * carries a ledger amount, so none of the exact-number logic above is involved. Renders as one short translated line.
     */
    public record GrowthHint(String category, int percent, int count) {
        public static final int MAX_PERCENT = 60, MAX_COUNT = 256;
        public GrowthHint {
            if (!"electromaster".equals(category) && !"meltdowner".equals(category) && !"teleporter".equals(category) && !"vecmanip".equals(category))
                throw new IllegalArgumentException("Unsupported growth hint category");
            if (percent < 0 || percent > MAX_PERCENT || count < 0 || count > MAX_COUNT) throw new IllegalArgumentException("Growth hint out of range");
        }
        /** Whether the style is paying (or, for TP, has been exercised) right now; idle hints show a how-to line instead. */
        public boolean engaged() { return switch (category) { case "teleporter" -> count > 0; default -> percent > 0; }; }
        /** 0..1 fill of the style meter: share of the +60% ceiling the next award would earn (TP: skills used out of 4). */
        public double level() {
            return category.equals("teleporter") ? clamp(count / 4.0) : clamp(percent / (double) MAX_PERCENT);
        }
        public String key() { return "growth." + category + (engaged() ? "" : ".idle"); }
        public String text() { return String.format(Locale.ROOT, fallback(), arguments().toArray()); }
        public String fallback() {
            return switch (category) {
                case "electromaster" -> engaged() ? "Resonance x%s: next cast +%s" : "Resonance: keep casting to charge";
                case "meltdowner" -> engaged() ? "Overload push: next cast +%s" : "Overload push: idle, ride the edge";
                case "teleporter" -> engaged() ? "Variety %s in 30 s: new skill +%s" : "Variety: try different skills for +50%%";
                default -> engaged() ? "Flow reading: next cast +%s" : "Flow reading: idle, move fast to learn";
            };
        }
        /** The %s arguments of {@link #fallback()}, in order. */
        public List<String> arguments() {
            if (!engaged()) return List.of();
            String bonus = percent + "%";
            return category.equals("electromaster") || category.equals("teleporter") ? List.of(Integer.toString(count), bonus) : List.of(bonus);
        }
    }
}

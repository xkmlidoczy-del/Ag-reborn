package cn.academy.port.highpower;

import java.math.BigDecimal;
import java.util.UUID;

/** Read-only skill-CP presentation. No client state here can spend, reserve or refill resources. */
public final class SkillBudgetHudModel {
    public static final int STALE_TICKS = 45;
    private SkillBudgetHudModel() {}
    /** Current usable expansion skills expose this wallet, except the audited zero-CP grounding stance. */
    public static boolean currentPresetUsesBudget(cn.academy.port.core.AbilityProgress state) {
        if (state == null) return false;
        for (int slot = 0; slot < cn.academy.port.core.SkillPresets.MAX_KEYS; slot++) {
            String id = state.presets.currentSkill(slot);
            // Canon skills spend the same high-power CP wallet as the classic expansion skills.
            if ((HighPowerSkills.contains(id) || cn.academy.port.canon.CanonSkills.IDS.contains(id)) && !HighPowerSkills.GROUNDING_RETURN.equals(id)
                    && cn.academy.port.preset.PresetSkills.mappedUsable(state, id)
                    && cn.academy.port.SkillCatalog.enabled(state.category, id)) return true;
        }
        return false;
    }
    public record Values(String category, double available, double total, double maximum, double held) {
        public Values {
            if (!HighPowerRecoveryHudModel.hudCategory(category) || !finite(available) || !finite(total) || !finite(maximum) || !finite(held)
                    || maximum <= 0 || total > maximum || available > total || held > total || available + held > total)
                throw new IllegalArgumentException("Invalid skill CP presentation");
        }
        private static boolean finite(double value) { return Double.isFinite(value) && value >= 0 && value <= 1e20
                && (value == 0 || BigDecimal.valueOf(value).stripTrailingZeros().scale() <= cn.academy.port.highpower.core.CombatAmounts.MAX_ABSOLUTE_SCALE); }
        public String availableText() { return text(available); }
        public String totalText() { return text(total); }
        public String maximumText() { return text(maximum); }
        public String heldText() { return text(held); }
        /** Available CP already excludes both short reservations and long control holds. */
        public String reservedText() { return BigDecimal.valueOf(total).subtract(BigDecimal.valueOf(available)).max(BigDecimal.ZERO).stripTrailingZeros().toPlainString(); }
        public boolean reserved() { return available < total; }
        private static String text(double value) { return value == 0 ? "0" : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    }
    /** Presentation-only rounding fence: decimal control holds can round one binary ULP above the stored total. */
    public static double displayAvailable(double available, double total, double held) {
        if (!Double.isFinite(available) || !Double.isFinite(total) || !Double.isFinite(held) || available < 0 || total < 0 || held < 0) return available;
        double sum = available + held;
        if (sum > total && sum <= Math.nextUp(total)) {
            // Stepping available by its own ULP is insufficient when held is much larger.
            // One step below the subtraction bounds the real sum without increasing any displayed balance.
            available = Math.min(available, Math.max(0, Math.nextDown(total - held)));
        }
        return available;
    }
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
        public boolean accept(HighPowerRecoveryHudModel.Identity identity, Values next, UUID owner, int entity, String dimension) {
            if (connection == null || world == null || player == null || !identity.owner().equals(owner)
                    || identity.entity() != entity || !identity.dimension().equals(dimension)
                    || identity.epoch() <= retiredEpoch || identity.epoch() < epoch
                    || identity.epoch() == epoch && identity.sequence() <= sequence) return false;
            epoch = identity.epoch(); sequence = identity.sequence(); values = next; age = 0; return true;
        }
        public void tick(boolean paused) { if (!paused && age < STALE_TICKS) age++; }
        public Values current(String category) { return age < STALE_TICKS && values != null && values.category().equals(category) ? values : null; }
    }
}

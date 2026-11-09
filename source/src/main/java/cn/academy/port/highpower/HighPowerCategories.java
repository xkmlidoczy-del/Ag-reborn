package cn.academy.port.highpower;

import cn.academy.port.core.AbilityProgress;

/** Explicit shared-ledger categories. Other classic categories receive no high-power benefits. */
public final class HighPowerCategories {
    public static final String ELECTROMASTER = "electromaster";
    public static final String MELTDOWNER = "meltdowner";
    public static final String TELEPORTER = "teleporter";
    public static final String VECMANIP = "vecmanip";

    private HighPowerCategories() {}

    public static boolean isElectromaster(String category) { return ELECTROMASTER.equals(category); }
    public static boolean isMeltdowner(String category) { return MELTDOWNER.equals(category); }
    public static boolean isTeleporter(String category) { return TELEPORTER.equals(category); }
    public static boolean isVector(String category) { return VECMANIP.equals(category); }
    public static boolean enabled(String category) { return isElectromaster(category) || isMeltdowner(category) || isTeleporter(category) || isVector(category) || cn.academy.port.canon.CanonSkills.activeCategory(category); }
    public static boolean active(AbilityProgress state) {
        return state != null && enabled(state.category) && state.level >= 1 && state.level <= 5;
    }
    /** Eligibility for the food-only proportional extension is tied to the category's learned root. */
    public static String recoveryRoot(String category) {
        return isElectromaster(category) ? HighPowerSkills.FORKED_ARC
                : isMeltdowner(category) ? HighPowerSkills.DUAL_POINT
                : isTeleporter(category) ? HighPowerSkills.SAFE_LANDING_PROBE
                : isVector(category) ? HighPowerSkills.DEFLECTION_STRIKE : cn.academy.port.canon.CanonSkills.root(category);
    }
}

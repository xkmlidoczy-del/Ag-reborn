package cn.academy.port.highpower;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The one reviewed optional physics bundle for V-ADV-01: Sable 2.0.6 with the companion and Veil versions it bundles. It adds
 * rigid-body sub-levels and rendering only; ACA's exact damage and mining contracts were re-run with it installed. Any other
 * version, or any additional mod (Create, Aeronautics, ...), still needs its own review and is refused.
 */
public final class PhysicsBundle {
    public static final Map<String, String> MODS = Map.of("sable", "2.0.6", "sablecompanion", "1.6.0", "veil", "4.3.2");
    public static final Set<String> PACKS = Set.of("mod/sable", "mod/sablecompanion", "mod/veil");

    private PhysicsBundle() {}

    /** True only for exactly the reviewed base set plus exactly this bundle. */
    public static boolean exact(Map<String, String> base, Map<String, String> mods) {
        if (base == null || mods == null) return false;
        Map<String, String> expected = new HashMap<>(base);
        expected.putAll(MODS);
        return expected.equals(mods);
    }
}

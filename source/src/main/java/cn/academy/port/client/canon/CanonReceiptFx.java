package cn.academy.port.client.canon;

import cn.academy.port.VisualTierConfig;
import java.util.Set;

/** These enhanced presentations use the accepted receipt in place of the legacy inferred decorations. */
final class CanonReceiptFx {
    private static final Set<String> PAPER_PLACEMENTS = Set.of("paper_ladder", "paper_gate", "paper_safety_rail",
            "paper_platform", "paper_puzzle_link", "paper_catwalk", "paper_emergency_stairs", "paper_switch_bridge");

    private CanonReceiptFx() {}

    static boolean replacesLegacy(String skill) {
        return VisualTierConfig.tier().enhanced() && ("aero_barrage".equals(skill) || "paper_array".equals(skill)
                || CanonFxMental.targetPointField(skill) || PAPER_PLACEMENTS.contains(skill));
    }

    static boolean paperPlacement(String skill) {
        return PAPER_PLACEMENTS.contains(skill) && VisualTierConfig.tier().enhanced();
    }
}

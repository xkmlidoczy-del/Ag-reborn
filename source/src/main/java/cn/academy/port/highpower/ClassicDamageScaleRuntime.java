package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.highpower.core.ClassicDamageScale;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Scales the damage of classic skills of an enrolled esper onto the level unit through the existing mutable {@link SkillAttackEvent}, so no
 * classic skill or the classic damage entry point is touched. High-power skills (own budgets, never amplified) and unenrolled casters are skipped.
 */
@EventBusSubscriber(modid = "academy")
public final class ClassicDamageScaleRuntime {
    private ClassicDamageScaleRuntime() {}

    /** Only source-catalogued classic identities own the original-number-to-D conversion.
     * Closed controllers may use private event suffixes which are deliberately not learnable skill ids.
     */
    static boolean isClassicSkill(String category, String id) {
        return SkillCatalog.ALL.stream().anyMatch(skill -> skill.category().equals(category) && skill.id().equals(id));
    }

    @SubscribeEvent
    public static void attack(SkillAttackEvent event) {
        if (EsperLegacyBypass.standAside() || event.player == null || !event.player.server.isSameThread() || !EsperBalanceConfig.scaleClassic()
                || !HighPowerCombat.enabled(event.player) || event.skill == null) return;
        int separator = event.skill.indexOf('.');
        if (separator <= 0) return;
        String category = event.skill.substring(0, separator), id = event.skill.substring(separator + 1);
        var state = AbilityStorage.get(event.player);
        // Only the classic skills of the caster's own admitted category; every high-power skill id keeps its closed budget.
        if (!HighPowerCategories.active(state) || !category.equals(state.category) || !isClassicSkill(category, id)) return;
        try {
            double scaled = ClassicDamageScale.scaled(event.amount, HighPowerGrowth.values(event.player).damage(), EsperBalanceConfig.divisor());
            if (Double.isFinite(scaled) && scaled > 0) event.amount = scaled;
        } catch (IllegalStateException | IllegalArgumentException unavailable) { /* no admitted growth: classic amount unchanged */ }
    }
}

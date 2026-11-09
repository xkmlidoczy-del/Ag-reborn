/* Adapted from classic DevelopActionSkill/Level/Reset. See NOTICE. */
package cn.academy.port.develop;

import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

public final class DevelopmentActions {
    public static final List<String> CATEGORIES = java.util.stream.Stream.concat(List.of("electromaster", "meltdowner", "teleporter", "vecmanip").stream(),cn.academy.port.canon.CanonSkills.CATEGORIES.stream()).toList();
    private DevelopmentActions() {}

    /** Inventory hook for induction factors and a held magnetic coil, not a claim those items are ported. */
    public interface CategoryItems {
        Optional<String> differentFactor(String currentCategory);
        boolean hasHeldMagneticCoil();
        /** Source clears the entire factor inventory slot, not one item in its stack. */
        void consumeFactor(String category);
        /** Source clears the entire held magnetic-coil slot. */
        void consumeHeldMagneticCoil();

        CategoryItems NONE = new CategoryItems() {
            public Optional<String> differentFactor(String category) { return Optional.empty(); }
            public boolean hasHeldMagneticCoil() { return false; }
            public void consumeFactor(String category) { throw new IllegalStateException("No factor item adapter installed"); }
            public void consumeHeldMagneticCoil() { throw new IllegalStateException("No magnetic-coil item adapter installed"); }
        };
    }

    private static Optional<String> factor(AbilityProgress state, CategoryItems items) {
        return items.differentFactor(state.category).filter(CATEGORIES::contains).filter(cat -> !cat.equals(state.category));
    }

    public static boolean canLearn(AbilityProgress state, DeveloperType developer, SkillCatalog.Skill skill) {
        // Duplicate learning is intentionally left idempotent at completion, as in source LearningHelper.
        return (!cn.academy.port.canon.CanonSkills.knownSkill(skill.id()) || cn.academy.port.canon.CanonSkills.EXPERIMENTAL)
                && (!cn.academy.port.highpower.HighPowerSkills.contains(skill.id()) || SkillCatalog.enabled(skill.category(), skill.id()))
                && state.category.equals(skill.category()) && state.level >= skill.level()
                && developer.supportsSkill(skill.level())
                && SkillCatalog.requirements(skill).stream().allMatch(req -> state.learned(req.id()) && (float)state.exp(req.id()) >= (float)req.exp())
                && (skill.anyLearnedSkillLevel() == 0 || SkillCatalog.ALL.stream().anyMatch(other ->
                other.category().equals(state.category) && other.level() == skill.anyLearnedSkillLevel() && state.learned(other.id())));
    }

    public static DevelopmentProcess.Action skill(AbilityProgress state, SkillCatalog.Skill skill) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(skill);
        return new DevelopmentProcess.Action() {
            public String id() { return "skill:" + skill.id(); }
            public int stimulations() { return ClassicRules.learningStimulations(skill.level()); }
            public boolean validate(DevelopmentProcess.Developer developer) { return canLearn(state, developer.type(), skill); }
            public void complete() { state.learn(skill.id()); }
        };
    }

    /** Like LearningHelper, level/category acquisition has no minimum machine tier. */
    public static boolean canLevelUp(AbilityProgress state) {
        return !state.hasCategory() || (!cn.academy.port.canon.CanonSkills.knownCategory(state.category) || cn.academy.port.canon.CanonSkills.EXPERIMENTAL) && state.canLevelUp(SkillCatalog.levelSkillCount(state));
    }

    public static DevelopmentProcess.Action level(AbilityProgress state, CategoryItems items, IntUnaryOperator randomIndex) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(items);
        Objects.requireNonNull(randomIndex);
        return new DevelopmentProcess.Action() {
            public String id() { return "level"; }
            public int stimulations() { return 5 * (state.level + 1); }
            public boolean validate(DevelopmentProcess.Developer developer) { return canLevelUp(state); }
            public void complete() {
                if (state.hasCategory()) {
                    state.setLevel(state.level + 1);
                } else {
                    Optional<String> factor = factor(state, items);
                    String category;
                    if (factor.isPresent()) {
                        category = factor.get();
                        items.consumeFactor(category);
                    } else {
                        int index = randomIndex.applyAsInt(CATEGORIES.size());
                        if (index < 0 || index >= CATEGORIES.size()) throw new IllegalArgumentException("invalid category random index");
                        category = CATEGORIES.get(index);
                    }
                    // Source AbilityData.setCategory promotes an uncategorized level-0 player to level 1.
                    state.changeCategoryClassic(category);
                }
            }
        };
    }

    public static boolean canReset(AbilityProgress state, DeveloperType developer, CategoryItems items) {
        return state.level >= 3 && developer == DeveloperType.ADVANCED
                && items.hasHeldMagneticCoil() && factor(state, items).isPresent();
    }

    public static DevelopmentProcess.Action reset(AbilityProgress state, CategoryItems items) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(items);
        return new DevelopmentProcess.Action() {
            public String id() { return "reset"; }
            public int stimulations() { return state.level * 10; }
            public boolean validate(DevelopmentProcess.Developer developer) { return canReset(state, developer.type(), items); }
            public void complete() {
                String category = factor(state, items).orElseThrow();
                int level = state.level;
                state.changeCategoryClassic(category);
                state.setLevel(level - 1);
                items.consumeHeldMagneticCoil();
                items.consumeFactor(category);
            }
        };
    }
}

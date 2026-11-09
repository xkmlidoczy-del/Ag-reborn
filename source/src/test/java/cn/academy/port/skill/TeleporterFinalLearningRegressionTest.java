package cn.academy.port.skill;

import cn.academy.port.SkillAvailability;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.preset.PresetSkills;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Real common metadata/implemented gates and finite developer learning, without native world bootstrap. */
public final class TeleporterFinalLearningRegressionTest {
    private static int checks;
    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
    private static void near(double actual, double expected, String why) {
        check(Math.abs(actual - expected) < 1e-7, why + ": " + actual + " != " + expected);
    }

    private static final class Battery implements DevelopmentProcess.Developer {
        private final DeveloperType type;
        private double energy;
        private Battery(DeveloperType type, double energy) {this.type = type; this.energy = energy;}
        public DeveloperType type() {return type;}
        public boolean tryPullEnergy(double amount) {
            double removed = Math.min(energy, amount);
            energy -= removed;
            return removed == amount;
        }
        public double energy() {return energy;}
        public double maxEnergy() {return type.energy;}
    }

    private static AbilityProgress prerequisites(SkillCatalog.Skill skill) {
        var state = new AbilityProgress();
        state.selectCategory("teleporter");
        state.setLevel(skill.level());
        for (var requirement : skill.requirements()) {
            state.learn(requirement.id());
            state.experience.put(requirement.id(), requirement.exp());
        }
        return state;
    }

    private static Map<String, Double> originalRequirements(String source, String originalName) {
        var names = Map.of("locTP", "location_teleport", "shiftTP", "shift_tp", "markTP", "mark_teleport", "penetrateTP", "penetrate_teleport");
        var matcher = Pattern.compile(Pattern.quote(originalName) + "\\.(?:setParent|addSkillDep)\\((\\w+), ([.0-9]+)f\\)").matcher(source);
        Map<String, Double> result = new LinkedHashMap<>();
        while (matcher.find()) {
            String id = names.get(matcher.group(1));
            if (id == null) throw new AssertionError("Unresolved original prerequisite " + matcher.group(1));
            result.put(id, Double.parseDouble(matcher.group(2)));
        }
        if (result.isEmpty()) throw new AssertionError("No original prerequisites " + originalName);
        return Map.copyOf(result);
    }

    public static void main(String[] args) {
        check(TeleporterFinalSourceFixtures.verifyAll() == 44, "Exact original source bundle");
        String category = TeleporterFinalSourceFixtures.academy("src/main/java/cn/academy/vanilla/teleporter/CatTeleporter.java");
        TeleporterFinalSourceFixtures.contains(TeleporterFinalSourceFixtures.academy("src/main/java/cn/academy/ability/api/Skill.java"), "return (int) (3 + level * level * 0.5f);");
        String originalDevelop = TeleporterFinalSourceFixtures.academy("src/main/java/cn/academy/ability/develop/DevelopData.java");
        TeleporterFinalSourceFixtures.contains(originalDevelop, "double consume = devType.getCPS() / devType.getTPS();");
        TeleporterFinalSourceFixtures.contains(originalDevelop, "if(++tickThisStim > devType.getTPS())");
        TeleporterFinalSourceFixtures.contains(originalDevelop, "boolean success = type.validate(player, developer);");
        TeleporterFinalSourceFixtures.contains(TeleporterFinalSourceFixtures.academy("src/main/java/cn/academy/ability/develop/condition/DevConditionDep.java"), "data.getSkillExp(dependency) >= requiredExp");
        checks += 5;
        String originalTiers = TeleporterFinalSourceFixtures.academy("src/main/java/cn/academy/ability/develop/DeveloperType.java");
        for (DeveloperType tier : DeveloperType.values()) {
            var values = Pattern.compile(tier.name() + "\\s*\\(IFConstants\\.\\w+,\\s*([.0-9]+),\\s*([.0-9]+),\\s*([0-9]+),\\s*([.0-9]+),").matcher(originalTiers);
            if (!values.find()) throw new AssertionError("Original developer constants " + tier);
            near(tier.syncRate, Double.parseDouble(values.group(1)), "Original developer sync rate " + tier);
            near(tier.energy, Double.parseDouble(values.group(2)), "Original finite IF capacity " + tier);
            check(tier.tps == Integer.parseInt(values.group(3)), "Original developer tick constant " + tier);
            near(tier.cps, Double.parseDouble(values.group(4)), "Original developer IF per stimulation " + tier);
        }
        var ids = List.of(TeleporterFinalRules.LOCATION, TeleporterFinalRules.SHIFT, TeleporterFinalRules.FLASH);
        var originalNames = List.of("locTP", "shiftTP", "flashing");
        int[] levels = {3, 4, 5}, expectedTicks = {147, 176, 240};
        DeveloperType[] tiers = {DeveloperType.NORMAL, DeveloperType.ADVANCED, DeveloperType.ADVANCED};
        check(!SkillAvailability.learnable(null) && !SkillAvailability.learnable("not_an_implemented_skill"), "Registry rejects unknown/null IDs");
        for (int index = 0; index < ids.size(); index++) {
            String id = ids.get(index);
            check(SkillAvailability.learnable(id) && PresetSkills.IMPLEMENTED.contains(id), "Final skill admitted by real implemented registry " + id);
            check(!SkillAvailability.PASSIVES.contains(id), "Final skill is active gameplay " + id);
            var skill = SkillCatalog.find("teleporter", id).orElseThrow();
            check(skill.level() == levels[index] && skill.controllable(), "Original level and controllable metadata " + id);
            Map<String, Double> metadataRequirements = new LinkedHashMap<>();
            for (var requirement : skill.requirements()) metadataRequirements.put(requirement.id(), requirement.exp());
            check(metadataRequirements.equals(originalRequirements(category, originalNames.get(index))), "Exact original category prerequisites " + id);
            var state = prerequisites(skill);
            var tier = DeveloperType.minimumForSkill(skill.level());
            check(tier == tiers[index], "Original minimum real developer tier " + id);
            check(!state.learned(id) && state.exp(id) == 0 && !PresetSkills.selectable(state, id), "No pre-grant of target skill " + id);
            check(DevelopmentActions.canLearn(state, tier, skill) && SkillCatalog.canLearn(state, skill), "Canonical exact-threshold eligibility " + id);
            for (DeveloperType lower : DeveloperType.values()) {
                if (lower.ordinal() < tier.ordinal()) check(!DevelopmentActions.canLearn(state, lower, skill), "Lower developer rejects " + id);
            }
            for (var requirement : skill.requirements()) {
                double original = state.exp(requirement.id());
                state.experience.put(requirement.id(), Math.nextDown(original));
                check(DevelopmentActions.canLearn(state, tier, skill) && SkillCatalog.canLearn(state, skill), "Double-only prerequisite predecessor rounds to exact source float and accepts " + id);
                state.experience.put(requirement.id(), (double)Math.nextDown((float)original));
                check(!DevelopmentActions.canLearn(state, tier, skill), "Each prerequisite threshold enforced " + id);
                state.experience.remove(requirement.id());
                check(!DevelopmentActions.canLearn(state, tier, skill), "Each prerequisite must genuinely be learned " + id);
                state.learn(requirement.id());
                state.experience.put(requirement.id(), original);
            }
            state.level--;
            check(!DevelopmentActions.canLearn(state, tier, skill), "Player level gate " + id);
            state.level++;
            state.category = "meltdowner";
            check(!DevelopmentActions.canLearn(state, tier, skill), "Player category gate " + id);
            state.category = "teleporter";

            var process = new DevelopmentProcess();
            var battery = new Battery(tier, tier.energy);
            process.start(battery, DevelopmentActions.skill(state, skill));
            int ticks = ClassicRules.learningStimulations(skill.level()) * tier.ticksPerStimulation();
            check(ticks == expectedTicks[index], "Exact original TPS+1 learning schedule " + id);
            for (int tick = 1; tick <= ticks; tick++) {
                process.tick();
                if (tick < ticks) check(!state.learned(id), "No early target grant " + id);
            }
            check(process.state() == DevelopmentProcess.State.DONE && state.learned(id) && state.exp(id) == 0, "Finite learning earns zero-mastery target " + id);
            near(battery.energy, tier.energy - tier.actualConsumption(ClassicRules.learningStimulations(skill.level())), "Finite IF charged every genuine learning tick " + id);
            check(PresetSkills.selectable(state, id) && state.presets.edit(0, 0, id, value -> PresetSkills.selectable(state, value)), "Canonical preset editor accepts genuinely earned zero mastery " + id);
            check(!SkillCatalog.canLearn(state, skill), "Catalog rejects already learned target " + id);

            var poor = prerequisites(skill);
            var insufficient = new Battery(tier, 1);
            process.start(insufficient, DevelopmentActions.skill(poor, skill));
            process.tick();
            check(process.state() == DevelopmentProcess.State.FAILED && !poor.learned(id) && insufficient.energy == 0, "Finite-power failure drains remainder without granting " + id);
            var cancelled = prerequisites(skill);
            var cancelledBattery = new Battery(tier, tier.energy);
            process.start(cancelledBattery, DevelopmentActions.skill(cancelled, skill));
            process.tick();
            double spent = cancelledBattery.energy;
            process.abort();
            process.tick();
            check(process.state() == DevelopmentProcess.State.FAILED && !cancelled.learned(id) && cancelledBattery.energy == spent, "Abort neither grants nor refunds/continues consumption " + id);
            var stale = prerequisites(skill);
            var staleBattery = new Battery(tier, tier.energy);
            process.start(staleBattery, DevelopmentActions.skill(stale, skill));
            for (int tick = 1; tick < ticks; tick++) process.tick();
            stale.experience.remove(skill.requirements().getFirst().id());
            process.tick();
            check(process.state() == DevelopmentProcess.State.FAILED && !stale.learned(id), "Completion revalidates removed prerequisite " + id);
        }
        var unsupported = SkillCatalog.ALL.stream().filter(s -> !PresetSkills.IMPLEMENTED.contains(s.id()) && !SkillAvailability.PASSIVES.contains(s.id())).findFirst();
        if (unsupported.isPresent()) {
            check(!SkillAvailability.learnable(unsupported.orElseThrow().id()), "Unimplemented classic metadata stays blocked by common availability registry");
        } else {
            check(SkillCatalog.ALL.stream().filter(SkillCatalog.Skill::controllable).allMatch(s -> PresetSkills.IMPLEMENTED.contains(s.id()) && SkillAvailability.learnable(s.id())), "Every classic active skill is now accepted by the single shared registry");
        }
        check(!SkillAvailability.learnable("unknown_classic_skill"), "Unknown IDs remain rejected after the final source skill is integrated");
        System.out.println("PASS " + checks + " final Teleporter source dependencies, implemented gates, finite147/176/240-tick learning and zero-mastery preset checks");
    }
}

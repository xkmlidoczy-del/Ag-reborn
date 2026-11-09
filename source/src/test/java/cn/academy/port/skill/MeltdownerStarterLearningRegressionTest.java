package cn.academy.port.skill;

import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.preset.PresetSkills;

/** Genuine finite portable development, canonical metadata unchanged. */
public final class MeltdownerStarterLearningRegressionTest {
    private static int checks;
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    public static void main(String[] args) {
        check(SkillCatalog.ALL.size() == 50, "canonical 50-entry catalog unchanged");
        for (String id : java.util.List.of(ScatterBombSession.ID, LightShieldSession.ID)) {
            var skill = SkillCatalog.find("meltdowner", id).orElseThrow();
            double threshold = id.equals(ScatterBombSession.ID) ? .8 : 1;
            check(skill.controllable() && skill.level() == 2 && skill.anyLearnedSkillLevel() == 0, "canonical portable ACTIVE level2 skill");
            check(skill.requirements().equals(java.util.List.of(new SkillCatalog.Requirement("electron_bomb", threshold))), "exact classic mastered ElectronBomb prerequisite");
            var state = ready(); check(!SkillCatalog.canLearn(state, skill), "unlearned root blocks learning");
            state.learn("electron_bomb"); state.experience.put("electron_bomb", Math.nextDown(threshold));
            check(SkillCatalog.canLearn(state, skill), "double-only ElectronBomb predecessor rounds to exact source float and accepts");
            state.experience.put("electron_bomb", (double)Math.nextDown((float)threshold));
            check(!SkillCatalog.canLearn(state, skill), "just-below prerequisite mastery blocks learning");
            state.experience.put("electron_bomb", threshold); check(SkillCatalog.canLearn(state, skill), "exact mastery prerequisite unlocks");
            for (var type : DeveloperType.values()) check(DevelopmentActions.canLearn(state, type, skill), "portable and higher developers support genuinelevel2 learning");
            state.level = 1; check(!DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill), "level1 blocks learning"); state.level = 2;
            var action = DevelopmentActions.skill(state, skill); check(action.stimulations() == 5, "source5 stimulations");
            var battery = new Battery(10000); var process = new DevelopmentProcess(); process.start(battery, action);
            for (int tick = 1; tick <= 130; tick++) { process.tick(); if (tick < 130) check(!state.learned(id), "five26tick stimulations never finish early"); }
            check(process.state() == DevelopmentProcess.State.DONE && state.learned(id) && state.exp(id) == 0, "actual process learns zero-mastery skill");
            check(Math.abs(battery.energy - 6100) < 1E-8, "genuine finite3900IF drained, not nominal3750");
            check(state.presets.currentSkill(0).isEmpty(), "learning does not autobind preset"); state.activated = true;
            check(PresetSkills.selectable(state, id), "natural zero-mastery implemented skill can be bound");
            final var learnedState = state;
            check(state.presets.edit(0, 0, id, value -> PresetSkills.selectable(learnedState, value)), "natural real preset binds");
            check(id.equals(ScatterBombSession.ID) ? ScatterBombSession.mayStart(state) : LightShieldSession.mayStart(state), "naturally learned skill starts through authoritative gates");
            check(!SkillCatalog.canLearn(state, skill), "catalog duplicate learning rejected");
            state = ready(); state.learn("electron_bomb"); state.experience.put("electron_bomb", threshold);
            battery = new Battery(29); process = new DevelopmentProcess(); process.start(battery, DevelopmentActions.skill(state, skill)); process.tick();
            check(process.state() == DevelopmentProcess.State.FAILED && !state.learned(id) && battery.energy == 0, "finite depletion drains partial remainder without granting skill");
            battery = new Battery(10000); process = new DevelopmentProcess(); process.start(battery, DevelopmentActions.skill(state, skill));
            for (int tick = 0; tick < 129; tick++) process.tick(); state.experience.put("electron_bomb", 0D); process.tick();
            check(process.state() == DevelopmentProcess.State.FAILED && !state.learned(id), "prerequisite revalidated at completion");
        }
        check(PresetSkills.IMPLEMENTED.containsAll(java.util.List.of("arc_gen", "charging", "mag_movement", "mag_manip", "thunder_bolt", "mine_detect", "body_intensify", "thunder_clap", "railgun", "electron_bomb", "threatening_teleport", "dir_shock", "ground_shock", "scatter_bomb", "light_shield")), "all previous active paths preserved");
        System.out.println("PASS " + checks + " canonical prerequisite, finite portable energy and natural preset-learning checks");
    }
    private static AbilityProgress ready() { var s = new AbilityProgress(); s.selectCategory("meltdowner"); s.setLevel(2); return s; }
    private static final class Battery implements DevelopmentProcess.Developer {
        double energy; Battery(double energy) { this.energy = energy; }
        public DeveloperType type() { return DeveloperType.PORTABLE; }
        public boolean tryPullEnergy(double amount) { double before = energy; energy = Math.max(0, energy - amount); return before >= amount; }
        public double energy() { return energy; }
        public double maxEnergy() { return 10000; }
    }
}

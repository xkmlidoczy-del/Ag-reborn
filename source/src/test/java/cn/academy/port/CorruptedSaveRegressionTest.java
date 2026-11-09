package cn.academy.port;

import cn.academy.port.core.AbilityProgress;
import net.minecraft.nbt.CompoundTag;

/** Standalone NBT codec checks. This does not start Minecraft or construct a player/world. */
public final class CorruptedSaveRegressionTest {
    private static int assertions;

    private static void check(boolean value, String description) {
        assertions++;
        if (!value) throw new AssertionError(description);
    }

    private static CompoundTag fixture() {
        var state = new AbilityProgress();
        state.selectCategory("electromaster");
        state.setLevel(4);
        state.learn("railgun");
        state.learn("brain_course");
        state.activated = true;
        state.cp = 4321;
        state.overload = 123;
        state.extraCp = 71;
        state.extraOverload = 17;
        state.levelExperience = .25;
        state.cpDelay = 5;
        state.overloadDelay = 12;
        state.experience.put("railgun", .6);
        state.cooldowns.put("railgun", 40);
        return AbilityStorage.encode(state);
    }

    private static void bounded(AbilityProgress state, String label) {
        check(state.level >= 0 && state.level <= 5, label + " level");
        check(Double.isFinite(state.cp) && state.cp >= 0 && state.cp <= state.maxCp(), label + " CP");
        check(Double.isFinite(state.overload) && state.overload >= 0 && state.overload <= state.maxOverload(), label + " overload");
        check(Double.isFinite(state.extraCp) && state.extraCp >= 0, label + " CP training");
        check(Double.isFinite(state.extraOverload) && state.extraOverload >= 0, label + " overload training");
        check(Double.isFinite(state.levelExperience) && state.levelExperience >= 0 && state.levelExperience <= 10000, label + " level experience");
        check(state.cpDelay >= 0 && state.cpDelay <= 15, label + " CP recovery delay");
        check(state.overloadDelay >= 0 && state.overloadDelay <= 32, label + " overload recovery delay");
        check(state.experience.values().stream().allMatch(value -> Double.isFinite(value) && value >= 0 && value <= 1), label + " mastery");
        check(state.cooldowns.values().stream().allMatch(value -> value >= 0 && value <= 12000), label + " cooldown bounds");
        check(state.cooldowns.keySet().stream().allMatch(id->cn.academy.port.preset.PresetSkills.registeredMapping(state,id)), label + " cooldowns retain only registered category controllables");
        check(state.experience.keySet().stream().allMatch(id -> SkillCatalog.find(state.category, id).isPresent()), label + " known category skills only");
    }

    public static void main(String[] args) {
        bounded(AbilityStorage.decode(fixture()), "valid control");
        double[] corrupt = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                -Double.MAX_VALUE, -1, 0, Double.MAX_VALUE};
        String[] fields = {"cp", "overload", "extra_cp", "extra_overload", "level_exp"};
        for (String field : fields) {
            for (double value : corrupt) {
                var tag = fixture();
                tag.putDouble(field, value);
                bounded(AbilityStorage.decode(tag), field + "=" + value);
            }
        }
        for (int value : new int[]{Integer.MIN_VALUE, -1, 0, 1, 5, 6, Integer.MAX_VALUE}) {
            var tag = fixture();
            tag.putInt("level", value);
            tag.putInt("cp_delay", value);
            tag.putInt("overload_delay", value);
            tag.getCompound("cooldowns").putInt("railgun", value);
            bounded(AbilityStorage.decode(tag), "integer=" + value);
        }
        for (double value : corrupt) {
            var tag = fixture();
            tag.getCompound("skills").putDouble("railgun", value);
            bounded(AbilityStorage.decode(tag), "mastery=" + value);
        }

        var filtered = fixture();
        filtered.getCompound("skills").putDouble("not_a_skill", 1);
        filtered.getCompound("skills").putDouble("electron_bomb", 1);
        filtered.getCompound("cooldowns").putInt("not_a_skill", 1);
        filtered.getCompound("cooldowns").putInt("arc_gen", 1);
        var decoded = AbilityStorage.decode(filtered);
        check(!decoded.learned("not_a_skill") && !decoded.learned("electron_bomb"), "unknown and foreign-category skills removed");
        check(!decoded.cooldowns.containsKey("not_a_skill") && decoded.cooldowns.get("arc_gen")==1, "unknown cooldown removed; source unlearned registered controllable cooldown retained");
        bounded(decoded, "filtered keys");

        for (String category : new String[]{"", "invalid", "Electromaster", "../electromaster"}) {
            var tag = fixture();
            tag.putString("category", category);
            decoded = AbilityStorage.decode(tag);
            check(!decoded.hasCategory() && !decoded.canUse("railgun"), "invalid category cannot cast");
            check(decoded.experience.isEmpty() && decoded.cooldowns.isEmpty(), "invalid category drops skill state");
            bounded(decoded, "category=" + category);
        }

        var typed = fixture();
        for (String field : fields) typed.putString(field, "not a number");
        typed.putString("level", "not a number");
        typed.putString("cp_delay", "not a number");
        typed.putString("overload_delay", "not a number");
        typed.putString("skills", "not a compound");
        typed.putString("cooldowns", "not a compound");
        decoded = AbilityStorage.decode(typed);
        check(decoded.experience.isEmpty() && decoded.cooldowns.isEmpty(), "wrong compound types do not fabricate skills");
        bounded(decoded, "wrong NBT types");

        var noSchema = fixture();
        noSchema.remove("schema");
        decoded = AbilityStorage.decode(noSchema);
        check(!decoded.hasCategory() && !decoded.activated && decoded.overloadFine, "missing schema is fresh inactive state");
        check(decoded.cp == 0 && decoded.baseCp()==100 && decoded.baseOverload()==100 && decoded.experience.isEmpty() && decoded.cooldowns.isEmpty(), "missing schema drops unrecognized saved fields");
        bounded(decoded, "missing schema");

        var roundTrip = AbilityStorage.decode(AbilityStorage.encode(AbilityStorage.decode(filtered)));
        bounded(roundTrip, "sanitized round trip");
        check(roundTrip.experience.equals(AbilityStorage.decode(filtered).experience), "sanitized mastery round trip");
        check(roundTrip.cooldowns.equals(AbilityStorage.decode(filtered).cooldowns), "sanitized cooldown round trip");
        System.out.println("PASS " + assertions + " corrupted-save codec regression assertions (no Minecraft launch)");
    }
}

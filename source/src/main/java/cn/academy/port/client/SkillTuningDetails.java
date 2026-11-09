package cn.academy.port.client;

import cn.academy.port.core.RangedSkillRanges;
import cn.academy.port.core.SkillTuning;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Formats gameplay parameters with locale-specific labels and consistent units. */
final class SkillTuningDetails {
    private static final BigDecimal TICKS_PER_SECOND = BigDecimal.valueOf(20);

    private SkillTuningDetails() {}

    static String append(String description, String skill, double mastery, boolean mentalRemote) {
        var fields = SkillTuning.fields(skill);
        String prose = SkillTuning.format(skill, description);
        List<String> lines = new ArrayList<>();
        var aiming = RangedSkillRanges.find(skill);
        if (aiming != null) {
            double reach = mentalRemote ? RangedSkillRanges.withMentalRemote(skill) : aiming.at(mastery);
            lines.add(Component.translatable("ac.skill.remote_reach",
                    decimal(BigDecimal.valueOf(reach).setScale(2, RoundingMode.HALF_UP))).getString());
        }
        for (String key : SkillTuning.displayFields(skill)) {
            if (key.equals("reach") && aiming != null) continue;
            if (description.contains("{{" + key + "}}") || description.contains("{{" + key + ":s}}")) continue;
            var value = fields.get(key);
            if (value != null) {
                lines.add(label(key, formatted(key, value)));
                continue;
            }
            var novice = fields.get(key + "_novice");
            var mastered = fields.get(key + "_mastered");
            if (novice != null && mastered != null
                    && !description.contains("{{" + key + "_novice}}")) {
                String span = Component.translatable("ac.skill.tuning.mastery_span",
                        formatted(key, novice), formatted(key, mastered)).getString();
                lines.add(label(key, span));
            }
        }
        return lines.isEmpty() ? prose : prose + "\n\n" + String.join("\n", lines);
    }

    private static String label(String key, String value) {
        return Component.translatable("ac.skill.tuning." + key, value).getString();
    }

    private static String formatted(String key, BigDecimal value) {
        return decimal(key.endsWith("_ticks") ? value.divide(TICKS_PER_SECOND) : value);
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}

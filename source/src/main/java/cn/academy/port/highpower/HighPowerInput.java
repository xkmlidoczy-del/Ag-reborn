package cn.academy.port.highpower;

import cn.academy.port.core.SkillPresets;

/** Bounded wire identity; no client-provided skill name, power level, battery or damage amount. */
public final class HighPowerInput {
    public record Input(int slot, long input, int actor, long epoch) {
        public String wire() { return slot + ":" + input + ":" + actor + ":" + epoch; }
    }
    private HighPowerInput() {}
    public static Input parse(String text) {
        if (text == null || text.length() > 64) return null;
        String[] fields = text.split(":", -1);
        if (fields.length != 4) return null;
        try {
            var value = new Input(Integer.parseInt(fields[0]), Long.parseLong(fields[1]),
                    Integer.parseInt(fields[2]), Long.parseLong(fields[3]));
            return SkillPresets.validSlot(value.slot) && value.input > 0 && value.actor >= 0 && value.epoch > 0
                    && value.wire().equals(text) ? value : null;
        } catch (NumberFormatException invalid) { return null; }
    }
}

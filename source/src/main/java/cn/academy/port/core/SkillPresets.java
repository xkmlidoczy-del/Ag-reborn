/* AcademyCraft 1.0.7 PresetData semantics, validated for modern server ownership. See NOTICE. */
package cn.academy.port.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.function.Predicate;

/** Four independently editable mappings. Browsing an editor never selects a gameplay preset. */
public final class SkillPresets {
    public static final int MAX_PRESETS = 4;
    public static final int MAX_KEYS = 4;
    private final String[][] mappings = new String[MAX_PRESETS][MAX_KEYS];
    private int current;
    private long revision;

    public SkillPresets() { for (var preset : mappings) Arrays.fill(preset, ""); }
    public static boolean validPreset(int id) { return id >= 0 && id < MAX_PRESETS; }
    public static boolean validSlot(int id) { return id >= 0 && id < MAX_KEYS; }
    public int current() { return current; }
    public long revision() { return revision; }
    public String skill(int preset, int slot) {
        return validPreset(preset) && validSlot(slot) ? mappings[preset][slot] : "";
    }
    public String currentSkill(int slot) { return skill(current, slot); }
    public boolean contains(int preset, String skill) {
        return validPreset(preset) && skill != null && !skill.isEmpty()
                && Arrays.asList(mappings[preset]).contains(skill);
    }
    public boolean currentContains(String skill) { return contains(current, skill); }
    public String[] copy(int preset) {
        return validPreset(preset) ? mappings[preset].clone() : new String[]{"", "", "", ""};
    }
    public boolean switchTo(int id) {
        if (!validPreset(id)) return false;
        current = id; changed(); return true;
    }
    public boolean edit(int preset, int slot, String skill, Predicate<String> selectable) {
        if (!validPreset(preset) || !validSlot(slot) || skill == null || selectable == null) return false;
        var next = copy(preset); next[slot] = skill;
        return replace(preset, next, selectable);
    }
    /** Reject an invalid edit atomically; never silently learn, move or replace another slot. */
    public boolean replace(int preset, String[] skills, Predicate<String> selectable) {
        if (!validPreset(preset) || skills == null || skills.length != MAX_KEYS || selectable == null) return false;
        var seen = new HashSet<String>();
        for (var skill : skills) {
            if (skill == null || skill.length() > 48 || (!skill.isEmpty() && (!selectable.test(skill) || !seen.add(skill)))) return false;
        }
        System.arraycopy(skills, 0, mappings[preset], 0, MAX_KEYS); changed(); return true;
    }
    /** Classic category-change clear keeps the selected index and removes all mappings. */
    public void clear() { for (var preset : mappings) Arrays.fill(preset, ""); changed(); }
    /** Save loading is a separate recovery path: invalid values are dropped, first duplicate wins. */
    public void restore(int selected, long savedRevision, String[][] saved, Predicate<String> selectable) {
        current = validPreset(selected) ? selected : 0;
        revision = Math.max(0, savedRevision);
        for (int preset = 0; preset < MAX_PRESETS; preset++) {
            Arrays.fill(mappings[preset], "");
            var seen = new HashSet<String>();
            for (int slot = 0; slot < MAX_KEYS; slot++) {
                String value = saved != null && preset < saved.length && saved[preset] != null && slot < saved[preset].length
                        ? saved[preset][slot] : "";
                if (value != null && !value.isEmpty() && value.length() <= 48 && selectable.test(value) && seen.add(value)) mappings[preset][slot] = value;
            }
        }
    }
    private void changed() { if (revision < Long.MAX_VALUE) revision++; }
}

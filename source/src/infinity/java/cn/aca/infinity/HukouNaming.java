package cn.aca.infinity;

import net.minecraft.world.entity.Entity;

public final class HukouNaming {
    private HukouNaming() {}

    public record Pair(String prefix, String father, String mother) {}

    public static Pair inspect(Entity target) {
        String raw = target.getDisplayName().getString();
        if (raw == null || raw.isBlank()) {
            raw = target.getType().getDescription().getString();
        }
        String prefix = extractPrefix(raw);
        return new Pair(prefix, prefix + "大勇", prefix + "翠花");
    }

    public static String extractPrefix(String rawName) {
        if (rawName == null) return "某";
        // Strip Minecraft formatting codes (§x)
        String cleaned = rawName.replaceAll("§.", "").trim();
        // Strip leading bracketed prefixes like [LV4] or 【Boss】 if followed by actual name
        cleaned = cleaned.replaceAll("^(?:\\[[^\\]]*\\]|【[^】]*】|\\([^)]*\\)|（[^）]*）|\\s)+", "").trim();
        if (cleaned.isEmpty()) {
            cleaned = rawName.replaceAll("§.", "").trim();
        }
        for (int i = 0; i < cleaned.length(); ) {
            int cp = cleaned.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) {
                return new String(Character.toChars(cp));
            }
            i += Character.charCount(cp);
        }
        return "某";
    }
}

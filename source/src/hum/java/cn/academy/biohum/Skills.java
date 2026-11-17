package cn.academy.biohum;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** The 0.4.1 profiles retain their original slot order for save and command migration. */
public final class Skills {
    public enum Action {
        LEGACY, RAIL, RAY, PLASMA, DISRUPT, WIRE, BLADE, SHOT, RELOAD, BLINK, CARRY,
        ELECTRIC, TRACK, NETWORK, FEINT, STRIKE, DEVICE, ANALYZE, REPORT, ROUTE, SUPPLY,
        HEAL, SHIELD, FORMATION, GRAPPLE, FLY, RESTORE, DEEP_BLOOD, MIND, SEAL,
        SUMMON, FORGE, ARS_MAGNA, DIVINE, PRECEDENCE, SAINT_BREAK, MERCY, HOLY_RIGHT,
        SLICE, TERRITORY, PRESSURE, BANISH, RETURN, UPHEAVE, WONDERLAND, REWRITE,
        RITUAL, REFRACT, ARMOR, COPY, WINGS, DECOY, COLD, TRIPLE, SHADE, AWAKEN,
        THERMAL, CROSS_ALIGN, CROSS, GUNGNIR, IRON_COLOSSUS, MANIFEST, VECTOR_THROW, VOICE, SKY_STRIKE, PHASE_WORLD, SPECIAL, CLOSE
    }

    public enum Target { ENEMY, LIVING, ALLY, POINT, SELF, OPTIONAL }
    public enum Shape { BEAM, ARC, IMPACT, FIELD, SIGIL, TRAIL, GUARD, MARK }

    public record Skill(String id, String actor, String label, Action action, Target target, Shape shape,
                        double range, double power, double radius, int duration, int windup, int recovery,
                        int cooldown, int tint, boolean signature, boolean decision, boolean combat,
                        String prop, String evidence, String source, String condition, String outcome,
                        String counter) {
        public int length() { return windup + recovery; }
    }

    public record Phase(String actor, String id, String label, float scale, int batch, List<String> skills) {}
    private record Book(List<Skill> skills, List<Phase> phases, List<Skill> reactions) {}

    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    private static final Map<String, Skill> ENTRIES = new LinkedHashMap<>();
    private static final Map<String, Skill> REACTIONS = new LinkedHashMap<>();
    private static final Map<String, Map<String, Phase>> PHASES = new LinkedHashMap<>();

    static {
        try (var input = Skills.class.getResourceAsStream("/assets/acabiohum/skills.json")) {
            if (input == null) throw new IOException("Missing skills.json");
            var book = new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), Book.class);
            for (var move : book.skills) {
                if (move.id == null || !ID.matcher(move.id).matches() || move.action == null || move.target == null
                        || move.shape == null || !Double.isFinite(move.range) || move.range < 0
                        || !Double.isFinite(move.radius) || move.radius < 0 || !Double.isFinite(move.power)
                        || move.power < 0 || move.power > Float.MAX_VALUE || move.windup < 0 || move.recovery < 1
                        || move.cooldown < 0 || move.duration < 0 || ENTRIES.putIfAbsent(move.id, move) != null) {
                    throw new IOException("Invalid or duplicate skill " + move.id);
                }
            }
            if (book.reactions != null) {
                for (var move : book.reactions) {
                    if (move.action != Action.CLOSE || ENTRIES.containsKey(move.id)
                            || REACTIONS.putIfAbsent(move.id, move) != null) {
                        throw new IOException("Invalid close reaction " + move.id);
                    }
                }
            }
            for (var phase : book.phases) {
                var own = PHASES.computeIfAbsent(phase.actor, key -> new LinkedHashMap<>());
                if (!ID.matcher(phase.id).matches() || phase.scale <= 0 || !Float.isFinite(phase.scale)
                        || phase.skills.isEmpty() || phase.skills.stream().distinct().count() != phase.skills.size()
                        || phase.skills.stream().anyMatch(id -> !ENTRIES.containsKey(id)
                            || !ENTRIES.get(id).actor.equals(phase.actor))
                        || own.putIfAbsent(phase.id, phase) != null) {
                    throw new IOException("Invalid or duplicate phase " + phase.id);
                }
            }
            for (var profile : Profiles.ALL.values()) {
                if (phase(profile.id(), profile.phase()) == null
                        || profile.abilities().stream().anyMatch(id -> !ENTRIES.containsKey(id))) {
                    throw new IOException("Missing skill set for " + profile.id());
                }
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static Skill get(String id) { return ENTRIES.getOrDefault(id, REACTIONS.get(id)); }
    public static List<Skill> all() { return List.copyOf(ENTRIES.values()); }
    public static List<Phase> phases(String actor) {
        return List.copyOf(PHASES.getOrDefault(actor, Map.of()).values());
    }
    public static Phase phase(String actor, String id) {
        return PHASES.getOrDefault(actor, Map.of()).get(id);
    }
    public static String legacy(Profiles.Profile profile, int slot) {
        return slot >= 0 && slot < profile.abilities().size() ? profile.abilities().get(slot) : "";
    }
    public static String actionId(Profiles.Profile profile, String action) {
        if (action.startsWith("cast/")) return action.substring("cast/".length());
        return switch (action) {
            case "skill0" -> legacy(profile, 0);
            case "skill1" -> legacy(profile, 1);
            case "skill2" -> legacy(profile, 2);
            default -> "";
        };
    }

    private Skills() {}
}

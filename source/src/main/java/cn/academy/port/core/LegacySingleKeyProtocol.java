package cn.academy.port.core;

import java.util.IdentityHashMap;
import java.util.Map;

/** Fixed six-skill, phase-specific wire identities inside the unchanged 64-character request codec. */
public final class LegacySingleKeyProtocol {
    public static final String PRESS = "single_key_press", RELEASE = "single_key_release", ABORT = "single_key_abort";
    public static final String PENDING_RELEASE = "single_key_pending_release", PENDING_ABORT = "single_key_pending_abort";
    public enum Skill {
        CHARGING("charging", "c"), MAG_MOVEMENT("mag_movement", "mm"), MAG_MANIP("mag_manip", "ma"),
        GROUND_SHOCK("ground_shock", "gs"), DIRECTED_SHOCK("dir_shock", "ds"), THREATENING_TELEPORT("threatening_teleport", "tt");
        private final String id, alias;
        Skill(String id, String alias) { this.id = id; this.alias = alias; }
        public String id() { return id; }
        public String alias() { return alias; }
        /** Full IDs remain the internal mapping and callback identifiers. */
        public static Skill find(String id) {
            for (Skill skill : values()) if (skill.id.equals(id)) return skill;
            return null;
        }
        private static Skill fromAlias(String alias) {
            for (Skill skill : values()) if (skill.alias.equals(alias)) return skill;
            return null;
        }
    }
    public record StartRequest(int slot, Skill skill, long input, int actorId, long ownerEpoch) {
        public StartRequest {
            if (slot < 0 || slot >= 4 || !valid(skill, input, actorId, ownerEpoch))
                throw new IllegalArgumentException("Mapped current-owner positive-input start required");
        }
        public String wire() { return slot + ":" + identity(skill, input, actorId, ownerEpoch); }
    }
    public record PendingRequest(Skill skill, long input, int actorId, long ownerEpoch) {
        public PendingRequest {
            if (!valid(skill, input, actorId, ownerEpoch))
                throw new IllegalArgumentException("Current-owner positive pending input required");
        }
        public String wire() { return identity(skill, input, actorId, ownerEpoch); }
    }
    public record AcceptedRequest(Skill skill, long input, int actorId, long ownerEpoch, long token) {
        public AcceptedRequest {
            if (!valid(skill, input, actorId, ownerEpoch) || token <= 0)
                throw new IllegalArgumentException("Current-owner positive accepted input and token required");
        }
        public String wire() { return identity(skill, input, actorId, ownerEpoch) + ":" + numeral(token); }
    }
    public static StartRequest parseStart(String value) {
        String[] fields = fields(value, 5);
        if (fields == null || fields[0].length() != 1 || fields[0].charAt(0) < '0' || fields[0].charAt(0) > '3') return null;
        Skill skill = Skill.fromAlias(fields[1]);
        long input = positive(fields[2]), epoch = positive(fields[4]);
        int actorId = actor(fields[3]);
        return valid(skill, input, actorId, epoch) ? new StartRequest(fields[0].charAt(0) - '0', skill, input, actorId, epoch) : null;
    }
    public static PendingRequest parsePending(String value) {
        String[] fields = fields(value, 4);
        if (fields == null) return null;
        Skill skill = Skill.fromAlias(fields[0]);
        long input = positive(fields[1]), epoch = positive(fields[3]);
        int actorId = actor(fields[2]);
        return valid(skill, input, actorId, epoch) ? new PendingRequest(skill, input, actorId, epoch) : null;
    }
    public static AcceptedRequest parseAccepted(String value) {
        String[] fields = fields(value, 5);
        if (fields == null) return null;
        Skill skill = Skill.fromAlias(fields[0]);
        long input = positive(fields[1]), epoch = positive(fields[3]), token = positive(fields[4]);
        int actorId = actor(fields[2]);
        return valid(skill, input, actorId, epoch) && token > 0 ? new AcceptedRequest(skill, input, actorId, epoch, token) : null;
    }
    private static boolean valid(Skill skill, long input, int actorId, long epoch) {
        return skill != null && input > 0 && actorId >= 0 && epoch > 0;
    }
    private static String identity(Skill skill, long input, int actorId, long epoch) {
        return skill.alias() + ":" + numeral(input) + ":" + actorId + ":" + numeral(epoch);
    }
    private static String numeral(long value) { return Long.toString(value, 36); }
    private static String[] fields(String value, int count) {
        if (value == null || value.isEmpty() || value.length() > 64) return null;
        String[] fields = value.split(":", -1);
        return fields.length == count ? fields : null;
    }
    private static long positive(String number) {
        if (number.isEmpty() || number.length() > 13 || number.charAt(0) == '0') return 0;
        for (int i = 0; i < number.length(); ++i) {
            char digit = number.charAt(i);
            if (!(digit >= '0' && digit <= '9' || digit >= 'a' && digit <= 'z')) return 0;
        }
        try {
            long value = Long.parseLong(number, 36);
            return value > 0 && number.equals(numeral(value)) ? value : 0;
        } catch (NumberFormatException ignored) { return 0; }
    }
    private static int actor(String number) {
        if (number.isEmpty() || number.length() > 10 || number.length() > 1 && number.charAt(0) == '0') return -1;
        for (int i = 0; i < number.length(); ++i) if (number.charAt(i) < '0' || number.charAt(i) > '9') return -1;
        try {
            int actorId = Integer.parseInt(number);
            return actorId >= 0 && number.equals(Integer.toString(actorId)) ? actorId : -1;
        } catch (NumberFormatException ignored) { return -1; }
    }

    /** Actual player and connection references scope each monotonically increasing family input history. */
    public static final class NonceLedger {
        private record Entry(Object connection, long highest) {}
        private final Map<Object, Entry> players = new IdentityHashMap<>();
        public boolean claim(Object owner, Object connection, long input) {
            if (owner == null || connection == null || input <= 0) return false;
            Entry previous = players.get(owner);
            if (previous != null && previous.connection == connection && input <= previous.highest) return false;
            players.put(owner, new Entry(connection, input));
            return true;
        }
        public void forget(Object owner) { players.remove(owner); }
        public void clear() { players.clear(); }
        public long highest(Object owner, Object connection) {
            Entry entry = players.get(owner);
            return entry != null && entry.connection == connection ? entry.highest : 0;
        }
    }
    private LegacySingleKeyProtocol() {}
}

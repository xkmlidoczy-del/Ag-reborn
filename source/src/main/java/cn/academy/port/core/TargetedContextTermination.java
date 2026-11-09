/* AcademyCraft1.0.7 context termination transport adaptation. GPLv3; see NOTICE. */
package cn.academy.port.core;

/** Fixed context identity wire and exact live-session identity guard; no engine/client dependencies. */
public final class TargetedContextTermination {
    public static final String ACTION = "context_abort";
    public static final String FLASH_DIRECTION_ACTION = "flashing_direction_owned";
    public static final String PENDING_ACTION = "pending_context_abort";
    private TargetedContextTermination() {}

    public enum Skill {
        VEC_DEVIATION("vec_deviation"), VEC_REFLECTION("vec_reflection"),
        FLASHING("flashing"), STORM_WING("storm_wing");
        private final String id;
        Skill(String id) { this.id = id; }
        public String id() { return id; }
        public static Skill find(String id) {
            for (var skill : values()) if (skill.id.equals(id)) return skill;
            return null;
        }
    }

    public record Request(Skill skill, long input, long token) {
        public Request {
            if (skill == null || input <= 0 || token <= 0) throw new IllegalArgumentException("Positive context input/token required");
        }
        public String wire() { return skill.id() + ":" + input + ":" + token; }
    }

    /** Separate modern pre-acceptance cancellation; accepted context/V wire still requires a token. */
    public record PendingRequest(Skill skill, long input) {
        public PendingRequest {
            if(skill==null||input<=0)throw new IllegalArgumentException("Fixed pending context and positive original input required");
        }
        public String wire(){return skill.id()+":"+input;}
    }

    public static PendingRequest parsePending(String wire) {
        if(wire==null||wire.length()>64)return null;
        int separator=wire.indexOf(':');
        if(separator<1||wire.indexOf(':',separator+1)>=0)return null;
        var skill=Skill.find(wire.substring(0,separator));long input=positive(wire.substring(separator+1));
        return skill!=null&&input>0?new PendingRequest(skill,input):null;
    }

    /** Direction is keyed to one accepted Flashing context; no client position/rotation is transmitted. */
    public record FlashDirection(long input, long token, long sequence, int key) {
        public FlashDirection {
            if(input<=0||token<=0||sequence<1||sequence>4096||key<1||key>4)
                throw new IllegalArgumentException("Positive accepted Flashing context and bounded sequence/key required");
        }
        public String wire(){return input+":"+token+":"+sequence+":"+key;}
    }

    public static FlashDirection parseFlashDirection(String wire) {
        if(wire==null||wire.length()>64)return null;
        String[] fields=wire.split(":",-1);if(fields.length!=4)return null;
        long input=positive(fields[0]),token=positive(fields[1]),sequence=positive(fields[2]),key=positive(fields[3]);
        return input>0&&token>0&&sequence>=1&&sequence<=4096&&key>=1&&key<=4
                ?new FlashDirection(input,token,sequence,(int)key):null;
    }

    /** Captures actual server Hold identities, never current preset registrations. */
    public record Binding(Object owner, Object level, Object state, long input, long token) {}

    public static boolean matches(Binding binding, Object owner, Object level, Object state, long input, long token) {
        return binding != null && owner != null && level != null && state != null
                && binding.owner() == owner && binding.level() == level && binding.state() == state
                && input > 0 && token > 0 && binding.input() == input && binding.token() == token;
    }

    /** Existing Request value limit is 64 UTF-16 chars. No trimming, signs, alternate digits or extra fields. */
    public static Request parse(String wire) {
        if (wire == null || wire.length() > 64) return null;
        int first = wire.indexOf(':'), second = wire.indexOf(':', first + 1);
        if (first < 1 || second <= first + 1 || wire.indexOf(':', second + 1) >= 0) return null;
        var skill = Skill.find(wire.substring(0, first));
        if (skill == null) return null;
        long input = positive(wire.substring(first + 1, second)), token = positive(wire.substring(second + 1));
        return input > 0 && token > 0 ? new Request(skill, input, token) : null;
    }

    private static long positive(String text) {
        if (text.isEmpty() || text.length() > 19 || text.charAt(0) < '1' || text.charAt(0) > '9') return 0;
        long value = 0;
        for (int i = 0; i < text.length(); i++) {
            char digit = text.charAt(i);
            if (digit < '0' || digit > '9') return 0;
            int number = digit - '0';
            if (value > (Long.MAX_VALUE - number) / 10) return 0;
            value = value * 10 + number;
        }
        return value;
    }
}

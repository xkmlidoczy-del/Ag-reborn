package cn.academy.port.client;

import cn.academy.port.highpower.NewSkillRuntime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Per-skill authenticated input state for the seven post-0.81.2 skills (the shared edge/ack machine, one instance each). */
public final class NewSkillClient {
    private static final Map<String, BrakingTouchInputState> INPUTS = new LinkedHashMap<>();
    static {
        for (var kind : NewSkillRuntime.Kind.values()) INPUTS.put(kind.id, new BrakingTouchInputState());
    }
    private NewSkillClient() {}

    public static boolean handles(String skill) { return skill != null && INPUTS.containsKey(skill); }
    public static BrakingTouchInputState input(String skill) { return INPUTS.get(skill); }
    public static Iterable<Map.Entry<String, BrakingTouchInputState>> all() { return INPUTS.entrySet(); }
    public static void resetAll() { for (var state : INPUTS.values()) state.reset(); }
}

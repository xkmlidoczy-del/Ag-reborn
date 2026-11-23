package cn.aca.omnitrix;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

final class SkillCast {
    final WristNetwork.Performance motion;
    final Form form;
    final Kind kind;
    final Vec3 direction;
    final UUID target;
    final Set<UUID> hit = new HashSet<>();
    final int[] beats;
    int nextBeat;
    int lastFlags;
    Vec3 previousDivePosition;

    SkillCast(Form form, Kind kind, int code, int style, long now, int[] beats, int recovery, Vec3 origin, Vec3 focus,
              Vec3 direction, UUID target) {
        this.form = form;
        this.kind = kind;
        this.beats = beats;
        this.direction = direction;
        this.target = target;
        motion = new WristNetwork.Performance(code, style, now, now + beats[0],
                now + beats[beats.length - 1] + recovery, origin, focus);
    }

    boolean due(long now) {
        return nextBeat < beats.length && now >= motion.start() + beats[nextBeat];
    }

    long lastImpact() {
        return motion.start() + beats[beats.length - 1];
    }
}

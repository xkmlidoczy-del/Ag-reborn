package cn.aca.omnitrix.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;

final class PoseClip {
    private record Key(float tick, float x, float y, float z) {}

    private record Channel(Key[] keys, Key neutral) {
        static Channel read(JsonObject json, String name, float neutral) {
            JsonArray raw = json.getAsJsonArray(name);
            Key[] keys = new Key[raw == null ? 0 : raw.size()];
            for (int i = 0; i < keys.length; i++) {
                JsonArray values = raw.get(i).getAsJsonArray();
                keys[i] = new Key(values.get(0).getAsFloat(), values.get(1).getAsFloat(),
                        values.get(2).getAsFloat(), values.get(3).getAsFloat());
            }
            return new Channel(keys, new Key(0, neutral, neutral, neutral));
        }

        Key sample(float tick) {
            if (keys.length == 0) return neutral;
            if (tick <= keys[0].tick) return keys[0];
            for (int i = 1; i < keys.length; i++) {
                Key next = keys[i];
                if (tick <= next.tick) {
                    Key prev = keys[i - 1];
                    float delta = (tick - prev.tick) / (next.tick - prev.tick);
                    return new Key(tick, Mth.lerp(delta, prev.x, next.x),
                            Mth.lerp(delta, prev.y, next.y), Mth.lerp(delta, prev.z, next.z));
                }
            }
            return keys[keys.length - 1];
        }
    }

    private record Track(Channel position, Channel rotation, Channel scale) {}
    private final Map<String, Track> tracks = new HashMap<>();
    final float length;
    private final boolean repeat;

    PoseClip(JsonObject data) {
        length = data.get("length").getAsFloat();
        repeat = data.get("loop").getAsBoolean();
        for (var entry : data.getAsJsonObject("tracks").entrySet()) {
            JsonObject track = entry.getValue().getAsJsonObject();
            tracks.put(entry.getKey(), new Track(Channel.read(track, "position", 0),
                    Channel.read(track, "rotation", 0), Channel.read(track, "scale", 1)));
        }
    }

    Rig.Motion layer(Rig.Motion base, float time, float weight) {
        if (weight <= 0) return base;
        float tick = repeat ? ((time % length) + length) % length : Mth.clamp(time, 0, length);
        return bone -> {
            Track track = tracks.get(bone);
            Rig.Joint joint = base.at(bone);
            if (track == null) return joint;
            Key p = track.position.sample(tick);
            Key r = track.rotation.sample(tick);
            return new Rig.Joint(joint.x() + p.x * weight, joint.y() + p.y * weight, joint.z() + p.z * weight,
                    joint.pitch() + r.x * weight, joint.yaw() + r.y * weight, joint.roll() + r.z * weight,
                    joint.scale() * Mth.lerp(weight, 1, track.scale.sample(tick).x), joint.tint());
        };
    }
}

package cn.acamech.client;

import cn.acamech.MechAttack;
import net.minecraft.util.Mth;

/** Additive joint tracks in degrees and Blockbench units. */
final class AttackPose {
    private static final Frame REST = frame(0, 0, 0, 0);
    private static final Track[] HYDRAULIC = {
            track("root", frame(6, 0, 0, 0, 2, 5), frame(9, 0, 0, 0, 7, -8),
                    frame(11, 0, 0, 0, 7.5f, -9), frame(15, 0, 0, 0, 2, -3)),
            track("pelvis", frame(6, -4, -10, -5), frame(9, 13, 9, 3), frame(12, 11, 12, 4)),
            track("chest", frame(6, -12, -23, -8), frame(9, 41, 22, 8), frame(11, 37, 29, 10),
                    frame(15, 13, 12, 4)),
            track("head", frame(6, 8, 21, 5), frame(9, -35, -17, -4), frame(13, -25, -16, -3)),
            track("thigh.L", frame(6, -17, -3, -7), frame(10, -45, 1, -9), frame(15, -14, 0, -5)),
            track("shin.L", frame(6, 23, 0, 0), frame(10, 57, 0, -1), frame(15, 17, 0, 0)),
            track("foot.L", frame(6, -10, 4, 0), frame(10, -24, 5, 0), frame(15, -5, 0, 0)),
            track("thigh.R", frame(6, 2, 4, 5), frame(10, 17, 6, 8), frame(15, 4, 1, 2)),
            track("shin.R", frame(6, 10, 0, 0), frame(10, 7, 0, 2)),
            track("foot.R", frame(10, -32, 0, 0)),
            track("shoulder.L", frame(6, 5, -9, 10), frame(9, -2, 7, -8), frame(12, -5, 3, -3)),
            track("upper_arm.L", frame(4, -12, -9, -14), frame(6, 14, -18, -20),
                    frame(9, -83, -6, 2), frame(11, -88, -3, -3), frame(16, -23, -2, -10)),
            track("forearm.L", frame(6, -106, 1, 21), frame(9, -9, 0, 1, 2.5f, 0),
                    frame(11, -13, 0, 1, 1.5f, 0), frame(15, -61, 0, 12)),
            track("hand.L", frame(6, -12, 0, 18), frame(10, 9, 0, -12)),
            track("upper_arm.R", frame(6, -36, 6, 10), frame(10, 28, -4, 16), frame(14, 12, 0, 5)),
            track("forearm.R", frame(6, -53, -3, -10), frame(10, -28, 8, -14))
    };

    private static final Track[] SWEEP = {
            track("root", frame(3, 0, 0, 0, 3.3f, -2), frame(7, 0, 0, 0, 6, -9)),
            track("pelvis", frame(3, 3, 21, 5), frame(7, 12, -28, -8), frame(11, 3, -11, -4)),
            track("chest", frame(3, 15, 43, 12), frame(5, 25, -8, -4), frame(7, 26, -48, -13), frame(10, 10, -33, -6)),
            track("head", frame(3, -9, -33, -8), frame(7, -25, 43, 9)),
            track("thigh.L", frame(3, -18, -4, -9), frame(7, -33, 13, -12), frame(11, -12, 0, -5)),
            track("shin.L", frame(3, 23, 0, 0), frame(7, 43, 0, 0), frame(11, 13, 0, 0)),
            track("foot.L", frame(3, -10, -4, 0), frame(7, -16, 9, 0)),
            track("thigh.R", frame(3, -13, -11, 10), frame(7, 17, -9, 12)),
            track("shin.R", frame(3, 21, 0, 0), frame(7, 8, 0, 0)),
            track("foot.R", frame(3, -9, 0, 0), frame(7, -20, 0, 0)),
            track("shoulder.R", frame(3, -13, 18, 14), frame(7, -9, -12, 21)),
            track("upper_arm.R", frame(3, -54, 15, 37), frame(5, -69, 5, 9), frame(7, -58, -23, -15), frame(10, -31, -15, 8)),
            track("forearm.R", frame(3, -27, -9, -15), frame(5, -8, 0, 3), frame(8, -25, 13, 4), frame(12, -48, 0, 0)),
            track("hand.R", frame(3, 10, 0, -18), frame(7, -15, 0, 17)),
            track("upper_arm.L", frame(3, -31, 7, -14), frame(7, 22, 8, -35), frame(11, -4, 3, -12)),
            track("forearm.L", frame(3, -57, -5, 13), frame(7, -25, 0, 6))
    };

    private static final Track[] WINGS = {
            track("root", frame(7, 0, 0, 0, 4.0f, 1), frame(10, 0, 0, 0, 3.6f, -8), frame(13, 0, 0, 0, 5.0f, -6)),
            track("pelvis", frame(7, -13, 0, 0), frame(10, 15, 0, 0), frame(14, 12, 0, 0)),
            track("chest", frame(7, -17, 0, 0), frame(10, 23, 0, 0), frame(14, 17, 0, 0)),
            track("head", frame(7, 20, 0, 0), frame(11, -23, 0, 0), frame(17, -12, 0, 0)),
            track("thigh.L", frame(7, -24, -5, -8), frame(11, -37, -2, -11), frame(17, -12, 0, -3)),
            track("thigh.R", frame(7, -24, 5, 8), frame(11, -37, 2, 11), frame(17, -12, 0, 3)),
            track("shin.L", frame(7, 39, 0, 0), frame(11, 48, 0, 0), frame(17, 18, 0, 0)),
            track("shin.R", frame(7, 39, 0, 0), frame(11, 48, 0, 0), frame(17, 18, 0, 0)),
            track("foot.L", frame(7, -12, 0, 0), frame(11, -20, 0, 0)),
            track("foot.R", frame(7, -12, 0, 0), frame(11, -20, 0, 0)),
            track("upper_arm.L", frame(7, 11, -12, -36), frame(11, -73, 12, 14), frame(15, -42, 12, 10)),
            track("upper_arm.R", frame(7, 11, 12, 36), frame(11, -73, -12, -14), frame(15, -42, -12, -10)),
            track("forearm.L", frame(7, -69, 0, -13), frame(11, -54, -15, 13), frame(16, -39, 1, 7)),
            track("forearm.R", frame(7, -69, 0, 13), frame(11, -54, 15, -13), frame(16, -39, -1, -7)),
            track("wing_root.L", frame(7, -15, -29, 18), frame(11, 18, 64, -8), frame(14, 13, 46, -18), frame(19, 3, 13, -4)),
            track("wing_root.R", frame(7, -15, 29, -18), frame(11, 18, -64, 8), frame(14, 13, -46, 18), frame(19, 3, -13, 4)),
            track("wing_outer.L", frame(7, 9, -26, 5), frame(12, 16, 43, -27), frame(17, 5, 27, -15)),
            track("wing_outer.R", frame(7, 9, 26, -5), frame(12, 16, -43, 27), frame(17, 5, -27, 15))
    };

    private AttackPose() {}

    static void apply(BoxRig rig, MechAttack move, float age) {
        Track[] tracks = switch (move) {
            case HYDRAULIC -> HYDRAULIC;
            case SWEEP -> SWEEP;
            case WINGS -> WINGS;
        };
        float weight = Mth.clamp(age / 2f, 0, 1);
        for (Track track : tracks) {
            Frame from = REST;
            Frame to = null;
            for (Frame key : track.frames) {
                if (age <= key.tick) {
                    to = key;
                    break;
                }
                from = key;
            }
            if (to == null) to = frame(move.duration, 0, 0, 0);
            float blend = ease((age - from.tick) / (to.tick - from.tick));
            rig.rotate(track.bone, radians(Mth.lerp(blend, from.pitch, to.pitch) * weight),
                    radians(Mth.lerp(blend, from.yaw, to.yaw) * weight),
                    radians(Mth.lerp(blend, from.roll, to.roll) * weight));
            if (from.y != 0 || to.y != 0 || from.z != 0 || to.z != 0) {
                rig.offset(track.bone, 0, Mth.lerp(blend, from.y, to.y), Mth.lerp(blend, from.z, to.z));
            }
        }
        if (move == MechAttack.WINGS) {
            float motion = ease((age - 7) / 5) * ease((move.duration - age) / 11);
            for (int feather = 1; feather <= 5; feather++) {
                float lag = (feather - 3) * motion;
                rig.rotate("feather_0" + feather + ".L", 0, radians(lag * 5), radians(-lag * 4));
                rig.rotate("feather_0" + feather + ".R", 0, radians(-lag * 5), radians(lag * 4));
            }
        }
    }

    static float ease(float value) {
        float t = Mth.clamp(value, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float radians(float degrees) { return degrees * Mth.DEG_TO_RAD; }
    private static Track track(String bone, Frame... frames) { return new Track(bone, frames); }
    private static Frame frame(float time, float pitch, float yaw, float roll) { return frame(time, pitch, yaw, roll, 0, 0); }
    private static Frame frame(float time, float pitch, float yaw, float roll, float y, float z) {
        return new Frame(time, pitch, yaw, roll, y, z);
    }

    private record Track(String bone, Frame[] frames) {}
    private record Frame(float tick, float pitch, float yaw, float roll, float y, float z) {}
}

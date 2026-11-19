package cn.aca.omnitrix.client;

import cn.aca.omnitrix.Form;
import cn.aca.omnitrix.Kind;
import net.minecraft.util.Mth;

final class Motion {
    private Motion() {}

    static float ease(float n) {
        float x = Mth.clamp(n, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** Crisp mechanical Ease-Out-Back curve with tactile spring settle for watch pop-up & hologram snap. */
    static float easeOutBack(float n, float overshoot) {
        float x = Mth.clamp(n, 0, 1) - 1f;
        return 1f + x * x * ((overshoot + 1f) * x + overshoot);
    }

    /** Asymmetric fast-in / hold / smooth-out melee strike curve (peaks at 28% for crisp impact snap). */
    static float impactCurve(float action) {
        float x = Mth.clamp(action, 0, 1);
        if (x <= 0f || x >= 1f) return 0f;
        if (x < .28f) {
            float t = x / .28f;
            return 1f - (1f - t) * (1f - t) * (1f - t);
        }
        if (x < .40f) {
            return 1f - .04f * ((x - .28f) / .12f);
        }
        float r = (x - .40f) / .60f;
        float smooth = 1f - r * r * (3f - 2f * r);
        return .96f * smooth;
    }

    static float part(float time, float start, float end) { return ease((time - start) / (end - start)); }

    static Rig.Motion watch(Kind kind, float open, float bind, float press, float time) {
        return watch(kind, open, bind, press, time, time * 6, 0f);
    }

    static Rig.Motion watch(Kind kind, float open, float bind, float press, float time, float bearing) {
        return watch(kind, open, bind, press, time, bearing, 0f);
    }

    static Rig.Motion watch(Kind kind, float open, float bind, float press, float time, float bearing, float dialYaw) {
        // Spring overshoot on pop-up core + crisp mechanical depression when slammed
        float pop = open <= 0 ? 0 : open < 1 ? easeOutBack(open, 1.85f) : 1f;
        float slamCompress = ease(press);
        return name -> {
            int side = name.endsWith("l") || name.startsWith("tube_l") ? -1 : 1;
            if (name.startsWith("strap_")) {
                float angle = (1 - bind) * side;
                return kind == Kind.CELESTIAL ? Rig.Joint.bend(0, angle * 115, 0) : Rig.Joint.bend(0, 0, angle * 82);
            }
            if (name.startsWith("tube_")) {
                float surge = slamCompress * 9f;
                return Rig.Joint.bend((1 - bind) * -37 + (float) Math.sin(time * .24 + side) * open * 13 - surge, 0,
                        side * ((1 - bind) * 31 + slamCompress * 5f));
            }
            if (name.equals("dial")) {
                float fullLift = kind == Kind.CLASSIC ? 1.24f : kind == Kind.CELESTIAL ? .66f : .40f;
                float lift = pop * fullLift - slamCompress * (fullLift * .84f);
                float dialScale = 1f + Math.max(0f, pop - 1f) * .16f - slamCompress * .05f;
                return new Rig.Joint(0, lift, 0, 0, dialYaw, 0, dialScale);
            }
            if (name.startsWith("halo_")) {
                float pulse = open > 0 ? (float) Math.sin(time * .16) * 5.5f : 0f;
                float slamFlare = slamCompress * -18f;
                return Rig.Joint.bend(side * (open * 38 + pulse + slamFlare), side * open * 58 + dialYaw * .42f, 0);
            }
            if (name.equals("needle")) return Rig.Joint.bend(0, bearing + dialYaw * .5f, 0);
            return Rig.Joint.ZERO;
        };
    }

    static Rig.Motion actor(Form form, boolean carni, float time, float travel, float amount, float lookX, float lookY,
                            boolean airborne, boolean swimming, float action, float materialize) {
        return actor(form, carni, time, travel, amount, lookX, lookY, airborne, swimming ? 1f : 0f, action, materialize);
    }

    static Rig.Motion actor(Form form, boolean carni, float time, float travel, float amount, float lookX, float lookY,
                            boolean airborne, float aquatic, float action, float materialize) {
        boolean swimming = aquatic > .5f;
        float speed = switch (form) {
            case XLR8 -> 1.25f;
            case GREY_MATTER -> 1.12f;
            case FOUR_ARMS, DIAMONDHEAD -> .67f;
            case WILDMUTT -> .90f;
            case RIPJAWS -> swimming ? 1.05f : .78f;
            default -> .78f;
        };
        float phase = travel * speed + (carni ? .34f : 0f);
        float walk = Mth.sin(phase) * amount;
        float opposite = Mth.cos(phase) * amount;
        // Phase-lagged secondary motion waves (shoulder -> elbow -> wrist, hip -> knee -> tail)
        float forearmLag = Mth.sin(phase - .46f) * amount;
        float shinLag = Mth.sin(phase - .38f) * amount;
        float breath = Mth.sin(time * .105f) * .12f;
        float breathChest = Mth.cos(time * .105f - .35f);
        float strike = impactCurve(action);
        float opening = 1 - materialize;
        boolean quad = form == Form.WILDMUTT || form == Form.STINKFLY || carni && form == Form.GREY_MATTER;
        boolean hovering = form == Form.GHOSTFREAK || form == Form.ALIEN_X || carni && form == Form.UPGRADE;
        return name -> {
            float side = name.endsWith("_l") ? 1 : -1;
            if (name.equals("head")) {
                // Horizon stabilization counter-yaw against torso twist + focused strike snap
                float counterYaw = -opposite * (carni ? 3.5f : 2.2f);
                return Rig.Joint.bend(lookX * .56f + (carni ? opposite * 9 : -amount * 3.5f) - strike * 12,
                        lookY * .64f + counterYaw,
                        carni ? (float) Math.sin(time * .2f) * 4 : -opposite * 1.4f);
            }
            if (name.equals("body")) {
                float tilt = switch (form) {
                    case XLR8 -> -23 * amount - strike * 14;
                    case RIPJAWS -> Mth.lerp(aquatic, -6 * amount - strike * 9, carni ? 3 : -62);
                    case STINKFLY -> airborne ? -9 + strike * 24 : -4 * amount;
                    case GREY_MATTER -> carni ? 11 * amount : 5 * amount;
                    case FOUR_ARMS -> -7.5f * amount - 12 * strike;
                    case DIAMONDHEAD -> -6.5f * amount - 9 * strike;
                    case HEATBLAST, UPGRADE -> -5.5f * amount - 8 * strike;
                    case ALIEN_X -> airborne ? 12 * amount : -2.5f * amount;
                    default -> -4.5f * amount;
                };
                float bounce = hovering
                        ? 1.45f + (float) Math.sin(time * .13f) * .68f
                        : Math.abs(walk) * .54f - (airborne ? .18f : 0f);
                float torsoYaw = opposite * (carni ? 9.5f : 4.8f) + strike * (form == Form.FOUR_ARMS ? 11f : 8f);
                float torsoRoll = (carni ? walk * 4.2f : opposite * 2.4f);
                return new Rig.Joint(0, breath + bounce, 0,
                        tilt + opening * 10 + breathChest * .9f * (1f - amount),
                        torsoYaw, torsoRoll, 1);
            }
            if (name.startsWith("arm_")) {
                if (carni && form == Form.UPGRADE) return Rig.Joint.ZERO;
                float swing = side * walk * (quad ? 39 : 34);
                swing += aquatic * (-63 + side * opposite * 25);
                if (form == Form.ALIEN_X && airborne) swing += -24;
                if (form == Form.GHOSTFREAK) swing += -17 + Mth.cos(time * .15f) * 6.5f;
                if (form == Form.STINKFLY && airborne) swing += 18;
                if (form == Form.XLR8) swing += 27 * amount;
                if (form == Form.DIAMONDHEAD && carni) swing *= .68f;
                // Natural V-Taper heroic lat flare at idle (keeps arms proud off the lats instead of glued flat)
                float heroicFlare = quad ? 0f : (form == Form.FOUR_ARMS ? 6.5f : form == Form.DIAMONDHEAD ? 5.2f : 3.8f)
                        * (1f - amount * .5f) + breathChest * 1.1f;
                float attack = switch (form) {
                    case FOUR_ARMS -> strike * (carni ? 94 : 88);
                    case ALIEN_X -> strike * 48;
                    case GREY_MATTER -> strike * 35;
                    case WILDMUTT, STINKFLY -> strike * 46;
                    default -> strike * 70;
                };
                boolean raisedDorsal = carni && form == Form.FOUR_ARMS && name.equals("arm_l");
                float pitch = swing + (raisedDorsal ? -attack * .72f : attack) + opening * 14
                        + (airborne && !hovering && !quad ? -14f : 0f);
                return Rig.Joint.bend(pitch, side * strike * -8f,
                        side * (heroicFlare + strike * (form == Form.ALIEN_X ? -34 : 9) + (carni ? opening * 22 : 0)));
            }
            if (name.startsWith("elbow_")) {
                // Phase-lagged forearm follow-through prevents stiff plank-arm swing
                float lagSwing = name.contains("extra")
                        ? Math.max(0f, -side * opposite) * 20f + Math.abs(opposite) * 8f
                        : Math.max(0f, side * forearmLag) * 22f + Math.abs(walk) * 8f;
                float bend = quad
                        ? Math.max(0, side * opposite) * 20
                        : 11 + lagSwing + strike * 28 + (airborne && !hovering ? 12f : 0f);
                if (swimming) bend += side * opposite * 15;
                return Rig.Joint.bend(bend, 0, 0);
            }
            if (name.startsWith("leg_")) {
                if (form == Form.RIPJAWS && !carni) {
                    return new Rig.Joint(0, 0, 0, -side * walk * 44 - (airborne ? 13 : 0), 0, 0, 1 - aquatic);
                }
                if (form == Form.XLR8) return Rig.Joint.bend(side * walk * 21 + amount * 25 - strike * 22, 0, -side * amount * 11);
                return Rig.Joint.bend(-side * (quad ? opposite * 38 : walk * 44) - (airborne ? 13 : 0), 0, 0);
            }
            if (name.startsWith("shin_")) {
                float kneeFlex = Math.max(0, side * shinLag) * 29f + amount * 4f;
                return Rig.Joint.bend(-kneeFlex - (airborne ? 17 : 0), 0, 0);
            }
            if (name.startsWith("wheel_")) return Rig.Joint.bend(travel * 85, 0, 0);
            if (name.startsWith("extra_")) return Rig.Joint.bend(-side * walk * 26 + strike * 86 + 13, 0,
                    side * (-opening * 36 + strike * 14 - (carni ? 9 : 5)));
            if (name.startsWith("middle_")) return Rig.Joint.bend(side * opposite * 32, 0, 0);
            if (name.equals("tail")) {
                boolean fluke = form == Form.RIPJAWS && !carni;
                float pitch = fluke ? 62 * aquatic : 0;
                return new Rig.Joint(0, 0, 0, pitch + strike * 12,
                        Mth.sin(time * (swimming ? .46f : .20f)) * (swimming ? 17 : 10) - opposite * 8f,
                        form == Form.XLR8 ? walk * 11 : 0, fluke ? aquatic : 1);
            }
            if (name.startsWith("tail_") && name.length() > 5 && Character.isDigit(name.charAt(5))) {
                int segment = name.charAt(5) - '0';
                return Rig.Joint.bend(0, Mth.sin(time * (swimming ? .39f : .15f) - segment * .76f)
                        * (swimming ? 11 : 7), 0);
            }
            if (name.startsWith("wing_fore_") || name.startsWith("wing_hind_")) {
                return Rig.Joint.bend(0, side * Mth.sin(time * 1.77f + (name.startsWith("wing_hind_") ? 1 : 0)) * (airborne ? 13 : 3.5f), 0);
            }
            if (name.startsWith("wing_")) return Rig.Joint.bend(0, side * 17 * strike,
                    side * (airborne ? Mth.sin(time * (carni ? 2.1f : 2.6f)) * 39 : 9 + Mth.sin(time * .37f) * 4) + side * opening * 45);
            if (name.equals("visor")) return Rig.Joint.bend(17 * (1 - Math.min(1, amount * 2 + opening)), 0, 0);
            if (name.equals("jaw")) return Rig.Joint.bend(strike * (form == Form.RIPJAWS ? 84 : carni ? 44 : 30) + Mth.sin(time * .12f) * 5, 0, 0);
            if (name.equals("lower_jaw")) return Rig.Joint.bend(45 * strike, 0, 0);
            if (name.startsWith("jaw_fork_")) return Rig.Joint.bend(20 * strike, side * 13 * strike, side * 9 * strike);
            if (name.equals("lure")) return Rig.Joint.bend(Mth.sin(time * .19f) * 9 + strike * 15, opposite * 9, 0);
            if (name.startsWith("eyestalk_")) return Rig.Joint.bend(Mth.sin(time * .22f + name.hashCode() % 5) * 8 - strike * 19,
                    lookY * .22f + side * strike * 34, side * opposite * 5);
            if (name.startsWith("stinger_")) return Rig.Joint.bend(strike * 20, side * strike * 19, 0);
            if (name.startsWith("circuit_pulse_")) {
                float wave = Mth.sin(time * .18f - name.hashCode() % 17);
                int red = (int) (190 + 55 * wave);
                int green = (int) (211 + 39 * wave);
                return Rig.Joint.ZERO.tint(0xff0000ff | red << 16 | green << 8);
            }
            if (name.startsWith("tail_ghost_")) return Rig.Joint.bend(Mth.cos(time * .12f + name.hashCode() % 4) * 11,
                    Mth.sin(time * .18f + name.hashCode() % 5) * 16, -side * walk * 8);
            if (name.equals("seam_eye")) {
                float drift = Mth.sin(time * .072f);
                float depth = drift > 0 ? drift * .72f : -drift * .47f;
                return new Rig.Joint(-drift * .43f, drift * 1.52f, depth, 0, 0, 0, 1);
            }
            if (name.equals("ember_core")) return new Rig.Joint(0, 0, 0, 0, 0, 0, 1 + Mth.sin(time * .28f) * .12f);
            if (name.startsWith("hock_")) return Rig.Joint.bend(-side * walk * 27 + strike * 18, 0, 0);
            if (name.startsWith("flame")) return Rig.Joint.bend(Mth.sin(time * .50f + name.hashCode() % 9) * 13, 0, opposite * 7.5f);
            if (name.startsWith("tendril_")) return Rig.Joint.bend(Mth.sin(time * .25f + name.hashCode() % 5) * 16, 0, walk * 13);
            if (name.startsWith("forearm_blade_")) return new Rig.Joint(0, 0, 0, 0, 0, 0, .50f + strike * .62f);
            if (name.startsWith("fractured_ring_")) {
                float flutter = Mth.sin(time * .16f + name.hashCode() % 7);
                return new Rig.Joint(side * strike * .72f, flutter * .85f, 0, 0, flutter * 8, side * strike * 14, 1);
            }
            if (name.startsWith("orbit_node") || name.startsWith("broken_orbit")) {
                return new Rig.Joint(0, Mth.sin(time * .13f + name.hashCode() % 5) * 1.8f, 0, 0, time * (carni ? 2.3f : 1), strike * 30, 1);
            }
            return Rig.Joint.ZERO;
        };
    }
}

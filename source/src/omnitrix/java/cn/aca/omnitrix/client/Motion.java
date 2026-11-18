package cn.aca.omnitrix.client;

import cn.aca.omnitrix.Form;
import cn.aca.omnitrix.Kind;
import net.minecraft.util.Mth;

final class Motion {
    private Motion() {}

    static float ease(float n) {
        float x = Mth.clamp(n, 0, 1);
        return x*x*(3-2*x);
    }

    static float part(float time, float start, float end) { return ease((time-start)/(end-start)); }

    static Rig.Motion watch(Kind kind, float open, float bind, float press, float time) {
        return watch(kind, open, bind, press, time, time * 6, 0f);
    }

    static Rig.Motion watch(Kind kind, float open, float bind, float press, float time, float bearing) {
        return watch(kind, open, bind, press, time, bearing, 0f);
    }

    static Rig.Motion watch(Kind kind, float open, float bind, float press, float time, float bearing, float dialYaw) {
        // Spring overshoot on pop-up core for crisp mechanical Omnitrix tactile feel
        float pop = open <= 0 ? 0 : open < 1 ? (float) (open + Math.sin(open * Math.PI) * .22) : 1f;
        return name -> {
            int side = name.endsWith("l") || name.startsWith("tube_l") ? -1 : 1;
            if (name.startsWith("strap_")) {
                float angle = (1 - bind) * side;
                return kind == Kind.CELESTIAL ? Rig.Joint.bend(0, angle * 115, 0) : Rig.Joint.bend(0, 0, angle * 82);
            }
            if (name.startsWith("tube_")) {
                return Rig.Joint.bend((1 - bind) * -37 + (float) Math.sin(time * .24 + side) * open * 13, 0,
                        side * (1 - bind) * 31);
            }
            if (name.equals("dial")) {
                float lift = pop * (kind == Kind.CLASSIC ? 1.18f : .34f) - press * .76f;
                return new Rig.Joint(0, lift, 0, 0, dialYaw, 0, 1);
            }
            if (name.startsWith("halo_")) {
                float pulse = open > 0 ? (float) Math.sin(time * .16) * 5f : 0f;
                return Rig.Joint.bend(side * (open * 36 + pulse), side * open * 56 + dialYaw * .35f, 0);
            }
            if (name.equals("needle")) return Rig.Joint.bend(0, bearing, 0);
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
        float walk = Mth.sin(travel * speed + (carni ? .34f : 0))*amount;
        float opposite = Mth.cos(travel * speed)*amount;
        float breath = Mth.sin(time*.105f)*.11f;
        float strike = Mth.sin(action*(float)Math.PI);
        float opening = 1-materialize;
        boolean quad = form == Form.WILDMUTT || form == Form.STINKFLY || carni && form == Form.GREY_MATTER;
        boolean hovering = form == Form.GHOSTFREAK || form == Form.ALIEN_X || carni && form == Form.UPGRADE;
        return name -> {
            float side = name.endsWith("_l") ? 1 : -1;
            if (name.equals("head")) return Rig.Joint.bend(lookX*.54f + (carni ? opposite*9 : 0) - strike*11,lookY*.61f,
                    carni ? (float)Math.sin(time*.2f)*4 : 0);
            if (name.equals("body")) {
                float tilt = switch (form) {
                    case XLR8 -> -21*amount-strike*13;
                    case RIPJAWS -> Mth.lerp(aquatic, 5 * amount, carni ? 3 : -62);
                    case STINKFLY -> airborne ? -8 + strike*24 : 0;
                    case GREY_MATTER -> carni ? 11*amount : 4*amount;
                    case FOUR_ARMS -> -10*strike;
                    case ALIEN_X -> airborne ? 12*amount : 0;
                    default -> 0;
                };
                return new Rig.Joint(0, breath+(hovering ? 1.45f + (float)Math.sin(time*.13f)*.68f : Math.abs(walk)*.49f),
                        0, tilt+opening*10, opposite*(carni?9:3), carni ? walk*4 : opposite*2,1);
            }
            if (name.startsWith("arm_")) {
                if (carni && form == Form.UPGRADE) return Rig.Joint.ZERO;
                float swing = side*walk*(quad ? 39 : 31);
                swing += aquatic * (-63 + side * opposite * 25);
                if (form == Form.ALIEN_X && airborne) swing += -24;
                if (form == Form.GHOSTFREAK) swing += -17+Mth.cos(time*.15f)*6;
                if (form == Form.STINKFLY && airborne) swing += 18;
                if (form == Form.XLR8) swing += 25*amount;
                if (form == Form.DIAMONDHEAD && carni) swing *= .68;
                float attack = switch (form) {
                    case FOUR_ARMS -> strike*(carni ? 92 : 84);
                    case ALIEN_X -> strike*45;
                    case GREY_MATTER -> strike*32;
                    case WILDMUTT, STINKFLY -> strike*43;
                    default -> strike*66;
                };
                boolean raisedDorsal = carni && form == Form.FOUR_ARMS && name.equals("arm_l");
                float pitch = swing + (raisedDorsal ? -attack * .72f : attack) + opening * 14;
                return Rig.Joint.bend(pitch, 0,
                        side*(strike*(form == Form.ALIEN_X ? -34 : 7)+(carni ? opening*22 : 0)));
            }
            if (name.startsWith("elbow_")) {
                float extraPhase = name.contains("extra") ? Math.abs(opposite) * 14 : Math.abs(walk) * 16;
                float bend = quad ? Math.max(0, side * opposite) * 18 : 9 + extraPhase + strike * 24;
                if (swimming) bend += side * opposite * 15;
                return Rig.Joint.bend(bend, 0, 0);
            }
            if (name.startsWith("leg_")) {
                if (form == Form.RIPJAWS && !carni) {
                    return new Rig.Joint(0, 0, 0, -side * walk * 43 - (airborne ? 11 : 0), 0, 0, 1 - aquatic);
                }
                if (form == Form.XLR8) return Rig.Joint.bend(side*walk*19+amount*24 - strike*22, 0, -side*amount*11);
                return Rig.Joint.bend(-side*(quad ? opposite*37 : walk*43) - (airborne ? 11 : 0),0,0);
            }
            if (name.startsWith("shin_")) return Rig.Joint.bend(-Math.max(0, side * walk) * 24 - (airborne ? 14 : 0), 0, 0);
            if (name.startsWith("wheel_")) return Rig.Joint.bend(travel * 85, 0, 0);
            if (name.startsWith("extra_")) return Rig.Joint.bend(-side*walk*24+strike*82+12,0,
                    side*(-opening*36+strike*12-(carni?9:4)));
            if (name.startsWith("middle_")) return Rig.Joint.bend(side*opposite*32,0,0);
            if (name.equals("tail")) {
                boolean fluke = form == Form.RIPJAWS && !carni;
                float pitch = fluke ? 62 * aquatic : 0;
                return new Rig.Joint(0, 0, 0, pitch + strike * 12,
                        Mth.sin(time * (swimming ? .46f : .20f)) * (swimming ? 17 : 10),
                        form == Form.XLR8 ? walk * 10 : 0, fluke ? aquatic : 1);
            }
            if (name.startsWith("tail_") && name.length() > 5 && Character.isDigit(name.charAt(5))) {
                int segment = name.charAt(5) - '0';
                return Rig.Joint.bend(0, Mth.sin(time * (swimming ? .39f : .15f) - segment * .72f)
                        * (swimming ? 10 : 6), 0);
            }
            if (name.startsWith("wing_fore_") || name.startsWith("wing_hind_")) {
                return Rig.Joint.bend(0,side*Mth.sin(time*1.77f+(name.startsWith("wing_hind_")?1:0))*(airborne?12:3),0);
            }
            if (name.startsWith("wing_")) return Rig.Joint.bend(0,side*17*strike,
                    side*(airborne?Mth.sin(time*(carni?2.1f:2.6f))*39:9+Mth.sin(time*.37f)*4)+side*opening*45);
            if (name.equals("visor")) return Rig.Joint.bend(17 * (1 - Math.min(1, amount*2+opening)),0,0);
            if (name.equals("jaw")) return Rig.Joint.bend(strike*(form==Form.RIPJAWS ? 82 : carni?44:28)+Mth.sin(time*.12f)*5,0,0);
            if (name.equals("lower_jaw")) return Rig.Joint.bend(43*strike,0,0);
            if (name.startsWith("jaw_fork_")) return Rig.Joint.bend(20*strike,side*13*strike,side*9*strike);
            if (name.equals("lure")) return Rig.Joint.bend(Mth.sin(time*.19f)*9+strike*15,opposite*9,0);
            if (name.startsWith("eyestalk_")) return Rig.Joint.bend(Mth.sin(time*.22f+name.hashCode()%5)*8-strike*19,
                    lookY*.22f + side*strike*34,side*opposite*5);
            if (name.startsWith("stinger_")) return Rig.Joint.bend(strike*20,side*strike*19,0);
            if (name.startsWith("circuit_pulse_")) {
                float wave = Mth.sin(time * .18f - name.hashCode() % 17);
                int red = (int)(190 + 55 * wave);
                int green = (int)(211 + 39 * wave);
                return Rig.Joint.ZERO.tint(0xff0000ff | red << 16 | green << 8);
            }
            if (name.startsWith("tail_ghost_")) return Rig.Joint.bend(Mth.cos(time*.12f+name.hashCode()%4)*11,
                    Mth.sin(time*.18f+name.hashCode()%5)*16,-side*walk*8);
            if (name.equals("seam_eye")) {
                float drift = Mth.sin(time * .072f);
                float depth = drift > 0 ? drift * .72f : -drift * .47f;
                return new Rig.Joint(-drift * .43f, drift * 1.52f, depth, 0, 0, 0, 1);
            }
            if (name.equals("ember_core")) return new Rig.Joint(0,0,0,0,0,0,1+Mth.sin(time*.28f)*.12f);
            if (name.startsWith("hock_")) return Rig.Joint.bend(-side*walk*26+strike*17,0,0);
            if (name.startsWith("flame")) return Rig.Joint.bend(Mth.sin(time*.50f+name.hashCode()%9)*12,0,opposite*7);
            if (name.startsWith("tendril_")) return Rig.Joint.bend(Mth.sin(time*.25f+name.hashCode()%5)*16, 0, walk*13);
            if (name.startsWith("forearm_blade_")) return new Rig.Joint(0, 0, 0, 0, 0, 0, .50f + strike * .60f);
            if (name.startsWith("fractured_ring_")) {
                float flutter = Mth.sin(time * .16f + name.hashCode() % 7);
                return new Rig.Joint(side * strike * .72f, flutter * .85f, 0, 0, flutter * 8, side * strike * 14, 1);
            }
            if (name.startsWith("orbit_node") || name.startsWith("broken_orbit")) {
                return new Rig.Joint(0, Mth.sin(time*.13f + name.hashCode()%5)*1.8f, 0, 0, time*(carni?2.3f:1), strike*30,1);
            }
            return Rig.Joint.ZERO;
        };
    }
}

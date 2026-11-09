package cn.academy.port.highpower.core;

import java.util.Map;
import java.util.Set;

/**
 * Cosmetic casting gestures: basic body mechanics plus distinct technological control profiles for flagship skills.
 * The original nine enum ordinals remain stable on the wire; LIFT is retired from skill mappings. Pure,
 * deterministic and bounded: it never touches gameplay state, so a wrong or missing gesture can only look wrong.
 * <p>
 * Timeline: {@code age} is ticks since the key went down; {@code since} is ticks since it was released, negative while held.
 * Every gesture plays for at least {@link #minTicks} so tap skills still read, then relaxes over {@link #RELAX_TICKS}.
 */
public final class CastPose {
    public enum Archetype { STRIKE, AIM, GUARD, SLAM, SWEEP, LIFT, GATHER, FLICK, PLACE,
        CONSTRUCT_LOCK, CONSTRUCT_PULL, CONSTRUCT_STEER, CONSTRUCT_THROW, CONSTRUCT_LAND,
        LANCE, RAIL_DEPLOY, FIELD_TUNE, DISCHARGE, ACCELERATOR, CANOPY, PHASE_ROUTE, PHASE_GATE,
        VECTOR_CAGE, VECTOR_DOMAIN, FLIGHT, BRAKE, LISTEN, GRID_DEPLOY }

    public static final int RELAX_TICKS = 8;
    private static final Map<String, Archetype> MAP = Map.ofEntries(
            // classic electromaster
            e("arc_gen", Archetype.STRIKE), e("charging", Archetype.GATHER), e("mag_movement", Archetype.AIM), e("mag_manip", Archetype.GATHER),
            e("mine_detect", Archetype.PLACE), e("body_intensify", Archetype.GATHER), e("thunder_bolt", Archetype.STRIKE), e("railgun", Archetype.AIM),
            e("thunder_clap", Archetype.DISCHARGE),
            // classic meltdowner
            e("electron_bomb", Archetype.FLICK), e("rad_intensify", Archetype.GATHER), e("scatter_bomb", Archetype.FLICK), e("light_shield", Archetype.GUARD),
            e("meltdowner", Archetype.AIM), e("mine_ray_basic", Archetype.AIM), e("mine_ray_expert", Archetype.AIM), e("mine_ray_luck", Archetype.AIM),
            e("ray_barrage", Archetype.AIM), e("jet_engine", Archetype.FLIGHT), e("electron_missile", Archetype.FLICK),
            // classic teleporter
            e("threatening_teleport", Archetype.STRIKE), e("dim_folding_theorem", Archetype.GUARD), e("penetrate_teleport", Archetype.FLICK),
            e("mark_teleport", Archetype.PLACE), e("flesh_ripping", Archetype.STRIKE), e("location_teleport", Archetype.FLICK), e("shift_tp", Archetype.FLICK),
            e("space_fluct", Archetype.GATHER), e("flashing", Archetype.FLICK),
            // classic vector manipulation
            e("dir_shock", Archetype.STRIKE), e("ground_shock", Archetype.SLAM), e("vec_accel", Archetype.FLICK), e("vec_deviation", Archetype.GUARD),
            e("dir_blast", Archetype.STRIKE), e("storm_wing", Archetype.FLIGHT), e("blood_retro", Archetype.STRIKE), e("vec_reflection", Archetype.GUARD),
            e("plasma_cannon", Archetype.ACCELERATOR),
            // high-power electromaster
            e("forked_arc", Archetype.STRIKE), e("magnetic_bracer", Archetype.GUARD), e("charge_decoy", Archetype.GATHER), e("polarity_tether", Archetype.AIM),
            e("conductive_circuit", Archetype.PLACE), e("grounding_return", Archetype.SLAM), e("magnetic_ground_step", Archetype.SLAM),
            e("magnetic_item_pull", Archetype.GATHER), e("iron_sand_lance", Archetype.LANCE), e("thunder_net", Archetype.GRID_DEPLOY), e("potential_buffer", Archetype.GUARD),
            e("rail_thunder_array", Archetype.RAIL_DEPLOY), e("magnetic_domain", Archetype.FIELD_TUNE), e("pulse_shield", Archetype.GUARD), e("rail_relay", Archetype.PLACE),
            // high-power meltdowner
            e("dual_point", Archetype.AIM), e("scatter_plates", Archetype.PLACE), e("beam_etching", Archetype.AIM), e("point_meltdown", Archetype.AIM),
            e("scatter_locator_plate", Archetype.PLACE), e("remote_scatter_relay", Archetype.PLACE), e("thermal_shell", Archetype.GUARD),
            e("folded_mirror_array", Archetype.GRID_DEPLOY), e("disintegration_sweep", Archetype.SWEEP), e("particle_laminate", Archetype.GUARD),
            e("directional_dissipation_curtain", Archetype.GUARD), e("multicore_canopy", Archetype.CANOPY), e("zero_gap_armor", Archetype.GUARD),
            e("beam_step", Archetype.FLIGHT), e("reverse_jet_brake", Archetype.BRAKE),
            // high-power teleporter
            e("safe_landing_probe", Archetype.PLACE), e("displaced_projection", Archetype.FLICK), e("phase_sidestep", Archetype.FLICK), e("return_mark", Archetype.PLACE),
            e("cross_substitution", Archetype.FLICK), e("recovery_mark", Archetype.PLACE), e("corner_exchange", Archetype.FLICK), e("short_interception", Archetype.GUARD),
            e("spatial_pincer_corridor", Archetype.PHASE_GATE), e("multidimensional_landing_array", Archetype.PHASE_ROUTE), e("spatial_relay", Archetype.PHASE_ROUTE),
            e("tactical_coordinate_formation", Archetype.PHASE_ROUTE), e("emergency_return", Archetype.GATHER), e("incident_bypass", Archetype.GUARD),
            e("phase_corridor", Archetype.PHASE_GATE),
            // high-power vector manipulation
            e("deflection_strike", Archetype.STRIKE), e("vector_parry", Archetype.GUARD), e("braking_touch", Archetype.GUARD), e("shear_windblade", Archetype.SWEEP),
            e("inertial_anchor", Archetype.SLAM), e("yaw_palm", Archetype.SWEEP), e("trajectory_return", Archetype.FLICK), e("circulation_relief", Archetype.SLAM),
            e("circulation_listening", Archetype.LISTEN), e("vector_cage", Archetype.VECTOR_CAGE), e("multi_axis_reflection", Archetype.GUARD),
            e("wind_pressure_lift", Archetype.FLIGHT), e("autonomous_vector_circle", Archetype.VECTOR_DOMAIN), e("zenith_vector_dominion", Archetype.VECTOR_DOMAIN),
            e("impulse_split", Archetype.SWEEP), e("vector_construct", Archetype.CONSTRUCT_LOCK));
    /** Passive training courses: not casts, no gesture. */
    public static final Set<String> NOT_CASTS = Set.of("brain_course", "brain_course_advanced", "mind_course");
    /** The first-person hand of these skills is already animated by their own effect class; only the third-person arms are added. */
    private static final Set<String> OWN_FIRST_PERSON = Set.of("dir_shock", "dir_blast");

    /** Server feedback suffixes that mean the cast did not start (the action bar shows them under message.academy.highpower.<skill>.<suffix>). */
    private static final Set<String> REFUSALS = Set.of("not_ready", "no_target", "unsupported", "no_backend", "forbidden", "terrain", "too_large",
            "protected", "changed", "in_structure", "controlled", "server_busy", "massless");
    private static final String FEEDBACK = "message.academy.highpower.";
    /** True when {@code key} is the server telling the caster that {@code skill} was refused. Paid-but-ineffective and informational keys are not refusals. */
    public static boolean refusal(String key, String skill) {
        if (key == null || skill == null || skill.isEmpty() || !key.startsWith(FEEDBACK + skill + ".")) return false;
        return REFUSALS.contains(key.substring(FEEDBACK.length() + skill.length() + 1));
    }

    private CastPose() {}

    private static Map.Entry<String, Archetype> e(String id, Archetype a) { return Map.entry(id, a); }

    public static Archetype of(String skill) { return skill == null ? null : MAP.get(skill); }
    public static Set<String> mapped() { return MAP.keySet(); }
    public static boolean firstPersonOwnedElsewhere(String skill) { return skill != null && OWN_FIRST_PERSON.contains(skill); }

    public static int minTicks(Archetype a) {
        return switch (a) { case STRIKE -> 10; case AIM -> 8; case GUARD -> 10; case SLAM -> 12; case SWEEP -> 12; case LIFT -> 14; case GATHER -> 10; case FLICK -> 8; case PLACE -> 10; default -> 12; };
    }
    private static double rampIn(Archetype a) {
        return switch (a) { case STRIKE -> 6; case AIM -> 5; case GUARD -> 4; case SLAM -> 6; case SWEEP -> 5; case LIFT -> 7; case GATHER -> 6; case FLICK -> 3; case PLACE -> 5; default -> 4; };
    }

    private static double clamp01(double x) { return x != x ? 0 : Math.max(0, Math.min(1, x)); }
    private static double smooth(double x) { double t = clamp01(x); return t * t * (3 - 2 * t); }
    /** Rises to 1 at {@code x}=0.5 and returns to 0 at 0 and 1. */
    private static double bump(double x) { double t = clamp01(x); return Math.sin(Math.PI * t); }

    /** How far a gesture winds up the opposite way before it fires (fraction of its pose). Deliberate technical controls barely do. */
    private static double antic(Archetype a) {
        return switch (a) { case STRIKE -> .24; case FLICK -> .2; case SLAM -> .16; case SWEEP, CONSTRUCT_THROW -> .18; case PHASE_ROUTE, RAIL_DEPLOY -> .1; default -> .05; };
    }
    /** Overshoot of the settle (easeOutBack coefficient): snappy for strikes and flicks, restrained for precise controls. */
    private static double back(Archetype a) {
        return switch (a) { case STRIKE, FLICK, CONSTRUCT_THROW -> 1.7; case SLAM -> 1.3; case SWEEP -> 1.2; default -> .8; };
    }
    /**
     * Pose scale over time: a short counter-movement, a fast rise with a little overshoot, then a damped settle. It is 0 at the
     * press, slightly negative during the wind-up, peaks near 1.1 and rests at 1. Multiplies the pose; the 0..1 envelope still decides
     * how much of the vanilla limb is replaced.
     */
    static double dyn(Archetype a, double age) {
        double r = rampIn(a), t = age / r;
        if (t <= 0) return 0;
        if (t < .25) return -antic(a) * Math.sin(Math.PI * t / .25);
        if (t < 1) { double x = (t - .25) / .75, c1 = back(a), y = x - 1; return 1 + (c1 + 1) * y * y * y + c1 * y * y; }
        double u = (t - 1) * r;
        return 1 + .025 * back(a) * Math.exp(-u * .45) * Math.sin(u * 1.1);
    }
    private static double out(double since) { return since < 0 ? 1 : 1 - smooth(since / RELAX_TICKS); }
    /** Slow tension that builds while a gesture is held (0 right after the rise, 1 after about twelve ticks). */
    private static double hold(Archetype a, double age, double since) { return since < 0 ? smooth((age - rampIn(a)) / 12) : 0; }

    /** 0..1 strength of the gesture: eases in after the key goes down, eases out after it is released. */
    public static double envelope(Archetype a, double age, double since) {
        if (a == null || !Double.isFinite(age) || !Double.isFinite(since) || age < 0) return 0;
        double in = smooth(age / rampIn(a));
        double out = since < 0 ? 1 : 1 - smooth(since / RELAX_TICKS);
        return in * out;
    }

    /** First-person hand: translation in blocks (x right, y up, z forward is negative) and rotation in degrees. */
    public record Hand(double x, double y, double z, double rotX, double rotY, double rotZ) {
        public static final Hand NONE = new Hand(0, 0, 0, 0, 0, 0);
    }

    /** Inverse native mouse-look lag; apply yaw BEFORE pitch, after vanilla's pitch then yaw. */
    public record LookLag(double pitch, double yaw) {
        public static final LookLag NONE = new LookLag(0, 0);
    }
    public static LookLag firstPersonLookLag(double strength, double pitch, double yaw) {
        if (!Double.isFinite(strength) || !Double.isFinite(pitch) || !Double.isFinite(yaw) || strength <= 0) return LookLag.NONE;
        double s = clamp01(strength);
        // The pose itself winds up before reaching full strength. Ease stability in ahead of that
        // wind-up, while still returning continuously to native look lag at either silent boundary.
        double blend = 1 - (1 - s) * (1 - s) * (1 - s);
        return new LookLag(-pitch * blend, -yaw * blend);
    }

    /** Third-person arms: model-part angles in radians for the physical right and left arm, plus a forward body lean. */
    public record Arms(double rx, double ry, double rz, double lx, double ly, double lz, double lean) {
        public static final Arms NONE = new Arms(0, 0, 0, 0, 0, 0, 0);
    }

    /** The exact transform consumed by both camera and player rendering. Strength is used to blend vanilla limbs. */
    public record Motion(Hand hand, Arms arms, double strength, Body body) {
        public static final Motion NONE = new Motion(Hand.NONE, Arms.NONE, 0, Body.NONE);
        public Motion(Hand hand, Arms arms, double strength) { this(hand, arms, strength, Body.NONE); }
    }

    /**
     * Whole-body support for a gesture, already scaled by the gesture's drive. {@code twist} is the torso yaw in radians
     * (positive turns the right shoulder forward; mirrored for left-handed players), {@code spread} widens the stance and
     * {@code lunge} swings the off-side foot forward (negative leans the weight back).
     */
    public record Body(double twist, double spread, double lunge) {
        public static final Body NONE = new Body(0, 0, 0);
    }
    public record Wrist(double x,double y,double z) {}
    /** Vanilla arm wrist proxy transformed in exactly the same T * Rx * Ry * Rz order as RenderHandEvent. */
    public static Wrist wristCamera(Hand h,boolean right) {
        double x=right?.56:-.56,y=-.6,z=-.72;
        double ax=Math.toRadians(h.rotX),ay=Math.toRadians(h.rotY),az=Math.toRadians(h.rotZ);
        double xx=x*Math.cos(az)-y*Math.sin(az), yy=x*Math.sin(az)+y*Math.cos(az);
        double rx=xx*Math.cos(ay)+z*Math.sin(ay), rz=-xx*Math.sin(ay)+z*Math.cos(ay);
        return new Wrist(rx+h.x,yy*Math.cos(ax)-rz*Math.sin(ax)+h.y,yy*Math.sin(ax)+rz*Math.cos(ax)+h.z);
    }
    public static Motion sample(Archetype a, double age, double since, boolean right) {
        return new Motion(hand(a, age, since, right), arms(a, age, since, right), envelope(a, age, since), body(a, age, since, right));
    }
    public static Motion mix(Motion a, Motion b, double t) {
        t = smooth(t); double u=1-t;
        Hand x=a.hand, y=b.hand; Arms p=a.arms, q=b.arms;
        Body m=a.body, n=b.body;
        return new Motion(new Hand(x.x*u+y.x*t,x.y*u+y.y*t,x.z*u+y.z*t,x.rotX*u+y.rotX*t,x.rotY*u+y.rotY*t,x.rotZ*u+y.rotZ*t),
                new Arms(p.rx*u+q.rx*t,p.ry*u+q.ry*t,p.rz*u+q.rz*t,p.lx*u+q.lx*t,p.ly*u+q.ly*t,p.lz*u+q.lz*t,p.lean*u+q.lean*t),a.strength*u+b.strength*t,
                new Body(m.twist*u+n.twist*t,m.spread*u+n.spread*t,m.lunge*u+n.lunge*t));
    }

    /** @param rightHanded true when the main arm is the right arm; the gesture mirrors for left-handed players. */
    public static Hand hand(Archetype a, double age, double since, boolean rightHanded) {
        double env0 = envelope(a, age, since);
        if (a == null || env0 <= 0) return Hand.NONE;
        double env = dyn(a, age) * out(since);
        double s = rightHanded ? 1 : -1, held = since < 0 ? 1 : 0, rel = since < 0 ? 0 : since, sw = smooth(age / 10.0), pulse = Math.sin(age * .5);
        Hand h = switch (a) {
            case STRIKE -> { double thrust = since < 0 ? 0 : bump(rel / 6.0); yield new Hand(.05 * s * held, -.12 * held, .15 * held - .38 * thrust, 18 * held - 34 * thrust, 0, -6 * s * held); }
            case AIM -> new Hand(.02 * s, .10 + .006 * pulse, -.12, -22, -4 * s, 0);
            case GUARD -> new Hand(-.14 * s, .08, -.2, -10, 28 * s, 6 * s);
            case SLAM -> new Hand(0, -.30, -.06, 40, 0, 0);
            case SWEEP -> new Hand(s * (-.42 + .38 * sw), .10, -.14, 12, s * (34 - 58 * sw), -10 * s * (1 - sw));
            // These are offsets BEFORE vanilla renderPlayerArm's own transforms. Large positive X rotations
            // lift its normally cropped shoulder cap into view; keep the hand raised without exposing that cap.
            case LIFT -> new Hand(-.08 * s, .02 + .008 * pulse, -.08, 8, 8 * s, 0);
            case GATHER -> new Hand(-.2 * s, .01 + .012 * pulse, -.1, 8, 36 * s, -12 * s);
            case FLICK -> new Hand(0, .065, -.14 * bump(age / 8.0), 8 * bump(age / 8.0), 0, 0);
            case PLACE -> new Hand(0, -.2, -.1, 26, 0, 0);
            // Small, deliberate operations near the sightline. No overhead ritual pose.
            case CONSTRUCT_LOCK -> new Hand(-.02*s, .11, -.22, 12, -12*s, 5*s);
            case CONSTRUCT_PULL -> new Hand(-.12*s, .015, -.06, 8, 18*s, -10*s);
            case CONSTRUCT_STEER -> new Hand(-.1*s, 0, -.08, 8, 12*s, -6*s);
            case CONSTRUCT_THROW -> { double f=smooth(age/4); yield new Hand(.05*s, .14, -.1-.3*f, 16-8*f, -8*s, -12*s); }
            case CONSTRUCT_LAND -> new Hand(-.06*s, -.05, -.1, 10, 12*s, 8*s);
            case LANCE -> new Hand(-.06*s, .10, -.25, 18, -18*s, -16*s);
            case RAIL_DEPLOY -> new Hand(s*(-.23+.15*sw), .065, -.14, 8, s*(18-24*sw), -8*s);
            case FIELD_TUNE -> new Hand(-.16*s, .06, -.11, 10, 32*s, -22*s);
            case DISCHARGE -> new Hand(.12*s, .16, -.18, 12, -28*s, -28*s);
            case ACCELERATOR -> new Hand(-.18*s, 0, -.1, 8, 26*s, -12*s);
            case CANOPY -> new Hand(-.08*s, .15, -.12, 10, -16*s, -16*s);
            case PHASE_ROUTE -> new Hand(s*(-.19+.13*sw), -.023, -.09, 8, s*(24-34*sw), 10*s);
            case PHASE_GATE -> new Hand(.08*s, .18, -.26, 16, -26*s, -16*s);
            case VECTOR_CAGE -> new Hand(-.2*s, 0, -.1, 8, 42*s, -10*s);
            case VECTOR_DOMAIN -> new Hand(.04*s, .13, -.2, 18, -12*s, -34*s);
            case FLIGHT -> new Hand(.16*s, .04, -.12, 24, -24*s, -32*s);
            case BRAKE -> new Hand(.1*s, .16, -.28, 8, -18*s, -18*s);
            case LISTEN -> new Hand(0, -.01, -.09, 8, 16*s, 8*s);
            case GRID_DEPLOY -> new Hand(.04*s, -.07, -.12, 10, 28*s, 4*s);
        };
        double breathe = hold(a, age, since) * out(since);
        return new Hand(h.x() * env, h.y() * env + .004 * breathe * Math.sin(age * .3), h.z() * env + .004 * breathe * Math.sin(age * .23 + 1),
                h.rotX() * env + .9 * breathe * Math.sin(age * .21), h.rotY() * env, h.rotZ() * env + .9 * s * breathe * Math.sin(age * .26 + .7));
    }

    /** Arms for a third-person model. Angles blend from the model's own pose by {@code env} in the caller. */
    public static Arms arms(Archetype a, double age, double since, boolean rightHanded) {
        double env0 = envelope(a, age, since);
        if (a == null || env0 <= 0) return Arms.NONE;
        double env = dyn(a, age) * out(since), breathe = hold(a, age, since) * out(since);
        double s = rightHanded ? 1 : -1, rel = since < 0 ? 0 : since, sw = smooth(age / 10.0), pulse = Math.sin(age * .5), tremor = .02 * Math.sin(age * .9);
        // main arm (mx, my, mz), off arm (ox, oy, oz) in the main-arm-is-right frame; y and z mirror for left-handed players.
        double mx, my, mz, ox, oy, oz, lean = 0;
        switch (a) {
            case STRIKE -> { double thrust = since < 0 ? 0 : bump(rel / 6.0); mx = .45 * (1 - thrust) - 1.5 * thrust; my = 0; mz = .15 * (1 - thrust); ox = -.5; oy = .1; oz = -.2; lean = .08 * thrust; }
            case AIM -> { mx = -1.55 + tremor; my = .05; mz = 0; ox = -.3; oy = .3; oz = -.15; }
            case GUARD -> { mx = -1.2; my = -.35; mz = .1; ox = -1.3; oy = .35; oz = -.1; }
            case SLAM -> { mx = -.45; my = 0; mz = .08; ox = -.45; oy = 0; oz = -.08; lean = .32; }
            case SWEEP -> { mx = -1.4; my = .9 - 1.7 * sw; mz = 0; ox = -.2; oy = 0; oz = -.3; lean = .05; }
            case LIFT -> { mx = -2.45 + .04 * pulse; my = 0; mz = .28; ox = -2.45 + .04 * pulse; oy = 0; oz = -.28; lean = -.1; }
            case GATHER -> { mx = -1.05 + .05 * pulse; my = -.55; mz = 0; ox = -1.05 + .05 * pulse; oy = .55; oz = 0; lean = .06; }
            case FLICK -> { double f = bump(age / 8.0); mx = -1.0 - .6 * f; my = -.1; mz = .08; ox = -.3; oy = .2; oz = -.1; }
            case PLACE -> { mx = -.95; my = .15; mz = 0; ox = -.2; oy = 0; oz = -.1; lean = .12; }
            case CONSTRUCT_LOCK -> { mx=-1.48; my=-.15; mz=.06; ox=-.4; oy=.3; oz=-.22; }
            case CONSTRUCT_PULL -> { mx=-1.04; my=-.38; mz=.18; ox=-.8; oy=.38; oz=-.2; lean=-.06; }
            case CONSTRUCT_STEER -> { mx=-1.22; my=-.22; mz=.12; ox=-.75; oy=.32; oz=-.2; lean=-.03; }
            case CONSTRUCT_THROW -> { double f=smooth(age/4); mx=-1.1-.55*f; my=.15*f; mz=-.18*f; ox=-.55; oy=.25; oz=-.35; lean=.16*f; }
            case CONSTRUCT_LAND -> { mx=-.78; my=-.12; mz=.18; ox=-.65; oy=.32; oz=-.25; lean=.1; }
            case LANCE -> { mx=-1.5; my=.2; mz=-.18; ox=-.85; oy=.6; oz=-.35; lean=-.06; }
            case RAIL_DEPLOY -> { mx=-1.3; my=.65-1.0*sw; mz=.12; ox=-1.15; oy=.5; oz=-.28; }
            case FIELD_TUNE -> { mx=-1.0; my=-.8; mz=.4; ox=-1.35; oy=.7; oz=-.45; }
            case DISCHARGE -> { mx=-1.35; my=.8; mz=.85; ox=-1.35; oy=-.8; oz=-.85; lean=-.12; }
            case ACCELERATOR -> { mx=-1.4; my=-.45; mz=.12; ox=-1.25; oy=.4; oz=-.12; lean=.1; }
            case CANOPY -> { mx=-1.9; my=.35; mz=.55; ox=-1.25; oy=.55; oz=-.35; lean=-.08; }
            case PHASE_ROUTE -> { mx=-1.15; my=.6-1.1*sw; mz=.2; ox=-.9; oy=.5; oz=-.1; }
            case PHASE_GATE -> { mx=-1.55; my=.6; mz=.2; ox=-1.4; oy=-.5; oz=-.2; }
            case VECTOR_CAGE -> { mx=-1.1; my=-.7; mz=-.15; ox=-1.1; oy=.7; oz=.15; lean=.12; }
            case VECTOR_DOMAIN -> { mx=-1.5; my=.25; mz=.6; ox=-.6; oy=.65; oz=-.6; lean=-.04; }
            case FLIGHT -> { mx=-.5; my=.4; mz=1.0; ox=-.5; oy=-.4; oz=-1.0; lean=.18; }
            case BRAKE -> { mx=-1.5; my=.25; mz=.4; ox=-1.5; oy=-.25; oz=-.4; lean=-.15; }
            case LISTEN -> { mx=-2.05; my=-.55; mz=-.2; ox=-.25; oy=.2; oz=-.15; }
            case GRID_DEPLOY -> { mx=-.9; my=-.6; mz=.1; ox=-1.4; oy=.4; oz=-.1; lean=.08; }
            default -> throw new IllegalArgumentException("unhandled cast profile: " + a);
        }
        // Held poses are never frozen: the shoulders breathe, out of phase, and the weight drifts a little.
        double mxs = mx * env + .035 * breathe * Math.sin(age * .3), mys = (my * env + .03 * breathe * Math.sin(age * .21 + .4)) * s, mzs = (mz * env + .02 * breathe * Math.sin(age * .26 + 1)) * s;
        double oxs = ox * env + .03 * breathe * Math.sin(age * .3 + 1.7), oys = (oy * env + .03 * breathe * Math.sin(age * .21 + 2)) * s, ozs = (oz * env + .02 * breathe * Math.sin(age * .26 + 2.3)) * s;
        lean = lean * env + .012 * breathe * Math.sin(age * .17);
        // The main arm is the right arm for right-handed players; otherwise the roles swap and y/z mirror (done above through s).
        return rightHanded ? new Arms(mxs, mys, mzs, oxs, oys, ozs, lean) : new Arms(oxs, oys, ozs, mxs, mys, mzs, lean);
    }

    /** Torso twist and stance for a gesture, in the same timeline as the arms. */
    public static Body body(Archetype a, double age, double since, boolean rightHanded) {
        double env0 = envelope(a, age, since);
        if (a == null || env0 <= 0) return Body.NONE;
        double drive = dyn(a, age) * out(since), s = rightHanded ? 1 : -1, sw = smooth(age / 10.0), t = since < 0 ? 0 : bump((since < 0 ? 0 : since) / 6.0), f = smooth(age / 4);
        double[] b = switch (a) {
            case STRIKE -> new double[] {-.14 * (1 - t) + .2 * t, .08, .08 + .2 * t};
            case AIM -> new double[] {.08, .06, .12};
            case GUARD -> new double[] {-.05, .05, .1};
            case SLAM -> new double[] {0, .17, 0};
            case SWEEP -> new double[] {-.2 + .4 * sw, .09, .06};
            case LIFT -> new double[] {0, .1, 0};
            case GATHER -> new double[] {0, .04, 0};
            case FLICK -> new double[] {.12 * bump(age / 8.0), .04, .1 * bump(age / 8.0)};
            case PLACE -> new double[] {.04, .05, .04};
            case CONSTRUCT_LOCK -> new double[] {.1, .05, .1};
            case CONSTRUCT_PULL -> new double[] {.04, .08, -.06};
            case CONSTRUCT_STEER -> new double[] {.05, .08, 0};
            case CONSTRUCT_THROW -> new double[] {-.05 + .28 * f, .09, .08 + .2 * f};
            case CONSTRUCT_LAND -> new double[] {0, .12, 0};
            case LANCE -> new double[] {.1, .07, .12};
            case RAIL_DEPLOY -> new double[] {-.18 + .3 * sw, .07, .05};
            case FIELD_TUNE -> new double[] {.04, .1, 0};
            case DISCHARGE -> new double[] {0, .16, -.05};
            case ACCELERATOR -> new double[] {.07, .1, .12};
            case CANOPY -> new double[] {0, .12, 0};
            case PHASE_ROUTE -> new double[] {-.14 + .26 * sw, .06, .04};
            case PHASE_GATE -> new double[] {.06, .08, .06};
            case VECTOR_CAGE -> new double[] {0, .09, .05};
            case VECTOR_DOMAIN -> new double[] {.05, .07, 0};
            case FLIGHT -> new double[] {0, .14, .1};
            case BRAKE -> new double[] {0, .1, -.12};
            case LISTEN -> new double[] {.06, .03, 0};
            case GRID_DEPLOY -> new double[] {-.05 + .1 * sw, .06, 0};
        };
        // The wind-up reverses the torso and arms only; the feet never cross or narrow behind the pose.
        double plant = Math.max(0, drive);
        return new Body(b[0] * drive * s, b[1] * plant, b[2] * plant);
    }
}

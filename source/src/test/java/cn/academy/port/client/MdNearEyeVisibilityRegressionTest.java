package cn.academy.port.client;

import cn.academy.port.client.vfx2.MdNearEyeVisibility;
import cn.academy.port.client.vfx2.MdNearEyeVisibility.Owner;

/** Standalone checks of the actual ownership policy and fragment-alpha numerical oracle. */
public final class MdNearEyeVisibilityRegressionTest {
    private static int checks;

    public static void main(String[] args) {
        ownershipAndContext();
        continuousDepthAndDirection();
        alphaComposition();
        System.out.println("PASS " + checks + " Meltdowner near-eye policy/depth/composition checks; GPU readability remains unverified");
    }

    private static void ownershipAndContext() {
        Object player = new Object(), level = new Object(), connection = new Object(), other = new Object();
        long session = Long.MAX_VALUE - 20;
        Owner owner = new Owner(player, level, connection, session);
        check(eligible(owner, player, level, connection, session, player, true, true, false), "exact local ownership accepted");
        check(!eligible(null, player, level, connection, session, player, true, true, false), "old overload/unknown caster rejected");
        check(!eligible(new Owner(other, level, connection, session), player, level, connection, session, player, true, true, false),
                "unrelated beam rejected even if its axis intersects camera");
        check(!eligible(owner, other, level, connection, session, other, true, true, false), "respawn/entity ID reuse rejected by identity");
        check(!eligible(owner, player, other, connection, session, player, true, true, false), "world replacement rejected");
        check(!eligible(owner, player, level, other, session, player, true, true, false), "connection replacement rejected");
        check(!eligible(owner, player, level, connection, session + 1, player, true, true, false), "session replacement rejected");
        check(!eligible(owner, player, level, connection, session, other, true, true, false), "observer/free camera rejected");
        check(!eligible(owner, player, level, connection, session, player, false, true, false), "third person and detached camera rejected");
        check(!eligible(owner, player, level, connection, session, player, true, false, false), "dead/removed caster rejected");
        check(!eligible(owner, player, level, connection, session, player, true, true, true), "spectator rejected");
        check(!eligible(owner, null, level, connection, session, null, true, true, false), "missing player rejected");
        check(!eligible(owner, player, null, connection, session, player, true, true, false), "missing level rejected");
        check(!eligible(owner, player, level, null, session, player, true, true, false), "missing connection rejected");
        check(!eligible(new Owner(null, null, null, session), null, null, null, session, null, true, true, false),
                "matching null references never establish ownership");
        check(!MdNearEyeVisibility.eligible(owner, false, player, level, connection, session, player, player,
                true, false, true, false), "reflected and mining rays rejected even with exact local owner");
        check(!MdNearEyeVisibility.eligible(owner, true, player, level, connection, session, player, other,
                true, false, true, false), "event camera mismatch rejected");
        check(!MdNearEyeVisibility.eligible(owner, true, player, level, connection, session, other, player,
                true, false, true, false), "Minecraft camera mismatch rejected");
        check(!MdNearEyeVisibility.eligible(owner, true, player, level, connection, session, player, player,
                true, true, true, false), "detached event camera rejected");
        // Value equality must never substitute for reference identity at any identity boundary.
        String sameA = new String("player"), sameB = new String("player");
        check(!eligible(new Owner(sameA, level, connection, session), sameB, level, connection, session, sameB, true, true, false),
                "equal but distinct identities rejected");
    }

    private static void continuousDepthAndDirection() {
        for (boolean core : new boolean[]{false, true}) {
            double start = MdNearEyeVisibility.FADE_START;
            double end = core ? MdNearEyeVisibility.CORE_FADE_END : MdNearEyeVisibility.GLOW_FADE_END;
            for (double depth : new double[]{-100, -1, 0, .001, .05, start})
                check(value(depth, core) == 0, "unsafe depth has zero contribution including very short eye-axis geometry");
            check(Math.abs(value((start + end) / 2, core) - .5) < 1e-15, "physical midpoint is half strength");
            for (double depth : new double[]{end, end + 1, 256, 30_000_000, Double.MAX_VALUE})
                check(value(depth, core) == 1, "distant direction retains exact material alpha");
            double previous = 0;
            for (int i = 0; i <= 10000; i++) {
                double current = value(start + (end - start) * i / 10000, core);
                check(Double.isFinite(current) && current >= previous && current >= 0 && current <= 1,
                        "continuous monotonic finite fade with no reentry/proximity threshold");
                previous = current;
            }
            for (double join : new double[]{start, end}) {
                double epsilon = 1e-6;
                double left = (value(join, core) - value(join - epsilon, core)) / epsilon;
                double right = (value(join + epsilon, core) - value(join, core)) / epsilon;
                check(Math.abs(left - right) < 1e-4, "first derivative continuous at fade join");
                check(Math.abs(value(join + epsilon, core) - value(join - epsilon, core)) < 1e-9,
                        "small camera movement cannot cause a hard material jump");
            }
            for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
                check(value(bad, core) == 1, "invalid numerical input fails to classic in pure oracle");
        }
        for (double depth : new double[]{.121, .2, .3, .5, .8, 1.2, 1.6})
            check(value(depth, true) >= value(depth, false), "directional core always recovers at least as quickly as broad glow");
        check(value(.3, true) > 0 && value(.3, false) > 0, "short-shot original cap beyond unsafe depth remains drawable");
    }

    private static void alphaComposition() {
        // Alpha over an opaque scene is 1-product(1-a). This bounds 256 overlapping shots
        // with 12 conservatively counted fragments per layer; it does not prove GPU readability.
        for (double depth : new double[]{0, .05, .119, .12}) {
            double transparency = 1;
            for (int beam = 0; beam < 256; beam++) for (int face = 0; face < 12; face++) {
                transparency *= 1 - (200 / 255d) * value(depth, true);
                transparency *= 1 - (60 / 255d) * value(depth, true);
                transparency *= 1 - .9 * value(depth, false);
            }
            check(transparency == 1, "unsafe fragments cannot accumulate opacity across 256 shots");
        }
        for (double alpha : new double[]{0, 1 / 255d, .1, 60 / 255d, 200 / 255d, 1}) {
            check(alpha * value(1.6, false) == alpha && alpha * value(.8, true) == alpha, "far alpha unchanged exactly");
            for (int step = 0; step <= 160; step++) {
                double glow = alpha * value(step / 100d, false), core = alpha * value(step / 100d, true);
                check(glow >= 0 && glow <= alpha && core >= 0 && core <= alpha, "fade cannot amplify original layer alpha");
            }
        }
    }

    private static boolean eligible(Owner owner, Object player, Object level, Object connection,
                                    long session, Object camera, boolean firstPerson, boolean alive, boolean spectator) {
        return MdNearEyeVisibility.eligible(owner, true, player, level, connection, session, camera, camera, firstPerson, false, alive, spectator);
    }
    private static double value(double depth, boolean core) { return MdNearEyeVisibility.visibility(depth, core); }
    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
}

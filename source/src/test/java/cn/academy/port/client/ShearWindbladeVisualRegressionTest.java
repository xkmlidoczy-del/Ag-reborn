package cn.academy.port.client;

import cn.academy.port.highpower.core.ShearWindbladeGeometry.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;

/** Actual receipt state and production render geometry; no Minecraft, timer or collision substitutes. */
public final class ShearWindbladeVisualRegressionTest {
    private static final Point ORIGIN = new Point(0, 64, 0), ZERO = new Point(0, 0, 0);
    private static final UUID OWNER = new UUID(421, 1);
    private static int checks;
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static List<Point> front(boolean vertical, double distance) {
        var f = cn.academy.port.highpower.core.ShearWindbladeGeometry.frame(ORIGIN, new Point(0, 0, 1), 0);
        return cn.academy.port.highpower.core.ShearWindbladeGeometry.front(f, vertical ? 1 : 0, distance);
    }
    private static ShearWindbladeState.Front pose(int blade, boolean vertical, double from, double to, int status) {
        return new ShearWindbladeState.Front(blade, front(vertical, from), front(vertical, to), status);
    }
    private static ShearWindbladeState.Frame packet(int owner, long input, long sequence, boolean vertical,
                                                   List<ShearWindbladeState.Front> fronts, List<ShearWindbladeState.Impact> impacts) {
        return new ShearWindbladeState.Frame(new ShearWindbladeState.Identity(owner, owner == 19 ? OWNER : new UUID(421, owner), 71, input, input),
                sequence, 0, vertical, ORIGIN, fronts, impacts);
    }
    private static ShearWindbladeState.Frame packet(long sequence, ShearWindbladeState.Front... fronts) {
        return packet(19, 1, sequence, false, List.of(fronts), List.of());
    }
    private static ShearWindbladeState.Frame ended(long input, long sequence) {
        return new ShearWindbladeState.Frame(new ShearWindbladeState.Identity(19, OWNER, 71, input, input), sequence, 1, false, ZERO, List.of(), List.of());
    }
    public static void main(String[] args) throws Exception {
        var first = packet(1, pose(0, false, 0, 2, 0));
        var state = new ShearWindbladeState();
        check(state.accept(first, true), "certified first front admitted");
        check(!state.accept(first, true), "duplicate cannot replay the first blade");
        check(!state.accept(packet(2, pose(0, false, 0, 2, 0)), true), "consecutive receipt cannot rewind its previous endpoint");
        check(state.accept(packet(2, pose(0, false, 2, 4, 0)), true), "actual two metre continuation accepted");
        check(state.accept(packet(3, pose(0, false, 4, 6, 0), pose(1, false, 0, 2, 0)), true), "second true front joins after delay");
        check(state.local().fronts().size() == 2, "two fronts remain distinct, with no sector fill");
        check(state.accept(ended(1, 4), true) && state.active().isEmpty(), "server terminal clears exact controller");
        check(!state.accept(packet(5, pose(1, false, 2, 4, 0)), true), "late flight cannot revive server terminal");
        state.resetSession(); state.cancel(71, 1);
        check(!state.accept(first, true), "abort before acknowledgement tombstones future flight");
        state.resetSession(); state.accept(first, true); state.clear();
        check(!state.accept(packet(2, pose(0, false, 2, 4, 0)), true), "reload keeps retired replay fence");
        state.resetSession(); state.accept(first, true);
        for (int i = 0; i < 30; i++) check(state.tick(true, c -> true).isEmpty() && state.local().age() == 0, "pause freezes lifetime");
        for (int i = 1; i < 16; i++) check(state.tick(false, c -> true).isEmpty(), "bounded lifetime before sixteenth tick");
        check(state.tick(false, c -> true).size() == 1 && state.active().isEmpty(), "sixteenth tick expires exact input");
        check(!state.accept(packet(2, pose(0, false, 2, 4, 0)), true), "late receipt after natural expiry cannot revive");
        state.resetSession(); state.accept(first, true);
        check(state.tick(false, c -> false).size() == 1 && state.active().isEmpty(), "owner or context loss clears immediately");

        var blocked = new ShearWindbladeState();
        check(blocked.accept(packet(1, pose(0, false, 0, .25, 1)), true), "front stops at certified nearby blocker");
        check(blocked.accept(packet(2), true), "empty certified tick between blocked first front and delayed second");
        check(!blocked.accept(packet(3, pose(0, false, .25, 2.25, 0)), true), "blocked blade cannot resume");
        check(blocked.accept(packet(3, pose(1, false, 0, .25, 1)), true), "second independent front may still launch");

        var malformed = new ShearWindbladeState();
        check(!malformed.accept(packet(1, pose(0, false, 0, 2.01, 0)), true), "future leap beyond two metres rejected");
        check(!malformed.accept(packet(1, pose(0, false, 2, 0, 0)), true), "backwards flight rejected");
        check(!malformed.accept(packet(1, pose(0, false, 0, 2, 0), pose(0, false, 0, 2, 0)), true), "duplicate front rejected");
        var bent = new ArrayList<>(front(false, 2)); bent.set(3, new Point(0, 70, 0));
        check(!malformed.accept(packet(1, new ShearWindbladeState.Front(0, front(false, 0), bent, 0)), true), "full-sector or bent contour rejected");
        bent.set(3, new Point(Double.NaN, 64, 0));
        check(!malformed.accept(packet(1, new ShearWindbladeState.Front(0, front(false, 0), bent, 0)), true), "nonfinite front rejected");
        var impacts = new ShearWindbladeState();
        var actual = new ShearWindbladeState.Impact(0, 0, new Point(0, 64, 1), 1, 5);
        check(impacts.accept(packet(19, 1, 1, false, List.of(pose(0, false, 0, 2, 0)), List.of(actual)), true), "actual HP impact accepted");
        check(!impacts.accept(packet(19, 1, 2, false, List.of(pose(0, false, 2, 4, 0)), List.of(actual)), true), "native impact slot cannot repeat across fronts");
        check(new ShearWindbladeState().accept(packet(19, 1, 1, false, List.of(pose(0, false, 0, 2, 0)),
                List.of(new ShearWindbladeState.Impact(0, 0, new Point(0, 64, 1), 0, -1))), true), "refusal with no hit entity matches protocol but creates no dust");

        var cap = new ShearWindbladeState();
        for (int owner = 30; owner < 34; owner++) check(cap.accept(packet(owner, 1, 1, false, List.of(pose(0, false, 0, 2, 0)), List.of()), false), "remote admitted");
        check(cap.active().size() == 4 && cap.accept(first, true) && cap.active().size() == 4 && cap.local() != null, "four-controller cap reserves local priority");
        for (int owner = 34; owner < 40; owner++) cap.accept(packet(owner, 1, 1, false, List.of(pose(0, false, 0, 2, 0)), List.of()), false);
        check(cap.active().size() == 4 && cap.local().id().owner().equals(OWNER), "remote traffic never displaces local cast");
        var bounds = new ShearWindbladeState();
        for (int owner = 100; owner < 1124; owner++) check(bounds.accept(packet(owner, 1, 1, false, List.of(pose(0, false, 0, 2, 0)), List.of()), false), "bounded receipt watermark");
        check(!bounds.accept(packet(2000, 1, 1, false, List.of(pose(0, false, 0, 2, 0)), List.of()), false)
                && bounds.watermarkCount() == 1024, "watermark exhaustion fails closed without reopening evicted receipts");

        for (boolean vertical : List.of(false, true)) for (int receiptAge = 0; receiptAge < 8; receiptAge++) for (double partial : List.of(-1.0, 0.0, .25, .75, 1.0, 4.0)) {
            var fronts = List.of(pose(0, vertical, 4, 5.25, 1), pose(1, vertical, 0, 2, 0));
            var cast = new ShearWindbladeState.Controller(first.id(), true, vertical, ORIGIN, fronts, List.of(actual), 5, receiptAge);
            var shape = ShearWindbladeRenderGeometry.shape(cast, partial);
            check(shape.strokes().size() == 16, "every complete front retains exactly eight chords");
            check(shape.vertexCount(true) * 4 <= 2048 && shape.vertexCount(true) * 4 == 512, "four-controller entire-scene geometry stays within 2048 vertices");
            for (var line : shape.strokes()) {
                var f = fronts.get(line.blade());
                double low = f.previous().get(line.chord()).z(), high = f.current().get(line.chord()).z();
                check(line.from().z() >= low - 1e-9 && line.from().z() <= high + 1e-9, "no interpolation passes last certified blocker or forward endpoint");
                check(line.from().finite() && line.to().finite(), "finite contour points");
                var core = ShearWindbladeRenderGeometry.profile(line, true); var soft = ShearWindbladeRenderGeometry.profile(line, false);
                check(core.width() == .070 && core.gray() == 245 && soft.width() <= .150 && soft.alpha() <= core.alpha(), "readable neutral core with bounded decorative pressure edge");
            }
        }
        check(ShearWindbladeRenderGeometry.MAX_PARTICLES == 0 && ShearWindbladeRenderGeometry.AFTERIMAGES == 0, "no uncertified ground dust or false moving damage fronts");
        check(ShearWindbladeRenderGeometry.canUseShoulder(256, 512) && !ShearWindbladeRenderGeometry.canUseShoulder(256, 511), "budget fallback retains all core chords before soft materials");
        Point relative = ShearWindbladeRenderGeometry.relative(new Point(29_999_999.125, 64.5, -29_999_999.25), new Point(29_999_999, 64, -29_999_999));
        check(relative.equals(new Point(.125, .5, -.25)), "large world coordinates subtract camera before float conversion");
        var clipped = ShearWindbladeRenderGeometry.clipNear(new ShearWindbladeRenderGeometry.Stroke(new Point(0, 0, -.5), new Point(0, 0, 1), 0, 0, 200), new Point(0, 0, 1), .075);
        check(clipped != null && clipped.from().z() - .075 >= .08 - 1e-12, "entire pressure billboard remains in front of eye plane");
        check(ShearWindbladeRenderGeometry.nearEyeFade(.18) == 0 && ShearWindbladeRenderGeometry.nearEyeFade(.60) == 1, "eye-adjacent blade fragments fade without hiding distant fronts");
        verifyTexture();
        System.out.println("ShearWindbladeVisualRegressionTest: " + checks + " checks passed");
    }
    private static void verifyTexture() throws Exception {
        try (var stream = ShearWindbladeVisualRegressionTest.class.getResourceAsStream("/assets/academy/textures/effects/glow_line.png")) {
            check(stream != null, "shared pressure artwork available"); BufferedImage image = ImageIO.read(stream);
            double mean = 0;
            for (int i = 0; i < 1024; i++) {
                int y = (int)Math.floor((11.0 / 32 + 10.0 / 32 * (i + .5) / 1024) * image.getHeight());
                mean += (image.getRGB(image.getWidth() / 2, y) >>> 24) / 255.0;
            }
            double pixels = .070 * mean / 1024 * 238 / 255 * 720 / (2 * 8 * Math.tan(Math.toRadians(35)));
            check(pixels >= 2.8, "actual artwork core retains at least 2.8 pixels of alpha coverage at eight metres and default-size 720px view");
        }
    }
}

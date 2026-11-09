package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Native receipt provenance, finite pressure-path geometry, bounded lifetimes and world-edge precision. */
public final class DeflectionStrikeVisualRegressionTest {
    private static final UUID OWNER = new UUID(411, 1);
    private static final Point ORIGIN = new Point(0, 64, 0), FIRST = new Point(0, 64, 3), END = new Point(0, 64, 8), ZERO = new Point(0, 0, 0);
    private static int checks;
    private static void check(boolean condition, String name) { checks++; if (!condition) throw new AssertionError(name); }
    private static DeflectionStrikeState.Frame frame(long input, long token, long sequence, int slot, boolean crouch, Point from, Point end, int status) {
        return new DeflectionStrikeState.Frame(new DeflectionStrikeState.Identity(19, OWNER, 71, input, token), sequence, 0, slot, crouch, from, end, status);
    }
    private static DeflectionStrikeState.Frame ended(long input, long token, long sequence, boolean crouch) {
        return new DeflectionStrikeState.Frame(new DeflectionStrikeState.Identity(19, OWNER, 71, input, token), sequence, 1, 0, crouch, ZERO, ZERO, 0);
    }
    private static DeflectionStrikeState.Frame remote(int owner, int slot, long sequence, Point from, Point end) {
        return new DeflectionStrikeState.Frame(new DeflectionStrikeState.Identity(owner, new UUID(411, owner), 71, 1, 1), sequence, 0, slot, false, from, end, 1);
    }
    public static void main(String[] args) throws Exception {
        var state = new DeflectionStrikeState();
        var first = frame(1, 1, 1, 0, false, ORIGIN, FIRST, 1);
        var second = frame(1, 1, 2, 1, false, FIRST, END, 2);
        check(state.accept(first, true), "first authenticated attack contact admitted");
        check(!state.accept(first, true), "duplicate receipt cannot reanimate");
        check(state.accept(second, true), "second ordered contact merges into same controller");
        check(state.active().contacts().size() == 2 && state.active().origin().equals(ORIGIN) && state.active().end().equals(END), "one exact contiguous path and two native contacts");
        check(!state.accept(frame(1, 1, 3, 1, false, END, END, 1), true), "third contact cannot exceed two");
        check(state.accept(ended(1, 1, 3, false), true) && state.active() == null, "zero-geometry interruption closes cast");
        check(!state.accept(frame(1, 1, 4, 1, false, FIRST, END, 1), true), "late contact cannot revive terminal cast");
        check(state.accept(ended(2, 2, 1, false), true) && state.active() == null, "zero-target terminal acknowledges without inventing ring");
        check(!state.accept(frame(2, 2, 2, 0, false, ORIGIN, END, 1), true), "late FIRE after denied ack rejected");
        check(state.accept(frame(3, 3, 1, 1, false, ORIGIN, END, 0), true), "surviving slot1 allowed without inventing slot0");
        check(state.active().contacts().isEmpty(), "unconfirmed native outcome creates no dust source");
        check(!state.accept(frame(3, 3, 2, 0, false, ORIGIN, FIRST, 1), true), "slot0 cannot arrive backwards");
        check(!state.accept(frame(4, 3, 1, 0, false, ORIGIN, END, 1), true), "token reuse fenced across inputs");
        state.clear(); check(!state.accept(frame(3, 3, 2, 1, false, END, END, 1), true), "reload retains retired replay fence");
        state.cancel(71, 4); check(!state.accept(frame(4, 4, 1, 0, false, ORIGIN, END, 1), true), "cancelled input fenced");
        check(state.accept(frame(5, 5, 1, 0, true, ORIGIN, END, 3), true), "native crouch push creates one controller");
        for (int i = 0; i < 100; i++) check(state.tick(true, true) == null && state.active().age() == 0, "pause freezes lifetime");
        for (int i = 0; i < DeflectionStrikeState.LIFE_TICKS - 1; i++) check(state.tick(false, true) == null, "bounded visible life");
        check(state.tick(false, true) != null && state.active() == null, "twelfth active tick expires");
        check(!state.accept(frame(5, 5, 2, 1, true, END, END, 3), true), "late second receipt cannot revive expired cast");
        state.resetSession(); check(state.accept(first, true), "new connection resets identity fences");
        check(state.tick(false, false) != null && state.active() == null, "owner death/world/control loss clears immediately");

        var malformed = new DeflectionStrikeState();
        check(!malformed.accept(frame(1, 1, 1, 0, true, ORIGIN, END, 1), true), "crouch cannot claim damage feedback");
        check(!malformed.accept(frame(1, 1, 1, 0, false, ORIGIN, END, 3), true), "attack cannot claim crouch push");
        check(!malformed.accept(frame(1, 1, 1, 2, false, ORIGIN, END, 1), true), "slot outside two targets rejected");
        check(!malformed.accept(frame(1, 1, 1, 0, false, ORIGIN, new Point(0, 64, cn.academy.port.highpower.core.DeflectionStrikeRules.RANGE + .01), 1), true), "overlong segment rejected");
        check(!malformed.accept(frame(1, 1, 1, 0, false, ORIGIN, new Point(Double.NaN, 64, 0), 1), true), "NaN rejected");
        check(!malformed.accept(frame(1, 1, 1, 0, false, ORIGIN, new Point(0, Double.POSITIVE_INFINITY, 0), 1), true), "infinity rejected");
        check(malformed.accept(first, true), "valid first segment admitted after invalid packets");
        check(!malformed.accept(frame(1, 1, 2, 1, false, FIRST, new Point(1, 64, 7), 1), true), "second segment cannot bend");
        check(!malformed.accept(frame(1, 1, 2, 1, false, FIRST, new Point(0, 64, 2), 1), true), "second segment cannot reverse");
        check(!malformed.accept(frame(1, 1, 2, 1, false, new Point(0, 64, 4), END, 1), true), "second segment cannot create a gap");
        check(!malformed.accept(frame(1, 1, 2, 1, false, FIRST, new Point(0, 64, cn.academy.port.highpower.core.DeflectionStrikeRules.RANGE + 2), 1), true), "combined path cannot exceed the configured reach");

        var priority = new DeflectionStrikeState();
        check(priority.accept(first, true) && priority.accept(remote(2, 0, 1, ORIGIN, FIRST), false), "remote receipt still advances its replay fence");
        check(priority.active().id().owner().equals(OWNER), "local controller has priority over remote traffic");
        priority.clear();
        check(priority.accept(remote(2, 1, 2, FIRST, END), false), "next valid remote contact admitted after local closes");
        check(priority.accept(remote(3, 0, 1, ORIGIN, END), false), "new remote cast replaces one old controller");
        check(!priority.accept(remote(2, 1, 3, END, END), false), "retired remote controller cannot resurrect");
        var cap = new DeflectionStrikeState();
        for (int i = 1; i <= DeflectionStrikeState.MAX_WATERMARKS; i++) check(cap.accept(remote(i, 0, 1, ORIGIN, END), false), "bounded owner admitted");
        check(!cap.accept(remote(2000, 0, 1, ORIGIN, END), false) && cap.watermarkCount() == 1024, "replay capacity fails closed without evicting fences");

        var visual = new DeflectionStrikeState(); visual.accept(first, true); visual.accept(second, true);
        var attack = visual.active();
        var push = new DeflectionStrikeState.Controller(attack.id(), true, true, ORIGIN, END, List.of(new DeflectionStrikeState.Contact(END, 3)), 0);
        var refused = new DeflectionStrikeState.Controller(attack.id(), true, false, ORIGIN, END, List.of(), 0);
        check(DeflectionStrikeGeometry.MAX_CONTROLLERS == 1 && DeflectionStrikeGeometry.MAIN_LAYERS == 1 && DeflectionStrikeGeometry.AFTERIMAGES == 0, "single pressure layer without a portal stack");
        check(DeflectionStrikeGeometry.MATERIAL_LAYERS == 2 && DeflectionStrikeGeometry.MAX_PARTICLES == 16
                && DeflectionStrikeGeometry.MAX_VERTICES == 392, "two contour materials fit an explicit finite vertex and existing dust budget");
        for (double age = 0; age < 12; age += .25) for (var cast : List.of(attack, push, refused)) {
            var shape = DeflectionStrikeGeometry.shape(cast, age);
            check(shape.strokes().size() <= DeflectionStrikeGeometry.MAX_STROKES && shape.particles().size() <= 16
                    && shape.vertexCount() <= DeflectionStrikeGeometry.MAX_VERTICES, "geometry, material and particle budgets");
            for (var line : shape.strokes()) {
                check(line.from().finite() && line.to().finite() && line.width() > 0
                        && line.width() <= DeflectionStrikeGeometry.MAX_CORE_WIDTH && line.alpha() > 0, "finite white-gray pressure strokes");
                var core = DeflectionStrikeGeometry.profile(line, age, true);
                var shoulder = DeflectionStrikeGeometry.profile(line, age, false);
                check(core.width() == line.width() && shoulder.width() > core.width()
                        && shoulder.width() <= DeflectionStrikeGeometry.MAX_SHOULDER_WIDTH + 1e-12,
                        "soft edge has bounded support around the same crisp core");
                check(core.alpha() >= 0 && core.alpha() <= line.alpha() && shoulder.alpha() >= 0 && shoulder.alpha() <= line.alpha()
                        && core.gray() == 235 && shoulder.gray() < core.gray(), "coverage improves without raising peak brightness or alpha");
                if (line.role() == DeflectionStrikeGeometry.Role.HAND)
                    check(core.alpha() == 0 && shoulder.alpha() <= 60, "hand spends its eight vertices on a soft end-taper without a harsh core");
                if (cast.contacts().isEmpty()) check(line.role() != DeflectionStrikeGeometry.Role.CONTACT, "unconfirmed cast cannot acquire a contact material");
            }
            for (var dust : shape.particles()) check(dust.position().finite() && dust.size() > 0 && dust.alpha() > 0, "finite confirmed dust");
            check(!cast.contacts().isEmpty() || shape.particles().isEmpty(), "no speculative contact dust");
        }
        check(DeflectionStrikeGeometry.shape(attack, 0).particles().isEmpty(), "contact dust waits for traveling ring arrival");
        check(DeflectionStrikeGeometry.shape(attack, 5).particles().size() == 16, "two actual contacts produce sixteen dust motes");
        check(DeflectionStrikeGeometry.shape(push, 5).strokes().size() == 27, "open fan plus one confirmed force arrow");
        var hand = DeflectionStrikeGeometry.shape(refused, 3).strokes().subList(0, 3);
        check(hand.stream().allMatch(s -> s.role() == DeflectionStrikeGeometry.Role.HAND)
                && hand.getFirst().from().distanceSquared(new Point(-.13, 63.78, .14)) < 1e-20
                && hand.getFirst().to().distanceSquared(new Point(-.10, 63.78, .28)) < 1e-20
                && hand.get(1).from().distanceSquared(new Point(-.27, 63.78, .14)) < 1e-20
                && hand.get(1).to().distanceSquared(new Point(-.30, 63.78, .28)) < 1e-20
                && hand.get(2).from().distanceSquared(new Point(-.20, 63.78, .28)) < 1e-20
                && hand.get(2).to().distanceSquared(new Point(-.20, 63.78, .53)) < 1e-20
                && hand.getFirst().width() == .044 && hand.get(2).width() == .044 * .7,
                "near-eye correction preserves all existing hand endpoints and stroke widths");
        var handMaterial = DeflectionStrikeGeometry.profile(hand.getFirst(), 3, false);
        check(DeflectionStrikeGeometry.handAlpha(handMaterial.alpha(), 0, 2) == 0
                && DeflectionStrikeGeometry.handAlpha(handMaterial.alpha(), 1, 2) == 0
                && DeflectionStrikeGeometry.handAlpha(handMaterial.alpha(), .5, 2) == handMaterial.alpha(),
                "two hand half-quads taper to transparent ends with a bounded center");
        check(DeflectionStrikeGeometry.handAlpha(handMaterial.alpha(), .5, .35) == 0
                && DeflectionStrikeGeometry.handAlpha(handMaterial.alpha(), .5, .50) <= 5
                && DeflectionStrikeGeometry.handAlpha(handMaterial.alpha(), .5, 1.20) == handMaterial.alpha()
                && DeflectionStrikeGeometry.handNearEyeFade(Double.NaN) == 0,
                "near-eye hand rectangle is suppressed without fading distant pressure material");
        var contactAtEight = DeflectionStrikeGeometry.shape(push, 8).strokes().stream()
                .filter(s -> s.role() == DeflectionStrikeGeometry.Role.CONTACT).toList();
        check(contactAtEight.size() == 3 && Math.abs(contactAtEight.getFirst().width() - .008125) < 1e-12
                && contactAtEight.getFirst().alpha() == 93
                && DeflectionStrikeGeometry.profile(contactAtEight.getFirst(), 8, true).alpha() == 64,
                "confirmed contact geometry and its earlier material fade remain unchanged");
        var ring = DeflectionStrikeGeometry.shape(refused, 5).strokes();
        check(ring.size() == 32 && ring.getFirst().from().distanceSquared(ring.getLast().to()) < 1e-18, "attack narrow ring closes once");
        var fan = DeflectionStrikeGeometry.shape(push, 5).strokes();
        check(fan.getFirst().from().distanceSquared(fan.get(23).to()) > .5, "crouch fan has a deliberate open gap");
        check(ring.stream().allMatch(s -> Math.abs(s.from().z() - 8) < 1e-9), "ring is centered on exact server ray, no palm offset drift");
        check(Math.abs(ring.getFirst().from().distanceSquared(END) - .24 * .24) < 1e-12
                && Math.abs(fan.getFirst().from().distanceSquared(END) - .66 * .66) < 1e-12,
                "original ring and crouch fan radii remain exact");
        for (double age : List.of(0.0, 2.5, 5.0, 9.0, 11.75)) {
            var source = DeflectionStrikeGeometry.shape(refused, age).strokes();
            Point center = new Point(0, 64, 8 * Math.min(1, age / 5));
            double radius = .16 + .08 * Math.min(1, age / 5) - .04 * Math.max(0, (age - 5) / 7);
            int handStrokes = age < 5 ? 3 : 0;
            check(source.size() == 32 + handStrokes, "miss has only its immediate hand start and one pressure contour, without contact glints");
            for (int i = handStrokes; i < source.size(); i++)
                check(Math.abs(source.get(i).from().distanceSquared(center) - radius * radius) < 1e-12,
                        "material refinement preserves original travel timing and radial geometry");
        }
        check(DeflectionStrikeGeometry.shape(refused, 11).strokes().getFirst().width() < ring.getFirst().width(), "pressure thins as it settles");
        var late = DeflectionStrikeGeometry.shape(refused, 11).strokes().getFirst();
        check(DeflectionStrikeGeometry.profile(late, 11, false).alpha() > DeflectionStrikeGeometry.profile(late, 11, true).alpha(),
                "core resolves before its soft shoulder during the existing lifetime");
        for (double age : List.of(3.0, 5.0, 8.0)) {
            var wave = DeflectionStrikeGeometry.shape(refused, age).strokes().stream()
                    .filter(s -> s.role() == DeflectionStrikeGeometry.Role.WAVE).findFirst().orElseThrow();
            check(wave.width() == .080 && wave.alpha() == 215
                    && DeflectionStrikeGeometry.profile(wave, age, true).alpha() == 215
                    && DeflectionStrikeGeometry.profile(wave, age, false).width() == .160,
                    "traveling pressure band retains bounded readable support through age eight");
            verifyTextureCoverage(wave, age);
        }
        int previousCoreAlpha = 215;
        for (double age : List.of(9.0, 10.0, 11.0, 11.75)) {
            var fading = DeflectionStrikeGeometry.shape(refused, age).strokes().getFirst();
            int coreAlpha = DeflectionStrikeGeometry.profile(fading, age, true).alpha();
            check(coreAlpha < previousCoreAlpha, "pressure core dissolves monotonically during its final four ticks");
            previousCoreAlpha = coreAlpha;
        }
        verifySharedTextureFiltering();
        check(DeflectionStrikeGeometry.shape(attack, 12).strokes().isEmpty() && DeflectionStrikeGeometry.shape(attack, Double.NaN).particles().isEmpty(), "expired/nonfinite time draws nothing");
        Point relative = DeflectionStrikeGeometry.relative(new Point(29_999_999.125, 64.5, -29_999_999.25), new Point(29_999_999, 64, -29_999_999));
        check(relative.equals(new Point(.125, .5, -.25)), "large world coordinate subtraction precedes float conversion");
        var clipped = DeflectionStrikeGeometry.clipNear(new DeflectionStrikeGeometry.Stroke(new Point(0, 0, -.5), new Point(0, 0, 1), .01, 100), new Point(0, 0, 1));
        check(clipped != null && Math.abs(clipped.from().z() - .08) < 1e-9, "crossing near plane clips cleanly");
        var shoulderClipped = DeflectionStrikeGeometry.clipNear(new DeflectionStrikeGeometry.Stroke(
                new Point(0, 0, -.5), new Point(0, 0, 1), .160, 100), new Point(0, 0, 1), .160 / 2);
        check(shoulderClipped != null && shoulderClipped.from().z() - .160 / 2 >= DeflectionStrikeGeometry.NEAR_PLANE - 1e-12,
                "wide soft shoulder reserves its full half-width at the near plane");
        check(DeflectionStrikeGeometry.clipNear(new DeflectionStrikeGeometry.Stroke(new Point(0, 0, -.5), new Point(0, 0, -.1), .01, 100), new Point(0, 0, 1)) == null, "behind-eye strokes cannot invert");
        System.out.println("DeflectionStrikeVisualRegressionTest: " + checks + " checks passed");
    }
    private static void verifyTextureCoverage(DeflectionStrikeGeometry.Stroke stroke, double age) throws Exception {
        try (var stream = DeflectionStrikeVisualRegressionTest.class.getResourceAsStream("/assets/academy/textures/effects/glow_line.png")) {
            check(stream != null, "original pressure artwork is available");
            BufferedImage texture = ImageIO.read(stream);
            var core = DeflectionStrikeGeometry.profile(stroke, age, true);
            double baselineCoverage = .022 * meanAlpha(texture, 0, 1) * 215 / 255;
            double coreCoverage = core.width() * meanAlpha(texture, core.minV(), core.maxV()) * core.alpha() / 255;
            check(coreCoverage >= baselineCoverage * 8, "actual source alpha provides at least eight times the former subpixel core coverage");
            double pixelsAtEightMeters = coreCoverage * 720 / (2 * 8 * Math.tan(Math.toRadians(35)));
            check(pixelsAtEightMeters >= 3.25, "core retains readable alpha coverage through age eight at eight meters, 70 degree FOV and 720 pixel height");
        }
    }
    /** Match the unchanged shared texture's nearest sampling, including its transparent margins. */
    private static double meanAlpha(BufferedImage texture, double minV, double maxV) {
        double alpha = 0;
        for (int i = 0; i < 1024; i++) {
            double y = (minV + (maxV - minV) * (i + .5) / 1024) * texture.getHeight();
            int row = Math.max(0, Math.min(texture.getHeight() - 1, (int)Math.floor(y)));
            alpha += (texture.getRGB(texture.getWidth() / 2, row) >>> 24) / 255.0;
        }
        return alpha / 1024;
    }
    private static void verifySharedTextureFiltering() throws Exception {
        try (var stream = DeflectionStrikeVisualRegressionTest.class.getResourceAsStream(
                "/cn/academy/port/client/DeflectionStrikeEffects$Material.class")) {
            check(stream != null, "compiled pressure material is available for shared-texture checks");
            var material = new ClassNode(); new ClassReader(stream).accept(material, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            int textureStates = 0;
            for (var method : material.methods) for (var instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call)) continue;
                check(!call.name.equals("setFilter"), "pressure material never directly mutates a shared texture filter");
                if (!call.owner.endsWith("$TextureStateShard") || !call.name.equals("<init>")) continue;
                textureStates++;
                var mipmap = previousOpcode(call); var blur = previousOpcode(mipmap);
                check(mipmap != null && blur != null && mipmap.getOpcode() == Opcodes.ICONST_0 && blur.getOpcode() == Opcodes.ICONST_0,
                        "every pressure/dust pass preserves the shared original false/false filter flags");
            }
            check(textureStates == 1, "all pressure/dust passes use the single verified shared-texture state factory");
        }
    }
    private static AbstractInsnNode previousOpcode(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        do { instruction = instruction.getPrevious(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }
}

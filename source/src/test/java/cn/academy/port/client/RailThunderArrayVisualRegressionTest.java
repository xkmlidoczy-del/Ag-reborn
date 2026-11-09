package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Executes NEW151's real edge, receipt, clock and geometry helpers. In-game appearance is a separate check. */
public final class RailThunderArrayVisualRegressionTest {
    private static int checks;
    private static void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
    private static UUID id(int n) { return new UUID(0, n); }
    private static RailThunderArrayGeometry.Point p(double x, double y, double z) { return new RailThunderArrayGeometry.Point(x, y, z); }
    private static final List<RailThunderArrayGeometry.Point> AXES = List.of(p(0, 0, 1), p(.6, 0, .8), p(0, .6, .8));
    private static RailThunderArrayState.Segment segment(int axis, int status) {
        boolean target = status == RailThunderArrayState.HIT || status == RailThunderArrayState.REFUSED;
        return new RailThunderArrayState.Segment(axis, target ? 30 + axis : -1, target ? id(30 + axis) : RailThunderArrayState.NO_TARGET,
                p(0, 0, 0), AXES.get(axis).scale(48), status);
    }
    private static RailThunderArrayState.Frame frame(int owner, long epoch, long input, long token, long sequence, int phase, int age, int axes, List<RailThunderArrayState.Segment> segments) {
        return new RailThunderArrayState.Frame(new RailThunderArrayState.Identity(owner, id(owner), epoch, input, token), sequence, phase, age, p(0, 0, 0), AXES.subList(0, axes), segments, 0);
    }
    private static RailThunderArrayState.Frame f(long input, long sequence, int phase, int age, int axes, List<RailThunderArrayState.Segment> segments) { return frame(1, 2, input, 5, sequence, phase, age, axes, segments); }
    private static double visibleArea(RailThunderArrayGeometry.Point from, RailThunderArrayGeometry.Point to, double width) {
        var forward = p(0, 0, 1); var up = p(0, 1, 0);
        var clipped = RailThunderArrayGeometry.nearClip(from, to, forward, width);
        double area = 0;
        for (var segment : RailThunderArrayGeometry.clearCenter(clipped, forward, up, width)) {
            var ribbon = RailThunderArrayGeometry.ribbon(segment, forward, width);
            area += RailThunderArrayGeometry.projectedArea(ribbon, forward, up);
            if (ribbon != null) for (var vertex : List.of(ribbon.start().plus(ribbon.side()), ribbon.start().minus(ribbon.side()),
                    ribbon.end().plus(ribbon.side()), ribbon.end().minus(ribbon.side()))) {
                check(vertex.z() >= RailThunderArrayGeometry.NEAR_DEPTH - 1e-8, "whole first-person ribbon stays beyond the near plane");
                check(Math.max(Math.abs(vertex.x()), Math.abs(vertex.y())) / vertex.z() >= RailThunderArrayGeometry.CENTER_CLEAR - 1e-8,
                        "virtual wrist keeps the entire aiming center clear");
            }
        }
        return area;
    }
    public static void main(String[] args) throws Exception {
        var input = new RailThunderArrayInputState();
        for (int i = 1; i <= 3; i++) {
            var press = new HighPowerInput.Input(2, i, 9, 2); input.begin(press, 19);
            check(input.current().identity().equals(press) && input.holding(), "capture three independent preparatory physical presses");
            check(input.releaseWhenUp(true) == null && input.holding(), "held preparation cannot locally become a fire mode");
            check(input.release(press).equals(press) && !input.holding(), "each preparatory release has exactly one authenticated edge");
            check(input.releaseWhenUp(false) == null, "snapshot fallback cannot duplicate physical release");
            input.accepted(i, 2); check(input.permits(i, 2), "server-observed mark remains permitted after release");
        }
        var fourth = new HighPowerInput.Input(2, 4, 9, 2); input.begin(fourth, -100);
        check(input.permits(3, 2) && input.permits(4, 2), "fourth request preserves previous three marks until server response");
        for (int i = 0; i < 80; i++) check(input.releaseWhenUp(true) == null && input.holding(), "client never predicts eight-tick tap/hold classification");
        check(input.release(new HighPowerInput.Input(2, 3, 9, 2)) == null, "earlier mark cannot release fourth press");
        check(input.releaseWhenUp(false).equals(fourth) && input.release(fourth) == null, "physical release survives silent snapshot and is sent once");
        input.accepted(4, 2); check(!input.permits(3, 2) && input.permits(4, 2), "accepted fourth input retires previous input identities");
        check(input.cancelWhenBlocked(false) == null && input.current() != null, "release alone does not cancel active tap volley");
        check(input.cancelWhenBlocked(true).equals(fourth) && !input.permits(4, 2), "GUI/focus cancellation clears all local marks and pending cast");
        for (int i = 1; i <= 80; i++) input.begin(new HighPowerInput.Input(0, i, 9, 3), 19);
        check(input.pendingCount() == 16 && !input.permits(1, 3), "pending request storage has hard ceiling");
        input.accepted(80, 3); input.ended(80, 3); check(!input.permits(80, 3), "server termination closes receipt permission");
        input.begin(new HighPowerInput.Input(0, 1, 10, 4), 19); check(!input.permits(80, 3), "new actor/epoch replaces old pending requests");
        input.reset(); check(input.current() == null && !input.holding() && input.pendingCount() == 0, "session reset retains no physical or accepted identity");
        boolean invalid = false; try { input.begin(new HighPowerInput.Input(4, 1, 9, 2), 19); } catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, "invalid fifth slot refused");

        var state = new RailThunderArrayState();
        check(state.accept(f(1, 1, 0, 0, 0, List.of()), true), "first press starts free preparation without fake marks");
        for (int i = 1; i <= 3; i++) check(state.accept(f(i, i + 1, 0, i * 4, i, List.of(segment(i - 1, 0))), true), "three server-observed direction marks append to same token");
        check(!state.accept(f(3, 4, 0, 12, 3, List.of()), true), "duplicate cannot renew lease");
        check(!state.accept(f(2, 5, 0, 16, 3, List.of()), true), "late prior input cannot rewind preparation");
        var refusedMark = new RailThunderArrayState.Frame(f(3, 5, 0, 16, 3, List.of(segment(2, 0))).id(), 5, 0, 16, p(0, 0, 0), AXES, List.of(segment(2, 0)), 1);
        check(RailThunderArrayState.valid(refusedMark), "duplicate-direction refusal preserves existing unpaid preview");
        var changedAxis = new RailThunderArrayState.Frame(f(3, 5, 0, 16, 3, List.of()).id(), 5, 0, 16, p(0, 0, 0), List.of(p(1, 0, 0), AXES.get(1), AXES.get(2)), List.of(), 0);
        check(!state.accept(changedAxis, true), "camera rotation cannot replace captured server axis");
        check(!state.accept(f(3, 5, 0, 16, 2, List.of()), true), "partial preview cannot erase mark prefix");
        check(state.accept(f(4, 5, 1, 16, 3, List.of(segment(2, 0))), true), "only fourth input enters armed stage");
        check(!state.accept(f(5, 6, 1, 17, 3, List.of()), true), "armed cast cannot adopt another input");
        check(state.accept(f(4, 6, 2, 17, 3, List.of(segment(0, 2))), true), "short-release server phase is tap with confirmed hit");
        check(!state.accept(f(4, 7, 3, 24, 3, List.of(segment(0, 2))), true), "tap cannot turn into held electric net");
        check(!state.accept(f(4, 7, 0, 18, 3, List.of(segment(0, 0))), true), "late preview cannot replace confirmed emission");
        var movedOrigin = new RailThunderArrayState.Frame(f(4, 7, 2, 18, 3, List.of()).id(), 7, 2, 18, p(.5, 0, 0), AXES, List.of(), 0);
        check(!state.accept(movedOrigin, true), "committed server origin cannot move within volley");
        check(state.accept(f(4, 7, 2, 20, 3, List.of()), true), "empty keepalive does not invent an emission");
        check(state.accept(f(4, 8, 4, 63, 3, List.of()), true) && state.size() == 0, "normal terminal retires controller token");
        check(!state.accept(f(4, 999, 2, 64, 3, List.of(segment(1, 2))), true), "late confirmed packet cannot resurrect terminated volley");
        check(!state.accept(f(4, 999, 4, 64, 3, List.of()), true), "replayed terminal cannot restart flashes");
        state.resetSession(); check(state.accept(f(4, 1, 3, 24, 3, List.of(segment(1, 2))), false), "observer can first see server-confirmed hold");
        check(!state.accept(f(4, 2, 2, 25, 3, List.of(segment(1, 2))), false), "held net cannot convert to tap");
        check(!state.accept(frame(1, 3, 4, 5, 2, 3, 25, 3, List.of()), false), "same token cannot change owner epoch");
        state.cancel(2, 20); state.cancel(1, 999);
        check(!state.accept(frame(1, 2, 20, 6, 1, 0, 0, 0, List.of()), true), "cancel before receipt fences all previous marks without epoch rewind");
        check(state.accept(frame(1, 2, 21, 6, 1, 0, 0, 0, List.of()), true), "new physical input may create new token");
        state.clear(); check(!state.accept(frame(1, 2, 21, 6, 2, 0, 1, 1, List.of(segment(0, 0))), true), "explicit clear preserves same-session replay fence");
        state.resetSession(); check(state.accept(f(1, 1, 0, 0, 0, List.of()), true), "connection replacement resets transport session");
        check(!RailThunderArrayState.valid(f(1, 1, 0, 0, 1, List.of(segment(0, 2)))), "unpaid preview cannot claim confirmed hit");
        check(!RailThunderArrayState.valid(f(4, 1, 2, 20, 3, List.of(segment(0, 0)))), "active receipt cannot contain unpaid preview");
        check(!RailThunderArrayState.valid(f(4, 1, 2, 20, 2, List.of())), "active array needs all three axes");
        check(!RailThunderArrayState.valid(f(4, 1, 4, 20, 3, List.of(segment(0, 2)))), "terminal has no extra emission");
        check(!RailThunderArrayState.valid(f(1, 1, 0, 201, 1, List.of())), "preparation hard lifetime is200ticks");
        check(!RailThunderArrayState.valid(f(4, 1, 3, 269, 3, List.of())), "total render lifetime cannot exceed268ticks");
        var distant = new RailThunderArrayState.Segment(0, -1, RailThunderArrayState.NO_TARGET, p(0, 0, 0), p(0, 0, 96.1), 1);
        check(!RailThunderArrayState.valid(f(4, 1, 2, 20, 3, List.of(distant))), "server endpoint beyond96m refused");
        var fakeHit = new RailThunderArrayState.Segment(0, -1, RailThunderArrayState.NO_TARGET, p(0, 0, 0), p(0, 0, 12), 2);
        check(!RailThunderArrayState.valid(f(4, 1, 2, 20, 3, List.of(fakeHit))), "entity-free ray cannot produce target hit flash");
        check(!RailThunderArrayState.valid(f(4, 1, 3, 20, 3, Collections.nCopies(17, segment(0, 2)))), "seventeenth pulse segment refused");
        state.resetSession();
        for (int i = 100; i < 164; i++) check(state.accept(frame(i, 2, 1, 5, 1, 0, 0, 0, List.of()), false), "bounded observer context");
        check(!state.accept(frame(164, 2, 1, 5, 1, 0, 0, 0, List.of()), false) && state.size() == 64, "strict64controller cap");
        state.resetSession();
        for (int i = 100; i < 1124; i++) check(state.accept(frame(i, 2, 1, 5, 1, 4, 0, 0, List.of()), false), "bounded lifetime watermark");
        check(state.watermarkCount() == 1024 && !state.accept(frame(1124, 2, 1, 5, 1, 0, 0, 0, List.of()), false), "watermark capacity fails closed");
        check(!state.accept(frame(100, 2, 1, 5, 99, 0, 0, 0, List.of()), false), "capacity never evicts old replay fence");

        var lease = new RailThunderArrayState.Lease(20);
        for (int i = 0; i < 500; i++) check(lease.tick(true, true) && lease.age() == 20 && lease.quiet() == 0, "pause freezes bounded render clocks");
        check(!lease.tick(true, false), "GUI or focus invalidates even while paused");
        lease = new RailThunderArrayState.Lease(0);
        for (int tick = 1; tick < 268; tick++) { if (tick % 6 == 0) lease.received(tick, false); check(lease.tick(false, true), "six-tick heartbeat retains finite preparatory and active context"); }
        check(lease.age() == 268 && !lease.tick(false, true), "absolute age fence cannot be extended by heartbeats");
        lease = new RailThunderArrayState.Lease(50); for (int i = 1; i < 12; i++) check(lease.tick(false, true), "quiet grace finite");
        check(!lease.tick(false, true), "missing keepalive clears at12ticks");
        lease = new RailThunderArrayState.Lease(50); lease.received(40, true); check(lease.age() == 50, "late receipt never rewinds display age");
        for (int i = 1; i < 5; i++) check(lease.tick(false, true), "normal ending keeps only finite existing flashes");
        check(!lease.tick(false, true), "terminal flash ceiling5ticks");

        for (var direction : List.of(p(1, 0, 0), p(0, 1, 0), p(0, -1, 0), p(0, 0, 1), p(1, 2, 3))) {
            var axis = direction.scale(1 / direction.length()); var from = p(3, 8, 6);
            for (double distance : List.of(.1, 12.0, 48.0, 96.0)) {
                var to = from.plus(axis.scale(distance)); var rail = RailThunderArrayGeometry.rail(from, to);
                check(rail.size() == 3 && rail.getFirst().segment().start().equals(from) && rail.getLast().segment().end().equals(to), "classic asset caps preserve real96mclipped endpoints");
                for (int j = 0; j < rail.size(); j++) check(rail.get(j).texture() == j, "classic blend in tile blend out reuse");
                var net = RailThunderArrayGeometry.branch(from, to, 19, 1);
                check(net.size() <= 24 && net.getFirst().start().equals(from) && net.getLast().end().equals(to), "electric net is bounded segments along true accepted corridor");
                for (var segment : net) for (var vertex : List.of(segment.start(), segment.end())) {
                    double along = vertex.minus(from).dot(axis), off = vertex.minus(from).minus(axis.scale(along)).length();
                    check(vertex.finite() && along >= -1e-7 && along <= distance + 1e-7 && off <= .056, "net has small3Doffset without opaque volume");
                }
                check(RailThunderArrayGeometry.sideBranches(from, to, 19, 1).size() <= 4, "two short sidebranches have bounded ribbons");
            }
        }
        check(RailThunderArrayGeometry.branch(p(0, 0, 0), p(0, 0, 96.1), 1, 0).isEmpty(), "invalid96mgeometry empty");
        check(RailThunderArrayGeometry.rail(p(0, 0, 0), p(0, 0, 0)).isEmpty(), "degenerate rail empty");
        check(RailThunderArrayGeometry.rail(p(Double.NaN, 0, 0), p(0, 0, 1)).isEmpty(), "nonfinite rail empty");
        var forward = p(0, 0, 1); var up = p(0, 1, 0);
        check(RailThunderArrayGeometry.nearClip(p(1, 1, -1), p(2, 2, -3), forward, .045) == null, "behind-camera emission culled");
        var clipped = RailThunderArrayGeometry.nearClip(p(-1, 0, -1), p(1, 0, 1), forward, .045);
        check(clipped != null && clipped.start().z() >= .245 - 1e-8, "full halo near-plane clipping");
        check(RailThunderArrayGeometry.clearCenter(new RailThunderArrayGeometry.Segment(p(0, 0, 1), p(0, 0, 96)), forward, up, .045).isEmpty(), "aiming center empty along96mrail");
        for (double width : List.of(.0025, .008, .045)) {
            var split = RailThunderArrayGeometry.clearCenter(new RailThunderArrayGeometry.Segment(p(-2, 0, 10), p(2, 0, 10)), forward, up, width);
            check(split.size() == 2 && split.get(0).end().x() <= -.22 - width + 1e-8 && split.get(1).start().x() >= .22 + width - 1e-8, "center clear even at full ribbon width");
            for (var segment : split) { var ribbon = RailThunderArrayGeometry.ribbon(segment, forward, width); check(ribbon != null && ribbon.side().finite() && ribbon.halfWidth() <= width, "finite thin camera-facing ribbon"); }
        }
        for (boolean rightHanded : List.of(false, true)) for (double angle : List.of(0.0, 1.0, 3.0)) for (double distance : List.of(2.0, 10.0, 32.0, 96.0)) {
            var camera = p(0, 0, 0);
            var endpoint = p(Math.sin(Math.toRadians(angle)) * distance, 0, Math.cos(Math.toRadians(angle)) * distance);
            check(visibleArea(camera, endpoint, RailThunderArrayGeometry.PREVIEW_WIDTH) < 1e-12, "eye-origin preview degeneracy reproduced at0/1/3degrees");
            var wrist = RailThunderArrayGeometry.firstPersonOrigin(camera, endpoint, camera, forward, up, rightHanded);
            check(Math.abs(wrist.z() - .65) < 1e-9 && Math.abs(Math.abs(wrist.x()) - .32) < 1e-9 && Math.abs(wrist.y() + .29) < 1e-9,
                    "local cosmetic launch uses the existing small virtual-wrist convention");
            check(wrist.length() < .8 && wrist.z() > RailThunderArrayGeometry.NEAR_DEPTH + RailThunderArrayGeometry.MAX_HALF_WIDTH,
                    "virtual wrist is bounded and never clips its launch at the camera");
            check(visibleArea(wrist, endpoint, RailThunderArrayGeometry.PREVIEW_WIDTH) > 1e-6, "marked0/1/3degree preview has visible projected area");
            check(visibleArea(wrist, endpoint, RailThunderArrayGeometry.RAIL_CORE_WIDTH) > 1e-6, "confirmed0/1/3degree rail core has visible projected area");
            double texturedArea = 0; var parts = RailThunderArrayGeometry.rail(wrist, endpoint);
            check(parts.getLast().segment().end().equals(endpoint), "virtual launch preserves the exact server-clipped terrain endpoint");
            for (var part : parts) {
                texturedArea += visibleArea(part.segment().start(), part.segment().end(), RailThunderArrayGeometry.RAIL_HALO_WIDTH);
                var direction = endpoint.minus(wrist); double length = direction.length(); direction = direction.scale(1 / length);
                for (var vertex : List.of(part.segment().start(), part.segment().end())) {
                    double along = vertex.minus(wrist).dot(direction);
                    check(along >= -1e-7 && along <= length + 1e-7, "classic caps never extend beyond the clipped endpoint or virtual launch");
                }
            }
            check(texturedArea > 1e-6, "actual classic-textured rail parts have visible projected area");
        }
        for (double yaw : List.of(0.0, 90.0, 180.0, 270.0)) for (boolean rightHanded : List.of(false, true)) {
            var facing = p(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
            var cameraUp = p(0, 1, 0); var screenRight = facing.cross(cameraUp);
            var camera = p(4.5, 201.62, 4.5); var endpoint = camera.plus(facing.scale(10));
            var wrist = RailThunderArrayGeometry.firstPersonOrigin(camera, endpoint, camera, facing, cameraUp, rightHanded);
            var offset = wrist.minus(camera); double projectedRight = offset.dot(screenRight) / offset.dot(facing);
            check(rightHanded ? projectedRight > 0 : projectedRight < 0, "configured hand appears on its actual screen side at yaw" + yaw);
            check(Math.abs(offset.dot(screenRight) - (rightHanded ? .32 : -.32)) < 1e-9,
                    "semantic screen-right and screen-left wrist displacement retain the correct sign");
            check(Math.abs(offset.dot(cameraUp) + .29) < 1e-9 && Math.abs(offset.dot(facing) - .65) < 1e-9,
                    "handedness correction preserves bounded downward and forward launch offsets");
        }
        var exactWallEndpoint = p(0, 0, 2);
        check(RailThunderArrayGeometry.firstPersonOrigin(p(0, 0, 0), p(0, 0, -.1), p(0, 0, 0), forward, up, true).equals(p(0, 0, 0)),
                "virtual wrist cannot manufacture an in-front path to a behind-camera endpoint");
        check(RailThunderArrayGeometry.firstPersonOrigin(p(0, 0, 0), exactWallEndpoint, p(0, 0, 2), forward, up, true).equals(p(0, 0, 0)),
                "distant observer camera cannot acquire the local virtual wrist");
        check(RailThunderArrayGeometry.PREVIEW_ALPHA < RailThunderArrayGeometry.EMISSION_ALPHA / 3
                && RailThunderArrayGeometry.PREVIEW_WIDTH < RailThunderArrayGeometry.RAIL_CORE_WIDTH, "preview is materially fainter and thinner than emission");
        check(RailThunderArrayGeometry.ORANGE == 0xECAA5D && RailThunderArrayGeometry.IVORY == 0xF1F0DE
                && RailThunderArrayGeometry.BLUE != RailThunderArrayGeometry.ORANGE, "classic orange ivory axes differ from bluewhite electric net");
        check(RailThunderArrayGeometry.MAX_PARTICLES == 128, "strict approved128particle ceiling");

        String client = Files.readString(Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        String effects = Files.readString(Path.of("src/main/java/cn/academy/port/client/RailThunderArrayEffects.java"));
        check(client.contains("railArrayInputs.begin(identity,node.key)") && client.contains("request(\"hp_rail_thunder_array_press\",identity.wire())"), "press carries authenticated identity only");
        check(client.contains("railArrayInputs.release(identity)") && client.contains("railArrayInputs.releaseWhenUp(physicalDown(arrayHeld.physicalKey()))"), "physical snapshot-safe release wired");
        check(client.contains("railArrayInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null)") && client.contains("railArrayInputs.reset();RailThunderArrayEffects.clear()"), "GUI including item selector cancels and session clears every receipt");
        String cleanup = client.substring(client.indexOf("private static void cleanupModern("), client.indexOf("private static void modernSnapshotFallback("));
        check(!cleanup.contains("railArray"), "ordinary CP snapshot cannot cancel marked/held array");
        check(effects.contains("mc.level != captured") && effects.contains("mc.player != capturedPlayer") && effects.contains("mc.getConnection() != capturedConnection")
                && effects.contains("clearGeneration != generation") && effects.contains("sameInputSessionEpoch(capturedSession)"), "queued callbacks bound to current level player connection generation and session");
        check(effects.contains("railArrayInputMatches(packet.input(), packet.epoch())") && effects.contains("owner.getUUID().equals(packet.owner())"), "server receipt also requires authenticated ownerUUID and current input");
        check(effects.contains("marker.lease.tick(paused, validOwner(marker, mc))") && effects.contains("world.getEntity(marker.owner.getId()) == marker.owner"), "renderer executes tested pause and entity lifecycle");
        check(effects.contains("effects/railgun/blend_in") && effects.contains("effects/railgun/tile") && effects.contains("effects/railgun/blend_out"), "reuse original rail assets");
        check(effects.contains("MAX_CONTEXTS = 64, MAX_VISIBLE_CONTEXTS = 8, MAX_FLASHES = 16") && !effects.contains("addParticle")
                && !effects.contains("setDeltaMovement") && !effects.contains("setPos(") && !effects.contains("PacketDistributor"), "renderer owns bounded ribbons and zero particle entities or combat mutation");
        check(effects.contains("world.hasChunkAt") && effects.contains("ClipContext.Block.COLLIDER") && effects.contains("ClassicRenderTypes.world(texture)") && effects.contains("modelView.popMatrix()"), "depth-tested finite world rendering restores matrix and suppresses blockers");
        check(effects.contains("marker.owner != mc.player || !mc.options.getCameraType().isFirstPerson()")
                && effects.contains("!segment.from().equals(marker.state.origin())") && effects.contains("firstPersonOrigin(point(segment.from()), point(segment.to())"),
                "virtual launch is local-first-person-only and only replaces a server-origin visual segment");
        check(effects.contains("PREVIEW && visible(marker.owner, segment.from(), segment.to())")
                && effects.contains("!visible(marker.owner, segment.from(), segment.to())") && effects.contains("!visible(owner, from, to)"),
                "both authoritative origin LOS and cosmetic path LOS must pass; wrist cannot bypass a wall");
        check(!effects.contains("DIAGNOSTICS") && !effects.contains("DIAGNOSTIC_LOG") && !effects.contains("renderProbe")
                && !effects.contains("rail_array_visual_diagnostics"), "temporary receipt and render instrumentation removed from production");
        byte[] original = Files.readAllBytes(Path.of("src/main/resources/assets/academy/textures/abilities/electromaster/skills/railgun.png"));
        check(Arrays.equals(original, Files.readAllBytes(Path.of("src/main/resources/assets/academy/textures/abilities/electromaster/skills/rail_thunder_array.png"))), "licensed classic rail icon preserved byte-for-byte");
        for (String locale : List.of("en_us", "zh_cn", "zh_tw", "ja_jp")) {
            String lang = Files.readString(Path.of("src/main/resources/assets/academy/lang/" + locale + ".json"));
            for (String key : List.of("ac.ability.electromaster.rail_thunder_array.name", "ac.ability.electromaster.rail_thunder_array.desc", "academy.highpower.rail_thunder_array.marks", "academy.highpower.rail_thunder_array.mode_tap", "academy.highpower.rail_thunder_array.mode_hold"))
                check(lang.contains("\"" + key + "\""), "localizedNEW151 UI in" + locale);
        }
        System.out.println("PASS " + checks + " NEW151 input, authoritative stages, replay, pause, range, classic palette and bounded geometry checks; rendered appearance requires in-game review");
    }
}

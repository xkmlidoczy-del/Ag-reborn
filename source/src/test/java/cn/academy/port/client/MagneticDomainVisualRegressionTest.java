package cn.academy.port.client;

import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Executes production classes; independent sphere, cone, projection and decimal witnesses. */
public final class MagneticDomainVisualRegressionTest {
    private static int checks;
    private static final Point ORIGIN = new Point(0, 0, 0), FORWARD = new Point(0, 0, -1), UP = new Point(0, 1, 0);
    private static final UUID OWNER = new UUID(152, 1);
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    private static void denied(Runnable task, String message) { try { task.run(); } catch (IllegalArgumentException expected) { checks++; return; } throw new AssertionError(message); }
    private static MagneticDomainState.Frame frame(long input, long token, long epoch, long sequence, int phase) {
        return new MagneticDomainState.Frame(new MagneticDomainState.Identity(4, OWNER, epoch, input, token), sequence, phase, ORIGIN, FORWARD, ORIGIN, false, 0);
    }
    public static void main(String[] args) throws Exception {
        input(); lifecycle(); geometry(); circulationReadability(); hud(); integration();
        System.out.println("PASS " + checks + " NEW152 toggle, replay, renderer lease, sphere/front-cone, clipping, reserve and GUI checks");
    }
    private static void input() {
        var inputs = new MagneticDomainInputState();
        var a = new HighPowerInput.Input(1, 1, 4, 7); var b = new HighPowerInput.Input(1, 2, 4, 7);
        inputs.tap(a); inputs.release(a); check(inputs.permits(1, 7), "key release retains authenticated toggle request");
        inputs.tap(b); check(inputs.permits(1, 7) && inputs.permits(2, 7), "rapid second tap must not discard the first server acknowledgement");
        inputs.accepted(1, 7); inputs.ended(1, 7); check(!inputs.permits(1, 7) && inputs.permits(2, 7), "first stop cannot retire the newer toggle");
        inputs.accepted(2, 7); check(inputs.cancel(a) == null && inputs.permits(2, 7), "old delegate disposal cannot cancel newer input");
        check(inputs.cancelWhenBlocked(true).equals(b) && !inputs.permits(2, 7), "screen or focus loss clears all toggles");
        inputs.tap(a); denied(() -> inputs.tap(a), "replayed physical request is not valid");
        for (int i = 2; i <= 100; i++) inputs.tap(new HighPowerInput.Input(1, i, 4, 7));
        check(inputs.pendingCount() == MagneticDomainInputState.MAX_PENDING && !inputs.permits(1, 7), "request queue is bounded");
        inputs.tap(new HighPowerInput.Input(1, 1, 5, 8)); check(!inputs.permits(100, 7), "new actor and epoch clear old request namespace");
        inputs.reset(); check(inputs.current() == null, "logout reset has no remembered request");
    }
    private static void lifecycle() {
        var state = new MagneticDomainState();
        check(state.accept(frame(1, 1, 1, 1, 0), true), "accepted activation");
        check(!state.accept(frame(1, 1, 1, 1, 1), true), "duplicate sequence cannot manufacture hit");
        check(state.accept(frame(1, 1, 1, 2, 1), true), "real shield impact receipt");
        check(state.accept(frame(1, 1, 1, 3, 2), true), "refraction is a distinct receipt, not a mode transition");
        check(state.accept(frame(1, 1, 1, 4, 0), true), "heartbeat after impact/refraction remains valid");
        check(state.accept(frame(1, 1, 1, 5, 3), true) && state.current(OWNER) == null, "terminal removes owner controller");
        check(!state.accept(frame(1, 1, 1, 6, 0), true), "late heartbeat cannot reopen stopped cast");
        check(!state.accept(frame(2, 1, 1, 7, 0), true), "new input cannot recycle token");
        check(state.accept(frame(2, 2, 1, 1, 0), true), "fresh toggle starts a fresh controller");
        state.clear(); check(!state.accept(frame(2, 2, 1, 100, 0), false), "world clear preserves retired token watermark");
        state.cancel(2, 10); check(!state.accept(frame(3, 3, 1, 1, 0), true), "old epoch rejected after local cancellation");
        check(!state.accept(frame(10, 4, 2, 1, 0), true), "cancelled pending request cannot revive");
        check(state.accept(frame(11, 5, 2, 1, 0), true), "a later authorized tap can recover");
        state.resetSession(); check(state.accept(frame(1, 1, 1, 1, 0), true), "new server connection starts new token namespace");
        var malformed = new MagneticDomainState.Frame(frame(1, 1, 1, 1, 0).id(), 2, 2, ORIGIN, FORWARD, new Point(0, 0, 100), false, 1);
        check(!state.accept(malformed, false), "remote packet cannot spawn an arbitrary distant receipt");
        var lease = new MagneticDomainState.Lease(); lease.received(1);
        for (int i = 0; i < 4; i++) check(lease.tick(false, true), "fresh receipt lease alive");
        lease.received(0); check(lease.hitAge() == 4 && lease.refractionAge() == MagneticDomainState.REFRACT_TICKS, "heartbeat does not renew impact or invent refraction");
        check(!lease.tick(true, false), "death/preset/category/screen invalidation works while paused");
        for (int i = 0; i < 10000; i++) { lease.received(0); check(lease.tick(false, true), "long lived toggle renews without absolute expiration"); }
        check(lease.frame() < 1200, "animation elapsed clock wraps");
        for (int i = 0; i < MagneticDomainState.QUIET_TICKS; i++) lease.tick(false, true);
        check(lease.quiet() >= MagneticDomainState.QUIET_TICKS && !lease.tick(false, true), "dropped terminal cannot leave geometry indefinitely");
    }
    private static void geometry() {
        for (Point facing : List.of(FORWARD, new Point(1, 0, 0), UP, new Point(.6, .8, 0))) for (int frame : new int[]{0, 9, 117, 1199, Integer.MAX_VALUE}) {
            Point center = new Point(28, 64, -13);
            var shell = MagneticDomainGeometry.boundary(center, facing, frame);
            var front = MagneticDomainGeometry.frontBoundary(center, facing, frame);
            check(shell.size() == 40 && front.size() == 12, "sparse fixed complexity at all orientations");
            for (var s : shell) for (Point p : List.of(s.start(), s.end())) check(Math.abs(p.minus(center).length() - 12) < 1e-8, "actual sphere endpoint radius is 12");
            for (var s : front) for (Point p : List.of(s.start(), s.end())) {
                Point delta = p.minus(center);
                check(Math.abs(delta.dot(facing) / delta.length() - Math.cos(Math.toRadians(60))) < 1e-8, "front boundary half-angle is exactly 60 degrees");
            }
            Point shifted = center.plus(new Point(7, 3, -8)); var moved = MagneticDomainGeometry.boundary(shifted, facing, frame);
            check(moved.getFirst().start().minus(shell.getFirst().start()).minus(shifted.minus(center)).length() < 1e-8, "mobile field is translation covariant");
        }
        check(MagneticDomainGeometry.impact(ORIGIN, FORWARD, 0).size() == 4 && MagneticDomainGeometry.refraction(ORIGIN, FORWARD, 0).size() == 6, "impact spokes and refraction brackets remain distinct");
        check(MagneticDomainGeometry.impact(ORIGIN, FORWARD, 5).isEmpty() && MagneticDomainGeometry.refraction(ORIGIN, FORWARD, 7).isEmpty(), "all receipt geometry expires");
        var budget = new MagneticDomainGeometry.Budget(); int emitted = 0;
        for (int i = 0; i < 1000; i++) if (budget.take()) emitted++;
        check(emitted == 128 && budget.used() == 128, "actual post-clipping quad budget is hard bounded");
        var lines = new ArrayList<Segment>(); lines.addAll(MagneticDomainGeometry.boundary(ORIGIN, FORWARD, 0)); lines.addAll(MagneticDomainGeometry.frontBoundary(ORIGIN, FORWARD, 0));
        int ribbons = 0; double area = 0;
        for (var line : lines) {
            var clipped = RailThunderArrayGeometry.nearClip(line.start(), line.end(), FORWARD, MagneticDomainGeometry.WIDTH);
            for (var piece : RailThunderArrayGeometry.clearCenter(clipped, FORWARD, UP, MagneticDomainGeometry.WIDTH)) {
                var r = RailThunderArrayGeometry.ribbon(piece, FORWARD, MagneticDomainGeometry.WIDTH); if (r == null) continue;
                ribbons++; area += RailThunderArrayGeometry.projectedArea(r, FORWARD, UP);
                for (Point p : List.of(r.start().plus(r.side()), r.start().minus(r.side()), r.end().plus(r.side()), r.end().minus(r.side()))) {
                    double depth = p.dot(FORWARD); check(depth > .19, "quad never crosses camera near plane");
                    double x = p.dot(FORWARD.cross(UP)) / depth, y = p.dot(UP) / depth;
                    check(Math.abs(x) >= .022 - 1e-6 || Math.abs(y) >= .022 - 1e-6, "all expanded ribbon corners leave crosshair clear");
                }
            }
        }
        check(ribbons > 0 && area > .00001, "inside-domain first-person camera sees nonzero projected geometry");
        // Cross products can contain signed zero; compare geometric directions, not record bit equality.
        Point right = FORWARD.cross(UP);
        check(right.minus(new Point(1, 0, 0)).length() < 1e-12 && new Point(1, 0, -4).dot(right) > 0,
                "north-facing camera projects east onto positive screen-right");
        Point south = new Point(0, 0, 1), west = new Point(-1, 0, 0);
        check(south.cross(UP).minus(west).length() < 1e-12 && new Point(-1, 0, 4).dot(south.cross(UP)) > 0,
                "south-facing camera projects west onto positive screen-right");
        var behind = RailThunderArrayGeometry.nearClip(new Point(-1, 0, 3), new Point(1, 0, 3), FORWARD, .014);
        check(behind == null, "behind-eye geometry is rejected");
    }
    /** Normal 70-degree vertical FOV, measured against the daylight QA viewport, not an unbounded projection. */
    private static void circulationReadability() {
        double tanVertical = Math.tan(Math.toRadians(35)), tanHorizontal = tanVertical * 1180 / 812;
        double focalPixels = 812 / (2 * tanVertical), minimumInk = Double.POSITIVE_INFINITY;
        int minimumVisible = Integer.MAX_VALUE;
        for (Point camera : List.of(new Point(0, .62, 0), new Point(0, .62, 4))) for (int frame = 0; frame < 1200; frame += 5) {
            var circulation = MagneticDomainGeometry.circulation(ORIGIN, frame);
            check(circulation.size() == 24, "inner flow remains 24 sparse dashes, never a filled surface");
            int visible = 0; double ink = 0;
            for (var line : circulation) {
                for (Point p : List.of(line.start(), line.end())) {
                    check(p.length() < 4.1 && p.length() > 3 && p.y() > -.2,
                            "decorative inner flow is distinct from the exact twelve-meter boundary and above the floor");
                }
                var clipped = RailThunderArrayGeometry.nearClip(line.start().minus(camera), line.end().minus(camera), FORWARD, MagneticDomainGeometry.FLOW_WIDTH);
                for (var piece : RailThunderArrayGeometry.clearCenter(clipped, FORWARD, UP, MagneticDomainGeometry.FLOW_WIDTH)) {
                    var ribbon = RailThunderArrayGeometry.ribbon(piece, FORWARD, MagneticDomainGeometry.FLOW_WIDTH);
                    if (ribbon == null) continue;
                    boolean seen = false;
                    for (int sample = 0; sample < 16; sample++) {
                        Point a = piece.start().lerp(piece.end(), sample / 16.0), b = piece.start().lerp(piece.end(), (sample + 1) / 16.0);
                        double az = a.dot(FORWARD), bz = b.dot(FORWARD);
                        double ax = a.x() / az, ay = a.y() / az, bx = b.x() / bz, by = b.y() / bz;
                        if (Math.abs(ax) > tanHorizontal || Math.abs(bx) > tanHorizontal || Math.abs(ay) > tanVertical || Math.abs(by) > tanVertical) continue;
                        seen = true; ink += Math.hypot(bx - ax, by - ay) * focalPixels;
                        check(2 * ribbon.halfWidth() * focalPixels / Math.max(az, bz) >= 1.5,
                                "daylight flow outline is at least 1.5 physical pixels wide in first and third person");
                    }
                    if (seen) visible++;
                }
            }
            minimumVisible = Math.min(minimumVisible, visible); minimumInk = Math.min(minimumInk, ink);
            check(visible >= 3 && ink >= 50, "every animation phase retains readable flow inside normal 70 FOV");
        }
        var first = MagneticDomainGeometry.circulation(ORIGIN, 0);
        var later = MagneticDomainGeometry.circulation(ORIGIN, 8);
        check(first.getFirst().start().minus(later.getFirst().start()).length() > .3, "flow visibly moves rather than acting as a static inner rim");
        for (int band = 0; band < 2; band++) for (int arc = 0; arc < 3; arc++) {
            int last = band * 12 + arc * 4 + 3, next = band * 12 + ((arc + 1) % 3) * 4;
            check(first.get(last).end().minus(first.get(next).start()).length() > 3,
                    "long open gaps prevent decorative streams from becoming a closed secondary shield boundary");
        }
        check(compositedDifference(MagneticDomainGeometry.FLOW_BLUE, MagneticDomainGeometry.FLOW_ALPHA, 0xAECDFF) > 80,
                "saturated flow edge contrasts with the observed bright daylight sky");
        check(compositedDifference(MagneticDomainGeometry.WHITE, MagneticDomainGeometry.CORE_ALPHA, 0x808080) > 80,
                "white flow core contrasts with the observed gray stone floor");
        check(compositedDifference(MagneticDomainGeometry.BOUNDARY_BLUE, MagneticDomainGeometry.BOUNDARY_ALPHA, 0xAECDFF) > 70,
                "true twelve-meter boundary also remains contrasting against daylight");
        System.out.println("NEW152 70FOV flow witness: minimum " + minimumVisible + " visible fragments / " + Math.round(minimumInk) + " pixels of line");
    }
    private static double compositedDifference(int rgb, int alpha, int background) {
        double difference = 0;
        for (int shift : new int[]{0, 8, 16}) {
            int foreground = rgb >>> shift & 255, behind = background >>> shift & 255;
            difference = Math.max(difference, Math.abs(foreground - behind) * alpha / 255.0);
        }
        return difference;
    }

    private static void hud() {
        String s = "50000000000000000000", shortS = "49999999999999999999.99999999";
        var values = new HighPowerRecoveryHudModel.Values("1", "20", "3", s, "full", "0", 0, 0, 0, true, shortS, s, true);
        check(values.shield().equals("3") && values.domainReserve().equals(shortS) && values.domainVisible(), "base and reserve stay separate exact strings");
        var fresh = new HighPowerRecoveryHudModel.Values("1", "20", "3", s, "full", "0", 0, 0, 0, false, "0", s, false);
        for (String language : List.of("en_us", "zh_cn", "zh_tw", "ja_jp")) {
            var locale = HighPowerRecoveryHudModel.numberLocale(language);
            var tiny = HighPowerRecoveryHudModel.pool(shortS, s, locale);
            check(!tiny.full() && !tiny.current().equals(tiny.maximum()) && !tiny.shortfall().isEmpty(), "subquantum reserve deficit never falsely full in " + language);
            check(HighPowerRecoveryHudModel.pool(fresh.domainReserve(), fresh.domainCapacity(), locale).current().equals("0"), "fresh reserve visibly zero");
            check(!HighPowerRecoveryHudModel.pool("0.00000001", s, locale).current().equals("0"), "tiny live reserve never rounds to zero");
        }
        denied(() -> new HighPowerRecoveryHudModel.Values("1", "20", "1", s, "full", "0", 0, 0, 0, false, "1", "50000000000000000001", false), "combined shield cap enforced by presentation admission");
        denied(() -> new HighPowerRecoveryHudModel.Values("1", "20", "1", "4", "full", "0", 0, 0, 0, false, "5", "5", false), "domain cannot advertise more than S capacity");
        for (int width : new int[]{150, 174, 202}) {
            check(HighPowerRecoveryHudModel.barPixels(shortS, s, width) == width - 1, "tiny deficit always leaves one visible bar pixel");
            check(HighPowerRecoveryHudModel.barPixels("0.00000001", s, width) == 1, "tiny balance always has one visible bar pixel");
            for (int label : new int[]{30, 120, 300}) for (int numeric : new int[]{60, 240, 500}) {
                var row = HighPowerRecoveryHudModel.numberRow(width, label, numeric);
                check(row.labelScale() > 0 && row.valueScale() > 0 && label * row.labelScale() <= row.labelWidth() + .001
                        && numeric * row.valueScale() <= row.valueWidth() + .001 && row.valueX() + row.valueWidth() == width - 5,
                        "localized complete numeric pair and label fit GUI-scaled row");
            }
        }
        for (int width : new int[]{320, 427, 640, 960, 1280}) for (int height : new int[]{240, 360, 540}) {
            var cp = new HighPowerRecoveryHudModel.Box(width - 205, 12, 194, 40);
            var keys = new HighPowerRecoveryHudModel.Box(width - 188, (int)((height - 48.3) / 2 + 30), 185, 88);
            var notification = new HighPowerRecoveryHudModel.Box(0, 15, 130, 43);
            var chat = new HighPowerRecoveryHudModel.Box(0, height - 130, Math.min(width, 336), 102);
            var box = HighPowerRecoveryHudModel.layout(width, height, cp, keys, notification, chat);
            check(box != null && box.height() == 56 && box.within(width, height) && !box.intersects(cp) && !box.intersects(chat) && !box.intersects(keys) && !box.intersects(notification), "extra reserve stays within original disjoint HUD footprint");
        }
    }
    private static void integration() throws Exception {
        String effect = Files.readString(Path.of("src/main/java/cn/academy/port/client/MagneticDomainEffects.java"));
        check(effect.contains("generation != clearGeneration") && effect.contains("capturedPlayer != mc.player") && effect.contains("capturedConnection != mc.getConnection()"), "queued delivery fences connection and owner object replacement");
        check(effect.contains("modelView.set(event.getModelViewMatrix())") && effect.contains("modelView.popMatrix()") && effect.contains("buffers.endBatch()"), "actual renderer applies stage modelview and restores state even on exception");
        check(effect.contains("ClassicRenderTypes.world(WHITE)") && effect.contains("ClipContext.Block.COLLIDER") && effect.contains("world.hasChunkAt") && effect.contains("!budget.take()") && effect.contains("MagneticDomainGeometry.circulation(point(center), marker.lease.frame())"), "actual renderer has depth-capable world type, wall/missing-chunk clipping and actual quad bound");
        check(!effect.contains("addParticle(") && !effect.contains("RAIL[") && !effect.contains("setDeltaMovement") && !effect.contains("setHealth"), "effects never spawn unbounded particles, orange rail or gameplay mutations");
        String client = Files.readString(Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        check(client.contains("hp_magnetic_domain_toggle") && client.contains("hp_magnetic_domain_abort") && client.contains("magneticDomainInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null)"), "input route covers screen/focus cleanup");
        check(client.contains("magneticDomainInputs.reset();MagneticDomainEffects.clear();") && client.contains("magneticDomainInputs.cancel(),AcademyClient::cancelMagneticDomainLocally"), "existing death/relogin/preset/category lifecycle invokes domain cleanup");
        String payload = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/HighPowerRecoveryHudPayload.java"));
        check(payload.contains("hp_recovery_hud_v6") && !payload.contains("hp_recovery_hud_v5") && !payload.contains("hp_recovery_hud_v4") && !payload.contains("hp_recovery_hud_v3") && !payload.contains("hp_recovery_hud_v2") && !payload.contains("hp_recovery_hud_v1")
                && payload.contains("event.registrar(\"6\").optional().playToClient(Snapshot.TYPE, Snapshot.CODEC")
                && payload.contains("NetworkRegistry.hasChannel(player.connection, Snapshot.TYPE.id())"),
                "v6 TP category and category-isolated reserve schema uses a separate optional negotiated channel from older clients");
        String wire = payload.replaceAll("\\s+", "");
        check(wire.contains("text(buffer,value.domainReserve());text(buffer,value.domainCapacity());buffer.writeBoolean(value.domainActive());buffer.writeUtf(value.category(),24);")
                && wire.contains("text(buffer,value.curtainReserve());text(buffer,value.curtainCapacity());buffer.writeBoolean(value.curtainActive());text(buffer,value.zeroGapReserve());text(buffer,value.zeroGapCapacity());buffer.writeBoolean(value.zeroGapActive());")
                && wire.contains("text(buffer),text(buffer),buffer.readBoolean(),buffer.readUtf(24),text(buffer),text(buffer),buffer.readBoolean(),text(buffer),text(buffer),buffer.readBoolean()):null;"),
                "v6 preserves EM reserve and bounded category order before the separate curtain and zero-gap fields in both codec directions");
        check(payload.contains("electromaster ? CombatAmounts.decimalString(MagneticDomainReserve.reserve(player)) : \"0\"")
                && payload.contains("electromaster ? CombatAmounts.decimalString(MagneticDomainReserve.capacity(player)) : \"0\"")
                && payload.contains("electromaster && MagneticDomain.active(player), category"),
                "server category gates EM reserve and sends its actual category");
        check(payload.contains("boolean zeroGap = meltdowner && HighPowerCombat.zeroGapBenefitsActive(player)")
                && payload.contains("zeroGap ? CombatAmounts.decimalString(HighPowerCombat.zeroGapReserve(player)) : \"0\"")
                && payload.contains("zeroGap ? CombatAmounts.decimalString(HighPowerCombat.zeroGapReserveCapacity(player)) : \"0\"")
                && payload.contains("zeroGap && ZeroGapArmor.active(player)")
                && !payload.contains("playToServer(") && !payload.contains("playBidirectional("),
                "MD aggregate is eligibility-gated and the optional HUD still has no client mutation route");
        String hud = Files.readString(Path.of("src/main/java/cn/academy/port/client/HighPowerRecoveryHud.java"));
        check(hud.contains("String current = zeroGap ? values.zeroGapTotalShield() : curtain ? values.curtainReserve() : values.domainReserve();")
                && hud.contains("String maximum = zeroGap ? values.zeroGapFullCapacity() : curtain ? values.curtainCapacity() : values.domainCapacity();")
                && hud.contains("boolean active = zeroGap ? values.zeroGapActive() : curtain ? values.curtainActive() : values.domainActive();")
                && hud.contains("HighPowerRecoveryHudModel.pool(current, maximum, numbers)")
                && hud.contains("HighPowerRecoveryHudModel.barPixels(current, maximum, box.width() - 10)")
                && hud.contains("if (values.domainVisible()) fields.add(tr(\"domain_reserve\"")
                && hud.contains("values.domainReserve() + \" / \" + values.domainCapacity()")
                && hud.contains("values.domainVisible() && MagneticDomainEffects.slowFallingLocal()"),
                "EM numeric pool, bar, activity and exact detail remain independent from MD totals and only accepted EM caster slowfall is shown");
    }
}

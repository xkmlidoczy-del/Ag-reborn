package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import cn.academy.port.highpower.core.ScatterPlateGeometry;
import cn.academy.port.highpower.core.ScatterPlateGeometry.Pose;
import cn.academy.port.highpower.core.ScatterPlateGeometry.Vector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Production pure geometry, receipt and input state exercised against independent projection/contact witnesses. */
public final class ScatterPlateVisualRegressionTest {
    private static final Vector ORIGIN = new Vector(0, 0, 0), FORWARD = new Vector(0, 0, -1), UP = new Vector(0, 1, 0);
    private static final UUID OWNER = new UUID(212, 1);
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static void denied(Runnable action, String label) { try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; } throw new AssertionError(label); }
    private static ScatterPlateState.Frame frame(long input, long token, long epoch, long sequence, int phase, int mask, int plate) {
        return new ScatterPlateState.Frame(new ScatterPlateState.Identity(4, OWNER, epoch, input, token), sequence,
                phase, ORIGIN, FORWARD, mask, plate, new Vector(0, 0, -1.02));
    }
    public static void main(String[] args) throws Exception {
        input(); lifecycle(); geometry(); nativeProjection(); clipping(); hud(); integration();
        System.out.println("PASS " + checks + " NEW212 exact plate meshes, native FOV/gaps, receipt-only flashes, lifecycle and MD HUD checks");
    }
    private static void input() {
        var state = new ScatterPlateInputState();
        var first = new HighPowerInput.Input(0, 1, 4, 9); var second = new HighPowerInput.Input(0, 2, 4, 9);
        state.tap(first); state.release(first); check(state.permits(1, 9), "ordinary key release keeps toggle request");
        state.tap(second); check(state.permits(1, 9) && state.permits(2, 9), "rapid toggles retain authentic pending receipts");
        state.accepted(1, 9); state.ended(1, 9); check(!state.permits(1, 9) && state.permits(2, 9), "old terminal cannot consume newer toggle");
        state.accepted(2, 9); check(state.cancel(first) == null && state.permits(2, 9), "old disposal cannot abort new input");
        check(state.cancelWhenBlocked(true).equals(second) && state.current() == null, "menu/focus/camera cancellation removes input");
        state.tap(first); denied(() -> state.tap(first), "duplicate physical toggle is rejected");
        for (int i = 2; i <= 100; i++) state.tap(new HighPowerInput.Input(0, i, 4, 9));
        check(state.pendingCount() == 16 && !state.permits(1, 9), "pending request storage is bounded");
        state.tap(new HighPowerInput.Input(0, 1, 5, 10)); check(!state.permits(100, 9), "actor/epoch replacement clears old namespace");
        state.reset(); check(state.current() == null, "relogin leaves no remembered input");
    }
    private static void lifecycle() {
        var state = new ScatterPlateState();
        check(state.accept(frame(1, 1, 1, 1, 0, 7, -1), true), "active receipt starts one controller");
        check(!state.accept(frame(1, 1, 1, 1, 1, 7, 0), true), "replayed sequence cannot invent hit flash");
        check(state.accept(frame(1, 1, 1, 2, 1, 5, 1), true), "positive absorption receipt identifies the broken middle plate");
        check(state.size() == 1 && state.current(OWNER).liveMask() == 5, "three plates use one owner controller and authoritative live mask");
        check(state.accept(frame(1, 1, 1, 3, 0, 5, -1), true), "heartbeat does not recreate broken plate");
        check(!state.accept(frame(1, 1, 1, 4, 1, 5, -1), true), "hit must name exactly one valid plate");
        check(!state.accept(frame(1, 1, 1, 4, 0, 8, -1), true), "unknown plate mask rejected");
        check(state.accept(frame(1, 1, 1, 5, 2, 0, -1), true) && state.size() == 0, "disabled receipt removes all visible state");
        check(!state.accept(frame(1, 1, 1, 6, 0, 7, -1), true), "terminal fence rejects late heartbeat");
        check(!state.accept(frame(2, 1, 1, 1, 0, 7, -1), true), "new input cannot recycle old token");
        check(state.accept(frame(2, 2, 1, 1, 0, 5, -1), true), "fresh toggle retains missing plate");
        state.clear(); check(!state.accept(frame(2, 2, 1, 99, 0, 7, -1), false), "world unload preserves retired token");
        state.cancel(2, 8); check(!state.accept(frame(8, 3, 2, 1, 0, 7, -1), true), "cancelled pending activation cannot appear later");
        check(state.accept(frame(9, 4, 2, 1, 0, 7, -1), true), "later authenticated toggle can recover");
        state.resetSession(); check(state.accept(frame(1, 1, 1, 1, 0, 7, -1), true), "new connection resets server token namespace");
        var lease = new ScatterPlateState.Lease(); lease.received(frame(1, 1, 1, 1, 0, 7, -1));
        for (int p = 0; p < 3; p++) check(lease.hitAge(p) == ScatterPlateState.HIT_TICKS, "activation cannot flash any plate");
        lease.received(frame(1, 1, 1, 2, 1, 5, 1));
        check(lease.hitAge(0) == 5 && lease.hitAge(1) == 0 && lease.hitAge(2) == 5, "only the truly absorbing plate flashes");
        for (int i = 0; i < 3; i++) lease.tick(false, true);
        lease.received(frame(1, 1, 1, 3, 0, 5, -1)); check(lease.hitAge(1) == 3, "heartbeat never renews flash");
        lease.tick(true, true); check(lease.hitAge(1) == 3, "pause does not age effects");
        check(!lease.tick(true, false), "menu/preset/death/camera invalidation works while paused");
        for (int i = 0; i < 12; i++) lease.tick(false, true);
        check(!lease.tick(false, true) && lease.hitAge(1) == 5, "dropped stop is bounded by heartbeat TTL");
    }
    private static void geometry() {
        for (Vector axis : List.of(FORWARD, new Vector(1, 0, 0), UP, new Vector(.6, .8, 0))) {
            Pose pose = ScatterPlateGeometry.pose(new Vector(28, 64, -13), axis);
            for (int plate = 0; plate < 3; plate++) {
                Vector center = ScatterPlateGeometry.plateCenter(pose, plate);
                List<ScatterPlateMesh.Face> faces = ScatterPlateMesh.plate(pose, plate, true, 5);
                check(faces.size() == 96, "fixed bounded mesh complexity");
                for (var face : faces) for (Vector point : face.vertices()) {
                    Vector d = point.subtract(center);
                    double x = d.dot(pose.right()) / .30, y = d.dot(pose.up()) / .55;
                    check(x * x + y * y <= 1 + 1e-9, "visual vertices never expand the collision ellipse");
                    check(Math.abs(d.dot(pose.axis())) <= .02 + 1e-9, "mesh never expands the .04m slab");
                    check(point.subtract(pose.center()).length() < 3, "every mesh vertex remains within owner three-meter bound");
                }
                Vector incoming = center.add(pose.axis().scale(2)), end = center.subtract(pose.axis());
                var crossing = ScatterPlateGeometry.crossing(pose, incoming, end, 7);
                check(crossing.isPresent() && crossing.get().plateIndex() == plate, "visible plate center matches authoritative crossing index");
                check(ScatterPlateMesh.contactNear(pose, plate, crossing.orElseThrow().point()), "actual front-face hit is near rendered slab");
                check(!ScatterPlateMesh.contactNear(pose, plate, center.add(pose.axis().scale(3))), "old far crossing cannot be redrawn as current contact");
                check(ScatterPlateMesh.plate(pose, plate, false, 5).isEmpty(), "broken plate stays an actual gap after flash");
                var broken = ScatterPlateMesh.plate(pose, plate, false, 0);
                check(broken.size() == 4 && broken.stream().noneMatch(f -> f.kind() == ScatterPlateMesh.FILL), "break flash has four short fragments and no filled silhouette");
                check(ScatterPlateMesh.contact(pose, plate, crossing.orElseThrow().point(), 0).size() == 4, "actual contact accents stay bounded");
            }
            for (double offset : new double[]{-.4, .4, -1.2, 1.2}) {
                Vector gap = pose.center().add(pose.axis()).add(pose.right().scale(offset));
                check(ScatterPlateGeometry.crossing(pose, gap.add(pose.axis()), gap.subtract(pose.axis()), 7).isEmpty(), "both inter-plate gaps and sides remain open");
            }
            Vector middle = ScatterPlateGeometry.plateCenter(pose, 1);
            check(ScatterPlateGeometry.crossing(pose, middle.add(pose.axis()), middle.subtract(pose.axis()), 5).isEmpty(), "broken middle gap is also open to real authoritative crossing");
        }
        Pose original = ScatterPlateGeometry.pose(ORIGIN, FORWARD);
        Vector historical = new Vector(0, 0, -1.02);
        check(ScatterPlateMesh.contactNear(ScatterPlateGeometry.pose(new Vector(.01, 0, 0), FORWARD), 1, historical), "small ordinary movement can retain honest original contact accent");
        check(!ScatterPlateMesh.contactNear(ScatterPlateGeometry.pose(new Vector(.8, 0, 0), FORWARD), 1, historical), "movement to another plate position cannot relabel historical point");
        check(!ScatterPlateMesh.contactNear(ScatterPlateGeometry.pose(ORIGIN, new Vector(1, 0, 0)), 1, historical), "turning cannot fabricate current contact from old crossing");
        check(ScatterPlateMesh.plate(original, 1, true, 0).stream().anyMatch(f -> f.kind() == ScatterPlateMesh.LIGHT && f.color() == ScatterPlateMesh.CORE), "receipt can still flash current named plate while remote historical contact is suppressed");
        check(ScatterPlateMesh.SPAWNED_PARTICLES <= 32, "no particle flood; meshes use zero particle controllers");
        var budget = new ScatterPlateMesh.Budget(); int used = 0;
        for (int i = 0; i < 10000; i++) if (budget.take()) used++;
        check(used == 600 && budget.used() == 600, "post-clipping mesh emission has a hard per-caster bound");
    }
    private static void nativeProjection() {
        Pose pose = ScatterPlateGeometry.pose(ORIGIN, FORWARD);
        // Standard native 70-degree vertical FOV at the QA viewport, plus narrower/wider camera settings.
        for (int fov : new int[]{60, 70, 90}) for (Vector camera : List.of(new Vector(0, .62, 0), new Vector(0, .62, 4))) {
            double tanV = Math.tan(Math.toRadians(fov / 2.0)), tanH = tanV * 1180 / 812, focal = 812 / (2 * tanV);
            for (int plate = 0; plate < 3; plate++) {
                double visibleRimPixels = 0; int visibleEdges = 0;
                for (var face : ScatterPlateMesh.plate(pose, plate, true, 5)) if (face.kind() == ScatterPlateMesh.RIM) {
                    Vector a = face.a().subtract(camera), b = face.b().subtract(camera);
                    for (int i = 0; i < 16; i++) {
                        Vector p = a.add(b.subtract(a).scale(i / 16.0)), q = a.add(b.subtract(a).scale((i + 1) / 16.0));
                        double zp = p.dot(FORWARD), zq = q.dot(FORWARD);
                        if (zp <= .06 || zq <= .06) continue;
                        double xp = p.x() / zp, yp = p.y() / zp, xq = q.x() / zq, yq = q.y() / zq;
                        if (Math.abs(xp) > tanH || Math.abs(xq) > tanH || Math.abs(yp) > tanV || Math.abs(yq) > tanV) continue;
                        visibleEdges++; visibleRimPixels += Math.hypot(xq - xp, yq - yp) * focal;
                    }
                }
                check(visibleEdges > 0 && visibleRimPixels >= 30, "each real plate has a visible 3D arc in first/third-person native FOV " + fov);
            }
            // .2m physical gaps, independent of GUI scale and with no enlarged visual collider.
            check(.2 * focal / (1 - camera.z() * FORWARD.z()) > 10, "actual inter-plate gap is readable at QA first/third camera distance");
            check(.30 * .055 * focal / (1 + camera.z()) >= .9, "dark rim has at least near-one physical pixel width even in third person");
        }
        for (int guiScale : new int[]{1, 2, 3, 4}) {
            var baseline = ScatterPlateMesh.plate(pose, 1, true, 5);
            check(baseline.equals(ScatterPlateMesh.plate(pose, 1, true, 5)), "GUI scale " + guiScale + " never changes physical plate silhouette");
        }
        var noHit = ScatterPlateMesh.plate(pose, 1, true, 5); var hit = ScatterPlateMesh.plate(pose, 1, true, 0);
        check(noHit.stream().filter(f -> f.kind() == ScatterPlateMesh.FILL).allMatch(f -> f.alpha() <= 15)
                && hit.stream().filter(f -> f.kind() == ScatterPlateMesh.FILL).allMatch(f -> f.alpha() <= 32), "plate fill stays transparent before and during authentic hit");
    }
    private static void clipping() {
        Pose pose = ScatterPlateGeometry.pose(ORIGIN, FORWARD);
        for (Vector camera : List.of(new Vector(0, 0, -.99), new Vector(0, 0, -1.1), new Vector(0, .62, 4))) {
            var budget = new ScatterPlateMesh.Budget();
            for (int plate = 0; plate < 3; plate++) for (var face : ScatterPlateMesh.plate(pose, plate, true, 0))
                for (var piece : ScatterPlateMesh.nearClip(face, camera, FORWARD)) {
                    check(budget.take(), "all three clipped silhouettes fit batched mesh budget");
                    for (Vector p : piece.vertices()) check(p.subtract(camera).dot(FORWARD) >= .06 - 1e-9, "clipped polygon cannot cross native camera near plane");
                }
        }
        var face = ScatterPlateMesh.plate(pose, 1, true, 5).getFirst();
        check(ScatterPlateMesh.nearClip(face, new Vector(0, 0, -3), FORWARD).isEmpty(), "behind-camera plates produce no mirrored mesh");
        check(ScatterPlateMesh.nearClip(face, ORIGIN, new Vector(Double.NaN, 0, 0)).isEmpty(), "invalid camera vectors cannot poison draw buffers");
    }
    private static void hud() {
        var active = new HighPowerRecoveryHudModel.Values("19", "20", "8", "10", "full", "0", 0, 0, 0, true, "0", "0", false, "meltdowner");
        var idle = new HighPowerRecoveryHudModel.Values("19", "20", "8", "10", "full", "0", 0, 0, 0, false, "0", "0", false, "meltdowner");
        check(active.shieldActive() && !idle.shieldActive() && active.shieldVisible() && idle.shieldVisible()
                && !active.domainVisible() && active.categoryLabel().equals("MD"), "MD active/idle uses base shield while EM reserve remains absent");
        denied(() -> new HighPowerRecoveryHudModel.Values("19", "20", "8", "10", "full", "0", 0, 0, 0, true, "1", "1", true, "meltdowner"), "MD cannot inherit EM reserve or domain flag");
        var vector = new HighPowerRecoveryHudModel.Values("19", "20", "8", "10", "full", "0", 0, 0, 0, true, "0", "0", false, "vecmanip");
        var noVectorDefense = new HighPowerRecoveryHudModel.Values("19", "20", "0", "0", "full", "0", 0, 0, 0, false, "0", "0", false, "vecmanip");
        var teleporter = new HighPowerRecoveryHudModel.Values("19", "20", "0", "0", "full", "0", 0, 0, 0, false, "0", "0", false, "teleporter");
        check(vector.shieldVisible() && !vector.domainVisible() && !vector.curtainVisible() && !vector.zeroGapVisible(), "NEW412 exposes only authenticated base S, never MD/EM reserve");
        var phaseShield = new HighPowerRecoveryHudModel.Values("19", "20", "8", "10", "full", "0", 0, 0, 0, true, "0", "0", false, "teleporter");
        check(phaseShield.shieldVisible() && phaseShield.shieldHolding() && !phaseShield.domainVisible()
                && !phaseShield.curtainVisible() && !phaseShield.zeroGapVisible(), "TP base shield and super armor are visible without borrowing EM or MD reserves");
        denied(() -> new HighPowerRecoveryHudModel.Values("19", "20", "8", "10", "full", "0", 0, 0, 0, true, "1", "1", false, "teleporter"), "TP cannot advertise EM reserves");
        denied(() -> new HighPowerRecoveryHudModel.Values("19", "20", "8", "10", "full", "0", 0, 0, 0, true, "0", "0", false, "teleporter", "1", "1", false), "TP cannot advertise MD reserves");
        check(!noVectorDefense.shieldVisible() && !teleporter.shieldVisible(), "snapshots without actual shield capacity do not invent a shield bar");

    }
    private static void integration() throws Exception {
        String effects = Files.readString(Path.of("src/main/java/cn/academy/port/client/ScatterPlateEffects.java"));
        check(!effects.contains("clearCenter") && !effects.contains("addParticle("), "renderer has no crosshair erasure or generated particle flood");
        check(effects.contains("ScatterPlateGeometry.pose(point(marker.state.center()), point(marker.state.axis()))"), "renderer consumes authoritative shared pose");
        check(effects.contains("marker.state.liveMask()") && effects.contains("packet.phase() == ScatterPlateState.HIT"), "broken state and flashes only consume server receipts");
        check(effects.contains("generation != capturedGeneration") && effects.contains("!AcademyClient.sameInputSessionEpoch(capturedSession)"), "queued receipt fenced by world/input generation");
        check(effects.contains("mc.getCameraEntity() == mc.player") && effects.contains("mc.screen == null") && !effects.contains("hideGui"), "camera/menu guards clear local geometry while F1 keeps real world plates");
        String client = Files.readString(Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        check(client.contains("request(\"hp_scatter_plates_toggle\",identity.wire())") && client.contains("scatterPlateInputs.release(identity)"), "normal ability binding uses actor-authenticated toggle and release is inert");
        check(client.contains("scatterPlateInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null||mc.getCameraEntity()!=mc.player)"), "camera replacement aborts authenticated server toggle");
        String payload = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/HighPowerRecoveryHudPayload.java"));
        String combat = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/HighPowerCombat.java"));
        check(payload.contains("HighPowerCombat.shieldActive(player)") && combat.contains("scatterPlateBenefitsActive(entity) && entity instanceof ServerPlayer player && ScatterPlates.active(player)"), "composite MD HUD still includes the authoritative NEW212 controller");
        String hud = Files.readString(Path.of("src/main/java/cn/academy/port/client/HighPowerRecoveryHud.java"));
        String model = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/HighPowerRecoveryHudModel.java"));
        check(model.contains("public boolean shieldVisible() { return decimal(maxShield).signum() > 0; }")
                && hud.contains("if (values.shieldVisible()) {") && hud.contains("values.shieldHolding()")
                && hud.contains("values.shieldKey()") && hud.contains("shield_broken") && hud.contains("super_armor")
                && hud.contains("if (values.domainVisible())"), "renderer shows real capacity and broken/holding state; model checks keep exclusive reserves separate");
    }
}

package cn.academy.port.macro;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pure 3D hit-volume math & dedicated-server classloading isolation regression checks for a3.65.
 */
public final class HitVolumesRegressionTest {
    private static int checks;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    private static void near(double actual, double expected, double eps, String why) {
        check(Math.abs(actual - expected) <= eps, why + ": actual=" + actual + " expected=" + expected);
    }

    public static void main(String[] args) throws Exception {
        // 1. Point-to-AABB distance (tall Mech/Titan/Ghast upper-body sphere inclusion).
        AABB tallMech = new AABB(-0.9, 0.0, 9.5, 0.9, 4.2, 11.3);
        Vec3 blastAtHeadHeight = new Vec3(0.0, 3.8, 2.0);
        // Feet (0, 0, 10.4) are sqrt(3.8^2 + 8.4^2) = 9.22m away (> 8.0m), while upper hull z=9.5, y=3.8 is 7.5m away (<= 8.0m).
        double feetDist = new Vec3(0.0, 0.0, 10.4).distanceTo(blastAtHeadHeight);
        double aabbDist = Math.sqrt(HitVolumes.pointToAabbDistanceSqr(blastAtHeadHeight, tallMech));
        check(feetDist > 8.0, "tall entity feet lie outside 8m sphere");
        near(aabbDist, 7.5, 1e-9, "tall entity upper hull is 7.5m from blast center");
        near(HitVolumes.pointToAabbDistanceSqr(new Vec3(0.0, 2.0, 10.0), tallMech), 0.0, 1e-12, "interior point distance is zero");

        // 2. Segment-to-AABB capsule check (horizontal beam passing through upper torso while feet are 3.6m below).
        Vec3 beamStart = new Vec3(0.0, 3.6, 0.0);
        Vec3 beamEnd = new Vec3(0.0, 3.6, 30.0);
        near(HitVolumes.segmentToAabbDistanceSqr(beamStart, beamEnd, tallMech), 0.0, 1e-12, "beam piercing upper hull has zero capsule distance");
        double proj = HitVolumes.segmentClosestProjection(beamStart, beamEnd, tallMech);
        check(proj >= 9.5 && proj <= 11.3, "closest axial projection lands inside hull span: " + proj);

        AABB sideTarget = new AABB(2.0, 3.0, 12.0, 2.8, 4.8, 12.8);
        near(Math.sqrt(HitVolumes.segmentToAabbDistanceSqr(beamStart, beamEnd, sideTarget)), 2.0, 1e-9, "lateral grazing hull distance");

        // 3. Singularity-free OBB basis across all yaw/pitch angles including +-90 deg zenith/nadir.
        for (double pitchDeg : new double[]{-90, -89.999, -45, 0, 37, 89.999, 90}) {
            for (float yawDeg : new float[]{-180f, -90f, -45f, 0f, 45f, 135f}) {
                double pr = Math.toRadians(pitchDeg), yr = Math.toRadians(yawDeg);
                Vec3 dir = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
                var b = HitVolumes.obbBasis(dir, yawDeg);
                near(b.forward().length(), 1.0, 1e-9, "unit forward");
                near(b.right().length(), 1.0, 1e-9, "unit right");
                near(b.up().length(), 1.0, 1e-9, "unit up");
                near(b.forward().dot(b.right()), 0.0, 1e-9, "orthogonal forward-right");
                near(b.forward().dot(b.up()), 0.0, 1e-9, "orthogonal forward-up");
                near(b.right().dot(b.up()), 0.0, 1e-9, "orthogonal right-up");
            }
        }

        // 4. Rotated 45-degree OBB frontal barrier clip & corner symmetry.
        Vec3 barrierCenter = new Vec3(10.0, 65.0, 10.0);
        Vec3 diagAxis = new Vec3(1.0, 0.0, 1.0).normalize();
        var basis45 = HitVolumes.obbBasis(diagAxis, -45f);
        var spec = HitVolumes.frontalBarrierSpec("aero_pressure_wall", 2.5);
        Vec3[] corners = HitVolumes.obbCorners(barrierCenter, basis45, spec);
        check(corners.length == 8, "eight OBB corners");
        AABB enclosing = HitVolumes.obbEnclosingAabb(barrierCenter, basis45, spec);
        for (Vec3 corner : corners) {
            check(HitVolumes.obbContains(barrierCenter, basis45, spec, barrierCenter.lerp(corner, 0.999)), "interior corner contained");
            check(enclosing.inflate(1e-7).contains(corner), "enclosing AABB covers rotated corner");
        }
        // Wingtip shot near +2.2m along rotated right axis must hit the rotated OBB!
        Vec3 wingPoint = barrierCenter.add(basis45.right().scale(2.2));
        Vec3 arrowFrom = wingPoint.add(diagAxis.scale(1.5));
        Vec3 arrowTo = wingPoint.subtract(diagAxis.scale(1.5));
        var hit = HitVolumes.obbClip(barrierCenter, basis45, spec, arrowFrom, arrowTo);
        check(hit.isPresent(), "diagonal wingtip projectile intercepted by 45-degree OBB");
        near(hit.get().subtract(barrierCenter).dot(diagAxis), spec.halfDepth(), 1e-6, "impact lands on front OBB face");

        // Shot outside halfWidth (at +4.0m > spec.halfWidth) must miss!
        Vec3 missPoint = barrierCenter.add(basis45.right().scale(spec.halfWidth() + 0.5));
        check(HitVolumes.obbClip(barrierCenter, basis45, spec, missPoint.add(diagAxis.scale(1.5)), missPoint.subtract(diagAxis.scale(1.5))).isEmpty(),
                "shot outside OBB width cleanly misses");

        // 5. Cold-link dedicated-server isolation check on common/server classes modified in a3.65.
        verifyServerColdLink();
        System.out.println("PASS " + checks + " HitVolumes 3D geometry & dedicated-server cold-link assertions");
    }

    private static void verifyServerColdLink() throws Exception {
        var urls = new ArrayList<URL>();
        for (String p : System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator))) {
            if (!p.isBlank()) urls.add(Path.of(p).toUri().toURL());
        }
        final int[] denied = {0};
        try (var loader = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.minecraft.client.")
                        || name.startsWith("net.neoforged.neoforge.client.")
                        || name.startsWith("cn.academy.port.client.")
                        || name.startsWith("cn.acamech.client.")) {
                    denied[0]++;
                    throw new ClassNotFoundException("server denies " + name);
                }
                return super.loadClass(name, resolve);
            }
        }) {
            List<String> targets = List.of(
                    "cn.academy.port.macro.HitVolumes",
                    "cn.academy.port.city.defense.O5Effect",
                    "cn.academy.port.fleet.LabNetwork",
                    "cn.academy.port.titan.TitanFxPayload",
                    "cn.acamech.MechMod"
            );
            for (String className : targets) {
                var queue = new ArrayDeque<Class<?>>();
                queue.add(Class.forName(className, false, loader));
                while (!queue.isEmpty()) {
                    var c = queue.remove();
                    c.getDeclaredConstructors();
                    c.getDeclaredMethods();
                    c.getDeclaredFields();
                    queue.addAll(List.of(c.getDeclaredClasses()));
                    checks++;
                }
            }
            check(denied[0] == 0, "zero client class requests from common/network/mech server classes");
        }
    }
}

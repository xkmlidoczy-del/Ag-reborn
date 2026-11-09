package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Bounded accepted-context geometry and source wiring; native pixels are reviewed separately. */
public final class ExpertMiningDecorRegressionTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static ExpertMiningDecor.Ray ray(String skill, long token, boolean shown, boolean active, Point direction, double length, double age) {
        return new ExpertMiningDecor.Ray(skill, token, shown, active, new Point(0, 1.6, 0), direction, length, age);
    }
    private static boolean empty(ExpertMiningDecor.Ray ray) { return ExpertMiningDecor.fx(ray, 20, VisualTier.CINEMATIC).strokes().isEmpty(); }
    public static void main(String[] args) throws Exception {
        Point z = new Point(0, 0, 1);
        check(empty(null), "no accepted context means no decoration");
        for (String skill : List.of("mine_ray_basic", "mine_ray_luck", "meltdowner", ""))
            check(empty(ray(skill, 1, true, true, z, 15, 10)), "other skills retain original visual identity");
        for (boolean shown : List.of(false, true)) for (boolean active : List.of(false, true))
            if (!shown || !active) check(empty(ray("mine_ray_expert", 1, shown, active, z, 15, 10)), "hidden, ended or invalid owner is zero geometry");
        for (long token : new long[]{0, -1}) check(empty(ray("mine_ray_expert", token, true, true, z, 15, 10)), "unaccepted token draws nothing");
        for (double length : new double[]{0, 1, 15.01, Double.NaN, Double.POSITIVE_INFINITY})
            check(empty(ray("mine_ray_expert", 1, true, true, z, length, 10)), "invalid real ray is closed");
        for (Point direction : new Point[]{null, new Point(0, 0, 0), new Point(0, 0, 2), new Point(Double.NaN, 0, 1)})
            check(empty(ray("mine_ray_expert", 1, true, true, direction, 15, 10)), "invalid direction closed");
        for (double age : new double[]{-1, Double.NaN}) check(empty(ray("mine_ray_expert", 1, true, true, z, 15, age)), "invalid age closed");
        for (VisualTier tier : new VisualTier[]{VisualTier.OFF, VisualTier.LOW})
            check(ExpertMiningDecor.fx(ray("mine_ray_expert", 1, true, true, z, 15, 10), 20, tier).strokes().isEmpty(), "original tiers untouched");
        for (Point axis : new Point[]{z, new Point(0, 1, 0), new Point(0, -1, 0), new Point(1, 0, 0), new Point(.6, .8, 0)})
            for (double length : new double[]{1.2, 3, 15}) for (VisualTier tier : new VisualTier[]{VisualTier.STANDARD, VisualTier.CINEMATIC})
                for (double time = 0; time < 200; time += 2.5) {
                    var r = ray("mine_ray_expert", 1, true, true, axis, length, 10);
                    var parts = ExpertMiningDecor.fx(r, time, tier);
                    check(parts.strokes().size() == ExpertMiningDecor.STROKES_PER_CAST, "three open rings and two helix strands have fixed budget");
                    check(parts.sprites().isEmpty() && parts.decals().isEmpty(), "no fabricated impact, debris or hit marker");
                    for (var stroke : parts.strokes()) for (Point p : new Point[]{stroke.segment().start(), stroke.segment().end()}) {
                        check(p.finite(), "finite vertices for all look directions");
                        Point v = p.minus(r.origin()); double along = v.x()*axis.x()+v.y()*axis.y()+v.z()*axis.z();
                        check(along >= -1e-9 && along < length && along <= 4.8 + 1e-9, "all decoration lies inside accepted visual beam axial extent");
                        check(v.minus(axis.scale(along)).length() <= .351, "bounded narrow focus envelope leaves target readable");
                    }
                    var quads = VisualKit.projectParts(parts, new Point(0, 1.6, -2), z, new Point(0, 1, 0), tier, ExpertMiningDecor.MAX_QUADS);
                    check(quads.size() <= ExpertMiningDecor.QUADS_PER_CAST, "center-clear projection can split each stroke into at most two bounded quads");
                }
        String classic = Files.readString(Path.of("src/main/java/cn/academy/port/client/ClassicMeltdownerBeamEffects.java"));
        String method = classic.substring(classic.indexOf("static List<ExpertDecorRay> expertDecorRays()"), classic.indexOf("@SubscribeEvent public static void render"));
        check(classic.contains("MAX_EXPERT_DECOR_CASTS = 4") && classic.contains("record ExpertDecorRay(String skill, long token, Vec3 origin, Vec3 direction, double length, double age)"), "original effect exposes only its own bounded immutable data record");
        check(!classic.contains("ExpertMiningDecor") && !classic.contains("RailThunderArrayGeometry"), "classic remains independent of optional decorator and geometry classpaths");
        for (String guard : List.of("activeLevel != mc.level", "activePlayer != mc.player", "activeConnection != mc.getConnection()", "EXPERT_MINERS.values()", "c.shown && c.valid(mc)", "c.ray.valid(mc)", "c.token > 0", "c.caster.position().subtract(mc.player.position()).lengthSqr() <= 96 * 96", ".limit(MAX_EXPERT_DECOR_CASTS)"))
            check(method.contains(guard), "accepted snapshot/session/owner/cap wiring: " + guard);
        for (String mutation : List.of(".put(", ".remove(", ".clear(", "startLocal(", "updateMineRay(", "new Ray("))
            check(!method.contains(mutation), "snapshot never mutates source effects: " + mutation);
        String renderer = Files.readString(Path.of("src/main/java/cn/academy/port/client/VisualDecorRenderer.java"));
        check(renderer.contains("ClassicMeltdownerBeamEffects.expertDecorRays()") && renderer.contains("new ExpertMiningDecor.Ray(ray.skill(), ray.token(), true, true") && renderer.contains("new Point(origin.x, origin.y, origin.z)") && renderer.contains("new Point(direction.x, direction.y, direction.z)") && renderer.contains("ray.length(), ray.age()") && renderer.contains("ExpertMiningDecor.MAX_QUADS"), "external adapter forwards exact real accepted snapshot geometry with global cap");
        System.out.println("ExpertMiningDecorRegressionTest: " + checks + " checks passed");
    }
}

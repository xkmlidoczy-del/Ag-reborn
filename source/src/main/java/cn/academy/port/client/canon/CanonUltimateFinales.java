package cn.academy.port.client.canon;

import cn.academy.port.VisualTier;
import cn.academy.port.macro.MacroSkillRules;
import cn.academy.port.macro.MacroVfx;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** A short, depth-tested accent for each LV5 receipt. Native meshes and server boundaries retain ownership. */
final class CanonUltimateFinales {
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final float[] IVORY = {0.96f, 0.98f, 1f};

    private CanonUltimateFinales() {}

    static void draw(CanonFx.Canvas canvas, MacroVfx.Event event, MacroSkillRules.Profile profile, Vec3 centre,
                     float[] color, double age, double fade, VisualTier tier, int allowance) {
        if (!profile.ultimate() || age >= 32 || event.phase() == MacroVfx.Phase.END && !event.skill().equals("float_release_field")) return;
        boolean charge = event.phase() == MacroVfx.Phase.CHARGE;
        double progress = age / 32;
        double alpha = fade * (1 - progress) * (charge ? 0.52 : 0.8);
        if (alpha <= 0.025) return;
        double reach = event.actualRadius() > 0 ? Math.min(11, event.actualRadius() * 0.26) : 3.0;
        double size = Math.max(0.6, reach * (charge ? 1.0 - 0.45 * progress : 0.46 + 0.54 * AgModernVfx.snapEase(age / 7)));
        boolean dense = allowance <= 250;
        int segments = !dense && tier == VisualTier.CINEMATIC ? 18 : 12;
        Vec3 anchor = centre.add(0, 0.15, 0);
        Vec3 direction = event.to().subtract(event.from());
        Vec3 forward = direction.lengthSqr() < 0.01 ? Z : direction.normalize();
        double phase = age * (charge ? -0.04 : 0.047) + (event.skill().hashCode() & 15) * 0.13;
        int scope = canvas.limit(tier == VisualTier.LOW ? 36 : dense ? 54 : 94);
        int before = canvas.left;
        try {
            switch (profile.category()) {
                case "electromaster" -> {
                    for (int i = 0; i < 4; i++) {
                        Vec3 tip = anchor.add(radial(phase + i * Math.PI / 2, size, size * 0.92));
                        AgModernVfx.fractalArc(canvas, anchor.add(0, size * 0.35, 0), tip, 2,
                                event.cast().getLeastSignificantBits() + i * 37L, 0.55, 0.13, color, alpha, true);
                    }
                    ring(canvas, anchor, X, Z, size, phase, segments, color, alpha * 0.65);
                }
                case "meltdowner" -> {
                    Vec3 cross = forward.cross(Y);
                    Vec3 side = cross.lengthSqr() < 0.01 ? X : cross.normalize();
                    Vec3 vertical = side.cross(forward);
                    for (int i = 0; i < 3; i++) {
                        Vec3 band = anchor.subtract(forward.scale(size * (0.55 + i * 0.23)));
                        ring(canvas, band, side, vertical, size * (0.22 + i * 0.12), phase + i, 10, color, alpha);
                    }
                }
                case "teleporter" -> {
                    polygon(canvas, anchor, X, Z, size, phase, 4, color, alpha);
                    polygon(canvas, anchor.add(0, size * 0.75, 0), X, Z, size * 0.67, -phase, 4, color, alpha * 0.8);
                    for (int i = 0; i < 4; i++) {
                        Vec3 edge = anchor.add(radial(Math.PI * i / 2 + phase, size, 0));
                        trail(canvas, List.of(edge, edge.add(0, size * 0.85, 0)), 0.07, IVORY, alpha * 0.73);
                    }
                }
                case "vecmanip" -> {
                    for (int i = 0; i < 6; i++) {
                        double turn = i * Math.PI / 3 + phase;
                        Vec3 tip = anchor.add(radial(turn, size, 0.45));
                        Vec3 back = anchor.add(radial(turn, size * 0.73, 0.45));
                        Vec3 side = radial(turn + Math.PI / 2, size * 0.12, 0);
                        trail(canvas, List.of(back.add(side), tip, back.subtract(side)), 0.09, color, alpha);
                        trail(canvas, List.of(anchor.add(radial(turn, size * 0.4, 0.45)), tip), 0.065, IVORY, alpha * 0.73);
                    }
                }
                case "mental_out" -> {
                    Vec3 target = anchor.add(0, size * 0.66, 0);
                    polygon(canvas, target, X, Z, size * 0.75, phase, 5, color, alpha);
                    for (int i = 0; i < 5; i++) {
                        Vec3 point = target.add(radial(i * Math.PI * 0.4 + phase, size * 0.75, 0));
                        trail(canvas, List.of(point, target.add(0, size * 0.36, 0)), 0.075, IVORY, alpha * 0.83);
                    }
                }
                case "dark_matter" -> {
                    for (int i = 0; i < 5; i++) {
                        Vec3 bottom = anchor.add(radial(phase + i * Math.PI * 0.4, size * 0.82, 0.35));
                        Vec3 top = bottom.add(radial(phase + i * Math.PI * 0.4, size * 0.32, size * (i % 2 == 0 ? 0.82 : 0.6)));
                        Canon3dMeshKit.facetedPrism(canvas, bottom, top, 0.23, 4, phase, IVORY, color, alpha, false);
                    }
                    ring(canvas, anchor, X, Z, size, phase, segments, color, alpha * 0.49);
                }
                case "nitrogen_armor" -> {
                    polygon(canvas, anchor, X, Z, size, phase, 6, color, alpha);
                    polygon(canvas, anchor.add(0, size * 0.43, 0), X, Z, size * 0.76, phase, 6, IVORY, alpha * 0.8);
                    polygon(canvas, anchor.add(0, size * 0.8, 0), X, Z, size * 0.39, phase, 6, color, alpha * 0.5);
                }
                case "aero_hand" -> {
                    for (int i = 0; i < 2; i++) {
                        var spiral = new ArrayList<Vec3>(13);
                        for (int j = 0; j <= 12; j++) {
                            double f = j / 12.0;
                            spiral.add(anchor.add(radial(phase + i * Math.PI + f * 6.0, size * (0.46 + f * 0.52), size * f)));
                        }
                        trail(canvas, spiral, 0.12, color, alpha);
                    }
                }
                case "hydro_hand" -> {
                    ring(canvas, anchor, X, Z, size, phase, segments, color, alpha * 0.7);
                    for (int i = 0; i < 6; i++) {
                        double turn = phase + i * Math.PI / 3;
                        trail(canvas, List.of(anchor.add(radial(turn, size * 0.55, 0)),
                                anchor.add(radial(turn, size * 0.75, size * 0.48)),
                                anchor.add(radial(turn, size, size * 0.20))), 0.11, color, alpha);
                    }
                }
                case "float_dial" -> {
                    Vec3 sphere = anchor.add(0, size * 0.68, 0);
                    ring(canvas, sphere, X, Y, size * 0.64, phase, segments, color, alpha);
                    ring(canvas, sphere, Z, Y, size * 0.64, -phase, segments, IVORY, alpha * 0.8);
                    ring(canvas, sphere, X, Z, size * 0.64, phase, segments, color, alpha * 0.72);
                }
                case "liquid_shadow" -> {
                    for (int i = 0; i < 5; i++) {
                        double turn = phase + i * Math.PI * 0.4;
                        trail(canvas, List.of(anchor.add(radial(turn, size * 0.33, 0)),
                                anchor.add(radial(turn, size * 0.74, size * 0.63)),
                                anchor.add(radial(turn + 0.36, size, size * 0.28))), 0.24, color, alpha);
                    }
                }
                case "paper_psychokinesis" -> {
                    for (int i = 0; i < 6; i++) {
                        double turn = phase + i * Math.PI / 3;
                        Vec3 p = anchor.add(radial(turn, size * 0.83, size * 0.42));
                        var nose = p.add(radial(turn, 0.6, 0.36));
                        Vec3 fold = p.add(radial(turn + Math.PI / 2, 0.41, -0.17));
                        trail(canvas, List.of(fold, nose, p.subtract(fold.subtract(p))), 0.095, color, alpha);
                        trail(canvas, List.of(p, nose), 0.05, IVORY, alpha * 0.75);
                    }
                }
            }
        } finally {
            canvas.restore(scope);
            if (CanonFx.QA_BUDGET) canvas.record("ultimate:" + profile.category() + ":" + event.skill(), before);
        }
    }

    private static Vec3 radial(double angle, double radius, double height) {
        return new Vec3(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
    }

    private static void trail(CanonFx.Canvas c, List<Vec3> path, double width, float[] color, double alpha) {
        c.darkSheathRibbon(path, u -> width * (0.77 + 0.23 * Math.sin(u * Math.PI)), u -> alpha, color);
    }

    private static void ring(CanonFx.Canvas c, Vec3 center, Vec3 a, Vec3 b, double radius, double phase,
                             int sides, float[] color, double alpha) {
        AgModernVfx.planarRing(c, center, a, b, radius, phase, sides, 0.12, color, alpha, true);
    }

    private static void polygon(CanonFx.Canvas c, Vec3 center, Vec3 a, Vec3 b, double radius, double phase,
                                int sides, float[] color, double alpha) {
        var path = new ArrayList<Vec3>(sides + 1);
        for (int i = 0; i <= sides; i++) {
            double angle = phase + i * Math.TAU / sides;
            path.add(center.add(a.scale(radius * Math.cos(angle))).add(b.scale(radius * Math.sin(angle))));
        }
        trail(c, path, 0.10, color, alpha);
    }
}

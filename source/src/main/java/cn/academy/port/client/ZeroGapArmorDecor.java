package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import java.util.ArrayList;
import java.util.List;

/**
 * CINEMATIC presentation for NEW252 zero-gap armour: a lit footprint with a pulsing ring, a breathing halo and
 * rim sparkles on every selected plate, drifting green motes around the wearer and a contact burst (spokes, sparks,
 * shock sprite) at each confirmed crossing. Original green-white palette. Pure; never a plate, protection or damage region.
 */
public final class ZeroGapArmorDecor {
    public static final int MAX_QUADS = 520;
    public record Contact(Point at, Point normal, double age) {}
    private static final int CORE = 0xD8F8D8, GREEN = 0x6AF26A, DEEP = 0x1FA54A;

    private ZeroGapArmorDecor() {}

    public static FxLib.Parts fx(Point feet, List<Point> plateCenters, List<Contact> contacts, long seed, double time, VisualTier tier) {
        if (tier == null || tier.layers < 3 || feet == null || !feet.finite() || !Double.isFinite(time)) return FxLib.Parts.EMPTY;
        double w = tier.widthScale, pulse = (time % 60) / 60;
        FxLib.Builder out = new FxLib.Builder();
        out.add(FxLib.groundGlow(feet.plus(new Point(0, .03, 0)), 3.2 * w, GREEN, 85, (.4 + pulse * 3.4) * w, 150 * Math.sin(Math.PI * pulse), tier));
        out.add(FxLib.embers(feet, 1.9 * w, 3.2, seed ^ 0x252, time, 36, CORE, DEEP, tier));
        List<Billboard> sprites = new ArrayList<>();
        for (int i = 0; i < plateCenters.size(); i++) {
            Point c = plateCenters.get(i);
            if (c == null || !c.finite()) continue;
            double breathe = .75 + .25 * Math.sin(time * .12 + i * 2.1);
            sprites.add(new Billboard(c, 1.25 * w, GREEN, FxLib.alpha(70 * tier.glow, breathe), RailThunderArrayGrandGeometry.RADIAL, 0));
            // Four rim sparkles orbiting the plate centre, twinkling out of phase.
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2 + time * .03 + i;
                Point at = c.plus(new Point(Math.cos(a) * .55 * w, Math.sin(a * 1.7) * .5 * w, Math.sin(a) * .55 * w));
                sprites.add(new Billboard(at, .2 * w, CORE, FxLib.alpha(235 * tier.glow, .5 + .5 * Math.sin(time * .4 + k * 1.9 + i)), RailThunderArrayGrandGeometry.STAR, a));
            }
        }
        out.add(new FxLib.Parts(List.of(), List.copyOf(sprites), List.of()));
        for (Contact contact : contacts) {
            if (contact == null || contact.at == null || contact.age < 0 || contact.age >= 8) continue;
            long cs = seed * 31 + (long) (contact.at.x() * 7 + contact.at.z() * 5);
            out.add(FxLib.spokes(contact.at, cs, contact.age, 8, 14, 2.4 * w, CORE, 215, tier));
            out.add(FxLib.sparkShower(contact.at, contact.normal, cs ^ 0x99, contact.age, 30, 8, 1.2, 8, 14, CORE, DEEP, tier));
            out.add(new FxLib.Parts(List.of(), List.of(new Billboard(contact.at, (.3 + contact.age * .55) * w, GREEN, FxLib.alpha(160 * tier.glow, 1 - contact.age / 8), RailThunderArrayGrandGeometry.RING, 0)), List.of()));
        }
        return out.build();
    }
}

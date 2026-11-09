package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Departure and arrival of a skill teleport. A caster whose position jumps by {@link #MIN_JUMP} blocks or more in one client tick while a
 * cast of theirs is live gets, for {@link #LIFE} ticks: an afterimage and rising rings where they were, a data trail along the path and
 * an arrival burst (ground ring, light column, sparks, spokes) where they land. Each skill level adds a layer. View only: it reads
 * positions, never writes any. Moves with no cast of the player (respawn, commands, other mods) are ignored.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class TeleportTrace {
    public static final double MIN_JUMP = 2.5;
    public static final int LIFE = 30, MAX_EVENTS = 12;
    public record Event(Point from, Point to, long start, String category, int level, long seed) {}
    private static final List<Event> EVENTS = new ArrayList<>();
    private static final Map<Integer, Vec3> LAST = new HashMap<>();
    private static Object world;

    private TeleportTrace() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { EVENTS.clear(); LAST.clear(); return; }
        if (world != mc.level) { EVENTS.clear(); LAST.clear(); world = mc.level; }
        if (mc.isPaused()) return;
        long now = mc.level.getGameTime();
        EVENTS.removeIf(e -> now - e.start() > LIFE);
        var casts = CastPoseClient.signatures(0);
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Player p)) continue;
            Vec3 here = p.position(), before = LAST.put(p.getId(), here);
            if (before == null || before.distanceTo(here) < MIN_JUMP || p.isPassenger() || p.isFallFlying()) continue;
            CastPoseClient.SignatureCast cast = null;
            for (var c : casts) if (c.entity() == p.getId()) { cast = c; break; }
            if (cast == null) continue;
            String category = CastSignatureDecor.category(cast, AcademyClient.state.category);
            int level = CastSignatureDecor.level(cast, category);
            if (EVENTS.size() >= MAX_EVENTS) EVENTS.remove(0);
            EVENTS.add(new Event(new Point(before.x, before.y, before.z), new Point(here.x, here.y, here.z), now, category, level, p.getId() * 104729L + now));
        }
        if (LAST.size() > 512) LAST.clear();
    }

    /** Test and probe hook: stage an event without a cast. */
    public static void stage(Event e) { EVENTS.add(e); }

    public static List<Event> events() { return List.copyOf(EVENTS); }

    public static FxLib.Parts fx(double gameTime, VisualTier tier) {
        if (EVENTS.isEmpty() || tier == null || !tier.enhanced()) return FxLib.Parts.EMPTY;
        var out = new FxLib.Builder();
        for (var e : EVENTS) out.add(one(e, gameTime - e.start(), tier));
        return out.build();
    }

    static FxLib.Parts one(Event e, double age, VisualTier tier) {
        if (age < 0 || age > LIFE) return FxLib.Parts.EMPTY;
        int lv = Math.max(1, Math.min(5, e.level())), color = switch (e.category()) {
            case "electromaster" -> CastSignatureDecor.EM; case "meltdowner" -> CastSignatureDecor.MD; case "vecmanip" -> CastSignatureDecor.VM; default -> CastSignatureDecor.TP; };
        double fade = Math.max(0, 1 - age / LIFE), quick = Math.max(0, 1 - age / 14.0), w = tier.widthScale;
        var out = new FxLib.Builder();
        Point up = new Point(0, 1, 0), from = e.from(), to = e.to();
        // Where they were: a ghost box and two rings rising off it.
        var strokes = new ArrayList<Stroke>();
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4, x = Math.cos(a) * .38, z = Math.sin(a) * .38;
            CastSignatureDecor.line(strokes, from.plus(new Point(x, 0, z)), from.plus(new Point(x, 1.8, z)), .012 * w, color, (int) (200 * quick));
        }
        out.add(new FxLib.Parts(List.copyOf(strokes), List.of(), List.of()));
        for (int k = 0; k < Math.min(3, 1 + lv / 2); k++)
            out.add(CastSignatureDecor.ring(from.plus(new Point(0, .1 + age * (.09 + .02 * k) + k * .3, 0)), up, .55 - k * .08 + age * .015, color, 200 * quick, age * .1, 14, tier));
        out.add(FxLib.groundGlow(from.plus(new Point(0, .03, 0)), .5, color, 90 * quick, .6 + age * .1 * lv, 150 * quick, tier));
        // The path: a trail that shrinks toward the arrival point.
        double trail = Math.max(0, 1 - age / 12.0);
        if (trail > 0) {
            Point mid = from.lerp(to, 1 - trail * .15);
            out.add(FxLib.dispersion(from.plus(new Point(0, .9, 0)), mid.plus(new Point(0, .9, 0)), .02 + .006 * lv, 160 * trail, tier));
            if (lv >= 3) out.add(FxLib.lightning(from.plus(new Point(0, .9, 0)), to.plus(new Point(0, .9, 0)), e.seed(), age, 8, .3, .012, 0xFFFFFF, color, trail * .7, tier));
        }
        // Where they land.
        double burst = Math.min(1, age / 2.0) * fade;
        out.add(FxLib.groundGlow(to.plus(new Point(0, .03, 0)), .6 + .1 * lv, color, 120 * burst, .5 + age * .22 * (1 + lv * .25), 220 * fade, tier));
        var column = new ArrayList<Stroke>();
        CastSignatureDecor.line(column, to, to.plus(new Point(0, 1.5 + .5 * lv, 0)), .03 * w, 0xFFFFFF, (int) (230 * Math.max(0, 1 - age / 10.0)));
        CastSignatureDecor.line(column, to, to.plus(new Point(0, 1.5 + .5 * lv, 0)), .1 * w, color, (int) (110 * Math.max(0, 1 - age / 14.0)));
        out.add(new FxLib.Parts(List.copyOf(column), List.of(), List.of()));
        if (lv >= 2) out.add(CastSignatureDecor.ring(to.plus(new Point(0, .1 + age * .06, 0)), up, .7 + age * .05, 0xFFFFFF, 190 * fade, -age * .12, 16, tier));
        if (lv >= 4) out.add(FxLib.sparkShower(to.plus(new Point(0, .1, 0)), up, e.seed() + 1, age, 14 + 4 * lv, 5.5, 1.2, 9, 16, 0xFFFFFF, color, tier));
        if (lv >= 5) {
            out.add(FxLib.spokes(to.plus(new Point(0, 1, 0)), e.seed(), age, 14, 14, 1.4, color, 200, tier));
            out.add(CastSignatureDecor.cube(to.plus(new Point(0, .9, 0)), .9 - Math.min(.6, age * .04), color, 160 * fade, age * .08, tier));
        }
        return out.build();
    }
}

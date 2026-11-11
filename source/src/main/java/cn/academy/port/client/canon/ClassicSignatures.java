package cn.academy.port.client.canon;

import static cn.academy.port.client.canon.CanonFxScripts.*;
import static cn.academy.port.client.canon.CanonSignatures.*;

import cn.academy.port.client.canon.CanonFx.Canvas;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Per-skill signature details for the original four systems (Electromaster, Meltdowner, Teleporter, Vector Manipulation). Their
 * choreography classes and motifs stay as they are; this layer adds the one idea each skill's description names (Directional Shock's
 * compressed fist and 3 m cone, Thunder Bolt's 8 m secondary discharge, Dual Point's two converging beams...), including the hold
 * phase of charge and hold skills. The seventeen OWN skills keep their bespoke renderers and get nothing here. Presentation only.
 */
public final class ClassicSignatures {
    /** One cast: hold time runs while the key is down, t counts ticks since release (negative while holding). */
    record C(String skill, int tier, double k, Vec3 hand, Vec3 end, Vec3 ground, Vec3 f, Vec3 r, Vec3 feet, double t, double hold, double e, double life) {
        boolean held() { return t < 0; }
        double grow(double ticks) { return easeOutBack(clamp01((held() ? hold : t) / ticks)); }
        double ph(double ticks) { return clamp01(t / ticks); }
        double charge(double ticks) { return clamp01(hold / ticks); }
        Vec3 dir() { Vec3 d = end.subtract(hand); return d.lengthSqr() < 1e-6 ? f : d.normalize(); }
        double dist() { return end.distanceTo(hand); }
    }
    private static final class Live {
        final String skill; final int tier; Vec3 hand, end, ground, feet; final double start; double release = Double.NaN, seen;
        Live(String skill, int tier, double start) { this.skill = skill; this.tier = tier; this.start = start; }
    }
    private static final Map<String, BiConsumer<Canvas, C>> SIG = new HashMap<>();
    private static final Map<String, Integer> LIFE = new HashMap<>();
    private static final Map<Long, Live> LIVE = new HashMap<>();
    private static Object world;
    private static final java.util.Set<String> FAILED = new java.util.HashSet<>();
    private static final float[] EM = {.37f, .83f, 1f}, EM_W = {.9f, .97f, 1f}, IRON = {.42f, .48f, .56f}, MD = {.43f, 1f, .59f}, MD_W = {.85f, 1f, .88f},
            GOLD = {1f, .85f, .4f}, TP = {.65f, .43f, 1f}, TP_W = {.9f, .85f, 1f}, VM = {.7f, .84f, 1f}, VM_W = {.94f, .98f, 1f}, BLOOD = {1f, .3f, .3f};
    private ClassicSignatures() {}

    public static boolean wants(String skill) { return SIG.containsKey(skill); }
    static boolean any() { return !LIVE.isEmpty(); }
    static int count() { return LIVE.size(); }
    static void clear() { LIVE.clear(); world = null; }

    /** Fed every render frame for each live classic cast, held or released; the first released frame freezes the aim. */
    public static void observe(long key, String category, String skill, int level, Vec3 hand, Vec3 end, Vec3 ground, Vec3 feet, boolean released) {
        var mc = Minecraft.getInstance(); if (mc.level != world) { clear(); world = mc.level; }
        if (mc.level == null || !SIG.containsKey(skill) || hand == null || end == null || ground == null || feet == null) return;
        double now = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        Live l = LIVE.get(key);
        if (l == null) { if (LIVE.size() > 64) return; l = new Live(skill, Math.max(1, Math.min(5, level)), now); LIVE.put(key, l); }
        l.seen = now;
        if (!Double.isNaN(l.release)) return;
        l.hand = hand; l.end = end; l.ground = ground; l.feet = feet;
        if (released) { l.release = now; CanonEnvironment.classic(skill, category, l.tier, hand, end, ground, feet); }
    }

    static void draw(Canvas c, double now, int allowance) {
        for (Iterator<Live> it = LIVE.values().iterator(); it.hasNext(); ) {
            Live l = it.next(); int life = LIFE.getOrDefault(l.skill, 22);
            boolean released = !Double.isNaN(l.release);
            double t = released ? now - l.release : -1, hold = (released ? l.release : now) - l.start;
            if (released ? t > life : now - l.seen > 3) { it.remove(); continue; }
            double e = released ? clamp01(Math.min(1, (life - t) / (life * .35))) : clamp01(hold / 3);
            Vec3 f = flat(l.end.subtract(l.hand)), r = rightOf(f);
            int before=c.left,scope=c.limit(allowance);
            try {
                SIG.get(l.skill).accept(c, new C(l.skill, l.tier, .75 + .17 * l.tier, l.hand, l.end, l.ground, f, r, l.feet, t, hold, e, life));
            } catch (RuntimeException decorationOnly) { if (FAILED.add(l.skill)) com.mojang.logging.LogUtils.getLogger().warn("Classic signature {} failed and is skipped", l.skill, decorationOnly); it.remove(); }
            finally {c.restore(scope);c.record("classic:"+l.skill,before);}
        }
    }

    /** QA: a synthetic cast from the local player at the given hold length and time since release (-1 = still holding). */
    public static void qa(String skill, int level, double held, double since) {
        var mc = Minecraft.getInstance(); if (mc.player == null || mc.level == null) return;
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getViewVector(1);
        var clip = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(24)), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        Vec3 end = clip.getLocation(), hand = eye.add(0, -.35, 0).add(rightOf(flat(look)).scale(.3)).add(look.scale(.3));
        double now = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        world = mc.level; long key = -1 - Math.abs((long) skill.hashCode());
        Live l = new Live(skill, level, now - held - Math.max(0, since)); l.hand = hand; l.end = end; l.feet = mc.player.position();
        l.ground = new Vec3(end.x, mc.player.getY(), end.z); l.seen = now + 400; if (since >= 0) l.release = now - since;
        LIVE.put(key, l);
        if (since >= 0) { String category = cn.academy.port.SkillCatalog.REGISTERED.stream().filter(sk -> sk.id().equals(skill)).map(sk -> sk.category()).findFirst().orElse("vecmanip");
            CanonEnvironment.classic(skill, category, level, hand, end, l.ground, l.feet); }
    }

    // ------------------------------------------------------------------ helpers
    /** A jagged lightning bolt that re-rolls every two ticks. */
    private static void bolt(Canvas c, Vec3 a, Vec3 b, double w, float[] rgb, double al, double jag, double seedT) {
        int n = Math.max(4, Math.min(48, (int) (a.distanceTo(b) * 1.6))); Vec3 d = b.subtract(a), side = rightOf(d.normalize()), up = upOf(d.normalize(), side);
        int s = (int) Math.floor(seedT / 2); Vec3 prev = a;
        for (int i = 1; i <= n; i++) {
            double u = i / (double) n, amp = i == n ? 0 : jag * Math.sin(Math.PI * u);
            Vec3 p = a.add(d.scale(u)).add(side.scale((rnd(s * 31 + i) - .5) * 2 * amp)).add(up.scale((rnd(s * 17 + i + 5) - .5) * 2 * amp));
            strip(c, "em_arc_sheet", prev, p, w * 2, (s + i) % 8, rgb, al);
            strip(c, "common_speedline", prev, p, w * .28, 0, rgb, al * .8); prev = p;
        }
    }
    private static void beam(Canvas c, Vec3 a, Vec3 b, double w, float[] rgb, double al) {
        if(AgEffectMaterials.beams()){c.energyStream(a,b,w,rgb,al);return;}
        strip(c, "md_beam_halo", a, b, w * 2.4, 0, rgb, al * .7);
        strip(c, "md_beam_core", a, b, w, 0, MD_W, al);
    }
    private static void orb(Canvas c, Vec3 at, double size, double ph, float[] rgb, double al) {
        sprite(c, "md_charge_sheet", at, size, 0, Fx4Atlas.get("md_charge_sheet").frame(ph), rgb, al, true);
    }
    private static void hex(Canvas c, Vec3 at, Vec3 ax, Vec3 ay, float[] rgb, double al) { glow(c, "md_hex_plate", at, ax, ay, 0, rgb, al); }
    private static void reticle(Canvas c, Vec3 at, double size, double spin, float[] rgb, double al) { sprite(c, "tp_reticle", at, size, spin, 0, rgb, al, true); }
    private static void rift(Canvas c, Vec3 at, double size, double ph, double al) {
        if (ph < 0 || ph >= 1) return; c.cellSprite("fx4:tp_rift_sheet", CanonFx4.Mode.ALPHA, at, size, 0, Fx4Atlas.get("tp_rift_sheet").frame(ph), TP_W, (float) (al * (1 - ph * .6)));
    }
    private static void ghost(Canvas c, Vec3 feet, Vec3 r, double al) { plane(c, "tp_afterimage_mask", feet.add(0, .95, 0), r.scale(.45), UP.scale(.95), 0, TP_W, al); }
    private static void column(Canvas c, Vec3 at, double h, double w, float[] rgb, double al) {
        strip(c, "tp_lattice", at, at.add(0, h, 0), w * 1.8, 0, rgb, al);
        strip(c, "common_speedline", at, at.add(0, h, 0), w * .16, 0, rgb, al * .55);
    }
    private static void arrows(Canvas c, Vec3 a, Vec3 b, double w, float[] rgb, double al) { strip(c, "vm_arrow_field", a, b, w, 0, rgb, al); }
    private static void shear(Canvas c, Vec3 a, Vec3 b, double w, float[] rgb, double al) { strip(c, "vm_shear_streak", a, b, w, 0, rgb, al); }
    private static void pressure(Canvas c, Vec3 at, Vec3 ax, Vec3 ay, double ph, float[] rgb, double al) {
        if (ph < 0 || ph >= 1) return; glow(c, "vm_pressure_sheet", at, ax, ay, Fx4Atlas.get("vm_pressure_sheet").frame(ph), rgb, al);
    }
    /** Air pressure bending the scene: an expanding refraction disc, legible in any light. */
    private static void shock(Canvas c, Vec3 at, double size, double ph, double strength) {
        if (ph < 0 || ph >= 1) return; c.refract("fx4:dm_refraction_normal", false, at, size * (.35 + .65 * easeOut(ph)), (float) (strength * (1 - ph)), VM_W);
    }
    private static Vec3 gnd(Vec3 p, double y) { return new Vec3(p.x, y + .06, p.z); }
    private static Vec3 flatX(double s) { return new Vec3(s, 0, 0); }
    private static Vec3 flatZ(double s) { return new Vec3(0, 0, s); }
    private static void fan(Canvas c, Vec3 at, Vec3 f, Vec3 r, double rad, double span, int n, String key, float[] rgb, double al) {
        var p = new ArrayList<Vec3>(); for (int i = 0; i <= n; i++) p.add(at.add(rot(f, r, -span / 2 + span * i / n).scale(rad)));
        ribbon(c, key, p, .5, rgb, al, 0);
    }
    private static void reg(int life, BiConsumer<Canvas, C> s, String... ids) { for (String id : ids) { SIG.put(id, s); LIFE.put(id, life); } }

    static {
        reg(30,(c,x)->{
            Vec3 core=x.feet().add(0,1,0);double tt=x.held()?x.hold():x.t();
            for(int i=0;i<4;i++){double a=i*Math.PI/2+tt*.04;Vec3 d=rot(x.f(),x.r(),a);
                var p=arc(core.add(d.scale(1.5)).add(0,-.6,0),core.add(d.scale(2.4)).add(0,.8,0),.55,8);
                ribbon(c,"em_fieldline",p,.4,EM,.75*x.e(),tt*.1);ribbon(c,"common_speedline",p,.055,EM,.6*x.e(),0);}
        },"magnetic_domain");
        reg(22,(c,x)->{
            Vec3 at=x.feet().add(0,1.1,0).add(x.f().scale(1.2));double open=x.grow(6);
            for(int i=-1;i<=1;i++)hex(c,at.add(x.r().scale(i*.62*open)),x.r().scale(.5*open),UP.scale(.78*open),MD,.8*x.e());
            for(int s:new int[]{-1,1})strip(c,"md_beam_halo",at.add(x.r().scale(s*1.1)).add(0,-.7,0),at.add(x.r().scale(s*1.1)).add(0,.7,0),.08,0,MD_W,.7*x.e());
        },"light_shield");
        // ============================================================ Electromaster: jagged arcs, field lines, iron sand
        reg(18, (c, x) -> { if (x.held()) return; bolt(c, x.hand(), x.end(), .2 * x.k(), EM_W, x.e(), .35, x.t()); flash(c, x.end(), 1.8 * x.k(), x.ph(10), EM, x.e()); }, "arc_gen");
        reg(16, (c, x) -> { if (x.held()) return; bolt(c, x.hand(), x.end(), .15 * x.k(), EM_W, x.e(), .35, x.t());
            for (int i = -1; i <= 1; i += 2) { Vec3 fork = x.end().add(x.r().scale(i * 3)).add(0, -.3, 0); bolt(c, x.end(), fork, .09, EM, x.e() * clamp01((x.t() - 2) / 2), .4, x.t() + i * 7); flash(c, fork, .9, (x.t() - 3) / 6, EM, x.e()); } }, "forked_arc");
        reg(24, (c, x) -> { for (int i = 0; i < 6; i++) { double a = i * 1.047 + (x.held() ? x.hold() : x.t()) * .15, rad = 1.3 * (1 - frac((x.held() ? x.hold() : x.t()) * .06 + i / 6.0));
                sprite(c, "em_spark_sheet", x.end().add(Math.cos(a) * rad, .3 + rad * .3, Math.sin(a) * rad), .2, a, i, EM_W, x.e(), true); }
            ring(c, x.end(), x.r().scale(.8), UP.scale(.8), frac(Math.max(0, x.t()) / 10), EM, x.e()); }, "charging");
        reg(10, (c, x) -> { for (int i = 0; i < 5; i++) { var p = new ArrayList<Vec3>(); for (int j = 0; j <= 10; j++) { double a = -1.05 + 2.1 * j / 10; p.add(x.hand().add(rot(x.f(), x.r(), a).scale(2.2 + i * .45)).add(0, (i - 2) * .35, 0)); }
                ribbon(c, "em_fieldline", p, .36, EM, .9 * x.e(), (x.held() ? x.hold() : x.t()) * .15 + i * .2);
                ribbon(c, "common_speedline", p, .075, EM, .65 * x.e(), 0); } }, "magnetic_bracer");
        reg(18, (c, x) -> { for (int i = 0; i < 3; i++) { double off = (i - 1) * .35; ribbon(c, "em_fieldline", arc(x.hand(), x.end(), off, 10), .2, EM, x.e(), -(x.held() ? x.hold() : x.t()) * .3); }
            strip(c, "common_speedline", x.hand(), x.end(), .07, 0, EM, .8 * x.e());
            sprite(c, "em_spark_sheet", x.end(), 1.3, x.t() * .2, 0, EM_W, x.e(), true); }, "mag_movement");
        reg(20, (c, x) -> { for (int i = 0; i < 3; i++) { double tt = (x.held() ? x.hold() : x.t()) * .12 + i * 1.05; Vec3 ax = rot(x.f(), x.r(), tt).scale(.95), ay = UP.scale(.95);
                ring(c, x.end(), i == 1 ? flatX(.95) : ax, i == 1 ? flatZ(.95) : ay, .35, EM, x.e()); }
            ribbon(c, "em_fieldline", arc(x.hand(), x.end(), .5, 10), .16, EM, .7 * x.e(), x.t() * .2); }, "mag_manip");
        reg(40, (c, x) -> { if (x.held()) return; for (int i = 0; i < 3; i++) { double u = clamp01((x.t() - i * 5) / 24), rad = 15 * x.k() * u;
                ring(c, gnd(x.feet(), x.feet().y), flatX(rad), flatZ(rad), .2 + .5 * u, EM, .8 * x.e() * (1 - u)); }
            if (x.held()) column(c, x.feet(), .8, .18, EM, .55 * x.e()); }, "mine_detect");
        reg(14, (c, x) -> { Vec3 core = x.feet().add(0, 1, 0); double ch = x.held() ? x.charge(40) : 1;
            for (int i = 0; i < 2 + (int) (ch * 4); i++) { double a = i * 1.7 + Math.floor((x.held() ? x.hold() : x.t()) / 2) * 2.3; Vec3 o = new Vec3(Math.cos(a) * .45, (rnd(i * 3 + (int) x.hold()) - .5) * 1.6, Math.sin(a) * .45);
                bolt(c, core.add(o), core.add(o.scale(-.6)).add(0, .5, 0), .13, EM_W, .9 * x.e(), .15, x.hold() + i); }
            if (!x.held()) flash(c, core, 2.6, x.ph(8), EM, x.e()); }, "body_intensify");
        reg(22, (c, x) -> { if (x.held()) return; bolt(c, x.hand(), x.end(), .32 * x.k(), EM_W, x.e(), .5, x.t()); flash(c, x.end(), 2.4 * x.k(), x.ph(6), EM_W, x.e());
            for (int i = 0; i < 6; i++) { double a = i * 1.047 + .3; Vec3 tip = gnd(x.end(), x.ground().y).add(Math.cos(a) * 8 * x.grow(6), 0, Math.sin(a) * 8 * x.grow(6));
                if (x.t() > 3) bolt(c, x.end(), tip, .1, EM, x.e(), .6, x.t() + i * 5); }
            c.ground("ring", true, gnd(x.end(), x.ground().y), 8 * x.grow(6), EM, (float) (.5 * x.e())); }, "thunder_bolt");
        reg(40, (c, x) -> { Vec3 at = x.held() ? x.end() : x.end().add(0, .6, 0); sprite(c, "em_spark_sheet", at, .7 + .1 * Math.sin((x.held() ? x.hold() : x.t()) * .6), 0, (int) ((x.held() ? x.hold() : x.t()) * .8) % 16, EM_W, x.e(), true);
            flash(c, at, .9, frac((x.held() ? x.hold() : x.t()) / 10), EM, .8 * x.e());
            for (int i = 0; i < 3; i++) { double a = i * 2.09 + x.t() * .1; var path = arc(at, at.add(Math.cos(a) * 1.3, .5, Math.sin(a) * 1.3), .8, 6);
                ribbon(c, "em_fieldline", path, .3, EM, .8 * x.e(), x.t() * .2); ribbon(c, "common_speedline", path, .055, EM, .65 * x.e(), 0); } }, "charge_decoy");
        reg(30, (c, x) -> { var path = arc(x.hand(), x.end(), -.6, 12); ribbon(c, "em_fieldline", path, .32, EM, x.e(), x.t() * .3);
            ribbon(c, "common_speedline", path, .08, EM, .8 * x.e(), 0);
            sprite(c, "em_spark_sheet", x.end(), .6, 0, 5, new float[]{1f, .4f, .4f}, x.e(), true); sprite(c, "em_spark_sheet", x.hand(), .4, 0, 5, EM, x.e(), true); }, "polarity_tether");
        reg(24, (c, x) -> { Vec3 a = gnd(x.feet(), x.ground().y), b = gnd(x.end(), x.ground().y); bolt(c, a, b, .12, EM, x.e(), .2, x.t());
            for (Vec3 p : new Vec3[]{a, b}) { column(c, p, 1, .2, EM, .6 * x.e()); c.ground("ring", true, p, .9, EM, (float) (.6 * x.e())); } }, "conductive_circuit");
        reg(30, (c, x) -> { Vec3 f0 = gnd(x.feet(), x.feet().y); for (int i = 0; i < 4; i++) { double a = i * 1.57 + .4; bolt(c, x.feet().add(0, 1, 0), f0.add(Math.cos(a) * 1.2, 0, Math.sin(a) * 1.2), .14, EM_W, .9 * x.e(), .15, x.t() + i * 3); }
            ring(c, f0, flatX(1.6), flatZ(1.6), .35, EM, .8 * x.e()); }, "grounding_return");
        reg(30, (c, x) -> { if (x.held()) return; Vec3 f0 = gnd(x.feet(), x.feet().y); for (int s = -1; s <= 1; s += 2) bolt(c, f0.add(x.r().scale(s * .35)).add(x.f().scale(-2.5)), f0.add(x.r().scale(s * .35)).add(x.f().scale(.6)), .1, EM, x.e(), .08, x.t() + s);
            for (int i = 0; i < 3; i++) c.ground("ring", true, f0.add(x.f().scale(-i * .9)), .5, EM_W, (float) (.5 * x.e() * (1 - i * .3))); }, "magnetic_ground_step");
        reg(30, (c, x) -> { Vec3 at = gnd(x.end(), x.ground().y); if (x.held()) { c.ground("ring2", true, at, 2.2, EM, (float) (.6 * x.e())); return; }
            for (int i = 0; i < 4; i++) { double a = i * 1.57; ribbon(c, "em_fieldline", arc(at.add(Math.cos(a) * 1.5, .3, Math.sin(a) * 1.5), x.hand(), 1.2, 10), .14, EM, .8 * x.e(), -x.t() * .3); } }, "magnetic_item_pull");
        reg(20, (c, x) -> { if (x.held()) { double ch = x.charge(12); for (int i = 0; i < 10; i++) { double a = i * .628 + x.hold() * .35, rad = .7 - .35 * ch;
                    sprite(c, "em_ironsand", x.hand().add(Math.cos(a) * rad, Math.sin(a * 2) * .2, Math.sin(a) * rad), .16, a, i, IRON, x.e(), false); } return; }
            for (int i = 0; i < 3; i++) { double u = clamp01((x.t() - i * 3) / 4); if (u <= 0) continue; Vec3 tip = x.hand().add(x.dir().scale(x.dist() * u));
                c.cellStrip("fx4:em_ironsand", CanonFx4.Mode.ALPHA, tip.subtract(x.dir().scale(1.6)), tip, .14, i, IRON, (float) x.e()); bolt(c, tip.subtract(x.dir().scale(1.2)), tip, .05, EM, .7 * x.e(), .1, x.t() + i); } }, "iron_sand_lance");
        reg(24, (c, x) -> { Vec3 at = x.end(); var nodes = new ArrayList<Vec3>(); for (int i = 0; i < 6; i++) { double a = i * 1.047 + .5; nodes.add(at.add(Math.cos(a) * 4, (rnd(i) - .5) * 1.2, Math.sin(a) * 4)); }
            bolt(c, x.hand(), at, .14, EM_W, x.e(), .3, x.t()); for (int i = 0; i < 6; i++) { bolt(c, at, nodes.get(i), .08, EM, x.e(), .35, x.t() + i); bolt(c, nodes.get(i), nodes.get((i + 1) % 6), .06, EM, .7 * x.e(), .3, x.t() + i * 9); } }, "thunder_net");
        reg(30, (c, x) -> { Vec3 at = x.feet().add(0, 1, 0); for (int i = 0; i < 4; i++) fan(c, at.add(0, (i - 1.5) * .45, 0), x.f(), x.r(), 1.6, 2.1, 12, "em_fieldline", EM, .55 * x.e()); }, "potential_buffer");
        reg(24, (c, x) -> {
            if (!x.held()) return; // The accepted NewSkillFx receipt owns the measured sweep and discharge.
            Vec3 core = x.feet().add(0, .9, 0);
            for (int i = 0; i < 6; i++) {
                Vec3 d = rot(x.f(), x.r(), i * Math.PI / 3);
                strip(c, "common_speedline", core.add(d.scale(.7)), core.add(d.scale(1.5)), .065, 0, EM, .7 * x.e());
            }
        }, "pulse_shield");
        reg(30, (c, x) -> { Vec3 a = gnd(x.feet(), x.feet().y), b = gnd(x.end(), x.ground().y), d = b.subtract(a); int n = Math.max(2, (int) (d.length() / 1.2));
            for (int s = -1; s <= 1; s += 2) bolt(c, a.add(rightOf(d.normalize()).scale(s * .4)), b.add(rightOf(d.normalize()).scale(s * .4)), .13, EM, x.e(), .03, x.t());
            for (int i = 0; i <= n; i++) { Vec3 p = a.add(d.scale(i / (double) n)); strip(c, "em_fieldline", p.add(rightOf(d.normalize()).scale(-.55)), p.add(rightOf(d.normalize()).scale(.55)), .12, 0, EM_W, .6 * x.e()); } }, "rail_relay");

        // ============================================================ Meltdowner: charge orbs, beam cores, hex plates
        reg(16, (c, x) -> { Vec3 orbAt = x.hand().add(x.f().scale(.4)).add(0, .4, 0);
            if (x.held() || x.t() < 4) { orb(c, orbAt, .6 + .4 * clamp01(x.hold() / 10), frac((x.held() ? x.hold() : x.t()) / 16), MD, x.e()); if (x.held()) return; }
            double w = .22 * x.k() * (1 - x.ph(16)); beam(c, orbAt, x.end(), w, MD, x.e()); flash(c, x.end(), 1.8 * x.k(), x.ph(8), MD, x.e()); }, "electron_bomb", "meltdowner");
        reg(6, (c, x) -> { double w = x.skill().equals("mine_ray_basic") ? .08 : .12; beam(c, x.hand(), x.end(), w, x.skill().equals("mine_ray_luck") ? GOLD : MD, x.e());
            orb(c, x.end(), .5, frac((x.held() ? x.hold() : x.t()) / 12), x.skill().equals("mine_ray_luck") ? GOLD : MD, x.e());
            if (x.skill().equals("mine_ray_expert")) hex(c, x.end(), x.r().scale(.5), UP.scale(.5), MD_W, .7 * x.e());
            if (x.skill().equals("mine_ray_luck")) for (int i = 0; i < 4; i++) { double a = (x.held() ? x.hold() : x.t()) * .3 + i * 1.57; sprite(c, "em_spark_sheet", x.end().add(Math.cos(a) * .5, .3, Math.sin(a) * .5), .15, 0, i, GOLD, x.e(), true); } },
            "mine_ray_basic", "mine_ray_expert", "mine_ray_luck");
        reg(16, (c, x) -> { if (x.held()) return; beam(c, x.hand(), x.end(), .12, MD, x.e()); hex(c, x.end(), x.r().scale(.4), UP.scale(.4), MD_W, x.e());
            for (int i = 0; i < 7; i++) { Vec3 d = rot(x.f(), x.r(), (i - 3) * .18); if (x.t() > 2) beam(c, x.end(), x.end().add(d.scale(12 * x.ph(6))), .07, MD, x.e()); } }, "ray_barrage");
        reg(16, (c, x) -> { if (x.held()) { reticle(c, x.end(), 1, x.hold() * .1, MD, .7 * x.e()); return; }
            beam(c, x.hand(), x.hand().add(x.dir().scale(x.dist() * x.ph(5))), .3, MD, x.e() * (1 - x.ph(14)));
            for (int i = 0; i < 3; i++) glow(c, "md_beam_halo", x.feet().add(0, .9, 0).add(x.f().scale(-.6 - i * .6)), x.f().scale(-.5), UP.scale(.3 + i * .12), 0, MD, x.e() * (1 - i * .3)); }, "jet_engine");
        reg(30, (c, x) -> { Vec3 core = x.feet().add(0, 3, 0); orb(c, core, 1.6, frac((x.held() ? x.hold() : x.t()) / 20), MD, x.e());
            for (int i = 0; i < 4; i++) { double a = i * 1.57 + Math.floor((x.held() ? x.hold() : x.t()) / 8); Vec3 tgt = x.feet().add(Math.cos(a) * 7, .8, Math.sin(a) * 7);
                double u = frac((x.held() ? x.hold() : x.t()) / 8); if (u < .5) ribbon(c, "md_beam_core", arc(core, tgt, 1.5, 8), .12, MD_W, x.e() * (1 - u * 2), 0); } }, "electron_missile");
        reg(16, (c, x) -> { if (x.held()) return; Vec3 other = x.end().add(x.r().scale(6)).add(x.f().scale(-3)).add(0, 1.5, 0); beam(c, x.hand(), x.end(), .1, MD, x.e());
            if (x.t() > 2) beam(c, other, x.end(), .1, MD, x.e()); orb(c, other, .5, x.ph(10), MD, x.e()); reticle(c, x.end(), 1.2 * x.grow(4), x.t() * .2, MD_W, x.e()); }, "dual_point");
        reg(20, (c, x) -> { for (int i = -1; i <= 1; i++) { Vec3 d = rot(x.f(), x.r(), i * .55); hex(c, x.feet().add(0, 1.1, 0).add(d.scale(1.6 * x.grow(6))), rightOf(d).scale(.5), UP.scale(.55), MD, .8 * x.e()); } }, "scatter_plates");
        reg(30, (c, x) -> { Vec3 at = gnd(x.end(), x.ground().y); for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) { Vec3 p = at.add(i, 0, j);
                double u = clamp01(((x.held() ? x.hold() : x.t()) - (i + 1) * 2 - (j + 1) * 6) / 4); if (u > 0) hex(c, p, flatX(.45 * u), flatZ(.45 * u), x.held() ? MD : MD_W, .6 * x.e()); }
            if (!x.held()) beam(c, x.hand(), at, .06, MD, x.e()); }, "beam_etching");
        reg(24, (c, x) -> { Vec3 m = x.end(); hex(c, m, x.r().scale(.6), UP.scale(.8), MD_W, x.e()); if (x.held()) return;
            beam(c, x.hand(), m, .1, MD, x.e()); Vec3 out = m.add(rot(x.f(), x.r(), 1.2).scale(10 * x.ph(5))); beam(c, m, out, .1, MD, x.e()); }, "folded_mirror_array");
        reg(14, (c, x) -> { Vec3 f0 = x.feet().add(0, -.1, 0); hex(c, f0, flatX(.8), flatZ(.8), MD, x.e()); ring(c, f0, flatX(1.4), flatZ(1.4), x.ph(10), MD_W, x.e());
            for (int i = 0; i < 3; i++) beam(c, f0, f0.add(0, -1.2, 0).add(x.r().scale(i - 1)), .06, MD, x.e() * (1 - x.ph(8))); }, "beam_step");
        reg(40, (c, x) -> { Vec3 a = x.feet().add(x.r().scale(-3)).add(x.f().scale(4)).add(0, 1, 0), b = a.add(x.r().scale(6));
            for (Vec3 p : new Vec3[]{a, b}) hex(c, p, x.f().scale(.5), UP.scale(1), MD_W, x.e());
            if (!x.held()) for (int i = 0; i < 4; i++) { double y = (i - 1.5) * .4; if (frac(x.t() / 20 + i * .25) < .3) beam(c, a.add(0, y, 0), b.add(0, y, 0), .05, MD, x.e()); } }, "particle_laminate");
        reg(10, (c, x) -> { for (int i = -1; i <= 1; i++) { Vec3 d = rot(x.f(), x.r(), i * .7); hex(c, x.feet().add(0, 1, 0).add(d.scale(2)), rightOf(d).scale(1), UP.scale(1.1), MD, .65 * x.e()); } }, "directional_dissipation_curtain");
        reg(30, (c, x) -> { Vec3 core = x.feet().add(0, 1, 0); double spin = (x.held() ? x.hold() : x.t()) * .08;
            for (int i = 0; i < 6; i++) { double a = i * 1.047 + spin; Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a)); boolean front = d.dot(x.f()) > .3;
                hex(c, core.add(d.scale(1.1 * x.grow(6))), rightOf(d).scale(.42), UP.scale(.5), front ? MD_W : MD, (front ? .85 : .5) * x.e()); } }, "zero_gap_armor");
        reg(16, (c, x) -> { reticle(c, x.end(), 1.1, (x.held() ? x.hold() : x.t()) * .15, MD, x.e()); if (x.held()) return;
            beam(c, x.hand(), x.end(), .09, MD, x.e()); orb(c, x.end(), .9, x.ph(10), MD_W, x.e()); }, "point_meltdown");
        reg(30, (c, x) -> { var nodes = new ArrayList<Vec3>(); Vec3 a = x.hand(); nodes.add(a); for (int i = 1; i <= 3; i++) nodes.add(a.add(rot(x.f(), x.r(), (i % 2 == 0 ? 1 : -1) * .4).scale(i * 3.5)).add(0, .3 * i, 0));
            for (int i = 1; i < nodes.size(); i++) { sprite(c, "md_hex_plate", nodes.get(i), .4, x.t() * .1, 0, MD_W, x.e(), true); if (!x.held() && x.t() > i * 2) beam(c, nodes.get(i - 1), nodes.get(i), .07, MD, x.e()); } }, "remote_scatter_relay");
        reg(24, (c, x) -> { Vec3 at = x.end(); hex(c, at, x.r().scale(.35), UP.scale(.35), MD_W, x.e()); arrows(c, at, at.add(rot(x.f(), x.r(), .9).scale(2)), .25, MD, .7 * x.e()); }, "scatter_locator_plate");
        reg(18, (c, x) -> { for (int s = -1; s <= 1; s += 2) glow(c, "md_beam_halo", x.feet().add(x.r().scale(s * .3)).add(x.f().scale(.5)).add(0, .3, 0), x.f().scale(.7), UP.scale(.25), 0, MD, x.e() * (1 - x.ph(16)));
            ring(c, x.feet(), flatX(1.3), flatZ(1.3), x.ph(10), MD_W, x.e()); }, "reverse_jet_brake");

        // ============================================================ Teleporter: rifts, reticles, afterimages, coordinate lattices
        reg(14, (c, x) -> { if (x.held()) { reticle(c, x.end(), .8, x.hold() * .1, TP, .7 * x.e()); return; } rift(c, x.hand(), .6, x.ph(8), x.e()); rift(c, x.end(), 1 * x.k(), x.ph(10), x.e()); flash(c, x.end(), 1.2, x.ph(6), TP, x.e()); }, "threatening_teleport");
        reg(18, (c, x) -> { if (x.held()) return; ghost(c, x.feet().subtract(x.dir().scale(Math.min(4, x.dist()))), x.r(), .6 * x.e());
            var p = new ArrayList<Vec3>(); for (int i = 0; i <= 8; i++) p.add(x.feet().add(0, 1, 0).subtract(x.dir().scale(Math.min(4, x.dist()) * (1 - i / 8.0)))); ribbon(c, "tp_lattice", p, .3, TP, x.e(), 0);
            rift(c, x.feet().add(0, 1, 0), 1.8, x.ph(12), x.e()); }, "penetrate_teleport", "phase_corridor");
        reg(18, (c, x) -> { if (x.held()) { reticle(c, x.end(), 1 + .4 * x.charge(20), x.hold() * .08, TP, x.e()); column(c, gnd(x.end(), x.ground().y), 2, .25, TP, .5 * x.e()); return; }
            rift(c, x.feet().add(0, 1, 0), 1.6, x.ph(12), x.e()); ghost(c, x.feet().subtract(x.f().scale(3)), x.r(), .5 * x.e() * (1 - x.ph(18))); }, "mark_teleport");
        reg(16, (c, x) -> { if (x.held()) { reticle(c, x.end(), .9, -x.hold() * .2, TP, x.e()); return; }
            for (int i = 0; i < 3; i++) { double a = i * 1.05 + .3; Vec3 d = rot(UP, x.r(), a); c.cellStrip("fx4:tp_rift_sheet", CanonFx4.Mode.ALPHA, x.end().subtract(d.scale(.7 * x.grow(4))), x.end().add(d.scale(.7 * x.grow(4))), .12, 4 + i, BLOOD, (float) x.e()); }
            flash(c, x.end(), 1.4, x.ph(6), TP, x.e()); }, "flesh_ripping");
        reg(30, (c, x) -> { Vec3 f0 = gnd(x.feet(), x.feet().y); column(c, f0, 3 * x.grow(8), .7, TP, .7 * x.e()); c.ground("ring2", true, f0, 1.4, TP, (float) (.6 * x.e()));
            for (int i = 0; i < 6; i++) { double a = i * 1.047 + x.t() * .05; sprite(c, "tp_reticle", f0.add(Math.cos(a) * 1.3, .3 + i * .4, Math.sin(a) * 1.3), .25, a, 0, TP_W, x.e(), true); } }, "location_teleport");
        reg(18, (c, x) -> { if (x.held()) { reticle(c, x.end(), .9, x.hold() * .1, TP, .8 * x.e()); return; }
            strip(c, "tp_lattice", x.hand(), x.hand().add(x.dir().scale(x.dist() * x.ph(4))), .35, 0, TP, x.e());
            Vec3 b = x.end(); for (int i = 0; i < 4; i++) { Vec3 a1 = b.add(rot(x.r(), UP, i * 1.571).scale(.5)), a2 = b.add(rot(x.r(), UP, (i + 1) * 1.571).scale(.5)); strip(c, "tp_lattice", a1, a2, .08, 0, TP_W, x.e()); } }, "shift_tp");
        reg(30, (c, x) -> { for (int i = 0; i < 4; i++) { double u = clamp01((Math.max(0, x.t()) - i * 3) / 3); if (u <= 0) continue; ghost(c, x.feet().add(x.f().scale(-1.6 * (4 - i))).add(x.r().scale((i % 2 - .5) * 1.4)), x.r(), .45 * x.e() * (1 - u * .5)); }
            rift(c, x.feet().add(0, 1, 0), 1.2, frac(Math.max(0, x.t()) / 6), .8 * x.e()); }, "flashing");
        reg(40, (c, x) -> { Vec3 g = gnd(x.end(), x.ground().y); var p = arc(x.feet().add(0, .3, 0), g, 2, 14); for (int i = 0; i < p.size() - 1; i += 2) strip(c, "tp_lattice", p.get(i), p.get(i + 1), .08, 0, TP, .7 * x.e());
            c.ground("ring", true, g, .9, TP_W, (float) (.7 * x.e())); reticle(c, g.add(0, .5, 0), .7, (x.held() ? x.hold() : x.t()) * .05, TP, x.e()); }, "safe_landing_probe");
        reg(16, (c, x) -> { Vec3 from = x.feet().add(x.r().scale(-4)); ghost(c, from, x.r(), .55 * x.e() * (1 - x.ph(16))); rift(c, from.add(0, 1, 0), 1.3, x.ph(10), x.e()); rift(c, x.feet().add(0, 1, 0), 1.3, x.ph(10) - .1, x.e()); }, "phase_sidestep");
        reg(18, (c, x) -> { if (x.held()) return; Vec3 side = x.hand().add(x.r().scale(1.5)); rift(c, x.hand(), .5, x.ph(6), x.e()); rift(c, side, .6, x.ph(8), x.e());
            strip(c, "common_speedline", side, side.add(x.end().subtract(side).scale(x.ph(5))), .08, 0, TP_W, x.e()); reticle(c, x.end(), .7, 0, TP, x.e()); }, "displaced_projection");
        reg(30, (c, x) -> { Vec3 g = gnd(x.skill().equals("recovery_mark") ? x.end() : x.feet(), x.skill().equals("recovery_mark") ? x.ground().y : x.feet().y);
            plane(c, "tp_fold_decal", g, flatX(.9 * x.grow(6)), flatZ(.9 * x.grow(6)), 0, TP_W, .8 * x.e()); column(c, g, 2.5, .25, x.skill().equals("emergency_return") ? BLOOD : TP, .6 * x.e()); },
            "return_mark", "recovery_mark", "emergency_return");
        reg(24, (c, x) -> { Vec3 a = gnd(x.feet().add(x.r().scale(-3)).add(x.f().scale(3)), x.feet().y), b = gnd(x.feet().add(x.r().scale(3)).add(x.f().scale(3)), x.feet().y);
            for (Vec3 p : new Vec3[]{a, b}) { c.ground("ring", true, p, .8, TP, (float) (.7 * x.e())); column(c, p, 1.6, .2, TP, .6 * x.e()); }
            if (!x.held()) for (Vec3 p : new Vec3[]{a, b}) ribbon(c, "tp_lattice", arc(p.add(0, 1, 0), x.end(), 1.2, 8), .15, TP_W, x.e(), x.t() * .3); }, "cross_substitution");
        reg(20, (c, x) -> { Vec3 a = x.feet().add(0, 1, 0), b = x.end().add(0, .5, 0); reticle(c, a, .9, x.t() * .1, TP, x.e()); reticle(c, b, .9, -x.t() * .1, TP, x.e());
            if (!x.held()) { ribbon(c, "tp_lattice", arc(a, b, 1.4, 10), .14, TP_W, x.e(), x.t() * .3); ribbon(c, "tp_lattice", arc(b, a, -1, 10), .14, TP_W, x.e(), x.t() * .3); } }, "corner_exchange");
        reg(20, (c, x) -> { reticle(c, x.end(), 1.1, (x.held() ? x.hold() : x.t()) * .2, BLOOD, x.e()); if (x.held()) return;
            for (int i = 0; i < 3; i++) { Vec3 s = x.hand().add(x.r().scale((i - 1) * 2.5)).add(x.f().scale(3)).add(0, 1, 0); ribbon(c, "common_speedline", arc(s, x.end(), .8, 8), .1, TP_W, x.e(), -x.t() * .3); rift(c, s, .5, x.ph(8), x.e()); } }, "short_interception");
        reg(40, (c, x) -> { var pts = List.of(gnd(x.feet().add(x.f().scale(2)), x.feet().y), gnd(x.feet().add(x.f().scale(5)).add(x.r().scale(3)), x.feet().y), gnd(x.end(), x.ground().y));
            for (int i = 0; i < pts.size(); i++) { c.ground("ring", true, pts.get(i), .9, TP, (float) (.7 * x.e())); column(c, pts.get(i), 1.2, .2, TP_W, .6 * x.e()); if (i > 0) ribbon(c, "tp_lattice", arc(pts.get(i - 1).add(0, .5, 0), pts.get(i).add(0, .5, 0), 1, 8), .12, TP, x.e(), x.t() * .2); } }, "spatial_relay");
        reg(40, (c, x) -> { Vec3 f0 = gnd(x.feet(), x.feet().y); for (int i = 0; i < 6; i++) { double a = i * 1.047; Vec3 p = f0.add(Math.cos(a) * 4, 0, Math.sin(a) * 4);
                c.ground("ring", true, p, .6, TP, (float) (.7 * x.e())); reticle(c, p.add(0, .8, 0), .5, x.t() * .05 + i, TP_W, x.e()); } }, "tactical_coordinate_formation");
        reg(30, (c, x) -> { Vec3 tgt = x.end(); for (int i = 0; i < 3; i++) { double a = i * 2.09 + .5; Vec3 p = tgt.add(Math.cos(a) * 5, 1, Math.sin(a) * 5);
                reticle(c, p, .6, x.t() * .1, TP, x.e()); if (!x.held()) strip(c, "common_speedline", p, p.add(tgt.subtract(p).scale(x.ph(8))), .1, 0, TP_W, x.e()); }
            reticle(c, tgt, 1.2, -x.t() * .1, BLOOD, x.e()); }, "spatial_pincer_corridor");
        reg(20, (c, x) -> { Vec3 w = x.feet().add(0, 1.1, 0).add(x.f().scale(1.3)); double g = x.held() ? x.charge(8) : 1;
            c.cellPlane("fx4:tp_rift_sheet", CanonFx4.Mode.ALPHA, w, x.r().scale(.7 * g), UP.scale(.9 * g), Fx4Atlas.get("tp_rift_sheet").frame(frac((x.held() ? x.hold() : x.t()) / 16)), TP_W, (float) (.8 * x.e())); }, "incident_bypass");

        // ============================================================ Vector Manipulation: compressed pressure, arrow fields, shear
        reg(28, (c, x) -> { Vec3 fist = x.hand().add(x.f().scale(.35)), d = x.dir(), sd = rightOf(d), up = upOf(d, sd);
            if (x.held()) { double ch = x.charge(7); pressure(c, fist, sd.scale(1 - .5 * ch), up.scale(1 - .5 * ch), 1 - frac(x.hold() / 10), VM, .9 * x.e());
                for (int i = 0; i < 6; i++) { double a = i * 1.047 + x.hold() * .3; Vec3 o = fist.add(rot(sd, up, a).scale(1.2 - .8 * frac(x.hold() / 6 + i / 6.0))); arrows(c, o, fist, .2, VM_W, .8 * x.e()); }
                shock(c, fist, .7, frac(x.hold() / 8), .25 * ch); return; }
            // the punch: a cone of compressed air 3 m long, rings stacked along it, arrows driving forward, and the air visibly bending
            for (int i = 0; i < 5; i++) { Vec3 at = fist.add(d.scale(.5 + i * .7)); double s = .45 + i * .35;
                ring(c, at, sd.scale(s), up.scale(s), (x.t() - i * .6) / 12, VM_W, x.e()); }
            for (int i = 0; i < 7; i++) { double a = i * .9; Vec3 o = rot(sd, up, a).scale(.25 + .1 * (i % 3)); arrows(c, fist.add(o), fist.add(d.scale(3.4 * x.grow(4))).add(o.scale(2.6)), .34, VM, x.e()); }
            pressure(c, fist.add(d.scale(3)), sd.scale(2 * x.k()), up.scale(2 * x.k()), x.ph(16), VM_W, x.e());
            shock(c, fist.add(d.scale(1.2)), 1.6, x.ph(8), .9); shock(c, fist.add(d.scale(3)), 2.6 * x.k(), (x.t() - 2) / 12, .8); }, "dir_shock");
        reg(34, (c, x) -> { Vec3 f0 = gnd(x.feet(), x.feet().y); if (x.held()) { c.ground("ring2", true, f0, 1.2 + .8 * x.charge(15), VM, (float) (.6 * x.e())); shock(c, f0.add(0, .3, 0), 1.4, frac(x.hold() / 10), .3); return; }
            for (int i = -2; i <= 2; i++) { Vec3 d = rot(x.f(), x.r(), i * .14); shear(c, f0.add(d.scale(.5)), f0.add(d.scale(.5 + 10 * x.grow(10))), .45, VM_W, x.e()); }
            shock(c, f0.add(0, .4, 0), 2.4, x.ph(8), .8);
            for (int i = 0; i < 5; i++) { Vec3 p = f0.add(x.f().scale(2 + i * 2)); pressure(c, p.add(0, .4, 0), x.r().scale(1.5), UP.scale(1), (x.t() - i * 1.5) / 12, VM, x.e()); shock(c, p.add(0, .5, 0), 1.6, (x.t() - i * 1.5) / 10, .6); } }, "ground_shock");
        reg(16, (c, x) -> { Vec3 core = x.feet().add(0, 1, 0); if (x.held()) { double ch = x.charge(20); for (int i = 0; i < 6; i++) { double a = i * 1.047 + x.hold() * .2;
                    Vec3 p = core.add(rot(x.r(), UP, a).scale(.8)); arrows(c, p, p.add(x.f().scale(.5 + ch)), .16, VM, .8 * x.e()); } return; }
            for (int i = 0; i < 4; i++) shear(c, core.add(x.r().scale((i - 1.5) * .3)).add(x.f().scale(-.5)), core.add(x.r().scale((i - 1.5) * .3)).add(x.f().scale(-4.5)), .18, VM_W, x.e());
            ring(c, core.add(x.f().scale(-.4)), x.r().scale(1.1), UP.scale(1.1), x.ph(8), VM_W, x.e()); shock(c, core.add(x.f().scale(-.8)), 1.8, x.ph(10), .7); }, "vec_accel");
        reg(12, (c, x) -> { Vec3 core = x.feet().add(0, 1, 0); double tt = x.held() ? x.hold() : x.t();
            for (int i = 0; i < 6; i++) { double a = i * 1.047 + tt * .05; Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a)); var p = new ArrayList<Vec3>();
                for (int j = 0; j <= 6; j++) { double u = j / 6.0; p.add(core.add(d.scale(2.2 - u * .9)).add(rightOf(d).scale(Math.sin(u * 2.2) * .6)).add(0, -u * .7, 0)); } ribbon(c, "vm_arrow_field", p, .22, VM, .6 * x.e(), tt * .1); } },
            "vec_deviation");
        reg(30, (c, x) -> { Vec3 at = x.end(); if (x.held()) { reticle(c, at, 1.1, 0, VM, .7 * x.e()); pressure(c, x.hand(), x.r().scale(.7), UP.scale(.7), frac(x.hold() / 8), VM, .8 * x.e()); shock(c, x.hand(), .6, frac(x.hold() / 6), .3); return; }
            pressure(c, at, x.r().scale(2.6 * x.k()), UP.scale(2.6 * x.k()), x.ph(18), VM_W, x.e()); for (int i = 0; i < 4; i++) ring(c, at, x.r().scale(1.2 + i * .7), UP.scale(1.2 + i * .7), (x.t() - i) / 12, VM, x.e());
            strip(c, "vm_arrow_field", x.hand(), at, .7, 0, VM_W, x.e() * (1 - x.ph(10))); shock(c, at, 3.2 * x.k(), x.ph(12), 1); shock(c, x.hand().add(x.dir().scale(1)), 1.2, x.ph(6), .7); }, "dir_blast");
        reg(18, (c, x) -> { Vec3 at = x.end(); if (x.held()) { for (int i = 0; i < 4; i++) { double a = i * 1.57 - x.hold() * .25; Vec3 o = at.add(rot(x.r(), UP, a).scale(1.2)); arrows(c, o, o.add(rot(x.r(), UP, a + 1.57).scale(.6)), .14, BLOOD, .8 * x.e()); } return; }
            for (int i = 0; i < 6; i++) { double a = i * 1.047; Vec3 o = at.add(rot(x.r(), UP, a).scale(.2 + 1.4 * x.ph(6))); arrows(c, at, o, .16, BLOOD, x.e()); } flash(c, at, 1.6, x.ph(8), BLOOD, x.e()); shock(c, at, 1.4, x.ph(8), .6); }, "blood_retro");
        reg(12, (c, x) -> { Vec3 core = x.feet().add(0, 1, 0); double tt = x.held() ? x.hold() : x.t();
            for (int i = 0; i < 10; i++) { double a = i * .628 + tt * .04, b = Math.sin(i * 1.7) * .7; Vec3 d = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b));
                arrows(c, core.add(d.scale(1.4)), core.add(d.scale(2.1)), .16, VM_W, .7 * x.e()); }
            c.filmShell("fx4:common_noise_tile", CanonFx4.Mode.ADD, core, 1.4, 1.5, 8, 12, VM, (float) (.15 * x.e()), 0); }, "vec_reflection");
        reg(14, (c, x) -> { if (x.held()) return; Vec3 a = x.hand(), b = a.add(x.dir().scale(8)); arrows(c, a, a.add(x.dir().scale(8 * x.grow(3))), .3, VM_W, x.e());
            for (int i = 1; i <= 2; i++) { Vec3 p = a.add(x.dir().scale(i * 3)); ring(c, p, rightOf(x.dir()).scale(.7), upOf(x.dir(), rightOf(x.dir())).scale(.7), (x.t() - i) / 7, VM, x.e()); shock(c, p, 1.1, (x.t() - i) / 8, .6); } }, "deflection_strike");
        reg(12, (c, x) -> { Vec3 core = x.feet().add(0, 1.1, 0); for (int i = 0; i < 7; i++) { Vec3 d = rot(x.f(), x.r(), -1.05 + i * .35); arrows(c, core.add(d.scale(1.1)), core.add(d.scale(1.9)), .2, VM, .8 * x.e()); }
            fan(c, core, x.f(), x.r(), 1.5, 2.1, 12, "vm_shear_streak", VM_W, .5 * x.e()); }, "vector_parry");
        reg(16, (c, x) -> { Vec3 at = x.end(); for (int i = 0; i < 6; i++) { double a = i * 1.047; Vec3 o = at.add(rot(x.r(), UP, a).scale(1 - .5 * x.ph(8))); arrows(c, o, o.add(at.subtract(o).scale(.5)), .12, VM_W, x.e()); }
            ring(c, at, x.r().scale(.6), UP.scale(.6), x.ph(10), VM, x.e()); }, "braking_touch");
        reg(30, (c, x) -> { Vec3 f0 = gnd(x.feet(), x.feet().y); c.ground("ring2", true, f0, 1.3, VM, (float) (.6 * x.e()));
            for (int i = 0; i < 4; i++) { double a = i * 1.57 + .78; Vec3 p = f0.add(Math.cos(a) * .9, 1.2, Math.sin(a) * .9); arrows(c, p, p.add(0, -1.1, 0), .14, VM_W, .8 * x.e()); } }, "inertial_anchor");
        reg(14, (c, x) -> { if (x.held()) return; Vec3 a = x.feet().add(0, 1, 0).add(x.f().scale(1)); for (int i = 0; i < 3; i++) { Vec3 p = a.add(x.f().scale(i * 2)); arrows(c, p, p.add(x.r().scale(2.2 * x.grow(5))), .3, VM_W, x.e()); }
            shear(c, a, a.add(x.f().scale(6)), .5, VM, .5 * x.e()); shock(c, a.add(x.f().scale(2)).add(x.r().scale(1)), 1.8, x.ph(9), .7); }, "yaw_palm");
        reg(30, (c, x) -> { Vec3 core = x.feet().add(0, 1, 0); for (int s = -1; s <= 1; s += 2) { var p = new ArrayList<Vec3>();
                for (int j = 0; j <= 10; j++) { double a = s * (.5 + j * .1) * Math.PI / 2 + (x.held() ? x.hold() : x.t()) * .08 * s; p.add(core.add(rot(x.f(), x.r(), a).scale(2))); } ribbon(c, "vm_arrow_field", p, .28, VM, .7 * x.e(), x.t() * .2 * s); } }, "circulation_relief");
        reg(40, (c, x) -> { Vec3 f0 = gnd(x.feet(), x.feet().y); double u = frac(Math.max(0, x.t()) / 30); c.ground("ring", true, f0, 20 * u, VM, (float) (.4 * x.e() * (1 - u)));
            for (int i = 0; i < 4; i++) { double a = i * 1.57 + .9; Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a)); arrows(c, x.feet().add(0, 1.8, 0).add(d.scale(.9)), x.feet().add(0, 1.8, 0).add(d.scale(1.3)), .12, VM_W, .6 * x.e()); } }, "circulation_listening");
        reg(30, (c, x) -> { Vec3 core = x.feet().add(0, 1, 0); fan(c, core, x.f(), x.r(), 2.4, 2.8, 16, "vm_shear_streak", VM_W, .55 * x.e());
            for (int i = 0; i < 9; i++) { Vec3 d = rot(x.f(), x.r(), -1.4 + i * .35); arrows(c, core.add(d.scale(2.1)), core.add(d.scale(2.7)), .16, VM, .7 * x.e()); } }, "autonomous_vector_circle");
        reg(30, (c, x) -> { Vec3 at = x.feet().add(0, 1.2, 0).add(x.f().scale(2)); for (int s = -1; s <= 1; s += 2) arrows(c, at, at.add(rot(x.f(), x.r(), s * .7).scale(2.4)), .25, VM_W, x.e());
            pressure(c, at, x.r().scale(.6), UP.scale(.6), frac(Math.max(0, x.t()) / 12), VM, .8 * x.e()); shock(c, at, 1, frac(Math.max(0, x.t()) / 14), .4); }, "impulse_split");
    }
}

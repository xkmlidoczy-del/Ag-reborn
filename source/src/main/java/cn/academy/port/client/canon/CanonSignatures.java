package cn.academy.port.client.canon;

import static cn.academy.port.client.canon.CanonFxScripts.*;

import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.client.canon.CanonFx.View;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.world.phys.Vec3;

/**
 * Per-skill signature details on top of the role art and the action-shape layer. Role art and shapes are shared by skills that do
 * similar things; this layer adds the one visual idea each skill's own description names (Spear Fan throws three spears, Feather
 * Rain gathers feathers overhead before they fall, Probe opens a scan frame over the target...), so no two skills of a category
 * read the same. Hydro Hand keeps its own art. Presentation only; cheap (a handful of quads per skill).
 */
final class CanonSignatures {
    /** Everything a signature needs about one visual, resolved once per frame. */
    record X(View v, double e, double t, int tier, double k, Vec3 feet, Vec3 eye, Vec3 end, Vec3 f, Vec3 r, boolean onTarget, double since, double life) {
        Vec3 at() { return onTarget ? (v.category().equals("float_dial")?end.add(0,1,0):end) : eye.add(f.scale(2)); }
        double grow(double ticks) { return easeOutBack(clamp01(t / ticks)); }
        double ph(double ticks) { return clamp01(t / ticks); }
    }
    private static final Map<String, BiConsumer<Canvas, X>> SIG = new HashMap<>();
    static final Vec3 UP = new Vec3(0, 1, 0);
    private static final float[] PEARL = {.97f, .97f, 1f}, HALO = {.8f, .9f, 1f}, ICE = {.68f, .88f, 1f}, FROST = {.92f, .98f, 1f},
            AIR = {.6f, 1f, .88f}, MINT = {.9f, 1f, .97f}, MIND = {1f, .5f, .82f}, VIOLET = {.78f, .62f, 1f},
            FLOAT = {.62f, 1f, .72f}, FOAM = {.88f, 1f, .95f}, PAPER = {1f, .9f, .7f}, GOLD = {1f, .78f, .45f},
            INK = {.16f, .16f, .22f}, SHADE = {.55f, .58f, .75f}, RED = {1f, .45f, .4f};
    private CanonSignatures() {}
    static float[] colour(String category){return switch(category){case "dark_matter"->PEARL;case "nitrogen_armor"->ICE;case "aero_hand"->AIR;default->HALO;};}

    static void draw(Canvas c, View v) {
        var sig = SIG.get(v.skill()); if (sig == null || v.phase() == 3 || CanonReceiptFx.replacesLegacy(v.skill())) return;
        double e = env(v); if (e <= .01) return;
        int tier = Math.max(1, Math.min(5, v.tier())); double life = Math.max(4, v.life()), arrive = Math.min(life * .4, 4);
        Vec3 feet = v.anchor(), end = v.endpoint(); boolean onTarget = CanonShapes.onTarget(v);
        Vec3 f = flat(v.aimAxis()!=null?v.aimAxis():onTarget ? end.subtract(feet) : Vec3.directionFromRotation(0, v.yaw()));
        sig.accept(c, new X(v, e, v.age(), tier, .75 + .17 * tier, feet, feet.add(0, 1.4, 0), end, f, rightOf(f), onTarget, v.age() - arrive, life));
    }

    // ------------------------------------------------------------------ shared helpers
    static Vec3 rot(Vec3 f, Vec3 r, double a) { return f.scale(Math.cos(a)).add(r.scale(Math.sin(a))); }
    static void plane(Canvas c, String key, Vec3 at, Vec3 ax, Vec3 ay, int cell, float[] rgb, double a) { c.cellPlane("fx4:" + key, CanonFx4.Mode.ALPHA, at, ax, ay, cell, rgb, (float) a); }
    /** Daylight body: the same stroke in a deeper, saturated tint drawn with real alpha under the additive highlight, so it reads on bright sky and grass. */
    static float[] deep(float[] c) { return new float[]{c[0] * c[0] * .75f, c[1] * c[1] * .75f, c[2] * c[2] * .75f}; }
    static void glow(Canvas c, String key, Vec3 at, Vec3 ax, Vec3 ay, int cell, float[] rgb, double a) {
        c.cellPlane("fx4:" + key, CanonFx4.Mode.ALPHA, at, ax.scale(1.06), ay.scale(1.06), cell, deep(rgb), (float) (a * .55));
        c.cellPlane("fx4:" + key, CanonFx4.Mode.ADD, at, ax, ay, cell, rgb, (float) a);
    }
    static void sprite(Canvas c, String key, Vec3 at, double size, double spin, int cell, float[] rgb, double a, boolean add) {
        if (add) c.cellSprite("fx4:" + key, CanonFx4.Mode.ALPHA, at, size * 1.06, spin, cell, deep(rgb), (float) (a * .55));
        c.cellSprite("fx4:" + key, add ? CanonFx4.Mode.ADD : CanonFx4.Mode.ALPHA, at, size, spin, cell, rgb, (float) a);
    }
    static void streak(Canvas c, Vec3 a, Vec3 b, double w, float[] rgb, double al) { strip(c, "common_speedline", a, b, w, 0, rgb, al); }
    static void strip(Canvas c, String key, Vec3 a, Vec3 b, double w, int cell, float[] rgb, double al) {
        c.cellStrip("fx4:" + key, CanonFx4.Mode.ALPHA, a, b, w * 1.3, cell, deep(rgb), (float) (al * .55));
        c.cellStrip("fx4:" + key, CanonFx4.Mode.ADD, a, b, w, cell, rgb, (float) al);
    }
    static void ring(Canvas c, Vec3 at, Vec3 ax, Vec3 ay, double ph, float[] rgb, double a) {
        if (ph < 0 || ph >= 1) return; var g = Fx4Atlas.get("common_shock_ring_sheet");
        c.cellPlane("fx4:common_shock_ring_sheet", CanonFx4.Mode.ALPHA, at, ax, ay, g.frame(ph), deep(rgb), (float) (a * .55 * (1 - ph)));
        c.cellPlane("fx4:common_shock_ring_sheet", CanonFx4.Mode.ADD, at, ax, ay, g.frame(ph), rgb, (float) (a * (1 - ph)));
    }
    static void flash(Canvas c, Vec3 at, double size, double ph, float[] rgb, double a) {
        if (ph < 0 || ph >= 1) return; var g = Fx4Atlas.get("common_hit_flash_sheet");
        c.cellSprite("fx4:common_hit_flash_sheet", CanonFx4.Mode.ADD, at, size, 0, g.frame(ph), rgb, (float) (a * (1 - ph)));
    }
    static List<Vec3> arc(Vec3 a, Vec3 b, double lift, int n) {
        Vec3 mid = a.add(b).scale(.5).add(0, lift, 0); var out = new ArrayList<Vec3>();
        for (int i = 0; i <= n; i++) { double u = i / (double) n; out.add(a.scale((1 - u) * (1 - u)).add(mid.scale(2 * u * (1 - u))).add(b.scale(u * u))); }
        return out;
    }
    static void ribbon(Canvas c, String key, List<Vec3> pts, double w, float[] rgb, double a, double scroll) {
        c.ribbon4("fx4:" + key, CanonFx4.Mode.ALPHA, pts, x -> w * 1.3 * (.3 + .7 * Math.sin(Math.PI * Math.max(.05, x))), x -> a * .55, deep(rgb), 2, scroll);
        c.ribbon4("fx4:" + key, CanonFx4.Mode.ADD, pts, x -> w * (.3 + .7 * Math.sin(Math.PI * Math.max(.05, x))), x -> a, rgb, 2, scroll);
    }
    static void halo(Canvas c, Vec3 at, double rad, int n, String key, double size, double spin, float[] rgb, double a, boolean add) {
        for (int i = 0; i < n; i++) { double ang = i * Math.PI * 2 / n + spin; sprite(c, key, at.add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad), size, ang, i, rgb, a, add); }
    }
    static void wall(Canvas c, String key, Vec3 centre, Vec3 r, double w, double h, int nx, int ny, float[] rgb, double a, double grow) {
        for (int i = 0; i < nx; i++) for (int j = 0; j < ny; j++) {
            Vec3 at = centre.add(r.scale((i - (nx - 1) / 2.0) * w / nx * grow)).add(0, (j - (ny - 1) / 2.0) * h / ny * grow, 0);
            plane(c, key, at, r.scale(w / nx * .45), UP.scale(h / ny * .45), i * 3 + j, rgb, a);
        }
    }
    // ------------------------------------------------------------------ paper construct outlines: {centre, ax, ay} per sheet
    private static Vec3 base(X x) { return (x.onTarget() ? x.end() : x.feet().add(x.f().scale(2))).add(0, .05, 0); }
    private static List<Vec3[]> slab(X x, int along, int across, double y) {
        var out = new ArrayList<Vec3[]>(); Vec3 o = base(x).add(0, y, 0);
        for (int i = 0; i < along; i++) for (int j = 0; j < across; j++)
            out.add(new Vec3[]{o.add(x.f().scale(i - (along - 1) / 2.0)).add(x.r().scale(j - (across - 1) / 2.0)), x.f().scale(.48), x.r().scale(.48)});
        return out;
    }
    private static List<Vec3[]> stairs(X x, int n, double rise) {
        var out = new ArrayList<Vec3[]>(); Vec3 o = base(x);
        for (int i = 0; i < n; i++) out.add(new Vec3[]{o.add(x.f().scale(i)).add(0, i * rise, 0), x.f().scale(.48), x.r().scale(.48)});
        return out;
    }
    private static List<Vec3[]> upright(X x, int wide, int high, boolean posts) {
        var out = new ArrayList<Vec3[]>(); Vec3 o = base(x);
        for (int i = 0; i < wide; i++) for (int j = 0; j < high; j++)
            out.add(new Vec3[]{o.add(x.r().scale(i - (wide - 1) / 2.0)).add(0, .5 + j, 0), x.r().scale(posts ? .08 : .48), UP.scale(.48)});
        if (posts) out.add(new Vec3[]{o.add(0, 1, 0), x.r().scale(wide / 2.0), UP.scale(.06)});
        return out;
    }
    private static List<Vec3[]> rungs(X x, int n) {
        var out = new ArrayList<Vec3[]>(); Vec3 o = base(x);
        for (int i = 0; i < n; i++) out.add(new Vec3[]{o.add(0, .3 + i * .55, 0), x.r().scale(.45), UP.scale(.06)});
        for (int s = -1; s <= 1; s += 2) out.add(new Vec3[]{o.add(x.r().scale(s * .45)).add(0, n * .275, 0), x.r().scale(.05), UP.scale(n * .28)});
        return out;
    }
    /** Each sheet folds out of the hand, flies on a short arc and snaps flat into its slot, staggered; a gold crease glints on arrival. */
    private static void fold(Canvas c, X x, List<Vec3[]> slots) {
        Vec3 hand = x.eye().add(x.r().scale(.35)).add(0, -.3, 0); int n = slots.size();
        for (int i = 0; i < n; i++) {
            Vec3[] s = slots.get(i); double u = clamp01((x.t() - i * 6.0 / n) / 5), k = easeOut(u);
            if (u <= 0) continue;
            Vec3 mid = hand.add(s[0]).scale(.5).add(0, 1, 0), p = hand.scale((1 - k) * (1 - k)).add(mid.scale(2 * k * (1 - k))).add(s[0].scale(k * k));
            double sc = .35 + .65 * k; Vec3 ax = s[1].scale(sc), ay = s[2].scale(sc);
            if (u < 1) ay = ay.add(UP.scale((1 - k) * .3));
            plane(c, "pp_sheet_atlas", p, ax, ay, i, PAPER, x.e());
            if (u >= 1) { double glint = clamp01(1 - (x.t() - i * 6.0 / n - 5) / 6); if (glint > 0) glow(c, "pp_fiber", p.add(0, .02, 0), ax, ay, 0, GOLD, .7 * glint * x.e()); }
        }
    }
    private static void reg(BiConsumer<Canvas, X> s, String... ids) { for (String id : ids) SIG.put(id, s); }

    static {
        // Individual seams and flow details for defensive forms; their existing solid bodies retain the checked dimensions.
        reg((c, x) -> {
            double radius=x.v().radius();if(radius<=0)return;
            Vec3 core=CanonBarrierGeometry.centre(x.v());
            for(int i=0;i<6;i++){double a=i*Math.PI/3;Vec3 d=rot(x.f(),x.r(),a),at=core.add(d.scale(radius*.75));
                plane(c,"dm_shard_atlas",at,d.cross(UP).scale(.12),UP.scale(.65*x.grow(6)),i,PEARL,.85*x.e());}
        }, "dark_panel_dome");
        reg((c, x) -> {
            Vec3 core=x.eye().add(x.f().scale(1.5));
            for(int i=0;i<3;i++){double a=i*Math.PI*2/3+x.t()*.06;Vec3 d=rot(x.r(),UP,a);
                glow(c,"dm_shard_atlas",core.add(d.scale(.85)),d.scale(.65),x.f().scale(.18),i*3,PEARL,.75*x.e());}
        }, "dark_construct");
        reg((c, x) -> {
            double w=cn.academy.port.canon.CanonExpansionCombatA.barrierHalfWidth(x.v().skill()),d=cn.academy.port.canon.CanonExpansionCombatA.barrierDistance(x.v().skill());
            Vec3 at=CanonBarrierGeometry.centre(x.v());double open=easeOut(x.ph(5));
            glow(c,"na_compress_sheet",at,x.r().scale(w*open),UP.scale(w*open),Fx4Atlas.get("na_compress_sheet").frame(x.ph(14)),FROST,.8*x.e());
            for(int s:new int[]{-1,1})streak(c,at.add(x.r().scale(s*w)).add(0,-w,0),at.add(x.r().scale(s*w)).add(0,w,0),.055,ICE,.8*x.e());
        }, "nitrogen_plate");
        reg((c, x) -> {
            double radius=x.v().radius()>0?x.v().radius():6;
            for(int i=0;i<4;i++){Vec3 d=rot(x.f(),x.r(),i*Math.PI/2);var p=new ArrayList<Vec3>();
                for(int j=0;j<=8;j++){double a=j*Math.PI/16;p.add(x.feet().add(d.scale(radius*Math.cos(a))).add(0,radius*.65*Math.sin(a),0));}
                ribbon(c,"na_vapor_sheet",p,.26,ICE,.6*x.e(),-x.t()*.08);}
        }, "nitrogen_dome");
        reg((c, x) -> {
            double w=cn.academy.port.canon.CanonExpansionCombatA.barrierHalfWidth(x.v().skill()),d=cn.academy.port.canon.CanonExpansionCombatA.barrierDistance(x.v().skill());
            Vec3 centre=CanonBarrierGeometry.centre(x.v());
            for(int i=0;i<5;i++){double y=(i-2)*w*.42;var p=arc(centre.add(x.r().scale(-w)).add(0,y,0),centre.add(x.r().scale(w)).add(0,y,0),.08,8);
                ribbon(c,"ah_stream",p,.12,AIR,.7*x.e(),-x.t()*.15+i*.2);}
        }, "aero_pressure_wall");
        reg((c, x) -> {
            double radius=x.v().radius();if(radius<=0)return;
            Vec3 centre=CanonBarrierGeometry.centre(x.v()).subtract(0,radius,0);
            for(int i=0;i<6;i++){Vec3 d=rot(x.f(),x.r(),i*Math.PI/3);Vec3 at=centre.add(d.scale(radius));
                var p=arc(at.add(0,.1,0),at.add(0,2.3,0).add(rightOf(d).scale(.45)),.2,6);
                ribbon(c,"ah_stream",p,.34,AIR,.65*x.e(),-x.t()*.14);}
        }, "aero_air_curtain");
        // ============================================================ Dark Matter: white matter, feathers and crystal shards
        reg((c, x) -> { // Dark Blade: a crystal blade sweeps through the target
            double sw = clamp01(x.t() / 5) * 2 - 1; Vec3 at = x.at(), d = rot(x.f(), x.r(), sw * 1.1);
            glow(c, "dm_shard_atlas", at.add(d.scale(.2)), d.scale(1.4 * x.k()), UP.scale(.18), 2, PEARL, x.e());
            flash(c, at, 1.6 * x.k(), x.since() / 6, HALO, x.e()); }, "dark_blade");
        reg((c, x) -> { // Dark Shard: one big spinning shard with a refraction wake
            Vec3 a = x.eye(), b = x.at(); double u = clamp01(x.t() / 4); Vec3 p = a.add(b.subtract(a).scale(u));
            sprite(c, "dm_shard_atlas", p, .7 * x.k(), x.t() * .6, 5, PEARL, x.e(), true);
            c.refract("fx4:dm_refraction_normal", true, p, .9, (float) (.6 * x.e()), HALO); }, "dark_shard");
        reg((c, x) -> { // Feather Guard: one large feather flips up in front to deflect
            double flip = easeOutBack(x.ph(5)); Vec3 at = x.eye().add(x.f().scale(1.1));
            plane(c, "dm_feather_atlas", at, rot(x.r(), x.f(), (1 - flip) * 1.2).scale(.6 * x.k()), UP.scale(1.1 * x.k()), 1, PEARL, x.e());
            ring(c, at, x.r().scale(1.2), UP.scale(1.2), x.ph(10), HALO, x.e()); }, "dark_feather_guard");
        reg((c, x) -> { // Dark Step: feathers left on the ground where you pushed off
            for (int i = 0; i < 4; i++) { Vec3 at = x.feet().add(x.f().scale(-.6 - i * .5)).add(x.r().scale((i % 2 - .5) * .5)).add(0, .05, 0);
                plane(c, "dm_feather_atlas", at, x.f().scale(.35), x.r().scale(.12), i, PEARL, x.e() * (1 - i * .18)); } }, "dark_step");
        reg((c, x) -> { // Dark Spread: five feathers fan out
            double g = x.grow(6); for (int i = 0; i < 5; i++) { Vec3 d = rot(x.f(), x.r(), (i - 2) * .3);
                plane(c, "dm_feather_atlas", x.eye().add(d.scale(1 + 3 * g)), d.scale(.6), UP.scale(.18).add(x.r().scale(.1)), i, PEARL, x.e()); } }, "dark_spread");
        reg((c, x) -> { // Dark Sweep: a crescent of feathers swept round at arm's length
            double sw = clamp01(x.t() / 6); for (int i = 0; i < 9; i++) { double a = -1.2 + 2.4 * i / 8 * sw; Vec3 d = rot(x.f(), x.r(), a);
                plane(c, "dm_feather_atlas", x.eye().add(d.scale(3.2)), d.cross(UP).normalize().scale(.5), UP.scale(.15), i, PEARL, x.e() * (.4 + .6 * i / 8.0)); } }, "dark_sweep");
        reg((c, x) -> halo(c, x.eye(), 1.3, 6, "dm_shard_atlas", .35, x.t() * .08, PEARL, x.e(), true), "dark_guard");
        reg((c, x) -> { // Dark Lift: a feather burst below pushes you up
            ring(c, x.feet().add(0, .1, 0), new Vec3(1.6, 0, 0), new Vec3(0, 0, 1.6), x.ph(10), PEARL, x.e());
            for (int i = 0; i < 6; i++) { double a = i * 1.047; plane(c, "dm_feather_atlas", x.feet().add(Math.cos(a) * .8, .2 - x.ph(12) * .8, Math.sin(a) * .8), new Vec3(Math.cos(a), -.6, Math.sin(a)).scale(.35), UP.scale(.1), i, PEARL, x.e()); } }, "dark_lift");
        reg((c, x) -> { // Shard Return: a shard curves back to your hand
            var p = arc(x.at(), x.eye().add(x.r().scale(.4)), 1.5, 10); ribbon(c, "common_speedline", p, .15, HALO, x.e(), x.t() * .1);
            sprite(c, "dm_shard_atlas", p.get((int) Math.min(10, x.ph(8) * 10)), .45, x.t() * .5, 3, PEARL, x.e(), true); }, "dark_shard_return");
        reg((c, x) -> { // Dark Wings: a faint halo behind the head
            glow(c, "dm_shard_atlas", x.eye().add(0, .7, 0).add(x.f().scale(-.35)), x.r().scale(.5 * x.grow(8)), UP.scale(.5 * x.grow(8)), 12, HALO, .6 * x.e()); }, "dark_wings");
        reg((c, x) -> { // Dark Pierce: a lance of light with ripples along the line
            Vec3 a = x.eye(), b = x.at(), d = b.subtract(a); double len = d.length(); Vec3 n = d.normalize();
            streak(c, a, a.add(n.scale(len * clamp01(x.t() / 3))), .35 * x.k(), PEARL, x.e());
            for (int i = 1; i <= 4; i++) ring(c, a.add(n.scale(len * i / 5)), rightOf(n).scale(.8), upOf(n, rightOf(n)).scale(.8), (x.t() - i) / 8, HALO, x.e()); }, "dark_pierce");
        reg((c, x) -> halo(c, x.at().add(0, -.4, 0), .8, 4, "dm_shard_atlas", .3, x.t() * .2, PEARL, x.e(), true), "dark_shard_orbit");
        reg((c, x) -> wall(c, "dm_shard_atlas", CanonBarrierGeometry.centre(x.v()), x.r(), 1.4, 1.4, 4, 3, PEARL, .85 * x.e(), easeOut(x.ph(6))), "dark_shard_screen");
        reg((c, x) -> { // Wing Brake: wings flare forward like an air brake
            for (int s = -1; s <= 1; s += 2) plane(c, "dm_feather_atlas", x.eye().add(x.r().scale(s * .9)).add(x.f().scale(.5)), x.r().scale(s * .9).add(x.f().scale(.6)), UP.scale(.5), s + 2, PEARL, x.e()); }, "dark_wing_brake");
        reg((c, x) -> { // Feather Rain: feathers gather in a ring overhead before they fall
            Vec3 top = (x.onTarget() ? x.end() : x.feet().add(x.f().scale(4))).add(0, 5.5, 0); double g = x.grow(6);
            for (int i = 0; i < 10; i++) { double a = i * .628 + x.t() * .05; plane(c, "dm_feather_atlas", top.add(Math.cos(a) * 1.6 * g, 0, Math.sin(a) * 1.6 * g), new Vec3(Math.cos(a), 0, Math.sin(a)).scale(.45), UP.scale(.12), i, PEARL, x.e()); }
            c.refract("fx4:dm_refraction_normal", true, top, 2.2 * g, (float) (.4 * x.e()), HALO); }, "dark_rain");
        reg((c, x) -> { // Wing Charge: a comet tail of feathers behind you
            for (int i = 0; i < 7; i++) plane(c, "dm_feather_atlas", x.eye().add(x.f().scale(-.6 - i * .45)).add(0, Math.sin(x.t() * .3 + i) * .2, 0), x.f().scale(-.4), x.r().scale(.12), i, PEARL, x.e() * (1 - i * .12)); }, "dark_wing_charge");
        reg((c, x) -> { // Shard Salvo: shards curve in on homing arcs
            for (int i = 0; i < 3; i++) { var p = arc(x.eye().add(x.r().scale((i - 1) * .9)), x.at(), 1.2 + i * .5, 10);
                ribbon(c, "common_speedline", p, .12, HALO, .8 * x.e(), -x.t() * .2); sprite(c, "dm_shard_atlas", p.get((int) Math.min(10, x.ph(6) * 10)), .35, x.t(), i, PEARL, x.e(), true); } }, "dark_shard_salvo");
        reg((c, x) -> { // Wing Rescue: a canopy of feathers above slows the fall
            Vec3 top = x.eye().add(0, 1.2, 0); for (int i = 0; i < 8; i++) { double a = i * .785; plane(c, "dm_feather_atlas", top.add(Math.cos(a) * .9, -.15, Math.sin(a) * .9), new Vec3(Math.cos(a), -.25, Math.sin(a)).scale(.5), new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(.14), i, PEARL, x.e()); } }, "dark_wing_rescue");
        reg((c, x) -> { // Spear Fan: three long crystal spears along the fan
            double u = clamp01(x.t() / 4); for (int i = -1; i <= 1; i++) { Vec3 d = rot(x.f(), x.r(), i * .25), tip = x.eye().add(d.scale(1 + 9 * u));
                glow(c, "dm_shard_atlas", tip, d.scale(1.6 * x.k()), UP.scale(.12), 6, PEARL, x.e()); streak(c, tip.subtract(d.scale(3)), tip, .12, HALO, .7 * x.e()); } }, "dark_spear_fan");
        reg((c, x) -> { // Wing Shelter: a wing wraps round the protected ally
            Vec3 at = x.at(); for (int i = 0; i < 7; i++) { double a = -1.4 + i * .47; Vec3 d = rot(x.f().scale(-1), x.r(), a);
                plane(c, "dm_feather_atlas", at.add(d.scale(1.1)), UP.scale(.7), d.cross(UP).normalize().scale(.2), i, PEARL, x.e()); } }, "dark_wing_shelter");
        reg((c, x) -> { // Shard Ring: an orbiting formation fires outward
            double rad = 1.4 + 6 * clamp01(x.since() / 8); halo(c, x.eye().add(0, -.2, 0), rad, 10, "dm_shard_atlas", .35, x.t() * .1, PEARL, x.e() * (1 - clamp01(x.since() / 10)), true); }, "dark_shard_ring");
        reg((c, x) -> { // Matter Barrage: a stream of small shards weaving to the target
            Vec3 a = x.eye(), b = x.at(), n = b.subtract(a).normalize(), side = rightOf(n);
            for (int i = 0; i < 8; i++) { double u = frac(x.t() * .08 + i / 8.0); Vec3 p = a.add(b.subtract(a).scale(u)).add(side.scale(Math.sin(u * 9 + i) * .4));
                sprite(c, "dm_shard_atlas", p, .22, u * 8, i, PEARL, x.e(), true); } }, "dark_matter_barrage");
        reg((c, x) -> { // Dark Synthesis: a crystal splits into two copies
            double g = x.ph(10); Vec3 at = x.eye().add(x.f().scale(.8));
            for (int s = -1; s <= 1; s += 2) sprite(c, "dm_shard_atlas", at.add(x.r().scale(s * .3 * g)), .4, s * x.t() * .1, 9, PEARL, x.e(), true);
            flash(c, at, 1.2, (x.t() - 6) / 8, HALO, x.e()); }, "dark_synthesis");

        // ============================================================ Nitrogen Armor: compressed air, frost and pressure
        reg((c, x) -> halo(c, x.feet().add(0, .3, 0), 1.0, 8, "na_frost_atlas", .3, x.t() * .05, FROST, .8 * x.e(), true), "nitrogen_armor");
        reg((c, x) -> { Vec3 at = x.eye().add(x.f().scale(1)); glow(c, "na_frost_atlas", at, x.r().scale(.7), UP.scale(.9), 4, ICE, x.e()); ring(c, at, x.r(), UP, x.ph(8), FROST, x.e()); }, "nitrogen_guard");
        reg((c, x) -> { // Nitrogen Dash: a vapour trail jets out behind
            var g = Fx4Atlas.get("na_vapor_sheet"); for (int i = 0; i < 4; i++) glow(c, "na_vapor_sheet", x.feet().add(0, .8, 0).add(x.f().scale(-.8 - i * .7)), x.r().scale(.5 + i * .2), UP.scale(.5 + i * .2), g.frame(frac(x.t() * .05 + i * .2)), FROST, x.e() * (1 - i * .2)); }, "nitrogen_dash");
        reg((c, x) -> { Vec3 at = x.at(); var g = Fx4Atlas.get("na_compress_sheet"); glow(c, "na_compress_sheet", at, x.r().scale(1.2 * x.k()), UP.scale(1.2 * x.k()), g.frame(x.ph(10)), ICE, x.e()); }, "nitrogen_burst");
        reg((c, x) -> { // Nitrogen Brake: frost skid marks on the ground
            for (int s = -1; s <= 1; s += 2) plane(c, "na_frost_atlas", x.feet().add(x.r().scale(s * .3)).add(x.f().scale(-.8)).add(0, .05, 0), x.f().scale(1.2), x.r().scale(.15), s + 2, FROST, x.e()); }, "nitrogen_brake");
        reg((c, x) -> { // Nitrogen Ram: a frost wedge in front of the shoulder
            Vec3 at = x.eye().add(x.f().scale(1)).add(0, -.3, 0); glow(c, "na_frost_atlas", at, x.f().scale(.9).add(x.r().scale(.5)), UP.scale(.8), 7, ICE, x.e()); glow(c, "na_frost_atlas", at, x.f().scale(.9).add(x.r().scale(-.5)), UP.scale(.8), 8, ICE, x.e()); }, "nitrogen_ram");
        reg((c, x) -> { Vec3 at = x.at(); for (int i = 0; i < 3; i++) streak(c, at.subtract(x.f().scale(1.5)).add(0, (i - 1) * .3, 0), at.add(x.f().scale(.8)).add(0, (i - 1) * .3, 0), .14, FROST, x.e()); }, "nitrogen_shove");
        reg((c, x) -> { var g = Fx4Atlas.get("na_vapor_sheet"); glow(c, "na_vapor_sheet", x.feet().add(0, .1, 0), new Vec3(1.1, 0, 0), new Vec3(0, 0, 1.1), g.frame(frac(x.t() * .04)), FROST, .8 * x.e()); }, "nitrogen_buoy");
        reg((c, x) -> { // Nitrogen Punch: a frost knuckle print bursts at the target
            Vec3 at = x.at(); glow(c, "na_frost_atlas", at, x.r().scale(.8), UP.scale(.8), 0, FROST, x.e()); flash(c, at, 2 * x.k(), x.since() / 6, ICE, x.e()); }, "nitrogen_punch");
        reg((c, x) -> { Vec3 at = x.at(); for (int i = 0; i < 4; i++) streak(c, at.add(x.r().scale((i - 1.5) * .3)), at.add(x.r().scale((i - 1.5) * .3)).add(0, 1.6 + 2 * x.ph(8), 0), .12, FROST, x.e()); }, "nitrogen_uppercut");
        reg((c, x) -> { // Pressure Sweep: frost crescent hugging the ground
            for (int i = 0; i < 7; i++) { double a = -1.2 + i * .4; Vec3 d = rot(x.f(), x.r(), a); glow(c, "na_frost_atlas", x.feet().add(d.scale(3)).add(0, .3, 0), d.cross(UP).normalize().scale(.5), UP.scale(.35), i, ICE, x.e()); } }, "nitrogen_sweep");
        reg((c, x) -> { // Shell Reinforce: hexagonal frost plates lock onto the shell
            for (int i = 0; i < 6; i++) { double a = i * 1.047 + x.t() * .02; Vec3 d = new Vec3(Math.cos(a), .2, Math.sin(a)).normalize();
                glow(c, "na_frost_atlas", x.feet().add(0, 1, 0).add(d.scale(.95 * x.grow(8))), rightOf(d).scale(.35), upOf(d, rightOf(d)).scale(.35), 9, FROST, x.e()); } }, "nitrogen_shell_reinforce");
        reg((c, x) -> { Vec3 at = x.at().add(0, -.5, 0); streak(c, at.subtract(x.f().scale(2)), at, .25, FROST, x.e()); ring(c, at, x.r().scale(.8), UP.scale(.8), x.ph(8), ICE, x.e()); }, "nitrogen_item_push");
        reg((c, x) -> { // Shield Line: a row of frost panels you can see the arrows hit
            wall(c, "na_frost_atlas", CanonBarrierGeometry.centre(x.v()), x.r(), 1.4, 1.4, 4, 2, FROST, .8 * x.e(), easeOut(x.ph(6))); }, "nitrogen_shield_line");
        reg((c, x) -> { c.ground("ring", true, x.feet().add(0, .06, 0), 1.6, FROST, (float) (.6 * x.e())); plane(c, "na_frost_decal", x.feet().add(0, .05, 0), new Vec3(1.4, 0, 0), new Vec3(0, 0, 1.4), 0, FROST, .7 * x.e()); }, "nitrogen_fall_guard");
        reg((c, x) -> { // Pressure Cone: a frost cone opening forward
            glow(c, "na_vapor_sheet", x.eye().add(x.f().scale(3)), x.f().scale(3), x.r().scale(2.2 * x.grow(6)), Fx4Atlas.get("na_vapor_sheet").frame(x.ph(14)), ICE, .7 * x.e()); }, "nitrogen_cone");
        reg((c, x) -> { Vec3 at = x.at(); halo(c, at.add(0, .2, 0), .9, 6, "na_frost_atlas", .3, x.t() * .1, FROST, x.e(), true); }, "nitrogen_ally_guard");
        reg((c, x) -> { // Ground Wave: frost cracks race outward over the ground
            for (int i = 0; i < 8; i++) { double a = i * .785; Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a)); double len = 5 * x.grow(8);
                streak(c, x.feet().add(0, .08, 0), x.feet().add(d.scale(len)).add(0, .08, 0), .18, FROST, x.e()); } }, "nitrogen_ground_wave");
        reg((c, x) -> { // Nitrogen Bulwark: a tall frost tower shield
            double w=cn.academy.port.canon.CanonExpansionCombatA.barrierHalfWidth(x.v().skill());
            glow(c, "na_shell_rim", CanonBarrierGeometry.centre(x.v()), x.r().scale(w), UP.scale(w * easeOut(x.ph(6))), 0, ICE, x.e()); }, "nitrogen_bulwark");
        reg((c, x) -> { // Shock Ring: an airborne pressure halo pushed downward
            ring(c, x.feet().add(0, .2, 0), new Vec3(6 * x.ph(10), 0, 0), new Vec3(0, 0, 6 * x.ph(10)), x.ph(10), FROST, x.e()); halo(c, x.feet().add(0, .4, 0), 1.5, 8, "na_frost_atlas", .3, x.t() * .2, ICE, x.e(), true); }, "nitrogen_shock_ring");
        reg((c, x) -> { // Nitrogen Rocket: a frost exhaust plume below
            var g = Fx4Atlas.get("na_vapor_sheet"); for (int i = 0; i < 3; i++) glow(c, "na_vapor_sheet", x.feet().add(0, -.3 - i * .6, 0), x.r().scale(.4 + i * .3), x.f().scale(.4 + i * .3), g.frame(frac(x.t() * .06 + i * .3)), FROST, x.e() * (1 - i * .25)); }, "nitrogen_rocket");
        reg((c, x) -> { // Full Impact: a frost crater where you land
            plane(c, "na_frost_decal", x.at().add(0, -.9, 0), new Vec3(2.2 * x.grow(6), 0, 0), new Vec3(0, 0, 2.2 * x.grow(6)), 0, FROST, .85 * x.e()); flash(c, x.at(), 2.4, x.since() / 6, ICE, x.e()); }, "nitrogen_impact");

        // ============================================================ Aero Hand: pressure, wind and dust
        reg((c, x) -> { for (int i = 0; i < 3; i++) ring(c, x.feet().add(0, .3, 0).add(x.f().scale(-.5 - i * .6)), x.r().scale(.6 + i * .2), UP.scale(.6 + i * .2), x.ph(8) - i * .15, AIR, x.e()); }, "aero_jet");
        reg((c, x) -> { Vec3 at = x.eye().add(x.f().scale(1.3)); glow(c, "ah_cone", at, x.f().scale(-.8), x.r().scale(1.1), 0, MINT, .8 * x.e()); }, "aero_brake");
        reg((c, x) -> { // Updraft: a rising column of air under your feet
            var g = Fx4Atlas.get("ah_vortex_sheet"); glow(c, "ah_vortex_sheet", x.feet().add(0, .1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), g.frame(frac(x.t() * .05)), AIR, x.e());
            for (int i = 0; i < 4; i++) streak(c, x.feet().add(Math.cos(i * 1.57) * .5, 0, Math.sin(i * 1.57) * .5), x.feet().add(Math.cos(i * 1.57) * .5, 2.5 * x.grow(6), Math.sin(i * 1.57) * .5), .1, MINT, x.e()); }, "aero_updraft");
        reg((c, x) -> { Vec3 at = x.at(); glow(c, "vm_pressure_sheet", at, x.r().scale(.9), UP.scale(.9), Fx4Atlas.get("vm_pressure_sheet").frame(x.ph(10)), AIR, x.e()); }, "aero_push");
        reg((c, x) -> { for (int i = 0; i < 3; i++) streak(c, x.feet().add(0, .4 + i * .5, 0).add(x.r().scale(-.8)), x.feet().add(0, .4 + i * .5, 0).add(x.r().scale(.6)), .1, MINT, x.e()); }, "aero_side_step");
        reg((c, x) -> { ring(c, x.eye().add(x.f().scale(.8)), x.r().scale(.9), UP.scale(.9), x.ph(8), AIR, x.e()); }, "aero_backstep");
        reg((c, x) -> { var g = Fx4Atlas.get("ah_dust_sheet"); plane(c, "ah_dust_sheet", x.feet().add(0, -.4, 0), new Vec3(1.3, 0, 0), new Vec3(0, 0, 1.3), g.frame(frac(x.t() * .05)), AIR, .7 * x.e()); }, "aero_fall_brake");
        reg((c, x) -> { var p = arc(x.at(), x.eye(), .5, 8); ribbon(c, "ah_stream", p, .25, AIR, x.e(), x.t() * .2); }, "aero_item_pull");
        reg((c, x) -> { Vec3 at = x.at(); var g = Fx4Atlas.get("ah_dust_sheet"); plane(c, "ah_dust_sheet", at, x.r().scale(1.1), UP.scale(1.1), g.frame(x.ph(12)), AIR, x.e()); flash(c, at, 1.4, x.since() / 6, MINT, x.e()); }, "aero_gust");
        reg((c, x) -> { Vec3 at = x.at(); for (int i = 0; i < 3; i++) streak(c, at.subtract(x.f().scale(1 + i * .5)).add(0, (i - 1) * .25, 0), at.add(x.f().scale(1.5)), .1, MINT, x.e()); }, "aero_launch");
        reg((c, x) -> glow(c, "ah_cone", x.eye().add(x.f().scale(4)), x.f().scale(4), x.r().scale(3 * x.grow(6)), 0, AIR, .55 * x.e()), "aero_cone");
        reg((c, x) -> { for (int i = 0; i < 2; i++) ring(c, x.feet().add(0, .4 + i * .6, 0), new Vec3(4 * x.ph(8), 0, 0), new Vec3(0, 0, 4 * x.ph(8)), x.ph(8) - i * .1, AIR, x.e()); }, "aero_ring_push");
        reg((c, x) -> { for (int i = 0; i < 3; i++) glow(c, "ah_cone", x.feet().add(0, -.4 - i * .5, 0), UP.scale(-.6), x.r().scale(.5 + i * .2), 0, MINT, x.e() * (1 - i * .25)); }, "aero_vertical_jet");
        reg((c, x) -> { // Arrow Deflect: a sideways gust swatting the arrow
            Vec3 at = x.at(); var p = new ArrayList<Vec3>(); for (int i = 0; i <= 8; i++) { double a = -1 + i / 4.0; p.add(at.add(rot(x.f().scale(-1), x.r(), a).scale(.9))); }
            ribbon(c, "ah_stream", p, .3, MINT, x.e(), x.t() * .3); }, "aero_arrow_deflect");
        reg((c, x) -> { var g = Fx4Atlas.get("ah_vortex_sheet"); glow(c, "ah_vortex_sheet", x.feet().add(0, .2, 0), new Vec3(2.2, 0, 0), new Vec3(0, 0, 2.2), g.frame(frac(x.t() * .05)), AIR, x.e()); }, "aero_vortex");
        reg((c, x) -> { Vec3 at = x.at(); for (int i = 0; i < 4; i++) streak(c, at.add(x.r().scale(-2)).add(0, (i - 1.5) * .3, 0), at.add(x.r().scale(1.5)).add(0, (i - 1.5) * .3, 0), .1, MINT, x.e()); }, "aero_crosswind");
        reg((c, x) -> { for (int i = 0; i < 5; i++) { double a = -1 + i * .5; Vec3 d = rot(x.f(), x.r(), a); var g = Fx4Atlas.get("ah_dust_sheet");
                plane(c, "ah_dust_sheet", x.feet().add(d.scale(2 + 5 * x.ph(10))).add(0, .4, 0), d.cross(UP).normalize().scale(.9), UP.scale(.6), g.frame(frac(x.t() * .04 + i * .2)), AIR, .8 * x.e()); } }, "aero_ground_blast");
        reg((c, x) -> { var p = new ArrayList<Vec3>(); for (int i = 0; i <= 10; i++) { double a = i / 10.0 * 1.6; p.add(x.eye().add(rot(x.f().scale(-1), x.r(), a).scale(1.2))); } ribbon(c, "ah_stream", p, .25, MINT, x.e(), x.t() * .2); }, "aero_airborne_turn");
        reg((c, x) -> { Vec3 at = x.at(); glow(c, "vm_pressure_sheet", at, x.r().scale(1.3), UP.scale(1.3), Fx4Atlas.get("vm_pressure_sheet").frame(x.ph(10)), AIR, x.e()); }, "aero_barrage");
        reg((c, x) -> { var g = Fx4Atlas.get("ah_vortex_sheet"); for (int i = 0; i < 3; i++) glow(c, "ah_vortex_sheet", x.feet().add(0, .3 + i * 1.2, 0), new Vec3(3 - i * .6, 0, 0), new Vec3(0, 0, 3 - i * .6), g.frame(frac(x.t() * .05 + i * .3)), AIR, x.e() * (1 - i * .2)); }, "aero_squall");
        reg((c, x) -> { for (int i = 0; i < 2; i++) glow(c, "ah_cone", x.feet().add(0, -.3, 0).add(x.f().scale(-.3 - i * .4)), x.f().scale(-.5).add(UP.scale(-.4)), x.r().scale(.5), 0, MINT, x.e()); }, "aero_double_jet");
        reg((c, x) -> { // Pressure Lance: compression rings stacked along the line
            Vec3 a = x.eye(), b = x.at(), n = b.subtract(a).normalize(); double len = a.distanceTo(b);
            for (int i = 1; i <= 5; i++) ring(c, a.add(n.scale(len * i / 6)), rightOf(n).scale(.6), upOf(n, rightOf(n)).scale(.6), (x.t() - i * .7) / 6, AIR, x.e()); }, "aero_pressure_lance");

        // ============================================================ Mental Out: holographic scans, threads and status arcs
        reg((c, x) -> { // Probe: a holographic scan frame over the target
            Vec3 at = x.at().add(0, .5, 0); glow(c, "mo_holo_panel", at.add(x.f().scale(-.2)), x.r().scale(.55 * x.grow(6)), UP.scale(.4 * x.grow(6)), 0, MIND, x.e()); }, "mental_probe");
        reg((c, x) -> { for (int i = 0; i < 3; i++) ring(c, x.feet().add(0, .1 + i * .3, 0), new Vec3(16 * frac(x.t() / 30.0 + i / 3.0), 0, 0), new Vec3(0, 0, 16 * frac(x.t() / 30.0 + i / 3.0)), frac(x.t() / 30.0 + i / 3.0), VIOLET, .6 * x.e()); }, "mental_life_scan");
        reg((c, x) -> { Vec3 at = x.at(); glow(c, "mo_status_arc", at.add(0, .9, 0), x.r().scale(.5), UP.scale(.5), 0, MIND, x.e()); streak(c, at.add(0, .9, 0), at.add(x.f().scale(2)).add(0, .9, 0), .08, RED, .8 * x.e()); }, "mental_intent_scan");
        reg((c, x) -> { halo(c, x.eye().add(0, .8, 0), .6, 6, "mo_star_blink_sheet", .2, x.t() * .1, MIND, x.e(), false); ring(c, x.feet().add(0, .1, 0), new Vec3(10 * x.ph(14), 0, 0), new Vec3(0, 0, 10 * x.ph(14)), x.ph(14), RED, .7 * x.e()); }, "mental_hostile_scan");
        reg((c, x) -> { Vec3 at = x.at().add(0, .7, 0); streak(c, at.add(x.r().scale(-.5)).add(0, .5, 0), at.add(x.r().scale(.5)).add(0, -.5, 0), .1, RED, x.e()); streak(c, at.add(x.r().scale(.5)).add(0, .5, 0), at.add(x.r().scale(-.5)).add(0, -.5, 0), .1, RED, x.e()); }, "mental_focus_break");
        reg((c, x) -> { Vec3 at = x.at(); var p = arc(x.eye(), at, 1, 8); ribbon(c, "mo_thread", p.subList(0, 4), .08, MIND, x.e(), 0); ribbon(c, "mo_thread", p.subList(6, 9), .08, MIND, x.e() * (1 - x.ph(10)), 0); flash(c, p.get(5), .6, x.ph(6), MIND, x.e()); }, "mental_disengage");
        reg((c, x) -> { Vec3 at = x.at(); var path = new ArrayList<Vec3>(); for (int i = 0; i <= 6; i++) path.add(at.add(0, -.9, 0).add(x.f().scale(i * .5)).add(x.r().scale(Math.sin(i) * .3)));
            ribbon(c, "mo_thread", path, .08, VIOLET, x.e() * (1 - x.ph(12)), x.t() * .2); }, "mental_route_interrupt");
        reg((c, x) -> { var p = arc(x.at(), x.eye(), 1.5, 10); ribbon(c, "mo_thread", p, .1, MIND, x.e(), -x.t() * .2); glow(c, "mo_status_arc", x.eye().add(0, .6, 0), x.r().scale(.35), UP.scale(.35), 0, RED, x.e()); }, "mental_attention_shift");
        reg((c, x) -> { Vec3 at = x.at(); glow(c, "mo_holo_panel", at.add(0, 1.2, 0), x.r().scale(.4), UP.scale(.25), 0, VIOLET, x.e()); }, "mental_target_sense");
        reg((c, x) -> { Vec3 at = x.at().add(0, .9, 0); for (int i = 0; i < 3; i++) sprite(c, "mo_star_blink_sheet", at.add(Math.cos(x.t() * .3 + i * 2.1) * .5, .2, Math.sin(x.t() * .3 + i * 2.1) * .5), .25, 0, i, MIND, x.e(), false); }, "mental_disrupt");
        reg((c, x) -> { Vec3 at = x.at(); halo(c, at.add(0, .2, 0), .7, 4, "mo_holo_panel", .3, x.t() * .05, VIOLET, .8 * x.e(), true); }, "mental_interpose");
        reg((c, x) -> { Vec3 at = x.at(); var p = arc(at, at.add(x.r().scale(3)), 1, 8); ribbon(c, "mo_thread", p, .1, RED, x.e(), x.t() * .2); }, "mental_redirect");
        reg((c, x) -> ring(c, x.feet().add(0, .1, 0), new Vec3(12 * x.ph(12), 0, 0), new Vec3(0, 0, 12 * x.ph(12)), x.ph(12), MIND, x.e()), "mental_pursuit_break");
        reg((c, x) -> { Vec3 at = x.at().add(0, 1.2, 0); for (int s = -1; s <= 1; s += 2) glow(c, "mo_status_arc", at.add(x.r().scale(s * .7)), x.r().scale(.3), UP.scale(.3), 0, VIOLET, x.e()); }, "mental_suppress_pair");
        reg((c, x) -> { Vec3 at = x.at(), o = at.add(x.r().scale(2.5)); ribbon(c, "mo_thread", arc(at, o, .8, 8), .1, RED, x.e(), x.t() * .2); ribbon(c, "mo_thread", arc(o, at, -.4, 8), .1, RED, x.e(), x.t() * .2); }, "mental_conflict");
        reg((c, x) -> { c.ground("ring2", true, x.feet().add(0, .07, 0), 12 * x.grow(10), VIOLET, (float) (.35 * x.e())); halo(c, x.eye().add(0, 1, 0), .5, 5, "mo_star_blink_sheet", .18, x.t() * .05, VIOLET, x.e(), false); }, "mental_silence_field");
        reg((c, x) -> { for (int i = 0; i < 6; i++) { double a = i * 1.047; streak(c, x.feet().add(Math.cos(a) * 2, .3, Math.sin(a) * 2), x.feet().add(Math.cos(a) * (2 + 8 * x.ph(10)), .3, Math.sin(a) * (2 + 8 * x.ph(10))), .1, RED, x.e()); } }, "mental_panic_field");
        reg((c, x) -> { Vec3 at = x.at(); for (int s = -1; s <= 1; s += 2) ribbon(c, "mo_thread", arc(at.add(x.r().scale(s * 3)), at, .8, 8), .08, MIND, x.e(), x.t() * .2); }, "mental_lure_pair");
        reg((c, x) -> { c.ground("ring", true, x.feet().add(0, .07, 0), 12 * x.grow(10), MIND, (float) (.3 * x.e())); glow(c, "mo_status_arc", x.eye().add(0, .8, 0), x.r().scale(.4), UP.scale(.4), 0, MIND, x.e()); }, "mental_pursuit_field");
        reg((c, x) -> { Vec3 at = x.at(); halo(c, at.add(0, 1.4, 0), .9, 5, "mo_holo_panel", .35, x.t() * .04, MIND, .7 * x.e(), true); }, "mental_out_crowd");
        reg((c, x) -> { Vec3 a = x.feet().add(x.r().scale(-4)).add(0, 1, 0), b = x.feet().add(x.r().scale(4)).add(0, 1, 0); ribbon(c, "mo_thread", arc(a, b, 1.5, 10), .1, RED, x.e(), x.t() * .2); ribbon(c, "mo_thread", arc(b, a, 2.2, 10), .1, RED, x.e(), x.t() * .2); }, "mental_crossfire");
        reg((c, x) -> { Vec3 at = x.at(); for (int i = 0; i < 4; i++) { double a = i * 1.57; var p = arc(at, at.add(Math.cos(a) * 3, 0, Math.sin(a) * 3), 1, 6); ribbon(c, "mo_thread", p, .07, MIND, x.e() * (1 - x.ph(14)), 0); } }, "mental_crowd_detach");
        reg((c, x) -> { var p = new ArrayList<Vec3>(); for (int i = 0; i <= 24; i++) { double a = i / 24.0 * Math.PI * 2 + x.t() * .02; p.add(x.feet().add(Math.cos(a) * 6, 1.2, Math.sin(a) * 6)); } ribbon(c, "mo_thread", p, .12, RED, x.e(), x.t() * .2); }, "mental_conflict_ring");
        reg((c, x) -> { for (int i = 0; i < 3; i++) { Vec3 at = x.at().add(x.r().scale((i - 1) * 2.5)); glow(c, "mo_signal_ring_sheet", at.add(0, 1.2, 0), x.r().scale(.4), UP.scale(.4), Fx4Atlas.get("mo_signal_ring_sheet").frame(frac(x.t() * .05 + i * .3)), VIOLET, x.e() * clamp01((x.t() - i * 4) / 4)); } }, "mental_relay_interference");
        reg((c, x) -> { Vec3 at = x.at(); for (int i = -1; i <= 1; i++) streak(c, at.add(x.r().scale(i * .4)).add(0, 3, 0), at.add(x.r().scale(i * .4)).add(0, 1.1, 0), .05, MIND, x.e());
            glow(c, "mo_holo_panel", at.add(0, 3.1, 0), x.r().scale(.7), x.f().scale(.25), 0, MIND, x.e()); }, "mental_puppet");

        // ============================================================ Float Dial: bubbles, slice rings and light columns
        reg((c, x) -> { Vec3 at = x.onTarget() ? x.end() : x.feet(); sprite(c, "fd_bubble_atlas", at.add(0, 1, 0), 1.6 * x.grow(8), 0, 0, FOAM, .7 * x.e(), false); }, "float_lift");
        reg((c, x) -> { Vec3 at = x.onTarget() ? x.end() : x.feet(); sprite(c, "fd_bubble_atlas", at.add(0, .4 + .6 * x.ph(20), 0), .6, 0, 1, FOAM, .8 * x.e(), false); }, "float_item_lift");
        reg((c, x) -> { Vec3 at = x.onTarget() ? x.end() : x.feet(); for (int i = 0; i < 3; i++) glow(c, "fd_slice_ring", at.add(0, .2 + i * .4 - x.ph(20) * .4, 0), new Vec3(.5, 0, 0), new Vec3(0, 0, .5), 0, FLOAT, x.e()); }, "float_item_lower");
        reg((c, x) -> { var p = arc(x.at().add(0, -.6, 0), x.eye().add(0, -.4, 0), .4, 8); for (int i = 0; i < 5; i++) sprite(c, "fd_bubble_atlas", p.get(Math.min(8, (int) (frac(x.t() * .05 + i / 5.0) * 8))), .25, 0, i, FOAM, x.e(), false); }, "float_item_pull");
        reg((c, x) -> { Vec3 at = x.onTarget() ? x.end() : x.at(); var g = Fx4Atlas.get("fd_pop_sheet"); glow(c, "fd_pop_sheet", at.add(0, .5, 0), x.r().scale(.8), UP.scale(.8), g.frame(x.ph(8)), FOAM, x.e()); }, "float_item_release");
        reg((c, x) -> { Vec3 at = x.onTarget() ? x.end() : x.feet(); sprite(c, "fd_bubble_atlas", at.add(0, 1, 0), 2.2, 0, 2, FOAM, .55 * x.e(), false); }, "float_rescue");
        reg((c, x) -> { Vec3 at = x.at().add(0, -.5, 0); for (int i = 0; i < 3; i++) sprite(c, "fd_bubble_atlas", at.add(x.f().scale(i * .5 * x.ph(10))), .3, 0, i, FOAM, x.e() * (1 - i * .25), false); }, "float_item_push");
        reg((c, x) -> { for (int i = 0; i < 6; i++) { double a = i * 1.047 + x.t() * .01; glow(c, "fd_column", x.feet().add(Math.cos(a) * 6, 2, Math.sin(a) * 6), x.r().scale(.4), UP.scale(2 * x.grow(8)), 0, FLOAT, .5 * x.e()); } }, "float_field");
        reg((c, x) -> { for (int i = 0; i < 4; i++) { double a = i * 1.57 + x.t() * .03; sprite(c, "fd_bubble_atlas", x.feet().add(Math.cos(a) * 2.5, .8 + .3 * Math.sin(x.t() * .1 + i), Math.sin(a) * 2.5), .6, 0, i, FOAM, x.e(), false); } }, "float_item_field");
        reg((c, x) -> { var g = Fx4Atlas.get("fd_pop_sheet"); for (int i = 0; i < 6; i++) { double a = i * 1.047; glow(c, "fd_pop_sheet", x.feet().add(Math.cos(a) * 3, 1, Math.sin(a) * 3), x.r().scale(.6), UP.scale(.6), g.frame(clamp01((x.t() - i) / 8)), FOAM, x.e()); } }, "float_release_field");
        reg((c, x) -> { for (int i = 0; i < 2; i++) glow(c, "fd_slice_ring", x.feet().add(0, -.1 - i * .3, 0), new Vec3(.9 - i * .2, 0, 0), new Vec3(0, 0, .9 - i * .2), 0, FLOAT, x.e()); }, "float_hover");
        reg((c, x) -> { for (int i = 0; i < 4; i++) { double u = frac(x.t() * .04 + i / 4.0); glow(c, "fd_slice_ring", x.feet().add(0, u * 2.5, 0), new Vec3(.8, 0, 0), new Vec3(0, 0, .8), 0, FLOAT, x.e() * (1 - u)); } }, "float_ascent");
        reg((c, x) -> { for (int i = 0; i < 4; i++) { double u = frac(x.t() * .04 + i / 4.0); glow(c, "fd_slice_ring", x.feet().add(0, 2.5 - u * 2.5, 0), new Vec3(.8, 0, 0), new Vec3(0, 0, .8), 0, FLOAT, x.e() * u); } }, "float_descent");
        reg((c, x) -> { for (int i = 0; i < 3; i++) glow(c, "fd_slice_ring", x.feet().add(0, -.2 + i * .05, 0), new Vec3(1.4 + i * .5, 0, 0), new Vec3(0, 0, 1.4 + i * .5), 0, FOAM, x.e() * (1 - i * .25)); }, "float_air_brake");
        reg((c, x) -> { Vec3 at = x.at().add(0, -1, 0); for (int i = 0; i < 2; i++) glow(c, "fd_slice_ring", at.add(0, -.1 - i * .3, 0), new Vec3(.8, 0, 0), new Vec3(0, 0, .8), 0, FLOAT, x.e()); sprite(c, "fd_bubble_atlas", at.add(0, 1, 0), 1.6, 0, 3, FOAM, .5 * x.e(), false); }, "float_ally_hover");
        reg((c, x) -> { Vec3 at = x.at().add(0, -1, 0); glow(c, "fd_column", at.add(0, 1.5, 0), x.r().scale(.5), UP.scale(1.5), 0, FLOAT, .6 * x.e()); }, "float_ally_ascent");
        reg((c, x) -> { Vec3 at = x.at().add(0, -1, 0); for (int i = 0; i < 3; i++) { double u = frac(x.t() * .05 + i / 3.0); glow(c, "fd_slice_ring", at.add(0, 2 - u * 2, 0), new Vec3(.7, 0, 0), new Vec3(0, 0, .7), 0, FOAM, x.e() * u); } }, "float_ally_descent");
        reg((c, x) -> { Vec3 at = x.onTarget() ? x.end() : x.at(); sprite(c, "fd_bubble_atlas", at.add(0, 1, 0), 1.8, 0, 4, FOAM, .6 * x.e(), false); glow(c, "fd_slice_ring", at.add(0, -.05, 0), new Vec3(.9, 0, 0), new Vec3(0, 0, .9), 0, FLOAT, x.e()); }, "float_suspend");
        reg((c, x) -> { Vec3 at = x.onTarget() ? x.end() : x.at(); sprite(c, "fd_bubble_atlas", at.add(0, 1 - .5 * x.ph(20), 0), 1.6, 0, 5, FOAM, .55 * x.e(), false); }, "float_hostile_descent");
        reg((c, x) -> { for (int i = 0; i < 6; i++) { double a = i * 1.047 + x.t() * .015; sprite(c, "fd_bubble_atlas", x.feet().add(Math.cos(a) * 5, 1.2, Math.sin(a) * 5), 1.4, 0, i, FOAM, .5 * x.e(), false); } }, "float_hover_field");
        reg((c, x) -> { for (int i = 0; i < 8; i++) { double a = i * .785; sprite(c, "fd_bubble_atlas", x.feet().add(Math.cos(a) * 3, .4, Math.sin(a) * 3), .8, 0, i, FOAM, .7 * x.e(), false); } glow(c, "fd_slice_ring", x.feet().add(0, .05, 0), new Vec3(4, 0, 0), new Vec3(0, 0, 4), 0, FLOAT, .5 * x.e()); }, "float_rescue_field");
        reg((c, x) -> { Vec3 at = x.onTarget() ? x.end() : x.at(); glow(c, "fd_slice_ring", at.add(0, .05, 0), new Vec3(.6, 0, 0), new Vec3(0, 0, .6), 0, FLOAT, x.e()); glow(c, "fd_column", at.add(0, .8, 0), x.r().scale(.25), UP.scale(.8), 0, FOAM, .5 * x.e()); }, "float_item_shelf");
        reg((c, x) -> { var p = new ArrayList<Vec3>(); for (int i = 0; i <= 16; i++) { double a = i / 16.0 * Math.PI * 2 + x.t() * .08; p.add(x.feet().add(Math.cos(a) * 1.6, 1 + .2 * Math.sin(a * 2), Math.sin(a) * 1.6)); } ribbon(c, "common_speedline", p, .12, FLOAT, x.e(), x.t() * .2); }, "float_item_orbit");
        reg((c, x) -> { for (int i = 0; i < 4; i++) { double a = i * 1.57; Vec3 at = x.feet().add(Math.cos(a) * 2, 0, Math.sin(a) * 2); glow(c, "fd_slice_ring", at.add(0, .05, 0), new Vec3(.5, 0, 0), new Vec3(0, 0, .5), 0, FLOAT, x.e()); glow(c, "fd_column", at.add(0, .7, 0), x.r().scale(.2), UP.scale(.7), 0, FOAM, .5 * x.e()); } }, "float_item_shelf_field");

        // ============================================================ Paper Psychokinesis: sheets, scraps and folds
        reg((c, x) -> { Vec3 a = x.eye(), b = x.at(); double u = clamp01(x.t() / 4); Vec3 p = a.add(b.subtract(a).scale(u)), n = b.subtract(a).normalize();
            plane(c, "pp_sheet_atlas", p, n.scale(.5), rightOf(n).scale(.12), 0, PAPER, x.e()); streak(c, p.subtract(n.scale(1.5)), p, .08, GOLD, .7 * x.e()); }, "paper_blade");
        // Constructs: sheets fly from the hand and lock into the outline of what is being built (bridge, stairs, wall, rungs...)
        reg((c, x) -> fold(c, x, slab(x, 1, 1, 0)), "paper_patch");
        reg((c, x) -> fold(c, x, slab(x, 5, 1, 0)), "paper_walkway", "paper_lift_bridge", "paper_switch_bridge");
        reg((c, x) -> fold(c, x, stairs(x, 3, .5)), "paper_step");
        reg((c, x) -> fold(c, x, stairs(x, 6, .5)), "paper_ramp");
        reg((c, x) -> fold(c, x, slab(x, 2, 2, 0)), "paper_pad");
        reg((c, x) -> fold(c, x, slab(x, 3, 3, 0)), "paper_platform");
        reg((c, x) -> fold(c, x, slab(x, 8, 1, 0)), "paper_catwalk");
        reg((c, x) -> fold(c, x, upright(x, 3, 2, false)), "paper_screen", "paper_cover");
        reg((c, x) -> fold(c, x, upright(x, 4, 1, true)), "paper_safety_rail");
        reg((c, x) -> fold(c, x, stairs(x, 6, 1)), "paper_emergency_stairs");
        reg((c, x) -> fold(c, x, rungs(x, 5)), "paper_ladder");
        reg((c, x) -> { var g = Fx4Atlas.get("pp_scrap_sheet"); plane(c, "pp_scrap_sheet", x.end(), x.r().scale(.7), UP.scale(.7), g.frame(x.ph(12)), PAPER, x.e()); }, "paper_retract");
        reg((c, x) -> { Vec3 a = x.eye(), b = x.at(); double u = clamp01(x.t() / 10); Vec3 p = a.add(b.subtract(a).scale(u)).add(0, Math.sin(u * 6) * .2, 0);
            var path = new ArrayList<Vec3>(); for (int i = 0; i <= 6; i++) { double w = u - i * .03; if (w < 0) break; path.add(a.add(b.subtract(a).scale(w)).add(0, Math.sin(w * 6) * .2, 0)); }
            if (path.size() > 1) ribbon(c, "common_speedline", path, .06, GOLD, .7 * x.e(), 0); plane(c, "pp_sheet_atlas", p, x.f().scale(.3), x.r().scale(.3), 1, PAPER, x.e()); }, "paper_plane");
        reg((c, x) -> { for (int i = 0; i < 6; i++) { double a = i * 1.047 + x.t() * .06; Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a)); plane(c, "pp_sheet_atlas", x.feet().add(d.scale(.75)).add(0, .7 + .25 * Math.sin(x.t() * .1 + i), 0), d.cross(UP).normalize().scale(.25), UP.scale(.3), i, PAPER, x.e()); } }, "paper_armor");
        reg((c, x) -> { for (int s = -1; s <= 1; s += 2) for (int i = 0; i < 4; i++) plane(c, "pp_sheet_atlas", x.eye().add(x.r().scale(s * (.5 + i * .35))).add(x.f().scale(-.3)).add(0, -.1 - i * .08, 0), x.r().scale(s * .2), x.f().scale(.3), i + 2, PAPER, x.e()); }, "paper_wing");
        reg((c, x) -> { Vec3 at = x.end().add(0, .5, 0); glow(c, "pp_sheet_atlas", at, x.r().scale(.25), UP.scale(.25), 3, GOLD, x.e()); flash(c, at, .8, x.ph(8), GOLD, x.e()); }, "paper_switch", "paper_plate", "paper_gate", "paper_puzzle_link");
        reg((c, x) -> { for (int i = 0; i < 6; i++) { double a = i * 1.047 + x.t() * .04; plane(c, "pp_sheet_atlas", x.feet().add(Math.cos(a) * 2.5, 2.4, Math.sin(a) * 2.5), new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(.35), UP.scale(.45), i, PAPER, x.e()); } }, "paper_array");

        // ============================================================ Liquid Shadow: ink, drips and tendrils from the body
        reg((c, x) -> { Vec3 at = x.v().origin(); var g = Fx4Atlas.get("ls_ink_sheet"); plane(c, "ls_ink_sheet", at.add(0, .5, 0), x.r().scale(.9), UP.scale(.9), g.frame(x.ph(14)), INK, x.e()); }, "liquid_scout", "liquid_dismiss");
        reg((c, x) -> { Vec3 at = x.v().origin(); for (int i = 0; i < 2; i++) glow(c, "mo_status_arc", at.add(0, 1.6, 0), x.r().scale(.3 + i * .15), UP.scale(.3 + i * .15), 0, SHADE, x.e()); }, "liquid_pause");
        reg((c, x) -> { Vec3 a = x.v().origin(), b = x.feet(); var p = arc(a.add(0, .1, 0), b.add(0, .1, 0), .1, 8); ribbon(c, "ls_wet_ribbon", p, .3, INK, x.e(), -x.t() * .1); }, "liquid_recall", "liquid_follow");
        reg((c, x) -> { Vec3 at = x.v().origin(); var p = new ArrayList<Vec3>(); for (int i = 0; i <= 10; i++) { double a = i / 10.0 * Math.PI * 1.5 + x.t() * .1; p.add(at.add(Math.cos(a) * .9, .2, Math.sin(a) * .9)); } ribbon(c, "ls_wet_ribbon", p, .2, SHADE, x.e(), 0); }, "liquid_turn", "liquid_retreat");
        reg((c, x) -> { Vec3 at = x.v().origin(); for (int i = 0; i < 3; i++) ring(c, at.add(0, .8, 0), new Vec3(1 + i, 0, 0), new Vec3(0, 0, 1 + i), frac(x.t() / 20.0 + i / 3.0), SHADE, .6 * x.e()); }, "liquid_listen", "liquid_dual_sensor", "liquid_survey");
        reg((c, x) -> { Vec3 at = x.v().origin(); var p = new ArrayList<Vec3>(); for (int i = 0; i <= 8; i++) p.add(at.add(x.f().scale(i * .5)).add(x.r().scale(Math.sin(i * .9) * .4)).add(0, .05, 0)); ribbon(c, "ls_wet_ribbon", p, .25, INK, x.e(), x.t() * .1); }, "liquid_path", "liquid_route");
        reg((c, x) -> { Vec3 at = x.v().origin(); for (int i = 0; i < 3; i++) c.tube("fx4:ls_tendril", CanonFx4.Mode.ALPHA, arc(at, at.add(rot(x.f(), x.r(), (i - 1) * .6).scale(1.4)).add(0, 1.2, 0), .6, 6), u -> .12 * (1 - u) + .03, u -> x.e(), INK, 1, 0, 4); }, "liquid_guard", "liquid_intercept", "liquid_screen", "liquid_switch");
        reg((c, x) -> { Vec3 a = x.v().origin(), b = x.onTarget() ? x.end().add(0, 1, 0) : a.add(x.f().scale(2)).add(0, 1, 0); c.tube("fx4:ls_tendril", CanonFx4.Mode.ALPHA, arc(a, b, 1.2, 8), u -> .2 * (1 - u) + .05, u -> x.e(), INK, 1, -x.t() * .1, 5); flash(c, b, 1.2, x.since() / 6, SHADE, x.e()); }, "liquid_strike", "liquid_contact_cut");
        reg((c, x) -> { Vec3 a = x.v().origin(), b = x.onTarget() ? x.end().add(0, .8, 0) : a.add(x.f().scale(2)); var g = Fx4Atlas.get("ls_ink_sheet"); plane(c, "ls_ink_sheet", b, x.r().scale(1), UP.scale(1), g.frame(x.ph(12)), INK, x.e()); }, "liquid_push", "liquid_pulse");
        reg((c, x) -> { Vec3 a = x.v().origin(), b = x.onTarget() ? x.end().add(0, .5, 0) : a.add(x.f().scale(2)); for (int i = 0; i < 3; i++) { double ang = i * 2.09 + x.t() * .1; c.tube("fx4:ls_tendril", CanonFx4.Mode.ALPHA, arc(a, b.add(Math.cos(ang) * .4, 0, Math.sin(ang) * .4), .8, 6), u -> .1 * (1 - u) + .03, u -> x.e(), INK, 1, 0, 4); } }, "liquid_grapple", "liquid_contact_crush");
        reg((c, x) -> { Vec3 a = x.v().origin(); for (int i = 0; i < 6; i++) { double ang = i * 1.047; c.tube("fx4:ls_tendril", CanonFx4.Mode.ALPHA, arc(a, a.add(Math.cos(ang) * 3, .2, Math.sin(ang) * 3), 1.4, 6), u -> .1 * (1 - u) + .03, u -> x.e(), INK, 1, 0, 4); } }, "liquid_contact_sweep");
        reg((c,x) -> CanonFxLiquid.fieldContact(c,x.v(),x.e()), CanonFxLiquid.FIELD_SKILL);
    }
}

package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.MotifKit.B;
import cn.academy.port.client.MotifKit.M;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Gathers what each motif needs about its cast (hand, view basis, the aim point and the ground under it) and draws it. The aim point is
 * re-read every frame while the key is held and frozen at the first frame after release, so a landed strike does not follow the camera.
 * Read-only: it looks at the world and the cast snapshot and writes nothing to either.
 */
public final class MotifDirector {
    private MotifDirector() {}

    static final int MAX_QUADS = 2600, MAX_LEGACY_QUADS = 700;
    private static final double CULL = 96;
    private static final boolean DEBUG = Boolean.getBoolean("aca.motif.debug");
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final java.util.Set<String> FAILED = new java.util.HashSet<>();
    private record ConfirmedPoint(Vec3 point,long expires){}
    private static final Map<Integer,ConfirmedPoint> ARC_POINTS=new HashMap<>();
    static void acceptedArc(int entity,Vec3 point){
        long now=net.minecraft.Util.getMillis();ARC_POINTS.entrySet().removeIf(e->e.getValue().expires()<now);
        if(ARC_POINTS.size()<128||ARC_POINTS.containsKey(entity))ARC_POINTS.put(entity,new ConfirmedPoint(point,now+1200));
    }

    // QA hook (showcase probe): draws one skill's motif at an exact (age, since) with a fixed clock, without any real cast.
    private static volatile String qaSkill = "";
    private static volatile double qaAge, qaSince = -1;
    private static CastPoseClient.SignatureCast qaCast;
    private static volatile double qaDist = 8, qaLat = -3.5, qaPitch = -9, qaYaw = 0;
    /** Pose a synthetic caster {@code dist} blocks ahead of the camera and {@code lat} to its right, facing screen-right, pitched by {@code pitchDeg}. */
    public static void qaPose(String skill, double age, double since, double dist, double lat, double pitchDeg, double yawDeg) {
        qaSkill = skill; qaAge = age; qaSince = since; qaDist = dist; qaLat = lat; qaPitch = pitchDeg; qaYaw = yawDeg;
    }
    public static void qaPose(String skill, double age, double since, double dist, double lat, double pitchDeg) { qaPose(skill, age, since, dist, lat, pitchDeg, 0); }
    public static void qaPose(String skill, double age, double since) { qaPose(skill, age, since, 8, -3.5, -9); }
    public static void qaOff() { qaSkill = ""; qaCast = null; }
    /** The cast list plus the QA pose cast, when one is set. */
    static List<CastPoseClient.SignatureCast> withQa(List<CastPoseClient.SignatureCast> casts) {
        Minecraft mc = Minecraft.getInstance();
        if (qaSkill.isEmpty() || mc.player == null) return casts;
        String category = "";
        int level = 3;
        for (var sk : cn.academy.port.SkillCatalog.REGISTERED) if (sk.id().equals(qaSkill)) { category = sk.category(); level = sk.level(); break; }
        var archetype = cn.academy.port.highpower.core.CastPose.of(qaSkill);
        qaCast = new CastPoseClient.SignatureCast(mc.player.getId(), qaSkill, archetype == null ? cn.academy.port.highpower.core.CastPose.Archetype.AIM : archetype, qaAge, qaSince, true, category, level);
        var out = new java.util.ArrayList<>(casts);
        out.add(qaCast);
        return out;
    }

    private record Aim(Point end, Point ground, Point normal, Point f, Point s, Point u) {}
    private static final Map<Long, Aim> FROZEN = new HashMap<>();
    private static final Map<Long, Double> SEEN = new HashMap<>();

    static void render(RenderLevelStageEvent event, VisualTier tier, List<CastPoseClient.SignatureCast> casts, float partial, double time, String local) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || tier == null || !tier.enhanced()) return;
        var prims = new MotifRenderer.Prims();
        var legacy = new FxLib.Builder();
        var camera = event.getCamera().getPosition();
        for (var cast : casts) {
            if (!(mc.level.getEntity(cast.entity()) instanceof Player p)) continue;
            // The native mining context owns the live beam, including the expert focus decoration.
            if (cast != qaCast && (ClassicMeltdownerBeamTimeline.MINE.equals(cast.skill())
                    || ClassicAdvancedMineRayTimeline.owns(cast.skill()))) continue;
            // Native MD receivers own the released ray. Keep the enhanced charge choreography,
            // but do not add a second speculative beam through the signature/motif layers.
            if (cast != qaCast && cast.sinceRelease() >= 0
                    && (cast.skill().equals("meltdowner") || cast.skill().equals("electron_bomb"))) continue;
            if (cast != qaCast) fx4(mc, p, cast, partial, time, local);
            if (!SkillMotifs.has(cast.skill())) continue;
            if (camera.distanceToSqr(p.position()) > CULL * CULL) continue;
            String category = CastSignatureDecor.category(cast, local);
            int level = CastSignatureDecor.level(cast, category);
            boolean qa = cast == qaCast;
            long seed = qa ? 1234567L : cast.entity() * 7919L + Math.round(time - cast.age());
            double clock = qa ? 2000 + qaAge + Math.max(0, qaSince) : time;
            Vec3 feet = p.getPosition(partial), hand = CastPoseClient.handAnchor(p, time, partial);
            Aim aim;
            if (qa) {
                Vec3[] syn = synthetic(event, mc, p);
                feet = syn[0]; hand = syn[1];
                aim = new Aim(pt(syn[2]), pt(syn[3]), pt(syn[4]), pt(syn[5]), pt(syn[6]), pt(syn[7]));
            } else {
                aim = aim(mc, p, cast.skill(), partial, seed, cast.sinceRelease() >= 0);
                SEEN.put(seed, time);
            }
            var m = new M(cast.skill(), category, level, pt(feet), pt(hand), aim.f(), aim.s(), aim.u(), aim.end(), aim.ground(), aim.normal(),
                    cast.age(), cast.sinceRelease(), clock, seed, tier);
            if (qa) {   // a stick figure for scale
                var ref = new B(tier);
                ref.line(m.feet(), m.feet().plus(new Point(0, 1.8, 0)), .02, 0xFFFFFF, 150);
                ref.mote(m.feet().plus(new Point(0, 1.8, 0)), .1, 0xFFFFFF, 150);
                ref.mote(m.hand(), .05, 0xFFAA33, 220);
                prims.addAll(ref.p);
            }
            var b = new B(tier);
            try {
                SkillMotifs.draw(b, m);
            } catch (RuntimeException motifFailure) {
                if (FAILED.add(cast.skill())) LOGGER.warn("Motif for {} failed and is skipped this frame", cast.skill(), motifFailure);
                continue;   // decoration only: one bad motif never takes the frame down
            }
            if (DEBUG && Math.round(time * 2) % 2 == 0) LOGGER.info("MOTIF {} age={} since={} ribbons={} sprites={} plates={} end={} ground={}", cast.skill(), String.format("%.1f", cast.age()), String.format("%.1f", cast.sinceRelease()),
                    b.p.ribbons.size(), b.p.sprites.size(), b.p.plates.size(), aim.end(), aim.ground());
            prims.addAll(b.p);
            legacy.add(b.legacy.build());
        }
        SEEN.values().removeIf(t -> time - t > 200);
        FROZEN.keySet().removeIf(k -> !SEEN.containsKey(k));
        MotifRenderer.render(event, prims, tier, MAX_QUADS);
        VisualKit.render(event, legacy.build(), tier, MAX_LEGACY_QUADS);
    }

    /** QA only: feet, hand, end, ground, normal, f, s, u of a caster standing in front of the camera facing screen-right. */
    private static Vec3[] synthetic(RenderLevelStageEvent event, Minecraft mc, Player p) {
        var look = event.getCamera().getLookVector();
        Vec3 L = new Vec3(look.x(), 0, look.z());
        L = L.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : L.normalize();
        Vec3 up = new Vec3(0, 1, 0), R = L.cross(up).normalize();
        var cam = event.getCamera().getPosition();
        double gy = p.getY();
        Vec3 feet = new Vec3(cam.x, gy, cam.z).add(L.scale(qaDist)).add(R.scale(qaLat));
        double pitch = Math.toRadians(qaPitch);
        double yaw = Math.toRadians(qaYaw);
        Vec3 flat = R.scale(Math.cos(yaw)).add(L.scale(Math.sin(yaw)));   // screen-right, turned away from the lens by the yaw
        Vec3 f = flat.scale(Math.cos(pitch)).add(0, Math.sin(pitch), 0).normalize();
        Vec3 s = f.cross(up).normalize(), u = s.cross(f);
        Vec3 eye = feet.add(0, 1.62, 0), hand = eye.add(0, -.35, 0).add(s.scale(.3)).add(f.scale(.3));
        Vec3 end, ground, normal = up;
        if (f.y < -1e-3 && (gy - eye.y) / f.y < 28) {
            end = eye.add(f.scale((gy - eye.y) / f.y));
            ground = end;
        } else {
            end = eye.add(f.scale(28));
            Vec3 flatDir = new Vec3(f.x, 0, f.z).normalize();
            ground = new Vec3(eye.x, gy, eye.z).add(flatDir.scale(12));
            normal = f.scale(-1);
        }
        return new Vec3[] {feet, hand, end, ground, normal, f, s, u};
    }

    /** FX4 layers (particles, ground traces, refraction, caster camera) fire once when a cast is released; motifs and OWN renderers draw the rest. */
    private static void fx4(Minecraft mc, Player p, CastPoseClient.SignatureCast cast, float partial, double time, String local) {
        String category = CastSignatureDecor.category(cast, local);
        long key = cast.entity() * 7919L + Math.round(time - cast.age());
        boolean released = cast.sinceRelease() >= 0;
        if (!released) cn.academy.port.client.canon.CanonFxClassic.observe(key, category, cast.skill(), 0, null, null, null, false, false);
        boolean sig = cn.academy.port.client.canon.ClassicSignatures.wants(cast.skill());
        if (!released && !sig) return;
        Aim aim = aim(mc, p, cast.skill(), partial, key, released);
        Vec3 hand = CastPoseClient.handAnchor(p, time, partial);
        if (sig) cn.academy.port.client.canon.ClassicSignatures.observe(key, category, cast.skill(), CastSignatureDecor.level(cast, category), hand,
                new Vec3(aim.end().x(), aim.end().y(), aim.end().z()), new Vec3(aim.ground().x(), aim.ground().y(), aim.ground().z()), p.getPosition(partial), released);
        if (!released) return;
        cn.academy.port.client.canon.CanonFxClassic.observe(key, category, cast.skill(), CastSignatureDecor.level(cast, category), hand,
                new Vec3(aim.end().x(), aim.end().y(), aim.end().z()), new Vec3(aim.ground().x(), aim.ground().y(), aim.ground().z()), true, p == mc.player);
    }

    private static Point pt(Vec3 v) { return new Point(v.x, v.y, v.z); }

    private static Aim aim(Minecraft mc, Player p, String skill, float partial, long key, boolean released) {
        if (released) {
            Aim kept = FROZEN.get(key);
            if (kept != null) return kept;
        }
        Aim live = read(mc, p, skill, partial);
        if (released) FROZEN.put(key, live);
        return live;
    }

    private static Aim read(Minecraft mc, Player p, String skill, float partial) {
        Vec3 eye = p.getEyePosition(partial), look = p.getViewVector(partial);
        double reach = SkillMotifs.range(skill);
        var block = mc.level.clip(new ClipContext(eye, eye.add(look.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        var clip = java.util.Set.of("arc_gen","thunder_bolt","electron_bomb","dir_shock","dir_blast","blood_retro","threatening_teleport").contains(skill)
                ?cn.academy.port.skill.ClassicRaytrace.living(p,reach,ClipContext.Fluid.NONE):block;
        boolean hit = clip.getType() == HitResult.Type.BLOCK;
        Vec3 end = clip.getLocation();
        var confirmed=skill.equals("arc_gen")?ARC_POINTS.get(p.getId()):null;
        if(confirmed!=null&&confirmed.expires()>=net.minecraft.Util.getMillis())end=confirmed.point();
        Vec3 normal = hit ? new Vec3(block.getDirection().getStepX(), block.getDirection().getStepY(), block.getDirection().getStepZ()) : look.scale(-1);
        Vec3 ground;
        if (hit && block.getDirection() == net.minecraft.core.Direction.UP) {
            ground = end;
        } else {
            Vec3 at;
            if (hit) at = end.add(normal.scale(.3));
            else {
                Vec3 flat = new Vec3(look.x, 0, look.z);
                flat = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
                at = eye.add(flat.scale(Math.min(12, reach)));
            }
            Vec3 from = new Vec3(at.x, Math.max(at.y, p.getY()) + (hit ? .5 : 1.0), at.z);
            var down = mc.level.clip(new ClipContext(from, from.add(0, -60, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            ground = down.getType() == HitResult.Type.BLOCK ? down.getLocation() : new Vec3(at.x, p.getY(), at.z);
        }
        Vec3 f = look.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 s = f.cross(new Vec3(0, 1, 0));
        s = s.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : s.normalize();
        Vec3 u = s.cross(f);
        return new Aim(pt(end), pt(ground), pt(normal.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : normal.normalize()), pt(f), pt(s), pt(u));
    }

    static void clear() { FROZEN.clear(); SEEN.clear(); ARC_POINTS.clear(); }
}

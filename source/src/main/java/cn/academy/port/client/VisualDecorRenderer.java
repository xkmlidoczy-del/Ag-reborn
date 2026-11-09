package cn.academy.port.client;

import cn.academy.port.HighPowerVisualConfig;
import cn.academy.port.VisualTier;
import cn.academy.port.VisualTierConfig;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Renders the CINEMATIC decoration stacks of the effects whose own renderers are frozen by differential proofs
 * (shear windblade, landing array, multicore canopy, trajectory return). Each effect only exposes a read-only
 * snapshot; this class builds the pure decoration, so the frozen sources never reference the decoration kit.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class VisualDecorRenderer {
    private VisualDecorRenderer() {}

    private static boolean on(ModConfigSpec.BooleanValue toggle) {
        try { return HighPowerVisualConfig.SPEC.isLoaded() && toggle.get(); } catch (RuntimeException unreadable) { return false; }
    }
    private static double time(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + (mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }
    private static Point point(double[] v) { return v == null ? null : new Point(v[0], v[1], v[2]); }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        VisualTier tier = VisualTierConfig.tier();
        if (!tier.enhanced()) return;
        // Category/action cues are available at STANDARD too; CINEMATIC adds the larger effect stacks below.
        try { signatures(event, tier); } catch (RuntimeException decorationFailure) { /* optional decoration only */ }
        try { expertMining(event, tier); } catch (RuntimeException decorationFailure) { /* original mining beam remains */ }
        if (tier.layers < 3) return;
        try {
            if (on(HighPowerVisualConfig.SHEAR_WINDBLADE_VOLUME)) shear(event, ShearWindbladeEffects.decorCasts(event), tier);
            if (on(HighPowerVisualConfig.LANDING_ARRAY_CUES)) landing(event, ShearlessCues.cues(event), tier);
            if (on(HighPowerVisualConfig.TRAJECTORY_RETURN_PRESSURE)) trajectory(event, TrajectoryReturnEffects.decorCasts(event), tier);
            railgun(event, ClassicDecorSnapshots.beams(), tier);
            vector(event, tier);
            electro(event, tier);
            teleport(event, tier);
            meltdown(event, tier);
            construct(event, tier);
        } catch (RuntimeException decorationFailure) {
            // Decoration is optional: a failure draws nothing and never touches the effect's own rendering.
        }
    }

    /** Expert focusing is drawn only while the original authenticated mining ray exists. */
    private static void expertMining(RenderLevelStageEvent event, VisualTier tier) {
        var parts = new FxLib.Builder();
        for (var ray : ClassicMeltdownerBeamEffects.expertDecorRays()) {
            var origin = ray.origin(); var direction = ray.direction();
            parts.add(ExpertMiningDecor.fx(new ExpertMiningDecor.Ray(ray.skill(), ray.token(), true, true,
                    new Point(origin.x, origin.y, origin.z), new Point(direction.x, direction.y, direction.z),
                    ray.length(), ray.age()), time(event), tier));
        }
        VisualKit.render(event, parts.build(), tier, ExpertMiningDecor.MAX_QUADS);
    }

    /** Every cast carries its category's signature on top of its own effect (see {@link CastSignatureDecor}). */
    private static void signatures(RenderLevelStageEvent event, VisualTier tier) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var casts = MotifDirector.withQa(CastPoseClient.signatures(partial));
        if (casts.isEmpty()) return;
        String local = "";
        try { local = AcademyClient.state.category; } catch (RuntimeException unavailable) { /* other players only */ }
        var parts = new FxLib.Builder(); double time = time(event);
        var camera = event.getCamera().getPosition();
        var selected = casts.stream().filter(c -> mc.level.getEntity(c.entity()) instanceof net.minecraft.world.entity.player.Player)
                .sorted(java.util.Comparator.comparingDouble((CastPoseClient.SignatureCast c) -> c.local() ? -1
                        : camera.distanceToSqr(mc.level.getEntity(c.entity()).position())))
                .limit(CastSignatureDecor.MAX_CASTS).toList();
        for (var cast : selected) {
            if (!(mc.level.getEntity(cast.entity()) instanceof net.minecraft.world.entity.player.Player p)) continue;
            if (camera.distanceToSqr(p.position()) > VECTOR_CULL * VECTOR_CULL) continue;
            if (SkillMotifs.replaces(cast.skill())) continue;   // the skill has its own visual identity (see MotifDirector)
            String category = CastSignatureDecor.category(cast, local);
            int level = CastSignatureDecor.level(cast, category);
            var feet = p.getPosition(partial); var hand = CastPoseClient.handAnchor(p, time, partial); var look = p.getViewVector(partial);
            parts.add(CastSignatureDecor.fx(cast.skill(), cast.archetype(), category, level, new Point(feet.x, feet.y, feet.z), new Point(hand.x, hand.y, hand.z),
                    new Point(look.x, look.y, look.z), cast.age(), cast.sinceRelease(), cast.entity() * 7919L + (long) Math.floor(time - cast.age()), time, tier));
        }
        VisualKit.render(event, parts.build(), tier, CastSignatureDecor.MAX_QUADS);
        MotifDirector.render(event, tier, selected, partial, time, local);
        skillCues(event, tier, selected, partial, time);
    }

    /** Level-tiered cues for skills with a faint, hidden or instant effect (see {@link SkillFxKit}), plus teleport departure/arrival. */
    private static void skillCues(RenderLevelStageEvent event, VisualTier tier, List<CastPoseClient.SignatureCast> selected, float partial, double time) {
        Minecraft mc = Minecraft.getInstance();
        String local = "";
        try { local = AcademyClient.state.category; } catch (RuntimeException unavailable) { /* other players only */ }
        var parts = new FxLib.Builder();
        var camera = event.getCamera().getPosition();
        for (var cast : selected) {
            if (!(mc.level.getEntity(cast.entity()) instanceof net.minecraft.world.entity.player.Player p)) continue;
            var kind = SkillMotifs.replaces(cast.skill()) ? SkillFxKit.Kind.NONE : SkillFxKit.kind(cast.skill(), cast.archetype());
            if (kind == SkillFxKit.Kind.NONE || camera.distanceToSqr(p.position()) > VECTOR_CULL * VECTOR_CULL) continue;
            String category = CastSignatureDecor.category(cast, local);
            int level = CastSignatureDecor.level(cast, category);
            var feet = p.getPosition(partial); var hand = CastPoseClient.handAnchor(p, time, partial); var look = p.getViewVector(partial);
            var eye = p.getEyePosition(partial);
            double reach = SkillFxKit.range(cast.skill());
            var clip = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(reach)), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, p));
            var hit = clip.getLocation();
            if (kind == SkillFxKit.Kind.MARK && clip.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                // Open sky ahead: put the mark on the ground about ten blocks along the view instead of at the horizon.
                var probe = eye.add(look.x * 10, 0, look.z * 10);
                hit = mc.level.clip(new net.minecraft.world.level.ClipContext(probe, probe.add(0, -12, 0), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, p)).getLocation();
            }
            parts.add(SkillFxKit.fx(kind, category, level, new Point(feet.x, feet.y, feet.z), new Point(hand.x, hand.y, hand.z), new Point(look.x, look.y, look.z),
                    new Point(hit.x, hit.y, hit.z), cast.age(), cast.sinceRelease(), cast.entity() * 7919L + (long) Math.floor(time - cast.age()), time, tier));
        }
        parts.add(TeleportTrace.fx(mc.level.getGameTime() + (mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false)), tier));
        VisualKit.render(event, parts.build(), tier, SkillFxKit.MAX_QUADS);
    }

    /** Thin indirection so the landing snapshot call stays in one place. */
    private static final class ShearlessCues {
        static List<double[]> cues(RenderLevelStageEvent event) { return MultidimensionalLandingArrayEffects.decorCues(event); }
    }

    private static void shear(RenderLevelStageEvent event, List<ShearWindbladeState.Controller> casts, VisualTier tier) {
        if (casts.isEmpty()) return;
        double partial = Minecraft.getInstance().isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false), time = time(event);
        var parts = new FxLib.Builder();
        for (var cast : casts) parts.add(ShearWindbladeDecor.fx(ShearWindbladeRenderGeometry.shape(cast, partial), cast, time, tier));
        VisualKit.render(event, parts.build(), tier, ShearWindbladeDecor.MAX_QUADS);
    }

    /** QA fixture entry: staged controllers, drawn through the same pure decoration. */
    static void shearFixture(RenderLevelStageEvent event, List<ShearWindbladeState.Controller> controllers, double partial) {
        VisualTier tier = VisualTierConfig.tier();
        if (tier.layers < 3 || !on(HighPowerVisualConfig.SHEAR_WINDBLADE_VOLUME)) return;
        var parts = new FxLib.Builder(); double time = time(event);
        for (var controller : controllers) parts.add(ShearWindbladeDecor.fx(ShearWindbladeRenderGeometry.shape(controller, partial), controller, time, tier));
        VisualKit.render(event, parts.build(), tier, ShearWindbladeDecor.MAX_QUADS);
    }

    private static void landing(RenderLevelStageEvent event, List<double[]> cues, VisualTier tier) {
        if (cues.isEmpty()) return;
        var parts = new FxLib.Builder(); double time = time(event);
        for (double[] c : cues) parts.add(LandingArrayDecor.cue(new Point(c[0], c[1], c[2]), c[3] > .5, (int) c[4], MultidimensionalLandingArrayMesh.SUCCESS_TICKS, time, tier));
        VisualKit.render(event, parts.build(), tier, LandingArrayDecor.MAX_QUADS);
    }

    private static final java.util.Set<Long> SHAKEN = new java.util.HashSet<>();

    /** Preserve the original local shake; the A2 presentation follows its accepted packet. */
    private static void railgun(RenderLevelStageEvent event, List<double[]> beams, VisualTier tier) {
        if (beams.isEmpty()) SHAKEN.clear();
        double level = 3;
        try { level = Math.max(1, Math.min(5, cn.academy.port.client.AcademyClient.state.level)); } catch (RuntimeException unavailable) { /* keep the default */ }
        for (double[] b : beams) {
            long id = (long) b[9];
            if (b[8] > .5 && b[7] < 120 && SHAKEN.add(id)) CameraShake.impulse(.25 + .75 * level / 5, 12);
        }
        if (SHAKEN.size() > 64) SHAKEN.clear();
        RailgunDecorClient.render(event, tier);
    }

    private static void trajectory(RenderLevelStageEvent event, List<TrajectoryReturnEffects.DecorCast> casts, VisualTier tier) {
        if (casts.isEmpty()) return;
        var parts = new FxLib.Builder(); double time = time(event);
        for (var cast : casts) {
            List<TrajectoryReturnDecor.Trail> trails = new ArrayList<>();
            for (var t : cast.trails()) {
                List<Point[]> segments = new ArrayList<>();
                for (double[] s : t.segments()) segments.add(new Point[] {new Point(s[0], s[1], s[2]), new Point(s[3], s[4], s[5])});
                trails.add(new TrajectoryReturnDecor.Trail(segments, point(t.reversal()), point(t.position()), point(t.velocity()), t.reversed(), t.reversedAge()));
            }
            parts.add(TrajectoryReturnDecor.fx(trails, cast.token(), time, tier));
        }
        VisualKit.render(event, parts.build(), tier, TrajectoryReturnDecor.MAX_QUADS);
    }

    private static final double VECTOR_CULL = 96;

    /** The ten vector skills: each effect exposes only a snapshot; the skill level (second digit of its id) sets the base power. */
    private static void vector(RenderLevelStageEvent event, VisualTier tier) {
        double level = 3;
        try { level = cn.academy.port.client.AcademyClient.state.level; } catch (RuntimeException unavailable) { /* keep the default */ }
        var parts = new FxLib.Builder(); double time = time(event);
        var camera = event.getCamera().getPosition();
        vectorOne(parts, VectorDecor.Skill.STRIKE, 1, DeflectionStrikeEffects.decorSnaps(event), camera, level, time, tier);
        vectorOne(parts, VectorDecor.Skill.PARRY, 1, VectorParryEffects.decorSnaps(event), camera, level, time, tier);
        vectorOne(parts, VectorDecor.Skill.BRAKE, 1, BrakingTouchEffects.decorSnaps(event), camera, level, time, tier);
        vectorOne(parts, VectorDecor.Skill.ANCHOR, 2, InertialAnchorEffects.decorSnaps(event), camera, level, time, tier);
        vectorOne(parts, VectorDecor.Skill.PALM, 2, YawPalmEffects.decorSnaps(event), camera, level, time, tier);
        vectorOne(parts, VectorDecor.Skill.RELIEF, 3, CirculationReliefEffects.decorSnaps(event), camera, level, time, tier);
        vectorOne(parts, VectorDecor.Skill.LISTEN, 3, CirculationListeningEffects.decorSnaps(event), camera, level, time, tier);
        vectorOne(parts, VectorDecor.Skill.CAGE, 4, VectorCageEffects.decorSnaps(event), camera, level, time, tier);
        vectorOne(parts, VectorDecor.Skill.REFLECT, 4, MultiAxisReflectionEffects.decorSnaps(event), camera, level, time, tier);
        vectorOne(parts, VectorDecor.Skill.CIRCLE, 5, AutonomousVectorCircleEffects.decorSnaps(event), camera, level, time, tier);
        VisualKit.render(event, parts.build(), tier, VectorDecor.MAX_QUADS * 3);
    }

    private static void vectorOne(FxLib.Builder parts, VectorDecor.Skill skill, int skillLevel, List<DecorSnap> snaps,
                                  net.minecraft.world.phys.Vec3 camera, double casterLevel, double time, VisualTier tier) {
        for (DecorSnap snap : snaps) {
            Point at = point(snap.point(0));
            if (at == null || !at.finite() || camera.distanceToSqr(at.x(), at.y(), at.z()) > VECTOR_CULL * VECTOR_CULL) continue;
            parts.add(VectorDecor.fx(skill, snap, time, VectorDecor.power(skillLevel, snap.local() ? casterLevel : 3), tier));
        }
    }

    /** QA fixture entry: a staged snapshot drawn through the same pure decoration. */
    public static void vectorFixture(RenderLevelStageEvent event, VectorDecor.Skill skill, int skillLevel, DecorSnap snap, double casterLevel) {
        VisualTier tier = VisualTierConfig.tier();
        if (tier.layers < 3) return;
        var parts = new FxLib.Builder();
        parts.add(VectorDecor.fx(skill, snap, time(event), VectorDecor.power(skillLevel, casterLevel), tier));
        VisualKit.render(event, parts.build(), tier, VectorDecor.MAX_QUADS * 3);
    }

    /** The electromaster skills: the second digit of the skill id sets the base power. */
    private static void electro(RenderLevelStageEvent event, VisualTier tier) {
        double level = 3;
        try { level = cn.academy.port.client.AcademyClient.state.level; } catch (RuntimeException unavailable) { /* keep the default */ }
        var parts = new FxLib.Builder(); double time = time(event);
        var camera = event.getCamera().getPosition();
        electroOne(parts, ElectroDecor.Skill.TETHER, 1, PolarityTetherEffects.decorSnaps(event), camera, level, time, tier);
        electroOne(parts, ElectroDecor.Skill.CIRCUIT, 2, ConductiveCircuitEffects.decorSnaps(event), camera, level, time, tier);
        electroOne(parts, ElectroDecor.Skill.GROUND, 2, GroundingReturnEffects.decorSnaps(event), camera, level, time, tier);
        electroOne(parts, ElectroDecor.Skill.DECOY, 2, ElectromasterChargeDecoyEffects.decorSnaps(event), camera, level, time, tier);
        electroOne(parts, ElectroDecor.Skill.LANCE, 3, IronSandLanceEffects.decorSnaps(event), camera, level, time, tier);
        electroOne(parts, ElectroDecor.Skill.STEP, 3, MagneticGroundStepEffects.decorSnaps(event), camera, level, time, tier);
        electroOne(parts, ElectroDecor.Skill.PULL, 3, MagneticItemPullEffects.decorSnaps(event), camera, level, time, tier);
        electroOne(parts, ElectroDecor.Skill.NET, 4, ThunderNetEffects.decorSnaps(event), camera, level, time, tier);
        electroOne(parts, ElectroDecor.Skill.BUFFER, 4, PotentialBufferEffects.decorSnaps(event), camera, level, time, tier);
        electroOne(parts, ElectroDecor.Skill.ARC, 1, arcSnaps(), camera, level, time, tier);
        electroOne(parts, ElectroDecor.Skill.BRACER, 1, bracerSnaps(event), camera, level, time, tier);
        VisualKit.render(event, parts.build(), tier, ElectroDecor.MAX_QUADS * 3);
    }

    private static void electroOne(FxLib.Builder parts, ElectroDecor.Skill skill, int skillLevel, List<DecorSnap> snaps,
                                   net.minecraft.world.phys.Vec3 camera, double casterLevel, double time, VisualTier tier) {
        for (DecorSnap snap : snaps) {
            Point at = point(snap.point(0));
            if (at == null || !at.finite() || camera.distanceToSqr(at.x(), at.y(), at.z()) > VECTOR_CULL * VECTOR_CULL) continue;
            parts.add(ElectroDecor.fx(skill, snap, time, ElectroDecor.power(skillLevel, snap.local() ? casterLevel : 3), tier));
        }
    }

    /** QA fixture entry for the electromaster skills. */
    public static void electroFixture(RenderLevelStageEvent event, ElectroDecor.Skill skill, int skillLevel, DecorSnap snap, double casterLevel) {
        VisualTier tier = VisualTierConfig.tier();
        if (tier.layers < 3) return;
        var parts = new FxLib.Builder();
        parts.add(ElectroDecor.fx(skill, snap, time(event), ElectroDecor.power(skillLevel, casterLevel), tier));
        VisualKit.render(event, parts.build(), tier, ElectroDecor.MAX_QUADS * 3);
    }

    /** Forked-arc bolts from the classic arc list: points = origin, end; age = ticks of the ten-tick flicker. */
    private static List<DecorSnap> arcSnaps() {
        List<DecorSnap> out = new ArrayList<>();
        for (double[] a : ClassicDecorSnapshots.arcs()) {
            long token = Double.doubleToLongBits(a[0] * 31 + a[1] * 17 + a[2] * 13 + a[6]);
            out.add(DecorSnap.of(false, (int) a[7], 0, token, List.of(new double[] {a[0], a[1], a[2]}, new double[] {a[0] + a[3] * a[6], a[1] + a[4] * a[6], a[2] + a[5] * a[6]}), a[7], a[6]));
        }
        return out;
    }

    /** Players holding the Magnetic Bracer: the local key hold, or anyone sneaking with the prototype in use (both synced to every client). */
    private static List<DecorSnap> bracerSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        List<DecorSnap> out = new ArrayList<>();
        if (mc.level == null) return out;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        List<double[]> arcs = ClassicDecorSnapshots.arcs();
        for (var player : mc.level.players()) {
            boolean local = player == mc.player;
            boolean active = local && BracerDecorState.heldLocal()
                    || player.isUsingItem() && player.isCrouching() && player.getUseItem().is(cn.academy.port.highpower.ElectromasterPrototype.CONTROL.get());
            if (!active || player.isSpectator() || !player.isAlive()) continue;
            var eye = player.getEyePosition(partial); var look = player.getViewVector(partial);
            var wrist = eye.add(look.scale(.45)).add(0, -.4, 0);
            double[] hit = DecorSnap.NONE; double age = -1;
            for (double[] a : arcs) {
                if (wrist.distanceToSqr(a[0], a[1], a[2]) > .8 * .8) continue;
                if (age < 0 || a[7] < age) { age = a[7]; hit = new double[] {a[0] + a[3] * a[6], a[1] + a[4] * a[6], a[2] + a[5] * a[6]}; }
            }
            double dim = local && mc.options.getCameraType().isFirstPerson() ? .45 : 1;
            out.add(DecorSnap.of(local, 0, 0, player.getId(), List.of(new double[] {wrist.x, wrist.y, wrist.z}, new double[] {look.x, look.y, look.z}, hit), age, dim));
        }
        return out;
    }

    /** Success beats outlive the effect's own seven-tick cosmetic: first sighting of a token starts a decoration tail. */
    private record TeleportTail(TeleportDecor.Skill skill, int level, DecorSnap snap, double start) {}
    private static final java.util.Map<String, TeleportTail> TELEPORT_TAILS = new java.util.LinkedHashMap<>();

    private static void teleport(RenderLevelStageEvent event, VisualTier tier) {
        double level = 3;
        try { level = cn.academy.port.client.AcademyClient.state.level; } catch (RuntimeException unavailable) { /* keep the default */ }
        var parts = new FxLib.Builder(); double time = time(event);
        var camera = event.getCamera().getPosition();
        teleportOne(parts, TeleportDecor.Skill.PROJECTION, 1, DisplacedProjectionEffects.decorSnaps(), camera, level, time, tier);
        teleportOne(parts, TeleportDecor.Skill.SIDESTEP, 1, PhaseSidestepEffects.decorSnaps(), camera, level, time, tier);
        teleportOne(parts, TeleportDecor.Skill.PROBE, 1, SafeLandingProbeEffects.decorSnaps(), camera, level, time, tier);
        teleportOne(parts, TeleportDecor.Skill.SUBSTITUTE, 2, CrossSubstitutionEffects.decorSnaps(), camera, level, time, tier);
        teleportOne(parts, TeleportDecor.Skill.RETURN, 2, ReturnMarkEffects.decorSnaps(), camera, level, time, tier);
        teleportOne(parts, TeleportDecor.Skill.RECOVERY, 2, RecoveryMarkEffects.decorSnaps(), camera, level, time, tier);
        teleportOne(parts, TeleportDecor.Skill.INTERCEPT, 3, ShortInterceptionEffects.decorSnaps(), camera, level, time, tier);
        teleportOne(parts, TeleportDecor.Skill.CORNER, 3, CornerExchangeEffects.decorSnaps(), camera, level, time, tier);
        teleportOne(parts, TeleportDecor.Skill.PINCER, 4, SpatialPincerEffects.decorSnaps(), camera, level, time, tier);
        teleportOne(parts, TeleportDecor.Skill.EMERGENCY, 4, EmergencyReturnEffects.decorSnaps(), camera, level, time, tier);
        teleportOne(parts, TeleportDecor.Skill.RELAY, 4, SpatialRelayEffects.decorSnaps(), camera, level, time, tier);
        teleportOne(parts, TeleportDecor.Skill.COORDINATE, 5, TacticalCoordinateEffects.decorSnaps(), camera, level, time, tier);
        for (var it = TELEPORT_TAILS.values().iterator(); it.hasNext(); ) {
            TeleportTail tail = it.next();
            double age = time - tail.start();
            if (age < 0 || age > TeleportDecor.TAIL_TICKS) { it.remove(); continue; }
            DecorSnap s = tail.snap();
            DecorSnap aged = new DecorSnap(s.local(), (int) Math.floor(age), 1, s.token(), s.points(), s.numbers(), s.polylines());
            parts.add(TeleportDecor.fx(tail.skill(), aged, time, TeleportDecor.power(tail.level(), s.local() ? level : 3), tier));
        }
        VisualKit.render(event, parts.build(), tier, TeleportDecor.MAX_QUADS * 3);
    }

    private static void teleportOne(FxLib.Builder parts, TeleportDecor.Skill skill, int skillLevel, List<DecorSnap> snaps,
                                    net.minecraft.world.phys.Vec3 camera, double casterLevel, double time, VisualTier tier) {
        for (DecorSnap snap : snaps) {
            Point at = null;
            for (int i = 0; i < snap.points().size() && at == null; i++) { Point c = point(snap.point(i)); if (c != null && c.finite()) at = c; }
            if (at == null || camera.distanceToSqr(at.x(), at.y(), at.z()) > VECTOR_CULL * VECTOR_CULL) continue;
            if (snap.phase() == 1) {
                String key = skill.name() + ':' + snap.token();
                if (!TELEPORT_TAILS.containsKey(key)) {
                    if (TELEPORT_TAILS.size() >= 24) TELEPORT_TAILS.remove(TELEPORT_TAILS.keySet().iterator().next());
                    TELEPORT_TAILS.put(key, new TeleportTail(skill, skillLevel, snap, time - snap.age()));
                }
                continue;
            }
            parts.add(TeleportDecor.fx(skill, snap, time, TeleportDecor.power(skillLevel, snap.local() ? casterLevel : 3), tier));
        }
    }

    /** QA fixture entry for the teleporter skills. */
    public static void teleportFixture(RenderLevelStageEvent event, TeleportDecor.Skill skill, int skillLevel, DecorSnap snap, double casterLevel) {
        VisualTier tier = VisualTierConfig.tier();
        if (tier.layers < 3) return;
        var parts = new FxLib.Builder();
        parts.add(TeleportDecor.fx(skill, snap, time(event), TeleportDecor.power(skillLevel, casterLevel), tier));
        VisualKit.render(event, parts.build(), tier, TeleportDecor.MAX_QUADS * 3);
    }

    /** The meltdowner skills (second digit of the id = base power). */
    private static void meltdown(RenderLevelStageEvent event, VisualTier tier) {
        double level = 3;
        try { level = cn.academy.port.client.AcademyClient.state.level; } catch (RuntimeException unavailable) { /* keep the default */ }
        var parts = new FxLib.Builder(); double time = time(event);
        var camera = event.getCamera().getPosition();
        meltOne(parts, MeltDecor.Skill.DUAL, 1, DualPointEffects.decorSnaps(), camera, level, time, tier);
        meltOne(parts, MeltDecor.Skill.PLATES, 1, ScatterPlateEffects.decorSnaps(), camera, level, time, tier);
        meltOne(parts, MeltDecor.Skill.ETCH, 1, BeamEtchingEffects.decorSnaps(), camera, level, time, tier);
        meltOne(parts, MeltDecor.Skill.MIRROR, 2, FoldedMirrorEffects.decorSnaps(), camera, level, time, tier);
        meltOne(parts, MeltDecor.Skill.SHELL, 2, ThermalShellEffects.decorSnaps(), camera, level, time, tier);
        meltOne(parts, MeltDecor.Skill.LOCATOR, 2, ScatterLocatorEffects.decorSnaps(), camera, level, time, tier);
        meltOne(parts, MeltDecor.Skill.SWEEP, 3, DisintegrationSweepEffects.decorSnaps(), camera, level, time, tier);
        meltOne(parts, MeltDecor.Skill.STEP, 3, BeamStepEffects.decorSnaps(), camera, level, time, tier);
        meltOne(parts, MeltDecor.Skill.LAMINATE, 4, ParticleLaminateEffects.decorSnaps(), camera, level, time, tier);
        meltOne(parts, MeltDecor.Skill.CURTAIN, 4, DissipationCurtainEffects.decorSnaps(), camera, level, time, tier);
        meltOne(parts, MeltDecor.Skill.RELAY, 5, RemoteScatterRelayEffects.decorSnaps(), camera, level, time, tier);
        VisualKit.render(event, parts.build(), tier, MeltDecor.MAX_QUADS * 3);
    }

    private static void meltOne(FxLib.Builder parts, MeltDecor.Skill skill, int skillLevel, List<DecorSnap> snaps,
                                net.minecraft.world.phys.Vec3 camera, double casterLevel, double time, VisualTier tier) {
        for (DecorSnap snap : snaps) {
            Point at = null;
            for (int i = 0; i < snap.points().size() && at == null; i++) { Point c = point(snap.point(i)); if (c != null && c.finite()) at = c; }
            if (at == null || camera.distanceToSqr(at.x(), at.y(), at.z()) > VECTOR_CULL * VECTOR_CULL) continue;
            parts.add(MeltDecor.fx(skill, snap, time, MeltDecor.power(skillLevel, snap.local() ? casterLevel : 3), tier));
        }
    }

    /** QA fixture entry for the meltdowner skills. */
    public static void meltFixture(RenderLevelStageEvent event, MeltDecor.Skill skill, int skillLevel, DecorSnap snap, double casterLevel) {
        VisualTier tier = VisualTierConfig.tier();
        if (tier.layers < 3) return;
        var parts = new FxLib.Builder();
        parts.add(MeltDecor.fx(skill, snap, time(event), MeltDecor.power(skillLevel, casterLevel), tier));
        VisualKit.render(event, parts.build(), tier, MeltDecor.MAX_QUADS * 3);
    }

    /** V-ADV-01 cues (preview frame, held structure, throw, landing). */
    private static void construct(RenderLevelStageEvent event, VisualTier tier) {
        var cues = ConstructClient.cues();
        if (cues.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        var parts = new FxLib.Builder(); double time = time(event);
        var camera = event.getCamera().getPosition();
        for (var cue : cues) {
            var v = cue.visual();
            if (camera.distanceToSqr(v.center()) > 128 * 128) continue;
            double age = Math.max(0, time - cue.received());
            var aux=v.aux();
            if(v.phase()==cn.academy.port.highpower.ConstructRuntime.HOLD && mc.level.getEntity(v.entityId()) instanceof net.minecraft.world.entity.player.Player owner)
                aux=CastPoseClient.handAnchor(owner,time,event.getPartialTick().getGameTimeDeltaPartialTick(false));
            if(v.phase()==cn.academy.port.highpower.ConstructRuntime.DASH) {
                var owner=mc.level.getEntity(v.entityId());
                if(owner!=null) parts.add(ConstructDecor.dashFx(new Point(v.min().x,v.min().y,v.min().z), new Point(v.max().x,v.max().y,v.max().z),
                        new Point(owner.getX(),owner.getY(),owner.getZ()), new Point(aux.x,aux.y,aux.z), ConstructClient.phaseAge(v.owner(),time), time, tier));
                continue;
            }
            parts.add(ConstructDecor.fx(v.phase(), new Point(v.center().x, v.center().y, v.center().z), new Point(v.min().x, v.min().y, v.min().z),
                    new Point(v.max().x, v.max().y, v.max().z), new Point(aux.x,aux.y,aux.z), v.blocks(), age, time, tier));
            parts.add(ConstructDecor.controlBeat(v.phase(), new Point(v.center().x,v.center().y,v.center().z), new Point(v.min().x, v.min().y, v.min().z), new Point(v.max().x, v.max().y, v.max().z), new Point(aux.x,aux.y,aux.z), ConstructClient.phaseAge(v.owner(),time), tier));
        }
        VisualKit.render(event, parts.build(), tier, ConstructDecor.MAX_QUADS * 2);
    }
}

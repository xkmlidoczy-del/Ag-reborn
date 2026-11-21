package cn.academy.port.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.*;
import java.util.function.Predicate;

/**
 * Vanilla section queries plus the large loaded bodies whose origins can be outside those sections.
 */
@EventBusSubscriber(modid = "academy")
public final class CombatTargeting {
    private static final Map<ServerLevel, Set<LivingEntity>> LARGE = new IdentityHashMap<>();

    private CombatTargeting() {}

    private static boolean large(Entity body) {
        return body.getBbWidth() > 4 || body.getBbHeight() > 4 || body.isMultipartEntity();
    }

    public static void resize(Entity entity) {
        if (!(entity instanceof LivingEntity living)
                || !(entity.level() instanceof ServerLevel level)) return;
        if (large(entity))
            LARGE.computeIfAbsent(
                            level, ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                    .add(living);
        else if (LARGE.containsKey(level)) LARGE.get(level).remove(living);
    }

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent event) {
        resize(event.getEntity());
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        var large = LARGE.get(event.getLevel());
        if (large != null) large.remove(event.getEntity());
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel) LARGE.remove(event.getLevel());
    }

    private static List<AABB> bounds(LivingEntity body) {
        if (!body.isMultipartEntity() || body.getParts() == null)
            return List.of(body.getBoundingBox());
        List<AABB> result = new ArrayList<>();
        result.add(body.getBoundingBox());
        for (Entity part : body.getParts())
            if (part != null && !part.isRemoved()) result.add(part.getBoundingBox());
        return result;
    }

    public static List<LivingEntity> candidates(
            ServerLevel level, AABB box, Predicate<LivingEntity> eligible) {
        var result =
                new LinkedHashSet<>(level.getEntitiesOfClass(LivingEntity.class, box, eligible));
        var large = LARGE.get(level);
        if (large != null)
            for (LivingEntity body : large) {
                if (!result.contains(body)
                        && body.isAlive()
                        && body.level() == level
                        && level.getEntity(body.getId()) == body
                        && bounds(body).stream().anyMatch(box::intersects)
                        && eligible.test(body)) result.add(body);
            }
        return List.copyOf(result);
    }

    public static double distanceSquared(LivingEntity target, Vec3 point) {
        return bounds(target).stream()
                .mapToDouble(b -> b.distanceToSqr(point))
                .min()
                .orElse(Double.POSITIVE_INFINITY);
    }

    /** Range and obstruction at the surface also apply when the owner revalidates a short cast. */
    public static boolean visibleWithin(LivingEntity observer, LivingEntity target, double range) {
        double limit = range * range;
        if (!large(target)) return observer.distanceToSqr(target) <= limit && observer.hasLineOfSight(target);
        Vec3 eye = observer.getEyePosition();
        for (AABB box : bounds(target)) {
            if (box.distanceToSqr(eye) > limit) continue;
            Vec3 surface = closest(eye, box);
            if (observer.level().clip(new ClipContext(eye, surface, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, observer)).getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    private static Vec3 closest(Vec3 point, AABB box) {
        return new Vec3(Math.clamp(point.x, box.minX, box.maxX), Math.clamp(point.y, box.minY, box.maxY),
                Math.clamp(point.z, box.minZ, box.maxZ));
    }

    /** Look for the nearer giant body before a classic ray's first ordinary hit. */
    public static LivingEntity largeOnAim(ServerLevel level, LivingEntity observer, Vec3 end, HitResult classic) {
        var bodies = LARGE.get(level);
        if (bodies == null || bodies.isEmpty()) return null;
        var eye = observer.getEyePosition();
        if (classic instanceof EntityHitResult hit && !large(hit.getEntity())) {
            var box = hit.getEntity().getBoundingBox().inflate(hit.getEntity().getPickRadius());
            end = box.contains(eye) ? eye : box.clip(eye, end).orElse(end);
        }
        var hits = ray(level, observer, eye, end, 0,
                body -> large(body) && !body.isSpectator() && (body.isPickable() || body.isMultipartEntity()));
        return hits.isEmpty() ? null : hits.getFirst();
    }

    public static List<LivingEntity> ray(
            ServerLevel level,
            LivingEntity observer,
            Vec3 from,
            Vec3 to,
            double padding,
            Predicate<LivingEntity> eligible) {
        var wall =
                level.clip(
                        new ClipContext(
                                from,
                                to,
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                observer));
        Vec3 end = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
        var hits = new LinkedHashMap<LivingEntity, Double>();
        for (var body :
                candidates(
                        level,
                        new AABB(from, end).inflate(Math.max(0.001, padding)),
                        e -> e != observer && e.isAlive() && eligible.test(e))) {
            for (AABB box : bounds(body)) {
                var expanded = box.inflate(padding);
                Vec3 at = expanded.contains(from) ? from : expanded.clip(from, end).orElse(null);
                if (at == null) continue;
                Vec3 surface = closest(at, box);
                if (level.clip(
                                        new ClipContext(
                                                from,
                                                surface,
                                                ClipContext.Block.COLLIDER,
                                                ClipContext.Fluid.NONE,
                                                observer))
                                .getType()
                        != HitResult.Type.MISS) continue;
                hits.merge(body, from.distanceToSqr(surface), Math::min);
            }
        }
        return hits.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
    }
}

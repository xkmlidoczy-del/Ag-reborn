package cn.academy.port.highpower;

import cn.academy.port.highpower.core.ShortInterceptionRules;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Closed NEW331 geometry: loaded finite corridors, actual first bodies, no alternate exits. */
final class ShortInterceptionWorld {
    record Target(LivingEntity entity, UUID uuid, int id, Vec3 position, AABB bounds, Vec3 point) {
        static Target capture(LivingEntity e) { return new Target(e, e.getUUID(), e.getId(), e.position(), e.getBoundingBox(), e.getBoundingBox().getCenter()); }
        boolean same(ServerPlayer p) { return hostile(p, entity) && uuid.equals(entity.getUUID()) && id == entity.getId()
                && position.equals(entity.position()) && bounds.equals(entity.getBoundingBox()); }
    }
    record Plan(Target target, Vec3 source, Vec3 entry, Vec3 exit, Vec3 velocity, double usedRange) {}
    private ShortInterceptionWorld() {}
    static boolean finite(Vec3 p) { return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z)
            && Math.abs(p.x) <= 30_000_000 && Math.abs(p.z) <= 30_000_000 && Math.abs(p.y) <= 30_000_000; }
    static boolean hostile(ServerPlayer p, LivingEntity e) { return DisplacedProjectionWorld.hostile(p, e); }
    static LivingEntity acquire(ServerPlayer p) {
        Vec3 from = p.getEyePosition(), to = from.add(p.getLookAngle().scale(48));
        if (!corridor(p, from, to, .01, .01)) return null;
        var block = p.serverLevel().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        double nearest = block.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : from.distanceToSqr(block.getLocation());
        Entity selected = null;
        var bodies = p.serverLevel().getEntities(p, new AABB(from, to).inflate(.3), e -> e.isPickable() && !e.isSpectator());
        if (bodies.size() > 64) return null;
        for (Entity e : bodies) {
            Optional<Vec3> hit = e.getBoundingBox().contains(from) ? Optional.of(from) : e.getBoundingBox().clip(from, to);
            if (hit.isPresent() && from.distanceToSqr(hit.get()) < nearest) { nearest = from.distanceToSqr(hit.get()); selected = e; }
        }
        return selected instanceof LivingEntity living && visibleTarget(p, living) ? living : null;
    }
    static boolean visibleTarget(ServerPlayer p, LivingEntity target) {
        return hostile(p, target) && p.getEyePosition().distanceTo(target.getBoundingBox().getCenter()) <= 48
                && clear(p, p.getEyePosition(), target.getBoundingBox().getCenter(), target, null);
    }
    /** Null is an overflow/invalid scan, never permission to fall back. Empty is a valid zero-selection. */
    static List<ShortInterceptionArrowBridge.Snapshot> select(ServerPlayer p) {
        Vec3 from = p.getEyePosition(), axis = p.getLookAngle().normalize(), to = from.add(axis.scale(48));
        if (!finite(from) || !finite(axis) || axis.lengthSqr() < .9 || !corridor(p, from, to, .75, .75)) return null;
        var candidates = p.serverLevel().getEntities(p, new AABB(from, to).inflate(.75), e -> e instanceof Arrow);
        if (candidates.size() > 64) return null;
        var result = new ArrayList<ShortInterceptionArrowBridge.Snapshot>();
        for (Entity e : candidates) {
            Arrow arrow = (Arrow)e; Vec3 delta = arrow.position().subtract(from); double along = delta.dot(axis);
            if (along < 0 || along > 48 || delta.subtract(axis.scale(along)).lengthSqr() > .75 * .75
                    || !clear(p, from, arrow.position(), arrow, arrow)) continue;
            var snapshot = ShortInterceptionArrowBridge.snapshot(p, arrow); if (snapshot != null) result.add(snapshot);
        }
        result.sort(Comparator.comparingDouble((ShortInterceptionArrowBridge.Snapshot s) -> s.position().distanceToSqr(from))
                .thenComparing(s -> s.arrow().getUUID()));
        return List.copyOf(result.subList(0, Math.min(4, result.size())));
    }
    static Plan plan(ServerPlayer p, Target target, Vec3 entry, double speed, int slot, int count, Entity medium) {
        Vec3 from = p.getEyePosition(), axis = target.point.subtract(from).normalize();
        if (!finite(entry) || !Double.isFinite(speed) || speed <= 0 || speed > 8 || axis.lengthSqr() < .9) return null;
        Vec3 right = new Vec3(-axis.z, 0, axis.x);
        if (right.lengthSqr() < 1e-8) return null;
        Vec3 exit = target.point.subtract(axis.scale(2)).add(right.normalize().scale(ShortInterceptionRules.offset(slot, count)));
        double used = from.distanceTo(entry) + entry.distanceTo(exit);
        if (!ShortInterceptionRules.path(from.distanceTo(entry), entry.distanceTo(exit), exit.distanceTo(target.point))) return null;
        Plan plan = new Plan(target, from, entry, exit, target.point.subtract(exit).normalize().scale(speed), used);
        return validate(p, plan, medium) ? plan : null;
    }
    static boolean validate(ServerPlayer p, Plan plan, Entity medium) {
        if (plan == null || !plan.target.same(p) || !p.getEyePosition().equals(plan.source)
                || !visibleTarget(p, plan.target.entity) || !finite(plan.entry) || !finite(plan.exit)) return false;
        double width = medium == null ? .25 : medium.getBbWidth(), height = medium == null ? .25 : medium.getBbHeight();
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || width > .6 || height <= 0 || height > .6) return false;
        if (!swept(p, plan.entry, plan.exit, width, height, medium)) return false;
        AABB exit = new AABB(plan.exit.x-width/2, plan.exit.y, plan.exit.z-width/2, plan.exit.x+width/2, plan.exit.y+height, plan.exit.z+width/2);
        if (!p.serverLevel().noCollision(exit) || !p.serverLevel().getEntities(p, exit, e -> e != medium && e.isPickable() && !e.isSpectator()).isEmpty()) return false;
        return clear(p, plan.source, plan.entry, medium, medium) && clear(p, plan.exit, plan.target.point, plan.target.entity, medium);
    }
    static boolean clear(ServerPlayer p, Vec3 from, Vec3 to, Entity endpoint, Entity ignored) {
        if (!corridor(p, from, to, .01, .01)) return false;
        var block = p.serverLevel().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        double nearest = block.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : from.distanceToSqr(block.getLocation());
        var entities = p.serverLevel().getEntities(p, new AABB(from, to).inflate(.3), e -> e != ignored && e.isPickable() && !e.isSpectator());
        if (entities.size() > 64) return false;
        for (Entity e : entities) if (e != endpoint) {
            if (e.getBoundingBox().contains(from)) return false;
            var hit = e.getBoundingBox().clip(from, to); if (hit.isPresent() && from.distanceToSqr(hit.get()) <= Math.min(nearest, from.distanceToSqr(to))) return false;
        }
        return block.getType() == HitResult.Type.MISS;
    }
    static boolean swept(ServerPlayer p, Vec3 from, Vec3 to, double width, double height, Entity ignored) {
        Set<BlockPos> cells = cells(from, to, width/2+.125, height+.125); if (cells == null || !allowed(p, cells)) return false;
        Vec3 a = from.add(0, height/2, 0), b = to.add(0, height/2, 0);
        for (BlockPos pos : cells) {
            if (!p.serverLevel().getFluidState(pos).isEmpty()) return false;
            var shape = p.serverLevel().getBlockState(pos).getCollisionShape(p.serverLevel(), pos);
            for (AABB local : shape.toAabbs()) {
                AABB box = local.move(pos).inflate(width/2, height/2, width/2);
                if (box.contains(a) || box.contains(b) || box.clip(a, b).isPresent()) return false;
            }
        }
        var bodies = p.serverLevel().getEntities(p, new AABB(a, b).inflate(.7), e -> e != ignored && e.isPickable() && !e.isSpectator());
        if (bodies.size() > 64) return false;
        for (Entity e : bodies) { var box = e.getBoundingBox().inflate(width/2, height/2, width/2); if (box.contains(a) || box.contains(b) || box.clip(a,b).isPresent()) return false; }
        return true;
    }
    static boolean corridor(ServerPlayer p, Vec3 from, Vec3 to, double radius, double height) { var cells = cells(from,to,radius,height); return cells != null && allowed(p,cells); }
    private static Set<BlockPos> cells(Vec3 from, Vec3 to, double radius, double height) {
        if (!finite(from) || !finite(to) || from.distanceTo(to) > 48.001) return null;
        int steps = Math.max(1,(int)Math.ceil(from.distanceTo(to)*4)); var cells = new HashSet<BlockPos>();
        for (int i=0;i<=steps;i++) { Vec3 v = from.lerp(to,(double)i/steps);
            for (int x=(int)Math.floor(v.x-radius);x<=Math.floor(v.x+radius);x++)
                for (int y=(int)Math.floor(v.y-radius);y<=Math.floor(v.y+height);y++)
                    for (int z=(int)Math.floor(v.z-radius);z<=Math.floor(v.z+radius);z++) cells.add(new BlockPos(x,y,z));
            if (cells.size()>4096) return null;
        } return cells;
    }
    private static boolean allowed(ServerPlayer p, Set<BlockPos> cells) {
        if (p == null || !p.server.isSameThread()) return false; var w=p.serverLevel();
        for (BlockPos pos : cells) if (pos.getY()<w.getMinBuildHeight() || pos.getY()>=w.getMaxBuildHeight()
                || !w.getWorldBorder().isWithinBounds(pos) || w.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null) return false;
        for (BlockPos pos : cells) if (!w.mayInteract(p,pos)) return false; return true;
    }
}

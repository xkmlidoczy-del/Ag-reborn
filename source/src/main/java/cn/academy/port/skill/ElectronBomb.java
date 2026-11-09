/* AcademyCraft 1.0.7 ElectronBomb/EntityMdBall behavior adapted for NeoForge. See NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AbilityDamage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative immediate activation followed by a delayed, player-following orb shot.
 * This is not a hold/release skill: releasing its input does not cancel an accepted launch.
 * Call perform/tick/abort on the server thread and clear when that server stops.
 * Orb and beam packets describe their actual geometry; they require a separate client renderer.
 */
public final class ElectronBomb {
    public static final String ID = "electron_bomb";
    public static final double RANGE = SkillTuning.number("electron_bomb", "reach");
    public static final double EXPERIENCE_GAIN = .005;
    private static final Map<UUID, List<Cast>> CASTS = new HashMap<>();

    private ElectronBomb() {}

    /** All values are captured before the activation's EXP increment. */
    public static ClassicRules.SkillCost cost(double experience) {
        return new ClassicRules.SkillCost(ClassicRules.lerp(35, 80, experience),
                ClassicRules.lerp(16, 13, experience), ClassicRules.lerp(6, 12, experience),
                RANGE, (int) ClassicRules.lerp(20, 10, experience));
    }

    public static int lifeTicks(double experience) {
        return ClassicRules.clamp(experience, 0, 1) >= .8 ? 5 : 20;
    }

    public static int firingDelayTicks(double experience) {
        return lifeTicks(experience) - 2;
    }

    /** Authoritative category/learning/cooldown gates, independent of client input. */
    static boolean mayStart(AbilityProgress state) {
        return state.category.equals("meltdowner") && state.level >= 1 && state.canUse(ID);
    }

    /** Pure state commit used by deterministic tests and by perform after player validation. */
    public static LaunchPlan prepare(AbilityProgress state, boolean creative) {
        if (!mayStart(state)) return null;
        double experience = state.exp(ID);
        var cost = cost(experience);
        if (!state.consumeSkill(ID,cost.cp(), cost.overload(), creative)) return null;
        state.addExperience(ID, EXPERIENCE_GAIN);
        state.setCooldown(ID, cost.cooldown());
        return new LaunchPlan(cost, lifeTicks(experience));
    }

    /** Accept exactly one cast request; no ammunition, held ticks, or client aim data is used. */
    public static boolean perform(ServerPlayer player) {
        if (!onServerThread(player) || !player.isAlive() || player.isSpectator()) return false;
        var plan = prepare(AbilityStorage.get(player), player.getAbilities().instabuild);
        if (plan == null) return false;

        var world = player.serverLevel();
        // EntityMdBall's random, fixed world-space offset. It follows translation, not later yaw.
        float theta = -player.getYRot() / 180 * Mth.PI
                + (-Mth.PI * .45f + world.random.nextFloat() * (Mth.PI * .9f));
        float range = .8f + world.random.nextFloat() * .5f;
        var offset = new Vec3(Mth.sin(theta) * range,
                -1.2f + world.random.nextFloat() * 1.4f + 1.6,
                Mth.cos(theta) * range);
        var cast = new Cast(world.dimension(), world.getGameTime(), plan, offset,
                player.position().add(offset));
        CASTS.computeIfAbsent(player.getUUID(), id -> new ArrayList<>()).add(cast);
        sendCharge(player, cast);
        return true;
    }

    /** Once per player's server post tick. Cooldown/EXP were already committed at activation. */
    public static void tick(ServerPlayer player) {
        if (!onServerThread(player)) return;
        var pending = CASTS.get(player.getUUID());
        if (pending == null) return;
        var state = AbilityStorage.get(player);
        if (!player.isAlive() || player.isSpectator() || !state.category.equals("meltdowner")
                || !state.learned(ID)) {
            abort(player);
            return;
        }

        var world = player.serverLevel();
        long now = world.getGameTime();
        List<Cast> ready = new ArrayList<>();
        var iterator = pending.iterator();
        while (iterator.hasNext()) {
            var cast = iterator.next();
            if (!cast.dimension.equals(world.dimension()) || now < cast.startedAt) {
                iterator.remove();
            } else if (cast.plan.ready(now - cast.startedAt)) {
                // Remove before damage callbacks, so repeated ticks/reentrant hooks cannot double fire.
                iterator.remove();
                ready.add(cast);
            } else if (now > cast.lastUpdatedAt) {
                cast.lastPosition = player.position().add(cast.offset);
                cast.lastUpdatedAt = now;
            }
        }
        if (pending.isEmpty()) CASTS.remove(player.getUUID());
        for (var cast : ready) fire(player, cast);
    }

    /** Lifecycle cleanup only. Do not connect ordinary key-up to this method. No refund is due. */
    public static void abort(ServerPlayer player) {
        if (onServerThread(player)) CASTS.remove(player.getUUID());
    }

    /** Clear transient, nonpersisted launches after server stop. */
    public static void clear() {
        CASTS.clear();
    }

    private static boolean onServerThread(ServerPlayer player) {
        return player.serverLevel().getServer().isSameThread();
    }

    private static void fire(ServerPlayer player, Cast cast) {
        var world = player.serverLevel();
        // LambdaLib EntityX invokes callbacks in super.onUpdate BEFORE EntityMdBall.updatePosition.
        // Consequently the original shot starts at the preceding ball-update position.
        Vec3 from = cast.lastPosition;
        Vec3 destination = lookingDestination(player);
        var hit = trace(world, player, from, destination);
        if (hit instanceof EntityHitResult entityHit) {
            // Normal armor/NeoForge damage events apply. The original skill does not bypass armor.
            RadiationMarks.attack(player, entityHit.getEntity(),
                    (float) cast.plan.cost.damage());
        }
        // Classic visuals extend to the looking destination even when the oblique orb ray hit earlier.
        sendRay(player, from, destination);
    }

    private static Vec3 lookingDestination(ServerPlayer player) {
        Vec3 from = player.getEyePosition();
        // LambdaLib Motion3D(true) uses head yaw, not body/entity yaw.
        float yaw = player.getYHeadRot() / 180 * Mth.PI;
        float pitch = player.getXRot() / 180 * Mth.PI;
        var look = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch),
                Mth.cos(yaw) * Mth.cos(pitch)).normalize();
        Vec3 end = from.add(look.scale(RANGE));
        var hit = trace(player.serverLevel(), player, from, end);
        if (hit instanceof EntityHitResult entityHit) {
            // Original getLookingPos uses the entity's base position, then adds 60% of eye height.
            return entityHit.getLocation().add(0, entityHit.getEntity().getEyeHeight() * .6, 0);
        }
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    private static HitResult trace(ServerLevel world, ServerPlayer player, Vec3 from, Vec3 to) {
        var block = world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        // LambdaLib requires canBeCollidedWith. Modern isPickable is its ray-targeting counterpart.
        // Logical balls are not world entities and therefore cannot occlude another ball's shot.
        Entity nearest = null;
        double best = 0;
        for (var candidate : world.getEntities(player, new AABB(from, to).inflate(1), Entity::isPickable)) {
            double distance = ThreateningTeleport.classicEntityDistanceSquared(candidate.getBoundingBox(), from, to);
            if (Double.isFinite(distance) && (distance < best || best == 0)) {
                nearest = candidate;
                best = distance;
            }
        }
        if (nearest == null) return block;
        var entity = new EntityHitResult(nearest, nearest.position());
        // Preserve the classic helper's unusual distance comparison using entity.position(),
        // rather than the bounding-box intersection; the entity wins equal distances.
        return block.getType() == HitResult.Type.MISS
                || from.distanceToSqr(entity.getLocation()) <= from.distanceToSqr(block.getLocation())
                ? entity : block;
    }

    private static CompoundTag effectTag(ServerPlayer player, String kind, Vec3 from) {
        var tag = new CompoundTag();
        tag.putString("kind", kind);
        tag.putInt("entity", player.getId());
        tag.putDouble("x", from.x);
        tag.putDouble("y", from.y);
        tag.putDouble("z", from.z);
        return tag;
    }

    private static void sendCharge(ServerPlayer player, Cast cast) {
        var tag = effectTag(player, "electron_bomb_charge", cast.lastPosition);
        tag.putInt("life", cast.plan.life);
        // Offsets include the original server-only +1.6 Y correction.
        tag.putDouble("offset_x", cast.offset.x);
        tag.putDouble("offset_y", cast.offset.y);
        tag.putDouble("offset_z", cast.offset.z);
        broadcast(player, tag);
    }

    private static void sendRay(ServerPlayer player, Vec3 from, Vec3 to) {
        var tag = effectTag(player, "electron_bomb", from);
        Vec3 delta = to.subtract(from);
        Vec3 direction = delta.normalize();
        tag.putDouble("length", delta.length());
        tag.putDouble("dx", direction.x);
        tag.putDouble("dy", direction.y);
        tag.putDouble("dz", direction.z);
        broadcast(player, tag);
    }

    private static void broadcast(ServerPlayer player, CompoundTag tag) {
        var pos = player.position();
        PacketDistributor.sendToPlayersNear(player.serverLevel(), null, pos.x, pos.y, pos.z,
                50, new AcademyNetwork.ClientData(tag));
    }

    public record LaunchPlan(ClassicRules.SkillCost cost, int life) {
        boolean ready(long elapsedTicks) { return elapsedTicks >= life - 2; }
    }

    private static final class Cast {
        final ResourceKey<Level> dimension;
        final long startedAt;
        final LaunchPlan plan;
        final Vec3 offset;
        long lastUpdatedAt;
        Vec3 lastPosition;

        Cast(ResourceKey<Level> dimension, long startedAt, LaunchPlan plan, Vec3 offset, Vec3 position) {
            this.dimension = dimension;
            this.startedAt = startedAt;
            this.plan = plan;
            this.offset = offset;
            this.lastPosition = position;
            this.lastUpdatedAt = startedAt;
        }
    }
}

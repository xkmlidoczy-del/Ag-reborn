/* AcademyCraft 1.0.7 DirectedShock and LambdaLib 1.2.3 Raytrace adaptation. See NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AbilityDamage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.core.LegacySingleKeyProtocol;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-owned hold/release context for VecManip's first classic skill.
 * Call every entry point on the server thread. A client never supplies duration,
 * mastery, costs, damage, position, direction, or the selected target.
 */
public final class DirectedShock {
    public static final String ID = "dir_shock";
    public static final int MIN_TICKS = 6;
    public static final int MAX_ACCEPTED_TICKS = 50;
    public static final int MAX_TOLERANT_TICKS = 200;
    public static final int PUNCH_ANIM_TICKS = 6;
    public static final double RANGE = SkillTuning.number("dir_shock", "reach");
    public static final double HIT_EXPERIENCE = 0.0035;
    public static final double MISS_EXPERIENCE = 0.0010;

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final LegacySingleKeyProtocol.NonceLedger INPUTS = new LegacySingleKeyProtocol.NonceLedger();
    private static long nextToken;

    private DirectedShock() {}

    private record Hold(ServerLevel level, AbilityProgress state, long startedAt, float damage,
                        LegacySingleKeyServerIdentity identity) {}

    /** Classic strict bounds: releasing at 6 or 50 ticks cancels the skill. */
    public static boolean acceptsRelease(long heldTicks) {
        return heldTicks > MIN_TICKS && heldTicks < MAX_ACCEPTED_TICKS;
    }

    /** A hold can remain prepared after 49 ticks, but cannot then be performed. */
    public static boolean acceptsHold(long heldTicks) {
        return heldTicks >= 0 && heldTicks < MAX_TOLERANT_TICKS;
    }

    public static ClassicRules.SkillCost cost(double mastery) {
        return new ClassicRules.SkillCost(ClassicRules.lerp(50, 100, mastery),
                ClassicRules.lerp(18, 12, mastery), ClassicRules.lerp(7, 15, mastery),
                RANGE, (int) ClassicRules.lerp(60, 20, mastery));
    }

    /** Includes catalog/category/level checks in addition to shared resource state. */
    public static boolean canUse(AbilityProgress state) {
        return state != null && "vecmanip".equals(state.category) && state.canUse(ID)
                && SkillCatalog.find(state.category, ID)
                .filter(skill -> skill.controllable() && state.level >= skill.level()).isPresent();
    }

    /** Key-down. Repeated requests do not replace an existing hold's timestamp. */
    public static boolean start(ServerPlayer player) { return startInternal(player, 0); }

    /** Positive physical-input identity; the input0 overload remains trusted server-only glue. */
    public static boolean canAcceptInput(ServerPlayer player, long input) {
        return LegacySingleKeyServerIdentity.canClaim(INPUTS, player, input);
    }

    public static boolean start(ServerPlayer player, long input) {
        return input > 0 && startInternal(player, input);
    }

    private static boolean startInternal(ServerPlayer player, long input) {if(cn.academy.port.AbilityConsumption.busy(player))return false;
        if (!onServerThread(player)) return false;
        AbilityProgress state = AbilityStorage.get(player);
        if (!canAct(player, state)) return false;
        Hold previous = HOLDS.get(player.getUUID());
        if (previous != null) {
            if (valid(player, state, previous)) return false;
            abort(previous.identity().owner());
            if (HOLDS.get(player.getUUID()) == previous) return false;
        }
        if (nextToken == Long.MAX_VALUE
                || input > 0 && !LegacySingleKeyServerIdentity.claim(INPUTS, player, input)) return false;
        // Legacy damage is a val captured when ShockContext is constructed.
        Hold hold = new Hold(player.serverLevel(), state,
                player.serverLevel().getGameTime(), (float) cost(state.exp(ID)).damage(),
                new LegacySingleKeyServerIdentity(player, input, ++nextToken, state));
        HOLDS.put(player.getUUID(), hold);
        sendVisual(player, hold, "dir_shock_prepare", 0);
        return true;
    }

    /** Tick from PlayerTickEvent.Post; rejects stale state, death and dimension changes. */
    public static void tick(ServerPlayer player) {
        if (!onServerThread(player)) return;
        Hold hold = HOLDS.get(player.getUUID());
        if (hold != null && !valid(player, AbilityStorage.get(player), hold)) abort(player);
    }

    /**
     * Key-up. Removes the context before any effects, preventing replay/double spend.
     * True means CP consumption succeeded, including a paid miss; false means cancel.
     */
    public static boolean release(ServerPlayer player) { return releaseInternal(player, 0, false); }

    /** Accepted context terminals require all issued identity components. */
    public static boolean release(ServerPlayer player, long input, long token, long ownerEpoch) {
        if (!onServerThread(player)) return false;
        Hold hold = HOLDS.get(player.getUUID());
        return hold != null && hold.identity().matches(player, input, token, ownerEpoch)
                && release(player, input);
    }

    public static boolean release(ServerPlayer player, long input) {
        return input > 0 && releaseInternal(player, input, true);
    }

    private static boolean releaseInternal(ServerPlayer player, long input, boolean correlated) {
        if (!onServerThread(player)) return false;
        Hold owned = HOLDS.get(player.getUUID());
        if (owned == null || owned.identity().owner() != player) return false;
        if (correlated && (owned == null || !owned.identity().matches(player, input))) return false;
        Hold hold = HOLDS.remove(player.getUUID());
        if (hold == null) return false;
        AbilityProgress state = AbilityStorage.get(player);
        long heldTicks = player.serverLevel().getGameTime() - hold.startedAt();
        if (!valid(player, state, hold) || !acceptsRelease(heldTicks)) {
            sendVisual(player, hold, "dir_shock_abort", 0);
            return false;
        }
        var cost = cost(state.exp(ID));
        if (!state.consumeSkill(ID,cost.cp(), cost.overload(), player.getAbilities().instabuild)) {
            sendVisual(player, hold, "dir_shock_abort", 0);
            return false;
        }

        Entity target = trace(player);
        if (target != null) {
            // Preserve hit classification even if armor/invulnerability rejects damage.
            // Do not let the extra impulse bypass vanilla PvP/team protection.
            boolean playerProtected = target instanceof Player && !AcademyConfig.ATTACK_PLAYERS.get()
                    || target instanceof ServerPlayer other && !other.canHarmPlayer(player);
            if (!playerProtected) {
                AbilityDamage.attack(player, "vecmanip.dir_shock", target, hold.damage());
                knockback(player, target, state.exp(ID));
            }
            recordResult(state, true);
            sendVisual(player, hold, "dir_shock", RANGE);
        } else {
            recordResult(state, false);
            sendVisual(player, hold, "dir_shock_abort", 0);
        }
        return true;
    }

    /** Key abort, logout, clone, or explicit ability/preset cancellation. */
    public static void abort(ServerPlayer player) {
        if (!onServerThread(player)) return;
        Hold hold = HOLDS.get(player.getUUID());
        if (hold == null || hold.identity().owner() != player) return;
        HOLDS.remove(player.getUUID());
        sendVisual(player, hold, "dir_shock_abort", 0);
    }

    public static boolean abort(ServerPlayer player, long input, long token, long ownerEpoch) {
        if (!onServerThread(player)) return false;
        Hold hold = HOLDS.get(player.getUUID());
        return hold != null && hold.identity().matches(player, input, token, ownerEpoch)
                && abort(player, input);
    }

    public static boolean abort(ServerPlayer player, long input) {
        if (!onServerThread(player) || input <= 0) return false;
        Hold hold = HOLDS.get(player.getUUID());
        if (hold == null || !hold.identity().matches(player, input)) return false;
        abort(player);
        return true;
    }

    /** Final owner/session removal, unlike an ordinary cancellation, retires replay history. */
    public static void remove(ServerPlayer player) {
        if (!onServerThread(player)) return;
        abort(player);
        INPUTS.forget(player);
    }

    /** ServerStoppedEvent; never persists transient input contexts. */
    public static void clear() {
        HOLDS.clear();
        INPUTS.clear();
    }

    /** Dedicated identity envelope; generic AcademyNetwork.effect callers retain their geometry API. */
    private static void sendVisual(ServerPlayer player, Hold hold, String kind, double length) {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", kind);
        hold.identity().write(tag);
        tag.putLong("tick", Math.max(0, hold.level().getGameTime() - hold.startedAt()));
        tag.putDouble("length", length);
        Vec3 origin = player.getEyePosition(), direction = ClassicRaytrace.direction(player);
        tag.putDouble("x", origin.x); tag.putDouble("y", origin.y); tag.putDouble("z", origin.z);
        tag.putDouble("dx", direction.x); tag.putDouble("dy", direction.y); tag.putDouble("dz", direction.z);
        PacketDistributor.sendToPlayersNear(player.serverLevel(), null, origin.x, origin.y, origin.z, 32,
                new AcademyNetwork.ClientData(tag));
    }

    private static boolean onServerThread(ServerPlayer player) {
        return player != null && player.serverLevel().getServer().isSameThread();
    }

    private static boolean canAct(ServerPlayer player, AbilityProgress state) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && canUse(state)
                && finite(player.getEyePosition()) && finite(player.getLookAngle())
                && player.getLookAngle().lengthSqr() > 0;
    }

    private static boolean valid(ServerPlayer player, AbilityProgress state, Hold hold) {
        return hold.identity().owner() == player && hold.level() == player.serverLevel() && hold.state() == state
                && (hold.identity().input() == 0 || hold.identity().owns(player))
                && canAct(player, state)
                && acceptsHold(player.serverLevel().getGameTime() - hold.startedAt());
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static Entity trace(ServerPlayer player) {
        var world = player.serverLevel();
        Vec3 from = player.getEyePosition();
        Vec3 end = from.add(player.getLookAngle().normalize().scale(RANGE));
        Entity target = null;
        double closest = 0;
        for (Entity candidate : world.getEntities(player, new AABB(from, end).inflate(1),
                DirectedShock::traceable)) {
            Vec3 intercept = classicIntercept(candidate.getBoundingBox().inflate(0.3F), from, end);
            if (intercept == null) continue;
            double distance = from.distanceToSqr(intercept);
            if (distance < closest || closest == 0) {
                target = candidate;
                closest = distance;
            }
        }
        if (target == null) return null;
        var block = world.clip(new ClipContext(from, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        // LambdaLib selected the closest inflated-hitbox intercept, but compared the
        // resulting entity's feet against blocks. Preserve that unusual final test.
        return block.getType() == HitResult.Type.MISS
                || entityWinsBlock(from, target.position(), block.getLocation())
                ? target : null;
    }

    private static boolean traceable(Entity entity) {
        return (entity instanceof LivingEntity || entity instanceof EnderDragonPart)
                && entity.isAlive() && !entity.isSpectator() && entity.isPickable();
    }

    static boolean entityWinsBlock(Vec3 origin, Vec3 targetFeet, Vec3 blockHit) {
        return origin.distanceToSqr(targetFeet) <= origin.distanceToSqr(blockHit);
    }

    /**
     * Classic calculateIntercept tested all six planes, including an exit when the
     * origin was inside. Modern AABB.clip only tests entry planes, so cannot stand in
     * for it here. Equal segment-end and boundary-origin intersections remain valid.
     */
    static Vec3 classicIntercept(AABB box, Vec3 from, Vec3 to) {
        double[] origin = {from.x, from.y, from.z};
        double[] delta = {to.x - from.x, to.y - from.y, to.z - from.z};
        double[] low = {box.minX, box.minY, box.minZ};
        double[] high = {box.maxX, box.maxY, box.maxZ};
        double nearest = Double.POSITIVE_INFINITY;
        Vec3 result = null;
        for (int axis = 0; axis < 3; axis++) {
            // Vec3.getIntermediateWith*Value used this squared-delta threshold.
            if (delta[axis] * delta[axis] < 1.0000000116860974E-7) continue;
            for (double plane : new double[]{low[axis], high[axis]}) {
                double t = (plane - origin[axis]) / delta[axis];
                if (t < 0 || t > 1 || !Double.isFinite(t)) continue;
                int a = (axis + 1) % 3;
                int b = (axis + 2) % 3;
                double pointA = origin[a] + delta[a] * t;
                double pointB = origin[b] + delta[b] * t;
                if (pointA < low[a] || pointA > high[a] || pointB < low[b] || pointB > high[b]) continue;
                Vec3 point = from.add(delta[0] * t, delta[1] * t, delta[2] * t);
                double distance = from.distanceToSqr(point);
                if (distance < nearest) {
                    nearest = distance;
                    result = point;
                }
            }
        }
        return result;
    }

    static void recordResult(AbilityProgress state, boolean hit) {
        if (hit) state.setCooldown(ID, cost(state.exp(ID)).cooldown());
        state.addExperience(ID, hit ? HIT_EXPERIENCE : MISS_EXPERIENCE);
    }

    /** Exact two-normalization strong knockback; y is overwritten between them. */
    static Vec3 strongImpulse(Vec3 casterHead, Vec3 targetHead) {
        Vec3 direction = casterHead.subtract(targetHead).normalize();
        return new Vec3(direction.x, -0.6F, direction.z).normalize().scale(-0.7F);
    }

    /** Called after the optional .1 vertical lift, as in the classic source. */
    static Vec3 extraImpulse(Vec3 casterFeet, Vec3 targetFeet) {
        return targetFeet.subtract(casterFeet).normalize().scale(0.24);
    }

    static boolean hasStrongKnockback(double mastery) {
        return mastery >= 0.25F;
    }

    public static void knockback(LivingEntity player, Entity target, double mastery) {
        if (hasStrongKnockback(mastery)) {
            Vec3 impulse = strongImpulse(player.getEyePosition(), target.getEyePosition());
            target.setPos(target.getX(), target.getY() + 0.1, target.getZ());
            target.setDeltaMovement(impulse);
        }
        target.setDeltaMovement(target.getDeltaMovement().add(extraImpulse(player.position(), target.position())));
        // Modern tracking needs explicit dirty flags for the overwritten velocity.
        target.hasImpulse = true;
        target.hurtMarked = true;
    }
}

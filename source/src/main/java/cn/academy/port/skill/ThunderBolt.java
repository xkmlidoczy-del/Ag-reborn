/* AcademyCraft 1.0.7 ThunderBolt/EMDamageHelper adaptation. GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.AbilityDamage;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.AcademyNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative immediate ray plus endpoint sphere, never a generic projectile. */
public final class ThunderBolt {
    public static final String ID = ThunderBoltRules.ID;
    public static final double RANGE = ThunderBoltRules.RANGE, AOE_RANGE = ThunderBoltRules.AOE_RANGE;
    // Only the visual packet is bounded. Every source-selected AOE receives its damage attempt.
    private static final int MAX_VISUAL_TARGETS = 256;
    // Only live stack frames are reserved; no tick, logout or persistence lifecycle is needed.
    private static final Set<UUID> PERFORMING = new HashSet<>();

    private ThunderBolt() {}

    public static boolean perform(ServerPlayer player) {
        return player != null && perform(player, player.serverLevel().random);
    }

    /**
     * Trusted server-only chance seam for deterministic native fixtures. The wire dispatch
     * calls perform(player) exclusively; a client never supplies this random source.
     */
    public static boolean perform(ServerPlayer player, RandomSource random) {
        if (player == null || !player.serverLevel().getServer().isSameThread() || player.isRemoved()
                || !player.isAlive() || player.isSpectator() || !finite(player.getEyePosition())
                || !finite(ClassicRaytrace.direction(player)) || random == null) return false;
        if (!PERFORMING.add(player.getUUID())) return false;
        try {
            return performAccepted(player, random);
        } finally {
            PERFORMING.remove(player.getUUID());
        }
    }

    private static boolean performAccepted(ServerPlayer player, RandomSource random) {
        var state = AbilityStorage.get(player);
        var plan = ThunderBoltRules.prepare(state, player.getAbilities().instabuild);
        if (plan == null) return false;

        AttackData data = attackData(player);
        // The classic context sends its accepted effect before invoking any attack hooks.
        sendEffect(player, data);
        if (data.target != null) {
            attack(player, data.target, plan.cost().damage(), random);
            if (ThunderBoltRules.rollsSlowdown(plan.experience())
                    && random.nextDouble() < .8
                    && data.target instanceof LivingEntity living && mayAffect(player, living))
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        ThunderBoltRules.DIRECT_SLOW_TICKS, ThunderBoltRules.SLOW_AMPLIFIER));
        }
        for (Entity entity : data.aoes) {
            attack(player, entity, plan.aoeDamage(), random);
            // Preserve the original wrong-target bug: AOE rolls slow the PRIMARY target.
            // Scala null.isInstanceOf returns false. This Java instanceof guard also does.
            if (ThunderBoltRules.rollsSlowdown(plan.experience())
                    && random.nextDouble() < .8
                    && data.target instanceof LivingEntity living && mayAffect(player, living))
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        ThunderBoltRules.AOE_SLOW_TICKS, ThunderBoltRules.SLOW_AMPLIFIER));
        }
        ThunderBoltRules.complete(state, plan, data.target != null, data.aoes.size(),()->{cn.academy.port.achievements.ClassicAchievements.trigger(player,"electromaster.thunder_bolt");});
        return true;
    }

    /** Public for narrow native fixtures; the client never supplies point, entities or aim. */
    public static AttackData attackData(ServerPlayer player) {
        HitResult hit = ClassicRaytrace.living(player, RANGE, ClipContext.Fluid.NONE);
        Entity primary = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        Vec3 point;
        if (hit.getType() == HitResult.Type.MISS) {
            Vec3 velocity = player.getDeltaMovement();
            var offset = ThunderBoltRules.missOffset(velocity.x, velocity.y, velocity.z);
            point = player.getEyePosition().add(offset.x(), offset.y(), offset.z());
        } else {
            // LambdaLib entity hits report entity feet; ThunderBolt adds the full eye height.
            point = primary == null ? hit.getLocation() : hit.getLocation().add(0, primary.getEyeHeight(), 0);
        }
        Vec3 center = point;
        List<Entity> aoes = player.serverLevel().getEntities(player,
                new AABB(center, center).inflate(AOE_RANGE),
                entity -> entity != primary && !entity.isSpectator()
                        && (entity instanceof LivingEntity || entity instanceof EnderDragonPart)
                        && ThunderBoltRules.withinAoe(entity.position().distanceToSqr(center)));
        return new AttackData(primary, List.copyOf(aoes), point);
    }

    private static boolean mayAffect(ServerPlayer player, Entity target) {
        return !(target instanceof Player && !AcademyConfig.ATTACK_PLAYERS.get())
                && !(target instanceof ServerPlayer other && !other.canHarmPlayer(player));
    }

    private static void attack(ServerPlayer player, Entity target, double damage, RandomSource random) {
        if (!mayAffect(player, target)) return;
        AbilityDamage.attack(player, "electromaster.thunder_bolt", target, damage);
        // EMDamageHelper charges creepers after ctx.attack, even when hurt returned false.
        if (target instanceof Creeper creeper && random.nextFloat() < .3F) {
            CompoundTag saved = new CompoundTag();
            creeper.addAdditionalSaveData(saved);
            saved.putBoolean("powered", true);
            creeper.readAdditionalSaveData(saved);
            cn.academy.port.achievements.ClassicAchievements.trigger(player,"electromaster.attack_creeper");
        }
    }

    private static void sendEffect(ServerPlayer player, AttackData data) {
        var tag = new CompoundTag();
        tag.putString("kind", "thunder_bolt");
        tag.putInt("entity", player.getId());
        putVector(tag, "x", "y", "z", player.getEyePosition());
        putVector(tag, "dx", "dy", "dz", ClassicRaytrace.direction(player));
        putVector(tag, "point_x", "point_y", "point_z", data.point);
        if (data.target != null) tag.putInt("target", data.target.getId());
        var aoes = new ListTag();
        for (int i = 0; i < Math.min(MAX_VISUAL_TARGETS, data.aoes.size()); i++) {
            Entity entity = data.aoes.get(i);
            var target = new CompoundTag();
            target.putInt("entity", entity.getId());
            putVector(target, "x", "y", "z", entity.position().add(0, entity.getEyeHeight(), 0));
            aoes.add(target);
        }
        tag.put("aoes", aoes);
        // Include nearby observers as the existing modern effect adapters do.
        var position = player.position();
        PacketDistributor.sendToPlayersNear(player.serverLevel(), null, position.x, position.y,
                position.z, 50, new AcademyNetwork.ClientData(tag));
    }

    private static void putVector(CompoundTag tag, String x, String y, String z, Vec3 vector) {
        tag.putDouble(x, vector.x); tag.putDouble(y, vector.y); tag.putDouble(z, vector.z);
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    public record AttackData(Entity target, List<Entity> aoes, Vec3 point) {}
}

package cn.academy.biohum;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

/** The data-authored actions use the same target, cooldown, and synchronization paths as the earlier cast. */
public record RosterMove(String id, String label, Kind kind, Pose pose, Visual visual, int color,
                         double range, float power, int duration, int windup, int recovery, int cooldown,
                         Rule rule, String interaction, String reply) implements NpcMove {
    public enum Kind {
        STRIKE, DRIVE, SWEEP, BOLT, COPY, SLOW, RETREAT, INTERRUPT, MARK,
        HEAL, REGEN, GUARD, SPEED, CLEANSE, DASH
    }

    public enum Pose { THRUST, SWORD, CAST, RITUAL, BRACE, STEP, POINT, AID, CALL, THROW }
    public enum Visual { ARC, BEAM, RING, CORNERS, SIGIL, FLASH, TRAIL, HEART, WALL, SPARK, MIST }
    public enum Rule { ANY, NPC, PHYSICAL, ARCANE, FEINT, HOSTILE, SELF, STEADY }

    public static final ResourceKey<DamageType> FEINT = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(BioHum.ID, "hound_feint"));
    public static final Set<String> ELECTRIC_SOURCES = Set.of("mikoto", "misaka_10032", "misaka_worst");
    private static final Map<String, RosterMove> ACTIONS = load();

    private static Map<String, RosterMove> load() {
        try (var in = RosterMove.class.getResourceAsStream("/assets/acabiohum/roster_moves.json")) {
            if (in == null) throw new IOException("Missing roster moves");
            var moves = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), RosterMove[].class);
            Map<String, RosterMove> byId = new LinkedHashMap<>();
            for (var move : moves) {
                if (move.kind == null || move.pose == null || move.visual == null || move.rule == null
                        || move.range <= 0 || move.windup < 5 || move.recovery < 5
                        || move.cooldown <= move.windup + move.recovery || byId.putIfAbsent(move.id, move) != null) {
                    throw new IOException("Invalid roster move " + move.id);
                }
            }
            return Map.copyOf(byId);
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    static RosterMove find(String id) {
        return ACTIONS.get(id);
    }

    public boolean requiresTarget() {
        return switch (kind) {
            case HEAL, REGEN, GUARD, SPEED, CLEANSE, DASH -> false;
            default -> true;
        };
    }

    private boolean validTarget(HumNpc npc, LivingEntity target) {
        if (!Abilities.target(npc, target, range) || !npc.validTarget(target)) return false;
        return switch (rule) {
            case NPC -> target instanceof HumNpc other
                    && !Set.of("touma", "accelerator", "othinus").contains(other.characterId);
            case HOSTILE -> target instanceof Monster || target == npc.getLastHurtByMob()
                    || npc.owner() != null && target == npc.owner().getLastHurtByMob();
            default -> true;
        };
    }

    private boolean canApply(HumNpc npc, LivingEntity target) {
        if (rule == Rule.STEADY && npc.getHealth() <= npc.getMaxHealth() * .35f) return false;
        if (requiresTarget() && !validTarget(npc, target)) return false;
        if (kind == Kind.COPY && npc.level().getEntitiesOfClass(HumNpc.class, npc.getBoundingBox().inflate(8),
                source -> source.isAlive() && ELECTRIC_SOURCES.contains(source.characterId)).isEmpty()) return false;
        return kind != Kind.DASH || landing(npc) != null;
    }

    @Override
    public boolean start(HumNpc npc, LivingEntity target) {
        if (!requiresTarget()) target = null;
        if (!canApply(npc, target) || !npc.windup(id, target, windup)) return false;
        ServerLevel level = (ServerLevel) npc.level();
        npc.setReady(id, level.getGameTime() + cooldown);
        npc.expression(requiresTarget() ? 2 : 1);
        npc.getNavigation().stop();
        if (target != null) npc.getLookControl().setLookAt(target, 45, 45);
        npc.beginSignature(this);
        SignatureEffects.ring(level, npc.position().add(0, .10, 0), .46 * npc.profile().scale(), color, 9);
        return true;
    }

    @Override
    public boolean finish(HumNpc npc, LivingEntity target) {
        if (!canApply(npc, target)) return false;
        var level = (ServerLevel) npc.level();
        if (!requiresTarget()) return applySupport(npc, level);
        Vec3 center = target.getBoundingBox().getCenter();
        switch (kind) {
            case STRIKE -> {
                if (hit(npc, target, power)) slow(target, npc, 0);
            }
            case DRIVE -> {
                if (hit(npc, target, power)) push(npc, target, .58 + power * .035);
            }
            case SWEEP -> {
                double radius = .9 + range / 8;
                for (var victim : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(radius),
                        t -> t.distanceToSqr(target) <= radius * radius && validTarget(npc, t))) {
                    if (hit(npc, victim, victim == target ? power : power * .50f)) {
                        slow(victim, npc, 0);
                    }
                }
            }
            case BOLT, COPY -> {
                String projectile = kind == Kind.COPY ? "electric" : rule == Rule.PHYSICAL ? "physical" : "magic";
                Abilities.bolt(npc, target, projectile, power, 1.25 + power * .025);
            }
            case SLOW -> slow(target, npc, (int) power);
            case RETREAT -> {
                retreat(npc, target);
                slow(target, npc, 0);
            }
            case INTERRUPT -> {
                if (target instanceof HumNpc caster) {
                    caster.interrupt();
                    String primary = caster.skills().getFirst();
                    caster.setReady(primary, Math.max(caster.readyAt(primary), level.getGameTime() + duration / 2));
                }
                slow(target, npc, 1);
            }
            case MARK -> {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0), npc);
                var player = npc.audience();
                if (player != null) {
                    npc.tell(player, target.getName().getString() + " · " + Math.round(npc.distanceTo(target)) + " 格");
                }
            }
            default -> throw new IllegalStateException("Expected a target action: " + id);
        }
        releaseEffect(level, npc.rightHand(), center, target.position());
        return true;
    }

    private boolean applySupport(HumNpc npc, ServerLevel level) {
        if (kind == Kind.DASH) {
            Vec3 from = npc.position();
            Vec3 to = landing(npc);
            if (to == null || !Abilities.teleport(npc, to)) return false;
            releaseEffect(level, from.add(0, .2, 0), to.add(0, .2, 0), to);
            return true;
        }
        List<LivingEntity> team = rule == Rule.SELF ? List.of(npc) : level.getEntitiesOfClass(
                LivingEntity.class, npc.getBoundingBox().inflate(range), other -> other.isAlive()
                        && (npc.ally(other) || other == npc.audience()) && npc.distanceToSqr(other) <= range * range);
        for (LivingEntity ally : team) {
            switch (kind) {
                case HEAL -> {
                    ally.heal(power);
                    ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, 0), npc);
                }
                case REGEN -> ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, (int) power), npc);
                case GUARD -> ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, (int) power), npc);
                case SPEED -> ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 0), npc);
                case CLEANSE -> ally.getActiveEffects().stream().map(MobEffectInstance::getEffect)
                        .filter(effect -> effect.value().getCategory() == MobEffectCategory.HARMFUL).toList()
                        .forEach(ally::removeEffect);
                default -> throw new IllegalStateException("Expected a support action: " + id);
            }
            releaseEffect(level, npc.rightHand(), ally.getBoundingBox().getCenter(), ally.position());
        }
        return true;
    }

    private Vec3 landing(HumNpc npc) {
        Vec3 front = Vec3.directionFromRotation(0, npc.getYRot()).scale(power);
        Vec3 destination = npc.position().add(front);
        return Abilities.safeDestination(npc, destination) ? destination : null;
    }

    private boolean hit(HumNpc npc, LivingEntity target, float amount) {
        DamageSource damage;
        if (rule == Rule.FEINT) {
            var types = npc.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            damage = new DamageSource(types.getHolderOrThrow(FEINT), npc);
        } else {
            damage = rule == Rule.ARCANE ? npc.damageSources().indirectMagic(null, npc) : npc.damageSources().mobAttack(npc);
        }
        return target.hurt(damage, amount);
    }

    private void slow(LivingEntity target, HumNpc npc, int amplifier) {
        if (duration > 0) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, amplifier), npc);
    }

    private void push(HumNpc npc, LivingEntity target, double speed) {
        Vec3 direction = target.position().subtract(npc.position()).multiply(1, 0, 1).normalize();
        target.push(direction.x * speed, .17, direction.z * speed);
    }

    private void retreat(HumNpc npc, LivingEntity target) {
        Vec3 direction = target.position().subtract(npc.position()).multiply(1, 0, 1).normalize();
        Vec3 offset = direction.scale(power);
        Vec3 destination = target.position().add(offset);
        if (target.level().noCollision(target, target.getBoundingBox().expandTowards(offset))) {
            Abilities.teleport(target, destination);
        }
    }

    @Override
    public void interact(HumNpc npc, ServerPlayer player) {
        LivingEntity target = requiresTarget() ? Abilities.aimed(npc, player) : null;
        String status = target != null && validTarget(npc, target)
                ? " " + target.getName().getString() + "距我 " + Math.round(npc.distanceTo(target)) + " 格。"
                : "";
        npc.expression(1);
        npc.tell(player, reply + status);
    }

    @Override
    public void defend(HumNpc npc, ServerLevel level) {
        if ((requiresTarget() && kind != Kind.MARK) || kind == Kind.DASH || npc.isNoAi() || npc.mode() != 3
                || npc.owner() == null || level.getGameTime() % 10 != 0) return;
        LivingEntity threat = npc.owner().getLastHurtByMob();
        if (threat != null && npc.owner().getLastHurtByMobTimestamp() + 90 > npc.owner().tickCount
                && Abilities.target(npc, threat, 16)) {
            Abilities.cast(npc, 0, threat);
        }
    }

    private void releaseEffect(ServerLevel level, Vec3 hand, Vec3 center, Vec3 feet) {
        switch (visual) {
            case ARC -> SignatureEffects.arc(level, hand, center, color, .24);
            case BEAM -> SignatureEffects.beam(level, hand, center, color);
            case RING -> SignatureEffects.ring(level, feet.add(0, .12, 0), 1.33, color, 24);
            case CORNERS -> SignatureEffects.corners(level, feet, color);
            case SIGIL -> {
                SignatureEffects.ring(level, feet.add(0, .10, 0), 1.14, color, 24);
                SignatureEffects.ring(level, feet.add(0, .12, 0), .70, color, 12);
            }
            case FLASH -> {
                SignatureEffects.line(level, hand, center, color, 1.4f, 12);
                Abilities.particles(level, center, ParticleTypes.FLASH, 2);
            }
            case TRAIL -> SignatureEffects.line(level, hand, center, color, .9f, 14);
            case HEART -> {
                SignatureEffects.ring(level, feet.add(0, .14, 0), .52, color, 14);
                Abilities.particles(level, center.add(0, .45, 0), ParticleTypes.HEART, 3);
            }
            case WALL -> {
                SignatureEffects.corners(level, feet, color);
                SignatureEffects.ring(level, center, .80, color, 12);
            }
            case SPARK -> {
                SignatureEffects.arc(level, hand, center, color, .16);
                Abilities.particles(level, center, ParticleTypes.CRIT, 6);
            }
            case MIST -> {
                SignatureEffects.ring(level, feet.add(0, .30, 0), .87, color, 18);
                Abilities.particles(level, center, ParticleTypes.CLOUD, 14);
            }
        }
    }
}

package cn.academy.port.api;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

/** A read-only account of one native invocation; no balance is persisted by the observer. */
@EventBusSubscriber(modid = "academy")
public record CombatReceipt(
        long sequence,
        UUID target,
        ResourceKey<Level> dimension,
        DamageSource source,
        boolean admitted,
        boolean canceled,
        float before,
        float after,
        float healthSpent,
        float absorptionSpent,
        BigDecimal temporarySpent,
        BigDecimal dnaSpent,
        BigDecimal baseSpent,
        BigDecimal targetShieldSpent,
        String phaseBefore,
        String phaseAfter,
        boolean totem,
        boolean rescued,
        boolean deathAccepted,
        boolean killed) {
    private static long nextSequence;
    private static final ThreadLocal<Observation> ACTIVE = new ThreadLocal<>();
    private static final Map<LivingEntity, Pending> DEATHS = new IdentityHashMap<>();

    private record Pending(
            ServerLevel world,
            DamageSource source,
            CombatDefense defense,
            long until,
            Consumer<LivingEntity> confirmed) {
        float health(LivingEntity entity) {
            var reading = defense == null ? null : defense.observe(entity);
            return reading == null ? entity.getHealth() : reading.health();
        }
    }

    private static final class Observation {
        final LivingEntity target;
        final DamageSource source;
        final CombatDefense defense;
        float absorption, otherHealth;
        int nested;
        BigDecimal temporary = BigDecimal.ZERO,
                dna = BigDecimal.ZERO,
                base = BigDecimal.ZERO,
                otherShield = BigDecimal.ZERO;
        boolean canceled, totem, rescued, death;

        Observation(LivingEntity target, DamageSource source, CombatDefense defense) {
            this.target = target;
            this.source = source;
            this.defense = defense;
        }

        CombatDefense.Pools pools() {
            return defense == null ? null : defense.observe(target);
        }
    }

    static CombatReceipt observe(
            LivingEntity target, DamageSource source, java.util.function.BooleanSupplier attack) {
        CombatDefense defense = target.getCapability(CombatDefense.CAPABILITY);
        var initial = defense == null ? null : defense.observe(target);
        float before = initial == null ? target.getHealth() : initial.health();
        var parent = ACTIVE.get();
        var result = new Observation(target, source, defense);
        ACTIVE.set(result);
        try {
            boolean admitted = attack.getAsBoolean();
            var ending = defense == null ? null : defense.observe(target);
            float after = ending == null ? target.getHealth() : ending.health();
            BigDecimal privateShield =
                    initial == null || ending == null
                            ? BigDecimal.ZERO
                            : initial.shield()
                                    .subtract(ending.shield())
                                    .subtract(result.otherShield)
                                    .max(BigDecimal.ZERO);
            return new CombatReceipt(
                    ++nextSequence,
                    target.getUUID(),
                    target.level().dimension(),
                    source,
                    admitted,
                    result.canceled,
                    before,
                    after,
                    Math.max(0F, before - after - result.otherHealth),
                    result.absorption,
                    result.temporary,
                    result.dna,
                    result.base,
                    privateShield,
                    initial == null ? "" : initial.phase(),
                    ending == null ? "" : ending.phase(),
                    result.totem,
                    result.rescued,
                    result.death,
                    target.isRemoved()
                            && target.getRemovalReason() == Entity.RemovalReason.KILLED
                            && result.death);
        } finally {
            if (parent == null) ACTIVE.remove();
            else ACTIVE.set(parent);
        }
    }

    public boolean affected() {
        return healthSpent > 0
                || absorptionSpent > 0
                || temporarySpent.signum() > 0
                || dnaSpent.signum() > 0
                || baseSpent.signum() > 0
                || targetShieldSpent.signum() > 0
                || totem
                || rescued
                || killed;
    }

    /** Exclude nested third-party hits on the same target from this strike's attributed HP. */
    public static Runnable enterDamage(LivingEntity who, DamageSource source) {
        var entry = ACTIVE.get();
        if (entry == null || entry.target != who || entry.source == source) return null;
        if (entry.nested++ > 0) return () -> entry.nested--;
        var before = entry.pools();
        float start = before == null ? who.getHealth() : before.health();
        return () -> {
            entry.nested--;
            var after = entry.pools();
            entry.otherHealth += start - (after == null ? who.getHealth() : after.health());
            if (before != null && after != null)
                entry.otherShield = entry.otherShield.add(before.shield().subtract(after.shield()));
        };
    }

    private static Observation matching(LivingEntity who, DamageSource source) {
        var entry = ACTIVE.get();
        return entry != null && entry.target == who && entry.source == source ? entry : null;
    }

    public static void canceled(LivingEntity target, DamageSource source) {
        var hit = matching(target, source);
        if (hit != null) hit.canceled = true;
    }

    public static void nativePost(LivingEntity who, DamageContainer container) {
        var entry = matching(who, container.getSource());
        if (entry != null)
            entry.absorption += container.getReduction(DamageContainer.Reduction.ABSORPTION);
    }

    public static void shields(
            LivingEntity who,
            DamageSource source,
            BigDecimal temporary,
            BigDecimal dna,
            BigDecimal base) {
        var entry = matching(who, source);
        if (entry != null) {
            entry.temporary = entry.temporary.add(temporary);
            entry.dna = entry.dna.add(dna);
            entry.base = entry.base.add(base);
        }
    }

    public static void usedTotem(LivingEntity who, DamageSource source) {
        if (!(who.level() instanceof ServerLevel)) return;
        var entry = matching(who, source);
        if (entry != null) entry.totem = true;
        DEATHS.remove(who);
    }

    public static void deathEvent(LivingEntity who, DamageSource source, boolean canceled) {
        if (!(who.level() instanceof ServerLevel)) return;
        var entry = matching(who, source);
        if (entry != null) {
            entry.rescued = canceled;
            entry.death = !canceled;
        }
        var pending = DEATHS.get(who);
        if (pending != null && (canceled || pending.source != source)) DEATHS.remove(who);
    }

    /**
     * Confirm the removed instance, including its reason; unloads, recovery and later lethal
     * sources retire the watch.
     */
    public void whenKilled(LivingEntity body, Consumer<LivingEntity> confirmed) {
        if (body.getUUID().equals(target)
                && body.level() instanceof ServerLevel level
                && level.dimension().equals(dimension)
                && after <= 0
                && !rescued
                && !totem
                && deathAccepted
                && !killed
                && !body.isRemoved()) {
            DEATHS.put(
                    body,
                    new Pending(
                            level,
                            source,
                            body.getCapability(CombatDefense.CAPABILITY),
                            level.getGameTime() + 600,
                            confirmed));
        }
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (DEATHS.isEmpty()) return;
        var it = DEATHS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            var body = entry.getKey();
            var pending = entry.getValue();
            if (event.getLevel() != pending.world) continue;
            if (body.isRemoved()) {
                it.remove();
                if (body.level() == pending.world
                        && body.getRemovalReason() == Entity.RemovalReason.KILLED
                        && pending.health(body) <= 0) pending.confirmed.accept(body);
            } else if (body.level() != pending.world
                    || pending.health(body) > 0
                    || pending.world.getGameTime() >= pending.until
                    || pending.world.getEntity(body.getUUID()) != body) it.remove();
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        DEATHS.clear();
    }
}

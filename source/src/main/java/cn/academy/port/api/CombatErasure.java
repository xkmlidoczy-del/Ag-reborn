package cn.academy.port.api;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Deliberate removal of a selected life, distinct from damage and its rescue mechanics. */
public final class CombatErasure {
    // Entity.equals uses its network ID, which a freshly respawned player inherits.
    private static final Map<Entity, WeakReference<Entity>> ERASED = new WeakHashMap<>();
    private record Execution(ServerPlayer player, DamageSource source) {}
    private static final ThreadLocal<Execution> ACTIVE = new ThreadLocal<>();

    private CombatErasure() {}

    public static boolean erase(ServerPlayer caster, LivingEntity target) {
        if (!caster.server.isSameThread() || target.level() != caster.level() || target == caster
                || !target.isAlive() || target.isRemoved() || target.isSpectator()) return false;
        if (!(target instanceof ServerPlayer player)) {
            ERASED.put(target, new WeakReference<>(target));
            target.setRemoved(Entity.RemovalReason.DISCARDED);
            return target.isRemoved();
        }
        if (player.isCreative() || !caster.server.isPvpAllowed() || !caster.canHarmPlayer(player)
                || !cn.academy.port.AcademyConfig.ATTACK_PLAYERS.get()
                || awaitingRespawn(player) || ACTIVE.get() != null) return false;
        var source = new DamageSource(caster.damageSources().fellOutOfWorld().typeHolder(), caster) {
            @Override public Component getLocalizedDeathMessage(LivingEntity victim) {
                return Component.translatable("death.attack.academy.erasure", victim.getDisplayName(), caster.getDisplayName());
            }
        };
        ACTIVE.set(new Execution(player, source));
        ERASED.put(player, new WeakReference<>(player));
        try {
            player.getCombatTracker().recordDamage(source, player.getHealth());
            player.setLastHurtByMob(caster);
            player.setHealth(0F);
            player.die(source);
            return player.isDeadOrDying();
        } finally {
            ACTIVE.remove();
            if (!player.isDeadOrDying()) ERASED.remove(player);
        }
    }

    public static boolean committing(LivingEntity target, DamageSource source) {
        var execution = ACTIVE.get();
        return execution != null && execution.player == target && execution.source == source;
    }

    public static boolean erasedBody(Entity target) {
        return !(target instanceof ServerPlayer) && marked(target);
    }

    public static boolean awaitingRespawn(LivingEntity target) {
        return target instanceof ServerPlayer && marked(target);
    }

    private static boolean marked(Entity target) {
        if (!(target.level() instanceof ServerLevel world) || !world.getServer().isSameThread()) return false;
        var active = ERASED.get(target);
        return active != null && active.get() == target;
    }
}

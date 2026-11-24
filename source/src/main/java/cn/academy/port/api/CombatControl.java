package cn.academy.port.api;

import cn.academy.port.highpower.PlayerShieldArmor;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** World-time control lease for entities whose behavior runs outside ordinary AI goals. */
@EventBusSubscriber(modid = "academy")
public final class CombatControl {
    private static final Map<LivingEntity, Long> HELD = new WeakHashMap<>();
    private static final Set<String> AI_STEPS = Set.of("tick", "baseTick", "aiStep", "customServerAiStep");
    private static final ClassValue<Boolean> CUSTOM_AI = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try {
                for (Class<?> owner = type; owner != null && !owner.getName().startsWith("net.minecraft."); owner = owner.getSuperclass()) {
                    for (var method : owner.getDeclaredMethods())
                        if (method.getParameterCount() == 0 && AI_STEPS.contains(method.getName())) return true;
                }
            } catch (LinkageError unavailable) {
                return true;
            }
            return false;
        }
    };

    private CombatControl() {}

    /** A commanded body with its own tick needs the controller to drive movement and attacks. */
    public static boolean needsDriver(LivingEntity body) {
        return CUSTOM_AI.get(body.getClass());
    }

    public static boolean canAffect(ServerPlayer caster, Entity target) {
        if (target == caster || target.level() != caster.level() || !target.isAlive() || target.isSpectator()
                || PlayerShieldArmor.ignoresControl(target) || caster.isAlliedTo(target) || target.isAlliedTo(caster))
            return false;
        return !(target instanceof ServerPlayer player) || !player.isCreative()
                && caster.server.isPvpAllowed() && caster.canHarmPlayer(player);
    }

    public static boolean hold(LivingEntity target, long expiry) {
        if (!(target.level() instanceof ServerLevel world) || !world.getServer().isSameThread()
                || !target.isAlive() || expiry <= world.getGameTime() || PlayerShieldArmor.ignoresControl(target))
            return false;
        if (target.isPassenger()) target.stopRiding();
        HELD.merge(target, expiry, Math::max);
        return true;
    }

    public static boolean held(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel world) || !world.getServer().isSameThread() || HELD.isEmpty())
            return false;
        Long until = HELD.get(target);
        if (until == null) return false;
        if (until <= world.getGameTime() || !target.isAlive()
                || target instanceof ServerPlayer player && (player.isCreative() || PlayerShieldArmor.ignoresControl(player))) {
            HELD.remove(target);
            return false;
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pause(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !held(target)) return;
        target.setDeltaMovement(Vec3.ZERO);
        if (!(target instanceof ServerPlayer)) event.setCanceled(true);
    }

    public static boolean rejectsAttack(DamageSource source) {
        return source.getDirectEntity() instanceof LivingEntity actor && held(actor)
                || source.getEntity() instanceof ServerPlayer owner && held(owner);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void silence(LivingIncomingDamageEvent event) {
        if (rejectsAttack(event.getSource())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel) HELD.remove(event.getEntity());
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        HELD.clear();
    }
}

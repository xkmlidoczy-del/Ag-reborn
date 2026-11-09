package cn.academy.port.highpower;

import cn.academy.port.highpower.core.EsperDamageRules;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Erasure defence for enrolled espers. Damage of every kind is accounted by the ledger; this refuses only what is not damage:
 * the kill command, invalid amounts, and any death or native-health collapse that the exact ledger did not cause. Runs just after the
 * combat code's own first look at the hit so its bookkeeping sees a normal cancelled hit; exact skill damage is never touched.
 */
@EventBusSubscriber(modid = "academy")
public final class EsperDamageGuard {
    private EsperDamageGuard() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void guard(LivingIncomingDamageEvent event) {
        if (EsperLegacyBypass.standAside() || !(event.getEntity() instanceof ServerPlayer player) || !player.server.isSameThread() || event.isCanceled() || !HighPowerCombat.enabled(player)) return;
        var source = event.getSource();
        String type = source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("");
        var decision = EsperDamageRules.decide(EsperProtectionConfig.policy(), type, event.getAmount(), HighPowerCombat.isExactSkillSource(source));
        if (decision != EsperDamageRules.Decision.ALLOW) { event.setCanceled(true); return; }
        // An infinite hit becomes the largest finite one, so armour arithmetic cannot turn it into NaN (and thus into an ignored hit).
        if (Float.isInfinite(event.getAmount()) && event.getAmount() > 0) event.setAmount(Float.MAX_VALUE);
        if (EsperDamageRules.environmental(type) && !HighPowerCombat.isExactSkillSource(source)) {
            try {
                double scaled = EsperDamageRules.environmentalAmount(EsperProtectionConfig.policy(), type, event.getAmount(), HighPowerGrowth.values(player).damage());
                if (scaled != event.getAmount() && Float.isFinite((float) scaled)) event.setAmount((float) scaled);
            } catch (IllegalStateException | IllegalArgumentException unavailable) { /* no admitted growth: leave the native amount */ }
        }
    }

    /** A death while the ledger still has health is an erasure attempt (direct die(), forced setHealth(0)); the ledger is authoritative. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void death(LivingDeathEvent event) {
        if (EsperLegacyBypass.standAside() || !(event.getEntity() instanceof ServerPlayer player) || !player.server.isSameThread() || event.isCanceled()) return;
        var ledger = HighPowerCombat.exactState(player);
        if (EsperDamageRules.forcedDeath(EsperProtectionConfig.policy(), ledger.isPresent(), ledger.map(state -> state.isDead()).orElse(true))) {
            event.setCanceled(true);
            HighPowerCombat.resynchronizeExact(player);
        }
    }

    /** Native health forced to zero (or the death animation started) behind a living ledger is repaired before vanilla can act on it. */
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Pre event) {
        if (EsperLegacyBypass.standAside() || !(event.getEntity() instanceof ServerPlayer player) || !player.server.isSameThread() || !EsperProtectionConfig.policy().enabled()) return;
        if (player.getHealth() > 0 && !player.isDeadOrDying()) return;
        var ledger = HighPowerCombat.exactState(player);
        if (EsperDamageRules.forcedDeath(EsperProtectionConfig.policy(), ledger.isPresent(), ledger.map(state -> state.isDead()).orElse(true))) {
            player.deathTime = 0;
            HighPowerCombat.resynchronizeExact(player);
        }
    }
}

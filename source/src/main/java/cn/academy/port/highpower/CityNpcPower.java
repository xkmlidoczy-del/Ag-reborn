package cn.academy.port.highpower;

import cn.academy.port.city.CityEspers;
import cn.academy.port.city.CityNpcEntity;
import cn.academy.port.highpower.core.ExactCombatState;
import java.math.BigDecimal;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

/** Numeric adapter for real city espers. Consent is separate from numeric enrollment. */
@EventBusSubscriber(modid="academy")
public final class CityNpcPower {
    private CityNpcPower() {}
    public static boolean active(LivingEntity entity) {
        return (entity instanceof CityNpcEntity npc && npc.esper()!=CityEspers.Profile.NONE||entity instanceof cn.academy.port.city.defense.GuardianEntity) && HighPowerCombat.enabled(entity);
    }
    public static boolean baseShieldEnabled() { return !EsperLegacyBypass.standAside() && EsperProtectionConfig.categoryShields(); }
    public static boolean holding(LivingEntity entity) { return active(entity) && baseShieldEnabled() && HighPowerCombat.shield(entity).signum()>0; }
    public static BigDecimal health(LivingEntity entity) {
        return HighPowerCombat.exactState(entity).map(ExactCombatState::health).orElseGet(()->BigDecimal.valueOf(entity.getHealth()));
    }
    public static BigDecimal maxHealth(LivingEntity entity) {
        return HighPowerCombat.exactState(entity).map(ExactCombatState::maxHealth).orElseGet(()->BigDecimal.valueOf(entity.getMaxHealth()));
    }
    public static BigDecimal shield(LivingEntity entity) { return HighPowerCombat.shield(entity); }
    static ExactCombatState nonlethal(CityNpcEntity npc, ExactCombatState next) {
        if (!npc.ambient() && !npc.combat().dueling()) return next;
        BigDecimal floor=npc.combat().dueling()?next.maxHealth().multiply(new BigDecimal("0.15")).max(BigDecimal.ONE):BigDecimal.ONE;
        return next.health().compareTo(floor)>=0?next:new ExactCombatState(floor,next.maxHealth(),next.shield(),next.maxShield(),false);
    }
    /** Conservative pre-armor stop for an NPC's consensual hit on an enrolled player. */
    public static double sparringDamage(LivingEntity target, double amount) {
        return HighPowerCombat.exactState(target).map(s->BigDecimal.valueOf(amount).min(s.shield().add(
                s.health().subtract(s.maxHealth().multiply(new BigDecimal("0.15"))).max(BigDecimal.ZERO))).doubleValue()).orElse(amount);
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void knockback(LivingKnockBackEvent event) {
        if (!event.isCanceled() && holding(event.getEntity())) event.setCanceled(true);
    }
}

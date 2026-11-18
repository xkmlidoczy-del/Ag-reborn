package cn.academy.port.highpower;

import cn.academy.port.highpower.core.ExactCombatState;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Optional diagnostics for actual committed skill damage. */
@EventBusSubscriber(modid = "academy")
public final class DamageAudit {
    private static final boolean ENABLED = Boolean.getBoolean("academy.damageAudit");
    private static final ThreadLocal<Attempt> CURRENT = new ThreadLocal<>();
    private DamageAudit() {}

    public static final class Attempt {
        private final Attempt parent;
        private final LivingEntity target;
        private final DamageSource source;
        private final BigDecimal hp, shield;
        private final float absorption;
        private LivingIncomingDamageEvent incoming;

        private Attempt(LivingEntity target, DamageSource source) {
            this.parent = CURRENT.get();
            this.target = target;
            this.source = source;
            this.hp = CityNpcPower.health(target);
            this.shield = CityNpcPower.shield(target);
            this.absorption = target.getAbsorptionAmount();
        }
    }

    public static Attempt begin(LivingEntity target, DamageSource source) {
        if (!ENABLED || target.level().isClientSide()) return null;
        var attempt = new Attempt(target, source);
        CURRENT.set(attempt);
        return attempt;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void observe(LivingIncomingDamageEvent event) {
        if (!ENABLED) return;
        var a = CURRENT.get();
        if (a != null && a.target == event.getEntity() && a.source == event.getSource()) a.incoming = event;
    }

    public static void finish(Attempt a, String skill, Number raw, Number scaled, boolean accepted, String reason) {
        if (a == null) return;
        if (a.parent == null) CURRENT.remove();
        else CURRENT.set(a.parent);
        var hpTaken = a.hp.subtract(CityNpcPower.health(a.target)).max(BigDecimal.ZERO);
        var shieldTaken = a.shield.subtract(CityNpcPower.shield(a.target)).max(BigDecimal.ZERO);
        var absorptionTaken = Math.max(0, a.absorption - a.target.getAbsorptionAmount());
        String result = reason;
        if (result == null) {
            if (a.incoming == null) result = "before_native_event";
            else if (a.incoming.isCanceled()) result = "incoming_cancelled";
            else if (!accepted) result = "native_refused";
            else result = hpTaken.signum() + shieldTaken.signum() + absorptionTaken > 0 ? "damage" : "zero_delta";
        }
        LogUtils.getLogger().info("ACA_DAMAGE skill={} target={} raw={} scaled={} shield={} hp={} absorption={} result={}",
                skill, a.target.getType().toShortString(), raw, scaled, shieldTaken, hpTaken, absorptionTaken, result);
    }

    static void nativeLedger(LivingDamageEvent.Post event, ExactCombatState before, ExactCombatState after) {
        if (!ENABLED) return;
        LogUtils.getLogger().info("ACA_NATIVE_DAMAGE source={} target={} nativeDamage={} shield={} hp={}",
                event.getSource().getMsgId(), event.getEntity().getType().toShortString(), event.getNewDamage(),
                before.shield().subtract(after.shield()), before.health().subtract(after.health()));
    }

    public static boolean refused(String skill, Number raw, String reason) {
        if (ENABLED) LogUtils.getLogger().info("ACA_DAMAGE skill={} raw={} result={}", skill, raw, reason);
        return false;
    }
}

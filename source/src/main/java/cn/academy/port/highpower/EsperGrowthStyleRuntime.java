package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillExpAddedEvent;
import cn.academy.port.highpower.core.EsperGrowthStyle;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Applies {@link EsperGrowthStyle}. An award is never mutated in place: the bonus is queued and granted on the player's next tick,
 * outside the award that triggered it, so the closed native earned-XP receipt of the original award stays valid.
 */
@EventBusSubscriber(modid = "academy")
public final class EsperGrowthStyleRuntime {
    private static final Map<ServerPlayer, Memory> MEMORY = new WeakHashMap<>();
    private static boolean granting;
    private EsperGrowthStyleRuntime() {}

    private static final class Memory {
        final EsperGrowthStyle.Tracker tracker = new EsperGrowthStyle.Tracker();
        Vec3 last; double speed; String skill; double pending;
        HighPowerRecoveryHudModel.GrowthHint sent; long sentAt = Long.MIN_VALUE, checked = Long.MIN_VALUE;
    }

    @SubscribeEvent
    public static void awarded(SkillExpAddedEvent event) {
        if (granting || EsperLegacyBypass.standAside() || !(event.getEntity() instanceof ServerPlayer player) || !player.server.isSameThread() || !EsperGrowthStyleConfig.enabled()
                || !HighPowerCombat.enabled(player) || !Float.isFinite(event.amount) || event.amount <= 0) return;
        var state = AbilityStorage.get(player);
        if (!HighPowerCategories.active(state) || !event.category.equals(state.category)) return;
        var memory = MEMORY.computeIfAbsent(player, p -> new Memory());
        double maximum = state.maxOverload(), overload = maximum > 0 ? state.overload / maximum : 0;
        double bonus = EsperGrowthStyle.bonus(memory.tracker, state.category, event.skill, player.serverLevel().getGameTime(), overload, memory.speed);
        if (bonus <= 0) return;
        memory.skill = event.skill; memory.pending += event.amount * bonus;
    }

    /** Hint cadence mirrors the recovery HUD: sent on change, refreshed every second, and silent when the style is not running. */
    private static void publishHint(ServerPlayer player, Memory memory) {
        long now = player.serverLevel().getGameTime();
        if (memory.checked != Long.MIN_VALUE && now >= memory.checked && now - memory.checked < HighPowerRecoveryHudModel.SEND_INTERVAL) return;
        memory.checked = now;
        if (EsperLegacyBypass.standAside() || !EsperGrowthStyleConfig.enabled() || !HighPowerCombat.enabled(player) || !EsperGrowthHintPayload.supported(player)) return;
        var state = AbilityStorage.get(player);
        if (!HighPowerCategories.active(state)) return;
        double maximum = state.maxOverload(), overload = maximum > 0 ? state.overload / maximum : 0;
        var hint = EsperGrowthStyle.hint(memory.tracker, state.category, now, overload, memory.speed);
        if (hint == null) return;
        var value = new HighPowerRecoveryHudModel.GrowthHint(hint.category(), hint.percent(), hint.count());
        if (value.equals(memory.sent) && now >= memory.sentAt && now - memory.sentAt < HighPowerRecoveryHudModel.KEEPALIVE_TICKS) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new EsperGrowthHintPayload.Hint(player.getUUID(), value));
        memory.sent = value; memory.sentAt = now;
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.server.isSameThread()) return;
        var memory = MEMORY.get(player);
        if (memory == null) { if (EsperGrowthStyleConfig.enabled() && HighPowerCombat.enabled(player)) MEMORY.put(player, memory = new Memory()); else return; }
        Vec3 at = player.position();
        double step = memory.last == null ? 0 : at.distanceTo(memory.last);
        memory.last = at;
        // A teleport is not flow: ignore jumps; smooth the rest so one lurch cannot max the style.
        memory.speed = step > 8 ? 0 : memory.speed * .8 + step * .2;
        publishHint(player, memory);
        if (memory.pending <= 0 || memory.skill == null) return;
        double extra = memory.pending; String skill = memory.skill; memory.pending = 0; memory.skill = null;
        var state = AbilityStorage.get(player);
        if (!HighPowerCombat.enabled(player) || !HighPowerCategories.active(state) || !Double.isFinite(extra) || extra <= 0) return;
        granting = true;
        try { state.addExperienceRaw(skill, extra); }
        catch (RuntimeException unavailable) { /* a category switch between award and grant simply forfeits the small bonus */ }
        finally { granting = false; }
    }
}

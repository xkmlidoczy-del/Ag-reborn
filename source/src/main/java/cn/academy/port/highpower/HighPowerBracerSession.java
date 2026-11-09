package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Held key context, separate from the prototype item's crouch/use gesture; both use one shield/CP pool. */
@EventBusSubscriber(modid="academy")
public final class HighPowerBracerSession {
    private record Hold(Object connection, ServerLevel world, AbilityProgress state, int preset,
                        long revision, HighPowerInput.Input input) {}
    private record Watermark(long epoch, long input) {}
    private static final Map<ServerPlayer, Hold> HOLDS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Watermark> INPUTS = new IdentityHashMap<>();
    private HighPowerBracerSession() {}

    public static boolean authenticated(ServerPlayer p, HighPowerInput.Input input) {
        if (p == null || input == null || p.connection == null || p.hasDisconnected() || p.isRemoved()
                || !p.isAlive() || p.isSpectator() || !p.server.isSameThread()
                || p.serverLevel().getEntity(p.getId()) != p || p.getId() != input.actor()) return false;
        return LegacySingleKeyOwnerEpoch.matches(p, p.connection, p.serverLevel(), AbilityStorage.get(p), input.epoch());
    }
    public static boolean start(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!authenticated(p, input)) return false;
        var previous = INPUTS.get(p);
        if (previous != null && previous.epoch == input.epoch() && input.input() <= previous.input) return false;
        INPUTS.put(p, new Watermark(input.epoch(), input.input()));
        var state = AbilityStorage.get(p);
        if (!HighPowerSkills.MAGNETIC_BRACER.equals(state.presets.currentSkill(input.slot()))
                || !HighPowerSkills.eligible(state, HighPowerSkills.MAGNETIC_BRACER)
                || !ElectromasterPrototype.ready(p) || HighPowerCombat.unsupportedReason(p).isPresent()) return false;
        HOLDS.put(p, new Hold(p.connection, p.serverLevel(), state, state.presets.current(), state.presets.revision(), input));
        return true;
    }
    /** Captured input identity survives a preset change; a late release cannot terminate a newer hold. */
    public static boolean stop(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!authenticated(p, input)) return false;
        var prior = INPUTS.get(p);
        if (prior == null || prior.epoch != input.epoch() || input.input() > prior.input)
            INPUTS.put(p, new Watermark(input.epoch(), input.input()));
        var hold = HOLDS.get(p);
        if (hold == null || !hold.input.equals(input)) return false;
        abort(p); return true;
    }
    public static boolean active(ServerPlayer p) {
        var hold = HOLDS.get(p);
        if (hold == null) return false;
        var state = AbilityStorage.get(p);
        if (!authenticated(p, hold.input) || hold.connection != p.connection || hold.world != p.serverLevel()
                || hold.state != state || hold.preset != state.presets.current() || hold.revision != state.presets.revision()
                || !HighPowerSkills.MAGNETIC_BRACER.equals(state.presets.currentSkill(hold.input.slot()))
                || !HighPowerSkills.eligible(state, HighPowerSkills.MAGNETIC_BRACER)
                || !ElectromasterPrototype.ready(p) || HighPowerCombat.unsupportedReason(p).isPresent()) {
            abort(p); return false;
        }
        return true;
    }
    public static void abort(ServerPlayer p) {
        if (HOLDS.remove(p) != null) HighPowerCombat.shieldActive(p, false);
    }
    public static void remove(ServerPlayer p) { abort(p); INPUTS.remove(p); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if (e.getOriginal() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { HOLDS.clear(); INPUTS.clear(); }
}

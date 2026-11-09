/* AcademyCraft 1.0.7 LightShield/LSContext adaptation. Copyright Lambda Innovation, GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Original global ContextManager.find behavior deliberately preserved, including cross-player absorption. */
@EventBusSubscriber(modid = "academy")
public final class LightShield {
    public static final String ID = LightShieldSession.ID;
    // Classic ServerManager.alive is a LinkedList and findAny uses a sequential stream.
    // Ordered first-live selection is a deterministic modern equivalent, not a source guarantee.
    private static final Map<UUID, Hold> HOLDS = new LinkedHashMap<>();
    private static final Map<UUID, Long> INPUTS = new HashMap<>();
    private static final Set<UUID> TICKING = new HashSet<>();
    private static long nextToken;
    private LightShield() {}
    public static boolean start(ServerPlayer player) {if(cn.academy.port.AbilityConsumption.busy(player))return false; return start(player, 0); }
    public static boolean start(ServerPlayer player, long inputNonce) {if(cn.academy.port.AbilityConsumption.busy(player))return false;
        if (!MeltdownerStarterSupport.serverThread(player) || !canStart(player) || inputNonce < 0
                || inputNonce > 0 && inputNonce <= INPUTS.getOrDefault(player.getUUID(), 0L)) return false;
        var old = HOLDS.get(player.getUUID());
        if (old != null) { if (identityValid(old)) return false; remove(player); }
        var session = LightShieldSession.begin(AbilityStorage.get(player), player.getAbilities().instabuild);
        if (session == null) return false;
        var hold = new Hold(player, player.serverLevel(), session, ++nextToken, inputNonce, MeltdownerStarterSupport.audience(player));
        HOLDS.put(player.getUUID(), hold); if (inputNonce > 0) INPUTS.put(player.getUUID(), inputNonce);
        send(hold, "light_shield_start"); if (session.ending()) finish(hold, true); return true;
    }
    public static void tick(ServerPlayer player) {
        if (!MeltdownerStarterSupport.serverThread(player)) return;
        var hold = HOLDS.get(player.getUUID()); if (hold == null) return;
        if (!identityValid(hold)) { remove(player); return; }
        var state = hold.session.state();
        if (!state.activated || !state.overloadFine || state.interfering) { finish(hold, true); return; }
        long now = player.serverLevel().getGameTime(); if (now <= hold.lastTick) return;
        hold.lastTick = now; TICKING.add(player.getUUID());
        try {
            if (hold.session.beginTick(player.getAbilities().instabuild)) {
                // Source WorldUtils uses a cube query followed by feet-distance <=3, not shield mesh collisions.
                var candidates = player.serverLevel().getEntities(player,
                        new AABB(player.position(), player.position()).inflate(3), entity -> entity.distanceToSqr(player) <= 9
                                && LightShieldSession.reachable(entity.getX() - player.getX(), entity.getZ() - player.getZ(), player.getYRot()));
                for (var target : candidates) {
                    int resistance = target instanceof LivingEntity living ? living.invulnerableTime : 0;
                    if (hold.session.touch(player.getAbilities().instabuild, resistance <= 0)) {
                        RadiationMarks.attack(player, "meltdowner." + ID, target, hold.session.touchDamage());
                        hold.session.completeTouch();
                    }
                }
            }
        } finally { TICKING.remove(player.getUUID()); }
        if (hold.session.ending()) finish(hold, true);
    }
    /** Classic LivingHurtEvent is pre-mitigation, mapped to NeoForge's incoming event. */
    @SubscribeEvent public static void hurt(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Player)
                || !event.getEntity().getServer().isSameThread()) return;
        Hold chosen = null;
        for (var hold : new ArrayList<>(HOLDS.values())) {
            if (hold.player.getServer() != event.getEntity().getServer()) continue;
            if (!identityValid(hold)) { remove(hold.player); continue; }
            if (chosen == null) chosen = hold;
        }
        if (chosen == null) return;
        // Source never verifies event.entityLiving == context.player. Null direct source still awards EXP.
        float amount = chosen.session.absorb(event.getAmount(), event.getSource().getDirectEntity() != null,
                chosen.player.getAbilities().instabuild);
        event.setAmount(amount); if (amount == 0) event.setCanceled(true);
        if (chosen.session.ending() && !TICKING.contains(chosen.player.getUUID())) finish(chosen, true);
        AbilityStorage.save(chosen.player); AcademyNetwork.sync(chosen.player);
    }
    public static boolean release(ServerPlayer player) {
        if (!MeltdownerStarterSupport.serverThread(player)) return false;
        var hold = HOLDS.get(player.getUUID()); if (hold == null) return false;
        if (!identityValid(hold)) { remove(player); return false; }
        finish(hold, true); return true;
    }
    public static void abort(ServerPlayer player) { release(player); }
    /** Lifecycle disposal has no stale-player potion/cooldown side effect. */
    public static void remove(ServerPlayer player) {
        if (!MeltdownerStarterSupport.serverThread(player)) return;
        var hold = HOLDS.get(player.getUUID()); if (hold != null) finish(hold, false);
        INPUTS.remove(player.getUUID()); TICKING.remove(player.getUUID());
    }
    public static boolean active(ServerPlayer player) { return player != null && HOLDS.containsKey(player.getUUID()); }
    public static int heldTicks(ServerPlayer player) { var hold = player == null ? null : HOLDS.get(player.getUUID()); return hold == null ? 0 : hold.session.ticks(); }
    public static void clear() { HOLDS.clear(); INPUTS.clear(); TICKING.clear(); nextToken = 0; }
    private static boolean canStart(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && LightShieldSession.mayStart(AbilityStorage.get(player)) && MeltdownerStarterSupport.finiteAim(player) && MeltdownerStarterSupport.finite(player.position());
    }
    private static boolean identityValid(Hold hold) {
        var player = hold.player; var state = AbilityStorage.get(player);
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && hold.level == player.serverLevel()
                && hold.session.state() == state && hold.session.active() && state.category.equals("meltdowner")
                && state.level >= LightShieldSession.LEVEL && state.learned(ID)
                && player.serverLevel().getGameTime() >= hold.lastTick && MeltdownerStarterSupport.finiteAim(player) && MeltdownerStarterSupport.finite(player.position());
    }
    private static void finish(Hold hold, boolean perform) {
        var player = hold.player; if (!HOLDS.remove(player.getUUID(), hold)) return;
        if (perform && hold.session.complete()) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, LightShieldSession.SLOW_TICKS, LightShieldSession.SLOW_AMPLIFIER));
            AbilityStorage.save(player); AcademyNetwork.sync(player);
        } else hold.session.discard();
        send(hold, "light_shield_end");
    }
    private static void send(Hold hold, String kind) {
        MeltdownerStarterSupport.send(hold.audience, MeltdownerStarterSupport.packet(hold.player, kind, hold.token, hold.input));
    }
    private static final class Hold {
        final ServerPlayer player; final ServerLevel level; final LightShieldSession session; final long token, input;
        final Set<ServerPlayer> audience; long lastTick;
        Hold(ServerPlayer player, ServerLevel level, LightShieldSession session, long token, long input, Set<ServerPlayer> audience) {
            this.player = player; this.level = level; this.session = session; this.token = token; this.input = input;
            this.audience = audience; lastTick = level.getGameTime() - 1;
        }
    }
}

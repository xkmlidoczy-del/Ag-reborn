/* AcademyCraft1.0.7 BodyIntensify adaptation. Copyright Lambda Innovation. GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Canonical ACTIVE charge-and-release skill. Never shuffle into buffs absent from executable source. */
public final class BodyIntensify {
    public static final String ID = BodyIntensifySession.ID;
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final Map<UUID, Long> CLIENT_INPUTS = new HashMap<>();
    private static final Set<UUID> COMMITTING = new HashSet<>();
    private static long nextToken;
    private BodyIntensify() {}
    public static boolean start(ServerPlayer player) {if(cn.academy.port.AbilityConsumption.busy(player))return false; return start(player, 0); }
    /** Client nonce is correlation only, never an aim/mastery/duration/resource authority. */
    public static boolean start(ServerPlayer player, long inputNonce) {if(cn.academy.port.AbilityConsumption.busy(player))return false;
        if (!serverThread(player) || !canAct(player) || COMMITTING.contains(player.getUUID()) || inputNonce < 0
                || inputNonce > 0 && inputNonce <= CLIENT_INPUTS.getOrDefault(player.getUUID(), 0L)) return false;
        Hold old = HOLDS.get(player.getUUID());
        if (old != null) { if (valid(player, old)) return false; abort(player); }
        BodyIntensifySession session = BodyIntensifySession.begin(AbilityStorage.get(player), player.getAbilities().instabuild);
        if (session == null) return false;
        Hold hold = new Hold(player.serverLevel(), session, ++nextToken, inputNonce, audience(player));
        HOLDS.put(player.getUUID(), hold);
        if (inputNonce > 0) CLIENT_INPUTS.put(player.getUUID(), inputNonce); send(player, hold, "body_intensify_start", false);
        if (!session.active()) abort(player);
        return true;
    }
    public static void tick(ServerPlayer player) {
        if (!serverThread(player)) return;
        Hold hold = HOLDS.get(player.getUUID());
        if (hold == null) return;
        if (!valid(player, hold)) { abort(player); return; }
        long now = player.serverLevel().getGameTime();
        if (now <= hold.lastTick) return;
        hold.lastTick = now;
        if (!hold.session.tick(player.getAbilities().instabuild)) abort(player);
    }
    public static boolean release(ServerPlayer player) { return player != null && release(player, player.serverLevel().random); }
    /** Trusted fixture seam only; network dispatch never accepts a client RNG or duration. */
    public static boolean release(ServerPlayer player, RandomSource random) {
        if (!serverThread(player) || random == null) return false;
        Hold hold = HOLDS.get(player.getUUID());
        if (hold == null) return false;
        if (!valid(player, hold)) { abort(player); return false; }
        HOLDS.remove(player.getUUID(), hold);
        if (!COMMITTING.add(player.getUUID())) { hold.session.end(); send(player, hold, "body_intensify_end", false); return false; }
        boolean performed = false;
        try {
            var release = hold.session.release(random::nextDouble);
            if (release == null) return true; // Accepted early release is source failure, not a transaction.
            for (var buff : release.buffs()) {
                var effect = switch (buff.effect()) {
                    case SPEED -> MobEffects.MOVEMENT_SPEED;
                    case JUMP -> MobEffects.JUMP;
                    case REGENERATION -> MobEffects.REGENERATION;
                    case STRENGTH -> MobEffects.DAMAGE_BOOST;
                    case RESISTANCE -> MobEffects.DAMAGE_RESISTANCE;
                };
                player.addEffect(new MobEffectInstance(effect, buff.duration(), buff.amplifier(), false, true));
            }
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, release.hungerDuration(), release.hungerAmplifier()));
            cn.academy.port.achievements.ClassicAchievements.trigger(player,"electromaster.body_intensify");
            hold.session.complete(); performed = true;
            return true;
        } finally {
            COMMITTING.remove(player.getUUID());
            send(player, hold, "body_intensify_end", performed);
        }
    }
    public static void abort(ServerPlayer player) {
        if (!serverThread(player)) return;
        Hold hold = HOLDS.remove(player.getUUID());
        if (hold != null) { hold.session.end(); send(player, hold, "body_intensify_end", false); }
    }
    /** Final player/session disposal retires replay history; ordinary cancellation keeps it. */
    public static void remove(ServerPlayer player) { if (serverThread(player)) { abort(player); CLIENT_INPUTS.remove(player.getUUID()); } }
    public static boolean active(ServerPlayer player) { return player != null && HOLDS.containsKey(player.getUUID()); }
    public static int heldTicks(ServerPlayer player) { Hold hold = player == null ? null : HOLDS.get(player.getUUID()); return hold == null ? 0 : hold.session.ticks(); }
    public static void clear() { HOLDS.clear(); COMMITTING.clear(); CLIENT_INPUTS.clear(); nextToken = 0; }
    private static boolean serverThread(ServerPlayer player) { return player != null && player.serverLevel().getServer().isSameThread(); }
    private static boolean canAct(ServerPlayer player) { return player.isAlive() && !player.isRemoved() && !player.isSpectator() && BodyIntensifySession.mayStart(AbilityStorage.get(player)); }
    private static boolean valid(ServerPlayer player, Hold hold) {
        return hold.level == player.serverLevel() && hold.session.state() == AbilityStorage.get(player)
                && hold.session.active() && canAct(player) && player.serverLevel().getGameTime() >= hold.lastTick;
    }
    private static Set<ServerPlayer> audience(ServerPlayer player) {
        var audience = new LinkedHashSet<ServerPlayer>();
        for (ServerPlayer viewer : player.serverLevel().players()) if (viewer.distanceToSqr(player) <= 25 * 25) audience.add(viewer);
        audience.add(player); return audience;
    }
    private static void send(ServerPlayer player, Hold hold, String kind, boolean performed) {
        var tag = new CompoundTag(); tag.putString("kind", kind); tag.putInt("entity", player.getId()); tag.putLong("token", hold.token); tag.putLong("input", hold.inputNonce); tag.putBoolean("performed", performed);
        for (ServerPlayer viewer : hold.audience) if (!viewer.hasDisconnected()) PacketDistributor.sendToPlayer(viewer, new AcademyNetwork.ClientData(tag));
    }
    private static final class Hold {
        final ServerLevel level; final BodyIntensifySession session; final long token, inputNonce; final Set<ServerPlayer> audience; long lastTick;
        Hold(ServerLevel level, BodyIntensifySession session, long token, long inputNonce, Set<ServerPlayer> audience) {
            this.level = level; this.session = session; this.token = token; this.inputNonce = inputNonce; this.audience = audience; lastTick = level.getGameTime() - 1;
        }
    }
}

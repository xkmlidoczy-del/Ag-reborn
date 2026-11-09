/* AcademyCraft 1.0.7 CurrentCharging/ChargingContext adaptation. Copyright Lambda Innovation, GPLv3. See NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.CurrentChargingSession;
import cn.academy.port.core.LegacySingleKeyProtocol;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-owned held charging context. Never accept a client-supplied target, mode, mastery or charge amount. */
public final class CurrentCharging {
    public static final String ID = CurrentChargingSession.ID;
    public static final double RANGE = CurrentChargingSession.RANGE;
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final LegacySingleKeyProtocol.NonceLedger NONCES = new LegacySingleKeyProtocol.NonceLedger();
    private static long nextToken;
    private CurrentCharging() {}

    /** Key-down captures only item-vs-block mode and mastery, not the equipped stack. */
    public static boolean start(ServerPlayer player) { return startInternal(player, 0); }

    /** Authenticated wire entry: positive inputs are single-use for this live connection. */
    public static boolean canAcceptInput(ServerPlayer player, long input) {
        return LegacySingleKeyServerIdentity.canClaim(NONCES, player, input);
    }

    /** Claims only after the shared ingress has completed its nonmutating preflight. */
    public static boolean start(ServerPlayer player, long input) {
        if (!LegacySingleKeyServerIdentity.claim(NONCES, player, input)) return false;
        return startInternal(player, input);
    }

    private static boolean startInternal(ServerPlayer player, long input) {if(cn.academy.port.AbilityConsumption.busy(player))return false;
        if (!onServerThread(player) || !canAct(player)) return false;
        Hold previous = HOLDS.get(player.getUUID());
        if (previous != null) {
            if (valid(player, previous)) return false;
            if (HOLDS.remove(player.getUUID(), previous)) {
                previous.session.end();
                send(previous.identity.owner(), previous, "charging_end");
            }
        }
        var session = CurrentChargingSession.begin(AbilityStorage.get(player),
                !player.getMainHandItem().isEmpty(), player.getAbilities().instabuild);
        if (session == null) return false;
        // Start may pay enough overload to lock ability use, just as OverloadEvent
        // disposed the original context. No loop should outlive that cancellation.
        var hold = new Hold(player, session, input, ++nextToken, audience(player));
        if (!session.active()) {
            send(player, hold, "charging_end");
            return true;
        }
        HOLDS.put(player.getUUID(), hold);
        send(player, hold, "charging_start");
        return true;
    }

    /** Called once per player post-tick; same-world-tick replays cannot charge/spend twice. */
    public static void tick(ServerPlayer player) {
        if (!onServerThread(player)) return;
        var hold = HOLDS.get(player.getUUID());
        if (hold == null) return;
        if (!valid(player, hold)) { abort(player); return; }
        long now = player.serverLevel().getGameTime();
        if (now <= hold.lastTick) return;
        hold.lastTick = now;
        CurrentChargingSession.Target target;
        if (hold.session.itemMode()) target = ChargingEnergy.item(player.getMainHandItem());
        else {
            var hit = ClassicRaytrace.living(player, RANGE, ClipContext.Fluid.NONE);
            target = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                    ? ChargingEnergy.block(player.serverLevel(), block.getBlockPos())
                    : ChargingEnergy.block(null, null);
        }
        var result = hold.session.tick(target, player.getAbilities().instabuild);
        if (result != CurrentChargingSession.TickResult.CONTINUE) abort(player);
    }

    /** Key-up is termination, not a one-shot discharge. Replays have no additional cost. */
    public static boolean release(ServerPlayer player) {
        if (!onServerThread(player) || !HOLDS.containsKey(player.getUUID())) return false;
        abort(player);
        return true;
    }

    /** A wire key-up can terminate only the captured positive physical input. */
    public static boolean release(ServerPlayer player, long input) {
        if (!onServerThread(player)) return false;
        var hold = HOLDS.get(player.getUUID());
        if (hold == null || !hold.identity.matches(player, input)) return false;
        abort(player);
        return true;
    }

    /** Accepted wire key-up also carries the issued owner epoch and server token. */
    public static boolean release(ServerPlayer player, long input, long token, long epoch) {
        if (!onServerThread(player)) return false;
        var hold = HOLDS.get(player.getUUID());
        if (hold == null || !hold.identity.matches(player, input, token, epoch)) return false;
        return release(player, input);
    }

    /** Key-abort, deactivation, preset/screen cancellation, logout, death, clone or dimension change. */
    public static void abort(ServerPlayer player) {
        if (!onServerThread(player)) return;
        var current = HOLDS.get(player.getUUID());
        if (current != null && current.identity.owner() != player) return;
        var hold = HOLDS.remove(player.getUUID());
        if (hold != null) {
            hold.session.end();
            send(player, hold, "charging_end");
        }
    }

    /** Exact wire cancellation; input0 is never a wildcard for a positive hold. */
    public static boolean abort(ServerPlayer player, long input) {
        if (!onServerThread(player)) return false;
        var hold = HOLDS.get(player.getUUID());
        if (hold == null || !hold.identity.matches(player, input)) return false;
        abort(player);
        return true;
    }

    /** Accepted cancellation cannot cross a same-ID/UUID/connection owner replacement. */
    public static boolean abort(ServerPlayer player, long input, long token, long epoch) {
        if (!onServerThread(player)) return false;
        var hold = HOLDS.get(player.getUUID());
        if (hold == null || !hold.identity.matches(player, input, token, epoch)) return false;
        return abort(player, input);
    }

    /** Final player lifecycle cleanup; ordinary release/abort preserves nonce history. */
    public static void remove(ServerPlayer player) {
        if (!onServerThread(player)) return;
        abort(player);
        NONCES.forget(player);
    }

    /** Read-only introspection for native lifecycle verification. */
    public static boolean active(ServerPlayer player) { return player != null && HOLDS.containsKey(player.getUUID()); }
    /** Server-stopped cleanup, never persist held inputs across restart. */
    public static void clear() { HOLDS.clear(); NONCES.clear(); nextToken = 0; }

    private static boolean onServerThread(ServerPlayer player) {
        return player != null && player.serverLevel().getServer().isSameThread();
    }
    private static boolean canAct(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && CurrentChargingSession.mayStart(AbilityStorage.get(player))
                && finite(player.getEyePosition()) && finite(ClassicRaytrace.direction(player));
    }
    private static boolean valid(ServerPlayer player, Hold hold) {
        return hold.identity.owner() == player && hold.level == player.serverLevel() && hold.session.state() == AbilityStorage.get(player)
                && (hold.identity.input() == 0 || hold.identity.owns(player))
                && hold.session.active() && canAct(player)
                && player.serverLevel().getGameTime() >= hold.lastTick;
    }
    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
    private static Set<ServerPlayer> audience(ServerPlayer player) {
        var result = new LinkedHashSet<ServerPlayer>();
        for (var viewer : player.serverLevel().players())
            if (viewer.distanceToSqr(player) <= 25 * 25) result.add(viewer);
        result.add(player);
        return result;
    }
    private static void send(ServerPlayer player, Hold hold, String kind) {
        var tag = new CompoundTag();
        tag.putString("kind", kind);
        hold.identity.write(tag);
        tag.putBoolean("item_mode", hold.session.itemMode());
        // Match context recipient capture: every started viewer gets the end even
        // after leaving the start radius or changing dimension.
        for (var viewer : hold.audience)
            if (!viewer.hasDisconnected()) PacketDistributor.sendToPlayer(viewer, new AcademyNetwork.ClientData(tag));
    }
    private static final class Hold {
        final ServerLevel level;
        final CurrentChargingSession session;
        final long token;
        final LegacySingleKeyServerIdentity identity;
        final Set<ServerPlayer> audience;
        long lastTick;
        Hold(ServerPlayer owner, CurrentChargingSession session, long input, long token, Set<ServerPlayer> audience) {
            this.level = owner.serverLevel(); this.session = session; this.token = token; this.audience = audience;
            identity = new LegacySingleKeyServerIdentity(owner, input, token, session.state());
            lastTick = level.getGameTime() - 1;
        }
    }
}

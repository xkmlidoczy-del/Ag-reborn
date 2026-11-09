/* AcademyCraft1.0.7 ThunderClap adaptation. Copyright Lambda Innovation. GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.AbilityDamage;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.AcademyConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Held, server-authoritative, block-only aiming context. Key-up/abort do not discharge. */
public final class ThunderClap {
    public static final String ID = ThunderClapSession.ID;
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final Map<UUID, Long> CLIENT_INPUTS = new HashMap<>();
    private static final Set<UUID> COMMITTING = new HashSet<>();
    private static long nextToken;
    private ThunderClap() {}
    public static boolean start(ServerPlayer player) {if(cn.academy.port.AbilityConsumption.busy(player))return false; return start(player, 0); }
    /** Client nonce is correlation only, never an aim/mastery/duration/resource authority. */
    public static boolean start(ServerPlayer player, long inputNonce) {if(cn.academy.port.AbilityConsumption.busy(player))return false;
        if (!serverThread(player) || !canAct(player) || COMMITTING.contains(player.getUUID()) || inputNonce < 0
                || inputNonce > 0 && inputNonce <= CLIENT_INPUTS.getOrDefault(player.getUUID(), 0L)) return false;
        Hold old = HOLDS.get(player.getUUID());
        if (old != null) { if (valid(player, old)) return false; abort(player); }
        ThunderClapSession session = ThunderClapSession.begin(AbilityStorage.get(player), player.getAbilities().instabuild);
        if (session == null) return false;
        Hold hold = new Hold(player.serverLevel(), session, ++nextToken, inputNonce, audience(player));
        HOLDS.put(player.getUUID(), hold);
        if (inputNonce > 0) CLIENT_INPUTS.put(player.getUUID(), inputNonce);
        // Source sends effect_start before overload consume; same-token end covers overload disposal.
        send(player, hold, "thunder_clap_start");
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
        hold.point = aim(player); // Source updates target BEFORE increment and CP/end logic.
        var result = hold.session.tick(player.getAbilities().instabuild);
        if (result == ThunderClapSession.TickResult.FIRE) discharge(player, hold);
        else if (result != ThunderClapSession.TickResult.CONTINUE) abort(player);
    }
    public static Vec3 aim(Entity player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(ClassicRaytrace.direction(player).normalize().scale(ThunderClapSession.AIM_DISTANCE));
        HitResult result = ClassicRaytrace.perform(player, start, end, ClipContext.Fluid.NONE, entity -> false);
        return result.getType() == HitResult.Type.MISS ? end : result.getLocation();
    }
    /** Source attackRange queries ALL noncaster loaded entity kinds; no line-of-sight test.
     * Legacy weatherEffects were a separate list; modern LightningBolt must stay outside this query. */
    public static List<Entity> targets(ServerPlayer player, Vec3 point, double range) {
        return List.copyOf(player.serverLevel().getEntities(player, new AABB(point, point).inflate(range),
                entity -> !(entity instanceof LightningBolt)
                        && ThunderClapSession.within(entity.position().distanceToSqr(point), range)));
    }
    private static void discharge(ServerPlayer player, Hold hold) {
        HOLDS.remove(player.getUUID(), hold);
        if (!COMMITTING.add(player.getUUID())) { hold.session.end(); send(player, hold, "thunder_clap_end"); return; }
        try {
            var lightning = EntityType.LIGHTNING_BOLT.create(hold.level);
            if (lightning != null) {
                lightning.setPos(hold.point);
                // Actual lightning: weather sound/fire/vanilla strike behavior, not visual-only.
                hold.level.addFreshEntity(lightning);
            }
            double range = ThunderClapSession.range(hold.session.mastery());
            float damage = ThunderClapSession.damage(hold.session.mastery(), hold.session.ticks());
            for (Entity target : targets(player, hold.point, range)) {
                // Central AbilityDamage preserves original calculation-event-before-canAttack ordering.
                if (target instanceof ServerPlayer other && !other.canHarmPlayer(player)) continue;
                float applied = ThunderClapSession.radialDamage(damage, target.position().distanceTo(hold.point), range);
                AbilityDamage.attack(player, "electromaster.thunder_clap", target, applied);
            }
            hold.session.complete();
            cn.academy.port.achievements.ClassicAchievements.trigger(player,"electromaster.thunder_clap");
        } finally {
            COMMITTING.remove(player.getUUID());
            send(player, hold, "thunder_clap_end");
        }
    }
    public static boolean release(ServerPlayer player) {
        if (!serverThread(player) || !HOLDS.containsKey(player.getUUID())) return false;
        abort(player); return true;
    }
    public static void abort(ServerPlayer player) {
        if (!serverThread(player)) return;
        Hold hold = HOLDS.remove(player.getUUID());
        if (hold != null) { hold.session.end(); send(player, hold, "thunder_clap_end"); }
    }
    /** Final player/session disposal retires replay history; ordinary cancellation keeps it. */
    public static void remove(ServerPlayer player) { if (serverThread(player)) { abort(player); CLIENT_INPUTS.remove(player.getUUID()); } }
    public static boolean active(ServerPlayer player) { return player != null && HOLDS.containsKey(player.getUUID()); }
    public static int heldTicks(ServerPlayer player) { Hold hold = player == null ? null : HOLDS.get(player.getUUID()); return hold == null ? 0 : hold.session.ticks(); }
    public static void clear() { HOLDS.clear(); COMMITTING.clear(); CLIENT_INPUTS.clear(); nextToken = 0; }
    private static boolean serverThread(ServerPlayer player) { return player != null && player.serverLevel().getServer().isSameThread(); }
    private static boolean canAct(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && ThunderClapSession.mayStart(AbilityStorage.get(player))
                && finite(player.getEyePosition()) && finite(ClassicRaytrace.direction(player));
    }
    private static boolean valid(ServerPlayer player, Hold hold) {
        return hold.level == player.serverLevel() && hold.session.state() == AbilityStorage.get(player)
                && hold.session.active() && canAct(player) && player.serverLevel().getGameTime() >= hold.lastTick;
    }
    private static boolean finite(Vec3 vector) { return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z); }
    private static Set<ServerPlayer> audience(ServerPlayer player) {
        var audience = new LinkedHashSet<ServerPlayer>();
        for (ServerPlayer viewer : player.serverLevel().players()) if (viewer.distanceToSqr(player) <= 25 * 25) audience.add(viewer);
        audience.add(player); return audience;
    }
    private static void send(ServerPlayer player, Hold hold, String kind) {
        var tag = new CompoundTag(); tag.putString("kind", kind); tag.putInt("entity", player.getId()); tag.putLong("token", hold.token); tag.putLong("input", hold.inputNonce);
        for (ServerPlayer viewer : hold.audience) if (!viewer.hasDisconnected()) PacketDistributor.sendToPlayer(viewer, new AcademyNetwork.ClientData(tag));
    }
    private static final class Hold {
        final ServerLevel level; final ThunderClapSession session; final long token, inputNonce; final Set<ServerPlayer> audience;
        long lastTick; Vec3 point = Vec3.ZERO;
        Hold(ServerLevel level, ThunderClapSession session, long token, long inputNonce, Set<ServerPlayer> audience) {
            this.level = level; this.session = session; this.token = token; this.inputNonce = inputNonce; this.audience = audience; lastTick = level.getGameTime() - 1;
        }
    }
}

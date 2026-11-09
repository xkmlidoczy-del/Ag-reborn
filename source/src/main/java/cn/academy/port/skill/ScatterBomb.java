/* AcademyCraft 1.0.7 ScatterBomb/EntityMdBall adaptation. Copyright Lambda Innovation, GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-owned held context; ordinary release AND key-abort shoot every accumulated ball. */
public final class ScatterBomb {
    public static final String ID = ScatterBombSession.ID;
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final Map<UUID, Long> INPUTS = new HashMap<>();
    private static final Set<UUID> COMMITTING = new HashSet<>();
    private static long nextToken;
    private ScatterBomb() {}
    public static boolean start(ServerPlayer player) {if(cn.academy.port.AbilityConsumption.busy(player))return false; return start(player, 0); }
    public static boolean start(ServerPlayer player, long inputNonce) {if(cn.academy.port.AbilityConsumption.busy(player))return false;
        if (!MeltdownerStarterSupport.serverThread(player) || !canStart(player) || COMMITTING.contains(player.getUUID())
                || inputNonce < 0 || inputNonce > 0 && inputNonce <= INPUTS.getOrDefault(player.getUUID(), 0L)) return false;
        Hold old = HOLDS.get(player.getUUID());
        if (old != null) { if (identityValid(player, old)) return false; remove(player); }
        var session = ScatterBombSession.begin(AbilityStorage.get(player), player.getAbilities().instabuild);
        if (session == null) return false;
        var hold = new Hold(player.serverLevel(), session, ++nextToken, inputNonce, MeltdownerStarterSupport.audience(player));
        HOLDS.put(player.getUUID(), hold);
        if (inputNonce > 0) INPUTS.put(player.getUUID(), inputNonce);
        send(player, hold, "scatter_bomb_start");
        if (session.ending()) finish(player, hold, true);
        return true;
    }
    public static void tick(ServerPlayer player) {
        if (!MeltdownerStarterSupport.serverThread(player)) return;
        var hold = HOLDS.get(player.getUUID()); if (hold == null) return;
        if (!identityValid(player, hold)) { remove(player); return; }
        var state = hold.session.state();
        if (!state.activated || !state.overloadFine || state.interfering) { finish(player, hold, true); return; }
        long now = player.serverLevel().getGameTime(); if (now <= hold.lastTick) return;
        hold.lastTick = now;
        // Original balls follow translation on each entity update, keeping their spawn-yaw offset.
        for (var ball : hold.balls) ball.position = player.position().add(ball.offset);
        var tick = hold.session.tick(player.getAbilities().instabuild);
        if (tick.spawnBall()) spawn(player, hold);
        if (tick.selfHurt()) player.hurt(player.damageSources().playerAttack(player), 6);
        if (tick.ending()) finish(player, hold, true);
    }
    public static boolean release(ServerPlayer player) {
        if (!MeltdownerStarterSupport.serverThread(player)) return false;
        var hold = HOLDS.get(player.getUUID()); if (hold == null) return false;
        if (!identityValid(player, hold)) { remove(player); return false; }
        finish(player, hold, true); return true;
    }
    /** This is the original MSG_KEYABORT termination, so it still shoots. */
    public static void abort(ServerPlayer player) { release(player); }
    /** Death, disconnect, clone, dimension and server shutdown discard without stale-world attacks. */
    public static void remove(ServerPlayer player) {
        if (!MeltdownerStarterSupport.serverThread(player)) return;
        var hold = HOLDS.get(player.getUUID()); if (hold != null) finish(player, hold, false);
        INPUTS.remove(player.getUUID());
    }
    public static boolean active(ServerPlayer player) { return player != null && HOLDS.containsKey(player.getUUID()); }
    public static int heldTicks(ServerPlayer player) { var h = player == null ? null : HOLDS.get(player.getUUID()); return h == null ? 0 : h.session.ticks(); }
    public static int ballCount(ServerPlayer player) { var h = player == null ? null : HOLDS.get(player.getUUID()); return h == null ? 0 : h.balls.size(); }
    public static void clear() { HOLDS.clear(); INPUTS.clear(); COMMITTING.clear(); nextToken = 0; }
    private static boolean canStart(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && ScatterBombSession.mayStart(AbilityStorage.get(player)) && MeltdownerStarterSupport.finiteAim(player) && MeltdownerStarterSupport.finite(player.position())
                && MeltdownerStarterSupport.finite(ClassicRaytrace.direction(player));
    }
    private static boolean identityValid(ServerPlayer player, Hold hold) {
        var state = AbilityStorage.get(player);
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && hold.level == player.serverLevel()
                && hold.session.state() == state && hold.session.active() && state.category.equals("meltdowner")
                && state.level >= ScatterBombSession.LEVEL && state.learned(ID)
                && player.serverLevel().getGameTime() >= hold.lastTick && MeltdownerStarterSupport.finiteAim(player) && MeltdownerStarterSupport.finite(player.position())
                && MeltdownerStarterSupport.finite(ClassicRaytrace.direction(player));
    }
    private static void spawn(ServerPlayer player, Hold hold) {
        var random = player.serverLevel().random;
        float theta = -player.getYRot() / 180 * Mth.PI + (-Mth.PI * .45F + random.nextFloat() * (Mth.PI * .9F));
        float radius = .8F + random.nextFloat() * .5F;
        var offset = new Vec3(Mth.sin(theta) * radius, -1.2F + random.nextFloat() * 1.4F + 1.6, Mth.cos(theta) * radius);
        var ball = new Ball(offset, player.position().add(offset)); hold.balls.add(ball);
        var tag = MeltdownerStarterSupport.packet(player, "scatter_bomb_ball", hold.token, hold.input);
        tag.putInt("index", hold.balls.size() - 1); tag.putInt("life", ScatterBombSession.BALL_LIFE);
        tag.putDouble("offset_x", offset.x); tag.putDouble("offset_y", offset.y); tag.putDouble("offset_z", offset.z);
        MeltdownerStarterSupport.position(tag, ball.position); MeltdownerStarterSupport.send(hold.audience, tag);
    }
    private static void finish(ServerPlayer player, Hold hold, boolean perform) {
        if (!HOLDS.remove(player.getUUID(), hold)) return;
        if (!perform || !COMMITTING.add(player.getUUID())) {
            hold.session.discard(); send(player, hold, "scatter_bomb_end"); return;
        }
        try {
            // newDest is a raw Motion3D eye + head-look * 15, not ElectronBomb's looking-position ray.
            for (int index = 0; index < hold.balls.size(); index++) {
                var ball = hold.balls.get(index);
                var random = player.serverLevel().random;
                var aim = ScatterBombAim.direction(player.getYHeadRot(), player.getXRot(), random.nextFloat(), random.nextFloat());
                Vec3 destination = player.getEyePosition().add(new Vec3(aim.x(), aim.y(), aim.z()).scale(ScatterBombSession.RANGE));
                var hit = ClassicRaytrace.perform(player, ball.position, destination, ClipContext.Fluid.NONE, entity -> true);
                if (hit instanceof EntityHitResult entityHit) {
                    // Source resets hurtResistantTime before EACH ball, allowing seven damage transactions.
                    if (entityHit.getEntity() instanceof LivingEntity living) living.invulnerableTime = -1;
                    RadiationMarks.attack(player, "meltdowner." + ID, entityHit.getEntity(), hold.session.damage());
                }
                sendRay(player, hold, index, ball.position, destination);
            }
            hold.session.complete();
        } finally {
            COMMITTING.remove(player.getUUID()); send(player, hold, "scatter_bomb_end");
            AbilityStorage.save(player); AcademyNetwork.sync(player);
        }
    }
    private static void send(ServerPlayer player, Hold hold, String kind) {
        MeltdownerStarterSupport.send(hold.audience, MeltdownerStarterSupport.packet(player, kind, hold.token, hold.input));
    }
    private static void sendRay(ServerPlayer player, Hold hold, int index, Vec3 origin, Vec3 destination) {
        var tag = MeltdownerStarterSupport.packet(player, "scatter_bomb_ray", hold.token, hold.input);
        tag.putInt("index", index); MeltdownerStarterSupport.position(tag, origin);
        Vec3 delta = destination.subtract(origin), direction = delta.normalize();
        tag.putDouble("dx", direction.x); tag.putDouble("dy", direction.y); tag.putDouble("dz", direction.z);
        tag.putDouble("length", delta.length()); MeltdownerStarterSupport.send(hold.audience, tag);
    }
    private static final class Ball {
        final Vec3 offset; Vec3 position;
        Ball(Vec3 offset, Vec3 position) { this.offset = offset; this.position = position; }
    }
    private static final class Hold {
        final ServerLevel level; final ScatterBombSession session; final long token, input;
        final Set<ServerPlayer> audience; final List<Ball> balls = new ArrayList<>(); long lastTick;
        Hold(ServerLevel level, ScatterBombSession session, long token, long input, Set<ServerPlayer> audience) {
            this.level = level; this.session = session; this.token = token; this.input = input; this.audience = audience;
            lastTick = level.getGameTime() - 1;
        }
    }
}

/* AcademyCraft 1.0.7 VecAccel authoritative server adaptation. GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server computes charge, ground gate, CP, overload, cooldown and previous-body aim. No client physics payload. */
public final class VecAccel {
    private static final boolean VISUAL_QA = Boolean.getBoolean("academy.visual.qa");
    private static final org.slf4j.Logger QA_LOG = com.mojang.logging.LogUtils.getLogger();
    public static final String ID = VecAccelSession.ID;
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final Map<UUID, Long> INPUTS = new HashMap<>();
    private static long nextToken;
    private VecAccel() {}
    public static boolean start(ServerPlayer p) {if(cn.academy.port.AbilityConsumption.busy(p))return false; return start(p, 0); }
    public static boolean start(ServerPlayer p, long nonce) {if(cn.academy.port.AbilityConsumption.busy(p))return false;
        if (!VectorStarterSupport.ready(p) || nonce < 0 || nonce > 0 && nonce <= INPUTS.getOrDefault(p.getUUID(), 0L)) return false;
        var old = HOLDS.get(p.getUUID()); if (old != null) { if (valid(p, old)) return false; remove(p); }
        var session = VecAccelSession.begin(AbilityStorage.get(p)); if (session == null) return false;
        var h = new Hold(p, session, ++nextToken, nonce); HOLDS.put(p.getUUID(), h);
        if (nonce > 0) INPUTS.put(p.getUUID(), nonce); send(p, h, "vec_accel_start", null);
        if (VISUAL_QA) QA_LOG.info("VecAccel QA server start token={} input={} position={} yaw={} pitch={}", h.token, nonce, p.position(), p.getYRot(), p.getXRot());
        return true;
    }
    public static void tick(ServerPlayer p) {
        if (!VectorStarterSupport.serverThread(p)) return;
        var h = HOLDS.get(p.getUUID()); if (h == null) return;
        if (!valid(p, h)) { abort(p); return; }
        long now = p.serverLevel().getGameTime(); if (now <= h.lastTick) return; h.lastTick = now;
        h.session.tick(groundWithinTwo(p));
    }
    /** Block-only feet-to-feet-minus-two trace, inclusive endpoint, no onGround substitute. */
    public static boolean groundWithinTwo(ServerPlayer p) {
        Vec3 from = p.position();
        return p.serverLevel().clip(new ClipContext(from, from.add(0, -2, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.BLOCK;
    }
    public static Vec3 initialVelocity(float previousYaw, float previousPitch, int ticks) {
        float yaw = previousYaw * (float) Math.PI / 180, pitch = (previousPitch - 10) * (float) Math.PI / 180;
        // Original EntityLook uses MathHelper's lookup table and float products.
        return new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch))
                .scale(VecAccelSession.speed(ticks));
    }
    public static boolean release(ServerPlayer p) { return release(p, 0); }
    public static boolean release(ServerPlayer p, long nonce) {
        if (!VectorStarterSupport.serverThread(p)) return false;
        var h = HOLDS.get(p.getUUID()); if (h == null || nonce > 0 && nonce != h.input) return false;
        if (!valid(p, h)) { abort(p); return false; }
        HOLDS.remove(p.getUUID(), h);
        Vec3 velocity = initialVelocity(p.yRotO, p.xRotO, h.session.ticks());
        if (VISUAL_QA) QA_LOG.info("VecAccel QA server release token={} input={} ticks={} ground={} position={} previousAim={},{} currentAim={},{} velocity={}", h.token, nonce, h.session.ticks(), h.session.canPerform(), p.position(), p.yRotO, p.xRotO, p.getYRot(), p.getXRot(), velocity);
        if (!VectorStarterSupport.finite(velocity) || !h.session.release(p.getAbilities().instabuild)) {
            send(p, h, "vec_accel_end", null); return false;
        }
        p.setDeltaMovement(velocity); p.stopRiding(); p.fallDistance = 0; p.hasImpulse = true; p.hurtMarked = true;
        send(p, h, "vec_accel_perform", velocity); AbilityStorage.save(p); AcademyNetwork.sync(p); return true;
    }
    public static void abort(ServerPlayer p) { abort(p, 0); }
    public static boolean abort(ServerPlayer p, long nonce) {
        if (!VectorStarterSupport.serverThread(p)) return false;
        var h = HOLDS.get(p.getUUID()); if (h == null || nonce > 0 && h.input != nonce) return false;
        HOLDS.remove(p.getUUID(), h); h.session.discard(); send(p, h, "vec_accel_end", null); return true;
    }
    public static void remove(ServerPlayer p) { if (VectorStarterSupport.serverThread(p)) { abort(p); INPUTS.remove(p.getUUID()); } }
    public static boolean active(ServerPlayer p) { return p != null && HOLDS.containsKey(p.getUUID()); }
    public static int heldTicks(ServerPlayer p) { var h = p == null ? null : HOLDS.get(p.getUUID()); return h == null ? 0 : h.session.ticks(); }
    public static void clear() { HOLDS.clear(); INPUTS.clear(); nextToken = 0; }
    private static boolean valid(ServerPlayer p, Hold h) {
        return VectorStarterSupport.ready(p) && h.world == p.serverLevel() && h.session.state() == AbilityStorage.get(p)
                && h.session.active() && VecAccelSession.mayStart(h.session.state()) && p.serverLevel().getGameTime() >= h.lastTick;
    }
    private static void send(ServerPlayer p, Hold h, String kind, Vec3 velocity) {
        var tag = VectorStarterSupport.packet(p, kind, h.token, h.input); tag.putInt("ticks", h.session.ticks());
        if (velocity != null) { tag.putDouble("vx", velocity.x); tag.putDouble("vy", velocity.y); tag.putDouble("vz", velocity.z); }
        VectorStarterSupport.send(h.audience, tag);
    }
    private static final class Hold {
        final ServerLevel world; final VecAccelSession session; final long token, input; final Set<ServerPlayer> audience; long lastTick;
        Hold(ServerPlayer p, VecAccelSession session, long token, long input) {
            world = p.serverLevel(); this.session = session; this.token = token; this.input = input;
            audience = VectorStarterSupport.audience(p); lastTick = world.getGameTime() - 1;
        }
    }
}

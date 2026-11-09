package cn.academy.port.highpower;

import cn.academy.port.highpower.core.ConstructRules;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * V-ADV-01 Vector Construct Manipulation (skill 444). Tap a block to preview the connected structure, tap again to grab it;
 * while held it hovers in front of the caster and turns with the view. Tap to set it back on the block grid (never
 * overwriting), sneak-tap to throw it. Only intent crosses the network: the server raycasts, searches, validates rights,
 * charges CP and drives the body. Requires the optional Sable mod; without it the skill refuses before charging.
 */
@EventBusSubscriber(modid = "academy")
public final class ConstructRuntime {
    public static final int PREVIEW = 0, REFUSED = 1, HOLD = 2, THROWN = 3, LANDED = 4, END = 5, DASH = 6, SHATTER = 7, ENTOMB = 8;
    public static final String DAMAGE_KEY = "vecmanip.vector_construct";
    public static Consumer<Visual> clientReceiver = ignored -> {};

    private enum Phase { IDLE, PREVIEW, DASH, HOLD }
    private static final class State {
        Phase phase = Phase.IDLE;
        ConstructLink.Selection selection;
        long previewUntil, nextUpkeep, token;
        UUID body;
        int blocks, span;
        double mass, yaw0, playerYaw0;
        long dashStart, holdSince;
        double radius, height; boolean dimsKnown;
        Vec3 dashStand, grabFrom;
    }
    private static final class Thrown {
        final UUID owner; final int ownerId; final long token; final ServerLevel level; final long until, born; final double speed, mass; final BigDecimal damage; final Set<UUID> hit = new HashSet<>();
        double last;
        Thrown(UUID owner, int ownerId, long token, ServerLevel level, long until, double speed, double mass,BigDecimal damage) {
            this.owner = owner; this.ownerId = ownerId; this.token = token; this.level = level; this.until = until; this.born = level.getGameTime(); this.speed = speed; this.mass = mass; this.damage=damage;
        }
    }
    /** A lock in progress: the broken-off blocks arrive at {@code placeAt}, stay until {@code restoreAt} and then break away again. */
    private static final class Entomb {
        final ServerLevel level; final UUID owner, target; final long placeAt, restoreAt; final java.util.List<net.minecraft.world.level.block.state.BlockState> palette;
        final java.util.List<Placed> placed = new ArrayList<>(); boolean done; long gaveUpAt;
        Entomb(ServerLevel level, UUID owner, UUID target, long placeAt, long restoreAt, java.util.List<net.minecraft.world.level.block.state.BlockState> palette) {
            this.level = level; this.owner = owner; this.target = target; this.placeAt = placeAt; this.restoreAt = restoreAt; this.palette = palette;
        }
    }
    private record Placed(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {}
    private static final java.util.List<Entomb> ENTOMBS = new ArrayList<>();
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<UUID, Thrown> THROWN_BODIES = new HashMap<>();
    private static long tokens;

    private ConstructRuntime() {}

    private static double level(ServerPlayer p) { return HighPowerGrowth.values(p).position().doubleValue(); }
    private static long now(ServerPlayer p) { return p.serverLevel().getGameTime(); }
    private static boolean feedback(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("message.academy.highpower." + HighPowerSkills.VECTOR_CONSTRUCT + "." + key), true);
        return false;
    }
    private static int holding() { int n = 0; for (State s : STATES.values()) if (s.phase == Phase.HOLD) n++; return n; }

    /** One tap of the skill key. {@code ready} and {@code usable} are the shared skill gates evaluated by the caller. */
    public static boolean press(ServerPlayer p, boolean ready, boolean usable, double baseCp, int cooldown) {
        ConstructLink link = ConstructLink.get();
        if (link == null) return feedback(p, "no_backend");
        State st = STATES.computeIfAbsent(p.getUUID(), id -> new State());
        boolean sneak = p.isShiftKeyDown();
        if (st.phase == Phase.PREVIEW && now(p) > st.previewUntil) { st.phase = Phase.IDLE; st.selection = null; }
        switch (st.phase) {
            case IDLE -> {
                if (!ready || !usable) return feedback(p, "not_ready");
                HitResult hit = p.pick(ConstructRules.RANGE, 1, false);
                if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return feedback(p, "no_target");
                double lv = level(p);
                UUID owned = link.ownedBodyAt(p, block.getBlockPos());
                if (owned != null) return takeExisting(p, st, link, owned, baseCp);
                var sel = link.select(p, block.getBlockPos(), ConstructRules.maxBlocks(lv), ConstructRules.maxSpan(lv));
                if (!sel.ok()) { send(p, st, REFUSED, block.getLocation(), null, null, Vec3.ZERO, 0); return feedback(p, sel.reason()); }
                st.phase = Phase.PREVIEW; st.selection = sel; st.previewUntil = now(p) + ConstructRules.PREVIEW_TICKS; st.token = ++tokens;
                send(p, st, PREVIEW, vec(sel.center()), vec(sel.min()), vec(sel.max()), Vec3.ZERO, sel.blocks());
                p.displayClientMessage(Component.translatable("message.academy.highpower.vector_construct.preview", sel.blocks(), sel.span()), true);
                return true;
            }
            case PREVIEW -> {
                if (sneak) { cancel(p, st); return true; }
                if (!ready || !usable) return feedback(p, "not_ready");
                if (holding() >= ConstructRules.SERVER_MAX) return feedback(p, "server_busy");
                double lv = level(p);
                var again = link.select(p, st.selection.origin(), ConstructRules.maxBlocks(lv), ConstructRules.maxSpan(lv));
                if (!again.ok() || again.blocks() != st.selection.blocks()) { cancel(p, st); return feedback(p, again.ok() ? "changed" : again.reason()); }
                var min = new cn.academy.port.highpower.core.V3(again.min()[0], again.min()[1], again.min()[2]);
                var max = new cn.academy.port.highpower.core.V3(again.max()[0], again.max()[1], again.max()[2]);
                if (ConstructRules.dashGap(p.getX(), p.getZ(), min, max) > ConstructRules.DASH_REACH) {
                    // Locked on from afar: crouch, dash to the structure, then take hold of it at the hand.
                    if (HighPowerResources.cp(p) < ConstructRules.startCost(baseCp, again.blocks())) return feedback(p, "not_ready");
                    var stand = ConstructRules.dashStand(new cn.academy.port.highpower.core.V3(p.getX(), p.getY(), p.getZ()), min, max);
                    st.phase = Phase.DASH; st.dashStart = now(p); st.dashStand = new Vec3(stand.x(), stand.y(), stand.z()); st.selection = again; st.token = ++tokens;
                    send(p, st, DASH, vec(again.center()), vec(again.min()), vec(again.max()), st.dashStand, again.blocks());
                    return true;
                }
                return commitGrab(p, st, link, baseCp, again);
            }
            case DASH -> {
                if (sneak) { stopDash(p); cancel(p, st); return true; }
                return false;
            }
            case HOLD -> {
                if (sneak) { launch(p, st, link, cooldown); return true; }
                String refused = link.land(p, st.body);
                if (refused != null) return feedback(p, "land_" + refused);
                var b = link.body(p.serverLevel(), st.body);
                send(p, st, LANDED, b == null ? p.position() : vec(b.center()), null, null, Vec3.ZERO, st.blocks);
                finishHold(p, st, cooldown);
                return true;
            }
        }
        return false;
    }

    /** Re-checks the selection, charges the grab and takes the structure into the hold. Shared by the instant grab and the end of a dash. */
    private static boolean commitGrab(ServerPlayer p, State st, ConstructLink link, double baseCp, ConstructLink.Selection again) {
        double lv = level(p);
        double cost = ConstructRules.startCost(baseCp, again.blocks());
        if (!HighPowerResources.trySpend(p, HighPowerSkills.VECTOR_CONSTRUCT, cost, 1)) { if (st.phase == Phase.DASH) cancel(p, st); return feedback(p, "not_ready"); }
        var grab = link.grab(p, again, ConstructRules.maxBlocks(lv), ConstructRules.maxSpan(lv));
        if (!grab.ok()) { cancel(p, st); return feedback(p, grab.reason()); }
        hold(p, st, link, grab.body(), grab.blocks(), grab.span(), grab.mass());
        return true;
    }

    private static void stopDash(ServerPlayer p) { p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0); p.hurtMarked = true; }

    /** Crouch in place, then run at the dash stand point at {@link ConstructRules#DASH_SPEED}; take hold once close enough. */
    private static void tickDash(ServerPlayer p, State st, ConstructLink link, double baseCp, boolean ready) {
        long age = now(p) - st.dashStart;
        if (!p.isAlive() || !ready) { stopDash(p); cancel(p, st); feedback(p, "not_ready"); return; }
        var sel = st.selection;
        var min = new cn.academy.port.highpower.core.V3(sel.min()[0], sel.min()[1], sel.min()[2]);
        var max = new cn.academy.port.highpower.core.V3(sel.max()[0], sel.max()[1], sel.max()[2]);
        double gap = ConstructRules.dashGap(p.getX(), p.getZ(), min, max);
        boolean arrived = age >= ConstructRules.DASH_CROUCH_TICKS && gap <= ConstructRules.DASH_ARRIVE;
        if (age >= ConstructRules.DASH_CROUCH_TICKS && !arrived) {
            double dx = st.dashStand.x - p.getX(), dz = st.dashStand.z - p.getZ(), d = Math.hypot(dx, dz);
            if (d < 1e-6) arrived = true;
            else {
                double step = Math.min(ConstructRules.DASH_SPEED, d);
                p.setDeltaMovement(dx / d * step, p.getDeltaMovement().y, dz / d * step); p.hurtMarked = true; p.fallDistance = 0;
            }
        }
        if (!arrived && age > ConstructRules.DASH_MAX_TICKS) {
            // Blocked on the way: grab only if the structure has come within the grab range anyway, otherwise give up for free.
            arrived = gap <= ConstructRules.DASH_REACH;
            if (!arrived) { stopDash(p); cancel(p, st); feedback(p, "blocked"); return; }
        }
        if (arrived) {
            stopDash(p);
            var again = link.select(p, sel.origin(), ConstructRules.maxBlocks(level(p)), ConstructRules.maxSpan(level(p)));
            if (!again.ok() || again.blocks() != sel.blocks()) { cancel(p, st); feedback(p, again.ok() ? "changed" : again.reason()); return; }
            commitGrab(p, st, link, baseCp, again);
            return;
        }
        if (age % 2 == 0) send(p, st, DASH, vec(sel.center()), vec(sel.min()), vec(sel.max()), st.dashStand, sel.blocks());
    }

    private static boolean takeExisting(ServerPlayer p, State st, ConstructLink link, UUID body, double baseCp) {
        if (holding() >= ConstructRules.SERVER_MAX) return feedback(p, "server_busy");
        var b = link.body(p.serverLevel(), body);
        if (b == null) return feedback(p, "no_target");
        for (State other : STATES.values()) if (other != st && body.equals(other.body)) return feedback(p, "controlled");
        int span = (int) Math.ceil(Math.max(b.max()[0] - b.min()[0], Math.max(b.max()[1] - b.min()[1], b.max()[2] - b.min()[2])));
        if (span > ConstructRules.maxSpan(level(p))) return feedback(p, "too_large");
        if (!HighPowerResources.trySpend(p, HighPowerSkills.VECTOR_CONSTRUCT, ConstructRules.startCost(baseCp, 1), 1)) return feedback(p, "not_ready");
        st.token = ++tokens;
        hold(p, st, link, body, 0, span, b.mass());
        return true;
    }

    private static void hold(ServerPlayer p, State st, ConstructLink link, UUID body, int blocks, int span, double mass) {
        var b = link.body(p.serverLevel(), body);
        st.phase = Phase.HOLD; st.body = body; st.blocks = blocks; st.span = Math.max(1, span); st.mass = mass;
        st.yaw0 = b == null ? 0 : b.yaw(); st.playerYaw0 = p.getYRot(); st.nextUpkeep = now(p) + 10; st.selection = null;
        st.holdSince = now(p); st.grabFrom = b == null ? null : vec(b.center());
        // Circumscribed footprint radius and height decide how far the centre sits from the hand. Sable may not have the bounding box
        // ready on the very tick of the grab, so fall back to the span and take the real size from the first tick that has one.
        st.radius = st.span * .7; st.height = st.span; st.dimsKnown = false;
        measure(st, b);
        steer(p, st, link);
    }

    private static void measure(State st, ConstructLink.Body b) {
        if (st.dimsKnown || b == null) return;
        double w = b.max()[0] - b.min()[0], d = b.max()[2] - b.min()[2], h = b.max()[1] - b.min()[1];
        if (!(w >= .5 && d >= .5 && h >= .5) || !Double.isFinite(w + d + h)) return;
        st.radius = Math.hypot(w, d) / 2; st.height = h; st.dimsKnown = true;
    }

    private static Vec3 target(ServerPlayer p, State st) {
        Vec3 eye = p.getEyePosition();
        var t = ConstructRules.holdTarget(new cn.academy.port.highpower.core.V3(eye.x, eye.y, eye.z), Math.toRadians(p.getYRot()),
                p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT, p.getY(), st.radius, st.height);
        Vec3 target = new Vec3(t.x(), t.y(), t.z());
        // The structure is seized where it stands and drawn into the hand over a few ticks rather than snapped there.
        if (st.grabFrom != null) {
            var v = ConstructRules.drawIn(new cn.academy.port.highpower.core.V3(st.grabFrom.x, st.grabFrom.y, st.grabFrom.z), t,
                    new cn.academy.port.highpower.core.V3(p.getX(), p.getY(), p.getZ()), Math.toRadians(p.getYRot()),
                    (now(p) - st.holdSince) / (double) ConstructRules.GRAB_EASE_TICKS);
            target = new Vec3(v.x(), v.y(), v.z());
        }
        return target;
    }

    private static void steer(ServerPlayer p, State st, ConstructLink link) {
        Vec3 t = target(p, st);
        double yaw = st.yaw0 - Math.toRadians(p.getYRot() - st.playerYaw0);
        link.control(p.serverLevel(), st.body, new double[] {t.x, t.y, t.z}, yaw, ConstructRules.maxAccel(level(p)));
    }

    private static void launch(ServerPlayer p, State st, ConstructLink link, int cooldown) {
        double speed = ConstructRules.throwSpeed(level(p));
        // Fly from the structure to the point under the crosshair, not parallel to the view: the held body sits off the eye line.
        Vec3 aimPoint = link.throwAim(p, st.body, 64);
        var held = link.body(p.serverLevel(), st.body);
        Vec3 center = held == null ? p.getEyePosition() : vec(held.center()), eye = p.getEyePosition(), view = p.getLookAngle();
        var direction = ConstructRules.throwDirection(new cn.academy.port.highpower.core.V3(center.x, center.y, center.z),
                new cn.academy.port.highpower.core.V3(eye.x, eye.y, eye.z), new cn.academy.port.highpower.core.V3(view.x, view.y, view.z),
                aimPoint == null ? null : new cn.academy.port.highpower.core.V3(aimPoint.x, aimPoint.y, aimPoint.z), 64);
        if (direction.length() < 1e-9) return;
        Vec3 look = new Vec3(direction.x(), direction.y(), direction.z());
        if (link.launch(p.serverLevel(), st.body, new double[] {look.x, look.y, look.z}, speed)) {
            THROWN_BODIES.put(st.body, new Thrown(p.getUUID(), p.getId(), Math.max(1, st.token), p.serverLevel(), now(p) + ConstructRules.THROW_HIT_TICKS, speed, st.mass,HighPowerGrowth.values(p).damage()));
            var b = link.body(p.serverLevel(), st.body);
            send(p, st, THROWN, b == null ? p.position() : vec(b.center()), b == null ? null : vec(b.min()), b == null ? null : vec(b.max()), look, st.blocks);
        }
        finishHold(p, st, cooldown);
    }

    private static void finishHold(ServerPlayer p, State st, int cooldown) {
        ConstructLink link = ConstructLink.get();
        if (link != null && st.body != null) link.release(st.body);
        st.phase = Phase.IDLE; st.body = null; st.selection = null;
        HighPowerResources.trySpend(p, HighPowerSkills.VECTOR_CONSTRUCT, 0, Math.max(1, cooldown));
    }

    private static void cancel(ServerPlayer p, State st) {
        st.phase = Phase.IDLE; st.selection = null;
        send(p, st, END, p.position(), null, null, Vec3.ZERO, 0);
    }

    /** Drops control without touching the structure: Sable keeps it as a free, persisted body the owner can grab again. */
    private static void drop(ServerPlayer p) {
        State st = STATES.remove(p.getUUID());
        ConstructLink link = ConstructLink.get();
        if (st != null && st.phase == Phase.HOLD && link != null && st.body != null) link.release(st.body);
    }

    // ---- ticking ---------------------------------------------------------------------------------------------------------------

    static void tick(ServerPlayer p, boolean ready, double upkeepBase, int cooldown) {
        State st = STATES.get(p.getUUID());
        if (st == null) return;
        ConstructLink link = ConstructLink.get();
        if (link == null) { STATES.remove(p.getUUID()); return; }
        if (st.phase == Phase.PREVIEW && now(p) > st.previewUntil) { cancel(p, st); return; }
        if (st.phase == Phase.DASH) { tickDash(p, st, link, upkeepBase, ready); return; }
        if (st.phase != Phase.HOLD) return;
        var b = link.body(p.serverLevel(), st.body);
        if (b == null || !p.isAlive()) { st.phase = Phase.IDLE; if (st.body != null) link.release(st.body); st.body = null; feedback(p, "lost"); return; }
        if (!ready) { safeStop(p, st, link, cooldown); return; }
        measure(st, b);
        steer(p, st, link);
        st.mass = b.mass();
        if (now(p) >= st.nextUpkeep) {
            st.nextUpkeep = now(p) + 10;
            double cost = ConstructRules.upkeep(upkeepBase, b.mass(), link.effort(st.body)) * 10;
            if (!Double.isFinite(cost) || !HighPowerResources.trySpend(p, HighPowerSkills.VECTOR_CONSTRUCT, cost, 1)) { safeStop(p, st, link, cooldown); return; }
        }
        if (now(p) % 2 == 0) {
            Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(.6)).add(0, -.35, 0);
            send(p, st, HOLD, vec(b.center()), vec(b.min()), vec(b.max()), hand, st.blocks);
        }
    }

    /** Out of budget or no longer allowed: try to set it down where it is; if that is refused, let it go as a free body. */
    private static void safeStop(ServerPlayer p, State st, ConstructLink link, int cooldown) {
        String refused = link.land(p, st.body);
        feedback(p, refused == null ? "budget_landed" : "budget_drop");
        send(p, st, refused == null ? LANDED : END, p.position(), null, null, Vec3.ZERO, st.blocks);
        finishHold(p, st, cooldown);
    }

    private static void tickThrown(net.minecraft.server.MinecraftServer server) {
        tickEntombs(server);
        if (THROWN_BODIES.isEmpty()) return;
        ConstructLink link = ConstructLink.get();
        if (link == null) { THROWN_BODIES.clear(); return; }
        for (var it = THROWN_BODIES.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next(); Thrown t = e.getValue();
            if (t.level.getGameTime() > t.until) { it.remove(); continue; }
            var b = link.body(t.level, e.getKey());
            if (b == null) { it.remove(); continue; }
            ServerPlayer owner = server.getPlayerList().getPlayer(t.owner);
            if (owner == null || owner.serverLevel() != t.level) continue;
            double speed = Math.sqrt(b.velocity()[0] * b.velocity()[0] + b.velocity()[1] * b.velocity()[1] + b.velocity()[2] * b.velocity()[2]);
            double scale = ConstructRules.impactScale(b.mass(), speed, t.speed);
            java.util.List<LivingEntity> struck = new ArrayList<>();
            if (scale > 0) for (LivingEntity target : link.touching(t.level, e.getKey(), .35)) {
                if (target == owner || !t.hit.add(target.getUUID())) continue;
                BigDecimal raw = t.damage.multiply(BigDecimal.valueOf(scale));
                if (HighPowerCombat.attackEmission(new ImpactPermit(owner,e.getKey(),t,target,raw))) {
                    Vec3 push = new Vec3(b.velocity()[0], Math.max(.2, b.velocity()[1]), b.velocity()[2]).normalize().scale(Math.min(2.5, .3 + speed * .06));
                    target.push(push.x, push.y, push.z);
                    target.hurtMarked = true;
                    struck.add(target);
                }
            }
            // A flight ends on its first impact: an enemy struck, or the structure slamming into the ground and losing most of its speed.
            boolean terrain = struck.isEmpty() && t.level.getGameTime() - t.born >= ConstructRules.SHATTER_MIN_FLIGHT_TICKS && ConstructRules.slammed(speed, t.last);
            t.last = speed;
            if (!struck.isEmpty() || terrain) { shatterBody(server, owner, t, e.getKey(), b, struck, link); it.remove(); }
        }
    }

    // ---- shatter and lock --------------------------------------------------------------------------------------------------------

    /** The thrown structure breaks apart; pieces of it fly to each struck enemy and become ordinary blocks around them. */
    private static void shatterBody(net.minecraft.server.MinecraftServer server, ServerPlayer owner, Thrown t, UUID body, ConstructLink.Body b, java.util.List<LivingEntity> struck, ConstructLink link) {
        var palette = struck.isEmpty() ? java.util.List.<net.minecraft.world.level.block.state.BlockState>of() : link.palette(t.level, body, 48);
        Vec3 c = vec(b.center()), v = new Vec3(b.velocity()[0], b.velocity()[1], b.velocity()[2]);
        Vec3 dir = v.lengthSqr() > 1e-9 ? v.normalize() : Vec3.ZERO;
        sendVisual(t.level, t.ownerId, t.owner, t.token, SHATTER, c, vec(b.min()), vec(b.max()), dir, 0);
        for (LivingEntity target : struck) {
            var box = target.getBoundingBox();
            sendVisual(t.level, t.ownerId, t.owner, t.token, ENTOMB, box.getCenter(), new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ), c, 0);
            lockTarget(t.level, t.owner, target.getUUID(), palette);
        }
        link.shatter(t.level, body);
    }

    /** Schedules the lock: after the gather time, blocks drawn from {@code palette} are placed in the shell around the target for a few seconds. */
    static void lockTarget(ServerLevel level, UUID owner, UUID target, java.util.List<net.minecraft.world.level.block.state.BlockState> palette) {
        long at = level.getGameTime() + ConstructRules.ENTOMB_GATHER_TICKS;
        ENTOMBS.add(new Entomb(level, owner, target, at, at + ConstructRules.ENTOMB_HOLD_TICKS, palette));
    }

    /** Test seam for the lock alone, without a thrown body: the same scheduling a real hit uses. */
    public static void lockForTest(ServerPlayer owner, LivingEntity target, java.util.List<net.minecraft.world.level.block.state.BlockState> palette) {
        lockTarget(owner.serverLevel(), owner.getUUID(), target.getUUID(), palette);
    }

    private static boolean lockable(net.minecraft.world.level.block.state.BlockState state, ServerLevel level, net.minecraft.core.BlockPos pos) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity() && !(state.getBlock() instanceof net.minecraft.world.level.block.Fallable)
                && state.isCollisionShapeFullBlock(level, pos);
    }

    private static int activeBlocks() { int n = 0; for (Entomb e : ENTOMBS) n += e.placed.size(); return n; }

    private static void tickEntombs(net.minecraft.server.MinecraftServer server) {
        if (ENTOMBS.isEmpty()) return;
        for (var it = ENTOMBS.iterator(); it.hasNext(); ) {
            Entomb e = it.next(); long now = e.level.getGameTime();
            if (e.placed.isEmpty() && !e.done) {
                if (now < e.placeAt) continue;
                e.done = true;
                placeCage(server, e);
                if (e.placed.isEmpty()) { it.remove(); continue; }
            }
            if (!e.placed.isEmpty() && now >= e.restoreAt && restoreCage(e, true, now)) it.remove();
        }
    }

    private static void placeCage(net.minecraft.server.MinecraftServer server, Entomb e) {
        ServerPlayer owner = server.getPlayerList().getPlayer(e.owner);
        var entity = e.level.getEntity(e.target);
        if (owner == null || owner.serverLevel() != e.level || !(entity instanceof LivingEntity target) || !target.isAlive()) return;
        var box = target.getBoundingBox();
        var cells = ConstructRules.cage(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        if (cells.isEmpty() || activeBlocks() + cells.size() > ConstructRules.ENTOMB_MAX_ACTIVE_BLOCKS) return;
        var random = e.level.random;
        for (int[] cell : cells) {
            var pos = new net.minecraft.core.BlockPos(cell[0], cell[1], cell[2]);
            if (!e.level.isLoaded(pos) || e.level.isOutsideBuildHeight(pos)) continue;
            var there = e.level.getBlockState(pos);
            if (!there.isAir() && !there.canBeReplaced()) continue;
            net.minecraft.world.level.block.state.BlockState state = null;
            for (int tries = 0; tries < 6 && !e.palette.isEmpty(); tries++) {
                var candidate = e.palette.get(random.nextInt(e.palette.size()));
                if (lockable(candidate, e.level, pos)) { state = candidate; break; }
            }
            if (state == null) state = net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState();
            if (!e.level.isUnobstructed(state, pos, net.minecraft.world.phys.shapes.CollisionContext.empty())) continue;
            if (!e.level.mayInteract(owner, pos) || owner.blockActionRestricted(e.level, pos, owner.gameMode.getGameModeForPlayer())
                    || net.neoforged.neoforge.event.EventHooks.onBlockPlace(owner, net.neoforged.neoforge.common.util.BlockSnapshot.create(e.level.dimension(), e.level, pos), net.minecraft.core.Direction.UP)) continue;
            if (e.level.setBlock(pos, state, 3)) e.placed.add(new Placed(pos.immutable(), state));
        }
        if (!e.placed.isEmpty()) {
            target.setDeltaMovement(Vec3.ZERO); target.hurtMarked = true;
            var first = e.placed.getFirst();
            e.level.playSound(null, first.pos(), first.state().getSoundType().getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1.4f, .8f);
        }
    }

    /** Breaks away the blocks that are still the ones placed (never anything a player put there since). True when nothing is left to retry. */
    private static boolean restoreCage(Entomb e, boolean withEffects, long now) {
        if (e.gaveUpAt == 0) e.gaveUpAt = now + 2400;
        boolean pending = false;
        for (var it = e.placed.iterator(); it.hasNext(); ) {
            Placed p = it.next();
            if (!e.level.isLoaded(p.pos())) { pending = true; continue; }
            if (e.level.getBlockState(p.pos()) == p.state()) {
                if (withEffects) e.level.destroyBlock(p.pos(), false); else e.level.removeBlock(p.pos(), false);
            }
            it.remove();
        }
        return !pending || now > e.gaveUpAt;
    }

    @SubscribeEvent public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent e) {
        for (Entomb en : ENTOMBS) restoreCage(en, false, en.level.getGameTime());
        ENTOMBS.clear();
    }

    /** Created only after the real physics backend reports contact and the target's one-hit fence is claimed. */
    public static final class ImpactPermit implements AbilityEmission {
        private final ServerPlayer player;
        private final UUID body;
        private final Thrown thrown;
        private final LivingEntity target;
        private final Vec3 position;
        private final BigDecimal damage;
        private boolean claimed;
        private ImpactPermit(ServerPlayer player,UUID body,Thrown thrown,LivingEntity target,BigDecimal damage) {
            this.player=player; this.body=body; this.thrown=thrown; this.target=target; this.position=target.position(); this.damage=damage;
        }
        public ServerPlayer player() { return player; }
        public LivingEntity target() { return target; }
        public String skill() { return DAMAGE_KEY; }
        public BigDecimal damage() { return damage; }
        public boolean claim() { if(claimed || !valid()) return false; claimed=true; return true; }
        public boolean valid() {
            if(!player.server.isSameThread()) return false;
            var state=cn.academy.port.AbilityStorage.get(player);
            return player.server.isSameThread() && player.isAlive() && !player.hasDisconnected() && player.serverLevel()==thrown.level
                    && THROWN_BODIES.get(body)==thrown && thrown.owner.equals(player.getUUID()) && now(player)<=thrown.until
                    && thrown.hit.contains(target.getUUID()) && target.level()==thrown.level && thrown.level.getEntity(target.getId())==target
                    && target.position().equals(position) && state.activated && state.overloadFine && !state.interfering
                    && HighPowerCategories.VECMANIP.equals(state.category) && state.level>=4 && state.learned(HighPowerSkills.VECTOR_CONSTRUCT)
                    && cn.academy.port.SkillCatalog.enabled(state.category,HighPowerSkills.VECTOR_CONSTRUCT) && HighPowerResources.ready(player);
        }
    }

    public static void reset(ServerPlayer p) { drop(p); }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e) { tickThrown(e.getServer()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) drop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) drop(p); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) drop(p); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { STATES.clear(); THROWN_BODIES.clear(); ENTOMBS.clear(); }

    /** Test/inspection: whether the player is currently holding a body, and which. */
    public static UUID heldBody(ServerPlayer p) { State s = STATES.get(p.getUUID()); return s == null || s.phase != Phase.HOLD ? null : s.body; }
    public static boolean previewing(ServerPlayer p) { State s = STATES.get(p.getUUID()); return s != null && s.phase == Phase.PREVIEW; }
    /** Test/inspection: the hold target and measured size, as steered now. */
    public static String probe(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.phase != Phase.HOLD) return "not holding";
        return "target=" + target(p, s) + " radius=" + s.radius + " height=" + s.height + " known=" + s.dimsKnown;
    }
    public static boolean dashing(ServerPlayer p) { State s = STATES.get(p.getUUID()); return s != null && s.phase == Phase.DASH; }

    // ---- network ---------------------------------------------------------------------------------------------------------------

    private static Vec3 vec(double[] v) { return v == null ? null : new Vec3(v[0], v[1], v[2]); }

    private static void send(ServerPlayer p, State st, int phase, Vec3 center, Vec3 min, Vec3 max, Vec3 aux, int blocks) {
        sendVisual(p.serverLevel(), p.getId(), p.getUUID(), Math.max(1, st.token), phase, center, min, max, aux, blocks);
    }

    private static void sendVisual(ServerLevel level, int entityId, UUID owner, long token, int phase, Vec3 center, Vec3 min, Vec3 max, Vec3 aux, int blocks) {
        if (!finite(center)) return;
        Visual v = new Visual(entityId, owner, Math.max(1, token), phase, center, finite(min) ? min : center, finite(max) ? max : center,
                finite(aux) ? aux : Vec3.ZERO, Math.max(0, blocks), level.dimension().location());
        for (ServerPlayer o : level.players())
            if (o.distanceToSqr(center) <= 128 * 128 && o.connection != null && !o.hasDisconnected()) PacketDistributor.sendToPlayer(o, v);
    }

    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z) && Math.abs(v.x) <= 3e7 && Math.abs(v.y) <= 3e7 && Math.abs(v.z) <= 3e7; }

    /** Server-to-client decoration cue; {@code aux} is the hand position while holding and the throw direction when thrown. */
    public record Visual(int entityId, UUID owner, long token, int phase, Vec3 center, Vec3 min, Vec3 max, Vec3 aux, int blocks, ResourceLocation dimension) implements CustomPacketPayload {
        public Visual {
            if (entityId < 0 || owner == null || token <= 0 || phase < PREVIEW || phase > ENTOMB || !finite(center) || !finite(min) || !finite(max) || !finite(aux)
                    || blocks < 0 || blocks > ConstructRules.ABS_MAX_BLOCKS || dimension == null) throw new IllegalArgumentException("construct visual");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "construct_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entityId); b.writeUUID(v.owner); b.writeLong(v.token); b.writeVarInt(v.phase);
            for (Vec3 x : new Vec3[] {v.center, v.min, v.max, v.aux}) { b.writeDouble(x.x); b.writeDouble(x.y); b.writeDouble(x.z); }
            b.writeVarInt(v.blocks); b.writeResourceLocation(v.dimension);
        }, b -> {
            int id = b.readInt(); UUID owner = b.readUUID(); long token = b.readLong(); int phase = b.readVarInt();
            Vec3[] xs = new Vec3[4];
            for (int i = 0; i < 4; i++) xs[i] = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
            return new Visual(id, owner, token, phase, xs[0], xs[1], xs[2], xs[3], b.readVarInt(), b.readResourceLocation());
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) {
            e.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (payload, context) -> clientReceiver.accept(payload));
        }
    }
}

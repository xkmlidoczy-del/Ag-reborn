/* AcademyCraft 1.0.7 VecDeviation/EntityAffection adaptation. GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.AcademyNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import java.util.*;

/** Source toggle-on-press, no key-up termination, no cooldown, and global first-live player damage reduction. */
@EventBusSubscriber(modid = "academy")
public final class VecDeviation {
    public static final String ID = VecDeviationSession.ID, MARK = "ac_vm_deviated";
    private static final Map<UUID, Hold> HOLDS = new LinkedHashMap<>();
    private static final Map<UUID, Long> INPUTS = new HashMap<>();
    private static final Set<UUID> TICKING = new HashSet<>();
    private static long nextToken;
    private VecDeviation() {}
    public static boolean start(ServerPlayer p) {if(cn.academy.port.AbilityConsumption.busy(p))return false; return start(p, 0); }
    /** Source second physical press toggles the live context off. Nonce rejects replay of that press. */
    public static boolean start(ServerPlayer p, long nonce) {if(cn.academy.port.AbilityConsumption.busy(p))return false;
        if (!VectorStarterSupport.ready(p) || nonce < 0 || nonce > 0 && nonce <= INPUTS.getOrDefault(p.getUUID(), 0L)
                || TICKING.contains(p.getUUID())) return false;
        var old = HOLDS.get(p.getUUID());
        if (old != null && valid(old)) {
            if (nonce > 0) INPUTS.put(p.getUUID(), nonce); finish(old); return true;
        }
        if (old != null) remove(p);
        var session = VecDeviationSession.begin(AbilityStorage.get(p), p.getAbilities().instabuild); if (session == null) return false;
        var h = new Hold(p, session, ++nextToken, nonce); HOLDS.put(p.getUUID(), h);
        if (nonce > 0) INPUTS.put(p.getUUID(), nonce); send(h, "vec_deviation_start"); return true;
    }
    public static void tick(ServerPlayer p) {
        if (!VectorStarterSupport.serverThread(p)) return;
        var h = HOLDS.get(p.getUUID()); if (h == null) return;
        if (!valid(h)) { remove(p); return; }
        var state = h.session.state();
        if (!state.overloadFine) { finish(h); return; } // Source toggle survives activation/interference delegate aborts.
        long now = p.serverLevel().getGameTime(); if (now <= h.lastTick || !TICKING.add(p.getUUID())) return; h.lastTick = now;
        try {
            if (h.session.beginTick(p.getAbilities().instabuild)) {
                double radius = SkillTuning.number(ID, "radius");
                // Original WorldUtils excludes null: caster is intentionally among the candidates.
                var candidates = new ArrayList<>(p.serverLevel().getEntities((Entity) null,
                        new AABB(p.position(), p.position()).inflate(radius), e -> e.distanceToSqr(p) <= radius * radius));
                if (!candidates.contains(p)) candidates.add(p); // Same semantics for non-listed native test players.
                candidates.removeAll(h.visited);
                for (var target : candidates) {
                    if (!h.session.active() || !p.isAlive() || p.isRemoved()) break; // Native lifecycle callback fence.
                    if (marked(target) || excluded(target)) continue;
                    h.session.affect(difficulty(target),p.getAbilities().instabuild);if(HOLDS.get(p.getUUID())!=h||!valid(h)||target.isRemoved()||target.level()!=h.world)break;
                    Vec3 at = target.getEyePosition();
                    boolean wave = false;
                    if (target instanceof LargeFireball fireball) {
                        int power = fireball.saveWithoutId(new CompoundTag()).getInt("ExplosionPower");
                        fireball.discard();
                        // Native explosion hooks retain damage/block protection. Source owner is null, fire=true.
                        boolean protectedCity=cn.academy.port.highpower.environment.SkillEnvironment.protectedCity(p.serverLevel());
                        p.serverLevel().explode(null, fireball.getX(), fireball.getY(), fireball.getZ(), power, !protectedCity,
                                protectedCity ? Level.ExplosionInteraction.NONE : Level.ExplosionInteraction.MOB);
                    } else if (target instanceof SmallFireball) target.discard();
                    else {
                        if (target instanceof AbstractArrow arrow) arrow.setBaseDamage(0);
                        target.setDeltaMovement(Vec3.ZERO); target.hasImpulse = true; target.hurtMarked = true;
                        target.getPersistentData().putBoolean(MARK, true); wave = true;
                    }
                    var tag = VectorStarterSupport.packet(p, "vec_deviation_stop", h.token, h.input);
                    tag.putLong("sequence", ++h.sequence); tag.putInt("target", target.getId()); tag.putBoolean("wave", wave);
                    VectorStarterSupport.position(tag, at); tag.putFloat("yaw", p.getYRot()); tag.putFloat("pitch", p.getXRot());
                    VectorStarterSupport.send(h.audience, tag);
                }
                // Includes excluded and already-marked entities; exiting and reentering never retries this context.
                h.visited.addAll(candidates);
                h.session.endTick(p.getAbilities().instabuild);
            }
        } finally { TICKING.remove(p.getUUID()); }
        if (h.session.ending()) finish(h);
    }
    /** Classic EntityLiving/Mob/Monster mappings exclude native mobs, dropped items, and XP orbs only. */
    public static boolean excluded(Entity e) { return ClassicEntityAffectionConfiguration.isExcluded(e); }
    public static float difficulty(Entity e) { return ClassicEntityAffectionConfiguration.difficultyOf(e); }
    public static boolean marked(Entity e) { return e.getPersistentData().getBoolean(MARK); }
    @SubscribeEvent public static void hurt(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide || !(event.getEntity() instanceof Player)
                || !event.getEntity().getServer().isSameThread()) return;
        Hold chosen = null;
        for (var h : new ArrayList<>(HOLDS.values())) {
            if (h.player.getServer() != event.getEntity().getServer()) continue;
            if (!valid(h)) { remove(h.player); continue; }
            if (chosen == null) chosen = h;
        }
        if (chosen == null) return;
        // Source does not compare harmed player to context owner, and accepts environmental damage.
        if(chosen.session.state().consumptionInProgress())return;float reduced=chosen.session.reduceDamage(event.getAmount(),chosen.player.getAbilities().instabuild);if(HOLDS.get(chosen.player.getUUID())!=chosen||!valid(chosen))return;
        event.setAmount(reduced);
        var tag = VectorStarterSupport.packet(chosen.player, "vec_deviation_sound", chosen.token, chosen.input);
        tag.putLong("sequence", ++chosen.sequence); VectorStarterSupport.position(tag, chosen.player.position()); VectorStarterSupport.send(chosen.audience, tag);
        AbilityStorage.save(chosen.player); AcademyNetwork.sync(chosen.player);
    }
    /** Key-up is a no-op for contextActivate. Matching nonce is still required by wire ingress. */
    public static boolean release(ServerPlayer p, long nonce) {
        var h = p == null ? null : HOLDS.get(p.getUUID());
        return VectorStarterSupport.serverThread(p) && h != null && (nonce == 0 || nonce == h.input) && valid(h);
    }
    public static boolean release(ServerPlayer p) { return release(p, 0); }
    public static void abort(ServerPlayer p) { abort(p, 0); }
    public static boolean abort(ServerPlayer p, long nonce) {
        if (!VectorStarterSupport.serverThread(p)) return false;
        var h = HOLDS.get(p.getUUID()); if (h == null || nonce > 0 && nonce != h.input) return false;
        finish(h); return true;
    }
    public static void remove(ServerPlayer p) {
        if (!VectorStarterSupport.serverThread(p)) return;
        var h = HOLDS.get(p.getUUID()); if (h != null) finish(h); INPUTS.remove(p.getUUID()); TICKING.remove(p.getUUID());
    }
    private static boolean valid(Hold h) {
        var p = h.player; var s = AbilityStorage.get(p);
        return VectorStarterSupport.ready(p) && h.world == p.serverLevel() && h.session.state() == s && h.session.active()
                && s.category.equals("vecmanip") && s.level >= 2 && s.learned(ID)
                && Double.isFinite(s.cp) && Double.isFinite(s.overload) && Double.isFinite(s.exp(ID)) && p.serverLevel().getGameTime() >= h.lastTick;
    }
    private static void finish(Hold h) { if (HOLDS.remove(h.player.getUUID(), h)) { h.session.discard(); send(h, "vec_deviation_end"); } }
    private static void send(Hold h, String kind) { VectorStarterSupport.send(h.audience, VectorStarterSupport.packet(h.player, kind, h.token, h.input)); }
    public static boolean active(ServerPlayer p) { return p != null && HOLDS.containsKey(p.getUUID()); }
    public static int heldTicks(ServerPlayer p) { var h = p == null ? null : HOLDS.get(p.getUUID()); return h == null ? 0 : h.session.ticks(); }
    public static void clear() { HOLDS.clear(); INPUTS.clear(); TICKING.clear(); nextToken = 0; }
    private static final class Hold {
        final ServerPlayer player; final ServerLevel world; final VecDeviationSession session;
        final long token, input; final Set<ServerPlayer> audience; final Set<Entity> visited = Collections.newSetFromMap(new IdentityHashMap<>()); long lastTick, sequence;
        Hold(ServerPlayer p, VecDeviationSession session, long token, long input) {
            player = p; world = p.serverLevel(); this.session = session; this.token = token; this.input = input;
            audience = VectorStarterSupport.audience(p); lastTick = world.getGameTime() - 1;
        }
    }
    /** Modern pending-start cancellation derives the token from the exact skill/input Hold. */
    public static boolean abortPendingContext(ServerPlayer p, long input) {
        if (!VectorStarterSupport.serverThread(p) || input <= 0) return false;
        var h = HOLDS.get(p.getUUID());
        return h != null && h.input == input && h.token > 0 && abortContext(p, input, h.token);
    }
    /** Strict authenticated context terminal: independent of current preset mappings. */
    public static boolean abortContext(ServerPlayer p, long input, long token) {
        if (!VectorStarterSupport.serverThread(p) || p.hasDisconnected() || !p.isAlive() || p.isRemoved()
                || p.isSpectator() || cn.academy.port.AbilityConsumption.busy(p) || TICKING.contains(p.getUUID())
                || p.serverLevel().getEntity(p.getId()) != p) return false;
        var h = HOLDS.get(p.getUUID());
        if (h == null || !cn.academy.port.core.TargetedContextTermination.matches(
                new cn.academy.port.core.TargetedContextTermination.Binding(h.player, h.world, h.session.state(), h.input, h.token),
                p, p.serverLevel(), AbilityStorage.get(p), input, token)
                || !valid(h) || h.session.ending() || h.session.state().level > 5) return false;
        finish(h); return true;
    }
}

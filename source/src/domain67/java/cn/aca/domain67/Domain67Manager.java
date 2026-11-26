package cn.aca.domain67;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side engine for Domain Expansion: Restaurant 67 (v1.2.0 Anti-Spam & Heavy Rolling Log Edition).
 * - Strict anti-spam hint throttling (`sendThrottledHint`) — zero chat history spam and zero repeat actionbar flood!
 * - Larger, heavier, floor-rolling Cursed Logs (`spawnOnStage` with `dom.center.y` floor clamping & 45-54 tick presence).
 */
public final class Domain67Manager {
    public static final float DOMAIN_RADIUS = 32.0f;
    public static final int STAGE_BLOCK_RADIUS = 26;
    public static final int DOMAIN_DURATION_TICKS = 360; // 18 seconds
    public static final int BURNOUT_COOLDOWN_TICKS = 160; // 8 seconds
    public static final int BROADCAST_COOLDOWN_TICKS = 100; // 5 seconds
    public static final int FINISHER_COOLDOWN_TICKS = 50; // 2.5 seconds
    public static final int BENTO_COOLDOWN_TICKS = 70; // 3.5 seconds

    private static final Map<UUID, ActiveDomain> ACTIVE_DOMAINS = new HashMap<>();
    private static final Map<UUID, Long> DOMAIN_READY_AT = new HashMap<>();
    private static final Map<UUID, Long> BROADCAST_READY_AT = new HashMap<>();
    private static final Map<UUID, Long> FINISHER_READY_AT = new HashMap<>();
    private static final Map<UUID, Long> BENTO_READY_AT = new HashMap<>();
    private static final Map<UUID, Long> LAST_HINT_AT = new HashMap<>();
    private static final Map<UUID, CircusLureState> CIRCUS_LURED = new HashMap<>();

    private Domain67Manager() {}

    public static final class CircusLureState {
        public final UUID ownerId;
        public final ResourceKey<Level> dim;
        public final Vec3 lurePoint;
        public final long expireTick;

        public CircusLureState(UUID ownerId, ResourceKey<Level> dim, Vec3 lurePoint, long expireTick) {
            this.ownerId = ownerId;
            this.dim = dim;
            this.lurePoint = lurePoint;
            this.expireTick = expireTick;
        }
    }

    public static final class DinerState {
        public final UUID entityId;
        public final Vec3 seatPos;
        public float noodlesRemaining;
        public final float noodlesMax;
        public final float eatRatePerTick;
        public boolean finishedNoReward;
        public long lastGuardLogTick = Long.MIN_VALUE;

        public DinerState(UUID entityId, Vec3 seatPos, float noodlesMax, float eatRatePerTick) {
            this.entityId = entityId;
            this.seatPos = seatPos;
            this.noodlesMax = noodlesMax;
            this.noodlesRemaining = noodlesMax;
            this.eatRatePerTick = eatRatePerTick;
        }

        public float progress() {
            return Mth.clamp(1.0f - (noodlesRemaining / Math.max(1.0f, noodlesMax)), 0.0f, 1.0f);
        }
    }

    public static final class ActiveDomain {
        public final UUID ownerId;
        public final ResourceKey<Level> dimension;
        public final Vec3 center;
        public final BlockPos stageOrigin;
        public final float radius;
        public final long startTick;
        public final long endTick;
        public boolean waveTransformationStarted = false;
        public long lastCueBroadcastTick = Long.MIN_VALUE;
        public final Map<UUID, DinerState> diners = new HashMap<>();

        public ActiveDomain(UUID ownerId, ResourceKey<Level> dimension, Vec3 center, BlockPos stageOrigin,
                            float radius, long startTick, long endTick) {
            this.ownerId = ownerId;
            this.dimension = dimension;
            this.center = center;
            this.stageOrigin = stageOrigin;
            this.radius = radius;
            this.startTick = startTick;
            this.endTick = endTick;
        }
    }

    public static ActiveDomain getActiveDomain(UUID ownerId) {
        return ACTIVE_DOMAINS.get(ownerId);
    }

    public static boolean isLured(UUID entityId) {
        return CIRCUS_LURED.containsKey(entityId);
    }

    public static void clearAll() {
        ACTIVE_DOMAINS.clear();
        DOMAIN_READY_AT.clear();
        BROADCAST_READY_AT.clear();
        FINISHER_READY_AT.clear();
        BENTO_READY_AT.clear();
        LAST_HINT_AT.clear();
        CIRCUS_LURED.clear();
    }

    private static void sendThrottledHint(ServerPlayer player, Component message) {
        long now = player.serverLevel().getGameTime();
        long prev = LAST_HINT_AT.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (prev == Long.MIN_VALUE || now - prev >= 45L) {
            LAST_HINT_AT.put(player.getUUID(), now);
            player.displayClientMessage(message, true);
        }
    }

    public static void handlePlayerAction(ServerPlayer player, int actionId, int arg0) {
        if (!CircusMegaphoneItem.hasMegaphone(player)) {
            sendThrottledHint(player, Component.translatable("msg.aca_domain67.need_megaphone"));
            return;
        }
        switch (actionId) {
            case Domain67Network.ACT_EXPAND_DOMAIN -> toggleOrExpandDomain(player);
            case Domain67Network.ACT_CIRCUS_BROADCAST -> castCircusBroadcast(player);
            case Domain67Network.ACT_LOG_FINISHER -> castOmniLogFinisher(player);
            case Domain67Network.ACT_BENTO_CAMEO -> castNoHeartburnBentoCameo(player);
            default -> {}
        }
    }

    public static int castCircusBroadcast(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long readyAt = BROADCAST_READY_AT.getOrDefault(player.getUUID(), 0L);
        if (!player.getAbilities().instabuild && now < readyAt) {
            sendThrottledHint(player, Component.translatable("msg.aca_domain67.cooldown",
                    String.format("%.1f", (readyAt - now) / 20.0f)));
            return 0;
        }
        BROADCAST_READY_AT.put(player.getUUID(), now + BROADCAST_COOLDOWN_TICKS);

        Vec3 lurePos = player.position().add(player.getLookAngle().scale(5.5));
        AABB box = AABB.ofSize(player.position(), 64.0, 28.0, 64.0);
        int count = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!AcademyCompat.canTarget(player, e)) continue;
            if (AcademyCompat.ignoresControl(e)) continue;
            CIRCUS_LURED.put(e.getUUID(), new CircusLureState(player.getUUID(), level.dimension(), lurePos, now + 180L));
            hijackAiToward(e, lurePos);
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 180, 0, false, false));
            count++;
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 1.25f, 1.32f);
        Domain67Network.broadcastCue(level, player.position(),
                new Domain67Network.VfxCuePayload(
                        Domain67Network.CUE_CIRCUS_BROADCAST, player.getUUID(),
                        player.position().add(0, 1.2, 0), lurePos, count));
        return count;
    }

    public static ActiveDomain toggleOrExpandDomain(ServerPlayer player) {
        if (ACTIVE_DOMAINS.containsKey(player.getUUID())) {
            dismissDomain(player, true);
            return null;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long readyAt = DOMAIN_READY_AT.getOrDefault(player.getUUID(), 0L);
        if (!player.getAbilities().instabuild && now < readyAt) {
            sendThrottledHint(player, Component.translatable("msg.aca_domain67.cooldown",
                    String.format("%.1f", (readyAt - now) / 20.0f)));
            return null;
        }

        BlockPos stageOrigin = player.blockPosition();
        Vec3 center = new Vec3(stageOrigin.getX() + 0.5, stageOrigin.getY(), stageOrigin.getZ() + 0.5);
        ActiveDomain dom = new ActiveDomain(
                player.getUUID(), level.dimension(), center, stageOrigin, DOMAIN_RADIUS,
                now, now + DOMAIN_DURATION_TICKS);

        AABB box = AABB.ofSize(center, DOMAIN_RADIUS * 2.0, DOMAIN_RADIUS * 1.5, DOMAIN_RADIUS * 2.0);
        List<LivingEntity> victims = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!AcademyCompat.canTarget(player, e)) continue;
            if (e.position().distanceToSqr(center) <= DOMAIN_RADIUS * DOMAIN_RADIUS) {
                victims.add(e);
            }
        }

        int n = Math.max(1, victims.size());
        for (int i = 0; i < victims.size(); i++) {
            LivingEntity victim = victims.get(i);
            double angle = (Math.PI * 2.0 * i) / n;
            double seatDist = Math.min(12.8, 5.0 + (i % 4) * 2.1);
            Vec3 seat = new Vec3(
                    center.x + Math.cos(angle) * seatDist,
                    center.y,
                    center.z + Math.sin(angle) * seatDist);

            boolean lured = CIRCUS_LURED.remove(victim.getUUID()) != null;
            float maxNoodles = lured ? 160.0f : 110.0f;
            float baseRate = 0.22f + Math.min(0.42f, victim.getMaxHealth() / 420.0f);
            float eatRate = lured ? baseRate * 0.70f : baseRate;
            dom.diners.put(victim.getUUID(), new DinerState(victim.getUUID(), seat, maxNoodles, eatRate));
            pinDinerToSeat(victim, seat);
        }

        ACTIVE_DOMAINS.put(player.getUUID(), dom);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.2f, 0.80f);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.5f, 1.35f);

        Domain67Network.broadcastCue(level, center,
                new Domain67Network.VfxCuePayload(
                        Domain67Network.CUE_DOMAIN_EXPAND, player.getUUID(),
                        center, center, victims.size()));
        syncDomain(level, dom, now);
        return dom;
    }

    public static int castOmniLogFinisher(ServerPlayer player) {
        ActiveDomain dom = ACTIVE_DOMAINS.get(player.getUUID());
        if (dom == null) {
            sendThrottledHint(player, Component.translatable("msg.aca_domain67.need_domain"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long readyAt = FINISHER_READY_AT.getOrDefault(player.getUUID(), 0L);
        if (!player.getAbilities().instabuild && now < readyAt) {
            sendThrottledHint(player, Component.translatable("msg.aca_domain67.cooldown",
                    String.format("%.1f", (readyAt - now) / 20.0f)));
            return 0;
        }
        FINISHER_READY_AT.put(player.getUUID(), now + FINISHER_COOLDOWN_TICKS);

        List<LivingEntity> targets = collectActiveDiners(player, dom);
        Vec3 lookHoriz = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
        if (lookHoriz.lengthSqr() < 1e-4) lookHoriz = new Vec3(0, 0, 1);
        else lookHoriz = lookHoriz.normalize();

        Vec3 focus = targets.isEmpty()
                ? new Vec3(dom.center.x + lookHoriz.x * 7.0, dom.center.y + 0.6, dom.center.z + lookHoriz.z * 7.0)
                : targets.get(0).position().add(0, 0.6, 0);

        // 8 Prominent Iron-Bound Walnut Logs launching from surrounding rifts and rolling across the marble floor!
        for (int i = 0; i < 8; i++) {
            double ang = (Math.PI * 2.0 * i) / 8.0;
            double yOff = (i % 2 == 0) ? 2.4 : 4.2;
            Vec3 riftPos = new Vec3(focus.x + Math.cos(ang) * 9.2, dom.center.y + yOff, focus.z + Math.sin(ang) * 9.2);
            Vec3 vel = focus.subtract(riftPos).normalize().scale(0.95);
            CursedLogEntity.spawnOnStage(level, player.getUUID(), CursedLogEntity.VARIANT_BARRAGE,
                    riftPos, vel, 1.48f, 10.0f, 44, null, dom.center.y);
        }

        // 1 Colossal 3.2x Giant Enforcement Cursed Log crashing down from the ceiling & rolling heavily across stage!
        Vec3 megaStart = new Vec3(focus.x, dom.center.y + 12.8, focus.z);
        CursedLogEntity.spawnOnStage(level, player.getUUID(), CursedLogEntity.VARIANT_MEGA_BLACK_FLASH,
                megaStart, new Vec3(lookHoriz.x * 0.18, -1.18, lookHoriz.z * 0.18),
                3.2f, 34.0f, 52, null, dom.center.y);

        int punished = 0;
        var src = level.damageSources().playerAttack(player);
        for (LivingEntity target : targets) {
            DinerState st = dom.diners.get(target.getUUID());
            if (st != null && !st.finishedNoReward && AcademyCompat.hurt(target, src, 28.0f)) punished++;
        }

        level.playSound(null, focus.x, focus.y, focus.z,
                SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.15f, 1.42f);
        Domain67Network.broadcastCue(level, dom.center,
                new Domain67Network.VfxCuePayload(
                        Domain67Network.CUE_LOG_BARRAGE, player.getUUID(),
                        dom.center, focus, punished));
        return punished;
    }

    public static int castNoHeartburnBentoCameo(ServerPlayer player) {
        ActiveDomain dom = ACTIVE_DOMAINS.get(player.getUUID());
        if (dom == null) {
            sendThrottledHint(player, Component.translatable("msg.aca_domain67.need_domain"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long readyAt = BENTO_READY_AT.getOrDefault(player.getUUID(), 0L);
        if (!player.getAbilities().instabuild && now < readyAt) {
            sendThrottledHint(player, Component.translatable("msg.aca_domain67.cooldown",
                    String.format("%.1f", (readyAt - now) / 20.0f)));
            return 0;
        }
        BENTO_READY_AT.put(player.getUUID(), now + BENTO_COOLDOWN_TICKS);

        int hits = 0;
        var src = level.damageSources().playerAttack(player);
        for (LivingEntity target : collectActiveDiners(player, dom)) {
            boolean shielded = AcademyCompat.ignoresControl(target);
            if (!AcademyCompat.hurt(target, src, 16.0f)) continue;
            hits++;
            if (shielded || !target.isAlive() || AcademyCompat.ignoresControl(target)) continue;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
            target.setDeltaMovement(0, 0.72, 0);
            target.hurtMarked = true;
        }
        player.heal(4.0f);
        level.playSound(null, dom.center.x, dom.center.y, dom.center.z,
                SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.25f, 0.88f);
        Domain67Network.broadcastCue(level, dom.center,
                new Domain67Network.VfxCuePayload(
                        Domain67Network.CUE_BENTO_CAMEO, player.getUUID(),
                        dom.center, player.position(), hits));
        return hits;
    }

    public static void dismissDomain(ServerPlayer player, boolean applyBurnout) {
        ActiveDomain dom = ACTIVE_DOMAINS.remove(player.getUUID());
        if (dom == null) return;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (applyBurnout && !player.getAbilities().instabuild) {
            DOMAIN_READY_AT.put(player.getUUID(), now + BURNOUT_COOLDOWN_TICKS);
        }
        DomainTerrainVault.get(level).beginCollapse(level, player.getUUID());

        Domain67Network.broadcastCue(level, dom.center,
                new Domain67Network.VfxCuePayload(
                        Domain67Network.CUE_DOMAIN_CLOSE, player.getUUID(),
                        dom.center, dom.center, 0));
        Domain67Network.sendSync(level, dom.center,
                new Domain67Network.DomainSyncPayload(
                        player.getUUID(), false, dom.center.x, dom.center.y, dom.center.z,
                        dom.radius, 0, 0, DOMAIN_DURATION_TICKS,
                        applyBurnout ? BURNOUT_COOLDOWN_TICKS : 0, List.of()));
    }

    public static void tickServer(MinecraftServer server) {
        for (ServerLevel sl : server.getAllLevels()) {
            DomainTerrainVault.get(sl).tickLevel(sl);
        }

        Iterator<Map.Entry<UUID, CircusLureState>> lureIt = CIRCUS_LURED.entrySet().iterator();
        while (lureIt.hasNext()) {
            var entry = lureIt.next();
            ServerLevel sl = server.getLevel(entry.getValue().dim);
            if (sl == null || sl.getGameTime() >= entry.getValue().expireTick) {
                lureIt.remove();
                continue;
            }
            ServerPlayer caster = server.getPlayerList().getPlayer(entry.getValue().ownerId);
            if (caster == null || !(sl.getEntity(entry.getKey()) instanceof LivingEntity e)
                    || !AcademyCompat.canTarget(caster, e) || AcademyCompat.ignoresControl(e)) {
                lureIt.remove();
            } else if ((sl.getGameTime() & 3L) == 0L) {
                hijackAiToward(e, entry.getValue().lurePoint);
            }
        }

        Iterator<Map.Entry<UUID, ActiveDomain>> domIt = ACTIVE_DOMAINS.entrySet().iterator();
        while (domIt.hasNext()) {
            var entry = domIt.next();
            ActiveDomain dom = entry.getValue();
            ServerLevel sl = server.getLevel(dom.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(dom.ownerId);
            if (sl == null || owner == null || !owner.isAlive() || owner.level() != sl) {
                if (sl != null) DomainTerrainVault.get(sl).beginCollapse(sl, dom.ownerId);
                domIt.remove();
                continue;
            }
            long now = sl.getGameTime();

            if (!dom.waveTransformationStarted && (now - dom.startTick) >= 10L) {
                dom.waveTransformationStarted = true;
                DomainTerrainVault.get(sl).beginArena(sl, dom.ownerId, dom.stageOrigin, STAGE_BLOCK_RADIUS);
            }

            if (now >= dom.endTick) {
                int punished = 0;
                var src = sl.damageSources().playerAttack(owner);
                for (DinerState diner : dom.diners.values()) {
                    if (diner.finishedNoReward) continue;
                    if (sl.getEntity(diner.entityId) instanceof LivingEntity victim && AcademyCompat.canTarget(owner, victim)) {
                        Vec3 spawnAt = victim.position().add(0, 10.5, 0);
                        CursedLogEntity.spawnOnStage(sl, dom.ownerId, CursedLogEntity.VARIANT_MEGA_BLACK_FLASH,
                                spawnAt, new Vec3(0, -1.22, 0), 2.85f, 26.0f, 46, null, dom.center.y);
                        if (AcademyCompat.hurt(victim, src, 24.0f)) punished++;
                    }
                }
                domIt.remove();
                DomainTerrainVault.get(sl).beginCollapse(sl, dom.ownerId);
                if (!owner.getAbilities().instabuild) {
                    DOMAIN_READY_AT.put(dom.ownerId, now + BURNOUT_COOLDOWN_TICKS);
                }
                Domain67Network.broadcastCue(sl, dom.center,
                        new Domain67Network.VfxCuePayload(
                                Domain67Network.CUE_DOMAIN_CLOSE, dom.ownerId,
                                dom.center, dom.center, punished));
                Domain67Network.sendSync(sl, dom.center,
                        new Domain67Network.DomainSyncPayload(
                                dom.ownerId, false, dom.center.x, dom.center.y, dom.center.z,
                                dom.radius, DOMAIN_DURATION_TICKS, 0, DOMAIN_DURATION_TICKS,
                                BURNOUT_COOLDOWN_TICKS, List.of()));
                continue;
            }

            for (DinerState diner : dom.diners.values()) {
                if (!(sl.getEntity(diner.entityId) instanceof LivingEntity victim)
                        || !AcademyCompat.canTarget(owner, victim) || AcademyCompat.ignoresControl(victim)
                        || diner.finishedNoReward) continue;

                diner.noodlesRemaining = Math.max(0.0f, diner.noodlesRemaining - diner.eatRatePerTick);
                if (diner.noodlesRemaining <= 0.0f) {
                    diner.finishedNoReward = true;
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                    victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0));
                    if (dom.lastCueBroadcastTick == Long.MIN_VALUE || now - dom.lastCueBroadcastTick >= 30L) {
                        dom.lastCueBroadcastTick = now;
                        Domain67Network.broadcastCue(sl, victim.position(),
                                new Domain67Network.VfxCuePayload(
                                        Domain67Network.CUE_FINISHED_NO_REWARD, dom.ownerId,
                                        victim.position(), victim.position(), victim.getId()));
                    }
                    continue;
                }

                double distFromCenter = victim.position().distanceTo(dom.center);
                if (distFromCenter > dom.radius - 2.6) {
                    if (diner.lastGuardLogTick == Long.MIN_VALUE || now - diner.lastGuardLogTick >= 35L) {
                        diner.lastGuardLogTick = now;
                        Vec3 outDir = victim.position().subtract(dom.center).normalize();
                        Vec3 riftPos = new Vec3(
                                dom.center.x + outDir.x * (dom.radius - 0.8),
                                dom.center.y + 0.9,
                                dom.center.z + outDir.z * (dom.radius - 0.8));
                        Vec3 inwardVel = dom.center.subtract(riftPos).normalize().scale(1.05);
                        CursedLogEntity.spawnOnStage(sl, dom.ownerId, CursedLogEntity.VARIANT_GUARD,
                                riftPos, inwardVel, 1.85f, 12.0f, 40, diner.seatPos, dom.center.y);
                        Domain67Network.broadcastCue(sl, riftPos,
                                new Domain67Network.VfxCuePayload(
                                        Domain67Network.CUE_GUARD_LOG, dom.ownerId,
                                        riftPos, diner.seatPos, victim.getId()));
                    }
                    pinDinerToSeat(victim, diner.seatPos);
                } else if ((now & 3L) == 0L) {
                    pinDinerToSeat(victim, diner.seatPos);
                }
            }

            if ((now % 4L) == 0L) {
                syncDomain(sl, dom, now);
            }
        }
    }

    private static List<LivingEntity> collectActiveDiners(ServerPlayer owner, ActiveDomain dom) {
        List<LivingEntity> out = new ArrayList<>();
        for (UUID id : dom.diners.keySet()) {
            if (owner.serverLevel().getEntity(id) instanceof LivingEntity e && AcademyCompat.canTarget(owner, e)) {
                out.add(e);
            }
        }
        return out;
    }

    private static void syncDomain(ServerLevel level, ActiveDomain dom, long now) {
        List<Domain67Network.DinerSnapshot> snaps = new ArrayList<>();
        for (DinerState st : dom.diners.values()) {
            if (level.getEntity(st.entityId) instanceof LivingEntity e && e.isAlive()) {
                snaps.add(new Domain67Network.DinerSnapshot(
                        e.getId(), (float) e.getX(), (float) e.getY(), (float) e.getZ(),
                        st.progress(), st.finishedNoReward));
            }
        }
        int elapsed = (int) Math.max(0, now - dom.startTick);
        int rem = (int) Math.max(0, dom.endTick - now);
        Domain67Network.sendSync(level, dom.center,
                new Domain67Network.DomainSyncPayload(
                        dom.ownerId, true, dom.center.x, dom.center.y, dom.center.z,
                        dom.radius, elapsed, rem, DOMAIN_DURATION_TICKS, 0, snaps));
    }

    private static void hijackAiToward(LivingEntity entity, Vec3 target) {
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().moveTo(target.x, target.y, target.z, 1.35);
            try {
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
            } catch (Exception ignored) {}
        }
        Vec3 diff = target.subtract(entity.position());
        double horiz = Math.hypot(diff.x, diff.z);
        if (horiz > 2.2) {
            entity.setDeltaMovement(diff.x / horiz * 0.34, entity.getDeltaMovement().y, diff.z / horiz * 0.34);
            entity.hurtMarked = true;
        }
    }

    private static void pinDinerToSeat(LivingEntity entity, Vec3 seat) {
        if (AcademyCompat.ignoresControl(entity)) return;
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            try {
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            } catch (Exception ignored) {}
        }
        Vec3 delta = seat.subtract(entity.position());
        if (delta.horizontalDistanceSqr() > 1.8) {
            entity.setDeltaMovement(delta.x * 0.35, Math.min(0.1, entity.getDeltaMovement().y), delta.z * 0.35);
            entity.hurtMarked = true;
        } else {
            entity.setDeltaMovement(0.0, Math.min(0.0, entity.getDeltaMovement().y), 0.0);
        }
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 4, false, false));
        if (entity.level() instanceof ServerLevel sl && (sl.getGameTime() & 7L) == 0L) {
            sl.sendParticles(ParticleTypes.WAX_ON, entity.getX(), entity.getY() + 1.15, entity.getZ(),
                    1, 0.2, 0.15, 0.2, 0.01);
        }
    }
}

package cn.aca.domain67.client;

import cn.aca.domain67.Domain67Network;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Dist-clean client state, anti-spam peripheral toast slot & gentle spring-inertia tracker (v1.2.0):
 * - Single-Slot Deduplicated Peripheral Toast (`activeToast`) with strict per-category 45-tick cooldowns —
 *   NEVER stacks multiple banners, NEVER blocks center screen, and NEVER spams on repeated skill casts!
 * - Uses i18n translation keys (`zh_cn.json` / `en_us.json`) so Chinese locale renders 100% pure Simplified Chinese!
 */
public final class Domain67ClientCache {
    public static final float EXPAND_TOTAL_TICKS = 42.0f;
    public static final float COLLAPSE_TOTAL_TICKS = 32.0f;
    public static final float MAX_HORIZON_WAVE_RADIUS = 320.0f;

    public static final int RIFT_MODE_SPATIAL = 2;
    public static final int RIFT_MODE_BROADCAST = 4;
    public static final int RIFT_MODE_SAUCE_WAVE = 5;

    private static final long TOAST_COOLDOWN_TICKS = 45L;

    public record RiftVisual(Vec3 pos, Vec3 target, long startTick, int duration, int mode) {}

    public record PeripheralToast(
            String categoryKey,
            String badge,
            String titleKey,
            String subKey,
            int countArg,
            int accentRgb,
            long startTick,
            int durationTicks
    ) {
        public float age(long now, float partial) {
            return (now - startTick) + partial;
        }
    }

    public static final class ClientDomainView {
        public final UUID ownerId;
        public double cx, cy, cz;
        public float maxRadius;
        public final long clientBornTick;
        public long clientCollapseStartTick = Long.MIN_VALUE;
        public int serverTicksRemaining;
        public int serverTotalDuration;
        public List<Domain67Network.DinerSnapshot> diners = List.of();

        public ClientDomainView(Domain67Network.DomainSyncPayload p, long now) {
            this.ownerId = p.ownerId();
            this.cx = p.cx();
            this.cy = p.cy();
            this.cz = p.cz();
            this.maxRadius = p.radius();
            this.clientBornTick = now - p.ticksElapsed();
            this.serverTicksRemaining = p.ticksRemaining();
            this.serverTotalDuration = p.totalDuration();
            this.diners = p.diners();
        }

        public void updateFromSync(Domain67Network.DomainSyncPayload p, long now) {
            this.cx = p.cx();
            this.cy = p.cy();
            this.cz = p.cz();
            this.maxRadius = p.radius();
            this.serverTicksRemaining = p.ticksRemaining();
            this.serverTotalDuration = p.totalDuration();
            this.diners = p.diners();
            this.clientCollapseStartTick = Long.MIN_VALUE;
        }

        public void beginCollapse(long now) {
            if (this.clientCollapseStartTick == Long.MIN_VALUE) {
                this.clientCollapseStartTick = now;
            }
        }

        public boolean isCollapsing() {
            return clientCollapseStartTick != Long.MIN_VALUE;
        }

        public boolean isExpired(long now) {
            return isCollapsing() && (now - clientCollapseStartTick) > (long) COLLAPSE_TOTAL_TICKS + 2L;
        }

        public float localAge(long now, float partial) {
            return Math.max(0f, (now - clientBornTick) + partial);
        }

        public float collapseProgress(long now, float partial) {
            if (!isCollapsing()) return 0f;
            return Mth.clamp(((now - clientCollapseStartTick) + partial) / COLLAPSE_TOTAL_TICKS, 0f, 1f);
        }

        public float currentRadiusFactor(long now, float partial) {
            float age = localAge(now, partial);
            float expFactor;
            if (age < 9.0f) {
                expFactor = 0.0f;
            } else {
                float p = Mth.clamp((age - 9.0f) / 19.0f, 0f, 1f);
                expFactor = 1f - (1f - p) * (1f - p) * (1f - p);
            }
            if (!isCollapsing()) return expFactor;

            float cp = collapseProgress(now, partial);
            if (cp < 0.36f) {
                return expFactor * (1.0f + 0.03f * Mth.sin(cp / 0.36f * (float) Math.PI));
            }
            float imp = (cp - 0.36f) / 0.64f;
            float shrink = 1.0f - imp * imp * (3.0f - 2.0f * imp);
            return expFactor * Math.max(0f, shrink);
        }

        public float worldWaveRadius(long now, float partial) {
            float age = localAge(now, partial);
            float expR;
            if (age < 9.0f) {
                expR = 0.0f;
            } else {
                float p = Mth.clamp((age - 9.0f) / 29.0f, 0f, 1f);
                float curve = p * p * (3.0f - 2.0f * p);
                expR = MAX_HORIZON_WAVE_RADIUS * curve;
            }
            if (!isCollapsing()) return expR;

            float cp = collapseProgress(now, partial);
            if (cp < 0.25f) return expR;
            float imp = (cp - 0.25f) / 0.75f;
            return expR * Math.max(0f, 1.0f - imp * imp);
        }

        public float shatterFactor(long now, float partial) {
            if (isCollapsing()) {
                return Mth.clamp(collapseProgress(now, partial) * 1.18f, 0f, 1f);
            }
            float age = localAge(now, partial);
            if (age >= 8.5f && age <= 16.5f) {
                float p = (age - 8.5f) / 8.0f;
                return (1.0f - p) * 0.72f;
            }
            return 0f;
        }

        public int exteriorCurtainPhase(long now, float partial) {
            if (isCollapsing()) {
                return 505 + (int) (Mth.clamp(collapseProgress(now, partial), 0f, 1f) * 495f);
            }
            float age = localAge(now, partial);
            float seal = Mth.clamp((age - 9.0f) / 21.0f, 0f, 1f);
            return (int) (seal * 500f);
        }

        public float propRiseFactor(long now, float partial, float distRatio) {
            float age = localAge(now, partial);
            float delay = 11.0f + distRatio * 10.0f;
            float rise = Mth.clamp((age - delay) / 10.0f, 0f, 1f);
            float smoothRise = 1f - (1f - rise) * (1f - rise);
            if (!isCollapsing()) return smoothRise;

            float cp = collapseProgress(now, partial);
            float sink = 1f - Mth.clamp((cp - distRatio * 0.15f) / 0.65f, 0f, 1f);
            return smoothRise * sink * sink;
        }
    }

    private static final Map<UUID, ClientDomainView> DOMAINS = new ConcurrentHashMap<>();
    private static final List<RiftVisual> RIFTS = new ArrayList<>();
    private static final Map<String, Long> LAST_TOAST_AT = new HashMap<>();
    private static PeripheralToast activeToast = null;

    private static long clientTicks = 0L;
    private static long handSignStartTick = Long.MIN_VALUE;
    private static long shakeStartTick = Long.MIN_VALUE;
    private static float shakeIntensity = 0f;

    private static boolean camInit = false;
    private static float lastPlayerYaw = 0f;
    private static float lastPlayerPitch = 0f;
    private static float prevLagYaw = 0f, lagYaw = 0f, velYaw = 0f;
    private static float prevLagPitch = 0f, lagPitch = 0f, velPitch = 0f;

    private Domain67ClientCache() {}

    public static long ticks() {
        return clientTicks;
    }

    public static void tick() {
        clientTicks++;
        for (var entry : DOMAINS.entrySet()) {
            ClientDomainView v = entry.getValue();
            if (!v.isCollapsing() && v.serverTicksRemaining > 0) {
                v.serverTicksRemaining--;
            }
            if (v.isExpired(clientTicks)) {
                DOMAINS.remove(entry.getKey());
            }
        }
        RIFTS.removeIf(r -> clientTicks - r.startTick >= r.duration);
        if (activeToast != null && clientTicks - activeToast.startTick >= activeToast.durationTicks) {
            activeToast = null;
        }
    }

    public static void updateCameraSpring(boolean hasPlayer, float yRot, float xRot, double horizSpeed) {
        prevLagYaw = lagYaw;
        prevLagPitch = lagPitch;
        if (hasPlayer) {
            if (!camInit) {
                camInit = true;
                lastPlayerYaw = yRot;
                lastPlayerPitch = xRot;
            }
            float dYaw = Mth.wrapDegrees(yRot - lastPlayerYaw);
            float dPitch = xRot - lastPlayerPitch;
            lastPlayerYaw = yRot;
            lastPlayerPitch = xRot;

            velYaw = (velYaw + Mth.clamp(dYaw, -18f, 18f) * 0.28f - lagYaw * 0.30f) * 0.65f;
            velPitch = (velPitch + Mth.clamp(dPitch, -14f, 14f) * 0.28f - lagPitch * 0.30f) * 0.65f;
            lagYaw = Mth.clamp(lagYaw + velYaw, -10f, 10f);
            lagPitch = Mth.clamp(lagPitch + velPitch, -8f, 8f);
        } else {
            camInit = false;
            lagYaw *= 0.7f;
            lagPitch *= 0.7f;
        }
    }

    public static float uiLagYaw(float partial) {
        return Mth.lerp(partial, prevLagYaw, lagYaw);
    }

    public static float uiLagPitch(float partial) {
        return Mth.lerp(partial, prevLagPitch, lagPitch);
    }

    /**
     * Pushes or deduplicates a single peripheral micro-toast with strict cooldown throttling so repeated
     * skill presses NEVER spam or re-pop animations!
     */
    public static boolean pushThrottledToast(String categoryKey, String badge, String titleKey, String subKey,
                                             int countArg, int accentRgb, int durationTicks) {
        Long last = LAST_TOAST_AT.get(categoryKey);
        if (last != null && clientTicks >= last && (clientTicks - last) < TOAST_COOLDOWN_TICKS) {
            return false;
        }
        LAST_TOAST_AT.put(categoryKey, clientTicks);
        activeToast = new PeripheralToast(categoryKey, badge, titleKey, subKey, countArg, accentRgb, clientTicks, durationTicks);
        return true;
    }

    public static PeripheralToast activeToast() {
        return activeToast;
    }

    public static void clearStateOnly() {
        DOMAINS.clear();
        RIFTS.clear();
        LAST_TOAST_AT.clear();
        activeToast = null;
        handSignStartTick = Long.MIN_VALUE;
        shakeStartTick = Long.MIN_VALUE;
        camInit = false;
    }

    public static Map<UUID, ClientDomainView> domains() {
        return DOMAINS;
    }

    public static List<RiftVisual> rifts() {
        return RIFTS;
    }

    public static float handSignProgress(float partial) {
        if (handSignStartTick == Long.MIN_VALUE) return -1f;
        float age = (clientTicks - handSignStartTick) + partial;
        if (age < 0f || age > 28f) return -1f;
        return age / 28f;
    }

    public static float cameraShakeAmount(float partial) {
        if (shakeStartTick == Long.MIN_VALUE) return 0f;
        float age = (clientTicks - shakeStartTick) + partial;
        if (age < 0f || age > 12f) return 0f;
        return shakeIntensity * (1f - age / 12f);
    }

    public static ClientDomainView primaryDomain(Vec3 viewerPos) {
        ClientDomainView best = null;
        double bestDist = Double.MAX_VALUE;
        for (var d : DOMAINS.values()) {
            double dist = viewerPos.distanceToSqr(d.cx, d.cy, d.cz);
            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return best;
    }

    public static void handleDomainSync(Domain67Network.DomainSyncPayload payload) {
        if (payload.active()) {
            DOMAINS.compute(payload.ownerId(), (id, existing) -> {
                if (existing == null || existing.isCollapsing()) {
                    return new ClientDomainView(payload, clientTicks);
                }
                existing.updateFromSync(payload, clientTicks);
                return existing;
            });
        } else {
            ClientDomainView existing = DOMAINS.get(payload.ownerId());
            if (existing != null) {
                existing.beginCollapse(clientTicks);
            }
        }
    }

    public static void handleVfxCue(Domain67Network.VfxCuePayload cue, UUID localPlayerId) {
        switch (cue.cueId()) {
            case Domain67Network.CUE_DOMAIN_EXPAND -> {
                if (cue.actor().equals(localPlayerId)) {
                    handSignStartTick = clientTicks;
                }
                triggerShake(1.2f);
                LAST_TOAST_AT.remove("close");
                pushThrottledToast("expand", "67",
                        "ui.aca_domain67.toast.expand.title",
                        "ui.aca_domain67.toast.expand.sub",
                        cue.extra(), 0xFFF2C96B, 42);
            }
            case Domain67Network.CUE_DOMAIN_CLOSE -> {
                ClientDomainView existing = DOMAINS.get(cue.actor());
                if (existing != null) {
                    existing.beginCollapse(clientTicks);
                }
                triggerShake(1.0f);
                LAST_TOAST_AT.remove("expand");
                pushThrottledToast("close", "67",
                        "ui.aca_domain67.toast.close.title",
                        cue.extra() > 0 ? "ui.aca_domain67.toast.close.sub_punish" : "ui.aca_domain67.toast.close.sub_clean",
                        cue.extra(), 0xFFE5B85C, 34);
            }
            case Domain67Network.CUE_CIRCUS_BROADCAST -> {
                RIFTS.add(new RiftVisual(cue.from(), cue.to(), clientTicks, 32, RIFT_MODE_BROADCAST));
                triggerShake(0.45f);
                pushThrottledToast("broadcast", Domain67Client.keyLabel(Domain67Client.KEY_CIRCUS_BROADCAST),
                        "ui.aca_domain67.toast.broadcast.title",
                        "ui.aca_domain67.toast.broadcast.sub",
                        cue.extra(), 0xFFF2C96B, 32);
            }
            case Domain67Network.CUE_GUARD_LOG -> {
                // World rift + log visual only — ZERO popup spam!
                RIFTS.add(new RiftVisual(cue.from(), cue.to(), clientTicks, 24, RIFT_MODE_SPATIAL));
                triggerShake(0.75f);
            }
            case Domain67Network.CUE_LOG_BARRAGE -> {
                for (int i = 0; i < 8; i++) {
                    double ang = (Math.PI * 2.0 * i) / 8.0;
                    double yOff = (i % 2 == 0) ? 2.4 : 4.2;
                    Vec3 rp = new Vec3(cue.to().x + Math.cos(ang) * 9.2, cue.from().y + yOff, cue.to().z + Math.sin(ang) * 9.2);
                    RIFTS.add(new RiftVisual(rp, cue.to(), clientTicks, 26, RIFT_MODE_SPATIAL));
                }
                triggerShake(1.55f);
                pushThrottledToast("log", Domain67Client.keyLabel(Domain67Client.KEY_LOG_FINISHER),
                        "ui.aca_domain67.toast.log.title",
                        "ui.aca_domain67.toast.log.sub",
                        cue.extra(), 0xFFE86A4A, 34);
            }
            case Domain67Network.CUE_MEGA_LOG_IMPACT -> {
                triggerShake(1.85f);
            }
            case Domain67Network.CUE_BENTO_CAMEO -> {
                RIFTS.add(new RiftVisual(cue.from(), cue.to(), clientTicks, 30, RIFT_MODE_SAUCE_WAVE));
                triggerShake(0.75f);
                pushThrottledToast("bento", "78",
                        "ui.aca_domain67.toast.bento.title",
                        "ui.aca_domain67.toast.bento.sub",
                        cue.extra(), 0xFFF2C96B, 32);
            }
            case Domain67Network.CUE_FINISHED_NO_REWARD -> {
                // Silent transition — shown cleanly on targeted diner micro-pill without HUD spam!
            }
            default -> {}
        }
    }

    private static void triggerShake(float intensity) {
        shakeStartTick = clientTicks;
        shakeIntensity = Math.max(shakeIntensity * 0.4f, intensity);
    }
}

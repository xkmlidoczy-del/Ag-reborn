package cn.aca.infinity.client;

import cn.aca.infinity.GauntletData;
import cn.aca.infinity.InfinityNetwork;
import cn.aca.infinity.InfinityNetwork.EntityMarkPayload;
import cn.aca.infinity.InfinityNetwork.VfxCuePayload;
import cn.aca.infinity.InfinityNetwork.WearerSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class ClientCache implements InfinityNetwork.ClientReceiver {
    public record WearerView(WearerSnapshot snapshot, long clientReceivedTick) {
        public long estimatedServerNow(long clientNow) {
            return snapshot.now() + Math.max(0L, clientNow - clientReceivedTick);
        }

        public GauntletData data() {
            return snapshot.gauntletData();
        }
    }

    public record MarkView(EntityMarkPayload mark, long clientReceivedTick) {
        public long estimatedServerNow(long clientNow) {
            return mark.now() + Math.max(0L, clientNow - clientReceivedTick);
        }

        public boolean hasHukou(long clientNow) {
            return !mark.fatherName().isEmpty() && mark.hukouUntil() > estimatedServerNow(clientNow);
        }

        public boolean isAshamed(long clientNow) {
            return mark.shameUntil() > estimatedServerNow(clientNow);
        }

        public boolean isSpaceTethered(long clientNow) {
            return mark.spaceTetherUntil() > estimatedServerNow(clientNow);
        }

        public boolean isTimeFrozen(long clientNow) {
            return mark.timeFrozenUntil() > estimatedServerNow(clientNow);
        }

        public float frozenDamagePool() {
            return mark.frozenDamagePool();
        }

        public boolean isStandingAttention(long clientNow) {
            return mark.mindStandUntil() > estimatedServerNow(clientNow);
        }

        public boolean isMindControlled(long clientNow) {
            return mark.mindControlUntil() > estimatedServerNow(clientNow);
        }

        public boolean isCrystalSnared(long clientNow) {
            return mark.crystalSnareUntil() > estimatedServerNow(clientNow);
        }

        public boolean isGawking(long clientNow) {
            return mark.gawkTargetId() >= 0 && mark.gawkUntil() > estimatedServerNow(clientNow);
        }

        public boolean isParentSlowed(long clientNow) {
            return mark.parentSlowUntil() > estimatedServerNow(clientNow);
        }
    }

    public record ActiveCue(VfxCuePayload cue, long startClientTick, int durationTicks) {}

    private static final Map<UUID, WearerView> WEARERS = new HashMap<>();
    private static final Map<Integer, MarkView> MARKS = new HashMap<>();
    private static final List<ActiveCue> CUES = new ArrayList<>();

    private static long clientTickCounter = 0L;
    private static long snapFlashClientTick = -1000L;
    private static float shakeTrauma = 0f;
    private static long shakeClientTick = -1000L;

    public static void clear() {
        WEARERS.clear();
        MARKS.clear();
        CUES.clear();
        clientTickCounter = 0L;
        snapFlashClientTick = -1000L;
        shakeTrauma = 0f;
        shakeClientTick = -1000L;
        InfinityPostShader.reset();
    }

    public static long clientTicks() {
        return clientTickCounter;
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        clientTickCounter++;
        CUES.removeIf(c -> clientTickCounter - c.startClientTick() > c.durationTicks());
        if (clientTickCounter % 100L == 0L) {
            MARKS.entrySet().removeIf(e -> mc.level.getEntity(e.getKey()) == null);
        }
    }

    public static WearerView self() {
        var mc = Minecraft.getInstance();
        return mc.player == null ? null : WEARERS.get(mc.player.getUUID());
    }

    public static WearerView getWearer(Player player) {
        return player == null ? null : WEARERS.get(player.getUUID());
    }

    public static MarkView getMark(Entity entity) {
        return entity == null ? null : MARKS.get(entity.getId());
    }

    public static List<ActiveCue> activeCues() {
        return CUES;
    }

    public static long snapFlashTick() {
        return snapFlashClientTick;
    }

    public static float currentShake(long cTick, float partial) {
        float age = (cTick - shakeClientTick) + partial;
        if (age < 0f || age > 14f) return 0f;
        float tr = shakeTrauma * (1f - age / 14f);
        return tr * tr;
    }

    private static void addTrauma(long cTick, float impulse) {
        float cur = currentShake(cTick, 0f);
        shakeClientTick = cTick;
        shakeTrauma = Math.min(1.65f, Math.max((float) Math.sqrt(cur) + impulse * 0.55f, impulse));
    }

    @Override
    public void onWearer(WearerSnapshot snapshot, Player viewer) {
        long now = viewer.level().getGameTime();
        WEARERS.put(snapshot.actor(), new WearerView(snapshot, now));
    }

    @Override
    public void onEntityMark(EntityMarkPayload mark, Player viewer) {
        long now = viewer.level().getGameTime();
        MARKS.put(mark.entityId(), new MarkView(mark, now));
    }

    @Override
    public void onVfxCue(VfxCuePayload cue, Player viewer) {
        long cNow = clientTickCounter;
        if (cue.cueId() == InfinityNetwork.CUE_TIME_DOME) {
            // A recast in the same spot renews the dome without stacking its render passes.
            CUES.removeIf(active -> active.cue().cueId() == InfinityNetwork.CUE_TIME_DOME
                    && active.cue().actor().equals(cue.actor())
                    && active.cue().from().distanceToSqr(cue.from()) <= 4.0);
        }
        int dur = switch (cue.cueId()) {
            case InfinityNetwork.CUE_TIME_DOME -> Math.max(60, cue.extra());
            case InfinityNetwork.CUE_SNAP, InfinityNetwork.CUE_SHIP_SNAP_COLLAPSE -> 46;
            case InfinityNetwork.CUE_SHIP_RAIN_FIRE -> 36;
            case InfinityNetwork.CUE_COMBO_SINGULARITY, InfinityNetwork.CUE_COMBO_TEMPORAL_RECKONING,
                 InfinityNetwork.CUE_COMBO_PUBLIC_EXEC, InfinityNetwork.CUE_SPACE_DECOY_BURST -> 32;
            case InfinityNetwork.CUE_SOUL_HUKOU, InfinityNetwork.CUE_MIND_ROLLCALL, InfinityNetwork.CUE_REALITY_WAVE,
                 InfinityNetwork.CUE_SHIP_WARP, InfinityNetwork.CUE_REALITY_BUTTERFLY_HEAL -> 28;
            default -> 24;
        };
        CUES.add(new ActiveCue(cue, cNow, dur));
        InfinityPostShader.onCue(cue, viewer, cNow);

        double distSqr = cue.from().distanceToSqr(viewer.position());
        float falloff = (float) Math.max(0.0, 1.0 - Math.sqrt(distSqr) / 64.0);
        if (cue.cueId() == InfinityNetwork.CUE_SNAP) {
            if (falloff > 0.05f || cue.actor().equals(viewer.getUUID())) {
                snapFlashClientTick = cNow;
                addTrauma(cNow, 1.55f * Math.max(0.55f, falloff));
            }
        } else if (cue.cueId() == InfinityNetwork.CUE_COMBO_SINGULARITY
                || cue.cueId() == InfinityNetwork.CUE_COMBO_TEMPORAL_RECKONING
                || cue.cueId() == InfinityNetwork.CUE_SPACE_DECOY_BURST
                || cue.cueId() == InfinityNetwork.CUE_POWER_WRATH
                || cue.cueId() == InfinityNetwork.CUE_SHIP_CANNON
                || cue.cueId() == InfinityNetwork.CUE_SHIP_RAIN_FIRE) {
            if (falloff > 0.1f) {
                addTrauma(cNow, 1.18f * falloff);
            }
        } else if (cue.cueId() == InfinityNetwork.CUE_POWER_PUNCH
                || cue.cueId() == InfinityNetwork.CUE_COMBO_PUBLIC_EXEC
                || cue.cueId() == InfinityNetwork.CUE_SOUL_HUKOU
                || cue.cueId() == InfinityNetwork.CUE_SHIP_WARP) {
            if (falloff > 0.15f) {
                addTrauma(cNow, 0.72f * falloff);
            }
        }
    }
}

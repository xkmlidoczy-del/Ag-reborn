package cn.aca.omnitrix;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class Wrist implements INBTSerializable<CompoundTag> {
    public enum Stage { IDLE, BINDING, ARMING, ACTIVE, REVERTING, COOLING, LOCKING }

    ItemStack stack = ItemStack.EMPTY;
    Stage stage = Stage.IDLE;
    Form form;
    long began, until, primaryAt, secondaryAt;
    long shiftPrimaryAt, shiftSecondaryAt, flickAt;
    long equipReadyAt;
    long lastRequest = Long.MIN_VALUE;
    long lastPanel = Long.MIN_VALUE;
    long attackCueAt;
    Vec3 lastHumanClear;
    int carniCursor;
    int targetId = -1;
    int revision;
    boolean carniLocked;
    boolean civilianOverride;
    boolean misfire;
    boolean hurriedAtOpen;
    int resistance;
    long lastResist = Long.MIN_VALUE;
    long lastDamageAt = Long.MIN_VALUE;
    long lastAegisCueAt = Long.MIN_VALUE;
    double barrier, maxBarrier;
    int warningSecond = -1;
    int dryTicks;
    boolean panelUp;
    int sequence;
    SkillCast skill;
    record BufferedSkill(boolean secondary, boolean shifted, long expires) {}
    BufferedSkill bufferedSkill;
    long primaryStarted = Long.MIN_VALUE;
    long linkUntil, brakeUntil, gripUntil, skillHintUntil;
    BlockPos gripWall;
    long crystalShellUntil, springUntil, circuitUntil, steerUntil;
    Vec3 steerHeading = Vec3.ZERO;
    final Set<UUID> markedTargets = new HashSet<>();
    final Set<BlockPos> markedCrystals = new HashSet<>();
    record Landing(long earliest, long expires) {}
    final Map<UUID, Landing> landings = new HashMap<>();

    public ItemStack stack() { return stack; }
    public Kind kind() { return stack.getItem() instanceof WatchItem item ? item.kind() : null; }
    public Form form() { return form; }
    public boolean hasForm() { return (stage == Stage.ACTIVE || stage == Stage.REVERTING) && form != null; }
    public boolean controlled() {
        return kind() == Kind.CARNITRIX && (stage == Stage.ARMING || stage == Stage.ACTIVE || stage == Stage.REVERTING);
    }
    public Stage stage() { return stage; }
    public long until() { return until; }
    boolean formExpired(long now) { return kind() != Kind.CELESTIAL && now >= until; }

    void enter(Stage next, long now, long deadline) {
        if (next != Stage.ACTIVE || stage != Stage.ACTIVE) clearSkills();
        stage = next;
        began = now;
        until = deadline;
        panelUp = false;
        revision++;
    }

    void clearLink() {
        markedTargets.clear();
        markedCrystals.clear();
        linkUntil = 0;
    }

    void clearSkills() {
        skill = null;
        bufferedSkill = null;
        primaryStarted = Long.MIN_VALUE;
        clearLink();
        brakeUntil = gripUntil = skillHintUntil = 0;
        gripWall = null;
        crystalShellUntil = springUntil = circuitUntil = steerUntil = 0;
        steerHeading = Vec3.ZERO;
        shiftPrimaryAt = shiftSecondaryAt = flickAt = 0;
        landings.clear();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag data = new CompoundTag();
        data.putInt("version", 3);
        data.put("watch", stack.saveOptional(provider));
        data.putString("stage", stage.name());
        data.putInt("form", form == null ? -1 : form.ordinal());
        data.putLong("began", began);
        data.putLong("until", until);
        data.putLong("equipReadyAt", equipReadyAt);
        if (hasForm()) {
            data.putDouble("dnaCapacity", maxBarrier);
            data.putDouble("dnaShield", barrier);
            data.putLong("dnaLastHit", lastDamageAt);
        }
        data.putInt("cycle", carniCursor);
        data.putBoolean("bonded", carniLocked);
        data.putBoolean("civilianOverride", civilianOverride);
        if (lastHumanClear != null) {
            data.putDouble("safeX", lastHumanClear.x);
            data.putDouble("safeY", lastHumanClear.y);
            data.putDouble("safeZ", lastHumanClear.z);
        }
        return data;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag data) {
        clearSkills();
        stack = ItemStack.parseOptional(provider, data.getCompound("watch"));
        form = Form.at(data.getInt("form"));
        try {
            stage = Stage.valueOf(data.getString("stage"));
        } catch (IllegalArgumentException ignored) {
            stage = Stage.IDLE;
        }
        began = data.getLong("began");
        until = data.getLong("until");
        equipReadyAt = data.getLong("equipReadyAt");
        carniCursor = Math.floorMod(data.getInt("cycle"), Form.CLASSIC.size());
        carniLocked = data.getBoolean("bonded");
        civilianOverride = data.getBoolean("civilianOverride");
        if (stage == Stage.LOCKING) stage = Stage.IDLE;
        if (data.contains("safeX")) lastHumanClear = new Vec3(data.getDouble("safeX"), data.getDouble("safeY"), data.getDouble("safeZ"));
        if (kind() == null) {
            stack = ItemStack.EMPTY;
            stage = Stage.IDLE;
            form = null;
        } else if ((stage == Stage.ARMING || stage == Stage.ACTIVE || stage == Stage.REVERTING)
                && (form == null || !form.canUse(kind()))) {
            stage = Stage.IDLE;
            form = null;
        }
        if (kind() == Kind.CELESTIAL) {
            if (data.getInt("version") < 2) equipReadyAt = 0;
            if (stage == Stage.COOLING) stage = Stage.IDLE;
            if (stage == Stage.ACTIVE || stage == Stage.IDLE) until = 0;
        }
        if (kind() != null && hasForm()) {
                maxBarrier = form.maxBarrier(kind());
                double previous = data.contains("dnaCapacity") ? data.getDouble("dnaCapacity") : maxBarrier;
                barrier = data.contains("dnaShield") && previous > 0
                        ? maxBarrier * Math.clamp(data.getDouble("dnaShield") / previous, 0, 1) : maxBarrier;
                lastDamageAt = data.contains("dnaLastHit") ? data.getLong("dnaLastHit") : Long.MIN_VALUE;
        }
    }
}

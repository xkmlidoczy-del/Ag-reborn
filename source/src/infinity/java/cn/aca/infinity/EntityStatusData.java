package cn.aca.infinity;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class EntityStatusData implements INBTSerializable<CompoundTag> {
    // Soul Stone: Hukou ("查户口") & Social Death & Bystander Gawk & Call Parents Retreat
    public String fatherName = "";
    public String motherName = "";
    public long hukouUntil = 0L;
    public long shameUntil = 0L;
    public long parentSlowUntil = 0L;
    public Vec3 parentFearOrigin = Vec3.ZERO;
    public int hukouCalls = 0;

    public int gawkTargetId = -1;
    public long gawkUntil = 0L;

    // Space Stone: Singularity Tether ("虫洞锚定" -> Power Resonance!)
    public long spaceTetherUntil = 0L;
    public Vec3 spaceTetherAnchor = Vec3.ZERO;

    // Time Stone: Local Stasis ("期末停滞") + Delayed 1.35x Temporal Reckoning Pool + Enemy 3s History
    public long timeFrozenUntil = 0L;
    public Vec3 frozenPos = Vec3.ZERO;
    public Vec3 frozenDelta = Vec3.ZERO;
    public float frozenYaw = 0f;
    public float frozenPitch = 0f;
    public float frozenDamagePool = 0.0f;
    public UUID frozenAttacker = null;
    public boolean frozenProjectileReflected = false;
    public Vec3 pastPos3s = Vec3.ZERO;
    public Vec3 prevSnapshotPos = Vec3.ZERO;
    public long lastHistorySampleTick = 0L;

    // Mind Stone: Classroom Roll-Call ("课堂点名罚站") & 4-Layer Forced Mind Control ("傀儡倒戈")
    public long mindStandUntil = 0L;
    public UUID mindMaster = null;
    public long mindControlUntil = 0L;
    public UUID mindFocus;
    public long nextMindSearch;
    public long lastPuppetSwingTick = -100L;

    // Reality Stone: Law-Grade Confiscation & Unarmed Shrink/Weaken + Crimson Crystal Snare
    public long disarmedUntil = 0L;
    public ItemStack confiscatedMain = ItemStack.EMPTY;
    public ItemStack confiscatedOff = ItemStack.EMPTY;
    public long crystalSnareUntil = 0L;

    // Power Stone: Parental Wrath Vulnerability ("家长震怒破防")
    public long shatteredUntil = 0L;

    public boolean hasHukou(long now) {
        return hukouUntil > now && !fatherName.isEmpty();
    }

    public boolean isAshamed(long now) {
        return shameUntil > now;
    }

    public boolean isParentSlowed(long now) {
        return parentSlowUntil > now;
    }

    public boolean isGawking(long now) {
        return gawkUntil > now && gawkTargetId >= 0;
    }

    public boolean isSpaceTethered(long now) {
        return spaceTetherUntil > now;
    }

    public boolean isTimeFrozen(long now) {
        return timeFrozenUntil > now;
    }

    public boolean isStandingAttention(long now) {
        return mindStandUntil > now;
    }

    public boolean isMindControlled(long now) {
        return mindControlUntil > now && mindMaster != null;
    }

    public boolean isDisarmed(long now) {
        return disarmedUntil > now;
    }

    public boolean isCrystalSnared(long now) {
        return crystalSnareUntil > now;
    }

    public boolean isShattered(long now) {
        return shatteredUntil > now;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (!fatherName.isEmpty()) tag.putString("Father", fatherName);
        if (!motherName.isEmpty()) tag.putString("Mother", motherName);
        tag.putLong("HukouUntil", hukouUntil);
        tag.putLong("ShameUntil", shameUntil);
        tag.putLong("ParentSlowUntil", parentSlowUntil);
        tag.putDouble("FearX", parentFearOrigin.x);
        tag.putDouble("FearY", parentFearOrigin.y);
        tag.putDouble("FearZ", parentFearOrigin.z);
        tag.putInt("HukouCalls", hukouCalls);
        tag.putLong("SpaceTetherUntil", spaceTetherUntil);
        tag.putDouble("TetherX", spaceTetherAnchor.x);
        tag.putDouble("TetherY", spaceTetherAnchor.y);
        tag.putDouble("TetherZ", spaceTetherAnchor.z);
        tag.putLong("TimeFrozenUntil", timeFrozenUntil);
        tag.putDouble("FrozenX", frozenPos.x);
        tag.putDouble("FrozenY", frozenPos.y);
        tag.putDouble("FrozenZ", frozenPos.z);
        tag.putFloat("FrozenDamagePool", frozenDamagePool);
        if (frozenAttacker != null) tag.putUUID("FrozenAttacker", frozenAttacker);
        tag.putLong("MindStandUntil", mindStandUntil);
        if (mindMaster != null) tag.putUUID("MindMaster", mindMaster);
        tag.putLong("MindControlUntil", mindControlUntil);
        if (mindFocus != null) tag.putUUID("MindFocus", mindFocus);
        tag.putLong("DisarmedUntil", disarmedUntil);
        tag.putLong("CrystalSnareUntil", crystalSnareUntil);
        if (!confiscatedMain.isEmpty()) tag.put("ConfiscatedMain", confiscatedMain.save(provider));
        if (!confiscatedOff.isEmpty()) tag.put("ConfiscatedOff", confiscatedOff.save(provider));
        tag.putLong("ShatteredUntil", shatteredUntil);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        fatherName = tag.getString("Father");
        motherName = tag.getString("Mother");
        hukouUntil = tag.getLong("HukouUntil");
        shameUntil = tag.getLong("ShameUntil");
        parentSlowUntil = tag.getLong("ParentSlowUntil");
        parentFearOrigin = new Vec3(tag.getDouble("FearX"), tag.getDouble("FearY"), tag.getDouble("FearZ"));
        hukouCalls = tag.getInt("HukouCalls");
        spaceTetherUntil = tag.getLong("SpaceTetherUntil");
        spaceTetherAnchor = new Vec3(tag.getDouble("TetherX"), tag.getDouble("TetherY"), tag.getDouble("TetherZ"));
        timeFrozenUntil = tag.getLong("TimeFrozenUntil");
        frozenPos = new Vec3(tag.getDouble("FrozenX"), tag.getDouble("FrozenY"), tag.getDouble("FrozenZ"));
        frozenDamagePool = tag.getFloat("FrozenDamagePool");
        frozenAttacker = tag.hasUUID("FrozenAttacker") ? tag.getUUID("FrozenAttacker") : null;
        mindStandUntil = tag.getLong("MindStandUntil");
        mindMaster = tag.hasUUID("MindMaster") ? tag.getUUID("MindMaster") : null;
        mindControlUntil = tag.getLong("MindControlUntil");
        mindFocus = tag.hasUUID("MindFocus") ? tag.getUUID("MindFocus") : null;
        disarmedUntil = tag.getLong("DisarmedUntil");
        crystalSnareUntil = tag.getLong("CrystalSnareUntil");
        confiscatedMain = tag.contains("ConfiscatedMain") ? ItemStack.parseOptional(provider, tag.getCompound("ConfiscatedMain")) : ItemStack.EMPTY;
        confiscatedOff = tag.contains("ConfiscatedOff") ? ItemStack.parseOptional(provider, tag.getCompound("ConfiscatedOff")) : ItemStack.EMPTY;
        shatteredUntil = tag.getLong("ShatteredUntil");
    }
}

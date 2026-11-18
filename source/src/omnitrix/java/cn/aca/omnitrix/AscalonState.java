package cn.aca.omnitrix;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class AscalonState implements INBTSerializable<CompoundTag> {
    boolean armored;
    long transitionTick = -1000L;
    float siphonCharge = 100f;
    float flightBoost;
    int machTier;
    int flightIdleTicks;
    double smoothedServerSpeed;
    long lastMachCueAt = -1000L;
    Vec3 lastFlightPos;
    long slashAt;
    long riftAt;
    long tempestAt;
    long syzygyAt;
    long skySyzygyUntil;
    long parryCounterUntil;
    long iaidoChargeSince = Long.MIN_VALUE;
    long riftRecallUntil;
    Vec3 riftOrigin;
    Vec3 riftDest;
    long phantomArrayUntil;
    long lastPhantomVolleyAt = -1000L;
    long lastSwingAt = Long.MIN_VALUE;
    long lastParryCueAt = -1000L;
    int comboStep;
    int crescentComboStep;
    long crescentChainUntil;
    int revision;
    record BufferedCast(WristNetwork.Request request, long expires) {}
    BufferedCast bufferedCast;

    void clearCast() {
        bufferedCast = null;
        iaidoChargeSince = Long.MIN_VALUE;
        riftRecallUntil = 0;
        riftOrigin = riftDest = null;
    }

    public boolean armored() { return armored; }
    public long transitionTick() { return transitionTick; }
    public float siphonCharge() { return siphonCharge; }
    public float flightBoost() { return flightBoost; }
    public int machTier() { return machTier; }
    public long slashAt() { return slashAt; }
    public long riftAt() { return riftAt; }
    public long tempestAt() { return tempestAt; }
    public long syzygyAt() { return syzygyAt; }
    public long skySyzygyUntil() { return skySyzygyUntil; }
    public long parryCounterUntil() { return parryCounterUntil; }
    public long iaidoChargeSince() { return iaidoChargeSince; }
    public long riftRecallUntil() { return riftRecallUntil; }
    public long phantomArrayUntil() { return phantomArrayUntil; }
    public int comboStep() { return comboStep; }
    public int revision() { return revision; }

    void addCharge(float delta) {
        siphonCharge = Math.clamp(siphonCharge + delta, 0f, 100f);
        revision++;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 2);
        tag.putBoolean("armored", armored);
        tag.putFloat("siphonCharge", siphonCharge);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        armored = tag.getBoolean("armored");
        siphonCharge = tag.contains("siphonCharge") ? Math.clamp(tag.getFloat("siphonCharge"), 0f, 100f) : 100f;
    }
}

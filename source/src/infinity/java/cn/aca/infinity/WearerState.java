package cn.aca.infinity;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class WearerState implements INBTSerializable<CompoundTag> {
    public record TemporalSnapshot(long tick, Vec3 pos, float yaw, float pitch, float health, int food, float saturation) {}

    public ItemStack gauntlet = ItemStack.EMPTY;
    public boolean wheelOpen = false;
    public boolean socketMode = false;
    public final long[] primaryReadyAt = new long[Stone.values().length];
    public final long[] secondaryReadyAt = new long[Stone.values().length];
    public long snapReadyAt = 0L;
    public long soulSaveReadyAt = 0L;
    public long snapAnimAt = -1000L;
    public long castAnimAt = -1000L;
    public int castAnimStone = 0;
    public int revision = 0;

    public final Deque<TemporalSnapshot> history = new ArrayDeque<>(16);

    public boolean equipped() {
        return !gauntlet.isEmpty() && gauntlet.is(AcaInfinity.INFINITY_GAUNTLET.get());
    }

    public GauntletData data() {
        if (!equipped()) return GauntletData.EMPTY;
        return gauntlet.getOrDefault(AcaInfinity.GAUNTLET_DATA.get(), GauntletData.EMPTY);
    }

    public void updateData(GauntletData updated) {
        if (equipped()) {
            gauntlet.set(AcaInfinity.GAUNTLET_DATA.get(), updated);
            revision++;
        }
    }

    public void recordSnapshot(long tick, Vec3 pos, float yaw, float pitch, float health, int food, float saturation) {
        while (!history.isEmpty() && tick - history.peekFirst().tick() > 120L) {
            history.removeFirst();
        }
        history.addLast(new TemporalSnapshot(tick, pos, yaw, pitch, health, food, saturation));
        while (history.size() > 14) {
            history.removeFirst();
        }
    }

    public TemporalSnapshot oldestSnapshot() {
        return history.peekFirst();
    }

    void clampCastCooldowns(long now) {
        for (int i = 0; i < primaryReadyAt.length; i++) {
            primaryReadyAt[i] = Math.min(primaryReadyAt[i], now + Stone.PRIMARY_COOLDOWN_TICKS);
            secondaryReadyAt[i] = Math.min(secondaryReadyAt[i], now + Stone.SECONDARY_COOLDOWN_TICKS);
        }
        snapReadyAt = Math.min(snapReadyAt, now + InfinityConfig.snapCooldownTicks());
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (!gauntlet.isEmpty()) {
            tag.put("Gauntlet", gauntlet.save(provider));
        }
        long[] prim = new long[Stone.values().length];
        long[] sec = new long[Stone.values().length];
        System.arraycopy(primaryReadyAt, 0, prim, 0, prim.length);
        System.arraycopy(secondaryReadyAt, 0, sec, 0, sec.length);
        tag.putLongArray("PrimaryReady", prim);
        tag.putLongArray("SecondaryReady", sec);
        tag.putLong("SnapReady", snapReadyAt);
        tag.putLong("SoulSaveReady", soulSaveReadyAt);
        tag.putInt("Revision", revision);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        gauntlet = tag.contains("Gauntlet") ? ItemStack.parseOptional(provider, tag.getCompound("Gauntlet")) : ItemStack.EMPTY;
        long[] prim = tag.getLongArray("PrimaryReady");
        for (int i = 0; i < Math.min(prim.length, primaryReadyAt.length); i++) primaryReadyAt[i] = prim[i];
        long[] sec = tag.getLongArray("SecondaryReady");
        for (int i = 0; i < Math.min(sec.length, secondaryReadyAt.length); i++) secondaryReadyAt[i] = sec[i];
        snapReadyAt = tag.getLong("SnapReady");
        soulSaveReadyAt = tag.getLong("SoulSaveReady");
        revision = tag.getInt("Revision");
        wheelOpen = false;
        socketMode = false;
    }
}

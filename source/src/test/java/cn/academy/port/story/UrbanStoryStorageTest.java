package cn.academy.port.story;

import net.minecraft.nbt.CompoundTag;
import static cn.academy.port.story.UrbanStoryProgress.*;

public final class UrbanStoryStorageTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var progress = new UrbanStoryProgress();
        progress.recordVerified(Objective.BROADCAST_SOURCE, Approach.EQUIPMENT);
        progress.recordVerified(Objective.BROADCAST_RESCUE, Approach.COMBAT);
        var encoded = UrbanStoryStorage.encode(progress);
        var loaded = UrbanStoryStorage.decode(encoded).orElseThrow();
        check(loaded.snapshot().equals(progress.snapshot()) && loaded.chapter() == Chapter.BROADCAST,
                "partial chapter and its routes survive encoding");
        var future = encoded.copy(); future.putInt("schema", 2);
        check(UrbanStoryStorage.decode(future).isEmpty(), "unknown future schema rejected");
        var badType = encoded.copy(); badType.getCompound("evidence").putInt("BROADCAST_LEAD", 1);
        check(UrbanStoryStorage.decode(badType).isEmpty(), "wrong evidence type rejected");
        var badName = encoded.copy(); badName.getCompound("evidence").putString("UNRECOGNIZED", "ABILITY");
        check(UrbanStoryStorage.decode(badName).isEmpty(), "unknown objective rejected");
        var badRoute = encoded.copy(); badRoute.getCompound("evidence").putString("BROADCAST_LEAD", "INVALID");
        check(UrbanStoryStorage.decode(badRoute).isEmpty(), "unknown route rejected");
        var skip = encoded.copy(); skip.getCompound("evidence").putString("STUDENT_WITNESS", "ABILITY");
        check(UrbanStoryStorage.decode(skip).isEmpty(), "future-chapter objective rejected");
        check(UrbanStoryStorage.decode(new CompoundTag()).isEmpty(), "missing schema rejected");
        System.out.println("PASS urban story NBT round trip and invalid-save checks");
    }
}

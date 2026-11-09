package cn.academy.port.story;

import java.util.EnumMap;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Per-player storage. No network request can directly record quest evidence. */
public final class UrbanStoryStorage {
    public static final String KEY = "academy:urban_story";
    private static final int SCHEMA = 1;
    private UrbanStoryStorage() {}

    public static CompoundTag encode(UrbanStoryProgress progress) {
        var tag = new CompoundTag();
        tag.putInt("schema", SCHEMA);
        var evidence = new CompoundTag();
        progress.snapshot().forEach((objective, approach) -> evidence.putString(objective.name(), approach.name()));
        tag.put("evidence", evidence);
        return tag;
    }

    /** Unknown schemas and malformed evidence fail closed; callers must not overwrite them. */
    public static Optional<UrbanStoryProgress> decode(CompoundTag tag) {
        if (tag == null || !tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != SCHEMA
                || !tag.contains("evidence", Tag.TAG_COMPOUND)) return Optional.empty();
        var evidence = tag.getCompound("evidence");
        var restored = new EnumMap<UrbanStoryProgress.Objective, UrbanStoryProgress.Approach>(UrbanStoryProgress.Objective.class);
        for (String key : evidence.getAllKeys()) {
            if (!evidence.contains(key, Tag.TAG_STRING)) return Optional.empty();
            try {
                var objective = UrbanStoryProgress.Objective.valueOf(key);
                var approach = UrbanStoryProgress.Approach.valueOf(evidence.getString(key));
                restored.put(objective, approach);
            } catch (IllegalArgumentException invalid) {
                return Optional.empty();
            }
        }
        return UrbanStoryProgress.restore(restored);
    }

    public static Optional<UrbanStoryProgress> read(ServerPlayer player) {
        if (player == null) return Optional.empty();
        var data = player.getPersistentData();
        if (!data.contains(KEY)) return Optional.of(new UrbanStoryProgress());
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) return Optional.empty();
        return decode(data.getCompound(KEY));
    }

    /** Native adapters call only after validating a world event on the server thread. */
    public static Optional<UrbanStoryProgress.Result> recordVerified(ServerPlayer player,
            UrbanStoryProgress.Objective objective, UrbanStoryProgress.Approach approach) {
        if (player == null || !player.serverLevel().getServer().isSameThread() || player.isRemoved()) return Optional.empty();
        var current = read(player);
        if (current.isEmpty()) return Optional.empty();
        var progress = current.get();
        var result = progress.recordVerified(objective, approach);
        if (result == UrbanStoryProgress.Result.RECORDED) player.getPersistentData().put(KEY, encode(progress));
        return Optional.of(result);
    }

    /** Copy raw data, including unsupported newer schemas, across death and player replacement. */
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer old && event.getEntity() instanceof ServerPlayer replacement
                && old.getPersistentData().contains(KEY))
            replacement.getPersistentData().put(KEY, old.getPersistentData().get(KEY).copy());
    }
}

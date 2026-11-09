package cn.academy.port.story;

import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;

/** Trusted story-NPC bridge. Player data and arbitrary entity names are never read as memories. */
public final class UrbanStoryClues {
    public static final String WITNESS_KEY = "academy:urban_witness";
    public static final String STUDENT_WITNESS = "student_witness";
    private static final double RANGE_SQUARED = 24 * 24;
    private UrbanStoryClues() {}

    public static boolean canProbe(ServerPlayer player, LivingEntity entity) {
        if (player == null || !(entity instanceof Villager) || !player.isAlive() || player.isRemoved()
                || player.isSpectator() || !entity.isAlive() || entity.isRemoved()
                || player.serverLevel() != entity.level() || !player.serverLevel().getServer().isSameThread()
                || player.serverLevel().getEntity(entity.getId()) != entity
                || !entity.getPersistentData().contains(WITNESS_KEY, Tag.TAG_STRING)
                || !STUDENT_WITNESS.equals(entity.getPersistentData().getString(WITNESS_KEY))
                || player.distanceToSqr(entity) > RANGE_SQUARED || !player.hasLineOfSight(entity)) return false;
        var progress = UrbanStoryStorage.read(player);
        return progress.isPresent() && progress.get().chapter() == UrbanStoryProgress.Chapter.STUDENT
                && !progress.get().completed(UrbanStoryProgress.Objective.STUDENT_WITNESS);
    }

    /** Called only after ability admission/payment; no client-provided clue or objective is accepted. */
    public static boolean probe(ServerPlayer player, LivingEntity entity) {
        if (!canProbe(player, entity)) return false;
        var result = UrbanStoryStorage.recordVerified(player, UrbanStoryProgress.Objective.STUDENT_WITNESS,
                UrbanStoryProgress.Approach.ABILITY);
        if (result.orElse(null) != UrbanStoryProgress.Result.RECORDED) return false;
        player.sendSystemMessage(Component.translatable("academy.urban.clue.student_witness"));
        return true;
    }
}

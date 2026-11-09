package cn.academy.port.skill;

import cn.academy.port.core.RangedSkillRanges;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Presentation witness for a surviving block on the actual shot's centerline. */
public record RailgunVisualContact(double distance, Direction face) {
    public static RailgunVisualContact sample(ServerPlayer player, ServerLevel level, Vec3 origin,
                                             Vec3 direction, double length) {
        if (player.serverLevel() != level || !Double.isFinite(length) || length <= 0
                || length > RangedSkillRanges.fixed("railgun")) return null;
        Vec3 end = origin.add(direction.scale(length));
        var context = new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        // A non-null miss terminates traversal immediately at an unloaded cell.
        BlockHitResult hit = BlockGetter.traverseBlocks(origin, end, context, (clip, pos) -> {
            if (!level.hasChunkAt(pos)) return BlockHitResult.miss(end, Direction.UP, pos.immutable());
            var state = level.getBlockState(pos);
            return clip.getBlockShape(state, level, pos).clip(origin, end, pos);
        }, ignored -> null);
        if (hit == null || hit.getType() != BlockHitResult.Type.BLOCK || hit.isInside()) return null;
        double distance = hit.getLocation().subtract(origin).dot(direction);
        return Double.isFinite(distance) && distance > 0 && distance <= length
                ? new RailgunVisualContact(distance, hit.getDirection()) : null;
    }
}

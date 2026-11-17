package cn.academy.biohum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

/** The small chamber fits in one chunk of the existing District 7 tower. */
final class AcademyTowerChamber {
    static final ResourceKey<Level> CITY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("academy", "story_city_map"));
    // CityPlan reserves the windowless tower at x/z [-184,-137], y [0,69].
    static final BlockPos SEAT = new BlockPos(-152, 30, -152);
    static final BlockPos ENTRY = SEAT.offset(0, 1, 3);
    static final BlockPos STREET = new BlockPos(-134, 0, -152);
    static final ChunkPos CHUNK = new ChunkPos(SEAT);
    private static final BlockPos CONSOLE = SEAT.offset(-2, 1, 2);
    private static final BlockPos LOW = SEAT.offset(-4, 0, -4);
    private static final BlockPos HIGH = SEAT.offset(4, 6, 4);
    static final AABB INTERIOR = new AABB(Vec3.atLowerCornerOf(LOW.offset(1, 1, 1)),
            Vec3.atLowerCornerOf(HIGH));
    private static final ResourceLocation TOWER_WALL =
            ResourceLocation.fromNamespaceAndPath("academy", "white_panel_wall");

    private AcademyTowerChamber() {}

    static boolean prepare(ServerLevel level) {
        if (!WorldEffects.inWorld(level, LOW) || !WorldEffects.inWorld(level, HIGH)) return false;
        if (level.getBlockState(SEAT).is(Blocks.WAXED_OXIDIZED_CUT_COPPER)
                && level.getBlockState(CONSOLE).is(Blocks.WAXED_OXIDIZED_COPPER_BULB)) return true;

        var wall = BuiltInRegistries.BLOCK.getOptional(TOWER_WALL).orElse(null);
        if (wall == null) return false;
        // Existing worlds receive the same chamber only inside an untouched tower volume.
        for (BlockPos pos : BlockPos.betweenClosed(LOW, HIGH)) {
            if (!level.getBlockState(pos).is(wall)) return false;
        }

        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        var placed = new ArrayList<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(LOW, HIGH)) {
            if (!level.setBlock(pos, chamberBlock(pos), flags)) {
                for (BlockPos changed : placed.reversed()) level.setBlock(changed, wall.defaultBlockState(), flags);
                return false;
            }
            placed.add(pos.immutable());
        }
        return true;
    }

    private static BlockState chamberBlock(BlockPos pos) {
        int x = Math.abs(pos.getX() - SEAT.getX()), z = Math.abs(pos.getZ() - SEAT.getZ());
        int y = pos.getY() - SEAT.getY();
        if (pos.equals(CONSOLE)) return Blocks.WAXED_OXIDIZED_COPPER_BULB.defaultBlockState();
        if (y == 0) {
            if (x <= 1 && z <= 1) return Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState();
            if (x == 3 && z == 3) return Blocks.SEA_LANTERN.defaultBlockState();
            return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        }
        if (y == 6 || x == 4 || z == 4) return Blocks.DEEPSLATE_TILES.defaultBlockState();
        return Blocks.AIR.defaultBlockState();
    }
}

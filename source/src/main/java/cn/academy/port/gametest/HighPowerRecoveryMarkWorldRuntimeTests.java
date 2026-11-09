package cn.academy.port.gametest;

import cn.academy.port.AcademyCraft;
import cn.academy.port.highpower.DisplacedNeedleEntity;
import cn.academy.port.highpower.DisplacedNeedleRegistry;
import cn.academy.port.highpower.RecoveryMarkWorld;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native geometry fixtures are declared; no survival progression or chunk unload is simulated. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerRecoveryMarkWorldRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_recovery_mark_world";
    private static final TicketType<UUID> TICKET = TicketType.create("academy_recovery_world_fixture", UUID::compareTo);
    private HighPowerRecoveryMarkWorldRuntimeTests() {}

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_block_impact_allows_only_shallow_original_surface_withdrawal(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.impact();
            Vec3 source = f.needle.position(), feet = f.p.position(); var identity = f.needle.identity();
            f.aim(f.needle.getBoundingBox().getCenter());
            var sight = RecoveryMarkWorld.sight(f.p, f.needle);
            h.assertTrue(sight.valid(), "native embedded needle exposes its actual body before the stone surface: " + sight.reason());
            var assessment = RecoveryMarkWorld.mark(f.p, f.needle);
            h.assertTrue(assessment.valid(), "actual native block impact permits narrowly outward extraction: " + assessment.reason());
            var check = RecoveryMarkWorld.check(f.p, f.needle, assessment.anchor());
            h.assertTrue(check.valid(), "unchanged original shape and source revalidate: " + check.reason());
            h.assertTrue(assessment.anchor().destination().equals(feet) && f.needle.position().equals(source)
                    && f.needle.identity().equals(identity) && f.needle.mediumSnapshot().getCount() == 1,
                    "read-only assessment preserves exact entity, item, identity and source");
            f.place(f.absolute(6.5, 5, 8.5));
            h.assertTrue(RecoveryMarkWorld.check(f.p, f.needle, assessment.anchor()).valid(), "current grounded player may move within all three range bounds");
            h.assertTrue(assessment.anchor().destination().equals(feet), "moving owner never moves frozen registration feet");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void original_embedding_state_source_and_later_wall_are_independently_revalidated(GameTestHelper h) {
        for (int mode = 0; mode < 3; mode++) try (Fixture f = new Fixture(h)) {
            f.impact(); var mark = RecoveryMarkWorld.mark(f.p, f.needle);
            h.assertTrue(mark.valid(), "declared embedded starting mark succeeds: " + mark.reason());
            if (mode == 0) f.block(10, 5, 8, Blocks.DIRT);
            if (mode == 1) f.needle.setPos(f.needle.position().add(-.01, 0, 0));
            if (mode == 2) f.block(8, 5, 8, Blocks.STONE);
            var result = RecoveryMarkWorld.check(f.p, f.needle, mark.anchor());
            h.assertFalse(result.valid(), "changed original block/source or new later obstacle refuses, mode=" + mode);
            h.assertTrue(result.reason().equals(mode < 2 ? "source_embedding_changed" : "corridor_block_collision"),
                    "specific native failure is the source exception or independent corridor: " + result.reason());
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void deep_and_multipart_native_sources_never_receive_a_whole_block_exception(GameTestHelper h) {
        for (int mode = 0; mode < 2; mode++) try (Fixture f = new Fixture(h)) {
            f.block(10, 5, 8, mode == 0 ? Blocks.STONE : Blocks.OAK_STAIRS);
            f.needle.setPos(f.absolute(mode == 0 ? 10.5 : 9.95, 5.125, 8.5));
            if (mode == 1) {
                BlockPos pos = h.absolutePos(new BlockPos(10, 5, 8));
                var shape = h.getLevel().getBlockState(pos).getCollisionShape(h.getLevel(), pos, CollisionContext.of(f.needle));
                h.assertTrue(shape.toAabbs().size() > 1, "actual native stair shape is multipart for this declared fixture");
            }
            var result = RecoveryMarkWorld.mark(f.p, f.needle);
            h.assertFalse(result.valid(), "deep-center or multipart original overlap refuses, mode=" + mode);
            h.assertTrue(result.reason().equals("corridor_block_collision"), "native source geometry is the refusal: " + result.reason());
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void removed_source_obstacle_uses_strict_clear_route_without_stale_waiver(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.impact(); var mark = RecoveryMarkWorld.mark(f.p, f.needle);
            h.assertTrue(mark.valid(), "original embedded registration: " + mark.reason());
            f.block(10, 5, 8, Blocks.AIR);
            f.needle.setPos(f.absolute(9.5, 5.25, 8.5));
            var result = RecoveryMarkWorld.check(f.p, f.needle, mark.anchor());
            h.assertTrue(result.valid(), "moved source can use a genuinely clear ordinary route: " + result.reason());
            f.block(9, 5, 8, Blocks.STONE);
            h.assertFalse(RecoveryMarkWorld.check(f.p, f.needle, mark.anchor()).valid(),
                    "new source block never inherits original embedded permission");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void frozen_destination_fluid_hazard_and_body_changes_refuse_read_only(GameTestHelper h) {
        for (int mode = 0; mode < 3; mode++) try (Fixture f = new Fixture(h)) {
            var mark = RecoveryMarkWorld.mark(f.p, f.needle);
            h.assertTrue(mark.valid(), "ordinary clear registration: " + mark.reason());
            Vec3 source = f.needle.position(); var identity = f.needle.identity();
            f.place(f.absolute(6.5, 5, 8.5));
            if (mode == 0) f.block(5, 5, 8, Blocks.WATER);
            if (mode == 1) f.block(5, 4, 8, Blocks.MAGMA_BLOCK);
            if (mode == 2) f.block(5, 5, 8, Blocks.STONE);
            var result = RecoveryMarkWorld.check(f.p, f.needle, mark.anchor());
            String expected = mode == 0 ? "destination_fluid" : mode == 1 ? "destination_hazard" : "destination_block_collision";
            h.assertTrue(!result.valid() && result.reason().equals(expected), "frozen landing is checked again: " + result.reason());
            h.assertTrue(f.needle.position().equals(source) && f.needle.identity().equals(identity)
                    && f.needle.mediumSnapshot().getCount() == 1, "unsafe destination changes no native entity or custody");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void exposed_aim_cannot_select_an_embedded_body_from_behind_its_wall(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.impact(); f.aim(f.needle.getBoundingBox().getCenter());
            h.assertTrue(RecoveryMarkWorld.sight(f.p, f.needle).valid(), "exposed native body is visible on impact side");
            f.place(f.absolute(12.5, 5, 8.5)); f.aim(f.needle.getBoundingBox().getCenter());
            var result = RecoveryMarkWorld.sight(f.p, f.needle);
            h.assertTrue(!result.valid() && result.reason().equals("needle_not_visible"), "original embedding never permits selecting through wall: " + result.reason());
            var mark = RecoveryMarkWorld.mark(f.p, f.needle);
            h.assertFalse(mark.valid(), "reverse retrieval into and through original wall is also forbidden");
            h.succeed();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final AcademyWirelessDeviceRuntimeTests.NativeMenuActors actors;
        final ServerPlayer p;
        final DisplacedNeedleEntity needle;
        final Map<BlockPos, BlockState> changed = new LinkedHashMap<>();
        final Set<ChunkPos> held = new LinkedHashSet<>();
        final UUID ticket = UUID.randomUUID();
        Fixture(GameTestHelper h) {
            this.h = h; actors = new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h); p = actors.player();
            for (int x = 3; x <= 13; x++) for (int z = 6; z <= 10; z++) block(x, 4, z, Blocks.STONE);
            place(absolute(5.5, 5, 8.5));
            p.getInventory().clearContent(); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), 2));
            needle = DisplacedNeedleRegistry.deploy(p, absolute(9.5, 5.25, 8.5), () -> true, () -> true).orElseThrow();
        }
        Vec3 absolute(double x, double y, double z) { return h.absoluteVec(new Vec3(x, y, z)); }
        void place(Vec3 at) {
            p.moveTo(at.x, at.y, at.z, 0, 0); p.setPose(Pose.STANDING); p.setNoGravity(false);
            p.getAbilities().flying = false; p.fallDistance = 0; p.setOnGround(true);
            p.move(MoverType.SELF, new Vec3(0, -.0784, 0)); p.setDeltaMovement(0, -.0784, 0); p.connection.resetPosition();
            h.assertTrue(p.onGround() && p.position().equals(at), "real native floor collision establishes grounded fixture");
        }
        void block(int x, int y, int z, Block block) {
            BlockPos pos = h.absolutePos(new BlockPos(x, y, z));
            h.assertTrue(h.getBounds().contains(Vec3.atCenterOf(pos)), "declared fixture edits stay inside template");
            ChunkPos chunk = new ChunkPos(pos);
            if (held.add(chunk)) {
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticket);
                h.getLevel().getChunk(chunk.x, chunk.z);
            }
            changed.putIfAbsent(pos, h.getLevel().getBlockState(pos)); h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        }
        void impact() {
            block(10, 5, 8, Blocks.STONE);
            needle.setDeltaMovement(1, 0, 0); needle.tick();
            h.assertTrue(needle.inGround && needle.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(10, 5, 8)))),
                    "real native AbstractArrow tick produces actual shallow block embedding");
        }
        void aim(Vec3 at) {
            Vec3 d = at.subtract(p.getEyePosition());
            p.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
            p.setXRot((float)-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
        }
        @Override public void close() {
            needle.discard(); actors.close();
            changed.forEach((pos, state) -> h.getLevel().setBlockAndUpdate(pos, state));
            held.forEach(chunk -> h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticket));
        }
    }
}

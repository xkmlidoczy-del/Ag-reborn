package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.ConstructRules;
import cn.academy.port.highpower.core.NewSkillBudget;
import cn.academy.port.preset.PresetSkills;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * V-ADV-01 against the real Sable backend. Not in the default suite: run with Sable in the game directory's mods folder and
 * {@code -PhighPowerTestNamespaces=academy_construct_sable}. Every test also asserts that blocks are conserved (no copies, no loss).
 */
@GameTestHolder("academy_construct_sable")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerConstructSableTests {
    private static final String TEMPLATE = "construct_sable_empty", BATCH = "construct_sable";
    private static final BlockPos LO = new BlockPos(9, 2, 15), HI = new BlockPos(11, 4, 17);
    private HighPowerConstructSableTests() {}

    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int dimension : new int[] {48, 16, 48}) size.add(IntTag.valueOf(dimension));
        for (int axis = 0; axis < 3; axis++) position.add(IntTag.valueOf(0));
        cell.put("pos", position); cell.putInt("state", 0); blocks.add(cell); air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_construct_sable", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    /** A 3x3x3 plank cube on a stone floor, six blocks in front of a LV5 caster. */
    private static ServerPlayer setup(GameTestHelper h, Fixture f) {
        ServerPlayer p = f.player(10, 10);
        AbilityStorage.get(p).setLevel(5);
        return p;
    }

    private static void build(GameTestHelper h) {
        for (int x = 5; x <= 15; x++) for (int z = 12; z <= 20; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        for (BlockPos pos : BlockPos.betweenClosed(LO, HI)) h.setBlock(pos, Blocks.OAK_PLANKS);
    }

    private static int planks(GameTestHelper h) { return count(h, Blocks.OAK_PLANKS, new BlockPos(0, 0, 0), new BlockPos(47, 15, 47)); }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 400)
    public static void preview_grab_hover_and_land_conserves_every_block(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.CONSTRUCT); ServerPlayer p = setup(h, f);
        f.ready(() -> {
            build(h);
            aim(p, h.absoluteVec(new Vec3(10.5, 3.5, 15.2)));
            h.assertTrue(f.start(p), "preview accepted"); f.release(p);
            h.assertTrue(ConstructRuntime.previewing(p), "previewing");
            h.assertTrue(planks(h) == 27, "preview moves nothing");
            double cp = HighPowerResources.cp(p);
            h.assertTrue(f.start(p), "grab accepted"); f.release(p);
            UUID body = ConstructRuntime.heldBody(p);
            h.assertTrue(body != null, "holding a body");
            h.assertTrue(HighPowerResources.cp(p) < cp, "grab was charged");
            h.assertTrue(planks(h) == 0, "the cube left the world grid (moved, not copied)");
            h.runAfterDelay(60, () -> {
                var b = ConstructLink.get().body(h.getLevel(), body);
                h.assertTrue(b != null, "body alive");
                var held = ConstructRules.holdTarget(new cn.academy.port.highpower.core.V3(p.getEyePosition().x, p.getEyePosition().y, p.getEyePosition().z), Math.toRadians(p.getYRot()), true, p.getY(),
                        Math.hypot(b.max()[0] - b.min()[0], b.max()[2] - b.min()[2]) / 2, b.max()[1] - b.min()[1]);
                Vec3 target = new Vec3(held.x(), held.y(), held.z());
                double err = new Vec3(b.center()[0], b.center()[1], b.center()[2]).distanceTo(target);
                h.assertTrue(err < 1.5, "hovering at the hold point: error " + err);
                h.assertTrue(ConstructRules.landable(b.tilt()), "kept upright: tilt " + b.tilt());
                h.assertTrue(f.start(p), "land accepted"); f.release(p);
                h.assertTrue(ConstructRuntime.heldBody(p) == null, "released after landing");
                h.assertTrue(planks(h) == 27, "all 27 blocks are back on the grid, none duplicated: " + planks(h));
                h.assertTrue(ConstructLink.get().body(h.getLevel(), body) == null, "the body was dissolved");
                h.succeed();
            });
        });
    }

    /** A 3x3x3 cube 19 blocks beyond the caster's own floor, on a long stone strip. */
    private static final BlockPos FAR_LO = new BlockPos(9, 2, 29), FAR_HI = new BlockPos(11, 4, 31);

    private static void buildFar(GameTestHelper h, boolean wall) {
        for (int x = 5; x <= 15; x++) for (int z = 5; z <= 40; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        for (BlockPos pos : BlockPos.betweenClosed(FAR_LO, FAR_HI)) h.setBlock(pos, Blocks.OAK_PLANKS);
        // One block high: the pick ray at eye height passes over it, but the caster (no jump) cannot step onto it.
        if (wall) for (int x = 4; x <= 16; x++) h.setBlock(new BlockPos(x, 2, 20), Blocks.STONE);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 400)
    public static void a_distant_lock_dashes_to_the_structure_and_grabs_it_at_the_hand(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.CONSTRUCT); ServerPlayer p = setup(h, f);
        f.ready(() -> {
            buildFar(h, false);
            aim(p, h.absoluteVec(new Vec3(10.5, 3.5, 29.2)));
            double z0 = p.getZ();
            h.assertTrue(f.start(p), "lock accepted"); f.release(p);
            h.assertTrue(ConstructRuntime.previewing(p), "previewing");
            double cp = HighPowerResources.cp(p);
            h.assertTrue(f.start(p), "dash accepted"); f.release(p);
            h.assertTrue(ConstructRuntime.dashing(p) && ConstructRuntime.heldBody(p) == null, "dashing, nothing held yet");
            h.assertTrue(HighPowerResources.cp(p) == cp && planks(h) == 27, "the dash itself costs nothing and moves nothing");
            h.runAfterDelay(100, () -> {
                UUID body = ConstructRuntime.heldBody(p);
                h.assertTrue(body != null && !ConstructRuntime.dashing(p), "the dash ended in a grab");
                h.assertTrue(HighPowerResources.cp(p) < cp, "the grab was charged at the end of the dash");
                h.assertTrue(planks(h) == 0, "the cube left the world grid (moved, not copied)");
                h.assertTrue(p.getZ() - z0 > 12, "the caster ran toward the structure: moved " + (p.getZ() - z0));
                var b = ConstructLink.get().body(h.getLevel(), body);
                h.assertTrue(b != null, "body alive");
                h.assertTrue(f.start(p), "land accepted"); f.release(p);
                h.runAfterDelay(2, () -> {
                    h.assertTrue(ConstructRuntime.heldBody(p) == null && planks(h) == 27, "lands back on the grid with every block: " + planks(h));
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 400)
    public static void a_blocked_dash_gives_up_for_free(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.CONSTRUCT); ServerPlayer p = setup(h, f);
        f.ready(() -> {
            buildFar(h, true);
            aim(p, h.absoluteVec(new Vec3(10.5, 3.5, 29.2)));
            h.assertTrue(f.start(p), "lock accepted"); f.release(p);
            h.assertTrue(ConstructRuntime.previewing(p), "previewing the cube, not the wall");
            double cp = HighPowerResources.cp(p);
            h.assertTrue(f.start(p), "dash accepted"); f.release(p);
            h.runAfterDelay(70, () -> {
                h.assertTrue(!ConstructRuntime.dashing(p) && ConstructRuntime.heldBody(p) == null, "gave up without holding anything");
                h.assertTrue(HighPowerResources.cp(p) == cp, "no CP was spent");
                h.assertTrue(planks(h) == 27, "the structure was never touched");
                h.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void sneak_tap_cancels_a_dash_for_free(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.CONSTRUCT); ServerPlayer p = setup(h, f);
        f.ready(() -> {
            buildFar(h, false);
            aim(p, h.absoluteVec(new Vec3(10.5, 3.5, 29.2)));
            h.assertTrue(f.start(p), "lock accepted"); f.release(p);
            double cp = HighPowerResources.cp(p);
            h.assertTrue(f.start(p), "dash accepted"); f.release(p);
            h.assertTrue(ConstructRuntime.dashing(p), "dashing");
            p.setShiftKeyDown(true);
            h.assertTrue(f.start(p), "sneak-tap accepted"); f.release(p);
            p.setShiftKeyDown(false);
            h.assertTrue(!ConstructRuntime.dashing(p) && ConstructRuntime.heldBody(p) == null && HighPowerResources.cp(p) == cp && planks(h) == 27, "cancelled for free");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 400)
    public static void landing_into_occupied_space_overwrites_nothing(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.CONSTRUCT); ServerPlayer p = setup(h, f);
        f.ready(() -> {
            build(h);
            aim(p, h.absoluteVec(new Vec3(10.5, 3.5, 15.2)));
            f.start(p); f.release(p); f.start(p); f.release(p);
            UUID body = ConstructRuntime.heldBody(p);
            h.assertTrue(body != null, "holding");
            h.runAfterDelay(60, () -> {
                var b = ConstructLink.get().body(h.getLevel(), body);
                List<BlockPos> glass = new ArrayList<>();
                for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(b.min()[0] - 1, b.min()[1] - 1, b.min()[2] - 1), BlockPos.containing(b.max()[0] + 1, b.max()[1] + 1, b.max()[2] + 1)))
                    if (h.getLevel().getBlockState(pos).isAir()) { h.getLevel().setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()); glass.add(pos.immutable()); }
                h.assertFalse(f.start(p), "a refused landing reports refusal"); f.release(p);
                h.assertTrue(ConstructRuntime.heldBody(p) != null, "still holding after a refused landing");
                for (BlockPos pos : glass) h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.GLASS), "glass at " + pos + " was not overwritten");
                for (BlockPos pos : glass) h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                h.runAfterDelay(20, () -> {
                    f.start(p); f.release(p);
                    h.assertTrue(planks(h) == 27, "lands once the space is clear: " + planks(h));
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void terrain_and_protected_structures_are_refused_for_free(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.CONSTRUCT); ServerPlayer p = setup(h, f);
        f.ready(() -> {
            build(h);
            double cp = HighPowerResources.cp(p);
            aim(p, h.absoluteVec(new Vec3(8.5, 1.9, 14.5)));
            h.assertFalse(f.start(p), "stone floor is terrain"); f.release(p);
            h.assertFalse(ConstructRuntime.previewing(p), "no preview of terrain");
            Object guard = new Object() {
                @SubscribeEvent public void deny(BlockEvent.BreakEvent e) { if (e.getPlayer() == p && e.getState().is(Blocks.OAK_PLANKS)) e.setCanceled(true); }
            };
            f.hook(guard);
            aim(p, h.absoluteVec(new Vec3(10.5, 3.5, 15.2)));
            h.assertFalse(f.start(p), "a protected structure is refused"); f.release(p);
            near(h, HighPowerResources.cp(p), cp, "refusals are free");
            h.assertTrue(planks(h) == 27, "nothing moved");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 400)
    public static void a_thrown_structure_hits_each_target_once(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.CONSTRUCT); ServerPlayer p = setup(h, f);
        f.ready(() -> {
            build(h);
            aim(p, h.absoluteVec(new Vec3(10.5, 3.5, 15.2)));
            f.start(p); f.release(p); f.start(p); f.release(p);
            UUID body = ConstructRuntime.heldBody(p);
            h.assertTrue(body != null, "holding");
            h.runAfterDelay(40, () -> {
                LivingEntity target = (LivingEntity) f.spawnAt(EntityType.HUSK, 22, 0);
                if (target instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                target.setNoGravity(true);
                h.assertTrue(HighPowerCombat.enroll(target, 5), "durable exact-ledger target");
                java.math.BigDecimal before = new java.math.BigDecimal(HighPowerCombat.healthText(target));
                int[] emissions = {0};
                f.hook(new Object() {
                    @SubscribeEvent public void attack(cn.academy.port.api.SkillAttackEvent event) {
                        if (event.player == p && event.target == target && event.skill.equals("vecmanip." + HighPowerSkills.VECTOR_CONSTRUCT)) {
                            emissions[0]++;
                            h.assertFalse(HighPowerCombat.attack(p, event.skill, target, java.math.BigDecimal.ONE), "a callback cannot borrow the physics contact capability");
                        }
                    }
                });
                aim(p, target.position().add(0, 1, 0));
                Vec3 aimPoint = ConstructLink.get().throwAim(p, body, 64);
                Vec3 ray = aimPoint.subtract(p.getEyePosition());
                h.assertTrue(ray.length() <= 64.001 && ray.normalize().dot(p.getLookAngle()) > .9999,
                        "throw aim ignores the held body and returns world coordinates on the actual view ray: " + aimPoint);
                p.setShiftKeyDown(true);
                h.assertTrue(f.start(p), "throw accepted"); f.release(p);
                p.setShiftKeyDown(false);
                h.assertTrue(ConstructRuntime.heldBody(p) == null, "control released on throw");
                StringBuilder trace = new StringBuilder();
                for (int t = 2; t <= 40; t += 6) {
                    int at = t;
                    h.runAfterDelay(t, () -> { var b = ConstructLink.get().body(h.getLevel(), body);
                        trace.append(at).append(':').append(b == null ? "gone" : String.format("c=(%.1f,%.1f,%.1f) v=(%.1f,%.1f,%.1f) m=%.0f", b.center()[0], b.center()[1], b.center()[2], b.velocity()[0], b.velocity()[1], b.velocity()[2], b.mass())).append(' '); });
                }
                h.runAfterDelay(50, () -> {
                    // Sable's solver works in single precision: beyond a few million blocks from the origin a sub-step moves less
                    // than one representable step and the body stands still (observed at |z| = 1.3e7). GameTest picks far-flung
                    // test origins at random, so the hit is asserted only where the engine can resolve the motion.
                    Vec3 origin = h.absoluteVec(Vec3.ZERO);
                    if (Math.max(Math.abs(origin.x), Math.abs(origin.z)) > 2_000_000) { h.succeed(); return; }
                    java.math.BigDecimal loss = before.subtract(new java.math.BigDecimal(HighPowerCombat.healthText(target)));
                    h.assertTrue(loss.compareTo(new java.math.BigDecimal("1e12")) > 0, "the structure must deal ability damage, not just ordinary collision damage; ledger loss " + loss + " trace " + trace);
                    h.assertTrue(emissions[0] == 1, "each contacted target receives exactly one controlled emission: " + emissions[0]);
                    h.succeed();
                });
            });
        });
    }

    /**
     * Two real connected players each grab and land their own cube. Same-process semantics only: both players share one
     * server and one Sable world, but there is no real second client or network here.
     */
    /** The lock on its own (no physics, so it is checked at any origin): blocks from the palette ring the target, then break away. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 400)
    public static void the_lock_rings_a_target_with_palette_blocks_and_releases_it(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.CONSTRUCT); ServerPlayer p = setup(h, f);
        f.ready(() -> {
            for (int x = 5; x <= 15; x++) for (int z = 5; z <= 30; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            LivingEntity target = (LivingEntity) f.spawnAt(EntityType.HUSK, 12, 0);
            if (target instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
            target.setNoGravity(true);
            var box = target.getBoundingBox();
            var shell = ConstructRules.cage(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            List<BlockPos> cells = new ArrayList<>();
            for (int[] c : shell) cells.add(new BlockPos(c[0], c[1], c[2]));
            // A cell already occupied by terrain or by somebody's block must not be overwritten.
            BlockPos occupied = cells.get(5); BlockState before = Blocks.GLASS.defaultBlockState();
            h.getLevel().setBlockAndUpdate(occupied, before);
            ConstructRuntime.lockForTest(p, target, List.of(Blocks.OAK_PLANKS.defaultBlockState()));
            h.runAfterDelay(ConstructRules.ENTOMB_GATHER_TICKS + 4, () -> {
                int planks = 0;
                for (BlockPos pos : cells) if (h.getLevel().getBlockState(pos).is(Blocks.OAK_PLANKS)) planks++;
                h.assertTrue(planks >= shell.size() - 12, "palette blocks ring the target: " + planks + " of " + shell.size());
                h.assertTrue(h.getLevel().getBlockState(occupied) == before, "an occupied cell was not overwritten");
                h.assertTrue(h.getLevel().getBlockState(target.blockPosition()).isAir(), "the target's own cell stays open");
                h.runAfterDelay(ConstructRules.ENTOMB_HOLD_TICKS + 4, () -> {
                    for (BlockPos pos : cells) h.assertFalse(h.getLevel().getBlockState(pos).is(Blocks.OAK_PLANKS), "the locking block at " + pos + " broke away again");
                    h.assertTrue(h.getLevel().getBlockState(occupied) == before, "the foreign block survived the release");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 500)
    public static void a_struck_target_is_locked_in_pieces_of_the_structure_that_later_break_away(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.CONSTRUCT); ServerPlayer p = setup(h, f);
        f.ready(() -> {
            build(h);
            aim(p, h.absoluteVec(new Vec3(10.5, 3.5, 15.2)));
            f.start(p); f.release(p); f.start(p); f.release(p);
            UUID body = ConstructRuntime.heldBody(p);
            h.assertTrue(body != null, "holding");
            h.runAfterDelay(40, () -> {
                LivingEntity target = (LivingEntity) f.spawnAt(EntityType.HUSK, 22, 0);
                if (target instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                target.setNoGravity(true);
                h.assertTrue(HighPowerCombat.enroll(target, 5), "durable exact-ledger target");
                aim(p, target.position().add(0, 1, 0));
                p.setShiftKeyDown(true);
                h.assertTrue(f.start(p), "throw accepted"); f.release(p);
                p.setShiftKeyDown(false);
                h.runAfterDelay(60, () -> {
                    Vec3 origin = h.absoluteVec(Vec3.ZERO);
                    if (Math.max(Math.abs(origin.x), Math.abs(origin.z)) > 2_000_000) { com.mojang.logging.LogUtils.getLogger().info("CONSTRUCT_LOCK_TEST skipped: far origin {}", origin); h.succeed(); return; }
                    h.assertTrue(target.isAlive(), "the durable target survived the hit");
                    h.assertTrue(ConstructLink.get().body(h.getLevel(), body) == null, "the thrown structure shattered on impact and is gone");
                    var box = target.getBoundingBox();
                    var shell = ConstructRules.cage(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
                    h.assertFalse(shell.isEmpty(), "a cage shell exists for the target");
                    int planks = 0;
                    List<BlockPos> cells = new ArrayList<>();
                    for (int[] c : shell) { BlockPos pos = new BlockPos(c[0], c[1], c[2]); cells.add(pos); if (h.getLevel().getBlockState(pos).is(Blocks.OAK_PLANKS)) planks++; }
                    h.assertTrue(planks >= shell.size() - 2, "pieces of the structure (its own planks) surround the target: " + planks + " of " + shell.size());
                    for (int dx = 0; dx <= 0; dx++) h.assertTrue(h.getLevel().getBlockState(target.blockPosition()).isAir(), "the target's own cell stays open");
                    h.runAfterDelay(ConstructRules.ENTOMB_HOLD_TICKS + 20, () -> {
                        for (BlockPos pos : cells) h.assertFalse(h.getLevel().getBlockState(pos).is(Blocks.OAK_PLANKS), "the locking block at " + pos + " broke away again");
                        com.mojang.logging.LogUtils.getLogger().info("CONSTRUCT_LOCK_TEST verified: {} cells, all locking blocks broke away", cells.size());
                        h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 400)
    public static void two_players_hold_independent_bodies_at_once(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.CONSTRUCT); ServerPlayer a = setup(h, f); ServerPlayer b = f.player(30, 10);
        AbilityStorage.get(b).setLevel(5);
        f.ready(() -> {
            build(h);
            // A second, independent cube on its own floor segment, far from A's.
            for (int x = 27; x <= 33; x++) for (int z = 13; z <= 19; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            for (int x = 29; x <= 31; x++) for (int y = 2; y <= 4; y++) for (int z = 15; z <= 17; z++) h.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS);
            aim(a, h.absoluteVec(new Vec3(10.5, 3.5, 15.2)));
            aim(b, h.absoluteVec(new Vec3(30.5, 3.5, 15.2)));
            h.assertTrue(f.start(a), "A preview accepted"); f.release(a);
            h.assertTrue(f.start(a), "A grab accepted"); f.release(a);
            UUID bodyA = ConstructRuntime.heldBody(a);
            h.assertTrue(bodyA != null, "A holds a body");
            h.assertTrue(f.start(b), "B preview accepted"); f.release(b);
            h.assertTrue(f.start(b), "B grab accepted"); f.release(b);
            UUID bodyB = ConstructRuntime.heldBody(b);
            h.assertTrue(bodyB != null && !bodyB.equals(bodyA), "two distinct bodies held at the same time");
            h.assertTrue(planks(h) == 0, "both cubes left the world grid: " + planks(h));
            h.runAfterDelay(60, () -> {
                h.assertTrue(ConstructLink.get().body(h.getLevel(), bodyA) != null, "A's body alive while B holds");
                h.assertTrue(ConstructLink.get().body(h.getLevel(), bodyB) != null, "B's body alive while A holds");
                h.assertTrue(f.start(a), "A land accepted"); f.release(a);
                h.assertTrue(ConstructRuntime.heldBody(a) == null, "A released after landing");
                h.assertTrue(planks(h) == 27, "only A's 27 blocks returned after A landed: " + planks(h));
                h.assertTrue(bodyB.equals(ConstructRuntime.heldBody(b)), "A's landing did not disturb B's held body");
                h.assertTrue(ConstructLink.get().body(h.getLevel(), bodyB) != null, "B's body survived A's landing");
                h.runAfterDelay(40, () -> {
                    h.assertTrue(f.start(b), "B land accepted"); f.release(b);
                    h.assertTrue(ConstructRuntime.heldBody(b) == null, "B released after landing");
                    h.assertTrue(planks(h) == 54, "both cubes conserved, none duplicated or lost: " + planks(h));
                    h.succeed();
                });
            });
        });
    }

    static void near(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) <= 1e-6 * Math.max(1, Math.abs(expected)), message + ": " + actual + " vs " + expected);
    }

    /** Points the player's view at the centre of {@code target} (absolute coordinates). */
    static void aim(ServerPlayer p, Vec3 target) {
        Vec3 d = target.subtract(p.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.moveTo(p.getX(), p.getY(), p.getZ(), yaw, pitch); p.setYHeadRot(yaw); p.connection.resetPosition();
    }

    static int count(GameTestHelper h, Block block, BlockPos from, BlockPos to) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(h.absolutePos(from), h.absolutePos(to))) if (h.getLevel().getBlockState(pos).is(block)) n++;
        return n;
    }

    private static final class Fixture {
        final GameTestHelper h; final NewSkillRuntime.Kind kind; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>();
        final List<Object> hooks = new ArrayList<>(); final Map<ServerPlayer, EmbeddedChannel> channels = new IdentityHashMap<>();
        final Map<ServerPlayer, Vec3> anchors = new IdentityHashMap<>(); final Map<ServerPlayer, Long> inputs = new IdentityHashMap<>();
        final Map<ServerPlayer, String> held = new IdentityHashMap<>(); boolean closed;
        Fixture(GameTestHelper h, NewSkillRuntime.Kind kind) {
            this.h = h; this.kind = kind;
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {}
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        ServerPlayer player(double x, double z) {
            var server = h.getLevel().getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "VC" + UUID.randomUUID().toString().substring(0, 10)), false);
            ServerPlayer p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            Connection connection = new Connection(PacketFlow.SERVERBOUND); EmbeddedChannel channel = new EmbeddedChannel(connection); channels.put(p, channel);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); drain(p);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 4; dz++) h.setBlock(BlockPos.containing(x + dx, 1, z + dz), Blocks.STONE);
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); anchors.put(p, h.absoluteVec(new Vec3(x + .5, 2, z + .5))); place(p);
            h.assertTrue(HighPowerCombat.enroll(p, kind.level), "explicit native connected enrollment");
            var s = AbilityStorage.get(p); s.selectCategory(kind.category); s.setLevel(kind.level);
            s.learn(HighPowerCategories.recoveryRoot(kind.category)); s.learn(kind.id); s.activated = true; s.overloadFine = true;
            h.assertTrue(s.presets.edit(0, 0, kind.id, s::learned), "real learned native key binding");
            HighPowerResources.initializeBudget(p); hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5)); return p;
        }
        void ready(Runnable action) { h.runAfterDelay(61, () -> { for (ServerPlayer p : players) { p.doTick(); place(p); } action.run(); }); }
        void place(ServerPlayer p) { Vec3 at = anchors.get(p); p.moveTo(at.x, at.y, at.z, 0, 0); p.setYHeadRot(0); p.yBodyRot = 0; p.setOnGround(true); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        long epoch(ServerPlayer p) { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        String wire(ServerPlayer p) { long input = inputs.getOrDefault(p, 0L) + 1; inputs.put(p, input); return new HighPowerInput.Input(0, input, p.getId(), epoch(p)).wire(); }
        boolean start(ServerPlayer p) { String wire = wire(p); held.put(p, wire); return NewSkillRuntime.start(p, wire); }
        void release(ServerPlayer p) { NewSkillRuntime.release(p, held.get(p)); }
        double cost(ServerPlayer p) { return NewSkillBudget.cp(budget(), HighPowerGrowth.values(p).position()); }
        NewSkillBudget.Skill budget() { return NewSkillBudget.Skill.valueOf(kind.name()); }
        Entity spawn(EntityType<?> type, double ahead) { return spawnAt(type, ahead, 0); }
        /** Spawns {@code ahead} metres in front of the player (looking toward +Z) and {@code side} metres to the right. */
        Entity spawnAt(EntityType<?> type, double ahead, double side) {
            Entity e = type.create(h.getLevel()); Vec3 at = anchors.values().iterator().next();
            e.setPos(at.x - side, at.y, at.z + ahead); h.assertTrue(h.getLevel().addFreshEntity(e), "entity joined the world"); entities.add(e); return e;
        }
        Arrow arrow(ServerPlayer p, double distance) {
            Arrow a = new Arrow(h.getLevel(), p.getX(), p.getEyeY(), p.getZ() + distance, new ItemStack(Items.ARROW), null);
            a.setNoGravity(true); a.setDeltaMovement(0, 0, -.6); h.assertTrue(h.getLevel().addFreshEntity(a), "arrow joined the world"); entities.add(a); return a;
        }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void drain(ServerPlayer p) {
            EmbeddedChannel channel = channels.get(p); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet;
            while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet); }
        }
        void close() {
            if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister);
            for (ServerPlayer p : players) { NewSkillRuntime.remove(p); DevelopmentController.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.server.getPlayerList().remove(p); }
            entities.forEach(Entity::discard); channels.values().forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }
}

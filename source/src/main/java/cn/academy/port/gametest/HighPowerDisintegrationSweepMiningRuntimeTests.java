package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillBlockDestroyEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.DisintegrationSweepRules;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW231 reaches the unchanged three native redirects only through real authenticated releases. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerDisintegrationSweepMiningRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_disintegration_sweep_mining";
    private static final List<Block> SOFT = List.of(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT,
            Blocks.MUD, Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM);
    private HighPowerDisintegrationSweepMiningRuntimeTests() {}

    @GameTest(template=TEMPLATE, batch=BATCH+"_seven", timeoutTicks=35)
    public static void sweep_mining_exact_seven_native_drops_one_wear_and_only_removal_cost(GameTestHelper h) {
        List<Fixture> fixtures = new ArrayList<>();
        for (int i = 0; i < SOFT.size(); i++) {
            Fixture f = new Fixture(h, i); f.target(SOFT.get(i), 31); f.shovel(); fixtures.add(f);
        }
        casts(h, fixtures, () -> {
            for (Fixture f : fixtures) {
                h.assertTrue(h.getLevel().isEmptyBlock(f.target.pos()), "reviewed 31m native removal " + f.target.state());
                h.assertValueEqual(f.hook.sources, 1, "one skill terrain event");
                h.assertValueEqual(f.hook.breaks, 1, "one native BreakEvent");
                h.assertValueEqual(f.drops(), 1, "ordinary native loot exactly once");
                h.assertValueEqual(f.p.getMainHandItem().getDamageValue(), 1, "native tool wear exactly once");
                near(h, f.rawCp(), f.before - f.cost(), "only one committed cell costs CP");
                h.assertFalse(HighPowerResources.hasReservation(f.p), "completed native claim is released");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_floor_face", timeoutTicks=35)
    public static void sweep_mining_floor_grazing_sample_does_not_omit_later_visible_cell_face(GameTestHelper h) {
        Fixture f = new Fixture(h, 0); BlockPos bottom = h.absolutePos(new BlockPos(12, 40, 12));
        // Match the native crouched client: feet at the floor top, column 5.5m forward,
        // yaw/pitch zero. The first lower-face sample clips the floor's thick outline.
        f.p.setPos(bottom.getX() + .5, bottom.getY(), bottom.getZ() - 5.5); f.p.connection.resetPosition();
        for (int x = bottom.getX() - 1; x <= bottom.getX() + 1; x++) {
            for (int z = f.p.blockPosition().getZ() - 1; z <= bottom.getZ() + 1; z++) {
                BlockPos floor = new BlockPos(x, bottom.getY() - 1, z);
                h.getLevel().getChunk(floor); f.positions.add(floor); h.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            }
        }
        List<BlockPos> column = List.of(bottom, bottom.above(), bottom.above(2));
        for (BlockPos pos : column) { f.positions.add(pos); h.getLevel().setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState()); }
        f.target = new BeamEtchingMining.Target(bottom, Blocks.DIRT.defaultBlockState(),
                new Vec3(bottom.getX() + .5, bottom.getY() + .5, bottom.getZ()), Direction.NORTH);
        f.shovel(); f.p.getMainHandItem().setDamageValue(6); f.before = f.rawCp();
        h.assertTrue(DisintegrationSweep.start(f.p, f.wire()), "native crouched floor-adjacent hold starts");
        h.runAfterDelay(8, () -> {
            h.assertTrue(DisintegrationSweep.release(f.p, f.wire()), "charged release freezes the original three dirt faces");
            var marks = DisintegrationSweep.marks(f.p);
            h.assertTrue(marks.stream().map(DisintegrationSweep.Mark::pos).collect(java.util.stream.Collectors.toSet()).equals(new HashSet<>(column)),
                    "failed first grazing sample cannot permanently omit the bottom cell");
            var lower = marks.stream().filter(mark -> mark.pos().equals(bottom)).findFirst().orElseThrow();
            h.assertTrue(lower.surface().y > bottom.getY() + .035, "unsafe first grazing point stays refused; a later existing ray supplies the admitted face");
            h.assertValueEqual(f.hook.sources, 0, "acquisition remains read-only before native work");
            h.assertValueEqual(f.p.getMainHandItem().getDamageValue(), 6, "preparation has no tool wear");
            near(h, f.rawCp(), f.before, "preparation has no CP debit");
            long[] counts = acquisitionCounts(f.p);
            h.assertTrue(counts[0] <= 402L * 193 && counts[1] <= 201L * 192, "retrying existing face samples preserves the finite 201-ray prefix budget");
            System.out.println("NEW231 floor-face mining release prefixes: outline=" + counts[0] + ", native=" + counts[1]);
        });
        h.runAfterDelay(20, () -> {
            for (BlockPos pos : column) h.assertTrue(h.getLevel().isEmptyBlock(pos), "each of the three original visible cells was removed once");
            h.assertValueEqual(f.hook.sources, 3, "three skill terrain decisions");
            h.assertValueEqual(f.hook.breaks, 3, "three ordinary native BreakEvents");
            h.assertValueEqual(f.p.getMainHandItem().getDamageValue(), 9, "actual client tool transition is six to nine");
            int drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(bottom).expandTowards(0, 2, 0).inflate(.9)).stream()
                    .filter(item -> item.getItem().is(Items.DIRT)).mapToInt(item -> item.getItem().getCount()).sum();
            h.assertValueEqual(drops, 3, "three ordinary native dirt drops");
            near(h, f.rawCp(), f.before - 3 * f.cost(), "only the three committed cells settle CP");
            h.assertFalse(HighPowerResources.hasReservation(f.p), "no claim remains after the native sweep"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_range", timeoutTicks=35)
    public static void sweep_mining_31_through_exact_48_metres_and_213_exact_12_regression(GameTestHelper h) {
        List<Fixture> sweeps = new ArrayList<>(); double[] distances = {31, 40, 47.999, 48, 48.001};
        for (int i = 0; i < distances.length; i++) {
            Fixture f = new Fixture(h, i); f.target(Blocks.DIRT, distances[i]); sweeps.add(f);
        }
        // This is the old public NEW213 entry. Learning NEW231 never lends it the new range.
        for (int i = 0; i < 4; i++) {
            Fixture f = new Fixture(h, 6 + i); double distance = new double[]{12, 12.001, 31, 48}[i];
            f.etchingTarget(distance); var capacity = BeamEtchingMining.newCapacity(); double before = f.rawCp();
            boolean allowed = i == 0;
            h.assertTrue(BeamEtchingMining.inspect(f.p, f.target, capacity).eligible() == allowed, "NEW213 exact twelve-metre inspection " + distance);
            h.assertTrue(BeamEtchingMining.attempt(f.p, f.target, 1, () -> true, capacity).removed() == allowed, "NEW213 unchanged native range " + distance);
            near(h, f.rawCp(), before - (allowed ? 1 : 0), "NEW213 retains existing cost contract");
            h.assertValueEqual(f.drops(), allowed ? 1 : 0, "NEW213 range does not create extra drops"); f.close();
        }
        casts(h, sweeps, () -> {
            for (int i = 0; i < sweeps.size(); i++) {
                Fixture f = sweeps.get(i); boolean allowed = distances[i] <= 48;
                h.assertTrue(h.getLevel().isEmptyBlock(f.target.pos()) == allowed, "fixed hand-to-surface range " + distances[i]);
                h.assertValueEqual(f.drops(), allowed ? 1 : 0, "range-bound ordinary native loot");
                near(h, f.rawCp(), f.before - (allowed ? f.cost() : 0), "out-of-range cell is free");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_cancel", timeoutTicks=35)
    public static void sweep_mining_source_and_native_cancellation_are_free(GameTestHelper h) {
        List<Fixture> fixtures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Fixture f = new Fixture(h, i); f.target(Blocks.DIRT, 40); f.shovel(); fixtures.add(f);
            if (i == 0) f.hook.source = event -> event.setCanceled(true);
            else f.hook.nativeBreak = event -> event.setCanceled(true);
        }
        casts(h, fixtures, () -> {
            for (int i = 0; i < fixtures.size(); i++) {
                Fixture f = fixtures.get(i); f.assertRefused("permission cancellation");
                h.assertValueEqual(f.hook.sources, 1, "one source decision");
                h.assertValueEqual(f.hook.breaks, i, "native event is neither skipped nor duplicated");
                h.assertTrue(h.getLevel().getBlockState(f.target.pos()) == f.target.state(), "veto preserves exact cell");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_source_mutation", timeoutTicks=35)
    public static void sweep_mining_source_callbacks_recheck_48m_origin_and_cell_before_native_event(GameTestHelper h) {
        Fixture moved = new Fixture(h, 0); moved.target(Blocks.DIRT, 48); moved.shovel();
        Fixture replaced = new Fixture(h, 1); replaced.target(Blocks.DIRT, 31); replaced.shovel();
        moved.hook.source = event -> moved.p.setPos(moved.p.position().add(0, 0, -.01));
        replaced.hook.source = event -> h.getLevel().setBlockAndUpdate(replaced.target.pos(), Blocks.STONE.defaultBlockState());
        casts(h, List.of(moved, replaced), () -> {
            for (Fixture f : List.of(moved, replaced)) {
                h.assertValueEqual(f.hook.sources, 1, "actual source callback changes an admitted input");
                h.assertValueEqual(f.hook.breaks, 0, "source mutation is refused before native BreakEvent");
                h.assertFalse(h.getLevel().isEmptyBlock(f.target.pos()), "no native removal after source mutation");
                f.assertRefused("source mutation");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_mutable", timeoutTicks=35)
    public static void sweep_mining_mutable_callbacks_revalidate_snapshot_tool_inventory_cp_and_owner(GameTestHelper h) {
        List<Fixture> fixtures = new ArrayList<>();
        for (int scenario = 0; scenario < 13; scenario++) {
            Fixture f = new Fixture(h, scenario); f.target(Blocks.DIRT, scenario == 4 ? 48 : 31); f.shovel(); fixtures.add(f);
            int which = scenario;
            f.hook.nativeBreak = event -> {
                switch (which) {
                    case 0 -> h.getLevel().setBlockAndUpdate(f.target.pos(), Blocks.STONE.defaultBlockState());
                    case 1 -> f.p.setItemInHand(InteractionHand.MAIN_HAND, f.p.getMainHandItem().copy());
                    case 2 -> f.p.getInventory().setItem(8, new ItemStack(Items.APPLE));
                    case 3 -> f.p.getPersistentData().put(HighPowerResources.DATA_KEY, f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy());
                    case 4 -> f.p.setPos(f.p.position().add(0, 0, -.01));
                    case 5 -> f.p.setYRot(15);
                    case 6 -> f.p.setGameMode(GameType.ADVENTURE);
                    case 7 -> f.p.containerMenu = ChestMenu.threeRows(17, f.p.getInventory());
                    case 8 -> AbilityStorage.get(f.p).presets.edit(0, 0, BeamEtching.ID, id -> true);
                    case 9 -> f.p.setHealth(0);
                    case 10 -> f.p.server.getPlayerList().remove(f.p);
                    case 11 -> f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", 0);
                    case 12 -> throw new IllegalStateException("deliberate sweep native callback failure");
                    default -> throw new AssertionError();
                }
            };
        }
        casts(h, fixtures, () -> {
            for (int i = 0; i < fixtures.size(); i++) {
                Fixture f = fixtures.get(i);
                h.assertValueEqual(f.hook.breaks, 1, "real mutable native callback reached " + i);
                h.assertFalse(h.getLevel().isEmptyBlock(f.target.pos()), "changed callback never performs native removal " + i);
                h.assertValueEqual(f.p.getMainHandItem().getDamageValue(), 0, "mutation refused before tool wear " + i);
                h.assertValueEqual(f.drops(), 0, "mutation refusal never synthesizes loot " + i);
                near(h, f.rawCp(), i == 11 ? 0 : f.before, "no whole-ledger rollback or refund mint " + i);
                h.assertFalse(HighPowerResources.hasReservation(f.p), "lifecycle/exception claim released " + i);
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_reentry", timeoutTicks=35)
    public static void sweep_mining_opaque_one_use_permit_and_cross_skill_reentry_share_native_scope(GameTestHelper h) {
        Fixture f = new Fixture(h, 0); f.target(Blocks.DIRT, 31); f.shovel();
        h.assertTrue(Modifier.isFinal(DisintegrationSweep.MiningPermit.class.getModifiers()), "capability cannot be subclassed");
        for (var constructor : DisintegrationSweep.MiningPermit.class.getDeclaredConstructors())
            h.assertTrue(Modifier.isPrivate(constructor.getModifiers()), "only the enclosing controller constructs a capability");
        h.assertFalse(BeamEtchingMining.attemptSweep(null, BeamEtchingMining.newCapacity()).removed(), "absent sweep capability cannot request range");
        DisintegrationSweep.MiningPermit[] captured = {null};
        f.hook.source = event -> captured[0] = activePermit(f.p);
        f.hook.nativeBreak = event -> {
            h.assertTrue(captured[0] != null, "actual controller capability observed without constructing one");
            h.assertFalse(BeamEtchingMining.attemptSweep(captured[0], BeamEtchingMining.newCapacity()).removed(), "already consumed permit rejects callback replay");
            h.assertFalse(BeamEtchingMining.attempt(f.p, f.target, 1, () -> true, BeamEtchingMining.newCapacity()).removed(), "NEW213 cannot nest inside NEW231");
            h.assertFalse(f.p.gameMode.destroyBlock(f.target.pos()), "native reentry rejected before another BreakEvent");
        };
        casts(h, List.of(f), () -> {
            h.assertTrue(h.getLevel().isEmptyBlock(f.target.pos()), "outer native attempt survives refused reentry");
            h.assertValueEqual(f.hook.sources, 1, "single outer skill event"); h.assertValueEqual(f.hook.breaks, 1, "single native BreakEvent");
            h.assertValueEqual(f.drops(), 1, "single native loot item");
            h.assertFalse(BeamEtchingMining.attemptSweep(captured[0], BeamEtchingMining.newCapacity()).removed(), "retained capability expires with its controller");
            near(h, f.rawCp(), f.before - f.cost(), "one settlement across all attempted reentries"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_exception", timeoutTicks=35)
    public static void sweep_mining_post_removal_exception_keeps_committed_cell_wear_and_cp(GameTestHelper h) {
        Fixture f = new Fixture(h, 0); f.target(Blocks.DIRT, 40); f.shovel();
        NeighborFailure hook = f.register(new NeighborFailure(f));
        casts(h, List.of(f), () -> {
            h.assertTrue(hook.thrown, "real post-removal native neighbor hook reached");
            h.assertTrue(h.getLevel().isEmptyBlock(f.target.pos()), "committed cell is never rolled back");
            h.assertValueEqual(f.p.getMainHandItem().getDamageValue(), 1, "already applied native wear retained");
            h.assertValueEqual(f.drops(), 0, "interrupted native loot is never fabricated");
            near(h, f.rawCp(), f.before - f.cost(), "actual cell removal settles even without native boolean return");
            h.assertFalse(HighPowerResources.hasReservation(f.p), "post-removal exception releases reservation"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_settlement", timeoutTicks=35)
    public static void sweep_mining_settlement_releases_claim_before_native_loot_callback(GameTestHelper h) {
        Fixture f = new Fixture(h, 0); f.target(Blocks.DIRT, 31); f.shovel();
        f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", 10);
        LootSpend hook = f.register(new LootSpend(f));
        casts(h, List.of(f), () -> {
            h.assertTrue(hook.called, "ordinary native loot callback reached");
            h.assertValueEqual(f.drops(), 1, "legitimate callback spend preserves native loot");
            near(h, f.rawCp(), 10 - f.cost() - 3, "native settlement and legitimate shared spend add without rollback");
            h.assertFalse(HighPowerResources.hasReservation(f.p), "no stranded claim"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_reservation", timeoutTicks=35)
    public static void sweep_mining_live_claim_preserves_legitimate_shared_spend_without_ledger_rollback(GameTestHelper h) {
        Fixture f = new Fixture(h, 0); f.target(Blocks.DIRT, 31); f.shovel();
        f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", 10);
        f.hook.nativeBreak = event -> {
            h.assertTrue(HighPowerResources.hasReservation(f.p), "native callback observes live CP claim");
            near(h, HighPowerResources.availableCp(f.p), 10 - f.cost(), "claim is excluded from spendable CP");
            h.assertFalse(HighPowerResources.trySpend(f.p, DualPoint.ID, 10, 1), "another skill cannot consume the claimed portion");
            h.assertTrue(HighPowerResources.trySpend(f.p, DualPoint.ID, 3, 1), "legitimate callback spends only unreserved CP");
            f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putString("unrelated", "retained");
        };
        casts(h, List.of(f), () -> {
            h.assertTrue(h.getLevel().isEmptyBlock(f.target.pos()), "valid reservation follows the shared ledger writer");
            near(h, f.rawCp(), 10 - 3 - f.cost(), "both costs survive without restoring an old ledger");
            h.assertTrue(f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getString("unrelated").equals("retained"), "unrelated callback data remains");
            h.assertValueEqual(f.drops(), 1, "shared spend does not duplicate or suppress native loot");
            h.assertFalse(HighPowerResources.hasReservation(f.p), "native claim is fully released"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_wear", timeoutTicks=35)
    public static void sweep_mining_empty_hand_and_final_durability_keep_native_harvest(GameTestHelper h) {
        Fixture empty = new Fixture(h, 0); empty.target(Blocks.DIRT, 31);
        Fixture broken = new Fixture(h, 1); broken.target(Blocks.DIRT, 40);
        ItemStack tool = new ItemStack(Items.WOODEN_SHOVEL); tool.setDamageValue(tool.getMaxDamage() - 1);
        broken.p.setItemInHand(InteractionHand.MAIN_HAND, tool);
        casts(h, List.of(empty, broken), () -> {
            for (Fixture f : List.of(empty, broken)) {
                h.assertTrue(h.getLevel().isEmptyBlock(f.target.pos()), "ordinary empty/final-tool native harvest");
                h.assertTrue(f.p.getMainHandItem().isEmpty(), "native final wear never recreates the tool");
                h.assertValueEqual(f.drops(), 1, "one native drop"); near(h, f.rawCp(), f.before - f.cost(), "one actual removal payment");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_capacity", timeoutTicks=35)
    public static void sweep_mining_capacity_components_and_unreviewed_tools_fail_closed(GameTestHelper h) {
        List<Fixture> fixtures = new ArrayList<>();
        for (int scenario = 0; scenario < 5; scenario++) {
            Fixture f = new Fixture(h, scenario); f.target(Blocks.DIRT, 31);
            switch (scenario) {
                case 0 -> { for (int slot = 0; slot < 36; slot++) f.p.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64)); f.shovel(); }
                case 1 -> {
                    for (int slot = 0; slot < 36; slot++) f.p.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64)); f.shovel();
                    ItemStack named = new ItemStack(Items.DIRT, 63); named.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("different components"));
                    f.p.getInventory().setItem(1, named);
                }
                case 2 -> f.p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
                case 3 -> f.p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
                case 4 -> {
                    ItemStack enchanted = new ItemStack(Items.IRON_SHOVEL);
                    enchanted.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
                    f.p.setItemInHand(InteractionHand.MAIN_HAND, enchanted);
                }
                default -> throw new AssertionError();
            }
            if (scenario == 4) {
                // The existing owner contract rejects unreviewed held enchantments before
                // preparation. Requiring a successful hold here would weaken that gate.
                f.before = f.rawCp();
                h.assertFalse(VanillaMiningContract.reviewedTool(f.p.getMainHandItem()), "Silk Touch is also outside the closed mining tool contract");
                h.assertTrue(HighPowerCombat.unsupportedReason(f.p).isPresent(), "existing owner contract rejects the held enchantment");
                h.assertFalse(DisintegrationSweep.start(f.p, f.wire()), "unreviewed held enchantment refuses before preparation");
                h.assertTrue(h.getLevel().getBlockState(f.target.pos()) == f.target.state(), "early refusal leaves the original cell intact");
                h.assertValueEqual(f.hook.sources, 0, "early refusal posts no source event");
                h.assertValueEqual(f.hook.breaks, 0, "early refusal posts no native event");
                f.assertRefused("held enchantment owner gate"); f.close();
            } else fixtures.add(f);
        }
        casts(h, fixtures, () -> {
            for (Fixture f : fixtures) {
                h.assertTrue(h.getLevel().getBlockState(f.target.pos()) == f.target.state(), "unreviewed input never enters harvest");
                h.assertValueEqual(f.hook.sources, 0, "failed admission is read-only");
                h.assertValueEqual(f.drops(), 0, "failed admission creates no loot"); near(h, f.rawCp(), f.before, "failed admission never reserves/debits");
                h.assertFalse(HighPowerResources.hasReservation(f.p), "no failed-admission claim");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_blocks", timeoutTicks=35)
    public static void sweep_mining_unreviewed_blocks_and_ores_never_enter_enhanced_native_path(GameTestHelper h) {
        List<Fixture> fixtures = new ArrayList<>();
        List<Block> refused = List.of(Blocks.STONE, Blocks.IRON_ORE, Blocks.SAND, Blocks.GRAVEL, Blocks.CHEST, Blocks.BEDROCK);
        for (int i = 0; i < refused.size(); i++) { Fixture f = new Fixture(h, i); f.target(refused.get(i), 31); f.shovel(); fixtures.add(f); }
        casts(h, fixtures, () -> {
            for (Fixture f : fixtures) {
                h.assertValueEqual(f.hook.sources, 0, "noncanonical target cannot reach a mutable mining callback");
                h.assertValueEqual(f.hook.breaks, 0, "noncanonical target cannot reach native BreakEvent");
                near(h, f.rawCp(), f.before, "unreviewed target costs nothing");
                h.assertFalse(HighPowerResources.hasReservation(f.p), "unreviewed target holds no claim");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_provenance", timeoutTicks=12)
    public static void sweep_mining_reuses_exact_canonical_loot_provenance_contract(GameTestHelper h) throws java.io.IOException {
        Fixture f = new Fixture(h, 0);
        for (Block block : SOFT) {
            String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
            var resource = f.p.server.getResourceManager().getResource(net.minecraft.resources.ResourceLocation.withDefaultNamespace("loot_table/blocks/" + name + ".json")).orElseThrow();
            byte[] bytes; try (var input = resource.open()) { bytes = input.readAllBytes(); }
            h.assertTrue(VanillaMiningContract.canonicalLootEvidence(block.defaultBlockState(), resource.sourcePackId(), resource.knownPackInfo(), bytes), "official exact-seven resource admitted");
            h.assertFalse(VanillaMiningContract.canonicalLootEvidence(block.defaultBlockState(), "file/copy.zip", resource.knownPackInfo(), bytes), "a byte-identical datapack copy is not official provenance");
            h.assertFalse(VanillaMiningContract.canonicalLootEvidence(block.defaultBlockState(), "vanilla", Optional.empty(), bytes), "a claimed pack id cannot impersonate canonical loot");
            bytes[bytes.length / 2] ^= 1;
            h.assertFalse(VanillaMiningContract.canonicalLootEvidence(block.defaultBlockState(), "vanilla", resource.knownPackInfo(), bytes), "same-name modified loot remains refused");
        }
        h.succeed();
    }

    private static void casts(GameTestHelper h, List<Fixture> fixtures, Runnable verify) {
        for (Fixture f : fixtures) {
            f.before = f.rawCp();
            h.assertTrue(DisintegrationSweep.start(f.p, f.wire()), "real authenticated crouched hold, lane=" + f.lane
                    + ", owner gate=" + HighPowerCombat.unsupportedReason(f.p).orElse("ready"));
        }
        h.runAfterDelay(8, () -> {
            for (Fixture f : fixtures) {
                near(h, f.rawCp(), f.before, "eight real preparation ticks are harmless");
                h.assertTrue(DisintegrationSweep.release(f.p, f.wire()), "same input releases prepared mining sweep");
            }
        });
        h.runAfterDelay(20, verify);
    }
    private static void near(GameTestHelper h, double actual, double expected, String why) {
        h.assertTrue(Math.abs(actual - expected) < 1e-7, why + ": " + actual + " / " + expected);
    }
    /** Observe an existing capability for replay tests; never manufacture or mutate one. */
    private static DisintegrationSweep.MiningPermit activePermit(ServerPlayer owner) {
        try {
            var contexts = DisintegrationSweep.class.getDeclaredField("CONTEXTS"); contexts.setAccessible(true);
            Object context = ((Map<?, ?>) contexts.get(null)).get(owner.getUUID());
            var mining = context.getClass().getDeclaredField("digging"); mining.setAccessible(true);
            return (DisintegrationSweep.MiningPermit) mining.get(context);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static long[] acquisitionCounts(ServerPlayer owner) {
        try {
            var contexts = DisintegrationSweep.class.getDeclaredField("CONTEXTS"); contexts.setAccessible(true);
            Object context = ((Map<?, ?>) contexts.get(null)).get(owner.getUUID());
            var surface = context.getClass().getDeclaredField("acquisitionSurfacePrefixes"); surface.setAccessible(true);
            var nativeShapes = context.getClass().getDeclaredField("acquisitionNativePrefixes"); nativeShapes.setAccessible(true);
            return new long[]{surface.getLong(context), nativeShapes.getLong(context)};
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    public static final class Hook {
        final ServerPlayer owner; int sources, breaks;
        Consumer<SkillBlockDestroyEvent> source = event -> {};
        Consumer<BlockEvent.BreakEvent> nativeBreak = event -> {};
        Hook(ServerPlayer owner) { this.owner = owner; }
        @SubscribeEvent public void source(SkillBlockDestroyEvent event) {
            if (event.player == owner && event.skill.equals("meltdowner.disintegration_sweep")) { sources++; source.accept(event); }
        }
        @SubscribeEvent public void nativeBreak(BlockEvent.BreakEvent event) {
            if (event.getPlayer() == owner) { breaks++; nativeBreak.accept(event); }
        }
    }
    public static final class NeighborFailure {
        final Fixture f; boolean thrown;
        NeighborFailure(Fixture f) { this.f = f; }
        @SubscribeEvent public void neighbor(BlockEvent.NeighborNotifyEvent event) {
            if (!thrown && event.getLevel() == f.p.level() && event.getPos().equals(f.target.pos()) && event.getState().isAir()
                    && HighPowerResources.hasReservation(f.p)) {
                thrown = true; throw new IllegalStateException("deliberate post-removal sweep notification exception");
            }
        }
    }
    public static final class LootSpend {
        final Fixture f; boolean called;
        LootSpend(Fixture f) { this.f = f; }
        @SubscribeEvent public void join(EntityJoinLevelEvent event) {
            if (!called && event.getLevel() == f.p.level() && event.getEntity() instanceof ItemEntity item && item.getItem().is(Items.DIRT)
                    && item.getBoundingBox().intersects(new AABB(f.target.pos()).inflate(1))) {
                called = true;
                f.h.assertFalse(HighPowerResources.hasReservation(f.p), "debit releases reservation before native loot callback");
                near(f.h, HighPowerResources.availableCp(f.p), 10 - f.cost(), "settled balance is not reserved twice");
                f.h.assertTrue(HighPowerResources.trySpend(f.p, DualPoint.ID, 3, 1), "legitimate callback can spend the unreserved remainder");
                f.h.assertFalse(BeamEtchingMining.attempt(f.p, f.target, 1, () -> true, BeamEtchingMining.newCapacity()).removed(), "native scope still rejects reentry after claim settlement");
            }
        }
    }
    private static final class Fixture {
        final GameTestHelper h; final ServerPlayer p; final EmbeddedChannel channel; final Hook hook; final int lane;
        final List<Object> hooks = new ArrayList<>(); final List<BlockPos> positions = new ArrayList<>();
        BeamEtchingMining.Target target; double before; boolean closed;
        Fixture(GameTestHelper h, int lane) {
            this.h = h; this.lane = lane; var server = h.getLevel().getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "Sweep" + UUID.randomUUID().toString().substring(0, 9)), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection); NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, p, cookie);
            var tutorial = cn.academy.port.tutorial.TutorialStorage.get(p);
            tutorial.restore(tutorial.conditionBits(), tutorial.activatedIds(), true, tutorial.firstOpened()); cn.academy.port.tutorial.TutorialStorage.save(p);
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); p.getAbilities().instabuild = false;
            p.setShiftKeyDown(true); p.setPose(Pose.CROUCHING); p.setYRot(0); p.setXRot(0); p.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(HighPowerCombat.enroll(p, 3), "real connected LV3 mining owner");
            var state = AbilityStorage.get(p); state.selectCategory("meltdowner"); state.setLevel(3);
            for (String id : List.of(DualPoint.ID, BeamEtching.ID, FoldedMirrorArray.ID, DisintegrationSweep.ID)) state.learn(id);
            state.activated = true; state.overloadFine = true; h.assertTrue(state.presets.edit(0, 0, DisintegrationSweep.ID, id -> true), "bind genuine learned sweep");
            HighPowerResources.initializeBudget(p); hook = register(new Hook(p));
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { close(); }
            });
        }
        void target(Block block, double distance) {
            BlockPos pos = h.absolutePos(new BlockPos(12 + lane * 4, 40, 56));
            Vec3 surface = new Vec3(pos.getX() + .5, pos.getY() + .5, pos.getZ());
            Vec3 wanted = surface.add(0, 0, -distance); p.setPos(wanted); p.setPos(p.position().add(wanted.subtract(DualPoint.hand(p, 0))));
            p.connection.resetPosition();
            // Fixture setup explicitly loads its short test corridor. The skill itself must not.
            for (int x = pos.getX() - 1; x <= pos.getX() + 1; x++)
                for (int z = p.blockPosition().getZ() - 1; z <= pos.getZ() + 1; z++) h.getLevel().getChunk(new BlockPos(x, pos.getY(), z));
            // Native drops stay near their observed cell, and refused sand/gravel cannot
            // leave unrelated falling-block entities in another test's world.
            positions.add(pos.below()); h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            positions.add(pos); h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
            target = new BeamEtchingMining.Target(pos, h.getLevel().getBlockState(pos), surface, Direction.NORTH);
        }
        void etchingTarget(double distance) {
            target(Blocks.DIRT, distance); p.setShiftKeyDown(false); p.setPose(Pose.STANDING);
            Vec3 wanted = target.surface().add(0, 0, -distance); p.setPos(p.position().add(wanted.subtract(p.getEyePosition()))); p.connection.resetPosition();
        }
        void shovel() { p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL)); }
        String wire() { return new HighPowerInput.Input(0, 1, p.getId(), LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p))).wire(); }
        double rawCp() { return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getDouble("cp"); }
        double cost() { return DisintegrationSweepRules.miningCost(java.math.BigDecimal.valueOf(3)); }
        int drops() {
            ItemStack expected = VanillaMiningContract.expectedDrop(target.state());
            return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(target.pos()).inflate(.9)).stream()
                    .filter(item -> ItemStack.isSameItemSameComponents(item.getItem(), expected)).mapToInt(item -> item.getItem().getCount()).sum();
        }
        void assertRefused(String why) {
            near(h, rawCp(), before, why + " leaves CP unchanged"); h.assertValueEqual(drops(), 0, why + " creates no loot");
            h.assertValueEqual(p.getMainHandItem().getDamageValue(), 0, why + " leaves durability unchanged");
            h.assertFalse(HighPowerResources.hasReservation(p), why + " releases reservation");
        }
        <T> T register(T listener) { hooks.add(listener); NeoForge.EVENT_BUS.register(listener); return listener; }
        void close() {
            if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); DisintegrationSweep.remove(p); BeamEtching.remove(p);
            for (BlockPos pos : positions) { h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).forEach(ItemEntity::discard); h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); }
            p.containerMenu = p.inventoryMenu; AbilityStorage.remove(p); p.server.getPlayerList().remove(p); channel.finishAndReleaseAll();
        }
    }
}

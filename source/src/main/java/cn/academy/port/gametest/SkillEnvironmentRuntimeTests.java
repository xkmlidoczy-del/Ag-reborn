package cn.academy.port.gametest;

import cn.academy.port.highpower.environment.SkillEnvironment;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder("academy_environment")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class SkillEnvironmentRuntimeTests {
    private static final String TEMPLATE = "empty";
    private static final String BATCH = "environment_native";

    @SubscribeEvent
    public static void install(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel world)) return;
        var structure = new CompoundTag();
        var size = new ListTag();
        for (int value : new int[]{32, 8, 32}) size.add(IntTag.valueOf(value));
        structure.put("size", size);
        structure.put("blocks", new ListTag());
        structure.put("entities", new ListTag());
        var palette = new ListTag();
        var air = new CompoundTag();
        air.putString("Name", "minecraft:air");
        palette.add(air);
        structure.put("palette", palette);
        world.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_environment", TEMPLATE))
                .load(world.registryAccess().lookupOrThrow(Registries.BLOCK), structure);
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void configured_mode_and_core_impulse_admission(GameTestHelper h) {
        var world = h.getLevel();
        var current = SkillEnvironment.mode(world);
        String expected = System.getProperty("academy.environment.expectedMode", "RESTORE");
        h.assertTrue(current.name().equals(expected), "server loads the requested environment mode " + expected + ", actual " + current);
        BlockPos cell = h.absolutePos(new BlockPos(8, 3, 8));
        world.setBlockAndUpdate(cell, Blocks.GLASS.defaultBlockState());
        try (var owner = new Owner(h)) {
            var cast = rail(owner.player);
            var roll = new AtomicInteger();
            boolean changed = cast.breakBlock(cell, () -> { roll.incrementAndGet(); return false; });
            h.assertTrue(changed == (current == SkillEnvironment.Mode.RESTORE || current == SkillEnvironment.Mode.PERMANENT),
                    "only RESTORE and PERMANENT are allowed to change block state");
            h.assertTrue(roll.get() == (current == SkillEnvironment.Mode.PERMANENT ? 1 : 0),
                    "drop supplier invoked once for permanent and never for temporary or denied cells");
            if (current == SkillEnvironment.Mode.RESTORE) {
                h.assertTrue(SkillEnvironment.isVacancy(world.getBlockState(cell)), "restore creates an owned native vacancy");
                world.setBlockAndUpdate(cell, Blocks.AIR.defaultBlockState());
            } else if (current == SkillEnvironment.Mode.PERMANENT) {
                h.assertTrue(world.getBlockState(cell).isAir(), "permanent commit produces actual air");
            } else {
                h.assertTrue(world.getBlockState(cell).is(Blocks.GLASS), "read-only modes preserve the original");
            }
            var target = h.spawn(EntityType.ZOMBIE, new BlockPos(7, 3, 6));
            target.setNoAi(true);
            var wind = SkillEnvironment.begin(owner.player, "aero_hand.aero_ground_blast", 4);
            h.assertFalse(wind.allowsBlockChanges(), "initial Aero pilot cannot mutate blocks");
            h.assertTrue(wind.push(target, new Vec3(1, 0, 0), .4) == (current != SkillEnvironment.Mode.OFF),
                    "only permitted non-off modes apply native impulse");
            h.assertFalse(wind.push(target, new Vec3(1, 0, 0), .4), "same cast cannot duplicate the native impulse");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void denied_hooks_and_changed_cells_never_commit(GameTestHelper h) {
        var world = h.getLevel();
        if (!SkillEnvironment.showsDebris() || SkillEnvironment.mode(world) == SkillEnvironment.Mode.COSMETIC) {
            h.succeed();
            return;
        }
        BlockPos cell = h.absolutePos(new BlockPos(8, 3, 8));
        world.setBlockAndUpdate(cell, Blocks.GLASS.defaultBlockState());
        try (var owner = new Owner(h)) {
            var hook = new PermissionProbe(owner.player.getUUID(), cell);
            NeoForge.EVENT_BUS.register(hook);
            try {
                h.assertFalse(rail(owner.player).breakBlock(cell, false), "cancelled native break is respected");
                h.assertTrue(hook.breaks == 1 && world.getBlockState(cell).is(Blocks.GLASS), "one permission probe, no write");
                hook.refuse = false;
                h.assertFalse(rail(owner.player).breakBlock(cell, false), "callback mutation invalidates the admitted original");
                h.assertTrue(world.getBlockState(cell).is(Blocks.COBBLESTONE), "callback-owned edit survives");
            } finally {
                NeoForge.EVENT_BUS.unregister(hook);
            }
            world.setBlockAndUpdate(cell, Blocks.GLASS.defaultBlockState());
            var rule = world.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
            boolean before = rule.get();
            try {
                rule.set(false, world.getServer());
                h.assertFalse(rail(owner.player).breakBlock(cell, false), "mobGriefing=false gates live terrain");
            } finally {
                rule.set(before, world.getServer());
            }
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void native_write_cap_and_permanent_loot_reentry(GameTestHelper h) {
        var world = h.getLevel();
        var mode = SkillEnvironment.mode(world);
        if (mode != SkillEnvironment.Mode.RESTORE && mode != SkillEnvironment.Mode.PERMANENT) {
            h.succeed();
            return;
        }
        try (var owner = new Owner(h)) {
            var cast = rail(owner.player);
            var cells = new java.util.ArrayList<BlockPos>();
            for (int i = 0; i < 13; i++) {
                BlockPos pos = h.absolutePos(new BlockPos(3 + (i % 5) * 4, 3, 3 + (i / 5) * 4));
                cells.add(pos);
                world.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                h.assertTrue(cast.breakBlock(pos, false) == (i < 12), "LV4 environment writes are capped at 12");
            }
            h.assertTrue(world.getBlockState(cells.getLast()).is(Blocks.STONE), "budget denial leaves final cell intact");
            for (BlockPos pos : cells) world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            if (mode == SkillEnvironment.Mode.PERMANENT) {
                BlockPos dropCell = h.absolutePos(new BlockPos(8, 3, 17));
                world.setBlockAndUpdate(dropCell, Blocks.STONE.defaultBlockState());
                var loot = new LootProbe(world, dropCell);
                NeoForge.EVENT_BUS.register(loot);
                try {
                    var permanent = rail(owner.player);
                    h.assertTrue(permanent.breakBlock(dropCell, true), "one permanent terrain commit");
                    h.assertFalse(permanent.breakBlock(dropCell, true), "same cast cannot repeat native loot settlement");
                    h.assertTrue(loot.drops == 1, "stone produces one native cobblestone stack");
                    h.assertTrue(world.getBlockState(dropCell).is(Blocks.GLASS), "later loot callback edit survives");
                } finally {
                    NeoForge.EVENT_BUS.unregister(loot);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 45)
    public static void restored_snapshot_survives_logout_and_foreign_edit(GameTestHelper h) {
        var world = h.getLevel();
        if (SkillEnvironment.mode(world) != SkillEnvironment.Mode.RESTORE) {
            h.succeed();
            return;
        }
        BlockPos cell = h.absolutePos(new BlockPos(8, 3, 8));
        BlockPos next = cell.east();
        world.setBlockAndUpdate(cell, Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(next, Blocks.GLASS.defaultBlockState());
        UUID id;
        try (var owner = new Owner(h)) {
            id = owner.player.getUUID();
            var cast = rail(owner.player);
            h.assertTrue(cast.breakBlock(cell, true), "native stone witness is admitted");
            h.assertTrue(cast.breakBlock(next, true), "adjacent owned vacancy permits progression");
            h.assertFalse(rail(owner.player).breakBlock(cell, true), "overlapping cast refuses an owned vacancy");
            var tile = world.getBlockEntity(cell);
            h.assertTrue(tile != null, "native transaction has a block entity");
            var data = tile.saveWithFullMetadata(world.registryAccess());
            h.assertTrue(data.getLong("expires") - world.getGameTime() == 600 && data.hasUUID("transaction")
                    && id.equals(data.getUUID("owner")), "same-cell snapshot has deadline, owner and transaction");
            data.putLong("expires", world.getGameTime());
            tile.loadWithComponents(data, world.registryAccess());
            tile.setChanged();
            world.setBlockAndUpdate(next, Blocks.COBBLESTONE.defaultBlockState());
        }
        var probe = new RestoreProbe(id, cell);
        NeoForge.EVENT_BUS.register(probe);
        h.runAfterDelay(30, () -> {
            NeoForge.EVENT_BUS.unregister(probe);
            h.assertTrue(probe.offlinePlace > 0, "offline restore used same original profile and placed through native protection");
            h.assertTrue(world.getBlockState(cell).is(Blocks.STONE), "offline restore recovered the exact original");
            h.assertTrue(world.getBlockState(next).is(Blocks.COBBLESTONE), "player replacement remains authoritative");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 2200)
    public static void saved_vacancy_restores_after_server_restart_even_if_mode_is_off(GameTestHelper h) {
        String phase = System.getProperty("academy.environment.restartPhase", "normal");
        if (phase.equals("normal")) {
            h.succeed();
            return;
        }
        var world = h.getLevel();
        // Separate from the GameTest arena, so test structure placement cannot recreate this witness.
        var cell = new BlockPos(4104, world.getMinBuildHeight() + 12, 4104);
        int chunkX = cell.getX() >> 4, chunkZ = cell.getZ() >> 4;
        world.setChunkForced(chunkX, chunkZ, true);
        world.getChunkAt(cell);
        if (phase.equals("write") || phase.equals("hold")) {
            h.assertTrue(SkillEnvironment.mode(world) == SkillEnvironment.Mode.RESTORE, "write phase needs restore mode");
            world.setBlockAndUpdate(cell, Blocks.GLASS.defaultBlockState());
            try (var owner = new Owner(h)) {
                h.assertTrue(rail(owner.player).breakBlock(cell, true), "persistent witness created by actual restore cast");
            }
            h.assertTrue(SkillEnvironment.isVacancy(world.getBlockState(cell)), "marker exists before server shutdown");
            if (phase.equals("hold")) {
                world.getServer().saveEverything(false, true, true);
                System.out.println("ACA_A2_ENVIRONMENT_RESTART_DURABLE_READY");
                h.runAfterDelay(1800, h::succeed);
            } else {
                h.succeed();
            }
            return;
        }
        h.assertTrue(phase.equals("read"), "known restart phase");
        h.assertTrue(SkillEnvironment.mode(world) == SkillEnvironment.Mode.OFF, "reload sees administrator's off mode");
        h.assertTrue(SkillEnvironment.isVacancy(world.getBlockState(cell)), "chunk loaded the original marker from disk");
        var tile = world.getBlockEntity(cell);
        h.assertTrue(tile != null && tile.saveWithFullMetadata(world.registryAccess()).hasUUID("transaction"),
                "loaded marker still carries its owned snapshot");
        h.runAfterDelay(660, () -> {
            h.assertTrue(world.getBlockState(cell).is(Blocks.GLASS), "offline owner and off mode do not lose a persisted restoration");
            world.setChunkForced(chunkX, chunkZ, false);
            h.succeed();
        });
    }

    private static SkillEnvironment.Cast rail(ServerPlayer owner) {
        return SkillEnvironment.begin(owner, "electromaster.railgun", 4);
    }

    public static final class PermissionProbe {
        private final UUID owner;
        private final BlockPos cell;
        private int breaks;
        private boolean refuse = true;

        PermissionProbe(UUID owner, BlockPos cell) { this.owner = owner; this.cell = cell; }

        @SubscribeEvent
        public void breakBlock(BlockEvent.BreakEvent event) {
            if (!event.getPlayer().getUUID().equals(owner) || !event.getPos().equals(cell)) return;
            breaks++;
            if (refuse) event.setCanceled(true);
            else event.getLevel().setBlock(cell, Blocks.COBBLESTONE.defaultBlockState(), 3);
        }
    }

    public static final class LootProbe {
        private final net.minecraft.server.level.ServerLevel world;
        private final BlockPos cell;
        private int drops;

        LootProbe(net.minecraft.server.level.ServerLevel world, BlockPos cell) {
            this.world = world;
            this.cell = cell;
        }

        @SubscribeEvent
        public void item(EntityJoinLevelEvent event) {
            if (event.getLevel() == world && event.getEntity() instanceof ItemEntity item
                    && item.getItem().is(Items.COBBLESTONE) && item.position().distanceToSqr(Vec3.atCenterOf(cell)) < 2) {
                drops += item.getItem().getCount();
                world.setBlockAndUpdate(cell, Blocks.GLASS.defaultBlockState());
            }
        }
    }

    public static final class RestoreProbe {
        private final UUID owner;
        private final BlockPos cell;
        private int offlinePlace;

        RestoreProbe(UUID owner, BlockPos cell) { this.owner = owner; this.cell = cell; }

        @SubscribeEvent
        public void placeBlock(BlockEvent.EntityPlaceEvent event) {
            if (event.getEntity() instanceof FakePlayer actor && actor.getUUID().equals(owner)
                    && event.getPos().equals(cell) && event.getPlacedBlock().is(Blocks.STONE)) offlinePlace++;
        }
    }

    private static final class Owner implements AutoCloseable {
        final ServerPlayer player;
        final EmbeddedChannel channel;

        Owner(GameTestHelper h) {
            var world = h.getLevel();
            var server = world.getServer();
            UUID id = UUID.randomUUID();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(id, "Env" + id.toString().substring(0, 8)), false);
            player = new ServerPlayer(server, world, cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(h.absoluteVec(new Vec3(5.5, 3, 5.5)));
            player.connection.resetPosition();
        }

        @Override public void close() {
            player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}

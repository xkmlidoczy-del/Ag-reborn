package cn.aca.domain67.qa;

import cn.aca.domain67.AcaDomain67;
import cn.aca.domain67.CursedLogEntity;
import cn.aca.domain67.Domain67Manager;
import cn.aca.domain67.Domain67Network;
import cn.aca.domain67.DomainTerrainVault;
import cn.aca.domain67.client.Domain67ClientCache;
import cn.academy.port.AbilityStorage;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.PlayerShieldArmor;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@Mod("aca_domain67_qa")
@GameTestHolder("aca_domain67_qa")
@PrefixGameTestTemplate(false)
public final class Domain67GameTests {
    public Domain67GameTests() {}

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void circus_broadcast_lures_and_marks_mobs(GameTestHelper helper) {
        Domain67Manager.clearAll();
        ServerPlayer player = connectedPlayer(helper);
        player.setGameMode(GameType.CREATIVE);
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        player.teleportTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);

        Zombie mob = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 4));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(AcaDomain67.CIRCUS_MEGAPHONE_67.get()));
        int count = Domain67Manager.castCircusBroadcast(player);
        helper.assertTrue(count >= 1, "Circus broadcast should mark at least 1 nearby mob");
        helper.assertTrue(Domain67Manager.isLured(mob.getUUID()), "Zombie should carry CIRCUS_LURED state");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "domain_expansion", timeoutTicks = 120)
    public static void domain_traps_diners_and_spawns_guard_log(GameTestHelper helper) {
        Domain67Manager.clearAll();
        ServerPlayer player = connectedPlayer(helper);
        player.setGameMode(GameType.CREATIVE);
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        player.teleportTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);

        Zombie mob = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 4));
        var dom = Domain67Manager.toggleOrExpandDomain(player);
        helper.assertTrue(dom != null, "Domain 67 should expand cleanly");
        helper.assertTrue(Math.abs(dom.radius - 32.0f) < 0.01f, "v1.1.0 Domain radius must be expanded to 32.0m");
        helper.assertTrue(dom.diners.containsKey(mob.getUUID()), "Nearby zombie should be trapped as a diner");

        mob.setPos(dom.center.x + dom.radius - 1.0, dom.center.y, dom.center.z);
        helper.runAfterDelay(3, () -> {
            var guardLogs = helper.getLevel().getEntitiesOfClass(CursedLogEntity.class,
                    AABB.ofSize(dom.center, 80, 48, 80), log -> log.getVariant() == CursedLogEntity.VARIANT_GUARD);
            helper.assertTrue(!guardLogs.isEmpty(), "Boundary approach at r=31m should spawn a guard log");
            Domain67Manager.dismissDomain(player, false);
            DomainTerrainVault.get(helper.getLevel()).restoreImmediate(helper.getLevel(), player.getUUID());
            helper.assertTrue(Domain67Manager.getActiveDomain(player.getUUID()) == null, "Domain should dismiss cleanly");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "wave_arena", timeoutTicks = 160)
    public static void wavefront_stage_transforms_terrain_protects_chests_and_restores_100pct(GameTestHelper helper) {
        Domain67Manager.clearAll();
        BlockPos grassFloor = new BlockPos(4, 1, 4);
        BlockPos marbleTile = new BlockPos(8, 1, 8);
        BlockPos treeTrunk = new BlockPos(5, 2, 4);
        BlockPos treeLeaves = new BlockPos(5, 3, 4);
        BlockPos protectedChest = new BlockPos(3, 2, 4);
        BlockPos protectedBedrock = new BlockPos(3, 1, 4);

        helper.setBlock(grassFloor, Blocks.GRASS_BLOCK);
        helper.setBlock(marbleTile, Blocks.DIRT);
        helper.setBlock(treeTrunk, Blocks.OAK_LOG);
        helper.setBlock(treeLeaves, Blocks.OAK_LEAVES);
        helper.setBlock(protectedBedrock, Blocks.BEDROCK);
        helper.setBlock(protectedChest, Blocks.CHEST);
        if (helper.getBlockEntity(protectedChest) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND, 64));
        }

        ServerPlayer player = connectedPlayer(helper);
        player.setGameMode(GameType.CREATIVE);
        BlockPos absOrigin = helper.absolutePos(new BlockPos(4, 2, 4));
        player.teleportTo(absOrigin.getX() + 0.5, absOrigin.getY(), absOrigin.getZ() + 0.5);

        Domain67Manager.toggleOrExpandDomain(player);

        // At tick +18, central floor is Warm Honey-Oak Parquet (STRIPPED_BIRCH_WOOD) & dining bay is Smooth Quartz,
        // natural tree is cleared, while Chest (64 Diamonds) + Bedrock remain 100% untouched!
        helper.runAfterDelay(18, () -> {
            helper.assertBlockPresent(Blocks.STRIPPED_BIRCH_WOOD, grassFloor);
            helper.assertBlockPresent(Blocks.SMOOTH_QUARTZ, marbleTile);
            helper.assertBlockPresent(Blocks.AIR, treeTrunk);
            helper.assertBlockPresent(Blocks.AIR, treeLeaves);
            helper.assertBlockPresent(Blocks.BEDROCK, protectedBedrock);
            helper.assertBlockPresent(Blocks.CHEST, protectedChest);
            if (helper.getBlockEntity(protectedChest) instanceof ChestBlockEntity chest) {
                helper.assertTrue(chest.getItem(0).getCount() == 64, "Chest contents must remain 100% intact");
            }

            // Dismiss domain -> triggers wave-back progressive collapse restoration across the 26m stage!
            Domain67Manager.dismissDomain(player, false);
            helper.runAfterDelay(14, () -> {
                helper.assertBlockPresent(Blocks.GRASS_BLOCK, grassFloor);
                helper.assertBlockPresent(Blocks.DIRT, marbleTile);
                helper.assertBlockPresent(Blocks.OAK_LOG, treeTrunk);
                helper.assertBlockPresent(Blocks.OAK_LEAVES, treeLeaves);
                helper.assertBlockPresent(Blocks.BEDROCK, protectedBedrock);
                helper.assertBlockPresent(Blocks.CHEST, protectedChest);
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void finished_noodles_grants_no_reward_branch(GameTestHelper helper) {
        Domain67Manager.clearAll();
        ServerPlayer player = connectedPlayer(helper);
        player.setGameMode(GameType.CREATIVE);
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        player.teleportTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);

        Zombie mob = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 4));
        var dom = Domain67Manager.toggleOrExpandDomain(player);
        var diner = dom.diners.get(mob.getUUID());
        helper.assertTrue(diner != null, "Diner state must exist");
        diner.noodlesRemaining = 0.05f;

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(diner.finishedNoReward, "Diner should transition to finishedNoReward=true");
            Domain67Manager.dismissDomain(player, false);
            DomainTerrainVault.get(helper.getLevel()).restoreImmediate(helper.getLevel(), player.getUUID());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "log_finisher", timeoutTicks = 120)
    public static void omni_rift_log_finisher_and_bento_cameo(GameTestHelper helper) {
        Domain67Manager.clearAll();
        ServerPlayer player = connectedPlayer(helper);
        player.setGameMode(GameType.CREATIVE);
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        player.teleportTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);

        Zombie mob = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 4));
        float initialHp = mob.getHealth();
        Domain67Manager.toggleOrExpandDomain(player);

        int punished = Domain67Manager.castOmniLogFinisher(player);
        helper.assertTrue(punished >= 1, "Omni-Rift Log Finisher should liquidate trapped diner");
        var logs = helper.getLevel().getEntitiesOfClass(CursedLogEntity.class, AABB.ofSize(player.position(), 80, 64, 80));
        helper.assertTrue(logs.size() >= 9, "Expected 8 barrage logs + 1 mega black-flash log, got " + logs.size());
        helper.assertTrue(!mob.isAlive() || mob.getHealth() < initialHp, "Target must take damage from sure-hit log");

        Zombie mob2 = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 5));
        Domain67Manager.dismissDomain(player, false);
        Domain67Manager.toggleOrExpandDomain(player);
        int bentoHits = Domain67Manager.castNoHeartburnBentoCameo(player);
        helper.assertTrue(bentoHits >= 1, "78-Yuan No-Heartburn Bento cameo should hit trapped diner");
        Domain67Manager.dismissDomain(player, false);
        DomainTerrainVault.get(helper.getLevel()).restoreImmediate(helper.getLevel(), player.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void full_world_wave_and_curtain_collapse_curves(GameTestHelper helper) {
        UUID fakeId = UUID.randomUUID();
        var syncOpen = new Domain67Network.DomainSyncPayload(fakeId, true, 0.0, 64.0, 0.0, 32.0f, 0, 360, 360, 0, List.of());
        var view = new Domain67ClientCache.ClientDomainView(syncOpen, 100L);

        helper.assertTrue(view.worldWaveRadius(105L, 0f) == 0.0f, "Beat 1 stasis wave radius must be 0");
        helper.assertTrue(view.shatterFactor(110L, 0f) > 0.4f, "Beat 2 must trigger Voronoi Reality-Glass shatter");
        helper.assertTrue(view.worldWaveRadius(142L, 0f) >= 315.0f, "Beat 3 wave must reach 320m full-world horizon");
        helper.assertTrue(view.exteriorCurtainPhase(134L, 0f) == 500, "Exterior JJK curtain must seal completely to ground");

        Domain67ClientCache.clearStateOnly();
        boolean firstAccepted = Domain67ClientCache.pushThrottledToast(
                "log", ";", "ui.aca_domain67.toast.log.title", "ui.aca_domain67.toast.log.sub", 2, 0xFFE86A4A, 34);
        boolean spamSuppressed = !Domain67ClientCache.pushThrottledToast(
                "log", ";", "ui.aca_domain67.toast.log.title", "ui.aca_domain67.toast.log.sub", 2, 0xFFE86A4A, 34);
        helper.assertTrue(firstAccepted && Domain67ClientCache.activeToast() != null, "First toast should display cleanly");
        helper.assertTrue(spamSuppressed, "Repeated cast within cooldown window must be suppressed (zero spam)");
        helper.assertFalse(Domain67ClientCache.pushThrottledToast(
                "need_dom", "[", "ui.aca_domain67.toast.need_domain.title", "ui.aca_domain67.toast.need_domain.sub",
                0, 0xFFE5B85C, 28), "A different category must not replace a visible message");

        Domain67ClientCache.clearStateOnly();
        UUID spectator = UUID.randomUUID();
        var openCue = new Domain67Network.VfxCuePayload(Domain67Network.CUE_DOMAIN_EXPAND,
                fakeId, Vec3.ZERO, Vec3.ZERO, 1);
        Domain67ClientCache.handleVfxCue(openCue, spectator);
        helper.assertTrue(Domain67ClientCache.activeToast() == null, "Observer should not see the caster's toast");
        for (int cueId : new int[]{Domain67Network.CUE_CIRCUS_BROADCAST, Domain67Network.CUE_LOG_BARRAGE,
                Domain67Network.CUE_BENTO_CAMEO, Domain67Network.CUE_GUARD_LOG}) {
            var cue = new Domain67Network.VfxCuePayload(cueId, fakeId, Vec3.ZERO, Vec3.ZERO, 1);
            Domain67ClientCache.handleVfxCue(cue, spectator);
            Domain67ClientCache.handleVfxCue(cue, fakeId);
        }
        helper.assertTrue(Domain67ClientCache.activeToast() == null, "Routine skills should use world effects");
        helper.assertTrue(Domain67ClientCache.rifts().size() == 22, "Both viewers must still receive their world effects");
        Domain67ClientCache.handleVfxCue(openCue, fakeId);
        var localToast = Domain67ClientCache.activeToast();
        helper.assertTrue(localToast != null, "Only the caster gets the entry toast");
        var closeCue = new Domain67Network.VfxCuePayload(Domain67Network.CUE_DOMAIN_CLOSE,
                fakeId, Vec3.ZERO, Vec3.ZERO, 0);
        Domain67ClientCache.handleVfxCue(closeCue, fakeId);
        helper.assertTrue(Domain67ClientCache.activeToast() == localToast, "Fast entry and exit cannot reanimate the status slot");

        view.beginCollapse(200L);
        helper.assertTrue(view.isCollapsing(), "Domain must enter collapsing phase");
        helper.assertTrue(view.shatterFactor(216L, 0f) > 0.4f, "Collapse must fracture dimensional ceiling");
        helper.assertTrue(view.exteriorCurtainPhase(216L, 0f) > 600, "Exterior curtain must enter Voronoi shatter range");
        helper.assertTrue(view.isExpired(235L), "Collapse sequence must expire cleanly after 32 ticks");
        Domain67ClientCache.clearStateOnly();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "academy_shield", timeoutTicks = 110)
    public static void shielded_player_takes_native_damage_without_domain_control(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var players = server.getPlayerList();
        boolean originalPvp = server.isPvpAllowed();
        var caster = connectedPlayer(helper);
        var diner = connectedPlayer(helper);
        // Let vanilla's 60-tick protection after login expire before testing an ordinary PvP hit.
        helper.runAfterDelay(61, () -> {
            try {
                server.setPvpAllowed(true);
                caster.setGameMode(GameType.CREATIVE);
                diner.setGameMode(GameType.SURVIVAL);
                caster.setPos(helper.absoluteVec(new Vec3(4.5, 2, 4.5)));
                diner.setPos(helper.absoluteVec(new Vec3(6.5, 2, 4.5)));
                AbilityStorage.get(diner).changeCategoryClassic("electromaster");
                AbilityStorage.get(diner).setLevel(1);
                helper.assertTrue(HighPowerCombat.enroll(diner, 1), "Fixture must carry ACA's LV1 shield");
                helper.assertTrue(PlayerShieldArmor.ignoresControl(diner), "Active real shield must grant super armor");

                Vec3 initialMotion = new Vec3(0.12, -0.08, 0.17);
                diner.setDeltaMovement(initialMotion);
                Domain67Manager.castCircusBroadcast(caster);
                helper.assertFalse(Domain67Manager.isLured(diner.getUUID()), "Broadcast must leave shielded players free");
                var domain = Domain67Manager.toggleOrExpandDomain(caster);
                helper.assertTrue(domain.diners.containsKey(diner.getUUID()), "Shielded player can still be hit as a diner");
                helper.assertTrue(diner.getDeltaMovement().equals(initialMotion), "Seat assignment must preserve motion");

                float health = diner.getHealth();
                var shield = HighPowerCombat.shield(diner);
                CursedLogEntity log = CursedLogEntity.spawnOnStage(level, caster.getUUID(), CursedLogEntity.VARIANT_GUARD,
                        diner.position().add(0, 0.2, 0), new Vec3(0.25, 0, 0), 1, 10, 5, domain.center, diner.getY());
                log.tick();
                log.discard();
                var shieldAfter = HighPowerCombat.shield(diner);
                AcaDomain67.LOGGER.info("Domain67 LV1 log: shield {} -> {}, health {} -> {}, motion {} -> {}",
                        shield, shieldAfter, health, diner.getHealth(), initialMotion, diner.getDeltaMovement());
                helper.assertTrue(shield.signum() > 0 && shieldAfter.signum() == 0,
                        "Rolling log must debit and break the real LV1 shield");
                helper.assertTrue(diner.getHealth() < health, "Unabsorbed damage must reach actual player health");
                helper.assertTrue(diner.getDeltaMovement().equals(initialMotion), "Shield-break hit must not knock the player back");
                helper.assertFalse(diner.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Shielded diner must not be slowed");
                helper.succeed();
            } finally {
                Domain67Manager.dismissDomain(caster, false);
                server.setPvpAllowed(originalPvp);
                players.remove(caster);
                players.remove(diner);
            }
        });
    }

    @GameTest(template = "empty", batch = "native_cancellation", timeoutTicks = 60)
    public static void cancelled_log_does_not_debit_or_move_the_target(GameTestHelper helper) {
        var level = helper.getLevel();
        var target = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 4));
        var log = CursedLogEntity.spawnOnStage(level, null, CursedLogEntity.VARIANT_GUARD,
                target.position().add(0, 0.2, 0), new Vec3(0.25, 0, 0), 1, 10, 5,
                target.position().add(2, 0, 0), target.getY());
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.function.Consumer<LivingIncomingDamageEvent> gate = event -> {
            if (event.getEntity() == target) {
                cancelled.set(true);
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, gate);
        try {
            float health = target.getHealth();
            Vec3 motion = target.getDeltaMovement();
            log.tick();
            helper.assertTrue(cancelled.get(), "The real log must reach the NeoForge damage event");
            helper.assertTrue(target.getHealth() == health, "Cancelled native hit must leave health intact");
            helper.assertTrue(target.getDeltaMovement().equals(motion), "Cancelled native hit must not apply scripted motion");
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(gate);
            log.discard();
        }
    }

    @GameTest(template = "empty", batch = "terrain_recovery", timeoutTicks = 60)
    public static void arena_save_retains_unloaded_blocks_and_restores_loaded_blocks(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(4, 2, 4));
        var owner = UUID.randomUUID();
        var vault = DomainTerrainVault.get(level);
        level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
        vault.beginArena(level, owner, pos, 4);
        try {
            vault.tickLevel(level);
            helper.assertTrue(level.getBlockState(pos.below()).is(Blocks.STRIPPED_BIRCH_WOOD), "Missing temporary runway");
            Player miner = helper.makeMockPlayer(GameType.SURVIVAL);
            var mining = new BlockEvent.BreakEvent(level, pos.below(), level.getBlockState(pos.below()), miner);
            helper.assertTrue(NeoForge.EVENT_BUS.post(mining).isCanceled(), "Temporary blocks must not drop resources");

            var saved = vault.save(new CompoundTag(), level.registryAccess());
            var arena = saved.getList("Domains", Tag.TAG_COMPOUND).stream()
                    .map(CompoundTag.class::cast).filter(t -> t.getUUID("Owner").equals(owner)).findFirst().orElseThrow();
            ListTag blocks = arena.getList("Blocks", Tag.TAG_COMPOUND);
            BlockPos unloaded = pos.below().offset(32000, 0, 32000);
            helper.assertFalse(level.isLoaded(unloaded), "Recovery fixture must have one unloaded chunk");
            var pending = new CompoundTag();
            pending.putInt("X", unloaded.getX());
            pending.putInt("Y", unloaded.getY());
            pending.putInt("Z", unloaded.getZ());
            pending.putDouble("Dist", 0.5);
            pending.put("State", NbtUtils.writeBlockState(Blocks.GRASS_BLOCK.defaultBlockState()));
            pending.put("Stage", NbtUtils.writeBlockState(Blocks.SMOOTH_QUARTZ.defaultBlockState()));
            blocks.add(pending);

            var recovery = DomainTerrainVault.load(saved, level.registryAccess());
            recovery.restoreAll(level);
            helper.assertTrue(level.getBlockState(pos.below()).is(Blocks.DIRT), "Reload restore must recover loaded original");
            helper.assertTrue(recovery.hasRecord(owner) && !level.isLoaded(unloaded), "Recovery must defer without loading chunks");
            var recovered = recovery.save(new CompoundTag(), level.registryAccess()).getList("Domains", Tag.TAG_COMPOUND);
            var left = recovered.stream().map(CompoundTag.class::cast).filter(t -> t.getUUID("Owner").equals(owner))
                    .findFirst().orElseThrow().getList("Blocks", Tag.TAG_COMPOUND);
            helper.assertTrue(left.size() == 1 && left.getCompound(0).equals(pending),
                    "Unloaded snapshot must remain in the next save with its original state");
            helper.succeed();
        } finally {
            vault.restoreImmediate(level, owner);
        }
    }

    private static ServerPlayer connectedPlayer(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var id = UUID.randomUUID();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(id, "D67-" + id.toString().substring(0, 8)), false);
        var player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}

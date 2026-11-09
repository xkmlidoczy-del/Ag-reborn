package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.develop.DeveloperItemEnergy;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.skill.CoinTosses;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * An opt-in, two-JVM native player.dat/Anvil restart probe. The EmbeddedChannel only
 * admits a mock profile; it does not establish a client, socket, codec or renderer.
 * Seed deliberately leaves its admitted player online through actual shutdown.
 * The separate verify JVM requires the graceful-stop certificate and native files.
 */
@GameTestHolder("academy_restart")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class AcademyRestartRuntimeTests {
    private static final String NAMESPACE = "academy_restart";
    private static final String TEMPLATE = "restart_empty";
    private static final String WORLD = "academy-restart-world";
    private static final String PROGRESS = "academy:classic_progress";
    private static final String ESCROW = "academy:coin_escrow";
    private static final UUID PROFILE = UUID.fromString("6c7c7918-557c-4f9a-9426-88dd520dc516");
    private static final UUID BOOT = UUID.randomUUID();
    private static final int VERIFY_TICKS = 5;
    private static volatile Context context;

    private AcademyRestartRuntimeTests() {}

    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!enabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var tag = new CompoundTag();
        var size = new ListTag();
        size.add(IntTag.valueOf(4)); size.add(IntTag.valueOf(4)); size.add(IntTag.valueOf(4));
        tag.put("size", size);
        var block = new CompoundTag();
        var pos = new ListTag();
        pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0));
        block.put("pos", pos); block.putInt("state", 0);
        var blocks = new ListTag(); blocks.add(block); tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        var air = new CompoundTag(); air.putString("Name", "minecraft:air");
        var palette = new ListTag(); palette.add(air); tag.put("palette", palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath(NAMESPACE, TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template = TEMPLATE, batch = "academy_restart", timeoutTicks = 40)
    public static void native_player_disk_restart(GameTestHelper helper) {
        String phase = System.getProperty("academy.restart.phase", "");
        check(phase.equals("seed") || phase.equals("verify"), "Set academy.restart.phase=seed or verify");
        check(context == null, "Exactly one restart test per JVM is supported");
        var fixture = new Context(helper, phase);
        context = fixture;
        helper.testInfo.addListener(new GameTestListener() {
            public void testStructureLoaded(GameTestInfo info) {}
            public void testPassed(GameTestInfo info, GameTestRunner runner) {
                if (fixture.seed()) fixture.seedPassed = true;
                else fixture.close();
            }
            public void testFailed(GameTestInfo info, GameTestRunner runner) { fixture.close(); }
            public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) { fixture.close(); }
        });
        if (fixture.seed()) seed(fixture);
        else verify(fixture);
    }

    private static void seed(Context fixture) {
        check(!Files.exists(fixture.proof) && !Files.exists(fixture.playerFile)
                && !Files.exists(fixture.playerFile.resolveSibling(PROFILE + ".dat_old")),
                "Seed requires a fresh disposable run-restart* directory; do not reuse a prior profile");
        var player = fixture.admit();
        check(fixture.loads == 0, "Fresh seed must not load existing native player data");
        fillSeed(AbilityStorage.get(player));
        try{
            var source=player.createCommandSourceStack().withPermission(4);
            check(player.server.getCommands().getDispatcher().execute("aim cheats_on",source)==1,"Seed actual self-command persists explicit opt-in");
            check(player.server.getCommands().getDispatcher().execute("aim unlearn arc_gen",source)==1,"Seed actual unlearn command retains source raw mastery and preset identity");
        }catch(com.mojang.brigadier.exceptions.CommandSyntaxException bad){throw new AssertionError("Production registered commands must execute during native seed",bad);}
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        fixture.energyWorld.seed(player);
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, coin(7));
        player.getInventory().setItem(1, developer());
        player.getInventory().setItem(2, AcademyEnergyDiskFixture.playerUnit());
        // Enable the fixture's initial QTE, then restore the independently seeded
        // cooldown. No judgement is submitted; the escrow must remain outstanding.
        AbilityStorage.get(player).cooldowns.remove("railgun");
        check(CoinTosses.toss(player, InteractionHand.MAIN_HAND), "Native pending coin toss must start");
        AbilityStorage.get(player).cooldowns.put("railgun", 900);
        check(CoinTosses.hasPendingAttempt(player), "Pending QTE must exist before graceful stop");
        check(player.getPersistentData().contains(ESCROW), "Pending toss has native persistent escrow");
        checkInventory(player, 6);
        checkEvolution(AbilityStorage.get(player), 0);
        AbilityStorage.save(player);
        fixture.helper.getLevel().getServer().getPlayerList().saveAll();
        check(fixture.saves == 1, "Seed must observe actual PlayerDataStorage SaveToFile event");
        checkDisk(fixture, read(fixture.playerFile), 0, 6, true);
        fixture.pendingNativeSave = true;
        System.out.println("ACADEMY_RESTART seed native pending-escrow save passed; pid=" + ProcessHandle.current().pid());
        fixture.helper.succeed(); // Retain player until genuine ServerStoppingEvent.
    }

    private static void verify(Context fixture) {
        check(!Files.exists(fixture.verified), "This bounded probe already verified; start a fresh run-restart* directory");
        var proof = read(fixture.proof);
        check(proof.getInt("schema") == 1 && proof.getUUID("profile").equals(PROFILE), "Correct seed certificate/profile required");
        check(proof.getLong("seed_pid") != ProcessHandle.current().pid()
                && !proof.getUUID("seed_boot").equals(BOOT), "Verify must run in a distinct JVM process");
        check(proof.getBoolean("pending_native_save") && proof.getBoolean("stopping_observed")
                && proof.getBoolean("final_native_save") && proof.getBoolean("stopped_observed"),
                "Seed must have completed actual graceful-stop lifecycle and native disk save");
        fixture.seedTicks = proof.getInt("seed_post_ticks");
        check(fixture.seedTicks >= 0 && fixture.seedTicks <= 2, "Seed shutdown may advance at most two player-post ticks");
        check(proof.getCompound("expected_progress").equals(expected(fixture.seedTicks)),
                "Certificate must match independently known seeds and bounded tick evolution");
        fixture.energyWorld.verifyBeforeMutation(proof);
        var disk = read(fixture.playerFile);
        checkDisk(fixture, disk, fixture.seedTicks, 7, false);
        var player = fixture.admit(); // PlayerList.load → PlayerDataStorage.load → Entity.load.
        check(fixture.loads == 1, "Admission must observe actual native LoadFromFile exactly once");
        check(player.getPersistentData().getCompound(PROGRESS).equals(disk.getCompound("NeoForgeData").getCompound(PROGRESS)),
                "Native admission must load exactly the serialized progress from player.dat");
        check(AbilityStorage.encodeSaved(AbilityStorage.get(player)).equals(expected(fixture.seedTicks)),
                "New JVM cache must decode exact known persisted state");
        check(cn.academy.port.command.ClassicAbilityCommands.active(player),"Actual PlayerDataStorage admission restores PlayerPersisted self-command opt-in");
        checkEvolution(AbilityStorage.get(player), fixture.seedTicks, false);
        checkInventory(player, 7);
        check(!player.getPersistentData().contains(ESCROW) && !CoinTosses.hasPendingAttempt(player),
                "Graceful stop must refund once, remove escrow, and leave no transient QTE after restart");
        long[] lastTick = {fixture.helper.getLevel().getGameTime()};
        fixture.helper.onEachTick(() -> {
            long now = fixture.helper.getLevel().getGameTime();
            if (fixture.closed || fixture.helper.testInfo.isDone() || now <= lastTick[0]) return;
            check(now - lastTick[0] == 1, "Verify must observe consecutive real world ticks");
            lastTick[0] = now;
            // Like the base native mock/FakePlayer suite, one explicit registered
            // gameplay post-tick is driven per actual world tick; no live client exists.
            int previous = fixture.posts;
            player.tickCount++;
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
            check(fixture.posts == previous + 1 && fixture.posts <= VERIFY_TICKS,
                    "Exactly one fixture post-tick per observed world tick");
            check(AbilityStorage.encodeSaved(AbilityStorage.get(player)).equals(expected(fixture.seedTicks + fixture.posts)),
                    "Exact seeded state must evolve only by expected bounded delay/cooldown changes");
            checkEvolution(AbilityStorage.get(player), fixture.seedTicks + fixture.posts, false);
            checkInventory(player, 7);
            check(!player.getPersistentData().contains(ESCROW) && !CoinTosses.hasPendingAttempt(player),
                    "Repeated post-ticks must not refund a second coin or restore transient escrow");
            fixture.energyWorld.checkLive(true);
            if (fixture.posts == VERIFY_TICKS) {
                var result = proof.copy();
                result.putLong("verify_pid", ProcessHandle.current().pid());
                result.putUUID("verify_boot", BOOT);
                result.putInt("verify_post_ticks", fixture.posts);
                result.putBoolean("verified", true);
                result.put("verified_progress", AbilityStorage.encode(AbilityStorage.get(player)));
                fixture.energyWorld.verified(result);
                write(fixture.verified, result);
                System.out.println("ACADEMY_RESTART verify native disk/new-JVM reload passed; seed_pid="
                        + proof.getLong("seed_pid") + " verify_pid=" + ProcessHandle.current().pid());
                fixture.helper.succeed();
            }
        });
    }

    /** Observe the real production stop handler after its NORMAL priority refund/save. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopping(ServerStoppingEvent event) {
        var fixture = context;
        if (fixture == null || !fixture.seed() || !fixture.seedPassed || fixture.closed) return;
        check(event.getServer() == fixture.helper.getLevel().getServer(), "Owned server stopping");
        check(fixture.posts >= 0 && fixture.posts <= 2, "Bounded seed-to-stop player-tick count");
        check(fixture.pendingNativeSave, "Pending escrow must have been saved before stop");
        check(!fixture.player.getPersistentData().contains(ESCROW)
                && !CoinTosses.hasPendingAttempt(fixture.player), "Actual production graceful stop removes pending escrow");
        checkInventory(fixture.player, 7);
        checkEvolution(AbilityStorage.get(fixture.player), fixture.posts);
        check(fixture.player.getPersistentData().getCompound(PROGRESS).equals(expected(fixture.posts)),
                "Actual production stopping persists exact independently expected progress");
        fixture.energyWorld.stopping(fixture.player);
        fixture.stopSeen = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void saving(PlayerEvent.SaveToFile event) {
        var fixture = context;
        if (fixture == null || event.getEntity() != fixture.player) return;
        check(event.getPlayerDirectory().toPath().toAbsolutePath().normalize().equals(fixture.playerFile.getParent()),
                "Native save is restricted to the isolated playerdata directory");
        fixture.saves++;
        if (fixture.seed() && fixture.stopSeen) {
            checkDisk(fixture, read(fixture.playerFile), fixture.posts, 7, false);
            fixture.finalNativeSave = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void loading(PlayerEvent.LoadFromFile event) {
        var fixture = context;
        if (fixture == null || event.getEntity() != fixture.player) return;
        check(event.getPlayerDirectory().toPath().toAbsolutePath().normalize().equals(fixture.playerFile.getParent()),
                "Native load is restricted to the isolated playerdata directory");
        fixture.loads++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void chunkSaving(ChunkDataEvent.Save event) {
        var fixture = context;
        if (fixture != null && !fixture.closed) fixture.energyWorld.nativeSave(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void chunkLoading(ChunkDataEvent.Load event) {
        var fixture = context;
        if (fixture != null && !fixture.closed) fixture.energyWorld.nativeLoad(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void playerPostTick(PlayerTickEvent.Post event) {
        var fixture = context;
        if (fixture != null && !fixture.closed && event.getEntity() == fixture.player) fixture.posts++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopped(ServerStoppedEvent event) {
        var fixture = context;
        if (fixture == null) return;
        try {
            if (fixture.seed() && fixture.seedPassed && !fixture.closed) {
                check(fixture.stopSeen && fixture.finalNativeSave, "Real stopping and final PlayerDataStorage save required");
                checkDisk(fixture, read(fixture.playerFile), fixture.posts, 7, false);
                var proof = new CompoundTag();
                proof.putInt("schema", 1); proof.putUUID("profile", PROFILE);
                proof.putLong("seed_pid", ProcessHandle.current().pid()); proof.putUUID("seed_boot", BOOT);
                proof.putInt("seed_post_ticks", fixture.posts);
                proof.putBoolean("pending_native_save", fixture.pendingNativeSave);
                proof.putBoolean("stopping_observed", fixture.stopSeen);
                proof.putBoolean("final_native_save", fixture.finalNativeSave);
                proof.putBoolean("stopped_observed", true);
                proof.putInt("native_save_events", fixture.saves);
                proof.put("expected_progress", expected(fixture.posts));
                fixture.energyWorld.certifyClosedWorld(proof);
                write(fixture.proof, proof);
                System.out.println("ACADEMY_RESTART seed actual graceful-stop/final native save certified; pid="
                        + ProcessHandle.current().pid() + " post_ticks=" + fixture.posts);
            }
        } finally {
            if (fixture.channel != null) fixture.channel.finishAndReleaseAll();
        }
    }

    private static void fillSeed(AbilityProgress state) {
        state.category = "electromaster"; state.level = 4; state.activated = true;
        state.overloadFine = true; state.interfering = false;
        state.cp = 321.25; state.overload = 73.5;
        state.extraCp = 125.75; state.extraOverload = 7.25; state.levelExperience = 1.125;
        state.cpDelay = 15; state.overloadDelay = 32;
        state.experience.clear(); state.experience.put("arc_gen", .375); state.experience.put("railgun", .625);
        state.experience.put("brain_course", .25); state.experience.put("mind_course", .5);
        // Independent valid seed: classic level4 CP5800 + learned Brain Course1000, overload350.
        // Direct field/bitset fixture setup must not rely on getter-triggered recalculation.
        state.restoreCalculatedMaxima(6800, 350);
        state.cooldowns.clear(); state.cooldownMaxTicks.clear(); state.setCooldown("arc_gen", 300); state.setCooldown("railgun", 900);
        state.presets.clear();
        state.presets.edit(0, 0, "arc_gen", id -> cn.academy.port.preset.PresetSkills.selectable(state, id));
        state.presets.edit(2, 1, "railgun", id -> cn.academy.port.preset.PresetSkills.selectable(state, id));
        state.presets.edit(2, 3, "arc_gen", id -> cn.academy.port.preset.PresetSkills.selectable(state, id));
        state.presets.switchTo(2);
    }

    private static CompoundTag expected(int ticks) {
        check(ticks >= 0 && ticks <= 2 + VERIFY_TICKS, "Only bounded seed/verify tick evolution is accepted");
        var state = new AbilityProgress(); fillSeed(state);
        // Independently fixed post-command oracle: Arc's learned bit is gone, its raw exp and mapping remain.
        state.experience.remove("arc_gen");state.unlearnedExperience.put("arc_gen",.375);
        // Independent arithmetic, not the production tick implementation used
        // as its own oracle. All allowed ticks remain inside recovery delays.
        state.cpDelay = 15 - ticks; state.overloadDelay = 32 - ticks;
        state.cooldowns.put("arc_gen", 300 - ticks);
        state.cooldowns.put("railgun", 900 - ticks);
        return AbilityStorage.encodeSaved(state);
    }

    private static void checkEvolution(AbilityProgress state, int ticks) {checkEvolution(state,ticks,true);}

    private static void checkEvolution(AbilityProgress state,int ticks,boolean seedLiveCooldowns) {
        checkStable(state);
        check(state.cpDelay == 15 - ticks && state.overloadDelay == 32 - ticks,
                "Explicit seeded recovery delays decrease by exactly bounded post-tick count");
        if(!seedLiveCooldowns){check(state.cooldowns.isEmpty()&&state.cooldownMaxTicks.isEmpty(),"Original transient CooldownData starts empty in the new JVM; disk is not a live sync snapshot");return;}
        check(state.cooldowns.size() == 2 && state.cooldowns.get("arc_gen") == 300 - ticks
                && state.cooldowns.get("railgun") == 900 - ticks,
                "Explicit known cooldowns decrease by exactly bounded post-tick count");
        check(state.cooldownMaximum("arc_gen") == 300 && state.cooldownMaximum("railgun") == 900,
                "Captured maximum cooldowns remain fixed while remaining decreases");
    }

    private static void checkStable(AbilityProgress state) {
        check(state.category.equals("electromaster") && state.level == 4 && state.activated && state.overloadFine,
                "Category, level, activation and overload gate retain explicit known seeds");
        check(state.presets.current() == 2 && state.presets.revision() == 5
                && state.presets.skill(0,0).equals("arc_gen") && state.presets.skill(2,1).equals("railgun")
                && state.presets.skill(2,3).equals("arc_gen") && state.presets.skill(1,0).isEmpty(),
                "Independently known 4x4 preset mapping, selected index and revision survive native reload");
        check(state.experience.size() == 3 && !state.learned("arc_gen") && state.exp("arc_gen") == .375 && state.exp("railgun") == .625
                && state.experience.get("brain_course") == .25 && state.experience.get("mind_course") == .5,
                "Known learned and command-unlearned retained mastery values survive restart without restoring a learned bit");
        check(state.extraCp == 125.75 && state.extraOverload == 7.25 && state.levelExperience == 1.125,
                "Known CP/overload training and level experience survive restart");
        check(state.baseCp() == 6800 && state.baseOverload() == 350,
                "Independently fixed valid level4/course cached raw maxima survive native reload");
        check(state.cp == 321.25 && state.overload == 73.5,
                "Bounded ticks remain inside seeded recovery delays, preserving exact finite resources");
    }

    private static ItemStack coin(int count) { return new ItemStack(AcademyCraft.COIN.get(), count); }

    private static ItemStack developer() {
        var stack = new ItemStack(AcademyCraft.DEVELOPER.get());
        new DeveloperItemEnergy(stack, DeveloperType.PORTABLE).energy(4321.25);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("restart_fixture", "finite-seed-v1"));
        return stack;
    }

    private static void checkInventory(ServerPlayer player, int coins) {
        check(player.getInventory().selected == 0, "Selected inventory slot survives native storage");
        check(player.getInventory().getItem(0).is(AcademyCraft.COIN.get())
                && player.getInventory().getItem(0).getCount() == coins, "Exact known coin count in slot 0");
        var actual = player.getInventory().getItem(1);
        check(actual.is(AcademyCraft.DEVELOPER.get()) && actual.getCount() == 1, "Known portable developer in slot 1");
        check(actual.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .equals(developer().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()),
                "Exact full developer CUSTOM_DATA retains finite energy and unrelated sentinel");
        double energy = new DeveloperItemEnergy(actual, DeveloperType.PORTABLE).energy();
        check(Double.isFinite(energy) && energy == 4321.25, "Known finite 4321.25 IF survives native item serialization");
        int occupied = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (!player.getInventory().getItem(i).isEmpty()) occupied++;
        var unit = player.getInventory().getItem(2);
        check(unit.save(player.registryAccess()).equals(AcademyEnergyDiskFixture.playerUnit().save(player.registryAccess())),
                "Exact native player energy unit components retain 5432.125 IF and unrelated sentinel");
        double unitEnergy = cn.academy.port.energy.ClassicEnergyItemHelper.getEnergy(unit);
        check(Double.isFinite(unitEnergy) && unitEnergy == 5432.125, "Known finite fractional energy-unit IF survives native item serialization");
        check(occupied == 3, "No additional fixture coins/developers/units may appear in other inventory slots");
    }

    private static void checkDisk(Context fixture, CompoundTag disk, int ticks, int coins, boolean pending) {
        var persistent = disk.getCompound("NeoForgeData");
        check(persistent.getCompound(PROGRESS).equals(expected(ticks)), "Native on-disk progress equals independent full seeded state");
        check(persistent.getCompound("PlayerPersisted").getBoolean("aim_cheats"),"Native player.dat contains original self-command opt-in marker");
        check(persistent.contains(ESCROW) == pending, "Native on-disk escrow presence matches lifecycle phase");
        if (pending) check(persistent.getCompound(ESCROW).equals(coin(1).save(fixture.helper.getLevel().registryAccess())),
                "Native pending escrow is exactly one known Academy coin");
        check(disk.getInt("SelectedItemSlot") == 0, "Native on-disk selected item slot");
        var items = disk.getList("Inventory", 10);
        check(items.size() == 3, "Exactly three known on-disk inventory entries");
        boolean sawCoin = false, sawDeveloper = false, sawUnit = false;
        for (int i = 0; i < items.size(); i++) {
            var entry = items.getCompound(i).copy(); int slot = entry.getByte("Slot") & 255; entry.remove("Slot");
            if (slot == 0) {
                check(!sawCoin && entry.equals(coin(coins).save(fixture.helper.getLevel().registryAccess())), "Exact serialized native coin stack/count");
                sawCoin = true;
            } else if (slot == 1) {
                check(!sawDeveloper && entry.equals(developer().save(fixture.helper.getLevel().registryAccess())), "Exact serialized native developer stack/CUSTOM_DATA");
                sawDeveloper = true;
            } else if (slot == 2) {
                check(!sawUnit && entry.equals(AcademyEnergyDiskFixture.playerUnit().save(fixture.helper.getLevel().registryAccess())), "Exact serialized native energy-unit stack/CUSTOM_DATA/damage");
                sawUnit = true;
            } else check(false, "Unexpected native inventory slot " + slot);
        }
        check(sawCoin && sawDeveloper && sawUnit, "All three known native inventory slots must exist");
    }

    private static boolean enabled() {
        return GameTestHooks.isGametestEnabled()
                && Set.of(System.getProperty("neoforge.enabledGameTestNamespaces", "").split(",")).contains(NAMESPACE);
    }

    private static CompoundTag read(Path path) {
        try {
            check(Files.isRegularFile(path) && Files.size(path) > 0 && Files.size(path) < 4 * 1024 * 1024,
                    "Expected bounded native/certificate file exists: " + path);
            return NbtIo.readCompressed(path, NbtAccounter.create(4 * 1024 * 1024));
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    private static void write(Path path, CompoundTag tag) {
        try { NbtIo.writeCompressed(tag, path); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class Context {
        final GameTestHelper helper;
        final String phase;
        final Path playerFile, proof, verified;
        final AcademyEnergyDiskFixture energyWorld;
        ServerPlayer player;
        EmbeddedChannel channel;
        int loads, saves, posts, seedTicks;
        boolean closed, seedPassed, pendingNativeSave, stopSeen, finalNativeSave;

        Context(GameTestHelper helper, String phase) {
            this.helper = helper; this.phase = phase;
            try {
                var server = helper.getLevel().getServer();
                Path run = server.getServerDirectory().toRealPath();
                check(run.getFileName().toString().startsWith("run-restart"), "Only isolated run-restart* game directories are supported");
                Path world = server.getWorldPath(LevelResource.ROOT).toRealPath();
                check(world.startsWith(run) && world.getFileName().toString().equals(WORLD),
                        "Only the isolated academy-restart-world below run-restart* may be used");
                Path playerDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toRealPath();
                check(playerDir.startsWith(world), "Native playerdata must not escape the disposable world");
                energyWorld = new AcademyEnergyDiskFixture(helper, world);
                playerFile = playerDir.resolve(PROFILE + ".dat");
                proof = run.resolve("academy-restart-seed-proof.nbt");
                verified = run.resolve("academy-restart-verified.nbt");
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }

        boolean seed() { return phase.equals("seed"); }

        ServerPlayer admit() {
            var level = helper.getLevel(); var server = level.getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(PROFILE, "ACRestartProbe"), false);
            player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            // Optional receive-channel declaration MUST precede native login's
            // AcademyNetwork.sync. This is an embedded mock, not negotiation.
            NetworkRegistry.onMinecraftRegister(connection, Set.of(AcademyNetwork.ClientData.TYPE.id()));
            try {
                server.getPlayerList().placeNewPlayer(connection, player, cookie);
                check(server.getPlayerList().getPlayer(PROFILE) == player, "Native PlayerList actually admits fixed profile");
                var pos = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
                player.teleportTo(pos.x, pos.y, pos.z); player.setNoGravity(true); player.setInvulnerable(true);
                return player;
            } catch (RuntimeException failure) {
                try { close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        void close() {
            if (closed) return; closed = true;
            try {
                if (player != null) {
                    var list = helper.getLevel().getServer().getPlayerList();
                    if (list.getPlayer(PROFILE) == player) list.remove(player);
                    else player.discard();
                }
            } finally { if (channel != null) channel.finishAndReleaseAll(); }
        }
    }
}

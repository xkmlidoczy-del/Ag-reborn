package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.CurrentChargingSession;
import cn.academy.port.develop.DeveloperItemEnergy;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.preset.PresetSkills;
import cn.academy.port.skill.CurrentCharging;
import com.mojang.logging.LogUtils;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.SocketChannel;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import org.slf4j.Logger;

/** Opt-in disposable TCP QA. Never casts, injects an input, or creates a player. */
@GameTestHolder("academy_socket")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class AcademySocketRuntimeTests {
    public static final int PORT = 25569;
    private static final int TIMEOUT_TICKS = 18000;
    private static final Logger LOG = LogUtils.getLogger();
    private static final String OBSERVER = "academy_socket_observer";
    private static MinecraftServer listeningServer;
    private static InetAddress bindAddress;
    private static Session session;
    private static long nextPacedTick;
    private AcademySocketRuntimeTests() {}

    private static boolean optedIn() { return Boolean.getBoolean("academy.socket.qa"); }
    private static Path directory() {
        Path dir = Path.of("").toAbsolutePath().normalize();
        if (!dir.getFileName().toString().matches("run-socket-[A-Za-z0-9_-]+"))
            throw new IllegalStateException("Socket QA requires a disposable run-socket-* working directory");
        return dir;
    }
    private static boolean enabled(MinecraftServer server) {
        return optedIn() && GameTestHooks.isGametestEnabled() && server instanceof GameTestServer;
    }
    @SubscribeEvent
    public static void starting(ServerStartingEvent event) throws IOException {
        if (!optedIn()) return;
        MinecraftServer server = event.getServer();
        if (!enabled(server)) throw new IllegalStateException("Socket QA is restricted to native GameTestServer");
        directory();
        // GameTestServer inherits its synthetic-test authentication defaults. No setting is changed.
        if (server.usesAuthentication()) throw new IllegalStateException("Unexpected authenticated GameTestServer; do not weaken it");
        bindAddress = InetAddress.getLoopbackAddress();
        if (!bindAddress.isLoopbackAddress()) throw new IllegalStateException("Refusing a non-loopback bind");
        server.getConnection().startTcpServerListener(bindAddress, PORT);
        listeningServer = server;
        nextPacedTick = System.nanoTime();
        String endpoint = bindAddress instanceof java.net.Inet6Address ? "[" + bindAddress.getHostAddress() + "]:" + PORT : bindAddress.getHostAddress() + ":" + PORT;
        Files.writeString(directory().resolve("socket-endpoint.txt"), endpoint + System.lineSeparator());
        LOG.info("ACADEMY_SOCKET_LISTEN endpoint={} loopback=true namespace=academy_socket timeoutTicks={}", endpoint, TIMEOUT_TICKS);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void pace(ServerTickEvent.Pre event) {
        if (event.getServer() != listeningServer || !enabled(event.getServer())) return;
        // Vanilla GameTestServer.waitUntilNextTick deliberately skips normal 20Hz sleeping.
        // Throttle this explicitly opted-in, disposable test only; real player ticks still perform gameplay.
        long wait = nextPacedTick - System.nanoTime();
        if (wait > 0) {
            try { TimeUnit.NANOSECONDS.sleep(wait); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Socket QA pacing interrupted", e); }
        }
        nextPacedTick = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(50);
    }
    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!optedIn() || !(event.getLevel() instanceof ServerLevel level) || !enabled(level.getServer())) return;
        directory();
        CompoundTag tag = new CompoundTag();
        tag.put("size", ints(8, 6, 20));
        ListTag blocks = new ListTag();
        CompoundTag airBlock = new CompoundTag(); airBlock.put("pos", ints(0, 0, 0)); airBlock.putInt("state", 0); blocks.add(airBlock);
        tag.put("blocks", blocks); tag.put("entities", new ListTag());
        ListTag palette = new ListTag(); CompoundTag air = new CompoundTag(); air.putString("Name", "minecraft:air"); palette.add(air); tag.put("palette", palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_socket", "socket_empty"))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }
    private static ListTag ints(int... values) { ListTag list = new ListTag(); for (int value : values) list.add(IntTag.valueOf(value)); return list; }

    @GameTest(template = "socket_empty", batch = "academy_socket", timeoutTicks = TIMEOUT_TICKS)
    public static void real_tcp_client_charging_arc_presets(GameTestHelper helper) {
        helper.assertTrue(enabled(helper.getLevel().getServer()) && helper.getLevel().getServer() == listeningServer,
                "explicit opt-in listener must already exist");
        helper.assertTrue(AcademyConfig.DAMAGE_SCALE.get() == 1.0, "fresh disposable damage scale must be 1");
        if (session != null) throw new IllegalStateException("Only one bounded socket session is allowed");
        session = new Session(helper);
        helper.testInfo.addListener(new GameTestListener() {
            public void testStructureLoaded(GameTestInfo info) {}
            public void testPassed(GameTestInfo info, GameTestRunner runner) { session.cleanup(); }
            public void testFailed(GameTestInfo info, GameTestRunner runner) { session.failure("Native GameTest failure: " + info.getError()); session.cleanup(); }
            public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) { session.cleanup(); }
        });
        LOG.info("ACADEMY_SOCKET_READY: connect the native NeoForge client to socket-endpoint.txt; no player or gameplay is simulated");
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void playerTick(PlayerTickEvent.Post event) {
        Session current = session;
        if (current == null || current.done || !(event.getEntity() instanceof ServerPlayer player)
                || player.serverLevel().getServer() != listeningServer) return;
        try {
            if (current.player == null) current.admit(player);
            if (current.player == player) current.tick();
        } catch (RuntimeException e) { current.failNative(e.toString()); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void attack(SkillAttackEvent event) {
        Session current = session;
        if (current == null || current.done || event.player != current.player || event.target != current.target) return;
        if (event.skill.equals("electromaster.arc_gen")) {
            current.arcAttacks++;
            current.arcDamage = event.amount;
            current.arcPaidCp = current.state.cp;
            current.arcPaidOverload = current.state.overload;
        }
    }
    @SubscribeEvent
    public static void watch(ServerTickEvent.Post event) {
        Session current = session;
        if (current == null || current.done || event.getServer() != listeningServer) return;
        if (System.nanoTime() - current.started > TimeUnit.MINUTES.toNanos(15)) {
            current.failNative("15-minute physical QA observation window expired in phase " + current.phase);
        } else if (current.player != null && current.player.hasDisconnected()) {
            current.failNative("Real TCP player disconnected in phase " + current.phase);
        }
    }
    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        if (event.getServer() == listeningServer && session != null && !session.done) session.failure("Server stopped before observed outcome");
    }

    private enum Phase { CHARGING, RELEASE_QUIET, FIRST_ARC, CYCLE_AND_EDIT, SECOND_ARC, DRAIN }
    private record Wire(String action, String value, double cp, double overload, double levelExp, int current, long revision) {}
    private static final class Session {
        final GameTestHelper helper;
        final long started = System.nanoTime();
        final Properties proof = new Properties();
        final List<Wire> wires = new ArrayList<>();
        ServerPlayer player;
        Connection connection;
        AbilityProgress state;
        Villager target;
        ItemStack developer;
        Phase phase = Phase.CHARGING;
        boolean done;
        volatile boolean observerInstalled;
        int chargedTicks, quietTicks, cycleSteps, arcAttacks, drainTicks;
        int arcMaximum;
        volatile int sentStates, sentChargingStart, sentChargingEnd, sentArc;
        double previousEnergy, releaseEnergy, chargeProgress, arcDamage, arcPaidCp, arcPaidOverload;
        long lastWorldTick = Long.MIN_VALUE;
        Wire chargingPress, chargingRelease, firstArc, edit, secondArc;
        Session(GameTestHelper helper) { this.helper = helper; proof.setProperty("status", "pending"); }
        void admit(ServerPlayer candidate) {
            helper.assertTrue(candidate.getClass() == ServerPlayer.class, "player must come from native PlayerList, never a synthetic subclass");
            Connection c = candidate.connection.getConnection();
            helper.assertTrue(!c.isMemoryConnection() && c.channel() instanceof SocketChannel, "actual non-memory TCP socket channel");
            helper.assertTrue(c.getRemoteAddress() instanceof InetSocketAddress remote && remote.getAddress().isLoopbackAddress(), "actual remote endpoint is loopback");
            helper.assertTrue(c.channel().localAddress() instanceof InetSocketAddress local && local.getAddress().isLoopbackAddress() && local.getPort() == PORT, "actual listener local endpoint stays loopback on bounded port");
            helper.assertTrue(candidate.connection.getConnectionType() == ConnectionType.NEOFORGE, "native NeoForge configuration admitted player into PLAY");
            var setup = ChannelAttributes.getPayloadSetup(c);
            helper.assertTrue(setup != null, "negotiated payload setup exists; do not configure mock connection");
            var request = setup.getChannel(ConnectionProtocol.PLAY, AcademyNetwork.Request.TYPE.id());
            var data = setup.getChannel(ConnectionProtocol.PLAY, AcademyNetwork.ClientData.TYPE.id());
            helper.assertTrue(request != null && data != null && "1".equals(request.chosenVersion()) && "1".equals(data.chosenVersion()), "required Academy payload channels actually negotiated version 1");
            helper.assertTrue(c.channel().pipeline().get("decoder") != null && c.channel().pipeline().get("encoder") != null
                    && c.channel().pipeline().get("splitter") != null && c.channel().pipeline().get("prepender") != null, "native TCP serialization pipeline remains present");
            player = candidate; connection = c;
            c.channel().eventLoop().execute(() -> {
                c.channel().pipeline().addBefore("packet_handler", OBSERVER, new ChannelDuplexHandler() {
                    @Override public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
                        if (message instanceof ServerboundCustomPayloadPacket payload && payload.payload() instanceof AcademyNetwork.Request request) {
                            // Snapshot is queued before the unchanged registered handler. Never invoke gameplay here.
                            player.getServer().execute(() -> {
                                try { record(request); }
                                catch (RuntimeException e) { failNative(e.toString()); }
                            });
                        }
                        super.channelRead(context, message);
                    }
                    @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                        if (message instanceof ClientboundCustomPayloadPacket packet && packet.payload() instanceof AcademyNetwork.ClientData payload && payload.data() != null) {
                            switch (payload.data().getString("kind")) {
                                case "state" -> sentStates++;
                                case "charging_start" -> sentChargingStart++;
                                case "charging_end" -> sentChargingEnd++;
                                case "arc" -> sentArc++;
                                default -> {}
                            }
                        }
                        super.write(context, message, promise);
                    }
                });
                observerInstalled = true;
            });
            // Initial fixture setup is test data only. All subsequent skills/edits are real client requests.
            player.setGameMode(GameType.SURVIVAL);
            state = AbilityStorage.get(player); state.selectCategory("electromaster"); state.setLevel(5);
            for (String id : List.of("arc_gen", "charging", "railgun")) state.experience.put(id, 1.0);
            state.activated = true; state.overloadFine = true; state.levelExperience = 0;
            state.presets.replace(0, new String[]{"arc_gen", "", "charging", "railgun"}, id -> PresetSkills.selectable(state, id));
            state.presets.switchTo(0);
            player.getInventory().clearContent(); developer = new ItemStack(AcademyCraft.DEVELOPER.get());
            new DeveloperItemEnergy(developer, DeveloperType.PORTABLE).energy(0);
            player.getInventory().setItem(0, developer); player.getInventory().setItem(1, new ItemStack(Items.IRON_INGOT, 8)); player.getInventory().selected = 0;
            player.getAbilities().instabuild = false; player.getAbilities().invulnerable = false; player.onUpdateAbilities();
            player.getFoodData().setFoodLevel(20);
            for (int x = 0; x < 8; x++) for (int z = 0; z < 16; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.GOLD_BLOCK);
            Vec3 start = helper.absoluteVec(new Vec3(3.5, 1, 2.5));
            player.teleportTo(helper.getLevel(), start.x, start.y, start.z, 0, 0);
            target = EntityType.VILLAGER.create(helper.getLevel());
            helper.assertTrue(target != null, "real native living target created");
            Vec3 end = helper.absoluteVec(new Vec3(3.5, 1, 6.5)); target.moveTo(end.x, end.y, end.z, 180, 0);
            target.setNoAi(true); target.setNoGravity(true); target.setSilent(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); target.setHealth(200);
            target.setCustomName(Component.literal("Socket QA Arc target (200 HP)")); target.setCustomNameVisible(true);
            helper.getLevel().addFreshEntity(target);
            AbilityStorage.save(player); AcademyNetwork.sync(player);
            proof.setProperty("transport", c.channel().getClass().getName());
            proof.setProperty("memoryConnection", "false"); proof.setProperty("loopback", "true"); proof.setProperty("payloadRequestVersion", request.chosenVersion());
            proof.setProperty("payloadDataVersion", data.chosenVersion()); proof.setProperty("configurationAdmission", "NEOFORGE PLAY player");
            proof.setProperty("fixture", "survival,mastered-electromaster,finite-empty-portable,preset0=[arc_gen,empty,charging,railgun]");
            tell("Hold R for about one second, then release. Keep portable developer selected. Do not move or use commands.");
        }
        void record(AcademyNetwork.Request request) {
            if (done || state == null) return;
            Wire wire = new Wire(request.action(), request.value(), state.cp, state.overload, state.levelExperience, state.presets.current(), state.presets.revision());
            wires.add(wire);
            LOG.info("ACADEMY_SOCKET_WIRE phase={} action={} value={} selected={} revision={}", phase, wire.action, wire.value, wire.current, wire.revision);
            if (wire.action.equals("slot_press") && wire.value.equals("2") && chargingPress == null) chargingPress = wire;
            if (wire.action.equals("slot_release") && wire.value.equals("2") && chargingRelease == null) chargingRelease = wire;
            if (wire.action.equals("slot_press") && wire.value.equals("0")) {
                if (phase == Phase.FIRST_ARC && firstArc == null) firstArc = wire;
                if (phase == Phase.SECOND_ARC && secondArc == null) secondArc = wire;
            }
            if (phase == Phase.CYCLE_AND_EDIT && wire.action.equals("preset_switch")) {
                int expected = (cycleSteps + 1) % 4;
                if (cycleSteps < 4) {
                    helper.assertTrue(wire.current == cycleSteps % 4 && wire.value.equals(Integer.toString(expected)), "physical cycle sequence 1,2,3,0 is confirmed against native selected state");
                    cycleSteps++;
                }
                else if (edit != null && wire.value.equals("1")) {
                    helper.assertTrue(wire.current == 0 && state.presets.skill(1, 0).equals("arc_gen") && wire.revision > edit.revision,
                            "nonselected native edit is committed before the fifth physical switch");
                    phase = Phase.SECOND_ARC;
                }
            }
            if (phase == Phase.CYCLE_AND_EDIT && wire.action.equals("preset_edit") && wire.value.equals("1:0:arc_gen")) {
                helper.assertTrue(cycleSteps == 4 && wire.current == 0, "browse/edit page 2 only after full four-preset cycle, while gameplay stays page1"); edit = wire;
            }
        }
        double energy() { return new DeveloperItemEnergy(developer, DeveloperType.PORTABLE).energy(); }
        void tick() {
            long now = helper.getLevel().getGameTime();
            if (now == lastWorldTick) return;
            if (lastWorldTick != Long.MIN_VALUE) helper.assertValueEqual(now - lastWorldTick, 1L, "real player post ticks remain consecutive native world ticks");
            lastWorldTick = now;
            helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && !player.getAbilities().instabuild, "fixture remains paid-resource survival");
            helper.assertTrue(state.presets.skill(0, 0).equals("arc_gen") && state.presets.skill(0, 1).isEmpty()
                    && state.presets.skill(0, 2).equals("charging") && state.presets.skill(0, 3).equals("railgun"), "original sparse four-slot preset stays intact");
            double value = energy(); helper.assertTrue(Double.isFinite(value) && value >= 0 && value <= DeveloperType.PORTABLE.energy, "portable energy remains finite and capacity-bounded");
            switch (phase) {
                case CHARGING -> {
                    if (value > previousEnergy) {
                        helper.assertTrue(observerInstalled && chargingPress != null && CurrentCharging.active(player), "IF increase comes from observed decoded physical slot press and native active hold");
                        close(value - previousEnergy, 35, "mastered native tick transfers exactly 35 IF"); chargedTicks++;
                        helper.assertTrue(chargedTicks <= 120, "release R within six seconds, before finite target fills");
                        close(state.cp, chargingPress.cp - chargedTicks * 7, "actual survival charging ticks pay exactly 7 CP each");
                        close(state.levelExperience, chargingPress.levelExp + chargedTicks * CurrentChargingSession.SUPPORTED_EXPERIENCE, "source supported progress per actual tick");
                        close(state.overload, 48, "captured mastered overload floor remains 48");
                    }
                    if (chargingRelease != null && !CurrentCharging.active(player)) {
                        helper.assertTrue(chargedTicks >= 5, "physical hold lasts at least five actual ticks");
                        close(value, chargedTicks * 35, "release preserves finite earned IF");
                        releaseEnergy = value; chargeProgress = state.levelExperience; phase = Phase.RELEASE_QUIET;
                        tell("R release received over TCP. Checking five quiet ticks before the Arc step.");
                    }
                    previousEnergy = value;
                }
                case RELEASE_QUIET -> {
                    close(value, releaseEnergy, "physical release stops IF on subsequent native ticks");
                    helper.assertTrue(!CurrentCharging.active(player), "physical release removed native held context");
                    close(state.levelExperience, chargeProgress, "release adds no held-skill progress");
                    if (++quietTicks >= 5) { phase = Phase.FIRST_ARC; tell("Release passed. Aim straight at the named villager and left-click once for Arc. Do not hold or repeat the click."); }
                }
                case FIRST_ARC -> {
                    if (firstArc != null && arcAttacks == 1) {
                        assertArc(firstArc, 1);
                        proof.setProperty("arcCooldownMaximum", Integer.toString(arcMaximum));
                        phase = Phase.CYCLE_AND_EDIT;
                        tell("Arc hit passed. Tap C four times (release between taps; pause ~0.5s) to cycle 2,3,4,1. Then N, browse page2, set its first row to Arc, close with Esc, and tap C once to select page2.");
                    }
                }
                case CYCLE_AND_EDIT -> {
                    if (edit != null) {
                        helper.assertValueEqual(state.presets.current(), 0, "editing nonselected page does not select it");
                        helper.assertValueEqual(state.presets.skill(1, 0), "arc_gen", "real preset_edit wire commits learned skill to nonselected page");
                        helper.assertTrue(state.presets.revision() > edit.revision, "authoritative edit advances revision");
                    }
                }
                case SECOND_ARC -> {
                    helper.assertValueEqual(state.presets.current(), 1, "fifth physical C request selects edited page2");
                    helper.assertValueEqual(state.presets.currentSkill(0), "arc_gen", "edited page resolves Arc at LMB");
                    if (secondArc == null && drainTicks++ == 0) tell("Edited page2 selected by server. Aim at the same villager and left-click once more to prove the edited mapping.");
                    if (secondArc != null && arcAttacks == 2) { assertArc(secondArc, 2); drainTicks = 0; phase = Phase.DRAIN; }
                }
                case DRAIN -> {
                    if (++drainTicks >= 20 && sentStates > 0 && sentChargingStart == 1 && sentChargingEnd == 1 && sentArc == 2) succeed();
                }
            }
        }
        void assertArc(Wire wire, int hit) {
            close(arcDamage, 9, "mastered native SkillAttackEvent amount");
            close(target.getHealth(), 200 - hit * 9, "real native living target damage");
            close(arcPaidCp, wire.cp - 70, "wire-resolved Arc pays authoritative 70 CP");
            close(arcPaidOverload, wire.overload + 11, "wire-resolved Arc pays authoritative 11 overload");
            close(state.levelExperience, wire.levelExp + .0072, "real Arc entity hit awards source progression");
            int maximum = state.cooldownMaximum("arc_gen");
            helper.assertValueEqual(maximum, 5, "mastered Arc captures greatest server cooldown maximum separately from remaining ticks");
            helper.assertTrue(state.cooldowns.getOrDefault("arc_gen", 0) > 0 && state.cooldowns.get("arc_gen") <= maximum, "live cooldown blocks immediate replay");
            arcMaximum = Math.max(arcMaximum, maximum);
            close(energy(), releaseEnergy, "Arc and preset UI do not charge finite developer");
        }
        void close(double actual, double expected, String message) { helper.assertTrue(Math.abs(actual - expected) < .00001, message + ": expected " + expected + ", got " + actual); }
        void tell(String message) { LOG.info("ACADEMY_SOCKET_STEP phase={} {}", phase, message); player.sendSystemMessage(Component.literal("[Socket QA] " + message)); }
        void succeed() {
            proof.setProperty("status", "passed"); proof.setProperty("chargingNativeTicks", Integer.toString(chargedTicks));
            proof.setProperty("chargingFiniteIF", Double.toString(releaseEnergy)); proof.setProperty("chargingPaidCP", Integer.toString(chargedTicks * 7));
            proof.setProperty("chargingStartFloor", "48"); proof.setProperty("releaseQuietNativeTicks", Integer.toString(quietTicks));
            proof.setProperty("arcNativeHits", Integer.toString(arcAttacks)); proof.setProperty("targetFinalHealth", Float.toString(target.getHealth()));
            proof.setProperty("presetCycle", "1,2,3,0,1"); proof.setProperty("nonselectedEdit", "1:0:arc_gen while current=0");
            proof.setProperty("outboundNativeStates", Integer.toString(sentStates)); proof.setProperty("outboundChargingStart", Integer.toString(sentChargingStart));
            proof.setProperty("outboundChargingEnd", Integer.toString(sentChargingEnd)); proof.setProperty("outboundArcEffects", Integer.toString(sentArc));
            proof.setProperty("decodedAcademyRequests", Integer.toString(wires.size()));
            proof.setProperty("claimBoundary", "Server proves real TCP NeoForge admission, decoded native payloads, native authoritative changes and sent effects. Physical UI and client receipt need independent client proof and CUA evidence.");
            write("socket-server-proof.properties"); done = true;
            LOG.info("ACADEMY_SOCKET_PROOF_PASS nativeTCP=true chargingTicks={} finiteIF={} arcHits={} cooldownMaximum={} editedPreset=1", chargedTicks, releaseEnergy, arcAttacks, arcMaximum);
            player.sendSystemMessage(Component.literal("[Socket QA] Server TCP/gameplay proof passed. Verify socket-client-proof.properties and screenshots separately."));
            helper.runAfterDelay(0, helper::succeed);
        }
        void failNative(String reason) {
            if (done) return;
            failure(reason);
            // Run failure inside the native GameTestTicker so listeners/reporters are notified normally.
            helper.runAfterDelay(0, () -> helper.fail(reason));
        }
        void failure(String reason) {
            if (done) return;
            proof.setProperty("status", "failed"); proof.setProperty("phase", phase.toString()); proof.setProperty("reason", reason);
            proof.setProperty("chargingNativeTicks", Integer.toString(chargedTicks)); proof.setProperty("arcNativeHits", Integer.toString(arcAttacks));
            write("socket-server-failure.properties"); done = true;
            LOG.error("ACADEMY_SOCKET_PROOF_FAIL phase={} reason={}", phase, reason);
        }
        void write(String name) {
            try (OutputStream output = Files.newOutputStream(directory().resolve(name))) { proof.store(output, "Observed native socket QA; not a serialization-only or memory-channel result"); }
            catch (IOException e) { throw new IllegalStateException("Cannot write narrow socket proof", e); }
        }
        void cleanup() {
            if (target != null) target.discard();
            if (connection != null && connection.channel().isOpen()) connection.channel().eventLoop().execute(() -> {
                if (connection.channel().pipeline().get(OBSERVER) != null) connection.channel().pipeline().remove(OBSERVER);
            });
        }
    }
}

package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.develop.*;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.machine.*;
import cn.academy.port.preset.PresetSkills;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW341 native Advanced-machine learning. Fixtures explicitly accelerate paid development ticks. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerSpatialPincerLearningRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_spatial_pincer_corridor_learning";
    private static final String ID = HighPowerSkills.SPATIAL_PINCER_CORRIDOR;
    private static final int TICKS = 176, COST = 7040;
    private HighPowerSpatialPincerLearningRuntimeTests() {}

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void paid_313_311_321_chain_and_176_actual_advanced_ticks_grant_only341(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(); var s = AbilityStorage.get(p);
        s.changeCategoryClassic("teleporter"); s.setLevel(4); f.parents(p);
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow();
        h.assertTrue(skill.level() == 4 && skill.requirements().equals(List.of(
                new SkillCatalog.Requirement(HighPowerSkills.CROSS_SUBSTITUTION, 0)))
                && s.exp(HighPowerSkills.CROSS_SUBSTITUTION) == 0, "paid321 at zero mastery is the exact LV4 prerequisite");
        h.assertTrue(DeveloperType.minimumForSkill(4) == DeveloperType.ADVANCED
                && ClassicRules.learningStimulations(4) == 11
                && DeveloperType.ADVANCED.ticksPerStimulation() * 11 == TICKS
                && DeveloperType.ADVANCED.actualConsumption(11) == COST,
                "LV4 needs Advanced, eleven stimulations,176 actual paid ticks and7040 IF");
        var old = AbilityStorage.encodeSaved(s);
        h.assertTrue(!AbilityStorage.decodeSaved(old, s.cpDataConfig()).learned(ID), "prior native save has no silent341 grant");
        f.deplete(p); var combat = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();
        var resources = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy(); double xp = s.levelExperience;
        var growth = HighPowerGrowth.values(p); float nativeHp = p.getHealth();
        var advanced = f.machine(p, DeveloperType.ADVANCED, COST);
        h.assertTrue(f.start(p, ID, Integer.MAX_VALUE), "actual token-authenticated Advanced machine starts canonical341");
        var process = DevelopmentController.process(p);
        h.assertTrue(process.snapshot().developerType() == DeveloperType.ADVANCED && process.maxStimulations() == 11
                && process.action().id().equals("skill:spatial_pincer_corridor"), "server owns the Advanced tier, skill and eleven stimulations");
        f.develop(p, TICKS - 1);
        h.assertTrue(!s.learned(ID) && advanced.battery().getEnergy() == 40
                && process.stimulation() == 10 && process.tickThisStimulation() == 15, "175 actual paid ticks cannot learn early");
        f.develop(p, 1);
        h.assertTrue(s.learned(ID) && s.exp(ID) == 0 && s.exp(HighPowerSkills.CROSS_SUBSTITUTION) == 0
                && s.levelExperience == xp && advanced.battery().getEnergy() == 0
                && process.state() == DevelopmentProcess.State.DONE, "176 paid ticks debit7040 IF and award no mastery or level XP");
        h.assertTrue(combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY))
                && resources.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)), "native learning preserves depleted high-power ledgers exactly");
        var afterGrowth = HighPowerGrowth.values(p);
        h.assertTrue(afterGrowth.position().compareTo(growth.position()) == 0
                && afterGrowth.earnedPosition().compareTo(growth.earnedPosition()) == 0
                && afterGrowth.cpMaximum() == growth.cpMaximum() && p.getHealth() == nativeHp
                && HighPowerResources.cp(p) == 37, "learning does not refill high-power CP/health or advance active/earned growth");
        h.assertTrue(HighPowerSkills.ALL.size() == 51 && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).id().equals("remote_scatter_relay") && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection") && HighPowerSkills.ALL.get(46).id().equals("vector_cage") && HighPowerSkills.ALL.get(45).id().equals("circulation_listening") && HighPowerSkills.ALL.get(44).id().equals("circulation_relief") && HighPowerSkills.ALL.get(43).id().equals("trajectory_return") && HighPowerSkills.ALL.get(42).id().equals("yaw_palm") && HighPowerSkills.ALL.get(41).id().equals("inertial_anchor") && HighPowerSkills.ALL.get(40).id().equals("shear_windblade") && HighPowerSkills.ALL.get(39).id().equals("braking_touch") && HighPowerSkills.ALL.get(35).id().equals(ID)
                && SkillCatalog.REGISTERED.size() == 101 && cn.academy.port.SkillCatalog.REGISTERED.get(100).id().equals("remote_scatter_relay") && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown") && SkillCatalog.REGISTERED.get(98).id().equals("wind_pressure_lift") && SkillCatalog.REGISTERED.get(85).id().equals(ID)
                && PresetSkills.REGISTERED.size() == 86 && cn.academy.port.preset.PresetSkills.REGISTERED.contains("remote_scatter_relay") && cn.academy.port.preset.PresetSkills.REGISTERED.contains("point_meltdown") && PresetSkills.REGISTERED.contains("wind_pressure_lift") && SkillCatalog.REGISTERED_CONFIG_KEYS.size() == 92 && SkillCatalog.REGISTERED_CONFIG_KEYS.contains(cn.academy.port.core.ClassicSkillConfiguration.key("meltdowner", "remote_scatter_relay")) && SkillCatalog.REGISTERED_CONFIG_KEYS.contains(cn.academy.port.core.ClassicSkillConfiguration.key("vecmanip", "wind_pressure_lift")),
                "NEW341 appends after all thirty-five old high-power skills without changing classic ordinals");
        for (int preset = 0; preset < 4; preset++) for (int slot = 0; slot < 4; slot++) {
            h.assertTrue(wire(p, "preset_edit", preset + ":" + slot + ":" + ID), "341 binds every ordinary native slot");
            h.assertFalse(wire(p, "preset_edit", preset + ":" + ((slot + 1) % 4) + ":" + ID), "duplicate binding remains refused");
            h.assertTrue(wire(p, "preset_edit", preset + ":" + slot + ":"), "clear only tested native mapping");
        }
        h.assertTrue(wire(p, "preset_edit", "3:3:" + ID) && wire(p, "activate_state", "true")
                && HighPowerSkills.eligible(s, ID), "ordinary binding and activation admit paid341");
        var saved = AbilityStorage.encodeSaved(s); var restored = AbilityStorage.decodeSaved(saved, s.cpDataConfig());
        h.assertTrue(saved.getInt("schema") == 3 && restored.learned(ID) && restored.presets.skill(3, 3).equals(ID)
                && saved.equals(AbilityStorage.encodeSaved(restored)), "canonical341 retains unchanged schema and exact binding save roundtrip");
        h.assertFalse(f.start(p, ID, 1), "learned341 cannot be purchased again");
        h.assertTrue(SkillCatalog.ALL.size() == 50 && PresetSkills.IMPLEMENTED.size() == 35
                && SkillCatalog.levelSkillCount(s) == SkillCatalog.ALL.stream().filter(entry -> entry.category().equals("teleporter")
                && entry.level() == 4 && entry.controllable()).count(), "classic catalog, presets and level XP denominator remain unchanged");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void exact_parent_integer_level_hardware_and_category_are_server_owned(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(); var s = AbilityStorage.get(p);
        s.changeCategoryClassic("teleporter"); s.setLevel(4);
        f.learn(p, HighPowerSkills.SAFE_LANDING_PROBE); f.learn(p, HighPowerSkills.DISPLACED_PROJECTION);
        f.learn(p, HighPowerSkills.RECOVERY_MARK); f.learn(p, HighPowerSkills.SPATIAL_RELAY);
        var advanced = f.machine(p, DeveloperType.ADVANCED, COST);
        h.assertFalse(f.start(p, ID, 1), "paid323 and paid343 cannot substitute for321");
        h.assertTrue(advanced.battery().getEnergy() == COST, "missing parent consumes no IF");
        f.learn(p, HighPowerSkills.CROSS_SUBSTITUTION);
        s.setLevel(3); advanced = f.machine(p, DeveloperType.ADVANCED, COST);
        h.assertFalse(f.start(p, ID, 2), "paid321 and Advanced machine cannot bypass integerLV4");
        h.assertTrue(advanced.battery().getEnergy() == COST, "low level consumes no IF"); s.setLevel(4);
        var portable = f.portable(p, 10000);
        h.assertFalse(DevelopmentController.startSkill(p, ID), "Portable refuses LV4");
        h.assertTrue(portable.energy() == 10000, "Portable rejection is free");
        var normal = f.machine(p, DeveloperType.NORMAL, COST);
        h.assertFalse(f.start(p, ID, 3), "Normal refuses LV4");
        h.assertTrue(normal.battery().getEnergy() == COST, "Normal rejection is free");
        h.assertFalse(wire(p, "preset_edit", "0:0:" + ID), "unlearned341 cannot bind");
        s.category = "meltdowner"; advanced = f.machine(p, DeveloperType.ADVANCED, COST);
        h.assertFalse(f.start(p, ID, 4), "stale321 cannot cross categories");
        h.assertTrue(advanced.battery().getEnergy() == COST && !s.learned(ID), "wrong category has no payment or grant");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void unpaid_final_tick_cancel_and_changed_gate_never_grant341(GameTestHelper h) {
        var f = new Fixture(h);
        for (String reason : List.of("battery", "cancel", "level", "parent", "category", "configuration")) {
            var p = f.player(); var s = AbilityStorage.get(p); s.changeCategoryClassic("teleporter"); s.setLevel(4); f.parents(p);
            var advanced = f.machine(p, DeveloperType.ADVANCED, reason.equals("battery") ? COST - 1 : COST);
            h.assertTrue(f.start(p, ID, 1), "actual machine begins before late " + reason); f.develop(p, TICKS - 1);
            h.assertTrue(!s.learned(ID) && advanced.battery().getEnergy() == (reason.equals("battery") ? 39 : 40), "final IF balance before " + reason);
            try {
                switch (reason) {
                    case "cancel" -> DevelopmentController.abort(p);
                    case "level" -> s.setLevel(3);
                    case "parent" -> s.unlearn(HighPowerSkills.CROSS_SUBSTITUTION);
                    case "category" -> s.changeCategoryClassic("electromaster");
                    case "configuration" -> SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("teleporter", ID),
                            new ClassicSkillConfiguration.Flags(false, false)));
                    default -> { }
                }
                f.develop(p, 1);
                h.assertTrue(!s.learned(ID) && DevelopmentController.process(p).state() == DevelopmentProcess.State.FAILED
                        && advanced.battery().getEnergy() == (reason.equals("cancel") ? 40 : reason.equals("battery") ? 39 : 0),
                        "native terminal refusal has no grant or refund; atomic machine shortage retains39 IF: " + reason);
            } finally { SkillCatalog.resetConfiguration(); }
        }
        h.succeed();
    }

    private static boolean wire(ServerPlayer player, String action, String value) {
        return AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request(action, value));
    }

    private static final class Fixture {
        final GameTestHelper h;
        final Map<DeveloperType, MachineDeveloperBlockEntity> machines = new EnumMap<>(DeveloperType.class);
        final List<ServerPlayer> players = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>();
        boolean closed;

        Fixture(GameTestHelper h) {
            this.h = h;
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {}
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        ServerPlayer player() {
            var server = h.getLevel().getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "PincerLearn"), false);
            var player = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            players.add(player);
            var connection = new Connection(PacketFlow.SERVERBOUND);
            var channel = new EmbeddedChannel(connection);
            channels.add(channel);
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            var pos = h.absoluteVec(new Vec3(5.5, 2, 4.5));
            player.moveTo(pos.x, pos.y, pos.z, 0, 0);
            player.setNoGravity(true);
            player.setGameMode(GameType.SURVIVAL);
            return player;
        }
        DeveloperItemEnergy portable(ServerPlayer player, double amount) {
            var stack = new ItemStack(AcademyCraft.DEVELOPER.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var energy = new DeveloperItemEnergy(stack, DeveloperType.PORTABLE);
            energy.energy(amount);
            return energy;
        }
        void develop(ServerPlayer player, int ticks) {
            for (int tick = 0; tick < ticks; tick++) DevelopmentController.tick(player, ignored -> {});
        }
        MachineDeveloperBlockEntity machine(ServerPlayer player, DeveloperType type, double amount) {
            h.assertTrue(type == DeveloperType.NORMAL || type == DeveloperType.ADVANCED, "only actual stationary developer tiers use this fixture");
            var base = h.absolutePos(new BlockPos(12, 1, type == DeveloperType.NORMAL ? 14 : 8));
            var machine = machines.get(type);
            if (machine == null) {
                var item = type == DeveloperType.NORMAL ? MachineDevelopers.NORMAL_ITEM.get() : MachineDevelopers.ADVANCED_ITEM.get();
                var stack = new ItemStack(item);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                h.assertTrue(item.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                        new BlockHitResult(Vec3.atCenterOf(base), Direction.UP, base, false))).consumesAction(),
                        "the complete physical " + type + " machine is placed through its real item");
                machine = (MachineDeveloperBlockEntity) h.getLevel().getBlockEntity(base);
                machines.put(type, machine);
            }
            machines.values().forEach(MachineDeveloperBlockEntity::endUsers);
            machine.battery().load(amount);
            player.moveTo(base.getX() + .5, base.getY(), base.getZ() - 1.5, 0, 0);
            h.assertTrue(machine.developerType() == type && machine.use(player), "a sender-bound " + type + " session opens");
            return machine;
        }
        boolean start(ServerPlayer player, String skill, int sequence) {
            String full = MachineDeveloperSessions.activeToken(player).orElseThrow() + ":"
                    + new HighPowerLearningProtocol.Request(skill, sequence).wire();
            return MachineDeveloperSessions.request(player, new AcademyNetwork.Request("machine_hp_learn", full));
        }
        void learn(ServerPlayer player, String skill) {
            int level = SkillCatalog.find(AbilityStorage.get(player).category, skill).orElseThrow().level();
            int count = ClassicRules.learningStimulations(level);
            var type = DeveloperType.minimumForSkill(level);
            double cost = type.actualConsumption(count);
            if (type == DeveloperType.PORTABLE) {
                var battery = portable(player, cost);
                h.assertTrue(DevelopmentController.startSkill(player, skill), "finite Portable prerequisite learning starts for " + skill);
                develop(player, count * type.ticksPerStimulation());
                h.assertTrue(battery.energy() == 0, "the prerequisite pays every Portable tick");
            } else {
                var machine = machine(player, type, cost);
                h.assertTrue(start(player, skill, 341), "the actual " + type + " machine starts paid learning for " + skill);
                develop(player, count * type.ticksPerStimulation());
                h.assertTrue(machine.battery().getEnergy() == 0, "the prerequisite pays every physical machine tick");
            }
            h.assertTrue(AbilityStorage.get(player).learned(skill)
                    && DevelopmentController.process(player).state() == DevelopmentProcess.State.DONE,
                    "ordinary paid learning completes for " + skill);
        }
        void parents(ServerPlayer player) {
            learn(player, HighPowerSkills.SAFE_LANDING_PROBE);
            learn(player, HighPowerSkills.DISPLACED_PROJECTION);
            learn(player, HighPowerSkills.CROSS_SUBSTITUTION);
        }
        void deplete(ServerPlayer player) {
            HighPowerGrowth.values(player);
            // Declared depleted-pool fixture: learning may not refill current high-power CP or health.
            player.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", 37);
            var maximum = new BigDecimal(HighPowerCombat.maxHealthText(player));
            player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health",
                    CombatAmounts.decimalString(maximum.divide(BigDecimal.TWO)));
            player.setHealth(10);
        }
        void close() {
            if (closed) return;
            closed = true;
            SkillCatalog.resetConfiguration();
            for (var player : players) {
                DevelopmentController.remove(player);
                MachineDeveloperSessions.close(player);
                AbilityStorage.remove(player);
                var list = h.getLevel().getServer().getPlayerList();
                if (list.getPlayer(player.getUUID()) == player) list.remove(player); else player.discard();
            }
            channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }
}

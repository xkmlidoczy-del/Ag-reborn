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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
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

/** NEW242 uses native LV4 Advanced development, canonical saves and the existing resource ledgers.
 * Setup explicitly accelerates paid developer ticks and declares injury/shield/CP fixtures. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerDissipationCurtainLearningRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_dissipation_curtain_learning";
    private static final int TICKS = 176, COST = 7040;

    private HighPowerDissipationCurtainLearningRuntimeTests() {}

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void integer_lv4_and_176_paid_advanced_ticks_are_required_without_refill(GameTestHelper h) {
        var f = new Fixture(h);
        var p = f.player();
        var s = AbilityStorage.get(p);
        s.changeCategoryClassic("meltdowner");
        s.setLevel(3);
        var skill = SkillCatalog.find("meltdowner", HighPowerSkills.DISSIPATION_CURTAIN).orElseThrow();
        h.assertTrue(skill.level() == 4 && skill.requirements().equals(List.of(
                new SkillCatalog.Requirement(HighPowerSkills.SCATTER_PLATES, 0)))
                && HighPowerSkills.DISSIPATION_CURTAIN_PROPOSAL == 242,
                "NEW242 is exactly LV4 with the learned scatter-plates prerequisite");
        h.assertTrue(ClassicRules.learningStimulations(4) == 11
                && DeveloperType.minimumForSkill(4) == DeveloperType.ADVANCED
                && DeveloperType.ADVANCED.ticksPerStimulation() == 16
                && DeveloperType.ADVANCED.energyPerTick() == 40
                && DeveloperType.ADVANCED.actualConsumption(11) == COST,
                "native LV4 contract is 11 stimulations, 176 ticks and 7040 IF");
        f.parents(p);
        var advanced = f.machine(p, DeveloperType.ADVANCED, COST);
        h.assertFalse(f.start(p, skill.id(), 1), "paid prerequisites and an Advanced machine cannot bypass integer LV4");
        h.assertTrue(advanced.battery().getEnergy() == COST, "wrong integer level consumes no machine IF");
        s.setLevel(4);
        var portable = f.portable(p, 10000);
        h.assertFalse(DevelopmentController.startSkill(p, skill.id()), "Portable refuses LV4 despite valid level and parents");
        h.assertTrue(portable.energy() == 10000, "Portable rejection leaves its battery untouched");
        var normal = f.machine(p, DeveloperType.NORMAL, COST);
        h.assertFalse(f.start(p, skill.id(), 2), "Normal also refuses LV4 despite valid level and parents");
        h.assertTrue(normal.battery().getEnergy() == COST, "Normal rejection leaves its physical battery untouched");
        h.assertFalse(wire(p, "preset_edit", "0:3:" + skill.id()), "unlearned NEW242 cannot bind");

        f.deplete(p);
        var before = Ledger.capture(p);
        double xp = s.levelExperience;
        advanced = f.machine(p, DeveloperType.ADVANCED, COST);
        h.assertTrue(f.start(p, skill.id(), Integer.MAX_VALUE), "canonical request starts the actual Advanced machine");
        var process = DevelopmentController.process(p);
        h.assertTrue(process.snapshot().developerType() == DeveloperType.ADVANCED
                && process.maxStimulations() == 11 && process.action().id().equals("skill:directional_dissipation_curtain"),
                "the server owns the real Advanced action and all eleven stimulations");
        f.develop(p, TICKS - 1);
        h.assertTrue(!s.learned(skill.id()) && advanced.battery().getEnergy() == 40
                && process.stimulation() == 10 && process.tickThisStimulation() == 15,
                "175 paid ticks leave exactly 40 IF and cannot grant NEW242 early");
        f.develop(p, 1);
        h.assertTrue(s.learned(skill.id()) && s.exp(skill.id()) == 0 && s.levelExperience == xp
                && advanced.battery().getEnergy() == 0 && process.state() == DevelopmentProcess.State.DONE,
                "tick 176 spends the final 40 IF, learns the canonical skill and awards no mastery or level XP");
        before.assertUnchanged(h, p, "successful Advanced learning");
        for (int preset = 0; preset < 4; preset++) for (int slot = 0; slot < 4; slot++) {
            h.assertTrue(wire(p, "preset_edit", preset + ":" + slot + ":" + skill.id()), "NEW242 binds each native preset slot");
            h.assertFalse(wire(p, "preset_edit", preset + ":" + ((slot + 1) % 4) + ":" + skill.id()), "native duplicate mapping is refused");
            h.assertTrue(wire(p, "preset_edit", preset + ":" + slot + ":"), "the tested mapping clears normally");
        }
        h.assertTrue(wire(p, "preset_edit", "3:3:" + skill.id()) && wire(p, "activate_state", "true")
                && HighPowerSkills.eligible(s, skill.id()), "paid NEW242 uses ordinary native activation");
        h.assertFalse(f.start(p, skill.id(), 3), "a learned skill cannot be purchased twice");
        assertClassicBoundary(h, s);
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void paid_other_branch_cannot_replace_exact_scatter_parent(GameTestHelper h) {
        var f = new Fixture(h);
        var p = f.player();
        var s = AbilityStorage.get(p);
        s.changeCategoryClassic("meltdowner");
        s.setLevel(4);
        f.learn(p, HighPowerSkills.DUAL_POINT);
        f.learn(p, HighPowerSkills.FOLDED_MIRROR_ARRAY);
        f.learn(p, HighPowerSkills.DISINTEGRATION_SWEEP);
        f.learn(p, HighPowerSkills.PARTICLE_LAMINATE);
        var machine = f.machine(p, DeveloperType.ADVANCED, COST);
        h.assertFalse(f.start(p, HighPowerSkills.DISSIPATION_CURTAIN, 1),
                "the fully paid 211 to 221 to 231 to 241 branch cannot replace NEW212");
        h.assertTrue(machine.battery().getEnergy() == COST && !s.learned(HighPowerSkills.DISSIPATION_CURTAIN),
                "the wrong paid branch spends no Advanced IF and cannot silently learn NEW242");
        f.learn(p, HighPowerSkills.SCATTER_PLATES);
        machine = f.machine(p, DeveloperType.ADVANCED, COST);
        h.assertTrue(s.exp(HighPowerSkills.SCATTER_PLATES) == 0
                && f.start(p, HighPowerSkills.DISSIPATION_CURTAIN, 2),
                "genuinely paid NEW212 at zero mastery satisfies the exact prerequisite");
        f.develop(p, TICKS);
        h.assertTrue(s.learned(HighPowerSkills.DISSIPATION_CURTAIN) && machine.battery().getEnergy() == 0,
                "the real 211 to 212 to 242 branch completes with finite payment");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void final_shortage_cancel_and_late_gate_changes_never_grant_lv4(GameTestHelper h) {
        var f = new Fixture(h);
        for (String invalidation : List.of("battery", "level", "category", "parent", "config", "cancel")) {
            var p = f.player();
            var s = AbilityStorage.get(p);
            s.changeCategoryClassic("meltdowner");
            s.setLevel(4);
            f.parents(p);
            f.deplete(p);
            var before = Ledger.capture(p);
            var machine = f.machine(p, DeveloperType.ADVANCED, invalidation.equals("battery") ? COST - 1 : COST);
            h.assertTrue(f.start(p, HighPowerSkills.DISSIPATION_CURTAIN, 1), "valid Advanced ingress precedes late " + invalidation);
            f.develop(p, TICKS - 1);
            double remainder = invalidation.equals("battery") ? 39 : 40;
            h.assertTrue(machine.battery().getEnergy() == remainder && !s.learned(HighPowerSkills.DISSIPATION_CURTAIN)
                    && DevelopmentController.process(p).state() == DevelopmentProcess.State.DEVELOPING,
                    "175 paid ticks leave the exact final-tick balance before " + invalidation);
            try {
                switch (invalidation) {
                    case "cancel" -> DevelopmentController.abort(p);
                    case "level" -> s.setLevel(3);
                    case "category" -> s.changeCategoryClassic("electromaster");
                    case "parent" -> s.unlearn(HighPowerSkills.SCATTER_PLATES);
                    case "config" -> SkillCatalog.initializeConfiguration(Map.of(
                            ClassicSkillConfiguration.key("meltdowner", HighPowerSkills.DISSIPATION_CURTAIN),
                            new ClassicSkillConfiguration.Flags(false, false)));
                }
                f.develop(p, 1);
                double expected = invalidation.equals("cancel") ? 40 : invalidation.equals("battery") ? 39 : 0;
                h.assertTrue(machine.battery().getEnergy() == expected && !s.learned(HighPowerSkills.DISSIPATION_CURTAIN)
                        && DevelopmentController.process(p).state() == DevelopmentProcess.State.FAILED,
                        "final Advanced tick refuses " + invalidation + " without grant or refund; an insufficient pull preserves 39 IF");
                before.assertExactTags(h, p, "failed Advanced completion after " + invalidation);
            } finally {
                SkillCatalog.resetConfiguration();
            }
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void native_saved_capability_preserves_canonical_ids_and_depleted_ledgers(GameTestHelper h) {
        var f = new Fixture(h);
        var p = f.player();
        var s = AbilityStorage.get(p);
        s.changeCategoryClassic("meltdowner");
        s.setLevel(4);
        f.parents(p);
        f.learn(p, HighPowerSkills.FOLDED_MIRROR_ARRAY);
        f.learn(p, HighPowerSkills.DISINTEGRATION_SWEEP);
        f.learn(p, HighPowerSkills.PARTICLE_LAMINATE);
        f.learn(p, HighPowerSkills.THERMAL_SHELL);
        f.learn(p, HighPowerSkills.BEAM_ETCHING);
        f.learn(p, HighPowerSkills.BEAM_STEP);
        String[] prior = {"dual_point", "scatter_plates", "beam_etching", "folded_mirror_array", "thermal_shell", "disintegration_sweep", "beam_step", "particle_laminate"};
        for (int i = 0; i < prior.length; i++)
            h.assertTrue(wire(p, "preset_edit", (i / 4) + ":" + (i % 4) + ":" + prior[i]), "every old MD mapping binds before NEW242");
        var oldSnapshot = AbilityStorage.encodeSaved(s);
        var old = AbilityStorage.decodeSaved(oldSnapshot, s.cpDataConfig());
        h.assertTrue(oldSnapshot.getInt("schema") == 3 && !old.learned(HighPowerSkills.DISSIPATION_CURTAIN)
                && !oldSnapshot.getCompound("skills").contains("directional_dissipation_curtain")
                && AbilityStorage.encodeSaved(old).equals(oldSnapshot),
                "a genuine pre-NEW242 native capability round-trips exactly without migration or silent learning");

        f.learn(p, HighPowerSkills.DISSIPATION_CURTAIN);
        f.deplete(p);
        var resources = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
        resources.putLong("next_directional_dissipation_curtain", p.serverLevel().getGameTime() + 500);
        h.assertTrue(wire(p, "preset_edit", "3:3:directional_dissipation_curtain"), "the canonical NEW242 mapping enters native saved presets");
        var capability = AbilityStorage.encodeSaved(s);
        var before = Ledger.capture(p);
        var loaded = f.reload(p);
        var restored = AbilityStorage.get(loaded);
        h.assertTrue(AbilityStorage.encodeSaved(restored).equals(capability)
                && restored.learned(HighPowerSkills.DISSIPATION_CURTAIN) && restored.exp(HighPowerSkills.DISSIPATION_CURTAIN) == 0
                && restored.presets.skill(3, 3).equals("directional_dissipation_curtain"),
                "actual entity save/load restores the complete native capability and canonical NEW242 ID");
        for (int i = 0; i < prior.length; i++)
            h.assertTrue(restored.presets.skill(i / 4, i % 4).equals(prior[i]), "native reload retains each old MD mapping");
        before.assertUnchanged(h, loaded, "native save/reload");
        assertClassicBoundary(h, restored);

        s = AbilityStorage.get(p);
        s.changeCategoryClassic("electromaster");
        f.learn(p, HighPowerSkills.FORKED_ARC);
        s.changeCategoryClassic("meltdowner");
        for (int preset = 0; preset < 4; preset++) for (int slot = 0; slot < 4; slot++)
            h.assertTrue(s.presets.skill(preset, slot).isEmpty(), "ordinary category changes clear every old mapping");
        var machine = f.machine(p, DeveloperType.ADVANCED, COST);
        h.assertFalse(f.start(p, HighPowerSkills.DISSIPATION_CURTAIN, 1), "category return cannot bypass renewed paid prerequisites");
        h.assertTrue(machine.battery().getEnergy() == COST, "missing-parent rejection does not drain the Advanced machine");
        f.parents(p);
        f.learn(p, HighPowerSkills.DISSIPATION_CURTAIN);
        before.assertStableBalances(h, p, "category switches and complete paid relearning");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void canonical_wire_fits_native_bound_and_pays_sender_owned_advanced_machine(GameTestHelper h) {
        var f = new Fixture(h);
        var p = f.player();
        var s = AbilityStorage.get(p);
        s.changeCategoryClassic("meltdowner");
        s.setLevel(4);
        f.parents(p);
        var machine = f.machine(p, DeveloperType.ADVANCED, COST);
        var request = new HighPowerLearningProtocol.Request(HighPowerSkills.DISSIPATION_CURTAIN, Integer.MAX_VALUE);
        String full = MachineDeveloperSessions.activeToken(p).orElseThrow() + ":" + request.wire();
        h.assertTrue(request.wire().equals("dc:~zik0zj")
                && full.getBytes(StandardCharsets.UTF_8).length <= 64
                && HighPowerLearningProtocol.parse(request.wire()).skill().equals("directional_dissipation_curtain"),
                "the wire-only dc alias fits the unchanged 64-byte bound and restores the canonical NEW242 ID");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        AcademyNetwork.Request decoded;
        try {
            AcademyNetwork.Request.CODEC.encode(buffer, new AcademyNetwork.Request("machine_hp_learn", full));
            decoded = AcademyNetwork.Request.CODEC.decode(buffer);
            h.assertTrue(buffer.readableBytes() == 0 && decoded.value().equals(full), "the real bounded native codec consumes the whole correlated request");
        } finally {
            buffer.release();
        }
        h.assertFalse(MachineDeveloperSessions.request(p, new AcademyNetwork.Request("machine_hp_learn", UUID.randomUUID() + ":" + request.wire())),
                "a forged machine token cannot purchase NEW242");
        h.assertTrue(machine.battery().getEnergy() == COST, "forged-session rejection consumes no IF");
        h.assertTrue(MachineDeveloperSessions.request(p, decoded), "the actual sender-owned Advanced session accepts the decoded request");
        f.develop(p, TICKS - 1);
        h.assertTrue(machine.battery().getEnergy() == 40 && !s.learned(HighPowerSkills.DISSIPATION_CURTAIN), "the physical Advanced machine still needs tick 176");
        f.develop(p, 1);
        h.assertTrue(machine.battery().getEnergy() == 0 && s.learned(HighPowerSkills.DISSIPATION_CURTAIN)
                && HighPowerLearningProtocol.result(request, true, "").getString("skill").equals("directional_dissipation_curtain"),
                "finite real-machine completion and feedback retain the exact canonical ID");
        assertClassicBoundary(h, s);
        h.succeed();
    }

    private static void assertClassicBoundary(GameTestHelper h, cn.academy.port.core.AbilityProgress state) {
        long classicLv4 = SkillCatalog.ALL.stream().filter(skill -> skill.category().equals("meltdowner")
                && skill.level() == 4 && skill.controllable()).count();
        h.assertTrue(state.level == 4 && classicLv4 == 3 && SkillCatalog.ALL.size() == 50
                && PresetSkills.IMPLEMENTED.size() == 35 && SkillCatalog.REGISTERED.size() == 111 && cn.academy.port.SkillCatalog.REGISTERED.get(101).id().equals("scatter_locator_plate") && cn.academy.port.SkillCatalog.REGISTERED.get(102).equals(new cn.academy.port.SkillCatalog.Skill("vecmanip", "autonomous_vector_circle", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("multi_axis_reflection", 0)), 0)) && cn.academy.port.SkillCatalog.REGISTERED.get(100).id().equals("remote_scatter_relay") && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown") && SkillCatalog.REGISTERED.get(98).id().equals("wind_pressure_lift")
                && PresetSkills.REGISTERED.size() == 96 && cn.academy.port.preset.PresetSkills.REGISTERED.contains("scatter_locator_plate") && cn.academy.port.preset.PresetSkills.REGISTERED.contains("autonomous_vector_circle") && cn.academy.port.preset.PresetSkills.REGISTERED.contains("remote_scatter_relay") && cn.academy.port.preset.PresetSkills.REGISTERED.contains("point_meltdown") && PresetSkills.REGISTERED.contains("wind_pressure_lift") && HighPowerSkills.ALL.size() == 61 && cn.academy.port.highpower.HighPowerSkills.ALL.get(51).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "scatter_locator_plate", 2, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.highpower.HighPowerSkills.SCATTER_LOCATOR_PLATE_PROPOSAL == 223 && cn.academy.port.highpower.HighPowerSkills.ALL.get(52).equals(new cn.academy.port.SkillCatalog.Skill("vecmanip", "autonomous_vector_circle", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("multi_axis_reflection", 0)), 0)) && cn.academy.port.highpower.HighPowerSkills.AUTONOMOUS_VECTOR_CIRCLE_PROPOSAL == 452 && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).id().equals("remote_scatter_relay") && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection") && HighPowerSkills.ALL.get(46).id().equals("vector_cage") && HighPowerSkills.ALL.get(45).id().equals("circulation_listening") && HighPowerSkills.ALL.get(44).id().equals("circulation_relief") && HighPowerSkills.ALL.get(43).id().equals("trajectory_return") && HighPowerSkills.ALL.get(42).id().equals("yaw_palm") && HighPowerSkills.ALL.get(41).id().equals("inertial_anchor") && HighPowerSkills.ALL.get(40).id().equals("shear_windblade") && HighPowerSkills.ALL.get(39).id().equals("braking_touch") && HighPowerSkills.ALL.get(30).id().equals(HighPowerSkills.CORNER_EXCHANGE) && HighPowerSkills.ALL.get(31).id().equals(HighPowerSkills.SHORT_INTERCEPTION)
                && HighPowerSkills.ALL.get(32).id().equals(HighPowerSkills.SPATIAL_RELAY)
                && HighPowerSkills.ALL.get(33).id().equals(HighPowerSkills.EMERGENCY_RETURN)
                && HighPowerSkills.ALL.get(34).id().equals(HighPowerSkills.TACTICAL_COORDINATE_FORMATION)
                && SkillCatalog.REGISTERED.get(84).equals(HighPowerSkills.ALL.get(34))
                && HighPowerSkills.IDS.size() == 61 && cn.academy.port.highpower.HighPowerSkills.IDS.contains("scatter_locator_plate") && cn.academy.port.highpower.HighPowerSkills.IDS.contains("autonomous_vector_circle") && cn.academy.port.highpower.HighPowerSkills.IDS.contains("remote_scatter_relay") && cn.academy.port.highpower.HighPowerSkills.IDS.contains("point_meltdown") && HighPowerSkills.IDS.contains("wind_pressure_lift") && HighPowerSkills.IDS.contains("multi_axis_reflection") && HighPowerSkills.IDS.contains("circulation_listening") && HighPowerSkills.IDS.contains("circulation_relief") && HighPowerSkills.IDS.contains("trajectory_return") && HighPowerSkills.IDS.contains("yaw_palm") && HighPowerSkills.IDS.contains("inertial_anchor") && HighPowerSkills.IDS.contains("shear_windblade") && SkillCatalog.levelSkillCount(state) == classicLv4
                && SkillCatalog.ALL.stream().noneMatch(skill -> skill.id().equals("directional_dissipation_curtain"))
                && !PresetSkills.IMPLEMENTED.contains("directional_dissipation_curtain")
                && PresetSkills.REGISTERED.contains("directional_dissipation_curtain")
                && SkillCatalog.REGISTERED.stream().filter(skill -> skill.id().equals("directional_dissipation_curtain")).count() == 1
                && HighPowerSkills.ALL.get(35).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR)
                && SkillCatalog.REGISTERED.get(85).equals(HighPowerSkills.ALL.get(35)),
                "NEW353 appends at ordinal84: 85 registered skills, 70 presets and 35 pack skills while classic 50/35 and the MD LV4 XP denominator stay unchanged");
    }

    private record Ledger(CompoundTag combat, CompoundTag resources, BigDecimal q, BigDecimal cap) {
        static Ledger capture(ServerPlayer player) {
            var growth = HighPowerGrowth.values(player);
            return new Ledger(player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy(),
                    player.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy(), growth.position(), growth.earnedPosition());
        }
        void assertExactTags(GameTestHelper h, ServerPlayer player, String operation) {
            h.assertTrue(combat.equals(player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY))
                    && resources.equals(player.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),
                    operation + " preserves the exact HP, shield, CP and growth ledger tags");
        }
        void assertUnchanged(GameTestHelper h, ServerPlayer player, String operation) {
            var actual = HighPowerGrowth.values(player);
            h.assertTrue(q.compareTo(actual.position()) == 0 && cap.compareTo(actual.earnedPosition()) == 0,
                    operation + " cannot raise active q or earned capacity Q");
            assertExactTags(h, player, operation);
        }
        void assertStableBalances(GameTestHelper h, ServerPlayer player, String operation) {
            var actual = HighPowerGrowth.values(player);
            h.assertTrue(q.compareTo(actual.position()) == 0 && cap.compareTo(actual.earnedPosition()) == 0
                    && stable(combat).equals(stable(player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)))
                    && resources.equals(player.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),
                    operation + " preserves exact balances, q/Q and the saved cooldown despite native witness rebasing");
        }
        private static CompoundTag stable(CompoundTag input) {
            var copy = input.copy();
            var active = copy.getCompound("activeGrowth");
            active.remove("category");
            active.remove("nativeLevel");
            active.remove("nativeXp");
            active.remove("revision");
            active.remove("initial");
            return copy;
        }
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
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "CurtainLearn"), false);
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
                h.assertTrue(start(player, skill, 242), "the actual " + type + " machine starts paid learning for " + skill);
                develop(player, count * type.ticksPerStimulation());
                h.assertTrue(machine.battery().getEnergy() == 0, "the prerequisite pays every physical machine tick");
            }
            h.assertTrue(AbilityStorage.get(player).learned(skill)
                    && DevelopmentController.process(player).state() == DevelopmentProcess.State.DONE,
                    "ordinary paid learning completes for " + skill);
        }
        void parents(ServerPlayer player) {
            learn(player, HighPowerSkills.DUAL_POINT);
            learn(player, HighPowerSkills.SCATTER_PLATES);
        }
        void deplete(ServerPlayer player) {
            HighPowerGrowth.values(player);
            var ability = AbilityStorage.get(player);
            var before = HighPowerCombat.shield(player);
            var amount = new BigDecimal("12.325");
            String category = ability.category;
            h.assertTrue(category.equals(HighPowerCategories.MELTDOWNER), "depletion starts with genuine MD learning");
            h.assertFalse(HighPowerCombat.spendShield(player, amount), "public EM shield debit remains closed to MD");
            h.assertTrue(HighPowerCombat.shield(player).compareTo(before) == 0, "the rejected MD debit leaves its balance intact");
            // Declared test fixture: central debit with the existing EM eligibility seam, never a production MD bypass.
            ability.category = HighPowerCategories.ELECTROMASTER;
            try {
                h.assertTrue(HighPowerCombat.spendShield(player, amount), "the declared fixture spends real base shield through the central paired write");
            } finally {
                ability.category = category;
            }
            h.assertTrue(HighPowerCombat.shield(player).compareTo(before.subtract(amount)) == 0, "the setup removes exactly 123.25 base shield");
            var maximum = new BigDecimal(HighPowerCombat.maxHealthText(player));
            player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health",
                    CombatAmounts.decimalString(maximum.divide(BigDecimal.TWO)));
            player.setHealth(10);
            // Declared existing-reserve fixture: learning may neither mint nor refill either independent half.
            var reserve = new CompoundTag();
            reserve.putInt("version", 2);
            reserve.putString("amount", "18");
            reserve.putString("capacity", player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getString("maxShield"));
            reserve.putString("lower", "7");
            reserve.putString("upper", "11");
            player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).put(MagneticDomainReserve.KEY, reserve);
            player.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", 37);
        }
        ServerPlayer reload(ServerPlayer original) {
            AbilityStorage.save(original);
            var saved = original.saveWithoutId(new CompoundTag());
            AbilityStorage.remove(original);
            var restored = new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[CurtainReload]"));
            players.add(restored);
            restored.load(saved);
            AbilityStorage.get(restored);
            return restored;
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

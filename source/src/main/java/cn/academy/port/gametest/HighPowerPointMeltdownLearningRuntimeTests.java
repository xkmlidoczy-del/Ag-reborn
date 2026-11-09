package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.develop.*;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.machine.*;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
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

/** NEW233 finite native learning, sender-owned Normal developer and unchanged shared ledgers.
 * Level/material setup, injury/debit fixtures and accelerated native developer ticks are declared;
 * every learned skill, including prerequisites and relearning, pays its actual finite native cost. */
@GameTestHolder("academy_point_meltdown_runtime")
@PrefixGameTestTemplate(false)
public final class HighPowerPointMeltdownLearningRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_point_meltdown_learning";
    private static final String ID = "point_meltdown", CATEGORY = "meltdowner", PARENT = "beam_etching";
    private static final int TICKS = 147, COST = 5145;
    private HighPowerPointMeltdownLearningRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void point_meltdown_requires_beam_parent_lv3_and_all_147_normal_payments(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(); var s = AbilityStorage.get(p);
        var skill = SkillCatalog.find(CATEGORY, ID).orElseThrow();
        h.assertTrue(skill.equals(new SkillCatalog.Skill(CATEGORY, ID, 3, true,
                        List.of(new SkillCatalog.Requirement(PARENT, 0)), 0))
                        && HighPowerSkills.POINT_MELTDOWN_PROPOSAL == 233,
                "NEW233 has exactly LV3 and learned BeamEtching at zero mastery");
        h.assertTrue(DeveloperType.minimumForSkill(3) == DeveloperType.NORMAL
                        && ClassicRules.learningStimulations(3) == 7
                        && DeveloperType.NORMAL.ticksPerStimulation() == 21
                        && DeveloperType.NORMAL.energyPerTick() == 35
                        && DeveloperType.NORMAL.actualConsumption(7) == COST,
                "independent native formula requires 147 Normal ticks and 5145 IF");
        var machine = f.normal(p, COST);
        h.assertFalse(f.start(p, ID), "fresh MD cannot bypass the exact BeamEtching prerequisite");
        near(h, machine.battery().getEnergy(), COST, "missing parent spends no IF");
        h.assertFalse(HighPowerCombat.enabled(p), "rejected first purchase creates no combat ledger");
        f.learn(p, HighPowerSkills.DUAL_POINT); machine = f.normal(p, COST);
        h.assertFalse(f.start(p, ID), "learned DualPoint ancestor cannot substitute for BeamEtching");
        near(h, machine.battery().getEnergy(), COST, "wrong parent spends no IF");
        f.learn(p, PARENT); h.assertTrue(s.exp(PARENT) == 0, "actual paid parent requires no invented mastery");
        s.setLevel(2); machine = f.normal(p, COST);
        h.assertFalse(f.start(p, ID), "learned BeamEtching cannot bypass integer LV3");
        near(h, machine.battery().getEnergy(), COST, "low integer level spends no IF"); s.setLevel(3);
        var portable = f.portable(p, 10000);
        h.assertFalse(DevelopmentController.startSkill(p, ID), "Portable developer cannot learn native LV3");
        near(h, portable.energy(), 10000, "wrong developer tier spends no IF");
        h.assertFalse(wire(p, "preset_edit", "0:0:" + ID), "unlearned PointMeltdown cannot bind");
        f.learn(p, HighPowerSkills.SCATTER_PLATES); f.deplete(p); var before = f.ledger(p);
        machine = f.normal(p, COST); h.assertTrue(f.start(p, ID), "sender-owned physical Normal machine starts development");
        f.develop(p, TICKS - 1);
        h.assertTrue(!s.learned(ID) && DevelopmentController.process(p).state() == DevelopmentProcess.State.DEVELOPING,
                "146 paid native ticks never grant the skill early");
        near(h, machine.battery().getEnergy(), 35, "exact final native payment remains");
        h.assertTrue(before.equals(f.ledger(p)), "incomplete learning preserves HP, S, plate shares, reserve, CP and exact q/Q");
        f.develop(p, 1);
        h.assertTrue(s.learned(ID) && s.exp(ID) == 0 && DevelopmentController.process(p).state() == DevelopmentProcess.State.DONE,
                "only the 147th paid native tick grants the canonical learned bit without mastery");
        near(h, machine.battery().getEnergy(), 0, "all 5145 finite IF was paid");
        h.assertTrue(before.equals(f.ledger(p)), "completed learning cannot refill or advance any shared ledger or level XP");
        for (int preset = 0; preset < 4; preset++) for (int slot = 0; slot < 4; slot++) {
            h.assertTrue(wire(p, "preset_edit", preset + ":" + slot + ":" + ID), "genuinely learned NEW233 binds every native slot");
            h.assertFalse(wire(p, "preset_edit", preset + ":" + ((slot + 1) % 4) + ":" + ID), "native duplicate-per-preset rule remains enforced");
            h.assertTrue(wire(p, "preset_edit", preset + ":" + slot + ":"), "clear only the tested binding");
        }
        h.assertTrue(wire(p, "preset_edit", "3:3:" + ID) && wire(p, "activate_state", "true")
                && HighPowerSkills.eligible(s, ID), "paid skill uses ordinary preset and activation contracts");
        h.assertFalse(f.start(p, ID), "already learned NEW233 cannot be purchased twice");
        h.assertTrue(before.equals(f.ledger(p)), "preset editing and duplicate rejection change no exact ledger"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void final_shortage_lost_parent_and_late_native_gates_never_grant_or_refund(GameTestHelper h) {
        var f = new Fixture(h);
        for (String invalidation : List.of("battery", "parent", "level", "category", "config", "cancel")) {
            var p = f.player(); var s = AbilityStorage.get(p); f.parents(p); f.learn(p, HighPowerSkills.SCATTER_PLATES); f.deplete(p);
            var machine = f.normal(p, invalidation.equals("battery") ? COST - 1 : COST);
            h.assertTrue(f.start(p, ID), "valid finite Normal development begins before late " + invalidation);
            f.develop(p, TICKS - 1);
            near(h, machine.battery().getEnergy(), invalidation.equals("battery") ? 34 : 35, "146 native payments debit exactly 5110 IF");
            h.assertFalse(s.learned(ID), "no early grant before late " + invalidation);
            try {
                switch (invalidation) {
                    case "parent" -> s.unlearn(PARENT);
                    case "level" -> s.setLevel(2);
                    case "category" -> s.changeCategoryClassic("electromaster");
                    case "config" -> disable();
                    case "cancel" -> DevelopmentController.abort(p);
                }
                var beforeFinal = f.ledger(p); f.develop(p, 1);
                h.assertTrue(!s.learned(ID) && DevelopmentController.process(p).state() == DevelopmentProcess.State.FAILED,
                        "actual native completion refuses late " + invalidation);
                near(h, machine.battery().getEnergy(), invalidation.equals("battery") ? 34 : invalidation.equals("cancel") ? 35 : 0,
                        "short physical pull is atomic; spent work is never refunded for " + invalidation);
                h.assertTrue(beforeFinal.equals(f.ledger(p)), "failed final learning cannot repair or refill any shared ledger for " + invalidation);
            } finally { SkillCatalog.resetConfiguration(); }
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void native_save_category_switch_and_paid_relearning_preserve_exact_depleted_ledgers(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(); var s = AbilityStorage.get(p); f.parents(p);
        f.learn(p, HighPowerSkills.SCATTER_PLATES); f.learn(p, HighPowerSkills.THERMAL_SHELL);
        String[] prior = {HighPowerSkills.DUAL_POINT, PARENT, HighPowerSkills.SCATTER_PLATES, HighPowerSkills.THERMAL_SHELL};
        for (int slot = 0; slot < 4; slot++) h.assertTrue(wire(p, "preset_edit", "2:" + slot + ":" + prior[slot]), "existing MD canonical mapping binds");
        var oldSaved = AbilityStorage.encodeSaved(s); var oldLoaded = AbilityStorage.decodeSaved(oldSaved, s.cpDataConfig());
        h.assertTrue(!oldLoaded.learned(ID) && AbilityStorage.encodeSaved(oldLoaded).equals(oldSaved), "old native save receives no silent learning or rewritten mappings");
        f.learn(p, ID); f.deplete(p); var before = f.ledger(p);
        h.assertTrue(wire(p, "preset_edit", "3:3:" + ID), "retain canonical NEW233 native binding");
        AbilityStorage.save(p); var saved = p.saveWithoutId(new CompoundTag()); AbilityStorage.remove(p); p.load(saved); s = AbilityStorage.get(p);
        h.assertTrue(s.learned(ID) && s.exp(ID) == 0 && s.presets.skill(3, 3).equals(ID), "actual native entity save/load preserves full PointMeltdown identity");
        for (int slot = 0; slot < 4; slot++) h.assertTrue(s.presets.skill(2, slot).equals(prior[slot]), "native reload preserves prior MD mapping " + prior[slot]);
        h.assertTrue(before.equals(f.ledger(p)), "native reload preserves exact injury, shield shares, reserve, q/Q, CP and cooldown deadlines");
        for (String category : List.of("electromaster", CATEGORY, "teleporter", CATEGORY)) {
            s.changeCategoryClassic(category);
            h.assertTrue(s.experience.isEmpty(), "ordinary category change clears learned skills");
            for (int preset = 0; preset < 4; preset++) for (int slot = 0; slot < 4; slot++)
                h.assertTrue(s.presets.skill(preset, slot).isEmpty(), "ordinary category change clears every native mapping");
            if (category.equals(CATEGORY)) {
                var machine = f.normal(p, COST); h.assertFalse(f.start(p, ID), "returning to MD still requires exact BeamEtching relearning");
                near(h, machine.battery().getEnergy(), COST, "returning without parent spends no IF");
                f.parents(p); f.learn(p, HighPowerSkills.SCATTER_PLATES); f.learn(p, ID);
                h.assertTrue(wire(p, "preset_edit", "3:3:" + ID), "fully paid relearning restores normal binding eligibility");
            }
            var growth = HighPowerGrowth.values(p);
            h.assertTrue(growth.position().toPlainString().equals(before.activePosition())
                            && growth.earnedPosition().toPlainString().equals(before.earnedPosition()) && before.equals(f.ledger(p)),
                    "category change and full paid relearning preserve actual q/Q and all depleted shared pools");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void maximum_sequence_machine_codec_is_canonical_authenticated_and_finitely_paid(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(); f.parents(p); var machine = f.normal(p, COST);
        var request = new HighPowerLearningProtocol.Request(ID, Integer.MAX_VALUE);
        String full = MachineDeveloperSessions.activeToken(p).orElseThrow() + ":" + request.wire();
        h.assertTrue(request.wire().equals("point_meltdown:~zik0zj") && full.getBytes(StandardCharsets.UTF_8).length == 59
                        && HighPowerLearningProtocol.parse(request.wire()).equals(request),
                "full canonical NEW233 ID fits unchanged 64-byte machine protocol at maximum sequence");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess()); AcademyNetwork.Request decoded;
        try {
            AcademyNetwork.Request.CODEC.encode(buffer, new AcademyNetwork.Request("machine_hp_learn", full));
            decoded = AcademyNetwork.Request.CODEC.decode(buffer);
            h.assertTrue(buffer.readableBytes() == 0 && decoded.value().equals(full), "actual native codec consumes the entire authenticated request");
        } finally { buffer.release(); }
        h.assertFalse(MachineDeveloperSessions.request(p, new AcademyNetwork.Request("machine_hp_learn", UUID.randomUUID() + ":" + request.wire())),
                "foreign machine nonce cannot spend or begin learning");
        near(h, machine.battery().getEnergy(), COST, "foreign nonce preserves finite machine energy");
        h.assertTrue(MachineDeveloperSessions.request(p, decoded), "decoded sender-owned machine request starts real native development");
        f.develop(p, TICKS); near(h, machine.battery().getEnergy(), 0, "decoded request pays all 5145 finite IF");
        h.assertTrue(AbilityStorage.get(p).learned(ID) && HighPowerLearningProtocol.result(request, true, "").getString("skill").equals(ID),
                "learned identity and native feedback retain point_meltdown"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void disabled_configuration_and_missing_cp_preserve_saved_learning_and_exact_ledgers(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(); var s = AbilityStorage.get(p); f.parents(p); f.learn(p, HighPowerSkills.SCATTER_PLATES); f.deplete(p);
        var before = f.ledger(p); var machine = f.normal(p, COST);
        try {
            disable(); h.assertFalse(f.start(p, ID), "disabled NEW233 refuses learning before native payment");
            near(h, machine.battery().getEnergy(), COST, "disabled learning spends no IF");
            h.assertTrue(before.equals(f.ledger(p)), "config rejection changes no shared ledger");
        } finally { SkillCatalog.resetConfiguration(); }
        f.learn(p, ID); h.assertTrue(wire(p, "preset_edit", "3:3:" + ID), "paid canonical binding precedes live disable");
        var saved = AbilityStorage.encodeSaved(s);
        try {
            disable(); h.assertFalse(HighPowerSkills.eligible(s, ID), "live disable gates use of the saved learned skill");
            h.assertFalse(wire(p, "preset_edit", "0:0:" + ID), "disabled skill cannot gain a new mapping");
            h.assertTrue(AbilityStorage.encodeSaved(s).equals(saved), "disable preserves learned and preset identity without save rewriting");
            var snapshot = SkillCatalog.configurationSnapshot(); SkillCatalog.resetConfiguration();
            h.assertTrue(SkillCatalog.applyConfigurationSnapshot(snapshot) && !SkillCatalog.enabled(CATEGORY, ID), "existing bounded native config snapshot includes NEW233");
        } finally { SkillCatalog.resetConfiguration(); }
        p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).remove("cp"); s.unlearn(ID); var missingBudget = f.ledger(p);
        f.learn(p, ID);
        h.assertTrue(HighPowerResources.cp(p) == 0 && missingBudget.equals(f.ledger(p)), "paid relearning cannot initialize or refill missing CP in an existing ledger");
        s.unlearn(ID); p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("shield", "not-a-decimal");
        var corrupt = f.ledger(p); machine = f.normal(p, COST);
        h.assertFalse(f.start(p, ID), "corrupt existing exact combat ledger refuses compatibility ingress");
        near(h, machine.battery().getEnergy(), COST, "corrupt-ledger rejection spends no machine IF");
        h.assertTrue(corrupt.equals(f.ledger(p)) && !s.learned(ID), "corrupt ledger remains unchanged and cannot mint learning"); h.succeed();
    }

    private static void disable() { SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key(CATEGORY, ID), new ClassicSkillConfiguration.Flags(false, false))); }
    private static boolean wire(ServerPlayer player, String action, String value) { return AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request(action, value)); }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-7, why + ": " + actual + " != " + expected); }
    /** Category/level witness metadata may rebase, but actual q, earned Q and every stored balance must stay exact. */
    private record Ledger(CompoundTag combat, CompoundTag resources, String activePosition, String earnedPosition, double levelExperience, float nativeHealth) {}
    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(); final List<EmbeddedChannel> channels = new ArrayList<>();
        MachineDeveloperBlockEntity machine; int sequence; boolean closed;
        Fixture(GameTestHelper h) { this.h = h; h.testInfo.addListener(new GameTestListener() {
            public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
            public void testFailed(GameTestInfo i, GameTestRunner r) { close(); } public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
        }); }
        ServerPlayer player() {
            var server = h.getLevel().getServer(); UUID id = UUID.randomUUID(); var cookie = CommonListenerCookie.createInitial(new GameProfile(id, "PML" + id.toString().substring(0, 10)), false);
            var p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            var connection = new Connection(PacketFlow.SERVERBOUND); var channel = new EmbeddedChannel(connection); channels.add(channel);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie);
            channel.runPendingTasks(); channel.flushOutbound(); Object packet;
            while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet); }
            Vec3 at = h.absoluteVec(new Vec3(5.5, 2, 4.5)); p.moveTo(at.x, at.y, at.z, 0, 0); p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true);
            var s = AbilityStorage.get(p); s.selectCategory(CATEGORY); s.setLevel(3); s.activated = true; s.overloadFine = true; return p;
        }
        MachineDeveloperBlockEntity normal(ServerPlayer p, double amount) {
            BlockPos base = h.absolutePos(new BlockPos(12, 1, 14));
            if (machine == null) {
                var item = MachineDevelopers.NORMAL_ITEM.get(); var stack = new ItemStack(item); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
                h.assertTrue(item.place(new BlockPlaceContext(p, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(base), Direction.UP, base, false))).consumesAction(), "complete physical Normal developer placed");
                machine = (MachineDeveloperBlockEntity) h.getLevel().getBlockEntity(base);
            }
            machine.endUsers(); machine.battery().load(amount); p.moveTo(base.getX() + .5, base.getY(), base.getZ() - 1.5, 0, 0);
            h.assertTrue(machine.use(p), "actual sender-bound native Normal session opens"); return machine;
        }
        DeveloperItemEnergy portable(ServerPlayer p, double amount) {
            var stack = new ItemStack(AcademyCraft.DEVELOPER.get()); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var energy = new DeveloperItemEnergy(stack, DeveloperType.PORTABLE); energy.energy(amount); return energy;
        }
        boolean start(ServerPlayer p, String skill) {
            return MachineDeveloperSessions.request(p, new AcademyNetwork.Request("machine_hp_learn",
                    MachineDeveloperSessions.activeToken(p).orElseThrow() + ":" + new HighPowerLearningProtocol.Request(skill, ++sequence).wire()));
        }
        void develop(ServerPlayer p, int ticks) { for (int tick = 0; tick < ticks; tick++) DevelopmentController.tick(p, ignored -> {}); }
        void parents(ServerPlayer p) { learn(p, HighPowerSkills.DUAL_POINT); learn(p, PARENT); }
        void learn(ServerPlayer p, String id) {
            var s = AbilityStorage.get(p); var skill = SkillCatalog.find(s.category, id).orElseThrow();
            DeveloperType tier = DeveloperType.minimumForSkill(skill.level()); int stimulations = ClassicRules.learningStimulations(skill.level()); double cost = tier.actualConsumption(stimulations);
            if (tier == DeveloperType.PORTABLE) {
                var energy = portable(p, cost); h.assertTrue(DevelopmentController.startSkill(p, id), "actual finite portable learning starts for " + id);
                develop(p, stimulations * tier.ticksPerStimulation()); near(h, energy.energy(), 0, "every finite native prerequisite IF is paid for " + id);
            } else {
                h.assertTrue(tier == DeveloperType.NORMAL, "NEW233 fixture never substitutes an over-tier developer");
                var developer = normal(p, cost); h.assertTrue(start(p, id), "actual finite Normal-machine learning starts for " + id);
                develop(p, stimulations * tier.ticksPerStimulation()); near(h, developer.battery().getEnergy(), 0, "every finite native Normal IF is paid for " + id);
            }
            h.assertTrue(s.learned(id) && s.exp(id) == 0 && DevelopmentController.process(p).state() == DevelopmentProcess.State.DONE, "genuine completed native purchase grants only learned state for " + id);
        }
        void deplete(ServerPlayer p) {
            HighPowerGrowth.values(p); var plates = ScatterPlateStorage.read(p).orElseThrow(); var s = AbilityStorage.get(p); String category = s.category;
            BigDecimal amount = plates.plate(0).current().add(new BigDecimal("12.325")), shield = HighPowerCombat.shield(p);
            h.assertFalse(HighPowerCombat.spendShield(p, amount), "production MD cannot enter EM-only base shield debit");
            s.category = HighPowerCategories.ELECTROMASTER;
            try { h.assertTrue(HighPowerCombat.spendShield(p, amount), "declared depletion fixture uses the paired ordinary base debit"); }
            finally { s.category = category; }
            h.assertTrue(HighPowerCombat.shield(p).compareTo(shield.subtract(amount)) == 0 && ScatterPlateStorage.read(p).orElseThrow().plate(0).current().signum() == 0,
                    "fixture has exact depleted base shield and an exhausted independent plate");
            var combat = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
            combat.putString("health", CombatAmounts.decimalString(new BigDecimal(HighPowerCombat.maxHealthText(p)).divide(BigDecimal.TWO))); p.setHealth(10);
            var reserve = new CompoundTag(); reserve.putInt("version", MagneticDomainReserve.VERSION); reserve.putString("capacity", HighPowerCombat.maxShieldText(p));
            reserve.putString("lower", "3.125"); reserve.putString("upper", "5.25"); reserve.putString("amount", "8.375"); combat.put(MagneticDomainReserve.KEY, reserve);
            var resources = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY); resources.putDouble("cp", 37);
            resources.putLong("next_" + ID, p.serverLevel().getGameTime() + 500); resources.putLong("next_" + PARENT, p.serverLevel().getGameTime() + 500);
            HighPowerGrowth.values(p);
        }
        Ledger ledger(ServerPlayer p) {
            var combat = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy(); String q = combat.getCompound("activeGrowth").getString("position"), earned = combat.getString("growthPosition");
            combat.remove("activeGrowth");
            return new Ledger(combat, p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy(), q, earned, AbilityStorage.get(p).levelExperience, p.getHealth());
        }
        void close() {
            if (closed) return; closed = true; SkillCatalog.resetConfiguration(); if (machine != null) machine.endUsers();
            for (var p : players) { DevelopmentController.remove(p); MachineDeveloperSessions.close(p); AbilityStorage.remove(p); p.server.getPlayerList().remove(p); }
            channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }
}

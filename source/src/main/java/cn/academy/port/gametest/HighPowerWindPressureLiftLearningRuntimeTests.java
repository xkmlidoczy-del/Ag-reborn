package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.develop.*;
import cn.academy.port.highpower.*;
import cn.academy.port.machine.*;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
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

/** NEW443 real finite developer machines and authenticated machine wire. Enrollment,
 * placement/materials and accelerated DevelopmentController ticks are declared fixtures. */
@GameTestHolder("academy_wind_pressure_lift")
@PrefixGameTestTemplate(false)
public final class HighPowerWindPressureLiftLearningRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_wind_pressure_lift_learning", ID = "wind_pressure_lift";
    private HighPowerWindPressureLiftLearningRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void wind_lift_lv4_after_paid_442_requires_all_176_advanced_ticks_and_preserves_shared_ledgers(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(); var state = AbilityStorage.get(p);
        var skill = SkillCatalog.find(HighPowerCategories.VECMANIP, ID).orElseThrow();
        h.assertTrue(skill.level() == 4 && skill.requirements().equals(List.of(new SkillCatalog.Requirement(MultiAxisReflection.ID, 0)))
                && HighPowerSkills.WIND_PRESSURE_LIFT_PROPOSAL == 443, "canonical NEW443 is LV4 after learned NEW442 at zero mastery");
        h.assertTrue(ClassicRules.learningStimulations(4) == 11 && DeveloperType.ADVANCED.ticksPerStimulation() == 16
                && DeveloperType.ADVANCED.energyPerTick() == 40 && DeveloperType.ADVANCED.actualConsumption(11) == 7040,
                "independent native formula is 176 paid ticks and 7040 IF");
        var machine = f.machine(p, DeveloperType.ADVANCED, 7040);
        h.assertFalse(f.request(p, ID), "missing exact NEW442 parent refuses before machine payment"); near(h, machine.battery().getEnergy(), 7040, "missing parent retains finite battery");
        f.learn(p, MultiAxisReflection.ID);
        h.assertTrue(state.exp(MultiAxisReflection.ID) == 0, "genuinely paid native prerequisite has no invented mastery requirement");
        machine = f.machine(p, DeveloperType.ADVANCED, 7040); state.setLevel(3);
        h.assertFalse(f.request(p, ID), "learned parent cannot bypass integer LV4"); near(h, machine.battery().getEnergy(), 7040, "low integer level is free"); state.setLevel(4);
        machine = f.machine(p, DeveloperType.NORMAL, 7040);
        h.assertFalse(f.request(p, ID), "physical Normal developer cannot substitute for Advanced"); near(h, machine.battery().getEnergy(), 7040, "wrong native tier remains unspent");
        h.assertFalse(state.presets.edit(0, 0, ID, state::learned), "unlearned lift cannot bind");
        f.deplete(p); Ledger before = f.ledger(p); machine = f.machine(p, DeveloperType.ADVANCED, 7040);
        h.assertTrue(f.request(p, ID), "actual sender-bound Advanced session accepts canonical lift development"); f.develop(p, 175);
        h.assertFalse(state.learned(ID), "175 actual development ticks cannot grant early"); near(h, machine.battery().getEnergy(), 40, "exact final native payment remains");
        f.develop(p, 1); h.assertTrue(state.learned(ID) && state.exp(ID) == 0 && DevelopmentController.process(p).state() == DevelopmentProcess.State.DONE,
                "only the full 176 paid ticks grant the canonical learned bit"); near(h, machine.battery().getEnergy(), 0, "all 7040 finite IF was spent");
        h.assertTrue(before.equals(f.ledger(p)), "complete learning never refills HP, S, reserve, CP, growth or level XP");
        h.assertTrue(state.presets.edit(0, 0, ID, state::learned), "genuinely completed native learning binds its normal slot");
        h.assertTrue(SkillCatalog.ALL.size() == 50 && cn.academy.port.preset.PresetSkills.IMPLEMENTED.size() == 35,
                "new high-power learning does not enlarge the classic catalog or preset count"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void wind_lift_final_if_shortage_or_lost_parent_never_grants_or_refunds_shared_resources(GameTestHelper h) {
        Fixture f = new Fixture(h);
        for (boolean shortage : List.of(true, false)) {
            ServerPlayer p = f.player(); f.learn(p, MultiAxisReflection.ID); f.deplete(p); Ledger before = f.ledger(p);
            var machine = f.machine(p, DeveloperType.ADVANCED, shortage ? 7039 : 7040); h.assertTrue(f.request(p, ID), "valid initial Advanced learning before late failure"); f.develop(p, 175);
            near(h, machine.battery().getEnergy(), shortage ? 39 : 40, "first 175 native payments debit exactly 7000 IF");
            if (!shortage) AbilityStorage.get(p).unlearn(MultiAxisReflection.ID);
            f.develop(p, 1);
            h.assertFalse(AbilityStorage.get(p).learned(ID), "last-tick shortage or lost exact parent cannot grant");
            h.assertTrue(DevelopmentController.process(p).state() == DevelopmentProcess.State.FAILED, "actual native completion fails closed");
            near(h, machine.battery().getEnergy(), shortage ? 39 : 0, "short physical pull is atomic; a fully paid failed validation has no refund");
            h.assertTrue(before.equals(f.ledger(p)), "failed native development changes no exact combat or shared resource ledger");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void wind_lift_real_bounded_machine_codec_restores_canonical_skill_and_native_saved_binding(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(); f.learn(p, MultiAxisReflection.ID); var machine = f.machine(p, DeveloperType.ADVANCED, 7040);
        var request = new HighPowerLearningProtocol.Request(ID, Integer.MAX_VALUE);
        String token = MachineDeveloperSessions.activeToken(p).orElseThrow().toString(), full = token + ":" + request.wire();
        h.assertTrue(request.wire().equals("wpl:~zik0zj") && full.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 64
                && HighPowerLearningProtocol.parse(request.wire()).skill().equals(ID), "bounded machine-only alias decodes to the full canonical ID at maximum sequence");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess()); AcademyNetwork.Request decoded;
        try { AcademyNetwork.Request.CODEC.encode(buffer, new AcademyNetwork.Request("machine_hp_learn", full)); decoded = AcademyNetwork.Request.CODEC.decode(buffer);
            h.assertTrue(buffer.readableBytes() == 0 && decoded.value().equals(full), "actual native bounded request codec consumes and preserves the full authenticated value"); }
        finally { buffer.release(); }
        h.assertFalse(MachineDeveloperSessions.request(p, new AcademyNetwork.Request("machine_hp_learn", UUID.randomUUID() + ":" + request.wire())), "foreign machine nonce cannot start learning");
        h.assertTrue(MachineDeveloperSessions.request(p, decoded), "sender-owned decoded machine request is accepted"); f.develop(p, 176);
        h.assertTrue(machine.battery().getEnergy() == 0 && AbilityStorage.get(p).learned(ID), "decoded request completes real finite native Advanced development");
        h.assertTrue(AcademyGameplay.requestFromClient(p, new AcademyNetwork.Request("preset_edit", "3:3:" + ID)), "actual canonical preset request binds the learned full ID");
        AbilityStorage.save(p); CompoundTag saved = p.saveWithoutId(new CompoundTag()); AbilityStorage.remove(p); p.load(saved); var restored = AbilityStorage.get(p);
        h.assertTrue(restored.learned(ID) && restored.exp(ID) == 0 && restored.presets.skill(3, 3).equals(ID), "real native player NBT roundtrip retains full skill and binding identity");
        h.assertFalse(AbilityStorage.encodeSaved(restored).getCompound("skills").contains("wpl"), "transport alias never leaks into native learned save keys");
        h.assertTrue(HighPowerLearningProtocol.result(request, true, "").getString("skill").equals(ID), "native learning feedback names the canonical skill"); h.succeed();
    }

    private record Ledger(CompoundTag combat, CompoundTag resources, double experience) {}
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-7, why + ": " + actual + " != " + expected); }
    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(); final List<EmbeddedChannel> channels = new ArrayList<>();
        final Map<DeveloperType, MachineDeveloperBlockEntity> machines = new EnumMap<>(DeveloperType.class); int sequence; boolean closed;
        Fixture(GameTestHelper h) { this.h = h; h.testInfo.addListener(new GameTestListener() {
            public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
            public void testFailed(GameTestInfo i, GameTestRunner r) { close(); } public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
        }); }
        ServerPlayer player() {
            var server = h.getLevel().getServer(); UUID id = UUID.randomUUID(); var cookie = CommonListenerCookie.createInitial(new GameProfile(id, "WLL" + id.toString().substring(0, 10)), false);
            var p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            var connection = new Connection(PacketFlow.SERVERBOUND); var channel = new EmbeddedChannel(connection); channels.add(channel); NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie);
            channel.runPendingTasks(); channel.flushOutbound(); Object packet; while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet); }
            Vec3 at = h.absoluteVec(new Vec3(5.5, 2, 4.5)); p.moveTo(at.x, at.y, at.z, 0, 0); p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true);
            h.assertTrue(HighPowerCombat.enroll(p, 4), "declared first connected LV4 enrollment"); var state = AbilityStorage.get(p); state.selectCategory(HighPowerCategories.VECMANIP); state.setLevel(4); state.activated = true; state.overloadFine = true;
            HighPowerResources.initializeBudget(p); return p;
        }
        MachineDeveloperBlockEntity machine(ServerPlayer p, DeveloperType type, double energy) {
            BlockPos base = h.absolutePos(new BlockPos(type == DeveloperType.ADVANCED ? 21 : 12, 1, 14)); var machine = machines.get(type);
            if (machine == null) {
                var item = type == DeveloperType.ADVANCED ? MachineDevelopers.ADVANCED_ITEM.get() : MachineDevelopers.NORMAL_ITEM.get(); var stack = new ItemStack(item); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
                h.assertTrue(item.place(new BlockPlaceContext(p, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(base), Direction.UP, base, false))).consumesAction(), "complete physical " + type + " machine placed");
                machine = (MachineDeveloperBlockEntity) h.getLevel().getBlockEntity(base); machines.put(type, machine);
            }
            machine.endUsers(); machine.battery().load(energy); p.moveTo(base.getX() + .5, base.getY(), base.getZ() - 1.5, 0, 0);
            h.assertTrue(machine.use(p), "actual sender-bound native machine session opens"); return machine;
        }
        DeveloperItemEnergy portable(ServerPlayer p, double energy) { var stack = new ItemStack(AcademyCraft.DEVELOPER.get()); p.setItemInHand(InteractionHand.MAIN_HAND, stack); var item = new DeveloperItemEnergy(stack, DeveloperType.PORTABLE); item.energy(energy); return item; }
        boolean request(ServerPlayer p, String skill) { return MachineDeveloperSessions.request(p, new AcademyNetwork.Request("machine_hp_learn", MachineDeveloperSessions.activeToken(p).orElseThrow() + ":" + new HighPowerLearningProtocol.Request(skill, ++sequence).wire())); }
        void develop(ServerPlayer p, int ticks) { for (int tick = 0; tick < ticks; tick++) DevelopmentController.tick(p, ignored -> {}); }
        void learn(ServerPlayer p, String id) {
            var state = AbilityStorage.get(p); if (state.learned(id)) return; var skill = SkillCatalog.find(state.category, id).orElseThrow();
            for (var parent : skill.requirements()) learn(p, parent.id());
            DeveloperType tier = DeveloperType.minimumForSkill(skill.level()); int stimulations = ClassicRules.learningStimulations(skill.level()); double energy = tier.actualConsumption(stimulations);
            if (tier == DeveloperType.PORTABLE) { var battery = portable(p, energy); h.assertTrue(DevelopmentController.startSkill(p, id), "actual portable prerequisite learning starts for " + id); develop(p, stimulations * tier.ticksPerStimulation()); near(h, battery.energy(), 0, "prerequisite consumes every finite portable IF"); }
            else { var physical = machine(p, tier, energy); h.assertTrue(request(p, id), "actual machine prerequisite learning starts for " + id); develop(p, stimulations * tier.ticksPerStimulation()); near(h, physical.battery().getEnergy(), 0, "prerequisite consumes every finite machine IF"); }
            h.assertTrue(state.learned(id) && state.exp(id) == 0 && DevelopmentController.process(p).state() == DevelopmentProcess.State.DONE, "genuine completed prerequisite has a learned bit and no mastery");
        }
        void deplete(ServerPlayer p) {
            HighPowerGrowth.values(p); var combat = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY); combat.putString("health", "17.25"); combat.putString("shield", "9.125");
            CompoundTag reserve = new CompoundTag(); reserve.putInt("version", MagneticDomainReserve.VERSION); reserve.putString("capacity", HighPowerCombat.maxShieldText(p)); reserve.putString("lower", "3"); reserve.putString("upper", "5"); reserve.putString("amount", "8"); combat.put(MagneticDomainReserve.KEY, reserve);
            p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", 37);
        }
        Ledger ledger(ServerPlayer p) { return new Ledger(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy(), p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy(), AbilityStorage.get(p).levelExperience); }
        void close() { if (closed) return; closed = true; machines.values().forEach(MachineDeveloperBlockEntity::endUsers);
            for (var p : players) { DevelopmentController.remove(p); MachineDeveloperSessions.close(p); AbilityStorage.remove(p); p.server.getPlayerList().remove(p); }
            channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }
}

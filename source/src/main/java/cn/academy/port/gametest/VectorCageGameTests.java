package cn.academy.port.gametest;

import cn.academy.port.highpower.core.VectorCageRules;
import cn.academy.port.*;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.develop.*;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.machine.*;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW441 native controls. Enrollment, fixed poses and finite developer acceleration are fixtures.
 * Activation, four moving-plane contacts, hurt, effects and payment aging use ordinary entry points
 * and actual native ticks. Native callbacks queue terminal results for GameTestInfo's own tick. */
@GameTestHolder("academy_vector_cage_runtime")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class VectorCageGameTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_vector_cage";
    private static final String ID = "vector_cage", SKILL = "vecmanip.vector_cage";
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.vector_cage_native_test");
    private static int opened, closed;
    private VectorCageGameTests() {}

    @BeforeBatch(batch=BATCH) public static void begin(ServerLevel level) { opened = closed = 0; LOG.info("NEW441 native batch started"); }
    @AfterBatch(batch=BATCH) public static void end(ServerLevel level) { LOG.info("NEW441 native batch terminal callback: opened={} closed={}", opened, closed); }
    @SubscribeEvent public static void template(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int dimension : new int[]{64, 16, 64}) size.add(IntTag.valueOf(dimension));
        for (int axis = 0; axis < 3; axis++) position.add(IntTag.valueOf(0));
        cell.put("pos", position); cell.putInt("state", 0); blocks.add(cell); air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_vector_cage_runtime", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void lv4_exact_431_parent_and_finite_advanced_learning_preserve_zero_xp_and_resources(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(28, 12, 4, false);
        f.ready(() -> {
            var state = AbilityStorage.get(p); var skill = SkillCatalog.find(HighPowerCategories.VECMANIP, ID).orElseThrow();
            h.assertTrue(skill.level() == 4 && skill.requirements().equals(List.of(new SkillCatalog.Requirement(TrajectoryReturn.ID, 0))), "NEW441 is LV4 and requires exactly NEW431 at zero mastery");
            state.unlearn(TrajectoryReturn.ID); int stimulations = ClassicRules.learningStimulations(4);
            double price = DeveloperType.ADVANCED.actualConsumption(stimulations); int ticks = DeveloperType.ADVANCED.ticksPerStimulation() * stimulations;
            near(h, price, 7040, "eleven finite Advanced stimulations cost exactly 7040 IF"); h.assertTrue(ticks == 176, "Advanced learning requires all 176 paid native development ticks");
            MachineDeveloperBlockEntity machine = f.advanced(p, price);
            h.assertFalse(f.learnRequest(p, ID), "another Vector branch cannot substitute for 431"); near(h, machine.battery().getEnergy(), price, "missing parent is free");
            f.learn(p, TrajectoryReturn.ID); h.assertTrue(state.exp(TrajectoryReturn.ID) == 0, "genuinely paid prerequisite retains zero mastery");
            state.setLevel(3); machine = f.advanced(p, price); h.assertFalse(f.learnRequest(p, ID), "integer LV4 remains required after paying for parent");
            state.setLevel(4); h.assertFalse(state.presets.edit(0, 0, ID, state::learned), "unlearned cage cannot bind");
            machine = f.normal(p, price); h.assertFalse(f.learnRequest(p, ID), "finite Normal machine cannot learn an LV4 skill"); near(h, machine.battery().getEnergy(), price, "insufficient developer tier consumes no IF");
            Ledger before = new Ledger(p); CompoundTag budget = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy();
            machine = f.advanced(p, price); h.assertTrue(f.learnRequest(p, ID), "ordinary finite Advanced machine starts cage learning");
            f.develop(p, ticks - 1); h.assertFalse(state.learned(ID), "last finite IF tick is mandatory");
            near(h, machine.battery().getEnergy(), DeveloperType.ADVANCED.energyPerTick(), "one actual payment remains");
            f.develop(p, 1); h.assertTrue(state.learned(ID) && state.exp(ID) == 0, "last genuine tick completes learning with zero mastery");
            near(h, machine.battery().getEnergy(), 0, "finite battery is exhausted exactly"); before.unchanged(h, p);
            h.assertTrue(budget.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)), "learning cannot refill CP or rewrite cooldowns");
            h.assertTrue(state.presets.edit(0, 0, ID, state::learned), "newly learned skill binds through ordinary preset validation");
            CompoundTag saved = AbilityStorage.encodeSaved(state);
            h.assertTrue(saved.equals(AbilityStorage.encodeSaved(AbilityStorage.decodeSaved(saved, state.cpDataConfig()))), "native codec preserves learned mappings"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=120)
    public static void empty_mark_first_release_and_long_hold_clear_are_free(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            double cp = HighPowerResources.cp(p); Ledger ledger = new Ledger(p);
            h.assertTrue(f.shortPress(p) && VectorCage.preview(p) && !VectorCage.active(p), "first short press marks only a preview");
            near(h, HighPowerResources.cp(p), cp, "mark is free");
            f.after(p, 5, () -> {
                cpEqual(h, p, () -> h.assertFalse(f.shortPress(p), "empty confirmation cannot buy an active cage"));
                h.assertFalse(VectorCage.active(p), "empty scan remains inactive");
                f.after(p, 5, () -> {
                    if (!VectorCage.preview(p)) h.assertTrue(f.shortPress(p), "fresh empty preview can be marked after the request fence");
                    String held = f.press(p); double before = HighPowerResources.cp(p);
                    f.after(p, 6, () -> {
                        f.release(p, held);
                        h.assertFalse(VectorCage.preview(p) || VectorCage.active(p), "long hold clears without confirming");
                        h.assertTrue(HighPowerResources.cp(p) >= before, "hold has no CP debit"); ledger.unchanged(h, p); f.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void zero_cp_confirmation_and_forged_actor_epoch_or_slot_are_free(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { long epoch = LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p));
            cpEqual(h, p, () -> {
                h.assertFalse(f.request(p, "vector_cage_start", new HighPowerInput.Input(0, 1, p.getId() + 1, epoch).wire()), "forged actor cannot arm a hold");
                h.assertFalse(f.request(p, "vector_cage_start", new HighPowerInput.Input(0, 2, p.getId(), epoch + 1).wire()), "forged epoch cannot arm a hold");
                h.assertFalse(f.request(p, "vector_cage_start", new HighPowerInput.Input(1, 3, p.getId(), epoch).wire()), "unmapped slot cannot arm a hold");
            });
            f.inputs.put(p, 3L); f.mark(p, false); Zombie target = f.target(f.center(p).add(3.7, .1, 0), true);
            f.after(p, 5, () -> { f.cp(p, 0); cpEqual(h, p, () -> h.assertFalse(f.shortPress(p), "valid hostile cannot justify an unpaid zero-CP field"));
                h.assertFalse(VectorCage.active(p), "zero CP creates no active damage context"); eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "unpaid target receives no damage"); f.succeed(); }); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void rejected_newer_authenticated_start_retires_old_pending_release(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { String old = f.press(p); long next = f.inputs.get(p) + 1;
            String rejected = new HighPowerInput.Input(1, next, p.getId(), LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p))).wire();
            f.inputs.put(p, next); cpEqual(h, p, () -> {
                h.assertFalse(f.request(p, "vector_cage_start", rejected), "newer authenticated request to unbound slot is rejected");
                h.assertFalse(f.release(p, old), "old release cannot regain authority after a newer rejected authenticated request");
            }); h.assertFalse(VectorCage.preview(p) || VectorCage.active(p), "stale release cannot create a preview"); f.succeed(); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void preview_abort_during_confirmation_hold_cannot_revive_preview_on_late_release(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { f.mark(p, false); f.after(p, 5, () -> { String held = f.press(p); VectorCage.abort(p);
            cpEqual(h, p, () -> h.assertFalse(f.release(p, held), "late release cannot revive its aborted captured preview"));
            h.assertFalse(VectorCage.preview(p) || VectorCage.active(p), "aborted preview remains terminal"); f.succeed(); }); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=330)
    public static void preview_expires_after_its_real_tick_deadline_without_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            h.assertTrue(f.shortPress(p), "ordinary first release marks preview"); Ledger ledger = new Ledger(p);
            f.after(p, VectorCageRules.PREVIEW_TICKS - 1, () -> h.assertTrue(VectorCage.preview(p), "preview survives its last physical tick"));
            f.after(p, VectorCageRules.PREVIEW_TICKS + 1, () -> { h.assertFalse(VectorCage.preview(p) || VectorCage.active(p), "preview expires at its fixed real-tick deadline"); h.assertTrue(VectorCage.cooldownDebt(p) == 0, "free preview creates no paid debt"); ledger.unchanged(h, p); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=105)
    public static void five_actual_q_anchors_pay_one_whole_cast_cost_and_frozen_cooldown(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> actors = new ArrayList<>();
        for (int q = 1; q <= 5; q++) actors.add(f.player(6 + (q - 1) * 12, 12, q, true));
        f.ready(() -> {
            double[] costs = {24, 12, 6, 2, .1}; int[] cooldowns = {40, 40, 40, 40, 4}; int[] finished = {0};
            for (int i = 0; i < actors.size(); i++) {
                ServerPlayer p = actors.get(i); int index = i;
                eq(h, HighPowerGrowth.values(p).position(), BigDecimal.valueOf(i + 1), "native active coordinate retains declared rollback anchor");
                f.mark(p, false); Zombie target = f.target(f.center(p).add(3.7, .1, 0), true); Ledger ledger = new Ledger(p);
                f.after(p, 5, () -> {
                    double cp = HighPowerResources.cp(p); h.assertTrue(f.shortPress(p), "one actual marked hostile confirms at q " + (index + 1));
                    near(h, HighPowerResources.cp(p), cp - costs[index], "whole 4-pulse cast pays once at each q anchor");
                    h.assertTrue(VectorCage.cooldownDebt(p) == cooldowns[index], "payment writes the approved physical debt");
                    f.after(p, 8, () -> { h.assertTrue(hp(target).compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth()) < 0, "each anchor uses actual native damage"); ledger.unchanged(h, p); if (++finished[0] == actors.size()) f.succeed(); });
                });
            }
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=105)
    public static void raw_candidate_overflow_is_free(GameTestHelper h) { populationRefusal(h, true); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=105)
    public static void ninth_hostile_is_excluded_from_frozen_eight_target_budget(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { f.mark(p, false); Vec3 c = f.center(p); List<Zombie> admitted = new ArrayList<>();
            for (int i = 0; i < 8; i++) admitted.add(f.target(c.add(-2.8 + i * .8, .1, 0), true));
            Zombie ninth = f.target(c.add(3.7, .1, 3.7), true);
            f.confirm(p, () -> { h.assertTrue(VectorCage.targetCount(p) == 8, "nearest eight native hostiles are frozen deterministically");
                f.after(p, 12, () -> { eq(h, hp(ninth), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "ninth original candidate cannot steal an admitted share");
                    BigDecimal total = admitted.stream().map(t -> cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(t))).reduce(BigDecimal.ZERO, BigDecimal::add);
                    eq(h, total, HighPowerGrowth.values(p).damage().multiply(n(".625")), "eight first-pulse contacts still share only one quarter of whole cast"); f.succeed(); }); }); });
    }
    private static void populationRefusal(GameTestHelper h, boolean raw) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            f.mark(p, false); Vec3 c = f.center(p); List<Zombie> targets = new ArrayList<>();
            int count = raw ? 1 : 9; for (int i = 0; i < count; i++) targets.add(f.target(c.add(-3 + i * .6, .1, 0), true));
            if (raw) for (int i = 0; i < 64; i++) { ArmorStand stand = new ArmorStand(h.getLevel(), c.x + i % 8 * .05, c.y + .1, c.z + i / 8 * .05); stand.setNoGravity(true); f.add(stand); }
            f.after(p, 5, () -> { cpEqual(h, p, () -> h.assertFalse(f.shortPress(p), "complete overflowing scan fails closed before payment"));
                h.assertFalse(VectorCage.active(p), "overflow cannot retain an arbitrary prefix"); for (Zombie target : targets) eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "refusal leaves every real hostile untouched"); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=105)
    public static void players_team_members_unknown_classes_and_invulnerable_bodies_cannot_supply_a_target(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(), other = f.player(52, 12, 4, true);
        f.ready(() -> {
            f.mark(p, false); Vec3 c = f.center(p); other.setPos(c.add(0, .1, 0));
            Zombie teammate = f.target(c.add(1, .1, 0), true), immune = f.target(c.add(-1, .1, 0), true); immune.setInvulnerable(true);
            var board = h.getLevel().getScoreboard(); var team = board.addPlayerTeam("vc" + p.getId()); f.teams.add(team);
            board.addPlayerToTeam(p.getScoreboardName(), team); board.addPlayerToTeam(teammate.getScoreboardName(), team);
            Zombie unknown = new Zombie(EntityType.ZOMBIE, h.getLevel()) {}; f.prepare(unknown, c.add(0, .1, 1));
            f.after(p, 5, () -> { cpEqual(h, p, () -> h.assertFalse(f.shortPress(p), "no supported nonallied damageable hostile was admitted"));
                h.assertFalse(VectorCage.active(p), "excluded bodies cannot justify payment"); eq(h, hp(teammate), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "ally cannot be harmed"); eq(h, hp(immune), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "immune target stays immune"); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=170)
    public static void only_real_moving_planes_hit_and_four_original_pulse_shares_conserve_two_point_five_d(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            f.mark(p, false); Vec3 c = f.center(p); Zombie edge = f.target(c.add(3.7, .1, 0), true), interior = f.target(c.add(0, .1, 0), true);
            Observation edgeEvents = f.observe(edge), innerEvents = f.observe(interior); BigDecimal whole = HighPowerGrowth.values(p).damage().multiply(n("2.5")), share = whole.divide(n("8")); Ledger ledger = new Ledger(p);
            f.confirm(p, () -> {
                h.assertTrue(VectorCage.targetCount(p) == 2, "two real hostile identities are frozen once");
                f.after(p, 5, () -> { eq(h, hp(edge), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "warmup has no plane damage"); eq(h, hp(interior), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "interior does not create a volume hit"); });
                f.after(p, 7, () -> { eq(h, hp(edge), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(share), "edge intersects first actual moving face"); eq(h, hp(interior), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "interior still does not intersect that face"); });
                f.after(p, 12, () -> { eq(h, hp(interior), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(share), "late first sweep reaches the actual inner AABB"); h.assertTrue(edgeEvents.incoming == 1 && innerEvents.incoming == 1, "each frozen body attempts once in one sweep"); });
                f.after(p, 73, () -> {
                    eq(h, hp(edge), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(whole.divide(n("2"))), "edge keeps exactly four original shares");
                    eq(h, hp(interior), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(whole.divide(n("2"))), "inside body also keeps exactly four original shares");
                    h.assertTrue(edgeEvents.incoming == 4 && innerEvents.incoming == 4, "all four native pulse windows fire exactly once per target");
                    for (Observation observation : List.of(edgeEvents, innerEvents)) for (int i = 1; i < observation.ticks.size(); i++) h.assertTrue(observation.ticks.get(i) - observation.ticks.get(i - 1) >= 20, "actual target attempts remain at least twenty physical ticks apart");
                    ledger.unchanged(h, p);
                });
                f.after(p, 81, () -> { h.assertFalse(VectorCage.active(p), "paid field ends after eighty native ticks"); f.succeed(); });
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void exact_outer_face_touch_is_admitted_but_one_ulp_outside_gets_no_damage(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { f.mark(p, false); Vec3 c = f.center(p);
            Zombie touching = f.target(c.add(-4.3, .1, 0), true);
            touching.setPos(c.x - 4 - touching.getBbWidth() / 2.0, c.y + .1, c.z);
            h.assertTrue(touching.getBoundingBox().maxX == c.x - 4, "real native width puts exact AABB edge on the finite outer wall");
            Zombie outside = f.target(new Vec3(Math.nextDown(touching.getX()), touching.getY(), touching.getZ()), true);
            h.assertTrue(outside.getBoundingBox().maxX < c.x - 4, "separate ordinary body is a real one-ULP miss"); Observation hit = f.observe(touching);
            f.confirm(p, () -> { h.assertTrue(VectorCage.targetCount(p) == 1, "broad-phase admits the exact closed edge without widening hostile selection");
                f.after(p, 8, () -> { h.assertTrue(hit.incoming == 1 && hp(touching).compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth()) < 0, "closed outer face contact produces one genuine native hit");
                    eq(h, hp(outside), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "one-ULP outside body has no real plane contact"); f.succeed(); }); }); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=170)
    public static void frozen_target_misses_lose_their_share_without_retargeting_or_redistribution(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            f.mark(p, false); Vec3 c = f.center(p); Zombie first = f.target(c.add(-3.7, .1, 0), true), missed = f.target(c.add(3.7, .1, 0), true);
            BigDecimal expected = HighPowerGrowth.values(p).damage().multiply(n("1.25"));
            f.confirm(p, () -> {
                missed.setPos(c.add(12, .1, 0)); Zombie newcomer = f.target(c.add(3.7, .1, 0), true);
                f.after(p, 73, () -> { eq(h, hp(first), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(expected), "surviving target never receives missing frozen shares"); eq(h, hp(missed), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "body outside finite faces misses its original allocation"); eq(h, hp(newcomer), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "late newcomer cannot replace a frozen identity"); f.succeed(); });
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void simultaneous_corner_contact_has_one_native_hurt_and_fixed_negative_x_tie(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            f.mark(p, false); Vec3 c = f.center(p); Zombie target = f.target(c.add(-3.7, .1, -3.7), true); Observation seen = f.observe(target);
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); f.discount(target);
            f.confirm(p, () -> f.after(p, 7, () -> { h.assertTrue(seen.incoming == 1 && seen.knockbacks == 1, "two crossing walls still own exactly one hurt and one impulse");
                h.assertTrue(seen.source != null && seen.source.getSourcePosition().x < target.getX(), "deterministic -X face owns exact corner tie");
                near(h, seen.originalRatioX, -1, "native inward ratio uses selected negative-X wall"); near(h, seen.originalRatioZ, 0, "corner has no additional Z impulse"); assertSource(h, seen.source, p); f.succeed(); }));
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void whole_volume_wall_mutation_before_confirm_is_free(GameTestHelper h) { terrainMutation(h, false); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void whole_volume_wall_mutation_after_payment_closes_field_without_refund(GameTestHelper h) { terrainMutation(h, true); }
    private static void terrainMutation(GameTestHelper h, boolean paid) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            f.mark(p, false); Vec3 c = f.center(p); Zombie target = f.target(c.add(3.7, .1, 0), true);
            // This block is away from the first moving face and the owner-to-target ray.
            BlockPos obstacle = BlockPos.containing(c.add(-1, 2.5, 2));
            if (!paid) h.getLevel().setBlockAndUpdate(obstacle, Blocks.STONE.defaultBlockState());
            f.after(p, 5, () -> {
                double cp = HighPowerResources.cp(p); h.assertTrue(f.shortPress(p) == paid, "whole immutable volume is checked at confirmation");
                near(h, HighPowerResources.cp(p), cp - (paid ? 2 : 0), "only a confirmed field pays");
                if (paid) h.getLevel().setBlockAndUpdate(obstacle, Blocks.STONE.defaultBlockState());
                f.after(p, 8, () -> { eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "hidden whole-volume obstruction revokes all later plane authority"); h.assertFalse(VectorCage.active(p), "invalid geometry cannot retain active field"); h.assertTrue(h.getLevel().getBlockState(obstacle).is(Blocks.STONE), "cage never destroys obstruction"); f.succeed(); });
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void context_sensitive_scaffolding_refuses_even_when_native_collider_is_empty(GameTestHelper h) { contextSensitiveTerrain(h, false); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void context_sensitive_powder_snow_refuses_before_and_after_leather_boots(GameTestHelper h) { contextSensitiveTerrain(h, true); }
    private static void contextSensitiveTerrain(GameTestHelper h, boolean powder) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            Vec3 eye = p.getEyePosition(); BlockHitResult floor = h.getLevel().clip(new net.minecraft.world.level.ClipContext(eye,
                    eye.add(p.getLookAngle().scale(56)), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.ANY, p));
            h.assertTrue(floor.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && floor.getDirection() == Direction.UP, "independent native ray identifies the ordinary support top face");
            BlockPos cell = BlockPos.containing(floor.getLocation().add(-2, 1, 2));
            var state = (powder ? Blocks.POWDER_SNOW : Blocks.SCAFFOLDING).defaultBlockState();
            if (!powder) {
                state = state.setValue(net.minecraft.world.level.block.ScaffoldingBlock.DISTANCE, 0);
                h.getLevel().setBlockAndUpdate(cell.below(), state);
            }
            h.getLevel().setBlockAndUpdate(cell, state);
            h.assertTrue(state.getCollisionShape(h.getLevel(), cell, net.minecraft.world.phys.shapes.CollisionContext.of(p)).isEmpty(), "real context-sensitive vanilla block currently reports an empty collider");
            cpEqual(h, p, () -> h.assertFalse(f.shortPress(p), "context-dependent collider cannot acquire immutable whole-volume authority"));
            h.assertFalse(VectorCage.preview(p) || VectorCage.active(p), "unsealed native geometry produces no preview");
            f.after(p, 5, () -> {
                if (powder) p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
                else { p.setShiftKeyDown(true); p.setPose(Pose.CROUCHING); }
                cpEqual(h, p, () -> h.assertFalse(f.shortPress(p), "changed boots or posture cannot convert context-sensitive terrain into a frozen cage"));
                h.assertTrue(h.getLevel().getBlockState(cell).is(powder ? Blocks.POWDER_SNOW : Blocks.SCAFFOLDING), "refusal preserves the actual world block"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void moving_owner_out_of_all_vertex_range_revokes_frozen_field(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { f.mark(p, false); Vec3 c = f.center(p); Zombie target = f.target(c.add(3.7, .1, 0), true);
            f.confirm(p, () -> { p.setPos(c.add(55, 0, 0)); f.after(p, 8, () -> { h.assertFalse(VectorCage.active(p), "center within 56 cannot excuse far-side face vertices beyond 56"); eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "lost current-owner range refuses damage"); f.succeed(); }); }); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=120)
    public static void native_canceled_attempt_cannot_repeat_within_sweep_or_in_another_casters_same_tick(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(), other = f.player(28, 12, 4, true);
        f.ready(() -> {
            f.mark(p, false); f.mark(other, false); vector(h, f.center(p), f.center(other), "both native top-face marks describe the same actual field");
            Zombie target = f.target(f.center(p).add(3.7, .1, 0), true); Observation observation = f.observe(target);
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) { if (e.getEntity() == target) e.setCanceled(true); } });
            f.after(other, 5, () -> { h.assertTrue(f.shortPress(p) && f.shortPress(other), "two owners independently pay after both completed five actual player Posts");
                f.after(p, 12, () -> { h.assertTrue(observation.incoming == 1, "canceled first attempt still consumes one target fence and cross-caster tick gate"); eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "native cancellation grants no damage"); f.succeed(); }); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=120)
    public static void native_iframe_is_preserved_and_rejected_share_is_not_retried(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { f.mark(p, false); Zombie target = f.target(f.center(p).add(3.7, .1, 0), false); Observation observation = f.observe(target); f.discount(target);
            f.confirm(p, () -> { h.assertTrue(target.hurt(p.damageSources().playerAttack(p), 8), "genuine stronger ordinary attack establishes native iframe"); float before = target.getHealth(); int initial = observation.incoming;
                f.after(p, 12, () -> { near(h, target.getHealth(), before, "lower cage share cannot bypass ordinary iframe"); h.assertTrue(observation.incoming <= initial + 1 && observation.posts == 1, "failed pulse is not repeatedly issued until iframe opens"); f.succeed(); }); }); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void native_armor_effect_enchantment_and_absorption_match_ordinary_control(GameTestHelper h) { defenseControl(h, 0); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void native_full_shield_uses_actual_wall_origin_and_preserves_ordinary_block(GameTestHelper h) { defenseControl(h, 1); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void native_partial_shield_matches_ordinary_block_without_new_bypass(GameTestHelper h) { defenseControl(h, 2); }
    private static void defenseControl(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(), controlCaster = f.player(40, 12, 4, true);
        f.ready(() -> {
            f.mark(p, mode != 0); p.setShiftKeyDown(false); Vec3 c = f.center(p);
            Zombie target = f.target(c.add(3.7, .1, 0), false), control = f.target(c.add(15.7, .1, 0), false); f.discount(target);
            if (mode == 0) { defenses(h, target); defenses(h, control); } else {
                shield(target, -90); shield(control, -90);
                for (Zombie body : List.of(target, control)) body.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
                h.assertFalse(target.isDamageSourceBlocked(p.damageSources().playerAttack(p)), "owner itself is behind the shield; only real wall origin is frontal");
            }
            Observation actual = f.observe(target), ordinary = f.observe(control); boolean[] compared = {false};
            if (mode == 2) f.hook(new Object() { @SubscribeEvent public void block(LivingShieldBlockEvent e) { if (e.getEntity() == target || e.getEntity() == control) e.setBlockedDamage(2); } });
            f.hook(new Object() { @SubscribeEvent(priority=EventPriority.LOWEST) public void end(ServerTickEvent.Post event) {
                if (event.getServer() != p.server || actual.incoming == 0 || compared[0]) return; compared[0] = true;
                try {
                    Vec3 actualMotion = target.getDeltaMovement(), actualCasterMotion = p.getDeltaMovement();
                    Vec3 translated = actual.source.getSourcePosition().add(control.position().subtract(target.position()));
                    boolean accepted = control.hurt(new DamageSource(controlCaster.damageSources().playerAttack(controlCaster).typeHolder(), controlCaster, controlCaster, translated), 5);
                    h.assertTrue(accepted == (mode != 1), "ordinary control exercises intended full/partial defense");
                    near(h, target.getHealth(), control.getHealth(), "native HP residual equals independent ordinary control");
                    vector(h, actualMotion, control.getDeltaMovement(), "native shield's early .5 and later .4 calls retain ordinary motion");
                    vector(h, actualCasterMotion, controlCaster.getDeltaMovement(), "native shield reaction leaves caster motion equal to translated control");
                    h.assertTrue(actual.strengths.equals(ordinary.strengths), "same native knockback calls and strengths, with no added cage impulse");
                } catch (Throwable failure) { f.fail(failure); }
            } });
            f.confirm(p, () -> f.after(p, 7, () -> {
                h.assertTrue(compared[0] && actual.source != null, "real wall collision and independent native control both completed");
                h.assertTrue(actual.posts == ordinary.posts && actual.armor == ordinary.armor && actual.effects == ordinary.effects && actual.enchantments == ordinary.enchantments && actual.absorption == ordinary.absorption, "every native mitigation stage agrees with ordinary player attack");
                if (mode == 0) h.assertTrue(actual.armor > 0 && actual.effects > 0 && actual.enchantments > 0 && actual.absorption == .5F, "control genuinely exercised all reducers");
                else {
                    h.assertTrue(actual.blocked && ordinary.blocked && actual.blockedDamage == ordinary.blockedDamage, "actual source side preserves full or partial shield block");
                    h.assertFalse(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "native full/partial shield branch receives no additional low-mode control");
                    h.assertTrue(VectorCage.receipts(p).stream().allMatch(r -> r.velocityDelta().equals(Vec3.ZERO)), "shield branch cannot claim inward-control receipt");
                }
                assertSource(h, actual.source, p); f.succeed();
            }));
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void native_knockback_only_redirects_direction_and_keeps_ordinary_strength_and_y(GameTestHelper h) { knockbackControl(h, 0); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void full_knockback_resistance_adds_no_manual_impulse(GameTestHelper h) { knockbackControl(h, 1); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void canceled_native_knockback_keeps_external_motion_without_second_push(GameTestHelper h) { knockbackControl(h, 2); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void exactly_centered_first_face_redirects_its_existing_zero_ratio_native_call(GameTestHelper h) { knockbackControl(h, 3); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void nested_native_knockback_keeps_only_ordinary_nested_motion(GameTestHelper h) { nestedKnockback(h, false); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void native_knockback_ratio_reversal_cannot_gain_outward_force(GameTestHelper h) { nestedKnockback(h, true); }
    private static void nestedKnockback(GameTestHelper h, boolean reverse) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { f.mark(p, false); Vec3 c = f.center(p); Zombie target = f.target(c.add(3.7, .1, 0), false), control = f.target(c.add(15.7, .1, 0), false);
            f.discount(target); target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); control.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            boolean[] called = {false}, compared = {false};
            f.hook(new Object() {
                @SubscribeEvent public void nativeCall(LivingKnockBackEvent e) {
                    if (e.getEntity() != target || called[0]) return; called[0] = true;
                    if (reverse) e.setRatioX(-e.getRatioX());
                    else { target.knockback(.2, 0, -1); control.knockback(.2, 0, -1); }
                }
                @SubscribeEvent(priority=EventPriority.LOWEST) public void end(ServerTickEvent.Post e) {
                    if (e.getServer() != p.server || !called[0] || compared[0]) return; compared[0] = true;
                    try { vector(h, target.getDeltaMovement(), reverse ? Vec3.ZERO : control.getDeltaMovement(), "invalid outer redirect adds no force beyond independent ordinary nested control"); }
                    catch (Throwable failure) { f.fail(failure); }
                }
            });
            f.confirm(p, () -> f.after(p, 8, () -> { h.assertTrue(called[0] && compared[0], "real hurt-owned native knockback reached adversarial callback"); near(h, target.getHealth(), 195, "accepted original damage is neither repeated nor refunded by impulse mutation"); f.succeed(); })); });
    }
    private static void knockbackControl(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            f.mark(p, false); Vec3 c = f.center(p); double offset = mode == 3 ? -4 : 3.7; Zombie target = f.target(c.add(offset, .1, 0), false), control = f.target(c.add(offset + 12, .1, 0), false); f.discount(target);
            for (Zombie body : List.of(target, control)) body.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(mode == 1 ? 1 : 0);
            Observation actual = f.observe(target), ordinary = f.observe(control); Vec3[] outcome = {null};
            f.hook(new Object() {
                @SubscribeEvent public void knockback(LivingKnockBackEvent e) {
                    if (e.getEntity() == control) { e.setRatioX(mode == 3 ? -1 : 1); e.setRatioZ(0); }
                    if (mode == 2 && (e.getEntity() == target || e.getEntity() == control)) { e.getEntity().setDeltaMovement(.13, -.06, .21); e.setCanceled(true); }
                }
                @SubscribeEvent(priority=EventPriority.LOWEST) public void post(ServerTickEvent.Post e) {
                    if (e.getServer() == p.server && actual.incoming > 0 && outcome[0] == null) {
                        // Observe immediately after controller hurt, before native entity physics.
                        try {
                            outcome[0] = target.getDeltaMovement();
                            Vec3 source = actual.source.getSourcePosition().add(control.position().subtract(target.position()));
                            control.hurt(new DamageSource(p.damageSources().playerAttack(p).typeHolder(), p, p, source), 5);
                            vector(h, outcome[0], control.getDeltaMovement(), "single ordinary hurt call retains native Y and resistance/cancellation");
                        } catch (Throwable failure) { f.fail(failure); }
                    }
                }
            });
            f.confirm(p, () -> f.after(p, 8, () -> {
                h.assertTrue(outcome[0] != null && actual.incoming == 1 && actual.knockbacks == ordinary.knockbacks, "one native hurt owns at most its one native impulse");
                if (mode != 1) { near(h, actual.originalStrength, .4F, "cage retains ordinary native knockback strength"); near(h, actual.originalRatioX, mode == 3 ? -1 : 1, "selected native wall pushes inward even with original zero horizontal offset"); near(h, actual.originalRatioZ, 0, "no additional tangential impulse"); }
                near(h, target.getHealth(), control.getHealth(), "knockback cancellation never refunds accepted native damage"); f.succeed();
            }));
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void native_incoming_resource_mutation_revokes_closed_damage_authority(GameTestHelper h) { callbackMutation(h, 0); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void native_target_move_revokes_closed_contact_before_hp_commit(GameTestHelper h) { callbackMutation(h, 1); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void native_target_identity_unload_revokes_closed_contact_before_hp_commit(GameTestHelper h) { callbackMutation(h, 2); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void native_final_damage_amplification_cannot_expand_original_share(GameTestHelper h) { callbackMutation(h, 3); }
    private static void callbackMutation(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { f.mark(p, false); Zombie target = f.target(f.center(p).add(3.7, .1, 0), true); boolean[] called = {false};
            f.hook(new Object() {
                @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) { if (e.getEntity() != target || called[0] || mode == 3) return; called[0] = true;
                    if (mode == 0) f.cp(p, HighPowerResources.cp(p) + 1); else if (mode == 1) target.setPos(target.position().add(2, 0, 0)); else nativeUnload(h, target); }
                @SubscribeEvent public void pre(LivingDamageEvent.Pre e) { if (e.getEntity() == target && mode == 3) { called[0] = true; e.setNewDamage(e.getNewDamage() * 2); } }
            });
            f.confirm(p, () -> f.after(p, 8, () -> { h.assertTrue(called[0], "actual native stage reached adversarial listener"); eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "stale closed contact cannot commit exact HP"); vector(h, target.getDeltaMovement(), Vec3.ZERO, "rejected authority cannot create fallback impulse"); f.succeed(); })); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void captured_native_source_cannot_reenter_transfer_or_replay(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { f.mark(p, false); Vec3 c = f.center(p); Zombie target = f.target(c.add(3.7, .1, 0), true), other = f.target(c.add(15, .1, 0), true); DamageSource[] captured = {null};
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) { if (e.getEntity() == target && captured[0] == null) { captured[0] = e.getSource(); h.assertFalse(other.hurt(captured[0], 5), "actual captured source cannot borrow a second victim"); } } });
            f.confirm(p, () -> f.after(p, 8, () -> { h.assertTrue(captured[0] != null, "real native cage source was observed"); eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "reentrant use revokes outer source before exact HP"); eq(h, hp(other), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "wrong target never acquires authority"); h.assertFalse(target.hurt(captured[0], 5), "finished source cannot replay"); h.assertFalse(HighPowerCombat.attack(p, SKILL, other, n("5")), "forged generic skill name cannot mint a private source"); f.succeed(); })); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void owner_category_change_closes_live_field_and_preserves_paid_debt(GameTestHelper h) { ownerCleanup(h, 0); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void actual_owner_death_closes_live_field_and_preserves_paid_debt(GameTestHelper h) { ownerCleanup(h, 1); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void actual_owner_logout_closes_live_field_without_refund(GameTestHelper h) { ownerCleanup(h, 2); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void native_owner_entity_manager_unload_closes_field_without_refund(GameTestHelper h) { ownerCleanup(h, 3); }
    private static void ownerCleanup(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { f.mark(p, false); Zombie target = f.target(f.center(p).add(3.7, .1, 0), true);
            f.confirm(p, () -> {
                if (mode == 0) AbilityStorage.get(p).selectCategory(HighPowerCategories.ELECTROMASTER);
                else if (mode == 1) { p.setHealth(0); p.die(p.damageSources().generic()); }
                else if (mode == 2) p.server.getPlayerList().remove(p); else nativeUnload(h, p);
                h.runAfterDelay(8, () -> { h.assertFalse(VectorCage.active(p) || VectorCage.preview(p), "native lifecycle change releases owner context"); eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "retired owner cannot emit a delayed pulse"); h.assertTrue(VectorCage.cooldownDebt(p) > 0, "death/category/logout cannot erase paid physical debt"); f.succeed(); });
            }); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=130)
    public static void native_reload_world_clock_jumps_and_duplicate_callbacks_cannot_reset_paid_tick_debt(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { f.mark(p, false); f.target(f.center(p).add(3.7, .1, 0), true);
            f.confirm(p, () -> {
                int debt = VectorCage.cooldownDebt(p); h.assertTrue(debt == 40, "q4 paid cast begins with forty real player ticks");
                for (int i = 0; i < 20; i++) VectorCage.tick(p);
                h.assertTrue(VectorCage.cooldownDebt(p) == debt, "direct duplicate maintenance cannot age real player debt");
                long gameTime = h.getLevel().getGameTime();
                var clock = (net.minecraft.world.level.storage.ServerLevelData)h.getLevel().getLevelData();
                try { clock.setGameTime(gameTime + 100000); VectorCage.tick(p); h.assertTrue(VectorCage.cooldownDebt(p) == debt, "world-clock jump does not pay native debt"); }
                finally { clock.setGameTime(gameTime); }
                f.reload(p); h.assertFalse(VectorCage.active(p) || VectorCage.preview(p), "real NBT load cannot restore field authority");
                h.assertTrue(VectorCage.cooldownDebt(p) == debt, "native save/load preserves unpaid physical countdown");
                f.after(p, 5, () -> { h.assertTrue(VectorCage.cooldownDebt(p) == debt - 5, "exactly five real player Posts repay five debt units"); f.succeed(); });
            }); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=170)
    public static void crouch_at_mark_freezes_low_damage_and_slowness_after_actual_positive_loss(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { f.mark(p, true); p.setShiftKeyDown(false); Zombie target = f.target(f.center(p).add(3.7, .1, 0), true); BigDecimal total = HighPowerGrowth.values(p).damage().multiply(n(".5"));
            f.confirm(p, () -> {
                f.after(p, 7, () -> { MobEffectInstance effect = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN); h.assertTrue(effect != null && effect.getAmplifier() == 1 && effect.getDuration() > 0 && effect.getDuration() <= 20, "positive accepted low-mode loss grants bounded native Slowness II"); eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total.divide(n("4"))), "confirmation crouch cannot change marked half-D mode"); });
                f.after(p, 73, () -> { eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total), "four low-mode pulses total only original half-D"); f.succeed(); });
            }); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void canceled_or_zero_native_loss_cannot_grant_low_mode_slowness(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { f.mark(p, true); Vec3 c = f.center(p); Zombie canceled = f.target(c.add(-3.7, .1, 0), true), zero = f.target(c.add(3.7, .1, 0), true);
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) { if (e.getEntity() == canceled) e.setCanceled(true); } @SubscribeEvent public void pre(LivingDamageEvent.Pre e) { if (e.getEntity() == zero) e.setNewDamage(0); } });
            f.confirm(p, () -> f.after(p, 8, () -> { for (Zombie target : List.of(canceled, zero)) { eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "canceled/zero residual has no accepted loss"); h.assertFalse(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "no positive accepted loss means no slowness authority"); } f.succeed(); })); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=120)
    public static void preexisting_and_foreign_hidden_slowness_merge_survive_owner_cleanup(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { f.mark(p, true); Vec3 c = f.center(p); Zombie preexisting = f.target(c.add(-3.7, .1, 0), true), merged = f.target(c.add(3.7, .1, 0), true);
            MobEffectInstance original = new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 2); preexisting.addEffect(original);
            f.confirm(p, () -> f.after(p, 7, () -> {
                h.assertTrue(preexisting.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == original && original.getAmplifier() == 2, "preexisting stronger native potion wins intact");
                MobEffectInstance owned = merged.getEffect(MobEffects.MOVEMENT_SLOWDOWN); h.assertTrue(owned != null, "second body acquired real low-mode slowness");
                merged.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0));
                h.assertTrue(merged.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == owned, "weaker long foreign effect enters vanilla hidden chain in place");
                Tag before = owned.save(); VectorCage.abort(p);
                h.assertTrue(merged.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == owned && owned.save().equals(before), "cleanup cannot erase, shorten or reconstruct foreign hidden chain");
                h.assertTrue(preexisting.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == original, "cleanup cannot erase preexisting foreign effect"); f.succeed();
            })); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void native_effect_applicable_veto_cannot_be_overridden(GameTestHelper h) { effectCallback(h, 0); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void native_effect_added_abort_refuses_stale_outer_commit(GameTestHelper h) { effectCallback(h, 1); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void native_effect_added_foreign_reentry_preserves_foreign_instance(GameTestHelper h) { effectCallback(h, 2); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void native_effect_added_reuse_of_pending_owned_object_refuses_both_commits(GameTestHelper h) { effectCallback(h, 3); }
    private static void effectCallback(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { f.mark(p, true); Zombie target = f.target(f.center(p).add(3.7, .1, 0), true); boolean[] called = {false}, nestedAccepted = {true}; MobEffectInstance foreign = new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 2);
            f.hook(new Object() {
                @SubscribeEvent public void applicable(MobEffectEvent.Applicable e) { if (mode == 0 && e.getEntity() == target && e.getEffectInstance().is(MobEffects.MOVEMENT_SLOWDOWN)) { called[0] = true; e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY); } }
                @SubscribeEvent public void added(MobEffectEvent.Added e) { if (mode == 0 || e.getEntity() != target || !e.getEffectInstance().is(MobEffects.MOVEMENT_SLOWDOWN) || called[0]) return; called[0] = true; if (mode == 1) VectorCage.abort(p); else if (mode == 2) target.addEffect(foreign); else nestedAccepted[0] = target.addEffect(e.getEffectInstance(), p); }
            });
            f.confirm(p, () -> f.after(p, 8, () -> { h.assertTrue(called[0], "real native effect callback executes"); h.assertTrue(hp(target).compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth()) < 0, "accepted damage precedes optional native effect");
                if (mode == 3) h.assertFalse(nestedAccepted[0], "native HEAD guard refuses recursive reuse of the pending exact effect instance");
                if (mode == 2) h.assertTrue(target.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == foreign && foreign.getAmplifier() == 2, "outer owned effect cannot overwrite nested foreign addition"); else h.assertFalse(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "veto or ended owner prevents late owned effect commit"); f.succeed(); })); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=145)
    public static void native_remove_cancellation_leaves_only_bounded_ordinary_residue(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { f.mark(p, true); Zombie target = f.target(f.center(p).add(3.7, .1, 0), true); boolean[] removal = {false};
            f.hook(new Object() { @SubscribeEvent public void remove(MobEffectEvent.Remove e) { if (e.getEntity() == target && e.getEffect().equals(MobEffects.MOVEMENT_SLOWDOWN)) { removal[0] = true; e.setCanceled(true); } } });
            f.confirm(p, () -> f.after(p, 7, () -> { MobEffectInstance effect = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN); h.assertTrue(effect != null, "owned native slowness exists"); int duration = effect.getDuration();
                h.assertFalse(target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN), "actual native Remove listener cancels removal");
                h.assertTrue(removal[0], "native removal cancellation was genuinely observed"); VectorCage.abort(p);
                h.assertTrue(target.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == effect && effect.getDuration() <= duration, "cancelable native cleanup cannot forcibly erase an effect or extend it");
                f.after(p, 22, () -> { h.assertFalse(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "ordinary twenty-tick residue expires without a renewed lease"); f.succeed(); });
            })); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=155)
    public static void native_expiry_cancellation_is_respected_and_cannot_reacquire_same_cast_lease(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> { f.mark(p, true); Zombie target = f.target(f.center(p).add(3.7, .1, 0), true);
            boolean[] cancel = {true}, expired = {false}; MobEffectInstance[] owned = {null};
            f.hook(new Object() { @SubscribeEvent public void expiry(MobEffectEvent.Expired e) {
                if (e.getEntity() == target && e.getEffectInstance().is(MobEffects.MOVEMENT_SLOWDOWN)) { expired[0] = true; if (cancel[0]) e.setCanceled(true); }
            } });
            f.confirm(p, () -> {
                f.after(p, 7, () -> { owned[0] = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN); h.assertTrue(owned[0] != null, "positive native contact acquired original owned instance"); });
                f.after(p, 29, () -> { h.assertTrue(expired[0], "real native expiry reached cancellation listener");
                    h.assertTrue(target.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == owned[0] && owned[0].getDuration() == 0, "canceled expiry preserves only native zero-duration residue without refreshing or replacement"); cancel[0] = false; });
                f.after(p, 49, () -> { h.assertFalse(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "later accepted pulse cannot reclaim a lease surrendered by canceled native expiry");
                    h.assertTrue(VectorCage.receipts(p).size() >= 3, "lack of effect is verified despite three real positive pulse contacts"); f.succeed(); });
            }); });
    }

    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static BigDecimal hp(LivingEntity target) { return HighPowerCombat.enabled(target) ? n(HighPowerCombat.healthText(target)) : n(Float.toString(target.getHealth())); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String reason) { h.assertTrue(actual.compareTo(expected) == 0, reason + ": " + actual + " != " + expected); }
    private static void near(GameTestHelper h, double actual, double expected, String reason) { h.assertTrue(Math.abs(actual - expected) < 1e-5, reason + ": " + actual + " != " + expected); }
    private static void vector(GameTestHelper h, Vec3 actual, Vec3 expected, String reason) { h.assertTrue(actual.distanceToSqr(expected) < 1e-10, reason + ": " + actual + " != " + expected); }
    private static void cpEqual(GameTestHelper h, ServerPlayer p, Runnable action) { double before = HighPowerResources.cp(p); action.run(); near(h, HighPowerResources.cp(p), before, "refused/free request cannot debit CP"); }
    private static void shield(LivingEntity target, float yaw) { target.setYRot(yaw); target.setYHeadRot(yaw); target.yBodyRot = yaw; target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); target.startUsingItem(InteractionHand.OFF_HAND); }
    private static void defenses(GameTestHelper h, LivingEntity target) {
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET); helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(true)); target.setItemSlot(EquipmentSlot.HEAD, helmet);
        target.getAttribute(Attributes.ARMOR).setBaseValue(5); target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0)); target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0)); target.setAbsorptionAmount(.5F);
    }
    private static void assertSource(GameTestHelper h, DamageSource source, ServerPlayer caster) {
        h.assertTrue(source != null && source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() == caster && source.getDirectEntity() == caster && source.sourcePositionRaw() != null && source.sourcePositionRaw().equals(source.getSourcePosition()), "closed native source retains real player and explicit wall origin");
        h.assertFalse(source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) || source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypeTags.BYPASSES_RESISTANCE) || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_COOLDOWN) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY), "cage source grants no new defense bypass");
    }
    private static void nativeUnload(GameTestHelper h, Entity target) {
        try { var field = ServerLevel.class.getDeclaredField("entityManager"); field.setAccessible(true); var method = net.minecraft.world.level.entity.PersistentEntitySectionManager.class.getDeclaredMethod("unloadEntity", net.minecraft.world.level.entity.EntityAccess.class); method.setAccessible(true); method.invoke(field.get(h.getLevel()), target);
            h.assertTrue(target.isRemoved() && target.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK && h.getLevel().getEntity(target.getUUID()) == null, "actual native entity-manager unload revoked UUID lookup");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned native unload fixture unavailable", failure); }
    }
    private static final class Ledger {
        final BigDecimal q, earned, health, shield; final double mastery, xp;
        Ledger(ServerPlayer p) { q = HighPowerGrowth.values(p).position(); earned = HighPowerCombat.earnedGrowthPosition(p); health = hp(p); shield = HighPowerCombat.shield(p); mastery = AbilityStorage.get(p).exp(ID); xp = AbilityStorage.get(p).levelExperience; }
        void unchanged(GameTestHelper h, ServerPlayer p) { eq(h, HighPowerGrowth.values(p).position(), q, "cage cannot grow q"); eq(h, HighPowerCombat.earnedGrowthPosition(p), earned, "cage cannot certify Q"); eq(h, hp(p), health, "cage cannot refill owner exact HP"); eq(h, HighPowerCombat.shield(p), shield, "cage cannot borrow or refill owner shield"); h.assertTrue(AbilityStorage.get(p).exp(ID) == mastery && AbilityStorage.get(p).levelExperience == xp, "cage grants no mastery or level XP"); }
    }
    public static final class Observation {
        final LivingEntity target; DamageSource source; int incoming, posts, knockbacks; boolean blocked; float damage, armor, effects, enchantments, absorption, blockedDamage, originalStrength; double originalRatioX, originalRatioZ; final List<Integer> ticks = new ArrayList<>(); final List<Float> strengths = new ArrayList<>();
        Observation(LivingEntity target) { this.target = target; }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void incoming(LivingIncomingDamageEvent e) { if (e.getEntity() == target) { incoming++; source = e.getSource(); ticks.add(target.getServer().getTickCount()); } }
        @SubscribeEvent(receiveCanceled=true) public void knockback(LivingKnockBackEvent e) { if (e.getEntity() == target) { knockbacks++; strengths.add(e.getOriginalStrength()); originalStrength = e.getOriginalStrength(); originalRatioX = e.getOriginalRatioX(); originalRatioZ = e.getOriginalRatioZ(); } }
        @SubscribeEvent public void shield(LivingShieldBlockEvent e) { if (e.getEntity() == target) { blocked = e.getBlocked(); blockedDamage = e.getBlockedDamage(); } }
        @SubscribeEvent public void post(LivingDamageEvent.Post e) { if (e.getEntity() == target) { posts++; damage = e.getNewDamage(); armor = e.getReduction(DamageContainer.Reduction.ARMOR); effects = e.getReduction(DamageContainer.Reduction.MOB_EFFECTS); enchantments = e.getReduction(DamageContainer.Reduction.ENCHANTMENTS); absorption = e.getReduction(DamageContainer.Reduction.ABSORPTION); } }
    }
    /** Actual player Post events only age checks; native callbacks never complete GameTestInfo. */
    public static final class PlayerPosts {
        final Fixture f; final ServerPlayer owner; final Map<Integer, List<Runnable>> checks = new HashMap<>(); int last, completed;
        PlayerPosts(Fixture f, ServerPlayer owner) { this.f = f; this.owner = owner; last = owner.server.getTickCount(); }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(PlayerTickEvent.Post e) {
            if (e.getEntity() != owner || f.closed || f.terminalQueued || owner.server.getTickCount() <= last) return;
            last = owner.server.getTickCount(); List<Runnable> due = checks.remove(++completed);
            if (due != null) for (Runnable action : due) try { action.run(); } catch (Throwable failure) { f.fail(failure); }
        }
    }
    public static final class NativeTicks {
        final Fixture f; int last;
        NativeTicks(Fixture f) { this.f = f; last = f.h.getLevel().getServer().getTickCount(); }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void after(ServerTickEvent.Post e) {
            if (e.getServer() != f.h.getLevel().getServer() || f.closed || f.terminalQueued || e.getServer().getTickCount() <= last) return;
            last = e.getServer().getTickCount();
            try { for (ServerPlayer p : List.copyOf(f.players)) if (!p.isRemoved() && !p.hasDisconnected()) { p.doTick(); f.packets(p); } } catch (Throwable failure) { f.fail(failure); }
        }
    }
    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>(); final List<Object> hooks = new ArrayList<>();
        final List<net.minecraft.world.scores.PlayerTeam> teams = new ArrayList<>(); final Map<ServerPlayer, EmbeddedChannel> channels = new IdentityHashMap<>();
        final Map<ServerPlayer, Vec3> anchors = new IdentityHashMap<>(); final Map<ServerPlayer, Long> inputs = new IdentityHashMap<>(); final Map<ServerPlayer, PlayerPosts> clocks = new IdentityHashMap<>();
        boolean closed, terminalQueued; Throwable failure; MachineDeveloperBlockEntity machine, advancedMachine; int learningSequence;
        Fixture(GameTestHelper h) {
            this.h = h; opened++; hook(new NativeTicks(this));
            for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            h.onEachTick(() -> { if (terminalQueued && !closed && !h.testInfo.isDone()) { if (failure == null) h.succeed(); else h.testInfo.fail(failure); } });
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { LOG.error("NEW441 failure: " + info.getTestName(), info.getError()); close(); }
                public void testAddedForRerun(GameTestInfo old, GameTestInfo next, GameTestRunner runner) { close(); }
            });
        }
        void succeed() { if (!closed && !terminalQueued) terminalQueued = true; }
        void fail(Throwable problem) { if (!closed && !terminalQueued) { failure = problem; terminalQueued = true; } }
        ServerPlayer player() { return player(28, 12, 4, true); }
        ServerPlayer player(double x, double z, int q, boolean learned) {
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "VC" + UUID.randomUUID().toString().substring(0, 10)), false);
            ServerPlayer p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            Connection connection = new Connection(PacketFlow.SERVERBOUND); EmbeddedChannel channel = new EmbeddedChannel(connection); channels.put(p, channel);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); packets(p);
            p.setGameMode(GameType.SURVIVAL); p.getFoodData().setFoodLevel(6); p.setNoGravity(true); anchors.put(p, h.absoluteVec(new Vec3(x + .5, 2, z + .5))); place(p);
            int level = Math.max(4, q); h.assertTrue(HighPowerCombat.enroll(p, level), "declared genuine connected native caster enrollment");
            var state = AbilityStorage.get(p); state.selectCategory(HighPowerCategories.VECMANIP); state.setLevel(level);
            for (String skill : List.of(HighPowerSkills.DEFLECTION_STRIKE, "braking_touch", "yaw_palm", TrajectoryReturn.ID)) state.learn(skill);
            if (learned) state.learn(ID); state.activated = true; state.overloadFine = true;
            h.assertTrue(state.presets.edit(0, 0, learned ? ID : HighPowerSkills.DEFLECTION_STRIKE, state::learned), "declared learned fixture uses ordinary bound slot");
            HighPowerResources.initializeBudget(p);
            if (q < 4) { state.setLevel(q); HighPowerGrowth.values(p); state.setLevel(4); HighPowerGrowth.values(p); state.activated = true; state.overloadFine = true; }
            return p;
        }
        void place(ServerPlayer p) { Vec3 at = anchors.get(p); p.moveTo(at.x, at.y, at.z, 0, 20); p.setYHeadRot(0); p.yBodyRot = 0; p.setOnGround(true); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        void ready(Runnable action) { h.runAfterDelay(61, () -> { try { for (ServerPlayer p : players) { p.doTick(); place(p); packets(p); } action.run(); } catch (Throwable problem) { fail(problem); } }); }
        void after(ServerPlayer p, int ticks, Runnable action) { PlayerPosts clock = clocks.computeIfAbsent(p, owner -> { PlayerPosts result = new PlayerPosts(this, owner); hook(result); return result; }); clock.checks.computeIfAbsent(clock.completed + ticks, ignored -> new ArrayList<>()).add(action); }
        String wire(ServerPlayer p) { long input = inputs.getOrDefault(p, 0L) + 1; inputs.put(p, input); return new HighPowerInput.Input(0, input, p.getId(), LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p))).wire(); }
        boolean request(ServerPlayer p, String action, String value) { RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess()); try { AcademyNetwork.Request.CODEC.encode(buf, new AcademyNetwork.Request(action, value)); var decoded = AcademyNetwork.Request.CODEC.decode(buf); h.assertTrue(buf.readableBytes() == 0, "ordinary request codec consumes full bounded message"); return decoded.action().startsWith("machine_") ? MachineDeveloperSessions.request(p, decoded) : AcademyGameplay.requestFromClient(p, decoded); } finally { buf.release(); } }
        String press(ServerPlayer p) { String held = wire(p); h.assertTrue(request(p, "vector_cage_start", held), "ordinary authenticated key down accepted"); return held; }
        boolean release(ServerPlayer p, String held) { return request(p, "vector_cage_release", held); }
        boolean shortPress(ServerPlayer p) { String held = wire(p); return request(p, "vector_cage_start", held) && release(p, held); }
        void mark(ServerPlayer p, boolean low) { p.setShiftKeyDown(low); h.assertTrue(shortPress(p) && VectorCage.preview(p), "first ordinary short release creates top-face preview"); }
        Vec3 center(ServerPlayer p) { var visual = VectorCage.lastVisual(p); h.assertTrue(visual != null, "native preview exposes its frozen visual center"); return visual.center(); }
        void confirm(ServerPlayer p, Runnable after) { after(p, 5, () -> { h.assertTrue(shortPress(p) && VectorCage.active(p), "second ordinary short release confirms frozen field"); after.run(); }); }
        Zombie target(Vec3 feet, boolean exact) { Husk target = new Husk(EntityType.HUSK, h.getLevel()); prepare(target, feet); if (exact) h.assertTrue(HighPowerCombat.enroll(target, 5), "declared exact native hostile enrollment"); else { target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); target.setHealth(200); } return target; }
        void prepare(Zombie target, Vec3 feet) { target.setNoAi(true); target.setNoGravity(true); target.setSilent(true); target.setPos(feet); target.setOnGround(true); target.getAttribute(Attributes.ARMOR).setBaseValue(0); target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1); add(target); }
        void add(Entity target) { h.assertTrue(h.getLevel().addFreshEntity(target), "real native entity enters live world"); entities.add(target); }
        Observation observe(LivingEntity target) { Observation observer = new Observation(target); hook(observer); return observer; }
        void discount(LivingEntity target) { hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void skill(SkillAttackEvent event) { if (event.target == target && event.skill.equals(SKILL)) event.amount = 5; } }); }
        void cp(ServerPlayer p, double value) { p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", value); }
        void hook(Object listener) { hooks.add(listener); NeoForge.EVENT_BUS.register(listener); }
        void packets(ServerPlayer p) { EmbeddedChannel channel = channels.get(p); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet; while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet); } }
        void reload(ServerPlayer p) { AbilityStorage.save(p); CompoundTag saved = p.saveWithoutId(new CompoundTag()); VectorCage.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.load(saved); place(p); inputs.put(p, 0L); }
        MachineDeveloperBlockEntity normal(ServerPlayer p, double energy) { return developer(p, energy, false); }
        MachineDeveloperBlockEntity advanced(ServerPlayer p, double energy) { return developer(p, energy, true); }
        MachineDeveloperBlockEntity developer(ServerPlayer p, double energy, boolean advanced) {
            BlockPos base = h.absolutePos(new BlockPos(54, 2, advanced ? 56 : 48));
            if (machine != null) machine.endUsers(); if (advancedMachine != null) advancedMachine.endUsers();
            MachineDeveloperBlockEntity selected = advanced ? advancedMachine : machine;
            if (selected == null) {
                var item = advanced ? MachineDevelopers.ADVANCED_ITEM.get() : MachineDevelopers.NORMAL_ITEM.get();
                ItemStack stack = new ItemStack(item); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
                h.assertTrue(item.place(new BlockPlaceContext(p, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(base), Direction.UP, base, false))).consumesAction(), "actual complete native developer placed in clear supported space");
                selected = (MachineDeveloperBlockEntity)h.getLevel().getBlockEntity(base);
                h.assertTrue(selected != null, "native developer master exists at placed air block");
                if (advanced) advancedMachine = selected; else machine = selected;
            }
            selected.battery().load(energy); p.moveTo(base.getX() + .5, base.getY(), base.getZ() - 1.5, 0, 0);
            h.assertTrue(selected.use(p), "genuine player-bound developer session opens"); return selected;
        }
        boolean learnRequest(ServerPlayer p, String skill) { String wire = MachineDeveloperSessions.activeToken(p).orElseThrow() + ":" + new HighPowerLearningProtocol.Request(skill, ++learningSequence).wire(); return request(p, "machine_hp_learn", wire); }
        void develop(ServerPlayer p, int ticks) { for (int i = 0; i < ticks; i++) DevelopmentController.tick(p, ignored -> {}); }
        void learn(ServerPlayer p, String skill) { int count = ClassicRules.learningStimulations(SkillCatalog.find(HighPowerCategories.VECMANIP, skill).orElseThrow().level()); MachineDeveloperBlockEntity developer = normal(p, DeveloperType.NORMAL.actualConsumption(count)); h.assertTrue(learnRequest(p, skill), "finite genuine parent learning starts"); develop(p, count * DeveloperType.NORMAL.ticksPerStimulation()); near(h, developer.battery().getEnergy(), 0, "parent consumed finite IF exactly"); h.assertTrue(AbilityStorage.get(p).learned(skill), "genuine prerequisite development completes"); }
        void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); if (machine != null) machine.endUsers(); if (advancedMachine != null) advancedMachine.endUsers();
            for (ServerPlayer p : players) { VectorCage.remove(p); DevelopmentController.remove(p); MachineDeveloperSessions.close(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.server.getPlayerList().remove(p); }
            entities.forEach(Entity::discard); teams.forEach(h.getLevel().getScoreboard()::removePlayerTeam); channels.values().forEach(EmbeddedChannel::finishAndReleaseAll); VectorCageGameTests.closed++;
            LOG.info("NEW441 native fixture closed: {} opened={} closed={} connected={}", h.testInfo.getTestName(), opened, VectorCageGameTests.closed, players.stream().filter(p -> p.server.getPlayerList().getPlayer(p.getUUID()) == p).count()); }
    }
}

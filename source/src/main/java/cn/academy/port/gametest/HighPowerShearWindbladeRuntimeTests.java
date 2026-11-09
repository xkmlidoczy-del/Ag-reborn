package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.ShearWindblade;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.highpower.core.ContinuousHighPowerGrowth;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.DamageMitigation;
import cn.academy.port.highpower.core.DefenseProfile;
import cn.academy.port.highpower.core.DeflectionStrikeRules;
import cn.academy.port.highpower.core.HighPowerLevel;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.minecraft.server.network.CommonListenerCookie;

/** NEW421: real connected input, finite travelling native fronts, and closed native damage. */
@GameTestHolder("academy_windblade_runtime")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerShearWindbladeRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_shear_windblade";
    private static final String ID = "shear_windblade", SKILL = "vecmanip.shear_windblade";
    private HighPowerShearWindbladeRuntimeTests() { }
    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int dimension : new int[] {32, 10, 32}) size.add(IntTag.valueOf(dimension));
        for (int axis = 0; axis < 3; axis++) position.add(IntTag.valueOf(0));
        cell.put("pos", position); cell.putInt("state", 0); blocks.add(cell);
        air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_windblade_runtime", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }


    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 120)
    public static void five_active_q_anchors_charge_once_and_conserve_one_whole_budget(GameTestHelper h) { economics(h, 1); }
    private static void economics(GameTestHelper h, int level) {
        Fixture f = new Fixture(h, level); Zombie target = f.target(8, true);
        double[] costs = {8, 8, 3, .5, .05}; int[] cooldowns = {30, 30, 15, 5, 1};
        double cp = f.cp(), xp = AbilityStorage.get(f.p).levelExperience; long now = h.getLevel().getGameTime();
        BigDecimal q = HighPowerGrowth.values(f.p).position(), earned = HighPowerCombat.earnedGrowthPosition(f.p);
        eq(h, q, BigDecimal.valueOf(level), "declared native active q anchor");
        h.assertTrue(f.cast(), "genuine integer-unlocked input emits at q " + level);
        near(h, f.cp(), cp - costs[level - 1], "both blades have one initial CP debit");
        h.assertTrue(AbilityStorage.get(f.p).cooldowns.getOrDefault(ID, 0) == cooldowns[level - 1], "cooldown anchors are ticks, including LV5 one tick");
        h.assertTrue(f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_" + ID) == now + cooldowns[level - 1], "persistent numerical cooldown has the approved tick duration");
        h.assertFalse(f.cast(), "a travelling owner cannot start a second controller");
        f.after(18, () -> {
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(level)), "one UUID receives at most the whole frozen cast total");
            h.assertFalse(ShearWindblade.active(f.p), "both finite fronts retire");
            eq(h, HighPowerGrowth.values(f.p).position(), q, "ordinary use grants no active growth");
            eq(h, HighPowerCombat.earnedGrowthPosition(f.p), earned, "ordinary use grants no earned growth");
            h.assertTrue(AbilityStorage.get(f.p).levelExperience == xp && AbilityStorage.get(f.p).exp(ID) == 0, "windblade cannot farm skill or level XP");
            f.close(); if (level < 5) economics(h, level + 1); else h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void first_four_actual_contacts_split_exact_total_and_fifth_body_never_receives_a_share(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); List<Zombie> victims = new ArrayList<>();
        for (int i = 0; i < 5; i++) victims.add(f.target(3 + i * 3, true));
        h.assertTrue(f.cast(), "four reviewed contacts are frozen before payment");
        var receipt = ShearWindblade.receipt(f.p);
        h.assertTrue(receipt != null && receipt.targets().equals(victims.subList(0, 4).stream().map(Entity::getUUID).toList()), "admission is by actual front contact time");
        BigDecimal sum = receipt.shares().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        eq(h, sum, total(2), "allocation has one exact conserved total");
        eq(h, receipt.shares().get(3), total(2).subtract(receipt.shares().get(0).multiply(BigDecimal.valueOf(3))), "last slot owns the exact remainder");
        f.after(18, () -> {
            for (int i = 0; i < 4; i++) eq(h, hp(victims.get(i)), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2).divide(BigDecimal.valueOf(4))), "each frozen UUID receives only its original quarter");
            eq(h, hp(victims.get(4)), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "unallocated physical body gets no fifth attack"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void equal_time_contacts_use_uuid_order_instead_of_entity_insertion_order(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); List<Zombie> victims = new ArrayList<>(); Vec3 center = f.eye().add(0, 0, 6);
        for (int i = 5; i >= 1; i--) victims.add(f.targetAt(center, true, f.uuid(i)));
        h.assertTrue(f.cast(), "overlapping genuine native bodies form one opportunity");
        var selected = ShearWindblade.receipt(f.p).targets();
        h.assertTrue(selected.equals(List.of(f.uuid(1), f.uuid(2), f.uuid(3), f.uuid(4))), "exact contact tie is resolved by stable UUID");
        f.after(18, () -> { eq(h, hp(victims.get(0)), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "UUID-last tied body is not allocated"); h.succeed(); });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void small_native_body_between_vertices_is_hit_by_the_continuous_chord_sweep(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); Zombie target = f.targetAt(f.eye().add(.15, 0, 7), true);
        target.getAttribute(Attributes.SCALE).setBaseValue(.1); target.refreshDimensions(); f.center(target, f.eye().add(.15, 0, 7));
        h.assertTrue(target.getBbWidth() < .07, "actual native scale yields a body narrower than the vertex gap");
        h.assertTrue(target.getBoundingBox().minX - .035 > f.eye().x && target.getBoundingBox().maxX + .035 < f.eye().x + .3,
                "neither adjacent vertex trajectory can touch the body, even with approved radius");
        h.assertTrue(f.cast(), "continuous interior chord contact admits a target between samples");
        f.after(18, () -> { eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2)), "thin swept chord cannot tunnel between vertex rays"); h.succeed(); });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void standing_and_crouching_select_horizontal_and_vertical_finite_arc_planes(GameTestHelper h) { plane(h, false); }
    private static void plane(GameTestHelper h, boolean crouch) {
        Fixture f = new Fixture(h, 2); f.p.setShiftKeyDown(crouch);
        Zombie horizontal = f.small(f.eye().add(1, 0, 7)), vertical = f.small(f.eye().add(0, 1, 7));
        h.assertTrue(f.cast(), "declared gesture has an actual plane contact");
        f.after(18, () -> {
            eq(h, hp(horizontal), crouch ? cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth() : cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2)), "horizontal wing follows normal gesture only");
            eq(h, hp(vertical), crouch ? cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2)) : cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "vertical wing follows crouch only");
            f.close(); if (!crouch) plane(h, true); else h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void outside_fan_and_beyond_twenty_four_metres_are_free_geometric_misses(GameTestHelper h) {
        for (int mode = 0; mode < 3; mode++) try (Fixture f = new Fixture(h, 2)) {
            Vec3 offset = mode == 0 ? new Vec3(2, 0, 20) : mode == 1 ? new Vec3(0, 2, 20) : new Vec3(0, 0, 24.6);
            Zombie target = f.small(f.eye().add(offset)); double cp = f.cp();
            h.assertFalse(f.cast(), "no widening cone, vertical volume or range extension: " + mode);
            near(h, f.cp(), cp, "empty trajectory costs no CP"); eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "outside finite swept arc is untouched");
            h.assertTrue(!ShearWindblade.active(f.p) && AbilityStorage.get(f.p).cooldowns.getOrDefault(ID, 0) == 0, "empty attempt creates no live controller or cooldown");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void fronts_travel_two_metres_per_native_tick_and_second_front_waits_two_ticks(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); Zombie target = f.target(12, true); Vec3 origin = f.eye(); long started = h.getLevel().getGameTime();
        h.assertTrue(f.cast(), "remote target is a valid frozen future contact"); eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "launch is not instant damage along full range");
        var initial = ShearWindblade.receipt(f.p).fronts();
        h.assertTrue(initial.size() == 1 && initial.get(0).index() == 0, "only first front is emitted at launch");
        h.assertTrue(initial.get(0).current().size() == 9, "finite arc is exactly eight chords");
        f.after(1, () -> {
            h.assertTrue(h.getLevel().getGameTime() > started, "timing witness is a genuine native tick");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "a twelve-metre target cannot be hit on tick one");
            var fronts = ShearWindblade.receipt(f.p).fronts();
            h.assertTrue(fronts.size() == 1 && fronts.get(0).index() == 0, "second blade still waits its two-tick delay");
            for (var front : fronts) for (int i = 0; i < 9; i++) h.assertTrue(front.current().get(i).distanceTo(front.previous().get(i)) <= 2.000001, "one tick has at most two metres of actual travel");
        });
        f.after(3, () -> { eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "no full-range catch-up during first three ticks");
            var fronts = ShearWindblade.receipt(f.p).fronts(); h.assertTrue(fronts.size() == 2 && fronts.get(1).current().get(4).z > origin.z + .4, "second finite front starts only after its delay");
            near(h, fronts.get(0).current().get(4).z - fronts.get(1).current().get(4).z, 4, "second front remains exactly two native ticks behind first"); });
        f.after(18, () -> { eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2)), "actual progressing front eventually reaches distant native body"); h.assertFalse(ShearWindblade.active(f.p), "finite travel expires"); h.succeed(); });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void walls_friends_unknown_classes_and_protected_bodies_block_before_payment(GameTestHelper h) {
        for (int mode = 0; mode < 5; mode++) try (Fixture f = new Fixture(h, 2)) {
            Zombie rear = f.target(9, true); LivingEntity body = null;
            if (mode == 0) f.wall(4);
            if (mode == 1) { body = new Cow(EntityType.COW, h.getLevel()); f.add(body, f.eye().add(0, 0, 4)); }
            if (mode == 2) { body = new Husk(EntityType.HUSK, h.getLevel()) { }; f.add(body, f.eye().add(0, 0, 4)); }
            if (mode == 3) { body = f.target(4, false); body.getPersistentData().putBoolean("academy:protected", true); }
            if (mode == 4) {
                body = f.target(4, false); var board = h.getLevel().getScoreboard(); String name = "sw" + UUID.randomUUID().toString().substring(0, 8);
                var team = board.addPlayerTeam(name); f.teams.add(team); board.addPlayerToTeam(f.p.getScoreboardName(), team); board.addPlayerToTeam(body.getScoreboardName(), team);
            }
            double cp = f.cp(); h.assertFalse(f.cast(), "first unreviewed or allied physical obstacle refuses rear target: " + mode);
            near(h, f.cp(), cp, "blocked empty front is free"); eq(h, hp(rear), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "cannot pierce a non-admitted first body"); f.assertBlocks();
            if (body != null) h.assertTrue(body.getHealth() == body.getMaxHealth(), "obstruction cannot be damaged or auto-enrolled");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void an_exactly_tied_nonhostile_contact_takes_precedence_over_hostile_uuid(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.targetAt(f.eye().add(0, 0, 5), true, f.uuid(1)); Cow cow = new Cow(EntityType.COW, h.getLevel()); cow.setUUID(f.uuid(9));
            f.add(cow, new Vec3(f.eye().x, f.eye().y, target.getBoundingBox().minZ + cow.getBbWidth() / 2.0));
            h.assertTrue(cow.getBoundingBox().minZ == target.getBoundingBox().minZ, "native body entry faces coincide exactly");
            double cp = f.cp(); h.assertFalse(f.cast(), "tied nonhostile wins over hostile admission"); near(h, f.cp(), cp, "blocked tie is free"); eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "hostile UUID order cannot bypass tied blocker");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void sixty_five_bodies_overflow_before_hostile_filtering_and_pay_nothing(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Zombie front = f.target(1, true);
            for (int i = 0; i < 64; i++) f.add(new Cow(EntityType.COW, h.getLevel()), f.eye().add(0, 0, 1.8));
            double cp = f.cp(); h.assertFalse(f.cast(), "all native bodies count before selection or early first-hit return");
            near(h, f.cp(), cp, "oversized broad phase is refused before payment"); eq(h, hp(front), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "overflow cannot emit even toward an earlier eligible body");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void exactly_sixty_four_native_bodies_are_bounded_without_premature_overflow(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Zombie first = f.target(1, true);
            for (int i = 0; i < 63; i++) f.add(new Cow(EntityType.COW, h.getLevel()), f.eye().add(0, 0, 1.8));
            double cp = f.cp(); h.assertTrue(f.cast(), "the inclusive 64-body bound retains an earlier eligible contact");
            eq(h, hp(first), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2)), "one admitted UUID precedes later nonhostile blockers");
            near(h, f.cp(), cp - 8, "valid boundary query pays once");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void overflow_in_a_later_preflight_step_refuses_the_whole_cast_before_payment(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Zombie first = f.target(1, true);
            for (int i = 0; i < 65; i++) f.add(new Cow(EntityType.COW, h.getLevel()), f.eye().add(0, 0, 8));
            double cp = f.cp(); h.assertFalse(f.cast(), "unknown later preflight cannot preserve a partial earlier plan");
            eq(h, hp(first), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "even earlier hostile stays untouched when full frozen planning overflows");
            near(h, f.cp(), cp, "later bounded-query refusal is free before first emission");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void duplicate_uuid_across_two_blades_receives_one_native_attempt(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); Zombie target = f.target(5, true); Observation hit = f.observe(target);
        h.assertTrue(f.cast(), "two-front cast has one frozen target");
        f.after(18, () -> { h.assertTrue(hit.skills == 1 && hit.incoming == 1 && hit.posts == 1, "UUID is attempted once across both blades, independently of native iframes"); eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2)), "second front cannot duplicate budget"); h.succeed(); });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void target_moving_out_after_emission_misses_without_refunding_or_retargeting(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); Zombie target = f.target(10, true); double cp = f.cp();
        h.assertTrue(f.cast(), "nonempty future contact pays at first actual front emission"); near(h, f.cp(), cp - 8, "one irreversible emission payment");
        f.center(target, f.eye().add(5, 0, 10)); Zombie newcomer = f.target(10, true);
        f.after(18, () -> { eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "moving frozen body is not tracked outside blade plane"); eq(h, hp(newcomer), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "newcomer receives no reassigned frozen share"); h.succeed(); });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void callback_invalidated_share_stays_lost_and_new_body_stops_remaining_blades(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); Zombie first = f.target(1, true), second = f.target(7, true); Zombie[] newcomer = {null};
        f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
            if (event.getEntity() != first || newcomer[0] != null) return; f.center(second, f.eye().add(5, 0, 7)); newcomer[0] = f.target(7, true);
        } });
        h.assertTrue(f.cast() && newcomer[0] != null, "real first-hit callback changes the later world after frozen allocation");
        f.after(18, () -> { eq(h, hp(first), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2).divide(BigDecimal.TWO)), "first target never inherits the missing half"); eq(h, hp(second), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "moved target loses original half"); eq(h, hp(newcomer[0]), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "new blocking hostile cannot acquire later slot"); h.succeed(); });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void callback_retarget_and_late_geometry_mutation_cannot_commit_stale_native_damage(GameTestHelper h) {
        for (boolean exact : List.of(true, false)) for (boolean pre : List.of(false, true)) try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(1, exact); f.discount(target); BigDecimal before = hp(target); boolean[] called = {false};
            Runnable move = () -> { called[0] = true; f.center(target, f.eye().add(5, 0, 1)); };
            f.hook(new Object() {
                @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) { if (!pre && e.getEntity() == target) move.run(); }
                @SubscribeEvent public void damage(LivingDamageEvent.Pre e) { if (pre && e.getEntity() == target) move.run(); }
            });
            double cp = f.cp(); h.assertTrue(f.cast() && called[0], "real native callback mutates current body");
            eq(h, hp(target), before, "live contact guard protects native and exact commit"); near(h, f.cp(), cp - 8, "callback refusal stays paid");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void cancellation_and_callback_amplification_cannot_expand_frozen_share(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(1, true); int kind = mode; boolean[] called = {false};
            f.hook(new Object() {
                @SubscribeEvent public void skill(SkillAttackEvent e) { if (e.target == target && kind == 0) { called[0] = true; e.amount *= 2; } }
                @SubscribeEvent(priority = EventPriority.LOWEST) public void incoming(LivingIncomingDamageEvent e) { if (e.getEntity() == target && (kind == 1 || kind == 2)) { called[0] = true; if (kind == 1) e.setCanceled(true); else e.setAmount(e.getAmount() * 2); } }
                @SubscribeEvent(priority = EventPriority.LOWEST) public void pre(LivingDamageEvent.Pre e) { if (e.getEntity() == target && kind == 3) { called[0] = true; e.setNewDamage(e.getNewDamage() * 2); } }
            });
            double cp = f.cp(); h.assertTrue(f.cast() && called[0], "actual event reaches refusal/amplification case " + mode);
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "closed amount rejects extra authority"); near(h, f.cp(), cp - 8, "one paid emission survives native veto");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void callback_cp_and_cooldown_mutations_refuse_current_commit_without_an_extra_debit(GameTestHelper h) {
        for (boolean clock : List.of(false, true)) try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(1, true); boolean[] called = {false}; double cp = f.cp();
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
                if (event.getEntity() != target || called[0]) return; called[0] = true;
                var data = f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
                if (clock) data.putLong("next_" + ID, data.getLong("next_" + ID) + 1);
                else data.putDouble("cp", data.getDouble("cp") - 1);
            } });
            h.assertTrue(f.cast() && called[0], "real native callback changes the captured resource witness");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "changed CP or deadline cannot commit under stale authority");
            near(h, f.cp(), cp - 8 - (clock ? 0 : 1), "only initial debit and explicit observer mutation survive");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void genuine_player_tick_cp_recovery_between_front_steps_preserves_paid_flight(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); Zombie target = f.target(22, true); double before = f.cp(); long started = h.getLevel().getGameTime();
        h.assertTrue(f.cast(), "distant frozen contact launches a paid travelling controller"); double paid = f.cp();
        near(h, paid, before - 8, "first native emission debits the approved cost");
        f.after(3, () -> {
            h.assertTrue(h.getLevel().getGameTime() - started >= 3, "three genuine server ticks elapsed");
            h.assertTrue(f.cp() > paid, "actual player tick recovery replenishes shared CP during flight");
            h.assertTrue(ShearWindblade.active(f.p), "legitimate native recovery does not poison the paid controller");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "distant body is still beyond the actual current front");
        });
        f.after(18, () -> {
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2)), "unchanged frozen share remains valid after ordinary between-step recovery");
            h.assertFalse(ShearWindblade.active(f.p), "normally recovering finite flight still retires"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void skill_callback_cp_refund_cannot_reauthorize_a_second_same_tick_target(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Zombie first = f.target(1, true), second = f.target(1.7, true); double before = f.cp(); boolean[] called = {false};
            f.hook(new Object() { @SubscribeEvent public void skill(SkillAttackEvent event) {
                if (event.target != first || !event.skill.equals(SKILL) || called[0]) return;
                called[0] = true; near(h, f.cp(), before - 8, "callback sees the actual initial eight-CP payment");
                f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", before);
            } });
            h.assertTrue(f.cast() && called[0], "first frozen target reaches actual skill callback after payment");
            near(h, f.cp(), before, "observer deliberately restores pre-cast CP to expose the free-damage regression");
            eq(h, hp(first), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "refund invalidates the first captured authority");
            eq(h, hp(second), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "a later target cannot rebase authority onto the refunded ledger");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 120)
    public static void skill_resource_mutations_retire_all_slots_across_current_and_future_ticks(GameTestHelper h) { resourceMutationCase(h, 0, 0); }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 120)
    public static void incoming_resource_mutations_retire_all_slots_across_current_and_future_ticks(GameTestHelper h) { resourceMutationCase(h, 1, 0); }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 120)
    public static void pre_damage_resource_mutations_retire_all_slots_across_current_and_future_ticks(GameTestHelper h) { resourceMutationCase(h, 2, 0); }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 120)
    public static void absorption_resource_mutations_retire_all_slots_across_current_and_future_ticks(GameTestHelper h) { resourceMutationCase(h, 3, 0); }

    /** Real observers mutate CP, the persistent deadline, native cooldown, or ledger identity.
     * Three schedules cover two launch contacts, a later second contact, and two later contacts.
     * The first failed native permit must retire the entire frozen cast, not refresh its witness.
     */
    private static void resourceMutationCase(GameTestHelper h, int stage, int scenario) {
        Fixture f = new Fixture(h, 2); int kind = scenario == 5 ? 0 : scenario % 4, timing = scenario == 0 || scenario == 5 ? 0 : scenario == 2 ? 1 : 2;
        Zombie first = f.target(timing == 2 ? 5 : 1, true), second = f.target(timing == 0 ? 1.7 : timing == 1 ? 8 : 5.7, true);
        f.discount(first); f.discount(second); boolean[] called = {false};
        if (stage == 3) { first.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0)); first.setAbsorptionAmount(.5F); }
        Runnable mutate = () -> {
            if (called[0]) return; called[0] = true;
            var data = f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
            switch (kind) {
                case 0 -> data.putDouble("cp", data.getDouble("cp") - 1);
                case 1 -> data.putLong("next_" + ID, data.getLong("next_" + ID) + 1);
                case 2 -> { var state = AbilityStorage.get(f.p); state.setCooldown(ID, state.cooldowns.getOrDefault(ID, 0) + 1); }
                default -> f.p.getPersistentData().put(HighPowerResources.DATA_KEY, data.copy());
            }
        };
        f.hook(new Object() {
            @SubscribeEvent(priority = EventPriority.LOWEST) public void skill(SkillAttackEvent event) {
                if (stage == 0 && event.target == first && event.skill.equals(SKILL)) { mutate.run(); if (scenario == 5) event.amount = 0; }
            }
            @SubscribeEvent(priority = EventPriority.LOWEST) public void incoming(LivingIncomingDamageEvent event) {
                if (event.getEntity() != first) return;
                if (stage == 1) { mutate.run(); if (scenario == 5) event.setCanceled(true); }
                if (stage == 3) event.addReductionModifier(DamageContainer.Reduction.ABSORPTION, (container, reduction) -> { mutate.run(); return reduction; });
            }
            @SubscribeEvent(priority = EventPriority.LOWEST) public void pre(LivingDamageEvent.Pre event) {
                if (stage == 2 && event.getEntity() == first) { mutate.run(); if (scenario == 5) event.setNewDamage(0); }
            }
        });
        h.assertTrue(f.cast(), "paid frozen pair is emitted before resource observer scenario " + stage + "/" + scenario);
        f.after(18, () -> {
            h.assertTrue(called[0], "requested real native observer ran at its actual contact tick");
            eq(h, hp(first), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "resource mutation rejects current native commit " + stage + "/" + scenario);
            eq(h, hp(second), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "remaining same-step and future slots cannot capture a replacement resource witness " + stage + "/" + scenario);
            if (stage == 3) near(h, first.getAbsorptionAmount(), .5, "refused native commit preserves original absorption");
            h.assertFalse(ShearWindblade.active(f.p), "poisoned emission cannot leave a reusable controller");
            f.close(); if (scenario < (stage < 3 ? 5 : 4)) resourceMutationCase(h, stage, scenario + 1); else h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void armor_toughness_enchantments_effects_and_absorption_match_native_controls(GameTestHelper h) { armorCase(h, true); }
    private static void armorCase(GameTestHelper h, boolean exact) {
        Fixture f = new Fixture(h, 2); Zombie target = f.target(1, exact), control = f.targetAt(f.eye().add(6, 0, 1), false);
        defenses(h, target); defenses(h, control); f.discount(target); Observation hit = f.observe(target), nativeHit = f.observe(control);
        f.after(2, () -> {
            BigDecimal before = hp(target); h.assertTrue(control.hurt(f.source(f.eye().add(6, 0, 0)), 5), "ordinary native player attack accepts the paired control");
            h.assertTrue(f.cast(), "near front reaches identical native reducer pipeline");
            h.assertTrue(hit.posts == 1 && nativeHit.posts == 1 && hit.armor > 0 && hit.effects > 0 && hit.enchantments > 0 && hit.absorption == .5F, "all native reducers execute once");
            h.assertTrue(hit.armor == nativeHit.armor && hit.effects == nativeHit.effects && hit.enchantments == nativeHit.enchantments && hit.absorption == nativeHit.absorption, "native reductions agree with ordinary control");
            if (exact) eq(h, hp(target), before.subtract(DamageMitigation.mitigate(n("5"), new DefenseProfile(BigDecimal.valueOf(target.getArmorValue()), BigDecimal.valueOf(target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)), 1, 4)).subtract(n(".5"))), "exact ledger receives the native mitigated decimal remainder");
            else near(h, target.getHealth(), control.getHealth(), "ordinary float HP agrees with control");
            assertSource(h, hit.source, f.p); f.close(); if (exact) armorCase(h, false); else h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 110)
    public static void held_front_and_rear_shields_obey_the_actual_travelling_front_origin(GameTestHelper h) { shieldCase(h, 0); }
    private static void shieldCase(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h, 2); Zombie target = f.target(8, mode < 2); float yaw = mode % 2 == 0 ? 180 : 0;
        shield(target, yaw); f.discount(target); Observation hit = f.observe(target); BigDecimal before = hp(target); Vec3 initial = f.eye();
        f.after(7, () -> {
            face(target, yaw); h.assertTrue(target.isBlocking(), "native shield raise delay completes before launch"); h.assertTrue(f.cast(), "future shield contact is paid");
            f.after(18, () -> {
                boolean blocked = mode % 2 == 0; h.assertTrue(hit.incoming == 1 && hit.blocked == blocked, "native facing determines shield block");
                eq(h, hp(target), blocked ? before : before.subtract(n("5")), "native shield governs exact and ordinary health");
                assertSource(h, hit.source, f.p); h.assertTrue(hit.source.getSourcePosition().z > initial.z + 2, "damage source is an actual advanced front point, not the caster eye");
                f.close(); if (mode < 3) shieldCase(h, mode + 1); else h.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void accepted_zero_and_full_absorption_keep_one_paid_attempt_without_hp_damage(GameTestHelper h) {
        for (boolean exact : List.of(true, false)) for (boolean absorption : List.of(false, true)) try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(1, exact); f.discount(target); BigDecimal before = hp(target); Observation hit = f.observe(target);
            if (absorption) { target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 2)); target.setAbsorptionAmount(10); }
            else f.hook(new Object() { @SubscribeEvent public void damage(LivingDamageEvent.Pre e) { if (e.getEntity() == target) e.setNewDamage(0); } });
            double cp = f.cp(); h.assertTrue(f.cast(), "real native zero-result attempt is emitted");
            eq(h, hp(target), before, "zero and absorbed native residuals never become exact damage"); near(h, f.cp(), cp - 8, "zero-result native success stays paid");
            h.assertTrue(hit.incoming == 1, "one native attempt occurred"); if (absorption) near(h, target.getAbsorptionAmount(), 5, "native absorption consumed exactly five points");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void established_native_iframes_reject_equal_damage_without_second_absorption(GameTestHelper h) {
        for (boolean exact : List.of(true, false)) try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(1, exact); f.discount(target); target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 2)); target.setAbsorptionAmount(10);
            Observation hit = f.observe(target); BigDecimal before = hp(target);
            h.assertTrue(target.hurt(f.p.damageSources().playerAttack(f.p), 5) && target.invulnerableTime > 10, "ordinary hit establishes genuine vanilla iframe");
            h.assertTrue(f.cast(), "controller pays before native immunity decision"); eq(h, hp(target), before, "bridge cannot bypass iframe");
            near(h, target.getAbsorptionAmount(), 5, "equal rejected hit cannot consume absorption twice"); h.assertTrue(hit.posts == 1, "no second native damage post");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void callback_reentry_and_captured_sources_never_duplicate_or_transfer_authority(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(1, true), other = f.targetAt(f.eye().add(5, 0, 1), true); f.discount(target); String wire = f.wire(); DamageSource[] source = {null};
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) {
                if (e.getEntity() != target || source[0] != null) return; source[0] = e.getSource();
                h.assertFalse(ShearWindblade.tap(f.p, wire) || f.cast(), "same and fresh input cannot reenter live emission");
                h.assertFalse(other.hurt(source[0], 5), "captured closed source cannot transfer victim authority");
            } });
            double cp = f.cp(); h.assertTrue(ShearWindblade.tap(f.p, wire) && source[0] != null, "outer emission reaches real native callback");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "recursive source misuse invalidates the current commit"); eq(h, hp(other), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "other target never receives borrowed authority");
            near(h, f.cp(), cp - 8, "reentry never duplicates payment"); ShearWindblade.abort(f.p);
            h.assertFalse(other.hurt(source[0], 5), "source cannot replay after completion");
            h.assertFalse(HighPowerCombat.attack(f.p, SKILL, other, n("5")), "generic named skill source cannot mint a windblade permit");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 160)
    public static void native_owner_lifecycle_changes_retire_pending_damage_without_refund(GameTestHelper h) { lifecycle(h, 0); }
    private static void lifecycle(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h, 2); Zombie target = f.target(14, true); double cp = f.cp(); h.assertTrue(f.cast(), "paid travel starts before owner invalidation"); near(h, f.cp(), cp - 8, "one launch payment");
        switch (mode) {
            case 0 -> AbilityStorage.get(f.p).changeCategoryClassic("electromaster");
            case 1 -> AbilityStorage.get(f.p).presets.switchTo(1);
            case 2 -> LegacySingleKeyOwnerEpoch.forget(f.p);
            case 3 -> f.p.setHealth(0);
            case 4 -> ShearWindblade.abort(f.p);
            case 5 -> AbilityStorage.remove(f.p);
            default -> ShearWindblade.remove(f.p);
        }
        f.after(18, () -> { eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "invalid owner cannot finish future native damage: " + mode); h.assertFalse(ShearWindblade.active(f.p), "lifecycle closes both blades"); f.close(); if (mode < 6) lifecycle(h, mode + 1); else h.succeed(); });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void authenticated_input_rejects_forgery_stale_epoch_replay_and_old_release(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); Zombie target = f.target(12, true); long epoch = f.epoch(); double cp = f.cp();
        h.assertFalse(ShearWindblade.tap(f.p, new HighPowerInput.Input(0, 1, f.p.getId() + 99, epoch).wire()), "wrong network actor refuses");
        h.assertFalse(ShearWindblade.tap(f.p, new HighPowerInput.Input(0, 2, f.p.getId(), epoch + 1).wire()), "wrong owner epoch refuses");
        h.assertFalse(ShearWindblade.tap(f.p, new HighPowerInput.Input(1, 3, f.p.getId(), epoch).wire()), "unbound slot refuses");
        h.assertFalse(ShearWindblade.tap(f.p, "0:0004:" + f.p.getId() + ":" + epoch), "noncanonical identity spelling refuses");
        near(h, f.cp(), cp, "forged inputs cannot debit CP"); f.inputs = 9; String wire = f.wire(); h.assertTrue(ShearWindblade.tap(f.p, wire), "fresh authenticated bound input works");
        h.assertFalse(ShearWindblade.tap(f.p, wire), "replayed input cannot create another controller");
        h.assertFalse(ShearWindblade.abort(f.p, new HighPowerInput.Input(0, 9, f.p.getId(), epoch).wire()), "older release cannot end a newer gesture");
        h.assertTrue(ShearWindblade.active(f.p) && ShearWindblade.abort(f.p, wire), "matching authenticated release retires both pending fronts");
        f.after(18, () -> { eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "released fronts cannot hit later"); h.assertFalse(ShearWindblade.tap(f.p, wire), "released identity remains replay-fenced"); h.succeed(); });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void four_tick_physical_fence_survives_abort_native_save_and_new_owner_epoch(GameTestHelper h) {
        Fixture f = new Fixture(h, 5); Zombie target = f.target(12, true); long started = h.getLevel().getGameTime();
        h.assertTrue(f.cast(), "LV5 launches with one-tick numerical cooldown"); ShearWindblade.abort(f.p);
        AbilityStorage.save(f.p); CompoundTag saved = f.p.saveWithoutId(new CompoundTag()); ShearWindblade.remove(f.p); AbilityStorage.remove(f.p); LegacySingleKeyOwnerEpoch.forget(f.p); f.p.load(saved); f.place(); f.inputs = 0;
        f.after(1, () -> { double cp = f.cp(); h.assertTrue(h.getLevel().getGameTime() - started < 4, "attempt is truly before physical deadline"); h.assertFalse(f.cast(), "new session cannot erase physical minimum"); near(h, f.cp(), cp, "premature attempt is free"); });
        f.after(5, () -> { double cp = f.cp(); h.assertTrue(f.cast(), "authentic input works after actual physical interval"); near(h, f.cp(), cp - .05, "new allowed controller pays exactly LV5 cost"); ShearWindblade.abort(f.p); eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "aborted paid fronts have no delayed attack"); h.succeed(); });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void actual_fractional_xp_uses_continuous_q_cost_damage_and_exact_three_slot_remainder(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); var state = AbilityStorage.get(f.p);
        state.addExperience(ID, ClassicRules.levelThreshold(2, SkillCatalog.levelSkillCount(state)) * .37);
        BigDecimal q = HighPowerGrowth.values(f.p).position(); h.assertTrue(q.compareTo(BigDecimal.TWO) > 0 && q.compareTo(BigDecimal.valueOf(3)) < 0, "actual native XP makes a fractional active coordinate");
        List<Zombie> targets = List.of(f.target(4, true), f.target(8, true), f.target(12, true)); double cp = f.cp(), xp = state.levelExperience;
        BigDecimal damage = HighPowerGrowth.values(f.p).damage().multiply(n("1.5")).min(CombatAmounts.CAP);
        double cost = ContinuousHighPowerGrowth.curve("8", "8", "3", ".5", ".05").value(q).doubleValue();
        int cd = ContinuousHighPowerGrowth.ceilCooldownTicks(ContinuousHighPowerGrowth.curve("30", "30", "15", "5", "1").value(q));
        h.assertTrue(cost > 3 && cost < 8 && f.cast(), "fractional q changes actual runtime economics continuously"); near(h, f.cp(), cp - cost, "actual native debit uses sampled q");
        h.assertTrue(state.cooldowns.getOrDefault(ID, 0) == cd, "only scheduling boundary ceils theoretical tick cooldown");
        var shares = ShearWindblade.receipt(f.p).shares(); eq(h, shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add), damage, "three decimal shares conserve exact 1.5D");
        eq(h, shares.get(0), shares.get(1), "first equal shares use the same division"); eq(h, shares.get(2), damage.subtract(shares.get(0).multiply(BigDecimal.TWO)), "last share alone carries exact remainder");
        f.after(18, () -> { for (int i = 0; i < 3; i++) eq(h, hp(targets.get(i)), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(shares.get(i)), "native impact spends the frozen exact fractional share"); h.assertTrue(state.levelExperience == xp, "casting creates no extra XP"); h.succeed(); });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void translated_world_coordinates_preserve_continuous_contact_order(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); f.offset = new Vec3(7.125, .375, .25); f.place();
        Zombie first = f.targetAt(f.eye().add(.8, 0, 5), true), second = f.targetAt(f.eye().add(-.8, 0, 9), true);
        h.assertTrue(f.cast(), "non-integral translated origin preserves genuine swept contacts");
        h.assertTrue(ShearWindblade.receipt(f.p).targets().equals(List.of(first.getUUID(), second.getUUID())), "contact order does not depend on absolute coordinates");
        f.after(18, () -> { for (Zombie target : List.of(first, second)) eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2).divide(BigDecimal.TWO)), "translated collision retains its exact half"); h.succeed(); });
    }

    private static BigDecimal total(int level) { return HighPowerLevel.forLevel(level).damage().multiply(n("1.5")).min(CombatAmounts.CAP); }
    private static BigDecimal n(String text) { return new BigDecimal(text); }
    private static BigDecimal hp(LivingEntity entity) { return HighPowerCombat.enabled(entity) ? n(HighPowerCombat.healthText(entity)) : new BigDecimal(Float.toString(entity.getHealth())); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) { h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected); }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-5, why + ": " + actual + " != " + expected); }
    private static void vector(GameTestHelper h, Vec3 actual, Vec3 expected, String why) { h.assertTrue(actual.distanceToSqr(expected) < 1e-10, why + ": " + actual + " != " + expected); }
    private static void face(LivingEntity target, float yaw) { target.setYRot(yaw); target.setYHeadRot(yaw); target.yBodyRot = yaw; }
    private static void shield(LivingEntity target, float yaw) { face(target, yaw); target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); target.startUsingItem(InteractionHand.OFF_HAND); }
    private static void defenses(GameTestHelper h, LivingEntity target) {
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(true)); target.setItemSlot(EquipmentSlot.HEAD, helmet);
        target.getAttribute(Attributes.ARMOR).setBaseValue(5); target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
        target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0)); target.setAbsorptionAmount(.5F);
    }
    private static void assertSource(GameTestHelper h, DamageSource source, ServerPlayer caster) {
        h.assertTrue(source != null && source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypeTags.IS_PROJECTILE)
                && source.getEntity() == caster && source.getDirectEntity() == caster && source.sourcePositionRaw() != null,
                "actual player attack retains native attribution and an explicit front origin");
        h.assertFalse(source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                || source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_COOLDOWN)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY), "windblade grants no protection bypass tags");
    }
    public static final class Discount {
        final LivingEntity target; Discount(LivingEntity target) { this.target = target; }
        @SubscribeEvent(priority = EventPriority.HIGHEST) public void skill(SkillAttackEvent event) { if (event.target == target && event.skill.equals(SKILL)) event.amount = 5; }
    }
    public static final class Observation {
        final LivingEntity target; DamageSource source; int incoming, posts, skills; boolean blocked;
        float damage, armor, effects, enchantments, absorption, blockedDamage;
        Observation(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void skill(SkillAttackEvent event) { if (event.target == target && event.skill.equals(SKILL)) skills++; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) { if (event.getEntity() == target) { incoming++; source = event.getSource(); } }
        @SubscribeEvent public void shield(LivingShieldBlockEvent event) { if (event.getEntity() == target) { blocked = event.getBlocked(); blockedDamage = event.getBlockedDamage(); } }
        @SubscribeEvent public void post(LivingDamageEvent.Post event) {
            if (event.getEntity() != target) return; posts++; damage = event.getNewDamage();
            armor = event.getReduction(DamageContainer.Reduction.ARMOR); effects = event.getReduction(DamageContainer.Reduction.MOB_EFFECTS);
            enchantments = event.getReduction(DamageContainer.Reduction.ENCHANTMENTS); absorption = event.getReduction(DamageContainer.Reduction.ABSORPTION);
        }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final ServerPlayer p; final EmbeddedChannel channel; final Vec3 anchor;
        Vec3 offset = Vec3.ZERO;
        final List<net.minecraft.world.scores.PlayerTeam> teams = new ArrayList<>();
        final List<Entity> entities = new ArrayList<>(); final List<Object> hooks = new ArrayList<>();
        final Map<BlockPos, BlockState> previousBlocks = new LinkedHashMap<>(); long inputs; boolean closed;
        Fixture(GameTestHelper h, int level) {
            this.h = h; anchor = h.absoluteVec(new Vec3(8.5, 3, 3.5));
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "SW" + UUID.randomUUID().toString().substring(0, 10)), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); acknowledgeLogin();
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); place();
            h.assertTrue(HighPowerCombat.enroll(p, Math.max(2, level)), "genuine connected caster enrollment");
            var state = AbilityStorage.get(p); state.selectCategory("vecmanip"); state.setLevel(Math.max(2, level)); state.learn("deflection_strike"); state.learn(ID); state.activated = true; state.overloadFine = true;
            h.assertTrue(state.presets.edit(0, 0, ID, state::learned), "real learned Vector root input binding"); HighPowerResources.initializeBudget(p);
            // Explicit native level rollback may lower active q; raising the unlock never restores it.
            if (level == 1) { state.setLevel(1); HighPowerGrowth.values(p); state.setLevel(2); HighPowerGrowth.values(p); state.activated = true; state.overloadFine = true; }
            hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5));
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) { }
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        void acknowledgeLogin() {
            channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet;
            while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet); }
        }
        void place() { p.moveTo(anchor.x + offset.x, anchor.y + offset.y, anchor.z + offset.z, 0, 0); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        Vec3 eye() { return p.getEyePosition(); }
        void center(LivingEntity entity, Vec3 point) { entity.setPos(point.add(0, -entity.getBbHeight() / 2, 0)); }
        void add(LivingEntity entity, Vec3 point) {
            if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
            entity.setNoGravity(true); entity.setSilent(true); center(entity, point);
            h.assertTrue(h.getLevel().addFreshEntity(entity), "native body admitted to actual level"); entities.add(entity);
        }
        Zombie target(double distance, boolean exact) { return targetAt(eye().add(0, 0, distance), exact); }
        UUID uuid(long order) { return new UUID(p.getUUID().getMostSignificantBits(), order); }
        Zombie targetAt(Vec3 point, boolean exact) { return targetAt(point, exact, UUID.randomUUID()); }
        Zombie targetAt(Vec3 point, boolean exact, UUID identity) {
            Zombie mob = EntityType.HUSK.create(h.getLevel()); mob.setUUID(identity); mob.getAttribute(Attributes.ARMOR).setBaseValue(0);
            mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1); add(mob, point);
            if (exact) h.assertTrue(HighPowerCombat.enroll(mob, 5), "declared exact native hostile enrollment"); return mob;
        }
        double cp() { return HighPowerResources.cp(p); }
        String wire() { return new HighPowerInput.Input(0, ++inputs, p.getId(), LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p))).wire(); }
        boolean cast() { return ShearWindblade.tap(p, wire()); }
        long epoch() { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        void after(int ticks, Runnable action) { h.runAfterDelay(ticks, () -> { if (!closed) action.run(); }); }
        Zombie small(Vec3 point) { Zombie mob = targetAt(point, true); mob.getAttribute(Attributes.SCALE).setBaseValue(.2); mob.refreshDimensions(); center(mob, point); return mob; }
        void wall(double distance) { for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) block(eye().add(x, y, distance)); }
        DamageSource source(Vec3 origin) { return new DamageSource(p.damageSources().playerAttack(p).typeHolder(), p, p, origin); }
        void block(Vec3 point) { BlockPos position = BlockPos.containing(point); previousBlocks.putIfAbsent(position, h.getLevel().getBlockState(position)); h.getLevel().setBlockAndUpdate(position, Blocks.STONE.defaultBlockState()); }
        void assertBlocks() { for (BlockPos position : previousBlocks.keySet()) h.assertTrue(h.getLevel().getBlockState(position).is(Blocks.STONE), "strike never destroys or transforms terrain"); }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void discount(LivingEntity target) { hook(new Discount(target)); }
        Observation observe(LivingEntity target) { Observation result = new Observation(target); hook(result); return result; }
        @Override public void close() {
            if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister);
            ShearWindblade.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.server.getPlayerList().remove(p);
            teams.forEach(p.serverLevel().getScoreboard()::removePlayerTeam); entities.forEach(Entity::discard); previousBlocks.forEach((position, state) -> h.getLevel().setBlockAndUpdate(position, state)); channel.finishAndReleaseAll();
        }
    }
}

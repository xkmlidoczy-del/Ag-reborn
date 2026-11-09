package cn.academy.port.gametest;

import cn.academy.port.highpower.core.CirculationReliefRules;
import cn.academy.port.*;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.develop.*;
import cn.academy.port.highpower.*;
import cn.academy.port.machine.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW432: real authenticated toggles, native damage/mitigation and genuine player ticks. */
@GameTestHolder("academy_circulation_relief_runtime")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerCirculationReliefRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_circulation_relief", ID = "circulation_relief";
    private HighPowerCirculationReliefRuntimeTests() {}

    @SubscribeEvent public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int dimension : new int[]{40, 12, 40}) size.add(IntTag.valueOf(dimension));
        for (int axis = 0; axis < 3; axis++) position.add(IntTag.valueOf(0));
        cell.put("pos", position); cell.putInt("state", 0); blocks.add(cell); air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_circulation_relief_runtime", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void lv3_catalog_and_finite_native_learning_preserve_existing_resources_and_classic_counts(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 3, false);
        f.ready(() -> {
            var state = AbilityStorage.get(p); var skill = SkillCatalog.find(HighPowerCategories.VECMANIP, ID).orElseThrow();
            h.assertTrue(skill.level() == 3 && skill.requirements().equals(List.of(new SkillCatalog.Requirement(InertialAnchor.ID, 0)))
                    && HighPowerSkills.CIRCULATION_RELIEF_PROPOSAL == 432, "canonical NEW432 LV3 follows learned NEW422 at zero mastery");
            h.assertTrue(SkillCatalog.ALL.size() == 50 && cn.academy.port.preset.PresetSkills.IMPLEMENTED.size() == 35,
                    "the classic catalog and native presets are unchanged");
            h.assertTrue(SkillCatalog.levelSkillCount(state) == SkillCatalog.ALL.stream().filter(s -> s.category().equals("vecmanip") && s.level() == 3 && s.controllable()).count(),
                    "NEW432 does not change the classic LV3 experience denominator");
            state.unlearn(InertialAnchor.ID); int stimulations = ClassicRules.learningStimulations(skill.level());
            int ticks = DeveloperType.NORMAL.ticksPerStimulation() * stimulations;
            double cost = DeveloperType.NORMAL.actualConsumption(stimulations);
            var machine = f.normal(p, cost);
            h.assertFalse(f.learnRequest(p, ID), "learned other branches cannot replace NEW422");
            near(h, machine.battery().getEnergy(), cost, "missing exact parent consumes no IF");
            f.learn(p, InertialAnchor.ID); h.assertTrue(state.exp(InertialAnchor.ID) == 0, "genuinely paid NEW422 parent has zero mastery");
            state.setLevel(2); machine = f.normal(p, cost);
            h.assertFalse(f.learnRequest(p, ID), "paid NEW422 cannot bypass integer LV3");
            state.setLevel(3); var portable = f.portable(p, cost);
            h.assertFalse(DevelopmentController.startSkill(p, ID), "Portable cannot develop the LV3 skill");
            near(h, portable.energy(), cost, "wrong developer tier consumes no IF");
            h.assertFalse(state.presets.edit(0, 0, ID, state::learned), "unlearned circulation cannot bind");
            f.shield(p, n("17.25")); f.hit(p, p.damageSources().magic(), 1); f.reserve(p);
            HighPowerGrowth.values(p); p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", 37);
            CompoundTag combat = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();
            CompoundTag resources = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy(); double xp = state.levelExperience;
            machine = f.normal(p, cost); h.assertTrue(f.learnRequest(p, ID), "actual bound Normal machine starts canonical NEW432 development");
            f.develop(p, ticks - 1); h.assertFalse(state.learned(ID), "the final physical paid tick remains mandatory");
            near(h, machine.battery().getEnergy(), DeveloperType.NORMAL.energyPerTick(), "one exact native payment remains");
            f.develop(p, 1);
            h.assertTrue(state.learned(ID) && state.exp(ID) == 0 && state.levelExperience == xp && DevelopmentController.process(p).state() == DevelopmentProcess.State.DONE,
                    "only complete finite native learning grants the skill, with no XP");
            near(h, machine.battery().getEnergy(), 0, "all native machine IF was consumed");
            h.assertTrue(combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)) && resources.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),
                    "learning does not refill HP, base S, retained reserve, CP or growth");
            h.assertTrue(state.presets.edit(0, 0, ID, state::learned), "paid NEW432 binds the ordinary input slot"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void nearest_representable_side_edges_protect_and_adjacent_outward_vectors_front_and_rear_do_not(GameTestHelper h) {
        sectors(h, new double[]{60, 120, 240, 300, 60, 120, 240, 300, 59.99, 120.01, 239.99, 300.01, 0, 180},
                new boolean[]{true, true, true, true, false, false, false, false, false, false, false, false, false, false});
    }
    private static void sectors(GameTestHelper h, double[] angles, boolean[] expected) {
        Fixture f = new Fixture(h); List<ServerPlayer> players = new ArrayList<>();
        for (int i = 0; i < angles.length; i++) players.add(f.player(5 + i % 5 * 7, 5 + i / 5 * 12));
        f.ready(() -> {
            for (int i = 0; i < angles.length; i++) {
                ServerPlayer p = players.get(i); f.arm(p, "20"); BigDecimal health = hp(p); double angle = Math.toRadians(angles[i]);
                Vec3 origin = p.position().add(Math.sin(angle) * 2, 0, Math.cos(angle) * 2);
                if (i < 8) {
                    origin = nearestSideEdge(p, origin);
                    if (i >= 4) origin = new Vec3(Math.nextAfter(origin.x, p.getX()), origin.y, origin.z);
                    h.assertTrue(exactSide(p, origin) == expected[i], "actual represented world coordinates straddle the strict edge");
                }
                Zombie attacker = f.zombie(origin);
                h.assertTrue(f.hit(p, p.damageSources().mobAttack(attacker), 5), "genuine native hit at " + angles[i]);
                eq(h, shield(p), n(expected[i] ? "15" : "20"), "closed side sector at " + angles[i]);
                eq(h, hp(p), expected[i] ? health : health.subtract(n("5")), "only admitted side residual is absorbed");
                h.assertTrue(CirculationRelief.remainingHits(p) == (expected[i] ? 3 : 4), "only positive absorption consumes rolling quota");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void exact_four_meter_native_source_range_is_closed_and_just_outside_is_free(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer near = f.player(8, 8), far = f.player(24, 8);
        f.ready(() -> {
            for (ServerPlayer p : List.of(near, far)) {
                f.arm(p, "20"); BigDecimal health = hp(p); double distance = p == near ? 4 : 4.001;
                Zombie attacker = f.zombie(p.position().add(distance, 0, 0)); f.hit(p, p.damageSources().mobAttack(attacker), 5);
                eq(h, shield(p), n(p == near ? "15" : "20"), "exact source distance " + distance);
                eq(h, hp(p), p == near ? health : health.subtract(n("5")), "outside four meters keeps native HP damage");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void plain_arrow_native_collision_uses_arrow_contact_and_leaves_motion_identical_to_control(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8), control = f.player(24, 8);
        f.ready(() -> {
            f.arm(p, "20"); Zombie shooter = f.zombie(p.position().add(7, 0, 0)), other = f.zombie(control.position().add(7, 0, 0));
            Arrow arrow = f.arrow(p, shooter, null), nativeArrow = f.arrow(control, other, null);
            Observation protectedHit = f.observe(p), ordinaryHit = f.observe(control); BigDecimal health = hp(p);
            nativeArrow.tick(); arrow.tick();
            h.assertTrue(arrow.isRemoved() && nativeArrow.isRemoved() && protectedHit.posts == 1 && ordinaryHit.posts == 1,
                    "both actual native Arrow ticks collide, apply damage and retire normally");
            h.assertTrue(protectedHit.damage > 0 && protectedHit.damage == ordinaryHit.damage, "arrow damage remains entirely native");
            eq(h, shield(p), n("20").subtract(decimal(protectedHit.damage)), "direct arrow contact within four meters owns one absorption");
            eq(h, hp(p), health, "base S protects the actual post-native arrow remainder");
            vector(h, p.getDeltaMovement(), control.getDeltaMovement(), "circulation adds no motion or turn to the native arrow hit");
            h.assertTrue(CirculationRelief.remainingHits(p) == 3, "one native projectile hit uses one quota entry"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_armor_resistance_enchantment_and_absorption_precede_partial_base_shield(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8), control = f.player(24, 8);
        f.ready(() -> {
            defenses(h, p); defenses(h, control); f.arm(p, "2"); Observation a = f.observe(p), b = f.observe(control); BigDecimal health = hp(p);
            for (ServerPlayer target : List.of(control, p)) f.hit(target, target.damageSources().mobAttack(f.side(target)), 20);
            h.assertTrue(a.posts == 1 && b.posts == 1 && a.damage > 2 && a.armor > 0 && a.effects > 0 && a.enchantments > 0 && a.absorption == .5F,
                    "actual native damage runs every mitigation stage before circulation");
            h.assertTrue(a.damage == b.damage && a.armor == b.armor && a.effects == b.effects && a.enchantments == b.enchantments && a.absorption == b.absorption,
                    "native reductions execute exactly once with the same control result");
            eq(h, shield(p), BigDecimal.ZERO, "only two available S are spent");
            eq(h, hp(p), health.subtract(decimal(a.damage).subtract(n("2"))), "exact residual above S reaches HP once");
            vector(h, p.getDeltaMovement(), control.getDeltaMovement(), "native knockback stays authoritative after all mitigation"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void partial_exhaustion_spends_last_base_s_without_accessing_saved_reserve_or_refilling(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.arm(p, "2.25"); CompoundTag reserve = f.reserve(p); BigDecimal health = hp(p); Zombie a = f.side(p);
            f.hit(p, p.damageSources().mobAttack(a), 5);
            eq(h, shield(p), BigDecimal.ZERO, "exact final 2.25 S is spent one to one"); eq(h, hp(p), health.subtract(n("2.75")), "excess 2.75 reaches HP");
            h.assertTrue(CirculationRelief.remainingHits(p) == 3, "positive partial absorption counts once");
            p.invulnerableTime = 0; f.hit(p, p.damageSources().mobAttack(a), 5);
            eq(h, hp(p), health.subtract(n("7.75")), "next exhausted hit keeps full ordinary HP damage"); eq(h, shield(p), BigDecimal.ZERO, "zero pool cannot refill");
            h.assertTrue(CirculationRelief.remainingHits(p) == 3 && reserve.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound(MagneticDomainReserve.KEY)),
                    "zero absorption is free and old-category reserve stays inaccessible and exact"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void full_absorption_survives_observed_native_temporary_zero_hp_without_resurrection(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.arm(p, "100"); BigDecimal health = hp(p); boolean[] temporaryZero = {false}; int[] deaths = {0};
            f.hook(new Object() {
                @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) { if (e.getEntity() == p) temporaryZero[0] = p.getHealth() == 0; }
                @SubscribeEvent public void death(LivingDeathEvent e) { if (e.getEntity() == p) deaths[0]++; }
            });
            h.assertTrue(f.hit(p, p.damageSources().mobAttack(f.side(p)), 40), "real native forty-point hit completes");
            h.assertTrue(temporaryZero[0], "actual native Post observes temporary normalized HP zero before exact settlement");
            eq(h, shield(p), n("60"), "forty positive residual spends forty existing S exactly once");
            eq(h, hp(p), health, "genuine capture preserves preexisting exact HP through native temporary zero");
            h.assertTrue(p.isAlive() && deaths[0] == 0 && CirculationRelief.active(p), "successful absorption prevents native death without any rescue or resurrection");
            h.assertTrue(CirculationRelief.remainingHits(p) == 3, "temporary native HP zero does not lose or duplicate quota"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void true_lethal_partial_absorption_keeps_spent_s_and_quota_and_clears_dead_controller(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.arm(p, "2"); p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health", "1");
            CompoundTag reserve = f.reserve(p); int[] deaths = {0};
            f.hook(new Object() { @SubscribeEvent public void death(LivingDeathEvent e) { if (e.getEntity() == p) deaths[0]++; } });
            h.assertTrue(f.hit(p, p.damageSources().mobAttack(f.side(p)), 5), "real native hit permits the true lethal residual");
            eq(h, shield(p), BigDecimal.ZERO, "true death retains the already committed two-S debit"); eq(h, hp(p), BigDecimal.ZERO, "three residual damage is lethal to one exact HP");
            h.assertTrue(!p.isAlive() && deaths[0] == 1 && !CirculationRelief.active(p), "ordinary native death runs once and removes the controller");
            h.assertTrue(CirculationRelief.remainingHits(p) == 3, "true death preserves the committed positive-absorption entry");
            CompoundTag quota = p.getPersistentData().getCompound(CirculationRelief.QUOTA_KEY).copy();
            CirculationRelief.tick(p); HighPowerCombat.tick(new EntityTickEvent.Post(p));
            eq(h, hp(p), BigDecimal.ZERO, "later dead-owner callbacks cannot resurrect exact HP"); eq(h, shield(p), BigDecimal.ZERO, "dead cleanup cannot refund spent S");
            h.assertTrue(quota.equals(p.getPersistentData().getCompound(CirculationRelief.QUOTA_KEY)) && reserve.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound(MagneticDomainReserve.KEY)),
                    "death teardown preserves rolling debt and inaccessible reserve"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_totem_rescues_lethal_residual_once_without_refunding_s_or_quota(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8), control = f.player(24, 8);
        f.ready(() -> {
            f.arm(p, "2"); f.shield(control, n("2"));
            for (ServerPlayer target : List.of(p, control)) {
                target.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health", "1");
                target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            }
            CompoundTag reserve = f.reserve(p);
            h.assertTrue(f.hit(control, control.damageSources().mobAttack(f.side(control)), 3), "ordinary control receives the same lethal residual");
            h.assertTrue(f.hit(p, p.damageSources().mobAttack(f.side(p)), 5), "protected hit spends two S before three lethal residual");
            HighPowerCombat.tick(new EntityTickEvent.Post(control)); HighPowerCombat.tick(new EntityTickEvent.Post(p));
            h.assertTrue(p.isAlive() && control.isAlive() && p.getOffhandItem().isEmpty() && control.getOffhandItem().isEmpty(), "both ordinary native flows consume exactly one actual held totem");
            h.assertTrue(hp(p).signum() > 0, "native totem restores living exact HP"); eq(h, hp(p), hp(control), "rescue HP matches the ordinary residual control");
            near(h, p.getAbsorptionAmount(), control.getAbsorptionAmount(), "totem absorption remains native");
            for (var effect : List.of(MobEffects.REGENERATION, MobEffects.ABSORPTION, MobEffects.FIRE_RESISTANCE)) {
                h.assertTrue(p.hasEffect(effect) && control.hasEffect(effect) && p.getEffect(effect).getAmplifier() == control.getEffect(effect).getAmplifier(), "ordinary native totem effect remains authoritative");
            }
            eq(h, shield(p), BigDecimal.ZERO, "native rescue never refunds the committed S"); eq(h, shield(control), n("2"), "ordinary control spends no base S");
            h.assertTrue(CirculationRelief.remainingHits(p) == 3, "native totem cannot erase a committed rolling entry");
            h.assertTrue(reserve.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound(MagneticDomainReserve.KEY)), "totem cannot refill or borrow old reserve");
            CirculationRelief.tick(p); h.assertFalse(CirculationRelief.active(p), "exhausted stance ends normally after native rescue");
            eq(h, shield(p), BigDecimal.ZERO, "post-rescue cleanup preserves zero S"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_iframes_admit_only_damage_difference_without_a_second_impulse(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.arm(p, "20"); Zombie a = f.side(p); DamageSource source = p.damageSources().mobAttack(a); Observation hit = f.observe(p); BigDecimal health = hp(p);
            h.assertTrue(f.hit(p, source, 5), "initial native damage accepts"); Vec3 motion = p.getDeltaMovement();
            h.assertFalse(f.hit(p, source, 5), "equal native iframe hit rejects"); h.assertTrue(f.hit(p, source, 8), "stronger hit accepts only native differential");
            eq(h, shield(p), n("12"), "five plus native difference three spends exactly eight S"); eq(h, hp(p), health, "existing shield covers both native residuals");
            vector(h, p.getDeltaMovement(), motion, "differential damage does not invent an impulse");
            h.assertTrue(hit.posts == 2 && hit.knockbacks == 1 && CirculationRelief.remainingHits(p) == 2, "only two positive residuals count"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void cancellation_zero_residual_and_full_native_absorption_are_free(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> players = List.of(f.player(6, 8), f.player(18, 8), f.player(30, 8));
        f.ready(() -> {
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer p = players.get(i); f.arm(p, "20"); BigDecimal health = hp(p);
                if (i == 2) { p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 2)); p.setAbsorptionAmount(10); }
                else f.hook(new DamageGate(p, i));
                f.hit(p, p.damageSources().mobAttack(f.side(p)), 5);
                eq(h, shield(p), n("20"), "canceled or zero-native-residual hit is free"); eq(h, hp(p), health, "native zero result stays authoritative");
                h.assertTrue(CirculationRelief.remainingHits(p) == 4 && AbilityStorage.get(p).exp(ID) == 0, "zero residual grants no quota debit or skill experience");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void full_native_held_shield_in_front_side_overlap_preserves_base_s_and_quota(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); p.startUsingItem(InteractionHand.OFF_HAND);
            h.runAfterDelay(7, () -> {
                f.place(p); h.assertTrue(p.isBlocking(), "actual native shield delay elapsed"); f.arm(p, "20");
                double angle = Math.toRadians(75); Zombie a = f.zombie(p.position().add(2 * Math.sin(angle), 0, 2 * Math.cos(angle)));
                h.assertTrue(exactSide(p, a.position()), "held-shield source is independently inside the circulation side sector");
                BigDecimal health = hp(p); Observation hit = f.observe(p);
                h.assertFalse(f.hit(p, p.damageSources().mobAttack(a), 5), "native held shield blocks the eligible front-side overlap");
                h.assertTrue(hit.blocked && hit.blockedDamage == 5, "real native shield event owns all blocked damage");
                eq(h, shield(p), n("20"), "full block spends no base S"); eq(h, hp(p), health, "full block preserves HP");
                h.assertTrue(CirculationRelief.remainingHits(p) == 4, "full block consumes no rolling entry"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void four_positive_absorptions_and_fifth_hp_hit_share_a_true_twenty_tick_rolling_window(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.arm(p, "100"); Zombie a = f.side(p); BigDecimal health = hp(p);
            for (int i = 0; i < 5; i++) { p.invulnerableTime = 0; f.hit(p, p.damageSources().mobAttack(a), 5); }
            eq(h, shield(p), n("80"), "only four real positive contacts spend S"); eq(h, hp(p), health.subtract(n("5")), "fifth native contact reaches HP");
            h.assertTrue(CirculationRelief.remainingHits(p) == 0, "rolling quota is exhausted");
            for (int i = 0; i < 100; i++) { CirculationRelief.tick(p); CirculationRelief.playerTick(new PlayerTickEvent.Post(p)); }
            h.assertTrue(CirculationRelief.remainingHits(p) == 0, "duplicate maintenance and same-tick player callbacks cannot age quota");
            PlayerPosts clock = f.posts(p);
            clock.at(19, () -> { h.assertTrue(CirculationRelief.remainingHits(p) == 0, "nineteen completed native PlayerTick.Post callbacks cannot replenish the window"); eq(h, shield(p), n("80"), "waiting never refills S"); });
            clock.at(20, () -> { h.assertTrue(CirculationRelief.remainingHits(p) == 4, "twenty completed native PlayerTick.Post callbacks retire all four entries"); eq(h, shield(p), n("80"), "quota renewal never restores resources"); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void staggered_positive_contacts_expire_individually_at_twenty_and_twenty_five_ticks(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.arm(p, "100"); Zombie a = f.side(p); f.hit(p, p.damageSources().mobAttack(a), 5);
            PlayerPosts clock = f.posts(p);
            clock.at(5, () -> { f.place(p); a.setPos(p.position().add(2, 0, 0)); p.invulnerableTime = 0;
                for (int i = 0; i < 3; i++) { p.invulnerableTime = 0; f.hit(p, p.damageSources().mobAttack(a), 5); }
                h.assertTrue(CirculationRelief.remainingHits(p) == 0, "second cohort fills the rolling window"); });
            clock.at(19, () -> h.assertTrue(CirculationRelief.remainingHits(p) == 0, "earliest entry remains for nineteen completed native Posts"));
            clock.at(20, () -> h.assertTrue(CirculationRelief.remainingHits(p) == 1, "only first cohort expires at twenty completed native Posts"));
            clock.at(24, () -> h.assertTrue(CirculationRelief.remainingHits(p) == 1, "later cohort cannot inherit the first expiry"));
            clock.at(25, () -> { h.assertTrue(CirculationRelief.remainingHits(p) == 4, "later cohort gets its own full twenty native Posts"); eq(h, shield(p), n("80"), "staggered expiry changes no S"); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void release_toggle_and_native_reload_preserve_quota_and_inactive_ticks_keep_aging(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.arm(p, "100"); Zombie a = f.side(p); for (int i = 0; i < 4; i++) { p.invulnerableTime = 0; f.hit(p, p.damageSources().mobAttack(a), 5); }
            h.assertTrue(CirculationRelief.release(p, f.held.get(p)) && CirculationRelief.active(p), "release acknowledges input without ending the toggle");
            h.assertTrue(f.start(p) && !CirculationRelief.active(p), "fresh authenticated press ends the toggle");
            h.assertFalse(f.start(p), "same-tick restart cannot bypass the physical fence");
            CompoundTag quota = p.getPersistentData().getCompound(CirculationRelief.QUOTA_KEY).copy(); BigDecimal health = hp(p);
            f.reload(p); h.assertTrue(quota.equals(p.getPersistentData().getCompound(CirculationRelief.QUOTA_KEY)), "native save/load preserves every remaining quota tick");
            h.assertFalse(CirculationRelief.active(p), "reload never silently restores a paid stance");
            eq(h, shield(p), n("80"), "toggle and reload never refill S"); eq(h, hp(p), health, "toggle and reload never heal");
            PlayerPosts clock = f.posts(p);
            clock.at(19, () -> h.assertTrue(CirculationRelief.remainingHits(p) == 0, "inactive reloaded actor still owes nineteen completed native Posts"));
            clock.at(20, () -> { h.assertTrue(CirculationRelief.remainingHits(p) == 4, "genuine inactive owner Posts retire the quota normally"); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void legacy_normal_past_and_future_clock_save_fixtures_rebase_only_remaining_physical_debt(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> players = List.of(f.player(6, 8), f.player(18, 8), f.player(30, 8)); int[] completed = {0};
        f.ready(() -> {
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer p = players.get(i); f.arm(p, "20"); f.hit(p, p.damageSources().mobAttack(f.side(p)), 5);
                h.assertTrue(f.start(p), "actual fresh press retires the original paid toggle");
                long actual = f.controllerTime(p), observed = i == 0 ? actual : i == 1 ? 0 : actual + 2007;
                long ownClock = i == 2 ? actual + 901 : observed + 1;
                // These are explicitly old-format decoder fixtures. The future observation
                // reproduces a freeze/restart clock-origin mismatch; this is not a process restart.
                f.legacyPayment(p, observed, 7, 10, ownClock); f.reload(p);
                CompoundTag quota = p.getPersistentData().getCompound(CirculationRelief.QUOTA_KEY).copy(); double cp = HighPowerResources.cp(p); BigDecimal health = hp(p);
                h.assertFalse(f.start(p), "saved ten-tick restart debt cannot be skipped by any current clock origin");
                CompoundTag mapped = p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY).copy();
                h.assertTrue(mapped.getInt("version") == 2 && mapped.getInt("remaining") == 7 && mapped.getInt("restart") == 10,
                        "v1 migration preserves deadline-minus-observed, granting no apparent offline elapsed credit");
                h.assertTrue(mapped.getLong("resourceUntil") == ownClock, "migration captures only the exact own saved CP writer clock");
                for (int reload = 0; reload < 3; reload++) {
                    f.reload(p); h.assertFalse(f.start(p), "repeated reconnect cannot discard payment debt");
                    CirculationRelief.tick(p); CirculationRelief.playerTick(new PlayerTickEvent.Post(p));
                    h.assertTrue(mapped.equals(p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY)), "same-tick reload and maintenance cannot reset or age mapped debt");
                    h.assertTrue(quota.equals(p.getPersistentData().getCompound(CirculationRelief.QUOTA_KEY)), "repeated reload cannot reset real positive-hit quota");
                }
                near(h, HighPowerResources.cp(p), cp, "all premature reconnect attempts are free"); eq(h, shield(p), n("15"), "reload cannot refill spent S"); eq(h, hp(p), health, "reload cannot heal");
                PlayerPosts clock = f.posts(p);
                clock.at(9, () -> {
                    CompoundTag payment = p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY);
                    h.assertTrue(payment.getInt("remaining") == 0 && payment.getInt("restart") == 1, "only nine completed native Posts elapsed");
                    double before = HighPowerResources.cp(p); h.assertFalse(f.start(p), "one remaining native restart tick still refuses"); near(h, HighPowerResources.cp(p), before, "last early retry remains free");
                });
                clock.at(10, () -> {
                    h.assertTrue(p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY).getInt("restart") == 0, "exactly ten genuine Posts exhaust saved debt");
                    double before = HighPowerResources.cp(p), cost = cn.academy.port.highpower.core.VectorParryRules.activationCost(HighPowerGrowth.values(p).position());
                    h.assertTrue(f.start(p), "old normal/past/future clock records can reactivate at the exact physical boundary");
                    near(h, HighPowerResources.cp(p), before - cost, "reactivation pays a fresh approved CP cost");
                    eq(h, shield(p), n("15"), "recovered activation does not refill S"); eq(h, hp(p), health, "recovered activation does not heal");
                    h.assertTrue(CirculationRelief.remainingHits(p) == 3 && p.getPersistentData().getCompound(CirculationRelief.QUOTA_KEY).getIntArray("remaining")[0] == 10,
                            "old positive absorption still owes its other ten genuine ticks");
                    h.assertTrue(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_" + ID) == p.serverLevel().getGameTime() + 1,
                            "only the saved matching writer clock translates to the current native world origin");
                    if (++completed[0] == players.size()) f.succeed();
                });
            }
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void zero_legacy_debt_and_exact_v2_restart_expiry_allow_only_fresh_paid_activation(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer expired = f.player(8, 8), edge = f.player(24, 8);
        f.ready(() -> {
            long old = f.controllerTime(expired) + 4000; f.legacyPayment(expired, old, 0, 0, expired.serverLevel().getGameTime() + 3000); f.reload(expired);
            double before = HighPowerResources.cp(expired), cost = cn.academy.port.highpower.core.VectorParryRules.activationCost(HighPowerGrowth.values(expired).position());
            h.assertTrue(f.start(expired), "a valid zero-debt old clock receipt is immediately usable on the current native actor");
            near(h, HighPowerResources.cp(expired), before - cost, "zero saved debt never supplies a free activation");
            f.arm(edge, "20"); f.hit(edge, edge.damageSources().mobAttack(f.side(edge)), 5); h.assertTrue(f.start(edge), "fresh press turns off the live stance");
            f.reload(edge); h.assertFalse(CirculationRelief.active(edge), "native v2 reload never restores the old toggle");
            PlayerPosts clock = f.posts(edge);
            clock.at(3, () -> { double cp = HighPowerResources.cp(edge); h.assertFalse(f.start(edge), "three actual Posts cannot erase a four-tick restart fence"); near(h, HighPowerResources.cp(edge), cp, "expiry-minus-one attempt is free"); });
            clock.at(4, () -> {
                double cp = HighPowerResources.cp(edge), activation = cn.academy.port.highpower.core.VectorParryRules.activationCost(HighPowerGrowth.values(edge).position());
                h.assertTrue(f.start(edge), "the exact fourth native Post permits a new paid toggle"); near(h, HighPowerResources.cp(edge), cp - activation, "exact expiry charges once");
                eq(h, shield(edge), n("15"), "exact expiry never refills base S");
                h.assertTrue(CirculationRelief.remainingHits(edge) == 3, "restarting a v2 payment cannot reset rolling absorption quota"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void malformed_payment_versions_types_ranges_and_missing_legacy_writer_fail_closed_after_native_load(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> players = new ArrayList<>(); for (int i = 0; i < 12; i++) players.add(f.player(5 + i % 4 * 9, 6 + i / 4 * 12));
        f.ready(() -> {
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer p = players.get(i); long own = p.serverLevel().getGameTime() + 800;
                CompoundTag value = f.v2Payment(p, 0, 0, own);
                switch (i) {
                    case 0 -> value.putInt("version", 99);
                    case 1 -> value.putInt("remaining", 21);
                    case 2 -> value.putInt("restart", -1);
                    case 3 -> value.putLong("receipt", -1);
                    case 4 -> value.putLong("remaining", 0);
                    case 5 -> value.putLong("resourceUntil", -1);
                    case 6 -> value.putBoolean("unexpected", true);
                    case 7 -> { f.legacyPayment(p, 100, 21, 0, 101); value = p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY).copy(); }
                    case 8 -> { f.legacyPayment(p, 100, 0, 0, 101); value = p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY).copy(); value.putString("observed", "100"); }
                    case 9 -> { f.legacyPayment(p, 100, 0, 0, 101); value = p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY).copy(); p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).remove("next_" + ID); }
                    case 10 -> { f.legacyPayment(p, 100, 0, 0, 102); value = p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY).copy(); }
                    default -> { }
                }
                p.getPersistentData().put(CirculationRelief.PAYMENT_KEY, value.copy()); f.reload(p);
                double cp = HighPowerResources.cp(p); BigDecimal health = hp(p), pool = shield(p); CompoundTag budget = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy();
                if (i == 11) { h.assertTrue(f.start(p), "otherwise identical canonical zero-debt v2 control really activates"); continue; }
                h.assertFalse(f.start(p), "malformed old/new payment refuses after actual native NBT load " + i);
                h.assertFalse(CirculationRelief.active(p), "malformed persistence cannot create protection"); near(h, HighPowerResources.cp(p), cp, "malformed refusal never pays");
                h.assertTrue(value.equals(p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY)), "malformed persistence is not replaced with an empty usable fence");
                h.assertTrue(budget.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)), "malformed refusal never translates an untrusted resource writer");
                eq(h, shield(p), pool, "malformed persistence cannot refill S"); eq(h, hp(p), health, "malformed persistence cannot heal");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_callback_foreign_writer_mutation_refuses_reentrant_restart_without_overwriting_or_refund(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.shield(p, n("17.25")); long certified = p.serverLevel().getGameTime() + 500, foreign = certified + 99;
            p.getPersistentData().put(CirculationRelief.PAYMENT_KEY, f.v2Payment(p, 0, 0, certified)); f.reload(p);
            CompoundTag payment = p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY).copy(); double cp = HighPowerResources.cp(p); BigDecimal health = hp(p); boolean[] called = {false};
            f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) {
                if (e.getEntity() != p || called[0]) return; called[0] = true;
                p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putLong("next_" + ID, foreign);
                h.assertFalse(f.start(p), "actual native callback cannot promote a foreign writer into a saved-clock translation");
                h.assertTrue(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_" + ID) == foreign, "refused reentry preserves the callback's own clock value");
            } });
            f.hit(p, p.damageSources().magic(), 1);
            h.assertTrue(called[0] && !CirculationRelief.active(p), "genuine native Post performed the refused authenticated reentry");
            near(h, HighPowerResources.cp(p), cp, "mismatched writer neither debits nor refunds CP"); eq(h, shield(p), n("17.25"), "mismatched writer cannot refill S");
            eq(h, hp(p), health.subtract(BigDecimal.ONE), "refused restart preserves the ordinary native damage transaction");
            h.assertTrue(payment.equals(p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY)) && CirculationRelief.remainingHits(p) == 4, "refusal changes neither the saved receipt nor quota"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void insufficient_cp_after_legacy_mapping_preserves_own_and_unrelated_clocks_and_all_resources(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.shield(p, n("17.25")); long own = p.serverLevel().getGameTime() + 1000, other = own + 300;
            f.legacyPayment(p, f.controllerTime(p) + 2007, 0, 0, own); f.reload(p);
            CompoundTag budget = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY); budget.putDouble("cp", 0); budget.putLong("next_vector_parry", other);
            CompoundTag before = budget.copy(); BigDecimal health = hp(p);
            h.assertFalse(f.start(p), "valid mapped payment still requires enough current CP for fresh activation");
            h.assertTrue(before.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)), "free CP refusal preserves own saved clock and all unrelated clocks exactly");
            h.assertTrue(p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY).getInt("version") == 2, "strict valid legacy decoding completes independently of payment refusal");
            eq(h, shield(p), n("17.25"), "mapping and refusal cannot refill depleted S"); eq(h, hp(p), health, "mapping cannot heal");
            h.assertTrue(CirculationRelief.remainingHits(p) == 4 && !CirculationRelief.active(p), "free refusal creates neither a toggle nor quota debt"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void clone_copies_exact_rolling_debt_without_reactivating_or_refilling(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.arm(p, "20"); f.hit(p, p.damageSources().mobAttack(f.side(p)), 5);
            CompoundTag quota = p.getPersistentData().getCompound(CirculationRelief.QUOTA_KEY).copy();
            CompoundTag payment = p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY).copy();
            ServerPlayer replacement = new ServerPlayer(p.server, h.getLevel(), p.getGameProfile(), p.clientInformation());
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, p, false));
            h.assertTrue(quota.equals(replacement.getPersistentData().getCompound(CirculationRelief.QUOTA_KEY)), "native clone transfers exact remaining ticks");
            h.assertTrue(payment.equals(replacement.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY)), "native clone preserves the physical payment fence");
            h.assertFalse(CirculationRelief.active(p) || CirculationRelief.active(replacement), "neither old nor cloned identity remains active");
            eq(h, shield(p), n("15"), "clone teardown cannot refund old-owner S"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void real_ground_sink_accepts_four_meter_vertical_boundary_but_rejects_beyond(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer near = f.player(8, 8), far = f.player(24, 8);
        f.ready(() -> {
            for (ServerPlayer p : List.of(near, far)) {
                f.arm(p, "20"); p.setPos(p.position().add(0, p == near ? CirculationReliefRules.GROUND_SINK_RANGE : CirculationReliefRules.GROUND_SINK_RANGE+.001, 0)); p.setOnGround(false);
                BigDecimal health = hp(p); f.hit(p, p.damageSources().mobAttack(f.side(p)), 5);
                eq(h, shield(p), n(p == near ? "15" : "20"), "true collision sink vertical distance boundary");
                eq(h, hp(p), p == near ? health : health.subtract(n("5")), "missing legal sink leaves ordinary HP damage");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void contiguous_stone_supports_all_cardinal_near_seams_exact_edges_and_four_cell_corners(GameTestHelper h) {
        Fixture f = new Fixture(h); double[][] fractions = {{.05, .5}, {.95, .5}, {.5, .05}, {.5, .95}, {.05, .05}, {0, .5}, {0, 0}};
        List<ServerPlayer> players = new ArrayList<>(); for (int i = 0; i < fractions.length; i++) players.add(f.player(6 + i % 4 * 9, 8 + i / 4 * 16));
        f.ready(() -> {
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer p = players.get(i); f.fraction(p, fractions[i][0], fractions[i][1]); f.arm(p, "20"); BigDecimal health = hp(p);
                h.assertTrue(CirculationRelief.protecting(p), "entire unchanged footprint has real contiguous support at " + Arrays.toString(fractions[i]));
                h.assertTrue(f.hit(p, p.damageSources().mobAttack(f.side(p)), 5), "actual side contact at seam or corner");
                eq(h, shield(p), n("15"), "seam contact positively absorbs from base S"); eq(h, hp(p), health, "a real full floor protects exact HP across its block boundaries");
                h.assertTrue(CirculationRelief.remainingHits(p) == 3, "one real seam absorption consumes one rolling entry");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void native_arrow_impulse_and_accepted_movement_keep_protection_while_crossing_full_stone_seams(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(16, 16);
        f.ready(() -> {
            BlockPos floor = p.blockPosition().below(); for (int dx = -5; dx <= 5; dx++) for (int dz = -3; dz <= 3; dz++) h.getLevel().setBlockAndUpdate(floor.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
            f.fraction(p, .75, .5); f.arm(p, "100"); BigDecimal health = hp(p); Observation observed = f.observe(p); BigDecimal[] spent = {BigDecimal.ZERO}; int[] hits = {0};
            p.setDeltaMovement(-.56, 0, 0);
            Runnable contactAndMove = () -> {
                h.assertTrue(CirculationRelief.active(p) && CirculationRelief.protecting(p), "full stone keeps paid protection before the next actual arrow");
                p.invulnerableTime = 0; Zombie shooter = f.zombie(p.position().add(7, 0, 0)); Arrow arrow = f.arrow(p, shooter, null);
                int posts = observed.posts; arrow.tick();
                h.assertTrue(arrow.isRemoved() && observed.posts == posts + 1 && observed.damage > 0, "actual native Arrow.tick collides and settles once");
                spent[0] = spent[0].add(decimal(observed.damage)); hits[0]++;
                eq(h, shield(p), n("100").subtract(spent[0]), "every genuine arrow remainder spends existing S"); eq(h, hp(p), health, "seam traversal never creates an unprotected gap");
                Vec3 before = p.position(), motion = p.getDeltaMovement();
                if (hits[0] == 1) near(h, motion.x, -.68, "actual native .4 knockback combines with inherited -.56 momentum into -.68");
                Vec3 destination = before.add(motion.x, 0, motion.z);
                p.connection.resetPosition(); p.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(destination.x, destination.y, destination.z, true));
                vector(h, p.position(), destination, "the real server movement handler accepts travel over contiguous collision floor");
                if (hits[0] == 1) near(h, p.getX() - Math.floor(p.getX()), .07, "first real impulse reaches the formerly false-paused seam band");
                h.assertTrue(p.onGround() && CirculationRelief.active(p) && CirculationRelief.protecting(p), "grounded crossing retains protection at the resulting native position");
                h.assertTrue(CirculationRelief.remainingHits(p) == 4 - hits[0], "motion never resets or duplicates positive-absorption debt");
            };
            contactAndMove.run(); PlayerPosts clock = f.posts(p);
            clock.at(1, contactAndMove);
            clock.at(2, () -> { contactAndMove.run(); h.assertTrue(hits[0] == 3, "three native contacts and accepted movements span genuine owner ticks"); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void missing_fluid_short_and_diagonal_neighbors_or_real_platform_edges_cannot_supply_seam_support(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> players = new ArrayList<>(); for (int i = 0; i < 5; i++) players.add(f.player(6 + i % 3 * 12, 8 + i / 3 * 16));
        f.ready(() -> {
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer p = players.get(i); f.fraction(p, .05, i == 1 ? .05 : .5); f.arm(p, "20"); BigDecimal health = hp(p);
                BlockPos center = p.blockPosition().below(), neighbor = i == 1 ? center.offset(-1, 0, -1) : center.west();
                BlockState replacement = i == 2 ? Blocks.WATER.defaultBlockState() : i == 3 ? Blocks.STONE_SLAB.defaultBlockState() : Blocks.AIR.defaultBlockState();
                h.getLevel().setBlockAndUpdate(neighbor, replacement);
                if (i == 4) for (int dz = -2; dz <= 2; dz++) h.getLevel().setBlockAndUpdate(center.offset(-1, 0, dz), Blocks.AIR.defaultBlockState());
                h.assertTrue(h.getLevel().getBlockState(center).is(Blocks.STONE), "center ray still has an unchanged real full stone hit");
                h.assertTrue(CirculationRelief.active(p) && !CirculationRelief.protecting(p), "unsupported touched neighbor pauses the existing toggle " + i);
                Zombie a = f.side(p); h.assertTrue(f.hit(p, p.damageSources().mobAttack(a), 5), "real native side contact still accepts at unsupported edge");
                eq(h, shield(p), n("20"), "missing neighbor portion never borrows the center block's support"); eq(h, hp(p), health.subtract(n("5")), "unsupported footprint keeps ordinary native HP damage");
                h.assertTrue(CirculationRelief.remainingHits(p) == 4, "false seam support earns no quota reward");
                h.getLevel().setBlockAndUpdate(neighbor, Blocks.STONE.defaultBlockState());
                if (i == 4) for (int dz = -2; dz <= 2; dz++) h.getLevel().setBlockAndUpdate(center.offset(-1, 0, dz), Blocks.STONE.defaultBlockState());
                h.assertTrue(CirculationRelief.protecting(p), "restoring all real support portions resumes the same paid stance");
                p.invulnerableTime = 0; f.hit(p, p.damageSources().mobAttack(a), 5);
                eq(h, shield(p), n("15"), "restored real seam permits exactly one positive absorption"); eq(h, hp(p), health.subtract(n("5")), "restoration never heals the earlier unsupported injury");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_post_removing_only_touched_neighbor_revokes_captured_seam_support(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.fraction(p, .05, .5); f.arm(p, "20"); BigDecimal health = hp(p); BlockPos center = p.blockPosition().below(), neighbor = center.west(); boolean[] removed = {false};
            f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) {
                if (e.getEntity() != p) return; removed[0] = true; h.getLevel().setBlockAndUpdate(neighbor, Blocks.AIR.defaultBlockState());
            } });
            f.hit(p, p.damageSources().mobAttack(f.side(p)), 5);
            h.assertTrue(removed[0] && h.getLevel().getBlockState(center).is(Blocks.STONE), "actual native Post removed the neighboring portion without changing the ray-hit block");
            eq(h, shield(p), n("20"), "a cached central hit cannot authorize S after neighbor loss"); eq(h, hp(p), health.subtract(n("5")), "late support veto preserves ordinary exact settlement");
            h.assertTrue(CirculationRelief.remainingHits(p) == 4 && !CirculationRelief.protecting(p), "stale multi-cell support grants neither quota nor protection"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void loaded_central_sink_with_unloaded_touched_neighbor_refuses_without_forcing_neighbor_chunk(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.arm(p, "20"); BigDecimal health = hp(p); int chunkX = 1_200_000, chunkZ = 1_200_000;
            var central = h.getLevel().getChunk(chunkX, chunkZ);
            BlockPos center = new BlockPos(chunkX * 16, p.blockPosition().getY() - 1, chunkZ * 16 + 8), neighbor = center.west();
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunkX - 1, chunkZ) == null, "only the central fixture chunk is fully loaded");
            BlockState previous = central.getBlockState(center); central.setBlockState(center, Blocks.STONE.defaultBlockState(), false);
            try {
                h.assertTrue(central.getBlockState(center).is(Blocks.STONE), "loaded central ray genuinely reaches a solid full block");
                var ground = CirculationRelief.class.getDeclaredMethod("ground", ServerPlayer.class, Vec3.class); ground.setAccessible(true);
                Vec3 point = new Vec3(center.getX() + .05, center.getY() + 1, center.getZ() + .5);
                h.assertTrue(ground.invoke(null, p, point) == null, "the real production probe rejects only the unloaded neighbor portion");
                h.assertTrue(h.getLevel().getChunkSource().getChunkNow(neighbor.getX() >> 4, neighbor.getZ() >> 4) == null, "bounded support probe never forces the touched neighboring chunk");
                eq(h, shield(p), n("20"), "unloaded-neighbor probing does not debit or refill S"); eq(h, hp(p), health, "probe cannot alter HP");
                h.assertTrue(CirculationRelief.remainingHits(p) == 4, "unloaded support earns no quota");
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            finally { central.setBlockState(center, previous, false); }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void air_liquid_and_noncolliding_decoration_never_supply_fake_ground(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> players = List.of(f.player(6, 8), f.player(18, 8), f.player(30, 8));
        f.ready(() -> {
            BlockState[] states = {Blocks.AIR.defaultBlockState(), Blocks.WATER.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState()};
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer p = players.get(i); f.arm(p, "20"); f.clearColumn(p); h.getLevel().setBlockAndUpdate(p.blockPosition().below(), states[i]);
                BigDecimal health = hp(p); f.hit(p, p.damageSources().mobAttack(f.side(p)), 5);
                eq(h, shield(p), n("20"), "air/liquid/decorative state never supplies support " + i);
                eq(h, hp(p), health.subtract(n("5")), "unsupported sink retains native HP damage");
                h.assertTrue(CirculationRelief.remainingHits(p) == 4, "false ground consumes no quota");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void liquid_between_owner_and_real_floor_blocks_the_sink_without_mutating_world(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.arm(p, "20"); p.setPos(p.position().add(0, 2, 0)); p.setOnGround(false); BlockPos liquid = p.blockPosition().below();
            h.getLevel().setBlockAndUpdate(liquid, Blocks.WATER.defaultBlockState()); BlockState before = h.getLevel().getBlockState(liquid);
            BigDecimal health = hp(p); f.hit(p, p.damageSources().mobAttack(f.side(p)), 5);
            eq(h, shield(p), n("20"), "visible liquid intercepts real lower collision floor"); eq(h, hp(p), health.subtract(n("5")), "liquid occlusion leaves ordinary damage");
            h.assertTrue(h.getLevel().getBlockState(liquid).equals(before) && CirculationRelief.remainingHits(p) == 4, "sink search changes neither fluid nor quota"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void unloaded_sink_probe_fails_closed_without_loading_a_remote_column(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.arm(p, "20"); Vec3 original = p.position(); BlockPos remote = new BlockPos(20_000_007, p.blockPosition().getY(), 20_000_007);
            h.assertFalse(h.getLevel().hasChunkAt(remote), "remote control column is genuinely unloaded");
            // Entity.setPos itself synchronously loads its destination through native section
            // management. Invoke only the production loaded-world probe at the remote point.
            try {
                var ground = CirculationRelief.class.getDeclaredMethod("ground", ServerPlayer.class, Vec3.class); ground.setAccessible(true);
                h.assertTrue(ground.invoke(null, p, new Vec3(remote.getX() + .5, original.y, remote.getZ() + .5)) == null,
                        "real production probe rejects the unloaded column");
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            h.assertFalse(h.getLevel().hasChunkAt(remote), "ground probe must never synchronously load missing chunks");
            eq(h, shield(p), n("20"), "unloaded sink cannot debit or refill base S");
            h.assertTrue(CirculationRelief.remainingHits(p) == 4, "unloaded probe spends no admission");
            vector(h, p.position(), original, "loaded-world probe never moves the actor"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void activation_requires_real_ground_and_refuses_air_and_liquid_without_cp_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer air = f.player(8, 8), liquid = f.player(24, 8);
        f.ready(() -> {
            for (ServerPlayer p : List.of(air, liquid)) {
                f.clearColumn(p); if (p == liquid) h.getLevel().setBlockAndUpdate(p.blockPosition().below(), Blocks.WATER.defaultBlockState());
                double cp = HighPowerResources.cp(p); BigDecimal pool = shield(p), health = hp(p);
                h.assertFalse(f.start(p), "startup refuses a missing real solid sink");
                h.assertFalse(CirculationRelief.active(p), "failed startup installs no toggle"); near(h, HighPowerResources.cp(p), cp, "ground refusal is free");
                eq(h, shield(p), pool, "startup never spends or refills S"); eq(h, hp(p), health, "startup cannot change HP");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void real_collision_surface_just_above_feet_cannot_be_used_or_skipped_for_lower_ground(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.arm(p, "20"); BigDecimal health = hp(p);
            // Keep the real stone surface only .005 above the owner's feet. A ray-origin
            // tolerance must not expand the legal sink height or look through that surface.
            p.setPos(p.position().add(0, -.005, 0)); p.setOnGround(false);
            f.hit(p, p.damageSources().mobAttack(f.side(p)), 5);
            eq(h, shield(p), n("20"), "a real solid collision above feet is never a legal sink");
            eq(h, hp(p), health.subtract(n("5")), "above-feet obstruction retains ordinary damage");
            h.assertTrue(CirculationRelief.remainingHits(p) == 4, "ray origin tolerance cannot mint ground authority"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void synthetic_post_outside_native_hurt_has_no_hp_s_or_quota_authority(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.arm(p, "20"); BigDecimal health = hp(p); DamageSource source = p.damageSources().mobAttack(f.side(p));
            NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(p, new DamageContainer(source, 5)));
            net.neoforged.neoforge.common.CommonHooks.onLivingDamagePost(p, new DamageContainer(source, 5));
            eq(h, shield(p), n("20"), "synthetic native-looking events cannot spend S"); eq(h, hp(p), health, "synthetic events cannot manufacture HP settlement");
            h.assertTrue(CirculationRelief.remainingHits(p) == 4, "synthetic events have no positive absorption authority"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void synthetic_commonhooks_post_inside_native_post_preserves_only_original_dispatch_authority(GameTestHelper h) {
        postReplay(h, false);
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void recursive_same_native_post_object_cannot_steal_or_duplicate_original_absorption(GameTestHelper h) {
        postReplay(h, true);
    }
    private static void postReplay(GameTestHelper h, boolean sameObject) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.arm(p, "2"); BigDecimal health = hp(p); boolean[] injected = {false}; LivingDamageEvent.Post[] captured = {null};
            f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) {
                if (e.getEntity() != p || injected[0]) return; injected[0] = true; captured[0] = e;
                if (sameObject) NeoForge.EVENT_BUS.post(e); else net.neoforged.neoforge.common.CommonHooks.onLivingDamagePost(p, new DamageContainer(e.getSource(), e.getNewDamage()));
                eq(h, shield(p), n("2"), "nested Post dispatch cannot settle original S early"); eq(h, hp(p), health, "nested Post cannot settle original HP early");
                h.assertTrue(CirculationRelief.remainingHits(p) == 4, "nested Post dispatch cannot borrow original quota authority");
            } });
            f.hit(p, p.damageSources().mobAttack(f.side(p)), 5);
            h.assertTrue(injected[0] && captured[0] != null, "actual native Post listener executed replay");
            eq(h, shield(p), BigDecimal.ZERO, "only original native dispatch spends two S"); eq(h, hp(p), health.subtract(n("3")), "only original native dispatch settles excess HP");
            h.assertTrue(CirculationRelief.remainingHits(p) == 3, "one original absorption consumes one entry");
            NeoForge.EVENT_BUS.post(captured[0]); eq(h, hp(p), health.subtract(n("3")), "late exact-object replay has no authority");
            h.assertTrue(CirculationRelief.remainingHits(p) == 3, "late replay cannot consume another entry");
            h.assertTrue(f.start(p) && !CirculationRelief.active(p), "fresh authenticated press turns the paid toggle off");
            NeoForge.EVENT_BUS.post(captured[0]);
            eq(h, hp(p), health.subtract(n("3")), "turning off the toggle cannot restore spent Post authority");
            eq(h, shield(p), BigDecimal.ZERO, "inactive replay cannot restore or debit resources");
            h.assertTrue(CirculationRelief.remainingHits(p) == 3, "inactive replay cannot debit another quota entry"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void unavailable_native_post_hook_refuses_start_free_and_ends_existing_toggle(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer active = f.player(8, 8), idle = f.player(24, 8);
        f.ready(() -> {
            h.assertTrue(AnchorDamagePostProof.available(), "the actual pinned native provider is installed before this isolated denial");
            f.arm(active, "20"); double activeCp = HighPowerResources.cp(active), idleCp = HighPowerResources.cp(idle);
            BigDecimal activeHealth = hp(active), idleHealth = hp(idle), idlePool = shield(idle);
            try {
                var failed = cn.academy.port.highpower.mixin.NativeAnchorPostMixinPlugin.class.getDeclaredField("failed"); failed.setAccessible(true);
                boolean prior = failed.getBoolean(null);
                try {
                    // Disable only; never manufacture installation/review evidence. All work is
                    // synchronous on the server thread, and the original flag is restored below.
                    failed.setBoolean(null, true); h.assertFalse(AnchorDamagePostProof.available(), "real provider reports unavailable");
                    h.assertFalse(f.start(idle), "unavailable proof refuses startup before CP payment");
                    CirculationRelief.tick(active);
                    h.assertFalse(CirculationRelief.active(active) || CirculationRelief.active(idle), "lost provider tears down existing protection");
                    near(h, HighPowerResources.cp(active), activeCp, "teardown refunds or charges no CP"); near(h, HighPowerResources.cp(idle), idleCp, "refused startup charges no CP");
                    eq(h, shield(active), n("20"), "proof loss cannot debit or refill active S"); eq(h, shield(idle), idlePool, "proof refusal preserves idle S");
                    eq(h, hp(active), activeHealth, "proof loss cannot heal or damage"); eq(h, hp(idle), idleHealth, "refusal preserves native HP");
                } finally { failed.setBoolean(null, prior); }
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            h.assertTrue(AnchorDamagePostProof.available(), "the original actual provider state is restored before another test can run"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void accepted_nested_hurt_revokes_outer_defense_but_accounts_each_native_damage_once(GameTestHelper h) {
        nestedHurt(h, true);
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void rejected_nested_hurt_still_revokes_outer_defense_without_inventing_damage(GameTestHelper h) {
        nestedHurt(h, false);
    }
    private static void nestedHurt(GameTestHelper h, boolean accepted) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.arm(p, "20"); Zombie a = f.side(p); DamageSource source = p.damageSources().mobAttack(a); BigDecimal health = hp(p); boolean[] nested = {false};
            f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) {
                if (e.getEntity() != p || nested[0]) return; nested[0] = true;
                if (accepted) { p.invulnerableTime = 0; h.assertTrue(f.hit(p, p.damageSources().magic(), 2), "nested genuine native magic accepts"); }
                else h.assertFalse(f.hit(p, source, 5), "nested equal native hurt is truly iframe-rejected");
                NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(p, new DamageContainer(source, 5)));
                eq(h, shield(p), n("20"), "reentry and synthetic Post cannot restore outer absorption authority");
            } });
            f.hit(p, source, 5); h.assertTrue(nested[0], "actual native Post reentered hurt");
            eq(h, shield(p), n("20"), "outer defense is retired after any nested hurt");
            eq(h, hp(p), health.subtract(n(accepted ? "7" : "5")), "each accepted native transaction settles exactly once");
            h.assertTrue(CirculationRelief.remainingHits(p) == 4, "retired defense never consumes a rolling entry"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void callbacks_preserve_frozen_source_classification_and_use_current_identity_position_and_resources(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> players = List.of(f.player(6, 8), f.player(18, 8), f.player(30, 8), f.player(18, 24));
        f.ready(() -> {
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer p = players.get(i); f.arm(p, "20"); Zombie a = f.side(p), other = f.zombie(p.position().add(-2, 0, 0));
                Arrow arrow = f.arrow(p, a, null); final int mode = i; BigDecimal health = hp(p);
                DamageSource source = i == 0 ? p.damageSources().arrow(arrow, a) : p.damageSources().mobAttack(a);
                f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) {
                    if (e.getEntity() != p) return;
                    switch (mode) { case 0 -> arrow.setOwner(other); case 1 -> a.setPos(p.position().add(0, 0, 2));
                        case 2 -> p.setPos(p.position().add(.01, 0, 0)); default -> f.shield(p, n("7")); }
                } });
                f.hit(p, source, 5);
                boolean admitted = i == 1 || i == 3;
                eq(h, shield(p), n(i == 3 ? "2" : i == 1 ? "15" : "20"), "callback uses frozen classification and current authoritative balance " + i);
                eq(h, hp(p), admitted ? health : health.subtract(n("5")), "current identity and owner position veto only stale captures");
                h.assertTrue(CirculationRelief.remainingHits(p) == (admitted ? 3 : 4), "only an actually settled absorption debits quota");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_callback_cannot_promote_an_original_front_hit_into_an_eligible_side_hit(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.arm(p, "20"); Zombie a = f.zombie(p.position().add(0, 0, 2)); BigDecimal health = hp(p);
            f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) {
                if (e.getEntity() == p) { a.setPos(p.position().add(2, 0, 0)); p.setYRot(90); }
            } });
            f.hit(p, p.damageSources().mobAttack(a), 5);
            eq(h, shield(p), n("20"), "post callback cannot create an originally absent side capture");
            eq(h, hp(p), health.subtract(n("5")), "original front contact keeps ordinary native HP settlement");
            h.assertTrue(CirculationRelief.remainingHits(p) == 4, "late eligibility cannot mint quota authority"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void post_callback_removal_of_real_sink_cannot_spend_cached_ground_authority(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.arm(p, "20"); BigDecimal health = hp(p);
            f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) { if (e.getEntity() == p) f.clearColumn(p); } });
            f.hit(p, p.damageSources().mobAttack(f.side(p)), 5);
            eq(h, shield(p), n("20"), "late destroyed sink invalidates capture"); eq(h, hp(p), health.subtract(n("5")), "missing sink leaves native damage");
            h.assertTrue(CirculationRelief.remainingHits(p) == 4, "stale ground has no quota authority"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void unsupported_damage_types_subclasses_spoofed_positions_and_skill_sources_are_refused(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> players = new ArrayList<>(); for (int i = 0; i < 6; i++) players.add(f.player(6 + i % 3 * 12, 8 + i / 3 * 16));
        f.ready(() -> {
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer p = players.get(i); f.arm(p, "20"); Zombie a = f.side(p); DamageSource nativeSource = p.damageSources().mobAttack(a);
                DamageSource source = switch (i) { case 0 -> new DamageSource(nativeSource.typeHolder(), a) {}; case 1 -> p.damageSources().magic();
                    case 2 -> new AbilityDamage.SkillDamageSource(p, "vecmanip.circulation_relief", false);
                    case 3 -> new DamageSource(nativeSource.typeHolder(), a, a, p.position()); case 4 -> new DamageSource(nativeSource.typeHolder(), null, a);
                    default -> new DamageSource(h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.SONIC_BOOM), a); };
                Observation hit = f.observe(p); BigDecimal health = hp(p); f.hit(p, source, 5);
                eq(h, shield(p), n("20"), "closed native whitelist rejects unsupported source " + i);
                eq(h, hp(p), health.subtract(decimal(hit.damage)), "refused source preserves its exact post-native HP path");
                h.assertTrue(CirculationRelief.remainingHits(p) == 4, "unsupported provenance never consumes quota");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void critical_tipped_enchanted_and_modified_gravity_arrows_are_not_plain_arrows(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> players = new ArrayList<>(); for (int i = 0; i < 4; i++) players.add(f.player(6 + i * 9, 8));
        f.ready(() -> {
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer p = players.get(i); f.arm(p, "20"); Zombie shooter = f.side(p); ItemStack bow = null;
                if (i == 2) { bow = new ItemStack(Items.BOW); bow.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PUNCH), 1); }
                Arrow arrow = f.arrow(p, shooter, bow); if (i == 0) arrow.setCritArrow(true); if (i == 1) arrow.addEffect(new MobEffectInstance(MobEffects.POISON, 40)); if (i == 3) arrow.setNoGravity(true);
                BigDecimal health = hp(p); f.hit(p, p.damageSources().arrow(arrow, shooter), 5);
                eq(h, shield(p), n("20"), "nonplain arrow cannot borrow vanilla kinetic provenance"); eq(h, hp(p), health.subtract(n("5")), "nonplain arrow retains ordinary HP damage");
                h.assertTrue(CirculationRelief.remainingHits(p) == 4, "nonplain arrows spend no rolling entries");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void friendly_team_melee_and_friendly_arrow_sources_cannot_spend_base_s(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8), other = f.player(24, 8);
        f.ready(() -> {
            var scoreboard = h.getLevel().getScoreboard(); var team = scoreboard.addPlayerTeam("cr" + UUID.randomUUID().toString().substring(0, 10)); f.teams.add(team);
            for (ServerPlayer target : List.of(p, other)) {
                Zombie a = f.side(target); scoreboard.addPlayerToTeam(target.getScoreboardName(), team); scoreboard.addPlayerToTeam(a.getScoreboardName(), team);
                f.arm(target, "20"); BigDecimal health = hp(target);
                DamageSource source = target == p ? target.damageSources().mobAttack(a) : target.damageSources().arrow(f.arrow(target, a, null), a);
                f.hit(target, source, 5); eq(h, shield(target), n("20"), "allied source cannot supply a defense receipt"); eq(h, hp(target), health.subtract(n("5")), "allied direct fixture keeps its native HP result");
                h.assertTrue(CirculationRelief.remainingHits(target) == 4, "friendly source cannot spend quota");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void circulation_and_other_vector_stances_are_mutually_exclusive_in_both_directions(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer circulation = f.player(8, 8), parry = f.player(24, 8), anchor = f.player(8, 24), braking = f.player(24, 24);
        f.ready(() -> {
            f.arm(circulation, "20"); double cp = HighPowerResources.cp(circulation);
            h.assertFalse(VectorParry.start(circulation, f.wire(circulation, 1)), "active432 refuses412");
            h.assertFalse(InertialAnchor.start(circulation, f.wire(circulation, 2)), "active432 refuses422");
            h.assertFalse(BrakingTouch.start(circulation, f.wire(circulation, 3)), "active432 refuses413");
            near(h, HighPowerResources.cp(circulation), cp, "all conflict refusals are free");
            h.assertTrue(VectorParry.start(parry, f.wire(parry, 1)), "real authenticated412 starts"); cp = HighPowerResources.cp(parry);
            h.assertFalse(f.start(parry), "active412 refuses432"); near(h, HighPowerResources.cp(parry), cp, "reverse412 conflict is free");
            h.assertTrue(InertialAnchor.start(anchor, f.wire(anchor, 2)), "real grounded422 starts"); cp = HighPowerResources.cp(anchor);
            h.assertFalse(f.start(anchor), "active422 refuses432"); near(h, HighPowerResources.cp(anchor), cp, "reverse422 conflict is free");
            Snowball snow = new Snowball(h.getLevel(), braking); snow.setPos(braking.getX(), braking.getEyeY() - snow.getBbHeight() / 2, braking.getZ() + 1.4 + snow.getBbWidth() / 2);
            snow.setDeltaMovement(.12, -.08, .17); f.entity(snow);
            h.assertTrue(BrakingTouch.start(braking, f.wire(braking, 3)), "real authenticated413 brakes a freshly joined owned snowball");
            h.assertTrue(BrakingTouch.active(braking), "braking action retains its actual active receipt"); cp = HighPowerResources.cp(braking);
            h.assertFalse(f.start(braking), "active413 refuses432"); near(h, HighPowerResources.cp(braking), cp, "reverse413 conflict is free"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void activation_cleanup_and_category_switch_never_change_motion_heal_or_award_experience(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            f.hit(p, p.damageSources().magic(), 3); p.invulnerableTime = 0; f.place(p); f.shield(p, n("20"));
            var state = AbilityStorage.get(p); double xp = state.levelExperience, speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
            BigDecimal health = hp(p), growth = HighPowerGrowth.values(p).position(), earned = HighPowerCombat.earnedGrowthPosition(p); Vec3 motion = new Vec3(.1, .05, -.1); p.setDeltaMovement(motion);
            h.assertTrue(f.start(p), "ordinary moving owner can activate the ground-sink toggle");
            vector(h, p.getDeltaMovement(), motion, "activation adds no movement effect"); near(h, p.getAttributeValue(Attributes.MOVEMENT_SPEED), speed, "activation never changes movement speed");
            f.place(p); f.hit(p, p.damageSources().mobAttack(f.side(p)), 5); eq(h, hp(p), health, "positive absorption cannot heal previous injury");
            h.assertTrue(state.exp(ID) == 0 && state.levelExperience == xp, "positive absorption awards no mastery or level XP");
            eq(h, HighPowerGrowth.values(p).position(), growth, "contact cannot grow active q"); eq(h, HighPowerCombat.earnedGrowthPosition(p), earned, "contact cannot certify earned Q");
            CompoundTag quota = p.getPersistentData().getCompound(CirculationRelief.QUOTA_KEY).copy();
            state.changeCategoryClassic(HighPowerCategories.ELECTROMASTER); CirculationRelief.tick(p);
            h.assertFalse(CirculationRelief.active(p), "ordinary category change tears down the stance");
            eq(h, shield(p), n("15"), "switching category preserves depleted base shield"); eq(h, hp(p), health, "category switch cannot heal");
            h.assertTrue(quota.equals(p.getPersistentData().getCompound(CirculationRelief.QUOTA_KEY)), "category switch cannot erase rolling debt");
            near(h, p.getAttributeValue(Attributes.MOVEMENT_SPEED), speed, "cleanup leaves native movement speed unchanged"); h.succeed();
        });
    }

    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static BigDecimal hp(ServerPlayer p) { return n(HighPowerCombat.healthText(p)); }
    private static BigDecimal shield(ServerPlayer p) { return HighPowerCombat.shield(p); }
    private static BigDecimal decimal(float value) { return new BigDecimal(Float.toString(value)); }
    /** Irrational 60-degree directions have no exact finite binary representation. */
    private static Vec3 nearestSideEdge(ServerPlayer p, Vec3 origin) {
        double outward = origin.x > p.getX() ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        int attempts = 0;
        while (!exactSide(p, origin) && attempts++ < 4096) origin = new Vec3(Math.nextAfter(origin.x, outward), origin.y, origin.z);
        for (int i = 0; i < 4096; i++) {
            Vec3 next = new Vec3(Math.nextAfter(origin.x, p.getX()), origin.y, origin.z);
            if (!exactSide(p, next)) return origin;
            origin = next;
        }
        throw new IllegalStateException("Cannot bracket represented side boundary");
    }
    private static boolean exactSide(ServerPlayer p, Vec3 origin) {
        BigDecimal x = new BigDecimal(origin.x - p.getX()), z = new BigDecimal(origin.z - p.getZ());
        Vec3 look = p.getLookAngle(); BigDecimal fx = new BigDecimal(look.x), fz = new BigDecimal(look.z);
        BigDecimal dot = x.multiply(fx).add(z.multiply(fz));
        return dot.multiply(dot).multiply(n("4")).compareTo(x.multiply(x).add(z.multiply(z)).multiply(fx.multiply(fx).add(fz.multiply(fz)))) <= 0;
    }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) { h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected); }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-6, why + ": " + actual + " != " + expected); }
    private static void vector(GameTestHelper h, Vec3 actual, Vec3 expected, String why) { h.assertTrue(actual.distanceToSqr(expected) < 1e-10, why + ": " + actual + " != " + expected); }
    private static void defenses(GameTestHelper h, ServerPlayer p) {
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET); helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(true)); p.setItemSlot(EquipmentSlot.HEAD, helmet); p.getAttribute(Attributes.ARMOR).setBaseValue(5); p.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0)); p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0)); p.setAbsorptionAmount(.5F);
    }
    public static final class DamageGate {
        final ServerPlayer owner; final int mode;
        DamageGate(ServerPlayer owner, int mode) { this.owner = owner; this.mode = mode; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) { if (e.getEntity() == owner && mode == 0) e.setCanceled(true); }
        @SubscribeEvent public void pre(LivingDamageEvent.Pre e) { if (e.getEntity() == owner && mode == 1) e.setNewDamage(0); }
    }
    public static final class Observation {
        final ServerPlayer owner; int posts, knockbacks; float damage, armor, effects, enchantments, absorption, blockedDamage; boolean blocked;
        Observation(ServerPlayer owner) { this.owner = owner; }
        @SubscribeEvent public void shield(LivingShieldBlockEvent e) { if (e.getEntity() == owner) { blocked = e.getBlocked(); blockedDamage = e.getBlockedDamage(); } }
        @SubscribeEvent public void knockback(LivingKnockBackEvent e) { if (e.getEntity() == owner) knockbacks++; }
        @SubscribeEvent public void post(LivingDamageEvent.Post e) { if (e.getEntity() != owner) return; posts++; damage = e.getNewDamage(); armor = e.getReduction(DamageContainer.Reduction.ARMOR); effects = e.getReduction(DamageContainer.Reduction.MOB_EFFECTS); enchantments = e.getReduction(DamageContainer.Reduction.ENCHANTMENTS); absorption = e.getReduction(DamageContainer.Reduction.ABSORPTION); }
    }

    /** Observe completed native owner ticks after the controller, independent of GameTest scheduling phase. */
    public static final class PlayerPosts {
        final Fixture fixture; final GameTestHelper helper; final ServerPlayer owner; final Map<Integer, Runnable> checks = new HashMap<>(); int last, completed;
        PlayerPosts(Fixture fixture, ServerPlayer p) { this.fixture=fixture; helper=fixture.h; owner = p; last = p.server.getTickCount(); }
        void at(int count, Runnable action) { checks.put(count, action); }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void tick(PlayerTickEvent.Post event) {
            if (fixture.closed || fixture.completion.pending() || helper.testInfo.isDone() || event.getEntity() != owner || owner.server.getTickCount() <= last) return;
            last = owner.server.getTickCount(); Runnable check = checks.remove(++completed);
            if (check != null) try { check.run(); } catch (Throwable failure) { fixture.fail(failure); }
        }
    }

    /** Test-only handoff: native callbacks keep their timing; only terminal notification is deferred. */
    public static final class Completion {
        private boolean pending, delivered; private Throwable failure;
        public boolean pending() { return pending; }
        public void succeed() { if(!delivered)pending=true; }
        public void fail(Throwable problem) {
            Objects.requireNonNull(problem);
            if(!delivered) {pending=true;if(failure==null)failure=problem;}
        }
        public void flush(Runnable success,java.util.function.Consumer<Throwable> failed) {
            if(!pending || delivered)return;
            delivered=true;
            if(failure==null)success.run();else failed.accept(failure);
        }
    }

    @GameTest(template=TEMPLATE,batch="circulation_relief_category_efficiency",timeoutTicks=100)
    public static void category_side_relief_saves_actual_shield_vs_no_cast_and_quota_exhaustion_restores_full_cost(GameTestHelper h) {
        forceEfficiencyCategory(h); Fixture f=new Fixture(h);ServerPlayer p=f.player(8,8),control=f.player(22,8);
        f.ready(()->{
            f.arm(p,"20");f.shield(control,n("20"));Zombie a=f.zombie(p.position().add(2,0,0)),b=f.zombie(control.position().add(2,0,0));
            h.assertTrue(f.hit(control,control.damageSources().mobAttack(b),5),"real no-cast native category baseline");
            eq(h,shield(control),n("15"),"ordinary category shield pays five S");
            h.assertTrue(f.hit(p,p.damageSources().mobAttack(a),5),"real active side contact");
            eq(h,shield(p),n("16.25"),"actual relief saves25% relative to no casting");
            for(int i=1;i<4;i++){p.invulnerableTime=0;h.assertTrue(f.hit(p,p.damageSources().mobAttack(a),5),"independent native admitted side contact");}
            eq(h,shield(p),n("5"),"four native side contacts save exactly five S in total");
            h.assertTrue(CirculationRelief.remainingHits(p)==0,"four real admissions exhaust existing rolling quota");
            p.invulnerableTime=0;h.assertTrue(f.hit(p,p.damageSources().mobAttack(a),5),"fifth real contact still settles");
            eq(h,shield(p),BigDecimal.ZERO,"exhausted quota restores ordinary five S cost, not another discount");f.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch="circulation_relief_category_efficiency",timeoutTicks=100)
    public static void category_front_contact_keeps_normal_cost_without_spending_side_relief_quota(GameTestHelper h) {
        forceEfficiencyCategory(h);Fixture f=new Fixture(h);ServerPlayer p=f.player(8,8);
        f.ready(()->{f.arm(p,"20");Zombie a=f.zombie(p.position().add(0,0,2));h.assertTrue(f.hit(p,p.damageSources().mobAttack(a),5),"real native front contact");
            eq(h,shield(p),n("15"),"outside side sectors category shield pays full cost");h.assertTrue(CirculationRelief.remainingHits(p)==4,"front contact cannot buy a side discount or spend its quota");f.succeed();});
    }
    private static void forceEfficiencyCategory(GameTestHelper h) {
        EsperLegacyBypass.force(true);
        h.testInfo.addListener(new GameTestListener(){boolean closed;private void close(){if(!closed){closed=true;EsperLegacyBypass.force(false);}}
            public void testStructureLoaded(GameTestInfo i){} public void testPassed(GameTestInfo i,GameTestRunner r){close();}
            public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
    }
    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>(); final List<Object> hooks = new ArrayList<>();
        final List<net.minecraft.world.scores.PlayerTeam> teams = new ArrayList<>();
        final Map<ServerPlayer, EmbeddedChannel> channels = new IdentityHashMap<>(); final Map<ServerPlayer, Vec3> anchors = new IdentityHashMap<>();
        final Map<ServerPlayer, Long> inputs = new IdentityHashMap<>(); final Map<ServerPlayer, String> held = new IdentityHashMap<>();
        MachineDeveloperBlockEntity machine; int learnSequence; boolean closed; final Completion completion=new Completion();
        Fixture(GameTestHelper h) { this.h = h; h.onEachTick(this::finishQueuedTerminal); h.testInfo.addListener(new GameTestListener() { public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); } public void testFailed(GameTestInfo i, GameTestRunner r) { close(); } public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); } }); }
        void succeed() { if(!closed)completion.succeed(); }
        void fail(Throwable failure) { if(!closed)completion.fail(failure); }
        void finishQueuedTerminal() { if(!closed && !h.testInfo.isDone())completion.flush(h::succeed,h.testInfo::fail); }
        ServerPlayer player(double x, double z) { return player(x, z, 3, true); }
        ServerPlayer player(double x, double z, int level, boolean learned) {
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "CR" + UUID.randomUUID().toString().substring(0, 10)), false);
            ServerPlayer p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            Connection connection = new Connection(PacketFlow.SERVERBOUND); EmbeddedChannel channel = new EmbeddedChannel(connection); channels.put(p, channel); NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); drain(p);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) h.setBlock(BlockPos.containing(x + dx, 1, z + dz), Blocks.STONE);
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); anchors.put(p, h.absoluteVec(new Vec3(x + .5, 2, z + .5))); place(p);
            h.assertTrue(HighPowerCombat.enroll(p, level), "genuine connected LV3 Vector enrollment");
            var state = AbilityStorage.get(p); state.selectCategory(HighPowerCategories.VECMANIP); state.setLevel(level);
            state.learn(HighPowerSkills.DEFLECTION_STRIKE); state.learn(VectorParry.ID); state.learn(BrakingTouch.ID); state.learn(InertialAnchor.ID); if (learned) state.learn(ID); state.activated = true; state.overloadFine = true;
            h.assertTrue(state.presets.edit(0, 0, learned ? ID : HighPowerSkills.DEFLECTION_STRIKE, state::learned) && state.presets.edit(0, 1, VectorParry.ID, state::learned)
                    && state.presets.edit(0, 2, InertialAnchor.ID, state::learned) && state.presets.edit(0, 3, BrakingTouch.ID, state::learned), "real learned bindings own native key slots");
            HighPowerResources.initializeBudget(p); hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5)); return p;
        }
        void ready(Runnable action) { h.runAfterDelay(61, () -> { for (ServerPlayer p : players) { p.doTick(); place(p); } action.run(); }); }
        void place(ServerPlayer p) { Vec3 at = anchors.get(p); p.moveTo(at.x, at.y, at.z, 0, 0); p.setYHeadRot(0); p.yBodyRot = 0; p.setShiftKeyDown(false); p.setPose(Pose.STANDING); p.setOnGround(true); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        void fraction(ServerPlayer p, double x, double z) { p.setPos(Math.floor(p.getX()) + x, p.getY(), Math.floor(p.getZ()) + z); p.connection.resetPosition(); }
        String wire(ServerPlayer p, int slot) { long next = inputs.getOrDefault(p, 0L) + 1; inputs.put(p, next); return new HighPowerInput.Input(slot, next, p.getId(), LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p))).wire(); }
        boolean start(ServerPlayer p) { String wire = wire(p, 0); boolean result = CirculationRelief.start(p, wire); if (result) held.put(p, wire); return result; }
        void arm(ServerPlayer p, String shield) { shield(p, n(shield)); boolean started = start(p); h.assertTrue(started, "authenticated paid circulation toggle starts" + (started ? "" : diagnostic(p))); }
        String diagnostic(ServerPlayer p) {
            String detail;
            try {
                var ground = CirculationRelief.class.getDeclaredMethod("ground", ServerPlayer.class, Vec3.class); ground.setAccessible(true);
                var canAct = CirculationRelief.class.getDeclaredMethod("canAct", ServerPlayer.class); canAct.setAccessible(true);
                detail = ", ground=" + ground.invoke(null, p, p.position()) + ", canAct=" + canAct.invoke(null, p);
            } catch (ReflectiveOperationException e) { detail = ", diagnostic=" + e; }
            return ": proof=" + AnchorDamagePostProof.available() + ", unsupported=" + HighPowerCombat.unsupportedReason(p)
                    + ", floor=" + h.getLevel().getBlockState(p.blockPosition().below()) + ", pos=" + p.position() + ", cp=" + HighPowerResources.cp(p)
                    + ", payment=" + p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY) + detail;
        }
        void shield(ServerPlayer p, BigDecimal amount) { p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("shield", amount.toPlainString()); }
        CompoundTag reserve(ServerPlayer p) { CompoundTag r = new CompoundTag(); r.putInt("version", MagneticDomainReserve.VERSION); r.putString("amount", "9"); r.putString("capacity", HighPowerCombat.maxShieldText(p)); r.putString("lower", "4"); r.putString("upper", "5"); p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).put(MagneticDomainReserve.KEY, r); return r.copy(); }
        Zombie side(ServerPlayer p) { return zombie(p.position().add(2, 0, 0)); }
        Zombie zombie(Vec3 at) { Zombie a = new Zombie(EntityType.ZOMBIE, h.getLevel()); a.setNoAi(true); a.setNoGravity(true); a.setSilent(true); a.setPos(at); a.getAttribute(Attributes.ARMOR).setBaseValue(0); a.getAttribute(Attributes.ATTACK_KNOCKBACK).setBaseValue(0); entity(a); return a; }
        Arrow arrow(ServerPlayer target, LivingEntity shooter, ItemStack weapon) { Arrow arrow = new Arrow(h.getLevel(), shooter, new ItemStack(Items.ARROW), weapon); arrow.setPos(target.position().add(2, 1, 0)); arrow.setDeltaMovement(-3, 0, 0); entity(arrow); return arrow; }
        void entity(Entity e) { h.assertTrue(h.getLevel().addFreshEntity(e), "actual entity joins native server level"); entities.add(e); }
        boolean hit(ServerPlayer p, DamageSource source, float amount) { boolean previous = p.server.isPvpAllowed(); p.server.setPvpAllowed(true); try { return p.hurt(source, amount); } finally { p.server.setPvpAllowed(previous); } }
        void clearColumn(ServerPlayer p) { BlockPos floor = p.blockPosition().below(); for (int dy = 0; dy < 6; dy++) h.getLevel().setBlockAndUpdate(floor.below(dy), Blocks.AIR.defaultBlockState()); }
        void reload(ServerPlayer p) { AbilityStorage.save(p); CompoundTag saved = p.saveWithoutId(new CompoundTag()); CirculationRelief.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.load(saved); place(p); inputs.put(p, 0L); }
        long controllerTime(ServerPlayer p) { try { var now = CirculationRelief.class.getDeclaredMethod("now", ServerPlayer.class); now.setAccessible(true); return (long)now.invoke(null, p); } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); } }
        void legacyPayment(ServerPlayer p, long observed, int remaining, int restart, long resourceClock) {
            CompoundTag payment = new CompoundTag(); payment.putInt("version", 1); payment.putLong("observed", observed); payment.putLong("until", observed + remaining); payment.putLong("stopUntil", observed + restart); payment.putString("dimension", p.serverLevel().dimension().location().toString());
            p.getPersistentData().put(CirculationRelief.PAYMENT_KEY, payment); p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putLong("next_" + ID, resourceClock);
        }
        CompoundTag v2Payment(ServerPlayer p, int remaining, int restart, long resourceClock) {
            CompoundTag payment = new CompoundTag(); payment.putInt("version", 2); payment.putInt("remaining", remaining); payment.putInt("restart", restart); payment.putLong("receipt", 0); payment.putString("dimension", p.serverLevel().dimension().location().toString()); payment.putLong("resourceUntil", resourceClock);
            p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putLong("next_" + ID, resourceClock); return payment;
        }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        Observation observe(ServerPlayer p) { Observation result = new Observation(p); hook(result); return result; }
        PlayerPosts posts(ServerPlayer p) { PlayerPosts clock = new PlayerPosts(this, p); hook(clock); return clock; }
        void drain(ServerPlayer p) { EmbeddedChannel channel = channels.get(p); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet; while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet); } }
        DeveloperItemEnergy portable(ServerPlayer p, double amount) { ItemStack stack = new ItemStack(AcademyCraft.DEVELOPER.get()); p.setItemInHand(InteractionHand.MAIN_HAND, stack); DeveloperItemEnergy energy = new DeveloperItemEnergy(stack, DeveloperType.PORTABLE); energy.energy(amount); return energy; }
        void develop(ServerPlayer p, int ticks) { for (int tick = 0; tick < ticks; tick++) DevelopmentController.tick(p, ignored -> {}); }
        MachineDeveloperBlockEntity normal(ServerPlayer p, double amount) {
            BlockPos base = h.absolutePos(new BlockPos(16, 1, 24));
            if (machine == null) { var item = MachineDevelopers.NORMAL_ITEM.get(); ItemStack stack = new ItemStack(item); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
                h.assertTrue(item.place(new BlockPlaceContext(p, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(base), Direction.UP, base, false))).consumesAction(), "real complete Normal developer is placed");
                machine = (MachineDeveloperBlockEntity) h.getLevel().getBlockEntity(base); }
            machine.endUsers(); machine.battery().load(amount); p.moveTo(base.getX() + .5, base.getY(), base.getZ() - 1.5, 0, 0);
            h.assertTrue(machine.use(p), "actual player-bound Normal developer session opens"); return machine;
        }
        boolean learnRequest(ServerPlayer p, String skill) { String value = MachineDeveloperSessions.activeToken(p).orElseThrow() + ":" + new HighPowerLearningProtocol.Request(skill, ++learnSequence).wire(); return MachineDeveloperSessions.request(p, new AcademyNetwork.Request("machine_hp_learn", value)); }
        void learn(ServerPlayer p, String skill) { int level = SkillCatalog.find(HighPowerCategories.VECMANIP, skill).orElseThrow().level(); int count = ClassicRules.learningStimulations(level); DeveloperType type = DeveloperType.minimumForSkill(level); double cost = type.actualConsumption(count);
            if (type == DeveloperType.PORTABLE) { DeveloperItemEnergy energy = portable(p, cost); h.assertTrue(DevelopmentController.startSkill(p, skill), "real finite paid parent learning starts"); develop(p, count * type.ticksPerStimulation()); near(h, energy.energy(), 0, "every parent-development tick pays"); }
            else { var developer = normal(p, cost); h.assertTrue(learnRequest(p, skill), "actual machine starts finite parent learning"); develop(p, count * type.ticksPerStimulation()); near(h, developer.battery().getEnergy(), 0, "every parent-machine tick pays"); }
            h.assertTrue(AbilityStorage.get(p).learned(skill), "finite native payment grants the actual parent"); }
        void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); if (machine != null) machine.endUsers();
            teams.forEach(h.getLevel().getScoreboard()::removePlayerTeam);
            for (ServerPlayer p : players) { CirculationRelief.remove(p); InertialAnchor.remove(p); VectorParry.remove(p); BrakingTouch.remove(p); DevelopmentController.remove(p); MachineDeveloperSessions.close(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.server.getPlayerList().remove(p); }
            entities.forEach(Entity::discard); channels.values().forEach(EmbeddedChannel::finishAndReleaseAll); }
    }
}

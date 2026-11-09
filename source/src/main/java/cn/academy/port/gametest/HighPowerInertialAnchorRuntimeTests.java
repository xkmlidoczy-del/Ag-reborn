package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.highpower.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW422: connected actors, real native hurt and accepted movement packets; no synthetic proof fixtures. */
@GameTestHolder("academy_inertial_anchor_runtime")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerInertialAnchorRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_inertial_anchor", ID = InertialAnchor.ID;
    private HighPowerInertialAnchorRuntimeTests() {}

    @SubscribeEvent public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int dimension : new int[]{32, 10, 32}) size.add(IntTag.valueOf(dimension));
        for (int axis = 0; axis < 3; axis++) position.add(IntTag.valueOf(0));
        cell.put("pos", position); cell.putInt("state", 0); blocks.add(cell); air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_inertial_anchor_runtime", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void front_native_zombie_halves_base_strength_and_pays_exact_one_percent_without_hp_absorption(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8), control = f.player(22, 8);
        f.ready(() -> {
            h.assertTrue(InertialAnchorNativeBridge.available(p), "reviewed native knockback seam installed");
            f.arm(p); BigDecimal health = hp(p), pool = shield(p); f.visuals(p);
            Zombie a = f.zombie(p.position().add(0, 0, 2)), b = f.zombie(control.position().add(0, 0, 2));
            h.assertTrue(f.hit(control, control.damageSources().mobAttack(b), 5), "ordinary native control accepts");
            h.assertTrue(f.hit(p, p.damageSources().mobAttack(a), 5), "native frontal hit accepts");
            eq(h, shield(p), pool.subtract(fee(p)), "one exact maximum-base-S fee");
            eq(h, hp(p), health.subtract(n("5")), "melee HP is never absorbed"); eq(h, hp(p), hp(control), "same native HP as control");
            vector(h, p.getDeltaMovement(), control.getDeltaMovement().scale(.5), "zero-momentum native base strength is halved");
            h.assertTrue(InertialAnchor.remainingHits(p) == 3 && f.contacts(p) == 1, "one committed contact owns one quota and receipt"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void rear_native_melee_preserves_full_impulse_and_base_shield(GameTestHelper h) { sector(h, new double[]{180}, new boolean[]{false}); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void horizontal_sector_accepts_inside_and_rejects_just_outside_sixty_degrees(GameTestHelper h) {
        sector(h, new double[]{59.99, 60.01, -59.99, -60.01}, new boolean[]{true, false, true, false});
    }
    private static void sector(GameTestHelper h, double[] angles, boolean[] admitted) {
        Fixture f = new Fixture(h); List<ServerPlayer> owners = new ArrayList<>();
        for (int i = 0; i < angles.length; i++) owners.add(f.player(5 + i * 7, 8));
        f.ready(() -> {
            for (int i = 0; i < owners.size(); i++) {
                ServerPlayer p = owners.get(i); f.arm(p); BigDecimal before = shield(p), health = hp(p);
                double angle = Math.toRadians(angles[i]); Zombie a = f.zombie(p.position().add(Math.sin(angle) * 2, 0, Math.cos(angle) * 2));
                h.assertTrue(f.hit(p, p.damageSources().mobAttack(a), 5), "real native sector hit");
                eq(h, shield(p), admitted[i] ? before.subtract(fee(p)) : before, "closed horizontal sector " + angles[i]);
                eq(h, hp(p), health.subtract(n("5")), "sector never adds melee HP absorption");
                near(h, p.getDeltaMovement().horizontalDistance(), admitted[i] ? (double).2F : (double).4F, "native impulse magnitude " + angles[i]);
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void unaffordable_fee_cannot_borrow_retained_reserve_or_partially_reduce(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            BigDecimal before = fee(p).subtract(BigDecimal.ONE); f.shield(p, before); CompoundTag reserve = reserve(p); f.arm(p);
            Zombie a = f.zombie(p.position().add(0, 0, 2)); f.hit(p, p.damageSources().mobAttack(a), 5);
            eq(h, shield(p), before, "insufficient base S stays exact");
            h.assertTrue(reserve.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound(MagneticDomainReserve.KEY)), "retained reserve cannot fund fee");
            near(h, p.getDeltaMovement().z, -(double).4F, "unaffordable hit remains ordinary");
            h.assertTrue(InertialAnchor.remainingHits(p) == 4, "unpaid hit consumes no quota"); h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void exact_fee_balance_commits_once_to_zero_without_refill(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { f.shield(p, fee(p)); f.arm(p); Zombie a = f.zombie(p.position().add(0, 0, 2));
            f.hit(p, p.damageSources().mobAttack(a), 5); eq(h, shield(p), BigDecimal.ZERO, "exact fee commits to zero");
            near(h, p.getDeltaMovement().z, -(double).2F, "last fully affordable native reduction applies");
            f.place(p); p.invulnerableTime = 0; f.hit(p, p.damageSources().mobAttack(a), 5);
            eq(h, shield(p), BigDecimal.ZERO, "second hit cannot refill or spend below zero"); near(h, p.getDeltaMovement().z, -(double).4F, "exhaustion keeps next native impulse ordinary"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void partial_native_resistance_is_applied_after_half_strength(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { p.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(.5); f.arm(p); BigDecimal before = shield(p);
            f.hit(p, p.damageSources().mobAttack(f.zombie(p.position().add(0, 0, 2))), 5);
            near(h, p.getDeltaMovement().z, -(double).2F * .5, "native resistance retains its ordinary multiplier"); eq(h, shield(p), before.subtract(fee(p)), "fee is flat and independent of resistance"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void full_native_resistance_is_free_and_grants_no_contact(GameTestHelper h) { rejectedKnockback(h, p -> p.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1), e -> {}); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void canceled_native_knockback_is_free(GameTestHelper h) { rejectedKnockback(h, p -> {}, e -> e.setCanceled(true)); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void listener_zero_strength_remains_free(GameTestHelper h) { rejectedKnockback(h, p -> {}, e -> e.setStrength(0)); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void listener_strength_rewrite_cannot_be_overwritten_or_charged(GameTestHelper h) { rejectedKnockback(h, p -> {}, e -> e.setStrength(.6F)); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void listener_ratio_rewrite_cannot_be_overwritten_or_charged(GameTestHelper h) { rejectedKnockback(h, p -> {}, e -> e.setRatioZ(-e.getRatioZ())); }
    private static void rejectedKnockback(GameTestHelper h, Consumer<ServerPlayer> setup, Consumer<LivingKnockBackEvent> callback) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8), control = f.player(22, 8);
        f.ready(() -> { setup.accept(p); setup.accept(control); f.arm(p); BigDecimal before = shield(p); f.visuals(p);
            f.hook(new KnockbackHook(p, callback)); f.hook(new KnockbackHook(control, callback));
            f.hit(control, control.damageSources().mobAttack(f.zombie(control.position().add(0, 0, 2))), 5);
            f.hit(p, p.damageSources().mobAttack(f.zombie(p.position().add(0, 0, 2))), 5);
            eq(h, shield(p), before, "rejected event leaves complete base pool unchanged"); eq(h, hp(p), hp(control), "ordinary native HP matches control");
            vector(h, p.getDeltaMovement(), control.getDeltaMovement(), "cancellation/resistance/listener owns native motion");
            h.assertTrue(InertialAnchor.remainingHits(p) == 4 && f.contacts(p) == 0, "rejected event yields no quota or contact"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void rejected_nested_hurt_retires_outer_fee_authority(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { f.arm(p); Zombie a = f.zombie(p.position().add(0, 0, 2)); BigDecimal before = shield(p); boolean[] called = {false};
            f.hook(new KnockbackHook(p, e -> { if (!called[0]) { called[0] = true; h.assertFalse(f.hit(p, p.damageSources().mobAttack(a), 5), "nested equal hit is genuinely iframe-rejected"); } }));
            f.hit(p, p.damageSources().mobAttack(a), 5); h.assertTrue(called[0], "native knockback callback reentered hurt");
            eq(h, shield(p), before, "rejected nested hurt still invalidates outer fee"); near(h, p.getDeltaMovement().z, -(double).4F, "outer native motion remains ordinary"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void recursive_knockback_preserves_native_control_momentum_without_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8), control = f.player(22, 8);
        f.ready(() -> { f.arm(p); BigDecimal before = shield(p);
            for (ServerPlayer target : List.of(p, control)) { boolean[] once = {false}; f.hook(new KnockbackHook(target, e -> { if (!once[0]) { once[0] = true; target.knockback(.2, 1, 0); } })); }
            for (ServerPlayer target : List.of(control, p)) f.hit(target, target.damageSources().mobAttack(f.zombie(target.position().add(0, 0, 2))), 5);
            eq(h, shield(p), before, "recursive dispatch cannot borrow pending payment"); vector(h, p.getDeltaMovement(), control.getDeltaMovement(), "nested and outer native impulses match control"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void direct_knockback_has_no_melee_receipt(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { f.arm(p); BigDecimal before = shield(p); p.knockback(.4F, 0, 1);
            eq(h, shield(p), before, "unrelated direct native knockback never spends S"); near(h, p.getDeltaMovement().z, -(double).4F, "direct impulse is unchanged");
            h.assertTrue(InertialAnchor.remainingHits(p) == 4, "direct knockback cannot spend admission quota"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void decorated_equipment_extra_knockback_husk_pvp_and_arrow_sources_remain_unsupported(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> owners = new ArrayList<>(); for (int i = 0; i < 5; i++) owners.add(f.player(4 + i * 6, 8)); ServerPlayer attackerPlayer = f.player(22, 17);
        f.ready(() -> { for (int i = 0; i < owners.size(); i++) {
            ServerPlayer p = owners.get(i); f.arm(p); BigDecimal before = shield(p), health = hp(p); LivingEntity a = f.zombie(p.position().add(0, 0, 2)); DamageSource source;
            if (i == 0) { ItemStack sword = new ItemStack(Items.IRON_SWORD); sword.set(DataComponents.UNBREAKABLE, new Unbreakable(true)); a.setItemSlot(EquipmentSlot.MAINHAND, sword); }
            if (i == 1) a.getAttribute(Attributes.ATTACK_KNOCKBACK).setBaseValue(1);
            if (i == 2) { Husk husk = new Husk(EntityType.HUSK, h.getLevel()); husk.setNoAi(true); husk.setNoGravity(true); husk.setPos(a.position()); f.entity(husk); a = husk; }
            if (i == 3) { attackerPlayer.setPos(p.position().add(0, 0, 2)); source = p.damageSources().playerAttack(attackerPlayer); }
            else if (i == 4) { Arrow arrow = new Arrow(h.getLevel(), a, new ItemStack(Items.ARROW), new ItemStack(Items.BOW)); arrow.setPos(p.position().add(0, 1, 2)); arrow.setDeltaMovement(0, 0, -.5); f.entity(arrow); source = p.damageSources().arrow(arrow, a); }
            else source = p.damageSources().mobAttack(a);
            h.assertTrue(f.hit(p, source, 5), "unsupported source still reaches native hurt " + i); eq(h, shield(p), before, "unsupported source has no fee " + i); eq(h, hp(p), health.subtract(n("5")), "unsupported source retains exact HP damage " + i);
            h.assertTrue(InertialAnchor.remainingHits(p) == 4, "unsupported source leaves quota " + i);
        } h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void canceled_incoming_damage_has_no_impulse_or_fee(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { f.arm(p); BigDecimal before = shield(p), health = hp(p); f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) { if (e.getEntity() == p) e.setCanceled(true); } });
            h.assertFalse(f.hit(p, p.damageSources().mobAttack(f.zombie(p.position().add(0, 0, 2))), 5), "native cancellation returns false"); eq(h, shield(p), before, "canceled intake free"); eq(h, hp(p), health, "canceled native damage stays zero"); vector(h, p.getDeltaMovement(), Vec3.ZERO, "no invented knockback"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_melee_armor_effects_enchantments_and_absorption_match_ordinary_control(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8), control = f.player(22, 8);
        f.ready(() -> { defenses(h, p); defenses(h, control); f.arm(p); BigDecimal before = shield(p); Observation a = f.observe(p), b = f.observe(control);
            for (ServerPlayer target : List.of(control, p)) f.hit(target, target.damageSources().mobAttack(f.zombie(target.position().add(0, 0, 2))), 20);
            eq(h, hp(p), hp(control), "all native melee HP mitigation matches"); h.assertTrue(a.posts == 1 && b.posts == 1 && a.damage == b.damage && a.armor == b.armor && a.effects == b.effects && a.enchantments == b.enchantments && a.absorption == b.absorption, "every native reducer executes once identically");
            h.assertTrue(a.armor > 0 && a.effects > 0 && a.enchantments > 0 && a.absorption > 0, "paired test actually exercised native reducers"); eq(h, shield(p), before.subtract(fee(p)), "only flat knockback fee is charged"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=105)
    public static void four_positive_native_reductions_per_tick_and_release_toggle_cannot_reset_quota(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { f.arm(p); BigDecimal before = shield(p), health = hp(p); Zombie a = f.zombie(p.position().add(0, 0, 2));
            for (int i = 0; i < 5; i++) { f.place(p); p.invulnerableTime = 0; f.hit(p, p.damageSources().mobAttack(a), 5); near(h, p.getDeltaMovement().z, i < 4 ? -(double).2F : -(double).4F, "physical admission number " + i); }
            eq(h, shield(p), before.subtract(fee(p).multiply(n("4"))), "exactly four flat fees"); eq(h, hp(p), health.subtract(n("25")), "all five melee hits retain HP damage");
            h.assertTrue(InertialAnchor.remainingHits(p) == 0, "quota exhausted"); h.assertTrue(InertialAnchor.release(p, f.held.get(p)), "release acknowledged"); h.assertTrue(InertialAnchor.active(p), "release retains toggle");
            h.assertTrue(f.start(p), "fresh press ends toggle"); h.assertFalse(f.start(p), "same-tick reactivation blocked"); h.assertTrue(InertialAnchor.remainingHits(p) == 0, "toggle cannot reset physical quota");
            h.runAfterDelay(1, () -> { h.assertTrue(InertialAnchor.remainingHits(p) == 4, "only new native server tick renews quota"); h.succeed(); }); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_reload_preserves_balances_and_quota_but_removes_owned_modifier(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { f.arm(p); f.hit(p, p.damageSources().mobAttack(f.zombie(p.position().add(0, 0, 2))), 5);
            BigDecimal before = shield(p), health = hp(p); CompoundTag quota = p.getPersistentData().getCompound(InertialAnchor.QUOTA_KEY).copy(); f.reload(p);
            h.assertFalse(InertialAnchor.active(p), "reload never silently restores toggle"); h.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(InertialAnchor.MOVEMENT_MODIFIER) == null, "owned transient modifier cleaned");
            eq(h, shield(p), before, "reload preserves exact shield"); eq(h, hp(p), health, "reload preserves injury"); h.assertTrue(quota.equals(p.getPersistentData().getCompound(InertialAnchor.QUOTA_KEY)) && InertialAnchor.remainingHits(p) == 3, "same-tick persisted quota retained"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_death_cleans_owned_modifier_and_preserves_unrelated_modifier(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { AttributeModifier other = new AttributeModifier(ResourceLocation.fromNamespaceAndPath("academy", "anchor_test_unrelated"), .1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL); p.getAttribute(Attributes.MOVEMENT_SPEED).addTransientModifier(other); f.arm(p);
            h.assertTrue(f.hit(p, p.damageSources().genericKill(), Float.MAX_VALUE), "native lethal command damage"); h.assertFalse(p.isAlive(), "native death completed"); h.assertFalse(InertialAnchor.active(p), "death ends toggle");
            h.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(InertialAnchor.MOVEMENT_MODIFIER) == null, "own movement modifier removed"); h.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(other.id()) == other, "unrelated modifier retained"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void toggle_release_and_off_preserve_owned_movement_modifier_identity(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED); f.arm(p); double cp = HighPowerResources.cp(p);
            near(h, p.getAttributeValue(Attributes.MOVEMENT_SPEED), speed * .8, "active movement speed costs twenty percent"); Object modifier = p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(InertialAnchor.MOVEMENT_MODIFIER);
            h.assertTrue(InertialAnchor.release(p, f.held.get(p)) && InertialAnchor.active(p), "release retains paid toggle"); near(h, HighPowerResources.cp(p), cp, "release has no extra payment");
            h.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(InertialAnchor.MOVEMENT_MODIFIER) == modifier, "release does not replace modifier"); h.assertTrue(f.start(p), "fresh press toggles off");
            near(h, p.getAttributeValue(Attributes.MOVEMENT_SPEED), speed, "toggle-off restores prior speed"); near(h, HighPowerResources.cp(p), cp, "toggle-off is free"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void airborne_crouched_fast_and_unsupported_activation_are_refused_free(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> players = new ArrayList<>(); for (int i = 0; i < 4; i++) players.add(f.player(5 + i * 7, 8));
        f.ready(() -> { for (int i = 0; i < players.size(); i++) { ServerPlayer p = players.get(i); double cp = HighPowerResources.cp(p);
            if (i == 0) p.setOnGround(false); else if (i == 1) { p.setShiftKeyDown(true); p.setPose(Pose.CROUCHING); } else if (i == 2) p.setDeltaMovement(.051, 0, 0); else p.setPos(p.position().add(0, 2, 0));
            h.assertFalse(f.start(p), "unstable activation rejected " + i); near(h, HighPowerResources.cp(p), cp, "rejected activation is free"); h.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(InertialAnchor.MOVEMENT_MODIFIER) == null, "no leaked movement modifier");
        } h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void parry_braking_and_anchor_exclude_each_other_in_both_directions(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer a = f.player(6, 8), b = f.player(16, 8), c = f.player(26, 8);
        f.ready(() -> { f.arm(a); double cp = HighPowerResources.cp(a); h.assertFalse(VectorParry.start(a, f.wire(a, 1)), "anchor blocks parry"); a.setShiftKeyDown(true); a.setPose(Pose.CROUCHING); h.assertFalse(BrakingTouch.start(a, f.wire(a, 2)), "anchor blocks braking"); near(h, HighPowerResources.cp(a), cp, "blocked sibling inputs are free");
            h.assertTrue(VectorParry.start(b, f.wire(b, 1)), "parry starts independently"); h.assertFalse(f.start(b), "parry blocks anchor");
            c.setShiftKeyDown(true); c.setPose(Pose.CROUCHING); h.assertTrue(BrakingTouch.start(c, f.wire(c, 2)), "braking landing arms independently"); c.setShiftKeyDown(false); c.setPose(Pose.STANDING); h.assertFalse(f.start(c), "braking blocks anchor"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void genuine_landing_absorbs_partial_base_s_then_exact_residual_hp_without_motion_rewrite(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8), control = f.player(22, 8);
        f.ready(() -> { f.shield(p, n("2")); f.arm(p); BigDecimal health = hp(p), otherHealth = hp(control); double cp = HighPowerResources.cp(p);
            f.land(control, 8); f.land(p, 8); eq(h, otherHealth.subtract(hp(control)), n("5"), "native fall control is genuinely damaging");
            eq(h, shield(p), BigDecimal.ZERO, "exact two base S spent one-to-one"); eq(h, health.subtract(hp(p)), n("3"), "only three residual reaches HP");
            vector(h, p.getDeltaMovement(), control.getDeltaMovement(), "landing preserves native momentum"); near(h, p.fallDistance, control.fallDistance, "only native code resets fall distance"); near(h, HighPowerResources.cp(p), cp, "landing adds no CP fee");
            h.assertTrue(InertialAnchor.remainingHits(p) == 3, "positive native fall absorption uses one admission"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void canceled_zero_and_harmless_native_landings_are_free(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> players = List.of(f.player(6, 8), f.player(16, 8), f.player(26, 8));
        f.ready(() -> { for (int i = 0; i < players.size(); i++) { ServerPlayer p = players.get(i); f.arm(p); final int mode = i; int[] calls = {0};
            f.hook(new Object() { @SubscribeEvent public void fall(LivingFallEvent e) { if (e.getEntity() == p) { calls[0]++; if (mode == 0) e.setCanceled(true); else if (mode == 1) e.setDamageMultiplier(0); } } });
            BigDecimal before = shield(p), health = hp(p); f.land(p, i == 2 ? 2 : 8); eq(h, shield(p), before, "native harmless landing never charges"); eq(h, hp(p), health, "native zero remains zero"); h.assertTrue(calls[0] == 1 && InertialAnchor.remainingHits(p) == 4, "real fall callback but no paid admission");
        } h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void synthetic_fall_event_hurt_and_direct_cause_never_gain_landing_absorption(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { f.arm(p); BigDecimal before = shield(p), health = hp(p); NeoForge.EVENT_BUS.post(new LivingFallEvent(p, 8, 1));
            h.assertTrue(p.causeFallDamage(8, 1, p.damageSources().fall()), "direct native cause performs ordinary damage"); eq(h, hp(p), health.subtract(n("5")), "direct cause has no accepted movement authority");
            p.invulnerableTime = 0; f.hit(p, p.damageSources().fall(), 5); eq(h, hp(p), health.subtract(n("10")), "arbitrary FALL source has no ground-contact authority"); eq(h, shield(p), before, "synthetic falls never spend base S"); h.assertTrue(InertialAnchor.remainingHits(p) == 4, "synthetic falls keep quota"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_fall_protection_resistance_and_absorption_precede_base_s_once(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8), control = f.player(22, 8);
        f.ready(() -> { for (ServerPlayer target : List.of(p, control)) { ItemStack boots = new ItemStack(Items.LEATHER_BOOTS); boots.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4); target.setItemSlot(EquipmentSlot.FEET, boots); target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0)); target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(2); target.setAbsorptionAmount(.25F); }
            f.shield(p, n(".5")); f.arm(p); BigDecimal health = hp(p), controlHealth = hp(control); f.land(control, 8); f.land(p, 8); BigDecimal damage = controlHealth.subtract(hp(control));
            h.assertTrue(damage.compareTo(n(".5")) > 0, "native controls have positive postmitigation damage"); eq(h, shield(p), BigDecimal.ZERO, "half point base S exhausted"); eq(h, health.subtract(hp(p)), damage.subtract(n(".5")), "postmitigation fall residual charged once"); near(h, p.getAbsorptionAmount(), control.getAbsorptionAmount(), "native absorption consumed identically"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void synthetic_post_inside_real_fall_pre_cannot_steal_or_duplicate_ground_receipt(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { f.shield(p, n("2")); f.arm(p); BigDecimal health = hp(p); boolean[] injected = {false};
            f.hook(new Object() { @SubscribeEvent public void pre(LivingDamageEvent.Pre e) { if (e.getEntity() == p && e.getSource() == p.damageSources().fall() && !injected[0]) { injected[0] = true; NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(p, e.getContainer())); } } });
            f.land(p, 8); h.assertTrue(injected[0], "forged Post was posted inside actual native fall transaction"); eq(h, shield(p), BigDecimal.ZERO, "only genuine Post spends two S"); eq(h, hp(p), health.subtract(n("3")), "forged Post cannot double exact HP or steal true settlement"); h.assertTrue(InertialAnchor.remainingHits(p) == 3, "one genuine fall admission"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_lethal_fall_keeps_spent_shield_and_does_not_resurrect_or_reset_velocity(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { f.shield(p, n("2")); f.arm(p); p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health", "1"); f.land(p, 8);
            eq(h, shield(p), BigDecimal.ZERO, "lethal residual retains committed shield spend"); eq(h, hp(p), BigDecimal.ZERO, "fall residual is lethal"); h.assertFalse(p.isAlive(), "ordinary native death remains authoritative"); h.assertFalse(InertialAnchor.active(p), "death retires toggle"); h.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(InertialAnchor.MOVEMENT_MODIFIER) == null, "death removes owned modifier"); h.succeed(); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void airborne_melee_keeps_full_native_impulse_while_toggle_remains_active(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { f.arm(p); BigDecimal before = shield(p), health = hp(p); p.setPos(p.position().add(0, 1, 0)); p.setOnGround(false);
            f.hit(p, p.damageSources().mobAttack(f.zombie(p.position().add(0, 0, 2))), 5);
            eq(h, shield(p), before, "airborne toggle grants no melee benefit"); eq(h, hp(p), health.subtract(n("5")), "airborne HP remains ordinary");
            near(h, p.getDeltaMovement().z, -(double).4F, "airborne native strength remains full"); near(h, p.getDeltaMovement().y, 0, "no invented ground lift"); h.assertTrue(InertialAnchor.active(p), "airborne transition itself does not silently toggle off"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void replacing_owned_modifier_with_foreign_same_id_never_removes_foreign_instance(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { f.arm(p); var speed = p.getAttribute(Attributes.MOVEMENT_SPEED); speed.removeModifier(InertialAnchor.MOVEMENT_MODIFIER);
            AttributeModifier foreign = new AttributeModifier(InertialAnchor.MOVEMENT_MODIFIER, -.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL); speed.addTransientModifier(foreign);
            InertialAnchor.tick(p); h.assertFalse(InertialAnchor.active(p), "replacement revokes controller identity"); h.assertTrue(speed.getModifier(InertialAnchor.MOVEMENT_MODIFIER) == foreign, "cleanup preserves exact foreign replacement instance"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void fake_ground_stationary_support_collision_correction_and_removed_support_never_spend_s(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> players = List.of(f.player(8, 8), f.player(22, 8), f.player(8, 22), f.player(22, 22));
        f.ready(() -> { for (int mode = 0; mode < players.size(); mode++) {
            ServerPlayer p = players.get(mode); f.arm(p); BigDecimal before = shield(p); Vec3 floor = f.anchors.get(p), origin, target;
            if (mode == 0) { target = floor.add(0, 3, 0); origin = target.add(0, .25, 0); }
            else if (mode == 1) { origin = floor; target = floor; }
            else if (mode == 2) { origin = floor.add(0, .5956327336298841, 0); target = floor.add(1.3, 0, 0); BlockPos obstruction = BlockPos.containing(target); h.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState()); h.getLevel().setBlockAndUpdate(obstruction.above(), Blocks.STONE.defaultBlockState()); }
            else { origin = floor.add(0, .25, 0); target = floor; f.hook(new Object() { @SubscribeEvent public void fall(LivingFallEvent e) { if (e.getEntity() == p) h.getLevel().setBlockAndUpdate(BlockPos.containing(floor).below(), Blocks.AIR.defaultBlockState()); } }); }
            p.setNoGravity(false); p.setPos(origin); p.setOnGround(false); p.fallDistance = 8; p.setDeltaMovement(target.subtract(origin)); p.connection.resetPosition();
            p.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(target.x, target.y, target.z, true));
            if (mode == 2) h.assertTrue(p.position().equals(origin), "native collision correction really returned to source");
            eq(h, shield(p), before, "uncertified native movement remains free " + mode); h.assertTrue(InertialAnchor.remainingHits(p) == 4, "invalid ground proof grants no quota " + mode);
        } h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void nested_hurt_in_real_fall_post_accounts_both_native_hits_without_ground_absorption(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { f.shield(p, n("20")); f.arm(p); BigDecimal before = shield(p), health = hp(p); boolean[] nested = {false};
            f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) {
                if (e.getEntity() != p || e.getSource() != p.damageSources().fall() || nested[0]) return;
                nested[0] = true; p.invulnerableTime = 0; h.assertTrue(f.hit(p, p.damageSources().magic(), 2), "nested native hurt is accepted inside real fall Post");
            } });
            f.land(p, 8); h.assertTrue(nested[0], "real fall Post callback reentered hurt"); eq(h, shield(p), before, "nested hurt revokes every outer ground receipt");
            eq(h, hp(p), health.subtract(n("7")), "both native damage transactions remain accounted"); h.assertTrue(InertialAnchor.remainingHits(p) == 4, "reentrant fall has no admitted absorption"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void synthetic_common_hooks_post_inside_actual_post_preserves_only_original_event_authority(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { f.shield(p, n("2")); f.arm(p); BigDecimal health = hp(p); boolean[] synthetic = {false};
            f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) {
                if (e.getEntity() != p || e.getSource() != p.damageSources().fall() || synthetic[0]) return;
                synthetic[0] = true;
                net.neoforged.neoforge.common.CommonHooks.onLivingDamagePost(p, new DamageContainer(e.getSource(), e.getNewDamage()));
                eq(h, shield(p), n("2"), "nested synthetic CommonHooks event cannot consume original event's S");
            } });
            f.land(p, 8); h.assertTrue(synthetic[0], "synthetic CommonHooks dispatch occurred inside original Post");
            eq(h, shield(p), BigDecimal.ZERO, "original actual Post retains its one ground absorption"); eq(h, hp(p), health.subtract(n("3")), "nested synthetic Post cannot duplicate HP settlement");
            h.assertTrue(InertialAnchor.remainingHits(p) == 3, "only original identity consumes quota"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void reposting_same_actual_fall_post_object_cannot_settle_nested_dispatch(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { f.shield(p, n("2")); f.arm(p); BigDecimal health = hp(p); boolean[] reposted = {false};
            f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) {
                if (e.getEntity() != p || e.getSource() != p.damageSources().fall() || reposted[0]) return;
                reposted[0] = true;
                NeoForge.EVENT_BUS.post(e);
                eq(h, shield(p), n("2"), "reposted exact same event object cannot settle from nested dispatch");
                h.assertTrue(InertialAnchor.remainingHits(p) == 4, "nested same-object repost cannot consume quota");
            } });
            f.land(p, 8); h.assertTrue(reposted[0], "same actual native Post object was recursively dispatched");
            eq(h, shield(p), BigDecimal.ZERO, "original dispatch retains exactly one two-S absorption");
            eq(h, hp(p), health.subtract(n("3")), "same-object replay cannot duplicate or steal original HP settlement");
            h.assertTrue(InertialAnchor.remainingHits(p) == 3, "only original native dispatch consumes one quota"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void feather_falling_refuses_activation_free_and_keeps_paired_native_fall_enchantment(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8), control = f.player(22, 8);
        f.ready(() -> {
            var feather = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FEATHER_FALLING);
            for (ServerPlayer target : List.of(p, control)) { ItemStack boots = new ItemStack(Items.LEATHER_BOOTS); boots.enchant(feather, 4); target.setItemSlot(EquipmentSlot.FEET, boots); }
            double cp = HighPowerResources.cp(p); BigDecimal before = shield(p), health = hp(p), controlHealth = hp(control);
            h.assertTrue(HighPowerCombat.unsupportedReason(p).isPresent(), "Feather Falling remains outside the certified compatibility contract");
            h.assertFalse(f.start(p), "unsupported Feather Falling refuses anchor activation"); near(h, HighPowerResources.cp(p), cp, "unsupported activation costs no CP");
            h.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(InertialAnchor.MOVEMENT_MODIFIER) == null, "refusal installs no movement modifier");
            f.land(control, 8); f.land(p, 8); BigDecimal nativeDamage = controlHealth.subtract(hp(control));
            h.assertTrue(nativeDamage.signum() > 0 && nativeDamage.compareTo(n("5")) < 0, "paired native Feather Falling genuinely reduces fall damage");
            eq(h, health.subtract(hp(p)), nativeDamage, "refused activation preserves ordinary native Feather Falling damage"); eq(h, shield(p), before, "unsupported fall spends no S");
            h.assertTrue(p.getItemBySlot(EquipmentSlot.FEET).getOrDefault(DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).getLevel(feather) == 4, "native Feather Falling enchantment is retained"); h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void enchantment_only_boot_swap_in_real_fall_pre_refuses_s_and_retains_native_mitigation_order(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8), control = f.player(22, 8);
        f.ready(() -> {
            var feather = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FEATHER_FALLING);
            for (ServerPlayer target : List.of(p, control)) target.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            f.arm(p); BigDecimal before = shield(p), health = hp(p), controlHealth = hp(control); boolean[] changed = {false, false};
            f.hook(new Object() { @SubscribeEvent public void pre(LivingDamageEvent.Pre e) {
                int index = e.getEntity() == p ? 0 : e.getEntity() == control ? 1 : -1;
                if (index < 0 || changed[index] || e.getSource() != e.getEntity().damageSources().fall()) return;
                changed[index] = true; int armor = e.getEntity().getArmorValue(); ItemStack boots = new ItemStack(Items.LEATHER_BOOTS); boots.enchant(feather, 4); e.getEntity().setItemSlot(EquipmentSlot.FEET, boots);
                h.assertTrue(e.getEntity().getArmorValue() == armor, "callback changes enchantment compatibility without changing armor value");
            } });
            f.land(control, 8); f.land(p, 8);
            h.assertTrue(changed[0] && changed[1], "both actual native fall Pre callbacks installed Feather Falling");
            BigDecimal initialDamage = controlHealth.subtract(hp(control)); eq(h, health.subtract(hp(p)), initialDamage, "current native hit retains the already-computed mitigation order");
            eq(h, shield(p), before, "new unsupported enchantment blocks enhancement before S publication"); h.assertTrue(InertialAnchor.remainingHits(p) == 4, "late incompatibility cannot consume quota");
            health = hp(p); controlHealth = hp(control); p.invulnerableTime = 0; control.invulnerableTime = 0;
            f.land(control, 8); f.land(p, 8); BigDecimal featherDamage = controlHealth.subtract(hp(control));
            h.assertTrue(featherDamage.signum() > 0 && featherDamage.compareTo(initialDamage) < 0, "retained Feather Falling normally reduces the next genuine native fall");
            eq(h, health.subtract(hp(p)), featherDamage, "unsupported active toggle retains the same native enchanted fall damage as control"); eq(h, shield(p), before, "neither incompatible fall spends S"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void nested_hurt_then_synthetic_same_source_post_preserves_original_fall_accounting_guard(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8);
        f.ready(() -> { f.shield(p, n("20")); f.arm(p); BigDecimal before = shield(p), health = hp(p); boolean[] injected = {false};
            f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) {
                if (e.getEntity() != p || e.getSource() != p.damageSources().fall() || injected[0]) return;
                injected[0] = true; p.invulnerableTime = 0;
                h.assertTrue(f.hit(p, p.damageSources().magic(), 2), "nested actual hurt completes before synthetic fall Post");
                eq(h, hp(p), health.subtract(n("2")), "nested actual hit settles while original outer fall still awaits its Post handler");
                NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(p, new DamageContainer(e.getSource(), e.getNewDamage())));
                eq(h, hp(p), health.subtract(n("2")), "synthetic same-source Post cannot settle early after defense veto");
                eq(h, shield(p), before, "nested hurt and synthetic Post never spend ground-contact shield");
                h.assertTrue(InertialAnchor.remainingHits(p) == 4, "vetoed benefit and forged event consume no quota");
            } });
            f.land(p, 8); h.assertTrue(injected[0], "actual fall Post performed both nested operations");
            eq(h, hp(p), health.subtract(n("7")), "original native fall and nested actual hit each settle exactly once");
            eq(h, shield(p), before, "ordinary fall accounting survives without restoring defense authority");
            h.assertTrue(InertialAnchor.remainingHits(p) == 4, "combined reentry never regains admission quota"); h.succeed(); });
    }

    private static BigDecimal n(String text) { return new BigDecimal(text); }
    private static BigDecimal hp(ServerPlayer p) { return n(HighPowerCombat.healthText(p)); }
    private static BigDecimal shield(ServerPlayer p) { return HighPowerCombat.shield(p); }
    private static BigDecimal fee(ServerPlayer p) { return n(HighPowerCombat.maxShieldText(p)).movePointLeft(2); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) { h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected); }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-6, why + ": " + actual + " != " + expected); }
    private static void vector(GameTestHelper h, Vec3 actual, Vec3 expected, String why) { h.assertTrue(actual.distanceToSqr(expected) < 1e-12, why + ": " + actual + " != " + expected); }
    private static CompoundTag reserve(ServerPlayer p) { CompoundTag r = new CompoundTag(); r.putInt("version", MagneticDomainReserve.VERSION); r.putString("amount", "9"); r.putString("capacity", HighPowerCombat.maxShieldText(p)); r.putString("lower", "4"); r.putString("upper", "5"); p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).put(MagneticDomainReserve.KEY, r); return r.copy(); }
    private static void defenses(GameTestHelper h, ServerPlayer p) { ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET); helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4); helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(true)); p.setItemSlot(EquipmentSlot.HEAD, helmet); p.getAttribute(Attributes.ARMOR).setBaseValue(5); p.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2); p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0)); p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0)); p.setAbsorptionAmount(.5F); }
    public static final class KnockbackHook { final ServerPlayer owner; final Consumer<LivingKnockBackEvent> callback; KnockbackHook(ServerPlayer owner, Consumer<LivingKnockBackEvent> callback) { this.owner = owner; this.callback = callback; } @SubscribeEvent public void event(LivingKnockBackEvent e) { if (e.getEntity() == owner) callback.accept(e); } }
    public static final class Observation { final ServerPlayer owner; int posts; float damage, armor, effects, enchantments, absorption; Observation(ServerPlayer owner) { this.owner = owner; } @SubscribeEvent public void post(LivingDamageEvent.Post e) { if (e.getEntity() != owner) return; posts++; damage = e.getNewDamage(); armor = e.getReduction(DamageContainer.Reduction.ARMOR); effects = e.getReduction(DamageContainer.Reduction.MOB_EFFECTS); enchantments = e.getReduction(DamageContainer.Reduction.ENCHANTMENTS); absorption = e.getReduction(DamageContainer.Reduction.ABSORPTION); } }

    @GameTest(template=TEMPLATE,batch="inertial_anchor_category_efficiency",timeoutTicks=100)
    public static void category_real_native_landing_halves_actual_cost_vs_uncast_category_shield(GameTestHelper h) {
        forceEfficiencyCategory(h);Fixture f=new Fixture(h);ServerPlayer p=f.player(8,8),control=f.player(22,8);
        f.ready(()->{
            f.shield(p,n("20"));f.shield(control,n("20"));f.arm(p);BigDecimal a=hp(p).add(shield(p)),b=hp(control).add(shield(control));
            f.land(control,8);f.land(p,8);BigDecimal baseline=b.subtract(hp(control).add(shield(control))),actual=a.subtract(hp(p).add(shield(p)));
            h.assertTrue(baseline.signum()>0,"real native no-cast category landing actually costs resources");
            eq(h,actual,baseline.multiply(n(".5")),"active anchor costs exactly half of same accepted native landing including normal environment scaling");
            h.assertTrue(InertialAnchor.remainingHits(p)==3,"positive real landing spends one existing admission");h.succeed();
        });
    }
    private static void forceEfficiencyCategory(GameTestHelper h) {
        EsperLegacyBypass.force(true);
        h.testInfo.addListener(new GameTestListener(){boolean closed;private void close(){if(!closed){closed=true;EsperLegacyBypass.force(false);}}
            public void testStructureLoaded(GameTestInfo i){} public void testPassed(GameTestInfo i,GameTestRunner r){close();}
            public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
    }
    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>(); final List<Object> hooks = new ArrayList<>();
        final Map<ServerPlayer, EmbeddedChannel> channels = new IdentityHashMap<>(); final Map<ServerPlayer, Vec3> anchors = new IdentityHashMap<>();
        final Map<ServerPlayer, Long> inputs = new IdentityHashMap<>(); final Map<ServerPlayer, String> held = new IdentityHashMap<>(); boolean closed;
        Fixture(GameTestHelper h) { this.h = h; h.testInfo.addListener(new GameTestListener() { public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); } public void testFailed(GameTestInfo i, GameTestRunner r) { close(); } public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); } }); }
        ServerPlayer player(double x, double z) {
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "IA" + UUID.randomUUID().toString().substring(0, 10)), false);
            ServerPlayer p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            Connection connection = new Connection(PacketFlow.SERVERBOUND); EmbeddedChannel channel = new EmbeddedChannel(connection); channels.put(p, channel); NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); drain(p);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 4; dz++) h.setBlock(BlockPos.containing(x + dx, 1, z + dz), Blocks.STONE);
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); anchors.put(p, h.absoluteVec(new Vec3(x + .5, 2, z + .5))); place(p); h.assertTrue(HighPowerCombat.enroll(p, 2), "real connected LV2 Vector enrollment");
            var state = AbilityStorage.get(p); state.selectCategory(HighPowerCategories.VECMANIP); state.setLevel(2); state.learn(HighPowerSkills.DEFLECTION_STRIKE); state.learn(VectorParry.ID); state.learn(BrakingTouch.ID); state.learn(ID); state.activated = true; state.overloadFine = true;
            h.assertTrue(state.presets.edit(0, 0, ID, state::learned) && state.presets.edit(0, 1, VectorParry.ID, state::learned) && state.presets.edit(0, 2, BrakingTouch.ID, state::learned), "real learned bindings for bidirectional exclusion");
            HighPowerResources.initializeBudget(p); hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5)); return p;
        }
        void ready(Runnable action) { h.runAfterDelay(61, () -> { for (ServerPlayer p : players) { p.doTick(); place(p); } action.run(); }); }
        void place(ServerPlayer p) { Vec3 at = anchors.get(p); p.moveTo(at.x, at.y, at.z, 0, 0); p.setYHeadRot(0); p.yBodyRot = 0; p.setShiftKeyDown(false); p.setPose(Pose.STANDING); p.setOnGround(true); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        String wire(ServerPlayer p, int slot) { long next = inputs.getOrDefault(p, 0L) + 1; inputs.put(p, next); return new HighPowerInput.Input(slot, next, p.getId(), LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p))).wire(); }
        boolean start(ServerPlayer p) { String wire = wire(p, 0); boolean result = InertialAnchor.start(p, wire); if (result) held.put(p, wire); return result; }
        void arm(ServerPlayer p) { h.assertTrue(p.onGround() && p.getDeltaMovement().horizontalDistance() == 0, "activation uses stationary supported actor"); h.assertTrue(SafeLandingProbeWorld.sourceRefusal(p).isEmpty(), "full native support and clearance"); h.assertTrue(start(p), "authenticated anchor toggle starts"); }
        void shield(ServerPlayer p, BigDecimal amount) { p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("shield", amount.toPlainString()); }
        Zombie zombie(Vec3 at) { Zombie a = new Zombie(EntityType.ZOMBIE, h.getLevel()); a.setNoAi(true); a.setNoGravity(true); a.setSilent(true); a.setPos(at); a.getAttribute(Attributes.ARMOR).setBaseValue(0); a.getAttribute(Attributes.ATTACK_KNOCKBACK).setBaseValue(0); entity(a); return a; }
        void entity(Entity e) { h.assertTrue(h.getLevel().addFreshEntity(e), "actual entity joined server level"); entities.add(e); }
        boolean hit(ServerPlayer p, DamageSource source, float amount) { boolean previous = p.server.isPvpAllowed(); p.server.setPvpAllowed(true); try { return p.hurt(source, amount); } finally { p.server.setPvpAllowed(previous); } }
        void land(ServerPlayer p, float distance) { Vec3 floor = anchors.get(p); p.setNoGravity(false); p.setPos(floor.add(0, .25, 0)); p.setOnGround(false); p.fallDistance = distance; p.setDeltaMovement(0, -.25, 0); p.connection.resetPosition(); p.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(floor.x, floor.y, floor.z, true)); h.assertTrue(p.position().distanceToSqr(floor) < 1e-10, "real native packet accepted supported ground contact"); }
        void reload(ServerPlayer p) { AbilityStorage.save(p); CompoundTag saved = p.saveWithoutId(new CompoundTag()); InertialAnchor.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.load(saved); place(p); inputs.put(p, 0L); }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        Observation observe(ServerPlayer p) { Observation result = new Observation(p); hook(result); return result; }
        List<Object> drain(ServerPlayer p) { EmbeddedChannel channel = channels.get(p); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); List<Object> packets = new ArrayList<>(); Object packet; while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); packets.add(packet); ReferenceCountUtil.release(packet); } return packets; }
        List<InertialAnchor.Visual> visuals(ServerPlayer p) { List<InertialAnchor.Visual> result = new ArrayList<>(); for (Object packet : drain(p)) if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof InertialAnchor.Visual v && v.owner().equals(p.getUUID())) result.add(v); return result; }
        long contacts(ServerPlayer p) { return visuals(p).stream().filter(v -> v.phase() == InertialAnchor.KNOCKBACK || v.phase() == InertialAnchor.LANDED).count(); }
        void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); for (ServerPlayer p : players) { InertialAnchor.remove(p); VectorParry.remove(p); BrakingTouch.remove(p); DevelopmentController.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.server.getPlayerList().remove(p); } entities.forEach(Entity::discard); channels.values().forEach(EmbeddedChannel::finishAndReleaseAll); }
    }
}

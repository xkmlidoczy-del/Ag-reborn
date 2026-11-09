package cn.academy.port.gametest;

import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.ShortInterceptionArrowBridge;
import cn.academy.port.highpower.ScatterPlates;
import cn.academy.port.highpower.ScatterPlateArrowReceipts;
import cn.academy.port.highpower.DissipationCurtain;
import cn.academy.port.highpower.DissipationCurtainArrowReceipts;
import cn.academy.port.highpower.ZeroGapArmorArrowReceipts;
import cn.academy.port.highpower.core.DamageMitigation;
import cn.academy.port.highpower.core.DefenseProfile;
import java.math.BigDecimal;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import cn.academy.port.gametest.HighPowerShortInterceptionRuntimeTests.Fixture;

/** NEW331 native body acceptance: real release and arrow.tick, never a fabricated bridge permit. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerShortInterceptionCombatRuntimeTests {
    private static final String TEMPLATE = HighPowerShortInterceptionRuntimeTests.TEMPLATE;
    private static final String BATCH = "highpower_short_interception_combat";
    private HighPowerShortInterceptionCombatRuntimeTests() {}
    private static BigDecimal hp(LivingEntity target) { return new BigDecimal(HighPowerCombat.healthText(target)); }
    private static BigDecimal raw(Fixture f) { return HighPowerGrowth.values(f.p).damage().multiply(new BigDecimal("1.8")); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) {
        h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected);
    }
    private static void hit(Arrow arrow) { for (int i = 0; i < 4 && !arrow.isRemoved(); i++) arrow.tick(); }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void exact_native_arrow_replaces_one_body_amount_and_keeps_original_shooter(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(HighPowerCombat.enroll(f.target, 5), "declared exact native hostile");
        Watch watch = new Watch(f.target); f.hook(watch); BigDecimal before = hp(f.target), amount = raw(f);
        f.ready(() -> {
            Arrow arrow = f.arrow(3, 0); double base = arrow.getBaseDamage();
            h.assertTrue(f.tap(), "real release relocates one arrow"); hit(arrow);
            eq(h, hp(f.target), before.subtract(amount), "only frozen 1.8D is charged, with no old native damage replay");
            h.assertValueEqual(watch.incoming, 1, "one actual native body hurt supplies one incoming event");
            h.assertValueEqual(watch.posts, 1, "one actual native HP commit");
            h.assertTrue(watch.source.is(DamageTypes.ARROW) && watch.source.is(DamageTypeTags.IS_PROJECTILE)
                    && watch.source.getDirectEntity() == arrow && watch.source.getEntity() == f.shooter,
                    "native ARROW/direct Arrow/original shooter survive controller substitution");
            h.assertTrue(watch.source.sourcePositionRaw() == null && watch.origin != null && arrow.getOwner() == f.shooter
                    && arrow.getBaseDamage() == base && arrow.isRemoved(), "native position mode, owner, baseDamage and successful discard continuation remain native");
            var ordinary = f.target.damageSources().arrow(arrow, f.shooter);
            h.assertTrue(watch.source.getLocalizedDeathMessage(f.target).equals(ordinary.getLocalizedDeathMessage(f.target)),
                    "native localized death credit remains the original shooter");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void native_equal_iframes_accept_only_first_frozen_amount(GameTestHelper h) {
        Fixture f = new Fixture(h); HighPowerCombat.enroll(f.target, 5); Watch watch = new Watch(f.target); f.hook(watch);
        BigDecimal before = hp(f.target), amount = raw(f);
        f.ready(() -> {
            Arrow first = f.arrow(2, 0), second = f.arrow(3, 0); h.assertTrue(f.tap(), "real two-arrow snapshot admitted");
            hit(first); hit(second);
            eq(h, hp(f.target), before.subtract(amount), "native cooldown rejects equal logical raw amount without pooling it");
            h.assertValueEqual(watch.incoming, 2, "each native arrow attempts its one own body hurt");
            h.assertValueEqual(watch.posts, 1, "equal second iframe never creates an exact HP transaction");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void native_armor_resistance_and_real_absorption_apply_once(GameTestHelper h) {
        Fixture f = new Fixture(h); HighPowerCombat.enroll(f.target, 5);
        f.target.getAttribute(Attributes.ARMOR).setBaseValue(12); f.target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(4);
        f.target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        f.target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1)); f.target.setAbsorptionAmount(6);
        BigDecimal before = hp(f.target), loss = DamageMitigation.mitigate(raw(f), new DefenseProfile(12, 4, 1, 0)).subtract(BigDecimal.valueOf(6));
        Watch watch = new Watch(f.target); f.hook(watch);
        f.ready(() -> {
            var control = f.mob(f.target.position().add(4, 0, 0));
            control.getAttribute(Attributes.ARMOR).setBaseValue(12); control.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(4);
            control.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
            control.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1)); control.setAbsorptionAmount(6);
            Watch ordinary = new Watch(control); f.hook(ordinary);
            h.assertTrue(f.target.getAbsorptionAmount() == 6 && control.getAbsorptionAmount() == 6,
                    "both declared six-point pools fit native effect-provided maximum absorption");
            Arrow arrow = f.arrow(3, 0); h.assertTrue(f.tap(), "real native arrow admitted"); hit(arrow);
            Arrow nativeControl = new Arrow(h.getLevel(), f.shooter, new ItemStack(Items.ARROW), null);
            nativeControl.setPos(control.getBoundingBox().getCenter().add(0, 0, -.9)); nativeControl.setDeltaMovement(0, 0, 1);
            nativeControl.setBaseDamage(HighPowerCombat.MAX_NATIVE_WITNESS); h.getLevel().addFreshEntity(nativeControl); f.entities.add(nativeControl); nativeControl.tick();
            eq(h, hp(f.target), before.subtract(loss), "reviewed native modifiers debit exact raw residual once");
            h.assertTrue(f.target.getAbsorptionAmount() == 0 && watch.posts == 1 && ordinary.posts == 1
                    && watch.absorption == 6 && ordinary.absorption == 6 && control.getAbsorptionAmount() == 0,
                    "paired real native arrow confirms that each actual absorption pool is consumed exactly once");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void ordinary_unenrolled_hostile_death_keeps_native_arrow_credit(GameTestHelper h) {
        Fixture f = new Fixture(h); Watch watch = new Watch(f.target); f.hook(watch);
        f.ready(() -> {
            Arrow arrow = f.arrow(3, 0); h.assertTrue(f.tap(), "ordinary hostile receives native arrow admission"); hit(arrow);
            h.assertTrue(!f.target.isAlive() && watch.deaths == 1 && watch.incoming == 1 && watch.posts == 1,
                    "ordinary native hostile dies through one normal death decision");
            h.assertTrue(watch.deathSource.is(DamageTypes.ARROW) && watch.deathSource.getDirectEntity() == arrow
                    && watch.deathSource.getEntity() == f.shooter && arrow.isRemoved(), "native original shooter retains death credit and native success continuation");
            h.assertFalse(HighPowerCombat.enabled(f.target), "ordinary target was never enrolled as a side effect");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void incoming_cancel_and_pre_zero_preserve_exact_hp(GameTestHelper h) { veto(h, 0); }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void pre_zero_preserves_exact_hp_without_old_arrow_replay(GameTestHelper h) { veto(h, 1); }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void late_absorption_amplification_is_vetoed_before_native_hp_write(GameTestHelper h) { veto(h, 2); }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void late_owner_write_retires_permission_before_native_hp_write(GameTestHelper h) { veto(h, 3); }
    private static void veto(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h); HighPowerCombat.enroll(f.target, 5); BigDecimal before = hp(f.target);
        Watch watch = new Watch(f.target); f.hook(watch);
        f.ready(() -> {
            Arrow arrow = f.arrow(3, 0); h.assertTrue(f.tap(), "real arrow admission before native stage veto");
            f.hook(new StageVeto(f.target, arrow, mode)); hit(arrow);
            eq(h, hp(f.target), before, "native cancellation/zero/late mutation cannot debit an exact amount");
            h.assertValueEqual(watch.incoming, 1, "native veto never replays the old hurt operand");
            h.assertFalse(ShortInterceptionArrowBridge.leased(arrow), "veto permanently retires this one native permission");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void projectile_impact_cancel_consumes_permission_before_callbacks(GameTestHelper h) {
        Fixture f = new Fixture(h); HighPowerCombat.enroll(f.target, 5); BigDecimal before = hp(f.target); Watch watch = new Watch(f.target); f.hook(watch);
        f.ready(() -> {
            Arrow arrow = f.arrow(3, 0); h.assertTrue(f.tap(), "real arrow admitted");
            Impact hook = new Impact(arrow, event -> event.setCanceled(true)); f.hook(hook); hit(arrow);
            eq(h, hp(f.target), before, "canceled native impact never invokes body hurt");
            h.assertTrue(hook.calls == 1 && watch.incoming == 0 && !ShortInterceptionArrowBridge.leased(arrow), "claim is spent before the only mutable impact callback");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void same_owner_write_retires_boost_and_preserves_ordinary_body_hurt(GameTestHelper h) {
        Fixture f = new Fixture(h); HighPowerCombat.enroll(f.target, 5); BigDecimal before = hp(f.target); Watch watch = new Watch(f.target); f.hook(watch);
        f.ready(() -> {
            Arrow arrow = f.arrow(3, 0); h.assertTrue(f.tap(), "native arrow admitted"); arrow.setOwner(f.shooter); hit(arrow);
            h.assertTrue(watch.incoming == 1 && watch.source.getClass() == DamageSource.class && watch.source.getEntity() == f.shooter,
                    "even same-owner writes retire the permit and resume the exact ordinary source");
            BigDecimal loss = before.subtract(hp(f.target)); h.assertTrue(loss.signum() > 0 && loss.compareTo(BigDecimal.TEN) <= 0,
                    "ordinary damage remains its literal unscaled native amount");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void unknown_unleased_native_arrow_is_exact_passthrough(GameTestHelper h) {
        Fixture f = new Fixture(h); HighPowerCombat.enroll(f.target, 5); BigDecimal before = hp(f.target); Watch watch = new Watch(f.target); f.hook(watch);
        Arrow arrow = f.arrow(3, 0); Vec3 center = f.target.getBoundingBox().getCenter(); arrow.setPos(center.add(0, 0, -1.8)); arrow.setDeltaMovement(0, 0, 1.5); hit(arrow);
        h.assertTrue(watch.incoming == 1 && watch.posts == 1 && watch.source.getClass() == DamageSource.class && arrow.isRemoved(),
                "unleased native arrow retains source identity, one body call and its native true continuation");
        BigDecimal loss = before.subtract(hp(f.target)); h.assertTrue(loss.signum() > 0 && loss.compareTo(BigDecimal.TEN) <= 0,
                "unknown native damage is never multiplied into the exact raw coordinate"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void impact_callback_reentry_cannot_reclaim_native_boost(GameTestHelper h) {
        Fixture f = new Fixture(h); HighPowerCombat.enroll(f.target, 5); BigDecimal before = hp(f.target);
        Watch watch = new Watch(f.target); f.hook(watch);
        f.ready(() -> {
            Arrow arrow = f.arrow(3, 0); h.assertTrue(f.tap(), "native arrow admitted before reentry");
            boolean[] entered = {false};
            f.hook(new Impact(arrow, event -> { if (!entered[0]) { entered[0] = true; arrow.tick(); } }));
            hit(arrow);
            BigDecimal loss = before.subtract(hp(f.target));
            h.assertTrue(entered[0] && loss.signum() >= 0 && loss.compareTo(BigDecimal.TEN) <= 0,
                    "reentrant native tick permanently consumes the boost before its callback and leaves only native raw units");
            h.assertTrue(watch.source == null || watch.source.getClass() == DamageSource.class,
                    "reentrant impact cannot construct a second exact controller source");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void native_hostile_shield_uses_original_arrow_position_and_blocks_boost(GameTestHelper h) {
        Fixture f = new Fixture(h); HighPowerCombat.enroll(f.target, 5); BigDecimal before = hp(f.target);
        f.target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        f.target.setYRot(180); f.target.startUsingItem(InteractionHand.OFF_HAND);
        for (int i = 0; i < 6; i++) f.target.tick();
        h.assertTrue(f.target.isBlocking(), "declared hostile has a genuine native active shield");
        f.ready(() -> {
            Arrow arrow = f.arrow(3, 0); h.assertTrue(f.tap(), "plain arrow admitted toward native shield"); hit(arrow);
            eq(h, hp(f.target), before, "native shield uses preserved arrow origin and prevents exact HP debit");
            h.assertFalse(ShortInterceptionArrowBridge.leased(arrow), "shield rejection spends the native one-use permission");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void actual_scatter_crossing_refuses_interception_admission(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, -13);
        h.runAfterDelay(61, () -> {
            h.assertTrue(ScatterPlates.toggle(f.p, f.wire(1, 1)), "real native scatter controller activated");
            Arrow arrow = f.arrow(f.p.position().add(0, 1, 1.2), new Vec3(0, 0, -.3), null); arrow.tick();
            h.assertTrue(ScatterPlateArrowReceipts.hasEvidence(arrow) && ScatterPlateArrowReceipts.receiptCount(f.p) == 1,
                    "genuine native crossing creates historical scatter evidence before body contact");
            h.assertFalse(ShortInterceptionArrowBridge.eligibleArrow(f.p, arrow), "NEW331 refuses existing scatter evidence");
            h.assertTrue(ShortInterceptionArrowBridge.snapshot(f.p, arrow) == null, "no relocation snapshot is minted for the historical receipt");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void actual_curtain_crossing_refuses_interception_admission(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, -13);
        h.runAfterDelay(61, () -> {
            h.assertTrue(DissipationCurtain.start(f.p, f.wire(2, 1)), "real held curtain starts");
            h.runAfterDelay(6, () -> {
                Arrow arrow = f.arrow(f.p.position().add(0, 1.6, 6.2), new Vec3(0, 0, -1), null); arrow.tick();
                h.assertTrue(DissipationCurtainArrowReceipts.hasEvidence(arrow) && DissipationCurtainArrowReceipts.receiptCount(f.p) == 1,
                        "genuine native crossing creates historical curtain evidence");
                h.assertFalse(ShortInterceptionArrowBridge.eligibleArrow(f.p, arrow), "NEW331 refuses existing curtain evidence"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void actual_zero_gap_crossing_refuses_interception_admission(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, -13);
        ZeroGapArmorNativeFixture.ready(h, () -> {
            Arrow arrow = f.frontArrow(1.6, .5); arrow.tick();
            h.assertTrue(ZeroGapArmorArrowReceipts.hasEvidence(arrow) && ZeroGapArmorArrowReceipts.receiptCount(f.p) == 1,
                    "genuine native crossing creates historical zero-gap evidence");
            h.assertFalse(ShortInterceptionArrowBridge.eligibleArrow(f.p, arrow), "NEW331 refuses existing zero-gap evidence"); h.succeed();
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void exact_native_component_whitelist_preserves_durability_and_refuses_custom_payloads(GameTestHelper h) {
        Fixture f = new Fixture(h); Arrow arrow = f.arrow(3, 0);
        h.assertTrue(ShortInterceptionArrowBridge.eligibleArrow(f.p, arrow), "ordinary default arrow and bow are eligible");
        arrow.getWeaponItem().setDamageValue(12);
        var snapshot = ShortInterceptionArrowBridge.snapshot(f.p, arrow);
        h.assertTrue(snapshot != null && ShortInterceptionArrowBridge.matches(snapshot) && arrow.getWeaponItem().getDamageValue() == 12,
                "ordinary bow durability is observed exactly without normalizing the real item");
        CompoundTag tag = new CompoundTag(); tag.putString("unknown_native_damage", "refuse");
        arrow.getWeaponItem().set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        h.assertFalse(ShortInterceptionArrowBridge.eligibleArrow(f.p, arrow), "unknown weapon component refuses before reservation");
        arrow.getWeaponItem().remove(DataComponents.CUSTOM_DATA);
        arrow.getPickupItemStackOrigin().set(DataComponents.CUSTOM_NAME, Component.literal("Unreviewed arrow component"));
        h.assertFalse(ShortInterceptionArrowBridge.eligibleArrow(f.p, arrow), "nondefault pickup component refuses before reservation");
        arrow.getPickupItemStackOrigin().remove(DataComponents.CUSTOM_NAME);
        arrow.setDeltaMovement(0, 0, 8.01);
        h.assertFalse(ShortInterceptionArrowBridge.eligibleArrow(f.p, arrow), "selection rejects speed outside the reviewed eight-metre native segment");
        arrow.setDeltaMovement(0, 0, 1.5);
        var scoreboard = h.getLevel().getScoreboard();
        var team = scoreboard.addPlayerTeam("sit" + java.util.UUID.randomUUID().toString().substring(0, 9));
        try {
            scoreboard.addPlayerToTeam(f.p.getScoreboardName(), team); scoreboard.addPlayerToTeam(f.shooter.getScoreboardName(), team);
            h.assertFalse(ShortInterceptionArrowBridge.eligibleArrow(f.p, arrow), "allied native monster shooter is not an enemy admission");
        } finally { scoreboard.removePlayerTeam(team); }
        h.assertTrue(ShortInterceptionArrowBridge.eligibleArrow(f.p, arrow), "restored native defaults remain eligible without mutating original shooter or payload");
        Arrow crossbow = new Arrow(h.getLevel(), f.shooter, new ItemStack(Items.ARROW), new ItemStack(Items.CROSSBOW));
        crossbow.setPos(f.source.add(0, 2, 4)); crossbow.setDeltaMovement(0, 0, .2); h.getLevel().addFreshEntity(crossbow); f.entities.add(crossbow); crossbow.tick();
        crossbow.getWeaponItem().setDamageValue(7);
        h.assertTrue(ShortInterceptionArrowBridge.eligibleArrow(f.p, crossbow), "native default crossbow components and ordinary durability are reviewed");
        h.succeed();
    }

    public static final class Watch {
        final LivingEntity target; int incoming, posts, deaths; float absorption; DamageSource source, deathSource; Vec3 origin;
        Watch(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
            if (event.getEntity() == target) { incoming++; source = event.getSource(); origin = source.getSourcePosition(); }
        }
        @SubscribeEvent public void post(LivingDamageEvent.Post event) { if (event.getEntity() == target) { posts++; absorption = event.getReduction(DamageContainer.Reduction.ABSORPTION); } }
        @SubscribeEvent public void death(LivingDeathEvent event) { if (event.getEntity() == target) { deaths++; deathSource = event.getSource(); } }
    }
    public static final class StageVeto {
        final LivingEntity target; final Arrow arrow; final int mode;
        StageVeto(LivingEntity target, Arrow arrow, int mode) { this.target = target; this.arrow = arrow; this.mode = mode; }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void incoming(LivingIncomingDamageEvent event) {
            if (event.getEntity() != target) return;
            if (mode == 0) event.setCanceled(true);
            if (mode >= 2) event.addReductionModifier(DamageContainer.Reduction.ABSORPTION, (container, reduction) -> {
                if (mode == 2) container.setNewDamage(container.getNewDamage() + 1);
                else arrow.setOwner(arrow.getOwner());
                return reduction;
            });
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void pre(LivingDamageEvent.Pre event) {
            if (event.getEntity() == target && mode == 1) event.setNewDamage(0);
        }
    }
    public static final class Impact {
        final Arrow arrow; final Consumer<ProjectileImpactEvent> action; int calls;
        Impact(Arrow arrow, Consumer<ProjectileImpactEvent> action) { this.arrow = arrow; this.action = action; }
        @SubscribeEvent public void impact(ProjectileImpactEvent event) {
            if (event.getProjectile() == arrow && event.getRayTraceResult() instanceof EntityHitResult) { calls++; action.accept(event); }
        }
    }
}

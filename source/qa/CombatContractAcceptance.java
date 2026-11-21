package cn.academy.port.gametest;

import cn.academy.port.AcademyConfig;
import cn.academy.port.api.*;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.gametest.*;

import java.util.UUID;

/**
 * Independent cooperating phase, equipment, and refused-removal fixtures; loaded only by the QA
 * launcher.
 */
@GameTestHolder("academy_combat_contracts")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class CombatContractAcceptance {
    @SubscribeEvent
    public static void room(ServerStartedEvent event) {
        var level = event.getServer().overworld();
        level.getStructureManager()
                .getOrCreate(
                        ResourceLocation.fromNamespaceAndPath("academy_combat_contracts", "room"))
                .fillFromWorld(level, new BlockPos(0, 290, 0), new Vec3i(24, 10, 24), false, null);
    }

    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Providers {
        @SubscribeEvent
        public static void register(RegisterCapabilitiesEvent event) {
            event.registerEntity(
                    CombatDefense.CAPABILITY,
                    EntityType.ZOMBIE,
                    (entity, context) ->
                            entity instanceof PrivatePools pools
                                    ? pools
                                    : entity instanceof Phased
                                            ? (target, source, raw, limited) -> raw * .04f
                                            : null);
            event.registerItem(
                    EquipmentSeal.CAPABILITY,
                    (stack, context) -> (item, wearer, caster) -> true,
                    Items.WOODEN_SWORD);
        }
    }

    public static final class Phased extends Zombie {
        boolean rage;

        public Phased(ServerLevel level) {
            super(EntityType.ZOMBIE, level);
        }

        @Override
        public boolean hurt(DamageSource source, float damage) {
            if (rage || source.getDirectEntity() != source.getEntity()) return false;
            return super.hurt(source, Math.min(200000F, damage * .04F));
        }
    }

    public static final class PrivatePools extends Zombie implements CombatDefense {
        float hp = 500000F, shield = 10000F;

        PrivatePools(ServerLevel level) {
            super(EntityType.ZOMBIE, level);
        }

        public float withoutDamageCap(
                LivingEntity target, DamageSource source, float requested, float nativeAmount) {
            return requested;
        }

        public Pools observe(LivingEntity target) {
            return new Pools(
                    hp, java.math.BigDecimal.valueOf(shield), shield > 0 ? "barrier" : "exposed");
        }

        public boolean hurt(DamageSource source, float amount) {
            float capped = Math.min(1000F, amount);
            boolean admitted = super.hurt(source, capped);
            float damage = CombatDefense.settledDamage(this, source, capped);
            float paid = Math.min(shield, damage);
            shield -= paid;
            hp -= damage - paid;
            if (CombatDefense.settledDamage(this, source, capped) != 0)
                throw new AssertionError("duplicate admitted debit");
            return admitted;
        }
    }

    @GameTest(template = "room")
    public static void ownerCommitsItsPrivatePoolOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        var caster = caster(helper);
        var mob = new PrivatePools(level);
        reset(mob);
        mob.moveTo(caster.position());
        level.addFreshEntity(mob);
        var source = caster.damageSources().playerAttack(caster);
        var one = NativeCombatDamage.hurtDetailed(mob, source, 75000);
        helper.assertTrue(
                one.healthSpent() == 65000
                        && one.targetShieldSpent().floatValue() == 10000
                        && one.phaseBefore().equals("barrier")
                        && one.phaseAfter().equals("exposed")
                        && mob.hp == 435000,
                "read-only private pools and one authorized debit");
        var listener =
                new Object() {
                    @SubscribeEvent
                    public void damage(
                            net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
                                    event) {
                        if (event.getEntity() == mob) event.setCanceled(true);
                    }
                };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(listener);
        try {
            mob.invulnerableTime = 0;
            var blocked = NativeCombatDamage.hurtDetailed(mob, source, 75000);
            helper.assertTrue(
                    blocked.canceled() && !blocked.affected() && mob.hp == 435000,
                    "cooperating canceled debit is zero");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
            mob.discard();
        }
        helper.succeed();
    }

    public static final class RefusesDiscard extends Zombie {
        public RefusesDiscard(ServerLevel level) {
            super(EntityType.ZOMBIE, level);
        }

        @Override
        public void remove(Entity.RemovalReason reason) {
            if (reason != Entity.RemovalReason.DISCARDED) super.remove(reason);
        }
    }

    private static FakePlayer caster(GameTestHelper helper) {
        var p = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "Fixture"));
        p.moveTo(helper.absoluteVec(new Vec3(6, 3, 6)));
        return p;
    }

    private static void reset(LivingEntity target) {
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2_000_000);
        target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        target.setHealth(target.getMaxHealth());
        target.invulnerableTime = 0;
    }

    @GameTest(template = "room")
    public static void permittedCeilingPreservesPhase(GameTestHelper helper) {
        var level = helper.getLevel();
        var p = caster(helper);
        var mob = new Phased(level);
        mob.moveTo(p.position().add(0, 0, 3));
        level.addFreshEntity(mob);
        var source = p.damageSources().playerAttack(p);
        boolean enabled = AcademyConfig.BREAK_DAMAGE_CAPS.get();
        try {
            for (boolean open : new boolean[] {false, true}) {
                AcademyConfig.BREAK_DAMAGE_CAPS.set(open);
                reset(mob);
                var hit = NativeCombatDamage.hurtDetailed(mob, source, 10_000_000);
                helper.assertTrue(
                        hit.before() - hit.after() == (open ? 400_000 : 200_000),
                        "fixed ceiling after four-percent scale " + open);
            }
            reset(mob);
            mob.rage = true;
            helper.assertTrue(
                    !NativeCombatDamage.hurtDetailed(mob, source, 10_000_000).affected(),
                    "rage gate");
            mob.rage = false;
            var arrow = EntityType.ARROW.create(level);
            arrow.setOwner(p);
            helper.assertTrue(
                    !NativeCombatDamage.hurtDetailed(
                                    mob, level.damageSources().arrow(arrow, p), 10_000_000)
                            .affected(),
                    "indirect source gate");
        } finally {
            AcademyConfig.BREAK_DAMAGE_CAPS.set(enabled);
            mob.discard();
        }
        helper.succeed();
    }

    private static int count(Object tally, String name) throws ReflectiveOperationException {
        var field = tally.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(tally);
    }

    @GameTest(template = "room")
    public static void snapChecksActualRemoval(GameTestHelper helper)
            throws ReflectiveOperationException {
        var level = helper.getLevel();
        var caster = caster(helper);
        var type = Class.forName("cn.aca.infinity.SnapResults");
        var constructor = type.getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        var tally = constructor.newInstance(0);
        var discard = type.getDeclaredMethod("banish", LivingEntity.class);
        discard.setAccessible(true);
        var strike = type.getDeclaredMethod("strike", ServerPlayer.class, LivingEntity.class);
        strike.setAccessible(true);
        var immune = new RefusesDiscard(level);
        immune.moveTo(caster.position());
        level.addFreshEntity(immune);
        discard.invoke(tally, immune);
        helper.assertTrue(
                !immune.isRemoved()
                        && count(tally, "resisted") == 1
                        && count(tally, "banished") == 0,
                "refused discard counts only as resisted");
        var mob = EntityType.HUSK.create(level);
        reset(mob);
        mob.moveTo(caster.position());
        level.addFreshEntity(mob);
        strike.invoke(tally, caster, mob);
        helper.assertTrue(
                mob.getHealth() == 900000F
                        && count(tally, "damaged") == 1
                        && count(tally, "killed") == 0
                        && count(tally, "banished") == 0,
                "55 percent boss hit uses actual state");
        discard.invoke(tally, mob);
        helper.assertTrue(
                count(tally, "banished") == 1
                        && mob.getRemovalReason() == Entity.RemovalReason.DISCARDED,
                "confirmed discard");
        immune.remove(Entity.RemovalReason.KILLED);
        helper.succeed();
    }

    public static class ScriptedAttacker extends Zombie {
        ScriptedAttacker(ServerLevel level) {
            super(EntityType.ZOMBIE, level);
        }

        void strikeAndPull(ServerPlayer victim, float amount) {
            victim.hurt(damageSources().mobAttack(this), amount);
            victim.push(1.5, 0, 0);
            victim.addEffect(
                    new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40));
        }
    }

    @GameTest(template = "room")
    public static void breakingBlowKeepsOnlyItsOwnSuperArmor(GameTestHelper helper)
            throws ReflectiveOperationException {
        var player =
                new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "Armor")) {
                    @Override
                    public boolean isInvulnerableTo(DamageSource source) {
                        return false;
                    }
                };
        var spawn = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
        spawn.setAccessible(true);
        spawn.setInt(player, 0);
        var ability = cn.academy.port.AbilityStorage.get(player);
        ability.changeCategoryClassic("electromaster");
        ability.setLevel(5);
        ability.setActivateState(true);
        helper.assertTrue(cn.academy.port.highpower.HighPowerCombat.enroll(player, 5), "enroll");
        var source = new ScriptedAttacker(helper.getLevel());
        var velocity = player.getDeltaMovement();
        source.strikeAndPull(player, 18_000_010F);
        helper.assertTrue(
                player.getHealth() > 0
                        && player.getHealth() < 20_000_000
                        && !cn.academy.port.highpower.PlayerShieldArmor.holding(player),
                "shield exhausted with HP overflow");
        helper.assertTrue(
                player.getDeltaMovement().equals(velocity)
                        && !player.hasEffect(
                                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
                "same attack stopped");
        player.invulnerableTime = 0;
        source.strikeAndPull(player, 4F);
        helper.assertTrue(
                !player.getDeltaMovement().equals(velocity)
                        && player.hasEffect(
                                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
                "next independent attack can move and slow");
        helper.succeed();
    }

    public static final class Wide extends Zombie {
        Wide(ServerLevel level) {
            super(EntityType.ZOMBIE, level);
        }

        @Override
        public EntityDimensions getDefaultDimensions(Pose pose) {
            return EntityDimensions.scalable(36F, 18F);
        }
    }

    @GameTest(template = "room")
    public static void giantSurfaceInsideAndWall(GameTestHelper helper) {
        var level = helper.getLevel();
        var observer = caster(helper);
        var eye = observer.getEyePosition();
        var giant = new Wide(level);
        giant.setNoAi(true);
        giant.moveTo(eye.x + 16, eye.y - 1, eye.z + 26);
        giant.refreshDimensions();
        level.addFreshEntity(giant);
        var to = eye.add(0, 0, 12);
        helper.assertTrue(
                CombatTargeting.ray(level, observer, eye, to, 0, e -> e == giant).contains(giant),
                "near surface beyond normal center search");
        helper.assertTrue(CombatTargeting.visibleWithin(observer, giant, 12),
                "committing a cast accepts the same in-range body surface");
        var stone = BlockPos.containing(eye.add(0, 0, 4));
        var saved = level.getBlockState(stone);
        level.setBlockAndUpdate(
                stone, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        try {
            helper.assertTrue(
                    CombatTargeting.ray(level, observer, eye, to, 1, e -> e == giant).isEmpty(),
                    "wall blocks padded ray");
        } finally {
            level.setBlockAndUpdate(stone, saved);
        }
        giant.moveTo(eye.x, eye.y - 1, eye.z);
        helper.assertTrue(
                CombatTargeting.ray(level, observer, eye, to, 0, e -> e == giant).contains(giant),
                "inside large body");
        giant.discard();
        helper.succeed();
    }

    public static final class Nested {
        LivingEntity target;
        DamageSource outer;

        @SubscribeEvent
        public void damage(
                net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
            if (event.getEntity() != target || event.getSource() != outer) return;
            target.hurt(target.damageSources().magic(), 600);
            event.setCanceled(true);
        }
    }

    @GameTest(template = "room")
    public static void cancellationsAndUnloadsHaveSeparateAttribution(GameTestHelper helper) {
        var level = helper.getLevel();
        var caster = caster(helper);
        var target = EntityType.HUSK.create(level);
        reset(target);
        target.moveTo(caster.position());
        level.addFreshEntity(target);
        var hook = new Nested();
        hook.target = target;
        hook.outer = caster.damageSources().playerAttack(caster);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(hook);
        try {
            var hit = NativeCombatDamage.hurtDetailed(target, hook.outer, 1000);
            helper.assertTrue(
                    hit.canceled()
                            && !hit.admitted()
                            && hit.before() - hit.after() == 600
                            && hit.healthSpent() == 0
                            && !hit.affected(),
                    "nested damage excluded from canceled outer strike");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(hook);
        }
        reset(target);
        var lethal = NativeCombatDamage.hurtDetailed(target, hook.outer, 4_000_000);
        int[] confirmed = {0};
        lethal.whenKilled(target, ignored -> confirmed[0]++);
        target.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.runAfterDelay(
                5,
                () -> {
                    helper.assertTrue(confirmed[0] == 0, "unload does not confirm kill");
                    helper.succeed();
                });
    }

    @GameTest(template = "room")
    public static void firedArrowRetainsAllegiance(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = caster(helper);
        var mob = EntityType.HUSK.create(level);
        mob.moveTo(owner.position());
        level.addFreshEntity(mob);
        helper.assertTrue(
                ControlRelations.bind(owner, mob, level.getGameTime() + 40, "test"),
                "owner attached");
        var arrow = EntityType.ARROW.create(level);
        arrow.setOwner(mob);
        arrow.setNoGravity(true);
        arrow.moveTo(mob.position());
        level.addFreshEntity(arrow);
        ControlRelations.release(mob, "test");
        var source = level.damageSources().arrow(arrow, mob);
        helper.assertTrue(
                ControlRelations.refuses(owner, source),
                "fired projectile keeps original friendly-fire rule");
        var enemy = EntityType.HUSK.create(level);
        helper.assertTrue(
                !ControlRelations.refuses(enemy, source), "unrelated enemy still eligible");
        arrow.discard();
        mob.discard();
        helper.succeed();
    }

    @GameTest(template = "room")
    public static void cooperativeEquipmentKeepsItsStackAndDeadline(GameTestHelper helper) {
        var wearer = EntityType.HUSK.create(helper.getLevel());
        var caster = caster(helper);
        var stack = new ItemStack(Items.WOODEN_SWORD);
        wearer.setItemSlot(EquipmentSlot.MAINHAND, stack);
        wearer.moveTo(caster.position());
        helper.getLevel().addFreshEntity(wearer);
        helper.assertTrue(
                EquipmentSeal.apply(caster, wearer, 12) && EquipmentSeal.active(wearer),
                "seal is active");
        var saved = new net.minecraft.nbt.CompoundTag();
        wearer.saveWithoutId(saved);
        wearer.discard();
        var reloaded = EntityType.HUSK.create(helper.getLevel());
        reloaded.load(saved);
        helper.assertTrue(
                EquipmentSeal.active(reloaded) && reloaded.getMainHandItem().is(Items.WOODEN_SWORD),
                "reload preserves seal and item");
        helper.runAfterDelay(
                14,
                () -> {
                    helper.assertTrue(
                            !EquipmentSeal.active(reloaded)
                                    && reloaded.getMainHandItem().getCount() == 1,
                            "deadline and inventory survive reload");
                    helper.succeed();
                });
    }
}

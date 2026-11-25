package cn.academy.port.gametest;

import cn.academy.port.highpower.CombatAttributes;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.titan.TitanBlocks;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("academy_real_attributes")
@PrefixGameTestTemplate(false)
public final class NativeAttributesRuntimeTests {
    private NativeAttributesRuntimeTests() {}

    @GameTest(template = "highpower_empty", batch = "native_attributes", timeoutTicks = 20)
    public static void realValuesAndNativeDamage(GameTestHelper h) {
        var villager = h.spawn(EntityType.VILLAGER, 6, 2, 5);
        h.assertTrue(HighPowerCombat.enroll(villager, 5), "enroll as LV5");
        h.assertTrue(villager.getMaxHealth() == 2000 && villager.getHealth() == 2000,
                "LV5 health is native, not a 20 HP proxy");
        h.assertTrue(!villager.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).contains("health"),
                "no second saved health");
        villager.hurt(villager.damageSources().generic(), 10);
        h.assertTrue(villager.getHealth() == 1990, "one real hit");
        villager.heal(2);
        h.assertTrue(villager.getHealth() == 1992, "native heal is applied once");
        h.assertTrue(Attributes.MAX_HEALTH.value().sanitizeValue(1e20) == CombatAttributes.LIMIT, "upper health bound");
        h.assertTrue(Attributes.ARMOR.value().sanitizeValue(100) == 100, "Ascalon armor ceiling");
        h.assertTrue(Attributes.ARMOR_TOUGHNESS.value().sanitizeValue(100) == 100, "Ascalon toughness ceiling");
        h.succeed();
    }

    @GameTest(template = "highpower_empty", batch = "native_attributes", timeoutTicks = 20)
    public static void migrateHalfHealthOnce(GameTestHelper h) {
        var target = h.spawn(EntityType.VILLAGER, 6, 2, 5);
        var legacy = new CompoundTag();
        legacy.putInt("level", 5);
        legacy.putString("version", "1");
        legacy.putString("health", "10000000");
        legacy.putString("maxHealth", "20000000");
        legacy.putString("shield", "9000000");
        legacy.putString("maxShield", "10000000");
        legacy.putString("shieldEnabled", "false");
        target.getPersistentData().put(HighPowerCombat.DATA_KEY, legacy);
        h.assertTrue(HighPowerCombat.enabled(target), "legacy accepted");
        h.assertTrue(target.getMaxHealth() == 2000 && target.getHealth() == 1000, "half-health restored");
        h.assertTrue(HighPowerCombat.shieldText(target).equals("540"), "partial shield preserved");
        h.assertTrue(!legacy.contains("health") && legacy.getBoolean("nativeVitals"), "old health removed and marked");
        target.heal(2);
        h.assertTrue(HighPowerCombat.enabled(target) && target.getHealth() == 1002, "repeated read never replays migration");
        var checkpoint = target.saveWithoutId(new CompoundTag());
        var reloaded = EntityType.VILLAGER.create(h.getLevel());
        reloaded.load(checkpoint);
        h.assertTrue(HighPowerCombat.enabled(reloaded) && reloaded.getMaxHealth() == 2000
                && reloaded.getHealth() == 1002 && HighPowerCombat.shieldText(reloaded).equals("540"),
                "native saved health and shield survive entity reload unchanged");
        h.succeed();
    }

    @GameTest(template = "highpower_empty", batch = "native_attributes", timeoutTicks = 20)
    public static void nativeCapacityMigration(GameTestHelper h) {
        var target = h.spawn(EntityType.VILLAGER, 6, 2, 5);
        h.assertTrue(HighPowerCombat.enroll(target, 5), "enroll native LV5");
        var data = target.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
        data.putInt("capacityProfileVersion", 1);
        data.putInt("growthCurve", 3);
        data.putString("maxShield", "50");
        data.putString("shield", "20");
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        target.setHealth(40);
        var reloaded = EntityType.VILLAGER.create(h.getLevel());
        reloaded.load(target.saveWithoutId(new CompoundTag()));
        h.assertTrue(HighPowerCombat.enabled(reloaded) && reloaded.getMaxHealth() == 2000 && reloaded.getHealth() == 800
                && HighPowerCombat.shieldText(reloaded).equals("240"), "both native a3.84 balances retain 40 percent");
        var migrated = reloaded.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
        h.assertTrue(migrated.getInt("capacityProfileVersion") == 2 && migrated.getInt("growthCurve") == 4,
                "both migrations are recorded");
        reloaded.setHealth(600);
        h.assertTrue(HighPowerCombat.enabled(reloaded) && reloaded.getHealth() == 600, "subsequent reads leave balances alone");
        h.succeed();
    }

    @GameTest(template = "highpower_empty", batch = "native_attributes", timeoutTicks = 20)
    public static void titanAndForeignHooks(GameTestHelper h) {
        var titan = h.spawn(TitanBlocks.TYPES.get("myco_frog").get(), 6, 2, 5);
        var profile = titan.profile();
        h.assertTrue(titan.getMaxHealth() == profile.encounterHealth().floatValue(), "titan's health is the real encounter amount");
        h.assertTrue(titan.getAttributeValue(Attributes.ATTACK_DAMAGE) == profile.meleeAttack(), "native titan attack untruncated");
        h.assertTrue(titan.getAttributeValue(CombatAttributes.ABILITY_POWER) == profile.damageUnit().doubleValue(), "titan spell power");
        float initial = titan.getHealth();
        var hook = new Rewrite(titan.getUUID());
        NeoForge.EVENT_BUS.register(hook);
        try {
            titan.hurt(titan.damageSources().magic(), 10_000);
            h.assertTrue(titan.getHealth() == initial, "incoming cancellation is final");
            hook.cancel = false;
            titan.invulnerableTime = 0;
            titan.hurt(titan.damageSources().magic(), 10_000);
            h.assertTrue(titan.getHealth() == initial - 3456, "modified post-mitigation amount committed once");
        } finally {
            NeoForge.EVENT_BUS.unregister(hook);
        }
        h.succeed();
    }

    @GameTest(template = "highpower_empty", batch = "native_attributes", timeoutTicks = 20)
    public static void shieldArmorStopsDisplacementAndOnlyAttacksSpendIt(GameTestHelper h) throws ReflectiveOperationException {
        var player = new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ShieldProbe")) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
                return false;
            }
        };
        var spawnProtection = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
        spawnProtection.setAccessible(true);
        spawnProtection.setInt(player, 0);
        var ability = cn.academy.port.AbilityStorage.get(player);
        ability.changeCategoryClassic("electromaster");
        ability.setLevel(5);
        ability.setActivateState(true);
        h.assertTrue(HighPowerCombat.enroll(player, 5), "enroll shielded player");
        try {
            h.assertTrue(cn.academy.port.highpower.PlayerShieldArmor.holding(player), "live base shield grants armor");
            player.hurt(player.damageSources().fall(), 1_000_000);
            h.assertTrue(player.getHealth() == 2000 && HighPowerCombat.shieldText(player).equals("600"),
                    "environment doesn't damage health or shield");
            var motion = player.getDeltaMovement();
            player.knockback(1, 1, 0);
            h.assertTrue(player.getDeltaMovement().equals(motion), "a held shield prevents native knockback");
            h.assertTrue(!player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 100)), "a held shield prevents slowness");
            var attacker = h.spawn(EntityType.HUSK, 6, 2, 5);
            h.assertTrue(player.hurt(player.damageSources().mobAttack(attacker), 10), "native attack admitted");
            h.assertTrue(player.getHealth() == 2000 && HighPowerCombat.shieldText(player).equals("590"),
                    "real attack spends shield before health: hp=" + player.getHealth() + ", shield=" + HighPowerCombat.shieldText(player));
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().mobAttack(attacker), 595);
            h.assertTrue(player.getHealth() == 1995 && HighPowerCombat.shieldText(player).equals("0"),
                    "only the remainder after shield break hits native health");
            h.assertTrue(!cn.academy.port.highpower.PlayerShieldArmor.holding(player), "depleted shield releases super armor");
        } finally {
            cn.academy.port.AbilityStorage.remove(player);
            player.discard();
        }
        h.succeed();
    }

    @GameTest(template = "highpower_empty", batch = "native_attributes", timeoutTicks = 20)
    public static void nativeTotemAndDeath(GameTestHelper h) {
        var target = h.spawn(EntityType.VILLAGER, 6, 2, 5);
        var attacker = h.spawn(EntityType.HUSK, 7, 2, 5);
        h.assertTrue(HighPowerCombat.enroll(target, 1) && target.getMaxHealth() == 26, "LV1 native cap");
        target.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
        target.hurt(target.damageSources().mobAttack(attacker), 1000);
        h.assertTrue(target.isAlive() && target.getHealth() == 1 && target.getOffhandItem().isEmpty(),
                "native totem rescues at one real HP");
        target.removeAllEffects();
        target.invulnerableTime = 0;
        target.hurt(target.damageSources().mobAttack(attacker), 1000);
        h.assertTrue(!target.isAlive() && target.getHealth() == 0, "next unprotected hit kills");
        h.succeed();
    }

    public static final class Rewrite {
        private final UUID target;
        private boolean cancel = true;
        Rewrite(UUID target) { this.target = target; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
            if (cancel && event.getEntity().getUUID().equals(target)) event.setCanceled(true);
        }
        @SubscribeEvent public void pre(LivingDamageEvent.Pre event) {
            if (event.getEntity().getUUID().equals(target)) event.setNewDamage(3456);
        }
    }
}

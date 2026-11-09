package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.highpower.ElectromasterPrototype;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.VanillaCombatContract;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.HighPowerLevel;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-native opt-in bridge tests, not survival acquisition, client, rendering or boss acceptance. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerCombatRuntimeTests {
    private static final String TEMPLATE = "highpower_empty";
    private static final String SKILL = "electromaster.forked_arc";
    private static final BigDecimal MAX_HP = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth();
    private static final BigDecimal SATURATED_HIT = BigDecimal.valueOf(HighPowerCombat.MAX_NATIVE_WITNESS).multiply(BigDecimal.TEN);
    private static final BigDecimal LOW_HIT = cn.academy.port.highpower.core.HighPowerLevel.LV1.damage();
    private HighPowerCombatRuntimeTests() {}
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "highpower_hud_native_overlay_codec")
    public static void hud_native_overlay_codec(GameTestHelper h) {
        var original=net.minecraft.network.chat.Component.translatableWithFallback("hud.academy.highpower.summary.v1", "%s %s %s %s %s",4,"25.5","100Qi","50Qi","100K").withStyle(net.minecraft.ChatFormatting.AQUA);
        var wire=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC.encode(wire,original);
            var decoded=net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC.decode(wire);
            h.assertTrue(cn.academy.port.highpower.HighPowerStatusSummary.select(decoded,true,300,c->100)!=decoded,"native overlay codec preserves admitted integer/string arguments");
            h.assertTrue(decoded.getStyle().equals(original.getStyle()),"native codec retains root style");
            h.assertTrue(cn.academy.port.highpower.HighPowerStatusSummary.select(decoded,false,300,c->100)==decoded,"missing actual client panel keeps complete native fallback");
        } finally { wire.release(); }
        h.succeed();
    }


    @SubscribeEvent
    public static void install(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(32)); size.add(IntTag.valueOf(8)); size.add(IntTag.valueOf(32)); tag.put("size", size);
        ListTag blocks = new ListTag(); CompoundTag first = new CompoundTag(); ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0));
        first.put("pos", pos); first.putInt("state", 0); blocks.add(first); tag.put("blocks", blocks);
        tag.put("entities", new ListTag()); ListTag palette = new ListTag(); CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air"); palette.add(air); tag.put("palette", palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_highpower", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
        // Optional focused namespace uses the identical empty native fixture, never a shipped world.
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_highpower_far241", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_highpower_late251", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template = TEMPLATE, batch = "highpower_exact", timeoutTicks = 10)
    public static void exact_sub_float_hit_changes_hp_and_native_nbt_round_trips(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        h.assertTrue(HighPowerCombat.enroll(target, 5), "explicit native target enrollment");
        HighPowerCombat.shieldActive(target, true);
        BigDecimal shield = HighPowerCombat.shield(target);
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, LOW_HIT.movePointLeft(9)), "native pipeline accepts exact low hit");
        exact(h, target, MAX_HP.subtract(LOW_HIT.movePointLeft(9)), "sub-float loss remains visible in the exact ledger");
        h.assertTrue(target.getHealth() == target.getMaxHealth(), "float display may round while exact transaction is retained");
        h.assertTrue(HighPowerCombat.shield(target).compareTo(shield) == 0, "bracer pool never becomes a generic forcefield");
        CompoundTag ledger = target.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
        h.assertTrue(ledger.contains("health", Tag.TAG_STRING) && ledger.contains("maxHealth", Tag.TAG_STRING), "durable decimal-string fields");
        CompoundTag saved = target.saveWithoutId(new CompoundTag());
        Villager reloaded = new Villager(EntityType.VILLAGER, h.getLevel()); reloaded.load(saved);
        exact(h, reloaded, MAX_HP.subtract(LOW_HIT.movePointLeft(9)), "actual entity save/load preserves every decimal digit");
        h.assertFalse(HighPowerCombat.enroll(target, 5), "setup cannot be replayed to refill HP or shield");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_gates", timeoutTicks = 10)
    public static void incoming_cancellation_zero_pre_damage_and_hurt_cooldown_preserve_hp(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        HighPowerCombat.enroll(target, 5);
        DamageGate gate = new DamageGate(target); f.hook(gate);
        gate.cancel = true;
        h.assertFalse(HighPowerCombat.attack(player, SKILL, target, LOW_HIT), "native incoming cancellation returns false");
        exact(h, target, MAX_HP, "cancellation cannot debit the exact pool");
        gate.cancel = false; gate.zero = true;
        HighPowerCombat.attack(player, SKILL, target, LOW_HIT);
        exact(h, target, MAX_HP, "final pre-damage protection is respected");
        gate.zero = false; target.invulnerableTime = 0; // Fixture reset only; runtime never resets this.
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, LOW_HIT), "first permitted hit");
        h.assertFalse(HighPowerCombat.attack(player, SKILL, target, LOW_HIT), "equal immediate hit is rejected by native hurt cooldown");
        exact(h, target, MAX_HP.subtract(LOW_HIT), "rejected repeat cannot replay damage");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_mitigation", timeoutTicks = 10)
    public static void armor_toughness_and_resistance_use_exact_raw_vanilla_math(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        HighPowerCombat.enroll(target, 5);
        target.getAttribute(Attributes.ARMOR).setBaseValue(20);
        target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        ObserveDamage observed = new ObserveDamage(target); f.hook(observed);
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, SATURATED_HIT), "armored target accepts native pipeline");
        h.assertTrue(observed.armor > 0 && observed.resistance > 0, "real native armor and resistance still execute once");
        exact(h, target, MAX_HP.subtract(new BigDecimal("6720000")), "exact raw armor floor and Resistance I replace the old float ratio");
        h.assertTrue(target.isAlive(), "witness subtraction cannot falsely kill a high-HP target");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_native_defenses", timeoutTicks = 20)
    public static void vanilla_held_shield_and_absorption_still_process_the_native_witness(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player();
        Villager blocking = f.target(5.5, 2, 8.5); HighPowerCombat.enroll(blocking, 5);
        blocking.setYRot(180); blocking.setYHeadRot(180); blocking.yBodyRot = 180;
        blocking.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        blocking.startUsingItem(InteractionHand.OFF_HAND);
        Villager absorbing = f.target(10.5, 2, 8.5); HighPowerCombat.enroll(absorbing, 5);
        absorbing.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20);
        absorbing.setAbsorptionAmount(10);
        h.assertTrue(HighPowerCombat.attack(player, SKILL, absorbing, SATURATED_HIT), "native absorption target accepts high-power hit");
        h.assertTrue(absorbing.getAbsorptionAmount() == 0, "native absorption is actually consumed once");
        exact(h, absorbing, MAX_HP.subtract(new BigDecimal("9999990")),
                "ten native absorption points grant exactly ten exact HP protection, not the old multiplied hundred");
        h.runAtTickTime(7, () -> {
            blocking.setYRot(180); blocking.setYHeadRot(180);
            h.assertTrue(blocking.isBlocking(), "real held vanilla shield completed its native raising delay");
            h.assertFalse(HighPowerCombat.attack(player, SKILL, blocking, SATURATED_HIT), "native frontal shield block rejects ordinary player-attack source");
            exact(h, blocking, MAX_HP, "fully blocked hit cannot debit exact HP");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "highpower_native_defenses", timeoutTicks = 10)
    public static void fractional_absorption_below_native_ulp_is_subtracted_once(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        HighPowerCombat.enroll(target, 5);
        target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20); target.setAbsorptionAmount(.01F);
        ObserveDamage observed = new ObserveDamage(target); f.hook(observed);
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, SATURATED_HIT), "sub-ULP native absorption still permits the attack");
        h.assertTrue(observed.damage == observed.original && observed.absorption == .01F,
                "real float damage loses a 0.01 subtraction while DamageContainer retains absorption evidence");
        h.assertTrue(target.getAbsorptionAmount() == 0, "fractional absorption is consumed natively once");
        exact(h, target, MAX_HP.subtract(new BigDecimal("9999999.99")),
                "neither final float damage nor Post-plus-absorption reconstruction may erase or multiply absorption");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_native_defenses", timeoutTicks = 10)
    public static void unregistered_absorption_rewrite_is_refused_after_native_resources(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        HighPowerCombat.enroll(target, 5);
        target.getAttribute(Attributes.ARMOR).setBaseValue(20); target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20); target.setAbsorptionAmount(10);
        NativeDamageRewrite rewrite = new NativeDamageRewrite(target); rewrite.absorptionMultiplier = .5F; f.hook(rewrite);
        ObserveDamage observed = new ObserveDamage(target); f.hook(observed);
        h.assertFalse(HighPowerCombat.attack(player, SKILL, target, SATURATED_HIT), "an unregistered absorption rewrite is outside the strict contract");
        h.assertTrue(observed.absorption == 5 && target.getAbsorptionAmount() == 5,
                "already-performed native consumption is neither duplicated nor falsely rolled back");
        exact(h, target, MAX_HP, "unsupported absorption arithmetic cannot commit exact HP damage");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_native_defenses", timeoutTicks = 10)
    public static void raw_cooldown_residual_is_distinct_from_final_pre_veto(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player();
        for (int i = 0; i < 3; i++) {
            Villager target = f.target(5 + i * 4, 5, 5); HighPowerCombat.enroll(target, 5);
            h.assertTrue(target.hurt(target.damageSources().generic(), 999999), "ordinary first hit establishes actual native differential cooldown");
            BigDecimal before = new BigDecimal(HighPowerCombat.healthText(target));
            target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20); target.setAbsorptionAmount(1);
            if (i == 1) { NativeDamageRewrite veto = new NativeDamageRewrite(target); veto.finalPreDamage = 0F; f.hook(veto); }
            if (i == 2) target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4));
            ObserveDamage observed = new ObserveDamage(target); f.hook(observed);
            h.assertTrue(HighPowerCombat.attack(player, SKILL, target, SATURATED_HIT), "stronger witness passes native cooldown without a reset");
            h.assertTrue(i == 0 ? observed.damage > 0 : observed.damage == 0, "only actual zero protection vetoes the exact residual");
            exact(h, target, before.subtract(i == 0 ? BigDecimal.valueOf(9000000) : BigDecimal.ZERO),
                    "raw 10000000 minus native predecessor 999999 minus one absorption is 9000000; no residual ratio");
            h.assertTrue(target.getAbsorptionAmount() == (i == 0 ? 0 : 1), "only real absorption consumes the native pool");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_native_lifecycle", timeoutTicks = 10)
    public static void tiny_native_cooldown_residual_can_still_trigger_exact_lethal_totem(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        HighPowerCombat.enroll(target, 5);
        h.assertTrue(target.hurt(target.damageSources().generic(), 999999), "ordinary predecessor leaves a one-point native cooldown residual");
        target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20); target.setAbsorptionAmount(1);
        target.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        ObserveDamage observed = new ObserveDamage(target); f.hook(observed);
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, CombatAmounts.CAP), "native one-point residual gates a much larger raw residual");
        h.assertTrue(observed.nativeBeforeArmor == HighPowerCombat.MAX_NATIVE_WITNESS && observed.absorption == 1,
                "native armor receives the bounded true residual before one absorption, rather than the old one-point witness difference");
        h.assertTrue(target.isAlive() && target.getOffhandItem().isEmpty(), "exact lethal remainder still reaches enclosing native totem processing");
        exact(h, target, HighPowerLevel.LV5.damage(), "native totem restores its one normalized HP after the exact lethal debit");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_mitigation", timeoutTicks = 10)
    public static void nonlinear_armor_and_native_roundoff_do_not_distort_exact_raw_damage(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        HighPowerCombat.enroll(target, 5);
        target.getAttribute(Attributes.ARMOR).setBaseValue(20.75); target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
        h.assertTrue(target.getArmorValue() == 20, "native armor attribute is floored before raw mitigation");
        ObserveDamage observed = new ObserveDamage(target); f.hook(observed);
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, SATURATED_HIT), "full armored witness accepted");
        h.assertTrue(Float.toString(HighPowerCombat.MAX_NATIVE_WITNESS - observed.armor).equals("840000.06"), "native armor still exposes its actual float roundoff");
        exact(h, target, MAX_HP.subtract(new BigDecimal("8400000")), "exact raw armor avoids the old 0.6 witness-ratio error");
        Villager residual = f.target(9, 5, 5); HighPowerCombat.enroll(residual, 5);
        residual.getAttribute(Attributes.ARMOR).setBaseValue(20); residual.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
        h.assertTrue(residual.hurt(residual.damageSources().generic(), 999990), "prior hit establishes a ten-point differential witness");
        BigDecimal before = new BigDecimal(HighPowerCombat.healthText(residual));
        residual.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20); residual.setAbsorptionAmount(1);
        ObserveDamage residualObserved = new ObserveDamage(residual); f.hook(residualObserved);
        h.assertTrue(HighPowerCombat.attack(player, SKILL, residual, SATURATED_HIT), "differential cooldown follows actual native armor calculation");
        h.assertTrue(residualObserved.nativeBeforeArmor == HighPowerCombat.MAX_NATIVE_WITNESS && residualObserved.absorption == 1,
                "native armor now processes the bounded true raw residual before exact Pre preparation");
        exact(h, residual, before.subtract(new BigDecimal("7560007.4")), "raw residual 9000010 receives exact armor floor and one absorption");
        int timer = residual.invulnerableTime;
        h.assertTrue(HighPowerCombat.attack(player, SKILL, residual, SATURATED_HIT.multiply(new BigDecimal("1.5"))),
                "stronger raw hit passes exact native comparison despite equal bounded witnesses");
        exact(h, residual, before.subtract(new BigDecimal("11760007.4")), "new raw difference five million receives exact .84 armor mitigation");
        h.assertTrue(residual.invulnerableTime == timer, "stronger differential hit never resets native cooldown timer");
        h.assertFalse(HighPowerCombat.attack(player, SKILL, residual, SATURATED_HIT.multiply(new BigDecimal("1.5"))), "equal logical hit still refuses");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_exact", timeoutTicks = 10)
    public static void exact_raw_mitigation_preserves_tiny_input_digits_and_scale_boundary(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player();
        for (int i = 0; i < 3; i++) {
            // The final case reaches the core's actual scale boundary. Identity config
            // factors may pad scale to 258, but canonical validation must preserve 256.
            BigDecimal raw = SATURATED_HIT.add(BigDecimal.ONE.scaleByPowerOfTen(i == 2 ? -256 : -51));
            Villager target = f.target(5 + i * 4, 5, 5); HighPowerCombat.enroll(target, 5);
            if (i == 1) target.getAttribute(Attributes.ARMOR).setBaseValue(20);
            ObserveDamage observed = new ObserveDamage(target); f.hook(observed);
            h.assertTrue(HighPowerCombat.attack(player, SKILL, target, raw), "high-precision raw hit accepted case " + i);
            BigDecimal debit = i == 1 ? raw.multiply(new BigDecimal("0.84")) : raw;
            exact(h, target, MAX_HP.subtract(debit), "exact raw mitigation cannot discard tiny digits or reject canonical scale256 case " + i);
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_boundary", timeoutTicks = 10)
    public static void unsupported_absorption_and_amplified_native_amounts_fail_closed(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player();
        Villager absorption = f.target(5, 5, 5); HighPowerCombat.enroll(absorption, 5);
        absorption.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20); absorption.setAbsorptionAmount(10);
        NativeDamageRewrite extra = new NativeDamageRewrite(absorption); extra.absorptionBonus = 1; f.hook(extra);
        h.assertFalse(HighPowerCombat.attack(player, SKILL, absorption, LOW_HIT), "unregistered absorption creation cannot grant scalable free protection");
        exact(h, absorption, MAX_HP, "unsupported absorption keeps exact HP unchanged");
        h.assertTrue(absorption.getAbsorptionAmount() == 0, "already-performed native pool side effect is honestly not rolled back");
        Villager amplified = f.target(9, 5, 5); HighPowerCombat.enroll(amplified, 5);
        NativeDamageRewrite twice = new NativeDamageRewrite(amplified); twice.finalPreDamage = 2_000_000F; f.hook(twice);
        h.assertFalse(HighPowerCombat.attack(player, SKILL, amplified, CombatAmounts.CAP), "native amplification above exact per-hit cap is rejected");
        exact(h, amplified, MAX_HP, "above-cap damage cannot silently clip, kill or corrupt exact HP");
        h.assertTrue(absorption.isAlive() && amplified.isAlive(), "fail-closed ledger restores normalized HP before native death checks");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_strict_contract", timeoutTicks = 10)
    public static void vanilla_protection_is_exact_and_breach_refuses_only_highpower(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        HighPowerCombat.enroll(target, 5);
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(true)); target.setItemSlot(EquipmentSlot.HEAD, helmet);
        target.getAttribute(Attributes.ARMOR).setBaseValue(20); target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20); target.setAbsorptionAmount(10);
        h.assertTrue(HighPowerCombat.unsupportedReason(target).isEmpty(), "reviewed Protection IV graph and vanilla provenance are supported: " + HighPowerCombat.unsupportedReason(target));
        ObserveDamage observed = new ObserveDamage(target); f.hook(observed);
        ProtectionReadCounter reads = new ProtectionReadCounter(helmet); f.hook(reads);
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, SATURATED_HIT), "actual vanilla Protection hook executes inside the one native pipeline");
        Villager control = f.target(13, 5, 5); HighPowerCombat.enroll(control, 5);
        ItemStack controlHelmet = helmet.copy(); control.setItemSlot(EquipmentSlot.HEAD, controlHelmet);
        control.getAttribute(Attributes.ARMOR).setBaseValue(20); control.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
        control.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        control.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20); control.setAbsorptionAmount(10);
        ProtectionReadCounter controlReads = new ProtectionReadCounter(controlHelmet); f.hook(controlReads);
        h.assertTrue(control.hurt(control.damageSources().playerAttack(player), HighPowerCombat.MAX_NATIVE_WITNESS), "matched ordinary native control executes the same source, gear and effects");
        h.assertTrue(reads.calls > 0 && reads.calls == controlReads.calls,
                "exact adapter adds no mutable enchantment lookups beyond native immunity/protection calls; exact=" + reads.calls + ", native=" + controlReads.calls);
        h.assertTrue(observed.enchantments > 0 && observed.absorption == 10 && target.getAbsorptionAmount() == 0, "native enchantment and absorption resources execute once");
        exact(h, target, MAX_HP.subtract(new BigDecimal("5644790")), "raw armor .84, Resistance I .8, Protection IV .84, then ten absorption");
        reads.overrideLevel = 1; target.invulnerableTime = 0; // Fresh fixture hit after changing an unregistered native hook.
        h.assertFalse(HighPowerCombat.attack(player, SKILL, target, SATURATED_HIT), "native gameplay-level override cannot evade stored-Protection stage validation");
        controlReads.overrideLevel = 1; control.invulnerableTime = 0;
        NativeDamageRewrite controlVeto = new NativeDamageRewrite(control); controlVeto.finalPreDamage = 0F; f.hook(controlVeto);
        h.assertTrue(control.hurt(control.damageSources().playerAttack(player), HighPowerCombat.MAX_NATIVE_WITNESS), "matched ordinary veto control still runs native enchantment checks");
        h.assertTrue(reads.calls == controlReads.calls,
                "refused exact hit adds no duplicate enchantment lookup relative to a matched native zero-veto control");
        exact(h, target, MAX_HP.subtract(new BigDecimal("5644790")), "unknown gameplay Protection override cannot commit raw damage");
        Villager unsupported = f.target(9, 5, 5); HighPowerCombat.enroll(unsupported, 5);
        ItemStack mace = new ItemStack(Items.MACE);
        mace.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BREACH), 1);
        unsupported.setItemInHand(InteractionHand.MAIN_HAND, mace);
        h.assertTrue(HighPowerCombat.unsupportedReason(unsupported).isPresent(), "Breach requires its own reviewed contract");
        h.assertFalse(HighPowerCombat.attack(player, SKILL, unsupported, SATURATED_HIT), "unsupported enchantment blocks only the high-power bridge");
        h.assertTrue(unsupported.hurt(unsupported.damageSources().generic(), 3), "ordinary native combat still works with unsupported equipment");
        exact(h, unsupported, MAX_HP.subtract(BigDecimal.valueOf(3)), "contract refusal neither heals nor disables ordinary damage");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_strict_contract", timeoutTicks = 10)
    public static void positive_incoming_pre_and_composed_caps_never_scale_into_exact_damage(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player();
        for (int i = 0; i < 3; i++) {
            Villager target = f.target(5 + i * 4, 5, 5); HighPowerCombat.enroll(target, 5);
            target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20); target.setAbsorptionAmount(10);
            NativeDamageRewrite cap = new NativeDamageRewrite(target);
            if (i == 1) cap.finalPreDamage = 5F; else cap.incomingAmount = 5F;
            f.hook(cap);
            if (i == 2) { NativeDamageRewrite multiplier = new NativeDamageRewrite(target); multiplier.incomingMultiplier = 2; f.hook(multiplier); }
            h.assertFalse(HighPowerCombat.attack(player, SKILL, target, SATURATED_HIT), "unregistered positive cap/composed hook refuses exact attempt case " + i);
            exact(h, target, MAX_HP, "native cap five cannot become fifty or another inferred raw amount case " + i);
            h.assertTrue(target.getAbsorptionAmount() == 10, "detected unsupported rewrite is refused before native absorption case " + i);
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_strict_contract", timeoutTicks = 10)
    public static void incoming_positive_then_zero_veto_cannot_mint_cooldown_history(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        HighPowerCombat.enroll(target, 5);
        NativeDamageRewrite rewrite = new NativeDamageRewrite(target); rewrite.incomingAmount = 999990F; rewrite.finalPreDamage = 0F; f.hook(rewrite);
        HighPowerCombat.attack(player, SKILL, target, LOW_HIT);
        exact(h, target, MAX_HP, "zero veto protects HP despite an unregistered preceding positive rewrite");
        rewrite.incomingAmount = null; rewrite.finalPreDamage = null;
        h.assertFalse(HighPowerCombat.attack(player, SKILL, target, LOW_HIT.multiply(BigDecimal.valueOf(2))), "stronger native witness cannot trust the unapproved raw predecessor mapping");
        exact(h, target, MAX_HP, "invalid raw history cannot create a guessed differential exact hit");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_strict_contract", timeoutTicks = 10)
    public static void native_small_nonlinear_control_matches_exact_raw_cooldown_residual(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player();
        Villager exactTarget = f.target(5, 5, 5), control = f.target(9, 5, 5);
        for (Villager target : List.of(exactTarget, control)) {
            HighPowerCombat.enroll(target, 5);
            target.getAttribute(Attributes.ARMOR).setBaseValue(20); target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
            h.assertTrue(target.hurt(target.damageSources().generic(), 90), "ordinary predecessor establishes equal native controls");
        }
        BigDecimal before = new BigDecimal(HighPowerCombat.healthText(exactTarget));
        h.assertTrue(HighPowerCombat.attack(player, SKILL, exactTarget, BigDecimal.valueOf(100)), "raw difference 100 minus 90 passes native cooldown");
        h.assertTrue(control.hurt(control.damageSources().playerAttack(player), 100), "ordinary control uses the same player_attack source and ten-point residual");
        exact(h, exactTarget, before.subtract(BigDecimal.valueOf(3)), "nonlinear armor is evaluated on raw residual ten, not subtracting two mitigated hits");
        exact(h, control, new BigDecimal(HighPowerCombat.healthText(exactTarget)), "native ordinary control agrees at this exactly representable point");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_strict_contract", timeoutTicks = 10)
    public static void unknown_prior_cooldown_refuses_and_modified_ordinary_history_is_observed(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager unknown = f.target(5, 5, 5);
        h.assertTrue(unknown.hurt(unknown.damageSources().generic(), 1), "unenrolled prior hit has no exact mapping");
        HighPowerCombat.enroll(unknown, 5);
        h.assertFalse(HighPowerCombat.attack(player, SKILL, unknown, LOW_HIT), "unmapped pre-enrollment cooldown is conservatively refused");
        exact(h, unknown, MAX_HP, "refusal does not fabricate predecessor raw damage");
        Villager known = f.target(9, 5, 5); HighPowerCombat.enroll(known, 5);
        known.getPersistentData().putInt("academy:md_mark_ticks", 20); known.getPersistentData().putFloat("academy:md_mark_rate", .5F);
        h.assertTrue(known.hurt(known.damageSources().generic(), 10), "ordinary RadiationMarks hook changes ten to five natively");
        exact(h, known, MAX_HP.subtract(BigDecimal.valueOf(5)), "ordinary incoming hook stays active");
        known.getPersistentData().remove("academy:md_mark_ticks");
        h.assertTrue(HighPowerCombat.attack(player, SKILL, known, BigDecimal.TEN), "once unsupported mark ends, native predecessor five is usable");
        exact(h, known, MAX_HP.subtract(BigDecimal.TEN), "history records actual pre-armor incoming five, not original ten");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_strict_contract", timeoutTicks = 10)
    public static void runtime_contract_and_explicit_legacy_approval_never_refill_pools(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        HighPowerCombat.enroll(target, 5); HighPowerCombat.attack(player, SKILL, target, LOW_HIT);
        HighPowerCombat.spendShield(target, BigDecimal.TEN);
        String health = HighPowerCombat.healthText(target), shield = HighPowerCombat.shieldText(target);
        target.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).remove("adapterContract");
        h.assertTrue(HighPowerCombat.needsContractApproval(target) && HighPowerCombat.unsupportedReason(target).isPresent(), "old ledgers cannot silently acquire a new damage contract");
        h.assertFalse(HighPowerCombat.attack(player, SKILL, target, LOW_HIT), "unapproved legacy ledger refuses high-power attack");
        h.assertTrue(HighPowerCombat.approveVanillaContract(target), "explicit approval accepts a reviewed vanilla environment");
        h.assertTrue(health.equals(HighPowerCombat.healthText(target)) && shield.equals(HighPowerCombat.shieldText(target)), "approval preserves every HP and shield digit");
        Map<String, String> trusted = Map.of("minecraft", "1.21.1", "neoforge", "21.1.252", "academy", "0.1.0-m41-highpower.0.81.5-city-a3.61");
        var previousLandingVisual = new java.util.HashMap<>(trusted); previousLandingVisual.put("academy", "0.1.0-m41-highpower.0.56-prototype");
        h.assertTrue(VanillaCombatContract.runtimeReason(previousLandingVisual, Set.of("vanilla", "mod_data")).isPresent(), "released .56 cannot masquerade as the reviewed .57 artifact");
        var releasedFallback = new java.util.HashMap<>(trusted); releasedFallback.put("academy", "0.1.0-m41-highpower.0.50-prototype");
        h.assertTrue(VanillaCombatContract.runtimeReason(releasedFallback, Set.of("vanilla", "mod_data")).isPresent(), "released .50 cannot masquerade as current .54 reviewed artifact");
        var previousFunctional = new java.util.HashMap<>(trusted); previousFunctional.put("academy", "0.1.0-m41-highpower.0.51-prototype");
        h.assertTrue(VanillaCombatContract.runtimeReason(previousFunctional, Set.of("vanilla", "mod_data")).isPresent(), "released .51 cannot masquerade as current .54 reviewed artifact");
        var previousVisual = new java.util.HashMap<>(trusted); previousVisual.put("academy", "0.1.0-m41-highpower.0.52-prototype");
        h.assertTrue(VanillaCombatContract.runtimeReason(previousVisual, Set.of("vanilla", "mod_data")).isPresent(), "released .52 cannot masquerade as current .54 reviewed artifact");
        var plasmaVisual = new java.util.HashMap<>(trusted); plasmaVisual.put("academy", "0.1.0-m41-highpower.0.53-prototype");
        h.assertTrue(VanillaCombatContract.runtimeReason(plasmaVisual, Set.of("vanilla", "mod_data")).isPresent(), "released .53 cannot masquerade as current .54 reviewed artifact");
        h.assertTrue(VanillaCombatContract.runtimeReason(trusted, Set.of("vanilla", "mod_data")).isEmpty(), "named reviewed runtime is admitted");
        var previousBuild = new java.util.HashMap<>(trusted); previousBuild.put("academy", "0.1.0-m41-highpower.0.18-prototype");
        h.assertTrue(VanillaCombatContract.runtimeReason(previousBuild, Set.of("vanilla", "mod_data")).isPresent(), "previous build cannot masquerade as current reviewed artifact");
        var foreign = new java.util.HashMap<>(trusted); foreign.put("foreign_cap_plugin", "1.0");
        h.assertTrue(VanillaCombatContract.runtimeReason(foreign, Set.of("vanilla", "mod_data")).isPresent(), "unknown mod is refused even when its raw-only cap leaves a witness unchanged");
        h.assertTrue(VanillaCombatContract.runtimeReason(trusted, Set.of("vanilla", "file/custom-defense-data")).isPresent(), "unreviewed data pack is refused independently of namespace names");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_strict_contract", timeoutTicks = 10)
    public static void unsupported_caster_or_primary_cannot_spend_cast_budget(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.readyCaster(); Villager target = f.target(5.5, 2, 10.5);
        HighPowerCombat.enroll(target, 5);
        target.getPersistentData().putInt("academy:md_mark_ticks", 20); target.getPersistentData().putFloat("academy:md_mark_rate", 2);
        h.assertFalse(ElectromasterPrototype.cast(player), "unsupported primary is rejected before committing CP or cooldown");
        h.assertTrue(player.getPersistentData().getCompound("academy:electromaster_prototype").getDouble("cp") == 200, "primary rejection does not spend CP");
        target.getPersistentData().remove("academy:md_mark_ticks");
        player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).remove("adapterContract");
        h.assertFalse(ElectromasterPrototype.cast(player), "unapproved caster is rejected before budget commit");
        h.assertTrue(player.getPersistentData().getCompound("academy:electromaster_prototype").getDouble("cp") == 200, "caster rejection does not spend CP");
        exact(h, target, MAX_HP, "neither structural refusal damages the target");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_strict_contract", timeoutTicks = 10)
    public static void nested_exact_post_attack_cannot_replace_outer_history_or_replay_damage(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        HighPowerCombat.enroll(target, 5); NestedExactHit nested = new NestedExactHit(player, target); f.hook(nested);
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, LOW_HIT), "outer exact attack is accepted");
        h.assertTrue(nested.called && !nested.accepted, "same-entity reentrant exact attack is refused while native outer hurt is active");
        exact(h, target, MAX_HP.subtract(LOW_HIT), "only outer exact hit commits");
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, LOW_HIT.multiply(BigDecimal.TEN)), "nested refusal cannot replace history and prevent the genuine stronger raw hit");
        exact(h, target, MAX_HP.subtract(LOW_HIT.multiply(BigDecimal.TEN)), "second hit charges only its stronger raw differential");
        h.assertFalse(HighPowerCombat.attack(player, SKILL, target, LOW_HIT), "weaker raw hit remains refused after the stronger hit");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_strict_contract", timeoutTicks = 10)
    public static void invalid_late_absorption_cannot_leave_a_permanent_native_trace(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        HighPowerCombat.enroll(target, 5);
        NativeDamageRewrite invalid = new NativeDamageRewrite(target); invalid.absorptionBonus = Float.NaN; f.hook(invalid);
        h.assertFalse(HighPowerCombat.attack(player, SKILL, target, LOW_HIT), "invalid late reduction refuses exact transaction");
        exact(h, target, MAX_HP, "invalid native number cannot corrupt exact HP");
        // The foreign callback damaged a native resource, which the adapter must not
        // pretend to roll back. Repair only this fixture before probing trace cleanup.
        invalid.absorptionBonus = 0; target.setAbsorptionAmount(0); target.invulnerableTime = 0;
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, LOW_HIT), "fresh valid attack proves rejected Post popped its transient trace");
        exact(h, target, MAX_HP.subtract(LOW_HIT), "fresh transaction commits once after explicit fixture repair");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_exact_cooldown", timeoutTicks = 20)
    public static void saturated_stronger_raw_hits_preserve_small_differences_without_timer_reset(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        HighPowerCombat.enroll(target, 5);
        BigDecimal first = BigDecimal.valueOf(HighPowerCombat.MAX_NATIVE_WITNESS).multiply(BigDecimal.valueOf(2));
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, first), "first high-power hit establishes logical raw history");
        h.runAtTickTime(4, () -> {
            int timer = target.invulnerableTime;
            h.assertTrue(timer > 10 && timer < 20, "actual native timer has advanced within differential window");
            BigDecimal second = first.add(LOW_HIT), third = second.add(BigDecimal.ONE);
            h.assertTrue(HighPowerCombat.attack(player, SKILL, target, second), "a capped base plus an exact LV1 unit is genuinely stronger despite identical float witnesses");
            exact(h, target, MAX_HP.subtract(second), "second hit debits only the stronger-hit difference");
            h.assertTrue(HighPowerCombat.attack(player, SKILL, target, third), "another exact one-point difference remains representable independently of the huge base");
            exact(h, target, MAX_HP.subtract(third), "third hit debits exactly one HP");
            h.assertTrue(target.invulnerableTime == timer, "neither stronger hit resets or extends native cooldown");
            h.assertFalse(HighPowerCombat.attack(player, SKILL, target, third), "equal logical hit is still rejected");
            h.assertFalse(HighPowerCombat.attack(player, SKILL, target, second), "weaker logical hit is still rejected");
            exact(h, target, MAX_HP.subtract(third), "refusals do not debit or refill HP");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "highpower_exact_cooldown", timeoutTicks = 10)
    public static void ordinary_exact_mixed_cooldown_compares_raw_and_transports_true_residual(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        HighPowerCombat.enroll(target, 5);
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, BigDecimal.valueOf(HighPowerCombat.MAX_NATIVE_WITNESS).multiply(BigDecimal.valueOf(2))), "initial exact two-million hit");
        int timer = target.invulnerableTime;
        h.assertFalse(target.hurt(target.damageSources().generic(), 1_000_001F), "ordinary amount above witness but below logical prior must refuse");
        h.assertTrue(target.hurt(target.damageSources().generic(), 3_000_000F), "ordinary stronger raw amount enters native differential branch");
        exact(h, target, MAX_HP.subtract(new BigDecimal("3000000")), "ordinary hit receives true two-million residual, not nineteen million");
        ObserveDamage observed = new ObserveDamage(target); f.hook(observed);
        h.assertTrue(target.hurt(target.damageSources().generic(), 3_100_000F), "following ordinary hit returns to unmodified native comparison");
        h.assertTrue(observed.cooldown == 3_000_000F, "native lastHurt was actually committed to the ordinary incoming value");
        exact(h, target, MAX_HP.subtract(new BigDecimal("3100000")), "ordinary-to-ordinary differential is one hundred thousand");
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, new BigDecimal("3200000")), "exact stronger hit can follow an ordinary predecessor above witness cap");
        exact(h, target, MAX_HP.subtract(new BigDecimal("3200000")), "true hundred-thousand raw differential reaches armor/HP despite negative obsolete float argument");
        h.assertFalse(target.hurt(target.damageSources().generic(), 3_150_000F), "ordinary weaker amount cannot exploit the restored bounded native field");
        h.assertTrue(target.invulnerableTime == timer, "mixed differential hits never reset the native timer");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_exact_cooldown", timeoutTicks = 10)
    public static void canceled_and_zeroed_stronger_hits_keep_native_history_commit_rules(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        HighPowerCombat.enroll(target, 5); HighPowerCombat.attack(player, SKILL, target, LOW_HIT);
        DamageGate gate = new DamageGate(target); f.hook(gate); gate.cancel = true;
        h.assertFalse(HighPowerCombat.attack(player, SKILL, target, LOW_HIT.multiply(BigDecimal.valueOf(2))), "incoming cancellation happens before comparison and preserves prior history");
        gate.cancel = false;
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, LOW_HIT.multiply(BigDecimal.valueOf(2))), "uncanceled stronger hit still compares against first raw input");
        exact(h, target, MAX_HP.subtract(LOW_HIT.multiply(BigDecimal.valueOf(2))), "canceled attempt never replaced history");
        gate.zero = true; HighPowerCombat.attack(player, SKILL, target, LOW_HIT.multiply(BigDecimal.valueOf(3)));
        exact(h, target, MAX_HP.subtract(LOW_HIT.multiply(BigDecimal.valueOf(2))), "final zero veto protects HP");
        gate.zero = false;
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, LOW_HIT.multiply(BigDecimal.valueOf(4))), "native-accepted zeroed third hit still advanced pre-mitigation logical history");
        exact(h, target, MAX_HP.subtract(LOW_HIT.multiply(BigDecimal.valueOf(3))), "fourth hit charges only one raw hit beyond zeroed third input");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_exact_cooldown", timeoutTicks = 20)
    public static void native_shield_stage_still_precedes_logical_cooldown_refusal(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5.5, 2, 8.5);
        HighPowerCombat.enroll(target, 5); HighPowerCombat.attack(player, SKILL, target, LOW_HIT);
        target.setYRot(180); target.setYHeadRot(180); target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        target.startUsingItem(InteractionHand.OFF_HAND); ShieldStageCounter shield = new ShieldStageCounter(target); f.hook(shield);
        h.runAtTickTime(7, () -> {
            target.setYRot(180); target.setYHeadRot(180);
            h.assertTrue(target.isBlocking() && target.invulnerableTime > 10, "native shield raised inside the actual cooldown window");
            int timer = target.invulnerableTime;
            h.assertFalse(target.hurt(target.damageSources().playerAttack(player), 1_000_001F), "blocked/weaker ordinary hit is refused by native branch");
            h.assertTrue(shield.calls == 1 && shield.originallyBlocked && shield.shieldDamage > 0,
                    "native shield decision and durability amount occur before the logical comparison, not an early incoming cancellation");
            exact(h, target, MAX_HP.subtract(LOW_HIT), "block/refusal does not change exact HP");
            h.assertTrue(target.invulnerableTime == timer, "blocking/refusal does not reset the timer");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "highpower_exact_cooldown", timeoutTicks = 25)
    public static void thrown_native_hooks_unwind_ordinary_and_exact_frames_and_traces(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); List<Villager> targets = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Villager target = f.target(5 + i * 4, 5, 5); HighPowerCombat.enroll(target, 5); targets.add(target);
            ThrowOnceDamage hook = new ThrowOnceDamage(target, i >= 2); f.hook(hook);
            boolean propagated = false;
            try {
                if (i % 2 == 0) target.hurt(target.damageSources().generic(), 1000);
                else HighPowerCombat.attack(player, SKILL, target, LOW_HIT);
            } catch (RuntimeException expected) { propagated = true; }
            h.assertTrue(propagated && !hook.armed, "ordinary/exact native hook exception propagates unchanged case " + i);
            exact(h, target, MAX_HP, "throw before HP phase does not corrupt the ledger case " + i);
        }
        h.runAtTickTime(11, () -> {
            for (Villager target : targets) {
                h.assertTrue(target.invulnerableTime <= 10, "native cooldown elapsed naturally after exception");
                h.assertTrue(HighPowerCombat.attack(player, SKILL, target, LOW_HIT), "no abandoned frame/trace poisons a later valid hit");
                exact(h, target, MAX_HP.subtract(LOW_HIT), "fresh hit commits once after exceptional unwind");
            }
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "highpower_events", timeoutTicks = 10)
    public static void legacy_mutable_skill_hook_denies_or_amplifies_without_losing_original_precision(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        HighPowerCombat.enroll(target, 5); SkillGate hook = new SkillGate(target); f.hook(hook);
        hook.multiplier = 0;
        h.assertFalse(HighPowerCombat.attack(player, SKILL, target, LOW_HIT), "zeroed legacy hook denies hit before native side effects");
        exact(h, target, MAX_HP, "denied hook is free of HP mutation");
        hook.multiplier = 2;
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, LOW_HIT), "legacy double amplification is retained");
        exact(h, target, MAX_HP.subtract(LOW_HIT.multiply(BigDecimal.valueOf(2))), "hook amplification debits once");
        target.invulnerableTime = 0;
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, CombatAmounts.CAP), "above-cap hook result is an overkill held at the ceiling, not a refused hit");
        exact(h, target, BigDecimal.ZERO, "the over-ceiling hit defeats the target instead of being ignored");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_native_lifecycle", timeoutTicks = 10)
    public static void native_totem_death_and_death_veto_remain_authoritative(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player();
        Villager protectedTarget = f.target(5, 5, 5); HighPowerCombat.enroll(protectedTarget, 1);
        protectedTarget.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        h.assertTrue(HighPowerCombat.attack(player, SKILL, protectedTarget, CombatAmounts.CAP), "lethal exact hit goes through native hurt");
        h.assertTrue(protectedTarget.isAlive() && protectedTarget.getOffhandItem().isEmpty(), "native totem is consumed and saves target");
        exact(h, protectedTarget, LOW_HIT, "totem's one normalized native HP restores five percent of this ledger");
        Villager victim = f.target(9, 5, 5); HighPowerCombat.enroll(victim, 1);
        HighPowerCombat.attack(player, SKILL, victim, CombatAmounts.CAP);
        h.assertTrue(victim.isDeadOrDying(), "native lethal flow remains responsible for death");
        exact(h, victim, BigDecimal.ZERO, "native lethal result matches exact zero");
        Villager vetoed = f.target(13, 5, 5); HighPowerCombat.enroll(vetoed, 1);
        f.hook(new DeathVeto(vetoed)); HighPowerCombat.attack(player, SKILL, vetoed, CombatAmounts.CAP);
        h.assertTrue(vetoed.isAlive(), "native LivingDeathEvent cancellation remains effective");
        exact(h, vetoed, cn.academy.port.highpower.core.HighPowerLevel.LV1.maxHealth(), "death veto rolls back exact lethal HP transaction");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_native_lifecycle", timeoutTicks = 10)
    public static void native_admin_kill_preserves_its_explicit_death_semantics(GameTestHelper h) {
        Fixture f = new Fixture(h); Villager target = f.target(5, 5, 5); HighPowerCombat.enroll(target, 5);
        target.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        target.kill();
        h.assertTrue(target.isDeadOrDying(), "native kill's Float.MAX_VALUE cannot accidentally become immortality");
        exact(h, target, BigDecimal.ZERO, "explicit generic_kill maps to the existing capped ledger HP");
        h.assertFalse(target.hasEffect(MobEffects.REGENERATION), "vanilla generic_kill retains its native totem bypass");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_native_lifecycle", timeoutTicks = 10)
    public static void ordinary_same_tick_followup_cannot_overwrite_native_totem_rescue(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player();
        // A stronger second native hit can pass vanilla's differential hurt cooldown.
        // Exercise no absorption, fully absorbed damage, and partial absorption.
        float[] secondHits = {1020, 1004, 1020};
        BigDecimal rescue = cn.academy.port.highpower.core.HighPowerLevel.LV2.damage();
        BigDecimal[] expectedHp = {rescue.subtract(BigDecimal.valueOf(20)), rescue, rescue.subtract(BigDecimal.valueOf(12))};
        float[] expectedAbsorption = {0, 4, 0};
        for (int i = 0; i < secondHits.length; i++) {
            Villager target = f.target(5 + i * 4, 5, 5); HighPowerCombat.enroll(target, 2);
            h.assertTrue(HighPowerCombat.attack(player, SKILL, target, cn.academy.port.highpower.core.HighPowerLevel.LV2.maxHealth().subtract(BigDecimal.valueOf(100))), "fixture lowers exact HP through the real damage bridge");
            exact(h, target, new BigDecimal("100"), "fixture has one hundred exact HP before native lethal hit");
            target.invulnerableTime = 0; // Fixture setup only, never between the two ordinary hits.
            target.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            long sameTick = h.getLevel().getGameTime();
            h.assertTrue(target.hurt(target.damageSources().generic(), 1000), "first ordinary hit is accepted and lethal to exact HP");
            h.assertTrue(target.isAlive() && target.getOffhandItem().isEmpty(), "native totem already rescued this entity");
            if (i == 0) { target.removeEffect(MobEffects.ABSORPTION); target.setAbsorptionAmount(0); }
            else h.assertTrue(target.getAbsorptionAmount() == 8, "native totem supplies eight absorption points");
            h.assertTrue(target.hurt(target.damageSources().generic(), secondHits[i]), "stronger same-tick ordinary hit passes native hurt cooldown");
            h.assertTrue(h.getLevel().getGameTime() == sameTick, "no world tick or synthetic recovery separates the two native hits");
            h.assertTrue(target.isAlive(), "follow-up damage cannot revive the stale zero ledger and kill a rescued target");
            exact(h, target, expectedHp[i], "totem HP is restored before native follow-up subtraction case " + i);
            h.assertTrue(target.getAbsorptionAmount() == expectedAbsorption[i], "follow-up consumes native absorption exactly once case " + i);
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_native_lifecycle", timeoutTicks = 10)
    public static void same_tick_native_heal_cannot_multiply_pending_totem_recovery(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        prepareOrdinaryTotem(h, player, target);
        long sameTick = h.getLevel().getGameTime();
        target.heal(1);
        h.assertTrue(target.hurt(target.damageSources().generic(), 1004), "same-tick stronger follow-up is accepted and absorbed");
        h.assertTrue(h.getLevel().getGameTime() == sameTick && target.isAlive(), "no tick is needed to reconcile native rescue and healing");
        exact(h, target, cn.academy.port.highpower.core.HighPowerLevel.LV2.damage().add(BigDecimal.ONE), "one native heal adds one exact HP after the five-percent totem restoration");
        h.assertTrue(target.getAbsorptionAmount() == 4, "following native absorption remains intact");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_native_lifecycle", timeoutTicks = 10)
    public static void next_native_regeneration_tick_does_not_double_totem_recovery(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        prepareOrdinaryTotem(h, player, target);
        h.runAtTickTime(2, () -> {
            exact(h, target, cn.academy.port.highpower.core.HighPowerLevel.LV2.damage().add(BigDecimal.ONE), "native totem Regeneration II tick heals one exact HP, not another five percent");
            h.assertTrue(target.isAlive(), "native totem survivor remains alive through actual entity ticks");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "highpower_native_lifecycle", timeoutTicks = 10)
    public static void pending_native_totem_recovery_survives_entity_save_reload(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager target = f.target(5, 5, 5);
        prepareOrdinaryTotem(h, player, target);
        // Save before a tick or high-power wrapper can reconcile the ordinary hit.
        CompoundTag saved = target.saveWithoutId(new CompoundTag());
        target.discard();
        Villager reloaded = new Villager(EntityType.VILLAGER, h.getLevel()); reloaded.load(saved); f.entity(reloaded);
        h.assertTrue(HighPowerCombat.enabled(reloaded), "persisted pending marker is valid bounded decimal state");
        h.assertTrue(reloaded.hurt(reloaded.damageSources().generic(), 4), "native follow-up after reload reaches absorption");
        h.assertTrue(reloaded.isAlive(), "save/reload cannot discard a completed native totem rescue");
        exact(h, reloaded, cn.academy.port.highpower.core.HighPowerLevel.LV2.damage(), "persisted marker restores one normalized native HP before follow-up");
        h.assertTrue(reloaded.getAbsorptionAmount() == 4, "saved native absorption is consumed once after reload");
        h.succeed();
    }

    private static void prepareOrdinaryTotem(GameTestHelper h, ServerPlayer player, Villager target) {
        h.assertTrue(HighPowerCombat.enroll(target, 2), "totem fixture enrolled at level two");
        h.assertTrue(HighPowerCombat.attack(player, SKILL, target, cn.academy.port.highpower.core.HighPowerLevel.LV2.maxHealth().subtract(BigDecimal.valueOf(100))), "real bridge lowers fixture exact HP to one hundred");
        target.invulnerableTime = 0;
        target.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        h.assertTrue(target.hurt(target.damageSources().generic(), 1000), "ordinary native lethal hit accepted");
        h.assertTrue(target.isAlive() && target.getOffhandItem().isEmpty() && target.getHealth() == 1,
                "actual native totem has consumed itself and restored one adapter HP");
    }

    @GameTest(template = TEMPLATE, batch = "highpower_natural", timeoutTicks = 10)
    public static void ordinary_damage_and_cancelable_healing_reconcile_to_exact_health(GameTestHelper h) {
        Fixture f = new Fixture(h); Villager target = f.target(5, 5, 5); HighPowerCombat.enroll(target, 5);
        h.assertTrue(target.hurt(target.damageSources().generic(), 3), "ordinary native damage also enters exact ledger");
        exact(h, target, MAX_HP.subtract(BigDecimal.valueOf(3)), "natural three damage remains three exact HP");
        target.heal(1);
        h.runAtTickTime(1, () -> {
            exact(h, target, MAX_HP.subtract(BigDecimal.valueOf(2)), "natural heal reconciles after event cancellation is final");
            f.hook(new HealVeto(target)); target.heal(1);
            h.runAtTickTime(2, () -> {
                exact(h, target, MAX_HP.subtract(BigDecimal.valueOf(2)), "canceled heal cannot alter exact HP"); h.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = "highpower_boundary", timeoutTicks = 10)
    public static void ordinary_targets_are_bounded_and_boss_or_corrupt_adapters_fail_closed(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.player(); Villager ordinary = f.target(5, 5, 5);
        ObserveDamage observed = new ObserveDamage(ordinary); f.hook(observed);
        h.assertTrue(HighPowerCombat.attack(player, SKILL, ordinary, CombatAmounts.CAP), "ordinary listed target uses a real native hurt call");
        h.assertTrue(observed.original == HighPowerCombat.MAX_NATIVE_WITNESS, "unmarked ordinary target receives bounded witness only");
        h.assertFalse(HighPowerCombat.enabled(ordinary), "ordinary hit does not secretly enroll target");
        WitherBoss boss = new WitherBoss(EntityType.WITHER, h.getLevel());
        h.assertFalse(HighPowerCombat.enroll(boss, 5), "unsupported vanilla boss enrollment denied");
        h.assertFalse(HighPowerCombat.attack(player, SKILL, boss, LOW_HIT), "boss phases are not bypassed with a fake adapter");
        Villager corrupt = f.target(9, 5, 5); HighPowerCombat.enroll(corrupt, 5);
        corrupt.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health", "1e99999999");
        h.assertFalse(HighPowerCombat.enabled(corrupt), "malformed out-of-range NBT fails validation");
        h.assertFalse(HighPowerCombat.attack(player, SKILL, corrupt, LOW_HIT), "marked corrupt save cannot silently fall through to ordinary damage");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_clone", timeoutTicks = 10)
    public static void player_clone_preserves_decimal_pool_and_death_respawn_is_explicit(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer original = f.player();
        h.assertTrue(HighPowerCombat.spendShield(original, LOW_HIT), "directional ability can debit shared pool explicitly");
        BigDecimal remaining = HighPowerCombat.shield(original); HighPowerCombat.shieldActive(original, true);
        ServerPlayer replacement = f.barePlayer(); NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, original, false));
        h.assertTrue(HighPowerCombat.enabled(replacement) && HighPowerCombat.shield(replacement).compareTo(remaining) == 0,
                "non-death clone retains exact spent balance");
        h.assertFalse(HighPowerCombat.shieldActive(replacement), "clone never retains an active input state");
        ServerPlayer respawn = f.barePlayer(); NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(respawn, replacement, true));
        exact(h, respawn, MAX_HP, "actual death clone explicitly restores preset HP");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_arc", timeoutTicks = 10)
    public static void forked_arc_stops_at_real_solid_wall(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.readyCaster(); Villager target = f.target(5.5, 2, 13.5);
        HighPowerCombat.enroll(target, 5);
        for (int x = 4; x <= 6; x++) for (int y = 1; y <= 5; y++) h.setBlock(new BlockPos(x, y, 9), Blocks.STONE);
        h.assertTrue(ElectromasterPrototype.cast(player), "ready server-owned cast executes");
        exact(h, target, MAX_HP, "actual block clip stops the arc before target");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_arc", timeoutTicks = 10)
    public static void forked_arc_requires_conduction_and_limits_to_two_branches(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.readyCaster();
        Villager primary = f.target(5.5, 2, 12.5); HighPowerCombat.enroll(primary, 5);
        Villager first = f.target(7.5, 2, 12.5); HighPowerCombat.enroll(first, 5);
        Villager second = f.target(3.5, 2, 12.5); HighPowerCombat.enroll(second, 5);
        Villager third = f.target(9.5, 2, 12.5); HighPowerCombat.enroll(third, 5);
        Villager dry = f.target(5.5, 2, 15.5); HighPowerCombat.enroll(dry, 5);
        long expiry = h.getLevel().getGameTime() + 100;
        for (Villager target : List.of(first, second, third)) target.getPersistentData().putLong("academy:conductive_until", expiry);
        h.assertTrue(ElectromasterPrototype.cast(player), "actual first slice cast executes");
        exact(h, primary, MAX_HP.subtract(LOW_HIT), "primary receives level-one exact hit");
        BigDecimal branch = LOW_HIT.multiply(new BigDecimal("0.35"));
        exact(h, first, MAX_HP.subtract(branch), "nearest marked branch one receives attenuated damage");
        exact(h, second, MAX_HP.subtract(branch), "nearest marked branch two receives attenuated damage");
        exact(h, third, MAX_HP, "third eligible conductor is outside branch budget");
        exact(h, dry, MAX_HP, "unmarked dry target is never an eligible branch");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_bracer", timeoutTicks = 10)
    public static void magnetic_bracer_redirects_front_arrow_only_and_toggle_cannot_refill(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.readyCaster();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ElectromasterPrototype.CONTROL.get()));
        player.setShiftKeyDown(true); player.startUsingItem(InteractionHand.MAIN_HAND);
        Arrow front = f.arrow(player.getEyePosition().add(0, 0, 2), new Vec3(0, 0, -1));
        Arrow rear = f.arrow(player.getEyePosition().add(0, 0, -2), new Vec3(0, 0, 1));
        Snowball nonmetal = new Snowball(EntityType.SNOWBALL, h.getLevel());
        nonmetal.setPos(player.getEyePosition().add(.5, 0, 2)); nonmetal.setDeltaMovement(0, 0, -1); f.entity(nonmetal);
        Vec3 rearBefore = rear.getDeltaMovement(), nonmetalBefore = nonmetal.getDeltaMovement();
        BigDecimal shieldBefore = HighPowerCombat.shield(player);
        player.getPersistentData().getCompound("academy:electromaster_prototype").putLong("last_tick", Long.MIN_VALUE);
        ElectromasterPrototype.tick(new PlayerTickEvent.Post(player));
        h.assertTrue(front.getDeltaMovement().z > 0 && !front.isRemoved(), "front tagged arrow is redirected, not deleted or duplicated");
        h.assertTrue(rear.getDeltaMovement().equals(rearBefore), "rear arrow lies outside front cone");
        h.assertTrue(nonmetal.getDeltaMovement().equals(nonmetalBefore), "nonmetal projectile momentum remains untouched");
        BigDecimal expected = shieldBefore.subtract(LOW_HIT.multiply(new BigDecimal("0.1")));
        h.assertTrue(HighPowerCombat.shield(player).compareTo(expected) == 0, "one actual deflection spends exactly 0.1D");
        ElectromasterPrototype.tick(new PlayerTickEvent.Post(player));
        h.assertTrue(HighPowerCombat.shield(player).compareTo(expected) == 0, "same-tick controller replay cannot spend twice");
        h.runAtTickTime(1, () -> {
            player.stopUsingItem(); ElectromasterPrototype.tick(new PlayerTickEvent.Post(player));
            h.assertTrue(HighPowerCombat.shield(player).compareTo(expected) == 0, "releasing bracer cannot refill the pool");
            h.runAtTickTime(2, () -> {
                player.startUsingItem(InteractionHand.MAIN_HAND); ElectromasterPrototype.tick(new PlayerTickEvent.Post(player));
                h.assertTrue(HighPowerCombat.shield(player).compareTo(expected) == 0, "reactivating bracer cannot refill the pool");
                h.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = "highpower_interaction", timeoutTicks = 10)
    public static void close_villager_interaction_casts_once_and_respects_prior_cancellation(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.readyCaster();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ElectromasterPrototype.CONTROL.get()));
        Villager target = f.target(5.5, 2, 6.5); HighPowerCombat.enroll(target, 5);
        var denied = new PlayerInteractEvent.EntityInteractSpecific(player, InteractionHand.MAIN_HAND, target, new Vec3(0, 1, 0));
        denied.setCanceled(true); NeoForge.EVENT_BUS.post(denied);
        exact(h, target, MAX_HP, "previously canceled close-entity interaction cannot cast");
        h.assertTrue(player.getPersistentData().getCompound("academy:electromaster_prototype").getDouble("cp") == 200,
                "previous cancellation cannot consume CP");
        var accepted = new PlayerInteractEvent.EntityInteractSpecific(player, InteractionHand.MAIN_HAND, target, new Vec3(0, 1, 0));
        NeoForge.EVENT_BUS.post(accepted);
        h.assertTrue(accepted.isCanceled() && accepted.getCancellationResult().consumesAction(), "native interaction event is consumed before villager trading can swallow the cast");
        exact(h, target, MAX_HP.subtract(LOW_HIT), "close NPC receives one exact level-one cast");
        h.assertTrue(player.getPersistentData().getCompound("academy:electromaster_prototype").getDouble("cp") == 190,
                "accepted interaction spends one cast budget");
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteractSpecific(player, InteractionHand.MAIN_HAND, target, new Vec3(0, 1, 0)));
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, target));
        exact(h, target, MAX_HP.subtract(LOW_HIT), "same-tick specific/general interaction replay cannot duplicate damage");
        h.assertTrue(player.getPersistentData().getCompound("academy:electromaster_prototype").getDouble("cp") == 190,
                "same-tick interaction replay cannot consume another cast budget");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_bracer", timeoutTicks = 10)
    public static void stale_and_rear_arrows_cannot_starve_real_incoming_threat(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.readyCaster();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ElectromasterPrototype.CONTROL.get()));
        player.setShiftKeyDown(true); player.startUsingItem(InteractionHand.MAIN_HAND);
        List<Arrow> stale = new ArrayList<>(), rear = new ArrayList<>();
        // Sixteen earlier stationary arrows share the incoming arrow's entity section.
        // None is a moving threat and none should consume the inspection budget.
        for (int i = 0; i < 16; i++) stale.add(f.arrow(player.getEyePosition().add(0, 0, 2), Vec3.ZERO));
        for (int i = 0; i < 8; i++) rear.add(f.arrow(player.getEyePosition().add(0, 0, -2), new Vec3(0, 0, 1)));
        Arrow incoming = f.arrow(player.getEyePosition().add(0, 0, 2), new Vec3(0, 0, -1));
        BigDecimal before = HighPowerCombat.shield(player);
        player.getPersistentData().getCompound("academy:electromaster_prototype").putLong("last_tick", Long.MIN_VALUE);
        ElectromasterPrototype.tick(new PlayerTickEvent.Post(player));
        h.assertTrue(incoming.getDeltaMovement().z > 0 && !incoming.isRemoved(), "eligible incoming threat still receives an actual momentum deflection");
        for (Arrow arrow : stale) h.assertTrue(arrow.getDeltaMovement().equals(Vec3.ZERO), "stationary arrows remain untouched");
        for (Arrow arrow : rear) h.assertTrue(arrow.getDeltaMovement().equals(new Vec3(0, 0, 1)), "rear arrows remain untouched");
        h.assertTrue(HighPowerCombat.shield(player).compareTo(before.subtract(LOW_HIT.multiply(new BigDecimal("0.1")))) == 0,
                "crowded scan pays only for one eligible deflection");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "highpower_bracer", timeoutTicks = 10)
    public static void real_wall_embedded_arrow_keeps_stale_velocity_but_never_spends_guard(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.readyCaster();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ElectromasterPrototype.CONTROL.get()));
        player.setShiftKeyDown(true); player.startUsingItem(InteractionHand.MAIN_HAND);
        h.setBlock(new BlockPos(5, 3, 6), Blocks.STONE);
        Arrow embedded = f.arrow(player.getEyePosition().add(0, 0, 3.5), new Vec3(0, 0, -2));
        embedded.tick(); // Real native movement and block-hit processing; no fixture write to inGround.
        Vec3 staleVelocity = embedded.getDeltaMovement(), embeddedPosition = embedded.position();
        Vec3 toEmbedded = embeddedPosition.subtract(player.getEyePosition());
        h.assertTrue(embedded.inGround, "real Arrow.tick collision embeds the arrow in the wall");
        h.assertTrue(staleVelocity.lengthSqr() >= .001 && staleVelocity.dot(toEmbedded) < 0
                        && toEmbedded.lengthSqr() <= 16 && toEmbedded.normalize().dot(player.getLookAngle()) >= .5,
                "embedded arrow retains nonzero inward velocity that otherwise matches the front threat filter");
        BigDecimal before = HighPowerCombat.shield(player);
        player.getPersistentData().getCompound("academy:electromaster_prototype").putLong("last_tick", Long.MIN_VALUE);
        ElectromasterPrototype.tick(new PlayerTickEvent.Post(player));
        h.assertTrue(HighPowerCombat.shield(player).compareTo(before) == 0, "real embedded arrow cannot consume a deflection charge");
        h.assertTrue(embedded.inGround && embedded.position().equals(embeddedPosition)
                        && embedded.getDeltaMovement().equals(staleVelocity),
                "guard neither moves nor rewrites a grounded arrow's stale velocity");
        h.runAtTickTime(1, () -> {
            Arrow incoming = f.arrow(player.getEyePosition().add(1, 0, 2), new Vec3(0, 0, -1));
            ElectromasterPrototype.tick(new PlayerTickEvent.Post(player));
            h.assertTrue(!incoming.inGround && incoming.getDeltaMovement().z > 0, "a fresh approaching arrow still receives a real deflection");
            h.assertTrue(HighPowerCombat.shield(player).compareTo(before.subtract(LOW_HIT.multiply(new BigDecimal("0.1")))) == 0,
                    "only the fresh airborne threat spends the single 0.1D charge");
            h.assertTrue(embedded.inGround && embedded.getDeltaMovement().equals(staleVelocity), "grounded arrow remains untouched beside the fresh threat");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "highpower_native_player", timeoutTicks = 85)
    public static void actual_server_player_still_takes_nonprojectile_damage_while_holding_bracer(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer player = f.connectedPlayer();
        h.assertTrue(HighPowerCombat.enroll(player, 5), "real player receives an explicit exact ledger");
        AbilityStorage.get(player).selectCategory("electromaster"); AbilityStorage.get(player).setLevel(5);
        CompoundTag control = new CompoundTag(); control.putDouble("cp", 100000);
        player.getPersistentData().put("academy:electromaster_prototype", control);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ElectromasterPrototype.CONTROL.get()));
        player.setShiftKeyDown(true); player.startUsingItem(InteractionHand.MAIN_HAND);
        h.runAtTickTime(61, () -> {
            // The embedded fixture is admitted to PlayerList but has no socket connection
            // listener driving ServerPlayer.doTick. Run that real native player tick once;
            // do not reset the controller's guard or fabricate a protection result.
            player.doTick();
            h.assertTrue(HighPowerCombat.shieldActive(player), "actual Player.tick event recognizes held bracer; ready="
                    + ElectromasterPrototype.ready(player) + ", using=" + player.isUsingItem() + ", sneak=" + player.isShiftKeyDown());
            BigDecimal shield = HighPowerCombat.shield(player);
            h.assertTrue(player.hurt(player.damageSources().magic(), 3), "real nonprojectile magic damage passes after native spawn protection");
            exact(h, player, MAX_HP.subtract(BigDecimal.valueOf(3)), "held directional bracer does not absorb arbitrary magic/beam-like damage");
            h.assertTrue(HighPowerCombat.shield(player).compareTo(shield) == 0, "nonprojectile hit never spends the deflection pool");
            h.succeed();
        });
    }

    private static void exact(GameTestHelper h, LivingEntity entity, BigDecimal expected, String why) {
        h.assertTrue(new BigDecimal(HighPowerCombat.healthText(entity)).compareTo(expected) == 0,
                why + "; expected=" + expected.toPlainString() + ", actual=" + HighPowerCombat.healthText(entity));
    }

    private static final class Fixture {
        private final GameTestHelper h;
        private final List<Entity> entities = new ArrayList<>();
        private final List<ServerPlayer> players = new ArrayList<>();
        private final List<Object> hooks = new ArrayList<>();
        private final List<EmbeddedChannel> channels = new ArrayList<>();
        private boolean closed;
        Fixture(GameTestHelper h) {
            this.h = h;
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {}
                public void testPassed(GameTestInfo i, GameTestRunner r) { cleanup(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { cleanup(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { cleanup(); }
            });
        }
        ServerPlayer barePlayer() {
            FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[AC-HP]"));
            players.add(p); Vec3 at = h.absoluteVec(new Vec3(5.5, 2, 4.5)); p.moveTo(at.x, at.y, at.z, 0, 0);
            p.setYHeadRot(0); p.setNoGravity(true); p.getAbilities().instabuild = false; return p;
        }
        ServerPlayer connectedPlayer() {
            var level = h.getLevel(); var server = level.getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "ACHPProbe"), false);
            var player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
            players.add(player); var connection = new Connection(PacketFlow.SERVERBOUND);
            var channel = new EmbeddedChannel(connection); channels.add(channel); NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            h.assertTrue(server.getPlayerList().getPlayer(player.getUUID()) == player, "real PlayerList admission with official mock connection");
            Vec3 at = h.absoluteVec(new Vec3(5.5, 2, 4.5)); player.moveTo(at.x, at.y, at.z, 0, 0);
            player.setNoGravity(true); player.setGameMode(GameType.SURVIVAL); player.setYHeadRot(0);
            return player;
        }
        Arrow arrow(Vec3 at, Vec3 velocity) {
            Arrow arrow = new Arrow(EntityType.ARROW, h.getLevel()); arrow.setPos(at); arrow.setDeltaMovement(velocity); entity(arrow); return arrow;
        }
        void entity(Entity entity) { entity.setNoGravity(true); h.getLevel().addFreshEntity(entity); entities.add(entity); }
        ServerPlayer player() { ServerPlayer p = barePlayer(); var ability=cn.academy.port.AbilityStorage.get(p);ability.changeCategoryClassic("electromaster");ability.setLevel(5);h.assertTrue(HighPowerCombat.enroll(p, 5), "attacker enrollment: " + HighPowerCombat.unsupportedReason(p)); return p; }
        ServerPlayer readyCaster() {
            ServerPlayer p = barePlayer(); HighPowerCombat.enroll(p, 1);
            AbilityStorage.get(p).selectCategory("electromaster"); AbilityStorage.get(p).setLevel(1);
            CompoundTag control = new CompoundTag(); control.putDouble("cp", 200);
            p.getPersistentData().put("academy:electromaster_prototype", control); return p;
        }
        Villager target(double x, double y, double z) {
            Villager target = new Villager(EntityType.VILLAGER, h.getLevel());
            target.setNoAi(true); target.setNoGravity(true); target.setPos(h.absoluteVec(new Vec3(x, y, z)));
            h.getLevel().addFreshEntity(target); entities.add(target); return target;
        }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void cleanup() {
            if (closed) return; closed = true;
            hooks.forEach(NeoForge.EVENT_BUS::unregister);
            for (ServerPlayer player : players) {
                AbilityStorage.remove(player);
                var list = h.getLevel().getServer().getPlayerList();
                if (list.getPlayer(player.getUUID()) == player) list.remove(player); else player.discard();
            }
            entities.forEach(Entity::discard);
            channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }

    public static final class DamageGate {
        final LivingEntity target; boolean cancel, zero;
        DamageGate(LivingEntity target) { this.target = target; }
        @SubscribeEvent(priority = EventPriority.HIGHEST) public void incoming(LivingIncomingDamageEvent event) {
            if (event.getEntity() == target && cancel) event.setCanceled(true);
        }
        @SubscribeEvent(priority = EventPriority.HIGHEST) public void pre(LivingDamageEvent.Pre event) {
            if (event.getEntity() == target && zero) event.setNewDamage(0);
        }
    }
    public static final class ObserveDamage {
        final LivingEntity target; float original, damage, beforeAbsorption, absorption, armor, resistance, enchantments, cooldown, nativeBeforeArmor;
        ObserveDamage(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
            if (event.getEntity() == target) event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> {
                nativeBeforeArmor = container.getNewDamage(); return reduction;
            });
        }
        @SubscribeEvent(priority = EventPriority.LOWEST) public void pre(LivingDamageEvent.Pre event) {
            if (event.getEntity() == target) beforeAbsorption = event.getNewDamage();
        }
        @SubscribeEvent public void post(LivingDamageEvent.Post event) {
            if (event.getEntity() == target) {
                original = event.getOriginalDamage(); damage = event.getNewDamage();
                absorption = event.getReduction(DamageContainer.Reduction.ABSORPTION);
                armor = event.getReduction(DamageContainer.Reduction.ARMOR);
                resistance = event.getReduction(DamageContainer.Reduction.MOB_EFFECTS);
                enchantments = event.getReduction(DamageContainer.Reduction.ENCHANTMENTS);
                cooldown = event.getReduction(DamageContainer.Reduction.INVULNERABILITY);
            }
        }
    }
    public static final class NativeDamageRewrite {
        final LivingEntity target; Float finalPreDamage, incomingAmount; float incomingMultiplier = 1, absorptionMultiplier = 1, absorptionBonus;
        NativeDamageRewrite(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
            if (event.getEntity() == target) {
                if (incomingAmount != null) event.setAmount(incomingAmount);
                if (incomingMultiplier != 1) event.setAmount(event.getAmount() * incomingMultiplier);
                event.addReductionModifier(DamageContainer.Reduction.ABSORPTION,
                        (container, reduction) -> reduction * absorptionMultiplier + absorptionBonus);
            }
        }
        @SubscribeEvent(priority = EventPriority.LOWEST) public void pre(LivingDamageEvent.Pre event) {
            if (event.getEntity() == target && finalPreDamage != null) event.setNewDamage(finalPreDamage);
        }
    }
    public static final class ThrowOnceDamage {
        final LivingEntity target; final boolean reduction; boolean armed = true;
        ThrowOnceDamage(LivingEntity target, boolean reduction) { this.target = target; this.reduction = reduction; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
            if (event.getEntity() == target && reduction) event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, amount) -> {
                if (armed) { armed = false; throw new IllegalStateException("expected one-shot native reduction fixture"); }
                return amount;
            });
        }
        @SubscribeEvent public void pre(LivingDamageEvent.Pre event) {
            if (event.getEntity() == target && !reduction && armed) {
                armed = false; throw new IllegalStateException("expected one-shot native Pre fixture");
            }
        }
    }
    public static final class ShieldStageCounter {
        final LivingEntity target; int calls; boolean originallyBlocked; float shieldDamage;
        ShieldStageCounter(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void shield(LivingShieldBlockEvent event) {
            if (event.getEntity() == target) { calls++; originallyBlocked = event.getOriginalBlock(); shieldDamage = event.shieldDamage(); }
        }
    }
    public static final class ProtectionReadCounter {
        final ItemStack stack; int calls; Integer overrideLevel;
        ProtectionReadCounter(ItemStack stack) { this.stack = stack; }
        @SubscribeEvent public void enchantments(GetEnchantmentLevelEvent event) {
            if (event.getStack() == stack) {
                calls++;
                if (overrideLevel != null) event.getEnchantments().set(event.getHolder(Enchantments.PROTECTION).orElseThrow(), overrideLevel);
            }
        }
    }
    public static final class NestedExactHit {
        final ServerPlayer player; final LivingEntity target; boolean called, accepted;
        NestedExactHit(ServerPlayer player, LivingEntity target) { this.player = player; this.target = target; }
        @SubscribeEvent public void post(LivingDamageEvent.Post event) {
            if (event.getEntity() == target && !called) {
                called = true; accepted = HighPowerCombat.attack(player, SKILL, target, CombatAmounts.CAP);
            }
        }
    }
    public static final class SkillGate {
        final LivingEntity target; double multiplier;
        SkillGate(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void mutate(SkillAttackEvent event) { if (event.target == target) event.amount *= multiplier; }
    }
    public static final class DeathVeto {
        final LivingEntity target; DeathVeto(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void death(LivingDeathEvent event) { if (event.getEntity() == target) event.setCanceled(true); }
    }
    public static final class HealVeto {
        final LivingEntity target; HealVeto(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void heal(LivingHealEvent event) { if (event.getEntity() == target) event.setCanceled(true); }
    }
}

package cn.academy.port.highpower;

import cn.academy.port.skill.LightShield;
import cn.academy.port.skill.VecDeviation;
import cn.academy.port.skill.VecReflection;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.advancements.critereon.DamageSourcePredicate;
import net.minecraft.advancements.critereon.TagPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.item.enchantment.effects.AddValue;
import net.minecraft.world.level.storage.loot.predicates.DamageSourceCondition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHooks;

/**
 * Reviewed, deliberately closed runtime scope, not arbitrary event-bus introspection.
 * Approval means this exact vanilla/NeoForge/Academy build and built-in data, with the
 * existing Academy incoming writers inactive. External mods, data packs, unsupported enchantments,
 * code injection and replacement handlers need a separately implemented/reviewed adapter;
 * there is intentionally no string-based registration API that can claim compatibility.
 * Observed stage equality cannot detect invisible or compensating foreign hooks.
 */
public final class VanillaCombatContract {
    public static final String ID = "academy:vanilla_protection_cooldown_v2";
    private static final Map<String, String> MODS = Map.of("minecraft", "1.21.1", "neoforge", "21.1.252",
            "academy", "0.1.0-m41-highpower.0.81.5-city-a3.63-mech.1");
    private static final Set<String> PACKS = Set.of("vanilla", "mod_data", "mod/neoforge", "mod/academy", "bundle", "trade_rebalance");
    private static final Map<EntityType<?>, Class<?>> CLASSES = Map.ofEntries(
            Map.entry(EntityType.VILLAGER, Villager.class), Map.entry(EntityType.PIG, Pig.class),
            Map.entry(EntityType.COW, Cow.class), Map.entry(EntityType.SHEEP, Sheep.class),
            Map.entry(EntityType.CHICKEN, Chicken.class), Map.entry(EntityType.ZOMBIE, Zombie.class),
            Map.entry(EntityType.HUSK, Husk.class), Map.entry(EntityType.DROWNED, Drowned.class),
            Map.entry(EntityType.SKELETON, Skeleton.class), Map.entry(EntityType.STRAY, Stray.class),
            Map.entry(EntityType.WITHER_SKELETON, WitherSkeleton.class), Map.entry(EntityType.IRON_GOLEM, IronGolem.class),
            Map.entry(EntityType.CREEPER, Creeper.class));

    /** Reason text for an entity whose own override makes it immune to ordinary damage. */
    public static final String IMMUNE = "This entity is immune to damage";
    private VanillaCombatContract() {}

    public static boolean supportedClass(LivingEntity entity) {
        // This exact issued NPC cancels full-suit hits before entry; all admitted hits use GuardianEntity's native pipeline.
        if(entity.getClass()==cn.academy.port.city.defense.O5OperativeEntity.class&&entity.getType()==cn.academy.port.city.defense.CityDefenseContent.O5.get())return true;
        if(entity.getClass()==cn.academy.port.city.defense.GuardianEntity.class&&entity.getType()==cn.academy.port.city.defense.CityDefenseContent.GUARD.get())return true;
        if (entity.getClass() == ServerPlayer.class) return true;
        if (entity.getClass() == FakePlayer.class) return GameTestHooks.isGametestEnabled();
        // Real city espers use the same exact health/shield ledger. Attack consent is
        // still enforced by the city adapter, independently of this numeric contract.
        if (entity.getClass() == cn.academy.port.city.CityNpcEntity.class
                && entity.getType() == cn.academy.port.city.CityNpcContent.NPC.get())
            return ((cn.academy.port.city.CityNpcEntity)entity).esper() != cn.academy.port.city.CityEspers.Profile.NONE;
        // This specific ACA boss passes exact hits unchanged into the native IronGolem pipeline;
        // only ordinary player weapon damage is mapped before entry to its high-power pool.
        if (entity.getClass() == cn.academy.port.story.UrbanCalibrator.Boss.class
                && entity.getType() == cn.academy.port.story.UrbanCalibrator.BOSS.get()) return true;
        if (CompatMode.open()) return true;
        return CLASSES.get(entity.getType()) == entity.getClass();
    }

    /** Pure validation also lets native tests prove unknown runtime refusal without installing a mod. */
    public static Optional<String> runtimeReason(Map<String, String> mods, Set<String> packs) {
        var reviewed = new java.util.HashMap<>(mods);
        boolean visual = "4.7".equals(reviewed.get("geckolib"));
        if (visual) reviewed.remove("geckolib");
        // ACA Mech 0.4.3 registers its own entities, packets and recipes; it
        // has no server-wide damage hooks or vanilla data overrides.
        boolean mech = "0.4.3".equals(reviewed.get("acamech"));
        if (mech) reviewed.remove("acamech");
        boolean physics = PhysicsBundle.exact(MODS, reviewed);
        if (!MODS.equals(reviewed) && !physics) return Optional.of("This mod/version set has no approved high-power damage contract");
        if (!packs.contains("vanilla") || !packs.stream().allMatch(pack -> PACKS.contains(pack) || visual && pack.equals("mod/geckolib") || mech && pack.equals("mod/acamech") || physics && PhysicsBundle.PACKS.contains(pack))) {
            return Optional.of("Extra server data packs need an approved high-power damage contract");
        }
        return Optional.empty();
    }

    /** Stored basic Protection only; native gameplay-enchantment overrides are checked at their native stage. */
    public static int protectionPoints(LivingEntity entity) {
        int points = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            var stack = entity.getItemBySlot(slot);
            for (var entry : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
                var holder = entry.getKey();
                int level = entry.getIntValue();
                boolean armor = slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
                if (CompatMode.open() && !holder.is(Enchantments.PROTECTION)) continue;
                if (!holder.is(Enchantments.PROTECTION) || level < 1 || level > 4 || !armor || !vanillaProtection(entity, holder.value())) {
                    throw new IllegalArgumentException("Only reviewed vanilla Protection I-IV on armor is supported; other enchantments need an adapter");
                }
                points += level;
            }
        }
        return points;
    }

    private static boolean vanillaProtection(LivingEntity entity, Enchantment enchantment) {
        var resource = entity.getServer().getResourceManager().getResource(ResourceLocation.withDefaultNamespace("enchantment/protection.json"));
        if (resource.isEmpty() || !resource.get().sourcePackId().equals("vanilla")
                || !resource.get().knownPackInfo().equals(Optional.of(KnownPack.vanilla("core")))) return false;
        var expected = new ConditionalEffect<>(new AddValue(new LevelBasedValue.Linear(1F, 1F)),
                Optional.of(DamageSourceCondition.hasDamageSource(DamageSourcePredicate.Builder.damageType()
                        .tag(TagPredicate.isNot(DamageTypeTags.BYPASSES_INVULNERABILITY))).build()));
        return enchantment.definition().maxLevel() == 4
                && enchantment.definition().slots().equals(List.of(EquipmentSlotGroup.ARMOR))
                && enchantment.effects().keySet().equals(Set.of(EnchantmentEffectComponents.DAMAGE_PROTECTION))
                && enchantment.getEffects(EnchantmentEffectComponents.DAMAGE_PROTECTION).equals(List.of(expected));
    }

    public static Optional<String> unsupportedReason(LivingEntity entity) {
        if (!supportedClass(entity)) return Optional.of("This entity class has no approved high-power damage adapter");
        if (!(entity instanceof NativeCooldownAccess)) return Optional.of("The reviewed native cooldown seam is unavailable; ordinary combat remains active");
        var server = entity.getServer();
        if (server == null || entity.level().isClientSide) return Optional.of("High-power damage requires the server adapter");
        var mods = ModList.get().getMods().stream().collect(Collectors.toMap(info -> info.getModId(), info -> info.getVersion().toString()));
        var loadedPacks = server.getResourceManager().listPacks().toList();
        var packs = loadedPacks.stream().map(pack -> pack.packId()).collect(Collectors.toSet());
        var runtime = CompatMode.open() ? Optional.<String>empty() : runtimeReason(mods, packs);
        if (runtime.isPresent()) return runtime;
        // GameTest enables both official feature packs automatically. Their reviewed
        // bundle/trade resources do not change damage definitions or Protection effects.
        for (var pack : CompatMode.open() ? List.<net.minecraft.server.packs.PackResources>of() : loadedPacks) {
            if ((pack.packId().equals("bundle") || pack.packId().equals("trade_rebalance"))
                    && !pack.knownPackInfo().equals(Optional.of(KnownPack.vanilla(pack.packId())))) {
                return Optional.of("A feature pack lacks the approved vanilla provenance");
            }
        }
        var sourceData = server.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("damage_type/player_attack.json"));
        if (!CompatMode.open() && (sourceData.isEmpty() || !sourceData.get().sourcePackId().equals("vanilla"))) {
            return Optional.of("The player-attack damage definition is outside the approved vanilla data contract");
        }
        try { protectionPoints(entity); }
        catch (IllegalArgumentException unsupported) { return Optional.of(unsupported.getMessage()); }
        if (!CompatMode.open()) for (var stack : entity.getAllSlots()) {
            if (!stack.isEmpty()) {
                String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
                if (!namespace.equals("minecraft") && !namespace.equals("academy")) return Optional.of("Unsupported equipment damage behavior");
            }
        }
        if (!CompatMode.open()) for (var effect : entity.getActiveEffects()) {
            if (!BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).getNamespace().equals("minecraft")) {
                return Optional.of("Non-vanilla effects need an explicit high-power damage adapter");
            }
        }
        double armor = entity.getAttributeValue(Attributes.ARMOR), toughness = entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        if (CompatMode.open() ? !Double.isFinite(armor) || !Double.isFinite(toughness) || !Float.isFinite(entity.getAbsorptionAmount())
                : !Double.isFinite(armor) || armor < 0 || armor > 30 || !Double.isFinite(toughness) || toughness < 0 || toughness > 20
                || !Float.isFinite(entity.getAbsorptionAmount()) || entity.getAbsorptionAmount() < 0 || entity.getAbsorptionAmount() > 2048) {
            return Optional.of("Defense attributes exceed the approved vanilla bounds");
        }
        // Modded entities may be immune through isInvulnerableTo overrides (wisps, statues): never report a hit that cannot land.
        if (CompatMode.open() && !(entity instanceof ServerPlayer) && entity.isInvulnerableTo(entity.damageSources().generic())) {
            return Optional.of(IMMUNE);
        }
        if (entity.getPersistentData().getInt("academy:md_mark_ticks") > 0) {
            return Optional.of("Active radiation marks need an explicit high-power damage adapter");
        }
        if (entity instanceof ServerPlayer player) {
            if (LightShield.active(player) || VecDeviation.active(player) || VecReflection.active(player)) {
                return Optional.of("Active legacy damage defenses need an explicit high-power adapter");
            }
            // These two classic handlers affect ANY player, not only the hold owner.
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (LightShield.active(other) || VecDeviation.active(other)) {
                    return Optional.of("A server-wide legacy defense is active; high-power player damage is unsupported");
                }
            }
        }
        return Optional.empty();
    }
}

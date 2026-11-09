package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.highpower.CompatMode;
import cn.academy.port.highpower.HighPowerCombat;
import com.mojang.authlib.GameProfile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Open-compatibility sweep against real third-party mods. Every registered living entity, weapon, armor piece and charm of the
 * listed namespaces is enrolled/equipped and hit with an exact high-power hit. A mod that is not installed is skipped.
 * Invariant per subject: the contract does not refuse it, and the hit either debits the ledger by exactly the raw amount or
 * leaves the ledger untouched (a refused transaction falls back to ordinary combat); never a partial change or an exception.
 */
@GameTestHolder("academy_mod_compat")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerModCompatRuntimeTests {
    private static final String TEMPLATE = "mod_compat_empty", BATCH = "mod_compat";
    private static final BigDecimal HIT = cn.academy.port.highpower.core.HighPowerLevel.LV1.damage();
    private static final BigDecimal CAP = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth();
    private HighPowerModCompatRuntimeTests() {}

    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int dimension : new int[] {48, 16, 48}) size.add(IntTag.valueOf(dimension));
        for (int axis = 0; axis < 3; axis++) position.add(IntTag.valueOf(0));
        cell.put("pos", position); cell.putInt("state", 0); blocks.add(cell); air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_mod_compat", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 2400)
    public static void mowzies_mobs(GameTestHelper h) { entities(h, "mowziesmobs"); }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 2400)
    public static void cataclysm_bosses(GameTestHelper h) { entities(h, "cataclysm"); }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 2400)
    public static void irons_spellbooks_mobs(GameTestHelper h) { entities(h, "irons_spellbooks"); }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 2400)
    public static void irons_spellbooks_gear(GameTestHelper h) { gear(h, "irons_spellbooks"); }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 2400)
    public static void cataclysm_gear(GameTestHelper h) { gear(h, "cataclysm"); }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 2400)
    public static void mowzies_gear(GameTestHelper h) { gear(h, "mowziesmobs"); }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 2400)
    public static void artifacts_charms(GameTestHelper h) { gear(h, "artifacts"); }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 2400)
    public static void apotheosis_gear(GameTestHelper h) { gear(h, "apotheosis"); }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void aim_maxhp_learns_every_high_power_skill_of_each_category(GameTestHelper h) {
        for (String category : new String[] {"electromaster", "meltdowner", "teleporter", "vecmanip"}) {
            ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[HP-Aim-" + category + "]"));
            try {
                var server = h.getLevel().getServer();
                var source = p.createCommandSourceStack().withPermission(4);
                server.getCommands().performPrefixedCommand(source, "aim cheats_on");
                server.getCommands().performPrefixedCommand(source, "aim cat " + category);
                server.getCommands().performPrefixedCommand(source, "aim maxhp");
                var state = AbilityStorage.get(p);
                long expected = cn.academy.port.highpower.HighPowerSkills.ALL.stream().filter(s -> s.category().equals(category)).count();
                long learned = cn.academy.port.highpower.HighPowerSkills.ALL.stream().filter(s -> s.category().equals(category) && state.learned(s.id())).count();
                h.assertTrue(state.level == 5, category + " level 5, was " + state.level);
                h.assertTrue(expected > 0 && learned == expected, category + " learned " + learned + "/" + expected);
            } finally { AbilityStorage.remove(p); p.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void open_compatibility_keeps_the_ledger_active_beside_foreign_enchantments(GameTestHelper h) {
        Villager target = new Villager(EntityType.VILLAGER, h.getLevel());
        target.setNoAi(true); target.setNoGravity(true); target.setPos(h.absoluteVec(new Vec3(13.5, 2, 8.5)));
        h.getLevel().addFreshEntity(target);
        try {
            ItemStack helmet = new ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET);
            helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROJECTILE_PROTECTION), 4);
            target.setItemSlot(EquipmentSlot.HEAD, helmet);
            CompatMode.override(true);
            h.assertTrue(HighPowerCombat.enroll(target, 5), "open enrollment beside Projectile Protection: " + HighPowerCombat.unsupportedReason(target));
            CompatMode.override(false);
            h.assertTrue(HighPowerCombat.unsupportedReason(target).isPresent(), "the closed contract still refuses an enchantment outside vanilla Protection");
            CompatMode.override(true);
            h.assertTrue(HighPowerCombat.unsupportedReason(target).isEmpty(), "open compatibility keeps the exact ledger active beside that enchantment");
        } finally { CompatMode.override(null); target.discard(); }
        h.succeed();
    }

    /** Stand-in for a boss limiter: clamps every ordinary hit like Cataclysm's DamageCap, lets BYPASSES_INVULNERABILITY through. */
    private static class CappedZombie extends net.minecraft.world.entity.monster.Zombie {
        /** 0: per-hit cap, 1: refuses ordinary hits like a range limit, 2: refuses everything (a genuinely invulnerable entity). */
        final int mode;
        CappedZombie(net.minecraft.world.level.Level level, int mode) { super(EntityType.ZOMBIE, level); this.mode = mode; }
        @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
            if (mode == 2) return false;
            if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
            if (mode == 1) return false;
            return super.hurt(source, Math.min(amount, 1F));
        }
    }

    private static CappedZombie capped(GameTestHelper h, int mode, double x) {
        CappedZombie z = new CappedZombie(h.getLevel(), mode);
        z.setNoAi(true); z.setNoGravity(true); z.setPos(h.absoluteVec(new Vec3(x, 2, 8.5)));
        h.getLevel().addFreshEntity(z); return z;
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void skill_damage_breaks_a_per_hit_damage_cap(GameTestHelper h) {
        ServerPlayer caster = caster(h, new ArrayList<>());
        CappedZombie classic = capped(h, 0, 9.5), exact = capped(h, 0, 17.5), rejecting = capped(h, 1, 25.5), partial = capped(h, 0, 33.5), wall = capped(h, 2, 49.5);
        try {
            CompatMode.override(true);
            float full = classic.getHealth();
            h.assertTrue(cn.academy.port.AbilityDamage.attack(caster, "electromaster.arc_gen", classic, 5), "classic hit accepted");
            h.assertTrue(classic.getHealth() < full - 3.9F && classic.isAlive(), "a small hit is not clamped any more: " + classic.getHealth());
            classic.invulnerableTime = 0; // a later hit, outside the vanilla hurt cooldown
            cn.academy.port.AbilityDamage.attack(caster, "electromaster.arc_gen", classic, 100000);
            h.assertTrue(classic.isDeadOrDying() || !classic.isAlive(), "classic lethal hit defeats the capped target, health=" + classic.getHealth());
            h.assertTrue(HighPowerCombat.attackIronSandLance(caster, exact, HIT, exact.position().add(0, .8, -2)) || !exact.isAlive(), "exact path accepted or target gone");
            h.assertTrue(exact.isDeadOrDying() || !exact.isAlive(), "exact lethal hit defeats the capped target, health=" + exact.getHealth());
            cn.academy.port.AbilityDamage.attack(caster, "electromaster.arc_gen", rejecting, 100000);
            h.assertTrue(rejecting.isDeadOrDying() || !rejecting.isAlive(), "a limiter that refuses the hit before vanilla runs (range/DPS lockout) is broken");
            float wallHealth = wall.getHealth();
            cn.academy.port.AbilityDamage.attack(caster, "electromaster.arc_gen", wall, 100000);
            h.assertTrue(wall.isAlive() && wall.getHealth() == wallHealth, "an entity that refuses every hit, bypass included, is treated as invulnerable and left alone");
            var plain = new net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE, h.getLevel());
            plain.setNoAi(true); plain.setNoGravity(true); plain.setPos(h.absoluteVec(new Vec3(41.5, 2, 8.5))); h.getLevel().addFreshEntity(plain);
            try {
                cn.academy.port.AbilityDamage.attack(caster, "electromaster.arc_gen", plain, 5);
                float afterFirst = plain.getHealth();
                h.assertFalse(cn.academy.port.AbilityDamage.attack(caster, "electromaster.arc_gen", plain, 3), "vanilla hurt cooldown refuses a weaker follow-up");
                h.assertTrue(plain.getHealth() == afterFirst, "a refusal by vanilla itself (cooldown, shield, events, totems) is never overridden");
            } finally { plain.discard(); }
            partial.setHealth(partial.getMaxHealth());
            float before = partial.getHealth();
            cn.academy.port.AbilityDamage.attack(caster, "electromaster.arc_gen", partial, 12);
            float vanilla = cn.academy.port.highpower.CapBreak.vanillaExpected(partial, new cn.academy.port.AbilityDamage.SkillDamageSource(caster, "electromaster.arc_gen", false), 12);
            h.assertTrue(Math.abs((before - partial.getHealth()) - vanilla) < .01F && vanilla > 1.5F,
                    "a partial cap is topped up to exactly the vanilla armor result, lost=" + (before - partial.getHealth()) + ", vanilla=" + vanilla);
        } finally {
            CompatMode.override(null);
            for (var z : List.of(classic, exact, rejecting, partial, wall)) z.discard();
            AbilityStorage.remove(caster); caster.discard();
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void cataclysm_ignis_is_defeated_by_skill_damage_despite_its_damage_cap(GameTestHelper h) {
        EntityType<?> ignis = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath("cataclysm", "ignis")).orElse(null);
        if (ignis == null) { h.succeed(); return; } // Cataclysm is not installed
        ServerPlayer caster = caster(h, new ArrayList<>());
        Entity made = ignis.create(h.getLevel());
        try {
            CompatMode.override(true);
            LivingEntity boss = (LivingEntity) made;
            if (boss instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
            boss.setNoGravity(true); boss.setPos(h.absoluteVec(new Vec3(13.5, 2, 12.5)));
            h.getLevel().addFreshEntity(boss);
            float max = boss.getMaxHealth(), hp = boss.getHealth();
            cn.academy.port.AbilityDamage.attack(caster, "electromaster.arc_gen", boss, max * 0.5F);
            float lost = hp - boss.getHealth();
            h.assertTrue(lost > max * 0.4F || !boss.isAlive(), "a half-health hit lands in full instead of being clamped to the damage cap: lost=" + lost + " of " + max);
            boss.invulnerableTime = 0; // a later hit, outside the vanilla hurt cooldown
            cn.academy.port.AbilityDamage.attack(caster, "electromaster.arc_gen", boss, max * 10);
            h.assertTrue(boss.isDeadOrDying() || !boss.isAlive(), "a lethal hit defeats Ignis, health=" + boss.getHealth());
        } finally {
            CompatMode.override(null); made.discard(); AbilityStorage.remove(caster); caster.discard();
        }
        h.succeed();
    }

    private static ServerPlayer caster(GameTestHelper h, List<Entity> spawned) {
        ServerPlayer caster = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[HP-ModCompat]"));
        caster.setPos(h.absoluteVec(new Vec3(5.5, 2, 4.5))); caster.setNoGravity(true);
        var ability = AbilityStorage.get(caster); ability.changeCategoryClassic("electromaster"); ability.setLevel(5);
        h.assertTrue(HighPowerCombat.enroll(caster, 5), "caster enrollment: " + HighPowerCombat.unsupportedReason(caster));
        return caster;
    }

    private static String ledger(LivingEntity target) { return HighPowerCombat.healthText(target); }

    /** One exact hit; returns a defect string or null. */
    private static String hit(ServerPlayer caster, LivingEntity target, String label) {
        Optional<String> reason = HighPowerCombat.unsupportedReason(target);
        if (reason.isPresent()) return reason.get().equals(cn.academy.port.highpower.VanillaCombatContract.IMMUNE) ? null : label + ": refused by contract: " + reason.get();
        if (!HighPowerCombat.enroll(target, 5)) return label + ": enrollment failed";
        BigDecimal before = new BigDecimal(ledger(target));
        boolean accepted;
        try { accepted = HighPowerCombat.attackIronSandLance(caster, target, HIT, target.position().add(0, .8, -2)); }
        catch (RuntimeException failure) { return label + ": exception " + failure; }
        if (!target.isAlive()) return null; // the entity removed itself on being hit (wisp, frozen statue): no ledger left to compare
        BigDecimal after = new BigDecimal(ledger(target));
        if (accepted && after.compareTo(before.subtract(HIT)) != 0) {
            var source = target.damageSources().generic();
            return label + ": accepted but ledger " + before + " -> " + after + " (invulnerableTo=" + target.isInvulnerableTo(source)
                    + ", invulnerableFlag=" + target.isInvulnerable() + ", dead=" + target.isDeadOrDying() + ", alive=" + target.isAlive()
                    + ", class=" + target.getClass().getName() + ")";
        }
        if (!accepted && after.compareTo(before) != 0) return label + ": refused but ledger changed " + before + " -> " + after;
        return null;
    }

    private static void entities(GameTestHelper h, String namespace) {
        List<String> defects = new ArrayList<>(), seen = new ArrayList<>();
        List<Entity> spawned = new ArrayList<>();
        ServerPlayer caster = caster(h, spawned);
        int count = 0;
        try {
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                var key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (!key.getNamespace().equals(namespace)) continue;
                Entity made;
                try { made = type.create(h.getLevel()); } catch (RuntimeException unsupported) { continue; }
                if (!(made instanceof LivingEntity living)) continue;
                try {
                    if (living instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                    living.setNoGravity(true); living.setPos(h.absoluteVec(new Vec3(5.5 + (count % 6) * 6, 2, 12.5 + (count / 6 % 3) * 8)));
                    if (!h.getLevel().addFreshEntity(living)) continue;
                    spawned.add(living); count++;
                    String defect = hit(caster, living, key.toString());
                    seen.add(key.getPath() + (defect == null ? "=ok" : "=DEFECT"));
                    if (defect != null) defects.add(defect);
                } catch (RuntimeException failure) { defects.add(key + ": " + failure); }
                finally { living.discard(); }
            }
        } finally { AbilityStorage.remove(caster); caster.discard(); }
        System.out.println("MOD_COMPAT entities " + namespace + " count=" + count + " " + seen);
        if (count == 0) { h.succeed(); return; } // mod not installed
        h.assertTrue(defects.isEmpty(), "entity defects: " + defects);
        h.succeed();
    }

    private static void gear(GameTestHelper h, String namespace) {
        List<String> defects = new ArrayList<>();
        ServerPlayer caster = caster(h, new ArrayList<>());
        int count = 0, armorCount = 0;
        try {
            for (var item : BuiltInRegistries.ITEM) {
                var key = BuiltInRegistries.ITEM.getKey(item);
                if (!key.getNamespace().equals(namespace)) continue;
                ItemStack stack;
                try { stack = new ItemStack(item); } catch (RuntimeException unsupported) { continue; }
                if (stack.isEmpty()) continue;
                Villager target = new Villager(EntityType.VILLAGER, h.getLevel());
                try {
                    target.setNoAi(true); target.setNoGravity(true); target.setPos(h.absoluteVec(new Vec3(13.5, 2, 8.5)));
                    h.getLevel().addFreshEntity(target);
                    EquipmentSlot slot = target.getEquipmentSlotForItem(stack);
                    target.setItemSlot(slot, stack.copy());
                    if (slot != EquipmentSlot.MAINHAND) armorCount++;
                    // Charms are also tried as a second held stack: Curios-style items are not equipment-slot items.
                    if (slot == EquipmentSlot.MAINHAND) target.setItemSlot(EquipmentSlot.OFFHAND, stack.copy());
                    count++;
                    String defect = hit(caster, target, key.toString() + "@" + slot.getName());
                    if (defect != null) defects.add(defect);
                } catch (RuntimeException failure) { defects.add(key + ": " + failure); }
                finally { target.discard(); }
            }
        } finally { AbilityStorage.remove(caster); caster.discard(); }
        System.out.println("MOD_COMPAT gear " + namespace + " items=" + count + " armorSlots=" + armorCount + " defects=" + defects.size());
        if (count == 0) { h.succeed(); return; }
        h.assertTrue(defects.isEmpty(), "gear defects (first 15): " + defects.subList(0, Math.min(15, defects.size())));
        h.succeed();
    }
}

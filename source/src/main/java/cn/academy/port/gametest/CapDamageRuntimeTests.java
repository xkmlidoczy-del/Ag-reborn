package cn.academy.port.gametest;

import cn.academy.port.AbilityDamage;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.api.NativeCombatDamage;
import cn.academy.port.city.CityNpcContent;
import cn.academy.port.highpower.CapBreak;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.titan.TitanBlocks;
import cn.academy.port.titan.TitanCombat;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("academy_cap_break")
@EventBusSubscriber(modid = "academy")
@PrefixGameTestTemplate(false)
public final class CapDamageRuntimeTests {
    private static final String BATCH = "cap_break";
    private static final String TEMPLATE = "empty";

    @SubscribeEvent public static void template(ServerStartedEvent event) {
        if (!GameTestHooks.isGametestEnabled()) return;
        var world = event.getServer().overworld();
        world.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_cap_break", TEMPLATE)).fillFromWorld(world,
                new BlockPos(4, world.getMaxBuildHeight() - 30, 4), new Vec3i(32, 10, 32), false, null);
    }

    private static final class Limited extends Zombie {
        float cap = 1;
        int calls;
        float requested;
        boolean reject;
        Limited(ServerLevel level) { super(EntityType.ZOMBIE, level); }
        @Override public boolean hurt(DamageSource source, float amount) {
            calls++;
            requested = amount;
            return !reject && super.hurt(source, Math.min(cap, amount));
        }
    }

    private static Limited limited(GameTestHelper h, int x, int z, float max) {
        var target = new Limited(h.getLevel());
        target.moveTo(h.absoluteVec(new Vec3(x, 3, z)));
        target.setNoAi(true);
        target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        target.setHealth(max);
        h.getLevel().addFreshEntity(target);
        return target;
    }

    private static FakePlayer caster(GameTestHelper h) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "CapProbe"));
        player.moveTo(h.absoluteVec(new Vec3(8, 3, 8)).x, h.absoluteVec(new Vec3(8, 3, 8)).y,
                h.absoluteVec(new Vec3(8, 3, 8)).z, 0, 0);
        return player;
    }

    private static void fresh(LivingEntity e) { e.setHealth(e.getMaxHealth()); e.invulnerableTime = 0; }
    private static float loss(LivingEntity e) { return e.getMaxHealth() - e.getHealth(); }
    private static void close(GameTestHelper h, double exp, double got, double tolerance, String label) {
        h.assertTrue(Math.abs(exp - got) <= tolerance, label + " expected=" + exp + " actual=" + got);
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void positiveCapAndNativeRules(GameTestHelper h) {
        var target = limited(h, 10, 10, 20);
        var plain = h.spawn(EntityType.ZOMBIE, 14, 3, 12);
        var source = target.damageSources().mobAttack(plain);
        target.hurt(source, 5);
        float limitedLoss = loss(target);
        plain.hurt(source, 5);
        float expected = loss(plain);
        fresh(target);
        h.assertTrue(NativeCombatDamage.hurt(target, source, 5), "adapted hit admitted");
        close(h, expected, loss(target), .0001, "normal armor only");
        h.assertTrue(!NativeCombatDamage.hurt(target, source, 3), "vanilla cooldown still rejects a weaker second hit");
        close(h, expected, loss(target), .0001, "one HP debit");
        h.assertTrue(target.calls == 3, "one invocation per request");
        fresh(target);
        AcademyConfig.BREAK_DAMAGE_CAPS.set(false);
        try {
            NativeCombatDamage.hurt(target, source, 5);
            close(h, limitedLoss, loss(target), .0001, "disabled setting");
        } finally { AcademyConfig.BREAK_DAMAGE_CAPS.set(true); }
        fresh(target);
        target.cap = 0;
        NativeCombatDamage.hurt(target, source, 5);
        close(h, 0, loss(target), .0001, "zero stays zero");
        fresh(target);
        target.cap = 1; target.reject = true;
        h.assertTrue(!NativeCombatDamage.hurt(target, source, 5), "hard rejection stays final");
        close(h, 0, loss(target), .0001, "hard rejection life");
        target.reject = false;
        target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        h.assertTrue(NativeCombatDamage.hurt(target, source, 1000) && target.getHealth() == 1
                && target.getOffhandItem().isEmpty(), "totem resolves natively after a restored lethal hit");
        System.out.printf("CAP_QA fixture raw=5 cappedLoss=%.3f nativeRestoredLoss=%.3f%n", limitedLoss, expected);
        h.succeed();
    }

    public static final class Observer {
        final Limited target, nested;
        float incoming;
        int references;
        boolean cancelled = true;
        Observer(Limited target, Limited nested) { this.target = target; this.nested = nested; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) {
            if (e.getEntity() != target) return;
            references++;
            incoming = e.getContainer().getOriginalDamage();
            if (cancelled) { e.setCanceled(true); return; }
            NativeCombatDamage.hurt(nested, e.getSource(), 7);
            e.setAmount(4);
        }
        @SubscribeEvent public void pre(LivingDamageEvent.Pre e) {
            if (e.getEntity() == target) e.setNewDamage(2);
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void eventCancellationAndNestedHits(GameTestHelper h) {
        var target = limited(h, 11, 11, 40);
        var nested = limited(h, 15, 11, 40);
        var source = target.damageSources().magic();
        var probe = new Observer(target, nested);
        NeoForge.EVENT_BUS.register(probe);
        try {
            h.assertTrue(!NativeCombatDamage.hurt(target, source, 10), "cancelled hit returns false");
            close(h, 0, loss(target), .0001, "cancelled hit spends no HP");
            probe.cancelled = false; fresh(target);
            h.assertTrue(NativeCombatDamage.hurt(target, source, 10), "accepted rewrite");
            close(h, 10, probe.incoming, .0001, "even original damage is corrected before observers");
            close(h, 2, loss(target), .0001, "final NeoForge mutation is authoritative");
            close(h, CapBreak.vanillaExpected(nested, source, 7), loss(nested), .0001, "nested transaction has own request");
            h.assertTrue(probe.references == 2 && target.calls == 2 && nested.calls == 1, "no second attack or leaked scope");
        } finally { NeoForge.EVENT_BUS.unregister(probe); }
        h.succeed();
    }

    public static final class Receipts {
        private final Limited target;
        private String source;
        Receipts(Limited target) { this.target = target; }
        @SubscribeEvent public void afterDamage(LivingDamageEvent.Post event) {
            if (event.getEntity() != target) return;
            var reduction = net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ARMOR;
            System.out.printf("CAP_QA source=%s request=%.3f restored=%.3f armor=%.3f final=%.3f hp_loss=%.3f%n",
                    source, target.requested, event.getOriginalDamage(), event.getReduction(reduction), event.getNewDamage(), loss(target));
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void productionSources(GameTestHelper h) throws Exception {
        var player = caster(h);
        var target = limited(h, 8, 12, 20_000_000);
        var receipts = new Receipts(target);
        NeoForge.EVENT_BUS.register(receipts);
        try {
            var state = AbilityStorage.get(player);
            state.changeCategoryClassic("electromaster");
            state.setLevel(1);
            state.setActivateState(true);
            h.assertTrue(HighPowerCombat.enroll(player, 1), "LV1 enrollment");
            receipts.source = "academy_lv1";
            h.assertTrue(AbilityDamage.attack(player, "electromaster.arc_gen", target, 100), "LV1 classic skill hits");
            h.assertTrue(loss(target) > 4 && target.calls == 1, "LV1 cap break loss=" + loss(target) + " hits=" + target.calls);
            fresh(target);
            h.assertTrue(HighPowerCombat.commitAbilityRank(player, 5, () -> state.setLevel(5)), "LV5 native rank");
            receipts.source = "academy_lv5";
            h.assertTrue(HighPowerCombat.attack(player, "electromaster.arc_gen", target, BigDecimal.valueOf(500_000)), "LV5 skill hits");
            h.assertTrue(loss(target) >= 460_000 && target.calls == 2, "LV5 cap break without pool scaling loss=" + loss(target) + " hits=" + target.calls);
            fresh(target);
            var npc = h.spawn(CityNpcContent.NPC.get(), 5, 3, 8);
            receipts.source = "city_npc";
            h.assertTrue(AbilityDamage.attackEntity(npc, "electromaster.arc_gen", target, 34, false), "city NPC damage enters wrapper");
            h.assertTrue(loss(target) >= 30 && target.calls == 3, "NPC cap break loss=" + loss(target) + " hits=" + target.calls);
            fresh(target);
            var titan = h.spawn(TitanBlocks.TYPES.get("myco_frog").get(), 6, 3, 8);
            receipts.source = "titan";
            h.assertTrue(TitanCombat.limbHit(h.getLevel(), titan, target), "titan native hit");
            h.assertTrue(loss(target) > 10 && target.calls == 4, "titan cap break");
            var cosmic = Class.forName("cn.aca.omnitrix.Abilities").getDeclaredMethod("cosmicAnnihilate",
                    ServerPlayer.class, LivingEntity.class, float.class, Vec3.class, double.class);
            cosmic.setAccessible(true);
            fresh(target);
            receipts.source = "alien_x";
            h.assertTrue((boolean)cosmic.invoke(null, player, target, 5000F, Vec3.ZERO, 0D), "Alien X attack");
            close(h, 5000, loss(target), .0001, "Alien X pierces armor and single hit cap");
            var cleave = Class.forName("cn.aca.omnitrix.AscalonService").getMethod("fundamentalCleave",
                    ServerPlayer.class, LivingEntity.class, float.class, boolean.class, Vec3.class, double.class);
            fresh(target);
            receipts.source = "ascalon";
            h.assertTrue((boolean)cleave.invoke(null, player, target, 6500F, true, Vec3.ZERO, 0D), "Ascalon execution attack");
            close(h, 6500, loss(target), .0001, "Ascalon single hit");
            if (ModList.get().isLoaded("aca_infinity")) {
                var stone = Class.forName("cn.aca.infinity.StoneAbilities").getDeclaredMethod("castPowerPunch",
                        ServerPlayer.class, ServerLevel.class, long.class);
                stone.setAccessible(true);
                fresh(target);
                receipts.source = "power_stone";
                stone.invoke(null, player, h.getLevel(), h.getLevel().getGameTime());
                h.assertTrue(loss(target) > 5_000_000 && target.calls == 7, "real Power Stone cast resolved once through optional bridge, loss=" + loss(target));
                target.invulnerableTime = 0; fresh(target);
                var cancel = new Observer(target, limited(h, 25, 25, 20));
                NeoForge.EVENT_BUS.register(cancel);
                try {
                    stone.invoke(null, player, h.getLevel(), h.getLevel().getGameTime());
                    h.assertTrue(cancel.references == 1 && target.calls == 8 && loss(target) == 0, "Infinity bridge never retries a veto");
                } finally { NeoForge.EVENT_BUS.unregister(cancel); }
            }
            System.out.println("CAP_QA production_sources level1 level5 npc titan alien_x ascalon infinity=" + ModList.get().isLoaded("aca_infinity"));
        } finally {
            NeoForge.EVENT_BUS.unregister(receipts);
            AbilityStorage.remove(player);
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void realCataclysmBosses(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded("cataclysm")) { h.succeed(); return; }
        var attacker = h.spawn(EntityType.HUSK, 10, 3, 6);
        for (String id : new String[]{"ignis", "ancient_remnant"}) {
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse("cataclysm:" + id)).orElseThrow();
            var boss = (Mob)type.create(h.getLevel());
            boss.setNoAi(true); boss.setNoGravity(true);
            Vec3 pos = h.absoluteVec(new Vec3(10, 3, 10));
            boss.moveTo(pos);
            boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
            fresh(boss);
            h.getLevel().addFreshEntity(boss);
            float cap = ((Number)boss.getClass().getMethod("DamageCap").invoke(boss)).floatValue();
            double range = ((Number)boss.getClass().getMethod("RangeLimit").invoke(boss)).doubleValue();
            var bucket = boss.getClass().getSuperclass().getDeclaredField("damageBucket"); bucket.setAccessible(true);
            var source = boss.damageSources().mobAttack(attacker);
            float raw = Math.max(cap * 3, 75);
            bucket.setFloat(boss, cap);
            boss.hurt(source, raw);
            float ordinary = loss(boss);
            h.assertTrue(ordinary < 1, id + " ordinary hit swallowed by full bucket, got " + ordinary);
            fresh(boss); bucket.setFloat(boss, cap);
            h.assertTrue(NativeCombatDamage.hurt(boss, source, raw), id + " scoped hit passes bucket");
            float near = loss(boss);
            close(h, CapBreak.vanillaExpected(boss, source, raw), near, .01, id + " one full close-range hit");
            close(h, cap, bucket.getFloat(boss), .0001, id + " existing bucket isolated");
            fresh(boss);
            attacker.moveTo(pos.add(0, 0, range * 1.25));
            h.assertTrue(NativeCombatDamage.hurt(boss, source, raw), id + " softened range still admitted");
            float far = loss(boss);
            close(h, CapBreak.vanillaExpected(boss, source, raw * .5F), far, .02, id + " positive distance reduction preserved");
            fresh(boss);
            attacker.moveTo(pos.add(0, 0, range * 2));
            h.assertTrue(!NativeCombatDamage.hurt(boss, source, raw) && loss(boss) == 0, id + " hard distance refusal");
            attacker.moveTo(pos.add(0, 0, -4));
            if (id.equals("ignis")) {
                Object ultimate = boss.getClass().getField("ULTIMATE_ATTACK").get(null);
                Method setter = boss.getClass().getMethod("setAnimation", ultimate.getClass());
                setter.invoke(boss, ultimate);
                h.assertTrue(NativeCombatDamage.hurt(boss, source, raw), "Ignis ultimate damage is reduced but admitted");
                close(h, CapBreak.vanillaExpected(boss, source, raw * .5F), loss(boss), .02, "Ignis ultimate retains its half multiplier");
                fresh(boss);
                setter.invoke(boss, boss.getClass().getField("PHASE_2").get(null));
                h.assertTrue(!NativeCombatDamage.hurt(boss, source, raw) && loss(boss) == 0, "Ignis scripted phase immunity");
            }
            System.out.printf("CAP_QA cataclysm:%s cap=%.3f raw=%.3f ordinary=%.3f near=%.3f softened=%.3f range=%.3f%n", id, cap, raw, ordinary, near, far, range);
            boss.discard();
        }
        h.succeed();
    }
}

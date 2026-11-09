package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.api.SkillExpAddedEvent;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * NEW252 stage 2: optional persisted children follow real, already-shipped resource writers.
 * Enrollment, learned skills and asymmetric initial balances are declared native fixtures.
 * NEW252 is initialized explicitly through its storage helper; no runtime252 registration,
 * defense controller, physical-gap gameplay acceptance or automatic tick hook is claimed.
 */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerZeroGapResourceIntegrationTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_zero_gap_resources";
    private HighPowerZeroGapResourceIntegrationTests() {}

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void ordinary_spend_relearn_and_zero_delta_preserve_asymmetric_children(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.fake(2, false);
        f.asymmetric(p); var before = armor(p);
        h.assertTrue(HighPowerCombat.spendShield(p, n("5")), "real public ordinary shield expense");
        var after = conserved(h, p, "ordinary spend");
        eq(h, after.base().shield(), before.base().shield().subtract(n("5")), "one actual base debit");
        eq(h, after.plate(2).baseCurrent(), "0", "ordinary B1 spend exhausts its first child");
        eq(h, after.plate(3).baseCurrent(), "19", "only remaining B1 delta reaches its second child");
        for (int i = 0; i < 6; i++) {
            eq(h, after.plate(i).reserveCurrent(), before.plate(i).reserveCurrent(), "ordinary spend leaves reserve child " + i);
            if (i != 2 && i != 3) eq(h, after.plate(i).baseCurrent(), before.plate(i).baseCurrent(), "unpaid parent child " + i);
        }
        var state = AbilityStorage.get(p); state.category = HighPowerCategories.MELTDOWNER;
        state.unlearn(ScatterPlates.ID); state.learn(ScatterPlates.ID); ScatterPlateStorage.ensure(p);
        state.category = HighPowerCategories.ELECTROMASTER;
        h.assertTrue(HighPowerCombat.spendShield(p, BigDecimal.ZERO), "zero native expense remains supported");
        h.assertTrue(armor(p).toDecimalMap().equals(after.toDecimalMap()), "relearn/category/zero spend cannot repartition or refill");
        CompoundTag saved = tag(p).copy();
        h.assertFalse(HighPowerCombat.spendShield(p, after.base().shield().add(BigDecimal.ONE)), "unaffordable debit refuses");
        h.assertTrue(tag(p).equals(saved), "unaffordable debit publishes no parent, child or timing changes");
        ServerPlayer ordinary = f.fake(2, false);
        h.assertTrue(HighPowerCombat.spendShield(ordinary, BigDecimal.ONE), "pre252 ordinary writer remains usable");
        h.assertFalse(tag(ordinary).contains(ZeroGapArmorStorage.KEY) || tag(ordinary).contains(ZeroGapArmorStorage.INITIALIZED_KEY),
                "ordinary writers never enroll an owner into optional252 metadata");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void genuine_212_contact_retains_actual_parent_and_notifies_after_break_debt(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.connected(2, true, 0), p = f.connected(2, true, 7);
        f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void amount(SkillAttackEvent e) {
            if (e.player == caster && e.skill.equals("meltdowner.dual_point")) e.amount = 5;
        }});
        f.ready(() -> {
            f.seed(p, amounts("2", "4", "1", "3", "3", "5"), zeros(), "1000");
            var before = armor(p); int[] rewards = {0}; boolean[] callback = {false};
            f.hook(new Object() { @SubscribeEvent public void xp(SkillExpAddedEvent e) {
                if (e.getEntity() != p || !e.skill.equals(ScatterPlates.ID)) return;
                rewards[0]++; var settled = conserved(h, p, "productive212 XP callback");
                eq(h, settled.scatter().plate(0).current(), "6", "explicit contact did not become ordinary B0 debit");
                eq(h, settled.scatter().plate(1).current(), "0", "native contacted B1 settled before reward");
                eq(h, settled.scatter().plate(2).current(), "8", "uncontacted B2 remains authoritative");
                h.assertTrue(capture(p).timing().orElseThrow().breakTicks() == 4, "positive-to-zero debt is stamped before source XP callback");
                f.spend(p, BigDecimal.ONE);
                tag(p).getCompound("activeGrowth").putString("resourceCallbackSentinel", "retained"); callback[0] = true;
            }});
            f.armScatter(p); f.aim(caster, p);
            h.assertTrue(f.cast(caster), "genuine paid NEW211 hand beam reaches the old212 contact path");
            var after = conserved(h, p, "212 contact and nested expense");
            h.assertTrue(callback[0] && rewards[0] == 1, "exhausting old212 still awards exactly one productive mastery callback");
            h.assertTrue(AbilityStorage.get(p).exp(ScatterPlates.ID) == (double).005F, "unchanged native mastery amount");
            eq(h, after.plate(0).baseCurrent(), "1", "nested postcommit ordinary spend survives outer return");
            eq(h, after.plate(1).baseCurrent(), "4", "nested debit does not repartition sibling");
            eq(h, after.plate(2).baseCurrent(), "0", "contacted first child remains exhausted");
            eq(h, after.plate(3).baseCurrent(), "0", "contacted second child remains exhausted");
            for (int i = 4; i < 6; i++) eq(h, after.plate(i).baseCurrent(), before.plate(i).baseCurrent(), "actual B2 children survive " + i);
            eq(h, after.base().health(), "999", "five damage spends four actual B1 units then one HP");
            h.assertTrue(tag(p).getCompound("activeGrowth").getString("resourceCallbackSentinel").equals("retained"),
                    "source callback metadata is never overwritten by the outer commit");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void genuine_242_arrow_debits_actual_base_and_lower_only_after_native_body_damage(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.connected(4, true, 0); Zombie shooter = f.shooter(p);
        f.ready(() -> {
            f.seed(p, amounts(".2", ".3", ".1", ".4", ".4", ".6"), amounts(".2", "3", ".3", "7", "1.5", "10"), "1000");
            var before = armor(p); f.armCurtain(p);
            h.runAfterDelay(6, () -> {
                h.assertTrue(DissipationCurtain.active(p), "six native startup ticks completed");
                Arrow arrow = f.arrow(p, shooter); arrow.tick();
                var after = conserved(h, p, "real242 native arrow");
                eq(h, after.base().shield(), "0", "only two actual base units were available");
                eq(h, after.reserves().lower(), "0", "only two actual lower units were available");
                eq(h, after.reserves().upper(), "20", "242 cannot borrow the upper reserve");
                eq(h, after.base().health(), "989", "four actual resources absorb five of native sixteen; eleven reach HP");
                for (int i = 0; i < 6; i++) {
                    eq(h, after.plate(i).baseCurrent(), "0", "actual base loss reconciled once " + i);
                    eq(h, after.plate(i).reserveCurrent(), i % 2 == 0 ? BigDecimal.ZERO : before.plate(i).reserveCurrent(), "reserve parity retained " + i);
                }
                h.assertTrue(arrow.isRemoved() && DissipationCurtainArrowReceipts.receiptCount(p) == 0, "real native hit disposition and single-use source receipt survive");
                h.assertTrue(capture(p).timing().orElseThrow().breakTicks() == 4, "only exhausted total plate currents stamp physical debt");
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void native_source_revocation_never_debits_optional_children_or_changes_body_outcome(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.connected(4, true, 0); Zombie shooter = f.shooter(p), other = f.shooter(p);
        f.ready(() -> {
            f.asymmetric(p); f.armCurtain(p);
            h.runAfterDelay(6, () -> {
                var before = armor(p); boolean[] changed = {false}; Arrow arrow = f.arrow(p, shooter);
                f.hook(new Object() { @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent e) {
                    if (e.getEntity() == p && e.getSource().getDirectEntity() == arrow) { arrow.setOwner(other); changed[0] = true; }
                }});
                arrow.tick(); var after = conserved(h, p, "revoked native source");
                h.assertTrue(changed[0] && arrow.isRemoved(), "real native incoming callback revokes already-claimed arrow identity");
                currents(h, after, before, "revoked source leaves all twelve currents unchanged");
                eq(h, after.base().health(), before.base().health().subtract(n("16")), "ordinary native body damage still commits once");
                h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(p) == 0, "revoked source evidence is consumed");
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void real_152_arrow_fee_uses_lower_then_upper_and_preserves_callback_expense(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.connected(5, false, 0);
        // The declared LV5 source fixture includes the same native prerequisites as existing152 tests.
        for (String id : List.of(HighPowerSkills.MAGNETIC_BRACER, GroundingReturn.ID, PotentialBuffer.ID)) AbilityStorage.get(p).learn(id);
        f.ready(() -> {
            BigDecimal fee = n(HighPowerCombat.maxShieldText(p)).divide(n("100")), part = fee.divide(n("16"));
            f.seed(p, List.of(BigDecimal.ZERO, BigDecimal.ZERO, part, part.multiply(n("3")), BigDecimal.ZERO, BigDecimal.ZERO),
                    List.of(part, part.multiply(n("4")), part, part.multiply(n("4")), part.multiply(n("2")), part.multiply(n("8"))), "1000");
            f.armDomain(p); var before = armor(p); CompoundTag saved = tag(p).copy();
            h.assertFalse(MagneticDomainReserve.debitArrow(p, fee, () -> false), "failed actual arrow context is free");
            h.assertFalse(MagneticDomainReserve.debitArrow(p, fee.multiply(n("2")), () -> true), "wrong proposed fixed fee is free");
            h.assertTrue(tag(p).equals(saved), "failed public fee validation changes no ledger field");
            boolean[] sourceCallback = {false};
            h.assertTrue(MagneticDomainReserve.debitArrow(p, fee, () -> {
                sourceCallback[0] = true; h.assertTrue(HighPowerCombat.spendShield(p, BigDecimal.ONE), "source validation's nested ordinary expense"); return true;
            }), "real152 fixed refraction debit succeeds from freshly validated parents");
            var after = conserved(h, p, "152 fee with nested source callback");
            h.assertTrue(sourceCallback[0], "public arrow source callback executed");
            eq(h, after.base().shield(), "0", "fee uses remaining base exactly once");
            eq(h, after.reserves().lower(), "0", "152 then exhausts actual lower reserve");
            eq(h, after.reserves().upper(), fee.divide(n("2")).subtract(BigDecimal.ONE), "upper pays remaining fee plus prior one-unit expense without outer overwrite");
            eq(h, after.plate(5).reserveCurrent(), fee.divide(n("2")).subtract(BigDecimal.ONE), "last odd child retains its exact remainder");
            for (int i = 0; i < 5; i++) eq(h, after.plate(i).reserveCurrent(), "0", "ordered152 reserve child debit " + i);
            eq(h, after.base().health(), before.base().health(), "refraction never borrows HP");
            h.assertTrue(capture(p).timing().orElseThrow().breakTicks() == 4, "old152 exhaustion stamps debt before return");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=80)
    public static void one_quiet_recovery_budget_updates_base_then_lower_without_second_child_credit(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.fake(5, false);
        BigDecimal maximum = n(HighPowerCombat.maxShieldText(p)), budget = maximum.divide(n("100"));
        var initial = ZeroGapArmorShares.initialize(ExactCombatState.full(HighPowerLevel.LV5).withShieldEnabled(false),
                ScatterPlateShares.initialize(maximum, maximum), SharedShieldReserveShares.ZERO);
        List<BigDecimal> full = initial.plates().stream().map(ZeroGapArmorShares.Plate::baseCurrent).toList();
        f.seed(p, full, amounts("1", "2", "5", "7", "9", "12"), "1000");
        h.assertTrue(HighPowerCombat.spendShield(p, budget.divide(n("2"))), "real spend starts native sixty-tick quiet delay");
        var before = armor(p); long started = p.level().getGameTime();
        HighPowerCombat.tickShield(p); currents(h, armor(p), before, "same spend tick receives no recovery");
        h.runAfterDelay(59, () -> {
            HighPowerCombat.tickShield(p); currents(h, armor(p), before, "fifty-nine ticks remain too early");
            h.runAfterDelay(1, () -> {
                h.assertTrue(p.level().getGameTime() - started == 60, "exactly sixty actual native ticks elapsed");
                HighPowerCombat.tickShield(p); var after = conserved(h, p, "single native shared recovery");
                eq(h, after.base().shield(), maximum, "one budget first fills actual missing base half");
                eq(h, after.reserves().lower(), before.reserves().lower().add(budget.divide(n("2"))), "only the remaining half budget enters lower reserve");
                eq(h, after.reserves().upper(), before.reserves().upper(), "lower has room, so upper receives no credit");
                eq(h, after.total().subtract(before.total()), budget, "twelve child deltas sum to exactly one existing budget");
                HighPowerCombat.tickShield(p); currents(h, armor(p), after, "duplicate same-tick recovery creates no second child budget");
                eq(h, armor(p).base().health(), before.base().health(), "shield recovery never heals HP");
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void native_healing_totem_and_death_veto_change_hp_without_refilling_children(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer healed = f.connected(2, true, 0), totem = f.connected(2, true, 14), veto = f.connected(2, true, 28);
        Map<ServerPlayer, List<String>> nativeDamage = new IdentityHashMap<>();
        for (ServerPlayer p : List.of(healed, totem, veto)) nativeDamage.put(p, new ArrayList<>());
        f.hook(new Object() {
            private void add(LivingEntity entity, String detail) {
                if (!(entity instanceof ServerPlayer p) || !nativeDamage.containsKey(p)) return;
                List<String> history = nativeDamage.get(p); if (history.size() == 24) history.removeFirst();
                history.add("tick=" + p.server.getTickCount() + " " + detail);
            }
            @SubscribeEvent(priority=EventPriority.LOWEST, receiveCanceled=true) public void incoming(LivingIncomingDamageEvent e) {
                add(e.getEntity(), "incoming " + e.getSource().type().msgId() + " amount=" + e.getAmount() + " canceled=" + e.isCanceled());
            }
            @SubscribeEvent(priority=EventPriority.LOWEST) public void post(LivingDamageEvent.Post e) {
                add(e.getEntity(), "post " + e.getSource().type().msgId() + " damage=" + e.getNewDamage());
            }
            @SubscribeEvent(priority=EventPriority.LOWEST, receiveCanceled=true) public void death(LivingDeathEvent e) {
                add(e.getEntity(), "death " + e.getSource().type().msgId() + " canceled=" + e.isCanceled());
            }
        });
        // A connected fixture actor can miss the level's native entity-ticking pass.
        // Observe actual progress at a completed server-tick boundary;
        // supplement only a missing ServerPlayer.tick, never its private protection field.
        final java.lang.reflect.Field protectionField;
        try { protectionField = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime"); protectionField.setAccessible(true); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        java.util.function.ToIntFunction<ServerPlayer> protection = p -> {
            try { return protectionField.getInt(p); }
            catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        };
        List<ServerPlayer> actors = List.of(healed, totem, veto);
        Map<ServerPlayer, Integer> observedProtection = new IdentityHashMap<>(), observedTick = new IdentityHashMap<>();
        for (ServerPlayer p : actors) {
            observedProtection.put(p, protection.applyAsInt(p)); observedTick.put(p, p.server.getTickCount());
        }
        totem.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        for (ServerPlayer p : actors) {
            h.assertTrue(protection.applyAsInt(p) > 0, "initial real login protection is still active before the matched generic damage control");
            CompoundTag protectedLedger = tag(p).copy(); float protectedHealth = p.getHealth(); ItemStack protectedTotem = p.getOffhandItem().copy();
            List<String> protectedEvents = List.copyOf(nativeDamage.get(p));
            h.assertFalse(p.hurt(p.damageSources().generic(), 2), "the same ordinary generic damage is refused before native login protection expires");
            h.assertTrue(tag(p).equals(protectedLedger) && p.getHealth() == protectedHealth
                    && ItemStack.matches(p.getOffhandItem(), protectedTotem) && nativeDamage.get(p).equals(protectedEvents),
                    "protected control preserves exact/native HP, totem, all optional child data and every native damage event");
        }
        int warmupStarted = healed.server.getTickCount();
        boolean[] warmupComplete = {false}, exactNativeTicks = {true};
        f.hook(new Object() {
            @SubscribeEvent(priority=EventPriority.LOWEST) public void residentTick(ServerTickEvent.Post event) {
                if (f.closed || h.testInfo.isDone() || warmupComplete[0] || event.getServer() != healed.server) return;
                int tick = event.getServer().getTickCount();
                for (ServerPlayer p : actors) {
                    if (observedTick.get(p) >= tick) continue;
                    observedTick.put(p, tick);
                    int before = protection.applyAsInt(p);
                    if (before > 0 && before == observedProtection.get(p)) {
                        p.tick(); exactNativeTicks[0] &= protection.applyAsInt(p) == before - 1;
                    }
                    observedProtection.put(p, protection.applyAsInt(p));
                }
            }
        });
        f.ready(() -> {
            h.assertTrue(healed.server.getTickCount() - warmupStarted >= 61 && exactNativeTicks[0],
                    "at least sixty-one genuine server ticks elapsed and every supplemental native tick decremented protection exactly once");
            for (ServerPlayer p : actors) h.assertTrue(protection.applyAsInt(p) <= 0,
                    "sixty-one genuine server ticks expire every actor's native login protection; " + nativeDeathSnapshot(p));
            warmupComplete[0] = true;
            for (var p : List.of(healed, totem, veto)) f.asymmetric(p);
            var priorHeal = armor(healed); healed.heal(3); HighPowerCombat.tick(new EntityTickEvent.Post(healed));
            var afterHeal = conserved(h, healed, "native heal event settlement");
            eq(h, afterHeal.base().health(), "1003", "ordinary native healing retains exact one-to-one units"); currents(h, afterHeal, priorHeal, "HP-only healing");
            f.health(totem, "1"); f.health(veto, "1"); var priorTotem = armor(totem); var priorVeto = armor(veto);
            totem.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING)); boolean[] vetoed = {false};
            f.hook(new Object() { @SubscribeEvent public void death(LivingDeathEvent e) { if (e.getEntity() == veto) { vetoed[0] = true; e.setCanceled(true); } }});
            String beforeTotem = nativeDeathSnapshot(totem), beforeVeto = nativeDeathSnapshot(veto);
            System.out.println("NEW252 death fixture BEFORE: totem={" + beforeTotem + "},veto={" + beforeVeto
                    + "}; totemNativeEvents=" + nativeDamage.get(totem) + "; vetoNativeEvents=" + nativeDamage.get(veto));
            try {
                h.assertTrue(totem.hurt(totem.damageSources().generic(), 2), "ordinary native lethal damage reaches totem; before=" + beforeTotem
                        + "; after=" + nativeDeathSnapshot(totem) + "; nativeEvents=" + nativeDamage.get(totem));
                h.assertTrue(veto.hurt(veto.damageSources().generic(), 2), "ordinary native lethal damage reaches death event; before=" + beforeVeto
                        + "; after=" + nativeDeathSnapshot(veto) + "; deathVetoCalled=" + vetoed[0] + "; nativeEvents=" + nativeDamage.get(veto));
            } finally {
                System.out.println("NEW252 death fixture AFTER: totem={" + nativeDeathSnapshot(totem) + "},veto={" + nativeDeathSnapshot(veto)
                        + "}; deathVetoCalled=" + vetoed[0] + "; totemNativeEvents=" + nativeDamage.get(totem) + "; vetoNativeEvents=" + nativeDamage.get(veto));
            }
            HighPowerCombat.tick(new EntityTickEvent.Post(totem)); HighPowerCombat.tick(new EntityTickEvent.Post(veto));
            h.assertTrue(totem.isAlive() && totem.getOffhandItem().isEmpty(), "one real native totem consumed");
            h.assertTrue(vetoed[0] && veto.isAlive(), "native death cancellation remains authoritative");
            var savedTotem = conserved(h, totem, "native totem restoration");
            var savedVeto = conserved(h, veto, "native death veto restoration");
            h.assertTrue(savedTotem.base().health().signum() > 0, "totem reconciles restored exact HP"); eq(h, savedVeto.base().health(), "1", "veto restores the prior exact injury");
            currents(h, savedTotem, priorTotem, "totem cannot refill any child"); currents(h, savedVeto, priorVeto, "death veto cannot refill any child");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void earned_growth_and_operator_reset_stage_caps_and_q_without_refill_or_extra_xp(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.fake(2, true); f.seed(p, amounts("0", "0", "3", "9", "8", "10"), amounts("1", "2", "5", "7", "9", "12"), "100");
        var before = armor(p); var ability = AbilityStorage.get(p); int denominator = SkillCatalog.levelSkillCount(ability);
        double cp = HighPowerResources.cp(p), xp = ability.levelExperience; int[] events = {0};
        CompoundTag activeGrowth = tag(p).getCompound("activeGrowth");
        f.hook(new Object() { @SubscribeEvent public void xp(SkillExpAddedEvent e) {
            if (e.getEntity() == p) { events[0]++; conserved(h, p, "native XP observer sees a valid ledger"); }
        }});
        float award = (float)(ClassicRules.levelThreshold(2, denominator) * .2);
        ability.addExperience(DualPoint.ID, award); var grown = conserved(h, p, "real earned fractional capacity growth");
        h.assertTrue(grown.base().maxShield().compareTo(before.base().maxShield()) > 0, "actual native XP increases certified capacity Q");
        currents(h, grown, before, "capacity growth cannot refill any of twelve currents");
        h.assertTrue(tag(p).getCompound("activeGrowth") == activeGrowth, "earned growth retains the live active-growth object for callback holders");
        eq(h, grown.base().health(), before.base().health(), "earned growth preserves absolute injury");
        eq(h, HighPowerGrowth.values(p).position(), HighPowerGrowth.values(p).earnedPosition(), "active q and earned Q are coherent on return");
        h.assertTrue(events[0] == 1 && ability.levelExperience == (double)((float)xp + award)
                && SkillCatalog.levelSkillCount(ability) == denominator && ability.level == 2, "one unchanged native float-XP award and classic denominator");
        h.assertTrue(HighPowerResources.cp(p) == cp, "capacity increase does not refill current CP");
        ability.setLevel(1); ability.levelExperience = 0;
        h.assertTrue(HighPowerCombat.resetGrowthContract(p), "explicit operator reset accepts currents fitting all lower caps");
        var reset = conserved(h, p, "operator capacity reset"); currents(h, reset, before, "operator reset never redistributes current resources");
        eq(h, reset.base().maxShield(), HighPowerLevel.LV1.maxShield(), "reset adopts exact LV1 ceiling");
        eq(h, HighPowerGrowth.values(p).position(), BigDecimal.ONE, "operator reset active q");
        eq(h, HighPowerGrowth.values(p).earnedPosition(), BigDecimal.ONE, "operator reset certified Q");
        h.assertTrue(ability.levelExperience == 0 && events[0] == 1, "reset manufactures no native XP or Added callback");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void operator_reset_refuses_individual_child_overflow_even_when_every_parent_fits(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.fake(2, true);
        f.seed(p, amounts("20", "0", "0", "0", "0", "0"), amounts("1", "2", "5", "7", "9", "12"), "100");
        var before = armor(p); var ability = AbilityStorage.get(p); ability.level = 1; ability.levelExperience = 0; CompoundTag saved = tag(p).copy();
        h.assertTrue(before.scatter().plate(0).current().compareTo(ScatterPlateShares.initialize(BigDecimal.ZERO, HighPowerLevel.LV1.maxShield()).plate(0).capacity()) < 0,
                "the individual212 parent and total already fit the requested lower capacity");
        h.assertFalse(HighPowerCombat.resetGrowthContract(p), "one252 child above its own lower cap cannot be clipped or redistributed");
        h.assertTrue(tag(p).equals(saved), "refused reset does not stage Q, q, caps, reserve or child fields partially");
        conserved(h, p, "refused per-child contraction"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void native_save_clone_and_death_refill_only_actual_base_gain_and_copy_reserve_debt(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.fake(2, true), replacement = f.fake(2, true); f.asymmetric(p);
        var beforeBreak = armor(p); f.commit(p, beforeBreak.contact(0, n("1"), BigDecimal.ZERO, n("1")).transaction());
        var before = armor(p); var timing = capture(p).timing().orElseThrow();
        AbilityStorage.save(p); CompoundTag saved = new CompoundTag(); p.saveWithoutId(saved); AbilityStorage.remove(p); p.load(saved);
        currents(h, conserved(h, p, "native save/load"), before, "save/load keeps asymmetric resources");
        HighPowerCombat.clonePlayer(new PlayerEvent.Clone(replacement, p, false));
        currents(h, conserved(h, replacement, "nondeath native clone"), before, "nondeath clone has zero actual gain");
        h.assertTrue(capture(replacement).timing().orElseThrow().equals(timing), "nondeath clone preserves physical debt and phase");
        HighPowerCombat.clonePlayer(new PlayerEvent.Clone(replacement, p, true)); var death = conserved(h, replacement, "native death clone");
        for (int i = 0; i < 6; i++) {
            eq(h, death.plate(i).baseCurrent(), death.plate(i).baseCapacity(), "existing full-base death policy credits child " + i);
            eq(h, death.plate(i).reserveCurrent(), before.plate(i).reserveCurrent(), "death copies reserve without refill " + i);
        }
        eq(h, death.base().health(), death.base().maxHealth(), "existing full-HP clone policy remains intact");
        h.assertTrue(capture(replacement).timing().orElseThrow().equals(timing) && timing.breakTicks() == 4,
                "death and base gain cannot erase four-tick debt; no252 native ticker is installed");
        h.assertFalse(HighPowerCombat.shieldActive(replacement), "clone does not restore a live defense controller"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void legacy_scatter_helpers_preserve_parent_only_writes_without_healthy_252(GameTestHelper h) {
        Fixture f = new Fixture(h);
        for (boolean corrupt252 : List.of(false, true)) {
            ServerPlayer p = f.fake(2, true); var before = ScatterPlateStorage.ensure(p).orElseThrow();
            if (corrupt252) {
                CompoundTag malformed = new CompoundTag(); malformed.putString("version", "legacy-helper-probe");
                tag(p).put(ZeroGapArmorStorage.KEY, malformed); tag(p).putBoolean(ZeroGapArmorStorage.INITIALIZED_KEY, true);
            }
            CompoundTag saved = tag(p).copy();
            var proposedBase = ExactCombatState.full(HighPowerLevel.LV2).spendShield(n("5")).withShieldEnabled(false);
            var ordinary = before.afterBaseSpend(proposedBase.shield());
            Object change = invoke(ScatterPlateStorage.class, "baseChanged", new Class<?>[]{LivingEntity.class, ExactCombatState.class}, p, proposedBase);
            h.assertTrue(change != null, "legacy helper still returns its actual parent allocation change");
            CompoundTag expected = saved.copy(), parents = new CompoundTag(); ordinary.toDecimalMap().forEach(parents::putString);
            expected.put(ScatterPlateStorage.KEY, parents);
            if (corrupt252) expected.putBoolean(ZeroGapArmorStorage.INVALIDATED_KEY, true);
            h.assertTrue(tag(p).equals(expected), "legacy baseChanged publishes only parent shares and optional unavailable latch");

            p.getPersistentData().put(HighPowerCombat.DATA_KEY, saved.copy());
            var contacted = before.contact(1, n("5"), n("5")).shares();
            invoke(ScatterPlateStorage.class, "commit", new Class<?>[]{LivingEntity.class, ExactCombatState.class, ScatterPlateShares.class}, p, proposedBase, contacted);
            expected = saved.copy(); parents = new CompoundTag(); contacted.toDecimalMap().forEach(parents::putString);
            expected.put(ScatterPlateStorage.KEY, parents);
            if (corrupt252) expected.putBoolean(ZeroGapArmorStorage.INVALIDATED_KEY, true);
            h.assertTrue(tag(p).equals(expected), "legacy contact helper retains its caller-owned exact base write and original corrupt payload");
            // Complete the historical caller's paired base write before inspecting the final ledger.
            proposedBase.toDecimalMap().forEach(tag(p)::putString);
            h.assertTrue(HighPowerCombat.enabled(p) && ScatterPlateStorage.read(p).orElseThrow().toDecimalMap().equals(contacted.toDecimalMap()),
                    "caller completion restores the exact legacy parent/base agreement");
            h.assertFalse(tag(p).contains(ZeroGapArmorStorage.TIMING_KEY), "legacy helper cannot invent252 timing metadata");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void legacy_clone_without_pending_growth_retains_absent_modern_q_metadata(GameTestHelper h) {
        Fixture f = new Fixture(h);
        for (boolean initialized252 : List.of(false, true)) {
            ServerPlayer p = f.fake(2, true), replacement = f.fake(2, true); f.asymmetric(p); var before = armor(p);
            if (!initialized252) for (String key : List.of(ZeroGapArmorStorage.KEY, ZeroGapArmorStorage.INITIALIZED_KEY,
                    ZeroGapArmorStorage.INVALIDATED_KEY, ZeroGapArmorStorage.TIMING_KEY)) tag(p).remove(key);
            for (String key : List.of("growthCurve", "growthPosition", "earnedLevel")) tag(p).remove(key);
            tag(p).putBoolean("bracerActive", true); tag(p).putLong("lastShieldTick", 17);
            CompoundTag saved = tag(p).copy();
            h.assertTrue(HighPowerCombat.enabled(p), "declared integer-anchor legacy ledger is independently valid");
            for (boolean death : List.of(false, true)) {
                HighPowerCombat.clonePlayer(new PlayerEvent.Clone(replacement, p, death));
                for (String key : List.of("growthCurve", "growthPosition", "earnedLevel"))
                    h.assertFalse(tag(replacement).contains(key), "clone without a genuine growth receipt does not migrate absent " + key);
                h.assertTrue(HighPowerCombat.enabled(replacement), "legacy clone remains a valid certified integer ledger");
                eq(h, HighPowerCombat.earnedGrowthPosition(replacement), BigDecimal.TWO, "clone preserves the legacy integer Q");
                eq(h, n(HighPowerCombat.healthText(replacement)), death ? before.base().maxHealth() : before.base().health(), "legacy clone preserves native HP policy");
                eq(h, HighPowerCombat.shield(replacement), death ? before.base().maxShield() : before.base().shield(), "legacy clone preserves native base policy");
                h.assertTrue(tag(replacement).getCompound(MagneticDomainReserve.KEY).equals(saved.getCompound(MagneticDomainReserve.KEY)), "legacy clone never refills reserves");
                h.assertTrue(tag(replacement).getCompound("activeGrowth").equals(saved.getCompound("activeGrowth")), "no pending growth means no active-q metadata rewrite");
                h.assertFalse(tag(replacement).getBoolean("bracerActive"), "legacy clone clears the historical defense flag");
                h.assertTrue(tag(replacement).getLong("lastShieldTick") == Long.MIN_VALUE, "legacy clone clears the historical recovery tick");
                if (initialized252) conserved(h, replacement, "legacy Q with existing optional252");
                else h.assertFalse(tag(replacement).contains(ZeroGapArmorStorage.KEY) || tag(replacement).contains(ZeroGapArmorStorage.INITIALIZED_KEY),
                        "legacy clone does not enroll optional252");
            }
            h.assertTrue(tag(p).equals(saved), "both native clone paths retain the original legacy resource bytes");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void corrupt_optional_metadata_latches_unavailable_while_old_combat_remains_native(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.connected(2, false, 0);
        f.ready(() -> {
            f.asymmetric(p); var before = armor(p);
            tag(p).getCompound(ZeroGapArmorStorage.KEY).putString("reserve5", "malformed-final-child");
            CompoundTag bad = tag(p).getCompound(ZeroGapArmorStorage.KEY).copy();
            h.assertTrue(HighPowerCombat.spendShield(p, n("3")), "optional corrupt252 never blocks the old ordinary resource expense");
            h.assertTrue(p.hurt(p.damageSources().generic(), 2), "native damage still accepts the ordinary source");
            eq(h, HighPowerCombat.shield(p), before.base().shield().subtract(n("3")), "prior base expense remains exact");
            eq(h, n(HighPowerCombat.healthText(p)), before.base().health().subtract(n("2")), "prior native body damage remains exact");
            h.assertTrue(HighPowerCombat.enabled(p) && tag(p).getBoolean(ZeroGapArmorStorage.INVALIDATED_KEY), "only optional252 is durably unavailable");
            h.assertTrue(tag(p).getCompound(ZeroGapArmorStorage.KEY).equals(bad), "corrupt payload stays intact for diagnosis");
            var ability = AbilityStorage.get(p); BigDecimal oldMaximum = n(HighPowerCombat.maxShieldText(p));
            ability.addExperience(HighPowerSkills.FORKED_ARC, ClassicRules.levelThreshold(1, SkillCatalog.levelSkillCount(ability)) * .2);
            h.assertTrue(n(HighPowerCombat.maxShieldText(p)).compareTo(oldMaximum) > 0 && HighPowerCombat.enabled(p),
                    "real earned receipt still advances valid certified capacities with unavailable optional252");
            eq(h, HighPowerGrowth.values(p).position(), HighPowerGrowth.values(p).earnedPosition(), "fallback earned q and Q remain coherent");
            h.assertTrue(tag(p).getCompound(ZeroGapArmorStorage.KEY).equals(bad), "fallback growth retains original corrupt252 payload");
            ability.levelExperience = 0;
            h.assertTrue(HighPowerCombat.resetGrowthContract(p), "optional252 corruption does not block an otherwise valid operator reset");
            eq(h, n(HighPowerCombat.maxShieldText(p)), oldMaximum, "fallback operator reset stages its valid lower certified caps");
            h.assertTrue(ScatterPlateStorage.ensure(p).isPresent(), "healthy212 ensure keeps its pre252 behavior");
            // Restore numerically matching child bytes only as a corruption/revival probe.
            var currentBase = new ExactCombatState(n(HighPowerCombat.healthText(p)), n(HighPowerCombat.maxHealthText(p)),
                    HighPowerCombat.shield(p), n(HighPowerCombat.maxShieldText(p)), false);
            var matching = before.reconcileParents(currentBase, ScatterPlateStorage.read(p).orElseThrow(), before.reserves()).after();
            var matchingTag = new CompoundTag(); matching.toDecimalMap().forEach(matchingTag::putString);
            tag(p).put(ZeroGapArmorStorage.KEY, matchingTag); f.spend(p, BigDecimal.ZERO);
            h.assertTrue(ZeroGapArmorStorage.read(p).isEmpty() && ZeroGapArmorStorage.capture(p).isEmpty(), "later matching or ensure cannot revive the unavailable latch");
            h.assertTrue(tag(p).getBoolean(ZeroGapArmorStorage.INVALIDATED_KEY), "ordinary writers never clear the final252 marker");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void stale_source_and_invalid_complete_resource_proposals_leave_no_partial_debit(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.fake(2, false); f.asymmetric(p);
        var before = armor(p); var outer = ZeroGapArmorStorage.prepare(capture(p), before.contact(2, n("1"), n("1"), BigDecimal.ZERO).transaction());
        h.assertTrue(HighPowerCombat.spendShield(p, n("2")), "actual old writer commits while the earlier proposal waits");
        CompoundTag newer = tag(p).copy();
        h.assertFalse(ZeroGapArmorStorage.apply(outer), "stale-before proposal cannot overwrite a real old-writer expense");
        h.assertTrue(tag(p).equals(newer), "stale refusal changes no parent, child, metadata or debt");
        var current = armor(p); var captured = capture(p);
        var replacementProbe = ZeroGapArmorStorage.prepare(captured, current.reconcileParents(current.base(), current.scatter(), current.reserves()));
        p.getPersistentData().put(HighPowerCombat.DATA_KEY, tag(p).copy()); CompoundTag replaced = tag(p), identityBytes = replaced.copy();
        h.assertFalse(ZeroGapArmorStorage.apply(replacementProbe), "equal bytes in a different authoritative source cannot authenticate a prepared debit");
        h.assertTrue(tag(p) == replaced && tag(p).equals(identityBytes), "source-identity refusal preserves exact replacement");
        ExactCombatState proposed = current.base().spendShield(BigDecimal.ONE);
        boolean refused = false;
        try {
            Object result = invoke(HighPowerCombat.class, "commitResources", new Class<?>[]{LivingEntity.class, ExactCombatState.class, ScatterPlateShares.class, SharedShieldReserveShares.class},
                    p, proposed, current.scatter(), current.reserves());
            refused = Boolean.FALSE.equals(result);
        } catch (IllegalArgumentException | IllegalStateException expected) { refused = true; }
        h.assertTrue(refused, "invalid explicit212 proposal cannot claim unchanged parent balances against a reduced base");
        h.assertTrue(tag(p).equals(identityBytes), "proposed-validation refusal happens before any debit or unavailable marker");
        h.assertTrue(HighPowerCombat.spendShield(p, BigDecimal.ONE), "fresh real writer remains usable after refused proposals");
        conserved(h, p, "successful fresh expense following refusals"); h.succeed();
    }

    private static BigDecimal n(String value) { return new BigDecimal(value); }
    /** Read-only failure diagnostics; retains the original position, timer and native hurt conditions. */
    private static String nativeDeathSnapshot(ServerPlayer p) {
        String spawn;
        try { var field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime"); field.setAccessible(true); spawn = Integer.toString(field.getInt(p)); }
        catch (ReflectiveOperationException failure) { spawn = failure.getClass().getSimpleName(); }
        return "tick=" + p.server.getTickCount() + ",pos=" + p.position() + ",box=" + p.getBoundingBox()
                + ",exactHP=" + tag(p).getString("health") + ",nativeHP=" + p.getHealth() + ",iframes=" + p.invulnerableTime
                + ",spawnInvulnerability=" + spawn + ",alive=" + p.isAlive() + ",removed=" + p.isRemoved()
                + ",invulnerable=" + p.isInvulnerable() + ",abilityInvulnerable=" + p.getAbilities().invulnerable
                + ",registered=" + (p.serverLevel().getEntity(p.getUUID()) == p)
                + ",lastSource=" + (p.getLastDamageSource() == null ? "none" : p.getLastDamageSource().type().msgId());
    }
    private static List<BigDecimal> amounts(String... values) { return Arrays.stream(values).map(HighPowerZeroGapResourceIntegrationTests::n).toList(); }
    private static List<BigDecimal> zeros() { return Collections.nCopies(6, BigDecimal.ZERO); }
    private static BigDecimal sum(List<BigDecimal> values) { return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add); }
    private static CompoundTag tag(ServerPlayer p) { return p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY); }
    private static ZeroGapArmorStorage.Capture capture(ServerPlayer p) { return ZeroGapArmorStorage.capture(p).orElseThrow(); }
    private static ZeroGapArmorShares armor(ServerPlayer p) { return ZeroGapArmorStorage.read(p).orElseThrow(); }
    private static void eq(GameTestHelper h, BigDecimal actual, String expected, String why) { eq(h, actual, n(expected), why); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) {
        h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected);
    }
    private static ZeroGapArmorShares conserved(GameTestHelper h, ServerPlayer p, String why) {
        h.assertTrue(HighPowerCombat.enabled(p), why + " retains a certified exact base ledger");
        var armor = armor(p); var scatter = ScatterPlateStorage.read(p).orElseThrow(); CompoundTag reserve = tag(p).getCompound(MagneticDomainReserve.KEY);
        BigDecimal base = BigDecimal.ZERO, lower = BigDecimal.ZERO, upper = BigDecimal.ZERO;
        for (int i = 0; i < 3; i++) {
            BigDecimal pair = armor.plate(2 * i).baseCurrent().add(armor.plate(2 * i + 1).baseCurrent());
            eq(h, pair, scatter.plate(i).current(), why + " independent authoritative B" + i); base = base.add(pair);
        }
        for (int i = 0; i < 6; i++) if (i % 2 == 0) lower = lower.add(armor.plate(i).reserveCurrent()); else upper = upper.add(armor.plate(i).reserveCurrent());
        eq(h, base, HighPowerCombat.shield(p), why + " authoritative base sum");
        eq(h, lower, n(reserve.getString("lower")), why + " authoritative lower sum");
        eq(h, upper, n(reserve.getString("upper")), why + " authoritative upper sum");
        eq(h, lower.add(upper), n(reserve.getString("amount")), why + " existing aggregate reserve");
        eq(h, armor.base().maxShield(), n(HighPowerCombat.maxShieldText(p)), why + " certified capacity");
        return armor;
    }
    private static void currents(GameTestHelper h, ZeroGapArmorShares actual, ZeroGapArmorShares expected, String why) {
        for (int i = 0; i < 6; i++) {
            eq(h, actual.plate(i).baseCurrent(), expected.plate(i).baseCurrent(), why + " base" + i);
            eq(h, actual.plate(i).reserveCurrent(), expected.plate(i).reserveCurrent(), why + " reserve" + i);
        }
    }
    private static Object invoke(Class<?> owner, String name, Class<?>[] parameters, Object... arguments) {
        try { var method = owner.getDeclaredMethod(name, parameters); method.setAccessible(true); return method.invoke(null, arguments); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new AssertionError("Native boundary failed: " + name, e.getCause());
        } catch (ReflectiveOperationException e) { throw new AssertionError("Native boundary missing: " + name, e); }
    }

    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(), connected = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>(); final List<Entity> entities = new ArrayList<>();
        final List<Object> hooks = new ArrayList<>(); final Map<ServerPlayer, Vec3> positions = new IdentityHashMap<>(); boolean closed;
        Fixture(GameTestHelper h) {
            this.h = h;
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {}
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
            h.onEachTick(() -> { if (!closed) for (var p : connected) p.doTick(); });
        }
        ServerPlayer fake(int level, boolean md) {
            return prepare(new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[HP-252-Writer]")), level, md);
        }
        ServerPlayer connected(int level, boolean md, double offset) {
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "ZG" + UUID.randomUUID().toString().substring(0, 10)), false);
            var p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); var connection = new Connection(PacketFlow.SERVERBOUND);
            channels.add(new EmbeddedChannel(connection)); NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie);
            connected.add(p); p.setGameMode(GameType.SURVIVAL); p.setPos(h.absoluteVec(new Vec3(8.5, 18, 8.5 + offset))); p.connection.resetPosition();
            positions.put(p, p.position()); return prepare(p, level, md);
        }
        ServerPlayer prepare(ServerPlayer p, int level, boolean md) {
            players.add(p); p.setNoGravity(true); p.getAbilities().instabuild = false;
            var ability = AbilityStorage.get(p); ability.changeCategoryClassic(md ? HighPowerCategories.MELTDOWNER : HighPowerCategories.ELECTROMASTER); ability.setLevel(level);
            for (String id : md ? List.of(DualPoint.ID, ScatterPlates.ID, DissipationCurtain.ID) : List.of(HighPowerSkills.FORKED_ARC, MagneticDomain.ID)) ability.learn(id);
            ability.setActivateState(true); ability.overloadFine = true;
            h.assertTrue(HighPowerCombat.enroll(p, level), "declared native252 resource-owner fixture"); HighPowerResources.initializeBudget(p);
            ability.presets.edit(0, 0, md ? DualPoint.ID : MagneticDomain.ID, ability::learned); return p;
        }
        void ready(Runnable action) {
            h.runAfterDelay(61, () -> { for (var p : connected) { p.doTick(); p.setPos(positions.get(p)); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); } action.run(); });
        }
        void asymmetric(ServerPlayer p) { seed(p, amounts("0", "0", "3", "21", "8", "28"), amounts("1", "2", "5", "7", "9", "12"), "1000"); }
        /** Setup only: exact existing parents, explicit252 migration, then valid asymmetric child fixture. */
        void seed(ServerPlayer p, List<BigDecimal> bases, List<BigDecimal> reserves, String health) {
            BigDecimal shield = sum(bases), maximum = n(HighPowerCombat.maxShieldText(p));
            var base = new ExactCombatState(n(health), n(HighPowerCombat.maxHealthText(p)), shield, maximum, false);
            var parents = ScatterPlateShares.restore(shield, maximum, List.of(bases.get(0).add(bases.get(1)), bases.get(2).add(bases.get(3)), bases.get(4).add(bases.get(5))));
            var halves = new SharedShieldReserveShares(reserves.get(0).add(reserves.get(2)).add(reserves.get(4)), reserves.get(1).add(reserves.get(3)).add(reserves.get(5)));
            base.toDecimalMap().forEach(tag(p)::putString); var parentTag = new CompoundTag(); parents.toDecimalMap().forEach(parentTag::putString);
            tag(p).put(ScatterPlateStorage.KEY, parentTag); tag(p).putBoolean(ScatterPlateStorage.INITIALIZED_KEY, true);
            var reserveTag = new CompoundTag(); halves.toDecimalMap(base).forEach(reserveTag::putString); reserveTag.putInt("version", MagneticDomainReserve.VERSION); tag(p).put(MagneticDomainReserve.KEY, reserveTag);
            h.assertTrue(ZeroGapArmorStorage.apply(ZeroGapArmorStorage.prepareInitialization(capture(p))), "explicit tested252 storage initialization");
            var initialized = armor(p); var declared = ZeroGapArmorShares.restore(base, parents, halves, bases, reserves); List<Integer> breaks = new ArrayList<>();
            for (int i = 0; i < 6; i++) if (initialized.plate(i).current().signum() > 0 && declared.plate(i).current().signum() == 0) breaks.add(i);
            commit(p, new ZeroGapArmorShares.Transaction(initialized, declared, breaks)); conserved(h, p, "declared asymmetric starting fixture");
        }
        void health(ServerPlayer p, String hp) {
            var before = armor(p); var base = new ExactCombatState(n(hp), before.base().maxHealth(), before.base().shield(), before.base().maxShield(), false);
            commit(p, before.reconcileParents(base, before.scatter(), before.reserves()));
        }
        void commit(ServerPlayer p, ZeroGapArmorShares.Transaction transaction) {
            h.assertTrue(ZeroGapArmorStorage.apply(ZeroGapArmorStorage.prepare(capture(p), transaction)), "declared valid fixture transaction");
        }
        void spend(ServerPlayer p, BigDecimal amount) {
            var ability = AbilityStorage.get(p); String category = ability.category; ability.category = HighPowerCategories.ELECTROMASTER;
            try { h.assertTrue(HighPowerCombat.spendShield(p, amount), "real existing EM ordinary shield expense"); }
            finally { ability.category = category; }
        }
        void armScatter(ServerPlayer p) {
            var ability = AbilityStorage.get(p); ability.presets.edit(0, 0, ScatterPlates.ID, ability::learned); face(p, 180);
            h.assertTrue(ScatterPlates.toggle(p, wire(p, 1)), "real paid212 activation");
        }
        void armCurtain(ServerPlayer p) {
            var ability = AbilityStorage.get(p); ability.presets.edit(0, 0, DissipationCurtain.ID, ability::learned); face(p, 0);
            h.assertTrue(DissipationCurtain.start(p, wire(p, 1)), "real paid242 activation");
        }
        void armDomain(ServerPlayer p) { h.assertTrue(MagneticDomain.toggle(p, wire(p, 1)), "real paid152 activation"); }
        String wire(ServerPlayer p, long sequence) { return new HighPowerInput.Input(0, sequence, p.getId(), LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p))).wire(); }
        void aim(ServerPlayer p, LivingEntity target) {
            Vec3 d = target.getBoundingBox().getCenter().subtract(p.getEyePosition()); p.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
            p.setXRot((float)-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)))); p.setShiftKeyDown(false);
        }
        boolean cast(ServerPlayer p) {
            boolean prior = p.server.isPvpAllowed(); p.server.setPvpAllowed(true);
            try { return DualPoint.tap(p, wire(p, 1)); } finally { p.server.setPvpAllowed(prior); }
        }
        Zombie shooter(ServerPlayer p) {
            Zombie mob = EntityType.HUSK.create(h.getLevel()); mob.setNoAi(true); mob.setNoGravity(true); mob.setSilent(true);
            mob.getAttribute(Attributes.ARMOR).setBaseValue(0); mob.setPos(p.position().add(5, 0, 5)); h.getLevel().addFreshEntity(mob); entities.add(mob); return mob;
        }
        Arrow arrow(ServerPlayer p, Zombie shooter) {
            var arrow = new Arrow(h.getLevel(), shooter, new ItemStack(Items.ARROW), null); arrow.setPos(p.position().add(0, 1, 8)); arrow.setDeltaMovement(new Vec3(0, 0, -8));
            h.getLevel().addFreshEntity(arrow); entities.add(arrow); return arrow;
        }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void close() {
            if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister);
            for (var p : players) {
                ScatterPlates.remove(p); DissipationCurtain.remove(p); MagneticDomain.remove(p); DualPoint.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p);
                if (connected.contains(p)) h.getLevel().getServer().getPlayerList().remove(p); else p.discard();
            }
            entities.forEach(Entity::discard); channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        }
        static void face(ServerPlayer p, float yaw) { p.setYRot(yaw); p.setYHeadRot(yaw); p.yBodyRot = yaw; p.setXRot(0); }
    }
}

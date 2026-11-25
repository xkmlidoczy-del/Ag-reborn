package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AbilityDamage;
import cn.academy.port.city.CityEspers;
import cn.academy.port.city.CityNpcContent;
import cn.academy.port.city.defense.CityDefenseContent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.CombatAttributes;
import cn.academy.port.highpower.DeflectionStrike;
import cn.academy.port.highpower.DualPoint;
import cn.academy.port.highpower.ElectromasterPrototype;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.highpower.ShearWindblade;
import cn.academy.port.skill.VecReflection;
import cn.academy.port.skill.PlasmaCannon;
import cn.academy.port.skill.RailgunDamage;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("academy_real_attributes")
@PrefixGameTestTemplate(false)
public final class EsperBalanceRuntimeTests {
    private EsperBalanceRuntimeTests() {}

    @GameTest(template = "highpower_empty", batch = "native_attributes", timeoutTicks = 20)
    public static void allFiveNativeRanks(GameTestHelper h) {
        float[] health = {26, 42, 150, 580, 2000};
        String[] shield = {"6", "12", "50", "190", "600"};
        double[] power = {8, 15, 42, 76, 110};
        for (int i = 0; i < health.length; i++) {
            var f = new CanonExpansionARuntimeTests.Fixture(h);
            try {
                var target = f.p;
                enroll(h, target, "electromaster", HighPowerSkills.FORKED_ARC, i + 1);
                h.assertTrue(target.getMaxHealth() == health[i] && target.getHealth() == health[i], "rank health witness");
                h.assertTrue(HighPowerCombat.shieldText(target).equals(shield[i]), "rank shield witness");
                HighPowerGrowth.values(target);
                h.assertTrue(target.getAttributeValue(CombatAttributes.ABILITY_POWER) == power[i], "rank skill power witness");
            } finally {
                f.close();
            }
        }
        h.succeed();
    }

    @GameTest(template = "highpower_empty", batch = "native_attributes", timeoutTicks = 20)
    public static void cityEsperAndDefenseCaps(GameTestHelper h) {
        for (var profile : CityEspers.Profile.values()) {
            if (profile == CityEspers.Profile.NONE) continue;
            var npc = CityNpcContent.NPC.get().create(h.getLevel());
            var data = new CompoundTag();
            data.putString("Esper", profile.id());
            data.putInt("EsperLevel", 5);
            npc.readAdditionalSaveData(data);
            npc.combat();
            h.assertTrue(npc.getMaxHealth() == 2000 && HighPowerCombat.shieldText(npc).equals("600"),
                    "LV5 city caps for " + profile.id());
            npc.discard();
        }
        var o5 = CityDefenseContent.O5.get().create(h.getLevel());
        o5.prepareDrop(UUID.randomUUID(), 0, h.getLevel().getGameTime() + 100, h.absoluteVec(new Vec3(4, 3, 5)), 4);
        h.assertTrue(o5.getMaxHealth() == 20_000_000 && HighPowerCombat.shieldText(o5).equals("10000000"),
                "O5 uses the protected combat capacity");
        o5.discard();
        h.succeed();
    }

    @GameTest(template = "highpower_empty", batch = "native_attributes", timeoutTicks = 290)
    public static void forkedArcSustainedLevelFiveDps(GameTestHelper h) {
        sustainedDps(h, "electromaster", HighPowerSkills.FORKED_ARC, 200, 140, 155,
                (p, input) -> ElectromasterPrototype.castLearned(p));
    }

    @GameTest(template = "highpower_empty", batch = "native_attributes", timeoutTicks = 290)
    public static void dualPointSustainedLevelFiveDps(GameTestHelper h) {
        sustainedDps(h, "meltdowner", DualPoint.ID, 20, 150, 205, DualPoint::tap);
    }

    @GameTest(template = "highpower_empty", batch = "native_attributes", timeoutTicks = 290)
    public static void deflectionStrikeSustainedLevelFiveDps(GameTestHelper h) {
        sustainedDps(h, "vecmanip", DeflectionStrike.ID, 15, 175, 200, DeflectionStrike::tap);
    }

    @GameTest(template = "highpower_empty", batch = "native_attributes", timeoutTicks = 290)
    public static void shearWindbladeSustainedLevelFiveDps(GameTestHelper h) {
        sustainedDps(h, "vecmanip", ShearWindblade.ID, 10, 120, 335, ShearWindblade::tap);
    }

    @GameTest(template = "highpower_empty", batch = "native_attributes", timeoutTicks = 90)
    public static void masteredRailgunInSameAndCrossRankDuels(GameTestHelper h) {
        var f = new CanonExpansionARuntimeTests.Fixture(h);
        var peer = new CanonExpansionARuntimeTests.Fixture(h);
        var novice = new CanonExpansionARuntimeTests.Fixture(h);
        var p = f.p;
        enroll(h, p, "electromaster", "railgun", 5);
        enroll(h, peer.p, "electromaster", HighPowerSkills.FORKED_ARC, 5);
        enroll(h, novice.p, "electromaster", HighPowerSkills.FORKED_ARC, 1);
        var origin = p.position();
        var dummy = dummy(h, origin.add(0, 0, 3));
        peer.p.setPos(origin.add(4, 0, -2));
        novice.p.setPos(origin.add(-4, 0, -2));
        h.runAfterDelay(65, () -> {
            var server = p.server;
            boolean previous = server.isPvpAllowed();
            server.setPvpAllowed(true);
            try {
                aim(p, origin, dummy);
                float before = dummy.getHealth();
                RailgunDamage.perform(p, 110, 2000);
                float heavy = before - dummy.getHealth();
                h.assertTrue(heavy > 280 && heavy <= 300, "mastered railgun native damage " + heavy);
                var peerBefore = HighPowerCombat.shield(peer.p);
                h.assertTrue(AbilityDamage.attack(p, "electromaster.railgun", peer.p, 110),
                        "railgun hit against LV5 settled");
                double shieldSpent = peerBefore.subtract(HighPowerCombat.shield(peer.p)).doubleValue();
                h.assertTrue(peer.p.isAlive() && peer.p.getHealth() == 2000 && shieldSpent > 280 && shieldSpent <= 300,
                        "LV5 survives the full-strength heavy hit: shield spent " + shieldSpent);
                h.assertTrue(AbilityDamage.attack(p, "electromaster.railgun", novice.p, 110),
                        "the same railgun hit against LV1 settled");
                h.assertTrue(!novice.p.isAlive() && novice.p.getHealth() == 0,
                        "the cross-rank hit is lethal without a PvP damage reduction");
                System.out.println("[ACA Balance] Mastered railgun native HP " + heavy + ", LV5 shield spent "
                        + shieldSpent + ", LV1 defeated by one hit");
                h.succeed();
            } finally {
                server.setPvpAllowed(previous);
                dummy.discard();
                novice.close();
                peer.close();
                f.close();
            }
        });
    }

    @GameTest(template = "highpower_empty", batch = "native_attributes", timeoutTicks = 145)
    public static void plasmaCannonHasOneDamageBudget(GameTestHelper h) {
        var f = new CanonExpansionARuntimeTests.Fixture(h);
        var p = f.p;
        enroll(h, p, "vecmanip", PlasmaCannon.ID, 5);
        AbilityStorage.get(p).addExperience(PlasmaCannon.ID, 1);
        var origin = p.position();
        var target = dummy(h, origin.add(0, 0, 3));
        float before = target.getHealth();
        h.runAfterDelay(65, () -> {
            aim(p, origin, target);
            h.assertTrue(PlasmaCannon.start(p), "mastered plasma cannon starts");
        });
        for (int tick = 66; tick <= 125; tick++) {
            h.runAfterDelay(tick, p::doTick);
        }
        h.runAfterDelay(100, () -> {
            aim(p, origin, target);
            h.assertTrue(PlasmaCannon.release(p), "mastered plasma cannon released after charge");
        });
        h.runAfterDelay(127, () -> {
            try {
                float damage = before - target.getHealth();
                h.assertTrue(damage > 280 && damage <= 300, "plasma dual-trigger collision has one native budget: " + damage);
                System.out.println("[ACA Balance] Mastered plasma cannon native HP: " + damage);
                h.succeed();
            } finally {
                PlasmaCannon.remove(p);
                target.discard();
                f.close();
            }
        });
    }

    private static void sustainedDps(GameTestHelper h, String category, String skill, int minCasts,
                                     int minDps, int maxDps, BiPredicate<ServerPlayer, String> cast) {
        var f = new CanonExpansionARuntimeTests.Fixture(h);
        var p = f.p;
        enroll(h, p, category, skill, 5);
        var ability = AbilityStorage.get(p);
        if (skill.equals(ShearWindblade.ID)) ability.learn(DeflectionStrike.ID);
        h.assertTrue(ability.presets.edit(0, 0, skill, ability::learned), "bind the benchmark skill");
        var target = dummy(h, p.position().add(0, 0, 3));
        var origin = p.position();
        float before = target.getHealth();
        int[] attemptsAccepted = {0};
        for (int offset = 0; offset < 200; offset++) {
            int sequence = offset + 1;
            h.runAfterDelay(65 + offset, () -> {
                // The embedded connection does not drive ServerGamePacketListenerImpl's usual player tick.
                p.doTick();
                aim(p, origin, target);
                long epoch = LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), ability);
                String input = new HighPowerInput.Input(0, sequence, p.getId(), epoch).wire();
                if (cast.test(p, input)) attemptsAccepted[0]++;
            });
        }
        h.runAfterDelay(265, () -> {
            try {
                double dps = (before - target.getHealth()) / 10.0;
                System.out.println("[ACA Balance] " + skill + " native HP over 200 ticks: " + (before - target.getHealth())
                        + ", DPS " + dps + ", casts " + attemptsAccepted[0]);
                h.assertTrue(attemptsAccepted[0] >= minCasts, "ordinary CP and cooldown admitted casts: " + attemptsAccepted[0]);
                h.assertTrue(dps >= minDps && dps <= maxDps, "ten-second native HP DPS: " + dps);
                h.succeed();
            } finally {
                target.discard();
                f.close();
            }
        });
    }

    @GameTest(template = "highpower_empty", batch = "native_attributes", timeoutTicks = 95)
    public static void passiveCounterUsesBilledShieldAndItsOwnCooldown(GameTestHelper h) {
        var f = new CanonExpansionARuntimeTests.Fixture(h);
        var p = f.p;
        enroll(h, p, "electromaster", HighPowerSkills.FORKED_ARC, 5);
        var first = dummy(h, p.position().add(0, 0, 2));
        var second = dummy(h, p.position().add(2, 0, 0));
        float before = first.getHealth();
        h.runAfterDelay(65, () -> p.hurt(p.damageSources().mobAttack(first), 10));
        h.runAfterDelay(67, () -> {
            h.assertTrue(before - first.getHealth() == 3, "ten shield spent returns exactly three native damage");
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(second), 10);
        });
        h.runAfterDelay(80, () -> {
            h.assertTrue(before == second.getHealth(), "the defender's counter cooldown also covers a different attacker");
            p.hurt(p.damageSources().mobAttack(second), 600);
        });
        h.runAfterDelay(82, () -> {
            try {
                h.assertTrue(before - second.getHealth() == 60, "breaking the shield returns at most sixty damage");
                h.assertTrue(p.getHealth() == 1980 && HighPowerCombat.shieldText(p).equals("0"),
                        "600 shield and 20 native HP were paid once across three hits");
                h.succeed();
            } finally {
                first.discard();
                second.discard();
                f.close();
            }
        });
    }

    @GameTest(template = "highpower_empty", batch = "native_attributes", timeoutTicks = 90)
    public static void activeVectorReflectionUsesNativeIncomingAmount(GameTestHelper h) {
        var f = new CanonExpansionARuntimeTests.Fixture(h);
        var p = f.p;
        enroll(h, p, "vecmanip", VecReflection.ID, 5);
        var attacker = dummy(h, p.position().add(0, 0, 3));
        h.runAfterDelay(65, () -> {
            try {
                AbilityStorage.get(p).addExperience(VecReflection.ID, 1);
                h.assertTrue(VecReflection.start(p), "mastered vector reflection started");
                float before = attacker.getHealth();
                p.hurt(p.damageSources().mobAttack(attacker), 220);
                h.assertTrue(Math.abs(before - attacker.getHealth() - 264) < .01f,
                        "reflection gives 120 percent of the incoming damage");
                h.assertTrue(p.getHealth() == 2000 && HighPowerCombat.shieldText(p).equals("600"),
                        "a reflected hit does not debit shields or heal the defender");
                h.succeed();
            } finally {
                VecReflection.abort(p);
                attacker.discard();
                f.close();
            }
        });
    }

    private static void enroll(GameTestHelper h, ServerPlayer p, String category, String skill, int level) {
        var state = AbilityStorage.get(p);
        state.changeCategoryClassic(category);
        state.setLevel(level);
        state.learn(skill);
        state.setActivateState(true);
        state.overloadFine = true;
        p.setNoGravity(true);
        h.assertTrue(HighPowerCombat.enroll(p, level), "enrolled native LV" + level + " player");
        HighPowerResources.initializeBudget(p);
    }

    private static Husk dummy(GameTestHelper h, Vec3 at) {
        var target = EntityType.HUSK.create(h.getLevel());
        target.setNoAi(true);
        target.setNoGravity(true);
        target.moveTo(at, 0, 0);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(30_000);
        target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        target.setHealth(30_000);
        h.getLevel().addFreshEntity(target);
        return target;
    }

    private static void aim(ServerPlayer p, Vec3 at, Husk target) {
        p.moveTo(at, 0, 0);
        p.setDeltaMovement(Vec3.ZERO);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        p.connection.resetPosition();
    }
}

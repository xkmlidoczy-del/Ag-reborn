package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.EsperDamageRules.Decision;
import cn.academy.port.highpower.core.EsperDamageRules.Policy;
import java.util.List;
import java.util.Random;

/** Witnesses for what an enrolled esper's ledger accepts: every damage source passes at any size; only the kill command and non-damage amounts are refused. */
public final class EsperDamageRulesRegressionTest {
    private static int checks;
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    private static Decision d(String type, double amount) { return EsperDamageRules.decide(Policy.DEFAULT, type, amount, false); }

    public static void main(String[] args) {
        List<String> everyDamage = java.util.Arrays.asList("minecraft:in_fire", "minecraft:campfire", "minecraft:on_fire", "minecraft:lava", "minecraft:hot_floor", "minecraft:in_wall",
                "minecraft:cramming", "minecraft:drown", "minecraft:starve", "minecraft:cactus", "minecraft:sweet_berry_bush", "minecraft:freeze", "minecraft:dry_out",
                "minecraft:wither", "minecraft:magic", "minecraft:dragon_breath", "minecraft:outside_border", "minecraft:ender_pearl", "minecraft:player_attack",
                "minecraft:mob_attack", "minecraft:arrow", "minecraft:trident", "minecraft:fireball", "minecraft:explosion", "minecraft:player_explosion",
                "minecraft:lightning_bolt", "minecraft:thrown", "minecraft:indirect_magic", "minecraft:thorns", "minecraft:sonic_boom", "minecraft:mob_projectile",
                "minecraft:generic", "minecraft:out_of_world", "minecraft:fall", "minecraft:fly_into_wall", "minecraft:falling_anvil", "some_mod:custom", "", null);
        // Fire, lava, drowning, starvation, wither, freezing, dragon breath, the void and every direct hit are damage: accepted at any finite size, no per-hit cap.
        for (String id : everyDamage) for (double amount : new double[] {0, .001, .5, 1, 4, 20, 100, 4096, 4097, 1e5, 1e9, 1e19, 1e30, Float.MAX_VALUE, Double.MAX_VALUE})
            check(d(id, amount) == Decision.ALLOW, id + " passes at " + amount);
        // Erasure: the kill command is refused by default and allowed when the owner switches that guard off.
        check(d(EsperDamageRules.KILL, 1) == Decision.BLOCK_KILL && d(EsperDamageRules.KILL, Float.MAX_VALUE) == Decision.BLOCK_KILL && d(EsperDamageRules.KILL, 0) == Decision.BLOCK_KILL, "kill command refused by default");
        check(EsperDamageRules.decide(new Policy(true, false, true, 0.001), EsperDamageRules.KILL, Float.MAX_VALUE, false) == Decision.ALLOW, "kill command can be allowed by the owner");
        // Infinite damage is an overkill, never an ignored hit.
        for (String id : everyDamage) check(d(id, Double.POSITIVE_INFINITY) == Decision.ALLOW, "infinite damage passes as overkill: " + id);
        // Non-damage amounts are refused for every source.
        for (String id : everyDamage) for (double bad : new double[] {Double.NaN, -1, -1e30, Double.NEGATIVE_INFINITY})
            check(d(id, bad) == Decision.BLOCK_INVALID, "invalid amount refused: " + id + " " + bad);
        // Exact skill hits between espers are never refused by this guard, whatever their size.
        for (String id : List.of("minecraft:player_attack", "minecraft:magic", "minecraft:generic", EsperDamageRules.KILL))
            for (double amount : new double[] {0, 1, 1e12, 1e20, Double.POSITIVE_INFINITY, Double.NaN})
                check(EsperDamageRules.decide(Policy.DEFAULT, id, amount, true) == Decision.ALLOW, "exact skill damage is never limited here: " + id + " " + amount);
        // Disabled policy and missing policy allow everything.
        check(EsperDamageRules.decide(new Policy(false, true, true, 0.001), EsperDamageRules.KILL, 1, false) == Decision.ALLOW && EsperDamageRules.decide(null, EsperDamageRules.KILL, 1, false) == Decision.ALLOW, "guard can be switched off");
        // Forced death: refused exactly when a living ledger exists.
        check(EsperDamageRules.forcedDeath(Policy.DEFAULT, true, false), "death behind a living ledger is forced");
        check(!EsperDamageRules.forcedDeath(Policy.DEFAULT, true, true), "death out of a spent ledger is legitimate");
        check(!EsperDamageRules.forcedDeath(Policy.DEFAULT, false, false) && !EsperDamageRules.forcedDeath(Policy.DEFAULT, false, true), "non-enrolled entities are not governed");
        check(!EsperDamageRules.forcedDeath(new Policy(true, true, false, 0.001), true, false) && !EsperDamageRules.forcedDeath(new Policy(false, true, true, 0.001), true, false) && !EsperDamageRules.forcedDeath(null, true, false), "forced-death guard is switchable");
        // The rules are a pure function of the victim's incoming damage and never reduce what an esper deals.
        Random r = new Random(7);
        for (int i = 0; i < 20000; i++) {
            double amount = r.nextBoolean() ? r.nextDouble() * 6000 : Math.pow(10, r.nextDouble() * 40 - 5);
            String id = everyDamage.get(r.nextInt(everyDamage.size()));
            check(d(id, amount) == Decision.ALLOW, "finite non-negative damage always passes: " + id + " " + amount);
        }
        // Environmental scaling: world damage becomes a level-scaled threat, a pure function of the hit and the level constant (never of current health).
        var unit = new java.math.BigDecimal("1e7");
        for (String id : List.of("minecraft:lava", "minecraft:in_fire", "minecraft:on_fire", "minecraft:drown", "minecraft:starve", "minecraft:fall", "minecraft:out_of_world", "minecraft:wither", "minecraft:freeze", "minecraft:cactus"))
            check(Math.abs(EsperDamageRules.environmentalAmount(Policy.DEFAULT, id, 4, unit) - 4 * 1e7 * .001) < 1e-6, id + " scales by the level constant");
        for (String id : List.of("minecraft:player_attack", "minecraft:mob_attack", "minecraft:arrow", "minecraft:explosion", "minecraft:lightning_bolt", "minecraft:magic", "minecraft:generic", EsperDamageRules.KILL, "some_mod:custom", ""))
            check(EsperDamageRules.environmentalAmount(Policy.DEFAULT, id, 4, unit) == 4, id + " is not environmental and stays as it is");
        check(EsperDamageRules.environmentalAmount(Policy.DEFAULT, "minecraft:lava", 4, new java.math.BigDecimal("1")) == 4, "scaling never reduces a hit below its native amount");
        check(EsperDamageRules.environmentalAmount(new Policy(true, true, true, 0), "minecraft:lava", 4, unit) == 4 && EsperDamageRules.environmentalAmount(new Policy(false, true, true, .001), "minecraft:lava", 4, unit) == 4 && EsperDamageRules.environmentalAmount(null, "minecraft:lava", 4, unit) == 4, "scaling is switchable");
        check(EsperDamageRules.environmentalAmount(Policy.DEFAULT, "minecraft:lava", 4, new java.math.BigDecimal("5e18")) == 4 * 5e18 * .001, "LV5 unit");
        check(EsperDamageRules.environmentalAmount(Policy.DEFAULT, "minecraft:lava", 1e15, new java.math.BigDecimal("5e18")) == 1e20, "scaled amount is held at the per-hit ceiling");
        for (double bad : new double[] {Double.NaN, -1, 0, Double.POSITIVE_INFINITY}) { double out = EsperDamageRules.environmentalAmount(Policy.DEFAULT, "minecraft:lava", bad, unit); check(Double.isNaN(bad) ? Double.isNaN(out) : out == bad, "invalid amounts pass through for the invalid-amount rule: " + bad); }
        // Same hit, same cost: the result depends only on the hit and the unit, so health at the time of the hit cannot matter.
        check(EsperDamageRules.environmentalAmount(Policy.DEFAULT, "minecraft:lava", 4, unit) == EsperDamageRules.environmentalAmount(Policy.DEFAULT, "minecraft:lava", 4, unit), "deterministic");
        // At the default share a full-health esper lasts about 40 minutes in lava (8 points per second) at any level.
        for (String level : new String[] {"1e7", "1e10", "1e13", "1e16", "5e18"}) {
            var d = new java.math.BigDecimal(level); double perSecond = EsperDamageRules.environmentalAmount(Policy.DEFAULT, "minecraft:lava", 8, d), seconds = d.doubleValue() * 20 / perSecond;
            check(seconds > 1500 && seconds < 3500, "lava survival is minutes at every level: " + level + " -> " + seconds + " s");
        }
        System.out.println("PASS " + checks + " esper damage rules: all damage passes uncapped, kill command and non-damage amounts refused, forced death detected");
    }
}

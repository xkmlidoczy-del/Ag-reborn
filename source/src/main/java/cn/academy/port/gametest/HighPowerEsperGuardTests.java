package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.NewSkillBudget;
import cn.academy.port.preset.PresetSkills;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.minecraft.world.damagesource.DamageSource;
import cn.academy.port.skill.LightShield;
import cn.academy.port.skill.VecDeviation;
import cn.academy.port.skill.VecReflection;

/** The esper damage guard against a real enrolled player on a real server: what is refused, what still lands, and the exact ledger effect. */
@GameTestHolder("academy_esper_guard")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerEsperGuardTests {
    private static final String TEMPLATE = "esper_guard_empty", BATCH = "esper_guard";
    private HighPowerEsperGuardTests() {}

    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int dimension : new int[] {48, 12, 48}) size.add(IntTag.valueOf(dimension));
        for (int axis = 0; axis < 3; axis++) position.add(IntTag.valueOf(0));
        cell.put("pos", position); cell.putInt("state", 0); blocks.add(cell); air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_esper_guard", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    private static java.math.BigDecimal health(LivingEntity p) { return new java.math.BigDecimal(HighPowerCombat.healthText(p).replace(",", "")); }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void environmental_damage_is_level_scaled_and_other_damage_is_absolute(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PULSE_SHIELD); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            var sources = h.getLevel().damageSources();
            h.assertTrue(HighPowerCombat.enabled(p) && health(p).signum() > 0, "enrolled with a positive ledger");
            Object[][] rows = {{"starve", sources.starve()}, {"wither", sources.wither()}, {"magic", sources.magic()}, {"drown", sources.drown()}, {"lava", sources.lava()},
                    {"in_fire", sources.inFire()}, {"on_fire", sources.onFire()}, {"cactus", sources.cactus()}, {"freeze", sources.freeze()}, {"in_wall", sources.inWall()},
                    {"cramming", sources.cramming()}, {"dry_out", sources.dryOut()}, {"hot_floor", sources.hotFloor()}, {"dragon_breath", sources.dragonBreath()},
                    {"void", sources.fellOutOfWorld()}};
            var unit = HighPowerGrowth.values(p).damage();
            for (Object[] row : rows) {
                p.invulnerableTime = 0;
                var before = health(p);
                p.hurt((net.minecraft.world.damagesource.DamageSource) row[1], 5);
                var lost = before.subtract(health(p));
                String type = ((net.minecraft.world.damagesource.DamageSource) row[1]).typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("");
                if (cn.academy.port.highpower.core.EsperDamageRules.environmental(type)) {
                    // World damage is a level-scaled threat: native amount times D times the share, a constant of the level, not of current health.
                    var expected = unit.multiply(new BigDecimal("0.005"));
                    h.assertTrue(lost.subtract(expected).abs().compareTo(expected.multiply(new BigDecimal("0.001"))) <= 0, row[0] + " hurts by its level-scaled amount: lost " + lost + " expected " + expected);
                } else {
                    h.assertTrue(lost.signum() > 0 && lost.compareTo(BigDecimal.valueOf(5)) <= 0, row[0] + " is ordinary direct damage by its absolute amount: lost " + lost + " of " + before);
                }
                h.assertTrue(p.isAlive(), row[0] + " does not kill a healthy esper");
            }
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void large_direct_hits_are_accepted_by_their_amount_and_nan_does_nothing(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.RAIL_RELAY); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            var sources = h.getLevel().damageSources();
            var before = health(p);
            h.assertTrue(before.compareTo(BigDecimal.valueOf(HighPowerCombat.MAX_NATIVE_WITNESS).multiply(BigDecimal.valueOf(2))) > 0, "ledger large enough: " + before);
            p.invulnerableTime = 0; p.hurt(sources.generic(), HighPowerCombat.MAX_NATIVE_WITNESS * 2);
            var lost = before.subtract(health(p));
            h.assertTrue(lost.compareTo(BigDecimal.valueOf(HighPowerCombat.MAX_NATIVE_WITNESS).multiply(new BigDecimal("1.98"))) >= 0 && lost.compareTo(BigDecimal.valueOf(HighPowerCombat.MAX_NATIVE_WITNESS).multiply(BigDecimal.valueOf(2))) <= 0, "a hit above the native witness ceiling is accepted without any 4096 limit: lost " + lost);
            var after = health(p);
            p.invulnerableTime = 0; p.hurt(sources.generic(), Float.NaN);
            h.assertTrue(health(p).compareTo(after) == 0 && p.isAlive(), "NaN is not damage and does nothing");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void a_hit_beyond_the_ledger_ceiling_defeats_instead_of_being_ignored(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PULSE_SHIELD); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            p.invulnerableTime = 0; p.hurt(h.getLevel().damageSources().generic(), Float.MAX_VALUE);
            h.assertTrue(health(p).signum() == 0 && !p.isAlive(), "Float.MAX_VALUE (3e38, far above the 1e20 ceiling) defeats the esper: " + health(p) + " alive=" + p.isAlive());
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void an_infinite_hit_defeats_instead_of_being_ignored(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PULSE_SHIELD); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            p.invulnerableTime = 0; p.hurt(h.getLevel().damageSources().generic(), Float.POSITIVE_INFINITY);
            h.assertTrue(health(p).signum() == 0 && !p.isAlive(), "an infinite hit defeats the esper: " + health(p) + " alive=" + p.isAlive());
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void a_skill_hit_beyond_the_ceiling_lands_as_an_overkill(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PULSE_SHIELD); ServerPlayer victim = f.player(10, 10);
        f.ready(() -> {
            var attacker = new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[AC-OK]"));
            f.entities.add(attacker); Vec3 at = h.absoluteVec(new Vec3(14.5, 2, 14.5)); attacker.moveTo(at.x, at.y, at.z, 0, 0); attacker.setNoGravity(true);
            var ability = AbilityStorage.get(attacker); ability.changeCategoryClassic("electromaster"); ability.setLevel(5);
            h.assertTrue(HighPowerCombat.enroll(attacker, 5), "attacker enrolled");
            var server = h.getLevel().getServer(); boolean pvp = server.isPvpAllowed(); server.setPvpAllowed(true);
            try {
                victim.invulnerableTime = 0;
                h.assertTrue(HighPowerCombat.attack(attacker, "electromaster.forked_arc", victim, new BigDecimal("1e30")), "a 1e30 skill hit is accepted, not refused");
                h.assertTrue(health(victim).signum() == 0 && !victim.isAlive(), "and defeats the victim: " + health(victim));
            } finally { server.setPvpAllowed(pvp); }
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void erasure_attempts_never_kill_an_esper_whose_ledger_is_alive(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PULSE_SHIELD); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            var sources = h.getLevel().damageSources();
            var before = health(p);
            p.invulnerableTime = 0; p.hurt(sources.genericKill(), 5);
            h.assertTrue(health(p).compareTo(before) == 0 && p.isAlive(), "the kill command is refused");
            p.kill();
            h.assertTrue(health(p).compareTo(before) == 0 && p.isAlive(), "Entity.kill() cannot erase an esper");
            int[] deaths = {0, 0};
            f.hook(new Object() { @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST, receiveCanceled = true)
                public void seen(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e) { if (e.getEntity() == p) { deaths[0]++; if (e.isCanceled()) deaths[1]++; } } });
            p.die(sources.generic());
            h.assertTrue(deaths[0] == 1 && deaths[1] == 1, "the direct die() call raised one death event and the guard vetoed it: seen=" + deaths[0] + " vetoed=" + deaths[1]);
            h.assertTrue(health(p).compareTo(before) == 0 && !p.isDeadOrDying() && p.getHealth() > 0 && p.getPose() != Pose.DYING, "a direct die() call is vetoed (no dying pose) and native health restored: pose=" + p.getPose() + " hp=" + p.getHealth() + " dead=" + p.isDeadOrDying());
            p.setHealth(0);
            p.doTick();
            h.assertTrue(health(p).compareTo(before) == 0 && !p.isDeadOrDying() && p.getHealth() > 0 && p.isAlive(), "forced native health 0 is repaired from the ledger before vanilla acts on it");
            p.setHealth(0); p.invulnerableTime = 0; p.hurt(sources.generic(), 1); p.doTick();
            h.assertTrue(p.isAlive() && !p.isDeadOrDying() && health(p).signum() > 0, "a hit landing on a tampered body in the same tick still ends with a living, repaired esper");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void a_spent_ledger_still_kills_the_esper(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PULSE_SHIELD); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            var before = health(p);
            var lethal = before.multiply(BigDecimal.valueOf(2));
            h.assertTrue(lethal.compareTo(new BigDecimal("100000000000000000000")) <= 0, "fixture ledger leaves room for a lethal hit: " + before);
            p.invulnerableTime = 0; p.hurt(h.getLevel().damageSources().generic(), lethal.floatValue());
            h.assertTrue(health(p).signum() == 0 && !p.isAlive(), "damage that spends the whole ledger kills: " + health(p) + " alive=" + p.isAlive());
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void direct_hits_are_still_accounted_by_their_absolute_amount_not_a_percentage(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PULSE_SHIELD); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            var sources = h.getLevel().damageSources();
            var zombie = (LivingEntity) f.spawn(EntityType.ZOMBIE, 3);
            var before = health(p);
            p.invulnerableTime = 0; p.hurt(sources.mobAttack(zombie), 6);
            var after = health(p);
            var lost = before.subtract(after);
            h.assertTrue(lost.signum() > 0, "a direct mob hit lands");
            h.assertTrue(lost.compareTo(java.math.BigDecimal.valueOf(6)) <= 0, "and removes an absolute amount, never a share of the ledger: " + lost + " of " + before);
            h.assertTrue(lost.multiply(java.math.BigDecimal.valueOf(1000)).compareTo(before) < 0, "far below one thousandth of the pool, not a fraction of it");
            p.invulnerableTime = 0; p.hurt(sources.fall(), 3);
            h.assertTrue(health(p).compareTo(after) < 0, "a fall (kinetic) is still accounted");
            p.invulnerableTime = 0; p.hurt(sources.lightningBolt(), 3);
            h.assertTrue(p.isAlive(), "lightning stays an ordinary direct source");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void esper_skill_damage_is_never_limited_by_the_guard_or_the_witness_cap(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.RAIL_RELAY); ServerPlayer victim = f.player(10, 10);
        f.ready(() -> {
            var attacker = new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[AC-EG]"));
            f.entities.add(attacker); Vec3 at = h.absoluteVec(new Vec3(14.5, 2, 14.5)); attacker.moveTo(at.x, at.y, at.z, 0, 0); attacker.setNoGravity(true);
            var ability = AbilityStorage.get(attacker); ability.changeCategoryClassic("electromaster"); ability.setLevel(5);
            h.assertTrue(HighPowerCombat.enroll(attacker, 5), "attacker enrolled: " + HighPowerCombat.unsupportedReason(attacker));
            var server = h.getLevel().getServer(); boolean pvp = server.isPvpAllowed(); server.setPvpAllowed(true);
            try {
            var before = health(victim);
            // Far above both the ordinary-hit cap (4096) and the native float witness (1e6): the exact ledger must receive every point.
            h.assertTrue(before.compareTo(BigDecimal.valueOf(HighPowerCombat.MAX_NATIVE_WITNESS).multiply(BigDecimal.valueOf(2))) > 0, "ledger is large enough to probe past the native witness: " + before);
            var amounts = new BigDecimal[] {new BigDecimal("5000"), new BigDecimal("2000000"), before.divide(BigDecimal.valueOf(4), 0, java.math.RoundingMode.DOWN)};
            for (var raw : amounts) {
                victim.invulnerableTime = 0;
                var expected = health(victim).subtract(raw);
                h.assertTrue(HighPowerCombat.attack(attacker, "electromaster.forked_arc", victim, raw), "exact esper skill hit lands: " + raw);
                h.assertTrue(health(victim).compareTo(expected) == 0, "no limiter clips " + raw + ": " + health(victim) + " vs " + expected);
            }
            } finally { server.setPvpAllowed(pvp); }
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void meltdowner_overload_push_pays_a_bounded_bonus_without_breaking_the_original_receipt(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.REVERSE_JET); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            var state = AbilityStorage.get(p);
            float amount = 0.0005f;
            double perAward = cn.academy.port.AcademyConfig.consumptionConfig().progress(state.category, amount);
            // Calm: no extra beyond the award itself.
            state.overload = 0; double calmStart = state.levelExperience;
            state.addExperience(NewSkillRuntime.Kind.REVERSE_JET.id, amount); p.doTick(); p.doTick();
            double calm = state.levelExperience - calmStart;
            h.assertTrue(Math.abs(calm - perAward) < perAward * 1e-3 + 1e-9, "calm cast earns exactly the plain award: " + calm + " vs " + perAward);
            // Overload edge: +60% arrives on the next tick, once, outside the original award.
            state.overload = state.maxOverload(); double start = state.levelExperience;
            state.addExperience(NewSkillRuntime.Kind.REVERSE_JET.id, amount);
            double immediate = state.levelExperience - start;
            h.assertTrue(Math.abs(immediate - perAward) < perAward * 1e-3 + 1e-9, "the award itself is untouched: " + immediate + " vs " + perAward);
            p.doTick(); p.doTick(); p.doTick();
            double total = state.levelExperience - start;
            h.assertTrue(Math.abs(total - perAward * 1.6) < perAward * 2e-3 + 1e-9, "edge of overload pays +60%, once: " + total + " vs " + perAward * 1.6);
            h.assertTrue(HighPowerCombat.enabled(p) && health(p).signum() > 0, "the esper remains a valid enrolled combatant after a bonus grant");
            h.succeed();
        });
    }

    /** Plain per-award progress of a calm {@code amount} award for the player's category (the style bonus is always a fraction of this). */
    private static double plain(ServerPlayer p, float amount) { return cn.academy.port.AcademyConfig.consumptionConfig().progress(AbilityStorage.get(p).category, amount); }
    private static void near(GameTestHelper h, double actual, double expected, double per, String message) {
        h.assertTrue(Math.abs(actual - expected) <= per * 2e-3 + 1e-9, message + ": " + actual + " vs " + expected + " (plain award " + per + ")");
    }
    /** One award and its measured gain after the style's next-tick bonus grant: only valid when no other award lands in the same window. */
    private static double award(ServerPlayer p, String skill, float amount) {
        var state = AbilityStorage.get(p); double start = state.levelExperience;
        state.addExperience(skill, amount); p.doTick(); p.doTick();
        return state.levelExperience - start;
    }
    private static EsperGrowthHintPayload.Hint lastHint(Fixture f, ServerPlayer p) {
        EmbeddedChannel channel = f.channels.get(p); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks();
        EsperGrowthHintPayload.Hint found = null; Object packet;
        while ((packet = channel.readOutbound()) != null) {
            if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom && custom.payload() instanceof EsperGrowthHintPayload.Hint hint) found = hint;
            if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId()));
            ReferenceCountUtil.release(packet);
        }
        return found;
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 500)
    public static void electromaster_resonance_climbs_with_sustained_casting_is_synced_to_the_hud_and_resets_when_idle(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PULSE_SHIELD); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            float amount = 0.0005f; double per = plain(p, amount); double[] gains = new double[4];
            h.assertTrue(HighPowerCategories.isElectromaster(AbilityStorage.get(p).category) && per > 0, "an electromaster with a positive plain award");
            lastHint(f, p);
            resonance(h, f, p, 0, amount, per, gains);
        });
    }
    private static void resonance(GameTestHelper h, Fixture f, ServerPlayer p, int i, float amount, double per, double[] gains) {
        var state = AbilityStorage.get(p); double start = state.levelExperience;
        state.addExperience(NewSkillRuntime.Kind.PULSE_SHIELD.id, amount);
        h.runAfterDelay(3, () -> {
            gains[i] = state.levelExperience - start;
            if (i < 3) { h.runAfterDelay(9, () -> resonance(h, f, p, i + 1, amount, per, gains)); return; }
            // +10% per half-second step of unbroken casting; the first cast of a chain earns only the plain award.
            for (int step = 0; step < 4; step++) near(h, gains[step], per * (1 + .1 * step), per, "cast " + step + " of a chain pays +" + 10 * step + "%");
            h.runAfterDelay(8, () -> {
                var hint = lastHint(f, p);
                h.assertTrue(hint != null, "the growth hint reached the player's connection");
                h.assertTrue(hint.owner().equals(p.getUUID()) && hint.hint().category().equals("electromaster") && hint.hint().count() == 3 && hint.hint().percent() == 30,
                        "the HUD is told 3 stacks / +30%: " + hint);
                // Idle for over 5 s: the chain is gone, the next cast is plain again and the hint falls back to zero.
                h.runAfterDelay(IDLE_SPELL_TICKS, () -> {
                    h.assertTrue(HighPowerCategories.active(state) && HighPowerCombat.enabled(p), "still enrolled after the idle spell");
                    near(h, award(p, NewSkillRuntime.Kind.PULSE_SHIELD.id, amount), per, per, "after 5 s idle the chain restarts at the plain award");
                    var after = lastHint(f, p);
                    h.assertTrue(after == null || after.hint().count() == 0 && after.hint().percent() == 0, "no stale stacks are advertised after an idle reset: " + after);
                    h.succeed();
                });
            });
        });
    }
    private static final int IDLE_SPELL_TICKS = cn.academy.port.highpower.core.EsperGrowthStyle.RESONANCE_IDLE_TICKS + 10;

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void teleporter_variety_pays_only_fresh_skills_and_counts_them_for_the_hud(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PHASE_CORRIDOR); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            float amount = 0.0005f; double per = plain(p, amount);
            String a = NewSkillRuntime.Kind.PHASE_CORRIDOR.id, b = NewSkillRuntime.Kind.INCIDENT_BYPASS.id;
            h.assertTrue(HighPowerCategories.active(AbilityStorage.get(p)) && "teleporter".equals(AbilityStorage.get(p).category) && per > 0, "a teleporter with a positive plain award");
            lastHint(f, p);
            near(h, award(p, a, amount), per * 1.5, per, "first use of a skill pays +50%");
            near(h, award(p, a, amount), per, per, "repeating the same skill pays nothing extra");
            near(h, award(p, a, amount), per, per, "and spamming it still does not");
            near(h, award(p, b, amount), per * 1.5, per, "a different skill is fresh again: +50%");
            h.runAfterDelay(8, () -> {
                var hint = lastHint(f, p);
                h.assertTrue(hint != null && hint.hint().category().equals("teleporter") && hint.hint().count() == 2 && hint.hint().percent() == 50,
                        "the HUD is told 2 skills in the window, +50% for a new one: " + hint);
                h.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void vector_flow_reading_pays_for_speed_not_for_standing_still_or_being_teleported(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.IMPULSE_SPLIT); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            float amount = 0.0005f; double per = plain(p, amount); String skill = NewSkillRuntime.Kind.IMPULSE_SPLIT.id;
            h.assertTrue(HighPowerCategories.active(AbilityStorage.get(p)) && "vecmanip".equals(AbilityStorage.get(p).category) && per > 0, "a vector manipulator with a positive plain award");
            near(h, award(p, skill, amount), per, per, "standing still earns the plain award");
            // Half a block per tick for 40 ticks is the style's full-speed reference: the smoothed flow saturates near +50%.
            Vec3 at = h.absoluteVec(new Vec3(10.5, 2, 10.5));
            for (int i = 1; i <= 40; i++) { p.moveTo(at.x, at.y, at.z + .5 * i, 0, 0); p.doTick(); }
            double fast = award(p, skill, amount);
            h.assertTrue(fast > per * 1.45 && fast <= per * 1.5 * (1 + 2e-3), "casting at full flow pays about +50%, never more: " + fast / per);
            // Slow drift pays proportionally less than a sprint.
            for (int i = 0; i < 40; i++) p.doTick();
            near(h, award(p, skill, amount), per, per, "after coming to rest the flow bonus is gone");
            for (int i = 1; i <= 40; i++) { p.moveTo(at.x + .1 * i, at.y, at.z + 22, 0, 0); p.doTick(); }
            double slow = award(p, skill, amount);
            h.assertTrue(slow > per * 1.05 && slow < fast, "a slow drift pays something, but less than full flow: " + slow / per + " vs " + fast / per);
            // A teleport is not flow: build up speed again, then jump 20 blocks; the jump is ignored and the smoothing dropped.
            for (int i = 1; i <= 40; i++) { p.moveTo(at.x, at.y, at.z + .5 * i, 0, 0); p.doTick(); }
            p.moveTo(at.x, at.y, at.z, 0, 0); p.doTick();
            near(h, award(p, skill, amount), per, per, "a long jump is not flow");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void meltdowner_damage_unit_is_boosted_while_hp_and_shield_stay_on_the_shared_curve(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.REVERSE_JET); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            var values = HighPowerGrowth.values(p);
            var shared = cn.academy.port.highpower.core.ContinuousHighPowerGrowth.sample(values.position());
            h.assertTrue(values.damage().compareTo(shared.damage().multiply(new BigDecimal("1.5"))) == 0, "meltdowner deals 1.5 of the shared unit: " + values.damage() + " vs " + shared.damage());
            h.assertTrue(health(p).compareTo(shared.maxHealth()) == 0, "its full health pool is the shared one: " + health(p) + " vs " + shared.maxHealth());
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void classic_skill_damage_of_an_esper_is_lifted_onto_the_level_unit(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PULSE_SHIELD); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            var unit = HighPowerGrowth.values(p).damage();
            var target = (LivingEntity) f.spawn(EntityType.VILLAGER, 3); target.setNoGravity(true);
            h.assertTrue(HighPowerCombat.enroll(target, 5), "target enrolled");
            var before = health(target);
            h.assertTrue(cn.academy.port.AbilityDamage.attack(p, "electromaster.railgun", target, 110), "classic railgun hit lands");
            var lost = before.subtract(health(target));
            var expected = unit.multiply(BigDecimal.valueOf(110)).divide(BigDecimal.valueOf(50), java.math.MathContext.DECIMAL64);
            h.assertTrue(lost.subtract(expected).abs().compareTo(expected.multiply(new BigDecimal("0.001"))) <= 0, "a 110-point classic hit becomes 2.2 D: lost " + lost + " expected " + expected);
            // A high-power skill id keeps its own closed budget: the lift never touches it.
            target.invulnerableTime = 0; var mid = health(target);
            cn.academy.port.AbilityDamage.attack(p, "electromaster.forked_arc", target, 110);
            var plain = mid.subtract(health(target));
            h.assertTrue(plain.compareTo(BigDecimal.valueOf(110)) <= 0, "a high-power skill id is not lifted: lost " + plain);
            h.succeed();
        });
    }

    private static BigDecimal shield(LivingEntity p) { return new BigDecimal(HighPowerCombat.shieldText(p).replace(",", "")); }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void category_shield_takes_ordinary_hits_before_health_and_holds_super_armor_until_it_breaks(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PULSE_SHIELD, true); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            var sources = h.getLevel().damageSources();
            var hp0 = health(p); var s0 = shield(p); var hit = s0.divide(BigDecimal.TWO);
            h.assertTrue(s0.compareTo(hit) > 0, "the esper starts with a real shield: " + s0);
            p.invulnerableTime = 0; p.hurt(sources.generic(), hit.floatValue());
            h.assertTrue(health(p).compareTo(hp0) == 0 && s0.subtract(shield(p)).compareTo(hit) == 0, "the shield takes the whole hit, health untouched: shield " + shield(p) + " health " + health(p));
            p.setDeltaMovement(Vec3.ZERO); p.knockback(2, 1, 0);
            h.assertTrue(p.getDeltaMovement().lengthSqr() < 1e-9, "super armor: no knockback while shield is left");
            f.setShield(p, "10");
            p.invulnerableTime = 0; p.hurt(sources.generic(), hit.floatValue());
            h.assertTrue(shield(p).signum() == 0 && hp0.subtract(health(p)).compareTo(hit.subtract(BigDecimal.TEN)) == 0, "a breaking hit drains the rest of the shield first and only the remainder reaches health: shield " + shield(p) + " lost " + hp0.subtract(health(p)));
            p.setDeltaMovement(Vec3.ZERO); p.knockback(2, 1, 0);
            h.assertTrue(p.getDeltaMovement().lengthSqr() > 1e-4, "without shield the esper is knocked back again");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void every_category_has_the_shield_and_the_teleporter_veil_now_holds_the_whole_hit(GameTestHelper h) {
        Fixture tp = new Fixture(h, NewSkillRuntime.Kind.INCIDENT_BYPASS, true); ServerPlayer t = tp.player(10, 10);
        Fixture md = new Fixture(h, NewSkillRuntime.Kind.REVERSE_JET, true); ServerPlayer m = md.player(30, 10);
        Fixture vm = new Fixture(h, NewSkillRuntime.Kind.IMPULSE_SPLIT, true); ServerPlayer v = vm.player(10, 30);
        tp.ready(() -> md.ready(() -> vm.ready(() -> {
            var sources = h.getLevel().damageSources();
            for (ServerPlayer p : List.of(t, m, v)) {
                var hp0 = health(p); var s0 = shield(p); var hit = s0.divide(BigDecimal.valueOf(4));
                p.invulnerableTime = 0; p.hurt(sources.generic(), hit.floatValue());
                h.assertTrue(health(p).compareTo(hp0) == 0 && s0.subtract(shield(p)).compareTo(hit) == 0,
                        AbilityStorage.get(p).category + ": the shield takes the whole hit: shield " + s0 + " -> " + shield(p));
            }
            h.succeed();
        })));
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void electromaster_barrier_drinks_lightning_and_shocks_melee_attackers(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PULSE_SHIELD, true); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            var sources = h.getLevel().damageSources();
            f.setShield(p, "0");
            var hp0 = health(p);
            p.invulnerableTime = 0; p.hurt(sources.lightningBolt(), 5);
            var max = new BigDecimal(HighPowerCombat.maxShieldText(p).replace(",", ""));
            h.assertTrue(health(p).compareTo(hp0) == 0 && shield(p).compareTo(max.multiply(EsperShields.LIGHTNING_RECHARGE)) == 0, "lightning recharges a quarter of the barrier instead of hurting: " + shield(p) + " of " + max);
            var zombie = (net.minecraft.world.entity.monster.Zombie) f.spawn(EntityType.ZOMBIE, 1.5);
            zombie.setNoAi(true); float zh = zombie.getHealth();
            p.invulnerableTime = 0; p.hurt(sources.mobAttack(zombie), 3);
            h.runAfterDelay(2, () -> {
                h.assertTrue(zombie.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN) && (zombie.getHealth() < zh || !zombie.isAlive()), "the melee attacker is shocked and numbed");
                h.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void meltdowner_shell_vaporises_projectiles_and_vector_field_reflects_them(GameTestHelper h) {
        Fixture md = new Fixture(h, NewSkillRuntime.Kind.REVERSE_JET, true); ServerPlayer m = md.player(10, 10);
        Fixture vm = new Fixture(h, NewSkillRuntime.Kind.IMPULSE_SPLIT, true); ServerPlayer v = vm.player(30, 10);
        md.ready(() -> vm.ready(() -> {
            var shooter = (LivingEntity) md.spawn(EntityType.SKELETON, 6);
            for (ServerPlayer p : List.of(m, v)) {
                var arrow = new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, h.getLevel());
                arrow.setOwner(shooter); arrow.setPos(p.getX(), p.getY() + 1, p.getZ() + 1); arrow.setDeltaMovement(0, 0, -1.5);
                h.getLevel().addFreshEntity(arrow); md.entities.add(arrow);
                var s0 = shield(p); var max = new BigDecimal(HighPowerCombat.maxShieldText(p).replace(",", ""));
                var event = new net.neoforged.neoforge.event.entity.ProjectileImpactEvent(arrow, new net.minecraft.world.phys.EntityHitResult(p));
                NeoForge.EVENT_BUS.post(event);
                h.assertTrue(event.isCanceled() && s0.subtract(shield(p)).compareTo(max.multiply(EsperShields.PROJECTILE_COST)) == 0, AbilityStorage.get(p).category + ": the projectile is stopped for its shield cost");
                if (p == m) h.assertTrue(arrow.isRemoved(), "the meltdowner shell vaporises the arrow");
                else h.assertTrue(arrow.getOwner() == v && arrow.getDeltaMovement().z > 0, "the vector field turns the arrow back, now owned by the esper");
            }
            h.succeed();
        }));
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void passive_category_shields_recover_without_an_advanced_defense_and_do_not_chain_counters(GameTestHelper h) {
        Fixture em = new Fixture(h, NewSkillRuntime.Kind.PULSE_SHIELD, true);
        Fixture tp = new Fixture(h, NewSkillRuntime.Kind.INCIDENT_BYPASS, true);
        Fixture vm = new Fixture(h, NewSkillRuntime.Kind.IMPULSE_SPLIT, true);
        ServerPlayer e = em.player(10,10), t = tp.player(30,10), v = vm.player(10,30), other = em.player(12,10);
        em.ready(() -> {
            for (ServerPlayer p : List.of(e,t,v)) {
                em.setShield(p,"10");
                h.assertTrue(EsperShields.holding(p) && HighPowerCombat.shieldActive(p), "base shield advertises super armor without a paid defensive skill");
            }
            h.runAfterDelay(65, () -> {
                for (ServerPlayer p : List.of(e,t,v)) h.assertTrue(shield(p).compareTo(BigDecimal.TEN)>0,
                        AbilityStorage.get(p).category+": passive shield recovers after 60 peaceful ticks, without requiring an advanced defense");
                // Two adjacent shielded electromasters must not create a new retaliation every server tick.
                em.place(e); em.place(other);
                var server=h.getLevel().getServer(); boolean pvp=server.isPvpAllowed();server.setPvpAllowed(true);
                double[] first = {0}; var otherShieldBefore = shield(other);
                h.testInfo.addListener(new GameTestListener(){
                    public void testStructureLoaded(GameTestInfo i){}
                    public void testPassed(GameTestInfo i,GameTestRunner r){server.setPvpAllowed(pvp);}
                    public void testFailed(GameTestInfo i,GameTestRunner r){server.setPvpAllowed(pvp);}
                    public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){server.setPvpAllowed(pvp);}
                });
                e.invulnerableTime=0; other.invulnerableTime=0;
                e.hurt(h.getLevel().damageSources().playerAttack(other), 2);
                h.runAfterDelay(3, () -> {
                    h.assertTrue(shield(other).compareTo(otherShieldBefore)<0, "the initial melee hit caused a real single shield counter");
                    server.setPvpAllowed(pvp);
                    first[0]=shield(e).doubleValue()+shield(other).doubleValue();
                    h.runAfterDelay(8, () -> {
                        h.assertTrue(shield(e).doubleValue()+shield(other).doubleValue()==first[0], "one shield counter never creates a chain of automatic counters");
                        h.succeed();
                    });
                });
            });
        });
    }

    // Global classic incoming handlers must run in isolated batches, not alongside unrelated shield fixtures.
    @GameTest(template=TEMPLATE,batch="esper_legacy_light_self",timeoutTicks=150)
    public static void legacy_light_shield_remaining_native_damage_still_spends_base_shield_before_health(GameTestHelper h){legacyDefenseMatrix(h,LegacyDefense.LIGHT,false);}
    @GameTest(template=TEMPLATE,batch="esper_legacy_deviation_self",timeoutTicks=150)
    public static void legacy_vector_deviation_remaining_native_damage_still_spends_base_shield_before_health(GameTestHelper h){legacyDefenseMatrix(h,LegacyDefense.DEVIATION,false);}
    @GameTest(template=TEMPLATE,batch="esper_legacy_reflection_self",timeoutTicks=150)
    public static void legacy_vector_reflection_remaining_native_damage_still_spends_base_shield_before_health(GameTestHelper h){legacyDefenseMatrix(h,LegacyDefense.REFLECTION,false);}
    @GameTest(template=TEMPLATE,batch="esper_legacy_light_global",timeoutTicks=150)
    public static void another_players_legacy_light_shield_does_not_double_debit_or_bypass_base_shield(GameTestHelper h){legacyDefenseMatrix(h,LegacyDefense.LIGHT,true);}
    @GameTest(template=TEMPLATE,batch="esper_legacy_deviation_global",timeoutTicks=150)
    public static void another_players_legacy_deviation_does_not_double_debit_or_bypass_base_shield(GameTestHelper h){legacyDefenseMatrix(h,LegacyDefense.DEVIATION,true);}
    @GameTest(template=TEMPLATE,batch="esper_legacy_reflection_other",timeoutTicks=150)
    public static void another_players_legacy_reflection_does_not_grant_a_global_free_damage_reduction(GameTestHelper h){legacyDefenseMatrix(h,LegacyDefense.REFLECTION,true);}

    private enum LegacyDefense {
        LIGHT("light_shield",NewSkillRuntime.Kind.REVERSE_JET),
        DEVIATION("vec_deviation",NewSkillRuntime.Kind.IMPULSE_SPLIT),
        REFLECTION("vec_reflection",NewSkillRuntime.Kind.IMPULSE_SPLIT);
        final String id;final NewSkillRuntime.Kind kind;
        LegacyDefense(String id,NewSkillRuntime.Kind kind){this.id=id;this.kind=kind;}
        boolean start(ServerPlayer p){return switch(this){case LIGHT->LightShield.start(p);case DEVIATION->VecDeviation.start(p);case REFLECTION->VecReflection.start(p);};}
        boolean active(ServerPlayer p){return switch(this){case LIGHT->LightShield.active(p);case DEVIATION->VecDeviation.active(p);case REFLECTION->VecReflection.active(p);};}
    }
    private static void resetLegacy(ServerPlayer p){LightShield.remove(p);VecDeviation.remove(p);VecReflection.remove(p);}
    private static void prepareLegacy(ServerPlayer p,LegacyDefense defense){
        resetLegacy(p);var state=AbilityStorage.get(p);state.learn(defense.id);state.experience.put(defense.id,0d);
        state.extraOverload=cn.academy.port.core.ClassicRules.EXTRA_OVERLOAD_CAP[state.level];
        state.cp=state.maxCp();state.overload=0;state.activated=true;state.overloadFine=true;state.interfering=false;
        state.cooldowns.remove(defense.id);
    }
    private static void legacyDefenseMatrix(GameTestHelper h,LegacyDefense defense,boolean otherPlayer){
        Fixture f=new Fixture(h,defense.kind,true);ServerPlayer victim=f.player(10,10);
        ServerPlayer owner=otherPlayer?f.player(30,10):victim;
        // A second live global context proves the first owner alone pays and trains.
        ServerPlayer later=otherPlayer&&defense!=LegacyDefense.REFLECTION?f.player(30,30):null;
        var witness=new LegacyNativeDamageWitness(victim);f.hook(witness);
        f.ready(()->{
            for(boolean environment:new boolean[]{false,true})for(int phase=0;phase<3;phase++){
                prepareLegacy(owner,defense);if(later!=null)prepareLegacy(later,defense);
                h.assertTrue(defense.start(owner)&&defense.active(owner),"actual classic defense starts on its enrolled owner: "+defense);
                if(later!=null)h.assertTrue(defense.start(later),"second actual global context starts after the first");
                h.assertTrue(HighPowerCombat.unsupportedReason(owner).isPresent(),"exact high-power attacks retain their explicit unsupported-legacy boundary");
                BigDecimal amount=phase==0?new BigDecimal(HighPowerCombat.maxShieldText(victim)).divide(BigDecimal.valueOf(2)):phase==1?BigDecimal.ONE:BigDecimal.ZERO;
                f.setShield(victim,amount.toPlainString());var hpBefore=health(victim);var shieldBefore=shield(victim);
                var ownerShieldBefore=shield(owner);double cpBefore=AbilityStorage.get(owner).cp;
                double laterCp=later==null?0:AbilityStorage.get(later).cp,laterXp=later==null?0:AbilityStorage.get(later).exp(defense.id);
                DamageSource source;
                if(environment)source=victim.damageSources().cactus();
                else{
                    var mob=(net.minecraft.world.entity.monster.Zombie)f.spawn(EntityType.ZOMBIE,8);mob.setNoAi(true);mob.setNoGravity(true);
                    source=victim.damageSources().mobAttack(mob);
                }
                witness.watch(source);victim.invulnerableTime=0;
                h.assertTrue(victim.hurt(source,80),"ordinary native hurt remains accepted while the exact adapter is refused: "+defense+" environment="+environment+" phase="+phase);
                h.assertTrue(witness.early==1&&witness.late==1&&witness.posts==1,"one genuine incoming and Post, never a synthetic or duplicate settlement");
                h.assertTrue(witness.before>0&&Float.isFinite(witness.before),"real positive native amount reaches the classic defense");
                if(environment)h.assertTrue(witness.before>80,"real cactus damage passed the level-scaled environmental guard");
                boolean applies=!otherPlayer||defense!=LegacyDefense.REFLECTION;
                float expected=witness.before;
                double cpExpected=cpBefore;
                if(applies)switch(defense){
                    case LIGHT->{if(!environment){expected-=Math.min(expected,15);cpExpected=(float)cpBefore-5;}}
                    case DEVIATION->{float mastery=(float)AbilityStorage.get(owner).exp(defense.id);expected*=1-(.4F+mastery*(.9F-.4F));cpExpected=(float)cpBefore-Math.min((float)cpBefore,15);}
                    case REFLECTION->{expected-=.6F*expected;cpExpected=Math.max(0f,(float)cpBefore-20*witness.before);}
                }
                h.assertTrue(witness.remaining==expected&&witness.post==expected&&expected>1,"only the actual classic reduction reaches native armor/Post: actual="+witness.remaining+" expected="+expected);
                h.assertTrue(Math.abs(AbilityStorage.get(owner).cp-cpExpected)<1e-5,"classic incoming pays exactly once on the actual owner");
                if(later!=null)h.assertTrue(AbilityStorage.get(later).cp==laterCp&&AbilityStorage.get(later).exp(defense.id)==laterXp,"later global context neither double pays nor receives the first context's XP");
                BigDecimal damage=new BigDecimal(Float.toString(witness.post));
                BigDecimal shieldSpent=shieldBefore.subtract(shield(victim)),healthLost=hpBefore.subtract(health(victim));
                h.assertTrue(shieldSpent.add(healthLost).compareTo(damage)==0,"native remainder is conserved exactly once between base shield and health");
                if(phase==0)h.assertTrue(healthLost.signum()==0&&shieldSpent.compareTo(damage)==0&&shield(victim).signum()>0,"sufficient shield holds all remaining ordinary damage");
                else if(phase==1)h.assertTrue(shield(victim).signum()==0&&shieldSpent.compareTo(BigDecimal.ONE)==0&&healthLost.compareTo(damage.subtract(BigDecimal.ONE))==0,"breaking hit spends the last shield unit, then only its remainder reaches health");
                else h.assertTrue(shieldSpent.signum()==0&&healthLost.compareTo(damage)==0,"empty shield grants no free health protection");
                if(otherPlayer)h.assertTrue(shield(owner).compareTo(ownerShieldBefore)==0,"a global classic handler does not debit the bystander's base shield");
                victim.setDeltaMovement(Vec3.ZERO);victim.knockback(2,1,0);
                h.assertTrue((victim.getDeltaMovement().lengthSqr()<1e-9)==(phase==0),"actual native knockback is suppressed exactly while base shield remains");
                h.assertTrue(EsperShields.holding(victim)==(phase==0),"advertised shield/super armor agrees with actual settled resources");
                resetLegacy(owner);if(later!=null)resetLegacy(later);
            }
            h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch="esper_legacy_light_complete",timeoutTicks=150)
    public static void full_legacy_light_absorption_never_spends_the_base_shield_a_second_time(GameTestHelper h){
        Fixture f=new Fixture(h,NewSkillRuntime.Kind.REVERSE_JET,true);ServerPlayer p=f.player(10,10);
        var witness=new LegacyNativeDamageWitness(p);f.hook(witness);
        f.ready(()->{
            prepareLegacy(p,LegacyDefense.LIGHT);h.assertTrue(LightShield.start(p),"real novice classic light shield starts");
            var attacker=(net.minecraft.world.entity.monster.Zombie)f.spawn(EntityType.ZOMBIE,8);attacker.setNoAi(true);
            var source=p.damageSources().mobAttack(attacker);witness.watch(source);
            var hp=health(p);var shield=shield(p);double cp=AbilityStorage.get(p).cp;p.invulnerableTime=0;
            h.assertFalse(p.hurt(source,10),"complete classic absorption cancels the actual native hurt");
            h.assertTrue(witness.early==1&&witness.late==1&&witness.remaining==0&&witness.cancelled&&witness.posts==0,"a canceled native hit never fabricates a Post debit");
            h.assertTrue(health(p).compareTo(hp)==0&&shield(p).compareTo(shield)==0,"classic absorption spends neither base shield nor health");
            h.assertTrue(AbilityStorage.get(p).cp==(float)cp-5,"classic shield itself pays its single incoming CP debit");
            p.setDeltaMovement(Vec3.ZERO);p.knockback(2,1,0);h.assertTrue(p.getDeltaMovement().lengthSqr()<1e-9&&EsperShields.holding(p),"untouched base shield retains actual super armor");h.succeed();
        });
    }
    /** Observation only: all damage is produced by actual Player.hurt and original registered classic hooks. */
    public static final class LegacyNativeDamageWitness {
        final ServerPlayer victim;DamageSource source;int early,late,posts;float before,remaining,post;boolean cancelled;
        LegacyNativeDamageWitness(ServerPlayer victim){this.victim=victim;}
        void watch(DamageSource source){this.source=source;early=late=posts=0;before=remaining=post=Float.NaN;cancelled=false;}
        @SubscribeEvent(priority=EventPriority.HIGH,receiveCanceled=true) public void before(LivingIncomingDamageEvent event){if(event.getEntity()==victim&&event.getSource()==source){early++;before=event.getAmount();}}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void after(LivingIncomingDamageEvent event){if(event.getEntity()==victim&&event.getSource()==source){late++;remaining=event.getAmount();cancelled=event.isCanceled();}}
        @SubscribeEvent public void post(LivingDamageEvent.Post event){if(event.getEntity()==victim&&event.getSource()==source){posts++;post=event.getNewDamage();}}
    }

    private static final class Fixture {
        final GameTestHelper h; final NewSkillRuntime.Kind kind; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>();
        final List<Object> hooks = new ArrayList<>(); final Map<ServerPlayer, EmbeddedChannel> channels = new IdentityHashMap<>();
        final Map<ServerPlayer, Vec3> anchors = new IdentityHashMap<>(); final Map<ServerPlayer, Long> inputs = new IdentityHashMap<>();
        final Map<ServerPlayer, String> held = new IdentityHashMap<>(); boolean closed; final boolean shields;
        /** Ledger tests start with an empty shield so every hit is health accounting; shield tests opt in. */
        Fixture(GameTestHelper h, NewSkillRuntime.Kind kind) { this(h, kind, false); }
        Fixture(GameTestHelper h, NewSkillRuntime.Kind kind, boolean shields) {
            this.h = h; this.kind = kind; this.shields = shields; EsperLegacyBypass.force(true);
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {}
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        ServerPlayer player(double x, double z) {
            var server = h.getLevel().getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "EG" + UUID.randomUUID().toString().substring(0, 10)), false);
            ServerPlayer p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            Connection connection = new Connection(PacketFlow.SERVERBOUND); EmbeddedChannel channel = new EmbeddedChannel(connection); channels.put(p, channel);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); drain(p);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 4; dz++) h.setBlock(BlockPos.containing(x + dx, 1, z + dz), Blocks.STONE);
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); anchors.put(p, h.absoluteVec(new Vec3(x + .5, 2, z + .5))); place(p);
            h.assertTrue(HighPowerCombat.enroll(p, kind.level), "explicit native connected enrollment");
            var s = AbilityStorage.get(p); s.selectCategory(kind.category); s.setLevel(kind.level);
            s.learn(HighPowerCategories.recoveryRoot(kind.category)); s.learn(kind.id); s.activated = true; s.overloadFine = true;
            h.assertTrue(s.presets.edit(0, 0, kind.id, s::learned), "real learned native key binding");
            HighPowerResources.initializeBudget(p); hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5)); return p;
        }
        void ready(Runnable action) { h.runAfterDelay(61, () -> { for (ServerPlayer p : players) { p.doTick(); place(p); if (!shields) setShield(p, "0"); } action.run(); }); }
        void setShield(ServerPlayer p, String value) { var c = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY); c.putString("shield", value); c.putLong("lastShieldSpend", p.level().getGameTime()); }
        void place(ServerPlayer p) { Vec3 at = anchors.get(p); p.moveTo(at.x, at.y, at.z, 0, 0); p.setYHeadRot(0); p.yBodyRot = 0; p.setOnGround(true); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        long epoch(ServerPlayer p) { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        String wire(ServerPlayer p) { long input = inputs.getOrDefault(p, 0L) + 1; inputs.put(p, input); return new HighPowerInput.Input(0, input, p.getId(), epoch(p)).wire(); }
        boolean start(ServerPlayer p) { String wire = wire(p); held.put(p, wire); return NewSkillRuntime.start(p, wire); }
        void release(ServerPlayer p) { NewSkillRuntime.release(p, held.get(p)); }
        double cost(ServerPlayer p) { return NewSkillBudget.cp(budget(), HighPowerGrowth.values(p).position()); }
        NewSkillBudget.Skill budget() { return NewSkillBudget.Skill.valueOf(kind.name()); }
        Entity spawn(EntityType<?> type, double ahead) { return spawnAt(type, ahead, 0); }
        /** Spawns {@code ahead} metres in front of the player (looking toward +Z) and {@code side} metres to the right. */
        Entity spawnAt(EntityType<?> type, double ahead, double side) {
            Entity e = type.create(h.getLevel()); Vec3 at = anchors.values().iterator().next();
            e.setPos(at.x - side, at.y, at.z + ahead); h.assertTrue(h.getLevel().addFreshEntity(e), "entity joined the world"); entities.add(e); return e;
        }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void drain(ServerPlayer p) {
            EmbeddedChannel channel = channels.get(p); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet;
            while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet); }
        }
        void close() {
            if (closed) return; closed = true; EsperLegacyBypass.force(false); hooks.forEach(NeoForge.EVENT_BUS::unregister);
            for (ServerPlayer p : players) { resetLegacy(p); NewSkillRuntime.remove(p); DevelopmentController.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.server.getPlayerList().remove(p); }
            entities.forEach(Entity::discard); channels.values().forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }
}

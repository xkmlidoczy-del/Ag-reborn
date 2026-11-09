package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.skill.LightShield;
import cn.academy.port.skill.LightShieldSession;
import cn.academy.port.skill.ScatterBomb;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.util.function.IntConsumer;

/** Twelve native fixtures, compiled only by this worker. Distinct batches serialize global Shield quirks. */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class AcademyMeltdownerStarterRuntimeTests {
    private static final String TEMPLATE = "meltdowner_starter_empty";
    private AcademyMeltdownerStarterRuntimeTests() {}
    @SubscribeEvent public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var tag = new CompoundTag(); var size = new ListTag();
        size.add(IntTag.valueOf(32)); size.add(IntTag.valueOf(8)); size.add(IntTag.valueOf(32)); tag.put("size", size);
        var blocks = new ListTag(); var first = new CompoundTag(); var pos = new ListTag();
        pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); first.put("pos", pos); first.putInt("state", 0); blocks.add(first); tag.put("blocks", blocks);
        tag.put("entities", new ListTag()); var palette = new ListTag(); var air = new CompoundTag(); air.putString("Name", "minecraft:air"); palette.add(air); tag.put("palette", palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy", TEMPLATE)).load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }
    @GameTest(template = TEMPLATE, batch = "academy_meltdowner_starters_scatter80", timeoutTicks = 100)
    public static void scatter_bomb_80_ticks_seven_real_hits_ignore_immunity_and_no_cooldown(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(); var s = ready(p, ScatterBomb.ID, 0); var target = f.target(p.position().add(0, 0, 14));
        var hook = new AttackHook(p, false); f.hook(hook); double cp = s.cp;
        h.assertTrue(ScatterBomb.start(p), "novice hold starts"); h.assertFalse(ScatterBomb.start(p), "duplicate hold refuses repayment");
        f.ticks(80, tick -> {
            ScatterBomb.tick(p); int held = ScatterBomb.heldTicks(p); ScatterBomb.tick(p); h.assertValueEqual(ScatterBomb.heldTicks(p), held, "same-world-tick replay suppressed");
            h.assertValueEqual(ScatterBomb.ballCount(p), tick < 20 ? 0 : tick / 10 - 1, "source ball count for actual world ticks");
            if (tick == 80) {
                broadTarget(target); target.invulnerableTime = 100; h.assertTrue(ScatterBomb.release(p), "ordinary key-up shoots");
                h.assertValueEqual(hook.count(target), 7, "all seven native ray hits carry distinct transactions");
                h.assertTrue(hook.allResistanceCleared, "each ball clears hurt resistance before attack callback");
                close(h, target.getHealth(), 165, "seven native damage5 hits land despite initial immunity"); close(h, s.cp, cp - 240, "80 novice CP3 payments");
                close(h, s.exp(ScatterBomb.ID), .001F * 7, "termination awards seven-ball EXP"); h.assertTrue(s.cooldowns.isEmpty(), "source Scatter has no cooldown");
                h.assertFalse(ScatterBomb.release(p), "duplicate release cannot replay"); h.assertValueEqual(hook.count(target), 7, "duplicate release adds no attacks"); h.succeed();
            }
        });
    }
    @GameTest(template = TEMPLATE, batch = "academy_meltdowner_starters_scatter_fail20", timeoutTicks = 35)
    public static void scatter_bomb_failed_cp20_still_spawns_and_fires_its_first_ball(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(); var s = ready(p, ScatterBomb.ID, 0); s.cp = 57;
        var target = f.target(p.position().add(0, 0, 14)); var hook = new AttackHook(p, true); f.hook(hook); ScatterBomb.start(p);
        f.ticks(20, tick -> {
            if (tick == 20) broadTarget(target);
            ScatterBomb.tick(p);
            if (tick == 20) { h.assertFalse(ScatterBomb.active(p), "failed CP20 terminates"); h.assertValueEqual(hook.count(target), 1, "spawn-before-consume fires one ball"); close(h, s.cp, 0, "finite CP exhausted without negative balance"); close(h, s.exp(ScatterBomb.ID), .001F, "failed final consumption still trains its spawned ball"); h.assertTrue(s.cooldowns.isEmpty(), "failed Scatter termination no cooldown"); h.succeed(); }
        });
    }
    @GameTest(template = TEMPLATE, batch = "academy_meltdowner_starters_scatter_abort30", timeoutTicks = 45)
    public static void scatter_bomb_key_abort_uses_current_head_aim_and_captured_mastery(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(); var s = ready(p, ScatterBomb.ID, 0);
        var oldTarget = f.target(p.position().add(0, 0, 14)); var newTarget = f.target(p.position().add(14, 0, 0)); var hook = new AttackHook(p, true); f.hook(hook); ScatterBomb.start(p);
        f.ticks(30, tick -> {
            ScatterBomb.tick(p);
            if (tick == 30) { broadTarget(newTarget); p.setYHeadRot(-90); p.setYRot(90); s.experience.put(ScatterBomb.ID, 1D); ScatterBomb.abort(p); h.assertValueEqual(hook.count(oldTarget), 0, "start aim is not used at termination"); h.assertValueEqual(hook.count(newTarget), 2, "ordinary key-abort shoots two balls to current head aim"); close(h, hook.damage(newTarget), 5, "damage retains captured novice mastery"); h.assertFalse(ScatterBomb.active(p), "key-abort removes context"); h.succeed(); }
        });
    }
    @GameTest(template = TEMPLATE, batch = "academy_meltdowner_starters_scatter_block", timeoutTicks = 45)
    public static void scatter_bomb_block_occlusion_stops_damage_without_destroying_block(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(); var s = ready(p, ScatterBomb.ID, 0); var target = f.target(p.position().add(0, 0, 14));
        for (int x = 7; x <= 9; x++) for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, 20), Blocks.STONE);
        var hook = new AttackHook(p, false); f.hook(hook); ScatterBomb.start(p);
        f.ticks(30, tick -> { ScatterBomb.tick(p); if (tick == 30) { ScatterBomb.release(p); h.assertValueEqual(hook.count(target), 0, "oblique ball rays stop at collider"); close(h, target.getHealth(), 200, "occluded native target unharmed"); close(h, s.exp(ScatterBomb.ID), .002F, "misses still award accumulated-ball EXP"); h.assertBlockPresent(Blocks.STONE, new BlockPos(8, 2, 20)); h.succeed(); } });
    }
    @GameTest(template = TEMPLATE, batch = "academy_meltdowner_starters_scatter200", timeoutTicks = 220)
    public static void scatter_bomb_no_cp_after80_and_self_player_damage_at200(GameTestHelper h) {
        var f = new Fixture(h); var p = f.damagePlayer(); var s = ready(p, ScatterBomb.ID, 1); double cp = s.cp;
        // NeoForge FakePlayer is unconditionally invulnerable and refuses canHarmPlayer.
        // Use the existing real ServerPlayer/PlayerList/embedded transport fixture instead.
        f.ticks(200, tick -> {
            // An embedded transport is not in the server socket-list tick loop. Tick its
            // actual listener, retaining native doTick, spawn grace and ordinary CP recovery.
            p.connection.tick();
            if(tick==1)h.assertTrue(ScatterBomb.start(p),"real survival actor starts inside the first measured callback");
            ScatterBomb.tick(p);
            if(tick==80)close(h,s.cp,cp-480,"exact eighty native CP6 payments");
            if(tick>=80)h.assertTrue(s.cp>=cp-480-1E-5,"source stops charging CP after80; ordinary real-player recovery is retained");
            if (tick == 199) { h.assertTrue(ScatterBomb.active(p), "seven balls remain held through199"); close(h, p.getHealth(), 20, "no premature self damage"); }
            if (tick == 200) { h.assertFalse(ScatterBomb.active(p), "forced200 termination"); close(h, p.getHealth(), 14, "native ordinary self player attack deals6"); h.assertTrue(s.cooldowns.isEmpty(), "forced source end no cooldown"); h.succeed(); }
        });
    }
    @GameTest(template = TEMPLATE, batch = "academy_meltdowner_starters_touch", timeoutTicks = 20)
    public static void light_shield_touch_uses_body_yaw_feet_sphere_and_native_immunity(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(); var s = ready(p, LightShield.ID, 0);
        var front = f.target(p.position().add(0, 0, 2)); var rear = f.target(p.position().add(0, 0, -2)); var high = f.target(p.position().add(0, 4, 1));
        var immune = f.target(p.position().add(1, 0, 2)); immune.invulnerableTime = 100; var hook = new AttackHook(p, true); f.hook(hook); double cp = s.cp;
        p.setYHeadRot(180); p.setYRot(0); LightShield.start(p);
        f.ticks(1, tick -> { LightShield.tick(p); h.assertValueEqual(hook.count(front), 1, "front feet reachable despite opposite head aim"); h.assertValueEqual(hook.count(rear), 0, "rear target outside body-yaw sector"); h.assertValueEqual(hook.count(high), 0, "vertical separation fails source feet sphere"); h.assertValueEqual(hook.count(immune), 0, "positive native hurt resistance skips touch"); close(h, s.cp, cp - 9 - 50, "tick9CP plus one touch50CP"); close(h, s.overload, 115, "startup110 plus touch5strain"); close(h, s.exp(LightShield.ID), 1E-6F + (double) .001F, "tick and canceled damage callback still award source EXP"); LightShield.release(p); h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "end applies native SlownessII"); h.assertValueEqual(p.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier(), 1, "SlownessII amplifier1"); h.assertValueEqual(p.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration(), 100, "Slowness100ticks"); h.assertValueEqual(s.cooldowns.get(LightShield.ID), 2, "one novice tick cooldown2"); h.succeed(); });
    }
    @GameTest(template = TEMPLATE, batch = "academy_meltdowner_starters_incoming_global", timeoutTicks = 25)
    public static void light_shield_global_context_null_source_and_reversed_costs_are_literal(GameTestHelper h) {
        var f = new Fixture(h); var owner = f.player(); var s = ready(owner, LightShield.ID, 0); s.setLevel(5);
        var second = f.player(); var other = ready(second, LightShield.ID, 1); second.moveTo(owner.getX() + 20, owner.getY(), owner.getZ() - 20, 0, 0);
        var victim = f.player(); var attacker = f.player(); attacker.moveTo(owner.getX(), owner.getY(), owner.getZ() - 100, 0, 0);
        LightShield.start(owner); LightShield.start(second); double cp = s.cp;
        var environmental = incoming(victim, victim.damageSources().generic(), 20); h.assertFalse(environmental.isCanceled(), "null environmental source is not absorbed"); close(h, environmental.getAmount(), 20, "environmental damage untouched"); close(h, s.exp(LightShield.ID), .001F, "global first context still learns from environmental damage to another player");
        var damage = incoming(victim, victim.damageSources().playerAttack(attacker), 20); close(h, damage.getAmount(), 5, "rear far direct source still absorbs15 for another victim"); close(h, s.cp, cp - 5, "incoming swapped CP5"); close(h, s.overload, 160, "incoming swapped strain50"); close(h, other.exp(LightShield.ID), 1, "later mastery remains capped"); close(h, other.levelExperience, 0, "later active shield receives no first-context work");
        var retry = incoming(victim, victim.damageSources().playerAttack(attacker), 20); close(h, retry.getAmount(), 20, "same-tick retry gated"); close(h, s.exp(LightShield.ID), .002F, "gated retry adds no action EXP");
        LightShield.remove(owner); other.cp = 2; var failed = incoming(victim, victim.damageSources().playerAttack(attacker), 20); close(h, failed.getAmount(), 20, "next context selected, failed finite CP leaves damage untouched"); close(h, other.cp, 2, "failed incoming cannot partially consume");
        h.succeed();
    }
    @GameTest(template = TEMPLATE, batch = "academy_meltdowner_starters_interval19", timeoutTicks = 35)
    public static void light_shield_incoming_interval_is_inclusive18_and_full_absorb_cancels(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(); var s = ready(p, LightShield.ID, 1); s.setLevel(5); var victim = f.player(); var attacker = f.player(); float initialCp=(float)s.cp; LightShield.start(p);
        var first = incoming(victim, victim.damageSources().playerAttack(attacker), 50); h.assertTrue(first.isCanceled(), "master absorbs all50 and cancels event");
        f.ticks(19, tick -> { LightShield.tick(p); var hit = incoming(victim, victim.damageSources().playerAttack(attacker), 50); if (tick <= 18) { h.assertFalse(hit.isCanceled(), "inclusive18 gate preserves incoming event"); close(h, hit.getAmount(), 50, "no absorb before19"); } else { h.assertTrue(hit.isCanceled(), "tick19 absorbs all again"); close(h, s.cp, initialCp - 19 * 4 - 2 * 3, "19 tickCP4 plus two reversed incoming CP3"); h.succeed(); } });
    }
    @GameTest(template = TEMPLATE, batch = "academy_meltdowner_starters_timeout181", timeoutTicks = 200)
    public static void light_shield_strict181_timeout_runs_final_tick_and_captured_cooldown(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(); var s = ready(p, LightShield.ID, 1); s.setLevel(5); double cp = s.cp; LightShield.start(p);
        f.ticks(181, tick -> { LightShield.tick(p); if (tick == 180) h.assertTrue(LightShield.active(p), "exact maximum180 remains active"); if (tick == 181) { h.assertFalse(LightShield.active(p), "strict greater-than180 ends at181"); close(h, s.cp, cp - 181 * 4, "final terminating tick still consumes4CP"); h.assertValueEqual(s.cooldowns.get(LightShield.ID), 181, "captured mastered cooldown181"); h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "timeout applies genuine source Slowness"); h.succeed(); } });
    }
    @GameTest(template = TEMPLATE, batch = "academy_meltdowner_starters_shield_fail", timeoutTicks = 20)
    public static void light_shield_failed_tick_and_startup_overload_still_apply_end_effects(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(); var s = ready(p, LightShield.ID, 0); s.cp = 8; LightShield.start(p);
        f.ticks(1, tick -> { LightShield.tick(p); h.assertFalse(LightShield.active(p), "failed novice CP9 ends"); close(h, s.cp, 8, "failed consume leaves available CP"); close(h, s.exp(LightShield.ID), 1E-6F, "failed tick still grants source tick EXP"); h.assertValueEqual(s.cooldowns.get(LightShield.ID), 2, "failed first tick source cooldown2"); h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "failed tick applies source end Slowness");
            var overloaded = f.player(); var state = ready(overloaded, LightShield.ID, 0); state.overload = state.maxOverload() - 1;
            h.assertTrue(LightShield.start(overloaded), "source zero-CP startup consume succeeds"); h.assertFalse(LightShield.active(overloaded) || state.overloadFine, "startup overload disposes immediately"); h.assertTrue(overloaded.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "startup termination still slows"); h.assertTrue(state.cooldowns.isEmpty(), "zero held ticks gives zero cooldown"); h.succeed(); });
    }
    @GameTest(template = TEMPLATE, batch = "academy_meltdowner_starters_wire", timeoutTicks = 20)
    public static void meltdowner_starter_authenticated_slots_nonces_and_preset_lifecycle_guards(GameTestHelper h) {
        var f = new Fixture(h);
        for (String id : List.of(ScatterBomb.ID, LightShield.ID)) {
            var p = f.player(); var s = ready(p, id, 0); p.setYHeadRot(Float.NaN); h.assertFalse(start(p, id), "non-finite server aim cannot start"); p.setYHeadRot(0); h.assertTrue(client(p, "preset_edit", "0:0:" + id), "naturally learned zero-mastery skill binds");
            h.assertFalse(client(p, "skill_start", id), "wire cannot spoof arbitrary named start");
            for (String malformed : List.of("0:0", "0:-1", "0:01", "0:+1", "00:1", "4:1", "0:1:2", "0:9223372036854775808")) h.assertFalse(client(p, "slot_press_token", malformed), "malformed nonce input rejected: " + malformed);
            h.assertTrue(client(p, "slot_press_token", "0:1"), "correlated physical slot starts"); double strain = s.overload;
            h.assertFalse(client(p, "slot_press_token", "0:1"), "inflight nonce replay rejected"); close(h, s.overload, strain, "replay does not repay overload");
            client(p, "slot_abort", "0"); h.assertFalse(active(p, id), "ordinary key-abort terminates");
            s.overload = 0; s.overloadFine = true; h.assertFalse(client(p, "slot_press_token", "0:1"), "retired nonce cannot revive old context"); h.assertTrue(client(p, "slot_press_token", "0:2"), "new physical press accepted");
            // ScatterBomb/LightShield key-abort terminates this context before the mapping request.
            h.assertTrue(client(p, "slot_abort", "0"), "skill-specific key-abort authenticated before switch");
            h.assertFalse(active(p, id), "key-abort ends real context before switch");
            client(p, "preset_switch", "1"); h.assertFalse(active(p, id), "switch preserves callback-ended context"); s.overload = 0; client(p, "preset_switch", "0"); client(p, "slot_press_token", "0:3");
            h.assertTrue(active(p, id), "fresh context active before edit callback");
            h.assertTrue(client(p, "slot_abort", "0"), "skill-specific key-abort authenticated before other-page edit");
            h.assertFalse(active(p, id), "key-abort ends context before other-page edit");
            client(p, "preset_edit", "1:1:" + id); h.assertFalse(active(p, id), "other-page edit preserves callback-ended context");
            s.overload = 0; client(p, "slot_press_token", "0:4"); s.interfering = true; tick(p, id); h.assertFalse(active(p, id), "interference ends held context");
        }
        h.succeed();
    }
    @GameTest(template = TEMPLATE, batch = "academy_meltdowner_starters_dispose", timeoutTicks = 20)
    public static void meltdowner_starter_dimension_clone_death_logout_dispose_without_stale_effects(GameTestHelper h) {
        var f = new Fixture(h);
        for (String id : List.of(ScatterBomb.ID, LightShield.ID)) {
            var p = f.player(); var s = ready(p, id, 0); start(p, id);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(p, Level.OVERWORLD, Level.NETHER)); h.assertFalse(active(p, id), "dimension lifecycle removes context"); h.assertFalse(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "dimension cleanup has no stale end Slowness");
            s.overload = 0; start(p, id); var replacement = f.player(); NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, p, false)); h.assertFalse(active(p, id), "clone removes original context");
            // AbilityStorage.clone retires the original cache; subsequent contexts belong to replacement actor.
            p = replacement; s = AbilityStorage.get(p); s.overload = 0; s.overloadFine = true; s.activated = true; start(p, id); client(p, "toggle", ""); h.assertFalse(active(p, id), "deactivation ends replacement actor context");
            s.activated = true; s.overload = 0; s.cooldowns.clear(); start(p, id); NeoForge.EVENT_BUS.post(new LivingDeathEvent(p, p.damageSources().generic())); h.assertFalse(active(p, id) || s.activated, "death removes context and deactivates state");
            s.activated = true; s.overloadFine = true; s.overload = 0; s.cooldowns.clear(); start(p, id); NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p)); h.assertFalse(active(p, id), "logout removes transient state");
        }
        h.succeed();
    }
    private static void broadTarget(LivingEntity target) { target.setBoundingBox(new AABB(target.getX() - 2, target.getY(), target.getZ() - 2, target.getX() + 2, target.getY() + 3, target.getZ() + 2)); }
    private static LivingIncomingDamageEvent incoming(ServerPlayer victim, DamageSource source, float damage) { var event = new LivingIncomingDamageEvent(victim, new DamageContainer(source, damage)); NeoForge.EVENT_BUS.post(event); return event; }
    private static boolean client(ServerPlayer p, String action, String value) { return AcademyGameplay.requestFromClient(p, new AcademyNetwork.Request(action, value)); }
    private static boolean active(ServerPlayer p, String id) { return id.equals(ScatterBomb.ID) ? ScatterBomb.active(p) : LightShield.active(p); }
    private static void tick(ServerPlayer p, String id) { if (id.equals(ScatterBomb.ID)) ScatterBomb.tick(p); else LightShield.tick(p); }
    private static boolean start(ServerPlayer p, String id) { return id.equals(ScatterBomb.ID) ? ScatterBomb.start(p) : LightShield.start(p); }
    private static AbilityProgress ready(ServerPlayer p, String id, double exp) { var s = AbilityStorage.get(p); s.selectCategory("meltdowner"); s.setLevel(2); s.learn(id); s.experience.put(id, exp); s.activated = true; return s; }
    private static void close(GameTestHelper h, double actual, double expected, String label) { h.assertTrue(Double.isFinite(actual) && Math.abs(actual - expected) < 1E-5, label + ": expected " + expected + ", got " + actual); }
    private static final class Fixture {
        final GameTestHelper helper; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> targets = new ArrayList<>(); final List<Object> hooks = new ArrayList<>(); boolean closed;
        Fixture(GameTestHelper helper) {
            this.helper = helper; helper.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
                public void testAddedForRerun(GameTestInfo old, GameTestInfo next, GameTestRunner runner) { cleanup(); }
            });
        }
        AcademyWirelessDeviceRuntimeTests.NativeMenuActors nativeActors;
        ServerPlayer damageActor;
        Boolean originalPvp;
        ServerPlayer damagePlayer(){
            // GameTestServer defaults to PvP false; actual IntegratedServer enables it.
            // Match single-player only in this isolated batch, then restore on all outcomes.
            var server=helper.getLevel().getServer();originalPvp=server.isPvpAllowed();server.setPvpAllowed(true);
            nativeActors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(helper);var p=nativeActors.player();damageActor=p;p.setInvulnerable(false);Vec3 v=helper.absoluteVec(new Vec3(8.5,1,8.5));p.moveTo(v.x,v.y,v.z,0,0);p.setYHeadRot(0);p.setNoGravity(true);p.setDeltaMovement(Vec3.ZERO);return p;
        }
        FakePlayer player() { var p = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[AC-MD-Starter]")); players.add(p); Vec3 v = helper.absoluteVec(new Vec3(8.5, 1, 8.5)); p.moveTo(v.x, v.y, v.z, 0, 0); p.setYHeadRot(0); p.setNoGravity(true); p.setDeltaMovement(Vec3.ZERO); p.getAbilities().instabuild = false; return p; }
        Villager target(Vec3 point) { var v = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(1, 1, 1)); targets.add(v); v.moveTo(point.x, point.y, point.z, 0, 0); v.setNoGravity(true); v.setNoAi(true); v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); v.setHealth(200); return v; }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void ticks(int count, IntConsumer action) { for (int tick = 1; tick <= count; tick++) { int current = tick; helper.runAtTickTime(tick, () -> { if (!closed) action.accept(current); }); } }
        void cleanup() { if (closed) return; closed = true;try { for (var hook : hooks) NeoForge.EVENT_BUS.unregister(hook); for (var p : players) { ScatterBomb.remove(p); LightShield.remove(p); AbilityStorage.remove(p); p.discard(); } if(damageActor!=null){ScatterBomb.remove(damageActor);LightShield.remove(damageActor);}if(nativeActors!=null)nativeActors.close();for (var target : targets) target.discard(); } finally {if(originalPvp!=null)helper.getLevel().getServer().setPvpAllowed(originalPvp);} }
    }
    public static final class AttackHook {
        final ServerPlayer owner; final boolean cancel; final Map<Entity, Integer> counts = new IdentityHashMap<>(); final Map<Entity, Double> damage = new IdentityHashMap<>(); boolean allResistanceCleared = true;
        AttackHook(ServerPlayer owner, boolean cancel) { this.owner = owner; this.cancel = cancel; }
        @SubscribeEvent public void attack(SkillAttackEvent event) { if (event.player == owner && (event.skill.equals("meltdowner.scatter_bomb") || event.skill.equals("meltdowner.light_shield"))) { counts.merge(event.target, 1, Integer::sum); damage.put(event.target, event.amount); if (event.skill.equals("meltdowner.scatter_bomb") && event.target instanceof LivingEntity living) allResistanceCleared &= living.invulnerableTime <= 0; if (cancel) event.amount = 0; } }
        int count(Entity e) { return counts.getOrDefault(e, 0); } double damage(Entity e) { return damage.getOrDefault(e, 0D); }
    }
}

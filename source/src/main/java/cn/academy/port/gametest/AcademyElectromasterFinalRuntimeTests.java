package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.skill.BodyIntensify;
import cn.academy.port.skill.BodyIntensifySession;
import cn.academy.port.skill.ThunderClap;
import cn.academy.port.skill.ThunderClapSession;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.util.function.IntConsumer;

/** Nine native fixtures, COMPILED ONLY by the staging worker. No client, saves, or Gradle run. */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class AcademyElectromasterFinalRuntimeTests {
    private static final String TEMPLATE = "electromaster_final_empty", BATCH = "academy_electromaster_final";
    private AcademyElectromasterFinalRuntimeTests() {}
    @SubscribeEvent public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var tag = new CompoundTag(); var size = new ListTag();
        size.add(IntTag.valueOf(80)); size.add(IntTag.valueOf(8)); size.add(IntTag.valueOf(80)); tag.put("size", size);
        var blocks = new ListTag(); var first = new CompoundTag(); var pos = new ListTag();
        pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); first.put("pos", pos); first.putInt("state", 0); blocks.add(first); tag.put("blocks", blocks);
        tag.put("entities", new ListTag()); var palette = new ListTag(); var air = new CompoundTag(); air.putString("Name", "minecraft:air"); palette.add(air); tag.put("palette", palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy", TEMPLATE)).load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 90)
    public static void thunder_clap_block_only_aim_auto60_radial_attack_and_weather_lightning(GameTestHelper helper) {
        var f = new Fixture(helper); var p = f.player(); var s = ready(p, ThunderClap.ID, 0);
        helper.setBlock(new BlockPos(40, 2, 45), Blocks.STONE);
        Vec3 point = ThunderClap.aim(p); close(helper, point.z, helper.absoluteVec(new Vec3(0, 0, 45)).z, "aim reaches blockface");
        var center = f.target(point); var half = f.target(point.add(7.5, 0, 0)); var boundary = f.target(point.add(15, 0, 0)); var outside = f.target(point.add(15.01, 0, 0));
        var intercept = f.target(p.getEyePosition().add(0, 0, 8));
        close(helper, ThunderClap.aim(p).distanceTo(point), 0, "living ray intercept ignored");
        helper.setBlock(new BlockPos(43, 2, 45), Blocks.STONE); // No AOE LOS check.
        var hook = new AttackHook(p); f.hook(hook); double cp = s.cp;
        helper.assertTrue(ThunderClap.start(p), "natural novice hold starts"); helper.assertFalse(ThunderClap.start(p), "duplicate start no repayment");
        f.ticks(60, tick -> {
            ThunderClap.tick(p); int held = ThunderClap.heldTicks(p); ThunderClap.tick(p); helper.assertValueEqual(ThunderClap.heldTicks(p), held, "same-worldtick replay suppressed");
            if (tick < 60) helper.assertTrue(ThunderClap.active(p), "not fired before60");
            else {
                helper.assertFalse(ThunderClap.active(p), "source auto60 terminates"); close(helper, s.cp, cp - 720, "only40 paid CP18 ticks");
                close(helper, hook.damage(center), ThunderClapSession.damage(0, 60), "fullcenter damage"); close(helper, hook.damage(half), ThunderClapSession.damage(0, 60) * .5F, "linearhalf damage throughwall");
                close(helper, hook.damage(boundary), 0, "inclusive spherezero raw damage"); helper.assertValueEqual(hook.count(boundary), 1, "boundary participates once");
                helper.assertValueEqual(hook.count(outside), 0, "outside feet excluded"); helper.assertValueEqual(hook.count(intercept), 0, "aimintercept too far fromimpact");
                helper.assertTrue(f.lightning().size() == 1, "genuine weather lightning spawned"); close(helper, s.exp(ThunderClap.ID), .003F, "source performEXP"); helper.assertValueEqual(s.cooldowns.get(ThunderClap.ID), 600, "novice60cooldown");
                helper.assertFalse(ThunderClap.release(p), "late release cannot discharge twice"); helper.succeed();
            }
        });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 70)
    public static void thunder_clap_charged_keyup_cancels_without_exp_cooldown_or_lightning(GameTestHelper helper) {
        var f = new Fixture(helper); var p = f.player(); var s = ready(p, ThunderClap.ID, 0); double cp = s.cp;
        helper.assertTrue(ThunderClap.start(p), "hold starts");
        f.ticks(40, tick -> {
            ThunderClap.tick(p);
            if (tick == 40) {
                helper.assertTrue(ThunderClap.release(p), "charged release terminates rather than fires");
                helper.assertFalse(ThunderClap.active(p), "released context gone"); close(helper, s.cp, cp - 720, "source paidCP notrefunded"); close(helper, s.overload, 390, "startoverload notrefunded");
                close(helper, s.exp(ThunderClap.ID), 0, "noEXP"); helper.assertTrue(s.cooldowns.isEmpty() && f.lightning().isEmpty(), "no cooldown or weather effect"); helper.succeed();
            }
        });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 70)
    public static void thunder_clap_cp_failure_exactly40_fires_and_earlier_failure_cancels(GameTestHelper helper) {
        var f = new Fixture(helper); var p = f.player(); var s = ready(p, ThunderClap.ID, 0); s.cp = 39 * 18;
        var poor = f.player(); var poorState = ready(poor, ThunderClap.ID, 0); poorState.cp = 38 * 18;
        ThunderClap.start(p); ThunderClap.start(poor);
        f.ticks(40, tick -> {
            ThunderClap.tick(p); ThunderClap.tick(poor);
            if (tick == 40) {
                helper.assertFalse(ThunderClap.active(p) || ThunderClap.active(poor), "both contexts ended");
                close(helper, s.exp(ThunderClap.ID), .003F, "failed40CP still succeeds"); helper.assertValueEqual(s.cooldowns.get(ThunderClap.ID), 400, "failed40 exactcooldown");
                close(helper, poorState.exp(ThunderClap.ID), 0, "failed39 cancels"); helper.assertTrue(poorState.cooldowns.isEmpty(), "failed39 nocooldown"); helper.assertTrue(f.lightning().size() == 1, "only40 fireslightning"); helper.succeed();
            }
        });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 70)
    public static void body_intensify40_real_fixed_order_buffs_hunger_and_post_exp_cooldown(GameTestHelper helper) {
        var f = new Fixture(helper); var p = f.player(); var s = ready(p, BodyIntensify.ID, 0); double cp = s.cp;
        BodyIntensify.start(p); var probe = RandomSource.create(0); int expectedTime = (int) ((1 + probe.nextDouble()) * 40 * 1.5);
        helper.assertTrue(probe.nextDouble() < 1 && probe.nextDouble() < 2D / 3, "owned random produces both sourcebuffs");
        f.ticks(40, tick -> {
            s.overload = 0; BodyIntensify.tick(p); close(helper, s.overload, 200, "overload locked whileholding");
            if (tick == 40) {
                helper.assertTrue(BodyIntensify.release(p, RandomSource.create(0)), "valid40release");
                var jump = p.getEffect(MobEffects.JUMP); var regen = p.getEffect(MobEffects.REGENERATION); var hunger = p.getEffect(MobEffects.HUNGER);
                helper.assertTrue(jump != null && regen != null && hunger != null, "actual three native MobEffectInstances");
                helper.assertValueEqual(jump.getAmplifier(), 1, "JumpII"); helper.assertValueEqual(regen.getAmplifier(), 1, "RegenerationII"); helper.assertValueEqual(jump.getDuration(), expectedTime, "source Double durationtruncated");
                helper.assertValueEqual(regen.getDuration(), expectedTime, "shared randomizedbuffduration"); helper.assertValueEqual(hunger.getDuration(), 50, "Hunger50ticks"); helper.assertValueEqual(hunger.getAmplifier(), 2, "HungerIII");
                helper.assertFalse(p.hasEffect(MobEffects.MOVEMENT_SPEED) || p.hasEffect(MobEffects.DAMAGE_BOOST) || p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "discardedshuffle bug leaves Speed Strength Resistance unreachable");
                close(helper, s.cp, cp - 800, "40noviceCP20payments"); close(helper, s.exp(BodyIntensify.ID), .01F, "sourceEXP"); helper.assertValueEqual(s.cooldowns.get(BodyIntensify.ID), 897, "cooldown computedAFTEREXP"); helper.assertFalse(BodyIntensify.release(p), "release replayrejected"); helper.succeed();
            }
        });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void body_intensify_release10_hunger_only_and_release9_cancel(GameTestHelper helper) {
        var f = new Fixture(helper); var p = f.player(); var s = ready(p, BodyIntensify.ID, 0); var early = f.player(); var earlyState = ready(early, BodyIntensify.ID, 0);
        BodyIntensify.start(p); BodyIntensify.start(early);
        f.ticks(10, tick -> {
            BodyIntensify.tick(p); if (tick <= 9) BodyIntensify.tick(early);
            if (tick == 9) { BodyIntensify.release(early); helper.assertFalse(early.hasEffect(MobEffects.HUNGER), "release9 has nobuffs"); close(helper, earlyState.exp(BodyIntensify.ID), 0, "release9 noEXP"); helper.assertTrue(earlyState.cooldowns.isEmpty(), "release9 nocooldown"); }
            if (tick == 10) { BodyIntensify.release(p, RandomSource.create(1)); helper.assertTrue(p.hasEffect(MobEffects.HUNGER), "release10 succeedswithHunger"); helper.assertFalse(p.hasEffect(MobEffects.JUMP) || p.hasEffect(MobEffects.REGENERATION), "probabilityzero gives no beneficialbuff"); helper.assertValueEqual(p.getEffect(MobEffects.HUNGER).getDuration(), 12, "12tickstruncated"); close(helper, s.exp(BodyIntensify.ID), .01F, "Hungeronly counts success"); helper.succeed(); }
        });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 125)
    public static void body_intensify_no_cp_after40_and_timeout100_cancel(GameTestHelper helper) {
        var f = new Fixture(helper); var p = f.player(); var s = ready(p, BodyIntensify.ID, 1); var poor = f.player(); var poorState = ready(poor, BodyIntensify.ID, 0); poorState.cp = 199;
        double cp = s.cp; BodyIntensify.start(p); BodyIntensify.start(poor);
        f.ticks(100, tick -> {
            BodyIntensify.tick(p); BodyIntensify.tick(poor);
            if (tick >= 40) close(helper, s.cp, cp - 600, "only40masterCP15payments");
            if (tick == 10) helper.assertFalse(BodyIntensify.active(poor), "failedCP10 autocancels");
            if (tick == 99) helper.assertTrue(BodyIntensify.active(p), "stillactive99");
            if (tick == 100) { helper.assertFalse(BodyIntensify.active(p), "timeout100 autocancels"); helper.assertFalse(p.hasEffect(MobEffects.HUNGER) || poor.hasEffect(MobEffects.HUNGER), "no failurebuffs"); helper.assertTrue(s.cooldowns.isEmpty() && poorState.cooldowns.isEmpty(), "no failurecooldowns"); close(helper, poorState.exp(BodyIntensify.ID), 0, "failure noEXP"); helper.succeed(); }
        });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void final_em_authenticated_slots_abort_on_switch_edit_interference_and_logout(GameTestHelper helper) {
        var f = new Fixture(helper);
        for (String id : List.of(BodyIntensify.ID, ThunderClap.ID)) {
            var p = f.player(); var s = ready(p, id, 0);
            helper.assertTrue(client(p, "preset_edit", "0:0:" + id), "naturalzero masterybinds"); double cp = s.cp;
            helper.assertFalse(client(p, "skill_start", id), "raw namedwirestart spoofrejected"); close(helper, s.cp, cp, "spoof nospend");
            helper.assertTrue(client(p, "slot_press", "0"), "authenticatedslotstart"); helper.assertTrue(active(p, id), "real heldcontextstarted"); double overload = s.overload;
            // BodyIntensify/ThunderClap use the source key-abort path while their old slot is mapped.
            helper.assertTrue(client(p, "slot_abort", "0"), "skill-specific key-abort authenticated before switch");
            helper.assertFalse(active(p, id), "key-abort ends context before switch");
            client(p, "preset_switch", "1"); helper.assertFalse(active(p, id), "switch preserves callback-ended context"); close(helper, s.overload, overload, "switch nooverloadrefund");
            s.overload = 0; client(p, "preset_switch", "0"); client(p, "slot_press", "0"); helper.assertTrue(active(p, id), "fresh context active before edit callback");
            helper.assertTrue(client(p, "slot_abort", "0"), "skill-specific key-abort authenticated before other-page edit");
            helper.assertFalse(active(p, id), "key-abort ends context before other-page edit");
            client(p, "preset_edit", "1:1:" + id); helper.assertFalse(active(p, id), "other-page edit preserves callback-ended context");
            s.overload = 0; client(p, "slot_press", "0"); s.interfering = true; if (id.equals(BodyIntensify.ID)) BodyIntensify.tick(p); else ThunderClap.tick(p); helper.assertFalse(active(p, id), "interference cancels");
            s.interfering = false; s.overload = 0; client(p, "slot_press", "0"); NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p)); helper.assertFalse(active(p, id), "logoutcleanup");
        }
        helper.succeed();
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void final_em_dimension_clone_death_deactivation_and_overload_disposal(GameTestHelper helper) {
        var f = new Fixture(helper);
        for (String id : List.of(BodyIntensify.ID, ThunderClap.ID)) {
            var p = f.player(); var s = ready(p, id, 0); start(p, id);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(p, net.minecraft.world.level.Level.OVERWORLD, net.minecraft.world.level.Level.NETHER)); helper.assertFalse(active(p, id), "dimensioneventcancels");
            s.overload = 0; start(p, id); var replacement = f.player(); NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, p, false)); helper.assertFalse(active(p, id), "clonecleanupoldcontext");
            p = replacement; s = AbilityStorage.get(p); helper.assertTrue(s.learned(id), "clone preserves learned state on the replacement actor");
            s.overload = 0; helper.assertTrue(start(p, id), "replacement can start a fresh context"); client(p, "toggle", ""); helper.assertFalse(active(p, id), "deactivationcancel");
            s.activated = true; s.overload = s.maxOverload() - 1; helper.assertTrue(start(p, id), "zeroCPoverloadstart accepted"); helper.assertFalse(active(p, id) || s.overloadFine, "OverloadEventequivalent disposescontext");
            s.overload = 0; s.overloadFine = true; start(p, id); NeoForge.EVENT_BUS.post(new LivingDeathEvent(p, p.damageSources().generic())); helper.assertFalse(active(p, id) || s.activated, "deathcleanupandrecovery");
        }
        helper.succeed();
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void final_em_input_nonce_replays_and_malformed_wire_values_rejected(GameTestHelper helper) {
        var f=new Fixture(helper);
        for(String id:List.of(BodyIntensify.ID,ThunderClap.ID)){
            var p=f.player();var state=ready(p,id,0);client(p,"preset_edit","0:0:"+id);
            for(String value:List.of("0:0","0:-1","0:01","0:+1","00:1","4:1","0:1:2","0:9223372036854775808"))helper.assertFalse(client(p,"slot_press_token",value),"malformedcorrelation rejected"+value);
            helper.assertTrue(client(p,"slot_press_token","0:1"),"authenticatedcorrelatedpress starts");double paid=state.overload;
            helper.assertFalse(client(p,"slot_press_token","0:1"),"sameinflightnonce rejected");close(helper,state.overload,paid,"duplicate nonce nopayment");
            client(p,"slot_abort","0");state.overload=0;helper.assertFalse(client(p,"slot_press_token","0:1"),"retirednonce cannotrevivecancelledhold");
            helper.assertTrue(client(p,"slot_press_token","0:2"),"newphysicalpress accepted");helper.assertTrue(active(p,id),"newnonce realcontextactive");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p));state=ready(p,id,0);client(p,"preset_edit","0:0:"+id);
            helper.assertTrue(client(p,"slot_press_token","0:1"),"newconnectionhistory mayrestartnoncecounter");
        }
        helper.succeed();
    }
    private static boolean client(ServerPlayer p, String action, String value) { return AcademyGameplay.requestFromClient(p, new AcademyNetwork.Request(action, value)); }
    private static boolean active(ServerPlayer p, String id) { return id.equals(BodyIntensify.ID) ? BodyIntensify.active(p) : ThunderClap.active(p); }
    private static boolean start(ServerPlayer p, String id) { return id.equals(BodyIntensify.ID) ? BodyIntensify.start(p) : ThunderClap.start(p); }
    private static AbilityProgress ready(ServerPlayer p, String id, double exp) { var s = AbilityStorage.get(p); s.selectCategory("electromaster"); s.setLevel(5); s.learn(id); s.experience.put(id, exp); s.activated = true; return s; }
    private static void close(GameTestHelper h, double actual, double expected, String label) { h.assertTrue(Double.isFinite(actual) && Math.abs(actual - expected) < 1E-5, label + " expected=" + expected + " actual=" + actual); }
    private static final class Fixture {
        final GameTestHelper helper; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> targets = new ArrayList<>(); final List<Object> hooks = new ArrayList<>(); final Set<UUID> originalLightning = new HashSet<>(); boolean closed;
        Fixture(GameTestHelper helper) {
            this.helper = helper; for (LightningBolt lightning : lightning()) originalLightning.add(lightning.getUUID());
            helper.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
                public void testAddedForRerun(GameTestInfo old, GameTestInfo next, GameTestRunner runner) { cleanup(); }
            });
        }
        FakePlayer player() { var p = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[AC-FinalEM]")); players.add(p); Vec3 v = helper.absoluteVec(new Vec3(40.5, 1, 10.5)); p.moveTo(v.x, v.y, v.z, 0, 0); p.setYHeadRot(0); p.setNoGravity(true); p.setDeltaMovement(Vec3.ZERO); p.getAbilities().instabuild = false; return p; }
        Villager target(Vec3 point) { var v = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(1, 1, 1)); targets.add(v); v.moveTo(point.x, point.y, point.z, 0, 0); v.setNoGravity(true); v.setNoAi(true); v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); v.setHealth(200); return v; }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void ticks(int count, IntConsumer action) { for (int tick = 1; tick <= count; tick++) { int current = tick; helper.runAtTickTime(tick, () -> { if (!closed) action.accept(current); }); } }
        List<LightningBolt> lightning() { Vec3 origin = helper.absoluteVec(Vec3.ZERO); return helper.getLevel().getEntitiesOfClass(LightningBolt.class, new AABB(origin, origin.add(80, 8, 80)), e -> !originalLightning.contains(e.getUUID())); }
        void cleanup() { if (closed) return; closed = true; for (Object h : hooks) NeoForge.EVENT_BUS.unregister(h); for (ServerPlayer p : players) { BodyIntensify.remove(p); ThunderClap.remove(p); AbilityStorage.remove(p); p.discard(); } for (Entity e : targets) e.discard(); for (LightningBolt e : lightning()) e.discard(); }
    }
    public static final class AttackHook {
        final ServerPlayer owner; final Map<Entity, Integer> counts = new IdentityHashMap<>(); final Map<Entity, Double> damage = new IdentityHashMap<>();
        AttackHook(ServerPlayer owner) { this.owner = owner; }
        @SubscribeEvent public void attack(SkillAttackEvent event) { if (event.player == owner && event.skill.equals("electromaster.thunder_clap")) { counts.merge(event.target, 1, Integer::sum); damage.put(event.target, event.amount); event.amount = 0; } }
        int count(Entity e) { return counts.getOrDefault(e, 0); } double damage(Entity e) { return damage.getOrDefault(e, 0D); }
    }
}

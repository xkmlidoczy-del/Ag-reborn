package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.skill.ClassicRaytrace;
import cn.academy.port.skill.ThunderBolt;
import cn.academy.port.skill.ThunderBoltRules;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Focused native source fixtures, compiled but not run by this worker. They exercise actual
 * world queries/damage/debuffs and the trusted random seam, not client rendering or codecs.
 * Forty-wide footprint contains the twenty-block ray and both eight-block endpoint spheres.
 */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class AcademyThunderBoltRuntimeTests {
    private static final String TEMPLATE = "thunder_bolt_runtime_empty", BATCH = "academy_thunder_bolt";
    private static final String ID = ThunderBoltRules.ID;
    private AcademyThunderBoltRuntimeTests() {}

    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var tag = new CompoundTag(); var size = new ListTag();
        size.add(IntTag.valueOf(40)); size.add(IntTag.valueOf(6)); size.add(IntTag.valueOf(56)); tag.put("size", size);
        var blocks = new ListTag(); var originAir = new CompoundTag(); var origin = new ListTag();
        origin.add(IntTag.valueOf(0)); origin.add(IntTag.valueOf(0)); origin.add(IntTag.valueOf(0));
        originAir.put("pos", origin); originAir.putInt("state", 0); blocks.add(originAir); tag.put("blocks", blocks);
        tag.put("entities", new ListTag()); var palette = new ListTag(); var air = new CompoundTag();
        air.putString("Name", "minecraft:air"); palette.add(air); tag.put("palette", palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void thunder_bolt_primary_aoe_sphere_and_through_wall_selection(GameTestHelper helper) {
        var fixture = new Fixture(helper);
        try {
            var player = fixture.player(); var state = ready(player, 0);
            var primary = fixture.target(10.5, 1, 22.5);
            var throughWall = fixture.target(13.5, 1, 22.5);
            // Anchor to the exact world-space Double endpoint. int1 + Float eyeHeight
            // rounds in Float before widening, putting the former "boundary" outside64.
            Vec3 endpoint = primary.position().add(0, (double) primary.getEyeHeight(), 0);
            var boundary = fixture.targetAt(endpoint.add(8, 0, 0));
            var outside = fixture.targetAt(endpoint.add(8.001, 0, 0));
            var cubeCorner = fixture.targetAt(endpoint.add(7.5, 0, 7.5));
            helper.setBlock(new BlockPos(12, 2, 22), Blocks.STONE);
            var hook = new AttackHook(player, false, false); fixture.hook(hook);
            var selected = ThunderBolt.attackData(player);
            helper.assertTrue(selected.target() == primary, "twenty-block eye ray selects the primary");
            close(helper, selected.point().y, primary.getY() + primary.getEyeHeight(), "primary endpoint adds full eye height");
            helper.assertFalse(selected.aoes().contains(primary), "primary excluded from endpoint AOE");
            helper.assertTrue(selected.aoes().contains(throughWall), "AOE has no intervening-block line-of-sight check");
            helper.assertTrue(boundary.position().distanceToSqr(selected.point()) == 64,
                    "fixture feet are exactly radius8 in actual world-space Double coordinates");
            helper.assertTrue(selected.aoes().contains(boundary), "feet exactly eight blocks from center included");
            helper.assertFalse(selected.aoes().contains(outside), "feet just outside eight-block sphere excluded");
            helper.assertFalse(selected.aoes().contains(cubeCorner), "AABB corner excluded by sphere predicate");
            helper.assertTrue(ThunderBolt.perform(player), "novice native cast accepted");
            close(helper, primary.getHealth(), 190, "real primary receives damage10 exactly once");
            close(helper, throughWall.getHealth(), 194, "real occluded AOE receives damage6");
            close(helper, boundary.getHealth(), 194, "real sphere boundary receives AOE damage6");
            close(helper, outside.getHealth(), 200, "outside target untouched");
            close(helper, cubeCorner.getHealth(), 200, "cube corner target untouched");
            helper.assertValueEqual(hook.count(primary), 1, "primary never receives duplicate AOE attack");
            close(helper, hook.damage(primary), 10, "primary mutable event source damage");
            close(helper, hook.damage(throughWall), 6, "AOE mutable event source damage");
            helper.assertValueEqual(hook.skill, "electromaster.thunder_bolt", "exact ability damage identity");
            close(helper, state.exp(ID), .005, "one effective EXP award for all selected entities");
            helper.assertValueEqual(state.cooldowns.get(ID), 120, "captured novice cooldown");
            helper.succeed();
        } finally { fixture.cleanup(); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void thunder_bolt_aoe_slowdown_preserves_wrong_primary_target(GameTestHelper helper) {
        var fixture = new Fixture(helper);
        try {
            var player = fixture.player(); ready(player, 1);
            var primary = fixture.target(10.5, 1, 22.5);
            var aoe = fixture.target(13.5, 1, 22.5);
            // LegacyRandomSource seed2048: first double .9144 fails direct slow, second .0136
            // succeeds AOE slow. No global world generator or other fixture is changed.
            var probe = RandomSource.create(2048);
            helper.assertTrue(probe.nextDouble() >= .8 && probe.nextDouble() < .8,
                    "owned deterministic RNG produces failed-direct/successful-AOE sequence");
            helper.assertTrue(ThunderBolt.perform(player, RandomSource.create(2048)), "master cast accepted with owned chance source");
            var effect = primary.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            helper.assertTrue(effect != null, "successful AOE roll applies slowdown to PRIMARY target");
            helper.assertValueEqual(effect.getDuration(), 20, "primary only gets the AOE20t effect after failed direct roll");
            helper.assertValueEqual(effect.getAmplifier(), 3, "source Slowness IV amplifier");
            helper.assertFalse(aoe.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the AOE entity does not receive its own slowdown");
            close(helper, primary.getHealth(), 175, "master primary damage25");
            close(helper, aoe.getHealth(), 185, "master AOE damage15");
            helper.succeed();
        } finally { fixture.cleanup(); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void thunder_bolt_stationary_and_moving_miss_endpoint_quirks(GameTestHelper helper) {
        var fixture = new Fixture(helper);
        try {
            var stationary = fixture.player(); var stationaryState = ready(stationary, 1);
            var stationaryAoe = fixture.target(13.5, 1, 10.5);
            var stillData = ThunderBolt.attackData(stationary);
            helper.assertTrue(stillData.target() == null, "stationary aim ray has no primary");
            close(helper, stillData.point().distanceTo(stationary.getEyePosition()), 0, "stationary NaN source miss is guarded at caster eye");
            helper.assertTrue(stillData.aoes().contains(stationaryAoe), "finite stationary endpoint sphere selects nearby target");
            helper.assertTrue(ThunderBolt.perform(stationary, RandomSource.create(2048)), "AOE-only cast with null primary completes safely");
            close(helper, stationaryAoe.getHealth(), 185, "AOE-only master damage applied");
            helper.assertFalse(stationaryAoe.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "null primary skips wrong-target AOE slowdown safely");
            close(helper, stationaryState.exp(ID), 1, "mastery remains capped");

            var moving = fixture.player(); ready(moving, 0);
            moving.setDeltaMovement(new Vec3(.1, 0, 0));
            var movingAoe = fixture.target(30.5, 1, 10.5);
            var movingData = ThunderBolt.attackData(moving);
            helper.assertTrue(movingData.target() == null, "moving aim ray still has no primary");
            close(helper, movingData.point().x, moving.getEyePosition().x + 20, "null-ray miss endpoint follows normalized velocity+X");
            close(helper, movingData.point().z, moving.getEyePosition().z, "null-ray endpoint ignores aim+Z");
            helper.assertTrue(movingData.aoes().contains(movingAoe), "velocity endpoint selects moving-miss AOE");
            helper.assertFalse(movingData.aoes().contains(stationaryAoe), "stationary-eye target lies outside displaced endpoint sphere");
            helper.assertTrue(ThunderBolt.perform(moving), "moving miss accepted");
            close(helper, movingAoe.getHealth(), 194, "moving miss endpoint damages its selected AOE");
            close(helper, stationaryAoe.getHealth(), 185, "moving miss does not reattack stationary target");
            helper.succeed();
        } finally { fixture.cleanup(); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void thunder_bolt_consumption_cooldown_reentry_and_replay_are_single_commit(GameTestHelper helper) {
        var fixture = new Fixture(helper);
        try {
            var player = fixture.player(); var state = ready(player, 0);
            var primary = fixture.target(10.5, 1, 22.5);
            var hook = new AttackHook(player, true, true); fixture.hook(hook);
            double beforeCp = state.cp;
            helper.assertTrue(ThunderBolt.perform(player), "initial cast accepted");
            helper.assertFalse(hook.nestedAccepted, "reentrant damage callback cannot start another in-flight context");
            helper.assertFalse(ThunderBolt.perform(player), "immediate replay rejected by cooldown");
            helper.assertValueEqual(hook.count(primary), 1, "only one damage-hook attempt despite reentry/replay");
            close(helper, primary.getHealth(), 200, "fixture hook rejects damage");
            close(helper, state.cp, beforeCp - 280, "one CP payment");
            close(helper, state.overload, 50, "one overload payment");
            close(helper, state.extraCp, .7, "one CP training contribution");
            close(helper, state.extraOverload, .29, "one overload training contribution");
            close(helper, state.exp(ID), .005, "rejected damage still counts selected target as effective");
            close(helper, state.levelExperience, .005, "one level EXP contribution");
            helper.assertValueEqual(state.cooldowns.get(ID), 120, "cooldown starts from captured mastery");
            helper.assertValueEqual(state.cpDelay, 15, "CP recovery delay installed");
            helper.assertValueEqual(state.overloadDelay, 32, "overload recovery delay installed");

            var poor = fixture.player(); var poorState = ready(poor, 0); poorState.cp = 279;
            helper.assertFalse(ThunderBolt.perform(poor), "insufficient CP rejects before any attacks");
            close(helper, poorState.cp, 279, "failed cast leaves CP unchanged");
            close(helper, poorState.overload, 0, "failed cast leaves overload unchanged");
            close(helper, poorState.exp(ID), 0, "failed cast gives no mastery");
            helper.assertTrue(poorState.cooldowns.isEmpty(), "failed cast gives no cooldown");
            helper.succeed();
        } finally { fixture.cleanup(); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void thunder_bolt_network_slots_allow_natural_mastery_and_reject_name_spoof(GameTestHelper helper) {
        var fixture = new Fixture(helper);
        try {
            var player = fixture.player(); var state = ready(player, 0);
            helper.assertTrue(state.exp(ID) == 0 && ThunderBoltRules.canUse(state), "normal learned zero-mastery state can cast");
            helper.assertTrue(state.presets.edit(0, 0, ID, id -> id.equals(ID)), "fixture installs owned learned skill in current slot");
            double beforeCp = state.cp;
            helper.assertFalse(AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("cast", ID)),
                    "wire ingress rejects arbitrary skill-name cast spoof");
            close(helper, state.cp, beforeCp, "spoof consumes nothing");
            helper.assertTrue(AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "0")),
                    "authenticated implemented preset slot reaches immediate ThunderBolt dispatcher after integration");
            close(helper, state.cp, beforeCp - 280, "slot cast pays novice CP280");
            close(helper, state.exp(ID), .003, "empty ray/sphere slot cast gets miss EXP");
            helper.assertFalse(AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "0")),
                    "slot replay blocked by authoritative cooldown");
            close(helper, state.cp, beforeCp - 280, "slot replay never repays resources");
            helper.succeed();
        } finally { fixture.cleanup(); }
    }

    private static AbilityProgress ready(ServerPlayer player, double mastery) {
        var state = AbilityStorage.get(player); state.selectCategory("electromaster"); state.setLevel(4);
        state.learn(ID); state.experience.put(ID, mastery); state.activated = true; return state;
    }
    private static void close(GameTestHelper helper, double actual, double expected, String label) {
        helper.assertTrue(Double.isFinite(actual) && Math.abs(actual - expected) < .00001,
                label + ": expected " + expected + ", got " + actual);
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final List<ServerPlayer> players = new ArrayList<>();
        final List<Entity> targets = new ArrayList<>();
        final List<Object> hooks = new ArrayList<>();
        boolean closed;
        Fixture(GameTestHelper helper) {
            this.helper = helper;
            helper.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
                public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) { cleanup(); }
            });
        }
        FakePlayer player() {
            var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[AC-ThunderTest]"));
            players.add(player);
            var position = helper.absoluteVec(new Vec3(10.5, 1, 10.5));
            player.moveTo(position.x, position.y, position.z, 0, 0); player.setYHeadRot(0);
            player.setNoGravity(true); player.setDeltaMovement(Vec3.ZERO); player.getAbilities().instabuild = false;
            close(helper, ClassicRaytrace.direction(player).z, 1, "fixture head ray points world+Z");
            return player;
        }
        Villager target(double x, double y, double z) {
            var target = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(x, y, z));
            targets.add(target); target.setNoAi(true); target.setNoGravity(true); target.setDeltaMovement(Vec3.ZERO);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); target.setHealth(200); return target;
        }
        Villager targetAt(Vec3 worldPosition) {
            // Use the existing owned spawn/cleanup path, then move directly to the source endpoint.
            var target = target(0, 0, 0);
            target.moveTo(worldPosition.x, worldPosition.y, worldPosition.z, 0, 0);
            return target;
        }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void cleanup() {
            if (closed) return; closed = true; RuntimeException failure = null;
            for (var hook : hooks) failure = attempt(() -> NeoForge.EVENT_BUS.unregister(hook), failure);
            for (var player : players) failure = attempt(() -> {
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                AbilityStorage.remove(player); player.discard();
            }, failure);
            for (var target : targets) failure = attempt(target::discard, failure);
            if (failure != null) throw failure;
        }
        private static RuntimeException attempt(Runnable action, RuntimeException failure) {
            try { action.run(); }
            catch (RuntimeException next) { if (failure == null) failure = next; else failure.addSuppressed(next); }
            return failure;
        }
    }

    public static final class AttackHook {
        final ServerPlayer owner;
        final boolean zeroDamage, attemptReentry;
        final Map<Entity, Integer> calls = new IdentityHashMap<>();
        final Map<Entity, Double> damages = new IdentityHashMap<>();
        String skill;
        boolean nestedAccepted;
        AttackHook(ServerPlayer owner, boolean zeroDamage, boolean attemptReentry) {
            this.owner = owner; this.zeroDamage = zeroDamage; this.attemptReentry = attemptReentry;
        }
        @SubscribeEvent public void attack(SkillAttackEvent event) {
            if (event.player != owner || !"electromaster.thunder_bolt".equals(event.skill)) return;
            calls.merge(event.target, 1, Integer::sum); damages.put(event.target, event.amount); skill = event.skill;
            if (zeroDamage) event.amount = 0;
            if (attemptReentry) nestedAccepted |= ThunderBolt.perform(owner);
        }
        int count(Entity entity) { return calls.getOrDefault(entity, 0); }
        double damage(Entity entity) { return damages.getOrDefault(entity, 0.0); }
    }
}

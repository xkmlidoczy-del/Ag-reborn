package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.highpower.EsperLegacyBypass;
import cn.academy.port.highpower.EsperShields;
import cn.academy.port.highpower.HighPowerCategories;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.worldboss.Clips;
import cn.academy.port.worldboss.WorldBossCombat;
import cn.academy.port.worldboss.WorldBossContent;
import cn.academy.port.worldboss.WorldBossEnvironment;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * One serial, isolated native-world suite: no direct calls to combat helpers, artificial
 * event receipts, damage cooldown resets, or accelerated entity ticks. Each attack is
 * started through the same play/tick/clip-event path as its server AI. Fixture anchors
 * only hold stationary targets inside the authored hit volumes; attempted budgets are
 * sampled throughout the cast so native invulnerability cannot conceal over-attacking.
 */
@GameTestHolder("academy_worldboss")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class WorldBossRuntimeTests {
    private WorldBossRuntimeTests() {}
    private static final int[] ATTACKS = {Clips.ROAR, Clips.BITE, Clips.COMBO, Clips.RAM,
            Clips.LRAKE, Clips.RUPPER, Clips.TSWEEP, Clips.TSLAM, Clips.STOMP, Clips.TWIN,
            Clips.CHARGE, Clips.BREATH, Clips.LANCE, Clips.METEOR, Clips.NOVA, Clips.WORLDBREAKER};
    private static final float[] EXPECTED_CAPS = {0, 0, 0, 4, 10, 12, 12, 10, 10, 10, 12, 10, 12, 10, 10, 12, 12, 14, 16};
    private static final int[] EARLIEST = {0, 0, 0, 41, 36, 33, 38, 37, 41, 50, 48, 43, 39, 42, 46, 57, 42, 53, 67};
    private static final double[][] STATIONS = {{0, 2, -20}, {0, 2, 24}, {5, 1, 0},
            {0, 2, -32}, {0, 18, -32}, {0, 2, 0}};

    @SubscribeEvent public static void installTemplate(LevelEvent.Load event) {
        if(!GameTestHooks.isGametestEnabled()||!(event.getLevel() instanceof ServerLevel level))return;
        var tag=new CompoundTag();var cell=new CompoundTag();var air=new CompoundTag();
        var size=new ListTag();var position=new ListTag();var blocks=new ListTag();var palette=new ListTag();
        for(int dimension:new int[]{32,40,32})size.add(IntTag.valueOf(dimension));
        for(int axis=0;axis<3;axis++)position.add(IntTag.valueOf(0));
        cell.put("pos",position);cell.putInt("state",0);blocks.add(cell);air.putString("Name","minecraft:air");palette.add(air);
        tag.put("size",size);tag.put("blocks",blocks);tag.put("palette",palette);tag.put("entities",new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_worldboss","highpower_empty"))
            .load(level.registryAccess().lookupOrThrow(Registries.BLOCK),tag);
    }

    @GameTest(template="highpower_empty", batch="worldboss_serial_native", timeoutTicks=8000)
    public static void registered_sixty_hp_boss_all_attacks_budgets_cancellation_shields_and_lifecycle(GameTestHelper h) {
        new Fixture(h).begin();
    }

    private static BigDecimal health(LivingEntity e) { return new BigDecimal(HighPowerCombat.healthText(e)); }
    private static void near(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(Math.abs(actual - expected) <= 0.00001, what + ": " + actual + " != " + expected);
    }

    public static final class Trace {
        final Fixture f;
        WorldBossContent.Boss caster;
        int clip;
        long sequence;
        final Map<LivingEntity, Float> incoming = new IdentityHashMap<>(), post = new IdentityHashMap<>(), spent = new IdentityHashMap<>(), expectedNativeHealth = new IdentityHashMap<>();
        final Map<LivingEntity, Double> maximumMotionSquared = new IdentityHashMap<>();
        final Set<LivingEntity> canceled = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<LivingEntity, Integer> events = new IdentityHashMap<>(), knockbackCanceled = new IdentityHashMap<>(), knockbackOpen = new IdentityHashMap<>();
        final List<String> errors = new ArrayList<>();
        Trace(Fixture f) { this.f = f; }
        void start(WorldBossContent.Boss b, int c) {
            caster = b; clip = c; incoming.clear(); post.clear(); spent.clear(); events.clear();
            knockbackCanceled.clear(); knockbackOpen.clear(); canceled.clear(); errors.clear(); expectedNativeHealth.clear(); maximumMotionSquared.clear();
            for (LivingEntity target : f.observed) if (!HighPowerCombat.enabled(target)) expectedNativeHealth.put(target, target.getHealth());
            b.play(c); sequence = b.castSequence();
        }
        void sample() {
            if (caster == null || caster.isRemoved()) return;
            for (LivingEntity target : f.observed) {
                float value = caster.damageSpent(target.getUUID());
                if (!Float.isFinite(value) || value < 0 || value > WorldBossCombat.maximumDamage(clip) + 0.00001F)
                    errors.add("invalid or excessive attempted budget " + value + " clip=" + clip);
                spent.merge(target, value, Math::max);
                maximumMotionSquared.merge(target, target.getDeltaMovement().lengthSqr(), Math::max);
            }
        }
        @SubscribeEvent(priority=EventPriority.HIGHEST)
        public void cancel(LivingIncomingDamageEvent e) {
            if (e.getSource().getEntity() == caster && canceled.contains(e.getEntity())) e.setCanceled(true);
        }
        @SubscribeEvent(priority=EventPriority.LOWEST, receiveCanceled=true)
        public void incoming(LivingIncomingDamageEvent e) {
            if (caster == null || !f.observed.contains(e.getEntity())) return;
            // Environmental contact is part of the restored arena design. Seed the
            // ordinary float ledger for every actual native source, not only the boss.
            if (!HighPowerCombat.enabled(e.getEntity())) expectedNativeHealth.putIfAbsent(e.getEntity(), e.getEntity().getHealth());
            if (e.getSource().getEntity() != caster) return;
            if (e.getSource().getDirectEntity() != caster || !e.getSource().is(DamageTypes.MOB_ATTACK))
                errors.add("damage lost native boss ownership or canonical mob attack source");
            if (caster.level().getGameTime() - caster.start() < EARLIEST[clip]) errors.add("damage before authored attack window");
            if (!Float.isFinite(e.getAmount()) || e.getAmount() <= 0) errors.add("invalid native damage amount");
            incoming.merge(e.getEntity(), e.getAmount(), Float::sum);
            events.merge(e.getEntity(), 1, Integer::sum);
        }
        @SubscribeEvent public void post(LivingDamageEvent.Post e) {
            if (caster == null || !f.observed.contains(e.getEntity())) return;
            if (e.getSource().getEntity() == caster) post.merge(e.getEntity(), e.getNewDamage(), Float::sum);
            else if (e.getNewDamage() > 0) System.out.println("ACA_WORLDBOSS_NATIVE_OTHER_SOURCE target=" + e.getEntity().getScoreboardName()
                    + " type=" + e.getSource().typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("unregistered")
                    + " damage=" + e.getNewDamage() + " hp=" + e.getEntity().getHealth());
            if (!HighPowerCombat.enabled(e.getEntity())) {
                // Native LivingEntity.actuallyHurt performs float subtraction for EVERY Post,
                // not a double comparison of final HP loss against the sum of raw floats.
                float before = expectedNativeHealth.getOrDefault(e.getEntity(), e.getEntity().getMaxHealth());
                float expected = Math.max(0F, Math.min(e.getEntity().getMaxHealth(), before - e.getNewDamage()));
                expectedNativeHealth.put(e.getEntity(), expected);
                if (Float.floatToIntBits(expected) != Float.floatToIntBits(e.getEntity().getHealth()))
                    errors.add("native sequential float HP mismatch: expected=" + expected + " actual=" + e.getEntity().getHealth()
                            + " entity=" + e.getEntity().getScoreboardName() + " source=" + e.getSource().typeHolder().unwrapKey());
            }
        }
        @SubscribeEvent(priority=EventPriority.LOWEST, receiveCanceled=true)
        public void knockback(LivingKnockBackEvent e) {
            if (!f.observed.contains(e.getEntity())) return;
            (e.isCanceled() ? knockbackCanceled : knockbackOpen).merge(e.getEntity(), 1, Integer::sum);
        }
        void valid() {
            f.h.assertTrue(errors.isEmpty(), "native trace errors: " + errors);
            float cap = WorldBossCombat.maximumDamage(clip);
            for (LivingEntity e : f.observed) {
                f.h.assertTrue(incoming.getOrDefault(e, 0F) <= cap + .00001F,
                        "native incoming sums retain whole-action cap " + clip + " sum=" + incoming.getOrDefault(e, 0F));
                f.h.assertTrue(spent.getOrDefault(e, 0F) <= cap + .00001F, "attempts retain whole-action cap even through invulnerability");
            }
        }
    }

    public static final class Fixture {
        final GameTestHelper h;
        final ServerLevel level;
        final List<Entity> entities = new ArrayList<>();
        final List<ServerPlayer> players = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>();
        final List<Object> hooks = new ArrayList<>();
        final List<PlayerTeam> teams = new ArrayList<>();
        final Map<ServerLevel, List<ChunkPos>> forcedChunks = new IdentityHashMap<>();
        final List<LivingEntity> observed = new ArrayList<>();
        final Map<LivingEntity, Vec3> anchors = new IdentityHashMap<>();
        final Map<BlockPos, BlockState> fixtureBlocks = new LinkedHashMap<>();
        final Vec3 origin;
        final Trace trace;
        ServerPlayer aim;
        WorldBossContent.Boss boss;
        boolean closed, forceShields;
        int verifiedAttacks;

        Fixture(GameTestHelper h) {
            this.h = h; level = h.getLevel(); origin = h.absoluteVec(new Vec3(16, 180, 16));
            // Ordinary combat fixtures stay above ground so restored pit/scorch/spike
            // mechanics cannot accidentally turn their damage oracle into an occlusion test.
            trace = new Trace(this); hook(trace); hook(this);
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {}
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        Vec3 local(double x, double y, double z) { return origin.add(-x, y, -z); }
        WorldBossContent.Boss boss() { return boss(origin); }
        WorldBossContent.Boss boss(Vec3 at) {
            var b = WorldBossContent.BOSS.get().create(level);
            h.assertTrue(b != null, "registered boss creates native entity");
            b.setNoAi(true); b.setNoGravity(true); b.moveTo(at.x, at.y, at.z, 0, 0);
            b.setYHeadRot(0); b.yBodyRot = 0;
            h.assertTrue(level.addFreshEntity(b), "boss joins real native level"); entities.add(b); return b;
        }
        Husk target(Vec3 at) {
            var target = new Husk(EntityType.HUSK, level); target.setNoAi(true); target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); target.setHealth(200);
            target.getAttribute(Attributes.ARMOR).setBaseValue(0); target.setPos(at);
            h.assertTrue(level.addFreshEntity(target), "native ordinary 200 HP observation target joins level");
            entities.add(target); observed.add(target); anchors.put(target, at); return target;
        }
        ServerPlayer player(String name, GameType mode, Vec3 at) {
            var server = level.getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
            var p = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); var channel = new EmbeddedChannel(connection);
            channels.add(channel); NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, p, cookie); players.add(p);
            p.setGameMode(mode); p.setNoGravity(true); p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); p.setHealth(200);
            p.getFoodData().setFoodLevel(10); p.setPos(at); p.connection.resetPosition(); observed.add(p); anchors.put(p, at);
            // A genuine ServerPlayer.tick in its target chunk naturally expires vanilla spawn protection.
            // GameTest only force-loads the 32-block structure; ranged players stand outside that box.
            var chunk=new ChunkPos(BlockPos.containing(at));
            if(!level.getForcedChunks().contains(chunk.toLong())) {
                level.setChunkForced(chunk.x,chunk.z,true);forcedChunks.computeIfAbsent(level,key->new ArrayList<>()).add(chunk);
            }
            level.getChunkSource().move(p);
            hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, 8005)); return p;
        }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void before(ServerTickEvent.Pre e) {
            if (closed || e.getServer() != level.getServer()) return;
            for (var entry : anchors.entrySet()) {
                var target = entry.getKey(); if (target.isRemoved() || !target.isAlive()) continue;
                target.setPos(entry.getValue()); target.setDeltaMovement(Vec3.ZERO);
                if (target instanceof ServerPlayer p) p.connection.resetPosition();
            }
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void after(ServerTickEvent.Post e) {
            if (!closed && e.getServer() == level.getServer()) trace.sample();
        }
        void begin() {
            for (int clip = 0; clip < EXPECTED_CAPS.length; clip++) near(h, WorldBossCombat.maximumDamage(clip), EXPECTED_CAPS[clip], "independent declared vanilla-scale action budget " + clip);
            boss = boss();
            near(h, boss.getMaxHealth(), 60, "registered MAX_HEALTH is vanilla sixty");
            near(h, boss.getHealth(), 60, "fresh native HP is sixty");
            near(h, boss.getAttributeValue(Attributes.ATTACK_DAMAGE), 10, "registered ATTACK_DAMAGE is vanilla ten");
            h.assertFalse(HighPowerCombat.enabled(boss), "ordinary boss is not enrolled into normalized high-power health");
            var attacker = target(local(0, 2, 50));
            h.assertTrue(boss.hurt(boss.damageSources().mobAttack(attacker), 10), "native mob hurt accepts finite incoming damage");
            near(h, boss.getHealth(), 50, "sixty minus native ten is fifty");
            var saved = boss.saveWithoutId(new CompoundTag()); boss.discard();
            h.runAfterDelay(2, () -> {
                boss = WorldBossContent.BOSS.get().create(level); h.assertTrue(boss != null, "cold registered recreation exists");
                boss.load(saved); boss.setNoAi(true); boss.setNoGravity(true); entities.add(boss);
                h.assertTrue(level.addFreshEntity(boss), "native saved entity reloads after old object removal");
                near(h, boss.getHealth(), 50, "native save/load retains damaged health");
                h.assertTrue(boss.pendingMeteorCount() == 0, "cold load cannot resurrect scheduled meteor work");
                h.assertFalse(HighPowerCombat.enabled(boss), "load does not silently enroll or refill boss");
                h.assertTrue(boss.hurt(boss.damageSources().mobAttack(attacker), 100), "native lethal hit accepted");
                h.assertFalse(boss.isAlive(), "native lethal damage enters real death lifecycle");
                h.runAfterDelay(25, () -> {
                    h.assertTrue(boss.isRemoved(), "real dying mob is removed after vanilla death ticks");
                    clearTargets();
                    aim = player("WBVanillaAim", GameType.SURVIVAL, local(0, 2, -32));
                    h.runAfterDelay(61, () -> attack(0));
                });
            });
        }
        void clearTargets() {
            for (LivingEntity target : List.copyOf(observed)) if (!(target instanceof ServerPlayer)) {
                target.discard(); observed.remove(target); anchors.remove(target);
            }
        }
        void attack(int index) {
            if (index == ATTACKS.length) { chargeMovement(); return; }
            if (boss != null) boss.discard(); clearTargets(); boss = boss();
            aim.setHealth(200); anchors.put(aim, local(0, 2, -32)); aim.setPos(anchors.get(aim)); boss.setTarget(aim);
            int clip = ATTACKS[index];
            if(clip==Clips.METEOR) {
                // Ground-targeted meteors must be tested over an actual surface, not an airborne fixture over the void.
                BlockPos floor=BlockPos.containing(local(0,1,-32));
                fixtureBlocks.putIfAbsent(floor,level.getBlockState(floor));level.setBlock(floor,Blocks.STONE.defaultBlockState(),3);
            }
            trace.start(boss, clip);
            var positive = new ArrayList<LivingEntity>(); var denied = new ArrayList<LivingEntity>();
            for (double[] station : STATIONS) {
                Vec3 at = local(station[0], station[1], station[2]);
                positive.add(target(at)); var cancel = target(at); denied.add(cancel); trace.canceled.add(cancel);
            }
            h.runAfterDelay(Clips.get(clip).ticks() + 65, () -> {
                trace.valid(); int productive = 0, canceled = 0;
                for (LivingEntity target : positive) {
                    if (trace.events.getOrDefault(target, 0) > 0) productive++;
                    h.assertTrue(Float.floatToIntBits(target.getHealth()) == Float.floatToIntBits(trace.expectedNativeHealth.getOrDefault(target, 200F)),
                            "only the exact native sequence of float HP subtractions changes ordinary HP");
                    // Native contact with the authored magma/fire terrain is separately
                    // observed by the complete float ledger; it is not a second boss hit.
                }
                for (LivingEntity target : denied) {
                    if (trace.events.getOrDefault(target, 0) > 0) {
                        canceled++;
                        h.assertTrue(trace.spent.getOrDefault(target, 0F) > 0, "canceled damage still consumes a finite cast budget");
                        h.assertTrue(trace.knockbackOpen.getOrDefault(target, 0) == 0, "canceled native hit cannot add free knockback");
                    }
                    h.assertTrue(trace.post.getOrDefault(target, 0F) == 0F, "external veto produces zero boss-source native Post damage");
                    h.assertTrue(Float.floatToIntBits(target.getHealth()) == Float.floatToIntBits(trace.expectedNativeHealth.getOrDefault(target, 200F)),
                            "vetoed target HP exactly follows all native environmental Posts, with no canceled boss damage");
                }
                h.assertTrue(productive > 0, "clip " + clip + " has a real native positive hit, not merely metadata/FX");
                h.assertTrue(canceled > 0, "clip " + clip + " actually exercised a native event veto");
                h.assertTrue(boss.pendingMeteorCount() == 0, "all scheduled work drained at action end " + clip);
                verifiedAttacks++; System.out.println("ACA_WORLDBOSS_NATIVE_ATTACK_PASS clip=" + clip + " positive=" + productive + " veto=" + canceled + " cap=" + WorldBossCombat.maximumDamage(clip));
                attack(index + 1);
            });
        }
        void chargeMovement() {
            if (boss != null) boss.discard(); clearTargets();
            aim.setGameMode(GameType.SPECTATOR); anchors.put(aim, local(0, 2, 70));
            // Keep every block on the genuine forward path and the receiver ticking.
            for (int z = 0; z <= 48; z += 8) {
                var chunk = new ChunkPos(BlockPos.containing(origin.add(0, 0, z)));
                if (!level.getForcedChunks().contains(chunk.toLong())) {
                    level.setChunkForced(chunk.x, chunk.z, true);
                    forcedChunks.computeIfAbsent(level, key -> new ArrayList<>()).add(chunk);
                }
            }
            final var runner = boss(); boss = runner; runner.play(Clips.IDLE);
            final var receiver = target(origin.add(0, 2, 32));
            final int initialBossTicks = runner.tickCount, initialTargetTicks = receiver.tickCount;
            awaitChargeTicks(runner, receiver, initialBossTicks, initialTargetTicks, 2000, () -> {
                runner.setTarget(receiver); runner.setNoAi(false); trace.start(runner, Clips.CHARGE);
                final long sequence = runner.castSequence();
                final Vec3 startPosition = runner.position(); final int startTicks = runner.tickCount;
                h.runAfterDelay(41, () -> {
                    final Vec3 before = runner.position(), forward = runner.forward();
                    final int beforeTicks = runner.tickCount;
                    final long age41 = level.getGameTime() - runner.start();
                    System.out.println("ACA_WORLDBOSS_CHARGE_POSITION stage=before-window age=" + age41
                            + " ticks=" + beforeTicks + " pos=" + before + " start=" + startPosition
                            + " forward=" + forward + " noAi=" + runner.isNoAi());
                    h.assertTrue(runner.tickCount > startTicks && runner.castSequence() == sequence
                            && runner.clip() == Clips.CHARGE && !runner.isNoAi(),
                            "native ticking AI-enabled caster reaches the charge movement window");
                    h.assertTrue(before.subtract(startPosition).horizontalDistanceSqr() <= 1e-10,
                            "charge does not move the caster before its authored movement window");
                    h.runAfterDelay(29, () -> {
                        Vec3 after = runner.position(), delta = after.subtract(before);
                        double forwardDistance = delta.dot(forward), horizontal = Math.sqrt(delta.horizontalDistanceSqr());
                        System.out.println("ACA_WORLDBOSS_CHARGE_POSITION stage=inside-window age="
                                + (level.getGameTime() - runner.start()) + " ticks=" + runner.tickCount
                                + " pos=" + after + " delta=" + delta + " forwardDistance=" + forwardDistance
                                + " horizontalDistance=" + horizontal + " noAi=" + runner.isNoAi()
                                + " incoming=" + trace.events.getOrDefault(receiver, 0)
                                + " post=" + trace.post.getOrDefault(receiver, 0F)
                                + " spent=" + runner.damageSpent(receiver.getUUID()));
                        h.assertTrue(!runner.isRemoved() && !runner.isNoAi() && runner.clip() == Clips.CHARGE
                                && runner.castSequence() == sequence && runner.tickCount >= beforeTicks + 25,
                                "charge displacement comes from uninterrupted native entity ticks");
                        h.assertTrue(horizontal > 10 && forwardDistance > 10,
                                "AI-enabled charge moves the actual native boss over ten blocks forward");
                        trace.valid();
                        h.assertTrue(trace.post.getOrDefault(receiver, 0F) > 0
                                && runner.damageSpent(receiver.getUUID()) <= 10,
                                "moving charge reaches its forward native receiver within the same ten-damage budget");
                        runner.setNoAi(true); runner.setDeltaMovement(Vec3.ZERO); runner.getNavigation().stop(); runner.discard();
                        clearTargets(); aim.setGameMode(GameType.SURVIVAL);
                        System.out.println("ACA_WORLDBOSS_NATIVE_CHARGE_MOVEMENT_PASS age41=" + age41
                                + " age70=" + (level.getGameTime() - runner.start())
                                + " forwardDistance=" + forwardDistance + " horizontalDistance=" + horizontal
                                + " nativeBossTicks=" + (runner.tickCount - startTicks));
                        exclusions();
                    });
                });
            });
        }
        void awaitChargeTicks(WorldBossContent.Boss runner, LivingEntity receiver, int initialBossTicks,
                              int initialTargetTicks, int remaining, Runnable ready) {
            if (level.isPositionEntityTicking(runner.blockPosition())
                    && level.isPositionEntityTicking(receiver.blockPosition())
                    && runner.tickCount >= initialBossTicks + 3 && receiver.tickCount >= initialTargetTicks + 3) {
                System.out.println("ACA_WORLDBOSS_CHARGE_READY bossTicks=" + runner.tickCount
                        + " receiverTicks=" + receiver.tickCount + " bossPos=" + runner.position()
                        + " receiverPos=" + receiver.position()); ready.run(); return;
            }
            if (remaining <= 0 || runner.isRemoved() || receiver.isRemoved()) {
                h.assertTrue(false, "charge fixture requires genuine native entity ticks before timed movement; no manual ticking");
                return;
            }
            h.runAfterDelay(10, () -> awaitChargeTicks(runner, receiver, initialBossTicks,
                    initialTargetTicks, remaining - 10, ready));
        }
        void exclusions() {
            boss.discard(); clearTargets(); boss = boss();
            var victim = target(local(0, 2, 0));
            var allied = target(local(0, 2, 0));
            var creative = player("WBCreative", GameType.CREATIVE, local(0, 2, 0));
            var spectator = player("WBSpectator", GameType.SPECTATOR, local(0, 2, 0));
            var otherBoss = boss(); observed.add(otherBoss); anchors.put(otherBoss, origin);
            var team = level.getScoreboard().addPlayerTeam("wb" + UUID.randomUUID().toString().substring(0, 10)); teams.add(team);
            team.setAllowFriendlyFire(true);
            level.getScoreboard().addPlayerToTeam(boss.getScoreboardName(), team);
            level.getScoreboard().addPlayerToTeam(allied.getScoreboardName(), team);
            anchors.put(aim, local(0, 2, -32)); boss.setTarget(aim); trace.start(boss, Clips.NOVA);
            h.runAfterDelay(Clips.get(Clips.NOVA).ticks() + 5, () -> {
                trace.valid(); h.assertTrue(trace.events.getOrDefault(victim, 0) > 0, "exclusion matrix has ordinary hostile positive control");
                for (LivingEntity e : List.of(allied, creative, spectator, otherBoss)) {
                    h.assertTrue(trace.events.getOrDefault(e, 0) == 0, "creative/spectator/team/other boss never receives attempted native damage");
                    h.assertTrue(trace.spent.getOrDefault(e, 0F) == 0, "excluded entity never consumes cast budget");
                    h.assertTrue(trace.knockbackOpen.getOrDefault(e, 0) == 0, "excluded entity receives no native knockback");
                    h.assertTrue(e.getActiveEffects().isEmpty() && !e.isOnFire(), "excluded entity receives no hidden status effects");
                }
                creative.setGameMode(GameType.SPECTATOR); otherBoss.discard(); clearTargets();
                meteors();
            });
        }
        void meteors() {
            boss.discard(); boss = boss(); var second = boss();
            anchors.put(aim, local(0, 2, -32)); aim.setPos(anchors.get(aim)); aim.setHealth(200);
            boss.setTarget(aim); second.setTarget(aim); trace.start(second, Clips.METEOR); boss.play(Clips.METEOR);
            var removed = boss;
            final int[] discardedDamage = {0};
            Object vetoWitness = new Object() { @SubscribeEvent public void damage(LivingIncomingDamageEvent e) {
                if (e.getSource().getEntity() == removed) discardedDamage[0]++;
            }}; hook(vetoWitness);
            h.runAfterDelay(44, () -> {
                h.assertTrue(removed.pendingMeteorCount() > 0 && second.pendingMeteorCount() > 0, "two independent casters schedule their own actual meteor telegraphs");
                removed.discard(); h.assertTrue(removed.pendingMeteorCount() == 0, "native removal clears only removed caster pending work");
                h.runAfterDelay(Clips.get(Clips.METEOR).ticks() + 60, () -> {
                    trace.valid(); h.assertTrue(discardedDamage[0] == 0, "removed caster meteors never deal late damage");
                    System.out.println("ACA_WORLDBOSS_METEOR_DIAGNOSTIC hp="+aim.getHealth()+" alive="+aim.isAlive()+" removed="+aim.isRemoved()+" pos="+aim.position()+" floor="+level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,(int)Math.floor(aim.getX()),(int)Math.floor(aim.getZ()))+" eligible="+WorldBossCombat.eligibleTarget(second,aim)+" spent="+second.damageSpent(aim.getUUID())+" events="+trace.events.getOrDefault(aim,0)+" caster="+second.clip()+" target="+second.getTarget());
                    h.assertTrue(trace.events.getOrDefault(aim, 0) > 0, "removing one caster does not cancel another caster's actual impacts");
                    h.assertTrue(second.pendingMeteorCount() == 0, "surviving caster drains its own pending work");
                    boss = second; trace.start(boss, Clips.METEOR);
                    h.runAfterDelay(44, () -> {
                        h.assertTrue(boss.pendingMeteorCount() > 0, "new actual meteor cast has live pending work");
                        boss.play(Clips.IDLE); h.assertTrue(boss.pendingMeteorCount() == 0, "new animation cancels old cast's pending work");
                        h.runAfterDelay(90, () -> {
                            h.assertTrue(trace.events.getOrDefault(aim, 0) == 0, "canceled telegraphs cannot hit during later idle");
                            boss.discard(); dimensions();
                        });
                    });
                });
            });
        }
        void dimensions() {
            clearTargets(); boss = boss(); boss.setTarget(aim); boss.play(Clips.METEOR);
            var old = boss;
            h.runAfterDelay(44, () -> {
                h.assertTrue(old.pendingMeteorCount() > 0, "dimension-transfer fixture has actual pending meteor work");
                var nether = level.getServer().getLevel(Level.NETHER);
                h.assertTrue(nether != null && nether != level, "native independent Nether world exists");
                Vec3 there = new Vec3(origin.x, 150, origin.z);
                ChunkPos center = new ChunkPos(BlockPos.containing(there));
                var added = new ArrayList<ChunkPos>(); forcedChunks.put(nether, added);
                for (int x = center.x - 1; x <= center.x + 1; x++) for (int z = center.z - 1; z <= center.z + 1; z++) {
                    var chunk = new ChunkPos(x, z);
                    if (!nether.getForcedChunks().contains(chunk.toLong())) { nether.setChunkForced(x, z, true); added.add(chunk); }
                }
                Entity transition = old.changeDimension(new DimensionTransition(nether, there, Vec3.ZERO, 0, 0, DimensionTransition.DO_NOTHING));
                h.assertTrue(transition instanceof WorldBossContent.Boss, "real native entity dimension transition recreates registered boss");
                var moved = (WorldBossContent.Boss) transition; entities.add(moved); moved.setNoAi(true); moved.setNoGravity(true);
                h.assertTrue(old.isRemoved() && old.pendingMeteorCount() == 0 && moved.pendingMeteorCount() == 0,
                        "dimension transition cancels old owner work instead of transporting delayed meteors");
                h.assertTrue(moved.level() == nether && moved.clip() == Clips.IDLE, "cold native dimension copy starts safely idle in its actual destination");
                var other = new Husk(EntityType.HUSK, nether); other.setNoAi(true); other.setNoGravity(true);
                other.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); other.setHealth(200); other.getAttribute(Attributes.ARMOR).setBaseValue(0);
                Vec3 point = there.add(5, 2, 0); other.setPos(point); h.assertTrue(nether.addFreshEntity(other), "actual destination-world receiver exists above natural Nether roof");
                entities.add(other); observed.add(other); anchors.put(other, point);
                var counterpart = target(point); // identical coordinates, genuinely different level identity
                final int[] stale = {0};
                Object observer = new Object() { @SubscribeEvent public void damage(LivingIncomingDamageEvent e) {
                    if (e.getSource().getEntity() == old || e.getSource().getEntity() == moved) stale[0]++;
                }}; hook(observer);
                h.runAfterDelay(90, () -> {
                    h.assertTrue(stale[0] == 0, "old meteor telegraphs cannot resolve against either world after transfer");
                    near(h, other.getHealth(), 200, "destination receiver remains unharmed while idle");
                    near(h, counterpart.getHealth(), 200, "same-coordinate original-world receiver remains unharmed");
                    dimensionDiagnostic("before-native-readiness", moved, other);
                    awaitNativeDimensionTicks(moved, other, moved.tickCount, other.tickCount, 2000, () -> {
                        h.assertTrue(stale[0] == 0, "native destination warmup never replays old meteor telegraphs");
                        moved.setTarget(other); trace.start(moved, Clips.STOMP); boss = moved;
                        dimensionDiagnostic("stomp-start", moved, other);
                        h.runAfterDelay(Clips.get(Clips.STOMP).ticks() + 4, () -> {
                            dimensionDiagnostic("stomp-end", moved, other);
                            trace.valid();
                            h.assertTrue(trace.events.getOrDefault(other, 0) > 0 && other.getHealth() < 200,
                                    "destination boss executes actual damage in its own native world");
                            h.assertTrue(trace.events.getOrDefault(counterpart, 0) == 0 && counterpart.getHealth() == 200,
                                    "identical coordinates in another dimension cannot borrow a hit");
                            moved.discard(); clearTargets(); shields();
                        });
                    });
                });
            });
        }
        void dimensionDiagnostic(String stage, WorldBossContent.Boss moved, LivingEntity target) {
            var destination = (ServerLevel) moved.level();
            Vec3 center = moved.local(5.1, 0, 0); var box = target.getBoundingBox();
            Vec3 nearest = new Vec3(Math.max(box.minX, Math.min(center.x, box.maxX)),
                    Math.max(box.minY, Math.min(center.y, box.maxY)), Math.max(box.minZ, Math.min(center.z, box.maxZ)));
            var obstruction = destination.clip(new net.minecraft.world.level.ClipContext(center, nearest,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, moved));
            System.out.println("ACA_WORLDBOSS_DIMENSION_DIAGNOSTIC stage=" + stage
                    + " bossTicks=" + moved.tickCount + " victimTicks=" + target.tickCount
                    + " pos=" + moved.position() + " clip=" + moved.clip() + " age=" + (destination.getGameTime() - moved.start())
                    + " alive=" + moved.isAlive() + " removed=" + moved.isRemoved() + " spent=" + moved.damageSpent(target.getUUID())
                    + " eligible=" + WorldBossCombat.eligibleTarget(moved, target) + " target=" + moved.getTarget()
                    + " bossEntityTicking=" + destination.isPositionEntityTicking(moved.blockPosition())
                    + " victimEntityTicking=" + destination.isPositionEntityTicking(target.blockPosition())
                    + " bossRegistered=" + (destination.getEntity(moved.getUUID()) == moved)
                    + " victimRegistered=" + (destination.getEntity(target.getUUID()) == target)
                    + " targetPos=" + target.position() + " targetHP=" + target.getHealth()
                    + " targetCollision=" + !destination.noCollision(target, box)
                    + " stompDistance=" + center.distanceTo(nearest) + " stompLOS=" + obstruction.getType()
                    + " events=" + trace.events.getOrDefault(target, 0));
        }
        void awaitNativeDimensionTicks(WorldBossContent.Boss moved, LivingEntity target, int initialBossTicks,
                                       int initialTargetTicks, int remaining, Runnable ready) {
            var destination = (ServerLevel) moved.level();
            if (destination.isPositionEntityTicking(moved.blockPosition())
                    && destination.isPositionEntityTicking(target.blockPosition())
                    && moved.tickCount >= initialBossTicks + 3 && target.tickCount >= initialTargetTicks + 3) {
                dimensionDiagnostic("native-ready", moved, target); ready.run(); return;
            }
            if (remaining <= 0 || moved.isRemoved() || target.isRemoved()) {
                dimensionDiagnostic("native-readiness-failed", moved, target);
                h.assertTrue(false, "native destination chunks must actually entity-tick both actors before a timed attack; no manual tick fallback");
                return;
            }
            // Ticket insertion/full chunk generation precedes asynchronous entity-section
            // activation. Observe genuine ticks rather than consuming the authored strike
            // window while the target dimension is not yet ticking entities.
            h.runAfterDelay(10, () -> awaitNativeDimensionTicks(moved, target, initialBossTicks, initialTargetTicks, remaining - 10, ready));
        }
        void shields() {
            clearTargets(); aim.setGameMode(GameType.SPECTATOR); anchors.put(aim, local(0, 2, 70));
            forceShields = true; EsperLegacyBypass.force(true);
            var owners = new ArrayList<ServerPlayer>();
            for (String category : List.of(HighPowerCategories.ELECTROMASTER, HighPowerCategories.MELTDOWNER,
                    HighPowerCategories.TELEPORTER, HighPowerCategories.VECMANIP)) {
                var p = player("WBShield" + owners.size(), GameType.SURVIVAL, local(0, 2, -20));
                var state = AbilityStorage.get(p); state.changeCategoryClassic(category); state.setLevel(1);
                state.learn(HighPowerCategories.recoveryRoot(category)); state.activated = true; state.overloadFine = true;
                h.assertTrue(HighPowerCombat.enroll(p, 1), "genuine connected category owner enrolls " + category);
                owners.add(p);
            }
            h.runAfterDelay(61, () -> shieldAttack(owners, false));
        }
        void shieldAttack(List<ServerPlayer> owners, boolean breaking) {
            if (boss != null) boss.discard(); boss = boss();
            Map<ServerPlayer, BigDecimal> before = new IdentityHashMap<>();
            for (ServerPlayer p : owners) {
                anchors.put(p, local(0, 2, -20)); p.setPos(anchors.get(p)); p.setDeltaMovement(Vec3.ZERO);
                var tag = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
                tag.putString("shield", breaking ? "1" : "100"); tag.putLong("lastShieldSpend", level.getGameTime());
                before.put(p, health(p)); h.assertTrue(EsperShields.holding(p), "fixture starts with actual category shield");
            }
            boss.setTarget(owners.get(0)); trace.start(boss, Clips.BITE);
            h.runAfterDelay(Clips.get(Clips.BITE).ticks() + 3, () -> {
                trace.valid();
                for (ServerPlayer p : owners) {
                    String category = AbilityStorage.get(p).category;
                    h.assertTrue(trace.events.getOrDefault(p, 0) > 0, "real boss bite reaches native " + category + " player");
                    if (!breaking) {
                        h.assertTrue(health(p).compareTo(before.get(p)) == 0, "category shield absorbs real boss damage before HP " + category);
                        h.assertTrue(HighPowerCombat.shield(p).compareTo(new BigDecimal("100")) < 0 && EsperShields.holding(p), "real ordinary boss damage consumes shield " + category);
                        h.assertTrue(trace.knockbackOpen.getOrDefault(p, 0) == 0,
                                "shielded hurt cannot issue uncanceled native knockback " + category);
                        h.assertTrue(trace.maximumMotionSquared.getOrDefault(p, 0.0) <= 1e-12,
                                "shield holding produces no actual server velocity even when hurt returns false and skips knockback " + category);
                    } else {
                        h.assertTrue(HighPowerCombat.shield(p).signum() == 0 && health(p).compareTo(before.get(p)) < 0,
                                "breaking real hit spends remaining shield then actual HP " + category);
                        h.assertFalse(EsperShields.holding(p), "empty shield no longer holds super armor " + category);
                        h.assertTrue(trace.knockbackOpen.getOrDefault(p, 0) > 0, "native knockback resumes after shield empty " + category);
                    }
                }
                if (!breaking) shieldAttack(owners, true); else environment();
            });
        }
        void environment() {
            if (boss != null) boss.discard(); trace.caster = null;
            h.assertTrue(WorldBossEnvironment.activeBossCount(level) == 0, "all completed combat fixtures release their environment ownership");
            Vec3 ground = h.absoluteVec(new Vec3(16, 24, 16));
            var prepared = new LinkedHashMap<BlockPos, BlockState>();
            // Declared isolated-world fixture: a stone platform plus an item-bearing
            // chest makes excavation and exact block-entity restoration observable.
            for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
                BlockPos pos = BlockPos.containing(ground.add(x, -1, z));
                fixtureBlocks.put(pos, level.getBlockState(pos));
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3); prepared.put(pos, level.getBlockState(pos));
            }
            BlockPos chestPos = BlockPos.containing(ground.add(0, -1, 0));
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
            h.assertTrue(level.getBlockEntity(chestPos) instanceof ChestBlockEntity, "actual inventory-bearing chest fixture exists");
            var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
            chest.setItem(0, new ItemStack(Items.DIAMOND, 3)); chest.setChanged();
            BlockState chestState = level.getBlockState(chestPos);
            CompoundTag chestTag = chest.saveWithFullMetadata(level.registryAccess()); prepared.put(chestPos, chestState);
            var containerZone = new AABB(chestPos).inflate(8);
            final List<ItemStack> recoveryJoins = new ArrayList<>();
            hook(new Object() { @SubscribeEvent public void join(EntityJoinLevelEvent event) {
                if (event.getLevel() == level && event.getEntity() instanceof ItemEntity item && containerZone.contains(item.position()))
                    recoveryJoins.add(item.getItem().copy());
            }});
            long beforeTime = level.getGameTime(), beforeDay = level.getDayTime();
            boolean daylight = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
            boolean raining = level.getLevelData().isRaining(), thundering = level.getLevelData().isThundering();
            var first = boss(ground); WorldBossEnvironment.begin(first); first.play(Clips.SPAWN);
            h.runAfterDelay(4, () -> {
                h.assertTrue(level.getBlockState(chestPos).isAir() && level.getBlockEntity(chestPos) == null,
                        "native emergence actually excavates original terrain and removes its live block entity");
                h.assertTrue(WorldBossEnvironment.changedBlockCount(first) > 0, "first boss owns actual changed native blocks");
                h.assertTrue(level.getLevelData().isRaining() && level.getLevelData().isThundering() && Math.floorMod(level.getDayTime(), 24000) >= 18000,
                        "authored spawn storm actually changes native weather and time to a thunderous night");
                var second = boss(ground.add(8, 0, 0)); WorldBossEnvironment.begin(second); second.play(Clips.SPAWN);
                h.runAfterDelay(4, () -> {
                    h.assertTrue(WorldBossEnvironment.activeBossCount(level) == 2 && WorldBossEnvironment.weatherOwnerCount(level) == 2,
                            "two native bosses own independent overlapping environment and weather leases");
                    first.discard();
                    h.assertTrue(WorldBossEnvironment.activeBossCount(level) == 1 && WorldBossEnvironment.weatherOwnerCount(level) == 1,
                            "removing one boss releases only its own environment ownership");
                    h.assertTrue(level.getBlockState(chestPos).isAir() && level.getLevelData().isRaining() && level.getLevelData().isThundering(),
                            "remaining overlapping owner preserves the actual pit and thunderstorm");
                    var outside = new LinkedHashMap<BlockPos, BlockState>();
                    for (int x = -32; x <= 32; x += 2) for (int z = -32; z <= 32; z += 2) {
                        double px = second.getX() + x, pz = second.getZ() + z;
                        if (Math.hypot(px - ground.x, pz - ground.z) <= 17 || Math.hypot(x, z) <= 17) continue;
                        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(px), (int) Math.floor(pz));
                        for (int y = surface - 1; y <= surface + 8; y++) {
                            BlockPos pos = new BlockPos((int) Math.floor(px), y, (int) Math.floor(pz)); outside.put(pos, level.getBlockState(pos));
                        }
                    }
                    int beforeChanged = WorldBossEnvironment.changedBlockCount(second);
                    second.play(Clips.WORLDBREAKER);
                    h.runAfterDelay(Clips.get(Clips.WORLDBREAKER).ticks() + 15, () -> {
                        int actualChanges = 0;
                        for (var entry : outside.entrySet()) if (!level.getBlockState(entry.getKey()).equals(entry.getValue())) actualChanges++;
                        h.assertTrue(actualChanges > 0 && WorldBossEnvironment.changedBlockCount(second) > beforeChanged,
                                "real worldbreaker events scorch/raise actual native terrain beyond both emergence pits");
                        h.assertTrue(level.isRaining() && level.isThundering(), "real native rain/thunder levels complete their normal gradual storm transition");
                        final int changedOutsidePit = actualChanges;
                        // A player places and fills a NEW chest after the original
                        // diamond chest was temporarily excavated by the boss.
                        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
                        h.assertTrue(level.getBlockEntity(chestPos) instanceof ChestBlockEntity, "new player container occupies the temporary pit");
                        var lateChest = (ChestBlockEntity) level.getBlockEntity(chestPos);
                        ItemStack namedChest=new ItemStack(Items.CHEST);
                        namedChest.set(DataComponents.CUSTOM_NAME,Component.literal("World Boss recovery chest"));
                        lateChest.applyComponentsFromItemStack(namedChest);
                        lateChest.setItem(0, new ItemStack(Items.EMERALD, 5));
                        lateChest.getPersistentData().putString("academy:world_boss_recovery", "five-emerald"); lateChest.setChanged();
                        ItemStack expectedRecovery = new ItemStack(Items.CHEST); lateChest.saveToItem(expectedRecovery, level.registryAccess());
                        h.assertTrue(recoveryJoins.isEmpty(), "first excavation preserved the original diamond chest without duplicate item drops");
                        second.discard();
                        h.runAfterDelay(2, () -> {
                            h.assertTrue(WorldBossEnvironment.activeBossCount(level) == 0 && WorldBossEnvironment.weatherOwnerCount(level) == 0,
                                    "last native removal releases every environment and weather lease");
                            for (var entry : prepared.entrySet()) h.assertTrue(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                                    "last owner restores actual pre-boss platform block " + entry.getKey());
                            h.assertTrue(level.getBlockEntity(chestPos) instanceof ChestBlockEntity
                                            && level.getBlockEntity(chestPos).saveWithFullMetadata(level.registryAccess()).equals(chestTag),
                                    "last owner restores the exact original chest block entity and all inventory NBT");
                            h.assertTrue(recoveryJoins.size() == 1 && recoveryJoins.get(0).getCount() == 1
                                            && ItemStack.isSameItemSameComponents(recoveryJoins.get(0), expectedRecovery),
                                    "restoration emits exactly one whole new chest with all native NBT/components, without loose-content duplicates");
                            var actualRecovery = level.getEntitiesOfClass(ItemEntity.class, containerZone,
                                    item -> !item.isRemoved() && ItemStack.isSameItemSameComponents(item.getItem(), expectedRecovery));
                            h.assertTrue(actualRecovery.size() == 1 && actualRecovery.get(0).getItem().getCount() == 1,
                                    "the real recovery item entity survives restoration and is available for pickup");
                            var recovered = actualRecovery.get(0);
                            var contents = recovered.getItem().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
                            h.assertTrue(contents.getSlots() == 1 && contents.getStackInSlot(0).is(Items.EMERALD)
                                            && contents.getStackInSlot(0).getCount() == 5,
                                    "the new five emeralds are preserved once in native CONTAINER data");
                            h.assertTrue(recovered.isInvulnerable() && !recovered.hurt(level.damageSources().inFire(), 1000),
                                    "authored boss fire cannot destroy this recovery entity or its preserved contents");
                            h.assertTrue(ItemStack.isSameItemSameComponents(recovered.getItem(), expectedRecovery),
                                    "fire protection changes the entity only, never the preserved item/components");
                            entities.add(recovered);
                            for (var entry : outside.entrySet()) h.assertTrue(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                                    "scorch/spike region restores its actual pre-cast block " + entry.getKey());
                            long expectedDay = beforeDay + (daylight ? level.getGameTime() - beforeTime : 0);
                            h.assertTrue(level.getDayTime() == expectedDay && level.getLevelData().isRaining() == raining && level.getLevelData().isThundering() == thundering,
                                    "last weather owner restores actual original weather/time plus natural elapsed time");
                            h.assertTrue(level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT) == daylight,
                                    "weather staging never changes daylight-cycle configuration");
                            System.out.println("ACA_WORLDBOSS_NATIVE_ENVIRONMENT_PASS pit=true stormNight=true terrainOutsidePit=" + changedOutsidePit
                                    + " owners=2 overlap=true blockEntityNBT=true laterContainer=5Emerald wholeItemExactlyOnce=true fireProtected=true restored=true");
                            finish();
                        });
                    });
                });
            });
        }
        void finish() {
            h.assertTrue(verifiedAttacks == 16, "all fifteen attack clips and roar executed their real native hits and vetoes");
            System.out.println("ACA_WORLDBOSS_NATIVE_COMBAT_PASS clips=16 hp=60 attack=10 nativeVeto=true shields=4");
            h.succeed();
        }
        void close() {
            if (closed) return; closed = true;
            for (Object hook : hooks) NeoForge.EVENT_BUS.unregister(hook);
            if (forceShields) { EsperLegacyBypass.force(false); forceShields = false; }
            for (ServerPlayer p : players) { AbilityStorage.remove(p); level.getServer().getPlayerList().remove(p); }
            entities.forEach(Entity::discard); channels.forEach(EmbeddedChannel::finishAndReleaseAll);
            for (var entry : fixtureBlocks.entrySet()) { level.removeBlockEntity(entry.getKey()); level.setBlock(entry.getKey(), entry.getValue(), 3); }
            for (PlayerTeam team : teams) level.getScoreboard().removePlayerTeam(team);
            for (var entry : forcedChunks.entrySet()) for (ChunkPos chunk : entry.getValue()) entry.getKey().setChunkForced(chunk.x, chunk.z, false);
        }
    }
}

package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.develop.*;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CirculationListeningRules;
import cn.academy.port.highpower.core.VectorParryRules;
import cn.academy.port.machine.*;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW433 native runtime coverage. Enrollment, poses, finite batteries and accelerated developer
 * ticks are declared fixtures. Positive disturbances use exact native Arrow and Zombie classes,
 * real server ticks and collision-resolved Entity.move; fixture setPos never supplies motion proof. */
@GameTestHolder("academy_circulation_listening_runtime")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerCirculationListeningRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_circulation_listening";
    private static final String ID = "circulation_listening";
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.circulation_listening_native_test");
    private static int startedFixtures, closedFixtures;
    private HighPowerCirculationListeningRuntimeTests() {}

    @BeforeBatch(batch=BATCH) public static void beginNativeBatch(ServerLevel level) {
        startedFixtures = closedFixtures = 0;
        LOG.info("NEW433 native batch lifecycle started");
    }
    @AfterBatch(batch=BATCH) public static void finishNativeBatch(ServerLevel level) {
        // The runner's listener may precede the last fixture listener in the same dispatch.
        // Individual close logs then establish that the final actor/channel cleanup ran too.
        LOG.info("NEW433 native batch completion callback reached: created={} closed={}", startedFixtures, closedFixtures);
    }

    @SubscribeEvent public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int dimension : new int[]{64, 16, 64}) size.add(IntTag.valueOf(dimension));
        for (int axis = 0; axis < 3; axis++) position.add(IntTag.valueOf(0));
        cell.put("pos", position); cell.putInt("state", 0); blocks.add(cell);
        air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_circulation_listening_runtime", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=105)
    public static void lv3_exact_parent_and_finite_normal_learning_keep_native_ledgers_and_codec(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(false);
        f.ready(() -> {
            var state = AbilityStorage.get(p); var skill = SkillCatalog.find(HighPowerCategories.VECMANIP, ID).orElseThrow();
            h.assertTrue(skill.level() == 3 && skill.requirements().equals(List.of(new SkillCatalog.Requirement(CirculationRelief.ID, 0))),
                    "canonical NEW433 is LV3 and requires precisely learned NEW432 at zero mastery");
            h.assertTrue(SkillCatalog.ALL.size() == 50 && cn.academy.port.preset.PresetSkills.IMPLEMENTED.size() == 35,
                    "listening leaves all classic catalog and preset counts unchanged");
            state.unlearn(CirculationRelief.ID); int stimulations = ClassicRules.learningStimulations(3);
            double cost = DeveloperType.NORMAL.actualConsumption(stimulations); int ticks = DeveloperType.NORMAL.ticksPerStimulation() * stimulations;
            near(h, cost, 5145, "finite Normal learning uses approved native IF");
            var machine = f.normal(p, cost); h.assertFalse(f.learnRequest(p, ID), "other branches cannot replace the exact NEW432 parent");
            near(h, machine.battery().getEnergy(), cost, "missing parent is free");
            f.learn(p, CirculationRelief.ID); h.assertTrue(state.exp(CirculationRelief.ID) == 0, "genuinely paid parent has zero mastery");
            state.setLevel(2); machine = f.normal(p, cost); h.assertFalse(f.learnRequest(p, ID), "paid parent cannot bypass integer LV3");
            state.setLevel(3); var portable = f.portable(p, cost);
            h.assertFalse(DevelopmentController.startSkill(p, ID), "Portable cannot learn LV3 listening"); near(h, portable.energy(), cost, "wrong tier consumes no IF");
            h.assertFalse(state.presets.edit(0, 0, ID, state::learned), "unlearned listening cannot bind");
            f.deplete(p); Ledger before = new Ledger(p); CompoundTag resources = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy();
            machine = f.normal(p, cost);
            String oversized = MachineDeveloperSessions.activeToken(p).orElseThrow() + ":" + ID + ":~" + Integer.toString(Integer.MAX_VALUE, 36);
            h.assertTrue(oversized.length() > 64, "canonical full skill ID plus maximum native sequence exceeds the unchanged request bound");
            RegistryFriendlyByteBuf rejected = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                boolean refused = false;
                try { AcademyNetwork.Request.CODEC.encode(rejected, new AcademyNetwork.Request("machine_hp_learn", oversized)); }
                catch (io.netty.handler.codec.EncoderException expected) { refused = true; }
                h.assertTrue(refused, "native request codec still rejects the uncompressed oversized machine request");
            } finally { rejected.release(); }
            f.learnSequence = Integer.MAX_VALUE - 1;
            h.assertTrue(f.learnRequest(p, ID), "compact bounded machine wire accepts maximum sequence and starts genuine finite Normal learning");
            f.develop(p, ticks - 1); h.assertFalse(state.learned(ID), "final paid development tick remains mandatory");
            near(h, machine.battery().getEnergy(), DeveloperType.NORMAL.energyPerTick(), "one physical IF payment remains");
            f.develop(p, 1); h.assertTrue(state.learned(ID) && state.exp(ID) == 0 && DevelopmentController.process(p).state() == DevelopmentProcess.State.DONE,
                    "only completed native finite development grants listening");
            near(h, machine.battery().getEnergy(), 0, "every development tick pays once"); before.unchanged(h, p);
            h.assertTrue(resources.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)), "learning cannot refill CP or alter cooldowns");
            h.assertTrue(f.request(p, "preset_edit", "0:0:" + ID), "ordinary input slot accepts genuinely learned listening");
            CompoundTag saved = AbilityStorage.encodeSaved(state);
            h.assertTrue(saved.equals(AbilityStorage.encodeSaved(AbilityStorage.decodeSaved(saved, state.cpDataConfig()))), "native ability codec retains exact learned mappings");
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=105)
    public static void quiet_empty_world_has_no_cues_and_zero_cp_cannot_activate(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            f.deplete(p); f.cp(p, 0); Ledger before = new Ledger(p);
            h.assertFalse(f.start(p), "zero CP cannot buy the sensor"); before.unchanged(h, p);
            h.assertFalse(CirculationListening.active(p), "free refusal creates no active sensor");
            f.cp(p, 100); double cp = HighPowerResources.cp(p);
            h.assertTrue(f.start(p), "sufficient finite CP starts the sensor through authentic input");
            near(h, HighPowerResources.cp(p), cp - CirculationListeningRules.activationCost(HighPowerGrowth.values(p).position()), "activation charges exactly once");
            f.after(p, 8, () -> { f.quiet(p, "empty world cannot disclose a cue"); before.unchanged(h, p); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=105)
    public static void exact_native_arrow_flight_emits_coarse_owner_only_cue_without_mutating_projectile(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(), other = f.player(56, 56, true);
        f.ready(() -> {
            f.deplete(p); Ledger before = new Ledger(p); h.assertTrue(f.start(p), "authenticated listening starts");
            Arrow arrow = f.arrow(p.position().add(-7, 4, 7), new Vec3(.7, 0, 0));
            Arrow control = f.arrow(p.position().add(-7, 4, 29), new Vec3(.7, 0, 0));
            f.after(p, 1, () -> f.quiet(p, "first observed spawn has no previous genuine motion sample"));
            f.after(p, 8, () -> {
                var cues = f.cues(p); h.assertTrue(cues.stream().anyMatch(c -> c.kind() == CirculationListening.KIND_ARROW), "actual native ordinary arrow flight produces a bounded coarse cue");
                vector(h, arrow.getDeltaMovement(), control.getDeltaMovement(), "sensor preserves native arrow drag and gravity");
                near(h, arrow.getX(), control.getX(), "sensor adds no arrow displacement"); near(h, arrow.getY(), control.getY(), "sensor adds no lift");
                h.assertTrue(arrow.getClass() == Arrow.class && !arrow.isRemoved() && !control.isRemoved(), "positive baseline uses actual live vanilla arrows");
                h.assertTrue(f.visuals(p).stream().anyMatch(v -> !v.cues().isEmpty()), "actual owner transport carries native codec-checked disturbance cues");
                h.assertTrue(f.visuals(other).isEmpty(), "other client receives no owner sensor payload");
                before.unchanged(h, p); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=105)
    public static void actual_collision_resolved_native_sprinter_is_positive_and_static_sprint_flag_is_quiet(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            h.assertTrue(f.start(p), "authenticated listening starts");
            Zombie stationary = f.runner(p.position().add(-6, 0, 6), false);
            stationary.setDeltaMovement(.25, 0, 0); f.freeze(stationary);
            f.after(p, 6, () -> {
                f.quiet(p, "sprint flag and fabricated velocity without native displacement cannot emit");
                f.run(stationary, new Vec3(.25, 0, 0));
            });
            f.after(p, 12, () -> {
                h.assertTrue(f.cues(p).stream().anyMatch(c -> c.kind() == CirculationListening.KIND_RUNNER), "same exact native entity needs two real collision-resolved movement samples");
                h.assertTrue(stationary.getClass() == Zombie.class && stationary.isSprinting() && !stationary.isNoGravity(), "positive uses declared native sprint class and ordinary gravity"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void tiny_teleport_and_move_away_return_revoke_pairs_then_clean_native_motion_recovers(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            h.assertTrue(f.start(p), "listening starts"); Zombie runner = f.runner(p.position().add(-7, 0, 7), true);
            f.after(p, 6, () -> {
                h.assertFalse(f.cues(p).isEmpty(), "native positive control establishes prior sample");
                f.tamper(runner, () -> { Vec3 point = runner.position(); runner.setPos(point.add(.001, 0, 0)); runner.setPos(point); });
            });
            f.after(p, 10, () -> { f.quiet(p, "even tiny out-and-back teleport invalidates the entire native sample pair"); f.tamper(runner, null); });
            f.after(p, 16, () -> {
                h.assertFalse(f.cues(p).isEmpty(), "two later clean native samples can recover without reusing teleported history");
                int samples = CirculationListening.sampledCount(p), references = CirculationListeningMotionProof.watchReferenceCount();
                h.assertTrue(samples > 0 && CirculationListeningMotionProof.watchedEntityCount() <= 1024, "native observer retains only bounded watched samples");
                h.assertTrue(f.request(p, "circ_listen_abort", f.held.get(p)), "native cancel releases source watches");
                h.assertTrue(CirculationListening.sampledCount(p) == 0 && CirculationListeningMotionProof.watchReferenceCount() == references - samples, "retiring context releases exactly its own observer references"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void removed_replacement_identity_never_inherits_previous_motion_sample(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            h.assertTrue(f.start(p), "listening starts"); Zombie original = f.runner(p.position().add(-6, 0, 7), true);
            f.after(p, 6, () -> {
                h.assertFalse(f.cues(p).isEmpty(), "original identity establishes real movement history");
                Vec3 at = original.position(); UUID old = original.getUUID(); f.stop(original); original.discard();
                Zombie replacement = f.runner(at, false); replacement.setDeltaMovement(.25, 0, 0); f.freeze(replacement);
                h.assertFalse(old.equals(replacement.getUUID()), "replacement owns a distinct native identity");
            });
            f.after(p, 10, () -> { f.quiet(p, "new identity at old coordinates cannot inherit history or velocity"); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void invisibility_water_solid_los_and_unreviewed_subclasses_cannot_emit(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            h.assertTrue(f.start(p), "listening starts");
            Zombie invisible = f.runner(p.position().add(-8, 0, 7), true);
            invisible.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, 100));
            Zombie water = f.runner(p.position().add(-8, 0, -7), true);
            for (int x = -10; x <= 0; x++) for (int y = 0; y <= 2; y++) for (int z = -8; z <= -6; z++) h.getLevel().setBlockAndUpdate(p.blockPosition().offset(x, y, z), Blocks.WATER.defaultBlockState());
            Zombie wall = f.runner(p.position().add(7, 0, 5), true);
            for (int z = -12; z <= 12; z++) for (int y = 0; y <= 4; y++) h.getLevel().setBlockAndUpdate(p.blockPosition().offset(4, y, z), Blocks.STONE.defaultBlockState());
            Zombie custom = new Zombie(EntityType.ZOMBIE, h.getLevel()) {};
            f.prepare(custom, p.position().add(-8, 0, 0)); f.run(custom, new Vec3(.25, 0, 0));
            f.after(p, 8, () -> {
                LOG.info("NEW433 exclusions invisible={} water={} invisiblePos={} waterPos={} wallPos={} cues={}", invisible.isInvisible(), water.isInWater(), invisible.position(), water.position(), wall.position(), f.cues(p));
                h.assertTrue(invisible.isInvisible() && water.isInWater(), "actual native exclusion states exist");
                f.quiet(p, "invisible, immersed, occluded and unreviewed runtime classes have no cue authority"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void sixty_five_total_candidates_fail_closed_including_ineligible_entities(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            h.assertTrue(f.start(p), "listening starts"); f.runner(p.position().add(-7, 0, 7), true);
            for (int i = 0; i < 64; i++) {
                ArmorStand stand = new ArmorStand(h.getLevel(), p.getX() + 4 + i % 8 * .2, p.getY(), p.getZ() - 4 - i / 8 * .2);
                stand.setNoGravity(true); f.entity(stand);
            }
            f.after(p, 8, () -> {
                f.quiet(p, "the sixty-fifth total candidate suppresses the complete batch, even when sixty-four are ineligible");
                h.assertTrue(CirculationListening.sampledCount(p) <= 64, "native query never retains an unbounded candidate prefix"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void more_than_eight_real_disturbances_are_bounded_and_stable(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            h.assertTrue(f.start(p), "listening starts");
            for (int i = 0; i < 12; i++) f.runner(p.position().add(-4 + i * .65, 0, 10), true);
            f.after(p, 6, () -> {
                var first = f.cues(p); h.assertTrue(first.size() == 8, "twelve genuine native movers produce at most eight selected cues");
                for (int i = 0; i < 30; i++) CirculationListening.tick(p);
                h.assertTrue(first.equals(f.cues(p)), "same-tick maintenance cannot reshuffle or duplicate authoritative cues");
                h.assertTrue(first.stream().allMatch(c -> c.azimuth() >= 0 && c.azimuth() < 16 && c.elevation() >= -1 && c.elevation() <= 1 && c.strength() >= 1 && c.strength() <= 3),
                        "output consists only of approved coarse scalars"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void exactly_sixty_four_nonowner_candidates_preserve_genuine_positive(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            h.assertTrue(f.start(p), "listening starts"); f.runner(p.position().add(-7, 0, 7), true);
            for (int i = 0; i < 63; i++) {
                ArmorStand stand = new ArmorStand(h.getLevel(), p.getX() + 4 + i % 8 * .2, p.getY(), p.getZ() - 4 - i / 8 * .2);
                stand.setNoGravity(true); f.entity(stand);
            }
            f.after(p, 8, () -> { h.assertFalse(f.cues(p).isEmpty(), "exactly sixty-four nonowner results retain the genuine source; caster is excluded"); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void exact_native_reach_boundary_is_closed_and_next_scan_outside_is_quiet(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            h.assertTrue(f.start(p), "listening starts"); Zombie runner = f.runner(p.position().add(CirculationListeningRules.RANGE-1.75, 0, 0), true);
            f.after(p, 7, () -> {
                near(h, runner.getX() - p.getX(), CirculationListeningRules.RANGE, "native collision movement reaches exact represented reach boundary");
                near(h, runner.getY(), p.getY(), "both feet are at equal native floor height");
                h.assertTrue(f.cues(p).stream().anyMatch(c -> c.azimuth() == 4 && c.kind() == CirculationListening.KIND_RUNNER), "closed reach boundary has a real eastward coarse cue");
            });
            f.after(p, 9, () -> { h.assertTrue(runner.distanceToSqr(p) > CirculationListeningRules.RANGE * CirculationListeningRules.RANGE, "same native source is now truly outside range"); f.quiet(p, "next outside-range scan has no retained cue"); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=105)
    public static void unloaded_query_refuses_without_forcing_any_native_neighbor_chunk(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            h.assertTrue(f.start(p), "listening starts in loaded fixture");
            int chunkX = 1_150_000, chunkZ = 1_150_000;
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunkX, chunkZ) == null, "remote query fixture starts genuinely unloaded");
            Vec3 remote = new Vec3(chunkX * 16.0 + 8, p.getY(), chunkZ * 16.0 + 8);
            try {
                var loaded = CirculationListening.class.getDeclaredMethod("loadedQuery", ServerLevel.class, net.minecraft.world.phys.AABB.class);
                loaded.setAccessible(true);
                h.assertFalse((boolean)loaded.invoke(null, h.getLevel(), new net.minecraft.world.phys.AABB(remote, remote).inflate(20)), "native query's unloaded admission gate fails closed");
                for (int x = chunkX - 2; x <= chunkX + 2; x++) for (int z = chunkZ - 2; z <= chunkZ + 2; z++)
                    h.assertTrue(h.getLevel().getChunkSource().getChunkNow(x, z) == null, "query does not force an unloaded source or neighboring chunk");
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            f.quiet(p, "unloaded query cannot fabricate source history"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=105)
    public static void genuine_input_release_cancel_replay_and_late_old_cancel_preserve_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            h.assertTrue(f.start(p), "authenticated input starts"); String original = f.held.get(p); double cp = HighPowerResources.cp(p);
            h.assertTrue(f.request(p, "circ_listen_release", original) && CirculationListening.active(p), "release leaves ordinary toggle active");
            h.assertFalse(f.request(p, "circ_listen_start", original), "replayed press cannot retoggle or repay"); near(h, HighPowerResources.cp(p), cp, "replay is free");
            h.assertTrue(f.request(p, "circ_listen_abort", original) && !CirculationListening.active(p), "authenticated matching cancel ends sensor");
            h.assertFalse(f.start(p), "same-tick restart cannot bypass remaining physical cooldown");
            f.after(p, VectorParryRules.cooldownTicks(HighPowerGrowth.values(p).position()), () -> {
                h.assertTrue(f.start(p), "exact physical restart boundary permits a new paid context"); double paid = HighPowerResources.cp(p);
                h.assertFalse(f.request(p, "circ_listen_abort", original), "late old cancel cannot end newer context");
                h.assertTrue(CirculationListening.active(p), "new identity survives old transport"); near(h, HighPowerResources.cp(p), paid, "late cancel cannot refund payment"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void save_reload_keeps_remaining_native_payment_without_offline_clock_credit(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            f.deplete(p); h.assertTrue(f.start(p), "paid native context starts"); f.after(p, 3, () -> {
                h.assertTrue(f.request(p, "circ_listen_abort", f.held.get(p)), "native input ends prior paid context");
                CompoundTag debt = p.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY).copy(); Ledger ledger = new Ledger(p);
                h.assertTrue(debt.getInt("remaining") > 0 && debt.getInt("restart") > 0, "actual remaining payment and restart debt exist");
                long unrelated = p.serverLevel().getGameTime() + 8000;
                // Declared saved-clock-origin fixture: remaining debt stays authoritative
                // even when this skill's matching historical writer lies far in the future.
                debt.putLong("resourceUntil", unrelated + 1000);
                p.getPersistentData().put(CirculationListening.PAYMENT_KEY, debt.copy());
                p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putLong("next_" + ID, unrelated + 1000);
                p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putLong("next_vector_parry", unrelated);
                for (int i = 0; i < 3; i++) {
                    f.reload(p); CirculationListening.tick(p);
                    h.assertTrue(debt.equals(p.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY)), "same-tick native save/load grants no apparent offline credit");
                    h.assertFalse(CirculationListening.active(p) || f.start(p), "reload cannot silently activate or clear restart debt"); ledger.unchanged(h, p);
                }
                h.assertTrue(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_vector_parry") == unrelated, "reload never rewrites another skill's cooldown");
                int restart = debt.getInt("restart");
                f.after(p, restart - 1, () -> h.assertFalse(f.start(p), "one remaining completed native tick still refuses"));
                f.after(p, restart, () -> { h.assertTrue(f.start(p), "saved restart debt expires only after exact native owner Posts"); ledger.unchanged(h, p); f.succeed(); });
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void category_death_spectator_and_native_dimension_callbacks_end_without_refund(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> players = List.of(f.player(12, 12, true), f.player(48, 12, true), f.player(12, 48, true), f.player(48, 48, true));
        f.ready(() -> {
            for (ServerPlayer p : players) h.assertTrue(f.start(p), "independent native owner starts listening");
            List<Ledger> ledgers = players.stream().map(Ledger::new).toList();
            AbilityStorage.get(players.get(0)).changeCategoryClassic(HighPowerCategories.ELECTROMASTER);
            ServerPlayer doomed = players.get(1);
            doomed.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health", "1"); doomed.invulnerableTime = 0;
            h.assertTrue(doomed.hurt(doomed.damageSources().magic(), 5) && !doomed.isAlive(), "actual lethal native hurt and death dispatch retire the sensor");
            players.get(2).setGameMode(GameType.SPECTATOR);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(players.get(3), h.getLevel().dimension(), net.minecraft.world.level.Level.NETHER));
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer p = players.get(i); double paid = HighPowerResources.cp(p); CirculationListening.tick(p);
                h.assertFalse(CirculationListening.active(p), "native lifecycle boundary ends old context " + i);
                near(h, HighPowerResources.cp(p), paid, "teardown never refunds CP");
                eq(h, HighPowerCombat.shield(p), ledgers.get(i).shield, "teardown never refills base S");
                h.assertTrue(f.cues(p).isEmpty(), "ended context emits no retained disturbance cues");
            }
            f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void listening_and_relief_pay_independently_without_expanding_native_defense(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(), control = f.player(56, 56, true);
        f.ready(() -> {
            f.deplete(p); f.deplete(control); double cp = HighPowerResources.cp(p), cost = VectorParryRules.activationCost(HighPowerGrowth.values(p).position());
            h.assertTrue(f.start(p), "NEW433 starts");
            h.assertTrue(f.request(p, "circulation_relief", f.wire(p, 1)), "NEW432 may coexist with NEW433");
            h.assertTrue(f.request(control, "circulation_relief", f.wire(control, 1)), "independent NEW432 control starts");
            h.assertTrue(CirculationListening.active(p) && CirculationRelief.active(p), "both independently paid controllers stay active");
            near(h, HighPowerResources.cp(p), cp - 2 * cost, "both activation payments are independently debited");
            Ledger before = new Ledger(p); BigDecimal initial = HighPowerCombat.shield(p);
            for (ServerPlayer target : List.of(control, p)) {
                Zombie side = f.runner(target.position().add(2, 0, 0), false); target.invulnerableTime = 0;
                h.assertTrue(target.hurt(target.damageSources().mobAttack(side), 5), "native side damage reaches each identical target");
                target.invulnerableTime = 0; Zombie front = f.runner(target.position().add(0, 0, 2), false);
                h.assertTrue(target.hurt(target.damageSources().mobAttack(front), 5), "native front damage remains unprotected");
            }
            eq(h, HighPowerCombat.shield(p), initial.subtract(new BigDecimal("5")), "only ordinary NEW432 side absorption consumes S");
            eq(h, HighPowerCombat.shield(p), HighPowerCombat.shield(control), "listening grants no stronger shield");
            eq(h, new BigDecimal(HighPowerCombat.healthText(p)), new BigDecimal(HighPowerCombat.healthText(control)), "listening grants no front protection or health advantage");
            h.assertTrue(CirculationRelief.remainingHits(p) == CirculationRelief.remainingHits(control), "listening does not expand defensive quota");
            eq(h, HighPowerGrowth.values(p).position(), before.q, "simultaneous sensor cannot grow q"); eq(h, HighPowerCombat.earnedGrowthPosition(p), before.earned, "simultaneous sensor cannot certify Q");
            h.assertTrue(AbilityStorage.get(p).exp(ID) == before.mastery && AbilityStorage.get(p).levelExperience == before.levelExperience, "sensing and coexistence award no XP"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void independent_twenty_native_tick_renewals_cannot_be_accelerated_by_maintenance(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            f.deplete(p); h.assertTrue(f.start(p) && f.request(p, "circulation_relief", f.wire(p, 1)), "both independently paid controllers start");
            RenewalPayments payments = new RenewalPayments(p); f.hook(payments);
            long listening = p.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY).getLong("receipt");
            long relief = p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY).getLong("receipt");
            CompoundTag first = p.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY).copy();
            for (int i = 0; i < 40; i++) { CirculationListening.tick(p); CirculationListening.playerTick(new PlayerTickEvent.Post(p)); }
            LOG.info("NEW433 initial renewal first={} after={} relief={}", first, p.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY), p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY));
            h.assertTrue(first.equals(p.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY)), "same-tick duplicate callbacks cannot age physical paid debt");
            f.after(p, 19, () -> {
                LOG.info("NEW433 renewal post19 listening={} relief={} cp={}", p.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY), p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY), HighPowerResources.cp(p));
                h.assertTrue(p.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY).getInt("remaining") == 1
                        && p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY).getInt("remaining") == 1, "both still owe their twentieth completed native tick");
            });
            f.after(p, 20, () -> {
                LOG.info("NEW433 renewal post20 listening={} relief={} cp={}", p.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY), p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY), HighPowerResources.cp(p));
                h.assertTrue(p.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY).getLong("receipt") == listening
                        && p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY).getLong("receipt") == relief, "twentieth native Post exhausts payment without double renewing");
                double before = HighPowerResources.cp(p), cost = VectorParryRules.sustainCost(HighPowerGrowth.values(p).position());
                CirculationListening.tick(p); CirculationRelief.tick(p);
                near(h, HighPowerResources.cp(p), before, "same physical tick maintenance cannot race renewal");
                f.beforeNextRenewal = before; f.renewalCost = cost;
            });
            f.after(p, 21, () -> {
                LOG.info("NEW433 renewal post21 listening={} relief={} cp={} before={} cost={}", p.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY), p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY), HighPowerResources.cp(p), f.beforeNextRenewal, f.renewalCost);
                h.assertTrue(p.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY).getLong("receipt") == listening + 1
                        && p.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY).getLong("receipt") == relief + 1, "next native tick buys each independent twenty-tick receipt once");
                h.assertTrue(payments.renewals == 1 && payments.exact, "same-phase native observations prove two separate approved CP debits before ordinary recovery");
                near(h, payments.charged, 2 * f.renewalCost, "both simultaneous native renewal transactions spend their exact approved amount"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void accepted_native_player_sprint_packets_supply_real_displacement_with_zero_delta(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(), runner = f.player(40, 28, true);
        f.ready(() -> {
            runner.setNoGravity(false); runner.connection.handlePlayerCommand(new ServerboundPlayerCommandPacket(runner, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
            h.assertTrue(runner.isSprinting(), "native command packet turns sprint on");
            h.assertTrue(f.start(p), "listener starts through authenticated packet");
            f.packetVelocities.put(runner, new Vec3(.25, 0, 0));
            f.after(p, 8, () -> {
                h.assertTrue(runner.getDeltaMovement().horizontalDistanceSqr() == 0, "ordinary accepted position packets retain native zero horizontal deltaMovement");
                h.assertTrue(f.cues(p).stream().anyMatch(c -> c.kind() == CirculationListening.KIND_RUNNER), "two accepted native sprint movements create a cue using witnessed actual displacement");
                f.packetVelocities.clear(); runner.setGameMode(GameType.SPECTATOR);
            });
            f.after(p, 12, () -> { f.quiet(p, "spectator source cannot keep its earlier native movement cue"); f.succeed(); });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void native_restore_from_clone_copies_only_certified_own_clock_and_can_reactivate(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            f.deplete(p); h.assertTrue(f.start(p), "paid native owner starts"); Ledger ledger = new Ledger(p);
            long own = p.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY).getLong("resourceUntil"), unrelated = own + 5000;
            ServerPlayer replacement = f.cloneActor(p, unrelated);
            CompoundTag copied = replacement.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY);
            h.assertTrue(p.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY).equals(copied), "actual restoreFrom clone copies remaining physical debt exactly");
            h.assertTrue(replacement.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_" + ID) == own, "clone carries the exact certified own payment writer clock");
            near(h, HighPowerResources.cp(replacement), 37, "clone does not replace the new body's existing finite CP balance");
            h.assertTrue(replacement.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_vector_parry") == unrelated, "clone leaves unrelated new-body resource clocks untouched");
            h.assertFalse(CirculationListening.active(p) || CirculationListening.active(replacement), "neither old nor replacement body inherits an active sensor");
            ledger.unchanged(h, replacement); int restart = copied.getInt("restart");
            h.assertTrue(restart > 0 && !f.start(replacement), "replacement still owes native restart debt");
            f.after(replacement, restart, () -> {
                double cp = HighPowerResources.cp(replacement), cost = CirculationListeningRules.activationCost(HighPowerGrowth.values(replacement).position());
                h.assertTrue(f.start(replacement), "exact completed native replacement Posts permit authentic paid reactivation");
                near(h, HighPowerResources.cp(replacement), cp - cost, "reactivated clone pays a fresh activation exactly once"); ledger.unchanged(h, replacement);
                h.assertTrue(replacement.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_vector_parry") == unrelated, "reactivation cannot modify unrelated clocks"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115)
    public static void native_clone_rejects_mismatched_old_payment_clock_without_inventing_a_replacement(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            f.deplete(p); h.assertTrue(f.start(p), "paid native source starts");
            long certified = p.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY).getLong("resourceUntil");
            p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putLong("next_" + ID, certified + 1);
            ServerPlayer replacement = f.cloneActor(p, certified + 5000);
            h.assertFalse(replacement.getPersistentData().getCompound(HighPowerResources.DATA_KEY).contains("next_" + ID), "mismatched old writer cannot become a certified clone cooldown");
            int restart = replacement.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY).getInt("restart");
            f.after(replacement, Math.max(1, restart), () -> {
                double cp = HighPowerResources.cp(replacement);
                h.assertFalse(f.start(replacement), "exhausted restart debt cannot repair a missing certified cloned writer");
                near(h, HighPowerResources.cp(replacement), cp, "uncertified clone refusal neither pays nor refunds"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=105)
    public static void native_codec_rejects_oversized_and_out_of_range_cues_before_publication(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            for (int count : new int[]{9, 255}) {
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
                try {
                    buf.writeInt(p.getId()); buf.writeUUID(p.getUUID()); for (int field = 0; field < 4; field++) buf.writeLong(1);
                    buf.writeByte(CirculationListening.ACTIVE); buf.writeByte(count); boolean refused = false;
                    try { CirculationListening.Visual.CODEC.decode(buf); } catch (IllegalArgumentException expected) { refused = true; }
                    h.assertTrue(refused, "native codec refuses oversized batch before allocating source cues");
                } finally { buf.release(); }
            }
            for (int[] invalid : new int[][]{{16, 0, 1, 0}, {0, 2, 1, 0}, {0, 0, 0, 0}, {0, 0, 4, 0}, {0, 0, 1, 2}}) {
                boolean refused = false;
                try { new CirculationListening.Cue(invalid[0], invalid[1], invalid[2], invalid[3]); } catch (IllegalArgumentException expected) { refused = true; }
                h.assertTrue(refused, "coarse cue scalar outside approved bounds is refused");
            }
            h.assertFalse(CirculationListening.active(p), "malformed outbound representations create no active owner"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=105)
    public static void native_air_receipt_rejects_wall_and_fluid_mutation_before_final_seal(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        f.ready(() -> {
            Vec3 from = p.getEyePosition(), to = from.add(8, 0, 0); BlockPos touched = BlockPos.containing(from.add(3, 0, 0));
            try {
                var query = CirculationListening.class.getDeclaredMethod("airPath", ServerPlayer.class, Vec3.class, Vec3.class); query.setAccessible(true);
                Object first = query.invoke(null, p, from, to); h.assertTrue(first != null, "real vanilla ray initially traverses loaded clear air");
                var seal = first.getClass().getDeclaredMethod("unchanged"); seal.setAccessible(true);
                h.assertTrue((boolean)seal.invoke(first), "unchanged exact native block/fluid reads retain their receipt");
                h.getLevel().setBlockAndUpdate(touched, Blocks.STONE.defaultBlockState());
                h.assertFalse((boolean)seal.invoke(first), "later native wall mutation revokes the earlier ray before batch publication");
                h.assertTrue(query.invoke(null, p, from, to) == null, "fresh native ray independently confirms real wall occlusion");
                h.getLevel().setBlockAndUpdate(touched, Blocks.AIR.defaultBlockState());
                Object second = query.invoke(null, p, from, to); h.assertTrue(second != null, "restored native air supplies a new independent receipt");
                h.getLevel().setBlockAndUpdate(touched, Blocks.WATER.defaultBlockState());
                h.assertFalse((boolean)seal.invoke(second), "late native fluid mutation also revokes earlier air authority");
                h.assertTrue(query.invoke(null, p, from, to) == null, "fresh native ray confirms intervening fluid");
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            f.succeed();
        });
    }

    private static void near(GameTestHelper h, double a, double b, String why) { h.assertTrue(Math.abs(a - b) < 1e-6, why + ": " + a + " != " + b); }
    private static void eq(GameTestHelper h, BigDecimal a, BigDecimal b, String why) { h.assertTrue(a.compareTo(b) == 0, why + ": " + a + " != " + b); }
    private static void vector(GameTestHelper h, Vec3 a, Vec3 b, String why) { h.assertTrue(a.distanceToSqr(b) < 1e-10, why); }
    private static final class Ledger {
        final BigDecimal hp, shield, q, earned; final double mastery, levelExperience; final CompoundTag reserve;
        Ledger(ServerPlayer p) { hp = new BigDecimal(HighPowerCombat.healthText(p)); shield = HighPowerCombat.shield(p); q = HighPowerGrowth.values(p).position(); earned = HighPowerCombat.earnedGrowthPosition(p);
            mastery = AbilityStorage.get(p).exp(ID); levelExperience = AbilityStorage.get(p).levelExperience; reserve = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound(MagneticDomainReserve.KEY).copy(); }
        void unchanged(GameTestHelper h, ServerPlayer p) { eq(h, new BigDecimal(HighPowerCombat.healthText(p)), hp, "sensor cannot write exact HP"); eq(h, HighPowerCombat.shield(p), shield, "sensor cannot write base S");
            eq(h, HighPowerGrowth.values(p).position(), q, "sensor cannot write q"); eq(h, HighPowerCombat.earnedGrowthPosition(p), earned, "sensor cannot write Q");
            h.assertTrue(AbilityStorage.get(p).exp(ID) == mastery && AbilityStorage.get(p).levelExperience == levelExperience, "sensor cannot grant mastery or level XP");
            h.assertTrue(reserve.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound(MagneticDomainReserve.KEY)), "sensor cannot borrow or refill reserve"); }
    }
    public static final class PlayerPosts {
        final Fixture f; final GameTestHelper h; final ServerPlayer owner; final Map<Integer, Runnable> checks = new HashMap<>(); int last, completed;
        PlayerPosts(Fixture f, ServerPlayer p) { this.f = f; this.h = f.h; owner = p; last = p.server.getTickCount(); }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void tick(PlayerTickEvent.Post event) {
            if (f.terminalQueued || h.testInfo.isDone() || event.getEntity() != owner || owner.server.getTickCount() <= last) return;
            last = owner.server.getTickCount(); Runnable action = checks.remove(++completed);
            if (action != null) try { action.run(); } catch (Throwable failure) { LOG.error("NEW433 native assertion after " + completed + " owner Posts", failure); f.fail(failure); }
        }
    }
    /** Observe CP immediately around native controller ServerTick.Pre work, before the
     * separate ordinary PlayerTick recovery budget. Recovery is left fully native. */
    public static final class RenewalPayments {
        final ServerPlayer owner; long listening, relief; double cp, charged; int renewals; boolean exact = true;
        RenewalPayments(ServerPlayer owner) { this.owner = owner; }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void before(ServerTickEvent.Pre event) {
            if (event.getServer() != owner.server) return;
            listening = owner.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY).getLong("receipt");
            relief = owner.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY).getLong("receipt"); cp = HighPowerResources.cp(owner);
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void after(ServerTickEvent.Pre event) {
            if (event.getServer() != owner.server) return;
            long nextListening = owner.getPersistentData().getCompound(CirculationListening.PAYMENT_KEY).getLong("receipt");
            long nextRelief = owner.getPersistentData().getCompound(CirculationRelief.PAYMENT_KEY).getLong("receipt");
            if (nextListening == listening && nextRelief == relief) return;
            renewals++; double debit = cp - HighPowerResources.cp(owner); charged += debit;
            exact &= nextListening == listening + 1 && nextRelief == relief + 1
                    && Math.abs(debit - 2 * VectorParryRules.sustainCost(HighPowerGrowth.values(owner).position())) < 1e-6;
        }
    }
    /** Real native world ticks invoke this bounded fixture. No fake PlayerTick event is posted. */
    public static final class NativeTicks {
        final Fixture f; int last;
        NativeTicks(Fixture f) { this.f = f; last = f.h.getLevel().getServer().getTickCount(); }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void before(ServerTickEvent.Pre e) {
            if (e.getServer() != f.h.getLevel().getServer() || f.closed || f.terminalQueued) return;
            try {
                for (var entry : List.copyOf(f.velocities.entrySet())) {
                    Zombie runner = entry.getKey(); if (runner.isRemoved()) continue;
                    runner.move(MoverType.SELF, entry.getValue()); runner.setDeltaMovement(entry.getValue()); runner.setSprinting(true);
                    Runnable mutate = f.tampering.get(runner); if (mutate != null) mutate.run();
                }
                for (Zombie runner : f.frozen) runner.setDeltaMovement(new Vec3(.25, 0, 0));
                for (var entry : List.copyOf(f.packetVelocities.entrySet())) {
                    ServerPlayer runner = entry.getKey(); Vec3 destination = runner.position().add(entry.getValue());
                    runner.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(destination.x, destination.y, destination.z, true));
                    vector(f.h, runner.position(), destination, "real server movement handler accepts collision-free sprint packet");
                }
            } catch (Throwable failure) { f.fail(failure); }
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void resetBeforeNativeEntityTicks(ServerTickEvent.Pre e) {
            if (e.getServer() != f.h.getLevel().getServer() || f.closed || f.terminalQueued) return;
            // Explicit acceleration fixture: scanner sees the actual displacement's velocity;
            // later native AI/physics cannot add a second fixture movement in the same tick.
            for (Zombie runner : f.velocities.keySet()) runner.setDeltaMovement(Vec3.ZERO);
            for (Zombie runner : f.frozen) runner.setDeltaMovement(Vec3.ZERO);
        }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void after(ServerTickEvent.Post e) {
            if (e.getServer() != f.h.getLevel().getServer() || f.closed || f.terminalQueued || e.getServer().getTickCount() <= last) return;
            last = e.getServer().getTickCount();
            try {
                for (ServerPlayer p : List.copyOf(f.players)) if (!p.isRemoved() && !p.hasDisconnected()) p.doTick();
            } catch (Throwable failure) { f.fail(failure); }
        }
    }
    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>(); final List<Object> hooks = new ArrayList<>();
        final Map<ServerPlayer, EmbeddedChannel> channels = new IdentityHashMap<>(); final Map<ServerPlayer, Vec3> anchors = new IdentityHashMap<>();
        final Map<ServerPlayer, Long> inputs = new IdentityHashMap<>(); final Map<ServerPlayer, String> held = new IdentityHashMap<>();
        final Map<ServerPlayer, Vec3> packetVelocities = new IdentityHashMap<>();
        final Map<ServerPlayer, PlayerPosts> clocks = new IdentityHashMap<>(); final Map<Zombie, Vec3> velocities = new IdentityHashMap<>(); final Map<Zombie, Runnable> tampering = new IdentityHashMap<>();
        final Set<Zombie> frozen = Collections.newSetFromMap(new IdentityHashMap<>());
        MachineDeveloperBlockEntity machine; int learnSequence; boolean closed, terminalQueued; Throwable terminalFailure; double beforeNextRenewal, renewalCost;
        Fixture(GameTestHelper h) {
            this.h = h; startedFixtures++; hook(new NativeTicks(this));
            // Register before GameTestInfo iterates its scheduled work. Native callbacks only
            // queue an outcome; this poll owns the terminal transition inside tickInternal,
            // which is required for vanilla completion listeners and next-batch scheduling.
            h.onEachTick(this::finishQueuedTerminal);
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {}
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { LOG.error("NEW433 GameTest failed: " + i.getTestName(), i.getError()); close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        void succeed() { if (!terminalQueued && !closed) terminalQueued = true; }
        void fail(Throwable failure) { if (!terminalQueued && !closed) { terminalFailure = failure; terminalQueued = true; } }
        void finishQueuedTerminal() {
            if (!terminalQueued || closed || h.testInfo.isDone()) return;
            if (terminalFailure == null) h.succeed(); else h.testInfo.fail(terminalFailure);
        }
        ServerPlayer player() { return player(true); }
        ServerPlayer player(boolean learned) { return player(28, 28, learned); }
        ServerPlayer player(double x, double z, boolean learned) {
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "CL" + UUID.randomUUID().toString().substring(0, 10)), false);
            ServerPlayer p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            Connection connection = new Connection(PacketFlow.SERVERBOUND); EmbeddedChannel channel = new EmbeddedChannel(connection); channels.put(p, channel);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); visuals(p);
            for (int dx = -20; dx <= 20; dx++) for (int dz = -20; dz <= 20; dz++) if (x + dx >= 0 && x + dx < 64 && z + dz >= 0 && z + dz < 64) h.setBlock(BlockPos.containing(x + dx, 1, z + dz), Blocks.STONE);
            p.setGameMode(GameType.SURVIVAL); p.getFoodData().setFoodLevel(6); p.setNoGravity(true); anchors.put(p, h.absoluteVec(new Vec3(x + .5, 2, z + .5))); place(p);
            h.assertTrue(HighPowerCombat.enroll(p, 3), "declared genuine connected LV3 enrollment"); var state = AbilityStorage.get(p); state.selectCategory(HighPowerCategories.VECMANIP); state.setLevel(3);
            for (String skill : List.of(HighPowerSkills.DEFLECTION_STRIKE, VectorParry.ID, InertialAnchor.ID, CirculationRelief.ID)) state.learn(skill);
            if (learned) state.learn(ID); state.activated = true; state.overloadFine = true;
            h.assertTrue(state.presets.edit(0, 0, learned ? ID : HighPowerSkills.DEFLECTION_STRIKE, state::learned) && state.presets.edit(0, 1, CirculationRelief.ID, state::learned), "declared learned fixtures use ordinary slots");
            HighPowerResources.initializeBudget(p); return p;
        }
        void ready(Runnable action) { h.runAfterDelay(61, () -> { for (ServerPlayer p : players) { p.doTick(); place(p); visuals(p); } action.run(); }); }
        void place(ServerPlayer p) { Vec3 at = anchors.get(p); p.moveTo(at.x, at.y, at.z, 0, 0); p.setYHeadRot(0); p.yBodyRot = 0; p.setOnGround(true); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        boolean request(ServerPlayer p, String action, String value) {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            try { AcademyNetwork.Request.CODEC.encode(buf, new AcademyNetwork.Request(action, value)); AcademyNetwork.Request decoded = AcademyNetwork.Request.CODEC.decode(buf);
                h.assertTrue(buf.readableBytes() == 0, "native bounded input codec consumes the complete request"); return AcademyGameplay.requestFromClient(p, decoded); }
            finally { buf.release(); }
        }
        String wire(ServerPlayer p, int slot) { long next = inputs.getOrDefault(p, 0L) + 1; inputs.put(p, next); return new HighPowerInput.Input(slot, next, p.getId(), LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p))).wire(); }
        boolean start(ServerPlayer p) { String value = wire(p, 0); boolean started = request(p, "circ_listen_start", value); if (started) held.put(p, value); return started; }
        void after(ServerPlayer p, int delay, Runnable action) { PlayerPosts clock = clocks.computeIfAbsent(p, actor -> { PlayerPosts result = new PlayerPosts(this, actor); hook(result); return result; }); clock.checks.put(clock.completed + delay, action); }
        List<CirculationListening.Cue> cues(ServerPlayer p) { var visual = CirculationListening.lastVisual(p); return visual == null ? List.of() : visual.cues(); }
        void quiet(ServerPlayer p, String why) { h.assertTrue(cues(p).isEmpty(), why + ": " + cues(p)); }
        List<CirculationListening.Visual> visuals(ServerPlayer p) { List<CirculationListening.Visual> result = new ArrayList<>(); EmbeddedChannel channel = channels.get(p); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet;
            while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId()));
                if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof CirculationListening.Visual visual) { RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
                    try { CirculationListening.Visual.CODEC.encode(buf, visual); var decoded = CirculationListening.Visual.CODEC.decode(buf); h.assertTrue(decoded.equals(visual) && buf.readableBytes() == 0, "real owner cue payload roundtrips without source identity or coordinates"); result.add(decoded); } finally { buf.release(); } }
                ReferenceCountUtil.release(packet); } return result; }
        Arrow arrow(Vec3 at, Vec3 velocity) { Arrow a = new Arrow(h.getLevel(), at.x, at.y, at.z, new ItemStack(Items.ARROW), null); a.setDeltaMovement(velocity); entity(a); return a; }
        Zombie runner(Vec3 at, boolean moving) { Zombie result = new Zombie(EntityType.ZOMBIE, h.getLevel()); prepare(result, at); if (moving) run(result, new Vec3(.25, 0, 0)); return result; }
        void prepare(Zombie runner, Vec3 at) { runner.setNoAi(true); runner.setSilent(true); runner.setPos(at); runner.setSprinting(true); runner.getAttribute(Attributes.ARMOR).setBaseValue(0); runner.getAttribute(Attributes.ATTACK_KNOCKBACK).setBaseValue(0); entity(runner); }
        void run(Zombie runner, Vec3 velocity) { frozen.remove(runner); velocities.put(runner, velocity); }
        void stop(Zombie runner) { velocities.remove(runner); frozen.remove(runner); tampering.remove(runner); }
        void freeze(Zombie runner) { velocities.remove(runner); frozen.add(runner); }
        void tamper(Zombie runner, Runnable mutation) { if (mutation == null) tampering.remove(runner); else tampering.put(runner, mutation); }
        void entity(Entity entity) { h.assertTrue(h.getLevel().addFreshEntity(entity), "real native entity joins server level"); entities.add(entity); }
        void cp(ServerPlayer p, double value) { p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", value); }
        void deplete(ServerPlayer p) { var combat = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY); combat.putString("shield", "17.25"); combat.putLong("lastShieldSpend", p.serverLevel().getGameTime()); combat.putString("health", "37"); p.setHealth(10); cp(p, 100);
            CompoundTag reserve = new CompoundTag(); reserve.putInt("version", MagneticDomainReserve.VERSION); reserve.putString("amount", "9"); reserve.putString("capacity", HighPowerCombat.maxShieldText(p)); reserve.putString("lower", "4"); reserve.putString("upper", "5"); combat.put(MagneticDomainReserve.KEY, reserve); }
        ServerPlayer cloneActor(ServerPlayer original, long unrelatedClock) {
            Vec3 at = anchors.get(original); original.server.getPlayerList().remove(original); players.remove(original);
            var cookie = CommonListenerCookie.createInitial(original.getGameProfile(), false);
            ServerPlayer replacement = new ServerPlayer(original.server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND); EmbeddedChannel channel = new EmbeddedChannel(connection); channels.put(replacement, channel);
            NetworkRegistry.configureMockConnection(connection); original.server.getPlayerList().placeNewPlayer(connection, replacement, cookie); players.add(replacement); visuals(replacement);
            // placeNewPlayer reads the old save. Clear only these fixture-preloaded fields
            // to reproduce a fresh restoreFrom target while retaining an explicit finite
            // target CP balance and unrelated resource writer that clone must not replace.
            replacement.getPersistentData().remove(CirculationListening.PAYMENT_KEY);
            CompoundTag budget = replacement.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
            budget.remove("next_" + ID); budget.putDouble("cp", 37); budget.putLong("next_vector_parry", unrelatedClock);
            replacement.restoreFrom(original, true);
            anchors.put(replacement, at); replacement.setGameMode(GameType.SURVIVAL); replacement.setNoGravity(true); replacement.getFoodData().setFoodLevel(6); place(replacement);
            return replacement;
        }
        void reload(ServerPlayer p) { AbilityStorage.save(p); CompoundTag saved = p.saveWithoutId(new CompoundTag()); CirculationListening.remove(p); CirculationRelief.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.load(saved); place(p); inputs.put(p, 0L); }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        DeveloperItemEnergy portable(ServerPlayer p, double amount) { ItemStack stack = new ItemStack(AcademyCraft.DEVELOPER.get()); p.setItemInHand(InteractionHand.MAIN_HAND, stack); DeveloperItemEnergy energy = new DeveloperItemEnergy(stack, DeveloperType.PORTABLE); energy.energy(amount); return energy; }
        void develop(ServerPlayer p, int ticks) { for (int i = 0; i < ticks; i++) DevelopmentController.tick(p, ignored -> {}); }
        MachineDeveloperBlockEntity normal(ServerPlayer p, double amount) { BlockPos base = h.absolutePos(new BlockPos(54, 1, 28));
            if (machine == null) { var item = MachineDevelopers.NORMAL_ITEM.get(); ItemStack stack = new ItemStack(item); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
                h.assertTrue(item.place(new BlockPlaceContext(p, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(base), Direction.UP, base, false))).consumesAction(), "genuine complete Normal developer is placed"); machine = (MachineDeveloperBlockEntity)h.getLevel().getBlockEntity(base); }
            machine.endUsers(); machine.battery().load(amount); p.moveTo(base.getX() + .5, base.getY(), base.getZ() - 1.5, 0, 0); h.assertTrue(machine.use(p), "real native player-bound machine session opens"); return machine; }
        boolean learnRequest(ServerPlayer p, String skill) {
            var request = new HighPowerLearningProtocol.Request(skill, ++learnSequence);
            String value = MachineDeveloperSessions.activeToken(p).orElseThrow() + ":" + request.wire();
            h.assertTrue(value.length() <= 64 && request.equals(HighPowerLearningProtocol.parse(request.wire())), "bounded machine alias roundtrips to the canonical skill and exact sequence");
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            try { AcademyNetwork.Request.CODEC.encode(buf, new AcademyNetwork.Request("machine_hp_learn", value)); var decoded = AcademyNetwork.Request.CODEC.decode(buf);
                h.assertTrue(decoded.value().equals(value) && buf.readableBytes() == 0, "native codec preserves the complete bounded machine request");
                return MachineDeveloperSessions.request(p, decoded); }
            finally { buf.release(); }
        }
        void learn(ServerPlayer p, String skill) { int count = ClassicRules.learningStimulations(SkillCatalog.find(HighPowerCategories.VECMANIP, skill).orElseThrow().level()); var developer = normal(p, DeveloperType.NORMAL.actualConsumption(count));
            h.assertTrue(learnRequest(p, skill), "genuine finite Normal parent learning starts"); develop(p, count * DeveloperType.NORMAL.ticksPerStimulation()); near(h, developer.battery().getEnergy(), 0, "genuine parent consumes all finite IF"); h.assertTrue(AbilityStorage.get(p).learned(skill), "genuine parent completes"); }
        void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); if (machine != null) machine.endUsers();
            for (ServerPlayer p : players) { CirculationListening.remove(p); CirculationRelief.remove(p); DevelopmentController.remove(p); MachineDeveloperSessions.close(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.server.getPlayerList().remove(p); }
            entities.forEach(Entity::discard); channels.values().forEach(EmbeddedChannel::finishAndReleaseAll);
            closedFixtures++;
            LOG.info("NEW433 native fixture closed: name={} created={} closed={} ownPlayersStillConnected={} watchReferences={}",
                    h.testInfo.getTestName(), startedFixtures, closedFixtures,
                    players.stream().filter(p -> p.server.getPlayerList().getPlayer(p.getUUID()) == p).count(),
                    CirculationListeningMotionProof.watchReferenceCount());
        }
    }
}

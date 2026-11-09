package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CrossSubstitutionRules;
import cn.academy.port.highpower.core.ReturnMarkGeometry;
import cn.academy.port.highpower.core.ReturnMarkRules;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * NEW321 self-swap through genuine authenticated floor captures, native event-bus
 * callbacks, resident server players and the real connection teleport method.
 * Learned flags, finite floor patches and deliberate source placement are declared
 * fixtures. Time passes through server ticks, never repeated controller tick calls.
 * These assertions cover server transport; native client velocity reset is separate QA.
 */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerCrossSelfSwapRuntimeTests {
    private static final String TEMPLATE = "highpower_cross_self_swap_empty", BATCH = "highpower_cross_self_swap";
    private static final String ID = CrossSubstitution.ID;
    private static final String OWN = "academy:cross_substitution_fences", SHARED = "academy:safe_landing_probe_fences";
    private static final TicketType<UUID> TICKET = TicketType.create("academy_cross_self_swap_fixture", UUID::compareTo);
    private HighPowerCrossSelfSwapRuntimeTests() {}

    /** Isolated48m arena keeps native32m routes and their collision apron away from neighboring tests. */
    @SubscribeEvent public static void install(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(); ListTag size = new ListTag();
        size.add(IntTag.valueOf(48)); size.add(IntTag.valueOf(8)); size.add(IntTag.valueOf(32)); tag.put("size", size);
        ListTag blocks = new ListTag(); CompoundTag first = new CompoundTag(); ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0));
        first.put("pos", pos); first.putInt("state", 0); blocks.add(first); tag.put("blocks", blocks);
        tag.put("entities", new ListTag()); ListTag palette = new ListTag(); CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air"); palette.add(air); tag.put("palette", palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_highpower", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    /** The admitted sidestep distance of this player's growth (level-scaled). */
    static double sidestepDist(net.minecraft.server.level.ServerPlayer p){return cn.academy.port.highpower.core.PhaseSidestepRules.distance(cn.academy.port.highpower.HighPowerGrowth.values(p).position());}

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void genuine_A_to_B_and_B_to_A_use_exact_saved_feet_without_ammunition(GameTestHelper h) {
        direction(h, false);
    }
    private static void direction(GameTestHelper h, boolean reverse) {
        Fixture f = new Fixture(h); f.p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        f.ready(() -> {
            f.crouch(reverse ? f.b : f.a); Vec3 expected = reverse ? f.a : f.b;
            f.p.setYRot(73); f.p.setXRot(-18); f.p.setDeltaMovement(.025, -.0784, -.04);
            Snapshot before = f.snapshot(); Body beforeBody = Body.of(f.p); var custody = CrossNeedleRegistry.get(h.getLevel()).snapshots(f.p.getUUID());
            TeleportHook hook = f.onTeleport(event -> {
                h.assertTrue(CrossSubstitution.preparedPoints(f.p) == 0 && !CrossSubstitution.marked(f.p)
                        && CrossSubstitution.remainingTicks(f.p) == 0, "both marks are consumed before the actual native teleport event");
                h.assertTrue(!CrossSubstitution.receipt(f.p).paid(), "native callback precedes whole CP debit");
            });
            f.drain(); h.assertTrue(f.press(), "grounded crouch swaps " + (reverse ? "B to A" : "A to B") + ": " + CrossSubstitution.receipt(f.p));
            f.success(before, expected); h.assertTrue(Body.of(f.p).equals(beforeBody), "swap preserves server velocity, aim, fall, ground, health, hurt and effects");
            h.assertTrue(hook.calls == 1 && f.spawns == 0 && f.needles() == 0, "one native teleport and no medium or stock requirement");
            h.assertTrue(CrossNeedleRegistry.get(h.getLevel()).snapshots(f.p.getUUID()).equals(custody), "self-swap creates no custody row or attack permit");
            var packets = f.positions(); h.assertValueEqual(packets.size(), 1, "one native position packet");
            var packet = packets.getFirst(); h.assertTrue(new Vec3(packet.getX(), packet.getY(), packet.getZ()).equals(expected), "packet carries exact saved absolute XYZ");
            h.assertTrue(packet.getRelativeArguments().equals(RelativeMovement.ROTATION), "native XYZ velocity reset semantics are retained in transport flags");
            Snapshot paid = f.snapshot(); CompoundTag own = f.own(), shared = f.shared(); var receipt = CrossSubstitution.receipt(f.p);
            h.assertTrue(CrossSubstitution.release(f.p, f.wire(f.inputs)), "matching release is harmless");
            h.assertFalse(CrossSubstitution.press(f.p, f.wire(f.inputs)), "same physical input cannot replay");
            f.same(paid, "release and duplicate input"); h.assertTrue(f.own().equals(own) && f.shared().equals(shared)
                    && CrossSubstitution.receipt(f.p).equals(receipt), "release and duplicate retain history and fences");
            f.p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()));
            f.same(paid, "native teleport acknowledgment");
        });
        h.runAfterDelay(9, () -> { f.verify(); f.close(); if (!reverse) direction(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=45)
    public static void whole_existing_cost_curve_and_cooldown_commit_without_free_growth(GameTestHelper h) { anchor(h, 2); }
    private static void anchor(GameTestHelper h, int level) {
        double[] costs = {12, 6, 3, .75, .075}; int[] cooldowns = {60, 30, 16, 6, 1};
        for (int i = 0; i < 5; i++) {
            near(h, CrossSubstitutionRules.cpCost(BigDecimal.valueOf(i + 1)), costs[i], "unchanged whole CP curve anchor " + (i + 1));
            h.assertValueEqual(CrossSubstitutionRules.cooldownTicks(BigDecimal.valueOf(i + 1)), cooldowns[i], "unchanged economic cooldown anchor");
        }
        Fixture f = new Fixture(h, level, 22.5, 26.5);
        f.ready(() -> {
            f.crouch(f.a); Snapshot before = f.snapshot(); long now = h.getLevel().getGameTime();
            CompoundTag old = shared(3, 0, 17, 13); old.putString("unrelated", "retain"); f.p.getPersistentData().put(SHARED, old);
            h.assertTrue(f.press(), "native eligible LV" + level + " self-swap"); f.success(before, f.b);
            near(h, f.cp(), before.cp - costs[level - 1], "exact whole cost, never one-third needle cost");
            h.assertTrue(f.own().getInt("cooldown") == cooldowns[level - 1] && f.own().getInt("cast") == 8,
                    "economic cooldown and separate eight-tick physical cast fence");
            h.assertTrue(AbilityStorage.get(f.p).cooldowns.get(ID) == cooldowns[level - 1]
                    && f.budget().getLong("next_" + ID) == now + cooldowns[level - 1], "UI and resource deadline use the same whole cooldown");
            CompoundTag wanted = old.copy(); wanted.putInt("movement", 8);
            h.assertTrue(f.shared().equals(wanted), "only shared movement changes; preview, mastery, cooldown and foreign fields remain");
        });
        h.runAfterDelay(9, () -> { f.verify(); f.close(); if (level < 5) anchor(h, level + 1); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=16)
    public static void zero_or_one_point_crouch_is_free_and_retains_original_single_mark(GameTestHelper h) {
        Fixture f = new Fixture(h); f.crouch(f.source); Snapshot empty = f.snapshot();
        h.assertFalse(f.press(), "zero-point crouch cannot invent endpoints"); f.same(empty, "empty swap");
        h.assertValueEqual(CrossSubstitution.preparedPoints(f.p), 0, "zero endpoints remain zero");
        f.post(4, () -> { f.stand(f.source); f.markA(); });
        f.post(8, () -> {
            int ttl = CrossSubstitution.remainingTicks(f.p); Snapshot before = f.snapshot(); f.crouch(f.a);
            h.assertFalse(f.press(), "one-point crouch cannot move"); f.same(before, "incomplete swap");
            h.assertTrue(CrossSubstitution.preparedPoints(f.p) == 1 && CrossSubstitution.remainingTicks(f.p) == ttl,
                    "incomplete preflight retains exactly one mark and its original countdown");
            h.assertTrue(CrossSubstitution.receipt(f.p).reason().equals("two_points_required"), "explicit incomplete-pair reason");
        }); f.finish(9);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=45)
    public static void neither_near_both_near_and_outside_horizontal_or_vertical_tolerance_keep_marks(GameTestHelper h) { proximityRefusal(h, 0); }
    private static void proximityRefusal(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h, 3, 22.5, mode == 1 ? 23.5 : 26.5);
        f.ready(() -> {
            Vec3 source = switch (mode) {
                case 0 -> f.source;
                case 1 -> f.a.add(f.b).scale(.5);
                case 2 -> f.a.add(-.750001, 0, 0);
                default -> f.a.add(0, .125001, 0);
            };
            f.crouch(source); f.refuseKeepingMarks("invalid source proximity " + mode);
            String reason = CrossSubstitution.receipt(f.p).reason();
            h.assertTrue(mode == 3 ? reason.equals("source_floor_unsupported") || reason.equals("source_not_near_point")
                    : reason.equals(mode == 1 ? "source_near_both_points" : "source_not_near_point"), "source-selection or genuine raised-floor refusal: " + reason);
        });
        h.runAfterDelay(9, () -> { f.verify(); f.close(); if (mode < 3) proximityRefusal(h, mode + 1); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void exact_horizontal_boundary_is_inclusive_and_lands_at_other_saved_point(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            f.crouch(f.a.add(-.75, 0, 0)); Snapshot before = f.snapshot();
            h.assertTrue(f.press(), "exact .75 horizontal threshold is included"); f.success(before, f.b);
        }); f.finish(9);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void exact_vertical_boundary_uses_real_raised_support_and_saved_standing_body(GameTestHelper h) {
        Fixture f = new Fixture(h, 3, 22.6, 26.5);
        for (int x = 23; x <= 28; x++) for (int z = 6; z <= 16; z++)
            f.block(h.absolutePos(new BlockPos(x, 5, z)), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2));
        f.pointB = f.pointB.add(0, .125, 0); f.stand(f.source.add(0, .125, 0));
        f.ready(() -> {
            f.crouch(f.a.add(.75, .125, 0));
            h.assertTrue(h.getLevel().noCollision(f.p, f.p.getBoundingBox()), "native crouching source has real raised snow support and clear body");
            Snapshot before = f.snapshot(); h.assertTrue(f.press(), "exact .125 Y threshold is included: " + CrossSubstitution.receipt(f.p)); f.success(before, f.b);
        }); f.finish(9);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void admitted_cancellation_and_redirect_are_free_terminal_and_consume_pair(GameTestHelper h) { eventRefusal(h, false); }
    private static void eventRefusal(GameTestHelper h, boolean redirect) {
        Fixture f = new Fixture(h); f.ready(() -> {
            f.crouch(f.a); Snapshot before = f.snapshot(); Vec3 source = f.p.position();
            TeleportHook hook = f.onTeleport(event -> {
                h.assertTrue(CrossSubstitution.preparedPoints(f.p) == 0 && CrossSubstitution.remainingTicks(f.p) == 0, "admission consumes pair before external callbacks");
                if (redirect) event.setTargetZ(event.getTargetZ() + .1); else event.setCanceled(true);
            });
            h.assertFalse(f.press(), "actual native event " + (redirect ? "redirect" : "cancellation") + " refuses");
            f.freeTerminal(redirect ? "event_redirected" : "event_cancelled"); f.same(before, "native event refusal");
            h.assertTrue(f.p.position().equals(source) && hook.calls == 1, "callback cannot move source or cause a second teleport event");
            h.assertFalse(f.press(), "fresh crouch cannot replay consumed endpoints"); f.same(before, "retry after terminal refusal");
            h.assertValueEqual(hook.calls, 1, "consumed pair cannot reach callback again");
        }); h.runAfterDelay(9, () -> { f.verify(); f.close(); if (!redirect) eventRefusal(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void cancellation_preserves_foreign_callback_budget_edit_without_own_debit(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            f.crouch(f.a); double cp = f.cp();
            f.onTeleport(event -> { f.budget().putDouble("cp", f.cp() - 1); f.p.getPersistentData().putInt("academy:self_swap_callback", 7); event.setCanceled(true); });
            h.assertFalse(f.press(), "native cancellation follows declared callback edit"); f.freeTerminal("event_cancelled");
            near(h, f.cp(), cp - 1, "foreign debit is preserved without refund or whole self-swap charge");
            h.assertValueEqual(f.p.getPersistentData().getInt("academy:self_swap_callback"), 7, "callback metadata is retained");
        }); f.finish(9);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void callback_source_pose_aim_motion_ground_and_state_changes_cannot_pay(GameTestHelper h) { stateMutation(h, 0); }
    private static void stateMutation(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h); f.ready(() -> {
            f.crouch(f.a); double cp = f.cp();
            f.onTeleport(event -> {
                switch (mode) {
                    case 0 -> f.p.setPos(f.a.add(.1, 0, 0));
                    case 1 -> f.p.setPose(Pose.STANDING);
                    case 2 -> f.p.setYRot(f.p.getYRot() + 1);
                    case 3 -> f.p.setXRot(f.p.getXRot() + 1);
                    case 4 -> f.p.setDeltaMovement(.1, -.0784, 0);
                    case 5 -> f.p.setOnGround(false);
                    case 6 -> f.p.fallDistance = 2;
                    case 7 -> f.p.setShiftKeyDown(false);
                    case 8 -> AbilityStorage.get(f.p).activated = false;
                    default -> AbilityStorage.get(f.p).presets.edit(0, 0, DisplacedProjection.ID, AbilityStorage.get(f.p)::learned);
                }
            });
            f.drain(); h.assertFalse(f.press(), "post-admission source/state mutation " + mode + " refuses");
            near(h, f.cp(), cp, "callback mutation cannot pay");
            h.assertFalse(CrossSubstitution.marked(f.p) || CrossSubstitution.active(f.p), "changed admitted pair stays terminal");
            h.assertTrue(f.positions().isEmpty() && f.spawns == 0, "changed source cannot reach native teleport or attack publication");
            if (mode == 0) h.assertTrue(f.p.position().equals(f.a.add(.1, 0, 0)), "foreign position edit is not rolled back");
        }); h.runAfterDelay(9, () -> { f.verify(); f.close(); if (mode < 9) stateMutation(h, mode + 1); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void callback_reentry_and_explicit_abort_have_single_terminal_outcomes(GameTestHelper h) { reentry(h, false); }
    private static void reentry(GameTestHelper h, boolean abort) {
        Fixture f = new Fixture(h); f.ready(() -> {
            f.crouch(f.a); Snapshot before = f.snapshot(); boolean[] nested = {true};
            TeleportHook hook = f.onTeleport(event -> nested[0] = abort
                    ? CrossSubstitution.abort(f.p, f.wire(f.inputs)) : f.press());
            if (abort) {
                h.assertFalse(f.press(), "authenticated callback abort revokes admitted operation");
                near(h, f.cp(), before.cp, "callback abort is free"); h.assertFalse(CrossSubstitution.marked(f.p), "abort cannot restore marks");
            } else {
                h.assertTrue(f.press(), "outer admitted self-swap succeeds once"); f.success(before, f.b);
                h.assertFalse(nested[0], "reentrant input is rejected while processing");
            }
            h.assertValueEqual(hook.calls, 1, "reentry does not repost native teleport callback");
        }); h.runAfterDelay(9, () -> { f.verify(); f.close(); if (!abort) reentry(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void callback_new_destination_and_corridor_obstructions_revalidate_before_payment(GameTestHelper h) { callbackTerrain(h, false); }
    private static void callbackTerrain(GameTestHelper h, boolean corridor) {
        Fixture f = new Fixture(h); f.ready(() -> {
            f.crouch(f.a); Snapshot before = f.snapshot();
            f.onTeleport(event -> f.block(BlockPos.containing(corridor ? f.a.add(f.b).scale(.5).add(0, 1, 0) : f.b.add(0, 1, 0)), Blocks.STONE.defaultBlockState()));
            f.drain(); h.assertFalse(f.press(), "new " + (corridor ? "corridor" : "destination") + " block in real event refuses");
            f.same(before, "post-event terrain mutation"); f.freeTerminal(null);
            h.assertTrue(f.positions().isEmpty(), "geometry revalidation precedes actual native connection transport");
        }); h.runAfterDelay(9, () -> { f.verify(); f.close(); if (!corridor) callbackTerrain(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=55)
    public static void wall_entity_destination_ceiling_and_crouch_only_corridor_are_free_preflight(GameTestHelper h) { blocked(h, 0); }
    private static void blocked(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Vec3 midpoint = f.a.add(f.b).scale(.5);
            switch (mode) {
                case 0 -> f.block(BlockPos.containing(midpoint.add(0, 1, 0)), Blocks.STONE.defaultBlockState());
                case 1 -> f.mob(midpoint);
                case 2 -> f.mob(f.b);
                case 3 -> f.block(BlockPos.containing(f.b).above(), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
                default -> f.block(BlockPos.containing(midpoint).above(), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
            }
            f.crouch(f.a); f.refuseKeepingMarks("blocked full standing body " + mode);
            h.assertTrue(f.spawns == 0 && f.needles() == 3, "preflight cannot launch or consume a needle");
            if (mode >= 3) {
                AABB crouch = f.p.getBoundingBox().move((mode == 3 ? f.b : midpoint).subtract(f.p.position()));
                h.assertTrue(h.getLevel().noCollision(f.p, crouch), "declared top slab admits crouching body but rejects mandatory saved standing body");
            }
        }); h.runAfterDelay(9, () -> { f.verify(); f.close(); if (mode < 4) blocked(h, mode + 1); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void native_long_distance_swap_includes_actual_32m_and_rejects_beyond_without_old_range_changes(GameTestHelper h) { longRange(h, false); }
    private static void longRange(GameTestHelper h, boolean outside) {
        Fixture f = new Fixture(h, 3, 8.5, 40.25); f.ready(() -> {
            h.assertTrue(f.a.distanceTo(f.b) > 24 && f.source.distanceTo(f.a) > 12, "real native floor pair exercises the new long-range capture and sweep");
            double distance = outside ? 32.000001 : 32;
            f.crouch(new Vec3(f.b.x - distance, f.b.y, f.b.z));
            near(h, f.p.position().distanceTo(f.b), distance, "actual feet displacement sets range");
            if (outside) { f.refuseKeepingMarks("actual movement just beyond32"); h.assertTrue(CrossSubstitution.receipt(f.p).reason().equals("destination_out_of_range"), "exact range refusal"); }
            else { Snapshot before = f.snapshot(); h.assertTrue(f.press(), "exact32m actual full-body transport: " + CrossSubstitution.receipt(f.p)); f.success(before, f.b); }
            var zero = new SafeLandingProbeGeometry.Point(0, 0, 0);
            h.assertFalse(SafeLandingProbeGeometry.withinRange(zero, new SafeLandingProbeGeometry.Point(12.000001, 0, 0)), "NEW313 remains12m");
            double returnMarkReach = ReturnMarkRules.RANGE;
            h.assertTrue(ReturnMarkGeometry.withinRange(zero, new SafeLandingProbeGeometry.Point(returnMarkReach, 0, 0))
                    && !ReturnMarkGeometry.withinRange(zero, new SafeLandingProbeGeometry.Point(Math.nextUp(returnMarkReach), 0, 0)),
                    "NEW322 retains its own configured boundary");
        }); h.runAfterDelay(9, () -> { f.verify(); f.close(); if (!outside) longRange(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void native_first_UP_capture_near_32m_succeeds_and_beyond_ray_budget_refuses(GameTestHelper h) {
        Fixture inside = new Fixture(h, 3, 40.45, 26.5);
        inside.stand(h.absoluteVec(new Vec3(8.5, 5, 14.5))); inside.p.setYRot(-90); inside.p.setXRot(2.91F);
        // Vanilla's lookup-table sine makes the geometrically aimed2.902-degree ray
        // reach the floor after32m. This declared native angle reaches it around31.955m.
        // The actual native first-UP hit below, not the intended point, proves the bound.
        Vec3 hit = inside.surface(); Snapshot before = inside.snapshot();
        h.assertTrue(inside.p.getEyePosition().distanceTo(hit) > 31.9 && inside.p.getEyePosition().distanceTo(hit) <= 32,
                "independent native first-UP hit lies near the32m eye-ray cap: distance=" + inside.p.getEyePosition().distanceTo(hit));
        h.assertTrue(inside.press() && CrossSubstitution.preparedPoints(inside.p) == 1,
                "native long first-UP capture has no accidental12m body-translation cap"); inside.same(before, "near32m free preparation"); inside.close();
        Fixture outside = new Fixture(h, 3, 40.55, 26.5);
        outside.stand(h.absoluteVec(new Vec3(8.5, 5, 14.5))); outside.aim(outside.pointA); before = outside.snapshot();
        var missed = outside.ray(); h.assertTrue(missed.getType() == HitResult.Type.MISS,
                "independent native ray cannot reach beyond-budget floor: " + outside.rayDescription(missed));
        h.assertFalse(outside.press(), "floor beyond32m eye ray cannot become a prepared point"); outside.same(before, "beyond32m preparation refusal");
        h.assertTrue(CrossSubstitution.preparedPoints(outside.p) == 0 && outside.spawns == 0, "out-of-range ray invents no shorter point or publication"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void insufficient_cp_is_free_and_keeps_pair_for_a_later_real_input(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            f.crouch(f.a); f.budget().putDouble("cp", .1); f.refuseKeepingMarks("insufficient whole CP");
            h.assertTrue(CrossSubstitution.receipt(f.p).reason().equals("insufficient_cp"), "CP preflight refuses explicitly");
            near(h, f.cp(), .1, "preflight neither spends nor refills depleted CP");
        }); f.finish(9);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void level_five_swap_writes_one_shared_eight_tick_fence_blocking_312_313_and_322(GameTestHelper h) {
        Fixture f = new Fixture(h, 5, 22.5, 26.5); f.ready(() -> {
            f.crouch(f.a); h.assertTrue(f.press(), "LV5 native self-swap");
            h.assertTrue(f.shared().getInt("movement") == 8 && f.own().getInt("cast") == 8, "both physical fences start at eight");
            f.stand(f.b); double cp = f.cp(); f.p.setYRot(0); f.p.setXRot(0);
            h.assertFalse(PhaseSidestep.start(f.p, f.slotWire(1, 1)), "312 cannot chain through self-swap");
            f.aim(f.a); h.assertFalse(SafeLandingProbe.press(f.p, f.slotWire(2, 1)), "313 cannot chain through self-swap");
            h.assertTrue(ReturnMark.press(f.p, f.slotWire(3, 1)), "322 may prepare a free mark"); f.stand(f.b.add(0, 0, -3));
            h.assertFalse(ReturnMark.press(f.p, f.slotWire(3, 2)), "322 cannot start return through self-swap");
            near(h, f.cp(), cp, "all three shared-fence refusals stay free");
        });
        f.post(12, () -> h.assertTrue(f.shared().getInt("movement") == 4 && f.own().getInt("cast") == 4
                && f.own().getInt("cooldown") == 0, "four actual ticks age physical fences once after LV5 economics expire"));
        f.post(16, () -> {
            h.assertTrue(f.shared().getInt("movement") == 0 && f.own().getInt("cast") == 0, "exact eighth native tick expires both physical fences");
            f.p.setYRot(0); f.p.setXRot(0); h.assertTrue(PhaseSidestep.start(f.p, f.slotWire(1, 2)), "312 becomes eligible at the exact eighth tick");
        }); f.finish(17);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void existing_312_movement_blocks_prepared_swap_until_real_eighth_tick(GameTestHelper h) {
        Fixture f = new Fixture(h, 5, 22.5, 26.5); f.ready(() -> {
            f.stand(f.a.add(sidestepDist(f.p), 0, 0)); f.p.setYRot(0); f.p.setXRot(0);
            h.assertTrue(PhaseSidestep.start(f.p, f.slotWire(1, 1)), "actual312 moves onto savedA");
            h.assertTrue(f.p.position().equals(f.a), "native312 exact endpoint is savedA");
            f.crouch(f.a); f.refuseKeepingMarks("existing312 shared movement fence");
        });
        f.post(12, () -> { f.crouch(f.a); f.refuseKeepingMarks("four ticks do not bypass existing312 shared fence"); });
        f.post(16, () -> { f.crouch(f.a); Snapshot before = f.snapshot(); h.assertTrue(f.press(), "self-swap at actual eighth boundary after312"); f.success(before, f.b); });
        f.finish(17);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void existing_313_movement_blocks_prepared_swap_until_real_eighth_tick(GameTestHelper h) {
        Fixture f = new Fixture(h, 5, 22.5, 26.5); f.ready(() -> {
            f.aim(f.a); h.assertTrue(SafeLandingProbe.press(f.p, f.slotWire(2, 1))
                    && SafeLandingProbe.press(f.p, f.slotWire(2, 2)), "actual313 confirms onto native floor near savedA");
            f.crouch(f.p.position()); f.refuseKeepingMarks("existing313 shared movement fence");
        });
        f.post(16, () -> { f.crouch(f.p.position()); Snapshot before = f.snapshot(); h.assertTrue(f.press(), "self-swap at actual eighth boundary after313"); f.success(before, f.b); });
        f.finish(17);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void existing_322_native_return_blocks_prepared_swap_until_real_eighth_tick(GameTestHelper h) {
        Fixture f = new Fixture(h, 5, 22.5, 26.5); f.ready(() -> {
            f.stand(f.a); h.assertTrue(ReturnMark.press(f.p, f.slotWire(3, 1)), "actual322 captures sourceA");
            f.stand(f.a.add(0, 0, -3)); h.assertTrue(ReturnMark.press(f.p, f.slotWire(3, 2)), "actual322 starts four-tick return warning");
        });
        f.post(12, () -> {
            h.assertTrue(ReturnMark.receipt(f.p).paid() && ReturnMark.receipt(f.p).moved() && f.p.position().equals(f.a), "actual322 transport commits to savedA");
            f.crouch(f.a); f.refuseKeepingMarks("existing322 shared movement fence");
        });
        f.post(20, () -> { f.crouch(f.a); Snapshot before = f.snapshot(); h.assertTrue(f.press(), "self-swap at eighth boundary after actual322 commit"); f.success(before, f.b); });
        f.finish(21);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void late_native_collision_query_flight_or_nophysics_mutation_is_terminal_without_own_payment(GameTestHelper h) { lateQuery(h, false); }
    private static void lateQuery(GameTestHelper h, boolean noPhysics) {
        Fixture f = new Fixture(h); f.ready(() -> {
            f.crouch(f.a); CallbackHusk tangent = new CallbackHusk(h.getLevel());
            tangent.setNoAi(true); tangent.setNoGravity(true); tangent.setSilent(true); tangent.setPos(f.a.add(-f.p.getBbWidth() - 1e-8, 0, 0));
            h.assertTrue(h.getLevel().addFreshEntity(tangent), "declared native callback blocker admitted"); f.entities.add(tangent);
            h.assertFalse(tangent.getBoundingBox().intersects(f.p.getBoundingBox()), "declared tangent native body is a query candidate without source overlap");
            Snapshot before = f.snapshot();
            f.onTeleport(event -> {
                tangent.queryCount = 0; tangent.queryMutation = () -> { if (noPhysics) f.p.noPhysics = true; else f.p.getAbilities().flying = true; };
            });
            f.drain(); h.assertFalse(f.press(), "late native entity predicate changes unsupported motion state");
            h.assertTrue(tangent.queryCount >= 2 && tangent.queryMutation == null, "declared mutation runs in revalidation after post-event source preflight");
            h.assertTrue(noPhysics ? f.p.noPhysics : f.p.getAbilities().flying, "foreign callback flag stays set without rollback");
            f.same(before, "late native query mutation"); f.freeTerminal("source_motion_or_pose_unsupported");
            h.assertTrue(f.positions().isEmpty(), "late unsupported state never reaches actual transport");
        }); h.runAfterDelay(9, () -> { f.verify(); f.close(); if (!noPhysics) lateQuery(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void native_cross_point_guard_refuses_unloaded_source_without_loading_or_metadata_changes(GameTestHelper h) {
        Vec3 remote = new Vec3(25000000.5, 90, 25000000.5); ChunkPos chunk = new ChunkPos(BlockPos.containing(remote));
        var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "CSWUnloaded"), false);
        var p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection); NetworkRegistry.configureMockConnection(connection); p.connection = new ServerGamePacketListenerImpl(server, connection, p, cookie);
        try {
            // Guard-only fixture is deliberately not added: native setPos would load a chunk for an added entity.
            h.assertTrue(!p.isAddedToLevel() && h.getLevel().getEntity(p.getId()) == null
                    && h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) == null, "remote native guard source starts unloaded");
            p.setOnGround(true); p.setPose(Pose.STANDING); p.setShiftKeyDown(false); p.setDeltaMovement(Vec3.ZERO); p.setPos(remote); p.connection.resetPosition();
            CompoundTag before = p.getPersistentData().copy(); var assessment = SafeLandingProbeWorld.crossPoint(p);
            h.assertTrue(!assessment.valid() && assessment.reason().equals("unloaded_chunk"), "native32m point entry fails closed on unloaded source");
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) == null && p.getPersistentData().equals(before)
                    && p.position().equals(remote), "read-only capture neither loads chunks nor repairs source or metadata"); h.succeed();
        } finally { p.discard(); channel.finishAndReleaseAll(); }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=550)
    public static void repeated_preflight_refusal_does_not_renew_original_four_hundred_tick_lifetime(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> { f.crouch(f.source); f.refuseKeepingMarks("first proximity refusal"); });
        f.post(200, () -> { f.crouch(f.source); f.refuseKeepingMarks("later proximity refusal"); h.assertValueEqual(CrossSubstitution.remainingTicks(f.p), CrossSubstitutionRules.PREPARATION_TICKS - 200, "TTL stays anchored to first native mark"); });
        f.post(CrossSubstitutionRules.PREPARATION_TICKS - 1, () -> h.assertTrue(CrossSubstitution.preparedPoints(f.p) == 2 && CrossSubstitution.remainingTicks(f.p) == 1, "pair survives through its final native tick"));
        f.post(CrossSubstitutionRules.PREPARATION_TICKS, () -> { h.assertFalse(CrossSubstitution.marked(f.p), "pair expires exactly at the original first-mark deadline"); h.assertValueEqual(f.needles(), 3, "expiry consumes no stock"); near(h, AbilityStorage.get(f.p).exp(ID), 0, "refusal and expiry cannot farm mastery"); });
        f.finish(CrossSubstitutionRules.PREPARATION_TICKS + 1);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void preset_change_and_native_logout_reload_cannot_restore_free_prepared_points(GameTestHelper h) { lifecycle(h, false); }
    private static void lifecycle(GameTestHelper h, boolean logout) {
        Fixture f = new Fixture(h); f.ready(() -> {
            f.budget().putDouble("cp", 37); CompoundTag own = f.own();
            if (logout) {
                AbilityStorage.save(f.p); CompoundTag saved = f.p.saveWithoutId(new CompoundTag());
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.p));
                f.p.load(saved); AbilityStorage.remove(f.p); AbilityStorage.get(f.p); f.stand(f.source); f.wire(f.inputs + 1);
                CrossSubstitution.tick(f.p);
            } else {
                var state = AbilityStorage.get(f.p); h.assertTrue(state.presets.edit(0, 0, DisplacedProjection.ID, state::learned), "real mapped preset edit");
            }
            near(h, f.cp(), 37, "lifecycle cannot refill depleted CP"); h.assertTrue(f.own().equals(own), "lifecycle cannot erase current physical fence");
            // A same-tick manual tick is deliberately inert. Observe the next genuine native tick.
            f.post(1, () -> {
                h.assertFalse(CrossSubstitution.marked(f.p) || CrossSubstitution.active(f.p), "next native tick retires changed lifecycle preparation");
                if (!logout) { var state = AbilityStorage.get(f.p); h.assertTrue(state.presets.edit(0, 0, ID, state::learned), "restore mapping without recreating marks"); }
                double cp = f.cp(); f.crouch(f.a); h.assertFalse(f.press(), "new crouch after lifecycle cannot resurrect saved endpoints");
                near(h, f.cp(), cp, "lifecycle retry remains free after ordinary native recovery");
            });
        }); h.runAfterDelay(10, () -> { f.verify(); f.close(); if (!logout) lifecycle(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void declared_native_transport_fault_before_or_after_move_keeps_paid_nonreplayable_history(GameTestHelper h) { transportFault(h, false); }
    private static void transportFault(GameTestHelper h, boolean after) {
        Fixture f = new Fixture(h); f.p.connection = new ServerGamePacketListenerImpl(f.p.server, f.connection, f.p, f.cookie) {
            @Override public void teleport(double x, double y, double z, float yaw, float pitch, Set<RelativeMovement> relative) {
                if (after) super.teleport(x, y, z, yaw, pitch, relative);
                throw new IllegalStateException("Declared NEW321 fixture transport failure " + (after ? "after" : "before") + " native move");
            }
        };
        f.wire(1); f.ready(() -> {
            f.crouch(f.a); double cp = f.cp(); f.drain();
            h.assertFalse(f.press(), "declared unknown native transport completion reports refusal"); var receipt = CrossSubstitution.receipt(f.p);
            h.assertTrue(receipt.paid() && receipt.swapAdmitted() && receipt.selfSwap() && receipt.moved() == after,
                    "paid history retains only observed native movement fact");
            near(h, f.cp(), cp - 3, "unknown completion never refunds whole committed CP");
            h.assertTrue(f.p.position().equals(after ? f.b : f.a) && f.positions().size() == (after ? 1 : 0), "fault position and packet correspond to actual native transport");
            h.assertFalse(CrossSubstitution.marked(f.p) || CrossSubstitution.active(f.p), "unknown completion is terminal");
            Snapshot paid = f.snapshot(); h.assertFalse(CrossSubstitution.press(f.p, f.wire(f.inputs)), "paid original input cannot replay"); f.same(paid, "paid unknown duplicate");
            h.assertTrue(f.shared().getInt("movement") == 8 && f.own().getInt("cast") == 8, "unknown completion retains both physical fences");
        }); h.runAfterDelay(9, () -> { f.verify(); f.close(); if (!after) transportFault(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=85)
    public static void actual_native_injury_fire_effects_and_fall_damage_receive_no_swap_benefit(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {});
        f.post(65, () -> {
            h.assertTrue(f.p.hurt(f.p.damageSources().generic(), 4), "native login protection has expired and real injury succeeds");
            f.crouch(f.a); f.p.setRemainingFireTicks(80); f.p.addEffect(new MobEffectInstance(MobEffects.POISON, 120));
            f.p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140)); Body before = Body.of(f.p); Snapshot economic = f.snapshot();
            h.assertTrue(f.press(), "injured and burning grounded caster swaps"); f.success(economic, f.b);
            h.assertTrue(Body.of(f.p).equals(before), "same-tick move preserves exact hurt, iframe, fire, effects, gravity, HP and shield");
            BigDecimal hp = new BigDecimal(HighPowerCombat.healthText(f.p)); f.p.setOnGround(false); f.p.fallDistance = 8;
            // Native fall bypasses the ordinary hurt cooldown; swap must not intercept it.
            h.assertTrue(f.p.causeFallDamage(8, 1, f.p.damageSources().fall()), "actual native fall path remains admitted after swap");
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(f.p)).compareTo(hp) < 0 && !f.p.isNoGravity()
                    && !f.p.getAbilities().flying, "swap grants no fall immunity, healing or flight setting");
        }); f.finish(66);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=85)
    public static void actual_native_lethal_callback_consumes_marks_without_payment_or_rescue(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {});
        f.post(65, () -> {
            f.crouch(f.a); double cp = f.cp();
            f.onTeleport(event -> {
                float lethal = new BigDecimal(HighPowerCombat.healthText(f.p)).multiply(BigDecimal.valueOf(2)).floatValue();
                h.assertTrue(Float.isFinite(lethal) && lethal < 1e20f, "bounded native lethal fixture amount");
                h.assertTrue(f.p.hurt(f.p.damageSources().fellOutOfWorld(), lethal), "real lethal damage inside teleport callback succeeds");
            });
            f.drain(); h.assertFalse(f.press(), "death in callback rejects captured operation");
            near(h, f.cp(), cp, "death callback cannot commit self-swap CP");
            h.assertTrue(f.p.isDeadOrDying() && !CrossSubstitution.marked(f.p) && !CrossSubstitution.active(f.p)
                    && f.positions().isEmpty(), "death consumes admitted marks and never triggers an automatic rescue transport");
        }); f.finish(66);
    }

    private record Snapshot(double cp, long deadline, int cooldown, double mastery, double levelXp,
                            BigDecimal q, String hp, String shield, double overload, int needles) {}
    private record Body(Vec3 motion, float yaw, float pitch, float fall, boolean ground, boolean noGravity,
                        boolean flying, int hurt, int invulnerable, int fire, String hp, String shield,
                        Map<Object, String> effects) {
        static Body of(ServerPlayer p) {
            Map<Object, String> effects = new HashMap<>();
            p.getActiveEffectsMap().forEach((key, effect) -> effects.put(key, effect.getAmplifier() + ":" + effect.getDuration() + ":" + effect.isAmbient() + ":" + effect.isVisible()));
            return new Body(p.getDeltaMovement(), p.getYRot(), p.getXRot(), p.fallDistance, p.onGround(), p.isNoGravity(),
                    p.getAbilities().flying, p.hurtTime, p.invulnerableTime, p.getRemainingFireTicks(),
                    HighPowerCombat.healthText(p), HighPowerCombat.shieldText(p), Map.copyOf(effects));
        }
    }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-7, why + ": " + actual + " != " + expected); }
    private static CompoundTag shared(int preview, int movement, int cooldown, int mastery) {
        CompoundTag tag = new CompoundTag(); tag.putInt("version", 1); tag.putInt("preview", preview); tag.putInt("movement", movement);
        tag.putInt("cooldown", cooldown); tag.putInt("mastery", mastery); return tag;
    }
    /** Unenrolled obstacle callback leaves the caster's exact vanilla ServerPlayer class intact. */
    public static final class CallbackHusk extends Husk {
        Runnable queryMutation; int queryCount;
        CallbackHusk(net.minecraft.world.level.Level level) { super(EntityType.HUSK, level); }
        @Override public boolean canBeCollidedWith() {
            if (queryMutation != null && ++queryCount == 2) {
                Runnable action = queryMutation; queryMutation = null; action.run();
            }
            return super.canBeCollidedWith();
        }
    }
    public static final class TeleportHook {
        final ServerPlayer owner; final Consumer<EntityTeleportEvent> action; int calls;
        TeleportHook(ServerPlayer owner, Consumer<EntityTeleportEvent> action) { this.owner = owner; this.action = action; }
        @SubscribeEvent public void teleport(EntityTeleportEvent event) { if (event.getEntity() == owner) { calls++; action.accept(event); } }
    }
    public static final class SpawnHook {
        final Fixture f; SpawnHook(Fixture f) { this.f = f; }
        @SubscribeEvent public void join(EntityJoinLevelEvent event) { if (event.getEntity() instanceof DisplacedNeedleEntity needle && needle.getOwner() == f.p) { f.spawns++; f.entities.add(needle); } }
    }
    public static final class PostCheck {
        final Fixture f; final int tick; final Runnable action; boolean ran;
        PostCheck(Fixture f, int delay, Runnable action) { this.f = f; tick = f.p.server.getTickCount() + delay; this.action = action; }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(ServerTickEvent.Post event) {
            if (ran || event.getServer() != f.p.server || event.getServer().getTickCount() != tick) return; ran = true;
            try { action.run(); } catch (Throwable failure) { if (f.failure == null) f.failure = failure; }
        }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final ServerPlayer p; final Connection connection; final CommonListenerCookie cookie;
        final EmbeddedChannel channel; final Vec3 source; final Vec3 pointA; Vec3 pointB, a, b;
        final List<Entity> entities = new ArrayList<>(); final List<Object> hooks = new ArrayList<>(); final List<PostCheck> checks = new ArrayList<>();
        final Map<BlockPos, BlockState> changed = new LinkedHashMap<>(); final Set<ChunkPos> held = new LinkedHashSet<>(); final UUID ticket = UUID.randomUUID();
        long inputs; int spawns; boolean closed; Throwable failure;
        Fixture(GameTestHelper h) { this(h, 3, 22.5, 26.5); }
        Fixture(GameTestHelper h, int level, double ax, double bx) {
            this.h = h; source = h.absoluteVec(new Vec3(24.5, 5, 8.5)); pointA = h.absoluteVec(new Vec3(ax, 5, 14.5)); pointB = h.absoluteVec(new Vec3(bx, 5, 14.5));
            for (int x = 6; x <= 43; x++) for (int z = 4; z <= 18; z++) block(h.absolutePos(new BlockPos(x, 4, z)), Blocks.STONE.defaultBlockState());
            var server = h.getLevel().getServer(); cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "CSW" + UUID.randomUUID().toString().substring(0, 9)), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie);
            for (var packet : positions()) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()));
            p.setGameMode(GameType.SURVIVAL); p.getAbilities().invulnerable = false; p.getAbilities().instabuild = false; p.setNoGravity(false); stand(source);
            p.move(MoverType.SELF, new Vec3(0, -.0784, 0)); h.assertTrue(p.onGround() && p.position().equals(source), "real downward floor collision establishes native fixture ground");
            h.assertTrue(HighPowerCombat.enroll(p, level), "declared native high-power enrollment: " + HighPowerCombat.unsupportedReason(p)); var state = AbilityStorage.get(p);
            state.selectCategory(HighPowerCategories.TELEPORTER); state.setLevel(level);
            for (String skill : List.of(SafeLandingProbe.ID, PhaseSidestep.ID, DisplacedProjection.ID, ReturnMark.ID, ID)) state.learn(skill);
            state.activated = true; state.overloadFine = true;
            h.assertTrue(state.presets.edit(0, 0, ID, state::learned) && state.presets.edit(0, 1, PhaseSidestep.ID, state::learned)
                    && state.presets.edit(0, 2, SafeLandingProbe.ID, state::learned) && state.presets.edit(0, 3, ReturnMark.ID, state::learned), "real learned mapped skills");
            HighPowerResources.initializeBudget(p); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), 3));
            hook(new SpawnHook(this)); hook(new HighPowerPhaseSidestepRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5));
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); } public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        void stand(Vec3 at) { p.setShiftKeyDown(false); p.setPose(Pose.STANDING); place(at); }
        void crouch(Vec3 at) { p.setShiftKeyDown(true); p.setPose(Pose.CROUCHING); place(at); }
        void place(Vec3 at) { p.setPos(at); p.fallDistance = 0; p.setOnGround(true); p.setDeltaMovement(0, -.0784, 0); p.connection.resetPosition(); }
        void aim(Vec3 at) { Vec3 d = at.subtract(p.getEyePosition()); p.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z))); p.setXRot((float)-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x*d.x+d.z*d.z)))); }
        String slotWire(int slot, long input) { return new HighPowerInput.Input(slot, input, p.getId(), LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p))).wire(); }
        String wire(long input) { return slotWire(0, input); }
        boolean press() { return AcademyGameplay.requestFromClient(p, new AcademyNetwork.Request("hp_cross_substitution_start", wire(++inputs))); }
        net.minecraft.world.phys.BlockHitResult ray() {
            return h.getLevel().clip(new ClipContext(p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(32)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        }
        String rayDescription(net.minecraft.world.phys.BlockHitResult hit) {
            return "source=" + p.position() + ", eye=" + p.getEyePosition() + ", look=" + p.getLookAngle()
                    + ", end=" + p.getEyePosition().add(p.getLookAngle().scale(32)) + ", hit=" + hit.getType()
                    + "/" + hit.getDirection() + "/" + hit.getLocation() + ", yaw=" + p.getYRot() + ", pitch=" + p.getXRot();
        }
        Vec3 surface() {
            var hit = ray();
            h.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP,
                    "independent native ray reaches actual first-UP floor: " + rayDescription(hit)); return hit.getLocation();
        }
        void markA() { aim(pointA); a = surface(); h.assertTrue(press(), "genuine authenticated first floor mark: " + CrossSubstitution.receipt(p)); }
        void ready(Runnable action) {
            markA(); post(4, () -> { aim(pointB); b = surface(); h.assertTrue(press(), "genuine authenticated second floor mark: " + CrossSubstitution.receipt(p));
                h.assertTrue(CrossSubstitution.preparedPoints(p) == 2 && CrossSubstitution.remainingTicks(p) == CrossSubstitutionRules.PREPARATION_TICKS - 4, "second point retains original first-mark lifetime"); }); post(8, action);
        }
        void block(BlockPos pos, BlockState state) { h.assertTrue(h.getBounds().contains(Vec3.atCenterOf(pos)), "declared fixture block stays inside isolated native template"); ChunkPos chunk = new ChunkPos(pos); if (held.add(chunk)) { h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticket); h.getLevel().getChunk(chunk.x, chunk.z); }
            changed.putIfAbsent(pos, h.getLevel().getBlockState(pos)); h.getLevel().setBlockAndUpdate(pos, state); }
        Zombie mob(Vec3 at) { Zombie mob = EntityType.HUSK.create(h.getLevel()); mob.setNoAi(true); mob.setNoGravity(true); mob.setSilent(true); mob.setPos(at); h.assertTrue(h.getLevel().addFreshEntity(mob), "native blocking entity admitted"); entities.add(mob); return mob; }
        double cp() { return HighPowerResources.cp(p); }
        int needles() { int count = 0; for (int i = 0; i < p.getInventory().getContainerSize(); i++) { ItemStack stack = p.getInventory().getItem(i); if (stack.is(AcademyCraft.NEEDLE.get())) count += stack.getCount(); } return count; }
        CompoundTag budget() { return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY); }
        CompoundTag own() { return p.getPersistentData().getCompound(OWN).copy(); }
        CompoundTag shared() { return p.getPersistentData().getCompound(SHARED).copy(); }
        Snapshot snapshot() { var s = AbilityStorage.get(p); return new Snapshot(cp(), budget().getLong("next_" + ID), s.cooldowns.getOrDefault(ID, 0), s.exp(ID), s.levelExperience,
                HighPowerGrowth.values(p).position(), HighPowerCombat.healthText(p), HighPowerCombat.shieldText(p), s.overload, needles()); }
        void same(Snapshot before, String why) { h.assertTrue(snapshot().equals(before), why + " preserves CP, deadline, cooldown, mastery, native XP, growth, HP, shield, overload and stock"); }
        void success(Snapshot before, Vec3 destination) {
            var receipt = CrossSubstitution.receipt(p); h.assertTrue(receipt != null && receipt.paid() && receipt.selfSwap() && receipt.swapAdmitted() && receipt.moved(), "one confirmed paid self-swap receipt: " + receipt);
            h.assertTrue(receipt.paidNeedles() == 0 && receipt.published() == 0 && receipt.contacts() == 0 && receipt.hurts() == 0, "self-swap carries no needle payment, publication or combat authority");
            h.assertTrue(p.position().equals(destination) && receipt.arrival().equals(destination), "native feet equal exact saved destination with no emitter-height offset");
            near(h, cp(), before.cp - CrossSubstitutionRules.cpCost(before.q), "one whole native cost"); var now = snapshot();
            h.assertTrue(now.mastery == before.mastery && now.levelXp == before.levelXp && now.q.compareTo(before.q) == 0 && now.hp.equals(before.hp)
                    && now.shield.equals(before.shield) && now.overload == before.overload && now.needles == before.needles, "successful movement grants no XP, growth, healing, shield or ammunition change");
            h.assertFalse(CrossSubstitution.marked(p) || CrossSubstitution.active(p), "successful operation consumes both prepared points");
        }
        void refuseKeepingMarks(String why) {
            Snapshot before = snapshot(); int points = CrossSubstitution.preparedPoints(p), ttl = CrossSubstitution.remainingTicks(p); Vec3 source = p.position();
            h.assertFalse(press(), why + ": " + CrossSubstitution.receipt(p)); same(before, why);
            h.assertTrue(CrossSubstitution.preparedPoints(p) == points && CrossSubstitution.remainingTicks(p) == ttl && p.position().equals(source), why + " retains same marks, original TTL and source");
            var receipt = CrossSubstitution.receipt(p); h.assertTrue(!receipt.paid() && !receipt.swapAdmitted() && receipt.selfSwap(), "preflight never crosses operation admission: " + receipt);
        }
        void freeTerminal(String reason) { var r = CrossSubstitution.receipt(p); h.assertTrue(r != null && r.selfSwap() && r.swapAdmitted() && !r.paid() && !r.moved()
                    && (reason == null ? !r.reason().isEmpty() : reason.equals(r.reason())), "free admitted terminal receipt: " + r);
            h.assertFalse(CrossSubstitution.marked(p) || CrossSubstitution.active(p), "admitted refusal cannot restore points"); }
        TeleportHook onTeleport(Consumer<EntityTeleportEvent> action) { TeleportHook hook = new TeleportHook(p, action); hook(hook); return hook; }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void post(int delay, Runnable action) { PostCheck check = new PostCheck(this, delay, action); checks.add(check); hook(check); }
        void verify() { if (failure instanceof RuntimeException problem) throw problem; if (failure instanceof Error problem) throw problem; if (failure != null) throw new AssertionError(failure);
            h.assertTrue(checks.stream().allMatch(check -> check.ran), "all real server Post observations executed"); }
        void finish(int delay) { h.runAfterDelay(delay, () -> { verify(); h.succeed(); }); }
        List<Object> drain() { List<Object> packets = new ArrayList<>(); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet;
            while ((packet = channel.readOutbound()) != null) { packets.add(packet); ReferenceCountUtil.release(packet); } return packets; }
        List<ClientboundPlayerPositionPacket> positions() { List<ClientboundPlayerPositionPacket> packets = new ArrayList<>(); for (Object packet : drain()) if (packet instanceof ClientboundPlayerPositionPacket position) packets.add(position); return packets; }
        @Override public void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); CrossSubstitution.clear(p); PhaseSidestep.remove(p); SafeLandingProbe.remove(p); ReturnMark.remove(p);
            HighPowerBracerSession.remove(p); AbilityStorage.remove(p); p.server.getPlayerList().remove(p); entities.forEach(Entity::discard);
            changed.forEach((pos, state) -> h.getLevel().setBlockAndUpdate(pos, state)); held.forEach(chunk -> h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticket)); channel.finishAndReleaseAll(); }
    }
}

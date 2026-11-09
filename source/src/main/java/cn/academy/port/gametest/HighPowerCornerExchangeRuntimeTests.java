package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CornerExchangeRules;
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
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
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
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * NEW333 native server acceptance using exact ServerPlayer instances, authenticated
 * release edges, real entity ticks, native teleport callbacks and connection packets.
 * Enrollment, learned flags, finite floors and deliberate source placement are declared
 * fixtures. The clock is the actual server clock; repeated controller calls cannot age it.
 * This suite establishes server/transport semantics, not keyboard or rendered-client QA.
 */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerCornerExchangeRuntimeTests {
    private static final String TEMPLATE = "highpower_corner_exchange_empty", BATCH = "highpower_corner_exchange";
    private static final String ID = CornerExchange.ID, OWN = "academy:corner_exchange_fences", SHARED = "academy:safe_landing_probe_fences";
    private static final TicketType<UUID> TICKET = TicketType.create("academy_corner_exchange_fixture", UUID::compareTo);
    private HighPowerCornerExchangeRuntimeTests() {}

    /** A separate bounds-correct arena keeps long native corridors clear of neighboring tests. */
    @SubscribeEvent public static void install(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(); ListTag size = new ListTag();
        size.add(IntTag.valueOf(48)); size.add(IntTag.valueOf(8)); size.add(IntTag.valueOf(32)); tag.put("size", size);
        ListTag blocks = new ListTag(); CompoundTag first = new CompoundTag(); ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); first.put("pos", pos); first.putInt("state", 0); blocks.add(first);
        tag.put("blocks", blocks); tag.put("entities", new ListTag()); ListTag palette = new ListTag(); CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air"); palette.add(air); tag.put("palette", palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_highpower", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void authenticated_release_captures_free_exact_floor_and_duplicate_edges_are_inert(GameTestHelper h) {
        Fixture f = new Fixture(h); Snapshot before = f.snapshot(); CompoundTag shared = f.shared(); f.aim(f.pointA); Vec3 expected = f.surface();
        h.assertTrue(f.press(), "authenticated mapped press starts the gesture");
        h.assertTrue(CornerExchange.preparedPoints(f.p) == 0 && f.positions().isEmpty(), "press alone never records a floor or moves");
        for (int i = 0; i < 128; i++) CornerExchange.tick(f.p);
        h.assertTrue(CornerExchange.heldTicks(f.p) == 0 && CornerExchange.preparedPoints(f.p) == 0, "same-tick calls never recognize a hold or tap");
        h.assertFalse(CornerExchange.release(f.p, f.wire(f.inputs + 1)), "different physical input cannot release authenticated gesture");
        h.assertTrue(f.release(), "matching release captures first native first-UP floor");
        var r = CornerExchange.receipt(f.p);
        h.assertTrue(CornerExchange.preparedPoints(f.p) == 1 && CornerExchange.remainingTicks(f.p) == CornerExchangeRules.PREPARATION_TICKS
                && r != null && !r.paid() && !r.moved() && r.destination().equals(expected), "first release stores a free point for the full mark lifetime");
        h.assertFalse(CornerExchange.press(f.p, f.wire(f.inputs)), "same physical press cannot replay");
        CornerExchange.release(f.p, f.wire(f.inputs)); f.same(before, "mark and repeated edges");
        h.assertTrue(f.shared().equals(shared) && f.positions().isEmpty(), "capture does not acquire a shared movement fence"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void held_key_does_nothing_until_six_genuine_ticks_then_clears_without_release_tap(GameTestHelper h) {
        Fixture f = new Fixture(h); f.aim(f.pointA); Snapshot before = f.snapshot(); h.assertTrue(f.press(), "empty hold begins");
        for (int tick = 1; tick <= 5; tick++) { int expected = tick; f.post(tick, () -> {
            for (int i = 0; i < 64; i++) CornerExchange.tick(f.p);
            h.assertTrue(CornerExchange.heldTicks(f.p) == expected && CornerExchange.preparedPoints(f.p) == 0
                    && f.positions().isEmpty(), "genuine hold tick" + expected + " never marks or moves early"); f.same(before, "unrecognized hold");
        }); }
        f.post(6, () -> { h.assertFalse(CornerExchange.marked(f.p), "sixth genuine tick clears empty context freely");
            f.release(); h.assertFalse(CornerExchange.marked(f.p), "release of recognized hold cannot become a tap"); f.same(before, "six-tick empty clear"); }); f.finish(7);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void crouch_chord_cannot_prepare_either_mark_after_shift_is_released(GameTestHelper h) {
        Fixture f = new Fixture(h); f.crouch(f.source); f.aim(f.pointA); Snapshot before = f.snapshot(); h.assertTrue(f.press(), "crouch chord starts without early action");
        f.stand(f.source); f.aim(f.pointA); h.assertFalse(f.release(), "latched crouch cannot captureA after standing on release"); f.same(before, "crouch first-mark refusal");
        h.assertValueEqual(CornerExchange.preparedPoints(f.p), 0, "no first point from reverse gesture");
        f.post(4, () -> { f.aim(f.pointA); h.assertTrue(f.tap(), "standing tap prepares legitimateA"); });
        f.post(8, () -> { f.crouch(f.source); f.aim(f.pointB); h.assertTrue(f.press(), "crouch chord with one legitimate point"); f.stand(f.source); f.aim(f.pointB);
            int ttl = CornerExchange.remainingTicks(f.p); h.assertFalse(f.release(), "latched crouch cannot captureB after standing on release");
            h.assertTrue(CornerExchange.preparedPoints(f.p) == 1 && CornerExchange.remainingTicks(f.p) == ttl, "first point keeps exact original lifetime"); f.same(before, "crouch second-mark refusal"); }); f.finish(9);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void held_clear_removes_pair_before_source_and_economic_gates(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            CompoundTag fence = own(4, 40); f.p.getPersistentData().put(OWN, fence); f.p.setOnGround(false); f.p.fallDistance = 8;
            f.p.setDeltaMovement(0, -.3, 0); h.assertTrue(f.press(), "long clear starts despite unsafe source and own cooldown");
            f.post(5, () -> h.assertTrue(CornerExchange.preparedPoints(f.p) == 2, "pair survives until clear is recognized"));
            f.post(6, () -> { h.assertFalse(CornerExchange.marked(f.p), "six genuine held ticks clear the pair"); f.release();
                h.assertTrue(f.own().getInt("cooldown") == 34 && f.shared().getInt("movement") == 0, "clear ages but never resets cooldown or buys movement");
                near(h, AbilityStorage.get(f.p).exp(ID), 0, "held clear cannot farm mastery"); });
        }); f.finish(15);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void warning_waits_three_real_server_ticks_and_same_tick_calls_never_move(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            f.stand(f.a); Snapshot before = f.snapshot(); int issued = f.p.server.getTickCount(); TeleportHook hook = f.onTeleport(event -> {});
            h.assertTrue(f.tap(), "third release admits three-tick warning");
            for (int i = 0; i < 128; i++) CornerExchange.tick(f.p);
            h.assertTrue(CornerExchange.warningTicks(f.p) == 3 && f.p.position().equals(f.a), "same-tick calls never consume warning");
            for (int tick = 1; tick <= 2; tick++) { int left = 3 - tick; f.post(tick, () -> {
                for (int i = 0; i < 64; i++) CornerExchange.tick(f.p);
                h.assertTrue(CornerExchange.warningTicks(f.p) == left && f.p.position().equals(f.a) && hook.calls == 0, "warning remains unpaid until third genuine tick"); f.same(before, "warning");
            }); }
            f.post(3, () -> { f.success(before, f.b); h.assertTrue(hook.calls == 1 && hook.tick - issued == 3, "exactly one native event at genuine tick3");
                Snapshot paid = f.snapshot(); var receipt = CornerExchange.receipt(f.p); CornerExchange.tick(f.p); f.release();
                h.assertFalse(CornerExchange.press(f.p, f.wire(f.inputs)), "paid input cannot replay"); f.same(paid, "same-tick postcommit replay");
                h.assertTrue(CornerExchange.receipt(f.p).equals(receipt), "terminal receipt is historical"); });
        }); f.finish(12);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void pending_five_tick_tap_survives_due_mark_heartbeat_without_early_action(GameTestHelper h) {
        Fixture f = new Fixture(h, 5, 22.5, 26.5); f.ready(() -> {});
        f.post(13, () -> { f.stand(f.a); h.assertTrue(f.press(), "gesture begins just before stored-pair heartbeat is due"); f.drain(); });
        f.post(14, () -> { h.assertTrue(CornerExchange.preparedPoints(f.p) == 2 && CornerExchange.heldTicks(f.p) == 1 && !CornerExchange.active(f.p), "pending press remains gesture-only on heartbeat boundary");
            for (Object packet : f.drain()) if (packet instanceof ClientboundCustomPayloadPacket payload && payload.payload() instanceof CornerExchange.Visual visual)
                h.assertFalse(visual.phase() == CornerExchange.MARKED && visual.input() == f.inputs, "periodic MARKED cannot acknowledge current unreleased gesture"); });
        f.post(18, () -> { h.assertValueEqual(CornerExchange.heldTicks(f.p), 5, "five genuine ticks remain a tap"); h.assertTrue(f.release(), "matching release still starts warning after due heartbeat");
            h.assertTrue(CornerExchange.warningTicks(f.p) == 3 && f.p.position().equals(f.a), "release alone begins full unpaid warning"); });
        f.post(21, () -> h.assertTrue(CornerExchange.receipt(f.p).paid() && f.p.position().equals(f.b), "surviving five-tick tap commits once after three more genuine ticks")); f.finish(22);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void standing_keeps_and_crouching_reverses_current_commit_facing_with_exact_native_packet(GameTestHelper h) { direction(h, false); }
    private static void direction(GameTestHelper h, boolean reverse) {
        Fixture f = new Fixture(h); f.p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); f.ready(() -> {
            if (reverse) f.crouch(f.b); else f.stand(f.a); Vec3 expected = reverse ? f.a : f.b;
            f.p.setYRot(37); f.p.setXRot(-18); Body[] body = {null}; Snapshot[] before = {null};
            f.onTeleport(event -> { before[0] = f.snapshot(); body[0] = Body.of(f.p);
                h.assertTrue(!CornerExchange.receipt(f.p).paid(), "native teleport event precedes debit"); });
            h.assertTrue(f.tap(), "release starts requested orientation warning");
            f.post(1, () -> { f.p.setYRot(73); f.p.setXRot(-31); }); f.drain();
            f.post(3, () -> {
                f.success(before[0], expected); h.assertTrue(Body.of(f.p).equals(body[0]), "transport preserves native motion, fall, ground, hurt, iframes, fire and effects");
                near(h, Mth.wrapDegrees(f.p.getYRot() - (reverse ? 253 : 73)), 0, "orientation uses current commit yaw plus optional180"); near(h, f.p.getXRot(), -31, "current commit pitch unchanged");
                var packets = f.positions(); h.assertValueEqual(packets.size(), 1, "single native transport packet"); var packet = packets.getFirst();
                h.assertTrue(new Vec3(packet.getX(), packet.getY(), packet.getZ()).equals(expected) && packet.getRelativeArguments().isEmpty(), "absolute XYZ and facing explicitly reset native client XYZ velocity");
                near(h, Mth.wrapDegrees(packet.getYRot() - f.p.getYRot()), 0, "packet carries final yaw"); near(h, packet.getXRot(), -31, "packet carries final pitch");
                Snapshot paid = f.snapshot(); f.p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId())); f.same(paid, "native acknowledgment");
                h.assertTrue(f.positions().isEmpty(), "ack cannot produce a repeated native move");
            });
        }); h.runAfterDelay(12, () -> { f.verify(); f.close(); if (!reverse) direction(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void server_crouch_chord_survives_shift_release_and_uses_latest_callback_facing(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> { f.crouch(f.a); f.p.setYRot(20); f.p.setXRot(-10);
            h.assertTrue(f.press(), "server observes crouch when physical chord begins"); f.stand(f.a);
            h.assertTrue(f.release(), "Shift and key release may arrive with standing pose"); Snapshot[] before = {null};
            f.onTeleport(event -> { before[0] = f.snapshot(); f.p.setYRot(100); f.p.setXRot(27); });
            f.post(3, () -> { f.success(before[0], f.b); h.assertTrue(CornerExchange.receipt(f.p).reversed() && f.p.getPose() == Pose.STANDING, "stored server chord preserves reverse mode with stable release pose");
                near(h, Mth.wrapDegrees(f.p.getYRot() - 280), 0, "reverse adds180 to latest finite callback yaw"); near(h, f.p.getXRot(), 27, "latest callback pitch remains unchanged"); });
        }); f.finish(12);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void nonfinite_callback_facing_refuses_before_native_payment(GameTestHelper h) { invalidFacing(h, false); }
    private static void invalidFacing(GameTestHelper h, boolean pitch) {
        Fixture f = new Fixture(h); f.ready(() -> { f.stand(f.a); double[] cp = {0}; f.onTeleport(event -> { cp[0] = f.cp();
            // Native setters reject nonfinite input. This declared hostile callback instead
            // corrupts the actual private field, retaining the strict native player class.
            malformedFacing(f.p, pitch, pitch ? Float.NaN : Float.POSITIVE_INFINITY);
            h.assertFalse(Float.isFinite(pitch ? f.p.getXRot() : f.p.getYRot()), "declared malformed native orientation actually reaches the controller guard");
        }); h.assertTrue(f.tap(), "warning before invalid facing callback");
            f.post(3, () -> { near(h, f.cp(), cp[0], "nonfinite facing never pays"); h.assertTrue(!CornerExchange.receipt(f.p).paid() && f.positions().isEmpty(), "nonfinite callback facing cannot reach native transport");
                f.p.setYRot(0); f.p.setXRot(0); });
        }); h.runAfterDelay(12, () -> { f.verify(); f.close(); if (!pitch) invalidFacing(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void native_orientation_setters_reject_nonfinite_input_and_keep_valid_exchange_facing(GameTestHelper h) { rejectedSetter(h, false); }
    private static void rejectedSetter(GameTestHelper h, boolean pitch) {
        Fixture f = new Fixture(h); f.ready(() -> { f.stand(f.a); f.p.setYRot(73); f.p.setXRot(-31); Snapshot[] before = {null};
            f.onTeleport(event -> { before[0] = f.snapshot(); if (pitch) f.p.setXRot(Float.NaN); else f.p.setYRot(Float.POSITIVE_INFINITY);
                near(h, f.p.getYRot(), 73, "native setter rejects invalid replacement yaw"); near(h, f.p.getXRot(), -31, "native setter rejects invalid replacement pitch"); });
            h.assertTrue(f.tap(), "warning uses actual finite orientation retained by native setter"); f.post(3, () -> { f.success(before[0], f.b);
                near(h, f.p.getYRot(), 73, "successful transport retains native finite yaw"); near(h, f.p.getXRot(), -31, "successful transport retains native finite pitch"); });
        }); h.runAfterDelay(12, () -> { f.verify(); f.close(); if (!pitch) rejectedSetter(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=45)
    public static void legal_levels_and_all_curve_anchors_have_exact_cost_cooldown_and_no_growth(GameTestHelper h) { economics(h, 3); }
    private static void economics(GameTestHelper h, int level) {
        double[] costs = {8, 4, 2, .5, .05}; int[] cooldowns = {40, 20, 10, 4, 1};
        for (int i = 0; i < 5; i++) { near(h, CornerExchangeRules.cpCost(BigDecimal.valueOf(i + 1)), costs[i], "CP anchor" + (i + 1));
            h.assertValueEqual(CornerExchangeRules.cooldownTicks(BigDecimal.valueOf(i + 1)), cooldowns[i], "cooldown anchor" + (i + 1)); }
        Fixture f = new Fixture(h, level, 22.5, 26.5); f.ready(() -> {
            f.stand(f.a); Snapshot[] before = {null}; f.onTeleport(event -> before[0] = f.snapshot()); h.assertTrue(f.tap(), "legal native warning at LV" + level);
            f.post(3, () -> { f.success(before[0], f.b); int cd = CornerExchangeRules.cooldownTicks(before[0].q);
                h.assertTrue(f.own().getInt("cooldown") == cd && f.shared().getInt("movement") == 8, "separate q-economic and shared physical fences");
                h.assertTrue(AbilityStorage.get(f.p).cooldowns.get(ID) == cd && f.budget().getLong("next_" + ID) == h.getLevel().getGameTime() + cd, "UI and economic deadline agree"); });
        }); h.runAfterDelay(12, () -> { f.verify(); f.close(); if (level < 5) economics(h, level + 1); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void fractional_native_growth_uses_smooth_cost_without_mastery_award(GameTestHelper h) {
        Fixture f = new Fixture(h); AbilityStorage.get(f.p).addExperience(SafeLandingProbe.ID, .01); BigDecimal q = HighPowerGrowth.values(f.p).position();
        h.assertTrue(q.compareTo(BigDecimal.valueOf(3)) > 0 && q.compareTo(BigDecimal.valueOf(4)) < 0, "actual prerequisite XP creates fractional native growth");
        f.ready(() -> { f.stand(f.a); Snapshot[] before = {null}; f.onTeleport(event -> before[0] = f.snapshot()); h.assertTrue(f.tap(), "fractional native warning");
            f.post(3, () -> { f.success(before[0], f.b); double cost = before[0].cp - f.cp(); h.assertTrue(cost > .5 && cost < 2, "actual debit lies between adjacent anchors"); }); }); f.finish(12);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void successful_pair_repeats_both_directions_without_refreshing_original_ttl(GameTestHelper h) {
        Fixture f = new Fixture(h, 5, 22.5, 26.5); f.ready(() -> { f.stand(f.a); h.assertTrue(f.tap(), "first warning"); });
        f.post(11, () -> { h.assertTrue(CornerExchange.receipt(f.p).paid() && f.p.position().equals(f.b) && CornerExchange.remainingTicks(f.p) == CornerExchangeRules.PREPARATION_TICKS - 11, "first success retains original pair lifetime");
            f.stand(f.b); f.refuseKeepingPair("shared movement still fenced immediately after success"); });
        f.post(18, () -> h.assertValueEqual(f.shared().getInt("movement"), 1, "seventh native tick remains physically fenced"));
        f.post(19, () -> { f.stand(f.b); h.assertTrue(f.tap(), "eighth real tick allows same pair in reverse direction"); });
        f.post(22, () -> { h.assertTrue(CornerExchange.receipt(f.p).paid() && f.p.position().equals(f.a) && CornerExchange.preparedPoints(f.p) == 2
                && CornerExchange.remainingTicks(f.p) == CornerExchangeRules.PREPARATION_TICKS - 22, "second success returns to exactA without TTL renewal"); near(h, AbilityStorage.get(f.p).exp(ID), 0, "repeated use has no XP"); }); f.finish(23);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=50)
    public static void neither_near_both_near_and_outside_tolerances_keep_pair_for_new_input(GameTestHelper h) { proximity(h, 0); }
    private static void proximity(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h, 3, 22.5, mode == 1 ? 23.5 : 26.5); f.ready(() -> {
            Vec3 at = switch (mode) { case 0 -> f.source; case 1 -> f.a.add(f.b).scale(.5); case 2 -> f.a.add(-.750001, 0, 0); default -> f.a.add(0, .125001, 0); };
            f.stand(at); f.refuseKeepingPair("invalid source proximity" + mode);
        }); h.runAfterDelay(9, () -> { f.verify(); f.close(); if (mode < 3) proximity(h, mode + 1); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void exact_horizontal_proximity_boundary_is_inclusive(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> { f.stand(f.a.add(-.75, 0, 0)); Snapshot before = f.snapshot(); h.assertTrue(f.tap(), "exact .75 horizontal source admitted");
            f.post(3, () -> f.success(before, f.b)); }); f.finish(12);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void exact_vertical_proximity_boundary_uses_real_raised_floor_support(GameTestHelper h) {
        Fixture f = new Fixture(h, 3, 22.6, 26.5);
        for (int x = 23; x <= 28; x++) for (int z = 6; z <= 16; z++) f.block(h.absolutePos(new BlockPos(x, 5, z)), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2));
        f.pointB = f.pointB.add(0, .125, 0); f.stand(f.source.add(0, .125, 0));
        f.ready(() -> { f.stand(f.a.add(.75, .125, 0)); Snapshot before = f.snapshot(); h.assertTrue(f.tap(), "inclusive .125Y with real raised snow support: " + CornerExchange.receipt(f.p));
            f.post(3, () -> f.success(before, f.b)); }); f.finish(12);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void actual_twenty_four_metre_movement_is_inclusive_and_beyond_has_no_alternate(GameTestHelper h) { range(h, false); }
    private static void range(GameTestHelper h, boolean outside) {
        Fixture f = new Fixture(h, 3, 12.5, 36.25); f.ready(() -> {
            double distance = outside ? 24.000001 : 24; f.stand(new Vec3(f.b.x - distance, f.b.y, f.b.z));
            near(h, f.p.position().distanceTo(f.b), distance, "actual native movement distance");
            if (outside) { f.refuseKeepingPair("actual displacement beyond24 has no shorter fallback"); h.assertTrue(CornerExchange.receipt(f.p).reason().equals("destination_out_of_range"), "explicit actual-distance refusal"); }
            else { Snapshot before = f.snapshot(); h.assertTrue(f.tap(), "exact24m native warning"); f.post(3, () -> f.success(before, f.b)); }
        }); h.runAfterDelay(12, () -> { f.verify(); f.close(); if (!outside) range(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void native_capture_beyond_twelve_is_visible_and_beyond_twenty_four_ray_is_refused(GameTestHelper h) {
        Fixture f = new Fixture(h); f.stand(h.absoluteVec(new Vec3(8.5, 5, 14.5))); f.aim(h.absoluteVec(new Vec3(31.5, 5, 14.5)));
        Vec3 hit = f.surface(); h.assertTrue(f.p.getEyePosition().distanceTo(hit) > 22 && f.p.getEyePosition().distanceTo(hit) < 24, "independent actual ray proves long visible capture");
        Snapshot before = f.snapshot(); h.assertTrue(f.tap() && CornerExchange.preparedPoints(f.p) == 1, "native first point has no accidental12m translation cap"); f.same(before, "long free capture"); f.close();
        Fixture outside = new Fixture(h); outside.stand(h.absoluteVec(new Vec3(8.5, 5, 14.5))); outside.aim(h.absoluteVec(new Vec3(32.6, 5, 14.5)));
        h.assertTrue(outside.ray().getType() == HitResult.Type.MISS, "independent ray cannot reach floor outside24m budget"); before = outside.snapshot();
        h.assertFalse(outside.tap(), "outside ray cannot invent a nearer destination"); outside.same(before, "out-of-range capture"); h.assertValueEqual(CornerExchange.preparedPoints(outside.p), 0, "no invented mark"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=85)
    public static void wall_fluid_entity_and_crouch_only_clearance_refusals_preserve_pair(GameTestHelper h) { blocked(h, 0); }
    private static void blocked(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Vec3 middle = f.a.add(f.b).scale(.5); switch (mode) {
                case 0 -> f.block(BlockPos.containing(middle.add(0, 1, 0)), Blocks.STONE.defaultBlockState());
                case 1 -> f.block(BlockPos.containing(middle), Blocks.WATER.defaultBlockState());
                case 2 -> f.block(BlockPos.containing(f.b), Blocks.WATER.defaultBlockState());
                case 3 -> f.mob(middle); case 4 -> f.mob(f.b);
                case 5 -> f.block(BlockPos.containing(f.b).above(), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
                case 6 -> f.block(BlockPos.containing(middle).above(), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
                default -> f.block(BlockPos.containing(f.b).below(), Blocks.AIR.defaultBlockState());
            }
            f.crouch(f.a); f.refuseKeepingPair("unsafe full standing geometry" + mode);
            if (mode == 5 || mode == 6) h.assertTrue(h.getLevel().noCollision(f.p, f.p.getBoundingBox().move((mode == 5 ? f.b : middle).subtract(f.a))), "native crouching body fits but mandatory standing body does not");
        }); h.runAfterDelay(9, () -> { f.verify(); f.close(); if (mode < 7) blocked(h, mode + 1); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=65)
    public static void walking_pose_mapping_and_live_safety_changes_cancel_warning_without_payment(GameTestHelper h) { warningMutation(h, 0); }
    private static void warningMutation(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h); f.ready(() -> { f.stand(f.a); Snapshot before = f.snapshot(); h.assertTrue(f.tap(), "warning before native change");
            f.post(1, () -> { switch (mode) { case 0 -> f.p.setPos(f.a.add(.01, 0, 0)); case 1 -> { f.p.setPose(Pose.CROUCHING); f.p.setShiftKeyDown(true); }
                case 2 -> f.block(BlockPos.containing(f.b).above(), Blocks.STONE.defaultBlockState()); case 3 -> AbilityStorage.get(f.p).activated = false;
                default -> { var s = AbilityStorage.get(f.p); s.presets.edit(0, 0, DisplacedProjection.ID, s::learned); } } });
            f.post(3, () -> { h.assertFalse(CornerExchange.active(f.p), "changed warning stops"); f.same(before, "cancelled native warning" + mode);
                h.assertTrue(f.positions().isEmpty(), "changed warning has no native packet"); if (mode < 3) h.assertValueEqual(CornerExchange.preparedPoints(f.p), 2, "ordinary attempt failure preserves reusable pair"); });
        }); h.runAfterDelay(12, () -> { f.verify(); f.close(); if (mode < 4) warningMutation(h, mode + 1); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void native_cancel_and_redirect_are_free_terminal_attempts_preserving_pair(GameTestHelper h) { eventRefusal(h, false); }
    private static void eventRefusal(GameTestHelper h, boolean redirect) {
        Fixture f = new Fixture(h); f.ready(() -> { f.stand(f.a); Snapshot[] before = {null}; TeleportHook hook = f.onTeleport(event -> {
            before[0] = f.snapshot(); if (redirect) event.setTargetZ(event.getTargetZ() + .1); else event.setCanceled(true);
        }); h.assertTrue(f.tap(), "warning before actual native callback");
            f.post(3, () -> { f.freeAttempt(redirect ? "event_redirected" : "event_cancelled"); f.same(before[0], "native callback refusal");
                h.assertTrue(hook.calls == 1 && f.p.position().equals(f.a) && f.positions().isEmpty(), "single event and no native move");
                Snapshot free = f.snapshot(); h.assertFalse(CornerExchange.press(f.p, f.wire(f.inputs)), "original input is terminal after callback refusal"); f.release(); f.same(free, "cancelled attempt replay"); });
        }); h.runAfterDelay(12, () -> { f.verify(); f.close(); if (!redirect) eventRefusal(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void cancelled_callback_preserves_foreign_budget_edit_without_refund(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> { f.stand(f.a); double[] cp = {0}; f.onTeleport(event -> { cp[0] = f.cp(); f.budget().putDouble("cp", cp[0] - 1);
            f.p.getPersistentData().putInt("academy:corner_callback", 7); event.setCanceled(true); }); h.assertTrue(f.tap(), "warning before foreign resource edit");
            f.post(3, () -> { f.freeAttempt("event_cancelled"); near(h, f.cp(), cp[0] - 1, "foreign debit survives without own payment/refund"); h.assertValueEqual(f.p.getPersistentData().getInt("academy:corner_callback"), 7, "callback metadata retained"); }); }); f.finish(12);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=160)
    public static void native_callback_source_state_and_world_changes_are_revalidated_before_payment(GameTestHelper h) { callbackMutation(h, 0); }
    private static void callbackMutation(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h); f.ready(() -> { f.stand(f.a); double[] cp = {0}; f.onTeleport(event -> { cp[0] = f.cp(); switch (mode) {
            case 0 -> f.p.setPos(f.a.add(.1, 0, 0)); case 1 -> f.p.setPose(Pose.CROUCHING); case 2 -> f.p.setShiftKeyDown(true);
            case 3 -> f.p.setPose(Pose.SWIMMING); case 4 -> f.p.setDeltaMovement(.1, -.0784, 0); case 5 -> f.p.setOnGround(false);
            case 6 -> f.p.fallDistance = 2; case 7 -> f.p.getAbilities().flying = true; case 8 -> f.p.noPhysics = true;
            case 9 -> f.block(BlockPos.containing(f.b).above(), Blocks.STONE.defaultBlockState());
            case 10 -> f.block(BlockPos.containing(f.a.add(f.b).scale(.5)).above(), Blocks.STONE.defaultBlockState());
            default -> AbilityStorage.get(f.p).activated = false;
        } }); h.assertTrue(f.tap(), "warning before callback mutation" + mode);
            f.post(3, () -> { h.assertTrue(CornerExchange.receipt(f.p) != null && !CornerExchange.receipt(f.p).paid() && !CornerExchange.active(f.p), "callback mutation never commits");
                near(h, f.cp(), cp[0], "callback mutation has no own debit"); h.assertTrue(f.positions().isEmpty() && f.spawns == 0, "callback mutation has no transport or projectile publication"); });
        }); h.runAfterDelay(12, () -> { f.verify(); f.close(); if (mode < 11) callbackMutation(h, mode + 1); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void callback_reentry_and_abort_make_one_free_nonreplayable_attempt(GameTestHelper h) { reentry(h, false); }
    private static void reentry(GameTestHelper h, boolean abort) {
        Fixture f = new Fixture(h); f.ready(() -> { f.stand(f.a); double[] cp = {0}; TeleportHook hook = f.onTeleport(event -> { cp[0] = f.cp();
            if (abort) CornerExchange.abort(f.p, f.wire(f.inputs)); else f.tap(); }); h.assertTrue(f.tap(), "warning before callback reentry");
            f.post(3, () -> { near(h, f.cp(), cp[0], "reentrant/aborted native attempt remains free"); h.assertTrue(hook.calls == 1 && f.positions().isEmpty() && !CornerExchange.active(f.p), "reentry cannot post another event or move");
                h.assertFalse(CornerExchange.press(f.p, f.wire(f.inputs)), "terminal physical input cannot replay"); });
        }); h.runAfterDelay(12, () -> { f.verify(); f.close(); if (!abort) reentry(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void late_native_geometry_callback_cannot_enable_flight_or_noclip_before_debit(GameTestHelper h) { lateQuery(h, false); }
    private static void lateQuery(GameTestHelper h, boolean noPhysics) {
        Fixture f = new Fixture(h); f.ready(() -> { f.stand(f.a); CallbackHusk tangent = new CallbackHusk(h.getLevel()); tangent.setNoAi(true); tangent.setNoGravity(true); tangent.setSilent(true);
            tangent.setPos(f.a.add(-f.p.getBbWidth() - 1e-8, 0, 0)); h.assertTrue(h.getLevel().addFreshEntity(tangent), "native tangent collision callback fixture"); f.entities.add(tangent);
            h.assertFalse(tangent.getBoundingBox().intersects(f.p.getBoundingBox()), "query candidate does not overlap source"); double[] cp = {0};
            f.onTeleport(event -> { cp[0] = f.cp(); tangent.queryCount = 0; tangent.queryMutation = () -> { if (noPhysics) f.p.noPhysics = true; else f.p.getAbilities().flying = true; }; });
            h.assertTrue(f.tap(), "warning reaches native query revalidation"); f.post(3, () -> {
                h.assertTrue(tangent.queryCount >= 2 && tangent.queryMutation == null, "late actual native collision predicate runs");
                near(h, f.cp(), cp[0], "late unsupported source cannot pay"); h.assertTrue(!CornerExchange.receipt(f.p).paid() && f.positions().isEmpty(), "post-geometry source gate prevents native transport");
                h.assertTrue(noPhysics ? f.p.noPhysics : f.p.getAbilities().flying, "foreign state mutation retained without rollback"); });
        }); h.runAfterDelay(12, () -> { f.verify(); f.close(); if (!noPhysics) lateQuery(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void insufficient_cp_refuses_without_refill_or_consuming_pair(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> { f.stand(f.a); f.budget().putDouble("cp", .001); f.refuseKeepingPair("insufficient CP");
            near(h, f.cp(), .001, "preflight never refills depleted CP"); h.assertTrue(CornerExchange.receipt(f.p).reason().equals("insufficient_cp"), "explicit CP refusal"); }); f.finish(9);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void shared_eight_tick_fence_blocks_312_313_322_even_after_one_tick_economics(GameTestHelper h) {
        Fixture f = new Fixture(h, 5, 22.5, 26.5); f.ready(() -> { f.stand(f.a); h.assertTrue(f.tap(), "LV5 warning"); });
        f.post(11, () -> { h.assertTrue(CornerExchange.receipt(f.p).paid() && f.shared().getInt("movement") == 8, "native333 installs shared8"); f.stand(f.b);
            double cp = f.cp(); f.p.setYRot(0); f.p.setXRot(0); h.assertFalse(PhaseSidestep.start(f.p, f.slotWire(1, 1)), "312 cannot chain through333");
            f.aim(f.a); h.assertFalse(SafeLandingProbe.press(f.p, f.slotWire(2, 1)), "313 cannot chain through333");
            h.assertTrue(ReturnMark.press(f.p, f.slotWire(3, 1)), "322 may prepare free anchor"); f.stand(f.b.add(0, 0, -3));
            h.assertFalse(ReturnMark.press(f.p, f.slotWire(3, 2)), "322 cannot return through333"); near(h, f.cp(), cp, "cross-skill fencing has no extra debit"); });
        f.post(15, () -> h.assertTrue(f.shared().getInt("movement") == 4 && f.own().getInt("cooldown") == 0, "genuine clock keeps physical fence after economics expire"));
        f.post(19, () -> { h.assertValueEqual(f.shared().getInt("movement"), 0, "eighth native tick expires shared fence"); f.p.setYRot(0); f.p.setXRot(0);
            h.assertTrue(PhaseSidestep.start(f.p, f.slotWire(1, 2)), "312 is eligible at physical boundary"); }); f.finish(20);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=80)
    public static void actual_312_313_and_322_movements_block_333_until_their_shared_boundary(GameTestHelper h) { precedingMovement(h, 0); }
    /** The admitted sidestep distance of this player's growth (level-scaled). */
    static double sidestepDist(net.minecraft.server.level.ServerPlayer p){return cn.academy.port.highpower.core.PhaseSidestepRules.distance(cn.academy.port.highpower.HighPowerGrowth.values(p).position());}
    private static void precedingMovement(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h, 5, 22.5, 26.5); f.ready(() -> {
            if (mode == 0) { f.stand(f.a.add(sidestepDist(f.p), 0, 0)); f.p.setYRot(0); f.p.setXRot(0); h.assertTrue(PhaseSidestep.start(f.p, f.slotWire(1, 1)), "actual312 movement"); }
            else if (mode == 1) { f.aim(f.a); h.assertTrue(SafeLandingProbe.press(f.p, f.slotWire(2, 1)) && SafeLandingProbe.press(f.p, f.slotWire(2, 2)), "actual313 movement"); }
            else { f.stand(f.a); h.assertTrue(ReturnMark.press(f.p, f.slotWire(3, 1)), "actual322 anchor"); f.stand(f.a.add(0, 0, -3)); h.assertTrue(ReturnMark.press(f.p, f.slotWire(3, 2)), "actual322 warning"); }
        }); int commit = mode == 2 ? 12 : 8;
        f.post(commit + 1, () -> { h.assertTrue(f.shared().getInt("movement") > 0, "actual preceding movement fence exists"); f.stand(f.p.position()); f.refuseKeepingPair("existing native skill fence" + mode); });
        f.post(commit + 8, () -> { f.stand(f.p.position()); h.assertTrue(f.tap(), "333 warning at exact eighth preceding physical tick"); });
        f.post(commit + 11, () -> h.assertTrue(CornerExchange.receipt(f.p).paid() && f.p.position().equals(f.b), "333 eventually commits exact other point"));
        h.runAfterDelay(commit + 12, () -> { f.verify(); f.close(); if (mode < 2) precedingMovement(h, mode + 1); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=50)
    public static void actual_321_and_333_share_reciprocal_eight_tick_physical_fence(GameTestHelper h) {
        Fixture f = new Fixture(h, 5, 22.5, 26.5); var state = AbilityStorage.get(f.p);
        h.assertTrue(state.presets.edit(0, 3, CrossSubstitution.ID, state::learned), "declared321 mapping before either pair exists");
        f.ready(() -> { f.stand(f.source); f.aim(f.pointA); h.assertTrue(CrossSubstitution.press(f.p, f.slotWire(3, 1)), "native321 first visible mark"); });
        f.post(12, () -> { f.aim(f.pointB); h.assertTrue(CrossSubstitution.press(f.p, f.slotWire(3, 2)), "native321 second visible mark"); });
        f.post(16, () -> { f.stand(f.a); h.assertTrue(f.tap(), "native333 warning alongside retained321 pair"); });
        f.post(19, () -> { h.assertTrue(CornerExchange.receipt(f.p).paid(), "actual333 committed"); f.crouch(f.b); double cp = f.cp();
            h.assertFalse(CrossSubstitution.press(f.p, f.slotWire(3, 3)), "321 cannot cross fresh333 fence"); near(h, f.cp(), cp, "321 shared-fence refusal is free");
            h.assertValueEqual(CrossSubstitution.preparedPoints(f.p), 2, "321 preflight keeps own original pair"); });
        f.post(27, () -> { f.crouch(f.b); h.assertTrue(CrossSubstitution.press(f.p, f.slotWire(3, 4)), "321 moves at exact eighth tick after333");
            h.assertTrue(CrossSubstitution.receipt(f.p).paid() && f.p.position().distanceTo(f.a) < 1e-7, "actual321 native transport reachesA");
            f.stand(f.p.position()); f.refuseKeepingPair("333 cannot cross fresh321 fence"); });
        f.post(35, () -> { f.stand(f.p.position()); h.assertTrue(f.tap(), "333 becomes eligible at eighth tick after321"); });
        f.post(38, () -> h.assertTrue(CornerExchange.receipt(f.p).paid() && f.p.position().equals(f.b), "reciprocal native fencing ends with333 exactB transport")); f.finish(39);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=520)
    public static void first_mark_ttl_never_refreshes_on_success_refusal_or_expiring_warning(GameTestHelper h) {
        Fixture f = new Fixture(h, 5, 22.5, 26.5); f.ready(() -> { f.stand(f.a); h.assertTrue(f.tap(), "initial successful attempt"); });
        f.post(200, () -> { f.stand(f.source); f.refuseKeepingPair("later source refusal"); h.assertValueEqual(CornerExchange.remainingTicks(f.p), CornerExchangeRules.PREPARATION_TICKS - 200, "original first-mark expiry retained"); });
        f.post(CornerExchangeRules.PREPARATION_TICKS - 2, () -> { f.stand(f.b); h.assertTrue(f.tap(), "warning may start with two original ticks left"); });
        f.post(CornerExchangeRules.PREPARATION_TICKS - 1, () -> h.assertTrue(CornerExchange.remainingTicks(f.p) == 1 && CornerExchange.preparedPoints(f.p) == 2, "pair remains on its final actual tick"));
        f.post(CornerExchangeRules.PREPARATION_TICKS, () -> { h.assertFalse(CornerExchange.marked(f.p) || CornerExchange.active(f.p), "exact mark expiry cancels pending warning");
            h.assertTrue(f.p.position().equals(f.b) && !CornerExchange.receipt(f.p).paid(), "expiry never purchases or moves pending attempt"); near(h, AbilityStorage.get(f.p).exp(ID), 0, "whole lifetime grants no XP"); }); f.finish(CornerExchangeRules.PREPARATION_TICKS + 1);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=35)
    public static void native_logout_reload_retires_pair_but_preserves_paid_shared_fence_and_cp(GameTestHelper h) {
        Fixture f = new Fixture(h, 5, 22.5, 26.5); f.ready(() -> { f.stand(f.a); h.assertTrue(f.tap(), "paid warning before logout"); });
        f.post(11, () -> { h.assertTrue(CornerExchange.receipt(f.p).paid(), "actual move before save/logout"); f.budget().putDouble("cp", 37);
            AbilityStorage.save(f.p); CompoundTag saved = f.p.saveWithoutId(new CompoundTag()); CompoundTag shared = f.shared();
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.p)); f.p.load(saved); AbilityStorage.remove(f.p); AbilityStorage.get(f.p); f.stand(f.b); f.wire(f.inputs + 1); CornerExchange.tick(f.p);
            near(h, f.cp(), 37, "logout/load does not refill resource budget"); h.assertTrue(f.shared().equals(shared), "shared physical fence persists across reload");
            h.assertFalse(CornerExchange.marked(f.p) || CornerExchange.active(f.p), "no saved coordinates resurrect"); f.p.setYRot(0); f.p.setXRot(0);
            h.assertFalse(PhaseSidestep.start(f.p, f.slotWire(1, 1)), "relogged physical fence still blocks312"); }); f.finish(12);
    }

    @GameTest(template=TEMPLATE, batch="highpower_corner_exchange_world", timeoutTicks=20)
    public static void native_callback_dimension_change_is_retained_and_retires_old_world_pair(GameTestHelper h) {
        Fixture f = new Fixture(h); var other = f.p.server.getLevel(Level.NETHER); h.assertTrue(other != null, "native Nether exists");
        f.ready(() -> { f.stand(f.a); double[] cp = {0}; long deadline = f.budget().getLong("next_" + ID);
            f.onTeleport(event -> { if (f.p.serverLevel() == h.getLevel()) { cp[0] = f.cp(); f.p.teleportTo(other, f.source.x, f.source.y, f.source.z, Set.of(), f.p.getYRot(), f.p.getXRot()); } });
            h.assertTrue(f.tap(), "warning before native world transition"); f.post(3, () -> { near(h, f.cp(), cp[0], "callback world transition buys no333 movement");
                h.assertTrue(f.p.serverLevel() == other && !CornerExchange.marked(f.p) && !CornerExchange.active(f.p), "foreign world change retained and prior world pair retired");
                h.assertTrue(f.budget().getLong("next_" + ID) == deadline, "native world change sets no333 deadline"); }); }); f.finish(12);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void declared_native_transport_fault_before_or_after_move_remains_paid_and_nonreplayable(GameTestHelper h) { transportFault(h, false); }
    private static void transportFault(GameTestHelper h, boolean after) {
        Fixture f = new Fixture(h); f.p.connection = new ServerGamePacketListenerImpl(f.p.server, f.connection, f.p, f.cookie) {
            @Override public void teleport(double x, double y, double z, float yaw, float pitch, Set<RelativeMovement> relative) {
                if (after) super.teleport(x, y, z, yaw, pitch, relative); throw new IllegalStateException("Declared NEW333 native transport fault " + (after ? "after" : "before") + " movement");
            }
        }; f.wire(1); f.ready(() -> { f.stand(f.a); double[] cp = {0}; f.onTeleport(event -> cp[0] = f.cp()); h.assertTrue(f.tap(), "warning before declared transport uncertainty"); f.drain();
            f.post(3, () -> { var r = CornerExchange.receipt(f.p); h.assertTrue(r.paid() && r.moved() == after, "paid uncertain receipt reports only observed native movement");
                near(h, f.cp(), cp[0] - CornerExchangeRules.cpCost(HighPowerGrowth.values(f.p).position()), "native uncertainty never refunds debit");
                h.assertTrue(f.p.position().equals(after ? f.b : f.a) && f.positions().size() == (after ? 1 : 0), "uncertainty reflects actual transport outcome");
                h.assertFalse(CornerExchange.marked(f.p) || CornerExchange.active(f.p), "unknown paid completion retires pair"); Snapshot paid = f.snapshot();
                h.assertFalse(CornerExchange.press(f.p, f.wire(f.inputs)), "unknown paid attempt cannot replay"); f.same(paid, "uncertain paid replay"); h.assertValueEqual(f.shared().getInt("movement"), 8, "uncertain transport retains physical fence"); });
        }); h.runAfterDelay(12, () -> { f.verify(); f.close(); if (!after) transportFault(h, true); else h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void actual_native_injury_fire_effects_and_fall_receive_no_exchange_benefit(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {}); f.post(65, () -> {
            h.assertTrue(f.p.hurt(f.p.damageSources().generic(), 4), "native login protection has expired before real injury"); f.stand(f.a); f.p.setRemainingFireTicks(80);
            f.p.addEffect(new MobEffectInstance(MobEffects.POISON, 120)); f.p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140)); Body[] before = {null};
            f.onTeleport(event -> before[0] = Body.of(f.p)); h.assertTrue(f.tap(), "injured and burning grounded source may exchange");
            f.post(3, () -> { h.assertTrue(CornerExchange.receipt(f.p).paid() && Body.of(f.p).equals(before[0]), "native commit preserves exact hurt/iframe/fire/effect/fall state");
                BigDecimal hp = new BigDecimal(HighPowerCombat.healthText(f.p)); f.p.setOnGround(false); f.p.fallDistance = 8;
                h.assertTrue(f.p.causeFallDamage(8, 1, f.p.damageSources().fall()), "real subsequent fall remains damaging");
                h.assertTrue(new BigDecimal(HighPowerCombat.healthText(f.p)).compareTo(hp) < 0 && !f.p.isNoGravity() && !f.p.getAbilities().flying, "no fall immunity, healing or flight"); });
        }); f.finish(69);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void actual_native_lethal_callback_retires_pair_without_payment_or_automatic_rescue(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {}); f.post(65, () -> { f.stand(f.a); double[] cp = {0}; f.onTeleport(event -> {
            cp[0] = f.cp(); float lethal = new BigDecimal(HighPowerCombat.healthText(f.p)).multiply(BigDecimal.valueOf(2)).floatValue();
            h.assertTrue(f.p.hurt(f.p.damageSources().fellOutOfWorld(), lethal), "actual lethal native callback damage"); }); h.assertTrue(f.tap(), "warning before native death callback");
            f.post(3, () -> { near(h, f.cp(), cp[0], "death callback cannot pay"); h.assertTrue(f.p.isDeadOrDying() && !CornerExchange.marked(f.p) && !CornerExchange.active(f.p)
                    && f.positions().isEmpty(), "death removes pair without automatic rescue"); }); }); f.finish(69);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void native_unloaded_source_guard_does_not_load_chunks_or_change_metadata(GameTestHelper h) {
        Vec3 remote = new Vec3(25000000.5, 90, 25000000.5); ChunkPos chunk = new ChunkPos(BlockPos.containing(remote)); var server = h.getLevel().getServer();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "CEUnloaded"), false); var p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND); var channel = new EmbeddedChannel(connection); NetworkRegistry.configureMockConnection(connection); p.connection = new ServerGamePacketListenerImpl(server, connection, p, cookie);
        try { h.assertTrue(!p.isAddedToLevel() && h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) == null, "guard-only native player begins outside loaded chunks");
            p.setOnGround(true); p.setPose(Pose.STANDING); p.setShiftKeyDown(false); p.setDeltaMovement(Vec3.ZERO); p.setPos(remote); p.connection.resetPosition(); CompoundTag before = p.getPersistentData().copy();
            var assessment = SafeLandingProbeWorld.cornerPoint(p); h.assertTrue(!assessment.valid() && assessment.reason().equals("unloaded_chunk"), "native corner guard fails closed on unloaded source");
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) == null && p.getPersistentData().equals(before) && p.position().equals(remote), "guard neither loads chunks nor repairs metadata or source"); h.succeed();
        } finally { p.discard(); channel.finishAndReleaseAll(); }
    }

    private record Snapshot(double cp, long deadline, int cooldown, double mastery, double levelXp, BigDecimal q, String hp, String shield, double overload, int needles) {}
    private record Body(Vec3 motion, float fall, boolean ground, boolean noGravity, boolean flying, int hurt, int invulnerable, int fire, String hp, String shield, Map<Object, String> effects) {
        static Body of(ServerPlayer p) { Map<Object, String> effects = new HashMap<>(); p.getActiveEffectsMap().forEach((key, effect) -> effects.put(key, effect.getAmplifier() + ":" + effect.getDuration() + ":" + effect.isAmbient() + ":" + effect.isVisible()));
            return new Body(p.getDeltaMovement(), p.fallDistance, p.onGround(), p.isNoGravity(), p.getAbilities().flying, p.hurtTime, p.invulnerableTime, p.getRemainingFireTicks(), HighPowerCombat.healthText(p), HighPowerCombat.shieldText(p), Map.copyOf(effects)); }
    }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-7, why + ": " + actual + " != " + expected); }
    private static void malformedFacing(ServerPlayer p, boolean pitch, float value) {
        try { var field = Entity.class.getDeclaredField(pitch ? "xRot" : "yRot"); field.setAccessible(true); field.setFloat(p, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError("Declared native orientation fixture could not reach the actual field", failure); }
    }
    private static CompoundTag own(int request, int cooldown) { CompoundTag tag = new CompoundTag(); tag.putInt("version", 1); tag.putInt("request", request); tag.putInt("cooldown", cooldown); return tag; }
    public static final class CallbackHusk extends Husk {
        Runnable queryMutation; int queryCount; CallbackHusk(Level level) { super(EntityType.HUSK, level); }
        @Override public boolean canBeCollidedWith() { if (queryMutation != null && ++queryCount == 2) { Runnable action = queryMutation; queryMutation = null; action.run(); } return super.canBeCollidedWith(); }
    }
    public static final class TeleportHook {
        final ServerPlayer owner; final Consumer<EntityTeleportEvent> action; int calls, tick;
        TeleportHook(ServerPlayer owner, Consumer<EntityTeleportEvent> action) { this.owner = owner; this.action = action; }
        @SubscribeEvent public void teleport(EntityTeleportEvent event) { if (event.getEntity() == owner) { calls++; tick = owner.server.getTickCount(); action.accept(event); } }
    }
    public static final class SpawnHook {
        final Fixture f; SpawnHook(Fixture f) { this.f = f; }
        @SubscribeEvent public void join(EntityJoinLevelEvent event) { if (event.getEntity() instanceof DisplacedNeedleEntity needle && needle.getOwner() == f.p) { f.spawns++; f.entities.add(needle); } }
    }
    public static final class PostCheck {
        final Fixture f; final int tick; final Runnable action; boolean ran;
        PostCheck(Fixture f, int delay, Runnable action) { this.f = f; tick = f.p.server.getTickCount() + delay; this.action = action; }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(ServerTickEvent.Post event) { if (ran || event.getServer() != f.p.server || event.getServer().getTickCount() != tick) return; ran = true;
            try { action.run(); } catch (Throwable failure) { if (f.failure == null) f.failure = failure; } }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final ServerPlayer p; final Connection connection; final CommonListenerCookie cookie; final EmbeddedChannel channel;
        final Vec3 source, pointA; Vec3 pointB, a, b; final List<Entity> entities = new ArrayList<>(); final List<Object> hooks = new ArrayList<>(); final List<PostCheck> checks = new ArrayList<>();
        final Map<BlockPos, BlockState> changed = new LinkedHashMap<>(); final Set<ChunkPos> held = new LinkedHashSet<>(); final UUID ticket = UUID.randomUUID();
        long inputs; int spawns; boolean closed; Throwable failure;
        Fixture(GameTestHelper h) { this(h, 3, 22.5, 26.5); }
        Fixture(GameTestHelper h, int level, double ax, double bx) {
            this.h = h; source = h.absoluteVec(new Vec3(24.5, 5, 8.5)); pointA = h.absoluteVec(new Vec3(ax, 5, 14.5)); pointB = h.absoluteVec(new Vec3(bx, 5, 14.5));
            for (int x = 6; x <= 43; x++) for (int z = 4; z <= 18; z++) block(h.absolutePos(new BlockPos(x, 4, z)), Blocks.STONE.defaultBlockState());
            var server = h.getLevel().getServer(); cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "CEX" + UUID.randomUUID().toString().substring(0, 9)), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie);
            for (var packet : positions()) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()));
            p.setGameMode(GameType.SURVIVAL); p.getAbilities().invulnerable = false; p.getAbilities().instabuild = false; p.setNoGravity(false); stand(source);
            p.move(MoverType.SELF, new Vec3(0, -.0784, 0)); h.assertTrue(p.onGround() && p.position().equals(source), "genuine floor collision establishes native fixture ground");
            h.assertTrue(HighPowerCombat.enroll(p, level), "declared native enrollment: " + HighPowerCombat.unsupportedReason(p)); var state = AbilityStorage.get(p); state.selectCategory(HighPowerCategories.TELEPORTER); state.setLevel(level);
            for (String skill : List.of(SafeLandingProbe.ID, PhaseSidestep.ID, DisplacedProjection.ID, ReturnMark.ID, CrossSubstitution.ID, ID)) state.learn(skill);
            state.activated = true; state.overloadFine = true;
            h.assertTrue(state.presets.edit(0, 0, ID, state::learned) && state.presets.edit(0, 1, PhaseSidestep.ID, state::learned)
                    && state.presets.edit(0, 2, SafeLandingProbe.ID, state::learned) && state.presets.edit(0, 3, ReturnMark.ID, state::learned), "real learned mapped skills");
            HighPowerResources.initializeBudget(p); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), 3));
            hook(new SpawnHook(this)); hook(new HighPowerPhaseSidestepRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5));
            h.testInfo.addListener(new GameTestListener() { public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); } public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); } });
        }
        void stand(Vec3 at) { p.setShiftKeyDown(false); p.setPose(Pose.STANDING); place(at); }
        void crouch(Vec3 at) { p.setShiftKeyDown(true); p.setPose(Pose.CROUCHING); place(at); }
        void place(Vec3 at) { p.setPos(at); p.fallDistance = 0; p.setOnGround(true); p.setDeltaMovement(0, -.0784, 0); p.connection.resetPosition(); }
        void aim(Vec3 at) { Vec3 d = at.subtract(p.getEyePosition()); p.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z))); p.setXRot((float)-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x*d.x + d.z*d.z)))); }
        String slotWire(int slot, long input) { return new HighPowerInput.Input(slot, input, p.getId(), LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p))).wire(); }
        String wire(long input) { return slotWire(0, input); }
        boolean press() { return AcademyGameplay.requestFromClient(p, new AcademyNetwork.Request("hp_corner_exchange_start", wire(++inputs))); }
        boolean release() { return AcademyGameplay.requestFromClient(p, new AcademyNetwork.Request("hp_corner_exchange_release", wire(inputs))); }
        boolean tap() { boolean start = press(); return start && release(); }
        net.minecraft.world.phys.BlockHitResult ray() { return h.getLevel().clip(new ClipContext(p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().normalize().scale(24)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p)); }
        Vec3 surface() { var hit = ray(); h.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP, "independent actual24m ray reaches first-UP floor: " + hit.getType() + "/" + hit.getDirection() + "/" + hit.getLocation()); return hit.getLocation(); }
        void ready(Runnable action) { aim(pointA); a = surface(); h.assertTrue(tap(), "genuine first release mark: " + CornerExchange.receipt(p));
            post(4, () -> { aim(pointB); b = surface(); h.assertTrue(tap(), "genuine second release mark: " + CornerExchange.receipt(p));
                h.assertTrue(CornerExchange.preparedPoints(p) == 2 && CornerExchange.remainingTicks(p) == CornerExchangeRules.PREPARATION_TICKS - 4, "second point does not refresh first-point TTL"); }); post(8, action); }
        void block(BlockPos pos, BlockState state) { h.assertTrue(h.getBounds().contains(Vec3.atCenterOf(pos)), "fixture block stays inside dedicated native arena"); ChunkPos chunk = new ChunkPos(pos);
            if (held.add(chunk)) { h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticket); h.getLevel().getChunk(chunk.x, chunk.z); }
            changed.putIfAbsent(pos, h.getLevel().getBlockState(pos)); h.getLevel().setBlockAndUpdate(pos, state); }
        Zombie mob(Vec3 at) { Zombie mob = EntityType.HUSK.create(h.getLevel()); mob.setNoAi(true); mob.setNoGravity(true); mob.setSilent(true); mob.setPos(at); h.assertTrue(h.getLevel().addFreshEntity(mob), "native obstacle entity admitted"); entities.add(mob); return mob; }
        double cp() { return HighPowerResources.cp(p); }
        int needles() { int count = 0; for (int i = 0; i < p.getInventory().getContainerSize(); i++) { ItemStack stack = p.getInventory().getItem(i); if (stack.is(AcademyCraft.NEEDLE.get())) count += stack.getCount(); } return count; }
        CompoundTag budget() { return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY); }
        CompoundTag own() { return p.getPersistentData().getCompound(OWN).copy(); }
        CompoundTag shared() { return p.getPersistentData().getCompound(SHARED).copy(); }
        Snapshot snapshot() { var s = AbilityStorage.get(p); return new Snapshot(cp(), budget().getLong("next_" + ID), s.cooldowns.getOrDefault(ID, 0), s.exp(ID), s.levelExperience,
                HighPowerGrowth.values(p).position(), HighPowerCombat.healthText(p), HighPowerCombat.shieldText(p), s.overload, needles()); }
        void same(Snapshot before, String why) { h.assertTrue(snapshot().equals(before), why + " preserves CP/deadline/cooldown/mastery/XP/growth/HP/shield/overload/stock"); }
        void success(Snapshot before, Vec3 destination) { var r = CornerExchange.receipt(p); h.assertTrue(r != null && r.paid() && r.moved(), "confirmed paid native exchange: " + r);
            h.assertTrue(p.position().equals(destination) && r.destination().equals(destination), "exact saved native destination without offset/fallback"); near(h, cp(), before.cp - CornerExchangeRules.cpCost(before.q), "one smooth native CP debit"); Snapshot after = snapshot();
            h.assertTrue(after.mastery == before.mastery && after.levelXp == before.levelXp && after.q.compareTo(before.q) == 0 && after.hp.equals(before.hp) && after.shield.equals(before.shield)
                    && after.overload == before.overload && after.needles == before.needles && spawns == 0, "exchange grants no growth/healing/shield/ammunition/projectile authority");
            h.assertTrue(CornerExchange.preparedPoints(p) == 2 && !CornerExchange.active(p), "success retains reusable pair, ends current attempt"); }
        void refuseKeepingPair(String why) { Snapshot before = snapshot(); int points = CornerExchange.preparedPoints(p), ttl = CornerExchange.remainingTicks(p); Vec3 source = p.position();
            h.assertFalse(tap(), why + ": " + CornerExchange.receipt(p)); same(before, why); h.assertTrue(CornerExchange.preparedPoints(p) == points && CornerExchange.remainingTicks(p) == ttl && p.position().equals(source), why + " retains original pair/TTL/source"); }
        void freeAttempt(String reason) { var r = CornerExchange.receipt(p); h.assertTrue(r != null && !r.paid() && !r.moved() && r.terminal() && reason.equals(r.reason()), "free terminal attempt: " + r);
            h.assertTrue(CornerExchange.preparedPoints(p) == 2 && !CornerExchange.active(p), "ordinary terminal attempt keeps pair"); }
        TeleportHook onTeleport(Consumer<EntityTeleportEvent> action) { TeleportHook hook = new TeleportHook(p, action); hook(hook); return hook; }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void post(int delay, Runnable action) { PostCheck check = new PostCheck(this, delay, action); checks.add(check); hook(check); }
        void verify() { if (failure instanceof RuntimeException problem) throw problem; if (failure instanceof Error problem) throw problem; if (failure != null) throw new AssertionError(failure); h.assertTrue(checks.stream().allMatch(check -> check.ran), "all genuine server Post observations executed"); }
        void finish(int delay) { h.runAfterDelay(delay, () -> { verify(); h.succeed(); }); }
        List<Object> drain() { List<Object> packets = new ArrayList<>(); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet;
            while ((packet = channel.readOutbound()) != null) { packets.add(packet); ReferenceCountUtil.release(packet); } return packets; }
        List<ClientboundPlayerPositionPacket> positions() { List<ClientboundPlayerPositionPacket> packets = new ArrayList<>(); for (Object packet : drain()) if (packet instanceof ClientboundPlayerPositionPacket position) packets.add(position); return packets; }
        @Override public void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); CornerExchange.remove(p); CrossSubstitution.clear(p); PhaseSidestep.remove(p); SafeLandingProbe.remove(p); ReturnMark.remove(p);
            HighPowerBracerSession.remove(p); AbilityStorage.remove(p); p.server.getPlayerList().remove(p); entities.forEach(Entity::discard);
            changed.forEach((pos, state) -> h.getLevel().setBlockAndUpdate(pos, state)); held.forEach(chunk -> h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticket)); channel.finishAndReleaseAll(); }
    }
}

package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.RecoveryMarkRules;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW323 native server acceptance. Real PlayerList owners and production custody
 * deployment use declared enrollment, inventory, ground and pose fixtures. These
 * tests do not claim natural acquisition, a desktop client or a process restart. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerRecoveryMarkRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_recovery_mark";
    private static final String OWN = "academy:recovery_mark_fences";
    private static final TicketType<UUID> TICKET = TicketType.create("academy_recovery_mark_fixture", UUID::compareTo);
    private HighPowerRecoveryMarkRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void canonical_native_request_routes_and_visual_codec_preserve_exact_identity(GameTestHelper h) {
        Fixture f = new Fixture(h);
        h.assertTrue(AcademyGameplay.requestFromClient(f.p, new AcademyNetwork.Request("hp_recovery_mark_start", f.wire(1))), "canonical native press registers mark");
        h.assertTrue(AcademyGameplay.requestFromClient(f.p, new AcademyNetwork.Request("hp_recovery_mark_release", f.wire(1))), "canonical release is inert");
        var receipt = RecoveryMark.receipt(f.p);
        List<RecoveryMark.Visual> packets = List.of(
                new RecoveryMark.Visual(f.p.getId(), f.p.getUUID(), 1, f.epoch(), receipt.token(), 1, RecoveryMark.MARKED,
                        f.source, f.anchor, f.needle.getId(), f.needle.getUUID(), 1200, true, ""),
                new RecoveryMark.Visual(f.p.getId(), f.p.getUUID(), 2, f.epoch(), receipt.token(), 2, RecoveryMark.RECOVERED,
                        f.source, f.anchor, f.needle.getId(), f.needle.getUUID(), 0, true, ""),
                new RecoveryMark.Visual(f.p.getId(), f.p.getUUID(), 3, f.epoch(), receipt.token(), 3, RecoveryMark.CLEARED,
                        null, null, -1, null, 0, true, "cleared"),
                new RecoveryMark.Visual(f.p.getId(), f.p.getUUID(), 4, f.epoch(), receipt.token(), 4, RecoveryMark.REFUSED,
                        null, null, -1, null, 0, false, "needle_missing"));
        for (var packet : packets) {
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                RecoveryMark.Visual.CODEC.encode(buffer, packet); var decoded = RecoveryMark.Visual.CODEC.decode(buffer);
                h.assertTrue(packet.equals(decoded) && buffer.readableBytes() == 0, "bounded native visual codec roundtrips exact optional fields");
            } finally { buffer.release(); }
        }
        boolean invalid = false;
        try { new RecoveryMark.Visual(f.p.getId(), f.p.getUUID(), 1, f.epoch(), receipt.token(), 1, RecoveryMark.RECOVERED,
                f.source, f.anchor, f.needle.getId(), f.needle.getUUID(), 0, false, ""); }
        catch (IllegalArgumentException expected) { invalid = true; }
        h.assertTrue(invalid, "success packet cannot claim an unaccepted transport");
        h.assertTrue(AcademyGameplay.requestFromClient(f.p, new AcademyNetwork.Request("hp_recovery_mark_abort", f.wire(1))), "canonical native abort clears");
        h.assertFalse(RecoveryMark.marked(f.p), "aborted route leaves no automatic recovery"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void exact_authenticated_aim_marks_owned_existing_needle_freely(GameTestHelper h) {
        Fixture f = new Fixture(h); Snapshot before = f.snapshot(); var identity = f.needle.identity();
        h.assertTrue(f.press(1), "actual server aim registers certified existing needle");
        var receipt = RecoveryMark.receipt(f.p);
        h.assertTrue(receipt != null && receipt.phase() == RecoveryMark.MARKED && !receipt.paid() && !receipt.moved()
                && receipt.source().equals(f.source) && receipt.destination().equals(f.anchor)
                && receipt.needleUuid().equals(identity.entityId()), "mark retains exact needle identity and frozen feet");
        h.assertTrue(RecoveryMark.marked(f.p) && !RecoveryMark.active(f.p), "stored mark is not a held movement context");
        h.assertTrue(f.own().getInt("request") == 8 && f.own().getInt("cooldown") == 0, "only own request fence starts");
        for (int i = 0; i < 1400; i++) RecoveryMark.tick(f.p);
        h.assertTrue(RecoveryMark.remainingTicks(f.p) == 1200, "same-tick callbacks cannot age or extend the mark");
        h.assertTrue(RecoveryMark.release(f.p, f.wire(1)), "authenticated release remains inert");
        h.assertFalse(f.press(1), "same input cannot recall"); f.same(before, "free mark and duplicate"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void plain_inventory_item_or_wrong_server_aim_cannot_replace_certified_needle(GameTestHelper h) {
        for (int mode = 0; mode < 3; mode++) {
            Fixture f = new Fixture(h); Snapshot before = f.snapshot();
            if (mode == 0) f.needle.discard();
            if (mode == 1) f.aim(f.p.getEyePosition().add(0, 0, -12));
            if (mode == 2) f.block(10, 6, 8, Blocks.STONE);
            h.assertFalse(f.press(1), "missing, mis-aimed or obstructed needle is not registered: " + mode);
            h.assertFalse(RecoveryMark.marked(f.p), "refused selection leaves no mark");
            f.same(before, "selection refusal"); f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void fresh_second_press_moves_same_entity_once_for_actual_growth_cost(GameTestHelper h) {
        for (int level = 2; level <= 5; level++) {
            Fixture f = new Fixture(h, level); var identity = f.needle.identity(); ItemStack medium = f.needle.mediumSnapshot();
            Snapshot before = f.snapshot(); h.assertTrue(f.press(1), "free LV" + level + " mark");
            f.place(f.anchor.add(0, 0, 2));
            h.assertTrue(f.press(2), "fresh confirmation can follow registration immediately");
            f.success(2); double cost = RecoveryMarkRules.cpCost(HighPowerGrowth.values(f.p).position());
            near(h, f.cp(), before.cp - cost, "actual q cost paid once");
            h.assertTrue(f.needle.identity().equals(identity) && ItemStack.matches(f.needle.mediumSnapshot(), medium)
                    && DisplacedNeedleRegistry.existing(f.p).orElse(null) == f.needle && !f.needle.isContactEnabled(),
                    "same physical entity, original item and generation remain harmless");
            h.assertTrue(f.p.position().equals(f.anchor.add(0, 0, 2)), "recovery never moves player to anchor");
            h.assertTrue(f.own().getInt("request") == 8
                    && f.own().getInt("cooldown") == RecoveryMarkRules.cooldownTicks(HighPowerGrowth.values(f.p).position()),
                    "paid move restarts full physical request and cooldown fences");
            f.unchangedPools(before, "success"); Snapshot paid = f.snapshot(); var receipt = RecoveryMark.receipt(f.p);
            h.assertFalse(f.press(2), "committed input cannot replay");
            h.assertFalse(f.press(3), "new mark is blocked by durable request fence");
            RecoveryMark.release(f.p, f.wire(2)); RecoveryMark.tick(f.p);
            f.same(paid, "replay and release"); h.assertTrue(RecoveryMark.receipt(f.p).equals(receipt), "paid receipt remains historical");
            f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void crouch_clears_without_charge_even_airborne_with_cooldown(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.press(1), "safe mark before free clear");
        f.p.getPersistentData().put(OWN, own(8, 40)); f.p.setOnGround(false); f.p.fallDistance = 7;
        f.p.setShiftKeyDown(true); Snapshot before = f.snapshot(); Vec3 needle = f.needle.position();
        h.assertTrue(f.press(2), "crouch clears before ground or cooldown checks");
        h.assertFalse(RecoveryMark.marked(f.p), "clear consumes mark");
        h.assertTrue(RecoveryMark.receipt(f.p).phase() == RecoveryMark.CLEARED && f.own().equals(own(8, 40))
                && f.needle.position().equals(needle) && f.p.fallDistance == 7, "clear repairs no movement or fence");
        f.same(before, "crouch clear"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_needle_event_cancel_and_redirect_are_free_and_preserve_foreign_edits(GameTestHelper h) {
        for (int mode = 0; mode < 2; mode++) {
            Fixture f = new Fixture(h); h.assertTrue(f.press(1), "mark before event refusal"); Snapshot before = f.snapshot();
            int selected = mode;
            TeleportHook hook = f.onTeleport(event -> {
                near(h, f.cp(), before.cp, "event occurs before own debit");
                f.p.getPersistentData().putInt("academy:recovery_callback", 19);
                if (selected == 0) event.setCanceled(true); else event.setTargetX(event.getTargetX() + .01);
            });
            h.assertFalse(f.press(2), "native canceled or redirected needle event refuses");
            f.freeReceipt(mode == 0 ? "event_cancelled" : "event_redirected"); f.same(before, "event refusal");
            h.assertTrue(hook.calls == 1 && f.needle.position().equals(f.source)
                    && f.p.getPersistentData().getInt("academy:recovery_callback") == 19, "one event, original source, callback preserved");
            f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_event_reentry_cannot_tick_pickup_or_pay_a_second_recovery(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.press(1), "mark before reentry"); Snapshot before = f.snapshot();
        TeleportHook hook = f.onTeleport(event -> {
            h.assertFalse(f.press(3), "same controller reentry is bounded");
            Vec3 source = f.needle.position(); f.needle.tick(); f.needle.playerTouch(f.p);
            h.assertTrue(f.needle.position().equals(source) && !f.needle.isRemoved(), "registry barrier blocks native tick and pickup");
            h.assertFalse(DisplacedNeedleRegistry.recoverExisting(f.p, f.needle, f.needle.identity(), f.anchor,
                    () -> true, () -> { throw new AssertionError("reentrant payment must not run"); }), "nested registry recovery is blocked");
        });
        h.assertTrue(f.press(2), "one outer recovery succeeds after inert reentry"); f.success(2);
        near(h, f.cp(), before.cp - RecoveryMarkRules.cpCost(HighPowerGrowth.values(f.p).position()), "exactly one debit");
        h.assertTrue(hook.calls == 1, "one native teleport event"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void paid_native_section_owner_changes_remain_unconfirmed_without_refund_or_replay(GameTestHelper h) {
        for (int mode = 0; mode < 5; mode++) {
            Fixture f = new Fixture(h); Vec3 original = f.anchor.add(16, .2, 0);
            f.block(24, 4, 8, Blocks.STONE); f.needle.setPos(original); f.aim(f.needle.getBoundingBox().getCenter());
            h.assertTrue(f.press(1), "mark has an actual16m native section crossing");
            Snapshot before = f.snapshot(); double cost = RecoveryMarkRules.cpCost(HighPowerGrowth.values(f.p).position());
            var identity = f.needle.identity(); int selected = mode;
            SectionHook hook = new SectionHook(f, event -> {
                var paid = RecoveryMark.receipt(f.p);
                h.assertTrue(paid != null && paid.input() == 2 && paid.paid() && paid.phase() == RecoveryMark.REFUSED
                        && paid.reason().equals("transport_unconfirmed"), "paid input and uncertain receipt precede native position callbacks");
                near(h, f.cp(), before.cp - cost, "one debit already committed before section callback");
                h.assertFalse(f.press(3), "section callback cannot start a second controller payment");
                h.assertFalse(DisplacedNeedleRegistry.recoverExisting(f.p, f.needle, identity, original,
                        () -> true, () -> { throw new AssertionError("nested payment must not run"); }), "native recovery barrier rejects reentry");
                switch (selected) {
                    case 0 -> f.p.setPos(f.p.position().add(.01, 0, 0));
                    case 1 -> AbilityStorage.get(f.p).presets.edit(1, 1, RecoveryMark.ID, AbilityStorage.get(f.p)::learned);
                    case 2 -> AbilityStorage.get(f.p).activated = false;
                    case 3 -> RecoveryMark.cancelActive(f.p);
                    default -> h.assertTrue(RecoveryMark.abort(f.p, f.wire(3)), "later authenticated abort retires input without rewriting paid input");
                }
            });
            f.hook(hook);
            h.assertFalse(f.press(2), "changed owner after native relocation never reports confirmed recovery: " + mode);
            var receipt = RecoveryMark.receipt(f.p);
            h.assertTrue(hook.calls == 1 && receipt != null && receipt.input() == 2 && receipt.paid() && receipt.moved()
                    && receipt.phase() == RecoveryMark.REFUSED && receipt.reason().equals("transport_unconfirmed"),
                    "one actual section callback retains paid original input and observed move as unconfirmed: " + receipt);
            h.assertTrue(f.needle.position().equals(f.anchor) && f.needle.identity().equals(identity)
                    && !f.needle.isContactEnabled() && !RecoveryMark.marked(f.p), "native movement is preserved with same harmless original and consumed mark");
            near(h, f.cp(), before.cp - cost, "post-payment cancellation has no refund"); f.unchangedPools(before, "paid uncertainty");
            h.assertTrue(f.own().getInt("request") == 8 && f.own().getInt("cooldown") > 0, "paid fences survive owner callback changes");
            h.assertFalse(DisplacedNeedleRegistry.retryRefund(f.p), "existing physical medium does not create refundable escrow");
            if (mode == 0) h.assertTrue(f.p.position().equals(f.anchor.add(.01, 0, 0)), "foreign player movement is not rolled back");
            h.assertFalse(f.press(2), "old paid input remains retired"); near(h, f.cp(), before.cp - cost, "replay cannot charge twice");
            f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void callback_state_inventory_identity_and_abort_mutations_refuse_before_payment(GameTestHelper h) {
        for (int mode = 0; mode < 8; mode++) {
            Fixture f = new Fixture(h); h.assertTrue(f.press(1), "mark before callback mutation " + mode); Snapshot before = f.snapshot();
            int selected = mode;
            f.onTeleport(event -> {
                switch (selected) {
                    case 0 -> f.p.setYRot(f.p.getYRot() + 20);
                    case 1 -> f.p.setPos(f.p.position().add(.01, 0, 0));
                    case 2 -> f.p.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
                    case 3 -> AbilityStorage.get(f.p).presets.edit(1, 1, RecoveryMark.ID, AbilityStorage.get(f.p)::learned);
                    case 4 -> f.needle.setPos(f.source.add(0, .01, 0));
                    case 5 -> f.p.hurtTime = 4;
                    case 6 -> RecoveryMark.abort(f.p, f.wire(3));
                    default -> f.block(10, 5, 8, Blocks.STONE);
                }
            });
            h.assertFalse(f.press(2), "changed callback snapshot cannot commit case " + mode);
            f.freeReceipt(null); f.same(before, "callback mutation does not charge or heal");
            if (mode == 2) h.assertTrue(f.p.getInventory().getItem(0).is(Items.DIAMOND), "foreign inventory edit is retained");
            if (mode == 4) h.assertTrue(f.needle.position().equals(f.source.add(0, .01, 0)), "foreign needle movement is retained");
            f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void full_inventory_refuses_but_one_partial_stack_has_native_capacity(GameTestHelper h) {
        for (boolean partial : new boolean[]{false, true}) {
            Fixture f = new Fixture(h); h.assertTrue(f.press(1), "mark before inventory changes");
            for (int slot = 0; slot < f.p.getInventory().items.size(); slot++)
                f.p.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE, 64));
            if (partial) f.p.getInventory().setItem(9, new ItemStack(AcademyCraft.NEEDLE.get(), 63));
            Snapshot before = f.snapshot(); TeleportHook hook = f.onTeleport(event -> {});
            if (partial) { h.assertTrue(f.press(2), "one real partial stack leaves capacity"); f.success(2); }
            else { h.assertFalse(f.press(2), "full native inventory rejects recovery"); f.freeReceipt("inventory_full"); f.same(before, "full inventory"); }
            h.assertTrue(hook.calls == (partial ? 1 : 0), "capacity is checked before event and payment"); f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void frozen_anchor_survives_ordinary_movement_and_current_range_is_rechecked(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.press(1), "fixed initial anchor");
        Vec3 laterNeedle = f.source.add(0, .1, 1); f.needle.setPos(laterNeedle); f.place(f.anchor.add(1, 0, 1));
        h.assertTrue(f.press(2), "later actual needle position is recoverable to original fixed point");
        var receipt = RecoveryMark.receipt(f.p);
        h.assertTrue(receipt.source().equals(laterNeedle) && receipt.destination().equals(f.anchor)
                && f.needle.position().equals(f.anchor), "recall does not move anchor along with player or needle"); f.close();
        Fixture range = new Fixture(h); h.assertTrue(range.press(1), "mark before current-distance change");
        range.needle.setPos(range.anchor.add(RecoveryMarkRules.RANGE+.01, .2, 0)); Snapshot before = range.snapshot();
        h.assertFalse(range.press(2), "current needle beyond reach cannot borrow original range"); range.freeReceipt(null);
        range.same(before, "range refusal"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void no_op_airborne_busy_combat_and_changed_destination_are_free(GameTestHelper h) {
        for (int mode = 0; mode < 6; mode++) {
            Fixture f = new Fixture(h); h.assertTrue(f.press(1), "mark before current refusal");
            switch (mode) {
                case 0 -> f.needle.setPos(f.anchor);
                case 1 -> { f.p.setOnGround(false); f.p.fallDistance = 2; }
                case 2 -> f.p.hurtTime = 4;
                case 3 -> { h.assertTrue(f.needle.beginFlight(f.needle.identity()), "declared new native contact state"); }
                case 4 -> f.block(8, 4, 8, Blocks.AIR);
                default -> f.block(10, 5, 8, Blocks.STONE);
            }
            Snapshot before = f.snapshot(); h.assertFalse(f.press(2), "unsafe recall refuses case " + mode);
            f.freeReceipt(null); f.same(before, "unsafe recall"); f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void learned_lv2_and_current_can_use_are_required_without_zero_level_refund(GameTestHelper h) {
        for (int mode = 0; mode < 5; mode++) {
            Fixture f = new Fixture(h, mode == 0 ? 1 : 3); var state = AbilityStorage.get(f.p);
            if (mode == 1) state.setLevel(0);
            if (mode == 2) state.activated = false;
            if (mode == 3) state.setCooldown(RecoveryMark.ID, 2);
            if (mode == 4) state.experience.remove(RecoveryMark.ID);
            double stored = f.budget().getDouble("cp");
            h.assertFalse(f.press(1), "ineligible current state cannot mark: " + mode);
            h.assertTrue(f.budget().getDouble("cp") == stored, "zero or unusable level never refills or refunds CP"); f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void fractional_growth_charges_actual_q_and_insufficient_cp_never_moves(GameTestHelper h) {
        Fixture fractional = new Fixture(h, 2); AbilityStorage.get(fractional.p).addExperience(SafeLandingProbe.ID, .01);
        var q = HighPowerGrowth.values(fractional.p).position();
        h.assertTrue(q.compareTo(java.math.BigDecimal.valueOf(2)) > 0 && q.compareTo(java.math.BigDecimal.valueOf(3)) < 0,
                "real prerequisite XP supplies fractional current q");
        double before = fractional.cp(); h.assertTrue(fractional.press(1) && fractional.press(2), "fractional recovery commits");
        near(h, before - fractional.cp(), RecoveryMarkRules.cpCost(q), "fractional native debit follows continuous economics");
        fractional.close();
        Fixture empty = new Fixture(h); empty.budget().putDouble("cp", 0); Snapshot budget = empty.snapshot();
        h.assertTrue(empty.press(1), "registration does not require paying recovery cost");
        h.assertFalse(empty.press(2), "zero available CP cannot recover"); empty.freeReceipt("insufficient_cp");
        empty.same(budget, "insufficient CP"); h.assertTrue(empty.needle.position().equals(empty.source), "unpaid medium stays at original source"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void real_post_ticks_age_once_and_preset_or_owner_epoch_change_clears(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.press(1), "mark before observed ticks");
        f.post(3, () -> {
            h.assertTrue(RecoveryMark.remainingTicks(f.p) == 1197, "three physical ticks age exactly three");
            for (int i = 0; i < 1500; i++) RecoveryMark.tick(f.p);
            h.assertTrue(RecoveryMark.remainingTicks(f.p) == 1197, "duplicate observations do not age");
            AbilityStorage.get(f.p).presets.edit(1, 1, RecoveryMark.ID, AbilityStorage.get(f.p)::learned);
        });
        f.post(4, () -> h.assertFalse(RecoveryMark.marked(f.p), "preset revision invalidates mark next physical tick"));
        f.finish(5);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_logout_unload_and_owner_epoch_change_clear_without_restoring_mark(GameTestHelper h) {
        for (int mode = 0; mode < 3; mode++) {
            Fixture f = new Fixture(h); h.assertTrue(f.press(1), "mark before lifecycle seam"); CompoundTag fences = f.own();
            if (mode == 0) NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.p));
            else if (mode == 1) f.needle.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            else { LegacySingleKeyOwnerEpoch.forget(f.p); f.epoch(); h.assertFalse(f.press(2), "new owner epoch cannot confirm old mark"); }
            h.assertFalse(RecoveryMark.marked(f.p), "lifecycle invalidation retains no mark: " + mode);
            h.assertTrue(f.own().equals(fences), "logout/unload/epoch preserves physical fences"); f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void low_level_paid_cooldown_and_high_level_eight_tick_fence_survive_immediate_retry(GameTestHelper h) {
        Fixture f = new Fixture(h, 5); h.assertTrue(f.press(1) && f.press(2), "one LV5 recovery commits");
        f.place(f.anchor.add(0, 0, 2)); f.needle.setPos(f.source); f.aim(f.needle.getBoundingBox().getCenter());
        f.post(2, () -> {
            h.assertTrue(f.own().getInt("cooldown") == 0 && f.own().getInt("request") == 6, "LV5 cooldown ends while physical request fence remains");
            h.assertFalse(f.press(3), "second paid chain cannot start two ticks after last recovery");
        });
        f.post(8, () -> {
            f.aim(f.needle.getBoundingBox().getCenter());
            h.assertTrue(f.own().getInt("request") == 0, "request fence ages only with actual physical ticks");
        });
        f.finish(9);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_effects_health_shield_inventory_and_player_motion_are_not_repaired(GameTestHelper h) {
        Fixture f = new Fixture(h); f.p.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
        f.p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100)); f.p.setRemainingFireTicks(40);
        f.p.fallDistance = 7;
        Snapshot before = f.snapshot(); Vec3 motion = f.p.getDeltaMovement(); int immunity = f.p.invulnerableTime;
        h.assertTrue(f.press(1) && f.press(2), "manual post-flight recovery succeeds with unrelated effects intact");
        f.unchangedPools(before, "item recovery");
        h.assertTrue(f.p.getDeltaMovement().equals(motion) && f.p.invulnerableTime == immunity
                && f.p.fallDistance == 7 && f.p.getRemainingFireTicks() == 40 && f.p.getEffect(MobEffects.POISON).getDuration() == 100
                && f.p.getEffect(MobEffects.WEAKNESS).getDuration() == 100, "effects, fire, motion and immunity retain native values");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery_mark_expiry",timeoutTicks=1210)
    public static void mark_expires_after_twelve_hundred_observed_ticks_without_extension(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.press(1), "mark before full native lifetime observation");
        f.post(1199, () -> h.assertTrue(RecoveryMark.marked(f.p) && RecoveryMark.remainingTicks(f.p) == 1,
                "heartbeat packets never extend original mark lifetime"));
        f.post(1200, () -> {
            h.assertFalse(RecoveryMark.marked(f.p), "tick1200 consumes mark");
            var receipt = RecoveryMark.receipt(f.p);
            h.assertTrue(receipt != null && receipt.phase() == RecoveryMark.CLEARED && receipt.reason().equals("expired")
                    && !receipt.paid() && !receipt.moved(), "expiry is free without automatic item recovery");
        });
        f.finish(1201);
    }

    private record Snapshot(double cp, long deadline, int cooldown, double mastery, String hp, String shield, double overload) {}
    private static void near(GameTestHelper h, double actual, double expected, String why) {
        h.assertTrue(Math.abs(actual - expected) < 1e-7, why + ": " + actual + " != " + expected);
    }
    private static CompoundTag own(int request, int cooldown) {
        CompoundTag tag = new CompoundTag(); tag.putInt("version", 1); tag.putInt("request", request); tag.putInt("cooldown", cooldown); return tag;
    }
    public static final class TeleportHook {
        final Fixture fixture; final Consumer<EntityTeleportEvent> callback; int calls;
        TeleportHook(Fixture fixture, Consumer<EntityTeleportEvent> callback) { this.fixture = fixture; this.callback = callback; }
        @SubscribeEvent public void teleport(EntityTeleportEvent event) {
            if (event.getEntity() == fixture.needle) { calls++; callback.accept(event); }
        }
    }
    public static final class SectionHook {
        final Fixture fixture; final Consumer<EntityEvent.EnteringSection> callback; int calls;
        SectionHook(Fixture fixture, Consumer<EntityEvent.EnteringSection> callback) { this.fixture = fixture; this.callback = callback; }
        @SubscribeEvent public void enter(EntityEvent.EnteringSection event) {
            if (event.getEntity() == fixture.needle) { calls++; callback.accept(event); }
        }
    }
    public static final class PostCheck {
        final Fixture fixture; final int tick; final Runnable action; boolean ran;
        PostCheck(Fixture fixture, int delay, Runnable action) { this.fixture = fixture; tick = fixture.p.server.getTickCount() + delay; this.action = action; }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(ServerTickEvent.Post event) {
            if (ran || event.getServer() != fixture.p.server || event.getServer().getTickCount() != tick) return;
            ran = true;
            try { action.run(); } catch (Throwable failure) { if (fixture.postFailure == null) fixture.postFailure = failure; }
        }
    }
    static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final ServerPlayer p; final EmbeddedChannel channel; final Vec3 anchor, source;
        final DisplacedNeedleEntity needle; final Map<BlockPos, BlockState> changed = new LinkedHashMap<>();
        final Set<ChunkPos> held = new LinkedHashSet<>(); final UUID ticket = UUID.randomUUID();
        final List<Object> hooks = new ArrayList<>(); final List<PostCheck> checks = new ArrayList<>();
        Throwable postFailure; boolean closed;
        Fixture(GameTestHelper h) { this(h, 3); }
        Fixture(GameTestHelper h, int level) {
            this.h = h; anchor = h.absoluteVec(new Vec3(8.5, 5, 8.5)); source = h.absoluteVec(new Vec3(12.5, 5.2, 8.5));
            for (int x = 4; x <= 15; x++) for (int z = 4; z <= 15; z++) block(x, 4, z, Blocks.STONE);
            var server = h.getLevel().getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "RC" + UUID.randomUUID().toString().substring(0, 10)), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie);
            for (Object packet : drain()) if (packet instanceof ClientboundPlayerPositionPacket position)
                p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId()));
            p.setGameMode(GameType.SURVIVAL); p.getAbilities().instabuild = false; p.getAbilities().invulnerable = false; place(anchor);
            h.assertTrue(HighPowerCombat.enroll(p, level), "declared genuine first high-power enrollment");
            var state = AbilityStorage.get(p); state.selectCategory(HighPowerCategories.TELEPORTER); state.setLevel(level);
            state.learn(SafeLandingProbe.ID); state.learn(DisplacedProjection.ID); state.learn(RecoveryMark.ID);
            state.activated = true; state.overloadFine = true;
            h.assertTrue(state.presets.edit(0, 0, RecoveryMark.ID, state::learned), "declared learned native slot323");
            HighPowerResources.initializeBudget(p); epoch(); p.getInventory().clearContent();
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get()));
            needle = DisplacedNeedleRegistry.deploy(p, source, () -> true, () -> true).orElseThrow();
            h.assertTrue(needle.isAdmitted() && !needle.isContactEnabled() && p.getOffhandItem().isEmpty(), "actual original needle transferred into registry custody");
            aim(needle.getBoundingBox().getCenter()); drain();
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { close(); }
            });
            hook(new HighPowerPhaseSidestepRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5));
        }
        void place(Vec3 at) {
            p.moveTo(at.x, at.y, at.z, 0, 0); p.setPose(Pose.STANDING); p.setShiftKeyDown(false); p.setNoGravity(false);
            p.fallDistance = 0; p.setOnGround(true); p.move(MoverType.SELF, new Vec3(0, -.0784, 0));
            p.setDeltaMovement(0, -.0784, 0); p.connection.resetPosition();
            h.assertTrue(p.onGround() && p.position().equals(at), "native downward collision establishes grounded feet");
        }
        void block(int x, int y, int z, Block block) {
            BlockPos pos = h.absolutePos(new BlockPos(x, y, z)); h.assertTrue(h.getBounds().contains(Vec3.atCenterOf(pos)), "fixture block stays inside native template");
            ChunkPos chunk = new ChunkPos(pos);
            if (held.add(chunk)) { h.assertTrue(held.size() <= 9, "bounded resident chunks"); h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticket); h.getLevel().getChunk(chunk.x, chunk.z); }
            changed.putIfAbsent(pos, h.getLevel().getBlockState(pos)); h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        }
        void aim(Vec3 target) {
            Vec3 difference = target.subtract(p.getEyePosition()); p.setYRot((float)Math.toDegrees(Math.atan2(-difference.x, difference.z)));
            p.setXRot((float)-Math.toDegrees(Math.atan2(difference.y, Math.sqrt(difference.x * difference.x + difference.z * difference.z))));
        }
        long epoch() { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        String wire(long input) { return new HighPowerInput.Input(0, input, p.getId(), epoch()).wire(); }
        boolean press(long input) { return RecoveryMark.press(p, wire(input)); }
        CompoundTag budget() { return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY); }
        CompoundTag own() { return p.getPersistentData().getCompound(OWN).copy(); }
        double cp() { return HighPowerResources.cp(p); }
        Snapshot snapshot() {
            var state = AbilityStorage.get(p);
            return new Snapshot(cp(), budget().getLong("next_" + RecoveryMark.ID), state.cooldowns.getOrDefault(RecoveryMark.ID, 0),
                    state.exp(RecoveryMark.ID), HighPowerCombat.healthText(p), HighPowerCombat.shieldText(p), state.overload);
        }
        void same(Snapshot before, String reason) { h.assertTrue(snapshot().equals(before), reason + " preserves CP, cooldown, mastery, HP, shield and overload"); }
        void unchangedPools(Snapshot before, String reason) {
            Snapshot after = snapshot(); h.assertTrue(after.mastery == before.mastery && after.hp.equals(before.hp)
                    && after.shield.equals(before.shield) && after.overload == before.overload, reason + " has no XP, health, shield or overload change");
        }
        void success(long input) {
            var receipt = RecoveryMark.receipt(p);
            h.assertTrue(receipt != null && receipt.input() == input && receipt.phase() == RecoveryMark.RECOVERED && receipt.paid()
                    && receipt.moved() && receipt.reason().isEmpty() && needle.position().equals(anchor)
                    && !RecoveryMark.marked(p), "one exact paid recovery terminal: " + receipt);
        }
        void freeReceipt(String reason) {
            var receipt = RecoveryMark.receipt(p);
            h.assertTrue(receipt != null && !receipt.paid() && !receipt.moved() && receipt.phase() == RecoveryMark.REFUSED
                    && (reason == null ? !receipt.reason().isEmpty() : reason.equals(receipt.reason())), "free terminal refusal: " + receipt);
        }
        List<Object> drain() {
            List<Object> packets = new ArrayList<>(); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks();
            Object packet; while ((packet = channel.readOutbound()) != null) { packets.add(packet); ReferenceCountUtil.release(packet); } return packets;
        }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        TeleportHook onTeleport(Consumer<EntityTeleportEvent> callback) { var hook = new TeleportHook(this, callback); hook(hook); return hook; }
        void post(int ticks, Runnable action) { var check = new PostCheck(this, ticks, action); checks.add(check); hook(check); }
        void finish(int ticks) {
            h.runAfterDelay(ticks, () -> {
                if (postFailure instanceof RuntimeException failure) throw failure;
                if (postFailure instanceof Error failure) throw failure;
                if (postFailure != null) throw new AssertionError(postFailure);
                h.assertTrue(checks.stream().allMatch(check -> check.ran), "all native Post observations executed"); h.succeed();
            });
        }
        @Override public void close() {
            if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); RecoveryMark.remove(p);
            if (!needle.isRemoved()) needle.discard(); HighPowerBracerSession.remove(p); AbilityStorage.remove(p);
            p.server.getPlayerList().remove(p); changed.forEach((pos, state) -> h.getLevel().setBlockAndUpdate(pos, state));
            held.forEach(chunk -> h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticket)); channel.finishAndReleaseAll();
        }
    }
}

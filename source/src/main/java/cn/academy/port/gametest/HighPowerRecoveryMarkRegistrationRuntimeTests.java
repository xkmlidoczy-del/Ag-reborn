package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.RecoveryMarkRules;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** NEW323 registration acceptance through real PlayerList owners, native Q tosses,
 * native merge/pickup and real server ticks. Pose, inventory and ground are declared
 * fixtures; these tests do not claim natural acquisition or a desktop client. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerRecoveryMarkRegistrationRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_recovery_registration";
    private HighPowerRecoveryMarkRegistrationRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=75)
    public static void eight_actual_q_drops_register_ninth_is_free_and_one_recovery_keeps_seven(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.p.getInventory().setItem(f.p.getInventory().selected, new ItemStack(AcademyCraft.NEEDLE.get(), 9));
        List<ItemEntity> drops = new ArrayList<>();
        for (int i = 0; i < 9; i++) drops.add(f.selectedQ(i));
        h.assertTrue(f.p.getMainHandItem().isEmpty(), "nine actual Q actions remove exactly nine inventory needles");
        h.assertTrue(drops.stream().map(Entity::getUUID).distinct().count() == 9, "every native Q creates a different original entity");
        var before = f.base.snapshot();
        f.mark(drops.getFirst());
        for (int i = 1; i < 8; i++) {
            int index = i;
            f.post(i * 8, () -> {
                h.assertTrue(f.base.own().getInt("request") == 0, "actual eight ticks expire the request fence");
                f.mark(drops.get(index));
                h.assertTrue(RecoveryMark.markedCount(f.p) == index + 1, "each original adds exactly one registration");
            });
        }
        f.post(64, () -> {
            h.assertTrue(RecoveryMark.markedCount(f.p) == 8, "eight original Q entities coexist");
            List<Vec3> positions = drops.stream().map(Entity::position).toList();
            var handles = RecoveryMark.handles(f.p);
            f.aim(drops.get(8));
            h.assertFalse(f.tap(), "ninth aimed unregistered original cannot recall an existing registration");
            h.assertTrue(RecoveryMark.markedCount(f.p) == 8 && RecoveryMark.handles(f.p).equals(handles), "cap refusal preserves all eight handles, tokens, anchors and lifetimes");
            f.base.same(before, "eight free registrations and ninth refusal");
            ItemEntity chosen = drops.get(3);
            CompoundTag nativeBefore = nativeTag(chosen); int lifespan = chosen.lifespan;
            f.aim(chosen); double cp = f.base.cp();
            h.assertTrue(f.tap(), "explicit aim recalls one registered original at capacity");
            f.paid(chosen, cp);
            h.assertTrue(RecoveryMark.markedCount(f.p) == 7, "one successful recall consumes only its handle");
            var untouched = handles.stream().filter(mark -> !mark.needleUuid().equals(chosen.getUUID())).toList();
            h.assertTrue(RecoveryMark.handles(f.p).equals(untouched), "other seven retain exact tokens, anchors, sources and lifetimes");
            for (int i = 0; i < drops.size(); i++) if (i != 3)
                h.assertTrue(drops.get(i).position().equals(positions.get(i)), "unselected actual item never moves: " + i);
            sameNativeClock(h, chosen, nativeBefore, lifespan);
            h.assertTrue(chosen.getItem().getCount() == 1 && MagneticItemProvenance.ownedBy(chosen, f.p.getUUID()), "physical count and NEW133 toss ownership survive recovery");
        });
        f.finish(65);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=22)
    public static void different_eligible_aim_adds_and_multiple_handles_require_explicit_selection(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity a = f.q(0), b = f.q(2); f.mark(a);
        var initial = RecoveryMark.handles(f.p); var before = f.base.snapshot(); f.aim(b);
        h.assertFalse(f.tap(), "different eligible aim during request fence cannot fall back to recalling the sole old handle");
        f.freeCurrentReceipt();
        h.assertTrue(RecoveryMark.handles(f.p).equals(initial), "blocked new registration preserves the old original and its fixed lifetime");
        f.base.same(before, "fenced different-aim refusal");
        f.post(8, () -> {
            double cp = f.base.cp(); Vec3 aSource = a.position(), bSource = b.position();
            f.mark(b);
            h.assertTrue(RecoveryMark.markedCount(f.p) == 2 && a.position().equals(aSource), "aiming a different eligible drop adds without recalling the old one");
            near(h, f.base.cp(), cp, "second registration is free");
            f.lookAway(); var handles = RecoveryMark.handles(f.p);
            h.assertFalse(f.tap(), "look-away tap with two handles cannot choose arbitrarily");
            h.assertTrue(RecoveryMark.handles(f.p).equals(handles) && a.position().equals(aSource) && b.position().equals(bSource), "ambiguous aim preserves both original handles and entities");
            f.aim(a); h.assertTrue(f.tap(), "server aim chooses the older original, independent of last registration"); f.paid(a, cp);
            h.assertTrue(RecoveryMark.markedCount(f.p) == 1 && RecoveryMark.handles(f.p).getFirst().needleUuid().equals(b.getUUID())
                    && b.position().equals(bSource), "only selected original is consumed");
        });
        f.finish(9);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void lone_q_registration_keeps_fresh_lookaway_confirmation_and_native_item_clocks(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity item = f.q(1);
        CompoundTag tag = nativeTag(item); tag.putShort("Age", (short) 317); tag.putShort("PickupDelay", (short) 29); item.load(tag);
        item.lifespan = 5432; item.setNoGravity(false); item.setDeltaMovement(.012, -.03, .021);
        f.mark(item); CompoundTag before = nativeTag(item); int lifespan = item.lifespan;
        double cp = f.base.cp(); f.lookAway();
        h.assertTrue(f.tap(), "sole handle preserves the established immediate fresh second-tap confirmation"); f.paid(item, cp);
        sameNativeClock(h, item, before, lifespan);
        h.assertTrue(!item.isNoGravity() && item.getItem().getCount() == 1 && !item.isRemoved(), "recovery retains original ordinary native item");
        h.assertFalse(RecoveryMark.marked(f.p), "single recalled handle is consumed"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void lv5_surviving_handle_cannot_pay_again_until_eighth_physical_tick(GameTestHelper h) {
        Fixture f = new Fixture(h, 5); ItemEntity a = f.q(1), b = f.q(3); f.mark(a);
        f.post(8, () -> {
            f.mark(b); f.aim(a); double cp = f.base.cp();
            h.assertTrue(f.tap(), "first paid recall follows free registration without losing established confirmation behavior"); f.paid(a, cp);
            h.assertTrue(RecoveryMark.markedCount(f.p) == 1 && f.base.own().getInt("request") == 8, "paid action restarts full physical request fence while another registration survives");
        });
        for (int delay = 1; delay < 8; delay++) {
            int elapsed = delay;
            f.post(8 + delay, () -> {
                h.assertTrue(f.base.own().getInt("cooldown") == 0 && f.base.own().getInt("request") == 8 - elapsed,
                        "LV5 one-tick cooldown cannot replace the independent eight-tick paid fence");
                var handles = RecoveryMark.handles(f.p); Vec3 source = b.position(); var before = f.base.snapshot(); f.aim(b);
                h.assertFalse(f.tap(), "another selected original cannot pay at tick " + elapsed + " after prior recovery");
                f.freeCurrentReceipt();
                h.assertTrue(RecoveryMark.handles(f.p).equals(handles) && b.position().equals(source), "paid fence refusal retains remaining handle, token, anchor and current lifetime");
                f.base.same(before, "paid physical fence refusal");
            });
        }
        f.post(16, () -> {
            h.assertTrue(f.base.own().getInt("request") == 0, "eight actual ticks expire the durable paid fence");
            f.aim(b); double cp = f.base.cp(); h.assertTrue(f.tap(), "fresh eighth-tick selection can pay for the remaining original"); f.paid(b, cp);
            h.assertTrue(RecoveryMark.markedCount(f.p) == 0 && f.base.own().getInt("request") == 8, "second success consumes only remaining handle and restarts its own full fence");
        }); f.finish(17);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=75)
    public static void one_existing_311_and_seven_q_originals_share_eight_slots_without_ledger_replacement(GameTestHelper h) {
        Fixture f = new Fixture(h); var identity = f.base.needle.identity(); ItemStack medium = f.base.needle.mediumSnapshot();
        List<ItemEntity> drops = new ArrayList<>(); for (int i = 0; i < 7; i++) drops.add(f.q(i));
        f.base.aim(f.base.needle.getBoundingBox().getCenter()); h.assertTrue(f.tap(), "original admitted NEW311 needle registers");
        for (int i = 0; i < 7; i++) { int index = i; f.post((i + 1) * 8, () -> f.mark(drops.get(index))); }
        f.post(64, () -> {
            h.assertTrue(RecoveryMark.markedCount(f.p) == 8 && DisplacedNeedleRegistry.existing(f.p).orElse(null) == f.base.needle,
                    "one311 plus sevenQ reaches the same eight-slot cap without replacing custody");
            var others = RecoveryMark.handles(f.p).stream().filter(mark -> !mark.needleUuid().equals(f.base.needle.getUUID())).toList();
            f.base.aim(f.base.needle.getBoundingBox().getCenter()); double cp = f.base.cp();
            h.assertTrue(f.tap(), "explicit aim recalls the existing311 original from the mixed set");
            h.assertTrue(f.base.needle.position().equals(f.anchor) && f.base.needle.identity().equals(identity)
                    && ItemStack.matches(f.base.needle.mediumSnapshot(), medium) && !f.base.needle.isContactEnabled(), "original311 generation, exact medium and harmless state survive");
            near(h, f.base.cp(), cp - RecoveryMarkRules.cpCost(HighPowerGrowth.values(f.p).position()), "mixed existing311 recovery pays once");
            h.assertTrue(RecoveryMark.handles(f.p).equals(others), "all seven Q handles are unchanged by311 recovery");
            h.assertFalse(DisplacedNeedleRegistry.retryRefund(f.p), "nativeQ registrations never create or replenish311 refundable escrow");
        }); f.finish(65);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void false_owner_unproven_component_stack_and_copied_identity_are_free_refusals(GameTestHelper h) {
        for (int mode = 0; mode < 7; mode++) {
            Fixture f = new Fixture(h); Fixture foreign = mode == 0 ? new Fixture(h) : null;
            ItemEntity item;
            if (mode == 0) { foreign.base.place(foreign.anchor.add(0, 0, 6)); item = foreign.q(2); item.setTarget(f.p.getUUID()); item.setThrower(f.p); }
            else if (mode == 1) { item = f.raw(); item.setThrower(f.p); item.setTarget(f.p.getUUID()); }
            else {
                item = f.q(2);
                if (mode == 2) item.getItem().setCount(2);
                if (mode == 3) item.getItem().set(DataComponents.CUSTOM_NAME, Component.literal("not a plain needle"));
                if (mode == 4) item.setTarget(UUID.randomUUID());
                if (mode == 5) item.getPersistentData().getCompound(MagneticItemProvenance.KEY).putUUID("owner", UUID.randomUUID());
                if (mode == 6) {
                    ItemEntity copied = new ItemEntity(EntityType.ITEM, h.getLevel()); copied.load(nativeTag(item));
                    copied.setUUID(UUID.randomUUID()); item.discard(); h.getLevel().addFreshEntity(copied); f.items.add(copied); item = copied;
                }
            }
            Vec3 at = item.position(); var before = f.base.snapshot(); f.aim(item);
            h.assertFalse(f.tap(), "unqualified native item cannot register: " + mode);
            h.assertTrue(RecoveryMark.markedCount(f.p) == 0 && item.position().equals(at), "refusal creates no handle or movement");
            f.base.same(before, "ownership/identity/plain-single refusal");
            if (foreign != null) foreign.close(); f.close();
        } h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void actual_control_q_multi_stack_is_ineligible_without_changing_native_toss_count(GameTestHelper h) {
        Fixture f = new Fixture(h); f.p.getInventory().setItem(f.p.getInventory().selected, new ItemStack(AcademyCraft.NEEDLE.get(), 2));
        h.assertTrue(f.p.drop(true), "actual control-Q toss succeeds"); ItemEntity stack = f.findNewDrop(); f.position(stack, 2);
        h.assertTrue(stack.getItem().getCount() == 2 && MagneticItemProvenance.ownedBy(stack, f.p.getUUID()), "native count2 toss remains owned for NEW133");
        var before = f.base.snapshot(); f.aim(stack); h.assertFalse(f.tap(), "count2 is not a NEW323 registration");
        h.assertTrue(stack.getItem().getCount() == 2 && f.p.getMainHandItem().isEmpty(), "refusal does not split or refund control-Q stack");
        f.base.same(before, "control-Q multi-stack refusal");
        f.post(8, () -> {
            stack.getItem().setCount(1); f.aim(stack); var now = f.base.snapshot();
            h.assertFalse(f.tap(), "a later count1 remainder cannot acquire the missing original single-toss nonce");
            h.assertTrue(RecoveryMark.markedCount(f.p) == 0 && MagneticItemProvenance.ownedBy(stack, f.p.getUUID()), "NEW133 proof alone never grants323 registration");
            f.base.same(now, "control-Q remainder refusal");
        }); f.finish(9);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void native_merge_both_survivor_directions_synchronously_retire_both_323_handles(GameTestHelper h) {
        Fixture f = new Fixture(h);
        List<ItemEntity> first = List.of(f.q(0), f.q(4)), second = List.of(f.q(2), f.q(6));
        for (int i = 0; i < 2; i++) {
            int direction = i; ItemEntity a = first.get(i), b = second.get(i);
            ItemEntity survivor = i == 0 ? a : b, donor = i == 0 ? b : a;
            nativeAge(survivor, 40); nativeAge(donor, 12);
            if (i == 0) f.mark(a); else f.post(16, () -> f.mark(a));
            f.post(i * 16 + 8, () -> {
                f.mark(b);
                survivor.setPickUpDelay(11); donor.setPickUpDelay(31);
                int expectedAge = Math.min(survivor.getAge(), donor.getAge()); UUID survivorId = survivor.getUUID();
                double cp = f.base.cp(); merge(survivor, donor);
                h.assertTrue(RecoveryMark.markedCount(f.p) == 0, "native merge invalidates both handles synchronously before any tick: " + direction);
                h.assertTrue(survivor.getUUID().equals(survivorId) && !survivor.isRemoved() && donor.isRemoved()
                        && survivor.getItem().getCount() == 2, "native selected survivor, removal and count are untouched");
                h.assertTrue(MagneticItemProvenance.ownedBy(survivor, f.p.getUUID()) && MagneticItemProvenance.ownedBy(donor, f.p.getUUID()), "NEW133 same-owner proof survives both directions");
                h.assertTrue(survivor.getAge() == expectedAge && nativeTag(survivor).getShort("PickupDelay") == 31, "native minimum age and maximum pickup delay remain exact");
                near(h, f.base.cp(), cp, "merge invalidation is free");
            });
        } f.finish(25);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void merge_first_native_outbound_clear_observes_both_retired_before_count_transfer(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity survivor = f.q(1), donor = f.q(3); f.mark(survivor);
        f.post(8, () -> {
            f.mark(donor); double cp = f.base.cp(); boolean[] merging = {false}; int[] observations = {0}; Throwable[] failure = {null};
            ChannelOutboundHandlerAdapter observer = new ChannelOutboundHandlerAdapter() {
                @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                    if (message instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof RecoveryMark.Visual visual
                            && visual.owner().equals(f.p.getUUID()) && visual.phase() == RecoveryMark.CLEARED && visual.reason().equals("needle_changed")
                            && observations[0]++ == 0) {
                        try {
                            h.assertTrue(merging[0] && f.p.server.isSameThread(), "real native connection sends the first clear synchronously inside the merge call");
                            h.assertTrue(!survivor.isRemoved() && !donor.isRemoved() && survivor.getItem().getCount() == 1 && donor.getItem().getCount() == 1,
                                    "outbound observation precedes native donor removal and count transfer");
                            h.assertTrue(!survivor.getPersistentData().contains(MagneticItemProvenance.RECOVERY_KEY)
                                    && !donor.getPersistentData().contains(MagneticItemProvenance.RECOVERY_KEY), "both scoped toss nonces are already removed before the first clear leaves the connection");
                            h.assertTrue(RecoveryMark.markedCount(f.p) == 0 && visual.handles().isEmpty(), "neither handle retains registration authority in the first outbound snapshot");
                            h.assertTrue(MagneticItemProvenance.ownedBy(survivor, f.p.getUUID()) && MagneticItemProvenance.ownedBy(donor, f.p.getUUID()),
                                    "atomic323 retirement preserves both original NEW133 ownership proofs");
                            f.aim(donor); h.assertFalse(f.tap(), "outbound-send reentry cannot recall the not-yet-merged donor");
                            near(h, f.base.cp(), cp, "outbound-send reentry cannot charge a recovery");
                        } catch (Throwable problem) { failure[0] = problem; }
                    }
                    context.write(message, promise);
                }
            };
            f.base.channel.pipeline().addFirst("recovery_merge_clear_observer", observer);
            try { merging[0] = true; merge(survivor, donor); }
            finally { merging[0] = false; f.base.channel.pipeline().remove(observer); }
            if (failure[0] != null) throw new AssertionError("native outbound merge observation failed", failure[0]);
            h.assertTrue(observations[0] > 0, "real native clear packet traversed the installed outbound handler during merge");
            h.assertTrue(RecoveryMark.markedCount(f.p) == 0 && !survivor.isRemoved() && donor.isRemoved() && survivor.getItem().getCount() == 2,
                    "native merge then finishes with one count2 survivor and no resurrected registrations");
            near(h, f.base.cp(), cp, "full native merge plus outgoing callback stays free");
        }); f.finish(9);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void total_native_candidate_overflow_counts_non_needles_and_preserves_existing_handle(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity item = f.q(2); f.mark(item);
        f.post(8, () -> {
            List<ItemEntity> clutter = new ArrayList<>();
            for (int i = 0; i < 65; i++) {
                ItemEntity other = new ItemEntity(h.getLevel(), item.getX(), item.getY(), item.getZ(), new ItemStack(Items.COBBLESTONE), 0, 0, 0);
                other.setNoGravity(true); h.assertTrue(h.getLevel().addFreshEntity(other), "declared real non-needle candidate enters the native world");
                clutter.add(other); f.items.add(other);
            }
            h.assertTrue(clutter.stream().allMatch(other -> !other.isRemoved() && !other.getItem().is(AcademyCraft.NEEDLE.get())),
                    "all65 overflow entities are live native non-needle items before any merge tick");
            var handles = RecoveryMark.handles(f.p); var before = f.base.snapshot(); Vec3 source = item.position(); f.aim(item);
            h.assertFalse(f.tap(), "total native entity budget refuses even when only one candidate is a needle"); f.freeCurrentReceipt();
            h.assertTrue(RecoveryMark.receipt(f.p).reason().equals("entity_scan_budget") && RecoveryMark.handles(f.p).equals(handles)
                    && item.position().equals(source), "overflow reports its explicit bound and preserves the registered original, anchor and lifetime");
            f.base.same(before, "total candidate overflow"); clutter.forEach(Entity::discard);
            f.aim(item); double cp = f.base.cp(); h.assertTrue(f.tap(), "same genuine registered original remains recoverable after the native clutter is removed"); f.paid(item, cp);
        }); f.finish(9);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void merged_stack_partial_native_pickup_cannot_restore_a_one_count_registration(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity a = f.q(1), b = f.q(3); f.mark(a);
        f.post(8, () -> {
            f.mark(b); merge(a, b);
            h.assertTrue(RecoveryMark.markedCount(f.p) == 0 && a.getItem().getCount() == 2, "both registrations retire at the native merge seam");
            for (int slot = 0; slot < f.p.getInventory().items.size(); slot++) f.p.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE, 64));
            f.p.getInventory().setItem(9, new ItemStack(AcademyCraft.NEEDLE.get(), 63)); a.setNoPickUpDelay(); a.playerTouch(f.p);
            h.assertTrue(!a.isRemoved() && a.getItem().getCount() == 1 && f.p.getInventory().getItem(9).getCount() == 64, "actual native partial pickup leaves one original merged item");
            h.assertTrue(MagneticItemProvenance.ownedBy(a, f.p.getUUID()), "NEW133 ownership still identifies the native leftover");
        });
        f.post(16, () -> {
            f.p.getInventory().setItem(10, ItemStack.EMPTY); f.aim(a); var before = f.base.snapshot();
            h.assertFalse(f.tap(), "merged then partially picked count1 cannot reuse a consumed323 toss nonce");
            h.assertTrue(RecoveryMark.markedCount(f.p) == 0 && a.getItem().getCount() == 1, "no resurrection or mutation after partial pickup"); f.base.same(before, "merged leftover refusal");
        }); f.finish(17);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void native_pickup_and_fresh_q_redrop_create_new_identity_without_inheriting_handle(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity original = f.q(2); UUID oldId = original.getUUID(); f.mark(original);
        original.setNoPickUpDelay(); original.playerTouch(f.p);
        h.assertTrue(original.isRemoved() && RecoveryMark.markedCount(f.p) == 0, "ordinary native pickup retires the exact original registration synchronously");
        h.assertTrue(f.p.getInventory().countItem(AcademyCraft.NEEDLE.get()) == 1, "one actual original returns through native inventory pickup");
        ItemEntity next = f.selectedQ(4);
        h.assertTrue(!next.getUUID().equals(oldId) && RecoveryMark.markedCount(f.p) == 0, "fresh native Q creates new identity and inherits no old handle");
        f.post(8, () -> { f.mark(next); h.assertTrue(RecoveryMark.markedCount(f.p) == 1
                && RecoveryMark.handles(f.p).getFirst().needleUuid().equals(next.getUUID()), "fresh server toss proof permits explicit registration of the new original"); });
        f.finish(9);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void crouch_clears_all_freely_even_airborne_with_existing_fences(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity a = f.q(1), b = f.q(3); f.mark(a);
        f.post(8, () -> {
            f.mark(b); CompoundTag fences = f.base.own(); fences.putInt("cooldown", 40); f.p.getPersistentData().put("academy:recovery_mark_fences", fences.copy());
            f.p.setOnGround(false); f.p.fallDistance = 7; f.p.setShiftKeyDown(true); var before = f.base.snapshot();
            Vec3 first = a.position(), second = b.position(); h.assertTrue(f.tap(), "authenticated crouch edge clears the entire registration set");
            h.assertTrue(RecoveryMark.markedCount(f.p) == 0 && f.base.own().equals(fences)
                    && a.position().equals(first) && b.position().equals(second) && f.p.fallDistance == 7, "free clear retains native items, fall state and physical fences");
            f.base.same(before, "clear all");
        }); f.finish(9);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void native_cancel_redirect_and_reentry_are_free_or_exactly_one_selected_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity a = f.q(1), b = f.q(3); f.mark(a);
        f.post(8, () -> {
            f.mark(b); var other = RecoveryMark.handles(f.p).stream().filter(mark -> mark.needleUuid().equals(b.getUUID())).toList();
            var before = f.base.snapshot(); TeleportHook hook = f.onTeleport(a, event -> {
                h.assertFalse(f.tap(), "native event cannot reenter controller for another payment");
                f.p.getPersistentData().putInt("academy:registration_callback", 19); event.setCanceled(true);
            });
            f.aim(a); h.assertFalse(f.tap(), "native cancellation refuses selected Q recovery");
            h.assertTrue(hook.calls == 1 && RecoveryMark.handles(f.p).equals(other), "one canceled event retires only selected handle and leaves the other unchanged");
            f.base.same(before, "canceled recovery");
            h.assertTrue(f.p.getPersistentData().getInt("academy:registration_callback") == 19, "unrelated event mutation is retained");
            TeleportHook redirect = f.onTeleport(b, event -> event.setTargetX(event.getTargetX() + .01));
            f.aim(b); h.assertFalse(f.tap(), "native redirected Q recovery also refuses before debit");
            h.assertTrue(redirect.calls == 1 && RecoveryMark.markedCount(f.p) == 0, "redirect retires only its selected handle"); f.base.same(before, "redirected recovery");
        }); f.finish(9);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_q_event_reentry_cannot_duplicate_payment_or_input(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity item = f.q(2); f.mark(item); double cp = f.base.cp();
        TeleportHook hook = f.onTeleport(item, event -> {
            h.assertFalse(f.tap(), "same native controller reentry is bounded");
            near(h, f.base.cp(), cp, "native event runs before own debit");
        });
        h.assertTrue(f.tap(), "one outer native Q recovery commits after rejected reentry"); f.paid(item, cp);
        h.assertTrue(hook.calls == 1, "one event for the one paid relocation");
        h.assertFalse(RecoveryMark.press(f.p, f.base.wire(2)), "committed input cannot replay");
        near(h, f.base.cp(), cp - RecoveryMarkRules.cpCost(HighPowerGrowth.values(f.p).position()), "replay adds no second payment"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void paid_native_item_section_change_keeps_uncertain_receipt_without_refund_or_replay(GameTestHelper h) {
        for (int mode = 0; mode < 3; mode++) {
            Fixture f = new Fixture(h); ItemEntity item = f.q(2); f.base.block(24, 4, 8, Blocks.STONE);
            item.setPos(f.anchor.add(16, .2, 0)); f.mark(item); double cp = f.base.cp(); UUID id = item.getUUID(); int selected = mode;
            SectionHook hook = new SectionHook(item, event -> {
                var receipt = RecoveryMark.receipt(f.p);
                h.assertTrue(receipt != null && receipt.input() == 2 && receipt.paid() && receipt.reason().equals("transport_unconfirmed"), "paid uncertain receipt precedes real native section callback");
                near(h, f.base.cp(), cp - RecoveryMarkRules.cpCost(HighPowerGrowth.values(f.p).position()), "debit precedes native section entry");
                h.assertFalse(f.tap(), "section callback cannot pay a reentrant registration");
                if (selected == 0) f.p.setPos(f.p.position().add(.01, 0, 0));
                else if (selected == 1) RecoveryMark.cancelActive(f.p);
                else h.assertTrue(RecoveryMark.abort(f.p, f.base.wire(++f.input)), "fresh authenticated abort can retire the selected handle");
            }); f.base.hook(hook);
            h.assertFalse(f.tap(), "owner cancellation after native item relocation cannot claim confirmed recovery");
            var receipt = RecoveryMark.receipt(f.p);
            h.assertTrue(hook.calls == 1 && receipt != null && receipt.input() == 2 && receipt.paid() && receipt.moved()
                    && receipt.reason().equals("transport_unconfirmed") && receipt.phase() == RecoveryMark.REFUSED, "paid original input and observed native movement remain historical");
            h.assertTrue(item.position().equals(f.anchor) && item.getUUID().equals(id) && !item.isRemoved()
                    && RecoveryMark.markedCount(f.p) == 0, "same physical item remains relocated with selected handle consumed");
            h.assertFalse(RecoveryMark.press(f.p, f.base.wire(2)), "paid original input remains retired");
            near(h, f.base.cp(), cp - RecoveryMarkRules.cpCost(HighPowerGrowth.values(f.p).position()), "postpayment uncertainty has no refund or replay debit"); f.close();
        } h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_q_zero_cp_full_inventory_noop_range_and_route_refusals_are_free(GameTestHelper h) {
        for (int mode = 0; mode < 6; mode++) {
            Fixture f = new Fixture(h); ItemEntity item = f.q(0);
            if (mode == 0) f.base.budget().putDouble("cp", 0);
            f.mark(item);
            switch (mode) {
                case 0 -> { }
                case 1 -> { for (int slot = 0; slot < f.p.getInventory().items.size(); slot++) f.p.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64)); f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE, 64)); }
                case 2 -> item.setPos(f.anchor);
                case 3 -> item.setPos(f.anchor.add(24.01, .2, 0));
                case 4 -> f.base.block(8, 4, 8, Blocks.AIR);
                default -> { f.p.setOnGround(false); f.p.fallDistance = 2; }
            }
            Vec3 source = item.position(); var before = f.base.snapshot(); f.lookAway();
            h.assertFalse(f.tap(), "unsafe native item recovery refuses: " + mode);
            h.assertTrue(item.position().equals(source) && !item.isRemoved(), "refusal leaves the same original at its current source");
            f.base.same(before, "unpaid native item refusal"); f.close();
        } h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_q_one_partial_inventory_stack_permits_exact_original_recovery(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity item = f.q(2); f.mark(item);
        for (int slot = 0; slot < f.p.getInventory().items.size(); slot++) f.p.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE, 64));
        f.p.getInventory().setItem(9, new ItemStack(AcademyCraft.NEEDLE.get(), 63)); double cp = f.base.cp();
        h.assertTrue(f.tap(), "one actual partial inventory stack has capacity for the registered single original"); f.paid(item, cp);
        h.assertTrue(f.p.getInventory().getItem(9).getCount() == 63 && item.getItem().getCount() == 1 && !item.isRemoved(),
                "recovery relocates the original and does not synthesize an inventory insertion"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void registered_q_nonce_owner_stack_age_and_lifespan_changes_refuse_without_debit(GameTestHelper h) {
        for (int mode = 0; mode < 8; mode++) {
            Fixture f = new Fixture(h); ItemEntity item = f.q(2); exactAge(item, 100); f.mark(item);
            switch (mode) {
                case 0 -> item.getPersistentData().getCompound(MagneticItemProvenance.RECOVERY_KEY).putUUID("nonce", UUID.randomUUID());
                case 1 -> item.setThrower(f.base.needle);
                case 2 -> item.setTarget(f.p.getUUID());
                case 3 -> item.setItem(item.getItem().copy());
                case 4 -> exactAge(item, 99);
                case 5 -> exactAge(item, -32768);
                case 6 -> exactAge(item, item.lifespan);
                default -> item.lifespan++;
            }
            var before = f.base.snapshot(); Vec3 source = item.position(); CompoundTag nativeBefore = nativeTag(item); int lifespan = item.lifespan;
            f.lookAway(); h.assertFalse(f.tap(), "changed original binding cannot pay a recovery: " + mode);
            h.assertTrue(RecoveryMark.markedCount(f.p) == 0 && item.position().equals(source) && !item.isRemoved(), "invalid selected handle retires without moving or replacing its native item");
            sameNativeClock(h, item, nativeBefore, lifespan); f.base.same(before, "changed metadata refusal"); f.close();
        } h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_q_infinite_negative_and_expired_ages_cannot_register(GameTestHelper h) {
        for (int age : new int[]{-1, -32768, 6000}) {
            Fixture f = new Fixture(h); ItemEntity item = f.q(2); exactAge(item, age); var before = f.base.snapshot(); f.aim(item);
            h.assertFalse(f.tap(), "nonordinary native age cannot receive a registration: " + age);
            h.assertTrue(RecoveryMark.markedCount(f.p) == 0 && item.getAge() == age && !item.isRemoved(), "refusal does not repair, delete or reset native item age");
            f.base.same(before, "nonordinary age refusal"); f.close();
        } h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_item_callback_clock_motion_and_nonce_mutations_are_preserved_and_unpaid(GameTestHelper h) {
        for (int mode = 0; mode < 5; mode++) {
            Fixture f = new Fixture(h); ItemEntity item = f.q(2); f.mark(item); var before = f.base.snapshot(); Vec3 source = item.position(); int selected = mode;
            TeleportHook hook = f.onTeleport(item, event -> {
                switch (selected) {
                    case 0 -> item.setPickUpDelay(77);
                    case 1 -> exactAge(item, item.getAge() + 1);
                    case 2 -> item.lifespan++;
                    case 3 -> item.setDeltaMovement(.01, 0, 0);
                    default -> item.getPersistentData().getCompound(MagneticItemProvenance.RECOVERY_KEY).putUUID("nonce", UUID.randomUUID());
                }
            });
            h.assertFalse(f.tap(), "native callback changed synchronous item snapshot and cannot commit: " + mode);
            h.assertTrue(hook.calls == 1 && item.position().equals(source) && !item.isRemoved(), "one event and no own native movement after callback mutation");
            if (mode == 0) h.assertTrue(nativeTag(item).getShort("PickupDelay") == 77, "foreign pickup-delay change is not rolled back");
            if (mode == 1) h.assertTrue(item.getAge() == 1, "foreign native aging is not rolled back");
            if (mode == 3) h.assertTrue(item.getDeltaMovement().equals(new Vec3(.01, 0, 0)), "foreign motion is not rolled back");
            f.base.same(before, "native callback mutation"); f.close();
        } h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_q_logout_unload_epoch_and_preset_lifecycle_cannot_restore_registrations(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) {
            Fixture f = new Fixture(h); ItemEntity item = f.q(2); f.mark(item); CompoundTag fences = f.base.own(); var before = f.base.snapshot();
            if (mode == 0) NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.p));
            else if (mode == 1) item.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            else if (mode == 2) { LegacySingleKeyOwnerEpoch.forget(f.p); f.base.epoch(); h.assertFalse(f.tap(), "new owner epoch cannot confirm old registration"); }
            else { AbilityStorage.get(f.p).presets.edit(1, 1, RecoveryMark.ID, AbilityStorage.get(f.p)::learned); h.assertFalse(f.tap(), "changed preset revision cannot confirm an old registration"); }
            h.assertTrue(RecoveryMark.markedCount(f.p) == 0 && f.base.own().equals(fences), "native lifecycle clears ephemeral registration and retains physical fences: " + mode);
            f.base.same(before, "native lifecycle cleanup"); f.close();
        } h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_recovery_registration_expiry",timeoutTicks=1220)
    public static void staggered_q_handles_expire_independently_after_twelve_hundred_real_ticks(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity a = f.q(1), b = f.q(3); f.mark(a);
        f.post(8, () -> f.mark(b));
        f.post(1199, () -> {
            var handles = RecoveryMark.handles(f.p);
            h.assertTrue(handles.size() == 2 && remaining(handles, a) == 1 && remaining(handles, b) == 9, "second registration and heartbeat never extend first handle's fixed lifetime");
            for (int i = 0; i < 1500; i++) RecoveryMark.tick(f.p);
            h.assertTrue(RecoveryMark.handles(f.p).equals(handles), "repeated same-tick observers do not age either handle");
        });
        f.post(1200, () -> h.assertTrue(RecoveryMark.markedCount(f.p) == 1 && remaining(RecoveryMark.handles(f.p), b) == 8,
                "tick1200 expires only first original, preserving later independent lifetime"));
        f.post(1208, () -> h.assertTrue(RecoveryMark.markedCount(f.p) == 0 && !a.isRemoved() && !b.isRemoved()
                && a.getAge() >= 1200 && b.getAge() >= 1200, "last handle expires with native originals and their uninterrupted item ages intact"));
        f.finish(1209);
    }

    private static int remaining(List<RecoveryMark.HandleVisual> handles, ItemEntity item) {
        return handles.stream().filter(handle -> handle.needleUuid().equals(item.getUUID())).findFirst().orElseThrow().remainingTicks();
    }
    private static CompoundTag nativeTag(ItemEntity item) { return item.saveWithoutId(new CompoundTag()); }
    private static void nativeAge(ItemEntity item, int age) { CompoundTag tag = nativeTag(item); tag.putShort("Age", (short) age); item.load(tag); }
    /** Declared native-age fault injection without replacing the ItemStack or its toss evidence. */
    private static void exactAge(ItemEntity item, int age) {
        try { var field = ItemEntity.class.getDeclaredField("age"); field.setAccessible(true); field.setInt(item, age); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void sameNativeClock(GameTestHelper h, ItemEntity item, CompoundTag before, int lifespan) {
        CompoundTag after = nativeTag(item);
        h.assertTrue(before.getShort("Age") == after.getShort("Age") && before.getShort("PickupDelay") == after.getShort("PickupDelay")
                && item.lifespan == lifespan, "relocation never refreshes native item age, pickup delay or lifespan");
    }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-7, why + ": " + actual + " != " + expected); }
    private static void merge(ItemEntity survivor, ItemEntity donor) {
        try { Method method = ItemEntity.class.getDeclaredMethod("merge", ItemEntity.class, ItemStack.class, ItemEntity.class, ItemStack.class);
            method.setAccessible(true); method.invoke(null, survivor, survivor.getItem(), donor, donor.getItem());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    public static final class TeleportHook {
        final Entity target; final Consumer<EntityTeleportEvent> callback; int calls;
        TeleportHook(Entity target, Consumer<EntityTeleportEvent> callback) { this.target = target; this.callback = callback; }
        @SubscribeEvent public void teleport(EntityTeleportEvent event) { if (event.getEntity() == target) { calls++; callback.accept(event); } }
    }
    public static final class SectionHook {
        final Entity target; final Consumer<EntityEvent.EnteringSection> callback; int calls;
        SectionHook(Entity target, Consumer<EntityEvent.EnteringSection> callback) { this.target = target; this.callback = callback; }
        @SubscribeEvent public void section(EntityEvent.EnteringSection event) { if (event.getEntity() == target) { calls++; callback.accept(event); } }
    }
    private static final class Fixture implements AutoCloseable {
        final HighPowerRecoveryMarkRuntimeTests.Fixture base; final GameTestHelper h; final ServerPlayer p; final Vec3 anchor;
        final List<ItemEntity> items = new ArrayList<>(); long input; boolean closed;
        Fixture(GameTestHelper h) { this(h, 3); }
        Fixture(GameTestHelper h, int level) {
            this.h = h; base = new HighPowerRecoveryMarkRuntimeTests.Fixture(h, level); p = base.p; anchor = base.anchor;
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { close(); }
            });
        }
        ItemEntity q(int index) { p.getInventory().setItem(p.getInventory().selected, new ItemStack(AcademyCraft.NEEDLE.get())); return selectedQ(index); }
        ItemEntity selectedQ(int index) {
            int before = p.getInventory().getSelected().getCount(); h.assertTrue(p.drop(false), "actual ServerPlayer Q reaches native ItemTossEvent");
            h.assertTrue(p.getInventory().getSelected().getCount() == before - 1, "actual Q removes exactly one selected inventory item");
            ItemEntity item = findNewDrop(); h.assertTrue(item.getItem().is(AcademyCraft.NEEDLE.get()) && item.getItem().getCount() == 1
                    && item.getTarget() == null && MagneticItemProvenance.ownedBy(item, p.getUUID()), "genuine ordinary Q has one original needle, null pickup target and server ownership");
            position(item, index); return item;
        }
        ItemEntity findNewDrop() {
            var matches = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(2),
                    item -> !items.contains(item) && MagneticItemProvenance.ownedBy(item, p.getUUID()));
            h.assertTrue(matches.size() == 1, "one newly produced actual Q entity"); ItemEntity item = matches.getFirst(); items.add(item); return item;
        }
        void position(ItemEntity item, int index) {
            double angle = index == 8 ? Math.PI / 4 : Math.PI / 8 + index * Math.PI / 4;
            double radius = index == 8 ? 5.2 : 4.3;
            item.setPos(anchor.add(Math.cos(angle) * radius, .2, Math.sin(angle) * radius));
            item.setDeltaMovement(Vec3.ZERO); item.setNoGravity(true);
        }
        ItemEntity raw() { ItemEntity item = new ItemEntity(h.getLevel(), anchor.x, anchor.y + .2, anchor.z,
                new ItemStack(AcademyCraft.NEEDLE.get()), 0, 0, 0); h.getLevel().addFreshEntity(item); items.add(item); position(item, 2); return item; }
        void aim(Entity item) { base.aim(item.getBoundingBox().getCenter()); }
        void lookAway() { base.aim(p.getEyePosition().add(0, 10, 0)); }
        boolean tap() { return base.press(++input); }
        void mark(ItemEntity item) {
            aim(item); h.assertTrue(tap(), "server aim registers this genuine original: " + item.getUUID());
            var receipt = RecoveryMark.receipt(p); h.assertTrue(receipt != null && receipt.phase() == RecoveryMark.MARKED && !receipt.paid()
                    && !receipt.moved() && receipt.needleUuid().equals(item.getUUID()) && receipt.destination().equals(anchor), "free registration records exact actual item and frozen grounded feet");
        }
        void paid(ItemEntity item, double cp) {
            var receipt = RecoveryMark.receipt(p); h.assertTrue(receipt != null && receipt.paid() && receipt.moved()
                    && receipt.phase() == RecoveryMark.RECOVERED && receipt.needleUuid().equals(item.getUUID()) && item.position().equals(anchor), "one selected original reaches its recorded anchor");
            near(h, base.cp(), cp - RecoveryMarkRules.cpCost(HighPowerGrowth.values(p).position()), "one actual current-growth CP debit");
        }
        void freeCurrentReceipt() {
            var receipt = RecoveryMark.receipt(p);
            h.assertTrue(receipt != null && receipt.input() == input && receipt.phase() == RecoveryMark.REFUSED && !receipt.paid() && !receipt.moved(),
                    "refused fresh input receives an explicit free terminal receipt instead of waiting for client timeout");
        }
        TeleportHook onTeleport(Entity item, Consumer<EntityTeleportEvent> callback) { var hook = new TeleportHook(item, callback); base.hook(hook); return hook; }
        void post(int delay, Runnable action) { base.post(delay, action); }
        void finish(int delay) { base.finish(delay); }
        @Override public void close() { if (closed) return; closed = true; items.forEach(item -> { if (!item.isRemoved()) item.discard(); }); base.close(); }
    }
}

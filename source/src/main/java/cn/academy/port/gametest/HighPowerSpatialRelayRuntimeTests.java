package cn.academy.port.gametest;

import cn.academy.port.highpower.core.SpatialRelayRules;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.ContinuousHighPowerGrowth;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW343 acceptance with connected native owners, real Q/control-Q drops, native
 * merge callbacks and actual server ticks. Inventory, floors and initial item poses
 * are declared fixtures; the controller must retain the very same world entities. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerSpatialRelayRuntimeTests {
    private static final String TEMPLATE = "highpower_spatial_relay_empty", BATCH = "highpower_spatial_relay";
    private static final String ID = "spatial_relay";
    private static final TicketType<UUID> TICKET = TicketType.create("academy_spatial_relay_test", UUID::compareTo);
    private static final Item[] DISTINCT = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.DIAMOND,
            Items.EMERALD, Items.REDSTONE, Items.LAPIS_LAZULI, Items.COAL, Items.CHARCOAL, Items.QUARTZ,
            Items.AMETHYST_SHARD, Items.FLINT, Items.STICK, Items.BONE, Items.STRING, Items.FEATHER, Items.CLAY_BALL};
    private HighPowerSpatialRelayRuntimeTests() {}

    /** This48×8×32 arena contains every declared source, route and restored floor cell. */
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
    public static void native_q_control_q_issue_distinct_exact_owner_uuid_count_nonces(GameTestHelper h) {
        Fixture f = new Fixture(h); f.p.getInventory().setItem(f.p.getInventory().selected, new ItemStack(Items.IRON_INGOT, 9));
        ItemEntity one = f.selectedDrop(false); h.assertTrue(f.p.getMainHandItem().getCount() == 8, "real Q removes exactly one selected item");
        ItemEntity eight = f.selectedDrop(true); h.assertTrue(f.p.getMainHandItem().isEmpty(), "real control-Q removes the complete native remainder");
        var a = SpatialRelayProvenance.proof(one); var b = SpatialRelayProvenance.proof(eight);
        h.assertTrue(a != null && b != null && a.owner().equals(f.p.getUUID()) && b.owner().equals(f.p.getUUID())
                && a.entity().equals(one.getUUID()) && b.entity().equals(eight.getUUID()) && a.count() == 1 && b.count() == 8
                && !a.nonce().equals(b.nonce()), "independent genuine Q/control-Q get fresh owner/entity/count-bound evidence");
        h.assertTrue(one.getTarget() == null && eight.getTarget() == null && MagneticItemProvenance.ownedBy(eight, f.p.getUUID()),
                "ordinary null pickup ownership and NEW133 proof remain native");
        h.assertTrue(SpatialRelayMedium.capture(f.p, one) != null && SpatialRelayMedium.capture(f.p, eight) != null,
                "both genuine default-component stack sizes bind without splitting");
        ItemEntity copied = new ItemEntity(EntityType.ITEM, h.getLevel()); copied.load(nativeTag(eight)); copied.setUUID(UUID.randomUUID());
        h.assertTrue(SpatialRelayProvenance.proof(copied) == null && SpatialRelayMedium.capture(f.p, copied) == null,
                "NBT copied onto another UUID grants no relay authority");
        ItemEntity unknown = f.raw(Items.IRON_INGOT, 3, f.pointA); unknown.setThrower(f.p); unknown.setTarget(f.p.getUUID());
        h.assertTrue(SpatialRelayProvenance.proof(unknown) == null && SpatialRelayMedium.capture(f.p, unknown) == null,
                "native thrower and pickup target cannot invent a missing toss nonce"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void native_default_components_are_exact_and_decorated_or_container_tosses_never_bind(GameTestHelper h) {
        Fixture f = new Fixture(h);
        ItemEntity ordinary = f.drop(Items.IRON_INGOT, 7, f.pointA);
        h.assertTrue(ordinary.getItem().getComponents().equals(new ItemStack(Items.IRON_INGOT).getComponents())
                && SpatialRelayMedium.capture(f.p, ordinary) != null, "an exact vanilla default-components stack binds");
        for (int mode = 0; mode < 4; mode++) {
            ItemStack stack = new ItemStack(mode == 3 ? Items.SHULKER_BOX : Items.IRON_INGOT, 1);
            if (mode == 0) stack.set(DataComponents.CUSTOM_NAME, Component.literal("decorated relay refusal"));
            if (mode == 1) stack.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            if (mode == 2) stack.remove(DataComponents.MAX_STACK_SIZE);
            f.p.getInventory().setItem(f.p.getInventory().selected, stack); ItemEntity item = f.selectedDrop(true);
            h.assertTrue(SpatialRelayMedium.capture(f.p, item) == null, "nondefault or container original is refused: " + mode);
            h.assertTrue(item.getItem().getCount() == 1 && !item.isRemoved(), "eligibility check leaves native item untouched");
        } h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void another_connected_players_genuine_q_cannot_be_laundered_by_thrower_or_pickup_target(GameTestHelper h) {
        Fixture owner = new Fixture(h), other = new Fixture(h); ItemEntity item = other.drop(Items.IRON_INGOT, 4, other.pointA);
        h.assertTrue(SpatialRelayProvenance.proof(item).owner().equals(other.p.getUUID()), "other native owner has genuine independent toss evidence");
        item.setThrower(owner.p); item.setTarget(owner.p.getUUID());
        h.assertTrue(SpatialRelayMedium.capture(owner.p, item) == null && item.getItem().getCount() == 4 && !item.isRemoved(),
                "changing ordinary native metadata does not transfer scoped owner authority or physical stock"); other.close(); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void both_native_merge_directions_retire_relay_evidence_before_full_or_partial_transfer(GameTestHelper h) {
        Fixture f = new Fixture(h);
        for (boolean reverse : new boolean[]{false, true}) for (boolean partial : new boolean[]{false, true}) {
            ItemEntity first = f.drop(Items.IRON_INGOT, partial ? 60 : 3, f.pointA);
            ItemEntity second = f.drop(Items.IRON_INGOT, partial ? 10 : 7, f.pointA.add(1, 0, 0));
            ItemEntity survivor = reverse ? second : first, donor = reverse ? first : second;
            SpatialRelayMedium a = SpatialRelayMedium.capture(f.p, first), b = SpatialRelayMedium.capture(f.p, second);
            h.assertTrue(a != null && b != null, "both genuine native stacks are independently bound before merge");
            exactAge(survivor, 40); exactAge(donor, 12); survivor.setPickUpDelay(11); donor.setPickUpDelay(31);
            UUID survivorId = survivor.getUUID(); merge(survivor, donor);
            h.assertTrue(!a.valid() && !b.valid() && SpatialRelayProvenance.proof(first) == null && SpatialRelayProvenance.proof(second) == null,
                    "native HEAD invalidates both scoped nonces regardless of survivor or partial transfer");
            h.assertTrue(survivor.getUUID().equals(survivorId) && survivor.getItem().getCount() == (partial ? 64 : 10)
                    && (partial ? !donor.isRemoved() && donor.getItem().getCount() == 6 : donor.isRemoved()), "native identity, cap, removal and total count are unchanged");
            h.assertTrue(survivor.getAge() == 12 && nativeTag(survivor).getShort("PickupDelay") == 31
                    && MagneticItemProvenance.ownedBy(survivor, f.p.getUUID()), "native minimum age, maximum delay and original NEW133 proof remain exact");
            first.discard(); second.discard();
        } h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void bound_owner_nonce_stack_reference_count_native_clocks_and_target_cannot_mutate(GameTestHelper h) {
        Fixture f = new Fixture(h);
        for (int mode = 0; mode < 10; mode++) {
            ItemEntity item = f.drop(Items.IRON_INGOT, 3, f.pointA); exactAge(item, 100);
            SpatialRelayMedium medium = SpatialRelayMedium.capture(f.p, item); h.assertTrue(medium != null, "declared genuine binding");
            switch (mode) {
                case 0 -> item.getPersistentData().getCompound(SpatialRelayProvenance.KEY).putUUID("nonce", UUID.randomUUID());
                case 1 -> item.getPersistentData().getCompound(SpatialRelayProvenance.KEY).putUUID("owner", UUID.randomUUID());
                case 2 -> item.getItem().setCount(2);
                case 3 -> item.setItem(item.getItem().copy());
                case 4 -> item.getItem().set(DataComponents.CUSTOM_NAME, Component.literal("changed after bind"));
                case 5 -> item.setTarget(f.p.getUUID());
                case 6 -> exactAge(item, 99);
                case 7 -> item.lifespan++;
                case 8 -> item.setThrower(f.raw(Items.STICK, 1, f.pointA.add(1, 0, 0)));
                default -> item.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            }
            h.assertFalse(medium.valid(), "changed original cannot retain a queued handle: " + mode);
            h.assertTrue(item.getItem().getCount() == (mode == 2 ? 2 : 3), "validation never rebuilds, splits or refunds physical stock"); item.discard();
        } h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void three_visible_marks_relay_same_control_q_original_on_three_distinct_server_ticks(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity item = f.drop(Items.IRON_INGOT, 12, f.pointA.add(.75, 0, 0));
        exactAge(item, 317); item.lifespan = 5432; item.setPickUpDelay(200); UUID id = item.getUUID();
        MoveObserver observer = f.observe(item); f.ready(() -> {
            List<ItemStack> inventory = inventory(f.p); h.assertTrue(f.tap(), "fourth independent short release queues genuine stack");
            for (int leg = 1; leg <= 3; leg++) { int expected = leg;
                f.post(leg, () -> { h.assertTrue(observer.calls == expected, "exactly one positive original-item leg per actual server tick");
                    Vec3 destination = expected == 1 ? f.a : expected == 2 ? f.b : f.c;
                    h.assertTrue(item.position().equals(destination) && item.getUUID().equals(id) && item.getItem().getCount() == 12
                            && h.getLevel().getEntity(id) == item, "gather-to-A, A-to-B, B-to-C each retain the original native entity");
                    observer.assertLast(); sameInventory(h, f.p, inventory); int completed = SpatialRelay.receipt(f.p).completed(); double cp = f.cp();
                    SpatialRelay.tick(f.p); SpatialRelay.tick(f.p); near(h, f.cp(), cp, "same-tick duplicate callbacks cannot debit another leg");
                    h.assertTrue(SpatialRelay.receipt(f.p).completed() == completed && observer.calls == expected, "same-tick duplicates cannot advance queue"); });
            }
            f.post(4, () -> h.assertFalse(SpatialRelay.active(f.p), "completed queue retires without inventory insertion or refund"));
        }); f.finish(17);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=23)
    public static void zero_length_gather_is_free_and_full_inventory_does_not_create_a_pickup(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            ItemEntity item = f.drop(Items.GOLD_INGOT, 9, f.a); for (int i = 0; i < f.p.getInventory().getContainerSize(); i++) f.p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            List<ItemStack> inventory = inventory(f.p); MoveObserver observer = f.observe(item); h.assertTrue(f.tap(), "full inventory still permits physical relay");
            f.post(1, () -> { h.assertTrue(observer.calls == 1 && item.position().equals(f.b), "zero-length initial leg is skipped without event or payment"); observer.assertLast(); });
            f.post(2, () -> { h.assertTrue(observer.calls == 2 && item.position().equals(f.c) && item.getItem().getCount() == 9, "only the two positive relay legs are paid"); observer.assertLast(); sameInventory(h, f.p, inventory); });
            f.post(3, () -> { h.assertFalse(SpatialRelay.active(f.p), "two positive legs finish zero-prefix route"); item.playerTouch(f.p);
                h.assertTrue(!item.isRemoved() && item.getItem().getCount() == 9, "actual native pickup delay and full inventory remain authoritative"); });
        }); f.finish(16);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=72)
    public static void sixteen_originals_have_at_most_forty_eight_positive_legs_and_exact_count_conservation(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            List<ItemEntity> items = new ArrayList<>(); List<MoveObserver> observations = new ArrayList<>();
            for (int i = 0; i < 16; i++) { ItemEntity item = f.drop(DISTINCT[i], i + 1, f.a.add(.75, 0, 0)); items.add(item); observations.add(f.observe(item)); }
            List<ItemStack> inventory = inventory(f.p); h.assertTrue(f.tap(), "sixteen genuine original entities form one finite queue");
            for (int tick = 1; tick <= 48; tick++) { int expected = tick; f.post(tick, () -> {
                h.assertTrue(observations.stream().mapToInt(o -> o.calls).sum() == expected, "one total positive leg per physical tick across all16 originals");
                observations.stream().filter(o -> o.tick == f.p.server.getTickCount()).forEach(MoveObserver::assertLast);
                sameInventory(h, f.p, inventory); int total = items.stream().mapToInt(i -> i.getItem().getCount()).sum(); h.assertValueEqual(total, 136, "exact count conservation through every leg");
            }); }
            f.post(49, () -> { h.assertFalse(SpatialRelay.active(f.p), "finite48-leg queue finishes");
                for (ItemEntity item : items) h.assertTrue(!item.isRemoved() && item.position().equals(f.c) && h.getLevel().getEntity(item.getUUID()) == item, "every original arrives exactly at C"); });
        }); f.finish(62);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=22)
    public static void seventeenth_eligible_original_refuses_whole_queue_without_trimming_or_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> { List<ItemEntity> items = new ArrayList<>();
            for (int i = 0; i < 17; i++) items.add(f.drop(DISTINCT[i], 1, f.a.add(.75, 0, 0)));
            double cp = f.cp(); List<Vec3> positions = items.stream().map(Entity::position).toList();
            h.assertFalse(f.tap(), "seventeen eligible native originals refuse rather than silently truncate"); near(h, f.cp(), cp, "oversized gather is free");
            h.assertFalse(SpatialRelay.active(f.p), "oversized gather creates no partial active queue");
            for (int i = 0; i < items.size(); i++) h.assertTrue(items.get(i).position().equals(positions.get(i)) && items.get(i).getItem().getCount() == 1, "all original stock remains at source");
        }); f.finish(13);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void canceled_redirected_or_reentrant_native_event_never_debits_or_replays(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity item = f.drop(Items.IRON_INGOT, 4, f.pointA.add(.75, 0, 0));
        f.ready(() -> { Vec3 source = item.position(); double[] cp = {0}; TeleportHook hook = f.onTeleport(item, event -> {
                cp[0] = f.cp(); h.assertFalse(f.tap(), "native event cannot reenter relay preparation or payment");
                event.setCanceled(true); f.p.getPersistentData().putInt("academy:relay_external_sentinel", 7);
            }); h.assertTrue(f.tap(), "queue is admitted before native cancellation callback"); String wire = f.wire(f.inputs);
            f.post(1, () -> { h.assertTrue(hook.calls == 1 && item.position().equals(source) && item.getItem().getCount() == 4, "cancel runs once and leaves same physical original unmoved");
                near(h, f.cp(), cp[0], "precommit native cancellation is free"); h.assertFalse(SpatialRelay.active(f.p), "canceled handle is retired");
                h.assertFalse(SpatialRelay.release(f.p, wire) || SpatialRelay.press(f.p, wire), "original authenticated queue release cannot replay");
                h.assertTrue(f.p.getPersistentData().getInt("academy:relay_external_sentinel") == 7, "unrelated callback writes survive refusal"); });
        }); f.finish(14);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void native_redirect_or_callback_stack_mutation_is_preserved_and_free(GameTestHelper h) { callbackRefusal(h, 0); }
    private static void callbackRefusal(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h); ItemEntity item = f.drop(Items.IRON_INGOT, 4, f.pointA.add(.75, 0, 0));
        f.ready(() -> { Vec3 source = item.position(); double[] cp = {0}; TeleportHook hook = f.onTeleport(item, event -> {
                cp[0] = f.cp(); if (mode == 0) event.setTargetX(event.getTargetX() + .01); else if (mode == 1) item.getItem().setCount(3); else f.p.setPos(f.p.position().add(.01, 0, 0));
            }); h.assertTrue(f.tap(), "queue is admitted before callback refusal");
            f.post(1, () -> { h.assertTrue(hook.calls == 1 && item.position().equals(source), "changed callback cannot move original");
                near(h, f.cp(), cp[0], "changed precommit callback is free"); h.assertFalse(SpatialRelay.active(f.p), "changed callback retires handle");
                if (mode == 1) h.assertValueEqual(item.getItem().getCount(), 3, "external stack mutation is preserved, never refunded"); });
        }); f.finish(14);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void native_callback_count_change_never_gets_repaired_or_replaced(GameTestHelper h) { callbackRefusal(h, 1); }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void owner_movement_inside_native_callback_invalidates_only_unpaid_current_leg(GameTestHelper h) { callbackRefusal(h, 2); }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void ordinary_owner_movement_between_ticks_does_not_undo_bounded_original_route(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity item = f.drop(Items.IRON_INGOT, 5, f.pointA.add(.75, 0, 0)); MoveObserver observer = f.observe(item);
        f.ready(() -> { h.assertTrue(f.tap(), "bounded route starts"); f.post(1, () -> { observer.assertLast(); f.p.setPos(f.p.position().add(.1, 0, 0)); f.p.connection.resetPosition(); });
            f.post(2, () -> { h.assertTrue(observer.calls == 2 && item.position().equals(f.b), "ordinary owner movement between legs retains same bounded native route"); observer.assertLast(); });
            f.post(3, () -> { h.assertTrue(observer.calls == 3 && item.position().equals(f.c), "owner movement never redirects frozen destinations"); observer.assertLast(); });
        }); f.finish(16);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void insufficient_cp_after_first_leg_keeps_paid_progress_and_original_at_A(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity item = f.drop(Items.IRON_INGOT, 6, f.pointA.add(.75, 0, 0)); MoveObserver observer = f.observe(item);
        f.ready(() -> { h.assertTrue(f.tap(), "finite queue accepted"); f.post(1, () -> { observer.assertLast(); h.assertTrue(item.position().equals(f.a), "first paid original reaches A");
                f.hook(new EmptyBudget(f)); f.budget().putDouble("cp", 0); });
            f.post(2, () -> { h.assertTrue(observer.calls == 2 && item.position().equals(f.a) && item.getItem().getCount() == 6, "later resource refusal retains previous physical progress");
                h.assertFalse(SpatialRelay.active(f.p), "unfunded remainder is retired without replacement or refund"); });
        }); f.finish(15);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void newly_blocked_destination_stops_before_next_debit_without_undoing_first_leg(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity item = f.drop(Items.IRON_INGOT, 5, f.pointA.add(.75, 0, 0)); MoveObserver observer = f.observe(item);
        f.ready(() -> { h.assertTrue(f.tap(), "initial loaded clear route accepted"); f.post(1, () -> { observer.assertLast(); f.block(BlockPos.containing(f.b), Blocks.STONE.defaultBlockState()); });
            f.post(2, () -> { h.assertTrue(observer.calls == 1 && item.position().equals(f.a), "new full-space obstruction refuses before another event or debit");
                h.assertFalse(SpatialRelay.active(f.p), "blocked route ends with same physical item at last completed point"); });
        }); f.finish(15);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void paid_native_section_cancellation_is_uncertain_nonreplayable_and_never_refunded(GameTestHelper h) {
        Fixture f = new Fixture(h); f.pointB = f.pointA.add(17, 0, 0); ItemEntity item = f.drop(Items.IRON_INGOT, 5, f.pointA.add(.75, 0, 0)); MoveObserver observer = f.observe(item);
        f.ready(() -> { SectionHook hook = new SectionHook(item, event -> { var receipt = SpatialRelay.receipt(f.p);
                if (receipt != null && receipt.paid() && receipt.destination().equals(f.b)) {
                    h.assertTrue(f.cp() < observer.cpBefore, "actual CP debit precedes native destination section callback");
                    SpatialRelay.cancelActive(f.p);
                } }); f.hook(hook); h.assertTrue(f.tap(), "queue begins with genuine native item"); String wire = f.wire(f.inputs);
            f.post(2, () -> { var receipt = SpatialRelay.receipt(f.p);
                h.assertTrue(hook.calls > 0 && observer.calls == 2 && receipt != null && receipt.paid() && !SpatialRelay.active(f.p), "postcommit native cancellation leaves paid terminal evidence");
                h.assertTrue(item.position().equals(f.b) && item.getItem().getCount() == 5 && h.getLevel().getEntity(item.getUUID()) == item,
                        "uncertain native commit never rolls back, replaces or refunds original");
                near(h, f.cp(), observer.cpBefore - observer.cost, "uncertain committed positive leg has exactly one permanent debit");
                h.assertFalse(SpatialRelay.press(f.p, wire) || SpatialRelay.release(f.p, wire), "uncertain original request cannot replay"); });
        }); f.finish(15);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void unknown_removed_queued_original_is_never_recreated_or_inserted_into_inventory(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity item = f.drop(Items.DIAMOND, 7, f.pointA.add(.75, 0, 0)); MoveObserver observer = f.observe(item);
        f.ready(() -> { List<ItemStack> inventory = inventory(f.p); h.assertTrue(f.tap(), "queue accepted before unknown world removal");
            UUID uuid = item.getUUID(); item.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            f.post(1, () -> { h.assertFalse(SpatialRelay.active(f.p), "unloaded original retires ephemeral queue"); h.assertTrue(observer.calls == 0, "unloaded original never reaches transfer callback");
                h.assertTrue(h.getLevel().getEntity(uuid) == null, "unknown world removal is not repaired by recreating the item"); sameInventory(h, f.p, inventory);
                h.assertTrue(f.items.stream().filter(i -> i.getItem().is(Items.DIAMOND)).count() == 1, "test never substitutes a fabricated replacement for unknown original"); });
        }); f.finish(14);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void native_logout_clears_queue_and_new_owner_epoch_cannot_replay_original(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity item = f.drop(Items.IRON_INGOT, 3, f.pointA.add(.75, 0, 0));
        f.ready(() -> { h.assertTrue(f.tap(), "queued original before native logout event"); String old = f.wire(f.inputs); Vec3 source = item.position();
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.p)); h.assertFalse(SpatialRelay.active(f.p), "native logout synchronously retires old queue");
            LegacySingleKeyOwnerEpoch.forget(f.p); f.epoch(); h.assertFalse(SpatialRelay.press(f.p, old) || SpatialRelay.release(f.p, old), "new owner session rejects original wire identity");
            f.post(1, () -> h.assertTrue(item.position().equals(source) && !item.isRemoved() && item.getItem().getCount() == 3, "logout/relog does not deliver, refund or duplicate queued stock"));
        }); f.finish(14);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void native_epoch_and_preset_changes_retire_queue_before_next_positive_leg(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity item = f.drop(Items.IRON_INGOT, 3, f.pointA.add(.75, 0, 0)); MoveObserver observer = f.observe(item);
        f.ready(() -> { h.assertTrue(f.tap(), "queued original before owner identity invalidation"); Vec3 source = item.position();
            AbilityStorage.get(f.p).presets.edit(1, 1, ID, AbilityStorage.get(f.p)::learned); SpatialRelay.tick(f.p);
            h.assertFalse(SpatialRelay.active(f.p), "changed preset revision synchronously retires queued authority");
            f.post(1, () -> h.assertTrue(observer.calls == 0 && item.position().equals(source), "stale owner context never pays or moves after revision"));
        }); f.finish(14);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void fractional_real_growth_uses_smooth_per_leg_cp_and_grants_no_mastery_or_xp(GameTestHelper h) {
        Fixture f = new Fixture(h); AbilityStorage.get(f.p).addExperience(SafeLandingProbe.ID, .01); BigDecimal q = HighPowerGrowth.values(f.p).position();
        h.assertTrue(q.compareTo(BigDecimal.valueOf(4)) > 0 && q.compareTo(BigDecimal.valueOf(5)) < 0, "native prerequisite experience supplies fractional active growth");
        ItemEntity item = f.drop(Items.COPPER_INGOT, 9, f.pointA.add(.75, 0, 0)); MoveObserver observer = f.observe(item);
        f.ready(() -> { var ability = AbilityStorage.get(f.p); double xp = ability.levelExperience, mastery = ability.exp(ID);
            h.assertTrue(f.tap(), "fractional-growth relay queue"); f.post(1, () -> { observer.assertLast(); h.assertTrue(observer.cost > .025 && observer.cost < .25,
                        "actual positive leg follows smooth4/2/1/.25/.025 curve");
                h.assertTrue(ability.levelExperience == xp && ability.exp(ID) == mastery && HighPowerGrowth.values(f.p).position().compareTo(q) == 0,
                        "self-toss relay grants no XP, mastery or growth"); });
        }); f.finish(16);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void two_mark_crouched_short_release_queues_only_A_and_B(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity item = f.drop(Items.GOLD_INGOT, 4, f.pointA.add(.75, 0, 0)); MoveObserver observer = f.observe(item);
        f.a = f.surface(f.pointA); h.assertTrue(f.tap(), "first free mark");
        f.post(4, () -> { f.b = f.surface(f.pointB); h.assertTrue(f.tap() && SpatialRelay.points(f.p) == 2, "second free mark"); });
        f.post(8, () -> { f.p.setShiftKeyDown(true); h.assertTrue(f.tap(), "crouched fresh short release confirms exactly two prepared points");
            h.assertValueEqual(SpatialRelay.points(f.p), 2, "two-point confirmation does not invent C"); });
        f.post(9, () -> { h.assertTrue(observer.calls == 1 && item.position().equals(f.a), "first positive gather leg reaches A"); observer.assertLast(); });
        f.post(10, () -> { h.assertTrue(observer.calls == 2 && item.position().equals(f.b), "second positive leg reaches B"); observer.assertLast(); });
        f.post(11, () -> h.assertFalse(SpatialRelay.active(f.p), "two-point queue ends without a third positive leg")); f.finish(12);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void actual_six_tick_hold_release_clears_marks_without_marking_or_queueing(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity item = f.drop(Items.IRON_INGOT, 3, f.pointA.add(.75, 0, 0)); MoveObserver observer = f.observe(item);
        f.a = f.surface(f.pointA); h.assertTrue(f.tap(), "first mark before held clear");
        f.post(4, () -> h.assertTrue(f.press(), "fresh real key press begins hold"));
        f.post(9, () -> h.assertTrue(SpatialRelay.heldTicks(f.p) == 5 && SpatialRelay.points(f.p) == 1 && !SpatialRelay.active(f.p), "five real held ticks retain one free mark"));
        f.post(10, () -> { double cp = f.cp(); h.assertTrue(SpatialRelay.heldTicks(f.p) == 6 && f.release(), "release after six actual server ticks performs clear");
            h.assertTrue(SpatialRelay.points(f.p) == 0 && !SpatialRelay.active(f.p) && observer.calls == 0, "clear neither marks the aimed point nor transfers an item");
            near(h, f.cp(), cp, "six-tick clear is free"); }); f.finish(11);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=515)
    public static void three_marks_expire_from_first_real_tick_without_refreshing_the_lifetime(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> h.assertTrue(SpatialRelay.points(f.p) == 3 && SpatialRelay.remainingTicks(f.p) == SpatialRelayRules.PREPARATION_TICKS - 12,
                "later marks do not refresh the first mark's lifetime"));
        f.post(SpatialRelayRules.PREPARATION_TICKS - 1, () -> h.assertTrue(SpatialRelay.remainingTicks(f.p) == 1 && SpatialRelay.points(f.p) == 3, "native clock retains final preparation tick"));
        f.post(SpatialRelayRules.PREPARATION_TICKS, () -> h.assertTrue(SpatialRelay.points(f.p) == 0 && !SpatialRelay.active(f.p), "the exact native boundary expires all preparation")); f.finish(SpatialRelayRules.PREPARATION_TICKS + 1);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void native_merge_synchronously_retires_both_queued_handles_without_retargeting_survivor(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity a = f.drop(Items.IRON_INGOT, 60, f.pointA.add(.75, 0, 0));
        ItemEntity b = f.drop(Items.IRON_INGOT, 10, f.pointA.add(-.75, 0, 0)); MoveObserver first = f.observe(a), second = f.observe(b);
        f.ready(() -> { h.assertTrue(f.tap() && SpatialRelay.queuedCount(f.p) == 2, "two independently owned original entities are queued"); double cp = f.cp();
            merge(a, b); h.assertTrue(SpatialRelay.queuedCount(f.p) == 0 && SpatialRelayProvenance.proof(a) == null && SpatialRelayProvenance.proof(b) == null,
                    "both scoped queued handles are synchronously retired by native merge HEAD");
            h.assertTrue(a.getItem().getCount() == 64 && b.getItem().getCount() == 6 && !a.isRemoved() && !b.isRemoved(), "native partial merge conserves70 original items without refund");
            near(h, f.cp(), cp, "native merge retirement is free"); f.post(1, () -> h.assertTrue(first.calls == 0 && second.calls == 0 && !SpatialRelay.active(f.p),
                    "neither merged survivor nor leftover inherits an old queued leg"));
        }); f.finish(14);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void ordinary_native_destination_merge_after_commit_preserves_paid_leg_and_total_count(GameTestHelper h) {
        Fixture f = new Fixture(h); ItemEntity item = f.drop(Items.IRON_INGOT, 5, f.pointA.add(.75, 0, 0));
        ItemEntity destination = f.raw(Items.IRON_INGOT, 7, f.pointC); MoveObserver observer = f.observe(item);
        f.ready(() -> { h.assertTrue(f.tap(), "original enters route toward ordinary same-item destination"); f.post(3, () -> {
                h.assertTrue(observer.calls == 3 && item.position().equals(f.c), "all three positive native moves finish before destination merge"); observer.assertLast(); double cp = f.cp();
                merge(destination, item); h.assertTrue(item.isRemoved() && !destination.isRemoved() && destination.getItem().getCount() == 12,
                        "ordinary native merge consumes donor and retains exact sum after completed commit");
                near(h, f.cp(), cp, "normal destination merge neither refunds nor charges a relay leg");
                h.assertTrue(SpatialRelay.receipt(f.p).paid() && SpatialRelay.receipt(f.p).completed() == 3, "historical confirmed receipt survives ordinary postcommit merge");
            }); }); f.finish(16);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void native_world_mark_route_collision_hazard_and_unloaded_checks_are_read_only(GameTestHelper h) {
        Fixture f = new Fixture(h); f.surface(f.pointA); var assessment = SpatialRelayWorld.mark(f.p);
        h.assertTrue(assessment.valid(), "real loaded32m server aim creates one exact native anchor");
        ItemEntity item = f.drop(Items.IRON_INGOT, 3, f.pointA.add(1, 0, 0)); SpatialRelayMedium medium = SpatialRelayMedium.capture(f.p, item);
        h.assertTrue(medium != null && SpatialRelayWorld.checkLeg(f.p, medium, assessment.anchor()).valid(), "actual quarter-block item body has a clear grounded native route");
        Vec3 source = item.position(); double cp = f.cp(); BlockPos destination = BlockPos.containing(assessment.anchor().destination());
        f.block(destination, Blocks.STONE.defaultBlockState()); h.assertFalse(SpatialRelayWorld.checkLeg(f.p, medium, assessment.anchor()).valid(), "full destination collision cannot be skipped");
        f.block(destination, Blocks.AIR.defaultBlockState()); f.block(destination.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
        h.assertFalse(SpatialRelayWorld.checkAnchor(f.p, assessment.anchor()).valid(), "hazardous floor is refused");
        f.block(destination.below(), Blocks.STONE.defaultBlockState());
        int far = 1_600_000; h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far >> 4, far >> 4) == null, "remote chunk starts unloaded");
        try { Method loaded = SpatialRelayMedium.class.getDeclaredMethod("loaded", net.minecraft.server.level.ServerLevel.class, net.minecraft.world.phys.AABB.class); loaded.setAccessible(true);
            h.assertFalse((Boolean) loaded.invoke(null, h.getLevel(), new net.minecraft.world.phys.AABB(far, source.y, far, far + .25, source.y + .25, far + .25)), "production native body lookup rejects unloaded chunk");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far >> 4, far >> 4) == null && item.position().equals(source) && item.getItem().getCount() == 3,
                "read-only route checks never force-load, move, create or insert an item"); near(h, f.cp(), cp, "all native world assessments are free"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void first_ground_precedes_unused_ray_tail_below_world_and_marks_for_free(GameTestHelper h) {
        Fixture f = new Fixture(h); Vec3 target = f.source.add(0, 0, 2); Vec3 expected = f.surface(target);
        h.assertTrue(f.p.getEyePosition().add(f.p.getLookAngle().scale(32)).y < h.getLevel().getMinBuildHeight(),
                "unchanged lower-Y arena makes the unused32m tail leave build height");
        double cp = f.cp(); var result = SpatialRelayWorld.mark(f.p);
        h.assertTrue(result.valid() && result.anchor().destination().equals(expected),
                "actual first native UP surface wins before an untraversed below-world tail: " + result.reason());
        h.assertTrue(f.tap() && SpatialRelay.points(f.p) == 1, "authenticated short release saves the genuine first ground");
        near(h, f.cp(), cp, "mark remains free"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void nearer_first_wall_is_never_skipped_for_ground_or_invalid_unused_tail(GameTestHelper h) {
        Fixture f = new Fixture(h); Vec3 floor = f.surface(f.source.add(0, 0, 3));
        BlockPos wall = BlockPos.containing(f.p.getEyePosition().add(floor).scale(.5));
        for (int dy = -1; dy <= 1; dy++) f.block(wall.above(dy), Blocks.STONE.defaultBlockState());
        var nativeHit = h.getLevel().clip(new ClipContext(f.p.getEyePosition(), f.p.getEyePosition().add(f.p.getLookAngle().scale(32)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, f.p));
        h.assertTrue(nativeHit.getType() == HitResult.Type.BLOCK && nativeHit.getDirection() != Direction.UP
                && nativeHit.getLocation().distanceToSqr(f.p.getEyePosition()) < floor.distanceToSqr(f.p.getEyePosition()),
                "independent native clip hits the nearer vertical wall first");
        var result = SpatialRelayWorld.mark(f.p);
        h.assertTrue(!result.valid() && result.reason().equals("no_visible_ground"), "first wall remains authoritative: " + result.reason());
        double cp = f.cp(); h.assertFalse(f.tap(), "real input never searches behind first wall");
        near(h, f.cp(), cp, "first wall costs nothing"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void exact_border_before_first_floor_refuses_and_restoring_border_restores_same_floor(GameTestHelper h) {
        Fixture f = new Fixture(h); Vec3 expected = f.surface(f.pointA); var border = h.getLevel().getWorldBorder();
        var settings = border.createSettings(); double cp = f.cp();
        try {
            border.setCenter(f.source.x, f.source.z); border.setSize(8);
            h.assertFalse(border.isWithinBounds(BlockPos.containing(expected)), "known actual first floor lies beyond the temporary border");
            var result = SpatialRelayWorld.mark(f.p);
            h.assertTrue(!result.valid() && result.reason().equals("outside_world_border"), "actually crossed border is refused: " + result.reason());
            h.assertFalse(f.tap(), "no real mark through the border"); near(h, f.cp(), cp, "border refusal is free");
        } finally { border.applySettings(settings); }
        var restored = SpatialRelayWorld.mark(f.p);
        h.assertTrue(restored.valid() && restored.anchor().destination().equals(expected), "restored border admits identical native floor"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void unloaded_cell_before_any_first_hit_is_refused_without_loading(GameTestHelper h) {
        Fixture f = new Fixture(h); Vec3 source = new Vec3(24200008.5, h.getLevel().getMaxBuildHeight() - 8, 24200008.5);
        ChunkPos center = new ChunkPos(BlockPos.containing(source)); h.getLevel().getChunk(center.x, center.z);
        ChunkPos missing = null; int dx = 0, dz = 0;
        search: for (int[] direction : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) for (int step = 1; step <= 2; step++) {
            ChunkPos candidate = new ChunkPos(center.x + direction[0] * step, center.z + direction[1] * step);
            if (h.getLevel().getChunkSource().getChunkNow(candidate.x, candidate.z) == null) {
                missing = candidate; dx = direction[0]; dz = direction[1]; break search;
            }
        }
        h.assertTrue(missing != null, "actual horizontal32m prefix has an absent downstream chunk");
        f.p.setPos(source); f.p.setDeltaMovement(Vec3.ZERO); f.p.connection.resetPosition();
        f.p.setYRot((float)Math.toDegrees(Math.atan2(-dx, dz))); f.p.setXRot(0);
        double cp = f.cp(); var result = SpatialRelayWorld.mark(f.p);
        h.assertTrue(!result.valid() && result.reason().equals("unloaded_chunk"), "visited missing cell is refused: " + result.reason());
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(missing.x, missing.z) == null, "assessment never loads the missing chunk");
        h.assertTrue(f.p.position().equals(source) && SpatialRelay.points(f.p) == 0, "read-only refusal creates no movement or authority");
        near(h, f.cp(), cp, "unloaded refusal is free"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void non_dyadic_leftward_native_setpos_keeps_canonical_body_and_confirmed_receipt(GameTestHelper h) {
        Fixture f = new Fixture(h); f.pointA = h.absoluteVec(new Vec3(30.49, 5, 14.5)); f.pointB = h.absoluteVec(new Vec3(6.51, 5, 14.5));
        ItemEntity item = f.drop(Items.IRON_INGOT, 4, f.pointA.add(.75, 0, 0)); MoveObserver observer = f.observe(item);
        f.ready(() -> { h.assertTrue(f.tap(), "non-dyadic native route is admitted"); f.post(2, () -> { observer.assertLast();
                h.assertTrue(observer.calls == 2 && item.position().equals(f.b) && SpatialRelay.receipt(f.p).completed() == 2,
                        "actual leftward native setter confirms instead of falsely reporting paid uncertainty");
                h.assertTrue(item.getBoundingBox().equals(new net.minecraft.world.phys.AABB(f.b.x - .125, f.b.y, f.b.z - .125, f.b.x + .125, f.b.y + .25, f.b.z + .125)),
                        "native destination body uses exact canonical destination faces, not rounded translation of old faces");
            }); }); f.finish(16);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void actual_positive_leg_at_retained_q1_debits_four_cp(GameTestHelper h) { costAnchor(h, 1, 4); }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void actual_positive_leg_at_retained_q2_debits_two_cp(GameTestHelper h) { costAnchor(h, 2, 2); }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void actual_positive_leg_at_retained_q3_debits_one_cp(GameTestHelper h) { costAnchor(h, 3, 1); }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void actual_positive_leg_at_q4_debits_quarter_cp(GameTestHelper h) { costAnchor(h, 4, .25); }
    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=24)
    public static void actual_positive_leg_at_q5_debits_fractional_cp(GameTestHelper h) { costAnchor(h, 5, .025); }
    private static void costAnchor(GameTestHelper h, int q, double expected) {
        Fixture f = new Fixture(h, Math.max(4, q));
        if (q < 4) {
            // Declared native rollback/relevel fixture preserves already earned capacity,
            // while the genuine active-growth rules keep q below the LV4 unlock.
            AbilityStorage.get(f.p).setLevel(q); h.assertTrue(HighPowerGrowth.values(f.p).position().compareTo(BigDecimal.valueOf(q)) == 0,
                    "native rollback produces the declared retained active coordinate");
            AbilityStorage.get(f.p).setLevel(4); AbilityStorage.get(f.p).overloadFine = true;
        }
        h.assertTrue(HighPowerGrowth.values(f.p).position().compareTo(BigDecimal.valueOf(q)) == 0,
                "native level unlock does not snap active growth to its integer level");
        ItemEntity item = f.drop(Items.IRON_INGOT, 3, f.pointA.add(.75, 0, 0)); MoveObserver observer = f.observe(item);
        f.ready(() -> { h.assertTrue(f.tap(), "real native relay queue at active q" + q); f.post(1, () -> {
                h.assertTrue(observer.calls == 1 && item.position().equals(f.a), "cost witness comes from an actual positive item leg");
                near(h, observer.cost, expected, "approved exact q" + q + " cost anchor"); observer.assertLast();
            }); }); f.finish(16);
    }

    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-7, why + ": " + actual + " != " + expected); }
    private static CompoundTag nativeTag(ItemEntity item) { return item.saveWithoutId(new CompoundTag()); }
    private static CompoundTag nativeState(ItemEntity item) { CompoundTag tag = new CompoundTag(); item.addAdditionalSaveData(tag); tag.putInt("ExactAge", item.getAge()); tag.putInt("ExactLifespan", item.lifespan); return tag; }
    private static void exactAge(ItemEntity item, int age) { try { Field field = ItemEntity.class.getDeclaredField("age"); field.setAccessible(true); field.setInt(item, age); } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); } }
    private static void merge(ItemEntity survivor, ItemEntity donor) { try { Method method = ItemEntity.class.getDeclaredMethod("merge", ItemEntity.class, ItemStack.class, ItemEntity.class, ItemStack.class);
        method.setAccessible(true); method.invoke(null, survivor, survivor.getItem(), donor, donor.getItem()); } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); } }
    private static List<ItemStack> inventory(ServerPlayer player) { List<ItemStack> result = new ArrayList<>(); for (int i = 0; i < player.getInventory().getContainerSize(); i++) result.add(player.getInventory().getItem(i).copy()); return result; }
    private static void sameInventory(GameTestHelper h, ServerPlayer player, List<ItemStack> before) { for (int i = 0; i < before.size(); i++) h.assertTrue(ItemStack.matches(before.get(i), player.getInventory().getItem(i)), "relay never inserts or creates inventory items: slot" + i); }
    public static final class TeleportHook {
        final ItemEntity target; final Consumer<EntityTeleportEvent> callback; int calls;
        TeleportHook(ItemEntity target, Consumer<EntityTeleportEvent> callback) { this.target = target; this.callback = callback; }
        @SubscribeEvent public void teleport(EntityTeleportEvent event) { if (event.getEntity() == target) { calls++; callback.accept(event); } }
    }
    public static final class SectionHook {
        final ItemEntity target; final Consumer<EntityEvent.EnteringSection> callback; int calls;
        SectionHook(ItemEntity target, Consumer<EntityEvent.EnteringSection> callback) { this.target = target; this.callback = callback; }
        @SubscribeEvent public void section(EntityEvent.EnteringSection event) { if (event.getEntity() == target) { calls++; callback.accept(event); } }
    }
    public static final class MoveObserver {
        final Fixture f; final ItemEntity item; int calls, tick; double cpBefore, cost; CompoundTag nativeBefore; Vec3 motion; ItemStack stack; UUID uuid; List<ItemStack> inventory;
        MoveObserver(Fixture f, ItemEntity item) { this.f = f; this.item = item; }
        @SubscribeEvent public void teleport(EntityTeleportEvent event) { if (event.getEntity() != item) return;
            f.h.assertTrue(tick != f.p.server.getTickCount() || calls == 0, "an original receives at most one native teleport event per server tick"); calls++; tick = f.p.server.getTickCount(); cpBefore = f.cp();
            cost = ContinuousHighPowerGrowth.curve("4", "2", "1", ".25", ".025").value(HighPowerGrowth.values(f.p).position()).doubleValue();
            nativeBefore = nativeState(item); motion = item.getDeltaMovement(); stack = item.getItem().copy(); uuid = item.getUUID(); inventory = inventory(f.p);
        }
        void assertLast() { near(f.h, f.cp(), cpBefore - cost, "one actual positive native item leg debits exactly its current continuous CP cost");
            f.h.assertTrue(nativeState(item).equals(nativeBefore) && item.getDeltaMovement().equals(motion) && ItemStack.matches(stack, item.getItem())
                    && item.getUUID().equals(uuid), "native age/lifespan/pickup delay/thrower/target/count/components/motion/identity remain exact across commit"); sameInventory(f.h, f.p, inventory); }
    }
    /** Declared zero-resource fixture runs after natural recovery but before the ordinary relay Post handler. */
    public static final class EmptyBudget {
        final Fixture fixture;
        EmptyBudget(Fixture fixture) { this.fixture = fixture; }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(ServerTickEvent.Post event) {
            if (event.getServer() == fixture.p.server) fixture.budget().putDouble("cp", 0);
        }
    }
    public static final class PostCheck {
        final Fixture fixture; final int tick; final Runnable action; boolean ran;
        PostCheck(Fixture fixture, int delay, Runnable action) { this.fixture = fixture; tick = fixture.p.server.getTickCount() + delay; this.action = action; }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(ServerTickEvent.Post event) { if (ran || event.getServer() != fixture.p.server || event.getServer().getTickCount() != tick) return; ran = true;
            try { action.run(); } catch (Throwable problem) { if (fixture.failure == null) fixture.failure = problem; } }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final ServerPlayer p; final EmbeddedChannel channel; final Vec3 source; Vec3 pointA, pointB, pointC, a, b, c;
        final List<ItemEntity> items = new ArrayList<>(); final List<Object> hooks = new ArrayList<>(); final List<PostCheck> checks = new ArrayList<>();
        final Map<BlockPos, BlockState> changed = new LinkedHashMap<>(); final Set<ChunkPos> held = new LinkedHashSet<>(); final UUID ticket = UUID.randomUUID();
        long inputs; boolean closed; Throwable failure;
        Fixture(GameTestHelper h) { this(h, 4); }
        Fixture(GameTestHelper h, int level) {
            this.h = h; source = h.absoluteVec(new Vec3(24.5, 5, 8.5)); pointA = h.absoluteVec(new Vec3(18.5, 5, 14.5)); pointB = h.absoluteVec(new Vec3(24.5, 5, 14.5)); pointC = h.absoluteVec(new Vec3(30.5, 5, 14.5));
            for (int x = 6; x <= 43; x++) for (int z = 4; z <= 24; z++) block(h.absolutePos(new BlockPos(x, 4, z)), Blocks.STONE.defaultBlockState());
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "SR" + UUID.randomUUID().toString().substring(0, 10)), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie);
            channel.runPendingTasks(); channel.flushOutbound(); Object packet; while ((packet = channel.readOutbound()) != null) {
                if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet);
            }
            p.setGameMode(GameType.SURVIVAL); p.getAbilities().invulnerable = false; p.getAbilities().instabuild = false; p.setNoGravity(false); p.setPose(Pose.STANDING); p.setShiftKeyDown(false);
            p.setPos(source); p.setOnGround(true); p.setDeltaMovement(0, -.0784, 0); p.move(MoverType.SELF, new Vec3(0, -.0784, 0)); p.connection.resetPosition();
            h.assertTrue(p.onGround() && p.position().equals(source), "real floor collision establishes connected fixture ground");
            h.assertTrue(HighPowerCombat.enroll(p, level), "declared connected native LV4 high-power enrollment: " + HighPowerCombat.unsupportedReason(p));
            var ability = AbilityStorage.get(p); ability.selectCategory(HighPowerCategories.TELEPORTER); ability.setLevel(level);
            for (String skill : List.of(SafeLandingProbe.ID, PhaseSidestep.ID, DisplacedProjection.ID, RecoveryMark.ID, ReturnMark.ID, CrossSubstitution.ID, CornerExchange.ID, ID)) ability.learn(skill);
            ability.activated = true; ability.overloadFine = true; h.assertTrue(ability.presets.edit(0, 0, ID, ability::learned), "actual learned relay skill maps to native preset");
            HighPowerResources.initializeBudget(p); epoch(); hook(new HighPowerPhaseSidestepRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5));
            h.testInfo.addListener(new GameTestListener() { public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); } public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); } });
        }
        long epoch() { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        String wire(long input) { return new HighPowerInput.Input(0, input, p.getId(), epoch()).wire(); }
        boolean press() { return AcademyGameplay.requestFromClient(p, new AcademyNetwork.Request("hp_spatial_relay_start", wire(++inputs))); }
        boolean release() { return AcademyGameplay.requestFromClient(p, new AcademyNetwork.Request("hp_spatial_relay_release", wire(inputs))); }
        boolean tap() { return press() && release(); }
        void aim(Vec3 at) { Vec3 d = at.subtract(p.getEyePosition()); p.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z))); p.setXRot((float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)))); }
        Vec3 surface(Vec3 at) { aim(at); var hit = h.getLevel().clip(new ClipContext(p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(32)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
            h.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP, "independent actual32m server ray reaches visible loaded floor top"); return hit.getLocation(); }
        void ready(Runnable action) { a = surface(pointA); double cp = cp(); h.assertTrue(tap(), "first free native short release marks A: " + SpatialRelay.receipt(p)); near(h, cp(), cp, "mark A is free");
            post(4, () -> { b = surface(pointB); double before = cp(); h.assertTrue(tap(), "second release marks B: " + SpatialRelay.receipt(p)); near(h, cp(), before, "mark B is free"); });
            post(8, () -> { c = surface(pointC); double before = cp(); h.assertTrue(tap(), "third release marks C: " + SpatialRelay.receipt(p)); near(h, cp(), before, "mark C is free"); }); post(12, action); }
        ItemEntity selectedDrop(boolean whole) { Set<UUID> before = new HashSet<>(); h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(3)).forEach(i -> before.add(i.getUUID()));
            h.assertTrue(p.drop(whole), "actual native selected Q/control-Q toss succeeds"); var found = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(3), i -> !before.contains(i.getUUID()));
            h.assertTrue(found.size() == 1, "exactly one genuine newly tossed item enters native world"); ItemEntity item = found.getFirst(); items.add(item); item.setNoGravity(false); item.setDeltaMovement(Vec3.ZERO); item.setPickUpDelay(200); return item; }
        ItemEntity drop(Item type, int count, Vec3 at) { p.getInventory().setItem(p.getInventory().selected, new ItemStack(type, count)); ItemEntity item = selectedDrop(true); item.setPos(at); return item; }
        ItemEntity raw(Item type, int count, Vec3 at) { ItemEntity item = new ItemEntity(h.getLevel(), at.x, at.y, at.z, new ItemStack(type, count), 0, 0, 0); item.setNoGravity(false);
            h.assertTrue(h.getLevel().addFreshEntity(item), "declared unproven native world item fixture"); items.add(item); return item; }
        CompoundTag budget() { return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY); }
        double cp() { return HighPowerResources.cp(p); }
        void block(BlockPos pos, BlockState state) { h.assertTrue(h.getBounds().contains(Vec3.atCenterOf(pos)), "declared floor/obstacle stays within native test arena"); ChunkPos chunk = new ChunkPos(pos);
            if (held.add(chunk)) { h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticket); h.getLevel().getChunk(chunk.x, chunk.z); }
            changed.putIfAbsent(pos, h.getLevel().getBlockState(pos)); h.getLevel().setBlockAndUpdate(pos, state); }
        void hook(Object listener) { hooks.add(listener); NeoForge.EVENT_BUS.register(listener); }
        TeleportHook onTeleport(ItemEntity item, Consumer<EntityTeleportEvent> callback) { TeleportHook hook = new TeleportHook(item, callback); hook(hook); return hook; }
        MoveObserver observe(ItemEntity item) { MoveObserver observer = new MoveObserver(this, item); hook(observer); return observer; }
        void post(int delay, Runnable action) { PostCheck check = new PostCheck(this, delay, action); checks.add(check); hook(check); }
        void finish(int delay) { h.runAfterDelay(delay, () -> { if (failure != null) throw new AssertionError("native relay observation failed", failure);
            h.assertTrue(checks.stream().allMatch(c -> c.ran), "every real server Post observation executed"); h.succeed(); }); }
        @Override public void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); SpatialRelay.remove(p); HighPowerBracerSession.remove(p); AbilityStorage.remove(p);
            p.server.getPlayerList().remove(p); items.forEach(Entity::discard); changed.forEach((pos, state) -> h.getLevel().setBlockAndUpdate(pos, state));
            held.forEach(chunk -> h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticket)); channel.finishAndReleaseAll(); }
    }
}

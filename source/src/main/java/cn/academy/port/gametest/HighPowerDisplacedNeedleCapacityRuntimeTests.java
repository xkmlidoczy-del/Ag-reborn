package cn.academy.port.gametest;

import cn.academy.port.AcademyCraft;
import cn.academy.port.highpower.DisplacedNeedleEntity;
import cn.academy.port.highpower.DisplacedNeedleRegistry;
import cn.academy.port.highpower.core.DisplacedNeedleLedger;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Separates persisted owner-cap exhaustion from the two exact far-coordinate conditions
 * observed in the NEW342 full native runs. The coordinates never follow the random test
 * structure origin. Capacity fixtures are decoded through the production codec and are
 * installed only for a synchronous transaction, then the original SavedData object,
 * serialized content and dirty flag are restored. No synthetic owner reaches disk.
 */
@GameTestHolder("academy_highpower_needle_capacity")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class HighPowerDisplacedNeedleCapacityRuntimeTests {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final String TEMPLATE = "highpower_empty";
    private static final String BATCH = "highpower_displaced_needle_capacity_coordinates";
    private static final TicketType<UUID> TICKET = TicketType.create("academy_needle_capacity_fixture", UUID::compareTo);
    // Same first-failing structure offset (+111, -1, +266) at each recorded suite origin.
    private static final Vec3 FIRST_STRUCTURE = new Vec3(7_232_032, -60, 1_521_005);
    private static final Vec3 SECOND_STRUCTURE = new Vec3(11_506_181, -60, -3_852_138);

    private HighPowerDisplacedNeedleCapacityRuntimeTests() { }

    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int axis = 0; axis < 3; axis++) {
            size.add(IntTag.valueOf(4));
            position.add(IntTag.valueOf(0));
        }
        cell.put("pos", position);
        cell.putInt("state", 0);
        blocks.add(cell);
        air.putString("Name", "minecraft:air");
        palette.add(air);
        tag.put("size", size);
        tag.put("blocks", blocks);
        tag.put("palette", palette);
        tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_highpower_needle_capacity", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void first_exact_position_fresh_registry_publishes_one_original(GameTestHelper h) {
        verifyWhenNativeChunksReady(h, FIRST_STRUCTURE, false);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void second_exact_position_fresh_registry_publishes_one_original(GameTestHelper h) {
        verifyWhenNativeChunksReady(h, SECOND_STRUCTURE, false);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void first_exact_position_full_registry_refuses_before_join_or_inventory(GameTestHelper h) {
        verifyWhenNativeChunksReady(h, FIRST_STRUCTURE, true);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void second_exact_position_full_registry_refuses_before_join_or_inventory(GameTestHelper h) {
        verifyWhenNativeChunksReady(h, SECOND_STRUCTURE, true);
    }

    private static void verifyWhenNativeChunksReady(GameTestHelper h, Vec3 structure, boolean full) {
        var level = h.getLevel();
        var chunks = level.getChunkSource();
        Vec3 feet = structure.add(8.5, 5, 8.5);
        Vec3 source = structure.add(12.5, 5.2, 8.5);
        UUID ticket = UUID.randomUUID();
        Set<ChunkPos> held = new LinkedHashSet<>();
        h.testInfo.addListener(new GameTestListener() {
            private boolean closed;
            private void close() {
                if (closed) return;
                closed = true;
                for (var chunk : held) chunks.removeRegionTicket(TICKET, chunk, 2, ticket);
            }
            public void testStructureLoaded(GameTestInfo info) { }
            public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
            public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { close(); }
        });
        for (Vec3 point : List.of(feet, source)) {
            ChunkPos chunk = new ChunkPos(BlockPos.containing(point));
            if (held.add(chunk)) {
                chunks.addRegionTicket(TICKET, chunk, 2, ticket);
                level.getChunk(chunk.x, chunk.z);
            }
        }
        h.assertTrue(held.size() <= 2, "only the two exact native transaction chunks are held");
        LOGGER.info("Needle capacity fixture prepared: full={}, feet={}, source={}, readiness={}",
                full, feet, source, readiness(level, source));
        // A FULL chunk does not yet prove entity-section visibility or completed entity I/O.
        // Let real server ticks settle the tickets; no temporary registry or player exists yet.
        h.startSequence().thenWaitUntil(() -> {
            for (Vec3 point : List.of(feet, source)) {
                BlockPos pos = BlockPos.containing(point);
                h.assertTrue(level.areEntitiesLoaded(ChunkPos.asLong(pos)) && level.isPositionEntityTicking(pos),
                        "exact native transaction chunk becomes entity-ready: " + readiness(level, point));
            }
        }).thenExecute(() -> verify(h, structure, full)).thenSucceed();
    }

    private static String readiness(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        return "position=" + point + ",entitiesLoaded=" + level.areEntitiesLoaded(ChunkPos.asLong(pos))
                + ",entityTicking=" + level.isPositionEntityTicking(pos);
    }

    private static void verify(GameTestHelper h, Vec3 structure, boolean full) {
        var level = h.getLevel();
        Vec3 feet = structure.add(8.5, 5, 8.5);
        Vec3 source = structure.add(12.5, 5.2, 8.5);
        try (var actors = new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)) {
            // All chunk loading, genuine ticking and player admission precede registry installation.
            ServerPlayer player = actors.player();
            player.moveTo(feet.x, feet.y, feet.z, 0, 0);
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), 2));
            h.assertTrue(player.position().equals(feet), "native owner retains the exact recorded absolute feet");

            CompoundTag fixtureTag = registryTag(full ? DisplacedNeedleRegistry.MAX_OWNERS : 0);
            var fixture = DisplacedNeedleRegistry.load(level.getServer(), fixtureTag, level.registryAccess());
            h.assertFalse(fixture.isBlocked(), "capacity fixture is accepted by the production SavedData decoder");
            h.assertValueEqual(fixture.ownerCount(), full ? DisplacedNeedleRegistry.MAX_OWNERS : 0,
                    "decoded registry contains exactly the declared bounded number of owners");
            h.assertTrue(fixture.save(new CompoundTag(), level.registryAccess()).equals(fixtureTag),
                    "production codec preserves every capacity-fixture entry exactly");
            h.assertTrue(fixture.snapshot(player.getUUID()).isEmpty(), "native player has no pre-existing fixture owner entry");

            var storage = level.getServer().overworld().getDataStorage();
            var original = DisplacedNeedleRegistry.get(level);
            CompoundTag originalTag = original.save(new CompoundTag(), level.registryAccess()).copy();
            boolean originalDirty = original.isDirty();
            long originalTick = level.getGameTime();
            JoinProbe probe = new JoinProbe(player, source);
            NeoForge.EVENT_BUS.register(probe);
            try {
                storage.set(DisplacedNeedleRegistry.ID, fixture);
                h.assertTrue(DisplacedNeedleRegistry.get(level) == fixture, "production lookup uses the isolated codec-decoded registry");
                var available = DisplacedNeedleRegistry.availability(player);
                h.assertTrue(available == (full ? DisplacedNeedleRegistry.Availability.REGISTRY_FULL
                        : DisplacedNeedleRegistry.Availability.READY), "read-only capacity diagnosis precedes deployment");
                ItemStack hand = player.getOffhandItem();
                List<ItemStack> inventory = inventory(player);
                AtomicInteger guards = new AtomicInteger(), payments = new AtomicInteger();
                var result = DisplacedNeedleRegistry.deploy(player, source,
                        () -> { guards.incrementAndGet(); return true; },
                        () -> { payments.incrementAndGet(); return true; });
                var candidate = probe.candidates.isEmpty() ? null : probe.candidates.getFirst();
                String observed = "guards=" + guards.get() + ",joins=" + probe.candidates.size() + ",payments=" + payments.get()
                        + ",uuidIndexed=" + (candidate != null && level.getEntity(candidate.getUUID()) == candidate)
                        + ",nativeIdIndexed=" + (candidate != null && level.getEntity(candidate.getId()) == candidate)
                        + ",candidateRemoved=" + (candidate != null && candidate.isRemoved())
                        + ",ledger=" + fixture.snapshot(player.getUUID()) + "," + readiness(level, source);
                LOGGER.info("Needle capacity native transaction: full={}, admitted={}, {}", full, result.isPresent(), observed);
                if (full) {
                    h.assertTrue(result.isEmpty(), "full registry refuses at the exact far position");
                    h.assertValueEqual(probe.candidates.size(), 0, "registry capacity refuses before native join");
                    h.assertValueEqual(guards.get(), 0, "capacity refusal precedes caller guard callbacks");
                    h.assertValueEqual(payments.get(), 0, "capacity refusal never invokes payment");
                    h.assertTrue(player.getOffhandItem() == hand && sameInventory(player, inventory),
                            "capacity refusal preserves the original hand object and every inventory stack");
                    h.assertTrue(fixture.snapshot(player.getUUID()).isEmpty(), "capacity refusal never creates an owner record");
                    h.assertTrue(fixture.save(new CompoundTag(), level.registryAccess()).equals(fixtureTag),
                            "capacity refusal preserves every decoded registry entry");
                    h.assertFalse(fixture.isDirty(), "read-only refusal never dirties the isolated registry");
                } else {
                    h.assertTrue(result.isPresent(), "fresh registry admits native publication at the exact far position: " + observed);
                    DisplacedNeedleEntity medium = result.orElseThrow();
                    h.assertValueEqual(probe.candidates.size(), 1, "exactly one native candidate joins");
                    h.assertTrue(probe.candidates.getFirst() == medium && probe.pendingOriginalSeen,
                            "native join observes the one original escrowed needle before admission");
                    h.assertValueEqual(payments.get(), 1, "successful native publication pays exactly once");
                    h.assertTrue(guards.get() > 0 && player.getOffhandItem() == hand && hand.getCount() == 1,
                            "one actual offhand item transfers through the normal production guard");
                    h.assertTrue(medium.position().equals(source) && medium.getBoundingBox().equals(new AABB(
                                    source.x - .125, source.y, source.z - .125,
                                    source.x + .125, source.y + .25, source.z + .125)),
                            "native publication retains the exact far-coordinate position and quarter-block body");
                    h.assertTrue(level.getEntity(medium.getUUID()) == medium && level.getEntity(medium.getId()) == medium
                                    && medium.getOwner() == player && medium.isAdmitted() && !medium.isContactEnabled(),
                            "both native entity indexes expose the same harmless admitted original");
                    h.assertTrue(ItemStack.matches(medium.mediumSnapshot(), new ItemStack(AcademyCraft.NEEDLE.get())),
                            "entity custody contains exactly the original plain single needle");
                    var state = fixture.snapshot(player.getUUID()).orElseThrow();
                    h.assertTrue(state.state() == DisplacedNeedleLedger.State.ACTIVE
                                    && state.custody() == DisplacedNeedleLedger.Custody.ENTITY
                                    && medium.getUUID().equals(state.entityId()), "isolated owner ledger records actual native entity custody");
                    h.assertValueEqual(fixture.ownerCount(), 1, "fresh control creates only its single temporary owner");
                }
                h.assertTrue(player.position().equals(feet), "transaction never relocates the owner");
            } finally {
                // Even a native callback/assertion failure cannot leave synthetic capacity installed.
                try {
                    for (var medium : probe.candidates) if (!medium.isRemoved()) medium.discard();
                } finally {
                    storage.set(DisplacedNeedleRegistry.ID, original);
                    original.setDirty(originalDirty);
                    NeoForge.EVENT_BUS.unregister(probe);
                    h.assertTrue(DisplacedNeedleRegistry.get(level) == original, "exact original SavedData object is restored");
                    h.assertTrue(original.save(new CompoundTag(), level.registryAccess()).equals(originalTag),
                            "every original registry entry is unchanged after the synchronous fixture");
                    h.assertTrue(original.isDirty() == originalDirty, "original persistence dirty flag is unchanged");
                    h.assertTrue(level.getGameTime() == originalTick, "no native world tick runs with a fixture registry installed");
                }
            }
        }
    }

    private static CompoundTag registryTag(int owners) {
        CompoundTag root = new CompoundTag();
        root.putInt("version", 1);
        ListTag entries = new ListTag();
        for (int index = 0; index < owners; index++) {
            UUID owner = new UUID(0x4143414341500000L, index + 1L);
            CompoundTag entry = new CompoundTag(), ledger = new CompoundTag();
            new DisplacedNeedleLedger(owner).toDecimalMap().forEach(ledger::putString);
            entry.putString("owner", owner.toString());
            entry.putString("dimension", "");
            entry.put("ledger", ledger);
            entry.put("escrow", new CompoundTag());
            entries.add(entry);
        }
        root.put("entries", entries);
        return root;
    }

    private static List<ItemStack> inventory(ServerPlayer player) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            result.add(player.getInventory().getItem(slot).copy());
        return result;
    }

    private static boolean sameInventory(ServerPlayer player, List<ItemStack> expected) {
        if (player.getInventory().getContainerSize() != expected.size()) return false;
        for (int slot = 0; slot < expected.size(); slot++)
            if (!ItemStack.matches(player.getInventory().getItem(slot), expected.get(slot))) return false;
        return true;
    }

    public static final class JoinProbe {
        private final ServerPlayer owner;
        private final Vec3 source;
        private final List<DisplacedNeedleEntity> candidates = new ArrayList<>();
        private boolean pendingOriginalSeen;
        JoinProbe(ServerPlayer owner, Vec3 source) { this.owner = owner; this.source = source; }
        @SubscribeEvent public void join(EntityJoinLevelEvent event) {
            if (!(event.getEntity() instanceof DisplacedNeedleEntity medium) || medium.getOwner() != owner) return;
            candidates.add(medium);
            var registry = DisplacedNeedleRegistry.get(owner.serverLevel());
            var state = registry.snapshot(owner.getUUID()).orElseThrow();
            pendingOriginalSeen = state.state() == DisplacedNeedleLedger.State.PENDING
                    && registry.retainedEscrowCount(owner.getUUID()) == 1 && owner.getOffhandItem().getCount() == 1
                    && !medium.isAdmitted() && medium.position().equals(source);
        }
    }
}

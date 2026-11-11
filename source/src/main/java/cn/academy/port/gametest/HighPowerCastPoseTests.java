package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CastPose;
import cn.academy.port.highpower.core.NewSkillBudget;
import cn.academy.port.preset.PresetSkills;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Cosmetic cast-pose relay: only an activated ability may broadcast, intents are rate-limited, and nothing here touches game state. */
@GameTestHolder("academy_cast_pose")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerCastPoseTests {
    private static final String TEMPLATE = "cast_pose_empty", BATCH = "cast_pose";
    private HighPowerCastPoseTests() {}

    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int dimension : new int[] {48, 12, 48}) size.add(IntTag.valueOf(dimension));
        for (int axis = 0; axis < 3; axis++) position.add(IntTag.valueOf(0));
        cell.put("pos", position); cell.putInt("state", 0); blocks.add(cell); air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_cast_pose", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void only_an_activated_ability_can_relay_a_gesture_and_the_rate_is_capped(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PULSE_SHIELD); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            long t = h.getLevel().getGameTime();
            var intent = new CastPoseNet.Intent(CastPose.Archetype.GUARD.ordinal(), CastPoseNet.HOLD);
            var cue = CastPoseNet.accept(p, intent, t);
            h.assertTrue(cue != null && cue.entityId() == p.getId() && cue.archetype() == CastPose.Archetype.GUARD.ordinal() && cue.phase() == CastPoseNet.HOLD, "an activated player relays its gesture under its own entity id");
            h.assertTrue(CastPoseNet.accept(p, intent, t + 1) == null, "a second hold inside the rate window is dropped");
            h.assertTrue(CastPoseNet.accept(p, new CastPoseNet.Intent(CastPose.Archetype.GUARD.ordinal(), CastPoseNet.END), t + 1) != null, "an end always goes through so a gesture can never stick");
            h.assertTrue(CastPoseNet.accept(p, intent, t + 3) != null, "the next hold passes once the window has elapsed");
            AbilityStorage.get(p).activated = false;
            h.assertTrue(CastPoseNet.accept(p, intent, t + 40) == null, "a player without an activated ability cannot relay");
            AbilityStorage.get(p).activated = true;
            p.setGameMode(GameType.SPECTATOR);
            h.assertTrue(CastPoseNet.accept(p, intent, t + 60) == null, "a spectator cannot relay");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void malformed_intents_and_cues_are_rejected_by_construction(GameTestHelper h) {
        int n = CastPose.Archetype.values().length;
        int[][] bad = {{-1, 0}, {n, 0}, {0, -1}, {0, 3}, {Integer.MAX_VALUE, 0}, {0, Integer.MIN_VALUE}};
        for (int[] b : bad) {
            boolean intent = false, cue = false;
            try { new CastPoseNet.Intent(b[0], b[1]); } catch (IllegalArgumentException refused) { intent = true; }
            try { new CastPoseNet.Cue(1, b[0], b[1]); } catch (IllegalArgumentException refused) { cue = true; }
            h.assertTrue(intent && cue, "rejected: " + b[0] + "," + b[1]);
        }
        boolean negativeEntity = false;
        try { new CastPoseNet.Cue(-1, 0, 0); } catch (IllegalArgumentException refused) { negativeEntity = true; }
        h.assertTrue(negativeEntity, "a negative entity id is rejected");
        h.assertTrue(HighPowerSkills.ALL.stream().allMatch(skill -> CastPose.of(skill.id()) != null), "every native high-power record has its native gesture");
        h.assertTrue(cn.academy.port.canon.CanonSkills.IDS.stream().allMatch(id -> cn.academy.port.canon.CanonVfxDesign.find(id) != null), "every canon record has the accepted-event design used by CanonPoseClient");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void skill_signature_codec_preserves_identity_and_rejects_borrowed_power(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PULSE_SHIELD); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            long now = h.getLevel().getGameTime();
            var intent = new CastPoseNet.Intent(CastPose.Archetype.GUARD.ordinal(), CastPoseNet.HOLD, "pulse_shield");
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
            CastPoseNet.Intent decoded;
            try {
                CastPoseNet.Intent.CODEC.encode(buffer, intent); decoded = CastPoseNet.Intent.CODEC.decode(buffer);
                h.assertTrue(intent.equals(decoded) && buffer.readableBytes() == 0, "v3 request consumes exact skill identity");
            } finally { buffer.release(); }
            var cue = CastPoseNet.accept(p, decoded, now);
            h.assertTrue(cue != null && cue.category().equals("electromaster") && cue.skill().equals("pulse_shield")
                    && cue.level() == NewSkillRuntime.Kind.PULSE_SHIELD.level, "server provides category and skill level");
            buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                CastPoseNet.Cue.CODEC.encode(buffer, cue); var received = CastPoseNet.Cue.CODEC.decode(buffer);
                h.assertTrue(received.equals(cue) && buffer.readableBytes() == 0, "v3 observer codec preserves category, level and skill");
            } finally { buffer.release(); }
            h.assertTrue(CastPoseNet.accept(p, decoded, now + 1) == null, "identified casts retain rate limit");
            h.assertTrue(CastPoseNet.accept(p, new CastPoseNet.Intent(CastPose.Archetype.GUARD.ordinal(), CastPoseNet.END, "pulse_shield"), now + 1) != null,
                    "identified END bypasses rate window");
            h.assertTrue(CastPoseNet.accept(p, new CastPoseNet.Intent(CastPose.Archetype.AIM.ordinal(), CastPoseNet.HOLD, "pulse_shield"), now + 10) == null,
                    "skill cannot borrow another action");
            h.assertTrue(CastPoseNet.accept(p, new CastPoseNet.Intent(CastPose.Archetype.CANOPY.ordinal(), CastPoseNet.HOLD, "multicore_canopy"), now + 12) == null,
                    "skill cannot borrow another category or level");
            h.assertTrue(CastPoseNet.accept(p, new CastPoseNet.Intent(CastPose.Archetype.GUARD.ordinal(), CastPoseNet.HOLD, "unknown_skill"), now + 14) == null,
                    "unregistered identity rejected");
            boolean malformed = false;
            try { new CastPoseNet.Intent(0, 0, "x".repeat(65)); } catch (IllegalArgumentException expected) { malformed = true; }
            h.assertTrue(malformed, "bounded skill string");
            h.succeed();
        });
    }

    private static final class Fixture {
        final GameTestHelper h; final NewSkillRuntime.Kind kind; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>();
        final List<Object> hooks = new ArrayList<>(); final Map<ServerPlayer, EmbeddedChannel> channels = new IdentityHashMap<>();
        final Map<ServerPlayer, Vec3> anchors = new IdentityHashMap<>(); final Map<ServerPlayer, Long> inputs = new IdentityHashMap<>();
        final Map<ServerPlayer, String> held = new IdentityHashMap<>(); boolean closed;
        Fixture(GameTestHelper h, NewSkillRuntime.Kind kind) {
            this.h = h; this.kind = kind;
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {}
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        ServerPlayer player(double x, double z) {
            var server = h.getLevel().getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "CP" + UUID.randomUUID().toString().substring(0, 10)), false);
            ServerPlayer p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            Connection connection = new Connection(PacketFlow.SERVERBOUND); EmbeddedChannel channel = new EmbeddedChannel(connection); channels.put(p, channel);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); drain(p);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 4; dz++) h.setBlock(BlockPos.containing(x + dx, 1, z + dz), Blocks.STONE);
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); anchors.put(p, h.absoluteVec(new Vec3(x + .5, 2, z + .5))); place(p);
            h.assertTrue(HighPowerCombat.enroll(p, kind.level), "explicit native connected enrollment");
            var s = AbilityStorage.get(p); s.selectCategory(kind.category); s.setLevel(kind.level);
            s.learn(HighPowerCategories.recoveryRoot(kind.category)); s.learn(kind.id); s.activated = true; s.overloadFine = true;
            h.assertTrue(s.presets.edit(0, 0, kind.id, s::learned), "real learned native key binding");
            HighPowerResources.initializeBudget(p); hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5)); return p;
        }
        void ready(Runnable action) { h.runAfterDelay(61, () -> { for (ServerPlayer p : players) { p.doTick(); place(p); } action.run(); }); }
        void place(ServerPlayer p) { Vec3 at = anchors.get(p); p.moveTo(at.x, at.y, at.z, 0, 0); p.setYHeadRot(0); p.yBodyRot = 0; p.setOnGround(true); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        long epoch(ServerPlayer p) { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        String wire(ServerPlayer p) { long input = inputs.getOrDefault(p, 0L) + 1; inputs.put(p, input); return new HighPowerInput.Input(0, input, p.getId(), epoch(p)).wire(); }
        boolean start(ServerPlayer p) { String wire = wire(p); held.put(p, wire); return NewSkillRuntime.start(p, wire); }
        void release(ServerPlayer p) { NewSkillRuntime.release(p, held.get(p)); }
        double cost(ServerPlayer p) { return NewSkillBudget.cp(budget(), HighPowerGrowth.values(p).position()); }
        NewSkillBudget.Skill budget() { return NewSkillBudget.Skill.valueOf(kind.name()); }
        Entity spawn(EntityType<?> type, double ahead) { return spawnAt(type, ahead, 0); }
        /** Spawns {@code ahead} metres in front of the player (looking toward +Z) and {@code side} metres to the right. */
        Entity spawnAt(EntityType<?> type, double ahead, double side) {
            Entity e = type.create(h.getLevel()); Vec3 at = anchors.values().iterator().next();
            e.setPos(at.x - side, at.y, at.z + ahead); h.assertTrue(h.getLevel().addFreshEntity(e), "entity joined the world"); entities.add(e); return e;
        }
        Arrow arrow(ServerPlayer p, double distance) {
            Arrow a = new Arrow(h.getLevel(), p.getX(), p.getEyeY(), p.getZ() + distance, new ItemStack(Items.ARROW), null);
            a.setNoGravity(true); a.setDeltaMovement(0, 0, -.6); h.assertTrue(h.getLevel().addFreshEntity(a), "arrow joined the world"); entities.add(a); return a;
        }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void drain(ServerPlayer p) {
            EmbeddedChannel channel = channels.get(p); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet;
            while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet); }
        }
        void close() {
            if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister);
            for (ServerPlayer p : players) { NewSkillRuntime.remove(p); DevelopmentController.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.server.getPlayerList().remove(p); }
            entities.forEach(Entity::discard); channels.values().forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }
}

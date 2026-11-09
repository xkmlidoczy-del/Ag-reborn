/* AcademyCraft 1.0.7 Groundshock server-context adaptation. See NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.AbilityDamage;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.GroundShockWave;
import cn.academy.port.core.LegacySingleKeyProtocol;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-owned held input. No client duration, position, target, mastery or costs are trusted. */
public final class GroundShock {
    public static final String ID = GroundShockWave.ID;
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final LegacySingleKeyProtocol.NonceLedger INPUTS = new LegacySingleKeyProtocol.NonceLedger();
    private static long nextToken;
    private GroundShock() {}
    private record Hold(ServerLevel level, AbilityProgress state, long started,
                        GroundShockWave.Parameters parameters, LegacySingleKeyServerIdentity identity, Set<ServerPlayer> audience) {}

    /** Key down is free; classic values and the random vertical speed are captured now. */
    public static boolean start(ServerPlayer player) { return startCaptured(player, 0); }

    /** Nonmutating ingress replay check before any cross-skill prepress cleanup. */
    public static boolean canAcceptInput(ServerPlayer player, long input) {
        return LegacySingleKeyServerIdentity.canClaim(INPUTS, player, input);
    }

    /** Wire key down must carry a fresh positive physical input. */
    public static boolean start(ServerPlayer player, long input) {
        return input > 0 && startCaptured(player, input);
    }

    private static boolean startCaptured(ServerPlayer player, long input) {if(cn.academy.port.AbilityConsumption.busy(player))return false;
        if (!onServerThread(player)) return false;
        var state = AbilityStorage.get(player);
        if (!canAct(player, state)) return false;
        Hold previous = HOLDS.get(player.getUUID());
        if (previous != null) {
            if (valid(player, state, previous)) return false;
            abort(previous.identity().owner());
        }
        if (input > 0 && !LegacySingleKeyServerIdentity.claim(INPUTS, player, input)) return false;
        var hold = new Hold(player.serverLevel(), state, player.serverLevel().getGameTime(),
                GroundShockWave.parameters(state.exp(ID), player.serverLevel().random.nextFloat()),
                new LegacySingleKeyServerIdentity(player, input, ++nextToken, state), audience(player));
        HOLDS.put(player.getUUID(), hold);
        send(player, hold, "ground_shock_start", null);
        return true;
    }

    /** Source has no maximum hold duration and does not require grounded preparation. */
    public static void tick(ServerPlayer player) {
        if (!onServerThread(player)) return;
        var hold = HOLDS.get(player.getUUID());
        if (hold != null && !valid(player, AbilityStorage.get(player), hold)) abort(player);
    }

    /** An accepted wire release additionally matches the issued owner incarnation and server token. */
    public static boolean release(ServerPlayer player, long input, long token, long epoch) {
        if (!onServerThread(player)) return false;
        var hold = HOLDS.get(player.getUUID());
        if (hold == null || !hold.identity().matches(player, input, token, epoch)) return false;
        return release(player, input);
    }

    /** A mismatched wire release cannot remove or spend a newer held context. */
    public static boolean release(ServerPlayer player, long input) {
        if (!onServerThread(player) || input <= 0) return false;
        var hold = HOLDS.get(player.getUUID());
        if (hold == null || !hold.identity().matches(player, input)) return false;
        return release(player);
    }

    /** Removes before spending so duplicate release cannot replay the terrain wave. */
    public static boolean release(ServerPlayer player) {
        if (!onServerThread(player)) return false;
        var owned = HOLDS.get(player.getUUID());
        if (owned != null && owned.identity().owner() != player) return false;
        var hold = HOLDS.remove(player.getUUID());
        if (hold == null) return false;
        var state = AbilityStorage.get(player);
        Vec3 direction = ClassicRaytrace.direction(player).normalize();
        if (!valid(player, state, hold) || !GroundShockWave.acceptsRelease(
                player.serverLevel().getGameTime() - hold.started()) || !player.onGround()
                || !finite(direction) || direction.x == 0 && direction.z == 0
                || !state.consumeSkill(ID,hold.parameters().cp(), hold.parameters().overload(),
                player.getAbilities().instabuild)) {
            send(player, hold, "ground_shock_abort", null);
            return false;
        }
        var world = player.serverLevel();
        GroundShockWave.Random random = new GroundShockWave.Random() {
            public double nextDouble() { return world.random.nextDouble(); }
            public float nextFloat() { return world.random.nextFloat(); }
        };
        var result = GroundShockWave.perform(state, hold.parameters(), new WorldAdapter(player), random,
                player.getX(), player.getY(), player.getZ(), direction.x, direction.y, direction.z);
        cn.academy.port.achievements.ClassicAchievements.trigger(player,"vecmanip.ground_shock");
        send(player, hold, "ground_shock", result);
        return true;
    }
    /** An accepted wire cancellation additionally matches the issued owner incarnation and server token. */
    public static boolean abort(ServerPlayer player, long input, long token, long epoch) {
        if (!onServerThread(player)) return false;
        var hold = HOLDS.get(player.getUUID());
        if (hold == null || !hold.identity().matches(player, input, token, epoch)) return false;
        return abort(player, input);
    }

    /** Ordinary cancellation retains nonce history for this connection. */
    public static boolean abort(ServerPlayer player, long input) {
        if (!onServerThread(player) || input <= 0) return false;
        var hold = HOLDS.get(player.getUUID());
        if (hold == null || !hold.identity().matches(player, input)) return false;
        abort(player);
        return true;
    }

    public static void abort(ServerPlayer player) {
        if (!onServerThread(player)) return;
        var owned = HOLDS.get(player.getUUID());
        if (owned != null && owned.identity().owner() != player) return;
        var hold = HOLDS.remove(player.getUUID());
        if (hold != null) send(player, hold, "ground_shock_abort", null);
    }
    /** Final owner/session disposal is the only per-player nonce-history reset. */
    public static void remove(ServerPlayer player) {
        if (!onServerThread(player)) return;
        abort(player);
        INPUTS.forget(player);
    }

    public static boolean active(ServerPlayer player) { return player != null && HOLDS.containsKey(player.getUUID()); }
    /** Transient contexts never survive restart. */
    public static void clear() { HOLDS.clear(); INPUTS.clear(); nextToken = 0; }
    private static boolean onServerThread(ServerPlayer player) {
        return player != null && player.serverLevel().getServer().isSameThread();
    }
    private static boolean canAct(ServerPlayer player, AbilityProgress state) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && GroundShockWave.mayStart(state) && finite(player.position())
                && finite(ClassicRaytrace.direction(player));
    }
    private static boolean valid(ServerPlayer player, AbilityProgress state, Hold hold) {
        return hold.identity().owner() == player
                && (hold.identity().input() == 0 || hold.identity().owns(player))
                && hold.level() == player.serverLevel() && hold.state() == state && canAct(player, state)
                && player.serverLevel().getGameTime() >= hold.started();
    }
    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
    private static Set<ServerPlayer> audience(ServerPlayer player) {
        var result = new LinkedHashSet<ServerPlayer>();
        for (var viewer : player.serverLevel().players()) if (viewer.distanceToSqr(player) <= 25 * 25) result.add(viewer);
        result.add(player); return Set.copyOf(result);
    }
    private static void send(ServerPlayer player, Hold hold, String kind, GroundShockWave.Result result) {
        var tag = new CompoundTag(); tag.putString("kind", kind);
        hold.identity().write(tag);
        if (result != null) {
            int[] coordinates = new int[result.affected().size() * 3];
            for (int i = 0; i < result.affected().size(); i++) {
                var cell = result.affected().get(i); coordinates[i * 3] = cell.x();
                coordinates[i * 3 + 1] = cell.y(); coordinates[i * 3 + 2] = cell.z();
            }
            tag.putIntArray("blocks", coordinates);
        }
        // Match source context recipients captured at start; every viewer receives termination.
        for (var viewer : hold.audience()) if (!viewer.hasDisconnected())
            PacketDistributor.sendToPlayer(viewer, new AcademyNetwork.ClientData(tag));
    }

    private static final class WorldAdapter implements GroundShockWave.World {
        private final ServerPlayer player;
        private final ServerLevel world;
        WorldAdapter(ServerPlayer player) { this.player = player; world = player.serverLevel(); }
        private static BlockPos pos(GroundShockWave.Cell cell) { return new BlockPos(cell.x(), cell.y(), cell.z()); }
        private boolean loaded(BlockPos pos) { return !world.isOutsideBuildHeight(pos) && world.hasChunkAt(pos); }
        public GroundShockWave.Block block(GroundShockWave.Cell cell) {
            var pos = pos(cell);
            if (!loaded(pos)) return new GroundShockWave.Block(GroundShockWave.Kind.AIR, 0, false);
            BlockState state = world.getBlockState(pos);
            var kind = state.isAir() ? GroundShockWave.Kind.AIR : state.is(Blocks.STONE) ? GroundShockWave.Kind.STONE
                    : state.is(Blocks.GRASS_BLOCK) ? GroundShockWave.Kind.GRASS : state.is(Blocks.FARMLAND)
                    ? GroundShockWave.Kind.FARMLAND : GroundShockWave.Kind.OTHER;
            return new GroundShockWave.Block(kind, state.getDestroySpeed(world, pos), state.liquid());
        }
        private boolean permitted(BlockPos pos) {
            return loaded(pos) && AcademyConfig.contextTerrain(world,"vecmanip","ground_shock") && world.mayInteract(player, pos)
                    && !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world, pos, world.getBlockState(pos), player)).isCanceled();
        }
        public void convert(GroundShockWave.Cell cell, GroundShockWave.Kind replacement) {
            var pos = pos(cell);
            if (permitted(pos)) world.setBlock(pos, (replacement == GroundShockWave.Kind.COBBLESTONE
                    ? Blocks.COBBLESTONE : Blocks.DIRT).defaultBlockState(), 3);
        }
        public boolean canBreak(GroundShockWave.Cell cell) { return permitted(pos(cell)); }
        public void destroy(GroundShockWave.Cell cell, boolean drop) {
            var pos = pos(cell); var state = world.getBlockState(pos);
            // Source repeats air set/sound no-ops. Preserve random ordering without modern air updates.
            if (state.isAir()) return;
            if (drop) Block.dropResources(state, world, pos, world.getBlockEntity(pos));
            var sound = state.getSoundType(world, pos, player);
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            world.playSound(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                    sound.getBreakSound(), SoundSource.BLOCKS, .5F, 1);
        }
        public Iterable<?> targets(GroundShockWave.Cell cell) {
            return world.getEntities(player, new AABB(cell.x() - .2, cell.y() - .2, cell.z() - .2,
                    cell.x() + 1.4, cell.y() + 2.2, cell.z() + 1.4),
                    entity -> entity instanceof LivingEntity || entity instanceof EnderDragonPart);
        }
        public void attackAndLaunch(Object value, float damage, float verticalSpeed) {
            var target = (Entity) value;
            // Keep source affected-target EXP even if damage is rejected; respect modern PvP/team gates.
            if (target instanceof Player && !AcademyConfig.ATTACK_PLAYERS.get()
                    || target instanceof ServerPlayer other && !other.canHarmPlayer(player)) return;
            AbilityDamage.attack(player, "vecmanip.ground_shock", target, damage, true);
            Vec3 motion = target.getDeltaMovement();
            target.setDeltaMovement(motion.x, verticalSpeed, motion.z);
            target.hasImpulse = true; target.hurtMarked = true;
        }
    }
}

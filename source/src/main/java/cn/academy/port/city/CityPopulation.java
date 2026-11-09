package cn.academy.port.city;

import cn.academy.port.city.CityCrowdPlan.Hub;
import cn.academy.port.city.CityCrowdPlan.Period;
import cn.academy.port.city.CityCrowdPlan.Role;
import cn.academy.port.city.CityNpcEntity.Activity;
import cn.academy.port.city.CityNpcEntity.Expression;
import cn.academy.port.train.TrainManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Maintains a bounded local crowd in already loaded city neighborhoods. */
@EventBusSubscriber(modid = "academy")
public final class CityPopulation {
    public static final int PLAYER_BUDGET = 32;
    public static final int CITY_BUDGET = 96;
    private static final int ACTIVE_RADIUS = 88;
    private static final int KEEP_RADIUS = 112;
    private static final int SPAWNS_PER_SECOND = 6;
    private static final int PATH_DECISIONS_PER_TICK = 2;
    private static final Map<ServerLevel, CityPopulation> LEVELS = new IdentityHashMap<>();

    private final ServerLevel level;
    private final long planSeed;
    private final Map<Hub, List<Resident>> groups = new LinkedHashMap<>();

    private CityPopulation(ServerLevel level, long planSeed) {
        this.level = level;
        this.planSeed = planSeed;
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(TrainManager.CITY)
                || !(level.getChunkSource().getGenerator() instanceof CityGreyboxGenerator generator)) return;
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) || level.players().isEmpty()) {
            clear(level);
            return;
        }
        LEVELS.computeIfAbsent(level, key -> new CityPopulation(key, generator.planSeed())).update();
    }

    @SubscribeEvent
    public static void unloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) LEVELS.remove(level);
    }

    private static void clear(ServerLevel level) {
        CityPopulation crowd = LEVELS.remove(level);
        if (crowd != null) {
            for (var group : crowd.groups.values()) group.forEach(resident -> resident.entity.discard());
        }
    }

    private void update() {
        long now = level.getGameTime();
        if (now % 20 == 0) reconcile(now);
        int decisions = PATH_DECISIONS_PER_TICK;
        for (var group : groups.values()) {
            for (Resident resident : group) {
                if (resident.entity.isRemoved()) continue;
                if (resident.entity.conversing()) continue;
                if (decisions > 0 && now >= resident.next) {
                    resident.advance(now);
                    decisions--;
                }
                if (now % 10 == resident.slot % 10) resident.faceCompanion();
            }
        }
    }

    private void reconcile(long now) {
        List<ServerPlayer> players = level.players();
        Period period = Period.at(level.getDayTime());
        Map<Hub, Double> nearby = new HashMap<>();
        for (var player : players) {
            if (Math.abs(player.getY()) > 56) continue;
            int cellX = Math.floorDiv(player.getBlockX(), CityCrowdPlan.NEIGHBORHOOD);
            int cellZ = Math.floorDiv(player.getBlockZ(), CityCrowdPlan.NEIGHBORHOOD);
            for (int x = cellX - 2; x <= cellX + 2; x++) {
                for (int z = cellZ - 2; z <= cellZ + 2; z++) {
                    Hub hub = new Hub(x, z);
                    if (hub.district() == 0) continue;
                    double distance = horizontalDistance(hub.x(), hub.z(), player);
                    if (distance <= ACTIVE_RADIUS * ACTIVE_RADIUS) nearby.merge(hub, distance, Math::min);
                }
            }
        }

        var groupIterator = groups.entrySet().iterator();
        List<Resident> present = new ArrayList<>();
        while (groupIterator.hasNext()) {
            var entry = groupIterator.next();
            var hub = entry.getKey();
            var group = entry.getValue();
            int target = CityCrowdPlan.profile(hub.district()).target(period);
            group.removeIf(resident -> {
                var entity = resident.entity;
                if (entity.isRemoved()) return true;
                double distance = nearestPlayer(entity.position());
                if (distance > KEEP_RADIUS * KEEP_RADIUS || (resident.slot >= target && distance > 100)) {
                    entity.discard();
                    return true;
                }
                return false;
            });
            if (group.isEmpty()) groupIterator.remove();
            present.addAll(group);
        }

        Map<ServerPlayer, Integer> counts = new IdentityHashMap<>();
        for (var player : players) {
            int count = 0;
            for (var resident : present) {
                if (resident.entity.distanceToSqr(player) <= KEEP_RADIUS * KEEP_RADIUS) count++;
            }
            counts.put(player, count);
        }
        var hubs = nearby.keySet().stream().sorted(Comparator.<Hub>comparingDouble(nearby::get)
                .thenComparingInt(Hub::cellX).thenComparingInt(Hub::cellZ)).toList();
        int spawned = 0;
        for (Hub hub : hubs) {
            if (present.size() >= CITY_BUDGET || spawned >= SPAWNS_PER_SECOND) break;
            var profile = CityCrowdPlan.profile(hub.district());
            var group = groups.computeIfAbsent(hub, key -> new ArrayList<>());
            int misses = 0;
            for (int slot = 0; slot < profile.target(period) && spawned < SPAWNS_PER_SECOND
                    && present.size() < CITY_BUDGET && misses < 3; slot++) {
                int id = slot;
                if (group.stream().anyMatch(resident -> resident.slot == id)) continue;
                Resident resident = spawn(hub, slot, profile, present, counts, now);
                if (resident != null) {
                    group.add(resident);
                    present.add(resident);
                    spawned++;
                } else misses++;
            }
        }
    }

    private Resident spawn(Hub hub, int slot, CityCrowdPlan.Profile profile, List<Resident> present,
                           Map<ServerPlayer, Integer> counts, long now) {
        Random random = new Random(hub.seed(level.getSeed(), slot));
        Role role = profile.roles().get(slot % profile.roles().size());
        var look = role.looks.get(random.nextInt(role.looks.size()));
        for (int attempt = 0; attempt < 25; attempt++) {
            int x = hub.x() + random.nextInt(45) - 22, z = hub.z() + random.nextInt(45) - 22;
            BlockPos at = usable(hub.district(), x, z, false);
            if (at == null) continue;
            Vec3 point = Vec3.atBottomCenterOf(at);
            double distance = nearestPlayer(point);
            if (distance < 64 || distance > ACTIVE_RADIUS * ACTIVE_RADIUS) continue;
            if (counts.entrySet().stream().anyMatch(entry -> entry.getValue() >= PLAYER_BUDGET
                    && entry.getKey().distanceToSqr(point) <= KEEP_RADIUS * KEEP_RADIUS)) continue;
            if (present.stream().anyMatch(other -> other.entity.distanceToSqr(point) < 2.25)) continue;
            ChunkPos chunk = new ChunkPos(at);
            if (present.stream().filter(other -> other.entity.chunkPosition().equals(chunk))
                    .limit(6).count() >= 6) continue;
            CityNpcEntity entity = CityNpcContent.NPC.get().create(level);
            if (entity == null) return null;
            entity.moveTo(point.x, point.y, point.z, random.nextFloat() * 360, 0);
            entity.setYHeadRot(entity.getYRot());
            entity.prepareAmbient(look, Activity.STAND);
            CityEspers.assign(entity,hub.district(),role,hub.seed(level.getSeed(),slot));
            entity.identity("crowd:"+hub.cellX()+":"+hub.cellZ()+":"+slot);
            if (!level.noCollision(entity) || !level.addFreshEntity(entity)) continue;
            counts.replaceAll((player, count) -> player.distanceToSqr(point) <= KEEP_RADIUS * KEEP_RADIUS ? count + 1 : count);
            return new Resident(hub, slot, at, role, entity, random, now + 10 + random.nextInt(45));
        }
        return null;
    }

    private static double horizontalDistance(int x, int z, ServerPlayer player) {
        double dx = player.getX() - x, dz = player.getZ() - z;
        return dx * dx + dz * dz;
    }

    private double nearestPlayer(Vec3 point) {
        double best = Double.MAX_VALUE;
        for (var player : level.players()) best = Math.min(best, player.distanceToSqr(point));
        return best;
    }

    private BlockPos usable(int district, int x, int z, boolean crossing) {
        var pos = CityCrowdPlan.ground(planSeed, district, x, z, crossing);
        if (pos == null || level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return null;
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP) ? pos : null;
    }

    private final class Resident {
        final Hub hub;
        final int slot;
        final BlockPos home;
        final Role role;
        final CityNpcEntity entity;
        final Random random;
        long next;
        long walkDeadline;
        boolean walking;

        Resident(Hub hub, int slot, BlockPos home, Role role, CityNpcEntity entity, Random random, long next) {
            this.hub = hub;
            this.slot = slot;
            this.home = home;
            this.role = role;
            this.entity = entity;
            this.random = random;
            this.next = next;
        }

        void advance(long now) {
            if (walking && !entity.getNavigation().isDone()) {
                if (now < walkDeadline) {
                    next = now + 10;
                    return;
                }
                entity.getNavigation().stop();
            }
            if (!walking && startWalk()) {
                walking = true;
                entity.perform(Activity.STAND, Expression.NEUTRAL);
                next = now + 40;
                walkDeadline = now + 160;
                return;
            }
            walking = false;
            if((hub.district()==2 || hub.district()==7 || hub.district()==20)
                    && CityCrowdPlan.Period.at(level.getDayTime())!=CityCrowdPlan.Period.NIGHT
                    && entity.esper()!=CityEspers.Profile.NONE && CityEspers.cooldown(entity)==0 && random.nextInt(8)==0) {
                CityEspers.practice(entity);next=now+70;return;
            }
            Activity action = role.activities.get(random.nextInt(role.activities.size()));
            if (action == Activity.CHAT && companion() == null) action = Activity.SCAN;
            Expression face = action == Activity.WAVE && random.nextInt(4) == 0 ? Expression.SURPRISED : action.expression;
            entity.perform(action, face);
            next = now + 50 + random.nextInt(role == Role.SHOPKEEPER || role == Role.PERFORMER ? 120 : 65);
            faceCompanion();
        }

        boolean startWalk() {
            for (int attempt = 0; attempt < 8; attempt++) {
                int x = home.getX() + random.nextInt(role.radius * 2 + 1) - role.radius;
                int z = home.getZ() + random.nextInt(role.radius * 2 + 1) - role.radius;
                BlockPos at = usable(hub.district(), x, z, false);
                if (at == null || entity.distanceToSqr(Vec3.atBottomCenterOf(at)) < 9) continue;
                var path = entity.getNavigation().createPath(at, 1, 24);
                if (path == null || !path.canReach()) continue;
                boolean safe = true;
                for (int index = 0; index < path.getNodeCount(); index++) {
                    var node = path.getNode(index);
                    var ground = usable(hub.district(), node.x, node.z, true);
                    if (ground == null || ground.getY() != node.y) { safe=false; break; }
                }
                if (!safe) continue;
                return entity.getNavigation().moveTo(path, role.speed);
            }
            return false;
        }

        CityNpcEntity companion() {
            return level.getEntitiesOfClass(CityNpcEntity.class, entity.getBoundingBox().inflate(5, 2, 5),
                    other -> other != entity && other.ambient()).stream()
                    .min(Comparator.comparingDouble(entity::distanceToSqr)).orElse(null);
        }

        void faceCompanion() {
            if (entity.activity() == Activity.CHAT) {
                var other = companion();
                if (other != null) entity.getLookControl().setLookAt(other);
            } else if (entity.activity() == Activity.WAVE) {
                var player = level.getNearestPlayer(entity, 8);
                if (player != null) entity.getLookControl().setLookAt(player);
            }
        }
    }
}

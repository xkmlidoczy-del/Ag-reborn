package cn.academy.port.train;

import cn.academy.port.city.CityBlocks;
import cn.academy.port.city.CityPlatformGate;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Independent airport shuttle, using the same entity, seats and phase indicators as the express. */
@EventBusSubscriber(modid = "academy")
public final class AirportTrainManager {
    private static final TrainCarEntity[] CARS = new TrainCarEntity[3];
    private static final TicketType<ChunkPos> RIDE_TICKET = TicketType.create(
            "academy_airport_train", Comparator.comparingLong(ChunkPos::toLong));
    private static final Set<ChunkPos> TICKETS = new HashSet<>();
    private record Cue(int station, TrainLine.Phase phase) {}
    private static final Map<ServerPlayer, Cue> LAST_CUE = new WeakHashMap<>();
    private static double offset;

    private AirportTrainManager() {}

    public static AirportLine.State state(ServerLevel level) { return AirportLine.at(level.getGameTime() / 20.0 + offset); }
    private static Component stationName(int station) {
        return Component.translatable(station == 0 ? "ac.train.station.airport_interchange" : "ac.train.station.airport_terminal");
    }
    private static boolean occupied() {
        for (var car : CARS) if (car != null && !car.isRemoved() && car.isVehicle()) return true;
        return false;
    }
    private static TrainCarEntity car(int role) { return role >= 0 && role < CARS.length ? CARS[role] : null; }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().getLevel(TrainManager.CITY);
        if (level == null) return;
        var state = state(level);
        var center = state.point();
        boolean occupied = occupied();
        double range = car(1) == null ? 220 : 320;
        boolean near = occupied || level.players().stream().anyMatch(p ->
                Math.abs(p.getX() - center.x()) < range && Math.abs(p.getZ() - center.z()) < range);
        updateTickets(level, state, occupied);
        if (near) {
            for (int role = 0; role < CARS.length; role++) {
                TrainCarEntity car = CARS[role];
                if (car == null || car.isRemoved() || car.level() != level) {
                    car = new TrainCarEntity(TrainContent.CAR.get(), level);
                    car.role(role); car.airport(true); place(car, state);
                    CARS[role] = level.addFreshEntity(car) ? car : null;
                } else place(car, state);
            }
        } else release();
        if (level.getGameTime() % 5 == 0) updateGates(level, state);
        if (level.getGameTime() % 20 == 0) showStatus(level, state);
    }

    private static void place(TrainCarEntity car, AirportLine.State state) {
        var point = AirportLine.point(state.distance() + (car.role() - 1) * TrainLine.CAR_PITCH);
        car.setOldPosAndRot();
        car.setYRot((float) (point.yaw() + (car.role() == 0 ? 180 : 0)));
        car.setPos(point.x(), AirportLine.RAIL_Y, point.z());
        car.setDeltaMovement(point.dx() * state.direction() * state.speed() / 20.0, 0,
                point.dz() * state.direction() * state.speed() / 20.0);
        car.servicePhase(state.phase());
        for (var passenger : car.getPassengers()) car.positionRider(passenger);
    }

    private static void updateTickets(ServerLevel level, AirportLine.State state, boolean occupied) {
        if (!occupied && TICKETS.isEmpty()) return;
        Set<ChunkPos> wanted = new HashSet<>();
        if (occupied) for (int step = -3; step <= 6; step++) {
            var p = AirportLine.point(state.distance() + state.direction() * step * 16);
            int cx = Math.floorDiv((int) Math.floor(p.x()), 16), cz = Math.floorDiv((int) Math.floor(p.z()), 16);
            // Adjacent chunks cover the width of the rotating three-car consist on the curve.
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) wanted.add(new ChunkPos(cx + dx, cz + dz));
        }
        var chunks = level.getChunkSource();
        for (var pos : TICKETS) if (!wanted.contains(pos)) chunks.removeRegionTicket(RIDE_TICKET, pos, 1, pos);
        for (var pos : wanted) if (!TICKETS.contains(pos)) chunks.addRegionTicket(RIDE_TICKET, pos, 1, pos);
        TICKETS.clear(); TICKETS.addAll(wanted);
    }

    private static void release() {
        for (int i = 0; i < CARS.length; i++) {
            if (CARS[i] != null && !CARS[i].isRemoved() && !CARS[i].isVehicle()) CARS[i].discard();
            CARS[i] = null;
        }
    }

    private static BlockPos bay(int station, int role, int side, int y) {
        int a = (int) Math.round((role - 1) * TrainLine.CAR_PITCH);
        return station == 0 ? new BlockPos(1040 + side, y, 300 - a) : new BlockPos(1560 + a, y, -1790 + side);
    }

    private static void updateGates(ServerLevel level, AirportLine.State state) {
        for (int station = 0; station <= 1; station++) for (int role = 0; role < 3; role++)
            for (int side : new int[]{-3, 3}) for (int offset = -2; offset <= 2; offset++) {
                var centre = bay(station, role, side, TrainLine.RAIL_Y);
                var lower = station == 0 ? centre.offset(0, 0, offset) : centre.offset(offset, 0, 0);
                if (!level.hasChunkAt(lower)) continue;
                for (int y = 0; y <= 1; y++) {
                    var pos = lower.above(y); var block = level.getBlockState(pos);
                    boolean open = state.station() == station && state.boarding();
                    if (block.is(CityPlatformGate.GATE.get()) && block.getValue(DoorBlock.OPEN) != open)
                        level.setBlock(pos, block.setValue(DoorBlock.OPEN, open), 2);
                }
            }
    }

    private static void showStatus(ServerLevel level, AirportLine.State state) {
        for (var player : level.players()) {
            boolean rider = player.getVehicle() instanceof TrainCarEntity car && car.airport();
            int station = Math.abs(player.getY() - TrainLine.RAIL_Y) < 4
                    ? stationNear(player.getX(), player.getZ(), 60, 12) : -1;
            if (!rider && station < 0) { LAST_CUE.remove(player); continue; }
            int relevant = state.station() >= 0 ? state.station()
                    : state.phase() == TrainLine.Phase.DEPARTING ? 1 - state.next() : state.next();
            if (rider || station == relevant && state.phase() != TrainLine.Phase.CRUISE) {
                var cue = new Cue(relevant, state.phase());
                if (!cue.equals(LAST_CUE.put(player, cue))) {
                    float pitch = TrainPresentation.cuePitch(state.phase());
                    if (pitch > 0) player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, .6f, pitch);
                }
            } else LAST_CUE.remove(player);
            Component message;
            if (rider || station == state.station()) {
                message = state.station() < 0 ? Component.translatable("ac.train.next", stationName(state.next()), (int) Math.ceil(state.secondsLeft()))
                        : state.boarding() ? Component.translatable("ac.train.boarding", stationName(state.station()), stationName(state.next()), (int) Math.ceil(state.secondsLeft()))
                        : state.phase() == TrainLine.Phase.CLOSING ? Component.translatable("ac.train.closing", stationName(state.next()), (int) Math.ceil(state.secondsLeft()))
                        : Component.translatable("ac.train.arrived", stationName(state.station()));
            } else {
                message = Component.translatable("ac.train.eta", stationName(station), (int) Math.ceil(AirportLine.arrivalIn(station, level.getGameTime() / 20.0 + offset)));
            }
            player.displayClientMessage(message, true);
        }
    }

    private static int stationNear(double x, double z, double along, double cross) {
        if (Math.abs(x - 1040) <= cross && Math.abs(z - 300) <= along) return 0;
        if (Math.abs(x - 1560) <= along && Math.abs(z + 1790) <= cross) return 1;
        return -1;
    }

    static int panelRole(int station, BlockPos pos) {
        if (station < 0 || pos.getY() != TrainLine.RAIL_Y) return -1;
        // Older generated stations keep their panels at ±7; newly generated ones use ±9.
        for (int role = 0; role < 3; role++) for (int side : new int[]{-9, -7, 7, 9})
            if (pos.equals(bay(station, role, side, TrainLine.RAIL_Y))) return role;
        return -1;
    }
    static int panelStation(BlockPos pos) { return stationNear(pos.getX(), pos.getZ(), 50, 9); }
    static TrainCarEntity panelCar(int role) { return car(role); }

    static boolean tryBoard(ServerPlayer player, TrainCarEntity car) {
        var level = player.serverLevel();
        if (!level.dimension().equals(TrainManager.CITY) || player.isSpectator() || player.isPassenger()
                || !player.isAlive() || !player.onGround() || car.level() != level || !car.isAlive()
                || car(car.role()) != car) return false;
        var state = state(level);
        if (!state.boarding() || state.station() < 0) {
            player.displayClientMessage(Component.translatable("ac.train.wait_board"), true); return false;
        }
        var center = bay(state.station(), car.role(), 0, TrainLine.RAIL_Y);
        double along = state.station() == 0 ? Math.abs(player.getZ() - center.getZ() - .5) : Math.abs(player.getX() - center.getX() - .5);
        double cross = state.station() == 0 ? player.getX() - 1040 : player.getZ() + 1790;
        if (along > 7 || Math.abs(cross) < 2.5 || Math.abs(cross) > 9 || Math.abs(player.getY() - TrainLine.RAIL_Y) > .65) {
            player.displayClientMessage(Component.translatable("ac.train.board_platform"), true); return false;
        }
        if (!car.hasSeat()) { player.displayClientMessage(Component.translatable("ac.train.full"), true); return false; }
        return car.board(player, cross < 0);
    }

    static Vec3 exit(TrainCarEntity car, LivingEntity rider) {
        if (!(car.level() instanceof ServerLevel level) || !level.dimension().equals(TrainManager.CITY)) return null;
        int station = state(level).station();
        if (station < 0) return null;
        int side = car.preferredNorth(rider) ? -1 : 1;
        for (int cross : new int[]{5 * side, 5 * -side, 6 * side, 6 * -side}) for (int delta : new int[]{0, -1, 1, -2, 2, -3, 3}) {
            var base = bay(station, car.role(), cross, TrainLine.RAIL_Y);
            var pos = station == 0 ? base.offset(0, 0, delta) : base.offset(delta, 0, 0);
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) continue;
            Vec3 safe = DismountHelper.findSafeDismountLocation(rider.getType(), level, pos, true);
            if (safe != null && Math.abs(safe.y - TrainLine.RAIL_Y) < .01) return safe;
        }
        return null;
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        for (int i = 0; i < CARS.length; i++) CARS[i] = null;
        TICKETS.clear(); LAST_CUE.clear(); offset = 0;
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("academy_airport").requires(s -> s.hasPermission(2))
                .then(Commands.literal("goto").then(Commands.argument("station", IntegerArgumentType.integer(0, 1)).executes(c -> {
                    var p = c.getSource().getPlayerOrException(); var level = c.getSource().getServer().getLevel(TrainManager.CITY);
                    if (level == null || p.isPassenger()) return 0;
                    int station = IntegerArgumentType.getInteger(c, "station");
                    var target = bay(station, 1, -8, TrainLine.RAIL_Y);
                    p.teleportTo(level, target.getX() + .5, target.getY(), target.getZ() + .5, p.getYRot(), 0);
                    return 1;
                })))
                .then(Commands.literal("arrive").then(Commands.argument("station", IntegerArgumentType.integer(0, 1))
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0, 600)).executes(c -> {
                            var level = c.getSource().getServer().getLevel(TrainManager.CITY);
                            if (level == null || occupied()) return 0;
                            int station = IntegerArgumentType.getInteger(c, "station");
                            double delay = DoubleArgumentType.getDouble(c, "seconds");
                            offset = AirportLine.dwellStart(station) - level.getGameTime() / 20.0 - delay;
                            c.getSource().sendSuccess(() -> Component.translatable("ac.train.eta", stationName(station), (int) Math.ceil(delay)), false);
                            return 1;
                        }))))
                .then(Commands.literal("board").executes(c -> {
                    var player = c.getSource().getPlayerOrException(); TrainCarEntity best = null;
                    for (var car : CARS) if (car != null && car.isAlive() && car.level() == player.level()
                            && (best == null || car.distanceToSqr(player) < best.distanceToSqr(player))) best = car;
                    return best != null && tryBoard(player, best) ? 1 : 0;
                })));
    }
}

package cn.academy.port.train;

import cn.academy.port.city.CityTransit;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "academy")
public final class TrainManager {
    public static final ResourceKey<Level> CITY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("academy", "story_city_map"));
    private static final double SPAWN_RANGE = 220, KEEP_RANGE = 320;
    private static final TrainCarEntity[] CARS = new TrainCarEntity[3];
    private static final TicketType<ChunkPos> RIDE_TICKET = TicketType.create(
            "academy_city_train", Comparator.comparingLong(ChunkPos::toLong));
    private static final Set<ChunkPos> TICKETS = new HashSet<>();
    private static double offset;

    private TrainManager() {}

    static double seconds(ServerLevel level) { return level.getGameTime() / 20.0 + offset; }
    public static TrainLine.State state(ServerLevel level) { return TrainLine.at(seconds(level)); }
    static TrainCarEntity car(int role) { return role >= 0 && role < CARS.length ? CARS[role] : null; }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().getLevel(CITY);
        if (level == null) return;
        double time = seconds(level);
        TrainLine.State state = TrainLine.at(time);
        boolean occupied = occupied();
        double range = car(1) == null ? SPAWN_RANGE : KEEP_RANGE;
        boolean near = occupied || level.players().stream().anyMatch(p ->
                Math.abs(p.getX() - state.x()) < range && Math.abs(p.getZ() - TrainLine.Z) < range);
        updateTickets(level, state, occupied);
        if (near) {
            for (int role = 0; role < CARS.length; role++) {
                TrainCarEntity car = CARS[role];
                if (car == null || car.isRemoved() || car.level() != level) {
                    car = new TrainCarEntity(TrainContent.CAR.get(), level);
                    car.role(role);
                    place(car, state);
                    CARS[role] = level.addFreshEntity(car) ? car : null;
                } else {
                    place(car, state);
                }
            }
        } else {
            release();
        }
        TrainPresentation.tick(level, state, time);
    }

    private static void place(TrainCarEntity car, TrainLine.State state) {
        double x = state.x() + (car.role() - 1) * TrainLine.CAR_PITCH;
        car.setOldPosAndRot();
        car.setYRot(car.role() == 0 ? 90f : -90f);
        car.setPos(x, TrainLine.RAIL_Y, TrainLine.Z);
        car.setDeltaMovement(state.dir() * state.speed() / 20.0, 0, 0);
        car.servicePhase(state.phase());
        for (var passenger : car.getPassengers()) car.positionRider(passenger);
    }

    private static boolean occupied() {
        for (TrainCarEntity car : CARS) if (car != null && !car.isRemoved() && car.isVehicle()) return true;
        return false;
    }

    private static void updateTickets(ServerLevel level, TrainLine.State state, boolean occupied) {
        if (!occupied && TICKETS.isEmpty()) return;
        Set<ChunkPos> wanted = new HashSet<>();
        if (occupied) {
            int x = (int) Math.floor(state.x() / 16), z = (int) TrainLine.Z >> 4;
            for (int distance = -2; distance <= 6; distance++) wanted.add(new ChunkPos(x + state.dir() * distance, z));
        }
        var chunks = level.getChunkSource();
        for (ChunkPos pos : TICKETS) if (!wanted.contains(pos)) chunks.removeRegionTicket(RIDE_TICKET, pos, 1, pos);
        for (ChunkPos pos : wanted) if (!TICKETS.contains(pos)) chunks.addRegionTicket(RIDE_TICKET, pos, 1, pos);
        TICKETS.clear();
        TICKETS.addAll(wanted);
    }

    private static void release() {
        for (int role = 0; role < CARS.length; role++) {
            TrainCarEntity car = CARS[role];
            if (car != null && !car.isRemoved() && !car.isVehicle()) car.discard();
            CARS[role] = null;
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        for (int role = 0; role < CARS.length; role++) CARS[role] = null;
        TICKETS.clear();
        TrainPresentation.clear();
        offset = 0;
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        int last = TrainLine.STATIONS.size() - 1;
        event.getDispatcher().register(Commands.literal("academy_train").requires(s -> s.hasPermission(2))
                .then(Commands.literal("goto").then(Commands.argument("station", IntegerArgumentType.integer(0, last)).executes(c -> {
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    ServerLevel level = c.getSource().getServer().getLevel(CITY);
                    if (level == null || player.isPassenger()) return 0;
                    int station = IntegerArgumentType.getInteger(c, "station");
                    player.teleportTo(level, TrainLine.STATIONS.get(station).x() + 0.5,
                            TrainLine.RAIL_Y, TrainLine.Z - 8, player.getYRot(), 0);
                    return 1;
                })))
                .then(Commands.literal("arrive").then(Commands.argument("station", IntegerArgumentType.integer(0, last))
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0, 600)).executes(c -> {
                            ServerLevel level = c.getSource().getServer().getLevel(CITY);
                            if (level == null || occupied()) return 0;
                            int station = IntegerArgumentType.getInteger(c, "station");
                            double delay = DoubleArgumentType.getDouble(c, "seconds");
                            offset = TrainLine.dwellStart(station, true) - level.getGameTime() / 20.0 - delay;
                            c.getSource().sendSuccess(() -> Component.translatable("ac.train.eta",
                                    TrainPresentation.name(station), (int) Math.ceil(delay)), false);
                            return 1;
                        }))))
                .then(Commands.literal("info").executes(c -> {
                    ServerLevel level = c.getSource().getServer().getLevel(CITY);
                    if (level == null) return 0;
                    var state = state(level);
                    c.getSource().sendSuccess(() -> TrainPresentation.riderStatus(state), false);
                    return 1;
                }))
                .then(Commands.literal("board").executes(c -> {
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    TrainCarEntity best = null;
                    for (TrainCarEntity car : CARS) {
                        if (car != null && car.isAlive() && car.level() == player.level()
                                && (best == null || car.distanceToSqr(player) < best.distanceToSqr(player))) best = car;
                    }
                    return best != null && TrainPassengers.tryBoard(player, best) ? 1 : 0;
                })));
    }
}

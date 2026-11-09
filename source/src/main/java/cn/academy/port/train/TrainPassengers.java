package cn.academy.port.train;

import cn.academy.port.city.CityBlocks;
import cn.academy.port.city.CityTransit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = "academy")
public final class TrainPassengers {
    private TrainPassengers() {}

    @SubscribeEvent
    public static void boardingPanel(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getLevel().dimension().equals(TrainManager.CITY)) return;
        BlockPos pos = event.getPos();
        int metroAirportStation = CityMetroAirportTrainManager.panelStation(pos);
        int metroAirportRole = CityMetroAirportTrainManager.panelRole(metroAirportStation, pos);
        if (metroAirportRole >= 0 && event.getLevel().getBlockState(pos).is(CityBlocks.block("led_screen"))) {
            if (event.getEntity() instanceof ServerPlayer player && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 25) {
                TrainCarEntity metroCar = CityMetroAirportTrainManager.panelCar(metroAirportRole);
                if (metroCar == null || CityMetroAirportTrainManager.state(player.serverLevel()).station() != metroAirportStation)
                    player.displayClientMessage(Component.translatable("ac.train.wait_board"), true);
                else CityMetroAirportTrainManager.tryBoard(player, metroCar);
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
            return;
        }
        int metroNorthStation = CityMetroNorthTrainManager.panelStation(pos);
        int metroNorthRole = CityMetroNorthTrainManager.panelRole(metroNorthStation, pos);
        if (metroNorthRole >= 0 && event.getLevel().getBlockState(pos).is(CityBlocks.block("led_screen"))) {
            if (event.getEntity() instanceof ServerPlayer player && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 25) {
                TrainCarEntity metroCar = CityMetroNorthTrainManager.panelCar(metroNorthRole);
                if (metroCar == null || CityMetroNorthTrainManager.state(player.serverLevel()).station() != metroNorthStation)
                    player.displayClientMessage(Component.translatable("ac.train.wait_board"), true);
                else CityMetroNorthTrainManager.tryBoard(player, metroCar);
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
            return;
        }
        int metroStation = CityMetroTrainManager.panelStation(pos);
        int metroRole = CityMetroTrainManager.panelRole(metroStation, pos);
        if (metroRole >= 0 && event.getLevel().getBlockState(pos).is(CityBlocks.block("led_screen"))) {
            if (event.getEntity() instanceof ServerPlayer player && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 25) {
                TrainCarEntity metroCar = CityMetroTrainManager.panelCar(metroRole);
                if (metroCar == null || CityMetroTrainManager.state(player.serverLevel()).station() != metroStation)
                    player.displayClientMessage(Component.translatable("ac.train.wait_board"), true);
                else CityMetroTrainManager.tryBoard(player, metroCar);
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
            return;
        }
        int ringStation = CityRingTrainManager.panelStation(pos);
        int ringRole = CityRingTrainManager.panelRole(ringStation, pos);
        if (ringRole >= 0 && event.getLevel().getBlockState(pos).is(CityBlocks.block("led_screen"))) {
            if (event.getEntity() instanceof ServerPlayer player && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 25) {
                TrainCarEntity ringCar = CityRingTrainManager.panelCar(player.serverLevel(), ringStation, ringRole);
                if (ringCar == null)
                    player.displayClientMessage(Component.translatable("ac.train.wait_board"), true);
                else CityRingTrainManager.tryBoard(player, ringCar);
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
            return;
        }
        int northStation = NorthSouthTrainManager.panelStation(pos);
        int northRole = NorthSouthTrainManager.panelRole(northStation, pos);
        if (northRole >= 0 && event.getLevel().getBlockState(pos).is(CityBlocks.block("led_screen"))) {
            if (event.getEntity() instanceof ServerPlayer player && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 25) {
                TrainCarEntity northCar = NorthSouthTrainManager.panelCar(northRole);
                if (northCar == null || NorthSouthTrainManager.state(player.serverLevel()).station() != northStation)
                    player.displayClientMessage(Component.translatable("ac.train.wait_board"), true);
                else NorthSouthTrainManager.tryBoard(player, northCar);
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
            return;
        }
        int airportStation = AirportTrainManager.panelStation(pos);
        int airportRole = AirportTrainManager.panelRole(airportStation, pos);
        if (airportRole >= 0 && event.getLevel().getBlockState(pos).is(CityBlocks.block("led_screen"))) {
            if (event.getEntity() instanceof ServerPlayer player && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 25) {
                TrainCarEntity airportCar = AirportTrainManager.panelCar(airportRole);
                if (airportCar == null || AirportTrainManager.state(player.serverLevel()).station() != airportStation)
                    player.displayClientMessage(Component.translatable("ac.train.wait_board"), true);
                else AirportTrainManager.tryBoard(player, airportCar);
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
            return;
        }
        int station = TrainLine.stationNear(pos.getX(), CityTransit.PLATFORM_HALF_LENGTH);
        int role = CityTransit.boardCar(station, pos.getX(), pos.getY(), pos.getZ());
        if (role < 0 || !event.getLevel().getBlockState(pos).is(CityBlocks.block("led_screen"))) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 25) return;
            TrainCarEntity car = TrainManager.car(role);
            if (car == null || TrainManager.state(player.serverLevel()).station() != station) {
                player.displayClientMessage(Component.translatable("ac.train.wait_board"), true);
            } else {
                tryBoard(player, car);
            }
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        event.setCanceled(true);
    }

    static boolean tryBoard(ServerPlayer player, TrainCarEntity car) {
        if (car.metroAirport()) return CityMetroAirportTrainManager.tryBoard(player, car);
        if (car.metroNorth()) return CityMetroNorthTrainManager.tryBoard(player, car);
        if (car.metro()) return CityMetroTrainManager.tryBoard(player, car);
        if (car.ring()) return CityRingTrainManager.tryBoard(player, car);
        if (car.northSouth()) return NorthSouthTrainManager.tryBoard(player, car);
        if (car.airport()) return AirportTrainManager.tryBoard(player, car);
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(TrainManager.CITY) || player.isSpectator() || player.isPassenger()
                || !player.isAlive() || !player.onGround() || car.level() != level || !car.isAlive() || TrainManager.car(car.role()) != car) return false;
        var state = TrainManager.state(level);
        if (!state.boarding() || state.station() < 0) {
            player.displayClientMessage(Component.translatable("ac.train.wait_board"), true);
            return false;
        }
        int station = state.station();
        double dx = Math.abs(player.getX() - CityTransit.bayX(station, car.role()) - 0.5);
        double dz = Math.abs(player.getZ() - TrainLine.Z);
        if (dx > 7 || dz < 2.5 || dz > 9 || Math.abs(player.getY() - TrainLine.RAIL_Y) > 0.65) {
            player.displayClientMessage(Component.translatable("ac.train.board_platform"), true);
            return false;
        }
        if (!car.hasSeat()) {
            player.displayClientMessage(Component.translatable("ac.train.full"), true);
            return false;
        }
        return car.board(player, player.getZ() < TrainLine.Z);
    }

    @SubscribeEvent
    public static void dismount(EntityMountEvent event) {
        if (!event.isDismounting() || !(event.getEntityBeingMounted() instanceof TrainCarEntity car)
                || !(event.getEntityMounting() instanceof ServerPlayer player)) return;
        if (!car.isAlive() || !player.isAlive() || player.isRemoved() || player.hasDisconnected() || car.level() != player.level()) return;
        boolean boarding = car.metroAirport() ? CityMetroAirportTrainManager.state(player.serverLevel()).boarding()
                : car.metroNorth() ? CityMetroNorthTrainManager.state(player.serverLevel()).boarding()
                : car.metro() ? CityMetroTrainManager.state(player.serverLevel()).boarding()
                : car.ring() ? CityRingTrainManager.state(player.serverLevel(),car) != null
                        && CityRingTrainManager.state(player.serverLevel(),car).boarding()
                : car.northSouth() ? NorthSouthTrainManager.state(player.serverLevel()).boarding()
                : car.airport() ? AirportTrainManager.state(player.serverLevel()).boarding()
                : TrainManager.state(player.serverLevel()).boarding();
        if (!boarding) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("ac.train.no_dismount"), true);
        } else if (exit(car, player) == null) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("ac.train.exit_blocked"), true);
        }
    }

    static Vec3 exit(TrainCarEntity car, LivingEntity rider) {
        if (car.metroAirport()) return CityMetroAirportTrainManager.exit(car, rider);
        if (car.metroNorth()) return CityMetroNorthTrainManager.exit(car, rider);
        if (car.metro()) return CityMetroTrainManager.exit(car, rider);
        if (car.ring()) return CityRingTrainManager.exit(car, rider);
        if (car.northSouth()) return NorthSouthTrainManager.exit(car, rider);
        if (car.airport()) return AirportTrainManager.exit(car, rider);
        if (!(car.level() instanceof ServerLevel level) || !level.dimension().equals(TrainManager.CITY)) return null;
        int station = TrainManager.state(level).station();
        if (station < 0) return null;
        int x = CityTransit.bayX(station, car.role());
        int side = car.preferredNorth(rider) ? -1 : 1;
        for (int z : new int[]{5 * side, 5 * -side, 6 * side, 6 * -side}) {
            for (int dx : new int[]{0, -1, 1, -2, 2, -3, 3}) {
                BlockPos pos = new BlockPos(x + dx, TrainLine.RAIL_Y, (int) TrainLine.Z + z);
                if (!level.hasChunkAt(pos) || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) continue;
                Vec3 safe = DismountHelper.findSafeDismountLocation(rider.getType(), level, pos, true);
                if (safe != null && Math.abs(safe.y - TrainLine.RAIL_Y) < 0.01) return safe;
            }
        }
        return null;
    }
}

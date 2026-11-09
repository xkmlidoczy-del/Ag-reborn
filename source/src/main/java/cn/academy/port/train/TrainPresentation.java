package cn.academy.port.train;

import java.util.Map;
import java.util.WeakHashMap;
import cn.academy.port.city.CityTransit;
import cn.academy.port.city.CityPlatformGate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.DoorBlock;

final class TrainPresentation {
    private record Cue(int station, TrainLine.Phase phase) {}
    private static final Map<ServerPlayer, Cue> LAST_CUE = new WeakHashMap<>();

    private TrainPresentation() {}

    static Component name(int index) {
        return Component.translatable(TrainLine.STATIONS.get(index).translationKey());
    }

    static Component riderStatus(TrainLine.State state) {
        int seconds = (int) Math.ceil(state.secondsLeft());
        if (state.station() < 0) return Component.translatable("ac.train.next", name(state.next()), seconds);
        return switch (state.phase()) {
            case ARRIVED -> Component.translatable("ac.train.arrived", name(state.station()));
            case CLOSING -> Component.translatable("ac.train.closing", name(state.terminus()), seconds);
            default -> Component.translatable("ac.train.boarding", name(state.station()), name(state.terminus()), seconds);
        };
    }

    static void tick(ServerLevel level, TrainLine.State state, double time) {
        if (level.getGameTime() % 5 == 0) updateGates(level, state);
        int relevant = state.station() >= 0 ? state.station()
                : state.phase() == TrainLine.Phase.DEPARTING ? state.next() - state.dir() : state.next();
        boolean showTime = level.getGameTime() % 20 == 0;
        for (ServerPlayer player : level.players()) {
            boolean rider = player.getVehicle() instanceof TrainCarEntity car && !car.airport() && !car.northSouth() && !car.ring() && !car.metro() && !car.metroNorth() && !car.metroAirport();
            int station = Math.abs(player.getY() - TrainLine.RAIL_Y) < 5 && Math.abs(player.getZ() - TrainLine.Z) <= 28
                    ? TrainLine.stationNear(player.getX(), 90) : -1;
            boolean platformCue = station == relevant && state.phase() != TrainLine.Phase.CRUISE;
            if (rider || platformCue) {
                Cue cue = new Cue(relevant, state.phase());
                if (!cue.equals(LAST_CUE.put(player, cue))) announce(player, state);
            } else {
                LAST_CUE.remove(player);
            }
            if (!showTime) continue;
            if (!rider && station >= 0 && state.station() == station && state.boarding()) {
                player.displayClientMessage(Component.translatable("ac.train.platform_open", name(station), name(state.terminus()),
                        (int) Math.ceil(state.secondsLeft())), true);
            } else if (rider || (station >= 0 && state.station() == station)) {
                player.displayClientMessage(riderStatus(state), true);
            } else if (platformCue && state.phase() == TrainLine.Phase.APPROACHING) {
                player.displayClientMessage(Component.translatable("ac.train.approach", name(station)), true);
            } else if (station >= 0) {
                player.displayClientMessage(Component.translatable("ac.train.eta", name(station),
                        (int) Math.ceil(TrainLine.arrivalIn(station, time))), true);
            }
        }
    }

    private static void updateGates(ServerLevel level, TrainLine.State state) {
        for (int station = 0; station < TrainLine.STATIONS.size(); station++) {
            boolean open = state.station() == station && state.boarding();
            for (int car = 0; car < 3; car++) for (int dx = -2; dx <= 2; dx++) {
                int x = CityTransit.bayX(station, car) + dx;
                for (int side : new int[]{-3, 3}) {
                    var lower = new BlockPos(x, TrainLine.RAIL_Y, (int) TrainLine.Z + side);
                    if (!level.hasChunkAt(lower)) continue;
                    for (int dy = 0; dy <= 1; dy++) {
                        var pos = lower.above(dy); var block = level.getBlockState(pos);
                        if (block.is(CityPlatformGate.GATE.get()) && block.getValue(DoorBlock.OPEN) != open)
                            level.setBlock(pos, block.setValue(DoorBlock.OPEN, open), 2);
                    }
                }
            }
        }
    }

    private static void announce(ServerPlayer player, TrainLine.State state) {
        switch (state.phase()) {
            case APPROACHING -> {
                player.displayClientMessage(Component.translatable("ac.train.approach", name(state.next())), true);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.65f, 0.88f);
            }
            case ARRIVED -> player.displayClientMessage(riderStatus(state), true);
            case BOARDING -> player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.65f, 1.4f);
            case CLOSING -> player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6f, 0.65f);
            case DEPARTING -> player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.4f, 1.1f);
            default -> {}
        }
    }

    static float cuePitch(TrainLine.Phase phase) {
        return switch (phase) {
            case APPROACHING -> .88f;
            case BOARDING -> 1.4f;
            case CLOSING -> .65f;
            case DEPARTING -> 1.1f;
            default -> 0;
        };
    }

    static void clear() { LAST_CUE.clear(); }
}

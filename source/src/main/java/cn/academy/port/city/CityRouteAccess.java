package cn.academy.port.city;

import cn.academy.port.train.TrainLine;
import cn.academy.port.train.CityRingLine;
import cn.academy.port.train.CityMetroLine;
import cn.academy.port.train.CityMetroNorthLine;
import cn.academy.port.train.CityMetroAirportLine;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "academy")
public final class CityRouteAccess {
    private static final ResourceKey<Level> CITY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("academy", "story_city_map"));
    private static final Map<ServerPlayer, String> LAST_STOP = new WeakHashMap<>();

    private CityRouteAccess() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aca_city")
                .then(Commands.literal("map").executes(context -> {
                    var source = context.getSource();
                    source.sendSuccess(() -> Component.translatable("ac.city.map.title"), false);
                    for (var stop : TrainLine.STATIONS) {
                        source.sendSuccess(() -> Component.translatable("ac.city.map.stop",
                                Component.translatable(stop.translationKey()), stop.x(), stop.northDistrict(), stop.southDistrict()), false);
                    }
                    source.sendSuccess(() -> Component.translatable("ac.city.map.airport"), false);
                    source.sendSuccess(() -> Component.translatable("ac.city.map.north_south"), false);
                    source.sendSuccess(() -> Component.translatable("ac.city.map.campus_access"), false);
                    source.sendSuccess(() -> Component.translatable("ac.city.map.south_access"), false);
                    source.sendSuccess(() -> Component.translatable("ac.city.map.ring"), false);
                    source.sendSuccess(() -> Component.translatable("ac.city.map.metro"), false);
                    for (int i = 0; i < CityMetroLine.STATIONS.size(); i++) {
                        var stop = CityMetroLine.STATIONS.get(i);
                        int entranceX = CityMetroTransit.surfaceEntranceX(i);
                        source.sendSuccess(() -> Component.translatable("ac.city.map.metro.stop",
                                Component.translatable(stop.translationKey()), entranceX, 593), false);
                    }
                    source.sendSuccess(() -> Component.translatable("ac.city.map.metro_north"), false);
                    for (int i = 0; i < CityMetroNorthLine.STATIONS.size(); i++) {
                        var stop = CityMetroNorthLine.STATIONS.get(i);
                        if (CityMetroNorthTransit.hasSurfaceEntrance(i)) {
                            int entranceZ = CityMetroNorthTransit.entranceZ(i);
                            source.sendSuccess(() -> Component.translatable("ac.city.map.metro_north.stop",
                                    Component.translatable(stop.translationKey()), CityMetroNorthLine.X - 7, entranceZ), false);
                        } else source.sendSuccess(() -> Component.translatable("ac.city.map.metro_north.transfer",
                                Component.translatable(stop.translationKey())), false);
                    }
                    source.sendSuccess(() -> Component.translatable("ac.city.map.metro_airport"), false);
                    for (int i = 0; i < CityMetroAirportLine.STATIONS.size(); i++) {
                        var stop = CityMetroAirportLine.STATIONS.get(i);
                        if (CityMetroAirportTransit.hasSurfaceEntrance(i)) {
                            int entranceX = CityMetroAirportTransit.entranceX(i);
                            source.sendSuccess(() -> Component.translatable("ac.city.map.metro_airport.stop",
                                    Component.translatable(stop.translationKey()), entranceX, CityMetroAirportLine.Z - 7), false);
                        } else {
                            String key = i == 0 ? "ac.city.map.metro_airport.transfer" : "ac.city.map.metro_airport.terminal";
                            source.sendSuccess(() -> Component.translatable(key, Component.translatable(stop.translationKey())), false);
                        }
                    }
                    for (var stop : CityRingLine.STATIONS)
                        source.sendSuccess(() -> Component.translatable("ac.city.map.ring.stop",
                                Component.translatable(stop.translationKey()), stop.x(), stop.z()), false);
                    return TrainLine.STATIONS.size() + CityRingLine.STATIONS.size() + CityMetroLine.STATIONS.size() + CityMetroNorthLine.STATIONS.size() + CityMetroAirportLine.STATIONS.size() + 9;
                }))
                .then(Commands.literal("life").requires(source->source.hasPermission(2)).executes(c->{
                    var p=c.getSource().getPlayerOrException();var w=c.getSource().getServer().getLevel(CITY);if(w==null||p.isPassenger())return 0;
                    var at=new BlockPos(115,0,96);if(!w.getBlockState(at).isAir()){c.getSource().sendFailure(Component.translatable("academy.city.life.new_chunks"));return 0;}
                    p.teleportTo(w,115.5,0,96.5,180,0);p.sendSystemMessage(Component.translatable("academy.city.life.welcome"));return 1;
                }))
                .then(Commands.literal("commute").requires(source->source.hasPermission(2)).executes(c->{
                    var p=c.getSource().getPlayerOrException();var w=c.getSource().getServer().getLevel(CITY);if(w==null||p.isPassenger())return 0;
                    w.getBlockState(new BlockPos(144,44,12));p.teleportTo(w,144.5,45,12.5,180,0);p.sendSystemMessage(Component.translatable("academy.city.commute.intro"));return 1;
                }))
                .then(Commands.literal("route").requires(source -> source.hasPermission(2)).executes(context -> {
                    var source = context.getSource();
                    var player = source.getPlayerOrException();
                    var level = source.getServer().getLevel(CITY);
                    var start = new BlockPos(CitySampleRoute.START_X, 0, CitySampleRoute.START_Z);
                    if (level == null) {
                        source.sendFailure(Component.translatable("ac.city.route.missing"));
                        return 0;
                    }
                    if (!level.getBlockState(start).isAir() || !level.getBlockState(start.above()).isAir()
                            || !level.getBlockState(start.below()).is(CityBlocks.block("composite_floor"))
                            || !level.getBlockState(start.above(6)).is(CityBlocks.block("white_panel_wall"))) {
                        source.sendFailure(Component.translatable("ac.city.route.unavailable"));
                        return 0;
                    }
                    player.teleportTo(level, CitySampleRoute.START_X + 0.5, 0, CitySampleRoute.START_Z + 0.5, 0, 0);
                    player.displayClientMessage(Component.translatable("ac.city.route.campus"), true);
                    LAST_STOP.put(player, "campus");
                    return 1;
                })));
    }

    @SubscribeEvent
    public static void markStop(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        if (!player.level().dimension().equals(CITY)) {
            LAST_STOP.remove(player);
            return;
        }
        int x = player.getBlockX(), z = player.getBlockZ();
        double y = player.getY();
        String stop = null;
        if (x >= -86 && x <= -44 && z >= -88 && z <= -51 && y >= 0 && y < 5) stop = "campus";
        if (x >= -50 && x <= 50 && z >= 391 && z <= 409 && y >= 25 && y < 34) stop = "station";
        if (x >= -97 && x <= -33 && z >= 548 && z <= 591 && y >= 0 && y < 5) stop = "d15";
        if (stop == null) {
            LAST_STOP.remove(player);
        } else if (!stop.equals(LAST_STOP.put(player, stop))) {
            player.displayClientMessage(Component.translatable("ac.city.route." + stop), true);
        }
    }
}

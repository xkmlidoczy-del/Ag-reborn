package cn.academy.port.qa;

import cn.academy.port.city.CityCrowdPlan;
import cn.academy.port.city.CityBlocks;
import cn.academy.port.city.CityNpcEntity;
import cn.academy.port.city.CityPlan;
import cn.academy.port.city.CityTransit;
import cn.academy.port.city.CityPopulation;
import cn.academy.port.city.CityWindContent;
import cn.academy.port.client.CityWindRenderer;
import cn.academy.port.client.city.CityNpcRenderer;
import cn.academy.port.client.train.TrainCarRenderer;
import cn.academy.port.train.TrainCarEntity;
import cn.academy.port.train.TrainLine;
import cn.academy.port.train.AirportTrainManager;
import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Opt-in integrated server and client witnesses for the city prototype. */
final class StoryCityClientQa {
    private static final Logger LOG = LogUtils.getLogger();
    private static final ResourceKey<Level> TEMPLATES = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("academy", "story_city"));
    private static final ResourceKey<Level> CITY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("academy", "story_city_map"));
    private static CompletableFuture<Placement> pending;
    private static Vec3 boardedAt;
    private static int ticks;
    private static int serviceWait;
    private static int destination = -1;
    private static final Map<String, Integer> CROWD_COUNTS = new HashMap<>();

    private StoryCityClientQa() {}

    static boolean step(Minecraft mc, String arg) {
        String[] args = arg.split(",");
        return switch (args[0]) {
            case "template" -> template(mc, args[1], Integer.parseInt(args[2]), Integer.parseInt(args[3]));
            case "train" -> {
                train(mc, args[1]);
                yield true;
            }
            case "train_seat" -> {
                require(mc.player.getVehicle() instanceof TrainCarEntity, "seat check requires a real mounted player");
                var car = (TrainCarEntity) mc.player.getVehicle();
                int role = Integer.parseInt(args[1]);
                require(car.role() == role, "unexpected seat car role " + car.role());
                double floorHeight = mc.player.getY() - car.getY();
                require(Math.abs(floorHeight - 1.04) < .04, "rider feet must align with floor; relative=" + floorHeight);
                LOG.info("QA_TRAIN_SEAT role={} riderY={} carY={} relativeY={} eyeY={}", role,
                        mc.player.getY(), car.getY(), floorHeight, mc.player.getEyeY());
                yield true;
            }
            case "train_door" -> {
                int role = Integer.parseInt(args[1]);
                var expected = TrainLine.Phase.valueOf(args[2]);
                float low = Float.parseFloat(args[3]), high = Float.parseFloat(args[4]);
                boolean airport = Boolean.parseBoolean(args[5]);
                var cars = mc.level.getEntitiesOfClass(TrainCarEntity.class, mc.player.getBoundingBox().inflate(100),
                        car -> car.role() == role && car.airport() == airport);
                require(cars.size() == 1, "expected one nearby train car role=" + role + " found=" + cars.size());
                var car = cars.getFirst();
                float amount = car.doorOpen(1f);
                require(car.servicePhase() == expected && amount >= low && amount <= high,
                        "door state mismatch phase=" + car.servicePhase() + " amount=" + amount);
                LOG.info("QA_TRAIN_DOOR airport={} role={} phase={} opening={}", airport, role, car.servicePhase(), amount);
                yield true;
            }
            case "cue_reset" -> { TrainCueProbe.reset(); yield true; }
            case "d22_tower_height" -> {
                double low=Double.parseDouble(args[1]), high=Double.parseDouble(args[2]);
                double centreX=args.length>3?Double.parseDouble(args[3]):-828;
                double centreZ=args.length>4?Double.parseDouble(args[4]):-1404;
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                var actual=server.submit(() -> {
                    var player=server.getPlayerList().getPlayer(id);
                    require(player.serverLevel().dimension().equals(CITY) && player.gameMode.getGameModeForPlayer()==net.minecraft.world.level.GameType.SURVIVAL
                            && !player.getAbilities().flying && !player.isPassenger(),"tower climb must be in survival");
                    return player.position();
                }).join();
                require(actual.y>=low && actual.y<=high && Math.abs(actual.x-centreX)<=5 && Math.abs(actual.z-centreZ)<=5,
                        "tower climb height/position outside range: "+actual);
                LOG.info("QA_D22_TOWER_HEIGHT position={} expected={}..{}",actual,low,high);
                yield true;
            }
            case "cue_heard" -> {
                int count = TrainCueProbe.count();
                require(count > 0, "expected at least one train chime request");
                LOG.info("QA_TRAIN_CUE observedRequests={}", count);
                yield true;
            }
            case "airport_wait" -> airportWait(mc, Integer.parseInt(args[1]));
            case "north_wait" -> northWait(mc, Integer.parseInt(args[1]));
            case "metro_fluid_check" -> {
                int station=Integer.parseInt(args[1]);
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                int checked=server.submit(() -> {
                    var level=server.getPlayerList().getPlayer(id).serverLevel();
                    int center=cn.academy.port.train.CityMetroLine.STATIONS.get(station).x(), count=0;
                    var pos=new net.minecraft.core.BlockPos.MutableBlockPos();
                    for(int x=center-49;x<=center+49;x++) for(int z=591;z<=609;z++) for(int y=-15;y<=-9;y++) {
                        pos.set(x,y,z);
                        require(level.getFluidState(pos).isEmpty(),"fluid in metro platform at "+pos.immutable());
                        count++;
                    }
                    return count;
                }).join();
                LOG.info("QA_METRO_FLUID_CHECK station={} blocks={} fluid=0",station,checked); yield true;
            }
            case "city_fluid_feature_check" -> {
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                int count=server.submit(() -> {
                    var level=server.getPlayerList().getPlayer(id).serverLevel();
                    var generator=(cn.academy.port.city.CityGreyboxGenerator)level.getChunkSource().getGenerator();
                    var plains=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                            .getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
                    var original=plains.value().getGenerationSettings().features().stream().flatMap(net.minecraft.core.HolderSet::stream)
                            .flatMap(h -> h.unwrapKey().stream()).map(k -> k.location().toString()).collect(java.util.stream.Collectors.toSet());
                    var actual=generator.getBiomeGenerationSettings(plains).features().stream().flatMap(net.minecraft.core.HolderSet::stream)
                            .flatMap(h -> h.unwrapKey().stream()).map(k -> k.location().toString()).collect(java.util.stream.Collectors.toSet());
                    var excluded=java.util.Set.of("minecraft:lake_lava_surface","minecraft:lake_lava_underground","minecraft:spring_lava","minecraft:spring_lava_frozen","minecraft:spring_water");
                    require(original.contains("minecraft:lake_lava_underground") && original.contains("minecraft:spring_lava") && original.contains("minecraft:spring_water"),"expected vanilla sources");
                    var expected=new java.util.HashSet<>(original); expected.removeAll(excluded);
                    require(expected.equals(actual) && actual.contains("minecraft:ore_iron_middle") && actual.contains("minecraft:trees_plains"),"unexpected city generation feature filter");
                    return actual.size();
                }).join();
                LOG.info("QA_CITY_FLUID_FEATURE_CHECK remaining={} excluded_lava_and_water_sources=true",count);yield true;
            }
            case "metro_north_fluid_check" -> {
                int station=Integer.parseInt(args[1]);
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                int checked=server.submit(() -> {
                    var level=server.getPlayerList().getPlayer(id).serverLevel();
                    int center=cn.academy.port.train.CityMetroNorthLine.STATIONS.get(station).z(), count=0;
                    var pos=new net.minecraft.core.BlockPos.MutableBlockPos();
                    for(int x=-109;x<=-91;x++)for(int z=center-49;z<=center+49;z++)for(int y=-31;y<=-25;y++) {
                        pos.set(x,y,z);
                        require(level.getFluidState(pos).isEmpty(),"fluid in north metro platform at "+pos.immutable());count++;
                    }
                    return count;
                }).join();
                LOG.info("QA_METRO_NORTH_FLUID_CHECK station={} blocks={} fluid=0",station,checked);yield true;
            }
            case "metro_airport_wait" -> metroAirportWait(mc, Integer.parseInt(args[1]));
            case "metro_airport_train" -> { metroAirportTrain(mc,args[1],Integer.parseInt(args[2])); yield true; }
            case "metro_airport_gates" -> {
                int station=Integer.parseInt(args[1]);boolean expected=Boolean.parseBoolean(args[2]);
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                int count=server.submit(() -> {
                    var level=server.getPlayerList().getPlayer(id).serverLevel();int checked=0;
                    for(int role=0;role<3;role++)for(int delta=-2;delta<=2;delta++)for(int side:new int[]{-3,3})for(int y=-15;y<=-14;y++) {
                        var pos=cn.academy.port.city.CityMetroAirportTransit.bay(station,role,side,y).offset(delta,0,0);
                        var block=level.getBlockState(pos);
                        require(block.is(cn.academy.port.city.CityPlatformGate.GATE.get())
                                && block.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)==expected,"airport metro gate at "+pos+": "+block);
                        checked++;
                    }
                    return checked;
                }).join();
                LOG.info("QA_METRO_AIRPORT_GATES station={} open={} blocks={}",station,expected,count);yield true;
            }
            case "metro_airport_fluid_check" -> {
                int station=Integer.parseInt(args[1]);
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                int checked=server.submit(() -> {
                    var level=server.getPlayerList().getPlayer(id).serverLevel();
                    int center=cn.academy.port.train.CityMetroAirportLine.STATIONS.get(station).x(), count=0;
                    var pos=new net.minecraft.core.BlockPos.MutableBlockPos();
                    for(int x=center-49;x<=center+49;x++)for(int z=-1641;z<=-1623;z++)for(int y=-15;y<=-9;y++) {
                        pos.set(x,y,z);
                        require(level.getFluidState(pos).isEmpty(),"fluid in airport metro platform at "+pos.immutable());count++;
                    }
                    return count;
                }).join();
                LOG.info("QA_METRO_AIRPORT_FLUID_CHECK station={} blocks={} fluid=0",station,checked);yield true;
            }
            case "metro_north_wait" -> metroNorthWait(mc, Integer.parseInt(args[1]));
            case "metro_north_train" -> { metroNorthTrain(mc,args[1],Integer.parseInt(args[2])); yield true; }
            case "metro_north_gates" -> {
                int station=Integer.parseInt(args[1]);boolean expected=Boolean.parseBoolean(args[2]);
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                int count=server.submit(() -> {
                    var level=server.getPlayerList().getPlayer(id).serverLevel();int checked=0;
                    for(int role=0;role<3;role++)for(int delta=-2;delta<=2;delta++)for(int side:new int[]{-3,3})for(int y=-31;y<=-30;y++) {
                        var pos=cn.academy.port.city.CityMetroNorthTransit.bay(station,role,side,y).offset(0,0,delta);
                        var block=level.getBlockState(pos);
                        require(block.is(cn.academy.port.city.CityPlatformGate.GATE.get())
                                && block.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)==expected,"north metro gate at "+pos+": "+block);
                        checked++;
                    }
                    return checked;
                }).join();
                LOG.info("QA_METRO_NORTH_GATES station={} open={} blocks={}",station,expected,count);yield true;
            }
            case "metro_wait" -> metroWait(mc, Integer.parseInt(args[1]));
            case "metro_train" -> { metroTrain(mc,args[1],Integer.parseInt(args[2])); yield true; }
            case "metro_gates" -> {
                int station=Integer.parseInt(args[1]);boolean expected=Boolean.parseBoolean(args[2]);
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                int count=server.submit(() -> {
                    var level=server.getPlayerList().getPlayer(id).serverLevel();int checked=0;
                    for(int role=0;role<3;role++)for(int delta=-2;delta<=2;delta++)for(int side:new int[]{-3,3})for(int y=-15;y<=-14;y++) {
                        var pos=cn.academy.port.city.CityMetroTransit.bay(station,role,side,y).offset(delta,0,0);
                        var block=level.getBlockState(pos);
                        require(block.is(cn.academy.port.city.CityPlatformGate.GATE.get())
                                && block.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)==expected,"metro gate state at "+pos+": "+block);
                        checked++;
                    }
                    return checked;
                }).join();
                LOG.info("QA_METRO_GATES station={} open={} blocks={}",station,expected,count);yield true;
            }
            case "ring_wait" -> ringWait(mc, Integer.parseInt(args[1]));
            case "ring_train" -> { ringTrain(mc, args[1], Integer.parseInt(args[2])); yield true; }
            case "ring_fleet" -> {
                int primary=Integer.parseInt(args[1]), direction=Integer.parseInt(args[2]);
                var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
                var stops=server.submit(()->{
                    var level=server.getPlayerList().getPlayer(id).serverLevel();
                    var result=new java.util.ArrayList<Integer>();
                    for(int service=0;service<cn.academy.port.train.CityRingLine.SERVICES;service++) {
                        var state=cn.academy.port.train.CityRingTrainManager.state(level,service);
                        int expected=Math.floorMod(primary+service*cn.academy.port.train.CityRingLine.SERVICE_STATION_GAP,
                                cn.academy.port.train.CityRingLine.STATIONS.size());
                        require(state.station()==expected && state.direction()==direction && state.boarding(),
                                "ring service "+service+" expected boarding at "+expected+" got "+state);
                        result.add(state.station());
                    }
                    return result;
                }).join();
                LOG.info("QA_RING_FLEET direction={} stops={}",direction,stops);yield true;
            }
            case "ring_gates" -> {
                int station = Integer.parseInt(args[1]); boolean expected = Boolean.parseBoolean(args[2]);
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                int count = server.submit(() -> {
                    var level = server.getPlayerList().getPlayer(id).serverLevel(); int checked = 0;
                    var stop = cn.academy.port.train.CityRingLine.STATIONS.get(station);
                    for (int role=0;role<3;role++) for (int delta=-2;delta<=2;delta++)
                        for (int side:new int[]{-3,3}) for (int y=65;y<=66;y++) {
                            var pos=cn.academy.port.city.CityRingTransit.bay(station,role,side,y)
                                    .offset(stop.horizontal()?delta:0,0,stop.horizontal()?0:delta);
                            var block=level.getBlockState(pos);
                            require(block.is(cn.academy.port.city.CityPlatformGate.GATE.get())
                                    && block.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)==expected,
                                    "ring gate state at "+pos+": "+block);
                            checked++;
                        }
                    return checked;
                }).join();
                LOG.info("QA_RING_GATES station={} open={} blocks={}", station, expected, count);
                yield true;
            }
            case "north_train" -> { northTrain(mc, args[1], Integer.parseInt(args[2])); yield true; }
            case "north_gates" -> {
                int station = Integer.parseInt(args[1]); boolean expected = Boolean.parseBoolean(args[2]);
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                int count = server.submit(() -> {
                    var level = server.getPlayerList().getPlayer(id).serverLevel(); int checked = 0;
                    int center = cn.academy.port.train.NorthSouthLine.STATIONS.get(station).z();
                    for (int role = 0; role < 3; role++) for (int delta = -2; delta <= 2; delta++)
                        for (int side : new int[]{-3, 3}) for (int y = 45; y <= 46; y++) {
                            var pos = new BlockPos(150 + side, y, center + (int) Math.round((role - 1) * TrainLine.CAR_PITCH) + delta);
                            var block = level.getBlockState(pos);
                            require(block.is(cn.academy.port.city.CityPlatformGate.GATE.get())
                                    && block.getValue(net.minecraft.world.level.block.DoorBlock.OPEN) == expected,
                                    "north-south gate state at " + pos + ": " + block);
                            checked++;
                        }
                    return checked;
                }).join();
                LOG.info("QA_NORTH_GATES station={} open={} blocks={}", station, expected, count);
                yield true;
            }
            case "airport_warp_curve" -> {
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                server.submit(() -> {
                    var rider = server.getPlayerList().getPlayer(id);
                    require(rider.getVehicle() instanceof TrainCarEntity car && car.airport(), "airport curve probe needs a mounted rider");
                    try {
                        var field = AirportTrainManager.class.getDeclaredField("offset"); field.setAccessible(true);
                        double beforeBend = cn.academy.port.train.AirportLine.DWELL
                                + (cn.academy.port.train.AirportLine.STRAIGHT_NORTH - 100) / cn.academy.port.train.AirportLine.VMAX
                                + cn.academy.port.train.AirportLine.VMAX / (2 * cn.academy.port.train.AirportLine.ACC);
                        field.setDouble(null, beforeBend - rider.serverLevel().getGameTime() / 20.0);
                    } catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
                }).join();
                LOG.info("QA_AIRPORT_CURVE_CLOCK_FORCED 100 blocks before bend; not a continuous ride");
                yield true;
            }
            case "airport" -> {
                airport(mc, args[1], args.length > 2 ? Integer.parseInt(args[2]) : -1);
                yield true;
            }
            case "airport_gates" -> {
                int station = Integer.parseInt(args[1]); boolean expected = Boolean.parseBoolean(args[2]);
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                int count = server.submit(() -> {
                    var level = server.getPlayerList().getPlayer(id).serverLevel(); int found = 0;
                    for (int role = 0; role < 3; role++) for (int side : new int[]{-3, 3}) for (int delta = -2; delta <= 2; delta++) {
                        int a = (int) Math.round((role - 1) * TrainLine.CAR_PITCH);
                        var pos = station == 0 ? new BlockPos(1040 + side, 25, 300 - a + delta)
                                : new BlockPos(1560 + a + delta, 25, -1790 + side);
                        for (int y = 0; y < 2; y++) {
                            var state = level.getBlockState(pos.above(y));
                            require(state.is(cn.academy.port.city.CityPlatformGate.GATE.get())
                                    && state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN) == expected,
                                    "airport gate missing or wrong at " + pos.above(y));
                            found++;
                        }
                    }
                    return found;
                }).join();
                LOG.info("QA_AIRPORT_GATES station={} open={} blocks={}", station, expected, count);
                yield true;
            }
            case "train_audio" -> {
                try {
                    var field = cn.academy.port.client.train.TrainRunningAudio.class.getDeclaredField("ACTIVE");
                    field.setAccessible(true);
                    var sounds = (java.util.Map<?, ?>) field.get(null);
                    if (args[1].equals("moving")) {
                        require(sounds.size() == 1, "moving train needs one rolling audio loop, got " + sounds.size());
                        var sound = (net.minecraft.client.resources.sounds.SoundInstance) sounds.values().iterator().next();
                        require(mc.getSoundManager().isActive(sound) && sound.getVolume() > .02f,
                                "rolling sound must be active and audible by the sound manager");
                        LOG.info("QA_CITY_TRAIN_AUDIO moving loops={} volume={} pitch={}", sounds.size(), sound.getVolume(), sound.getPitch());
                    } else {
                        require(sounds.isEmpty(), "rolling loop must stop when train is not moving");
                        LOG.info("QA_CITY_TRAIN_AUDIO stopped loops=0");
                    }
                } catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
                yield true;
            }
            case "train_clock" -> {
                int station = Integer.parseInt(args[1]); double elapsed = Double.parseDouble(args[2]);
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                server.submit(() -> {
                    var player = server.getPlayerList().getPlayer(id);
                    require(player != null && !player.isPassenger(), "test clock requires an unoccupied player");
                    try {
                        var offset = cn.academy.port.train.TrainManager.class.getDeclaredField("offset");
                        offset.setAccessible(true);
                        offset.setDouble(null, TrainLine.dwellStart(station, true) + elapsed - player.serverLevel().getGameTime() / 20.0);
                    } catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
                }).join();
                LOG.info("QA_CITY_TRAIN_CLOCK station={} elapsed={}", station, elapsed);
                yield true;
            }
            case "runway_light" -> {
                var pos = new BlockPos(Integer.parseInt(args[1]), -1, Integer.parseInt(args[2]));
                int light = mc.level.getBrightness(LightLayer.BLOCK, pos.above());
                require(mc.level.getBlockState(pos).is(CityBlocks.block("neon_cyan")) && light >= 14,
                        "runway edge must contain a luminous flush light, got " + light);
                LOG.info("QA_CITY_RUNWAY_LIGHT pos={} above={}", pos, light);
                yield true;
            }
            case "forecourt_light" -> {
                var pos = new BlockPos(Integer.parseInt(args[1]), 4, Integer.parseInt(args[2]));
                int light = mc.level.getBrightness(LightLayer.BLOCK, pos.above());
                require(mc.level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LANTERN)
                                && mc.level.getBlockState(pos.below()).is(CityBlocks.block("white_panel_wall")) && light >= 14,
                        "forecourt lantern needs support and light, got " + light);
                LOG.info("QA_CITY_FORECOURT_LIGHT pos={} above={}", pos, light);
                yield true;
            }
            case "supported_lantern" -> {
                var pos = new BlockPos(Integer.parseInt(args[1]), 4, Integer.parseInt(args[2]));
                int light = mc.level.getBrightness(LightLayer.BLOCK, pos.above());
                require(mc.level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LANTERN)
                                && mc.level.getBlockState(pos.below()).is(CityBlocks.block("pillar_white")) && light >= 14,
                        "lantern needs solid support and emitted light at " + pos + ", got " + light);
                LOG.info("QA_CITY_SUPPORTED_LANTERN pos={} above={}", pos, light);
                yield true;
            }
            case "block_at" -> {
                var pos = new BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                String expected = args[4];
                var state = mc.level.getBlockState(pos);
                String actual = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                require(actual.equals(expected), "block at " + pos + " expected " + expected + " got " + actual);
                LOG.info("QA_CITY_BLOCK pos={} block={}", pos, actual);
                yield true;
            }
            case "d22_approach_light" -> {
                var pos = new BlockPos(-797, 4, Integer.parseInt(args[1]));
                int light = mc.level.getBrightness(LightLayer.BLOCK, pos.above());
                require(mc.level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LANTERN)
                                && mc.level.getBlockState(pos.below()).is(CityBlocks.block("pillar_white")) && light >= 14,
                        "D22 approach lantern needs support and light, got " + light);
                LOG.info("QA_CITY_D22_APPROACH_LIGHT pos={} above={}", pos, light);
                yield true;
            }
            case "d22_service_sites" -> {
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                int verified = server.submit(() -> {
                    var level = server.getPlayerList().getPlayer(id).serverLevel(); int count = 0;
                    for (int x : new int[]{-828, -804, -780, -756}) for (int z : new int[]{-1644, -1620, -1596, -1572}) {
                        level.getChunk(x >> 4, z >> 4);
                        level.getChunk((x + 5) >> 4, (z - 2) >> 4);
                        int height = CityPlan.sample(20261103L, x, z).height();
                        require(level.getBlockEntity(new BlockPos(x, height, z)) instanceof CityWindContent.HeadEntity,
                                "wind head missing at " + x + "," + z);
                        for (int y = 0; y <= 26; y++) {
                            var ladder = level.getBlockState(new BlockPos(x + 1, y, z));
                            require(ladder.is(net.minecraft.world.level.block.Blocks.LADDER)
                                            && ladder.getValue(net.minecraft.world.level.block.LadderBlock.FACING) == net.minecraft.core.Direction.EAST,
                                    "maintenance ladder missing at " + x + "," + y + "," + z);
                        }
                        for (int y : new int[]{8, 16, 24}) {
                            require(level.getBlockState(new BlockPos(x + 3, y, z)).is(CityBlocks.block("brushed_steel")), "service deck missing");
                            require(level.getBlockState(new BlockPos(x + 5, y, z - 2)).is(CityBlocks.block("neon_amber")), "service floor light missing");
                            require(level.getBlockState(new BlockPos(x + 5, y + 1, z)).is(net.minecraft.world.level.block.Blocks.IRON_BARS), "service rail missing");
                        }
                        count++;
                    }
                    return count;
                }).join();
                require(verified == 16, "all scaffold-zone wind service sites required");
                LOG.info("QA_CITY_D22_SERVICE_SITES verified={} ladder_blocks={} deck_levels={}", verified, verified * 27, verified * 3);
                yield true;
            }
            case "d22_district_samples" -> {
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                int verified = server.submit(() -> {
                    var level = server.getPlayerList().getPlayer(id).serverLevel(); int count = 0;
                    for (int[] site : new int[][]{{-1188, -1980}, {-996, -1836}, {-612, -1380}, {-420, -1236}}) {
                        int x = site[0], z = site[1];
                        level.getChunk(x >> 4, z >> 4); level.getChunk((x + 5) >> 4, (z - 2) >> 4);
                        int height = CityPlan.sample(20261103L, x, z).height();
                        require(level.getBlockEntity(new BlockPos(x, height, z)) instanceof CityWindContent.HeadEntity,
                                "district wind head missing at " + x + "," + z);
                        for (int y = 0; y <= 26; y++) {
                            var ladder = level.getBlockState(new BlockPos(x + 1, y, z));
                            require(ladder.is(net.minecraft.world.level.block.Blocks.LADDER)
                                            && ladder.getValue(net.minecraft.world.level.block.LadderBlock.FACING) == net.minecraft.core.Direction.EAST,
                                    "district ladder missing at " + x + "," + y + "," + z);
                        }
                        for (int y : new int[]{8, 16, 24}) {
                            require(level.getBlockState(new BlockPos(x + 3, y, z)).is(CityBlocks.block("brushed_steel")), "district service deck missing");
                            require(level.getBlockState(new BlockPos(x + 5, y, z - 2)).is(CityBlocks.block("neon_amber")), "district service light missing");
                            require(level.getBlockState(new BlockPos(x + 5, y + 1, z)).is(net.minecraft.world.level.block.Blocks.IRON_BARS), "district service rail missing");
                        }
                        count++;
                    }
                    return count;
                }).join();
                require(verified == 4, "district edge and interior samples required");
                LOG.info("QA_CITY_D22_DISTRICT_SAMPLES verified={} ladder_blocks={} deck_levels={}", verified, verified * 27, verified * 3);
                yield true;
            }
            case "gates" -> {
                int station = Integer.parseInt(args[1]); boolean expectedOpen = Boolean.parseBoolean(args[2]);
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                int count = server.submit(() -> {
                    var level = server.getPlayerList().getPlayer(id).serverLevel(); int checked = 0;
                    for (int car = 0; car < 3; car++) for (int dx = -2; dx <= 2; dx++)
                        for (int side : new int[]{-3, 3}) for (int y = 25; y <= 26; y++) {
                            var p = new BlockPos(CityTransit.bayX(station, car) + dx, y, (int) TrainLine.Z + side);
                            var state = level.getBlockState(p);
                            require(state.is(cn.academy.port.city.CityPlatformGate.GATE.get())
                                            && state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN) == expectedOpen,
                                    "gate state at " + p + " expected open=" + expectedOpen + ", got " + state);
                            checked++;
                        }
                    return checked;
                }).join();
                var client = mc.level.getBlockState(new BlockPos(CityTransit.bayX(station, 1), 25, (int) TrainLine.Z - 3));
                require(client.is(cn.academy.port.city.CityPlatformGate.GATE.get())
                                && client.getValue(net.minecraft.world.level.block.DoorBlock.OPEN) == expectedOpen,
                        "client needs the same gate state as the server");
                LOG.info("QA_CITY_PLATFORM_GATES station={} open={} checked={}", station, expectedOpen, count);
                yield true;
            }
            case "surface_lava" -> {
                int cx = Integer.parseInt(args[1]), cz = Integer.parseInt(args[2]), radius = Integer.parseInt(args[3]);
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                var locations = server.submit(() -> {
                    var level = server.getPlayerList().getPlayer(id).serverLevel();
                    java.util.List<BlockPos> found = new java.util.ArrayList<>();
                    java.util.Set<net.minecraft.world.level.ChunkPos> missing = new java.util.HashSet<>();
                    for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) {
                        if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
                            missing.add(new net.minecraft.world.level.ChunkPos(x >> 4, z >> 4));
                            continue;
                        }
                        for (int y = -8; y <= 6; y++) {
                            var p = new BlockPos(x, y, z);
                            if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.LAVA)) found.add(p);
                        }
                    }
                    require(missing.isEmpty(), "lava scan missed chunks " + missing);
                    return found;
                }).join();
                LOG.info("QA_CITY_SURFACE_LAVA center={},{} radius={} count={} first={}", cx, cz, radius, locations.size(), locations.stream().limit(8).toList());
                if (args.length > 4) require(locations.size() <= Integer.parseInt(args[4]), "unexpected surface lava count " + locations.size());
                yield true;
            }
            case "solid_surface" -> {
                int x0 = Integer.parseInt(args[1]), x1 = Integer.parseInt(args[2]);
                int z0 = Integer.parseInt(args[3]), z1 = Integer.parseInt(args[4]), y = Integer.parseInt(args[5]);
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                int holes = server.submit(() -> {
                    var level = server.getPlayerList().getPlayer(id).serverLevel(); int missing = 0;
                    for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                        require(level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null, "surface check requires loaded chunks");
                        var s = level.getBlockState(new BlockPos(x, y, z));
                        if (s.isAir() || s.is(net.minecraft.world.level.block.Blocks.LAVA)) missing++;
                    }
                    return missing;
                }).join();
                LOG.info("QA_CITY_SURFACE_SOLID x={}..{} z={}..{} y={} holes={}", x0, x1, z0, z1, y, holes);
                require(holes == 0, "surface contains " + holes + " holes or lava blocks");
                yield true;
            }
            case "phase" -> waitForPhase(mc, TrainLine.Phase.valueOf(args[1]), Integer.parseInt(args[2]), false);
            case "next_stop" -> waitForPhase(mc, TrainLine.Phase.BOARDING, Integer.parseInt(args[1]), true);
            case "npc_looks" -> {
                npcLooks(mc, Set.of(Arrays.copyOfRange(args, 1, args.length)));
                yield true;
            }
            case "npc_walk" -> {
                npcWalk(mc, args[1], new Vec3(Double.parseDouble(args[2]), Double.parseDouble(args[3]), Double.parseDouble(args[4])));
                yield true;
            }
            case "npc_moving" -> {
                var npc = mc.level.getEntitiesOfClass(CityNpcEntity.class, mc.player.getBoundingBox().inflate(32),
                        entity -> entity.look().id().equals(args[1])).stream().findFirst().orElseThrow();
                require(npc.walkAnimation.speed() > 0.05f, "city NPC walk animation must be active");
                LOG.info("QA_CITY_NPC_WALKING {} position={} stride={}", args[1], npc.position(), npc.walkAnimation.speed());
                yield true;
            }
            case "crowd" -> {
                crowd(mc, args[1], Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]));
                yield true;
            }
            case "crowd_area" -> {
                int district = Integer.parseInt(args[1]), x = Integer.parseInt(args[2]), z = Integer.parseInt(args[3]);
                int radius = Integer.parseInt(args[4]), minimum = Integer.parseInt(args[5]);
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                int count = server.submit(() -> {
                    var level = server.getPlayerList().getPlayer(id).serverLevel();
                    var box = new net.minecraft.world.phys.AABB(x - radius, -5, z - radius, x + radius, 8, z + radius);
                    return level.getEntitiesOfClass(CityNpcEntity.class, box,
                            entity -> entity.ambient() && CityPlan.district(entity.getBlockX(), entity.getBlockZ()) == district).size();
                }).join();
                require(count >= minimum, "district " + district + " landmark crowd too sparse: " + count);
                LOG.info("QA_CITY_CROWD_AREA D{} center={},{} radius={} actual={}", district, x, z, radius, count);
                yield true;
            }
            case "crowd_more" -> {
                require(CROWD_COUNTS.get(args[2]) >= CROWD_COUNTS.get(args[1]) + 2,
                        "evening venue should attract more people than its morning crowd");
                yield true;
            }
            case "lamp" -> {
                var pos = new BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                int distance = Integer.parseInt(args[4]);
                int actual = mc.level.getBrightness(LightLayer.BLOCK, pos.below(distance));
                require(mc.level.getBlockState(pos).is(CityBlocks.block("ceiling_light"))
                        && actual >= 14 - distance, "ceiling light must light the space below it, found " + actual);
                LOG.info("QA_CITY_CEILING_LIGHT fixture={} sample={} block_light={}", pos, pos.below(distance), actual);
                yield true;
            }
            case "trail_lamp" -> {
                var lamp = new BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                var sample = new BlockPos(Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6]));
                int minimum = Integer.parseInt(args[7]);
                int light = mc.level.getBrightness(LightLayer.BLOCK, sample);
                require(mc.level.getBlockState(lamp).is(net.minecraft.world.level.block.Blocks.LANTERN) && light >= minimum,
                        "generated mountain lantern must light the trail, got " + light + " at " + sample);
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                server.submit(() -> {
                    var level = server.getPlayerList().getPlayer(id).serverLevel();
                    require(level.getBlockState(lamp).is(net.minecraft.world.level.block.Blocks.LANTERN)
                                    && level.getBrightness(LightLayer.BLOCK, sample) >= minimum,
                            "server must have the same mountain lantern and block light");
                }).join();
                LOG.info("QA_CITY_TRAIL_LANTERN fixture={} sample={} block_light={}", lamp, sample, light);
                yield true;
            }
            case "window" -> {
                var pos = new BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                int light = mc.level.getBrightness(LightLayer.BLOCK, pos);
                int outside = mc.level.getBrightness(LightLayer.BLOCK, pos.north());
                require(mc.level.getBlockState(pos).is(CityBlocks.block("lit_window")) && light >= 12 && outside >= 11,
                        "generated window must be lit, got " + mc.level.getBlockState(pos) + " light=" + light + " outside=" + outside);
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                server.submit(() -> {
                    var level = server.getPlayerList().getPlayer(id).serverLevel();
                    require(level.getBlockState(pos).is(CityBlocks.block("lit_window"))
                                    && level.getBrightness(LightLayer.BLOCK, pos) >= 12,
                            "server must have the same generated lit window");
                }).join();
                LOG.info("QA_CITY_WINDOW pos={} source={} outside={}", pos, light, outside);
                yield true;
            }
            case "wind" -> {
                var pos = new BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                var entity = mc.level.getBlockEntity(pos);
                require(entity instanceof CityWindContent.HeadEntity head
                                && mc.getBlockEntityRenderDispatcher().getRenderer(head) instanceof CityWindRenderer,
                        "client needs the generated wind rotor and renderer at " + pos);
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                server.submit(() -> {
                    var level = server.getPlayerList().getPlayer(id).serverLevel();
                    require(level.getBlockEntity(pos) instanceof CityWindContent.HeadEntity,
                            "server needs the generated rotor at " + pos);
                }).join();
                float angle = CityWindRenderer.angle(mc.level.getGameTime(), 0, pos);
                float later = CityWindRenderer.angle(mc.level.getGameTime() + 20, 0, pos);
                require(angle != later, "rotor should advance with the city clock");
                LOG.info("QA_CITY_WIND pos={} angle={} after20={}", pos, angle, later);
                yield true;
            }
            case "route" -> {
                route(mc, args[1], new Vec3(Double.parseDouble(args[2]), Double.parseDouble(args[3]), Double.parseDouble(args[4])));
                yield true;
            }
            default -> throw new IllegalArgumentException("unknown city fixture " + arg);
        };
    }

    private record Placement(Vec3 camera, BlockPos origin, String name, int states) {}

    private static void npcLooks(Minecraft mc, Set<String> expected) {
        var npcs = mc.level.getEntitiesOfClass(CityNpcEntity.class, mc.player.getBoundingBox().inflate(32));
        var actual = npcs.stream().map(npc -> npc.look().id() + ":" + npc.activity().id()).collect(Collectors.toSet());
        require(npcs.size() == expected.size() && actual.equals(expected), "expected " + expected + ", found " + actual);
        for (var npc : npcs) {
            require(mc.getEntityRenderDispatcher().getRenderer(npc) instanceof CityNpcRenderer renderer
                    && mc.getResourceManager().getResource(renderer.getTextureLocation(npc)).isPresent(),
                    "missing NPC renderer or skin for " + npc.look());
            LOG.info("QA_CITY_NPC_LOOK {} activity={} expression={} position={}",
                    npc.look().id(), npc.activity().id(), npc.expression().id(), npc.position());
        }
    }

    private static void crowd(Minecraft mc, String label, int district, int min, int max) {
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        int count = server.submit(() -> {
            var player = server.getPlayerList().getPlayer(id);
            var level = player.serverLevel();
            require(level.dimension().equals(CITY), "crowd must be tested in the city");
            var near = level.getEntitiesOfClass(CityNpcEntity.class, player.getBoundingBox().inflate(112),
                    entity -> entity.ambient() && entity.distanceToSqr(player) <= 112 * 112);
            require(near.size() <= CityPopulation.PLAYER_BUDGET, "single-player crowd exceeds its budget");
            var allowed = CityCrowdPlan.profile(district).roles().stream().flatMap(role -> role.looks().stream()).collect(Collectors.toSet());
            var local = near.stream().filter(entity -> CityPlan.district(entity.getBlockX(), entity.getBlockZ()) == district).toList();
            require(local.size() >= min && local.size() <= max, label + " crowd count is " + local.size() + ", expected " + min + "–" + max);
            for (var npc : local) {
                require(!npc.shouldBeSaved() && npc.isInvulnerable() && allowed.contains(npc.look()),
                        "crowd member must be transient, protected and appropriate for the district");
            }
            long moving = local.stream().filter(entity -> !entity.getNavigation().isDone()).count();
            var activities = local.stream().map(entity -> entity.activity().id()).sorted().toList();
            var expressions = local.stream().map(entity -> entity.expression().id()).distinct().sorted().toList();
            var looks = local.stream().map(entity -> entity.look().id()).distinct().sorted().toList();
            LOG.info("QA_CITY_CROWD {} D{} period={} local={} nearby={} moving={} activities={} expressions={} looks={}",
                    label, district, CityCrowdPlan.Period.at(level.getDayTime()), local.size(), near.size(), moving, activities, expressions, looks);
            return local.size();
        }).join();
        CROWD_COUNTS.put(label, count);
    }

    private static void npcWalk(Minecraft mc, String look, Vec3 target) {
        var server = mc.getSingleplayerServer();
        var uuid = mc.player.getUUID();
        server.submit(() -> {
            var player = server.getPlayerList().getPlayer(uuid);
            var npc = player.serverLevel().getEntitiesOfClass(CityNpcEntity.class, player.getBoundingBox().inflate(32),
                    entity -> entity.look().id().equals(look)).stream().findFirst().orElseThrow();
            require(npc.getNavigation().moveTo(target.x, target.y, target.z, 0.85), "city NPC navigation must start");
            LOG.info("QA_CITY_NPC_PATH {} from={} to={}", look, npc.position(), target);
        }).join();
    }

    private static boolean template(Minecraft mc, String name, int x, int z) {
        if (pending == null) {
            require(name.matches("[a-z]+_[0-9]{2}"), "invalid template fixture id");
            var server = mc.getSingleplayerServer();
            require(server != null, "local integrated server required");
            var player = mc.player.getUUID();
            Path file = mc.gameDirectory.toPath().resolve("city_templates").resolve(name + ".nbt");
            pending = server.submit(() -> {
                try {
                    return place(server.getPlayerList().getPlayer(player), file, name, x, z);
                } catch (Exception error) {
                    throw new IllegalStateException("template placement failed: " + name, error);
                }
            });
            ticks = 0;
        }
        require(++ticks < 800, "template fixture did not settle: " + name);
        if (!pending.isDone()) return false;
        Placement result = pending.join();
        if (!mc.level.dimension().equals(TEMPLATES) || mc.player.position().distanceToSqr(result.camera()) > 0.1
                || !mc.level.hasChunkAt(result.origin())) return false;
        LOG.info("QA_CITY_TEMPLATE {} placed={} origin={}", result.name(), result.states(), result.origin());
        pending = null;
        return true;
    }

    private static Placement place(ServerPlayer player, Path file, String name, int x, int z) throws Exception {
        var level = player.server.getLevel(TEMPLATES);
        require(level != null, "template city dimension missing");
        var tag = NbtIo.readCompressed(file, NbtAccounter.create(8_000_000));
        var lookup = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        var id = ResourceLocation.fromNamespaceAndPath("academy_city_qa", name);
        var template = level.getStructureManager().getOrCreate(id);
        template.load(lookup, tag);
        var size = template.getSize();
        var origin = new BlockPos(x, 0, z);
        for (int cx = x >> 4; cx <= (x + size.getX() - 1) >> 4; cx++) {
            for (int cz = z >> 4; cz <= (z + size.getZ() - 1) >> 4; cz++) level.getChunk(cx, cz);
        }
        String command = "place template " + id + " " + x + " 0 " + z;
        int count = player.server.getCommands().getDispatcher().execute(command,
                player.createCommandSourceStack().withLevel(level).withPermission(4).withSuppressedOutput());
        require(count > 0, command);
        var palette = tag.getList("palette", Tag.TAG_COMPOUND);
        var blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            var row = blocks.getCompound(i);
            var pos = row.getList("pos", Tag.TAG_INT);
            BlockPos at = origin.offset(pos.getInt(0), pos.getInt(1), pos.getInt(2));
            var expected = NbtUtils.readBlockState(lookup, palette.getCompound(row.getInt("state")));
            var actual = level.getBlockState(at);
            if (!actual.is(expected.getBlock())) {
                throw new IllegalStateException(name + " missing " + expected + " at " + at + ": " + actual);
            }
        }
        Vec3 center = new Vec3(x + size.getX() / 2.0, size.getY() * 0.49, z + size.getZ() / 2.0);
        double distance = Math.max(46, size.getY() * 1.27);
        Vec3 camera = center.add(distance * 0.59, distance * 0.29, distance * 0.81);
        Vec3 look = center.subtract(camera);
        float yaw = (float) -Math.toDegrees(Math.atan2(look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
        player.teleportTo(level, camera.x, camera.y, camera.z, yaw, pitch);
        return new Placement(camera, origin, name, blocks.size());
    }

    private static boolean waitForPhase(Minecraft mc, TrainLine.Phase phase, int station, boolean riding) {
        require(++serviceWait < 2200, "timed out waiting for " + phase + " at station " + station);
        TrainCarEntity car = mc.player.getVehicle() instanceof TrainCarEntity vehicle ? vehicle : null;
        if (!riding && car == null) {
            car = mc.level.getEntitiesOfClass(TrainCarEntity.class, mc.player.getBoundingBox().inflate(80),
                    candidate -> candidate.role() == 1).stream().findFirst().orElse(null);
        }
        if (car == null || car.servicePhase() != phase
                || TrainLine.stationNear(car.getX(), 40) != station) return false;
        serviceWait = 0;
        if (riding) destination = station;
        LOG.info("QA_CITY_TRAIN_PHASE station={} phase={} player={}", station, phase, mc.player.position());
        return true;
    }

    private static boolean airportWait(Minecraft mc, int station) {
        require(++serviceWait < 8000, "airport shuttle timed out waiting for station " + station);
        var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        boolean ready = server.submit(() -> {
            var player = server.getPlayerList().getPlayer(id);
            var state = AirportTrainManager.state(player.serverLevel());
            return state.station() == station && state.boarding();
        }).join();
        if (!ready) return false;
        serviceWait = 0; destination = station;
        LOG.info("QA_AIRPORT_BOARDING station={} passenger={}", station, mc.player.getVehicle());
        return true;
    }

    private static boolean northWait(Minecraft mc, int station) {
        require(++serviceWait < 8000, "north-south train timed out waiting for station " + station);
        var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        boolean ready = server.submit(() -> {
            var player = server.getPlayerList().getPlayer(id);
            var state = cn.academy.port.train.NorthSouthTrainManager.state(player.serverLevel());
            return state.station() == station && state.boarding();
        }).join();
        if (!ready) return false;
        serviceWait = 0;
        LOG.info("QA_NORTH_BOARDING station={} passenger={}", station, mc.player.getVehicle());
        return true;
    }

    private static boolean ringWait(Minecraft mc, int station) {
        require(++serviceWait < 10000, "ring train timed out waiting for station " + station);
        var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
        boolean ready=server.submit(() -> {
            var player=server.getPlayerList().getPlayer(id);
            if(player.getVehicle() instanceof TrainCarEntity car && car.ring()) {
                var state=cn.academy.port.train.CityRingTrainManager.state(player.serverLevel(),car);
                return state!=null && state.station()==station && state.boarding();
            }
            for(int service=0;service<cn.academy.port.train.CityRingLine.SERVICES;service++) {
                var state=cn.academy.port.train.CityRingTrainManager.state(player.serverLevel(),service);
                if(state.station()==station && state.boarding()) return true;
            }
            return false;
        }).join();
        if (!ready) return false;
        serviceWait=0; LOG.info("QA_RING_BOARDING station={} passenger={}",station,mc.player.getVehicle());return true;
    }

    private static boolean metroAirportWait(Minecraft mc,int station) {
        require(++serviceWait<10000,"airport metro train timed out waiting for station "+station);
        var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
        boolean ready=server.submit(()->{
            var player=server.getPlayerList().getPlayer(id);
            var state=cn.academy.port.train.CityMetroAirportTrainManager.state(player.serverLevel());
            return state.station()==station && state.boarding();
        }).join();
        if(!ready)return false;
        serviceWait=0;LOG.info("QA_METRO_AIRPORT_BOARDING station={} passenger={}",station,mc.player.getVehicle());return true;
    }

    private static void metroAirportTrain(Minecraft mc,String phase,int station) {
        if(phase.equals("boarded")) {
            require(mc.player.getVehicle() instanceof TrainCarEntity car && car.metroAirport(),"must ride airport metro train");boardedAt=mc.player.position();
        } else if(phase.equals("moving")) {
            require(boardedAt!=null && mc.player.getVehicle() instanceof TrainCarEntity car && car.metroAirport()
                    && mc.player.position().distanceToSqr(boardedAt)>64,"airport metro must carry rider");
        } else if(phase.equals("exit")) {
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            var pos=server.submit(()->{
                var rider=server.getPlayerList().getPlayer(id);
                int x=cn.academy.port.train.CityMetroAirportLine.STATIONS.get(station).x();
                double cross=Math.abs(rider.getZ()+1632),along=Math.abs(rider.getX()-x);
                require(!rider.isPassenger() && rider.onGround() && Math.abs(rider.getY()+15)<.02
                        && along<30 && cross>=4.5 && cross<=8,"airport metro rider must exit onto destination platform");
                return rider.position();
            }).join();
            LOG.info("QA_METRO_AIRPORT_SAFE_EXIT station={} position={}",station,pos);
        }
        LOG.info("QA_METRO_AIRPORT_TRAIN {} station={} position={}",phase,station,mc.player.position());
    }

    private static boolean metroNorthWait(Minecraft mc,int station) {
        require(++serviceWait<10000,"north metro train timed out waiting for station "+station);
        var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
        boolean ready=server.submit(()->{
            var player=server.getPlayerList().getPlayer(id);
            var state=cn.academy.port.train.CityMetroNorthTrainManager.state(player.serverLevel());
            return state.station()==station && state.boarding();
        }).join();
        if(!ready)return false;
        serviceWait=0;LOG.info("QA_METRO_NORTH_BOARDING station={} passenger={}",station,mc.player.getVehicle());return true;
    }

    private static void metroNorthTrain(Minecraft mc,String phase,int station) {
        if(phase.equals("boarded")) {
            require(mc.player.getVehicle() instanceof TrainCarEntity car && car.metroNorth(),"must ride north metro train");boardedAt=mc.player.position();
        } else if(phase.equals("moving")) {
            require(boardedAt!=null && mc.player.getVehicle() instanceof TrainCarEntity car && car.metroNorth()
                    && mc.player.position().distanceToSqr(boardedAt)>64,"north metro must carry rider");
        } else if(phase.equals("exit")) {
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            var pos=server.submit(()->{
                var rider=server.getPlayerList().getPlayer(id);
                int z=cn.academy.port.train.CityMetroNorthLine.STATIONS.get(station).z();
                double cross=Math.abs(rider.getX()+100),along=Math.abs(rider.getZ()-z);
                require(!rider.isPassenger() && rider.onGround() && Math.abs(rider.getY()+31)<.02
                        && along<30 && cross>=4.5 && cross<=8,"north metro rider must exit onto destination platform");
                return rider.position();
            }).join();
            LOG.info("QA_METRO_NORTH_SAFE_EXIT station={} position={}",station,pos);
        }
        LOG.info("QA_METRO_NORTH_TRAIN {} station={} position={}",phase,station,mc.player.position());
    }

    private static boolean metroWait(Minecraft mc,int station) {
        require(++serviceWait<10000,"metro train timed out waiting for station "+station);
        var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
        boolean ready=server.submit(()->{
            var player=server.getPlayerList().getPlayer(id);
            var state=cn.academy.port.train.CityMetroTrainManager.state(player.serverLevel());
            return state.station()==station && state.boarding();
        }).join();
        if(!ready)return false;
        serviceWait=0;LOG.info("QA_METRO_BOARDING station={} passenger={}",station,mc.player.getVehicle());return true;
    }

    private static void metroTrain(Minecraft mc,String phase,int station) {
        if(phase.equals("boarded")) {
            require(mc.player.getVehicle() instanceof TrainCarEntity car && car.metro(),"must ride metro train");boardedAt=mc.player.position();
        } else if(phase.equals("moving")) {
            require(boardedAt!=null && mc.player.getVehicle() instanceof TrainCarEntity car && car.metro()
                    && mc.player.position().distanceToSqr(boardedAt)>64,"metro train must carry rider");
        } else if(phase.equals("exit")) {
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            var pos=server.submit(()->{
                var rider=server.getPlayerList().getPlayer(id);
                int x=cn.academy.port.train.CityMetroLine.STATIONS.get(station).x();
                double cross=Math.abs(rider.getZ()-600),along=Math.abs(rider.getX()-x);
                require(!rider.isPassenger() && rider.onGround() && Math.abs(rider.getY()+15)<.02
                        && along<30 && cross>=4.5 && cross<=8,"metro rider must exit onto destination platform");
                return rider.position();
            }).join();
            LOG.info("QA_METRO_SAFE_EXIT station={} position={}",station,pos);
        }
        LOG.info("QA_METRO_TRAIN {} station={} position={}",phase,station,mc.player.position());
    }

    private static void ringTrain(Minecraft mc,String phase,int station) {
        if (phase.equals("boarded")) {
            require(mc.player.getVehicle() instanceof TrainCarEntity car && car.ring(),"must ride ring train");
            boardedAt=mc.player.position();
        } else if (phase.equals("moving")) {
            require(boardedAt!=null && mc.player.getVehicle() instanceof TrainCarEntity car && car.ring()
                    && mc.player.position().distanceToSqr(boardedAt)>64,"ring train must carry rider");
        } else if (phase.equals("exit")) {
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            var pos=server.submit(() -> {
                var rider=server.getPlayerList().getPlayer(id);
                var stop=cn.academy.port.train.CityRingLine.STATIONS.get(station);
                double cross=stop.horizontal()?Math.abs(rider.getZ()-stop.z()):Math.abs(rider.getX()-stop.x());
                double along=stop.horizontal()?Math.abs(rider.getX()-stop.x()):Math.abs(rider.getZ()-stop.z());
                require(!rider.isPassenger() && rider.onGround() && Math.abs(rider.getY()-65)<.02
                        && along<30 && cross>=4.5 && cross<=8,"ring rider must exit onto destination platform");
                return rider.position();
            }).join();
            LOG.info("QA_RING_SAFE_EXIT station={} position={}",station,pos);
        }
        LOG.info("QA_RING_TRAIN {} station={} position={}",phase,station,mc.player.position());
    }

    private static void northTrain(Minecraft mc, String phase, int station) {
        if (phase.equals("boarded")) {
            require(mc.player.getVehicle() instanceof TrainCarEntity car && car.northSouth(), "must ride north-south train");
            boardedAt = mc.player.position();
        } else if (phase.equals("moving")) {
            require(boardedAt != null && mc.player.getVehicle() instanceof TrainCarEntity car && car.northSouth()
                    && mc.player.position().distanceToSqr(boardedAt) > 64, "north-south train must carry rider");
        } else if (phase.equals("exit")) {
            var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
            var pos = server.submit(() -> {
                var rider = server.getPlayerList().getPlayer(id);
                double cross = Math.abs(rider.getX() - 150), along = Math.abs(rider.getZ() - cn.academy.port.train.NorthSouthLine.STATIONS.get(station).z());
                require(!rider.isPassenger() && rider.onGround() && Math.abs(rider.getY() - 45) < .02
                        && along < 30 && cross >= 4.5 && cross <= 8, "north-south rider must exit onto destination platform");
                return rider.position();
            }).join();
            LOG.info("QA_NORTH_SAFE_EXIT station={} position={}", station, pos);
        }
        LOG.info("QA_NORTH_TRAIN {} station={} position={}", phase, station, mc.player.position());
    }

    private static void airport(Minecraft mc, String phase, int station) {
        if (phase.equals("boarded")) {
            require(mc.player.getVehicle() instanceof TrainCarEntity car && car.airport(), "player must ride airport train");
            boardedAt = mc.player.position();
        } else if (phase.equals("moving")) {
            require(boardedAt != null && mc.player.getVehicle() instanceof TrainCarEntity car && car.airport()
                    && mc.player.position().distanceToSqr(boardedAt) > 64, "airport train must carry rider");
        } else if (phase.equals("exit")) {
            var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
            var p = server.submit(() -> {
                var rider = server.getPlayerList().getPlayer(id);
                double along = station == 0 ? Math.abs(rider.getZ() - 300) : Math.abs(rider.getX() - 1560);
                double cross = station == 0 ? Math.abs(rider.getX() - 1040) : Math.abs(rider.getZ() + 1790);
                require(!rider.isPassenger() && rider.onGround() && Math.abs(rider.getY() - 25) < .02
                        && along < 30 && cross >= 4.5 && cross <= 8, "airport rider must dismount onto destination platform");
                return rider.position();
            }).join();
            LOG.info("QA_AIRPORT_SAFE_EXIT station={} position={}", station, p);
        }
        LOG.info("QA_AIRPORT {} passenger={}", phase, mc.player.position());
    }

    private static void train(Minecraft mc, String phase) {
        var cars = mc.level.getEntitiesOfClass(TrainCarEntity.class, mc.player.getBoundingBox().inflate(90));
        require(cars.size() == 3, "three client train cars are required, got " + cars.size());
        for (var car : cars) {
            require(mc.getEntityRenderDispatcher().getRenderer(car) instanceof TrainCarRenderer,
                    "client renderer is missing for " + car);
        }
        if (phase.equals("boarded")) {
            require(mc.player.getVehicle() instanceof TrainCarEntity, "client must be riding the real train");
            boardedAt = mc.player.position();
        } else if (phase.equals("moving")) {
            require(boardedAt != null && mc.player.getVehicle() instanceof TrainCarEntity
                    && mc.player.position().distanceToSqr(boardedAt) > 64, "train must carry its rider away from the station");
        }
        if (phase.equals("exit")) {
            require(destination >= 0, "wait for a destination before checking the exit");
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            var pos = server.submit(() -> {
                var rider = server.getPlayerList().getPlayer(id);
                double side = Math.abs(rider.getZ() - TrainLine.Z);
                require(!rider.isPassenger() && rider.onGround() && Math.abs(rider.getY() - TrainLine.RAIL_Y) < 0.02
                        && Math.abs(rider.getX() - TrainLine.STATIONS.get(destination).x()) < 30 && side >= 4.5 && side <= 8,
                        "rider must land on the destination platform");
                return rider.position();
            }).join();
            LOG.info("QA_CITY_TRAIN_SAFE_EXIT position={}", pos);
        }
        LOG.info("QA_CITY_TRAIN {} cars={} rider={}", phase, cars.size(), mc.player.position());
    }

    private static void route(Minecraft mc, String checkpoint, Vec3 expected) {
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        var actual = server.submit(() -> {
            var player = server.getPlayerList().getPlayer(id);
            require(player.serverLevel().dimension().equals(CITY) && player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SURVIVAL
                    && !player.getAbilities().flying && !player.isPassenger() && player.onGround(),
                    "route checkpoint must be reached on foot in survival");
            return player.position();
        }).join();
        require(actual.distanceToSqr(expected) < 1, "route checkpoint " + checkpoint + " expected " + expected + " got " + actual);
        LOG.info("QA_CITY_ROUTE {} position={} survival=true grounded=true", checkpoint, actual);
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }
}

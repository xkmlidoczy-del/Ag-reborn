package cn.academy.port.city;

import java.util.HashSet;
import java.util.Set;

/** Geometry, district placement and pedestrian clearance for city exterior landmarks. */
public final class CityDistrictLandmarksRegressionTest {
    public static void main(String[] args) {
        long seed = 20261103L;
        Set<Integer> districts = new HashSet<>();
        for (int z = -2000; z < 2000; z += 4) for (int x = -2000; x < 2000; x += 4) {
            if (!CityDistrictLandmarks.ownsLot(x, z)) continue;
            int d = CityPlan.district(x, z); districts.add(d);
            var col = CityDistrictLandmarks.at(seed, x, z);
            check(col != null && col.block(-1) != null, "landmark plot must have a floor");
            check(CityPlan.sample(seed, x, z).kind() != CityPlan.Kind.ROAD, "landmark covers a road");
            check((CitySampleRoute.at(x, z) == null || d == 7 && CitySampleRoute.schoolLink(x, z))
                    && CityTransit.overlay(x, z) == null, "landmark overlaps route/rail");
            check(CityTemplateSites.at(seed, x, z) == null, "landmark overlaps a template lot");
        }
        check(districts.equals(Set.of(1,2,3,4,5,6,7,8,9,10,11,12,13,14,16,17,18,19,20)), "expected district landmark plots: " + districts);
        var signatures = new java.util.HashSet<String>();
        for (int d : new int[]{8,9,10,11,12,13,14,16,18,19}) {
            var form = new StringBuilder(); int solids = 0;
            for (int a=30;a<=42;a++) for (int b=d==11?29:5;b<=(d==11?41:17);b++) for (int y=0;y<=19;y++) {
                String block = new CityDistrictLandmarks.Column(d,a,b,0,0,0).block(y);
                if (block != null && !block.equals("minecraft:air")) { solids++; form.append(a).append(',').append(b).append(',').append(y).append(':').append(block).append(';'); }
            }
            check(solids >= 4 && signatures.add(form.toString()), "distinct public feature for district " + d);
            for (int a=21;a<=25;a++) for (int b=0;b<48;b++)
                check(!CityDistrictSignature.footprint(d,a,b), "signature must preserve central approach");
        }
        var schoolDoor = CityDistrictLandmarks.at(seed, -184 + 23, -120 + 27);
        check("minecraft:air".equals(schoolDoor.block(0)) && "minecraft:air".equals(schoolDoor.block(2)), "D7 school entry clear");
        var secondDoor = CityDistrictLandmarks.at(seed, -248 + 23, -120 + 27);
        check(secondDoor != null && "minecraft:air".equals(secondDoor.block(0))
                && "minecraft:air".equals(secondDoor.block(2)), "second D7 school entry clear");
        check("academy:white_panel_slab[type=bottom]".equals(
                CityDistrictLandmarks.at(seed, -225, -90).block(4)), "second school entrance canopy");
        check("minecraft:green_concrete".equals(CityDistrictLandmarks.at(seed, -238, -85).block(-1)), "second school court");
        for(long campusSeed:new long[]{seed,0L,42L}) {
            for(int ox:new int[]{-312,-248}) for(int x=ox;x<ox+48;x++) for(int z=-56;z<=-9;z++) {
                var c=CityDistrictLandmarks.at(campusSeed,x,z);
                check(c!=null && CityDistrictLandmarks.ownsLot(x,z) && c.block(-1)!=null
                        && CityTemplateSites.at(campusSeed,x,z)==null
                        && CityNeighborhoodSpaces.at(campusSeed,x,z,CityPlan.sample(campusSeed,x,z))==null
                        && CityTransit.overlay(x,z)==null && CityNorthSouthTransit.overlay(campusSeed,x,z)==null
                        && CityRingTransit.overlay(campusSeed,x,z)==null,
                        "campus plot must avoid other structures at "+x+","+z);
            }
            for(int x=-290;x<=-224;x++) for(int z=-60;z<=-58;z++) {
                var walk=CitySampleRoute.at(x,z);
                check(walk!=null && "minecraft:air".equals(walk.block(0)) && "minecraft:air".equals(walk.block(2))
                        && CityNeighborhoodProps.at(campusSeed,x,z)==null,"campus crosswalk remains clear");
                if(x>=-260 && x<=-253) check((Math.floorMod(x,2)==0?"minecraft:white_concrete":"academy:asphalt")
                        .equals(walk.block(-1)),"west campus crossing markings");
            }
            for(int z=-58;z<=-45;z++) for(int x=-226;x<=-224;x++) {
                var c=CityDistrictLandmarks.at(campusSeed,x,z);
                if(z>=-56) check(c!=null && "minecraft:air".equals(c.block(0)) && "minecraft:air".equals(c.block(2)),
                        "library entrance must be clear");
            }
            for(int z=-56;z<=-49;z++) for(int x=-290;x<=-288;x++) {
                var c=CityDistrictLandmarks.at(campusSeed,x,z);
                check(c!=null && "minecraft:air".equals(c.block(0)) && "minecraft:air".equals(c.block(2)),
                        "sports field entrance must be clear");
            }
            check("minecraft:green_concrete".equals(CityDistrictLandmarks.at(campusSeed,-300,-30).block(-1)),"sports turf");
            check("minecraft:red_concrete".equals(CityDistrictLandmarks.at(campusSeed,-308,-36).block(-1)),"sports track");
            check("academy:ceiling_light".equals(CityDistrictLandmarks.at(campusSeed,-308,-52).block(10)),"sports field light");
            for(int[] light:new int[][]{{14,14},{14,33},{33,14},{33,33},{23,23}})
                check("minecraft:sea_lantern".equals(CityDistrictLandmarks.at(campusSeed,-312+light[0],-56+light[1]).block(-1)),
                        "flush field lighting");
            check("academy:curtain_glass_blue".equals(CityDistrictLandmarks.at(campusSeed,-219,-48).block(2)),"library glazed facade");
            check("minecraft:white_concrete".equals(CityDistrictLandmarks.at(campusSeed,-227,-49).block(11))
                    && "academy:brushed_steel".equals(CityDistrictLandmarks.at(campusSeed,-224,-49).block(11))
                    && "academy:blue_panel_wall".equals(CityDistrictLandmarks.at(campusSeed,-230,-49).block(11)),
                    "library open-book emblem");
        }
        for (int z = -92; z <= -58; z++) for (int x = -226; x <= -224; x++) {
            var walk = CitySampleRoute.at(x, z);
            check(walk != null && "academy:plaza_tile".equals(walk.block(-1))
                    && "minecraft:air".equals(walk.block(0)) && "minecraft:air".equals(walk.block(2))
                    && CityTemplateSites.at(seed, x, z) == null, "second school entrance route");
        }
        for (int x = -226; x <= -160; x++) for (int z = -60; z <= -58; z++) {
            var walk = CitySampleRoute.at(x, z);
            String floor = x >= -196 && x <= -189
                    ? Math.floorMod(x, 2) == 0 ? "minecraft:white_concrete" : "academy:asphalt" : "academy:plaza_tile";
            check(walk != null && floor.equals(walk.block(-1))
                    && "minecraft:air".equals(walk.block(0)) && CityTransit.overlay(x, z) == null,
                    "school-to-school crossing");
        }
        var fountain = CityDistrictLandmarks.at(seed, 0, 0);
        check("minecraft:water[level=0]".equals(fountain.block(-1)) && "academy:neon_cyan".equals(fountain.block(4)), "D7 fountain");
        check("academy:white_panel_wall".equals(CityDistrictLandmarks.at(seed, 6, 0).block(-1)), "D7 fountain retaining rim");
        var plazaLamp = CityDistrictLandmarks.at(seed, 18, 0);
        check("minecraft:lantern[hanging=false,waterlogged=false]".equals(plazaLamp.block(4))
                && "academy:pillar_white".equals(plazaLamp.block(3)), "D7 plaza supported lamp");
        check("minecraft:oak_log".equals(CityDistrictLandmarks.at(seed, 19, 19).block(1)), "D7 plaza tree");
        for (int z = -92; z <= -6; z++) for (int x = -162; x <= -69; x++) {
            if (!CitySampleRoute.schoolLink(x, z)) continue;
            var walk = CitySampleRoute.at(x, z);
            check(walk != null && "academy:plaza_tile".equals(walk.block(-1))
                    && "minecraft:air".equals(walk.block(0)) && "minecraft:air".equals(walk.block(1)), "D7 school link clearance");
            check(CityPlan.sample(seed, x, z).kind() != CityPlan.Kind.BUILDING && CityTemplateSites.at(seed, x, z) == null
                    && CityTransit.overlay(x, z) == null, "D7 school link must avoid buildings and rail");
            check(CityNeighborhoodProps.at(seed, x, z) == null, "D7 school link must not contain streetlight poles");
        }
        for (long alternateSeed : new long[]{0L, 42L, 987654321L})
            for (int z = -92; z <= -6; z++) for (int x = -162; x <= -69; x++) {
                if (!CitySampleRoute.schoolLink(x, z)) continue;
                check(CityNeighborhoodProps.at(alternateSeed, x, z) == null && CityTemplateSites.at(alternateSeed, x, z) == null,
                        "D7 school link must remain clear across seeds");
                if (!CityDistrictLandmarks.ownsLot(x, z))
                    check(CityPlan.sample(alternateSeed, x, z).kind() != CityPlan.Kind.BUILDING, "D7 school link must avoid buildings across seeds");
            }
        var schoolSide = CityDistrictLandmarks.at(seed, -184 + 4, -120 + 5);
        check("academy:curtain_glass_clear".equals(schoolSide.block(1)), "D7 school side has windows between piers");
        var civicSide = CityDistrictLandmarks.at(seed, -1048 + 6, -248 + 8);
        check("academy:curtain_glass_clear".equals(civicSide.block(1)), "administration side has windows between piers");
        var airportDoor = CityDistrictLandmarks.at(seed, 1592 + 29, -1868 + 59);
        check("minecraft:air".equals(airportDoor.block(0)) && "minecraft:air".equals(airportDoor.block(3)), "airport entry clear");
        for (int start : new int[]{1544, 1652}) for (int x = start; x < start + 48; x++)
            for (int z = -1864; z <= -1813; z++) {
                var wing = CityDistrictLandmarks.at(seed, x, z);
                check(wing != null && "academy:composite_floor".equals(wing.block(-1))
                                && wing.block(18) != null && CityPlan.district(x, z) == 23,
                        "terminal wing floor or roof missing at " + x + "," + z);
                check(CityAirportTransit.overlay(seed, x, z) == null && CityAirportApron.at(x, z) == null,
                        "terminal wing intersects airport rail or apron at " + x + "," + z);
            }
        for (int z : new int[]{-1850, -1829}) for (int x : new int[]{1591, 1592, 1651, 1652}) {
            var passage = CityDistrictLandmarks.at(seed, x, z);
            for (int y = 0; y <= 3; y++) check("minecraft:air".equals(passage.block(y)),
                    "terminal wing connector blocked at " + x + "," + z + "," + y);
        }
        for (int start : new int[]{1563, 1671}) for (int x = start; x < start + 8; x++)
            for (int z = -1813; z <= -1804; z++) {
                var path = CityDistrictLandmarks.at(seed, x, z);
                check(path != null && ("academy:plaza_tile".equals(path.block(-1))
                        || "academy:composite_floor".equals(path.block(-1))), "wing entry floor");
                for (int y = 0; y <= 3; y++) check("minecraft:air".equals(path.block(y)), "wing entry clearance");
            }
        for (int x = 1647; x <= 1678; x++) {
            var path = CityDistrictLandmarks.at(seed, x, -1803);
            check(path != null && "academy:plaza_tile".equals(path.block(-1))
                    && "minecraft:air".equals(path.block(0)) && "minecraft:air".equals(path.block(2)),
                    "east wing must connect to terminal forecourt");
        }
        for (int x = 1617; x <= 1625; x++) for (int z = -1808; z <= -1805; z++) {
            var canopy = CityDistrictLandmarks.at(seed, x, z);
            check("academy:steel_slab[type=bottom]".equals(canopy.block(5)), "airport canopy must not have gaps over path");
            for (int y = 0; y <= 3; y++) check("minecraft:air".equals(canopy.block(y)), "airport canopy path clearance");
        }
        check("academy:ceiling_light".equals(CityDistrictLandmarks.at(seed, 1622, -1806).block(4)), "airport canopy lighting");
        check("academy:ceiling_light".equals(CityDistrictLandmarks.at(seed, 1616, -1844).block(11)), "airport hall lighting");
        for (int z = -1808; z <= -1790; z++) {
            var approach = CityDistrictLandmarks.at(seed, 1621, z);
            check(approach != null && "academy:plaza_tile".equals(approach.block(-1))
                    && "minecraft:air".equals(approach.block(0)) && "minecraft:air".equals(approach.block(1)), "airport entrance walking path");
        }
        for (int x = 1212; x <= 1600; x++) {
            var road = CityDistrictLandmarks.at(seed, x, -1790);
            check(road != null && ("academy:asphalt".equals(road.block(-1)) || "academy:road_marking".equals(road.block(-1)))
                    && CityTransit.overlay(x, -1790) == null, "airport road must connect without rail conflicts");
            check(CityPlan.sample(seed, x, -1790).kind() != CityPlan.Kind.RUNWAY, "access road crosses runway");
        }
        check(CityPlan.sample(seed, 1211, -1790).kind() == CityPlan.Kind.ROAD, "airport access meets arterial road");
        check("academy:steel_slab[type=bottom]".equals(CityDistrictLandmarks.at(seed, 1588, -1798).block(4)), "airport shelter roof");
        check("minecraft:lantern[hanging=false,waterlogged=false]".equals(CityDistrictLandmarks.at(seed, 1601, -1806).block(4))
                && "academy:white_panel_wall".equals(CityDistrictLandmarks.at(seed, 1601, -1806).block(3)), "supported airport forecourt light");
        check("minecraft:grass_block".equals(CityDistrictLandmarks.at(seed, 1602, -1801).block(0)), "airport forecourt planter");
        for (int x=1390;x<=1479;x++) for (int z=-1780;z<=-1729;z++) {
            var hangar=CityDistrictLandmarks.at(seed,x,z);
            check(hangar!=null && hangar.block(-1)!=null && CityPlan.district(x,z)==23
                    && CityAirportApron.at(x,z)==null && CityAirportTransit.overlay(seed,x,z)==null,
                    "hangar conflicts with airport track or apron at "+x+","+z);
        }
        for (int z=-1762;z<=-1742;z++) for (int y=0;y<=14;y++)
            check("minecraft:air".equals(CityDistrictLandmarks.at(seed,1479,z).block(y)),"hangar doorway clearance");
        for (int x=1460;x<=1487;x++)
            check("minecraft:yellow_concrete".equals(CityDistrictLandmarks.at(seed,x,-1749).block(-1)),"hangar guide line");
        for (int x=1488;x<=1512;x++)
            check("minecraft:yellow_concrete".equals(CityAirportApron.at(x,-1749).block(-1)),"hangar apron connection");
        check("academy:neon_cyan".equals(CityDistrictLandmarks.at(seed, 1272, -1688).block(-1)), "flush runway edge light");
        check("minecraft:white_concrete".equals(CityDistrictLandmarks.at(seed, 1276, -1664).block(-1)), "runway threshold marking");
        var launch = CityDistrictLandmarks.at(seed, 1760 + 32, -1900 + 32);
        check("minecraft:gray_concrete".equals(launch.block(-1)) && "academy:white_panel_wall".equals(launch.block(20))
                && "minecraft:red_concrete".equals(launch.block(49)), "airport launchpad and static rocket exterior");
        var dam = CityDistrictLandmarks.at(seed, -1324, -760);
        check("academy:reinforced_concrete".equals(dam.block(-5)) && "academy:plaza_tile".equals(dam.block(8))
                && "minecraft:iron_bars".equals(dam.block(9)), "D21 dam section and guardrail");
        for (int center : new int[]{-820, -700}) {
            for (int x=-1320;x<=-1310;x++) {
                var deck=CityDistrictLandmarks.at(seed,x,center);
                check(deck!=null && "academy:plaza_tile".equals(deck.block(8))
                        && "minecraft:air".equals(deck.block(9)) && "minecraft:air".equals(deck.block(10)),
                        "reservoir lookout connection blocked at "+x+","+center);
            }
            var support=CityDistrictLandmarks.at(seed,-1309,center-4);
            check("academy:pillar_dark".equals(support.block(-4)) && "academy:plaza_tile".equals(support.block(8))
                    && "academy:pillar_white".equals(support.block(9))
                    && "minecraft:lantern[hanging=false,waterlogged=false]".equals(support.block(10)),
                    "lookout lighting or water support");
        }
        var northEnd = CityDistrictLandmarks.at(seed, -1320, -908);
        var southEnd = CityDistrictLandmarks.at(seed, -1320, -615);
        check("academy:white_panel_slab[type=bottom]".equals(northEnd.block(8))
                && "academy:white_panel_slab[type=bottom]".equals(southEnd.block(8))
                && "minecraft:air".equals(northEnd.block(9)) && "minecraft:air".equals(southEnd.block(9)), "dam ends provide half-step ramps");
        var observatory = CityDistrictLandmarks.at(seed, -1752, -819);
        check("academy:reinforced_concrete".equals(observatory.block(92))
                && "minecraft:air".equals(observatory.block(93)), "D21 observatory floor and entry");
        for (int z = -818; z <= -804; z++) {
            var path = CityDistrictLandmarks.at(seed, -1752, z);
            check("academy:plaza_tile".equals(path.block(92)) && "minecraft:air".equals(path.block(93))
                    && "minecraft:air".equals(path.block(94)), "observatory approach is walkable at z=" + z);
        }
        var beam = CityDistrictLandmarks.at(seed, -800, -1596);
        check(beam != null && beam.top() == 32, "D22 scaffold zone");
        int[][] towers={{-1068,-1860},{-588,-1860},{-1068,-1380},{-588,-1380},{-828,-1404}};
        for (long towerSeed : new long[]{seed, 1L, -42L}) for (int[] tower : towers) {
            int tx=tower[0],tz=tower[1];
            check(CityPlan.district(tx,tz)==22 && CityPlan.sample(towerSeed,tx,tz).kind()==CityPlan.Kind.TURBINE,
                    "30-level tower must retain its D22 wind turbine");
            for(int y=0;y<=121;y++) {
                check("academy:brushed_steel".equals(CityDistrictLandmarks.at(towerSeed,tx-2,tz-2).block(y)),"tower ladder support "+y);
                check("minecraft:ladder[facing=west,waterlogged=false]".equals(CityDistrictLandmarks.at(towerSeed,tx-3,tz-2).block(y)),"tower ladder "+y);
            }
            for(int level=4;level<=120;level+=4) {
                check("academy:brushed_steel".equals(CityDistrictLandmarks.at(towerSeed,tx-3,tz-1).block(level)),"tower landing "+level);
                check("minecraft:iron_bars[east=true,west=true,north=true,south=true]".equals(
                        CityDistrictLandmarks.at(towerSeed,tx-4,tz-4).block(level+1)),"connected tower corner rail "+level);
                var mastAtLevel=CityDistrictLandmarks.at(towerSeed,tx,tz).block(level);
                int mastHeight=CityPlan.sample(towerSeed,tx,tz).height();
                check(level<=mastHeight ? mastAtLevel==null : "academy:brushed_steel".equals(mastAtLevel),
                        "tower preserves mast and closes floor above it "+level);
                if (level==8 || level==16 || level==24) for (int[] side : new int[][]{{-4,0},{4,0},{0,-4},{0,4}}) {
                    var opening=CityDistrictLandmarks.at(towerSeed,tx+side[0],tz+side[1]);
                    check("academy:brushed_steel".equals(opening.block(level))
                            && opening.block(level+1)==null && opening.block(level+2)==null,
                            "tower links to maintenance grid "+tx+","+tz+" level "+level);
                }
            }
        }
        var turbine = CityPlan.sample(seed, -828, -1620);
        var ladder = CityDistrictLandmarks.at(seed, -827, -1620);
        check(turbine.kind() == CityPlan.Kind.TURBINE && turbine.height() >= 40, "D22 access uses an existing tall turbine mast");
        for (int y = 0; y <= 26; y++) check("minecraft:ladder[facing=east,waterlogged=false]".equals(ladder.block(y)), "continuous supported service ladder");
        var deck = CityDistrictLandmarks.at(seed, -825, -1620);
        var rail = CityDistrictLandmarks.at(seed, -823, -1620);
        var corner = CityDistrictLandmarks.at(seed, -823, -1622);
        for (int y : new int[]{8, 16, 24}) {
            check("academy:brushed_steel".equals(deck.block(y)), "maintenance platform floor");
            check(rail.block(y + 1) == null && rail.block(y + 2) == null
                    && "minecraft:iron_bars".equals(corner.block(y + 1))
                    && "academy:neon_amber".equals(corner.block(y)), "platform bridge opening and side guardrail");
        }
        int maintenanceSites = 0;
        for (int mx : new int[]{-828, -804, -780, -756}) for (int mz : new int[]{-1644, -1620, -1596, -1572}) {
            var mast = CityPlan.sample(seed, mx, mz);
            check(mast.kind() == CityPlan.Kind.TURBINE && mast.height() >= 40, "maintenance site must retain its turbine");
            var service = CityDistrictLandmarks.at(seed, mx + 1, mz);
            for (int y = 0; y <= 26; y++) check("minecraft:ladder[facing=east,waterlogged=false]".equals(service.block(y)), "service ladder at " + mx + "," + mz);
            for (int y : new int[]{8, 16, 24}) {
                check("academy:brushed_steel".equals(CityDistrictLandmarks.at(seed, mx + 3, mz).block(y)), "service deck");
                check("academy:neon_amber".equals(CityDistrictLandmarks.at(seed, mx + 5, mz - 2).block(y)), "service floor light");
                check(CityDistrictLandmarks.at(seed, mx + 5, mz).block(y + 1) == null, "bridge opening");
                check("minecraft:iron_bars".equals(CityDistrictLandmarks.at(seed, mx + 5, mz - 2).block(y + 1)), "service side guardrail");
            }
            maintenanceSites++;
        }
        check(maintenanceSites == 16, "expected sixteen scaffold-zone service sites");
        for (int floor : new int[]{8, 16, 24}) for (int x = -775; x <= -757; x++) {
            var lane = CityDistrictLandmarks.at(seed, x, -1595);
            check(lane != null && "academy:brushed_steel".equals(lane.block(floor)), "D22 continuous bridge floor");
            check(lane.block(floor + 1) == null && lane.block(floor + 2) == null, "D22 bridge headroom");
        }
        for (int floor : new int[]{8, 16, 24}) {
            var edge = CityDistrictLandmarks.at(seed, -772, -1594);
            check("minecraft:iron_bars".equals(edge.block(floor + 1)) && "minecraft:iron_bars".equals(edge.block(floor + 2)), "D22 bridge guardrail");
            check(CityDistrictLandmarks.at(seed, -780, -1596).block(floor) == null, "D22 preserve existing turbine mast");
        }
        int districtSites = 0;
        for (int mx = -1188; mx <= -420; mx += 24) for (int mz = -1980; mz <= -1236; mz += 24) {
            check(CityPlan.sample(seed, mx, mz).kind() == CityPlan.Kind.TURBINE, "district service mast must exist");
            var service = CityDistrictLandmarks.at(seed, mx + 1, mz);
            check(service != null && "minecraft:ladder[facing=east,waterlogged=false]".equals(service.block(12)), "district service ladder");
            for (int y : new int[]{8, 16, 24}) {
                check("academy:brushed_steel".equals(CityDistrictLandmarks.at(seed, mx + 3, mz).block(y)), "district service platform");
                check("academy:neon_amber".equals(CityDistrictLandmarks.at(seed, mx + 5, mz - 2).block(y)), "district service floor light");
            }
            districtSites++;
        }
        check(districtSites == 1056, "expected all district wind service sites");
        int links = 0;
        for (int mx = -1188; mx <= -420; mx += 24) for (int mz = -1980; mz <= -1236; mz += 24) {
            for (int floor : new int[]{8, 16, 24}) {
                if (mx < -420) {
                    int z = mz + 1;
                    for (int n = 5; n <= 26; n++) checkBridge(seed, mx + n, z, floor);
                    links++;
                }
                if (mz < -1236) {
                    int x = mx + (mx == -420 ? -1 : 1);
                    for (int n = 6; n <= 23; n++) checkBridge(seed, x, mz + n, floor);
                    links++;
                }
                if (mx > -1188 && mx < -420 && mz > -1980 && mz < -1236) {
                    for (int n = -3; n <= 3; n++) {
                        checkBridge(seed, mx + n, mz + 1, floor);
                        if (n != 0) checkBridge(seed, mx + 1, mz + n, floor);
                    }
                    for (int n = -1; n <= 1; n++) checkBridge(seed, mx + 2, mz + n, floor);
                }
            }
        }
        check(links == 3 * (32 * 32 + 33 * 31), "D22 bridge grid link count");
        check(CityDistrictLandmarks.at(seed, -1190, -1802) == null && CityDistrictLandmarks.at(seed, -825, -1210) == null,
                "district edge must not contain orphan service platforms");
        check(CityDistrictLandmarks.at(seed, -800, -1212).block(8) == null,
                "D22 entrance must not generate an isolated scaffold above the arterial road");
        for (int z = -1549; z <= -1212; z++) for (int x = -801; x <= -799; x++) {
            var approach = CityDistrictLandmarks.at(seed, x, z);
            check(approach != null && "minecraft:air".equals(approach.block(0)) && "minecraft:air".equals(approach.block(1)), "D22 approach headroom");
            check(CityPlan.sample(seed, x, z).kind() != CityPlan.Kind.TURBINE && CityTransit.overlay(x, z) == null,
                    "D22 approach must avoid turbine masts and rail");
        }
        for (int z : new int[]{-1530, -1480, -1430, -1380, -1330, -1280, -1230}) {
            var light = CityDistrictLandmarks.at(seed, -797, z);
            check("minecraft:lantern[hanging=false,waterlogged=false]".equals(light.block(4))
                    && "academy:pillar_white".equals(light.block(3)), "D22 approach supported light");
        }
        for (int z = 548; z <= 699; z++) for (int x = -68; x <= -62; x++) {
            var c = CitySampleRoute.at(x, z);
            check(c != null && "minecraft:air".equals(c.block(0)) && "minecraft:air".equals(c.block(1)), "D15 connector clear");
            check(CityTemplateSites.at(seed, x, z) == null && CityDistrictLandmarks.at(seed, x, z) == null, "D15 connector overlap");
            check(CityStreetProps.at(seed, x, z) == null, "D15 street props obstruct walking lane");
        }
        for (int z : new int[]{612, 650, 690}) {
            var westLight = CityStreetProps.at(seed, -70, z);
            var eastLight = CityStreetProps.at(seed, -60, z);
            check(westLight != null && eastLight != null && "academy:ceiling_light".equals(westLight.block(4))
                    && "academy:ceiling_light".equals(eastLight.block(4)), "D15 extension lighting");
        }
        for (int z = -56; z <= -9; z++) for (int x = -153; x <= -151; x++) {
            var park = CityCampusPark.at(seed, x, z, CityPlan.sample(seed, x, z));
            check(park != null && "academy:plaza_tile".equals(park.block(-1))
                    && park.block(0) == null && park.block(1) == null, "D7 garden pedestrian spine");
        }
        for (int z : new int[]{-47, -19}) for (int x = -184; x <= -137; x++) {
            var park = CityCampusPark.at(seed, x, z, CityPlan.sample(seed, x, z));
            check(park != null && "academy:plaza_tile".equals(park.block(-1))
                    && park.block(0) == null && park.block(1) == null, "D7 garden cross path");
        }
        check("minecraft:oak_log".equals(CityCampusPark.at(seed, -162, -39, CityPlan.sample(seed, -162, -39)).block(0)), "D7 garden oak");
        check("minecraft:cherry_log".equals(CityCampusPark.at(seed, -142, -39, CityPlan.sample(seed, -142, -39)).block(0)), "D7 garden cherry");
        check("academy:ceiling_light".equals(CityCampusPark.at(seed, -148, -44, CityPlan.sample(seed, -148, -44)).block(4)), "D7 garden light");
        System.out.println("PASS landmark plots, D7 garden paths, D21 dam/observatory, D22 scaffold, D23 entry/launch, D15 connector");
    }

    private static void checkBridge(long seed, int x, int z, int floor) {
        var c = CityDistrictLandmarks.at(seed, x, z);
        check(c != null && "academy:brushed_steel".equals(c.block(floor))
                && c.block(floor + 1) == null && c.block(floor + 2) == null,
                "D22 bridge floor or clearance at " + x + "," + floor + "," + z);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

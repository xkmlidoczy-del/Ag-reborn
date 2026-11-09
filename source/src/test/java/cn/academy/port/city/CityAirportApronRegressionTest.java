package cn.academy.port.city;

/** Airport stand markings and runway connection must not overwrite the rail or access route. */
public final class CityAirportApronRegressionTest {
    public static void main(String[] args) {
        long seed = 20261103L;
        int columns = 0;
        for (int x = 1488; x <= 1744; x++) for (int z = -1772; z <= -1689; z++) {
            var c = CityAirportApron.at(x, z);
            check(c != null && c.block(-1) != null, "apron ground coverage");
            if (c.block(0) != null) check(z == -1772 && "academy:pillar_dark".equals(c.block(0)), "only apron-edge floodlight bases may occupy ground");
            var base = CityPlan.sample(seed, x, z);
            check(base.district() == 23 && base.kind() != CityPlan.Kind.RUNWAY, "apron must stay off the runway proper");
            check(CityAirportTransit.overlay(seed, x, z) == null && CityTransit.overlay(x, z) == null
                    && CityDistrictLandmarks.at(seed, x, z) == null, "apron overlaps transit or a landmark");
            columns++;
        }
        for (int center : new int[]{1512, 1560, 1608, 1656, 1704}) {
            check("minecraft:white_concrete".equals(CityAirportApron.at(center - 16, -1750).block(-1)), "stand boundary");
            for (int z = -1758; z <= -1714; z++)
                check("minecraft:yellow_concrete".equals(CityAirportApron.at(center, z).block(-1)), "stand guidance line");
        }
        for (int z = -1714; z <= -1688; z++)
            check("minecraft:yellow_concrete".equals(CityAirportApron.at(1632, z).block(-1)), "continuous runway connection");
        check(CityAirportApron.at(1608, -1688) == null
                && "academy:neon_cyan".equals(CityDistrictLandmarks.at(seed, 1608, -1688).block(-1)), "unrelated runway edge light preserved");
        check("academy:neon_cyan".equals(CityAirportApron.at(1600, -1723).block(-1)), "embedded taxiway edge light");
        for (int x : new int[]{1496, 1544, 1592, 1640, 1688, 1736}) {
            var pole = CityAirportApron.at(x, -1772);
            var arm = CityAirportApron.at(x, -1771);
            check("academy:pillar_dark".equals(pole.block(8)) && "academy:steel_slab[type=bottom]".equals(pole.block(9))
                    && "academy:ceiling_light".equals(arm.block(8)) && "academy:steel_slab[type=bottom]".equals(arm.block(9)),
                    "supported apron floodlight");
        }
        System.out.println("PASS D23 airport apron " + columns + " columns, five stands and continuous runway connector");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

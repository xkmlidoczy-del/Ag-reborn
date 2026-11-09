package cn.academy.port.city;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Placement coverage, retained tower variants and ground-level exterior access. */
public final class CityDistrictBlocksRegressionTest {
    public static void main(String[] args) {
        long seed = 20261103L;
        Map<Integer, Integer> districts = new HashMap<>();
        Map<Integer, String> districtExamples = new java.util.TreeMap<>();
        var uses = new EnumMap<CityDistrictZoning.Use, Integer>(CityDistrictZoning.Use.class);
        var examples = new EnumMap<CityDistrictZoning.Use, String>(CityDistrictZoning.Use.class);
        Set<String> retained = new HashSet<>();
        int selected = 0;
        for (int row = 0; row < 5; row++) for (int col = 0; col < 5; col++) {
            int left = -2000 + col * 800, north = -2000 + row * 800;
            int d = CityPlan.district(left + 400, north + 400);
            if (d < 1 || d > 20) continue;
            for (int bx = 0; bx < 12; bx++) for (int bz = 0; bz < 12; bz++) {
                int ox = left + 24 + bx * 64, oz = north + 24 + bz * 64;
                var site = CityTemplateSites.at(seed, ox + 24, oz + 24);
                if (site == null || site.id().equals("grand_complex") || site.originX() != ox || site.originZ() != oz) continue;
                var block = CityDistrictBlocks.at(seed, ox + 24, oz + 24, site);
                if (block == null) { retained.add(site.id()); continue; }
                selected++; districts.merge(d, 1, Integer::sum); uses.merge(block.use(), 1, Integer::sum);
                districtExamples.putIfAbsent(d, "D" + d + "@" + ox + "," + oz + ":" + block.use() + ":v" + block.variant());
                examples.putIfAbsent(block.use(), "D" + d + "@" + ox + "," + oz + ":v" + block.variant());
                check(!CityAirportTransit.overlapsLot(ox, oz, 48, 48) && !CityNorthSouthTransit.overlapsLot(ox, oz, 48, 48),
                        "selected lot conflicts with railway");
                for (int[] c : new int[][]{{0,0},{0,47},{47,0},{47,47},{24,24}}) {
                    var part = CityDistrictBlocks.at(seed, ox + c[0], oz + c[1]);
                    check(part != null && part.use() == block.use() && part.variant() == block.variant(), "lot must be chunk-independent");
                }
                int door = block.use() == CityDistrictZoning.Use.SCIENCE ? 12 : 23;
                for (int b = 0; b <= 10; b++) {
                    var entrance = CityDistrictBlocks.at(seed, ox + door, oz + b);
                    check(entrance != null && "minecraft:air".equals(entrance.block(0))
                            && "minecraft:air".equals(entrance.block(1)) && "minecraft:air".equals(entrance.block(2)),
                            "north entrance obstructed for " + block.use());
                    check(entrance.block(-1) != null && !"minecraft:air".equals(entrance.block(-1)), "entrance needs floor");
                }
                if (block.use() == CityDistrictZoning.Use.SPORTS) for (int side : new int[]{6, 41}) {
                    for (int z : new int[]{32, 39}) for (int y = 0; y <= 3; y++)
                        check("academy:white_panel_wall".equals(CityDistrictBlocks.at(seed, ox + side, oz + z).block(y)),
                                "sports goal needs both posts");
                    for (int z = 32; z <= 39; z++)
                        check("academy:white_panel_wall".equals(CityDistrictBlocks.at(seed, ox + side, oz + z).block(3)),
                                "sports goal needs a continuous crossbar");
                }
                if (block.use() == CityDistrictZoning.Use.RESIDENTIAL || block.use() == CityDistrictZoning.Use.ACADEMIC) {
                    var tree = CityDistrictBlocks.at(seed, ox + 7, oz + 3);
                    check("minecraft:grass_block".equals(tree.block(-1)) && "minecraft:oak_log".equals(tree.block(0)),
                            "residential and campus forecourts need grounded trees");
                }
            }
        }
        for (int d = 1; d <= 20; d++) check(districts.getOrDefault(d, 0) > 0, "no low-rise site in D" + d);
        check(uses.size() == CityDistrictZoning.Use.values().length && selected > 200 && selected < 600,
                "missing uses or selection outside expected range: " + uses);
        for (String style : new String[]{"academic", "lab", "residential", "commercial"}) for (int n = 1; n <= 10; n++)
            check(retained.contains(style + "_" + (n < 10 ? "0" : "") + n), "lost existing tower variant " + style + "_" + n);
        System.out.println("PASS " + selected + " mixed-use low-rise lots across " + districts.size()
                + " districts; uses=" + uses + "; all 40 tower variants retained");
        System.out.println("examples=" + examples);
        System.out.println("districtExamples=" + districtExamples);
    }
    private static void check(boolean value, String detail) { if (!value) throw new AssertionError(detail); }
}

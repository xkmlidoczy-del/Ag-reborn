package cn.academy.port.city;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Real template bounds and zoning coverage for the planned forecourts. */
public final class CityTemplateFrontageRegressionTest {
    public static void main(String[] args) throws Exception {
        int templates = 0, minSetback = 48;
        for (String style : new String[]{"academic", "lab", "residential", "commercial"}) for (int n = 1; n <= 10; n++) {
            String id = style + "_" + (n < 10 ? "0" : "") + n;
            String path = "/data/academy/structure/city/library/" + id + ".nbt";
            try (var stream = CityTemplateFrontageRegressionTest.class.getResourceAsStream(path)) {
                check(stream != null, "missing template " + id);
                var root = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
                var size = root.getList("size", Tag.TAG_INT);
                int width = size.getInt(0), depth = size.getInt(2);
                check(width <= 48 && depth <= 48, "template exceeds lot " + id);
                minSetback = Math.min(minSetback, (48 - depth) / 2);
                int middle = (width - 1) / 2;
                boolean[][][] occupied = new boolean[4][2][4];
                var blocks = root.getList("blocks", Tag.TAG_COMPOUND);
                for (int i = 0; i < blocks.size(); i++) {
                    var pos = blocks.getCompound(i).getList("pos", Tag.TAG_INT);
                    int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
                    if (x >= middle - 2 && x <= middle + 1 && y >= 0 && y <= 3 && z >= 5 && z <= 6)
                        occupied[x - middle + 2][z - 5][y] = true;
                }
                for (int x = middle - 2; x <= middle + 1; x++) {
                    int a = x - middle + 2;
                    check(CityTemplateLibrary.entranceThreshold(width, depth, x, 5), "threshold geometry " + id);
                    check(occupied[a][0][0] && occupied[a][0][1] && !occupied[a][0][2] && !occupied[a][0][3]
                                    && occupied[a][1][1] && !occupied[a][1][2] && !occupied[a][1][3],
                            "door threshold, support or headroom differs in " + id);
                }
                check(!CityTemplateLibrary.entranceThreshold(width, depth, middle + 2, 5)
                        && !CityTemplateLibrary.entranceThreshold(width, depth, middle, 6), "threshold limited to doorway");
                templates++;
            }
        }
        check(templates == 40 && minSetback >= 6, "frontage needs six unoccupied setback rows");
        long seed = 20261103L;
        Map<Integer, Integer> districts = new HashMap<>();
        var uses = new EnumMap<CityDistrictZoning.Use, Integer>(CityDistrictZoning.Use.class);
        var examples = new EnumMap<CityDistrictZoning.Use, String>(CityDistrictZoning.Use.class);
        int sites = 0, treeSites = 0, retailSites = 0, technicalSites = 0;
        for (int row = 0; row < 5; row++) for (int col = 0; col < 5; col++) {
            int left = -2000 + col * 800, north = -2000 + row * 800;
            int d = CityPlan.district(left + 400, north + 400);
            if (d < 1 || d > 20) continue;
            for (int bx = 0; bx < 12; bx++) for (int bz = 0; bz < 12; bz++) {
                int ox = left + 24 + bx * 64, oz = north + 24 + bz * 64;
                var site = CityTemplateSites.at(seed, ox + 24, oz + 24);
                if (site == null || site.id().equals("grand_complex") || site.originX() != ox || site.originZ() != oz) continue;
                if (CityDistrictBlocks.at(seed, ox + 24, oz + 24, site) != null) continue;
                sites++; districts.merge(d, 1, Integer::sum);
                var use = CityDistrictZoning.use(d, bx, bz); uses.merge(use, 1, Integer::sum);
                check(CityDistrictZoning.at(d, ox + 24, oz + 3) == use, "street-grid use mismatch");
                for (int a = 21; a <= 26; a++) for (int b = 0; b <= 5; b++) {
                    int x = ox + a, z = oz + b;
                    var front = CityTemplateFrontage.plan(seed, x, z, CityPlan.sample(seed, x, z), true);
                    check(front != null && front.use() == use && "academy:plaza_tile".equals(front.block(-1))
                            && front.block(0) == null && front.block(1) == null && front.block(2) == null,
                            "six-wide entrance must remain clear");
                    check(CitySampleRoute.at(x, z) == null && CityTransit.overlay(x, z) == null
                            && !CityAirportTransit.overlapsLot(x, z, 1, 1)
                            && !CityNorthSouthTransit.overlapsLot(x, z, 1, 1), "forecourt conflicts with route or rail");
                }
                var west = CityTemplateFrontage.plan(seed, ox + 8, oz + 3, CityPlan.sample(seed, ox + 8, oz + 3), true);
                var east = CityTemplateFrontage.plan(seed, ox + 39, oz + 3, CityPlan.sample(seed, ox + 39, oz + 3), true);
                check(west != null && east != null && west.variant() == east.variant(), "deterministic lot variant");
                examples.putIfAbsent(use, "D" + d + "@" + ox + "," + oz + ":" + site.id() + ":v" + west.variant());
                String westBase = west.block(0);
                if (westBase != null && westBase.endsWith("_log")) treeSites++;
                if (use == CityDistrictZoning.Use.COMMERCIAL || use == CityDistrictZoning.Use.LEISURE) {
                    check("academy:led_screen".equals(west.block(1)) || "academy:led_screen".equals(east.block(1)), "retail sign"); retailSites++;
                }
                if (use == CityDistrictZoning.Use.SCIENCE || use == CityDistrictZoning.Use.INDUSTRIAL) {
                    check("academy:brushed_steel".equals(west.block(1)), "technical display"); technicalSites++;
                }
            }
        }
        for (int d = 1; d <= 20; d++) check(districts.getOrDefault(d, 0) > 0, "no template frontage in D" + d);
        check(uses.size() == CityDistrictZoning.Use.values().length && sites > 500 && treeSites > 50
                && retailSites > 50 && technicalSites > 50, "missing frontage variety");
        System.out.println("PASS " + templates + " template bounds, " + sites + " frontages across " + districts.size()
                + " districts; uses=" + uses + ", treeSites=" + treeSites + ", retailSites=" + retailSites
                + ", technicalSites=" + technicalSites);
        System.out.println("examples=" + examples);
    }
    private static void check(boolean condition, String detail) { if (!condition) throw new AssertionError(detail); }
}

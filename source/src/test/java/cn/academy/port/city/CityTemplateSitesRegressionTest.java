package cn.academy.port.city;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Checks actual placement coverage and conflicts across the default city layout. */
public final class CityTemplateSitesRegressionTest {
    public static void main(String[] args) {
        long seed = 20261103L;
        Set<String> variants = new HashSet<>();
        Set<String> sites = new HashSet<>();
        for (int z = -2000; z < 2000; z += 4) for (int x = -2000; x < 2000; x += 4) {
            var site = CityTemplateSites.at(seed, x, z);
            if (site == null) continue;
            if (!site.id().equals("grand_complex") && CityPlan.sample(seed, x, z).kind() == CityPlan.Kind.ROAD)
                throw new AssertionError("authored building covers a local road at " + x + "," + z);
            if (CitySampleRoute.at(x, z) != null || CityTransit.overlay(x, z) != null)
                throw new AssertionError("authored building covers the walk or railway at " + x + "," + z);
            variants.add(site.id());
            sites.add(site.id() + ":" + site.originX() + ":" + site.originZ());
        }
        for (String style : List.of("academic", "lab", "residential", "commercial")) for (int i = 1; i <= 10; i++) {
            String id = style + "_" + (i < 10 ? "0" : "") + i;
            if (!variants.contains(id)) throw new AssertionError("no generated lot for " + id);
        }
        if (sites.stream().filter(s -> s.startsWith("grand_complex:")).count() != 1)
            throw new AssertionError("D15 complex must have exactly one site");
        System.out.println("PASS 40 building variants and D15 complex across " + sites.size() + " sites; one million conflict samples");
    }
}
